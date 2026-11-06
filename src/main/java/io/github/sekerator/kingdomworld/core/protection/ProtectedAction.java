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
    /** Doors, buttons, levers, item frames, armor stands. */
    INTERACT(Permission.INTERACT);

    private final Permission permission;

    ProtectedAction(Permission permission) {
        this.permission = permission;
    }

    public Permission permission() {
        return permission;
    }
}
