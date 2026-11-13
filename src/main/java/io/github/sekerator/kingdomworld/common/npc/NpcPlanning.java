package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.NpcStyle;
import io.github.sekerator.kingdomworld.core.npc.SitePlanner;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.common.Tags;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Makes the plan of the places of the kingdoms of the world, a candidate at a time. The land of a candidate is judged by the
 * generator of the world without generating anything (its noise gives the height and the biome of any column): not the ocean,
 * a river, a beach, mountains, peaks, a swamp or mushroom fields, and ground that differs little in height over all the
 * capital of the level the candidate is judged for (a large kingdom asks for a wide flat place; the plan asks less on its
 * later passes, see {@link SitePlanner}).
 * In a world that was played before, a candidate whose chunks were generated already (read from the region files off the main
 * thread) or that lies near the land of a kingdom is shifted around before it is given up, so that no kingdom appears on
 * someone else's land or over what players built. Only records are written; the world is not touched.
 */
public final class NpcPlanning {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ground around the throne of a capital of the first layout may differ by this many blocks at most. */
    private static final int MAX_ROUGHNESS = 7;
    /** Ground of a town may differ more: its square is small and levelled. */
    private static final int MAX_TOWN_ROUGHNESS = 12;
    /** Ground over a capital of districts may differ by this many blocks at most, for small, medium and large kingdoms. */
    private static final int[] CAPITAL_ROUGHNESS = {8, 9, 10};
    /** Blocks more the ground may differ by on every later pass of the plan. */
    private static final int ROUGHNESS_PER_PASS = 3;
    /** Columns of water or shore a capital of districts may have on the later passes of the plan: a brook is filled in. */
    private static final int WET_PER_PASS = 2;
    /** Town candidates judged in a tick at most, while the time of the tick lasts. */
    private static final int TOWNS_PER_TICK = 8;
    /** Time a tick spends on the plan at most: candidates the noise judges at once are gone through several a tick. */
    private static final long BUDGET_NANOS = 16_000_000L;
    /** Time a tick spends on the plan at least, however long the tick was before the plan came to it. */
    private static final long MIN_BUDGET_NANOS = 3_000_000L;
    /** The plan takes what is left of a tick up to this length. */
    private static final long TICK_AIM_NANOS = 28_000_000L;
    /**
     * Threads that ask the generator for the heights of many columns at once (the whole land of a candidate, its towns, the
     * height of a capital), off the ticks of the server: the generator answers any thread, as it does while it generates
     * chunks on its own threads.
     */
    private static final java.util.concurrent.ExecutorService NOISE = java.util.concurrent.Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "KingdomWorld plan");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** What the plan took so far: ticks with work, their time in all, the longest. */
    private static long planTicks;
    private static long planNanos;
    private static long planWorst;

    private static SitePlanner planner;
    private static Judging judging;
    /** When the plan stops for this tick: the heights of the generator are asked for one column at a time until then. */
    private static long deadline = Long.MAX_VALUE;

    /**
     * The judgement of the current candidate over ticks.
     */
    private static final class Judging {
        final SitePlanner.Candidate candidate;
        final List<CompletableFuture<Optional<CompoundTag>>> reads = new ArrayList<>();
        boolean landDone;
        List<int[]> townCandidates;
        final List<int[]> towns = new ArrayList<>();
        int townIndex;
        /** For every chosen town, the reads of the chunks of its square and of the way to it. */
        List<List<CompletableFuture<Optional<CompoundTag>>>> townReads;
        /** The heights the generator gave for columns of the candidate: {ground under water, surface}, by column. */
        final java.util.Map<Long, int[]> columns = new java.util.HashMap<>();
        /** Columns asked of the generator off the tick, by column. */
        final java.util.Map<Long, CompletableFuture<int[]>> asked = new java.util.HashMap<>();

        Judging(SitePlanner.Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private NpcPlanning() {
    }

    static void reset() {
        planner = null;
        judging = null;
        CAPITAL_HEIGHTS.clear();
        planTicks = 0;
        planNanos = 0;
        planWorst = 0;
    }

    static void tick(MinecraftServer server) {
        if (!NpcSettings.rules().generate() || NpcWork.state(server).planned()) {
            return;
        }
        // A step that waits for region files ends the tick; steps the noise answers at once go on while the budget lasts, and a
        // step that runs out of it in the middle of a candidate goes on with it in the next tick. The budget is what is left of
        // a short tick: a tick that was long already gives the plan little.
        long start = System.nanoTime();
        long budget = Math.max(MIN_BUDGET_NANOS, Math.min(BUDGET_NANOS, TICK_AIM_NANOS
                - io.github.sekerator.kingdomworld.common.command.PerfCommands.sinceTickStart()));
        deadline = start + budget;
        try {
            while (step(server) && System.nanoTime() < deadline) {
                // next candidate
            }
        } finally {
            deadline = Long.MAX_VALUE;
            long spent = System.nanoTime() - start;
            planTicks++;
            planNanos += spent;
            planWorst = Math.max(planWorst, spent);
        }
    }

    /**
     * The heights of the generator at a column of the current candidate, {ground under water, surface}, looked up once: at once
     * while the time of the tick lasts, else off the tick; {@code null} while the answer is not there, so that the candidate is
     * judged on in a later tick.
     *
     * @param surface whether the surface is needed too, or the ground alone
     */
    @javax.annotation.Nullable
    private static int[] column(ServerLevel level, @javax.annotation.Nullable Judging judging, int x, int z, boolean surface) {
        long key = BlockPos.asLong(x, 0, z);
        int[] known = judging == null ? null : judging.columns.get(key);
        if (known != null && (!surface || known[1] != Integer.MIN_VALUE)) {
            return known;
        }
        if (judging != null) {
            CompletableFuture<int[]> asked = judging.asked.get(key);
            if (asked != null) {
                if (!asked.isDone()) {
                    return null;
                }
                judging.asked.remove(key);
                if (!asked.isCompletedExceptionally()) {
                    known = asked.join();
                    judging.columns.put(key, known);
                    if (!surface || known[1] != Integer.MIN_VALUE) {
                        return known;
                    }
                }
            }
            if (System.nanoTime() > deadline) {
                ask(level, judging, key, x, z, surface, known);
                return null;
            }
        }
        int[] heights = heights(level, x, z, surface, known);
        if (judging != null) {
            judging.columns.put(key, heights);
        }
        return heights;
    }

    /**
     * Asks the generator for a column off the tick, unless it is asked already.
     */
    private static void ask(ServerLevel level, Judging judging, long key, int x, int z, boolean surface, @javax.annotation.Nullable int[] known) {
        if (!judging.asked.containsKey(key)) {
            judging.asked.put(key, CompletableFuture.supplyAsync(() -> heights(level, x, z, surface, known), NOISE));
        }
    }

    /**
     * Asks the generator off the tick for the columns of a part of the land that is looked at whole.
     */
    private static void prefetch(ServerLevel level, Judging judging, List<int[]> points, boolean surface) {
        for (int[] point : points) {
            long key = BlockPos.asLong(point[0], 0, point[1]);
            int[] known = judging.columns.get(key);
            if (known == null || surface && known[1] == Integer.MIN_VALUE) {
                ask(level, judging, key, point[0], point[1], surface, known);
            }
        }
    }

    /**
     * The heights the generator gives for a column: {ground under water, surface (or {@link Integer#MIN_VALUE} if not asked)}.
     */
    private static int[] heights(ServerLevel level, int x, int z, boolean surface, @javax.annotation.Nullable int[] known) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int floor = known != null ? known[0] : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        int top = surface ? generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) : Integer.MIN_VALUE;
        return new int[]{floor, top};
    }

    /**
     * One step of the plan.
     *
     * @return whether the step came to a verdict, so that another may follow in the same tick
     */
    private static boolean step(MinecraftServer server) {
        NpcRules rules = NpcSettings.rules();
        NpcState state = NpcWork.state(server);
        if (!rules.generate() || state.planned()) {
            return false;
        }
        // A new world asked for the plan of states, or one begun, makes it instead of the plan of capitals.
        if (planner == null && io.github.sekerator.kingdomworld.common.land.LandPlanning.wanted(server, NpcSettings.states())) {
            io.github.sekerator.kingdomworld.common.land.LandPlanning.tick(server);
            return false;
        }
        ServerLevel level = server.overworld();
        if (planner == null) {
            BlockPos spawn = level.getSharedSpawnPos();
            planner = new SitePlanner(level.getSeed(), rules, spawn.getX(), spawn.getZ(), List.copyOf(state.sites()), state.planCursor(),
                    state.planShift(), state.planPass());
        }
        int pass = planner.pass();
        Optional<SitePlanner.Candidate> current = planner.current();
        if (planner.pass() != pass) {
            LOGGER.info("The plan of the kingdoms of the world has {} of {} place(s) after looking at all the candidates of a pass: it looks at "
                    + "as many new ones and asks less of a place (pass {} of {})", state.sites().size(), rules.count(), planner.pass() + 1,
                    SitePlanner.PASSES);
        }
        if (current.isEmpty()) {
            finish(state);
            return false;
        }
        if (judging == null || !judging.candidate.equals(current.get())) {
            judging = new Judging(current.get());
        }
        Optional<SitePlanner.Verdict> verdict = judge(level, judging, rules);
        if (verdict.isEmpty()) {
            return false;
        }
        Optional<SitePlanner.Candidate> accepted = planner.judge(verdict.get());
        accepted.ifPresent(candidate -> {
            NpcSite site = addSite(server, candidate.x(), candidate.z(), candidate.level(), candidate.seed(), judging.towns);
            LOGGER.info("Planned a kingdom of the world: {} with {} town(s), style {}{}", site, site.towns().size(),
                    site.style().isEmpty() ? "none" : site.style(), candidate.relax() > 0 ? ", on pass " + (candidate.relax() + 1) : "");
        });
        judging = null;
        boolean done = planner.done();
        state.setPlanProgress(planner.cursor(), planner.shift(), planner.pass(), done);
        if (done) {
            made(state, rules);
            return false;
        }
        return true;
    }

    private static void finish(NpcState state) {
        if (!state.planned()) {
            state.setPlanProgress(planner == null ? state.planCursor() : planner.cursor(), 0, planner == null ? state.planPass() : planner.pass(),
                    true);
            made(state, NpcSettings.rules());
        }
    }

    /**
     * Says in the log what the plan came to: how many places of each level, and what is missing of the quota.
     */
    private static void made(NpcState state, NpcRules rules) {
        int[] count = new int[NpcLevel.values().length];
        for (NpcSite site : state.sites()) {
            count[site.level().ordinal()]++;
        }
        LOGGER.info("The plan of the kingdoms of the world is made: {} place(s): {} large, {} medium, {} small", state.sites().size(),
                count[NpcLevel.LARGE.ordinal()], count[NpcLevel.MEDIUM.ordinal()], count[NpcLevel.SMALL.ordinal()]);
        LOGGER.info("The plan took {} tick(s) with work, {} ms in all, at most {} ms in a tick", planTicks, planNanos / 1_000_000,
                String.format(java.util.Locale.ROOT, "%.1f", planWorst / 1e6));
        if (state.sites().size() < rules.count()) {
            LOGGER.warn("The plan has {} place(s) of the {} wanted: the land within {} blocks of the spawn has no more room that suits",
                    state.sites().size(), rules.count(), rules.radius());
        }
        for (NpcLevel level : NpcLevel.values()) {
            if (count[level.ordinal()] < Math.min(rules.quota(level), rules.count())) {
                LOGGER.warn("The plan has {} {} kingdom(s) of the {} wanted: no more land flat and wide enough was found, smaller kingdoms "
                        + "took the places", count[level.ordinal()], level.id(), rules.quota(level));
            }
        }
    }

    /**
     * Adds a place of the layout of districts to the plan: its style by the climate of its land and its number in the plan, and
     * the wood that grows there, both read from the generator of the world without generating a chunk.
     */
    public static NpcSite addSite(MinecraftServer server, int x, int z, NpcLevel kingdom, long seed, List<int[]> towns) {
        ServerLevel level = server.overworld();
        NpcState state = NpcWork.state(server);
        Holder<Biome> biome = biomeAt(level, x, z);
        String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
        java.util.Set<String> tags = new java.util.HashSet<>();
        biome.tags().forEach(tag -> tags.add(tag.location().toString()));
        NpcStyle style = NpcStyle.pick(NpcStyle.climate(id, tags), seed, state.sites().size());
        String wood = TemplateMaterials.table().woodForBiome(id, tags);
        return state.addSite(x, z, kingdom, seed, NpcSite.towns(towns), NpcSite.LAYOUT_DISTRICTS, style.id(), wood);
    }

    private static Holder<Biome> biomeAt(ServerLevel level, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int height = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(height), QuartPos.fromBlock(z), random.sampler());
    }

    /**
     * Moves a place for a town to the middle of its chunk, so that the land of the town (a square of chunks around that chunk)
     * reaches as far on every side.
     */
    static int[] centred(int[] town) {
        return new int[]{(town[0] >> 4) * 16 + 8, (town[1] >> 4) * 16 + 8};
    }

    /**
     * Judges a candidate a step at a time.
     *
     * @return the verdict, or empty while it is not known yet
     */
    private static Optional<SitePlanner.Verdict> judge(ServerLevel level, Judging judging, NpcRules rules) {
        SitePlanner.Candidate candidate = judging.candidate;
        MinecraftServer server = level.getServer();
        int landRadius = NpcCity.landRadius(candidate.level(), NpcSite.LAYOUT_DISTRICTS);
        int land = landRadius * 16 + 16;
        if (!judging.landDone) {
            if (nearKingdom(server, candidate.x(), candidate.z(), land)) {
                return Optional.of(SitePlanner.Verdict.OCCUPIED);
            }
            Boolean suits = suitableCapital(level, candidate.x(), candidate.z(), candidate.level(), candidate.relax(), judging);
            if (suits == null) {
                // Its columns are not answered yet: the candidate is looked at on in a later tick.
                return Optional.empty();
            }
            if (!suits) {
                return Optional.of(SitePlanner.Verdict.UNSUITABLE);
            }
            judging.landDone = true;
            // Chunks of the land that exist in the region files mean that the world was played there already.
            ChunkPos center = new ChunkPos(new BlockPos(candidate.x(), 0, candidate.z()));
            int r = landRadius;
            int h = r / 2;
            for (int[] offset : new int[][]{{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}, {0, -r}, {0, r}, {-r, 0}, {r, 0}, {-h, -h}, {h, -h}, {-h, h},
                    {h, h}}) {
                judging.reads.add(level.getChunkSource().chunkMap.read(new ChunkPos(center.x + offset[0], center.z + offset[1])));
            }
            judging.townCandidates = new ArrayList<>();
            for (int[] town : SitePlanner.townCandidates(candidate.x(), candidate.z(), candidate.seed(),
                    NpcCity.townDistance(candidate.level(), NpcSite.LAYOUT_DISTRICTS))) {
                judging.townCandidates.add(centred(town));
            }
            return Optional.empty();
        }
        for (CompletableFuture<Optional<CompoundTag>> read : judging.reads) {
            if (!read.isDone()) {
                return Optional.empty();
            }
        }
        for (CompletableFuture<Optional<CompoundTag>> read : judging.reads) {
            Optional<CompoundTag> chunk = read.isCompletedExceptionally() ? Optional.empty() : read.join();
            if (chunk.isPresent() && generated(chunk.get())) {
                return Optional.of(SitePlanner.Verdict.OCCUPIED);
            }
        }
        int wanted = rules.towns(candidate.level());
        int minCity = io.github.sekerator.kingdomworld.common.road.RoadSettings.rules().minCityDistance();
        // The columns of the next towns are asked off the tick at once.
        for (int i = judging.townIndex; i < Math.min(judging.townCandidates.size(), judging.townIndex + TOWNS_PER_TICK); i++) {
            int[] town = judging.townCandidates.get(i);
            prefetch(level, judging, townPoints(town[0], town[1]), true);
        }
        for (int i = 0; i < TOWNS_PER_TICK && judging.townIndex < judging.townCandidates.size() && judging.towns.size() < wanted; i++) {
            if (i > 0 && System.nanoTime() > deadline) {
                // The next town is looked at in the next tick.
                break;
            }
            int[] town = judging.townCandidates.get(judging.townIndex);
            Boolean suits = nearKingdom(server, town[0], town[1], 64) ? Boolean.FALSE : suitableTown(level, judging, town[0], town[1]);
            if (suits == null) {
                // Its columns are not answered yet: this town is looked at on in a later tick.
                return Optional.empty();
            }
            judging.townIndex++;
            List<int[]> chosen = suits ? List.of(town) : List.of();
            boolean apart = true;
            for (int[] other : judging.towns) {
                apart &= Math.hypot(town[0] - other[0], town[1] - other[1]) >= Math.max(minCity, 112);
            }
            if (!chosen.isEmpty() && apart) {
                judging.towns.add(town);
            }
        }
        if (judging.towns.size() < wanted && judging.townIndex < judging.townCandidates.size()) {
            return Optional.empty();
        }
        // The towns are looked at as the capital is: a town whose square or way was generated already, or whose way passes the
        // land of a kingdom, is left out.
        if (judging.townReads == null) {
            judging.townReads = new ArrayList<>();
            int r = landRadius;
            int cx = candidate.x() >> 4;
            int cz = candidate.z() >> 4;
            for (int[] town : judging.towns) {
                List<CompletableFuture<Optional<CompoundTag>>> reads = new ArrayList<>();
                for (long chunk : townChunks(cx, cz, r, town[0], town[1])) {
                    reads.add(level.getChunkSource().chunkMap.read(new ChunkPos(chunk)));
                }
                judging.townReads.add(reads);
            }
            return Optional.empty();
        }
        for (List<CompletableFuture<Optional<CompoundTag>>> reads : judging.townReads) {
            for (CompletableFuture<Optional<CompoundTag>> read : reads) {
                if (!read.isDone()) {
                    return Optional.empty();
                }
            }
        }
        List<int[]> kept = new ArrayList<>();
        int r = landRadius;
        for (int i = 0; i < judging.towns.size(); i++) {
            int[] town = judging.towns.get(i);
            boolean free = true;
            for (CompletableFuture<Optional<CompoundTag>> read : judging.townReads.get(i)) {
                Optional<CompoundTag> chunk = read.isCompletedExceptionally() ? Optional.empty() : read.join();
                free &= chunk.isEmpty() || !generated(chunk.get());
            }
            for (long chunk : townChunks(candidate.x() >> 4, candidate.z() >> 4, r, town[0], town[1])) {
                free &= free && !nearKingdom(server, ChunkPos.getX(chunk) * 16 + 8, ChunkPos.getZ(chunk) * 16 + 8, 16);
            }
            if (free) {
                kept.add(town);
            }
        }
        judging.towns.clear();
        judging.towns.addAll(kept);
        return Optional.of(SitePlanner.Verdict.GOOD);
    }

    /**
     * The chunks of a town and of the way to it outside the land of the capital: the square of the town two chunks around and
     * the chunks of the straight way from the capital.
     */
    static List<Long> townChunks(int capitalX, int capitalZ, int landRadius, int townX, int townZ) {
        java.util.Set<Long> keys = new java.util.LinkedHashSet<>();
        int tx = townX >> 4;
        int tz = townZ >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                keys.add(ChunkPos.asLong(tx + dx, tz + dz));
            }
        }
        for (long chunk : NpcSpawner.corridor(capitalX, capitalZ, tx, tz)) {
            if (Math.abs(ChunkPos.getX(chunk) - capitalX) > landRadius || Math.abs(ChunkPos.getZ(chunk) - capitalZ) > landRadius) {
                keys.add(chunk);
            }
        }
        return new ArrayList<>(keys);
    }

    private static boolean generated(CompoundTag chunk) {
        String status = chunk.getString("Status");
        return status.isEmpty() || status.endsWith("full");
    }

    /**
     * Whether land of a kingdom lies within a distance of a point.
     */
    static boolean nearKingdom(MinecraftServer server, int x, int z, int distance) {
        String dimension = Dimensions.id(server.overworld());
        int chunks = (distance >> 4) + 1 + KingdomWorldApi.rules().claimBufferChunks();
        return KingdomWorldApi.state(server).territory().hasForeignLandWithin(dimension, ChunkKey.of(x >> 4, z >> 4), chunks, null);
    }

    /**
     * Whether the land around a point suits a capital of districts of a level, by the noise of the generator: the ground is
     * looked at on a grid over the whole square inside the wall and a little beyond it, and may differ in height by what the
     * level allows; no water, and a biome kingdoms may stand in.
     *
     * @param relax the pass of the plan: later passes allow rougher ground and a brook or two
     */
    public static boolean suitableCapital(ServerLevel level, int x, int z, NpcLevel kingdom, int relax) {
        return Boolean.TRUE.equals(suitableCapital(level, x, z, kingdom, relax, null));
    }

    /**
     * Whether a capital may stand at a place, judged over ticks for a candidate of the plan.
     *
     * @return {@code null} while the time of the tick runs out before all the columns it needs are known
     */
    @javax.annotation.Nullable
    private static Boolean suitableCapital(ServerLevel level, int x, int z, NpcLevel kingdom, int relax, @javax.annotation.Nullable Judging judging) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int sea = generator.getSeaLevel();
        int reach = NpcComposition.districtWall(kingdom) + 8;
        int step = Math.max(8, reach / 3);
        int allowed = CAPITAL_ROUGHNESS[kingdom.ordinal()] + ROUGHNESS_PER_PASS * Math.min(relax, 3);
        int wetAllowed = WET_PER_PASS * Math.min(relax, 3);
        // The cheap looks first: the biome of the throne, then the middle, the corners and the sides of the square. Most land
        // falls here, and the whole grid is looked at only where these nine columns are dry and about level.
        int[] throne = column(level, judging, x, z, false);
        if (throne == null) {
            return null;
        }
        RandomState random = level.getChunkSource().randomState();
        if (!allowed(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(throne[0]), QuartPos.fromBlock(z),
                random.sampler()))) {
            return false;
        }
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = -reach; dx <= reach; dx += reach) {
            for (int dz = -reach; dz <= reach; dz += reach) {
                int[] column = column(level, judging, x + dx, z + dz, false);
                if (column == null) {
                    return null;
                }
                int floor = column[0];
                if (floor <= sea) {
                    if (wetAllowed == 0 || (dx == 0 && dz == 0)) {
                        return false;
                    }
                    continue;
                }
                low = Math.min(low, floor);
                high = Math.max(high, floor);
            }
        }
        // The grid trims its two lowest and highest columns on later passes: the nine may differ a little more.
        if (high - low > allowed + (relax > 0 ? 6 : 0)) {
            return false;
        }
        int wet = 0;
        List<Integer> heights = new ArrayList<>();
        if (judging != null) {
            // The whole land is looked at: its columns are asked off the tick at once.
            List<int[]> grid = new ArrayList<>();
            for (int dx = -reach; dx <= reach; dx += step) {
                for (int dz = -reach; dz <= reach; dz += step) {
                    grid.add(new int[]{x + dx, z + dz});
                }
            }
            prefetch(level, judging, grid, true);
        }
        for (int dx = -reach; dx <= reach; dx += step) {
            for (int dz = -reach; dz <= reach; dz += step) {
                int[] column = column(level, judging, x + dx, z + dz, true);
                if (column == null) {
                    return null;
                }
                int floor = column[0];
                int surface = column[1];
                if (surface > floor || floor <= sea) {
                    // Water over the ground, or ground at the level of the sea.
                    if (++wet > wetAllowed || (dx == 0 && dz == 0)) {
                        return false;
                    }
                    continue;
                }
                heights.add(floor);
            }
        }
        heights.sort(Integer::compare);
        // The few lowest and highest columns are cut or filled: what counts is the ground between them.
        int trim = relax > 0 ? Math.min(2, heights.size() / 16) : 0;
        return !heights.isEmpty() && heights.get(heights.size() - 1 - trim) - heights.get(trim) <= allowed;
    }

    /** The heights of the columns of a capital asked of the generator off the tick, by the place of its throne. */
    private static final java.util.Map<Long, List<CompletableFuture<Integer>>> CAPITAL_HEIGHTS = new java.util.HashMap<>();

    /**
     * The height of a capital, its columns asked of the generator off the tick: empty until all of them are answered.
     */
    static java.util.OptionalInt capitalHeightSome(ServerLevel level, int x, int z, NpcLevel kingdom) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int reach = NpcComposition.districtWall(kingdom);
        int step = Math.max(4, reach / 4);
        long key = BlockPos.asLong(x, 0, z);
        List<CompletableFuture<Integer>> asked = CAPITAL_HEIGHTS.computeIfAbsent(key, k -> {
            List<CompletableFuture<Integer>> list = new ArrayList<>();
            for (int dx = -reach; dx <= reach; dx += step) {
                for (int dz = -reach; dz <= reach; dz += step) {
                    int cx = x + dx;
                    int cz = z + dz;
                    list.add(CompletableFuture.supplyAsync(() -> generator.getBaseHeight(cx, cz, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1,
                            NOISE));
                }
            }
            return list;
        });
        for (CompletableFuture<Integer> height : asked) {
            if (!height.isDone()) {
                return java.util.OptionalInt.empty();
            }
        }
        CAPITAL_HEIGHTS.remove(key);
        for (CompletableFuture<Integer> height : asked) {
            if (height.isCompletedExceptionally()) {
                // The generator failed off the tick: the height is worked out at once.
                return java.util.OptionalInt.of(capitalHeight(level, x, z, kingdom));
            }
        }
        List<Integer> sorted = new ArrayList<>();
        asked.forEach(height -> sorted.add(height.join()));
        sorted.sort(Integer::compare);
        return java.util.OptionalInt.of(Math.max(sorted.get(sorted.size() / 2), level.getSeaLevel()));
    }

    /**
     * The height the ground of a capital of districts is levelled to, from the generator: the middle of the heights over all of
     * it, not below the sea, so that about as much is cut as is filled.
     */
    static int capitalHeight(ServerLevel level, int x, int z, NpcLevel kingdom) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int reach = NpcComposition.districtWall(kingdom);
        int step = Math.max(4, reach / 4);
        List<Integer> heights = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx += step) {
            for (int dz = -reach; dz <= reach; dz += step) {
                heights.add(generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1);
            }
        }
        heights.sort(Integer::compare);
        return Math.max(heights.get(heights.size() / 2), level.getSeaLevel());
    }

    /**
     * Whether the land around a town of a candidate suits it, as {@link #suitable} looks at it, with the columns remembered;
     * {@code null} while its columns are not answered yet.
     */
    @javax.annotation.Nullable
    private static Boolean suitableTown(ServerLevel level, Judging judging, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int sea = generator.getSeaLevel();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] point : townPoints(x, z)) {
            int[] column = column(level, judging, point[0], point[1], true);
            if (column == null) {
                return null;
            }
            if (column[1] > column[0] || column[0] <= sea) {
                return false;
            }
            min = Math.min(min, column[0]);
            max = Math.max(max, column[0]);
            if (max - min > MAX_TOWN_ROUGHNESS) {
                return false;
            }
        }
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(max), QuartPos.fromBlock(z),
                random.sampler());
        return allowed(biome);
    }

    /**
     * The columns the land of a town is looked at by: its middle and eight around it.
     */
    private static List<int[]> townPoints(int x, int z) {
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{x, z});
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            points.add(new int[]{x + (int) Math.round(Math.cos(angle) * 28), z + (int) Math.round(Math.sin(angle) * 28)});
        }
        return points;
    }

    /**
     * Whether the land around a point suits a capital of the first layout or a town, by the noise of the generator.
     *
     * @param radius   how far around the point the ground is looked at
     * @param thorough whether to look at more columns (for a capital)
     */
    static boolean suitable(ServerLevel level, int x, int z, int radius, boolean thorough) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int sea = generator.getSeaLevel();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{x, z});
        int rings = thorough ? 2 : 1;
        for (int ring = 1; ring <= rings; ring++) {
            int r = radius * ring / rings;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                points.add(new int[]{x + (int) Math.round(Math.cos(angle) * r), z + (int) Math.round(Math.sin(angle) * r)});
            }
        }
        for (int[] point : points) {
            int floor = generator.getBaseHeight(point[0], point[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            int surface = generator.getBaseHeight(point[0], point[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
            if (surface > floor || floor <= sea) {
                // Water over the ground, or ground at the level of the sea.
                return false;
            }
            min = Math.min(min, floor);
            max = Math.max(max, floor);
            if (max - min > (thorough ? MAX_ROUGHNESS : MAX_TOWN_ROUGHNESS)) {
                return false;
            }
        }
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(max), QuartPos.fromBlock(z),
                random.sampler());
        return allowed(biome);
    }

    /**
     * Whether kingdoms may stand in a biome: not the ocean, a river, a beach, mountains, peaks, a swamp or mushroom fields.
     */
    static boolean allowed(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
                || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(Tags.Biomes.IS_SWAMP)
                || biome.is(Tags.Biomes.IS_MUSHROOM) || biome.is(Tags.Biomes.IS_PEAK) || biome.is(Tags.Biomes.IS_WATER)
                || biome.is(Tags.Biomes.IS_UNDERGROUND) || biome.is(Tags.Biomes.IS_VOID)) {
            return false;
        }
        String id = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        return !(id.contains("swamp") || id.contains("marsh") || id.contains("bog") || id.contains("ocean") || id.contains("river")
                || id.contains("peak") || id.contains("mountain") || id.contains("volcan"));
    }
}
