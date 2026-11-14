package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.UUID;

/**
 * The money the head of a city may spend from its treasury: what it holds, less what the head promised its constructions and
 * a reserve of a few days of its income. The head pays only through these methods; what it pays the crown for (materials of
 * the storehouses) goes to the treasury of the kingdom, what it pays abroad leaves the world.
 *
 * <p>Cities have no heads yet; this is ready for them and spends nothing until then.
 */
public final class CityFunds {
    private CityFunds() {
    }

    /**
     * What the head of a city may spend now.
     */
    public static long available(Economy economy, UUID kingdom, long city, CityEconomyRules rules) {
        CityTreasury treasury = economy.openCityTreasury(kingdom, city);
        long reserve = Money.multiplyCapped(rules.headReserveDays(), treasury.averageTax());
        return Math.max(0, economy.purse(treasury.key()) - treasury.committed() - reserve);
    }

    /**
     * The head of a city spends money of its treasury: to the crown for its goods, or out of the world for goods from abroad.
     *
     * @param toCrown whether the crown gets the money (materials of its storehouses), else it leaves the world
     */
    public static Outcome spend(Economy economy, UUID kingdom, long city, long amount, CityLedger line, boolean toCrown, long day,
                                CityEconomyRules rules) {
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount > available(economy, kingdom, city, rules)) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        CityPurses.PurseKey key = CityPurses.PurseKey.cityTreasury(kingdom, city);
        return toCrown ? economy.purseToTreasury(key, amount, LedgerCategory.CITIES, line, day)
                : economy.purseLeaves(key, city, amount, CityPurses.Outflow.IMPORT, day);
    }

    /**
     * The head promises a construction money of the treasury; it stays there, but nothing else may spend it.
     */
    public static Outcome commit(Economy economy, UUID kingdom, long city, long amount, CityEconomyRules rules) {
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount > available(economy, kingdom, city, rules)) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        return economy.cityCommit(kingdom, city, amount) ? Outcome.DONE : Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
    }

    /**
     * A construction no longer needs what was promised to it.
     */
    public static Outcome release(Economy economy, UUID kingdom, long city, long amount) {
        CityTreasury treasury = economy.openCityTreasury(kingdom, city);
        long freed = Math.min(Math.max(0, amount), treasury.committed());
        return economy.cityCommit(kingdom, city, -freed) ? Outcome.DONE : Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
    }
}
