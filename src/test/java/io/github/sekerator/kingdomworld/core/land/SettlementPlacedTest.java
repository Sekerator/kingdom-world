package io.github.sekerator.kingdomworld.core.land;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementPlacedTest {
    @Test
    void theWorldsChoicesMakeThePlanOfThePlacesKept() {
        ProvincePlan provinces = SettlementPlannerTest.provinces(21);
        SettlementPlan plan = SettlementPlanner.plan(provinces, SettlementRules.DEFAULT, 21);
        int n = plan.slots().size();
        int[] choices = new int[n];
        int left = 0;
        int moved = -1;
        for (int i = 0; i < n; i++) {
            SettlementSlot slot = plan.slot(i);
            if (slot.tier() == Tier.CAPITAL) {
                continue;
            }
            if (moved < 0 && !slot.alternates().isEmpty()) {
                choices[i] = 1;
                moved = i;
            } else if (i % 5 == 0) {
                choices[i] = -1;
                left++;
            }
        }
        assertTrue(moved >= 0, "some place has an alternate");
        SettlementPlan placed = plan.placed(choices);
        assertEquals(n - left, placed.slots().size());
        int at = 0;
        for (int i = 0; i < n; i++) {
            if (choices[i] < 0) {
                continue;
            }
            SettlementSlot before = plan.slot(i);
            SettlementSlot after = placed.slot(at);
            assertEquals(at, after.index());
            assertEquals(before.tier(), after.tier());
            assertEquals(before.state(), after.state());
            if (i == moved) {
                SettlementSlot.Spot spot = before.alternates().get(0);
                assertEquals(spot.x(), after.x());
                assertEquals(spot.z(), after.z());
                assertEquals(spot.province(), after.province());
            } else {
                assertEquals(before.x(), after.x());
                assertEquals(before.z(), after.z());
            }
            // A settlement left out hands its children to the settlement it belonged to.
            if (after.parent() >= 0) {
                SettlementSlot parent = placed.slot(after.parent());
                assertEquals(after.state(), parent.state());
                assertTrue(parent.tier().ordinal() < after.tier().ordinal(), "a child belongs to a settlement of a higher rank");
            } else {
                assertEquals(Tier.CAPITAL, after.tier());
            }
            assertTrue(after.alternates().isEmpty());
            at++;
        }
        for (PlannedProvince province : provinces.provinces()) {
            int seat = placed.seat(province.id());
            if (seat >= 0) {
                assertEquals(province.id(), placed.slot(seat).province());
            }
        }
        // Roads are laid between the places kept only.
        RoadPlan roads = RoadPlanner.plan(placed);
        for (PlannedEdge edge : roads.edges()) {
            assertTrue(edge.from() < placed.slots().size() && edge.to() < placed.slots().size());
        }
    }

    @Test
    void aChoiceForEverySettlement() {
        SettlementPlan plan = SettlementPlanner.plan(SettlementPlannerTest.provinces(22), SettlementRules.DEFAULT, 22);
        assertThrows(IllegalArgumentException.class, () -> plan.placed(new int[plan.slots().size() - 1]));
        int[] all = new int[plan.slots().size()];
        assertEquals(plan.slots().size(), plan.placed(all).slots().size());
        int[] beyond = new int[plan.slots().size()];
        Arrays.fill(beyond, 99);
        assertThrows(IllegalArgumentException.class, () -> plan.placed(beyond));
    }
}
