package io.github.sekerator.kingdomworld.core.economy;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Amounts of money: whole numbers of the smallest unit of a currency (a copper coin), never negative and never
 * larger than {@link #MAX}, so that no sum of a few balances can overflow a {@code long}. Every change of a balance
 * goes through the checked methods here; nothing uses floating point.
 */
public final class Money {
    /** The largest amount any balance, debt or payment may reach: a million billion copper coins. */
    public static final long MAX = 1_000_000_000_000_000L;
    /** Longest amount a player may type. */
    public static final int MAX_TEXT = 40;

    private Money() {
    }

    public static boolean isAmount(long amount) {
        return amount >= 0 && amount <= MAX;
    }

    /**
     * A positive amount a player may move: from 1 to {@link #MAX}.
     */
    public static boolean isPayment(long amount) {
        return amount >= 1 && amount <= MAX;
    }

    /**
     * The sum of two amounts, or empty if either is not an amount or the sum would exceed {@link #MAX}.
     */
    public static OptionalLong add(long a, long b) {
        if (!isAmount(a) || !isAmount(b)) {
            return OptionalLong.empty();
        }
        long sum = a + b;
        return sum > MAX ? OptionalLong.empty() : OptionalLong.of(sum);
    }

    /**
     * The sum of two amounts, at most {@link #MAX}; for totals that are only shown or for debts that stop growing.
     */
    public static long addCapped(long a, long b) {
        return Math.min(MAX, Math.max(0, Math.min(MAX, a)) + Math.max(0, Math.min(MAX, b)));
    }

    /**
     * A product of non-negative numbers, at most {@link #MAX}.
     */
    public static long multiplyCapped(long a, long b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        return a > MAX / b ? MAX : Math.min(MAX, a * b);
    }

    /**
     * {@code amount * numerator / denominator} rounded down, without overflow; for shares of a payment.
     */
    public static long share(long amount, long numerator, long denominator) {
        if (amount <= 0 || numerator <= 0 || denominator <= 0) {
            return 0;
        }
        return java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(numerator))
                .divide(java.math.BigInteger.valueOf(denominator)).min(java.math.BigInteger.valueOf(MAX)).longValue();
    }

    /**
     * Reads an amount typed by a player: a plain number of the smallest unit ({@code 125}) or coins with their
     * letters, Latin or Russian, in any order and with or without spaces ({@code 1g2s5c}, {@code 1з 2с 5м}).
     *
     * @return the amount, or empty if the text is not an amount or the amount is out of range
     */
    public static Optional<Long> parse(String text, CurrencyUnits units) {
        if (text == null) {
            return Optional.empty();
        }
        String value = text.strip().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.isEmpty() || value.length() > MAX_TEXT) {
            return Optional.empty();
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return number(value).filter(Money::isAmount);
        }
        long total = 0;
        int i = 0;
        boolean[] seen = new boolean[Denomination.values().length];
        while (i < value.length()) {
            int start = i;
            while (i < value.length() && Character.isDigit(value.charAt(i))) {
                i++;
            }
            if (start == i || i >= value.length()) {
                return Optional.empty();
            }
            Optional<Long> count = number(value.substring(start, i));
            Denomination denomination = letter(value.charAt(i));
            i++;
            if (count.isEmpty() || denomination == null || seen[denomination.ordinal()]) {
                return Optional.empty();
            }
            seen[denomination.ordinal()] = true;
            if (count.get() > MAX / units.value(denomination)) {
                return Optional.empty();
            }
            OptionalLong sum = add(total, count.get() * units.value(denomination));
            if (sum.isEmpty()) {
                return Optional.empty();
            }
            total = sum.getAsLong();
        }
        return Optional.of(total);
    }

    private static Optional<Long> number(String digits) {
        if (digits.length() > 19) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * The coin a letter names: g, s, c in Latin and з, с, м in Russian.
     */
    private static Denomination letter(char c) {
        return switch (c) {
            case 'g', 'з' -> Denomination.GOLD;
            case 's', 'с' -> Denomination.SILVER;
            case 'c', 'м' -> Denomination.COPPER;
            default -> null;
        };
    }
}
