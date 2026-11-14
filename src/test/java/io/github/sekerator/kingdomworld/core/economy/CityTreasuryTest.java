package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The treasury of a city: the share of the crown and what the city keeps add up to its income exactly, a city without a head
 * keeps a week of its own share, a city with one no more than a month besides what it saves for, a grant is spent first and
 * never sent back by itself, grants and recalls stay within the money there is, and the loyalty a recall costs.
 */
class CityTreasuryTest {
    private static final CityEconomyRules RULES = CityEconomyRules.DEFAULT;
    private static final long DAY = 10;

    @Test
    void theShareOfTheCrownAndWhatTheCityKeepsAddUpToItsIncome() {
        for (long tax = 0; tax <= 1_000; tax += 7) {
            for (int share = 0; share <= 1000; share += 125) {
                CityTreasury.Settlement settlement = CityTreasury.settle(tax, 0, tax, tax, share, true, tax, Money.MAX / 4, RULES);
                assertEquals(tax, settlement.share() + settlement.kept(), "tax " + tax + ", share " + share);
                assertEquals(0, settlement.idle(), "a city that saves keeps the rest");
            }
        }
        CityTreasury.Settlement half = CityTreasury.settle(100, 0, 100, 100, 500, true, 50, Money.MAX / 4, RULES);
        assertEquals(50, half.share());
        assertEquals(50, half.kept());
    }

    @Test
    void aCityWithoutAHeadKeepsAWeekOfItsOwnShare() {
        // Ten a day of taxes, half of it the city's: it keeps 7 x 5 = 35 and sends the rest.
        CityTreasury.Settlement settlement = CityTreasury.settle(200, 0, 10, 10, 500, false, 0, 0, RULES);
        assertEquals(5, settlement.share());
        assertEquals(200 - 5 - 35, settlement.idle());
        CityTreasury.Settlement young = CityTreasury.settle(30, 0, 10, 10, 500, false, 0, 0, RULES);
        assertEquals(0, young.idle(), "below a week of its share nothing more goes");
        CityTreasury.Settlement all = CityTreasury.settle(200, 0, 10, 10, 1000, false, 0, 0, RULES);
        assertEquals(190, all.idle(), "with a share of the crown of 100%% the city keeps nothing");
    }

    @Test
    void aCityWithAHeadKeepsAMonthOfItsShareBesidesWhatItSavesFor() {
        long month = RULES.cityReserveDays() * 5L;
        CityTreasury.Settlement settlement = CityTreasury.settle(1_000, 0, 10, 10, 500, true, 5, 0, RULES);
        assertEquals(1_000 - 5 - month, settlement.idle());
        CityTreasury.Settlement saving = CityTreasury.settle(1_000, 0, 10, 10, 500, true, 5, 400, RULES);
        assertEquals(Math.max(0, 1_000 - 5 - month - 400), saving.idle(), "what it saves for stays");
    }

    @Test
    void whatTheCrownGrantedNeverGoesBackByItself() {
        CityTreasury.Settlement settlement = CityTreasury.settle(500, 450, 10, 10, 1000, false, 0, 0, RULES);
        assertEquals(10, settlement.share());
        assertEquals(40, settlement.idle());
        CityTreasury.Settlement only = CityTreasury.settle(450, 450, 0, 10, 1000, false, 0, 0, RULES);
        assertEquals(0, only.total(), "a treasury of nothing but a grant sends nothing");
    }

    @Test
    void aGrantIsSpentFirstAndWithinTheMoneyOfTheCrown() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.adminTreasury(kingdom, 1_000, DAY);
        assertFalse(economy.cityGrant(kingdom, 3, 1_001, DAY).ok(), "no more than the crown has");
        assertFalse(economy.cityGrant(kingdom, 3, 0, DAY).ok());
        assertTrue(economy.cityGrant(kingdom, 3, 400, DAY).ok());
        CityTreasury city = economy.cityTreasury(kingdom, 3).orElseThrow();
        assertEquals(400, city.granted());
        assertEquals(400, economy.purse(city.key()));
        // The city earns 100 of its own and spends 150: the grant goes first.
        economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(kingdom, 3), 100, LedgerCategory.COMMERCE, DAY);
        economy.purseMove(CityPurses.PurseKey.cityPurse(kingdom, 3), city.key(), 100, CityLedger.TITHE, DAY);
        economy.purseToTreasury(city.key(), 150, LedgerCategory.CITIES, CityLedger.MATERIALS, DAY);
        assertEquals(250, city.granted());
        assertEquals(350, economy.purse(city.key()));
        assertEquals(400, city.tally().get(CityStatement.Line.GRANTED));
        assertEquals(150, city.tally().get(CityStatement.Line.SPENT));
        assertEquals(100, city.tally().get(CityStatement.Line.TITHE));
    }

    @Test
    void aRecallTakesNoMoreThanTheTreasuryHoldsBesidesWhatIsPromised() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.adminTreasury(kingdom, 1_000, DAY);
        economy.cityGrant(kingdom, 1, 300, DAY);
        economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(kingdom, 1), 200, LedgerCategory.COMMERCE, DAY);
        economy.purseMove(CityPurses.PurseKey.cityPurse(kingdom, 1), CityPurses.PurseKey.cityTreasury(kingdom, 1), 200, CityLedger.TITHE, DAY);
        assertTrue(economy.cityCommit(kingdom, 1, 100));
        assertFalse(economy.cityRecall(kingdom, 1, 401, DAY).outcome().ok(), "100 of the 500 are promised");
        Economy.Recall gift = economy.cityRecall(kingdom, 1, 250, DAY);
        assertTrue(gift.outcome().ok());
        assertEquals(0, gift.beyond(), "what was granted goes back without grudge");
        CityTreasury city = economy.cityTreasury(kingdom, 1).orElseThrow();
        assertFalse(city.recalled(DAY));
        Economy.Recall more = economy.cityRecall(kingdom, 1, 150, DAY);
        assertTrue(more.outcome().ok());
        assertEquals(100, more.beyond());
        assertTrue(city.recalled(DAY) && city.recalled(DAY + CityTreasury.RECALL_DAYS - 1) && !city.recalled(DAY + CityTreasury.RECALL_DAYS));
        assertEquals(0, city.granted());
        assertEquals(100, economy.purse(city.key()));
    }

    @Test
    void theLoyaltyARecallCosts() {
        assertEquals(0, CityTreasury.loyaltyLoss(0, 10));
        assertEquals(CityTreasury.MAX_LOYALTY_LOSS, CityTreasury.loyaltyLoss(1, 0), "a city of no income loses all at once");
        assertEquals(10, CityTreasury.loyaltyLoss(70, 10), "a week of income is ten points");
        assertEquals(CityTreasury.MAX_LOYALTY_LOSS, CityTreasury.loyaltyLoss(1_000, 10));
        assertEquals(1, CityTreasury.loyaltyLoss(7, 10));
    }

    @Test
    void theAveragesCountTheLastWeekWithToday() {
        CityTreasury city = new CityTreasury(UUID.randomUUID(), 2);
        for (long day = 1; day <= 9; day++) {
            city.tally().add(CityStatement.Line.TITHE, day * 10);
            city.tally().add(CityStatement.Line.PURSE_IN, 70);
            city.seal(day, 0);
        }
        assertEquals(CityTreasury.STATEMENTS, city.statements().size());
        city.tally().add(CityStatement.Line.TITHE, 100);
        // Days 4 to 9 sealed and 100 today: (40 + 50 + ... + 90 + 100) / 7.
        assertEquals((40 + 50 + 60 + 70 + 80 + 90 + 100) / 7, city.averageTax());
        assertEquals(60, city.averageWithToday(CityStatement.Line.PURSE_IN));
    }

    @Test
    void theTaxesOfACityFollowTheKingdomUnlessSetAndAreCutToIt() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        assertTrue(economy.setCityTaxes(kingdom, 4, 200, CityTreasury.AS_KINGDOM, 1000).ok());
        assertFalse(economy.setCityTaxes(kingdom, 4, 251, -1, -1).ok(), "the sales tax is 25%% at most");
        assertFalse(economy.setCityTaxes(kingdom, 4, -2, -1, -1).ok());
        CityTreasury city = economy.cityTreasury(kingdom, 4).orElseThrow();
        assertEquals(200, city.salesTax(100));
        assertEquals(150, city.tithe(150), "as in the kingdom");
        assertEquals(1000, city.crownShare(500));
        assertTrue(economy.setKingdomCityTaxes(kingdom, 80, 60, 400).ok());
        assertEquals(80, city.salesTax(80), "a city above the new taxes of the kingdom is cut to them");
        Treasury treasury = economy.treasury(kingdom).orElseThrow();
        assertEquals(80, treasury.citySalesTax(100));
        assertEquals(400, treasury.crownShare(500));
        assertEquals(100, new Treasury(kingdom, CurrencyUnits.DEFAULT, 0).citySalesTax(100), "without a setting the config holds");
    }

    @Test
    void theTaxIncomeOfTheCitiesDoesNotDependOnTheShareOrOnRecalls() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        economy.adminTreasury(kingdom, 1_000, DAY);
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, 1);
        economy.treasuryToPurse(purse, 300, LedgerCategory.COMMERCE, DAY);
        economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, 1), 60, CityLedger.TITHE, DAY);
        economy.purseMove(purse, CityPurses.PurseKey.cityTreasury(kingdom, 1), 40, CityLedger.SALES_TAX, DAY);
        assertEquals(100, CityTaxIncome.of(economy, kingdom, DAY));
        economy.purseToTreasury(CityPurses.PurseKey.cityTreasury(kingdom, 1), 50, LedgerCategory.CITIES, CityLedger.CROWN_SHARE, DAY);
        economy.cityRecall(kingdom, 1, 50, DAY);
        assertEquals(100, CityTaxIncome.of(economy, kingdom, DAY), "the share and the recall are the same money again");
        assertEquals(0, CityTaxIncome.of(economy, kingdom, DAY + 1));
        assertEquals(List.of(), economy.purses().keysOf(UUID.randomUUID()));
    }
}
