package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotRequestPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Keeps the land of the player's dimension known on the client while a map integration needs it. It asks the server
 * once per world and dimension; when the server answers with only the square around the player (the map is too large
 * to send at once), it asks again after the player has walked half way to the edge of that square. Changes in
 * between arrive as deltas. Without a map integration nothing is requested. Used on the client main thread.
 */
public final class ClientMapSync {
    private static final long RETRY_MILLIS = 3_000L;
    private static final Set<String> USERS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Supplier<String>> STATS = new ConcurrentHashMap<>();

    private static String snapshotDimension;
    private static boolean partial;
    private static int centerX;
    private static int centerZ;
    private static int radius;
    private static long lastRequest;
    private static int requests;
    private static int snapshots;

    private ClientMapSync() {
    }

    /**
     * Called by a map integration that shows land; from then on the land of the player's dimension is kept known.
     */
    public static void enable(String user) {
        USERS.add(user);
    }

    /**
     * Like {@link #enable(String)}, with a description of what the integration shows for the client self-check.
     */
    public static void enable(String user, Supplier<String> stats) {
        USERS.add(user);
        STATS.put(user, stats);
    }

    public static boolean active() {
        return !USERS.isEmpty();
    }

    public static Set<String> users() {
        return Set.copyOf(USERS);
    }

    /**
     * Called every client tick.
     */
    public static void tick(Minecraft minecraft) {
        if (!active() || minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        boolean needed = !dimension.equals(snapshotDimension) || !dimension.equals(ClientTerritoryCache.dimension());
        if (!needed && partial) {
            int chunkX = minecraft.player.chunkPosition().x;
            int chunkZ = minecraft.player.chunkPosition().z;
            needed = Math.max(Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ)) > radius / 2;
        }
        long now = Util.getMillis();
        if (needed && now - lastRequest >= RETRY_MILLIS) {
            lastRequest = now;
            requests++;
            KingdomWorldNetwork.sendToServer(new TerritorySnapshotRequestPacket());
        }
        ClientMapModel.tick(minecraft);
    }

    public static void onSnapshot(TerritorySnapshotPacket packet) {
        snapshotDimension = packet.dimension();
        partial = packet.partial();
        centerX = packet.centerX();
        centerZ = packet.centerZ();
        radius = packet.radius();
        snapshots++;
    }

    /**
     * Forgets what was received, for example when the player leaves the world.
     */
    public static void reset() {
        snapshotDimension = null;
        partial = false;
        lastRequest = 0L;
        ClientMapModel.reset();
    }

    /**
     * Requests sent and snapshots received since the game started, for the client self-check.
     */
    public static String stats() {
        StringBuilder text = new StringBuilder(requests + " request(s), " + snapshots + " snapshot(s)"
                + (partial ? ", partial radius " + radius : ""));
        STATS.forEach((user, supplier) -> text.append("; ").append(user).append(": ").append(supplier.get()));
        return text.toString();
    }
}
