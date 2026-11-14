package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the governor of a city builds next: a post first, no more at once than its builders manage, the buildings its level wants
 * in the order of the table, a house of the nobility leaning by its character, only what it may pay for, and a wall at last.
 */
class CityGrowthTest {
    private static final RulerRules RULES = RulerRules.DEFAULT;
    private static final List<String> ROLES = List.of("house", "warehouse", "market", "well", "workshop", "barracks", "tower", "tavern",
            "mage_tower");

    /** A village with free beds, a storehouse half full and nothing of the table built. */
    private static RulerView village() {
        RulerView view = new RulerView();
        view.cityLevel = CityLevel.VILLAGE.ordinal();
        view.bedsTotal = 10;
        view.bedsUsed = 4;
        view.storageSlots = 54;
        view.storageUsed = 20;
        view.knownRoles = ROLES;
        for (String role : ROLES) {
            view.builds.add(new RulerView.Option(role, "kingdomworld:" + role, "", 0, true));
        }
        return view;
    }

    private static CityGrowth.Situation crew(int builders, int sites) {
        return new CityGrowth.Situation(builders, 0, 0, sites, false, false, 0, 0, null);
    }

    private static CityGrowth.Situation money(long allowance, long budget) {
        return new CityGrowth.Situation(1, 0, 0, 0, false, false, allowance, budget, null);
    }

    @Test
    void aCityWithoutBuildersOrdersAPostWhileSomebodyIsWithoutWork() {
        RulerView view = village();
        assertEquals(CityGrowth.Kind.ORDER_POST, CityGrowth.decide(view, new CityGrowth.Situation(0, 0, 1, 0, false, false, 0, 0, null), RULES)
                .kind());
        assertEquals(CityGrowth.Kind.WAIT_POST, CityGrowth.decide(view, new CityGrowth.Situation(0, 3, 1, 0, true, false, 0, 0, null), RULES)
                .kind(), "the post ordered is waited for");
        assertEquals(CityGrowth.Kind.NO_BUILDER, CityGrowth.decide(view, new CityGrowth.Situation(0, 0, 0, 0, false, false, 0, 0, null), RULES)
                .kind(), "nobody to hire and nobody to help");
        assertEquals(CityGrowth.Kind.BUILD, CityGrowth.decide(view, new CityGrowth.Situation(0, 2, 0, 0, false, false, 0, 0, null), RULES)
                .kind(), "builders of other cities help where nobody builds");
        assertEquals(GovernorNote.Kind.NO_BUILDER, CityGrowth.decide(view, crew(0, 0), RULES).note().kind());
    }

    @Test
    void aCityBuildsNoMoreAtOnceThanItsBuildersManage() {
        RulerView view = village();
        CityGrowth.Decision waiting = CityGrowth.decide(view, crew(2, 2), RULES);
        assertEquals(CityGrowth.Kind.WAIT, waiting.kind());
        assertEquals(new GovernorNote(GovernorNote.Kind.WAITING, "", 2), waiting.note());
        assertEquals(CityGrowth.Kind.BUILD, CityGrowth.decide(view, crew(2, 1), RULES).kind());
        assertEquals(CityGrowth.Kind.WAIT, CityGrowth.decide(view, crew(1, 1), RULES).kind());
        assertEquals(CityGrowth.Kind.WAIT, CityGrowth.decide(view, new CityGrowth.Situation(0, 4, 0, 1, false, false, 0, 0, null), RULES)
                .kind(), "helpers take one construction at a time");
    }

    @Test
    void theBuildingsTheLevelWantsComeInTheOrderOfTheTable() {
        RulerView view = village();
        assertEquals("warehouse", CityGrowth.decide(view, crew(1, 0), RULES).role(), "a village wants a storehouse first");
        view.roles.put("warehouse", 1);
        assertEquals("market", CityGrowth.decide(view, crew(1, 0), RULES).role());
        view.planned.put("market", 1);
        assertEquals("well", CityGrowth.decide(view, crew(1, 0), RULES).role(), "a planned building counts");
        view.roles.put("well", 1);
        view.roles.put("workshop", 1);
        view.roles.put("barracks", 1);
        view.roles.put("tower", 1);
        CityGrowth.Decision wall = CityGrowth.decide(view, crew(1, 0), RULES);
        assertEquals(CityGrowth.Kind.WALL, wall.kind(), "a village wants no tavern: nothing is left but a wall");
        assertEquals(Wall.WallClass.PALISADE, wall.wall());
        view.cityLevel = CityLevel.TOWN.ordinal();
        assertEquals("well", CityGrowth.decide(view, crew(1, 0), RULES).role(), "a town wants two wells");
    }

    @Test
    void aHouseComesFirstWhenTheBedsAreTakenAndAStorehouseWhenTheStoresAreFull() {
        RulerView view = village();
        view.roles.put("warehouse", 1);
        view.bedsUsed = view.bedsTotal;
        assertEquals("house", CityGrowth.decide(view, crew(1, 0), RULES).role());
        view.planned.put("house", 1);
        assertEquals("market", CityGrowth.decide(view, crew(1, 0), RULES).role(), "one house at a time");
        view.storageUsed = view.storageSlots;
        assertEquals("warehouse", CityGrowth.decide(view, crew(1, 0), RULES).role(), "full stores want another storehouse");
        assertEquals(List.of("warehouse", "market", "well", "workshop", "barracks", "tower"), CityGrowth.wantedRoles(view, RULES, null));
    }

    @Test
    void theCharacterOfAHouseShiftsTheChoice() {
        RulerView view = village();
        view.roles.put("warehouse", 1);
        assertEquals("market", CityGrowth.decide(view, crew(1, 0), RULES).role(), "a mayor has no leanings");
        CityGrowth.Situation warrior = new CityGrowth.Situation(1, 0, 0, 0, false, false, 0, 0, RulerCharacter.WARRIOR);
        assertEquals("barracks", CityGrowth.decide(view, warrior, RULES).role());
        CityGrowth.Situation merchant = new CityGrowth.Situation(1, 0, 0, 0, false, false, 0, 0, RulerCharacter.MERCHANT);
        assertEquals("market", CityGrowth.decide(view, merchant, RULES).role());
        view.bedsUsed = view.bedsTotal;
        assertEquals("house", CityGrowth.decide(view, warrior, RULES).role(), "a full city wants a house first, whoever governs it");
    }

    @Test
    void aRoleWithoutATemplateIsPassedOver() {
        RulerView view = village();
        view.roles.put("warehouse", 1);
        view.builds.removeIf(option -> option.role().equals("market"));
        assertEquals("well", CityGrowth.decide(view, crew(1, 0), RULES).role());
    }

    @Test
    void withoutMoneyAGovernorBuildsOnlyWhatTheStorehousesHold() {
        RulerView view = village();
        view.builds.replaceAll(option -> new RulerView.Option(option.role(), option.template(), "", 120, true));
        CityGrowth.Decision lacking = CityGrowth.decide(view, money(0, 0), RULES);
        assertEquals(CityGrowth.Kind.LACK, lacking.kind());
        assertEquals(new GovernorNote(GovernorNote.Kind.LACKS, "kingdomworld:warehouse", 120), lacking.note());
        view.builds.replaceAll(option -> new RulerView.Option(option.role(), option.template(), "", 0, true));
        assertEquals(CityGrowth.Kind.BUILD, CityGrowth.decide(view, money(0, 0), RULES).kind());
    }

    @Test
    void withMoneyAGovernorSavesForWhatItCannotPayToday() {
        RulerView view = village();
        view.builds.replaceAll(option -> new RulerView.Option(option.role(), option.template(), "", 120, true));
        CityGrowth.Decision saving = CityGrowth.decide(view, money(300, 100), RULES);
        assertEquals(CityGrowth.Kind.SAVE, saving.kind());
        assertEquals(new GovernorNote(GovernorNote.Kind.SAVES, "kingdomworld:warehouse", 120), saving.note());
        assertEquals(CityGrowth.Kind.BUILD, CityGrowth.decide(view, money(300, 120), RULES).kind());
    }

    @Test
    void theAllowanceIsSharedFairlyAndTheReserveIsKept() {
        assertEquals(34, CityGrowth.share(100, 3), "rounded up");
        assertEquals(100, CityGrowth.share(100, 0));
        assertEquals(0, CityGrowth.share(0, 3));
        assertEquals(25, CityGrowth.budget(100, 4, 0, 10_000, 100));
        assertEquals(15, CityGrowth.budget(100, 4, 10, 10_000, 100), "what was spent today is gone");
        assertEquals(0, CityGrowth.budget(100, 4, 40, 10_000, 100), "never below nothing");
        assertEquals(10, CityGrowth.budget(100, 4, 0, 1_000, 990), "never below the reserve");
        assertEquals(0, CityGrowth.budget(100, 4, 0, 500, 990));
        assertEquals(0, CityGrowth.budget(0, 4, 0, 10_000, 100), "no allowance, no money");
    }

    @Test
    void theShareOfACityGoesIntoItsTreasuryUpToAWeekUnspentAndAboveTheReserve() {
        assertEquals(25, CityGrowth.grant(25, 0, 7, 10_000, 100));
        assertEquals(25, CityGrowth.grant(25, 150, 7, 10_000, 100), "below a week of shares unspent the whole share comes");
        assertEquals(15, CityGrowth.grant(25, 160, 7, 10_000, 100), "no more than a week of shares unspent");
        assertEquals(0, CityGrowth.grant(25, 175, 7, 10_000, 100));
        assertEquals(0, CityGrowth.grant(25, 500, 7, 10_000, 100), "more granted than a week takes nothing back");
        assertEquals(10, CityGrowth.grant(25, 0, 7, 1_000, 990), "never below the reserve of the kingdom");
        assertEquals(0, CityGrowth.grant(25, 0, 7, 500, 990));
        assertEquals(0, CityGrowth.grant(0, 0, 7, 10_000, 100), "no allowance, no grant");
        assertEquals(25, CityGrowth.grant(25, 0, 0, 10_000, 100), "at least one share");
    }

    @Test
    void aGovernorPlansWithItsShareOfTheStoresOnly() {
        assertEquals(50, CityGrowth.usable(100, 50));
        assertEquals(0, CityGrowth.usable(100, 0));
        assertEquals(100, CityGrowth.usable(100, 100));
        assertEquals(1_500_000_000_000L, CityGrowth.usable(3_000_000_000_000L, 50), "large stores do not overflow");
        Map<String, Integer> cost = Map.of("minecraft:oak_planks", 60, "minecraft:cobblestone", 10);
        Map<String, Long> stock = Map.of("minecraft:oak_planks", 100L, "minecraft:cobblestone", 40L);
        assertEquals(10 * 2, CityGrowth.missing(cost, stock, item -> 2, 50), "half of 100 planks leaves 10 lacking");
        assertEquals(0, CityGrowth.missing(cost, stock, item -> 2, 100));
        assertEquals((60 + 10) * 3, CityGrowth.missing(cost, stock, item -> 3, 0));
        assertEquals(70, CityGrowth.missing(cost, Map.of(), item -> 0, 50), "an item is worth 1 at least");
    }

    @Test
    void aLargeCityGetsAStoneWallAndACityWithAWallIsDone() {
        RulerView view = village();
        view.cityLevel = CityLevel.CITY.ordinal();
        view.builds.clear();
        CityGrowth.Decision wall = CityGrowth.decide(view, crew(1, 0), RULES);
        assertEquals(Wall.WallClass.STONE, wall.wall());
        assertEquals(new GovernorNote(GovernorNote.Kind.WALL, "stone", 0), wall.note());
        CityGrowth.Decision done = CityGrowth.decide(view, new CityGrowth.Situation(1, 0, 0, 0, false, true, 0, 0, null), RULES);
        assertEquals(CityGrowth.Kind.DONE, done.kind());
        assertTrue(done.template().isEmpty());
    }

    @Test
    void aNoteReadsBackAsItWasWritten() {
        GovernorNote note = new GovernorNote(GovernorNote.Kind.SAVES, "kingdomworld:small_house", 340);
        assertEquals(note, GovernorNote.parse(note.encode()));
        assertEquals(GovernorNote.NONE, GovernorNote.parse(""));
        assertEquals(GovernorNote.NONE, GovernorNote.parse("something|else|3"));
        assertEquals(new GovernorNote(GovernorNote.Kind.BUILDS, "x", 0), GovernorNote.parse("builds|x|many"));
        assertEquals("", GovernorNote.NONE.encode());
    }

    @Test
    void aCityOutOfRoomWidensByARingOfItsLandOrSaysItNeedsLand() {
        assertEquals(CityGrowth.LEAST_RING, CityGrowth.ring(0));
        assertEquals(11, CityGrowth.ring(7));
        assertEquals(CityGrowth.WIDEST_RING, CityGrowth.ring(40), "a ring is never wider than sixteen blocks");
        RulerView view = village();
        CityGrowth.Decision house = CityGrowth.decide(view, crew(1, 0), RULES);
        CityGrowth.Decision wider = CityGrowth.outOfRoom(house, true, 48, CityGrowth.ring(7));
        assertEquals(CityGrowth.Kind.EXPAND, wider.kind());
        assertEquals(59, wider.extent());
        assertEquals(house.template(), wider.template(), "what was wanted is built in the new ring");
        assertEquals(new GovernorNote(GovernorNote.Kind.EXPANDS, house.template(), 59), wider.note());
        assertEquals(1_000_016, CityGrowth.outOfRoom(house, true, 1_000_000, 16).extent(), "without an upper limit");
        CityGrowth.Decision stuck = CityGrowth.outOfRoom(house, false, 48, 11);
        assertEquals(CityGrowth.Kind.NEEDS_LAND, stuck.kind());
        assertEquals(GovernorNote.Kind.NEEDS_LAND, stuck.note().kind());
        assertEquals(0, stuck.extent());
    }

    @Test
    void theRingIsTheChunksBeyondTheSquare() {
        // A square of 24 blocks around the middle of a chunk holds the three by three chunks around it whole: the ring of 8 is the
        // chunks around them, up to 32 blocks from the middle.
        java.util.List<int[]> ring = CityGrowth.ringChunks(8, 8, 24, 8);
        assertTrue(ring.stream().noneMatch(chunk -> chunk[0] == 0 && chunk[1] == 0), "the chunk of the town hall is no ring");
        assertTrue(ring.stream().anyMatch(chunk -> chunk[0] == 2 && chunk[1] == 0), "a chunk at the edge is in the ring");
        assertTrue(ring.stream().allMatch(chunk -> Math.abs(chunk[0]) <= 2 && Math.abs(chunk[1]) <= 2), "nothing beyond the ring");
        assertEquals(25 - 9, ring.size());
    }
}
