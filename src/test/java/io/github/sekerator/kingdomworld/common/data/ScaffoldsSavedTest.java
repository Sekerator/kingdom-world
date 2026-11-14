package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.Scaffolding;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scaffolds of a construction and those left behind by constructions gone are saved and read back, so that a restart
 * forgets none of them; data saved before scaffolds has none.
 */
class ScaffoldsSavedTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static Construction construction() {
        return new Construction(7, UUID.randomUUID(), "kingdomworld:small_house", 3L, new WorldPos(OVERWORLD, 10, 64, -20), new Placement(0, false),
                new Box(6, 64, -28, 14, 72, -20), 100L, List.of(), 12);
    }

    @Test
    void theScaffoldsOfAConstructionAreSavedWithIt() {
        Construction construction = construction();
        long low = Scaffolding.pack(5, 64, -24);
        long high = Scaffolding.pack(5, 65, -24);
        construction.addScaffold(low);
        construction.addScaffold(high);
        Construction read = ConstructionNbt.readConstruction(ConstructionNbt.write(construction));
        assertEquals(Set.of(low, high), read.scaffolds());

        CompoundTag before = ConstructionNbt.write(construction());
        assertFalse(before.contains("scaffolds"), "a construction without scaffolds saves none");
        assertFalse(ConstructionNbt.readConstruction(before).hasScaffolds());
    }

    @Test
    void scaffoldsLeftBehindAreSavedWithTheRegister() {
        ConstructionBook book = new ConstructionBook();
        long cell = Scaffolding.pack(-3, 70, 41);
        book.leaveScaffolds(OVERWORLD, List.of(cell));
        book.leaveScaffolds("minecraft:the_nether", List.of(Scaffolding.pack(0, 40, 0)));
        CompoundTag saved = ConstructionNbt.write(book);
        assertTrue(saved.contains("leftScaffolds"));

        KingdomWorldState state = new KingdomWorldState();
        assertTrue(ConstructionNbt.read(saved, state).isEmpty());
        assertEquals(book.leftScaffolds(), state.construction().leftScaffolds());

        // Data saved before scaffolds reads as none left.
        KingdomWorldState older = new KingdomWorldState();
        assertTrue(ConstructionNbt.read(ConstructionNbt.empty(), older).isEmpty());
        assertTrue(older.construction().leftScaffolds().isEmpty());
    }
}
