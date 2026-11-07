package io.github.sekerator.kingdomworld.common.construction;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A blueprint of one building template. The king takes it in the construction tab of the throne; held in the hand it shows
 * the building in the world where it would stand, and a click lays out the construction there. It is worth nothing and
 * cannot be made: dropped, it is gone.
 */
public class BlueprintItem extends Item {
    private static final String TEMPLATE = "template";
    private static final String NAME = "name";
    private static final String FALLBACK = "fallback";
    private static final String WOOD = "wood";
    private static final String STONE = "stone";

    public BlueprintItem(Properties properties) {
        super(properties);
    }

    /**
     * Writes the template into a blueprint.
     */
    public static ItemStack describe(ItemStack stack, BuildingTemplate template) {
        return describe(stack, template, io.github.sekerator.kingdomworld.core.construction.Materials.NONE);
    }

    /**
     * Writes the template and the wood and stone to build it of into a blueprint.
     */
    public static ItemStack describe(ItemStack stack, BuildingTemplate template, io.github.sekerator.kingdomworld.core.construction.Materials materials) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TEMPLATE, template.id().toString());
        tag.putString(NAME, template.nameKey());
        tag.putString(FALLBACK, template.fallbackName());
        tag.putString(WOOD, materials.wood());
        tag.putString(STONE, materials.stone());
        return stack;
    }

    /**
     * The wood and stone a blueprint builds of.
     */
    public static io.github.sekerator.kingdomworld.core.construction.Materials materials(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? io.github.sekerator.kingdomworld.core.construction.Materials.NONE
                : new io.github.sekerator.kingdomworld.core.construction.Materials(tag.getString(WOOD), tag.getString(STONE));
    }

    /**
     * The id of the template of a blueprint, or empty.
     */
    public static String templateId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TEMPLATE);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.getString(NAME).isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable("item.kingdomworld.blueprint.named", BuildingTemplate.name(tag.getString(NAME), tag.getString(FALLBACK)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdomworld.blueprint.tooltip").withStyle(ChatFormatting.GRAY));
        io.github.sekerator.kingdomworld.core.construction.Materials materials = materials(stack);
        if (io.github.sekerator.kingdomworld.core.construction.Materials.AUTO.equals(materials.wood())) {
            tooltip.add(Component.translatable("item.kingdomworld.blueprint.wood_auto").withStyle(ChatFormatting.GRAY));
        } else if (!materials.wood().isEmpty()) {
            tooltip.add(Component.translatable("item.kingdomworld.blueprint.wood", TemplateMaterials.woodName(materials.wood()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!materials.stone().isEmpty()) {
            tooltip.add(Component.translatable("item.kingdomworld.blueprint.stone", TemplateMaterials.stoneName(materials.stone()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        // A blueprint is only a picture of the plan: dropped, it is gone.
        if (!entity.level().isClientSide) {
            entity.discard();
        }
        return true;
    }
}
