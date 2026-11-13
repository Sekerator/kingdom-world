package io.github.sekerator.kingdomworld.common.tactics;

import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.core.tactics.Spot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * How one body carries out its duty in the plan of its squad: it walks to its place (straight when it is close, by a path
 * from the shared budget of paths when it is far), goes round its target to the side of its sector instead of through it,
 * and strikes when the target is within reach. One for each goal that fights by plans; it keeps the cooldowns of its body.
 */
public final class TacticMover {
    /** Ticks between two blows, as a mob swings. */
    private static final int SWING_TICKS = 20;
    /** Ticks before a new path may be asked for. */
    private static final int PATH_TICKS = 10;
    /** Blocks within which a body walks straight to its place without a path. */
    private static final double STRAIGHT = 4.0;

    private final PathfinderMob body;
    private int pathCooldown;
    private int swingCooldown;
    private Spot going;

    public TacticMover(PathfinderMob body) {
        this.body = body;
    }

    /** Counts the cooldowns down; called once a tick by the goal. */
    public void tick() {
        if (pathCooldown > 0) {
            pathCooldown--;
        }
        if (swingCooldown > 0) {
            swingCooldown--;
        }
    }

    public static Spot spot(LivingEntity entity) {
        return new Spot(entity.getX(), entity.getY(), entity.getZ());
    }

    /**
     * Walks to a place.
     *
     * @return whether the body is there
     */
    public boolean walk(Spot place, double speed) {
        double distance = body.distanceToSqr(place.x(), place.y(), place.z());
        if (distance < 1.0) {
            body.getNavigation().stop();
            going = null;
            return true;
        }
        if (distance < STRAIGHT * STRAIGHT && Math.abs(body.getY() - place.y()) < 1.2) {
            // Close by and on the same level: a step, no path to search.
            body.getNavigation().stop();
            going = null;
            body.getMoveControl().setWantedPosition(place.x(), place.y(), place.z(), speed);
            return false;
        }
        boolean moved = going == null || going.distanceSq(place) > 4.0;
        if ((moved || body.getNavigation().isDone()) && pathCooldown <= 0 && PathBudget.tryAcquire()) {
            pathCooldown = PATH_TICKS;
            going = place;
            body.getNavigation().moveTo(place.x(), place.y(), place.z(), speed);
        }
        return false;
    }

    /**
     * Strikes a target from the side of a place: the body comes at it from there, going round it rather than through it, and
     * swings when it is within reach.
     */
    public void strikeFrom(LivingEntity target, Spot side, double speed) {
        body.getLookControl().setLookAt(target, 30.0f, 30.0f);
        Spot at = spot(target);
        double angle = Math.atan2(side.z() - at.z(), side.x() - at.x());
        double own = Math.atan2(body.getZ() - at.z(), body.getX() - at.x());
        double off = wrap(angle - own);
        double reach = Math.sqrt(reach(target));
        double distance = body.distanceTo(target);
        if (Math.abs(off) > Math.PI / 2 && distance < 5.0) {
            // On the wrong side and close: round the target by its flank first, a quarter at a time.
            walk(at.toward(own + Math.signum(off) * Math.PI / 2, Math.max(2.0, reach + 1.0)), speed);
        } else {
            walk(at.toward(angle, Math.max(0.8, reach - 0.4)), speed);
        }
        swing(target);
    }

    /**
     * Holds a place facing a target, and strikes it if it comes within reach.
     */
    public void hold(Spot place, LivingEntity target, double speed) {
        walk(place, speed);
        if (target != null) {
            body.getLookControl().setLookAt(target, 30.0f, 30.0f);
            swing(target);
        }
    }

    /**
     * Swings at a target within reach that the body sees, once its arm is ready.
     *
     * @return whether it struck
     */
    public boolean swing(LivingEntity target) {
        if (swingCooldown > 0 || body.distanceToSqr(target.getX(), target.getY(), target.getZ()) > reach(target)
                || !body.getSensing().hasLineOfSight(target)) {
            return false;
        }
        swingCooldown = SWING_TICKS;
        body.swing(InteractionHand.MAIN_HAND);
        body.doHurtTarget(target);
        return true;
    }

    /** The square of the reach of a blow of the body at a target, as a mob reaches. */
    public double reach(LivingEntity target) {
        double width = body.getBbWidth() * 2.0;
        return width * width + target.getBbWidth();
    }

    private static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        if (a > Math.PI) {
            a -= 2 * Math.PI;
        } else if (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }
}
