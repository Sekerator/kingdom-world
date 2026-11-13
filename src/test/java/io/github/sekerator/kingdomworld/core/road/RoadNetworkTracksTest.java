package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkTracksTest {
    private static final double PACE = 60.0;

    @Test
    void aCaravanGoesAlongATrackOfThePlanFasterThanAcrossCountry() {
        // A track bending a little between two settlements 1200 blocks apart.
        RoadNetwork.Track track = new RoadNetwork.Track(new int[]{0, 400, 800, 1200}, new int[]{0, 60, 60, 0});
        TravelPlan across = RoadNetwork.plan(List.of(), List.of(), "minecraft:overworld", 0, 0, 1200, 0, PACE, RoadRules.DEFAULT, 0, Long.MAX_VALUE);
        TravelPlan along = RoadNetwork.plan(List.of(), List.of(track), "minecraft:overworld", 0, 0, 1200, 0, PACE, RoadRules.DEFAULT, 0,
                Long.MAX_VALUE);
        assertTrue(along.ticks() < across.ticks(), "along " + along.ticks() + ", across " + across.ticks());
        assertTrue(along.points().stream().anyMatch(point -> point.z() == 60), "the caravan did not follow the track");
    }

    @Test
    void tracksMeetingAtASettlementMakeOneNetwork() {
        RoadNetwork.Track first = new RoadNetwork.Track(new int[]{0, 600, 1200}, new int[]{0, 300, 600});
        RoadNetwork.Track second = new RoadNetwork.Track(new int[]{1210, 1800, 2400}, new int[]{600, 300, 0});
        TravelPlan along = RoadNetwork.plan(List.of(), List.of(first, second), "minecraft:overworld", 0, 0, 2400, 0, PACE, RoadRules.DEFAULT, 0,
                Long.MAX_VALUE);
        TravelPlan.Point last = along.points().get(along.points().size() - 1);
        assertEquals(2400, last.x());
        // The way over both tracks is longer than the straight one but 1.2 times as fast: it wins only where the bend is small.
        TravelPlan across = RoadNetwork.plan(List.of(), List.of(), "minecraft:overworld", 0, 0, 2400, 0, PACE, RoadRules.DEFAULT, 0,
                Long.MAX_VALUE);
        assertTrue(along.ticks() <= across.ticks());
    }
}
