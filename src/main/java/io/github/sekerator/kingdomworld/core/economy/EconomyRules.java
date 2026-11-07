package io.github.sekerator.kingdomworld.core.economy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server settings of the economy.
 *
 * @param units           what the silver and the gold coin of a new currency are worth
 * @param defaultPollTax  the daily tax of a subject in a new kingdom
 * @param maxPollTax      the highest daily tax a king may set
 * @param maxWage         the highest daily wage a king may set for a profession
 * @param defaultWages    the usual daily wage of each profession; citizens measure their pay against it
 * @param moodWorkEffect  how much mood changes the pace of work, from 0 to 0.5 (see {@link Payroll#workSpeed})
 * @param confirmAbove    payments between players above this amount ask for confirmation
 * @param mintToVault     whether struck coins go to the vault of the treasury rather than straight to its balance
 */
public record EconomyRules(CurrencyUnits units, long defaultPollTax, long maxPollTax, long maxWage, Map<String, Long> defaultWages,
                           double moodWorkEffect, long confirmAbove, boolean mintToVault) {
    /** The usual daily wages of the professions of the base game. */
    public static final List<String> DEFAULT_WAGES = List.of("unemployed:0", "farmer:6", "lumberjack:6", "miner:8", "fisher:5", "herder:5",
            "carrier:4", "smelter:6", "cook:6", "crafter:7", "smith:10", "minter:8", "builder:7");
    public static final EconomyRules DEFAULT = new EconomyRules(CurrencyUnits.DEFAULT, 10, 1_000, 200, parseWages(DEFAULT_WAGES), 0.2,
            1_000, true);

    public EconomyRules {
        Objects.requireNonNull(units, "units");
        defaultWages = Map.copyOf(defaultWages);
        if (!Money.isAmount(maxPollTax) || defaultPollTax < 0 || defaultPollTax > maxPollTax || !Money.isAmount(maxWage)
                || !Money.isAmount(confirmAbove) || !(moodWorkEffect >= 0.0 && moodWorkEffect <= 0.5)) {
            throw new IllegalArgumentException("Economy rules out of range");
        }
        for (long wage : defaultWages.values()) {
            if (!Money.isAmount(wage)) {
                throw new IllegalArgumentException("Wage out of range: " + wage);
            }
        }
    }

    /**
     * The usual daily wage of a profession; 0 for professions without one.
     */
    public long defaultWage(String profession) {
        return defaultWages.getOrDefault(profession, 0L);
    }

    /**
     * Reads one wage of the config, {@code profession:amount}.
     */
    public static Optional<Map.Entry<String, Long>> parseWage(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] parts = text.split(":");
        if (parts.length != 2 || !io.github.sekerator.kingdomworld.core.citizen.Profession.isValidId(parts[0].strip())) {
            return Optional.empty();
        }
        try {
            long amount = Long.parseLong(parts[1].strip());
            return Money.isAmount(amount) ? Optional.of(Map.entry(parts[0].strip(), amount)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the wages of the config; entries that cannot be read are left out.
     */
    public static Map<String, Long> parseWages(List<? extends String> texts) {
        Map<String, Long> wages = new LinkedHashMap<>();
        for (String text : texts) {
            parseWage(text).ifPresent(entry -> wages.put(entry.getKey(), entry.getValue()));
        }
        return wages;
    }
}
