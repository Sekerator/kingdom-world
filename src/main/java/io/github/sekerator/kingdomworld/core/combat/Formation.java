package io.github.sekerator.kingdomworld.core.combat;

import java.util.Locale;
import java.util.Optional;

/**
 * How a squad marches and fights: a line holds best, a column marches fastest and fights worst, a wedge strikes hardest and
 * holds worse.
 *
 * @param speed   the speed of the march, by the speed of a line
 * @param attack  the strength of a squad that attacks
 * @param defense the strength of a squad that is attacked
 */
public enum Formation {
    LINE(1.0, 1.0, 1.10),
    COLUMN(1.25, 0.90, 0.90),
    WEDGE(0.90, 1.15, 0.95);

    private final double speed;
    private final double attack;
    private final double defense;

    Formation(double speed, double attack, double defense) {
        this.speed = speed;
        this.attack = attack;
        this.defense = defense;
    }

    public double speed() {
        return speed;
    }

    public double attack() {
        return attack;
    }

    public double defense() {
        return defense;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Formation> byId(String id) {
        for (Formation formation : values()) {
            if (formation.id().equals(id)) {
                return Optional.of(formation);
            }
        }
        return Optional.empty();
    }
}
