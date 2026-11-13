package io.github.sekerator.kingdomworld.core.tactics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the leader of a squad decided: a duty for each fighter, the enemy the squad strikes first, the shape it fights in
 * and whether it falls back.
 *
 * @param focus     the enemy most of the squad strikes, or {@code null}
 * @param formation the shape of the squad
 * @param retreat   whether the squad falls back towards help
 */
public record Plan(UUID leader, List<Duty> duties, UUID focus, Formation formation, boolean retreat) {
    public static final Plan NONE = new Plan(null, List.of(), null, Formation.NONE, false);

    public Plan {
        duties = List.copyOf(duties);
    }

    public Optional<Duty> duty(UUID fighter) {
        for (Duty duty : duties) {
            if (duty.fighter().equals(fighter)) {
                return Optional.of(duty);
            }
        }
        return Optional.empty();
    }

    /** The duties by fighter. */
    public Map<UUID, Duty> byFighter() {
        Map<UUID, Duty> map = new HashMap<>();
        duties.forEach(duty -> map.put(duty.fighter(), duty));
        return map;
    }

    /** How many fighters have a role. */
    public long count(Role role) {
        return duties.stream().filter(duty -> duty.role() == role).count();
    }

    /**
     * The shape of a squad in a fight.
     */
    public enum Formation {
        NONE,
        /** A ring around one enemy. */
        RING,
        /** A line facing a group of enemies, with wings going round its sides. */
        PINCER,
        /** A line facing the enemy, shields in front and archers behind. */
        LINE,
        /** Falling back in order. */
        COLUMN
    }
}
