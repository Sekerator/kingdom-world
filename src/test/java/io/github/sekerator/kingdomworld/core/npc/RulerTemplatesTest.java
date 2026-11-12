package io.github.sekerator.kingdomworld.core.npc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a ruler of the world may put up: never what belongs to the plan of a capital, the houses, smithy and tavern of its own
 * style instead of the plain ones, never those of another style, and the plain templates as before in a kingdom without a style.
 */
class RulerTemplatesTest {
    private static final Set<TemplateCatalog.Kind> OF_THE_PLAN = EnumSet.of(TemplateCatalog.Kind.GATEHOUSE, TemplateCatalog.Kind.CORNER_TOWER,
            TemplateCatalog.Kind.HALL, TemplateCatalog.Kind.CASTLE, TemplateCatalog.Kind.DRILL_YARD, TemplateCatalog.Kind.STORAGE_YARD,
            TemplateCatalog.Kind.CASTLE_WARD, TemplateCatalog.Kind.BARBICAN, TemplateCatalog.Kind.ROYAL_BARRACKS, TemplateCatalog.Kind.GUILD,
            TemplateCatalog.Kind.TREASURY, TemplateCatalog.Kind.COURT, TemplateCatalog.Kind.INFIRMARY, TemplateCatalog.Kind.YARD,
            TemplateCatalog.Kind.WALL_GATE, TemplateCatalog.Kind.DECORATION);
    /** The kinds rulers left alone before the styles came. */
    private static final Set<TemplateCatalog.Kind> OLD = EnumSet.of(TemplateCatalog.Kind.GATEHOUSE, TemplateCatalog.Kind.CORNER_TOWER,
            TemplateCatalog.Kind.HALL, TemplateCatalog.Kind.CASTLE, TemplateCatalog.Kind.DRILL_YARD, TemplateCatalog.Kind.STORAGE_YARD);
    private static final Set<TemplateCatalog.Kind> HOUSES = EnumSet.of(TemplateCatalog.Kind.POOR_HOUSE, TemplateCatalog.Kind.CRAFT_HOUSE,
            TemplateCatalog.Kind.MERCHANT_HOUSE, TemplateCatalog.Kind.NOBLE_HOUSE);

    private static List<String> kingdomStyles() {
        List<String> styles = new ArrayList<>(TemplateCatalog.STYLES);
        styles.add("");
        return styles;
    }

    private static boolean usable(String id, String style) {
        return RulerTemplates.usable(TemplateCatalog.of(id), style);
    }

    @Test
    void whatBelongsToThePlanIsNeverUsable() {
        for (String style : kingdomStyles()) {
            for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
                if (OF_THE_PLAN.contains(entry.kind()) || entry.id().contains("_camp_")) {
                    assertFalse(RulerTemplates.usable(entry, style), entry.id() + " in a kingdom of the style '" + style + "'");
                }
            }
        }
        for (String style : TemplateCatalog.STYLES) {
            assertFalse(usable("kingdomworld:" + style + "_camp_barracks", style));
            assertFalse(usable("kingdomworld:" + style + "_camp_store", style));
            assertFalse(usable("kingdomworld:" + style + "_camp_tower", style));
            assertFalse(usable("kingdomworld:" + style + "_wall_gate", style));
            assertFalse(usable("kingdomworld:" + style + "_castle_small", style));
        }
    }

    @Test
    void aKingdomWithoutAStyleUsesNoStyledTemplate() {
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            if (!entry.style().isEmpty()) {
                assertFalse(RulerTemplates.usable(entry, ""), entry.id());
                assertFalse(RulerTemplates.usable(entry, null), entry.id());
                assertFalse(RulerTemplates.usable(entry, "baroque"), entry.id());
            }
        }
    }

    @Test
    void plainTemplatesStayAsBeforeWithoutAStyle() {
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            if (entry.style().isEmpty()) {
                assertEquals(!OLD.contains(entry.kind()), RulerTemplates.usable(entry, ""), entry.id());
                // A style the table does not know counts as none.
                assertEquals(!OLD.contains(entry.kind()), RulerTemplates.usable(entry, "baroque"), entry.id());
            }
        }
        assertTrue(usable("kingdomworld:small_house", ""));
        assertTrue(usable("kingdomworld:smithy", ""));
        assertTrue(usable("kingdomworld:tavern", ""));
    }

    @Test
    void aTeremKingdomBuildsTeremHouses() {
        String terem = "terem";
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            if (entry.style().equals(terem) && HOUSES.contains(entry.kind())) {
                assertTrue(RulerTemplates.usable(entry, terem), entry.id());
            }
            if (entry.style().equals("nordic")) {
                assertFalse(RulerTemplates.usable(entry, terem), entry.id());
            }
        }
        assertTrue(usable("kingdomworld:terem_house_izba", terem));
        assertTrue(usable("kingdomworld:terem_house_boyar", terem));
        assertTrue(usable("kingdomworld:terem_smithy", terem));
        assertTrue(usable("kingdomworld:terem_tavern", terem));
        assertFalse(usable("kingdomworld:nordic_house_red", terem));
        assertFalse(usable("kingdomworld:nordic_smithy", terem));
        assertFalse(usable("kingdomworld:nordic_tavern", terem));
        assertFalse(usable("kingdomworld:small_house", terem));
        assertFalse(usable("kingdomworld:large_house", terem));
        assertFalse(usable("kingdomworld:smithy", terem));
        assertFalse(usable("kingdomworld:tavern", terem));
        assertFalse(usable("kingdomworld:inn", terem));
        assertTrue(usable("kingdomworld:workshop", terem));
        assertTrue(usable("kingdomworld:bakery", terem));
        assertTrue(usable("kingdomworld:carpentry", terem));
        assertTrue(usable("kingdomworld:storehouse", terem));
        assertTrue(usable("kingdomworld:barn", terem));
        assertTrue(usable("kingdomworld:market_square", terem));
        assertTrue(usable("kingdomworld:well", terem));
        assertTrue(usable("kingdomworld:barracks", terem));
        assertTrue(usable("kingdomworld:mage_tower", terem));
    }

    @Test
    void guildHousesAndMageTowersOfAStyleBelongToThePlan() {
        for (String style : TemplateCatalog.STYLES) {
            for (TemplateCatalog.Rank rank : TemplateCatalog.Rank.values()) {
                TemplateCatalog.guildHouse(style, TemplateCatalog.Kind.GUILD, rank)
                        .ifPresent(entry -> assertFalse(RulerTemplates.usable(entry, style), entry.id()));
                TemplateCatalog.guildHouse(style, TemplateCatalog.Kind.MAGE_TOWER, rank)
                        .ifPresent(entry -> assertFalse(RulerTemplates.usable(entry, style), entry.id()));
            }
            assertFalse(usable("kingdomworld:" + style + "_guild_b", style));
            assertFalse(usable("kingdomworld:" + style + "_mage_tower_ss", style));
        }
    }

    @Test
    void everyStyleHasHousesASmithyAndATavernForItsRuler() {
        for (String style : TemplateCatalog.STYLES) {
            int houses = 0;
            int taverns = 0;
            boolean smithy = false;
            for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
                if (!RulerTemplates.usable(entry, style)) {
                    continue;
                }
                if (entry.role().equals("house")) {
                    houses++;
                    assertEquals(style, entry.style(), entry.id());
                }
                if (entry.role().equals("tavern")) {
                    taverns++;
                    assertEquals(style, entry.style(), entry.id());
                }
                smithy |= entry.id().equals("kingdomworld:" + style + "_smithy");
            }
            assertTrue(houses >= 2, style + " houses " + houses);
            assertTrue(taverns >= 1, style + " taverns " + taverns);
            assertTrue(smithy, style + " smithy");
        }
    }

    @Test
    void templatesTheTableDoesNotKnowAreUsable() {
        for (String style : kingdomStyles()) {
            assertTrue(RulerTemplates.usable("somepack:hut", style));
            assertTrue(RulerTemplates.usable("kingdomworld:no_such_template", style));
        }
        assertFalse(RulerTemplates.usable("kingdomworld:castle", ""));
        assertTrue(RulerTemplates.usable("kingdomworld:cottage", ""));
        assertFalse(RulerTemplates.usable("kingdomworld:cottage", "gothic"));
    }

    @Test
    void quarters() {
        assertEquals(BuildingLooks.Quarter.POOR, RulerTemplates.quarter(TemplateCatalog.Kind.POOR_HOUSE));
        assertEquals(BuildingLooks.Quarter.CRAFT, RulerTemplates.quarter(TemplateCatalog.Kind.CRAFT_HOUSE));
        assertEquals(BuildingLooks.Quarter.MERCHANT, RulerTemplates.quarter(TemplateCatalog.Kind.MERCHANT_HOUSE));
        assertEquals(BuildingLooks.Quarter.NOBLE, RulerTemplates.quarter(TemplateCatalog.Kind.NOBLE_HOUSE));
        assertEquals(BuildingLooks.Quarter.CASTLE, RulerTemplates.quarter(TemplateCatalog.Kind.CASTLE));
        assertEquals(BuildingLooks.Quarter.GUILD, RulerTemplates.quarter(TemplateCatalog.Kind.GUILD));
        assertEquals(BuildingLooks.Quarter.MAGES, RulerTemplates.quarter(TemplateCatalog.Kind.MAGE_TOWER));
        Set<TemplateCatalog.Kind> own = EnumSet.of(TemplateCatalog.Kind.POOR_HOUSE, TemplateCatalog.Kind.CRAFT_HOUSE,
                TemplateCatalog.Kind.MERCHANT_HOUSE, TemplateCatalog.Kind.NOBLE_HOUSE, TemplateCatalog.Kind.CASTLE, TemplateCatalog.Kind.GUILD,
                TemplateCatalog.Kind.MAGE_TOWER);
        for (TemplateCatalog.Kind kind : TemplateCatalog.Kind.values()) {
            if (!own.contains(kind)) {
                // Workshops, taverns and the plain buildings wear the look of the style.
                assertEquals(BuildingLooks.Quarter.PUBLIC, RulerTemplates.quarter(kind), kind.name());
            }
        }
        // Every quarter a ruler builds in gives a look of every style.
        for (String style : TemplateCatalog.STYLES) {
            for (TemplateCatalog.Kind kind : TemplateCatalog.Kind.values()) {
                BuildingLooks.of(style, "minecraft:oak", "red", RulerTemplates.quarter(kind), 42L);
            }
        }
    }
}
