package io.github.sekerator.kingdomworld.core.land;

/**
 * Tells the class of a cell of the survey, or of a probed chunk, from what the generator of the world says of a few points of
 * it: the kind of biome at each point (a {@link Probe}) and the height of the ground at the middle. Nothing is generated for it.
 *
 * <p>A cell is looked at in four points. The sea needs most of them; a river or the shore needs one, so that a narrow river and
 * the edge of the sea are not lost between the points. Ground at the level of the sea or below without the sea or a river around
 * is a lake. Mountains, swamps, deserts and hills need two points each. Afterwards, cells far higher or lower than a neighbour are
 * steep whatever their biome ({@link #steepen}).
 */
public final class SurveyClassifier {
    /** How much higher or lower than a neighbour the ground of a cell may lie before it is steep. */
    public static final int STEEP_STEP = 24;

    /**
     * What a point of the land is, by its biome.
     */
    public enum Probe {
        OCEAN, RIVER, BEACH, SWAMP, MOUNTAIN, HILL, DESERT, LAND
    }

    private SurveyClassifier() {
    }

    /**
     * The class of a cell.
     *
     * @param probes   the points of the cell (four of them), a missing one taken for open land
     * @param floor    the height of the ground at the middle of the cell
     * @param seaLevel the level of the sea of the world
     */
    public static TerrainClass cell(Probe[] probes, int floor, int seaLevel) {
        int ocean = 0;
        int river = 0;
        int beach = 0;
        int swamp = 0;
        int mountain = 0;
        int hill = 0;
        int desert = 0;
        for (Probe probe : probes) {
            if (probe == null) {
                continue;
            }
            switch (probe) {
                case OCEAN -> ocean++;
                case RIVER -> river++;
                case BEACH -> beach++;
                case SWAMP -> swamp++;
                case MOUNTAIN -> mountain++;
                case HILL -> hill++;
                case DESERT -> desert++;
                case LAND -> {
                }
            }
        }
        if (ocean >= 3) {
            return TerrainClass.OCEAN;
        }
        if (river > 0) {
            return TerrainClass.RIVER;
        }
        if (ocean > 0 || beach > 0) {
            return TerrainClass.BEACH;
        }
        if (floor <= seaLevel) {
            return TerrainClass.LAKE;
        }
        if (mountain >= 2) {
            return TerrainClass.STEEP;
        }
        if (swamp >= 2) {
            return TerrainClass.SWAMP;
        }
        if (desert >= 2) {
            return TerrainClass.DESERT;
        }
        // A point in the mountains is at least a hill.
        if (hill + mountain >= 2) {
            return TerrainClass.HILLS;
        }
        return TerrainClass.LAND;
    }

    /**
     * The class of a chunk probed in its middle, for a border drawn along the water: the sea, a river, the shore, a lake, a swamp
     * or land.
     *
     * @param floor    the height of the ground at the middle of the chunk
     * @param seaLevel the level of the sea of the world
     */
    public static TerrainClass chunk(Probe probe, int floor, int seaLevel) {
        if (probe == Probe.OCEAN) {
            return TerrainClass.OCEAN;
        }
        if (probe == Probe.RIVER) {
            return TerrainClass.RIVER;
        }
        if (probe == Probe.BEACH) {
            return TerrainClass.BEACH;
        }
        if (floor <= seaLevel) {
            return TerrainClass.LAKE;
        }
        if (probe == Probe.SWAMP) {
            return TerrainClass.SWAMP;
        }
        return TerrainClass.LAND;
    }

    /**
     * Makes steep the cells of open land, hills, desert or swamp whose ground lies more than {@code threshold} higher or lower
     * than that of a neighbour on one of their four sides, a neighbour the survey looked at that is not open water (the floor of
     * the sea or of a lake lies under the water, not at the foot of a cliff). Only the heights decide: a cell made steep here does
     * not count as steep for its neighbours.
     *
     * @param classes the classes of the cells ({@link TerrainClass#id()}), changed in place
     * @param heights the heights of the ground of the cells
     */
    public static void steepen(byte[] classes, short[] heights, int width, int height, int threshold) {
        if (width < 1 || height < 1 || classes.length != width * height || heights.length != width * height) {
            throw new IllegalArgumentException("A class and a height for each of the " + width + " by " + height + " cells");
        }
        byte[] original = classes.clone();
        for (int cz = 0; cz < height; cz++) {
            for (int cx = 0; cx < width; cx++) {
                int cell = cz * width + cx;
                if (!steepens(TerrainClass.byId(original[cell]))) {
                    continue;
                }
                if (differs(original, heights, cell, cx > 0 ? cell - 1 : -1, threshold)
                        || differs(original, heights, cell, cx < width - 1 ? cell + 1 : -1, threshold)
                        || differs(original, heights, cell, cz > 0 ? cell - width : -1, threshold)
                        || differs(original, heights, cell, cz < height - 1 ? cell + width : -1, threshold)) {
                    classes[cell] = TerrainClass.STEEP.id();
                }
            }
        }
    }

    private static boolean steepens(TerrainClass terrain) {
        return terrain == TerrainClass.LAND || terrain == TerrainClass.HILLS || terrain == TerrainClass.DESERT
                || terrain == TerrainClass.SWAMP;
    }

    private static boolean differs(byte[] classes, short[] heights, int cell, int neighbour, int threshold) {
        return neighbour >= 0 && TerrainClass.byId(classes[neighbour]).dry()
                && Math.abs(heights[cell] - heights[neighbour]) > threshold;
    }
}
