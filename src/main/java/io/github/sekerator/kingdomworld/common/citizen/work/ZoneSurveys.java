package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estimates what new work posts yield by looking over their zones, one post after another and a budget of
 * blocks per tick, so that abstract work can begin before anybody watched it. A post is looked at when it was
 * placed or its settings changed and while its work has not been measured yet; its zone must be loaded.
 */
public final class ZoneSurveys {
    /** Blocks all surveys together look at per tick. */
    public static final int BLOCKS_PER_TICK = 2_048;

    private static final Deque<WorldPos> QUEUE = new ArrayDeque<>();
    private static final Set<WorldPos> QUEUED = new HashSet<>();
    private static final Map<WorldPos, WorkStatus> ZONE_STATUS = new HashMap<>();
    private static ZoneSurvey current;
    private static WorldPos currentPos;

    private ZoneSurveys() {
    }

    /**
     * Asks for a look over the zone of a post soon.
     */
    public static void request(WorldPos post) {
        if (QUEUED.add(post)) {
            QUEUE.add(post);
        }
    }

    /**
     * What the last look over the zone of a post found; {@link WorkStatus#WAITING} before the first one.
     */
    public static WorkStatus zoneStatus(WorldPos post) {
        return ZONE_STATUS.getOrDefault(post, WorkStatus.WAITING);
    }

    public static boolean pending(WorldPos post) {
        return QUEUED.contains(post) || post.equals(currentPos);
    }

    /**
     * Looks at a share of the zones waiting; called every server tick.
     */
    public static void tick(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        int budget = BLOCKS_PER_TICK;
        int started = 0;
        while (budget > 0) {
            if (current == null) {
                if (QUEUE.isEmpty() || started >= 8) {
                    return;
                }
                WorldPos next = QUEUE.poll();
                QUEUED.remove(next);
                started++;
                start(server, next);
                continue;
            }
            Optional<WorkPost> post = PostWork.post(server, currentPos);
            if (post.isEmpty()) {
                current = null;
                currentPos = null;
                continue;
            }
            budget -= Math.max(1, current.step(budget));
            if (current.done()) {
                current.complete();
                if (!post.get().yield().measured()) {
                    post.get().setYield(current.result());
                }
                ZONE_STATUS.put(currentPos, current.status());
                current = null;
                currentPos = null;
            }
        }
    }

    /**
     * Estimates a zone right away, for game tests and commands.
     */
    public static void surveyNow(MinecraftServer server, WorkPost post) {
        ServerLevel level = PostWork.level(server, post.pos());
        ProfessionBehavior behavior = ProfessionBehaviors.get(post.professionId()).orElse(null);
        if (level == null || behavior == null) {
            return;
        }
        ZoneSurvey survey = behavior.surveyZone(level, post);
        if (survey == null) {
            return;
        }
        while (!survey.done()) {
            survey.step(Integer.MAX_VALUE);
        }
        survey.complete();
        if (!post.yield().measured()) {
            post.setYield(survey.result());
        }
        ZONE_STATUS.put(post.pos(), survey.status());
    }

    private static void start(MinecraftServer server, WorldPos pos) {
        Optional<WorkPost> post = PostWork.post(server, pos);
        ServerLevel level = PostWork.level(server, pos);
        if (post.isEmpty() || level == null || !level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
            return;
        }
        ProfessionBehavior behavior = ProfessionBehaviors.get(post.get().professionId()).orElse(null);
        if (post.get().yield().measured() && (behavior == null || !behavior.resurveys())) {
            return;
        }
        current = behavior == null ? null : behavior.surveyZone(level, post.get());
        currentPos = current == null ? null : pos;
    }

    public static void forget(WorldPos post) {
        ZONE_STATUS.remove(post);
        if (QUEUED.remove(post)) {
            QUEUE.remove(post);
        }
    }

    public static void reset() {
        QUEUE.clear();
        QUEUED.clear();
        ZONE_STATUS.clear();
        current = null;
        currentPos = null;
    }
}
