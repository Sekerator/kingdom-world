package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcStyleTest {
    @Test
    void theStyleOfAPlaceIsTheSameForTheSameSeedAndSuitsItsClimate() {
        for (NpcStyle.Climate climate : NpcStyle.Climate.values()) {
            for (long seed = -20; seed < 20; seed++) {
                for (int ordinal = 0; ordinal < 10; ordinal++) {
                    NpcStyle style = NpcStyle.pick(climate, seed * 1_000_003L, ordinal);
                    assertEquals(style, NpcStyle.pick(climate, seed * 1_000_003L, ordinal));
                    assertTrue(NpcStyle.of(climate).contains(style), climate + " " + style);
                    assertNotEquals(style, NpcStyle.pick(climate, seed * 1_000_003L, ordinal + 1), "neighbours in the plan differ");
                }
            }
        }
    }

    @Test
    void placesInTurnGetEveryStyleOfTheirClimate() {
        for (long seed = 0; seed < 10; seed++) {
            Set<NpcStyle> seen = new HashSet<>();
            for (int ordinal = 0; ordinal < 8; ordinal++) {
                seen.add(NpcStyle.pick(NpcStyle.Climate.TEMPERATE, seed, ordinal));
            }
            assertEquals(new HashSet<>(NpcStyle.of(NpcStyle.Climate.TEMPERATE)), seen);
        }
    }

    @Test
    void theClimateIsReadFromTheBiome() {
        assertEquals(NpcStyle.Climate.DRY, NpcStyle.climate("minecraft:desert", Set.of()));
        assertEquals(NpcStyle.Climate.COLD, NpcStyle.climate("minecraft:snowy_taiga", Set.of()));
        assertEquals(NpcStyle.Climate.LUSH, NpcStyle.climate("minecraft:jungle", Set.of()));
        assertEquals(NpcStyle.Climate.TEMPERATE, NpcStyle.climate("minecraft:plains", Set.of()));
        assertEquals(NpcStyle.Climate.COLD, NpcStyle.climate("somemod:pine_hills", Set.of("minecraft:is_taiga")));
    }

    @Test
    void stylesAreReadBackByTheirIds() {
        for (NpcStyle style : NpcStyle.values()) {
            assertEquals(style, NpcStyle.byId(style.id()).orElseThrow());
            assertTrue(!style.wallBlock().isEmpty());
        }
        assertTrue(NpcStyle.byId("").isEmpty());
        assertTrue(NpcStyle.byId("marble").isEmpty());
    }
}
