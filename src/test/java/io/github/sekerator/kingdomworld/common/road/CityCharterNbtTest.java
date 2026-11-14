package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What cities keep of their own in saved data: it reads back as it was written, data written before it existed reads as
 * nothing, and a broken entry is described rather than read.
 */
class CityCharterNbtTest {
    private static final UUID US = new UUID(1, 1);
    private static final UUID THEM = new UUID(2, 2);

    @Test
    void theTownsfolkOfCitiesReadBackAsTheyWere() {
        RoadState state = new RoadState();
        CityCharter capital = state.charterOf(US, 0, 4_000);
        capital.setFolk(936);
        capital.setFolkCarry(0.25);
        capital.setLastDay(41);
        state.charterOf(THEM, 7, CityCharter.NEVER).setFolk(12);
        // A record that holds nothing yet is not written.
        state.charterOf(THEM, 8, CityCharter.NEVER);
        CompoundTag tag = new CompoundTag();
        RoadNbt.write(state, tag);
        assertEquals(2, tag.getList("charters", 10).size());
        RoadState read = new RoadState();
        List<String> problems = RoadNbt.read(tag, read);
        assertTrue(problems.isEmpty(), problems.toString());
        CityCharter back = read.charter(US, 0).orElseThrow();
        assertEquals(936, back.folk());
        assertEquals(0.25, back.folkCarry(), 1e-9);
        assertEquals(41, back.lastDay());
        assertEquals(4_000, back.lastArrival());
        CityCharter theirs = read.charter(THEM, 7).orElseThrow();
        assertEquals(12, theirs.folk());
        assertEquals(CityCharter.NEVER, theirs.lastDay(), "a key not written reads as nothing yet");
        assertEquals(CityCharter.NEVER, theirs.lastArrival());
        assertTrue(read.charter(THEM, 8).isEmpty());
    }

    @Test
    void dataWrittenBeforeHasNoTownsfolk() {
        CompoundTag old = new CompoundTag();
        old.put("cities", new ListTag());
        RoadState read = new RoadState();
        assertTrue(RoadNbt.read(old, read).isEmpty());
        assertTrue(read.charters().isEmpty());
    }

    @Test
    void aBrokenRecordIsDescribed() {
        CompoundTag tag = new CompoundTag();
        ListTag charters = new ListTag();
        CompoundTag broken = new CompoundTag();
        broken.putLong("city", 3);
        charters.add(broken);
        CompoundTag negative = new CompoundTag();
        negative.putUUID("kingdom", US);
        negative.putLong("city", -2);
        charters.add(negative);
        tag.put("charters", charters);
        RoadState read = new RoadState();
        assertEquals(2, RoadNbt.read(tag, read).size());
        assertTrue(read.charters().isEmpty());
    }
}
