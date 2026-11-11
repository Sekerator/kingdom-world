package io.github.sekerator.kingdomworld.core.protection;

import io.github.sekerator.kingdomworld.core.membership.Permission;

/**
 * Things a player may try to do in a kingdom's land, with the right that allows them.
 */
public enum ProtectedAction {
    BREAK(Permission.BUILD),
    PLACE(Permission.BUILD),
    /** Use buckets, tools and other items on blocks. */
    USE_ITEM(Permission.BUILD),
    /** Open a block with an inventory. */
    CONTAINER(Permission.CONTAINERS),
    /** Buttons, levers, repeaters, item frames, armor stands and the leaves of city gates. Doors open to everybody. */
    INTERACT(Permission.INTERACT),
    /**
     * Hurting a citizen of the kingdom. In peacetime only members of that kingdom may; war adds an exemption.
     * Monsters are not asked: the rule is about players and machines acting for them.
     */
    ATTACK_CITIZEN(null);

    private final Permission permission;

    ProtectedAction(Permission permission) {
        this.permission = permission;
    }

    /**
     * The right a member needs, or {@code null} if every member of the kingdom may.
     */
    public Permission permission() {
        return permission;
    }
}
