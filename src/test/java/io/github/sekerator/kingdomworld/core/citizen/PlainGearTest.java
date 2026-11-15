package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armor workers of dangerous work take: plain pieces, worn or not; the spoils of players stay theirs.
 */
class PlainGearTest {

    @Test
    void aPieceAsTheSmithMadeItIsPlainWornOrNot() {
        assertTrue(PlainGear.isPlain(List.of(), false, false));
        assertTrue(PlainGear.isPlain(List.of("Damage"), false, false));
        assertTrue(PlainGear.isPlain(List.of("Damage", "RepairCost"), false, false), "mended on an anvil");
    }

    @Test
    void enchantedNamedOrMarkedPiecesAreSpoils() {
        assertFalse(PlainGear.isPlain(List.of("Damage", "Enchantments"), false, false));
        assertFalse(PlainGear.isPlain(List.of("display"), false, false), "a name of its own");
        assertFalse(PlainGear.isPlain(List.of("AttributeModifiers"), false, false));
        assertFalse(PlainGear.isPlain(List.of("affix_data"), false, false), "the mark of a mod");
        assertFalse(PlainGear.isPlain(List.of("Trim"), false, false));
    }

    @Test
    void piecesWithMoreAttributesOrAHigherStepAreSpoils() {
        assertFalse(PlainGear.isPlain(List.of(), true, false), "attributes added by another mod");
        assertFalse(PlainGear.isPlain(List.of(), false, true), "a lifted step lets a worker climb onto crowns");
    }
}
