package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory;
import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;
import io.github.sekerator.kingdomworld.core.territory.TerritoryShapes;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outlines of the kingdoms in the player's dimension, built from {@link ClientTerritoryCache} for map integrations.
 * Chunk changes are collected and outlined at most twice a second, only for the kingdoms that changed; integrations
 * follow the model through a {@link Listener}. Used on the client main thread.
 */
public final class ClientMapModel {
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final TerritoryShapes SHAPES = new TerritoryShapes();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, List<Object>> SHOWN_INFO = new HashMap<>();
    private static final TerritoryListener CHUNKS = (dimension, chunk, oldOwner, newOwner) -> {
        if (dimension != null && dimension.equals(ClientMapModel.dimension)) {
            SHAPES.change(chunk, oldOwner, newOwner);
        }
    };

    private static boolean listening;
    private static String dimension;
    private static int generation = -1;
    private static String language;
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
         * @param changed   the new areas of every changed kingdom, an empty list when a kingdom has no land left
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
            ClientTerritoryCache.addListener(CHUNKS);
        }
    }

    public static String dimension() {
        return dimension;
    }

    /**
     * All outlined kingdoms of the dimension.
     */
    public static Map<UUID, List<ChunkOutlines.Area>> shapes() {
        return SHAPES.shapes();
    }

    static void tick(Minecraft minecraft) {
        if (LISTENERS.isEmpty() || ++ticks < UPDATE_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        update(minecraft.getLanguageManager().getSelected());
    }

    /**
     * Outlines what changed and tells the listeners.
     */
    public static void update(String currentLanguage) {
        boolean reset = false;
        if (generation != ClientTerritoryCache.generation() || !Objects.equals(dimension, ClientTerritoryCache.dimension())) {
            generation = ClientTerritoryCache.generation();
            dimension = ClientTerritoryCache.dimension();
            SHAPES.reset(dimension == null ? Map.of() : ClientTerritoryCache.owners());
            SHOWN_INFO.clear();
            reset = true;
        }
        for (KingdomMapInfo info : ClientTerritoryCache.kingdoms().values()) {
            SHAPES.setAnchor(info.id(), info.hasCapital() ? ChunkKey.of(info.capitalChunkX(), info.capitalChunkZ()) : null);
        }
        Map<UUID, List<ChunkOutlines.Area>> changed = SHAPES.hasChanges() ? SHAPES.rebuild() : Map.of();
        if (reset || !changed.isEmpty()) {
            for (Listener listener : LISTENERS) {
                listener.onLand(dimension, changed, reset);
            }
        }

        boolean languageChanged = !Objects.equals(language, currentLanguage);
        language = currentLanguage;
        Set<UUID> relabel = new LinkedHashSet<>();
        for (UUID kingdom : SHAPES.shapes().keySet()) {
            List<Object> key = infoKey(ClientTerritoryCache.kingdom(kingdom));
            List<Object> old = SHOWN_INFO.put(kingdom, key);
            if ((old != null && !old.equals(key) || languageChanged) && !changed.containsKey(kingdom)) {
                relabel.add(kingdom);
            }
        }
        SHOWN_INFO.keySet().retainAll(SHAPES.shapes().keySet());
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
        if (dimension != null || !SHAPES.shapes().isEmpty()) {
            Map<UUID, List<ChunkOutlines.Area>> removed = new HashMap<>();
            SHAPES.shapes().keySet().forEach(id -> removed.put(id, List.of()));
            SHAPES.clear();
            SHAPES.rebuild();
            dimension = null;
            for (Listener listener : LISTENERS) {
                listener.onLand(null, removed, true);
            }
        }
        generation = -1;
        SHOWN_INFO.clear();
    }
}
