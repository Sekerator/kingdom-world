package io.github.sekerator.kingdomworld.core.road;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How builders and miners pay for the columns of a levelled road, a second at a time, and how the items dug out and counted for
 * its embankments are settled once the road is laid or given up.
 *
 * <p>Miners pay the seconds of the rock of a column first; what is left of it the builders pay at {@code rockByBuilders} of their
 * seconds for one of a miner's; then the builders pay their own seconds. The column is paid for when both are, and its items are
 * taken then, all or none. Seconds of miners not spent in a second are not saved up.
 */
public final class RoadPayment {

    private RoadPayment() {
    }

    /**
     * The seconds a kingdom has for its roads in one second, and what it spent of them.
     */
    public static final class Budget {
        /** Seconds of builders left. */
        public double builders;
        /** Seconds of miners left. */
        public double miners;
        /** Seconds of miners spent. */
        public double minersSpent;
        /** Seconds of builders spent, on rock and on their own work. */
        public double buildersSpent;
        /** Seconds of builders spent on rock the miners did not dig. */
        public double buildersOnRock;

        public Budget(double builders, double miners) {
            this.builders = Math.max(0, builders);
            this.miners = Math.max(0, miners);
        }
    }

    /** What is too little to count. */
    private static final double EPSILON = 1e-6;

    /**
     * Gives what the budget allows to the work of the next column of a section.
     *
     * @return whether the work of the column is all given and its items may be taken
     */
    public static boolean pay(Road.Section section, ColumnWork work, Budget budget, double rockByBuilders) {
        double needMiners = work.miners() - section.minerWork();
        if (needMiners > EPSILON && budget.miners > EPSILON) {
            double paid = Math.min(needMiners, budget.miners);
            section.addWork(0, paid);
            budget.miners -= paid;
            budget.minersSpent += paid;
            needMiners -= paid;
        }
        if (needMiners > EPSILON && budget.builders > EPSILON) {
            double paid = Math.min(needMiners * rockByBuilders, budget.builders);
            section.addWork(0, paid / rockByBuilders);
            budget.builders -= paid;
            budget.buildersSpent += paid;
            budget.buildersOnRock += paid;
        }
        double needBuilders = work.builders() - section.builderWork();
        if (needBuilders > EPSILON && budget.builders > EPSILON) {
            double paid = Math.min(needBuilders, budget.builders);
            section.addWork(paid, 0);
            budget.builders -= paid;
            budget.buildersSpent += paid;
        }
        return given(section, work);
    }

    /**
     * Whether all the work of the column is given.
     */
    public static boolean given(Road.Section section, ColumnWork work) {
        return section.builderWork() + EPSILON >= work.builders() && section.minerWork() + EPSILON >= work.miners();
    }

    /**
     * What is left of the items counted for the embankments of a road once it is laid or given up.
     *
     * @param toStorage items dug out and not used: they go to the storehouses
     * @param shortfall items used before they were dug and never dug: they are taken from the storehouses
     */
    public record Settlement(Map<String, Integer> toStorage, Map<String, Integer> shortfall) {
        public boolean empty() {
            return toStorage.isEmpty() && shortfall.isEmpty();
        }
    }

    /**
     * Settles the credit of a road against its debt, item by item: what was counted and not used goes to the storehouses, less
     * what was never dug; what was used and never dug is short.
     */
    public static Settlement settle(Map<String, Integer> credit, Map<String, Integer> owed) {
        Map<String, Integer> toStorage = new LinkedHashMap<>();
        Map<String, Integer> shortfall = new LinkedHashMap<>();
        java.util.Set<String> items = new java.util.LinkedHashSet<>(credit.keySet());
        items.addAll(owed.keySet());
        for (String item : items) {
            int balance = credit.getOrDefault(item, 0) - owed.getOrDefault(item, 0);
            if (balance > 0) {
                toStorage.put(item, balance);
            } else if (balance < 0) {
                shortfall.put(item, -balance);
            }
        }
        return new Settlement(toStorage, shortfall);
    }
}
