package io.github.sekerator.kingdomworld.compat.xaero;

import io.github.sekerator.kingdomworld.client.map.ClientMapModel;
import io.github.sekerator.kingdomworld.client.map.ClientProvinceMap;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.map.MapLabels;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Land of kingdoms as chunk highlights for Xaero's maps, shared by the World Map and the Minimap highlighters. Xaero's
 * maps ask from their own threads, so the owners of the current dimension are copied into concurrent maps here; the
 * copy follows {@link ClientTerritoryCache} on the client main thread and remembers which map regions (32 by 32
 * chunks) changed, so the maps redraw only those. The provinces are read from a view of {@link ClientProvinceMap} that never
 * changes once made; a new view redraws every region. A capital chunk is drawn stronger: Xaero's maps have no markers for other
 * mods, so this is how a capital shows.
 */
final class KingdomHighlights implements ClientMapModel.Listener {
    static final KingdomHighlights INSTANCE = new KingdomHighlights();
    private static final int BORDER_ALPHA = 0xD0;

    private final Map<Long, UUID> owners = new ConcurrentHashMap<>();
    private final Map<Long, Integer> landPerRegion = new ConcurrentHashMap<>();
    private final Map<Long, Integer> regionVersions = new ConcurrentHashMap<>();
    private final Map<UUID, KingdomMapInfo> kingdoms = new ConcurrentHashMap<>();
    private final Map<UUID, Long> capitals = new ConcurrentHashMap<>();
    private final Set<Long> dirtyRegions = new HashSet<>();
    private volatile String dimension;
    private volatile int labelVersion;
    private volatile ClientProvinceMap.View provinces = ClientProvinceMap.View.EMPTY;
    private long provincesSeen = -1;
    private boolean allDirty;
    private boolean listening;
    private int generation = -1;

    private KingdomHighlights() {
    }

    /**
     * Starts following the land; called on the client main thread.
     */
    void listen() {
        if (!listening) {
            listening = true;
            ClientTerritoryCache.addClaimListener(this::onChunk);
            ClientMapModel.addListener(this);
        }
    }

    // ---------------------------------------------------------------- following the cache (main thread)

    private void onChunk(String chunkDimension, long chunk, UUID oldOwner, UUID newOwner) {
        if (generation != ClientTerritoryCache.generation()) {
            return;
        }
        if (newOwner == null) {
            owners.remove(chunk);
        } else {
            owners.put(chunk, newOwner);
        }
        long region = region(ChunkKey.x(chunk), ChunkKey.z(chunk));
        int delta = (oldOwner == null ? 1 : 0) - (newOwner == null ? 1 : 0);
        if (delta != 0) {
            landPerRegion.merge(region, delta, (a, b) -> a + b == 0 ? null : a + b);
        }
        // The borders of the neighbours change as well, and they may lie in the next region.
        markDirty(chunk);
        for (long side : ChunkKey.sides(chunk)) {
            markDirty(side);
        }
    }

    private void markDirty(long chunk) {
        long region = region(ChunkKey.x(chunk), ChunkKey.z(chunk));
        regionVersions.merge(region, 1, Integer::sum);
        dirtyRegions.add(region);
    }

    /**
     * Copies the cache again when it started over, and refreshes the labels of kingdoms. Called on every client tick
     * before the maps are told what changed.
     */
    void sync() {
        if (generation != ClientTerritoryCache.generation() || !java.util.Objects.equals(dimension, ClientTerritoryCache.dimension())) {
            generation = ClientTerritoryCache.generation();
            dimension = ClientTerritoryCache.dimension();
            owners.clear();
            landPerRegion.clear();
            owners.putAll(ClientTerritoryCache.owners());
            for (long chunk : owners.keySet()) {
                landPerRegion.merge(region(ChunkKey.x(chunk), ChunkKey.z(chunk)), 1, Integer::sum);
            }
            labelVersion++;
            allDirty = true;
        }
        if (ClientProvinceMap.version() != provincesSeen) {
            provincesSeen = ClientProvinceMap.version();
            provinces = ClientProvinceMap.view();
            labelVersion++;
            allDirty = true;
        }
        for (KingdomMapInfo info : ClientTerritoryCache.kingdoms().values()) {
            kingdoms.put(info.id(), info);
            if (info.hasCapital()) {
                capitals.put(info.id(), ChunkKey.of(info.capitalChunkX(), info.capitalChunkZ()));
            } else {
                capitals.remove(info.id());
            }
        }
    }

    @Override
    public void onLand(String newDimension, Map<UUID, List<ChunkOutlines.Area>> changed, boolean reset) {
        if (reset) {
            allDirty = true;
        }
    }

    @Override
    public void onKingdoms(Set<UUID> changed) {
        labelVersion++;
        allDirty = true;
    }

    /**
     * Regions to redraw since the last call; {@code null} means all of them.
     */
    Set<Long> takeDirty() {
        if (allDirty) {
            allDirty = false;
            dirtyRegions.clear();
            return null;
        }
        Set<Long> taken = Set.copyOf(dirtyRegions);
        dirtyRegions.clear();
        return taken;
    }

    // ---------------------------------------------------------------- answers for the maps (any thread)

    static long region(int chunkX, int chunkZ) {
        return ChunkKey.of(chunkX >> 5, chunkZ >> 5);
    }

    boolean shows(String askedDimension) {
        return askedDimension.equals(dimension) && KingdomWorldConfig.CLIENT.xaeroMaps.get();
    }

    boolean regionHasLand(String askedDimension, int regionX, int regionZ) {
        if (!shows(askedDimension)) {
            return false;
        }
        if (landPerRegion.containsKey(ChunkKey.of(regionX, regionZ))) {
            return true;
        }
        // A region of Xaero's maps is a tile of the provinces.
        ClientProvinceMap.View view = provinces;
        return askedDimension.equals(view.dimension())
                && view.tiles().containsKey(io.github.sekerator.kingdomworld.core.territory.ProvinceMap.tileKey(regionX, regionZ));
    }

    int regionHash(String askedDimension, int regionX, int regionZ) {
        if (!shows(askedDimension)) {
            return 0;
        }
        long region = ChunkKey.of(regionX, regionZ);
        return 31 * (31 * labelVersion + regionVersions.getOrDefault(region, 0)) + landPerRegion.getOrDefault(region, 0);
    }

    UUID owner(String askedDimension, int chunkX, int chunkZ) {
        if (!shows(askedDimension)) {
            return null;
        }
        UUID claimed = owners.get(ChunkKey.of(chunkX, chunkZ));
        if (claimed != null) {
            return claimed;
        }
        ClientProvinceMap.View view = provinces;
        return askedDimension.equals(view.dimension()) ? view.ownerAt(chunkX, chunkZ) : null;
    }

    /**
     * Colors of a chunk for Xaero's highlighters: the fill in the middle, then the top, right, bottom and left edges,
     * each as blue, green, red and alpha bytes from the highest. An edge facing land of another owner is a border.
     *
     * @return {@code false} if the chunk is not land of a kingdom
     */
    boolean colors(String askedDimension, int chunkX, int chunkZ, int[] result) {
        UUID owner = owner(askedDimension, chunkX, chunkZ);
        if (owner == null) {
            return false;
        }
        KingdomMapInfo info = kingdoms.get(owner);
        int rgb = info == null ? 0x808080 : info.color();
        int fill = (int) Math.round(Math.max(0.0, Math.min(1.0, KingdomWorldConfig.CLIENT.mapFillOpacity.get())) * 255);
        Long capital = capitals.get(owner);
        if (capital != null && capital == ChunkKey.of(chunkX, chunkZ) || city(askedDimension, chunkX, chunkZ) != null) {
            fill = Math.min(0xC0, fill * 2 + 0x30);
        }
        int center = color(rgb, fill);
        int border = color(rgb, BORDER_ALPHA);
        result[0] = center;
        result[1] = owner.equals(owner(askedDimension, chunkX, chunkZ - 1)) ? center : border;
        result[2] = owner.equals(owner(askedDimension, chunkX + 1, chunkZ)) ? center : border;
        result[3] = owner.equals(owner(askedDimension, chunkX, chunkZ + 1)) ? center : border;
        result[4] = owner.equals(owner(askedDimension, chunkX - 1, chunkZ)) ? center : border;
        return true;
    }

    private static int color(int rgb, int alpha) {
        return (rgb & 0xFF) << 24 | ((rgb >> 8) & 0xFF) << 16 | ((rgb >> 16) & 0xFF) << 8 | (alpha & 0xFF);
    }

    /**
     * The short text when the pointer rests on land: the kingdom, or its capital.
     */
    Component shortText(String askedDimension, int chunkX, int chunkZ) {
        UUID owner = owner(askedDimension, chunkX, chunkZ);
        KingdomMapInfo info = owner == null ? null : kingdoms.get(owner);
        if (info == null) {
            return null;
        }
        Long capital = capitals.get(owner);
        if (capital != null && capital == ChunkKey.of(chunkX, chunkZ)) {
            return MapLabels.capital(info);
        }
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city = city(askedDimension, chunkX, chunkZ);
        return city != null ? Component.translatable("kingdomworld.screen.map.city", city.name()) : Component.literal(info.name());
    }

    /**
     * The city whose town hall stands in a chunk, if any; Xaero's maps show it like a capital.
     */
    private static io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city(String askedDimension, int chunkX, int chunkZ) {
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (city.id() != 0 && !city.capitalOfPlan() && city.dimension().equals(askedDimension) && city.x() >> 4 == chunkX
                    && city.z() >> 4 == chunkZ) {
                return city;
            }
        }
        return null;
    }

    /**
     * The full text: the kingdom (or its capital), its ruler and its currency.
     */
    Component longText(String askedDimension, int chunkX, int chunkZ) {
        Component name = shortText(askedDimension, chunkX, chunkZ);
        if (name == null) {
            return null;
        }
        KingdomMapInfo info = kingdoms.get(owners.get(ChunkKey.of(chunkX, chunkZ)));
        MutableComponent text = name.copy();
        if (info != null) {
            text.append(" (").append(MapLabels.ruler(info)).append("; ")
                    .append(Component.translatable("kingdomworld.map.currency", MapLabels.currency(info))).append(")");
        }
        return text;
    }

    /**
     * Lines for the minimap's information display.
     */
    List<Component> lines(String askedDimension, int chunkX, int chunkZ) {
        Component name = shortText(askedDimension, chunkX, chunkZ);
        if (name == null) {
            return List.of();
        }
        KingdomMapInfo info = kingdoms.get(owners.get(ChunkKey.of(chunkX, chunkZ)));
        return info == null ? List.of(name) : List.of(name, MapLabels.ruler(info));
    }

    String dimensionId() {
        return dimension;
    }

    int landChunks() {
        return owners.size();
    }
}
