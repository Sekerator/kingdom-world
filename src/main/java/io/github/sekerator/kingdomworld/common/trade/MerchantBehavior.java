package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurvey;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The merchant: works at a work post with market stalls in its zone. While a healthy merchant works there, the stalls
 * are open to players; the merchants of a kingdom also sell what the king lets go abroad (see {@link TradeWork}), near
 * players or far from them. Near players a merchant stands at a stall.
 */
public final class MerchantBehavior implements ProfessionBehavior {
    /** Where the stalls of each post were seen last. */
    private static final Map<WorldPos, List<BlockPos>> KNOWN = new HashMap<>();

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new MerchantGoal(body, this);
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
        return new StallSurvey(level, post);
    }

    public static List<BlockPos> knownStalls(WorldPos post) {
        return KNOWN.getOrDefault(post, List.of());
    }

    public static boolean surveyed(WorldPos post) {
        return KNOWN.containsKey(post);
    }

    public static void reset() {
        KNOWN.clear();
    }

    /**
     * The zone a merchant post serves.
     */
    public static WorkZone zone(WorkPost post) {
        return WorkZone.of(post.pos(), Professions.MERCHANT.id(), post.radius(), GatheringSettings.rules().mineLevels());
    }

    /**
     * Whether a merchant of the kingdom keeps a stall open: a merchant post with stalls whose zone holds the stall and a
     * healthy merchant working there.
     */
    public static boolean keepsOpen(MinecraftServer server, UUID kingdomId, WorldPos stall) {
        var state = KingdomWorldApi.state(server);
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            if (!post.professionId().equals(Professions.MERCHANT.id()) || post.stations().getOrDefault(Stations.MARKET_STALL, 0) <= 0
                    || !zone(post).contains(stall.dimension(), stall.x(), stall.y(), stall.z())) {
                continue;
            }
            for (UUID worker : post.workers()) {
                Optional<Citizen> citizen = state.citizens().get(worker);
                if (citizen.isPresent() && citizen.get().state() != CitizenState.WOUNDED
                        && citizen.get().professionId().equals(Professions.MERCHANT.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The healthy merchants of a kingdom at posts with a stall, who sell abroad.
     */
    public static List<Citizen> working(MinecraftServer server, UUID kingdomId) {
        var state = KingdomWorldApi.state(server);
        List<Citizen> merchants = new ArrayList<>();
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            if (!post.professionId().equals(Professions.MERCHANT.id()) || post.stations().getOrDefault(Stations.MARKET_STALL, 0) <= 0
                    || !PostWork.onOwnLand(server, post)) {
                continue;
            }
            for (UUID worker : post.workers()) {
                state.citizens().get(worker).filter(citizen -> citizen.state() != CitizenState.WOUNDED
                        && citizen.professionId().equals(Professions.MERCHANT.id())).ifPresent(merchants::add);
            }
        }
        return merchants;
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
        } else if (post.stations().getOrDefault(Stations.MARKET_STALL, 0) <= 0) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_WORKSTATION);
        } else {
            PostWork.setStatus(server, citizen, WorkStatus.WORKING);
        }
    }

    /**
     * Looks for the market stalls of the zone.
     */
    private static final class StallSurvey extends ZoneSurvey {
        private final List<BlockPos> stalls = new ArrayList<>();

        StallSurvey(ServerLevel level, WorkPost post) {
            super(level, post, zone(post));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (state.is(ModRegistries.MARKET_STALL.get())) {
                stalls.add(pos.immutable());
            }
        }

        @Override
        protected void finish() {
            post.setStations(stalls.isEmpty() ? Map.of() : Map.of(Stations.MARKET_STALL, stalls.size()));
            KNOWN.put(post.pos(), List.copyOf(stalls));
        }

        @Override
        public PostYield result() {
            return new PostYield(Map.of(), Map.of(), GatheringRules.WORK_TICKS_PER_DAY, false);
        }

        @Override
        public WorkStatus status() {
            return stalls.isEmpty() ? WorkStatus.NO_WORKSTATION : WorkStatus.WORKING;
        }
    }
}
