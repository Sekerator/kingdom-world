package io.github.sekerator.kingdomworld.core.land;

import java.util.Locale;

/**
 * What most of the border between two provinces runs along. Earlier kinds win a tie.
 */
public enum BorderKind {
    /** A river or a lake. */
    RIVER,
    /** Mountains or hills. */
    RIDGE,
    /** The shore. */
    COAST,
    /** Open land. */
    PLAIN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The kind of the border between two cells side by side.
     */
    static BorderKind between(TerrainClass a, TerrainClass b) {
        if (a == TerrainClass.RIVER || a == TerrainClass.LAKE || b == TerrainClass.RIVER || b == TerrainClass.LAKE) {
            return RIVER;
        }
        if (a == TerrainClass.STEEP || b == TerrainClass.STEEP || (a == TerrainClass.HILLS && b == TerrainClass.HILLS)) {
            return RIDGE;
        }
        if (a == TerrainClass.BEACH || b == TerrainClass.BEACH) {
            return COAST;
        }
        return PLAIN;
    }
}
