package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementLifeTest {
    private static final UUID KINGDOM = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void peopleGrowTowardTheHousesWhileFed() {
        SettlementLife.Day day = SettlementLife.day(Tier.TOWN, 30, 40, 300, 50, 130);
        assertEquals(31, day.population(), "a fiftieth of 30, at least one");
        assertEquals(51, day.prosperity());
        assertEquals(4, day.guards());
        assertEquals(0, day.militia());
        // Fed every day, the town fills its houses and stops there.
        int people = 30;
        int food = 300;
        int prosperity = 50;
        for (int i = 0; i < 60; i++) {
            day = SettlementLife.day(Tier.TOWN, people, 40, food, prosperity, 130);
            people = day.population();
            food = day.food();
            prosperity = day.prosperity();
        }
        assertEquals(40, people);
        assertTrue(food <= 40 * SettlementLife.MAX_FOOD_DAYS, "food is kept within thirty days: " + food);
        assertTrue(prosperity <= SettlementLife.MAX_PROSPERITY);
    }

    @Test
    void hungerTakesPeopleAndContentment() {
        // Land that yields half of what the people eat, with nothing in store.
        SettlementLife.Day day = SettlementLife.day(Tier.VILLAGE, 20, 25, 0, 50, 50);
        assertEquals(19, day.population());
        assertEquals(47, day.prosperity());
        assertEquals(0, day.food());
        assertEquals(1, day.militia(), "a tenth of the people of a village");
        int people = 20;
        int prosperity = 50;
        for (int i = 0; i < 200; i++) {
            day = SettlementLife.day(Tier.VILLAGE, people, 25, 0, prosperity, 50);
            people = day.population();
            prosperity = day.prosperity();
        }
        assertEquals(0, people);
        assertEquals(0, prosperity);
    }

    @Test
    void aStoreCarriesPeopleThroughABadYield() {
        SettlementLife.Day day = SettlementLife.day(Tier.VILLAGE, 20, 25, 100, 50, 50);
        assertEquals(21, day.population(), "the store feeds what the land lacks");
        assertEquals(90, day.food());
    }

    @Test
    void housesByRankOnAHundredSeeds() {
        for (Tier tier : List.of(Tier.CITY, Tier.TOWN, Tier.VILLAGE, Tier.HAMLET)) {
            int[] range = SettlementLife.people(tier);
            int least = Integer.MAX_VALUE;
            int most = 0;
            for (long seed = 0; seed < 100; seed++) {
                int housing = SettlementLife.housing(tier, seed * 7919);
                assertTrue(housing >= range[0] && housing <= range[1], tier + " " + housing);
                least = Math.min(least, housing);
                most = Math.max(most, housing);
                int people = SettlementLife.startingPopulation(housing);
                assertTrue(people > 0 && people <= housing);
            }
            // A hundred seeds spread over the range.
            assertTrue(most - least >= (range[1] - range[0]) / 2, tier + " " + least + "-" + most);
        }
        assertEquals(0, SettlementLife.housing(Tier.CAPITAL, 5), "a capital is counted by its own people");
        assertEquals(8, SettlementLife.guards(Tier.CITY));
    }

    @Test
    void aSettlementFoundedHasItsNumbersAndLivesItsDays() {
        NpcState state = new NpcState();
        int[] changes = {0};
        state.setChangeListener(() -> changes[0]++);
        Settlement town = state.addSettlement(KINGDOM, 7, 0, Tier.TOWN, 100, -200, 42, new NpcNames.Name("Ostbrook", "Остбрук"));
        long version = state.settlementsVersion();
        town.found(40, 30, 125);
        assertTrue(state.settlementsVersion() > version, "a change of a settlement is seen");
        assertEquals(Settlement.Status.DATA, town.status());
        assertEquals(30 * SettlementLife.START_FOOD_DAYS, town.food());
        assertEquals(4, town.guards());
        assertEquals(125, town.fertility());
        town.live(SettlementLife.day(town.tier(), town.population(), town.housing(), town.food(), town.prosperity(), town.fertility()), 3);
        assertEquals(31, town.population());
        assertEquals(3, town.lastDay());
        assertTrue(changes[0] > 0);
        assertEquals(List.of(town), state.settlementsOf(KINGDOM));
        assertTrue(state.settlementNames().contains("Ostbrook"));
    }

    @Test
    void aVillageBecomesASmallTown() {
        NpcState state = new NpcState();
        Settlement village = state.addSettlement(KINGDOM, 1, 0, Tier.VILLAGE, 0, 0, 9, new NpcNames.Name("Brook", "Брук"));
        village.found(20, 15, 120);
        long version = state.settlementsVersion();
        village.promote(Tier.TOWN);
        assertEquals(Tier.TOWN, village.tier());
        assertEquals(4, village.guards());
        assertEquals(0, village.militia());
        int[] range = SettlementLife.people(Tier.TOWN);
        assertTrue(village.housing() >= range[0] && village.housing() <= range[1], "houses of a town: " + village.housing());
        assertEquals(15, village.population(), "its people grow day by day");
        assertTrue(state.settlementsVersion() > version);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> village.promote(Tier.VILLAGE));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> village.promote(Tier.CAPITAL));
    }

    @Test
    void aSettlementGoesToAnotherKingdomWithItsProvince() {
        UUID winner = UUID.fromString("00000000-0000-4000-8000-000000000002");
        NpcState state = new NpcState();
        int[] changes = {0};
        state.setChangeListener(() -> changes[0]++);
        Settlement town = state.addSettlement(KINGDOM, 7, 0, Tier.TOWN, 100, -200, 42, new NpcNames.Name("Ostbrook", "Остбрук"));
        town.found(40, 30, 125);
        int guards = town.guards();
        long version = state.settlementsVersion();
        int before = changes[0];
        assertTrue(state.moveSettlement(town.id(), winner));
        assertEquals(winner, town.kingdomId());
        assertTrue(state.settlementsVersion() > version, "the maps and the index of settlements see the change");
        assertTrue(changes[0] > before, "the data is to be saved");
        assertEquals(List.of(), state.settlementsOf(KINGDOM));
        assertEquals(List.of(town), state.settlementsOf(winner));
        assertEquals(7, town.province());
        assertEquals(guards, town.guards(), "its people and numbers stay as they are");
        assertEquals(30, town.population());
        long after = state.settlementsVersion();
        assertFalse(state.moveSettlement(town.id(), winner), "a second time finds it moved already");
        assertFalse(state.moveSettlement(9_999, winner), "no such settlement");
        assertEquals(after, state.settlementsVersion());
    }

    @Test
    void aSettlementRisingGoesOnForItsNewKingdomFromWhatStands() {
        UUID winner = UUID.fromString("00000000-0000-4000-8000-000000000002");
        NpcState state = new NpcState();
        Settlement town = state.addSettlement(KINGDOM, 7, 0, Tier.TOWN, 100, -200, 42, new NpcNames.Name("Ostbrook", "Остбрук"));
        town.found(40, 30, 125);

        town.setStage(SettlementStage.CHECK.id(), 0);
        assertTrue(state.moveSettlement(town.id(), winner));
        assertEquals(SettlementStage.CHECK.id(), town.stage(), "nothing of it stands yet: it rises for the winner as it would have");

        town.setStage(SettlementStage.GROUND.id(), 300);
        assertTrue(state.moveSettlement(town.id(), KINGDOM));
        assertEquals(SettlementStage.GROUND.id(), town.stage());
        assertEquals(0, town.step(), "its ground is levelled again for the layout of its new kingdom");

        town.setStage(SettlementStage.BUILD.id(), 1);
        assertTrue(state.moveSettlement(town.id(), winner));
        assertEquals(SettlementStage.BUILD.id(), town.stage(), "only its town hall stands: the buildings of the winner go up");
        assertEquals(1, town.step());

        town.setStatus(Settlement.Status.RAISING);
        town.setStage(SettlementStage.BUILD.id(), 5);
        assertTrue(state.moveSettlement(town.id(), KINGDOM));
        assertEquals(SettlementStage.PLACES.id(), town.stage(), "no second layout over the buildings that stand");
        assertEquals(0, town.step());

        town.setStatus(Settlement.Status.DATA);
        town.setStage(SettlementStage.WORK.id(), 2);
        assertTrue(state.moveSettlement(town.id(), winner));
        assertEquals(SettlementStage.PLACES.id(), town.stage(), "nor fields of the winner over those sown");

        town.setStage(SettlementStage.PEOPLE.id(), 6);
        assertTrue(state.moveSettlement(town.id(), KINGDOM));
        assertEquals(SettlementStage.PEOPLE.id(), town.stage(), "past its buildings it goes on where it was");
        assertEquals(6, town.step());

        town.setStatus(Settlement.Status.BLOCKED);
        town.setStage(SettlementStage.BUILD.id(), 5);
        assertTrue(state.moveSettlement(town.id(), winner));
        assertEquals(SettlementStage.BUILD.id(), town.stage(), "one that never rises keeps what it has");
    }

    @Test
    void theGridFindsTheSettlementOfAChunkAndThoseNear() {
        NpcState state = new NpcState();
        Settlement city = state.addSettlement(KINGDOM, 1, 0, Tier.CITY, 0, 0, 1, new NpcNames.Name("A", "А"));
        Settlement hamlet = state.addSettlement(KINGDOM, 1, city.id(), Tier.HAMLET, 40, 40, 2, new NpcNames.Name("B", "Б"));
        Settlement far = state.addSettlement(KINGDOM, 2, 0, Tier.VILLAGE, 5000, -3000, 3, new NpcNames.Name("C", "В"));
        SettlementGrid grid = SettlementGrid.of(state.settlements());
        assertEquals(3, grid.size());
        assertEquals(city.id(), grid.at(0, 0));
        assertEquals(city.id(), grid.at(-4, 4), "the square of a city reaches four chunks");
        assertEquals(0, grid.at(-5, 0));
        // Where the squares meet the city, of the higher rank, holds the chunk.
        assertEquals(city.id(), grid.at(2, 2));
        assertEquals(far.id(), grid.at(5000 >> 4, -3000 >> 4));
        assertEquals(far.id(), grid.at((5000 >> 4) + 2, (-3000 >> 4) - 2));
        assertEquals(List.of(city, hamlet), grid.near(0, 0, 100));
        assertEquals(List.of(hamlet, city), grid.near(50, 50, 100));
        assertEquals(List.of(far), grid.near(5100, -3000, 200));
        far.setStatus(Settlement.Status.RUINED);
        assertEquals(0, SettlementGrid.of(state.settlements()).at(5000 >> 4, -3000 >> 4), "a ruin stands nowhere");
    }
}
