package io.github.sekerator.kingdomworld.core.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The treasury of a city of a kingdom of players (0 the capital): what its king set for it, what the crown granted it and it
 * has not spent yet, how long its people remember a treasury taken from them, its journal and the statements of its last
 * days. The money itself lies in {@link CityPurses} under {@link CityPurses.PurseKind#CITY_TREASURY}.
 *
 * <p>The taxes of a city come into its treasury; at the end of its day the share of the crown of what came in that day goes
 * to the treasury of the kingdom, and a city without a head keeps no more than a week of its own share, a city with one no
 * more than a month of it besides what it saves for. What the crown granted never goes back by itself.
 */
public final class CityTreasury {
    /** Statements a city keeps. */
    public static final int STATEMENTS = 7;
    /** Days the people of a city remember that its treasury was taken. */
    public static final int RECALL_DAYS = 3;
    /** The most the loyalty of a noble house falls for a treasury taken. */
    public static final int MAX_LOYALTY_LOSS = 20;
    /** The value of a setting that follows the kingdom. */
    public static final int AS_KINGDOM = -1;

    private final UUID kingdomId;
    private final long cityId;
    private int salesTaxPermille = AS_KINGDOM;
    private int tithePermille = AS_KINGDOM;
    private int crownSharePermille = AS_KINGDOM;
    private long granted;
    private long recalledUntil = -1;
    private long committed;
    private long lastDay = -1;
    private final CityJournal journal = new CityJournal();
    private final Deque<CityStatement> statements = new ArrayDeque<>();
    private final CityStatement.Tally tally = new CityStatement.Tally();

    public CityTreasury(UUID kingdomId, long cityId) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.cityId = cityId;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public long cityId() {
        return cityId;
    }

    public CityPurses.PurseKey key() {
        return CityPurses.PurseKey.cityTreasury(kingdomId, cityId);
    }

    // ---------------------------------------------------------------- settings

    public int salesTaxSetting() {
        return salesTaxPermille;
    }

    public int titheSetting() {
        return tithePermille;
    }

    public int crownShareSetting() {
        return crownSharePermille;
    }

    /**
     * The tax on sales of the city in thousandths, its own or the kingdom's.
     */
    public int salesTax(int kingdomPermille) {
        return salesTaxPermille >= 0 ? salesTaxPermille : kingdomPermille;
    }

    public int tithe(int kingdomPermille) {
        return tithePermille >= 0 ? tithePermille : kingdomPermille;
    }

    public int crownShare(int kingdomPermille) {
        return crownSharePermille >= 0 ? crownSharePermille : kingdomPermille;
    }

    /**
     * Sets what the city pays and sends, in thousandths; {@link #AS_KINGDOM} follows the kingdom.
     */
    public void setTaxes(int salesTax, int tithe, int crownShare) {
        salesTaxPermille = setting(salesTax, CityEconomyRules.MAX_SALES_TAX_PERCENT);
        tithePermille = setting(tithe, CityEconomyRules.MAX_TITHE_PERCENT);
        crownSharePermille = setting(crownShare, CityEconomyRules.MAX_CROWN_SHARE_PERCENT);
    }

    private static int setting(int permille, int maxPercent) {
        return permille < 0 ? AS_KINGDOM : Math.min(permille, maxPercent * 10);
    }

    /**
     * Lowers the settings of the city that are above new settings of the kingdom down to them, as a demand of the nobles does.
     *
     * @return whether anything changed
     */
    public boolean capTaxes(int salesTax, int tithe) {
        boolean changed = false;
        if (salesTaxPermille > salesTax) {
            salesTaxPermille = salesTax;
            changed = true;
        }
        if (tithePermille > tithe) {
            tithePermille = tithe;
            changed = true;
        }
        return changed;
    }

    // ---------------------------------------------------------------- grants and recalls

    /**
     * What the crown granted and the city has not spent yet.
     */
    public long granted() {
        return granted;
    }

    void setGranted(long value) {
        granted = Math.max(0, Math.min(Money.MAX, value));
    }

    /**
     * The day until which the people of the city remember that its treasury was taken, -1 if it never was.
     */
    public long recalledUntil() {
        return recalledUntil;
    }

    public boolean recalled(long day) {
        return day <= recalledUntil;
    }

    void setRecalledUntil(long day) {
        recalledUntil = Math.max(recalledUntil, day);
    }

    /**
     * What the head of the city promised its constructions.
     */
    public long committed() {
        return committed;
    }

    void setCommitted(long value) {
        committed = Math.max(0, Math.min(Money.MAX, value));
    }

    // ---------------------------------------------------------------- days

    /**
     * The last day the city lived, -1 before the first.
     */
    public long lastDay() {
        return lastDay;
    }

    public void setLastDay(long day) {
        lastDay = day;
    }

    public CityJournal journal() {
        return journal;
    }

    /**
     * What the city wrote today, sealed into a statement at the end of its day.
     */
    public CityStatement.Tally tally() {
        return tally;
    }

    /**
     * The statements of the last days, the newest last.
     */
    public List<CityStatement> statements() {
        return List.copyOf(statements);
    }

    /**
     * The newest statement, or an empty one.
     */
    public CityStatement latest() {
        return statements.isEmpty() ? CityStatement.empty(-1) : statements.peekLast();
    }

    /**
     * Ends the day of the city: what it wrote becomes the statement of the day, with what its treasury holds now.
     */
    public CityStatement seal(long day, long balance) {
        tally.set(CityStatement.Line.BALANCE, balance);
        CityStatement statement = tally.seal(day);
        statements.addLast(statement);
        while (statements.size() > STATEMENTS) {
            statements.pollFirst();
        }
        lastDay = Math.max(lastDay, day);
        return statement;
    }

    public void restoreStatement(CityStatement statement) {
        statements.addLast(statement);
        while (statements.size() > STATEMENTS) {
            statements.pollFirst();
        }
    }

    /**
     * Restores what was read from saved data, without a check of the money.
     */
    public void restore(int salesTax, int tithe, int crownShare, long granted, long recalledUntil, long committed, long lastDay) {
        setTaxes(salesTax, tithe, crownShare);
        setGranted(granted);
        this.recalledUntil = recalledUntil;
        setCommitted(committed);
        this.lastDay = lastDay;
    }

    /**
     * The average of a line over the last statements and what the city wrote today, as many days as there are, at most
     * {@link #STATEMENTS}.
     */
    public long averageWithToday(CityStatement.Line line) {
        List<CityStatement> recent = new ArrayList<>(statements);
        while (recent.size() > STATEMENTS - 1) {
            recent.remove(0);
        }
        long sum = tally.get(line);
        for (CityStatement statement : recent) {
            sum = Money.addCapped(sum, statement.get(line));
        }
        return sum / (recent.size() + 1);
    }

    /**
     * The average daily tax income over the last week, today included.
     */
    public long averageTax() {
        List<CityStatement> recent = new ArrayList<>(statements);
        while (recent.size() > STATEMENTS - 1) {
            recent.remove(0);
        }
        long sum = Money.addCapped(tally.get(CityStatement.Line.TITHE), tally.get(CityStatement.Line.SALES_TAX));
        for (CityStatement statement : recent) {
            sum = Money.addCapped(sum, statement.taxIncome());
        }
        return sum / (recent.size() + 1);
    }

    /**
     * The average daily tax income over the sealed statements, for views.
     */
    public long averageTaxSealed() {
        if (statements.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (CityStatement statement : statements) {
            sum = Money.addCapped(sum, statement.taxIncome());
        }
        return sum / statements.size();
    }

    // ---------------------------------------------------------------- the rules

    /**
     * What a city sends the crown at the end of its day.
     *
     * @param share what the crown gets of the taxes of the day
     * @param idle  what the city keeps beyond its reserve
     * @param kept  what of the taxes of the day the city keeps
     */
    public record Settlement(long share, long idle, long kept) {
        public long total() {
            return Money.addCapped(share, idle);
        }
    }

    /**
     * Works out what a city sends the crown at the end of its day; nothing of what the crown granted goes back.
     *
     * @param balance    what the treasury of the city holds
     * @param granted    what of it the crown granted and the city has not spent
     * @param taxToday   the taxes that came in today
     * @param averageTax the average tax income of the last week, today included
     * @param sharePermille the share of the crown in thousandths
     * @param hasHead    whether the city has a head who spends its money
     * @param averageKept for a city with a head, the average of its own share over the last week
     * @param saving     for a city with a head, what it promised its constructions and saves for
     */
    public static Settlement settle(long balance, long granted, long taxToday, long averageTax, int sharePermille, boolean hasHead,
                                    long averageKept, long saving, CityEconomyRules rules) {
        long free = Math.max(0, balance - Math.max(0, granted));
        long share = Math.min(free, Money.share(Math.max(0, taxToday), Math.max(0, Math.min(1000, sharePermille)), 1000));
        long kept = Math.max(0, taxToday - share);
        long left = free - share;
        long reserve;
        if (hasHead) {
            reserve = Money.addCapped(Money.multiplyCapped(rules.cityReserveDays(), Math.max(0, averageKept)), Math.max(0, saving));
        } else {
            long keptPart = Money.share(Math.max(0, averageTax), 1000 - Math.max(0, Math.min(1000, sharePermille)), 1000);
            reserve = Money.multiplyCapped(rules.idleReserveDays(), keptPart);
        }
        long idle = Math.max(0, left - reserve);
        return new Settlement(share, idle, kept);
    }

    /**
     * How far the loyalty of the noble house that holds a city falls when the crown takes an amount beyond what it granted.
     */
    public static int loyaltyLoss(long beyondGrant, long averageDailyIncome) {
        if (beyondGrant <= 0) {
            return 0;
        }
        if (averageDailyIncome <= 0) {
            return MAX_LOYALTY_LOSS;
        }
        long week = Money.multiplyCapped(7, averageDailyIncome);
        long loss = Money.share(10, beyondGrant, week);
        return (int) Math.min(MAX_LOYALTY_LOSS, loss);
    }
}
