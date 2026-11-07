package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The currencies a client knows, sent by the server when a player joins and whenever one changes. Coins look up
 * their name and color here, the map its currency line. Holds only data, so common code (the coin item) may read it
 * on both sides; on a server it stays empty.
 */
public final class CurrencyDirectory {
    private static volatile Map<UUID, CurrencyInfo> currencies = Map.of();
    private static volatile boolean received;

    private CurrencyDirectory() {
    }

    public static void replace(List<CurrencyInfo> list) {
        Map<UUID, CurrencyInfo> next = new HashMap<>();
        for (CurrencyInfo info : list) {
            next.put(info.id(), info);
        }
        currencies = Map.copyOf(next);
        received = true;
    }

    public static Optional<CurrencyInfo> get(UUID id) {
        return Optional.ofNullable(currencies.get(id));
    }

    /**
     * Whether the server sent the list; before that an unknown currency is not yet known rather than unknown.
     */
    public static boolean received() {
        return received;
    }

    public static void clear() {
        currencies = Map.of();
        received = false;
    }
}
