package io.github.sekerator.kingdomworld.generator;

import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plans of the capitals of the kingdoms of the world are made from a table of the built-in templates; this holds the table
 * to the buildings the generator draws, so that a plan never reserves other ground than a building takes or counts beds it
 * does not have.
 */
class TemplateCatalogFilesTest {
    private static Map<String, BuildingGenerator.Building> drawn() {
        Map<String, BuildingGenerator.Building> map = new HashMap<>();
        for (BuildingGenerator.Building building : BuildingGenerator.buildings()) {
            map.put("kingdomworld:" + building.name(), building);
        }
        return map;
    }

    private static int beds(Structure structure) {
        int beds = 0;
        for (int y = 0; y < structure.sizeY; y++) {
            for (int z = 0; z < structure.sizeZ; z++) {
                for (int x = 0; x < structure.sizeX; x++) {
                    String state = structure.get(x, y, z);
                    if (state.contains("_bed[") && state.contains("part=head")) {
                        beds++;
                    }
                }
            }
        }
        return beds;
    }

    @Test
    void everyTemplateOfTheCatalogIsDrawnWithItsGroundRoleAndBeds() {
        Map<String, BuildingGenerator.Building> drawn = drawn();
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            BuildingGenerator.Building building = drawn.get(entry.id());
            assertNotNull(building, "The generator draws no " + entry.id());
            Structure structure = building.structure();
            assertEquals(entry.width(), structure.sizeX, entry.id() + " along its front");
            assertEquals(entry.depth(), structure.sizeZ, entry.id() + " from back to front");
            assertEquals(entry.role().isEmpty() ? "none" : entry.role(), building.role(), "the role of " + entry.id());
            // The rooms of a tavern are for guests: its beds are no homes.
            if (!entry.role().equals("tavern")) {
                assertEquals(entry.beds(), beds(structure), "beds of " + entry.id());
            }
            assertEquals(entry.upgrade(), building.level() > 1, entry.id() + " as an upgrade");
        }
    }

    @Test
    void workshopsMarketsAndTavernsHaveOnePostOfWork() {
        for (BuildingGenerator.Building building : BuildingGenerator.buildings()) {
            if (!building.role().equals("workshop") && !building.role().equals("market") && !building.role().equals("tavern")
                    && !building.role().equals("mage_tower")) {
                continue;
            }
            Structure structure = building.structure();
            int posts = 0;
            for (int y = 0; y < structure.sizeY; y++) {
                for (int z = 0; z < structure.sizeZ; z++) {
                    for (int x = 0; x < structure.sizeX; x++) {
                        if (structure.get(x, y, z).startsWith("kingdomworld:work_post")) {
                            posts++;
                        }
                    }
                }
            }
            assertEquals(1, posts, "posts of work in " + building.name());
        }
    }

    @Test
    void palacesLeaveTheThroneOut() {
        for (BuildingGenerator.Building building : BuildingGenerator.buildings()) {
            if (!building.type().equals("palace")) {
                continue;
            }
            Structure structure = building.structure();
            // The throne stands in the middle of the ground of a palace, on the layer above its floor.
            int x = (structure.sizeX - 1) / 2;
            int z = (structure.sizeZ - 1) / 2;
            assertTrue(structure.get(x, 1, z).isEmpty() && structure.get(x, 0, z).isEmpty(),
                    building.name() + " builds over the place of the throne");
            assertEquals(1, building.anchorY(), building.name() + " lies level with the ground");
        }
    }
}
