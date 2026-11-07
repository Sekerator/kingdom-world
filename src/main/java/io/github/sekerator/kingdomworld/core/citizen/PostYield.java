package io.github.sekerator.kingdomworld.core.citizen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How much one worker of a work post gathers in a game day, by item id: measured while the work was watched in
 * the world, or estimated from what the zone holds until the first measurement. The abstract simulation of
 * workers far from players produces by these rates.
 *
 * @param outputs  items gathered per worker and game day, by item id
 * @param inputs   items used up per worker and game day (feed for breeding), by item id
 * @param actions  uses of the tool per worker and game day (blocks broken, fish caught), for its wear
 * @param measured whether the rates come from watching the work rather than from an estimate
 */
public record PostYield(Map<String, Double> outputs, Map<String, Double> inputs, double actions, boolean measured) {
    /** Rates are kept for this many items at most, the largest ones. */
    public static final int MAX_ITEMS = 16;
    public static final PostYield NONE = new PostYield(Map.of(), Map.of(), 0.0, false);

    public PostYield {
        outputs = clean(outputs);
        inputs = clean(inputs);
        actions = Double.isFinite(actions) ? Math.max(0.0, actions) : 0.0;
    }

    private static Map<String, Double> clean(Map<String, Double> rates) {
        Objects.requireNonNull(rates, "rates");
        Map<String, Double> result = new LinkedHashMap<>();
        rates.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null
                        && Double.isFinite(entry.getValue()) && entry.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_ITEMS)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(result);
    }

    public boolean isEmpty() {
        return outputs.isEmpty();
    }

    /**
     * All items gathered per worker and game day.
     */
    public double total() {
        double sum = 0.0;
        for (double rate : outputs.values()) {
            sum += rate;
        }
        return sum;
    }

    /**
     * The rates after a new measurement: the first measurement replaces an estimate, later ones are averaged
     * with what was known, the new one weighing {@code weight} (0 to 1).
     */
    public PostYield blend(PostYield sample, double weight) {
        if (!measured || isEmpty() && inputs.isEmpty()) {
            return new PostYield(sample.outputs, sample.inputs, sample.actions, true);
        }
        double w = Math.max(0.0, Math.min(1.0, weight));
        return new PostYield(mix(outputs, sample.outputs, w), mix(inputs, sample.inputs, w),
                actions * (1.0 - w) + sample.actions * w, true);
    }

    private static Map<String, Double> mix(Map<String, Double> old, Map<String, Double> sample, double w) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : old.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * (1.0 - w));
        }
        for (Map.Entry<String, Double> entry : sample.entrySet()) {
            result.merge(entry.getKey(), entry.getValue() * w, Double::sum);
        }
        return result;
    }

    /**
     * Rates from a measurement: what was gathered and used in {@code workedTicks} of work, scaled to a working day
     * of {@link GatheringRules#WORK_TICKS_PER_DAY}.
     */
    public static PostYield measure(Map<String, Long> gathered, Map<String, Long> used, long actions, long workedTicks) {
        if (workedTicks <= 0) {
            return NONE;
        }
        double scale = GatheringRules.WORK_TICKS_PER_DAY / (double) workedTicks;
        Map<String, Double> outputs = new LinkedHashMap<>();
        gathered.forEach((item, count) -> outputs.put(item, count * scale));
        Map<String, Double> inputs = new LinkedHashMap<>();
        used.forEach((item, count) -> inputs.put(item, count * scale));
        return new PostYield(outputs, inputs, actions * scale, true);
    }
}
