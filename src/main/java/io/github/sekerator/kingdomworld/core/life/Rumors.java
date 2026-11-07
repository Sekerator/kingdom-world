package io.github.sekerator.kingdomworld.core.life;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * How an innkeeper picks the rumors of a day from all it could tell: two to four of them, a different choice every day, one
 * of each kind first so that the talk is not all about one thing. The words for directions, distances and the strength of a
 * camp are rough on purpose: a rumor is no map.
 */
public final class Rumors {
    public static final String CAMP = "camp";
    public static final String CONTRACT = "contract";
    public static final String NEWS = "news";
    public static final String PRICE = "price";
    public static final String WANTED = "wanted";

    /** Distances are rounded to this many blocks. */
    public static final int DISTANCE_STEP = 50;

    private Rumors() {
    }

    /**
     * The rumors of a day.
     *
     * @param candidates everything true the innkeeper could tell, in a stable order
     * @param seed       the tavern's own number, so that two taverns tell different things
     */
    public static List<Rumor> pick(List<Rumor> candidates, long day, long seed, LifeRules rules) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Random random = new Random(DaySchedule.mix(seed * 31 + day));
        int wanted = rules.rumorsMin() + random.nextInt(rules.rumorsMax() - rules.rumorsMin() + 1);
        Map<String, List<Rumor>> bySource = new LinkedHashMap<>();
        for (Rumor rumor : candidates) {
            bySource.computeIfAbsent(rumor.source(), s -> new ArrayList<>()).add(rumor);
        }
        List<List<Rumor>> groups = new ArrayList<>(bySource.values());
        for (List<Rumor> group : groups) {
            java.util.Collections.shuffle(group, random);
        }
        java.util.Collections.shuffle(groups, random);
        List<Rumor> result = new ArrayList<>();
        // One of each kind in turn, until enough are told.
        for (int round = 0; result.size() < wanted; round++) {
            boolean any = false;
            for (List<Rumor> group : groups) {
                if (round < group.size() && result.size() < wanted) {
                    result.add(group.get(round));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        return result;
    }

    /**
     * The direction from one place to another as a translation key: one of eight points of the compass, or "here" when they
     * are the same place.
     */
    public static String direction(int fromX, int fromZ, int toX, int toZ) {
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        if (dx == 0 && dz == 0) {
            return "kingdomworld.life.direction.here";
        }
        // Minecraft's north is towards negative z.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        return "kingdomworld.life.direction." + names[sector];
    }

    /**
     * A distance rounded to {@link #DISTANCE_STEP} blocks, never less than one step.
     */
    public static int roughDistance(int fromX, int fromZ, int toX, int toZ) {
        double distance = Math.sqrt((double) (toX - fromX) * (toX - fromX) + (double) (toZ - fromZ) * (toZ - fromZ));
        return (int) Math.max(DISTANCE_STEP, Math.round(distance / DISTANCE_STEP) * DISTANCE_STEP);
    }

    /**
     * How strong a camp sounds: a few robbers, a band or a large band, by the people it has.
     */
    public static String strength(int size) {
        if (size <= 3) {
            return "kingdomworld.life.camp.few";
        }
        return size <= 7 ? "kingdomworld.life.camp.band" : "kingdomworld.life.camp.large";
    }
}
