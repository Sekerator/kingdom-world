package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The royal armies have their own budget of bodies within the limit of the world, and never take the bodies of citizens.
 */
class ActiveSelectionBudgetTest {
    private static final ActiveSelection.Budget BUDGET = new ActiveSelection.Budget(30, 90);

    private static List<ActiveSelection.Candidate> some(int count, int distance, String group) {
        List<ActiveSelection.Candidate> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ActiveSelection.Candidate(UUID.randomUUID(), distance, false, group));
        }
        return list;
    }

    private static Set<UUID> ids(List<ActiveSelection.Candidate> candidates) {
        Set<UUID> ids = new HashSet<>();
        candidates.forEach(candidate -> ids.add(candidate.id()));
        return ids;
    }

    @Test
    void anArmyNearerThanTheCitizensTakesNoneOfTheirBodies() {
        List<ActiveSelection.Candidate> citizens = some(280, 5, "");
        List<ActiveSelection.Candidate> camp = some(100, 1, "k:camp0");
        List<ActiveSelection.Candidate> all = new ArrayList<>(camp);
        all.addAll(citizens);
        List<UUID> chosen = ActiveSelection.select(all, 8, 300, BUDGET);
        assertEquals(300, chosen.size());
        assertTrue(chosen.containsAll(ids(citizens)), "A citizen lost its body to the army");
        assertEquals(20, chosen.stream().filter(ids(camp)::contains).count());
    }

    @Test
    void everyCampAndAllArmiesKeepToTheirBudget() {
        List<ActiveSelection.Candidate> citizens = some(100, 3, "");
        List<ActiveSelection.Candidate> first = some(60, 1, "a:camp0");
        List<ActiveSelection.Candidate> second = some(60, 2, "a:camp1");
        List<ActiveSelection.Candidate> away = some(60, 2, "b:away");
        List<ActiveSelection.Candidate> all = new ArrayList<>(citizens);
        all.addAll(first);
        all.addAll(second);
        all.addAll(away);
        List<UUID> chosen = ActiveSelection.select(all, 8, 300, BUDGET);
        assertTrue(chosen.containsAll(ids(citizens)));
        assertEquals(30, chosen.stream().filter(ids(first)::contains).count());
        assertEquals(30, chosen.stream().filter(ids(second)::contains).count());
        assertEquals(30, chosen.stream().filter(ids(away)::contains).count());
        assertEquals(190, chosen.size());
        // The citizens come first in the order of spawning too.
        assertTrue(ids(citizens).containsAll(chosen.subList(0, 100)));
    }

    @Test
    void theNearestOfAGroupComeFirstAndAForcedOneAlways() {
        List<ActiveSelection.Candidate> far = some(40, 6, "k:camp0");
        List<ActiveSelection.Candidate> called = some(10, -1, "k:camp0");
        ActiveSelection.Candidate forced = new ActiveSelection.Candidate(UUID.randomUUID(), Integer.MAX_VALUE, true, "k:camp0");
        List<ActiveSelection.Candidate> all = new ArrayList<>(far);
        all.addAll(called);
        all.add(forced);
        List<UUID> chosen = ActiveSelection.select(all, 8, 300, BUDGET);
        assertTrue(chosen.contains(forced.id()));
        assertTrue(chosen.containsAll(ids(called)), "Soldiers an alarm called wait behind the others");
        assertEquals(31, chosen.size());
        // Without a budget every candidate in reach gets a body.
        assertEquals(51, ActiveSelection.select(all, 8, 300).size());
    }
}
