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
 * Feeds the web maps of the server. Land is outlined from the kingdom state and kept up to date through a territory
 * listener; at most once per {@code webMapUpdateSeconds} the changes (kingdoms with new land or new labels, removed
 * kingdoms, caravans where they are now, war zones) go to every web map that is ready. A web map that starts or
 * reloads gets everything. Runs on the server thread; a failing web map is logged and left out.
 */
public final class WebMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<WebMapSink> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, TerritoryShapes> SHAPES = new HashMap<>();
    private static final Map<UUID, WebMapModel.Kingdom> SHOWN = new LinkedHashMap<>();
    private static final Set<WebMapSink> FAILED = new HashSet<>();

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
        KingdomWorldApi.addTerritoryListener(server, (dimension, chunk, oldOwner, newOwner) ->
                shapes(dimension).change(chunk, oldOwner, newOwner));
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

        List<WebMapModel.Kingdom> changed = new ArrayList<>();
        Set<UUID> removed = new HashSet<>();
        Set<UUID> present = new HashSet<>();
        for (Kingdom kingdom : state.kingdoms().all()) {
            Optional<Territory> territory = state.territory().territory(kingdom.id());
            if (territory.isEmpty() || !TerritorySnapshots.canSee(null, kingdom.id())) {
                continue;
            }
            List<ChunkOutlines.Area> areas = shapes(territory.get().dimension()).shape(kingdom.id());
            if (areas.isEmpty()) {
                continue;
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
                removed.add(id);
            }
        }

        List<WebMapModel.Caravan> caravans = KingdomWorldConfig.SERVER.webMapCaravans.get() ? caravans(state) : List.of();
        List<WebMapModel.Zone> zones = zones(state);
        WebMapModel.Update incremental = new WebMapModel.Update(false, changed, removed, caravans, zones);
        WebMapModel.Update full = new WebMapModel.Update(true, List.copyOf(SHOWN.values()), Set.of(), caravans, zones);
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
            double x = caravan.fromX() + (caravan.toX() - caravan.fromX()) * progress;
            double z = caravan.fromZ() + (caravan.toZ() - caravan.fromZ()) * progress;
            String from = state.kingdoms().get(caravan.seller()).map(Kingdom::name).orElse("?");
            String to = state.kingdoms().get(caravan.buyer()).map(Kingdom::name).orElse("?");
            String label = texts.text("kingdomworld.map.caravan", from, to, (int) Math.round(progress * 100));
            list.add(new WebMapModel.Caravan(caravan.id(), caravan.dimension(), caravan.color(), x, z, caravan.fromX(), caravan.fromZ(),
                    caravan.toX(), caravan.toZ(), label));
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
