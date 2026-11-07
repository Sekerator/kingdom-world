package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * The branch of the guild in a kingdom: the commission it keeps from rewards and the money the king lets the guild spend
 * on contracts of its own each day. Whether it works (a registrar at a desk) is seen in the world, not stored.
 *
 * @param budgetDay the game day {@code spentToday} counts
 * @param lastAuto  game time the guild last posted a contract of its own here
 */
public record Branch(UUID kingdomId, int commissionPermille, long dailyBudget, long budgetDay, long spentToday, long lastAuto) {
    public Branch {
        Objects.requireNonNull(kingdomId, "kingdomId");
        if (commissionPermille < 0 || commissionPermille > 1000 || !Money.isAmount(dailyBudget) || !Money.isAmount(spentToday)) {
            throw new IllegalArgumentException("Branch out of range: " + kingdomId);
        }
    }

    public static Branch open(UUID kingdomId, GuildRules rules) {
        return new Branch(kingdomId, rules.defaultCommissionPermille, rules.defaultDailyBudget, -1, 0, Long.MIN_VALUE);
    }

    /**
     * What the guild may still spend today.
     */
    public long budgetLeft(long day) {
        return day == budgetDay ? Math.max(0, dailyBudget - spentToday) : dailyBudget;
    }

    public Branch spent(long day, long amount) {
        long already = day == budgetDay ? spentToday : 0;
        return new Branch(kingdomId, commissionPermille, dailyBudget, day, Money.addCapped(already, amount), lastAuto);
    }

    public Branch withCommission(int permille) {
        return new Branch(kingdomId, permille, dailyBudget, budgetDay, spentToday, lastAuto);
    }

    public Branch withBudget(long amount) {
        return new Branch(kingdomId, commissionPermille, amount, budgetDay, spentToday, lastAuto);
    }

    public Branch withLastAuto(long time) {
        return new Branch(kingdomId, commissionPermille, dailyBudget, budgetDay, spentToday, time);
    }
}
