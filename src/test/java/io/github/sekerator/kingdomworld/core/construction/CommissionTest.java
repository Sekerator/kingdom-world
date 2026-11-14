package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orders of the king from afar: the places of a city lie on rings around its middle, the nearest first, a building turns
 * its entrance towards the town hall, the orders the constructions and the orders from afar give the workshops never count
 * against the limit of the orders of the king, and the kinds and states of an order are known by their ids.
 */
class CommissionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final ConstructionRules RULES = new ConstructionRules(0, 64, 4.5, 12.0, 300, 40, 0.0, 48, 32, 48);

    @Test
    void theRingsGoOutFromTheMiddle() {
        List<int[]> first = PlotRings.ring(100, 200, PlotRings.STEP);
        assertEquals(8, first.size(), "a ring of radius 4 has a point every 4 blocks along its sides");
        for (int[] point : first) {
            assertEquals(PlotRings.STEP, Math.max(Math.abs(point[0] - 100), Math.abs(point[1] - 200)), "every point lies on the ring");
        }
        Set<Long> seen = new HashSet<>();
        for (int[] point : first) {
            assertTrue(seen.add(((long) point[0] << 32) ^ point[1]), "no point twice");
        }
        List<int[]> all = PlotRings.around(100, 200, 12);
        assertEquals(8 + 16 + 24, all.size());
        int last = 0;
        for (int[] point : all) {
            int distance = Math.max(Math.abs(point[0] - 100), Math.abs(point[1] - 200));
            assertTrue(distance >= last, "the nearest ring first");
            last = distance;
        }
        assertTrue(PlotRings.around(0, 0, 3).isEmpty(), "no ring fits inside a radius smaller than a step");
    }

    @Test
    void aBuildingStaysInsideTheSquareOfItsCity() {
        assertTrue(PlotRings.inside(new Box(-10, 60, -10, 10, 70, 10), 0, 0, 10));
        assertFalse(PlotRings.inside(new Box(-10, 60, -10, 11, 70, 10), 0, 0, 10));
        assertFalse(PlotRings.inside(new Box(-11, 60, -10, 10, 70, 10), 0, 0, 10));
        assertFalse(PlotRings.inside(new Box(0, 60, 0, 5, 70, 11), 0, 0, 10));
    }

    @Test
    void theEntranceTurnsTowardsTheHall() {
        for (Facing entrance : Facing.values()) {
            assertEquals(Facing.SOUTH, Placement.toward(entrance, 0, 9).apply(entrance), "the hall lies to the south of " + entrance);
            assertEquals(Facing.NORTH, Placement.toward(entrance, 1, -9).apply(entrance), "the hall lies mostly to the north");
            assertEquals(Facing.EAST, Placement.toward(entrance, 9, -1).apply(entrance), "the hall lies mostly to the east");
            assertEquals(Facing.WEST, Placement.toward(entrance, -9, 3).apply(entrance));
            assertFalse(Placement.toward(entrance, 5, 5).mirror(), "an order is never mirrored");
        }
    }

    @Test
    void theOrdersOfWorkDoNotCountAgainstTheKing() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        UUID kingdom = UUID.randomUUID();
        CreateKingdomResult result = state.found(kingdom, "Realm", 0x123456, king, new Capital(OVERWORLD, 8, 64, 8), 0, KingdomRules.DEFAULT);
        assertTrue(result instanceof CreateKingdomResult.Created, result.toString());
        Box box = new Box(0, 64, 0, 4, 68, 4);
        Construction construction = state.layConstruction(king, kingdom, "test:house", 1L, new WorldPos(OVERWORLD, 0, 64, 0), Placement.NONE, box,
                List.of(), 10, 0, RULES).construction().orElseThrow();

        // The construction orders planks, an order from afar orders a work post: neither is an order of the king.
        long planks = order(state, kingdom, "minecraft:oak_planks", Integer.MAX_VALUE);
        construction.putOrder("minecraft:oak_planks", planks);
        long post = order(state, kingdom, "kingdomworld:work_post", Integer.MAX_VALUE);
        state.construction().addCommission(new Commission(0, kingdom, Commission.Kind.POST, "", Materials.NONE, 0, OVERWORLD, 0, 0, 0,
                Construction.Priority.HIGH, king, Commission.Origin.KING, 0, Commission.State.LACKS_ITEM, 0, "item|kingdomworld:work_post", "",
                false, 600, post));
        assertEquals(0, state.kingOrders(kingdom));

        // The king still has his one order, and only one.
        assertTrue(order(state, kingdom, "minecraft:bread", 1) > 0);
        assertEquals(1, state.kingOrders(kingdom));
        assertEquals(KingdomError.TOO_MANY_ORDERS, state.order(null, kingdom, "minecraft:torch", Order.Kind.MAKE, 1, Order.Priority.NORMAL, 0, 1,
                id -> true).outcome().error());
    }

    private static long order(KingdomWorldState state, UUID kingdom, String item, int maxOrders) {
        KingdomWorldState.Ordering ordering = state.order(null, kingdom, item, Order.Kind.MAKE, 1, Order.Priority.NORMAL, 0, maxOrders, id -> true);
        assertTrue(ordering.outcome().ok(), String.valueOf(ordering.outcome().error()));
        return ordering.order().orElseThrow().id();
    }

    @Test
    void kindsAndStatesAreKnownByTheirIds() {
        for (Commission.Kind kind : Commission.Kind.values()) {
            assertEquals(kind, Commission.Kind.byId(kind.id()));
        }
        for (Commission.State state : Commission.State.values()) {
            assertEquals(state, Commission.State.byId(state.id()));
        }
        for (Commission.Origin origin : Commission.Origin.values()) {
            assertEquals(origin, Commission.Origin.byId(origin.id()));
        }
        assertNull(Commission.Kind.byId("castle_in_the_air"), "a kind of a later version is not known");
        assertNull(Commission.State.byId(""));
    }

    @Test
    void theStateOfAnOrderChangesAlone() {
        Commission waiting = new Commission(7, UUID.randomUUID(), Commission.Kind.BUILD, "kingdomworld:house", Materials.NONE, 3, OVERWORLD, 0, 0, 5,
                null, null, null, 100, null, 0, null, null, false, 0, 0);
        assertEquals(1, waiting.rotation(), "quarter turns are kept within a turn");
        assertEquals(Construction.Priority.NORMAL, waiting.priority());
        assertEquals(Commission.State.WAITING, waiting.state());
        Commission levelling = waiting.withAfter(42);
        assertEquals(Commission.State.LEVELLING, levelling.state());
        assertEquals(42, levelling.after());
        Commission lacking = waiting.withState(Commission.State.LACKS_ITEM, "x".repeat(1000), 600).withOrder(9);
        assertEquals(Commission.MAX_REASON, lacking.reason().length());
        assertEquals(9, lacking.order());
        assertEquals(waiting.created(), lacking.created());
        assertEquals(7, lacking.id());
    }
}
