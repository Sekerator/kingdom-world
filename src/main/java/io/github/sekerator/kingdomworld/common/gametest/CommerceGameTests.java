package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.commerce.CityTreasuryActions;
import io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData;
import io.github.sekerator.kingdomworld.common.commerce.CommerceWork;
import io.github.sekerator.kingdomworld.common.commerce.ShopActions;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.CoinItem;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
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
import java.util.UUID;

/**
 * Game tests of the money of the cities and the shops of citizens: a house of a merchant opens a shop that buys of the crown
 * and sells, a city without shops lets go what its purse cannot keep, players buy with the account and with coins, a counter
 * is placed only in a city and a broken one closes its shop, grants and recalls need the right and the money, an old world gets
 * treasuries and family shops, and a city handed over or a kingdom dissolved loses no coin.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommerceGameTests {
    private static final String EMPTY = "empty";

    private CommerceGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static WorldPos bed(GameTestHelper helper, Kingdom kingdom, int x, int z) {
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
     * A house of a merchant of the kingdom in the register of buildings.
     */
    private static Building merchantHouse(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to) {
        MinecraftServer server = helper.getLevel().getServer();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        long id = book.nextBuildingId();
        book.restoreCounters(0, id + 1);
        long now = KingdomWorldApi.gameTime(server);
        BlockPos a = helper.absolutePos(from);
        BlockPos b = helper.absolutePos(to);
        Building building = new Building(id, kingdom.id(), "kingdomworld:classic_house_shop", "house", "housing", BuildingRoles.HOUSE, 1,
                Materials.NONE, new WorldPos(Dimensions.id(helper.getLevel()), a.getX(), a.getY(), a.getZ()), Placement.NONE,
                new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                        Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())), 0, now, true, 100, now, false, new java.util.BitSet());
        book.putBuilding(building);
        BuildingEffects.changed();
        return building;
    }

    private static Citizen livingIn(GameTestHelper helper, Kingdom kingdom, WorldPos bed) {
        MinecraftServer server = helper.getLevel().getServer();
        return CitizenLife.arriveInto(server, kingdom.id(), bed, bed, false).orElseThrow(() -> new IllegalStateException("No citizen came"));
    }

    private static ServerPlayer king(GameTestHelper helper, UUID id, int x, int z) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "kw-shopper"));
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void live(MinecraftServer server, Kingdom kingdom, long day) {
        EconomyEvents.payday(server, kingdom, day);
        CommerceWork.drain(server);
    }

    private static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    // ---------------------------------------------------------------- shops

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_shop", timeoutTicks = 200)
    public static void aMerchantHouseOpensAShopThatBuysOfTheCrownAndSells(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Shop", UUID.randomUUID());
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 1);
            storehouse.insertItem(0, new ItemStack(Items.BREAD, 64), false);
            storehouse.insertItem(1, new ItemStack(Items.TORCH, 64), false);
            storehouse.insertItem(2, new ItemStack(Items.LEATHER, 64), false);
            merchantHouse(helper, kingdom, new BlockPos(3, 0, 3), new BlockPos(8, 3, 8));
            Citizen keeper = livingIn(helper, kingdom, bed(helper, kingdom, 5, 5));
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 5_000, day(server));
            long day = day(server);
            live(server, kingdom, day + 1);
            List<Business> shops = CommerceSavedData.get(server).shopsOf(kingdom.id(), 0);
            helper.assertTrue(shops.size() == 1, "The house of a merchant opened no shop: " + shops.size());
            helper.assertTrue(shops.get(0).keepers().contains(keeper.id()), "The townsperson of the house does not keep it");
            economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(kingdom.id(), 0), 300, LedgerCategory.TRANSFERS, day + 1);
            live(server, kingdom, day + 2);
            Business shop = CommerceSavedData.get(server).shopsOf(kingdom.id(), 0).get(0);
            helper.assertTrue(shop.debt() > 0 || !shop.goods().isEmpty() || shop.day(day + 2).map(Business.Day::sales).orElse(0L) > 0,
                    "The shop got no goods of the crown");
            economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(kingdom.id(), 0), 300, LedgerCategory.TRANSFERS, day + 2);
            live(server, kingdom, day + 3);
            long sold = 0;
            for (Business found : CommerceSavedData.get(server).shopsOf(kingdom.id(), 0)) {
                sold += found.day(day + 3).map(Business.Day::sales).orElse(0L) + found.day(day + 2).map(Business.Day::sales).orElse(0L);
            }
            helper.assertTrue(sold > 0, "The townspeople bought nothing in the shop");
            CityTreasury city = economy.cityTreasury(kingdom.id(), 0).orElseThrow();
            helper.assertTrue(city.journal().income(CityLedger.SALES_TAX, day, day + 3) > 0, "No sales tax came into the treasury of the city");
            TreasuryJournal.Totals cities = economy.treasury(kingdom.id()).orElseThrow().journal().totals(day + 3, TreasuryJournal.Period.WEEK)
                    .getOrDefault(LedgerCategory.CITIES, TreasuryJournal.Totals.ZERO);
            TreasuryJournal.Totals commerce = economy.treasury(kingdom.id()).orElseThrow().journal().totals(day + 3, TreasuryJournal.Period.WEEK)
                    .getOrDefault(LedgerCategory.COMMERCE, TreasuryJournal.Totals.ZERO);
            helper.assertTrue(cities.income() > 0, "The share of the crown did not reach the treasury of the kingdom");
            helper.assertTrue(commerce.income() > 0, "The crown sold the shop nothing");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_none", timeoutTicks = 200)
    public static void aCityWithoutShopsLetsGoWhatItsPurseCannotKeep(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest No Shops", UUID.randomUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, day(server));
            long day = day(server);
            live(server, kingdom, day + 1);
            CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom.id(), 0);
            // Money that came in long ago and does not count as what comes in now.
            if (economy.purse(purse) == 0) {
                economy.restorePurse(purse, 10_000);
            }
            long before = economy.purse(purse);
            live(server, kingdom, day + 2);
            helper.assertTrue(economy.purse(purse) < before, "The purse kept more than its days: " + economy.purse(purse));
            helper.assertTrue(CommerceSavedData.get(server).shopsOf(kingdom.id(), 0).isEmpty(), "A shop opened without a place");
            helper.assertTrue(economy.cityTreasury(kingdom.id(), 0).orElseThrow().statements().size() >= 1, "The city lived no day");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_short", timeoutTicks = 200)
    public static void aCityLeftShortOfGoodsWithoutShopsOpensAShopAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Short", UUID.randomUUID());
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 1);
            storehouse.insertItem(0, new ItemStack(Items.BREAD, 64), false);
            merchantHouse(helper, kingdom, new BlockPos(3, 0, 3), new BlockPos(8, 3, 8));
            livingIn(helper, kingdom, bed(helper, kingdom, 5, 5));
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 5_000, day(server));
            long day = day(server);
            // The shops of the city were short of goods of the crown on the day its last shop closed.
            CommerceSavedData.get(server).city(kingdom.id(), 0).markShortForTests("minecraft:bread");
            live(server, kingdom, day + 1);
            List<Business> shops = CommerceSavedData.get(server).shopsOf(kingdom.id(), 0);
            helper.assertTrue(shops.size() == 1, "A city short of goods on the day its last shop closed opens no shop again: " + shops.size());
            helper.assertFalse(CommerceSavedData.get(server).city(kingdom.id(), 0).crownShort(), "The city is still short of goods without shops");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_buy", timeoutTicks = 200)
    public static void aPlayerBuysWithTheAccountAndWithCoins(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Buyer", kingId);
        try {
            Citizen keeper = livingIn(helper, kingdom, bed(helper, kingdom, 6, 6));
            BlockPos counter = helper.absolutePos(new BlockPos(4, 0, 4));
            helper.getLevel().setBlockAndUpdate(counter, ModRegistries.SHOP_COUNTER.get().defaultBlockState());
            CommerceSavedData data = CommerceSavedData.get(server);
            String dimension = Dimensions.id(helper.getLevel());
            data.putCounter(new CommerceSavedData.Counter(kingdom.id(), dimension, counter.getX(), counter.getY(), counter.getZ()));
            Business shop = data.open(kingdom.id(), 0, new Business.Place(Business.PlaceKind.COUNTER, 0, 0, dimension, counter.getX(),
                    counter.getY(), counter.getZ()), day(server), 200);
            shop.setKeepers(List.of(keeper.id()), false);
            keeper.setBusiness(shop.id());
            shop.addGoods("minecraft:bread", 0, 20, 3_000);
            ServerPlayer player = king(helper, kingId, 3, 3);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(kingId, kingdom.id(), 500);
            CommercePackets.ShopTrade account = new CommercePackets.ShopTrade(shop.id(), "minecraft:bread", 3, 1_000, true);
            helper.assertTrue(ShopActions.purchase(player, shop, account).success(), "Buying from the account was refused");
            helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 3, "The bread did not come into the inventory");
            helper.assertTrue(economy.accounts().balance(kingId, kingdom.id()) < 500, "Nothing was taken from the account");
            helper.assertTrue(economy.purse(CityPurses.PurseKey.till(kingdom.id(), shop.id())) > 0, "The till got nothing");
            helper.assertTrue(economy.purse(CityPurses.PurseKey.cityTreasury(kingdom.id(), 0)) > 0, "The treasury of the city got no tax");
            player.getInventory().add(CoinItem.stack(kingdom.id(), Denomination.SILVER, 5));
            CommercePackets.ShopTrade coins = new CommercePackets.ShopTrade(shop.id(), "minecraft:bread", 2, 1_000, false);
            helper.assertTrue(ShopActions.purchase(player, shop, coins).success(), "Buying with coins was refused");
            helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 5, "The bread bought with coins did not come");
            helper.assertTrue(shop.count("minecraft:bread") == 15, "The shop did not give the bread away: " + shop.count("minecraft:bread"));
            CommercePackets.ShopTrade cheap = new CommercePackets.ShopTrade(shop.id(), "minecraft:bread", 1, 1, true);
            helper.assertFalse(ShopActions.purchase(player, shop, cheap).success(), "A price higher than the one seen was taken");
            helper.assertTrue(shop.count("minecraft:bread") == 15, "A refused purchase took goods");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_counter", timeoutTicks = 200)
    public static void aCounterStandsOnlyInACityAndABrokenOneClosesItsShop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Counter", kingId);
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 1);
            ServerPlayer player = king(helper, kingId, 3, 3);
            BlockPos far = helper.absolutePos(new BlockPos(2, 0, 2)).offset(400, 0, 400);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.commerce.BusinessPlaces.counterCity(server, kingdom.id(),
                    Dimensions.id(helper.getLevel()), far.getX(), far.getZ()).isEmpty(), "A counter far beyond the city would trade");
            BlockPos counter = helper.absolutePos(new BlockPos(4, 0, 4));
            helper.getLevel().setBlockAndUpdate(counter, ModRegistries.SHOP_COUNTER.get().defaultBlockState());
            ModRegistries.SHOP_COUNTER.get().setPlacedBy(helper.getLevel(), counter, helper.getLevel().getBlockState(counter), player,
                    new ItemStack(ModRegistries.SHOP_COUNTER_ITEM.get()));
            CommerceSavedData data = CommerceSavedData.get(server);
            String key = CommerceSavedData.counterKey(Dimensions.id(helper.getLevel()), counter.getX(), counter.getY(), counter.getZ());
            helper.assertTrue(data.counters().containsKey(key), "The king could not put up a counter in the capital");
            Citizen keeper = livingIn(helper, kingdom, bed(helper, kingdom, 6, 6));
            Business shop = data.open(kingdom.id(), 0, new Business.Place(Business.PlaceKind.COUNTER, 0, 0, Dimensions.id(helper.getLevel()),
                    counter.getX(), counter.getY(), counter.getZ()), day(server), 200);
            shop.setKeepers(List.of(keeper.id()), false);
            shop.addGoods("minecraft:torch", 3, 10, 1_000);
            KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), 1_000, day(server));
            helper.getLevel().removeBlock(counter, false);
            helper.assertTrue(data.shop(shop.id()).isEmpty(), "The shop of a broken counter stays open");
            helper.assertTrue(!data.counters().containsKey(key), "A broken counter is still known");
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.TORCH) == 10, "The crown did not buy back the goods");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- grants and recalls

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_grant", timeoutTicks = 200)
    public static void grantsAndRecallsNeedTheRightAndTheMoney(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Grant", kingId);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, day(server));
            ServerPlayer king = king(helper, kingId, 3, 3);
            ServerPlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "kw-stranger"));
            helper.assertFalse(CityTreasuryActions.grant(stranger, kingdom.id(), 0, 100).success(), "A stranger granted money");
            helper.assertFalse(CityTreasuryActions.grant(king, kingdom.id(), 0, 1_001).success(), "More than the crown has was granted");
            helper.assertTrue(CityTreasuryActions.grant(king, kingdom.id(), 0, 400).success(), "The king could not grant");
            helper.assertTrue(economy.purse(CityPurses.PurseKey.cityTreasury(kingdom.id(), 0)) == 400, "The grant did not arrive");
            helper.assertFalse(CityTreasuryActions.recall(king, kingdom.id(), 0, 401).success(), "More than the city holds was taken");
            helper.assertFalse(CityTreasuryActions.recall(stranger, kingdom.id(), 0, 100).success(), "A stranger took money back");
            helper.assertTrue(CityTreasuryActions.recall(king, kingdom.id(), 0, 400).success(), "The king could not take the grant back");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 1_000, "Money was made or lost");
            helper.assertFalse(economy.cityTreasury(kingdom.id(), 0).orElseThrow().recalled(day(server)), "Taking back a grant offended");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- old worlds

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_old", timeoutTicks = 200)
    public static void anOldWorldGetsTreasuriesOfCitiesAndFamilyShops(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Old World", UUID.randomUUID());
        try {
            merchantHouse(helper, kingdom, new BlockPos(3, 0, 3), new BlockPos(8, 3, 8));
            Citizen worker = livingIn(helper, kingdom, bed(helper, kingdom, 5, 5));
            // The only person of the house has a post: its family keeps the shop in the evening.
            KingdomWorldApi.change(server, "give a profession", state -> {
                state.citizens().setProfession(worker, "lumberjack");
                return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
            });
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, day(server));
            helper.assertTrue(economy.cityTreasuriesOf(kingdom.id()).isEmpty(), "A treasury of a city before the first payday");
            long day = day(server);
            live(server, kingdom, day + 1);
            helper.assertTrue(economy.cityTreasury(kingdom.id(), 0).isPresent(), "The capital got no treasury on its payday");
            List<Business> shops = CommerceSavedData.get(server).shopsOf(kingdom.id(), 0);
            helper.assertTrue(shops.size() == 1 && shops.get(0).family(), "The house opened no family shop");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- war and dissolving

    @GameTest(template = EMPTY, batch = "kingdomworld_commerce_war", timeoutTicks = 200)
    public static void aCityHandedOverAndAKingdomDissolvedLoseNoCoin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom loser = CitizenGameTests.found(helper, "GameTest Loser", UUID.randomUUID());
        Kingdom winner = null;
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            winner = KingdomWorldApi.kingdom(server, economy.treasuries().stream().map(t -> t.kingdomId())
                    .filter(id -> !id.equals(loser.id())).findFirst().orElse(loser.id())).orElse(null);
            if (winner == null || winner.id().equals(loser.id())) {
                // Any other kingdom of the world takes the city.
                helper.succeed();
                return;
            }
            UUID winnerId = winner.id();
            WorldPos hall = CitizenGameTests.at(helper, 6, 0, 6);
            City city = RoadWork.state(server).addCity(loser.id(), "Testtown", hall.dimension(), hall.x(), hall.y(), hall.z(),
                    KingdomWorldApi.gameTime(server));
            economy.adminTreasury(loser.id(), 2_000, day(server));
            economy.cityGrant(loser.id(), city.id(), 300, day(server));
            economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(loser.id(), city.id()), 200, LedgerCategory.TRANSFERS, day(server));
            long before = economy.treasury(winnerId).orElseThrow().reserve(loser.id());
            long supply = economy.supply(loser.id());
            CityWalls.handOver(server, city.id(), loser.id(), winnerId, city.id());
            helper.assertTrue(economy.treasury(winnerId).orElseThrow().reserve(loser.id()) - before == 500,
                    "The treasury and the purse of the city did not go to the winner");
            helper.assertTrue(economy.supply(loser.id()) == supply, "The money of the loser was made or lost");
            economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(loser.id(), 0), 100, LedgerCategory.TRANSFERS, day(server));
            long funds = economy.treasury(loser.id()).orElseThrow().funds();
            long swept = economy.sweepPurses(loser.id(), day(server));
            helper.assertTrue(swept == 100 && economy.treasury(loser.id()).orElseThrow().funds() == funds + 100,
                    "The purses of a kingdom about to go did not join its treasury");
            RoadWork.state(server).removeCity(city.id());
        } finally {
            CitizenGameTests.finish(helper, loser);
        }
        helper.assertTrue(KingdomWorldApi.state(server).economy().purses().keysOf(loser.id()).isEmpty(), "Purses of a dissolved kingdom stay");
        helper.succeed();
    }
}
