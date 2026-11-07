package io.github.sekerator.kingdomworld.core.territory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Land that maps show on a layer of its own, apart from the land of kingdoms: contested land and battle zones of wars.
 * Nothing creates zones yet; wars will, and the map integrations already draw the layers.
 *
 * @param layer     the layer the zone belongs to
 * @param dimension dimension id
 * @param id        stable id of the zone, unique within its layer
 * @param kingdoms  the kingdoms involved, the first one's color is used
 * @param chunks    the chunks of the zone as {@link ChunkKey}s
 */
public record MapZone(Layer layer, String dimension, String id, List<UUID> kingdoms, Set<Long> chunks) {
    public MapZone {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(id, "id");
        kingdoms = List.copyOf(kingdoms);
        chunks = Set.copyOf(chunks);
    }

    /**
     * Layers for zones; each has its own translation key {@code kingdomworld.map.layer.<name>}.
     */
    public enum Layer {
        CONTESTED("contested", 0xE0A030),
        BATTLE("battles", 0xD03030);

        private final String key;
        private final int color;

        Layer(String key, int color) {
            this.key = key;
            this.color = color;
        }

        public String key() {
            return key;
        }

        /**
         * Color of the layer's outlines, RGB.
         */
        public int color() {
            return color;
        }
    }
}
