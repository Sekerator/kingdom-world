package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Which place far from players the server loads next to put in what was paid for there: one at a time on the whole server.
 *
 * <p>A place is worth loading when (a) everything left of it is paid for, (b) much is paid for (a quarter of what is left, 256
 * steps at least), (c) the first step paid for waits {@value #LONG_WAIT} ticks or longer, or (d) it is finished but scaffolds
 * stand where nothing is loaded; items that waited a game day to be dropped count too. The kingdoms of players and those of the
 * world take turns, one of each while both wait, so that dozens of rulers never push the construction of a player back by
 * minutes; within each half the kingdoms take turns, and within a kingdom the place paid for in full comes first, then the one
 * with the most paid for. A place that could not be loaded or finished waits {@value #DEFER} ticks before it is tried again.
 */
public final class SitePick {
    /** Steps paid for that are always worth loading. */
    public static final int MANY = 256;
    /** Ticks after which the first step paid for is worth loading anyway. */
    public static final long LONG_WAIT = 12_000L;
    /** Ticks items wait to be dropped before their place is loaded for them: a game day. */
    public static final long DROP_WAIT = 24_000L;
    /** Ticks a place that failed waits. */
    public static final long DEFER = 6_000L;

    /**
     * What is loaded: a construction, a wall or the items that wait to be dropped in a chunk.
     */
    public enum Kind {
        CONSTRUCTION,
        WALL,
        DROPS
    }

    /**
     * A place that may be loaded.
     *
     * @param id            the construction, the wall, or the chunk of the items
     * @param players       whether the kingdom is one of players
     * @param pending       steps (or columns) paid for and not yet put in
     * @param remaining     steps (or columns) not yet put in, paid for or not
     * @param since         the tick the first step now paid for was paid, or -1
     * @param scaffoldsLeft whether it is finished but scaffolds still stand
     */
    public record Candidate(Kind kind, long id, UUID kingdomId, boolean players, int pending, int remaining, long since,
                            boolean scaffoldsLeft) {
        public Ref ref() {
            return new Ref(kind, id);
        }

        /**
         * Whether everything left is paid for.
         */
        public boolean allPaid() {
            return remaining > 0 && pending >= remaining;
        }

        /**
         * Whether the place is worth loading now.
         */
        public boolean worth(long now) {
            if (kind == Kind.DROPS) {
                return since >= 0 && now - since >= DROP_WAIT;
            }
            if (scaffoldsLeft && remaining == 0) {
                return true;
            }
            if (pending <= 0) {
                return false;
            }
            return allPaid() || pending >= Math.max(MANY, remaining / 4) || since >= 0 && now - since >= LONG_WAIT;
        }
    }

    /**
     * Which place, by its kind and id.
     */
    public record Ref(Kind kind, long id) {
    }

    private final Map<Ref, Long> deferred = new HashMap<>();
    private boolean lastPlayers;
    private UUID lastPlayerKingdom;
    private UUID lastWorldKingdom;

    /**
     * The next place to load, if one is worth it.
     */
    public Optional<Candidate> pick(List<Candidate> candidates, long now) {
        deferred.values().removeIf(until -> until <= now);
        List<Candidate> players = new ArrayList<>();
        List<Candidate> world = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.worth(now) || deferred.containsKey(candidate.ref())) {
                continue;
            }
            (candidate.players() ? players : world).add(candidate);
        }
        boolean fromPlayers;
        if (!players.isEmpty() && !world.isEmpty()) {
            fromPlayers = !lastPlayers;
        } else if (!players.isEmpty()) {
            fromPlayers = true;
        } else if (!world.isEmpty()) {
            fromPlayers = false;
        } else {
            return Optional.empty();
        }
        List<Candidate> half = fromPlayers ? players : world;
        UUID kingdom = nextKingdom(half, fromPlayers ? lastPlayerKingdom : lastWorldKingdom);
        Candidate chosen = half.stream().filter(candidate -> candidate.kingdomId().equals(kingdom))
                .min(Comparator.comparing((Candidate candidate) -> !candidate.allPaid())
                        .thenComparing(Comparator.comparingInt(Candidate::pending).reversed())
                        .thenComparingLong(Candidate::id))
                .orElseThrow();
        lastPlayers = fromPlayers;
        if (fromPlayers) {
            lastPlayerKingdom = kingdom;
        } else {
            lastWorldKingdom = kingdom;
        }
        return Optional.of(chosen);
    }

    /**
     * The kingdom after the last one served, in a fixed order, the first one again after the last.
     */
    private static UUID nextKingdom(List<Candidate> half, UUID last) {
        TreeSet<UUID> kingdoms = new TreeSet<>();
        for (Candidate candidate : half) {
            kingdoms.add(candidate.kingdomId());
        }
        if (last != null) {
            UUID next = kingdoms.higher(last);
            if (next != null) {
                return next;
            }
        }
        return kingdoms.first();
    }

    /**
     * A place that could not be loaded or finished waits {@value #DEFER} ticks.
     */
    public void defer(Ref ref, long now) {
        deferred.put(ref, now + DEFER);
    }

    public boolean deferred(Ref ref, long now) {
        Long until = deferred.get(ref);
        return until != null && until > now;
    }

    public void clear() {
        deferred.clear();
        lastPlayers = false;
        lastPlayerKingdom = null;
        lastWorldKingdom = null;
    }
}
