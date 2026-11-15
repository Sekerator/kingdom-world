package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The example of the levelled roads: a gravel road meets a ridge of rock 30 above it and 150 blocks across, with a way round
 * its end 120 blocks longer. Going round costs a plain road; a tunnel costs the rock of its miners, its torches and its roof; an
 * open cutting costs far more. Along the land and in moderation the road goes round, straight on it bores the tunnel; the card
 * shows the work, the days with the workers there are and the time of a caravan.
 */
class RoadEstimateTest {
    private static final RoadRules ROADS = RoadRules.DEFAULT;
    private static final EarthRules EARTH = EarthRules.DEFAULT;

    private static List<RouteCell> straight(int length, RouteCell.Kind kind, int y) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            cells.add(new RouteCell(x, y, 0, kind, 0, null));
        }
        return cells;
    }

    @Test
    void theExampleOfTheRidge() {
        // Rock under no soil: the ridge.
        RoadSurvey.Land rock = (x, z) -> LandColumn.land(94, 0);
        RoadSurvey.Land plain = (x, z) -> LandColumn.land(64, 3);
        RoadEstimate tunnel = RoadEstimate.of(straight(150, RouteCell.Kind.TUNNEL, 64), RoadClass.GRAVEL, rock, ROADS, EARTH, 150, 200, null);
        RoadEstimate flat = RoadEstimate.of(straight(150, RouteCell.Kind.GROUND, 64), RoadClass.GRAVEL, plain, ROADS, EARTH, 150, 200, null);
        RoadEstimate cut = RoadEstimate.of(straight(150, RouteCell.Kind.CUT, 64), RoadClass.GRAVEL, (x, z) -> LandColumn.land(94, 3), ROADS,
                EARTH, 150, 200, null);
        assertEquals(12.0 * 150, flat.effort(), 1e-6, "a plain block of gravel is worth twelve seconds");
        assertEquals(150 * 5 * 3 * 5 * 1.5, tunnel.miners(), 1e-6, "the rock of the tunnel");
        assertEquals(19, tunnel.torches());
        double tunnelExtra = tunnel.effort() - flat.effort();
        assertTrue(tunnelExtra > 16_875 && tunnelExtra < 17_700, "the tunnel costs about 17 600 more: " + tunnelExtra);
        assertTrue(cut.effort() - flat.effort() > 4 * tunnelExtra, "an open cutting loses to the tunnel");
        double around = 120 * EarthRules.base(RoadClass.GRAVEL, ROADS);
        assertEquals(1_440, around, 1e-6);
        // What each way of the king takes: going round 120 blocks longer, or the tunnel.
        RouteStyle[] styles = RouteStyle.values();
        boolean[] tunnels = {false, false, true};
        for (int s = 0; s < styles.length; s++) {
            double lambda = EARTH.lambda(RoadClass.GRAVEL, styles[s], ROADS);
            double round = 120 * (EarthRules.base(RoadClass.GRAVEL, ROADS) + lambda);
            assertEquals(tunnels[s], tunnelExtra < round, styles[s].id() + ": round " + round + " against the tunnel " + tunnelExtra);
            System.out.printf("%s: lambda %.0f, round %.0f, tunnel %.0f%n", styles[s].id(), lambda, round, tunnelExtra);
        }
        // Four builders and three miners at half their time: the tunnel takes about 4.2 game days.
        double seconds = tunnel.time(4, 1.5, EARTH);
        assertTrue(seconds > 4_600 && seconds < 5_600, "seconds " + seconds);
        assertTrue(RoadEstimate.EMPTY.time(0, 1, EARTH) == Double.POSITIVE_INFINITY, "no builders, no time");
        // A caravan on gravel goes 500 blocks a minute: 120 blocks less is 14.4 seconds.
        double saved = RoadNetwork.ticks(120, 200 * ROADS.caravanSpeed(RoadClass.GRAVEL)) / 20.0;
        assertEquals(14.4, saved, 1e-6);
    }

    @Test
    void lengthsCountADiagonalStepThroughItsCorner() {
        List<RouteCell> cells = List.of(new RouteCell(0, 64, 0, RouteCell.Kind.GROUND, 0, null), new RouteCell(1, 64, 0, RouteCell.Kind.GROUND, 0,
                null), new RouteCell(1, 64, 1, RouteCell.Kind.GROUND, 0, null), new RouteCell(2, 64, 1, RouteCell.Kind.GROUND, 0, null));
        assertEquals(1 + Math.sqrt(2), RoadEstimate.length(cells), 1e-9);
        RoadEstimate both = RoadEstimate.of(straight(10, RouteCell.Kind.GROUND, 64), RoadClass.PATH, (x, z) -> LandColumn.land(64, 3), ROADS,
                EARTH, 10, 200, null);
        RoadEstimate twice = both.plus(both);
        assertEquals(2 * both.builders(), twice.builders(), 1e-9);
        assertEquals(2 * both.length(), twice.length(), 1e-9);
    }
}
