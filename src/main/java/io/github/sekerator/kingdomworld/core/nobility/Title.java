package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Locale;
import java.util.Optional;

/**
 * Titles of the nobility, from the lowest to the highest. A title always comes with a fief.
 */
public enum Title {
    BARON,
    COUNT,
    DUKE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Title> byId(String id) {
        for (Title title : values()) {
            if (title.id().equals(id)) {
                return Optional.of(title);
            }
        }
        return Optional.empty();
    }

    /**
     * The next title, wrapping around; used by the tab to choose one.
     */
    public Title next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
