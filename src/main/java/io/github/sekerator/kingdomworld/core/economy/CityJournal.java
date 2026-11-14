package io.github.sekerator.kingdomworld.core.economy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Income and expenses of the treasury of a city by line and game day, for the last {@value #DAYS_KEPT} days; older days are
 * forgotten when a new day is written.
 */
public final class CityJournal {
    public static final int DAYS_KEPT = 8;

    private final NavigableMap<Long, EnumMap<CityLedger, TreasuryJournal.Totals>> days = new TreeMap<>();

    public void record(long day, CityLedger line, long income, long expense) {
        if (income < 0 || expense < 0 || (income == 0 && expense == 0)) {
            return;
        }
        EnumMap<CityLedger, TreasuryJournal.Totals> entries = days.computeIfAbsent(day, d -> new EnumMap<>(CityLedger.class));
        TreasuryJournal.Totals before = entries.getOrDefault(line, TreasuryJournal.Totals.ZERO);
        entries.put(line, new TreasuryJournal.Totals(Money.addCapped(before.income(), income), Money.addCapped(before.expense(), expense)));
        while (!days.isEmpty() && days.firstKey() <= day - DAYS_KEPT) {
            days.pollFirstEntry();
        }
    }

    /**
     * The totals of a day by line.
     */
    public Map<CityLedger, TreasuryJournal.Totals> day(long day) {
        EnumMap<CityLedger, TreasuryJournal.Totals> entries = days.get(day);
        return entries == null ? Map.of() : Collections.unmodifiableMap(entries);
    }

    /**
     * Income of one line over the days from {@code from} to {@code to}, both included.
     */
    public long income(CityLedger line, long from, long to) {
        long sum = 0;
        for (EnumMap<CityLedger, TreasuryJournal.Totals> entries : days.subMap(from, true, to, true).values()) {
            TreasuryJournal.Totals totals = entries.get(line);
            if (totals != null) {
                sum = Money.addCapped(sum, totals.income());
            }
        }
        return sum;
    }

    /**
     * Expenses of one line over the days from {@code from} to {@code to}, both included.
     */
    public long expense(CityLedger line, long from, long to) {
        long sum = 0;
        for (EnumMap<CityLedger, TreasuryJournal.Totals> entries : days.subMap(from, true, to, true).values()) {
            TreasuryJournal.Totals totals = entries.get(line);
            if (totals != null) {
                sum = Money.addCapped(sum, totals.expense());
            }
        }
        return sum;
    }

    /**
     * Every remembered day, for saving.
     */
    public Map<Long, Map<CityLedger, TreasuryJournal.Totals>> days() {
        Map<Long, Map<CityLedger, TreasuryJournal.Totals>> copy = new TreeMap<>();
        days.forEach((day, entries) -> copy.put(day, Collections.unmodifiableMap(new EnumMap<>(entries))));
        return Collections.unmodifiableMap(copy);
    }

    public void restore(long day, Map<CityLedger, TreasuryJournal.Totals> entries) {
        if (!entries.isEmpty()) {
            days.put(day, new EnumMap<>(entries));
        }
    }
}
