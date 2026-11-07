package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work on buildings of the register: the difference of two templates for an upgrade, the plan of a repair, and what a
 * demolition gives back after the loss.
 */
class BuildingWorkTest {

    private static TemplateDiff.Cell cell(int x, int y, int z, String state) {
        return new TemplateDiff.Cell(x, y, z, state, BlockCost.Kind.ONE);
    }

    private static TemplateDiff.Cell air(int x, int y, int z) {
        return new TemplateDiff.Cell(x, y, z, "air", BlockCost.Kind.NOTHING);
    }

    @Test
    void anUpgradeTouchesOnlyWhatDiffers() {
        List<TemplateDiff.Cell> before = List.of(cell(0, 0, 0, "cobblestone"), cell(1, 0, 0, "oak_planks"), cell(2, 0, 0, "oak_planks"),
                cell(3, 0, 0, "torch"), air(0, 1, 0));
        List<TemplateDiff.Cell> after = List.of(cell(0, 0, 0, "cobblestone"), cell(1, 0, 0, "stone_bricks"), air(2, 0, 0),
                cell(0, 1, 0, "lantern"), cell(5, 0, 0, "red_bed"));
        List<Map<String, Integer>> costs = List.of(Map.of("cobblestone", 1), Map.of("stone_bricks", 1), Map.of(), Map.of("lantern", 1),
                Map.of("red_bed", 1));
        TemplateDiff.Result diff = TemplateDiff.of(before, after, costs);
        // The same cobblestone stays; air where nothing stood stays too.
        assertTrue(diff.skip().get(0));
        assertFalse(diff.skip().get(1));
        assertTrue(diff.skip().get(2) || after.get(2).kind() == BlockCost.Kind.NOTHING);
        assertFalse(diff.skip().get(3));
        assertFalse(diff.skip().get(4));
        // Only the difference is paid for.
        assertEquals(Map.of("stone_bricks", 1, "lantern", 1, "red_bed", 1), diff.cost());
        // Planks where the new template wants air and the torch it does not have go.
        List<String> cleared = new ArrayList<>();
        for (int[] pos : diff.clear()) {
            cleared.add(pos[0] + "," + pos[1] + "," + pos[2]);
        }
        assertEquals(List.of("2,0,0", "3,0,0"), cleared);
    }

    @Test
    void theSameTemplateNeedsNothing() {
        List<TemplateDiff.Cell> cells = List.of(cell(0, 0, 0, "a"), cell(0, 1, 0, "b"),
                new TemplateDiff.Cell(0, 2, 0, "door_upper", BlockCost.Kind.PART));
        TemplateDiff.Result diff = TemplateDiff.of(cells, cells, List.of(Map.of("a", 1), Map.of("b", 1), Map.of()));
        assertEquals(3, diff.skip().cardinality());
        assertTrue(diff.clear().isEmpty());
        assertTrue(diff.cost().isEmpty());
    }

    @Test
    void aRepairPutsBackOnlyTheDamagedBlocks() {
        BitSet damaged = new BitSet();
        damaged.set(1);
        damaged.set(3);
        BitSet skip = Repairs.skip(4, damaged);
        assertTrue(skip.get(0));
        assertFalse(skip.get(1));
        assertTrue(skip.get(2));
        assertFalse(skip.get(3));
        List<Map<String, Integer>> costs = List.of(Map.of("cobblestone", 1), Map.of("oak_planks", 1), Map.of("oak_planks", 1),
                Map.of("glass_pane", 1));
        assertEquals(Map.of("oak_planks", 1, "glass_pane", 1), Repairs.cost(costs, damaged));

        List<PlacementOrder.Block> blocks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (!skip.get(i)) {
                blocks.add(new PlacementOrder.Block(i, i, 0, 0, PlacementOrder.Phase.STANDING));
            }
        }
        BuildPlan plan = BuildPlan.of(List.of(), blocks, costs);
        assertEquals(2, plan.places());
        Construction repair = new Construction(1, UUID.randomUUID(), "test:house", 1L, new WorldPos("minecraft:overworld", 0, 0, 0),
                Placement.NONE, new Box(0, 0, 0, 3, 0, 0), 0, List.of(), plan.size(), Construction.Kind.REPAIR, 5, Materials.NONE, skip);
        assertEquals(Map.of("oak_planks", 1, "glass_pane", 1), plan.remaining(repair));
        assertEquals(Construction.Kind.REPAIR, repair.kind());
        assertEquals(5, repair.building());
        assertTrue(repair.skips(0));
    }

    @Test
    void aDemolitionGivesBackTheMaterialsLessTheLoss() {
        assertEquals(75, Salvage.total(100, 25));
        assertEquals(0, Salvage.total(3, 100));
        assertEquals(3, Salvage.total(3, 0));
        // Block by block it gives back exactly what it would in one go.
        Construction demolition = new Construction(2, UUID.randomUUID(), "test:house", 1L, new WorldPos("minecraft:overworld", 0, 0, 0),
                Placement.NONE, new Box(0, 0, 0, 3, 3, 3), 0, List.of(), 10, Construction.Kind.DEMOLISH, 7, Materials.NONE, new BitSet());
        int back = 0;
        for (int i = 0; i < 10; i++) {
            back += demolition.salvage(Map.of("oak_planks", 1), 25).getOrDefault("oak_planks", 0);
        }
        assertEquals(Salvage.total(10, 25), back);
        assertEquals(10, demolition.removed().get("oak_planks"));
        assertEquals(Map.of("stone", 3), Repairs.salvage(Map.of("stone", 4, "torch", 1), 25));
    }

    @Test
    void aDemolitionTakesLeaningBlocksFirstFromTheTop() {
        BuildPlan plan = BuildPlan.demolition(List.of(new int[]{0, 1, 0}, new int[]{0, 3, 0}),
                List.of(new int[]{0, 0, 0}, new int[]{0, 2, 0}, new int[]{0, 5, 0}));
        List<Integer> heights = new ArrayList<>();
        for (BuildPlan.Step step : plan.steps()) {
            assertEquals(BuildPlan.Kind.CLEAR, step.kind());
            heights.add(step.y());
        }
        assertEquals(List.of(3, 1, 5, 2, 0), heights);
        Construction construction = new Construction(3, UUID.randomUUID(), "test:house", 1L, new WorldPos("minecraft:overworld", 0, 0, 0),
                Placement.NONE, new Box(0, 0, 0, 0, 5, 0), 0, List.of(), plan.size(), Construction.Kind.DEMOLISH, 7, Materials.NONE, new BitSet());
        assertEquals(0, plan.percent(construction));
        construction.markDone(0);
        construction.markDone(1);
        assertEquals(40, plan.percent(construction));
    }

    @Test
    void savedBlueprintsAreChecked() {
        ConstructionRules rules = ConstructionRules.DEFAULT;
        assertTrue(CustomBlueprint.check("Hut", BuildingRoles.HOUSE, 5, 4, 5, rules, 40, 0, 1, 0, 0, 16).isEmpty());
        assertEquals(CustomBlueprint.Problem.NEEDS_BED, CustomBlueprint.check("Hut", BuildingRoles.HOUSE, 5, 4, 5, rules, 40, 0, 0, 3, 0, 16)
                .orElseThrow());
        assertEquals(CustomBlueprint.Problem.NEEDS_CONTAINER, CustomBlueprint.check("Barn", BuildingRoles.WAREHOUSE, 5, 4, 5, rules, 40, 0, 2, 0,
                0, 16).orElseThrow());
        assertTrue(CustomBlueprint.check("Barn", BuildingRoles.WAREHOUSE, 5, 4, 5, rules, 40, 0, 0, 1, 0, 16).isEmpty());
        assertEquals(CustomBlueprint.Problem.TOO_BIG, CustomBlueprint.check("Tower", "", 5, 99, 5, rules, 40, 0, 0, 0, 0, 16).orElseThrow());
        assertEquals(CustomBlueprint.Problem.UNBUILDABLE, CustomBlueprint.check("Vault", "", 5, 4, 5, rules, 40, 2, 0, 0, 0, 16).orElseThrow());
        assertEquals(CustomBlueprint.Problem.EMPTY, CustomBlueprint.check("Air", "", 5, 4, 5, rules, 0, 0, 0, 0, 0, 16).orElseThrow());
        assertEquals(CustomBlueprint.Problem.BAD_NAME, CustomBlueprint.check("  ", "", 5, 4, 5, rules, 40, 0, 0, 0, 0, 16).orElseThrow());
        assertEquals(CustomBlueprint.Problem.BAD_NAME, CustomBlueprint.check("x".repeat(33), "", 5, 4, 5, rules, 40, 0, 0, 0, 0, 16)
                .orElseThrow());
        assertEquals(CustomBlueprint.Problem.UNKNOWN_ROLE, CustomBlueprint.check("Odd", "no_role", 5, 4, 5, rules, 40, 0, 0, 0, 0, 16)
                .orElseThrow());
        assertEquals(CustomBlueprint.Problem.TOO_MANY, CustomBlueprint.check("One more", "", 5, 4, 5, rules, 40, 0, 0, 0, 16, 16).orElseThrow());
    }

    @Test
    void savedBlueprintsKeepTheirBlocks() {
        UUID kingdom = UUID.randomUUID();
        int[] blocks = new int[2 * 2 * 2];
        java.util.Arrays.fill(blocks, -1);
        blocks[0] = 0;
        blocks[7] = 1;
        CustomBlueprint blueprint = new CustomBlueprint(3, kingdom, "Shed", "farming", BuildingRoles.WAREHOUSE, 2, 2, 2,
                List.of("minecraft:oak_planks", "minecraft:chest[facing=north,type=single,waterlogged=false]"), blocks, 1, 0, 1, Facing.SOUTH, 10L,
                UUID.randomUUID());
        assertEquals(0, blueprint.at(0, 0, 0));
        assertEquals(1, blueprint.at(1, 1, 1));
        assertEquals(-1, blueprint.at(1, 0, 0));
        assertEquals("kingdomworld:custom/3", blueprint.templateId());
        assertEquals(3L, CustomBlueprint.number(blueprint.templateId()).orElseThrow());
        assertTrue(CustomBlueprint.number("kingdomworld:small_house").isEmpty());
        assertTrue(CustomBlueprint.number("kingdomworld:custom/x").isEmpty());
        assertEquals("Barn", blueprint.renamed("Barn").name());
        assertArrayEquals(blocks, blueprint.renamed("Barn").blocks());

        ConstructionBook book = new ConstructionBook();
        book.addBlueprint(new CustomBlueprint(1, kingdom, "First", "housing", "", 2, 2, 2, blueprint.palette(), blocks, 0, 0, 0, Facing.SOUTH, 1L,
                kingdom));
        assertEquals(2, book.nextBlueprintId());
        assertEquals(1, book.blueprintsOf(kingdom).size());
        int revision = book.blueprintRevision();
        book.removeBlueprint(1);
        assertTrue(book.blueprintRevision() > revision);
        assertTrue(book.blueprintsOf(kingdom).isEmpty());
    }
}
