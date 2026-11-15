package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shops of the kingdoms of the world: keepers only of those the army leaves, one shop always open and another after days of
 * shortage, no trading house, the tax of the ruler out of the purses of its cities the same as before, the wages of soldiers out
 * of the world.
 */
class WorldShopRulesTest {
    private static final UUID KINGDOM = new UUID(5, 5);

    @Test
    void keepersComeOnlyOfThoseTheArmyLeaves() {
        assertEquals(0, WorldShopRules.spareKeepers(5, -1), "before the first turn of the ruler its army may want them all");
        assertEquals(0, WorldShopRules.spareKeepers(5, 5), "the army takes all it wants first: as many soldiers as without shops");
        assertEquals(0, WorldShopRules.spareKeepers(3, 7));
        assertEquals(2, WorldShopRules.spareKeepers(7, 5), "the shops get the rest");
    }

    @Test
    void aCityOfTheWorldKeepsOneShopAndOpensAnotherAfterThreeShortDays() {
        assertEquals(1, WorldShopRules.openings(true, 0), "a city without a shop always opens one");
        assertEquals(0, WorldShopRules.openings(false, 0b0000011), "two short days of seven are not enough");
        assertEquals(1, WorldShopRules.openings(false, 0b1010001), "three short days of seven open one more");
        int days = 0;
        for (int i = 0; i < 7; i++) {
            days = WorldShopRules.nextShortDays(days, i < 3);
        }
        assertEquals(0b1110000, days, "only the last seven days count, the newest lowest");
        assertEquals(0b1100000, WorldShopRules.nextShortDays(days, false) & 0b1110000);
    }

    @Test
    void noTradingHouseInTheWorld() {
        List<Trade> trades = WorldShopRules.trades(TradeRegistry.builtIn().all());
        assertTrue(trades.stream().noneMatch(trade -> Trade.TRADER.equals(trade.id())));
        assertTrue(trades.stream().anyMatch(trade -> Business.GENERAL_STORE.equals(trade.id())));
    }

    @Test
    void theTaxComesOutOfThePursesAsBeforeAndSoldiersArePaidOutOfTheWorld() {
        // 80 in a hundred of 101 and 50 paid into two purses: as before, 120 of 151, the rounding taken where money is.
        long[] taken = WorldShopRules.taxFromPurses(151 * 80 / 100, new long[]{101, 50}, new long[]{101, 50}, 80);
        assertArrayEquals(new long[]{80, 40}, taken);
        assertEquals(151 * 80 / 100, taken[0] + taken[1]);
        assertArrayEquals(new long[]{30, 0}, WorldShopRules.taxFromPurses(30, new long[]{10, 10}, new long[]{40, 0}, 80),
                "never more than a purse holds");
        Economy economy = new Economy();
        economy.open(KINGDOM, 0);
        economy.adminTreasury(KINGDOM, 1_000 - economy.treasury(KINGDOM).orElseThrow().funds(), 0);
        UUID farmer = UUID.randomUUID();
        UUID soldier = UUID.randomUUID();
        List<Economy.WageClaim> claims = List.of(new Economy.WageClaim(farmer, "farmer"), new Economy.WageClaim(soldier, "warrior"));
        Economy.Payday payday = Economy.Payday.NONE;
        for (long day = 1; day <= 3 && payday == Economy.Payday.NONE; day++) {
            payday = economy.payday(KINGDOM, day, List.of(), claims, EconomyRules.DEFAULT, id -> id.equals(soldier) ? -1 : 0);
        }
        assertEquals(payday.pay().get(farmer), economy.purse(CityPurses.PurseKey.cityPurse(KINGDOM, 0)),
                "the wage of the peaceful citizen lies in the purse of its city, that of the soldier left the world");
        assertEquals(-1, payday.cityOf(soldier));
    }
}
