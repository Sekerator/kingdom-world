package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The kingdom storehouse: a large chest whose contents belong to the kingdom data, so that citizens far away can
 * eat from it and bring their harvest. Every storehouse on a kingdom's land counts for that kingdom. Opening it is
 * protected like any container; hoppers and pipes of other mods use it through the item handler capability.
 * <p>
 * Two storehouses of one kingdom placed side by side at the same height and facing the same way join like a large
 * chest (not while sneaking): one window shows both, hoppers and pipes see both. Each half still keeps its own contents
 * in the kingdom data, so joining moves nothing and the kingdom counts every item once; a broken half drops its own
 * contents and leaves the other a storehouse of its own.
 */
public class StorehouseBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Alone, or which half of a double storehouse, as for chests. */
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;

    public StorehouseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, ChestType.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorehouseBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- joining

    /**
     * The side the other half of a double storehouse lies on.
     */
    public static Direction connectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    /**
     * Placed beside a storehouse of the same kingdom that stands alone and faces the way the new one will, the new one
     * joins it; never while sneaking.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        ChestType type = ChestType.SINGLE;
        if (!context.isSecondaryUseActive()) {
            if (facing == partnerFacing(context, facing.getClockWise())) {
                type = ChestType.LEFT;
            } else if (facing == partnerFacing(context, facing.getCounterClockWise())) {
                type = ChestType.RIGHT;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(TYPE, type);
    }

    /**
     * Which way a storehouse beside the placed one faces, if it stands alone and may join it.
     */
    @Nullable
    private Direction partnerFacing(BlockPlaceContext context, Direction side) {
        BlockPos at = context.getClickedPos().relative(side);
        BlockState state = context.getLevel().getBlockState(at);
        if (!state.is(this) || state.getValue(TYPE) != ChestType.SINGLE) {
            return null;
        }
        if (context.getLevel() instanceof ServerLevel server && !sameKingdom(server, context.getClickedPos(), at)) {
            return null;
        }
        return state.getValue(FACING);
    }

    /**
     * Whether two places count for the same kingdom, or for none: storehouses of different kingdoms never join.
     */
    static boolean sameKingdom(ServerLevel level, BlockPos a, BlockPos b) {
        if ((a.getX() >> 4) == (b.getX() >> 4) && (a.getZ() >> 4) == (b.getZ() >> 4)) {
            return true;
        }
        var territory = KingdomWorldApi.state(level.getServer()).territory();
        return Objects.equals(KingdomStorage.owner(territory, StorehouseBlockEntity.position(level, a)),
                KingdomStorage.owner(territory, StorehouseBlockEntity.position(level, b)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                  BlockPos neighborPos) {
        if (neighbor.is(this) && direction.getAxis().isHorizontal()) {
            ChestType other = neighbor.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE && other != ChestType.SINGLE && state.getValue(FACING) == neighbor.getValue(FACING)
                    && connectedDirection(neighbor) == direction.getOpposite()) {
                return state.setValue(TYPE, other.getOpposite());
            }
        } else if (state.getValue(TYPE) != ChestType.SINGLE && connectedDirection(state) == direction) {
            return state.setValue(TYPE, ChestType.SINGLE);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * The client guesses the shape of a new storehouse before the server answers and may join it to one of another
     * kingdom; the server sends its neighbours as they are, so that such a guess does not stay.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel && state.getValue(TYPE) == ChestType.SINGLE) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos at = pos.relative(side);
                BlockState there = level.getBlockState(at);
                if (there.is(this)) {
                    level.sendBlockUpdated(at, there, there, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
        ChestType type = state.getValue(TYPE);
        return type == ChestType.SINGLE ? rotated : rotated.setValue(TYPE, type.getOpposite());
    }

    /**
     * The places of the storehouse at a position: itself, or both halves of a double storehouse, the one on the left (seen
     * from the front) first. A half whose other half is gone, unloaded, turned another way or on the land of another
     * kingdom stands alone.
     */
    public static List<BlockPos> halves(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StorehouseBlock) || state.getValue(TYPE) == ChestType.SINGLE) {
            return List.of(pos.immutable());
        }
        BlockPos other = pos.relative(connectedDirection(state));
        if (!level.isLoaded(other)) {
            return List.of(pos.immutable());
        }
        BlockState partner = level.getBlockState(other);
        if (!(partner.getBlock() instanceof StorehouseBlock) || partner.getValue(TYPE) != state.getValue(TYPE).getOpposite()
                || partner.getValue(FACING) != state.getValue(FACING)) {
            return List.of(pos.immutable());
        }
        if (level instanceof ServerLevel server && !sameKingdom(server, pos, other)) {
            return List.of(pos.immutable());
        }
        return state.getValue(TYPE) == ChestType.RIGHT ? List.of(pos.immutable(), other.immutable()) : List.of(other.immutable(), pos.immutable());
    }

    /**
     * The inventory of the storehouse at a position as a container: one storehouse, or both halves of a double one with
     * the left one's slots first; {@code null} on the client.
     */
    @Nullable
    static IItemHandlerModifiable handler(ServerLevel level, List<BlockPos> halves) {
        List<IItemHandlerModifiable> parts = new ArrayList<>();
        for (BlockPos half : halves) {
            if (level.getBlockEntity(half) instanceof StorehouseBlockEntity storehouse) {
                IItemHandlerModifiable part = storehouse.ownHandler();
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return parts.size() == 1 ? parts.get(0) : new CombinedInvWrapper(parts.toArray(IItemHandlerModifiable[]::new));
    }

    // ---------------------------------------------------------------- use

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof StorehouseBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (KingdomWorldApi.isReadOnly(server.getServer())) {
            player.displayClientMessage(Component.translatable("kingdomworld.storehouse.read_only"), true);
            return InteractionResult.CONSUME;
        }
        List<BlockPos> halves = halves(server, pos);
        IItemHandlerModifiable handler = handler(server, halves);
        if (handler != null && halves.size() == 2 && handler.getSlots() != StorehouseMenu.SLOTS) {
            // A half without its block entity: this one opens alone.
            halves = List.of(pos.immutable());
            handler = handler(server, halves);
        }
        if (handler == null) {
            return InteractionResult.CONSUME;
        }
        StorehouseContainer container = new StorehouseContainer(server, halves, handler);
        if (halves.size() == 2) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new StorehouseMenu(id, inventory, container),
                    Component.translatable("kingdomworld.storehouse.double")));
        } else {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.sixRows(id, inventory, container),
                    Component.translatable("block.kingdomworld.storehouse")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            KingdomSavedData.get(server.getServer()).storehouses().getOrCreate(StorehouseBlockEntity.position(server, pos));
        }
    }

    /**
     * A broken storehouse drops what it holds, a half of a double storehouse only its own half; the other half stays with its
     * contents.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            KingdomSavedData data = KingdomSavedData.get(server.getServer());
            if (!data.isReadOnly()) {
                data.storehouses().remove(StorehouseBlockEntity.position(server, pos)).ifPresent(inventory -> {
                    for (ItemStack stack : inventory.takeAll()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    }
                });
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * A comparator reads the whole storehouse, both halves of a double one, as it reads a large chest.
     */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            IItemHandlerModifiable handler = handler(server, halves(server, pos));
            return handler == null ? 0 : ItemHandlerHelper.calcRedstoneFromInventory(handler);
        }
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld.storehouse.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.kingdomworld.storehouse.tooltip_double").withStyle(ChatFormatting.GRAY));
    }
}
