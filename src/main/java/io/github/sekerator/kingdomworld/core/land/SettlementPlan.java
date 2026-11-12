package io.github.sekerator.kingdomworld.core.land;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The settlements of all states of a world: their ranks, places and the settlements they belong to. Immutable.
 */
public final class SettlementPlan {
    private final ProvincePlan provinces;
    private final SettlementRules rules;
    private final List<SettlementSlot> slots;
    private final int[][] wanted;
    private final int[] seats;

    SettlementPlan(ProvincePlan provinces, SettlementRules rules, List<SettlementSlot> slots, int[][] wanted) {
        this.provinces = provinces;
        this.rules = rules;
        this.slots = List.copyOf(slots);
        this.wanted = new int[wanted.length][];
        for (int s = 0; s < wanted.length; s++) {
            this.wanted[s] = wanted[s].clone();
        }
        this.seats = new int[provinces.provinces().size() + 1];
        Arrays.fill(seats, -1);
        for (SettlementSlot slot : this.slots) {
            int seat = seats[slot.province()];
            if (seat < 0 || slot.tier().above(this.slots.get(seat).tier())) {
                seats[slot.province()] = slot.index();
            }
        }
        seats[0] = -1;
    }

    public SurveyRaster raster() {
        return provinces.raster();
    }

    public ProvincePlan provinces() {
        return provinces;
    }

    public SettlementRules rules() {
        return rules;
    }

    /**
     * All settlements, by their numbers: every state's capital first, then its cities, towns, villages and hamlets.
     */
    public List<SettlementSlot> slots() {
        return slots;
    }

    public SettlementSlot slot(int index) {
        return slots.get(index);
    }

    public List<SettlementSlot> slotsOf(int state) {
        List<SettlementSlot> list = new ArrayList<>();
        for (SettlementSlot slot : slots) {
            if (slot.state() == state) {
                list.add(slot);
            }
        }
        return list;
    }

    /**
     * Settlements of a rank a state has.
     */
    public int count(int state, Tier tier) {
        int count = 0;
        for (SettlementSlot slot : slots) {
            if (slot.state() == state && slot.tier() == tier) {
                count++;
            }
        }
        return count;
    }

    /**
     * Settlements of a rank the seed of the world gave a state; fewer stand when the land has no room for them.
     */
    public int wanted(int state, Tier tier) {
        return wanted[state][tier.ordinal()];
    }

    /**
     * The main settlement of a province (its highest rank, the first on a tie), or -1 for a province without settlements.
     */
    public int seat(int province) {
        return province <= 0 || province >= seats.length ? -1 : seats[province];
    }
}
