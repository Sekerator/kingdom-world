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
        assertEquals(0, SettlementLife.guards(Tier.VILLAGE));
        assertEquals(0, SettlementLife.guards(Tier.HAMLET));
        assertEquals(2, SettlementLife.militia(Tier.VILLAGE, 25), "a village keeps its militia as a number");
    }

    @Test
    void aLargeStateWithAllItsSettlementsStaysWithinTheLimitsOfAKingdom() {
        NpcRules rules = NpcRules.DEFAULT;
        int[] cities = SettlementRules.DEFAULT.cities(NpcLevel.LARGE);
        int[] towns = SettlementRules.DEFAULT.towns(NpcLevel.LARGE);
        int army = rules.armyMost(NpcLevel.LARGE);
        int[] most = SettlementLife.garrison(rules.guards(NpcLevel.LARGE), CAPITAL_POSTS, cities[1], towns[1], army, rules.army(NpcLevel.LARGE), 20);
        assertTrue(most[0] <= 380, most[0] + " soldiers in a large state");
        // The most towns of the rules give a post each: 47 squads at the very most, within the limit of a kingdom.
        assertTrue(most[1] <= 48, most[1] + " squads in a large state");
        assertTrue(most[0] < ArmyRules.WORLD_SOLDIERS && most[1] < ArmyRules.WORLD_SQUADS, "the limits of a kingdom of the world hold");
        for (NpcLevel level : NpcLevel.values()) {
            int[] state = SettlementLife.garrison(rules.guards(level), CAPITAL_POSTS, SettlementRules.DEFAULT.cities(level)[1],
                    SettlementRules.DEFAULT.towns(level)[1], rules.armyMost(level), rules.army(level), 20);
            assertTrue(state[0] <= ArmyRules.WORLD_SOLDIERS && state[1] <= ArmyRules.WORLD_SQUADS, level + ": " + state[0] + " soldiers, "
                    + state[1] + " squads");
        }
    }
}
