package io.github.sekerator.kingdomworld.core.economy;

/**
 * A number of coins of each kind, for example the coins in a treasury vault or the coins an amount is paid out in.
 * Counts are never negative and never larger than {@link Money#MAX}.
 */
public record Coins(long copper, long silver, long gold) {
    public static final Coins NONE = new Coins(0, 0, 0);

    public Coins {
        if (copper < 0 || silver < 0 || gold < 0 || copper > Money.MAX || silver > Money.MAX || gold > Money.MAX) {
            throw new IllegalArgumentException("Coin counts out of range: " + copper + "/" + silver + "/" + gold);
        }
    }

    public static Coins of(Denomination denomination, long count) {
        return switch (denomination) {
            case COPPER -> new Coins(count, 0, 0);
            case SILVER -> new Coins(0, count, 0);
            case GOLD -> new Coins(0, 0, count);
        };
    }

    public long count(Denomination denomination) {
        return switch (denomination) {
            case COPPER -> copper;
            case SILVER -> silver;
            case GOLD -> gold;
        };
    }

    public boolean isEmpty() {
        return copper == 0 && silver == 0 && gold == 0;
    }

    /**
     * Number of coins, at most {@link Money#MAX}.
     */
    public long total() {
        return Money.addCapped(Money.addCapped(copper, silver), gold);
    }

    /**
     * What the coins are worth, or {@link Money#MAX} if more.
     */
    public long value(CurrencyUnits units) {
        long sum = copper;
        sum = Money.addCapped(sum, Money.multiplyCapped(silver, units.silver()));
        return Money.addCapped(sum, Money.multiplyCapped(gold, units.gold()));
    }

    /**
     * These coins and {@code count} more of a kind.
     *
     * @throws ArithmeticException if a count would grow beyond {@link Money#MAX}
     */
    public Coins plus(Denomination denomination, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative count " + count);
        }
        long next = count(denomination) + count;
        if (count > Money.MAX || next > Money.MAX) {
            throw new ArithmeticException("Too many coins");
        }
        return with(denomination, next);
    }

    /**
     * These coins less {@code count} of a kind.
     *
     * @throws IllegalArgumentException if there are not that many
     */
    public Coins minus(Denomination denomination, long count) {
        if (count < 0 || count > count(denomination)) {
            throw new IllegalArgumentException("Cannot take " + count + " " + denomination + " of " + this);
        }
        return with(denomination, count(denomination) - count);
    }

    private Coins with(Denomination denomination, long count) {
        return switch (denomination) {
            case COPPER -> new Coins(count, silver, gold);
            case SILVER -> new Coins(copper, count, gold);
            case GOLD -> new Coins(copper, silver, count);
        };
    }

    /**
     * Counts out an amount in coins: as many coins of {@code largest} as fit, then the rest in smaller coins. The
     * coins are worth exactly the amount.
     *
     * @param largest the largest coin to use; {@link Denomination#GOLD} counts out with the fewest coins
     */
    public static Coins countOut(long amount, CurrencyUnits units, Denomination largest) {
        if (!Money.isAmount(amount)) {
            throw new IllegalArgumentException("Amount out of range: " + amount);
        }
        long rest = amount;
        long gold = 0;
        long silver = 0;
        if (largest == Denomination.GOLD) {
            gold = rest / units.gold();
            rest -= gold * units.gold();
        }
        if (largest != Denomination.COPPER) {
            silver = rest / units.silver();
            rest -= silver * units.silver();
        }
        return new Coins(rest, silver, gold);
    }
}
