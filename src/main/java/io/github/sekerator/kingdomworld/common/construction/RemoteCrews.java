package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.HelperWork;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Helpers for constructions no post reaches: a city without builders of its own, a place outside every city. The builders of
 * the kingdom without a construction of their own give them half of their time while roads and walls wait for them too, all of
 * it otherwise, the most urgent and oldest construction first; they help as fast as at home where a built road leads from their
 * city to that of the construction, slower without one ({@code remoteSpeed}). They do not walk there and have no body at the
 * place: they pay for the blocks, as builders far from players do, and the loader of places puts them in.
 */
public final class RemoteCrews {
    /** Helpers at work on each construction in the last second, and how fast in percent, by construction. */
    private static final Map<Long, int[]> HELPING = new HashMap<>();

    private RemoteCrews() {
    }

    static void reset() {
        HELPING.clear();
    }

    /**
     * Helpers at work on a construction in the last second: their number and how fast they help, in percent of their speed at home.
     */
    public static int[] helping(long constructionId) {
        return HELPING.getOrDefault(constructionId, new int[]{0, 0});
    }

    /**
     * The constructions of a kingdom no post of a builder reaches, the most urgent and oldest first.
     */
    public static List<Construction> unreached(MinecraftServer server, UUID kingdomId) {
        List<WorkPost> posts = KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId, Professions.BUILDER.id());
        List<Construction> result = new ArrayList<>();
        for (Construction construction : KingdomWorldApi.state(server).construction().of(kingdomId)) {
            if (construction.paused() || construction.status().isProblem() && construction.status() != Construction.Status.NO_BUILDER
                    && construction.status() != Construction.Status.NO_MATERIALS) {
                continue;
            }
            boolean reached = false;
            for (WorkPost post : posts) {
                if (ConstructionWork.reaches(server, post, construction)) {
                    reached = true;
                    break;
                }
            }
            if (!reached) {
                result.add(construction);
            }
        }
        result.sort(Comparator.comparing((Construction c) -> -c.priority().ordinal()).thenComparingLong(Construction::created)
                .thenComparingLong(Construction::id));
        return result;
    }

    /**
     * Lets the helpers of a kingdom work on far constructions for a second.
     *
     * @param helpers   the builders of the kingdom without a construction of their own
     * @param roadsWait whether roads or walls of the kingdom wait for builders too
     * @return the share of the second of the helpers left for roads and walls, from 0 to 1
     */
    public static double work(MinecraftServer server, UUID kingdomId, List<Citizen> helpers, boolean roadsWait, long now) {
        if (helpers.isEmpty() || !ConstructionSettings.helpOtherCities()) {
            return 1.0;
        }
        List<Construction> far = unreached(server, kingdomId);
        if (far.isEmpty()) {
            return 1.0;
        }
        double share = HelperWork.remoteShare(roadsWait);
        ConstructionRules rules = ConstructionSettings.rules();
        Map<Long, Long> linked = HelperWork.linked(roadsBetweenCities(server, kingdomId));
        double remote = ConstructionSettings.remoteSpeed();
        for (Construction construction : far) {
            Optional<BuildPlan> plan = ConstructionWork.plan(construction);
            if (plan.isEmpty()) {
                continue;
            }
            long site = ConstructionWork.cityOf(server, construction);
            double budget = construction.carry();
            double home = 0;
            double fast = 0;
            for (Citizen helper : helpers) {
                double blocks = rules.blocks(20, helper.experience(), EconomySettings.workSpeed(helper.mood())) * share;
                long city = PostWork.postOf(server, helper).map(post -> ConstructionWork.cityOf(server, post.kingdomId(), post.pos()))
                        .map(found -> found == io.github.sekerator.kingdomworld.core.construction.BuilderReach.NO_CITY ? 0L : found).orElse(0L);
                double speed = HelperWork.speed(HelperWork.connected(linked, city, site), remote);
                budget += blocks * speed;
                fast += blocks * speed;
                home += blocks;
            }
            boolean waited = !construction.pending().isEmpty();
            ConstructionRules.Progress progress = ConstructionRules.work(plan.get(), construction, budget,
                    cost -> ConstructionWork.pay(server, construction, cost));
            construction.setCarry(progress.carry());
            if (progress.steps() <= 0) {
                if (progress.starved()) {
                    construction.setStatus(Construction.Status.NO_MATERIALS);
                }
                continue;
            }
            for (Citizen helper : helpers) {
                ConstructionWork.worked(construction, helper.id(), now);
            }
            if (!waited && !construction.pending().isEmpty()) {
                SiteLoader.pendingGrew(construction, now);
            }
            if (!construction.status().isProblem() || construction.status() == Construction.Status.NO_MATERIALS
                    || construction.status() == Construction.Status.NO_BUILDER) {
                construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
            }
            HELPING.put(construction.id(), new int[]{helpers.size(), home <= 0 ? 0 : (int) Math.round(100 * fast / home)});
            int each = Math.max(1, progress.steps() / helpers.size());
            for (Citizen helper : helpers) {
                helper.addExperience(each);
            }
            // The share went to this construction; what the far constructions did not use goes to the roads.
            return 1.0 - share;
        }
        return 1.0;
    }

    /**
     * Forgets the helpers of the last second, before they are counted again.
     */
    public static void startSecond() {
        HELPING.clear();
    }

    /**
     * The built roads of a kingdom between two of its cities, as their two cities (0 for the capital).
     */
    static List<long[]> roadsBetweenCities(MinecraftServer server, UUID kingdomId) {
        List<long[]> result = new ArrayList<>();
        for (Road road : RoadWork.state(server).roadsOf(kingdomId)) {
            if (road.status() != Road.Status.DONE) {
                continue;
            }
            Long from = cityOf(road.from(), kingdomId);
            Long to = cityOf(road.to(), kingdomId);
            if (from != null && to != null) {
                result.add(new long[]{from, to});
            }
        }
        return result;
    }

    private static Long cityOf(Endpoint end, UUID kingdomId) {
        if (end.kind() == Endpoint.Kind.CAPITAL && kingdomId.equals(end.kingdom())) {
            return 0L;
        }
        if (end.kind() == Endpoint.Kind.CITY) {
            return end.city();
        }
        return null;
    }
}
