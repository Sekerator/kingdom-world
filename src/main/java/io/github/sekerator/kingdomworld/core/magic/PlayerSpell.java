package io.github.sekerator.kingdomworld.core.magic;

import java.util.Locale;
import java.util.Optional;

/**
 * Spells players learn at a focus and cast with a staff. Each cast spends energy of the crystals the player carries.
 */
public enum PlayerSpell {
    FIREBALL,
    HEAL,
    LIGHT,
    DETECT,
    SHIELD,
    BLINK;

    public static final int COUNT = values().length;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<PlayerSpell> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (PlayerSpell spell : values()) {
            if (spell.id().equals(id)) {
                return Optional.of(spell);
            }
        }
        return Optional.empty();
    }

    public static PlayerSpell ofIndex(int index) {
        return values()[Math.max(0, Math.min(COUNT - 1, index))];
    }
}
