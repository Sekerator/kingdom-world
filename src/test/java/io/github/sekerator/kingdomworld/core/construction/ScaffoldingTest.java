package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scaffolds of the builders: the column chosen for a block out of reach and its height, the cells no scaffold may take,
 * the order they are taken down in, and that none is forgotten when a construction goes away.
 */
class ScaffoldingTest {
    private static final double REACH = 4.5;
    private static final double EYE = 1.53;

    /**
     * Flat ground with its top at y 0 (feet stand at y 1), blocks placed on it, cells no scaffold may take and grounds that
     * carry none.
     */
    private static final class Ground implements Scaffolding.World {
        final Set<Long> solid = new HashSet<>();
        final Set<Long> forbidden = new HashSet<>();
        final Set<Long> holding = new HashSet<>();
        final Set<Long> planned = new HashSet<>();

        Ground block(int x, int y, int z) {
            solid.add(Scaffolding.pack(x, y, z));
            return this;
        }

        boolean solid(int x, int y, int z) {
            return y <= 0 || solid.contains(Scaffolding.pack(x, y, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            return !solid(x, y, z);
        }

        @Override
        public boolean firm(int x, int y, int z) {
            return solid(x, y, z);
        }

        @Override
        public boolean placeable(int x, int y, int z) {
            return !solid(x, y, z) && !forbidden.contains(Scaffolding.pack(x, 0, z)) && !forbidden.contains(Scaffolding.pack(x, y, z));
        }

        @Override
        public boolean carries(int x, int y, int z) {
            return solid(x, y, z) && !holding.contains(Scaffolding.pack(x, y, z));
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return planned.contains(Scaffolding.pack(x, y, z));
        }
    }

    private static boolean reaches(Scaffolding.Spot spot, int x, int y, int z) {
        return Scaffolding.reaches(spot.x() + 0.5, spot.feet() + EYE, spot.z() + 0.5, x, y, z, REACH);
    }

    @Test
    void aBlockWithinReachIsWorkedFromTheGround() {
        Ground ground = new Ground();
        Optional<Scaffolding.Spot> spot = Scaffolding.find(3, 3, 0, REACH, EYE, ground, 0, 1, 0, cell -> false);
        assertTrue(spot.isPresent());
        assertEquals(0, spot.get().height());
        assertEquals(1, spot.get().ground());
        assertTrue(reaches(spot.get(), 3, 3, 0));
    }

    @Test
    void aRoofOutOfReachGetsTheLowestColumnThatReachesIt() {
        Ground ground = new Ground();
        int roof = 9;
        Optional<Scaffolding.Spot> spot = Scaffolding.find(0, roof, 0, REACH, EYE, ground, 5, 1, 0, cell -> false);
        assertTrue(spot.isPresent());
        Scaffolding.Spot found = spot.get();
        assertTrue(found.height() > 0, "a block nine blocks up needs scaffolds");
        assertTrue(reaches(found, 0, roof, 0), "the builder reaches the block from the top of the column");
        // One scaffold less does not reach it.
        assertFalse(Scaffolding.reaches(found.x() + 0.5, found.feet() - 1 + EYE, found.z() + 0.5, 0, roof, 0, REACH));
        assertEquals(found.height(), found.cells().size());
        assertEquals(found.ground(), found.cells().get(0)[1]);
        assertEquals(found.feet() - 1, found.cells().get(found.cells().size() - 1)[1]);
        // The body is never in the block: under it the head stays below it.
        if (found.x() == 0 && found.z() == 0) {
            assertTrue(found.feet() + 1 < roof);
        }
    }

    @Test
    void aColumnNearTheBuilderGoesFirstAmongEqualOnes() {
        Ground ground = new Ground();
        Scaffolding.Spot east = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 20, 1, 0, cell -> false).orElseThrow();
        Scaffolding.Spot west = Scaffolding.find(0, 9, 0, REACH, EYE, ground, -20, 1, 0, cell -> false).orElseThrow();
        assertEquals(east.height(), west.height());
        assertTrue(east.x() >= 0, "from the east the column stands east: " + east);
        assertTrue(west.x() <= 0, "from the west the column stands west: " + west);
    }

    @Test
    void forbiddenCellsTakeNoScaffold() {
        Ground ground = new Ground();
        // Everything around the block is a road, a building of others or the land of others, but for one column.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (!(x == 2 && z == -1)) {
                    ground.forbidden.add(Scaffolding.pack(x, 0, z));
                }
            }
        }
        Scaffolding.Spot spot = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        assertEquals(2, spot.x());
        assertEquals(-1, spot.z());
        // With that column taken too, no column helps.
        ground.forbidden.add(Scaffolding.pack(2, 0, -1));
        assertTrue(Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).isEmpty());
        // A block reached from the ground needs no scaffold, so forbidden cells do not matter to it.
        assertEquals(0, Scaffolding.find(0, 3, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow().height());
    }

    @Test
    void aForbiddenCellHighUpEndsTheColumnThere() {
        Ground ground = new Ground();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                ground.forbidden.add(Scaffolding.pack(x, 3, z));
            }
        }
        assertTrue(Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).isEmpty(),
                "no column may pass a layer that takes no scaffold");
    }

    @Test
    void aGroundHoldingSomethingCarriesNoColumn() {
        Ground ground = new Ground();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                ground.holding.add(Scaffolding.pack(x, 0, z));
            }
        }
        assertTrue(Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).isEmpty());
        // A chest carries no column, but one may stand on it.
        assertEquals(0, Scaffolding.find(1, 3, 1, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow().height());
    }

    @Test
    void columnsTheBuilderCouldNotGetToAreLeftOut() {
        Ground ground = new Ground();
        Scaffolding.Spot first = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        long refused = Scaffolding.pack(first.x(), 0, first.z());
        Scaffolding.Spot second = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> cell == refused).orElseThrow();
        assertFalse(second.x() == first.x() && second.z() == first.z());
        assertEquals(first.height(), second.height());
    }

    @Test
    void aBlockTooHighForAnyColumnIsLeftToTheNearestPoint() {
        Ground ground = new Ground();
        int high = 1 + Scaffolding.MAX_HEIGHT + 8;
        assertTrue(Scaffolding.find(0, high, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).isEmpty());
        Scaffolding.Spot highest = Scaffolding.find(0, Scaffolding.MAX_HEIGHT + 3, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        assertTrue(highest.height() <= Scaffolding.MAX_HEIGHT);
    }

    @Test
    void aWalledInBlockHasNoColumn() {
        Ground ground = new Ground();
        // A block in the middle of a solid cube: no body fits anywhere near it at its height.
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = 1; y <= 14; y++) {
                    ground.block(x, y, z);
                }
            }
        }
        assertTrue(Scaffolding.find(0, 8, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).isEmpty());
    }

    @Test
    void aColumnStandsInsideAHouseUnderItsRoof() {
        Ground ground = new Ground();
        // Walls eight high around an inside of 5 by 5, the first ring of the roof on top of them; the middle of the roof is next.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
                for (int y = 1; y <= 8 && wall; y++) {
                    ground.block(x, y, z);
                }
                if (Math.abs(x) >= 2 || Math.abs(z) >= 2) {
                    ground.block(x, 9, z);
                }
            }
        }
        // The builder stands inside on the floor: the roof it could stand on is no place it walks to.
        Scaffolding.Spot spot = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        assertTrue(Math.abs(spot.x()) <= 2 && Math.abs(spot.z()) <= 2, "the column stands inside: " + spot);
        assertEquals(1, spot.ground(), "the column stands on the floor: " + spot);
        assertTrue(spot.height() > 0);
        assertTrue(reaches(spot, 0, 9, 0));
        assertTrue(spot.feet() + 1 < 9 || spot.x() != 0 || spot.z() != 0);
    }

    @Test
    void aColumnOutOfTheWayOfTheBuildingGoesFirst() {
        Ground ground = new Ground();
        Scaffolding.Spot free = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        for (int y = 1; y <= 12; y++) {
            ground.planned.add(Scaffolding.pack(free.x(), y, free.z()));
        }
        Scaffolding.Spot other = Scaffolding.find(0, 9, 0, REACH, EYE, ground, 0, 1, 0, cell -> false).orElseThrow();
        assertFalse(other.x() == free.x() && other.z() == free.z(), "a column where the walls go up later is passed over");
    }

    @Test
    void aBuilderOnAColumnClimbsOrGoesDownToTheNextBlock() {
        Ground ground = new Ground();
        // On a column at 1, 0 standing with its feet at 6 (five scaffolds).
        OptionalInt lower = Scaffolding.feetOn(1, 0, 1, 6, 1, 4, 3, REACH, EYE, ground);
        assertTrue(lower.isPresent());
        assertTrue(lower.getAsInt() <= 6);
        OptionalInt higher = Scaffolding.feetOn(1, 0, 1, 6, 0, 13, 0, REACH, EYE, ground);
        assertTrue(higher.isPresent());
        assertTrue(higher.getAsInt() > 6);
        assertTrue(Scaffolding.reaches(1.5, higher.getAsInt() + EYE, 0.5, 0, 13, 0, REACH));
        // The block is the column itself, or too far to the side: the builder comes down and goes elsewhere.
        assertTrue(Scaffolding.feetOn(1, 0, 1, 6, 1, 3, 0, REACH, EYE, ground).isEmpty());
        assertTrue(Scaffolding.feetOn(1, 0, 1, 6, 12, 6, 0, REACH, EYE, ground).isEmpty());
        // A cell above that takes no scaffold stops the climb.
        ground.forbidden.add(Scaffolding.pack(1, 7, 0));
        assertTrue(Scaffolding.feetOn(1, 0, 1, 6, 0, 13, 0, REACH, EYE, ground).isEmpty());
    }

    @Test
    void scaffoldsComeDownFromTheTop() {
        List<Long> cells = List.of(Scaffolding.pack(0, 1, 0), Scaffolding.pack(0, 3, 0), Scaffolding.pack(4, 2, 1), Scaffolding.pack(0, 2, 0),
                Scaffolding.pack(4, 3, 1));
        List<Long> order = Scaffolding.takeDownOrder(cells);
        for (int i = 1; i < order.size(); i++) {
            assertTrue(Scaffolding.y(order.get(i - 1)) >= Scaffolding.y(order.get(i)), "from the top down: " + order);
        }
        assertEquals(cells.size(), order.size());
        assertEquals(1, Scaffolding.y(order.get(order.size() - 1)));
    }

    @Test
    void positionsArePackedAndUnpacked() {
        int[][] positions = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-30_000_000, -2048, 12}, {123, 2047, -456}};
        for (int[] position : positions) {
            long packed = Scaffolding.pack(position[0], position[1], position[2]);
            assertEquals(position[0], Scaffolding.x(packed));
            assertEquals(position[1], Scaffolding.y(packed));
            assertEquals(position[2], Scaffolding.z(packed));
        }
        assertNotEquals(Scaffolding.pack(1, 2, 3), Scaffolding.pack(3, 2, 1));
    }

    @Test
    void theBandAroundTheSiteIsThreeBlocks() {
        Box box = new Box(0, 64, 0, 9, 72, 9);
        assertTrue(Scaffolding.inBand(box, -3, 64, 5));
        assertTrue(Scaffolding.inBand(box, 12, 61, 12));
        assertTrue(Scaffolding.inBand(box, 5, 74, 5));
        assertFalse(Scaffolding.inBand(box, -4, 64, 5));
        assertFalse(Scaffolding.inBand(box, 5, 60, 5));
        assertFalse(Scaffolding.inBand(box, 5, 75, 5));
        assertFalse(Scaffolding.inBand(box, 5, 64, 13));
    }

    // ---------------------------------------------------------------- kept with the construction

    private static Construction construction(long id) {
        return new Construction(id, UUID.randomUUID(), "kingdomworld:house", 1L, new WorldPos("minecraft:overworld", 0, 64, 0),
                new Placement(0, false), new Box(0, 64, 0, 9, 72, 9), 0L, List.of(), 10);
    }

    @Test
    void aConstructionKeepsItsScaffoldsUntilTheyAreTakenDown() {
        Construction construction = construction(1);
        long a = Scaffolding.pack(1, 65, 1);
        long b = Scaffolding.pack(1, 66, 1);
        assertTrue(construction.addScaffold(a));
        assertTrue(construction.addScaffold(b));
        assertTrue(construction.addScaffold(b));
        assertEquals(2, construction.scaffolds().size());
        construction.removeScaffold(b);
        assertEquals(Set.of(a), construction.scaffolds());
        construction.removeScaffold(a);
        assertFalse(construction.hasScaffolds());
        // A copy read back from saved data has the same scaffolds.
        Construction copy = construction(1);
        copy.restoreScaffolds(new long[]{a, b});
        assertEquals(Set.of(a, b), copy.scaffolds());
    }

    @Test
    void scaffoldsOfAConstructionThatGoesAwayAreLeftToBeTakenDown() {
        ConstructionBook book = new ConstructionBook();
        Construction cancelled = book.add(UUID.randomUUID(), "kingdomworld:house", 1L, new WorldPos("minecraft:overworld", 0, 64, 0),
                new Placement(0, false), new Box(0, 64, 0, 9, 72, 9), 0L, List.of(), 10);
        long a = Scaffolding.pack(1, 65, 1);
        long b = Scaffolding.pack(1, 66, 1);
        cancelled.addScaffold(a);
        cancelled.addScaffold(b);
        book.remove(cancelled.id());
        assertFalse(cancelled.hasScaffolds());
        assertEquals(Set.of(a, b), book.leftScaffolds().get("minecraft:overworld"));
        book.forgetLeftScaffold("minecraft:overworld", b);
        book.forgetLeftScaffold("minecraft:overworld", a);
        assertTrue(book.leftScaffolds().isEmpty(), "nothing is left once every scaffold is taken down");
        assertEquals(0, book.leftScaffoldCount());

        // A kingdom that goes away leaves the scaffolds of its constructions too.
        UUID kingdom = UUID.randomUUID();
        Construction other = book.add(kingdom, "kingdomworld:house", 1L, new WorldPos("minecraft:the_nether", 0, 64, 0), new Placement(0, false),
                new Box(0, 64, 0, 9, 72, 9), 0L, List.of(), 10, Construction.Kind.BUILD, 0, Materials.NONE, new BitSet());
        other.addScaffold(a);
        book.removeKingdom(kingdom);
        assertEquals(Set.of(a), book.leftScaffolds().get("minecraft:the_nether"));
        // Read back from saved data.
        ConstructionBook restored = new ConstructionBook();
        restored.restoreLeftScaffolds("minecraft:the_nether", new long[]{a});
        assertEquals(book.leftScaffolds(), restored.leftScaffolds());
    }

    @Test
    void aPlaceOutOfReachIsNoLongerAProblemOfTheConstruction() {
        Construction construction = construction(2);
        construction.restore(Construction.Priority.NORMAL, false, false, new BitSet(), List.of(), Map.of(), Map.of(),
                Construction.Status.UNREACHABLE, new WorldPos("minecraft:overworld", 1, 70, 1), 0.0);
        assertEquals(Construction.Status.NO_BUILDER, construction.status());
        assertEquals(null, construction.statusPos());
        assertFalse(Construction.Status.UNREACHABLE.isProblem());
    }
}
