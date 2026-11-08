package io.github.sekerator.kingdomworld.core.membership;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * Which rights each position of one kingdom holds. The king always holds every right; king-only rights are
 * never held by other positions. Everything else is chosen by the king.
 */
public final class RolePermissions {
    private final EnumMap<Role, EnumSet<Permission>> granted = new EnumMap<>(Role.class);

    /**
     * Creates a table in which no position except the king holds any right.
     */
    public RolePermissions() {
        for (Role role : Role.values()) {
            granted.put(role, EnumSet.noneOf(Permission.class));
        }
    }

    /**
     * The rights a newly founded kingdom starts with.
     */
    public static RolePermissions defaults() {
        RolePermissions table = new RolePermissions();
        table.grant(Role.MARSHAL, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT, Permission.RECRUIT, Permission.TERRITORY,
                Permission.MANAGE_GUILD, Permission.MANAGE_ARMY);
        table.grant(Role.TREASURER, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT, Permission.MANAGE_TREASURY,
                Permission.MANAGE_MARKET, Permission.MANAGE_TRADE);
        table.grant(Role.CHANCELLOR, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT, Permission.RECRUIT, Permission.MANAGE_TRADE,
                Permission.MANAGE_LAW, Permission.MANAGE_DIPLOMACY);
        table.grant(Role.SPYMASTER, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT);
        table.grant(Role.COURT_MAGE, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT, Permission.MANAGE_MAGIC);
        table.grant(Role.STEWARD, Permission.BUILD, Permission.CONTAINERS, Permission.INTERACT, Permission.TERRITORY,
                Permission.MANAGE_CITIZENS, Permission.MANAGE_PRODUCTION, Permission.MANAGE_MARKET, Permission.MANAGE_CONSTRUCTION);
        table.grant(Role.SUBJECT, Permission.BUILD, Permission.INTERACT);
        return table;
    }

    /**
     * Whether the right of a position can be changed at all.
     */
    public static boolean isEditable(Role role, Permission permission) {
        return role != Role.KING && !permission.isKingOnly();
    }

    public boolean has(Role role, Permission permission) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(permission, "permission");
        if (role == Role.KING) {
            return true;
        }
        return !permission.isKingOnly() && granted.get(role).contains(permission);
    }

    /**
     * Every right the position effectively holds.
     */
    public EnumSet<Permission> effective(Role role) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : Permission.values()) {
            if (has(role, permission)) {
                result.add(permission);
            }
        }
        return result;
    }

    /**
     * Grants or revokes an editable right.
     *
     * @return whether the table changed
     * @throws IllegalArgumentException if the right of this position cannot be edited
     */
    public boolean set(Role role, Permission permission, boolean value) {
        if (!isEditable(role, permission)) {
            throw new IllegalArgumentException("Right " + permission + " of " + role + " cannot be changed");
        }
        EnumSet<Permission> rights = granted.get(role);
        return value ? rights.add(permission) : rights.remove(permission);
    }

    public RolePermissions copy() {
        RolePermissions copy = new RolePermissions();
        for (Map.Entry<Role, EnumSet<Permission>> entry : granted.entrySet()) {
            copy.granted.get(entry.getKey()).addAll(entry.getValue());
        }
        return copy;
    }

    private void grant(Role role, Permission... permissions) {
        for (Permission permission : permissions) {
            set(role, permission, true);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RolePermissions table && granted.equals(table.granted);
    }

    @Override
    public int hashCode() {
        return granted.hashCode();
    }

    @Override
    public String toString() {
        return "RolePermissions" + granted;
    }
}
