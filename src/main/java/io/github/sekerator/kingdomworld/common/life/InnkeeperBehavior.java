package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The innkeeper: works at a work post inside a tavern (the counter). While a healthy innkeeper works there, the tavern is open:
 * citizens spend their evenings in it and players buy food and drink, hear rumors and rent rooms from the innkeeper. Near
 * players the innkeeper stands at the post.
 */
public final class InnkeeperBehavior implements ProfessionBehavior {
    public static final Profession INNKEEPER = new Profession("innkeeper", SiteKinds.WORK_POST);

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new CounterGoal(body, this);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    /**
     * The working tavern a post stands in.
     */
    public static Optional<BuildingEffects.Effect> tavernOf(MinecraftServer server, WorkPost post) {
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, post.kingdomId(), BuildingRoles.TAVERN)) {
            Building building = effect.building();
            if (building.dimension().equals(post.pos().dimension()) && building.box().contains(post.pos().x(), post.pos().y(), post.pos().z())) {
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a healthy innkeeper works at a post inside the tavern.
     */
    public static boolean keeps(MinecraftServer server, Building tavern) {
        var state = KingdomWorldApi.state(server);
        for (WorkPost post : state.citizens().posts().postsOf(tavern.kingdomId())) {
            if (!post.professionId().equals(INNKEEPER.id()) || !tavern.dimension().equals(post.pos().dimension())
                    || !tavern.box().contains(post.pos().x(), post.pos().y(), post.pos().z())) {
                continue;
            }
            for (UUID worker : post.workers()) {
                Optional<Citizen> citizen = state.citizens().get(worker);
                if (citizen.isPresent() && citizen.get().state() != CitizenState.WOUNDED
                        && citizen.get().professionId().equals(INNKEEPER.id())) {
                    return true;
                }
            }
        }
        return false;
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
        } else if (!PostWork.onOwnLand(server, post)) {
            PostWork.setStatus(server, citizen, WorkStatus.OUTSIDE_TERRITORY);
        } else if (tavernOf(server, post).isEmpty()) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_WORKSTATION);
        } else {
            PostWork.setStatus(server, citizen, WorkStatus.WORKING);
        }
    }

    /**
     * An innkeeper near players: stands at the counter, the post, and looks at the room.
     */
    private static final class CounterGoal extends PostWorkGoal {
        private static final double REACH_SQR = 2.0 * 2.0;
        private static final int SHIFT = 400;
        private int standing;

        CounterGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected boolean isWorkTime() {
            // The day of the innkeeper is decided by the life of cities: until the night.
            return true;
        }

        @Override
        protected boolean deliversToPost() {
            return false;
        }

        @Override
        protected void onStart() {
            standing = 0;
        }

        @Override
        @Nullable
        protected WorkStatus work(ServerLevel level) {
            if (tavernOf(level.getServer(), post).isEmpty()) {
                return WorkStatus.NO_WORKSTATION;
            }
            BlockPos counter = new BlockPos(post.pos().x(), post.pos().y(), post.pos().z());
            if (!near(counter, REACH_SQR)) {
                moveTo(counter);
                return null;
            }
            report(WorkStatus.WORKING);
            return ++standing >= SHIFT ? WorkStatus.WORKING : null;
        }
    }
}
