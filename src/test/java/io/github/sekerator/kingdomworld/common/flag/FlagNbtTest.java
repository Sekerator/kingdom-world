package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.common.data.KingdomNbt;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flag kept with its kingdom: written by the names of dyes and the ids of patterns, and read without ever throwing.
 */
class FlagNbtTest {
    private static final KingdomFlag FLAG = new KingdomFlag(11, List.of(new KingdomFlag.Layer("minecraft:cross", 0),
            new KingdomFlag.Layer("minecraft:border", 4), new KingdomFlag.Layer("somemod:eagle", 15)));

    @Test
    void aFlagGoesThereAndBack() {
        CompoundTag tag = FlagNbt.write(FLAG);
        assertEquals("blue", tag.getString("base"));
        CompoundTag first = tag.getList("layers", 10).getCompound(0);
        assertEquals("minecraft:cross", first.getString("pattern"));
        assertEquals("white", first.getString("color"));
        assertEquals(FLAG, FlagNbt.read(tag));
        assertEquals(KingdomFlag.plain(0), FlagNbt.read(FlagNbt.write(KingdomFlag.plain(0))));
    }

    @Test
    void theKingdomKeepsItsFlagOnlyWhenItHasOne() {
        Kingdom plain = new Kingdom(UUID.randomUUID(), "Valoria", 0x1F618D, null, 5L, "gothic");
        CompoundTag plainTag = KingdomNbt.write(plain);
        assertFalse(plainTag.contains(FlagNbt.FLAG), "the flag of the colour is not written");
        assertEquals(plain, KingdomNbt.read(plainTag));
        Kingdom flagged = plain.withFlag(FLAG);
        CompoundTag flaggedTag = KingdomNbt.write(flagged);
        assertTrue(flaggedTag.contains(FlagNbt.FLAG));
        assertEquals(flagged, KingdomNbt.read(flaggedTag));
        assertEquals("gothic", KingdomNbt.read(flaggedTag).style());
    }

    @Test
    void aBrokenFlagIsReadAsTheFlagOfTheColour() {
        assertNull(FlagNbt.read(null));
        assertNull(FlagNbt.read(StringTag.valueOf("blue")));
        assertNull(FlagNbt.read(new CompoundTag()), "no field");
        CompoundTag numberField = new CompoundTag();
        numberField.putInt("base", 11);
        assertNull(FlagNbt.read(numberField));
        CompoundTag unknownField = FlagNbt.write(FLAG);
        unknownField.putString("base", "crimson");
        assertNull(FlagNbt.read(unknownField));
        CompoundTag layersNotAList = FlagNbt.write(FLAG);
        layersNotAList.putString("layers", "cross");
        assertNull(FlagNbt.read(layersNotAList));
        CompoundTag layersOfNumbers = FlagNbt.write(FLAG);
        ListTag numbers = new ListTag();
        numbers.add(IntTag.valueOf(3));
        layersOfNumbers.put("layers", numbers);
        assertNull(FlagNbt.read(layersOfNumbers));

        CompoundTag kingdom = KingdomNbt.write(new Kingdom(UUID.randomUUID(), "Valoria", 0x1F618D, null, 5L));
        kingdom.put(FlagNbt.FLAG, unknownField);
        assertNull(KingdomNbt.read(kingdom).flag(), "a kingdom with a broken flag still loads");
    }

    @Test
    void badLayersAreDroppedAndTooManyCut() {
        CompoundTag tag = new CompoundTag();
        tag.putString("base", "red");
        ListTag layers = new ListTag();
        layers.add(layer("minecraft:cross", "white"));
        layers.add(layer("minecraft:cross", "crimson"));
        layers.add(layer("Cross Pattern", "white"));
        layers.add(layer("minecraft:base", "white"));
        layers.add(new CompoundTag());
        layers.add(layer("removedmod:eagle", "black"));
        for (int i = 0; i < 8; i++) {
            layers.add(layer("minecraft:border", "yellow"));
        }
        tag.put("layers", layers);
        KingdomFlag read = FlagNbt.read(tag);
        assertEquals(14, read.base());
        assertEquals(KingdomFlag.MAX_LAYERS, read.layers().size());
        assertEquals(new KingdomFlag.Layer("minecraft:cross", 0), read.layers().get(0));
        assertEquals(new KingdomFlag.Layer("removedmod:eagle", 15), read.layers().get(1), "a pattern of a mod taken out is kept");
        assertEquals(new KingdomFlag.Layer("minecraft:border", 4), read.layers().get(5));

        CompoundTag noLayers = new CompoundTag();
        noLayers.putString("base", "white");
        assertEquals(KingdomFlag.plain(0), FlagNbt.read(noLayers));
    }

    private static CompoundTag layer(String pattern, String color) {
        CompoundTag tag = new CompoundTag();
        tag.putString("pattern", pattern);
        tag.putString("color", color);
        return tag;
    }
}
