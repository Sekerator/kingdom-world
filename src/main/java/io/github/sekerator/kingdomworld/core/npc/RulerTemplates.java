package io.github.sekerator.kingdomworld.core.npc;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which templates a ruler of the world may put up in a kingdom of a style, and in which quarter a building of a kind stands
 * as far as its look goes.
 *
 * <p>A ruler puts up houses, workshops, taverns and the plain buildings every kingdom has. The castle, the wall and its gates,
 * the institutions of the square, the houses of the guilds, the decorations and the camps of the army belong to the plan of a
 * capital: no ruler adds them by itself. A kingdom of an architectural style builds the houses, the smithy and the tavern of its
 * own style instead of the plain ones (every style has them) and never those of another style; a kingdom without a style keeps
 * to the plain templates. Templates the table does not know (those of data packs) may always be used.
 */
public final class RulerTemplates {
    /** Kinds of the templates that belong to the plan of a capital, its wall and its castle. */
    private static final Set<TemplateCatalog.Kind> OF_THE_PLAN = EnumSet.of(
            TemplateCatalog.Kind.GATEHOUSE,
            TemplateCatalog.Kind.CORNER_TOWER,
            TemplateCatalog.Kind.HALL,
            TemplateCatalog.Kind.CASTLE,
            TemplateCatalog.Kind.DRILL_YARD,
            TemplateCatalog.Kind.STORAGE_YARD,
            TemplateCatalog.Kind.CASTLE_WARD,
            TemplateCatalog.Kind.BARBICAN,
            TemplateCatalog.Kind.ROYAL_BARRACKS,
            TemplateCatalog.Kind.GUILD,
            TemplateCatalog.Kind.TREASURY,
            TemplateCatalog.Kind.COURT,
            TemplateCatalog.Kind.INFIRMARY,
            TemplateCatalog.Kind.YARD,
            TemplateCatalog.Kind.WALL_GATE,
            TemplateCatalog.Kind.DECORATION);
    /** Kinds of the templates of a style a ruler of that style puts up. */
    private static final Set<TemplateCatalog.Kind> OF_A_STYLE = EnumSet.of(
            TemplateCatalog.Kind.POOR_HOUSE,
            TemplateCatalog.Kind.CRAFT_HOUSE,
            TemplateCatalog.Kind.MERCHANT_HOUSE,
            TemplateCatalog.Kind.NOBLE_HOUSE,
            TemplateCatalog.Kind.WORKSHOP,
            TemplateCatalog.Kind.TAVERN);
    /** Kinds of the plain templates a kingdom of a style builds in its own style instead. */
    private static final Set<TemplateCatalog.Kind> REPLACED = EnumSet.of(
            TemplateCatalog.Kind.SMALL_HOUSE,
            TemplateCatalog.Kind.LARGE_HOUSE,
            TemplateCatalog.Kind.TAVERN);
    /** The plain smithy: every style has a smithy of its own. */
    private static final String PLAIN_SMITHY = "kingdomworld:smithy";
    /** What the ids of the buildings of the camps of the army have in them. */
    private static final String CAMP = "_camp_";

    private RulerTemplates() {
    }

    /**
     * Whether a ruler of a kingdom of a style may put up a building of a template.
     *
     * @param kingdomStyle the architectural style of the kingdom, or empty for a kingdom without one; a style the table does not
     *                     know counts as none
     */
    public static boolean usable(TemplateCatalog.Entry entry, String kingdomStyle) {
        if (OF_THE_PLAN.contains(entry.kind()) || entry.id().contains(CAMP)) {
            return false;
        }
        String style = kingdomStyle == null ? "" : kingdomStyle;
        if (!entry.style().isEmpty()) {
            // The mage towers of a style belong to the plan of its capital, like its guild houses.
            return entry.style().equals(style) && OF_A_STYLE.contains(entry.kind());
        }
        if (!TemplateCatalog.STYLES.contains(style)) {
            return true;
        }
        return !REPLACED.contains(entry.kind()) && !PLAIN_SMITHY.equals(entry.id());
    }

    /**
     * Whether a ruler of a kingdom of a style may put up a building of a template by its id: always for a template the table
     * does not know.
     */
    public static boolean usable(String templateId, String kingdomStyle) {
        return TemplateCatalog.get(templateId).map(entry -> usable(entry, kingdomStyle)).orElse(true);
    }

    /**
     * The quarter whose look a building of a kind a ruler puts up wears: a house that of its kind, the castle and the houses of
     * the guilds their own, every other building the look of the style.
     */
    public static BuildingLooks.Quarter quarter(TemplateCatalog.Kind kind) {
        return switch (kind) {
            case POOR_HOUSE -> BuildingLooks.Quarter.POOR;
            case CRAFT_HOUSE -> BuildingLooks.Quarter.CRAFT;
            case MERCHANT_HOUSE -> BuildingLooks.Quarter.MERCHANT;
            case NOBLE_HOUSE -> BuildingLooks.Quarter.NOBLE;
            case CASTLE -> BuildingLooks.Quarter.CASTLE;
            case GUILD -> BuildingLooks.Quarter.GUILD;
            case MAGE_TOWER -> BuildingLooks.Quarter.MAGES;
            default -> BuildingLooks.Quarter.PUBLIC;
        };
    }
}
