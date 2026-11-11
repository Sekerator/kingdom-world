package io.github.sekerator.kingdomworld.core.economy;

/**
 * What citizens keep at home. A share of every wage paid is set aside for the house of the citizen; when the house is
 * looked at, the money first buys a little food from the storehouses of the kingdom (the price goes back to the
 * treasury), and the rest is laid into the container of the house as coins, up to a limit. What goes beyond the limit
 * leaves the world, as the whole wage did before. Nothing is made: every coin and every loaf in a house was paid from a
 * wage.
 *
 * @param sharePercent share of a paid wage set aside for the house, in percent; 0 keeps houses empty
 * @param coinsMax     most money a house keeps as coins, in copper coins
 * @param foodItems    items of food a house keeps at most
 * @param foodPortion  items of food a house buys at a time
 */
public record HouseholdRules(int sharePercent, long coinsMax, int foodItems, int foodPortion) {
    public static final HouseholdRules DEFAULT = new HouseholdRules(20, 300, 6, 2);

    public HouseholdRules {
        sharePercent = Math.max(0, Math.min(100, sharePercent));
        coinsMax = Math.max(0, Math.min(Money.MAX, coinsMax));
        foodItems = Math.max(0, foodItems);
        foodPortion = Math.max(0, foodPortion);
    }

    /**
     * What of a paid wage is set aside for the house of the citizen.
     */
    public long share(long paid) {
        return paid <= 0 ? 0 : Money.share(paid, sharePercent, 100);
    }

    /**
     * The most a house has set aside and not yet laid in: a full purse of coins and the price of its food, so that a house
     * nobody looks at for a long time does not gather a fortune.
     */
    public long purseMax() {
        return Money.multiplyCapped(coinsMax, 2);
    }

    /**
     * What money set aside becomes when the house is looked at.
     *
     * @param food  items of food to buy
     * @param paid  what they cost, back to the treasury
     * @param coins money laid in as coins
     * @param lost  money that leaves the world: the house is full
     */
    public record Settlement(int food, long paid, long coins, long lost) {
    }

    /**
     * Settles the money set aside for a house.
     *
     * @param purse     the money set aside
     * @param coinsHere the worth of the coins of the kingdom the house holds now
     * @param foodHere  items of food the house holds now
     * @param price     the price of one item of the food on offer; 0 or less buys none
     * @param onOffer   items of food the storehouses can spare
     */
    public Settlement settle(long purse, long coinsHere, int foodHere, long price, int onOffer) {
        if (purse <= 0) {
            return new Settlement(0, 0, 0, 0);
        }
        int food = 0;
        if (price > 0) {
            long affordable = purse / price;
            food = (int) Math.max(0, Math.min(Math.min(foodPortion, foodItems - foodHere), Math.min(onOffer, affordable)));
        }
        long paid = food * Math.max(0, price);
        long rest = purse - paid;
        long coins = Math.max(0, Math.min(rest, coinsMax - Math.max(0, coinsHere)));
        return new Settlement(food, paid, coins, rest - coins);
    }
}
