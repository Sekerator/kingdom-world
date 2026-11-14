package io.github.sekerator.kingdomworld.common.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

/**
 * The storehouse inventory as a vanilla container, so that the ordinary large chest screen can show one storehouse and
 * the screen of a double storehouse both halves of one.
 */
final class StorehouseContainer implements Container {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final ServerLevel level;
    private final List<BlockPos> halves;
    private final IItemHandlerModifiable handler;

    /**
     * @param halves  the storehouse, or both halves of a double storehouse in the order of the handler
     * @param handler their inventory
     */
    StorehouseContainer(ServerLevel level, List<BlockPos> halves, IItemHandlerModifiable handler) {
        this.level = level;
        this.halves = List.copyOf(halves);
        this.handler = handler;
    }

    @Override
    public int getContainerSize() {
        return handler.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return handler.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return handler.extractItem(slot, amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = handler.getStackInSlot(slot);
        return stack.isEmpty() ? ItemStack.EMPTY : handler.extractItem(slot, stack.getCount(), false);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        handler.setStackInSlot(slot, stack);
    }

    @Override
    public void setChanged() {
        // Menus change the stacks they get from getItem in place, which the inventory cannot notice itself.
        io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(level.getServer()).setDirty();
    }

    /**
     * Open while every half stands, the halves of a double storehouse are still joined and the player is near one of them:
     * a broken half closes the window of the double storehouse.
     */
    @Override
    public boolean stillValid(Player player) {
        boolean near = false;
        for (BlockPos pos : halves) {
            if (!level.getBlockState(pos).is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.STOREHOUSE.get())) {
                return false;
            }
            near |= player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
        }
        return near && (halves.size() == 1 || StorehouseBlock.halves(level, halves.get(0)).equals(halves));
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
