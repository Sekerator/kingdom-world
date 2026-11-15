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
 * NBT form of the shops of citizens (format 3; format 2 reads with no hands of the townsfolk):
 *
 * <pre>
 * { formatVersion: 3, nextId, nextCommission,
 *   shops: [ { id, kingdom, city, place: { kind, building, index, dimension, x, y, z }, trade, pinned, nextTrade, tradeSince,
 *              keepers: [ { id, share } ], family, folkHands, shared,
 *              goods: [ { item, category, count, cost, labor, made } ], inputs: [ { item, count, cost, labor } ], fuel, fuelCost,
 *              commissions: [ { id, order, item, count, paid, day } ],
 *              debt, margin, status, poorDays, opened, profitDue, days: [ { day, values: long[] } ] } ],
 *   cities: [ { key, carry: long[], unmet: long[], outside, mastersPass, orders: [ { shop, worth } ], unspent, short, missing, places,
 *               keepers, closed: [ { place, day } ] } ],
 *   surveys: [ { place, day, stations: [ string ] } ], crownDays: [ { kingdom, day, spent, items } ], boughtFor: [ { order, count } ],
 *   counters: [ { kingdom, dimension, x, y, z } ] }
 * </pre>
 *
 * <p>Format 1, the shops before the crafts, reads as general stores whose places were never looked at, their days of trade
 * padded with the new values; its goods were all bought. Reading is strict: what cannot be understood throws, and the caller loses
 * the shops rather than half of them.
 */
public final class CommerceNbt {
    public static final int FORMAT_VERSION = 3;

    private CommerceNbt() {
    }

    public static CompoundTag write(CommerceSavedData data, CompoundTag tag) {
        tag.putInt("formatVersion", FORMAT_VERSION);
        tag.putLong("nextId", data.nextId());
        tag.putLong("nextCommission", data.nextCommission());
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
            city.put("unmet", new LongArrayTag(trade.unmet.clone()));
            city.putLong("outside", trade.outside);
            city.putBoolean("mastersPass", trade.mastersPass);
            ListTag orders = new ListTag();
            trade.orders.forEach((shop, worth) -> {
                CompoundTag order = new CompoundTag();
                order.putLong("shop", shop);
                order.putLong("worth", worth);
                orders.add(order);
            });
            city.put("orders", orders);
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
        ListTag surveys = new ListTag();
        data.surveys().forEach((place, survey) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("place", place);
            entry.putLong("day", survey.day());
            ListTag stations = new ListTag();
            for (String station : survey.stations()) {
                stations.add(net.minecraft.nbt.StringTag.valueOf(station));
            }
            entry.put("stations", stations);
            surveys.add(entry);
        });
        tag.put("surveys", surveys);
        ListTag crownDays = new ListTag();
        data.crownDays().forEach((kingdom, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", kingdom);
            entry.putLong("day", values[0]);
            entry.putLong("spent", values[1]);
            entry.putLong("items", values[2]);
            crownDays.add(entry);
        });
        tag.put("crownDays", crownDays);
        ListTag boughtFor = new ListTag();
        data.boughtFor().forEach((key, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("order", key);
            entry.putLong("count", count);
            boughtFor.add(entry);
        });
        tag.put("boughtFor", boughtFor);
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
        tag.putString("pinned", shop.pinned());
        tag.putString("nextTrade", shop.nextTrade());
        tag.putLong("tradeSince", shop.tradeSince());
        ListTag keepers = new ListTag();
        int[] shares = shop.shares();
        for (int i = 0; i < shop.keepers().size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", shop.keepers().get(i));
            entry.putInt("share", i < shares.length ? shares[i] : Business.KEEPER_SHARE);
            keepers.add(entry);
        }
        tag.put("keepers", keepers);
        tag.putBoolean("family", shop.family());
        if (shop.folkHands() > 0) {
            tag.putInt("folkHands", shop.folkHands());
        }
        if (shop.lastShared() > 0) {
            tag.putLong("shared", shop.lastShared());
        }
        ListTag goods = new ListTag();
        shop.goods().forEach((key, lot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", Business.itemOf(key));
            entry.putString("category", GoodsCategory.values()[Math.max(0, Math.min(GoodsCategory.values().length - 1, lot.category()))].id());
            entry.putLong("count", lot.count());
            entry.putLong("cost", lot.costMilli());
            entry.putLong("labor", lot.laborMilli());
            entry.putBoolean("made", Business.isMade(key));
            goods.add(entry);
        });
        tag.put("goods", goods);
        ListTag inputs = new ListTag();
        shop.inputs().forEach((item, lot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item);
            entry.putLong("count", lot.count());
            entry.putLong("cost", lot.costMilli());
            entry.putLong("labor", lot.laborMilli());
            inputs.add(entry);
        });
        tag.put("inputs", inputs);
        tag.putLong("fuel", shop.fuelTicks());
        tag.putLong("fuelCost", shop.fuelCostMilli());
        ListTag commissions = new ListTag();
        for (Business.Commission commission : shop.commissions()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", commission.id());
            entry.putLong("order", commission.order());
            entry.putString("item", commission.item());
            entry.putInt("count", commission.count());
            entry.putLong("paid", commission.paid());
            entry.putLong("day", commission.day());
            commissions.add(entry);
        }
        tag.put("commissions", commissions);
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
        // Shops of format 2 have no hands of the townsfolk: they take them on the next day of the townsfolk.
        shop.setFolkHands(tag.getInt("folkHands"));
        shop.setLastShared(tag.getLong("shared"));
        shop.restoreTrade(tag.getString("pinned"), tag.getString("nextTrade"), tag.contains("tradeSince") ? tag.getLong("tradeSince")
                : tag.getLong("opened"));
        for (CompoundTag entry : compounds(tag, "goods")) {
            GoodsCategory category = GoodsCategory.byId(entry.getString("category"))
                    .orElseThrow(() -> new IllegalArgumentException("Goods of an unknown kind " + entry.getString("category")));
            String item = entry.getString("item");
            if (item.isEmpty() || entry.getLong("count") < 0 || entry.getLong("cost") < 0 || entry.getLong("labor") < 0) {
                throw new IllegalArgumentException("Goods that cannot be read");
            }
            boolean made = entry.getBoolean("made");
            shop.restoreGoods(Business.key(item, made), new Business.Lot(category.ordinal(), entry.getLong("count"), entry.getLong("cost"),
                    entry.getLong("labor"), made));
        }
        for (CompoundTag entry : compounds(tag, "inputs")) {
            String item = entry.getString("item");
            if (item.isEmpty() || entry.getLong("count") < 0 || entry.getLong("cost") < 0 || entry.getLong("labor") < 0) {
                throw new IllegalArgumentException("Raw materials that cannot be read");
            }
            shop.restoreInput(item, new Business.Lot(-1, entry.getLong("count"), entry.getLong("cost"), entry.getLong("labor"), false));
        }
        shop.setFuel(tag.getLong("fuel"), tag.getLong("fuelCost"));
        for (CompoundTag entry : compounds(tag, "commissions")) {
            if (entry.getString("item").isEmpty() || entry.getLong("paid") < 0) {
                throw new IllegalArgumentException("Work paid in advance that cannot be read");
            }
            shop.addCommission(new Business.Commission(entry.getLong("id"), entry.getLong("order"), entry.getString("item"), entry.getInt("count"),
                    entry.getLong("paid"), entry.getLong("day")));
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
        data.restoreNextCommission(tag.getLong("nextCommission"));
        for (CompoundTag entry : compounds(tag, "shops")) {
            data.restore(readShop(entry));
        }
        for (CompoundTag entry : compounds(tag, "cities")) {
            CommerceSavedData.CityTrade trade = new CommerceSavedData.CityTrade();
            long[] carry = entry.getLongArray("carry");
            System.arraycopy(carry, 0, trade.carry, 0, Math.min(carry.length, trade.carry.length));
            long[] unmet = entry.getLongArray("unmet");
            System.arraycopy(unmet, 0, trade.unmet, 0, Math.min(unmet.length, trade.unmet.length));
            trade.outside = Math.max(0, entry.getLong("outside"));
            trade.mastersPass = entry.getBoolean("mastersPass");
            for (CompoundTag order : compounds(entry, "orders")) {
                trade.orders.put(order.getLong("shop"), Math.max(0, order.getLong("worth")));
            }
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
        for (CompoundTag entry : compounds(tag, "surveys")) {
            java.util.Set<String> stations = new java.util.LinkedHashSet<>();
            ListTag list = entry.getList("stations", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                stations.add(list.getString(i));
            }
            data.restoreSurvey(entry.getString("place"), new CommerceSavedData.Survey(stations, entry.getLong("day")));
        }
        for (CompoundTag entry : compounds(tag, "crownDays")) {
            if (entry.hasUUID("kingdom")) {
                data.restoreCrownDay(entry.getUUID("kingdom"), entry.getLong("day"), entry.getLong("spent"), entry.getLong("items"));
            }
        }
        for (CompoundTag entry : compounds(tag, "boughtFor")) {
            data.restoreBoughtFor(entry.getString("order"), Math.max(0, entry.getLong("count")));
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
