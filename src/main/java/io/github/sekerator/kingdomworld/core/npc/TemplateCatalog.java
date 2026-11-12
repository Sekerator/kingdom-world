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
     * A template.
     *
     * @param id      its id, for example {@code kingdomworld:cottage}
     * @param width   blocks along its front
     * @param depth   blocks from its back to its front
     * @param beds    beds in it for those who live there (the rooms of a tavern are for guests)
     * @param upgrade whether it is a building a level higher, which stands only where a ruler improved a lower one
     * @param points  its named places
     */
    public record Entry(String id, Kind kind, int width, int depth, int beds, boolean upgrade, Map<String, List<Spot>> points) {
        public Entry {
            Map<String, List<Spot>> copy = new LinkedHashMap<>();
            points.forEach((name, spots) -> copy.put(name, List.copyOf(spots)));
            points = Collections.unmodifiableMap(copy);
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
        ENTRIES.put(NS + name, new Entry(NS + name, kind, width, depth, beds, false, points(points)));
    }

    private static void upgrade(String name, Kind kind, int width, int depth, int beds) {
        ENTRIES.put(NS + name, new Entry(NS + name, kind, width, depth, beds, true, Map.of()));
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
     * Whether the beds of a template are for soldiers: those of every building of the role of barracks.
     */
    public static boolean bunks(Entry entry) {
        return entry.role().equals("barracks");
    }
}
