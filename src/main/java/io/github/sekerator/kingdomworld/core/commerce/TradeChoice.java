package io.github.sekerator.kingdomworld.core.commerce;

import java.util.Collection;
import java.util.Set;

/**
 * Which trade a place of a shop takes up: what its city lacks most among the crafts whose workstations stand there, else a
 * trading house where the masters of the city make more than the city buys, else the general store. A place nobody looked at
 * yet resells until its workstations are known. A trade fixed by a member with the right over citizens never changes, and a shop
 * changes its trade at most once a week, after it sold off what it holds.
 */
public final class TradeChoice {
    /** Days a shop keeps its trade at least. */
    public static final int CHANGE_DAYS = 7;

    private TradeChoice() {
    }

    /**
     * What is known of a place.
     *
     * @param stations   the workstations found at it
     * @param surveyed   whether it was looked at
     * @param tradeHouse whether a trading house may stand there: a working market in the city or a house of a merchant
     */
    public record Place(Set<String> stations, boolean surveyed, boolean tradeHouse) {
        public Place {
            stations = Set.copyOf(stations);
        }
    }

    /**
     * What is known of a city.
     *
     * @param unmet           what its people did not find to buy yesterday, worth by kind of goods; for building materials what
     *                        the crown and the head of the city could not buy
     * @param masters         whether masters work in it
     * @param outsideUnchosen the demand from abroad of the city that no trading house took yesterday
     */
    public record City(long[] unmet, boolean masters, double outsideUnchosen) {
        public City {
            long[] all = new long[GoodsCategory.values().length];
            if (unmet != null) {
                System.arraycopy(unmet, 0, all, 0, Math.min(unmet.length, all.length));
            }
            unmet = all;
        }

        public long unmet(GoodsCategory category) {
            return unmet[category.ordinal()];
        }
    }

    /**
     * How much a craft would give the city: what it lacks of the kinds the craft makes.
     */
    public static long need(Trade trade, City city) {
        long sum = 0;
        for (GoodsCategory category : trade.goods()) {
            sum += Math.max(0, city.unmet(category));
        }
        return sum;
    }

    /**
     * The trade a place would take up now.
     */
    public static String choose(Collection<Trade> trades, Place place, City city) {
        if (!place.surveyed()) {
            return Business.GENERAL_STORE;
        }
        Trade best = null;
        long bestNeed = 0;
        for (Trade trade : trades) {
            if (!trade.craft() || !trade.fits(place.stations())) {
                continue;
            }
            long need = need(trade, city);
            // Of two crafts that would give as much, the narrower one: a toolmaker before a carpenter for tools.
            if (need > bestNeed || need > 0 && need == bestNeed && best != null && trade.goods().size() < best.goods().size()) {
                best = trade;
                bestNeed = need;
            }
        }
        if (best != null) {
            return best.id();
        }
        boolean trader = false;
        for (Trade trade : trades) {
            if (trade.id().equals(Trade.TRADER)) {
                trader = true;
                break;
            }
        }
        if (trader && place.tradeHouse() && city.masters() && city.outsideUnchosen() > 0) {
            return Trade.TRADER;
        }
        return Business.GENERAL_STORE;
    }

    /**
     * The trade a standing shop would take up now: a workshop keeps its craft while its place serves it, unless another craft would
     * give the city more; a trading house keeps its trade while masters work in its city; else what {@link #choose} gives.
     *
     * @param current the trade the shop has
     */
    public static String chooseFor(Collection<Trade> trades, Place place, City city, String current) {
        String chosen = choose(trades, place, city);
        Trade now = null;
        Trade other = null;
        for (Trade trade : trades) {
            if (trade.id().equals(current)) {
                now = trade;
            }
            if (trade.id().equals(chosen)) {
                other = trade;
            }
        }
        if (now != null && now.craft() && place.surveyed() && now.fits(place.stations())) {
            if (other == null || !other.craft() || need(other, city) <= need(now, city)) {
                return current;
            }
        }
        if (now != null && now.kind() == Trade.Kind.EXPORT && place.tradeHouse() && city.masters() && !chosenCraft(other)) {
            return current;
        }
        return chosen;
    }

    private static boolean chosenCraft(Trade trade) {
        return trade != null && trade.craft();
    }

    /**
     * The trade a shop keeps or takes up: the fixed one, else the one chosen once its trade is a week old.
     *
     * @param current the trade it has
     * @param pinned  the trade fixed for its place, empty for none
     * @param chosen  what {@link #choose} gives for its place
     * @param since   the day it took up its trade
     */
    public static String next(String current, String pinned, String chosen, long since, long day) {
        if (pinned != null && !pinned.isEmpty()) {
            return pinned;
        }
        if (chosen.equals(current) || day - since < CHANGE_DAYS) {
            return current;
        }
        return chosen;
    }
}
