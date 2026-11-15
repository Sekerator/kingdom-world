package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the crown buys of the shops of its citizens when its own workshops cannot make what it needs: the cheapest offers first,
 * whole items, never above the price it allows for an item and never beyond the money it may spend. The crown pays the price of
 * the shop without the tax: the treasury does not tax itself. Only goods citizens made are offered: what a shop bought of the
 * crown never goes back to it.
 */
public final class CrownPurchase {
    private CrownPurchase() {
    }

    /**
     * Goods a shop offers the crown.
     *
     * @param key        the key of the goods in the shop
     * @param priceMilli what one costs, in thousandths of a coin
     * @param worth      the worth of one
     */
    public record Offer(long shop, String key, long count, long priceMilli, double worth) {
    }

    /**
     * Goods the crown takes of a shop.
     *
     * @param total what it pays, in whole coins
     */
    public record Take(long shop, String key, long count, long total) {
    }

    /**
     * The most the crown pays for one of an item, in thousandths of a coin.
     */
    public static long capMilli(double worth, int capPercent) {
        return (long) Math.floor(Math.max(0, worth) * Math.max(0, capPercent) * 10.0);
    }

    /**
     * The money the crown may spend on goods of citizens now: what is left of its sum of the day, never so that the treasury falls
     * below what it keeps.
     *
     * @param funds      what the treasury holds
     * @param keep       what it never goes below (days of wages)
     * @param perDay     the sum of a day
     * @param spentToday what it spent on goods of citizens today
     */
    public static long budget(long funds, long keep, long perDay, long spentToday) {
        return Math.max(0, Math.min(Math.max(0, perDay - Math.max(0, spentToday)), Math.max(0, funds) - Math.max(0, keep)));
    }

    /**
     * Chooses what to buy: the cheapest offers for one item first, as many as needed, within the price allowed and the money.
     *
     * @param need     how many are needed
     * @param capMilli the most one may cost, in thousandths of a coin
     * @param budget   the money that may be spent
     */
    public static List<Take> choose(List<Offer> offers, long need, long capMilli, long budget) {
        List<Offer> sorted = new ArrayList<>(offers);
        sorted.sort(Comparator.comparingLong(Offer::priceMilli).thenComparingLong(Offer::shop).thenComparing(Offer::key));
        List<Take> takes = new ArrayList<>();
        long left = Math.max(0, need);
        long money = Math.max(0, budget);
        for (Offer offer : sorted) {
            if (left <= 0 || money <= 0) {
                break;
            }
            if (offer.count() <= 0 || offer.priceMilli() <= 0 || offer.priceMilli() > capMilli) {
                continue;
            }
            long count = Math.min(left, offer.count());
            count = Math.min(count, Money.share(money, ShopPricing.MILLI, offer.priceMilli()));
            long total = ShopPricing.total(offer.priceMilli(), count);
            while (count > 0 && total > money) {
                count--;
                total = ShopPricing.total(offer.priceMilli(), count);
            }
            if (count <= 0) {
                continue;
            }
            takes.add(new Take(offer.shop(), offer.key(), count, total));
            left -= count;
            money -= total;
        }
        return takes;
    }
}
