package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The royal army of a kingdom of the world: its size by level, seed and ruler, larger than the guard, and its camps.
 */
class RoyalArmyTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;

    @Test
    void theArmyLiesInTheRangeOfItsLevelAndFollowsTheSeed() {
        int[][] ranges = {{50, 100}, {80, 150}, {100, 200}};
        for (NpcLevel level : NpcLevel.values()) {
            int[] range = ranges[level.ordinal()];
            for (RulerCharacter character : RulerCharacter.values()) {
                for (long seed = 0; seed < 200; seed++) {
                    int size = RoyalArmy.size(RULES, level, seed, character, List.of());
                    assertTrue(size >= range[0] && size <= range[1], level + " " + character + " " + size);
                    assertEquals(size, RoyalArmy.size(RULES, level, seed, character, List.of()), "The seed chooses one number");
                }
            }
        }
        // Different seeds give different armies.
        long distinct = java.util.stream.LongStream.range(0, 50)
                .map(seed -> RoyalArmy.size(RULES, NpcLevel.LARGE, seed, RulerCharacter.BUILDER, List.of())).distinct().count();
        assertTrue(distinct > 10, "Only " + distinct + " sizes of 50 seeds");
    }

    @Test
    void aWarriorKeepsMoreThanAMerchant() {
        for (NpcLevel level : NpcLevel.values()) {
            int least = RULES.armyLeast(level);
            int width = RULES.armyMost(level) - least;
            for (long seed = 0; seed < 100; seed++) {
                int warrior = RoyalArmy.size(RULES, level, seed, RulerCharacter.WARRIOR, List.of());
                int merchant = RoyalArmy.size(RULES, level, seed, RulerCharacter.MERCHANT, List.of());
                assertTrue(warrior >= least + width * 7 / 10, "A warrior keeps " + warrior + " in " + level);
                assertTrue(merchant <= least + (width * 3 + 9) / 10, "A merchant keeps " + merchant + " in " + level);
            }
        }
        int bold = RoyalArmy.size(RULES, NpcLevel.MEDIUM, 7, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.BOLD));
        int thrifty = RoyalArmy.size(RULES, NpcLevel.MEDIUM, 7, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.THRIFTY));
        assertTrue(bold > thrifty, "A bold ruler keeps " + bold + ", a thrifty one " + thrifty);
    }

    @Test
    void theArmyIsLargerThanTheGuardEverywhere() {
        for (NpcLevel level : NpcLevel.values()) {
            int guard = RULES.guards(level) + NpcRules.MAX_TOWNS * RULES.townGuards(level);
            for (RulerCharacter character : RulerCharacter.values()) {
                for (long seed = 0; seed < 50; seed++) {
                    int army = RoyalArmy.size(RULES, level, seed, character, List.of(RulerCharacter.Trait.THRIFTY,
                            RulerCharacter.Trait.PATIENT));
                    assertTrue(army > guard, level + ": army " + army + ", guard " + guard);
                }
            }
        }
    }

    @Test
    void theArmyBeyondTheCapitalLivesInCamps() {
        assertEquals(20, RoyalArmy.inCapital(RULES, NpcLevel.LARGE, 200));
        assertEquals(0, RoyalArmy.camps(RULES, NpcLevel.SMALL, 6));
        assertEquals(1, RoyalArmy.camps(RULES, NpcLevel.SMALL, 50));
        assertEquals(1, RoyalArmy.camps(RULES, NpcLevel.SMALL, 100));
        assertEquals(1, RoyalArmy.camps(RULES, NpcLevel.LARGE, 120));
        assertEquals(2, RoyalArmy.camps(RULES, NpcLevel.LARGE, 200));
        assertEquals(RoyalArmy.MAX_CAMPS, RoyalArmy.camps(RULES, NpcLevel.LARGE, NpcRules.MAX_ARMY));
    }
}
