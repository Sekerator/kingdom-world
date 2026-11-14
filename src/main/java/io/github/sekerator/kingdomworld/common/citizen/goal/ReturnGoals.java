package io.github.sekerator.kingdomworld.common.citizen.goal;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Bodies going back where they belong: one sent back from the window of the workers drops what it does and walks home or to its
 * post, and is put there if it does not get there in time or gets stuck; one with nothing to do that went far from its home or post
 * walks back by itself. Paths come from the {@link PathBudget} like those of the other goals.
 */
public final class ReturnGoals {
    /** Ticks a body may stand still on its way before it counts as stuck. */
    private static final int STUCK_TICKS = 200;
    /** How close to where it goes a body has arrived. */
    private static final double ARRIVED_SQR = 3.0 * 3.0;
    /** A body walking back by itself stops once it is this near its home or post again. */
    private static final double BACK_SQR = 12.0 * 12.0;
    /** Ticks between looks whether a body with nothing to do is far from where it belongs. */
    private static final int LOOK_TICKS = 40;

    private ReturnGoals() {
    }

    /**
     * Walks a body to a place with paths from the budget and tells whether it moves at all.
     */
    private abstract static class Walk extends Goal {
        protected final CitizenEntity body;
        private int pathCooldown;
        private int still;
        @Nullable
        private BlockPos lastPos;

        Walk(CitizenEntity body) {
            this.body = body;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public void start() {
            pathCooldown = 0;
            still = 0;
            lastPos = body.blockPosition();
        }

        @Override
        public void stop() {
            body.getNavigation().stop();
        }

        /**
         * Walks on to the place.
         *
         * @return whether the body stood still for {@link #STUCK_TICKS}
         */
        protected boolean walk(BlockPos place) {
            BlockPos now = body.blockPosition();
            if (lastPos != null && now.distSqr(lastPos) < 1.0) {
                still++;
            } else {
                still = 0;
                lastPos = now;
            }
            if (pathCooldown > 0) {
                pathCooldown--;
            } else if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
                body.getNavigation().moveTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, 0.65);
                pathCooldown = 40;
            }
            return still > STUCK_TICKS;
        }
    }

    /**
     * A body sent back: it walks home or to its post above anything but running for its life, and is put there when the time is up
     * or it is stuck.
     */
    public static final class SentBack extends Walk {
        public SentBack(CitizenEntity body) {
            super(body);
        }

        @Override
        public boolean canUse() {
            return body.returnTarget() != null && !body.isFrightened();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos target = body.returnTarget();
            if (target == null) {
                return;
            }
            if (body.blockPosition().distSqr(target) <= ARRIVED_SQR) {
                body.arrivedBack();
                return;
            }
            boolean stuck = walk(target);
            if (stuck || body.level().getGameTime() >= body.returnUntil()) {
                body.putBack();
            }
        }
    }

    /**
     * A body with nothing to do far from its home or its post walks back by itself. Soldiers, those running for their lives, those
     * sent back already and those held somewhere (an expedition, the land of another kingdom) are left to their own goals, and so is a
     * citizen whose profession has work through its hours of work, between its rounds too; work, the day (festival, tavern, sleep) and
     * care come first anyway.
     */
    public static final class Idle extends Walk {
        @Nullable
        private BlockPos target;
        private int look;

        public Idle(CitizenEntity body) {
            super(body);
        }

        @Override
        public boolean canUse() {
            if (body.returnTarget() != null || body.isFrightened() || Professions.isMilitary(body.professionId()) || ++look < LOOK_TICKS) {
                return false;
            }
            look = 0;
            target = idleTarget(false);
            return target != null;
        }

        @Nullable
        private BlockPos idleTarget(boolean going) {
            if (body.citizenId() == null || body.getServer() == null) {
                return null;
            }
            Optional<Citizen> citizen = KingdomWorldApi.state(body.getServer()).citizens().get(body.citizenId());
            if (citizen.isEmpty()) {
                return null;
            }
            BlockPos here = body.blockPosition();
            return io.github.sekerator.kingdomworld.common.citizen.CitizenWhereabouts.idleTarget(body.getServer(), citizen.get(),
                    new io.github.sekerator.kingdomworld.core.citizen.WorldPos(
                            io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(body.level()), here.getX(), here.getY(), here.getZ()),
                    io.github.sekerator.kingdomworld.common.life.LifeWork.works(body), going);
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || body.returnTarget() != null || body.isFrightened() || body.blockPosition().distSqr(target) <= BACK_SQR) {
                return false;
            }
            // Looked at again now and then: hours of work that began or a hold that came up end the walk, which otherwise goes on
            // home, past the reach of being far, into the reach of the walks about home.
            if (++look >= LOOK_TICKS) {
                look = 0;
                return idleTarget(true) != null;
            }
            return true;
        }

        @Override
        public void tick() {
            if (target != null) {
                walk(target);
            }
        }

        @Override
        public void stop() {
            super.stop();
            target = null;
        }
    }
}
