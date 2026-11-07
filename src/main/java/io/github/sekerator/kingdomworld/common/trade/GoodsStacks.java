package io.github.sekerator.kingdomworld.common.trade;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Items of the game as goods of the trade and back, and the inventory work of the market: counting, taking and giving
 * items so that nothing is made or lost.
 */
public final class GoodsStacks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Slots of the inventory that take new items: the main inventory and the hotbar. */
    private static final int MAIN_SLOTS = 36;

    private GoodsStacks() {
    }

    public static String itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }

    public static String itemId(ItemStack stack) {
        return itemId(stack.getItem());
    }

    /**
     * The item of an id, or {@code null} for an unknown one.
     */
    public static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(location);
        return item == null || item == Items.AIR ? null : item;
    }

    /**
     * A stack as goods, with its data.
     */
    public static Goods goods(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new Goods(itemId(stack), stack.getCount(), tag == null || tag.isEmpty() ? "" : tag.toString());
    }

    /**
     * Goods as stacks of the game, each at most a full stack. Goods of an item that no longer exists give nothing.
     */
    public static List<ItemStack> stacks(Goods goods) {
        List<ItemStack> stacks = new ArrayList<>();
        Item item = item(goods.item());
        if (item == null) {
            LOGGER.warn("Goods of the unknown item {} x{} were dropped", goods.item(), goods.count());
            return stacks;
        }
        CompoundTag tag = null;
        if (goods.hasData()) {
            try {
                tag = TagParser.parseTag(goods.data());
            } catch (Exception e) {
                LOGGER.warn("The data of goods {} could not be read and was left out: {}", goods.item(), e.getMessage());
            }
        }
        ItemStack sample = new ItemStack(item);
        if (tag != null) {
            sample.setTag(tag);
        }
        int left = goods.count();
        int max = Math.max(1, sample.getMaxStackSize());
        while (left > 0) {
            int part = Math.min(left, max);
            stacks.add(sample.copyWithCount(part));
            left -= part;
        }
        return stacks;
    }

    /**
     * Whether a stack is a plain item the kingdom buys: no data, or only an undamaged tool's empty damage.
     */
    public static boolean plain(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ModRegistries.COIN.get())) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return true;
        }
        return tag.size() == 1 && tag.contains("Damage") && !stack.isDamaged();
    }

    /**
     * Plain items of a kind in the inventory.
     */
    public static int countPlain(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item) && plain(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Takes plain items of a kind out of the inventory.
     *
     * @return how many were taken
     */
    public static int takePlain(Inventory inventory, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item) && plain(stack)) {
                int taken = Math.min(left, stack.getCount());
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
     * Whether all the stacks fit into the main inventory together.
     */
    public static boolean fits(Inventory inventory, List<ItemStack> stacks) {
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOTS);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            slots.add(inventory.getItem(slot).copy());
        }
        for (ItemStack wanted : stacks) {
            int left = wanted.getCount();
            for (ItemStack slot : slots) {
                if (left > 0 && !slot.isEmpty() && ItemStack.isSameItemSameTags(slot, wanted) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    left -= moved;
                }
            }
            for (int i = 0; i < slots.size() && left > 0; i++) {
                if (slots.get(i).isEmpty()) {
                    int moved = Math.min(left, wanted.getMaxStackSize());
                    slots.set(i, wanted.copyWithCount(moved));
                    left -= moved;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives a player a stack; what does not fit falls at the player's feet, so nothing is lost.
     */
    public static void give(ServerPlayer player, ItemStack stack) {
        ItemStack rest = stack.copy();
        player.getInventory().add(rest);
        if (!rest.isEmpty()) {
            player.drop(rest, false);
        }
        player.getInventory().setChanged();
    }
}
