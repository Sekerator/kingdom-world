package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the goods the crown can spare are split among the shops of a kingdom on a day. Together the shops take no more of an item
 * than a share of its free stock at the start of the day of the kingdom (the stock less what the king keeps and what the
 * workshops set aside), the share split among them by what each lacks; nothing edible is taken below the rations of everybody
 * who eats from the storehouses for the days of growth.
 */
public final class RestockPlan {
    private RestockPlan() {
    }

    /**
     * What a shop wants of an item.
     */
    public record Need(long shop, String item, int category, long wanted) {
    }

    /**
     * What a shop gets.
     */
    public record Take(long shop, String item, int category, long count) {
    }

    /**
     * The split of a day.
     *
     * @param takes the goods each shop gets
     * @param short_ the shops that got less than they wanted because the crown had too little
     * @param missing the item the shops lacked most, empty when none
     */
    public record Result(List<Take> takes, java.util.Set<Long> short_, String missing) {
    }

    /**
     * Splits the goods.
     *
     * @param free         the free stock of each item at the start of the day of the kingdom
     * @param takePercent  the share of it all shops may take
     * @param nutrition    the nutrition of one of an edible item; absent for others
     * @param spareNutrition what the storehouses hold beyond the rations of everybody for the days of growth
     */
    public static Result allocate(List<Need> needs, Map<String, Long> free, int takePercent, Map<String, Integer> nutrition, long spareNutrition) {
        Map<String, List<Need>> byItem = new LinkedHashMap<>();
        for (Need need : needs) {
            if (need.wanted() > 0) {
                byItem.computeIfAbsent(need.item(), k -> new ArrayList<>()).add(need);
            }
        }
        List<Take> takes = new ArrayList<>();
        java.util.Set<Long> short_ = new java.util.HashSet<>();
        String missing = "";
        long missingMost = 0;
        long spare = Math.max(0, spareNutrition);
        for (Map.Entry<String, List<Need>> entry : byItem.entrySet()) {
            String item = entry.getKey();
            long cap = Money.share(Math.max(0, free.getOrDefault(item, 0L)), Math.max(0, Math.min(100, takePercent)), 100);
            int each = nutrition.getOrDefault(item, 0);
            if (each > 0) {
                cap = Math.min(cap, spare / each);
            }
            long wanted = 0;
            for (Need need : entry.getValue()) {
                wanted = Money.addCapped(wanted, need.wanted());
            }
            long given = 0;
            List<Need> list = entry.getValue();
            long[] counts = new long[list.size()];
            if (wanted <= cap) {
                for (int i = 0; i < list.size(); i++) {
                    counts[i] = list.get(i).wanted();
                }
                given = wanted;
            } else {
                for (int i = 0; i < list.size(); i++) {
                    counts[i] = Money.share(list.get(i).wanted(), cap, Math.max(1, wanted));
                    given += counts[i];
                }
                for (int i = 0; i < list.size() && given < cap; i++) {
                    if (counts[i] < list.get(i).wanted()) {
                        counts[i]++;
                        given++;
                    }
                }
                long lacking = wanted - given;
                if (lacking > missingMost) {
                    missingMost = lacking;
                    missing = item;
                }
            }
            for (int i = 0; i < list.size(); i++) {
                Need need = list.get(i);
                if (counts[i] < need.wanted()) {
                    short_.add(need.shop());
                }
                if (counts[i] > 0) {
                    takes.add(new Take(need.shop(), item, need.category(), counts[i]));
                }
            }
            if (each > 0) {
                spare = Math.max(0, spare - given * each);
            }
        }
        return new Result(takes, short_, missing);
    }
}
