package io.github.sekerator.kingdomworld.core.siege;

import java.util.Objects;
import java.util.UUID;

/**
 * A siege of a city of the enemy: the attacker leads its squads with the order "siege" to the city, breaks its gates and
 * holds its square. Without players near it goes on far from them: the gate loses strength by the power of the squads and
 * the square is held while no defenders stand there.
 *
 * @param city  the city besieged, 0 for the capital
 * @param x     where the city stands
 * @param z     where the city stands
 * @param rams  rams the besiegers brought
 * @param npc   whether a ruler of the world leads it
 */
public record Siege(long war, UUID attacker, UUID defender, long city, String dimension, int x, int y, int z, long started, int rams,
                    boolean npc) {
    /** Most rams one siege brings. */
    public static final int MAX_RAMS = 4;

    public Siege {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(dimension, "dimension");
        rams = Math.max(0, Math.min(MAX_RAMS, rams));
    }

    public static String key(long war, UUID defender, long city) {
        return war + "/" + defender + "/" + city;
    }

    public String key() {
        return key(war, defender, city);
    }

    public Siege withRams(int value) {
        return new Siege(war, attacker, defender, city, dimension, x, y, z, started, value, npc);
    }
}
