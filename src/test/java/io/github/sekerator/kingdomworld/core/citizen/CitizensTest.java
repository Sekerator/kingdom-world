package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizensTest {
    private static final String DIM = "minecraft:overworld";

    private Citizens citizens;
    private AtomicInteger changes;
    private UUID realm;

    @BeforeEach
    void setUp() {
        citizens = new Citizens();
        changes = new AtomicInteger();
        citizens.setChangeListener(changes::incrementAndGet);
        realm = UUID.randomUUID();
    }

    private Citizen newCitizen(UUID kingdom) {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom, 42, Gender.FEMALE, 1, -20 * CitizenNeeds.TICKS_PER_DAY,
                new WorldPos(DIM, 0, 64, 0));
        citizens.add(citizen);
        return citizen;
    }

    private static WorldPos pos(int x, int z) {
        return new WorldPos(DIM, x, 64, z);
    }

    @Test
    void eachBedHousesOneCitizen() {
        Citizen anna = newCitizen(realm);
        Citizen boris = newCitizen(realm);
        WorldPos bed = pos(1, 1);
        assertFalse(citizens.assignHome(anna, bed), "unknown bed");
        citizens.addSite(bed, SiteKinds.BED, realm);
        assertTrue(citizens.assignHome(anna, bed));
        assertFalse(citizens.assignHome(boris, bed), "the bed is taken");
        assertEquals(1, citizens.sites().occupied(realm, SiteKinds.BED));

        WorldPos second = pos(30, 1);
        citizens.addSite(second, SiteKinds.BED, realm);
        assertEquals(second, citizens.sites().nearestFree(realm, SiteKinds.BED, pos(0, 0)).orElseThrow().pos());
        assertTrue(citizens.assignHome(anna, second), "moving out frees the old bed");
        assertTrue(citizens.sites().get(bed).orElseThrow().isFree());
        assertTrue(citizens.assignHome(boris, bed));
    }

    @Test
    void foreignOrBrokenBedsFreeTheirResident() {
        Citizen anna = newCitizen(realm);
        WorldPos bed = pos(1, 1);
        citizens.addSite(bed, SiteKinds.BED, realm);
        citizens.assignHome(anna, bed);

        UUID other = UUID.randomUUID();
        assertTrue(citizens.addSite(bed, SiteKinds.BED, other), "the bed now stands in another kingdom");
        assertNull(anna.home());
        assertFalse(citizens.assignHome(anna, bed), "a bed of another kingdom");

        WorldPos own = pos(2, 2);
        citizens.addSite(own, SiteKinds.BED, realm);
        citizens.assignHome(anna, own);
        citizens.removeSite(own);
        assertNull(anna.home(), "a broken bed is no home");
        assertTrue(citizens.sites().get(own).isEmpty());
    }

    @Test
    void workplacesFollowTheProfession() {
        Citizen anna = newCitizen(realm);
        WorldPos composter = pos(5, 5);
        citizens.addSite(composter, SiteKinds.COMPOSTER, realm);
        assertFalse(citizens.assignWorkplace(anna, composter), "the unemployed do not work");
        citizens.setProfession(anna, Professions.FARMER.id());
        assertTrue(citizens.assignWorkplace(anna, composter));
        assertEquals(composter, anna.workplace());
        citizens.setProfession(anna, Professions.UNEMPLOYED.id());
        assertNull(anna.workplace());
        assertTrue(citizens.sites().get(composter).orElseThrow().isFree());
    }

    @Test
    void deathFreesPlacesAndWritesTheChronicle() {
        Citizen anna = newCitizen(realm);
        WorldPos bed = pos(1, 1);
        citizens.addSite(bed, SiteKinds.BED, realm);
        citizens.assignHome(anna, bed);
        DeathRecord record = citizens.die(anna.id(), CauseOfDeath.MONSTER, "", 48_000L, pos(9, 9)).orElseThrow();

        assertEquals(CitizenState.DEAD, anna.state());
        assertTrue(citizens.get(anna.id()).isEmpty());
        assertTrue(citizens.sites().get(bed).orElseThrow().isFree());
        assertEquals(List.of(record), citizens.registry().chronicle(realm));
        assertEquals(CauseOfDeath.MONSTER, record.cause());
        assertEquals(3, record.day());
        assertEquals(1, citizens.registry().deathsSince(realm, 48_000L));
        assertEquals(0, citizens.registry().deathsSince(realm, 48_001L));
        assertTrue(citizens.die(anna.id(), CauseOfDeath.OTHER, "", 0L, null).isEmpty(), "dies only once");
    }

    @Test
    void chronicleKeepsTheNewestEntries() {
        for (int i = 0; i < CitizenRegistry.CHRONICLE_SIZE + 5; i++) {
            Citizen citizen = newCitizen(realm);
            citizens.die(citizen.id(), CauseOfDeath.FALL, "", i, null);
        }
        List<DeathRecord> chronicle = citizens.registry().chronicle(realm);
        assertEquals(CitizenRegistry.CHRONICLE_SIZE, chronicle.size());
        assertEquals(CitizenRegistry.CHRONICLE_SIZE + 4, chronicle.get(0).gameTime(), "newest first");
    }

    @Test
    void healthDecidesTheState() {
        Citizen anna = newCitizen(realm);
        anna.setHealth(9.0f, 0.5f);
        assertEquals(CitizenState.WOUNDED, anna.state());
        anna.setHealth(10.0f, 0.5f);
        assertEquals(CitizenState.HEALTHY, anna.state());
        int before = changes.get();
        anna.setHealth(10.0f, 0.5f);
        assertEquals(before, changes.get(), "an unchanged value is no change");
        anna.setFood(25.0f);
        assertEquals(CitizenNeeds.MAX_FOOD, anna.food());
        assertEquals(20, anna.ageDays(0L));
    }

    @Test
    void kingdomsKeepTheirOwnPeople() {
        Citizen anna = newCitizen(realm);
        UUID other = UUID.randomUUID();
        Citizen boris = newCitizen(other);
        assertEquals(List.of(anna), citizens.registry().citizensOf(realm));
        assertEquals(List.of(boris), citizens.registry().citizensOf(other));
        assertEquals(List.of(anna), citizens.registry().findByIdPrefix(anna.shortId().toUpperCase()));
        citizens.addSite(pos(1, 1), SiteKinds.BED, realm);
        citizens.removeKingdom(realm);
        assertTrue(citizens.registry().citizensOf(realm).isEmpty());
        assertEquals(0, citizens.sites().count(realm, SiteKinds.BED));
        assertEquals(1, citizens.registry().size());
        assertThrows(IllegalArgumentException.class, () -> citizens.add(boris));
    }

    @Test
    void restoreDropsHomesThatAreNoPlaces() {
        UUID id = UUID.randomUUID();
        Citizen saved = new Citizen(id, realm, 1, Gender.MALE, 0, 0L, pos(0, 0));
        saved.restore(15.0f, CitizenState.HEALTHY, "farmer", pos(1, 1), pos(3, 3), 12.0f, 60, 7, 4, "minecraft:wheat", 0.5);
        Citizen lost = new Citizen(UUID.randomUUID(), realm, 2, Gender.MALE, 0, 0L, pos(0, 0));
        lost.restore(20.0f, CitizenState.HEALTHY, "unemployed", pos(2, 2), null, 20.0f, 50, 0, 0, "", 0.0);
        citizens.restore(realm, List.of(new Site(pos(1, 1), SiteKinds.BED, realm, null),
                        new Site(pos(3, 3), SiteKinds.COMPOSTER, realm, null)), List.of(saved, lost), 77L,
                List.of(new DeathRecord(UUID.randomUUID(), 3, Gender.FEMALE, "farmer", CauseOfDeath.FIRE, "", 5L, pos(4, 4))));

        assertEquals(id, citizens.sites().get(pos(1, 1)).orElseThrow().occupant());
        assertEquals(id, citizens.sites().get(pos(3, 3)).orElseThrow().occupant());
        assertNull(lost.home(), "a home without a bed is dropped");
        assertEquals(77L, citizens.registry().lastArrival(realm));
        assertEquals(1, citizens.registry().chronicle(realm).size());
        assertEquals("minecraft:wheat", saved.workProduct());
        assertThrows(IllegalArgumentException.class, () -> citizens.restore(realm, List.of(), List.of(saved), 0L, List.of()));
    }
}
