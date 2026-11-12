package io.github.sekerator.kingdomworld.core.army;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The squad of a soldier and the soldiers and squads of a kingdom are kept at hand and stay right through every change.
 */
class ArmyBooksTest {
    private static Soldier soldier(UUID kingdom) {
        return new Soldier(UUID.randomUUID(), kingdom, 0, 0, 0, 0L);
    }

    @Test
    void theSquadOfASoldierFollowsEveryChange() {
        UUID kingdom = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ArmyState army = new ArmyState();
        List<Soldier> ours = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Soldier soldier = soldier(i < 4 ? kingdom : other);
            army.restoreSoldier(soldier);
            ours.add(soldier);
        }
        army.restoreSquad(new ArmySquad(1, kingdom, List.of(ours.get(0).citizenId(), ours.get(1).citizenId()), null, 0, Order.NONE));
        army.restoreSquad(new ArmySquad(2, other, List.of(ours.get(4).citizenId()), null, 0, Order.NONE));
        army.restoreSquad(new ArmySquad(3, kingdom, List.of(ours.get(2).citizenId()), null, 0, Order.NONE));

        assertEquals(1L, army.squadOf(ours.get(1).citizenId()).orElseThrow().id());
        assertTrue(army.squadOf(ours.get(3).citizenId()).isEmpty());
        assertEquals(List.of(1L, 3L), army.squadsOf(kingdom).stream().map(ArmySquad::id).toList());
        assertEquals(List.of(ours.get(0), ours.get(1), ours.get(2), ours.get(3)), army.soldiersOf(kingdom));

        // Leaving: the squad keeps the others; the last one out ends the squad.
        army.leaveSquad(ours.get(0).citizenId());
        assertTrue(army.squadOf(ours.get(0).citizenId()).isEmpty());
        assertEquals(1L, army.squadOf(ours.get(1).citizenId()).orElseThrow().id());
        army.forgetSoldier(ours.get(1).citizenId());
        assertTrue(army.squad(1).isEmpty() && army.squadOf(ours.get(1).citizenId()).isEmpty());
        assertEquals(List.of(3L), army.squadsOf(kingdom).stream().map(ArmySquad::id).toList());
        assertEquals(List.of(ours.get(0), ours.get(2), ours.get(3)), army.soldiersOf(kingdom));

        // A kingdom that is gone takes its soldiers and squads along, the other kingdom keeps its own.
        army.forgetKingdom(kingdom);
        assertTrue(army.soldiersOf(kingdom).isEmpty() && army.squadsOf(kingdom).isEmpty());
        assertTrue(army.squadOf(ours.get(2).citizenId()).isEmpty());
        assertEquals(2L, army.squadOf(ours.get(4).citizenId()).orElseThrow().id());
        assertEquals(List.of(ours.get(4), ours.get(5)), army.soldiersOf(other));
    }
}
