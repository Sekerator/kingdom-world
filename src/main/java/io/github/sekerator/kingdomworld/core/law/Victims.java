package io.github.sekerator.kingdomworld.core.law;

import java.util.Optional;

/**
 * Who suffered from a crime, as the record keeps it in the text of {@link Crime#victim()}: nothing (unknown or the
 * kingdom at large), the name of a player, a citizen (by the seed of its name, so that every reader sees the name in its
 * own alphabet) or the storehouses of the kingdom.
 */
public final class Victims {
    /** The storehouses of the kingdom: what is taken from them is taken from the treasury. */
    public static final String TREASURY = "@treasury";
    private static final String CITIZEN = "@citizen:";

    private Victims() {
    }

    /**
     * A citizen as a victim.
     *
     * @param nameSeed the seed its name is made from
     */
    public record CitizenName(int nameSeed, boolean female) {
    }

    public static String citizen(int nameSeed, boolean female) {
        return CITIZEN + nameSeed + (female ? ":f" : ":m");
    }

    public static boolean isTreasury(String victim) {
        return TREASURY.equals(victim);
    }

    /**
     * The citizen a victim text names, if it names one.
     */
    public static Optional<CitizenName> citizen(String victim) {
        if (victim == null || !victim.startsWith(CITIZEN)) {
            return Optional.empty();
        }
        String rest = victim.substring(CITIZEN.length());
        int colon = rest.lastIndexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CitizenName(Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1).equals("f")));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a victim text is a plain name to show as it is.
     */
    public static boolean isName(String victim) {
        return victim != null && !victim.isEmpty() && !victim.startsWith("@");
    }
}
