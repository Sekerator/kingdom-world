package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.construction.Box;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellIndexTest {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};

    /**
     * A square as the areas of cities are: a middle and half a side, both edges inside.
     */
    private record Square(String dimension, int x, int z, int radius) {
        boolean contains(String other, int px, int pz) {
            return dimension.equals(other) && Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
        }
    }

    /**
     * A box of a building in a dimension.
     */
    private record Placed(String dimension, Box box) {
    }

    /**
     * A road as its columns: x and z of each block.
     */
    private record Way(String dimension, List<int[]> columns) {
        boolean covers(String other, int x, int z) {
            if (!dimension.equals(other)) {
                return false;
            }
            for (int[] column : columns) {
                if (column[0] == x && column[1] == z) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String dimension(Random random) {
        return DIMENSIONS[random.nextInt(DIMENSIONS.length)];
    }

    private static <T> Optional<T> firstOf(List<T> list, java.util.function.Predicate<T> test) {
        for (T value : list) {
            if (test.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether every thing of a part comes in the order of the whole.
     */
    private static <T> boolean inOrder(List<T> part, List<T> whole) {
        int at = 0;
        for (T value : part) {
            while (at < whole.size() && whole.get(at) != value) {
                at++;
            }
            if (at == whole.size()) {
                return false;
            }
        }
        return true;
    }

    private static int near(Random random, int center, int reach) {
        return center + random.nextInt(2 * reach + 1) - reach;
    }

    @Test
    void theCityOfAPlaceIsTheOneAFullLookFinds() {
        Random random = new Random(15_001);
        for (int round = 0; round < 1_000; round++) {
            List<Square> squares = new ArrayList<>();
            int count = random.nextInt(25);
            for (int i = 0; i < count; i++) {
                int radius = switch (random.nextInt(40)) {
                    case 0 -> -1 - random.nextInt(5);
                    case 1 -> 20_000 + random.nextInt(50_000);
                    default -> random.nextInt(1_500);
                };
                squares.add(new Square(dimension(random), random.nextInt(10_001) - 5_000, random.nextInt(10_001) - 5_000, radius));
            }
            CellIndex<Square> index = new CellIndex<>(9);
            for (Square square : squares) {
                index.add(square.dimension(), (long) square.x() - square.radius(), (long) square.z() - square.radius(),
                        (long) square.x() + square.radius(), (long) square.z() + square.radius(), square);
            }
            assertEquals(squares, index.all());
            for (int ask = 0; ask < 60; ask++) {
                String dimension = dimension(random);
                int x;
                int z;
                if (!squares.isEmpty() && random.nextInt(3) > 0) {
                    Square square = squares.get(random.nextInt(squares.size()));
                    int reach = Math.max(1, Math.min(Math.abs(square.radius()) + 3, 100_000));
                    x = near(random, square.x(), reach);
                    z = near(random, square.z(), reach);
                    if (random.nextBoolean()) {
                        // Right on an edge, or just past it.
                        x = square.x() + square.radius() + random.nextInt(3) - 1;
                    }
                } else if (random.nextInt(20) == 0) {
                    x = (random.nextBoolean() ? 1 : -1) * (CellIndex.FAR + random.nextInt(1_000));
                    z = random.nextInt(10_001) - 5_000;
                } else {
                    x = random.nextInt(20_001) - 10_000;
                    z = random.nextInt(20_001) - 10_000;
                }
                int px = x;
                int pz = z;
                List<Square> candidates = index.at(dimension, px, pz);
                assertTrue(inOrder(candidates, squares), "candidates keep the order of the squares");
                assertEquals(firstOf(squares, square -> square.contains(dimension, px, pz)),
                        firstOf(candidates, square -> square.contains(dimension, px, pz)),
                        "round " + round + " at " + dimension + " " + px + " " + pz);
            }
        }
    }

    @Test
    void theBuildingsAtAPlaceAreThoseAFullLookFinds() {
        Random random = new Random(15_002);
        for (int round = 0; round < 1_000; round++) {
            List<Placed> buildings = new ArrayList<>();
            int count = random.nextInt(40);
            for (int i = 0; i < count; i++) {
                int minX = random.nextInt(2_001) - 1_000;
                int minY = random.nextInt(100);
                int minZ = random.nextInt(2_001) - 1_000;
                int wide = random.nextInt(60) == 0 ? 1_100 + random.nextInt(500) : random.nextInt(48);
                buildings.add(new Placed(dimension(random), new Box(minX, minY, minZ, minX + random.nextInt(wide + 1), minY + random.nextInt(20),
                        minZ + random.nextInt(wide + 1))));
            }
            CellIndex<Placed> index = new CellIndex<>(4);
            for (Placed building : buildings) {
                Box box = building.box();
                index.add(building.dimension(), box.minX(), box.minZ(), box.maxX(), box.maxZ(), building);
            }
            for (int ask = 0; ask < 60; ask++) {
                String dimension = dimension(random);
                int x;
                int y;
                int z;
                if (!buildings.isEmpty() && random.nextInt(3) > 0) {
                    Box box = buildings.get(random.nextInt(buildings.size())).box();
                    x = near(random, (box.minX() + box.maxX()) / 2, box.sizeX() / 2 + 2);
                    y = near(random, (box.minY() + box.maxY()) / 2, box.sizeY() / 2 + 2);
                    z = near(random, (box.minZ() + box.maxZ()) / 2, box.sizeZ() / 2 + 2);
                } else {
                    x = random.nextInt(2_201) - 1_100;
                    y = random.nextInt(130);
                    z = random.nextInt(2_201) - 1_100;
                }
                int px = x;
                int py = y;
                int pz = z;
                List<Placed> all = new ArrayList<>();
                for (Placed building : buildings) {
                    if (building.dimension().equals(dimension) && building.box().contains(px, py, pz)) {
                        all.add(building);
                    }
                }
                List<Placed> found = new ArrayList<>();
                for (Placed building : index.at(dimension, px, pz)) {
                    if (building.dimension().equals(dimension) && building.box().contains(px, py, pz)) {
                        found.add(building);
                    }
                }
                assertEquals(all, found, "round " + round + " at " + dimension + " " + px + " " + py + " " + pz);
            }
        }
    }

    @Test
    void theRoadsThroughAColumnAreThoseAFullLookFinds() {
        Random random = new Random(15_003);
        for (int round = 0; round < 1_000; round++) {
            List<Way> roads = new ArrayList<>();
            int count = random.nextInt(12);
            for (int i = 0; i < count; i++) {
                List<int[]> columns = new ArrayList<>();
                int x = random.nextInt(1_001) - 500;
                int z = random.nextInt(1_001) - 500;
                int length = 2 + random.nextInt(200);
                for (int step = 0; step < length; step++) {
                    columns.add(new int[]{x, z});
                    if (random.nextBoolean()) {
                        // A road three blocks wide.
                        columns.add(new int[]{x + 1, z});
                        columns.add(new int[]{x - 1, z});
                    }
                    x += random.nextInt(3) - 1;
                    z += random.nextInt(3) - 1;
                }
                roads.add(new Way(dimension(random), columns));
            }
            CellIndex<Way> index = new CellIndex<>(4);
            for (Way road : roads) {
                Set<Long> chunks = new LinkedHashSet<>();
                for (int[] column : road.columns()) {
                    chunks.add(((long) (column[0] >> 4) << 32) | ((column[1] >> 4) & 0xFFFF_FFFFL));
                }
                for (long chunk : chunks) {
                    index.addCell(road.dimension(), (int) (chunk >> 32), (int) chunk, road);
                }
            }
            assertEquals(roads, index.all(), "every road once, in order");
            for (int ask = 0; ask < 60; ask++) {
                String dimension = dimension(random);
                int x;
                int z;
                if (!roads.isEmpty() && random.nextInt(3) > 0) {
                    List<int[]> columns = roads.get(random.nextInt(roads.size())).columns();
                    int[] column = columns.get(random.nextInt(columns.size()));
                    x = column[0] + random.nextInt(5) - 2;
                    z = column[1] + random.nextInt(5) - 2;
                } else {
                    x = random.nextInt(1_501) - 750;
                    z = random.nextInt(1_501) - 750;
                }
                int px = x;
                int pz = z;
                List<Way> all = new ArrayList<>();
                for (Way road : roads) {
                    if (road.covers(dimension, px, pz)) {
                        all.add(road);
                    }
                }
                List<Way> found = new ArrayList<>();
                for (Way road : index.at(dimension, px, pz)) {
                    if (road.covers(dimension, px, pz)) {
                        found.add(road);
                    }
                }
                assertEquals(all, found, "round " + round + " at " + dimension + " " + px + " " + pz);
            }
        }
    }

    @Test
    void aThingTooWideOrTooFarMakesEveryQuestionLookAtAll() {
        CellIndex<String> index = new CellIndex<>(4);
        index.add("a", 0, 0, 15, 15, "small");
        assertEquals(List.of("small"), index.at("a", 3, 3));
        assertEquals(List.of(), index.at("a", 100, 100));
        assertEquals(List.of(), index.at("b", 3, 3));
        assertEquals(List.of("small"), index.at("a", CellIndex.FAR + 1, 0), "a place far out gets everything");
        assertFalse(index.unsorted());
        index.add("a", 0, 0, 16L * CellIndex.MOST_CELLS, 0, "wide");
        assertTrue(index.unsorted());
        index.add("b", 500, 500, 500, 500, "late");
        assertEquals(List.of("small", "wide", "late"), index.at("b", 100, 100));
    }

    @Test
    void aThingAddedAgainRightAfterItselfIsThereOnce() {
        CellIndex<String> index = new CellIndex<>(4);
        index.addCell("a", 0, 0, "road");
        index.addCell("a", 0, 0, "road");
        index.addCell("a", 1, 0, "road");
        index.addCell("a", 0, 0, "other");
        assertEquals(List.of("road", "other"), index.at("a", 5, 5));
        assertEquals(List.of("road"), index.at("a", 17, 5));
        assertEquals(List.of("road", "other"), index.all());
    }

    @Test
    void negativePlacesFallIntoTheirOwnCells() {
        CellIndex<String> index = new CellIndex<>(9);
        index.add("a", -513, -1, -512, -1, "west");
        assertEquals(List.of("west"), index.at("a", -1_024, -512));
        assertEquals(List.of("west"), index.at("a", -513, -1));
        assertEquals(List.of(), index.at("a", -1_025, -1));
        assertEquals(List.of(), index.at("a", 0, 0));
    }
}
