package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotRequestPacket;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the land sent to map integrations on clients: a client gets the land of its dimension, only the
 * square around the player when the land is larger than the limit, and never land of a kingdom hidden from it; the provinces of
 * its dimension with the holders it may see, a province changing hands as one change.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapGameTests {
    private static final String EMPTY = "empty";

    private MapGameTests() {
    }

    private static Kingdom found(GameTestHelper helper, BlockPos throne, String name, int color) {
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(helper.getLevel(), throne, name, color, UUID.randomUUID());
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        helper.fail("Could not found '" + name + "': " + result);
        throw new IllegalStateException("unreachable");
    }

    private static void clearNear(ServerLevel level, ChunkPos center, int radius) {
        MinecraftServer server = level.getServer();
        List<UUID> stale = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            if (territory.chunks().stream().anyMatch(chunk -> ChunkKey.distance(chunk, ChunkKey.of(center.x, center.z)) <= radius)) {
                stale.add(territory.kingdomId());
            }
        }
        stale.forEach(id -> KingdomWorldApi.dissolveKingdom(server, id));
    }

    private static Set<Long> chunksOf(TerritorySnapshotPacket snapshot, UUID kingdom) {
        Set<Long> result = new HashSet<>();
        for (int i = 0; i < snapshot.palette().size(); i++) {
            if (snapshot.palette().get(i).id().equals(kingdom)) {
                for (long chunk : snapshot.chunks().get(i)) {
                    result.add(chunk);
                }
            }
        }
        return result;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_maps")
    public static void territorySnapshotSendsOnlyVisibleLandWithinTheLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkPos center = new ChunkPos(throne);
        clearNear(level, center, 30);
        Kingdom near = found(helper, throne, "GameTest Map Near", 0x3366CC);
        Kingdom far = found(helper, throne.offset(16 * 16, 0, 0), "GameTest Map Far", 0xCC6633);
        ServerPlayer viewer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "kw-map-viewer"));
        try {
            Set<Long> nearLand = Set.copyOf(KingdomWorldApi.state(server).territory().territory(near.id()).orElseThrow().chunks());
            Set<Long> farLand = Set.copyOf(KingdomWorldApi.state(server).territory().territory(far.id()).orElseThrow().chunks());

            // Everything fits: the whole land of the dimension.
            TerritorySnapshotPacket whole = TerritorySnapshots.build(level, viewer.getUUID(), center.x, center.z, 65_536, 64);
            helper.assertTrue(!whole.partial(), "Small land must be sent whole");
            helper.assertTrue(chunksOf(whole, near.id()).equals(nearLand), "Near kingdom's land is incomplete");
            helper.assertTrue(chunksOf(whole, far.id()).equals(farLand), "Far kingdom's land is incomplete");
            KingdomMapInfo info = whole.palette().stream().filter(k -> k.id().equals(near.id())).findFirst().orElseThrow();
            helper.assertTrue(info.hasCapital() && info.capitalChunkX() == center.x && info.name().equals(near.name()),
                    "Map facts of the kingdom are wrong");

            // Over the limit: only the square around the viewer that fits the limit.
            TerritorySnapshotPacket limited = TerritorySnapshots.build(level, viewer.getUUID(), center.x, center.z, 9, 64);
            helper.assertTrue(limited.partial() && limited.radius() == 1, "Large land must be sent around the player: " + limited.radius());
            helper.assertTrue(limited.chunkCount() <= 9, "Snapshot exceeds its limit: " + limited.chunkCount());
            helper.assertTrue(chunksOf(limited, near.id()).equals(nearLand), "Land around the player is missing");
            helper.assertTrue(chunksOf(limited, far.id()).isEmpty(), "Land far from the player was sent");
            helper.assertTrue(limited.covers(center.x + 1, center.z) && !limited.covers(center.x + 2, center.z), "Covered square is wrong");

            // A kingdom hidden from the viewer never reaches it, neither whole nor on the atlas.
            TerritorySnapshots.setVisibility((player, kingdom) -> !kingdom.equals(far.id()) || !viewer.getUUID().equals(player));
            TerritorySnapshotPacket hidden = TerritorySnapshots.build(level, viewer.getUUID(), center.x, center.z, 65_536, 64);
            helper.assertTrue(chunksOf(hidden, far.id()).isEmpty(), "Hidden kingdom was sent");
            helper.assertTrue(hidden.palette().stream().noneMatch(k -> k.id().equals(far.id())), "Hidden kingdom is named");
            helper.assertTrue(chunksOf(hidden, near.id()).equals(nearLand), "Visible kingdom went missing");
            ChunkPos farCapital = new ChunkPos(throne.offset(16 * 16, 0, 0));
            MapAreaPacket area = KingdomViews.mapArea(level, viewer.getUUID(), farCapital.x - 2, farCapital.z - 2, 5, 5);
            helper.assertTrue(area.palette().isEmpty() && area.ownerIndex(farCapital.x, farCapital.z) == -1, "Atlas showed a hidden kingdom");
            TerritorySnapshotPacket other = TerritorySnapshots.build(level, UUID.randomUUID(), center.x, center.z, 65_536, 64);
            helper.assertTrue(chunksOf(other, far.id()).equals(farLand), "Kingdom hidden from one player is hidden from all");
            TerritorySnapshots.setVisibility(null);

            // The compact form survives the trip, and the request goes through the request guard.
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            limited.encode(buf);
            TerritorySnapshotPacket decoded = TerritorySnapshotPacket.decode(buf);
            helper.assertTrue(decoded.palette().equals(limited.palette()) && chunksOf(decoded, near.id()).equals(nearLand)
                    && decoded.partial() && decoded.centerX() == center.x && decoded.radius() == 1, "Snapshot changed in transit");
            helper.assertTrue(new TerritorySnapshotRequestPacket().respond(viewer).isPresent(), "A player's request was refused");
            helper.assertTrue(new TerritorySnapshotRequestPacket().respond(null).isEmpty(), "A request without a player was answered");
        } finally {
            TerritorySnapshots.setVisibility(null);
            KingdomWorldApi.dissolveKingdom(server, near.id());
            KingdomWorldApi.dissolveKingdom(server, far.id());
        }
        helper.succeed();
    }

    /**
     * The provinces reach a client as the snapshot of its dimension: the tiles, the table and the holders it may see; a hidden
     * holder reaches it as nobody; a province changing hands is one change however large it is; the atlas of the server sees the
     * holder of a province.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_maps_provinces")
    public static void provincesReachAClientAsItMaySeeThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkPos center = new ChunkPos(throne);
        clearNear(level, center, 30);
        int held = 65_301;
        int wild = 65_302;
        io.github.sekerator.kingdomworld.core.territory.TerritoryIndex territory =
                io.github.sekerator.kingdomworld.common.land.LandEvents.attach(server);
        io.github.sekerator.kingdomworld.core.territory.ProvinceMap map = territory.provinceMap().orElseThrow();
        for (int id : new int[]{held, wild}) {
            if (map.exists(id)) {
                territory.clearProvince(id);
            }
        }
        Kingdom holder = found(helper, throne, "GameTest Province Map", 0x2E8B57);
        ServerPlayer viewer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "kw-province-viewer"));
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level);
        try {
            map.define(io.github.sekerator.kingdomworld.core.territory.Province.plain(held, (center.x + 8) * 16, center.z * 16,
                    io.github.sekerator.kingdomworld.core.land.ProvinceKind.CORE));
            map.define(io.github.sekerator.kingdomworld.core.territory.Province.plain(wild, (center.x + 20) * 16, center.z * 16,
                    io.github.sekerator.kingdomworld.core.land.ProvinceKind.WILD));
            territory.fillProvince(held, center.x + 6, center.z - 2, center.x + 10, center.z + 2);
            territory.fillProvince(wild, center.x + 15, center.z - 5, center.x + 24, center.z + 4);
            territory.transferProvince(held, holder.id(), 0);

            // The snapshot of the dimension: tiles with both provinces, the table, the holder named.
            UUID[] told = new UUID[map.provinces().stream().mapToInt(io.github.sekerator.kingdomworld.core.territory.Province::id).max().orElse(0) + 1];
            List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket> parts =
                    io.github.sekerator.kingdomworld.common.land.ProvinceSync.snapshot(server, dimension, viewer.getUUID(), told);
            helper.assertTrue(!parts.isEmpty() && parts.get(0).reset(), "The snapshot does not start the provinces over");
            helper.assertTrue(holder.id().equals(ownerIn(parts, held)), "The holder of a province did not reach the client");
            helper.assertTrue(ownerIn(parts, wild) == null && hasProvince(parts, wild), "The wild province is missing or held");
            helper.assertTrue(chunkOf(parts, center.x + 8, center.z) == held && chunkOf(parts, center.x + 20, center.z) == wild,
                    "The tiles of the provinces did not reach the client");
            for (io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket part : parts) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                part.encode(buf);
                helper.assertTrue(buf.readableBytes() <= 256 * 1024, "A part of the snapshot takes " + buf.readableBytes() + " bytes");
                io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.decode(buf);
            }

            // A holder hidden from the viewer reaches it as nobody, and is not named.
            TerritorySnapshots.setVisibility((player, kingdom) -> !kingdom.equals(holder.id()) || !viewer.getUUID().equals(player));
            List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket> hidden =
                    io.github.sekerator.kingdomworld.common.land.ProvinceSync.snapshot(server, dimension, viewer.getUUID(), new UUID[0]);
            helper.assertTrue(ownerIn(hidden, held) == null && hidden.get(0).palette().stream().noneMatch(k -> k.id().equals(holder.id())),
                    "A hidden holder of a province reached the client");
            TerritorySnapshots.setVisibility(null);

            // A province of a hundred chunks changing hands is one change.
            io.github.sekerator.kingdomworld.common.land.ProvinceSync.listen(server);
            int before = io.github.sekerator.kingdomworld.common.land.ProvinceSync.pendingOwners().size();
            territory.transferProvince(wild, holder.id(), 0);
            java.util.Map<Integer, UUID> pending = io.github.sekerator.kingdomworld.common.land.ProvinceSync.pendingOwners();
            helper.assertTrue(holder.id().equals(pending.get(wild)) && pending.size() <= before + 1,
                    "A province changing hands is not one change: " + pending);

            // The atlas of the server sees the holder of a province.
            MapAreaPacket area = KingdomViews.mapArea(level, viewer.getUUID(), center.x + 4, center.z - 4, 9, 9);
            int index = area.ownerIndex(center.x + 8, center.z);
            helper.assertTrue(index >= 0 && area.palette().get(index).id().equals(holder.id()), "The atlas does not see the province");
            MapAreaPacket far = KingdomViews.mapArea(level, viewer.getUUID(), center.x - 64, center.z - 64, 64, 64, 4);
            helper.assertTrue(far.palette().stream().anyMatch(k -> k.id().equals(holder.id())), "The atlas zoomed out does not see the province");
            helper.assertTrue(dimension.equals(map.dimension()), "The provinces lie in another dimension");
        } finally {
            TerritorySnapshots.setVisibility(null);
            for (int id : new int[]{held, wild}) {
                if (map.exists(id)) {
                    territory.clearProvince(id);
                }
            }
            KingdomWorldApi.dissolveKingdom(server, holder.id());
        }
        helper.succeed();
    }

    private static UUID ownerIn(List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket> parts, int id) {
        for (io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket part : parts) {
            for (int i = 0; i < part.provinces().size(); i++) {
                if (part.provinces().get(i).id() == id) {
                    int index = part.owners()[i] - 1;
                    return index < 0 ? null : part.palette().get(index).id();
                }
            }
        }
        return null;
    }

    private static boolean hasProvince(List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket> parts, int id) {
        return parts.stream().anyMatch(part -> part.provinces().stream().anyMatch(entry -> entry.id() == id));
    }

    private static int chunkOf(List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket> parts, int chunkX, int chunkZ) {
        long key = io.github.sekerator.kingdomworld.core.territory.ProvinceMap.tileKey(chunkX >> 5, chunkZ >> 5);
        for (io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket part : parts) {
            for (int i = 0; i < part.tileKeys().length; i++) {
                if (part.tileKeys()[i] == key && part.tiles().get(i).length > 0) {
                    return part.tiles().get(i)[io.github.sekerator.kingdomworld.core.territory.ProvinceMap.indexInTile(chunkX, chunkZ)];
                }
            }
        }
        return 0;
    }
}
