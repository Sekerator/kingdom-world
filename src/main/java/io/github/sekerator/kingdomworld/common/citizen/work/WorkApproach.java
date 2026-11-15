package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.core.citizen.StandSpots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * How a worker comes to the block it works at: it walks to a place on the ground beside the block from which it reaches it
 * ({@link StandSpots}), never to the block itself, which the game would take up to the top of the blocks over it (the crown
 * of a tree). A place is taken only when a whole walk leads there; the place is kept until the worker is there or the walk
 * fails. A worker that stands on leaves first gets down to the ground.
 */
final class WorkApproach {
    /**
     * Where the worker is with its way to the block.
     */
    enum Result {
        /** Within reach of the block, on the ground: the work may begin. */
        ARRIVED,
        /** On its way. */
        WALKING,
        /** No place beside the block that a walk leads to. */
        UNREACHABLE
    }

    /** Places beside a block whose walks are worked out before the block counts as out of reach. */
    private static final int TRIES = 6;
    /** Walks to a place that end short of it before the next place is tried. */
    private static final int RETRIES = 3;
    /**
     * Blocks beyond which a worker first walks towards the block without asking for a whole walk to a place by it: the game cuts
     * long walks short.
     */
    private static final double FAR = 24.0;
    /** Walks towards a far block that bring the worker no nearer before the block counts as out of reach. */
    private static final int FAR_TRIES = 4;
    /** Blocks around a worker on leaves, across and down, within which it looks for ground to get down to. */
    private static final int DOWN_ACROSS = 5;
    private static final int DOWN_DEPTH = 24;
    /** Ticks a worker on leaves with no ground near waits before it looks again. */
    private static final int DOWN_WAIT = 40;
    private static final double SPEED = 0.6;

    private final CitizenEntity body;
    @Nullable
    private BlockPos block;
    private double reachSqr;
    @Nullable
    private Deque<BlockPos> spots;
    private int tried;
    @Nullable
    private BlockPos spot;
    private int retries;
    private double closest;
    private int farTries;
    private boolean unreachable;
    @Nullable
    private BlockPos ground;
    private int wait;

    WorkApproach(CitizenEntity body) {
        this.body = body;
    }

    /**
     * Forgets the way, for a new round of work.
     */
    void reset() {
        block = null;
        spots = null;
        spot = null;
        ground = null;
        tried = 0;
        retries = 0;
        farTries = 0;
        closest = Double.MAX_VALUE;
        unreachable = false;
        wait = 0;
    }

    /**
     * One tick of the way to a block; paths come from the {@link PathBudget}.
     *
     * @param reachSqr the square of the reach of the work, from the feet to the middle of the block
     */
    Result toward(ServerLevel level, BlockPos target, double reachSqr) {
        if (!target.equals(block) || reachSqr != this.reachSqr) {
            reset();
            block = target.immutable();
            this.reachSqr = reachSqr;
        }
        PathNavigation navigation = body.getNavigation();
        boolean onLeaves = onLeaves(level);
        if (!onLeaves && body.onGround() && body.distanceToSqr(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5) <= reachSqr) {
            navigation.stop();
            ground = null;
            return Result.ARRIVED;
        }
        if (unreachable) {
            return Result.UNREACHABLE;
        }
        if (onLeaves && (spot == null || navigation.isDone())) {
            getDown(level);
            return Result.WALKING;
        }
        ground = null;
        if (spots == null) {
            spots = new ArrayDeque<>();
            for (StandSpots.Spot found : StandSpots.around(block.getX(), block.getY(), block.getZ(), reachSqr, body.getX(), body.getY(),
                    body.getZ(), (x, y, z) -> footing(level, new BlockPos(x, y, z)))) {
                spots.add(new BlockPos(found.x(), found.y(), found.z()));
            }
        }
        if (spot != null) {
            if (!footing(level, spot).standable()) {
                spot = null;
            } else if (!navigation.isDone()) {
                return Result.WALKING;
            } else if (++retries > RETRIES) {
                spot = null;
            } else {
                if (PathBudget.tryAcquire()) {
                    Path path = navigation.createPath(spot, 0);
                    if (path != null && path.canReach()) {
                        navigation.moveTo(path, SPEED);
                    } else {
                        spot = null;
                    }
                }
                return Result.WALKING;
            }
        }
        double distance = Math.sqrt(body.distanceToSqr(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5));
        if (distance > FAR) {
            return closeIn(navigation, distance);
        }
        if (spots.isEmpty() || tried >= TRIES) {
            unreachable = true;
            navigation.stop();
            return Result.UNREACHABLE;
        }
        if (!PathBudget.tryAcquire()) {
            return Result.WALKING;
        }
        BlockPos candidate = spots.poll();
        tried++;
        Path path = navigation.createPath(candidate, 0);
        BlockPos reached = reached(level, path, candidate);
        if (reached != null) {
            spot = reached;
            retries = 0;
            navigation.moveTo(path, SPEED);
        }
        return Result.WALKING;
    }

    /**
     * Walks towards a far block, as far as the game works a walk out, while that brings the worker nearer.
     */
    private Result closeIn(PathNavigation navigation, double distance) {
        if (!navigation.isDone()) {
            return Result.WALKING;
        }
        if (distance < closest - 1.0) {
            closest = distance;
            farTries = 0;
        } else if (++farTries > FAR_TRIES) {
            unreachable = true;
            return Result.UNREACHABLE;
        }
        if (PathBudget.tryAcquire()) {
            BlockPos toward = spots == null || spots.isEmpty() ? block : spots.peek();
            Path path = navigation.createPath(toward, 1);
            if (path != null) {
                navigation.moveTo(path, SPEED);
            }
        }
        return Result.WALKING;
    }

    /**
     * Where a walk to a place leads the worker: the place if it gets there, otherwise the end of the walk if that is a place to
     * stand within reach of the block too; {@code null} if it leads to no such place.
     */
    @Nullable
    private BlockPos reached(ServerLevel level, @Nullable Path path, BlockPos candidate) {
        if (path == null) {
            return null;
        }
        if (path.canReach()) {
            return candidate;
        }
        if (path.getEndNode() == null) {
            return null;
        }
        BlockPos end = path.getEndNode().asBlockPos();
        return StandSpots.distanceSqr(end.getX(), end.getY(), end.getZ(), block.getX(), block.getY(), block.getZ())
                <= StandSpots.chosenReachSqr(reachSqr) && footing(level, end).standable() ? end : null;
    }

    /**
     * Gets a worker that stands on leaves down to the nearest ground: it walks there if a walk leads there, and otherwise climbs
     * down at once, as a body caught on a crown it cannot walk off.
     */
    private void getDown(ServerLevel level) {
        PathNavigation navigation = body.getNavigation();
        if (ground != null && footing(level, ground).standable()) {
            if (navigation.isDone()) {
                body.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
                ground = null;
            }
            return;
        }
        ground = null;
        if (wait > 0) {
            wait--;
            return;
        }
        if (!PathBudget.tryAcquire()) {
            return;
        }
        List<BlockPos> below = groundBelow(level);
        if (below.isEmpty()) {
            // Leaves over water or a drop: the worker looks again in a while.
            wait = DOWN_WAIT;
            return;
        }
        for (BlockPos candidate : below.subList(0, Math.min(3, below.size()))) {
            Path path = navigation.createPath(candidate, 0);
            if (path != null && path.canReach()) {
                navigation.moveTo(path, SPEED);
                ground = candidate;
                return;
            }
        }
        navigation.stop();
        BlockPos nearest = below.get(0);
        body.teleportTo(nearest.getX() + 0.5, nearest.getY(), nearest.getZ() + 0.5);
    }

    /**
     * Places on the ground around and below the worker, the nearest first.
     */
    private List<BlockPos> groundBelow(ServerLevel level) {
        BlockPos at = body.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -DOWN_ACROSS; dx <= DOWN_ACROSS; dx++) {
            for (int dz = -DOWN_ACROSS; dz <= DOWN_ACROSS; dz++) {
                for (int dy = -DOWN_DEPTH; dy <= 1; dy++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    if (footing(level, pos).standable()) {
                        found.add(pos);
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.getX(), body.getY(), body.getZ())));
        return found;
    }

    private boolean onLeaves(ServerLevel level) {
        return level.getBlockState(body.getOnPos()).is(BlockTags.LEAVES);
    }

    /**
     * What a place offers the feet of a worker; nothing in a place that is not loaded.
     */
    static StandSpots.Footing footing(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet) || !level.isInWorldBounds(feet)) {
            return new StandSpots.Footing(false, false, false, false, false);
        }
        BlockPos head = feet.above();
        BlockPos floor = feet.below();
        BlockState under = level.getBlockState(floor);
        return new StandSpots.Footing(
                level.getBlockState(feet).getCollisionShape(level, feet).isEmpty(),
                level.getBlockState(head).getCollisionShape(level, head).isEmpty(),
                !level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty(),
                !under.getCollisionShape(level, floor).isEmpty(),
                under.is(BlockTags.LEAVES));
    }
}
