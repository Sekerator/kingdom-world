package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a body stands to work at a block (fell a tree, fill a chest): on the ground near the block, never on the block itself
 * or on leaves, near enough to reach the block, the places nearest to the body first. The game takes a walk that ends in a
 * solid block up to the top of the blocks over it, so that a walk to the trunk of a tree ends on its crown, out of reach of
 * the trunk; a walk to a place on the ground beside the block ends where the work is.
 */
public final class StandSpots {
    /** Blocks east, west, north and south of the block of work within which a place to stand is looked for. */
    public static final int ACROSS = 4;
    /** Blocks below and above the block of work within which the feet of a body may be. */
    public static final int UP_DOWN = 2;
    /** How much nearer than its reach a place to stand lies, so that a body that stops a little short of it still reaches. */
    public static final double MARGIN = 0.5;

    /**
     * A place for the feet of a body.
     */
    public record Spot(int x, int y, int z) {
    }

    /**
     * What a place offers a body.
     *
     * @param feetFree    nothing to bump into where the feet are
     * @param headFree    nothing to bump into where the head is
     * @param wet         water or another fluid at the feet or the head
     * @param floorSolid  something solid under the feet
     * @param floorLeaves the floor is leaves, which bear a body but are no ground to work from
     */
    public record Footing(boolean feetFree, boolean headFree, boolean wet, boolean floorSolid, boolean floorLeaves) {
        /**
         * Whether a body may stand there to work: room for its feet and head, dry, on a solid floor that is not leaves.
         */
        public boolean standable() {
            return feetFree && headFree && !wet && floorSolid && !floorLeaves;
        }
    }

    /**
     * What a world offers at a place.
     */
    @FunctionalInterface
    public interface Ground {
        Footing at(int x, int y, int z);
    }

    private StandSpots() {
    }

    /**
     * The square of the distance from feet at a place, in the middle of its block, to the middle of a block.
     */
    public static double distanceSqr(int x, int y, int z, int blockX, int blockY, int blockZ) {
        double dx = blockX - x;
        double dy = blockY + 0.5 - y;
        double dz = blockZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The reach within which a place to stand is chosen: {@link #MARGIN} short of the reach of the work.
     */
    public static double chosenReachSqr(double reachSqr) {
        double reach = Math.max(0.0, Math.sqrt(reachSqr) - MARGIN);
        return reach * reach;
    }

    /**
     * The places a body may stand at to work at a block: within {@link #ACROSS} blocks of it across and {@link #UP_DOWN} up or
     * down, out of the column of the block itself, within {@link #chosenReachSqr(double) reach} of it and standable; the
     * nearest to the body first, and of those equally near the nearest to the block.
     *
     * @param reachSqr the square of the reach of the work, from the feet to the middle of the block
     */
    public static List<Spot> around(int blockX, int blockY, int blockZ, double reachSqr, double fromX, double fromY, double fromZ,
                                    Ground ground) {
        double within = chosenReachSqr(reachSqr);
        List<Spot> spots = new ArrayList<>();
        for (int dx = -ACROSS; dx <= ACROSS; dx++) {
            for (int dz = -ACROSS; dz <= ACROSS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -UP_DOWN; dy <= UP_DOWN; dy++) {
                    int x = blockX + dx;
                    int y = blockY + dy;
                    int z = blockZ + dz;
                    if (distanceSqr(x, y, z, blockX, blockY, blockZ) <= within && ground.at(x, y, z).standable()) {
                        spots.add(new Spot(x, y, z));
                    }
                }
            }
        }
        spots.sort(Comparator.comparingDouble((Spot spot) -> fromSqr(spot, fromX, fromY, fromZ))
                .thenComparingDouble(spot -> distanceSqr(spot.x(), spot.y(), spot.z(), blockX, blockY, blockZ)));
        return spots;
    }

    private static double fromSqr(Spot spot, double x, double y, double z) {
        double dx = spot.x() + 0.5 - x;
        double dy = spot.y() - y;
        double dz = spot.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
