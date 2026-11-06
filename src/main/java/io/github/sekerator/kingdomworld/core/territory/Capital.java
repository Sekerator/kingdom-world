package io.github.sekerator.kingdomworld.core.territory;

import java.util.Objects;

/**
 * Where a kingdom's throne stands.
 *
 * @param dimension dimension id, for example {@code minecraft:overworld}
 * @param x         block x of the throne
 * @param y         block y of the throne
 * @param z         block z of the throne
 */
public record Capital(String dimension, int x, int y, int z) {
    public Capital {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isEmpty()) {
            throw new IllegalArgumentException("Empty dimension id");
        }
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public long chunk() {
        return ChunkKey.of(chunkX(), chunkZ());
    }

    public boolean isAt(String otherDimension, int otherX, int otherY, int otherZ) {
        return x == otherX && y == otherY && z == otherZ && dimension.equals(otherDimension);
    }
}
