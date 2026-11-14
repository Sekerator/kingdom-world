package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NBT form of the shops of citizens (format 1):
 *
 * <pre>
 * { formatVersion: 1, nextId,
 *   shops: [ { id, kingdom, city, place: { kind, building, index, dimension, x, y, z }, trade, keepers: [ uuid ], family,
 *              goods: [ { item, category, count, cost } ], debt, margin, status, poorDays, opened, profitDue,
 *              days: [ { day, values: long[] } ] } ],
 *   cities: [ { key, carry: long[], unspent, short, missing, places, keepers, closed: [ { place, day } ] } ],
 *   counters: [ { kingdom, dimension, x, y, z } ] }
 * </pre>
 *
 * <p>Reading is strict: what cannot be understood throws, and the caller loses the shops rather than half of them.
 */
public final class CommerceNbt {
    public static final int FORMAT_VERSION = 1;

    private CommerceNbt() {
    }

    public static CompoundTag write(CommerceSavedData data, CompoundTag tag) {
        tag.putInt("formatVersion", FORMAT_VERSION);
        tag.putLong("nextId", data.nextId());
        ListTag shops = new ListTag();
        for (Business shop : data.shops().values()) {
            shops.add(writeShop(shop));
        }
        tag.put("shops", shops);
        ListTag cities = new ListTag();
        data.cities().forEach((key, trade) -> {
            CompoundTag city = new CompoundTag();
            city.putString("key", key);
            city.put("carry", new LongArrayTag(trade.carry.clone()));
            city.putLong("unspent", trade.unspent);
            city.putBoolean("short", trade.short_);
            city.putString("missing", trade.missing);
            city.putBoolean("places", trade.noPlaces);
            city.putBoolean("keepers", trade.noKeepers);
            ListTag closed = new ListTag();
            trade.closed.forEach((place, day) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("place", place);
                entry.putLong("day", day);
                closed.add(entry);
            });
            city.put("closed", closed);
            cities.add(city);
        });
        tag.put("cities", cities);
        ListTag counters = new ListTag();
        for (CommerceSavedData.Counter counter : data.counters().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", counter.kingdom());
            entry.putString("dimension", counter.dimension());
            entry.putInt("x", counter.x());
            entry.putInt("y", counter.y());
            entry.putInt("z", counter.z());
            counters.add(entry);
        }
        tag.put("counters", counters);
        return tag;
    }

    public static CompoundTag writeShop(Business shop) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", shop.id());
        tag.putUUID("kingdom", shop.kingdomId());
        tag.putLong("city", shop.cityId());
        CompoundTag place = new CompoundTag();
        place.putString("kind", shop.place().kind().id());
        place.putLong("building", shop.place().building());
        place.putInt("index", shop.place().index());
        place.putString("dimension", shop.place().dimension());
        place.putInt("x", shop.place().x());
        place.putInt("y", shop.place().y());
        place.putInt("z", shop.place().z());
        tag.put("place", place);
        tag.putString("trade", shop.trade());
        ListTag keepers = new ListTag();
        for (UUID keeper : shop.keepers()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", keeper);
            keepers.add(entry);
        }
        tag.put("keepers", keepers);
        tag.putBoolean("family", shop.family());
        ListTag goods = new ListTag();
        shop.goods().forEach((item, lot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item);
            entry.putString("category", GoodsCategory.values()[Math.max(0, Math.min(GoodsCategory.values().length - 1, lot.category()))].id());
            entry.putLong("count", lot.count());
            entry.putLong("cost", lot.costMilli());
            goods.add(entry);
        });
        tag.put("goods", goods);
        tag.putLong("debt", shop.debt());
        tag.putInt("margin", shop.marginPermille());
        tag.putString("status", shop.status().id());
        tag.putInt("poorDays", shop.poorDays());
        tag.putLong("opened", shop.opened());
        tag.putLong("profitDue", shop.profitDue());
        ListTag days = new ListTag();
        shop.rawDays().forEach((day, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("day", day);
            entry.put("values", new LongArrayTag(values));
            days.add(entry);
        });
        tag.put("days", days);
        return tag;
    }

    public static Business readShop(CompoundTag tag) {
        if (!tag.hasUUID("kingdom") || !tag.contains("place", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("A shop without a kingdom or a place");
        }
        CompoundTag placeTag = tag.getCompound("place");
        Business.PlaceKind kind = Business.PlaceKind.byId(placeTag.getString("kind"))
                .orElseThrow(() -> new IllegalArgumentException("A shop of an unknown place " + placeTag.getString("kind")));
        Business.Place place = new Business.Place(kind, placeTag.getLong("building"), placeTag.getInt("index"), placeTag.getString("dimension"),
                placeTag.getInt("x"), placeTag.getInt("y"), placeTag.getInt("z"));
        Business shop = new Business(tag.getLong("id"), tag.getUUID("kingdom"), tag.getLong("city"), place, tag.getLong("opened"),
                tag.getInt("margin"));
        List<UUID> keepers = new ArrayList<>();
        for (CompoundTag entry : compounds(tag, "keepers")) {
            if (!entry.hasUUID("id")) {
                throw new IllegalArgumentException("A keeper without an id");
            }
            keepers.add(entry.getUUID("id"));
        }
        Business.Status status = Business.Status.byId(tag.getString("status"))
                .orElseThrow(() -> new IllegalArgumentException("A shop of an unknown state " + tag.getString("status")));
        shop.restore(tag.getString("trade"), keepers, tag.getBoolean("family"), tag.getLong("debt"), tag.getInt("margin"), status,
                tag.getInt("poorDays"), tag.getLong("profitDue"));
        for (CompoundTag entry : compounds(tag, "goods")) {
            GoodsCategory category = GoodsCategory.byId(entry.getString("category"))
                    .orElseThrow(() -> new IllegalArgumentException("Goods of an unknown kind " + entry.getString("category")));
            String item = entry.getString("item");
            if (item.isEmpty() || entry.getLong("count") < 0 || entry.getLong("cost") < 0) {
                throw new IllegalArgumentException("Goods that cannot be read");
            }
            shop.restoreGoods(item, new Business.Lot(category.ordinal(), entry.getLong("count"), entry.getLong("cost")));
        }
        for (CompoundTag entry : compounds(tag, "days")) {
            shop.restoreDay(entry.getLong("day"), entry.getLongArray("values"));
        }
        return shop;
    }

    public static void read(CompoundTag tag, CommerceSavedData data) {
        if (tag.contains("formatVersion") && tag.getInt("formatVersion") > FORMAT_VERSION) {
            throw new IllegalArgumentException("The shops were saved by a newer version (format " + tag.getInt("formatVersion") + ")");
        }
        data.restoreNextId(tag.getLong("nextId"));
        for (CompoundTag entry : compounds(tag, "shops")) {
            data.restore(readShop(entry));
        }
        for (CompoundTag entry : compounds(tag, "cities")) {
            CommerceSavedData.CityTrade trade = new CommerceSavedData.CityTrade();
            long[] carry = entry.getLongArray("carry");
            System.arraycopy(carry, 0, trade.carry, 0, Math.min(carry.length, trade.carry.length));
            trade.unspent = Math.max(0, entry.getLong("unspent"));
            trade.short_ = entry.getBoolean("short");
            trade.missing = entry.getString("missing");
            trade.noPlaces = entry.getBoolean("places");
            trade.noKeepers = entry.getBoolean("keepers");
            for (CompoundTag closed : compounds(entry, "closed")) {
                trade.closed.put(closed.getString("place"), closed.getLong("day"));
            }
            data.restoreCity(entry.getString("key"), trade);
        }
        for (CompoundTag entry : compounds(tag, "counters")) {
            if (!entry.hasUUID("kingdom")) {
                throw new IllegalArgumentException("A counter without a kingdom");
            }
            data.restoreCounter(new CommerceSavedData.Counter(entry.getUUID("kingdom"), entry.getString("dimension"), entry.getInt("x"),
                    entry.getInt("y"), entry.getInt("z")));
        }
    }

    private static List<CompoundTag> compounds(CompoundTag parent, String key) {
        if (!parent.contains(key)) {
            return List.of();
        }
        if (!parent.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(key + " is not a list");
        }
        ListTag list = (ListTag) parent.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " does not hold compounds");
        }
        List<CompoundTag> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

}
