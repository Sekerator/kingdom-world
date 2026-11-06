package io.github.sekerator.kingdomworld.common.kingdom;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.BorderCrossingPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritoryDeltaPacket;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps players informed about land:
 * <ul>
 *   <li>chunk ownership changes are collected during a tick and sent to all online players as one delta per
 *       dimension at the end of the tick;</li>
 *   <li>every half second each player's chunk is checked, and crossing into or out of a kingdom's land is
 *       reported to that player.</li>
 * </ul>
 * Used on the server thread only.
 */
public final class TerritorySync {
    /** Collects changes; registered on the live territory index. */
    public static final TerritoryListener LISTENER = TerritorySync::onChunkChanged;

    private static final UUID NONE = new UUID(0L, 0L);
    private static final int BORDER_CHECK_INTERVAL_TICKS = 10;
    private static final Map<String, Map<Long, UUID>> PENDING = new LinkedHashMap<>();
    private static final Map<UUID, UUID> LAST_OWNER = new HashMap<>();

    private TerritorySync() {
    }

    private static void onChunkChanged(String dimension, long chunk, UUID oldOwner, UUID newOwner) {
        PENDING.computeIfAbsent(dimension, d -> new LinkedHashMap<>()).put(chunk, newOwner == null ? NONE : newOwner);
    }

    /**
     * Sends the changes of this tick. Called at the end of every server tick.
     */
    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Map.Entry<String, Map<Long, UUID>> entry : PENDING.entrySet()) {
            String dimension = entry.getKey();
            Map<Long, UUID> changes = entry.getValue();
            List<KingdomMapInfo> palette = new ArrayList<>();
            Map<UUID, Integer> index = new HashMap<>();
            List<Long> chunks = new ArrayList<>(changes.keySet());
            for (int start = 0; start < chunks.size(); start += TerritoryDeltaPacket.MAX_CHANGES) {
                int end = Math.min(chunks.size(), start + TerritoryDeltaPacket.MAX_CHANGES);
                long[] keys = new long[end - start];
                int[] owners = new int[end - start];
                palette.clear();
                index.clear();
                for (int i = start; i < end; i++) {
                    keys[i - start] = chunks.get(i);
                    UUID owner = changes.get(chunks.get(i));
                    Optional<Kingdom> kingdom = NONE.equals(owner) ? Optional.empty() : state.kingdoms().get(owner);
                    if (kingdom.isEmpty()) {
                        owners[i - start] = 0;
                    } else {
                        Integer known = index.get(owner);
                        if (known == null) {
                            palette.add(KingdomViews.mapInfo(server, state, kingdom.get(), dimension));
                            known = palette.size();
                            index.put(owner, known);
                        }
                        owners[i - start] = known;
                    }
                }
                KingdomWorldNetwork.sendToAll(new TerritoryDeltaPacket(dimension, List.copyOf(palette), keys, owners));
            }
        }
        PENDING.clear();
    }

    /**
     * Checks whether the player crossed a border. Called every tick for every player.
     */
    public static void checkBorder(ServerPlayer player) {
        if (player.tickCount % BORDER_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        UUID owner = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
        UUID current = owner == null ? NONE : owner;
        UUID previous = LAST_OWNER.put(player.getUUID(), current);
        if (previous == null || previous.equals(current)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        if (owner != null) {
            state.kingdoms().get(owner).ifPresent(kingdom ->
                    KingdomWorldNetwork.sendToPlayer(player, new BorderCrossingPacket(true, kingdom.name(), kingdom.color())));
        } else {
            state.kingdoms().get(previous).ifPresent(kingdom ->
                    KingdomWorldNetwork.sendToPlayer(player, new BorderCrossingPacket(false, kingdom.name(), kingdom.color())));
        }
    }

    public static void forget(UUID playerId) {
        LAST_OWNER.remove(playerId);
    }

    public static void reset() {
        PENDING.clear();
        LAST_OWNER.clear();
    }

    /**
     * Readable form of a chunk key for logs.
     */
    static String describe(long chunk) {
        return ChunkKey.format(chunk);
    }
}
