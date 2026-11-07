package io.github.sekerator.kingdomworld.core.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One side of a battle: fighters followed one by one (soldiers, adventurers, mercenaries), a crowd known only by its
 * strength and head count (monsters, robbers on the road), or both.
 *
 * @param strength strength of the crowd, besides the fighters
 * @param units    head count of the crowd
 */
public record Side(Kind kind, List<Fighter> fighters, double strength, int units) {

    /**
     * Who fights on a side.
     */
    public enum Kind {
        SOLDIERS,
        MERCENARIES,
        ADVENTURERS,
        MONSTERS,
        /** Robbers on the roads; the ambushes of caravans of a later stage. */
        BANDITS,
        /** The army of another kingdom; wars of a later stage. */
        ARMY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Side NOBODY = new Side(Kind.MONSTERS, List.of(), 0.0, 0);

    public Side {
        Objects.requireNonNull(kind, "kind");
        fighters = List.copyOf(fighters);
        strength = Double.isFinite(strength) ? Math.max(0.0, strength) : 0.0;
        units = Math.max(0, units);
    }

    public static Side of(Kind kind, List<Fighter> fighters) {
        return new Side(kind, fighters, 0.0, 0);
    }

    /**
     * A crowd of {@code units} with a strength in all.
     */
    public static Side crowd(Kind kind, double strength, int units) {
        return new Side(kind, List.of(), strength, units);
    }

    /**
     * Both sides fighting together, under the kind of this one.
     */
    public Side with(Side other) {
        List<Fighter> all = new ArrayList<>(fighters);
        all.addAll(other.fighters);
        return new Side(kind, all, strength + other.strength, units + other.units);
    }

    public double power() {
        return CombatPower.of(fighters) + (units > 0 ? strength : 0.0);
    }

    public int size() {
        return fighters.size() + units;
    }

    /**
     * Whether nobody fights on this side.
     */
    public boolean isEmpty() {
        return size() == 0 || power() <= 0.0;
    }
}
