package io.github.sekerator.kingdomworld.core.membership;

import java.util.Locale;
import java.util.Optional;

/**
 * Positions at a kingdom's court. Every member holds exactly one; a kingdom has at most one {@link #KING}.
 * The order is the display order, from the highest position to the lowest.
 */
public enum Role {
    KING,
    MARSHAL,
    TREASURER,
    CHANCELLOR,
    SPYMASTER,
    COURT_MAGE,
    STEWARD,
    SUBJECT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Role> byId(String id) {
        for (Role role : values()) {
            if (role.id().equals(id)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the role can be handed out with {@code setRole}. The crown only changes hands by abdication.
     */
    public boolean isAssignable() {
        return this != KING;
    }

    /**
     * The next assignable role in display order, wrapping around; used by the throne screen to cycle roles.
     */
    public Role nextAssignable() {
        return stepAssignable(1);
    }

    /**
     * The assignable role one step from this one in display order, wrapping around: the next one for a direction
     * above zero, the one before otherwise. The throne screen goes forward on a left click and back on a right one.
     */
    public Role stepAssignable(int direction) {
        Role[] roles = values();
        Role found = this;
        do {
            found = roles[Math.floorMod(found.ordinal() + (direction > 0 ? 1 : -1), roles.length)];
        } while (!found.isAssignable());
        return found;
    }
}
