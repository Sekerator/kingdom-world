package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * What a kingdom of the world is made of when it appears: the buildings of its capital and where they stand around the
 * throne, its towns, the class of its wall and roads, its citizens and their work. The same for the same seed, so that a
 * raising interrupted by a restart goes on with the same buildings in the same places.
 *
 * <p>There are two layouts, and a place keeps the one it was planned with. The first ({@link NpcSite#LAYOUT_GRID}) is a grid
 * of plots {@value #SPACING} blocks apart around the square of the throne, ring {@code k} holding {@code 8k} plots, every
 * front towards the throne. The second ({@link NpcSite#LAYOUT_DISTRICTS}) is a capital of districts: a palace around the
 * throne, avenues to the gates, streets with buildings of many kinds on them ({@link CityPlan}).
 *
 * @param layout     the layout the composition was made for
 * @param seed       the seed of the place
 * @param buildings  the buildings of the capital in the order they are put up
 * @param citizens   the citizens of the kingdom: one for every bed of a house and every soldier
 * @param jobs       the work of the citizens by profession; whoever is left is without work
 * @param fields     fields of the capital outside its wall
 * @param wallRadius the radius of the wall of the capital, or 0 if it follows the level of the capital as a city
 * @param gates      where the wall has its gates (the capital of districts only)
 * @param posts      the posts of the guards, by name (the capital of districts only)
 * @param lots       free ground for buildings to come, levelled with the capital
 * @param fieldSpots the middles of the fields outside the wall and, after them, of the posts of woodcutters and miners
 * @param bunks      beds for soldiers in the barracks of the capital and the towns
 */
public record NpcComposition(int layout, NpcLevel level, long seed, List<Plot> buildings, int rings, int towns, int townHouses,
                             boolean stoneWall, String roadClass, int citizens, Map<String, Integer> jobs, int soldiers, int fields,
                             int wallRadius, List<Gate> gates, List<Post> posts, List<Lot> lots, List<int[]> fieldSpots, int bunks) {
    /** Blocks between the centres of two plots of the capital of the first layout. */
    public static final int SPACING = 13;
    /** The widest building on a plot of the first layout, in blocks. */
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
    /** The tavern of a medium kingdom; a large one has the tavern with rooms, a level higher. */
    public static final String TAVERN = "kingdomworld:tavern";
    public static final String TAVERN_2 = "kingdomworld:tavern_2";

    /** Ground of a capital of districts: a yard, where buildings and gardens are. */
    public static final int GROUND_YARD = CityPlan.YARD;
    /** A street between rows of buildings. */
    public static final int GROUND_STREET = CityPlan.STREET;
    /** An avenue from the palace to a gate. */
    public static final int GROUND_AVENUE = CityPlan.AVENUE_GROUND;
    /** The square around the palace. */
    public static final int GROUND_SQUARE = CityPlan.SQUARE;
    /** The way of the guards along the wall. */
    public static final int GROUND_WALK = CityPlan.WALK;
    /** A green with a tree at an open corner. */
    public static final int GROUND_GREEN = CityPlan.GREEN;

    /**
     * A building of the capital.
     *
     * @param x       the middle of the ground it takes, in blocks east of the throne
     * @param z       the same to the south
     * @param facing  where its front looks: 0 south, 1 west, 2 north, 3 east
     * @param mirror  whether it is built mirrored
     * @param quarter the district it stands in, or empty
     */
    public record Plot(String template, int x, int z, int facing, boolean mirror, String quarter) {
        /**
         * The column of the plot on the grid of the first layout.
         */
        public int gx() {
            return Math.floorDiv(x + SPACING / 2, SPACING);
        }

        /**
         * The row of the plot on the grid of the first layout.
         */
        public int gz() {
            return Math.floorDiv(z + SPACING / 2, SPACING);
        }
    }

    /**
     * A gate of the wall.
     *
     * @param x    its middle, in blocks from the throne
     * @param side 0 north, 1 east, 2 south, 3 west
     */
    public record Gate(int x, int z, int side) {
    }

    /**
     * A post of the guards: a place to stand, or the corners of a round to walk.
     *
     * @param points the places, as {x, z} from the throne
     */
    public record Post(String name, List<int[]> points) {
        public Post {
            points = List.copyOf(points);
        }
    }

    /**
     * Free ground for a building to come.
     *
     * @param x      its middle, in blocks from the throne
     * @param facing where a front on it looks
     * @param width  blocks along the street
     * @param depth  blocks from the street back
     */
    public record Lot(int x, int z, int facing, int width, int depth) {
    }

    public NpcComposition {
        buildings = List.copyOf(buildings);
        jobs = Collections.unmodifiableMap(new LinkedHashMap<>(jobs));
        gates = List.copyOf(gates);
        posts = List.copyOf(posts);
        lots = List.copyOf(lots);
        fieldSpots = List.copyOf(fieldSpots);
    }

    /**
     * Beds in a template the plans know: one for each citizen who lives or sleeps there.
     *
     * @throws IllegalArgumentException for a template the plans do not know, so that a new building never counts for nothing
     */
    public static int beds(String template) {
        return TemplateCatalog.of(template).beds();
    }

    /**
     * Whether the beds of a template are for soldiers.
     */
    public static boolean bunks(String template) {
        return TemplateCatalog.get(template).map(TemplateCatalog::bunks).orElse(false);
    }

    /**
     * The ground a building takes: {minX, minZ, maxX, maxZ} in blocks from the throne.
     */
    public static int[] footprint(Plot plot) {
        TemplateCatalog.Entry entry = TemplateCatalog.of(plot.template());
        boolean turned = plot.facing() == 1 || plot.facing() == 3;
        int sizeX = turned ? entry.depth() : entry.width();
        int sizeZ = turned ? entry.width() : entry.depth();
        int minX = plot.x() - (sizeX - 1) / 2;
        int minZ = plot.z() - (sizeZ - 1) / 2;
        return new int[]{minX, minZ, minX + sizeX - 1, minZ + sizeZ - 1};
    }

    /**
     * Houses of a town of the first layout and their beds: small houses, every third one large.
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
     * The buildings of a town of this kingdom around its town hall, in the order they are put up; positions are in blocks from
     * the town hall.
     *
     * @param index the number of the town, from 0
     */
    public List<Plot> town(int index) {
        if (layout == NpcSite.LAYOUT_DISTRICTS) {
            return CityPlan.town(level, seed, index);
        }
        int[][] plots = {{0, -12, 0}, {12, 0, 1}, {0, 12, 2}, {-12, 0, 3}, {12, -12, 0}, {-12, 12, 2}};
        List<String> templates = townBuildings(townHouses);
        List<Plot> list = new ArrayList<>();
        for (int i = 0; i < templates.size() && i < plots.length; i++) {
            int[] where = templates.get(i).equals(WELL) ? new int[]{-9, -9, 0} : plots[i];
            list.add(new Plot(templates.get(i), where[0], where[1], where[2], false, ""));
        }
        return list;
    }

    /**
     * The radius of the palisade of a town of this kingdom, or 0 if its towns have none.
     */
    public int townWall() {
        return layout == NpcSite.LAYOUT_DISTRICTS ? districtTownWall(level) : 0;
    }

    /**
     * How far the levelled ground of a town reaches from its town hall.
     */
    public int townReach() {
        return layout == NpcSite.LAYOUT_DISTRICTS ? CityPlan.TOWN_WALL : 18;
    }

    /**
     * The middles of the fields of a town, in blocks from its town hall.
     */
    public List<int[]> townFields() {
        return layout == NpcSite.LAYOUT_DISTRICTS ? List.of(new int[]{0, -CityPlan.TOWN_FIELD}) : List.of();
    }

    /**
     * The composition of a kingdom of a level in the first layout.
     *
     * @param cityPeople    the citizens a large city needs
     * @param cityBuildings the buildings a large city needs
     * @param townPeople    the citizens a town needs
     * @param townBuildings the buildings a town needs
     * @param wallRadius    the radius of the walls of a village; a town has half as much again, a large city twice as much
     */
    public static NpcComposition of(NpcLevel level, long seed, NpcRules rules, int townPeople, int townBuildings, int cityPeople,
                                    int cityBuildings, int wallRadius) {
        return of(level, seed, rules, townPeople, townBuildings, cityPeople, cityBuildings, wallRadius, rules.towns(level));
    }

    /**
     * The radius of the wall of a capital of districts of a level.
     */
    public static int districtWall(NpcLevel level) {
        return CityPlan.shape(level).wall();
    }

    /**
     * The radius of the palisade of the towns of a kingdom of districts of a level, or 0 if its towns have none.
     */
    public static int districtTownWall(NpcLevel level) {
        return level == NpcLevel.LARGE ? CityPlan.TOWN_WALL : 0;
    }

    /**
     * How far the fields and outer posts of a capital of districts of a level reach from the throne, in blocks.
     */
    public static int districtReach(NpcLevel level) {
        return CityPlan.outerReach(CityPlan.shape(level));
    }

    /**
     * The composition of a kingdom of a level in the layout of districts, whose place has room for {@code townsAtMost} towns.
     */
    public static NpcComposition districts(NpcLevel level, long seed, NpcRules rules, int townsAtMost) {
        return CityPlan.make(level, seed, rules, townsAtMost);
    }

    /**
     * The composition of a kingdom of a level in the first layout whose place has room for {@code townsAtMost} towns: the work
     * of the citizens is planned only for the people of the towns that are there.
     */
    public static NpcComposition of(NpcLevel level, long seed, NpcRules rules, int townPeople, int townBuildings, int cityPeople,
                                    int cityBuildings, int wallRadius, int townsAtMost) {
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
                list.addAll(List.of(MARKET, STOREHOUSE, WELL, WORKSHOP, WORKSHOP, TAVERN));
                for (int i = 0; i < barracks; i++) {
                    list.add(BARRACKS);
                }
                list.addAll(List.of(WATCHTOWER, WATCHTOWER));
            }
            default -> {
                radius = wallRadius * 2;
                list.addAll(List.of(MARKET, STOREHOUSE, WELL, WORKSHOP, WORKSHOP, STOREHOUSE, WELL, WORKSHOP, MAGE_TOWER, TAVERN_2));
                for (int i = 0; i < barracks; i++) {
                    list.add(BARRACKS);
                }
                list.addAll(List.of(WATCHTOWER, WATCHTOWER, WATCHTOWER, WATCHTOWER));
            }
        }
        int rings = Math.max(1, (radius - 3 - PLOT / 2 - 1) / SPACING);
        int plots = 4 * rings * (rings + 1);
        int towns = Math.max(0, Math.min(rules.towns(level), townsAtMost));
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
            case MEDIUM -> List.of(new String[]{"farmer", "3"}, new String[]{"merchant", "1"}, new String[]{"innkeeper", "1"}, new String[]{"crafter", "1"},
                    new String[]{"smith", "1"}, new String[]{"lumberjack", "1"}, new String[]{"miner", "1"}, new String[]{"carrier", "1"},
                    new String[]{"builder", "1"}, new String[]{"minter", "1"}, new String[]{"registrar", "1"}, new String[]{"farmer", "1"},
                    new String[]{"lumberjack", "1"});
            default -> List.of(new String[]{"farmer", "6"}, new String[]{"merchant", "2"}, new String[]{"innkeeper", "1"}, new String[]{"crafter", "1"},
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
        int bunks = 0;
        for (String building : list) {
            if (building.equals(BARRACKS)) {
                bunks += beds(building);
            }
        }
        return new NpcComposition(NpcSite.LAYOUT_GRID, level, seed, placed, rings, towns, townHouses, level != NpcLevel.SMALL, road, citizens,
                jobs, soldierCount, fields, 0, List.of(), List.of(), List.of(), List.of(), bunks);
    }

    private static int bedsOf(List<String> buildings) {
        int beds = 0;
        for (String building : buildings) {
            beds += beds(building);
        }
        return beds;
    }

    /**
     * Puts the buildings on the plots of the grid of the first layout: watchtowers on the corners of the outer ring, the rest
     * from the throne outwards in the order given; every front looks towards the throne.
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
            result.add(gridPlot(WATCHTOWER, corner[0], corner[1]));
        }
        int skipped = Math.max(0, towers.size() - corners.length);
        for (String building : buildings) {
            if (building.equals(WATCHTOWER)) {
                if (skipped-- > 0 && !free.isEmpty()) {
                    int[] cell = free.remove(free.size() - 1);
                    result.add(gridPlot(WATCHTOWER, cell[0], cell[1]));
                }
                continue;
            }
            if (free.isEmpty()) {
                break;
            }
            int[] cell = free.remove(0);
            result.add(gridPlot(building, cell[0], cell[1]));
        }
        return result;
    }

    private static Plot gridPlot(String template, int gx, int gz) {
        return new Plot(template, gx * SPACING, gz * SPACING, facing(gx, gz), false, "");
    }

    /**
     * Where the front of a building on a plot of the grid looks so that it faces the throne.
     */
    public static int facing(int gx, int gz) {
        if (Math.abs(gz) >= Math.abs(gx)) {
            return gz < 0 ? 0 : 2;
        }
        return gx < 0 ? 3 : 1;
    }

    public boolean districts() {
        return layout == NpcSite.LAYOUT_DISTRICTS;
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
     * Buildings of the capital with a role.
     */
    public int buildingsWith(String role) {
        int count = 0;
        for (Plot plot : buildings) {
            if (TemplateCatalog.get(plot.template()).map(entry -> entry.role().equals(role)).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Beds of the capital for citizens who are not soldiers.
     */
    public int homes() {
        int beds = 0;
        for (Plot plot : buildings) {
            if (!bunks(plot.template())) {
                beds += beds(plot.template());
            }
        }
        return beds;
    }

    /**
     * The radius of the square of the capital that is levelled, in blocks from the throne: the buildings of the first
     * layout, the wall of the second.
     */
    public int reach() {
        return districts() ? wallRadius : rings * SPACING + PLOT / 2 + 1;
    }

    /**
     * How far the fields and outer posts of the capital reach from the throne, in blocks: the land of the capital must hold
     * them.
     */
    public int outerReach() {
        return districts() ? CityPlan.outerReach(CityPlan.shape(level)) : reach() + 20;
    }

    /**
     * What the ground of the capital of districts is at a column: one of the grounds of {@link CityPlan}.
     */
    public int ground(int x, int z) {
        return CityPlan.ground(CityPlan.shape(level), x, z);
    }

    /**
     * Half the side of the ground of the palace, in blocks; 0 in the first layout.
     */
    public int precinct() {
        return districts() ? CityPlan.shape(level).precinct() : 0;
    }

    /**
     * The post of the guards of a name, if the capital has one.
     */
    public java.util.Optional<Post> post(String name) {
        for (Post post : posts) {
            if (post.name().equals(name)) {
                return java.util.Optional.of(post);
            }
        }
        return java.util.Optional.empty();
    }
}
