package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveSelectionTest {
    private static final String DIM = "minecraft:overworld";

    @Test
    void distanceUsesTheNearestAnchorAndPlayerOfTheSameDimension() {
        List<WorldPos> anchors = List.of(new WorldPos(DIM, 0, 64, 0), new WorldPos(DIM, 160, 64, 0));
        List<ActiveSelection.PlayerSpot> players = List.of(new ActiveSelection.PlayerSpot(DIM, 12, 3),
                new ActiveSelection.PlayerSpot("minecraft:the_nether", 0, 0));
        assertEquals(3, ActiveSelection.distance(anchors, players));
        assertEquals(Integer.MAX_VALUE, ActiveSelection.distance(anchors, List.of(new ActiveSelection.PlayerSpot("other", 0, 0))));
    }

    @Test
    void anchorsAreHomeAndWorkplaceOrWhereTheCitizenIs() {
        Citizen citizen = new Citizen(UUID.randomUUID(), UUID.randomUUID(), 1, Gender.MALE, 0, 0L, new WorldPos(DIM, 5, 64, 5));
        assertEquals(List.of(new WorldPos(DIM, 5, 64, 5)), ActiveSelection.anchors(citizen));
    }

    @Test
    void nearestPlayersWinWhenOverTheLimit() {
        List<ActiveSelection.Candidate> candidates = new ArrayList<>();
        UUID far = UUID.randomUUID();
        UUID forced = UUID.randomUUID();
        candidates.add(new ActiveSelection.Candidate(far, 7, false));
        candidates.add(new ActiveSelection.Candidate(forced, Integer.MAX_VALUE, true));
        List<UUID> near = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            near.add(id);
            candidates.add(new ActiveSelection.Candidate(id, i, false));
        }
        List<UUID> selected = ActiveSelection.select(candidates, 6, 4);
        assertEquals(4, selected.size());
        assertEquals(forced, selected.get(0), "forced citizens come first");
        assertEquals(near.subList(0, 3), selected.subList(1, 4), "then the nearest");
        assertTrue(!ActiveSelection.select(candidates, 6, 100).contains(far), "too far for a body");
        assertEquals(7, ActiveSelection.select(candidates, 7, 100).size());
        assertTrue(ActiveSelection.select(candidates, 6, 0).isEmpty());
    }
}
