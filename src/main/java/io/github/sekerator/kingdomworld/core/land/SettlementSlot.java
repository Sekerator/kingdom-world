package io.github.sekerator.kingdomworld.core.land;

import java.util.List;

/**
 * The place of a settlement of the plan.
 *
 * @param index      the number of the settlement in the plan, from 0
 * @param state      the state it belongs to
 * @param tier       its rank
 * @param x          the block in its middle
 * @param z          the block in its middle
 * @param cell       its cell of the survey
 * @param province   its province
 * @param parent     the settlement it belongs to (a village to its town, a town to its city), or -1 for a capital
 * @param alternates places for it near this one, the nearest first, for when the world turns this one down; each keeps the
 *                   distances from the places of the other settlements
 */
public record SettlementSlot(int index, int state, Tier tier, int x, int z, int cell, int province, int parent, List<Spot> alternates) {
    public SettlementSlot {
        alternates = List.copyOf(alternates);
    }

    /**
     * A place for a settlement.
     */
    public record Spot(int x, int z, int cell, int province) {
    }
}
