package io.github.sekerator.kingdomworld.common.citizen.goal;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The work of a body: runs the goal of the citizen's current profession and switches to a new one when the
 * profession changes. Work happens in the morning and by day of the citizen's own day (see the life of cities); an ill
 * citizen works slower.
 */
public final class WorkGoal extends Goal {
    private final CitizenEntity body;
    private String profession = "";
    private Goal work;
    private int sickTicks;

    public WorkGoal(CitizenEntity body) {
        this.body = body;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private Goal current() {
        if (!profession.equals(body.professionId())) {
            profession = body.professionId();
            work = ProfessionBehaviors.get(profession).map(behavior -> behavior.createWork(body)).orElse(null);
        }
        return work;
    }

    /**
     * What the work of the body is at, for the window of the workers; {@code null} for a profession without work of its own.
     */
    public io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing doing() {
        if (work instanceof io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal post) {
            return post.doing();
        }
        return work == null ? null : io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.WORKING;
    }

    @Override
    public boolean canUse() {
        Goal goal = current();
        return goal != null && io.github.sekerator.kingdomworld.common.life.LifeWork.works(body) && goal.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return work != null && profession.equals(body.professionId()) && io.github.sekerator.kingdomworld.common.life.LifeWork.works(body)
                && work.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return work == null || work.isInterruptable();
    }

    @Override
    public void start() {
        if (work != null) {
            work.start();
        }
    }

    @Override
    public void stop() {
        if (work != null) {
            work.stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (work != null) {
            // An ill citizen works slower: its work goes on only in part of the ticks.
            double factor = io.github.sekerator.kingdomworld.common.life.LifeWork.workFactor(body.citizenId());
            if (factor < 1.0 && (sickTicks++ % 100) >= Math.round(factor * 100)) {
                return;
            }
            work.tick();
        }
    }
}
