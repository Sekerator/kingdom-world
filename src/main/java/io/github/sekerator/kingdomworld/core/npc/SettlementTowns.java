package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * How a small town, a village and a hamlet of a state are laid out in the style of their kingdom. A small town has its town
 * hall on a square in the middle of its main street, with the market, the tavern, a smithy, the storehouse, a well and a watch
 * tower in the rows along the street, two rows of houses along the lanes behind them, a palisade with a gate at each end of
 * the street and its fields outside. A village has two rows of houses along its street with its barn and its well, and its
 * fields beyond the houses; a hamlet two or three houses, a small storehouse, a well and a field. Positions are in blocks from
 * the middle of the settlement, x to the east and z to the south.
 */
public final class SettlementTowns {
    /** Half the side of the square around the town hall. */
    static final int SQUARE = 4;
    /** Where the fronts of the rows along the street stand, from the middle of the street. */
    static final int FRONT = SQUARE + 2;
    /** The deepest building of a row along the street of a town. */
    static final int STREET_DEPTH = 15;
    /** The width of the lanes behind the rows of the street of a town. */
    static final int LANE = 2;
    /** Where the fronts of the rows along the lanes of a town stand. */
    public static final int LANE_FRONT = FRONT + STREET_DEPTH + LANE;
    /** The deepest building of a row along a lane, of a village or a hamlet. */
    static final int HOUSE_DEPTH = 11;
    /** How far the rows of a town reach along its street. */
    static final int TOWN_ROW = 31;
    /** The radius of the palisade of a small town of a state: the rows of its lanes and a walk inside it. */
    public static final int TOWN_WALL = LANE_FRONT + HOUSE_DEPTH + 2;
    /** How far the rows of a village reach along its street. */
    static final int VILLAGE_ROW = 22;
    /** How far the rows of a hamlet reach along its street. */
    static final int HAMLET_ROW = 16;
    /** Blocks between two buildings of a row at least. */
    static final int GAP = 2;
    /** Blocks from the palisade to the middle of the fields outside it. */
    static final int FIELD_OUT = 9;

    private static final String NS = "kingdomworld:";

    private SettlementTowns() {
    }

    // ---------------------------------------------------------------- a small town

    /**
     * The buildings of a small town of a state: the market, the tavern and a smithy along the north side of its street, the
     * storehouse, the well, a second workshop and the watch tower along the south side (with a barracks in a large kingdom),
     * houses along the lanes behind.
     */
    static List<NpcComposition.Plot> town(NpcLevel kingdom, boolean styled, String style, long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x544F574E32L);
        String own = styled ? style : "";
        List<Entry> street = new ArrayList<>();
        street.add(market(random));
        street.add(fitting(own, Kind.TAVERN, STREET_DEPTH, NS + "tavern"));
        street.add(fitting(own, Kind.WORKSHOP, STREET_DEPTH, NS + "smithy"));
        List<Entry> houses = houses(own, HOUSE_DEPTH);
        Entry[] last = new Entry[1];
        street.add(pick(houses, random, last));
        List<Entry> south = new ArrayList<>();
        south.add(fitting(own, Kind.STOREHOUSE, STREET_DEPTH, NS + "storehouse"));
        south.add(TemplateCatalog.of(NS + "well"));
        if (kingdom == NpcLevel.LARGE) {
            south.add(TemplateCatalog.of(NS + "barracks"));
        }
        List<String> workshops = List.of(NS + "bakery", NS + "carpentry", NS + "weavery", NS + "workshop");
        south.add(TemplateCatalog.of(workshops.get(random.nextInt(workshops.size()))));
        south.add(tower(own));
        south.add(pick(houses, random, last));
        List<NpcComposition.Plot> plots = new ArrayList<>();
        row(street, -FRONT, 0, TOWN_ROW, random, plots, "town");
        row(south, FRONT, 2, TOWN_ROW, random, plots, "town");
        List<Entry> north = new ArrayList<>();
        List<Entry> back = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            north.add(pick(houses, random, last));
            back.add(pick(houses, random, last));
        }
        row(north, -LANE_FRONT, 0, TOWN_ROW, random, plots, "town");
        row(back, LANE_FRONT, 2, TOWN_ROW, random, plots, "town");
        return plots;
    }

    /**
     * The fields of a small town outside its palisade: two, four in a large kingdom.
     */
    static List<int[]> townFields(NpcLevel kingdom) {
        int out = TOWN_WALL + FIELD_OUT;
        List<int[]> fields = new ArrayList<>(List.of(new int[]{-15, -out}, new int[]{15, out}));
        if (kingdom == NpcLevel.LARGE) {
            fields.add(new int[]{15, -out});
            fields.add(new int[]{-15, out});
        }
        return fields;
    }

    /**
     * The gates of the palisade of a small town: one at each end of its street.
     */
    static List<int[]> townGates() {
        return List.of(new int[]{-TOWN_WALL, 0}, new int[]{TOWN_WALL, 0});
    }

    /**
     * Whether a place of a small town, in blocks from its middle, lies on one of the lanes behind the rows of its street.
     */
    public static boolean lane(int dx, int dz) {
        int from = FRONT + STREET_DEPTH;
        return Math.abs(dz) >= from && Math.abs(dz) < from + LANE && Math.abs(dx) <= TOWN_ROW;
    }

    /**
     * The round of the guard of a small town: from the west gate past the square to the east gate and back.
     */
    static List<int[]> townRound() {
        int inside = TOWN_WALL - 3;
        return List.of(new int[]{-inside, 0}, new int[]{0, SQUARE + 1}, new int[]{inside, 0}, new int[]{0, -SQUARE - 1});
    }

    // ---------------------------------------------------------------- a village and a hamlet

    /**
     * The buildings of a village: its barn and houses along the north side of its street, its well and houses along the south
     * side; four to six houses by its seed.
     */
    static List<NpcComposition.Plot> village(boolean styled, String style, long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x56494C4CL);
        String own = styled ? style : "";
        int count = 4 + random.nextInt(3);
        List<Entry> houses = houses(own, HOUSE_DEPTH);
        Entry[] last = new Entry[1];
        List<Entry> north = new ArrayList<>();
        north.add(TemplateCatalog.of(NS + "barn"));
        List<Entry> south = new ArrayList<>();
        south.add(TemplateCatalog.of(NS + "well"));
        for (int i = 0; i < count; i++) {
            (i % 2 == 0 ? north : south).add(pick(houses, random, last));
        }
        List<NpcComposition.Plot> plots = new ArrayList<>();
        row(north, -FRONT, 0, VILLAGE_ROW, random, plots, "village");
        row(south, FRONT, 2, VILLAGE_ROW, random, plots, "village");
        return plots;
    }

    /**
     * The fields of a village beyond its houses: two, three for a village of six houses.
     */
    static List<int[]> villageFields(long seed) {
        int out = FRONT + HOUSE_DEPTH + 9;
        List<int[]> fields = new ArrayList<>(List.of(new int[]{-10, -out}, new int[]{10, out}));
        if (new SplittableRandom(seed ^ 0x56494C4CL).nextInt(3) == 2) {
            fields.add(new int[]{10, -out});
        }
        return fields;
    }

    /**
     * The buildings of a hamlet: two or three houses, a small storehouse and a well.
     */
    static List<NpcComposition.Plot> hamlet(boolean styled, String style, long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x48414D4CL);
        String own = styled ? style : "";
        int count = 2 + random.nextInt(2);
        List<Entry> houses = houses(own, HOUSE_DEPTH);
        Entry[] last = new Entry[1];
        List<Entry> north = new ArrayList<>();
        north.add(TemplateCatalog.of(NS + "storehouse"));
        north.add(pick(houses, random, last));
        List<Entry> south = new ArrayList<>();
        south.add(TemplateCatalog.of(NS + "well"));
        for (int i = 1; i < count; i++) {
            (i % 2 == 0 ? north : south).add(pick(houses, random, last));
        }
        List<NpcComposition.Plot> plots = new ArrayList<>();
        row(north, -FRONT, 0, HAMLET_ROW, random, plots, "village");
        row(south, FRONT, 2, HAMLET_ROW, random, plots, "village");
        return plots;
    }

    /**
     * The field of a hamlet, beyond the houses on the north side.
     */
    static List<int[]> hamletFields() {
        return List.of(new int[]{0, -(FRONT + HOUSE_DEPTH + 8)});
    }

    /**
     * The round of the watch of a village or a hamlet along its street.
     */
    static List<int[]> street(int reach) {
        return List.of(new int[]{-reach, 0}, new int[]{0, SQUARE}, new int[]{reach, 0}, new int[]{0, -SQUARE});
    }

    // ---------------------------------------------------------------- choosing

    /**
     * The market of a town: the open square of stalls, now and then the covered market.
     */
    private static Entry market(SplittableRandom random) {
        return TemplateCatalog.of(random.nextInt(4) == 0 ? NS + "market_hall" : NS + "market_square");
    }

    /**
     * A building of a kind in the style of the kingdom that fits the depth of a row, otherwise the plain one named.
     */
    static Entry fitting(String style, Kind kind, int depth, String plain) {
        if (!style.isEmpty()) {
            for (Entry entry : TemplateCatalog.ofStyle(style, kind)) {
                if (entry.depth() <= depth && entry.width() <= 15 && entry.guildRank().isEmpty()) {
                    return entry;
                }
            }
        }
        return TemplateCatalog.of(plain);
    }

    /**
     * The watch tower of the style, otherwise the wooden one.
     */
    private static Entry tower(String style) {
        Optional<Entry> styled = style.isEmpty() ? Optional.empty() : TemplateCatalog.get(NS + style + "_camp_tower");
        return styled.orElse(TemplateCatalog.of(NS + "wooden_watchtower"));
    }

    /**
     * The houses of the style for a row no deeper than given: those of the poor, the craftsmen and the merchants; the plain
     * houses for a kingdom without a style.
     */
    static List<Entry> houses(String style, int depth) {
        List<Entry> list = new ArrayList<>();
        if (!style.isEmpty()) {
            for (Kind kind : List.of(Kind.POOR_HOUSE, Kind.CRAFT_HOUSE, Kind.MERCHANT_HOUSE)) {
                for (Entry entry : TemplateCatalog.ofStyle(style, kind)) {
                    if (entry.depth() <= depth && entry.width() <= 13 && entry.beds() > 0) {
                        list.add(entry);
                    }
                }
            }
        }
        if (list.isEmpty()) {
            for (Kind kind : List.of(Kind.SMALL_HOUSE, Kind.LARGE_HOUSE)) {
                for (Entry entry : TemplateCatalog.variants(kind)) {
                    if (entry.depth() <= depth && entry.beds() > 0) {
                        list.add(entry);
                    }
                }
            }
        }
        return list;
    }

    /**
     * A house unlike the one before it.
     */
    private static Entry pick(List<Entry> houses, SplittableRandom random, Entry[] last) {
        List<Entry> choices = new ArrayList<>(houses);
        if (choices.size() > 1) {
            choices.remove(last[0]);
        }
        last[0] = choices.get(random.nextInt(choices.size()));
        return last[0];
    }

    /**
     * A row along x with its fronts on a line, the first building in its middle and the next ones by turns to the west and
     * the east of it; what does not fit is left out from the end of the list.
     *
     * @param front  where the fronts stand, in blocks south of the middle (negative north)
     * @param facing 0 for fronts to the south (a row north of its street), 2 for fronts to the north
     * @param reach  how far the row reaches either way from the middle
     */
    static void row(List<Entry> entries, int front, int facing, int reach, SplittableRandom random, List<NpcComposition.Plot> plots,
                    String quarter) {
        List<Entry> kept = new ArrayList<>(entries);
        int room = 2 * reach + 1;
        while (kept.size() > 1 && width(kept) + (kept.size() - 1) * GAP > room) {
            kept.remove(kept.size() - 1);
        }
        // The first in the middle, the next by turns to the west and to the east.
        List<Entry> ordered = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            if (i % 2 == 1) {
                ordered.add(0, kept.get(i));
            } else {
                ordered.add(kept.get(i));
            }
        }
        int total = width(ordered);
        int gaps = ordered.size() - 1;
        int free = room - total - gaps * GAP;
        int share = gaps > 0 ? Math.min(3, free / gaps) : 0;
        int length = total + gaps * (GAP + share);
        int at = -length / 2;
        for (Entry entry : ordered) {
            int x = at + (entry.width() - 1) / 2;
            int min = facing == 0 ? front - entry.depth() + 1 : front;
            plots.add(new NpcComposition.Plot(entry.id(), x, min + (entry.depth() - 1) / 2, facing, random.nextBoolean(), quarter));
            at += entry.width() + GAP + share;
        }
    }

    private static int width(List<Entry> entries) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.width();
        }
        return total;
    }
}
