package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
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
 * a river, a beach, mountains, peaks, a swamp or mushroom fields, and ground that differs little in height around the throne.
 * In a world that was played before, a candidate whose chunks were generated already (read from the region files off the main
 * thread) or that lies near the land of a kingdom is shifted around before it is given up, so that no kingdom appears on
 * someone else's land or over what players built. Only records are written; the world is not touched.
 */
final class NpcPlanning {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ground around the throne may differ by this many blocks at most. */
    private static final int MAX_ROUGHNESS = 7;
    /** Ground of a town may differ more: its square is small and levelled. */
    private static final int MAX_TOWN_ROUGHNESS = 12;
    /** Town candidates judged in a tick. */
    private static final int TOWNS_PER_TICK = 2;

    private static SitePlanner planner;
    private static Judging judging;

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

        Judging(SitePlanner.Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private NpcPlanning() {
    }

    static void reset() {
        planner = null;
        judging = null;
    }

    static void tick(MinecraftServer server) {
        NpcRules rules = NpcSettings.rules();
        NpcState state = NpcWork.state(server);
        if (!rules.generate() || state.planned()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (planner == null) {
            BlockPos spawn = level.getSharedSpawnPos();
            planner = new SitePlanner(level.getSeed(), rules, spawn.getX(), spawn.getZ(), List.copyOf(state.sites()), state.planCursor(),
                    state.planShift());
        }
        Optional<SitePlanner.Candidate> current = planner.current();
        if (current.isEmpty()) {
            finish(state);
            return;
        }
        if (judging == null || !judging.candidate.equals(current.get())) {
            judging = new Judging(current.get());
        }
        Optional<SitePlanner.Verdict> verdict = judge(level, judging, rules);
        if (verdict.isEmpty()) {
            return;
        }
        Optional<SitePlanner.Candidate> accepted = planner.judge(verdict.get());
        accepted.ifPresent(candidate -> {
            NpcSite site = state.addSite(candidate.x(), candidate.z(), candidate.level(), candidate.seed(), NpcSite.towns(judging.towns));
            LOGGER.info("Planned a kingdom of the world: {} with {} town(s)", site, site.towns().size());
        });
        judging = null;
        state.setPlanProgress(planner.cursor(), planner.shift(), planner.done());
        if (planner.done()) {
            finish(state);
        }
    }

    private static void finish(NpcState state) {
        if (!state.planned()) {
            state.setPlanProgress(planner == null ? state.planCursor() : planner.cursor(), 0, true);
            LOGGER.info("The plan of the kingdoms of the world is made: {} place(s)", state.sites().size());
        }
    }

    /**
     * Judges a candidate a step at a time.
     *
     * @return the verdict, or empty while it is not known yet
     */
    private static Optional<SitePlanner.Verdict> judge(ServerLevel level, Judging judging, NpcRules rules) {
        SitePlanner.Candidate candidate = judging.candidate;
        MinecraftServer server = level.getServer();
        int land = rules.landRadius(candidate.level()) * 16 + 16;
        if (!judging.landDone) {
            if (nearKingdom(server, candidate.x(), candidate.z(), land)) {
                return Optional.of(SitePlanner.Verdict.OCCUPIED);
            }
            if (!suitable(level, candidate.x(), candidate.z(), 40, true)) {
                return Optional.of(SitePlanner.Verdict.UNSUITABLE);
            }
            judging.landDone = true;
            // Chunks of the land that exist in the region files mean that the world was played there already.
            ChunkPos center = new ChunkPos(new BlockPos(candidate.x(), 0, candidate.z()));
            int r = rules.landRadius(candidate.level());
            for (int[] offset : new int[][]{{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}, {0, -r}, {0, r}, {-r, 0}, {r, 0}}) {
                judging.reads.add(level.getChunkSource().chunkMap.read(new ChunkPos(center.x + offset[0], center.z + offset[1])));
            }
            judging.townCandidates = SitePlanner.townCandidates(candidate.x(), candidate.z(), candidate.seed(), rules.townDistance());
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
        for (int i = 0; i < TOWNS_PER_TICK && judging.townIndex < judging.townCandidates.size() && judging.towns.size() < wanted; i++) {
            int[] town = judging.townCandidates.get(judging.townIndex++);
            List<int[]> chosen = SitePlanner.chooseTowns(List.of(town), point -> !nearKingdom(server, point[0], point[1], 48)
                    && suitable(level, point[0], point[1], 16, false), 1, 0);
            boolean apart = true;
            for (int[] other : judging.towns) {
                apart &= Math.hypot(town[0] - other[0], town[1] - other[1]) >= Math.max(minCity, 64);
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
            int r = rules.landRadius(candidate.level());
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
        int r = rules.landRadius(candidate.level());
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
     * Whether the land around a point suits a capital or a town, by the noise of the generator.
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
