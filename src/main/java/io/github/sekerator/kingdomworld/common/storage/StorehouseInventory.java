package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Objects;

/**
 * The contents of one storehouse. They live in the kingdom data rather than in the block entity, so that the
 * simulation of citizens far from players can take food from and bring harvest to storehouses in unloaded
 * chunks. The block entity only offers this inventory to players, hoppers and pipes.
 */
public class StorehouseInventory extends ItemStackHandler {
    /** Slots of a storehouse, as many as a large chest. */
    public static final int SLOTS = 54;

    private final WorldPos pos;
    private Runnable changeListener = () -> {
    };

    public StorehouseInventory(WorldPos pos) {
        super(SLOTS);
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    public WorldPos pos() {
        return pos;
    }

    /**
     * Whether items may be put in: a container of a warehouse whose place is not loaded only gives.
     */
    public boolean takesIn() {
        return true;
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    protected void onContentsChanged(int slot) {
        changeListener.run();
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < getSlots(); slot++) {
            if (!getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Takes every stack out, for example to drop it when the block is broken.
     */
    public java.util.List<ItemStack> takeAll() {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
                setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        return stacks;
    }
}
