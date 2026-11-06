package io.github.sekerator.kingdomworld.common.citizen.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Crops in general: vanilla crops and crops of other mods are recognised by {@link CropBlock} or the
 * {@code minecraft:crops} tag and an age property; seeds are taken from what the crop drops, so modded crops are
 * replanted with their own seeds.
 */
public final class FarmCrops {

    private FarmCrops() {
    }

    /**
     * Whether the block is a crop a farmer tends: a single block plant that is harvested whole. Stems (melons,
     * pumpkins) and two block plants are left alone.
     */
    public static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        return block instanceof CropBlock || (state.is(BlockTags.CROPS) && ageProperty(state) != null);
    }

    /**
     * Whether a crop is ripe.
     */
    public static boolean isMature(BlockState state) {
        if (!isCrop(state)) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return false;
        }
        int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
        return state.getValue(age) >= max;
    }

    @Nullable
    private static IntegerProperty ageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && "age".equals(property.getName())) {
                return integer;
            }
        }
        return null;
    }

    /**
     * The crop as freshly planted.
     */
    public static BlockState planted(BlockState ripe) {
        if (ripe.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        return ripe.getBlock().defaultBlockState();
    }

    /**
     * What harvesting the crop drops, by its loot table.
     */
    public static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity harvester) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, harvester)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);
        return new ArrayList<>(state.getDrops(params));
    }

    /**
     * Whether an item plants the crop: it places the crop's block, or it is what picking the crop gives.
     */
    public static boolean isSeedOf(ItemStack stack, BlockState crop, ServerLevel level, BlockPos pos) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == crop.getBlock()) {
            return true;
        }
        ItemStack clone = crop.getBlock().getCloneItemStack(level, pos, crop);
        return !clone.isEmpty() && clone.getItem() == stack.getItem() && clone.getItem() instanceof BlockItem;
    }

    /**
     * Takes one seed for the crop out of the drops.
     *
     * @return whether a seed was found
     */
    public static boolean takeSeed(List<ItemStack> drops, BlockState crop, ServerLevel level, BlockPos pos) {
        for (ItemStack drop : drops) {
            if (isSeedOf(drop, crop, level, pos)) {
                drop.shrink(1);
                drops.removeIf(ItemStack::isEmpty);
                return true;
            }
        }
        return false;
    }

    /**
     * Takes one seed for the crop out of a bag, for a harvest that dropped none.
     *
     * @return whether a seed was found
     */
    public static boolean takeSeed(net.minecraft.world.Container bag, BlockState crop, ServerLevel level, BlockPos pos) {
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (isSeedOf(bag.getItem(slot), crop, level, pos)) {
                bag.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * The main product of a crop, what it is grown for: the drop that is not its seed (wheat, not wheat seeds), or
     * the seed for crops that are their own seed (carrots, potatoes).
     */
    @Nullable
    public static Item product(List<ItemStack> drops, BlockState crop, ServerLevel level, BlockPos pos) {
        Item fallback = null;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            if (!isSeedOf(drop, crop, level, pos)) {
                return drop.getItem();
            }
            if (fallback == null) {
                fallback = drop.getItem();
            }
        }
        return fallback;
    }
}
