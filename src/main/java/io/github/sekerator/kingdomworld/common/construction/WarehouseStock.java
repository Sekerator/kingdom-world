package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the containers of the warehouses held when their place was last loaded, so that the kingdom counts them while nobody is
 * near: the king's orders, the planner and the work far from players see them as they see a storehouse. Taking from such a
 * container loads its place for a moment and takes from the container itself, a few places a tick at most; nothing is ever put
 * into a container that is not loaded. What a container holds is looked at again whenever its place is loaded and when it
 * unloads. It lives while the server runs: after a restart a warehouse counts from the first time its place is loaded.
 *
 * <p>Server thread only.
 */
final class WarehouseStock {
    /** Places of containers a tick may load to take from them. */
    static final int LOADS_PER_TICK = 2;

    private static final Map<WorldPos, List<ItemStack>> SEEN = new HashMap<>();
    private static long loadTick = Long.MIN_VALUE;
    private static int loads;

    private WarehouseStock() {
    }

    /**
     * Remembers what a container holds now.
     */
    static void remember(WorldPos pos, IItemHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            stacks.add(handler.getStackInSlot(slot).copy());
        }
        SEEN.put(pos, stacks);
    }

    @Nullable
    static List<ItemStack> seen(WorldPos pos) {
        return SEEN.get(pos);
    }

    /**
     * Forgets the containers that are no longer containers of a warehouse.
     */
    static void keepOnly(Collection<WorldPos> containers) {
        SEEN.keySet().retainAll(new java.util.HashSet<>(containers));
    }

    static void reset() {
        SEEN.clear();
        loadTick = Long.MIN_VALUE;
        loads = 0;
    }

    /**
     * Whether this tick may still load the place of a container.
     */
    private static boolean mayLoad(long tick) {
        if (tick != loadTick) {
            loadTick = tick;
            loads = 0;
        }
        if (loads >= LOADS_PER_TICK) {
            return false;
        }
        loads++;
        return true;
    }

    /**
     * A container of a warehouse whose place is not loaded, as a storehouse: it shows what it held when its place was last
     * loaded, gives from the container itself by loading its place, and takes nothing in.
     */
    static final class Remembered extends StorehouseInventory {
        private final ServerLevel level;
        private final BlockPos at;

        Remembered(WorldPos pos, ServerLevel level, BlockPos at) {
            super(pos);
            this.level = level;
            this.at = at.immutable();
        }

        private List<ItemStack> stacks() {
            List<ItemStack> stacks = SEEN.get(pos());
            return stacks == null ? List.of() : stacks;
        }

        @Override
        public boolean takesIn() {
            return false;
        }

        @Override
        public int getSlots() {
            return stacks().size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemStack> stacks = stacks();
            return slot >= 0 && slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
        }

        /**
         * The container itself, its place loaded for it; {@code null} if this tick loaded enough places or it is gone.
         */
        @Nullable
        private IItemHandler container() {
            if (!level.isLoaded(at) && !mayLoad(level.getServer().getTickCount())) {
                return null;
            }
            BlockEntity entity = level.getBlockEntity(at);
            IItemHandler handler = entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (handler == null || !BuildingEffects.isContainer(level, at, level.getBlockState(at))) {
                SEEN.remove(pos());
                return null;
            }
            return handler;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack seen = getStackInSlot(slot);
            if (amount <= 0 || seen.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (simulate) {
                return seen.copyWithCount(Math.min(amount, seen.getCount()));
            }
            IItemHandler handler = container();
            if (handler == null || slot >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = handler.extractItem(slot, amount, false);
            remember(pos(), handler);
            return taken;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
