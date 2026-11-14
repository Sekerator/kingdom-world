package io.github.sekerator.kingdomworld.core.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * What the money of the treasury of a city came in for or went out for. Saved by name; new ones only at the end.
 */
public enum CityLedger {
    /** The tithe of the earnings of the shopkeepers. */
    TITHE,
    /** The tax on the sales of the shops of the city. */
    SALES_TAX,
    /** The share of the crown of the taxes of the day. */
    CROWN_SHARE,
    /** What a city keeps beyond its reserve, sent to the crown. */
    IDLE_SHARE,
    /** Money the crown granted. */
    GRANT,
    /** Money the crown took back. */
    RECALL,
    /** Materials of the constructions of the city. */
    MATERIALS,
    /** Goods the city bought abroad. */
    IMPORTS,
    /** Goods the city bought from its people. */
    PURCHASES,
    /** Everything else, such as money found without a city. */
    OTHER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CityLedger> byId(String id) {
        for (CityLedger line : values()) {
            if (line.id().equals(id)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the line is a tax the city collected: income of the city that the nobles and tributes count as income.
     */
    public boolean isTax() {
        return this == TITHE || this == SALES_TAX;
    }
}
