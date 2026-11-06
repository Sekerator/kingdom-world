package io.github.sekerator.kingdomworld.core.request;

import java.util.UUID;

/**
 * The sender of a client request, abstracted from the game so validation rules can be unit tested.
 */
public interface Requester {

    UUID id();

    /**
     * Whether the sender is still connected and allowed to act.
     */
    boolean isConnected();

    /**
     * Whether the sender has at least the given operator permission level (0-4).
     */
    boolean hasPermissionLevel(int level);
}
