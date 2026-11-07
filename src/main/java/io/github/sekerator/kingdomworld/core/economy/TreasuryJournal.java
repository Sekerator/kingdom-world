package io.github.sekerator.kingdomworld.core.economy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Income and expenses of a treasury by category and game day, for today, yesterday and the last seven days. Older
 * days are forgotten when a new day is written.
 */
public final class TreasuryJournal {
    /** Days the journal remembers, today included. */
    public static final int DAYS_KEPT = 8;

    /**
     * Money in and out.
     */
    public record Totals(long income, long expense) {
        public static final Totals ZERO = new Totals(0, 0);

        public Totals {
            if (!Money.isAmount(income) || !Money.isAmount(expense)) {
                throw new IllegalArgumentException("Totals out of range: " + income + "/" + expense);
            }
        }

        Totals plus(long moreIncome, long moreExpense) {
            return new Totals(Money.addCapped(income, moreIncome), Money.addCapped(expense, moreExpense));
        }

        public boolean isEmpty() {
            return income == 0 && expense == 0;
        }
    }

    /**
     * The periods the throne screen shows.
     */
    public enum Period {
        TODAY(0, 0),
        YESTERDAY(1, 1),
        WEEK(0, 6);

        private final int newest;
        private final int oldest;

        Period(int newest, int oldest) {
            this.newest = newest;
            this.oldest = oldest;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final NavigableMap<Long, EnumMap<LedgerCategory, Totals>> days = new TreeMap<>();

    /**
     * Writes money that came in or went out on a day.
     */
    public void record(long day, LedgerCategory category, long income, long expense) {
        if (income < 0 || expense < 0 || (income == 0 && expense == 0)) {
            return;
        }
        EnumMap<LedgerCategory, Totals> entries = days.computeIfAbsent(day, d -> new EnumMap<>(LedgerCategory.class));
        entries.put(category, entries.getOrDefault(category, Totals.ZERO).plus(income, expense));
        prune(day);
    }

    private void prune(long today) {
        while (!days.isEmpty() && days.firstKey() <= today - DAYS_KEPT) {
            days.pollFirstEntry();
        }
    }

    /**
     * Totals of each category over a period that ends on {@code today}.
     */
    public Map<LedgerCategory, Totals> totals(long today, Period period) {
        EnumMap<LedgerCategory, Totals> result = new EnumMap<>(LedgerCategory.class);
        for (Map.Entry<Long, EnumMap<LedgerCategory, Totals>> day : days.subMap(today - period.oldest, true, today - period.newest, true)
                .entrySet()) {
            day.getValue().forEach((category, totals) ->
                    result.put(category, result.getOrDefault(category, Totals.ZERO).plus(totals.income(), totals.expense())));
        }
        return result;
    }

    /**
     * Totals of all categories over a period.
     */
    public Totals sum(long today, Period period) {
        Totals sum = Totals.ZERO;
        for (Totals totals : totals(today, period).values()) {
            sum = sum.plus(totals.income(), totals.expense());
        }
        return sum;
    }

    /**
     * Every remembered day, for saving.
     */
    public Map<Long, Map<LedgerCategory, Totals>> days() {
        Map<Long, Map<LedgerCategory, Totals>> copy = new TreeMap<>();
        days.forEach((day, entries) -> copy.put(day, Collections.unmodifiableMap(new EnumMap<>(entries))));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Restores a day from saved data.
     */
    public void restore(long day, Map<LedgerCategory, Totals> entries) {
        if (!entries.isEmpty()) {
            days.put(day, new EnumMap<>(entries));
        }
    }
}
