package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A block of magic that the kingdom whose land it stands on keeps track of: the magic focus, the crystal accumulator, the
 * crystal generator and the gate. Placing one on kingdom land records it; breaking it forgets it. Blocks that spend the
 * kingdom's energy for others (generators and gates) work only when a player with the right to run the mage tower placed
 * them; placed any other way they stay inert.
 */
public class MagicDeviceBlock extends Block {
    private final MagicDevice.Kind kind;

    public MagicDeviceBlock(MagicDevice.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public MagicDevice.Kind kind() {
        return kind;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player
                && !MagicDevices.mayPlace(player, context.getClickedPos(), kind)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            MagicDevices.placed(level, pos.immutable(), kind);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            MagicDevices.placedBy(level, pos.immutable(), kind, placer);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MagicDevices.removed(level, pos.immutable());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || (kind != MagicDevice.Kind.FOCUS && kind != MagicDevice.Kind.GATE)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (kind == MagicDevice.Kind.FOCUS) {
                MagicActions.openFocus(serverPlayer, pos.immutable());
            } else {
                MagicActions.openGate(serverPlayer, pos.immutable());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld." + MagicRegistries.blockId(kind) + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
