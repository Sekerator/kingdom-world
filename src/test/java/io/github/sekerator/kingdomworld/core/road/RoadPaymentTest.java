package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builders and miners paying for the columns of a levelled road, and the credit of what it digs settled once it is done.
 */
class RoadPaymentTest {
    private static final EarthRules EARTH = EarthRules.DEFAULT;

    private static Road road() {
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            cells.add(new RouteCell(i, 70, 0, RouteCell.Kind.CUT, 4, null));
        }
        return new Road(1, new UUID(1, 1), RoadClass.GRAVEL, "minecraft:overworld", Endpoint.point(0, 0), Endpoint.point(20, 0), cells,
                Road.Mode.OWN, 0, Road.Status.BUILDING, List.of(), RoadLayout.LEVELLED, null);
    }

    /** A column with 10 seconds of builders and 25 of miners. */
    private static final ColumnWork ROCKY = new ColumnWork(10, 25, new int[7]);

    @Test
    void minersPayTheRockFirstAndBuildersTheRest() {
        Road.Section section = road().section(0);
        RoadPayment.Budget budget = new RoadPayment.Budget(12, 30);
        assertTrue(RoadPayment.pay(section, ROCKY, budget, EARTH.rockByBuilders()));
        assertEquals(25, budget.minersSpent, 1e-9);
        assertEquals(5, budget.miners, 1e-9, "what the miners did not spend is left this second");
        assertEquals(2, budget.builders, 1e-9);
        assertEquals(0, budget.buildersOnRock, 1e-9);
    }

    @Test
    void withoutMinersBuildersDigTheRockTwiceAsLong() {
        Road.Section section = road().section(0);
        RoadPayment.Budget budget = new RoadPayment.Budget(100, 0);
        assertTrue(RoadPayment.pay(section, ROCKY, budget, EARTH.rockByBuilders()));
        assertEquals(25 * 2.0 + 10, budget.buildersSpent, 1e-9);
        assertEquals(50, budget.buildersOnRock, 1e-9);
        assertEquals(40, budget.builders, 1e-9);
    }

    @Test
    void aColumnIsPaidOverSecondsAndTheWorkIsKept() {
        Road.Section section = road().section(0);
        int seconds = 0;
        boolean done = false;
        while (!done) {
            seconds++;
            // Four builders and three miners at half their time: 4 and 1.5 seconds a second.
            done = RoadPayment.pay(section, ROCKY, new RoadPayment.Budget(4, 1.5), EARTH.rockByBuilders());
            assertTrue(seconds < 100);
        }
        assertTrue(section.minerWork() >= 25 - 1e-6);
        assertTrue(section.builderWork() >= 10 - 1e-6);
        // Miners pay 1.5 a second, builders after their 10 seconds help with the rock: well under the 60 seconds builders alone take.
        assertTrue(seconds <= 12, "seconds " + seconds);
        section.markPaid(Map.of());
        assertEquals(0, section.builderWork(), 1e-9, "the next column starts afresh");
        assertEquals(0, section.minerWork(), 1e-9);
        assertFalse(RoadPayment.given(section, ROCKY));
    }

    @Test
    void theCreditOfARoadCreatesNoItems() {
        Road road = road();
        // Columns paid for that dig 40 cobblestone and 6 dirt; 30 cobblestone go into embankments at once.
        road.addCredit("minecraft:cobblestone", 40);
        road.addCredit("minecraft:dirt", 6);
        assertEquals(30, road.useCredit("minecraft:cobblestone", 30));
        assertEquals(0, road.useCredit("minecraft:planks", 3), "nothing is taken that was not counted");
        // All is dug but two blocks of ore, whose ore goes to the storehouses: 38 cobblestone and the dirt settle the debt.
        assertEquals(38, road.settleOwed("minecraft:cobblestone", 38));
        assertEquals(6, road.settleOwed("minecraft:dirt", 9), "more dirt dug than counted goes to the storehouses as it is");
        RoadPayment.Settlement done = RoadPayment.settle(road.takeCredit(), road.takeOwed());
        assertEquals(Map.of("minecraft:cobblestone", 8, "minecraft:dirt", 6), done.toStorage(),
                "the cobblestone dug and not used, less the two blocks of ore never dug as cobblestone");
        assertTrue(done.shortfall().isEmpty());
        assertTrue(road.credit().isEmpty() && road.owed().isEmpty());
    }

    @Test
    void aRoadGivenUpBeforeItsCuttingsAreDugIsShort() {
        Road road = road();
        road.addCredit("minecraft:cobblestone", 40);
        assertEquals(35, road.useCredit("minecraft:cobblestone", 35));
        // Only ten blocks were dug before the road was given up.
        road.settleOwed("minecraft:cobblestone", 10);
        RoadPayment.Settlement given = RoadPayment.settle(road.credit(), road.owed());
        assertTrue(given.toStorage().isEmpty());
        assertEquals(Map.of("minecraft:cobblestone", 25), given.shortfall(),
                "35 went into embankments, only 10 were dug: 25 are taken from the storehouses");
    }
}
