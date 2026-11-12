package io.github.sekerator.kingdomworld.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * The outer ward of the great castle: the buildings that stand in the ring of open ground between the citadel and the outer
 * wall of a large kingdom. The barbican stands in the line of the outer wall over its moat, the royal stables, the barracks
 * of the royal guard and the castle garden stand inside. They are drawn in the stone of the castle, with round towers under
 * cones, battlements and banners in the colour of the kingdom, so that they read as one family with the citadel.
 */
final class CastleWard {
    private static final String AIR = Structure.AIR;
    private static final String SPRUCE_PLANKS = "minecraft:spruce_planks";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String SPRUCE_LOG = "minecraft:spruce_log";
    private static final String OAK_LOG = "minecraft:oak_log";
    private static final String STRIPPED_OAK = "minecraft:stripped_oak_log";
    private static final String SPRUCE_STAIRS = "minecraft:spruce_stairs";
    private static final String SPRUCE_SLAB = "minecraft:spruce_slab";
    private static final String DOOR = "minecraft:spruce_door";
    private static final String HATCH = "minecraft:oak_trapdoor";
    private static final String DIRT = "minecraft:dirt";
    private static final String COARSE_DIRT = "minecraft:coarse_dirt";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";
    private static final String GRAVEL = "minecraft:gravel";
    private static final String WATER = "minecraft:water[level=0]";
    private static final String HAY = "minecraft:hay_block[axis=y]";
    private static final String BARREL = "minecraft:barrel[facing=up,open=false]";
    private static final String TABLE_TOP = "minecraft:oak_pressure_plate[powered=false]";
    private static final String GRINDSTONE = "minecraft:grindstone[face=floor,facing=south]";

    private CastleWard() {
    }

    static List<BuildingGenerator.Building> buildings() {
        List<BuildingGenerator.Building> list = new ArrayList<>();
        list.add(new BuildingGenerator.Building("classic_barbican", "defense", "gatehouse", "minecraft:iron_bars", 12, STREET, 20,
                barbican(), "tower", 1, "")
                .at("castle_gate", PASS_X1, STREET, 8).at("castle_gate", PASS_X2, STREET, 8)
                .at("castle_wall", 1, WALK, 9).at("castle_wall", 23, WALK, 9)
                .at("door", 12, STREET, 19));
        list.add(new BuildingGenerator.Building("classic_stables", "farming", "stables", "minecraft:hay_block", 7, 0, 20,
                royalStables(), "none", 1, "")
                .at("door", 7, 1, 14));
        list.add(new BuildingGenerator.Building("classic_guard_barracks", "defense", "barracks", "kingdomworld:barracks", 8, 0, 20,
                royalGuardBarracks(), "barracks", 1, "").params("\"bunks\": 16")
                .at("door", 8, 1, 15).at("guard", 7, 1, 13).at("guard", 9, 1, 13));
        list.add(new BuildingGenerator.Building("classic_garden", "decoration", "garden", "minecraft:oak_sapling", 8, 0, 16,
                castleGarden(), "none", 1, "")
                .at("door", 8, 0, 16));
        return list;
    }

    // ---------------------------------------------------------------- small shapes

    /**
     * The facing that turns a stair on the ring around a round plan towards its middle.
     */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    /**
     * A ring of stairs just outside a round plan: brackets hanging upside down under an eave, or the sloping foot of a tower.
     */
    private static void stairRing(Structure s, int cx, int y, int cz, int diameter, String stairs, boolean upsideDown) {
        int radius = (diameter + 1) / 2;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter) && s.inside(cx + dx, y, cz + dz)
                        && !s.solid(cx + dx, y, cz + dz)) {
                    String facing = inward(dx, dz);
                    s.set(cx + dx, y, cz + dz, upsideDown ? Kit.upside(stairs, facing) : Kit.stairs(stairs, facing));
                }
            }
        }
    }

    /**
     * A stair on the edge of a round plan, turned towards its middle; where the edge turns a corner the stair is an outer
     * corner, so that the ring reads as round.
     */
    private static String roundStair(int dx, int dz, int diameter, String stairs, boolean upsideDown) {
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        boolean outX = sx != 0 && !Kit.inDisc(dx + sx, dz, diameter);
        boolean outZ = sz != 0 && !Kit.inDisc(dx, dz + sz, diameter);
        String facing;
        String shape = "straight";
        if (outX && outZ) {
            facing = sz < 0 ? "south" : "north";
            shape = (sz < 0) == (sx < 0) ? "outer_left" : "outer_right";
        } else if (outX) {
            facing = sx > 0 ? "west" : "east";
        } else if (outZ) {
            facing = sz > 0 ? "north" : "south";
        } else {
            facing = inward(dx, dz);
        }
        return stairs + "[facing=" + facing + ",half=" + (upsideDown ? "top" : "bottom") + ",shape=" + shape + ",waterlogged=false]";
    }

    /**
     * A cone over a round tower: an eave of stairs one block out over the wall, then rings that shrink by one block on every
     * side, each as many layers tall as the next number of {@code rises}, the upper edge of every ring of stairs so that the
     * cone slopes instead of stepping. A shell two blocks thick, hollow inside.
     *
     * @param y the layer of the eave
     * @return the layer over the point
     */
    private static int coneRoof(Structure s, int cx, int y, int cz, int diameter, int[] rises, Kit.Roof roof) {
        int radius = (diameter + 1) / 2;
        // The eave.
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Kit.inDisc(dx, dz, diameter + 2) && !Kit.inDisc(dx, dz, diameter)) {
                    s.set(cx + dx, y, cz + dz, roundStair(dx, dz, diameter + 2, roof.stairs(), false));
                } else if (Kit.inDisc(dx, dz, diameter) && !Kit.inDisc(dx, dz, diameter - 4)) {
                    s.set(cx + dx, y, cz + dz, roof.block());
                }
            }
        }
        int layer = y + 1;
        int d = diameter;
        for (int ring = 0; d >= 1; ring++, d -= 2) {
            int rise = ring < rises.length ? rises[ring] : 2;
            for (int step = 0; step < rise; step++) {
                boolean top = step == rise - 1 && d > 1;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!Kit.inDisc(dx, dz, d) || d > 4 && Kit.inDisc(dx, dz, d - 4)) {
                            continue;
                        }
                        boolean edge = !Kit.inDisc(dx, dz, d - 2);
                        s.set(cx + dx, layer, cz + dz, top && edge ? roundStair(dx, dz, d, roof.stairs(), false) : roof.block());
                    }
                }
                layer++;
            }
        }
        return layer;
    }

    /**
     * A barn roof whose ridge runs along z, so that a gable looks at the street: the lower {@code steepRows} rows rise two
     * blocks each (a block under a stair), the rows above them one, the ridge is a slab, and the gable walls are filled with
     * {@code gable} under it.
     *
     * @param y the layer of the eaves
     * @return for every column from {@code x1} the lowest layer of the roof over it
     */
    private static int[] barnRoof(Structure s, int x1, int x2, int z1, int z2, int y, int steepRows, Kit.Roof roof, String gable,
                                  int wallZ1, int wallZ2) {
        int[] under = new int[x2 - x1 + 1];
        int height = y;
        for (int row = 0; x1 + row <= x2 - row; row++) {
            boolean steep = row > 0 && row <= steepRows;
            if (row > 0) {
                height += steep ? 2 : 1;
            }
            int west = x1 + row;
            int east = x2 - row;
            if (steep) {
                s.fill(west, height - 1, z1, west, height - 1, z2, roof.block()).fill(east, height - 1, z1, east, height - 1, z2, roof.block());
            }
            if (west < east) {
                s.fill(west, height, z1, west, height, z2, Kit.stairs(roof.stairs(), "east"));
                s.fill(east, height, z1, east, height, z2, Kit.stairs(roof.stairs(), "west"));
            } else {
                s.fill(west, height, z1, west, height, z2, Kit.slab(roof.slab()));
            }
            under[west - x1] = steep ? height - 1 : height;
            under[east - x1] = under[west - x1];
        }
        for (int wall : new int[]{wallZ1, wallZ2}) {
            for (int x = x1 + 1; x < x2; x++) {
                for (int h = y; h < under[x - x1]; h++) {
                    if (!s.solid(x, h, wall)) {
                        s.set(x, h, wall, gable);
                    }
                }
            }
        }
        return under;
    }

    /**
     * A lamp post: a post of rough stone wall with a lantern on it.
     */
    private static void lampPost(Structure s, int x, int y, int z, int height) {
        s.fill(x, y, z, x, y + height - 1, z, Kit.ROUGH_FENCE);
        s.set(x, y + height, z, Kit.lantern());
    }

    /**
     * A table of a fence with a pressure plate on it.
     */
    private static void table(Structure s, int x, int y, int z) {
        s.set(x, y, z, Kit.fence("spruce")).set(x, y + 1, z, TABLE_TOP);
    }

    // ---------------------------------------------------------------- the barbican

    /** The layer level with the street outside: the moat and the foundations lie under it. */
    private static final int STREET = 3;
    /** The top of the outer wall and the feet of those who walk on it, six above the street. */
    private static final int WALK_FLOOR = 8;
    private static final int WALK = 9;
    /** The line of the outer wall through the barbican, three thick; its battlements stand on the outer row. */
    private static final int WALL_Z1 = 9;
    private static final int WALL_Z2 = 11;
    /** The moat: its back lining, the water between, its front lining at the street. */
    private static final int MOAT_BACK = 14;
    private static final int MOAT_FRONT = 20;
    /** The two round towers. */
    private static final int TOWER_Z = 11;
    private static final int TOWER_WEST = 5;
    private static final int TOWER_EAST = 19;
    private static final int TOWER_D = 7;
    private static final int TOWER_TOP = 16;
    /** The gatehouse between the towers, the passage through it and the roof over the room above the gate. */
    private static final int GATE_X1 = 8;
    private static final int GATE_X2 = 16;
    private static final int GATE_Z1 = 4;
    private static final int GATE_Z2 = 13;
    private static final int PASS_X1 = 11;
    private static final int PASS_X2 = 13;
    private static final int PASS_TOP = 6;
    private static final int DECK = 12;

    /**
     * The outer gate of the castle ward, standing in the line of the outer wall. A moat lined with rough stone runs along
     * the whole front; a stone bridge with parapets and lanterns crosses it to a deep double arch between two round towers
     * under slate cones. Inside the arch hangs the portcullis, half raised, and the passage runs on under a vault with murder
     * holes to the arch on the side of the ward. Over the gate lies the guard room with the windlass of the portcullis, and
     * over that a roof with battlements that juts out over the gate. The wall walk comes in from both sides through the
     * towers; stairs inside the gatehouse lead up to it from the ward, ladders climb the towers to their lookout rooms.
     */
    static Structure barbican() {
        Structure s = new Structure(25, 30, 21);
        barbicanGround(s);
        // The outer wall runs through the whole width; the towers and the gatehouse are cut out of it below.
        s.fill(0, 0, WALL_Z1, 24, STREET - 1, WALL_Z2, Kit.ROUGH);
        s.fill(0, STREET, WALL_Z1, 24, WALK_FLOOR, WALL_Z2, Kit.WALL);
        s.fill(0, STREET, WALL_Z1, 24, STREET, WALL_Z2, Kit.ROUGH);
        Kit.battlements(s, 0, WALL_Z2, 24, WALL_Z2, WALK, Kit.WALL);
        gatehouse(s);
        barbicanTower(s, TOWER_WEST, 1);
        barbicanTower(s, TOWER_EAST, -1);
        bridge(s);
        wardSide(s);
        // Lanterns in the crenels of the wall at both ends of the walk.
        s.set(0, WALK + 1, WALL_Z2, Kit.lantern()).set(24, WALK + 1, WALL_Z2, Kit.lantern());
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * The ground: dirt and grass on the side of the ward and on the berm before the wall, the moat along the front with its
     * bed and linings of rough stone, trimmed copings on both banks and water in its lowest open layer.
     */
    private static void barbicanGround(Structure s) {
        s.fill(0, 0, 0, 24, STREET - 2, MOAT_BACK - 1, DIRT);
        s.fill(0, STREET - 1, 0, 24, STREET - 1, MOAT_BACK - 1, GRASS);
        s.fill(0, 0, MOAT_BACK, 24, 0, MOAT_FRONT, Kit.ROUGH);
        s.fill(0, 1, MOAT_BACK, 24, STREET - 1, MOAT_BACK, Kit.ROUGH);
        s.fill(0, 1, MOAT_FRONT, 24, STREET - 1, MOAT_FRONT, Kit.ROUGH);
        s.fill(0, 1, MOAT_BACK, 0, STREET - 1, MOAT_FRONT, Kit.ROUGH);
        s.fill(24, 1, MOAT_BACK, 24, STREET - 1, MOAT_FRONT, Kit.ROUGH);
        s.fill(0, STREET - 1, MOAT_BACK, 24, STREET - 1, MOAT_BACK, Kit.TRIM);
        s.fill(0, STREET - 1, MOAT_FRONT, 24, STREET - 1, MOAT_FRONT, Kit.TRIM);
        s.fill(0, STREET - 1, MOAT_BACK, 0, STREET - 1, MOAT_FRONT, Kit.TRIM);
        s.fill(24, STREET - 1, MOAT_BACK, 24, STREET - 1, MOAT_FRONT, Kit.TRIM);
        s.fill(1, 1, MOAT_BACK + 1, 23, 1, MOAT_FRONT - 1, WATER);
    }

    /**
     * A round tower of the barbican: a solid footing, a sloping foot of rough stone stairs, a room on the ground with a door
     * to the ward, a room at the height of the walk with doors to the wall and to the room over the gate, a lookout room
     * with a door to the roof of the gatehouse, brackets under the eave and a slate cone with a flag. A ladder climbs
     * through all floors. {@code side} is 1 for the west tower, -1 for the east one, so that both are drawn alike.
     */
    private static void barbicanTower(Structure s, int cx, int side) {
        int cz = TOWER_Z;
        for (int y = 0; y < STREET; y++) {
            Kit.disc(s, cx, y, cz, TOWER_D, Kit.ROUGH);
        }
        // The sloping foot stands on rough stone down to the bed of the moat.
        stairRing(s, cx, STREET, cz, TOWER_D, Kit.ROUGH_STAIRS, false);
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (Kit.inDisc(dx, dz, TOWER_D + 2) && !Kit.inDisc(dx, dz, TOWER_D)) {
                    s.fill(cx + dx, 0, cz + dz, cx + dx, STREET - 1, cz + dz, Kit.ROUGH);
                }
            }
        }
        Kit.tube(s, cx, STREET, TOWER_TOP, cz, TOWER_D, Kit.WALL);
        Kit.tube(s, cx, STREET, STREET + 1, cz, TOWER_D, Kit.ROUGH);
        Kit.tube(s, cx, WALK_FLOOR, WALK_FLOOR, cz, TOWER_D, Kit.TRIM);
        Kit.tube(s, cx, DECK, DECK, cz, TOWER_D, Kit.TRIM);
        // Floors and rooms.
        Kit.disc(s, cx, STREET - 1, cz, 5, SPRUCE_PLANKS);
        for (int y = STREET; y <= TOWER_TOP; y++) {
            Kit.disc(s, cx, y, cz, 5, AIR);
        }
        Kit.disc(s, cx, WALK_FLOOR, cz, 5, SPRUCE_PLANKS);
        Kit.disc(s, cx, DECK, cz, 5, SPRUCE_PLANKS);
        // Brackets under the eave and the cone with a flag on its point.
        stairRing(s, cx, TOWER_TOP, cz, TOWER_D, Kit.TRIM_STAIRS, true);
        int top = coneRoof(s, cx, TOWER_TOP + 1, cz, TOWER_D, new int[]{2, 3, 3, 2}, Kit.SLATE);
        s.set(cx, top, cz, Kit.fence("spruce"));
        s.set(cx, top + 1, cz, Kit.banner("red", 0));
        // The door to the ward and the ladder up the back.
        int ladderX = cx - side;
        s.door(cx, STREET, cz - 3, DOOR, "south", "left");
        for (int y = STREET; y <= DECK + 1; y++) {
            s.set(ladderX, y, cz - 2, Kit.ladder("south"));
        }
        // Doors at the height of the walk: to the wall on the outer side, to the room over the gate on the inner side.
        int outer = cx - 3 * side;
        int inner = cx + 3 * side;
        String in = side > 0 ? "east" : "west";
        String out = side > 0 ? "west" : "east";
        s.door(outer, WALK, cz - 1, DOOR, in, "left");
        s.door(inner, WALK, cz - 1, DOOR, in, "left");
        s.door(inner, DECK + 1, cz, DOOR, in, "left");
        // Slits and windows.
        s.fill(cx, STREET + 1, cz + 3, cx, STREET + 2, cz + 3, Kit.BARS);
        s.fill(outer, STREET + 1, cz, outer, STREET + 2, cz, Kit.BARS);
        s.fill(cx, WALK + 1, cz + 3, cx, WALK + 2, cz + 3, Kit.BARS);
        s.fill(outer, WALK + 1, cz + 1, outer, WALK + 2, cz + 1, Kit.BARS);
        s.fill(cx, DECK + 2, cz - 3, cx, DECK + 3, cz - 3, Kit.GLASS);
        s.fill(outer, DECK + 2, cz, outer, DECK + 3, cz, Kit.GLASS);
        s.fill(cx - 2, DECK + 2, cz + 2, cx - 2, DECK + 3, cz + 2, Kit.GLASS);
        s.fill(cx + 2, DECK + 2, cz + 2, cx + 2, DECK + 3, cz + 2, Kit.GLASS);
        // A banner of the kingdom on the face to the moat.
        s.set(cx, WALK + 4, cz + 4, Kit.wallBanner("red", "south"));
        // The rooms: stores on the ground, the guard's table at the walk, a lookout above; all lit.
        s.set(cx + side, STREET, cz + 2, BARREL).set(cx, STREET, cz + 2, BARREL).set(cx + side, STREET + 1, cz + 2, BARREL);
        s.set(cx + 2 * side, STREET, cz, Kit.chest(out));
        s.set(cx, STREET + 4, cz, Kit.hangingLantern());
        s.set(cx + side, WALK, cz + 2, BARREL).set(cx - side, WALK, cz + 2, Kit.chest("north"));
        s.set(cx, WALK + 2, cz + 1, Kit.hangingLantern());
        s.set(cx + side, DECK + 1, cz - 1, Kit.lantern());
        s.set(cx - side, DECK + 1, cz + 2, BARREL);
    }

    /**
     * The gatehouse between the towers: a block of stone on a rough plinth with the passage through it, a stair inside each
     * flank from the ward up to the room over the gate, and a roof with battlements that juts out over the outer arch on
     * brackets.
     */
    private static void gatehouse(Structure s) {
        s.fill(GATE_X1, 0, GATE_Z1, GATE_X2, STREET - 1, GATE_Z2, Kit.ROUGH);
        s.fill(GATE_X1, STREET, GATE_Z1, GATE_X2, DECK, GATE_Z2, Kit.WALL);
        s.walls(GATE_X1, STREET, GATE_Z1, GATE_X2, STREET + 1, GATE_Z2, Kit.ROUGH);
        s.walls(GATE_X1, WALK_FLOOR, GATE_Z1, GATE_X2, WALK_FLOOR, GATE_Z2, Kit.TRIM);
        // The passage, paved, under a vault.
        s.fill(PASS_X1, STREET - 1, 0, PASS_X2, STREET - 1, MOAT_BACK, Kit.WALL);
        s.fill(PASS_X1, STREET, GATE_Z1, PASS_X2, PASS_TOP, GATE_Z2, AIR);
        for (int z = GATE_Z1 + 1; z < GATE_Z2; z++) {
            s.set(PASS_X1, PASS_TOP + 1, z, Kit.upside(Kit.WALL_STAIRS, "west"));
            s.set(PASS_X2, PASS_TOP + 1, z, Kit.upside(Kit.WALL_STAIRS, "east"));
            s.set(12, PASS_TOP + 1, z, Kit.topSlab(Kit.WALL_SLAB));
        }
        // The outer arch: five wide and a step deeper than the inner one, with a keystone.
        s.fill(PASS_X1 - 1, STREET, GATE_Z2, PASS_X2 + 1, PASS_TOP + 1, GATE_Z2, AIR);
        s.set(PASS_X1 - 1, WALK_FLOOR, GATE_Z2, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(PASS_X2 + 1, WALK_FLOOR, GATE_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(PASS_X1, WALK_FLOOR, GATE_Z2, Kit.TRIM).set(PASS_X2, WALK_FLOOR, GATE_Z2, Kit.TRIM);
        s.set(12, WALK_FLOOR, GATE_Z2, Kit.CARVED);
        // Rough piers frame the arch on both sides.
        s.fill(GATE_X1 + 1, STREET, GATE_Z2, GATE_X1 + 1, WALK_FLOOR, GATE_Z2, Kit.ROUGH);
        s.fill(GATE_X2 - 1, STREET, GATE_Z2, GATE_X2 - 1, WALK_FLOOR, GATE_Z2, Kit.ROUGH);
        // The inner arch on the side of the ward.
        s.set(PASS_X1, PASS_TOP + 1, GATE_Z1, Kit.upside(Kit.TRIM_STAIRS, "west"));
        s.set(PASS_X2, PASS_TOP + 1, GATE_Z1, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(12, PASS_TOP + 1, GATE_Z1, Kit.CARVED);
        // The portcullis, half raised, its bars rising through the floor into the guard room.
        s.fill(PASS_X1, PASS_TOP, GATE_Z2 - 1, PASS_X2, WALK + 1, GATE_Z2 - 1, Kit.BARS);
        // Murder holes in the vault; hatches close them in the floor of the guard room.
        for (int z : new int[]{7, 10}) {
            s.set(12, PASS_TOP + 1, z, AIR);
        }
        // Lanterns in niches of the passage.
        for (int z : new int[]{6, 9}) {
            s.set(PASS_X1 - 1, STREET + 1, z, Kit.lantern()).set(PASS_X2 + 1, STREET + 1, z, Kit.lantern());
        }
        // The guard room over the gate.
        s.fill(GATE_X1 + 1, WALK_FLOOR, GATE_Z1 + 1, GATE_X2 - 1, WALK_FLOOR, GATE_Z2 - 1, SPRUCE_PLANKS);
        s.fill(GATE_X1 + 1, WALK, GATE_Z1 + 1, GATE_X2 - 1, DECK - 1, GATE_Z2 - 1, AIR);
        s.fill(PASS_X1, WALK_FLOOR, GATE_Z2 - 1, PASS_X2, WALK + 1, GATE_Z2 - 1, Kit.BARS);
        for (int z : new int[]{7, 10}) {
            s.set(12, WALK_FLOOR, z, Kit.trapdoor(HATCH, "north", true));
        }
        // The stairs in both flanks, from doors on the side of the ward up to the guard room.
        for (int x : new int[]{GATE_X1 + 1, GATE_X2 - 1}) {
            s.door(x, STREET, GATE_Z1, DOOR, "south", "left");
            for (int k = 0; k <= WALK_FLOOR - STREET; k++) {
                s.set(x, STREET + k, GATE_Z1 + 1 + k, Kit.stairs(Kit.WALL_STAIRS, "south"));
                s.fill(x, STREET + k + 1, GATE_Z1 + 1 + k, x, STREET + k + 2, GATE_Z1 + 1 + k, AIR);
            }
            String toWall = x < 12 ? "east" : "west";
            s.set(x, STREET + 2, GATE_Z1 + 1, Kit.wallTorch(toWall));
            int rail = x < 12 ? x + 1 : x - 1;
            s.set(rail, WALK, 8, Kit.fence("spruce")).set(rail, WALK, 9, Kit.fence("spruce")).set(x, WALK, 7, Kit.fence("spruce"));
        }
        // The windlass of the portcullis: a drum on two posts before the bars.
        s.fill(PASS_X1 - 1, WALK, GATE_Z2 - 2, PASS_X1 - 1, WALK + 1, GATE_Z2 - 2, Kit.fence("spruce"));
        s.fill(PASS_X2 + 1, WALK, GATE_Z2 - 2, PASS_X2 + 1, WALK + 1, GATE_Z2 - 2, Kit.fence("spruce"));
        s.fill(PASS_X1, WALK + 1, GATE_Z2 - 2, PASS_X2, WALK + 1, GATE_Z2 - 2, Kit.log(SPRUCE_LOG, "x"));
        // Stores and a table in the guard room, lanterns under the roof.
        s.set(10, WALK, 5, BARREL).set(11, WALK, 5, Kit.chest("south")).set(13, WALK, 5, Kit.chest("south")).set(14, WALK, 5, BARREL);
        s.set(12, WALK, 5, BARREL).set(12, WALK + 1, 5, Kit.lantern());
        table(s, 12, WALK, 6);
        s.set(11, WALK, 6, Kit.stairs(SPRUCE_STAIRS, "west")).set(13, WALK, 6, Kit.stairs(SPRUCE_STAIRS, "east"));
        s.set(12, DECK - 1, 9, Kit.hangingLantern()).set(10, DECK - 1, 6, Kit.hangingLantern()).set(14, DECK - 1, 6, Kit.hangingLantern());
        // Windows to the field and to the ward; a banner over the keystone of the outer arch.
        for (int x : new int[]{10, 14}) {
            s.fill(x, WALK, GATE_Z2, x, WALK + 1, GATE_Z2, Kit.GLASS);
            s.fill(x, WALK, GATE_Z1, x, WALK + 1, GATE_Z1, Kit.GLASS);
        }
        s.set(12, WALK + 1, GATE_Z2 + 1, Kit.wallBanner("red", "south"));
        s.set(12, WALK + 1, GATE_Z1 - 1, Kit.wallBanner("red", "north"));
        // The roof: it juts out over the outer arch on brackets, battlements all round, lanterns in the crenels.
        s.fill(GATE_X1 + 1, DECK, GATE_Z2 + 1, GATE_X2 - 1, DECK, GATE_Z2 + 1, Kit.WALL);
        for (int x = GATE_X1 + 1; x <= GATE_X2 - 1; x++) {
            s.set(x, DECK - 1, GATE_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        Kit.battlements(s, GATE_X1 + 1, GATE_Z2 + 1, GATE_X2 - 1, GATE_Z2 + 1, DECK + 1, Kit.WALL);
        Kit.battlements(s, GATE_X1, GATE_Z1, GATE_X2, GATE_Z1, DECK + 1, Kit.WALL);
        Kit.battlements(s, GATE_X1, GATE_Z1, GATE_X1, GATE_Z2, DECK + 1, Kit.WALL);
        Kit.battlements(s, GATE_X2, GATE_Z1, GATE_X2, GATE_Z2, DECK + 1, Kit.WALL);
        s.set(11, DECK + 2, GATE_Z2 + 1, Kit.lantern()).set(13, DECK + 2, GATE_Z2 + 1, Kit.lantern());
        s.set(11, DECK + 2, GATE_Z1, Kit.lantern()).set(13, DECK + 2, GATE_Z1, Kit.lantern());
        s.set(12, DECK + 1, 8, Kit.lantern());
        // Lanterns on brackets beside the outer arch.
        for (int x : new int[]{GATE_X1 + 1, GATE_X2 - 1}) {
            s.set(x, PASS_TOP + 1, GATE_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
            s.set(x, PASS_TOP, GATE_Z2 + 1, Kit.hangingLantern());
        }
        // Small turrets on brackets at the back corners, looking over the ward, each under a little cone.
        for (int x : new int[]{GATE_X1, GATE_X2}) {
            int z = GATE_Z1;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx < GATE_X1 || x + dx > GATE_X2 || z + dz < GATE_Z1) {
                        s.set(x + dx, DECK - 1, z + dz, roundStair(dx, dz, 3, Kit.TRIM_STAIRS, true));
                    }
                }
            }
            s.fill(x - 1, DECK, z - 1, x + 1, DECK + 2, z + 1, Kit.WALL);
            s.fill(x - 1, DECK, z - 1, x + 1, DECK, z + 1, Kit.TRIM);
            s.set(x, DECK + 1, z - 1, Kit.BARS).set(x + (x < 12 ? -1 : 1), DECK + 1, z, Kit.BARS);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    s.set(x + dx, DECK + 3, z + dz, roundStair(dx, dz, 3, Kit.SLATE.stairs(), false));
                }
            }
            s.fill(x, DECK + 3, z, x, DECK + 4, z, Kit.SLATE.block());
            s.set(x, DECK + 5, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        }
    }

    /**
     * The bridge over the moat: a deck of stone on three piers with the water passing between them, parapets with posts and
     * lanterns at both ends, and the paved apron before the gate.
     */
    private static void bridge(Structure s) {
        int x1 = PASS_X1 - 1;
        int x2 = PASS_X2 + 1;
        s.fill(GATE_X1 + 1, STREET - 1, MOAT_BACK, GATE_X2 - 1, STREET - 1, MOAT_BACK, Kit.WALL);
        s.fill(x1, STREET - 1, MOAT_BACK + 1, x2, STREET - 1, MOAT_FRONT, Kit.WALL);
        for (int z : new int[]{MOAT_BACK + 1, MOAT_BACK + 3, MOAT_BACK + 5}) {
            s.fill(x1, 1, z, x2, 1, z, Kit.ROUGH);
        }
        for (int x : new int[]{x1, x2}) {
            s.fill(x, STREET, MOAT_BACK + 2, x, STREET, MOAT_FRONT - 1, Kit.WALL_FENCE);
            for (int z : new int[]{MOAT_BACK + 1, MOAT_FRONT}) {
                s.set(x, STREET, z, Kit.WALL).set(x, STREET + 1, z, Kit.lantern());
            }
        }
    }

    /**
     * The side of the ward: the road from the inner arch, gravel before the doors of the towers, lamp posts, and stores of
     * the guard stacked against the towers.
     */
    private static void wardSide(Structure s) {
        s.fill(PASS_X1 - 1, STREET - 1, 0, PASS_X1 - 1, STREET - 1, GATE_Z1 - 1, Kit.ROUGH);
        s.fill(PASS_X2 + 1, STREET - 1, 0, PASS_X2 + 1, STREET - 1, GATE_Z1 - 1, Kit.ROUGH);
        for (int cx : new int[]{TOWER_WEST, TOWER_EAST}) {
            s.fill(cx - 1, STREET - 1, 2, cx + 1, STREET - 1, TOWER_Z - 4, GRAVEL);
            s.set(cx - 1, STREET + 1, TOWER_Z - 4, Kit.wallTorch("north")).set(cx + 1, STREET + 1, TOWER_Z - 4, Kit.wallTorch("north"));
        }
        s.fill(TOWER_WEST - 1, STREET - 1, 2, PASS_X1 - 2, STREET - 1, 3, GRAVEL);
        s.fill(PASS_X2 + 2, STREET - 1, 2, TOWER_EAST + 1, STREET - 1, 3, GRAVEL);
        lampPost(s, PASS_X1 - 2, STREET, 1, 2);
        lampPost(s, PASS_X2 + 2, STREET, 1, 2);
        for (int x : new int[]{1, 23}) {
            int in = x < 12 ? 1 : -1;
            s.fill(x, STREET - 1, 6, x + in, STREET - 1, 7, COARSE_DIRT);
            s.set(x, STREET, 6, BARREL).set(x, STREET, 7, BARREL).set(x, STREET + 1, 7, BARREL).set(x + in, STREET, 7, HAY);
        }
    }

    // ---------------------------------------------------------------- the royal stables

    /** The walls of the stable and the layer of the lofts on top of them. */
    private static final int STABLE_X1 = 1;
    private static final int STABLE_X2 = 13;
    private static final int STABLE_Z1 = 1;
    private static final int STABLE_Z2 = 13;
    private static final int LOFT = 5;
    private static final String CAULDRON = "minecraft:water_cauldron[level=3]";
    private static final String DIRT_PATH = "minecraft:dirt_path";

    /**
     * The royal stables: a long stable of stone under a tall barn roof of tiles, its timbered gable with the loft door and
     * a hoist beam turned to a fenced yard. A wide arched door opens on an aisle that runs up to the roof; three stalls on
     * either side with plank partitions, gates, troughs and hay; a tack room and a feed room by the door; hay lofts over the
     * stalls joined by a bridge behind the loft door. On the ridge sits a lantern cupola with a weathervane. In the yard
     * stand a cart loaded with hay, a stone trough, a mounting block and a lamp post.
     */
    static Structure royalStables() {
        Structure s = new Structure(15, 21, 21);
        stableShell(s);
        stableStalls(s);
        stableLoft(s);
        stableYard(s);
        s.connect();
        return s;
    }

    /**
     * The shell: floors, stone walls on a rough plinth with quoins and buttresses, a trimmed band at the top of the walls,
     * the barn roof with timbered gables of plaster, the great door, windows, banners, the loft door with its hoist beam and
     * the cupola on the ridge.
     */
    private static void stableShell(Structure s) {
        s.fill(STABLE_X1, 0, STABLE_Z1, STABLE_X2, 0, STABLE_Z2, Kit.ROUGH);
        s.fill(STABLE_X1 + 1, 0, STABLE_Z1 + 1, STABLE_X2 - 1, 0, STABLE_Z2 - 1, COARSE_DIRT);
        s.fill(6, 0, STABLE_Z1 + 1, 8, 0, STABLE_Z2, Kit.ROUGH);
        s.fill(2, 0, 11, 5, 0, 12, SPRUCE_PLANKS).fill(9, 0, 11, 12, 0, 12, SPRUCE_PLANKS);
        s.walls(STABLE_X1, 1, STABLE_Z1, STABLE_X2, LOFT, STABLE_Z2, Kit.WALL);
        s.walls(STABLE_X1, 1, STABLE_Z1, STABLE_X2, 1, STABLE_Z2, Kit.ROUGH);
        s.walls(STABLE_X1, LOFT, STABLE_Z1, STABLE_X2, LOFT, STABLE_Z2, Kit.TRIM);
        for (int x : new int[]{STABLE_X1, STABLE_X2}) {
            for (int z : new int[]{STABLE_Z1, STABLE_Z2}) {
                s.fill(x, 1, z, x, LOFT - 1, z, Kit.ROUGH);
            }
            // Buttresses on the long walls where the partitions of the stalls meet them.
            int out = x == STABLE_X1 ? x - 1 : x + 1;
            for (int z : new int[]{4, 7, 10}) {
                s.fill(out, 0, z, out, LOFT - 2, z, Kit.ROUGH);
                s.set(out, LOFT - 1, z, Kit.stairs(Kit.ROUGH_STAIRS, x == STABLE_X1 ? "east" : "west"));
            }
        }
        int[] under = barnRoof(s, 0, 14, 0, 14, LOFT, 3, Kit.TILE, Kit.PLASTER, STABLE_Z1, STABLE_Z2);
        // The timbers of both gables: a sill beam over the stone and posts up to the roof.
        for (int z : new int[]{STABLE_Z1, STABLE_Z2}) {
            s.fill(STABLE_X1 + 1, LOFT + 1, z, STABLE_X2 - 1, LOFT + 1, z, Kit.log(OAK_LOG, "x"));
            for (int x : new int[]{4, 10}) {
                s.fill(x, LOFT + 2, z, x, under[x] - 1, z, Kit.log(OAK_LOG, "y"));
            }
            s.fill(7, LOFT + 3, z, 7, under[7] - 1, z, Kit.log(OAK_LOG, "y"));
        }
        s.fill(7, LOFT + 1, STABLE_Z1, 7, LOFT + 2, STABLE_Z1, Kit.GLASS);
        // The great door under an arch with a keystone, lanterns on brackets beside it and banners on the gable.
        s.fill(6, 1, STABLE_Z2, 8, LOFT - 2, STABLE_Z2, AIR);
        s.set(6, LOFT - 1, STABLE_Z2, Kit.upside(Kit.TRIM_STAIRS, "west")).set(8, LOFT - 1, STABLE_Z2, Kit.upside(Kit.TRIM_STAIRS, "east"));
        s.set(7, LOFT - 1, STABLE_Z2, Kit.CARVED);
        for (int x : new int[]{5, 9}) {
            s.set(x, LOFT - 1, STABLE_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
            s.set(x, LOFT - 2, STABLE_Z2 + 1, Kit.hangingLantern());
            s.set(x, LOFT + 4, STABLE_Z2 + 1, Kit.wallBanner("red", "south"));
        }
        // Windows of the tack room and the feed room with sills and shutters; high barred windows of the stalls.
        for (int x : new int[]{3, 11}) {
            s.fill(x, 2, STABLE_Z2, x, 3, STABLE_Z2, Kit.GLASS);
            s.set(x, 1, STABLE_Z2 + 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
            s.fill(x - 1, 2, STABLE_Z2 + 1, x - 1, 3, STABLE_Z2 + 1, Kit.shutter(Kit.SHUTTER, "south"));
            s.fill(x + 1, 2, STABLE_Z2 + 1, x + 1, 3, STABLE_Z2 + 1, Kit.shutter(Kit.SHUTTER, "south"));
        }
        for (int x : new int[]{STABLE_X1, STABLE_X2}) {
            for (int z : new int[]{2, 5, 8}) {
                s.fill(x, 3, z, x, 3, z + 1, Kit.BARS);
            }
            s.fill(x, 2, 12, x, 3, 12, Kit.GLASS);
        }
        // The loft door in the front gable with its leaves open, and the hoist beam over it with a bale on its chain.
        s.fill(7, LOFT + 1, STABLE_Z2, 7, LOFT + 2, STABLE_Z2, AIR);
        s.fill(6, LOFT + 1, STABLE_Z2 + 1, 6, LOFT + 2, STABLE_Z2 + 1, Kit.shutter(HATCH, "south"));
        s.fill(8, LOFT + 1, STABLE_Z2 + 1, 8, LOFT + 2, STABLE_Z2 + 1, Kit.shutter(HATCH, "south"));
        s.fill(7, LOFT + 4, STABLE_Z2, 7, LOFT + 4, STABLE_Z2 + 2, Kit.log(SPRUCE_LOG, "z"));
        s.set(7, LOFT + 3, STABLE_Z2 + 2, Kit.chain()).set(7, LOFT + 2, STABLE_Z2 + 2, HAY);
        // The cupola on the ridge: corner posts with rails between them, a lantern inside, a pointed slate cap and a
        // weathervane.
        int ridge = under[7];
        s.fill(6, ridge, 6, 8, ridge, 8, OAK_PLANKS);
        for (int x : new int[]{6, 8}) {
            for (int z : new int[]{6, 8}) {
                s.fill(x, ridge + 1, z, x, ridge + 2, z, Kit.log(STRIPPED_OAK, "y"));
            }
            s.set(x, ridge + 1, 7, Kit.fence("oak"));
            s.set(7, ridge + 1, x, Kit.fence("oak"));
        }
        s.set(7, ridge + 1, 7, Kit.lantern());
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                s.set(7 + dx, ridge + 3, 7 + dz, roundStair(dx, dz, 3, Kit.SLATE.stairs(), false));
            }
        }
        s.fill(7, ridge + 3, 7, 7, ridge + 4, 7, Kit.SLATE.block());
        s.set(7, ridge + 5, 7, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /**
     * Inside on the ground: three stalls on either side of the aisle, each with a plank partition topped by a rail, a double
     * gate to the aisle, a trough and hay; posts and a beam carry the lofts. By the door a tack room with chests, barrels
     * and an anvil, and a feed room with hay and a composter. Lanterns hang in every stall and on chains in the aisle.
     */
    private static void stableStalls(Structure s) {
        for (int side : new int[]{1, -1}) {
            int back = side > 0 ? 2 : 12;
            int front = side > 0 ? 5 : 9;
            int mid = side > 0 ? 3 : 11;
            String toAisle = side > 0 ? "east" : "west";
            String fromAisle = side > 0 ? "west" : "east";
            for (int z : new int[]{4, 7}) {
                s.fill(back, 1, z, front, 1, z, SPRUCE_PLANKS).fill(back, 2, z, front, 2, z, Kit.fence("spruce"));
            }
            s.fill(back, 1, 10, front, LOFT - 1, 10, SPRUCE_PLANKS);
            s.fill(front, 1, 11, front, LOFT - 1, 12, SPRUCE_PLANKS);
            for (int z : new int[]{4, 7, 10}) {
                s.fill(front, 1, z, front, LOFT - 1, z, Kit.log(SPRUCE_LOG, "y"));
            }
            s.fill(front, LOFT - 1, STABLE_Z1 + 1, front, LOFT - 1, 9, Kit.log(SPRUCE_LOG, "z"));
            for (int z : new int[]{2, 5, 8}) {
                for (int g = 0; g < 2; g++) {
                    s.set(front, 1, z + g, "minecraft:spruce_fence_gate[facing=" + toAisle + ",in_wall=false,open=false,powered=false]");
                }
                s.set(back, 1, z, CAULDRON).set(back, 1, z + 1, HAY);
                s.set(mid, 1, z + 1, "minecraft:yellow_carpet");
                s.set(mid, LOFT - 1, z + 1, Kit.hangingLantern());
            }
            // The door of the room by the entrance, and the ladder up to the loft beside it.
            s.door(front, 1, 11, DOOR, fromAisle, "left");
            for (int y = 1; y <= LOFT; y++) {
                s.set(front + side, y, 10, Kit.ladder(toAisle));
            }
            s.set(mid, LOFT - 1, 11, Kit.hangingLantern());
        }
        // The tack room.
        s.set(2, 1, 11, Kit.chest("east")).set(2, 1, 12, BARREL).set(2, 2, 12, BARREL).set(3, 1, 12, Kit.chest("north"));
        s.set(4, 1, 12, "minecraft:anvil[facing=east]");
        // The feed room.
        s.set(12, 1, 11, HAY).set(12, 2, 11, HAY).set(12, 1, 12, HAY).set(11, 1, 12, BARREL);
        s.set(10, 1, 12, "minecraft:composter[level=3]");
        // Lanterns on chains down the aisle.
        for (int z : new int[]{3, 7}) {
            s.fill(7, LOFT, z, 7, 14, z, Kit.chain());
            s.set(7, LOFT - 1, z, Kit.hangingLantern());
        }
    }

    /**
     * The hay lofts over the stalls, with rails along the aisle, stacked bales and lanterns, joined over the door by a bridge
     * that leads to the loft door.
     */
    private static void stableLoft(Structure s) {
        s.fill(2, LOFT, 2, 5, LOFT, 12, SPRUCE_PLANKS).fill(9, LOFT, 2, 12, LOFT, 12, SPRUCE_PLANKS);
        s.fill(6, LOFT, 11, 8, LOFT, 12, SPRUCE_PLANKS);
        s.set(6, LOFT, 10, Kit.ladder("east")).set(8, LOFT, 10, Kit.ladder("west"));
        s.fill(5, LOFT + 1, 2, 5, LOFT + 1, 9, Kit.fence("spruce")).fill(9, LOFT + 1, 2, 9, LOFT + 1, 9, Kit.fence("spruce"));
        s.fill(6, LOFT + 1, 11, 8, LOFT + 1, 11, Kit.fence("spruce"));
        for (int x : new int[]{2, 3, 11, 12}) {
            s.fill(x, LOFT + 1, 2, x, LOFT + 1, 9, HAY);
        }
        s.fill(3, LOFT + 2, 3, 3, LOFT + 2, 8, HAY).fill(11, LOFT + 2, 3, 11, LOFT + 2, 8, HAY);
        s.set(4, LOFT + 1, 3, Kit.lantern()).set(4, LOFT + 1, 8, Kit.lantern());
        s.set(10, LOFT + 1, 3, Kit.lantern()).set(10, LOFT + 1, 8, Kit.lantern());
        s.set(7, LOFT + 1, 12, Kit.lantern());
    }

    /**
     * The yard before the stable: trodden earth with a path to the door inside a kerb of rough stone, a rail of fences on the
     * sides and the front, stone piers with lanterns at the entrance, a cart loaded with hay, a stone trough with a mounting
     * block, hay and a lamp post.
     */
    private static void stableYard(Structure s) {
        s.fill(0, 0, STABLE_Z2 + 1, 14, 0, 20, Kit.ROUGH);
        s.fill(1, 0, STABLE_Z2 + 1, 13, 0, 19, COARSE_DIRT);
        s.fill(6, 0, STABLE_Z2 + 1, 8, 0, 19, DIRT_PATH);
        s.fill(6, 0, 20, 8, 0, 20, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(0, 1, STABLE_Z2 + 1, 0, 1, 19, Kit.fence("oak")).fill(14, 1, STABLE_Z2 + 1, 14, 1, 19, Kit.fence("oak"));
        s.fill(0, 1, 20, 4, 1, 20, Kit.fence("oak")).fill(10, 1, 20, 14, 1, 20, Kit.fence("oak"));
        s.set(0, 2, 20, Kit.lantern()).set(14, 2, 20, Kit.lantern());
        for (int x : new int[]{5, 9}) {
            s.fill(x, 1, 20, x, 2, 20, Kit.WALL).set(x, 3, 20, Kit.TRIM).set(x, 4, 20, Kit.lantern());
        }
        // The cart: a bed on two wheels, loaded with hay and a barrel, its shafts to the front.
        s.fill(10, 1, 16, 11, 1, 17, Kit.topSlab(SPRUCE_SLAB));
        s.set(9, 1, 17, Kit.shutter(HATCH, "west")).set(12, 1, 17, Kit.shutter(HATCH, "east"));
        s.set(10, 2, 16, "minecraft:hay_block[axis=z]").set(11, 2, 16, "minecraft:hay_block[axis=z]").set(10, 2, 17, BARREL);
        s.set(10, 1, 18, Kit.fence("spruce")).set(11, 1, 18, Kit.fence("spruce"));
        // The trough, a mounting block and hay by the wall.
        s.fill(1, 0, 15, 3, 1, 19, Kit.ROUGH);
        s.fill(2, 1, 16, 2, 1, 18, WATER);
        s.set(4, 1, 16, Kit.stairs(Kit.ROUGH_STAIRS, "west")).set(4, 1, 17, Kit.ROUGH).set(4, 2, 17, Kit.slab(Kit.ROUGH_SLAB));
        s.set(1, 1, 14, HAY).set(2, 1, 14, HAY).set(1, 2, 14, HAY);
        s.set(13, 1, 14, BARREL).set(13, 2, 14, BARREL).set(12, 1, 14, BARREL);
        lampPost(s, 12, 1, 19, 2);
    }

    // ---------------------------------------------------------------- the barracks of the royal guard

    /** The main block of the barracks: its walls, the floor of the upper storey and the band under the eaves. */
    private static final int GUARD_X1 = 1;
    private static final int GUARD_X2 = 15;
    private static final int GUARD_Z1 = 2;
    private static final int GUARD_Z2 = 12;
    private static final int UPPER = 5;
    private static final int EAVES = 9;
    /** The watch tower in the middle of the front, over the entrance; the platform lies on its walls. */
    private static final int TW_X1 = 6;
    private static final int TW_X2 = 10;
    private static final int TW_Z1 = 10;
    private static final int TW_Z2 = 14;
    private static final int TW_TOP = 14;
    private static final String BED = "minecraft:blue_bed";

    /**
     * The barracks of the royal guard: a two-storeyed block of stone under a roof of shingles, small turrets with slate caps
     * on its front corners and a tall chimney at its back, and a square watch tower in the middle of its front that rises
     * over the ridge to a platform with battlements on brackets, a bell, and a slate spire on four posts with a flag. The
     * door in the foot of the tower opens on a hall with the mess table, the armoury along one end and the kitchen at the
     * other, the barracks desk before the entrance and stairs up to the dormitory with sixteen bunks, footlockers and chests
     * under the open rafters. Before it lies the training yard: butts for the bows on one side, dummies for the sword on the
     * other.
     */
    static Structure royalGuardBarracks() {
        Structure s = new Structure(17, 29, 21);
        guardShell(s);
        guardTower(s);
        guardHall(s);
        guardDormitory(s);
        trainingYard(s);
        s.connect();
        s.trimSky(3);
        return s;
    }

    /**
     * A gable roof whose ridge runs along x: rows of stairs rising from both eaves, a slab on the ridge, and the gable walls
     * at both ends filled under it.
     */
    private static void gableX(Structure s, int x1, int x2, int z1, int z2, int y, Kit.Roof roof, String gable, int wallX1, int wallX2) {
        for (int row = 0; z1 + row <= z2 - row; row++) {
            int back = z1 + row;
            int front = z2 - row;
            int height = y + row;
            if (back < front) {
                s.fill(x1, height, back, x2, height, back, Kit.stairs(roof.stairs(), "south"));
                s.fill(x1, height, front, x2, height, front, Kit.stairs(roof.stairs(), "north"));
                if (row > 0) {
                    s.fill(wallX1, height, back + 1, wallX1, height, front - 1, gable);
                    s.fill(wallX2, height, back + 1, wallX2, height, front - 1, gable);
                }
            } else {
                s.fill(x1, height, back, x2, height, back, Kit.slab(roof.slab()));
                s.set(wallX1, height - 1, back, gable).set(wallX2, height - 1, back, gable);
            }
        }
    }

    /**
     * The shell of the main block: a rough plinth and quoins, stone walls with trimmed bands at the upper floor and under the
     * eaves, windows with sills on both storeys, the gable roof of shingles with tie beams under it, windows in the gables,
     * the turrets on brackets at the front corners and the chimney of the kitchen.
     */
    private static void guardShell(Structure s) {
        s.fill(GUARD_X1, 0, GUARD_Z1, GUARD_X2, 0, GUARD_Z2, Kit.ROUGH);
        s.fill(GUARD_X1 + 1, 0, GUARD_Z1 + 1, GUARD_X2 - 1, 0, GUARD_Z2 - 1, SPRUCE_PLANKS);
        s.walls(GUARD_X1, 1, GUARD_Z1, GUARD_X2, EAVES, GUARD_Z2, Kit.WALL);
        s.walls(GUARD_X1, 1, GUARD_Z1, GUARD_X2, 1, GUARD_Z2, Kit.ROUGH);
        for (int x : new int[]{GUARD_X1, GUARD_X2}) {
            for (int z : new int[]{GUARD_Z1, GUARD_Z2}) {
                s.fill(x, 1, z, x, EAVES, z, Kit.ROUGH);
            }
        }
        s.walls(GUARD_X1, UPPER, GUARD_Z1, GUARD_X2, UPPER, GUARD_Z2, Kit.TRIM);
        s.walls(GUARD_X1, EAVES, GUARD_Z1, GUARD_X2, EAVES, GUARD_Z2, Kit.TRIM);
        s.fill(GUARD_X1 + 1, UPPER, GUARD_Z1 + 1, GUARD_X2 - 1, UPPER, GUARD_Z2 - 1, SPRUCE_PLANKS);
        gableX(s, GUARD_X1 - 1, GUARD_X2 + 1, GUARD_Z1 - 1, GUARD_Z2 + 1, EAVES, Kit.SHINGLE, Kit.WALL, GUARD_X1, GUARD_X2);
        for (int x : new int[]{4, 12}) {
            s.fill(x, EAVES, GUARD_Z1 + 1, x, EAVES, GUARD_Z2 - 1, Kit.log(SPRUCE_LOG, "z"));
        }
        // Windows of both storeys on the front and the back (where the chimney leaves room), with sills; windows in the
        // gables.
        for (int x : new int[]{3, 5, 9, 11, 13}) {
            for (int z : new int[]{GUARD_Z1, GUARD_Z2}) {
                if (z == GUARD_Z1 ? x == 13 : x == 9) {
                    continue;
                }
                int out = z == GUARD_Z1 ? z - 1 : z + 1;
                String face = z == GUARD_Z1 ? "south" : "north";
                s.fill(x, 2, z, x, 3, z, Kit.GLASS).fill(x, UPPER + 2, z, x, UPPER + 3, z, Kit.GLASS);
                s.set(x, 1, out, Kit.stairs(Kit.TRIM_STAIRS, face));
                s.set(x, UPPER + 1, out, Kit.slab(Kit.TRIM_SLAB));
            }
        }
        for (int x : new int[]{GUARD_X1, GUARD_X2}) {
            s.fill(x, 2, 7, x, 3, 7, Kit.GLASS).fill(x, UPPER + 2, 7, x, UPPER + 3, 7, Kit.GLASS);
            s.fill(x, EAVES + 2, 6, x, EAVES + 3, 6, Kit.GLASS).fill(x, EAVES + 2, 8, x, EAVES + 3, 8, Kit.GLASS);
            s.set(x, EAVES + 4, 7, Kit.CARVED);
        }
        // Small turrets on brackets at the front corners, rising through the eaves under pointed slate caps.
        for (int x : new int[]{GUARD_X1, GUARD_X2}) {
            int z = GUARD_Z2;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    boolean outside = x + dx < GUARD_X1 || x + dx > GUARD_X2 || z + dz > GUARD_Z2;
                    if (outside) {
                        s.set(x + dx, UPPER + 1, z + dz, roundStair(dx, dz, 3, Kit.TRIM_STAIRS, true));
                    } else if (x + dx != x && z + dz != z) {
                        // The corner inside the dormitory stays free.
                        s.set(x + dx, EAVES + 2, z + dz, roundStair(dx, dz, 3, Kit.SLATE.stairs(), false));
                        continue;
                    }
                    s.fill(x + dx, UPPER + 2, z + dz, x + dx, EAVES + 1, z + dz, Kit.WALL);
                    s.set(x + dx, UPPER + 2, z + dz, Kit.TRIM);
                    s.set(x + dx, EAVES + 2, z + dz, roundStair(dx, dz, 3, Kit.SLATE.stairs(), false));
                }
            }
            s.fill(x, UPPER + 2, z, x, EAVES + 1, z, Kit.ROUGH);
            s.set(x + (x == GUARD_X1 ? -1 : 1), EAVES, z, Kit.BARS).set(x, EAVES, z + 1, Kit.BARS);
            s.fill(x, EAVES + 2, z, x, EAVES + 3, z, Kit.SLATE.block());
            s.set(x, EAVES + 4, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        }
        // The chimney of the kitchen: a stack of its own against the back wall, stepping out round the eave.
        s.fill(12, 0, GUARD_Z1 - 1, 13, EAVES - 1, GUARD_Z1 - 1, Kit.CHIMNEY);
        s.fill(12, 0, GUARD_Z1 - 2, 13, EAVES + 7, GUARD_Z1 - 2, Kit.CHIMNEY);
        s.fill(12, EAVES - 1, GUARD_Z1 - 1, 13, EAVES - 1, GUARD_Z1 - 1, Kit.stairs(Kit.CHIMNEY_STAIRS, "north"));
        s.set(12, EAVES + 8, GUARD_Z1 - 2, Kit.CHIMNEY_POT).set(13, EAVES + 8, GUARD_Z1 - 2, Kit.CHIMNEY_POT);
    }

    /**
     * A tented roof over a square plan, steeper than the usual one: an eave of stairs, then rings that step in by one block
     * on every side, each {@code rise} layers tall, blocks under a ring of stairs; a column in the middle closes it.
     *
     * @return the layer over the top of the column
     */
    private static int tallSpire(Structure s, int x1, int z1, int x2, int z2, int y, int rise, Kit.Roof roof) {
        int layer = y;
        int ring = 0;
        while (x2 - ring - (x1 + ring) >= 2 && z2 - ring - (z1 + ring) >= 2) {
            int west = x1 + ring;
            int east = x2 - ring;
            int back = z1 + ring;
            int front = z2 - ring;
            if (ring > 0) {
                for (int k = 0; k < rise - 1; k++) {
                    s.walls(west, layer, back, east, layer, front, roof.block());
                    layer++;
                }
            }
            s.fill(west, layer, back, east, layer, back, Kit.stairs(roof.stairs(), "south"));
            s.fill(west, layer, front, east, layer, front, Kit.stairs(roof.stairs(), "north"));
            s.fill(west, layer, back + 1, west, layer, front - 1, Kit.stairs(roof.stairs(), "east"));
            s.fill(east, layer, back + 1, east, layer, front - 1, Kit.stairs(roof.stairs(), "west"));
            s.set(west, layer, back, Kit.stairs(roof.stairs(), "south", "outer_left"));
            s.set(east, layer, back, Kit.stairs(roof.stairs(), "south", "outer_right"));
            s.set(west, layer, front, Kit.stairs(roof.stairs(), "north", "outer_right"));
            s.set(east, layer, front, Kit.stairs(roof.stairs(), "north", "outer_left"));
            layer++;
            ring++;
        }
        int mx = (x1 + x2) / 2;
        int mz = (z1 + z2) / 2;
        s.fill(mx, layer - 1, mz, mx, layer + rise - 2, mz, roof.block());
        return layer + rise - 1;
    }

    /**
     * The watch tower over the entrance: the door under a hood with lanterns and a banner above, a room at the height of the
     * dormitory, a lookout room over the roof, a ladder up through both to the platform. The platform juts out on brackets
     * behind battlements; four posts carry the slate spire over it, and the bell hangs from a beam under the spire.
     */
    private static void guardTower(Structure s) {
        // Walls: on the ground only the part before the main front, above the roof all round.
        s.fill(TW_X1, 0, GUARD_Z2, TW_X2, 0, TW_Z2, Kit.ROUGH);
        s.fill(TW_X1 + 1, 0, GUARD_Z2, TW_X2 - 1, 0, TW_Z2 - 1, SPRUCE_PLANKS);
        s.fill(TW_X1, 1, GUARD_Z2, TW_X1, TW_TOP, TW_Z2, Kit.WALL).fill(TW_X2, 1, GUARD_Z2, TW_X2, TW_TOP, TW_Z2, Kit.WALL);
        s.fill(TW_X1, 1, TW_Z2, TW_X2, TW_TOP, TW_Z2, Kit.WALL);
        s.fill(TW_X1, UPPER + 1, TW_Z1, TW_X2, TW_TOP, TW_Z1, Kit.WALL);
        s.fill(TW_X1, UPPER + 1, TW_Z1, TW_X1, TW_TOP, TW_Z2, Kit.WALL).fill(TW_X2, UPPER + 1, TW_Z1, TW_X2, TW_TOP, TW_Z2, Kit.WALL);
        s.fill(TW_X1 + 1, 1, TW_Z1 + 1, TW_X2 - 1, TW_TOP, TW_Z2 - 1, AIR);
        s.fill(TW_X1 + 1, 1, GUARD_Z2, TW_X2 - 1, UPPER - 1, GUARD_Z2, AIR);
        for (int x : new int[]{TW_X1, TW_X2}) {
            s.fill(x, 1, TW_Z2, x, TW_TOP, TW_Z2, Kit.ROUGH);
            s.set(x, 1, GUARD_Z2 + 1, Kit.ROUGH);
        }
        s.fill(TW_X1 + 1, 1, TW_Z2, TW_X2 - 1, 1, TW_Z2, Kit.ROUGH);
        s.walls(TW_X1, UPPER, TW_Z1, TW_X2, UPPER, TW_Z2, Kit.TRIM).walls(TW_X1, EAVES, TW_Z1, TW_X2, EAVES, TW_Z2, Kit.TRIM);
        s.fill(TW_X1 + 1, UPPER, TW_Z1 + 1, TW_X2 - 1, UPPER, TW_Z2 - 1, SPRUCE_PLANKS);
        s.fill(TW_X1 + 1, EAVES + 2, TW_Z1 + 1, TW_X2 - 1, EAVES + 2, TW_Z2 - 1, SPRUCE_PLANKS);
        // The door under a hood of stone with a lantern on either side, and the banner of the guard above.
        s.door(8, 1, TW_Z2, DOOR, "north", "left");
        s.set(8, 3, TW_Z2, Kit.CARVED);
        for (int x = 7; x <= 9; x++) {
            s.set(x, 3, TW_Z2 + 1, Kit.upside(Kit.TRIM_STAIRS, "north"));
        }
        s.set(7, 2, TW_Z2 + 1, Kit.hangingLantern()).set(9, 2, TW_Z2 + 1, Kit.hangingLantern());
        s.set(8, EAVES + 1, TW_Z2 + 1, Kit.wallBanner("red", "south"));
        // Windows and slits.
        s.fill(8, UPPER + 2, TW_Z2, 8, UPPER + 3, TW_Z2, Kit.GLASS);
        s.fill(8, EAVES + 3, TW_Z2, 8, EAVES + 4, TW_Z2, Kit.BARS);
        s.fill(TW_X1, EAVES + 3, 12, TW_X1, EAVES + 4, 12, Kit.BARS).fill(TW_X2, EAVES + 3, 12, TW_X2, EAVES + 4, 12, Kit.BARS);
        // Inside: the door from the dormitory, the ladder, lanterns and stores of the watch.
        s.door(8, UPPER + 1, TW_Z1, DOOR, "south", "left");
        for (int y = UPPER + 1; y <= TW_TOP + 1; y++) {
            s.set(TW_X1 + 1, y, TW_Z1 + 1, Kit.ladder("east"));
        }
        s.set(TW_X2 - 1, UPPER + 1, TW_Z2 - 1, Kit.lantern()).set(TW_X2 - 1, UPPER + 1, TW_Z1 + 2, BARREL);
        s.set(TW_X2 - 1, EAVES + 3, TW_Z2 - 1, Kit.lantern()).set(TW_X2 - 1, EAVES + 3, TW_Z1 + 1, BARREL);
        s.set(TW_X1 + 1, EAVES + 3, TW_Z2 - 1, Kit.chest("east"));
        s.set(8, UPPER - 1, 13, Kit.hangingLantern());
        // The platform on brackets, its battlements, the posts and the spire.
        int deck = TW_TOP + 1;
        for (int x = TW_X1 - 1; x <= TW_X2 + 1; x++) {
            for (int z = TW_Z1 - 1; z <= TW_Z2 + 1; z++) {
                boolean ring = x < TW_X1 || x > TW_X2 || z < TW_Z1 || z > TW_Z2;
                if (ring) {
                    String facing = x < TW_X1 ? "east" : x > TW_X2 ? "west" : z < TW_Z1 ? "south" : "north";
                    s.set(x, TW_TOP, z, Kit.upside(Kit.TRIM_STAIRS, facing));
                }
                s.set(x, deck, z, Kit.WALL);
            }
        }
        s.set(TW_X1 + 1, deck, TW_Z1 + 1, Kit.ladder("east"));
        Kit.battlements(s, TW_X1 - 1, TW_Z1 - 1, TW_X2 + 1, TW_Z1 - 1, deck + 1, Kit.WALL);
        Kit.battlements(s, TW_X1 - 1, TW_Z2 + 1, TW_X2 + 1, TW_Z2 + 1, deck + 1, Kit.WALL);
        Kit.battlements(s, TW_X1 - 1, TW_Z1 - 1, TW_X1 - 1, TW_Z2 + 1, deck + 1, Kit.WALL);
        Kit.battlements(s, TW_X2 + 1, TW_Z1 - 1, TW_X2 + 1, TW_Z2 + 1, deck + 1, Kit.WALL);
        int roof = deck + 5;
        for (int x : new int[]{TW_X1, TW_X2}) {
            for (int z : new int[]{TW_Z1, TW_Z2}) {
                s.fill(x, deck + 1, z, x, roof, z, Kit.WALL_FENCE);
            }
        }
        s.fill(TW_X1 + 1, roof - 1, TW_Z1, TW_X2 - 1, roof - 1, TW_Z1, Kit.log(SPRUCE_LOG, "x"));
        s.fill(TW_X1 + 1, roof - 1, TW_Z2, TW_X2 - 1, roof - 1, TW_Z2, Kit.log(SPRUCE_LOG, "x"));
        s.fill(8, roof - 1, TW_Z1 + 1, 8, roof - 1, TW_Z2 - 1, Kit.log(SPRUCE_LOG, "z"));
        s.set(8, roof - 2, 12, "minecraft:bell[attachment=ceiling,facing=south,powered=false]");
        s.set(7, roof - 2, TW_Z2, Kit.hangingLantern()).set(9, roof - 2, TW_Z1, Kit.hangingLantern());
        int top = tallSpire(s, TW_X1 - 1, TW_Z1 - 1, TW_X2 + 1, TW_Z2 + 1, roof, 2, Kit.SLATE);
        s.set(8, top - 1, 12, Kit.SLATE.block()).set(8, top, 12, Kit.fence("spruce")).set(8, top + 1, 12, Kit.banner("red", 0));
    }

    /**
     * The hall on the ground: a red runner from the door to the barracks desk, the long mess table with benches, the armoury
     * along the west wall with grindstones, a smithing table, an anvil, barrels and chests, the kitchen in the east corner
     * and the stairs up along the east wall. Lanterns hang under the floor above.
     */
    private static void guardHall(Structure s) {
        s.fill(8, 1, 10, 8, 1, TW_Z2 - 1, Kit.CLOTH_CARPET);
        s.set(8, 1, 9, "kingdomworld:barracks[facing=south]");
        // The mess table and its benches.
        s.fill(4, 1, 5, 11, 1, 6, Kit.topSlab(SPRUCE_SLAB));
        for (int x = 4; x <= 11; x++) {
            s.set(x, 1, 4, Kit.stairs(SPRUCE_STAIRS, "north")).set(x, 1, 7, Kit.stairs(SPRUCE_STAIRS, "south"));
        }
        s.set(5, 2, 5, Kit.lantern()).set(10, 2, 6, Kit.lantern());
        // The armoury.
        s.set(2, 1, 3, "minecraft:smithing_table").set(2, 1, 4, GRINDSTONE.replace("facing=south", "facing=east"));
        s.set(2, 1, 5, BARREL).set(2, 2, 5, BARREL).set(2, 1, 6, Kit.chest("east")).set(2, 1, 7, Kit.chest("east"));
        s.set(2, 1, 8, GRINDSTONE.replace("facing=south", "facing=east")).set(2, 1, 9, BARREL).set(2, 1, 10, BARREL).set(2, 2, 10, BARREL);
        s.set(3, 1, 11, "minecraft:anvil[facing=east]").set(2, 1, 11, Kit.chest("east"));
        // The kitchen against the back wall by the chimney, the pantry in the front corner.
        s.set(12, 1, 3, "minecraft:furnace[facing=south,lit=false]").set(13, 1, 3, "minecraft:smoker[facing=south,lit=false]");
        s.set(14, 1, 3, BARREL).set(14, 2, 3, BARREL);
        s.set(14, 1, 11, BARREL).set(14, 1, 10, BARREL).set(14, 2, 11, BARREL).set(14, 1, 9, "minecraft:cauldron");
        s.set(13, 1, 11, Kit.chest("north")).set(12, 1, 11, BARREL);
        // The stairs up along the east wall, on a wall of stone.
        for (int k = 0; k < UPPER; k++) {
            int z = 8 - k;
            if (k > 0) {
                s.fill(GUARD_X2 - 1, 1, z, GUARD_X2 - 1, k, z, Kit.WALL);
            }
            s.set(GUARD_X2 - 1, 1 + k, z, Kit.stairs(Kit.WALL_STAIRS, "north"));
        }
        s.fill(GUARD_X2 - 1, UPPER, 5, GUARD_X2 - 1, UPPER, 6, AIR);
        for (int[] spot : new int[][]{{5, 8}, {11, 8}, {5, 3}, {11, 3}, {3, 10}}) {
            s.set(spot[0], UPPER - 1, spot[1], Kit.hangingLantern());
        }
    }

    /**
     * The dormitory under the rafters: eight bunks along the back wall with chests between pairs, eight along the front on
     * either side of the tower with footlockers at their feet, a rail around the stairs and lanterns hanging from the beams.
     */
    private static void guardDormitory(Structure s) {
        int y = UPPER + 1;
        for (int x : new int[]{2, 3, 5, 6, 8, 9, 11, 12}) {
            s.bed(x, y, GUARD_Z1 + 2, BED, "north", 0, -1);
        }
        for (int x : new int[]{4, 7, 10}) {
            s.set(x, y, GUARD_Z1 + 1, Kit.chest("south"));
        }
        for (int x : new int[]{2, 3, 4, 5, 11, 12, 13, 14}) {
            s.bed(x, y, GUARD_Z2 - 2, BED, "south", 0, 1);
            s.set(x, y, GUARD_Z2 - 3, Kit.chest("north"));
        }
        s.set(13, y, 6, Kit.fence("spruce")).set(14, y, 7, Kit.fence("spruce"));
        for (int x : new int[]{4, 12}) {
            s.set(x, EAVES - 1, 7, Kit.hangingLantern());
        }
        s.set(8, y, 7, BARREL).set(8, y + 1, 7, Kit.lantern());
        s.set(7, y, GUARD_Z2 - 3, BARREL).set(9, y, GUARD_Z2 - 3, BARREL);
    }

    /**
     * The training yard before the barracks: trodden earth inside a low wall with lit posts, a paved way to the door, two
     * butts with targets on the west side, two dummies with arms and a weapon rack on the east side, a bench by the wall.
     */
    private static void trainingYard(Structure s) {
        s.fill(0, 0, GUARD_Z2 + 1, 16, 0, 20, COARSE_DIRT);
        s.fill(TW_X1, 0, GUARD_Z2 + 1, TW_X2, 0, TW_Z2, Kit.ROUGH);
        s.fill(TW_X1 + 1, 0, GUARD_Z2, TW_X2 - 1, 0, TW_Z2 - 1, SPRUCE_PLANKS);
        s.fill(7, 0, TW_Z2 + 1, 9, 0, 19, Kit.ROUGH);
        s.fill(7, 0, 20, 9, 0, 20, Kit.stairs(Kit.ROUGH_STAIRS, "north"));
        s.fill(0, 1, GUARD_Z2 + 1, 0, 1, 20, Kit.ROUGH_FENCE).fill(16, 1, GUARD_Z2 + 1, 16, 1, 20, Kit.ROUGH_FENCE);
        s.fill(0, 1, 20, 6, 1, 20, Kit.ROUGH_FENCE).fill(10, 1, 20, 16, 1, 20, Kit.ROUGH_FENCE);
        for (int x : new int[]{0, 6, 10, 16}) {
            s.set(x, 1, 20, Kit.ROUGH).set(x, 2, 20, Kit.lantern());
        }
        // Butts for the bows: hay with targets on it.
        for (int z : new int[]{15, 18}) {
            s.set(1, 1, z, HAY).set(1, 2, z, "minecraft:target[power=0]");
        }
        s.set(1, 1, 16, HAY).set(1, 1, 17, BARREL);
        // Dummies for the sword: a post, a body of hay with arms, a carved head.
        for (int[] d : new int[][]{{12, 15}, {14, 18}}) {
            s.set(d[0], 1, d[1], Kit.fence("spruce")).set(d[0], 2, d[1], HAY);
            s.set(d[0], 3, d[1], "minecraft:carved_pumpkin[facing=south]");
            s.set(d[0] - 1, 2, d[1], Kit.fence("spruce")).set(d[0] + 1, 2, d[1], Kit.fence("spruce"));
        }
        // A weapon rack and a bench.
        s.set(15, 1, 13, GRINDSTONE).set(15, 1, 14, BARREL).set(15, 2, 14, BARREL).set(14, 1, 13, Kit.chest("south"));
        s.set(4, 1, 13, Kit.stairs(SPRUCE_STAIRS, "north")).set(3, 1, 13, Kit.stairs(SPRUCE_STAIRS, "north"));
        lampPost(s, 5, 1, 17, 2);
        lampPost(s, 11, 1, 18, 2);
    }

    // ---------------------------------------------------------------- the castle garden

    /** The middle of the garden, where the pavilion stands. */
    private static final int GARDEN_MID = 8;
    private static final String HEDGE = Kit.leaves("azalea");
    private static final String BLOSSOM = Kit.leaves("cherry");
    private static final String OAK_LEAVES = Kit.leaves("oak");

    /**
     * The castle garden: a square of lawn inside a clipped hedge with stone piers at the entrance. Two paths of gravel cross
     * in the middle, where a round pavilion stands on steps: eight slender columns under a ring of stone and a slate dome
     * with a lantern on its top and one hanging inside. Before it, on either side of the way in, a round basin of water with
     * a lamp on a pedestal in the middle; behind it two trees in flower beds. Low hedges edge the beds, benches stand at the
     * ends of the paths, lamp posts light the ways.
     */
    static Structure castleGarden() {
        Structure s = new Structure(17, 12, 17);
        s.fill(0, 0, 0, 16, 0, 16, GRASS);
        gardenPaths(s);
        for (int x : new int[]{3, 13}) {
            gardenTree(s, x, 3);
            gardenBasin(s, x, 13);
        }
        gardenBeds(s);
        pavilion(s);
        // The lawn stays grass only where nothing stands on it.
        for (int z = 0; z < s.sizeZ; z++) {
            for (int x = 0; x < s.sizeX; x++) {
                String above = s.get(x, 1, z);
                if (s.get(x, 0, z).equals(GRASS) && (above.contains("_leaves") || above.contains("_log") || above.contains("_wall"))) {
                    s.set(x, 0, z, DIRT);
                }
            }
        }
        s.connect();
        return s;
    }

    /**
     * The hedge around the garden with the entrance, the paths of gravel edged with trim, benches at their ends and lamp
     * posts.
     */
    private static void gardenPaths(Structure s) {
        s.fill(0, 0, 0, 16, 0, 0, Kit.ROUGH).fill(0, 0, 16, 16, 0, 16, Kit.ROUGH);
        s.fill(0, 0, 0, 0, 0, 16, Kit.ROUGH).fill(16, 0, 0, 16, 0, 16, Kit.ROUGH);
        s.fill(0, 1, 0, 16, 2, 0, HEDGE);
        s.fill(0, 1, 16, 16, 1, 16, HEDGE).fill(0, 1, 0, 0, 1, 16, HEDGE).fill(16, 1, 0, 16, 1, 16, HEDGE);
        // The two paths across, each three wide.
        s.fill(7, 0, 1, 9, 0, 16, GRAVEL).fill(1, 0, 7, 15, 0, 9, GRAVEL);
        s.fill(7, 0, 16, 9, 0, 16, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        s.fill(7, 1, 16, 9, 1, 16, AIR);
        // The piers of the entrance and of the corners.
        for (int[] p : new int[][]{{6, 16}, {10, 16}, {0, 16}, {16, 16}, {0, 0}, {16, 0}}) {
            s.fill(p[0], 1, p[1], p[0], 2, p[1], Kit.WALL).set(p[0], 3, p[1], Kit.TRIM).set(p[0], 4, p[1], Kit.lantern());
        }
        // Benches at the ends of the paths, their backs to the hedge.
        for (int z = 7; z <= 9; z++) {
            s.set(1, 1, z, Kit.stairs(Kit.TRIM_STAIRS, "west")).set(15, 1, z, Kit.stairs(Kit.TRIM_STAIRS, "east"));
        }
        for (int x = 7; x <= 9; x++) {
            s.set(x, 1, 1, Kit.stairs(Kit.TRIM_STAIRS, "north"));
        }
        // Lamp posts where the paths leave the pavilion.
        for (int[] p : new int[][]{{6, 6}, {10, 6}, {6, 10}, {10, 10}}) {
            lampPost(s, p[0], 1, p[1], 2);
        }
    }

    /**
     * A tree in a round bed: a trunk of oak, a rounded crown of leaves with flowering leaves through it and a light hidden
     * inside, flowers around its foot.
     */
    private static void gardenTree(Structure s, int x, int z) {
        s.fill(x, 1, z, x, 6, z, Kit.log(OAK_LOG, "y"));
        // A round crown around the layer 6, its outer leaves left out here and there so that its edge is not straight.
        for (int dy = -2; dy <= 3; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int r = dx * dx + dz * dz + dy * dy;
                    boolean outer = r > 7;
                    if (r > 11 || dx == 0 && dz == 0 && dy <= 0 || outer && Math.floorMod(dx * 7 + dz * 11 + dy * 5, 5) == 0) {
                        continue;
                    }
                    s.set(x + dx, 6 + dy, z + dz, Math.floorMod(dx * 3 + dz * 5 + dy, 4) == 0 ? OAK_LEAVES : BLOSSOM);
                }
            }
        }
        s.set(x, 7, z, "minecraft:shroomlight");
        // A round bed of flowers at its foot, red and white in rings.
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.inDisc(dx, dz, 5) && (dx != 0 || dz != 0) && !s.solid(x + dx, 1, z + dz)) {
                    s.set(x + dx, 1, z + dz, Kit.onRim(dx, dz, 5) ? "minecraft:red_tulip" : "minecraft:lily_of_the_valley");
                }
            }
        }
    }

    /**
     * A round basin of water with a rim of trim and a lamp on a pedestal in the middle.
     */
    private static void gardenBasin(Structure s, int x, int z) {
        Kit.disc(s, x, 0, z, 5, Kit.ROUGH);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Kit.onRim(dx, dz, 5)) {
                    s.set(x + dx, 1, z + dz, Kit.TRIM);
                } else if (Kit.inDisc(dx, dz, 5)) {
                    s.set(x + dx, 1, z + dz, WATER);
                }
            }
        }
        s.set(x, 1, z, Kit.CARVED).set(x, 2, z, Kit.ROUGH_FENCE).set(x, 3, z, Kit.lantern());
        // Rose bushes in the corners of the bed around the basin.
        for (int dz : new int[]{-2, 2}) {
            for (int dx : new int[]{-2, 2}) {
                s.set(x + dx, 1, z + dz, "minecraft:rose_bush[half=lower]").set(x + dx, 2, z + dz, "minecraft:rose_bush[half=upper]");
            }
        }
    }

    /**
     * The beds along the paths: low hedges where the beds meet the gravel, rows of flowers inside.
     */
    private static void gardenBeds(Structure s) {
        String[] rows = {"minecraft:red_tulip", "minecraft:white_tulip", "minecraft:lily_of_the_valley", "minecraft:pink_tulip"};
        for (int side : new int[]{1, -1}) {
            int edge = GARDEN_MID - 2 * side;
            // Hedges along the way in and along the cross path, broken by the lamp posts.
            for (int z = 11; z <= 15; z++) {
                s.set(GARDEN_MID - 2 * side, 1, z, HEDGE);
            }
            for (int z = 1; z <= 5; z++) {
                s.set(GARDEN_MID - 2 * side, 1, z, HEDGE);
            }
            for (int x = 1; x <= 5; x++) {
                int xx = side > 0 ? x : 16 - x;
                s.set(xx, 1, 6, HEDGE).set(xx, 1, 10, HEDGE);
            }
            // The ends of the hedges clipped into taller posts.
            for (int z : new int[]{1, 5, 11, 15}) {
                s.set(edge, 2, z, HEDGE);
            }
            for (int z : new int[]{6, 10}) {
                s.set(side > 0 ? 1 : 15, 2, z, HEDGE).set(side > 0 ? 5 : 11, 2, z, HEDGE);
            }
            // Flowers between the hedges and the basins and trees, wherever the lawn is free.
            for (int z = 1; z <= 15; z++) {
                int x = edge - side;
                if (!s.solid(x, 1, z) && s.get(x, 0, z).equals(GRASS)) {
                    s.set(x, 1, z, rows[z % rows.length]);
                }
            }
        }
    }

    /**
     * The pavilion in the middle: a round floor on a ring of steps, eight slender columns, a ring of stone over them and a
     * slate dome with a lantern on its top; a lantern hangs inside on a chain, a pedestal with a pot stands in the middle.
     */
    private static void pavilion(Structure s) {
        int c = GARDEN_MID;
        Kit.disc(s, c, 0, c, 7, Kit.ROUGH);
        Kit.disc(s, c, 1, c, 5, Kit.TRIM);
        stairRing(s, c, 1, c, 5, Kit.TRIM_STAIRS, false);
        for (int y = 2; y <= 4; y++) {
            Kit.disc(s, c, y, c, 5, AIR);
        }
        for (int[] p : new int[][]{{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}}) {
            s.fill(c + p[0], 2, c + p[1], c + p[0], 4, c + p[1], Kit.WALL_FENCE);
        }
        Kit.tube(s, c, 5, 5, c, 5, Kit.TRIM);
        int top = coneRoof(s, c, 6, c, 5, new int[]{1, 1, 2}, Kit.SLATE);
        s.set(c, top, c, Kit.lantern());
        s.fill(c, 6, c, c, 7, c, Kit.chain());
        s.set(c, 5, c, Kit.hangingLantern());
        s.set(c, 2, c, Kit.CARVED).set(c, 3, c, "minecraft:flower_pot");
    }
}
