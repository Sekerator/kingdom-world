package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The window of a double storehouse: both halves side by side, six rows of eighteen slots (the left half's nine columns
 * first, as the slots of its container come first), and the player's inventory under them. Shift clicks move between the
 * whole storehouse and the inventory, as in a large chest; mods that sort a container see all its slots as one.
 */
public class StorehouseMenu extends AbstractContainerMenu {
    /** Pixels of one slot. */
    public static final int SLOT = 18;
    public static final int ROWS = 6;
    /** Columns of one half. */
    public static final int HALF_COLUMNS = 9;
    public static final int SLOTS = 2 * StorehouseInventory.SLOTS;
    /** Width and height of the window. */
    public static final int WIDTH = 7 + 2 * HALF_COLUMNS * SLOT + 7;
    public static final int HEIGHT = 222;
    /** Where the storehouse and the player's inventory start in the window. */
    public static final int STORE_TOP = 18;
    public static final int INVENTORY_TOP = 139;
    public static final int INVENTORY_LEFT = (WIDTH - 9 * SLOT) / 2 + 1;

    private final Container container;

    /**
     * The menu on the server, over the inventory of both halves.
     */
    public StorehouseMenu(int id, Inventory inventory, Container container) {
        super(ModRegistries.STOREHOUSE_MENU.get(), id);
        checkContainerSize(container, SLOTS);
        this.container = container;
        container.startOpen(inventory.player);
        for (int index = 0; index < SLOTS; index++) {
            int half = index / StorehouseInventory.SLOTS;
            int inHalf = index % StorehouseInventory.SLOTS;
            int column = half * HALF_COLUMNS + inHalf % HALF_COLUMNS;
            addSlot(new Slot(container, index, 8 + column * SLOT, STORE_TOP + (inHalf / HALF_COLUMNS) * SLOT));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_LEFT + col * SLOT, INVENTORY_TOP + row * SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_LEFT + col * SLOT, INVENTORY_TOP + 58));
        }
    }

    /**
     * The menu on the client: the server fills its slots.
     */
    public static StorehouseMenu client(int id, Inventory inventory) {
        return new StorehouseMenu(id, inventory, new SimpleContainer(SLOTS));
    }

    public Container container() {
        return container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
