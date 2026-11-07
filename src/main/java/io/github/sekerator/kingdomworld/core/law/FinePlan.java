package io.github.sekerator.kingdomworld.core.law;

/**
 * How a fine is paid: coins the player carries first, then the account in the kingdom's currency, and what is still missing
 * becomes a debt to the treasury. The three parts always add up to the fine.
 */
public record FinePlan(long fromCoins, long fromAccount, long toDebt) {

    public FinePlan {
        if (fromCoins < 0 || fromAccount < 0 || toDebt < 0) {
            throw new IllegalArgumentException("A part of a fine below zero");
        }
    }

    /**
     * @param coins   worth of the coins of the kingdom the player carries
     * @param account what the account in the kingdom's currency holds
     */
    public static FinePlan of(long fine, long coins, long account) {
        long left = Math.max(0, fine);
        long fromCoins = Math.min(left, Math.max(0, coins));
        left -= fromCoins;
        long fromAccount = Math.min(left, Math.max(0, account));
        left -= fromAccount;
        return new FinePlan(fromCoins, fromAccount, left);
    }

    public long total() {
        return fromCoins + fromAccount + toDebt;
    }
}
