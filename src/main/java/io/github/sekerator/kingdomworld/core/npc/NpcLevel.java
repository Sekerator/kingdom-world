package io.github.sekerator.kingdomworld.core.npc;

import java.util.Locale;
import java.util.Optional;

/**
 * How large a kingdom of the world is when it appears: a small one has a village for a capital behind a palisade and one
 * town, a medium one a town with a stone wall and two towns, a large one a large city with a stone wall, a tower of mages
 * and three towns.
 */
public enum NpcLevel {
    SMALL,
    MEDIUM,
    LARGE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<NpcLevel> byId(String id) {
        for (NpcLevel level : values()) {
            if (level.id().equals(id)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    /**
     * Picks a level by weights, {@code roll} being a number from 0 (inclusive) to 1 (exclusive).
     *
     * @param weights one weight for each level, in order; all of them zero means small
     */
    public static NpcLevel pick(int[] weights, double roll) {
        long total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return SMALL;
        }
        double point = roll * total;
        long sum = 0;
        for (int i = 0; i < values().length && i < weights.length; i++) {
            sum += Math.max(0, weights[i]);
            if (point < sum) {
                return values()[i];
            }
        }
        return values()[Math.min(values().length, weights.length) - 1];
    }
}
