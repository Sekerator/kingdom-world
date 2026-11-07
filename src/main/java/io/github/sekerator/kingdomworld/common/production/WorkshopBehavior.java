package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurvey;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A workshop: the smelter, the cook, the crafter and the smith. They work at the workstations in the zone of their
 * post, make what the king ordered (and in auto mode what the post makes of its own), take the ingredients from
 * the storehouses (or from the post's container, where carriers bring them) and bring the results to the
 * storehouses. Far from players they make things by the time a making takes, in one go each.
 */
public final class WorkshopBehavior implements ProfessionBehavior {
    /** Makings one worker does in one simulation step at most. */
    private static final int MAX_PER_STEP = 64;
    /** Where the workstations of each workshop were seen last, by kind. */
    private static final Map<WorldPos, Map<String, List<BlockPos>>> KNOWN = new HashMap<>();

    private final String profession;

    public WorkshopBehavior(String profession) {
        this.profession = profession;
    }

    public String profession() {
        return profession;
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new WorkshopGoal(body, this);
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
        return new StationSurvey(level, post);
    }

    @Override
    public Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        return Production.wanted(post, container);
    }

    @Override
    public boolean isInput(WorkPost post, ItemStack stack) {
        return Production.isInput(post, stack);
    }

    /**
     * Workstations of a post where they were seen last, by kind; empty before its zone was looked at.
     */
    public static Map<String, List<BlockPos>> knownStations(WorldPos post) {
        return KNOWN.getOrDefault(post, Map.of());
    }

    public static boolean surveyed(WorldPos post) {
        return KNOWN.containsKey(post);
    }

    public static void forget(WorldPos post) {
        KNOWN.remove(post);
    }

    public static void reset() {
        KNOWN.clear();
    }

    // ---------------------------------------------------------------- far from players

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
        Set<String> stations = new LinkedHashSet<>(post.stations().keySet());
        ProductionRules rules = ProductionSettings.rules();
        double factor = Production.suppliedByCarriers(server, post) ? 1.0 : GatheringRules.SELF_CARRY_FACTOR;
        double slowdown = rules.slowdown(post.yield().measured() ? post.yield().actions() : 0.0);
        double budget = post.progress().getOrDefault(Production.WORK_BANK, 0.0) + ProductionRules.workTicks(intervalTicks, factor);
        double keep = 0.0;
        int made = 0;
        WorkStatus status = WorkStatus.WORKING;
        for (int i = 0; i < MAX_PER_STEP; i++) {
            Production.Search search = Production.findJob(server, post, citizen, stations);
            if (search.job() == null) {
                status = made > 0 ? WorkStatus.WORKING : search.reason();
                break;
            }
            double cost = rules.ticks(search.job().recipe(), citizen.experience()) * slowdown;
            if (budget < cost) {
                // Not enough time left this step: it is kept for the next one.
                keep = budget;
                break;
            }
            Production.Made result = Production.makeAbstractly(server, post, citizen, search.job(), search.fuel());
            if (result != Production.Made.DONE) {
                status = made > 0 ? WorkStatus.WORKING : switch (result) {
                    case NO_FUEL -> WorkStatus.NO_FUEL;
                    case STORAGE_FULL -> WorkStatus.STORAGE_FULL;
                    default -> WorkStatus.NO_INGREDIENTS;
                };
                break;
            }
            budget -= cost;
            made++;
            post.setCurrent(search.job().recipe().output(), 1.0);
            if (search.job().order() != 0) {
                // The order moved on: its parent may be ready now.
                Production.plan(server, post.kingdomId());
            }
        }
        Map<String, Double> progress = new LinkedHashMap<>(post.progress());
        progress.put(Production.WORK_BANK, Math.max(0.0, keep));
        post.setProgress(progress);
        if (made > 0) {
            citizen.addExperience(made);
        }
        PostWork.setStatus(server, citizen, status);
        if (made > 0) {
            PostWork.accident(server, citizen, post, this, Equipment.of(server, citizen.id()), intervalTicks, server.overworld().getRandom());
        }
    }

    // ---------------------------------------------------------------- the zone

    /**
     * Looks for the workstations of the zone: what kinds stand there for the work far from players, and where, for
     * the workers near players.
     */
    private final class StationSurvey extends ZoneSurvey {
        private final Map<String, List<BlockPos>> stations = new LinkedHashMap<>();

        StationSurvey(ServerLevel level, WorkPost post) {
            super(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), GatheringSettings.rules().mineLevels()));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (!Workstations.mayBeStation(state)) {
                return;
            }
            String kind = Workstations.kindOf(level, pos, state);
            if (kind != null) {
                stations.computeIfAbsent(kind, k -> new ArrayList<>()).add(pos.immutable());
            }
        }

        @Override
        protected void finish() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            stations.forEach((kind, list) -> counts.put(kind, list.size()));
            post.setStations(counts);
            KNOWN.put(post.pos(), Map.copyOf(stations));
        }

        @Override
        public PostYield result() {
            double perDay = GatheringRules.WORK_TICKS_PER_DAY / (double) ProductionSettings.rules().craftTicks();
            return new PostYield(Map.of("*", perDay), Map.of(), perDay, false);
        }

        @Override
        public WorkStatus status() {
            for (String kind : Production.stationsOf(profession)) {
                if (stations.containsKey(kind)) {
                    return WorkStatus.WORKING;
                }
            }
            return WorkStatus.NO_WORKSTATION;
        }
    }
}
