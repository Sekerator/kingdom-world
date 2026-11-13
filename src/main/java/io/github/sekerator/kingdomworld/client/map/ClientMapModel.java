package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory;
import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;
import io.github.sekerator.kingdomworld.core.territory.TerritoryShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outlines of the kingdoms in the player's dimension for map integrations: their claims, built from {@link ClientTerritoryCache},
 * and the provinces they hold, each an area of its own outlined on a thread of its own ({@link ClientProvinceShapes}). Claims are
 * collected and outlined at most twice a second, only for the kingdoms that changed; a province is outlined again only when its
 * chunks change, and when it changes hands only the land of its old and new holder is put together anew. Integrations follow the
 * model through a {@link Listener}. Used on the client main thread.
 */
public final class ClientMapModel {
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final TerritoryShapes SHAPES = new TerritoryShapes();
    private static final ClientProvinceShapes PROVINCES = new ClientProvinceShapes();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, List<Object>> SHOWN_INFO = new HashMap<>();
    /** The areas of every kingdom shown: its claims and its provinces. */
    private static final Map<UUID, List<ChunkOutlines.Area>> LAND = new HashMap<>();
    /** The holder of every province as the land was last put together. */
    private static Map<Integer, UUID> shownOwners = new HashMap<>();
    private static final TerritoryListener CHUNKS = (dimension, chunk, oldOwner, newOwner) -> {
        if (dimension != null && dimension.equals(ClientMapModel.dimension)) {
            SHAPES.change(chunk, oldOwner, newOwner);
        }
    };

    private static boolean listening;
    private static String dimension;
    private static int generation = -1;
    private static Object language;
    private static int ticks;

    private ClientMapModel() {
    }

    /**
     * Follows the model of a map integration.
     */
    public interface Listener {
        /**
         * Land changed.
         *
         * @param dimension the dimension shown, {@code null} when the player left the world
         * @param changed   the new areas of every changed kingdom (its claims and each of its provinces), an empty list when a
         *                  kingdom has no land left
         * @param reset     whether everything was started over: drop all land shown before
         */
        void onLand(String dimension, Map<UUID, List<ChunkOutlines.Area>> changed, boolean reset);

        /**
         * Name, color, ruler, capital or currency of kingdoms changed, or the language did: their labels need to be
         * made again.
         */
        void onKingdoms(Set<UUID> changed);
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
        if (!listening) {
            listening = true;
            ClientTerritoryCache.addClaimListener(CHUNKS);
        }
    }

    public static String dimension() {
        return dimension;
    }

    /**
     * All outlined kingdoms of the dimension: their claims and their provinces.
     */
    public static Map<UUID, List<ChunkOutlines.Area>> shapes() {
        return Collections.unmodifiableMap(LAND);
    }

    /**
     * How the outlining of the provinces went last, for the client self-check.
     */
    public static String provinceStats() {
        return PROVINCES.stats();
    }

    static void tick(Minecraft minecraft) {
        if (LISTENERS.isEmpty() || ++ticks < UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        // The loaded language, not the selected one: labels are made again once the new texts are in place.
        update(Language.getInstance());
    }

    /**
     * Outlines what changed and tells the listeners.
     */
    public static void update(Object currentLanguage) {
        boolean reset = false;
        String shown = ClientTerritoryCache.dimension() != null ? ClientTerritoryCache.dimension() : ClientProvinceMap.dimension();
        if (generation != ClientTerritoryCache.generation() || !Objects.equals(dimension, shown)) {
            generation = ClientTerritoryCache.generation();
            dimension = shown;
            SHAPES.reset(dimension == null ? Map.of() : ClientTerritoryCache.owners());
            SHOWN_INFO.clear();
            LAND.clear();
            shownOwners = new HashMap<>();
            reset = true;
        }
        for (KingdomMapInfo info : ClientTerritoryCache.kingdoms().values()) {
            SHAPES.setAnchor(info.id(), info.hasCapital() ? ChunkKey.of(info.capitalChunkX(), info.capitalChunkZ()) : null);
        }
        Set<UUID> dirty = new LinkedHashSet<>(SHAPES.hasChanges() ? SHAPES.rebuild().keySet() : Set.of());

        // The provinces: outlines made anew, and holders changed since the land was last put together.
        Set<Integer> outlined = PROVINCES.update();
        Map<Integer, UUID> owners = new HashMap<>();
        if (dimension != null && dimension.equals(ClientProvinceMap.dimension())) {
            for (ProvinceSnapshotPacket.Entry entry : ClientProvinceMap.provinces()) {
                UUID owner = ClientProvinceMap.owner(entry.id());
                if (owner != null) {
                    owners.put(entry.id(), owner);
                }
            }
        }
        Set<Integer> ids = new HashSet<>(shownOwners.keySet());
        ids.addAll(owners.keySet());
        for (int id : ids) {
            UUID before = shownOwners.get(id);
            UUID now = owners.get(id);
            if (!Objects.equals(before, now)) {
                if (before != null) {
                    dirty.add(before);
                }
                if (now != null) {
                    dirty.add(now);
                }
            } else if (now != null && outlined.contains(id)) {
                dirty.add(now);
            }
        }
        shownOwners = owners;

        Map<UUID, List<ChunkOutlines.Area>> changed = new LinkedHashMap<>();
        if (!dirty.isEmpty()) {
            Map<UUID, List<Integer>> held = new HashMap<>();
            owners.forEach((id, owner) -> held.computeIfAbsent(owner, key -> new ArrayList<>()).add(id));
            for (UUID kingdom : dirty) {
                List<ChunkOutlines.Area> areas = new ArrayList<>(SHAPES.shape(kingdom));
                for (int id : held.getOrDefault(kingdom, List.of())) {
                    areas.addAll(PROVINCES.areas(id));
                }
                if (areas.isEmpty()) {
                    if (LAND.remove(kingdom) != null || reset) {
                        changed.put(kingdom, List.of());
                    }
                } else {
                    List<ChunkOutlines.Area> land = List.copyOf(areas);
                    LAND.put(kingdom, land);
                    changed.put(kingdom, land);
                }
            }
        }
        if (reset || !changed.isEmpty()) {
            for (Listener listener : LISTENERS) {
                listener.onLand(dimension, changed, reset);
            }
        }

        boolean languageChanged = language != currentLanguage;
        language = currentLanguage;
        Set<UUID> relabel = new LinkedHashSet<>();
        for (UUID kingdom : LAND.keySet()) {
            List<Object> key = infoKey(ClientTerritoryCache.kingdom(kingdom));
            List<Object> old = SHOWN_INFO.put(kingdom, key);
            if ((old != null && !old.equals(key) || languageChanged) && !changed.containsKey(kingdom)) {
                relabel.add(kingdom);
            }
        }
        SHOWN_INFO.keySet().retainAll(LAND.keySet());
        if (!relabel.isEmpty()) {
            for (Listener listener : LISTENERS) {
                listener.onKingdoms(relabel);
            }
        }
    }

    private static List<Object> infoKey(KingdomMapInfo info) {
        if (info == null) {
            return List.of();
        }
        String currency = CurrencyDirectory.get(info.id()).map(CurrencyInfo::name).orElse("");
        return List.of(info.name(), info.color(), info.rulerName(), info.hasCapital(), info.capitalChunkX(), info.capitalChunkZ(),
                currency);
    }

    static void reset() {
        if (dimension != null || !LAND.isEmpty()) {
            Map<UUID, List<ChunkOutlines.Area>> removed = new HashMap<>();
            LAND.keySet().forEach(id -> removed.put(id, List.of()));
            SHAPES.clear();
            SHAPES.rebuild();
            LAND.clear();
            dimension = null;
            for (Listener listener : LISTENERS) {
                listener.onLand(null, removed, true);
            }
        }
        PROVINCES.clear();
        shownOwners = new HashMap<>();
        generation = -1;
        SHOWN_INFO.clear();
    }
}
