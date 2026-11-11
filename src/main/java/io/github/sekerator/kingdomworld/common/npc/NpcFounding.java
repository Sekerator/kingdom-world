package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Founds the kingdoms of the world as data as soon as their places are planned, one place at a time over ticks, without loading
 * or generating a chunk: the land is looked at in the territory and in the region files (a chunk where players spent longer than
 * the rules allow keeps the kingdom away, as when it appears in the world), the height of the throne comes from the generator,
 * and the kingdom gets its land, ruler, names, laws and treasury. Its buildings are raised later ({@link NpcSpawner}).
 */
final class NpcFounding {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Founding current;

    /**
     * The founding of one place over ticks.
     */
    private static final class Founding {
        final int siteId;
        int stage;
        /** Reads of the chunks of the land and of the towns, for the time players spent there. */
        final Map<Long, CompletableFuture<Optional<CompoundTag>>> reads = new LinkedHashMap<>();
        List<Long> land;
        int claimIndex;
        boolean claimedInPass;
        int claimed;

        Founding(int siteId) {
            this.siteId = siteId;
        }
    }

    private NpcFounding() {
    }

    static void reset() {
        current = null;
    }

    /**
     * Whether a place is being founded now.
     */
    static boolean busy() {
        return current != null;
    }

    static void tick(MinecraftServer server) {
        if (!NpcSettings.rules().generate()) {
            return;
        }
        NpcState state = NpcWork.state(server);
        if (current != null) {
            Optional<NpcSite> site = state.site(current.siteId);
            if (site.isEmpty() || site.get().status() != NpcSite.Status.PLANNED) {
                current = null;
            }
        }
        if (current == null) {
            // The largest kingdoms are founded first: their land is the widest and shows on the maps first.
            NpcSite next = null;
            for (NpcSite site : state.sites()) {
                if (site.status() == NpcSite.Status.PLANNED && (next == null || site.level().ordinal() > next.level().ordinal())) {
                    next = site;
                }
            }
            if (next == null) {
                return;
            }
            current = new Founding(next.id());
        }
        NpcSite site = state.site(current.siteId).orElseThrow();
        ServerLevel level = server.overworld();
        try {
            step(server, level, site, current);
        } catch (RuntimeException e) {
            LOGGER.error("The kingdom of the world at {} could not be founded", site, e);
            site.block("failed: " + e);
            current = null;
        }
    }

    /**
     * Founds a planned place to its end at once, for game tests and operators: the region files are waited for.
     *
     * @return whether the place stands as data
     */
    static boolean foundNow(MinecraftServer server, NpcSite site) {
        Founding founding = new Founding(site.id());
        Founding before = current;
        current = founding;
        try {
            for (int i = 0; i < 10_000 && site.status() == NpcSite.Status.PLANNED && current == founding; i++) {
                founding.reads.values().forEach(read -> {
                    try {
                        read.join();
                    } catch (RuntimeException ignored) {
                        // A read that failed counts as a chunk never generated.
                    }
                });
                step(server, server.overworld(), site, founding);
            }
        } finally {
            current = before != null && before.siteId != site.id() ? before : null;
        }
        return site.status() == NpcSite.Status.FOUNDED;
    }

    private static void step(MinecraftServer server, ServerLevel level, NpcSite site, Founding founding) {
        UUID id = NpcSpawner.kingdomId(server, site);
        KingdomWorldState state = KingdomWorldApi.state(server);
        NpcRules rules = NpcSettings.rules();
        switch (founding.stage) {
            case 0 -> {
                if (state.kingdoms().get(id).isEmpty()) {
                    String problem = problem(level, state, site, id);
                    if (problem != null) {
                        block(site, problem);
                        return;
                    }
                }
                for (long chunk : chunksToRead(site)) {
                    LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
                    founding.reads.put(chunk, loaded != null ? CompletableFuture.completedFuture(Optional.of(inhabited(loaded)))
                            : level.getChunkSource().chunkMap.read(new ChunkPos(ChunkKey.x(chunk), ChunkKey.z(chunk))));
                }
                founding.stage = 1;
            }
            case 1 -> {
                for (CompletableFuture<Optional<CompoundTag>> read : founding.reads.values()) {
                    if (!read.isDone()) {
                        return;
                    }
                }
                if (state.kingdoms().get(id).isEmpty()) {
                    long limit = rules.maxInhabitedMinutes() * 1200L;
                    int r = NpcCity.landRadius(site);
                    int cx = site.x() >> 4;
                    int cz = site.z() >> 4;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            long time = time(founding, ChunkKey.of(cx + dx, cz + dz));
                            if (time > limit) {
                                block(site, "players spent " + time / 1200 + " minutes in chunk [" + (cx + dx) + ", " + (cz + dz) + "]");
                                return;
                            }
                        }
                    }
                    List<NpcSite.Town> kept = new ArrayList<>();
                    for (NpcSite.Town town : site.towns()) {
                        String problem = townProblem(state, site, town, id, founding, limit, Dimensions.id(level));
                        if (problem == null) {
                            kept.add(town);
                        } else {
                            LOGGER.info("The town at {} {} of {} is left out: {}", town.x(), town.z(), site, problem);
                        }
                    }
                    if (kept.size() != site.towns().size()) {
                        site.retainTowns(kept);
                    }
                }
                founding.reads.clear();
                founding.stage = 2;
            }
            case 2 -> {
                if (site.groundY() == 0) {
                    site.setGroundY(site.districts() ? NpcPlanning.capitalHeight(level, site.x(), site.z(), site.level())
                            : groundHeight(level, site.x(), site.z()));
                }
                BlockPos throne = NpcSpawner.thronePos(site);
                if (state.kingdoms().get(id).isEmpty()) {
                    String problem = NpcSpawner.createKingdom(server, level, site, id, throne, true);
                    if (problem != null) {
                        block(site, problem);
                        return;
                    }
                } else if (site.kingdomId().isEmpty()) {
                    site.setKingdomId(id);
                }
                if (!site.given(NpcSite.LAWS)) {
                    NpcSpawner.setLaws(server, site, id);
                }
                if (!site.given(NpcSite.MONEY)) {
                    NpcSpawner.grantMoney(server, site, id);
                }
                founding.land = NpcSpawner.landChunks(site);
                founding.stage = 3;
            }
            default -> {
                if (claim(level, site, id, founding)) {
                    site.setStatus(NpcSite.Status.FOUNDED);
                    io.github.sekerator.kingdomworld.common.road.RoadSync.changed();
                    LOGGER.info("The kingdom of the world of {} is founded: {} chunks of land; its buildings come later", site,
                            founding.claimed);
                    if (current == founding) {
                        current = null;
                    }
                }
            }
        }
    }

    private static void block(NpcSite site, String reason) {
        LOGGER.warn("The kingdom of the world at {} is not founded: {}", site, reason);
        site.block(reason);
        current = null;
    }

    /**
     * Why a kingdom cannot be founded at a place by the data: the dimension, the land of another kingdom near the throne or
     * within the land of the place; {@code null} if it can.
     */
    private static String problem(ServerLevel level, KingdomWorldState state, NpcSite site, UUID own) {
        String dimension = Dimensions.id(level);
        var kingdomRules = KingdomWorldApi.rules();
        if (!kingdomRules.allowedDimensions().contains(dimension)) {
            return "kingdoms may not be founded in " + dimension;
        }
        long chunk = ChunkKey.of(site.x() >> 4, site.z() >> 4);
        int r = NpcCity.landRadius(site);
        int buffer = kingdomRules.claimBufferChunks() + 1;
        if (state.territory().ownerOf(dimension, chunk) != null
                || state.territory().hasForeignLandWithin(dimension, chunk, Math.max(r + buffer, kingdomRules.minChunksFromOtherKingdom()), own)) {
            return "the land of another kingdom is near";
        }
        return null;
    }

    private static String townProblem(KingdomWorldState state, NpcSite site, NpcSite.Town town, UUID own, Founding founding, long limit,
                                      String dimension) {
        int buffer = KingdomWorldApi.rules().claimBufferChunks() + 1;
        int r = NpcCity.landRadius(site);
        for (long key : NpcPlanning.townChunks(site.x() >> 4, site.z() >> 4, r, town.x(), town.z())) {
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (state.territory().hasForeignLandWithin(dimension, ChunkKey.of(x, z), buffer, own)) {
                return "the land of another kingdom is near chunk [" + x + ", " + z + "]";
            }
            long time = time(founding, ChunkKey.of(x, z));
            if (time > limit) {
                return "players spent " + time / 1200 + " minutes in chunk [" + x + ", " + z + "]";
            }
        }
        return null;
    }

    /**
     * The chunks whose time with players is looked at: the land of the capital and the squares and ways of the towns.
     */
    private static List<Long> chunksToRead(NpcSite site) {
        java.util.Set<Long> keys = new java.util.LinkedHashSet<>();
        int r = NpcCity.landRadius(site);
        int cx = site.x() >> 4;
        int cz = site.z() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                keys.add(ChunkKey.of(cx + dx, cz + dz));
            }
        }
        for (NpcSite.Town town : site.towns()) {
            for (long chunk : NpcPlanning.townChunks(cx, cz, r, town.x(), town.z())) {
                keys.add(ChunkKey.of(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)));
            }
        }
        return new ArrayList<>(keys);
    }

    private static long time(Founding founding, long chunk) {
        CompletableFuture<Optional<CompoundTag>> read = founding.reads.get(chunk);
        if (read == null || read.isCompletedExceptionally()) {
            return 0;
        }
        return read.getNow(Optional.empty()).map(tag -> tag.getLong("InhabitedTime")).orElse(0L);
    }

    private static CompoundTag inhabited(LevelChunk chunk) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("InhabitedTime", chunk.getInhabitedTime());
        return tag;
    }

    /**
     * Claims the land of the place a few chunks a tick, next to land already held, until a pass claims nothing more.
     *
     * @return whether the land is all claimed
     */
    private static boolean claim(ServerLevel level, NpcSite site, UUID id, Founding founding) {
        int budget = NpcSettings.build().claimsPerTick();
        while (budget > 0) {
            if (founding.claimIndex >= founding.land.size()) {
                if (!founding.claimedInPass) {
                    return true;
                }
                founding.claimIndex = 0;
                founding.claimedInPass = false;
            }
            long chunk = founding.land.get(founding.claimIndex++);
            int x = ChunkPos.getX(chunk);
            int z = ChunkPos.getZ(chunk);
            if (!id.equals(KingdomWorldApi.ownerOfChunk(level, x, z)) && KingdomWorldApi.claimChunk(level, new ChunkPos(x, z), id, null).ok()) {
                founding.claimed++;
                founding.claimedInPass = true;
                budget--;
            }
        }
        return false;
    }

    /**
     * The height of the square of the throne from the generator, as the world will have it: the middle of the heights of nine
     * columns around it, not below the sea. The square is levelled to it when the buildings are raised.
     */
    static int groundHeight(ServerLevel level, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        List<Integer> heights = new ArrayList<>();
        int reach = NpcSpawner.PLAZA;
        for (int dx = -reach; dx <= reach; dx += reach) {
            for (int dz = -reach; dz <= reach; dz += reach) {
                heights.add(generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1);
            }
        }
        heights.sort(Integer::compare);
        return Math.max(heights.get(heights.size() / 2), level.getSeaLevel());
    }
}
