package io.github.sekerator.kingdomworld.core.economy;

import java.util.UUID;

/**
 * The taxes the cities of a kingdom collected on a day: the tithe and the sales tax that came into their treasuries after what
 * their people evaded. They are income of the kingdom when they are collected, for the shares of its nobles, its tributes and
 * its rebels; the share the cities send the crown later is the same money again and does not count.
 */
public final class CityTaxIncome {
    private CityTaxIncome() {
    }

    public static long of(Economy economy, UUID kingdom, long day) {
        long sum = 0;
        for (CityTreasury city : economy.cityTreasuriesOf(kingdom)) {
            sum = Money.addCapped(sum, city.journal().income(CityLedger.TITHE, day, day));
            sum = Money.addCapped(sum, city.journal().income(CityLedger.SALES_TAX, day, day));
        }
        return sum;
    }
}
