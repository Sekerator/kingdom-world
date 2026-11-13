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

    private static boolean kind(NpcComposition.Plot plot, TemplateCatalog.Kind... kinds) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        for (TemplateCatalog.Kind wanted : kinds) {
            if (kind == wanted) {
                return true;
            }
        }
        return false;
    }

    private static boolean has(SettlementLayouts.Layout layout, TemplateCatalog.Kind... kinds) {
        return layout.plots().stream().anyMatch(plot -> kind(plot, kinds));
    }

    /** Whether every plot lies within a square of a half side around the middle. */
    private static void within(List<NpcComposition.Plot> plots, int half, String where) {
        for (NpcComposition.Plot plot : plots) {
            int[] box = NpcComposition.footprint(plot);
            assertTrue(Math.abs(box[0]) <= half && Math.abs(box[2]) <= half && Math.abs(box[1]) <= half && Math.abs(box[3]) <= half,
                    where + ": " + plot.template() + " reaches beyond " + half);
        }
    }

    @Test
    void villagesAndHamletsInEveryStyle() {
        for (String style : TemplateCatalog.STYLES) {
            for (long seed = 1; seed <= 12; seed++) {
                for (NpcLevel level : NpcLevel.values()) {
                    String where = style + " seed " + seed + " " + level;
                    SettlementLayouts.Layout village = SettlementLayouts.of(Tier.VILLAGE, level, true, style, seed, TemplateCatalog.Rank.B, RULES);
                    long houses = village.plots().stream().filter(SettlementLayoutsTest::house).count();
                    assertTrue(houses >= 3 && houses <= 6, where + ": " + houses + " houses in a village");
                    assertTrue(village.plots().stream().noneMatch(plot -> kind(plot, TemplateCatalog.Kind.TOWER)), where + ": a village has no tower");
                    assertTrue(has(village, TemplateCatalog.Kind.STOREHOUSE), where + ": a village has its barn");
                    assertTrue(has(village, TemplateCatalog.Kind.WELL), where + ": a village has its well");
                    assertTrue(village.fields().size() >= 2, where + ": a village has its fields");
                    apart(village.plots(), where);
                    within(village.plots(), SettlementLayouts.VILLAGE_REACH, where);
                    assertFalse(village.hall());
                    assertEquals(0, village.wall(), where + ": a village has no wall");
                    assertTrue(village.homes() > 0);
                    assertTrue(village.post(SettlementLayouts.ROUND).map(post -> post.points().size() >= 2).orElse(false),
                            where + ": no round of its watch");

                    SettlementLayouts.Layout hamlet = SettlementLayouts.of(Tier.HAMLET, level, true, style, seed, TemplateCatalog.Rank.B, RULES);
                    long few = hamlet.plots().stream().filter(SettlementLayoutsTest::house).count();
                    assertTrue(few >= 2 && few <= 3, where + ": " + few + " houses in a hamlet");
                    assertTrue(hamlet.plots().stream().anyMatch(plot -> plot.template().endsWith("well")), where + ": a hamlet has a well");
                    assertTrue(has(hamlet, TemplateCatalog.Kind.STOREHOUSE), where + ": a hamlet has a storehouse");
                    assertEquals(1, hamlet.fields().size(), where + ": a hamlet has a field");
                    apart(hamlet.plots(), where);
                    within(hamlet.plots(), SettlementLayouts.HAMLET_REACH, where);
                    for (NpcComposition.Plot plot : hamlet.plots()) {
                        // The rows look at the street: the north row south, the south row north.
                        assertEquals(plot.z() < 0 ? 0 : 2, plot.facing(), where + ": " + plot.template() + " looks away from the street");
                    }
                }
            }
        }
    }

    @Test
    void aSmallTownHasWhatATownLivesBy() {
        for (String style : TemplateCatalog.STYLES) {
            for (long seed = 1; seed <= 8; seed++) {
                for (NpcLevel level : NpcLevel.values()) {
                    String where = style + " seed " + seed + " " + level;
                    SettlementLayouts.Layout town = SettlementLayouts.of(Tier.TOWN, level, true, style, seed, TemplateCatalog.Rank.B, RULES);
                    assertTrue(town.hall(), where);
                    assertTrue(has(town, TemplateCatalog.Kind.MARKET), where + ": no market");
                    assertTrue(has(town, TemplateCatalog.Kind.TAVERN), where + ": no tavern");
                    assertTrue(has(town, TemplateCatalog.Kind.WORKSHOP), where + ": no workshop");
                    assertTrue(has(town, TemplateCatalog.Kind.STOREHOUSE), where + ": no storehouse");
                    assertTrue(has(town, TemplateCatalog.Kind.WELL), where + ": no well");
                    long houses = town.plots().stream().filter(SettlementLayoutsTest::house).count();
                    assertTrue(houses >= 6, where + ": " + houses + " houses");
                    assertTrue(town.homes() >= 15, where + ": " + town.homes() + " beds");
                    assertEquals(SettlementTowns.TOWN_WALL, town.wall(), where + ": every small town has its palisade");
                    assertEquals(2, town.gates().size(), where + ": a gate at each end of the street");
                    assertEquals(2, town.gatePosts().size(), where + ": a post inside each gate");
                    assertTrue(town.post(SettlementLayouts.ROUND).isPresent(), where + ": no round");
                    assertEquals(level == NpcLevel.LARGE ? 4 : 2, town.fields().size(), where + ": fields");
                    apart(town.plots(), where);
                    // Inside the palisade, a walk along it kept free, and on the land of the town.
                    within(town.plots(), SettlementTowns.TOWN_WALL - 2, where);
                    assertTrue(SettlementTowns.TOWN_WALL + 2 <= SettlementLayouts.land(Tier.TOWN) * 8, "the palisade lies on the land of the town");
                    for (int[] field : town.fields()) {
                        assertTrue(Math.abs(field[1]) - 6 > SettlementTowns.TOWN_WALL, where + ": a field inside the palisade");
                    }
                }
            }
        }
        SettlementLayouts.Layout plain = SettlementLayouts.of(Tier.TOWN, NpcLevel.MEDIUM, false, "", 5, TemplateCatalog.Rank.B, RULES);
        assertTrue(has(plain, TemplateCatalog.Kind.MARKET) && has(plain, TemplateCatalog.Kind.TAVERN), "a plain town has its market and tavern");
        apart(plain.plots(), "plain town");
        assertNull(plain.city());
    }

    @Test
    void aLargeCityIsTheDistrictsOfAMediumCapitalWithTheResidenceOfItsGovernor() {
        for (String style : TemplateCatalog.STYLES) {
            for (long seed = 1; seed <= 4; seed++) {
                SettlementLayouts.Layout city = SettlementLayouts.of(Tier.CITY, NpcLevel.LARGE, true, style, seed, TemplateCatalog.Rank.SS, RULES);
                String where = style + " seed " + seed;
                assertNotNull(city.city());
                assertTrue(city.hall());
                assertTrue(city.plots().stream().noneMatch(SettlementLayouts::palace), where + ": a city has no palace");
                NpcComposition.Plot residence = city.plots().get(0);
                assertEquals(SettlementLayouts.RESIDENCE, residence.quarter(), where + ": the residence goes up first");
                assertEquals(TemplateCatalog.Kind.CASTLE, TemplateCatalog.of(residence.template()).kind(), where + ": the residence is a castle");
                assertTrue(residence.x() == 0 && residence.z() == 0, where + ": the residence stands in the middle, round the town hall");
                // Buildings, without the gates and towers of its wall, its decorations and wells.
                long buildings = city.plots().stream().filter(SettlementLayouts::building).count();
                assertTrue(buildings >= 25 && buildings <= 40, where + ": " + buildings + " buildings");
                apart(city.plots(), where);
                assertTrue(city.wall() > 0 && city.gates().size() >= 2, where + ": a city has a wall with gates");
                assertEquals(city.gates().size(), city.gatePosts().size(), where + ": a post inside every gate");
                assertEquals(2 * city.gates().size(), city.post(SettlementLayouts.ROUND).orElseThrow().points().size(), where + ": the round");
                assertTrue(has(city, TemplateCatalog.Kind.MARKET) && has(city, TemplateCatalog.Kind.TAVERN) && has(city, TemplateCatalog.Kind.INFIRMARY)
                                && has(city, TemplateCatalog.Kind.STOREHOUSE)
                                && has(city, TemplateCatalog.Kind.BARRACKS_HALL, TemplateCatalog.Kind.BARRACKS),
                        where + ": a city lacks a building it lives by");
                assertFalse(city.works().isEmpty(), where + ": the places of its woodcutters and miners");
                // Everything of the city lies on its land of 11 by 11 chunks around its middle chunk.
                int land = SettlementLayouts.land(Tier.CITY) * 16 / 2;
                assertTrue(city.reach() + 6 <= land + 8, where + ": the ground reaches " + city.reach());
                within(city.plots(), land, where);
            }
        }
        SettlementLayouts.Layout plain = SettlementLayouts.of(Tier.CITY, NpcLevel.MEDIUM, false, "", 3, TemplateCatalog.Rank.B, RULES);
        assertTrue(plain.plots().stream().noneMatch(SettlementLayouts::palace));
        assertTrue(plain.plots().stream().anyMatch(plot -> SettlementLayouts.RESIDENCE.equals(plot.quarter()) && plot.x() == 0 && plot.z() == 0),
                "a city of districts keeps the hall of its plan as the residence of its governor");
        apart(plain.plots(), "districts");
    }

    @Test
    void theRoundOfACityPassesEveryGate() {
        List<int[]> route = SettlementLayouts.cityRound(List.of(new int[]{0, -86}, new int[]{86, 0}, new int[]{0, 86}, new int[]{-86, 0}));
        assertEquals(8, route.size());
        for (int[] point : route) {
            assertTrue(Math.abs(point[0]) <= 86 && Math.abs(point[1]) <= 86);
        }
        assertTrue(route.stream().anyMatch(point -> point[0] == 0 && point[1] == -78), "inside the north gate");
    }

    @Test
    void theLanesOfATownLieBehindTheRowsOfItsStreet() {
        assertTrue(SettlementTowns.lane(0, SettlementTowns.LANE_FRONT - 1));
        assertTrue(SettlementTowns.lane(10, -(SettlementTowns.LANE_FRONT - 2)));
        assertFalse(SettlementTowns.lane(0, 0));
        assertFalse(SettlementTowns.lane(0, SettlementTowns.LANE_FRONT));
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
