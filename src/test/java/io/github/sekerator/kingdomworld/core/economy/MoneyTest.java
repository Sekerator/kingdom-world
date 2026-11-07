package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {
    private static final CurrencyUnits UNITS = CurrencyUnits.DEFAULT;

    @Test
    void amountsAreReadInUnitsOrCoins() {
        assertEquals(Optional.of(125L), Money.parse("125", UNITS));
        assertEquals(Optional.of(125L), Money.parse("1g2s5c", UNITS));
        assertEquals(Optional.of(125L), Money.parse("5c 1g 2s", UNITS));
        assertEquals(Optional.of(300L), Money.parse("3G", UNITS));
        assertEquals(Optional.of(125L), Money.parse("1з2с5м", UNITS));
        assertEquals(Optional.of(0L), Money.parse("0", UNITS));
        assertEquals(Optional.of(1_205L), Money.parse("1g2s5c", new CurrencyUnits(100, 1_000)));
        for (String bad : new String[]{"", " ", "-5", "1.5", "g", "5x", "1g1g", "1gs", "12345678901234567890", "abc", null}) {
            assertEquals(Optional.empty(), Money.parse(bad, UNITS), "for " + bad);
        }
        assertEquals(Optional.empty(), Money.parse("10000000000000000g", UNITS), "an overflow is refused");
        assertEquals(Optional.empty(), Money.parse(String.valueOf(Money.MAX + 1), UNITS));
        assertEquals(Optional.of(Money.MAX), Money.parse(String.valueOf(Money.MAX), UNITS));
    }

    @Test
    void sumsNeverOverflow() {
        assertEquals(5L, Money.add(2, 3).orElseThrow());
        assertTrue(Money.add(Money.MAX, 1).isEmpty());
        assertTrue(Money.add(-1, 1).isEmpty());
        assertTrue(Money.add(Long.MAX_VALUE, Long.MAX_VALUE).isEmpty());
        assertEquals(Money.MAX, Money.addCapped(Money.MAX, Money.MAX));
        assertEquals(Money.MAX, Money.addCapped(Long.MAX_VALUE, 5));
        assertEquals(Money.MAX, Money.multiplyCapped(Long.MAX_VALUE, 1_000));
        assertEquals(0, Money.multiplyCapped(-5, 10));
        assertEquals(50, Money.share(100, 1, 2));
        assertEquals(Money.MAX, Money.share(Money.MAX, Money.MAX, 1));
        assertEquals(333, Money.share(1_000, 1, 3));
        assertFalse(Money.isPayment(0));
        assertTrue(Money.isPayment(Money.MAX));
        assertFalse(Money.isPayment(Money.MAX + 1));
    }

    @Test
    void amountsAreCountedOutInCoinsOfExactlyTheirWorth() {
        assertEquals(new Coins(5, 2, 1), Coins.countOut(125, UNITS, Denomination.GOLD));
        assertEquals(new Coins(5, 12, 0), Coins.countOut(125, UNITS, Denomination.SILVER));
        assertEquals(new Coins(125, 0, 0), Coins.countOut(125, UNITS, Denomination.COPPER));
        for (long amount : new long[]{0, 1, 9, 10, 99, 100, 101, 12_345, Money.MAX}) {
            for (Denomination largest : Denomination.values()) {
                assertEquals(amount, Coins.countOut(amount, UNITS, largest).value(UNITS), amount + " in " + largest);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Coins.countOut(-1, UNITS, Denomination.GOLD));
    }

    @Test
    void coinsKeepTheirCountsInRange() {
        Coins coins = Coins.NONE.plus(Denomination.GOLD, 3).plus(Denomination.COPPER, 7);
        assertEquals(307, coins.value(UNITS));
        assertEquals(new Coins(7, 0, 1), coins.minus(Denomination.GOLD, 2));
        assertThrows(IllegalArgumentException.class, () -> coins.minus(Denomination.SILVER, 1));
        assertThrows(ArithmeticException.class, () -> coins.plus(Denomination.GOLD, Money.MAX));
        assertEquals(Money.MAX, new Coins(0, 0, Money.MAX).value(UNITS), "worth is capped instead of overflowing");
        assertThrows(IllegalArgumentException.class, () -> new CurrencyUnits(10, 10));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyUnits(1, 100));
    }
}
