package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deals of a ruler: offers judged one after another by what the ones taken before left, prices weighed against selling abroad
 * and buying abroad, regular deals by what they take in a week, and the surplus its merchants take abroad.
 */
class RulerDealsTest {
    private static final RulerRules RULES = RulerRules.DEFAULT;
    private static final UUID PARTNER = UUID.randomUUID();

    private static RulerView kingdom() {
        RulerView view = new RulerView();
        view.day = 20;
        view.character = RulerCharacter.BUILDER;
        view.traits = List.of(RulerCharacter.Trait.PATIENT);
        view.balance = 10_000;
        view.upkeep = 100;
        view.income = 150;
        view.expenses = 100;
        view.goods.put("minecraft:wheat", new RulerView.Good("minecraft:wheat", 400, 16, 2));
        view.goods.put("minecraft:iron_ingot", new RulerView.Good("minecraft:iron_ingot", 0, 16, 10));
        return view;
    }

    private static RulerView.Offer sell(long deal, int count, long price, int period) {
        return new RulerView.Offer(deal, PARTNER, "Partner", false, true, "minecraft:wheat", count, price, 2L * count, 1_000, 0, period);
    }

    private static RulerView.Offer buy(long deal, int count, long price, int period) {
        return new RulerView.Offer(deal, PARTNER, "Partner", false, false, "minecraft:iron_ingot", count, price, 10L * count, 0, 16, period);
    }

    @Test
    void aSaleOfASurplusAboveTheExportPriceIsTaken() {
        RulerView view = kingdom();
        // 64 wheat worth 128: 0.7 of the worth is below the slack but above what merchants get abroad.
        assertTrue(RulerMind.judge(sell(1, 64, 90, 0), view, new RulerMemory(), RULES).accept());
        assertFalse(RulerMind.judge(sell(1, 64, 60, 0), view, new RulerMemory(), RULES).accept(), "below the export price");
    }

    @Test
    void aNeededPurchaseCheaperThanAnImportIsTaken() {
        RulerView view = kingdom();
        // 16 ingots worth 160: 1.3 of the worth is above the slack but below what an import costs.
        assertTrue(RulerMind.judge(buy(1, 16, 208, 0), view, new RulerMemory(), RULES).accept());
        RulerMind.Verdict dear = RulerMind.judge(buy(1, 16, 250, 0), view, new RulerMemory(), RULES);
        assertEquals(RulerMind.PRICE_HIGH, dear.reason());
    }

    @Test
    void offersAreJudgedOnWhatTheOnesBeforeLeft() {
        RulerView view = kingdom();
        long spare = RulerMind.spare(view, RULES);
        // Two purchases, each within what may be spared, together beyond it: the second is declined.
        long price = spare * 2 / 3;
        view.goods.put("minecraft:iron_ingot", new RulerView.Good("minecraft:iron_ingot", 0, 10_000, price));
        for (long deal = 1; deal <= 2; deal++) {
            view.offers.add(new RulerView.Offer(deal, PARTNER, "Partner", false, false, "minecraft:iron_ingot", 1, price, price, 0, 10_000, 0));
        }
        List<RulerDecision> answers = RulerMind.judgeAll(view, new RulerMemory(), RULES);
        assertEquals(1, answers.stream().filter(a -> a.kind() == Kind.ACCEPT).count(), answers.toString());
        assertEquals(RulerMind.NO_MONEY, answers.stream().filter(a -> a.kind() == Kind.DECLINE).findFirst().orElseThrow().subject());
    }

    @Test
    void twoSalesDoNotTakeTheSameGoods() {
        RulerView view = kingdom();
        // 400 wheat, 16 wanted: one sale of 300 leaves 100, a second one of 300 would empty the stores.
        view.offers.add(sell(1, 300, 700, 0));
        view.offers.add(sell(2, 300, 700, 0));
        List<RulerDecision> answers = RulerMind.judgeAll(view, new RulerMemory(), RULES);
        assertEquals(Kind.ACCEPT, answers.get(0).kind());
        assertEquals(Kind.DECLINE, answers.get(1).kind(), answers.toString());
    }

    @Test
    void aRegularSaleIsWeighedByAWeekOfDeliveries() {
        RulerView view = kingdom();
        // 100 wheat every day would take 700 a week: more than the stores hold beyond what the kingdom wants.
        assertFalse(RulerMind.judge(sell(1, 100, 250, 1), view, new RulerMemory(), RULES).accept());
        assertTrue(RulerMind.judge(sell(1, 100, 250, 7), view, new RulerMemory(), RULES).accept());
    }

    @Test
    void aRegularDealThatNoLongerPaysIsNotKept() {
        RulerView view = kingdom();
        RulerView.Offer deal = sell(1, 100, 250, 7);
        assertTrue(RulerMind.keeps(deal, view, new RulerMemory(), RULES));
        // The fields gave less: the wheat it sells every week is now wanted at home.
        view.goods.put("minecraft:wheat", new RulerView.Good("minecraft:wheat", 90, 16, 2));
        assertFalse(RulerMind.keeps(deal, view, new RulerMemory(), RULES));
    }

    @Test
    void theSurplusItsMerchantsDoNotSellAbroadYetIsSold() {
        RulerView view = kingdom();
        view.selling.add("minecraft:wheat");
        view.population = 10;
        view.bedsTotal = 20;
        List<RulerDecision> decisions = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(10)).decisions();
        assertTrue(decisions.stream().anyMatch(d -> d.kind() == Kind.SELL && d.subject().equals("minecraft:wheat")), decisions.toString());
        view.exporting.add("minecraft:wheat");
        decisions = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(10)).decisions();
        assertFalse(decisions.stream().anyMatch(d -> d.kind() == Kind.SELL && d.subject().equals("minecraft:wheat")), decisions.toString());
    }
}
