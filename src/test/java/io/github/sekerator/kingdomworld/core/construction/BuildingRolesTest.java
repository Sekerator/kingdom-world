package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roles of buildings: the open register, the numbers that grow with the level, the threshold of integrity below which
 * a role rests, and what houses and wells do to the mood and the growth of a kingdom.
 */
class BuildingRolesTest {
    private static final UUID KINGDOM = UUID.randomUUID();

    static Building building(String role, int level, int integrity) {
        return new Building(1, KINGDOM, "kingdomworld:small_house", "house", "housing", role, level, Materials.NONE,
                new WorldPos("minecraft:overworld", 0, 64, 0), Placement.NONE, new Box(-4, 64, -8, 4, 72, 0), 7L, 100L, false, integrity, 100L,
                false, new BitSet());
    }

    @Test
    void theModRegistersItsRoles() {
        for (String role : List.of(BuildingRoles.HOUSE, BuildingRoles.WAREHOUSE, BuildingRoles.WORKSHOP, BuildingRoles.MARKET, BuildingRoles.TOWER,
                BuildingRoles.WALL, BuildingRoles.WELL, BuildingRoles.BARRACKS, BuildingRoles.MAGE_TOWER)) {
            assertTrue(BuildingRoles.exists(role), role);
        }
        assertEquals(BuildingRole.Requirement.BED, BuildingRoles.get(BuildingRoles.HOUSE).orElseThrow().requirement());
        assertEquals(BuildingRole.Requirement.CONTAINER, BuildingRoles.get(BuildingRoles.WAREHOUSE).orElseThrow().requirement());
        assertFalse(BuildingRoles.exists(""));
        assertFalse(BuildingRoles.exists("no_such_role"));
    }

    @Test
    void otherPartsAndDataPacksAddRoles() {
        BuildingRoles.register(new BuildingRole("test_barracks", BuildingRole.Requirement.BED, Map.of("soldiers", 4.0), Map.of("soldiers", 2.0)));
        assertTrue(BuildingRoles.exists("test_barracks"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildingRoles.register(new BuildingRole("test_barracks", BuildingRole.Requirement.NONE, Map.of())));
        assertEquals(8.0, BuildingRoles.get("test_barracks").orElseThrow().params(3, Map.of()).get("soldiers"));

        BuildingRoles.replaceData(List.of(new BuildingRole("test_tavern", BuildingRole.Requirement.NONE, Map.of("mood", 3.0)),
                new BuildingRole(BuildingRoles.HOUSE, BuildingRole.Requirement.NONE, Map.of())));
        assertTrue(BuildingRoles.exists("test_tavern"));
        // A role of the code wins over a data role of the same id.
        assertEquals(BuildingRole.Requirement.BED, BuildingRoles.get(BuildingRoles.HOUSE).orElseThrow().requirement());
        BuildingRoles.replaceData(List.of());
        assertFalse(BuildingRoles.exists("test_tavern"));
    }

    @Test
    void numbersGrowWithTheLevelUnlessTheTemplateGivesItsOwn() {
        BuildingRole house = BuildingRoles.get(BuildingRoles.HOUSE).orElseThrow();
        double first = house.params(1, Map.of()).get(BuildingRoles.COMFORT);
        double second = house.params(2, Map.of()).get(BuildingRoles.COMFORT);
        assertTrue(second > first, "comfort grows with the level");
        assertEquals(12.0, house.params(2, Map.of(BuildingRoles.COMFORT, 12.0)).get(BuildingRoles.COMFORT));
        assertEquals(first, house.params(1, Map.of(BuildingRoles.COMFORT, Double.NaN)).get(BuildingRoles.COMFORT));
        BuildingRole warehouse = BuildingRoles.get(BuildingRoles.WAREHOUSE).orElseThrow();
        assertTrue(warehouse.params(2, Map.of()).get(BuildingRoles.CONTAINERS) > warehouse.params(1, Map.of()).get(BuildingRoles.CONTAINERS));
        assertEquals(5.0, BuildingRole.param(Map.of(), "missing", 5.0));
    }

    @Test
    void aRoleRestsBelowTheThresholdOfIntegrity() {
        assertEquals(BuildingRoles.Status.ACTIVE, BuildingRoles.status(building("house", 1, 100), 70, false, true));
        assertEquals(BuildingRoles.Status.ACTIVE, BuildingRoles.status(building("house", 1, 70), 70, false, true));
        assertEquals(BuildingRoles.Status.DAMAGED, BuildingRoles.status(building("house", 1, 69), 70, false, true));
        // Not looked at yet: it counts as whole.
        assertEquals(BuildingRoles.Status.ACTIVE, BuildingRoles.status(building("house", 1, -1), 70, false, true));
        assertEquals(BuildingRoles.Status.DEMOLISHING, BuildingRoles.status(building("house", 1, 100), 70, true, true));
        assertEquals(BuildingRoles.Status.LAND_LOST, BuildingRoles.status(building("house", 1, 100), 70, false, false));
        assertEquals(BuildingRoles.Status.NONE, BuildingRoles.status(building("", 1, 100), 70, false, true));
        assertTrue(BuildingRoles.Status.ACTIVE.works());
        assertFalse(BuildingRoles.Status.DAMAGED.works());
    }

    @Test
    void aRepairBringsTheRoleBack() {
        BitSet missing = new BitSet();
        missing.set(3, 40);
        Building damaged = building("house", 1, 100).withIntegrity(50, 200L, missing);
        assertEquals(BuildingRoles.Status.DAMAGED, BuildingRoles.status(damaged, 70, false, true));
        assertEquals(37, damaged.damagedCount());
        Building repaired = damaged.repaired(300L);
        assertEquals(100, repaired.integrity());
        assertEquals(0, repaired.damagedCount());
        assertEquals(BuildingRoles.Status.ACTIVE, BuildingRoles.status(repaired, 70, false, true));
    }

    @Test
    void comfortOfHousesAndWellsLiftsTheMood() {
        CitizenNeeds.MoodInputs plain = new CitizenNeeds.MoodInputs(15.0f, true, false, 0, 100, 0);
        CitizenNeeds.MoodInputs cosy = new CitizenNeeds.MoodInputs(15.0f, true, false, 0, 100, 10);
        CitizenNeeds.MoodInputs palace = new CitizenNeeds.MoodInputs(15.0f, true, false, 0, 100, 1_000);
        assertEquals(CitizenNeeds.targetMood(plain) + 10, CitizenNeeds.targetMood(cosy));
        assertEquals(Math.min(CitizenNeeds.MAX_MOOD, CitizenNeeds.targetMood(plain) + CitizenNeeds.MAX_COMFORT), CitizenNeeds.targetMood(palace));
    }

    @Test
    void freeBedsInHousesBringNewcomersSooner() {
        CitizenRules rules = CitizenRules.DEFAULT;
        long base = CitizenNeeds.growthInterval(rules.growthIntervalTicks(), CitizenNeeds.NEUTRAL_MOOD);
        long sooner = Math.round(base * CitizenNeeds.HOUSE_GROWTH_FACTOR);
        long food = 1_000_000;
        assertEquals(CitizenNeeds.GrowthCheck.TOO_SOON, CitizenNeeds.checkGrowth(3, 2, 0, food, sooner, CitizenNeeds.NEUTRAL_MOOD, rules));
        assertEquals(CitizenNeeds.GrowthCheck.READY, CitizenNeeds.checkGrowth(3, 2, 1, food, sooner, CitizenNeeds.NEUTRAL_MOOD, rules));
        assertEquals(CitizenNeeds.GrowthCheck.NO_FREE_BED, CitizenNeeds.checkGrowth(3, 0, 0, food, base * 4, CitizenNeeds.NEUTRAL_MOOD, rules));
    }
}
