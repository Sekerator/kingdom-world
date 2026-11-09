package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.BattleRules;
import io.github.sekerator.kingdomworld.core.combat.Fighter;
import io.github.sekerator.kingdomworld.core.combat.Flank;
import io.github.sekerator.kingdomworld.core.combat.Formation;
import io.github.sekerator.kingdomworld.core.combat.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldTest {
    private static final String WORLD = "minecraft:overworld";

    private static WorldPos at(int x, int z) {
        return new WorldPos(WORLD, x, 64, z);
    }

    // ---------------------------------------------------------------- the march

    @Test
    void aMarchPassesItsPointsInOrderAndFacesTheWayItGoes() {
        // Around an enemy at (100, 0): first to the north of it, then to its rear.
        March march = March.standing(at(0, 0)).towards(List.of(at(100, -60), at(160, 0)));
        assertFalse(march.arrived());
        double before = march.remaining();
        March after = march.step(50);
        assertEquals(before - 50, after.remaining(), 1.5);
        assertEquals(2, after.path().size(), "The first point is not reached yet");
        assertTrue(after.faceX() > 0 && after.faceZ() < 0, "The squad does not face its way");
        March past = after.step(80);
        assertEquals(1, past.path().size(), "The first point was not passed");
        assertEquals(at(160, 0), past.path().get(0));
        March done = past.step(1_000);
        assertTrue(done.arrived());
        assertEquals(at(160, 0), done.at());
        assertEquals(0, done.secondsLeft(3.0));
        assertEquals((long) Math.ceil(march.remaining() / 3.0), march.secondsLeft(3.0));
    }

    @Test
    void aMarchStopsBeforeAPointInAnotherDimension() {
        March march = March.standing(at(0, 0)).towards(List.of(new WorldPos("minecraft:the_nether", 10, 64, 10)));
        March after = march.step(100);
        assertTrue(after.arrived());
        assertEquals(at(0, 0), after.at());
    }

    @Test
    void ordersCarryTheirPointsOnTheWay() {
        Order order = Order.move(at(300, 0)).withVia(List.of(at(100, 100), at(200, 100)));
        assertEquals(2, order.via().size());
        assertTrue(Order.Kind.MOVE.marches());
        assertFalse(Order.Kind.ESCORT.marches());
        assertThrows(IllegalArgumentException.class, () -> Order.escort(4).withVia(List.of(at(1, 1))));
        List<WorldPos> many = new ArrayList<>();
        for (int i = 0; i <= Order.MAX_POINTS; i++) {
            many.add(at(i, i));
        }
        assertThrows(IllegalArgumentException.class, () -> Order.move(at(0, 0)).withVia(many));
        Order attack = Order.attack(at(10, 10), 7);
        assertEquals(7, attack.target());
        assertEquals(0, Order.move(at(1, 1)).target());
        assertTrue(Order.scout(at(0, 0), 2).scouts(at(40, -30)));
        assertFalse(Order.scout(at(0, 0), 2).scouts(at(80, 0)));
    }

    // ---------------------------------------------------------------- the way

    @Test
    void theWayBendsToARoadThatSavesTime() {
        // A road along z = 3 (chunks), from x = 0 to x = 40; the squad goes from chunk (0, 0) to chunk (40, 0).
        Set<Long> road = new java.util.HashSet<>();
        for (int x = -2; x <= 42; x++) {
            road.add(RouteSearch.key(x, 3));
        }
        RouteSearch search = new RouteSearch(at(8, 8), at(40 * 16 + 8, 8), road::contains, 1.0 / 1.5);
        int steps = 0;
        while (search.state() == RouteSearch.State.SEARCHING) {
            search.advance(50);
            steps++;
        }
        assertEquals(RouteSearch.State.FOUND, search.state());
        assertTrue(steps > 1, "The search did not go a little at a time");
        List<WorldPos> path = search.path();
        assertEquals(at(40 * 16 + 8, 8), path.get(path.size() - 1));
        assertTrue(path.stream().anyMatch(point -> point.chunkZ() == 3), "The way did not take the road: " + path);
    }

    @Test
    void withoutRoadsTheWayIsStraightAndAFarWayIsTheLine() {
        RouteSearch search = new RouteSearch(at(8, 8), at(20 * 16 + 8, 8), chunk -> false, 0.66);
        assertEquals(List.of(at(20 * 16 + 8, 8)), search.finish());
        RouteSearch far = new RouteSearch(at(0, 0), at(100_000, 0), chunk -> false, 0.66);
        assertEquals(RouteSearch.State.STRAIGHT, far.state());
        assertEquals(List.of(at(100_000, 0)), far.path());
    }

    // ---------------------------------------------------------------- flanks and formations

    @Test
    void aBlowAtTheSideOrTheRearOfAnEngagedSquadIsStronger() {
        // The squad at the origin faces its enemy to the east.
        assertEquals(Flank.FRONT, Flank.of(0, 0, 1, 0, 50, 5));
        assertEquals(Flank.SIDE, Flank.of(0, 0, 1, 0, 0, 50));
        assertEquals(Flank.SIDE, Flank.of(0, 0, 1, 0, 5, -40));
        assertEquals(Flank.REAR, Flank.of(0, 0, 1, 0, -50, 10));
        Flank.Rules rules = new Flank.Rules(0.2, 0.35);
        assertEquals(1.0, Flank.FRONT.edge(rules));
        assertEquals(1.2, Flank.SIDE.edge(rules), 1e-9);
        assertEquals(1.35, Flank.REAR.edge(rules), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new Flank.Rules(-0.1, 0.2));
    }

    @Test
    void anEdgeTipsAnEvenBattle() {
        List<Fighter> ours = new ArrayList<>();
        List<Fighter> theirs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ours.add(Fighter.of(UUID.randomUUID(), 1, 6, 5, 100));
            theirs.add(Fighter.of(UUID.randomUUID(), 1, 6, 5, 100));
        }
        Side first = Side.of(Side.Kind.SOLDIERS, ours);
        Side second = Side.of(Side.Kind.ARMY, theirs);
        Battle.Result even = Battle.resolve(first, 1.0, second, 1.0, BattleRules.DEFAULT, () -> 0.5);
        Battle.Result flanked = Battle.resolve(first, Formation.WEDGE.attack() * Flank.REAR.edge(Flank.Rules.DEFAULT), second,
                Formation.LINE.defense(), BattleRules.DEFAULT, () -> 0.5);
        assertEquals(0.5, even.chance(), 1e-9);
        assertTrue(flanked.chance() > 0.6, "A wedge at the rear did not tip the battle: " + flanked.chance());
        assertTrue(Formation.COLUMN.speed() > Formation.LINE.speed() && Formation.WEDGE.attack() > Formation.LINE.attack());
    }

    // ---------------------------------------------------------------- the fog of war

    @Test
    void squadsOfOthersShowOnlyInSightAndLeaveAPaleMark() {
        UUID us = UUID.randomUUID();
        UUID them = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        FogOfWar fog = new FogOfWar();
        List<FogOfWar.Eye> eyes = List.of(new FogOfWar.Eye(WORLD, 0, 0, 64));
        FogOfWar.Contact near = new FogOfWar.Contact(1, them, at(40, 30), 5);
        FogOfWar.Contact far = new FogOfWar.Contact(2, them, at(500, 0), 8);
        FogOfWar.Contact ally = new FogOfWar.Contact(3, friend, at(900, 900), 4);
        Set<Long> alive = Set.of(1L, 2L, 3L);
        FogOfWar.View first = fog.look(us, eyes, List.of(near, far, ally), Set.of(friend), alive, 100, 1_000);
        assertEquals(Set.of(1L, 3L), ids(first.visible()), "Only squads in sight and allies show");
        assertTrue(first.lastSeen().isEmpty());
        // The near squad walks away: a pale mark stays where it was last seen, with the time.
        FogOfWar.Contact gone = new FogOfWar.Contact(1, them, at(300, 300), 5);
        FogOfWar.View second = fog.look(us, eyes, List.of(gone, far, ally), Set.of(friend), alive, 200, 1_000);
        assertEquals(Set.of(3L), ids(second.visible()));
        assertEquals(1, second.lastSeen().size());
        assertEquals(at(40, 30), second.lastSeen().get(0).at());
        assertEquals(100, second.lastSeen().get(0).seen());
        // A scouted area sees the far squad; the mark of a squad fades with time.
        List<FogOfWar.Eye> scouted = List.of(new FogOfWar.Eye(WORLD, 0, 0, 64), new FogOfWar.Eye(WORLD, 480, 0, 80));
        FogOfWar.View third = fog.look(us, scouted, List.of(gone, far), Set.of(), alive, 1_500, 1_000);
        assertEquals(Set.of(2L), ids(third.visible()));
        assertTrue(third.lastSeen().isEmpty(), "An old mark did not fade");
        FogOfWar.View fourth = fog.look(us, eyes, List.of(gone, far), Set.of(), Set.of(1L), 1_600, 1_000);
        assertTrue(fourth.lastSeen().isEmpty(), "The mark of a squad that is gone stayed");
        assertFalse(new FogOfWar.Eye("minecraft:the_nether", 0, 0, 64).sees(at(1, 1)));
    }

    private static Set<Long> ids(List<FogOfWar.Contact> contacts) {
        Set<Long> ids = new java.util.HashSet<>();
        contacts.forEach(contact -> ids.add(contact.squadId()));
        return ids;
    }
}
