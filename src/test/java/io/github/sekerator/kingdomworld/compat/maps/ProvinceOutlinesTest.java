package io.github.sekerator.kingdomworld.compat.maps;

import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The outlines of provinces made from their tiles, as the web maps make them (the client makes them the same way).
 */
class ProvinceOutlinesTest {
    private static void fill(Map<Long, char[]> tiles, int id, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                char[] tile = tiles.computeIfAbsent(ProvinceMap.tileKey(x >> 5, z >> 5), key -> new char[ProvinceMap.TILE_CHUNKS]);
                tile[ProvinceMap.indexInTile(x, z)] = (char) id;
            }
        }
    }

    @Test
    void aRectangleALetterLAndAHoleAcrossTiles() {
        Map<Long, char[]> tiles = new HashMap<>();
        // A rectangle over four tiles.
        fill(tiles, 1, 20, 20, 50, 40);
        // A letter L.
        fill(tiles, 2, 60, 0, 64, 30);
        fill(tiles, 2, 65, 26, 80, 30);
        // A ring with a province inside it.
        fill(tiles, 3, -40, -40, -20, -20);
        fill(tiles, 4, -33, -33, -27, -27);
        Map<Integer, List<ChunkOutlines.Area>> outlines = WebMaps.outlines(tiles);

        assertEquals(1, outlines.get(1).size());
        assertEquals(4, outlines.get(1).get(0).outer().corners().size());
        assertEquals(31 * 21, outlines.get(1).get(0).chunks());

        assertEquals(1, outlines.get(2).size());
        assertEquals(6, outlines.get(2).get(0).outer().corners().size());

        assertEquals(1, outlines.get(3).size());
        assertEquals(1, outlines.get(3).get(0).holes().size(), "the province inside is a hole of the ring");
        assertEquals(21 * 21 - 7 * 7, outlines.get(3).get(0).chunks());
        assertEquals(7 * 7, outlines.get(4).get(0).chunks());
    }
}
