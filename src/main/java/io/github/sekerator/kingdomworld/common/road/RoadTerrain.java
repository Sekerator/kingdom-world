package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.core.road.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The land of a dimension as the search for the way of a road sees it, read chunk by chunk and kept while the search runs.
 * Only a few chunks a tick are loaded or generated for the first time; the search waits for the rest.
 */
final class RoadTerrain implements Terrain {
    /** Ticks a ticket of a search lasts: half a minute. */
    private static final int TICKET_TICKS = 600;
    /** Chunks a search asks the game for; the ticket lapses by itself. */
    private static final net.minecraft.server.level.TicketType<ChunkPos> TICKET = net.minecraft.server.level.TicketType.create(
            "kingdomworld_road_terrain", java.util.Comparator.comparingLong(ChunkPos::toLong), TICKET_TICKS);
    private final ServerLevel level;
    private final Map<Long, Column[]> chunks = new HashMap<>();
    /** Chunks asked for that the game has not loaded yet, with the time they were asked for. */
    private final Map<Long, Long> asked = new HashMap<>();
    /**
     * Chunks a search waits for at once: the game finishes a chunk it loaded or generated on the main thread, and many asked
     * for together come in together, in one long tick.
     */
    private static final int ASKED_AT_ONCE = 3;
    private int loadsLeft = Integer.MAX_VALUE;

    RoadTerrain(ServerLevel level) {
        this.level = level;
    }

    /**
     * A new tick: so many chunks may be read for the first time.
     */
    void allow(int chunks) {
        loadsLeft = chunks;
    }

    @Override
    public Column column(int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) {
            return new Column(level.getMinBuildHeight(), Surface.BLOCKED, null);
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Column[] columns = chunks.get(key);
        if (columns == null) {
            ChunkAccess loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (loaded == null && RoadPlanning.loadNow && loadsLeft > 0) {
                loadsLeft--;
                loaded = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
            if (loaded == null) {
                // The chunk is asked for and read once the game has loaded or generated it, off the tick: the search waits for it
                // instead of loading it in the tick.
                if (loadsLeft > 0 && ask(key, chunkX, chunkZ)) {
                    loadsLeft--;
                }
                return null;
            }
            columns = read(loaded, chunkX, chunkZ);
            chunks.put(key, columns);
            asked.remove(key);
        }
        return columns[(x & 15) * 16 + (z & 15)];
    }

    /**
     * Asks the game for a chunk, unless it was asked for already or as many as may wait at once are loading.
     *
     * @return whether the chunk was asked for now
     */
    private boolean ask(long key, int chunkX, int chunkZ) {
        long now = level.getGameTime();
        // A chunk loaded meanwhile is waited for no more, and one whose ticket lapsed before it came is asked for again.
        asked.values().removeIf(at -> now - at >= TICKET_TICKS);
        asked.keySet().removeIf(other -> level.getChunkSource().getChunkNow(ChunkPos.getX(other), ChunkPos.getZ(other)) != null);
        if (asked.containsKey(key) || asked.size() >= ASKED_AT_ONCE) {
            return false;
        }
        asked.put(key, now);
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        return true;
    }

    private Column[] read(ChunkAccess chunk, int chunkX, int chunkZ) {
        UUID owner = KingdomWorldApi.ownerOfChunk(level, chunkX, chunkZ);
        Column[] columns = new Column[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                pos.set(x, y, z);
                BlockState state = chunk.getBlockState(pos);
                columns[lx * 16 + lz] = new Column(y, surface(pos, state), owner);
            }
        }
        return columns;
    }

    private Surface surface(BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return Surface.DANGER;
        }
        if (state.getFluidState().is(FluidTags.WATER) && !state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
            return Surface.WATER;
        }
        if (isDangerous(state)) {
            return Surface.DANGER;
        }
        if (state.isAir() || ConstructionWork.isProtected(level, pos, state) || state.hasBlockEntity()) {
            return Surface.BLOCKED;
        }
        return Surface.LAND;
    }

    static boolean isDangerous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.WITHER_ROSE)
                || state.is(BlockTags.CAMPFIRES);
    }
}
