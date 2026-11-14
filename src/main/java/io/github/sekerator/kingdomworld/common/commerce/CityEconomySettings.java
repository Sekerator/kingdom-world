package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.Treasury;

/**
 * The rules of the money of the cities of the running server, built from {@code [cityEconomy]} of the server config and refreshed
 * once a second by the economy, so that a change of the config takes effect without a restart.
 */
public final class CityEconomySettings {
    private static CityEconomyRules override;
    private static CityEconomyRules cached;

    private CityEconomySettings() {
    }

    public static CityEconomyRules rules() {
        if (override != null) {
            return override;
        }
        CityEconomyRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    public static boolean enabled() {
        return rules().enabled();
    }

    public static void refresh() {
        cached = fromConfig();
    }

    public static void reset() {
        cached = null;
        override = null;
    }

    /**
     * Replaces the configured rules, for game tests and the client self-test; {@code null} restores the config.
     */
    public static void override(CityEconomyRules rules) {
        override = rules;
    }

    private static CityEconomyRules fromConfig() {
        try {
            KingdomWorldConfig.CityEconomy c = KingdomWorldConfig.SERVER.cityEconomy;
            return new CityEconomyRules(c.enabled.get(), c.salesTaxPercent.get(), c.tithePercent.get(), c.crownSharePercent.get(),
                    c.idleReserveDays.get(), c.headReserveDays.get(), c.cityReserveDays.get(), c.crownShareFairPercent.get(),
                    c.purseKeepDays.get(), c.afarPercent.get(),
                    new int[]{c.basketFood.get(), c.basketClothing.get(), c.basketTools.get(), c.basketHousehold.get(), c.basketLuxury.get()},
                    c.businessUpkeepPercent.get(), c.turnoverPerWorker.get(), c.familyShopPercent.get(), c.fairIncomePercent.get(),
                    c.minMarginPercent.get(), c.maxMarginPercent.get(), c.marginStepPercent.get(), c.keeperUsualIncome.get(),
                    c.closeAfterDays.get(), c.shopCredit.get(), c.takeFromCrownPercent.get(), c.stockDays.get(), c.tavernEveningPrice.get(),
                    c.evasionStartPercent.get(), c.evasionMaxPercent.get());
        } catch (RuntimeException e) {
            // The config is not loaded (a unit check or a client without a server): the defaults hold.
            return CityEconomyRules.DEFAULT;
        }
    }

    /**
     * The taxes of the cities of a kingdom and the share of the crown, in thousandths: what its king set, else the config.
     */
    public static int salesTax(Treasury treasury) {
        return treasury.citySalesTax(rules().salesTaxPercent() * 10);
    }

    public static int tithe(Treasury treasury) {
        return treasury.cityTithe(rules().tithePercent() * 10);
    }

    public static int crownShare(Treasury treasury) {
        return treasury.crownShare(rules().crownSharePercent() * 10);
    }
}
