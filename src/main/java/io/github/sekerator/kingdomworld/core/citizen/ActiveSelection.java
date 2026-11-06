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
     */
    public record Candidate(UUID id, int distance, boolean forced) {
    }

    private ActiveSelection() {
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
        List<WorldPos> anchors = new ArrayList<>(2);
        if (citizen.home() != null) {
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
        for (Candidate candidate : eligible) {
            if (result.size() >= maxActive) {
                break;
            }
            result.add(candidate.id());
        }
        return result;
    }
}
