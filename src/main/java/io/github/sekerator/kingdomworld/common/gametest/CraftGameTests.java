package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.commerce.CityTies;
import io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData;
import io.github.sekerator.kingdomworld.common.commerce.CraftSettings;
import io.github.sekerator.kingdomworld.common.commerce.CrownPurchases;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
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
            // A master that sold nothing yet keeps a day of its strength of every kind for its city: it made more than that.
            bakery.addMade("minecraft:bread", GoodsCategory.FOOD.ordinal(), 120, 2_000, 100);
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
            long kept = bakery.count(Business.key("minecraft:bread", true));
            helper.assertTrue(kept < 120, "The bread the bakery made was not exported beyond what it keeps: " + kept + " of 120 left");
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

    // ---------------------------------------------------------------- the crown buys of citizens

    /**
     * A carpenter holding planks its masters made, and a storehouse of the kingdom with room.
     */
    private static Business carpenterWithPlanks(GameTestHelper helper, Kingdom kingdom, int planks) {
        CitizenGameTests.storehouse(helper, 0, 0, 1);
        Business carpenter = workshop(helper, kingdom, "carpenter", Set.of("crafting_table"));
        carpenter.addMade("minecraft:oak_planks", GoodsCategory.MATERIALS.ordinal(), planks, 1_000, 50);
        return carpenter;
    }

    private static long planksInStore(MinecraftServer server, Kingdom kingdom) {
        return KingdomStorage.countsById(server, kingdom.id()).getOrDefault("minecraft:oak_planks", 0L);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_order", timeoutTicks = 200)
    public static void anOrderNoWorkshopMakesIsBoughtOfAMaster(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Order", kingId);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            Business carpenter = carpenterWithPlanks(helper, kingdom, 20);
            KingdomWorldState state = KingdomWorldApi.state(server);
            Order order = state.order(kingId, kingdom.id(), "minecraft:oak_planks", Order.Kind.MAKE, 8, Order.Priority.NORMAL,
                    KingdomWorldApi.gameTime(server), Integer.MAX_VALUE, id -> true).order().orElseThrow();
            Production.plan(server, kingdom.id());
            helper.assertTrue(order.status() == Order.Status.NO_WORKSHOP, "The order has a workshop after all: " + order.status());
            long funds = economy.treasury(kingdom.id()).orElseThrow().funds();
            CrownPurchases.step(server, kingdom.id());
            helper.assertTrue(planksInStore(server, kingdom) == 8, "The planks are not in the storehouse: " + planksInStore(server, kingdom));
            helper.assertTrue(order.made() == 8, "The order does not count the planks bought: " + order.made());
            helper.assertTrue(carpenter.count(Business.key("minecraft:oak_planks", true)) == 12, "The carpenter did not give up eight planks");
            long paid = funds - economy.treasury(kingdom.id()).orElseThrow().funds();
            long till = economy.purse(CityPurses.PurseKey.till(kingdom.id(), carpenter.id()));
            helper.assertTrue(paid > 0 && till == paid, "The till did not get what the crown paid: " + till + " / " + paid);
            helper.assertTrue(economy.cityTally(kingdom.id(), 0).get(CityStatement.Line.CROWN_BOUGHT) == paid, "The city counts no purchase");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_construction", timeoutTicks = 200)
    public static void aConstructionShortOfPlanksBuysThemOfACarpenter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Planks", UUID.randomUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            carpenterWithPlanks(helper, kingdom, 40);
            // An order as a construction gives it for what it lacks: no order of the king, beyond the limit of the king.
            KingdomWorldState state = KingdomWorldApi.state(server);
            Order order = state.order(null, kingdom.id(), "minecraft:oak_planks", Order.Kind.MAKE, 24, Order.Priority.NORMAL,
                    KingdomWorldApi.gameTime(server), Integer.MAX_VALUE, id -> true).order().orElseThrow();
            Production.plan(server, kingdom.id());
            CrownPurchases.step(server, kingdom.id());
            helper.assertTrue(planksInStore(server, kingdom) == 24, "The planks for the construction are not in the storehouse");
            helper.assertTrue(order.made() == 24, "The order of the construction does not count them");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData.get(server).boughtFor(kingdom.id(), order.id()) == 24,
                    "The order does not tell what was bought of citizens");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_retrade", timeoutTicks = 200)
    public static void aWorkshopTakingUpAnotherTradeGivesTheCrownItsDepositBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Retrade", kingId);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            CitizenGameTests.storehouse(helper, 0, 0, 1).insertItem(0, new ItemStack(Items.OAK_LOG, 64), false);
            Business carpenter = workshop(helper, kingdom, "carpenter", Set.of("crafting_table"));
            KingdomWorldState state = KingdomWorldApi.state(server);
            state.order(kingId, kingdom.id(), "minecraft:oak_planks", Order.Kind.MAKE, 8, Order.Priority.NORMAL, KingdomWorldApi.gameTime(server),
                    Integer.MAX_VALUE, id -> true);
            Production.plan(server, kingdom.id());
            long funds = economy.treasury(kingdom.id()).orElseThrow().funds();
            CrownPurchases.step(server, kingdom.id());
            helper.assertTrue(!carpenter.commissions().isEmpty(), "The crown paid the carpenter nothing in advance");
            helper.assertTrue(carpenter.goods().isEmpty(), "The carpenter holds goods already: " + carpenter.goods());
            ServerPlayer king = player(helper, kingId, 5, 5);
            helper.assertTrue(ShopActions.fix(king, carpenter, Business.GENERAL_STORE).success(), "The general store was not pinned");
            helper.assertTrue(carpenter.trade().equals(Business.GENERAL_STORE), "The workshop did not take up the general store: " + carpenter.trade());
            helper.assertTrue(carpenter.commissions().isEmpty(), "The work paid in advance stays with a general store");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == funds, "The deposit did not go back to the crown: "
                    + economy.treasury(kingdom.id()).orElseThrow().funds() + " of " + funds);
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_craft_reserve", timeoutTicks = 200)
    public static void purchasesNeverTakeTheTreasuryBelowItsDaysOfWages(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Reserve", UUID.randomUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 600, day);
            // Ten farmers paid a day: the treasury keeps seven days of their wages.
            List<Economy.WageClaim> claims = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                claims.add(new Economy.WageClaim(UUID.randomUUID(), "farmer"));
            }
            // The first payday of a treasury only marks its day: the wages are paid from the next one on.
            economy.payday(kingdom.id(), day, List.of(), claims, io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules());
            economy.payday(kingdom.id(), day + 1, List.of(), claims, io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules());
            long keep = CraftSettings.rules().crownBuyKeepWageDays() * economy.treasury(kingdom.id()).orElseThrow().lastPayroll().due();
            helper.assertTrue(keep > 0, "No wages to keep");
            carpenterWithPlanks(helper, kingdom, 500);
            KingdomWorldState state = KingdomWorldApi.state(server);
            state.order(null, kingdom.id(), "minecraft:oak_planks", Order.Kind.MAKE, 500, Order.Priority.NORMAL, KingdomWorldApi.gameTime(server),
                    Integer.MAX_VALUE, id -> true);
            Production.plan(server, kingdom.id());
            CrownPurchases.step(server, kingdom.id());
            long funds = economy.treasury(kingdom.id()).orElseThrow().funds();
            helper.assertTrue(funds >= keep, "The purchases took the treasury below its days of wages: " + funds + " < " + keep);
            helper.assertTrue(planksInStore(server, kingdom) < 500, "The crown bought everything it could not pay for");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    /**
     * A stand of the load: the purchases of the crown of a kingdom of players with workshops of citizens of several trades and
     * orders no workshop of the crown makes, which the crown looks for masters for at every step. It prints the average and the
     * worst step and asks nothing of them.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_load_bench_crown", timeoutTicks = 400, required = false)
    public static void theCrownLooksForMastersOfManyStuckOrders(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Crown Load", kingId);
        String[] trades = {"toolmaker", "bakery", "cookshop", "carpenter", "tailor", "mason", "glazier"};
        String[] items = {"minecraft:oak_planks", "minecraft:stick", "minecraft:chest", "minecraft:bread", "minecraft:glass", "minecraft:torch",
                "minecraft:stone_bricks", "minecraft:ladder", "minecraft:iron_sword", "minecraft:white_wool", "minecraft:cake",
                "minecraft:cookie", "minecraft:barrel", "minecraft:bookshelf", "minecraft:crafting_table", "minecraft:furnace",
                "minecraft:stone_slab", "minecraft:oak_fence", "minecraft:oak_door", "minecraft:glass_pane"};
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 1_000_000, day);
            CitizenGameTests.storehouse(helper, 0, 0, 1).insertItem(0, new ItemStack(Items.OAK_LOG, 64), false);
            ConstructionBook book = KingdomWorldApi.state(server).construction();
            CommerceSavedData data = CommerceSavedData.get(server);
            for (int i = 0; i < 21; i++) {
                Building house = craftHouse(helper, kingdom, new BlockPos(3, 0, 3), new BlockPos(8, 3, 8));
                Citizen master = CitizenLife.arriveInto(server, kingdom.id(), bed(helper, kingdom, 1 + i % 7 * 2, 10 + i / 7 * 3),
                        bed(helper, kingdom, 1 + i % 7 * 2, 10 + i / 7 * 3), false).orElseThrow(() -> new IllegalStateException("No citizen came"));
                WorldPos at = house.anchor();
                Business.Place place = new Business.Place(Business.PlaceKind.CRAFT_HOUSE, house.id(), 0, at.dimension(), at.x(), at.y(), at.z());
                data.putSurvey(place.key(), new CommerceSavedData.Survey(Set.of("crafting_table", "furnace", "smoker", "stonecutter"), day));
                Business shop = data.open(kingdom.id(), 0, place, day, 200);
                String trade = trades[i % trades.length];
                shop.changeTrade(trade, day);
                shop.setPinned(trade);
                shop.setKeepers(List.of(master.id()), false);
                master.setBusiness(shop.id());
            }
            KingdomWorldState state = KingdomWorldApi.state(server);
            state.orders().setBuying(kingdom.id(), new io.github.sekerator.kingdomworld.core.production.OrderBook.Buying(true, 200, 1_000_000L));
            for (int i = 0; i < 40; i++) {
                state.orders().add(kingdom.id(), items[i % items.length], Order.Kind.MAKE, 4 + i, Order.Priority.NORMAL,
                        KingdomWorldApi.gameTime(server));
            }
            Production.plan(server, kingdom.id());
            long total = 0;
            long worst = 0;
            int rounds = 20;
            for (int i = 0; i < rounds; i++) {
                long start = System.nanoTime();
                CrownPurchases.step(server, kingdom.id());
                long took = System.nanoTime() - start;
                total += took;
                worst = Math.max(worst, took);
            }
            com.mojang.logging.LogUtils.getLogger().info(String.format(java.util.Locale.ROOT, "Load bench: the purchases of the crown with %d "
                    + "shops and %d orders: %.3f ms on average, %.3f ms at worst (%d rounds)", data.shopsOf(kingdom.id()).size(),
                    state.orders().ordersOf(kingdom.id()).size(), total / 1e6 / rounds, worst / 1e6, rounds));
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }
}
