package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A levelled road in saved data: its layout, survey, credit and earthworks read back as they were written; a road written before
 * reads as one of the first layout; a survey that does not fit reads as nothing without making the data read-only.
 */
class LevelledRoadNbtTest {
    private static final UUID US = new UUID(3, 3);

    private static List<RouteCell> cells(int count) {
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RouteCell.Kind kind = i % 40 < 10 ? RouteCell.Kind.TUNNEL : i % 40 < 20 ? RouteCell.Kind.CUT : RouteCell.Kind.GROUND;
            cells.add(new RouteCell(i, 70, 0, kind, kind == RouteCell.Kind.GROUND ? 0 : 6, null));
        }
        return cells;
    }

    private static Road levelled(RoadState state, List<RouteCell> cells) {
        int units = RoadLayout.blocks(cells, RoadClass.GRAVEL, RoadLayout.LEVELLED, null).size();
        List<RoadLayout.ColumnSurvey> surveys = new ArrayList<>();
        for (int i = 0; i < units; i++) {
            surveys.add(new RoadLayout.ColumnSurvey(i % 3, 300 + i % 7, i % 11 == 0 ? 400 : 0, 0, i % 50 == 0 ? 280 : 0, i % 5,
                    i % 8 == 4, i == 7));
        }
        Road road = new Road(state.nextRoadId(), US, RoadClass.GRAVEL, "minecraft:overworld", Endpoint.point(0, 0),
                Endpoint.point(cells.size(), 0), cells, Road.Mode.OWN, 10, Road.Status.BUILDING, List.of(), RoadLayout.LEVELLED, surveys);
        road.setEarthTotal(5_000);
        road.addEarthDone(1_250);
        road.addCredit("minecraft:cobblestone", 40);
        road.useCredit("minecraft:cobblestone", 15);
        road.addCredit("minecraft:dirt", 3);
        road.settleOwed("minecraft:dirt", 1);
        Road.Section first = road.section(0);
        for (int i = 0; i < 5; i++) {
            first.markPaid(Map.of());
        }
        first.markPlaced();
        first.markPlaced();
        first.addWork(7.5, 12.25);
        state.putRoad(road);
        return road;
    }

    @Test
    void aLevelledRoadReadsBackAsItWas() {
        RoadState state = new RoadState();
        Road road = levelled(state, cells(10_000));
        state.setMinersHelp(US, false);
        CompoundTag tag = new CompoundTag();
        RoadNbt.write(state, tag);
        RoadState read = new RoadState();
        List<String> problems = RoadNbt.read(tag, read);
        assertTrue(problems.isEmpty(), problems.toString());
        Road back = read.road(road.id()).orElseThrow();
        assertTrue(back.levelled());
        assertEquals(10_000, back.length(), "a road longer than the old limit of 8192");
        assertEquals(road.cells(), back.cells());
        assertEquals(road.units(), back.units());
        for (int i = 0; i < road.units(); i++) {
            RoadLayout.Block before = road.blocks().get(i);
            RoadLayout.Block after = back.blocks().get(i);
            assertEquals(before.role(), after.role());
            assertEquals(i < 2 ? RoadLayout.ColumnSurvey.EMPTY : before.survey(), after.survey(),
                    "a placed block is written as nothing, the others as they were: block " + i);
        }
        assertEquals(5_000, back.earthTotal(), 1e-9);
        assertEquals(1_250, back.earthDone(), 1e-9);
        assertEquals(Map.of("minecraft:cobblestone", 25, "minecraft:dirt", 3), back.credit());
        assertEquals(Map.of("minecraft:cobblestone", 40, "minecraft:dirt", 2), back.owed());
        assertEquals(7.5, back.section(0).builderWork(), 1e-9);
        assertEquals(12.25, back.section(0).minerWork(), 1e-9);
        assertEquals(5, back.section(0).paid());
        assertEquals(2, back.section(0).placed());
        assertFalse(read.minersHelp(US));
        assertTrue(read.minersHelp(new UUID(9, 9)));
        // Built blocks join into long runs: the survey is short.
        int[] runs = tag.getList("roads", 10).getCompound(0).getIntArray("survey");
        assertTrue(runs.length / 4 < road.units(), "runs " + runs.length / 4);
    }

    @Test
    void aRoadWrittenBeforeIsOfTheFirstLayout() {
        RoadState state = new RoadState();
        List<RouteCell> cells = cells(64);
        Road old = state.addRoad(US, RoadClass.PAVED, "minecraft:overworld", Endpoint.point(0, 0), Endpoint.point(64, 0),
                cells.stream().map(c -> new RouteCell(c.x(), c.y(), c.z(), RouteCell.Kind.GROUND, 0, null)).toList(), List.of(), Map.of(),
                Road.Mode.OWN, 0, 0, new ArrayList<>());
        CompoundTag tag = new CompoundTag();
        RoadNbt.write(state, tag);
        CompoundTag entry = tag.getList("roads", 10).getCompound(0);
        assertFalse(entry.contains("layout"), "a road of the first layout is written as before");
        assertFalse(entry.contains("survey"));
        tag.remove("minersOff");
        RoadState read = new RoadState();
        assertTrue(RoadNbt.read(tag, read).isEmpty());
        Road back = read.road(old.id()).orElseThrow();
        assertFalse(back.levelled());
        assertEquals(old.blocks(), back.blocks());
        assertEquals(0, back.section(0).builderWork(), 1e-9);
        assertTrue(back.credit().isEmpty());
        assertTrue(read.minersHelp(US), "data written before has every kingdom's miners helping");
    }

    @Test
    void aSurveyThatDoesNotFitReadsAsNothing() {
        RoadState state = new RoadState();
        Road road = levelled(state, cells(100));
        CompoundTag tag = new CompoundTag();
        RoadNbt.write(state, tag);
        ListTag roads = tag.getList("roads", 10);
        CompoundTag entry = roads.getCompound(0);
        int[] runs = entry.getIntArray("survey");
        entry.put("survey", new IntArrayTag(java.util.Arrays.copyOf(runs, runs.length - 4)));
        RoadState read = new RoadState();
        assertTrue(RoadNbt.read(tag, read).isEmpty(), "the file stays writable");
        Road back = read.road(road.id()).orElseThrow();
        assertTrue(back.levelled());
        assertTrue(back.blocks().stream().allMatch(block -> block.survey() == RoadLayout.ColumnSurvey.EMPTY));
        entry.put("survey", new IntArrayTag(new int[]{1, 2, 3}));
        RoadState damaged = new RoadState();
        assertTrue(RoadNbt.read(tag, damaged).isEmpty(), "a damaged survey reads as nothing too");
        assertTrue(damaged.road(road.id()).orElseThrow().blocks().stream().allMatch(block -> block.survey() == RoadLayout.ColumnSurvey.EMPTY));
    }
}
