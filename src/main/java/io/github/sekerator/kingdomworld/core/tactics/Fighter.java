package io.github.sekerator.kingdomworld.core.tactics;

import java.util.Objects;
import java.util.UUID;

/**
 * One of a squad as its leader sees it in a fight.
 *
 * @param health   health now
 * @param full     full health
 * @param ranged   whether it fights with a bow or a crossbow
 * @param shield   whether it carries a shield
 * @param skill    how well it leads: 0 a fighter, 1 a sergeant or the most experienced, 2 a vice-captain, 3 a captain
 * @param strength what it is worth in a fight (health and damage), for the balance of the fight
 * @param healer   whether it heals the wounded (a mage or a healer)
 */
public record Fighter(UUID id, Spot spot, double health, double full, boolean ranged, boolean shield, int skill, double strength,
                      boolean healer) {
    public Fighter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spot, "spot");
        if (!(full > 0) || health < 0 || strength < 0 || skill < 0) {
            throw new IllegalArgumentException("Fighter out of range");
        }
    }

    public Fighter(UUID id, Spot spot, double health, double full, boolean ranged, boolean shield, int skill, double strength) {
        this(id, spot, health, full, ranged, shield, skill, strength, false);
    }

    /** The share of its full health it has, 0 to 1. */
    public double share() {
        return Math.max(0.0, Math.min(1.0, health / full));
    }

    /** What it is worth now: its strength by the health it has left. */
    public double worth() {
        return strength * share();
    }
}
