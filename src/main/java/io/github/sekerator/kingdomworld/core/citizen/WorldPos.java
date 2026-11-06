package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;

import java.util.Objects;

/**
 * A block position in a dimension: a bed, a workplace, where a citizen was last seen.
 *
 * @param dimension dimension id, for example {@code minecraft:overworld}
 */
public record WorldPos(String dimension, int x, int y, int z) {
    public WorldPos {
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

    /**
     * Squared distance in blocks, or {@link Double#POSITIVE_INFINITY} for another dimension.
     */
    public double distanceSqr(WorldPos other) {
        if (!dimension.equals(other.dimension)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * {@code "x y z"}, for messages and logs.
     */
    public String coordinates() {
        return x + " " + y + " " + z;
    }
}
