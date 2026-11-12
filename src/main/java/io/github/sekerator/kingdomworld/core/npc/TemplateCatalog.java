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
    public static final List<String> STYLES = List.of("classic", "terem", "nordic", "southern", "desert", "eastern", "gothic", "mountain");

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
        add("classic_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 1 5");
        add("classic_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("classic_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 5");
        add("classic_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 1 3");
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
        add("terem_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 0 5");
        add("terem_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("terem_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door -1 2 3");
        add("terem_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 0 2");
        // The style of the north.
        add("nordic_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_wall -12 5 -3, 12 5 -3, -12 5 5, 12 5 5, -7 5 12, 7 5 12, -7 5 -12, 7 5 -12; " +
                "castle_gate -1 0 12, 1 0 12; treasury 2 0 -5; palace_door 0 0 5; " +
                "palace_hall -2 0 3, 2 0 3, -2 0 5, 2 0 5");
        add("nordic_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_wall -16 6 -3, 16 6 -3, -16 6 5, 16 6 5, -10 6 16, 10 6 16, -10 6 -16, 10 6 -16; " +
                "castle_gate -1 0 16, 1 0 16; treasury 2 0 -6; palace_door 0 0 5; " +
                "palace_hall -2 0 3, 2 0 3, -2 0 5, 2 0 5");
        add("nordic_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_wall -18 6 -3, 18 6 -3, -18 6 5, 18 6 5, -11 6 18, 11 6 18, -11 6 -18, 11 6 -18; " +
                "castle_gate -1 0 18, 1 0 18; treasury 2 0 -7; palace_door 0 0 5; " +
                "palace_hall -2 0 3, 2 0 3, -2 0 5, 2 0 5");
        add("nordic_guild_b", Kind.GUILD, 15, 19, 0,
                "door 0 2 6; guard 2 2 -2");
        add("nordic_guild_a", Kind.GUILD, 21, 25, 0,
                "door -3 2 8; guard -1 2 -4");
        add("nordic_guild_s", Kind.GUILD, 27, 29, 0,
                "door -3 2 8; guard -1 2 -5");
        add("nordic_guild_ss", Kind.GUILD, 35, 37, 0,
                "door 0 2 -1; guard 6 2 -8");
        add("nordic_mage_tower_b", Kind.MAGE_TOWER, 11, 15, 1,
                "door 0 0 4");
        add("nordic_mage_tower_a", Kind.MAGE_TOWER, 13, 19, 2,
                "door 0 1 4");
        add("nordic_mage_tower_s", Kind.MAGE_TOWER, 19, 21, 3,
                "door -4 0 3");
        add("nordic_mage_tower_ss", Kind.MAGE_TOWER, 25, 27, 4,
                "door 0 1 2");
        add("nordic_house_hut", Kind.POOR_HOUSE, 7, 9, 1);
        add("nordic_house_fisher", Kind.CRAFT_HOUSE, 13, 15, 2);
        add("nordic_house_red", Kind.CRAFT_HOUSE, 11, 11, 3);
        add("nordic_house_longhouse", Kind.CRAFT_HOUSE, 13, 15, 2);
        add("nordic_house_merchant", Kind.MERCHANT_HOUSE, 13, 15, 3);
        add("nordic_house_chieftain", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("nordic_smithy", Kind.WORKSHOP, 13, 11, 0,
                "door -1 1 4");
        add("nordic_tavern", Kind.TAVERN, 13, 19, 0,
                "door -1 1 8");
        add("nordic_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 1 -1, 1 1 -1; castle_wall -8 5 -1, 8 5 -1");
        add("nordic_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 0 5");
        add("nordic_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("nordic_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door -1 0 5");
        add("nordic_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 1 3");
        // The style of the south.
        add("southern_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_gate -1 0 11, 1 0 11; " +
                "castle_wall -1 12 12, 1 12 12, -6 6 12, -4 6 12, 4 6 12, 11 6 12, -12 11 0, 12 11 0; " +
                "palace_hall -2 0 1, 2 0 1, -2 0 3, 2 0 3; palace_door -1 0 4, 1 0 4; treasury 6 0 3");
        add("southern_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_gate -1 0 16, 1 0 16; " +
                "castle_wall -2 13 17, 2 13 17, -11 6 17, -5 6 17, 5 6 17, 11 6 17, -17 11 0, 17 11 0; " +
                "palace_hall -2 0 1, 2 0 1, -2 0 3, 2 0 3; palace_door -1 0 6, 1 0 6; treasury 8 0 3");
        add("southern_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_gate -1 0 19, 1 0 19; " +
                "castle_wall -3 14 20, 3 14 20, -13 6 20, -6 6 20, 6 6 20, 13 6 20, -20 11 0, 20 11 0; " +
                "palace_hall -2 0 1, 2 0 1, -2 0 3, 2 0 3; palace_door -1 0 7, 1 0 7; treasury 10 0 3");
        add("southern_guild_b", Kind.GUILD, 13, 15, 0,
                "door -2 1 2; guard 1 1 -3");
        add("southern_guild_a", Kind.GUILD, 17, 19, 0,
                "door 0 1 3; guard 2 1 -3");
        add("southern_guild_s", Kind.GUILD, 23, 25, 0,
                "door -6 1 5; guard -1 1 -5");
        add("southern_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 1 10; guard 0 1 7");
        add("southern_mage_tower_b", Kind.MAGE_TOWER, 11, 11, 1,
                "door 0 1 4");
        add("southern_mage_tower_a", Kind.MAGE_TOWER, 13, 15, 2,
                "door 0 1 4");
        add("southern_mage_tower_s", Kind.MAGE_TOWER, 19, 17, 3,
                "door 3 1 4");
        add("southern_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 5 1 1");
        add("southern_house_fisher", Kind.POOR_HOUSE, 5, 7, 1);
        add("southern_house_white", Kind.CRAFT_HOUSE, 7, 9, 2);
        add("southern_house_court", Kind.CRAFT_HOUSE, 11, 11, 2);
        add("southern_house_narrow", Kind.CRAFT_HOUSE, 7, 11, 3);
        add("southern_house_merchant", Kind.MERCHANT_HOUSE, 11, 13, 3);
        add("southern_house_villa", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("southern_smithy", Kind.WORKSHOP, 13, 11, 0,
                "door 0 1 5");
        add("southern_tavern", Kind.TAVERN, 13, 15, 0,
                "door 0 1 3");
        add("southern_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 1 5; castle_gate -1 1 0, 1 1 0; castle_wall -10 7 0, 10 7 0");
        add("southern_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 1 4");
        add("southern_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 4");
        add("southern_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 4");
        add("southern_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 1 3");
        // The style of the desert.
        add("desert_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_gate -1 0 14, 1 0 14; treasury 4 0 1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 5, 1 0 5; castle_wall -5 8 -13, 5 8 -13, -13 8 -4, -13 8 4, 13 8 -2, 13 8 5");
        add("desert_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_gate -1 0 18, 1 0 18; treasury 5 0 -1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 8, 1 0 8; " +
                "castle_wall -8 9 -17, 8 9 -17, -17 9 -6, -17 9 8, 17 9 -6, 17 9 8, -12 9 17, 12 9 17");
        add("desert_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_gate -1 0 21, 1 0 21; treasury 6 0 -1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 9, 1 0 9; " +
                "castle_wall -11 10 -20, 10 10 -20, -20 10 -7, -20 10 8, 20 10 -5, 20 10 8, -14 10 20, 14 10 20");
        add("desert_guild_b", Kind.GUILD, 13, 15, 0,
                "door 0 1 5; guard -1 1 -1");
        add("desert_guild_a", Kind.GUILD, 19, 21, 0,
                "door 0 1 8; guard -1 1 -4");
        add("desert_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 1 9; guard -1 1 -7");
        add("desert_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 1 11; guard -1 1 -9");
        add("desert_mage_tower_b", Kind.MAGE_TOWER, 11, 11, 1,
                "door 0 1 3");
        add("desert_mage_tower_a", Kind.MAGE_TOWER, 13, 13, 2,
                "door 0 1 3");
        add("desert_mage_tower_s", Kind.MAGE_TOWER, 17, 17, 3,
                "door 0 1 6");
        add("desert_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 0 1 7");
        add("desert_house_hut", Kind.POOR_HOUSE, 5, 7, 1);
        add("desert_house_court", Kind.CRAFT_HOUSE, 13, 13, 3);
        add("desert_house_windtower", Kind.CRAFT_HOUSE, 7, 9, 2);
        add("desert_house_merchant", Kind.MERCHANT_HOUSE, 11, 11, 3);
        add("desert_house_terrace", Kind.CRAFT_HOUSE, 9, 11, 3);
        add("desert_house_noble", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("desert_smithy", Kind.WORKSHOP, 11, 13, 0,
                "door 0 1 4");
        add("desert_tavern", Kind.TAVERN, 13, 15, 0,
                "door 0 1 5");
        add("desert_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 1 0, 1 1 0; castle_wall -3 13 1, 3 13 1");
        add("desert_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 1 5");
        add("desert_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("desert_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 5");
        add("desert_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 1 3");
        // The style of the east.
        add("eastern_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_wall -12 4 -6, 12 4 -6, -12 4 0, 12 4 0, 0 4 12, -6 4 12, 6 4 12; " +
                "treasury 3 0 -1; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 4, 1 0 4; " +
                "castle_gate -1 0 12, 1 0 12");
        add("eastern_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_wall -17 5 -6, 17 5 -6, -17 5 2, 17 5 2, 0 5 17, -6 5 17, 6 5 17; " +
                "treasury 4 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 4, 1 0 4; " +
                "castle_gate -1 0 17, 1 0 17");
        add("eastern_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_wall -20 5 -6, 20 5 -6, -20 5 5, 20 5 5, 0 5 20, -7 5 20, 7 5 20; " +
                "treasury 4 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 6, 1 0 6; " +
                "castle_gate -1 0 20, 1 0 20");
        add("eastern_guild_b", Kind.GUILD, 13, 15, 0,
                "door 0 2 4; guard -1 2 -1");
        add("eastern_guild_a", Kind.GUILD, 17, 19, 0,
                "door 0 3 3; guard -1 3 -2");
        add("eastern_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 3 0; guard -1 3 -5");
        add("eastern_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 3 -4; guard -1 3 -9");
        add("eastern_mage_tower_b", Kind.MAGE_TOWER, 13, 13, 1,
                "door 0 1 5");
        add("eastern_mage_tower_a", Kind.MAGE_TOWER, 15, 15, 2,
                "door 0 1 6");
        add("eastern_mage_tower_s", Kind.MAGE_TOWER, 23, 19, 3,
                "door -3 2 5");
        add("eastern_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 0 2 2");
        add("eastern_house_shack", Kind.POOR_HOUSE, 5, 7, 1);
        add("eastern_house_farmer", Kind.CRAFT_HOUSE, 11, 9, 2);
        add("eastern_house_town", Kind.MERCHANT_HOUSE, 9, 13, 2);
        add("eastern_house_merchant", Kind.MERCHANT_HOUSE, 11, 13, 3);
        add("eastern_house_garden", Kind.CRAFT_HOUSE, 11, 15, 2);
        add("eastern_house_noble", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("eastern_smithy", Kind.WORKSHOP, 13, 13, 0,
                "door 0 1 3");
        add("eastern_tavern", Kind.TAVERN, 13, 15, 0,
                "door 0 1 3");
        add("eastern_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 1 0, 1 1 0; castle_wall -8 6 0, 8 6 0");
        add("eastern_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 1 5");
        add("eastern_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("eastern_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 5");
        add("eastern_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 0 3");
        // The style gothic.
        add("gothic_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; castle_gate -1 0 12, 1 0 12; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 6, 1 0 6; treasury 7 0 -4; " +
                "castle_wall -13 7 -7, -13 7 5, 13 7 -7, 13 7 5, -8 7 -13, 8 7 -13, -6 7 13, 6 7 13");
        add("gothic_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; castle_gate -1 0 16, 1 0 16; treasury 3 0 -4; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 8, 1 0 8; " +
                "castle_wall -17 8 -11, -17 8 7, 17 8 -11, 17 8 7, -8 8 -17, 8 8 -17, -9 8 17, 8 8 17");
        add("gothic_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; castle_gate -1 0 19, 1 0 19; treasury 3 0 -5; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; " +
                "palace_door -1 0 7, 1 0 7; " +
                "castle_wall -20 9 -14, -20 9 6, 20 9 -14, 20 9 6, -9 9 -20, 9 9 -20, -11 9 20, 10 9 20");
        add("gothic_guild_b", Kind.GUILD, 15, 15, 0,
                "door -2 1 4; guard -1 1 -1");
        add("gothic_guild_a", Kind.GUILD, 19, 19, 0,
                "door -3 1 6; guard -1 1 -1");
        add("gothic_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 1 8; guard 2 1 -4");
        add("gothic_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 1 11; guard 2 1 -6");
        add("gothic_mage_tower_b", Kind.MAGE_TOWER, 11, 11, 1,
                "door 0 1 2");
        add("gothic_mage_tower_a", Kind.MAGE_TOWER, 13, 13, 2,
                "door 0 1 3");
        add("gothic_mage_tower_s", Kind.MAGE_TOWER, 19, 17, 3,
                "door 3 1 1");
        add("gothic_mage_tower_ss", Kind.MAGE_TOWER, 25, 25, 4,
                "door 0 1 8");
        add("gothic_house_hovel", Kind.POOR_HOUSE, 5, 7, 1);
        add("gothic_house_narrow", Kind.CRAFT_HOUSE, 9, 13, 3);
        add("gothic_house_oriel", Kind.CRAFT_HOUSE, 11, 11, 3);
        add("gothic_house_merchant", Kind.MERCHANT_HOUSE, 11, 13, 3);
        add("gothic_house_turret", Kind.MERCHANT_HOUSE, 13, 13, 3);
        add("gothic_house_manor", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("gothic_smithy", Kind.WORKSHOP, 13, 11, 0,
                "door -3 1 3");
        add("gothic_tavern", Kind.TAVERN, 13, 15, 0,
                "door -3 0 7");
        add("gothic_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 0 5; castle_gate -1 1 0, 1 1 0; castle_wall -8 8 0, 8 8 0");
        add("gothic_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 0 5");
        add("gothic_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("gothic_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 5");
        add("gothic_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 0 3");
        // The style of the mountains.
        add("mountain_castle_small", Kind.CASTLE, 31, 31, 0,
                "door 0 0 15; treasury -3 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 4, 1 0 4; " +
                "castle_gate -1 0 11, 1 0 11; " +
                "castle_wall -11 8 1, -11 12 13, -3 20 11, -4 8 5, 11 8 1, 11 12 13, 3 20 11, 4 8 5");
        add("mountain_castle_medium", Kind.CASTLE, 41, 41, 0,
                "door 0 0 20; treasury -5 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 6, 1 0 6; " +
                "castle_gate -1 0 15, 1 0 15; " +
                "castle_wall -15 10 1, -15 14 17, -3 20 15, -4 10 7, 15 10 1, 15 14 17, 3 20 15, 4 10 7");
        add("mountain_castle_large", Kind.CASTLE, 47, 47, 0,
                "door 0 0 23; treasury -6 0 -2; palace_hall -2 0 3, 2 0 3, -2 0 1, 2 0 1; palace_door -1 0 7, 1 0 7; " +
                "castle_gate -1 0 17, 1 0 17; " +
                "castle_wall -18 11 1, -16 16 18, -3 21 18, -4 11 8, 18 11 1, 16 16 18, 3 21 18, 4 11 8");
        add("mountain_guild_b", Kind.GUILD, 13, 15, 0,
                "door 0 1 3; guard 2 1 -1");
        add("mountain_guild_a", Kind.GUILD, 19, 19, 0,
                "door 0 1 5; guard 2 1 -3");
        add("mountain_guild_s", Kind.GUILD, 23, 25, 0,
                "door 0 3 4; guard 2 3 -6");
        add("mountain_guild_ss", Kind.GUILD, 31, 33, 0,
                "door 0 4 2; guard 3 4 -5");
        add("mountain_mage_tower_b", Kind.MAGE_TOWER, 13, 13, 1,
                "door 0 1 3");
        add("mountain_mage_tower_a", Kind.MAGE_TOWER, 15, 15, 2,
                "door 0 1 4");
        add("mountain_mage_tower_s", Kind.MAGE_TOWER, 19, 21, 3,
                "door 0 1 5");
        add("mountain_mage_tower_ss", Kind.MAGE_TOWER, 27, 27, 4,
                "door 0 3 3");
        add("mountain_house_dugout", Kind.POOR_HOUSE, 9, 9, 1);
        add("mountain_house_miner", Kind.CRAFT_HOUSE, 9, 11, 2);
        add("mountain_house_forge", Kind.CRAFT_HOUSE, 13, 13, 3);
        add("mountain_house_rock", Kind.CRAFT_HOUSE, 11, 13, 2);
        add("mountain_house_merchant", Kind.MERCHANT_HOUSE, 13, 15, 3);
        add("mountain_house_noble", Kind.NOBLE_HOUSE, 13, 15, 4);
        add("mountain_smithy", Kind.WORKSHOP, 15, 13, 0,
                "door -1 0 5");
        add("mountain_tavern", Kind.TAVERN, 21, 17, 0,
                "door -3 1 6");
        add("mountain_wall_gate", Kind.WALL_GATE, 23, 11, 0,
                "door 0 1 5; castle_gate -1 1 0, 1 1 0; castle_wall -2 16 -1, 2 16 -1");
        add("mountain_camp_barracks", Kind.BARRACKS_HALL, 13, 11, 12,
                "door 0 1 5");
        add("mountain_camp_yard", Kind.DRILL_YARD, 13, 11, 0,
                "door 0 0 5");
        add("mountain_camp_store", Kind.STOREHOUSE, 13, 11, 0,
                "door 0 0 5");
        add("mountain_camp_tower", Kind.TOWER, 7, 7, 0,
                "door 0 1 3");
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
