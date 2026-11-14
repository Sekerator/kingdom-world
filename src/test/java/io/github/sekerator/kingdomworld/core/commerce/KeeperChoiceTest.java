package io.github.sekerator.kingdomworld.core.commerce;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who keeps a shop: townspeople only, the people of the house first, then the nearest, no more than the room of the place, and a
 * family in the evening when a house has nobody else.
 */
class KeeperChoiceTest {
    @Test
    void townspeopleOnlyAndNoMoreThanTheRoom() {
        List<KeeperChoice.Candidate> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(new KeeperChoice.Candidate(new UUID(0, i), i % 2 == 0, false, i));
        }
        KeeperChoice.Choice choice = KeeperChoice.choose(many, 2, false);
        assertEquals(List.of(new UUID(0, 0), new UUID(0, 2)), choice.keepers());
        assertEquals(1, KeeperChoice.choose(many, 1, false).keepers().size());
        assertTrue(KeeperChoice.choose(List.of(), 2, true).keepers().isEmpty());
        assertFalse(KeeperChoice.choose(List.of(), 2, true).any(), "an empty house keeps no shop");
    }

    @Test
    void theOrderIsTheSameEveryTime() {
        List<KeeperChoice.Candidate> twins = List.of(new KeeperChoice.Candidate(new UUID(0, 9), true, false, 4),
                new KeeperChoice.Candidate(new UUID(0, 3), true, false, 4));
        assertEquals(List.of(new UUID(0, 3)), KeeperChoice.choose(twins, 1, false).keepers(), "equally near: by id");
    }
}
