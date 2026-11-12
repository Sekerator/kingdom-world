package io.github.sekerator.kingdomworld.core.land;

import java.util.ArrayList;
import java.util.List;

/**
 * The provinces of a world over its survey: the provinces of every state and the wild provinces of the neutral land. Immutable.
 */
public final class ProvincePlan {
    private final StatePlan states;
    private final ProvinceRules rules;
    private final long seed;
    private final int[] province;
    private final List<PlannedProvince> provinces;
    private final int[] thrones;
    private final List<List<Integer>> byState;

    ProvincePlan(StatePlan states, ProvinceRules rules, long seed, int[] province, List<PlannedProvince> provinces, int[] thrones) {
        this.states = states;
        this.rules = rules;
        this.seed = seed;
        this.province = province;
        this.provinces = List.copyOf(provinces);
        this.thrones = thrones.clone();
        List<List<Integer>> lists = new ArrayList<>();
        for (int s = 0; s < states.states().size(); s++) {
            lists.add(new ArrayList<>());
        }
        for (PlannedProvince p : provinces) {
            if (p.state() >= 0) {
                lists.get(p.state()).add(p.id());
            }
        }
        List<List<Integer>> frozen = new ArrayList<>();
        for (List<Integer> list : lists) {
            frozen.add(List.copyOf(list));
        }
        this.byState = List.copyOf(frozen);
    }

    public SurveyRaster raster() {
        return states.raster();
    }

    public StatePlan states() {
        return states;
    }

    public ProvinceRules rules() {
        return rules;
    }

    public long seed() {
        return seed;
    }

    /**
     * The province of a cell, or 0.
     */
    public int provinceAt(int cell) {
        return province[cell];
    }

    /**
     * A province by its number, from 1.
     */
    public PlannedProvince province(int id) {
        return provinces.get(id - 1);
    }

    /**
     * All provinces, in the order of their numbers.
     */
    public List<PlannedProvince> provinces() {
        return provinces;
    }

    /**
     * The state of a province, or -1 for a wild one and for 0.
     */
    public int stateOf(int id) {
        return id <= 0 ? -1 : provinces.get(id - 1).state();
    }

    /**
     * The provinces of a state, its heart first.
     */
    public List<Integer> provincesOf(int state) {
        return byState.get(state);
    }

    /**
     * The province of the capital of a state, or 0 for a state that got no land.
     */
    public int heart(int state) {
        List<Integer> list = byState.get(state);
        return list.isEmpty() ? 0 : list.get(0);
    }

    /**
     * The cell of the throne of a state.
     */
    public int throneCell(int state) {
        return thrones[state];
    }
}
