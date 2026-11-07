package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Goods the guards of a kingdom took from an executed criminal. The record lives in the data of the law, the goods
 * themselves with it (not in a chest in the world), so nothing can be copied. The owner buys them back whole; after
 * {@link LawRules#confiscationDays} days nobody claimed, they go to the storehouses of the kingdom.
 *
 * @param worth what the goods are worth, for the ransom
 * @param items how many items there are
 * @param day   the game day they were taken
 */
public record Confiscation(long id, UUID kingdomId, String kingdomName, UUID owner, String ownerName, long time, long day, long worth,
                           int items) {
    public Confiscation {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(owner, "owner");
        kingdomName = kingdomName == null ? "" : kingdomName;
        ownerName = ownerName == null ? "" : ownerName;
        if (id < 1 || !Money.isAmount(worth) || items < 0) {
            throw new IllegalArgumentException("Confiscation out of range: #" + id);
        }
    }

    public boolean expired(long today, LawRules rules) {
        return today - day >= rules.confiscationDays;
    }

    /**
     * Game days left before the goods go to the storehouses.
     */
    public long daysLeft(long today, LawRules rules) {
        return Math.max(0, rules.confiscationDays - (today - day));
    }
}
