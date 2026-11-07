package io.github.sekerator.kingdomworld.compat.maps;

import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.MapZone;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What the web maps of the server (BlueMap, Dynmap) show, as plain values: built on the server thread from the kingdom
 * state by {@link WebMaps} and handed to every web map, which turns it into its markers. Texts are already in the
 * language of the web maps.
 */
public final class WebMapModel {
    private WebMapModel() {
    }

    /**
     * A kingdom with its land.
     *
     * @param color    RGB
     * @param currency the name of its currency
     * @param areas    its land in {@code dimension}, the largest area first
     */
    public record Kingdom(UUID id, String name, int color, String ruler, String currency, String dimension, boolean hasCapital,
                          int capitalX, int capitalY, int capitalZ, List<ChunkOutlines.Area> areas) {
        public Kingdom {
            Objects.requireNonNull(id, "id");
            areas = List.copyOf(areas);
        }

        /**
         * The popup of the kingdom on a web map: name, ruler and currency, as HTML.
         */
        public String detailHtml(MapTexts texts) {
            String rulerLine = ruler.isEmpty() ? texts.text("kingdomworld.map.ruler_none") : texts.text("kingdomworld.map.ruler", ruler);
            return "<b>" + MapTexts.html(name) + "</b><br>" + MapTexts.html(rulerLine) + "<br>"
                    + MapTexts.html(texts.text("kingdomworld.map.currency", currency));
        }

        /**
         * Same name, color, ruler, currency and capital.
         */
        public boolean sameLabel(Kingdom other) {
            return other != null && name.equals(other.name) && color == other.color && ruler.equals(other.ruler)
                    && currency.equals(other.currency) && hasCapital == other.hasCapital && capitalX == other.capitalX
                    && capitalY == other.capitalY && capitalZ == other.capitalZ && dimension.equals(other.dimension);
        }
    }

    /**
     * A caravan where it is now.
     *
     * @param color RGB of the selling kingdom
     * @param way   points of its way through the roads, packed as {@code x << 32 | z}; empty for a straight way
     */
    public record Caravan(long id, String dimension, int color, double x, double z, int fromX, int fromZ, int toX, int toZ,
                          String label, List<Long> way) {
        public Caravan {
            way = List.copyOf(way);
        }

        public Caravan(long id, String dimension, int color, double x, double z, int fromX, int fromZ, int toX, int toZ, String label) {
            this(id, dimension, color, x, z, fromX, fromZ, toX, toZ, label, List.of());
        }

        /**
         * The x of every point of the way, from the start to the goal.
         */
        public double[] wayX() {
            if (way.size() < 2) {
                return new double[]{fromX + 0.5, toX + 0.5};
            }
            return way.stream().mapToDouble(point -> (int) (point >> 32) + 0.5).toArray();
        }

        public double[] wayZ() {
            if (way.size() < 2) {
                return new double[]{fromZ + 0.5, toZ + 0.5};
            }
            return way.stream().mapToDouble(point -> (int) point.longValue() + 0.5).toArray();
        }
    }

    /**
     * A city of a kingdom.
     *
     * @param color RGB of its kingdom
     */
    public record City(long id, String dimension, String name, String kingdom, int color, int x, int z, String label) {
    }

    /**
     * A road as a line of points.
     *
     * @param color RGB of its class
     */
    public record Road(long id, String dimension, int color, String label, double[] xs, double[] zs) {
    }

    /**
     * Land on one of the war layers.
     *
     * @param color RGB
     */
    public record Zone(MapZone.Layer layer, String dimension, String id, int color, String label, List<ChunkOutlines.Area> areas) {
        public Zone {
            areas = List.copyOf(areas);
        }
    }

    /**
     * Changes for the web maps.
     *
     * @param full      whether this is everything: markers shown before are dropped first
     * @param kingdoms  kingdoms to draw (again): all of them in a full update, otherwise the changed ones
     * @param removed   kingdoms whose markers go away
     * @param caravans  all caravans on the way
     * @param zones     all zones of the war layers
     * @param shown     how many kingdoms the web maps show after this update
     */
    public record Update(boolean full, List<Kingdom> kingdoms, Set<UUID> removed, List<Caravan> caravans, List<Zone> zones,
                         int shown, List<City> cities, List<Road> roads) {
        public Update {
            kingdoms = List.copyOf(kingdoms);
            removed = Set.copyOf(removed);
            caravans = List.copyOf(caravans);
            zones = List.copyOf(zones);
            cities = List.copyOf(cities);
            roads = List.copyOf(roads);
        }

        public Update(boolean full, List<Kingdom> kingdoms, Set<UUID> removed, List<Caravan> caravans, List<Zone> zones, int shown) {
            this(full, kingdoms, removed, caravans, zones, shown, List.of(), List.of());
        }

        public boolean isEmpty() {
            return !full && kingdoms.isEmpty() && removed.isEmpty();
        }
    }
}
