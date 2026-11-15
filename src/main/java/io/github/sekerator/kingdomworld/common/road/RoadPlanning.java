package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.RouteSearch;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Planning roads: the search for a way between two points runs on the server a few thousand steps a tick; the way found
 * is kept for a while as a plan, shown on the atlas with its length and cost, and becomes a road when the king confirms
 * it in a class. Points near a city or a capital start and end there.
 */
public final class RoadPlanning {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks within which a point of a plan is taken to mean a city or a capital. */
    public static final int SNAP = 48;
    /** Ticks a plan is kept for the king to confirm. */
    private static final long PLAN_TICKS = 20L * 60 * 15;
    /** Searches running at once at most, the one for a road of the plan of the states counted. */
    static final int MAX_JOBS = 4;
    /** Time the searches for roads take in a tick at most, besides the slice under way. */
    private static final long SEARCH_NANOS = 5_000_000L;
    /** Steps of a search between two looks at the time. */
    private static final int SEARCH_SLICE = 100;

    /**
     * A way found for a road: of the old search in the first layout, any class; of the planner of levelled roads in its class and
     * way, with the survey of its earthworks, the owner of every block of its width and the gates it cuts into walls of its own.
     *
     * @param guessed whether some of its land was only guessed: it is read and planned again before the road is laid
     * @param gates   the walls of its own cities it crosses outside a gate: the wall, x and z
     */
    public record Plan(long id, UUID kingdomId, @Nullable UUID player, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells,
                       long made, int layout, @Nullable RoadClass roadClass, io.github.sekerator.kingdomworld.core.road.RouteStyle style,
                       List<RoadLayout.ColumnSurvey> surveys, double earthTotal, boolean guessed, List<UUID> blockOwners, List<long[]> gates,
                       @Nullable io.github.sekerator.kingdomworld.core.road.RoadEstimate estimate) {
        public Plan {
            cells = List.copyOf(cells);
            surveys = List.copyOf(surveys);
            blockOwners = java.util.Collections.unmodifiableList(new ArrayList<>(blockOwners));
            gates = List.copyOf(gates);
        }

        public Plan(long id, UUID kingdomId, @Nullable UUID player, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells,
                    long made) {
            this(id, kingdomId, player, dimension, from, to, cells, made, RoadLayout.LAID, null,
                    io.github.sekerator.kingdomworld.core.road.RouteStyle.TERRAIN, List.of(), 0, false, List.of(), List.of(), null);
        }

        public boolean levelled() {
            return layout == RoadLayout.LEVELLED;
        }

        /**
         * Every kingdom other than the planner whose land the road touches, with the sections that touch it: for a levelled road
         * over the whole width of its blocks with their verges.
         */
        public Map<UUID, Integer> crossed() {
            if (!levelled() || roadClass == null) {
                return RoadState.crossedSections(cells, kingdomId);
            }
            Map<UUID, Integer> result = new LinkedHashMap<>();
            List<java.util.Set<UUID>> bySection = new ArrayList<>();
            List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, null);
            for (int b = 0; b < blocks.size() && b < blockOwners.size(); b++) {
                int section = blocks.get(b).cell() / Road.SECTION_CELLS;
                while (bySection.size() <= section) {
                    bySection.add(new java.util.HashSet<>());
                }
                UUID owner = blockOwners.get(b);
                if (owner != null && !owner.equals(kingdomId)) {
                    bySection.get(section).add(owner);
                }
            }
            for (java.util.Set<UUID> owners : bySection) {
                for (UUID owner : owners) {
                    result.merge(owner, 1, Integer::sum);
                }
            }
            return result;
        }

        /**
         * The kingdom whose land most of each section is on, over the whole width of a levelled road.
         */
        public List<UUID> sectionOwners() {
            if (!levelled() || roadClass == null) {
                return RoadState.sectionOwners(cells);
            }
            List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, null);
            List<Map<UUID, Integer>> counts = new ArrayList<>();
            List<Integer> wild = new ArrayList<>();
            for (int b = 0; b < blocks.size() && b < blockOwners.size(); b++) {
                int section = blocks.get(b).cell() / Road.SECTION_CELLS;
                while (counts.size() <= section) {
                    counts.add(new LinkedHashMap<>());
                    wild.add(0);
                }
                UUID owner = blockOwners.get(b);
                if (owner == null) {
                    wild.set(section, wild.get(section) + 1);
                } else {
                    counts.get(section).merge(owner, 1, Integer::sum);
                }
            }
            List<UUID> owners = new ArrayList<>();
            for (int s = 0; s < counts.size(); s++) {
                UUID best = null;
                int bestCount = wild.get(s);
                for (Map.Entry<UUID, Integer> entry : counts.get(s).entrySet()) {
                    if (entry.getValue() > bestCount) {
                        best = entry.getKey();
                        bestCount = entry.getValue();
                    }
                }
                owners.add(best);
            }
            return owners;
        }
    }

    private static final class Job {
        final long id;
        final UUID kingdomId;
        final UUID player;
        final ServerLevel level;
        final Endpoint from;
        final Endpoint to;
        final RoadTerrain terrain;
        final RouteSearch search;
        /** The class the plan is shown in. */
        RoadClass viewClass = RoadClass.GRAVEL;
        /** For an operator: build the road in this class at once when the way is found. */
        RoadClass buildClass;
        Road.Mode buildMode;

        Job(long id, UUID kingdomId, UUID player, ServerLevel level, Endpoint from, Endpoint to, RoadRules rules) {
            this.id = id;
            this.kingdomId = kingdomId;
            this.player = player;
            this.level = level;
            this.from = from;
            this.to = to;
            this.terrain = new RoadTerrain(level);
            this.search = new RouteSearch(terrain, from.x(), from.z(), to.x(), to.z(), kingdomId, rules);
        }
    }

    private static final Map<Long, Job> JOBS = new LinkedHashMap<>();
    private static final Map<Long, Plan> PLANS = new LinkedHashMap<>();
    private static long nextId = 1;

    private RoadPlanning() {
    }

    static void reset() {
        JOBS.clear();
        PLANS.clear();
        nextId = 1;
    }

    public static Optional<Plan> plan(long id) {
        return Optional.ofNullable(PLANS.get(id));
    }

    /**
     * Where a point of a plan is: a city or a capital nearby, or the point itself.
     */
    public static Endpoint endpoint(MinecraftServer server, String dimension, int x, int z) {
        double best = (double) SNAP * SNAP;
        Endpoint result = Endpoint.point(x, z);
        for (City city : RoadWork.state(server).cities()) {
            if (city.dimension().equals(dimension) && city.distanceSqr(x, z) <= best) {
                best = city.distanceSqr(x, z);
                result = Endpoint.city(city);
            }
        }
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            Capital capital = territory.capital();
            if (capital == null || !capital.dimension().equals(dimension)) {
                continue;
            }
            double dx = capital.x() - x;
            double dz = capital.z() - z;
            if (dx * dx + dz * dz <= best) {
                best = dx * dx + dz * dz;
                result = Endpoint.capital(territory.kingdomId(), capital.x(), capital.z());
            }
        }
        return result;
    }

    /**
     * The name of a place for people.
     */
    public static Component name(MinecraftServer server, Endpoint endpoint) {
        return switch (endpoint.kind()) {
            case CITY -> RoadWork.state(server).city(endpoint.city()).<Component>map(city ->
                    io.github.sekerator.kingdomworld.common.npc.NpcTexts.city(city.name()))
                    .orElseGet(() -> Component.translatable(RoadWork.KEY + "place", endpoint.x(), endpoint.z()));
            case CAPITAL -> Component.translatable(RoadWork.KEY + "capital_of", RoadWork.kingdomName(server, endpoint.kingdom()));
            case POINT -> Component.translatable(RoadWork.KEY + "place", endpoint.x(), endpoint.z());
        };
    }

    /**
     * Starts the search for the way of a road.
     *
     * @param player the player who asked, who gets the plan; {@code null} for an operator
     */
    public static Outcome start(ServerLevel level, @Nullable UUID player, UUID kingdomId, int fromX, int fromZ, int toX, int toZ) {
        return start(level, player, kingdomId, fromX, fromZ, toX, toZ, null, null);
    }

    /**
     * Starts the old search for a player who plans in a dimension with a ceiling, the plan shown in a class.
     */
    public static Outcome start(ServerLevel level, UUID player, UUID kingdomId, int fromX, int fromZ, int toX, int toZ, RoadClass viewClass) {
        Outcome outcome = start(level, player, kingdomId, fromX, fromZ, toX, toZ, null, null);
        if (outcome.ok()) {
            for (Job job : JOBS.values()) {
                if (job.kingdomId.equals(kingdomId)) {
                    job.viewClass = viewClass;
                }
            }
        }
        return outcome;
    }

    /**
     * Starts the search for the way of a road that is built in a class as soon as the way is found; for operators.
     */
    public static Outcome startAndBuild(ServerLevel level, UUID kingdomId, int fromX, int fromZ, int toX, int toZ, RoadClass roadClass,
                                        Road.Mode mode) {
        return start(level, null, kingdomId, null, fromX, fromZ, toX, toZ, roadClass, mode);
    }

    /**
     * Starts the search for the way of a road that begins exactly at a place given (the gate of a capital whose throne stands
     * under a roof) and is built in a class as soon as the way is found.
     *
     * @param from where the road begins: the search starts at its coordinates
     */
    public static Outcome startAndBuildFrom(ServerLevel level, UUID kingdomId, Endpoint from, int toX, int toZ, RoadClass roadClass,
                                            Road.Mode mode) {
        return start(level, null, kingdomId, from, from.x(), from.z(), toX, toZ, roadClass, mode);
    }

    private static Outcome start(ServerLevel level, @Nullable UUID player, UUID kingdomId, int fromX, int fromZ, int toX, int toZ,
                                 @Nullable RoadClass buildClass, @Nullable Road.Mode buildMode) {
        return start(level, player, kingdomId, null, fromX, fromZ, toX, toZ, buildClass, buildMode);
    }

    private static Outcome start(ServerLevel level, @Nullable UUID player, UUID kingdomId, @Nullable Endpoint given, int fromX, int fromZ,
                                 int toX, int toZ, @Nullable RoadClass buildClass, @Nullable Road.Mode buildMode) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!RoadWork.mayManage(server, player, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (RoadWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        for (Job job : JOBS.values()) {
            if (job.kingdomId.equals(kingdomId)) {
                return Outcome.fail(KingdomError.ROUTE_SEARCHING);
            }
        }
        if (PlannedRoads.searching(kingdomId) || jobCount() >= MAX_JOBS) {
            return Outcome.fail(KingdomError.ROUTE_SEARCHING);
        }
        RoadRules rules = RoadSettings.rules();
        String dimension = Dimensions.id(level);
        Endpoint from = given != null ? given : endpoint(server, dimension, fromX, fromZ);
        Endpoint to = endpoint(server, dimension, toX, toZ);
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (Math.sqrt(dx * dx + dz * dz) > rules.maxRouteLength()) {
            return Outcome.fail(KingdomError.ROUTE_TOO_LONG);
        }
        long id = nextId++;
        Job job = new Job(id, kingdomId, player, level, from, to, rules);
        job.buildClass = buildClass;
        job.buildMode = buildMode;
        JOBS.put(id, job);
        return Outcome.DONE;
    }

    /** Whether searches read the chunks they need at once instead of asking for them and waiting. */
    static boolean loadNow;

    /**
     * Searches on with the chunks read at once, for a raising run to its end without ticks (game tests).
     */
    public static void tickSearchNow(MinecraftServer server) {
        loadNow = true;
        try {
            tickSearch(server);
        } finally {
            loadNow = false;
        }
    }

    /**
     * Searches on. Called every tick.
     */
    public static void tickSearch(MinecraftServer server) {
        long now = KingdomWorldApi.gameTime(server);
        PLANS.values().removeIf(plan -> now - plan.made() > PLAN_TICKS);
        if (JOBS.isEmpty()) {
            return;
        }
        RoadRules rules = RoadSettings.rules();
        int share = Math.max(1, rules.nodesPerTick() / JOBS.size());
        int chunks = Math.max(1, rules.chunksPerTick() / JOBS.size());
        Iterator<Job> iterator = JOBS.values().iterator();
        long started = System.nanoTime();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (System.nanoTime() - started > SEARCH_NANOS) {
                // The time of the tick is spent: the others search on in the next one.
                break;
            }
            job.terrain.allow(chunks);
            RouteSearch.Status status = RouteSearch.Status.RUNNING;
            try {
                // A slice at a time, so that a search over rough ground stops when the time of the tick is spent.
                for (int done = 0; done < share && status == RouteSearch.Status.RUNNING; done += SEARCH_SLICE) {
                    status = job.search.step(Math.min(SEARCH_SLICE, share - done));
                    if (System.nanoTime() - started > SEARCH_NANOS) {
                        break;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.error("The search for the way of a road failed", e);
                iterator.remove();
                reply(server, job, RoadViews.PlanView.failed(job.kingdomId, KingdomError.NO_ROUTE.id()));
                continue;
            }
            if (status == RouteSearch.Status.RUNNING) {
                continue;
            }
            iterator.remove();
            if (status == RouteSearch.Status.FOUND) {
                Plan plan = new Plan(job.id, job.kingdomId, job.player, Dimensions.id(job.level), job.from, job.to, job.search.route(), now);
                PLANS.put(plan.id(), plan);
                LOGGER.info("A way for a road of {} found: {} blocks after {} steps", RoadWork.kingdomName(server, job.kingdomId).getString(),
                        plan.cells().size(), job.search.expanded());
                reply(server, job, view(server, plan, job.viewClass));
                if (job.buildClass != null) {
                    Outcome built = build(server, null, job.kingdomId, plan.id(), job.buildClass, job.buildMode, new Road[1]);
                    if (!built.ok()) {
                        LOGGER.warn("The road of {} could not be built: {}", RoadWork.kingdomName(server, job.kingdomId).getString(),
                                built.error());
                    }
                }
            } else {
                LOGGER.info("No way for a road of {}: {} after {} steps", RoadWork.kingdomName(server, job.kingdomId).getString(),
                        job.search.failure(), job.search.expanded());
                reply(server, job, RoadViews.PlanView.failed(job.kingdomId, failure(job.search.failure()).id()));
            }
        }
    }

    static KingdomError failure(RouteSearch.Failure failure) {
        return failure == RouteSearch.Failure.TOO_FAR ? KingdomError.ROUTE_TOO_LONG : KingdomError.NO_ROUTE;
    }

    private static void reply(MinecraftServer server, Job job, RoadViews.PlanView view) {
        if (job.player == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player != null) {
            KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.PlanResponse(view));
        }
    }

    /**
     * Whether a search for a kingdom runs, for a road of its own or of the plan of the states.
     */
    public static boolean searching(UUID kingdomId) {
        return PlannedRoads.searching(kingdomId) || JOBS.values().stream().anyMatch(job -> job.kingdomId.equals(kingdomId));
    }

    /**
     * Searches running, the one for a road of the plan of the states counted.
     */
    static int jobCount() {
        return JOBS.size() + (PlannedRoads.searching() ? 1 : 0);
    }

    /**
     * Searches the way of a road at once, loading what it needs; for operators and game tests.
     */
    public static Optional<Plan> searchNow(ServerLevel level, UUID kingdomId, int fromX, int fromZ, int toX, int toZ, RoadRules rules) {
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Endpoint from = endpoint(server, dimension, fromX, fromZ);
        Endpoint to = endpoint(server, dimension, toX, toZ);
        RoadTerrain terrain = new RoadTerrain(level);
        RouteSearch search = new RouteSearch(terrain, from.x(), from.z(), to.x(), to.z(), kingdomId, rules);
        // No tick passes in this loop: the chunks are read at once, as the search asks for them.
        boolean before = loadNow;
        loadNow = true;
        try {
            while (search.status() == RouteSearch.Status.RUNNING) {
                terrain.allow(Integer.MAX_VALUE);
                search.step(rules.maxNodes());
            }
        } finally {
            loadNow = before;
        }
        if (search.status() != RouteSearch.Status.FOUND) {
            return Optional.empty();
        }
        Plan plan = new Plan(nextId++, kingdomId, null, dimension, from, to, search.route(), KingdomWorldApi.gameTime(server));
        PLANS.put(plan.id(), plan);
        return Optional.of(plan);
    }

    /**
     * What the atlas shows of a plan of the old search, in a class: its length, bridges and embankments, its materials and the
     * seconds of its builders; no earthworks.
     */
    public static RoadViews.PlanView view(MinecraftServer server, Plan plan, RoadClass roadClass) {
        RoadRules rules = RoadSettings.rules();
        List<RoadLayout.Block> blocks = RoadLayout.blocks(plan.cells(), roadClass);
        int[] materials = RoadLayout.materials(blocks, roadClass);
        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.round(RoadLayout.seconds(blocks, roadClass, rules)));
        int[] works = new int[RoadViews.PlanView.WORKS];
        for (RouteCell cell : plan.cells()) {
            if (cell.kind() == RouteCell.Kind.BRIDGE) {
                works[7]++;
            } else if (cell.kind() == RouteCell.Kind.FILL) {
                works[6]++;
            }
        }
        List<String> crossed = new ArrayList<>();
        for (UUID kingdom : plan.crossed().keySet()) {
            crossed.add(RoadWork.kingdomName(server, kingdom).getString());
        }
        int[][] way = way(plan.cells());
        RouteCell first = plan.cells().get(0);
        RouteCell last = plan.cells().get(plan.cells().size() - 1);
        int length = plan.cells().size();
        int straight = (int) Math.round(Math.hypot(last.x() - first.x(), last.z() - first.z()));
        double pace = RoadActions.pace();
        int builders = RoadBuilding.freeBuilders(server, plan.kingdomId()).size();
        double rate = RoadBuilding.builderRate(plan.kingdomId());
        int term = rate <= 0 && builders == 0 ? -1 : (int) Math.round(seconds / Math.max(0.1, rate > 0 ? rate : builders)
                / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY * 10);
        return new RoadViews.PlanView(plan.id(), plan.kingdomId(), 0, "", RoadViews.PlanView.Stage.DONE, 100, 0, roadClass, plan.style(),
                new int[]{first.x(), first.z(), last.x(), last.z()}, new int[]{first.x(), first.z(), last.x(), last.z()}, List.of(),
                name(server, plan.from()).getString(), name(server, plan.to()).getString(), length, straight,
                (int) Math.round(io.github.sekerator.kingdomworld.core.road.RoadNetwork.ticks(length, pace * rules.caravanSpeed(roadClass)) / 20),
                (int) Math.round(io.github.sekerator.kingdomworld.core.road.RoadNetwork.ticks(straight, pace) / 20), works, materials,
                missing(server, plan.kingdomId(), materials, roadClass), seconds, 0, builders, 0, term, 0, "", crossed, 0, false, false,
                way[0], way[1], kinds(way[2]), new int[0]);
    }

    /**
     * Points of a way to draw, {@value RoadViews#MAX_POINTS} at most, with the kind of block at each.
     */
    static int[][] way(List<RouteCell> cells) {
        int step = Math.max(1, (cells.size() + RoadViews.MAX_POINTS - 2) / (RoadViews.MAX_POINTS - 1));
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < cells.size(); i += step) {
            RouteCell cell = cells.get(i);
            points.add(new int[]{cell.x(), cell.z(), cell.kind().ordinal()});
        }
        RouteCell last = cells.get(cells.size() - 1);
        points.add(new int[]{last.x(), last.z(), last.kind().ordinal()});
        int[] xs = new int[points.size()];
        int[] zs = new int[points.size()];
        int[] kinds = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
            kinds[i] = points.get(i)[2];
        }
        return new int[][]{xs, zs, kinds};
    }

    private static byte[] kinds(int[] kinds) {
        byte[] bytes = new byte[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            bytes[i] = (byte) kinds[i];
        }
        return bytes;
    }

    /**
     * Items of each material the storehouses of a kingdom lack for a road, by the tags of the materials.
     */
    static int[] missing(MinecraftServer server, UUID kingdomId, int[] materials, RoadClass roadClass) {
        int[] missing = new int[materials.length];
        Map<String, Long> stock = io.github.sekerator.kingdomworld.common.storage.KingdomStorage.countsById(server, kingdomId);
        for (RoadLayout.Material material : RoadLayout.Material.values()) {
            int need = materials[material.ordinal()];
            if (need <= 0) {
                continue;
            }
            long have = 0;
            for (Map.Entry<String, Long> entry : stock.entrySet()) {
                net.minecraft.world.item.Item item = io.github.sekerator.kingdomworld.common.citizen.work.PostWork.item(entry.getKey());
                if (item != null && RoadBlocks.fits(item, material, roadClass)) {
                    have += entry.getValue();
                }
            }
            missing[material.ordinal()] = (int) Math.max(0, need - have);
        }
        return missing;
    }

    /**
     * What the work and the items of a road are worth in the currency of a kingdom, only for comparison: the wages of its builders
     * and miners for the seconds of their work, and every item at the market value of the item of its tag the storehouses hold most
     * of, else the first of the tag.
     */
    static long coins(MinecraftServer server, UUID kingdomId, double builderSeconds, double minerSeconds, int[] materials, RoadClass roadClass) {
        java.util.Optional<io.github.sekerator.kingdomworld.core.economy.Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        io.github.sekerator.kingdomworld.core.economy.EconomyRules rules = io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules();
        double sum = 0;
        if (treasury.isPresent()) {
            sum += builderSeconds * treasury.get().wage(io.github.sekerator.kingdomworld.core.citizen.Professions.BUILDER.id(), rules)
                    / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY;
            sum += minerSeconds * treasury.get().wage(io.github.sekerator.kingdomworld.core.citizen.Professions.MINER.id(), rules)
                    / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY;
        }
        Map<String, Long> stock = io.github.sekerator.kingdomworld.common.storage.KingdomStorage.countsById(server, kingdomId);
        for (RoadLayout.Material material : RoadLayout.Material.values()) {
            int count = materials[material.ordinal()];
            if (count <= 0) {
                continue;
            }
            net.minecraft.world.item.Item best = null;
            long most = -1;
            for (Map.Entry<String, Long> entry : stock.entrySet()) {
                net.minecraft.world.item.Item item = io.github.sekerator.kingdomworld.common.citizen.work.PostWork.item(entry.getKey());
                if (item != null && RoadBlocks.fits(item, material, roadClass) && entry.getValue() > most) {
                    best = item;
                    most = entry.getValue();
                }
            }
            if (best == null) {
                for (net.minecraft.core.Holder<net.minecraft.world.item.Item> holder : net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getTagOrEmpty(RoadBlocks.tagOf(material, roadClass))) {
                    best = holder.value();
                    break;
                }
            }
            if (best != null) {
                sum += count * io.github.sekerator.kingdomworld.common.trade.MarketStock.worth(server, best).amount();
            }
        }
        return Math.round(sum);
    }

    /**
     * Keeps a levelled plan found by the planner for its player to confirm.
     */
    static Plan keep(MinecraftServer server, UUID kingdomId, @Nullable UUID player, String dimension, RoadClass roadClass,
                     io.github.sekerator.kingdomworld.core.road.RouteStyle style, List<int[]> points,
                     io.github.sekerator.kingdomworld.core.road.RoutePlan.Result result,
                     @Nullable io.github.sekerator.kingdomworld.core.road.LandSnapshot snapshot) {
        List<RouteCell> cells = result.cells();
        RouteCell first = cells.get(0);
        RouteCell last = cells.get(cells.size() - 1);
        int[] start = points.get(0);
        int[] end = points.get(points.size() - 1);
        Endpoint from = endpoint(server, dimension, start[0], start[1]);
        Endpoint to = endpoint(server, dimension, end[0], end[1]);
        if (from.kind() == Endpoint.Kind.POINT) {
            from = Endpoint.point(first.x(), first.z());
        }
        if (to.kind() == Endpoint.Kind.POINT) {
            to = Endpoint.point(last.x(), last.z());
        }
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, null);
        List<UUID> owners = new ArrayList<>(blocks.size());
        for (RoadLayout.Block block : blocks) {
            owners.add(snapshot == null ? KingdomWorldApi.ownerOfChunk(server.overworld(), block.x() >> 4, block.z() >> 4)
                    : snapshot.owner(block.x() >> 4, block.z() >> 4));
        }
        // The walls of its own cities the road crosses outside a gate: a gate is cut there when it is confirmed.
        List<long[]> gates = new ArrayList<>();
        for (io.github.sekerator.kingdomworld.core.road.Wall wall : RoadWork.state(server).walls()) {
            if (!wall.dimension().equals(dimension) || !wall.kingdomId().equals(kingdomId)) {
                continue;
            }
            java.util.Set<Long> ring = new java.util.HashSet<>();
            for (RouteCell cell : wall.cells()) {
                ring.add(io.github.sekerator.kingdomworld.core.road.LandSnapshot.column(cell.x(), cell.z()));
            }
            boolean inside = false;
            for (RouteCell cell : cells) {
                boolean on = ring.contains(io.github.sekerator.kingdomworld.core.road.LandSnapshot.column(cell.x(), cell.z()));
                if (on && !inside && !wall.inGate(wall.nearestCell(cell.x(), cell.z()))) {
                    gates.add(new long[]{wall.id(), cell.x(), cell.z()});
                }
                inside = on;
            }
        }
        long now = KingdomWorldApi.gameTime(server);
        Plan plan = new Plan(nextId++, kingdomId, player, dimension, from, to, cells, now, RoadLayout.LEVELLED, roadClass, style, result.surveys(),
                result.earthTotal(), result.guessedColumns() > 0, owners, gates, result.estimate());
        PLANS.put(plan.id(), plan);
        return plan;
    }

    /**
     * What the atlas shows of a levelled plan, or of why it failed.
     */
    static RoadViews.PlanView levelledView(MinecraftServer server, UUID kingdomId, int seq, RoadClass roadClass,
                                            io.github.sekerator.kingdomworld.core.road.RouteStyle style, List<int[]> points,
                                            io.github.sekerator.kingdomworld.core.road.RoutePlan.Result result, @Nullable Plan plan, int queue) {
        int[] given = new int[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            given[i * 2] = points.get(i)[0];
            given[i * 2 + 1] = points.get(i)[1];
        }
        int[] snapped = new int[result.snapped().size() * 2];
        for (int i = 0; i < result.snapped().size(); i++) {
            snapped[i * 2] = result.snapped().get(i)[0];
            snapped[i * 2 + 1] = result.snapped().get(i)[1];
        }
        List<RoadViews.PlanView.LegView> legs = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            io.github.sekerator.kingdomworld.core.road.LegPlanner.Leg leg = i < result.legs().size() ? result.legs().get(i) : null;
            io.github.sekerator.kingdomworld.core.road.RoadEstimate estimate = i < result.estimates().size() ? result.estimates().get(i) : null;
            boolean failed = i == result.failedLeg();
            String reason = failed ? result.failure().id() : "";
            int[] stuck = failed && result.stuck() != null ? result.stuck() : new int[2];
            RoadViews.PlanView.Stage stage = failed ? RoadViews.PlanView.Stage.FAILED : leg != null && leg.found()
                    ? RoadViews.PlanView.Stage.DONE : RoadViews.PlanView.Stage.IDLE;
            legs.add(new RoadViews.PlanView.LegView(stage, reason, stuck[0], stuck[1], estimate == null ? 0 : (int) Math.round(estimate.length()),
                    estimate == null ? 0 : (int) Math.round(estimate.builders()), estimate == null ? 0 : (int) Math.round(estimate.miners()),
                    estimate == null ? new int[RoadViews.PlanView.WORKS] : works(estimate)));
        }
        if (!result.found() || plan == null) {
            RoadViews.PlanView base = RoadViews.PlanView.of(kingdomId, seq, "", RoadViews.PlanView.Stage.FAILED, 100, queue, roadClass, style);
            return new RoadViews.PlanView(0, kingdomId, seq, "", RoadViews.PlanView.Stage.FAILED, 100, queue, roadClass, style, given, snapped, legs,
                    "", "", 0, 0, 0, 0, base.works(), base.materials(), base.missing(), 0, 0, 0, 0, -1, 0, "", List.of(), 0, false, true, new int[0],
                    new int[0], new byte[0], new int[0]);
        }
        io.github.sekerator.kingdomworld.core.road.RoadEstimate estimate = result.estimate();
        int[] materials = estimate.materials();
        double rate = RoadBuilding.builderRate(kingdomId);
        int builders = RoadBuilding.freeBuilders(server, kingdomId).size();
        if (rate <= 0) {
            rate = builders;
        }
        double minerRate = RoadWork.state(server).minersHelp(kingdomId) ? RoadCrews.speed(kingdomId) : 0;
        double seconds = estimate.time(rate, minerRate, RoadSettings.earth());
        int term = Double.isFinite(seconds) ? (int) Math.round(seconds / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY * 10) : -1;
        List<String> foreign = new ArrayList<>();
        for (Map.Entry<UUID, double[]> entry : estimate.foreign().entrySet()) {
            foreign.add(RoadWork.kingdomName(server, entry.getKey()).getString());
        }
        for (UUID kingdom : plan.crossed().keySet()) {
            String name = RoadWork.kingdomName(server, kingdom).getString();
            if (!foreign.contains(name)) {
                foreign.add(name);
            }
        }
        String currency = KingdomWorldApi.state(server).economy().treasury(kingdomId)
                .map(io.github.sekerator.kingdomworld.core.economy.Treasury::currencyName).orElse("");
        int[][] way = way(plan.cells());
        return new RoadViews.PlanView(plan.id(), kingdomId, seq, "", RoadViews.PlanView.Stage.DONE, 100, 0, roadClass, style, given, snapped, legs,
                name(server, plan.from()).getString(), name(server, plan.to()).getString(), (int) Math.round(estimate.length()),
                (int) Math.round(estimate.straight()), (int) Math.round(estimate.caravanTicks() / 20), (int) Math.round(estimate.straightTicks() / 20),
                works(estimate), materials, missing(server, kingdomId, materials, roadClass), (int) Math.round(estimate.builders()),
                (int) Math.round(estimate.miners()), builders, (int) Math.round(minerRate * 100), term,
                coins(server, kingdomId, estimate.builders(), estimate.miners(), materials, roadClass), currency, foreign, plan.gates().size(),
                plan.guessed(), true, way[0], way[1], kinds(way[2]), profile(plan, RoadSettings.earth()));
    }

    static int[] works(io.github.sekerator.kingdomworld.core.road.RoadEstimate estimate) {
        return new int[]{estimate.logs(), estimate.soil(), estimate.rock(), estimate.fill(), estimate.cutCells(), estimate.tunnelCells(),
                estimate.fillCells(), estimate.bridgeCells(), estimate.pierBlocks(), estimate.lining(), estimate.torches()};
    }

    /**
     * Probes of the profile of a plan, {@value RoadViews.PlanView#MAX_PROBES} at most: the ground, the deck and the kind.
     */
    static int[] profile(Plan plan, io.github.sekerator.kingdomworld.core.road.EarthRules earth) {
        List<RouteCell> cells = plan.cells();
        int count = Math.min(RoadViews.PlanView.MAX_PROBES, cells.size());
        int[] probes = new int[count * 3];
        for (int i = 0; i < count; i++) {
            RouteCell cell = cells.get((int) ((long) i * (cells.size() - 1) / Math.max(1, count - 1)));
            int ground = switch (cell.kind()) {
                case CUT -> cell.y() + cell.depth();
                case TUNNEL -> cell.y() + earth.tunnelHeight() + cell.depth();
                case FILL -> cell.y() - cell.depth() - 1;
                case BRIDGE -> cell.depth() > 0 ? cell.y() - cell.depth() - 1 : cell.y() - 2;
                default -> cell.y();
            };
            probes[i * 3] = ground;
            probes[i * 3 + 1] = cell.y();
            probes[i * 3 + 2] = cell.kind().ordinal();
        }
        return probes;
    }

    /**
     * Turns a plan into a road: building starts at once when it crosses no other kingdom, otherwise each of them gets a
     * request.
     *
     * @param actor the player confirming, who needs the right to run construction; {@code null} for an operator
     * @param made  receives the road
     */
    public static Outcome build(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long planId, RoadClass roadClass, Road.Mode mode,
                                Road[] made) {
        Plan plan = PLANS.get(planId);
        if (plan == null || !plan.kingdomId().equals(kingdomId)) {
            return Outcome.fail(KingdomError.UNKNOWN_PLAN);
        }
        if (!RoadWork.mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (plan.levelled() && plan.roadClass() != roadClass) {
            return Outcome.fail(KingdomError.ROAD_CLASS_CHANGED);
        }
        if (plan.cells().size() > Road.MAX_CELLS) {
            // A road keeps no more blocks than this: refused here rather than failing inside the change.
            return Outcome.fail(KingdomError.ROUTE_TOO_LONG);
        }
        RoadRules rules = RoadSettings.rulesFor(server, kingdomId);
        List<Crossing> sent = new ArrayList<>();
        long now = KingdomWorldApi.gameTime(server);
        Outcome outcome = RoadWork.change(server, "plan a road", roads -> {
            if (rules.roadsFull(roads.ownedRoads(kingdomId))) {
                return Outcome.fail(KingdomError.TOO_MANY_ROADS);
            }
            made[0] = roads.addRoad(kingdomId, roadClass, plan.dimension(), plan.from(), plan.to(), plan.cells(), plan.sectionOwners(),
                    plan.crossed(), mode, now, now + (long) rules.crossingDays() * CitizenNeeds.TICKS_PER_DAY, sent, plan.layout(),
                    plan.levelled() ? plan.surveys() : null);
            if (plan.levelled()) {
                made[0].setEarthTotal(plan.earthTotal());
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return outcome;
        }
        PLANS.remove(planId);
        Road road = made[0];
        CityWalls.gatesFor(server, road);
        for (long[] gate : plan.gates()) {
            Outcome cut = CityWalls.gateAt(server, actor, gate[0], (int) gate[1], (int) gate[2]);
            if (!cut.ok()) {
                LOGGER.info("The gate of road {} in wall {} at {} {} was not ordered: {}", road.id(), gate[0], gate[1], gate[2], cut.error());
            }
        }
        LOGGER.info("Road {} of {} planned: {} blocks, {}, {} request(s) to cross land", road.id(),
                RoadWork.kingdomName(server, kingdomId).getString(), road.length(), road.roadClass().id(), sent.size());
        if (plan.levelled() && plan.estimate() != null) {
            io.github.sekerator.kingdomworld.core.road.RoadEstimate estimate = plan.estimate();
            RoadWork.notify(server, kingdomId, Component.translatable(RoadWork.KEY + "road_planned_levelled", road.id(),
                    RoadTexts.className(road.roadClass()), RoadTexts.blocks(road.length()), estimate.soil() + estimate.rock(), estimate.fill(),
                    estimate.tunnelCells(), estimate.bridgeCells()).withStyle(ChatFormatting.GREEN));
        } else {
            RoadWork.notify(server, kingdomId, Component.translatable(RoadWork.KEY + "road_planned", road.id(),
                    RoadTexts.className(road.roadClass()), RoadTexts.blocks(road.length())).withStyle(ChatFormatting.GREEN));
        }
        for (Crossing crossing : sent) {
            double[] works = plan.levelled() && plan.estimate() != null ? plan.estimate().foreign().get(crossing.owner()) : null;
            if (works != null) {
                // The works on the land of the kingdom asked, in days of its builders and miners.
                RoadWork.notify(server, crossing.owner(), Component.translatable(RoadWork.KEY + "crossing_asked_levelled",
                        RoadWork.kingdomName(server, kingdomId), RoadTexts.sections(crossing.sections()), RoadTexts.mode(crossing.mode()),
                        String.format(java.util.Locale.ROOT, "%.1f", works[0] / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY),
                        String.format(java.util.Locale.ROOT, "%.1f", works[1] / io.github.sekerator.kingdomworld.core.road.EarthRules.DAY))
                        .withStyle(ChatFormatting.GOLD));
                continue;
            }
            RoadWork.notify(server, crossing.owner(), Component.translatable(RoadWork.KEY + "crossing_asked",
                    RoadWork.kingdomName(server, kingdomId), RoadTexts.sections(crossing.sections()),
                    RoadTexts.mode(crossing.mode())).withStyle(ChatFormatting.GOLD));
        }
        RoadSync.changed();
        return Outcome.DONE;
    }

    /**
     * A road was given up because a kingdom declined or did not answer.
     */
    static void dropped(MinecraftServer server, Road road) {
        RoadBuilding.giveBackPools(server, road);
        RoadWork.notify(server, road.kingdomId(), Component.translatable(RoadWork.KEY + "road_dropped", road.id())
                .withStyle(ChatFormatting.GOLD));
        RoadSync.changed();
    }
}
