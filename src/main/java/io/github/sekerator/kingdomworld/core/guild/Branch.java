package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * The branch of the guild in a kingdom: its rank, the commission it keeps from rewards and the money the king lets the guild
 * spend on contracts of its own each day. Whether it works (a registrar at a desk) is seen in the world, not stored.
 *
 * @param budgetDay the game day {@code spentToday} counts
 * @param lastAuto  game time the guild last posted a contract of its own here
 * @param rank      the rank of the branch: the highest exam taken here, the highest rank its own contracts and the contracts
 *                  posted here ask for
 * @param done      contracts finished at the branch, which a branch of players grows by
 */
public record Branch(UUID kingdomId, int commissionPermille, long dailyBudget, long budgetDay, long spentToday, long lastAuto, Rank rank,
                     long done) {
    /** The rank of a branch that has done nothing yet, and of one in data from before branches had ranks. */
    public static final Rank FIRST_RANK = Rank.E;

    public Branch {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(rank, "rank");
        if (commissionPermille < 0 || commissionPermille > 1000 || !Money.isAmount(dailyBudget) || !Money.isAmount(spentToday) || done < 0) {
            throw new IllegalArgumentException("Branch out of range: " + kingdomId);
        }
    }

    /**
     * A branch of the first rank that has done nothing yet.
     */
    public Branch(UUID kingdomId, int commissionPermille, long dailyBudget, long budgetDay, long spentToday, long lastAuto) {
        this(kingdomId, commissionPermille, dailyBudget, budgetDay, spentToday, lastAuto, FIRST_RANK, 0);
    }

    public static Branch open(UUID kingdomId, GuildRules rules) {
        return new Branch(kingdomId, rules.defaultCommissionPermille, rules.defaultDailyBudget, -1, 0, Long.MIN_VALUE);
    }

    /**
     * What the guild may still spend today: the budget of the king, and at least what the rank of the branch asks for.
     */
    public long budgetLeft(long day, GuildRules rules) {
        long budget = Math.max(dailyBudget, rules.ownBudget(rank));
        return day == budgetDay ? Math.max(0, budget - spentToday) : budget;
    }

    public Branch spent(long day, long amount) {
        long already = day == budgetDay ? spentToday : 0;
        return new Branch(kingdomId, commissionPermille, dailyBudget, day, Money.addCapped(already, amount), lastAuto, rank, done);
    }

    public Branch withCommission(int permille) {
        return new Branch(kingdomId, permille, dailyBudget, budgetDay, spentToday, lastAuto, rank, done);
    }

    public Branch withBudget(long amount) {
        return new Branch(kingdomId, commissionPermille, amount, budgetDay, spentToday, lastAuto, rank, done);
    }

    public Branch withLastAuto(long time) {
        return new Branch(kingdomId, commissionPermille, dailyBudget, budgetDay, spentToday, time, rank, done);
    }

    public Branch withRank(Rank value) {
        return new Branch(kingdomId, commissionPermille, dailyBudget, budgetDay, spentToday, lastAuto, value, done);
    }

    /**
     * One more contract finished at the branch.
     */
    public Branch finished() {
        return new Branch(kingdomId, commissionPermille, dailyBudget, budgetDay, spentToday, lastAuto, rank, Math.min(Long.MAX_VALUE - 1, done) + 1);
    }
}
