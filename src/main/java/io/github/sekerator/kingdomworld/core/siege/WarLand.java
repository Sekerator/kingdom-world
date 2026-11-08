package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Whether the land of a kingdom is open to a kingdom at war with it: to break and place blocks, to rob its chests, to besiege
 * and take it. Only the land of a leader or member of the other side of a war that has started, and never while that kingdom
 * is shielded because its players are all away. The land of third kingdoms and unowned land are never touched.
 */
public final class WarLand {
    /**
     * Why land is open or not.
     */
    public enum Access {
        /** Open: the war has started and the owner is not shielded. */
        OPEN,
        /** Nobody owns the land; war has nothing to open there. */
        WILD,
        /** The actor's own land (or no kingdom at all acts). */
        OWN,
        /** The two kingdoms are not at war. */
        PEACE,
        /** War is declared but has not started yet. */
        PREPARING,
        /** The owner is shielded: its players are away. */
        SHIELDED
    }

    private WarLand() {
    }

    /**
     * @param actor    the kingdom of the player or soldier that acts, {@code null} for none
     * @param owner    the owner of the land, {@code null} for unowned land
     * @param shielded whether a kingdom is shielded now
     */
    public static Access access(DiplomacyState state, UUID actor, UUID owner, Predicate<UUID> shielded) {
        if (owner == null) {
            return Access.WILD;
        }
        if (actor == null || actor.equals(owner)) {
            return Access.OWN;
        }
        if (!state.fighting(actor, owner)) {
            Optional<War> war = state.warBetween(actor, owner);
            return war.isPresent() ? Access.PREPARING : Access.PEACE;
        }
        if (shielded.test(owner)) {
            return Access.SHIELDED;
        }
        return Access.OPEN;
    }

    public static boolean open(DiplomacyState state, UUID actor, UUID owner, Predicate<UUID> shielded) {
        return access(state, actor, owner, shielded) == Access.OPEN;
    }
}
