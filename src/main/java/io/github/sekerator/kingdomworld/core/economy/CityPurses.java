package io.github.sekerator.kingdomworld.core.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The money of the cities of the kingdoms of players that lies outside the treasuries and the accounts of players: the purse
 * of the townspeople of each city, the treasury of each city and the till of each shop, always in the currency of their
 * kingdom. Not to be mistaken for the purses a player pays from with coins or the account
 * ({@code common.trade.Purses}).
 *
 * <p>The balances are kept here and changed only through {@link Economy}, whose every movement takes from one place and gives
 * to another in the same call, or changes nothing. Purses are not accounts: players, debts, the bank and payments between
 * players never see them.
 *
 * <p>What leaves the world from a purse (goods from afar, the upkeep of shops, imports of a city, a purse too full) is counted
 * by city and day for the reports; the count is not saved.
 */
public final class CityPurses {
    /** Days the count of money that left the world remembers. */
    public static final int DAYS_KEPT = 8;

    /**
     * What a purse is. New kinds come only at the end; they are saved by name.
     */
    public enum PurseKind {
        /** The purse of the townspeople of a city: what they earned and did not spend yet. Its id is the city, 0 the capital. */
        CITY_PURSE,
        /** The treasury of a city. Its id is the city, 0 the capital. */
        CITY_TREASURY,
        /** The till of a shop. Its id is the shop. */
        TILL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<PurseKind> byId(String id) {
            for (PurseKind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Where money that leaves the world from a purse goes.
     */
    public enum Outflow {
        /** Goods the townspeople buy from afar. */
        AFAR,
        /** The upkeep of a shop: rent, firewood, small things. */
        UPKEEP,
        /** Goods a city buys abroad. */
        IMPORT,
        /** A purse of the townspeople that holds more than it keeps. */
        PURSE_FULL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One purse.
     *
     * @param kingdom the kingdom, whose currency the purse holds
     * @param id      the city (0 the capital) or the shop
     */
    public record PurseKey(PurseKind kind, UUID kingdom, long id) {
        public PurseKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(kingdom, "kingdom");
        }

        public static PurseKey cityPurse(UUID kingdom, long city) {
            return new PurseKey(PurseKind.CITY_PURSE, kingdom, city);
        }

        public static PurseKey cityTreasury(UUID kingdom, long city) {
            return new PurseKey(PurseKind.CITY_TREASURY, kingdom, city);
        }

        public static PurseKey till(UUID kingdom, long shop) {
            return new PurseKey(PurseKind.TILL, kingdom, shop);
        }
    }

    private final Map<PurseKey, Long> balances = new LinkedHashMap<>();
    /** Money that left the world, by day, then by kingdom and city, then by outflow. */
    private final NavigableMap<Long, Map<String, long[]>> outflows = new TreeMap<>();

    /**
     * What a purse holds; 0 for one never used.
     */
    public long balance(PurseKey key) {
        return balances.getOrDefault(key, 0L);
    }

    /**
     * Every purse that holds money, for saving and for checks.
     */
    public Map<PurseKey, Long> all() {
        return Collections.unmodifiableMap(balances);
    }

    /**
     * The purses of a kingdom that hold money.
     */
    public List<PurseKey> keysOf(UUID kingdom) {
        List<PurseKey> keys = new ArrayList<>();
        for (PurseKey key : balances.keySet()) {
            if (key.kingdom().equals(kingdom)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * All the money of the purses of a kingdom, in its currency.
     */
    public long total(UUID kingdom) {
        long sum = 0;
        for (Map.Entry<PurseKey, Long> entry : balances.entrySet()) {
            if (entry.getKey().kingdom().equals(kingdom)) {
                sum = Money.addCapped(sum, entry.getValue());
            }
        }
        return sum;
    }

    boolean canReceive(PurseKey key, long amount) {
        return Money.isAmount(amount) && Money.add(balance(key), amount).isPresent();
    }

    /**
     * Sets what a purse holds; an empty purse is forgotten. Only {@link Economy} moves money.
     */
    void set(PurseKey key, long amount) {
        if (!Money.isAmount(amount)) {
            throw new IllegalArgumentException("Purse " + key + " out of range: " + amount);
        }
        if (amount == 0) {
            balances.remove(key);
        } else {
            balances.put(key, amount);
        }
    }

    /**
     * Restores a purse read from saved data.
     */
    public void restore(PurseKey key, long amount) {
        if (balances.containsKey(key)) {
            throw new IllegalArgumentException("Two purses " + key);
        }
        set(key, amount);
    }

    // ---------------------------------------------------------------- money that left the world

    private static String cityKey(UUID kingdom, long city) {
        return kingdom + "/" + city;
    }

    void noteOutflow(UUID kingdom, long city, Outflow outflow, long amount, long day) {
        if (amount <= 0) {
            return;
        }
        long[] counts = outflows.computeIfAbsent(day, d -> new LinkedHashMap<>())
                .computeIfAbsent(cityKey(kingdom, city), k -> new long[Outflow.values().length]);
        counts[outflow.ordinal()] = Money.addCapped(counts[outflow.ordinal()], amount);
        while (!outflows.isEmpty() && outflows.firstKey() <= day - DAYS_KEPT) {
            outflows.pollFirstEntry();
        }
    }

    /**
     * What left the world from the purses, the treasury and the tills of a city on a day, by outflow.
     */
    public Map<Outflow, Long> outflows(UUID kingdom, long city, long day) {
        Map<Outflow, Long> result = new EnumMap<>(Outflow.class);
        Map<String, long[]> ofDay = outflows.get(day);
        long[] counts = ofDay == null ? null : ofDay.get(cityKey(kingdom, city));
        if (counts != null) {
            for (Outflow outflow : Outflow.values()) {
                if (counts[outflow.ordinal()] > 0) {
                    result.put(outflow, counts[outflow.ordinal()]);
                }
            }
        }
        return result;
    }

    /**
     * Forgets every purse of a kingdom without moving its money, for a kingdom whose data could not be restored.
     */
    void forget(UUID kingdom) {
        balances.keySet().removeIf(key -> key.kingdom().equals(kingdom));
    }
}
