package io.github.sekerator.kingdomworld.compat.journeymap;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.client.map.ClientMapModel;
import io.github.sekerator.kingdomworld.client.map.ClientMapSync;
import io.github.sekerator.kingdomworld.client.map.ClientMapZones;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.map.ClientTradeCache;
import io.github.sekerator.kingdomworld.client.map.MapLabels;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.MapZone;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.Overlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kingdoms on JourneyMap: the land of each kingdom as polygons along its outline (holes included) in the kingdom's
 * color with a translucent fill, the name over its largest area, the ruler and the currency when the pointer rests on
 * the land, the capital as a crown and caravans as carts that move along their way. Zones of the war layers are drawn
 * as well once wars send any. Overlays are made from {@link ClientMapModel} and redrawn only for kingdoms that changed.
 * Used on the client main thread.
 */
final class JourneyMapLayer implements ClientMapModel.Listener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GROUP = "KingdomWorld";
    private static final ResourceLocation CAPITAL = KingdomWorld.id("textures/map/capital.png");
    private static final ResourceLocation CARAVAN = KingdomWorld.id("textures/map/caravan.png");
    private static final int ICON = 24;
    private static final int CARAVAN_INTERVAL_TICKS = 20;

    private static volatile IClientAPI api;
    private static volatile boolean started;
    private static JourneyMapLayer instance;
    private static boolean switchedOff;

    private final IClientAPI jm;
    private final Map<UUID, List<Overlay>> kingdoms = new HashMap<>();
    private final Map<Long, MarkerOverlay> caravans = new HashMap<>();
    private final Map<Long, MarkerOverlay> cities = new HashMap<>();
    private final List<Overlay> zones = new ArrayList<>();
    private String dimension;
    private int zonesVersion = -1;
    private int ticks;
    private boolean warned;

    private JourneyMapLayer(IClientAPI jm) {
        this.jm = jm;
    }

    /**
     * JourneyMap hands over its API.
     */
    static void attach(IClientAPI clientApi) {
        api = clientApi;
    }

    /**
     * The compat module is active; the layer starts on the first client tick after JourneyMap handed over its API.
     */
    static void start() {
        if (!started) {
            started = true;
            MinecraftForge.EVENT_BUS.addListener(JourneyMapLayer::onClientTick);
        }
    }

    /**
     * Whether the layer runs, for the client self-check.
     */
    static boolean running() {
        return instance != null;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (instance == null) {
            IClientAPI clientApi = api;
            if (clientApi == null || switchedOff) {
                return;
            }
            if (!KingdomWorldConfig.CLIENT.journeyMap.get()) {
                switchedOff = true;
                LOGGER.info("KingdomWorld JourneyMap layer switched off in the client config");
                return;
            }
            instance = new JourneyMapLayer(clientApi);
            ClientMapSync.enable("journeymap", JourneyMapLayer::stats);
            ClientMapModel.addListener(instance);
            ClientEventRegistry.MAPPING_EVENT.subscribe(KingdomWorld.MOD_ID, instance::onMapping);
            LOGGER.info("KingdomWorld JourneyMap layer ready");
        }
        instance.tick();
    }

    private void onMapping(MappingEvent event) {
        // JourneyMap starts mapping a world or a dimension: show everything again in case it dropped overlays.
        if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
            kingdoms.values().forEach(list -> list.forEach(this::show));
            caravans.values().forEach(this::show);
            zones.forEach(this::show);
        }
    }

    // ---------------------------------------------------------------- land

    @Override
    public void onLand(String newDimension, Map<UUID, List<ChunkOutlines.Area>> changed, boolean reset) {
        if (reset) {
            for (UUID id : new HashSet<>(kingdoms.keySet())) {
                removeKingdom(id);
            }
            caravans.values().forEach(this::hide);
            caravans.clear();
            zones.forEach(this::hide);
            zones.clear();
            zonesVersion = -1;
            dimension = newDimension;
        }
        if (dimension == null) {
            return;
        }
        changed.forEach((id, areas) -> {
            removeKingdom(id);
            if (!areas.isEmpty()) {
                showKingdom(id, areas);
            }
        });
    }

    @Override
    public void onKingdoms(Set<UUID> changed) {
        for (UUID id : changed) {
            removeKingdom(id);
            List<ChunkOutlines.Area> areas = ClientMapModel.shapes().get(id);
            if (areas != null && !areas.isEmpty() && dimension != null) {
                showKingdom(id, areas);
            }
        }
    }

    private void removeKingdom(UUID id) {
        List<Overlay> old = kingdoms.remove(id);
        if (old != null) {
            old.forEach(this::hide);
        }
    }

    private void showKingdom(UUID id, List<ChunkOutlines.Area> areas) {
        KingdomMapInfo info = ClientTerritoryCache.kingdom(id);
        if (info == null) {
            return;
        }
        ResourceKey<Level> key = dimensionKey();
        String title = MapLabels.joined(MapLabels.kingdom(info));
        ShapeProperties shape = shape(info.color(), KingdomWorldConfig.CLIENT.mapFillOpacity.get().floatValue());
        List<Overlay> overlays = new ArrayList<>();
        for (int i = 0; i < areas.size(); i++) {
            PolygonOverlay polygon = polygon(key, shape, areas.get(i));
            polygon.setTitle(title);
            if (i == 0) {
                polygon.setLabel(info.name());
                polygon.setTextProperties(label(info.color(), info.hasCapital() && nearCapital(areas.get(i), info)));
            }
            overlays.add(polygon);
        }
        if (info.hasCapital()) {
            BlockPos center = new BlockPos(info.capitalChunkX() * 16 + 8, 64, info.capitalChunkZ() * 16 + 8);
            MarkerOverlay capital = new MarkerOverlay(KingdomWorld.MOD_ID, center, icon(CAPITAL, 18));
            capital.setDimension(key);
            capital.setOverlayGroupName(GROUP);
            // No title of its own: over the crown JourneyMap shows the title of the land, and two would overlap.
            capital.setDisplayOrder(1000);
            overlays.add(capital);
        }
        overlays.forEach(this::show);
        kingdoms.put(id, overlays);
    }

    private static PolygonOverlay polygon(ResourceKey<Level> key, ShapeProperties shape, ChunkOutlines.Area area) {
        List<MapPolygon> holes = new ArrayList<>();
        for (ChunkOutlines.Ring hole : area.holes()) {
            holes.add(polygon(hole));
        }
        PolygonOverlay overlay = new PolygonOverlay(KingdomWorld.MOD_ID, key, shape, polygon(area.outer()), holes);
        overlay.setOverlayGroupName(GROUP);
        overlay.setDisplayOrder(100);
        return overlay;
    }

    private static MapPolygon polygon(ChunkOutlines.Ring ring) {
        List<BlockPos> points = new ArrayList<>(ring.corners().size());
        for (ChunkOutlines.Corner corner : ring.corners()) {
            points.add(new BlockPos(corner.blockX(), 64, corner.blockZ()));
        }
        return new MapPolygon(points);
    }

    private static ShapeProperties shape(int color, float fillOpacity) {
        return new ShapeProperties()
                .setStrokeColor(color)
                .setStrokeOpacity(0.95f)
                .setStrokeWidth(KingdomWorldConfig.CLIENT.mapBorderWidth.get().floatValue())
                .setFillColor(color)
                .setFillOpacity(fillOpacity);
    }

    /**
     * The name of a kingdom: white on a band of its color, under the crown when the capital is where the name stands.
     */
    private static TextProperties label(int color, boolean underCrown) {
        return new TextProperties()
                .setColor(0xFFFFFF)
                .setBackgroundColor(color)
                .setBackgroundOpacity(0.7f)
                .setOpacity(1.0f)
                .setFontShadow(true)
                .setScale(1.25f)
                .setOffsetY(underCrown ? 14 : 0);
    }

    /**
     * Whether JourneyMap writes the name (in the middle of the area's bounds) where the crown of the capital stands.
     */
    private static boolean nearCapital(ChunkOutlines.Area area, KingdomMapInfo info) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ChunkOutlines.Corner corner : area.outer().corners()) {
            minX = Math.min(minX, corner.blockX());
            minZ = Math.min(minZ, corner.blockZ());
            maxX = Math.max(maxX, corner.blockX());
            maxZ = Math.max(maxZ, corner.blockZ());
        }
        double dx = (minX + maxX) / 2.0 - (info.capitalChunkX() * 16 + 8);
        double dz = (minZ + maxZ) / 2.0 - (info.capitalChunkZ() * 16 + 8);
        return Math.abs(dx) < 16 && Math.abs(dz) < 12;
    }

    private static MapImage icon(ResourceLocation texture, int size) {
        return new MapImage(texture, ICON, ICON).setDisplayWidth(size).setDisplayHeight(size).centerAnchors();
    }

    private ResourceKey<Level> dimensionKey() {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return ResourceKey.create(Registries.DIMENSION, id != null ? id : Level.OVERWORLD.location());
    }

    // ---------------------------------------------------------------- caravans and war zones

    private void tick() {
        if (++ticks < CARAVAN_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (dimension == null || minecraft.level == null) {
            return;
        }
        updateCaravans(minecraft.level.getGameTime());
        updateCities();
        if (zonesVersion != ClientMapZones.version()) {
            zonesVersion = ClientMapZones.version();
            updateZones();
        }
    }

    private void updateCaravans(long gameTime) {
        Set<Long> present = new HashSet<>();
        if (KingdomWorldConfig.CLIENT.mapCaravans.get()) {
            ResourceKey<Level> key = dimensionKey();
            for (TradeViews.CaravanInfo caravan : ClientTradeCache.caravans()) {
                if (!caravan.dimension().equals(dimension)) {
                    continue;
                }
                // Along its way through the roads, where the time says it is.
                double[] at = caravan.position(gameTime);
                BlockPos pos = BlockPos.containing(at[0], 64, at[1]);
                MarkerOverlay marker = caravans.get(caravan.id());
                if (marker == null) {
                    marker = new MarkerOverlay(KingdomWorld.MOD_ID, pos, icon(CARAVAN, 16));
                    marker.setDimension(key);
                    marker.setOverlayGroupName(GROUP);
                    marker.setDisplayOrder(1100);
                    caravans.put(caravan.id(), marker);
                } else {
                    marker.setPoint(pos);
                    marker.flagForRerender();
                }
                Component text = MapLabels.caravan(caravan, gameTime);
                marker.setTitle(text.getString());
                show(marker);
                present.add(caravan.id());
            }
        }
        caravans.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            hide(entry.getValue());
            return true;
        });
    }

    /**
     * Cities as small crowns with their name.
     */
    private void updateCities() {
        Set<Long> present = new HashSet<>();
        ResourceKey<Level> key = dimensionKey();
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (!city.dimension().equals(dimension)) {
                continue;
            }
            MarkerOverlay marker = cities.get(city.id());
            if (marker == null) {
                marker = new MarkerOverlay(KingdomWorld.MOD_ID, new BlockPos(city.x(), 64, city.z()), icon(CAPITAL, 12));
                marker.setDimension(key);
                marker.setOverlayGroupName(GROUP);
                marker.setDisplayOrder(1050);
                cities.put(city.id(), marker);
            }
            marker.setTitle(Component.translatable("kingdomworld.screen.map.city", city.name()).getString());
            show(marker);
            present.add(city.id());
        }
        cities.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            hide(entry.getValue());
            return true;
        });
    }

    private void updateZones() {
        zones.forEach(this::hide);
        zones.clear();
        ResourceKey<Level> key = dimensionKey();
        for (MapZone zone : ClientMapZones.zones()) {
            if (!zone.dimension().equals(dimension)) {
                continue;
            }
            int color = zone.kingdoms().isEmpty() || ClientTerritoryCache.kingdom(zone.kingdoms().get(0)) == null
                    ? zone.layer().color() : ClientTerritoryCache.kingdom(zone.kingdoms().get(0)).color();
            String title = Component.translatable("kingdomworld.map.layer." + zone.layer().key()).getString();
            ShapeProperties shape = shape(color, 0.35f);
            for (ChunkOutlines.Area area : ChunkOutlines.of(zone.chunks())) {
                PolygonOverlay overlay = polygon(key, shape, area);
                overlay.setOverlayGroupName(GROUP + " " + zone.layer().key());
                overlay.setTitle(title);
                overlay.setDisplayOrder(200);
                zones.add(overlay);
            }
        }
        zones.forEach(this::show);
    }

    // ---------------------------------------------------------------- JourneyMap calls

    private void show(Overlay overlay) {
        try {
            jm.show(overlay);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("KingdomWorld JourneyMap layer: an overlay was refused: {}", e.toString());
            }
        }
    }

    private void hide(Overlay overlay) {
        try {
            jm.remove(overlay);
        } catch (RuntimeException e) {
            LOGGER.debug("KingdomWorld JourneyMap layer: could not remove an overlay", e);
        }
    }

    /**
     * Counts of what is shown, for the client self-check.
     */
    static String stats() {
        JourneyMapLayer layer = instance;
        if (layer == null) {
            return "not running";
        }
        int polygons = 0;
        for (List<Overlay> list : layer.kingdoms.values()) {
            polygons += list.size();
        }
        return layer.kingdoms.size() + " kingdom(s), " + polygons + " overlay(s), " + layer.caravans.size() + " caravan(s)";
    }
}
