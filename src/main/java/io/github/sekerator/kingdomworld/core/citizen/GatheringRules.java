package io.github.sekerator.kingdomworld.core.citizen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server settings of work posts and gathering, and the formulas of the work done far from players. Pure
 * functions, shared by the abstract simulation and the tests.
 *
 * @param defaultRadius     radius of the zone of a new post, in blocks (smaller if the land is smaller)
 * @param maxRadius         largest radius of a zone
 * @param lumberjackDefaultRadius radius of the zone of a lumberjack's post, in blocks; it is not cut to the land, since
 *                          the lumberjack fells and plants only on the kingdom's own chunks anyway
 * @param lumberjackMaxRadius largest radius of the zone of a lumberjack's post
 * @param maxSeats          most workers one post takes
 * @param maxTreeBlocks     most logs of one tree a lumberjack fells
 * @param mineLevels        most levels a mine has, one every {@link #MINE_LEVEL_STEP} blocks, or every
 *                          {@link #MINE_DEEP_STEP} blocks when anything is dug at the automatic depth
 * @param maxHerd           largest herd a herder keeps
 * @param carrierRange      how far from its post a carrier fetches and brings goods, in blocks
 * @param carrierItemsPerMinute items one carrier far from players moves in a minute
 * @param riskMultiplier    multiplies every risk of accidents far from players; 0 turns them off
 * @param risks             the accident risk of each profession per minute of work, by profession id
 */
public record GatheringRules(int defaultRadius, int maxRadius, int lumberjackDefaultRadius, int lumberjackMaxRadius, int maxSeats,
                             int maxTreeBlocks, int mineLevels, int maxHerd, int carrierRange, int carrierItemsPerMinute,
                             double riskMultiplier, Map<String, TaskRisks.Risk> risks) {
    /** Ticks of a game day spent working; workers rest at night. */
    public static final long WORK_TICKS_PER_DAY = CitizenNeeds.TICKS_PER_DAY / 2;
    /** Blocks between two levels of a mine. */
    public static final int MINE_LEVEL_STEP = 4;
    /** Blocks between two levels of a mine that digs anything at the automatic depth: it goes for the depths. */
    public static final int MINE_DEEP_STEP = 16;
    /** A worker without a container at its post carries its harvest to the storehouse itself and gathers less. */
    public static final double SELF_CARRY_FACTOR = 0.75;
    /** Weight of a new measurement when it is averaged with the rates known before. */
    public static final double MEASURE_WEIGHT = 0.5;
    /** Least time of watched work, in ticks, that makes a measurement. */
    public static final long MEASURE_TICKS = 2_400L;

    public static final GatheringRules DEFAULT = new GatheringRules(8, 16, 16, 32, 4, 160, 8, 12, 48, 64, 1.0, TaskRisks.DEFAULTS);

    public GatheringRules {
        if (defaultRadius < 1 || maxRadius < defaultRadius || lumberjackDefaultRadius < 1 || lumberjackMaxRadius < lumberjackDefaultRadius
                || maxSeats < 1 || maxTreeBlocks < 1 || mineLevels < 1 || maxHerd < 2 || carrierRange < 1 || carrierItemsPerMinute < 0) {
            throw new IllegalArgumentException("Gathering rules out of range");
        }
        if (!Double.isFinite(riskMultiplier) || riskMultiplier < 0.0) {
            throw new IllegalArgumentException("Risk multiplier must not be negative: " + riskMultiplier);
        }
        risks = Map.copyOf(Objects.requireNonNull(risks, "risks"));
    }

    public TaskRisks.Risk risk(String professionId) {
        return risks.getOrDefault(professionId, TaskRisks.Risk.NONE);
    }

    /**
     * Result of one step of abstract work: whole items to hand out and the fractions carried to the next step.
     */
    public record Output(Map<String, Integer> items, Map<String, Double> progress, int actions) {
    }

    /**
     * What a number of workers gather during one simulation step far from players.
     *
     * @param rates    per worker and game day
     * @param workers  workers that worked this step
     * @param factor   share of the full rate (for example {@link #SELF_CARRY_FACTOR})
     * @param progress fractions left from earlier steps, by item id; the key {@code ""} carries tool uses
     */
    public static Output abstractOutput(PostYield rates, int workers, int intervalTicks, double factor,
                                        Map<String, Double> progress) {
        double share = Math.max(0, workers) * Math.max(0.0, factor) * intervalTicks / (double) CitizenNeeds.TICKS_PER_DAY;
        Map<String, Integer> items = new LinkedHashMap<>();
        Map<String, Double> left = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : rates.outputs().entrySet()) {
            double total = progress.getOrDefault(entry.getKey(), 0.0) + entry.getValue() * share;
            int whole = (int) Math.min(Math.floor(total), 64.0 * 27);
            if (whole > 0) {
                items.put(entry.getKey(), whole);
            }
            left.put(entry.getKey(), total - whole);
        }
        double uses = progress.getOrDefault("", 0.0) + rates.actions() * share;
        int actions = (int) Math.min(Math.floor(uses), 100_000.0);
        left.put("", uses - actions);
        return new Output(items, left, actions);
    }

    /**
     * Items used up during one step, by item id, rounded up so that work never runs on nothing.
     */
    public static Map<String, Integer> abstractInputs(PostYield rates, int workers, int intervalTicks, double factor) {
        double share = Math.max(0, workers) * Math.max(0.0, factor) * intervalTicks / (double) CitizenNeeds.TICKS_PER_DAY;
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : rates.inputs().entrySet()) {
            int count = (int) Math.ceil(entry.getValue() * share - 1e-9);
            if (count > 0) {
                result.put(entry.getKey(), count);
            }
        }
        return result;
    }

    /**
     * Share of the work that can be done when only part of the needed inputs is there: 1 if every input is
     * complete, otherwise the smallest share among them.
     *
     * @param available how many of each needed item could be had
     */
    public static double inputShare(Map<String, Integer> needed, Map<String, Integer> available) {
        double share = 1.0;
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            if (entry.getValue() > 0) {
                share = Math.min(share, available.getOrDefault(entry.getKey(), 0) / (double) entry.getValue());
            }
        }
        return Math.max(0.0, share);
    }

    /**
     * Items a number of carriers far from players move during one step.
     */
    public int carried(int carriers, int intervalTicks) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, carriers) * carrierItemsPerMinute * intervalTicks
                / CitizenNeeds.REFERENCE_STEP_TICKS);
    }

    /**
     * The radius a new post gets: the default one, or less if the land around the post is smaller.
     *
     * @param largestInside largest radius whose zone lies entirely on the kingdom's land
     */
    public int radiusForNewPost(int largestInside) {
        return Math.max(1, Math.min(defaultRadius, largestInside));
    }

    /**
     * Whether the zone of a post of the profession must lie entirely on the kingdom's land. A lumberjack's zone need not:
     * it fells and plants only on the kingdom's own chunks of it, so a larger zone near the border loses nothing.
     */
    public static boolean boundToLand(String professionId) {
        return !Professions.LUMBERJACK.id().equals(professionId);
    }

    /**
     * The radius a post of the profession starts with.
     */
    public int defaultRadius(String professionId) {
        return Professions.LUMBERJACK.id().equals(professionId) ? lumberjackDefaultRadius : defaultRadius;
    }

    /**
     * The largest radius of a post of the profession.
     */
    public int maxRadius(String professionId) {
        return Professions.LUMBERJACK.id().equals(professionId) ? lumberjackMaxRadius : maxRadius;
    }

    /**
     * The radius a new post of the profession gets: its default one, cut to the land where the profession needs that.
     *
     * @param largestInside largest radius whose zone lies entirely on the kingdom's land
     */
    public int radiusForNewPost(String professionId, int largestInside) {
        int radius = defaultRadius(professionId);
        return Math.max(1, boundToLand(professionId) ? Math.min(radius, largestInside) : radius);
    }

    /**
     * The radius a post keeps when it takes up a profession: at most the largest one of the new profession, and for a
     * lumberjack at least its own default, since a grove needs room.
     */
    public int radiusOnProfession(String professionId, int radius) {
        int result = Math.min(radius, maxRadius(professionId));
        if (Professions.LUMBERJACK.id().equals(professionId)) {
            result = Math.max(result, lumberjackDefaultRadius);
        }
        return Math.max(1, result);
    }
}
