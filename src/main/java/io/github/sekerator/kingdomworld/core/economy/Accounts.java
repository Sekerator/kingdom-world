package io.github.sekerator.kingdomworld.core.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The accounts of the players: what each player has in each currency. An account with nothing on it is forgotten.
 * Changed only through {@link Economy}.
 */
public final class Accounts {
    private final Map<UUID, Map<UUID, Long>> byPlayer = new LinkedHashMap<>();

    public long balance(UUID player, UUID currency) {
        Map<UUID, Long> accounts = byPlayer.get(player);
        return accounts == null ? 0L : accounts.getOrDefault(currency, 0L);
    }

    /**
     * What a player has, by currency.
     */
    public Map<UUID, Long> of(UUID player) {
        Map<UUID, Long> accounts = byPlayer.get(player);
        return accounts == null ? Map.of() : Collections.unmodifiableMap(accounts);
    }

    public Set<UUID> players() {
        return Collections.unmodifiableSet(byPlayer.keySet());
    }

    /**
     * What all players together have in a currency, at most {@link Money#MAX}.
     */
    public long total(UUID currency) {
        long sum = 0;
        for (Map<UUID, Long> accounts : byPlayer.values()) {
            sum = Money.addCapped(sum, accounts.getOrDefault(currency, 0L));
        }
        return sum;
    }

    boolean canCredit(UUID player, UUID currency, long amount) {
        return Money.isAmount(amount) && Money.add(balance(player, currency), amount).isPresent();
    }

    void credit(UUID player, UUID currency, long amount) {
        if (!canCredit(player, currency, amount)) {
            throw new IllegalArgumentException("Cannot credit " + amount + " to " + player);
        }
        set(player, currency, balance(player, currency) + amount);
    }

    void debit(UUID player, UUID currency, long amount) {
        if (!Money.isAmount(amount) || balance(player, currency) < amount) {
            throw new IllegalArgumentException("Cannot debit " + amount + " from " + player);
        }
        set(player, currency, balance(player, currency) - amount);
    }

    void set(UUID player, UUID currency, long amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        if (!Money.isAmount(amount)) {
            throw new IllegalArgumentException("Balance out of range: " + amount);
        }
        if (amount == 0) {
            Map<UUID, Long> accounts = byPlayer.get(player);
            if (accounts != null) {
                accounts.remove(currency);
                if (accounts.isEmpty()) {
                    byPlayer.remove(player);
                }
            }
            return;
        }
        byPlayer.computeIfAbsent(player, p -> new LinkedHashMap<>()).put(currency, amount);
    }

    /**
     * Forgets every account in a currency, for example when the data of a kingdom is restored again.
     */
    void clear() {
        byPlayer.clear();
    }
}
