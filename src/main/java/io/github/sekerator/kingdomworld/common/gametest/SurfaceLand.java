package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.BiPredicate;

/**
 * The surface of the world of the game tests for the tests that work on it (roads, camps): the world is an ordinary world of a
 * seed, the tests standing deep under its land, and the surface over a test may be the sea. Such a test looks for dry land near.
 */
final class SurfaceLand {
    /** Blocks between two places looked at, ring after ring. */
    static final int STEP = 256;
    /** Rings of places looked at. */
    static final int RINGS = 6;

    private SurfaceLand() {
    }

    /**
     * The place nearest to a point, on rings {@value #STEP} blocks apart, that suits a test; null when none of
     * {@value #RINGS} rings does.
     */
    static int[] near(ServerLevel level, int x, int z, BiPredicate<Integer, Integer> suits) {
        for (int ring = 0; ring <= RINGS; ring++) {
            int count = Math.max(1, ring * 8);
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                int cx = x + (int) Math.round(Math.cos(angle) * ring * STEP);
                int cz = z + (int) Math.round(Math.sin(angle) * ring * STEP);
                if (suits.test(cx, cz)) {
                    return new int[]{cx, cz};
                }
            }
        }
        return null;
    }

    /**
     * The height of the dry land of nobody at a column, its chunk generated if it is not; {@link Integer#MIN_VALUE} for water,
     * lava or land of a kingdom.
     */
    static int dry(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!level.getBlockState(top.below()).getFluidState().isEmpty() || KingdomWorldApi.ownerOfChunk(level, x >> 4, z >> 4) != null) {
            return Integer.MIN_VALUE;
        }
        return top.getY();
    }

    /**
     * Whether a square of columns, every few blocks, is dry land of nobody, its heights within a span.
     */
    static boolean drySquare(ServerLevel level, int x, int z, int radius, int every, int span) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += every) {
            for (int dz = -radius; dz <= radius; dz += every) {
                int y = dry(level, x + dx, z + dz);
                if (y == Integer.MIN_VALUE) {
                    return false;
                }
                low = Math.min(low, y);
                high = Math.max(high, y);
                if (high - low > span) {
                    return false;
                }
            }
        }
        return true;
    }
}
