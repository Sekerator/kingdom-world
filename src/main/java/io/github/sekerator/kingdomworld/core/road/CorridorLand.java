package io.github.sekerator.kingdomworld.core.road;

import java.util.Set;
import java.util.UUID;

/**
 * The land of a corridor of the fine search in flat arrays over its frame, read once from the land of a plan: what the search and
 * the profile ask of a column millions of times is a few array reads. Columns of the frame outside the corridor are closed.
 *
 * <p>Besides the columns themselves it keeps, for every column, the highest water within the half width of the road and a block
 * (a cutting or a tunnel never goes below it), and the roughness of the land around it (the mean difference of the ground of the
 * 5 by 5 columns round it), which the search adds to the work of the middle of the road.
 */
public final class CorridorLand {
    /** Flags of a column kept besides those of {@link LandColumn}. */
    static final int OUTSIDE = 1 << 20;
    static final int FOREIGN = 1 << 21;
    static final int CAVE_LIQUID = 1 << 22;
    private static final int NO_WATER = Short.MIN_VALUE;

    private final int x0;
    private final int z0;
    private final int width;
    private final int depth;
    private final short[] ground;
    private final short[] top;
    private final byte[] soil;
    private final short[] logs;
    private final int[] flags;
    private final short[] naturalTo;
    private final short[] caveTop;
    private final short[] caveBottom;
    private final short[] roadY;
    private final int[] roadIds;
    private final short[] waterNear;
    private final float[] rough;
    private final int minY;
    private final int maxY;
    private int guessed;

    /**
     * Reads the corridor.
     *
     * @param chunks the chunks of the corridor
     * @param land   the land of a plan; a column it does not know is closed
     * @param owners the owner of a block, {@code null} for nobody
     * @param halfWidth blocks from the middle of the road to the edge of its verge
     */
    public CorridorLand(Set<Long> chunks, RoadSurvey.Land land, java.util.function.BiFunction<Integer, Integer, UUID> owners, UUID kingdomId,
                        int halfWidth, int minY, int maxY, java.util.function.BiPredicate<Integer, Integer> guessedAt) {
        int cx0 = Integer.MAX_VALUE;
        int cz0 = Integer.MAX_VALUE;
        int cx1 = Integer.MIN_VALUE;
        int cz1 = Integer.MIN_VALUE;
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            cx0 = Math.min(cx0, cx);
            cz0 = Math.min(cz0, cz);
            cx1 = Math.max(cx1, cx);
            cz1 = Math.max(cz1, cz);
        }
        this.x0 = cx0 * 16;
        this.z0 = cz0 * 16;
        this.width = (cx1 - cx0 + 1) * 16;
        this.depth = (cz1 - cz0 + 1) * 16;
        this.minY = minY;
        this.maxY = maxY;
        int n = width * depth;
        ground = new short[n];
        top = new short[n];
        soil = new byte[n];
        logs = new short[n];
        flags = new int[n];
        naturalTo = new short[n];
        caveTop = new short[n];
        caveBottom = new short[n];
        roadY = new short[n];
        roadIds = new int[n];
        waterNear = new short[n];
        rough = new float[n];
        java.util.Arrays.fill(flags, OUTSIDE);
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            boolean guess = guessedAt != null && guessedAt.test(cx, cz);
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = cx * 16 + dx;
                    int z = cz * 16 + dz;
                    LandColumn column = land.at(x, z);
                    if (column == null) {
                        continue;
                    }
                    int i = index(x, z);
                    ground[i] = (short) clamp(column.ground());
                    top[i] = (short) clamp(column.top());
                    soil[i] = (byte) column.soil();
                    logs[i] = (short) Math.min(Short.MAX_VALUE, column.logs());
                    UUID owner = owners.apply(x, z);
                    flags[i] = column.flags() | (owner != null && !owner.equals(kingdomId) ? FOREIGN : 0)
                            | (column.caveLiquid() ? CAVE_LIQUID : 0);
                    naturalTo[i] = (short) clamp(column.naturalTo());
                    caveTop[i] = (short) (column.cave() ? clamp(column.caveTop()) : NO_WATER);
                    caveBottom[i] = (short) (column.cave() ? clamp(column.caveBottom()) : NO_WATER);
                    roadY[i] = (short) (column.roadY() == LandColumn.NONE ? NO_WATER : clamp(column.roadY()));
                    if (guess) {
                        guessed++;
                    }
                }
            }
        }
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                int i = index(x, z);
                int water = NO_WATER;
                for (int dx = -halfWidth - 1; dx <= halfWidth + 1; dx++) {
                    for (int dz = -halfWidth - 1; dz <= halfWidth + 1; dz++) {
                        if (inside(x + dx, z + dz)) {
                            int j = index(x + dx, z + dz);
                            if ((flags[j] & OUTSIDE) == 0 && top[j] > ground[j]) {
                                water = Math.max(water, top[j]);
                            }
                        }
                    }
                }
                waterNear[i] = (short) water;
                if ((flags[i] & OUTSIDE) != 0) {
                    continue;
                }
                double sum = 0;
                int count = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (inside(x + dx, z + dz) && (flags[index(x + dx, z + dz)] & OUTSIDE) == 0) {
                            sum += Math.abs(ground[index(x + dx, z + dz)] - ground[i]);
                            count++;
                        }
                    }
                }
                rough[i] = count == 0 ? 0 : (float) (sum / count);
            }
        }
    }

    /**
     * Gives the columns of roads the number of their road, so that the search knows a foreign road it already stands on.
     */
    public void roads(java.util.function.BiFunction<Integer, Integer, Long> roadAt) {
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                int i = index(x, z);
                if ((flags[i] & LandColumn.ROAD) != 0) {
                    Long id = roadAt.apply(x, z);
                    roadIds[i] = id == null ? 0 : (int) (long) id;
                }
            }
        }
    }

    private int clamp(int y) {
        return Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, y));
    }

    public boolean inside(int x, int z) {
        return x >= x0 && z >= z0 && x < x0 + width && z < z0 + depth;
    }

    int index(int x, int z) {
        return (x - x0) * depth + (z - z0);
    }

    public int x0() {
        return x0;
    }

    public int z0() {
        return z0;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    /**
     * Columns of the corridor that are only guessed.
     */
    public int guessedColumns() {
        return guessed;
    }

    /**
     * Whether nothing may be done on a column: outside the corridor, built over, beyond the border, without ground or in a
     * foreign wall outside its gates.
     */
    public boolean closed(int x, int z) {
        if (!inside(x, z)) {
            return true;
        }
        int f = flags[index(x, z)];
        if ((f & (LandColumn.WALL | LandColumn.GATE | LandColumn.ROAD)) != 0) {
            return (f & (OUTSIDE | LandColumn.BORDER | LandColumn.VOID | LandColumn.FOREIGN_WALL)) != 0;
        }
        return (f & (OUTSIDE | LandColumn.BUILT | LandColumn.BORDER | LandColumn.VOID | LandColumn.FOREIGN_WALL)) != 0;
    }

    public int ground(int x, int z) {
        return ground[index(x, z)];
    }

    public int top(int x, int z) {
        return top[index(x, z)];
    }

    public boolean water(int x, int z) {
        int i = index(x, z);
        return top[i] > ground[i];
    }

    public int flags(int x, int z) {
        return flags[index(x, z)];
    }

    public boolean foreign(int x, int z) {
        return inside(x, z) && (flags[index(x, z)] & FOREIGN) != 0;
    }

    public int soil(int x, int z) {
        return soil[index(x, z)];
    }

    public int logs(int x, int z) {
        return logs[index(x, z)];
    }

    public int naturalTo(int x, int z) {
        return naturalTo[index(x, z)];
    }

    /**
     * The height of the road on a column, or {@link LandColumn#NONE}: also the floor of a wall.
     */
    public int roadY(int x, int z) {
        short y = roadY[index(x, z)];
        return y == NO_WATER ? LandColumn.NONE : y;
    }

    public int roadId(int x, int z) {
        return roadIds[index(x, z)];
    }

    /**
     * The highest water within the half width of the road and a block, or {@link LandColumn#NONE}.
     */
    public int waterNear(int x, int z) {
        short y = waterNear[index(x, z)];
        return y == NO_WATER ? LandColumn.NONE : y;
    }

    public float rough(int x, int z) {
        return rough[index(x, z)];
    }

    /**
     * Blocks of a cave between two heights, both included.
     */
    public int caveWithin(int x, int z, int low, int high) {
        int i = index(x, z);
        if (caveTop[i] == NO_WATER || high < low) {
            return 0;
        }
        return Math.max(0, Math.min(high, caveTop[i]) - Math.max(low, caveBottom[i]) + 1);
    }

    /**
     * Whether a cave with a liquid in it reaches between two heights.
     */
    public boolean liquidWithin(int x, int z, int low, int high) {
        int i = index(x, z);
        return (flags[i] & CAVE_LIQUID) != 0 && caveWithin(x, z, low, high) > 0;
    }

    /**
     * The column as {@link LandColumn}, for the exact work of the profile.
     */
    public LandColumn column(int x, int z) {
        int i = index(x, z);
        int f = flags[i];
        boolean water = top[i] > ground[i];
        return new LandColumn(ground[i], water ? top[i] : LandColumn.NONE, soil[i], logs[i], f & 0xFFFF,
                roadY[i] == NO_WATER ? LandColumn.NONE : roadY[i], naturalTo[i], caveTop[i] == NO_WATER ? LandColumn.NONE : caveTop[i],
                caveBottom[i] == NO_WATER ? LandColumn.NONE : caveBottom[i], (f & CAVE_LIQUID) != 0);
    }
}
