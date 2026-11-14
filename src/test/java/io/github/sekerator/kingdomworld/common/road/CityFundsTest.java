package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A city pays the crown for the materials of its constructions out of its treasury and gets back what the crown could not buy:
 * the money only moves between the two treasuries, never beyond what the city may spend, and no coin is made or lost.
 */
class CityFundsTest {
    private static final CityEconomyRules RULES = CityEconomyRules.DEFAULT;
    private static final long DAY = 12;

    @Test
    void aCityPaysTheCrownForItsWorksAndGetsBackWhatWasNotBought() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        assertTrue(economy.adminTreasury(kingdom, 10_000, DAY).ok());
        assertTrue(economy.cityGrant(kingdom, 1, 500, DAY).ok());
        CityPurses.PurseKey city = CityPurses.PurseKey.cityTreasury(kingdom, 1);
        long supply = economy.supply(kingdom);
        long crown = economy.treasury(kingdom).orElseThrow().balance();

        assertTrue(CityFunds.payWorks(economy, kingdom, 1, 300, DAY, RULES).ok());
        assertEquals(200, economy.purse(city));
        assertEquals(crown + 300, economy.treasury(kingdom).orElseThrow().balance());
        assertEquals(300, economy.cityTreasury(kingdom, 1).orElseThrow().journal().expense(CityLedger.MATERIALS, DAY, DAY));

        assertTrue(CityFunds.refundWorks(economy, kingdom, 1, 120, DAY).ok());
        assertEquals(320, economy.purse(city));
        assertEquals(crown + 180, economy.treasury(kingdom).orElseThrow().balance());
        assertEquals(supply, economy.supply(kingdom), "Paying for works moved money only between the treasuries");
    }

    @Test
    void aCityNeverPaysBeyondWhatItMaySpend() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        assertTrue(economy.adminTreasury(kingdom, 1_000, DAY).ok());
        assertTrue(economy.cityGrant(kingdom, 2, 100, DAY).ok());
        long supply = economy.supply(kingdom);

        assertFalse(CityFunds.payWorks(economy, kingdom, 2, 101, DAY, RULES).ok());
        assertFalse(CityFunds.payWorks(economy, kingdom, 2, 0, DAY, RULES).ok());
        assertFalse(CityFunds.payWorks(economy, kingdom, 2, -5, DAY, RULES).ok());
        assertTrue(CityFunds.commit(economy, kingdom, 2, 60, RULES).ok());
        assertFalse(CityFunds.payWorks(economy, kingdom, 2, 50, DAY, RULES).ok(), "Money promised to a construction was spent");
        assertTrue(CityFunds.payWorks(economy, kingdom, 2, 40, DAY, RULES).ok());
        assertEquals(60, economy.purse(CityPurses.PurseKey.cityTreasury(kingdom, 2)));
        assertFalse(CityFunds.refundWorks(economy, kingdom, 2, 0, DAY).ok());
        assertEquals(supply, economy.supply(kingdom));
    }
}
