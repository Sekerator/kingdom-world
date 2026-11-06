package io.github.sekerator.kingdomworld.core.protection;

import io.github.sekerator.kingdomworld.core.membership.MembershipRegistry;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {
    private static final String DIM = "minecraft:overworld";
    private static final long CHUNK = ChunkKey.of(0, 0);

    private MembershipRegistry membership;
    private AccessPolicy policy;
    private UUID realm;
    private UUID king;
    private UUID subject;
    private UUID stranger;

    @BeforeEach
    void setUp() {
        membership = new MembershipRegistry();
        policy = new AccessPolicy();
        realm = UUID.randomUUID();
        king = UUID.randomUUID();
        subject = UUID.randomUUID();
        stranger = UUID.randomUUID();
        membership.register(realm, king, 0L);
        membership.invite(king, subject, 0L);
        membership.acceptInvitation(subject, realm, 0L);
    }

    private boolean allowed(AccessSubject who, ProtectedAction action, AccessPolicy.Settings settings) {
        return policy.isAllowed(membership, realm, who, action, DIM, CHUNK, settings);
    }

    private boolean allowed(AccessSubject who, ProtectedAction action) {
        return allowed(who, action, AccessPolicy.Settings.DEFAULT);
    }

    @Test
    void unclaimedLandIsFree() {
        assertTrue(policy.isAllowed(membership, null, AccessSubject.player(stranger), ProtectedAction.BREAK, DIM, CHUNK,
                AccessPolicy.Settings.DEFAULT));
        assertTrue(policy.isAllowed(membership, null, AccessSubject.OTHER, ProtectedAction.CONTAINER, DIM, CHUNK,
                AccessPolicy.Settings.DEFAULT));
    }

    @Test
    void strangersAreKeptOut() {
        for (ProtectedAction action : ProtectedAction.values()) {
            assertFalse(allowed(AccessSubject.player(stranger), action), action.toString());
            assertFalse(allowed(AccessSubject.OTHER, action), action.toString());
        }
    }

    @Test
    void membersActByTheRightsOfTheirPosition() {
        for (ProtectedAction action : ProtectedAction.values()) {
            assertTrue(allowed(AccessSubject.player(king), action), action.toString());
        }
        // A subject builds and interacts but does not open containers by default.
        assertTrue(allowed(AccessSubject.player(subject), ProtectedAction.BREAK));
        assertTrue(allowed(AccessSubject.player(subject), ProtectedAction.USE_ITEM));
        assertTrue(allowed(AccessSubject.player(subject), ProtectedAction.INTERACT));
        assertFalse(allowed(AccessSubject.player(subject), ProtectedAction.CONTAINER));
        membership.setRolePermission(king, Role.SUBJECT, Permission.CONTAINERS, true);
        assertTrue(allowed(AccessSubject.player(subject), ProtectedAction.CONTAINER));
        membership.setRolePermission(king, Role.SUBJECT, Permission.BUILD, false);
        assertFalse(allowed(AccessSubject.player(subject), ProtectedAction.PLACE));
    }

    @Test
    void onlyMembersMayHurtTheirCitizensInPeacetime() {
        assertTrue(allowed(AccessSubject.player(king), ProtectedAction.ATTACK_CITIZEN));
        assertTrue(allowed(AccessSubject.player(subject), ProtectedAction.ATTACK_CITIZEN), "every member, no right needed");
        assertFalse(allowed(AccessSubject.player(stranger), ProtectedAction.ATTACK_CITIZEN));
        assertTrue(allowed(AccessSubject.bypass(stranger), ProtectedAction.ATTACK_CITIZEN));
        AccessPolicy.Exemption war = (who, owner, action, dimension, chunk) -> action == ProtectedAction.ATTACK_CITIZEN;
        policy.addExemption(war);
        assertTrue(allowed(AccessSubject.player(stranger), ProtectedAction.ATTACK_CITIZEN), "war allows attacks");
        policy.removeExemption(war);
    }

    @Test
    void settingsControlInteractionsAndFakePlayers() {
        AccessPolicy.Settings open = new AccessPolicy.Settings(false, false);
        assertTrue(allowed(AccessSubject.player(stranger), ProtectedAction.INTERACT, open));
        assertFalse(allowed(AccessSubject.player(stranger), ProtectedAction.BREAK, open));

        assertTrue(allowed(AccessSubject.fakePlayer(UUID.randomUUID()), ProtectedAction.BREAK));
        assertFalse(allowed(AccessSubject.fakePlayer(UUID.randomUUID()), ProtectedAction.BREAK, open));
    }

    @Test
    void bypassAndExemptionsAllow() {
        assertTrue(allowed(AccessSubject.bypass(stranger), ProtectedAction.CONTAINER));
        AccessPolicy.Exemption war = (who, owner, action, dimension, chunk) ->
                who.playerId() != null && who.playerId().equals(stranger) && action == ProtectedAction.BREAK;
        policy.addExemption(war);
        assertTrue(allowed(AccessSubject.player(stranger), ProtectedAction.BREAK));
        assertFalse(allowed(AccessSubject.player(stranger), ProtectedAction.CONTAINER));
        policy.removeExemption(war);
        assertFalse(allowed(AccessSubject.player(stranger), ProtectedAction.BREAK));
    }
}
