package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The shops of the citizens and what each city remembers of its trade, stored with the overworld in
 * {@code <world>/data/kingdomworld_commerce.dat} (see {@link CommerceNbt}). A file that cannot be read loses its shops only: their
 * tills stay among the purses of the economy and go to the townspeople of the capital on the next day of the city.
 */
public final class CommerceSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_commerce";

    /**
     * What a city remembers of its trade.
     *
     * @param carry    the fractions of the split of the purse carried to the next day, by kind of goods
     * @param unspent  what its townspeople did not find to spend at its last day
     * @param short_   whether at its last day its shops could not get the goods of the crown they wanted
     * @param missing  the item they lacked most, empty for none
     * @param closed   places where a shop closed of poverty, and the day it did
     */
    public static final class CityTrade {
        long[] carry = new long[io.github.sekerator.kingdomworld.core.commerce.GoodsCategory.values().length];
        long unspent;
        boolean short_;
        String missing = "";
        boolean noPlaces;
        boolean noKeepers;
        final Map<String, Long> closed = new LinkedHashMap<>();

        public long unspent() {
            return unspent;
        }

        public boolean crownShort() {
            return short_;
        }

        public String missing() {
            return missing;
        }
    }

    /**
     * A counter a player put up, or one found when its chunk was looked at.
     */
    public record Counter(UUID kingdom, String dimension, int x, int y, int z) {
        public String key() {
            return dimension + "/" + x + "/" + y + "/" + z;
        }
    }

    private final Map<String, Counter> counters = new LinkedHashMap<>();
    private final Map<Long, Business> shops = new LinkedHashMap<>();
    private final Map<String, CityTrade> cities = new HashMap<>();
    private long nextId = 1;

    public static CommerceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CommerceSavedData::load, CommerceSavedData::new, DATA_NAME);
    }

    public static CommerceSavedData load(CompoundTag tag) {
        CommerceSavedData data = new CommerceSavedData();
        try {
            CommerceNbt.read(tag, data);
        } catch (RuntimeException e) {
            // The shops are lost, their money is not: the tills go to the capital on the next day of their city.
            LOGGER.error("KingdomWorld could not read the shops of the citizens; they are gone, their tills go to the capitals", e);
            data.shops.clear();
            data.cities.clear();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CommerceNbt.write(this, tag);
    }

    // ---------------------------------------------------------------- shops

    public Map<Long, Business> shops() {
        return Collections.unmodifiableMap(shops);
    }

    public Optional<Business> shop(long id) {
        return Optional.ofNullable(shops.get(id));
    }

    public List<Business> shopsOf(UUID kingdom, long city) {
        List<Business> result = new ArrayList<>();
        for (Business shop : shops.values()) {
            if (shop.kingdomId().equals(kingdom) && shop.cityId() == city) {
                result.add(shop);
            }
        }
        return result;
    }

    public List<Business> shopsOf(UUID kingdom) {
        List<Business> result = new ArrayList<>();
        for (Business shop : shops.values()) {
            if (shop.kingdomId().equals(kingdom)) {
                result.add(shop);
            }
        }
        return result;
    }

    public long nextId() {
        return nextId;
    }

    public Business open(UUID kingdom, long city, Business.Place place, long day, int margin) {
        Business shop = new Business(nextId++, kingdom, city, place, day, margin);
        shops.put(shop.id(), shop);
        setDirty();
        return shop;
    }

    public void remove(long id) {
        if (shops.remove(id) != null) {
            setDirty();
        }
    }

    void restore(Business shop) {
        shops.put(shop.id(), shop);
        nextId = Math.max(nextId, shop.id() + 1);
    }

    void restoreNextId(long value) {
        nextId = Math.max(nextId, value);
    }

    // ---------------------------------------------------------------- counters

    public Map<String, Counter> counters() {
        return Collections.unmodifiableMap(counters);
    }

    public static String counterKey(String dimension, int x, int y, int z) {
        return dimension + "/" + x + "/" + y + "/" + z;
    }

    public void putCounter(Counter counter) {
        Counter before = counters.put(counter.key(), counter);
        if (!counter.equals(before)) {
            setDirty();
        }
    }

    public Optional<Counter> removeCounter(String key) {
        Counter removed = counters.remove(key);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    void restoreCounter(Counter counter) {
        counters.put(counter.key(), counter);
    }

    // ---------------------------------------------------------------- cities

    private static String key(UUID kingdom, long city) {
        return kingdom + "/" + city;
    }

    public CityTrade city(UUID kingdom, long city) {
        return cities.computeIfAbsent(key(kingdom, city), k -> new CityTrade());
    }

    public Optional<CityTrade> cityIfKnown(UUID kingdom, long city) {
        return Optional.ofNullable(cities.get(key(kingdom, city)));
    }

    Map<String, CityTrade> cities() {
        return cities;
    }

    void restoreCity(String key, CityTrade trade) {
        cities.put(key, trade);
    }

    public void forgetCity(UUID kingdom, long city) {
        if (cities.remove(key(kingdom, city)) != null) {
            setDirty();
        }
    }

    /**
     * Whether a business of a kingdom stands at a place already.
     */
    public Optional<Business> at(String placeKey) {
        for (Business shop : shops.values()) {
            if (shop.place().key().equals(placeKey)) {
                return Optional.of(shop);
            }
        }
        return Optional.empty();
    }
}
