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
    /** A lumberjack with no sapling to plant where a tree stood: none in its bag, the post's container or the storehouses. */
    NO_SAPLINGS(true),
    NO_ROCK(true),
    /** Every tunnel of the mine is dug; another goal or depth starts a new mine. */
    MINE_WORKED_OUT(true),
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
    /** A workshop without the workstation its work needs in the zone (a furnace, a crafting table). */
    NO_WORKSTATION(true),
    /** A workshop that has work but not the ingredients for it. */
    NO_INGREDIENTS(true),
    /** Furnace work without anything to burn in the storehouses. */
    NO_FUEL(true),
    /** A workshop with nothing ordered and nothing to do on its own. */
    NO_ORDERS(false),
    /** A builder without a construction of its kingdom within reach of its post. */
    NO_CONSTRUCTIONS(false),
    /** A builder whose construction lacks the materials for the next block. */
    NO_MATERIALS(true),
    /** A builder waiting while a container with items stands in the way of its construction. */
    BLOCKED(true),
    /** A builder that could not reach the place of a block and went on with another. */
    UNREACHABLE(true),
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
