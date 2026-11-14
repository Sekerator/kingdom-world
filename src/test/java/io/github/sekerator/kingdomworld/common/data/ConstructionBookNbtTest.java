package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the book of construction keeps besides constructions and buildings: items waiting to be dropped where nothing was
 * loaded go there and back, an older save without them loads as it was, and items taken out of the book are gone from it.
 */
class ConstructionBookNbtTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void itemsToDropGoThereAndBack() {
        ConstructionBook book = new ConstructionBook();
        UUID kingdom = UUID.randomUUID();
        book.addDrop(OVERWORLD, 1, 64, 2, "minecraft:oak_log", 5, kingdom, 100L);
        book.addDrop(OVERWORLD, 1, 64, 2, "minecraft:oak_log", 3, kingdom, 200L);
        book.addDrop(OVERWORLD, 40, 70, 2, "minecraft:cobblestone", 64, kingdom, 300L);
        assertEquals(2, book.drops().size(), "the same item at the same place adds up");
        CompoundTag tag = ConstructionNbt.write(book);

        KingdomWorldState state = new KingdomWorldState();
        List<String> problems = ConstructionNbt.read(tag, state);
        assertTrue(problems.isEmpty(), problems.toString());
        List<ConstructionBook.Drop> drops = state.construction().drops();
        assertEquals(2, drops.size());
        ConstructionBook.Drop logs = drops.stream().filter(drop -> drop.item().equals("minecraft:oak_log")).findFirst().orElseThrow();
        assertEquals(8, logs.count());
        assertEquals(100L, logs.since(), "the oldest wait counts");
        assertEquals(kingdom, logs.kingdomId());

        // Taken out to be dropped, they are gone at once: dropped twice they would be made twice.
        List<ConstructionBook.Drop> taken = state.construction().takeDrops(OVERWORLD, ChunkKey.of(0, 0));
        assertEquals(1, taken.size());
        assertEquals(1, state.construction().drops().size());
        assertTrue(state.construction().takeDrops(OVERWORLD, ChunkKey.of(0, 0)).isEmpty());
        assertTrue(state.construction().takeDrops("minecraft:the_nether", ChunkKey.of(2, 0)).isEmpty(), "another dimension");
    }

    @Test
    void anOlderSaveWithoutItemsToDropLoads() {
        CompoundTag old = ConstructionNbt.write(new ConstructionBook());
        old.remove("drops");
        KingdomWorldState state = new KingdomWorldState();
        assertTrue(ConstructionNbt.read(old, state).isEmpty());
        assertTrue(state.construction().drops().isEmpty());
    }
}
