package io.github.sekerator.kingdomworld.core.economy;

import java.util.List;
import java.util.Optional;

/**
 * Which coin the mint strikes next when the storehouses hold metal for more than one kind. Without this choice a steady
 * flow of one metal (copper from a smelter, say) would keep the mint busy with it alone and the other metal would wait
 * for ever.
 * <p>
 * When struck coins go into the vault, the vault should hold the same worth of every kind of coin, so that it can pay
 * out and give change in each: the mint strikes the kind whose coins in the vault are worth the least. When they go
 * straight onto the balance, the vault says nothing about the mint's work, and the kinds take turns.
 */
public final class MintChoice {
    private MintChoice() {
    }

    /**
     * The kind of coin, of those the mint has metal for, whose coins in the vault are worth the least; a tie goes to the
     * kind met first. Empty if the mint has no metal at all.
     *
     * @param available the kinds the mint has metal for, in the order of the rates of the config
     */
    public static Optional<Denomination> forVault(List<Denomination> available, Coins vault, CurrencyUnits units) {
        Denomination best = null;
        long least = Long.MAX_VALUE;
        for (Denomination coin : available) {
            long worth = Money.multiplyCapped(vault.count(coin), units.value(coin));
            if (best == null || worth < least) {
                best = coin;
                least = worth;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The kind of coin after the one struck last, in the order of the available kinds and round again from the first,
     * so that every metal gets its turn. Without a last kind, or when its metal is gone, the turn goes to the first kind
     * after it in the order of the coins, or else to the first available. Empty if the mint has no metal at all.
     *
     * @param available the kinds the mint has metal for, in the order of the rates of the config
     * @param last      the kind struck last, or {@code null}
     */
    public static Optional<Denomination> inTurn(List<Denomination> available, Denomination last) {
        if (available.isEmpty()) {
            return Optional.empty();
        }
        if (last == null) {
            return Optional.of(available.get(0));
        }
        int at = available.indexOf(last);
        if (at >= 0) {
            return Optional.of(available.get((at + 1) % available.size()));
        }
        for (Denomination coin : available) {
            if (coin.ordinal() > last.ordinal()) {
                return Optional.of(coin);
            }
        }
        return Optional.of(available.get(0));
    }
}
