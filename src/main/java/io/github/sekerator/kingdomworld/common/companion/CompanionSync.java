package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Party;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps clients up to date once a second: the list in the corner (the members of the player's party with their health,
 * then the player's companions) goes to whoever has one, and changed only when it changed; the window of a companion that
 * is open gets the companion's state every other second.
 */
final class CompanionSync {
    /** What each player was last sent, so that an unchanged list is not sent again. */
    private static final Map<UUID, List<CompanionViews.HudLine>> SENT = new HashMap<>();
    private static int ticks;

    private CompanionSync() {
    }

    static void tick(MinecraftServer server) {
        ticks++;
        CompanionState state = CompanionWork.state(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<CompanionViews.HudLine> lines = lines(server, state, player.getUUID());
            List<CompanionViews.HudLine> before = SENT.get(player.getUUID());
            if (!lines.equals(before) && !(lines.isEmpty() && before == null)) {
                SENT.put(player.getUUID(), lines);
                KingdomWorldNetwork.sendToPlayer(player, new CompanionPackets.PartyHud(lines));
            }
            if (ticks % 2 == 0 && player.containerMenu instanceof CompanionMenu menu) {
                state.companion(menu.companionId()).ifPresent(companion -> KingdomWorldNetwork.sendToPlayer(player,
                        new CompanionPackets.CompanionUpdate(CompanionActions.view(server, companion))));
            }
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /**
     * The list in the corner of a player: the party, the player included, then the player's companions.
     */
    static List<CompanionViews.HudLine> lines(MinecraftServer server, CompanionState state, UUID viewer) {
        List<CompanionViews.HudLine> lines = new ArrayList<>();
        Optional<Party> party = state.parties().partyOf(viewer);
        if (party.isPresent() && party.get().members().size() > 1) {
            for (UUID member : party.get().members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                lines.add(new CompanionViews.HudLine(false, PlayerNames.name(server, member), 0, io.github.sekerator.kingdomworld.core.citizen.Gender.MALE,
                        party.get().isLeader(member), online != null, online == null ? 0.0f : round(online.getHealth()),
                        online == null ? 20.0f : online.getMaxHealth(), false));
            }
        }
        for (Companion companion : state.ownedBy(viewer)) {
            float health = CompanionBodies.body(companion.id()).map(body -> body.getHealth()).orElse(companion.health());
            lines.add(new CompanionViews.HudLine(true, "", companion.nameSeed(), companion.gender(), false,
                    CompanionBodies.body(companion.id()).isPresent(), round(health), companion.maxHealth(), companion.warned()));
        }
        return lines;
    }

    private static float round(float health) {
        return (float) Math.ceil(health);
    }

    static void reset() {
        SENT.clear();
        ticks = 0;
    }
}
