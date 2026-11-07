package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Coins in a player's inventory: counting them, taking them, and working out how many new coins fit, so that a bank
 * hands out only what the inventory takes and charges the account for exactly that.
 */
public final class CoinStacks {
    /** Slots of the inventory that take new items: the main inventory and the hotbar. */
    private static final int MAIN_SLOTS = 36;
    /** The slot of the second hand. */
    private static final int OFFHAND_SLOT = 40;

    private CoinStacks() {
    }

    /**
     * Coins of a currency in the inventory, by kind.
     */
    public static Coins count(Inventory inventory, UUID currency) {
        Coins coins = Coins.NONE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(ModRegistries.COIN.get())) {
                continue;
            }
            if (CoinItem.currency(stack).filter(currency::equals).isPresent()) {
                Denomination denomination = CoinItem.denomination(stack).orElse(null);
                if (denomination != null) {
                    coins = coins.plus(denomination, stack.getCount());
                }
            }
        }
        return coins;
    }

    /**
     * Takes coins of a kind out of the inventory.
     *
     * @return how many were taken; fewer than asked only if there were fewer
     */
    public static long take(Inventory inventory, UUID currency, Denomination denomination, long count) {
        long left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && CoinItem.is(stack, currency, denomination)) {
                int taken = (int) Math.min(left, stack.getCount());
                stack.shrink(taken);
                if (stack.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
                left -= taken;
            }
        }
        inventory.setChanged();
        return count - left;
    }

    /**
     * How many of the wanted coins fit into the inventory: stacks of the same coin are filled first, then free slots,
     * the largest coins first, the way the inventory takes items.
     */
    public static Coins fitting(Inventory inventory, UUID currency, Coins wanted) {
        int stackSize = new ItemStack(ModRegistries.COIN.get()).getMaxStackSize();
        int freeSlots = 0;
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                freeSlots++;
            }
        }
        Coins fit = Coins.NONE;
        for (Denomination denomination : Denomination.largestFirst()) {
            long want = wanted.count(denomination);
            if (want <= 0) {
                continue;
            }
            long room = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (slot >= MAIN_SLOTS && slot != OFFHAND_SLOT) {
                    continue;
                }
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && CoinItem.is(stack, currency, denomination)) {
                    room += Math.max(0, stackSize - stack.getCount());
                }
            }
            long placed = Math.min(want, room);
            long rest = want - placed;
            long slotsNeeded = Math.min(freeSlots, (rest + stackSize - 1) / stackSize);
            long inSlots = Math.min(rest, slotsNeeded * stackSize);
            freeSlots -= (int) slotsNeeded;
            fit = fit.plus(denomination, placed + inSlots);
        }
        return fit;
    }

    /**
     * Puts coins into the inventory.
     *
     * @return the coins that did not fit after all; none if {@link #fitting} was asked first
     */
    public static Coins give(Inventory inventory, UUID currency, Coins coins) {
        int stackSize = new ItemStack(ModRegistries.COIN.get()).getMaxStackSize();
        Coins left = Coins.NONE;
        for (Denomination denomination : Denomination.largestFirst()) {
            long count = coins.count(denomination);
            while (count > 0) {
                int part = (int) Math.min(count, stackSize);
                ItemStack stack = CoinItem.stack(currency, denomination, part);
                inventory.add(stack);
                count -= part;
                if (!stack.isEmpty()) {
                    left = left.plus(denomination, stack.getCount() + count);
                    count = 0;
                }
            }
        }
        inventory.setChanged();
        return left;
    }
}
