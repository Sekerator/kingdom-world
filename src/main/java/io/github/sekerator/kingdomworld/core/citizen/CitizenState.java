package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;
import java.util.Optional;

/**
 * Health state of a citizen.
 */
public enum CitizenState {
    HEALTHY,
    /** Health below the wounded threshold: the citizen seeks the infirmary or home. */
    WOUNDED,
    /** Final; the record leaves the population and stays in the chronicle. */
    DEAD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CitizenState> byId(String id) {
        for (CitizenState state : values()) {
            if (state.id().equals(id)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
