package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.SitePick;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finishes constructions and walls far from players: one place at a time on the whole server is loaded for a while, through a
 * {@link ChunkHold}, and what its builders paid for is put in, within about two milliseconds a tick, so that a building of a
 * kingdom nobody visits is finished, registered and lived in without a player near it. Which place comes next decides
 * {@link SitePick}; the kingdoms of players and those of the world take turns.
 *
 * <p>The chunks held are those of the place widened by a block (placing a block at its edge updates its neighbours), with the
 * columns of its scaffolds; of a wall, the next columns paid for. Before letting go, the beds, posts and storehouses of the new
 * building are entered into the data at once, as the scan of a loaded chunk does. The place is let go when its work is done, a
 * player came near (the work goes on as near any player), it did not load within {@value #WAIT_TICKS} ticks or was held
 * {@value #HOLD_TICKS} ticks; a place that failed waits before it is tried again. Items that waited to be dropped where nothing
 * was loaded are dropped here too, once.
 */
public final class SiteLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks between two looks for a place while none is served. */
    static final int PICK_EVERY = 100;
    /** Ticks a place may take to load. */
    static final int WAIT_TICKS = 1_200;
    /** Ticks a place is held at most. */
    static final int HOLD_TICKS = 2_400;
    /** Time the work of a place takes in a tick, about. */
    private static final long STEP_NANOS = 2_000_000L;
    /** Columns of a wall held at once. */
    static final int WALL_COLUMNS = 24;
    /** Ticks without progress after which a loaded place counts as done: what is left waits for something else. */
    private static final int IDLE_TICKS = 100;

    private static final SitePick PICK = new SitePick();
    /** The game time the first step now paid for of a construction was paid, by construction. */
    private static final Map<Long, Long> PENDING_SINCE = new HashMap<>();
    /** The same for the paid columns of walls, by wall. */
    private static final Map<Long, Long> WALL_SINCE = new HashMap<>();
    private static Target target;
    /** Steps put in a tick, worked out from the time they take. */
    private static double steps = 32;

    private static final class Target {
        final SitePick.Ref ref;
        final ChunkHold hold;
        final String dimension;
        /** The chunks whose places are scanned before letting go. */
        final Set<Long> scan;
        int ticks;
        int idle;
        boolean releasing;

        Target(SitePick.Ref ref, ChunkHold hold, String dimension, Set<Long> scan) {
            this.ref = ref;
            this.hold = hold;
            this.dimension = dimension;
            this.scan = scan;
        }
    }

    private SiteLoader() {
    }

    /**
     * Whether the construction is the place served now: the second tick of the constructions leaves its steps to this loader.
     */
    public static boolean serving(long constructionId) {
        Target current = target;
        return current != null && !current.releasing && current.ref.kind() == SitePick.Kind.CONSTRUCTION && current.ref.id() == constructionId;
    }

    /**
     * Whether the wall is the place served now.
     */
    public static boolean servingWall(long wallId) {
        Target current = target;
        return current != null && !current.releasing && current.ref.kind() == SitePick.Kind.WALL && current.ref.id() == wallId;
    }

    /**
     * Builders paid for the first step of a construction that waits to be put in: the wait counts from now.
     */
    public static void pendingGrew(Construction construction, long now) {
        PENDING_SINCE.putIfAbsent(construction.id(), now);
    }

    /**
     * Called every server tick.
     */
    static void tick(MinecraftServer server) {
        if (target != null) {
            step(server);
            return;
        }
        if (server.getTickCount() % PICK_EVERY != 0 || !ConstructionSettings.siteLoading() || KingdomWorldApi.isReadOnly(server)
                || ChunkHold.paused(server)) {
            return;
        }
        long now = KingdomWorldApi.gameTime(server);
        Map<SitePick.Ref, Place> places = new HashMap<>();
        List<SitePick.Candidate> candidates = candidates(server, now, places);
        Optional<SitePick.Candidate> chosen = PICK.pick(candidates, now);
        if (chosen.isEmpty()) {
            return;
        }
        Place place = places.get(chosen.get().ref());
        ServerLevel level = ConstructionWork.level(server, place.dimension());
        if (level == null) {
            PICK.defer(chosen.get().ref(), now);
            return;
        }
        target = new Target(chosen.get().ref(), new ChunkHold(level, place.hold()), place.dimension(), place.scan());
        LOGGER.debug("Loading the place of {} {} ({} chunks) to put in what was paid for", chosen.get().kind(), chosen.get().id(),
                place.hold().size());
    }

    private record Place(String dimension, Set<Long> hold, Set<Long> scan) {
    }

    private static void step(MinecraftServer server) {
        Target current = target;
        if (current.releasing) {
            if (current.hold.release()) {
                target = null;
            }
            return;
        }
        if (!ConstructionSettings.siteLoading() || KingdomWorldApi.isReadOnly(server)) {
            letGo(server, current, false);
            return;
        }
        current.hold.hold();
        current.ticks++;
        if (current.hold.visited()) {
            // A player is near: the place is loaded anyway and its work goes on as near any player.
            letGo(server, current, false);
            return;
        }
        if (!current.hold.ready()) {
            if (current.ticks > WAIT_TICKS) {
                letGo(server, current, true);
            }
            return;
        }
        if (current.ticks > HOLD_TICKS) {
            letGo(server, current, true);
            return;
        }
        if (ChunkHold.paused(server)) {
            return;
        }
        boolean done = switch (current.ref.kind()) {
            case CONSTRUCTION -> construction(server, current);
            case WALL -> wall(server, current);
            case DROPS -> drops(server, current);
        };
        if (done) {
            letGo(server, current, false);
        }
    }

    /**
     * Enters the places of the new building into the data, then lets the chunks go one by one.
     */
    private static void letGo(MinecraftServer server, Target current, boolean failed) {
        ServerLevel level = current.hold.level();
        if (!failed && !KingdomWorldApi.isReadOnly(server)) {
            for (long key : current.scan) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkKey.x(key), ChunkKey.z(key));
                if (chunk != null) {
                    CitizenSites.scanChunk(level, chunk);
                }
            }
        }
        if (failed) {
            PICK.defer(current.ref, KingdomWorldApi.gameTime(server));
            LOGGER.debug("The place of {} {} was let go unfinished after {} ticks", current.ref.kind(), current.ref.id(), current.ticks);
        }
        current.releasing = true;
    }

    // ---------------------------------------------------------------- the work of a place

    private static boolean construction(MinecraftServer server, Target current) {
        Optional<Construction> found = KingdomWorldApi.state(server).construction().get(current.ref.id());
        if (found.isEmpty()) {
            // Finished and entered into the register, or given up.
            return true;
        }
        Construction construction = found.get();
        Optional<BuildPlan> plan = ConstructionWork.plan(construction);
        if (plan.isEmpty() || construction.paused()) {
            return true;
        }
        if (!construction.pending().isEmpty()) {
            long start = System.nanoTime();
            int done = ConstructionWork.applyPending(current.hold.level(), construction, plan.get(), Math.max(1, (int) steps));
            adapt(System.nanoTime() - start);
            if (construction.pending().isEmpty()) {
                PENDING_SINCE.remove(construction.id());
            }
            if (done > 0) {
                current.idle = 0;
                return false;
            }
            // Blocked by a container, or waiting for cobblestone: what is left waits for something else.
            return ++current.idle > IDLE_TICKS;
        }
        if (ConstructionWork.finished(construction, plan.get())) {
            // The second tick takes the scaffolds down and enters the building into the register while the place is held.
            return ++current.idle > IDLE_TICKS;
        }
        // All that was paid for is in; the builders go on paying.
        return true;
    }

    private static boolean wall(MinecraftServer server, Target current) {
        Optional<Wall> found = RoadWork.state(server).wall(current.ref.id());
        if (found.isEmpty()) {
            return true;
        }
        Wall wall = found.get();
        long start = System.nanoTime();
        int done = 0;
        int budget = Math.max(1, (int) steps / 4);
        for (Road.Section section : wall.sections()) {
            if (done >= budget) {
                break;
            }
            done += io.github.sekerator.kingdomworld.common.road.CityWalls.placeSection(current.hold.level(), wall, section, budget - done);
        }
        adapt((System.nanoTime() - start) * 4);
        if (done > 0) {
            current.idle = 0;
            return false;
        }
        if (unplaced(wall) == 0) {
            WALL_SINCE.remove(wall.id());
        }
        // The next columns paid for lie beyond the chunks held, or none are left: another place is picked.
        return true;
    }

    private static boolean drops(MinecraftServer server, Target current) {
        ServerLevel level = current.hold.level();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        for (ConstructionBook.Drop drop : book.takeDrops(current.dimension, current.ref.id())) {
            Item item = PostWork.item(drop.item());
            if (item == null) {
                continue;
            }
            int left = drop.count();
            WorldPos at = new WorldPos(drop.dimension(), drop.x(), drop.y(), drop.z());
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                left -= count;
                // There may be room in the storehouses by now; what still does not fit lies at the place.
                ItemStack rest = KingdomStorage.insert(server, drop.kingdomId(), new ItemStack(item, count), at);
                if (!rest.isEmpty()) {
                    Containers.dropItemStack(level, drop.x() + 0.5, drop.y() + 0.5, drop.z() + 0.5, rest);
                }
            }
        }
        return true;
    }

    /**
     * Works out how many steps a tick puts in from the time the last ones took, as the raising of settlements does.
     */
    private static void adapt(long nanos) {
        if (nanos > STEP_NANOS) {
            steps = Math.max(1, steps * Math.max(0.1, 0.5 * STEP_NANOS / (double) nanos));
        } else if (nanos < STEP_NANOS / 2) {
            steps = Math.min(512, steps * 1.25);
        }
    }

    // ---------------------------------------------------------------- what may be loaded

    private static List<SitePick.Candidate> candidates(MinecraftServer server, long now, Map<SitePick.Ref, Place> places) {
        List<SitePick.Candidate> list = new ArrayList<>();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        Set<Long> seen = new java.util.HashSet<>();
        for (Construction construction : book.all()) {
            seen.add(construction.id());
            ServerLevel level = ConstructionWork.level(server, construction.dimension());
            if (level == null || construction.paused() || construction.status() == Construction.Status.OUTSIDE_LAND
                    || construction.status() == Construction.Status.TEMPLATE_MISSING || construction.status() == Construction.Status.TEMPLATE_CHANGED) {
                continue;
            }
            Optional<BuildPlan> plan = ConstructionWork.plan(construction);
            if (plan.isEmpty()) {
                continue;
            }
            int pending = construction.pending().size();
            int remaining = Math.max(0, plan.get().size() - construction.doneCount());
            boolean scaffoldsLeft = remaining == 0 && construction.hasScaffolds() && !scaffoldsLoaded(level, construction);
            if (pending == 0) {
                PENDING_SINCE.remove(construction.id());
                if (!scaffoldsLeft) {
                    continue;
                }
            } else if (ConstructionWork.loaded(level, construction)) {
                // The place is loaded: the second tick puts in what was paid for.
                continue;
            }
            if (!ConstructionWork.onOwnLand(server, construction)) {
                continue;
            }
            // A step paid for before the server started waits since long ago: older worlds get their constructions finished.
            long since = pending == 0 ? -1 : PENDING_SINCE.computeIfAbsent(construction.id(), id -> 0L);
            SitePick.Candidate candidate = new SitePick.Candidate(SitePick.Kind.CONSTRUCTION, construction.id(), construction.kingdomId(),
                    !NpcWork.isNpc(server, construction.kingdomId()), pending, remaining, since, scaffoldsLeft);
            list.add(candidate);
            places.put(candidate.ref(), new Place(construction.dimension(), chunksOf(construction), construction.box().chunks()));
        }
        PENDING_SINCE.keySet().retainAll(seen);
        Set<Long> walls = new java.util.HashSet<>();
        for (Wall wall : RoadWork.state(server).walls()) {
            walls.add(wall.id());
            ServerLevel level = ConstructionWork.level(server, wall.dimension());
            int unplaced = unplaced(wall);
            if (level == null || unplaced == 0) {
                WALL_SINCE.remove(wall.id());
                continue;
            }
            Set<Long> chunks = nextColumns(wall);
            if (chunks.isEmpty() || loadedAll(level, chunks)) {
                continue;
            }
            int remaining = 0;
            for (Road.Section section : wall.sections()) {
                remaining += wall.blocksOf(section).size() - section.placed();
            }
            long since = WALL_SINCE.computeIfAbsent(wall.id(), id -> 0L);
            SitePick.Candidate candidate = new SitePick.Candidate(SitePick.Kind.WALL, wall.id(), wall.kingdomId(),
                    !NpcWork.isNpc(server, wall.kingdomId()), unplaced, remaining, since, false);
            list.add(candidate);
            places.put(candidate.ref(), new Place(wall.dimension(), chunks, Set.of()));
        }
        WALL_SINCE.keySet().retainAll(walls);
        Map<String, Map<Long, ConstructionBook.Drop>> oldest = new HashMap<>();
        for (ConstructionBook.Drop drop : book.drops()) {
            oldest.computeIfAbsent(drop.dimension(), key -> new HashMap<>()).merge(drop.chunk(), drop,
                    (a, b) -> a.since() <= b.since() ? a : b);
        }
        oldest.forEach((dimension, chunks) -> chunks.forEach((chunk, drop) -> {
            ServerLevel level = ConstructionWork.level(server, dimension);
            if (level == null) {
                return;
            }
            SitePick.Candidate candidate = new SitePick.Candidate(SitePick.Kind.DROPS, chunk, drop.kingdomId(),
                    !NpcWork.isNpc(server, drop.kingdomId()), 0, 0, drop.since(), false);
            // Two dimensions may share a chunk number: the first one asked is served first, the other one the next time.
            if (!places.containsKey(candidate.ref())) {
                list.add(candidate);
                places.put(candidate.ref(), new Place(dimension, Set.of(chunk), Set.of()));
            }
        }));
        return list;
    }

    /**
     * The chunks held for a construction: its box and every column of its scaffolds, each widened by a block.
     */
    static Set<Long> chunksOf(Construction construction) {
        Box box = construction.box();
        Set<Long> chunks = ChunkHold.ringed(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        for (long cell : construction.scaffolds()) {
            BlockPos pos = BlockPos.of(cell);
            chunks.addAll(ChunkHold.ringed(pos.getX(), pos.getZ(), pos.getX(), pos.getZ()));
        }
        return chunks;
    }

    private static boolean scaffoldsLoaded(ServerLevel level, Construction construction) {
        for (long cell : construction.scaffolds()) {
            BlockPos pos = BlockPos.of(cell);
            if (!ChunkHold.loaded(level, pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean loadedAll(ServerLevel level, Set<Long> chunks) {
        for (long key : chunks) {
            if (!ChunkHold.loaded(level, ChunkKey.x(key), ChunkKey.z(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Columns of a wall paid for and not yet standing.
     */
    static int unplaced(Wall wall) {
        int sum = 0;
        for (Road.Section section : wall.sections()) {
            sum += Math.max(0, section.paid() - section.placed());
        }
        return sum;
    }

    /**
     * The chunks of the next columns of a wall paid for, {@value #WALL_COLUMNS} at most, each widened by a block.
     */
    static Set<Long> nextColumns(Wall wall) {
        Set<Long> chunks = new LinkedHashSet<>();
        int columns = 0;
        for (Road.Section section : wall.sections()) {
            List<Wall.Block> blocks = wall.blocksOf(section);
            for (int i = section.placed(); i < section.paid() && i < blocks.size() && columns < WALL_COLUMNS; i++, columns++) {
                Wall.Block block = blocks.get(i);
                chunks.addAll(ChunkHold.ringed(block.x(), block.z(), block.x(), block.z()));
            }
        }
        return chunks;
    }

    /**
     * Lets every held chunk go and forgets everything, when the server stops.
     */
    static void reset() {
        if (target != null) {
            target.hold.releaseAll();
        }
        target = null;
        steps = 32;
        PICK.clear();
        PENDING_SINCE.clear();
        WALL_SINCE.clear();
    }

    /**
     * The place served now, for diagnostics: its kind and id, or empty.
     */
    public static Optional<String> describe() {
        Target current = target;
        return current == null ? Optional.empty() : Optional.of(current.ref.kind() + " " + current.ref.id() + ", " + current.hold.chunks().size()
                + " chunks, " + current.ticks + " ticks" + (current.releasing ? ", letting go" : ""));
    }
}
