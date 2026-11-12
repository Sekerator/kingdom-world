package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementPlannerTest {
    private static final SettlementRules RULES = SettlementRules.DEFAULT;

    /**
     * The provinces of a world of three large, two medium and three small states.
     */
    static ProvincePlan provinces(long seed) {
        SurveyRaster raster = SyntheticLand.world(seed, StateRules.RADIUS, 0.08);
        StatePlan states = StatePlanner.plan(raster, StateRules.defaults(0, 0).withQuota(3, 2, 3), seed);
        return ProvincePlanner.plan(states, ProvinceRules.DEFAULT, seed);
    }

    @Test
    void everyStateGetsTheSettlementsOfItsSizeOnAHundredSeeds() {
        int[] seen = new int[NpcLevel.values().length];
        long wanted = 0;
        long placed = 0;
        for (long world = 1; world <= 4; world++) {
            ProvincePlan provinces = provinces(world * 1000);
            for (long seed = 0; seed < 25; seed++) {
                SettlementPlan plan = SettlementPlanner.plan(provinces, RULES, world * 1000 + seed);
                for (PlannedState state : provinces.states().states()) {
                    int s = state.index();
                    NpcLevel level = state.level();
                    seen[level.ordinal()]++;
                    String where = "world " + world + ", seed " + seed + ", state " + s + " (" + level.id() + ")";
                    assertEquals(1, plan.count(s, Tier.CAPITAL), where);
                    assertInRange(plan.count(s, Tier.CITY), RULES.cities(level), where + " cities");
                    assertInRange(plan.count(s, Tier.TOWN), RULES.towns(level), where + " towns");
                    int children = plan.count(s, Tier.VILLAGE) + plan.count(s, Tier.HAMLET);
                    assertInRange(children, RULES.villages(level), where + " villages and hamlets");
                    for (Tier tier : Tier.values()) {
                        assertTrue(plan.count(s, tier) <= plan.wanted(s, tier), where + " " + tier.id());
                        wanted += plan.wanted(s, tier);
                        placed += plan.count(s, tier);
                    }
                    int villages = plan.count(s, Tier.VILLAGE);
                    assertTrue(Math.abs(villages - children * RULES.villageShare()) <= 1.5, where + ": " + villages + " villages of " + children);
                }
            }
        }
        for (NpcLevel level : NpcLevel.values()) {
            assertTrue(seen[level.ordinal()] > 0, "states of every size were planned");
        }
        assertTrue(placed >= 0.995 * wanted, placed + " of " + wanted + " settlements placed");
    }

    @Test
    void settlementsKeepTheirPlacesAndDistances() {
        ProvincePlan provinces = provinces(77);
        SettlementPlan plan = SettlementPlanner.plan(provinces, RULES, 77);
        SurveyRaster raster = plan.raster();
        RefinedLand land = BorderRefiner.refine(provinces, BorderRefinerTest.probes(raster, BorderRefiner.probeCells(provinces)), 77);
        List<SettlementSlot> slots = plan.slots();
        int[] children = new int[slots.size()];
        boolean[] cityIn = new boolean[provinces.provinces().size() + 1];
        for (SettlementSlot slot : slots) {
            assertEquals(slot.province(), provinces.provinceAt(slot.cell()));
            assertEquals(slot.state(), provinces.stateOf(slot.province()));
            PlannedProvince province = provinces.province(slot.province());
            assertTrue(province.kind() == ProvinceKind.HEART || province.kind() == ProvinceKind.CORE, "no settlement in the frontier or the wild");
            if (slot.tier() == Tier.CAPITAL) {
                assertEquals(-1, slot.parent());
                assertEquals(provinces.throneCell(slot.state()), slot.cell());
                assertEquals(slot.index(), plan.seat(slot.province()), "the capital is the seat of the heart");
                continue;
            }
            assertTrue(standsOn(slot.tier(), raster.terrain(slot.cell())), "settlements stand on land");
            assertTrue(slot.parent() >= 0 && plan.slot(slot.parent()).tier().above(slot.tier()), "a settlement belongs to a higher one");
            if (slot.tier() == Tier.CITY) {
                assertEquals(ProvinceKind.CORE, province.kind());
                assertTrue(!cityIn[slot.province()], "one city in a province");
                cityIn[slot.province()] = true;
            }
            if (slot.tier() == Tier.VILLAGE || slot.tier() == Tier.HAMLET) {
                SettlementSlot parent = plan.slot(slot.parent());
                double from = Math.hypot(slot.x() - parent.x(), slot.z() - parent.z());
                assertTrue(from >= RULES.childMin() && from <= RULES.childMax(), "a village " + from + " blocks from its settlement");
                children[slot.parent()]++;
            }
            assertBorderAway(land, slot.x(), slot.z(), slot.province());
            assertTrue(slot.alternates().size() <= RULES.alternates());
            for (SettlementSlot.Spot spot : slot.alternates()) {
                assertTrue(standsOn(slot.tier(), raster.terrain(spot.cell())));
                assertEquals(slot.state(), provinces.stateOf(spot.province()));
                if (slot.tier() == Tier.CITY || slot.tier() == Tier.TOWN) {
                    assertEquals(slot.province(), spot.province());
                }
                assertBorderAway(land, spot.x(), spot.z(), spot.province());
                for (SettlementSlot other : slots) {
                    if (other.index() != slot.index()) {
                        assertTrue(Math.hypot(other.x() - spot.x(), other.z() - spot.z()) >= RULES.apart(slot.tier(), other.tier()),
                                "an alternate keeps the distances");
                    }
                }
            }
        }
        for (int count : children) {
            assertTrue(count <= RULES.childrenMax());
        }
        for (int a = 0; a < slots.size(); a++) {
            for (int b = a + 1; b < slots.size(); b++) {
                SettlementSlot one = slots.get(a);
                SettlementSlot two = slots.get(b);
                double distance = Math.hypot(one.x() - two.x(), one.z() - two.z());
                assertTrue(distance >= RULES.apart(one.tier(), two.tier()),
                        one.tier().id() + " " + a + " and " + two.tier().id() + " " + b + " only " + distance + " apart");
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameSettlements() {
        ProvincePlan provinces = provinces(5);
        assertEquals(SettlementPlanner.plan(provinces, RULES, 5).slots(), SettlementPlanner.plan(provinces, RULES, 5).slots());
    }

    /**
     * Every chunk within 64 blocks of a place lies in its province, however the borders were drawn.
     */
    private static void assertBorderAway(RefinedLand land, int x, int z, int province) {
        for (int chunkX = (x - 64) >> 4; chunkX <= (x + 64) >> 4; chunkX++) {
            for (int chunkZ = (z - 64) >> 4; chunkZ <= (z + 64) >> 4; chunkZ++) {
                double dx = Math.max(0, Math.max((chunkX << 4) - x, x - ((chunkX << 4) + 16)));
                double dz = Math.max(0, Math.max((chunkZ << 4) - z, z - ((chunkZ << 4) + 16)));
                if (Math.hypot(dx, dz) < 64) {
                    assertEquals(province, land.provinceAt(chunkX, chunkZ), "the border keeps 64 blocks from " + x + ", " + z);
                }
            }
        }
    }

    private static boolean standsOn(Tier tier, TerrainClass terrain) {
        return terrain.settles() || ((tier == Tier.VILLAGE || tier == Tier.HAMLET) && terrain == TerrainClass.HILLS);
    }

    private static void assertInRange(int value, int[] range, String what) {
        assertTrue(value >= range[0] && value <= range[1], what + ": " + value + " not in " + range[0] + "-" + range[1]);
    }
}
