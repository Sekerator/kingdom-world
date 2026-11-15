package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shops of citizens survive saving: goods made by citizens apart from goods bought, raw materials, fuel, work paid in advance,
 * the trade fixed and the workstations of places; the shops of the first format read as general stores whose places nobody looked
 * at, their days of trade padded.
 */
class CommerceNbtTest {
    private static final UUID KINGDOM = new UUID(3, 4);

    @Test
    void theFirstFormatReadsAsGeneralStores() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("formatVersion", 1);
        tag.putLong("nextId", 5);
        CompoundTag shop = new CompoundTag();
        shop.putLong("id", 4);
        shop.putUUID("kingdom", KINGDOM);
        shop.putLong("city", 0);
        CompoundTag place = new CompoundTag();
        place.putString("kind", "craft_house");
        place.putLong("building", 9);
        place.putString("dimension", "minecraft:overworld");
        shop.put("place", place);
        shop.putString("trade", "general_store");
        shop.putString("status", "trading");
        shop.putLong("opened", 12);
        ListTag goods = new ListTag();
        CompoundTag bread = new CompoundTag();
        bread.putString("item", "minecraft:bread");
        bread.putString("category", "food");
        bread.putLong("count", 7);
        bread.putLong("cost", 2_500);
        goods.add(bread);
        shop.put("goods", goods);
        ListTag days = new ListTag();
        CompoundTag day = new CompoundTag();
        day.putLong("day", 20);
        day.put("values", new LongArrayTag(new long[]{30, 20, 10, 5, 12, 0, 0, 0, 0}));
        days.add(day);
        shop.put("days", days);
        ListTag shops = new ListTag();
        shops.add(shop);
        tag.put("shops", shops);
        CommerceSavedData data = new CommerceSavedData();
        CommerceNbt.read(tag, data);
        Business read = data.shop(4).orElseThrow();
        assertEquals(Business.GENERAL_STORE, read.trade());
        assertEquals(7, read.count("minecraft:bread"));
        assertFalse(read.goods().get("minecraft:bread").made(), "goods of the first shops were all bought");
        assertEquals(12, read.tradeSince());
        assertEquals(30, read.day(20).orElseThrow().sales());
        assertEquals(12, read.averageCategory(21, GoodsCategory.FOOD.ordinal()));
        assertTrue(data.survey(read.place().key()).isEmpty(), "the place was never looked at");
    }

    @Test
    void theSecondFormatKeepsEverything() {
        CommerceSavedData data = new CommerceSavedData();
        Business.Place place = new Business.Place(Business.PlaceKind.CRAFT_HOUSE, 9, 0, "minecraft:overworld", 1, 64, 2);
        Business shop = data.open(KINGDOM, 3, place, 10, 150);
        shop.changeTrade("bakery", 11);
        shop.setPinned("bakery");
        shop.setKeepers(List.of(new UUID(5, 6)), false);
        shop.addGoods("minecraft:bread", GoodsCategory.FOOD.ordinal(), 2, 2_000);
        shop.addMade("minecraft:bread", GoodsCategory.FOOD.ordinal(), 3, 3_000, 100);
        shop.addInput("minecraft:wheat", 9, 1_000);
        shop.setFuel(800, 2_000);
        shop.addCommission(new Business.Commission(1, 7, "minecraft:bread", 4, 20, 11));
        shop.record(11, new Business.Day(10, 6, 3, 2, null, 9, 4, 20));
        data.putSurvey(place.key(), new CommerceSavedData.Survey(Set.of("crafting_table", "furnace"), 11));
        data.city(KINGDOM, 3).wantMaterials(5);

        CommerceSavedData copy = new CommerceSavedData();
        CommerceNbt.read(CommerceNbt.write(data, new CompoundTag()), copy);
        Business read = copy.shop(shop.id()).orElseThrow();
        assertEquals("bakery", read.trade());
        assertEquals("bakery", read.pinned());
        assertEquals(11, read.tradeSince());
        assertEquals(2, read.count("minecraft:bread"));
        assertEquals(3, read.count(Business.key("minecraft:bread", true)));
        Business.Lot made = read.goods().get(Business.key("minecraft:bread", true));
        assertTrue(made.made());
        assertEquals(100, made.laborMilli());
        assertEquals(9, read.input("minecraft:wheat"));
        assertEquals(800, read.fuelTicks());
        assertEquals(2_000, read.fuelCostMilli());
        assertEquals(1, read.commissions().size());
        assertEquals(20, copy.held(KINGDOM), "the money held for the commission counts");
        Business.Day day = read.day(11).orElseThrow();
        assertEquals(9, day.made());
        assertEquals(4, day.exported());
        assertEquals(20, day.toCrown());
        assertEquals(Set.of("crafting_table", "furnace"), copy.survey(place.key()).orElseThrow().stations());
        assertEquals(data.nextCommission(), copy.nextCommission());
    }
}
