package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionRegistryTest {

    @Test
    void defaultsAreUnemployedFarmerAndTheGatherers() {
        ProfessionRegistry registry = ProfessionRegistry.withDefaults();
        assertEquals(List.of(Professions.UNEMPLOYED, Professions.FARMER, Professions.LUMBERJACK, Professions.MINER,
                Professions.FISHER, Professions.HERDER, Professions.CARRIER), registry.all());
        assertEquals(SiteKinds.WORK_POST, Professions.MINER.workplaceKind());
        assertTrue(Professions.worksAtPosts("farmer"));
        assertTrue(Professions.worksAtPosts("carrier"));
        assertFalse(Professions.worksAtPosts("unemployed"));
        assertEquals(6, Professions.postProfessions().size());
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
        assertEquals(8, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Profession("farmer", null)));
    }

    @Test
    void cyclingWrapsAround() {
        ProfessionRegistry registry = ProfessionRegistry.withDefaults();
        assertEquals(Professions.FARMER, registry.next("unemployed"));
        assertEquals(Professions.LUMBERJACK, registry.next("farmer"));
        assertEquals(Professions.UNEMPLOYED, registry.next("carrier"));
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
