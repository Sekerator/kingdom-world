package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceListener;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.Deflater;

/**
 * Tells players of the provinces: the whole map of provinces of their dimension when they join or change dimension, a part of
 * {@value ProvinceSnapshotPacket#MAX_TILES} tiles a tick; then what changed of the tiles and the table, at most a part a tick; and
 * every province that changed hands as one entry, however large. A province of a kingdom a player may not see
 * ({@link TerritorySnapshots#canSee}) reaches that player as a province of nobody; what each player was told is remembered, and
 * every few seconds a change of what they may see is sent as a correction. Server thread only.
 */
public final class ProvinceSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID NONE = new UUID(0L, 0L);
    /** Ticks between the checks of what players may see. */
    static final int VISIBILITY_CHECK_TICKS = 100;

    /** The parts of a snapshot each player still waits for (and what came after them), one a tick, and what they weigh. */
    private static final Map<UUID, Deque<Object>> QUEUES = new HashMap<>();
    private static final Map<UUID, long[]> WEIGHTS = new HashMap<>();
    /** What each player was told of the holder of each province, by number; {@code null} for nobody. */
    private static final Map<UUID, UUID[]> TOLD = new HashMap<>();
    /** Provinces that changed hands since the last tick. */
    private static final Map<Integer, UUID> CHANGED = new LinkedHashMap<>();
    private static final ProvinceListener LISTENER = (dimension, province, oldOwner, newOwner) ->
            CHANGED.put(province, newOwner == null ? NONE : newOwner);
    private static TerritoryIndex listened;
    private static int ticks;

    private ProvinceSync() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ProvinceSync::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ProvinceSync::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(ProvinceSync::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(ProvinceSync::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ProvinceSync::onServerStopped);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            queueSnapshot(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            queueSnapshot(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick(event.getServer());
        }
    }

    static void forget(UUID player) {
        QUEUES.remove(player);
        WEIGHTS.remove(player);
        TOLD.remove(player);
    }

    static void reset() {
        QUEUES.clear();
        WEIGHTS.clear();
        TOLD.clear();
        CHANGED.clear();
        if (listened != null) {
            listened.removeProvinceListener(LISTENER);
        }
        listened = null;
        ticks = 0;
    }

    // ---------------------------------------------------------------- snapshots

    /**
     * Makes the parts of the whole snapshot for a player and sends them over the next ticks, one a tick. A player in a dimension
     * without provinces gets a single empty part: the provinces known before are forgotten.
     */
    public static void queueSnapshot(ServerPlayer player) {
        MinecraftServer server = player.server;
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        UUID[] told = new UUID[map == null ? 1 : maxId(map) + 1];
        TOLD.put(player.getUUID(), told);
        Deque<Object> parts = new ArrayDeque<>(snapshot(server, Dimensions.id(player.level()), player.getUUID(), told));
        QUEUES.put(player.getUUID(), parts);
        WEIGHTS.put(player.getUUID(), new long[]{parts.size(), 0, 0});
    }

    /**
     * The parts of the whole snapshot of a dimension as a player may see it: the first starts the provinces over and carries the
     * table, every part at most {@value ProvinceSnapshotPacket#MAX_TILES} tiles. A dimension without provinces gives one empty
     * part.
     *
     * @param told filled with what the player is told of the holder of each province, by number; may be shorter
     */
    public static List<ProvinceSnapshotPacket> snapshot(MinecraftServer server, String dimension, UUID viewer, UUID[] told) {
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        List<ProvinceSnapshotPacket> parts = new ArrayList<>();
        if (map == null || !map.dimension().equals(dimension)) {
            parts.add(new ProvinceSnapshotPacket(dimension, true, new long[0], List.of(), List.of(), new int[0], List.of(), new int[0]));
            return parts;
        }
        long[] keys = map.tileKeys();
        Arrays.sort(keys);
        Table table = table(server, map, map.provinces().stream().mapToInt(Province::id).toArray(), viewer, told);
        int at = 0;
        do {
            int end = Math.min(keys.length, at + ProvinceSnapshotPacket.MAX_TILES);
            long[] part = Arrays.copyOfRange(keys, at, end);
            List<char[]> tiles = new ArrayList<>(part.length);
            for (long key : part) {
                char[] tile = map.tile(key);
                tiles.add(tile == null ? new char[0] : tile);
            }
            boolean first = at == 0;
            parts.add(new ProvinceSnapshotPacket(dimension, first, part, tiles, first ? table.entries : List.of(), new int[0],
                    first ? table.palette : List.of(), first ? table.owners : new int[0]));
            at = end;
        } while (at < keys.length);
        return parts;
    }

    /**
     * The provinces that changed hands since the last tick and are not sent yet, for game tests.
     */
    public static Map<Integer, UUID> pendingOwners() {
        return Map.copyOf(CHANGED);
    }

    /**
     * Starts following the provinces of the land of a server now rather than at the end of the tick, for game tests.
     */
    public static void listen(MinecraftServer server) {
        TerritoryIndex territory = LandEvents.attach(server);
        if (listened != territory) {
            if (listened != null) {
                listened.removeProvinceListener(LISTENER);
            }
            territory.addProvinceListener(LISTENER);
            listened = territory;
        }
    }

    /**
     * The table of some provinces as a player may see them, remembering what the player was told.
     */
    private static Table table(MinecraftServer server, ProvinceMap map, int[] ids, UUID viewer, UUID[] told) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<ProvinceSnapshotPacket.Entry> entries = new ArrayList<>();
        List<KingdomMapInfo> palette = new ArrayList<>();
        Map<UUID, Integer> index = new HashMap<>();
        int[] owners = new int[ids.length];
        int count = 0;
        for (int id : ids) {
            Province province = map.province(id).orElse(null);
            if (province == null) {
                continue;
            }
            UUID owner = visible(map.owner(id), viewer);
            if (id < told.length) {
                told[id] = owner;
            }
            entries.add(new ProvinceSnapshotPacket.Entry(id, province.centerX(), province.centerZ(), province.kind(), province.nameSeed()));
            owners[count++] = paletteIndex(server, state, owner, map.dimension(), palette, index);
        }
        return new Table(entries, palette, Arrays.copyOf(owners, count));
    }

    private record Table(List<ProvinceSnapshotPacket.Entry> entries, List<KingdomMapInfo> palette, int[] owners) {
    }

    private static int paletteIndex(MinecraftServer server, KingdomWorldState state, UUID owner, String dimension, List<KingdomMapInfo> palette,
                                    Map<UUID, Integer> index) {
        if (owner == null) {
            return 0;
        }
        Integer known = index.get(owner);
        if (known != null) {
            return known;
        }
        Kingdom kingdom = state.kingdoms().get(owner).orElse(null);
        if (kingdom == null) {
            return 0;
        }
        palette.add(KingdomViews.mapInfo(server, state, kingdom, dimension));
        index.put(owner, palette.size());
        return palette.size();
    }

    /**
     * The holder a player may see: the holder itself, or nobody for a kingdom the map visibility hides from the player.
     */
    private static UUID visible(UUID owner, UUID viewer) {
        return owner != null && TerritorySnapshots.canSee(viewer, owner) ? owner : null;
    }

    private static int maxId(ProvinceMap map) {
        int max = 0;
        for (Province province : map.provinces()) {
            max = Math.max(max, province.id());
        }
        return max;
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        listen(server);
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        if (map != null) {
            sendChanges(server, map);
            sendOwners(server, map);
            if (++ticks >= VISIBILITY_CHECK_TICKS) {
                ticks = 0;
                correctVisibility(server, map);
            }
        } else {
            CHANGED.clear();
        }
        sendQueued(server);
    }

    /**
     * What changed of the tiles and the table since the last tick, to every player in the dimension of the provinces: a part a
     * tick at most, the rest of the tiles left for the next ticks.
     */
    private static void sendChanges(MinecraftServer server, ProvinceMap map) {
        long[] keys = map.drainTouchedTiles();
        int[] ids = map.drainTouchedProvinces();
        if (keys.length == 0 && ids.length == 0) {
            return;
        }
        List<ServerPlayer> players = playersIn(server, map.dimension());
        if (players.isEmpty()) {
            return;
        }
        Arrays.sort(keys);
        List<int[]> forgotten = new ArrayList<>();
        List<Integer> described = new ArrayList<>();
        for (int id : ids) {
            if (map.exists(id)) {
                described.add(id);
            } else {
                forgotten.add(new int[]{id});
            }
        }
        int[] removed = forgotten.stream().mapToInt(value -> value[0]).toArray();
        int[] table = described.stream().mapToInt(Integer::intValue).toArray();
        for (ServerPlayer player : players) {
            UUID[] told = told(player, map);
            Table part = table(server, map, table, player.getUUID(), told);
            int at = 0;
            do {
                int end = Math.min(keys.length, at + ProvinceSnapshotPacket.MAX_TILES);
                long[] chunk = Arrays.copyOfRange(keys, at, end);
                List<char[]> tiles = new ArrayList<>(chunk.length);
                for (long key : chunk) {
                    char[] tile = map.tile(key);
                    tiles.add(tile == null ? new char[0] : tile);
                }
                boolean first = at == 0;
                send(player, new ProvinceSnapshotPacket(map.dimension(), false, chunk, tiles, first ? part.entries : List.of(),
                        first ? removed : new int[0], first ? part.palette : List.of(), first ? part.owners : new int[0]));
                at = end;
            } while (at < keys.length);
        }
    }

    /**
     * The provinces that changed hands since the last tick, one entry each, to every player in their dimension.
     */
    private static void sendOwners(MinecraftServer server, ProvinceMap map) {
        if (CHANGED.isEmpty()) {
            return;
        }
        Map<Integer, UUID> changed = new LinkedHashMap<>(CHANGED);
        CHANGED.clear();
        for (ServerPlayer player : playersIn(server, map.dimension())) {
            UUID[] told = told(player, map);
            List<Integer> ids = new ArrayList<>();
            List<UUID> owners = new ArrayList<>();
            changed.forEach((id, owner) -> {
                UUID shown = visible(NONE.equals(owner) ? null : owner, player.getUUID());
                if (id < told.length && !Objects.equals(told[id], shown) || id >= told.length) {
                    ids.add(id);
                    owners.add(shown);
                }
            });
            sendDelta(server, player, map, ids, owners, told);
        }
    }

    /**
     * What each player may see of the holders, compared with what the player was told: the differences go as one delta.
     */
    private static void correctVisibility(MinecraftServer server, ProvinceMap map) {
        for (ServerPlayer player : playersIn(server, map.dimension())) {
            if (QUEUES.containsKey(player.getUUID())) {
                continue;
            }
            UUID[] told = told(player, map);
            List<Integer> ids = new ArrayList<>();
            List<UUID> owners = new ArrayList<>();
            for (Province province : map.provinces()) {
                UUID shown = visible(map.owner(province.id()), player.getUUID());
                if (!Objects.equals(told[province.id()], shown)) {
                    ids.add(province.id());
                    owners.add(shown);
                }
            }
            sendDelta(server, player, map, ids, owners, told);
        }
    }

    private static void sendDelta(MinecraftServer server, ServerPlayer player, ProvinceMap map, List<Integer> ids, List<UUID> owners,
                                  UUID[] told) {
        if (ids.isEmpty()) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<KingdomMapInfo> palette = new ArrayList<>();
        Map<UUID, Integer> index = new HashMap<>();
        int[] provinces = new int[ids.size()];
        int[] indices = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            provinces[i] = ids.get(i);
            indices[i] = paletteIndex(server, state, owners.get(i), map.dimension(), palette, index);
            if (provinces[i] < told.length) {
                told[provinces[i]] = indices[i] == 0 ? null : owners.get(i);
            }
        }
        send(player, new ProvinceDeltaPacket(map.dimension(), provinces, palette, indices));
    }

    /**
     * What a player was told of the holders, grown to the highest number of a province.
     */
    private static UUID[] told(ServerPlayer player, ProvinceMap map) {
        UUID[] told = TOLD.get(player.getUUID());
        int size = maxId(map) + 1;
        if (told == null || told.length < size) {
            told = told == null ? new UUID[size] : Arrays.copyOf(told, size);
            TOLD.put(player.getUUID(), told);
        }
        return told;
    }

    /**
     * A packet for a player: after the parts of a snapshot still on their way, or at once.
     */
    private static void send(ServerPlayer player, Object packet) {
        Deque<Object> queue = QUEUES.get(player.getUUID());
        if (queue != null) {
            queue.add(packet);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, packet);
    }

    /**
     * The next part of every snapshot on its way; when the last one is sent, what the snapshot weighed goes to the log.
     */
    private static void sendQueued(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Deque<Object> queue = QUEUES.get(player.getUUID());
            if (queue == null) {
                continue;
            }
            Object part = queue.poll();
            if (part != null) {
                long[] weight = WEIGHTS.get(player.getUUID());
                if (weight != null && part instanceof ProvinceSnapshotPacket snapshot) {
                    long[] bytes = weigh(snapshot);
                    weight[1] += bytes[0];
                    weight[2] += bytes[1];
                }
                KingdomWorldNetwork.sendToPlayer(player, part);
            }
            if (queue.isEmpty()) {
                QUEUES.remove(player.getUUID());
                long[] weight = WEIGHTS.remove(player.getUUID());
                if (weight != null && weight[1] > 0) {
                    LOGGER.info("Provinces sent to {}: {} part(s), {} bytes, about {} bytes compressed", player.getGameProfile().getName(),
                            weight[0], weight[1], weight[2]);
                }
            }
        }
        QUEUES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /**
     * What a part weighs as written and as the network would compress it.
     */
    static long[] weigh(ProvinceSnapshotPacket part) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            part.encode(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            Deflater deflater = new Deflater();
            try {
                deflater.setInput(bytes);
                deflater.finish();
                byte[] out = new byte[Math.max(64, bytes.length)];
                long compressed = 0;
                while (!deflater.finished()) {
                    compressed += deflater.deflate(out);
                }
                return new long[]{bytes.length, compressed};
            } finally {
                deflater.end();
            }
        } finally {
            buf.release();
        }
    }

    private static List<ServerPlayer> playersIn(MinecraftServer server, String dimension) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof FakePlayer) && Dimensions.id(player.level()).equals(dimension)) {
                players.add(player);
            }
        }
        return players;
    }
}
