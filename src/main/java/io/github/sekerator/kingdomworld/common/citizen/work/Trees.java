package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Natural trees, as a lumberjack sees them: logs (tag {@code minecraft:logs}) standing on the ground with leaves
 * that grew there (tag {@code minecraft:leaves}, not placed by a player, so {@code persistent=false}). Logs
 * without such leaves belong to a building and are never felled. Trees of other mods have the same tags and
 * properties and are felled like vanilla ones.
 */
public final class Trees {
    /** Natural leaves a tree must touch at least. */
    private static final int MIN_LEAVES = 3;
    /** Leaves taken down with a tree at most. */
    private static final int MAX_LEAVES = 320;
    /** How far from the logs leaves still belong to the tree. */
    private static final int LEAF_DISTANCE = 6;

    /** The sapling each kind of leaves drops, found once by rolling its loot table. */
    private static final Map<Block, Item> SAPLING_OF = new HashMap<>();

    /**
     * A tree to fell.
     *
     * @param bases  logs standing on the ground, where saplings go
     * @param logs   every log, from the top down
     * @param leaves the natural leaves of its crown
     */
    public record Tree(List<BlockPos> bases, List<BlockPos> logs, List<BlockPos> leaves, @Nullable BlockState leafState) {
    }

    private Trees() {
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    /**
     * Leaves that grew on a tree: a block with the leaves tag that a player did not place.
     */
    public static boolean isNaturalLeaves(BlockState state) {
        if (!state.is(BlockTags.LEAVES)) {
            return false;
        }
        return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * Whether a log stands on the ground: the block below is neither a log nor air or water.
     */
    public static boolean isBase(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isLog(state)) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return !isLog(below) && !below.isAir() && below.getFluidState().isEmpty() && !below.is(BlockTags.LEAVES);
    }

    /**
     * Finds the tree that stands on a base log, if it is a natural tree inside the zone and on the land.
     *
     * @return {@code null} for anything that is not a natural tree, or a tree too large to fell
     */
    @Nullable
    public static Tree find(ServerLevel level, BlockPos base, WorkZone zone, LandCheck land, int maxLogs) {
        Set<BlockPos> logs = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(base.immutable());
        logs.add(base.immutable());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!open.isEmpty()) {
            BlockPos pos = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        next.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        if (logs.contains(next) || next.getY() < base.getY() || !level.isLoaded(next)) {
                            continue;
                        }
                        if (!isLog(level.getBlockState(next))) {
                            continue;
                        }
                        if (!zone.contains(next.getX(), next.getY(), next.getZ()) || !land.owns(next)) {
                            // A tree that reaches out of the zone or off the land is left standing.
                            return null;
                        }
                        BlockPos found = next.immutable();
                        logs.add(found);
                        open.add(found);
                        if (logs.size() > maxLogs) {
                            return null;
                        }
                    }
                }
            }
        }
        // The leaves of the crown, and whether there are enough natural ones to call it a tree.
        Set<BlockPos> leaves = new HashSet<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        Map<Block, Integer> leafKinds = new HashMap<>();
        for (BlockPos log : logs) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos side = log.relative(direction);
                if (!leaves.contains(side) && level.isLoaded(side) && isNaturalLeaves(level.getBlockState(side))) {
                    leaves.add(side);
                    distance.put(side, 1);
                    frontier.add(side);
                }
            }
        }
        if (leaves.size() < MIN_LEAVES) {
            return null;
        }
        while (!frontier.isEmpty() && leaves.size() < MAX_LEAVES) {
            BlockPos pos = frontier.poll();
            int d = distance.get(pos);
            if (d >= LEAF_DISTANCE) {
                continue;
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos side = pos.relative(direction);
                if (leaves.contains(side) || !level.isLoaded(side) || !zone.contains(side.getX(), side.getY(), side.getZ())
                        || !land.owns(side)) {
                    continue;
                }
                BlockState state = level.getBlockState(side);
                if (isNaturalLeaves(state)) {
                    leaves.add(side);
                    distance.put(side, d + 1);
                    frontier.add(side);
                }
            }
        }
        BlockState leafState = null;
        int best = 0;
        for (BlockPos leaf : leaves) {
            BlockState state = level.getBlockState(leaf);
            int count = leafKinds.merge(state.getBlock(), 1, Integer::sum);
            if (count > best) {
                best = count;
                leafState = state;
            }
        }
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos log : logs) {
            if (log.getY() == base.getY() && isBase(level, log, level.getBlockState(log))) {
                bases.add(log);
            }
        }
        List<BlockPos> ordered = new ArrayList<>(logs);
        ordered.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        List<BlockPos> crown = new ArrayList<>(leaves);
        crown.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()));
        return new Tree(bases, ordered, crown, leafState);
    }

    /**
     * What breaking a block drops, by its loot table, with the given tool.
     */
    public static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, @Nullable Entity breaker) {
        return Block.getDrops(state, level, pos, level.getBlockEntity(pos), breaker, tool);
    }

    /**
     * The sapling a kind of leaves drops, or {@code null} if it drops none: found by rolling the leaves' loot table
     * a number of times, once per kind of leaves.
     */
    @Nullable
    public static Item saplingOf(ServerLevel level, BlockState leaves) {
        Block block = leaves.getBlock();
        if (SAPLING_OF.containsKey(block)) {
            return SAPLING_OF.get(block);
        }
        Item found = null;
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
        for (int roll = 0; roll < 200 && found == null; roll++) {
            for (ItemStack drop : leaves.getDrops(params)) {
                if (isSapling(drop)) {
                    found = drop.getItem();
                    break;
                }
            }
        }
        SAPLING_OF.put(block, found);
        return found;
    }

    public static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem;
    }

    /**
     * The block of a sapling item, if it is one.
     */
    @Nullable
    public static Block saplingBlock(ItemStack stack) {
        return isSapling(stack) ? ((BlockItem) stack.getItem()).getBlock() : null;
    }
}
