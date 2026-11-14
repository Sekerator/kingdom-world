package io.github.sekerator.kingdomworld.compat.maps;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.MapZones;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.MapZone;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryShapes;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Feeds the web maps of the server. Claims are outlined from the kingdom state and kept up to date through a listener of the
 * claims; every province is one area of its own, outlined from its tiles on a thread of its own whenever the chunks of the
 * provinces changed, and shown in the color of its holder. At most once per {@code webMapUpdateSeconds} the changes (kingdoms with
 * new land, provinces that changed hands or new labels, removed kingdoms, caravans where they are now, war zones) go to every web
 * map that is ready. A web map that starts or reloads gets everything. Runs on the server thread; a failing web map is logged and
 * left out.
 */
public final class WebMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<WebMapSink> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, TerritoryShapes> SHAPES = new HashMap<>();
    private static final Map<UUID, WebMapModel.Kingdom> SHOWN = new LinkedHashMap<>();
    private static final Set<WebMapSink> FAILED = new HashSet<>();
    /** The outlines of the provinces by number, as of {@link #provinceChunks}, and the provinces each kingdom was shown with. */
    private static Map<Integer, List<ChunkOutlines.Area>> provinceAreas = Map.of();
    private static long provinceChunks = -1;
    private static java.util.concurrent.CompletableFuture<Map<Integer, List<ChunkOutlines.Area>>> outlining;
    private static long outliningChunks;
    private static final Map<UUID, List<Integer>> SHOWN_PROVINCES = new HashMap<>();

    private static boolean registered;
    private static MinecraftServer server;
    private static MapTexts texts;
    private static int ticks;

    private WebMaps() {
    }

    /**
     * Adds a web map; the first one starts following the server.
     */
    public static synchronized void addSink(WebMapSink sink) {
        SINKS.add(sink);
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(WebMaps::onServerStarted);
            MinecraftForge.EVENT_BUS.addListener(WebMaps::onServerStopping);
            MinecraftForge.EVENT_BUS.addListener(WebMaps::onServerTick);
        }
    }

    /**
     * The web maps are brought up to date at the next tick rather than at their next turn: a capital moved, and its mark goes
     * with it at once.
     */
    public static void soon() {
        ticks = Integer.MAX_VALUE / 2;
    }

    public static MapTexts texts() {
        return texts;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        texts = MapTexts.load(KingdomWorldConfig.SERVER.webMapLanguage.get());
        SHAPES.clear();
        SHOWN.clear();
        FAILED.clear();
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (String dimension : state.territory().dimensions()) {
            shapes(dimension).reset(state.territory().claimsIn(dimension));
        }
        // The claims alone, chunk by chunk: the provinces are outlined whole.
        state.territory().addListener((dimension, chunk, oldOwner, newOwner) -> shapes(dimension).change(chunk, oldOwner, newOwner));
        provinceAreas = Map.of();
        provinceChunks = -1;
        outlining = null;
        SHOWN_PROVINCES.clear();
        ticks = Integer.MAX_VALUE / 2;
        LOGGER.info("KingdomWorld web maps: {} in language {}", SINKS.stream().map(WebMapSink::name).toList(), texts.language());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        for (WebMapSink sink : SINKS) {
            try {
                sink.stop();
            } catch (Exception | LinkageError e) {
                LOGGER.warn("KingdomWorld web map {} did not stop cleanly: {}", sink.name(), e.toString());
            }
        }
        server = null;
        SHAPES.clear();
        SHOWN.clear();
        SHOWN_PROVINCES.clear();
        provinceAreas = Map.of();
        outlining = null;
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() != server || server == null) {
            return;
        }
        ticks++;
        boolean wantsFull = false;
        boolean anyReady = false;
        for (WebMapSink sink : SINKS) {
            if (!FAILED.contains(sink) && sink.ready()) {
                anyReady = true;
                wantsFull |= sink.wantsFull();
            }
        }
        if (!anyReady || (ticks < KingdomWorldConfig.SERVER.webMapUpdateSeconds.get() * 20 && !wantsFull)) {
            return;
        }
        ticks = 0;
        update();
    }

    private static TerritoryShapes shapes(String dimension) {
        return SHAPES.computeIfAbsent(dimension, key -> new TerritoryShapes());
    }

    private static void update() {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Territory territory : state.territory().territories()) {
            shapes(territory.dimension()).setAnchor(territory.kingdomId(), territory.capital().chunk());
        }
        Set<UUID> landChanged = new HashSet<>();
        for (TerritoryShapes dimension : SHAPES.values()) {
            landChanged.addAll(dimension.rebuild().keySet());
        }
        io.github.sekerator.kingdomworld.core.territory.ProvinceMap provinces = state.territory().provinceMap().orElse(null);
        boolean outlinesChanged = outlineProvinces(provinces);

        List<WebMapModel.Kingdom> changed = new ArrayList<>();
        Set<UUID> removed = new HashSet<>();
        Set<UUID> present = new HashSet<>();
        for (Kingdom kingdom : state.kingdoms().all()) {
            Optional<Territory> territory = state.territory().territory(kingdom.id());
            if (territory.isEmpty() || !TerritorySnapshots.canSee(null, kingdom.id())) {
                continue;
            }
            List<ChunkOutlines.Area> claimed = shapes(territory.get().dimension()).shape(kingdom.id());
            List<Integer> held = provinces != null && provinces.dimension().equals(territory.get().dimension())
                    ? provinces.provincesOf(kingdom.id()) : List.of();
            List<ChunkOutlines.Area> areas = new ArrayList<>(claimed);
            for (int province : held) {
                areas.addAll(provinceAreas.getOrDefault(province, List.of()));
            }
            if (areas.isEmpty()) {
                continue;
            }
            List<Integer> shownProvinces = SHOWN_PROVINCES.put(kingdom.id(), held);
            if (!held.equals(shownProvinces == null ? List.of() : shownProvinces) || outlinesChanged && !held.isEmpty()) {
                landChanged.add(kingdom.id());
            }
            present.add(kingdom.id());
            WebMapModel.Kingdom view = view(state, kingdom, territory.get(), areas);
            WebMapModel.Kingdom old = SHOWN.put(kingdom.id(), view);
            if (old == null || landChanged.contains(kingdom.id()) || !old.sameLabel(view)) {
                changed.add(view);
            }
        }
        for (UUID id : List.copyOf(SHOWN.keySet())) {
            if (!present.contains(id)) {
                SHOWN.remove(id);
                SHOWN_PROVINCES.remove(id);
                removed.add(id);
            }
        }

        List<WebMapModel.Caravan> caravans = KingdomWorldConfig.SERVER.webMapCaravans.get() ? caravans(state) : List.of();
        List<WebMapModel.Zone> zones = zones(state);
        List<WebMapModel.City> cities = cities(state);
        List<WebMapModel.Road> roads = roads();
        WebMapModel.Update incremental = new WebMapModel.Update(false, changed, removed, caravans, zones, SHOWN.size(), cities, roads);
        WebMapModel.Update full = new WebMapModel.Update(true, List.copyOf(SHOWN.values()), Set.of(), caravans, zones, SHOWN.size(), cities,
                roads);
        for (WebMapSink sink : SINKS) {
            if (FAILED.contains(sink) || !sink.ready()) {
                continue;
            }
            try {
                sink.apply(server, sink.wantsFull() ? full : incremental, texts);
            } catch (Exception | LinkageError e) {
                FAILED.add(sink);
                LOGGER.error("KingdomWorld web map {} failed and is switched off until the server restarts", sink.name(), e);
            }
        }
    }

    /**
     * Outlines the provinces anew, on a thread of its own, when their chunks changed; their tiles are copied here first.
     *
     * @return whether new outlines came in since the last update
     */
    private static boolean outlineProvinces(io.github.sekerator.kingdomworld.core.territory.ProvinceMap provinces) {
        boolean changed = false;
        if (outlining != null && outlining.isDone()) {
            try {
                provinceAreas = outlining.join();
                provinceChunks = outliningChunks;
                changed = true;
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException e) {
                LOGGER.error("KingdomWorld web maps could not outline the provinces", e);
                provinceChunks = outliningChunks;
            }
            outlining = null;
        }
        if (provinces == null) {
            if (!provinceAreas.isEmpty()) {
                provinceAreas = Map.of();
                changed = true;
            }
            return changed;
        }
        if (outlining == null && provinces.chunksVersion() != provinceChunks) {
            Map<Long, char[]> tiles = new HashMap<>();
            for (long key : provinces.tileKeys()) {
                tiles.put(key, provinces.tile(key));
            }
            outliningChunks = provinces.chunksVersion();
            outlining = java.util.concurrent.CompletableFuture.supplyAsync(() -> outlines(tiles));
        }
        return changed;
    }

    /**
     * The outlines of every province of some tiles. Any thread.
     */
    static Map<Integer, List<ChunkOutlines.Area>> outlines(Map<Long, char[]> tiles) {
        Map<Integer, List<Long>> chunks = new HashMap<>();
        tiles.forEach((key, tile) -> {
            int baseX = io.github.sekerator.kingdomworld.core.territory.ProvinceMap.tileX(key) * io.github.sekerator.kingdomworld.core.territory.ProvinceMap.TILE;
            int baseZ = io.github.sekerator.kingdomworld.core.territory.ProvinceMap.tileZ(key) * io.github.sekerator.kingdomworld.core.territory.ProvinceMap.TILE;
            for (int i = 0; i < tile.length; i++) {
                if (tile[i] != 0) {
                    chunks.computeIfAbsent((int) tile[i], id -> new ArrayList<>()).add(io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(baseX + (i & 31), baseZ + (i >> 5)));
                }
            }
        });
        Map<Integer, List<ChunkOutlines.Area>> areas = new HashMap<>();
        chunks.forEach((id, list) -> areas.put(id, ChunkOutlines.of(list)));
        return areas;
    }

    private static WebMapModel.Kingdom view(KingdomWorldState state, Kingdom kingdom, Territory territory, List<ChunkOutlines.Area> areas) {
        String currencyName = KingdomWorldApi.treasury(server, kingdom.id()).map(Treasury::currencyName).orElse("");
        String currency = texts.resolve(MoneyTexts.currency(currencyName, kingdom.name()));
        String ruler = PlayerNames.name(server, kingdom.rulerId());
        Capital capital = territory.capital();
        return new WebMapModel.Kingdom(kingdom.id(), kingdom.name(), kingdom.color(), ruler, currency, territory.dimension(), true,
                capital.x(), capital.y(), capital.z(), areas);
    }

    private static List<WebMapModel.Caravan> caravans(KingdomWorldState state) {
        long now = KingdomWorldApi.gameTime(server);
        List<WebMapModel.Caravan> list = new ArrayList<>();
        for (TradeViews.CaravanInfo caravan : TradeWork.caravans(server)) {
            if (!TerritorySnapshots.canSee(null, caravan.seller()) || !TerritorySnapshots.canSee(null, caravan.buyer())) {
                continue;
            }
            double progress = caravan.progress(now);
            double[] at = caravan.position(now);
            String from = state.kingdoms().get(caravan.seller()).map(Kingdom::name).orElse("?");
            String to = state.kingdoms().get(caravan.buyer()).map(Kingdom::name).orElse("?");
            String label = texts.text("kingdomworld.map.caravan", from, to, (int) Math.round(progress * 100));
            list.add(new WebMapModel.Caravan(caravan.id(), caravan.dimension(), caravan.color(), at[0], at[1], caravan.fromX(), caravan.fromZ(),
                    caravan.toX(), caravan.toZ(), label, caravan.way()));
        }
        return list;
    }

    private static List<WebMapModel.City> cities(KingdomWorldState state) {
        List<WebMapModel.City> list = new ArrayList<>();
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.common.road.RoadSync.all(server).cities()) {
            // Capitals (id 0) have their own marker.
            if (city.id() == 0 || city.capitalOfPlan() || !TerritorySnapshots.canSee(null, city.kingdomId())) {
                continue;
            }
            String kingdom = state.kingdoms().get(city.kingdomId()).map(Kingdom::name).orElse("?");
            // Towns of kingdoms of the world are named in the language of the web maps.
            String name = io.github.sekerator.kingdomworld.common.road.RoadSync.localName(city, texts.language());
            list.add(new WebMapModel.City(city.id(), city.dimension(), name, kingdom, city.color(), city.x(), city.z(),
                    texts.text("kingdomworld.map.city", name, kingdom)));
        }
        return list;
    }

    private static List<WebMapModel.Road> roads() {
        List<WebMapModel.Road> list = new ArrayList<>();
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapRoad road
                : io.github.sekerator.kingdomworld.common.road.RoadSync.all(server).roads()) {
            if (!TerritorySnapshots.canSee(null, road.kingdomId())) {
                continue;
            }
            double[] xs = new double[road.xs().length];
            double[] zs = new double[road.zs().length];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = road.xs()[i] + 0.5;
                zs[i] = road.zs()[i] + 0.5;
            }
            int color = switch (road.roadClass()) {
                case PATH -> 0x8B6B3A;
                case GRAVEL -> 0x6E6E6E;
                case PAVED -> 0xE0DCCF;
            };
            String label = texts.text("kingdomworld.map.road", road.id(), texts.resolve(
                    io.github.sekerator.kingdomworld.common.road.RoadTexts.className(road.roadClass())));
            list.add(new WebMapModel.Road(road.id(), road.dimension(), color, label, xs, zs));
        }
        return list;
    }

    private static List<WebMapModel.Zone> zones(KingdomWorldState state) {
        List<WebMapModel.Zone> list = new ArrayList<>();
        for (MapZone zone : MapZones.all(server)) {
            if (zone.chunks().isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            int color = zone.layer().color();
            for (UUID id : zone.kingdoms()) {
                Optional<Kingdom> kingdom = state.kingdoms().get(id);
                kingdom.ifPresent(k -> names.add(k.name()));
            }
            String label = texts.text("kingdomworld.map.layer." + zone.layer().key())
                    + (names.isEmpty() ? "" : ": " + String.join(" / ", names));
            list.add(new WebMapModel.Zone(zone.layer(), zone.dimension(), zone.id(), color, label, ChunkOutlines.of(zone.chunks())));
        }
        return list;
    }
}
