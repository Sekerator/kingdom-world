package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementLayoutsTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;

    private static void apart(List<NpcComposition.Plot> plots, String where) {
        for (int i = 0; i < plots.size(); i++) {
            int[] a = NpcComposition.footprint(plots.get(i));
            for (int j = i + 1; j < plots.size(); j++) {
                int[] b = NpcComposition.footprint(plots.get(j));
                boolean overlap = a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
                assertFalse(overlap, where + ": " + plots.get(i).template() + " and " + plots.get(j).template() + " overlap");
            }
        }
    }

    private static boolean house(NpcComposition.Plot plot) {
        TemplateCatalog.Entry entry = TemplateCatalog.of(plot.template());
        return entry.beds() > 0 && !TemplateCatalog.bunks(entry);
    }

    @Test
    void villagesAndHamletsInEveryStyle() {
        for (String style : TemplateCatalog.STYLES) {
            for (long seed = 1; seed <= 12; seed++) {
                for (NpcLevel level : NpcLevel.values()) {
                    String where = style + " seed " + seed + " " + level;
                    SettlementLayouts.Layout village = SettlementLayouts.of(Tier.VILLAGE, level, true, style, seed, TemplateCatalog.Rank.B, RULES);
                    assertTrue(village.plots().size() >= 4 && village.plots().size() <= 6, where + ": " + village.plots().size() + " plots");
                    assertTrue(village.plots().stream().noneMatch(plot -> plot.template().contains("tower")), where + ": a village has no tower");
                    assertTrue(village.plots().stream().anyMatch(SettlementLayoutsTest::house), where + ": a village has a house");
                    apart(village.plots(), where);
                    assertFalse(village.hall());
                    assertTrue(village.homes() > 0);

                    SettlementLayouts.Layout hamlet = SettlementLayouts.of(Tier.HAMLET, level, true, style, seed, TemplateCatalog.Rank.B, RULES);
                    long houses = hamlet.plots().stream().filter(SettlementLayoutsTest::house).count();
                    assertTrue(houses >= 2 && houses <= 3, where + ": " + houses + " houses in a hamlet");
                    assertTrue(hamlet.plots().stream().anyMatch(plot -> plot.template().endsWith("well")), where + ": a hamlet has a well");
                    apart(hamlet.plots(), where);
                    for (NpcComposition.Plot plot : hamlet.plots()) {
                        // The rows of a town look at its street: the north row south, the south row north.
                        assertEquals(plot.z() < 0 ? 0 : 2, plot.facing(), where + ": " + plot.template() + " looks away from the street");
                    }
                }
            }
        }
    }

    @Test
    void aTownIsATownOfItsKingdom() {
        SettlementLayouts.Layout small = SettlementLayouts.of(Tier.TOWN, NpcLevel.SMALL, true, "nordic", 5, TemplateCatalog.Rank.B, RULES);
        SettlementLayouts.Layout large = SettlementLayouts.of(Tier.TOWN, NpcLevel.LARGE, true, "nordic", 5, TemplateCatalog.Rank.B, RULES);
        assertEquals(StyledPlan.town(NpcLevel.SMALL, 5, "nordic", 0), small.plots());
        assertTrue(small.hall() && large.hall());
        assertEquals(0, small.wall(), "the towns of a small kingdom have no palisade");
        assertTrue(large.wall() > 0, "the towns of a large kingdom have a palisade");
        assertEquals(2, small.fields().size());
        apart(large.plots(), "large town");
        SettlementLayouts.Layout districts = SettlementLayouts.of(Tier.TOWN, NpcLevel.MEDIUM, false, "", 5, TemplateCatalog.Rank.B, RULES);
        assertEquals(CityPlan.town(NpcLevel.MEDIUM, 5, 0), districts.plots());
        assertNull(small.city());
    }

    @Test
    void aLargeCityIsTheDistrictsOfAMediumCapitalWithATownHall() {
        for (String style : TemplateCatalog.STYLES) {
            for (long seed = 1; seed <= 4; seed++) {
                SettlementLayouts.Layout city = SettlementLayouts.of(Tier.CITY, NpcLevel.LARGE, true, style, seed, TemplateCatalog.Rank.SS, RULES);
                String where = style + " seed " + seed;
                assertNotNull(city.city());
                assertTrue(city.hall());
                assertTrue(city.plots().stream().noneMatch(SettlementLayouts::palace), where + ": a city has no palace");
                // Buildings, without the gates and towers of its wall, its decorations and wells.
                long buildings = city.plots().stream().filter(SettlementLayouts::building).count();
                assertTrue(buildings >= 25 && buildings <= 40, where + ": " + buildings + " buildings");
                apart(city.plots(), where);
                assertTrue(city.wall() > 0 && !city.city().gates().isEmpty(), where + ": a city has a wall with gates");
                // Everything of the city lies on its land of 11 by 11 chunks around its middle chunk.
                int land = SettlementLayouts.land(Tier.CITY) * 16 / 2;
                assertTrue(city.reach() + 6 <= land + 8, where + ": the ground reaches " + city.reach());
                for (NpcComposition.Plot plot : city.plots()) {
                    int[] box = NpcComposition.footprint(plot);
                    assertTrue(Math.abs(box[0]) <= land && Math.abs(box[2]) <= land && Math.abs(box[1]) <= land && Math.abs(box[3]) <= land,
                            where + ": " + plot.template() + " off the land");
                }
            }
        }
        SettlementLayouts.Layout plain = SettlementLayouts.of(Tier.CITY, NpcLevel.MEDIUM, false, "", 3, TemplateCatalog.Rank.B, RULES);
        assertTrue(plain.plots().stream().noneMatch(SettlementLayouts::palace));
        apart(plain.plots(), "districts");
    }

    @Test
    void ticketsByRank() {
        assertEquals(169, SettlementLayouts.tickets(Tier.CITY));
        assertEquals(49, SettlementLayouts.tickets(Tier.TOWN));
        assertEquals(25, SettlementLayouts.tickets(Tier.VILLAGE));
        assertEquals(16, SettlementLayouts.tickets(Tier.HAMLET));
        for (Tier tier : List.of(Tier.CITY, Tier.TOWN, Tier.VILLAGE, Tier.HAMLET)) {
            List<long[]> square = SettlementLayouts.square(1000, -2000, SettlementLayouts.ticketSide(tier));
            assertEquals(SettlementLayouts.tickets(tier), square.size());
            Set<Long> keys = new HashSet<>();
            boolean middle = false;
            for (long[] chunk : square) {
                keys.add(chunk[0] * 100_000 + chunk[1]);
                middle |= chunk[0] == 1000 >> 4 && chunk[1] == -2000 >> 4;
            }
            assertEquals(square.size(), keys.size());
            assertTrue(middle, tier + ": the square holds the middle chunk");
        }
        assertTrue(SettlementLayouts.tickets(Tier.CITY) <= 169, "a settlement holds 169 chunks at most");
    }

    @Test
    void aSettlerIsTheSameForItsSlot() {
        assertEquals(Settler.mix(42, 3), Settler.mix(42, 3));
        Set<Long> seen = new HashSet<>();
        for (int slot = 0; slot < 200; slot++) {
            assertTrue(seen.add(Settler.mix(7, slot)), "slot " + slot);
        }
    }

    @Test
    void stagesInOrder() {
        assertEquals(SettlementStage.LOAD, SettlementStage.byId(""));
        assertEquals(SettlementStage.GUARDS, SettlementStage.byId("guards"));
        assertEquals(SettlementStage.DONE, SettlementStage.LAW.next());
        assertEquals(SettlementStage.DONE, SettlementStage.DONE.next());
    }
}
