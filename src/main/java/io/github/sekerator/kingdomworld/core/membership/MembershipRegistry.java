package io.github.sekerator.kingdomworld.core.membership;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who belongs to which kingdom, with positions, rights, applications and invitations.
 *
 * <p>A player belongs to at most one kingdom. Every kingdom has at most one {@link Role#KING}. Applications
 * and invitations stay until they are answered, withdrawn by the kingdom's end or made obsolete because the
 * player joined a kingdom; they do not expire, so a player who is offline sees them on the next login.
 *
 * <p>Lookups by player are O(1). Every successful change notifies the change listener. Not thread safe.
 */
public final class MembershipRegistry {
    /** Open applications one player may have at the same time. */
    public static final int MAX_APPLICATIONS_PER_PLAYER = 5;
    /** Open applications, and separately open invitations, one kingdom may have at the same time. */
    public static final int MAX_PENDING_PER_KINGDOM = 50;

    private final Map<UUID, KingdomMembers> kingdoms = new LinkedHashMap<>();
    private final Map<UUID, UUID> kingdomOfPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> applicationsByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> invitationsByPlayer = new HashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    // ---------------------------------------------------------------- queries

    public Optional<KingdomMembers> kingdom(UUID kingdomId) {
        return Optional.ofNullable(kingdoms.get(kingdomId));
    }

    public Optional<UUID> kingdomOf(UUID playerId) {
        return Optional.ofNullable(kingdomOfPlayer.get(playerId));
    }

    public Optional<Member> memberOf(UUID playerId) {
        UUID kingdomId = kingdomOfPlayer.get(playerId);
        return kingdomId == null ? Optional.empty() : kingdoms.get(kingdomId).member(playerId);
    }

    public boolean isMember(UUID playerId, UUID kingdomId) {
        return kingdomId != null && kingdomId.equals(kingdomOfPlayer.get(playerId));
    }

    /**
     * Whether the player belongs to the kingdom and their position holds the right.
     */
    public boolean hasPermission(UUID playerId, UUID kingdomId, Permission permission) {
        if (!isMember(playerId, kingdomId)) {
            return false;
        }
        return kingdoms.get(kingdomId).has(playerId, permission);
    }

    /**
     * Kingdoms the player has applied to.
     */
    public Set<UUID> applicationsOf(UUID playerId) {
        return Collections.unmodifiableSet(applicationsByPlayer.getOrDefault(playerId, Set.of()));
    }

    /**
     * Kingdoms that have invited the player.
     */
    public Set<UUID> invitationsOf(UUID playerId) {
        return Collections.unmodifiableSet(invitationsByPlayer.getOrDefault(playerId, Set.of()));
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Starts tracking a new kingdom with default rights.
     *
     * @param king the founder, who becomes king, or {@code null}
     * @throws IllegalArgumentException if the kingdom is known already or the king belongs to a kingdom
     */
    public void register(UUID kingdomId, UUID king, long gameTime) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        if (kingdoms.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Kingdom already registered: " + kingdomId);
        }
        if (king != null && kingdomOfPlayer.containsKey(king)) {
            throw new IllegalArgumentException("Player " + king + " already belongs to a kingdom");
        }
        kingdoms.put(kingdomId, new KingdomMembers(kingdomId, RolePermissions.defaults()));
        if (king != null) {
            join(king, kingdomId, Role.KING, gameTime);
        }
        changeListener.run();
    }

    /**
     * Restores a kingdom from saved data. Either everything is restored or, on a conflict, nothing.
     *
     * @throws IllegalArgumentException if the data conflicts with what is already known
     */
    public void restore(UUID kingdomId, RolePermissions permissions, List<Member> members,
                        Map<UUID, Long> applications, Map<UUID, Long> invitations) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        if (kingdoms.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Kingdom already registered: " + kingdomId);
        }
        Set<UUID> seen = new LinkedHashSet<>();
        int kings = 0;
        for (Member member : members) {
            if (!seen.add(member.playerId())) {
                throw new IllegalArgumentException("Player " + member.playerId() + " listed twice");
            }
            if (kingdomOfPlayer.containsKey(member.playerId())) {
                throw new IllegalArgumentException("Player " + member.playerId() + " already belongs to another kingdom");
            }
            if (member.role() == Role.KING) {
                kings++;
            }
        }
        if (kings > 1) {
            throw new IllegalArgumentException("Kingdom " + kingdomId + " has " + kings + " kings");
        }
        KingdomMembers entry = new KingdomMembers(kingdomId, permissions.copy());
        kingdoms.put(kingdomId, entry);
        for (Member member : members) {
            entry.members.put(member.playerId(), member);
            kingdomOfPlayer.put(member.playerId(), kingdomId);
        }
        applications.forEach((player, time) -> {
            if (!seen.contains(player)) {
                entry.applications.put(player, time);
                applicationsByPlayer.computeIfAbsent(player, p -> new LinkedHashSet<>()).add(kingdomId);
            }
        });
        invitations.forEach((player, time) -> {
            if (!seen.contains(player)) {
                entry.invitations.put(player, time);
                invitationsByPlayer.computeIfAbsent(player, p -> new LinkedHashSet<>()).add(kingdomId);
            }
        });
        changeListener.run();
    }

    /**
     * Forgets a kingdom together with its members and pending requests.
     */
    public void unregister(UUID kingdomId) {
        KingdomMembers entry = kingdoms.remove(kingdomId);
        if (entry == null) {
            return;
        }
        for (UUID player : entry.members.keySet()) {
            kingdomOfPlayer.remove(player);
        }
        for (UUID player : entry.applications.keySet()) {
            removeIndex(applicationsByPlayer, player, kingdomId);
        }
        for (UUID player : entry.invitations.keySet()) {
            removeIndex(invitationsByPlayer, player, kingdomId);
        }
        changeListener.run();
    }

    // ---------------------------------------------------------------- transitions

    /**
     * A player asks to join a kingdom. If the kingdom has invited the player, they join right away.
     */
    public Outcome apply(UUID playerId, UUID kingdomId, long gameTime) {
        KingdomMembers entry = kingdoms.get(kingdomId);
        if (entry == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (kingdomOfPlayer.containsKey(playerId)) {
            return Outcome.fail(KingdomError.ALREADY_IN_KINGDOM);
        }
        if (entry.invitations.containsKey(playerId)) {
            join(playerId, kingdomId, Role.SUBJECT, gameTime);
            changeListener.run();
            return Outcome.JOINED;
        }
        if (entry.applications.containsKey(playerId)) {
            return Outcome.fail(KingdomError.ALREADY_APPLIED);
        }
        if (applicationsOf(playerId).size() >= MAX_APPLICATIONS_PER_PLAYER
                || entry.applications.size() >= MAX_PENDING_PER_KINGDOM) {
            return Outcome.fail(KingdomError.TOO_MANY_PENDING);
        }
        entry.applications.put(playerId, gameTime);
        applicationsByPlayer.computeIfAbsent(playerId, p -> new LinkedHashSet<>()).add(kingdomId);
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * A member with {@link Permission#RECRUIT} invites a player. If the player has applied, they join right away.
     */
    public Outcome invite(UUID actorId, UUID targetId, long gameTime) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.RECRUIT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (actorId.equals(targetId)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (kingdomOfPlayer.containsKey(targetId)) {
            return Outcome.fail(KingdomError.ALREADY_IN_KINGDOM);
        }
        if (entry.applications.containsKey(targetId)) {
            join(targetId, entry.kingdomId(), Role.SUBJECT, gameTime);
            changeListener.run();
            return Outcome.JOINED;
        }
        if (entry.invitations.containsKey(targetId)) {
            return Outcome.fail(KingdomError.ALREADY_INVITED);
        }
        if (entry.invitations.size() >= MAX_PENDING_PER_KINGDOM) {
            return Outcome.fail(KingdomError.TOO_MANY_PENDING);
        }
        entry.invitations.put(targetId, gameTime);
        invitationsByPlayer.computeIfAbsent(targetId, p -> new LinkedHashSet<>()).add(entry.kingdomId());
        changeListener.run();
        return Outcome.DONE;
    }

    public Outcome acceptApplication(UUID actorId, UUID applicantId, long gameTime) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.RECRUIT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (!entry.applications.containsKey(applicantId)) {
            return Outcome.fail(KingdomError.NO_APPLICATION);
        }
        if (kingdomOfPlayer.containsKey(applicantId)) {
            // The applicant joined another kingdom in the meantime; the application is stale.
            entry.applications.remove(applicantId);
            removeIndex(applicationsByPlayer, applicantId, entry.kingdomId());
            changeListener.run();
            return Outcome.fail(KingdomError.ALREADY_IN_KINGDOM);
        }
        join(applicantId, entry.kingdomId(), Role.SUBJECT, gameTime);
        changeListener.run();
        return Outcome.JOINED;
    }

    public Outcome rejectApplication(UUID actorId, UUID applicantId) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.RECRUIT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (entry.applications.remove(applicantId) == null) {
            return Outcome.fail(KingdomError.NO_APPLICATION);
        }
        removeIndex(applicationsByPlayer, applicantId, entry.kingdomId());
        changeListener.run();
        return Outcome.DONE;
    }

    public Outcome acceptInvitation(UUID playerId, UUID kingdomId, long gameTime) {
        KingdomMembers entry = kingdoms.get(kingdomId);
        if (entry == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!entry.invitations.containsKey(playerId)) {
            return Outcome.fail(KingdomError.NO_INVITATION);
        }
        if (kingdomOfPlayer.containsKey(playerId)) {
            return Outcome.fail(KingdomError.ALREADY_IN_KINGDOM);
        }
        join(playerId, kingdomId, Role.SUBJECT, gameTime);
        changeListener.run();
        return Outcome.JOINED;
    }

    public Outcome declineInvitation(UUID playerId, UUID kingdomId) {
        KingdomMembers entry = kingdoms.get(kingdomId);
        if (entry == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (entry.invitations.remove(playerId) == null) {
            return Outcome.fail(KingdomError.NO_INVITATION);
        }
        removeIndex(invitationsByPlayer, playerId, kingdomId);
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * A member leaves. The king has to abdicate or dissolve the kingdom instead.
     */
    public Outcome leave(UUID playerId) {
        UUID kingdomId = kingdomOfPlayer.get(playerId);
        if (kingdomId == null) {
            return Outcome.fail(KingdomError.NOT_IN_KINGDOM);
        }
        KingdomMembers entry = kingdoms.get(kingdomId);
        if (entry.members.get(playerId).role() == Role.KING) {
            return Outcome.fail(KingdomError.KING_CANNOT_LEAVE);
        }
        removeMember(entry, playerId);
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * Expels a member. The king may expel anyone else; other members with {@link Permission#RECRUIT} may only
     * expel plain subjects.
     */
    public Outcome kick(UUID actorId, UUID targetId) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.RECRUIT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (actorId.equals(targetId)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        Member target = entry.members.get(targetId);
        if (target == null) {
            return Outcome.fail(KingdomError.NOT_A_MEMBER);
        }
        if (target.role() == Role.KING) {
            return Outcome.fail(KingdomError.TARGET_IS_KING);
        }
        if (entry.members.get(actorId).role() != Role.KING && target.role() != Role.SUBJECT) {
            return Outcome.fail(KingdomError.OUTRANKED);
        }
        removeMember(entry, targetId);
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * Appoints a member to a position. Needs {@link Permission#APPOINT}.
     */
    public Outcome setRole(UUID actorId, UUID targetId, Role role) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.APPOINT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (!role.isAssignable()) {
            return Outcome.fail(KingdomError.ROLE_NOT_ASSIGNABLE);
        }
        if (actorId.equals(targetId)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        Member target = entry.members.get(targetId);
        if (target == null) {
            return Outcome.fail(KingdomError.NOT_A_MEMBER);
        }
        if (target.role() == Role.KING) {
            return Outcome.fail(KingdomError.TARGET_IS_KING);
        }
        if (target.role() == role) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        entry.members.put(targetId, target.withRole(role));
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * Grants or revokes a right of a position. Needs {@link Permission#APPOINT}.
     */
    public Outcome setRolePermission(UUID actorId, Role role, Permission permission, boolean granted) {
        KingdomMembers entry = kingdomWithRight(actorId, Permission.APPOINT);
        if (entry == null) {
            return refusalFor(actorId);
        }
        if (!RolePermissions.isEditable(role, permission)) {
            return Outcome.fail(KingdomError.PERMISSION_NOT_EDITABLE);
        }
        if (!entry.permissions.set(role, permission, granted)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * The king hands the crown to another member and becomes a plain subject.
     */
    public Outcome abdicate(UUID kingId, UUID heirId) {
        UUID kingdomId = kingdomOfPlayer.get(kingId);
        if (kingdomId == null) {
            return Outcome.fail(KingdomError.NOT_IN_KINGDOM);
        }
        KingdomMembers entry = kingdoms.get(kingdomId);
        Member king = entry.members.get(kingId);
        if (king.role() != Role.KING) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (kingId.equals(heirId)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        Member heir = entry.members.get(heirId);
        if (heir == null) {
            return Outcome.fail(KingdomError.NOT_A_MEMBER);
        }
        entry.members.put(kingId, king.withRole(Role.SUBJECT));
        entry.members.put(heirId, heir.withRole(Role.KING));
        changeListener.run();
        return Outcome.DONE;
    }

    /**
     * Administrative change of ruler: the player becomes king, joining the kingdom if needed (and leaving
     * another kingdom where they are not king). The previous king becomes a subject.
     */
    public Outcome crown(UUID kingdomId, UUID playerId, long gameTime) {
        KingdomMembers entry = kingdoms.get(kingdomId);
        if (entry == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        UUID current = kingdomOfPlayer.get(playerId);
        if (current != null && !current.equals(kingdomId)) {
            KingdomMembers other = kingdoms.get(current);
            if (other.members.get(playerId).role() == Role.KING) {
                return Outcome.fail(KingdomError.ALREADY_IN_KINGDOM);
            }
            removeMember(other, playerId);
        }
        Optional<UUID> oldKing = entry.king();
        if (oldKing.isPresent() && oldKing.get().equals(playerId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        oldKing.ifPresent(id -> entry.members.put(id, entry.members.get(id).withRole(Role.SUBJECT)));
        Member member = entry.members.get(playerId);
        if (member == null) {
            join(playerId, kingdomId, Role.KING, gameTime);
        } else {
            entry.members.put(playerId, member.withRole(Role.KING));
        }
        changeListener.run();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- internals

    private KingdomMembers kingdomWithRight(UUID actorId, Permission permission) {
        UUID kingdomId = kingdomOfPlayer.get(actorId);
        if (kingdomId == null) {
            return null;
        }
        KingdomMembers entry = kingdoms.get(kingdomId);
        return entry.has(actorId, permission) ? entry : null;
    }

    private Outcome refusalFor(UUID actorId) {
        return Outcome.fail(kingdomOfPlayer.containsKey(actorId) ? KingdomError.NO_PERMISSION : KingdomError.NOT_IN_KINGDOM);
    }

    private void join(UUID playerId, UUID kingdomId, Role role, long gameTime) {
        KingdomMembers entry = kingdoms.get(kingdomId);
        entry.members.put(playerId, new Member(playerId, role, gameTime));
        kingdomOfPlayer.put(playerId, kingdomId);
        // Joining a kingdom settles every other open request of the player.
        Set<UUID> applied = applicationsByPlayer.remove(playerId);
        if (applied != null) {
            for (UUID other : applied) {
                kingdoms.get(other).applications.remove(playerId);
            }
        }
        Set<UUID> invited = invitationsByPlayer.remove(playerId);
        if (invited != null) {
            for (UUID other : invited) {
                kingdoms.get(other).invitations.remove(playerId);
            }
        }
    }

    private void removeMember(KingdomMembers entry, UUID playerId) {
        entry.members.remove(playerId);
        kingdomOfPlayer.remove(playerId);
    }

    private static void removeIndex(Map<UUID, Set<UUID>> index, UUID playerId, UUID kingdomId) {
        Set<UUID> set = index.get(playerId);
        if (set != null) {
            set.remove(kingdomId);
            if (set.isEmpty()) {
                index.remove(playerId);
            }
        }
    }
}
