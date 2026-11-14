package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.road.RoadFootprint;
import io.github.sekerator.kingdomworld.core.construction.Box;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How an order from afar judges a place: the street is the most frequent height of its ground, and a building keeps two blocks
 * clear of the roads and walls near it.
 */
class RemotePlacesTest {
    @Test
    void theStreetIsTheMostFrequentHeight() {
        assertEquals(64, RemoteWorks.street(Map.of(63, 4, 64, 10, 65, 3)));
        assertEquals(63, RemoteWorks.street(Map.of(63, 7, 64, 7)), "the lower of two as frequent");
        assertEquals(70, RemoteWorks.street(Map.of(70, 1)));
    }

    @Test
    void aBuildingKeepsClearOfRoadsAndWalls() {
        Box box = new Box(10, 64, 10, 20, 70, 16);
        assertTrue(RoadFootprint.near(List.of(new int[]{21, 13}), box, 2), "a road along the east side");
        assertTrue(RoadFootprint.near(List.of(new int[]{22, 18}), box, 2), "two blocks off the corner");
        assertFalse(RoadFootprint.near(List.of(new int[]{23, 13}), box, 2), "three blocks off");
        assertFalse(RoadFootprint.near(List.of(new int[]{15, 19}), box, 2), "three blocks to the south");
        assertTrue(RoadFootprint.near(List.of(new int[]{15, 13}), box, 0), "a road through the place");
        assertFalse(RoadFootprint.near(List.of(), box, 2));
    }

    @Test
    void aHoldGivesItsTicketsAgainAndLetsGoFromTheFirstTick() {
        assertTrue(ChunkHold.due(0, ChunkHold.NEVER, 200), "the first refresh is due at once");
        assertTrue(ChunkHold.due(Integer.MAX_VALUE, ChunkHold.NEVER, 2), "late in a long game as well");
        assertTrue(ChunkHold.due(300, ChunkHold.NEVER, 2), "the first chunk is let go at once");
        assertFalse(ChunkHold.due(301, 300, 2), "then one every two ticks");
        assertTrue(ChunkHold.due(302, 300, 2));
        assertFalse(ChunkHold.due(399, 200, 200));
        assertTrue(ChunkHold.due(400, 200, 200), "the tickets are given again every 200 ticks");
    }

    @Test
    void onlyTheRoadsNearACityAreLookedAt() {
        List<int[]> cells = List.of(new int[]{0, 0}, new int[]{40, 0}, new int[]{0, -41}, new int[]{-40, 40});
        List<int[]> near = CityPlots.within(cells, 0, 0, 40);
        assertEquals(3, near.size());
        assertTrue(near.stream().noneMatch(cell -> cell[1] == -41));
    }
}
