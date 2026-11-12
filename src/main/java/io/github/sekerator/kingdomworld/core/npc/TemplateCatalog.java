package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in templates the kingdoms of the world are made of: what each is for, the ground it takes (roof overhang and
 * steps included) and its beds. The plan of a capital is made from this table without looking at the template files, so that it
 * is the same on every server; a test holds the table to the files the generator draws. What a building does in the game
 * follows from its role and its blocks, never from its name. A template may name places inside it (where the guards of a
 * castle stand, where the bank of a treasury is put): the table keeps them too, so that a plan knows them.
 */
public final class TemplateCatalog {
    /**
     * What a template is in the plan of a town: the plan asks for a kind and takes one of its variants.
     */
    public enum Kind {
        SMALL_HOUSE("house"),
        LARGE_HOUSE("house"),
        WORKSHOP("workshop"),
        STOREHOUSE("warehouse"),
        STORAGE_YARD("warehouse"),
        MARKET("market"),
        WELL("well"),
        TOWER("tower"),
        CORNER_TOWER("tower"),
        GATEHOUSE("tower"),
        BARRACKS("barracks"),
        BARRACKS_HALL("barracks"),
        DRILL_YARD(""),
        MAGE_TOWER("mage_tower"),
        TAVERN("tavern"),
        HALL(""),
        CASTLE(""),
        /** The outer ward of a great castle: its stables and garden. */
        CASTLE_WARD(""),
        /** The outer gate of the ward of a great castle, over its moat. */
        BARBICAN("tower"),
        /** The barracks of the royal guard in the ward of a great castle. */
        ROYAL_BARRACKS("barracks"),
        /** The hall of the adventurers' guild: its desk and the post of its registrar. */
        GUILD(""),
        /** The treasury: the bank, the mint and the post of the minter. */
        TREASURY(""),
        /** The court with its guardhouse, whose bunks are for soldiers. */
        COURT("barracks"),
        INFIRMARY(""),
        /** The yards of the builders and of the carriers, each with the post of its workers. */
        YARD(""),
        /** A piece of a town wall with its gate tower. */
        WALL_GATE("tower"),
        /** Monuments, chapels, clock towers and parks: the face of a capital, without a role. */
        DECORATION(""),
        /** Houses of the quarters of a capital, each a kind of its own, so that the plans of towns and of old capitals stay. */
        POOR_HOUSE("house"),
        CRAFT_HOUSE("house"),
        MERCHANT_HOUSE("house"),
        NOBLE_HOUSE("house");

        private final String role;

        Kind(String role) {
            this.role = role;
        }

        /**
         * The role of the buildings of this kind, or empty for those that have none.
         */
        public String role() {
            return role;
        }
    }

    /**
     * A place inside a template, as it is drawn (its front to the south): blocks east of the middle of its ground, layers above
     * the street (the feet of one standing there), blocks south of the middle of its ground.
     */
    public record Spot(int x, int y, int z) {
    }

    /**
     * The rank of a guild whose house a template is: the higher, the bigger and richer the house.
     */
    public enum Rank {
        B, A, S, SS;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The architectural styles of the kingdoms of the world that have templates of their own. */
    public static final List<String> STYLES = List.of("classic", "terem");

    /**
     * A template.
     *
     * @param id      its id, for example {@code kingdomworld:cottage}
     * @param width   blocks along its front
     * @param depth   blocks from its back to its front
     * @param beds    beds in it for those who live there (the rooms of a tavern are for guests)
     * @param upgrade whether it is a building a level higher, which stands only where a ruler improved a lower one
     * @param points  its named places
     * @param style   the architectural style it belongs to, empty for the templates of all styles
     * @param rank    the rank of the guild whose house it is, or {@code null}
     */
    public record Entry(String id, Kind kind, int width, int depth, int beds, boolean upgrade, Map<String, List<Spot>> points, String style,
                        Rank rank) {
        public Entry {
            Map<String, List<Spot>> copy = new LinkedHashMap<>();
            points.forEach((name, spots) -> copy.put(name, List.copyOf(spots)));
            points = Collections.unmodifiableMap(copy);
        }

        public Optional<Rank> guildRank() {
            return Optional.ofNullable(rank);
        }

        public String role() {
            return kind.role();
        }

        /**
         * The positions of a named place, none if the template has no such place.
         */
        public List<Spot> points(String name) {
            return points.getOrDefault(name, List.of());
        }
    }

    private static final String NS = "kingdomworld:";
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        add("small_house", Kind.SMALL_HOUSE, 9, 9, 1);
        upgrade("small_house_2", Kind.SMALL_HOUSE, 9, 9, 2);
        add("cottage", Kind.SMALL_HOUSE, 7, 9, 1);
        add("stone_cottage", Kind.SMALL_HOUSE, 9, 9, 2);
        add("lean_house", Kind.SMALL_HOUSE, 11, 9, 2);
        add("large_house", Kind.LARGE_HOUSE, 11, 11, 3);
        add("town_house", Kind.LARGE_HOUSE, 9, 11, 3);
        add("manor_house", Kind.LARGE_HOUSE, 13, 11, 4);
        add("long_house", Kind.LARGE_HOUSE, 13, 9, 3);
        add("workshop", Kind.WORKSHOP, 11, 9, 0);
        add("smithy", Kind.WORKSHOP, 11, 9, 0);
        add("carpentry", Kind.WORKSHOP, 11, 11, 0);
        add("weavery", Kind.WORKSHOP, 9, 9, 0);
        add("bakery", Kind.WORKSHOP, 9, 9, 0);
        add("storehouse", Kind.STOREHOUSE, 11, 9, 0);
        upgrade("storehouse_2", Kind.STOREHOUSE, 11, 9, 0);
        add("barn", Kind.STOREHOUSE, 13, 11, 0);
        add("storage_yard", Kind.STORAGE_YARD, 21, 11, 0);
        add("market_square", Kind.MARKET, 11, 11, 0);
        add("market_hall", Kind.MARKET, 13, 11, 0);
        add("well", Kind.WELL, 5, 5, 0);
        add("fountain", Kind.WELL, 7, 7, 0);
        add("watchtower", Kind.TOWER, 7, 8, 0);
        add("wooden_watchtower", Kind.TOWER, 7, 7, 0);
        add("corner_tower", Kind.CORNER_TOWER, 9, 9, 0);
        add("gatehouse", Kind.GATEHOUSE, 13, 5, 0);
        add("barracks", Kind.BARRACKS, 11, 9, 4);
        add("barracks_hall", Kind.BARRACKS_HALL, 13, 11, 12);
        add("drill_yard", Kind.DRILL_YARD, 13, 11, 0);
        add("mage_tower", Kind.MAGE_TOWER, 9, 9, 1);
        add("tavern", Kind.TAVERN, 11, 11, 0);
        upgrade("tavern_2", Kind.TAVERN, 11, 11, 0);
        add("inn", Kind.TAVERN, 13, 11, 0);
        add("great_hall", Kind.HALL, 17, 21, 0);
        add("castle", Kind.CASTLE, 35, 35, 0);
        // The style of stone with half-timbering.
        add("classic_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_gate -1 0 11, 1 0 11; palace_door -1 0 4, 1 0 4; " +
                "palace_hall -2 0 3, 2 0 3, -1 0 2, 1 0 2; treasury -3 0 -2; " +
                "castle_wall -12 6 -3, -12 6 5, 12 6 -3, 12 6 5, -6 6 12, 6 6 12, 0 6 11, 0 6 13");
        add("classic_castle_medium", Kind.CASTLE, 41, 41, 0,
                "palace_hall -2 0 3, 2 0 3, -2 0 5, 2 0 5; palace_door 0 0 6; castle_gate -1 0 17, 1 0 17; " +
                "treasury -2 0 -5; " +
                "castle_wall -18 7 -8, -18 7 8, 18 7 -8, 18 7 8, -7 7 -17, 7 7 -17, -10 7 18, 10 7 18; door 0 0 20");
        add("classic_castle_large", Kind.CASTLE, 47, 47, 0,
                "palace_hall -2 0 3, 2 0 3, -1 0 5, 1 0 5; palace_door -1 0 6, 1 0 6; castle_gate -1 0 19, 1 0 19; " +
                "castle_wall -18 8 -8, -18 8 8, 18 8 -8, 18 8 8, -10 8 -18, 10 8 -18, -11 8 18, 11 8 18; " +
                "treasury 0 0 -6; door 0 0 23");
        add("classic_barbican", Kind.BARBICAN, 25, 21, 0,
                "castle_gate -1 0 -2, 1 0 -2; castle_wall -11 6 -1, 11 6 -1; door 0 0 9");
        add("classic_stables", Kind.CASTLE_WARD, 15, 21, 0,
                "door 0 1 4");
        add("classic_guard_barracks", Kind.ROYAL_BARRACKS, 17, 21, 16,
                "door 0 1 5; guard -1 1 3, 1 1 3");
        add("classic_garden", Kind.CASTLE_WARD, 17, 17, 0,
                "door 0 0 8");
        add("classic_guild_b", Kind.GUILD, 13, 15, 0,
                "door 0 1 4; guard -2 1 -1");
        add("classic_guild_a", Kind.GUILD, 19, 19, 0,
                "door -2 1 5; guard 1 1 -1");
        add("classic_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 1 -2; guard 5 1 -6");
        add("classic_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 1 -5; guard 6 1 -10");
        add("classic_mage_tower_b", Kind.MAGE_TOWER, 11, 11, 1,
                "door 0 1 2");
        add("classic_mage_tower_a", Kind.MAGE_TOWER, 13, 13, 2,
                "door 0 1 3");
        add("classic_mage_tower_s", Kind.MAGE_TOWER, 17, 17, 3,
                "door 0 1 6");
        add("classic_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 0 1 6");
        add("classic_treasury", Kind.TREASURY, 17, 17, 0,
                "door 0 2 4; bank 0 2 -3; guard 3 2 1");
        add("classic_court", Kind.COURT, 19, 19, 4,
                "door -3 0 9; guard -3 1 6, -2 1 -2; cells 4 1 0");
        add("classic_infirmary", Kind.INFIRMARY, 17, 13, 0,
                "door -4 0 6");
        add("classic_builders_yard", Kind.YARD, 17, 13, 0,
                "door 0 0 6");
        add("classic_carters_yard", Kind.YARD, 17, 15, 0,
                "door 0 0 7");
        add("classic_monument", Kind.DECORATION, 9, 9, 0,
                "door 0 0 4");
        add("classic_chapel", Kind.DECORATION, 13, 21, 0,
                "door 0 0 10");
        add("classic_clock_tower", Kind.DECORATION, 9, 9, 0,
                "door 0 0 4");
        add("classic_park", Kind.DECORATION, 17, 17, 0,
                "door 0 0 8");
        add("classic_house_hut", Kind.POOR_HOUSE, 5, 7, 1);
        add("classic_house_clay", Kind.POOR_HOUSE, 7, 7, 2);
        add("classic_house_shack", Kind.POOR_HOUSE, 7, 9, 2);
        add("classic_house_cottage", Kind.POOR_HOUSE, 9, 9, 3);
        add("classic_house_jettied", Kind.CRAFT_HOUSE, 7, 11, 2);
        add("classic_house_craftsman", Kind.CRAFT_HOUSE, 11, 11, 2);
        add("classic_house_mansard", Kind.CRAFT_HOUSE, 9, 11, 3);
        add("classic_house_porch", Kind.CRAFT_HOUSE, 9, 11, 3);
        add("classic_house_shop", Kind.MERCHANT_HOUSE, 9, 13, 3,
                "door 0 0 5");
        add("classic_house_turret", Kind.MERCHANT_HOUSE, 11, 11, 3,
                "door -2 0 4");
        add("classic_house_manor", Kind.NOBLE_HOUSE, 13, 15, 4,
                "door 0 0 1; gate 0 0 7");
        add("classic_house_mansion", Kind.NOBLE_HOUSE, 13, 13, 4,
                "door -3 0 5");
        add("classic_smithy", Kind.WORKSHOP, 11, 13, 0,
                "door 0 1 4");
        add("classic_tavern", Kind.TAVERN, 13, 15, 0,
                "door 0 0 5");
        add("classic_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 0 0, 1 0 0; castle_wall -10 6 1, 10 6 1");
        // The style of the wooden terem.
        add("terem_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_wall -11 5 1, -7 5 -11, -7 5 11, 7 5 -11, 7 5 11, 0 5 12; " +
                "castle_gate -1 0 11, 1 0 11; treasury 4 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 4, 1 0 4");
        add("terem_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_wall -15 5 10, 15 5 10, -11 5 -15, -11 5 15, 11 5 -15, 11 5 15, 0 5 16; " +
                "castle_gate -1 0 15, 1 0 15; treasury 5 0 -1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 6, 1 0 6");
        add("terem_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_wall -18 6 11, 18 6 11, -13 6 -18, -13 6 18, 13 6 -18, 13 6 18, 0 6 19; " +
                "castle_gate -1 0 18, 1 0 18; treasury 6 0 -1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 7, 1 0 7");
        add("terem_guild_b", Kind.GUILD, 13, 15, 0,
                "door -3 3 4; guard 2 3 -1");
        add("terem_guild_a", Kind.GUILD, 19, 19, 0,
                "door -6 3 5; guard 1 3 -3");
        add("terem_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 3 0; guard 6 3 -6");
        add("terem_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 3 -2; guard 8 3 -9");
        add("terem_mage_tower_b", Kind.MAGE_TOWER, 11, 11, 1,
                "door 0 1 3");
        add("terem_mage_tower_a", Kind.MAGE_TOWER, 13, 15, 2,
                "door 0 1 4");
        add("terem_mage_tower_s", Kind.MAGE_TOWER, 17, 17, 3,
                "door -3 1 2");
        add("terem_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 0 1 0");
        add("terem_house_izba", Kind.CRAFT_HOUSE, 7, 9, 2);
        add("terem_house_pyatistenok", Kind.CRAFT_HOUSE, 9, 11, 3);
        add("terem_house_merchant", Kind.MERCHANT_HOUSE, 11, 11, 3);
        add("terem_house_tower", Kind.MERCHANT_HOUSE, 9, 11, 3);
        add("terem_house_boyar", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("terem_house_hut", Kind.POOR_HOUSE, 5, 7, 1);
        add("terem_smithy", Kind.WORKSHOP, 13, 11, 0,
                "door -3 1 4");
        add("terem_tavern", Kind.TAVERN, 13, 15, 0,
                "door 0 3 3");
        add("terem_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 1 0, 1 1 0; castle_wall -8 6 -1, 8 6 -1");
    }

    private TemplateCatalog() {
    }

    private static void add(String name, Kind kind, int width, int depth, int beds) {
        add(name, kind, width, depth, beds, "");
    }

    /**
     * A template with named places, written {@code "name x y z, x y z; name x y z"}.
     */
    private static void add(String name, Kind kind, int width, int depth, int beds, String points) {
        ENTRIES.put(NS + name, new Entry(NS + name, kind, width, depth, beds, false, points(points), styleOf(name), rankOf(name, kind)));
    }

    private static void upgrade(String name, Kind kind, int width, int depth, int beds) {
        ENTRIES.put(NS + name, new Entry(NS + name, kind, width, depth, beds, true, Map.of(), "", null));
    }

    /**
     * The style a template belongs to, by the first word of its name.
     */
    static String styleOf(String name) {
        int underscore = name.indexOf('_');
        String first = underscore < 0 ? name : name.substring(0, underscore);
        return STYLES.contains(first) ? first : "";
    }

    /**
     * The rank of the house of a guild, by the last word of its name.
     */
    static Rank rankOf(String name, Kind kind) {
        if (kind != Kind.GUILD && kind != Kind.MAGE_TOWER) {
            return null;
        }
        String last = name.substring(name.lastIndexOf('_') + 1);
        for (Rank rank : Rank.values()) {
            if (rank.id().equals(last)) {
                return rank;
            }
        }
        return null;
    }

    static Map<String, List<Spot>> points(String text) {
        Map<String, List<Spot>> points = new LinkedHashMap<>();
        for (String place : text.split(";")) {
            String trimmed = place.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            List<Spot> spots = new ArrayList<>();
            for (String spot : trimmed.substring(space + 1).split(",")) {
                String[] numbers = spot.trim().split("\\s+");
                spots.add(new Spot(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), Integer.parseInt(numbers[2])));
            }
            points.put(trimmed.substring(0, space), spots);
        }
        return points;
    }

    public static Optional<Entry> get(String id) {
        return Optional.ofNullable(ENTRIES.get(id));
    }

    /**
     * A template of the table.
     *
     * @throws IllegalArgumentException if the table has no such template: the plan never names one it does not know
     */
    public static Entry of(String id) {
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("No template " + id + " in the catalog of the kingdoms of the world");
        }
        return entry;
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /**
     * The variants of a kind a plan chooses from, in the order of the table.
     */
    public static List<Entry> variants(Kind kind) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.kind() == kind && !entry.upgrade()) {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * The templates of a style and kind, in the order of the table.
     */
    public static List<Entry> ofStyle(String style, Kind kind) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.kind() == kind && entry.style().equals(style) && !entry.upgrade()) {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * The house of a guild of a style for a rank: the adventurers' guild for {@link Kind#GUILD}, the mages' guild (its tower)
     * for {@link Kind#MAGE_TOWER}.
     */
    public static Optional<Entry> guildHouse(String style, Kind kind, Rank rank) {
        return ofStyle(style, kind).stream().filter(entry -> entry.rank() == rank).findFirst();
    }

    /**
     * Whether the beds of a template are for soldiers: those of every building of the role of barracks.
     */
    public static boolean bunks(Entry entry) {
        return entry.role().equals("barracks");
    }
}
