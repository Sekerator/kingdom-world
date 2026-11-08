package io.github.sekerator.kingdomworld.core.balance;

import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sixty game days of the treasuries of typical kingdoms with the rules of a new config: none goes bankrupt without a reason,
 * none grows without end, everybody who works is paid, a war with mercenaries costs what it should, and a tribute or a ransom
 * is a matter of days of income.
 */
class KingdomBudgetTest {
    private static final int DAYS = 60;
    /** The usual wage of soldiers of the config of the army. */
    private static final long SOLDIER_WAGE = 15;

    private static KingdomBudget.Rules rules() {
        EconomyRules economy = EconomyRules.DEFAULT;
        return new KingdomBudget.Rules(economy, TradeRules.DEFAULT, NobilityRules.DEFAULT, SOLDIER_WAGE, economy.defaultWage("merchant"), 200);
    }

    @Test
    void typicalTreasuriesNeitherGoBankruptNorGrowWithoutEnd() {
        StringBuilder table = new StringBuilder("\nkingdom | income/day | expense/day | lowest | after 60 days\n");
        for (KingdomBudget.Profile profile : KingdomBudget.typical()) {
            KingdomBudget.Result result = KingdomBudget.run(profile, rules(), DAYS);
            table.append(String.format(Locale.ROOT, "%s | %d | %d | %d | %d%n", profile.name(), result.dailyIncome(), result.dailyExpense(),
                    result.lowest(), result.last()));
            boolean atWar = profile.mercenaries() > 0;
            if (!atWar) {
                assertFalse(result.bankrupt(), profile.name() + " goes bankrupt in peace: " + result.lowest());
                assertTrue(result.dailyIncome() > result.dailyExpense(), profile.name() + " loses money in peace");
                // Growth stays within the income: no treasury runs away (less than a thousand days of expenses in sixty days).
                assertTrue(result.last() - profile.funds() < 1_000L * Math.max(1, result.dailyExpense()), profile.name() + " grows without end");
            }
        }
        System.out.println(table);
    }

    @Test
    void mercenariesMakeAWarDearButNotRuinous() {
        KingdomBudget.Profile war = KingdomBudget.typical().stream().filter(p -> p.mercenaries() > 0).findFirst().orElseThrow();
        KingdomBudget.Result withBand = KingdomBudget.run(war, rules(), DAYS);
        KingdomBudget.Profile peace = new KingdomBudget.Profile(war.name(), war.workers(), war.subjects(), war.itemWorth(), 0, 0, 0,
                war.nobleShare(), war.spies(), 0, war.funds());
        KingdomBudget.Result withoutBand = KingdomBudget.run(peace, rules(), DAYS);
        assertFalse(withoutBand.bankrupt(), "the army alone ruins a kingdom: " + withoutBand.lowest());
        assertTrue(withBand.lowest() < withoutBand.lowest(), "a band of mercenaries costs nothing");
        // A band of a lieutenant is paid from savings for a war of about a week (the longest war is 7 days).
        long weekOfBand = 7 * war.mercenaries();
        assertTrue(war.funds() + 7 * (withoutBand.dailyIncome() - withoutBand.dailyExpense()) >= weekOfBand,
                "a week of a band does not fit the savings of a warrior of the world");
    }

    @Test
    void everyWorkerIsPaidAndSoldiersCostMoreThanFarmers() {
        Map<String, Long> wages = new HashMap<>(EconomyRules.DEFAULT.defaultWages());
        for (String profession : new String[]{"farmer", "miner", "merchant", "builder", "innkeeper", "mage", "registrar"}) {
            assertTrue(wages.getOrDefault(profession, 0L) > 0, profession + " works for nothing");
        }
        assertTrue(SOLDIER_WAGE > wages.get("farmer"), "a soldier costs no more than a farmer");
    }

    @Test
    void tributesAndRansomsAreAMatterOfDays() {
        KingdomBudget.Profile large = KingdomBudget.typical().get(1);
        KingdomBudget.Result result = KingdomBudget.run(large, rules(), DAYS);
        long tributeOfADay = result.dailyIncome() * 200 / 1_000;
        assertTrue(tributeOfADay > 0 && tributeOfADay < result.dailyIncome() / 2, "a tribute takes too much or nothing: " + tributeOfADay);
        long ransom = NobilityRules.DEFAULT.ransom();
        assertTrue(ransom >= result.dailyIncome() / 4 && ransom <= 3 * result.dailyIncome(), "a ransom is not a matter of days: " + ransom
                + " against " + result.dailyIncome());
    }
}
