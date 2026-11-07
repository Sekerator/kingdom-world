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
    /** Searches running at once at most. */
    private static final int MAX_JOBS = 4;

    /**
     * A way found for a road.
     */
    public record Plan(long id, UUID kingdomId, @Nullable UUID player, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells,
                       long made) {
        public Plan {
            cells = List.copyOf(cells);
        }

        public Map<UUID, Integer> crossed() {
            return RoadState.crossedSections(cells, kingdomId);
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
     * Starts the search for the way of a road that is built in a class as soon as the way is found; for operators.
     */
    public static Outcome startAndBuild(ServerLevel level, UUID kingdomId, int fromX, int fromZ, int toX, int toZ, RoadClass roadClass,
                                        Road.Mode mode) {
        return start(level, null, kingdomId, fromX, fromZ, toX, toZ, roadClass, mode);
    }

    private static Outcome start(ServerLevel level, @Nullable UUID player, UUID kingdomId, int fromX, int fromZ, int toX, int toZ,
                                 @Nullable RoadClass buildClass, @Nullable Road.Mode buildMode) {
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
        if (JOBS.size() >= MAX_JOBS) {
            return Outcome.fail(KingdomError.ROUTE_SEARCHING);
        }
        RoadRules rules = RoadSettings.rules();
        String dimension = Dimensions.id(level);
        Endpoint from = endpoint(server, dimension, fromX, fromZ);
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
        while (iterator.hasNext()) {
            Job job = iterator.next();
            job.terrain.allow(chunks);
            RouteSearch.Status status;
            try {
                status = job.search.step(share);
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
                reply(server, job, view(server, plan));
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
     * Whether a search for a kingdom runs.
     */
    public static boolean searching(UUID kingdomId) {
        return JOBS.values().stream().anyMatch(job -> job.kingdomId.equals(kingdomId));
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
        while (search.status() == RouteSearch.Status.RUNNING) {
            terrain.allow(Integer.MAX_VALUE);
            search.step(rules.maxNodes());
        }
        if (search.status() != RouteSearch.Status.FOUND) {
            return Optional.empty();
        }
        Plan plan = new Plan(nextId++, kingdomId, null, dimension, from, to, search.route(), KingdomWorldApi.gameTime(server));
        PLANS.put(plan.id(), plan);
        return Optional.of(plan);
    }

    /**
     * What the atlas shows of a plan.
     */
    public static RoadViews.PlanView view(MinecraftServer server, Plan plan) {
        RoadRules rules = RoadSettings.rules();
        int[] materials = new int[9];
        int[] seconds = new int[3];
        for (RoadClass roadClass : RoadClass.values()) {
            List<RoadLayout.Block> blocks = RoadLayout.blocks(plan.cells(), roadClass);
            int[] counts = RoadLayout.materials(blocks, roadClass);
            for (int m = 0; m < 3; m++) {
                materials[roadClass.ordinal() * 3 + m] = counts[m];
            }
            seconds[roadClass.ordinal()] = (int) Math.min(Integer.MAX_VALUE, Math.round(RoadLayout.seconds(blocks, roadClass, rules)));
        }
        int bridges = 0;
        int fills = 0;
        for (RouteCell cell : plan.cells()) {
            if (cell.kind() == RouteCell.Kind.BRIDGE) {
                bridges++;
            } else if (cell.kind() == RouteCell.Kind.FILL) {
                fills++;
            }
        }
        List<String> crossed = new ArrayList<>();
        for (UUID kingdom : plan.crossed().keySet()) {
            crossed.add(RoadWork.kingdomName(server, kingdom).getString());
        }
        List<RouteCell> cells = plan.cells();
        int step = Math.max(1, cells.size() / 1_000);
        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();
        for (int i = 0; i < cells.size(); i += step) {
            xs.add(cells.get(i).x());
            zs.add(cells.get(i).z());
        }
        xs.add(cells.get(cells.size() - 1).x());
        zs.add(cells.get(cells.size() - 1).z());
        return new RoadViews.PlanView(plan.id(), plan.kingdomId(), "", false, name(server, plan.from()).getString(),
                name(server, plan.to()).getString(), cells.size(), bridges, fills, materials, seconds,
                RoadBuilding.freeBuilders(server, plan.kingdomId()).size(), crossed, xs.stream().mapToInt(Integer::intValue).toArray(),
                zs.stream().mapToInt(Integer::intValue).toArray());
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
        RoadRules rules = RoadSettings.rules();
        List<Crossing> sent = new ArrayList<>();
        long now = KingdomWorldApi.gameTime(server);
        Outcome outcome = RoadWork.change(server, "plan a road", roads -> {
            if (roads.ownedRoads(kingdomId) >= rules.maxRoads()) {
                return Outcome.fail(KingdomError.TOO_MANY_ROADS);
            }
            made[0] = roads.addRoad(kingdomId, roadClass, plan.dimension(), plan.from(), plan.to(), plan.cells(),
                    RoadState.sectionOwners(plan.cells()), plan.crossed(), mode, now, now + (long) rules.crossingDays() * CitizenNeeds.TICKS_PER_DAY,
                    sent);
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return outcome;
        }
        PLANS.remove(planId);
        Road road = made[0];
        LOGGER.info("Road {} of {} planned: {} blocks, {}, {} request(s) to cross land", road.id(),
                RoadWork.kingdomName(server, kingdomId).getString(), road.length(), road.roadClass().id(), sent.size());
        RoadWork.notify(server, kingdomId, Component.translatable(RoadWork.KEY + "road_planned", road.id(), RoadTexts.className(road.roadClass()),
                RoadTexts.blocks(road.length())).withStyle(ChatFormatting.GREEN));
        for (Crossing crossing : sent) {
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
