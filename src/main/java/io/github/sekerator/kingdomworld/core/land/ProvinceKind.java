package io.github.sekerator.kingdomworld.core.land;

import java.util.Locale;

/**
 * What a province of the plan is to its state.
 */
public enum ProvinceKind {
    /** The province of the capital. */
    HEART,
    /** A province of the state with room for its cities and towns. */
    CORE,
    /** An outlying province of the state, left empty. */
    FRONTIER,
    /** A province of the neutral land, nobody's. */
    WILD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
