package io.github.sekerator.kingdomworld.core.npc;

import java.util.List;
import java.util.Set;

/**
 * The architectural styles of kingdoms: which one suits a climate, which one a new kingdom of the world takes so that no two of
 * them share one, and the stone of their walls and streets.
 */
public final class ArchitecturalStyles {
    /** The ids of the styles, in the order of their indices. */
    public static final List<String> IDS = TemplateCatalog.STYLES;

    private ArchitecturalStyles() {
    }

    /**
     * Whether a style of this id exists.
     */
    public static boolean known(String id) {
        return id != null && IDS.contains(id);
    }

    /**
     * The index of a style, or -1 for an unknown id.
     */
    public static int index(String id) {
        return IDS.indexOf(id);
    }

    /**
     * The id of a style by its index, or empty for an index out of range.
     */
    public static String id(int index) {
        return index >= 0 && index < IDS.size() ? IDS.get(index) : "";
    }

    /**
     * The styles in the order a climate prefers them, every style once.
     *
     * @param mountains whether the place lies in mountains
     */
    public static List<String> preferences(NpcStyle.Climate climate, boolean mountains) {
        return IDS;
    }

    /**
     * The style a climate prefers most.
     */
    public static String byClimate(NpcStyle.Climate climate, boolean mountains) {
        return IDS.get(0);
    }

    /**
     * The style of a new kingdom of the world: the first the climate prefers that no other kingdom holds.
     *
     * @param taken the styles other kingdoms of the world hold
     */
    public static String pick(NpcStyle.Climate climate, boolean mountains, long seed, Set<String> taken) {
        return IDS.get(0);
    }

    /**
     * The block of the stone walls of a style.
     */
    public static String wallBlock(String style) {
        return "";
    }

    /**
     * The paving of the squares and avenues of a style.
     */
    public static String paving(String style) {
        return "";
    }

    /**
     * The rougher paving of the streets of a style.
     */
    public static String cobbles(String style) {
        return "";
    }
}
