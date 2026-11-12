package io.github.sekerator.kingdomworld.core.land;

import java.util.List;

/**
 * The roads of a world between its settlements. Immutable.
 */
public final class RoadPlan {
    private final SettlementPlan settlements;
    private final List<PlannedEdge> edges;
    private final int failed;

    RoadPlan(SettlementPlan settlements, List<PlannedEdge> edges, int failed) {
        this.settlements = settlements;
        this.edges = List.copyOf(edges);
        this.failed = failed;
    }

    public SettlementPlan settlements() {
        return settlements;
    }

    /**
     * The roads, by their numbers.
     */
    public List<PlannedEdge> edges() {
        return edges;
    }

    /**
     * Roads that were wanted but found no way over land (two capitals across the sea, say).
     */
    public int failed() {
        return failed;
    }
}
