package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;

/**
 * What a worker (or a work post as a whole) is doing, and if nothing, why. Shown in the work post screen and in
 * the citizens tab of the throne screen, so that a king sees what to fix: a missing tool, no water, a full store.
 */
public enum WorkStatus {
    /** Works normally. */
    WORKING(false),
    /** Nothing to report yet, for example a citizen without a profession. */
    NONE(false),
    /** Rests at night; work starts again in the morning. */
    RESTING(false),
    /** Wounded and out of work until healed. */
    WOUNDED(true),
    /** The profession needs a workplace and the citizen has none. */
    NO_WORKPLACE(true),
    /** A work post without a profession. */
    NO_PROFESSION(true),
    /** A work post nobody works at. */
    NO_WORKERS(true),
    /** The post stands outside the kingdom's land. */
    OUTSIDE_TERRITORY(true),
    /** Part of the zone lies outside the kingdom's land; work goes on in the rest. */
    ZONE_PARTLY_OUTSIDE(false),
    NO_TOOL(true),
    /** The tool there is cannot harvest what is left in the zone (for example an ore that needs iron). */
    TOOL_TOO_WEAK(true),
    NO_TREES(true),
    NO_ROCK(true),
    NO_WATER(true),
    NO_ANIMALS(true),
    /** Breeding needs food the animals eat and there is none. */
    NO_FEED(true),
    NO_CROPS(true),
    /** Nowhere to put the harvest: no container at the post and no storehouse. */
    NO_STOREHOUSE(true),
    /** The container of the post and the storehouses are full. */
    STORAGE_FULL(true),
    /** A carrier with nothing to carry. */
    NOTHING_TO_CARRY(false),
    /** Waiting for the zone to yield again (saplings growing, the herd at its size). */
    WAITING(false),
    /** The work has gone wrong in a way that does not fit the other reasons (an unknown profession). */
    UNKNOWN(true);

    private final boolean problem;

    WorkStatus(boolean problem) {
        this.problem = problem;
    }

    /**
     * Whether the status is a reason the work stands still that a player can fix.
     */
    public boolean isProblem() {
        return problem;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The status with this id, or {@link #NONE} for an unknown one (for example from a newer version).
     */
    public static WorkStatus byId(String id) {
        for (WorkStatus status : values()) {
            if (status.id().equals(id)) {
                return status;
            }
        }
        return NONE;
    }
}
