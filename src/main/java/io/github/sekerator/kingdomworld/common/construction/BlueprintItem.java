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

    public BlueprintItem(Properties properties) {
        super(properties);
    }

    /**
     * Writes the template into a blueprint.
     */
    public static ItemStack describe(ItemStack stack, BuildingTemplate template) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TEMPLATE, template.id().toString());
        tag.putString(NAME, template.nameKey());
        tag.putString(FALLBACK, template.fallbackName());
        return stack;
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
