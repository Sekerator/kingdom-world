package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.UUID;

/**
 * A place in a kingdom's land that citizens use: a bed, a workplace, an infirmary.
 *
 * @param kind     what the place is, see {@link SiteKinds}
 * @param occupant the citizen living or working there, or {@code null}; infirmaries have no fixed occupant
 */
public record Site(WorldPos pos, String kind, UUID kingdomId, UUID occupant) {
    public Site {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(kingdomId, "kingdomId");
    }

    public boolean isFree() {
        return occupant == null;
    }

    Site withOccupant(UUID citizen) {
        return new Site(pos, kind, kingdomId, citizen);
    }
}
