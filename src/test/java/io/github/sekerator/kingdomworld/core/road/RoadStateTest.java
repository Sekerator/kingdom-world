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

    @Test
    void aConsentToARoadAtTheRequestersCostIsRememberedForTheNextSuch() {
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        Road first = add(state, way(THEM, null), Road.Mode.PAYER, sent);
        assertEquals(1, sent.size());
        assertFalse(state.consented(THEM, US, Road.Mode.PAYER));
        assertTrue(state.answer(sent.get(0).id(), THEM, true).started().isPresent());
        assertEquals(Road.Status.BUILDING, first.status());
        assertTrue(state.consented(THEM, US, Road.Mode.PAYER));
        assertFalse(state.consented(US, THEM, Road.Mode.PAYER), "a consent goes one way");
        sent.clear();
        Road second = add(state, way(THEM, null), Road.Mode.PAYER, sent);
        assertTrue(sent.isEmpty(), "no new request for land that let such roads through before");
        assertEquals(Road.Status.BUILDING, second.status());
        assertEquals(US, second.section(1).builder(), "the requester builds and pays on the land of the owner");
        // Without remembering, each road asks again.
        state.setRememberConsent(false);
        Road third = add(state, way(THEM, null), Road.Mode.PAYER, sent);
        assertEquals(1, sent.size());
        assertEquals(Road.Status.WAITING_CONSENT, third.status());
    }

    @Test
    void aJointRoadAlwaysAsksTheOwnerOfTheLand() {
        RoadState state = new RoadState();
        List<Crossing> sent = new ArrayList<>();
        // The owner once let a road through that the requester built and paid for alone.
        add(state, way(THEM, null), Road.Mode.PAYER, sent);
        assertTrue(state.answer(sent.get(0).id(), THEM, true).outcome().ok());
        sent.clear();
        // A joint road would make the owner build and pay on its land: it is asked, whatever it agreed to before.
        Road joint = add(state, way(THEM, null), Road.Mode.JOINT, sent);
        assertEquals(Road.Status.WAITING_CONSENT, joint.status());
        assertEquals(1, sent.size());
        assertEquals(THEM, sent.get(0).owner());
        assertEquals(Road.Mode.JOINT, sent.get(0).mode());
        assertEquals(THEM, joint.section(1).builder());
        assertFalse(state.consented(THEM, US, Road.Mode.JOINT));
        // A joint road agreed to once is not remembered either.
        assertTrue(state.answer(sent.get(0).id(), THEM, true).outcome().ok());
        sent.clear();
        Road again = add(state, way(THEM, null), Road.Mode.JOINT, sent);
        assertEquals(Road.Status.WAITING_CONSENT, again.status());
        assertEquals(1, sent.size());
        // Remembered consents read back only with their mode; one that names none is not taken.
        RoadState restored = new RoadState();
        state.consents().forEach(restored::restoreConsent);
        assertTrue(restored.consented(THEM, US, Road.Mode.PAYER));
        RoadState legacy = new RoadState();
        legacy.restoreConsent(THEM + ">" + US);
        legacy.restoreConsent(THEM + ">" + US + ">joint");
        assertTrue(legacy.consents().isEmpty());
    }

    @Test
    void withoutALimitAKingdomFoundsAsManyCitiesAsItHasRoomFor() {
        RoadState state = new RoadState();
        RoadRules rules = RoadRules.DEFAULT;
        List<int[]> capitals = List.of(new int[]{0, 0});
        for (int i = 1; i <= 120; i++) {
            int x = i * rules.minCityDistance();
            assertTrue(state.checkCity(US, WORLD, x, 0, capitals, rules).ok(), "city " + i);
            state.addCity(US, "City " + i, WORLD, x, 64, 0, 0);
        }
        assertEquals(120, state.citiesOf(US).size());
        assertTrue(state.checkCity(US, WORLD, 0, 5_000, capitals, rules).ok());
        // A limit the server sets still holds.
        RoadRules.Builder limited = new RoadRules.Builder();
        limited.maxCities = 120;
        assertEquals(KingdomError.TOO_MANY_CITIES, state.checkCity(US, WORLD, 0, 5_000, capitals, limited.build()).error());
        assertTrue(state.checkCity(THEM, WORLD, 0, 5_000, capitals, limited.build()).ok());
    }

    @Test
    void citiesKeepApartAlongXOrZSoThatTheSquaresOfLargeCitiesNeverMeet() {
        RoadRules rules = RoadRules.DEFAULT;
        int wall = CityLevel.CITY.wallRadius(rules);
        assertEquals(48, wall);
        assertEquals(112, rules.minCityDistance());
        assertTrue(rules.minCityDistance() >= 2 * wall + 16, "16 blocks between the walls of two large cities");
        RoadState state = new RoadState();
        state.addCity(US, "Middle", WORLD, 0, 64, 0, 0);
        List<int[]> capitals = List.of();
        // Straight along an axis and on a slant alike: only as far apart as the squares need.
        assertTrue(state.checkCity(THEM, WORLD, 112, 0, capitals, rules).ok());
        assertTrue(state.checkCity(THEM, WORLD, 0, -112, capitals, rules).ok());
        assertTrue(state.checkCity(THEM, WORLD, 112, 112, capitals, rules).ok());
        assertTrue(state.checkCity(THEM, WORLD, 112, 80, capitals, rules).ok());
        assertEquals(KingdomError.CITY_TOO_CLOSE, state.checkCity(THEM, WORLD, 111, 0, capitals, rules).error());
        assertEquals(KingdomError.CITY_TOO_CLOSE, state.checkCity(THEM, WORLD, 80, 80, capitals, rules).error(),
                "113 blocks along a straight line, but the squares of two large cities would meet");
        for (int dx = -2 * wall; dx <= 2 * wall; dx += 8) {
            for (int dz = -2 * wall; dz <= 2 * wall; dz += 8) {
                assertTrue(rules.tooClose(dx, dz), "two large cities " + dx + " " + dz + " apart would share land");
            }
        }
        // A capital counts the same way.
        assertEquals(KingdomError.CITY_TOO_CLOSE, state.checkCity(US, WORLD, 500, 500 + 100, List.of(new int[]{500, 500}), rules).error());
        assertTrue(state.checkCity(US, WORLD, 500, 500 + 112, List.of(new int[]{500, 500}), rules).ok());
    }

    @Test
    void theCitiesOfOldWorldsStillStandFarEnough() {
        // Cities founded before stand 160 blocks apart along a straight line: along x or z that is 113 blocks at least.
        RoadRules rules = RoadRules.DEFAULT;
        for (int angle = 0; angle < 360; angle += 5) {
            double a = Math.toRadians(angle);
            long dx = Math.round(160 * Math.cos(a));
            long dz = Math.round(160 * Math.sin(a));
            assertFalse(rules.tooClose(dx, dz), angle + " degrees");
        }
    }

    @Test
    void roadsHaveNoLimitByDefaultAndKingdomsOfTheWorldNoLimitOfCities() {
        assertFalse(RoadRules.DEFAULT.roadsFull(1_000_000));
        RoadRules.Builder limited = new RoadRules.Builder();
        limited.maxRoads = 32;
        assertTrue(limited.build().roadsFull(32));
        assertFalse(limited.build().roadsFull(31));
        RoadRules world = limited.build().forWorldKingdom(200);
        assertEquals(0, world.maxCities());
        assertEquals(200, world.maxRoads());
        assertFalse(world.citiesFull(1_000));
    }
}
