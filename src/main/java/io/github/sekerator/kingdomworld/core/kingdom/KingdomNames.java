package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rules for kingdom names.
 *
 * <p>A name is normalized by trimming it and collapsing runs of whitespace into a single space. A valid name
 * is {@value #MIN_LENGTH}-{@value #MAX_LENGTH} characters long (UTF-16 code units, the same unit the network
 * layer uses for string limits), starts with a letter or digit and contains only letters, digits, spaces,
 * hyphens, apostrophes and underscores. Letters of any script are allowed.
 *
 * <p>Names are unique ignoring case; {@link #key(String)} gives the comparison key.
 */
public final class KingdomNames {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private KingdomNames() {
    }

    /**
     * Trims the name and collapses inner whitespace into single spaces.
     */
    public static String normalize(String raw) {
        return WHITESPACE.matcher(raw.strip()).replaceAll(" ");
    }

    /**
     * Case-insensitive uniqueness key of a name.
     */
    public static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT);
    }

    /**
     * Validates the normalized form of {@code raw}.
     *
     * @return the first problem found, or empty if the name is acceptable
     */
    public static Optional<NameProblem> validate(String raw) {
        String name = normalize(raw);
        if (name.isEmpty()) {
            return Optional.of(NameProblem.EMPTY);
        }
        if (name.length() < MIN_LENGTH) {
            return Optional.of(NameProblem.TOO_SHORT);
        }
        if (name.length() > MAX_LENGTH) {
            return Optional.of(NameProblem.TOO_LONG);
        }
        if (!Character.isLetterOrDigit(name.codePointAt(0))) {
            return Optional.of(NameProblem.INVALID_CHARACTERS);
        }
        boolean valid = name.codePoints().allMatch(KingdomNames::isAllowed);
        return valid ? Optional.empty() : Optional.of(NameProblem.INVALID_CHARACTERS);
    }

    public static boolean isValid(String raw) {
        return validate(raw).isEmpty();
    }

    private static boolean isAllowed(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == ' '
                || codePoint == '-'
                || codePoint == '\''
                || codePoint == '_';
    }
}
