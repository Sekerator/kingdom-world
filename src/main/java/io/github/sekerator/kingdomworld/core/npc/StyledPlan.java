package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Gate;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Lot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Rank;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Spot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The plan of a capital laid out in an architectural style ({@link NpcSite#LAYOUT_STYLED}), the same for the same seed: the
 * castle of the style around the throne, the square around it, then two rings of rows. The first ring looks at the square:
 * the house of the adventurers' guild and the tower of the mages by the south avenue, the treasury, the tavern, the court and the
 * infirmary on the other sides, the houses of the nobles between them, monuments, a chapel, a clock tower or a park in its open
 * corners. The second ring looks out to the wall: the market on the south, the craftsmen and the yards of the builders and
 * carriers on the east and west, the barracks of the army on the north, and houses behind them: the merchants by the market, the
 * craftsmen on the east and west, the poor along the north wall and towards the north corners. Avenues run from the square to
 * the gates of the wall, which stand in squares of their own; towers stand in the corners of the wall. Houses stand back from
 * their street by up to two blocks, some behind a front garden, the gaps between them differ, and trees stand in wide gaps, in
 * rows in the deep gardens behind the buildings and on the greens.
 *
 * <p>All positions are in blocks from the throne, x to the east and z to the south.
 *
 * <p>The plan draws its buildings from the lists of {@link TemplateCatalog#ofStyle(String, Kind)} in the order of the table and
 * is made anew from the seed of the place whenever it is needed, so adding, removing or reordering the templates of a kind of a
 * style changes the capitals that are half raised. Such a change needs a new layout or a frozen list.
 */
public final class StyledPlan {
    /** Blocks from an avenue to where the rows of the last ring begin on a side with a gate: the gate square. */
    public static final int GATE_CLEAR = 13;

    private static final int AVENUE = CityPlan.AVENUE;
    /** Blocks between two buildings of a row at least. */
    private static final int GAP = 2;
    /** The side of a free lot for a building to come. */
    private static final int LOT = 11;
    /** A gap between two buildings this wide or wider has a tree. */
    private static final int TREE_GAP = 5;
    /** Behind a house this much ground up to the back of its ring has a tree. */
    private static final int TREE_BACK = 7;
    /** Blocks between a tree and the ground of a building at least. */
    private static final int TREE_CLEAR = 3;
    /** The deepest setback of a house from the line of the fronts of its row. */
    private static final int SETBACK = 2;
    /**
     * Where the rows of the last ring begin on the sides that have fields inside the wall: past the outer fields, whose middles
     * lie 21 blocks from the avenue and which reach five blocks from their middles, and a gap.
     */
    static final int FIELD_CLEAR = 21 + 5 + 1 + GAP;
    /**
     * The beds of a capital by level beyond which no more houses are put up: the rows of styles with small houses would hold
     * twice the people of those with large ones. What room is left stays open, with trees and lots for buildings to come.
     */
    private static final int[] HOMES = {36, 52, 90};
    /** Blocks between the trees of a long gap. */
    private static final int TREE_STEP = 8;

    private static final int NORTH = CityPlan.NORTH;
    private static final int EAST = CityPlan.EAST;
    private static final int SOUTH = CityPlan.SOUTH;
    private static final int WEST = CityPlan.WEST;

    private static final String NS = "kingdomworld:";
    static final String CASTLE = "castle";
    static final String GUILD = "guild";
    static final String MAGES = "mages";
    static final String PUBLIC = "public";
    static final String NOBLE = "noble";
    static final String MERCHANT = "merchant";
    static final String CRAFT = "craft";
    static final String POOR = "poor";

    /** The decorations of the corners of the square, before the seed shuffles them. */
    private static final List<String> DECORATIONS = List.of("classic_monument", "classic_clock_tower", "classic_chapel", "classic_park");
    /** The corners of the square and of the wall, by signs of x and z: north-west, north-east, south-west, south-east. */
    private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    /** The half-rows of a ring in the order they are filled: {side, half}. */
    private static final int[][] ORDER = {{SOUTH, 0}, {SOUTH, 1}, {EAST, 1}, {EAST, 0}, {WEST, 1}, {WEST, 0}, {NORTH, 0}, {NORTH, 1}};
    /**
     * The half-rows of the last ring in the order their houses go up by turns, as indexes into {@link #ORDER}: a half of every
     * side before the other half of any, so that a side is not left without houses when the homes run out.
     */
    private static final int[] TURNS = {0, 2, 4, 6, 1, 3, 5, 7};

    private StyledPlan() {
    }

    /**
     * The measures of a styled capital of a level.
     *
     * @param precinct half the side of the ground of the castle, plus one
     * @param rings    where each ring of rows begins, from the throne
     * @param depths   how deep each ring of rows is
     * @param inward   whether the fronts of that ring look towards the throne
     * @param gardens  blocks of gardens between the last ring and the way along the wall
     * @param walk     blocks kept free along the wall inside it
     * @param gates    the sides that have a gate: 0 north, 1 east, 2 south, 3 west
     * @param fields   whether the last ring has fields inside the wall on its east and west sides, by the avenue, its rows there
     *                 beginning past them
     */
    public record Shape(NpcLevel level, int precinct, int[] rings, int[] depths, boolean[] inward, int gardens, int walk, int[] gates,
                        boolean fields) {
        /**
         * The outermost line of the last ring.
         */
        public int last() {
            int n = rings.length;
            return rings[n - 1] + depths[n - 1] - 1;
        }

        /**
         * The radius of the wall.
         */
        public int wall() {
            return last() + gardens + walk + 1;
        }

        boolean gate(int side) {
            for (int gate : gates) {
                if (gate == side) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The measures of the styled capital of a level.
     */
    public static Shape shape(NpcLevel level) {
        return switch (level) {
            case SMALL -> new Shape(level, 16, new int[]{27, 54}, new int[]{25, 15}, new boolean[]{true, false}, 0, 5,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH}, true);
            case MEDIUM -> new Shape(level, 21, new int[]{31, 62}, new int[]{29, 15}, new boolean[]{true, false}, 3, 6,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH, CityPlan.EAST, CityPlan.WEST}, false);
            case LARGE -> new Shape(level, 24, new int[]{38, 77}, new int[]{37, 15}, new boolean[]{true, false}, 3, 6,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH, CityPlan.EAST, CityPlan.WEST}, false);
        };
    }

    /**
     * How far the fields and the posts of the woodcutters and miners of a styled capital reach from the throne.
     */
    static int outerReach(Shape shape) {
        return shape.wall() + 12 + 6;
    }

    // ---------------------------------------------------------------- rows

    /**
     * A stretch of a ring between an avenue (or a gate square) and a corner, with what stands on it from the avenue outwards.
     */
    private static final class Row {
        final int ring;
        final int side;
        /** 0 for the half on the low coordinates (west of or north of the avenue), 1 for the other. */
        final int half;
        final int lo;
        final int hi;
        /** The line of the fronts, across the row. */
        final int front;
        final int facing;
        /** How deep the ring is. */
        final int depth;
        final List<Item> items = new ArrayList<>();

        Row(int ring, int side, int half, int lo, int hi, int front, int facing, int depth) {
            this.ring = ring;
            this.side = side;
            this.half = half;
            this.lo = lo;
            this.hi = hi;
            this.front = front;
            this.facing = facing;
            this.depth = depth;
        }

        int length() {
            return hi - lo + 1;
        }

        int used() {
            int sum = 0;
            for (Item item : items) {
                sum += item.entry().width();
            }
            return sum + Math.max(0, items.size() - 1) * GAP;
        }

        /**
         * Where the next building would begin, in blocks from the avenue end, the buildings before it packed.
         */
        int next() {
            return used() + (items.isEmpty() ? 0 : GAP);
        }

        boolean fits(Entry entry) {
            return entry.depth() <= depth && next() + entry.width() <= length();
        }

        boolean ns() {
            return side == NORTH || side == SOUTH;
        }

        /**
         * Whether the line of the fronts is the highest coordinate across the row: fronts looking south or east.
         */
        boolean high() {
            return facing == 0 || facing == 3;
        }

        /**
         * The lowest coordinate across the row of a building of a depth set back from the line of the fronts.
         */
        int acrossMin(int size, int setback) {
            return high() ? front - setback - size + 1 : front + setback;
        }

        /**
         * The coordinate across the row so many blocks behind the line of the fronts.
         */
        int behind(int blocks) {
            return high() ? front - blocks : front + blocks;
        }

        int[] point(int along, int across) {
            return ns() ? new int[]{along, across} : new int[]{across, along};
        }
    }

    /**
     * A building of a row.
     *
     * @param setback blocks between the line of the fronts and its front
     * @param house   whether it is a house of a quarter
     */
    private record Item(Entry entry, int setback, String quarter, boolean house) {
    }

    /**
     * The rows of a ring in the order they are filled. A ring whose fronts look inwards leaves its corners open; the last ring,
     * whose fronts look out, runs round its corners on its north and south sides, on a side with a gate begins past the gate
     * square and on a side with fields past the fields.
     */
    private static List<Row> rows(Shape shape, int ring) {
        List<Row> list = new ArrayList<>();
        int a = shape.rings()[ring];
        int depth = shape.depths()[ring];
        int b = a + depth - 1;
        boolean in = shape.inward()[ring];
        boolean last = ring == shape.rings().length - 1;
        // A last ring that reaches the way along the wall leaves its corners to the towers.
        int span = in ? a - 1 : last && shape.gardens() < 3 ? b - 4 : b;
        for (int[] order : ORDER) {
            int side = order[0];
            boolean ns = side == NORTH || side == SOUTH;
            int start = !last ? AVENUE + 1 : shape.gate(side) ? GATE_CLEAR : !ns && shape.fields() ? FIELD_CLEAR : AVENUE + 1;
            int end = ns ? span : a - 1;
            int lo = order[1] == 0 ? -end : start;
            int hi = order[1] == 0 ? -start : end;
            int front;
            int facing;
            switch (side) {
                case NORTH -> {
                    front = in ? -a : -b;
                    facing = in ? 0 : 2;
                }
                case SOUTH -> {
                    front = in ? a : b;
                    facing = in ? 2 : 0;
                }
                case EAST -> {
                    front = in ? a : b;
                    facing = in ? 1 : 3;
                }
                default -> {
                    front = in ? -a : -b;
                    facing = in ? 3 : 1;
                }
            }
            list.add(new Row(ring, side, order[1], lo, hi, front, facing, depth));
        }
        return list;
    }

    /**
     * What a row is for before its houses, from the avenue outwards: the institutions of the square on the first ring, the
     * market, workshops, stores, yards and barracks on the second.
     */
    private static List<Item> queue(NpcLevel level, String style, Rank rank, Row row) {
        boolean small = level == NpcLevel.SMALL;
        boolean large = level == NpcLevel.LARGE;
        List<Item> list = new ArrayList<>();
        int key = row.side * 2 + row.half;
        if (row.ring == 0) {
            switch (key) {
                case SOUTH * 2 -> TemplateCatalog.guildHouse(style, Kind.GUILD, rank).ifPresent(entry -> list.add(item(entry, GUILD)));
                case SOUTH * 2 + 1 -> {
                    TemplateCatalog.guildHouse(style, Kind.MAGE_TOWER, rank).ifPresent(entry -> list.add(item(entry, MAGES)));
                    if (small || large) {
                        list.add(item("well"));
                    }
                }
                case EAST * 2 + 1 -> {
                    list.add(item("classic_treasury"));
                    if (level == NpcLevel.MEDIUM) {
                        list.add(item("well"));
                    }
                }
                case EAST * 2 -> list.add(item(style + "_tavern"));
                case WEST * 2 + 1 -> list.add(item("classic_court"));
                case WEST * 2 -> list.add(item("classic_infirmary"));
                case NORTH * 2 -> {
                    if (large) {
                        list.add(item("classic_guard_barracks"));
                    }
                }
                default -> {
                    if (large) {
                        list.add(item("classic_stables"));
                        list.add(item("classic_garden"));
                    }
                }
            }
            return list;
        }
        String smithy = style + "_smithy";
        String barracks = style + "_camp_barracks";
        String store = style + "_camp_store";
        String yard = style + "_camp_yard";
        List<String> names = switch (key) {
            case SOUTH * 2 + 1 -> small ? List.of("market_square", "well", "bakery")
                    : large ? List.of("market_square", "fountain", "market_hall", "well") : List.of("market_square", "fountain", "market_hall");
            case SOUTH * 2 -> small ? List.of("classic_builders_yard", "classic_carters_yard")
                    : large ? List.of("market_square", "bakery", "weavery", "well") : List.of("bakery", "well");
            case EAST * 2 + 1 -> small ? List.of() : large ? List.of(smithy, "carpentry", "storehouse", "workshop")
                    : List.of(smithy, "carpentry", "workshop");
            case EAST * 2 -> small ? List.of() : large ? List.of("classic_builders_yard", "bakery", smithy, "barn")
                    : List.of("classic_builders_yard", "storehouse");
            case WEST * 2 + 1 -> small ? List.of() : large ? List.of("classic_carters_yard", "storage_yard", "well")
                    : List.of("classic_carters_yard", "barn");
            case WEST * 2 -> small ? List.of() : large ? List.of(store, "barn", "fountain") : List.of(store, "well");
            case NORTH * 2 -> small ? List.of(barracks, store) : large ? List.of(barracks, yard, barracks) : List.of(barracks, yard);
            default -> small ? List.of(smithy, "workshop", "barn") : large ? List.of(barracks, "well") : List.of(barracks);
        };
        for (String name : names) {
            list.add(item(name));
        }
        return list;
    }

    private static Item item(String name) {
        return item(TemplateCatalog.of(NS + name), PUBLIC);
    }

    private static Item item(Entry entry, String quarter) {
        return new Item(entry, 0, quarter, false);
    }

    /**
     * The houses a quarter draws from: the nobles by the square take noble and merchant houses, the merchants merchant and craft
     * houses, the craftsmen craft houses, the poor huts and the narrower craft houses.
     */
    private static List<Entry> pool(String style, String quarter) {
        List<Entry> list = new ArrayList<>();
        switch (quarter) {
            case NOBLE -> {
                list.addAll(TemplateCatalog.ofStyle(style, Kind.NOBLE_HOUSE));
                list.addAll(TemplateCatalog.ofStyle(style, Kind.MERCHANT_HOUSE));
            }
            case MERCHANT -> {
                list.addAll(TemplateCatalog.ofStyle(style, Kind.MERCHANT_HOUSE));
                list.addAll(TemplateCatalog.ofStyle(style, Kind.CRAFT_HOUSE));
            }
            case CRAFT -> list.addAll(TemplateCatalog.ofStyle(style, Kind.CRAFT_HOUSE));
            case POOR -> {
                list.addAll(TemplateCatalog.ofStyle(style, Kind.POOR_HOUSE));
                for (Entry entry : TemplateCatalog.ofStyle(style, Kind.CRAFT_HOUSE)) {
                    if (entry.width() <= 9) {
                        list.add(entry);
                    }
                }
            }
            default -> {
                // Any house of a town, for a row whose own quarter has none that fits.
                list.addAll(TemplateCatalog.ofStyle(style, Kind.POOR_HOUSE));
                list.addAll(TemplateCatalog.ofStyle(style, Kind.CRAFT_HOUSE));
                list.addAll(TemplateCatalog.ofStyle(style, Kind.MERCHANT_HOUSE));
            }
        }
        return list;
    }

    /**
     * The quarter of a house of the outer ring by its side and where it begins in the room its row has for houses: the poor along
     * the north wall behind the castle and in the far halves of the rows running from the east and west avenues to the north
     * corners, the merchants by the market on the south, the craftsmen between.
     *
     * @param offset blocks from the beginning of the room for houses
     * @param room   the length of that room
     */
    private static String quarterAt(Row row, int offset, int room) {
        boolean near = room > 0 && offset * 2 < room;
        return switch (row.side) {
            case NORTH -> POOR;
            case SOUTH -> row.half == 1 || near ? MERCHANT : CRAFT;
            // The half of a side north of its avenue runs towards the north corner.
            default -> row.half == 1 || near ? CRAFT : POOR;
        };
    }

    /**
     * Fills the room a row has left with houses of its quarters, each with its setback, no two alike side by side, until the
     * capital has the homes of its level; what is left stays open ground with trees and lots.
     *
     * @param homes the beds of the capital so far, counted on
     */
    private static void houses(Row row, String style, SplittableRandom random, int[] homes, int wanted) {
        int start = row.next();
        for (int guard = 0; guard < 24 && homes[0] < wanted; guard++) {
            if (!house(row, style, random, homes, start)) {
                break;
            }
        }
    }

    /**
     * The houses of the last ring, put up by turns, a house to a row, so that the homes of the level run out evenly over the
     * sides instead of leaving the last of them empty: the poor rows along the north wall first, up to a third of the homes
     * left, then the other rows, then whatever room is left on every side.
     */
    private static void outerHouses(List<Row> rows, String style, SplittableRandom random, int[] homes, int wanted) {
        int[] starts = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            starts[i] = rows.get(i).next();
        }
        boolean[] full = new boolean[rows.size()];
        int poor = homes[0] + Math.max(0, wanted - homes[0]) / 3;
        byTurns(rows, starts, full, row -> row.side == NORTH, style, random, homes, poor);
        byTurns(rows, starts, full, row -> row.side != NORTH, style, random, homes, wanted);
        byTurns(rows, starts, full, row -> true, style, random, homes, wanted);
    }

    /**
     * Puts up houses on the rows taken by turns, a side after a side ({@link #TURNS}), until none fits or the beds reach
     * {@code limit}.
     *
     * @param full the rows found full, kept
     */
    private static void byTurns(List<Row> rows, int[] starts, boolean[] full, java.util.function.Predicate<Row> taken, String style,
                                SplittableRandom random, int[] homes, int limit) {
        boolean more = true;
        for (int round = 0; more && round < 24; round++) {
            more = false;
            for (int turn = 0; turn < TURNS.length && homes[0] < limit; turn++) {
                int i = TURNS[turn];
                Row row = rows.get(i);
                if (!taken.test(row) || full[i]) {
                    continue;
                }
                full[i] = !house(row, style, random, homes, starts[i]);
                more |= !full[i];
            }
        }
    }

    /**
     * Puts up the next house of a row, of the quarter where it begins and with its setback, not like the building before it.
     *
     * @param start where the room of the row for houses begins
     * @return whether a house fitted
     */
    private static boolean house(Row row, String style, SplittableRandom random, int[] homes, int start) {
        Entry last = row.items.isEmpty() ? null : row.items.get(row.items.size() - 1).entry();
        String quarter = row.ring == 0 ? NOBLE : quarterAt(row, row.next() - start, row.length() - start);
        int setback = random.nextInt(SETBACK + 1);
        List<Entry> fitting = fitting(pool(style, quarter), row, last);
        if (fitting.isEmpty() && row.ring > 0) {
            fitting = fitting(pool(style, ""), row, last);
        }
        if (fitting.isEmpty()) {
            return false;
        }
        Entry house = fitting.get(random.nextInt(fitting.size()));
        row.items.add(new Item(house, Math.min(setback, row.depth - house.depth()), quarter, true));
        homes[0] += house.beds();
        return true;
    }

    private static List<Entry> fitting(List<Entry> pool, Row row, Entry last) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : pool) {
            if (entry != last && row.fits(entry)) {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * What a row puts on the ground besides its buildings.
     */
    private record Extras(List<Lot> lots, List<int[]> trees, List<int[]> gardens) {
    }

    /**
     * Sets the buildings of a row on the ground: the room left cut into random shares between them and after the last one, the
     * first by the avenue. Wide gaps get trees, in rows in a deep ring, buildings with a deep back garden a tree in it or an
     * orchard in rows, houses set back as far as they go a front garden; the room after the last building of the outer ring is a
     * free lot when it is wide enough.
     */
    private static void place(Row row, SplittableRandom random, List<Plot> plots, Extras extras, boolean outer) {
        int n = row.items.size();
        if (n == 0) {
            if (outer) {
                freeLots(row, row.lo, row.hi, extras.lots());
            }
            return;
        }
        int room = row.length() - row.used();
        int[] weights = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            weights[i] = random.nextInt(4);
            total += weights[i];
        }
        // The share of a gap after each building; the last one is the room after the last building.
        int[] shares = new int[n];
        if (total == 0) {
            shares[n - 1] = room;
        } else {
            int given = 0;
            for (int i = 0; i < n; i++) {
                shares[i] = room * weights[i] / total;
                given += shares[i];
            }
            shares[n - 1] += room - given;
        }
        // From the avenue outwards: the half on the low side runs backwards.
        boolean backwards = row.half == 0;
        int at = backwards ? row.hi : row.lo;
        for (int i = 0; i < n; i++) {
            Item item = row.items.get(i);
            Entry entry = item.entry();
            int width = entry.width();
            int lo = backwards ? at - width + 1 : at;
            int hi = lo + width - 1;
            int along = lo + (width - 1) / 2;
            int across = row.acrossMin(entry.depth(), item.setback()) + (entry.depth() - 1) / 2;
            boolean mirror = random.nextBoolean();
            int[] middle = row.point(along, across);
            Plot plot = new Plot(entry.id(), middle[0], middle[1], row.facing, mirror, item.quarter());
            plots.add(plot);
            if (item.house() && item.setback() == SETBACK) {
                extras.gardens().add(garden(row, plot, entry, lo, hi));
            }
            int back = row.depth - item.setback() - entry.depth();
            if (back >= TREE_BACK) {
                // The garden behind a building up to the back of its ring: a tree in its middle, an orchard in rows where it is
                // deep and wide.
                int lines = Math.max(1, back / TREE_STEP);
                int count = Math.max(1, (width + 2) / TREE_STEP);
                for (int r = 0; r < lines; r++) {
                    int behind = item.setback() + entry.depth() + (back * (2 * r + 1)) / (2 * lines);
                    for (int t = 0; t < count; t++) {
                        int spot = lo + (width * (2 * t + 1)) / (2 * count);
                        extras.trees().add(row.point(spot, row.behind(behind)));
                    }
                }
            }
            int gap = i < n - 1 ? GAP + shares[i] : shares[i];
            if (gap >= TREE_GAP) {
                // One tree in the middle of a gap, more along a long one, and further rows of them in a deep ring.
                int count = Math.max(1, (gap + 1) / TREE_STEP);
                for (int t = 0; t < count; t++) {
                    int offset = (gap * (2 * t + 1) + count) / (2 * count);
                    int mid = backwards ? lo - offset : hi + offset;
                    for (int behind = 3; behind <= row.depth - TREE_CLEAR; behind += TREE_STEP) {
                        extras.trees().add(row.point(mid, row.behind(behind)));
                    }
                }
            }
            if (i == n - 1 && outer && gap >= LOT + GAP) {
                if (backwards) {
                    freeLots(row, row.lo, lo - GAP - 1, extras.lots());
                } else {
                    freeLots(row, hi + GAP + 1, row.hi, extras.lots());
                }
            }
            at = backwards ? lo - 1 - gap : hi + 1 + gap;
        }
    }

    /**
     * The front garden of a house set back from its row: the strip between the line of the fronts and its front, with the
     * opening of its fence before its door.
     *
     * @return {minX, minZ, maxX, maxZ, facing, door}, the door as the coordinate along the row
     */
    private static int[] garden(Row row, Plot plot, Entry entry, int lo, int hi) {
        int near = row.front;
        int far = row.behind(SETBACK - 1);
        int door = lo + (hi - lo) / 2;
        List<Spot> doors = entry.points("door");
        if (!doors.isEmpty()) {
            int[] turned = turned(plot, doors.get(0));
            door = row.ns() ? turned[0] : turned[1];
        }
        door = Math.max(lo + 1, Math.min(hi - 1, door));
        int acrossMin = Math.min(near, far);
        int acrossMax = Math.max(near, far);
        return row.ns() ? new int[]{lo, acrossMin, hi, acrossMax, row.facing, door}
                : new int[]{acrossMin, lo, acrossMax, hi, row.facing, door};
    }

    /**
     * Free lots for buildings to come in a stretch of a row, their fronts on its line.
     */
    private static void freeLots(Row row, int lo, int hi, List<Lot> lots) {
        for (int at = lo; at + LOT - 1 <= hi; at += LOT + GAP) {
            int along = at + (LOT - 1) / 2;
            int across = row.acrossMin(LOT, 0) + (LOT - 1) / 2;
            int[] middle = row.point(along, across);
            lots.add(new Lot(middle[0], middle[1], row.facing, LOT, LOT));
        }
    }

    /**
     * A named place of a template on a plot: {x, z, y} from the throne, y in layers above the street.
     */
    private static int[] turned(Plot plot, Spot spot) {
        int[] r = new Placement(plot.facing(), plot.mirror()).apply(spot.x(), spot.y(), spot.z());
        return new int[]{plot.x() + r[0], plot.z() + r[2], r[1]};
    }

    private static List<int[]> points(Plot plot, String name) {
        List<int[]> list = new ArrayList<>();
        for (Spot spot : TemplateCatalog.of(plot.template()).points(name)) {
            list.add(turned(plot, spot));
        }
        return list;
    }

    // ---------------------------------------------------------------- the capital

    /**
     * The composition of a styled capital of a level whose place has room for {@code townsAtMost} towns.
     *
     * @param style  the architectural style of the kingdom
     * @param wanted the rank of its guild house and mage tower; one the square of the level cannot hold is lowered to the highest it
     *               holds
     * @throws IllegalArgumentException for an unknown style
     */
    static NpcComposition make(NpcLevel level, long seed, String style, Rank wanted, NpcRules rules, int townsAtMost) {
        if (!ArchitecturalStyles.known(style)) {
            throw new IllegalArgumentException("No architectural style " + style);
        }
        Rank rank = GuildHouses.fitting(level, wanted);
        SplittableRandom random = new SplittableRandom(seed ^ 0x5354594C4544L);
        Shape shape = shape(level);
        boolean small = level == NpcLevel.SMALL;
        boolean large = level == NpcLevel.LARGE;
        int wall = shape.wall();

        // The decorations of the corners of the square, in an order of the seed; a small capital has three.
        List<String> decorations = new ArrayList<>(DECORATIONS);
        for (int i = decorations.size() - 1; i > 0; i--) {
            Collections.swap(decorations, i, random.nextInt(i + 1));
        }
        if (small) {
            decorations = new ArrayList<>(decorations.subList(0, 3));
        }

        List<Plot> plots = new ArrayList<>();
        // The castle first: the throne stands in it. Then the gates of the wall and the towers in its corners.
        plots.add(new Plot(TemplateCatalog.ofStyle(style, Kind.CASTLE).get(level.ordinal()).id(), 0, 0, 0, false, CASTLE));
        List<Gate> gates = new ArrayList<>();
        Map<Integer, Plot> gatePlots = new LinkedHashMap<>();
        Entry wallGate = TemplateCatalog.of(NS + style + "_wall_gate");
        for (int side : shape.gates()) {
            int[] out = CityPlan.outward(side);
            gates.add(new Gate(out[0] * wall, out[1] * wall, side));
            // The gate stands inside the wall against it, its passage on the avenue.
            int centre = wall - 1 - (wallGate.depth() - 1) / 2;
            Plot plot = new Plot(wallGate.id(), out[0] * centre, out[1] * centre, CityPlan.facingOut(side), false, PUBLIC);
            plots.add(plot);
            gatePlots.put(side, plot);
        }
        Entry tower = TemplateCatalog.of(NS + style + "_camp_tower");
        for (int[] corner : CORNERS) {
            int x = cornerCentre(wall, tower.width(), corner[0] > 0);
            int z = cornerCentre(wall, tower.depth(), corner[1] > 0);
            plots.add(new Plot(tower.id(), x, z, corner[1] < 0 ? 0 : 2, false, PUBLIC));
        }

        Extras extras = new Extras(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        int rings = shape.rings().length;
        int[] beds = {0};
        int homeBeds = HOMES[level.ordinal()];
        for (int ring = 0; ring < rings; ring++) {
            boolean outer = ring == rings - 1;
            List<Row> list = rows(shape, ring);
            for (Row row : list) {
                for (Item item : queue(level, style, rank, row)) {
                    if (row.fits(item.entry())) {
                        row.items.add(item);
                        beds[0] += TemplateCatalog.bunks(item.entry()) ? 0 : item.entry().beds();
                    }
                }
            }
            if (outer) {
                outerHouses(list, style, random, beds, homeBeds);
            } else {
                for (Row row : list) {
                    houses(row, style, random, beds, homeBeds);
                }
            }
            for (Row row : list) {
                place(row, random, plots, extras, outer);
            }
            if (ring == 0) {
                corners(shape, decorations, plots, extras.trees());
            }
        }
        List<int[]> trees = keptTrees(shape, plots, extras);

        // People and work.
        int homes = 0;
        int bunks = 0;
        int markets = 0;
        int taverns = 0;
        int workshops = 0;
        int mages = 0;
        for (Plot plot : plots) {
            Entry entry = TemplateCatalog.of(plot.template());
            if (TemplateCatalog.bunks(entry)) {
                bunks += entry.beds();
            } else {
                homes += entry.beds();
            }
            switch (entry.kind()) {
                case MARKET -> markets++;
                case TAVERN -> taverns++;
                case WORKSHOP -> workshops++;
                case MAGE_TOWER -> mages++;
                default -> {
                }
            }
        }
        int towns = Math.max(0, Math.min(rules.towns(level), townsAtMost));
        int townHomes = 0;
        int townBunks = 0;
        for (int i = 0; i < towns; i++) {
            for (Plot plot : town(level, seed, style, i)) {
                Entry entry = TemplateCatalog.of(plot.template());
                if (TemplateCatalog.bunks(entry)) {
                    townBunks += entry.beds();
                } else {
                    townHomes += entry.beds();
                }
            }
        }
        // As in a capital of districts: the guard and the army sleep in the barracks of the capital, the guards of a town in its
        // barracks, or in its houses where it has none, never more than a third of its beds.
        int inCapital = Math.min(rules.guards(level) + rules.army(level), bunks);
        int ofTowns = towns * rules.townGuards(level);
        int inTownBunks = Math.min(ofTowns, townBunks);
        int inTownHomes = townBunks > 0 ? 0 : Math.min(ofTowns, townHomes / 3);
        int soldiers = inCapital + inTownBunks + inTownHomes;
        int citizens = homes + townHomes + inCapital + inTownBunks;
        int fields = Math.max(1, (int) Math.ceil((homes + bunks) * 1.5 / 18.0));
        Map<String, Integer> work = new LinkedHashMap<>();
        work.put("farmer", 2 * (fields + 2 * towns));
        work.put("merchant", 2 * markets);
        work.put("innkeeper", taverns);
        work.put("crafter", (small ? 1 : 2) * workshops + towns);
        // Every styled capital has a guild house with its registrar, a treasury with its minter and a tower of mages: they are
        // staffed before the work outside the buildings.
        work.put("registrar", 1);
        work.put("minter", 1);
        work.put("mage", 2 * mages);
        work.put("builder", large ? 4 : small ? 1 : 2);
        work.put("lumberjack", large ? 6 : small ? 2 : 4);
        work.put("miner", large ? 6 : small ? 2 : 3);
        work.put("carrier", large ? 6 : small ? 2 : 4);
        Map<String, Integer> jobs = jobs(work, citizens - soldiers);

        List<Post> posts = posts(shape, plots, gatePlots);
        List<int[]> fieldSpots = fieldSpots(shape, fields);
        String road = switch (level) {
            case SMALL -> "path";
            case MEDIUM -> "gravel";
            case LARGE -> "paved";
        };
        return new NpcComposition(NpcSite.LAYOUT_STYLED, level, seed, plots, rings, towns, 0, !small, road, citizens, jobs, soldiers,
                Math.min(fields, fieldSpots.size()), wall, gates, posts, extras.lots(), fieldSpots, bunks + townBunks, style, trees,
                extras.gardens());
    }

    /**
     * The workers of a capital by profession: as many as wanted while there are people, in the order of the need. A capital
     * whose houses hold fewer people than its work wants (the large ones of the styles with roomy houses) gives every
     * profession one worker first, so that its last trades (the miners and the carriers) are not left without anybody; the
     * rest goes in the order of the need. With people enough it is the same as filling the professions one after another.
     *
     * @param wanted the workers wanted by profession, in the order of the need
     * @param people the citizens who are not soldiers
     */
    static Map<String, Integer> jobs(Map<String, Integer> wanted, int people) {
        Map<String, Integer> jobs = new LinkedHashMap<>();
        int left = Math.max(0, people);
        for (Map.Entry<String, Integer> profession : wanted.entrySet()) {
            if (profession.getValue() > 0 && left > 0) {
                jobs.put(profession.getKey(), 1);
                left--;
            }
        }
        for (Map.Entry<String, Integer> profession : wanted.entrySet()) {
            Integer given = jobs.get(profession.getKey());
            if (given == null) {
                continue;
            }
            int more = Math.min(profession.getValue() - given, left);
            if (more > 0) {
                jobs.put(profession.getKey(), given + more);
                left -= more;
            }
        }
        return jobs;
    }

    /**
     * The open corners of the first ring: a decoration in the middle of each, its front towards the square, with trees on its
     * free sides; a corner without one is a grove of five trees.
     */
    private static void corners(Shape shape, List<String> decorations, List<Plot> plots, List<int[]> trees) {
        int a = shape.rings()[0];
        int b = a + shape.depths()[0] - 1;
        int middle = a + (b - a) / 2;
        for (int i = 0; i < CORNERS.length; i++) {
            int sx = CORNERS[i][0];
            int sz = CORNERS[i][1];
            int cx = sx * middle;
            int cz = sz * middle;
            if (i >= decorations.size()) {
                trees.add(new int[]{cx, cz});
                for (int[] diagonal : CORNERS) {
                    trees.add(new int[]{cx + diagonal[0] * 6, cz + diagonal[1] * 6});
                }
                continue;
            }
            Plot plot = new Plot(NS + decorations.get(i), cx, cz, sz < 0 ? 0 : 2, false, PUBLIC);
            plots.add(plot);
            int[] box = NpcComposition.footprint(plot);
            int minX = sx > 0 ? a : -b;
            int maxX = sx > 0 ? b : -a;
            int minZ = sz > 0 ? a : -b;
            int maxZ = sz > 0 ? b : -a;
            int midX = box[0] + (box[2] - box[0]) / 2;
            int midZ = box[1] + (box[3] - box[1]) / 2;
            int west = box[0] - minX;
            int east = maxX - box[2];
            int north = box[1] - minZ;
            int south = maxZ - box[3];
            if (west >= 6) {
                trees.add(new int[]{minX + (west - 1) / 2, midZ});
            }
            if (east >= 6) {
                trees.add(new int[]{box[2] + 1 + (east - 1) / 2, midZ});
            }
            if (north >= 6) {
                trees.add(new int[]{midX, minZ + (north - 1) / 2});
            }
            if (south >= 6) {
                trees.add(new int[]{midX, box[3] + 1 + (south - 1) / 2});
            }
        }
    }

    /**
     * The trees that keep their distance from every building, stand inside the wall on yards or greens and leave the front
     * gardens and free lots alone; each place once.
     */
    private static List<int[]> keptTrees(Shape shape, List<Plot> plots, Extras extras) {
        List<int[]> boxes = new ArrayList<>();
        for (Plot plot : plots) {
            boxes.add(NpcComposition.footprint(plot));
        }
        for (int[] garden : extras.gardens()) {
            boxes.add(new int[]{garden[0], garden[1], garden[2], garden[3]});
        }
        for (Lot lot : extras.lots()) {
            int half = (lot.width() - 1) / 2;
            boxes.add(new int[]{lot.x() - half, lot.z() - half, lot.x() + half, lot.z() + half});
        }
        List<int[]> kept = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] tree : extras.trees()) {
            int x = tree[0];
            int z = tree[1];
            if (Math.max(Math.abs(x), Math.abs(z)) >= shape.wall() - 2) {
                continue;
            }
            int ground = ground(shape, x, z);
            if (ground != CityPlan.YARD && ground != CityPlan.GREEN) {
                continue;
            }
            boolean clear = true;
            for (int[] box : boxes) {
                int dx = Math.max(0, Math.max(box[0] - x, x - box[2]));
                int dz = Math.max(0, Math.max(box[1] - z, z - box[3]));
                if (Math.max(dx, dz) < TREE_CLEAR) {
                    clear = false;
                    break;
                }
            }
            if (clear && seen.add((long) x << 32 ^ (z & 0xFFFFFFFFL))) {
                kept.add(new int[]{x, z});
            }
        }
        return kept;
    }

    /**
     * The middle of a building of {@code size} blocks that stands against the wall in a corner.
     */
    private static int cornerCentre(int wall, int size, boolean positive) {
        return positive ? wall - size + (size - 1) / 2 : -(wall - 1) + (size - 1) / 2;
    }

    // ---------------------------------------------------------------- the ground

    /**
     * What the ground of a styled capital is at a column: one of the grounds of {@link CityPlan}. The square around the castle,
     * the avenues to the gates, a square before every gate in the last ring, greens in the open corners of the first ring, a
     * street before the fronts of the last ring and the way of the guards along the wall; yards where buildings and gardens are.
     */
    static int ground(Shape shape, int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int reach = Math.max(ax, az);
        if (reach >= shape.wall()) {
            return CityPlan.YARD;
        }
        if (reach < shape.rings()[0]) {
            return CityPlan.SQUARE;
        }
        int n = shape.rings().length;
        if (reach > shape.last() + shape.gardens()) {
            return CityPlan.WALK;
        }
        if (reach > shape.last()) {
            // Before the fronts of a last ring that looks outwards a street runs first.
            return !shape.inward()[n - 1] && reach <= shape.last() + 3 ? CityPlan.STREET : CityPlan.YARD;
        }
        if (Math.min(ax, az) <= AVENUE) {
            return CityPlan.AVENUE_GROUND;
        }
        if (reach >= shape.rings()[n - 1]) {
            boolean ns = az >= ax;
            int side = ns ? (z < 0 ? NORTH : SOUTH) : (x < 0 ? WEST : EAST);
            int along = ns ? ax : az;
            if (shape.gate(side) && along <= GATE_CLEAR - 1) {
                return CityPlan.SQUARE;
            }
        }
        for (int ring = 0; ring < n; ring++) {
            int a = shape.rings()[ring];
            int b = a + shape.depths()[ring] - 1;
            if (reach >= a && reach <= b) {
                boolean corner = ax >= a && az >= a;
                return corner && shape.inward()[ring] ? CityPlan.GREEN : CityPlan.YARD;
            }
            if (reach < a) {
                // Between two rings: a street where fronts look at it, yards behind backs.
                boolean before = ring > 0 && !shape.inward()[ring - 1];
                boolean after = shape.inward()[ring];
                return before || after ? CityPlan.STREET : CityPlan.YARD;
            }
        }
        return CityPlan.WALK;
    }

    // ---------------------------------------------------------------- posts, fields

    /**
     * The posts of the guards: the gates, the square, the door and the hall of the castle, the stores and the rounds as in a
     * capital of districts, and the gate, the wall and the treasury of the castle.
     */
    private static List<Post> posts(Shape shape, List<Plot> plots, Map<Integer, Plot> gatePlots) {
        List<Post> list = new ArrayList<>();
        String[] sides = {"north", "east", "south", "west"};
        Plot castle = plots.get(0);
        for (int side : shape.gates()) {
            int[] out = CityPlan.outward(side);
            Plot gate = gatePlots.get(side);
            List<int[]> passage = gate == null ? List.of() : points(gate, "castle_gate");
            int inside = shape.wall() - 7;
            int[] point = passage.isEmpty() ? new int[]{out[0] * inside, out[1] * inside} : passage.get(0);
            list.add(new Post("gate_" + sides[side], List.of(point)));
        }
        int square = (shape.precinct() + shape.rings()[0]) / 2 + 1;
        list.add(new Post("square", List.of(new int[]{0, square})));
        List<int[]> door = points(castle, "palace_door");
        list.add(new Post("palace_door", door.isEmpty() ? List.of(new int[]{0, shape.precinct() + 1}) : door));
        List<int[]> hall = points(castle, "palace_hall");
        list.add(new Post("palace_hall", hall.isEmpty() ? List.of(new int[]{-2, 3}, new int[]{2, 3}) : hall));
        for (Plot plot : plots) {
            Entry entry = TemplateCatalog.of(plot.template());
            if (entry.kind() == Kind.STORAGE_YARD || entry.kind() == Kind.STOREHOUSE) {
                int[] box = NpcComposition.footprint(plot);
                // Inside the yard by its gate; before the door of a storehouse.
                int[] front = front(plot, box, entry.kind() == Kind.STORAGE_YARD ? -2 : 1);
                list.add(new Post("stores", List.of(front)));
                list.add(new Post("stores_round", List.of(new int[]{box[0] - 1, box[1] - 1}, new int[]{box[2] + 1, box[1] - 1},
                        new int[]{box[2] + 1, box[3] + 1}, new int[]{box[0] - 1, box[3] + 1})));
                break;
            }
        }
        int walk = shape.wall() - 2;
        list.add(new Post("wall_round", List.of(new int[]{-walk, -walk}, new int[]{0, -walk}, new int[]{walk, -walk}, new int[]{walk, 0},
                new int[]{walk, walk}, new int[]{0, walk}, new int[]{-walk, walk}, new int[]{-walk, 0})));
        List<int[]> castleGate = points(castle, "castle_gate");
        if (!castleGate.isEmpty()) {
            list.add(new Post("castle_gate", castleGate));
        }
        List<int[]> castleWall = new ArrayList<>(points(castle, "castle_wall"));
        if (!castleWall.isEmpty()) {
            // A round on the wall of the castle: its points by their angle around the throne.
            castleWall.sort(Comparator.comparingDouble((int[] p) -> StrictMath.atan2(p[1], p[0]))
                    .thenComparingInt(p -> p[0] * p[0] + p[1] * p[1]).thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
            list.add(new Post("castle_wall", castleWall));
        }
        List<int[]> treasury = points(castle, "treasury");
        if (!treasury.isEmpty()) {
            list.add(new Post("treasury", treasury));
        }
        return list;
    }

    /**
     * A point on the middle line of the front of a building: {@code out} blocks before it, or behind its front if negative.
     */
    private static int[] front(Plot plot, int[] box, int out) {
        int midX = box[0] + (box[2] - box[0]) / 2;
        int midZ = box[1] + (box[3] - box[1]) / 2;
        return switch (plot.facing()) {
            case 0 -> new int[]{midX, box[3] + out};
            case 1 -> new int[]{box[0] - out, midZ};
            case 2 -> new int[]{midX, box[1] - out};
            default -> new int[]{box[2] + out, midZ};
        };
    }

    /**
     * Places for fields: inside the wall where the last ring has no rows, then outside the wall along its sides, away from the
     * gates, as many as wanted and a few to spare.
     */
    private static List<int[]> fieldSpots(Shape shape, int fields) {
        List<int[]> list = new ArrayList<>();
        int n = shape.rings().length;
        if (shape.fields()) {
            int across = shape.rings()[n - 1] + shape.depths()[n - 1] / 2;
            for (int along : new int[]{9, 21}) {
                for (int sign : new int[]{-1, 1}) {
                    list.add(new int[]{across, sign * along});
                    list.add(new int[]{-across, sign * along});
                }
            }
        }
        int out = shape.wall() + 12;
        int step = 15;
        for (int offset = step; offset <= shape.wall() && list.size() < fields + 4; offset += step) {
            for (int sign : new int[]{-1, 1}) {
                list.add(new int[]{sign * offset, out});
                list.add(new int[]{out, sign * offset});
                list.add(new int[]{sign * offset, -out});
                list.add(new int[]{-out, sign * offset});
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- towns

    /**
     * The buildings of a town of a styled kingdom around its town hall, as in a kingdom of districts: two rows along its street
     * with their fronts on it. Houses of the style no larger than eleven by eleven, a workshop, a well and a watch tower of the
     * style; the town of a large kingdom has an inn and a barracks too.
     *
     * @param index the number of the town, from 0
     */
    static List<Plot> town(NpcLevel level, long seed, String style, int index) {
        SplittableRandom random = new SplittableRandom(seed ^ (0x53544F57L + index * 0x9E3779B97F4A7C15L));
        boolean large = level == NpcLevel.LARGE;
        List<Entry> houses = new ArrayList<>();
        for (Entry entry : pool(style, "")) {
            if (entry.width() <= CityPlan.DEPTH && entry.depth() <= CityPlan.DEPTH) {
                houses.add(entry);
            }
        }
        List<Entry> north = new ArrayList<>();
        List<Entry> south = new ArrayList<>();
        Entry[] last = new Entry[1];
        north.add(townHouse(houses, random, last));
        north.add(townHouse(houses, random, last));
        Entry smithy = TemplateCatalog.of(NS + style + "_smithy");
        List<Entry> workshops = List.of(TemplateCatalog.of(NS + "workshop"), TemplateCatalog.of(NS + "smithy"),
                TemplateCatalog.of(NS + "carpentry"));
        int plain = Math.floorMod(index + random.nextInt(workshops.size()), workshops.size());
        north.add(smithy.depth() <= CityPlan.DEPTH ? smithy : workshops.get(plain));
        if (large) {
            north.add(TemplateCatalog.of(NS + "tavern"));
            north.add(townHouse(houses, random, last));
        }
        south.add(townHouse(houses, random, last));
        south.add(TemplateCatalog.of(NS + "well"));
        south.add(townHouse(houses, random, last));
        south.add(TemplateCatalog.of(NS + style + "_camp_tower"));
        if (large) {
            south.add(0, TemplateCatalog.of(NS + "barracks"));
        }
        List<Plot> plots = new ArrayList<>();
        townRow(north, -CityPlan.TOWN_SQUARE - 2, 0, random, plots);
        townRow(south, CityPlan.TOWN_SQUARE + 2, 2, random, plots);
        return plots;
    }

    private static Entry townHouse(List<Entry> houses, SplittableRandom random, Entry[] last) {
        List<Entry> choices = new ArrayList<>(houses);
        if (choices.size() > 1) {
            choices.remove(last[0]);
        }
        last[0] = choices.get(random.nextInt(choices.size()));
        return last[0];
    }

    /**
     * A row of a town along x, centred on the town hall, its fronts on a line; what does not fit is left out from its end.
     */
    private static void townRow(List<Entry> entries, int front, int facing, SplittableRandom random, List<Plot> plots) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.width();
        }
        int room = 2 * CityPlan.TOWN_ROW + 1;
        while (total + (entries.size() - 1) * GAP > room && entries.size() > 1) {
            total -= entries.remove(entries.size() - 1).width();
        }
        int free = room - total - (entries.size() - 1) * GAP;
        int gaps = entries.size() - 1;
        int share = gaps > 0 ? Math.min(3, free / gaps) : 0;
        int length = total + gaps * (GAP + share);
        int at = -length / 2;
        for (Entry entry : entries) {
            int x = at + (entry.width() - 1) / 2;
            int min = facing == 0 ? front - entry.depth() + 1 : front;
            plots.add(new Plot(entry.id(), x, min + (entry.depth() - 1) / 2, facing, random.nextBoolean(), townQuarter(entry)));
            at += entry.width() + GAP + share;
        }
    }

    private static String townQuarter(Entry entry) {
        return switch (entry.kind()) {
            case POOR_HOUSE -> POOR;
            case CRAFT_HOUSE -> CRAFT;
            case MERCHANT_HOUSE -> MERCHANT;
            default -> PUBLIC;
        };
    }

    // ---------------------------------------------------------------- work

    /**
     * A post of work outside the buildings of a styled capital.
     *
     * @param what  the profession
     * @param x     in blocks east of the throne
     * @param z     in blocks south of the throne
     * @param seats the workers the post takes
     */
    public record WorkPlace(String what, int x, int z, int seats) {
    }

    /**
     * The posts of work of a styled capital outside its buildings: the builders and carriers by the gates of a medium or large
     * capital, in the squares before the gates, so that the builders reach the land outside the wall. The yard of each
     * profession counts as its first post; a post may get no seat. None for a capital that is not styled.
     */
    public static List<WorkPlace> workPlaces(NpcComposition composition) {
        if (!composition.styled() || composition.level() == NpcLevel.SMALL) {
            return List.of();
        }
        int by = composition.wallRadius() - 14;
        List<int[]> builders = new ArrayList<>();
        builders.add(new int[]{5, by});
        if (composition.level() == NpcLevel.LARGE) {
            builders.add(new int[]{5, -by});
            builders.add(new int[]{by, 5});
            builders.add(new int[]{-by, 5});
        }
        List<int[]> carriers = List.of(new int[]{-5, by});
        int builderJobs = composition.jobs().getOrDefault("builder", 0);
        int carrierJobs = composition.jobs().getOrDefault("carrier", 0);
        List<WorkPlace> list = new ArrayList<>();
        list.add(new WorkPlace("builder", builders.get(0)[0], builders.get(0)[1],
                NpcComposition.share(builderJobs, builders.size() + 1, 1)));
        list.add(new WorkPlace("carrier", carriers.get(0)[0], carriers.get(0)[1],
                NpcComposition.share(carrierJobs, carriers.size() + 1, 1)));
        for (int i = 1; i < builders.size(); i++) {
            list.add(new WorkPlace("builder", builders.get(i)[0], builders.get(i)[1],
                    NpcComposition.share(builderJobs, builders.size() + 1, i + 1)));
        }
        return list;
    }

    /**
     * The seats of a profession in the buildings of a styled capital: the yards of the builders and of the carriers take the first
     * share of theirs, the guild house its registrar, the treasury its minter. 0 for others and for a capital that is not styled.
     */
    public static int seatsInBuildings(NpcComposition composition, String profession) {
        if (!composition.styled()) {
            return 0;
        }
        int jobs = composition.jobs().getOrDefault(profession, 0);
        return switch (profession) {
            case "builder", "carrier" -> {
                int outer = 0;
                for (WorkPlace place : workPlaces(composition)) {
                    if (place.what().equals(profession)) {
                        outer++;
                    }
                }
                yield NpcComposition.share(jobs, outer + 1, 0);
            }
            case "registrar", "minter" -> jobs;
            default -> 0;
        };
    }

    /**
     * Where the bank of a styled capital stands in its treasury: {x, z, y} from the throne, y in layers above the street.
     */
    public static Optional<int[]> bank(NpcComposition composition) {
        for (Plot plot : composition.buildings()) {
            Optional<Entry> entry = TemplateCatalog.get(plot.template());
            if (entry.isPresent() && entry.get().kind() == Kind.TREASURY) {
                List<int[]> points = points(plot, "bank");
                return points.isEmpty() ? Optional.empty() : Optional.of(points.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * The profession of the post of work in a building of a template: {@code registrar}, {@code minter}, {@code builder},
     * {@code carrier}, or empty for a building without one.
     */
    public static String profession(TemplateCatalog.Entry entry) {
        return switch (entry.kind()) {
            case GUILD -> "registrar";
            case TREASURY -> "minter";
            case YARD -> entry.id().endsWith("_builders_yard") ? "builder" : entry.id().endsWith("_carters_yard") ? "carrier" : "";
            default -> "";
        };
    }
}
