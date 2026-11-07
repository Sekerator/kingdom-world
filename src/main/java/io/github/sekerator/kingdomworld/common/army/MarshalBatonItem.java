package io.github.sekerator.kingdomworld.common.army;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The marshal's baton. Sneak and use it in the air to choose the next squad of your kingdom; use it on a block to give the
 * chosen squad the point to hold, the next point of its patrol or the center of the area to guard, by the mode of the baton
 * (sneak and use it on a block to change the mode); use it in the air to see where the squads are. The server checks the
 * right to run the army.
 */
public class MarshalBatonItem extends Item {
    static final String TAG_SQUAD = "Squad";
    static final String TAG_MODE = "Mode";

    /**
     * What using the baton on a block does.
     */
    public enum Mode {
        HOLD,
        PATROL,
        GUARD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static Mode of(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            int index = tag == null ? 0 : tag.getInt(TAG_MODE);
            return values()[Math.max(0, Math.min(values().length - 1, index))];
        }
    }

    public MarshalBatonItem(Properties properties) {
        super(properties);
    }

    /**
     * The squad the baton has chosen, 0 for none.
     */
    public static long squad(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getLong(TAG_SQUAD));
    }

    static void setSquad(ItemStack stack, long squad) {
        stack.getOrCreateTag().putLong(TAG_SQUAD, squad);
    }

    public static Mode mode(ItemStack stack) {
        return Mode.of(stack);
    }

    static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, mode.ordinal());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                ArmyActions.batonNextSquad(serverPlayer, stack);
            } else {
                ArmyActions.batonShowSquads(serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos at = context.getClickedPos().relative(context.getClickedFace());
            if (player.isShiftKeyDown()) {
                Mode next = mode(stack).next();
                setMode(stack, next);
                serverPlayer.displayClientMessage(Component.translatable("kingdomworld.army.baton.mode_set",
                        Component.translatable("kingdomworld.army.baton.mode." + next.id())), true);
            } else {
                ArmyActions.batonOrder(serverPlayer, stack, at);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        long squad = squad(stack);
        tooltip.add((squad == 0 ? Component.translatable("kingdomworld.army.baton.no_squad")
                : Component.translatable("kingdomworld.army.baton.squad", squad)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("kingdomworld.army.baton.mode", Component.translatable("kingdomworld.army.baton.mode."
                + mode(stack).id())).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("kingdomworld.army.baton.hint.order").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("kingdomworld.army.baton.hint.mode").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("kingdomworld.army.baton.hint.squad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("kingdomworld.army.baton.hint.show").withStyle(ChatFormatting.GRAY));
    }
}
