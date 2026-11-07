package io.github.sekerator.kingdomworld.core.magic;

import java.util.Locale;
import java.util.Optional;

/**
 * What the mage tower of a kingdom can research. The first five open the spells of the kingdom; the arcana only raise
 * the level of the tower, which strong spells of players need. Every finished research raises the level by one.
 */
public enum Research {
    WARD(null),
    HEALING(null),
    FERTILITY(null),
    TAILWIND(null),
    GATES(WARD),
    ARCANA_1(null),
    ARCANA_2(ARCANA_1),
    ARCANA_3(ARCANA_2);

    /** Number of researches. */
    public static final int COUNT = values().length;

    private final Research requires;

    Research(Research requires) {
        this.requires = requires;
    }

    /**
     * A research that must be finished first, if any.
     */
    public Optional<Research> requires() {
        return Optional.ofNullable(requires);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Research> byId(String id) {
        for (Research research : values()) {
            if (research.id().equals(id)) {
                return Optional.of(research);
            }
        }
        return Optional.empty();
    }

    public static Research ofIndex(int index) {
        return values()[Math.max(0, Math.min(COUNT - 1, index))];
    }
}
