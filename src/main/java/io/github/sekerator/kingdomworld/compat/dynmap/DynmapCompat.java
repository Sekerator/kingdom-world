package io.github.sekerator.kingdomworld.compat.dynmap;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import io.github.sekerator.kingdomworld.compat.maps.MapIcons;
import io.github.sekerator.kingdomworld.compat.maps.MapTexts;
import io.github.sekerator.kingdomworld.compat.maps.WebMapModel;
import io.github.sekerator.kingdomworld.compat.maps.WebMapSink;
import io.github.sekerator.kingdomworld.compat.maps.WebMaps;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.MapZone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.GenericMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kingdoms on Dynmap, through {@code DynmapCommonAPI}: the marker set "Kingdoms" holds the land of each kingdom as
 * area markers along its outline with its name, ruler and currency in the popup, and its capital as a crown; the set
 * "Caravans" the caravans on the way with their route; the sets of the war layers their zones once wars create any.
 * Dynmap areas cannot have holes, so a hole is joined to the outline by a cut of no width: the land around it is
 * filled, the hole is not, and the cut shows as a thin line. Markers are not persistent and are made again when
 * Dynmap starts.
 *
 * <p>This is the only package that names Dynmap classes; Dynmap stays an optional dependency.
 */
public final class DynmapCompat implements CompatModule, WebMapSink {
    public static final String MOD_ID = "dynmap";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KINGDOMS = KingdomWorld.MOD_ID + ".kingdoms";
    private static final String CARAVANS = KingdomWorld.MOD_ID + ".caravans";
    private static final String ZONES = KingdomWorld.MOD_ID + ".";
    private static final String CAPITAL_ICON = KingdomWorld.MOD_ID + "_capital";
    private static final String CARAVAN_ICON = KingdomWorld.MOD_ID + "_caravan";

    private volatile MarkerAPI markers;
    private volatile boolean wantsFull;
    private final Map<UUID, List<GenericMarker>> kingdomMarkers = new HashMap<>();

    @Override
    public void init() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                markers = api.getMarkerAPI();
                wantsFull = true;
            }

            @Override
            public void apiDisabled(DynmapCommonAPI api) {
                markers = null;
            }
        });
        WebMaps.addSink(this);
        LOGGER.info("Dynmap {} detected, Dynmap compat module active", version);
    }

    @Override
    public String name() {
        return "Dynmap";
    }

    @Override
    public boolean ready() {
        return markers != null && KingdomWorldConfig.SERVER.dynmap.get();
    }

    @Override
    public boolean wantsFull() {
        return wantsFull;
    }

    @Override
    public void stop() {
        kingdomMarkers.clear();
        wantsFull = true;
    }

    @Override
    public void apply(MinecraftServer server, WebMapModel.Update update, MapTexts texts) {
        MarkerAPI api = markers;
        if (api == null) {
            return;
        }
        Map<String, String> worlds = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            worlds.put(level.dimension().location().toString(), worldName(level));
        }
        MarkerSet kingdoms = set(api, KINGDOMS, texts.text("kingdomworld.map.layer.kingdoms"), update.full());
        if (update.full()) {
            kingdomMarkers.clear();
        }
        for (UUID removed : update.removed()) {
            delete(kingdomMarkers.remove(removed));
        }
        MarkerIcon capitalIcon = icon(api, CAPITAL_ICON, texts.text("kingdomworld.map.layer.kingdoms"), MapIcons.capital(), "king");
        int count = 0;
        for (WebMapModel.Kingdom kingdom : update.kingdoms()) {
            delete(kingdomMarkers.remove(kingdom.id()));
            String world = worlds.get(kingdom.dimension());
            if (world != null) {
                List<GenericMarker> made = addKingdom(kingdoms, world, kingdom, capitalIcon, texts);
                kingdomMarkers.put(kingdom.id(), made);
                count += made.size();
            }
        }

        MarkerSet caravans = set(api, CARAVANS, texts.text("kingdomworld.map.layer.caravans"), true);
        MarkerIcon caravanIcon = icon(api, CARAVAN_ICON, texts.text("kingdomworld.map.layer.caravans"), MapIcons.caravan(), "cart");
        for (WebMapModel.Caravan caravan : update.caravans()) {
            String world = worlds.get(caravan.dimension());
            if (world == null) {
                continue;
            }
            caravans.createMarker("caravan-" + caravan.id(), MapTexts.html(caravan.label()), true, world, caravan.x(), 64, caravan.z(),
                    caravanIcon, false);
            PolyLineMarker route = caravans.createPolyLineMarker("route-" + caravan.id(), MapTexts.html(caravan.label()), true, world,
                    new double[]{caravan.fromX() + 0.5, caravan.toX() + 0.5}, new double[]{64, 64},
                    new double[]{caravan.fromZ() + 0.5, caravan.toZ() + 0.5}, false);
            if (route != null) {
                route.setLineStyle(2, 0.6, caravan.color());
            }
            count += 2;
        }

        for (MapZone.Layer layer : MapZone.Layer.values()) {
            List<WebMapModel.Zone> zones = update.zones().stream().filter(zone -> zone.layer() == layer).toList();
            String id = ZONES + layer.key();
            if (zones.isEmpty()) {
                MarkerSet old = api.getMarkerSet(id);
                if (old != null) {
                    old.deleteMarkerSet();
                }
                continue;
            }
            MarkerSet set = set(api, id, texts.text("kingdomworld.map.layer." + layer.key()), true);
            for (WebMapModel.Zone zone : zones) {
                String world = worlds.get(zone.dimension());
                int part = 0;
                for (ChunkOutlines.Area area : zone.areas()) {
                    if (world != null) {
                        area(set, "zone-" + zone.id() + "-" + part++, MapTexts.html(zone.label()), world, area, zone.color(), 0.35);
                        count++;
                    }
                }
            }
        }
        if (update.full()) {
            wantsFull = false;
            LOGGER.info("KingdomWorld Dynmap markers: {} kingdom(s), {} caravan(s), {} marker(s)", update.kingdoms().size(),
                    update.caravans().size(), count);
        }
    }

    private static MarkerSet set(MarkerAPI api, String id, String label, boolean clear) {
        MarkerSet set = api.getMarkerSet(id);
        if (set != null && clear) {
            set.deleteMarkerSet();
            set = null;
        }
        if (set == null) {
            set = api.createMarkerSet(id, label, null, false);
        } else {
            set.setMarkerSetLabel(label);
        }
        return set;
    }

    private static MarkerIcon icon(MarkerAPI api, String id, String label, byte[] png, String fallback) {
        MarkerIcon icon = api.getMarkerIcon(id);
        if (icon == null && png.length > 0) {
            icon = api.createMarkerIcon(id, label, new ByteArrayInputStream(png));
        }
        return icon != null ? icon : api.getMarkerIcon(fallback);
    }

    private static void delete(List<GenericMarker> list) {
        if (list != null) {
            list.forEach(GenericMarker::deleteMarker);
        }
    }

    private List<GenericMarker> addKingdom(MarkerSet set, String world, WebMapModel.Kingdom kingdom, MarkerIcon capitalIcon, MapTexts texts) {
        List<GenericMarker> made = new ArrayList<>();
        String popup = kingdom.detailHtml(texts);
        double opacity = KingdomWorldConfig.SERVER.webMapFillOpacity.get();
        String prefix = "kingdom-" + kingdom.id() + "-";
        for (int i = 0; i < kingdom.areas().size(); i++) {
            AreaMarker area = area(set, prefix + "area-" + i, MapTexts.html(kingdom.name()), world, kingdom.areas().get(i), kingdom.color(),
                    opacity);
            if (area != null) {
                area.setDescription(popup);
                made.add(area);
            }
        }
        if (kingdom.hasCapital()) {
            Marker capital = set.createMarker(prefix + "capital", MapTexts.html(texts.text("kingdomworld.map.capital", kingdom.name())), true,
                    world, kingdom.capitalX() + 0.5, kingdom.capitalY() + 1, kingdom.capitalZ() + 0.5, capitalIcon, false);
            if (capital != null) {
                capital.setDescription(popup);
                made.add(capital);
            }
        }
        return made;
    }

    private static AreaMarker area(MarkerSet set, String id, String label, String world, ChunkOutlines.Area area, int color, double opacity) {
        List<double[]> points = withHoles(area);
        double[] x = new double[points.size()];
        double[] z = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            x[i] = points.get(i)[0];
            z[i] = points.get(i)[1];
        }
        AreaMarker marker = set.createAreaMarker(id, label, true, world, x, z, false);
        if (marker != null) {
            marker.setLineStyle(KingdomWorldConfig.SERVER.webMapBorderWidth.get(), 0.95, color);
            marker.setFillStyle(opacity, color);
        }
        return marker;
    }

    /**
     * The outline in block coordinates with every hole joined in through the shortest cut from the outline.
     */
    static List<double[]> withHoles(ChunkOutlines.Area area) {
        List<double[]> ring = points(area.outer());
        for (ChunkOutlines.Ring hole : area.holes()) {
            List<double[]> inner = points(hole);
            int bestOuter = 0;
            int bestInner = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < ring.size(); i++) {
                for (int j = 0; j < inner.size(); j++) {
                    double dx = ring.get(i)[0] - inner.get(j)[0];
                    double dz = ring.get(i)[1] - inner.get(j)[1];
                    double distance = dx * dx + dz * dz;
                    if (distance < best) {
                        best = distance;
                        bestOuter = i;
                        bestInner = j;
                    }
                }
            }
            List<double[]> joined = new ArrayList<>(ring.subList(0, bestOuter + 1));
            for (int k = 0; k <= inner.size(); k++) {
                joined.add(inner.get((bestInner + k) % inner.size()));
            }
            joined.addAll(ring.subList(bestOuter, ring.size()));
            ring = joined;
        }
        return ring;
    }

    private static List<double[]> points(ChunkOutlines.Ring ring) {
        List<double[]> points = new ArrayList<>();
        for (ChunkOutlines.Corner corner : ring.corners()) {
            points.add(new double[]{corner.blockX(), corner.blockZ()});
        }
        return points;
    }

    /**
     * The name Dynmap gives a world on Forge: the save name for the overworld, DIM-1 and DIM1 for the Nether and the
     * End, namespace_path for other dimensions.
     */
    static String worldName(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        if (level.dimension() == Level.OVERWORLD) {
            return level.getServer().getWorldData().getLevelName();
        }
        if (level.dimension() == Level.NETHER) {
            return "DIM-1";
        }
        if (level.dimension() == Level.END) {
            return "DIM1";
        }
        return id.getNamespace() + "_" + id.getPath();
    }
}
