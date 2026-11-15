package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.ChunkHold;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.Wilderness;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CoarseRoute;
import io.github.sekerator.kingdomworld.core.road.EarthRules;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.LandColumn;
import io.github.sekerator.kingdomworld.core.road.LandGrid;
import io.github.sekerator.kingdomworld.core.road.LandSnapshot;
import io.github.sekerator.kingdomworld.core.road.LandTile;
import io.github.sekerator.kingdomworld.core.road.LegPlanner;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RoutePlan;
import io.github.sekerator.kingdomworld.core.road.RouteStyle;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The planner of levelled roads: a session for every player planning one, its legs planned off the main thread by
 * {@code plannerThreads} threads named "KingdomWorld road planner", one session after another. The main thread copies what they
 * read: the snapshot of owners, frames, roads and walls, and the blocks of loaded chunks, within {@code plannerMillisPerTick} a tick;
 * land neither loaded nor read is guessed from the noise of the generator, so that planning never generates the world. Only when a
 * plan over guessed land is confirmed is that land loaded, a few chunks a tick with tickets of its own taken off as soon as it is
 * copied, and the road planned again on it and laid.
 *
 * <p>A session lives fifteen minutes after its last request, until its player leaves or goes to another dimension. A request with
 * new points plans again only the legs that changed. The plans found are kept with those of the old search, so that confirming them
 * works alike.
 */
public final class RoadPlanSessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks a session lives after its last request. */
    static final long LIFETIME = 20L * 60 * 15;
    /** Ticks a chunk to be read may take to load. */
    static final int LOAD_TICKS = 1_200;
    /** Chunks loading for the planner at once, all sessions together. */
    static final int LOADING_AT_ONCE = 12;
    /** Ticks between two views of the progress of a session. */
    static final int PROGRESS_EVERY = 20;
    /** Tickets of the chunks the planner loads, each session with its own key. */
    static final TicketType<Long> TICKET = TicketType.create("kingdomworld_road_survey", Long::compare, 300);

    /**
     * A plan being made for a player.
     */
    static final class Session {
        final long id;
        final UUID player;
        final UUID kingdomId;
        final ServerLevel level;
        final String dimension;
        int seq;
        RoadClass roadClass;
        RouteStyle style;
        List<int[]> points = List.of();
        long lastRequest;
        volatile RoadViews.PlanView.Stage stage = RoadViews.PlanView.Stage.QUEUED;
        volatile int progress;
        final Map<String, LegPlanner.Leg> legs = new ConcurrentHashMap<>();
        AtomicBoolean cancel = new AtomicBoolean();
        volatile RoutePlan.Result result;
        volatile RoutePlanJob job;
        int order;
        /** Whether the land may be loaded and generated: the plan was confirmed over guessed land. */
        boolean survey;
        /** What to build once the plan over loaded land is found. */
        Road.Mode buildMode;
        RoadViews.PlanView last;

        Session(long id, UUID player, UUID kingdomId, ServerLevel level) {
            this.id = id;
            this.player = player;
            this.kingdomId = kingdomId;
            this.level = level;
            this.dimension = Dimensions.id(level);
        }
    }

    /**
     * Chunks a planner thread asks the main thread for.
     */
    static final class Ask {
        final Session session;
        final Set<Long> remaining;
        final Map<Long, LandScan.Copy> copies = new HashMap<>();
        final Set<Long> guess = new HashSet<>();
        final Map<Long, Long> loading = new HashMap<>();
        final CompletableFuture<Boolean> done = new CompletableFuture<>();

        Ask(Session session, Set<Long> chunks) {
            this.session = session;
            this.remaining = new LinkedHashSet<>(chunks);
        }
    }

    /**
     * What a planner thread does for a session.
     */
    static final class RoutePlanJob {
        final List<RoutePlan.Point> points;
        final RoadClass roadClass;
        final RouteStyle style;
        final LandSnapshot snapshot;
        final Map<Long, float[]> loaded;
        final LandGrid grid;
        final boolean survey;

        RoutePlanJob(List<RoutePlan.Point> points, RoadClass roadClass, RouteStyle style, LandSnapshot snapshot, Map<Long, float[]> loaded,
                     boolean survey) {
            this.points = points;
            this.roadClass = roadClass;
            this.style = style;
            this.snapshot = snapshot;
            this.loaded = loaded;
            this.grid = new LandGrid(snapshot);
            this.survey = survey;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final ConcurrentLinkedQueue<Ask> ASKS = new ConcurrentLinkedQueue<>();
    private static final List<Ask> SERVING = new ArrayList<>();
    private static final AtomicInteger ORDER = new AtomicInteger();
    private static final AtomicInteger STARTED = new AtomicInteger();
    private static ExecutorService executor;
    private static long nextId = 1;
    private static int ticketsTick = Integer.MIN_VALUE;
    private static int ticketsGiven;

    private RoadPlanSessions() {
    }

    // ---------------------------------------------------------------- the threads

    static synchronized ExecutorService executor() {
        if (executor == null) {
            int threads = RoadSettings.earth().plannerThreads();
            AtomicInteger count = new AtomicInteger();
            executor = Executors.newFixedThreadPool(threads, task -> {
                Thread thread = new Thread(task, "KingdomWorld road planner" + (threads > 1 ? " " + count.incrementAndGet() : ""));
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    /**
     * Stops every plan and the threads, when the server stops.
     */
    static synchronized void reset(@Nullable MinecraftServer server) {
        for (Session session : SESSIONS.values()) {
            session.cancel.set(true);
        }
        for (Ask ask : SERVING) {
            release(ask);
            ask.done.complete(false);
        }
        SERVING.clear();
        Ask ask;
        while ((ask = ASKS.poll()) != null) {
            ask.done.complete(false);
        }
        SESSIONS.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        CoarseLand.reset();
    }

    // ---------------------------------------------------------------- requests

    /**
     * A player asks for a plan through points, or with none gives it up.
     */
    public static void request(ServerPlayer player, UUID kingdomId, int seq, RoadClass roadClass, RouteStyle style, int[] xs, int[] zs) {
        MinecraftServer server = player.server;
        Session session = SESSIONS.get(player.getUUID());
        if (xs.length == 0) {
            if (session != null) {
                session.cancel.set(true);
                SESSIONS.remove(player.getUUID());
            }
            return;
        }
        if (session != null && (seq < session.seq || !session.kingdomId.equals(kingdomId) || session.level != player.serverLevel())) {
            if (seq < session.seq) {
                return;
            }
            session.cancel.set(true);
            SESSIONS.remove(player.getUUID());
            session = null;
        }
        if (session == null) {
            session = new Session(nextId++, player.getUUID(), kingdomId, player.serverLevel());
            SESSIONS.put(player.getUUID(), session);
        }
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            points.add(new int[]{xs[i], zs[i]});
        }
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i)[0] == points.get(i - 1)[0] && points.get(i)[1] == points.get(i - 1)[1]) {
                send(player, RoadViews.PlanView.of(kingdomId, seq, io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NO_ROUTE.id(),
                        RoadViews.PlanView.Stage.FAILED, 0, 0, roadClass, style));
                return;
            }
        }
        session.seq = seq;
        session.roadClass = roadClass;
        session.style = style;
        session.points = points;
        session.lastRequest = KingdomWorldApi.gameTime(server);
        session.survey = false;
        session.buildMode = null;
        start(server, session);
    }

    /**
     * The plan of a player is confirmed over land that was only guessed: its land is read, the road planned on it and laid.
     */
    static boolean survey(MinecraftServer server, UUID player, long planId, Road.Mode mode) {
        Session session = SESSIONS.get(player);
        if (session == null || session.result == null || session.last == null || session.last.planId() != planId) {
            return false;
        }
        session.survey = true;
        session.buildMode = mode;
        session.lastRequest = KingdomWorldApi.gameTime(server);
        start(server, session);
        return true;
    }

    /**
     * Starts or restarts the planning of a session with its points: the legs planned already stay.
     */
    private static void start(MinecraftServer server, Session session) {
        session.cancel.set(true);
        session.cancel = new AtomicBoolean();
        session.result = null;
        session.stage = RoadViews.PlanView.Stage.QUEUED;
        session.progress = 0;
        session.order = ORDER.incrementAndGet();
        RoutePlanJob job = prepare(server, session);
        session.job = job;
        AtomicBoolean cancel = session.cancel;
        Session target = session;
        executor().submit(() -> run(target, job, cancel));
        sendProgress(server, session);
    }

    // ---------------------------------------------------------------- the snapshot

    /**
     * Copies on the main thread what the planner reads besides the blocks: owners, frames, roads, walls, the ends at cities.
     */
    private static RoutePlanJob prepare(MinecraftServer server, Session session) {
        ServerLevel level = session.level;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double longest = 0;
        for (int i = 0; i < session.points.size(); i++) {
            int[] at = session.points.get(i);
            minX = Math.min(minX, at[0]);
            minZ = Math.min(minZ, at[1]);
            maxX = Math.max(maxX, at[0]);
            maxZ = Math.max(maxZ, at[1]);
            if (i > 0) {
                int[] before = session.points.get(i - 1);
                longest = Math.max(longest, Math.hypot(at[0] - before[0], at[1] - before[1]));
            }
        }
        int margin = (int) Math.max(128, 0.35 * longest) + 64;
        int cx0 = (minX - margin) >> 4;
        int cz0 = (minZ - margin) >> 4;
        int cx1 = (maxX + margin) >> 4;
        int cz1 = (maxZ + margin) >> 4;
        String dimension = session.dimension;
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Map<Long, UUID> owners = new HashMap<>();
        Map<Long, float[]> loaded = new HashMap<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkKey.of(cx, cz);
                UUID owner = territory.ownerOf(dimension, key);
                if (owner != null && !Wilderness.isWild(server, dimension, key)) {
                    owners.put(LandSnapshot.chunk(cx, cz), owner);
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    loaded.put(LandSnapshot.chunk(cx, cz), CoarseLand.summary(chunk));
                }
            }
        }
        List<int[]> frames = new ArrayList<>();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        for (io.github.sekerator.kingdomworld.core.construction.Building building : book.buildings()) {
            Box box = building.box();
            if (box != null && building.anchor().dimension().equals(dimension) && !BuildingRoles.WALL.equals(building.role())
                    && box.maxX() >= cx0 * 16 && box.minX() <= cx1 * 16 + 15 && box.maxZ() >= cz0 * 16 && box.minZ() <= cz1 * 16 + 15) {
                frames.add(new int[]{box.minX(), box.minZ(), box.maxX(), box.maxZ()});
            }
        }
        for (io.github.sekerator.kingdomworld.core.construction.Construction construction : book.all()) {
            Box box = construction.box();
            if (construction.dimension().equals(dimension) && box.maxX() >= cx0 * 16 && box.minX() <= cx1 * 16 + 15 && box.maxZ() >= cz0 * 16
                    && box.minZ() <= cz1 * 16 + 15) {
                frames.add(new int[]{box.minX(), box.minZ(), box.maxX(), box.maxZ()});
            }
        }
        RoadState state = RoadWork.state(server);
        Map<Long, Long> roads = new HashMap<>();
        for (Road road : state.roads()) {
            if (!road.dimension().equals(dimension)) {
                continue;
            }
            boolean own = road.kingdomId().equals(session.kingdomId);
            for (RoadLayout.Block block : road.blocks()) {
                if (block.x() >> 4 >= cx0 && block.x() >> 4 <= cx1 && block.z() >> 4 >= cz0 && block.z() >> 4 <= cz1) {
                    roads.putIfAbsent(LandSnapshot.column(block.x(), block.z()), LandSnapshot.road(road.id(), block.y(), own));
                }
            }
        }
        Map<Long, Integer> walls = new HashMap<>();
        for (Wall wall : state.walls()) {
            if (!wall.dimension().equals(dimension)) {
                continue;
            }
            boolean own = wall.kingdomId().equals(session.kingdomId);
            Set<Integer> gates = new HashSet<>();
            for (int gate : wall.gates()) {
                for (int d = -Wall.GATE_HALF; d <= Wall.GATE_HALF; d++) {
                    gates.add(Math.floorMod(gate + d, wall.cells().size()));
                }
            }
            for (int i = 0; i < wall.cells().size(); i++) {
                io.github.sekerator.kingdomworld.core.road.RouteCell cell = wall.cells().get(i);
                int flag = gates.contains(i) ? LandColumn.GATE : own ? LandColumn.WALL : LandColumn.FOREIGN_WALL;
                walls.put(LandSnapshot.column(cell.x(), cell.z()), LandSnapshot.wall(cell.y(), flag));
            }
        }
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        LandSnapshot snapshot = new LandSnapshot(session.kingdomId, owners, frames, roads, walls, new int[]{(int) Math.floor(border.getMinX()),
                (int) Math.floor(border.getMinZ()), (int) Math.ceil(border.getMaxX()), (int) Math.ceil(border.getMaxZ())},
                new int[]{level.getMinBuildHeight(), level.getMaxBuildHeight(), level.getSeaLevel()});
        List<RoutePlan.Point> points = new ArrayList<>();
        for (int i = 0; i < session.points.size(); i++) {
            int[] at = session.points.get(i);
            boolean end = i == 0 || i == session.points.size() - 1;
            points.add(end ? endPoint(server, session, at[0], at[1]) : RoutePlan.Point.at(at[0], at[1]));
        }
        return new RoutePlanJob(points, session.roadClass, session.style, snapshot, loaded, session.survey);
    }

    /**
     * An end of the road: at a city or a capital in {@link RoadPlanning#SNAP} blocks, the roads of the kingdom there and the gates of
     * its wall at their heights (the free land round it is added by the planner when it has read it); otherwise the point itself.
     */
    static RoutePlan.Point endPoint(MinecraftServer server, Session session, int x, int z) {
        Endpoint endpoint = RoadPlanning.endpoint(server, session.dimension, x, z);
        if (endpoint.kind() == Endpoint.Kind.POINT) {
            return RoutePlan.Point.at(x, z);
        }
        List<int[]> targets = new ArrayList<>();
        RoadState state = RoadWork.state(server);
        int cx = endpoint.x();
        int cz = endpoint.z();
        int reach = RoadPlanning.SNAP;
        for (Road road : state.roads()) {
            if (!road.dimension().equals(session.dimension) || !road.kingdomId().equals(session.kingdomId)) {
                continue;
            }
            for (io.github.sekerator.kingdomworld.core.road.RouteCell cell : road.cells()) {
                if (Math.abs(cell.x() - cx) <= reach && Math.abs(cell.z() - cz) <= reach) {
                    targets.add(new int[]{cell.x(), cell.z(), cell.y()});
                }
            }
        }
        int inside = 0;
        if (endpoint.kind() == Endpoint.Kind.CITY) {
            for (Wall wall : state.walls()) {
                if (wall.cityId() == endpoint.city() && wall.kingdomId().equals(session.kingdomId)) {
                    for (int gate : wall.gates()) {
                        io.github.sekerator.kingdomworld.core.road.RouteCell cell = wall.cells().get(gate);
                        targets.add(new int[]{cell.x(), cell.z(), cell.y()});
                    }
                    // A city with a wall is reached within it: the road goes through the wall, at a gate or where one is cut.
                    int radius = Integer.MAX_VALUE;
                    for (io.github.sekerator.kingdomworld.core.road.RouteCell cell : wall.cells()) {
                        radius = Math.min(radius, Math.max(Math.abs(cell.x() - cx), Math.abs(cell.z() - cz)));
                    }
                    inside = radius == Integer.MAX_VALUE ? 0 : radius;
                }
            }
        }
        // The middle of the city marks the end for the planner, which adds the free land round it, within its wall if it has one.
        targets.add(new int[]{cx, cz, Integer.MIN_VALUE, inside});
        return new RoutePlan.Point(cx, cz, targets);
    }

    // ---------------------------------------------------------------- off the main thread

    /**
     * Plans a session on a planner thread.
     */
    private static void run(Session session, RoutePlanJob job, AtomicBoolean cancel) {
        if (cancel.get()) {
            return;
        }
        STARTED.incrementAndGet();
        try {
            session.stage = RoadViews.PlanView.Stage.COARSE;
            SessionLand land = new SessionLand(session, job, cancel);
            List<RoutePlan.Point> points = new ArrayList<>();
            for (RoutePlan.Point point : job.points) {
                points.add(point.city() ? land.cityTargets(point) : point);
            }
            RoadRules rules = RoadSettings.rules();
            EarthRules earth = RoadSettings.earth();
            RoutePlan.Result result = RoutePlan.plan(land, points, job.roadClass, job.style, rules, earth, session.kingdomId, session.legs,
                    cancel::get, done -> {
                        session.progress = done;
                        session.stage = RoadViews.PlanView.Stage.SEARCHING;
                    }, RoadActions.pace());
            if (!cancel.get()) {
                session.result = result;
                session.stage = result.found() ? RoadViews.PlanView.Stage.DONE : RoadViews.PlanView.Stage.FAILED;
            }
        } catch (RuntimeException e) {
            LOGGER.error("The planner of roads failed", e);
            if (!cancel.get()) {
                session.result = new RoutePlan.Result(List.of(), List.of(), List.of(), List.of(), io.github.sekerator.kingdomworld.core.road.RoadEstimate.EMPTY,
                        List.of(), 0, LegPlanner.Reason.BLOCKED, 0, new int[]{0, 0}, 0);
                session.stage = RoadViews.PlanView.Stage.FAILED;
            }
        } finally {
            STARTED.decrementAndGet();
        }
    }

    /**
     * The land of a session for its planner thread: the tiles it has read, the main thread asked for the rest.
     */
    static class SessionLand implements LegPlanner.Land {
        private final Session session;
        private final RoutePlanJob job;
        private final AtomicBoolean cancel;

        SessionLand(Session session, RoutePlanJob job, AtomicBoolean cancel) {
            this.session = session;
            this.job = job;
            this.cancel = cancel;
        }

        @Override
        public LandColumn at(int x, int z) {
            return job.grid.at(x, z);
        }

        @Override
        public boolean read(Set<Long> chunks) {
            Set<Long> missing = new LinkedHashSet<>();
            for (long key : chunks) {
                if (!job.grid.has((int) (key >> 32), (int) key)) {
                    missing.add(key);
                }
            }
            if (missing.isEmpty()) {
                return true;
            }
            session.stage = RoadViews.PlanView.Stage.READING;
            Ask ask = new Ask(session, missing);
            ASKS.add(ask);
            boolean ok;
            try {
                while (true) {
                    if (cancel.get()) {
                        ask.done.complete(false);
                        return false;
                    }
                    try {
                        ok = ask.done.get(100, TimeUnit.MILLISECONDS);
                        break;
                    } catch (java.util.concurrent.TimeoutException e) {
                        // Waits on, looking whether it was given up.
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                return false;
            }
            for (LandScan.Copy copy : ask.copies.values()) {
                job.grid.put(LandScan.read(copy, job.snapshot));
            }
            for (long key : ask.guess) {
                job.grid.put(CoarseLand.guessTile(session.level, session.dimension, job.snapshot, (int) (key >> 32), (int) key));
            }
            session.stage = RoadViews.PlanView.Stage.SEARCHING;
            return ok;
        }

        @Override
        public UUID owner(int x, int z) {
            return job.snapshot.owner(x >> 4, z >> 4);
        }

        @Override
        public CoarseRoute.Field field(int cx0, int cz0, int cx1, int cz1) {
            session.stage = RoadViews.PlanView.Stage.COARSE;
            return CoarseLand.field(session.level, session.dimension, job.snapshot, job.grid, job.loaded, cx0, cz0, cx1, cz1, session.kingdomId,
                    cancel::get);
        }

        @Override
        public boolean guessed(int chunkX, int chunkZ) {
            LandTile tile = job.grid.tile(chunkX, chunkZ);
            return tile != null && tile.guessed();
        }

        @Override
        public long roadAt(int x, int z) {
            long value = job.snapshot.roadAt(x, z);
            return value < 0 ? -1 : LandSnapshot.roadId(value);
        }

        @Override
        public int minY() {
            return job.snapshot.minY();
        }

        @Override
        public int maxY() {
            return job.snapshot.maxY();
        }

        /**
         * The end at a city with the free land round its middle added: natural columns within 24 blocks, out of every frame.
         */
        RoutePlan.Point cityTargets(RoutePlan.Point point) {
            List<int[]> targets = new ArrayList<>();
            int[] middle = null;
            for (int[] target : point.targets()) {
                if (target[2] == Integer.MIN_VALUE) {
                    middle = target;
                } else {
                    targets.add(target);
                }
            }
            if (middle != null) {
                int reach = 24;
                Set<Long> chunks = new HashSet<>();
                for (int dx = -reach; dx <= reach; dx += 16) {
                    for (int dz = -reach; dz <= reach; dz += 16) {
                        chunks.add(LandSnapshot.chunk((middle[0] + dx) >> 4, (middle[1] + dz) >> 4));
                    }
                }
                chunks.add(LandSnapshot.chunk((middle[0] + reach) >> 4, (middle[1] + reach) >> 4));
                read(chunks);
                // The free land on a ring round the middle: enough places to arrive at, not so many that the search loses time.
                int wall = middle.length > 3 ? middle[3] : 0;
                for (int dx = -reach; dx <= reach; dx += 2) {
                    for (int dz = -reach; dz <= reach; dz += 2) {
                        int distance = Math.max(Math.abs(dx), Math.abs(dz));
                        if (wall > 0 ? distance < 2 || distance >= wall - 1 : distance < 12) {
                            continue;
                        }
                        LandColumn column = at(middle[0] + dx, middle[1] + dz);
                        if (column != null && !column.closed() && !column.water() && !column.has(LandColumn.LAVA)
                                && !column.has(LandColumn.ROAD)) {
                            targets.add(new int[]{middle[0] + dx, middle[1] + dz, column.top()});
                        }
                    }
                }
            }
            return new RoutePlan.Point(point.x(), point.z(), targets);
        }
    }

    // ---------------------------------------------------------------- the main thread

    /**
     * Copies what the planner threads asked for, within the time of a tick, and hands the plans found to their players. Called every
     * tick.
     */
    public static void tick(MinecraftServer server) {
        long now = KingdomWorldApi.gameTime(server);
        for (Iterator<Session> iterator = SESSIONS.values().iterator(); iterator.hasNext(); ) {
            Session session = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (player == null || player.serverLevel() != session.level || now - session.lastRequest > LIFETIME) {
                session.cancel.set(true);
                iterator.remove();
                continue;
            }
            RoutePlan.Result result = session.result;
            if (result != null && session.last != null && session.last.seq() == session.seq && !session.last.searching()) {
                continue;
            }
            if (result != null) {
                finish(server, session, player, result);
            } else if (server.getTickCount() % PROGRESS_EVERY == 0) {
                sendProgress(server, session);
            }
        }
        serve(server);
    }

    /**
     * Copies the chunks asked for, {@code plannerMillisPerTick} a tick at most.
     */
    private static void serve(MinecraftServer server) {
        Ask fresh;
        while ((fresh = ASKS.poll()) != null) {
            SERVING.add(fresh);
        }
        if (SERVING.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + RoadSettings.earth().plannerMillisPerTick() * 1_000_000L;
        int tick = server.getTickCount();
        if (tick != ticketsTick) {
            ticketsTick = tick;
            ticketsGiven = 0;
        }
        int perTick = RoadSettings.rules().chunksPerTick();
        boolean slow = ChunkHold.busy(server);
        int loading = 0;
        for (Ask ask : SERVING) {
            loading += ask.loading.size();
        }
        for (Iterator<Ask> iterator = SERVING.iterator(); iterator.hasNext(); ) {
            Ask ask = iterator.next();
            if (ask.done.isDone() || ask.session.cancel.get()) {
                release(ask);
                ask.done.complete(false);
                iterator.remove();
                continue;
            }
            ServerLevel level = ask.session.level;
            for (Iterator<Long> chunks = ask.remaining.iterator(); chunks.hasNext() && System.nanoTime() < deadline; ) {
                long key = chunks.next();
                int cx = (int) (key >> 32);
                int cz = (int) key;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    ask.copies.put(key, LandScan.copy(chunk));
                    Long since = ask.loading.remove(key);
                    if (since != null) {
                        // Copied: the ticket goes at once.
                        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(cx, cz), 0, ask.session.id);
                        loading--;
                    }
                    chunks.remove();
                    continue;
                }
                if (!ask.session.survey) {
                    // A plan being looked at never loads or generates the world: the land is guessed.
                    ask.guess.add(key);
                    chunks.remove();
                    continue;
                }
                Long since = ask.loading.get(key);
                if (since != null) {
                    if (tick - since > LOAD_TICKS) {
                        release(ask);
                        ask.done.complete(false);
                        break;
                    }
                    continue;
                }
                // On a busy server a chunk now and then rather than none: slower, but the road gets laid.
                boolean allowed = slow ? tick % PROGRESS_EVERY == 0 && ticketsGiven == 0 : ticketsGiven < perTick;
                if (allowed && loading < LOADING_AT_ONCE) {
                    level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(cx, cz), 0, ask.session.id);
                    ask.loading.put(key, (long) tick);
                    ticketsGiven++;
                    loading++;
                }
            }
            if (!ask.done.isDone() && ask.remaining.isEmpty()) {
                ask.done.complete(true);
            }
            if (ask.done.isDone()) {
                iterator.remove();
            }
        }
    }

    private static void release(Ask ask) {
        for (long key : ask.loading.keySet()) {
            ask.session.level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos((int) (key >> 32), (int) key), 0, ask.session.id);
        }
        ask.loading.clear();
    }

    /**
     * A plan was found or not: it is kept as a plan to confirm and shown to its player.
     */
    private static void finish(MinecraftServer server, Session session, ServerPlayer player, RoutePlan.Result result) {
        RoadPlanning.Plan plan = null;
        if (result.found()) {
            plan = RoadPlanning.keep(server, session.kingdomId, session.player, session.dimension, session.roadClass, session.style,
                    session.points, result, session.job == null ? null : session.job.snapshot);
        }
        RoadViews.PlanView view = RoadPlanning.levelledView(server, session.kingdomId, session.seq, session.roadClass, session.style,
                session.points, result, plan, order(session));
        session.last = view;
        send(player, view);
        if (plan != null && session.survey && session.buildMode != null) {
            Road.Mode mode = session.buildMode;
            session.survey = false;
            session.buildMode = null;
            Road[] made = new Road[1];
            io.github.sekerator.kingdomworld.core.kingdom.Outcome built = RoadPlanning.build(server, session.player, session.kingdomId, plan.id(),
                    session.roadClass, mode, made);
            if (!built.ok()) {
                io.github.sekerator.kingdomworld.common.kingdom.KingdomActions.reply(player,
                        io.github.sekerator.kingdomworld.common.kingdom.KingdomActions.Feedback.error(built.error()));
            }
        }
    }

    private static int order(Session session) {
        int before = 0;
        for (Session other : SESSIONS.values()) {
            if (other != session && other.result == null && other.order < session.order) {
                before++;
            }
        }
        return Math.max(0, before - Math.max(0, RoadSettings.earth().plannerThreads() - 1));
    }

    private static void sendProgress(MinecraftServer server, Session session) {
        ServerPlayer player = server.getPlayerList().getPlayer(session.player);
        if (player == null) {
            return;
        }
        RoadViews.PlanView.Stage stage = session.survey ? RoadViews.PlanView.Stage.SURVEYING : session.stage;
        RoadViews.PlanView view = RoadViews.PlanView.of(session.kingdomId, session.seq, "", stage, session.progress, order(session),
                session.roadClass, session.style);
        session.last = view;
        send(player, view);
    }

    static void send(ServerPlayer player, RoadViews.PlanView view) {
        KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.PlanResponse(view));
    }

    // ---------------------------------------------------------------- tests

    /**
     * Plans a road at once in the calling thread, reading the chunks it needs as it goes; for game tests and the self-test of the
     * client.
     */
    public static RoutePlan.Result planNow(ServerLevel level, UUID kingdomId, List<int[]> points, RoadClass roadClass, RouteStyle style) {
        MinecraftServer server = level.getServer();
        Session session = new Session(nextId++, new UUID(0, 0), kingdomId, level);
        session.points = points;
        session.roadClass = roadClass;
        session.style = style;
        RoutePlanJob job = prepare(server, session);
        AtomicBoolean cancel = new AtomicBoolean();
        SessionLand land = new SessionLand(session, job, cancel) {
            @Override
            public boolean read(Set<Long> chunks) {
                for (long key : chunks) {
                    int cx = (int) (key >> 32);
                    int cz = (int) key;
                    if (!job.grid.has(cx, cz)) {
                        LevelChunk chunk = level.getChunk(cx, cz);
                        job.grid.put(LandScan.read(LandScan.copy(chunk), job.snapshot));
                    }
                }
                return true;
            }
        };
        List<RoutePlan.Point> resolved = new ArrayList<>();
        for (RoutePlan.Point point : job.points) {
            resolved.add(point.city() ? land.cityTargets(point) : point);
        }
        RoutePlan.Result result = RoutePlan.plan(land, resolved, roadClass, style, RoadSettings.rules(), RoadSettings.earth(), kingdomId,
                new HashMap<>(), () -> false, done -> {
                }, RoadActions.pace());
        session.job = job;
        if (result.found()) {
            lastTestPlan = RoadPlanning.keep(server, kingdomId, null, session.dimension, roadClass, style, points, result, job.snapshot);
        }
        return result;
    }

    /** The plan the last planning at once kept, for game tests. */
    public static RoadPlanning.Plan lastTestPlan;

    /**
     * Sessions planning now, for diagnostics.
     */
    public static int active() {
        return SESSIONS.size();
    }
}
