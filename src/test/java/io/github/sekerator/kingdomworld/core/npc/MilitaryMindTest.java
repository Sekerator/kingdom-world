package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The military mind of a ruler: a reserve that does not melt, an army by wealth and danger, wars by reckoning and fights
 * weighed by the forces at hand.
 */
class MilitaryMindTest {
    @Test
    void twoSiegesDoNotMeltTheReserve() {
        // Six squads: the first siege takes four, the second finds none to take.
        int first = MilitaryMind.available(6, 0, 0);
        assertEquals(4, first);
        assertEquals(0, MilitaryMind.available(6 - first, first, 0));
        // In danger one squad more stays home.
        assertEquals(3, MilitaryMind.available(6, 0, 2));
        // Squads that came back may go again.
        assertEquals(4, MilitaryMind.available(6, 0, 0));
        assertEquals(0, MilitaryMind.available(0, 0, 0));
    }

    @Test
    void aRichRulerInDangerKeepsMoreSoldiers() {
        assertEquals(100, MilitaryMind.keep(100, 30, 0, 300));
        assertTrue(MilitaryMind.keep(100, 800, 0, 300) > MilitaryMind.keep(100, 150, 0, 300));
        assertTrue(MilitaryMind.keep(100, 30, 3, 300) > 100);
        assertEquals(150, MilitaryMind.keep(100, 10_000, 3, 150), "never above the most");
        assertEquals(0, MilitaryMind.keep(0, 10_000, 3, 150));
    }

    @Test
    void aPoorOrLosingTreasuryTakesNoRecruits() {
        assertTrue(MilitaryMind.recruits(200, Long.MAX_VALUE));
        assertFalse(MilitaryMind.recruits(10, Long.MAX_VALUE));
        assertFalse(MilitaryMind.recruits(200, 20));
    }

    @Test
    void dangersAddUp() {
        assertEquals(0, MilitaryMind.threat(0, 0, 0, 0, 0));
        assertEquals(1, MilitaryMind.threat(1, 0, 0, 0, 0));
        assertEquals(2, MilitaryMind.threat(0, 0, 2, 1, 0));
        assertEquals(MilitaryMind.MAX_THREAT, MilitaryMind.threat(1, 3, 2, 5, 3.0));
    }

    @Test
    void aWarThatCannotBeWonOrPaidIsNotStarted() {
        MilitaryMind.WarCase good = new MilitaryMind.WarCase(300, 100, 1.5, 400, 200, 500, 200_000, 50_000, 0);
        assertEquals("", MilitaryMind.refuseWar(good));
        assertEquals("weak", MilitaryMind.refuseWar(new MilitaryMind.WarCase(140, 100, 1.5, 400, 200, 500, 200_000, 50_000, 0)));
        assertEquals("poor", MilitaryMind.refuseWar(new MilitaryMind.WarCase(300, 100, 1.5, 10, 200, 500, 5_000, 50_000, 0)));
        assertEquals("busy", MilitaryMind.refuseWar(new MilitaryMind.WarCase(300, 100, 1.5, 400, 200, 500, 200_000, 50_000, 1)));
        assertEquals("worthless", MilitaryMind.refuseWar(new MilitaryMind.WarCase(300, 100, 1.5, 400, 200, 500, 200_000, 100, 0)));
        assertEquals("weak", MilitaryMind.refuseWar(new MilitaryMind.WarCase(0, 0, 1.5, 400, 200, 500, 200_000, 50_000, 0)));
    }

    @Test
    void aFightIsWeighedByTheForcesAtHand() {
        assertEquals(MilitaryMind.Field.RETREAT, MilitaryMind.assess(50, 100));
        assertEquals(MilitaryMind.Field.HOLD, MilitaryMind.assess(100, 100));
        assertEquals(MilitaryMind.Field.PRESS, MilitaryMind.assess(200, 100));
        assertEquals(MilitaryMind.Field.PRESS, MilitaryMind.assess(10, 0));
        assertTrue(MilitaryMind.suesForPeace(2, 100));
        assertTrue(MilitaryMind.suesForPeace(0, 2));
        assertFalse(MilitaryMind.suesForPeace(1, 100));
    }
}
