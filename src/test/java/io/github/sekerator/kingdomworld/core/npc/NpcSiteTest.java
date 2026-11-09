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
