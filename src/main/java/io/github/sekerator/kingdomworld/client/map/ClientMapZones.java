package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.core.territory.MapZone;

import java.util.List;

/**
 * Zones of the war layers (contested land, battle zones) known on the client, for map integrations. The layers are
 * drawn already; nothing fills them yet, the wars will send their zones here. Used on the client main thread.
 */
public final class ClientMapZones {
    private static List<MapZone> zones = List.of();
    private static int version;

    private ClientMapZones() {
    }

    public static void replace(List<MapZone> list) {
        zones = List.copyOf(list);
        version++;
    }

    public static List<MapZone> zones() {
        return zones;
    }

    /**
     * Changes whenever the zones do.
     */
    public static int version() {
        return version;
    }

    public static void clear() {
        replace(List.of());
    }
}
