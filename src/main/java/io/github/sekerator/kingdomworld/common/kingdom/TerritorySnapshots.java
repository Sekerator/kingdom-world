package io.github.sekerator.kingdomworld.common.kingdom;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.MapVisibility;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the land a client map integration receives: everything the player may see in their dimension, or, when that
 * is more than {@code snapshotMaxChunks}, the square of {@code snapshotRadiusChunks} around the player. Which kingdoms a
 * player sees is decided by the {@link MapVisibility} set through {@link KingdomWorldApi#setMapVisibility}.
 * Used on the server thread.
 */
public final class TerritorySnapshots {
    private static volatile MapVisibility visibility = MapVisibility.PUBLIC;

    private TerritorySnapshots() {
    }

    public static MapVisibility visibility() {
        return visibility;
    }

    public static void setVisibility(@Nullable MapVisibility policy) {
        visibility = policy == null ? MapVisibility.PUBLIC : policy;
    }

    public static TerritorySnapshotPacket build(ServerPlayer player) {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        return build(player.serverLevel(), player.getUUID(), player.chunkPosition().x, player.chunkPosition().z,
                config.snapshotMaxChunks.get(), config.snapshotRadiusChunks.get());
    }

    /**
     * @param maxChunks most chunks to send; above it only the square around the viewer is sent
     * @param radius    half side of that square in chunks
     */
    public static TerritorySnapshotPacket build(ServerLevel level, @Nullable UUID viewer, int centerX, int centerZ, int maxChunks,
                                               int radius) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(level);
        int limit = Math.max(1, Math.min(maxChunks, TerritorySnapshotPacket.MAX_CHUNKS));
        MapVisibility policy = visibility;
        Map<UUID, Boolean> visible = new HashMap<>();
        Map<Long, UUID> claims = state.territory().claimsIn(dimension);

        int seen = 0;
        for (UUID owner : claims.values()) {
            if (visible.computeIfAbsent(owner, id -> policy.canSee(viewer, id))) {
                seen++;
            }
        }
        boolean partial = seen > limit;
        // The square must fit into the limit even when all of it is land.
        int side = (int) Math.floor(Math.sqrt(limit));
        int reach = partial ? Math.max(0, Math.min(radius, (side - 1) / 2)) : 0;

        Map<UUID, List<Long>> byKingdom = new LinkedHashMap<>();
        int taken = 0;
        for (Map.Entry<Long, UUID> entry : claims.entrySet()) {
            if (!visible.get(entry.getValue())) {
                continue;
            }
            long chunk = entry.getKey();
            if (partial && (Math.abs(ChunkKey.x(chunk) - centerX) > reach || Math.abs(ChunkKey.z(chunk) - centerZ) > reach)) {
                continue;
            }
            if (taken >= limit) {
                break;
            }
            byKingdom.computeIfAbsent(entry.getValue(), id -> new ArrayList<>()).add(chunk);
            taken++;
        }

        List<KingdomMapInfo> palette = new ArrayList<>();
        List<long[]> chunks = new ArrayList<>();
        for (Map.Entry<UUID, List<Long>> entry : byKingdom.entrySet()) {
            Optional<Kingdom> kingdom = state.kingdoms().get(entry.getKey());
            if (kingdom.isEmpty() || palette.size() >= TerritorySnapshotPacket.MAX_KINGDOMS) {
                continue;
            }
            palette.add(KingdomViews.mapInfo(server, state, kingdom.get(), dimension));
            chunks.add(entry.getValue().stream().mapToLong(Long::longValue).toArray());
        }
        return new TerritorySnapshotPacket(dimension, partial, centerX, centerZ, reach, palette, chunks);
    }

    /**
     * Whether a viewer may see a kingdom on maps.
     */
    public static boolean canSee(@Nullable UUID viewer, UUID kingdom) {
        Objects.requireNonNull(kingdom, "kingdom");
        try {
            return visibility.canSee(viewer, kingdom);
        } catch (RuntimeException e) {
            // A visibility of another mod that fails hides the kingdom instead of failing the tick of the server.
            if (!visibilityFailed) {
                visibilityFailed = true;
                com.mojang.logging.LogUtils.getLogger().error("The map visibility {} failed; kingdoms it cannot judge are hidden",
                        visibility.getClass().getName(), e);
            }
            return false;
        }
    }

    private static boolean visibilityFailed;
}
