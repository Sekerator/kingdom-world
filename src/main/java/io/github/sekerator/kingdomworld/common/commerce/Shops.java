package io.github.sekerator.kingdomworld.common.commerce;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The shops of the citizens of a city during its day.
 */
public final class Shops {
    private Shops() {
    }

    /**
     * The shops of a city for its window, and why new ones do not open.
     */
    public record Overview(java.util.List<io.github.sekerator.kingdomworld.common.network.data.CityViews.ShopRow> rows, int count,
                           io.github.sekerator.kingdomworld.common.network.data.CityViews.Blocked blocked, long shortfall, String missing) {
    }

    public static Overview overview(MinecraftServer server, UUID kingdom, long city) {
        return new Overview(java.util.List.of(), 0, io.github.sekerator.kingdomworld.common.network.data.CityViews.Blocked.NONE, 0, "");
    }

    /**
     * Whether a shop of a kingdom exists.
     */
    public static boolean exists(MinecraftServer server, long shop, UUID kingdom) {
        return false;
    }

    /**
     * The shops of a city close: with the crown buying back their goods, or as loot of a winner.
     */
    static void closeCity(MinecraftServer server, UUID kingdom, long city, boolean buyout) {
    }

    /**
     * A city went to another kingdom: its shops close at once.
     */
    static void handOver(MinecraftServer server, UUID from, long city, UUID to) {
    }

    /**
     * Lives as much of the day of the shops of a city as the time allows.
     *
     * @return whether it is done
     */
    static boolean dayOfShops(MinecraftServer server, UUID kingdom, long city, long day, long start) {
        return true;
    }
}
