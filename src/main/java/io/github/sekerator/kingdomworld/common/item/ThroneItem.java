package io.github.sekerator.kingdomworld.common.item;

import io.github.sekerator.kingdomworld.common.kingdom.ThroneMoving;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The throne as an item. A plain throne is placed as any block. A throne handed out for moving the throne of a kingdom carries
 * the kingdom and the move ({@link ThroneMoving}): it shines, names its kingdom, does not stack, and placed by the king on a
 * chunk the kingdom claimed it becomes the heart of the kingdom at once, the old throne going without a drop. A throne of a move
 * that was finished or called off vanishes when somebody tries to place it.
 */
public class ThroneItem extends BlockItem {

    public ThroneItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Optional<ThroneMoving.Binding> binding = ThroneMoving.binding(context.getItemInHand());
        if (binding.isEmpty()) {
            return super.place(context);
        }
        if (context.getLevel().isClientSide) {
            // Only the server knows whether the throne may stand here; it places it and the client is told.
            return InteractionResult.SUCCESS;
        }
        return ThroneMoving.place(context, binding.get(), () -> super.place(context));
    }

    @Override
    public Component getName(ItemStack stack) {
        return ThroneMoving.binding(stack).isPresent() ? Component.translatable("item.kingdomworld.throne_move") : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ThroneMoving.binding(stack).isPresent() || super.isFoil(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return ThroneMoving.binding(stack).isPresent() ? 1 : super.getMaxStackSize(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<ThroneMoving.Binding> binding = ThroneMoving.binding(stack);
        if (binding.isEmpty()) {
            super.appendHoverText(stack, level, tooltip, flag);
            return;
        }
        Component kingdom = Component.literal(binding.get().name())
                .withStyle(style -> style.withColor(TextColor.fromRgb(binding.get().color())));
        tooltip.add(Component.translatable("item.kingdomworld.throne_move.kingdom", kingdom).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.kingdomworld.throne_move.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdomworld.throne_move.king").withStyle(ChatFormatting.DARK_GRAY));
    }
}
