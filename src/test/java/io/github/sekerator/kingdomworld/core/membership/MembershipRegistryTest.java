package io.github.sekerator.kingdomworld.core.membership;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipRegistryTest {
    private MembershipRegistry registry;
    private AtomicInteger changes;
    private UUID realm;
    private UUID otherRealm;
    private UUID king;
    private UUID otherKing;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        registry = new MembershipRegistry();
        changes = new AtomicInteger();
        registry.setChangeListener(changes::incrementAndGet);
        realm = UUID.randomUUID();
        otherRealm = UUID.randomUUID();
        king = UUID.randomUUID();
        otherKing = UUID.randomUUID();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        registry.register(realm, king, 0L);
        registry.register(otherRealm, otherKing, 0L);
    }

    private static void assertFails(KingdomError expected, Outcome outcome) {
        assertEquals(Optional.of(expected), outcome.failure());
    }

    private Role roleOf(UUID player) {
        return registry.memberOf(player).orElseThrow().role();
    }

    @Test
    void founderBecomesKing() {
        assertEquals(Optional.of(realm), registry.kingdomOf(king));
        assertEquals(Role.KING, roleOf(king));
        assertTrue(registry.hasPermission(king, realm, Permission.APPOINT));
        assertFalse(registry.hasPermission(king, otherRealm, Permission.BUILD));
        assertThrows(IllegalArgumentException.class, () -> registry.register(UUID.randomUUID(), king, 0L));
        assertThrows(IllegalArgumentException.class, () -> registry.register(realm, null, 0L));
    }

    @Test
    void applicationIsAcceptedByRecruiter() {
        assertEquals(Outcome.DONE, registry.apply(alice, realm, 5L));
        assertFails(KingdomError.ALREADY_APPLIED, registry.apply(alice, realm, 6L));
        assertEquals(Set.of(realm), registry.applicationsOf(alice));
        assertTrue(registry.kingdom(realm).orElseThrow().applications().containsKey(alice));

        // A plain subject may not accept applications.
        registry.apply(bob, realm, 7L);
        registry.acceptApplication(king, bob, 8L);
        assertFails(KingdomError.NO_PERMISSION, registry.acceptApplication(bob, alice, 9L));
        assertFails(KingdomError.NOT_IN_KINGDOM, registry.acceptApplication(UUID.randomUUID(), alice, 9L));

        assertEquals(Outcome.JOINED, registry.acceptApplication(king, alice, 10L));
        assertEquals(Role.SUBJECT, roleOf(alice));
        assertEquals(10L, registry.memberOf(alice).orElseThrow().joinedGameTime());
        assertTrue(registry.applicationsOf(alice).isEmpty());
        assertFails(KingdomError.NO_APPLICATION, registry.acceptApplication(king, alice, 11L));
    }

    @Test
    void joiningSettlesAllOtherRequests() {
        registry.apply(alice, realm, 1L);
        registry.apply(alice, otherRealm, 1L);
        registry.invite(otherKing, bob, 1L);
        registry.apply(bob, realm, 1L);
        assertEquals(Outcome.JOINED, registry.acceptApplication(otherKing, alice, 2L));
        assertTrue(registry.kingdom(realm).orElseThrow().applications().keySet().equals(Set.of(bob)));
        assertEquals(Outcome.JOINED, registry.acceptApplication(king, bob, 2L));
        assertTrue(registry.kingdom(otherRealm).orElseThrow().invitations().isEmpty());
        assertTrue(registry.invitationsOf(bob).isEmpty());
        assertFails(KingdomError.ALREADY_IN_KINGDOM, registry.apply(alice, realm, 3L));
    }

    @Test
    void applicationCanBeRejected() {
        registry.apply(alice, realm, 1L);
        assertEquals(Outcome.DONE, registry.rejectApplication(king, alice));
        assertTrue(registry.applicationsOf(alice).isEmpty());
        assertTrue(registry.kingdomOf(alice).isEmpty());
        assertFails(KingdomError.NO_APPLICATION, registry.rejectApplication(king, alice));
    }

    @Test
    void invitationIsAcceptedOrDeclined() {
        assertEquals(Outcome.DONE, registry.invite(king, alice, 1L));
        assertFails(KingdomError.ALREADY_INVITED, registry.invite(king, alice, 1L));
        assertFails(KingdomError.TARGET_IS_SELF, registry.invite(king, king, 1L));
        assertFails(KingdomError.ALREADY_IN_KINGDOM, registry.invite(king, otherKing, 1L));
        assertEquals(Set.of(realm), registry.invitationsOf(alice));
        assertFails(KingdomError.NO_INVITATION, registry.acceptInvitation(alice, otherRealm, 2L));
        assertEquals(Outcome.JOINED, registry.acceptInvitation(alice, realm, 2L));
        assertEquals(Optional.of(realm), registry.kingdomOf(alice));

        registry.invite(king, bob, 3L);
        assertEquals(Outcome.DONE, registry.declineInvitation(bob, realm));
        assertTrue(registry.invitationsOf(bob).isEmpty());
        assertFails(KingdomError.NO_INVITATION, registry.declineInvitation(bob, realm));
    }

    @Test
    void invitationMeetsApplication() {
        registry.invite(king, alice, 1L);
        assertEquals(Outcome.JOINED, registry.apply(alice, realm, 2L));
        registry.apply(bob, realm, 3L);
        assertEquals(Outcome.JOINED, registry.invite(king, bob, 4L));
        assertEquals(3, registry.kingdom(realm).orElseThrow().size());
    }

    @Test
    void membersLeaveButTheKingCannot() {
        registry.invite(king, alice, 1L);
        registry.acceptInvitation(alice, realm, 1L);
        assertEquals(Outcome.DONE, registry.leave(alice));
        assertTrue(registry.kingdomOf(alice).isEmpty());
        assertFails(KingdomError.NOT_IN_KINGDOM, registry.leave(alice));
        assertFails(KingdomError.KING_CANNOT_LEAVE, registry.leave(king));
    }

    @Test
    void expulsionFollowsRank() {
        UUID carol = UUID.randomUUID();
        for (UUID player : List.of(alice, bob, carol)) {
            registry.invite(king, player, 1L);
            registry.acceptInvitation(player, realm, 1L);
        }
        registry.setRole(king, alice, Role.MARSHAL);
        registry.setRole(king, bob, Role.TREASURER);

        // The marshal may expel subjects but not other officers or the king.
        assertFails(KingdomError.OUTRANKED, registry.kick(alice, bob));
        assertFails(KingdomError.TARGET_IS_KING, registry.kick(alice, king));
        assertFails(KingdomError.TARGET_IS_SELF, registry.kick(alice, alice));
        assertFails(KingdomError.NOT_A_MEMBER, registry.kick(alice, otherKing));
        assertFails(KingdomError.NO_PERMISSION, registry.kick(bob, carol));
        assertEquals(Outcome.DONE, registry.kick(alice, carol));
        assertTrue(registry.kingdomOf(carol).isEmpty());
        // The king may expel officers.
        assertEquals(Outcome.DONE, registry.kick(king, bob));
        assertTrue(registry.kingdomOf(bob).isEmpty());
    }

    @Test
    void onlyTheKingAppoints() {
        registry.invite(king, alice, 1L);
        registry.acceptInvitation(alice, realm, 1L);
        registry.invite(king, bob, 1L);
        registry.acceptInvitation(bob, realm, 1L);

        assertEquals(Outcome.DONE, registry.setRole(king, alice, Role.MARSHAL));
        assertEquals(Role.MARSHAL, roleOf(alice));
        assertFails(KingdomError.UNCHANGED, registry.setRole(king, alice, Role.MARSHAL));
        assertFails(KingdomError.ROLE_NOT_ASSIGNABLE, registry.setRole(king, alice, Role.KING));
        assertFails(KingdomError.TARGET_IS_SELF, registry.setRole(king, king, Role.SUBJECT));
        assertFails(KingdomError.NO_PERMISSION, registry.setRole(alice, bob, Role.STEWARD));
        assertFails(KingdomError.NOT_A_MEMBER, registry.setRole(king, otherKing, Role.STEWARD));

        assertFalse(registry.hasPermission(bob, realm, Permission.CONTAINERS));
        assertEquals(Outcome.DONE, registry.setRolePermission(king, Role.SUBJECT, Permission.CONTAINERS, true));
        assertTrue(registry.hasPermission(bob, realm, Permission.CONTAINERS));
        assertFails(KingdomError.UNCHANGED, registry.setRolePermission(king, Role.SUBJECT, Permission.CONTAINERS, true));
        assertFails(KingdomError.PERMISSION_NOT_EDITABLE, registry.setRolePermission(king, Role.MARSHAL, Permission.APPOINT, true));
        assertFails(KingdomError.NO_PERMISSION, registry.setRolePermission(alice, Role.SUBJECT, Permission.TERRITORY, true));
        // Rights of other kingdoms are untouched.
        assertFalse(registry.kingdom(otherRealm).orElseThrow().permissions().has(Role.SUBJECT, Permission.CONTAINERS));
    }

    @Test
    void abdicationHandsTheCrownToAMember() {
        registry.invite(king, alice, 1L);
        registry.acceptInvitation(alice, realm, 1L);
        assertFails(KingdomError.NOT_A_MEMBER, registry.abdicate(king, otherKing));
        assertFails(KingdomError.TARGET_IS_SELF, registry.abdicate(king, king));
        assertFails(KingdomError.NO_PERMISSION, registry.abdicate(alice, king));
        assertEquals(Outcome.DONE, registry.abdicate(king, alice));
        assertEquals(Role.KING, roleOf(alice));
        assertEquals(Role.SUBJECT, roleOf(king));
        assertEquals(Optional.of(alice), registry.kingdom(realm).orElseThrow().king());
        // The former king may now leave.
        assertEquals(Outcome.DONE, registry.leave(king));
    }

    @Test
    void unregisterForgetsMembersAndRequests() {
        registry.invite(king, alice, 1L);
        registry.apply(bob, realm, 1L);
        registry.unregister(realm);
        assertTrue(registry.kingdomOf(king).isEmpty());
        assertTrue(registry.invitationsOf(alice).isEmpty());
        assertTrue(registry.applicationsOf(bob).isEmpty());
        assertFails(KingdomError.UNKNOWN_KINGDOM, registry.apply(alice, realm, 2L));
        // The former king is free to found or join another kingdom.
        assertEquals(Outcome.DONE, registry.apply(king, otherRealm, 3L));
    }

    @Test
    void crownMovesRulerAndPullsPlayerFromAnotherKingdom() {
        registry.invite(otherKing, alice, 1L);
        registry.acceptInvitation(alice, otherRealm, 1L);
        assertEquals(Outcome.DONE, registry.crown(realm, alice, 2L));
        assertEquals(Optional.of(realm), registry.kingdomOf(alice));
        assertEquals(Role.KING, roleOf(alice));
        assertEquals(Role.SUBJECT, roleOf(king));
        assertFails(KingdomError.ALREADY_IN_KINGDOM, registry.crown(realm, otherKing, 3L));
        assertFails(KingdomError.UNCHANGED, registry.crown(realm, alice, 3L));
    }

    @Test
    void pendingRequestsAreLimited() {
        for (int i = 0; i < MembershipRegistry.MAX_APPLICATIONS_PER_PLAYER; i++) {
            UUID kingdom = UUID.randomUUID();
            registry.register(kingdom, null, 0L);
            assertEquals(Outcome.DONE, registry.apply(alice, kingdom, 0L));
        }
        assertFails(KingdomError.TOO_MANY_PENDING, registry.apply(alice, realm, 0L));
    }

    @Test
    void restoreIsAllOrNothing() {
        UUID restored = UUID.randomUUID();
        List<Member> conflicting = List.of(new Member(alice, Role.KING, 0L), new Member(king, Role.SUBJECT, 0L));
        int before = changes.get();
        assertThrows(IllegalArgumentException.class, () -> registry.restore(restored, RolePermissions.defaults(),
                conflicting, Map.of(), Map.of()));
        assertTrue(registry.kingdom(restored).isEmpty());
        assertTrue(registry.kingdomOf(alice).isEmpty());
        assertEquals(before, changes.get());

        registry.restore(restored, RolePermissions.defaults(), List.of(new Member(alice, Role.KING, 0L)),
                Map.of(bob, 4L), Map.of());
        assertEquals(Set.of(restored), registry.applicationsOf(bob));
        assertEquals(Role.KING, roleOf(alice));
    }
}
