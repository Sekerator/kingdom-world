package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money of a ruler: days of upkeep rather than the money it appeared with, what it promised already, a reserve for its wars,
 * hard times that do not flicker, and decisions of a turn that never spend together more than it may spare.
 */
class RulerBudgetTest {
    private static final RulerRules RULES = RulerRules.DEFAULT;

    private static RulerView kingdom(long balance, long upkeep, long income, long expenses) {
        RulerView view = new RulerView();
        view.day = 20;
        view.character = RulerCharacter.BUILDER;
        view.traits = List.of(RulerCharacter.Trait.PATIENT);
        view.population = 10;
        view.bedsUsed = 10;
        view.bedsTotal = 14;
        view.foodDays = 10;
        view.storageSlots = 108;
        view.storageUsed = 30;
        view.balance = balance;
        view.upkeep = upkeep;
        view.income = income;
        view.expenses = expenses;
        view.soldiers = 1;
        view.chunks = 50;
        view.maxChunks = 96;
        view.knownRoles = List.of("house", "warehouse", "market", "well", "workshop", "barracks", "tower", "mage_tower");
        for (RulerRules.RoleWant want : RULES.roles()) {
            view.roles.put(want.role(), want.count(0));
        }
        view.bounty = NpcLaws.of(view.character, view.traits, view.maxEntryFee, view.maxBounty).bounty();
        view.salesTaxPermille = RulerMind.salesTax(view, RULES);
        return view;
    }

    @Test
    void theReserveIsDaysOfUpkeepNotWhatWasSpent() {
        // A kingdom that bought much this week has high expenses but the same upkeep: its reserve does not swell.
        RulerView view = kingdom(100_000, 200, 300, 5_000);
        assertEquals(200L * RULES.reserveDays(), RulerMind.reserve(view, RULES));
        assertEquals(500, RulerMind.keepDays(view));
        assertFalse(RulerMind.poor(view, RULES), "a kingdom of 500 days is not poor");
    }

    @Test
    void theRunwayIsTheBalanceOverTheLoss() {
        RulerView view = kingdom(10_000, 100, 80, 180);
        assertEquals(100, RulerMind.runway(view));
        view.income = 200;
        assertEquals(Long.MAX_VALUE, RulerMind.runway(view));
    }

    @Test
    void aWarKeepsTheSoldiersPayAside() {
        RulerView peace = kingdom(50_000, 300, 300, 300);
        RulerView war = kingdom(50_000, 300, 300, 300);
        war.wars = 2;
        war.armyPayroll = 100;
        assertEquals(RulerMind.reserve(peace, RULES) + 2L * 100 * RulerMind.WAR_CHEST_DAYS, RulerMind.reserve(war, RULES));
        assertTrue(RulerMind.spare(war, RULES) < RulerMind.spare(peace, RULES));
    }

    @Test
    void whatIsPromisedIsNotSpare() {
        RulerView view = kingdom(50_000, 300, 300, 300);
        long free = RulerMind.spare(view, RULES);
        view.committed = 20_000;
        assertEquals(free - 20_000, RulerMind.spare(view, RULES));
        view.committed = 1_000_000;
        assertEquals(0, RulerMind.spare(view, RULES));
    }

    @Test
    void hardTimesDoNotFlicker() {
        // Four days of upkeep: a ruler that was well stays well, a ruler that was poor waits for some room.
        RulerView view = kingdom(400, 100, 100, 100);
        assertFalse(RulerMind.poor(view, RULES));
        view.wasPoor = true;
        assertTrue(RulerMind.poor(view, RULES));
        view.balance = 100 * RulerMind.WELL_DAYS;
        assertFalse(RulerMind.poor(view, RULES));
        // Two days: poor whatever it was.
        view.balance = 200;
        view.wasPoor = false;
        assertTrue(RulerMind.poor(view, RULES));
    }

    @Test
    void aRichRulerBuysAbroadALittleEveryDay() {
        RulerView rich = kingdom(10_000_000, 2_000, 2_000, 2_000);
        RulerView modest = kingdom(20_000, 300, 300, 300);
        assertTrue(RulerMind.rich(rich) && !RulerMind.rich(modest));
        long budget = RulerMind.importBudget(rich, RULES);
        assertTrue(budget <= RulerMind.spare(rich, RULES) / 60 + 1 && budget > 100_000, "rich " + budget);
        assertEquals(300L * 3, RulerMind.importBudget(modest, RULES), "three days of upkeep");
    }

    @Test
    void theDecisionsOfATurnNeverSpendMoreThanIsSpare() {
        RulerView view = kingdom(20_000, 100, 200, 100);
        view.bedsUsed = 14;
        long spare = RulerMind.spare(view, RULES);
        // A house and a contract on robbers, each worth two thirds of what may be spared: only the house is taken.
        long worth = spare * 2 / 3 * 1000 / view.importPermille;
        view.builds.add(new RulerView.Option("house", "kingdomworld:house", "", worth, true));
        view.camps.add(new RulerView.Camp("1", 100, 5, spare * 2 / 3, false));
        List<RulerDecision> decisions = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(10)).decisions();
        assertTrue(decisions.stream().anyMatch(d -> d.kind() == Kind.BUILD), decisions.toString());
        assertFalse(decisions.stream().anyMatch(d -> d.kind() == Kind.CONTRACT), decisions.toString());
        // With money for both, both are taken.
        view.balance = 200_000;
        decisions = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(10)).decisions();
        assertTrue(decisions.stream().anyMatch(d -> d.kind() == Kind.CONTRACT), decisions.toString());
    }

    @Test
    void aRichRulerInvestsInMoreThanItsLevelAsks() {
        RulerView rich = kingdom(10_000_000, 2_000, 2_000, 2_000);
        RulerView modest = kingdom(20_000, 300, 300, 300);
        for (RulerView view : List.of(rich, modest)) {
            for (String role : view.knownRoles) {
                view.builds.add(new RulerView.Option(role, "kingdomworld:" + role, "", 10, true));
            }
        }
        assertEquals(RulerMind.MAX_BUILDING + 1, RulerMind.buildingSlots(rich));
        assertTrue(RulerMind.wantedRoles(rich, RULES).stream().anyMatch(RulerMind.INVESTMENTS::contains), RulerMind.wantedRoles(rich, RULES).toString());
        assertTrue(RulerMind.wantedRoles(modest, RULES).isEmpty(), RulerMind.wantedRoles(modest, RULES).toString());
        List<RulerDecision> decisions = RulerMind.decide(rich, new RulerMemory(), RULES).decisions();
        assertTrue(decisions.stream().anyMatch(d -> d.kind() == Kind.BUILD), decisions.toString());
    }

    @Test
    void taxesFollowTheTreasury() {
        RulerView rich = kingdom(10_000_000, 2_000, 2_000, 2_000);
        RulerView losing = kingdom(20_000, 300, 100, 600);
        RulerView steady = kingdom(20_000, 300, 300, 300);
        for (RulerView view : List.of(rich, losing, steady)) {
            view.shops = 3;
        }
        assertTrue(RulerMind.tithe(rich, RULES) < RulerMind.tithe(steady, RULES));
        assertTrue(RulerMind.tithe(losing, RULES) > RulerMind.tithe(steady, RULES), "40 days left raise the tithe");
    }
}
