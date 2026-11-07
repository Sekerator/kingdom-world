package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * What a kingdom of the world is made of when it appears: the buildings of its capital and where they stand on the grid
 * around the throne, its towns, the class of its wall and roads, its citizens and their work.
 *
 * <p>The capital is a grid of plots {@value #SPACING} blocks apart around the square of the throne; ring {@code k} of the grid
 * holds {@code 8k} plots. Houses hold the citizens (a bed is a citizen), so the number of beds decides the level of the capital
 * as a city: a small kingdom stays a village, a medium one is a town and a large one a large city by the thresholds of cities.
 *
 * @param buildings the buildings of the capital in the order they are put up, each on its plot
 * @param citizens  the citizens of the kingdom, as many as the capital and its towns have beds
 * @param jobs      the work of the citizens by profession, soldiers first; whoever is left is without work
 */
public record NpcComposition(NpcLevel level, List<Plot> buildings, int rings, int towns, int townHouses, boolean stoneWall,
                             String roadClass, int citizens, Map<String, Integer> jobs, int soldiers, int fields) {
    /** Blocks between the centres of two plots of the capital. */
    public static final int SPACING = 13;
    /** The widest building on a plot, in blocks. */
    public static final int PLOT = 11;

    public static final String SMALL_HOUSE = "kingdomworld:small_house";
    public static final String LARGE_HOUSE = "kingdomworld:large_house";
    public static final String STOREHOUSE = "kingdomworld:storehouse";
    public static final String WORKSHOP = "kingdomworld:workshop";
    public static final String MARKET = "kingdomworld:market_square";
    public static final String WELL = "kingdomworld:well";
    public static final String WATCHTOWER = "kingdomworld:watchtower";
    public static final String BARRACKS = "kingdomworld:barracks";
    public static final String MAGE_TOWER = "kingdomworld:mage_tower";

    /** Beds in the buildings that have them. */
    private static final Map<String, Integer> BEDS = Map.of(SMALL_HOUSE, 1, LARGE_HOUSE, 3, BARRACKS, 4, MAGE_TOWER, 1);

    /**
     * A building on a plot of the grid around the throne.
     *
     * @param gx     the column of the plot, from -rings to rings
     * @param gz     the row of the plot
     * @param facing where its front looks: 0 south, 1 west, 2 north, 3 east (always towards the throne)
     */
    public record Plot(String template, int gx, int gz, int facing) {
        public int x() {
            return gx * SPACING;
        }

        public int z() {
            return gz * SPACING;
        }
    }

    public NpcComposition {
        buildings = List.copyOf(buildings);
        jobs = Collections.unmodifiableMap(new LinkedHashMap<>(jobs));
    }

    public static int beds(String template) {
        return BEDS.getOrDefault(template, 0);
    }

    /**
     * Houses of a town and their beds: small houses, every third one large.
     */
    public static List<String> townBuildings(int houses) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < houses; i++) {
            list.add(i % 3 == 2 ? LARGE_HOUSE : SMALL_HOUSE);
        }
        list.add(WELL);
        return list;
    }

    /**
     * The composition of a kingdom of a level.
     *
     * @param cityPeople    the citizens a large city needs
     * @param cityBuildings the buildings a large city needs
     * @param townPeople    the citizens a town needs
     * @param townBuildings the buildings a town needs
     * @param wallRadius    the radius of the walls of a village; a town has half as much again, a large city twice as much
     */
    public static NpcComposition of(NpcLevel level, long seed, NpcRules rules, int townPeople, int townBuildings, int cityPeople,
                                    int cityBuildings, int wallRadius) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x434F4D50L);
        List<String> list = new ArrayList<>();
        int radius;
        int soldiers = rules.soldiers(level);
        int barracks = soldiers > 4 ? 2 : soldiers > 0 ? 1 : 0;
        switch (level) {
            case SMALL -> {
                radius = wallRadius;
                list.addAll(List.of(MARKET, STOREHOUSE, WELL, WORKSHOP));
                for (int i = 0; i < barracks; i++) {
                    list.add(BARRACKS);
                }
                list.add(WATCHTOWER);
                list.add(LARGE_HOUSE);
            }
            case MEDIUM -> {
                radius = wallRadius * 3 / 2;
                list.addAll(List.of(MARKET, STOREHOUSE, WELL, WORKSHOP, WORKSHOP));
                for (int i = 0; i < barracks; i++) {
                    list.add(BARRACKS);
                }
                list.addAll(List.of(WATCHTOWER, WATCHTOWER));
            }
            default -> {
                radius = wallRadius * 2;
                list.addAll(List.of(MARKET, STOREHOUSE, WELL, WORKSHOP, WORKSHOP, STOREHOUSE, WELL, WORKSHOP, MAGE_TOWER));
                for (int i = 0; i < barracks; i++) {
                    list.add(BARRACKS);
                }
                list.addAll(List.of(WATCHTOWER, WATCHTOWER, WATCHTOWER, WATCHTOWER));
            }
        }
        int rings = Math.max(1, (radius - 3 - PLOT / 2 - 1) / SPACING);
        int plots = 4 * rings * (rings + 1);
        int towns = rules.towns(level);
        int townHouses = level == NpcLevel.SMALL ? 3 : 4;
        int townBeds = 0;
        for (String building : townBuildings(townHouses)) {
            townBeds += beds(building);
        }
        townBeds *= towns;
        // Houses until the capital has the people and buildings of its level, and at least a house for everyone at work.
        int wantPeople = switch (level) {
            case SMALL -> Math.min(townPeople - 1, 9);
            case MEDIUM -> Math.max(townPeople, 16);
            case LARGE -> Math.max(cityPeople, 40);
        };
        int wantBuildings = switch (level) {
            case SMALL -> 0;
            case MEDIUM -> townBuildings;
            case LARGE -> cityBuildings;
        };
        while (list.size() < plots && (bedsOf(list) < wantPeople || list.size() < wantBuildings)) {
            list.add(level == NpcLevel.SMALL && random.nextBoolean() ? SMALL_HOUSE : LARGE_HOUSE);
        }
        while (list.size() > plots) {
            list.remove(list.size() - 1);
        }
        int citizens = bedsOf(list) + townBeds;
        Map<String, Integer> jobs = new LinkedHashMap<>();
        int left = citizens;
        int soldierCount = Math.min(soldiers, left);
        left -= soldierCount;
        int fields = 0;
        List<String[]> wanted = switch (level) {
            case SMALL -> List.of(new String[]{"farmer", "2"}, new String[]{"merchant", "1"}, new String[]{"crafter", "1"},
                    new String[]{"lumberjack", "1"}, new String[]{"carrier", "1"}, new String[]{"registrar", "1"},
                    new String[]{"farmer", "1"}, new String[]{"builder", "1"});
            case MEDIUM -> List.of(new String[]{"farmer", "3"}, new String[]{"merchant", "1"}, new String[]{"crafter", "1"},
                    new String[]{"smith", "1"}, new String[]{"lumberjack", "1"}, new String[]{"miner", "1"}, new String[]{"carrier", "1"},
                    new String[]{"builder", "1"}, new String[]{"minter", "1"}, new String[]{"registrar", "1"}, new String[]{"farmer", "1"},
                    new String[]{"lumberjack", "1"});
            default -> List.of(new String[]{"farmer", "6"}, new String[]{"merchant", "2"}, new String[]{"crafter", "1"},
                    new String[]{"smith", "1"}, new String[]{"cook", "1"}, new String[]{"lumberjack", "3"}, new String[]{"miner", "2"},
                    new String[]{"carrier", "2"}, new String[]{"builder", "2"}, new String[]{"minter", "1"}, new String[]{"registrar", "1"},
                    new String[]{"mage", "2"}, new String[]{"farmer", "4"}, new String[]{"miner", "2"}, new String[]{"carrier", "1"});
        };
        for (String[] job : wanted) {
            int count = Math.min(Integer.parseInt(job[1]), left);
            if (count > 0) {
                jobs.merge(job[0], count, Integer::sum);
                left -= count;
            }
        }
        Integer farmers = jobs.get("farmer");
        if (farmers != null) {
            fields = (farmers + 1) / 2;
        }
        List<Plot> placed = arrange(list, rings);
        String road = switch (level) {
            case SMALL -> "path";
            case MEDIUM -> "gravel";
            case LARGE -> "paved";
        };
        return new NpcComposition(level, placed, rings, towns, townHouses, level != NpcLevel.SMALL, road, citizens, jobs, soldierCount,
                fields);
    }

    private static int bedsOf(List<String> buildings) {
        int beds = 0;
        for (String building : buildings) {
            beds += beds(building);
        }
        return beds;
    }

    /**
     * Puts the buildings on the plots of the grid: watchtowers on the corners of the outer ring, the rest from the throne
     * outwards in the order given; every front looks towards the throne.
     */
    public static List<Plot> arrange(List<String> buildings, int rings) {
        List<int[]> free = new ArrayList<>();
        for (int ring = 1; ring <= rings; ring++) {
            for (int gx = -ring; gx <= ring; gx++) {
                for (int gz = -ring; gz <= ring; gz++) {
                    if (Math.max(Math.abs(gx), Math.abs(gz)) == ring) {
                        free.add(new int[]{gx, gz});
                    }
                }
            }
        }
        // Within a ring, the middles of the sides first, so that the square of the throne opens to the streets.
        free.sort((a, b) -> {
            int ra = Math.max(Math.abs(a[0]), Math.abs(a[1]));
            int rb = Math.max(Math.abs(b[0]), Math.abs(b[1]));
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return Integer.compare(Math.abs(a[0]) + Math.abs(a[1]), Math.abs(b[0]) + Math.abs(b[1]));
        });
        List<Plot> result = new ArrayList<>();
        List<String> towers = new ArrayList<>();
        for (String building : buildings) {
            if (building.equals(WATCHTOWER)) {
                towers.add(building);
            }
        }
        int[][] corners = {{rings, rings}, {-rings, -rings}, {rings, -rings}, {-rings, rings}};
        for (int i = 0; i < towers.size() && i < corners.length; i++) {
            int[] corner = corners[i];
            free.removeIf(cell -> cell[0] == corner[0] && cell[1] == corner[1]);
            result.add(new Plot(WATCHTOWER, corner[0], corner[1], facing(corner[0], corner[1])));
        }
        int skipped = Math.max(0, towers.size() - corners.length);
        for (String building : buildings) {
            if (building.equals(WATCHTOWER)) {
                if (skipped-- > 0 && !free.isEmpty()) {
                    int[] cell = free.remove(free.size() - 1);
                    result.add(new Plot(WATCHTOWER, cell[0], cell[1], facing(cell[0], cell[1])));
                }
                continue;
            }
            if (free.isEmpty()) {
                break;
            }
            int[] cell = free.remove(0);
            result.add(new Plot(building, cell[0], cell[1], facing(cell[0], cell[1])));
        }
        return result;
    }

    /**
     * Where the front of a building on a plot looks so that it faces the throne.
     */
    public static int facing(int gx, int gz) {
        if (Math.abs(gz) >= Math.abs(gx)) {
            return gz < 0 ? 0 : 2;
        }
        return gx < 0 ? 3 : 1;
    }

    public int buildingsOf(String template) {
        int count = 0;
        for (Plot plot : buildings) {
            if (plot.template().equals(template)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The radius of the square of the capital the buildings take, in blocks from the throne.
     */
    public int reach() {
        return rings * SPACING + PLOT / 2 + 1;
    }
}
