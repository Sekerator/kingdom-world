package io.github.sekerator.kingdomworld.core.economy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What one day of a city was worth: the treasury of the city, the money of the crown from the city and its goods at the price
 * of export. The lines are kept by name; new ones only at the end.
 *
 * <p>The money of the crown from a city is what came in from it (goods sold to its shops and its treasury, evenings in its
 * taverns, the exports of its merchants, the coins of its mints, the share of its taxes and what it keeps beyond its reserve)
 * less what went to it (the wages of the workers of the crown who live in it, grants and the shares of the nobles of its fief).
 * Its goods are what the posts of the crown in it made, less the raw materials of its workshops, what its people ate and what
 * left the storehouses for its shops and the merchants.
 */
public record CityStatement(long day, long[] values) {
    /**
     * The lines of a statement.
     */
    public enum Line {
        TITHE, SALES_TAX, GRANTED, KEPT, TO_CROWN, SPENT, BALANCE,
        CROWN_WAGES, CROWN_GOODS_SOLD, CROWN_TAVERNS, CROWN_EXPORTS, CROWN_MINTING, CROWN_SHARE, CROWN_GRANTS, CROWN_NOBLES,
        GOODS_MADE, GOODS_RAW, GOODS_EATEN, GOODS_OUT,
        /** What came into the purse of the townspeople. */
        PURSE_IN,
        /** What the shops of the city sold. */
        SALES,
        /** What the townspeople wanted to spend in the shops of the city. */
        BASKET_WANTED,
        /** What of it they spent there. */
        BASKET_SPENT,
        /** What the purse did not spend: the demand that found no goods. */
        UNSPENT,
        /** What players paid in the shops. */
        PLAYERS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Line> byId(String id) {
            for (Line line : values()) {
                if (line.id().equals(id)) {
                    return Optional.of(line);
                }
            }
            return Optional.empty();
        }
    }

    public static final int LINES = Line.values().length;

    public CityStatement {
        if (values == null || values.length != LINES) {
            long[] fixed = new long[LINES];
            if (values != null) {
                System.arraycopy(values, 0, fixed, 0, Math.min(values.length, LINES));
            }
            values = fixed;
        } else {
            values = values.clone();
        }
        for (int i = 0; i < LINES; i++) {
            values[i] = Math.max(0, Math.min(Money.MAX, values[i]));
        }
    }

    public static CityStatement empty(long day) {
        return new CityStatement(day, new long[LINES]);
    }

    public long get(Line line) {
        return values[line.ordinal()];
    }

    @Override
    public long[] values() {
        return values.clone();
    }

    /**
     * The tax income of the treasury of the city on the day.
     */
    public long taxIncome() {
        return Money.addCapped(get(Line.TITHE), get(Line.SALES_TAX));
    }

    /**
     * What the crown got in money from the city, less what it paid for it; may be below 0.
     */
    public long crownMoney() {
        return get(Line.CROWN_GOODS_SOLD) + get(Line.CROWN_TAVERNS) + get(Line.CROWN_EXPORTS) + get(Line.CROWN_MINTING)
                + get(Line.CROWN_SHARE) - get(Line.CROWN_WAGES) - get(Line.CROWN_GRANTS) - get(Line.CROWN_NOBLES);
    }

    /**
     * What the goods of the city were worth to the crown at the price of export; may be below 0.
     */
    public long crownGoods() {
        return get(Line.GOODS_MADE) - get(Line.GOODS_RAW) - get(Line.GOODS_EATEN) - get(Line.GOODS_OUT);
    }

    public long crownTotal() {
        return crownMoney() + crownGoods();
    }

    /**
     * The share of their basket the townspeople bought in the shops of the city, in percent; 0 when they wanted nothing.
     */
    public int basketPercent() {
        long wanted = get(Line.BASKET_WANTED);
        return wanted <= 0 ? 0 : (int) Math.min(100, Money.share(100, get(Line.BASKET_SPENT), wanted));
    }

    /**
     * The average of a line over statements, rounded down; 0 for none.
     */
    public static long average(List<CityStatement> statements, Line line) {
        if (statements.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (CityStatement statement : statements) {
            sum = Money.addCapped(sum, statement.get(line));
        }
        return sum / statements.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CityStatement that && day == that.day && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(day) * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "CityStatement[day=" + day + ", values=" + Arrays.toString(values) + "]";
    }

    /**
     * The statement a city fills during its day, sealed at its end.
     */
    public static final class Tally {
        private final long[] values = new long[LINES];

        public void add(Line line, long amount) {
            if (amount > 0) {
                values[line.ordinal()] = Money.addCapped(values[line.ordinal()], amount);
            }
        }

        public void set(Line line, long amount) {
            values[line.ordinal()] = Math.max(0, Math.min(Money.MAX, amount));
        }

        public long get(Line line) {
            return values[line.ordinal()];
        }

        public CityStatement seal(long day) {
            CityStatement statement = new CityStatement(day, values);
            Arrays.fill(values, 0);
            return statement;
        }

        public long[] snapshot() {
            return values.clone();
        }

        public void restore(long[] saved) {
            Arrays.fill(values, 0);
            for (int i = 0; i < Math.min(saved.length, LINES); i++) {
                values[i] = Math.max(0, Math.min(Money.MAX, saved[i]));
            }
        }

        public boolean isEmpty() {
            for (long value : values) {
                if (value != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
