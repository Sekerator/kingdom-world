package io.github.sekerator.kingdomworld.core.magic;

import java.util.Locale;
import java.util.Optional;

/**
 * Where the energy of a kingdom came from or went to, for its journal.
 */
public enum EnergyCategory {
    /** Crystals dissolved by mages at a focus. */
    CRYSTALS,
    /** Changes made by an operator. */
    ADMIN,
    /** Research of the mage tower. */
    RESEARCH,
    /** Spells of the kingdom: the ward, healing, fertility, the fair wind. */
    SPELLS,
    /** Travel through the gates. */
    GATES,
    /** Spells mages cast to defend the land. */
    DEFENSE,
    /** Crystal generators feeding machines. */
    MACHINES,
    /** Mana and source given to the magic of other mods. */
    MANA;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<EnergyCategory> byId(String id) {
        for (EnergyCategory category : values()) {
            if (category.id().equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
