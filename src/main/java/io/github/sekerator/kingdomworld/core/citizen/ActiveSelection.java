package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Decides which citizens have a body in the world. A citizen qualifies while a player is within the activation
 * distance of its home or workplace (of where it is, if it has neither); when more qualify than the limit
 * allows, those nearest to a player win. Everybody else lives on in the abstract simulation.
 */
public final class ActiveSelection {
    /**
     * Chunks of slack on the activation distance for a citizen that has a body already, so that a player at the edge does
     * not make the body flicker.
     */
    public static final int KEEP_SLACK = 1;

    /**
     * Where a player is, in chunks.
     */
    public record PlayerSpot(String dimension, int chunkX, int chunkZ) {
        public PlayerSpot {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    /**
     * A citizen that may get a body.
     *
     * @param distance chunks to the nearest player, {@link Integer#MAX_VALUE} if no player is in its dimension
     * @param forced   keeps a body regardless of players (game tests, operators)
     * @param group    the group of a budget of bodies it belongs to (a camp of a royal army), or empty for the citizens and
     *                 the guard, who come first
     */
    public record Candidate(UUID id, int distance, boolean forced, String group) {
        public Candidate(UUID id, int distance, boolean forced) {
            this(id, distance, forced, "");
        }

        public Candidate {
            group = group == null ? "" : group;
        }
    }

    /**
     * The bodies the groups of candidates get at most: every group {@code perGroup}, all of them together {@code total}. The
     * candidates of no group come first, so that a group never takes the body of one of them.
     */
    public record Budget(int perGroup, int total) {
        public static final Budget NONE = new Budget(Integer.MAX_VALUE, Integer.MAX_VALUE);

        public Budget {
            perGroup = Math.max(0, perGroup);
            total = Math.max(0, total);
        }
    }

    private ActiveSelection() {
    }

    /**
     * How far from a player, in chunks, an anchor of a citizen may be at most for the citizen to get or keep a body: the
     * activation distance and the slack of those that have a body.
     */
    public static int reach(int activationChunks) {
        return activationChunks > Integer.MAX_VALUE - KEEP_SLACK ? Integer.MAX_VALUE : activationChunks + KEEP_SLACK;
    }

    /**
     * Square distance in chunks from the nearest of the anchors to the nearest player in the same dimension.
     */
    public static int distance(Collection<WorldPos> anchors, Collection<PlayerSpot> players) {
        int best = Integer.MAX_VALUE;
        for (WorldPos anchor : anchors) {
            if (anchor == null) {
                continue;
            }
            for (PlayerSpot player : players) {
                if (!player.dimension().equals(anchor.dimension())) {
                    continue;
                }
                int distance = Math.max(Math.abs(player.chunkX() - anchor.chunkX()), Math.abs(player.chunkZ() - anchor.chunkZ()));
                best = Math.min(best, distance);
            }
        }
        return best;
    }

    /**
     * The places that decide whether a citizen has a body: home and workplace, or where it is if it has neither.
     */
    public static List<WorldPos> anchors(Citizen citizen) {
        return anchors(citizen, false);
    }

    /**
     * The places that decide whether a citizen has a body; with {@code homeAway} (its home lies in another city than its post,
     * where it moves as soon as there is a bed) the home does not count, only the workplace.
     */
    public static List<WorldPos> anchors(Citizen citizen, boolean homeAway) {
        List<WorldPos> anchors = new ArrayList<>(2);
        if (citizen.home() != null && !(homeAway && citizen.workplace() != null)) {
            anchors.add(citizen.home());
        }
        if (citizen.workplace() != null) {
            anchors.add(citizen.workplace());
        }
        if (anchors.isEmpty()) {
            anchors.add(citizen.lastPos());
        }
        return anchors;
    }

    /**
     * The citizens that get a body: forced ones first, then those within {@code activationChunks}, nearest first,
     * at most {@code maxActive} in total.
     */
    public static List<UUID> select(Collection<Candidate> candidates, int activationChunks, int maxActive) {
        return select(candidates, activationChunks, maxActive, Budget.NONE);
    }

    /**
     * The citizens that get a body: forced ones first, then those of no group within {@code activationChunks}, nearest
     * first, and with what is left of {@code maxActive} the candidates of groups, nearest first, within the budget of their
     * groups.
     */
    public static List<UUID> select(Collection<Candidate> candidates, int activationChunks, int maxActive, Budget budget) {
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.forced() || candidate.distance() <= activationChunks) {
                eligible.add(candidate);
            }
        }
        eligible.sort(Comparator.comparing((Candidate c) -> !c.forced())
                .thenComparingInt(Candidate::distance)
                .thenComparing(Candidate::id));
        List<UUID> result = new ArrayList<>(Math.min(eligible.size(), Math.max(0, maxActive)));
        List<Candidate> grouped = new ArrayList<>();
        for (Candidate candidate : eligible) {
            if (result.size() >= maxActive) {
                break;
            }
            if (!candidate.forced() && !candidate.group().isEmpty()) {
                grouped.add(candidate);
                continue;
            }
            result.add(candidate.id());
        }
        java.util.Map<String, Integer> perGroup = new java.util.HashMap<>();
        int total = 0;
        for (Candidate candidate : grouped) {
            if (result.size() >= maxActive || total >= budget.total()) {
                break;
            }
            int used = perGroup.getOrDefault(candidate.group(), 0);
            if (used >= budget.perGroup()) {
                continue;
            }
            perGroup.put(candidate.group(), used + 1);
            total++;
            result.add(candidate.id());
        }
        return result;
    }
}
