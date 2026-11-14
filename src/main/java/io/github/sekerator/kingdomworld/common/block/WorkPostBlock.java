package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The work post: a workplace of citizens on the land of a kingdom. A right click opens its settings (profession,
 * zone, seats, the animals of a herd); a container next to it (a chest, a barrel, a crate of another mod)
 * receives what its workers gather.
 *
 * <p>The board on its sides shows the profession of the post ({@link #PROFESSION}). It is only a look that follows the data of
 * the kingdom: changing it neither registers nor removes the post, since {@link #onPlace} and {@link #onRemove} only act when
 * the block itself comes or goes.
 */
public class WorkPostBlock extends Block {
    /** What the board shows; set by the server from the profession of the post. */
    public static final EnumProperty<WorkPostLook> PROFESSION = EnumProperty.create("profession", WorkPostLook.class);

    public WorkPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PROFESSION, WorkPostLook.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PROFESSION);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WorkPostActions.open(serverPlayer, pos.immutable());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            CitizenSites.onPlaced(server, pos, state);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            CitizenSites.onRemoved(server, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld.work_post.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
