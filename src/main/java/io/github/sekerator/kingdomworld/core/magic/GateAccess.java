package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.membership.MembershipRegistry;

import java.util.UUID;

/**
 * Who may travel through the gates of a kingdom. Today only its own members; alliances (a later stage) replace the
 * policy so that allies may use each other's gates.
 */
@FunctionalInterface
public interface GateAccess {
    /** Members of the kingdom that owns the gates. */
    GateAccess MEMBERS_ONLY = (membership, player, gateKingdom) -> membership.isMember(player, gateKingdom);

    boolean mayUse(MembershipRegistry membership, UUID player, UUID gateKingdom);
}
