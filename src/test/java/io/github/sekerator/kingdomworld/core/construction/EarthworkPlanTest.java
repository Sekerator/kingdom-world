package io.github.sekerator.kingdomworld.core.construction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkPlanTest {
    /**
     * Earthworks over flat land of a height with a square of another height in the middle (a hill or a pit).
     */
    private static Earthwork land(int size, int flat, int middle, int from, int to, Earthwork.Mode mode, int target, boolean smooth,
                                  Map<String, Integer> dug, Map<String, Integer> stock) {
        int[] ground = new int[(size + 2) * (size + 2)];
        Arrays.fill(ground, flat);
        int[] top = new int[size * size];
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                boolean inner = dx >= from && dx <= to && dz >= from && dz <= to;
                ground[(dz + 1) * (size + 2) + dx + 1] = inner ? middle : flat;
                top[dz * size + dx] = inner ? middle : flat;
            }
        }
        return new Earthwork(100, 200, size, size, mode, target, smooth, ground, top, dug, stock);
    }

    @Test
    void levellingAHillDigsItDownAndFillsNothing() {
        Earthwork hill = land(5, 70, 74, 1, 3, Earthwork.Mode.LEVEL, 70, false, Map.of(), Map.of());
        assertEquals(0, hill.fills(), "the land around is at the height already");
        assertEquals(71, hill.digFrom(2, 2));
        assertEquals(74, hill.digTo(2, 2), "the hill goes down to the height");
        assertEquals(70, hill.surface(0, 0));
        assertEquals(4, hill.span());
        assertEquals(71, hill.bottom());
        assertEquals(74, hill.ceiling());
    }

    @Test
    void fillingAPitFillsItUpAndCutsNothing() {
        Earthwork pit = land(5, 70, 65, 1, 3, Earthwork.Mode.FILL, 70, false, Map.of(), Map.of(EarthworkPlan.DIRT, 100));
        assertEquals(9 * 5, pit.fills(), "nine columns of five blocks");
        assertEquals(66, pit.fillFrom(2, 2));
        assertEquals(70, pit.fillTo(2, 2));
        assertTrue(pit.digFrom(2, 2) > pit.digTo(2, 2), "nothing is dug in a pit");
        // Cutting down to the height leaves the pit as it is; filling up to a hill's height leaves the hill.
        Earthwork cut = land(5, 70, 65, 1, 3, Earthwork.Mode.CUT, 70, false, Map.of(), Map.of());
        assertEquals(0, cut.fills(), "a cut fills nothing");
        Earthwork fill = land(5, 70, 74, 1, 3, Earthwork.Mode.FILL, 70, false, Map.of(), Map.of());
        assertEquals(74, fill.surface(2, 2), "a fill digs nothing");
    }

    @Test
    void smoothEdgesSlopeOneToOneToTheLandAround() {
        // A deep cut into flat land at 80: without smoothing the edge is a cliff of twenty blocks.
        Earthwork cliff = land(21, 80, 80, 0, 20, Earthwork.Mode.LEVEL, 60, false, Map.of(), Map.of());
        assertEquals(60, cliff.surface(0, 10));
        Earthwork slope = land(45, 80, 80, 0, 44, Earthwork.Mode.LEVEL, 60, true, Map.of(), Map.of());
        assertEquals(79, slope.surface(0, 22), "the first column keeps within a block of the land outside");
        for (int dx = 0; dx < 44; dx++) {
            int step = Math.abs(slope.surface(dx + 1, 22) - slope.surface(dx, 22));
            assertTrue(step <= 1, "a step of " + step + " at " + dx);
        }
        assertEquals(60, slope.surface(22, 22), "the middle reaches the height");
    }

    @Test
    void theFillTakesWhatTheWorkDigsFirstWithEarthOnTop() {
        // Two pits of three columns each... a pit of nine columns five deep: 45 blocks, nine of them on top.
        Earthwork pit = land(5, 70, 65, 1, 3, Earthwork.Mode.LEVEL, 70, false,
                Map.of(EarthworkPlan.DIRT, 12, "minecraft:cobblestone", 20, "minecraft:gravel", 3), Map.of(EarthworkPlan.DIRT, 4));
        EarthworkPlan.Result plan = EarthworkPlan.of(pit);
        assertEquals(45, plan.fills().size());
        for (EarthworkPlan.Fill fill : plan.fills()) {
            if (fill.y() == 70) {
                assertEquals(EarthworkPlan.DIRT, fill.item(), "the top of the fill is earth while there is earth");
            }
        }
        assertEquals(12, plan.fromDug().get(EarthworkPlan.DIRT), "the earth dug is all used");
        assertEquals(20, plan.fromDug().get("minecraft:cobblestone"), "then the stone dug");
        assertEquals(3, plan.fromDug().get("minecraft:gravel"));
        assertEquals(4, plan.fromStock().get(EarthworkPlan.DIRT), "then the storehouses");
        assertEquals(45 - 12 - 20 - 3 - 4, plan.missing(), "the rest waits for earth");
        assertTrue(plan.spoil().isEmpty(), "nothing dug is left over");
    }

    @Test
    void whatTheFillDoesNotTakeIsSpoil() {
        Earthwork hill = land(5, 70, 74, 1, 3, Earthwork.Mode.LEVEL, 70, false, Map.of(EarthworkPlan.DIRT, 9, "minecraft:cobblestone", 27), Map.of());
        EarthworkPlan.Result plan = EarthworkPlan.of(hill);
        assertEquals(0, plan.fills().size());
        assertEquals(Map.of(EarthworkPlan.DIRT, 9, "minecraft:cobblestone", 27), plan.spoil());
        assertEquals(0, plan.missing());
    }

    @Test
    void theGroundGoesToTheSiteTheStorehousesOrTheSpoilHeapAndOreAlwaysToTheStorehouses() {
        assertEquals(EarthworkPlan.Destination.SITE, EarthworkPlan.destination(EarthworkPlan.DIRT, 5, false));
        assertEquals(EarthworkPlan.Destination.STOREHOUSE, EarthworkPlan.destination(EarthworkPlan.DIRT, 0, true));
        assertEquals(EarthworkPlan.Destination.SPOIL, EarthworkPlan.destination(EarthworkPlan.DIRT, 0, false), "full storehouses never stop it");
        assertEquals(EarthworkPlan.Destination.SPOIL, EarthworkPlan.destination("minecraft:cobblestone", 0, false));
        assertEquals(EarthworkPlan.Destination.STOREHOUSE, EarthworkPlan.destination("minecraft:raw_iron", 0, false), "ore is never spoil");
        assertEquals(EarthworkPlan.Destination.STOREHOUSE, EarthworkPlan.destination("minecraft:oak_log", 0, false), "nor wood");
    }

    @Test
    void aPlanComesOutTheSameEveryTime() {
        Earthwork one = land(7, 70, 66, 2, 4, Earthwork.Mode.LEVEL, 70, true, Map.of(EarthworkPlan.DIRT, 3), Map.of());
        Earthwork two = land(7, 70, 66, 2, 4, Earthwork.Mode.LEVEL, 70, true, Map.of(EarthworkPlan.DIRT, 3), Map.of());
        assertEquals(one.fingerprint(), two.fingerprint());
        assertEquals(EarthworkPlan.of(one), EarthworkPlan.of(two));
        assertTrue(one.fingerprint() != land(7, 70, 66, 2, 4, Earthwork.Mode.LEVEL, 71, true, Map.of(EarthworkPlan.DIRT, 3), Map.of()).fingerprint());
        assertEquals(Earthwork.Mode.CUT, Earthwork.Mode.byId("cut"));
        assertEquals(Earthwork.Mode.LEVEL, Earthwork.Mode.FILL.next());
    }
}
