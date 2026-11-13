package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Rank;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plan of a styled capital for every style, level, rank of its guild and a few seeds: its castle, institutions, quarters,
 * rows, trees, gardens, posts, people and towns.
 */
class StyledPlanTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;
    private static final int SEEDS = 10;
    private static final Set<Integer> WAYS = Set.of(NpcComposition.GROUND_STREET, NpcComposition.GROUND_AVENUE, NpcComposition.GROUND_SQUARE,
            NpcComposition.GROUND_WALK);
    private static final String[] SIDES = {"north", "east", "south", "west"};

    private static List<Rank> ranks(NpcLevel level) {
        return switch (level) {
            case SMALL -> List.of(Rank.B, Rank.A);
            case MEDIUM -> List.of(Rank.A, Rank.S);
            case LARGE -> List.of(Rank.S, Rank.SS);
        };
    }

    private static NpcComposition of(String style, NpcLevel level, Rank rank, long seed) {
        return NpcComposition.styled(level, seed, style, rank, RULES, RULES.towns(level));
    }

    /**
     * Every style, level, allowed rank and seed.
     */
    private static void every(Consumer<Case> check) {
        for (String style : TemplateCatalog.STYLES) {
            for (NpcLevel level : NpcLevel.values()) {
                for (Rank rank : ranks(level)) {
                    for (long seed = 1; seed <= SEEDS; seed++) {
                        check.accept(new Case(style, level, rank, seed, of(style, level, rank, seed)));
                    }
                }
            }
        }
    }

    private record Case(String style, NpcLevel level, Rank rank, long seed, NpcComposition composition) {
        String at() {
            return style + " " + level + " " + rank + " seed " + seed + ": ";
        }
    }

    private static Entry entry(Plot plot) {
        return TemplateCatalog.of(plot.template());
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    private static boolean inside(int[] box, int x, int z) {
        return x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3];
    }

    private static int distance(int[] box, int x, int z) {
        int dx = Math.max(0, Math.max(box[0] - x, x - box[2]));
        int dz = Math.max(0, Math.max(box[1] - z, z - box[3]));
        return Math.max(dx, dz);
    }

    /**
     * A building of a row: not the castle, a gate of the wall, a tower in its corners or a decoration of the square.
     */
    private static boolean row(Plot plot) {
        Kind kind = entry(plot).kind();
        return kind != Kind.CASTLE && kind != Kind.WALL_GATE && kind != Kind.TOWER && kind != Kind.DECORATION;
    }

    private static boolean house(Plot plot) {
        return Set.of("noble", "merchant", "craft", "poor").contains(plot.quarter());
    }

    private static List<String> points(List<int[]> points) {
        List<String> list = new ArrayList<>();
        for (int[] point : points) {
            list.add(Arrays.toString(point));
        }
        return list;
    }

    private static int count(NpcComposition composition, Kind kind) {
        int count = 0;
        for (Plot plot : composition.buildings()) {
            if (entry(plot).kind() == kind) {
                count++;
            }
        }
        return count;
    }

    @Test
    void theSameSeedGivesTheSameCapital() {
        int[] differ = new int[1];
        every(c -> {
            NpcComposition a = c.composition();
            NpcComposition b = of(c.style(), c.level(), c.rank(), c.seed());
            assertEquals(NpcSite.LAYOUT_STYLED, a.layout());
            assertTrue(a.styled() && a.districts(), c.at());
            assertEquals(c.style(), a.style());
            assertEquals(a.buildings(), b.buildings(), c.at());
            assertEquals(a.gates(), b.gates());
            assertEquals(a.lots(), b.lots());
            assertEquals(a.jobs(), b.jobs());
            assertEquals(a.citizens(), b.citizens());
            assertEquals(a.soldiers(), b.soldiers());
            assertEquals(a.bunks(), b.bunks());
            assertEquals(points(a.trees()), points(b.trees()), c.at());
            assertEquals(points(a.gardens()), points(b.gardens()), c.at());
            assertEquals(points(a.fieldSpots()), points(b.fieldSpots()));
            assertEquals(a.posts().size(), b.posts().size());
            for (int p = 0; p < a.posts().size(); p++) {
                assertEquals(a.posts().get(p).name(), b.posts().get(p).name());
                assertEquals(points(a.posts().get(p).points()), points(b.posts().get(p).points()));
            }
            for (int t = 0; t < a.towns(); t++) {
                assertEquals(a.town(t), b.town(t));
            }
            if (!a.buildings().equals(of(c.style(), c.level(), c.rank(), c.seed() + 100).buildings())) {
                differ[0]++;
            }
        });
        assertTrue(differ[0] > 400, "another seed usually gives another capital: " + differ[0]);
    }

    @Test
    void theCastleOfTheStyleStandsAtTheThrone() {
        every(c -> {
            Plot castle = c.composition().buildings().get(0);
            assertEquals(TemplateCatalog.ofStyle(c.style(), Kind.CASTLE).get(c.level().ordinal()).id(), castle.template(), c.at());
            assertTrue(castle.template().endsWith("_castle_" + c.level().id()), c.at() + castle.template());
            assertEquals(0, castle.x());
            assertEquals(0, castle.z());
            assertEquals(0, castle.facing());
            assertFalse(castle.mirror());
            assertEquals("castle", castle.quarter());
            int[] box = NpcComposition.footprint(castle);
            assertEquals(c.composition().precinct() - 1, box[2], c.at() + "the precinct is the castle and one block");
        });
    }

    @Test
    void theWallFollowsTheLevel() {
        int[] walls = {74, 86, 101};
        for (NpcLevel level : NpcLevel.values()) {
            int wall = walls[level.ordinal()];
            assertEquals(wall, StyledPlan.shape(level).wall());
            assertEquals(wall, NpcComposition.capitalWall(level, NpcSite.LAYOUT_STYLED));
            assertEquals(wall + 18, NpcComposition.capitalReach(level, NpcSite.LAYOUT_STYLED));
            assertEquals(NpcComposition.districtWall(level), NpcComposition.capitalWall(level, NpcSite.LAYOUT_DISTRICTS));
            assertEquals(NpcComposition.districtReach(level), NpcComposition.capitalReach(level, NpcSite.LAYOUT_DISTRICTS));
            assertEquals(0, NpcComposition.capitalWall(level, NpcSite.LAYOUT_GRID));
            assertEquals(0, NpcComposition.capitalReach(level, NpcSite.LAYOUT_GRID));
            NpcComposition composition = of("classic", level, ranks(level).get(0), 3L);
            assertEquals(wall, composition.wallRadius());
            assertEquals(wall, composition.reach());
            assertEquals(wall + 18, composition.outerReach());
            assertEquals(new int[]{16, 21, 24}[level.ordinal()], composition.precinct());
            assertEquals(level != NpcLevel.SMALL, composition.stoneWall());
            assertEquals(level == NpcLevel.LARGE ? CityPlan.TOWN_WALL : 0, composition.townWall());
            assertEquals(CityPlan.TOWN_WALL, composition.townReach());
            assertEquals(2, composition.townFields().size());
        }
    }

    @Test
    void buildingsNeverOverlapAndStayInsideTheWall() {
        every(c -> {
            List<Plot> plots = c.composition().buildings();
            int wall = c.composition().wallRadius();
            for (int a = 0; a < plots.size(); a++) {
                int[] box = NpcComposition.footprint(plots.get(a));
                for (int coordinate : box) {
                    assertTrue(Math.abs(coordinate) <= wall - 1, c.at() + plots.get(a) + " reaches the wall " + wall);
                }
                for (int b = a + 1; b < plots.size(); b++) {
                    assertFalse(overlap(box, NpcComposition.footprint(plots.get(b))), c.at() + plots.get(a) + " and " + plots.get(b));
                }
            }
        });
    }

    @Test
    void theInstitutionsStandOnTheSquare() {
        every(c -> {
            NpcComposition composition = c.composition();
            int a = StyledPlan.shape(c.level()).rings()[0];
            String guild = TemplateCatalog.guildHouse(c.style(), Kind.GUILD, c.rank()).orElseThrow().id();
            String tower = TemplateCatalog.guildHouse(c.style(), Kind.MAGE_TOWER, c.rank()).orElseThrow().id();
            for (String id : List.of(guild, tower)) {
                List<Plot> found = composition.buildings().stream().filter(plot -> plot.template().equals(id)).toList();
                assertEquals(1, found.size(), c.at() + id);
                Plot plot = found.get(0);
                assertEquals(2, plot.facing(), c.at() + id + " looks at the square from the south");
                assertEquals(a, NpcComposition.footprint(plot)[1], c.at() + id + " on the line of the fronts of the first ring");
            }
            assertEquals(1, count(composition, Kind.GUILD), c.at());
            assertEquals(1, count(composition, Kind.MAGE_TOWER), c.at());
            for (String id : List.of("classic_treasury", "classic_court", "classic_infirmary", "classic_builders_yard",
                    "classic_carters_yard")) {
                assertEquals(1, composition.buildingsOf("kingdomworld:" + id), c.at() + id);
            }
            assertEquals(1, composition.buildingsOf("kingdomworld:" + c.style() + "_tavern") , c.at() + "tavern");
            assertEquals(composition.gates().size(), composition.buildingsOf("kingdomworld:" + c.style() + "_wall_gate"), c.at());
            assertEquals(4, composition.buildingsOf("kingdomworld:" + c.style() + "_camp_tower"), c.at() + "towers of the corners");
            Set<String> decorations = new HashSet<>();
            for (Plot plot : composition.buildings()) {
                if (entry(plot).kind() == Kind.DECORATION) {
                    decorations.add(plot.template());
                }
            }
            assertEquals(c.level() == NpcLevel.SMALL ? 3 : 4, decorations.size(), c.at() + decorations);
            if (c.level() == NpcLevel.LARGE) {
                assertEquals(1, composition.buildingsOf("kingdomworld:classic_guard_barracks"), c.at());
                assertEquals(1, composition.buildingsOf("kingdomworld:classic_stables"), c.at());
                assertEquals(1, composition.buildingsOf("kingdomworld:classic_garden"), c.at());
            }
            Set<Integer> sides = new HashSet<>();
            for (NpcComposition.Gate gate : composition.gates()) {
                sides.add(gate.side());
            }
            assertEquals(c.level() == NpcLevel.SMALL ? Set.of(0, 2) : Set.of(0, 1, 2, 3), sides, c.at());
        });
    }

    @Test
    void everyCapitalHasWhatItsPeopleNeed() {
        every(c -> {
            Set<String> roles = new HashSet<>();
            for (Plot plot : c.composition().buildings()) {
                roles.add(entry(plot).role());
            }
            assertTrue(roles.containsAll(List.of("house", "warehouse", "workshop", "market", "well", "barracks", "tower", "tavern",
                    "mage_tower")), c.at() + roles);
            assertTrue(c.composition().buildingsWith("barracks") >= 2, c.at());
        });
    }

    @Test
    void theGroundHasItsSquaresGreensAndWays() {
        for (NpcLevel level : NpcLevel.values()) {
            StyledPlan.Shape shape = StyledPlan.shape(level);
            NpcComposition composition = of("gothic", level, ranks(level).get(0), 7L);
            int a = shape.rings()[0];
            int b = a + shape.depths()[0] - 1;
            int a1 = shape.rings()[1];
            for (int[] corner : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                for (int at : new int[]{a, (a + b) / 2, b}) {
                    assertEquals(NpcComposition.GROUND_GREEN, composition.ground(corner[0] * at, corner[1] * a), level + " corner");
                    assertEquals(NpcComposition.GROUND_GREEN, composition.ground(corner[0] * b, corner[1] * at), level + " corner");
                }
            }
            for (int at = shape.precinct(); at < a; at++) {
                assertEquals(NpcComposition.GROUND_SQUARE, composition.ground(at, -at), level + " the square at " + at);
                assertEquals(NpcComposition.GROUND_SQUARE, composition.ground(0, at), level + " the square at " + at);
            }
            for (NpcComposition.Gate gate : composition.gates()) {
                int[] out = CityPlan.outward(gate.side());
                for (int along : new int[]{-12, -5, 5, 12}) {
                    for (int across : new int[]{a1, shape.last()}) {
                        int x = out[0] != 0 ? out[0] * across : along;
                        int z = out[1] != 0 ? out[1] * across : along;
                        assertEquals(NpcComposition.GROUND_SQUARE, composition.ground(x, z), level + " gate square " + gate + " " + x + ", " + z);
                    }
                }
                int x = out[0] != 0 ? out[0] * a1 : 13;
                int z = out[1] != 0 ? out[1] * a1 : 13;
                assertEquals(NpcComposition.GROUND_YARD, composition.ground(x, z), level + " past the gate square");
                assertEquals(NpcComposition.GROUND_AVENUE, composition.ground(out[0] * a1 + (out[0] == 0 ? 1 : 0), out[1] * a1), level.id());
            }
            int wall = shape.wall();
            assertEquals(NpcComposition.GROUND_WALK, composition.ground(wall - 1, 0), level + " the walk");
            assertEquals(NpcComposition.GROUND_YARD, composition.ground(wall, 0), level + " outside");
            assertEquals(NpcComposition.GROUND_YARD, composition.ground(b + 1, 20), level + " the back yards");
        }
    }

    @Test
    void everyFrontOfARowLooksAtAWay() {
        List<String> failures = new ArrayList<>();
        every(c -> {
            NpcComposition composition = c.composition();
            for (Plot plot : composition.buildings()) {
                if (!row(plot)) {
                    continue;
                }
                int[] box = NpcComposition.footprint(plot);
                int midX = (box[0] + box[2]) / 2;
                int midZ = (box[1] + box[3]) / 2;
                boolean found = false;
                for (int out = 1; out <= 3 && !found; out++) {
                    int[] cell = switch (plot.facing()) {
                        case 0 -> new int[]{midX, box[3] + out};
                        case 1 -> new int[]{box[0] - out, midZ};
                        case 2 -> new int[]{midX, box[1] - out};
                        default -> new int[]{box[2] + out, midZ};
                    };
                    found = WAYS.contains(composition.ground(cell[0], cell[1]));
                }
                if (!found && failures.size() < 6) {
                    failures.add(c.at() + plot);
                }
            }
        });
        assertTrue(failures.isEmpty(), "fronts that look at no way: " + failures);
    }

    @Test
    void treesAndGardensKeepClearOfTheBuildings() {
        int[] trees = new int[1];
        int[] gardens = new int[1];
        every(c -> {
            NpcComposition composition = c.composition();
            int wall = composition.wallRadius();
            List<int[]> boxes = new ArrayList<>();
            for (Plot plot : composition.buildings()) {
                boxes.add(NpcComposition.footprint(plot));
            }
            Set<String> seen = new HashSet<>();
            for (int[] tree : composition.trees()) {
                assertEquals(2, tree.length);
                assertTrue(Math.max(Math.abs(tree[0]), Math.abs(tree[1])) < wall - 2, c.at() + "a tree by the wall " + Arrays.toString(tree));
                assertTrue(seen.add(Arrays.toString(tree)), c.at() + "two trees in one place");
                for (int[] box : boxes) {
                    assertTrue(distance(box, tree[0], tree[1]) >= 3, c.at() + "a tree at " + Arrays.toString(tree) + " by " + Arrays.toString(box));
                }
                int ground = composition.ground(tree[0], tree[1]);
                assertTrue(ground == NpcComposition.GROUND_YARD || ground == NpcComposition.GROUND_GREEN, c.at() + "a tree on a way");
            }
            for (int[] garden : composition.gardens()) {
                assertEquals(6, garden.length);
                assertTrue(garden[0] <= garden[2] && garden[1] <= garden[3], c.at() + Arrays.toString(garden));
                boolean ns = garden[4] == 0 || garden[4] == 2;
                assertEquals(2, ns ? garden[3] - garden[1] + 1 : garden[2] - garden[0] + 1, c.at() + "a strip of two blocks");
                int door = garden[5];
                assertTrue(ns ? door > garden[0] && door < garden[2] : door > garden[1] && door < garden[3], c.at() + "the opening");
                for (int[] box : boxes) {
                    assertFalse(overlap(garden, box), c.at() + "a garden " + Arrays.toString(garden) + " on " + Arrays.toString(box));
                }
                // The garden lies before the front of its house, between it and the street.
                int[] before = switch (garden[4]) {
                    case 0 -> new int[]{garden[0], garden[3] + 1};
                    case 1 -> new int[]{garden[0] - 1, garden[1]};
                    case 2 -> new int[]{garden[0], garden[1] - 1};
                    default -> new int[]{garden[2] + 1, garden[1]};
                };
                assertTrue(WAYS.contains(composition.ground(before[0], before[1])), c.at() + "a garden off the street");
            }
            trees[0] += composition.trees().size();
            gardens[0] += composition.gardens().size();
        });
        assertTrue(trees[0] > 0 && gardens[0] > 0, "trees " + trees[0] + ", gardens " + gardens[0]);
    }

    @Test
    void theGuardsHaveTheirPosts() {
        every(c -> {
            NpcComposition composition = c.composition();
            int wall = composition.wallRadius();
            for (Post post : composition.posts()) {
                for (int[] point : post.points()) {
                    assertTrue(Math.max(Math.abs(point[0]), Math.abs(point[1])) < wall, c.at() + post.name() + " " + Arrays.toString(point));
                }
            }
            for (NpcComposition.Gate gate : composition.gates()) {
                assertEquals(1, composition.post("gate_" + SIDES[gate.side()]).orElseThrow().points().size(), c.at() + gate);
            }
            int gatePosts = 0;
            for (Post post : composition.posts()) {
                if (post.name().matches("gate_\\w+")) {
                    gatePosts++;
                }
            }
            assertEquals(composition.gates().size(), gatePosts, c.at());
            for (String name : List.of("square", "palace_door", "stores", "stores_round")) {
                assertTrue(composition.post(name).isPresent(), c.at() + "no post " + name);
            }
            assertEquals(8, composition.post("wall_round").orElseThrow().points().size());
            int[] castle = NpcComposition.footprint(composition.buildings().get(0));
            List<int[]> hall = composition.post("palace_hall").orElseThrow().points();
            assertEquals(4, hall.size(), c.at());
            List<String> hallPoints = points(hall);
            assertTrue(hallPoints.contains("[-2, 3, 0]") && hallPoints.contains("[2, 3, 0]"), c.at() + hallPoints);
            assertEquals(2, composition.post("castle_gate").orElseThrow().points().size(), c.at());
            List<int[]> round = composition.post("castle_wall").orElseThrow().points();
            assertTrue(round.size() >= 4, c.at() + "castle wall " + round.size());
            for (int i = 1; i < round.size(); i++) {
                assertTrue(Math.atan2(round.get(i - 1)[1], round.get(i - 1)[0]) <= Math.atan2(round.get(i)[1], round.get(i)[0]),
                        c.at() + "the round on the castle wall goes round: " + points(round));
            }
            assertEquals(1, composition.post("treasury").orElseThrow().points().size(), c.at());
            for (String name : List.of("palace_door", "palace_hall", "castle_gate", "castle_wall", "treasury")) {
                for (int[] point : composition.post(name).orElseThrow().points()) {
                    assertEquals(3, point.length, c.at() + name);
                    assertTrue(inside(castle, point[0], point[1]), c.at() + name + " outside the castle: " + Arrays.toString(point));
                }
            }
            assertEquals(List.of(0, (composition.precinct() + StyledPlan.shape(c.level()).rings()[0]) / 2 + 1),
                    Arrays.stream(composition.post("square").orElseThrow().points().get(0)).boxed().toList());
        });
    }

    @Test
    void everyoneHasABedAndMostHaveWork() {
        every(c -> {
            NpcComposition composition = c.composition();
            NpcLevel level = c.level();
            int townHomes = 0;
            int townBunks = 0;
            for (int t = 0; t < composition.towns(); t++) {
                for (Plot plot : composition.town(t)) {
                    if (NpcComposition.bunks(plot.template())) {
                        townBunks += NpcComposition.beds(plot.template());
                    } else {
                        townHomes += NpcComposition.beds(plot.template());
                    }
                }
            }
            assertEquals(RULES.towns(level), composition.towns());
            int inCapital = RULES.guards(level) + RULES.army(level);
            assertTrue(composition.bunks() - townBunks >= inCapital, c.at() + "bunks " + (composition.bunks() - townBunks) + " for " + inCapital);
            int ofTowns = composition.towns() * RULES.townGuards(level);
            int inTownBunks = Math.min(ofTowns, townBunks);
            int inTownHomes = townBunks > 0 ? 0 : Math.min(ofTowns, townHomes / 3);
            assertEquals(inCapital + inTownBunks + inTownHomes, composition.soldiers(), c.at());
            assertEquals(composition.homes() + townHomes + inCapital + inTownBunks, composition.citizens(), c.at() + "a bed for everybody");
            int workers = 0;
            for (int count : composition.jobs().values()) {
                workers += count;
            }
            int civilians = composition.citizens() - composition.soldiers();
            assertTrue(workers <= civilians, c.at() + workers + " workers for " + civilians);
            assertTrue(workers * 2 > civilians, c.at() + "fewer than half are without work: " + workers + " of " + civilians);
            Map<String, Integer> jobs = composition.jobs();
            assertEquals(1, jobs.getOrDefault("registrar", 0), c.at() + jobs);
            assertEquals(1, jobs.getOrDefault("minter", 0), c.at() + jobs);
            assertEquals(2, jobs.getOrDefault("mage", 0), c.at() + jobs);
            assertTrue(composition.fields() >= 1 && composition.fields() <= composition.fieldSpots().size(), c.at());
            assertTrue(composition.citizens() > 0);
        });
    }

    @Test
    void everyTradeHasPeopleEvenWhereTheHousesHoldFew() {
        // Without towns a large capital of the styles with roomy houses has fewer people than its work wants.
        List<String> trades = List.of("farmer", "merchant", "innkeeper", "crafter", "registrar", "minter", "mage", "builder", "lumberjack",
                "miner", "carrier");
        for (String style : TemplateCatalog.STYLES) {
            for (NpcLevel level : NpcLevel.values()) {
                for (Rank rank : ranks(level)) {
                    for (long seed = 1; seed <= SEEDS; seed++) {
                        NpcComposition composition = NpcComposition.styled(level, seed, style, rank, RULES, 0);
                        String at = style + " " + level + " " + rank + " " + seed + ": ";
                        for (String trade : trades) {
                            assertTrue(composition.jobs().getOrDefault(trade, 0) > 0, at + "nobody is a " + trade + " " + composition.jobs());
                        }
                        int workers = composition.jobs().values().stream().mapToInt(Integer::intValue).sum();
                        assertTrue(workers <= composition.citizens() - composition.soldiers(), at + workers + " workers");
                    }
                }
            }
        }
    }

    @Test
    void theWorkIsGivenInTheOrderOfTheNeedWithOneOfEachFirst() {
        Map<String, Integer> wanted = new java.util.LinkedHashMap<>();
        wanted.put("farmer", 6);
        wanted.put("merchant", 0);
        wanted.put("crafter", 4);
        wanted.put("miner", 3);
        wanted.put("carrier", 2);
        // People enough: everybody wanted, in the order of the need.
        assertEquals(List.of("farmer", "crafter", "miner", "carrier"), List.copyOf(StyledPlan.jobs(wanted, 20).keySet()));
        assertEquals(Map.of("farmer", 6, "crafter", 4, "miner", 3, "carrier", 2), StyledPlan.jobs(wanted, 20));
        assertEquals(Map.of("farmer", 6, "crafter", 4, "miner", 3, "carrier", 2), StyledPlan.jobs(wanted, 15));
        // Too few: one of each first, then the rest by the need.
        assertEquals(Map.of("farmer", 6, "crafter", 2, "miner", 1, "carrier", 1), StyledPlan.jobs(wanted, 10));
        assertEquals(Map.of("farmer", 1, "crafter", 1, "miner", 1, "carrier", 1), StyledPlan.jobs(wanted, 4));
        assertEquals(Map.of("farmer", 1, "crafter", 1), StyledPlan.jobs(wanted, 2));
        assertEquals(Map.of(), StyledPlan.jobs(wanted, 0));
    }

    @Test
    void theBuildersAndCarriersHaveTheirSeats() {
        every(c -> {
            NpcComposition composition = c.composition();
            List<StyledPlan.WorkPlace> places = StyledPlan.workPlaces(composition);
            assertEquals(c.level() == NpcLevel.SMALL ? 0 : c.level() == NpcLevel.MEDIUM ? 2 : 5, places.size(), c.at());
            for (String profession : List.of("builder", "carrier")) {
                int seats = StyledPlan.seatsInBuildings(composition, profession);
                for (StyledPlan.WorkPlace place : places) {
                    if (place.what().equals(profession)) {
                        seats += place.seats();
                    }
                }
                assertEquals(composition.jobs().getOrDefault(profession, 0), seats, c.at() + profession);
            }
            assertEquals(composition.jobs().getOrDefault("registrar", 0), StyledPlan.seatsInBuildings(composition, "registrar"));
            assertEquals(composition.jobs().getOrDefault("minter", 0), StyledPlan.seatsInBuildings(composition, "minter"));
            List<int[]> boxes = new ArrayList<>();
            for (Plot plot : composition.buildings()) {
                boxes.add(NpcComposition.footprint(plot));
            }
            for (StyledPlan.WorkPlace place : places) {
                assertEquals(NpcComposition.GROUND_SQUARE, composition.ground(place.x(), place.z()), c.at() + "a post in a gate square " + place);
                for (int[] box : boxes) {
                    assertFalse(inside(box, place.x(), place.z()), c.at() + "a post in a building " + place);
                }
            }
            int[] bank = StyledPlan.bank(composition).orElseThrow();
            Plot treasury = composition.buildings().stream().filter(plot -> entry(plot).kind() == Kind.TREASURY).findFirst().orElseThrow();
            assertTrue(inside(NpcComposition.footprint(treasury), bank[0], bank[1]), c.at() + "the bank " + Arrays.toString(bank));
        });
        NpcComposition medium = of("nordic", NpcLevel.MEDIUM, Rank.A, 4L);
        List<StyledPlan.WorkPlace> places = StyledPlan.workPlaces(medium);
        assertEquals(new StyledPlan.WorkPlace("builder", 5, 72, 1), places.get(0));
        assertEquals(new StyledPlan.WorkPlace("carrier", -5, 72, 2), places.get(1));
        assertEquals(1, StyledPlan.seatsInBuildings(medium, "builder"));
        assertEquals(2, StyledPlan.seatsInBuildings(medium, "carrier"));
        NpcComposition large = of("desert", NpcLevel.LARGE, Rank.SS, 4L);
        assertEquals(List.of(1, 3, 1, 1, 0), StyledPlan.workPlaces(large).stream().map(StyledPlan.WorkPlace::seats).toList());
        assertEquals(List.of(87, 87, -87, 5, 5), StyledPlan.workPlaces(large).stream().map(StyledPlan.WorkPlace::z).toList());
        assertEquals(1, StyledPlan.seatsInBuildings(large, "builder"));
        assertEquals(3, StyledPlan.seatsInBuildings(large, "carrier"));
        NpcComposition small = of("terem", NpcLevel.SMALL, Rank.B, 4L);
        assertEquals(1, StyledPlan.seatsInBuildings(small, "builder"));
        assertEquals(2, StyledPlan.seatsInBuildings(small, "carrier"));
        assertTrue(StyledPlan.workPlaces(NpcComposition.districts(NpcLevel.LARGE, 4L, RULES, 4)).isEmpty(), "only styled capitals");
    }

    @Test
    void theProfessionsOfTheBuildings() {
        assertEquals("registrar", StyledPlan.profession(TemplateCatalog.of("kingdomworld:gothic_guild_s")));
        assertEquals("minter", StyledPlan.profession(TemplateCatalog.of("kingdomworld:classic_treasury")));
        assertEquals("builder", StyledPlan.profession(TemplateCatalog.of("kingdomworld:classic_builders_yard")));
        assertEquals("carrier", StyledPlan.profession(TemplateCatalog.of("kingdomworld:classic_carters_yard")));
        assertEquals("", StyledPlan.profession(TemplateCatalog.of("kingdomworld:terem_mage_tower_a")));
        assertEquals("", StyledPlan.profession(TemplateCatalog.of("kingdomworld:classic_court")));
        assertEquals("", StyledPlan.profession(TemplateCatalog.of("kingdomworld:smithy")));
        assertEquals(13, StyledPlan.GATE_CLEAR);
    }

    @Test
    void aCapitalHasHousesOfEveryQuarterSetBackUnevenly() {
        every(c -> {
            Set<String> templates = new HashSet<>();
            Set<String> quarters = new HashSet<>();
            Set<Integer> setbacks = new HashSet<>();
            NpcComposition composition = c.composition();
            Plot previous = null;
            for (Plot plot : composition.buildings()) {
                if (!house(plot)) {
                    previous = null;
                    continue;
                }
                Kind kind = entry(plot).kind();
                assertTrue(kind == Kind.POOR_HOUSE || kind == Kind.CRAFT_HOUSE || kind == Kind.MERCHANT_HOUSE || kind == Kind.NOBLE_HOUSE,
                        c.at() + plot);
                assertEquals(c.style(), entry(plot).style(), c.at() + plot);
                // The houses of a half-row follow each other in the plan; a half-row lies on one side of its avenue.
                boolean ns = plot.facing() == 0 || plot.facing() == 2;
                if (previous != null && previous.facing() == plot.facing()
                        && Integer.signum(ns ? previous.x() : previous.z()) == Integer.signum(ns ? plot.x() : plot.z())) {
                    assertFalse(previous.template().equals(plot.template()), c.at() + "two alike side by side: " + plot);
                }
                previous = plot;
                templates.add(plot.template());
                quarters.add(plot.quarter());
                setbacks.add(setback(composition, plot));
                // The poor live along the north wall and towards the north corners, the merchants of the outer ring by the market.
                if (plot.quarter().equals("poor")) {
                    assertTrue(plot.z() < 0, c.at() + "a house of the poor south of the throne: " + plot);
                }
                if (plot.quarter().equals("merchant")) {
                    assertTrue(plot.z() > 0, c.at() + "a house of merchants north of the throne: " + plot);
                }
            }
            assertTrue(setbacks.size() > 1, c.at() + "setbacks " + setbacks);
            assertTrue(setbacks.stream().allMatch(s -> s >= 0 && s <= 2), c.at() + "setbacks " + setbacks);
            assertEquals(Set.of("noble", "merchant", "craft", "poor"), quarters, c.at());
            if (c.level() == NpcLevel.LARGE) {
                assertTrue(templates.size() >= 4, c.at() + "house templates " + templates);
            }
        });
    }

    /**
     * Blocks between the front of a building and the way it looks at.
     */
    private static int setback(NpcComposition composition, Plot plot) {
        int[] box = NpcComposition.footprint(plot);
        int midX = (box[0] + box[2]) / 2;
        int midZ = (box[1] + box[3]) / 2;
        for (int out = 1; out <= 4; out++) {
            int[] cell = switch (plot.facing()) {
                case 0 -> new int[]{midX, box[3] + out};
                case 1 -> new int[]{box[0] - out, midZ};
                case 2 -> new int[]{midX, box[1] - out};
                default -> new int[]{box[2] + out, midZ};
            };
            if (WAYS.contains(composition.ground(cell[0], cell[1]))) {
                return out - 1;
            }
        }
        return -1;
    }

    @Test
    void theCapitalsHaveAboutAsManyBuildingsAsPlanned() {
        int[][] range = {{30, 55}, {45, 80}, {55, 100}};
        every(c -> {
            int size = c.composition().buildings().size();
            int[] wanted = range[c.level().ordinal()];
            assertTrue(size >= wanted[0] && size <= wanted[1], c.at() + size + " buildings");
        });
    }

    @Test
    void townsAreRowsOfTheStyle() {
        every(c -> {
            NpcComposition composition = c.composition();
            for (int t = 0; t < composition.towns(); t++) {
                List<Plot> town = composition.town(t);
                String at = c.at() + "town " + t + ": ";
                Set<String> ids = new HashSet<>();
                Set<Kind> kinds = new HashSet<>();
                for (Plot plot : town) {
                    Entry entry = entry(plot);
                    ids.add(plot.template());
                    kinds.add(entry.kind());
                    int[] box = NpcComposition.footprint(plot);
                    assertTrue(Math.abs(box[0]) <= CityPlan.TOWN_ROW && Math.abs(box[2]) <= CityPlan.TOWN_ROW, at + plot);
                    assertTrue(Math.abs(box[1]) < composition.townReach() && Math.abs(box[3]) < composition.townReach(), at + plot);
                    if (entry.role().equals("house")) {
                        assertTrue(entry.width() <= 11 && entry.depth() <= 11, at + plot);
                        assertEquals(c.style(), entry.style(), at + plot);
                    }
                    for (int[] field : composition.townFields()) {
                        boolean apart = box[2] < field[0] - 5 || box[0] > field[0] + 5 || box[3] < field[1] - 5 || box[1] > field[1] + 5;
                        assertTrue(apart, at + plot.template() + " stands on a field");
                    }
                }
                for (int a = 0; a < town.size(); a++) {
                    for (int b = a + 1; b < town.size(); b++) {
                        assertFalse(overlap(NpcComposition.footprint(town.get(a)), NpcComposition.footprint(town.get(b))), at + town.get(a));
                    }
                }
                assertTrue(ids.contains("kingdomworld:" + c.style() + "_camp_tower"), at + ids);
                assertTrue(kinds.contains(Kind.WELL) && kinds.contains(Kind.WORKSHOP), at + kinds);
                if (c.level() == NpcLevel.LARGE) {
                    assertTrue(ids.contains("kingdomworld:tavern") && ids.contains("kingdomworld:barracks"), at + ids);
                }
            }
        });
    }

    @Test
    void aRankAboveTheSquareIsLowered() {
        NpcComposition small = of("classic", NpcLevel.SMALL, Rank.SS, 2L);
        assertEquals(1, small.buildingsOf("kingdomworld:classic_guild_a"));
        NpcComposition none = of("classic", NpcLevel.MEDIUM, null, 2L);
        assertEquals(1, none.buildingsOf("kingdomworld:classic_guild_a"));
        assertEquals(1, of("classic", NpcLevel.LARGE, Rank.B, 2L).buildingsOf("kingdomworld:classic_guild_b"));
    }
}
