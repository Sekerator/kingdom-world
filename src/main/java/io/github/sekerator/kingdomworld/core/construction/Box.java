package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A box of blocks in the world, with both corners inside it.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("Empty box");
        }
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Box other) {
        return minX <= other.maxX && other.minX <= maxX && minY <= other.maxY && other.minY <= maxY && minZ <= other.maxZ
                && other.minZ <= maxZ;
    }

    /**
     * The chunks the box covers.
     */
    public Set<Long> chunks() {
        Set<Long> chunks = new LinkedHashSet<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                chunks.add(ChunkKey.of(cx, cz));
            }
        }
        return chunks;
    }

    /**
     * Index of a position inside the box, from 0 to the volume.
     */
    public int index(int x, int y, int z) {
        return ((y - minY) * sizeZ() + (z - minZ)) * sizeX() + (x - minX);
    }

    @Override
    public String toString() {
        return minX + " " + minY + " " + minZ + " .. " + maxX + " " + maxY + " " + maxZ;
    }
}
