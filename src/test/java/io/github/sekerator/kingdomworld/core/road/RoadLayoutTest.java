package io.github.sekerator.kingdomworld.core.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocks of a road of the first layout stay what they were before roads were levelled: the blocks, what each needs, the
 * materials and the seconds of work of every class on a few ways, taken from the code as it was and kept here as a sum.
 */
class RoadLayoutTest {

    /**
     * Ways of every shape: straight, diagonal, a bend, a bridge, an embankment, a jump.
     */
    static List<List<RouteCell>> ways() {
        List<List<RouteCell>> ways = new ArrayList<>();
        List<RouteCell> straight = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            straight.add(new RouteCell(100 + i, 64 + i / 10, 50, RouteCell.Kind.GROUND, 0, null));
        }
        ways.add(straight);
        List<RouteCell> diagonal = new ArrayList<>();
        int x = -30;
        int z = 12;
        for (int i = 0; i < 30; i++) {
            diagonal.add(new RouteCell(x, 70, z, RouteCell.Kind.GROUND, 0, null));
            if (i % 2 == 0) {
                x++;
            } else {
                z--;
            }
        }
        ways.add(diagonal);
        List<RouteCell> mixed = new ArrayList<>();
        x = 0;
        z = 0;
        int y = 63;
        for (int i = 0; i < 120; i++) {
            RouteCell.Kind kind = i >= 30 && i < 45 ? RouteCell.Kind.BRIDGE : i >= 70 && i < 76 ? RouteCell.Kind.FILL : RouteCell.Kind.GROUND;
            int depth = kind == RouteCell.Kind.FILL ? 1 + i % 3 : 0;
            mixed.add(new RouteCell(x, y, z, kind, depth, null));
            if (i < 50) {
                x++;
            } else if (i < 80) {
                x++;
                z++;
            } else if (i < 100) {
                z++;
            } else {
                x--;
            }
            if (i % 17 == 0) {
                y++;
            }
        }
        mixed.add(new RouteCell(x + 5, y - 9, z + 3, RouteCell.Kind.GROUND, 0, null));
        mixed.add(new RouteCell(x + 6, y - 9, z + 3, RouteCell.Kind.GROUND, 0, null));
        ways.add(mixed);
        return ways;
    }

    /**
     * Everything the layout says of the ways, one line a block.
     */
    static String describe(List<List<RouteCell>> ways) {
        StringBuilder text = new StringBuilder();
        for (List<RouteCell> way : ways) {
            for (RoadClass roadClass : RoadClass.values()) {
                List<RoadLayout.Block> blocks = RoadLayout.blocks(way, roadClass);
                text.append(roadClass.id()).append(' ').append(blocks.size()).append('\n');
                for (RoadLayout.Block block : blocks) {
                    text.append(block.cell()).append(',').append(block.x()).append(',').append(block.y()).append(',').append(block.z())
                            .append(',').append(block.kind().id()).append(',').append(block.depth()).append(',').append(block.side())
                            .append(',').append(block.needs(roadClass)).append('\n');
                }
                int[] materials = RoadLayout.materials(blocks, roadClass);
                text.append(materials[0]).append(' ').append(materials[1]).append(' ').append(materials[2]).append(' ')
                        .append(RoadLayout.seconds(blocks, roadClass, RoadRules.DEFAULT)).append('\n');
            }
        }
        return text.toString();
    }

    static long sum(String text) {
        long hash = 0xcbf2_9ce4_8422_2325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100_0000_01b3L;
        }
        return hash;
    }

    @Test
    void theFirstLayoutIsWhatItWas() {
        String text = describe(ways());
        assertEquals(GOLDEN_LINES, text.split("\n").length);
        assertEquals(GOLDEN_SUM, sum(text), "the blocks of the first layout changed");
    }

    @Test
    void aLevelledRoadHasItsMiddleTheWayAndAVergeOnEachSide() {
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cells.add(new RouteCell(i, 70, 0, RouteCell.Kind.GROUND, 0, null));
        }
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, RoadClass.GRAVEL, RoadLayout.LEVELLED, null);
        assertEquals(50, blocks.size());
        assertEquals(10, blocks.stream().filter(b -> b.role() == RoadLayout.Role.MIDDLE).count());
        assertEquals(20, blocks.stream().filter(b -> b.role() == RoadLayout.Role.SIDE).count());
        assertEquals(20, blocks.stream().filter(b -> b.role() == RoadLayout.Role.VERGE).count());
        // The middle first, then the way beside it, then the verges.
        assertEquals(List.of(RoadLayout.Role.MIDDLE, RoadLayout.Role.SIDE, RoadLayout.Role.SIDE, RoadLayout.Role.VERGE, RoadLayout.Role.VERGE),
                blocks.subList(0, 5).stream().map(RoadLayout.Block::role).toList());
        assertEquals(2, Math.abs(blocks.get(3).z()));
        assertTrue(blocks.stream().allMatch(b -> b.survey() == RoadLayout.ColumnSurvey.EMPTY));
        List<RoadLayout.Block> path = RoadLayout.blocks(cells, RoadClass.PATH, RoadLayout.LEVELLED, null);
        assertEquals(30, path.size(), "a path of one block and its two verges");
        assertEquals(RoadLayout.blocks(cells, RoadClass.GRAVEL), RoadLayout.blocks(cells, RoadClass.GRAVEL, RoadLayout.LAID, null));
    }

    @Test
    void aBendOfALevelledRoadTakesEveryColumnOnceWithItsWeightiestRole() {
        for (List<RouteCell> way : ways()) {
            for (RoadClass roadClass : RoadClass.values()) {
                List<RoadLayout.Block> blocks = RoadLayout.blocks(way, roadClass, RoadLayout.LEVELLED, null);
                Set<Long> columns = new HashSet<>();
                Set<Long> middles = new HashSet<>();
                for (RouteCell cell : way) {
                    middles.add(RoadLayout.column(cell.x(), cell.z()));
                }
                int last = 0;
                for (RoadLayout.Block block : blocks) {
                    assertTrue(columns.add(RoadLayout.column(block.x(), block.z())), "a column twice");
                    assertTrue(block.cell() >= last, "blocks in the order of the way");
                    last = block.cell();
                    if (middles.contains(RoadLayout.column(block.x(), block.z()))) {
                        assertEquals(RoadLayout.Role.MIDDLE, block.role(), "the middle of the way is never a verge");
                    }
                }
            }
        }
    }

    @Test
    void surveysThatDoNotFitTheBlocksAreLeftOut() {
        List<RouteCell> cells = List.of(new RouteCell(0, 70, 0, RouteCell.Kind.CUT, 4, null), new RouteCell(1, 70, 0, RouteCell.Kind.CUT, 4, null));
        RoadLayout.ColumnSurvey dug = new RoadLayout.ColumnSurvey(1, 3, 0, 0, 0, 0, false, false);
        List<RoadLayout.Block> fitting = RoadLayout.blocks(cells, RoadClass.PATH, RoadLayout.LEVELLED, java.util.Collections.nCopies(6, dug));
        assertTrue(fitting.stream().allMatch(b -> b.survey().equals(dug)));
        List<RoadLayout.Block> wrong = RoadLayout.blocks(cells, RoadClass.PATH, RoadLayout.LEVELLED, java.util.Collections.nCopies(5, dug));
        assertTrue(wrong.stream().allMatch(b -> b.survey() == RoadLayout.ColumnSurvey.EMPTY));
    }

    private static final int GOLDEN_LINES = 1308;
    private static final long GOLDEN_SUM = 6181460719879106214L;
}
