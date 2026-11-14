package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the planner of a road reads besides the blocks of the land, copied on the main thread before the work is handed over:
 * whose land each chunk is, the frames of buildings and constructions, the columns of every road of the dimension, the walls
 * and their gates, and the border of the world.
 */
public final class LandSnapshot {
    /** The kingdom that plans: its roads and walls are its own. */
    private final UUID kingdomId;
    private final Map<Long, UUID> owners;
    /** Frames widened by a block, by the chunks they touch: minX, minZ, maxX, maxZ. */
    private final Map<Long, List<int[]>> frames = new HashMap<>();
    /** Roads by column: the road, its height and whether it is the planner's own. */
    private final Map<Long, Long> roads;
    /** Walls by column: the floor of the wall and the flag ({@link LandColumn#WALL}, {@link LandColumn#GATE} or
     * {@link LandColumn#FOREIGN_WALL}). */
    private final Map<Long, Integer> walls;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int minY;
    private final int maxY;
    private final int sea;

    /**
     * @param owners  the owner of every chunk owned by somebody, by chunk key ({@link #chunk})
     * @param frames  frames of buildings and constructions: minX, minZ, maxX, maxZ, not yet widened
     * @param roads   columns of roads: {@link #road} values by column key ({@link #column})
     * @param walls   columns of walls: {@link #wall} values by column key
     * @param border  the border of the world: minX, minZ, maxX, maxZ
     * @param heights the bottom and the top of the world and the level of the sea ({@link LandColumn#NONE} for none)
     */
    public LandSnapshot(UUID kingdomId, Map<Long, UUID> owners, List<int[]> frames, Map<Long, Long> roads, Map<Long, Integer> walls,
                        int[] border, int[] heights) {
        this.kingdomId = kingdomId;
        this.owners = Map.copyOf(owners);
        this.roads = Map.copyOf(roads);
        this.walls = Map.copyOf(walls);
        for (int[] frame : frames) {
            int[] widened = {frame[0] - 1, frame[1] - 1, frame[2] + 1, frame[3] + 1};
            for (int cx = widened[0] >> 4; cx <= widened[2] >> 4; cx++) {
                for (int cz = widened[1] >> 4; cz <= widened[3] >> 4; cz++) {
                    this.frames.computeIfAbsent(chunk(cx, cz), key -> new ArrayList<>()).add(widened);
                }
            }
        }
        this.minX = border[0];
        this.minZ = border[1];
        this.maxX = border[2];
        this.maxZ = border[3];
        this.minY = heights[0];
        this.maxY = heights[1];
        this.sea = heights[2];
    }

    /**
     * An empty snapshot of a world without kingdoms, for tests.
     */
    public static LandSnapshot empty(UUID kingdomId) {
        return new LandSnapshot(kingdomId, Map.of(), List.of(), Map.of(), Map.of(), new int[]{-30_000_000, -30_000_000, 30_000_000, 30_000_000},
                new int[]{-64, 320, 63});
    }

    public static long chunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFF_FFFFL);
    }

    public static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /**
     * The value of a column of a road.
     */
    public static long road(long roadId, int y, boolean own) {
        return roadId << 20 | (long) (y + 2048 & 0xFFF) << 1 | (own ? 1 : 0);
    }

    /**
     * The value of a column of a wall.
     */
    public static int wall(int floor, int flag) {
        return (floor + 2048 & 0xFFF) << 12 | flag & 0xFFF;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int sea() {
        return sea;
    }

    /**
     * The kingdom whose land a chunk is, {@code null} for nobody's.
     */
    public UUID owner(int chunkX, int chunkZ) {
        return owners.get(chunk(chunkX, chunkZ));
    }

    public boolean withinBorder(int x, int z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    /**
     * Whether a column lies in the frame of a building or a construction, widened by a block.
     */
    public boolean inFrame(int x, int z) {
        List<int[]> list = frames.get(chunk(x >> 4, z >> 4));
        if (list == null) {
            return false;
        }
        for (int[] frame : list) {
            if (x >= frame[0] && x <= frame[2] && z >= frame[1] && z <= frame[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a chunk has the frame of a building or a construction in it.
     */
    public int framesIn(int chunkX, int chunkZ) {
        List<int[]> list = frames.get(chunk(chunkX, chunkZ));
        return list == null ? 0 : list.size();
    }

    /**
     * The road on a column: the {@link #road} value, or -1 for none.
     */
    public long roadAt(int x, int z) {
        Long value = roads.get(column(x, z));
        return value == null ? -1 : value;
    }

    public static long roadId(long value) {
        return value >>> 20;
    }

    public static int roadY(long value) {
        return (int) (value >> 1 & 0xFFF) - 2048;
    }

    public static boolean ownRoad(long value) {
        return (value & 1) != 0;
    }

    /**
     * The wall on a column: the {@link #wall} value, or -1 for none.
     */
    public int wallAt(int x, int z) {
        Integer value = walls.get(column(x, z));
        return value == null ? -1 : value;
    }

    public static int wallFloor(int value) {
        return (value >> 12 & 0xFFF) - 2048;
    }

    public static int wallFlag(int value) {
        return value & 0xFFF;
    }

    /**
     * A column of a tile with what the snapshot knows of it laid over: the border, frames, walls and roads.
     */
    public LandColumn overlay(LandColumn column, int x, int z) {
        if (!withinBorder(x, z)) {
            return column.withFlags(LandColumn.BORDER);
        }
        LandColumn result = column;
        if (inFrame(x, z)) {
            result = result.withFlags(LandColumn.BUILT);
        }
        int wall = wallAt(x, z);
        long road = roadAt(x, z);
        if (wall >= 0) {
            result = new LandColumn(result.ground(), result.waterTop(), result.soil(), result.logs(),
                    (result.flags() & ~LandColumn.BUILT) | wallFlag(wall), wallFloor(wall), result.naturalTo(), result.caveTop(),
                    result.caveBottom(), result.caveLiquid());
        }
        if (road >= 0) {
            result = result.withRoad(roadY(road), ownRoad(road));
        }
        return result;
    }
}
