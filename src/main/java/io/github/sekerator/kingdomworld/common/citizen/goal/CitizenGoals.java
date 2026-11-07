package io.github.sekerator.kingdomworld.common.citizen.goal;

import io.github.sekerator.kingdomworld.common.citizen.CitizenCare;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The everyday behaviour of citizen bodies apart from work.
 */
public final class CitizenGoals {

    private CitizenGoals() {
    }

    /**
     * Runs from monsters nearby.
     */
    public static Goal fleeMonsters(CitizenEntity body) {
        return new AvoidEntityGoal<>(body, Mob.class, entity -> entity instanceof Enemy
                && !io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.fights(body), 8.0f, 0.6, 0.8, entity -> true);
    }

    /**
     * Walks to a place and stays near it; paths come from the {@link PathBudget}.
     */
    private abstract static class GoToPlaceGoal extends Goal {
        protected final CitizenEntity body;
        private int pathCooldown;

        GoToPlaceGoal(CitizenEntity body) {
            this.body = body;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        protected abstract BlockPos place();

        protected abstract boolean wanted();

        @Override
        public boolean canUse() {
            BlockPos place = place();
            return place != null && wanted() && body.blockPosition().distSqr(place) > 2.5 * 2.5;
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos place = place();
            return place != null && wanted() && body.blockPosition().distSqr(place) > 1.5 * 1.5;
        }

        @Override
        public void start() {
            pathCooldown = 0;
        }

        @Override
        public void stop() {
            body.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos place = place();
            if (place == null) {
                return;
            }
            if (pathCooldown > 0) {
                pathCooldown--;
                return;
            }
            if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
                body.getNavigation().moveTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, 0.6);
                pathCooldown = 40;
            }
        }
    }

    /**
     * A wounded citizen goes to an infirmary of its kingdom with room, or home if there is none.
     */
    public static final class SeekCareGoal extends GoToPlaceGoal {
        public SeekCareGoal(CitizenEntity body) {
            super(body);
        }

        @Override
        protected boolean wanted() {
            return body.isWounded();
        }

        @Override
        protected BlockPos place() {
            WorldPos infirmary = body.isWounded() ? CitizenCare.chooseInfirmary(body) : null;
            if (infirmary != null) {
                return new BlockPos(infirmary.x(), infirmary.y(), infirmary.z());
            }
            return body.home();
        }
    }

    /**
     * At night a citizen goes home and stays by its bed.
     */
    public static final class GoHomeAtNightGoal extends GoToPlaceGoal {
        public GoHomeAtNightGoal(CitizenEntity body) {
            super(body);
        }

        @Override
        protected boolean wanted() {
            // Adventurers and soldiers keep watch at night, when the monsters come.
            return !body.level().isDay() && !io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.fights(body);
        }

        @Override
        protected BlockPos place() {
            return body.home();
        }
    }

    /**
     * Wanders about near home during the day; at night it stays put.
     */
    public static final class StrollNearHomeGoal extends WaterAvoidingRandomStrollGoal {
        private final CitizenEntity body;

        public StrollNearHomeGoal(CitizenEntity body) {
            super(body, 0.5);
            this.body = body;
        }

        @Override
        public boolean canUse() {
            return body.level().isDay() && super.canUse();
        }
    }
}
