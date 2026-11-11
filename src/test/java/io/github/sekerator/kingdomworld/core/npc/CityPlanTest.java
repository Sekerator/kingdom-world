package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityPlanTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;
    private static final int SEEDS = 30;

    private static NpcComposition of(NpcLevel level, long seed) {
        return NpcComposition.districts(level, seed, RULES, RULES.towns(level));
    }

    private static long seed(int i) {
        return i * 0x9E3779B97F4A7C15L + 17;
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    private static Kind kind(Plot plot) {
        return TemplateCatalog.of(plot.template()).kind();
    }

    private static boolean row(Plot plot) {
        return !plot.quarter().equals("palace") && !plot.quarter().equals("gate") && !plot.quarter().equals("wall");
    }

    private static Set<String> templates(NpcComposition composition, Kind... kinds) {
        Set<String> found = new HashSet<>();
        List<Kind> wanted = List.of(kinds);
        for (Plot plot : composition.buildings()) {
            if (wanted.contains(kind(plot))) {
                found.add(plot.template());
            }
        }
        return found;
    }

    private static List<String> points(List<int[]> points) {
        List<String> list = new ArrayList<>();
        for (int[] point : points) {
            list.add(Arrays.toString(point));
        }
        return list;
    }

    @Test
    void theSameSeedGivesTheSameCapital() {
        for (NpcLevel level : NpcLevel.values()) {
            int differ = 0;
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition a = of(level, seed(i));
                NpcComposition b = of(level, seed(i));
                assertEquals(NpcSite.LAYOUT_DISTRICTS, a.layout());
                assertTrue(a.districts());
                assertEquals(a.buildings(), b.buildings());
                assertEquals(a.gates(), b.gates());
                assertEquals(a.lots(), b.lots());
                assertEquals(a.jobs(), b.jobs());
                assertEquals(a.citizens(), b.citizens());
                assertEquals(a.soldiers(), b.soldiers());
                assertEquals(a.fields(), b.fields());
                assertEquals(a.bunks(), b.bunks());
                assertEquals(a.wallRadius(), b.wallRadius());
                assertEquals(a.towns(), b.towns());
                assertEquals(points(a.fieldSpots()), points(b.fieldSpots()));
                assertEquals(a.posts().size(), b.posts().size());
                for (int p = 0; p < a.posts().size(); p++) {
                    assertEquals(a.posts().get(p).name(), b.posts().get(p).name());
                    assertEquals(points(a.posts().get(p).points()), points(b.posts().get(p).points()));
                }
                for (int t = 0; t < a.towns(); t++) {
                    assertEquals(a.town(t), b.town(t));
                }
                if (!a.buildings().equals(of(level, seed(i) + 1).buildings())) {
                    differ++;
                }
            }
            assertTrue(differ > SEEDS / 2, level + ": another seed usually gives another capital, " + differ);
        }
    }

    @Test
    void buildingsNeverOverlapAndStayInsideTheWall() {
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition composition = of(level, seed(i));
                List<Plot> plots = composition.buildings();
                for (int a = 0; a < plots.size(); a++) {
                    int[] box = NpcComposition.footprint(plots.get(a));
                    for (int coordinate : box) {
                        assertTrue(Math.abs(coordinate) < composition.wallRadius(), level + " seed " + i + ": " + plots.get(a)
                                + " reaches the wall " + composition.wallRadius());
                    }
                    for (int b = a + 1; b < plots.size(); b++) {
                        assertFalse(overlap(box, NpcComposition.footprint(plots.get(b))),
                                level + " seed " + i + ": " + plots.get(a) + " and " + plots.get(b));
                    }
                }
            }
        }
    }

    @Test
    void thePalaceStandsAtTheThrone() {
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                Plot palace = of(level, seed(i)).buildings().get(0);
                assertEquals(level == NpcLevel.LARGE ? "kingdomworld:castle" : "kingdomworld:great_hall", palace.template());
                assertEquals(0, palace.x());
                assertEquals(0, palace.z());
                assertEquals("palace", palace.quarter());
            }
        }
    }

    @Test
    void capitalsGrowWithTheirLevel() {
        for (int i = 0; i < SEEDS; i++) {
            NpcComposition small = of(NpcLevel.SMALL, seed(i));
            NpcComposition medium = of(NpcLevel.MEDIUM, seed(i));
            NpcComposition large = of(NpcLevel.LARGE, seed(i));
            assertTrue(small.wallRadius() >= 40 && small.wallRadius() <= 48, "small wall " + small.wallRadius());
            assertTrue(large.wallRadius() >= 80 && large.wallRadius() <= 96, "large wall " + large.wallRadius());
            assertTrue(medium.wallRadius() > small.wallRadius() && medium.wallRadius() < large.wallRadius());
            int smallCount = small.buildings().size();
            assertTrue(smallCount >= 18 && smallCount <= 28, "small buildings " + smallCount);
            assertTrue(large.buildings().size() >= 60, "large buildings " + large.buildings().size());
            assertTrue(medium.buildings().size() > smallCount && medium.buildings().size() < large.buildings().size(),
                    "medium buildings " + medium.buildings().size());
            assertEquals(small.wallRadius(), NpcComposition.districtWall(NpcLevel.SMALL));
            assertTrue(NpcComposition.districtReach(NpcLevel.LARGE) > large.wallRadius());
            assertEquals(large.wallRadius(), large.reach());
            Set<Integer> sides = new HashSet<>();
            for (NpcComposition.Gate gate : large.gates()) {
                sides.add(gate.side());
            }
            assertTrue(large.gates().size() >= 4 && sides.size() == 4, "large gates " + large.gates());
            assertTrue(small.gates().size() >= 2, "small gates " + small.gates());
            assertFalse(small.stoneWall());
            assertTrue(large.stoneWall());
        }
    }

    @Test
    void aLargeCapitalIsBuiltOfManyTemplates() {
        Set<String> taverns = new HashSet<>();
        Set<String> markets = new HashSet<>();
        for (int i = 0; i < SEEDS; i++) {
            NpcComposition large = of(NpcLevel.LARGE, seed(i));
            String at = "seed " + i + ": ";
            assertTrue(templates(large, Kind.SMALL_HOUSE, Kind.LARGE_HOUSE).size() >= 4, at + "houses");
            assertTrue(templates(large, Kind.WORKSHOP).size() >= 3, at + "workshops");
            assertTrue(templates(large, Kind.STOREHOUSE, Kind.STORAGE_YARD).size() >= 2, at + "storehouses");
            assertTrue(templates(large, Kind.BARRACKS, Kind.BARRACKS_HALL).size() >= 2, at + "barracks");
            assertTrue(templates(large, Kind.WELL).size() >= 2, at + "wells");
            taverns.addAll(templates(large, Kind.TAVERN));
            markets.addAll(templates(large, Kind.MARKET));
            int mirrored = 0;
            int rows = 0;
            int away = 0;
            for (Plot plot : large.buildings()) {
                if (!row(plot)) {
                    continue;
                }
                rows++;
                if (plot.mirror()) {
                    mirrored++;
                }
                if (plot.facing() != NpcComposition.facing(plot.gx(), plot.gz())) {
                    away++;
                }
            }
            assertTrue(mirrored > 0 && mirrored < rows, at + "some plots are mirrored, some not: " + mirrored + " of " + rows);
            assertTrue(away * 4 >= rows, at + "not every front looks at the throne: " + away + " of " + rows);
        }
        assertTrue(taverns.size() >= 2, "taverns " + taverns);
        assertTrue(markets.size() >= 2, "markets " + markets);
    }

    @Test
    void everyoneHasABedAndMostHaveWork() {
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition composition = of(level, seed(i));
                String at = level + " seed " + i + ": ";
                int townHomes = 0;
                for (int t = 0; t < composition.towns(); t++) {
                    for (Plot plot : composition.town(t)) {
                        if (!NpcComposition.bunks(plot.template())) {
                            townHomes += NpcComposition.beds(plot.template());
                        }
                    }
                }
                assertEquals(RULES.towns(level), composition.towns());
                assertEquals(composition.homes() + townHomes + composition.soldiers(), composition.citizens(), at);
                assertEquals(RULES.soldiers(level), composition.soldiers(), at);
                if (level == NpcLevel.SMALL) {
                    assertTrue(composition.bunks() >= 16, at + "bunks " + composition.bunks());
                }
                if (level == NpcLevel.LARGE) {
                    assertTrue(composition.bunks() >= 50, at + "bunks " + composition.bunks());
                }
                int workers = 0;
                for (int count : composition.jobs().values()) {
                    workers += count;
                }
                int civilians = composition.citizens() - composition.soldiers();
                assertTrue(workers <= civilians, at + workers + " workers for " + civilians);
                assertTrue(workers * 2 > civilians, at + "fewer than half are without work: " + workers + " of " + civilians);
                assertTrue(composition.fields() >= 1, at);
                assertTrue(composition.fields() <= composition.fieldSpots().size(), at);
            }
        }
    }

    @Test
    void theGuardsHaveTheirPostsInsideTheWall() {
        String[] sides = {"north", "east", "south", "west"};
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition composition = of(level, seed(i));
                String at = level + " seed " + i + ": ";
                List<String> names = new ArrayList<>();
                for (Post post : composition.posts()) {
                    names.add(post.name());
                    for (int[] point : post.points()) {
                        assertTrue(Math.max(Math.abs(point[0]), Math.abs(point[1])) < composition.wallRadius(),
                                at + post.name() + " " + Arrays.toString(point));
                    }
                }
                for (String name : List.of("square", "palace_door", "palace_hall", "stores", "stores_round", "wall_round")) {
                    assertTrue(names.contains(name), at + "no post " + name);
                }
                for (NpcComposition.Gate gate : composition.gates()) {
                    assertTrue(composition.post("gate_" + sides[gate.side()]).isPresent(), at + "no post at " + gate);
                }
                int[] palace = NpcComposition.footprint(composition.buildings().get(0));
                for (int[] point : composition.post("palace_hall").orElseThrow().points()) {
                    assertTrue(point[0] >= palace[0] && point[0] <= palace[2] && point[1] >= palace[1] && point[1] <= palace[3],
                            at + "the hall is in the palace: " + Arrays.toString(point));
                }
                assertTrue(composition.post("wall_round").orElseThrow().points().size() >= 4);
            }
        }
    }

    @Test
    void everyFrontOfARowLooksAtAWay() {
        Set<Integer> ways = Set.of(NpcComposition.GROUND_STREET, NpcComposition.GROUND_AVENUE, NpcComposition.GROUND_SQUARE,
                NpcComposition.GROUND_WALK);
        List<String> failures = new ArrayList<>();
        int[] byLevel = new int[3];
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition composition = of(level, seed(i));
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
                        found = ways.contains(composition.ground(cell[0], cell[1]));
                    }
                    if (!found) {
                        byLevel[level.ordinal()]++;
                        if (failures.size() < 6) {
                            failures.add(level + " seed " + i + ": " + plot);
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "fronts that look at no way, by level " + Arrays.toString(byLevel) + ": " + failures);
    }

    @Test
    void townsAreRowsAroundTheirHall() {
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS; i++) {
                NpcComposition composition = of(level, seed(i));
                assertEquals(level == NpcLevel.LARGE, composition.townWall() > 0, level.id());
                assertEquals(1, composition.townFields().size());
                for (int t = 0; t < composition.towns(); t++) {
                    List<Plot> town = composition.town(t);
                    String at = level + " seed " + i + " town " + t + ": ";
                    Set<Kind> kinds = new HashSet<>();
                    Set<String> roles = new HashSet<>();
                    for (Plot plot : town) {
                        kinds.add(kind(plot));
                        roles.add(TemplateCatalog.of(plot.template()).role());
                    }
                    assertTrue(kinds.contains(Kind.WELL) && kinds.contains(Kind.TOWER), at + kinds);
                    if (level == NpcLevel.LARGE) {
                        assertTrue(town.size() >= 8 && town.size() <= 10, at + town.size());
                        assertTrue(roles.contains("workshop") && kinds.contains(Kind.BARRACKS), at + kinds);
                    }
                    if (level == NpcLevel.SMALL) {
                        assertTrue(town.size() >= 6 && town.size() <= 8, at + town.size());
                    }
                    for (int a = 0; a < town.size(); a++) {
                        int[] box = NpcComposition.footprint(town.get(a));
                        for (int coordinate : box) {
                            assertTrue(Math.abs(coordinate) < composition.townReach(), at + town.get(a));
                        }
                        for (int b = a + 1; b < town.size(); b++) {
                            assertFalse(overlap(box, NpcComposition.footprint(town.get(b))), at + town.get(a) + " and " + town.get(b));
                        }
                    }
                }
            }
        }
    }

    @Test
    void theWorkFollowsTheBuildings() {
        NpcComposition large = of(NpcLevel.LARGE, 5L);
        Map<String, Integer> jobs = large.jobs();
        assertTrue(jobs.getOrDefault("mage", 0) > 0, "a tower of mages has mages: " + jobs);
        assertTrue(jobs.getOrDefault("merchant", 0) > 0 && jobs.getOrDefault("innkeeper", 0) > 0, jobs.toString());
        assertNotEquals(0, large.buildingsWith("market"));
        assertEquals("paved", large.roadClass());
        assertEquals("path", of(NpcLevel.SMALL, 5L).roadClass());
    }
}
