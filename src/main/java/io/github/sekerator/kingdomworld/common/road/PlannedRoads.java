package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.PlannedRoad;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CorridorSearch;
import io.github.sekerator.kingdomworld.core.road.CorridorTerrain;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadNetwork;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The roads of the plan of the states become real roads when they are wanted: both their settlements stand and a player is
 * within {@value #NEAR_CHUNKS} chunks of the way, or, without a player near, one now and then in the background. One road of the
 * plan is searched at a time, along its rough line (see {@link CorridorSearch}), counted among the searches for roads of
 * {@link RoadPlanning}, one a kingdom; the road found is laid as the roads of the founding of a kingdom of the world are,
 * paid as part of the founding and placed as its chunks are loaded. A road the search cannot find waits a day and says so
 * in the log.
 */
public final class PlannedRoads {
    /** Chunks from the way of a road of the plan within which a player makes it wanted. */
    public static final int NEAR_CHUNKS = 12;
    /** Ticks between two looks for a road of the plan to lay. */
    private static final int LOOK_TICKS = 100;
    /** Ticks between two roads laid in the background, without a player near. */
    private static final int BACKGROUND_TICKS = 20 * 120;
    /** Blocks of the line of a road of the plan left out at a capital: the road begins outside its walls. */
    private static final int CAPITAL_MARGIN = RoadPlanning.SNAP;
    /** Time the search takes in a tick at most. */
    private static final long SEARCH_NANOS = 3_000_000L;
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Job job;
    private static long lastBackground = Long.MIN_VALUE / 2;
    /** Roads of the plan that could not be found, with the tick they are looked at again. */
    private static final Map<Integer, Long> WAITING = new HashMap<>();

    private record Job(int planned, UUID kingdomId, ServerLevel level, Endpoint from, Endpoint to, RoadTerrain terrain, CorridorSearch search) {
    }

    private PlannedRoads() {
    }

    static void reset() {
        job = null;
        lastBackground = Long.MIN_VALUE / 2;
        WAITING.clear();
    }

    /**
     * Whether a search for a road of the plan runs.
     */
    static boolean searching() {
        return job != null;
    }

    static boolean searching(UUID kingdomId) {
        return job != null && job.kingdomId().equals(kingdomId);
    }

    /**
     * The roads of the plan of a dimension not laid yet, as tracks for caravans.
     */
    public static List<RoadNetwork.Track> tracks(MinecraftServer server, String dimension) {
        if (!"minecraft:overworld".equals(dimension) || NpcWork.isReadOnly(server)) {
            return List.of();
        }
        List<RoadNetwork.Track> tracks = new ArrayList<>();
        RoadState roads = RoadWork.state(server);
        for (PlannedRoad planned : NpcWork.state(server).plannedRoads()) {
            Optional<Road> laid = planned.roadId() == 0 ? Optional.empty() : roads.road(planned.roadId());
            if (laid.isEmpty() || laid.get().paid() == 0) {
                tracks.add(new RoadNetwork.Track(planned.xs(), planned.zs()));
            }
        }
        return tracks;
    }

    static void tick(MinecraftServer server) {
        if (job != null) {
            step(server);
            return;
        }
        if (server.getTickCount() % LOOK_TICKS != 41 || RoadWork.isReadOnly(server) || NpcWork.isReadOnly(server)
                || RoadPlanning.jobCount() >= RoadPlanning.MAX_JOBS) {
            return;
        }
        begin(server);
    }

    /**
     * Whether a settlement stands in the world: raised, or the capital of a kingdom of the world that stands.
     */
    public static boolean stands(NpcState npc, Settlement settlement) {
        if (settlement.status() == Settlement.Status.STANDING) {
            return true;
        }
        if (settlement.tier() != Tier.CAPITAL || settlement.status() == Settlement.Status.RUINED) {
            return false;
        }
        Optional<NpcKingdom> kingdom = npc.kingdom(settlement.kingdomId());
        Optional<NpcSite> site = kingdom.flatMap(k -> npc.site(k.siteId()));
        return site.isPresent() && site.get().status() == NpcSite.Status.SPAWNED;
    }

    private static void begin(MinecraftServer server) {
        NpcState npc = NpcWork.state(server);
        ServerLevel level = server.overworld();
        long now = KingdomWorldApi.gameTime(server);
        PlannedRoad chosen = null;
        boolean near = false;
        for (PlannedRoad planned : npc.plannedRoads()) {
            if (planned.roadId() != 0 || WAITING.getOrDefault(planned.id(), Long.MIN_VALUE) > now) {
                continue;
            }
            Optional<Settlement> from = npc.settlement(planned.from());
            Optional<Settlement> to = npc.settlement(planned.to());
            if (from.isEmpty() || to.isEmpty() || !stands(npc, from.get()) || !stands(npc, to.get())
                    || RoadPlanning.searching(from.get().kingdomId())) {
                continue;
            }
            boolean wanted = playerNear(level, planned);
            if (wanted) {
                chosen = planned;
                near = true;
                break;
            }
            if (chosen == null) {
                chosen = planned;
            }
        }
        if (chosen == null || (!near && now - lastBackground < BACKGROUND_TICKS)) {
            return;
        }
        if (!near) {
            lastBackground = now;
        }
        start(server, chosen);
    }

    private static boolean playerNear(ServerLevel level, PlannedRoad planned) {
        if (level.players().isEmpty()) {
            return false;
        }
        CorridorTerrain line = new CorridorTerrain((x, z) -> null, planned.xs(), planned.zs(), NEAR_CHUNKS * 16);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && line.inside(player.getBlockX(), player.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the search for a road of the plan; for the background, operators and game tests.
     */
    public static Outcome start(MinecraftServer server, PlannedRoad planned) {
        NpcState npc = NpcWork.state(server);
        Optional<Settlement> from = npc.settlement(planned.from());
        Optional<Settlement> to = npc.settlement(planned.to());
        if (from.isEmpty() || to.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_PLAN);
        }
        if (job != null) {
            return Outcome.fail(KingdomError.ROUTE_SEARCHING);
        }
        int[] xs = planned.xs();
        int[] zs = planned.zs();
        int[][] line = trimmed(xs, zs, from.get().tier() == Tier.CAPITAL ? CAPITAL_MARGIN : 0, to.get().tier() == Tier.CAPITAL ? CAPITAL_MARGIN : 0);
        ServerLevel level = server.overworld();
        RoadTerrain terrain = new RoadTerrain(level);
        RoadRules rules = RoadSettings.rules();
        UUID builder = from.get().kingdomId();
        job = new Job(planned.id(), builder, level, endpoint(server, from.get(), line[0][0], line[1][0]),
                endpoint(server, to.get(), line[0][line[0].length - 1], line[1][line[1].length - 1]), terrain,
                new CorridorSearch(terrain, line[0], line[1], builder, rules));
        LOGGER.info("The road of the plan {} between the settlements {} and {} is searched for along its line", planned.id(), planned.from(),
                planned.to());
        return Outcome.DONE;
    }

    /**
     * The line of a road without so many blocks at its start and its end, where a capital stands.
     */
    static int[][] trimmed(int[] xs, int[] zs, int start, int end) {
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            points.add(new int[]{xs[i], zs[i]});
        }
        if (start > 0) {
            cut(points, start);
        }
        if (end > 0) {
            java.util.Collections.reverse(points);
            cut(points, end);
            java.util.Collections.reverse(points);
        }
        int[] outX = new int[points.size()];
        int[] outZ = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            outX[i] = points.get(i)[0];
            outZ[i] = points.get(i)[1];
        }
        return new int[][]{outX, outZ};
    }

    /**
     * Leaves out so many blocks of the start of a line, keeping two points at least.
     */
    private static void cut(List<int[]> points, int blocks) {
        double left = blocks;
        while (points.size() > 2) {
            int[] a = points.get(0);
            int[] b = points.get(1);
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (length > left) {
                double t = left / length;
                points.set(0, new int[]{(int) Math.round(a[0] + (b[0] - a[0]) * t), (int) Math.round(a[1] + (b[1] - a[1]) * t)});
                return;
            }
            left -= length;
            points.remove(0);
        }
        int[] a = points.get(0);
        int[] b = points.get(1);
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double t = Math.min(0.4, left / Math.max(1, length));
        points.set(0, new int[]{(int) Math.round(a[0] + (b[0] - a[0]) * t), (int) Math.round(a[1] + (b[1] - a[1]) * t)});
    }

    private static Endpoint endpoint(MinecraftServer server, Settlement settlement, int x, int z) {
        if (settlement.tier() == Tier.CAPITAL) {
            Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(settlement.kingdomId()).map(Territory::capital);
            if (capital.isPresent()) {
                return Endpoint.capital(settlement.kingdomId(), capital.get().x(), capital.get().z());
            }
        }
        if (settlement.cityId() != 0) {
            Optional<City> city = RoadWork.state(server).city(settlement.cityId());
            if (city.isPresent()) {
                return Endpoint.city(city.get());
            }
        }
        return Endpoint.point(x, z);
    }

    private static void step(MinecraftServer server) {
        Job at = job;
        RoadRules rules = RoadSettings.rules();
        at.terrain().allow(Math.max(1, rules.chunksPerTick() / Math.max(1, RoadPlanning.jobCount() + 1)));
        long started = System.nanoTime();
        CorridorSearch.Status status = at.search().status();
        try {
            for (int done = 0; done < rules.nodesPerTick() && status == CorridorSearch.Status.RUNNING; done += 100) {
                status = at.search().step(Math.min(100, rules.nodesPerTick() - done));
                if (System.nanoTime() - started > SEARCH_NANOS) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("The search for a road of the plan failed", e);
            status = CorridorSearch.Status.FAILED;
        }
        if (status == CorridorSearch.Status.RUNNING) {
            return;
        }
        job = null;
        long now = KingdomWorldApi.gameTime(server);
        if (status == CorridorSearch.Status.FAILED) {
            WAITING.put(at.planned(), now + CitizenNeeds.TICKS_PER_DAY);
            LOGGER.warn("No way for the road of the plan {}: {} after {} steps ({} stretches of {}, {} point(s) moved aside); it waits a day",
                    at.planned(), at.search().failure(), at.search().expanded(), at.search().stretch(), at.search().stretchCount(),
                    at.search().shifted());
            return;
        }
        Outcome laid = lay(server, at, at.search().route(), now);
        if (!laid.ok()) {
            WAITING.put(at.planned(), now + CitizenNeeds.TICKS_PER_DAY);
            LOGGER.warn("The road of the plan {} was found but not laid: {}", at.planned(), laid.error());
        }
    }

    /**
     * Lays a road along a way found and pays its blocks as part of the founding, once: the road of the plan knows its road.
     */
    private static Outcome lay(MinecraftServer server, Job at, List<RouteCell> cells, long now) {
        if (cells.isEmpty()) {
            return Outcome.fail(KingdomError.NO_ROUTE);
        }
        Optional<PlannedRoad> planned = NpcWork.state(server).plannedRoads().stream().filter(road -> road.id() == at.planned()).findFirst();
        if (planned.isEmpty() || planned.get().roadId() != 0) {
            return Outcome.fail(KingdomError.UNKNOWN_PLAN);
        }
        RoadRules rules = RoadSettings.rulesFor(server, at.kingdomId());
        Road[] made = new Road[1];
        List<Crossing> sent = new ArrayList<>();
        Outcome outcome = RoadWork.change(server, "lay a road of the plan", roads -> {
            if (rules.roadsFull(roads.ownedRoads(at.kingdomId()))) {
                return Outcome.fail(KingdomError.TOO_MANY_ROADS);
            }
            made[0] = roads.addRoad(at.kingdomId(), planned.get().roadClass(), "minecraft:overworld", at.from(), at.to(), cells,
                    RoadState.sectionOwners(cells), RoadState.crossedSections(cells, at.kingdomId()), Road.Mode.OWN, now,
                    now + (long) rules.crossingDays() * CitizenNeeds.TICKS_PER_DAY, sent);
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return outcome;
        }
        Road road = made[0];
        Outcome remembered = NpcWork.change(server, "remember the road of a road of the plan", npc -> {
            npc.setRoadOf(at.planned(), road.id());
            return Outcome.DONE;
        });
        if (!remembered.ok()) {
            return remembered;
        }
        io.github.sekerator.kingdomworld.common.npc.NpcSpawner.payRoad(server, at.level(), road);
        // The road comes into the towns of its ends through gates of their walls.
        CityWalls.gatesFor(server, road);
        LOGGER.info("The road of the plan {} is laid as road {}: {} blocks, {} request(s) to cross land, the most steps of a stretch {}",
                at.planned(), road.id(), road.length(), sent.size(), at.search().mostInStretch());
        RoadSync.changed();
        return Outcome.DONE;
    }
}
