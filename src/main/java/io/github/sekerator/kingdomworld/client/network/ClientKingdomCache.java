package io.github.sekerator.kingdomworld.client.network;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomSummary;

import java.util.List;

/**
 * Last kingdom list received from the server. Display only: the server stays the source of truth.
 * Accessed from the client main thread.
 */
public final class ClientKingdomCache {
    private static List<KingdomSummary> kingdoms = List.of();
    private static int total;
    private static boolean received;

    private ClientKingdomCache() {
    }

    static void update(int newTotal, List<KingdomSummary> newKingdoms) {
        kingdoms = List.copyOf(newKingdoms);
        total = newTotal;
        received = true;
    }

    public static void clear() {
        kingdoms = List.of();
        total = 0;
        received = false;
    }

    public static List<KingdomSummary> kingdoms() {
        return kingdoms;
    }

    /**
     * Number of kingdoms on the server, which may exceed the cached page.
     */
    public static int total() {
        return total;
    }

    public static boolean hasData() {
        return received;
    }
}
