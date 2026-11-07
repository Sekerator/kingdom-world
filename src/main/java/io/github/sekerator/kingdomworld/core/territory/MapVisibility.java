package io.github.sekerator.kingdomworld.core.territory;

import java.util.UUID;

/**
 * Decides which kingdoms a player sees on maps. The server asks it before it sends land to a client, so a hidden
 * kingdom never reaches the client at all. For now the map of kingdoms is public, as on the atlas; later mechanics
 * (intelligence, fog of war) replace it.
 */
@FunctionalInterface
public interface MapVisibility {
    /** Every kingdom is visible to everyone. */
    MapVisibility PUBLIC = (viewer, kingdom) -> true;

    /**
     * @param viewer  the player looking at a map, or {@code null} for public web maps
     * @param kingdom the kingdom whose land would be shown
     */
    boolean canSee(UUID viewer, UUID kingdom);
}
