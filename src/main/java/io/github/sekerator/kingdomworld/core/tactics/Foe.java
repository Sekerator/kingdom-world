package io.github.sekerator.kingdomworld.core.tactics;

import java.util.Objects;
import java.util.UUID;

/**
 * An enemy as a squad sees it: only one some fighter of the squad has in sight.
 *
 * @param danger   how much harm it does (its damage, with a bow at range more), for the focus of the squad
 * @param strength what it is worth in a fight, for the balance of the fight
 * @param ranged   whether it shoots
 * @param engaged  whether it fights one of the squad now
 * @param cornered whether it stands in a narrow way or a house, where only one or two can reach it at once
 * @param high     whether it stands where the squad cannot climb (a pillar, a ledge), out of reach of blades
 */
public record Foe(UUID id, Spot spot, double health, double full, double danger, double strength, boolean ranged, boolean engaged,
                  boolean cornered, boolean high) {
    public Foe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spot, "spot");
        if (!(full > 0) || health < 0 || danger < 0 || strength < 0) {
            throw new IllegalArgumentException("Foe out of range");
        }
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
