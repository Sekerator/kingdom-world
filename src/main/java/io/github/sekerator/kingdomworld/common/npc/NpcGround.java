package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * Levelling the ground of a kingdom of the world: columns are cut down to a height and filled up to it, trees over them go,
 * and only the kingdom's own land is touched. Blocks no player may break, containers and the blocks of the mod stay. Nothing
 * is dropped: what the ground was made of stays the world's.
 */
final class NpcGround {
    /** How deep a hole is filled at most, in blocks. */
    static final int MAX_FILL = 12;
    /** How high above the ground a column is cleared, in blocks. */
    static final int CLEAR_HEIGHT = 24;
    /**
     * How much higher the trees of a column go: where a hill is cut down, the crown of a tall tree on it rises over what is
     * cleared, and would hang in the air.
     */
    private static final int TREE_TOPS = 24;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /**
     * The ground of a place: its top block and what lies under it.
     */
    record Soil(BlockState top, BlockState fill) {
        static final Soil GRASS = new Soil(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        static final Soil SAND = new Soil(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
        static final Soil RED_SAND = new Soil(Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState());
    }

    private NpcGround() {
    }

    /**
     * The soil of the ground at a column: sand for sand, grass for anything else.
     */
    static Soil soilAt(ServerLevel level, int x, int z) {
        BlockState top = level.getBlockState(new BlockPos(x, groundAt(level, x, z), z));
        if (top.is(Blocks.RED_SAND)) {
            return Soil.RED_SAND;
        }
        if (top.is(BlockTags.SAND)) {
            return Soil.SAND;
        }
        return Soil.GRASS;
    }

    /**
     * The height of the solid ground of a column: below trees, plants, snow and water.
     */
    static int groundAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight()) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (!soft(state)) {
                break;
            }
            y--;
        }
        return y;
    }

    /**
     * Whether a block is not ground: air, a plant, leaves, a log, snow or a liquid.
     */
    static boolean soft(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)
                || Trees.isLog(state) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.SNOW)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Levels a column to a height: everything above it goes, the hole under it is filled, and its top becomes {@code top}.
     *
     * @param owner the kingdom whose land the column must be on
     * @return blocks changed, or -1 if the column is not the kingdom's land
     */
    static int level(ServerLevel level, UUID owner, int x, int z, int height, BlockState top, BlockState fill) {
        if (!owner.equals(io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.ownerAt(level, new BlockPos(x, height, z)))) {
            return -1;
        }
        int changed = clearAbove(level, x, z, height);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, height, z);
        BlockState at = level.getBlockState(pos);
        if (protectedAt(level, pos, at)) {
            return changed;
        }
        if (!at.equals(top)) {
            level.setBlock(pos, top, FLAGS);
            changed++;
        }
        for (int y = height - 1; y > height - MAX_FILL && y > level.getMinBuildHeight(); y--) {
            pos.setY(y);
            BlockState below = level.getBlockState(pos);
            if (!soft(below)) {
                break;
            }
            if (protectedAt(level, pos, below)) {
                break;
            }
            level.setBlock(pos, fill, FLAGS);
            changed++;
        }
        return changed;
    }

    /**
     * Clears a column above a height: up to {@value #CLEAR_HEIGHT} blocks over it, and the parts of trees up to
     * {@value #TREE_TOPS} blocks higher.
     */
    static int clearAbove(ServerLevel level, int x, int z, int height) {
        int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, height + CLEAR_HEIGHT + TREE_TOPS);
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > height; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || protectedAt(level, pos, state) || y > height + CLEAR_HEIGHT && !treePart(state)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            changed++;
        }
        return changed;
    }

    /**
     * Takes trees and tall plants off a column above its ground, leaving the ground as it is.
     */
    static int clearTrees(ServerLevel level, UUID owner, int x, int z) {
        if (!owner.equals(io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.ownerAt(level, new BlockPos(x, 0, z)))) {
            return 0;
        }
        int ground = groundAt(level, x, z);
        int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, ground + CLEAR_HEIGHT + 8);
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > ground; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (treePart(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Whether a block is part of a tree or of a huge mushroom.
     */
    private static boolean treePart(BlockState state) {
        return state.is(BlockTags.LEAVES) || Trees.isLog(state) || state.is(Blocks.VINE) || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    static boolean protectedAt(ServerLevel level, BlockPos pos, BlockState state) {
        return ConstructionWork.isProtected(level, pos, state) || ConstructionWork.holdsItems(level, pos);
    }

    /**
     * Sets a block where nothing protected stands, on the kingdom's land.
     */
    static boolean set(ServerLevel level, UUID owner, BlockPos pos, BlockState state) {
        if (!owner.equals(io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.ownerAt(level, pos))) {
            return false;
        }
        BlockState there = level.getBlockState(pos);
        if (protectedAt(level, pos, there)) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return true;
    }
}
