package io.github.sekerator.kingdomworld.compat.maps;

import net.minecraft.server.MinecraftServer;

/**
 * A web map of the server that shows kingdoms: BlueMap or Dynmap. {@link WebMaps} calls it on the server thread.
 */
public interface WebMapSink {

    /**
     * Name for the log.
     */
    String name();

    /**
     * Whether the map mod is up and takes markers now.
     */
    boolean ready();

    /**
     * Whether the sink needs everything again, for example because the map mod just started or reloaded.
     */
    boolean wantsFull();

    /**
     * Turns an update into markers. A full update also clears {@link #wantsFull()}.
     */
    void apply(MinecraftServer server, WebMapModel.Update update, MapTexts texts);

    /**
     * The server stops: forget the markers.
     */
    void stop();
}
