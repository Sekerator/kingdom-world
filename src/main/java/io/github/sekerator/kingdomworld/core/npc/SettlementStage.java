package io.github.sekerator.kingdomworld.core.npc;

import java.util.Locale;

/**
 * The stages a settlement of a state is raised in the world by, in order. A settlement keeps its stage and the step within it,
 * so that a restart goes on from there; every step looks at the world first.
 */
public enum SettlementStage {
    /** Its chunks are asked for and loaded. */
    LOAD,
    /** Its land is looked at: another kingdom, chunks where players lived long, what may not be broken. */
    CHECK,
    /** Its ground is levelled with its square and its street. */
    GROUND,
    /** Its town hall and its buildings go up. */
    BUILD,
    /** Its fields are sown. */
    WORK,
    /** The chunks of its square are looked through for beds and posts. */
    PLACES,
    /** The stores it is founded with come, once. */
    WEALTH,
    /** Its named citizens arrive and take its work. */
    PEOPLE,
    /** Its guard comes from abroad. */
    GUARDS,
    /** The road to the settlement it belongs to (the roads of the plan lay it). */
    ROADS,
    /** Its wall goes up. */
    WALL,
    /** Its town takes its laws and the posts of its guard. */
    LAW,
    /** It stands. */
    DONE;

    private static final SettlementStage[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The stage of an id; the first for an empty or unknown one.
     */
    public static SettlementStage byId(String id) {
        for (SettlementStage stage : VALUES) {
            if (stage.id().equals(id)) {
                return stage;
            }
        }
        return LOAD;
    }

    public SettlementStage next() {
        return this == DONE ? DONE : VALUES[ordinal() + 1];
    }
}
