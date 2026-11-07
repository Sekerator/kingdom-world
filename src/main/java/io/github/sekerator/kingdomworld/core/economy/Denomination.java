package io.github.sekerator.kingdomworld.core.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * The three coins of every currency, from the smallest to the largest. A copper coin is the smallest unit; what a
 * silver and a gold coin are worth is set per currency by {@link CurrencyUnits}.
 */
public enum Denomination {
    COPPER,
    SILVER,
    GOLD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Denomination> byId(String id) {
        for (Denomination denomination : values()) {
            if (denomination.id().equals(id)) {
                return Optional.of(denomination);
            }
        }
        return Optional.empty();
    }

    /**
     * The coins from the largest to the smallest, the order in which amounts are counted out.
     */
    public static Denomination[] largestFirst() {
        return new Denomination[]{GOLD, SILVER, COPPER};
    }
}
