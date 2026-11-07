package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCompositionTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;
    /** The thresholds of cities by default: a town takes 12 citizens and 6 buildings, a large city 40 and 20, walls 24 blocks. */
    private static final int TOWN_PEOPLE = 12;
    private static final int TOWN_BUILDINGS = 6;
    private static final int CITY_PEOPLE = 40;
    private static final int CITY_BUILDINGS = 20;
    private static final int WALL = 24;

    private static NpcComposition of(NpcLevel level, long seed) {
        return NpcComposition.of(level, seed, RULES, TOWN_PEOPLE, TOWN_BUILDINGS, CITY_PEOPLE, CITY_BUILDINGS, WALL);
    }

    private static int capitalBeds(NpcComposition composition) {
        int beds = 0;
        for (NpcComposition.Plot plot : composition.buildings()) {
            beds += NpcComposition.beds(plot.template());
        }
        return beds;
    }

    @Test
    void aSmallKingdomIsAVillageBehindAPalisade() {
        NpcComposition small = of(NpcLevel.SMALL, 1L);
        assertTrue(capitalBeds(small) < TOWN_PEOPLE, "a village: " + capitalBeds(small));
        assertFalse(small.stoneWall());
        assertEquals("path", small.roadClass());
        assertEquals(1, small.towns());
        for (String role : List.of(NpcComposition.MARKET, NpcComposition.STOREHOUSE, NpcComposition.WELL, NpcComposition.WORKSHOP,
                NpcComposition.BARRACKS, NpcComposition.WATCHTOWER)) {
            assertTrue(small.buildingsOf(role) > 0, role);
        }
        assertEquals(0, small.buildingsOf(NpcComposition.MAGE_TOWER));
        assertTrue(small.reach() + 3 <= WALL, "the buildings stay inside the wall: " + small.reach());
    }

    @Test
    void aMediumKingdomIsATownAndALargeOneALargeCity() {
        NpcComposition medium = of(NpcLevel.MEDIUM, 2L);
        assertTrue(capitalBeds(medium) >= TOWN_PEOPLE && medium.buildings().size() >= TOWN_BUILDINGS);
        assertTrue(capitalBeds(medium) < CITY_PEOPLE || medium.buildings().size() < CITY_BUILDINGS);
        assertTrue(medium.stoneWall());
        assertTrue(medium.reach() + 3 <= WALL * 3 / 2);
        NpcComposition large = of(NpcLevel.LARGE, 3L);
        assertTrue(capitalBeds(large) >= CITY_PEOPLE, "beds " + capitalBeds(large));
        assertTrue(large.buildings().size() >= CITY_BUILDINGS, "buildings " + large.buildings().size());
        assertEquals(1, large.buildingsOf(NpcComposition.MAGE_TOWER));
        assertEquals("paved", large.roadClass());
        assertEquals(3, large.towns());
        assertTrue(large.reach() + 3 <= WALL * 2);
    }

    @Test
    void everyoneHasAHomeAndTheWorkFitsThePeople() {
        for (NpcLevel level : NpcLevel.values()) {
            for (long seed = 0; seed < 20; seed++) {
                NpcComposition composition = of(level, seed);
                int townBeds = 0;
                for (String building : NpcComposition.townBuildings(composition.townHouses())) {
                    townBeds += NpcComposition.beds(building);
                }
                assertEquals(capitalBeds(composition) + townBeds * composition.towns(), composition.citizens());
                int workers = composition.soldiers();
                for (int count : composition.jobs().values()) {
                    workers += count;
                }
                assertTrue(workers <= composition.citizens(), level + ": " + workers + " workers for " + composition.citizens());
                assertEquals(RULES.soldiers(level), composition.soldiers());
                assertTrue(composition.fields() >= 1);
            }
        }
    }

    @Test
    void plotsAreTakenOnceAndFrontsLookAtTheThrone() {
        NpcComposition large = of(NpcLevel.LARGE, 9L);
        Set<String> cells = new HashSet<>();
        for (NpcComposition.Plot plot : large.buildings()) {
            assertTrue(cells.add(plot.gx() + "," + plot.gz()), "two buildings on " + plot);
            assertFalse(plot.gx() == 0 && plot.gz() == 0, "the square of the throne stays free");
            assertTrue(Math.max(Math.abs(plot.gx()), Math.abs(plot.gz())) <= large.rings());
            assertEquals(NpcComposition.facing(plot.gx(), plot.gz()), plot.facing());
        }
        // Watchtowers stand on the corners of the outer ring.
        for (NpcComposition.Plot plot : large.buildings()) {
            if (plot.template().equals(NpcComposition.WATCHTOWER)) {
                assertEquals(large.rings(), Math.abs(plot.gx()));
                assertEquals(large.rings(), Math.abs(plot.gz()));
            }
        }
        assertEquals(0, NpcComposition.facing(0, -1), "north of the throne looks south");
        assertEquals(2, NpcComposition.facing(0, 1));
        assertEquals(1, NpcComposition.facing(1, 0), "east of the throne looks west");
        assertEquals(3, NpcComposition.facing(-1, 0));
    }

    @Test
    void namesAreUniqueAndInBothAlphabets() {
        Set<String> taken = new HashSet<>();
        for (long seed = 0; seed < NpcNames.kingdomNames() + 20; seed++) {
            NpcNames.Name name = NpcNames.kingdom(seed * 31, candidate -> taken.contains(KingdomNames.key(candidate)));
            assertTrue(taken.add(name.key()), "a name twice: " + name);
            assertTrue(KingdomNames.isValid(name.latin()), name.latin());
            assertTrue(name.latin().chars().allMatch(c -> c < 128), name.latin());
            assertTrue(name.cyrillic().chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF), name.cyrillic());
        }
        // Past every combination the names get numbers.
        assertTrue(taken.stream().anyMatch(name -> name.endsWith(" ii")));
        Set<String> towns = new HashSet<>();
        for (long seed = 0; seed < 100; seed++) {
            NpcNames.Name town = NpcNames.town(seed, candidate -> towns.contains(KingdomNames.key(candidate)));
            assertTrue(towns.add(town.key()));
            assertTrue(town.latin().length() <= 32);
        }
        assertEquals(NpcNames.kingdom(5, name -> false), NpcNames.kingdom(5, name -> false), "the same seed gives the same name");
        assertEquals("XIV", NpcNames.roman(14));
    }

    @Test
    void rulersSetTheirLawsByTheirCharacter() {
        NpcLaws merchant = NpcLaws.of(RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.BOLD), 10_000, 1_000_000);
        NpcLaws just = NpcLaws.of(RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.JUST), 10_000, 1_000_000);
        NpcLaws warrior = NpcLaws.of(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.PATIENT), 10_000, 1_000_000);
        NpcLaws builder = NpcLaws.of(RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.PATIENT), 10_000, 1_000_000);
        assertTrue(merchant.entryFee() > builder.entryFee(), "a merchant asks more at the gates");
        assertTrue(just.entryFee() < merchant.entryFee(), "a just ruler asks less");
        assertTrue(just.slaveryBanned() && !merchant.slaveryBanned(), "only a just ruler bans slavery");
        assertTrue(warrior.fineMultiplierPermille() > builder.fineMultiplierPermille(), "a warrior fines harder");
        assertTrue(just.fineMultiplierPermille() < merchant.fineMultiplierPermille());
        assertTrue(warrior.bounty() > builder.bounty(), "a warrior pays more for heads");
        NpcLaws capped = NpcLaws.of(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.GENEROUS), 5, 100);
        assertEquals(100, capped.bounty(), "the rules cap the bounty");
        assertTrue(capped.entryFee() <= 5, "the rules cap the fee");
    }

    @Test
    void rulersHaveACharacterAndOneOrTwoTraitsThatAgree() {
        Set<RulerCharacter> seen = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            NpcSite site = new NpcSite(1, 0, 0, NpcLevel.LARGE, seed * 977, List.of());
            NpcKingdom kingdom = NpcKingdom.create(UUID.randomUUID(), site, new NpcNames.Name("Belogorye", "Белогорье"), List.of(), 0, 0);
            seen.add(kingdom.character());
            assertTrue(kingdom.traits().size() == 1 || kingdom.traits().size() == 2);
            if (kingdom.traits().size() == 2) {
                assertNotEquals(kingdom.traits().get(0), kingdom.traits().get(1));
                assertFalse(kingdom.traits().get(0).opposes(kingdom.traits().get(1)));
            }
            assertFalse(kingdom.rulerName(CitizenNames.Alphabet.LATIN).isEmpty());
            assertEquals(kingdom.rulerGender() == Gender.FEMALE, kingdom.rulerName(CitizenNames.Alphabet.CYRILLIC).endsWith("а"));
        }
        assertEquals(RulerCharacter.values().length, seen.size(), "every character rules somewhere");
        for (long seed = 0; seed < 100; seed++) {
            assertNotEquals(RulerCharacter.MAGE, RulerCharacter.pick(seed, NpcLevel.SMALL), "no tower, no mage");
        }
    }
}
