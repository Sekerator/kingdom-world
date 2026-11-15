package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tithe of a ruler of the world on what the shopkeepers of its kingdom earn: more for a greedy ruler and a poor treasury,
 * from nothing up to a half, and never a decision in a kingdom without shops of citizens.
 */
class RulerTitheTest {
    private static final RulerRules RULES = RulerRules.DEFAULT;

    private static RulerView view() {
        RulerView view = new RulerView();
        view.day = 10;
        view.character = RulerCharacter.BUILDER;
        view.traits = List.of(RulerCharacter.Trait.PATIENT);
        view.population = 10;
        view.bedsUsed = 10;
        view.bedsTotal = 14;
        view.foodDays = 10;
        view.storageSlots = 108;
        view.storageUsed = 30;
        view.balance = 3000;
        view.income = 200;
        view.expenses = 150;
        view.soldiers = 1;
        view.chunks = 50;
        view.maxChunks = 96;
        view.salesTaxPermille = RulerMind.salesTax(view, RULES);
        return view;
    }

    private static boolean decidesTithe(RulerView view) {
        RulerMind.Plan plan = RulerMind.decide(view, new RulerMemory(), new RulerRules(RULES.turnEveryDays(), 10, RULES.decreesPerDay(),
                RULES.maxChunks(), RULES.chunksPerClaim(), RULES.freeBeds(), RULES.storageFullPercent(), RULES.roles(), RULES.townPopulation(),
                RULES.maxTowns(), RULES.reserveDays(), RULES.soldiersPermille(), RULES.slackPermille(), RULES.roadDistance(), RULES.festivalMood(),
                RULES.growthChunks()));
        return plan.decisions().stream().anyMatch(decision -> decision.kind() == RulerDecision.Kind.TITHE);
    }

    @Test
    void theTitheFollowsGreedAndAPoorTreasury() {
        RulerView plain = view();
        assertEquals(100, RulerMind.tithe(plain, RULES), "a tenth for a ruler of no particular leaning");
        RulerView greedy = view();
        greedy.traits = List.of(RulerCharacter.Trait.THRIFTY, RulerCharacter.Trait.PROUD);
        assertEquals(170, RulerMind.tithe(greedy, RULES));
        RulerView generous = view();
        generous.traits = List.of(RulerCharacter.Trait.GENEROUS, RulerCharacter.Trait.JUST, RulerCharacter.Trait.GENEROUS);
        assertEquals(25, RulerMind.tithe(generous, RULES), "never below a quarter of the base");
        RulerView poor = view();
        poor.balance = 100;
        poor.income = 50;
        assertEquals(125, RulerMind.tithe(poor, RULES), "a quarter more when the treasury is poor");
        RulerView most = view();
        most.baseTithePercent = 50;
        most.traits = List.of(RulerCharacter.Trait.THRIFTY, RulerCharacter.Trait.PROUD, RulerCharacter.Trait.AMBITIOUS);
        assertEquals(RulerMind.MAX_TITHE, RulerMind.tithe(most, RULES), "at most a half");
    }

    @Test
    void theTitheIsDecidedOnlyWithShopsAndOnlyWhenTwoPointsOff() {
        RulerView none = view();
        none.tithePermille = 0;
        assertFalse(decidesTithe(none), "a kingdom without shops of citizens decides no tithe");
        RulerView shops = view();
        shops.shops = 2;
        shops.tithePermille = 0;
        assertTrue(decidesTithe(shops), "a kingdom with shops sets the tithe it wants");
        RulerView close = view();
        close.shops = 2;
        close.tithePermille = 110;
        assertFalse(decidesTithe(close), "a tithe one point off stays");
    }
}
