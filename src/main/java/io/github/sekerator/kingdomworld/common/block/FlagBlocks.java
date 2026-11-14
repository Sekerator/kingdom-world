package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.FlagSpots;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one test of whether a block is the flag of a kingdom, and the states of flags.
 *
 * <p>There is no tag of flags on purpose: data packs extend tags, and a block a tag made a flag would cost the builders
 * nothing and give endless blocks.
 */
public final class FlagBlocks {

    private FlagBlocks() {
    }

    /**
     * Whether a block is the flag of a kingdom, standing or on a wall.
     */
    public static boolean is(BlockState state) {
        return state.getBlock() instanceof AbstractKingdomFlagBlock;
    }

    /**
     * Whether a flag hangs on a wall rather than stands.
     */
    public static boolean onWall(BlockState state) {
        return state.getBlock() instanceof KingdomWallFlagBlock;
    }

    /**
     * A standing flag turned in sixteenths of a turn, 0 facing south.
     */
    public static BlockState standing(int rotation) {
        return ModRegistries.KINGDOM_FLAG.get().defaultBlockState().setValue(KingdomFlagBlock.ROTATION, Math.floorMod(rotation, 16));
    }

    /**
     * A flag on a wall facing a way.
     */
    public static BlockState wall(Direction facing) {
        return ModRegistries.KINGDOM_WALL_FLAG.get().defaultBlockState().setValue(KingdomWallFlagBlock.FACING, facing);
    }

    /**
     * The state of a flag at a place found for one, in the space of its template.
     */
    public static BlockState of(FlagSpots.Spot spot) {
        return spot.kind() == FlagSpots.Kind.WALL ? wall(direction(spot.facing())) : standing(spot.rotation());
    }

    public static Direction direction(Facing facing) {
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }
}
