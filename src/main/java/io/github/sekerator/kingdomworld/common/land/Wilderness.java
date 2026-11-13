package io.github.sekerator.kingdomworld.common.land;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.protection.AccessPolicy;
import io.github.sekerator.kingdomworld.core.protection.AccessSubject;
import io.github.sekerator.kingdomworld.core.protection.Wilds;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The wilds of the kingdoms of the world in the running server ({@link Wilds}): which chunks of their provinces nobody lives
 * in, and the exemption that lets players act there as on land of nobody. The chunks a kingdom lives in are gathered again
 * when its buildings, walls or roads change.
 */
public final class Wilderness {
    private record Cached(long buildings, long roads, Wilds.Inhabited chunks) {
    }

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    /** Players may act in the wilds of a kingdom of the world as on land of nobody. */
    public static final AccessPolicy.Exemption EXEMPTION = (subject, owner, action, dimension, chunk) -> {
        if (subject.kind() != AccessSubject.Kind.PLAYER) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && free(server, owner, dimension, chunk, subject.kind(), action);
    };

    private Wilderness() {
    }

    public static void register() {
        KingdomWorldApi.accessPolicy().addExemption(EXEMPTION);
    }

    public static void reset() {
        CACHE.clear();
    }

    /** Whether the wilds are open by the config. */
    public static boolean open() {
        try {
            return KingdomWorldConfig.SERVER.land.wildernessOpen.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean free(MinecraftServer server, UUID owner, String dimension, long chunk, AccessSubject.Kind subject,
                                io.github.sekerator.kingdomworld.core.protection.ProtectedAction action) {
        if (owner == null || !open() || !NpcWork.isNpc(server, owner)) {
            return false;
        }
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        boolean claimed = territory.claimOwner(dimension, chunk) != null;
        return Wilds.free(true, Wilds.Owner.WORLD, claimed, !claimed && inhabited(server, owner, dimension, chunk), subject, action);
    }

    /**
     * Whether a chunk is in the wilds: land of a kingdom of the world (by its provinces) that is not claimed and where none of
     * its buildings, walls and roads stands, while the wilds are open.
     */
    public static boolean isWild(MinecraftServer server, String dimension, long chunk) {
        if (!open()) {
            return false;
        }
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        UUID owner = territory.ownerOf(dimension, chunk);
        if (owner == null || territory.claimOwner(dimension, chunk) != null || !NpcWork.isNpc(server, owner)) {
            return false;
        }
        return !inhabited(server, owner, dimension, chunk);
    }

    /**
     * The kingdom whose places (homes, posts, storehouses) a chunk holds: the owner of its claim, a kingdom of players owning
     * its province, a kingdom of the world living there; nobody in the wilds.
     */
    public static UUID placeOwner(MinecraftServer server, String dimension, long chunk) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        UUID claim = territory.claimOwner(dimension, chunk);
        if (claim != null) {
            return claim;
        }
        UUID owner = territory.ownerOf(dimension, chunk);
        if (owner == null) {
            return null;
        }
        return isWild(server, dimension, chunk) ? null : owner;
    }

    /**
     * Whether a kingdom lives in a chunk: the square of one of its settlements being raised or standing there, or a building, a
     * wall or a road of it.
     */
    public static boolean inhabited(MinecraftServer server, UUID kingdomId, String dimension, long chunk) {
        if (dimension.equals("minecraft:overworld")) {
            java.util.Optional<io.github.sekerator.kingdomworld.core.npc.Settlement> settlement = io.github.sekerator.kingdomworld.common.npc.SettlementIndex
                    .at(server, io.github.sekerator.kingdomworld.core.territory.ChunkKey.x(chunk), io.github.sekerator.kingdomworld.core.territory.ChunkKey.z(chunk));
            if (settlement.isPresent() && kingdomId.equals(settlement.get().kingdomId()) && lived(settlement.get())) {
                return true;
            }
        }
        return chunks(server, kingdomId).contains(dimension, chunk);
    }

    /** Whether a settlement is lived in: being raised or standing. */
    public static boolean lived(io.github.sekerator.kingdomworld.core.npc.Settlement settlement) {
        return settlement.status() == io.github.sekerator.kingdomworld.core.npc.Settlement.Status.RAISING
                || settlement.status() == io.github.sekerator.kingdomworld.core.npc.Settlement.Status.STANDING;
    }

    /**
     * The chunks a kingdom lives in beyond its claims, gathered again when its buildings or its roads and walls changed.
     */
    static Wilds.Inhabited chunks(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        RoadState roads = RoadWork.state(server);
        long buildingsVersion = state.construction().version();
        long roadsVersion = roads.version();
        Cached cached = CACHE.get(kingdomId);
        if (cached != null && cached.buildings() == buildingsVersion && cached.roads() == roadsVersion) {
            return cached.chunks();
        }
        Wilds.Inhabited chunks = new Wilds.Inhabited();
        for (Building building : state.construction().buildingsOf(kingdomId)) {
            chunks.addBox(building.anchor().dimension(), building.box().minX(), building.box().minZ(), building.box().maxX(),
                    building.box().maxZ());
        }
        for (Road road : roads.roadsOf(kingdomId)) {
            for (RouteCell cell : road.cells()) {
                chunks.addBlock(road.dimension(), cell.x(), cell.z());
            }
        }
        for (Wall wall : roads.walls()) {
            if (kingdomId.equals(wall.kingdomId())) {
                for (RouteCell cell : wall.cells()) {
                    chunks.addBlock(wall.dimension(), cell.x(), cell.z());
                }
            }
        }
        CACHE.put(kingdomId, new Cached(buildingsVersion, roadsVersion, chunks));
        return chunks;
    }
}
