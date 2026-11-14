package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.FlagPackets;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells every client the flags of all kingdoms, the way the currencies are told: the whole directory to a player who joins, and
 * what changed to everyone, checked when the list of kingdoms changed and at once after a flag is saved.
 */
public final class FlagDirectory {
    /** What the clients were last told, or {@code null} before the first check. */
    private static Map<UUID, FlagPackets.Entry> lastSent;
    /** How many times changes were told, for tests. */
    private static int told;

    private FlagDirectory() {
    }

    /**
     * The flag every kingdom flies now, as the clients are told.
     */
    public static Map<UUID, FlagPackets.Entry> current(MinecraftServer server) {
        Map<UUID, FlagPackets.Entry> entries = new LinkedHashMap<>();
        for (Kingdom kingdom : KingdomWorldApi.state(server).kingdoms().all()) {
            entries.put(kingdom.id(), new FlagPackets.Entry(kingdom.id(), FlagWork.effective(server, kingdom), kingdom.flag() != null));
        }
        return entries;
    }

    /**
     * Sends a player who joins the whole directory.
     */
    public static void sendAll(ServerPlayer player) {
        Map<UUID, FlagPackets.Entry> now = current(player.server);
        for (FlagPackets.Directory packet : FlagPackets.Directory.split(true, List.copyOf(now.values()), List.of())) {
            KingdomWorldNetwork.sendToPlayer(player, packet);
        }
    }

    /**
     * Tells everyone what changed since the last time.
     */
    public static void pushNow(MinecraftServer server) {
        Map<UUID, FlagPackets.Entry> now = current(server);
        if (lastSent == null) {
            // The first check: the players who joined got the whole directory as it was then.
            lastSent = now;
            return;
        }
        List<FlagPackets.Entry> changed = new ArrayList<>();
        for (FlagPackets.Entry entry : now.values()) {
            if (!entry.equals(lastSent.get(entry.kingdom()))) {
                changed.add(entry);
            }
        }
        List<UUID> removed = new ArrayList<>();
        for (UUID id : lastSent.keySet()) {
            if (!now.containsKey(id)) {
                removed.add(id);
            }
        }
        lastSent = now;
        if (changed.isEmpty() && removed.isEmpty()) {
            return;
        }
        for (FlagPackets.Directory packet : FlagPackets.Directory.split(false, changed, removed)) {
            KingdomWorldNetwork.sendToAll(packet);
        }
        told++;
    }

    /**
     * How many times changes of flags were told to the clients since the server started.
     */
    public static int told() {
        return told;
    }

    public static void reset() {
        lastSent = null;
        told = 0;
    }
}
