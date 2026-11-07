package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.economy.Money;

/**
 * The good name of a player in one kingdom, from -100 to 100. It weighs a little: market prices move by up to
 * {@link LawRules#priceEffectPercent} percent, a high reputation enters cities for free, and the guards forget petty crimes
 * of a respected player sooner.
 */
public final class Reputation {
    public static final int MAX = 100;

    private Reputation() {
    }

    public static int clamp(long value) {
        return (int) Math.max(-MAX, Math.min(MAX, value));
    }

    /**
     * The factor on prices for a player of this reputation, in permille: 1000 - 100 for the best name at 10 percent, 1000 + 100
     * for the worst.
     */
    public static int pricePermille(int reputation, int effectPercent) {
        return 1000 - clamp(reputation) * Math.max(0, Math.min(100, effectPercent)) / 10;
    }

    /**
     * A price as this player pays it, rounded down but never below a coin, so that a good name never makes things free.
     */
    public static long price(long base, int reputation, int effectPercent) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, Money.share(base, pricePermille(reputation, effectPercent), 1000));
    }

    /**
     * What a kingdom pays this player for goods, rounded down: a good name earns a little more, a bad one less.
     */
    public static long payout(long base, int reputation, int effectPercent) {
        if (base <= 0) {
            return 0;
        }
        return Money.share(base, 2000L - pricePermille(reputation, effectPercent), 1000);
    }
}
