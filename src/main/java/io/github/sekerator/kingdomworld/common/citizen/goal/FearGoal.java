package io.github.sekerator.kingdomworld.common.citizen.goal;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenFear;
import io.github.sekerator.kingdomworld.core.citizen.Flight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A frightened body runs: above work, the day and strolls, so that nothing calls it back while the fright lasts. It runs
 * home and inside, or to a guard of its kingdom, when the way there does not lead past whoever frightened it; otherwise
 * straight away. At its refuge it waits the fright out. Paths come from the {@link PathBudget} like those of the other goals.
 */
public final class FearGoal extends Goal {
    private static final double SPEED = 0.85;
    /** How near its refuge a body stops, in blocks. */
    private static final double ARRIVED_SQR = 2.5 * 2.5;
    /** How near a guard a body stops, in blocks. */
    private static final double AT_GUARD_SQR = 4.0 * 4.0;
    /** A threat this near a body at its refuge drives it out again, in blocks. */
    private static final double TOO_NEAR_SQR = 5.0 * 5.0;
    private static final int RETHINK_TICKS = 20;
    /** How far a body with no refuge runs at a time, in blocks. */
    private static final int AWAY = 14;

    private final CitizenEntity body;
    private int rethink;

    public FearGoal(CitizenEntity body) {
        this.body = body;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return body.isFrightened() && !body.isSleeping() && CitizenFear.flees(body);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        rethink = 0;
        if (body.takeFearSpread()) {
            CitizenFear.spread(body);
        }
    }

    @Override
    public void stop() {
        body.getNavigation().stop();
        if (!body.isFrightened()) {
            body.calmDown();
        }
    }

    @Override
    public void tick() {
        Vec3 threat = body.threatPosition();
        if (threat == null) {
            return;
        }
        boolean idle = body.getNavigation().isDone();
        if (--rethink > 0 && !idle) {
            return;
        }
        Vec3 refuge = refuge(threat);
        if (refuge != null) {
            double reach = body.fearGuard() != null ? AT_GUARD_SQR : ARRIVED_SQR;
            if (body.distanceToSqr(refuge) <= reach) {
                // There: the body stays and waits the fright out.
                body.getNavigation().stop();
                rethink = RETHINK_TICKS;
                return;
            }
        }
        if (!PathBudget.tryAcquire()) {
            // No path this tick: the next one asks again.
            rethink = 0;
            return;
        }
        rethink = RETHINK_TICKS;
        if (refuge != null && body.getNavigation().moveTo(refuge.x, refuge.y, refuge.z, SPEED)) {
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(body, AWAY, 7, threat);
        if (away == null) {
            double[] point = Flight.away(body.getX(), body.getZ(), threat.x, threat.z, AWAY);
            away = new Vec3(point[0], body.getY(), point[1]);
        }
        body.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
    }

    /**
     * Where the body runs to: the guard it was sent to, its home, or nowhere in particular.
     */
    @Nullable
    private Vec3 refuge(Vec3 threat) {
        if (body.fearGuard() != null && body.level() instanceof ServerLevel level) {
            Entity guard = level.getEntity(body.fearGuard());
            if (guard != null && guard.isAlive() && safe(threat, guard.position())) {
                return guard.position();
            }
            body.setFearGuard(null);
        }
        BlockPos home = body.home();
        if (home != null) {
            Vec3 at = Vec3.atBottomCenterOf(home);
            boolean there = body.distanceToSqr(at) <= ARRIVED_SQR;
            if (there ? threat.distanceToSqr(at) > TOO_NEAR_SQR : safe(threat, at)) {
                return at;
            }
        }
        return null;
    }

    private boolean safe(Vec3 threat, Vec3 place) {
        return Flight.leadsAway(body.getX(), body.getZ(), threat.x, threat.z, place.x, place.z);
    }
}
