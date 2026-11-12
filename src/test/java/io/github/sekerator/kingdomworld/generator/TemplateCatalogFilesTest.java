package io.github.sekerator.kingdomworld.generator;

import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * The named places of the table are those the generator draws, measured from the middle of the ground and the street.
     */
    @Test
    void thePlacesOfTheTableAreThoseOfTheDrawings() {
        Map<String, BuildingGenerator.Building> drawn = drawn();
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            BuildingGenerator.Building building = drawn.get(entry.id());
            Structure structure = building.structure();
            Map<String, List<TemplateCatalog.Spot>> expected = new LinkedHashMap<>();
            building.points().forEach((name, positions) -> {
                List<TemplateCatalog.Spot> spots = new ArrayList<>();
                for (int[] p : positions) {
                    spots.add(new TemplateCatalog.Spot(p[0] - (structure.sizeX - 1) / 2, p[1] - building.anchorY(), p[2] - (structure.sizeZ - 1) / 2));
                }
                expected.put(name, spots);
            });
            assertEquals(expected, entry.points(), "the places of " + entry.id());
        }
    }

    private static int count(Structure structure, String prefix) {
        int found = 0;
        for (int y = 0; y < structure.sizeY; y++) {
            for (int z = 0; z < structure.sizeZ; z++) {
                for (int x = 0; x < structure.sizeX; x++) {
                    if (structure.get(x, y, z).startsWith(prefix)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Whether a block of the given kind stands within {@code reach} blocks of every post of work of a structure.
     */
    private static boolean nearThePost(Structure structure, String prefix, int reach) {
        for (int y = 0; y < structure.sizeY; y++) {
            for (int z = 0; z < structure.sizeZ; z++) {
                for (int x = 0; x < structure.sizeX; x++) {
                    if (!structure.get(x, y, z).startsWith("kingdomworld:work_post")) {
                        continue;
                    }
                    boolean near = false;
                    for (int dy = -1; dy <= 1 && !near; dy++) {
                        for (int dz = -reach; dz <= reach && !near; dz++) {
                            for (int dx = -reach; dx <= reach && !near; dx++) {
                                near = structure.inside(x + dx, y + dy, z + dz) && structure.get(x + dx, y + dy, z + dz).startsWith(prefix);
                            }
                        }
                    }
                    if (!near) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * The buildings of the institutions hold what their work needs: the guild its desk by the post of the registrar, the
     * treasury its mint by the post of the minter and the place of its bank left out on a floor, the court and the royal
     * barracks the block of barracks for their bunks, the infirmary its places of healing and no bed, the yards one post.
     */
    @Test
    void theBuildingsOfTheInstitutionsHoldWhatTheirWorkNeeds() {
        Map<String, BuildingGenerator.Building> drawn = drawn();
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            BuildingGenerator.Building building = drawn.get(entry.id());
            Structure structure = building.structure();
            String name = entry.id();
            int posts = count(structure, "kingdomworld:work_post");
            switch (entry.kind()) {
                case GUILD -> {
                    assertEquals(1, posts, "posts of " + name);
                    assertEquals(1, count(structure, "kingdomworld:guild_desk"), "desks of " + name);
                    assertTrue(nearThePost(structure, "kingdomworld:guild_desk", 3), "the desk of " + name + " is far from the post");
                }
                case TREASURY -> {
                    assertEquals(1, posts, "posts of " + name);
                    assertEquals(1, count(structure, "kingdomworld:mint"), "mints of " + name);
                    assertTrue(nearThePost(structure, "kingdomworld:mint", 3), "the mint of " + name + " is far from the post");
                    List<TemplateCatalog.Spot> bank = entry.points("bank");
                    assertEquals(1, bank.size(), "the place of the bank of " + name);
                    int[] at = building.points().get("bank").get(0);
                    assertTrue(structure.leftOut(at[0], at[1], at[2]), "the bank of " + name + " is not left out for the bank block");
                    assertTrue(structure.solid(at[0], at[1] - 1, at[2]), "the bank of " + name + " has no floor");
                }
                case COURT, ROYAL_BARRACKS -> {
                    assertTrue(count(structure, "kingdomworld:barracks") >= 1, name + " has no block of barracks for its bunks");
                    assertTrue(entry.beds() >= 1, name + " has no bunks");
                }
                case INFIRMARY -> {
                    assertTrue(count(structure, "kingdomworld:infirmary") >= 2, name + " heals too few");
                    assertEquals(0, entry.beds(), "beds of an infirmary would be homes");
                }
                case YARD -> assertEquals(1, posts, "posts of " + name);
                case MAGE_TOWER -> {
                    assertEquals(1, count(structure, "kingdomworld:magic_focus"), "magic focus of " + name);
                    assertTrue(entry.beds() >= 1, "the mages of " + name + " have no beds");
                }
                case CASTLE, CASTLE_WARD, BARBICAN, DECORATION -> {
                    assertEquals(0, entry.beds(), "beds of " + name + " would be homes");
                    assertEquals(0, posts, "posts of " + name);
                }
                case POOR_HOUSE, CRAFT_HOUSE, MERCHANT_HOUSE, NOBLE_HOUSE -> {
                    assertTrue(entry.beds() >= 1, name + " is a house without a bed");
                    assertEquals(0, posts, "posts of " + name);
                }
                default -> {
                }
            }
            boolean newKind = entry.kind().ordinal() >= TemplateCatalog.Kind.CASTLE_WARD.ordinal()
                    && !entry.role().equals("house");
            if (newKind) {
                assertFalse(entry.points("door").isEmpty(), name + " names no door for a guard");
            }
            for (String state : structure.used()) {
                assertFalse(state.startsWith("kingdomworld:throne") || state.startsWith("kingdomworld:bank"), name + " holds " + state);
                assertFalse(state.contains("potted_") || state.contains("waterlogged=true"), name + " holds " + state);
            }
        }
    }

    /**
     * The castles: around the throne the hall where its guards stand, the door of the keep, the gate, the walk of the wall
     * and the treasury named for the guards.
     */
    @Test
    void castlesNameThePlacesOfTheirGuards() {
        int castles = 0;
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            if (entry.kind() != TemplateCatalog.Kind.CASTLE || entry.id().equals("kingdomworld:castle")) {
                continue;
            }
            castles++;
            List<TemplateCatalog.Spot> hall = entry.points("palace_hall");
            assertTrue(hall.contains(new TemplateCatalog.Spot(-2, 0, 3)) && hall.contains(new TemplateCatalog.Spot(2, 0, 3)),
                    entry.id() + " has no guards beside the throne: " + hall);
            for (String place : List.of("palace_door", "castle_gate", "treasury", "door")) {
                assertFalse(entry.points(place).isEmpty(), entry.id() + " names no " + place);
            }
            assertTrue(entry.points("castle_wall").size() >= 4, entry.id() + " has too few places on the walk of its wall");
            assertEquals(1, entry.width() % 2, entry.id() + " is no odd width");
            assertEquals(entry.width(), entry.depth(), entry.id() + " is not square around its throne");
        }
        assertTrue(castles >= 3, "castles for small, medium and large kingdoms");
    }
}
