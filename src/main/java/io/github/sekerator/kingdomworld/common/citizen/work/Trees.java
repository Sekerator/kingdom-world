package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
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
     * Whether a log was worked by a player: stripped logs and bark blocks ({@code oak_wood}) do not grow on trees.
     */
    public static boolean isWorkedWood(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.contains("stripped") || path.endsWith("_wood") || path.endsWith("_hyphae");
    }

    /**
     * Whether a log lies on its side (axis x or z), as logs of walls do.
     */
    public static boolean isLying(BlockState state) {
        return state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
    }

    /**
     * Whether a block was clearly placed by a player: planks, stairs, slabs, doors, glass, fences, beds, wool,
     * signs, torches, worked wood, leaves placed by hand and blocks with contents. Bee nests grow on trees and do not
     * count.
     */
    public static boolean isBuildingBlock(BlockState state) {
        if (state.isAir() || state.is(BlockTags.BEEHIVES)) {
            return false;
        }
        if (state.is(BlockTags.LEAVES)) {
            return !isNaturalLeaves(state);
        }
        if (state.hasBlockEntity() || isLog(state) && isWorkedWood(state)) {
            return true;
        }
        Block block = state.getBlock();
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WALLS) || state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
                || state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.CANDLES) || block instanceof TorchBlock || block instanceof LanternBlock
                || block instanceof LadderBlock;
    }

    /**
     * Whether a block touches a block placed by a player on any side.
     */
    public static boolean touchesBuilding(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (level.isLoaded(side) && isBuildingBlock(level.getBlockState(side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the tree that stands on a base log, if it is a natural tree inside the zone and on the land.
     *
     * <p>A tree is made of logs of one kind connected to its base, never stripped logs or bark blocks; logs that lie
     * on their side near the ground are the walls of a building, not branches. A column of logs that touches a block
     * a player placed (planks, glass, a door) belongs to a building and stays standing whole, together with whatever
     * hangs on it; if the trunk itself is such a column, the whole tree stays.
     *
     * @return {@code null} for anything that is not a natural tree, or a tree too large to fell
     */
    @Nullable
    public static Tree find(ServerLevel level, BlockPos base, WorkZone zone, LandCheck land, int maxLogs) {
        BlockState baseState = level.getBlockState(base);
        if (!isLog(baseState) || isWorkedWood(baseState) || isLying(baseState)) {
            return null;
        }
        Block wood = baseState.getBlock();
        Set<BlockPos> logs = connected(level, base, wood, maxLogs, zone, land);
        if (logs == null) {
            return null;
        }
        // Columns of logs that touch a building are part of it.
        Map<Long, Boolean> builtColumns = new HashMap<>();
        for (BlockPos log : logs) {
            long column = BlockPos.asLong(log.getX(), 0, log.getZ());
            if (!builtColumns.getOrDefault(column, false) && touchesBuilding(level, log)) {
                builtColumns.put(column, true);
            }
        }
        if (builtColumns.getOrDefault(BlockPos.asLong(base.getX(), 0, base.getZ()), false)) {
            return null;
        }
        if (!builtColumns.isEmpty()) {
            Set<BlockPos> natural = new HashSet<>();
            for (BlockPos log : logs) {
                if (!builtColumns.getOrDefault(BlockPos.asLong(log.getX(), 0, log.getZ()), false)) {
                    natural.add(log);
                }
            }
            logs = reachable(base, natural);
        }
        // The leaves of the crown, and whether there are enough natural ones to call it a tree.
        Set<BlockPos> leaves = new HashSet<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        Map<Block, Integer> leafKinds = new HashMap<>();
        for (BlockPos log : logs) {
            for (Direction direction : Direction.values()) {
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
            for (Direction direction : Direction.values()) {
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
     * Logs of the kind connected to the base, at its height and above: diagonal steps count, as branches and acacia
     * trunks grow that way, but logs lying on their side close to the ground do not, because those are walls.
     *
     * @return {@code null} if the logs reach out of the zone or off the land, or are more than {@code maxLogs}
     */
    @Nullable
    private static Set<BlockPos> connected(ServerLevel level, BlockPos base, Block wood, int maxLogs, WorkZone zone, LandCheck land) {
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
                        BlockState state = level.getBlockState(next);
                        // Logs of another kind belong to something else; lying logs near the ground are walls.
                        if (!state.is(wood) || isWorkedWood(state) || isLying(state) && next.getY() < base.getY() + 2) {
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
        return logs;
    }

    /**
     * The logs of a set that are still connected to the base (by any of the 26 neighbours).
     */
    private static Set<BlockPos> reachable(BlockPos base, Set<BlockPos> logs) {
        Set<BlockPos> result = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        result.add(base);
        open.add(base);
        while (!open.isEmpty()) {
            BlockPos pos = open.poll();
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (logs.contains(near) && !result.contains(near)) {
                    BlockPos found = near.immutable();
                    result.add(found);
                    open.add(found);
                }
            }
        }
        return result;
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
