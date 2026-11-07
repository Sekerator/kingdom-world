package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.data.TradeViews;

import java.util.List;

/**
 * The caravans on the way, as the server last sent them, for the map. A caravan moves along its way by the game time.
 */
public final class ClientTradeCache {
    private static volatile List<TradeViews.CaravanInfo> caravans = List.of();

    private ClientTradeCache() {
    }

    public static void replace(List<TradeViews.CaravanInfo> list) {
        caravans = List.copyOf(list);
    }

    public static List<TradeViews.CaravanInfo> caravans() {
        return caravans;
    }

    public static void clear() {
        caravans = List.of();
    }
}
