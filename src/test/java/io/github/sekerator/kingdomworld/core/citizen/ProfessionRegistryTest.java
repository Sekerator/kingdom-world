package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionRegistryTest {

    @Test
    void defaultsAreUnemployedFarmerTheGatherersAndTheWorkshops() {
        ProfessionRegistry registry = ProfessionRegistry.withDefaults();
        assertEquals(List.of(Professions.UNEMPLOYED, Professions.FARMER, Professions.LUMBERJACK, Professions.MINER,
                Professions.FISHER, Professions.HERDER, Professions.CARRIER, Professions.SMELTER, Professions.COOK,
                Professions.CRAFTER, Professions.SMITH, Professions.MINTER, Professions.MERCHANT, Professions.REGISTRAR,
                Professions.ADVENTURER, Professions.BUILDER, Professions.WARRIOR, Professions.ARCHER, Professions.MAGE), registry.all());
        assertEquals(SiteKinds.WORK_POST, Professions.MINER.workplaceKind());
        assertTrue(Professions.worksAtPosts("farmer"));
        assertTrue(Professions.worksAtPosts("carrier"));
        assertFalse(Professions.worksAtPosts("unemployed"));
        assertEquals(15, Professions.postProfessions().size());
        assertFalse(Professions.worksAtPosts("adventurer"), "adventurers need no workplace");
        assertFalse(Professions.worksAtPosts("warrior") || Professions.worksAtPosts("archer"), "soldiers need no workplace");
        assertTrue(Professions.isMilitary("warrior") && Professions.isMilitary("archer") && !Professions.isMilitary("adventurer"));
        assertEquals(Professions.MAGE, Professions.nextCivilian("builder"), "the citizens tab skips the soldiers");
        assertEquals(Professions.UNEMPLOYED, Professions.nextCivilian("mage"));
        assertTrue(Professions.usesStations("registrar"));
        assertTrue(Professions.usesStations("mage"));
        assertTrue(Professions.isWorkshop("smith") && Professions.isWorkshop("cook"));
        assertFalse(Professions.isWorkshop("miner"));
        assertTrue(Professions.hasAutoMode("smelter") && Professions.hasAutoMode("cook"));
        assertFalse(Professions.hasAutoMode("crafter") || Professions.hasAutoMode("smith"));
        assertFalse(Professions.UNEMPLOYED.needsWorkplace());
        assertEquals(SiteKinds.COMPOSTER, Professions.FARMER.workplaceKind());
        assertTrue(Professions.REGISTRY.isKnown("farmer"));
    }

    @Test
    void newProfessionsAreAddedWithoutChangingTheOthers() {
        ProfessionRegistry registry = ProfessionRegistry.withDefaults();
        Profession miller = new Profession("miller", "mill");
        registry.register(miller);
        assertEquals(miller, registry.byId("miller").orElseThrow());
        assertEquals(20, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Profession("farmer", null)));
    }

    @Test
    void cyclingWrapsAround() {
        ProfessionRegistry registry = ProfessionRegistry.withDefaults();
        assertEquals(Professions.FARMER, registry.next("unemployed"));
        assertEquals(Professions.LUMBERJACK, registry.next("farmer"));
        assertEquals(Professions.SMELTER, registry.next("carrier"));
        assertEquals(Professions.MINTER, registry.next("smith"));
        assertEquals(Professions.MERCHANT, registry.next("minter"));
        assertEquals(Professions.REGISTRAR, registry.next("merchant"));
        assertEquals(Professions.ADVENTURER, registry.next("registrar"));
        assertEquals(Professions.BUILDER, registry.next("adventurer"));
        assertEquals(Professions.WARRIOR, registry.next("builder"));
        assertEquals(Professions.ARCHER, registry.next("warrior"));
        assertEquals(Professions.MAGE, registry.next("archer"));
        assertEquals(Professions.UNEMPLOYED, registry.next("mage"));
        assertEquals(Professions.UNEMPLOYED, registry.next("no_such_profession"));
    }

    @Test
    void idsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new Profession("Farmer", null));
        assertThrows(IllegalArgumentException.class, () -> new Profession("", null));
        assertThrows(IllegalArgumentException.class, () -> new Profession("x".repeat(Profession.MAX_ID_LENGTH + 1), null));
        assertTrue(Profession.isValidId("stone_mason2"));
        assertFalse(Profession.isValidId("2mason"));
        assertFalse(Profession.isValidId(null));
    }
}
