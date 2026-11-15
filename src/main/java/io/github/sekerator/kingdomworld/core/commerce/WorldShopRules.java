package io.github.sekerator.kingdomworld.core.commerce;

import java.util.Collection;
import java.util.List;

/**
 * The rules of the shops of citizens in the kingdoms of the world, apart from the server so that the tests share them: who may
 * keep one after the army, when a city opens one, which trades they take and how the tax of the ruler comes out of the purses of
 * its cities.
 */
public final class WorldShopRules {
    /** Days of shortage of the last seven after which a city of the world opens one more shop. */
    public static final int SHORT_DAYS_TO_OPEN = 3;

    private WorldShopRules() {
    }

    /**
     * How many more citizens without work may keep shops: those the army of the ruler does not want; before the first turn of the
     * ruler none, so that its army is never smaller for the shops.
     *
     * @param free      citizens without work who keep no shop
     * @param armyNeed  those the army still wants, or a negative number while unknown
     */
    public static int spareKeepers(int free, int armyNeed) {
        return armyNeed < 0 ? 0 : Math.max(0, free - armyNeed);
    }

    /**
     * How many shops a city of the world opens today: one while it has none, one more once those open ran short of goods three of
     * the last seven days.
     *
     * @param shortDays the last seven days, a bit each
     */
    public static int openings(boolean none, int shortDays) {
        return none || Integer.bitCount(shortDays & 0x7F) >= SHORT_DAYS_TO_OPEN ? 1 : 0;
    }

    /**
     * The days of shortage with today added.
     */
    public static int nextShortDays(int shortDays, boolean shortToday) {
        return ((shortDays << 1) | (shortToday ? 1 : 0)) & 0x7F;
    }

    /**
     * The trades a place of the world may take up: every one but the trading house.
     */
    public static List<Trade> trades(Collection<Trade> all) {
        return all.stream().filter(trade -> !Trade.TRADER.equals(trade.id())).toList();
    }

    /**
     * What the tax of a ruler takes of the purse of each city where the wages of its peaceful citizens lie: the share of each city
     * first, then what the rounding left of any purse with money, never more than a purse holds; together the tax, as long as the
     * purses hold it.
     *
     * @param tax     the tax as it was always worked out: the share of all the peaceful wages
     * @param paid    the peaceful wages paid into each purse
     * @param purses  what each purse holds now
     * @param percent the share of the wages the tax takes
     * @return what comes out of each purse
     */
    public static long[] taxFromPurses(long tax, long[] paid, long[] purses, int percent) {
        long[] taken = new long[paid.length];
        long left = Math.max(0, tax);
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < paid.length && left > 0; i++) {
                long want = pass == 0 ? Math.max(0, paid[i]) * percent / 100 : left;
                long part = Math.min(left, Math.min(want, Math.max(0, purses[i] - taken[i])));
                taken[i] += part;
                left -= part;
            }
        }
        return taken;
    }
}
