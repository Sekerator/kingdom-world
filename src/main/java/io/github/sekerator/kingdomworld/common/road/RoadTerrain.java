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
    private final ServerLevel level;
    private final Map<Long, Column[]> chunks = new HashMap<>();
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
            if (loadsLeft <= 0) {
                return null;
            }
            loadsLeft--;
            columns = read(chunkX, chunkZ);
            chunks.put(key, columns);
        }
        return columns[(x & 15) * 16 + (z & 15)];
    }

    private Column[] read(int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
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
