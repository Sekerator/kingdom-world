package io.github.sekerator.kingdomworld.core.economy;

/**
 * What the coins of a currency are worth in its smallest unit: a copper coin is always 1, a silver coin
 * {@code silver} and a gold coin {@code gold}. Fixed when the currency is created, so that coins never change their
 * worth.
 */
public record CurrencyUnits(long silver, long gold) {
    public static final long MAX_GOLD = 1_000_000L;
    public static final CurrencyUnits DEFAULT = new CurrencyUnits(10, 100);

    public CurrencyUnits {
        if (!valid(silver, gold)) {
            throw new IllegalArgumentException("Coin values out of range: silver " + silver + ", gold " + gold);
        }
    }

    public static boolean valid(long silver, long gold) {
        return silver >= 2 && gold > silver && gold <= MAX_GOLD;
    }

    /**
     * What one coin of the kind is worth.
     */
    public long value(Denomination denomination) {
        return switch (denomination) {
            case COPPER -> 1L;
            case SILVER -> silver;
            case GOLD -> gold;
        };
    }
}
