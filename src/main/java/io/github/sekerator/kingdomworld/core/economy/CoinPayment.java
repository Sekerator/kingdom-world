package io.github.sekerator.kingdomworld.core.economy;

import java.util.Optional;

/**
 * Paying a price with the coins a player carries: which coins to hand over and what change comes back. The coins are
 * worth at least the price and as little more as the coins allow; the change is less than the largest coin handed over.
 */
public final class CoinPayment {

    /**
     * Coins to hand over and the change for them.
     */
    public record Plan(Coins coins, long change) {
    }

    private CoinPayment() {
    }

    /**
     * Plans to pay a price from the coins carried.
     *
     * @return the plan, or empty if the coins are worth less than the price
     */
    public static Optional<Plan> plan(Coins carried, long price, CurrencyUnits units) {
        if (price <= 0 || !Money.isAmount(price)) {
            return price == 0 ? Optional.of(new Plan(Coins.NONE, 0)) : Optional.empty();
        }
        if (carried.value(units) < price) {
            return Optional.empty();
        }
        // As much as possible without paying more, the largest coins first.
        long rest = price;
        long[] taken = new long[Denomination.values().length];
        for (Denomination denomination : Denomination.largestFirst()) {
            long value = units.value(denomination);
            long count = Math.min(carried.count(denomination), rest / value);
            taken[denomination.ordinal()] = count;
            rest -= count * value;
        }
        if (rest > 0) {
            // One more coin of the smallest kind left that covers the rest; smaller ones are all spent by now.
            for (Denomination denomination : Denomination.values()) {
                long value = units.value(denomination);
                if (carried.count(denomination) > taken[denomination.ordinal()] && value >= rest) {
                    taken[denomination.ordinal()]++;
                    rest -= value;
                    break;
                }
            }
            // Smaller coins that are no longer needed go back.
            for (Denomination denomination : Denomination.values()) {
                long value = units.value(denomination);
                while (taken[denomination.ordinal()] > 0 && -rest >= value) {
                    taken[denomination.ordinal()]--;
                    rest += value;
                }
            }
        }
        if (rest > 0) {
            return Optional.empty();
        }
        Coins coins = new Coins(taken[Denomination.COPPER.ordinal()], taken[Denomination.SILVER.ordinal()], taken[Denomination.GOLD.ordinal()]);
        return Optional.of(new Plan(coins, -rest));
    }
}
