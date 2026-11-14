package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money of the cities only moves: whatever is done to the purses, the treasuries of the cities, the tills, the treasury of
 * the kingdom and the accounts, the money of the currency on the books changes by exactly what was said to come in or leave
 * the world, and nothing goes below 0.
 */
class CityPursesTest {
    private static final long DAY = 5;

    @Test
    void tenThousandRandomMovementsLoseAndMakeNoCoin() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.open(other, 0);
        UUID player = UUID.randomUUID();
        assertTrue(economy.adminTreasury(kingdom, 1_000_000, DAY).ok());
        assertTrue(economy.adminAccount(player, kingdom, 50_000).ok());
        List<CityPurses.PurseKey> keys = new ArrayList<>();
        for (long city = 0; city < 4; city++) {
            keys.add(CityPurses.PurseKey.cityPurse(kingdom, city));
            keys.add(CityPurses.PurseKey.cityTreasury(kingdom, city));
            keys.add(CityPurses.PurseKey.till(kingdom, 100 + city));
        }
        Random random = new Random(42);
        long expected = economy.supply(kingdom);
        long held = 0;
        for (int i = 0; i < 10_000; i++) {
            CityPurses.PurseKey from = keys.get(random.nextInt(keys.size()));
            CityPurses.PurseKey to = keys.get(random.nextInt(keys.size()));
            long amount = random.nextInt(2_000) - 100;
            switch (random.nextInt(9)) {
                case 0 -> economy.purseMove(from, to, amount, CityLedger.values()[random.nextInt(CityLedger.values().length)], DAY);
                case 1 -> economy.treasuryToPurse(to, amount, LedgerCategory.COMMERCE, CityLedger.OTHER, DAY);
                case 2 -> economy.purseToTreasury(from, amount, LedgerCategory.CITIES, CityLedger.CROWN_SHARE, DAY);
                case 3 -> {
                    long before = economy.purse(from);
                    if (economy.purseLeaves(from, 0, amount, CityPurses.Outflow.AFAR, DAY).ok()) {
                        expected -= Math.max(0, amount);
                        assertEquals(before - Math.max(0, amount), economy.purse(from));
                    }
                }
                case 4 -> {
                    long taken = economy.purseHold(from, amount);
                    held += taken;
                    expected -= taken;
                }
                case 5 -> {
                    long back = Math.min(held, Math.max(0, amount));
                    long took = economy.purseRelease(to, back, DAY);
                    held -= took;
                    expected += took;
                }
                case 6 -> {
                    long price = Math.max(0, amount);
                    economy.playerPurchase(player, CityPurses.PurseKey.till(kingdom, 100 + random.nextInt(4)),
                            CityPurses.PurseKey.cityTreasury(kingdom, random.nextInt(4)), price, price / 10, DAY);
                }
                case 7 -> economy.cityGrant(kingdom, random.nextInt(4), amount, DAY);
                default -> economy.cityRecall(kingdom, random.nextInt(4), amount, DAY);
            }
            for (Map.Entry<CityPurses.PurseKey, Long> purse : economy.purses().all().entrySet()) {
                assertTrue(purse.getValue() > 0, "A purse below or at 0 is kept: " + purse);
            }
            assertTrue(economy.treasury(kingdom).orElseThrow().funds() >= 0);
            for (CityTreasury city : economy.cityTreasuries()) {
                assertTrue(city.granted() <= economy.purse(city.key()), "More granted than the city holds");
            }
            assertEquals(expected, economy.supply(kingdom), "Step " + i + " made or lost money");
        }
        assertEquals(0, economy.supply(other), "Nothing reached the other currency");
    }

    @Test
    void movementsBetweenKingdomsAndBeyondTheMoneyAreRefusedWithoutAChange() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.open(other, 0);
        economy.adminTreasury(kingdom, 100, DAY);
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, 0);
        assertTrue(economy.treasuryToPurse(purse, 60, LedgerCategory.COMMERCE, DAY).ok());
        assertFalse(economy.purseMove(purse, CityPurses.PurseKey.cityPurse(other, 0), 10).ok(), "Two currencies");
        assertFalse(economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, 0), 61).ok(), "More than the purse holds");
        assertFalse(economy.treasuryToPurse(purse, 41, LedgerCategory.COMMERCE, DAY).ok(), "More than the treasury holds");
        assertFalse(economy.purseLeaves(purse, 0, -1, CityPurses.Outflow.UPKEEP, DAY).ok());
        assertEquals(60, economy.purse(purse));
        assertEquals(40, economy.treasury(kingdom).orElseThrow().funds());
        assertEquals(100, economy.supply(kingdom));
    }

    @Test
    void wagesStayInThePurseOfTheCityOfEachCitizen() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.adminTreasury(kingdom, 1_000, DAY);
        UUID farmer = UUID.randomUUID();
        UUID miner = UUID.randomUUID();
        List<Economy.WageClaim> claims = List.of(new Economy.WageClaim(farmer, "farmer"), new Economy.WageClaim(miner, "miner"));
        economy.payday(kingdom, 1, List.of(), claims, EconomyRules.DEFAULT, citizen -> -1);
        long before = economy.supply(kingdom);
        Economy.Payday payday = economy.payday(kingdom, 2, List.of(), claims, EconomyRules.DEFAULT, citizen -> citizen.equals(farmer) ? 0 : 3);
        long farmerWage = EconomyRules.DEFAULT.defaultWage("farmer");
        long minerWage = EconomyRules.DEFAULT.defaultWage("miner");
        assertEquals(farmerWage, economy.purse(CityPurses.PurseKey.cityPurse(kingdom, 0)));
        assertEquals(minerWage, economy.purse(CityPurses.PurseKey.cityPurse(kingdom, 3)));
        assertEquals(0, payday.cityOf(farmer));
        assertEquals(3, payday.cityOf(miner));
        assertEquals(before, economy.supply(kingdom), "The wages only moved into the cities");
        assertEquals(farmerWage, economy.cityTreasury(kingdom, 0).orElseThrow().tally().get(CityStatement.Line.CROWN_WAGES));
        Economy.Payday away = economy.payday(kingdom, 3, List.of(), claims, EconomyRules.DEFAULT, citizen -> -1);
        assertEquals(-1, away.cityOf(farmer));
        assertEquals(before - farmerWage - minerWage, economy.supply(kingdom), "A city of -1 lets the wage leave the world as before");
    }

    @Test
    void sweepingAndSeizingKeepEveryCoin() {
        Economy economy = new Economy();
        UUID loser = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        economy.open(loser, 0);
        economy.open(winner, 0);
        economy.adminTreasury(loser, 1_000, DAY);
        economy.cityGrant(loser, 2, 300, DAY);
        economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(loser, 2), 200, LedgerCategory.COMMERCE, DAY);
        economy.treasuryToPurse(CityPurses.PurseKey.till(loser, 9), 100, LedgerCategory.COMMERCE, DAY);
        long seized = economy.seizePurses(List.of(CityPurses.PurseKey.cityTreasury(loser, 2), CityPurses.PurseKey.cityPurse(loser, 2)),
                winner, DAY);
        assertEquals(500, seized);
        assertEquals(500, economy.treasury(winner).orElseThrow().reserve(loser), "Into the reserves of the currency of the loser");
        assertEquals(1_000, economy.supply(loser), "The money of the loser only changed hands");
        assertEquals(100, economy.sweepPurses(loser, DAY));
        assertEquals(500, economy.treasury(loser).orElseThrow().funds());
        assertTrue(economy.purses().keysOf(loser).isEmpty());
    }

    @Test
    void anUnknownKindOfPurseGoesToTheTreasuryOfItsKingdom() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.restoreUnknownPurse(kingdom, 77);
        assertEquals(77, economy.treasury(kingdom).orElseThrow().funds());
    }
}
