package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which storehouse a worker goes to: the storehouse of its own profession before a common one, never one of other professions;
 * inside the city of its post at any distance, anywhere on the land outside the cities; what comes in otherwise goes to the common
 * storehouses and then to any with room.
 */
class StorehouseRulesTest {
    private static final String WORLD = "minecraft:overworld";
    private static final StorehouseRules.Accepts WOOD = StorehouseRules.Accepts.of(List.of("lumberjack"));
    private static final StorehouseRules.Accepts FOOD = StorehouseRules.Accepts.of(List.of("farmer", "herder"));

    private static StorehouseRules.Candidate<String> store(String name, int x, int z, StorehouseRules.Accepts accepts, boolean room) {
        return new StorehouseRules.Candidate<>(name, WORLD, x, 64, z, accepts, room);
    }

    private static StorehouseRules.Reach anywhere() {
        return new StorehouseRules.Reach(WORLD, null);
    }

    @Test
    void theStorehouseOfTheProfessionComesBeforeANearerCommonOne() {
        List<StorehouseRules.Candidate<String>> stores = List.of(store("common", 5, 0, StorehouseRules.Accepts.ALL, true),
                store("wood", 40, 0, WOOD, true), store("far common", 80, 0, StorehouseRules.Accepts.ALL, true));
        assertEquals(List.of("wood", "common", "far common"), StorehouseRules.delivery(stores, "lumberjack", anywhere(), 0, 64, 0));
        // A full storehouse of wood leaves the common ones.
        List<StorehouseRules.Candidate<String>> full = List.of(store("common", 5, 0, StorehouseRules.Accepts.ALL, true),
                store("wood", 40, 0, WOOD, false));
        assertEquals(List.of("common"), StorehouseRules.delivery(full, "lumberjack", anywhere(), 0, 64, 0));
    }

    @Test
    void aStorehouseOfOtherProfessionsIsNeverChosen() {
        List<StorehouseRules.Candidate<String>> stores = List.of(store("food", 2, 0, FOOD, true), store("wood", 4, 0, WOOD, true));
        assertEquals(List.of(), StorehouseRules.delivery(stores, "miner", anywhere(), 0, 64, 0), "a miner has nowhere to go");
        assertEquals(List.of("food"), StorehouseRules.delivery(stores, "herder", anywhere(), 0, 64, 0));
        assertEquals(StorehouseRules.Kind.OTHERS, WOOD.kindFor("farmer"));
        assertEquals(StorehouseRules.Kind.OWN, FOOD.kindFor("farmer"));
        assertEquals(StorehouseRules.Kind.COMMON, StorehouseRules.Accepts.ALL.kindFor("farmer"));
        // Taking is from any storehouse.
        assertEquals(List.of("food", "wood"), StorehouseRules.taking(stores, anywhere(), 0, 64, 0));
    }

    @Test
    void aPostInACityReachesTheStorehousesOfTheCityAtAnyDistance() {
        StorehouseRules.Reach city = new StorehouseRules.Reach(WORLD, new StorehouseRules.Square(0, 0, 120));
        List<StorehouseRules.Candidate<String>> stores = List.of(store("in the city, 100 blocks away", 100, 10, StorehouseRules.Accepts.ALL, true),
                store("beyond the wall", 130, 0, StorehouseRules.Accepts.ALL, true));
        assertEquals(List.of("in the city, 100 blocks away"), StorehouseRules.delivery(stores, "crafter", city, 0, 64, 0));
        assertEquals(List.of("in the city, 100 blocks away"), StorehouseRules.taking(stores, city, 0, 64, 0));
        // Another dimension is out of reach.
        assertTrue(new StorehouseRules.Reach("minecraft:the_nether", null).contains("minecraft:the_nether", 1, 1));
        assertTrue(!city.contains("minecraft:the_nether", 1, 1));
    }

    @Test
    void aPostOutsideTheCitiesReachesAnyStorehouse() {
        List<StorehouseRules.Candidate<String>> stores = List.of(store("far away", 5_000, -3_000, StorehouseRules.Accepts.ALL, true));
        assertEquals(List.of("far away"), StorehouseRules.delivery(stores, "miner", anywhere(), 0, 64, 0));
        // Far from players nothing is out of reach either.
        assertEquals(List.of("far away"), StorehouseRules.delivery(stores, "miner", null, 0, 64, 0));
    }

    @Test
    void whatComesInOtherwiseGoesToTheCommonStorehousesThenToAnyWithRoom() {
        List<StorehouseRules.Candidate<String>> stores = List.of(store("wood", 1, 0, WOOD, true), store("common", 50, 0,
                StorehouseRules.Accepts.ALL, true), store("food", 2, 0, FOOD, true));
        assertEquals(List.of("common", "wood", "food"), StorehouseRules.income(stores, 0, 64, 0));
        List<StorehouseRules.Candidate<String>> commonFull = List.of(store("wood", 1, 0, WOOD, true), store("common", 50, 0,
                StorehouseRules.Accepts.ALL, false));
        assertEquals(List.of("wood"), StorehouseRules.income(commonFull, 0, 64, 0), "nothing is lost while any storehouse has room");
    }

    @Test
    void aSettingKeepsOnlyProfessionIdsSorted() {
        StorehouseRules.Accepts accepts = StorehouseRules.Accepts.of(List.of("lumberjack", "farmer", "Not An Id"));
        assertEquals(List.of("farmer", "lumberjack"), List.copyOf(accepts.professions()));
        assertTrue(StorehouseRules.Accepts.of(List.of()).all());
        assertEquals(StorehouseRules.Accepts.ALL, new StorehouseRules.Accepts(Set.of()));
    }

    @Test
    void aCarrierTakesTheGoodsOfAPostWhereTheTargetSays() {
        WorldPos home = new WorldPos(WORLD, 0, 64, 0);
        CarrierPlanner.Stock pile = new CarrierPlanner.Stock(new WorldPos(WORLD, 4, 64, 0), 30, 10, 27);
        WorldPos wood = new WorldPos(WORLD, 100, 64, 0);
        Optional<CarrierPlanner.Task> task = CarrierPlanner.next(home, List.of(pile), source -> Optional.of(wood), List.of(), List.of(), 576, 48);
        assertEquals(wood, task.orElseThrow().to(), "the storehouse of the work of the post, even beyond the range of the carrier");
        assertEquals(CarrierPlanner.Kind.COLLECT, task.get().kind());
        assertTrue(CarrierPlanner.next(home, List.of(pile), source -> Optional.empty(), List.of(), List.of(), 576, 48).isEmpty(),
                "goods with no storehouse of their work and no common one stay at the post");
    }
}
