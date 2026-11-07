package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The work posts of all kingdoms by position. Every post is also a place of kind {@link SiteKinds#WORK_POST} in
 * the {@link SiteRegistry}, so that the scan of the world forgets posts whose block is gone; {@link Citizens}
 * creates and removes both together and keeps the workers consistent with the citizens' workplaces.
 *
 * <p>Not thread safe.
 */
public final class WorkPosts {
    private final Map<WorldPos, WorkPost> byPos = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        for (WorkPost post : byPos.values()) {
            post.setChangeListener(listener);
        }
    }

    public Optional<WorkPost> get(WorldPos pos) {
        return Optional.ofNullable(byPos.get(pos));
    }

    /**
     * Posts of a kingdom in the order they were found.
     */
    public List<WorkPost> postsOf(UUID kingdomId) {
        List<WorkPost> result = new ArrayList<>();
        for (WorkPost post : byPos.values()) {
            if (post.kingdomId().equals(kingdomId)) {
                result.add(post);
            }
        }
        return result;
    }

    /**
     * Posts of a kingdom with the given profession.
     */
    public List<WorkPost> postsOf(UUID kingdomId, String professionId) {
        List<WorkPost> result = new ArrayList<>();
        for (WorkPost post : byPos.values()) {
            if (post.kingdomId().equals(kingdomId) && post.professionId().equals(professionId)) {
                result.add(post);
            }
        }
        return result;
    }

    /**
     * The post of a kingdom with the profession and a free seat nearest to {@code near}; any of them if
     * {@code near} is {@code null}.
     */
    public Optional<WorkPost> nearestWithSeat(UUID kingdomId, String professionId, WorldPos near) {
        WorkPost best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (WorkPost post : postsOf(kingdomId, professionId)) {
            if (post.freeSeats() <= 0) {
                continue;
            }
            double distance = near == null ? 0.0 : near.distanceSqr(post.pos());
            if (best == null || distance < bestDistance) {
                best = post;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public int size() {
        return byPos.size();
    }

    public List<WorkPost> all() {
        return List.copyOf(byPos.values());
    }

    // ---------------------------------------------------------------- changes, through Citizens

    WorkPost put(WorldPos pos, UUID kingdomId, int radius, int herdSize) {
        WorkPost post = new WorkPost(pos, kingdomId, radius, herdSize);
        post.setChangeListener(changeListener);
        byPos.put(pos, post);
        changeListener.run();
        return post;
    }

    Optional<WorkPost> remove(WorldPos pos) {
        WorkPost post = byPos.remove(pos);
        if (post == null) {
            return Optional.empty();
        }
        post.setChangeListener(() -> {
        });
        changeListener.run();
        return Optional.of(post);
    }

    List<WorkPost> removeKingdom(UUID kingdomId) {
        List<WorkPost> removed = postsOf(kingdomId);
        for (WorkPost post : removed) {
            byPos.remove(post.pos());
            post.setChangeListener(() -> {
            });
        }
        if (!removed.isEmpty()) {
            changeListener.run();
        }
        return removed;
    }

    /**
     * Restores a post from saved data, without workers.
     *
     * @throws IllegalArgumentException if another post is known at the position
     */
    void restore(WorkPost post) {
        if (byPos.containsKey(post.pos())) {
            throw new IllegalArgumentException("Two work posts at " + post.pos());
        }
        post.setChangeListener(changeListener);
        byPos.put(post.pos(), post);
    }
}
