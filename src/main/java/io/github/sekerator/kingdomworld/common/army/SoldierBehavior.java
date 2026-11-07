package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The warrior and the archer. Near players a soldier carries out the order of its squad (holds a point, walks a patrol,
 * guards an area, follows a player) or trains at a barracks, and fights the monsters it meets with vanilla goals; never a
 * player or a citizen in peace. Far from players it trains by formula and keeps its gear; its squad fights by the model of
 * battles.
 */
public final class SoldierBehavior implements ProfessionBehavior {
    private final boolean archer;

    public SoldierBehavior(boolean archer) {
        this.archer = archer;
    }

    public boolean archer() {
        return archer;
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new SoldierGoal(body, archer);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        if (ArmyWork.isReadOnly(server) || ArmyWork.isAwayFromHome(server, citizen.id())) {
            return;
        }
        ArmyWork.equip(server, citizen);
        ArmyWork.trainAbstractly(server, citizen, intervalTicks);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public boolean risky() {
        return true;
    }

    @Override
    public boolean fights() {
        return true;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.BATTLE;
    }
}
