package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSiteTest {
    private static NpcSite site() {
        return new NpcSite(1, 1200, -800, NpcLevel.MEDIUM, 42L, List.of(new NpcSite.Town(1400, -800)));
    }

    @Test
    void aPlaceFoundedAsDataCountsAsFoundedUntilItIsRemoved() {
        NpcSite site = site();
        assertFalse(site.founded(), "a planned place");
        site.setKingdomId(UUID.randomUUID());
        site.setStatus(NpcSite.Status.FOUNDED);
        assertTrue(site.founded());
        site.setStatus(NpcSite.Status.SPAWNING);
        assertTrue(site.founded(), "while its buildings are raised");
        site.setStatus(NpcSite.Status.SPAWNED);
        assertTrue(site.founded());
        site.setStatus(NpcSite.Status.REMOVED);
        assertFalse(site.founded());
    }

    @Test
    void whatTheFoundingGaveIsRememberedAndForgottenOnlyByARestart() {
        NpcSite site = site();
        assertFalse(site.given(NpcSite.MONEY) || site.given(NpcSite.LAWS));
        site.markGiven(NpcSite.MONEY);
        assertTrue(site.given(NpcSite.MONEY));
        assertFalse(site.given(NpcSite.LAWS));
        assertFalse(site.given(NpcSite.MONEY | NpcSite.LAWS), "both only once both were given");
        site.markGiven(NpcSite.LAWS);
        assertEquals(NpcSite.MONEY | NpcSite.LAWS, site.givenFlags());
        NpcSite restored = site();
        restored.restore(NpcSite.Status.FOUNDED, UUID.randomUUID(), SpawnStage.LOAD, 0, 70, "", site.givenFlags());
        assertTrue(restored.given(NpcSite.MONEY) && restored.given(NpcSite.LAWS));
        site.restart();
        assertEquals(0, site.givenFlags(), "a place spawned anew by an operator starts over");
    }

    @Test
    void aPlaceKeepsItsLayoutAndLook() {
        NpcSite old = site();
        assertEquals(NpcSite.LAYOUT_GRID, old.layout());
        assertFalse(old.districts());
        assertTrue(old.look().isEmpty());
        assertEquals("", old.wood());
        NpcState state = new NpcState();
        NpcSite site = state.addSite(10, 20, NpcLevel.LARGE, 5L, List.of(), NpcSite.LAYOUT_DISTRICTS, "brick", "minecraft:spruce");
        assertTrue(site.districts());
        assertEquals(NpcStyle.BRICK, site.look().orElseThrow());
        assertEquals("minecraft:spruce", site.wood());
        NpcSite unstyled = new NpcSite(2, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_DISTRICTS, null, null);
        assertEquals("", unstyled.style());
        assertEquals("", unstyled.wood());
        assertThrows(IllegalArgumentException.class, () -> new NpcSite(3, 0, 0, NpcLevel.SMALL, 1L, List.of(), 7, "", ""));
    }

    @Test
    void aStyledPlaceNeedsAnArchitecturalStyleAndKeepsItsGuildRank() {
        NpcState state = new NpcState();
        NpcSite site = state.addSite(10, 20, NpcLevel.LARGE, 5L, List.of(), NpcSite.LAYOUT_STYLED, "gothic", "minecraft:spruce", "ss");
        assertEquals(NpcSite.LAYOUT_STYLED, site.layout());
        assertTrue(site.styled());
        assertTrue(site.districts(), "a styled capital is a capital of districts");
        assertEquals("gothic", site.style());
        assertTrue(site.look().isEmpty(), "an architectural style is no look of the second layout");
        assertEquals("ss", site.guildRank());
        assertEquals("minecraft:spruce", site.wood());
        NpcSite noRank = new NpcSite(2, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "");
        assertEquals("", noRank.guildRank());
        assertTrue(noRank.styled());
        NpcSite districts = new NpcSite(3, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_DISTRICTS, "stone", "");
        assertTrue(districts.districts());
        assertFalse(districts.styled());
        assertEquals("", districts.guildRank());
        assertFalse(site().styled());
        assertThrows(IllegalArgumentException.class, () -> new NpcSite(4, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_STYLED, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new NpcSite(4, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_STYLED, null, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new NpcSite(4, 0, 0, NpcLevel.SMALL, 1L, List.of(), NpcSite.LAYOUT_STYLED, "brick", "", "a"));
        assertThrows(IllegalArgumentException.class, () -> new NpcSite(4, 0, 0, NpcLevel.SMALL, 1L, List.of(), 7, "classic", ""));
        assertThrows(IllegalArgumentException.class, () -> new NpcSite(4, 0, 0, NpcLevel.SMALL, 1L, List.of(), 0, "", ""));
    }

    @Test
    void thePlanKnowsTheStylesAndTheRankSSItsLivingPlacesHold() {
        NpcState state = new NpcState();
        NpcSite gothic = state.addSite(0, 0, NpcLevel.LARGE, 1L, List.of(), NpcSite.LAYOUT_STYLED, "gothic", "", "ss");
        NpcSite terem = state.addSite(5000, 0, NpcLevel.SMALL, 2L, List.of(), NpcSite.LAYOUT_STYLED, "terem", "", "b");
        state.addSite(9000, 0, NpcLevel.MEDIUM, 3L, List.of(), NpcSite.LAYOUT_DISTRICTS, "stone", "");
        assertEquals(java.util.Set.of("gothic", "terem"), state.takenStyles(), "a look of the second layout holds no style");
        assertTrue(state.holdsGuildRank("ss"));
        terem.setKingdomId(UUID.randomUUID());
        terem.setStatus(NpcSite.Status.SPAWNED);
        gothic.block("players live there");
        assertEquals(java.util.Set.of("terem"), state.takenStyles(), "a place left alone frees its style");
        assertFalse(state.holdsGuildRank("ss"), "and its rank");
        assertTrue(state.holdsGuildRank("b"));
        terem.setStatus(NpcSite.Status.REMOVED);
        assertTrue(state.takenStyles().isEmpty());
    }

    @Test
    void thePassOfThePlanIsKeptAndForgottenWithThePlan() {
        NpcState state = new NpcState();
        assertEquals(0, state.planPass());
        state.setPlanProgress(12, 3, 2, false);
        assertEquals(2, state.planPass());
        state.setPlanProgress(13, 0, false);
        assertEquals(2, state.planPass(), "the old setter keeps the pass");
        state.resetPlan();
        assertEquals(0, state.planPass());
        state.restorePlan(5, 1, 3, false, 4);
        assertEquals(3, state.planPass());
        assertEquals(4, state.nextSiteId());
    }

    @Test
    void theStatusOfAFoundedPlaceIsReadBack() {
        assertEquals(NpcSite.Status.FOUNDED, NpcSite.Status.byId("founded").orElseThrow());
    }

    @Test
    void theWayBuildingsAreRaisedIsReadFromItsName() {
        assertEquals(NpcBuildRules.Mode.BACKGROUND, NpcBuildRules.Mode.byId(" Background ").orElseThrow());
        assertEquals(NpcBuildRules.Mode.APPROACH, NpcBuildRules.Mode.byId("approach").orElseThrow());
        assertTrue(NpcBuildRules.Mode.byId("later").isEmpty());
        assertTrue(NpcBuildRules.DEFAULT.background());
        assertFalse(NpcBuildRules.DEFAULT.withMode(NpcBuildRules.Mode.APPROACH).background());
        assertThrows(IllegalArgumentException.class, () -> new NpcBuildRules(NpcBuildRules.Mode.BACKGROUND, 0, 1, 35, 16));
        assertThrows(IllegalArgumentException.class, () -> new NpcBuildRules(null, 300, 1, 35, 16));
    }
}
