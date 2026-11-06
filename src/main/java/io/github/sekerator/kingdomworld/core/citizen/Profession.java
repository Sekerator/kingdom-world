package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A profession a citizen can be given.
 *
 * @param id            stable lower case id, also used in translation keys ({@code kingdomworld.profession.<id>})
 * @param workplaceKind kind of place the profession works at (see {@link SiteKinds}), or {@code null} if it needs
 *                      none
 */
public record Profession(String id, String workplaceKind) {
    /** Longest id; ids travel over the network and into saved data. */
    public static final int MAX_ID_LENGTH = 32;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*");

    public Profession {
        Objects.requireNonNull(id, "id");
        if (id.length() > MAX_ID_LENGTH || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid profession id '" + id + "'");
        }
    }

    public boolean needsWorkplace() {
        return workplaceKind != null;
    }

    public static boolean isValidId(String id) {
        return id != null && id.length() <= MAX_ID_LENGTH && ID.matcher(id).matches();
    }
}
