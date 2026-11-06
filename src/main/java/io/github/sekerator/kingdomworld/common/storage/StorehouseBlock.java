package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The kingdom storehouse: a large chest whose contents belong to the kingdom data, so that citizens far away can
 * eat from it and bring their harvest. Every storehouse on a kingdom's land counts for that kingdom. Opening it is
 * protected like any container; hoppers and pipes of other mods use it through the item handler capability.
 */
public class StorehouseBlock extends BaseEntityBlock {

    public StorehouseBlock(Properties properties) {
        super(properties);
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

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof StorehouseBlockEntity storehouse)) {
            return InteractionResult.PASS;
        }
        if (KingdomWorldApi.isReadOnly(server.getServer())) {
            player.displayClientMessage(Component.translatable("kingdomworld.storehouse.read_only"), true);
            return InteractionResult.CONSUME;
        }
        IItemHandlerModifiable handler = storehouse.handler();
        if (handler != null) {
            StorehouseContainer container = new StorehouseContainer(server, pos.immutable(), handler);
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

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StorehouseBlockEntity storehouse) {
            IItemHandlerModifiable handler = storehouse.handler();
            return handler == null ? 0 : ItemHandlerHelper.calcRedstoneFromInventory(handler);
        }
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld.storehouse.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
