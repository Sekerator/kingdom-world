package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Gate;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Lot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The plan of a capital laid out in districts, the same for the same seed. A palace stands around the throne; two avenues run
 * from the square around it to the gates in the middle of the sides of the wall; between them rows of buildings follow square
 * streets around the palace, each row with its fronts on its street, the buildings of a row as wide as they are and set
 * apart by what room is left. The south side is the market, the east the craftsmen with their stores, the north the garrison
 * next to the palace, the west houses; a path runs along the wall inside it.
 *
 * <p>All positions are in blocks from the throne, x to the east and z to the south.
 */
final class CityPlan {
    /** How deep a row of buildings is. */
    static final int DEPTH = 11;
    /** Blocks an avenue takes on each side of its middle. */
    static final int AVENUE = 2;
    /** Blocks between two buildings of a row at least. */
    private static final int GAP = 2;
    /** Half the side of the square of a town, in blocks. */
    static final int TOWN_SQUARE = 4;
    /** How far the rows of a town reach along its street, in blocks from the town hall. */
    static final int TOWN_ROW = 27;
    /** The radius of the palisade of a town. */
    static final int TOWN_WALL = 31;
    /** How far the middle of the fields of a town lies from its street. */
    static final int TOWN_FIELD = 23;

    static final int NORTH = 0;
    static final int EAST = 1;
    static final int SOUTH = 2;
    static final int WEST = 3;

    /**
     * The measures of a capital of a level.
     *
     * @param precinct half the side of the ground of the palace
     * @param rings    where each row of buildings begins, from the throne; the square around the palace lies before the first
     * @param inward   whether the fronts of that row look towards the throne
     * @param gardens  blocks of gardens between the last row and the way along the wall
     * @param walk     blocks kept free along the wall inside it: the way of the guards, wide enough for the gatehouses
     * @param gates    the sides that have a gate
     * @param fields   whether the last row has houses only on its north and south sides and fields on the others
     */
    record Shape(int precinct, int[] rings, boolean[] inward, int gardens, int walk, int[] gates, boolean fields, String palace) {
        /**
         * The outermost line of the last row.
         */
        int last() {
            return rings[rings.length - 1] + DEPTH - 1;
        }

        /**
         * The radius of the wall.
         */
        int wall() {
            return last() + gardens + walk + 1;
        }
    }

    static Shape shape(NpcLevel level) {
        return switch (level) {
            case SMALL -> new Shape(11, new int[]{16, 29}, new boolean[]{true, false}, 0, 4, new int[]{SOUTH, NORTH}, true,
                    "kingdomworld:great_hall");
            case MEDIUM -> new Shape(11, new int[]{16, 31}, new boolean[]{true, false}, 6, 6, new int[]{SOUTH, NORTH, EAST, WEST}, false,
                    "kingdomworld:great_hall");
            case LARGE -> new Shape(17, new int[]{23, 42, 58}, new boolean[]{true, false, true}, 7, 6,
                    new int[]{SOUTH, NORTH, EAST, WEST}, false, "kingdomworld:castle");
        };
    }

    /**
     * A stretch of a row between an avenue and a corner.
     */
    private static final class Segment {
        final int ring;
        final int side;
        /** 0 for the half on the low coordinates (west of or north of the avenue), 1 for the other. */
        final int half;
        final int lo;
        final int hi;
        /** The line of the fronts, across the row. */
        final int front;
        final int facing;
        /** Buildings from the avenue outwards. */
        final List<Entry> near = new ArrayList<>();
        /** Buildings at the far end, the last one in the corner. */
        final List<Entry> far = new ArrayList<>();

        Segment(int ring, int side, int half, int lo, int hi, int front, int facing) {
            this.ring = ring;
            this.side = side;
            this.half = half;
            this.lo = lo;
            this.hi = hi;
            this.front = front;
            this.facing = facing;
        }

        int length() {
            return hi - lo + 1;
        }

        int used() {
            int sum = 0;
            int count = 0;
            for (Entry entry : near) {
                sum += entry.width();
                count++;
            }
            for (Entry entry : far) {
                sum += entry.width();
                count++;
            }
            return sum + Math.max(0, count - 1) * GAP;
        }

        boolean fits(Entry entry) {
            int count = near.size() + far.size();
            return entry.depth() <= DEPTH && used() + (count > 0 ? GAP : 0) + entry.width() <= length();
        }
    }

    private CityPlan() {
    }

    // ---------------------------------------------------------------- the capital

    static NpcComposition make(NpcLevel level, long seed, NpcRules rules, int townsAtMost) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x4449535452L);
        Shape shape = shape(level);
        List<Segment> segments = segments(shape);
        boolean large = level == NpcLevel.LARGE;
        boolean small = level == NpcLevel.SMALL;
        int outer = shape.rings().length - 1;

        // An inn and a barracks by the gates.
        segment(segments, outer, SOUTH, 1).near.add(TemplateCatalog.of(small ? "kingdomworld:tavern" : "kingdomworld:inn"));
        segment(segments, outer, SOUTH, 0).near.add(TemplateCatalog.of(small ? "kingdomworld:barracks" : "kingdomworld:barracks_hall"));
        if (!small) {
            segment(segments, outer, NORTH, 0).near.add(TemplateCatalog.of("kingdomworld:barracks"));
            segment(segments, outer, EAST, 1).near.add(TemplateCatalog.of(large ? "kingdomworld:storage_yard" : "kingdomworld:barn"));
            if (large) {
                segment(segments, outer, WEST, 1).near.add(TemplateCatalog.of("kingdomworld:tavern"));
            }
        }

        // What each side is for, from the palace outwards.
        Map<Integer, List<String>> wanted = new LinkedHashMap<>();
        wanted.put(SOUTH, names(small ? List.of("market_square", "well", "bakery")
                : large ? List.of("market_square", "fountain", "market_hall", "well", "market_square", "bakery", "weavery", "well")
                : List.of("market_square", "fountain", "market_hall", "bakery", "well")));
        wanted.put(NORTH, names(small ? List.of("barracks_hall")
                : large ? List.of("barracks_hall", "drill_yard", "barracks_hall", "mage_tower", "well")
                : List.of("barracks_hall", "drill_yard")));
        // A small capital keeps its stores in a barn: two storehouses hold what one would run over with after a harvest.
        wanted.put(EAST, names(small ? List.of("smithy", "barn", "workshop")
                : large ? List.of("smithy", "carpentry", "storehouse", "workshop", "bakery", "smithy", "barn", "weavery", "carpentry", "well")
                : List.of("smithy", "carpentry", "storehouse", "workshop")));
        wanted.put(WEST, names(small ? List.of() : large ? List.of("barracks_hall", "well", "fountain") : List.of("well")));

        for (Segment segment : segments) {
            List<String> queue = wanted.get(segment.side);
            for (int i = 0; i < queue.size(); ) {
                Entry entry = TemplateCatalog.of(queue.get(i));
                if (segment.fits(entry)) {
                    segment.near.add(entry);
                    queue.remove(i);
                } else {
                    i++;
                }
            }
        }

        // Houses in what room is left: large ones near the palace, small ones by the wall, no two alike side by side.
        List<Entry> largeHouses = TemplateCatalog.variants(Kind.LARGE_HOUSE);
        List<Entry> smallHouses = TemplateCatalog.variants(Kind.SMALL_HOUSE);
        for (Segment segment : segments) {
            double rich = shape.rings().length <= 1 ? 0.5 : 1.0 - (double) segment.ring / (shape.rings().length - 1);
            Entry last = null;
            for (int guard = 0; guard < 16; guard++) {
                List<Entry> pool = random.nextDouble() < 0.2 + 0.6 * rich ? largeHouses : smallHouses;
                List<Entry> fitting = new ArrayList<>();
                for (Entry entry : pool) {
                    if (segment.fits(entry) && entry != last) {
                        fitting.add(entry);
                    }
                }
                if (fitting.isEmpty()) {
                    for (Entry entry : smallHouses) {
                        if (segment.fits(entry) && entry != last) {
                            fitting.add(entry);
                        }
                    }
                }
                if (fitting.isEmpty()) {
                    break;
                }
                last = fitting.get(random.nextInt(fitting.size()));
                segment.near.add(last);
            }
        }

        List<Plot> plots = new ArrayList<>();
        // The palace first: the throne stands in it. Then the gatehouses, then the rows from the palace outwards.
        plots.add(new Plot(shape.palace(), 0, 0, 0, false, "palace"));
        List<Gate> gates = new ArrayList<>();
        Entry gatehouse = TemplateCatalog.of("kingdomworld:gatehouse");
        for (int side : shape.gates()) {
            int[] out = outward(side);
            gates.add(new Gate(out[0] * shape.wall(), out[1] * shape.wall(), side));
            if (!small) {
                // The gatehouse stands inside the wall against it, its passage on the avenue.
                int centre = shape.wall() - 1 - (gatehouse.depth() - 1) / 2;
                plots.add(new Plot(gatehouse.id(), out[0] * centre, out[1] * centre, facingOut(side), false, "gate"));
            }
        }
        // Towers in the corners of the wall, their doors towards the town: two for a small capital, four for the others.
        Entry tower = TemplateCatalog.of(large ? "kingdomworld:corner_tower" : small ? "kingdomworld:wooden_watchtower" : "kingdomworld:watchtower");
        int[][] corners = small ? new int[][]{{1, -1}, {-1, 1}} : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] corner : corners) {
            int x = cornerCentre(shape.wall(), tower.width(), corner[0] > 0);
            int z = cornerCentre(shape.wall(), tower.depth(), corner[1] > 0);
            plots.add(new Plot(tower.id(), x, z, corner[1] < 0 ? 0 : 2, false, "wall"));
        }
        List<Lot> lots = new ArrayList<>();
        for (Segment segment : segments) {
            place(segment, random, plots, lots);
        }

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
            for (Plot plot : town(level, seed, i)) {
                Entry entry = TemplateCatalog.of(plot.template());
                if (TemplateCatalog.bunks(entry)) {
                    townBunks += entry.beds();
                } else {
                    townHomes += entry.beds();
                }
            }
        }
        int soldiers = Math.min(rules.soldiers(level), bunks + townBunks);
        int citizens = homes + townHomes + soldiers;
        // A field of carrots feeds about twenty, one of wheat half as many through the baker: every third field is wheat. The
        // fields are counted for full barracks: newcomers take the free bunks, and a garrison eats as the citizens do.
        int fields = Math.max(1, (int) Math.ceil((homes + bunks) * 1.5 / 18.0));
        Map<String, Integer> jobs = new LinkedHashMap<>();
        int left = citizens - soldiers;
        left = job(jobs, "farmer", 2 * (fields + 2 * towns), left);
        left = job(jobs, "merchant", 2 * markets, left);
        left = job(jobs, "innkeeper", taverns, left);
        left = job(jobs, "crafter", 2 * workshops + towns, left);
        left = job(jobs, "builder", large ? 4 : small ? 1 : 2, left);
        left = job(jobs, "carrier", large ? 6 : small ? 2 : 4, left);
        left = job(jobs, "lumberjack", large ? 6 : small ? 2 : 4, left);
        left = job(jobs, "miner", large ? 6 : small ? 2 : 3, left);
        left = job(jobs, "registrar", 1, left);
        if (!small) {
            left = job(jobs, "minter", 1, left);
        }
        if (mages > 0) {
            job(jobs, "mage", 2 * mages, left);
        }

        List<Post> posts = posts(shape, plots);
        List<int[]> fieldSpots = fieldSpots(shape, fields);
        String road = switch (level) {
            case SMALL -> "path";
            case MEDIUM -> "gravel";
            case LARGE -> "paved";
        };
        return new NpcComposition(NpcSite.LAYOUT_DISTRICTS, level, seed, plots, shape.rings().length, towns, 0, !small, road, citizens, jobs,
                soldiers, Math.min(fields, fieldSpots.size()), shape.wall(), gates, posts, lots, fieldSpots, bunks + townBunks);
    }

    private static int job(Map<String, Integer> jobs, String profession, int wanted, int left) {
        int count = Math.max(0, Math.min(wanted, left));
        if (count > 0) {
            jobs.merge(profession, count, Integer::sum);
        }
        return left - count;
    }

    private static List<String> names(List<String> names) {
        List<String> list = new ArrayList<>();
        for (String name : names) {
            list.add("kingdomworld:" + name);
        }
        return list;
    }

    /**
     * The rows of a capital as stretches between the avenues and the corners, from the palace outwards. A row whose fronts look
     * outwards runs round its corners on its north and south sides; one whose fronts look inwards leaves its corners open.
     */
    private static List<Segment> segments(Shape shape) {
        List<Segment> list = new ArrayList<>();
        for (int ring = 0; ring < shape.rings().length; ring++) {
            int a = shape.rings()[ring];
            int b = a + DEPTH - 1;
            boolean in = shape.inward()[ring];
            boolean last = ring == shape.rings().length - 1;
            // A last row that reaches the way along the wall leaves its corners to the towers.
            int span = in ? a - 1 : last && shape.gardens() < 3 ? b - 4 : b;
            int side = a - 1;
            // North and south rows run along x, east and west rows along z.
            list.add(new Segment(ring, NORTH, 0, -span, -AVENUE - 1, in ? -a : -b, in ? 0 : 2));
            list.add(new Segment(ring, NORTH, 1, AVENUE + 1, span, in ? -a : -b, in ? 0 : 2));
            if (!last || !shape.fields()) {
                list.add(new Segment(ring, EAST, 0, -side, -AVENUE - 1, in ? a : b, in ? 1 : 3));
                list.add(new Segment(ring, EAST, 1, AVENUE + 1, side, in ? a : b, in ? 1 : 3));
            }
            list.add(new Segment(ring, SOUTH, 0, -span, -AVENUE - 1, in ? a : b, in ? 2 : 0));
            list.add(new Segment(ring, SOUTH, 1, AVENUE + 1, span, in ? a : b, in ? 2 : 0));
            if (!last || !shape.fields()) {
                list.add(new Segment(ring, WEST, 0, -side, -AVENUE - 1, in ? -a : -b, in ? 3 : 1));
                list.add(new Segment(ring, WEST, 1, AVENUE + 1, side, in ? -a : -b, in ? 3 : 1));
            }
        }
        return list;
    }

    private static Segment segment(List<Segment> segments, int ring, int side, int half) {
        for (Segment segment : segments) {
            if (segment.ring == ring && segment.side == side && segment.half == half) {
                return segment;
            }
        }
        throw new IllegalStateException("No row " + ring + " on side " + side);
    }

    /**
     * Sets the buildings of a stretch on the ground: the first by the avenue, the last in the corner, the room left shared
     * between them; a stretch with room for another building keeps a free lot for the ruler.
     */
    private static void place(Segment segment, SplittableRandom random, List<Plot> plots, List<Lot> lots) {
        List<Entry> order = new ArrayList<>(segment.near);
        for (int i = segment.far.size() - 1; i >= 0; i--) {
            order.add(segment.far.get(i));
        }
        int free = segment.length() - segment.used();
        if (order.isEmpty()) {
            freeLots(segment, segment.lo, segment.hi, lots);
            return;
        }
        // The room left goes between the buildings; what does not divide stays at the far end, before the corner building.
        int gaps = order.size() - 1;
        int share = gaps > 0 ? free / gaps : 0;
        int rest = gaps > 0 ? free - share * gaps : free;
        // From the avenue outwards: the half on the low side runs backwards.
        boolean backwards = segment.half == 0;
        int at = backwards ? segment.hi : segment.lo;
        for (int i = 0; i < order.size(); i++) {
            Entry entry = order.get(i);
            int width = entry.width();
            int lo = backwards ? at - width + 1 : at;
            int along = lo + (width - 1) / 2;
            int across = across(segment, entry);
            boolean mirror = random.nextBoolean();
            boolean ns = segment.side == NORTH || segment.side == SOUTH;
            plots.add(new Plot(entry.id(), ns ? along : across, ns ? across : along, segment.facing, mirror, quarter(segment.side)));
            int step = width + GAP + (i < gaps ? share : 0) + (i == gaps - 1 ? rest : 0);
            at = backwards ? at - step : at + step;
        }
        if (gaps == 0 && rest >= 9 + GAP) {
            // A stretch with one building and room beside it.
            int width = order.get(0).width();
            if (backwards) {
                freeLots(segment, segment.lo, segment.hi - width - GAP, lots);
            } else {
                freeLots(segment, segment.lo + width + GAP, segment.hi, lots);
            }
        }
    }

    /**
     * Free lots of nine blocks in a stretch of a row.
     */
    private static void freeLots(Segment segment, int lo, int hi, List<Lot> lots) {
        int width = 11;
        for (int at = lo; at + width - 1 <= hi; at += width + GAP) {
            int along = at + (width - 1) / 2;
            int across = acrossCentre(segment, DEPTH);
            boolean ns = segment.side == NORTH || segment.side == SOUTH;
            lots.add(new Lot(ns ? along : across, ns ? across : along, segment.facing, width, DEPTH));
        }
    }

    /**
     * The middle of a building across its row, its front on the line of the fronts.
     */
    private static int across(Segment segment, Entry entry) {
        return acrossCentre(segment, entry.depth());
    }

    private static int acrossCentre(Segment segment, int depth) {
        // Fronts looking south or east have the line of the fronts as their highest coordinate.
        boolean high = segment.facing == 0 || segment.facing == 3;
        int min = high ? segment.front - depth + 1 : segment.front;
        return min + (depth - 1) / 2;
    }

    /**
     * The middle of a building of {@code size} blocks that stands against the wall in a corner.
     */
    private static int cornerCentre(int wall, int size, boolean positive) {
        // The ground a building takes runs from its middle less half its size (rounded down) for its whole size.
        return positive ? wall - size + (size - 1) / 2 : -(wall - 1) + (size - 1) / 2;
    }

    private static String quarter(int side) {
        return switch (side) {
            case NORTH -> "garrison";
            case EAST -> "craft";
            case SOUTH -> "market";
            default -> "homes";
        };
    }

    /**
     * The direction out of the capital on a side: {x, z}.
     */
    static int[] outward(int side) {
        return switch (side) {
            case NORTH -> new int[]{0, -1};
            case EAST -> new int[]{1, 0};
            case SOUTH -> new int[]{0, 1};
            default -> new int[]{-1, 0};
        };
    }

    /**
     * Where the front of a building looks that faces out of the capital on a side.
     */
    static int facingOut(int side) {
        return switch (side) {
            case NORTH -> 2;
            case EAST -> 3;
            case SOUTH -> 0;
            default -> 1;
        };
    }

    // ---------------------------------------------------------------- the ground

    /** Grass or the soil of the place. */
    static final int YARD = 0;
    /** A street between rows. */
    static final int STREET = 1;
    /** An avenue. */
    static final int AVENUE_GROUND = 2;
    /** The square around the palace. */
    static final int SQUARE = 3;
    /** The way along the wall. */
    static final int WALK = 4;
    /** Open corners of the rows: greens with a tree. */
    static final int GREEN = 5;

    /**
     * What the ground of a capital is at a column: a street, an avenue, the square of the palace, the way along the wall, a
     * green or a yard. Buildings stand on yards; their templates bring their own floors.
     */
    static int ground(Shape shape, int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int reach = Math.max(ax, az);
        if (reach >= shape.wall()) {
            return YARD;
        }
        if (reach < shape.rings()[0]) {
            return SQUARE;
        }
        if (reach > shape.last() + shape.gardens()) {
            return WALK;
        }
        if (reach > shape.last()) {
            // Gardens behind the last row; where its fronts look outwards a street runs before them first.
            boolean outwards = !shape.inward()[shape.rings().length - 1];
            return outwards && reach <= shape.last() + 3 ? STREET : YARD;
        }
        if (Math.min(ax, az) <= AVENUE) {
            return AVENUE_GROUND;
        }
        for (int ring = 0; ring < shape.rings().length; ring++) {
            int a = shape.rings()[ring];
            int b = a + DEPTH - 1;
            if (reach >= a && reach <= b) {
                // A row; its corners are greens where its fronts look inwards.
                boolean corner = ax >= a && az >= a;
                return corner && shape.inward()[ring] ? GREEN : YARD;
            }
            if (reach < a) {
                // Between two rows: a street where fronts look at it, yards behind backs.
                boolean before = ring > 0 && !shape.inward()[ring - 1];
                boolean after = shape.inward()[ring];
                return before || after ? STREET : YARD;
            }
        }
        return WALK;
    }

    // ---------------------------------------------------------------- posts, fields

    /**
     * The posts of the guards: the gates, the square, the door and the hall of the palace, the stores and the rounds.
     */
    private static List<Post> posts(Shape shape, List<Plot> plots) {
        List<Post> list = new ArrayList<>();
        String[] sides = {"north", "east", "south", "west"};
        for (int side : shape.gates()) {
            int[] out = outward(side);
            int inside = shape.wall() - 7;
            list.add(new Post("gate_" + sides[side], List.of(new int[]{out[0] * inside, out[1] * inside})));
        }
        int square = (shape.precinct() + shape.rings()[0]) / 2 + 1;
        list.add(new Post("square", List.of(new int[]{0, square})));
        list.add(new Post("palace_door", List.of(new int[]{0, shape.precinct() + 1})));
        list.add(new Post("palace_hall", List.of(new int[]{-2, 3}, new int[]{2, 3})));
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
        return list;
    }

    /**
     * A point on the middle line of the front of a building: {@code out} blocks before it, or behind its front if negative.
     */
    private static int[] front(Plot plot, int[] box, int out) {
        int midX = (box[0] + box[2]) / 2;
        int midZ = (box[1] + box[3]) / 2;
        return switch (plot.facing()) {
            case 0 -> new int[]{midX, box[3] + out};
            case 1 -> new int[]{box[0] - out, midZ};
            case 2 -> new int[]{midX, box[1] - out};
            default -> new int[]{box[2] + out, midZ};
        };
    }

    /**
     * Places for fields outside the wall, as many as wanted and a few to spare: along the sides, away from the gates.
     */
    private static List<int[]> fieldSpots(Shape shape, int fields) {
        List<int[]> list = new ArrayList<>();
        if (shape.fields()) {
            // Fields inside the wall where the last row has no houses: two on each half of its east and west sides.
            int across = shape.rings()[shape.rings().length - 1] + DEPTH / 2;
            for (int along : new int[]{9, 21}) {
                for (int sign : new int[]{-1, 1}) {
                    list.add(new int[]{across, sign * along});
                    list.add(new int[]{-across, sign * along});
                }
            }
        }
        int out = shape.wall() + 12;
        int step = 15;
        // By turns on the four sides, from the middle of each side outwards, leaving the roads out of the gates free.
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

    /**
     * How far the fields and the posts of the woodcutters and miners reach from the throne.
     */
    static int outerReach(Shape shape) {
        return shape.wall() + 12 + 6;
    }

    // ---------------------------------------------------------------- towns

    /**
     * The buildings of a town around its town hall: two rows along its street with their fronts on it, houses, a workshop, a
     * well and a watch post; the towns of a large kingdom have a barracks too.
     */
    static List<Plot> town(NpcLevel level, long seed, int index) {
        SplittableRandom random = new SplittableRandom(seed ^ (0x544F574EL + index * 0x9E3779B97F4A7C15L));
        boolean large = level == NpcLevel.LARGE;
        List<Entry> north = new ArrayList<>();
        List<Entry> south = new ArrayList<>();
        List<Entry> smallHouses = TemplateCatalog.variants(Kind.SMALL_HOUSE);
        List<Entry> largeHouses = TemplateCatalog.variants(Kind.LARGE_HOUSE);
        List<Entry> workshops = List.of(TemplateCatalog.of("kingdomworld:workshop"), TemplateCatalog.of("kingdomworld:smithy"),
                TemplateCatalog.of("kingdomworld:carpentry"));
        north.add(largeHouses.get(random.nextInt(largeHouses.size())));
        north.add(smallHouses.get(random.nextInt(smallHouses.size())));
        north.add(workshops.get(Math.floorMod(index + random.nextInt(workshops.size()), workshops.size())));
        if (large) {
            north.add(smallHouses.get(random.nextInt(smallHouses.size())));
        }
        south.add(smallHouses.get(random.nextInt(smallHouses.size())));
        south.add(TemplateCatalog.of("kingdomworld:well"));
        south.add(smallHouses.get(random.nextInt(smallHouses.size())));
        south.add(TemplateCatalog.of("kingdomworld:wooden_watchtower"));
        if (large) {
            south.add(0, TemplateCatalog.of("kingdomworld:barracks"));
        }
        List<Plot> plots = new ArrayList<>();
        row(north, -TOWN_SQUARE - 2, 0, random, plots);
        row(south, TOWN_SQUARE + 2, 2, random, plots);
        return plots;
    }

    /**
     * A row of a town along x, centred on the town hall, its fronts on a line.
     */
    private static void row(List<Entry> entries, int front, int facing, SplittableRandom random, List<Plot> plots) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.width();
        }
        int room = 2 * TOWN_ROW + 1;
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
            boolean high = facing == 0;
            int min = high ? front - entry.depth() + 1 : front;
            plots.add(new Plot(entry.id(), x, min + (entry.depth() - 1) / 2, facing, random.nextBoolean(), "town"));
            at += entry.width() + GAP + share;
        }
    }
}
