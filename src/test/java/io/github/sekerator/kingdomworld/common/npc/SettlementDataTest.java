package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.PlannedRoad;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementDataTest {
    private static final UUID KINGDOM = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private static NpcState withSettlements() {
        NpcState state = new NpcState();
        Settlement capital = state.addSettlement(KINGDOM, 3, 0, Tier.CAPITAL, 10, 20, 1, new NpcNames.Name("Ozerovia", "Озеровия"));
        Settlement town = state.addSettlement(KINGDOM, 4, capital.id(), Tier.TOWN, 600, -40, 2, new NpcNames.Name("Ostbrook", "Остбрук"));
        town.found(40, 30, 125);
        town.setStatus(Settlement.Status.BLOCKED);
        town.setStage("walls", 3);
        town.setGroundY(71);
        town.give(5);
        town.setLastPlayerTick(12345);
        town.setFolded(new long[]{7, 8, 9});
        town.live(new SettlementLife.Day(31, 290, 4, 0, 51), 6);
        state.addPlannedRoad(capital.id(), town.id(), RoadClass.GRAVEL, new int[]{10, 300, 600}, new int[]{20, 0, -40});
        PlannedRoad second = state.addPlannedRoad(town.id(), capital.id(), RoadClass.PATH, new int[]{600, 10}, new int[]{-40, 20});
        state.setRoadOf(second.id(), 77);
        return state;
    }

    @Test
    void formatSixKeepsTheSettlementsAndTheRoadsOfThePlan() {
        NpcState state = withSettlements();
        CompoundTag tag = new CompoundTag();
        NpcNbt.write(state, tag);
        NpcState read = new NpcState();
        List<String> problems = NpcNbt.read(tag, read);
        assertEquals(List.of(), problems);
        assertEquals(2, read.settlements().size());
        List<Settlement> before = new ArrayList<>(state.settlements());
        List<Settlement> after = new ArrayList<>(read.settlements());
        for (int i = 0; i < before.size(); i++) {
            Settlement a = before.get(i);
            Settlement b = after.get(i);
            assertEquals(a.toString(), b.toString());
            assertEquals(a.id(), b.id());
            assertEquals(a.kingdomId(), b.kingdomId());
            assertEquals(a.province(), b.province());
            assertEquals(a.parent(), b.parent());
            assertEquals(a.tier(), b.tier());
            assertEquals(a.name(), b.name());
            assertEquals(a.status(), b.status());
            assertEquals(a.stage(), b.stage());
            assertEquals(a.step(), b.step());
            assertEquals(a.groundY(), b.groundY());
            assertEquals(a.givenFlags(), b.givenFlags());
            assertEquals(a.population(), b.population());
            assertEquals(a.housing(), b.housing());
            assertEquals(a.food(), b.food());
            assertEquals(a.guards(), b.guards());
            assertEquals(a.prosperity(), b.prosperity());
            assertEquals(a.fertility(), b.fertility());
            assertEquals(a.lastDay(), b.lastDay());
            assertEquals(a.lastPlayerTick(), b.lastPlayerTick());
            assertArrayEquals(a.folded(), b.folded());
        }
        assertEquals(new ArrayList<>(state.plannedRoads()), new ArrayList<>(read.plannedRoads()));
        assertEquals(state.nextSettlementId(), read.nextSettlementId());
        assertEquals(state.nextPlannedRoadId(), read.nextPlannedRoadId());
        // New numbers never repeat old ones.
        assertEquals(3, read.addSettlement(KINGDOM, 4, 0, Tier.HAMLET, 0, 0, 3, new NpcNames.Name("Lowmere", "Лоумир")).id());
    }

    @Test
    void formatFiveIsReadWithoutSettlements() {
        NpcState state = withSettlements();
        CompoundTag tag = new CompoundTag();
        NpcNbt.write(state, tag);
        // What format 5 wrote: no settlements, no roads of the plan and no numbers for them.
        tag.remove("settlements");
        tag.remove("roads");
        tag.getCompound("plan").remove("settlement");
        tag.getCompound("plan").remove("road");
        NpcState read = new NpcState();
        assertEquals(List.of(), NpcNbt.read(tag, read));
        assertTrue(read.settlements().isEmpty());
        assertTrue(read.plannedRoads().isEmpty());
        assertEquals(1, read.nextSettlementId());
        assertEquals(1, read.nextPlannedRoadId());
    }

    @Test
    void aSettlementUnreadableIsAProblemAndTheRestIsRead() {
        NpcState state = withSettlements();
        CompoundTag tag = new CompoundTag();
        NpcNbt.write(state, tag);
        tag.getList("settlements", 10).getCompound(0).putString("tier", "PALACE");
        NpcState read = new NpcState();
        List<String> problems = NpcNbt.read(tag, read);
        assertEquals(1, problems.size(), problems.toString());
        assertEquals(1, read.settlements().size());
        assertEquals(3, read.nextSettlementId(), "the number of the settlement lost is not given again");
    }

    @Test
    void aSettlementLivesItsDaysOnceEach() {
        NpcState state = new NpcState();
        Settlement village = state.addSettlement(KINGDOM, 1, 0, Tier.VILLAGE, 0, 0, 5, new NpcNames.Name("Brook", "Брук"));
        village.found(20, 15, 130);
        Settlement capital = state.addSettlement(KINGDOM, 1, 0, Tier.CAPITAL, 0, 0, 6, new NpcNames.Name("Throne", "Трон"));
        assertFalse(SettlementWork.live(capital, 10), "a capital is counted by its own people");
        assertTrue(SettlementWork.live(village, 10), "the first day it is seen it starts to live");
        assertEquals(15, village.population());
        assertEquals(10, village.lastDay());
        assertFalse(SettlementWork.live(village, 10), "a day is lived once");
        assertTrue(SettlementWork.live(village, 13));
        assertEquals(18, village.population(), "three days of growth");
        assertEquals(13, village.lastDay());
        assertTrue(SettlementWork.live(village, 13 + 1000));
        assertEquals(20, village.population(), "a jump of days is lived thirty days at most, up to the houses");
        village.setStatus(Settlement.Status.STANDING);
        assertFalse(SettlementWork.live(village, 2000), "a settlement raised in the world lives by its citizens");
    }
}
