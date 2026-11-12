package io.github.sekerator.kingdomworld.core.npc;

/**
 * Which templates a ruler of the world may put up in a kingdom of a style, and in which quarter a building of a kind stands
 * as far as its look goes.
 */
public final class RulerTemplates {
    private RulerTemplates() {
    }

    /**
     * Whether a ruler of a kingdom of a style may put up a building of a template.
     *
     * @param kingdomStyle the architectural style of the kingdom, or empty for a kingdom without one
     */
    public static boolean usable(TemplateCatalog.Entry entry, String kingdomStyle) {
        return true;
    }

    /**
     * The quarter whose look a building of a kind a ruler puts up wears.
     */
    public static BuildingLooks.Quarter quarter(TemplateCatalog.Kind kind) {
        return BuildingLooks.Quarter.PUBLIC;
    }
}
