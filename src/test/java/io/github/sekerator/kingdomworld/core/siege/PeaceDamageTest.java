package io.github.sekerator.kingdomworld.core.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blocks damaged in peace next to blocks broken in a war: what is written down, what is paid for and what comes back.
 */
class PeaceDamageTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private final UUID kingdom = UUID.randomUUID();

    @Test
    void aBlockBrokenInPeaceIsWrittenDownAsItStoodAndCostsNothing() {
        WarDamage damage = new WarDamage();
        assertTrue(damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 10, "{Name:\"minecraft:gravel\"}", "", 50)));
        WarDamage.Entry entry = damage.at(kingdom, OVERWORLD, 10).orElseThrow();
        assertTrue(entry.free());
        assertEquals(0, entry.war());
        assertEquals("{Name:\"minecraft:gravel\"}", entry.state());
        assertEquals("", entry.item());
        assertEquals(1, damage.countFree(kingdom));
        assertTrue(damage.cost(kingdom).isEmpty(), "Nothing is taken from the storehouses for it");
        assertEquals(0, damage.value(kingdom, item -> 100));
    }

    @Test
    void onlyTheFirstStateOfAPlaceIsKeptSoBreakingAgainChangesNothing() {
        WarDamage damage = new WarDamage();
        assertTrue(damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 10, "stone", "", 50)));
        assertFalse(damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 10, "dirt", "", 60)),
                "What somebody put there since and broke again is not what the kingdom had");
        assertEquals("stone", damage.at(kingdom, OVERWORLD, 10).orElseThrow().state());
        assertEquals(1, damage.count(kingdom));
        assertTrue(damage.mended(kingdom, OVERWORLD, 10));
        assertFalse(damage.contains(kingdom, OVERWORLD, 10));
        assertEquals(0, damage.countFree(kingdom));
        // Once it is back, breaking it again is written down again.
        assertTrue(damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 10, "stone", "", 90)));
    }

    @Test
    void aBlockAStrangerPlacedIsRememberedWithWhatItCovered() {
        WarDamage damage = new WarDamage();
        // On free ground: the place held air before.
        assertFalse(damage.placedOver(kingdom, OVERWORLD, 20, "tnt"), "Nothing is recorded there yet");
        damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 20, "air", "cobblestone", 10));
        assertEquals("cobblestone", damage.at(kingdom, OVERWORLD, 20).orElseThrow().placed());
        // Into the hole of a broken block: the record keeps the block of the city and learns what stands there now.
        damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 21, "oak_planks", "", 11));
        assertTrue(damage.placedOver(kingdom, OVERWORLD, 21, "dirt"));
        WarDamage.Entry filled = damage.at(kingdom, OVERWORLD, 21).orElseThrow();
        assertEquals("oak_planks", filled.state());
        assertEquals("dirt", filled.placed());
        assertTrue(filled.free());
        assertEquals(2, damage.count(kingdom));
    }

    @Test
    void damageOfWarAndOfPeaceStandSideBySide() {
        WarDamage damage = new WarDamage();
        damage.record(new WarDamage.Entry(7, kingdom, OVERWORLD, 1, "stone_bricks", "minecraft:stone_bricks", 5, 0));
        damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, 2, "glass", "", 1));
        assertEquals(2, damage.count(kingdom));
        assertEquals(1, damage.countFree(kingdom));
        assertFalse(damage.at(kingdom, OVERWORLD, 1).orElseThrow().free());
        assertEquals(1, damage.cost(kingdom).get("minecraft:stone_bricks"));
        // A city that changes hands takes both kinds with it, as they are.
        UUID winner = UUID.randomUUID();
        damage.move(kingdom, winner, entry -> true);
        assertEquals(0, damage.count(kingdom));
        assertEquals(1, damage.countFree(winner));
        assertTrue(damage.at(winner, OVERWORLD, 2).orElseThrow().free());
        assertFalse(damage.at(winner, OVERWORLD, 1).orElseThrow().free());
    }

    @Test
    void aKingdomKeepsManyRecordsAndDropsTheOldestBeyondThat() {
        WarDamage damage = new WarDamage();
        assertTrue(WarDamage.MAX_PER_KINGDOM >= 16_000, "Enough for a city square torn up block by block");
        for (int i = 0; i < WarDamage.MAX_PER_KINGDOM + 5; i++) {
            damage.record(WarDamage.Entry.inPeace(kingdom, OVERWORLD, i, "s", "", i));
        }
        assertEquals(WarDamage.MAX_PER_KINGDOM, damage.count(kingdom));
        assertFalse(damage.contains(kingdom, OVERWORLD, 0));
        assertTrue(damage.contains(kingdom, OVERWORLD, WarDamage.MAX_PER_KINGDOM + 4));
    }
}
