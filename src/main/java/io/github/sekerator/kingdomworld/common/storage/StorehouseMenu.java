package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.StorehouseRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The window of a storehouse: six rows of nine slots, or both halves of a double storehouse side by side, eighteen slots a row;
 * under them the line of whom the storehouse takes in from, and the player's inventory under that. Of a double storehouse the
 * first nine columns are the half of type RIGHT, whose slots come first in the container ({@link StorehouseBlock#halves}): it
 * stands on the left as one looks at the front, as the first half of a large chest does. Shift clicks move between the whole
 * storehouse and the inventory, as in a chest; mods that sort a container see all its slots as one.
 */
public class StorehouseMenu extends AbstractContainerMenu {
    /** Pixels of one slot. */
    public static final int SLOT = 18;
    public static final int ROWS = 6;
    /** Columns of one half. */
    public static final int HALF_COLUMNS = 9;
    /** Slots of a double storehouse. */
    public static final int SLOTS = 2 * StorehouseInventory.SLOTS;
    public static final int HEIGHT = 236;
    /** Where the storehouse, the line of whom it takes in from and the player's inventory start in the window. */
    public static final int STORE_TOP = 18;
    public static final int SETTINGS_TOP = STORE_TOP + ROWS * SLOT + 3;
    public static final int SETTINGS_HEIGHT = 12;
    public static final int INVENTORY_TOP = 153;

    /**
     * What the window shows of the setting of the storehouse.
     *
     * @param pos     the storehouse that was opened
     * @param canEdit whether the player may change whom it takes in from
     * @param accepts the professions it takes in from, empty for every citizen
     */
    public record Settings(BlockPos pos, boolean canEdit, List<String> accepts) {
        public Settings {
            Objects.requireNonNull(pos, "pos");
            accepts = List.copyOf(accepts);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(canEdit);
            buf.writeVarInt(accepts.size());
            for (String profession : accepts) {
                buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
            }
        }

        public static Settings decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean canEdit = buf.readBoolean();
            int size = buf.readVarInt();
            if (size < 0 || size > StorehouseRules.MAX_PROFESSIONS) {
                throw new DecoderException("Too many professions");
            }
            List<String> accepts = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String profession = buf.readUtf(Profession.MAX_ID_LENGTH);
                if (!Profession.isValidId(profession)) {
                    throw new DecoderException("Invalid profession id");
                }
                accepts.add(profession);
            }
            return new Settings(pos, canEdit, accepts);
        }
    }

    private final Container container;
    private final int halves;
    private final Settings settings;

    /**
     * The menu on the server, over the inventory of the storehouse or of both halves.
     */
    public StorehouseMenu(int id, Inventory inventory, Container container, int halves, Settings settings) {
        super(ModRegistries.STOREHOUSE_MENU.get(), id);
        if (halves < 1 || halves > 2) {
            throw new IllegalArgumentException("A storehouse has one or two halves");
        }
        checkContainerSize(container, halves * StorehouseInventory.SLOTS);
        this.container = container;
        this.halves = halves;
        this.settings = settings;
        container.startOpen(inventory.player);
        for (int index = 0; index < halves * StorehouseInventory.SLOTS; index++) {
            int half = index / StorehouseInventory.SLOTS;
            int inHalf = index % StorehouseInventory.SLOTS;
            int column = half * HALF_COLUMNS + inHalf % HALF_COLUMNS;
            addSlot(new Slot(container, index, 8 + column * SLOT, STORE_TOP + (inHalf / HALF_COLUMNS) * SLOT));
        }
        int left = inventoryLeft(halves);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, left + col * SLOT, INVENTORY_TOP + row * SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, left + col * SLOT, INVENTORY_TOP + 58));
        }
    }

    /**
     * The menu on the client: the server fills its slots; the number of halves and the setting come with the opening.
     */
    public static StorehouseMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        int halves = buf.readVarInt();
        if (halves < 1 || halves > 2) {
            throw new DecoderException("A storehouse has one or two halves");
        }
        return client(id, inventory, halves, Settings.decode(buf));
    }

    public static StorehouseMenu client(int id, Inventory inventory, int halves, Settings settings) {
        return new StorehouseMenu(id, inventory, new SimpleContainer(halves * StorehouseInventory.SLOTS), halves, settings);
    }

    /**
     * Width of the window of a storehouse of so many halves.
     */
    public static int width(int halves) {
        return 7 + halves * HALF_COLUMNS * SLOT + 7;
    }

    /**
     * Where the player's inventory starts in the window: in the middle.
     */
    public static int inventoryLeft(int halves) {
        return (width(halves) - 9 * SLOT) / 2 + 1;
    }

    public int halves() {
        return halves;
    }

    public Settings settings() {
        return settings;
    }

    public Container container() {
        return container;
    }

    private int storeSlots() {
        return halves * StorehouseInventory.SLOTS;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < storeSlots()) {
            if (!moveItemStackTo(stack, storeSlots(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, storeSlots(), false)) {
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
