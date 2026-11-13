package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.land.SettlementRules;
import io.github.sekerator.kingdomworld.core.land.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementGarrisonTest {
    /** Posts of the guard of a large styled capital (gates, stores, palace, square, rounds, castle) and two of its army in reserve. */
    private static final int CAPITAL_POSTS = 15;

    @Test
    void guardByRank() {
        assertEquals(8, SettlementLife.guards(Tier.CITY));
        assertEquals(4, SettlementLife.guards(Tier.TOWN));
        assertEquals(0, SettlementLife.guards(Tier.VILLAGE), "a village keeps no guard by number");
        assertEquals(0, SettlementLife.guards(Tier.HAMLET));
        assertEquals(2, SettlementLife.militia(Tier.VILLAGE, 25), "a village keeps its militia as a number");
    }

    @Test
    void everySettlementHasItsOwnGuardOrWatchInPerson() {
        assertEquals(8, SettlementLife.watch(Tier.CITY));
        assertEquals(4, SettlementLife.watch(Tier.TOWN));
        assertEquals(2, SettlementLife.watch(Tier.VILLAGE), "two watchmen in a village");
        assertEquals(1, SettlementLife.watch(Tier.HAMLET), "a watchman in a hamlet");
        assertEquals(0, SettlementLife.watch(Tier.CAPITAL), "the capital has its own garrison");
        assertEquals(3, SettlementLife.squads(Tier.CITY), "the gate, the round and the town hall of a large city");
        assertEquals(2, SettlementLife.squads(Tier.TOWN), "the gate and the round of a small town");
        assertEquals(1, SettlementLife.squads(Tier.VILLAGE));
    }

    @Test
    void aLargeStateWithAllItsSettlementsStaysWithinTheLimitsOfAKingdom() {
        NpcRules rules = NpcRules.DEFAULT;
        int[] cities = SettlementRules.DEFAULT.cities(NpcLevel.LARGE);
        int[] towns = SettlementRules.DEFAULT.towns(NpcLevel.LARGE);
        int[] villages = SettlementRules.DEFAULT.villages(NpcLevel.LARGE);
        int army = rules.armyMost(NpcLevel.LARGE);
        int[] most = SettlementLife.garrison(rules.guards(NpcLevel.LARGE), CAPITAL_POSTS, cities[1], towns[1], villages[1], army,
                rules.army(NpcLevel.LARGE), 20);
        // Every village counted with two watchmen, though a hamlet keeps one.
        assertTrue(most[0] <= ArmyRules.WORLD_SOLDIERS, most[0] + " soldiers in a large state");
        assertTrue(most[1] <= ArmyRules.WORLD_SQUADS, most[1] + " squads in a large state");
        for (NpcLevel level : NpcLevel.values()) {
            int[] state = SettlementLife.garrison(rules.guards(level), CAPITAL_POSTS, SettlementRules.DEFAULT.cities(level)[1],
                    SettlementRules.DEFAULT.towns(level)[1], SettlementRules.DEFAULT.villages(level)[1], rules.armyMost(level), rules.army(level), 20);
            assertTrue(state[0] <= ArmyRules.WORLD_SOLDIERS && state[1] <= ArmyRules.WORLD_SQUADS, level + ": " + state[0] + " soldiers, "
                    + state[1] + " squads");
        }
    }
}
