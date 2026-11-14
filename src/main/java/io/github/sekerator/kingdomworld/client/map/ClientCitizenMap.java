package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.packet.CitizenMapPackets;

/**
 * The citizens of the player's kingdom on the atlas, as the server sent them last.
 */
public final class ClientCitizenMap {
    private static CitizenMapPackets.View view = CitizenMapPackets.View.NONE;

    private ClientCitizenMap() {
    }

    public static void set(CitizenMapPackets.View value) {
        view = value == null ? CitizenMapPackets.View.NONE : value;
    }

    public static CitizenMapPackets.View view() {
        return view;
    }

    public static void clear() {
        view = CitizenMapPackets.View.NONE;
    }
}
