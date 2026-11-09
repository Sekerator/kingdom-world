package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets;

/**
 * The army of the kingdom of the player on the map, as the server last sent it: only what the kingdom sees.
 */
public final class ClientArmyMap {
    private static volatile ArmyMapPackets.View view = ArmyMapPackets.View.NONE;
    private static volatile boolean answered;

    private ClientArmyMap() {
    }

    public static void update(ArmyMapPackets.View value) {
        view = value;
        answered = true;
    }

    public static ArmyMapPackets.View view() {
        return view;
    }

    /**
     * Whether the server answered since the map of the army was opened.
     */
    public static boolean answered() {
        return answered;
    }

    public static void clear() {
        view = ArmyMapPackets.View.NONE;
        answered = false;
    }
}
