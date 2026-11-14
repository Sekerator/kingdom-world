package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.construction.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The land as it grew, to tell where a building may stand without taking anything of anybody: earth, stone and sand below the
 * street, only air, wild plants and trees with their crowns above it. Shared by the rulers of the world, who renew their
 * settlements on it, and by the orders of the king from afar, which never lay a building where a player built something.
 */
public final class NaturalGround {
    private NaturalGround() {
    }

    /**
     * Whether a column holds only what grew there from {@code from} to {@code to}: the ground as it grew up to the street (or the
     * paving the kingdom laid there), over it only air, wild plants, snow and trees with their crowns. A block made by somebody, a
     * field, a crop, water, a solid block over the street or a container anywhere and the column is taken.
     *
     * @param street the height of the street: the ground below it, the open air above it
     * @param laid   the top the kingdom laid at the street there
     * @param hall   a place to pass over (the town hall a residence stands round), or {@link Long#MIN_VALUE}
     */
    public static boolean column(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int street, int from, int to, BlockState laid,
                                 long hall) {
        return blocker(level, pos, x, z, street, from, to, laid, hall) == Integer.MIN_VALUE;
    }

    /**
     * The height of the first block of a column that is not as it grew, or {@link Integer#MIN_VALUE} if every block is; logs
     * without a crown over them count as made by somebody at the height of the highest log.
     */
    static int blocker(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int street, int from, int to, BlockState laid, long hall) {
        boolean log = false;
        boolean leaves = false;
        int lastLog = Integer.MIN_VALUE;
        for (int by = from; by <= to; by++) {
            BlockState there = level.getBlockState(pos.set(x, by, z));
            if (pos.asLong() == hall) {
                continue;
            }
            if (!there.isAir() && (protectedAt(level, pos, there) || there.hasBlockEntity())) {
                return by;
            }
            if (there.is(BlockTags.LOGS)) {
                log = true;
                lastLog = by;
                continue;
            }
            if (there.is(BlockTags.LEAVES)) {
                if (placedLeaves(there)) {
                    // A hedge or a roof of leaves: no trunk of a tree holds them.
                    return by;
                }
                leaves |= log;
                continue;
            }
            boolean fits = by <= street ? ground(there) || by == street && there.equals(laid) : wild(there);
            if (!fits) {
                return by;
            }
        }
        // A trunk is a tree when its crown is over it; logs with nothing over them were laid by somebody.
        return !log || leaves ? Integer.MIN_VALUE : lastLog;
    }

    /**
     * Whether a block is protected or holds items: a construction never takes it, nor does an order look further there.
     */
    static boolean protectedAt(ServerLevel level, BlockPos pos, BlockState state) {
        return ConstructionWork.isProtected(level, pos, state) || ConstructionWork.holdsItems(level, pos);
    }

    /**
     * Whether leaves were placed by somebody rather than grown on a tree: placed leaves never fall, and leaves more than six
     * blocks from a trunk would have fallen unless placed.
     */
    public static boolean placedLeaves(BlockState state) {
        return state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)
                || state.hasProperty(LeavesBlock.DISTANCE) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE;
    }

    /**
     * Whether a block is the ground as it grew: earth, sand, gravel, stone and ore, sandstone, terracotta, clay, snow and ice,
     * or a hollow in it. Farmland, paths, water and everything made are not.
     */
    public static boolean ground(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES);
    }

    /**
     * Whether a block may stand over the street on ground a building takes: air, a wild plant (grass, a fern, a flower, a
     * sapling, a mushroom, a bush), a vine or a layer of snow. A crop, sugar cane, a pumpkin, water and every solid block may be
     * somebody's, and are not.
     */
    public static boolean wild(BlockState state) {
        if (state.isAir() || state.is(Blocks.SNOW) || state.is(Blocks.VINE)) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM);
    }

    /**
     * The height of the solid ground of a column: below trees, plants, snow and water.
     */
    public static int groundAt(ServerLevel level, int x, int z) {
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
    public static boolean soft(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)
                || io.github.sekerator.kingdomworld.common.citizen.work.Trees.isLog(state) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)
                || state.is(BlockTags.SNOW) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Where a building of a box would take something of somebody: the first block that is not as it grew (a block with an
     * inventory or of the mod, a field, a path, a crop, water, a solid block over the street, leaves placed or logs without a
     * crown), a column not loaded or off the land of the kingdom; {@code null} if the ground of the whole box is free. Every
     * column of the box is looked at from {@code bottom} to {@code top}.
     *
     * @param street the height of the street: the ground below it, the open air above it
     */
    @Nullable
    public static BlockPos free(ServerLevel level, UUID kingdomId, Box box, int street, int bottom, int top) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                pos.set(x, street, z);
                if (!ChunkHold.loaded(level, pos) || !CityPlots.ownLand(level.getServer(), kingdomId,
                        io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level),
                        io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4))) {
                    return pos.immutable();
                }
                int y = blocker(level, pos, x, z, street, bottom, top, Blocks.AIR.defaultBlockState(), Long.MIN_VALUE);
                if (y != Integer.MIN_VALUE) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        return null;
    }
}
