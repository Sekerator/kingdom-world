package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the book of construction keeps besides constructions and buildings: items waiting to be dropped where nothing was
 * loaded and the orders of the king from afar go there and back, an older save without them loads as it was, an order of a
 * kind or state this version does not know is left out, and items taken out of the book are gone from it.
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
        old.remove("commissions");
        old.remove("nextCommission");
        KingdomWorldState state = new KingdomWorldState();
        assertTrue(ConstructionNbt.read(old, state).isEmpty());
        assertTrue(state.construction().drops().isEmpty());
        assertTrue(state.construction().commissions().isEmpty(), "an older save has no orders from afar");
        assertEquals(1, state.construction().nextCommissionId());
    }

    @Test
    void ordersFromAfarGoThereAndBack() {
        KingdomWorldState state = new KingdomWorldState();
        UUID kingdom = found(state);
        ConstructionBook book = new ConstructionBook();
        UUID king = UUID.randomUUID();
        Commission build = book.addCommission(new Commission(0, kingdom, Commission.Kind.BUILD, "kingdomworld:house_small",
                new Materials(Materials.KINGDOM, ""), 4, OVERWORLD, 0, 0, 0, Construction.Priority.HIGH, king, Commission.Origin.KING, 1200L,
                Commission.State.NO_PLACE, 0, "blocked|10|64|-3", "", false, 25_200L, 0));
        Commission at = book.addCommission(new Commission(0, kingdom, Commission.Kind.AT, "kingdomworld:well", new Materials("minecraft:spruce",
                "minecraft:stone_bricks"), -1, OVERWORLD, 300, -40, 3, Construction.Priority.LOW, null, Commission.Origin.KING, 1300L,
                Commission.State.LEVELLING, 77, "", "", false, 0, 0));
        Commission post = book.addCommission(new Commission(0, kingdom, Commission.Kind.POST, "", Materials.NONE, 0, OVERWORLD, 0, 0, 0,
                Construction.Priority.HIGH, king, Commission.Origin.GOVERNOR, 1400L, Commission.State.LACKS_ITEM, 0, "item|kingdomworld:work_post",
                "", false, 2000L, 55));
        Commission hall = book.addCommission(new Commission(0, kingdom, Commission.Kind.HALL, "", Materials.NONE, -1, OVERWORLD, 1000, 2000, 0,
                Construction.Priority.NORMAL, king, Commission.Origin.KING, 1500L, Commission.State.WAITING, 0, "", "Ривенхолд", true, 0, 0));
        CompoundTag tag = ConstructionNbt.write(book);

        assertTrue(ConstructionNbt.read(tag, state).isEmpty());
        assertEquals(List.of(build, at, post, hall), state.construction().commissions());
        assertEquals(5, state.construction().nextCommissionId(), "the next order gets a new number");
        assertEquals(build.id() + 4, state.construction().addCommission(build).id());
    }

    @Test
    void anOrderOfAnUnknownKindIsLeftOut() {
        KingdomWorldState state = new KingdomWorldState();
        UUID kingdom = found(state);
        ConstructionBook book = new ConstructionBook();
        book.addCommission(new Commission(0, kingdom, Commission.Kind.BUILD, "kingdomworld:house_small", Materials.NONE, 0, OVERWORLD, 0, 0, 0,
                Construction.Priority.NORMAL, null, Commission.Origin.KING, 0, Commission.State.WAITING, 0, "", "", false, 0, 0));
        book.addCommission(new Commission(0, kingdom, Commission.Kind.POST, "", Materials.NONE, 0, OVERWORLD, 0, 0, 0,
                Construction.Priority.NORMAL, null, Commission.Origin.KING, 0, Commission.State.WAITING, 0, "", "", false, 0, 0));
        CompoundTag tag = ConstructionNbt.write(book);
        ListTag list = tag.getList("commissions", Tag.TAG_COMPOUND);
        list.getCompound(0).putString("kind", "castle_in_the_air");
        list.getCompound(1).putString("state", "dreaming");

        ConstructionNbt.read(tag, state);
        assertTrue(state.construction().commissions().isEmpty(), "orders of a later version are left out, not read wrong");
        assertEquals(3, state.construction().nextCommissionId(), "their numbers are not given again");
    }

    private static UUID found(KingdomWorldState state) {
        UUID id = UUID.randomUUID();
        assertTrue(state.found(id, "Realm", 0x123456, UUID.randomUUID(), new Capital(OVERWORLD, 8, 64, 8), 0, KingdomRules.DEFAULT)
                instanceof CreateKingdomResult.Created);
        return id;
    }

    @Test
    void ordersFromAfarGoWithTheirKingdom() {
        ConstructionBook book = new ConstructionBook();
        UUID gone = UUID.randomUUID();
        UUID stays = UUID.randomUUID();
        book.addCommission(new Commission(0, gone, Commission.Kind.POST, "", Materials.NONE, 0, OVERWORLD, 0, 0, 0, null, null, null, 0, null, 0, "",
                "", false, 0, 0));
        book.addCommission(new Commission(0, stays, Commission.Kind.POST, "", Materials.NONE, 0, OVERWORLD, 0, 0, 0, null, null, null, 0, null, 0,
                "", "", false, 0, 0));
        book.removeKingdom(gone);
        assertEquals(1, book.commissions().size());
        assertEquals(stays, book.commissions().get(0).kingdomId());
        assertTrue(book.commissionsOf(gone).isEmpty());
    }
}
