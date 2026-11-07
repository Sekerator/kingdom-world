package io.github.sekerator.kingdomworld.compat.bluemap;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import com.mojang.logging.LogUtils;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import io.github.sekerator.kingdomworld.compat.maps.MapIcons;
import io.github.sekerator.kingdomworld.compat.maps.MapTexts;
import io.github.sekerator.kingdomworld.compat.maps.WebMapModel;
import io.github.sekerator.kingdomworld.compat.maps.WebMapSink;
import io.github.sekerator.kingdomworld.compat.maps.WebMaps;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.MapZone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kingdoms on BlueMap, through the official {@code BlueMapAPI}: on every map of a dimension the marker set
 * "Kingdoms" holds the land of each kingdom as shape markers along its outline (holes included) with its name over
 * the land and its capital as a point with a crown, the set "Caravans" the caravans on the way with their route, and
 * the sets of the war layers their zones once wars create any. BlueMap does not keep markers of other mods, so they
 * are made again whenever BlueMap starts or reloads.
 *
 * <p>This is the only package that names BlueMap classes; BlueMap stays an optional dependency.
 */
public final class BlueMapCompat implements CompatModule, WebMapSink {
    public static final String MOD_ID = "bluemap";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KINGDOMS = KingdomWorld.MOD_ID + "-kingdoms";
    private static final String CARAVANS = KingdomWorld.MOD_ID + "-caravans";
    private static final String ZONES = KingdomWorld.MOD_ID + "-";
    private static final String CAPITAL_ICON = "kingdomworld/capital.png";
    private static final String CARAVAN_ICON = "kingdomworld/caravan.png";

    private volatile BlueMapAPI api;
    private volatile boolean wantsFull;

    @Override
    public void init() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        BlueMapAPI.onEnable(enabled -> {
            api = enabled;
            wantsFull = true;
        });
        BlueMapAPI.onDisable(disabled -> {
            if (api == disabled) {
                api = null;
            }
        });
        WebMaps.addSink(this);
        LOGGER.info("BlueMap {} detected, BlueMap compat module active", version);
    }

    @Override
    public String name() {
        return "BlueMap";
    }

    @Override
    public boolean ready() {
        return api != null && KingdomWorldConfig.SERVER.blueMap.get();
    }

    @Override
    public boolean wantsFull() {
        return wantsFull;
    }

    @Override
    public void stop() {
        wantsFull = true;
    }

    @Override
    public void apply(MinecraftServer server, WebMapModel.Update update, MapTexts texts) {
        BlueMapAPI current = api;
        if (current == null) {
            return;
        }
        Map<String, Collection<BlueMapMap>> maps = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            current.getWorld(level).ifPresent(world -> maps.put(Dimensions.id(level), world.getMaps()));
        }
        int markers = 0;
        for (Map.Entry<String, Collection<BlueMapMap>> entry : maps.entrySet()) {
            for (BlueMapMap map : entry.getValue()) {
                markers += applyTo(map, entry.getKey(), update, texts);
            }
        }
        if (update.full()) {
            wantsFull = false;
            LOGGER.info("KingdomWorld BlueMap markers: {} kingdom(s), {} caravan(s), {} marker(s) on {} map(s)",
                    update.kingdoms().size(), update.caravans().size(), markers, maps.values().stream().mapToInt(Collection::size).sum());
        }
    }

    private int applyTo(BlueMapMap map, String dimension, WebMapModel.Update update, MapTexts texts) {
        Map<String, MarkerSet> sets = map.getMarkerSets();
        if (update.full()) {
            writeIcons(map);
        }
        MarkerSet kingdoms = set(sets, KINGDOMS, texts.text("kingdomworld.map.layer.kingdoms"), 0, update.full());
        for (UUID removed : update.removed()) {
            removePrefix(kingdoms, prefix(removed));
        }
        int markers = 0;
        for (WebMapModel.Kingdom kingdom : update.kingdoms()) {
            removePrefix(kingdoms, prefix(kingdom.id()));
            if (kingdom.dimension().equals(dimension)) {
                markers += addKingdom(map, kingdoms, kingdom, texts);
            }
        }

        MarkerSet caravans = set(sets, CARAVANS, texts.text("kingdomworld.map.layer.caravans"), 1, true);
        for (WebMapModel.Caravan caravan : update.caravans()) {
            if (caravan.dimension().equals(dimension)) {
                addCaravan(map, caravans, caravan);
                markers += 2;
            }
        }
        if (caravans.getMarkers().isEmpty()) {
            sets.remove(CARAVANS);
        }

        for (MapZone.Layer layer : MapZone.Layer.values()) {
            List<WebMapModel.Zone> zones = update.zones().stream()
                    .filter(zone -> zone.layer() == layer && zone.dimension().equals(dimension)).toList();
            String id = ZONES + layer.key();
            if (zones.isEmpty()) {
                // The war layers exist only while there is something to show.
                sets.remove(id);
                continue;
            }
            MarkerSet set = set(sets, id, texts.text("kingdomworld.map.layer." + layer.key()), 2 + layer.ordinal(), true);
            for (WebMapModel.Zone zone : zones) {
                int part = 0;
                for (ChunkOutlines.Area area : zone.areas()) {
                    set.put("zone-" + zone.id() + "-" + part++, shape(area, 64, zone.label(), MapTexts.html(zone.label()), zone.color(), 0.35f));
                    markers++;
                }
            }
        }
        return markers;
    }

    private static MarkerSet set(Map<String, MarkerSet> sets, String id, String label, int sorting, boolean clear) {
        MarkerSet set = sets.get(id);
        if (set == null) {
            set = MarkerSet.builder().label(label).toggleable(true).defaultHidden(false).sorting(sorting).build();
            sets.put(id, set);
        } else {
            set.setLabel(label);
            if (clear) {
                set.getMarkers().clear();
            }
        }
        return set;
    }

    private static String prefix(UUID kingdom) {
        return "kingdom-" + kingdom + "-";
    }

    private static void removePrefix(MarkerSet set, String prefix) {
        set.getMarkers().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private int addKingdom(BlueMapMap map, MarkerSet set, WebMapModel.Kingdom kingdom, MapTexts texts) {
        String prefix = prefix(kingdom.id());
        String detail = kingdom.detailHtml(texts);
        float opacity = KingdomWorldConfig.SERVER.webMapFillOpacity.get().floatValue();
        int markers = 0;
        int y = kingdom.hasCapital() ? kingdom.capitalY() : 64;
        for (int i = 0; i < kingdom.areas().size(); i++) {
            ChunkOutlines.Area area = kingdom.areas().get(i);
            set.put(prefix + "area-" + i, shape(area, y, kingdom.name(), detail, kingdom.color(), opacity));
            markers++;
        }
        if (!kingdom.areas().isEmpty()) {
            ChunkOutlines.Area largest = kingdom.areas().get(0);
            set.put(prefix + "label", HtmlMarker.builder()
                    .label(kingdom.name())
                    .position(largest.anchorX() * 16 + 8.0, y, largest.anchorZ() * 16 + 8.0)
                    .html(MapIcons.labelHtml(kingdom.name(), kingdom.color()))
                    .anchor(new Vector2i(0, kingdom.hasCapital() ? -14 : 0))
                    .listed(false)
                    .build());
            markers++;
        }
        if (kingdom.hasCapital()) {
            set.put(prefix + "capital", POIMarker.builder()
                    .label(texts.text("kingdomworld.map.capital", kingdom.name()))
                    .detail(detail)
                    .position(kingdom.capitalX() + 0.5, kingdom.capitalY() + 1, kingdom.capitalZ() + 0.5)
                    .icon(map.getAssetStorage().getAssetUrl(CAPITAL_ICON), MapIcons.SIZE / 2, MapIcons.SIZE / 2)
                    .build());
            markers++;
        }
        return markers;
    }

    private static ShapeMarker shape(ChunkOutlines.Area area, int y, String label, String detail, int color, float opacity) {
        List<Shape> holes = new ArrayList<>();
        for (ChunkOutlines.Ring hole : area.holes()) {
            holes.add(shape(hole));
        }
        int width = KingdomWorldConfig.SERVER.webMapBorderWidth.get();
        return ShapeMarker.builder()
                .label(label)
                .detail(detail)
                .shape(shape(area.outer()), y)
                .holes(holes.toArray(new Shape[0]))
                .centerPosition()
                .depthTestEnabled(false)
                .lineWidth(width)
                .lineColor(color(color, 0.95f))
                .fillColor(color(color, opacity))
                .build();
    }

    private static Shape shape(ChunkOutlines.Ring ring) {
        List<Vector2d> points = new ArrayList<>(ring.corners().size());
        for (ChunkOutlines.Corner corner : ring.corners()) {
            points.add(new Vector2d(corner.blockX(), corner.blockZ()));
        }
        return new Shape(points);
    }

    private void addCaravan(BlueMapMap map, MarkerSet set, WebMapModel.Caravan caravan) {
        set.put("caravan-" + caravan.id(), POIMarker.builder()
                .label(caravan.label())
                .detail(MapTexts.html(caravan.label()))
                .position(caravan.x(), 64, caravan.z())
                .icon(map.getAssetStorage().getAssetUrl(CARAVAN_ICON), MapIcons.SIZE / 2, MapIcons.SIZE / 2)
                .build());
        set.put("route-" + caravan.id(), LineMarker.builder()
                .label(caravan.label())
                .line(new Line(new Vector3d(caravan.fromX() + 0.5, 64, caravan.fromZ() + 0.5),
                        new Vector3d(caravan.toX() + 0.5, 64, caravan.toZ() + 0.5)))
                .centerPosition()
                .depthTestEnabled(false)
                .lineWidth(2)
                .lineColor(color(caravan.color(), 0.6f))
                .listed(false)
                .build());
    }

    private static Color color(int rgb, float alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    private static void writeIcons(BlueMapMap map) {
        write(map, CAPITAL_ICON, MapIcons.capital());
        write(map, CARAVAN_ICON, MapIcons.caravan());
    }

    private static void write(BlueMapMap map, String name, byte[] png) {
        try (OutputStream out = map.getAssetStorage().writeAsset(name)) {
            out.write(png);
        } catch (IOException e) {
            LOGGER.warn("KingdomWorld BlueMap: could not write the icon {} for map {}: {}", name, map.getId(), e.toString());
        }
    }
}
