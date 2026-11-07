package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurvey;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.production.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The registrar: works at a work post with desks of the guild in its zone. While a healthy registrar works there, the
 * desks are open and the branch of the guild in the kingdom works: players use the desks, the guild posts contracts of
 * its own. Near players a registrar stands at a desk.
 */
public final class RegistrarBehavior implements ProfessionBehavior {
    /** Where the desks of each post were seen last. */
    private static final Map<WorldPos, List<BlockPos>> KNOWN = new HashMap<>();

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new DeskGoal(body, this);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public boolean resurveys() {
        return true;
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new DeskSurvey(level, post);
    }

    static List<BlockPos> knownDesks(WorldPos post) {
        return KNOWN.getOrDefault(post, List.of());
    }

    static boolean surveyed(WorldPos post) {
        return KNOWN.containsKey(post);
    }

    public static void reset() {
        KNOWN.clear();
    }

    /**
     * The zone a registrar post serves.
     */
    public static WorkZone zone(WorkPost post) {
        return WorkZone.of(post.pos(), Professions.REGISTRAR.id(), post.radius(), GatheringSettings.rules().mineLevels());
    }

    private static boolean keeps(MinecraftServer server, WorkPost post) {
        var state = KingdomWorldApi.state(server);
        for (UUID worker : post.workers()) {
            Optional<Citizen> citizen = state.citizens().get(worker);
            if (citizen.isPresent() && citizen.get().state() != CitizenState.WOUNDED
                    && citizen.get().professionId().equals(Professions.REGISTRAR.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a registrar of the kingdom keeps a desk open: a registrar post whose zone holds the desk and a healthy
     * registrar working there.
     */
    public static boolean keepsOpen(MinecraftServer server, UUID kingdomId, WorldPos desk) {
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId)) {
            if (post.professionId().equals(Professions.REGISTRAR.id()) && post.stations().getOrDefault(Stations.GUILD_DESK, 0) > 0
                    && zone(post).contains(desk.dimension(), desk.x(), desk.y(), desk.z()) && keeps(server, post)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Healthy registrars of a kingdom at posts with a desk, on its land: the branch works while there is one.
     */
    public static int working(MinecraftServer server, UUID kingdomId) {
        int count = 0;
        var state = KingdomWorldApi.state(server);
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            if (!post.professionId().equals(Professions.REGISTRAR.id()) || post.stations().getOrDefault(Stations.GUILD_DESK, 0) <= 0
                    || !PostWork.onOwnLand(server, post)) {
                continue;
            }
            for (UUID worker : post.workers()) {
                if (state.citizens().get(worker).filter(citizen -> citizen.state() != CitizenState.WOUNDED
                        && citizen.professionId().equals(Professions.REGISTRAR.id())).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Desks of the guild the posts of a kingdom have in their zones.
     */
    public static int desks(MinecraftServer server, UUID kingdomId) {
        int count = 0;
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId)) {
            if (post.professionId().equals(Professions.REGISTRAR.id())) {
                count += post.stations().getOrDefault(Stations.GUILD_DESK, 0);
            }
        }
        return count;
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
        } else if (post.stations().getOrDefault(Stations.GUILD_DESK, 0) <= 0) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_WORKSTATION);
        } else {
            PostWork.setStatus(server, citizen, WorkStatus.WORKING);
        }
    }

    /**
     * Looks for the desks of the zone.
     */
    private static final class DeskSurvey extends ZoneSurvey {
        private final List<BlockPos> desks = new ArrayList<>();

        DeskSurvey(ServerLevel level, WorkPost post) {
            super(level, post, zone(post));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (state.is(GuildRegistries.GUILD_DESK.get())) {
                desks.add(pos.immutable());
            }
        }

        @Override
        protected void finish() {
            post.setStations(desks.isEmpty() ? Map.of() : Map.of(Stations.GUILD_DESK, desks.size()));
            KNOWN.put(post.pos(), List.copyOf(desks));
        }

        @Override
        public PostYield result() {
            return new PostYield(Map.of(), Map.of(), GatheringRules.WORK_TICKS_PER_DAY, false);
        }

        @Override
        public WorkStatus status() {
            return desks.isEmpty() ? WorkStatus.NO_WORKSTATION : WorkStatus.WORKING;
        }
    }

    /**
     * A registrar near players: walks to the nearest desk in the zone of its post and keeps it for a while.
     */
    private static final class DeskGoal extends PostWorkGoal {
        private static final double DESK_REACH_SQR = 2.5 * 2.5;
        private static final int GIVE_UP_TICKS = 600;
        private static final int SHIFT = 400;

        @Nullable
        private BlockPos desk;
        private int walking;
        private int standing;

        DeskGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected boolean deliversToPost() {
            return false;
        }

        @Override
        protected void onStart() {
            desk = null;
            walking = 0;
            standing = 0;
        }

        @Override
        @Nullable
        protected WorkStatus work(ServerLevel level) {
            if (desk == null || !level.getBlockState(desk).is(GuildRegistries.GUILD_DESK.get())) {
                desk = pick(level);
                if (desk == null) {
                    return surveyed(post.pos()) ? WorkStatus.NO_WORKSTATION : WorkStatus.WAITING;
                }
            }
            if (!near(desk, DESK_REACH_SQR)) {
                moveTo(desk);
                if (++walking > GIVE_UP_TICKS) {
                    desk = null;
                    ZoneSurveys.request(post.pos());
                    return WorkStatus.NO_WORKSTATION;
                }
                return null;
            }
            if (standing % 60 == 0) {
                lookAt(desk);
            }
            report(WorkStatus.WORKING);
            return ++standing >= SHIFT ? WorkStatus.WORKING : null;
        }

        @Nullable
        private BlockPos pick(ServerLevel level) {
            if (!surveyed(post.pos())) {
                ZoneSurveys.request(post.pos());
                return null;
            }
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : knownDesks(post.pos())) {
                if (!level.isLoaded(pos) || !level.getBlockState(pos).is(GuildRegistries.GUILD_DESK.get())) {
                    continue;
                }
                double distance = body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distance < bestDistance) {
                    best = pos;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                ZoneSurveys.request(post.pos());
            }
            return best;
        }
    }
}
