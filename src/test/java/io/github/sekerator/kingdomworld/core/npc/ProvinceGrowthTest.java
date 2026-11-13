package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvinceGrowthTest {
    private static final ProvinceGrowth.Rules RULES = ProvinceGrowth.Rules.DEFAULT;

    private static ProvinceGrowth.Candidate candidate(boolean wild, boolean spawn, boolean player, boolean inhabited, double neutral) {
        return new ProvinceGrowth.Candidate(7, 1_600, wild, spawn, player, inhabited, neutral, 3_000);
    }

    @Test
    void aStateGrowsOnceInTwelveDaysUpToThirtyPercentOverItsPlan() {
        assertTrue(ProvinceGrowth.due(112, 100, 20_000, 20_000, RULES));
        assertFalse(ProvinceGrowth.due(111, 100, 20_000, 20_000, RULES), "twelve days pass between two provinces");
        assertFalse(ProvinceGrowth.due(200, 100, 26_000, 20_000, RULES), "at 130% of its plan it stops");
        assertTrue(ProvinceGrowth.due(200, 100, 25_999, 20_000, RULES));
        assertFalse(ProvinceGrowth.due(200, StateLand.NEVER, 100, 0, RULES), "a kingdom the plan gave nothing grows as it always did");
        assertTrue(ProvinceGrowth.due(0, StateLand.NEVER, 100, 2_000, RULES), "the first province comes at once");
    }

    @Test
    void aWildProvinceIsNotTakenNearTheSpawnOrAPlayerOrWherePlayersLivedOrWhenTheWildsRunOut() {
        assertEquals(Optional.empty(), ProvinceGrowth.refusal(candidate(true, false, false, false, 0.5), RULES));
        assertEquals(ProvinceGrowth.Refusal.NOT_WILD, ProvinceGrowth.refusal(candidate(false, false, false, false, 0.5), RULES).orElseThrow());
        assertEquals(ProvinceGrowth.Refusal.SPAWN, ProvinceGrowth.refusal(candidate(true, true, false, false, 0.5), RULES).orElseThrow());
        assertEquals(ProvinceGrowth.Refusal.PLAYER_NEAR, ProvinceGrowth.refusal(candidate(true, false, true, false, 0.5), RULES).orElseThrow());
        assertEquals(ProvinceGrowth.Refusal.INHABITED, ProvinceGrowth.refusal(candidate(true, false, false, true, 0.5), RULES).orElseThrow());
        assertEquals(ProvinceGrowth.Refusal.TOO_LITTLE_NEUTRAL, ProvinceGrowth.refusal(candidate(true, false, false, false, 0.34), RULES).orElseThrow());
        assertEquals(Optional.empty(), ProvinceGrowth.refusal(candidate(true, false, false, false, 0.35), RULES));
    }

    @Test
    void theNeutralShareCountsTheProvinceTakenAndTheNearestIsLookedAtFirst() {
        assertEquals(0.35, ProvinceGrowth.neutralAfter(36_600, 100_000, 1_600), 1e-9);
        assertEquals(0.0, ProvinceGrowth.neutralAfter(1_000, 100_000, 1_600), 1e-9);
        assertEquals(0.0, ProvinceGrowth.neutralAfter(1_000, 0, 1), 1e-9);
        ProvinceGrowth.Candidate far = new ProvinceGrowth.Candidate(1, 1_000, true, false, false, false, 0.5, 9_000);
        ProvinceGrowth.Candidate nearBig = new ProvinceGrowth.Candidate(2, 2_000, true, false, false, false, 0.5, 2_000);
        ProvinceGrowth.Candidate nearSmall = new ProvinceGrowth.Candidate(3, 1_200, true, false, false, false, 0.5, 2_000);
        assertEquals(List.of(nearSmall, nearBig, far), ProvinceGrowth.ordered(List.of(far, nearBig, nearSmall)));
    }
}
