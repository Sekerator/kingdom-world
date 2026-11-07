package io.github.sekerator.kingdomworld.core.production;

import java.util.List;

/**
 * Ids of the kinds of workstations in the zone of a work post. A post remembers how many of each it has, so that
 * work far from players knows what can be made there.
 */
public final class Stations {
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String STONECUTTER = "stonecutter";
    public static final String SMITHING_TABLE = "smithing_table";
    public static final String FURNACE = "furnace";
    public static final String BLAST_FURNACE = "blast_furnace";
    public static final String SMOKER = "smoker";
    public static final String CAMPFIRE = "campfire";
    /** The mint of the minter. */
    public static final String MINT = "mint";

    /** Every kind, in the order the post screen lists them. */
    public static final List<String> ALL = List.of(FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE, CRAFTING_TABLE, STONECUTTER, SMITHING_TABLE, MINT);

    private Stations() {
    }

    public static boolean isKnown(String id) {
        return ALL.contains(id);
    }
}
