package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvinceSaleTest {
    private static final ProvinceSale.Rules RULES = ProvinceSale.Rules.DEFAULT;

    private static ProvinceSale.Facts good() {
        return new ProvinceSale.Facts(true, ProvinceKind.FRONTIER, false, false, 20, 19, 1.0, -1_000, 100, 0, false, 10, true, false);
    }

    private static ProvinceSale.Refusal refusal(ProvinceSale.Facts facts) {
        return ProvinceSale.refusal(facts, RULES).orElse(null);
    }

    @Test
    void anEmptyProvinceOnTheEdgeIsForSaleAndEveryRuleRefusesOnItsOwn() {
        assertEquals(Optional.empty(), ProvinceSale.refusal(good(), RULES));
        ProvinceSale.Facts f = good();
        assertEquals(ProvinceSale.Refusal.NOT_OF_THE_WORLD, refusal(new ProvinceSale.Facts(false, f.kind(), false, false, 20, 19, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.HEART, refusal(new ProvinceSale.Facts(true, ProvinceKind.HEART, false, false, 20, 19, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.WILD, refusal(new ProvinceSale.Facts(true, ProvinceKind.WILD, false, false, 20, 19, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.NEXT_TO_HEART, refusal(new ProvinceSale.Facts(true, f.kind(), true, false, 20, 19, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.SETTLED, refusal(new ProvinceSale.Facts(true, f.kind(), false, true, 20, 19, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.AT_WAR, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, -1_000, 100, 0, true, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.ILL_WILL, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, -1_000, 100, 0, false, -21, true, false)));
        // Keeps three quarters of what it was planned to hold, and its land together around the capital.
        assertEquals(ProvinceSale.Refusal.KEEPS_TOO_LITTLE, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 14, 1, -1_000, 100, 0, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.SPLITS_THE_LAND, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 0.7, -1_000, 100, 0, false, 10, true, false)));
        // One sale in ten days, a quarter of the planned provinces in all.
        assertEquals(ProvinceSale.Refusal.TOO_SOON, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, 95, 100, 0, false, 10, true, false)));
        assertEquals(Optional.empty(), ProvinceSale.refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, 90, 100, 4, false, 10, true, false), RULES));
        assertEquals(ProvinceSale.Refusal.SOLD_ENOUGH, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, 0, 100, 5, false, 10, true, false)));
        assertEquals(ProvinceSale.Refusal.NOT_NEXT_TO_BUYER, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, -1_000, 100, 0, false, 10, false, false)));
        assertEquals(ProvinceSale.Refusal.HELD_FOR_ANOTHER, refusal(new ProvinceSale.Facts(true, f.kind(), false, false, 20, 19, 1, -1_000, 100, 0, false, 10, true, true)));
    }

    @Test
    void thePriceFollowsTheCharacterAndTheRelation() {
        List<RulerCharacter.Trait> none = List.of();
        // 1600 chunks at 3 by a builder who thinks nothing of the buyer: 1600 x 3 x 1.0 x 1.2.
        assertEquals(5_760, ProvinceSale.price(1_600, RulerCharacter.BUILDER, none, 0, RULES));
        assertEquals(Math.round(1_600 * 3 * 0.85 * 1.2), ProvinceSale.price(1_600, RulerCharacter.MERCHANT, none, 0, RULES));
        assertEquals(Math.round(1_600 * 3 * 1.4 * 1.2), ProvinceSale.price(1_600, RulerCharacter.WARRIOR, none, 0, RULES));
        assertEquals(Math.round(1_600 * 3 * 1.25 * 0.9), ProvinceSale.price(1_600, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.PROUD), 50, RULES));
        assertEquals(Math.round(1_600 * 3 * 0.9 * 0.9), ProvinceSale.price(1_600, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.GENEROUS), 80, RULES));
        assertEquals(1.05, ProvinceSale.relationFactor(25), 1e-9);
        // A small province costs at least the least price.
        assertEquals(1_500, ProvinceSale.price(100, RulerCharacter.MERCHANT, none, 60, RULES));
        // The price of a large province: from about 4000 to about 7000 by the ruler.
        long low = ProvinceSale.price(1_600, RulerCharacter.MERCHANT, none, 50, RULES);
        long high = ProvinceSale.price(1_600, RulerCharacter.WARRIOR, none, 0, RULES);
        assertTrue(low >= 3_500 && high <= 8_100, low + " " + high);
    }

    @Test
    void aMerchantSellsReadilyAndAWarriorSeldom() {
        int merchant = 0;
        int warrior = 0;
        for (long seed = 0; seed < 1_000; seed++) {
            merchant += ProvinceSale.willing(RulerCharacter.MERCHANT, 0, seed) ? 1 : 0;
            warrior += ProvinceSale.willing(RulerCharacter.WARRIOR, 0, seed) ? 1 : 0;
            assertEquals(ProvinceSale.willing(RulerCharacter.BUILDER, 10, seed), ProvinceSale.willing(RulerCharacter.BUILDER, 10, seed));
        }
        assertTrue(merchant > 800 && warrior < 350 && warrior > 150, merchant + " " + warrior);
    }

    @Test
    void theLandOfTheSellerStaysTogetherAroundItsCapital() {
        // A chain 1 - 2 - 3 - 4 with the capital in 1: selling 2 cuts 3 and 4 off.
        Map<Integer, List<Integer>> neighbours = Map.of(1, List.of(2), 2, List.of(1, 3), 3, List.of(2, 4), 4, List.of(3));
        Map<Integer, Integer> chunks = Map.of(1, 100, 2, 100, 3, 100, 4, 100);
        Set<Integer> held = Set.of(1, 2, 3, 4);
        assertEquals(1.0 / 3.0, ProvinceSale.connectedShare(held, neighbours, chunks, 1, 2), 1e-9);
        assertEquals(1.0, ProvinceSale.connectedShare(held, neighbours, chunks, 1, 4), 1e-9);
        assertEquals(0.0, ProvinceSale.connectedShare(held, neighbours, chunks, 1, 1), 1e-9);
    }
}
