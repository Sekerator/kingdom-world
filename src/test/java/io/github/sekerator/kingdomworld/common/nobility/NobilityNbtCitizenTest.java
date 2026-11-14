package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A house headed by a citizen in saved data: it reads back with its citizen, and a house saved before has none and reads as it
 * was, without making the data of the nobility read-only.
 */
class NobilityNbtCitizenTest {
    private static final UUID KINGDOM = new UUID(3, 4);

    @Test
    void aHouseHeadedByACitizenReadsBackWithIt() {
        NobilityState state = new NobilityState();
        UUID citizen = new UUID(8, 9);
        NobleHouse house = state.foundFor(KINGDOM, citizen, 321, Gender.FEMALE, 5, 1);
        NobleHouse plain = state.found(KINGDOM, 6, 1);
        CompoundTag tag = NobilityNbt.write(state);
        NobilityState read = new NobilityState();
        List<String> problems = NobilityNbt.read(tag, read);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(citizen, read.house(house.id()).orElseThrow().citizen());
        assertEquals(0, read.house(house.id()).orElseThrow().wealth());
        assertNull(read.house(plain.id()).orElseThrow().citizen());
        assertEquals(1, read.countedHouses(KINGDOM));
    }

    @Test
    void aHouseSavedBeforeHasNoCitizen() {
        NobilityState state = new NobilityState();
        NobleHouse house = state.found(KINGDOM, 6, 1);
        CompoundTag tag = NobilityNbt.write(state);
        ListTag houses = tag.getList("houses", Tag.TAG_COMPOUND);
        for (int i = 0; i < houses.size(); i++) {
            houses.getCompound(i).remove("citizen");
        }
        NobilityState read = new NobilityState();
        assertTrue(NobilityNbt.read(tag, read).isEmpty());
        assertNull(read.house(house.id()).orElseThrow().citizen());
    }

    @Test
    void theMoneyOfTheGovernorsReadsBackAndACourtSavedBeforeHasNone() {
        NobilityState state = new NobilityState();
        state.court(KINGDOM).setGovernorAllowance(300);
        state.court(KINGDOM).governorSpend(12, 40);
        state.court(KINGDOM).governorSpend(12, 35);
        UUID other = new UUID(5, 5);
        state.court(other);
        CompoundTag tag = NobilityNbt.write(state);
        NobilityState read = new NobilityState();
        assertTrue(NobilityNbt.read(tag, read).isEmpty());
        assertEquals(300, read.court(KINGDOM).governorAllowance());
        assertEquals(75, read.court(KINGDOM).governorSpent(12));
        assertEquals(0, read.court(KINGDOM).governorSpent(13), "another day starts from nothing");
        assertEquals(0, read.court(other).governorAllowance(), "a court without the keys gives nothing");
        assertEquals(Long.MIN_VALUE, read.court(other).governorSpentDay());
    }
}
