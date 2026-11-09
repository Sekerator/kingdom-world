package io.github.sekerator.kingdomworld.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An item with a one-line translated tooltip that explains what it is for.
 */
public class DescribedItem extends Item {
    private final String tooltipKey;
    private final String[] moreKeys;

    /**
     * @param moreKeys further lines, in a darker grey
     */
    public DescribedItem(Properties properties, String tooltipKey, String... moreKeys) {
        super(properties);
        this.tooltipKey = tooltipKey;
        this.moreKeys = moreKeys.clone();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        for (String key : moreKeys) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
