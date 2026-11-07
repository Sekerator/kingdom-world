package io.github.sekerator.kingdomworld.core.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * The currency of a kingdom that was dissolved. Its coins and accounts stay, marked as old; a later version
 * exchanges them.
 *
 * @param id           the id of the dissolved kingdom and of its currency
 * @param name         the name the king gave the currency, empty if it carried the kingdom's
 * @param kingdomName  the name of the kingdom
 * @param color        the color of the kingdom
 * @param retiredTime  game time of the dissolution
 */
public record RetiredCurrency(UUID id, String name, String kingdomName, int color, CurrencyUnits units, long retiredTime) {
    public RetiredCurrency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kingdomName, "kingdomName");
        Objects.requireNonNull(units, "units");
        color &= 0xFFFFFF;
    }
}
