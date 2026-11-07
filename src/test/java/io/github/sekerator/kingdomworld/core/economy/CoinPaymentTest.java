package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinPaymentTest {
    private final CurrencyUnits units = CurrencyUnits.DEFAULT;

    @Test
    void anExactPaymentNeedsNoChange() {
        CoinPayment.Plan plan = CoinPayment.plan(new Coins(9, 1, 0), 15, units).orElseThrow();
        assertEquals(new Coins(5, 1, 0), plan.coins());
        assertEquals(0, plan.change());
    }

    @Test
    void aLargerCoinIsBrokenWithChange() {
        CoinPayment.Plan plan = CoinPayment.plan(new Coins(3, 0, 2), 150, units).orElseThrow();
        assertEquals(new Coins(0, 0, 2), plan.coins(), "Small coins that do not help stay with the player");
        assertEquals(50, plan.change());
        CoinPayment.Plan silver = CoinPayment.plan(new Coins(4, 3, 0), 7, units).orElseThrow();
        assertEquals(new Coins(0, 1, 0), silver.coins());
        assertEquals(3, silver.change());
    }

    @Test
    void tooFewCoinsCannotPay() {
        assertEquals(Optional.empty(), CoinPayment.plan(new Coins(5, 2, 0), 26, units));
        assertEquals(Optional.empty(), CoinPayment.plan(Coins.NONE, 1, units));
    }

    @Test
    void everyPlanPaysAtLeastThePriceAndNoMoreThanItNeeds() {
        Random random = new Random(3);
        for (int i = 0; i < 5_000; i++) {
            Coins carried = new Coins(random.nextInt(30), random.nextInt(30), random.nextInt(5));
            long price = 1 + random.nextInt(800);
            Optional<CoinPayment.Plan> plan = CoinPayment.plan(carried, price, units);
            if (carried.value(units) < price) {
                assertTrue(plan.isEmpty());
                continue;
            }
            CoinPayment.Plan found = plan.orElseThrow();
            long paid = found.coins().value(units);
            assertEquals(price + found.change(), paid);
            for (Denomination denomination : Denomination.values()) {
                assertTrue(found.coins().count(denomination) <= carried.count(denomination), "Paid with coins the player does not have");
                if (found.coins().count(denomination) > 0) {
                    assertTrue(found.change() < units.value(denomination), "A coin was handed over for nothing: " + carried + " for " + price);
                }
            }
        }
    }
}
