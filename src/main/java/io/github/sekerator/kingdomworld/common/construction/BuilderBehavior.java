package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * The builder: works at a work post and builds the constructions of its kingdom within reach of the post, the most urgent
 * first. Near players it walks to every block, clears the place and lays the blocks one by one; far from players it works
 * by the time a block takes, pays for each block in one go and the blocks are placed when their place is loaded.
 */
public final class BuilderBehavior implements ProfessionBehavior {

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new BuilderGoal(body, this);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public Equipment.ToolNeed tool() {
        return Equipment.ToolNeed.PICKAXE_IF_ANY;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.BUILDING;
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        Optional<WorkPost> found = PostWork.postOf(server, citizen);
        if (found.isEmpty()) {
            citizen.setWorkStatus(WorkStatus.NO_WORKPLACE);
            return;
        }
        WorkPost post = found.get();
        if (citizen.state() == CitizenState.WOUNDED) {
            PostWork.setStatus(server, citizen, WorkStatus.WOUNDED);
            return;
        }
        if (!PostWork.onOwnLand(server, post)) {
            PostWork.setStatus(server, citizen, WorkStatus.OUTSIDE_TERRITORY);
            return;
        }
        List<Construction> candidates = ConstructionWork.candidates(server, post);
        if (candidates.isEmpty()) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_CONSTRUCTIONS);
            return;
        }
        Equipment.Hands hands = Equipment.of(server, citizen.id());
        Equipment.equipTool(server, post.kingdomId(), hands, EquipmentSlot.MAINHAND, tool(), 0, post.pos());
        ConstructionRules rules = ConstructionSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        WorkStatus status = WorkStatus.NO_CONSTRUCTIONS;
        for (Construction construction : candidates) {
            Optional<BuildPlan> plan = ConstructionWork.plan(construction);
            if (plan.isEmpty()) {
                continue;
            }
            ConstructionWork.worked(construction, citizen.id(), now);
            boolean waited = !construction.pending().isEmpty();
            double budget = construction.carry() + rules.blocks(intervalTicks, citizen.experience(), EconomySettings.workSpeed(citizen.mood()));
            ConstructionRules.Progress progress = ConstructionRules.work(plan.get(), construction, budget,
                    cost -> ConstructionWork.pay(server, construction, cost));
            construction.setCarry(progress.carry());
            if (progress.steps() > 0) {
                if (!waited && !construction.pending().isEmpty()) {
                    SiteLoader.pendingGrew(construction, now);
                }
                citizen.addExperience(progress.steps());
                post.addProduced(PostWork.gameDay(server), progress.steps());
                if (!construction.status().isProblem() || construction.status() == Construction.Status.NO_MATERIALS) {
                    construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
                }
                PostWork.setStatus(server, citizen, WorkStatus.WORKING);
                PostWork.accident(server, citizen, post, this, hands, intervalTicks, server.overworld().getRandom());
                return;
            }
            if (progress.starved()) {
                construction.setStatus(Construction.Status.NO_MATERIALS);
                status = WorkStatus.NO_MATERIALS;
            } else if (construction.status() == Construction.Status.BLOCKED && status != WorkStatus.NO_MATERIALS) {
                status = WorkStatus.BLOCKED;
            }
        }
        PostWork.setStatus(server, citizen, status);
    }
}
