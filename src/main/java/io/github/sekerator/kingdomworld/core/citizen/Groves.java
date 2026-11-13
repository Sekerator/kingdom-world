package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The grove of a lumberjack: how many trees its zone holds, where new saplings go and how many trees it gives a day. Pure
 * rules, shared by the work near players, the look over the zone and the tests.
 *
 * <p>Every tree a lumberjack fells is planted again, and on free natural ground of its zone it plants new saplings on a
 * sparse grid, one on every {@value #SPACING}th block along each side counted from the post, never closer than
 * {@value #MIN_GAP} blocks to a tree or a sapling, until the zone holds one tree or sapling for every {@value #AREA_PER_TREE}
 * blocks of the kingdom's ground in it. Saplings are never touched; a tree that has grown is felled.
 */
public final class Groves {
    /** Blocks between the spots of the grid of new saplings, along each side. */
    public static final int SPACING = 5;
    /** Least distance from a new sapling to a tree or another sapling, in blocks along either side. */
    public static final int MIN_GAP = 4;
    /** Blocks of the kingdom's ground in the zone for each tree or sapling it may hold. */
    public static final int AREA_PER_TREE = 25;
    /** New saplings a lumberjack plants in one round of its work at most. */
    public static final int NEW_PER_ROUND = 4;
    /** Days a sapling needs to grow into a tree, on average. */
    public static final double REGROWTH_DAYS = 1.5;
    /** Trees one lumberjack fells in a working day at most. */
    public static final double MAX_TREES_PER_DAY = 16.0;

    /**
     * A column of the zone: a spot for a sapling, a tree or a sapling standing.
     */
    public record Spot(int x, int z) {
    }

    private Groves() {
    }

    /**
     * Trees and saplings a zone may hold, by the columns of the kingdom's ground in it.
     */
    public static int capacity(int ownColumns) {
        return Math.max(0, ownColumns) / AREA_PER_TREE;
    }

    /**
     * Whether a column lies on the grid of new saplings around a post.
     */
    public static boolean onGrid(int postX, int postZ, int x, int z) {
        return Math.floorMod(x - postX, SPACING) == 0 && Math.floorMod(z - postZ, SPACING) == 0;
    }

    /**
     * New saplings the zone has room for: none once its trees and saplings reach its capacity.
     */
    public static int room(int trees, int saplings, int capacity) {
        return Math.max(0, capacity - Math.max(0, trees) - Math.max(0, saplings));
    }

    /**
     * Spots for new saplings: of the candidates (columns of the grid already found free, natural, under the open sky and
     * far enough from anything built), those at least {@link #MIN_GAP} from every tree, sapling and spot chosen before,
     * the nearest to the post first, no more than the room of the zone and {@code max}.
     *
     * @param stands where trees and saplings stand now
     */
    public static List<Spot> choose(int postX, int postZ, Collection<Spot> candidates, Collection<Spot> stands, int room, int max) {
        int limit = Math.min(Math.max(0, room), Math.max(0, max));
        List<Spot> chosen = new ArrayList<>();
        if (limit == 0) {
            return chosen;
        }
        List<Spot> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingLong((Spot spot) -> distanceSqr(postX, postZ, spot))
                .thenComparingInt(Spot::x).thenComparingInt(Spot::z));
        for (Spot spot : ordered) {
            if (chosen.size() >= limit) {
                break;
            }
            if (isClear(spot, stands) && isClear(spot, chosen)) {
                chosen.add(spot);
            }
        }
        return chosen;
    }

    private static boolean isClear(Spot spot, Collection<Spot> others) {
        for (Spot other : others) {
            if (Math.abs(other.x() - spot.x()) < MIN_GAP && Math.abs(other.z() - spot.z()) < MIN_GAP) {
                return false;
            }
        }
        return true;
    }

    private static long distanceSqr(int x, int z, Spot spot) {
        long dx = (long) spot.x() - x;
        long dz = (long) spot.z() - z;
        return dx * dx + dz * dz;
    }

    /**
     * Trees one lumberjack fells a day in a grove it plants again: every tree, every sapling and every spot for a new
     * sapling (as many as the zone has room for) gives a tree every {@link #REGROWTH_DAYS} days, at most
     * {@link #MAX_TREES_PER_DAY}. The grove never runs out, far from players too.
     *
     * @param newSpots free spots for new saplings found in the zone
     */
    public static double treesPerDay(int trees, int saplings, int newSpots, int capacity) {
        int grove = Math.max(0, trees) + Math.max(0, saplings) + Math.min(Math.max(0, newSpots), room(trees, saplings, capacity));
        return Math.min(MAX_TREES_PER_DAY, grove / REGROWTH_DAYS);
    }
}
