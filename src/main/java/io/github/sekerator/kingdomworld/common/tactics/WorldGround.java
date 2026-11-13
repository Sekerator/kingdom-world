package io.github.sekerator.kingdomworld.common.tactics;

import io.github.sekerator.kingdomworld.core.tactics.Ground;
import io.github.sekerator.kingdomworld.core.tactics.Spot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The ground of a fight as the blocks of the world make it: a place to stand is one a mob would walk on (solid under the
 * feet, room for the body), not lava, fire, a cactus or water, and not over a drop of more than three blocks. Places in
 * chunks that are not loaded are not stood on.
 */
final class WorldGround implements Ground {
    /** The heights tried around a wanted place, nearest first. */
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2, -3};

    private final ServerLevel level;
    /** The height of the enemy: higher ground than it is better for shooting. */
    private final double base;

    WorldGround(ServerLevel level, double base) {
        this.level = level;
        this.base = base;
    }

    @Override
    public Spot settle(Spot spot) {
        int x = (int) Math.floor(spot.x());
        int z = (int) Math.floor(spot.z());
        int y0 = (int) Math.floor(spot.y() + 0.5);
        // The kind of a place looks at its neighbours too: every chunk they lie in must be loaded, so that nothing loads.
        if (!loaded(level, x, z)) {
            return null;
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy : HEIGHTS) {
            int y = y0 + dy;
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 2) {
                continue;
            }
            at.set(x, y, z);
            BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(level, at);
            if (type != BlockPathTypes.WALKABLE && type != BlockPathTypes.DOOR_OPEN) {
                continue;
            }
            BlockPos head = new BlockPos(x, y + 1, z);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                continue;
            }
            return new Spot(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /**
     * Whether the chunks of a column and of its neighbours are loaded now (a chunk with a ticket that is still loading is not).
     */
    static boolean loaded(ServerLevel level, int x, int z) {
        for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
            for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public double vantage(Spot spot) {
        return Math.max(0.0, Math.min(6.0, spot.y() - base));
    }
}
