package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MintMetalsTest {
    private static final String COPPER = "#forge:ingots/copper";
    private static final String IRON = "#forge:ingots/iron";
    private static final String SILVER = "#forge:ingots/silver";
    private static final String GOLD = "#forge:ingots/gold";
    private static final List<String> METALS = List.of(COPPER, IRON, SILVER, GOLD);

    private static Function<String, MintMetals.Setting> settings(Map<String, MintMetals.Setting> changed) {
        return metal -> changed.getOrDefault(metal, MintMetals.defaultFor(metal));
    }

    @Test
    void ironIsOffByDefaultAndEveryOtherMetalOn() {
        assertFalse(MintMetals.defaultFor(IRON).strike());
        assertFalse(MintMetals.defaultFor("minecraft:iron_ingot").strike());
        assertFalse(MintMetals.defaultFor("#c:iron_ingots").strike());
        for (String metal : List.of(COPPER, SILVER, GOLD, "minecraft:gold_ingot", "#forge:ingots/wrought_iron_plate")) {
            assertTrue(MintMetals.defaultFor(metal).strike(), metal);
            assertEquals(0, MintMetals.defaultFor(metal).keep(), metal);
        }
    }

    @Test
    void whatIsUsableLeavesWhatStays() {
        assertEquals(10, MintMetals.usable(new MintMetals.Setting(true, 0), 10));
        assertEquals(4, MintMetals.usable(new MintMetals.Setting(true, 6), 10));
        assertEquals(0, MintMetals.usable(new MintMetals.Setting(true, 10), 10));
        assertEquals(0, MintMetals.usable(new MintMetals.Setting(true, 64), 10));
        assertEquals(0, MintMetals.usable(new MintMetals.Setting(false, 0), 10));
        assertEquals(0, MintMetals.usable(new MintMetals.Setting(true, 0), -3));
        assertThrows(IllegalArgumentException.class, () -> new MintMetals.Setting(true, MintMetals.MAX_KEEP + 1));
        assertThrows(IllegalArgumentException.class, () -> new MintMetals.Setting(true, -1));
    }

    @Test
    void ironIsLeftWhileCopperAndGoldAreStruck() {
        List<MintMetals.Candidate> candidates = List.of(new MintMetals.Candidate(COPPER, Denomination.COPPER, 4),
                new MintMetals.Candidate(IRON, Denomination.SILVER, 30), new MintMetals.Candidate(GOLD, Denomination.GOLD, 2));
        MintMetals.Choice choice = MintMetals.choose(candidates, METALS, settings(Map.of()));
        assertEquals(Map.of(Denomination.COPPER, 0, Denomination.GOLD, 2), choice.first());
        assertEquals(List.of(Denomination.COPPER, Denomination.GOLD), choice.available(candidates));
        assertFalse(choice.held());
    }

    @Test
    void onlyIronLeftHoldsTheMint() {
        List<MintMetals.Candidate> candidates = List.of(new MintMetals.Candidate(IRON, Denomination.SILVER, 30));
        MintMetals.Choice choice = MintMetals.choose(candidates, METALS, settings(Map.of()));
        assertTrue(choice.first().isEmpty());
        assertTrue(choice.held());
    }

    @Test
    void nothingInTheStorehousesIsNoMetalNotAHeldMint() {
        MintMetals.Choice choice = MintMetals.choose(List.of(), METALS, settings(Map.of()));
        assertTrue(choice.first().isEmpty());
        assertFalse(choice.held());
        // Metal of which nothing is free (a keep order holds it) is no reason of the settings either.
        choice = MintMetals.choose(List.of(new MintMetals.Candidate(COPPER, Denomination.COPPER, 0)), METALS, settings(Map.of()));
        assertFalse(choice.held());
    }

    @Test
    void everyMetalOffHoldsTheMintEvenWithEmptyStorehouses() {
        Map<String, MintMetals.Setting> off = new HashMap<>();
        for (String metal : METALS) {
            off.put(metal, new MintMetals.Setting(false, 0));
        }
        MintMetals.Choice choice = MintMetals.choose(List.of(), METALS, settings(off));
        assertTrue(choice.first().isEmpty());
        assertTrue(choice.held());
    }

    @Test
    void metalBelowWhatStaysIsLeftAndTheNextOfItsCoinTaken() {
        // Copper keeps 64 and there are 40: the mint is held on copper. Silver ingots of a mod still give silver coins.
        Map<String, MintMetals.Setting> changed = Map.of(COPPER, new MintMetals.Setting(true, 64), IRON, new MintMetals.Setting(true, 25));
        List<MintMetals.Candidate> candidates = List.of(new MintMetals.Candidate(COPPER, Denomination.COPPER, 40),
                new MintMetals.Candidate(IRON, Denomination.SILVER, 25), new MintMetals.Candidate(SILVER, Denomination.SILVER, 3));
        MintMetals.Choice choice = MintMetals.choose(candidates, METALS, settings(changed));
        assertEquals(Map.of(Denomination.SILVER, 2), choice.first());
        assertFalse(choice.held());
        // Without the silver of the mod nothing is left to strike, and only because of the settings.
        choice = MintMetals.choose(candidates.subList(0, 2), METALS, settings(changed));
        assertTrue(choice.first().isEmpty());
        assertTrue(choice.held());
        // One iron ingot over what stays: it is struck.
        choice = MintMetals.choose(List.of(new MintMetals.Candidate(IRON, Denomination.SILVER, 26)), METALS, settings(changed));
        assertEquals(Map.of(Denomination.SILVER, 0), choice.first());
    }

    @Test
    void theTreasuryKeepsOnlyWhatTheKingChanged() {
        Treasury treasury = new Treasury(UUID.randomUUID(), CurrencyUnits.DEFAULT, 0);
        int[] changes = {0};
        treasury.setChangeListener(() -> changes[0]++);
        assertFalse(treasury.mintMetal(IRON).strike());
        assertTrue(treasury.mintMetal(COPPER).strike());
        assertTrue(treasury.mintMetals().isEmpty());

        assertTrue(treasury.setMintMetal(IRON, new MintMetals.Setting(true, 0)));
        assertTrue(treasury.mintMetal(IRON).strike());
        assertEquals(Map.of(IRON, new MintMetals.Setting(true, 0)), treasury.mintMetals());
        assertEquals(1, changes[0]);
        // Back to the default: nothing is kept for it.
        assertTrue(treasury.setMintMetal(IRON, new MintMetals.Setting(false, 0)));
        assertTrue(treasury.mintMetals().isEmpty());
        assertEquals(2, changes[0]);
        // The same again changes nothing.
        assertTrue(treasury.setMintMetal(IRON, new MintMetals.Setting(false, 0)));
        assertEquals(2, changes[0]);
    }

    @Test
    void theEconomyChecksTheMetalAndTellsAnUnchangedSetting() {
        Economy economy = new Economy();
        UUID kingdom = UUID.randomUUID();
        economy.open(kingdom, 0);
        assertTrue(economy.setMintMetal(kingdom, COPPER, new MintMetals.Setting(true, 32)).ok());
        assertFalse(economy.setMintMetal(kingdom, COPPER, new MintMetals.Setting(true, 32)).ok());
        assertFalse(economy.setMintMetal(kingdom, "with space", new MintMetals.Setting(true, 1)).ok());
        assertFalse(economy.setMintMetal(kingdom, "", new MintMetals.Setting(true, 1)).ok());
        assertFalse(economy.setMintMetal(UUID.randomUUID(), COPPER, new MintMetals.Setting(false, 0)).ok());
        assertEquals(32, economy.treasury(kingdom).orElseThrow().mintMetal(COPPER).keep());
    }
}
