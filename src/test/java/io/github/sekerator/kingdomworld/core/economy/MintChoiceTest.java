package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MintChoiceTest {
    private static final CurrencyUnits UNITS = CurrencyUnits.DEFAULT;
    private static final List<Denomination> ALL = List.of(Denomination.COPPER, Denomination.SILVER, Denomination.GOLD);

    @Test
    void theVaultGetsTheCoinItHoldsTheLeastWorthOf() {
        assertEquals(Optional.empty(), MintChoice.forVault(List.of(), Coins.NONE, UNITS));
        // An empty vault: the first kind of the config.
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.forVault(ALL, Coins.NONE, UNITS));
        // Copper worth 5 against no silver or gold: silver, met before gold.
        assertEquals(Optional.of(Denomination.SILVER), MintChoice.forVault(ALL, new Coins(5, 0, 0), UNITS));
        // 30 copper (30) against 2 silver (20) and 1 gold (100): silver.
        assertEquals(Optional.of(Denomination.SILVER), MintChoice.forVault(ALL, new Coins(30, 2, 1), UNITS));
        // 30 copper (30) against 4 silver (40): copper, although there are more copper coins.
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.forVault(ALL, new Coins(30, 4, 1), UNITS));
        // Worth, not the number of coins, counts, in the units of the currency.
        assertEquals(Optional.of(Denomination.SILVER), MintChoice.forVault(List.of(Denomination.COPPER, Denomination.SILVER),
                new Coins(30, 4, 0), new CurrencyUnits(5, 100)));
        // Only the kinds the mint has metal for are chosen from.
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.forVault(List.of(Denomination.COPPER), new Coins(1_000, 0, 0), UNITS));
        // A tie goes to the kind met first in the order of the rates.
        assertEquals(Optional.of(Denomination.GOLD), MintChoice.forVault(List.of(Denomination.GOLD, Denomination.SILVER),
                new Coins(0, 10, 1), UNITS));
        assertEquals(Optional.of(Denomination.GOLD), MintChoice.forVault(ALL, new Coins(Money.MAX, Money.MAX, 0), UNITS));
    }

    @Test
    void aSteadyFlowOfCopperDoesNotStarveTheIron() {
        // The storehouses never run out of copper ingots (five copper coins each), and hold five iron ingots (two silver
        // coins each): striking into the vault, the mint uses up the iron long before it is done with copper.
        Coins vault = Coins.NONE;
        int iron = 5;
        int strikes = 0;
        List<Denomination> order = new ArrayList<>();
        while (iron > 0 && strikes < 100) {
            List<Denomination> available = iron > 0 ? List.of(Denomination.COPPER, Denomination.SILVER) : List.of(Denomination.COPPER);
            Denomination coin = MintChoice.forVault(available, vault, UNITS).orElseThrow();
            order.add(coin);
            if (coin == Denomination.SILVER) {
                iron--;
                vault = vault.plus(Denomination.SILVER, 2);
            } else {
                vault = vault.plus(Denomination.COPPER, 5);
            }
            strikes++;
        }
        assertEquals(0, iron, "the iron was left for ever: " + order);
        assertEquals(10, vault.silver());
        assertTrue(strikes <= 25, "the iron waited too long: " + order);
        // Silver comes in the second striking already, not after all the copper.
        assertEquals(Denomination.SILVER, order.get(1));
    }

    @Test
    void withoutTheVaultTheKindsTakeTurns() {
        assertEquals(Optional.empty(), MintChoice.inTurn(List.of(), Denomination.COPPER));
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.inTurn(ALL, null));
        assertEquals(Optional.of(Denomination.SILVER), MintChoice.inTurn(ALL, Denomination.COPPER));
        assertEquals(Optional.of(Denomination.GOLD), MintChoice.inTurn(ALL, Denomination.SILVER));
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.inTurn(ALL, Denomination.GOLD));
        // One kind only: it every time.
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.inTurn(List.of(Denomination.COPPER), Denomination.COPPER));
        // The metal of the last kind is gone: the next kind after it, or round again.
        assertEquals(Optional.of(Denomination.GOLD), MintChoice.inTurn(List.of(Denomination.COPPER, Denomination.GOLD), Denomination.SILVER));
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.inTurn(List.of(Denomination.COPPER, Denomination.SILVER), Denomination.GOLD));
        // A config that lists gold before copper keeps its own order.
        assertEquals(Optional.of(Denomination.COPPER), MintChoice.inTurn(List.of(Denomination.GOLD, Denomination.COPPER), Denomination.GOLD));
        // Turns over a steady flow of copper: every other striking is silver.
        Denomination last = null;
        int silver = 0;
        for (int i = 0; i < 10; i++) {
            last = MintChoice.inTurn(List.of(Denomination.COPPER, Denomination.SILVER), last).orElseThrow();
            silver += last == Denomination.SILVER ? 1 : 0;
        }
        assertEquals(5, silver);
    }
}
