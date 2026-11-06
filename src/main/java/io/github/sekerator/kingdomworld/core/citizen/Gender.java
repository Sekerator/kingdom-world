package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;
import java.util.Optional;

/**
 * Gender of a citizen; chooses the name endings and the body model.
 */
public enum Gender {
    MALE,
    FEMALE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Gender> byId(String id) {
        for (Gender gender : values()) {
            if (gender.id().equals(id)) {
                return Optional.of(gender);
            }
        }
        return Optional.empty();
    }
}
