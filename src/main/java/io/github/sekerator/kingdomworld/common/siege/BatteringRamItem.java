package io.github.sekerator.kingdomworld.common.siege;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A ram: a log shod with iron. In the hands of a player it breaks gate leaves, palisades and walls of stone faster; in the
 * storehouse of a kingdom that besieges a city its soldiers bring it to the gate, and they strike harder with it.
 */
public class BatteringRamItem extends Item {
    public BatteringRamItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdomworld.battering_ram.desc").withStyle(ChatFormatting.GRAY));
    }
}
