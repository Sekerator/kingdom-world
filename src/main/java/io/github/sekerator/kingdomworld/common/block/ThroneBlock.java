package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The throne. An empty throne is decoration; using it with a Founder's Crown opens the founding screen. Once a
 * kingdom is founded on it, the throne is the kingdom's heart: using it opens the throne screen, and it cannot
 * be broken until the king dissolves the kingdom.
 *
 * <p>Whether a throne is a heart is decided by the kingdom data (the capital position), not by the block, so
 * the block needs no block entity.
 */
public class ThroneBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SEAT = Block.box(1, 0, 1, 15, 10, 15);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // The model faces north; the backrest stands on the side opposite to the facing.
        SHAPES.put(Direction.NORTH, Shapes.or(SEAT, Block.box(1, 0, 13, 15, 22, 15)));
        SHAPES.put(Direction.SOUTH, Shapes.or(SEAT, Block.box(1, 0, 1, 15, 22, 3)));
        SHAPES.put(Direction.EAST, Shapes.or(SEAT, Block.box(1, 0, 1, 3, 22, 15)));
        SHAPES.put(Direction.WEST, Shapes.or(SEAT, Block.box(13, 0, 1, 15, 22, 15)));
    }

    public ThroneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld.throne.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            KingdomActions.useThrone(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.CONSUME;
    }
}
