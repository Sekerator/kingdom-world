package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Survey stakes: two corners of a rectangle of land marked on blocks, as the architect's ruler marks them, and the window of
 * earthworks opens once the second is set (or again with a use in the air). Using them in the air while sneaking pulls the
 * stakes out.
 */
public class SurveyStakesItem extends Item {
    public SurveyStakesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return pull(context.getLevel(), player, context.getItemInHand()).getResult();
        }
        BlockPos pos = context.getClickedPos();
        // Both sides mark the corner: the server keeps it, the client opens the window at once.
        ArchitectRulerItem.Corners corners = ArchitectRulerItem.mark(context.getItemInHand(), pos, Dimensions.id(context.getLevel()));
        if (context.getLevel().isClientSide) {
            if (corners.complete()) {
                ArchitectRulerItem.Corners marked = corners;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.sekerator.kingdomworld.client.ClientHooks.openEarthwork(marked));
            }
        } else {
            String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (corners.complete()) {
                int[] size = corners.size();
                player.displayClientMessage(Component.translatable("kingdomworld.stakes.second", at, size[0], size[2])
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                player.displayClientMessage(Component.translatable("kingdomworld.stakes.first", at).withStyle(ChatFormatting.YELLOW), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return pull(level, player, stack);
        }
        ArchitectRulerItem.Corners corners = ArchitectRulerItem.corners(stack);
        if (!corners.complete()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.sekerator.kingdomworld.client.ClientHooks.openEarthwork(corners));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Pulls the stakes out: the corners are forgotten.
     */
    private static InteractionResultHolder<ItemStack> pull(Level level, Player player, ItemStack stack) {
        ArchitectRulerItem.clear(stack);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("kingdomworld.stakes.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdomworld.survey_stakes.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
