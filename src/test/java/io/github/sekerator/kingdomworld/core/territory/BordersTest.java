package io.github.sekerator.kingdomworld.core.territory;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whom a state borders by its provinces, and who stands in the way of one that wants land.
 */
class BordersTest {
    private static final UUID STATE = new UUID(0, 1);
    private static final UUID NORTH = new UUID(0, 2);
    private static final UUID EAST = new UUID(0, 3);
    private static final UUID SOUTH = new UUID(0, 4);

    /**
     * Provinces 1 and 2 of the state; 3 of the north next to 1, 4 of the east next to 1 and 2, 5 of nobody next to 2, 6 of the
     * south next to 2, 7 of the north far away.
     */
    private static final Map<Integer, List<Integer>> NEIGHBOURS = Map.of(
            1, List.of(2, 3, 4),
            2, List.of(1, 4, 5, 6),
            7, List.of(3));
    private static final Map<Integer, UUID> HOLDERS = new HashMap<>(Map.of(1, STATE, 2, STATE, 3, NORTH, 4, EAST, 6, SOUTH, 7, NORTH));

    private static List<Integer> neighbours(int province) {
        return NEIGHBOURS.getOrDefault(province, List.of());
    }

    @Test
    void aStateBordersTheHoldersOfTheProvincesNextToItsOwnEachOnce() {
        Set<UUID> all = Borders.holders(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, STATE, Integer.MAX_VALUE);
        assertEquals(List.of(NORTH, EAST, SOUTH), List.copyOf(all), "in the order met, land of nobody and the state itself left out");
        assertEquals(List.of(NORTH, EAST), List.copyOf(Borders.holders(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, STATE, 2)),
                "at most as many as asked");
        assertTrue(Borders.holders(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, STATE, 0).isEmpty());
        assertTrue(Borders.holders(List.of(), BordersTest::neighbours, HOLDERS::get, STATE, 5).isEmpty(), "a kingdom without provinces borders nobody");
    }

    @Test
    void nobodyStandsInTheWayOfAStateWhileWildLandNextToItIsFree() {
        assertTrue(Borders.inTheWay(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, p -> p == 5, STATE, 5).isEmpty(),
                "the wild province 5 is free: the state grows there by itself");
        assertEquals(Set.of(NORTH, EAST, SOUTH), Borders.inTheWay(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, p -> false, STATE, 5),
                "with no wild land free, every neighbour stands in the way");
        assertEquals(1, Borders.inTheWay(List.of(1, 2), BordersTest::neighbours, HOLDERS::get, p -> false, STATE, 1).size());
    }
}
