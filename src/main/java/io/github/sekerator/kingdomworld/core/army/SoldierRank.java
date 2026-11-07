package io.github.sekerator.kingdomworld.core.army;

import java.util.Locale;
import java.util.Optional;

/**
 * The ranks of soldiers, from the lowest. A soldier rises by experience: training at the barracks takes it up to
 * {@link ArmyRules#trainingCap}, battles all the way. The rank is a soldier's level in the model of strength.
 */
public enum SoldierRank {
    RECRUIT(0),
    PRIVATE(100),
    CORPORAL(300),
    SERGEANT(700),
    LIEUTENANT(1_500),
    CAPTAIN(3_000);

    /** Number of ranks. */
    public static final int COUNT = values().length;

    private final long experience;

    SoldierRank(long experience) {
        this.experience = experience;
    }

    /**
     * Experience a soldier needs for this rank.
     */
    public long experience() {
        return experience;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The rank of a soldier with this much experience.
     */
    public static SoldierRank of(long experience) {
        SoldierRank reached = RECRUIT;
        for (SoldierRank rank : values()) {
            if (experience >= rank.experience) {
                reached = rank;
            }
        }
        return reached;
    }

    public Optional<SoldierRank> next() {
        return ordinal() + 1 < COUNT ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public static SoldierRank ofIndex(int index) {
        return values()[Math.max(0, Math.min(COUNT - 1, index))];
    }

    public static Optional<SoldierRank> byId(String id) {
        for (SoldierRank rank : values()) {
            if (rank.id().equals(id)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
