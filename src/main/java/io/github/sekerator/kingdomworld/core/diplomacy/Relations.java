package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How pairs of kingdoms stand with each other: a number from -100 to 100 with the reasons behind it. Rulers of the world add
 * their own memory of a neighbour (deals, roads, land) on top of this.
 */
public final class Relations {
    /** How far a relation goes either way. */
    public static final int MAX = 100;

    /**
     * Why two kingdoms think better or worse of each other.
     */
    public enum Reason {
        /** A pact or another treaty was made. */
        TREATY(10),
        /** They became allies. */
        ALLIANCE(15),
        /** An ally came when called. */
        CALL_ANSWERED(10),
        /** An ally did not come when called. */
        CALL_REFUSED(-20),
        /** A treaty with this kingdom was broken. */
        BROKEN_TREATY(-30),
        /** A treaty with somebody else was broken, and word got here. */
        BROKEN_WORD(-10),
        /** An offer was declined. */
        DECLINED(-3),
        /** War was declared. */
        WAR(-40),
        /** Peace was made. */
        PEACE(15),
        /** A vassal was let go in peace. */
        RELEASED(10);

        private final int weight;

        Reason(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Reason> byId(String id) {
            for (Reason reason : values()) {
                if (reason.id().equals(id)) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The relation of one pair.
     */
    public static final class Relation {
        private int score;
        private final EnumMap<Reason, Integer> reasons = new EnumMap<>(Reason.class);

        public int score() {
            return score;
        }

        public Map<Reason, Integer> reasons() {
            return Collections.unmodifiableMap(reasons);
        }

        /**
         * The reason that weighs most, if any.
         */
        public Optional<Reason> main() {
            Reason best = null;
            int weight = 0;
            for (Map.Entry<Reason, Integer> entry : reasons.entrySet()) {
                int value = Math.abs(entry.getKey().weight() * entry.getValue());
                if (value > weight) {
                    weight = value;
                    best = entry.getKey();
                }
            }
            return Optional.ofNullable(best);
        }

        void add(Reason reason) {
            reasons.merge(reason, 1, Integer::sum);
            score = clamp(score + reason.weight());
        }
    }

    private final Map<String, Relation> pairs = new LinkedHashMap<>();

    public static String key(UUID a, UUID b) {
        return Treaty.key(a, b);
    }

    public int score(UUID a, UUID b) {
        Relation relation = pairs.get(key(a, b));
        return relation == null ? 0 : relation.score;
    }

    public Optional<Relation> get(UUID a, UUID b) {
        return Optional.ofNullable(pairs.get(key(a, b)));
    }

    public Map<String, Relation> all() {
        return Collections.unmodifiableMap(pairs);
    }

    void add(UUID a, UUID b, Reason reason) {
        if (a.equals(b)) {
            return;
        }
        pairs.computeIfAbsent(key(a, b), k -> new Relation()).add(reason);
    }

    /**
     * Whether a kingdom has dealings with another at all: some relation on record.
     */
    public boolean knows(UUID a, UUID b) {
        return pairs.containsKey(key(a, b));
    }

    /**
     * Feelings fade a little every day: each relation moves one step back towards nothing.
     */
    void fade() {
        for (Relation relation : pairs.values()) {
            relation.score -= Integer.signum(relation.score);
        }
    }

    void forget(UUID kingdom) {
        String id = kingdom.toString();
        pairs.keySet().removeIf(key -> key.startsWith(id + "|") || key.endsWith("|" + id));
    }

    public void restore(String key, int score, Map<Reason, Integer> reasons) {
        Relation relation = new Relation();
        relation.score = clamp(score);
        reasons.forEach((reason, count) -> {
            if (count != null && count > 0) {
                relation.reasons.put(reason, count);
            }
        });
        pairs.put(key, relation);
    }

    void clear() {
        pairs.clear();
    }

    static int clamp(int value) {
        return Math.max(-MAX, Math.min(MAX, value));
    }
}
