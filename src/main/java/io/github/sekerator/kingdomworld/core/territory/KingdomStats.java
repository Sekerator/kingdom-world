package io.github.sekerator.kingdomworld.core.territory;

import java.util.Objects;
import java.util.UUID;

/**
 * Facts about a kingdom that its chunk limit may depend on.
 *
 * @param kingdomId      the kingdom
 * @param members        all player members, the ruler included
 * @param playerSubjects player members other than the ruler
 * @param chunks         chunks the kingdom owns now
 */
public record KingdomStats(UUID kingdomId, int members, int playerSubjects, int chunks) {
    public KingdomStats {
        Objects.requireNonNull(kingdomId, "kingdomId");
    }
}
