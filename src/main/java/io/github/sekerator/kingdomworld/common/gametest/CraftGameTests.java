package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.commerce.CityTies;
import io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData;
import io.github.sekerator.kingdomworld.common.commerce.CraftSettings;
import io.github.sekerator.kingdomworld.common.commerce.Traders;
import io.github.sekerator.kingdomworld.common.commerce.ShopActions;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CraftRules;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the workshops of citizens and what the crown buys of them: a player sells raw materials to a workshop onto the
 * account out of its till, an order no workshop of the crown makes is bought of a master, a construction short of planks buys them
 * of a carpenter, a trading house exports only what citizens made, and the purchases of the crown never take its treasury below
 * the days of wages it keeps.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftGameTests {
    private static final String EMPTY = "empty";

    private CraftGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    static WorldPos bed(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(x, 0, z));
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlockAndUpdate(head.west(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        WorldPos pos = CitizenGameTests.at(helper, x, 0, z);
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom.id());
        return pos;
    }

    /**
     * A house of a craftsman of the kingdom in the register of buildings.
     */
    static Building craftHouse(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to) {
        MinecraftServer server = helper.getLevel().getServer();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        long id = book.nextBuildingId();
        book.restoreCounters(0, id + 1);
        long now = KingdomWorldApi.gameTime(server);
        BlockPos a = helper.absolutePos(from);
        BlockPos b = helper.absolutePos(to);
        Building building = new Building(id, kingdom.id(), "kingdomworld:classic_house_craftsman", "house", "housing", BuildingRoles.HOUSE, 1,
                Materials.NONE, new WorldPos(Dimensions.id(helper.getLevel()), a.getX(), a.getY(), a.getZ()), Placement.NONE,
                new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                        Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())), 0, now, true, 100, now, false, new java.util.BitSet());
        book.putBuilding(building);
        BuildingEffects.changed();
        return building;
    }

    /**
     * A workshop of a trade in a house of a craftsman, its workstations known, kept by a citizen living there.
     */
    static Business workshop(GameTestHelper helper, Kingdom kingdom, String trade, Set<String> stations) {
        MinecraftServer server = helper.getLevel().getServer();
        Building house = craftHouse(helper, kingdom, new BlockPos(3, 0, 3), new BlockPos(8, 3, 8));
        Citizen master = CitizenLife.arriveInto(server, kingdom.id(), bed(helper, kingdom, 5, 5), bed(helper, kingdom, 5, 5), false)
                .orElseThrow(() -> new IllegalStateException("No citizen came"));
        WorldPos at = house.anchor();
        Business.Place place = new Business.Place(Business.PlaceKind.CRAFT_HOUSE, house.id(), 0, at.dimension(), at.x(), at.y(), at.z());
        CommerceSavedData data = CommerceSavedData.get(server);
        long day = TreasuryActions.day(server);
        data.putSurvey(place.key(), new CommerceSavedData.Survey(stations, day));
        Business shop = data.open(kingdom.id(), 0, place, day, 200);
        shop.changeTrade(trade, day);
        shop.setPinned(trade);
        shop.setKeepers(List.of(master.id()), false);
        master.setBusiness(shop.id());
        return shop;
    }

    static ServerPlayer player(GameTestHelper helper, UUID id, int x, int z) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "kw-craft"));
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    // ---------------------------------------------------------------- players sell to workshops

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_sell", timeoutTicks = 200)
    public static void aPlayerSellsRawMaterialsToAWorkshopOntoTheAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bakery", kingId);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            Business bakery = workshop(helper, kingdom, "bakery", Set.of("crafting_table", "furnace"));
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom.id(), bakery.id());
            economy.treasuryToPurse(till, 400, LedgerCategory.TRANSFERS, day);
            ServerPlayer player = player(helper, kingId, 5, 6);
            player.getInventory().add(new ItemStack(Items.WHEAT, 12));
            long account = economy.accounts().balance(kingId, kingdom.id());
            long before = economy.purse(till);
            KingdomWorldApi.state(server).citizens().get(bakery.keepers().get(0)).ifPresent(master -> master.setMood(60));
            var feedback = ShopActions.sale(player, bakery, new CommercePackets.ShopTrade(bakery.id(), CommercePackets.ShopTrade.Action.SELL,
                    "minecraft:wheat", 12, 0, true));
            helper.assertTrue(feedback.success(), "The bakery did not buy the wheat: " + feedback);
            long paid = economy.accounts().balance(kingId, kingdom.id()) - account;
            helper.assertTrue(paid > 0, "Nothing came onto the account");
            helper.assertTrue(economy.purse(till) == before - paid, "The till did not pay what the account got: " + economy.purse(till) + " / "
                    + (before - paid));
            long sold = 12 - player.getInventory().countItem(Items.WHEAT);
            helper.assertTrue(sold > 0 && bakery.input("minecraft:wheat") == sold, "The wheat is not among the raw materials of the bakery");
            // It pays no more than the share of the worth of wheat, whatever the crown would ask.
            helper.assertTrue(paid <= sold * 3, "The bakery paid more than the worth: " + paid + " for " + sold);
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- trading houses

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_trader", timeoutTicks = 200)
    public static void aTradingHouseExportsOnlyWhatCitizensMade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Trader", UUID.randomUUID());
        CraftRules before = CraftSettings.rules();
        try {
            // A demand from abroad larger than the strength of the house, so that only what citizens made limits the export.
            CraftRules big = CraftRules.DEFAULT;
            CraftSettings.override(new CraftRules(big.crownBuysFromCitizens(), big.crownBuyCapPercent(), big.crownBuyPerDay(),
                    big.crownBuyKeepWageDays(), big.playerSellPercent(), big.playerSellOwnPercent(), new long[]{5_000, 5_000, 5_000},
                    big.outsideDemandPerRootPerson(), big.outsideDemandFullCities(), big.traderBuyPercent(), big.marketDemandFactor(),
                    big.roadLinkPercent(), big.craftExport(), big.regionalMarket(), big.houseWhenUnmetPercent()));
            CityTies.reset();
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            Business bakery = workshop(helper, kingdom, "bakery", Set.of("crafting_table", "furnace"));
            bakery.addMade("minecraft:bread", GoodsCategory.FOOD.ordinal(), 6, 2_000, 100);
            bakery.addGoods("minecraft:bread", GoodsCategory.FOOD.ordinal(), 20, 2_000);
            Citizen merchant = CitizenLife.arriveInto(server, kingdom.id(), bed(helper, kingdom, 10, 10), bed(helper, kingdom, 10, 10), false)
                    .orElseThrow(() -> new IllegalStateException("No citizen came"));
            BlockPos counter = helper.absolutePos(new BlockPos(12, 1, 12));
            Business house = CommerceSavedData.get(server).open(kingdom.id(), 0, new Business.Place(Business.PlaceKind.COUNTER, 0, 0,
                    Dimensions.id(helper.getLevel()), counter.getX(), counter.getY(), counter.getZ()), day, 200);
            house.changeTrade(Trade.TRADER, day);
            house.setKeepers(List.of(merchant.id()), false);
            merchant.setBusiness(house.id());
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom.id(), house.id());
            long supplyBefore = economy.supply(kingdom.id());
            Traders.day(server, kingdom.id(), 0, day);
            helper.assertTrue(bakery.count("minecraft:bread") == 20, "The trading house took bread the bakery bought of the crown");
            helper.assertTrue(bakery.count(Business.key("minecraft:bread", true)) == 0, "The bread the bakery made was not exported");
            long income = economy.purse(till) + economy.purse(CityPurses.PurseKey.till(kingdom.id(), bakery.id()));
            helper.assertTrue(income > 0, "No money came from abroad");
            helper.assertTrue(economy.supply(kingdom.id()) - supplyBefore == income, "The money from abroad is not all new money of the currency");
            helper.assertTrue(economy.cityTally(kingdom.id(), 0).get(CityStatement.Line.CITIZEN_EXPORTS) > 0, "The city counts no export");
        } finally {
            CraftSettings.override(null);
            CityTies.reset();
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }
}
