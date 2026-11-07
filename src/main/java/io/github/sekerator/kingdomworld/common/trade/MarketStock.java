package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The goods of a kingdom's storehouses as trade sees them: plain items only (no damaged tools, no enchanted or named
 * items), less what keep orders hold and workers set aside. Taking is all or nothing.
 */
public final class MarketStock {

    private MarketStock() {
    }

    /**
     * Plain items of a kind in the storehouses.
     */
    public static long stock(MinecraftServer server, UUID kingdom, Item item) {
        return KingdomStorage.count(server, kingdom, stack -> stack.is(item) && GoodsStacks.plain(stack));
    }

    /**
     * Plain items of a kind trade may use: the stock less keep orders and what workers set aside.
     */
    public static long free(MinecraftServer server, UUID kingdom, Item item) {
        String id = GoodsStacks.itemId(item);
        long kept = 0;
        for (Order order : KingdomWorldApi.state(server).orders().ordersOf(kingdom)) {
            if (order.kind() == Order.Kind.KEEP && order.item().equals(id) && !order.paused()) {
                kept += order.amount();
            }
        }
        return Math.max(0, stock(server, kingdom, item) - kept - Production.reservedFor(kingdom, id, null));
    }

    /**
     * The worth of one item of a kind.
     */
    public static ItemValues.Value worth(MinecraftServer server, Item item) {
        return ItemPrices.value(server, new ItemStack(item));
    }

    /**
     * Takes plain items of a kind from the storehouses, nearest to {@code near} first.
     *
     * @return the stacks taken, or {@code null} if there were fewer than {@code count}; then nothing was taken
     */
    @Nullable
    public static List<ItemStack> take(MinecraftServer server, UUID kingdom, Item item, int count, @Nullable WorldPos near) {
        if (count <= 0 || free(server, kingdom, item) < count) {
            return null;
        }
        List<ItemStack> taken = new ArrayList<>();
        int left = count;
        for (KingdomStorage.Found found : KingdomStorage.find(server, kingdom, stack -> stack.is(item) && GoodsStacks.plain(stack), near)) {
            if (left <= 0) {
                break;
            }
            ItemStack stack = KingdomStorage.extract(server, found, Math.min(left, found.stack().getCount()));
            if (!stack.isEmpty()) {
                left -= stack.getCount();
                taken.add(stack);
            }
        }
        if (left > 0) {
            putBack(server, kingdom, taken, near);
            return null;
        }
        return taken;
    }

    /**
     * Puts stacks into the storehouses of a kingdom.
     *
     * @return what did not fit
     */
    public static List<ItemStack> putBack(MinecraftServer server, UUID kingdom, List<ItemStack> stacks, @Nullable WorldPos near) {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack left = KingdomStorage.insert(server, kingdom, stack, near);
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }
        return rest;
    }

    /**
     * The stacks of goods, merged into goods of one kind each where they are alike.
     */
    public static List<Goods> goods(List<ItemStack> stacks) {
        List<Goods> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Goods goods = GoodsStacks.goods(stack);
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                Goods existing = result.get(i);
                if (existing.item().equals(goods.item()) && existing.data().equals(goods.data())
                        && (long) existing.count() + goods.count() <= Goods.MAX_COUNT) {
                    result.set(i, existing.withCount(existing.count() + goods.count()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(goods);
            }
        }
        return result;
    }

    /**
     * The goods as stacks of the game.
     */
    public static List<ItemStack> stacks(List<Goods> goods) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Goods entry : goods) {
            stacks.addAll(GoodsStacks.stacks(entry));
        }
        return stacks;
    }
}
