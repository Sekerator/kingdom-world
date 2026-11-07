package io.github.sekerator.kingdomworld.core.magic;

import java.util.Locale;
import java.util.Optional;

/**
 * Spells of a kingdom: switched on in the "Magic" tab, they spend the kingdom's energy while they work. Spells paid by
 * the day are charged when switched on and at the start of every game day; the others are paid per use. Without enough
 * energy a spell switches off; there is no debt of energy.
 */
public enum KingdomSpell {
    /** Fewer hostile monsters in the chosen chunks; paid per chunk and day. */
    WARD(Research.WARD, true),
    /** The infirmary heals faster; paid per wounded citizen. */
    HEALING(Research.HEALING, false),
    /** Fields and animals around work posts grow faster; paid per post and day. */
    FERTILITY(Research.FERTILITY, true),
    /** Caravans travel faster; paid per caravan. */
    TAILWIND(Research.TAILWIND, false),
    /** Subjects travel between the gates of the kingdom; paid per trip. */
    GATES(Research.GATES, false);

    public static final int COUNT = values().length;

    private final Research research;
    private final boolean daily;

    KingdomSpell(Research research, boolean daily) {
        this.research = research;
        this.daily = daily;
    }

    /**
     * The research that opens the spell.
     */
    public Research research() {
        return research;
    }

    /**
     * Whether the spell is paid at the start of every day rather than per use.
     */
    public boolean daily() {
        return daily;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<KingdomSpell> byId(String id) {
        for (KingdomSpell spell : values()) {
            if (spell.id().equals(id)) {
                return Optional.of(spell);
            }
        }
        return Optional.empty();
    }

    public static KingdomSpell ofIndex(int index) {
        return values()[Math.max(0, Math.min(COUNT - 1, index))];
    }
}
