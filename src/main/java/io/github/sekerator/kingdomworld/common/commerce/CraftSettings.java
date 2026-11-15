package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;

/**
 * The rules of the workshops and trading houses of citizens of the running server, built from {@code [cityEconomy]} of the server
 * config and refreshed once a second with the money of the cities, so that a change of the config takes effect without a restart.
 */
public final class CraftSettings {
    private static CraftRules override;
    private static CraftRules cached;

    private CraftSettings() {
    }

    public static CraftRules rules() {
        if (override != null) {
            return override;
        }
        CraftRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
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
    public static void override(CraftRules rules) {
        override = rules;
    }

    private static CraftRules fromConfig() {
        try {
            KingdomWorldConfig.CityEconomy c = KingdomWorldConfig.SERVER.cityEconomy;
            return new CraftRules(c.crownBuysFromCitizens.get(), c.crownBuyCapPercent.get(), c.crownBuyPerDay.get(), c.crownBuyKeepWageDays.get(),
                    c.playerSellPercent.get(), c.playerSellOwnPercent.get(),
                    new long[]{c.outsideDemandVillage.get(), c.outsideDemandTown.get(), c.outsideDemandCity.get()}, c.outsideDemandPerRootPerson.get(),
                    c.outsideDemandFullCities.get(), c.traderBuyPercent.get(), c.marketDemandFactor.get(), c.roadLinkPercent.get(),
                    new int[]{c.craftExportVillage.get(), c.craftExportTown.get(), c.craftExportCity.get()}, c.regionalMarket.get(),
                    c.houseWhenUnmetPercent.get());
        } catch (RuntimeException e) {
            // The config is not loaded (a unit check or a client without a server): the defaults hold.
            return CraftRules.DEFAULT;
        }
    }
}
