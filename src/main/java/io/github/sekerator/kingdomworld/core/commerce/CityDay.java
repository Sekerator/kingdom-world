package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The pure part of the day of a city: how the townspeople spend their purse in the shops of their city, what is left of a
 * shop at the end of its day, and how many new shops open.
 *
 * <p>Of the purse a share leaves the world for goods from afar; the rest is split by the basket among the kinds of goods, the
 * fractions of the split carried to the next day kind by kind. Each kind buys whole items, the cheapest for their worth first,
 * as many of one offer at a time as its money, the stock of the shop and the strength of the shop allow, worked out by
 * division and not item by item, so that a purse of thousands of coins costs no more than one of ten. What is not spent stays
 * in the purse.
 */
public final class CityDay {
    private CityDay() {
    }

    /**
     * An item a shop offers.
     *
     * @param worth      the worth of one, for the strength of the shop and the order of the offers
     * @param priceMilli its price without the tax, in thousandths of a coin
     * @param costMilli  what one cost the shop
     */
    public record Offer(long shop, String item, int category, double worth, long priceMilli, long costMilli, long stock) {
    }

    /**
     * Items the townspeople bought of a shop.
     *
     * @param total what they paid without the tax
     * @param tax   the tax they paid on top
     */
    public record Sale(long shop, String item, int category, long count, long total, long tax, long costMilli, double worth) {
        public long cost() {
            return ShopPricing.total(costMilli, count);
        }
    }

    /**
     * How the purse splits among the kinds of goods.
     *
     * @param budgets what each kind may spend
     * @param carry   the fractions carried to the next day, in hundredths of a coin
     */
    public record Budgets(long[] budgets, long[] carry) {
        public long total() {
            long sum = 0;
            for (long budget : budgets) {
                sum = Money.addCapped(sum, budget);
            }
            return sum;
        }
    }

    /**
     * What a day of spending did.
     *
     * @param afar   what left the world for goods from afar
     * @param wanted what the townspeople wanted to spend in the shops
     * @param spent  what they spent there, tax included
     */
    public record Spending(long afar, List<Sale> sales, long[] carry, long wanted, long spent) {
        public long unspent() {
            return Math.max(0, wanted - spent);
        }
    }

    /**
     * What leaves the world of a purse for goods from afar.
     */
    public static long afar(long purse, int afarPercent) {
        return Money.share(Math.max(0, purse), Math.max(0, Math.min(100, afarPercent)), 100);
    }

    /**
     * Splits a purse among the kinds of goods by the basket. Without luxury (a village) its share goes to household goods.
     */
    public static Budgets budgets(long purse, long[] carry, int[] basket, boolean luxury) {
        int kinds = GoodsCategory.values().length;
        int[] shares = new int[kinds];
        for (int i = 0; i < kinds; i++) {
            shares[i] = i < basket.length ? Math.max(0, basket[i]) : 0;
        }
        if (!luxury) {
            shares[GoodsCategory.HOUSEHOLD.ordinal()] += shares[GoodsCategory.LUXURY.ordinal()];
            shares[GoodsCategory.LUXURY.ordinal()] = 0;
        }
        long[] budgets = new long[kinds];
        long[] next = new long[kinds];
        for (int i = 0; i < kinds; i++) {
            long before = carry != null && i < carry.length ? Math.max(0, Math.min(99, carry[i])) : 0;
            java.math.BigInteger raw = java.math.BigInteger.valueOf(Math.max(0, purse)).multiply(java.math.BigInteger.valueOf(shares[i]))
                    .add(java.math.BigInteger.valueOf(before));
            java.math.BigInteger[] split = raw.divideAndRemainder(java.math.BigInteger.valueOf(100));
            budgets[i] = split[0].min(java.math.BigInteger.valueOf(Money.MAX)).longValue();
            next[i] = split[1].longValue();
        }
        return new Budgets(budgets, next);
    }

    /**
     * One kind of goods spends its budget: the offers cheapest for their worth first, whole items, by division.
     *
     * @param capacity the worth each shop can still sell today; it is lowered by what is sold
     * @return the sales
     */
    public static List<Sale> spendKind(long budget, List<Offer> offers, Map<Long, Double> capacity, int taxPermille) {
        List<Offer> sorted = new ArrayList<>(offers);
        sorted.sort(Comparator.comparingDouble((Offer offer) -> offer.priceMilli() / Math.max(1e-6, offer.worth()))
                .thenComparingLong(Offer::shop).thenComparing(Offer::item));
        List<Sale> sales = new ArrayList<>();
        long left = Math.max(0, budget);
        for (Offer offer : sorted) {
            if (left <= 0) {
                break;
            }
            if (offer.stock() <= 0 || offer.priceMilli() <= 0) {
                continue;
            }
            double room = capacity.getOrDefault(offer.shop(), 0.0);
            long byStrength = offer.worth() <= 0 ? offer.stock() : (long) Math.floor(room / offer.worth() + 1e-9);
            long unitWithTax = ShopPricing.withTaxMilli(offer.priceMilli(), taxPermille);
            long byMoney = Money.share(left, ShopPricing.MILLI, Math.max(1, unitWithTax));
            long count = Math.min(offer.stock(), Math.min(byStrength, byMoney));
            long total = ShopPricing.total(offer.priceMilli(), count);
            long tax = ShopPricing.tax(total, taxPermille);
            while (count > 0 && total + tax > left) {
                count--;
                total = ShopPricing.total(offer.priceMilli(), count);
                tax = ShopPricing.tax(total, taxPermille);
            }
            if (count <= 0) {
                continue;
            }
            left -= total + tax;
            capacity.put(offer.shop(), Math.max(0.0, room - count * Math.max(0.0, offer.worth())));
            sales.add(new Sale(offer.shop(), offer.item(), offer.category(), count, total, tax, offer.costMilli(), offer.worth()));
        }
        return sales;
    }

    /**
     * A whole day of spending at once, for checks and the budget of the cities.
     */
    public static Spending spend(long purse, long[] carry, int[] basket, int afarPercent, boolean luxury, List<Offer> offers,
                                 Map<Long, Double> capacity, int taxPermille) {
        long afar = afar(purse, afarPercent);
        Budgets budgets = budgets(purse, carry, basket, luxury);
        List<Sale> sales = new ArrayList<>();
        long spent = 0;
        for (int kind = 0; kind < GoodsCategory.values().length; kind++) {
            List<Offer> ofKind = new ArrayList<>();
            for (Offer offer : offers) {
                if (offer.category() == kind) {
                    ofKind.add(offer);
                }
            }
            for (Sale sale : spendKind(budgets.budgets()[kind], ofKind, capacity, taxPermille)) {
                sales.add(sale);
                spent += sale.total() + sale.tax();
            }
        }
        return new Spending(afar, sales, budgets.carry(), budgets.total(), spent);
    }

    // ---------------------------------------------------------------- the end of the day of a shop

    /**
     * What is left of the day of a shop.
     *
     * @param upkeep   what its upkeep takes out of the world
     * @param debtPaid what it pays back the crown
     * @param profit   what it earned for its keepers, paid on the next payday
     */
    public record Result(long upkeep, long debtPaid, long profit) {
    }

    /**
     * The end of the day of a shop: its upkeep leaves the world, and what its till holds beyond the working money (two days of
     * purchases) goes first to the debt to the crown and the rest is profit for its keepers, paid on the next payday. A young shop
     * builds its working money before it pays back or pays out.
     *
     * @param till           what its till holds
     * @param takings        what it sold today
     * @param workingCapital what it keeps for buying goods
     * @param profitDue      what it owes its keepers already
     */
    public static Result result(long till, long takings, long debt, long workingCapital, long profitDue, int upkeepPercent) {
        long upkeep = Math.min(Math.max(0, till), Money.share(Math.max(0, takings), Math.max(0, upkeepPercent), 100));
        long free = Math.max(0, till - upkeep - Math.max(0, profitDue));
        long excess = Math.max(0, free - Math.max(0, workingCapital));
        long debtPaid = Math.min(Math.max(0, debt), excess);
        long profit = excess - debtPaid;
        return new Result(upkeep, debtPaid, profit);
    }

    /**
     * How many new shops open in a city today.
     *
     * @param anyShops    whether the city has shops already
     * @param unspent     what its townspeople did not find to spend yesterday
     * @param crownShort  whether yesterday the shops of the city could not get goods of the crown
     */
    public static int openings(boolean anyShops, long unspent, long fairIncome, long turnoverPerWorker, boolean crownShort) {
        if (crownShort) {
            return 0;
        }
        if (anyShops && unspent <= Money.multiplyCapped(2, fairIncome)) {
            return 0;
        }
        long perShop = Math.max(1, Money.multiplyCapped(2, turnoverPerWorker));
        return (int) Math.max(1, Math.min(1_000, Math.max(0, unspent) / perShop));
    }

    /**
     * How much of a kind of goods a shop keeps in stock: the days of stock of what it sold of the kind a day lately, or one
     * basket of its strength while it sold none of it (new, or without goods of the kind for a while), so that a kind the crown
     * has again is tried.
     *
     * @param averageSold   the worth of the goods of the kind it sold a day lately, negative with no days written
     * @param basketPercent the share of the kind in the basket of the townspeople, in percent
     */
    public static long stockTarget(long averageSold, double strength, int basketPercent, int stockDays) {
        if (averageSold <= 0) {
            return (long) (Math.max(0.0, strength) * basketPercent / 100.0);
        }
        return Money.multiplyCapped(stockDays, averageSold);
    }

    /**
     * What the trade of a shop earned on a day: its takings less what the goods sold cost it and its upkeep. While a shop builds
     * up its working money and pays back its debt it pays out less, but its trade feeds its keepers all the same.
     */
    public static long earned(long takings, long soldCost, long upkeep) {
        return Math.max(0, takings - soldCost - upkeep);
    }

    /**
     * Whether a shopkeeper earned too little: less than half of the usual.
     */
    public static boolean poor(long earnedPerKeeper, long usual) {
        return earnedPerKeeper * 2 < usual;
    }
}
