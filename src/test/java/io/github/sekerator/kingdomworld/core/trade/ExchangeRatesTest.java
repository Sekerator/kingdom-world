package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeRatesTest {
    private final TradeRules rules = TradeRules.DEFAULT;

    @Test
    void theRateIsWealthOverMoneyWithinBounds() {
        assertEquals(ExchangeRates.ONE * 2, ExchangeRates.target(20_000, 10_000, rules));
        assertEquals(ExchangeRates.ONE / 2, ExchangeRates.target(5_000, 10_000, rules));
        assertEquals(rules.maxRate(), ExchangeRates.target(Money.MAX, 1, rules), "A rich kingdom with no money is capped");
        assertEquals(rules.minRate(), ExchangeRates.target(0, 10_000, rules), "A poor kingdom is capped from below");
        // A currency with little money counts at least the minimum supply.
        assertEquals(ExchangeRates.target(1_000, rules.minSupply(), rules), ExchangeRates.target(1_000, 3, rules));
        ExchangeRates.Wealth wealth = new ExchangeRates.Wealth(100, 20, 30, 10);
        assertEquals(150 + 70, wealth.total(7));
    }

    @Test
    void ratesMoveTowardsTheirTargetStepByStep() {
        long rate = ExchangeRates.ONE;
        long target = ExchangeRates.ONE * 4;
        long previous = rate;
        for (int day = 0; day < 200; day++) {
            rate = ExchangeRates.smooth(rate, target, rules);
            assertTrue(rate >= previous && rate <= target, "Day " + day + ": " + rate);
            previous = rate;
        }
        assertEquals(target, rate, "The rate reaches its target");
        assertEquals(ExchangeRates.ONE + (3 * ExchangeRates.ONE) / 4, ExchangeRates.smooth(ExchangeRates.ONE, target, rules),
                "A quarter of the way a day");
        assertEquals(rules.maxRate(), ExchangeRates.smooth(rules.maxRate(), Long.MAX_VALUE / 4, rules));
    }

    @Test
    void exchangingNeverGivesMoreThanItIsWorth() {
        Random random = new Random(7);
        for (int i = 0; i < 2_000; i++) {
            long amount = 1 + random.nextInt(1_000_000);
            long a = rules.minRate() + random.nextInt((int) (rules.maxRate() - rules.minRate()));
            long b = rules.minRate() + random.nextInt((int) (rules.maxRate() - rules.minRate()));
            int fee = random.nextInt(100);
            long got = ExchangeRates.convert(amount, a, b, fee);
            assertTrue(ExchangeRates.worth(got, b) <= ExchangeRates.worth(amount, a), "Got more than given");
            long back = ExchangeRates.convert(got, b, a, fee);
            assertTrue(back <= amount, "A round trip made money: " + amount + " -> " + got + " -> " + back);
            long price = 1 + random.nextInt(100_000);
            long cost = ExchangeRates.priceIn(price, a, b, fee);
            assertTrue(ExchangeRates.convert(cost, b, a, fee) >= price || cost == -1,
                    "Paying " + cost + " for a price of " + price + " pays too little");
        }
        assertEquals(0, ExchangeRates.convert(0, ExchangeRates.ONE, ExchangeRates.ONE, 0));
        assertEquals(98, ExchangeRates.convert(100, ExchangeRates.ONE, ExchangeRates.ONE, 20));
        assertEquals(103, ExchangeRates.priceIn(100, ExchangeRates.ONE, ExchangeRates.ONE, 20));
    }

    /**
     * Property: whatever players exchange, every currency holds exactly as much money as before; it only moves between
     * accounts, treasuries and reserves.
     */
    @Test
    void exchangesNeverMakeOrLoseMoney() {
        Random random = new Random(42);
        for (int world = 0; world < 20; world++) {
            Economy economy = new Economy();
            List<UUID> kingdoms = new ArrayList<>();
            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID kingdom = UUID.randomUUID();
                kingdoms.add(kingdom);
                economy.open(kingdom, 0);
                economy.adminTreasury(kingdom, 1 + random.nextInt(500_000), 0);
            }
            Map<UUID, Long> targets = new HashMap<>();
            for (UUID kingdom : kingdoms) {
                targets.put(kingdom, rules.minRate() + random.nextInt((int) (rules.maxRate() - rules.minRate())));
            }
            for (int day = 0; day < 5; day++) {
                economy.updateRates(day, targets, rules);
            }
            for (int i = 0; i < 6; i++) {
                UUID player = UUID.randomUUID();
                players.add(player);
                for (UUID kingdom : kingdoms) {
                    economy.adminAccount(player, kingdom, 1 + random.nextInt(20_000));
                }
            }
            Map<UUID, Long> before = new HashMap<>();
            for (UUID kingdom : kingdoms) {
                before.put(kingdom, economy.supply(kingdom));
            }
            int done = 0;
            for (int step = 0; step < 300; step++) {
                UUID bank = kingdoms.get(random.nextInt(kingdoms.size()));
                UUID currency = kingdoms.get(random.nextInt(kingdoms.size()));
                UUID player = players.get(random.nextInt(players.size()));
                long amount = 1 + random.nextInt(8_000);
                Economy.Exchanged result = economy.exchange(bank, currency, amount, random.nextInt(100), player, player, step);
                if (result.outcome().ok()) {
                    done++;
                    assertTrue(result.got() > 0);
                } else {
                    KingdomError error = result.outcome().error();
                    assertTrue(error == KingdomError.SAME_CURRENCY || error == KingdomError.NOT_ENOUGH_MONEY
                            || error == KingdomError.TREASURY_EMPTY || error == KingdomError.TOO_LITTLE, "Refused with " + error);
                }
                for (UUID kingdom : kingdoms) {
                    assertEquals(before.get(kingdom), economy.supply(kingdom), "Money of a currency changed at step " + step);
                }
            }
            assertTrue(done > 50, "Too few exchanges worked: " + done);
        }
    }

    @Test
    void theBankPaysOnlyWhatItsTreasuryHas() {
        Economy economy = new Economy();
        UUID bank = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        economy.open(bank, 0);
        economy.open(other, 0);
        economy.adminTreasury(bank, 50, 0);
        economy.adminAccount(player, other, 1_000);
        assertEquals(Optional.of(KingdomError.TREASURY_EMPTY), economy.exchange(bank, other, 100, 0, player, player, 0).outcome().failure());
        assertEquals(1_000, economy.accounts().balance(player, other), "A refused exchange took money");
        Economy.Exchanged done = economy.exchange(bank, other, 50, 0, player, player, 0);
        assertTrue(done.outcome().ok());
        assertEquals(50, done.got());
        assertEquals(50, economy.treasury(bank).orElseThrow().reserve(other), "The given currency went into the reserves");
        assertEquals(0, economy.treasury(bank).orElseThrow().funds());
        assertEquals(Optional.of(KingdomError.SAME_CURRENCY), economy.exchange(bank, bank, 5, 0, player, player, 0).outcome().failure());
        economy.retire(other, "Other", 0, 0);
        assertEquals(0, economy.treasury(bank).orElseThrow().reserve(other), "Reserves of a dissolved kingdom are worth nothing");
        assertEquals(Optional.of(KingdomError.OLD_CURRENCY), economy.exchange(bank, other, 5, 0, player, player, 0).outcome().failure());
    }
}
