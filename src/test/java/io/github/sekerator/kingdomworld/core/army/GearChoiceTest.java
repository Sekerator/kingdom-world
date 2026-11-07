package io.github.sekerator.kingdomworld.core.army;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearChoiceTest {

    @Test
    void meleeWeaponsAreJudgedByDamagePerSecond() {
        double wooden = GearChoice.meleeScore(3, 1.6);
        double iron = GearChoice.meleeScore(5, 1.6);
        double diamond = GearChoice.meleeScore(6, 1.6);
        double diamondAxe = GearChoice.meleeScore(8, 1.0);
        double dagger = GearChoice.meleeScore(2, 4.0);
        assertTrue(wooden < iron && iron < diamond, "a better sword scores more");
        assertTrue(diamondAxe < diamond, "a slow axe hits harder but less often than a sword");
        assertTrue(dagger > wooden, "a fast weapon of a mod with little damage still counts");
        assertEquals(0.0, GearChoice.meleeScore(0, 1.6), "a gun that does not hit is no melee weapon");
        assertEquals(0.0, GearChoice.meleeScore(Double.NaN, 1.6));
        assertEquals(GearChoice.meleeScore(5, 4.0), GearChoice.meleeScore(5, 0), 1e-12, "no speed modifier is the bare hand's speed");
    }

    @Test
    void theBestPieceWinsAndOfEqualOnesTheMostWorn() {
        List<GearChoice.Candidate> candidates = List.of(
                new GearChoice.Candidate(0, GearChoice.Slot.MELEE, 9.6, 200),
                new GearChoice.Candidate(1, GearChoice.Slot.MELEE, 11.2, 1_500),
                new GearChoice.Candidate(2, GearChoice.Slot.MELEE, 11.2, 40),
                new GearChoice.Candidate(3, GearChoice.Slot.RANGED, 50.0, 10),
                new GearChoice.Candidate(4, GearChoice.Slot.MELEE, 0.0, 1));
        assertEquals(OptionalInt.of(2), GearChoice.best(candidates, GearChoice.Slot.MELEE));
        assertEquals(OptionalInt.of(3), GearChoice.best(candidates, GearChoice.Slot.RANGED));
        assertEquals(OptionalInt.empty(), GearChoice.best(candidates, GearChoice.Slot.SHIELD));
        assertTrue(GearChoice.better(11.2, 9.6));
        assertFalse(GearChoice.better(9.6, 9.6), "the same piece again is no reason to swap");
        assertTrue(GearChoice.armorScore(8, 3) > GearChoice.armorScore(8, 0));
    }

    @Test
    void theConfigOverridesAndForbidsItemsAndTags() {
        GearChoice.Overrides overrides = GearChoice.parseAll(List.of("minecraft:trident=30", "#forge:tools/knives=deny", "nonsense",
                "simplyswords:longsword=12.5", "=3", "minecraft:stick=-1"));
        assertEquals(30.0, overrides.of("minecraft:trident", List.of()).orElseThrow());
        assertEquals(GearChoice.DENIED, overrides.of("somemod:knife", List.of("forge:tools/knives")).orElseThrow());
        assertEquals(12.5, overrides.of("simplyswords:longsword", List.of("forge:tools/knives")).orElseThrow(), "the item's own entry wins");
        assertTrue(overrides.of("minecraft:stick", List.of()).isEmpty(), "negative scores are not read");
        assertTrue(overrides.of("minecraft:iron_sword", List.of("minecraft:swords")).isEmpty());
        assertTrue(GearChoice.parse("Minecraft:Bow=deny").isPresent(), "ids are read in any case");
    }
}
