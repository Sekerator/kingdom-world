package io.github.sekerator.kingdomworld.core.economy;

import java.util.Locale;

/**
 * Plural forms of words after numbers in Russian and English.
 *
 * <p>Russian has three forms: one (1, 21, 101: «1 золотой», «21 медяк»), few (2-4, 22-24: «2 золотых», «3 медяка») and
 * many (0, 5-20, 25-30, 111-114: «5 медяков»). English has two: one (exactly 1) and other.
 *
 * <p>The server does not know the language of every player who reads a message, so a translation key carries a
 * {@link Form} that is enough for both languages at once: {@code n1} for exactly one, {@code n21} for numbers that are
 * singular in Russian but plural in English (21, 31, 101), {@code n2} for the Russian few and {@code n5} for the
 * Russian many. English gives its plural to the last three.
 */
public final class Plurals {

    /**
     * The Russian plural categories.
     */
    public enum Russian {
        ONE,
        FEW,
        MANY
    }

    /**
     * The English plural categories.
     */
    public enum English {
        ONE,
        OTHER
    }

    /**
     * A form for both languages, used as the suffix of translation keys.
     */
    public enum Form {
        /** Exactly one: singular in both languages. */
        N1,
        /** 21, 31, 101 and so on: singular in Russian, plural in English. */
        N21,
        /** 2-4, 22-24 and so on: the Russian few. */
        N2,
        /** 0, 5-20, 25-30 and so on: the Russian many. */
        N5;

        /**
         * The key suffix, for example {@code n21}.
         */
        public String suffix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Plurals() {
    }

    public static Russian russian(long number) {
        long n = Math.abs(number % 100);
        long last = n % 10;
        if (last == 1 && n != 11) {
            return Russian.ONE;
        }
        if (last >= 2 && last <= 4 && (n < 12 || n > 14)) {
            return Russian.FEW;
        }
        return Russian.MANY;
    }

    public static English english(long number) {
        return number == 1 ? English.ONE : English.OTHER;
    }

    public static Form form(long number) {
        if (number == 1) {
            return Form.N1;
        }
        return switch (russian(number)) {
            case ONE -> Form.N21;
            case FEW -> Form.N2;
            case MANY -> Form.N5;
        };
    }

    /**
     * Picks the Russian word for a number: {@code russian(21, "медяк", "медяка", "медяков")} gives «медяк».
     */
    public static String russian(long number, String one, String few, String many) {
        return switch (russian(number)) {
            case ONE -> one;
            case FEW -> few;
            case MANY -> many;
        };
    }

    /**
     * Picks the English word for a number: {@code english(2, "coin", "coins")} gives "coins".
     */
    public static String english(long number, String one, String other) {
        return english(number) == English.ONE ? one : other;
    }
}
