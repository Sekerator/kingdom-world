package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;

/**
 * The economy rules of the running server, built from the server config and refreshed once per second like the other
 * rules. Readable only while a server runs; game tests may replace them.
 */
public final class EconomySettings {
    private static EconomyRules override;
    private static EconomyRules cached;
    private static MintRates mintRates;
    private static int mintTicksOverride;

    private EconomySettings() {
    }

    public static EconomyRules rules() {
        if (override != null) {
            return override;
        }
        EconomyRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    /**
     * What the mint strikes from what.
     */
    public static MintRates mintRates() {
        MintRates rates = mintRates;
        if (rates == null) {
            rates = MintRates.fromConfig(KingdomWorldConfig.SERVER.mintRates.get());
            mintRates = rates;
        }
        return rates;
    }

    /**
     * Ticks a beginner minter needs for the coins of one item.
     */
    public static int mintTicks() {
        return mintTicksOverride > 0 ? mintTicksOverride : KingdomWorldConfig.SERVER.mintSeconds.get() * 20;
    }

    /**
     * Replaces the time of a striking, for game tests; 0 restores the config.
     */
    public static void overrideMintTicks(int ticks) {
        mintTicksOverride = Math.max(0, ticks);
    }

    public static void refresh() {
        cached = fromConfig();
        mintRates = null;
    }

    public static void reset() {
        cached = null;
        override = null;
        mintRates = null;
        mintTicksOverride = 0;
    }

    private static EconomyRules fromConfig() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        int silver = config.silverValue.get();
        int gold = config.goldValue.get();
        CurrencyUnits units = CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
        int maxTax = config.maxPollTax.get();
        return new EconomyRules(units, Math.min(config.defaultPollTax.get(), maxTax), maxTax, config.maxWage.get(),
                EconomyRules.parseWages(config.wages.get()), config.moodWorkEffect.get(), config.payConfirmAbove.get(),
                config.mintToVault.get());
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(EconomyRules rules) {
        override = rules;
    }

    /**
     * How fast a citizen at this mood works.
     */
    public static double workSpeed(int mood) {
        return io.github.sekerator.kingdomworld.core.economy.Payroll.workSpeed(mood, rules().moodWorkEffect());
    }
}
