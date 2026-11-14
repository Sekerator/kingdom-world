package io.github.sekerator.kingdomworld.core.guide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns a value of the configuration into the number a page of the guide shows: one number taken from a table of numbers,
 * scaled when the text asks for it (copper coins into gold ones, ticks into seconds), written with the decimal separator of the
 * language and without needless zeros.
 */
public final class GuideNumbersFormat {
    /** Shown where a value is missing or is not a number that can be scaled. */
    public static final String UNKNOWN = "?";

    private GuideNumbersFormat() {
    }

    /**
     * The number a token of the text shows for the raw value of the configuration.
     *
     * @param raw   the value as the configuration holds it ({@code "25"}, {@code "0.3"}, {@code "1, 2, 3"}), or null when unknown
     * @param comma whether the language writes decimals with a comma
     */
    public static String format(String raw, GuideInline.Number number, boolean comma) {
        if (raw == null) {
            return UNKNOWN;
        }
        String value = raw.strip();
        if (number.index() > 0) {
            String[] parts = value.replace("[", "").replace("]", "").strip().split("[,;\\s]+");
            if (number.index() > parts.length || parts[number.index() - 1].isEmpty()) {
                return UNKNOWN;
            }
            value = parts[number.index() - 1];
        }
        BigDecimal decimal = parse(value);
        if (decimal == null) {
            return number.operator() == 0 ? value : UNKNOWN;
        }
        if (number.operator() == '*') {
            decimal = decimal.multiply(BigDecimal.valueOf(number.operand()));
        } else if (number.operator() == '/') {
            if (number.operand() == 0) {
                return UNKNOWN;
            }
            decimal = decimal.divide(BigDecimal.valueOf(number.operand()), 6, RoundingMode.HALF_UP);
        }
        String text = plain(decimal);
        return comma ? text.replace('.', ',') : text;
    }

    /**
     * A number without needless zeros: whole numbers as they are, others with two decimals at most (four for small ones).
     */
    public static String plain(double value) {
        return plain(BigDecimal.valueOf(value));
    }

    private static String plain(BigDecimal value) {
        BigDecimal abs = value.abs();
        int scale = abs.compareTo(BigDecimal.ONE) >= 0 || abs.signum() == 0 ? 2 : abs.compareTo(new BigDecimal("0.01")) >= 0 ? 3 : 4;
        BigDecimal rounded = value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 0) {
            rounded = rounded.setScale(0, RoundingMode.UNNECESSARY);
        }
        String text = rounded.toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    private static BigDecimal parse(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
