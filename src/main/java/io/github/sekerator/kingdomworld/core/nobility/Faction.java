package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;

import java.util.Locale;
import java.util.Optional;

/**
 * Parties at a court. Houses join one by their character; part of the citizens lean to each.
 */
public enum Faction {
    /** Wants war, loot and a strong army. */
    WAR,
    /** Wants low taxes, peace and open roads. */
    MERCHANTS,
    /** Wants titles, land and the old privileges. */
    OLD_NOBILITY,
    /** Stands by the crown and wants order and works. */
    CROWN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Faction> byId(String id) {
        for (Faction faction : values()) {
            if (faction.id().equals(id)) {
                return Optional.of(faction);
            }
        }
        return Optional.empty();
    }

    /**
     * The party a house of a character and a trait joins.
     */
    public static Faction of(RulerCharacter character, RulerCharacter.Trait trait) {
        if (trait == RulerCharacter.Trait.PROUD || trait == RulerCharacter.Trait.AMBITIOUS) {
            return OLD_NOBILITY;
        }
        return switch (character) {
            case WARRIOR -> WAR;
            case MERCHANT -> MERCHANTS;
            case MAGE -> OLD_NOBILITY;
            case BUILDER -> CROWN;
        };
    }
}
