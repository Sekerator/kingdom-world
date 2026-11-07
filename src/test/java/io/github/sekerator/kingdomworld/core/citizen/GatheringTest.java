package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Risks of work, zones and the mine plan, the choice of tools and the work done far from players.
 */
class GatheringTest {
    private static final String OVERWORLD = "minecraft:overworld";

    // ---------------------------------------------------------------- risks

    @Test
    void armorAndExperienceLowerTheRisk() {
        assertEquals(1.0, TaskRisks.protection(0.0f, 0), 1e-9);
        assertEquals(0.25, TaskRisks.protection(TaskRisks.FULL_ARMOR, 0), 1e-9);
        assertEquals(0.5, TaskRisks.protection(0.0f, TaskRisks.VETERAN_EXPERIENCE * 3), 1e-9);
        assertEquals(0.125, TaskRisks.protection(100.0f, 100_000), 1e-9);
        assertTrue(TaskRisks.protection(10.0f, 0) < 1.0 && TaskRisks.protection(10.0f, 0) > 0.25);
    }

    @Test
    void riskGrowsWithTheLengthOfTheStep() {
        assertEquals(0.01, TaskRisks.overStep(0.01, CitizenNeeds.REFERENCE_STEP_TICKS), 1e-12);
        assertEquals(1.0 - 0.99 * 0.99, TaskRisks.overStep(0.01, 2 * CitizenNeeds.REFERENCE_STEP_TICKS), 1e-12);
        assertEquals(0.0, TaskRisks.overStep(0.0, 72_000), 1e-12);
        assertEquals(1.0, TaskRisks.overStep(1.0, 20), 1e-12);
    }

    @Test
    void accidentsFollowTheRolls() {
        TaskRisks.Risk risk = new TaskRisks.Risk(0.1, 0.01);
        int step = CitizenNeeds.REFERENCE_STEP_TICKS;
        assertEquals(TaskRisks.Accident.DEATH, TaskRisks.roll(risk, 1.0, step, 0.0f, 0, 0.005, 0.9));
        assertEquals(TaskRisks.Accident.INJURY, TaskRisks.roll(risk, 1.0, step, 0.0f, 0, 0.5, 0.05));
        assertEquals(TaskRisks.Accident.NONE, TaskRisks.roll(risk, 1.0, step, 0.0f, 0, 0.5, 0.5));
        // Full armor turns the same roll from an injury into nothing.
        assertEquals(TaskRisks.Accident.NONE, TaskRisks.roll(risk, 1.0, step, TaskRisks.FULL_ARMOR, 0, 0.5, 0.05));
        // The multiplier 0 switches accidents off, a larger one makes them likelier.
        assertEquals(TaskRisks.Accident.NONE, TaskRisks.roll(risk, 0.0, step, 0.0f, 0, 0.0, 0.0));
        assertEquals(TaskRisks.Accident.DEATH, TaskRisks.roll(risk, 3.0, step, 0.0f, 0, 0.025, 0.9));
    }

    @Test
    void injuriesLeaveTheWorkerWounded() {
        float health = TaskRisks.injuredHealth(20.0f, 0.5f);
        assertTrue(health < Citizen.MAX_HEALTH * 0.5f && health >= 1.0f);
        assertEquals(2.0f, TaskRisks.injuredHealth(2.0f, 0.5f), 1e-6, "an injury never heals");
    }

    @Test
    void riskSettingsAreRead() {
        assertEquals(new TaskRisks.Risk(0.004, 0.0004), TaskRisks.parse("miner:0.004:0.0004").orElseThrow().getValue());
        assertTrue(TaskRisks.parse("miner:2:0").isEmpty());
        assertTrue(TaskRisks.parse("Miner:0.1:0").isEmpty());
        assertTrue(TaskRisks.parse("miner:x:0").isEmpty());
        assertTrue(TaskRisks.parse("miner").isEmpty());
        Map<String, TaskRisks.Risk> parsed = TaskRisks.parseAll(TaskRisks.defaultSettings());
        assertEquals(TaskRisks.DEFAULTS, parsed);
        assertTrue(TaskRisks.defaultSettings().stream().noneMatch(text -> text.contains("E")), "settings are written plainly");
        assertTrue(parsed.get("miner").death() > parsed.get("fisher").death(), "mining is the most dangerous work");
    }

    // ---------------------------------------------------------------- zones and the mine

    @Test
    void zonesFitTheProfession() {
        WorldPos post = new WorldPos(OVERWORLD, 100, 64, 100);
        WorkZone mine = WorkZone.of(post, "miner", 7, 4);
        assertTrue(mine.minY() < 64 - GatheringRules.MINE_LEVEL_STEP, "a mine goes down");
        assertTrue(mine.maxY() <= 64 + 3);
        WorkZone forest = WorkZone.of(post, "lumberjack", 7, 4);
        assertTrue(forest.maxY() >= 64 + 30, "a lumberjack looks into the crowns");
        assertEquals(15, forest.width());
        assertTrue(forest.contains(OVERWORLD, 107, 70, 93));
        assertFalse(forest.contains(OVERWORLD, 108, 70, 93));
        assertFalse(forest.contains("minecraft:the_nether", 100, 64, 100));
    }

    @Test
    void zonesStayOnTheLand() {
        // The land: chunks 5..7 along x and 5..7 along z.
        WorldPos post = new WorldPos(OVERWORLD, 6 * 16 + 8, 64, 6 * 16 + 8);
        Set<Long> land = new HashSet<>();
        for (int x = 5; x <= 7; x++) {
            for (int z = 5; z <= 7; z++) {
                land.add(ChunkKey.of(x, z));
            }
        }
        // The post stands 24 blocks from the western edge and 23 from the eastern one.
        assertEquals(23, WorkZone.largestRadiusWithin(post, 64, land::contains));
        assertTrue(WorkZone.around(post, 23, 0, 0).liesWithin(land::contains));
        assertFalse(WorkZone.around(post, 24, 0, 0).liesWithin(land::contains));
        assertTrue(WorkZone.around(post, 24, 0, 0).touches(land::contains));
        assertEquals(0, WorkZone.largestRadiusWithin(new WorldPos(OVERWORLD, 0, 64, 0), 8, land::contains));
        assertEquals(8, GatheringRules.DEFAULT.radiusForNewPost(23));
        assertEquals(3, GatheringRules.DEFAULT.radiusForNewPost(3));
        assertEquals(1, GatheringRules.DEFAULT.radiusForNewPost(0));
    }

    @Test
    void theMineIsDugFromItsEntrance() {
        List<MinePlan.Cell> cells = MinePlan.cells(0, 60, 0, 7, 4);
        assertEquals(3, MinePlan.levels(7, 4));
        assertEquals(1, MinePlan.levels(7, 1));
        assertEquals(1, MinePlan.levels(2, 4));
        MinePlan.Cell first = cells.get(0);
        assertEquals(-1, first.from());
        assertEquals(0, first.x());
        assertEquals(1, first.z(), "the mine starts in front of the post");
        Set<String> seen = new HashSet<>();
        int lowest = 60;
        for (int i = 0; i < cells.size(); i++) {
            MinePlan.Cell cell = cells.get(i);
            assertTrue(seen.add(cell.x() + "," + cell.y() + "," + cell.z()), "a cell is planned twice: " + cell);
            assertTrue(Math.abs(cell.x()) <= 7 && Math.abs(cell.z()) <= 7, "a cell leaves the zone: " + cell);
            assertTrue(cell.y() <= 60, "a mine never goes up");
            assertTrue(cell.x() != 0 || cell.z() != 0 || cell.y() != 60, "the miner digs away its own post");
            if (i > 0) {
                assertTrue(cell.from() >= 0 && cell.from() < i, "a cell is entered from one dug before it");
                MinePlan.Cell from = cells.get(cell.from());
                int dx = Math.abs(cell.x() - from.x());
                int dz = Math.abs(cell.z() - from.z());
                assertEquals(1, dx + dz, "neighbouring cells touch side by side: " + from + " -> " + cell);
                assertTrue(Math.abs(cell.y() - from.y()) <= 1);
                if (cell.y() < from.y()) {
                    assertEquals(MinePlan.STAIR_HEIGHT, cell.height(), "stepping down needs a high cell");
                }
            }
            lowest = Math.min(lowest, cell.y());
        }
        assertEquals(60 - 2 * GatheringRules.MINE_LEVEL_STEP, lowest);
    }

    // ---------------------------------------------------------------- tools

    @Test
    void theWeakestSufficientToolIsTaken() {
        List<ToolChoice.Candidate> tools = List.of(new ToolChoice.Candidate(0, 3, 900), new ToolChoice.Candidate(1, 2, 200),
                new ToolChoice.Candidate(2, 2, 50), new ToolChoice.Candidate(3, 1, 10));
        assertEquals(2, ToolChoice.choose(tools, 2).getAsInt(), "iron is enough, the most worn iron goes first");
        assertEquals(0, ToolChoice.choose(tools, 3).getAsInt());
        assertEquals(3, ToolChoice.choose(tools, 0).getAsInt());
        assertEquals(0, ToolChoice.choose(tools, 4).getAsInt(), "with nothing strong enough the strongest is taken");
        assertTrue(ToolChoice.choose(List.of(), 0).isEmpty());
    }

    // ---------------------------------------------------------------- work far from players

    @Test
    void abstractWorkFollowsTheRates() {
        PostYield rates = new PostYield(Map.of("minecraft:cobblestone", 240.0, "minecraft:coal", 12.0), Map.of(), 252.0, true);
        Map<String, Double> progress = new LinkedHashMap<>();
        int interval = CitizenNeeds.REFERENCE_STEP_TICKS;
        int cobblestone = 0;
        int coal = 0;
        int actions = 0;
        // A day of twenty steps by two workers gives twice the daily rate.
        for (int step = 0; step < 20; step++) {
            GatheringRules.Output output = GatheringRules.abstractOutput(rates, 2, interval, 1.0, progress);
            cobblestone += output.items().getOrDefault("minecraft:cobblestone", 0);
            coal += output.items().getOrDefault("minecraft:coal", 0);
            actions += output.actions();
            progress = new LinkedHashMap<>(output.progress());
        }
        assertEquals(480, cobblestone);
        assertEquals(24, coal);
        assertEquals(504, actions);
        GatheringRules.Output alone = GatheringRules.abstractOutput(rates, 1, interval, GatheringRules.SELF_CARRY_FACTOR, Map.of());
        assertEquals(9, alone.items().get("minecraft:cobblestone"), "carrying it oneself gathers less");
        assertTrue(GatheringRules.abstractOutput(rates, 0, interval, 1.0, Map.of()).items().isEmpty());
    }

    @Test
    void inputsLimitTheWork() {
        PostYield herd = new PostYield(Map.of("minecraft:beef", 20.0), Map.of("minecraft:wheat", 10.0), 10.0, true);
        Map<String, Integer> needed = GatheringRules.abstractInputs(herd, 1, CitizenNeeds.REFERENCE_STEP_TICKS, 1.0);
        assertEquals(Map.of("minecraft:wheat", 1), needed);
        assertEquals(1.0, GatheringRules.inputShare(needed, Map.of("minecraft:wheat", 5)), 1e-9);
        assertEquals(0.0, GatheringRules.inputShare(needed, Map.of()), 1e-9);
        assertEquals(0.5, GatheringRules.inputShare(Map.of("a", 4), Map.of("a", 2)), 1e-9);
    }

    @Test
    void measurementsReplaceEstimatesAndThenAverage() {
        PostYield estimate = new PostYield(Map.of("minecraft:oak_log", 30.0), Map.of(), 30.0, false);
        PostYield sample = PostYield.measure(Map.of("minecraft:oak_log", 20L), Map.of(), 20L, GatheringRules.WORK_TICKS_PER_DAY / 10);
        assertEquals(200.0, sample.outputs().get("minecraft:oak_log"), 1e-9);
        PostYield first = estimate.blend(sample, GatheringRules.MEASURE_WEIGHT);
        assertTrue(first.measured());
        assertEquals(200.0, first.outputs().get("minecraft:oak_log"), 1e-9, "the first measurement replaces the estimate");
        PostYield second = first.blend(PostYield.measure(Map.of("minecraft:oak_log", 10L), Map.of(), 10L,
                GatheringRules.WORK_TICKS_PER_DAY / 10), GatheringRules.MEASURE_WEIGHT);
        assertEquals(150.0, second.outputs().get("minecraft:oak_log"), 1e-9);
        assertEquals(PostYield.NONE, PostYield.measure(Map.of("a", 1L), Map.of(), 1L, 0L));
        assertEquals(250.0, new PostYield(Map.of("a", 200.0, "b", 50.0, "c", -1.0), Map.of(), 0.0, true).total(), 1e-9);
    }

    @Test
    void carriersFarFromPlayersMoveByCount() {
        GatheringRules rules = GatheringRules.DEFAULT;
        assertEquals(2 * rules.carrierItemsPerMinute(), rules.carried(2, CitizenNeeds.REFERENCE_STEP_TICKS));
        assertEquals(0, rules.carried(0, CitizenNeeds.REFERENCE_STEP_TICKS));
        assertEquals(rules.carrierItemsPerMinute() * 5, rules.carried(1, 5 * CitizenNeeds.REFERENCE_STEP_TICKS));
    }
}
