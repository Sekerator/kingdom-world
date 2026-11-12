package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.ChunkBuckets;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingIndexTest {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether"};

    private static List<BuildingIndex.Entry> buildings(SplittableRandom random, int count) {
        List<BuildingIndex.Entry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(-200, 200);
            int y = random.nextInt(50, 90);
            int z = random.nextInt(-200, 200);
            Box box = new Box(x, y, z, x + random.nextInt(0, 40), y + random.nextInt(0, 12), z + random.nextInt(0, 40));
            double radius = random.nextInt(4) == 0 ? random.nextInt(0, 80) + random.nextDouble() : -1;
            list.add(new BuildingIndex.Entry(DIMENSIONS[random.nextInt(DIMENSIONS.length)], box, radius));
        }
        return list;
    }

    private static double distanceSqr(Box box, int x, int y, int z) {
        double dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
        double dy = Math.max(0, Math.max(box.minY() - y, y - box.maxY()));
        double dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void theIndexAnswersAsLookingAtEveryBuilding() {
        SplittableRandom random = new SplittableRandom(15);
        for (int round = 0; round < 1000; round++) {
            List<BuildingIndex.Entry> entries = buildings(random, random.nextInt(0, 60));
            BuildingIndex index = new BuildingIndex(entries);
            for (int probe = 0; probe < 40; probe++) {
                String dimension = DIMENSIONS[random.nextInt(DIMENSIONS.length)];
                int x = random.nextInt(-260, 260);
                int y = random.nextInt(45, 105);
                int z = random.nextInt(-260, 260);
                List<Integer> holding = new ArrayList<>();
                List<Integer> wells = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    BuildingIndex.Entry entry = entries.get(i);
                    if (!entry.dimension().equals(dimension)) {
                        continue;
                    }
                    if (entry.box().contains(x, y, z)) {
                        holding.add(i);
                    }
                    if (entry.wellRadius() >= 0 && distanceSqr(entry.box(), x, y, z) <= entry.wellRadius() * entry.wellRadius()) {
                        wells.add(i);
                    }
                }
                assertArrayEquals(holding.stream().mapToInt(Integer::intValue).toArray(), index.containing(dimension, x, y, z),
                        "buildings holding " + x + " " + y + " " + z);
                int[] near = index.wellsNear(dimension, x, z);
                List<Integer> candidates = new ArrayList<>();
                for (int i : near) {
                    candidates.add(i);
                }
                assertTrue(candidates.containsAll(wells), "every well within reach is among the candidates");
                for (int i = 1; i < near.length; i++) {
                    assertTrue(near[i] > near[i - 1], "candidates come in order");
                }
            }
        }
    }

    @Test
    void chunkBucketsAgreeWithAPlainList() {
        SplittableRandom random = new SplittableRandom(4);
        ChunkBuckets<Integer> buckets = new ChunkBuckets<>();
        List<Object[]> plain = new ArrayList<>();
        Map<Integer, Object[]> byValue = new HashMap<>();
        for (int step = 0; step < 20_000; step++) {
            String dimension = DIMENSIONS[random.nextInt(DIMENSIONS.length)];
            long chunk = ((long) random.nextInt(-6, 6) << 32) | (random.nextInt(-6, 6) & 0xFFFFFFFFL);
            if (random.nextInt(3) > 0 || plain.isEmpty()) {
                Object[] entry = {dimension, chunk, step};
                plain.add(entry);
                byValue.put(step, entry);
                buckets.add(dimension, chunk, step);
            } else {
                Object[] gone = plain.remove(random.nextInt(plain.size()));
                assertTrue(buckets.remove((String) gone[0], (long) gone[1], (Integer) gone[2]));
            }
            List<Integer> expected = new ArrayList<>();
            for (Object[] entry : plain) {
                if (entry[0].equals(dimension) && (long) entry[1] == chunk) {
                    expected.add((Integer) entry[2]);
                }
            }
            assertEquals(expected, buckets.get(dimension, chunk));
        }
    }
}
