package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStateTest {
    private static final UUID US = new UUID(1, 1);
    private static final UUID THEM = new UUID(2, 2);
    private static final UUID OTHERS = new UUID(3, 3);
    private static final String WORLD = "minecraft:overworld";

    /**
     * A way of 48 blocks: the first 16 on our land, the next 16 on theirs, the last 16 on the land of others (or wild).
     */
    private static List<RouteCell> way(UUID middle, UUID last) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x < 48; x++) {
            UUID owner = x < 16 ? US : x < 32 ? middle : last;
            cells.add(new RouteCell(x, 64, 0, RouteCell.Kind.GROUND, 0, owner));
        }
        return cells;
    }

    private static Road add(RoadState state, List<RouteCell> cells, Road.Mode mode, List<Crossing> sent) {
        return state.addRoad(US, RoadClass.GRAVEL, WORLD, Endpoint.point(0, 0), Endpoint.point(47, 0), cells, RoadState.sectionOwners(cells),
                RoadState.crossedSections(cells, US), mode, 100, 1_000, sent);
    }

    @Test
    void aRoadOnOwnAndWildLandStartsAtOnce() {
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        Road road = add(state, way(null, null), Road.Mode.JOINT, sent);
        assertEquals(Road.Status.BUILDING, road.status());
        assertEquals(Road.Mode.OWN, road.mode());
        assertTrue(sent.isEmpty());
        assertEquals(List.of(US), road.builders());
    }

    @Test
    void crossingTheLandOfOthersWaitsForTheirConsent() {
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        Road road = add(state, way(THEM, OTHERS), Road.Mode.JOINT, sent);
        assertEquals(Road.Status.WAITING_CONSENT, road.status());
        assertEquals(2, sent.size());
        // In a joint road each kingdom builds the part on its land.
        assertEquals(US, road.section(0).builder());
        assertEquals(THEM, road.section(1).builder());
        assertEquals(OTHERS, road.section(2).builder());
        long theirs = sent.stream().filter(c -> c.owner().equals(THEM)).findFirst().orElseThrow().id();
        long others = sent.stream().filter(c -> c.owner().equals(OTHERS)).findFirst().orElseThrow().id();
        assertEquals(KingdomError.NO_PERMISSION, state.answer(theirs, US, true).outcome().error(), "the planner answered for the owner");
        assertTrue(state.answer(theirs, THEM, true).outcome().ok());
        assertEquals(Road.Status.WAITING_CONSENT, road.status(), "one consent of two started the road");
        RoadState.Answer last = state.answer(others, OTHERS, true);
        assertTrue(last.started().isPresent());
        assertEquals(Road.Status.BUILDING, road.status());
        assertEquals(KingdomError.CROSSING_CLOSED, state.answer(others, OTHERS, false).outcome().error());
    }

    @Test
    void aDeclinedOrForgottenRequestGivesTheRoadUp() {
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        Road road = add(state, way(THEM, null), Road.Mode.PAYER, sent);
        assertEquals(Road.Mode.PAYER, road.mode());
        assertEquals(US, road.section(1).builder(), "the payer builds on the land of others too");
        RoadState.Answer answer = state.answer(sent.get(0).id(), THEM, false);
        assertTrue(answer.dropped().isPresent());
        assertTrue(state.road(road.id()).isEmpty());

        List<Crossing> again = new ArrayList<>();
        Road second = add(state, way(THEM, null), Road.Mode.JOINT, again);
        assertTrue(state.expire(999).isEmpty());
        List<Road> dropped = state.expire(1_000);
        assertEquals(1, dropped.size());
        assertEquals(second.id(), dropped.get(0).id());
        assertEquals(Crossing.Status.EXPIRED, state.crossing(again.get(0).id()).orElseThrow().status());
    }

    @Test
    void aRoadTouchingLandAtTheEdgeOfASectionStillAsks() {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x < 32; x++) {
            // One block of their land among our own.
            cells.add(new RouteCell(x, 64, 0, RouteCell.Kind.GROUND, 0, x == 20 ? THEM : US));
        }
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        Road road = add(state, cells, Road.Mode.JOINT, sent);
        assertEquals(1, sent.size());
        assertEquals(Road.Status.WAITING_CONSENT, road.status());
        assertEquals(US, road.section(1).builder(), "a section mostly on our land is ours to build");
    }

    @Test
    void citiesKeepTheirLimitAndDistance() {
        RoadState state = new RoadState();
        RoadRules.Builder builder = new RoadRules.Builder();
        builder.maxCities = 2;
        builder.minCityDistance = 100;
        RoadRules rules = builder.build();
        List<int[]> capitals = List.of(new int[]{0, 0});
        assertEquals(KingdomError.CITY_TOO_CLOSE, state.checkCity(US, WORLD, 50, 0, capitals, rules).error());
        assertTrue(state.checkCity(US, WORLD, 200, 0, capitals, rules).ok());
        state.addCity(US, "North", WORLD, 200, 64, 0, 0);
        assertEquals(KingdomError.CITY_TOO_CLOSE, state.checkCity(THEM, WORLD, 250, 0, capitals, rules).error());
        assertTrue(state.checkCity(US, "minecraft:the_nether", 250, 0, List.of(), rules).ok(), "cities of another dimension count");
        state.addCity(US, "South", WORLD, 0, 64, 300, 0);
        assertEquals(KingdomError.TOO_MANY_CITIES, state.checkCity(US, WORLD, 1000, 1000, capitals, rules).error());
        assertTrue(state.checkCity(THEM, WORLD, 1000, 1000, capitals, rules).ok());
    }

    @Test
    void aKingdomThatIsGoneTakesItsCitiesRoadsAndRequests() {
        RoadState state = new RoadState();
        state.addCity(THEM, "Their town", WORLD, 500, 64, 0, 0);
        List<Crossing> sent = new ArrayList<>();
        Road waiting = add(state, way(THEM, null), Road.Mode.JOINT, sent);
        List<Road> removed = state.forgetKingdom(THEM);
        assertTrue(state.citiesOf(THEM).isEmpty());
        assertEquals(List.of(waiting), removed, "the road waiting for the gone kingdom is given up");
        BanditCamp camp = state.addCamp(WORLD, 0, 64, 0, 3, true, 0);
        camp.learn(US);
        state.forgetKingdom(US);
        assertFalse(camp.knownTo(US));
    }

    @Test
    void sectionsArePaidOncePlacedOnceAndWear() {
        RoadState state = new RoadState();
        Road road = add(state, way(null, null), Road.Mode.OWN, new ArrayList<>());
        Road.Section section = road.section(0);
        int units = section.units();
        assertEquals(16 * 3, units);
        for (int i = 0; i < units; i++) {
            section.markPaid(Map.of("minecraft:gravel", 1));
        }
        assertTrue(section.done());
        assertEquals(units, section.pool().get("minecraft:gravel"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> section.markPaid(Map.of()));
        assertTrue(section.use("minecraft:gravel"));
        section.markPlaced();
        assertEquals(1, section.placed());
        section.wear(0.3);
        assertEquals(0.7, section.condition(), 1e-9);
        section.startRepair();
        assertFalse(section.repairStep(2));
        assertTrue(section.repairStep(2));
        assertEquals(1.0, section.condition(), 1e-9);
        assertFalse(section.repairing());
    }
}
