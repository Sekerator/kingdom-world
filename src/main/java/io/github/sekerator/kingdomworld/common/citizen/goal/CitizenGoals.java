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
     * A wounded or ill citizen goes to an infirmary of its kingdom with room, or home if there is none.
     */
    public static final class SeekCareGoal extends GoToPlaceGoal {
        public SeekCareGoal(CitizenEntity body) {
            super(body);
        }

        @Override
        protected boolean wanted() {
            // A wounded soldier does not walk off while an alarm of its kingdom lasts close by.
            return body.needsCare() && !io.github.sekerator.kingdomworld.common.law.LawWork.holdsOut(body);
        }

        @Override
        protected BlockPos place() {
            WorldPos infirmary = body.needsCare() ? CitizenCare.chooseInfirmary(body) : null;
            if (infirmary != null) {
                return new BlockPos(infirmary.x(), infirmary.y(), infirmary.z());
            }
            return body.home();
        }
    }

    /**
     * Wanders about near home when it is free: a citizen without work all day, a worker after its hours and on a festival.
     * At night it sleeps, and in its hours a worker stays at its work.
     */
    public static final class StrollNearHomeGoal extends WaterAvoidingRandomStrollGoal {
        private final CitizenEntity body;

        public StrollNearHomeGoal(CitizenEntity body) {
            super(body, 0.5);
            this.body = body;
        }

        @Override
        public boolean canUse() {
            if (body.citizenId() != null && body.getServer() != null) {
                if (io.github.sekerator.kingdomworld.common.life.LifeWork.phase(body.getServer(), body.citizenId())
                        == io.github.sekerator.kingdomworld.core.life.DaySchedule.Phase.NIGHT) {
                    return false;
                }
                boolean hasWork = io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.get(body.professionId()).isPresent();
                if (hasWork && io.github.sekerator.kingdomworld.common.life.LifeWork.works(body)) {
                    return false;
                }
            }
            return super.canUse();
        }
    }
}
