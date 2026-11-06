package io.github.sekerator.kingdomworld.core.membership;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Members, rights of positions and pending applications and invitations of one kingdom. Read-only view;
 * changes go through {@link MembershipRegistry}.
 */
public final class KingdomMembers {
    private final UUID kingdomId;
    final Map<UUID, Member> members = new LinkedHashMap<>();
    final RolePermissions permissions;
    /** Players who asked to join, with the game time of the request. */
    final Map<UUID, Long> applications = new LinkedHashMap<>();
    /** Players the kingdom invited, with the game time of the invitation. */
    final Map<UUID, Long> invitations = new LinkedHashMap<>();

    KingdomMembers(UUID kingdomId, RolePermissions permissions) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }

    public Optional<Member> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public int size() {
        return members.size();
    }

    public Optional<UUID> king() {
        for (Member member : members.values()) {
            if (member.role() == Role.KING) {
                return Optional.of(member.playerId());
            }
        }
        return Optional.empty();
    }

    /**
     * A copy of the rights table.
     */
    public RolePermissions permissions() {
        return permissions.copy();
    }

    public boolean has(UUID playerId, Permission permission) {
        Member member = members.get(playerId);
        return member != null && permissions.has(member.role(), permission);
    }

    public Map<UUID, Long> applications() {
        return Collections.unmodifiableMap(applications);
    }

    public Map<UUID, Long> invitations() {
        return Collections.unmodifiableMap(invitations);
    }
}
