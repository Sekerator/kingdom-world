package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the line above the hotbar of every player in step: while a player stands on the land of a kingdom that wants them,
 * one line names the kingdom and the fine (or the death that awaits a murderer). Sent only when it changes.
 */
final class LawSync {
    private static final Map<UUID, LawViews.WantedLine> SENT = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();

    private LawSync() {
    }

    static void dirty(UUID player) {
        DIRTY.add(player);
    }

    static void forget(UUID player) {
        SENT.remove(player);
        DIRTY.remove(player);
    }

    static void reset() {
        SENT.clear();
        DIRTY.clear();
    }

    /**
     * The line a player sees now.
     */
    static LawViews.WantedLine line(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID owner = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
        if (owner == null) {
            return LawViews.WantedLine.NONE;
        }
        Optional<Wanted> wanted = LawWork.state(server).wanted(owner, player.getUUID());
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, owner);
        if (wanted.isEmpty() || kingdom.isEmpty()) {
            return LawViews.WantedLine.NONE;
        }
        CurrencyUnits units = LawTexts.units(server, owner);
        long fine = wanted.get().fine(LawSettings.rules(), LawWork.state(server).law(owner).fineMultiplierPermille());
        return new LawViews.WantedLine(true, ProtectionService.shortName(kingdom.get().name()), kingdom.get().color(), wanted.get().executable(),
                fine, units.silver(), units.gold());
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LawViews.WantedLine line = line(player);
            LawViews.WantedLine before = SENT.get(player.getUUID());
            if (!line.equals(before) || DIRTY.remove(player.getUUID())) {
                SENT.put(player.getUUID(), line);
                KingdomWorldNetwork.sendToPlayer(player, new LawPackets.WantedLinePacket(line));
            }
        }
        DIRTY.clear();
    }
}
