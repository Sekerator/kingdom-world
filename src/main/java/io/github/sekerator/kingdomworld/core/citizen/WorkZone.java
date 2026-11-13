package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The zone a work post works in: a box around the post, as wide as the post's radius and as high as the
 * profession needs (miners dig below the post, lumberjacks look up into the crowns). Work happens only where
 * the zone lies on the kingdom's own land.
 */
public record WorkZone(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public WorkZone {
        Objects.requireNonNull(dimension, "dimension");
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Empty zone");
        }
    }

    /**
     * A box around a block.
     *
     * @param below blocks below the post that belong to the zone
     * @param above blocks above the post that belong to the zone
     */
    public static WorkZone around(WorldPos center, int radius, int below, int above) {
        int r = Math.max(0, radius);
        return new WorkZone(center.dimension(), center.x() - r, center.y() - Math.max(0, below), center.z() - r,
                center.x() + r, center.y() + Math.max(0, above), center.z() + r);
    }

    /**
     * The zone of a post with the given profession and radius.
     */
    public static WorkZone of(WorldPos post, String professionId, int radius, int mineLevels) {
        if (Professions.MINER.id().equals(professionId)) {
            // The mine itself follows its plan and the land, not this box.
            return around(post, radius, (Math.max(1, mineLevels) - 1) * GatheringRules.MINE_LEVEL_STEP + 1, 3);
        }
        if (Professions.LUMBERJACK.id().equals(professionId)) {
            return around(post, radius, 6, 32);
        }
        if (Professions.FISHER.id().equals(professionId)) {
            return around(post, radius, 4, 3);
        }
        if (Professions.HERDER.id().equals(professionId)) {
            return around(post, radius, 4, 4);
        }
        if (Professions.CARRIER.id().equals(professionId)) {
            return around(post, radius, 16, 16);
        }
        if (Professions.usesStations(professionId)) {
            return around(post, radius, 3, 3);
        }
        return around(post, radius, 2, 2);
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return this.dimension.equals(dimension) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) width() * height() * depth();
    }

    /**
     * The chunks the zone touches.
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
     * Whether every chunk of the zone passes the test, for example belongs to the post's kingdom.
     */
    public boolean liesWithin(LongPredicate ownChunk) {
        for (long chunk : chunks()) {
            if (!ownChunk.test(chunk)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether at least one chunk of the zone passes the test.
     */
    public boolean touches(LongPredicate ownChunk) {
        for (long chunk : chunks()) {
            if (ownChunk.test(chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The largest radius up to {@code max} whose square around the post lies entirely on the land, or 0 if not
     * even the post's own chunk does.
     */
    public static int largestRadiusWithin(WorldPos post, int max, LongPredicate ownChunk) {
        int best = 0;
        for (int radius = 1; radius <= max; radius++) {
            if (!around(post, radius, 0, 0).liesWithin(ownChunk)) {
                break;
            }
            best = radius;
        }
        return best;
    }
}
