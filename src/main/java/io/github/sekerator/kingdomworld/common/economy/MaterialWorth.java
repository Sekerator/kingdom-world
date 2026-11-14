package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * What the materials a kingdom lacks for a building are worth, by the prices of the items of the server: the rulers of the world
 * and the governors of the cities weigh their buildings by it.
 */
public final class MaterialWorth {
    private MaterialWorth() {
    }

    /**
     * What one of each item is worth, at least 1.
     */
    public static ToLongFunction<String> worth(MinecraftServer server) {
        ItemValues prices = ItemPrices.table(server);
        return item -> Math.max(1, prices.value(item).units());
    }

    /**
     * What the materials a kingdom lacks for a cost are worth.
     */
    public static long missing(MinecraftServer server, UUID kingdomId, Map<String, Integer> cost) {
        return missing(server, KingdomStorage.countsById(server, kingdomId), cost);
    }

    /**
     * What the materials a kingdom lacks for a cost are worth, from the counts of its storehouses.
     */
    public static long missing(MinecraftServer server, Map<String, Long> stock, Map<String, Integer> cost) {
        ToLongFunction<String> worth = worth(server);
        long total = 0;
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            long lack = entry.getValue() - stock.getOrDefault(entry.getKey(), 0L);
            if (lack > 0) {
                total += lack * worth.applyAsLong(entry.getKey());
            }
        }
        return total;
    }
}
