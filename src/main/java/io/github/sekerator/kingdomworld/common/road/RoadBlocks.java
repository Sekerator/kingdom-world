package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What roads are made of and how a block of road goes into the world. Materials come by item tags, so blocks of mods do
 * too: {@code kingdomworld:road_gravel}, {@code kingdomworld:road_paving}, {@code kingdomworld:road_bridge} and
 * {@code kingdomworld:road_fill}. A path needs no material: the grass becomes a path, as with a shovel.
 *
 * <p>Placing never breaks what must not be broken: a protected block, a block with items or any block entity stays, and
 * the block of road is left out, its paid items going back. What the road replaces gives its drops to the storehouses.
 */
public final class RoadBlocks {
    public static final TagKey<Item> GRAVEL = tag("road_gravel");
    public static final TagKey<Item> PAVING = tag("road_paving");
    public static final TagKey<Item> BRIDGE = tag("road_bridge");
    public static final TagKey<Item> FILL = tag("road_fill");

    private RoadBlocks() {
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(KingdomWorld.MOD_ID, name));
    }

    /**
     * The tag of items of a material for a class of road.
     */
    public static TagKey<Item> tagOf(RoadLayout.Material material, RoadClass roadClass) {
        return switch (material) {
            case SURFACE -> roadClass == RoadClass.PAVED ? PAVING : GRAVEL;
            case BRIDGE -> BRIDGE;
            case FILL -> FILL;
        };
    }

    /**
     * Whether an item is good for a material: a block of the tag.
     */
    public static boolean fits(Item item, RoadLayout.Material material, RoadClass roadClass) {
        return item instanceof BlockItem && new ItemStack(item).is(tagOf(material, roadClass));
    }

    /**
     * Whether a player stands on the road itself and not on grass or earth beside it.
     */
    public static boolean isRoadSurface(BlockState state) {
        return !state.isAir() && !state.is(BlockTags.DIRT) && !state.canBeReplaced();
    }

    /**
     * Gives items back to the storehouses of a kingdom; what does not fit falls at the place.
     */
    static void giveBack(ServerLevel level, UUID kingdomId, BlockPos at, Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item == null) {
                continue;
            }
            int left = entry.getValue();
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                left -= count;
                store(level, kingdomId, at, new ItemStack(item, count));
            }
        }
    }

    static void store(ServerLevel level, UUID kingdomId, BlockPos at, ItemStack stack) {
        ItemStack rest = KingdomStorage.insert(level.getServer(), kingdomId, stack, ConstructionWork.worldPos(level, at));
        if (!rest.isEmpty()) {
            level.getChunk(at);
            Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rest);
        }
    }

    /**
     * Whether nothing of value stands at a place: air, a liquid or a plant that any block replaces.
     */
    static boolean free(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * Whether a block may be replaced by road: not protected, holding no items and not a block entity.
     */
    static boolean replaceable(ServerLevel level, BlockPos pos, BlockState state) {
        return !ConstructionWork.isProtected(level, pos, state) && !state.hasBlockEntity() && !ConstructionWork.holdsItems(level, pos);
    }

    /**
     * Takes an item of a material from the paid items of a section.
     *
     * @return the item, or {@code null} if none fits
     */
    static Item usePaid(io.github.sekerator.kingdomworld.core.road.Road.Section section, RoadLayout.Material material, RoadClass roadClass) {
        for (String id : new ArrayList<>(section.pool().keySet())) {
            Item item = PostWork.item(id);
            if (item != null && fits(item, material, roadClass) && section.use(id)) {
                return item;
            }
        }
        return null;
    }

    /**
     * The height of the ground near a height at a column: the top block that is not free, within two blocks up or down.
     */
    static int groundNear(ServerLevel level, int x, int y, int z) {
        for (int at = y + 2; at >= y - 3; at--) {
            BlockState state = level.getBlockState(new BlockPos(x, at, z));
            if (!free(state) && state.getFluidState().isEmpty()) {
                return at;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Lays one block of road whose items were paid for. Items it does not use go back to the storehouses of the builder.
     *
     * @return whether anything was placed
     */
    static boolean place(ServerLevel level, io.github.sekerator.kingdomworld.core.road.Road road,
                         io.github.sekerator.kingdomworld.core.road.Road.Section section, RoadLayout.Block block) {
        RoadClass roadClass = road.roadClass();
        UUID builder = section.builder();
        List<RoadLayout.Material> needs = block.needs(roadClass);
        List<Item> items = new ArrayList<>();
        for (RoadLayout.Material material : needs) {
            items.add(usePaid(section, material, roadClass));
        }
        BlockPos top = new BlockPos(block.x(), block.y(), block.z());
        boolean placed = false;
        int used = 0;
        switch (block.kind()) {
            case GROUND -> {
                int ground = block.side() ? groundNear(level, block.x(), block.y(), block.z()) : block.y();
                if (ground != Integer.MIN_VALUE && Math.abs(ground - block.y()) <= 1) {
                    BlockPos pos = new BlockPos(block.x(), ground, block.z());
                    if (roadClass == RoadClass.PATH) {
                        placed = makePath(level, pos);
                    } else if (!items.isEmpty() && items.get(0) != null) {
                        placed = replace(level, builder, pos, items.get(0));
                        used = placed ? 1 : 0;
                    }
                }
            }
            case BRIDGE -> {
                if (!items.isEmpty() && items.get(0) != null && free(level.getBlockState(top))) {
                    clearAbove(level, builder, top);
                    ConstructionWork.place(level, top, stateOf(items.get(0)), items.get(0));
                    placed = true;
                    used = 1;
                }
            }
            case FILL -> {
                int ground = groundNear(level, block.x(), block.y() - block.depth() - 1, block.z());
                if (ground == Integer.MIN_VALUE) {
                    ground = block.y() - block.depth() - 1;
                }
                // The embankment from the ground up, then the top of the road.
                int index = 0;
                for (int y = ground + 1; y < block.y() && index < items.size() - 1; y++) {
                    BlockPos pos = new BlockPos(block.x(), y, block.z());
                    Item item = items.get(index);
                    if (item != null && free(level.getBlockState(pos))) {
                        ConstructionWork.place(level, pos, stateOf(item), item);
                        items.set(index, null);
                        placed = true;
                    }
                    index++;
                }
                Item last = items.get(items.size() - 1);
                if (last != null && free(level.getBlockState(top))) {
                    clearAbove(level, builder, top);
                    ConstructionWork.place(level, top, stateOf(last), last);
                    items.set(items.size() - 1, null);
                    if (roadClass == RoadClass.PATH) {
                        makePath(level, top);
                    }
                    placed = true;
                }
                // Every item placed was taken out of the list; the rest goes back.
                used = 0;
            }
        }
        // What was paid and not used goes back.
        for (int i = used; i < items.size(); i++) {
            Item item = items.get(i);
            if (item != null) {
                store(level, builder, top, new ItemStack(item));
            }
        }
        return placed;
    }

    private static BlockState stateOf(Item item) {
        return item instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /**
     * Turns grass or earth into a path, as a shovel does; anything else stays.
     */
    private static boolean makePath(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.DIRT_PATH)) {
            return true;
        }
        if (!(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT))) {
            return false;
        }
        if (!free(level.getBlockState(pos.above()))) {
            return false;
        }
        clearAbove(level, null, pos);
        level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * Replaces the ground at a place with a block of road: the ground's drops go to the storehouses.
     */
    private static boolean replace(ServerLevel level, UUID builder, BlockPos pos, Item item) {
        BlockState state = level.getBlockState(pos);
        BlockState target = stateOf(item);
        if (state.is(target.getBlock())) {
            return false;
        }
        if (!replaceable(level, pos, state) || !free(level.getBlockState(pos.above()))) {
            return false;
        }
        clearAbove(level, builder, pos);
        if (!state.isAir()) {
            List<ItemStack> drops = Block.getDrops(state, level, pos, null);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (builder != null) {
                for (ItemStack drop : drops) {
                    store(level, builder, pos, drop);
                }
            }
        }
        ConstructionWork.place(level, pos, target, item);
        return true;
    }

    /**
     * Grass, flowers and snow on top of a block of road go; their drops go to the storehouses.
     */
    private static void clearAbove(ServerLevel level, UUID builder, BlockPos pos) {
        for (int up = 1; up <= 2; up++) {
            BlockPos above = pos.above(up);
            BlockState state = level.getBlockState(above);
            if (state.isAir() || !state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || ConstructionWork.isProtected(level, above, state)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, above, null);
            level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (builder != null) {
                for (ItemStack drop : drops) {
                    store(level, builder, above, drop);
                }
            }
        }
    }

    /**
     * Whether the owner of the land at a block of road let the road be built there.
     */
    static boolean allowedAt(ServerLevel level, io.github.sekerator.kingdomworld.core.road.Road road, BlockPos pos,
                             java.util.Set<UUID> consented) {
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner == null || owner.equals(road.kingdomId()) || consented.contains(owner)
                || io.github.sekerator.kingdomworld.common.land.Wilderness.isWild(level.getServer(), io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level),
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /**
     * Whether a block of the middle of a road is a bridge or embankment.
     */
    static boolean raised(RouteCell cell) {
        return cell.kind() != RouteCell.Kind.GROUND;
    }
}
