package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.commerce.CommerceWork;
import io.github.sekerator.kingdomworld.common.commerce.FolkWork;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the townsfolk at work: on a farm post they bring the harvest to the storehouses and are paid into the purse of
 * their city, as hands of a workshop they pay the tithe of their share, and a named worker hired on a post takes the seat of one
 * of them.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FolkWorkGameTests {
    private static final String EMPTY = "empty";

    private FolkWorkGameTests() {
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_post", timeoutTicks = 200)
    public static void townsfolkOnAFarmBringTheHarvestAndArePaidIntoThePurseOfTheirCity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Folk Farm", UUID.randomUUID());
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 2);
            WorldPos at = GatheringGameTests.post(helper, new BlockPos(2, 0, 0), "farmer");
            WorkPost post = PostWork.post(server, at).orElseThrow();
            KingdomWorldApi.change(server, "test", state -> state.setPostSeats(null, at, 4, 4));
            post.rememberFarm(48, "minecraft:carrot");
            post.setFolkWorkers(4);
            FolkWork.work(server, post, 1_200);
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.CARROT) > 0, "The townsfolk brought no carrots");
            helper.assertTrue(post.status() != io.github.sekerator.kingdomworld.core.citizen.WorkStatus.WORKING,
                    "The townsfolk set the status of the post");
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 10_000, day);
            long wage = economy.treasury(kingdom.id()).orElseThrow().wage("farmer", EconomySettings.rules());
            CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom.id(), 0);
            long before = economy.purse(purse);
            // The first payday of a kingdom only starts the count.
            EconomyEvents.payday(server, kingdom, day + 1);
            EconomyEvents.payday(server, kingdom, day + 2);
            helper.assertTrue(economy.purse(purse) - before >= 4 * wage, "The wages of the townsfolk did not stay in their city: "
                    + (economy.purse(purse) - before) + " of " + 4 * wage);
            helper.assertTrue(post.folkWorkers() == 4, "The townsfolk paid in full left the post");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_hands", timeoutTicks = 200)
    public static void handsOfTheTownsfolkInAWorkshopPayTheTitheOfTheirShare(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Folk Hands", UUID.randomUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            Business bakery = CraftGameTests.workshop(helper, kingdom, "bakery", Set.of("crafting_table", "furnace"));
            bakery.setFolkHands(2);
            CityCharter charter = RoadWork.state(server).charterOf(kingdom.id(), 0, CityCharter.NEVER);
            charter.setFolkEarned(0);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom.id(), bakery.id());
            economy.treasuryToPurse(till, 100, LedgerCategory.TRANSFERS, day);
            bakery.setProfitDue(100);
            CityPurses.PurseKey cityTreasury = CityPurses.PurseKey.cityTreasury(kingdom.id(), 0);
            long treasuryBefore = economy.purse(cityTreasury);
            CommerceWork.payday(server, kingdom.id(), day);
            long tithe = economy.purse(cityTreasury) - treasuryBefore;
            helper.assertTrue(tithe > 0, "No tithe was paid of the profit");
            // A master's share of 2 and two hands of 0.8 each: the hands get 1.6 of 3.6 of what is left after the tithe.
            long expected = (100 - tithe) * 1_600 / 3_600;
            helper.assertTrue(Math.abs(charter.folkEarned() - expected) <= 1, "The hands did not earn their share after the tithe: "
                    + charter.folkEarned() + " of " + expected);
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_named_first", timeoutTicks = 200)
    public static void aNamedWorkerHiredOnAPostTakesTheSeatOfOneOfTheTownsfolk(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Folk Named", UUID.randomUUID());
        Citizen worker = CitizenGameTests.newcomer(helper, kingdom, 4, 4);
        try {
            WorldPos at = GatheringGameTests.post(helper, new BlockPos(2, 0, 0), "lumberjack");
            WorkPost post = PostWork.post(server, at).orElseThrow();
            KingdomWorldApi.change(server, "test", state -> state.setPostSeats(null, at, 2, 4));
            post.setFolkWorkers(2);
            helper.assertTrue(post.folkWorkers() == 2, "The townsfolk did not take the seats");
            KingdomWorldApi.state(server).citizens().assignToPost(worker, post);
            helper.assertTrue(post.workers().contains(worker.id()), "The named worker was not hired");
            helper.assertTrue(post.folkWorkers() == 1, "The named worker did not take the seat of one of the townsfolk: " + post.folkWorkers());
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker);
        }
        helper.succeed();
    }
}
