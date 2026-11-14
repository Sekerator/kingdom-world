package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadFootprint;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.PlotRings;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The work of the orders of the king from afar ({@link Commission}): one at a time on the whole server, the kingdoms in turn, in
 * each the most urgent and oldest order first. A building ordered for a city is tried at the places of the city the data allows
 * ({@link CityPlots}), at most {@value #PLACES} a pass, the nearest to its town hall first: the place is held loaded for a while
 * (through a {@link ChunkHold}), its ground read a row a tick, and a building is laid out only where the ground is level enough
 * and holds nothing but what grew there ({@link NaturalGround}), so that no order ever takes a block a player placed. Ground two
 * to four blocks uneven is levelled by earthworks first. A post of builders is set up by {@link RemotePosts}. Nothing is loaded
 * in the tick, and the work waits while the server is busy.
 */
public final class RemoteWorks {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.commission.";
    /** Places tried in one pass at most. */
    static final int PLACES = 8;
    /** Ticks a place may take to load. */
    static final int WAIT_TICKS = 1_200;
    /** Ticks between two looks for an order to work on. */
    static final int PICK_EVERY = 20;
    /** Ticks a missing item is looked for again. */
    static final long ITEM_RETRY = 600L;
    /** Ticks an order without a place waits before it is tried again: a game day. */
    static final long PLACE_RETRY = 24_000L;
    /** Time the laying out of a building takes in a tick. */
    static final long LAY_NANOS = 2_000_000L;
    /** Blocks of difference in the ground of a place that earthworks level first; more and the place is passed over. */
    static final int LEVEL_MOST = 4;
    /** Blocks between a building ordered at a point and the other places tried around it. */
    static final int AROUND_POINT = 8;

    /** Anchors found unfit in the world, by order; forgotten when an order without a place is tried again. */
    private static final Map<Long, Set<Long>> UNFIT = new HashMap<>();
    /** Places let go a chunk every other tick. */
    private static final List<ChunkHold> RELEASING = new ArrayList<>();
    private static UUID lastKingdom;
    private static Job job;

    private RemoteWorks() {
    }

    // ---------------------------------------------------------------- ordering

    /**
     * Orders a building for a city; the server chooses the place near its town hall.
     *
     * @param actor the player who orders it, whose rights count, or {@code null} for an operator
     */
    public static Outcome orderBuild(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String templateId, Materials materials,
                                     long cityId, Construction.Priority priority) {
        Outcome check = checkTemplate(server, actor, kingdomId, templateId);
        if (!check.ok()) {
            return check;
        }
        Optional<String> dimension = CityPlots.dimension(server, kingdomId, cityId);
        if (dimension.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        return add(server, new Commission(0, kingdomId, Commission.Kind.BUILD, templateId, materials, cityId, dimension.get(), 0, 0, 0, priority,
                actor, Commission.Origin.KING, KingdomWorldApi.gameTime(server), Commission.State.WAITING, 0, "", "", false, 0, 0));
    }

    /**
     * Orders a building at a point of the land of the kingdom, turned as asked: the point itself or a place within a few blocks.
     */
    public static Outcome orderAt(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String templateId, Materials materials,
                                  String dimension, int x, int z, int rotation, Construction.Priority priority) {
        Outcome check = checkTemplate(server, actor, kingdomId, templateId);
        if (!check.ok()) {
            return check;
        }
        if (!kingdomId.equals(KingdomWorldApi.state(server).territory().ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4)))) {
            return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
        }
        long city = CityAreas.at(server, kingdomId, dimension, x, z).map(CityAreas.Area::cityId).orElse(Commission.OUTSIDE);
        return add(server, new Commission(0, kingdomId, Commission.Kind.AT, templateId, materials, city, dimension, x, z, rotation, priority, actor,
                Commission.Origin.KING, KingdomWorldApi.gameTime(server), Commission.State.WAITING, 0, "", "", false, 0, 0));
    }

    /**
     * Orders a post of builders for a city, with a builder hired for it.
     */
    public static Outcome orderPost(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId, Commission.Origin origin) {
        Outcome check = checkKingdom(server, actor, kingdomId, true);
        if (!check.ok()) {
            return check;
        }
        Optional<String> dimension = CityPlots.dimension(server, kingdomId, cityId);
        if (dimension.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        return add(server, new Commission(0, kingdomId, Commission.Kind.POST, "", Materials.NONE, cityId, dimension.get(), 0, 0, 0,
                Construction.Priority.HIGH, actor, origin, KingdomWorldApi.gameTime(server), Commission.State.WAITING, 0, "", "", false, 0, 0));
    }

    /**
     * Gives an order up: what was laid out for it stays.
     */
    public static Outcome cancel(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long id) {
        Outcome check = checkKingdom(server, actor, kingdomId, false);
        if (!check.ok()) {
            return check;
        }
        if (KingdomWorldApi.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Commission> commission = state.construction().commission(id).filter(found -> found.kingdomId().equals(kingdomId));
        if (commission.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CONSTRUCTION);
        }
        drop(server, id);
        LOGGER.info("Kingdom {} gave up the order {}", kingdomId, id);
        return Outcome.DONE;
    }

    static Outcome add(MinecraftServer server, Commission commission) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        Commission added = KingdomWorldApi.state(server).construction().addCommission(commission);
        LOGGER.info("Kingdom {} ordered {} {} for city {} (order {})", added.kingdomId(), added.kind().id(), added.templateId(), added.cityId(),
                added.id());
        return Outcome.DONE;
    }

    /**
     * Removes an order, letting go of its place if it is being worked on.
     */
    static void drop(MinecraftServer server, long id) {
        if (job != null && job.commission.id() == id) {
            endJob();
        }
        UNFIT.remove(id);
        KingdomWorldApi.state(server).construction().removeCommission(id);
    }

    static Outcome checkKingdom(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, boolean citizens) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (state.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && (!state.membership().hasPermission(actor, kingdomId, Permission.MANAGE_CONSTRUCTION)
                || citizens && !state.membership().hasPermission(actor, kingdomId, Permission.MANAGE_CITIZENS))) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    private static Outcome checkTemplate(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String templateId) {
        Outcome check = checkKingdom(server, actor, kingdomId, false);
        if (!check.ok()) {
            return check;
        }
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        if (template.isEmpty() || template.get().owner() != null && !template.get().owner().equals(kingdomId)) {
            return Outcome.fail(KingdomError.UNKNOWN_TEMPLATE);
        }
        if (BuildingTemplates.problem(template.get(), ConstructionSettings.rules()) != null) {
            return Outcome.fail(KingdomError.TEMPLATE_UNAVAILABLE);
        }
        return Outcome.DONE;
    }

    /**
     * Why a building could not stand at a point by the data alone (the land, other constructions and buildings, roads and walls),
     * or {@code null} if it could; what stands there is looked at when the order is worked on.
     */
    @Nullable
    public static Component checkPoint(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String templateId, String dimension, int x,
                                       int z, int rotation) {
        Outcome check = checkTemplate(server, actor, kingdomId, templateId);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error()).message();
        }
        BuildingTemplate template = BuildingTemplates.get(templateId).orElseThrow();
        Placement placement = new Placement(Math.floorMod(rotation, 4), false);
        BlockPos anchor = ConstructionWork.anchorAt(template, x, 0, z, placement);
        Box box = template.box(anchor, placement);
        Box column = new Box(box.minX() - 1, -4096, box.minZ() - 1, box.maxX() + 1, 4096, box.maxZ() + 1);
        Outcome lay = KingdomWorldApi.state(server).checkLay(actor, kingdomId, dimension, column, ConstructionSettings.rules());
        if (!lay.ok()) {
            return KingdomActions.Feedback.error(lay.error()).message();
        }
        int reach = Math.max(template.sizeX(), template.sizeZ()) + CityPlots.ROAD_MARGIN + 1;
        if (RoadFootprint.near(CityPlots.within(RoadFootprint.roadsAndWalls(server, kingdomId), x, z, reach), box, CityPlots.ROAD_MARGIN)) {
            return Component.translatable(KEY + "near_road");
        }
        return null;
    }

    // ---------------------------------------------------------------- the work

    /**
     * The work on one order: what it holds loaded and what it does every tick.
     */
    abstract static class Job {
        Commission commission;
        final ServerLevel level;
        @Nullable
        ChunkHold hold;

        Job(Commission commission, ServerLevel level) {
            this.commission = commission;
            this.level = level;
        }

        abstract void tick(MinecraftServer server);

        /**
         * Lets go of the place held: a chunk every other tick.
         */
        void letGo() {
            if (hold != null) {
                RELEASING.add(hold);
                hold = null;
            }
        }
    }

    /**
     * Called every server tick.
     */
    static void tick(MinecraftServer server) {
        RELEASING.removeIf(ChunkHold::release);
        if (KingdomWorldApi.isReadOnly(server) || ChunkHold.paused(server)) {
            return;
        }
        if (job == null) {
            if (server.getTickCount() % PICK_EVERY == 0) {
                pick(server);
            }
            return;
        }
        Job current = job;
        if (KingdomWorldApi.state(server).construction().commission(current.commission.id()).isEmpty()) {
            endJob();
            return;
        }
        try {
            current.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("The order {} of {} failed", current.commission.id(), current.commission.kingdomId(), e);
            keep(server, current.commission.withState(Commission.State.NO_PLACE, "failed", KingdomWorldApi.gameTime(server) + PLACE_RETRY));
        }
    }

    private static void pick(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        TreeSet<UUID> kingdoms = new TreeSet<>();
        for (Commission commission : state.construction().commissions()) {
            kingdoms.add(commission.kingdomId());
        }
        if (kingdoms.isEmpty()) {
            return;
        }
        // The kingdom after the one served last first, so that every kingdom takes its turn.
        UUID from = lastKingdom == null ? kingdoms.last() : lastKingdom;
        List<UUID> order = new ArrayList<>(kingdoms.tailSet(from, false));
        order.addAll(kingdoms.headSet(from, true));
        for (UUID kingdom : order) {
            List<Commission> waiting = new ArrayList<>(state.construction().commissionsOf(kingdom));
            waiting.sort(Comparator.comparingInt((Commission c) -> -c.priority().ordinal()).thenComparingLong(Commission::created)
                    .thenComparingLong(Commission::id));
            for (Commission commission : waiting) {
                if (start(server, commission, now)) {
                    lastKingdom = kingdom;
                    return;
                }
            }
        }
    }

    /**
     * Starts the work on an order if it may go on now.
     */
    private static boolean start(MinecraftServer server, Commission commission, long now) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (commission.kind() != Commission.Kind.HALL && commission.cityId() != Commission.OUTSIDE
                && CityPlots.middle(server, commission.kingdomId(), commission.cityId()).isEmpty()) {
            // The city of the order went (given up, taken): the order goes with a message, what was laid out for it stays.
            drop(server, commission.id());
            tell(server, commission, Component.translatable(KEY + "city_gone", what(commission)).withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (commission.actor() != null && checkKingdom(server, commission.actor(), commission.kingdomId(),
                commission.kind() == Commission.Kind.POST).error() == KingdomError.NO_PERMISSION) {
            drop(server, commission.id());
            tell(server, commission, Component.translatable(KEY + "no_right", what(commission)).withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        switch (commission.state()) {
            case LEVELLING -> {
                if (state.construction().get(commission.after()).isPresent()) {
                    return false;
                }
                // The earthworks are done or given up: the place is looked at again.
                commission = commission.withState(Commission.State.WAITING, "", commission.retryAt());
                state.construction().putCommission(commission);
            }
            case NO_PLACE, LACKS_ITEM -> {
                if (commission.retryAt() > now) {
                    return false;
                }
                if (commission.state() == Commission.State.NO_PLACE) {
                    UNFIT.remove(commission.id());
                }
            }
            default -> {
            }
        }
        boolean building = commission.kind() == Commission.Kind.BUILD || commission.kind() == Commission.Kind.AT;
        if (building) {
            Optional<BuildingTemplate> template = BuildingTemplates.get(commission.templateId());
            if (template.isEmpty() || BuildingTemplates.problem(template.get(), ConstructionSettings.rules()) != null) {
                drop(server, commission.id());
                tell(server, commission, Component.translatable(KEY + "unavailable", what(commission)).withStyle(ChatFormatting.YELLOW), false);
                return false;
            }
            if (ConstructionSettings.rules().full(state.construction().count(commission.kingdomId()))) {
                // An operator brought the limit of constructions back: the order waits until one is finished.
                if (commission.state() != Commission.State.LIMIT) {
                    state.construction().putCommission(commission.withState(Commission.State.LIMIT, "", now));
                }
                return false;
            }
        }
        ServerLevel level = ConstructionWork.level(server, commission.dimension());
        if (level == null) {
            return false;
        }
        Job started = switch (commission.kind()) {
            case BUILD, AT -> new BuildJob(commission.withState(Commission.State.LOOKING, commission.reason(), now), level);
            case POST -> RemotePosts.job(commission.withState(Commission.State.LOOKING, "", now), level);
            case HALL -> null;
        };
        if (started == null) {
            return false;
        }
        state.construction().putCommission(started.commission);
        job = started;
        return true;
    }

    /**
     * Keeps the new state of the order worked on and ends the work on it.
     */
    static void keep(MinecraftServer server, Commission commission) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (state.construction().commission(commission.id()).isPresent()) {
            state.construction().putCommission(commission);
        }
        endJob();
    }

    /**
     * Ends the work on the order worked on, letting go of its place.
     */
    static void endJob() {
        if (job != null) {
            job.letGo();
        }
        job = null;
    }

    // ---------------------------------------------------------------- a building

    private enum Phase {
        PLACES,
        HOLD,
        MEASURE,
        LAY
    }

    /**
     * A building ordered for a city or at a point: one pass over at most {@value #PLACES} places.
     */
    private static final class BuildJob extends Job {
        private Phase phase = Phase.PLACES;
        private List<CityPlots.Plot> plots = List.of();
        private int plot;
        private int ticks;
        private int row;
        private int[] heights;
        private ConstructionWork.Laying laying;

        BuildJob(Commission commission, ServerLevel level) {
            super(commission, level);
        }

        @Override
        void tick(MinecraftServer server) {
            switch (phase) {
                case PLACES -> places(server);
                case HOLD -> hold(server);
                case MEASURE -> measure(server);
                case LAY -> lay(server);
            }
        }

        private Set<Long> unfit() {
            return UNFIT.computeIfAbsent(commission.id(), key -> new HashSet<>());
        }

        private void places(MinecraftServer server) {
            Optional<BuildingTemplate> template = BuildingTemplates.get(commission.templateId());
            if (template.isEmpty()) {
                drop(server, commission.id());
                return;
            }
            plots = commission.kind() == Commission.Kind.AT ? pointPlots(server, template.get())
                    : CityPlots.places(server, commission.kingdomId(), commission.cityId(), template.get(), commission.actor(), unfit(), PLACES);
            if (plots.isEmpty()) {
                // Every place the data allows was looked at: the order waits a day and tries them all again.
                String why = commission.reason().startsWith("blocked|") ? commission.reason() : "no_place";
                Commission waiting = commission.withState(Commission.State.NO_PLACE, why, KingdomWorldApi.gameTime(server) + PLACE_RETRY);
                if (commission.retryAt() == 0) {
                    // Told once: the order stays in the list with its reason until it is laid out or given up.
                    tell(server, waiting, Component.translatable(KEY + "no_place", what(waiting), reasonText(waiting))
                            .withStyle(ChatFormatting.YELLOW), false);
                }
                keep(server, waiting);
                return;
            }
            next(server);
        }

        /**
         * The places of a building ordered at a point: the point itself, then the places within a few blocks of it, turned the same.
         */
        private List<CityPlots.Plot> pointPlots(MinecraftServer server, BuildingTemplate template) {
            Placement placement = new Placement(commission.rotation(), false);
            List<int[]> points = new ArrayList<>();
            points.add(new int[]{commission.x(), commission.z()});
            points.addAll(PlotRings.around(commission.x(), commission.z(), AROUND_POINT));
            int reach = AROUND_POINT + Math.max(template.sizeX(), template.sizeZ()) + CityPlots.ROAD_MARGIN + 1;
            List<int[]> footprint = CityPlots.within(RoadFootprint.roadsAndWalls(server, commission.kingdomId()), commission.x(), commission.z(), reach);
            int y = level.getSeaLevel();
            List<CityPlots.Plot> found = new ArrayList<>();
            for (int[] point : points) {
                BlockPos anchor = ConstructionWork.anchorAt(template, point[0], y, point[1], placement);
                if (unfit().contains(anchor.asLong())) {
                    continue;
                }
                Box box = template.box(anchor, placement);
                if (CityPlots.fits(server, commission.kingdomId(), commission.dimension(), box, commission.actor(), footprint)) {
                    found.add(new CityPlots.Plot(anchor, placement, box));
                    if (found.size() >= PLACES) {
                        break;
                    }
                }
            }
            return found;
        }

        /**
         * Goes on to the next place of the pass, or ends the pass: the order waits for its turn again and the places passed over
         * are remembered.
         */
        private void next(MinecraftServer server) {
            letGo();
            laying = null;
            if (plot >= plots.size()) {
                keep(server, commission.withState(Commission.State.WAITING, commission.reason(), commission.retryAt()));
                return;
            }
            Box box = plots.get(plot).box();
            hold = new ChunkHold(level, ChunkHold.ringed(box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1));
            ticks = 0;
            row = 0;
            heights = new int[(box.sizeX() + 2) * (box.sizeZ() + 2)];
            phase = Phase.HOLD;
        }

        private void passOver(MinecraftServer server, @Nullable BlockPos blocker) {
            unfit().add(plots.get(plot).anchor().asLong());
            if (blocker != null && !commission.reason().startsWith("blocked|")) {
                commission = commission.withState(commission.state(), "blocked|" + blocker.getX() + "|" + blocker.getY() + "|" + blocker.getZ(),
                        commission.retryAt());
                KingdomWorldApi.state(server).construction().putCommission(commission);
            }
            plot++;
            next(server);
        }

        private void hold(MinecraftServer server) {
            hold.hold();
            if (hold.ready()) {
                phase = Phase.MEASURE;
                return;
            }
            if (++ticks > WAIT_TICKS) {
                // The place did not load in time: it is tried again in another pass.
                plot++;
                next(server);
            }
        }

        /**
         * Reads the ground of the place and the ring around it a row a tick, then judges it: level and free, levelled first, or
         * passed over.
         */
        private void measure(MinecraftServer server) {
            hold.hold();
            CityPlots.Plot place = plots.get(plot);
            Box box = place.box();
            int width = box.sizeX() + 2;
            int depth = box.sizeZ() + 2;
            if (row < depth) {
                int z = box.minZ() - 1 + row;
                for (int i = 0; i < width; i++) {
                    heights[row * width + i] = NaturalGround.groundAt(level, box.minX() - 1 + i, z);
                }
                row++;
                return;
            }
            Map<Integer, Integer> counts = new HashMap<>();
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int height : heights) {
                counts.merge(height, 1, Integer::sum);
                low = Math.min(low, height);
                high = Math.max(high, height);
            }
            int street = street(counts);
            if (high - low > LEVEL_MOST) {
                passOver(server, null);
                return;
            }
            Optional<BuildingTemplate> template = BuildingTemplates.get(commission.templateId());
            if (template.isEmpty()) {
                drop(server, commission.id());
                return;
            }
            BlockPos anchor = new BlockPos(place.anchor().getX(), street + 1, place.anchor().getZ());
            Box placed = template.get().box(anchor, place.placement());
            Box wide = new Box(placed.minX() - 1, placed.minY(), placed.minZ() - 1, placed.maxX() + 1, placed.maxY(), placed.maxZ() + 1);
            // The ground as it grew may stand up to the highest column; over it only what grows, and a cellar looks below too.
            BlockPos blocker = NaturalGround.free(level, commission.kingdomId(), wide, high, Math.min(street - 3, placed.minY() - 1),
                    placed.maxY() + 1);
            if (blocker != null) {
                passOver(server, blocker);
                return;
            }
            if (high - low > 1) {
                level(server, wide, street);
                return;
            }
            Materials materials = commission.materials();
            if (materials.kingdomStyle()) {
                // The style of the kingdom, chosen as the building is laid out.
                materials = KingdomLooks.materials(server, commission.kingdomId(), commission.templateId(), anchor);
            }
            laying = new ConstructionWork.Laying(level, commission.actor(), commission.kingdomId(), template.get(), anchor, place.placement(),
                    materials);
            phase = Phase.LAY;
        }

        /**
         * Earthworks level the place and the ring around it to the street first; the order waits for them.
         */
        private void level(MinecraftServer server, Box wide, int street) {
            Earthworks.Request request = new Earthworks.Request(new BlockPos(wide.minX(), street, wide.minZ()),
                    new BlockPos(wide.maxX(), street, wide.maxZ()), Earthwork.Mode.LEVEL, street, false);
            KingdomWorldState.ConstructionChange change = Earthworks.lay(level, commission.actor(), commission.kingdomId(), request);
            if (change.construction().isEmpty()) {
                passOver(server, null);
                return;
            }
            Construction earthworks = change.construction().get();
            earthworks.setOrderMissing(true);
            earthworks.setPriority(commission.priority());
            Commission levelling = commission.withAfter(earthworks.id());
            tell(server, levelling, Component.translatable(KEY + "levelling", what(levelling), where(wide, street)), true);
            keep(server, levelling);
        }

        private void lay(MinecraftServer server) {
            hold.hold();
            Optional<KingdomWorldState.ConstructionChange> done = laying.step(LAY_NANOS);
            if (done.isEmpty()) {
                return;
            }
            if (done.get().construction().isEmpty()) {
                LOGGER.debug("Order {} could not lay {} at {}: {}", commission.id(), commission.templateId(), plots.get(plot).anchor(),
                        done.get().outcome().error());
                passOver(server, null);
                return;
            }
            Construction construction = done.get().construction().get();
            construction.setOrderMissing(true);
            construction.setPriority(commission.priority());
            BlockPos at = ConstructionWork.blockPos(construction.anchor());
            UNFIT.remove(commission.id());
            KingdomWorldApi.state(server).construction().removeCommission(commission.id());
            tell(server, commission, Component.translatable(KEY + "laid", what(commission), at.getX() + " " + at.getY() + " " + at.getZ(),
                    cityName(server, commission)).withStyle(ChatFormatting.GREEN), true);
            endJob();
        }
    }

    /**
     * The height of the street of a place: the most frequent height of its ground, the lower of two as frequent.
     */
    static int street(Map<Integer, Integer> counts) {
        int best = Integer.MIN_VALUE;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount || entry.getValue() == bestCount && entry.getKey() < best) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- messages

    /**
     * Tells the members who manage construction, and the player who ordered it if online.
     */
    static void tell(MinecraftServer server, Commission commission, Component message, boolean success) {
        KingdomWorldApi.kingdom(server, commission.kingdomId()).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom,
                Permission.MANAGE_CONSTRUCTION, message));
        if (commission.actor() != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(commission.actor());
            if (player != null) {
                KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(success, message));
            }
        }
    }

    /**
     * What an order is, for messages: the name of its building, a post of builders or a city.
     */
    static Component what(Commission commission) {
        return switch (commission.kind()) {
            case BUILD, AT -> BuildingTemplates.get(commission.templateId()).map(BuildingTemplate::name)
                    .orElse(Component.literal(commission.templateId()));
            case POST -> Component.translatable(KEY + "post");
            case HALL -> Component.translatable(KEY + "hall", commission.name());
        };
    }

    /**
     * Why an order waits, from its reason: the block in the way or the item it lacks.
     */
    public static Component reasonText(Commission commission) {
        String reason = commission.reason();
        if (reason.startsWith("blocked|")) {
            String[] parts = reason.split("\\|");
            if (parts.length == 4) {
                return Component.translatable(KEY + "reason.blocked", parts[1] + " " + parts[2] + " " + parts[3]);
            }
        }
        int bar = reason.indexOf('|');
        if (bar > 0 && (reason.startsWith("item|") || reason.startsWith("store|"))) {
            String item = reason.substring(bar + 1);
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(item);
            Item found = id == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            Component name = found == null ? Component.literal(item) : found.getDescription();
            return Component.translatable(KEY + "reason." + reason.substring(0, bar), name);
        }
        return Component.translatable(KEY + "reason." + (reason.isEmpty() ? "none" : reason));
    }

    static Component cityName(MinecraftServer server, Commission commission) {
        if (commission.cityId() == Commission.CAPITAL) {
            return KingdomWorldApi.kingdom(server, commission.kingdomId()).map(Kingdom::name).map(name -> (Component) Component.literal(name))
                    .orElse(Component.empty());
        }
        if (commission.cityId() == Commission.OUTSIDE) {
            return Component.translatable(KEY + "outside");
        }
        return RoadWork.state(server).city(commission.cityId()).map(city -> (Component) Component.literal(city.name()))
                .orElse(Component.empty());
    }

    private static String where(Box box, int y) {
        return Math.floorDiv(box.minX() + box.maxX(), 2) + " " + y + " " + Math.floorDiv(box.minZ() + box.maxZ(), 2);
    }

    // ---------------------------------------------------------------- items

    /**
     * Orders an item of the workshops for an order that lacks it, outside the limit of the orders of the king.
     *
     * @return the order, or 0 if the workshops do not make it
     */
    static long orderItem(MinecraftServer server, Commission commission, String item) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (commission.order() > 0 && state.orders().get(commission.kingdomId(), commission.order()).isPresent()) {
            return commission.order();
        }
        if (!Production.orderable(server, item)) {
            return 0;
        }
        KingdomWorldState.Ordering ordering = state.order(null, commission.kingdomId(), item, Order.Kind.MAKE, 1, Order.Priority.HIGH,
                KingdomWorldApi.gameTime(server), Integer.MAX_VALUE, id -> Production.orderable(server, id));
        ordering.order().ifPresent(order -> LOGGER.info("Order {} of {} ordered a {} from the workshops", commission.id(), commission.kingdomId(),
                item));
        return ordering.order().map(Order::id).orElse(0L);
    }

    /**
     * Takes one item of an id from the storehouses of a kingdom, what others set aside left alone.
     */
    static boolean takeOne(MinecraftServer server, UUID kingdomId, String item, @Nullable WorldPos near) {
        List<KingdomStorage.Take> plan = KingdomStorage.plan(server, kingdomId, Map.of(item, 1),
                id -> Production.reservations().reserved(kingdomId, id, null), near);
        return plan != null && KingdomStorage.takePlanned(server, plan) != null;
    }

    /**
     * Gives an item back to the storehouses of a kingdom.
     */
    static void giveBack(MinecraftServer server, UUID kingdomId, Item item, @Nullable WorldPos near) {
        ItemStack rest = KingdomStorage.insert(server, kingdomId, new ItemStack(item), near);
        if (!rest.isEmpty()) {
            LOGGER.warn("No room in the storehouses of {} for a {} given back", kingdomId, item);
        }
    }

    // ---------------------------------------------------------------- the view and the server

    /**
     * The order worked on now, or 0.
     */
    public static long working() {
        return job == null ? 0 : job.commission.id();
    }

    /**
     * Lets every held chunk go and forgets everything, when the server stops.
     */
    static void reset() {
        if (job != null && job.hold != null) {
            job.hold.releaseAll();
        }
        for (ChunkHold held : RELEASING) {
            held.releaseAll();
        }
        RELEASING.clear();
        job = null;
        lastKingdom = null;
        UNFIT.clear();
    }
}
