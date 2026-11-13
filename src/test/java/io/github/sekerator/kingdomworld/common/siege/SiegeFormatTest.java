package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Siege format 3 keeps holds and conquests of provinces; data of format 2 reads as it is, data of a newer format stays
 * read-only.
 */
class SiegeFormatTest {
    private static final String WORLD = "minecraft:overworld";
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);

    @Test
    void holdsAndConquestsOfProvincesSurviveASave() {
        SiegeState state = new SiegeState();
        Hold town = state.holdAt(Hold.Kind.PROVINCE, 5, a, b, WORLD, 12, 100);
        town.step(true, false, false, 2_000, 2_100, SiegeRules.DEFAULT.provinceTicks(true));
        state.holdAt(Hold.Kind.PROVINCE, 5, a, b, WORLD, 13, 100).take(300, SiegeRules.DEFAULT.provinceTicks(false));
        Conquest provinces = state.addConquest(Conquest.Kind.PROVINCE, 0, a, b, WORLD, List.of(14L, 13L), 0, 400);
        state.advance(provinces.id());
        state.advance(provinces.id());
        state.advance(provinces.id());
        state.advance(provinces.id());

        SiegeState read = new SiegeState();
        assertEquals(List.of(), SiegeNbt.read(SiegeNbt.write(state), read));
        Hold held = read.hold(town.key()).orElseThrow();
        assertEquals(Hold.Kind.PROVINCE, held.kind());
        assertEquals(12, held.target());
        assertEquals(2_000, held.progress());
        assertFalse(held.taken());
        assertEquals(2_100, held.since(), "held since the first step");
        Hold taken = read.hold(Hold.key(Hold.Kind.PROVINCE, 5, b, WORLD, 13)).orElseThrow();
        assertTrue(taken.taken());
        assertEquals(300, taken.since());
        Conquest again = read.conquests().get(0);
        assertEquals(Conquest.Kind.PROVINCE, again.kind());
        assertEquals(Conquest.Step.SETTLEMENTS, again.step(), "the journal goes on where it stopped");
        assertEquals(List.of(14, 13), again.provinces());
        assertEquals(0, again.war(), "a cession by treaty has no war");
        assertEquals(state.conquests().get(0), again);
        assertTrue(read.nextConquestId() > again.id());

        SiegeSavedData data = new SiegeSavedData();
        data.state().restoreHold(held);
        data.state().restoreConquest(again);
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(3, saved.getInt(SiegeSavedData.TAG_FORMAT_VERSION));
        assertFalse(SiegeSavedData.load(saved).isReadOnly());
    }

    @Test
    void formatTwoReadsAsItIsAndANewerFormatStaysReadOnly() {
        SiegeState old = new SiegeState();
        old.holdAt(Hold.Kind.PLAZA, 3, a, b, WORLD, 9, 0).step(true, false, false, 600, 600, SiegeRules.DEFAULT);
        old.holdAt(Hold.Kind.CHUNK, 3, a, b, WORLD, 42, 0).take(700, SiegeRules.DEFAULT);
        old.addConquest(Conquest.Kind.ANNEX, 2, a, b, WORLD, List.of(), 0, 800);
        Conquest annex = old.conquests().get(0);
        CompoundTag tag = SiegeNbt.write(old);
        tag.putInt(SiegeSavedData.TAG_FORMAT_VERSION, 2);
        // An annexation of format 2 that stopped at its cities.
        ListTag conquests = tag.getList("conquests", Tag.TAG_COMPOUND);
        conquests.getCompound(0).putString("step", "cities");

        SiegeSavedData data = SiegeSavedData.load(tag);
        assertFalse(data.isReadOnly(), "format 2 reads as it is");
        assertEquals(2, data.state().holds().size());
        assertEquals(10, data.state().hold(Hold.key(Hold.Kind.PLAZA, 3, b, WORLD, 9)).orElseThrow().percent(SiegeRules.DEFAULT));
        assertTrue(data.state().hold(Hold.key(Hold.Kind.CHUNK, 3, b, WORLD, 42)).orElseThrow().taken());
        Conquest read = data.state().conquests().get(0);
        assertEquals(annex.id(), read.id());
        assertEquals(Conquest.Step.CITIES, read.step());
        assertEquals(Conquest.Step.SETTLEMENTS, read.advance().step(), "the annexation goes on through the new step");

        List<Conquest.Step> rest = new ArrayList<>();
        for (Conquest at = read.advance(); !at.done(); at = at.advance()) {
            rest.add(at.step());
        }
        assertEquals(List.of(Conquest.Step.SETTLEMENTS, Conquest.Step.DAMAGE, Conquest.Step.MEMBERS, Conquest.Step.DISSOLVE), rest);

        CompoundTag newer = tag.copy();
        newer.putInt(SiegeSavedData.TAG_FORMAT_VERSION, SiegeSavedData.FORMAT_VERSION + 1);
        assertTrue(SiegeSavedData.load(newer).isReadOnly());
        CompoundTag unknown = tag.copy();
        unknown.getList("holds", Tag.TAG_COMPOUND).getCompound(0).putString("kind", "fortress");
        assertTrue(SiegeSavedData.load(unknown).isReadOnly(), "a kind this build does not know keeps the data read-only");
    }
}
