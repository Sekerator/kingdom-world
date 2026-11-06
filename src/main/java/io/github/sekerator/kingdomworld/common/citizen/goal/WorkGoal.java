package io.github.sekerator.kingdomworld.common.citizen.goal;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The work of a body: runs the goal of the citizen's current profession and switches to a new one when the
 * profession changes.
 */
public final class WorkGoal extends Goal {
    private final CitizenEntity body;
    private String profession = "";
    private Goal work;

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

    @Override
    public boolean canUse() {
        Goal goal = current();
        return goal != null && goal.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return work != null && profession.equals(body.professionId()) && work.canContinueToUse();
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
            work.tick();
        }
    }
}
