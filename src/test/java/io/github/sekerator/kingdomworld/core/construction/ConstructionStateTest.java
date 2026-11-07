package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The life of a construction: laid out only on the kingdom's own land, away from other constructions and buildings and
 * within the limit, with the right to manage construction; paused, hurried and cancelled; its steps done or waiting; its
 * site filled and emptied; and at the end entered into the register.
 */
class ConstructionStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final ConstructionRules RULES = new ConstructionRules(2, 64, 4.5, 12.0, 300, 40, 0.0, 48, 32, 48);

    private static KingdomRules kingdomRules() {
        return KingdomRules.DEFAULT;
    }

    private static UUID found(KingdomWorldState state, UUID king, int chunkX) {
        UUID id = UUID.randomUUID();
        CreateKingdomResult result = state.found(id, "Realm " + chunkX, 0x123456, king,
                new Capital(OVERWORLD, chunkX * 16 + 8, 64, 8), 0, kingdomRules());
        assertTrue(result instanceof CreateKingdomResult.Created, result.toString());
        return id;
    }

    private static KingdomWorldState.ConstructionChange lay(KingdomWorldState state, UUID actor, UUID kingdom, Box box) {
        return state.layConstruction(actor, kingdom, "test:house", 1L, new WorldPos(OVERWORLD, box.minX(), box.minY(), box.minZ()),
                Placement.NONE, box, List.of(), 10, 0, RULES);
    }

    @Test
    void aConstructionIsLaidOnlyOnOwnFreeLand() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        UUID kingdom = found(state, king, 0);
        Box inside = new Box(0, 64, 0, 6, 70, 6);
        assertTrue(lay(state, king, kingdom, inside).outcome().ok());
        assertEquals(KingdomError.CONSTRUCTION_OVERLAPS, lay(state, king, kingdom, new Box(6, 64, 6, 9, 66, 9)).outcome().error());
        assertEquals(KingdomError.CONSTRUCTION_OUTSIDE_LAND, lay(state, king, kingdom, new Box(30, 64, 0, 40, 66, 4)).outcome().error(),
                "chunk 2 is not land of the kingdom");
        assertTrue(lay(state, king, kingdom, new Box(-10, 64, -10, -5, 66, -5)).outcome().ok());
        assertEquals(KingdomError.TOO_MANY_CONSTRUCTIONS, lay(state, king, kingdom, new Box(10, 64, 10, 12, 66, 12)).outcome().error());
        assertEquals(2, state.construction().count(kingdom));
    }

    @Test
    void theRightToManageConstructionIsNeeded() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        UUID kingdom = found(state, king, 0);
        UUID subject = UUID.randomUUID();
        UUID steward = UUID.randomUUID();
        assertTrue(state.membership().apply(subject, kingdom, 0).ok());
        assertTrue(state.membership().acceptApplication(king, subject, 0).ok());
        assertTrue(state.membership().apply(steward, kingdom, 0).ok());
        assertTrue(state.membership().acceptApplication(king, steward, 0).ok());
        assertTrue(state.membership().setRole(king, steward, Role.STEWARD).ok());
        Box box = new Box(0, 64, 0, 4, 66, 4);
        assertEquals(KingdomError.NO_PERMISSION, lay(state, subject, kingdom, box).outcome().error());
        KingdomWorldState.ConstructionChange laid = lay(state, steward, kingdom, box);
        assertTrue(laid.outcome().ok());
        long id = laid.construction().orElseThrow().id();
        assertEquals(KingdomError.NO_PERMISSION, state.pauseConstruction(subject, kingdom, id, true).outcome().error());
        assertTrue(state.pauseConstruction(steward, kingdom, id, true).outcome().ok());
        assertEquals(KingdomError.UNCHANGED, state.pauseConstruction(steward, kingdom, id, true).outcome().error());
        assertTrue(state.setConstructionPriority(null, kingdom, id, Construction.Priority.HIGH).outcome().ok());
        assertTrue(state.setOrderMissing(king, kingdom, id, true).outcome().ok());
        assertEquals(KingdomError.UNKNOWN_CONSTRUCTION, state.cancelConstruction(king, kingdom, 999).outcome().error());
        assertTrue(state.cancelConstruction(king, kingdom, id).outcome().ok());
        assertTrue(state.construction().get(id).isEmpty());
        assertTrue(RolePermissions.defaults().has(Role.STEWARD, Permission.MANAGE_CONSTRUCTION));
        assertFalse(RolePermissions.defaults().has(Role.SUBJECT, Permission.MANAGE_CONSTRUCTION));
    }

    @Test
    void stepsAreDoneOrWaitAndNeverBoth() {
        Construction construction = new Construction(1, UUID.randomUUID(), "test:house", 1L, new WorldPos(OVERWORLD, 0, 0, 0), Placement.NONE,
                new Box(0, 0, 0, 1, 1, 1), 0, List.of(), 5);
        construction.markPending(2);
        construction.markPending(3);
        assertTrue(construction.isPending(2));
        construction.markDone(2);
        assertTrue(construction.isDone(2));
        assertFalse(construction.isPending(2), "a step placed in the world no longer waits");
        construction.markPending(2);
        assertFalse(construction.isPending(2), "a done step is never paid again");
        construction.markDone(9);
        assertEquals(1, construction.doneCount(), "steps beyond the plan are ignored");
        assertEquals(List.of(3), construction.pending());
    }

    @Test
    void theSiteGivesOnlyWhatItHolds() {
        Construction construction = new Construction(1, UUID.randomUUID(), "test:house", 1L, new WorldPos(OVERWORLD, 0, 0, 0), Placement.NONE,
                new Box(0, 0, 0, 1, 1, 1), 0, List.of(), 5);
        construction.deliver("minecraft:stone", 10);
        construction.deliver("minecraft:stone", 5);
        assertEquals(15, construction.delivered().get("minecraft:stone"));
        assertEquals(15, construction.takeDelivered("minecraft:stone", 20));
        assertTrue(construction.delivered().isEmpty());
        assertEquals(0, construction.takeDelivered("minecraft:stone", 1));
        construction.deliver("minecraft:glass", 2);
        assertTrue(construction.hasDelivered(Map.of("minecraft:glass", 2)));
        assertFalse(construction.hasDelivered(Map.of("minecraft:glass", 3)));
        assertEquals(Map.of("minecraft:glass", 2), construction.takeAllDelivered());
        assertTrue(construction.delivered().isEmpty());
    }

    @Test
    void restoringKeepsOnlyWhatFitsThePlan() {
        Construction construction = new Construction(4, UUID.randomUUID(), "test:house", 1L, new WorldPos(OVERWORLD, 0, 0, 0), Placement.NONE,
                new Box(0, 0, 0, 1, 1, 1), 0, List.of(new int[]{0, 1, 0}), 3);
        BitSet done = new BitSet();
        done.set(0);
        done.set(7);
        construction.restore(Construction.Priority.HIGH, true, true, done, List.of(0, 1, 5), Map.of("minecraft:stone", 3, "minecraft:dirt", -2),
                Map.of("minecraft:glass", 9L), Construction.Status.BLOCKED, new WorldPos(OVERWORLD, 0, 1, 0), 0.5);
        assertEquals(1, construction.doneCount());
        assertEquals(List.of(1), construction.pending(), "done and unknown steps do not wait");
        assertEquals(Map.of("minecraft:stone", 3), construction.delivered());
        assertEquals(Construction.Status.BLOCKED, construction.status());
        assertEquals(1, construction.clear().size());
    }

    @Test
    void aFinishedConstructionGoesIntoTheRegisterAndTheKingdomTakesItAlong() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        UUID kingdom = found(state, king, 0);
        Construction construction = lay(state, king, kingdom, new Box(0, 64, 0, 4, 66, 4)).construction().orElseThrow();
        Building building = state.construction().register(construction, "house", "housing", 500, false);
        state.construction().remove(construction.id());
        assertEquals("house", building.type());
        assertEquals(100, building.integrity());
        assertEquals(KingdomError.CONSTRUCTION_OVERLAPS, lay(state, king, kingdom, new Box(2, 64, 2, 3, 65, 3)).outcome().error(),
                "a building keeps its place");
        assertEquals(1, state.construction().buildingsOf(kingdom).size());
        assertTrue(lay(state, king, kingdom, new Box(6, 64, 6, 8, 66, 8)).outcome().ok());
        state.dissolve(kingdom);
        assertTrue(state.construction().all().isEmpty());
        assertTrue(state.construction().buildings().isEmpty());
    }
}
