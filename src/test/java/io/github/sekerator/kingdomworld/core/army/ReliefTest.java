package io.github.sekerator.kingdomworld.core.army;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The steps of the answer to an alarm: who comes when the guard is hard pressed.
 */
class ReliefTest {
    private static final ForceRules RULES = ForceRules.DEFAULT;

    private static Relief.Step step(int losses, long ticks, boolean viceOut, boolean captainOut, boolean armyViceOut) {
        return Relief.step(new Relief.Facts(12, losses, ticks, viceOut, captainOut, armyViceOut), RULES);
    }

    @Test
    void theGuardAnswersAloneWhileItHolds() {
        assertEquals(Relief.Step.GUARDS, step(0, 0, false, false, false));
        assertEquals(Relief.Step.GUARDS, step(1, 399, false, false, false));
        // An alarm for a theft alone is no fight, however long it lasts.
        assertEquals(Relief.Step.GUARDS, step(0, 0, false, false, false));
    }

    @Test
    void lossesCallTheNextStep() {
        assertEquals(Relief.Step.GUARD_VICE, step(2, 0, false, false, false));
        assertEquals(Relief.Step.GUARD_VICE, step(3, 0, false, false, false));
        assertEquals(Relief.Step.GUARD_CAPTAIN, step(4, 0, false, false, false));
        assertEquals(Relief.Step.GUARD_CAPTAIN, step(5, 0, false, false, false));
        assertEquals(Relief.Step.ARMY, step(6, 0, false, false, false));
        assertEquals(Relief.Step.ARMY, step(9, 0, false, false, false));
        assertEquals(Relief.Step.ARMY_CAPTAIN, step(10, 0, false, false, false));
    }

    @Test
    void aFightThatDragsOnCallsTheNextStep() {
        assertEquals(Relief.Step.GUARD_VICE, step(0, 400, false, false, false));
        assertEquals(Relief.Step.GUARD_CAPTAIN, step(0, 800, false, false, false));
        assertEquals(Relief.Step.ARMY, step(0, 1_200, false, false, false));
        // Time alone never calls the captain of the army: that takes losses.
        assertEquals(Relief.Step.ARMY, step(0, 100_000, false, false, false));
    }

    @Test
    void anOfficerOutOfTheFightCallsTheNextOne() {
        assertEquals(Relief.Step.GUARD_CAPTAIN, step(0, 0, true, false, false));
        assertEquals(Relief.Step.ARMY, step(0, 0, false, true, false));
        assertEquals(Relief.Step.ARMY_CAPTAIN, step(0, 0, false, false, true));
    }

    @Test
    void aKingdomWithoutAGuardSendsItsArmyAtOnce() {
        assertEquals(Relief.Step.ARMY, Relief.step(new Relief.Facts(0, 0, 0, false, false, false), RULES));
    }

    @Test
    void theThresholdsComeFromTheRules() {
        ForceRules.Builder builder = new ForceRules.Builder();
        builder.viceLosses = 1;
        builder.armyTicks = 100;
        ForceRules quick = builder.build();
        assertEquals(Relief.Step.GUARD_VICE, Relief.step(new Relief.Facts(5, 1, 0, false, false, false), quick));
        assertEquals(Relief.Step.ARMY, Relief.step(new Relief.Facts(5, 0, 100, false, false, false), quick));
    }

    @Test
    void everySoldierAnswersAtItsStep() {
        // The fighters of the guard always; its officers one after the other.
        for (Relief.Step step : Relief.Step.values()) {
            assertTrue(Relief.answers(step, Branch.GUARD, Post.FIGHTER, false));
        }
        assertFalse(Relief.answers(Relief.Step.GUARDS, Branch.GUARD, Post.VICE_CAPTAIN, false));
        assertTrue(Relief.answers(Relief.Step.GUARD_VICE, Branch.GUARD, Post.VICE_CAPTAIN, false));
        assertFalse(Relief.answers(Relief.Step.GUARD_VICE, Branch.GUARD, Post.CAPTAIN, false));
        assertTrue(Relief.answers(Relief.Step.GUARD_CAPTAIN, Branch.GUARD, Post.CAPTAIN, false));
        // The army stays in reserve until its step; then its vice-captain leads a part of it out, the captain the rest.
        for (Post post : Post.values()) {
            assertFalse(Relief.answers(Relief.Step.GUARD_CAPTAIN, Branch.ARMY, post, true));
        }
        assertTrue(Relief.answers(Relief.Step.ARMY, Branch.ARMY, Post.VICE_CAPTAIN, false));
        assertTrue(Relief.answers(Relief.Step.ARMY, Branch.ARMY, Post.FIGHTER, true));
        assertFalse(Relief.answers(Relief.Step.ARMY, Branch.ARMY, Post.FIGHTER, false));
        assertFalse(Relief.answers(Relief.Step.ARMY, Branch.ARMY, Post.CAPTAIN, false));
        assertTrue(Relief.answers(Relief.Step.ARMY_CAPTAIN, Branch.ARMY, Post.FIGHTER, false));
        assertTrue(Relief.answers(Relief.Step.ARMY_CAPTAIN, Branch.ARMY, Post.CAPTAIN, false));
        // An officer of the guard who came stays when the army comes.
        assertTrue(Relief.answers(Relief.Step.ARMY_CAPTAIN, Branch.GUARD, Post.CAPTAIN, false));
    }

    @Test
    void halfOfTheArmyComesOutFirst() {
        assertEquals(0, Relief.firstWave(0, RULES));
        assertEquals(1, Relief.firstWave(1, RULES));
        assertEquals(3, Relief.firstWave(5, RULES));
        assertEquals(9, Relief.firstWave(18, RULES));
    }

    @Test
    void aStepReachedIsKept() {
        assertEquals(Relief.Step.ARMY, Relief.Step.ARMY.max(Relief.Step.GUARD_VICE));
        assertEquals(Relief.Step.ARMY, Relief.Step.GUARDS.max(Relief.Step.ARMY));
        assertEquals(Relief.Step.GUARDS, Relief.Step.GUARDS.max(null));
    }
}
