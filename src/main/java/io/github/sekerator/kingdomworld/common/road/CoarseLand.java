package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.land.SurveyProbes;
import io.github.sekerator.kingdomworld.core.road.CoarseRoute;
import io.github.sekerator.kingdomworld.core.road.LandSnapshot;
import io.github.sekerator.kingdomworld.core.road.LandGrid;
import io.github.sekerator.kingdomworld.core.road.LandTile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The land of the coarse stage of the planner of roads, a chunk at a time: from the tiles read already, from the height maps of
 * loaded chunks copied on the main thread, and for the rest from probes at the corners of the chunks, a cheap guess from the noise
 * of the generator and the height of the generator itself near the level of the sea or without a guess. Nothing is loaded and
 * nothing generated. The probes of a dimension are kept, {@value #CACHED} at most.
 */
public final class CoarseLand {
    /** Probes kept per dimension. */
    static final int CACHED = 262_144;
    private static final Map<String, Map<Long, Integer>> CACHE = new ConcurrentHashMap<>();

    private CoarseLand() {
    }

    static void reset() {
        CACHE.clear();
    }

    /**
     * The lowest, highest and mean height and the share of water of a loaded chunk, from its height maps; main thread.
     */
    static float[] summary(LevelChunk chunk) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        double sum = 0;
        int water = 0;
        double depth = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                low = Math.min(low, surface);
                high = Math.max(high, surface);
                sum += surface;
                if (surface > floor) {
                    water++;
                    depth += surface - floor;
                }
            }
        }
        return new float[]{(float) (sum / 256), low, high, water / 256f, water == 0 ? 0f : (float) (depth / water)};
    }

    /**
     * A probe of the land at a block: the floor and the surface of the generator, packed; off the main thread.
     */
    static int probe(ServerLevel level, String dimension, int x, int z) {
        Map<Long, Integer> cache = CACHE.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        long key = LandSnapshot.column(x, z);
        Integer known = cache.get(key);
        if (known != null) {
            return known;
        }
        int sea = level.getSeaLevel();
        Integer guess = SurveyProbes.guess(level, x, z);
        int floor;
        int surface;
        if (guess != null && !SurveyProbes.nearSea(guess, sea)) {
            floor = guess;
            surface = guess;
        } else {
            floor = SurveyProbes.floor(level, x, z) - 1;
            surface = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                    level.getChunkSource().randomState()) - 1;
        }
        int packed = pack(floor, Math.max(floor, surface));
        if (cache.size() >= CACHED) {
            cache.clear();
        }
        cache.put(key, packed);
        return packed;
    }

    static int pack(int floor, int surface) {
        return (floor + 2048 & 0xFFF) << 12 | surface + 2048 & 0xFFF;
    }

    static int floorOf(int packed) {
        return (packed >> 12 & 0xFFF) - 2048;
    }

    static int surfaceOf(int packed) {
        return (packed & 0xFFF) - 2048;
    }

    /**
     * A guessed tile of a chunk that is neither loaded nor read: the heights between probes every eight blocks, water where the
     * surface of the generator lies over its floor. The planner shows a road over such land as a guess and reads the land itself
     * before the road is laid. Off the main thread.
     */
    static LandTile guessTile(ServerLevel level, String dimension, LandSnapshot snapshot, int chunkX, int chunkZ) {
        int[][] floor = new int[3][3];
        int[][] surface = new int[3][3];
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                int p = probe(level, dimension, chunkX * 16 + i * 8, chunkZ * 16 + j * 8);
                floor[i][j] = floorOf(p);
                surface[i][j] = surfaceOf(p);
            }
        }
        io.github.sekerator.kingdomworld.core.road.LandColumn[] columns = new io.github.sekerator.kingdomworld.core.road.LandColumn[LandTile.SIZE];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int bed = (int) Math.round(between(floor, x, z));
                int top = (int) Math.round(between(surface, x, z));
                columns[x * 16 + z] = top > bed ? io.github.sekerator.kingdomworld.core.road.LandColumn.water(bed, top)
                        : io.github.sekerator.kingdomworld.core.road.LandColumn.land(bed, 3);
            }
        }
        return new LandTile(chunkX, chunkZ, snapshot.owner(chunkX, chunkZ), true, columns);
    }

    private static double between(int[][] grid, int x, int z) {
        int i = Math.min(1, x / 8);
        int j = Math.min(1, z / 8);
        double tx = (x - i * 8) / 8.0;
        double tz = (z - j * 8) / 8.0;
        return grid[i][j] * (1 - tx) * (1 - tz) + grid[i + 1][j] * tx * (1 - tz) + grid[i][j + 1] * (1 - tx) * tz + grid[i + 1][j + 1] * tx * tz;
    }

    /**
     * The coarse field of a frame of chunks; off the main thread.
     *
     * @param loaded summaries of the loaded chunks of the frame, by chunk key
     */
    static CoarseRoute.Field field(ServerLevel level, String dimension, LandSnapshot snapshot, LandGrid grid, Map<Long, float[]> loaded,
                                   int cx0, int cz0, int cx1, int cz1, UUID kingdomId, java.util.function.BooleanSupplier cancelled) {
        CoarseRoute.Field field = new CoarseRoute.Field(cx0, cz0, cx1 - cx0 + 1, cz1 - cz0 + 1);
        int minY = snapshot.minY();
        for (int cx = cx0; cx <= cx1; cx++) {
            if (cancelled.getAsBoolean()) {
                return field;
            }
            for (int cz = cz0; cz <= cz1; cz++) {
                UUID owner = snapshot.owner(cx, cz);
                boolean foreign = owner != null && !owner.equals(kingdomId);
                boolean outside = !snapshot.withinBorder(cx * 16 + 8, cz * 16 + 8);
                double built = Math.min(1.0, snapshot.framesIn(cx, cz) * 0.25);
                LandTile tile = grid.tile(cx, cz);
                if (tile != null) {
                    double[] s = tile.summary();
                    field.set(cx, cz, s[2], s[0], s[1], s[3], 0, Math.max(built, tile.builtShare()), foreign, outside);
                    continue;
                }
                float[] live = loaded.get(LandSnapshot.chunk(cx, cz));
                if (live != null) {
                    field.set(cx, cz, live[0], live[1], live[2], live[3], live[4], built, foreign, outside);
                    continue;
                }
                double sum = 0;
                double low = Double.POSITIVE_INFINITY;
                double high = Double.NEGATIVE_INFINITY;
                int wet = 0;
                double depth = 0;
                boolean voidLand = true;
                for (int dx = 0; dx <= 16; dx += 16) {
                    for (int dz = 0; dz <= 16; dz += 16) {
                        int p = probe(level, dimension, cx * 16 + dx, cz * 16 + dz);
                        int floor = floorOf(p);
                        int surface = surfaceOf(p);
                        sum += surface;
                        low = Math.min(low, surface);
                        high = Math.max(high, surface);
                        if (surface > floor) {
                            wet++;
                            depth += surface - floor;
                        }
                        voidLand &= floor <= minY + 1;
                    }
                }
                field.set(cx, cz, sum / 4, low, high, wet / 4.0, wet == 0 ? 0 : depth / wet, built, foreign, outside || voidLand);
            }
        }
        return field;
    }
}
