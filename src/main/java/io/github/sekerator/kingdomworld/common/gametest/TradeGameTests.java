package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.CoinItem;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.MinterBehavior;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.ExchangeActions;
import io.github.sekerator.kingdomworld.common.trade.MarketActions;
import io.github.sekerator.kingdomworld.common.trade.MerchantBehavior;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.common.trade.TradeActions;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.production.Stations;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Game tests of trade: a player buys at a stall with coins and from the account, the kingdom buys within its budget, a
 * lot sells with the tax for the treasury and is taken back, merchants sell surplus abroad within the day's limit, goods
 * ordered from abroad arrive after the delay, exchanges keep every currency's money and stay within the treasury,
 * old coins melt back into metal, caravans deliver and pay or are lost and give the payment back, the server checks the
 * rights of the market and of deals, the treasury's vault gives coins to the king, and trade survives saving. Every test
 * runs in a batch of its own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeGameTests {
    private static final String EMPTY = "empty";
    /** Blocks between the first and the second kingdom of a test: seven chunks. */
    static final int SECOND = 112;

    private TradeGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(1, 0, 1));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID player) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
    }

    private static Treasury treasury(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow();
    }

    private static long coins(Inventory inventory, UUID currency) {
        return CoinStacks.count(inventory, currency).value(CurrencyUnits.DEFAULT);
    }

    /**
     * The trade rules of the config with other limits.
     */
    private static TradeRules rules(int exportPerMerchant, long maxIncome, long importDelay, double loss) {
        TradeRules d = TradeRules.DEFAULT;
        return new TradeRules(d.exportFactor(), d.importFactor(), exportPerMerchant, 0.0, maxIncome, d.importMaxPerDay(), importDelay,
                d.demandElasticity(), d.minPricePercent(), d.maxPricePercent(), d.stockTarget(), d.spreadPercent(), d.defaultSalesTaxPercent(),
                d.maxSalesTaxPercent(), d.maxLotsPerPlayer(), d.maxEntries(), d.defaultExchangeFeePercent(), d.maxExchangeFeePercent(),
                d.minRate(), d.maxRate(), d.rateSmoothingPercent(), d.productionDays(), d.minSupply(), d.caravanBlocksPerMinute(), loss,
                d.proposalDays(), d.maxDealsPerKingdom(), d.maxPeriodDays());
    }

    private static TradePackets.MarketChange change(Kingdom kingdom, TradePackets.MarketChange.Action action, String item, long price,
                                                    long first, long second) {
        return new TradePackets.MarketChange(kingdom.id(), action, item, price, first, second);
    }

    /**
     * A second kingdom seven chunks east of the first, with a storehouse.
     */
    static Kingdom second(GameTestHelper helper, String name, UUID king) {
        ServerLevel level = helper.getLevel();
        BlockPos throne = helper.absolutePos(new BlockPos(2 + SECOND, 0, 2));
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, 0x1F618D, king);
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        throw new IllegalStateException("The second kingdom was not founded: " + result);
    }

    static void clearSecond(GameTestHelper helper) {
        GatheringGameTests.clear(helper.getLevel(), helper.absolutePos(new BlockPos(SECOND - 1, 0, -1)),
                helper.absolutePos(new BlockPos(SECOND + 8, 3, 8)));
    }

    /**
     * A merchant at a post with a stall next to it, keeping the stall open.
     */
    private static Citizen merchant(GameTestHelper helper, Kingdom kingdom, BlockPos stall, BlockPos post) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.setBlock(stall, ModRegistries.MARKET_STALL.get());
        WorldPos postPos = GatheringGameTests.post(helper, post, "merchant");
        WorkPost found = PostWork.post(server, postPos).orElseThrow();
        ZoneSurveys.surveyNow(server, found);
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 3);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), "merchant"));
        KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, postPos);
        return citizen;
    }

    // ---------------------------------------------------------------- the stall

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_stall")
    public static void aPlayerBuysAtAStallWithCoinsAndFromTheAccount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bread Market", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.BREAD, 20), false);
        ServerPlayer buyer = player(helper, UUID.randomUUID(), "kw-buyer");
        Citizen merchant = null;
        try {
            BlockPos stall = helper.absolutePos(new BlockPos(6, 0, 1));
            helper.assertTrue(StallActions.access(buyer, kingdom, stall) != null, "A stall without a merchant was open");
            merchant = merchant(helper, kingdom, new BlockPos(6, 0, 1), new BlockPos(5, 0, 1));
            helper.assertTrue(StallActions.access(buyer, kingdom, stall) == null, "A merchant did not open the stall");
            helper.assertTrue(MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_SELL,
                    "minecraft:bread", 5, 5, 0)).success(), "The bread was not put on sale");

            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(buyer.getUUID(), kingdom.id(), 50);
            buyer.getInventory().add(CoinItem.stack(kingdom.id(), Denomination.GOLD, 1));
            long before = coins(buyer.getInventory(), kingdom.id()) + economy.supply(kingdom.id());

            helper.assertTrue(StallActions.buy(buyer, kingdom, "minecraft:bread", 3, false, kingdom.id()).success(), "Buying with coins failed");
            helper.assertTrue(buyer.getInventory().countItem(Items.BREAD) == 3, "The bread did not arrive");
            helper.assertTrue(coins(buyer.getInventory(), kingdom.id()) == 85, "The change is wrong: " + coins(buyer.getInventory(), kingdom.id()));
            helper.assertTrue(StallActions.buy(buyer, kingdom, "minecraft:bread", 2, true, kingdom.id()).success(), "Buying from the account failed");
            helper.assertTrue(economy.accounts().balance(buyer.getUUID(), kingdom.id()) == 40, "The account was not charged");
            helper.assertTrue(treasury(server, kingdom).funds() == 25, "The treasury did not get 25: " + treasury(server, kingdom).funds());
            long after = coins(buyer.getInventory(), kingdom.id()) + economy.supply(kingdom.id());
            helper.assertTrue(after == before, "Money was made or lost: " + before + " before, " + after + " after");

            // The merchant may have eaten some bread meanwhile: what is kept counts from what is there now.
            long stock = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD));
            helper.assertTrue(!StallActions.buy(buyer, kingdom, "minecraft:bread", (int) stock - 4, true, kingdom.id()).success(),
                    "The kingdom sold bread it keeps");
            helper.assertTrue(!StallActions.buy(buyer, kingdom, "minecraft:bread", 9, true, kingdom.id()).success(),
                    "Bread was sold for more than the account holds");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) == stock, "Bread left the storehouses");
            helper.assertTrue(!StallActions.buy(buyer, kingdom, "minecraft:bread", 1, true, UUID.randomUUID()).success(),
                    "An unknown currency was taken");
        } finally {
            if (merchant != null) {
                CitizenGameTests.finish(helper, kingdom, merchant);
            } else {
                KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            }
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_buying")
    public static void theKingdomBuysFromPlayersWithinItsBudget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Grain Buyers", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4);
        ServerPlayer seller = player(helper, UUID.randomUUID(), "kw-farmer");
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_BUY, "minecraft:wheat", 3, 100, 30));
            seller.getInventory().add(new ItemStack(Items.WHEAT, 64));
            long before = economy.supply(kingdom.id());
            helper.assertTrue(StallActions.sell(seller, kingdom, "minecraft:wheat", 20, true).success(), "The sale failed");
            helper.assertTrue(seller.getInventory().countItem(Items.WHEAT) == 54, "The budget allows ten: " + seller.getInventory().countItem(Items.WHEAT));
            helper.assertTrue(economy.accounts().balance(seller.getUUID(), kingdom.id()) == 30, "The seller was not paid 30");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.WHEAT)) == 10, "The wheat is not in the storehouses");
            helper.assertTrue(economy.supply(kingdom.id()) == before, "Buying made or lost money");
            helper.assertTrue(!StallActions.sell(seller, kingdom, "minecraft:wheat", 1, true).success(), "The budget of the day was not kept");

            // Paid in coins, with a new budget: the stock limit holds.
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_BUY, "minecraft:wheat", 3, 12, 0));
            helper.assertTrue(StallActions.sell(seller, kingdom, "minecraft:wheat", 5, false).success(), "Selling for coins failed");
            helper.assertTrue(coins(seller.getInventory(), kingdom.id()) == 6, "Two wheat up to the stock of twelve, paid 6 in coins");
            helper.assertTrue(!StallActions.sell(seller, kingdom, "minecraft:wheat", 1, true).success(), "The kingdom bought beyond its stock");
            helper.assertTrue(!StallActions.sell(seller, kingdom, "minecraft:carrot", 1, true).success(), "The kingdom bought what it does not buy");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_lots")
    public static void aLotSellsWithTheTaxAndIsTakenBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lot Fair", UUID.randomUUID());
        ServerPlayer seller = player(helper, UUID.randomUUID(), "kw-smith");
        ServerPlayer buyer = player(helper, UUID.randomUUID(), "kw-knight");
        join(server, kingdom, seller.getUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.SET_TAX, "", 0, 50, 0));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 3);
            seller.getInventory().selected = 0;
            seller.getInventory().setItem(0, sword.copy());
            helper.assertTrue(!StallActions.listLot(buyer, kingdom, 1, 100).success(), "An outsider put up a lot");
            helper.assertTrue(StallActions.listLot(seller, kingdom, 1, 100).success(), "The lot was not put up");
            helper.assertTrue(seller.getInventory().getItem(0).isEmpty(), "The sword stayed in the hand");
            Market market = KingdomWorldApi.state(server).trade().market(kingdom.id(), TradeSettings.rules());
            long lot = market.lots().get(0).id();
            economy.adminAccount(buyer.getUUID(), kingdom.id(), 150);
            long before = economy.supply(kingdom.id());
            helper.assertTrue(StallActions.buyLot(buyer, kingdom, lot, true, kingdom.id()).success(), "The lot was not sold");
            int slot = buyer.getInventory().findSlotMatchingItem(sword);
            helper.assertTrue(slot >= 0, "The sword did not arrive with its enchantment");
            helper.assertTrue(economy.accounts().balance(seller.getUUID(), kingdom.id()) == 95, "The seller did not get the price less 5%");
            helper.assertTrue(treasury(server, kingdom).funds() == 5, "The tax did not reach the treasury");
            helper.assertTrue(economy.supply(kingdom.id()) == before, "The sale made or lost money");
            helper.assertTrue(!StallActions.buyLot(buyer, kingdom, lot, true, kingdom.id()).success(), "A lot was sold twice");

            seller.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
            helper.assertTrue(StallActions.listLot(seller, kingdom, 4, 500).success(), "The second lot was not put up");
            helper.assertTrue(seller.getInventory().countItem(Items.EMERALD) == 6, "The lot took the wrong number");
            long second = market.lots().get(0).id();
            helper.assertTrue(!StallActions.withdrawLot(buyer, kingdom, second).success(), "Another player took the lot back");
            helper.assertTrue(StallActions.withdrawLot(seller, kingdom, second).success(), "The seller could not take the lot back");
            helper.assertTrue(seller.getInventory().countItem(Items.EMERALD) == 10 && market.lots().isEmpty(), "The lot did not come back whole");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- abroad

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_export")
    public static void merchantsSellSurplusAbroadWithinTheDailyLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Export House", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false);
        store.insertItem(1, new ItemStack(Items.IRON_INGOT, 36), false);
        Citizen merchant = merchant(helper, kingdom, new BlockPos(6, 0, 1), new BlockPos(5, 0, 1));
        TradeSettings.override(rules(32, 10_000, 0, 0.0));
        try {
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_EXPORT, "minecraft:iron_ingot", 0,
                    50, 0));
            helper.assertTrue(MerchantBehavior.working(server, kingdom.id()).size() == 1, "The merchant does not work");
            TradeWork.exportStep(server, kingdom.id(), 24_000);
            TradeWork.exportStep(server, kingdom.id(), 24_000);
            long left = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT));
            helper.assertTrue(left == 68, "A merchant sells 32 a day: " + left + " left of 100");
            Treasury treasury = treasury(server, kingdom);
            helper.assertTrue(treasury.funds() == 32 * 12, "Iron sells abroad for 60% of its worth: " + treasury.funds());
            long day = TreasuryActions.day(server);
            helper.assertTrue(treasury.journal().totals(day, TreasuryJournal.Period.TODAY).get(LedgerCategory.TRADE).income() == 384,
                    "The journal does not show the trade");
            helper.assertTrue(merchant.experience() > 0, "The merchant learned nothing");

            // A low limit of income stops the merchants early, and they never sell below the threshold.
            TradeSettings.override(rules(1_000, 384 + 60, 0, 0.0));
            TradeWork.exportStep(server, kingdom.id(), 24_000);
            long after = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT));
            helper.assertTrue(after == 63, "The limit of income allows five more: " + after);
            TradeSettings.override(rules(1_000, 1_000_000, 0, 0.0));
            TradeWork.exportStep(server, kingdom.id(), 24_000);
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT)) == 50, "Iron below the threshold was sold");
        } finally {
            TradeSettings.override(null);
            CitizenGameTests.finish(helper, kingdom, merchant);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_import")
    public static void goodsFromAbroadArriveAfterTheDelay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Import Office", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4);
        TradeSettings.override(rules(64, 2_000, 1_000_000, 0.0));
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            helper.assertTrue(!MarketActions.orderImport(server, null, kingdom.id(), Items.NETHER_STAR, 1).success(), "A nether star came from abroad");
            helper.assertTrue(!MarketActions.orderImport(server, null, kingdom.id(), Items.ZOMBIE_SPAWN_EGG, 1).success(), "A spawn egg came from abroad");
            helper.assertTrue(!MarketActions.orderImport(server, null, kingdom.id(), Items.IRON_SWORD, 1).success(), "A made thing came from abroad");
            helper.assertTrue(MarketActions.orderImport(server, null, kingdom.id(), Items.IRON_INGOT, 10).success(), "Iron could not be ordered");
            helper.assertTrue(treasury(server, kingdom).funds() == 700, "Ten iron ingots cost 150% of 200: " + treasury(server, kingdom).funds());
            TradeWork.tick(server);
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT)) == 0, "The iron came too soon");
            helper.assertTrue(!MarketActions.orderImport(server, null, kingdom.id(), Items.IRON_INGOT, 50).success(), "An order beyond the treasury");

            TradeSettings.override(rules(64, 2_000, 0, 0.0));
            helper.assertTrue(MarketActions.orderImport(server, null, kingdom.id(), Items.COBBLESTONE, 64).success(), "Stone could not be ordered");
            TradeWork.tick(server);
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.COBBLESTONE)) == 64, "The stone did not arrive");
            helper.assertTrue(KingdomWorldApi.state(server).trade().market(kingdom.id(), TradeSettings.rules()).imports().size() == 1,
                    "The iron order should still wait");
        } finally {
            TradeSettings.override(null);
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- exchange

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_exchange")
    public static void exchangesKeepTheMoneyAndStayWithinTheTreasury(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom bank = CitizenGameTests.found(helper, "GameTest Exchange Bank", UUID.randomUUID());
        Kingdom other = KingdomWorldApi.createKingdom(server, "GameTest Far Realm", null) instanceof CreateKingdomResult.Created created
                ? created.kingdom() : null;
        ServerPlayer traveller = player(helper, UUID.randomUUID(), "kw-traveller");
        try {
            helper.assertTrue(other != null, "The other kingdom was not founded");
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(bank.id(), 300, 0);
            economy.adminAccount(traveller.getUUID(), other.id(), 1_000);
            MarketActions.apply(server, null, bank.id(), change(bank, TradePackets.MarketChange.Action.SET_FEE, "", 0, 20, 0));
            long bankMoney = economy.supply(bank.id());
            long otherMoney = economy.supply(other.id());
            helper.assertTrue(!ExchangeActions.exchange(traveller, bank, other.id(), 500, true, true).success(), "The bank paid more than it has");
            helper.assertTrue(ExchangeActions.exchange(traveller, bank, other.id(), 200, true, true).success(), "The exchange failed");
            helper.assertTrue(economy.accounts().balance(traveller.getUUID(), bank.id()) == 196, "200 at a fee of 2% give 196");
            helper.assertTrue(treasury(server, bank).reserve(other.id()) == 200, "The other currency went into the reserves");
            helper.assertTrue(economy.supply(bank.id()) == bankMoney && economy.supply(other.id()) == otherMoney, "The exchange made money");

            // With coins: the change comes back in coins of the other currency, the bank pays in its own coins.
            traveller.getInventory().add(CoinItem.stack(other.id(), Denomination.GOLD, 2));
            helper.assertTrue(ExchangeActions.exchange(traveller, bank, other.id(), 50, false, false).success(), "The exchange of coins failed");
            helper.assertTrue(coins(traveller.getInventory(), other.id()) == 150, "The change in coins is wrong");
            helper.assertTrue(coins(traveller.getInventory(), bank.id()) == 49, "The bank's coins are wrong");
            helper.assertTrue(treasury(server, bank).reserve(other.id()) == 250 && treasury(server, bank).funds() == 300 - 196 - 49,
                    "The treasury does not hold what it should");
            helper.assertTrue(!ExchangeActions.exchange(traveller, bank, bank.id(), 10, true, true).success(), "A currency was exchanged for itself");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, bank.id());
            if (other != null) {
                KingdomWorldApi.dissolveKingdom(server, other.id());
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- old coins

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_melt")
    public static void oldCoinsMeltBackIntoMetalAtTheMint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Melting Mint", UUID.randomUUID());
        Kingdom gone = KingdomWorldApi.createKingdom(server, "GameTest Fallen Realm", null) instanceof CreateKingdomResult.Created created
                ? created.kingdom() : null;
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        helper.setBlock(new BlockPos(6, 0, 1), ModRegistries.MINT.get());
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "minter");
        WorkPost post = PostWork.post(server, postPos).orElseThrow();
        ZoneSurveys.surveyNow(server, post);
        try {
            helper.assertTrue(gone != null, "The fallen kingdom was not founded");
            KingdomWorldApi.dissolveKingdom(server, gone.id());
            store.insertItem(0, CoinItem.stack(gone.id(), Denomination.SILVER, 5), false);
            long funds = treasury(server, kingdom).funds();
            helper.assertTrue(MinterBehavior.strike(server, post, false) == MinterBehavior.Struck.DONE, "Old coins were not melted");
            helper.assertTrue(MinterBehavior.strike(server, post, false) == MinterBehavior.Struck.DONE, "Old coins were not melted again");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT)) == 2, "Four silver coins give two iron");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(ModRegistries.COIN.get())) == 1, "One coin is too few to melt");
            helper.assertTrue(treasury(server, kingdom).funds() == funds, "Melting made money");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- deals and caravans

    static Deal propose(MinecraftServer server, Kingdom seller, Kingdom buyer, int count, long price) {
        TradePackets.TradeChange change = new TradePackets.TradeChange(seller.id(), TradePackets.TradeChange.Action.PROPOSE, buyer.id(), 0,
                "minecraft:wheat", count, price, true, false, 0);
        if (!TradeActions.apply(server, null, seller.id(), change).success()) {
            throw new IllegalStateException("The deal was not proposed");
        }
        return KingdomWorldApi.state(server).trade().dealsOf(seller.id()).get(0);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_caravan")
    public static void aCaravanDeliversAndPaysAndSurvivesSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Wheat Coast", UUID.randomUUID());
        Kingdom buyer = second(helper, "GameTest Bakers Hill", UUID.randomUUID());
        StorehouseInventory sellerStore = CitizenGameTests.storehouse(helper, 0, 0, 4);
        sellerStore.insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        CitizenGameTests.storehouse(helper, SECOND, 0, 4);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            long buyerMoney = economy.supply(buyer.id());
            Deal deal = propose(server, seller, buyer, 32, 100);
            helper.assertTrue(!TradeWork.accept(server, null, seller.id(), deal.id()).ok(), "The proposer accepted its own deal");
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            helper.assertTrue(KingdomStorage.count(server, seller.id(), stack -> stack.is(Items.WHEAT)) == 32, "The goods did not leave the seller");
            helper.assertTrue(treasury(server, buyer).funds() == 400, "The payment did not go into escrow");
            helper.assertTrue(economy.supply(buyer.id()) == buyerMoney, "Money in escrow vanished");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            helper.assertTrue(caravan.arriveTime() > caravan.departTime(), "The caravan does not travel");
            helper.assertTrue(KingdomWorldApi.state(server).trade().deal(deal.id()).orElseThrow().status() == Deal.Status.DONE, "The deal is not done");

            // A restart in the middle of the way loses nothing.
            KingdomSavedData loaded = KingdomSavedData.load(KingdomSavedData.get(server).save(new CompoundTag()));
            helper.assertTrue(!loaded.isReadOnly(), "Saved trade became read-only: " + loaded.readOnlyReason());
            Caravan saved = loaded.state().trade().caravan(caravan.id()).orElseThrow();
            helper.assertTrue(saved.equals(caravan), "The caravan changed in saving");
            helper.assertTrue(loaded.state().economy().escrow(caravan.escrowId()).orElseThrow().amount() == 100, "The escrow changed in saving");
            helper.assertTrue(loaded.state().economy().supply(buyer.id()) == buyerMoney, "Saving made or lost money");

            TradeWork.overrideRoll(() -> 1.0);
            TradeWork.arrive(server, caravan, KingdomWorldApi.gameTime(server));
            helper.assertTrue(KingdomStorage.count(server, buyer.id(), stack -> stack.is(Items.WHEAT)) == 32, "The goods did not arrive");
            helper.assertTrue(treasury(server, seller).reserve(buyer.id()) == 100, "The seller was not paid");
            helper.assertTrue(KingdomWorldApi.state(server).trade().caravans().isEmpty() && economy.escrows().isEmpty(), "The caravan stayed");
            helper.assertTrue(economy.supply(buyer.id()) == buyerMoney, "The delivery made or lost money");
        } finally {
            TradeWork.overrideRoll(null);
            KingdomWorldApi.dissolveKingdom(server, seller.id());
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            GatheringGameTests.clear(level, from, to);
            clearSecond(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_lost")
    public static void aLostCaravanGivesThePaymentBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Stormy Pass", UUID.randomUUID());
        Kingdom buyer = second(helper, "GameTest Waiting Town", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        CitizenGameTests.storehouse(helper, SECOND, 0, 4);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            Deal deal = propose(server, seller, buyer, 16, 80);
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            TradeWork.overrideRoll(() -> 0.0);
            TradeWork.arrive(server, caravan, KingdomWorldApi.gameTime(server));
            helper.assertTrue(treasury(server, buyer).funds() == 500, "The payment did not come back: " + treasury(server, buyer).funds());
            helper.assertTrue(treasury(server, seller).reserves().isEmpty(), "The seller was paid for lost goods");
            helper.assertTrue(KingdomStorage.count(server, buyer.id(), stack -> stack.is(Items.WHEAT)) == 0, "Lost goods arrived");
            helper.assertTrue(KingdomStorage.count(server, seller.id(), stack -> stack.is(Items.WHEAT)) == 48, "Lost goods came back");
            helper.assertTrue(KingdomWorldApi.state(server).trade().caravans().isEmpty() && economy.escrows().isEmpty(), "The caravan stayed");
        } finally {
            TradeWork.overrideRoll(null);
            KingdomWorldApi.dissolveKingdom(server, seller.id());
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            GatheringGameTests.clear(level, from, to);
            clearSecond(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_wait")
    public static void aCaravanThatWaitsTooLongGoesBackWithThePayment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Patient Fields", UUID.randomUUID());
        // The buyer has no storehouse at all: the caravan finds no room and waits.
        Kingdom buyer = second(helper, "GameTest Crowded Town", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            long buyerMoney = economy.supply(buyer.id());
            long sellerMoney = economy.supply(seller.id());
            TradePackets.TradeChange change = new TradePackets.TradeChange(seller.id(), TradePackets.TradeChange.Action.PROPOSE, buyer.id(), 0,
                    "minecraft:wheat", 16, 80, true, false, 2);
            helper.assertTrue(TradeActions.apply(server, null, seller.id(), change).success(), "The deal was not proposed");
            Deal deal = KingdomWorldApi.state(server).trade().dealsOf(seller.id()).get(0);
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            TradeWork.overrideRoll(() -> 1.0);
            TradeWork.arrive(server, caravan, caravan.arriveTime());
            Caravan waiting = KingdomWorldApi.state(server).trade().caravan(caravan.id()).orElseThrow();
            helper.assertTrue(waiting.state() == Caravan.State.WAITING, "The caravan does not wait for room: " + waiting.state());
            helper.assertTrue(treasury(server, buyer).funds() == 420, "The payment is not in escrow while the caravan waits");

            // Within the limit the caravan keeps waiting; after it the deal is closed.
            TradeSettings.overrideCaravanWait(1_000L);
            TradeWork.arrive(server, waiting, waiting.arriveTime() + 999L);
            helper.assertTrue(KingdomWorldApi.state(server).trade().caravan(caravan.id()).isPresent(), "The caravan gave up too early");
            TradeWork.arrive(server, waiting, waiting.arriveTime() + 1_000L);
            helper.assertTrue(KingdomWorldApi.state(server).trade().caravans().isEmpty(), "The caravan did not go back");
            helper.assertTrue(economy.escrows().isEmpty(), "The payment stayed in escrow");
            helper.assertTrue(treasury(server, buyer).funds() == 500, "The payment did not come back: " + treasury(server, buyer).funds());
            helper.assertTrue(treasury(server, seller).reserves().isEmpty(), "The seller was paid for goods that never arrived");
            helper.assertTrue(KingdomStorage.count(server, seller.id(), stack -> stack.is(Items.WHEAT)) == 64, "The goods did not come back");
            helper.assertTrue(economy.supply(buyer.id()) == buyerMoney && economy.supply(seller.id()) == sellerMoney, "Money was made or lost");
            helper.assertTrue(KingdomWorldApi.state(server).trade().deal(deal.id()).orElseThrow().status() == Deal.Status.CANCELLED,
                    "The regular deal is still open");
        } finally {
            TradeWork.overrideRoll(null);
            TradeSettings.overrideCaravanWait(-1);
            KingdomWorldApi.dissolveKingdom(server, seller.id());
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            GatheringGameTests.clear(level, from, to);
            clearSecond(helper);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- rights

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_rights")
    public static void theServerChecksTheRightsOfTheMarketAndOfDeals(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Guild Hall", king);
        Kingdom other = KingdomWorldApi.createKingdom(server, "GameTest Trading Partner", null) instanceof CreateKingdomResult.Created created
                ? created.kingdom() : null;
        UUID subject = UUID.randomUUID();
        UUID treasurer = UUID.randomUUID();
        UUID chancellor = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        for (UUID member : List.of(subject, treasurer, chancellor)) {
            join(server, kingdom, member);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().setRole(king, treasurer, Role.TREASURER);
        state.membership().setRole(king, chancellor, Role.CHANCELLOR);
        try {
            helper.assertTrue(other != null, "The partner was not founded");
            TradePackets.MarketChange sell = change(kingdom, TradePackets.MarketChange.Action.PUT_SELL, "minecraft:bread", 0, 0, 0);
            helper.assertTrue(!MarketActions.apply(server, subject, kingdom.id(), sell).success(), "A subject changed the market");
            helper.assertTrue(!MarketActions.apply(server, stranger, kingdom.id(), sell).success(), "A stranger changed the market");
            helper.assertTrue(!MarketActions.apply(server, chancellor, kingdom.id(), sell).success(), "A chancellor changed the market");
            helper.assertTrue(MarketActions.apply(server, treasurer, kingdom.id(), sell).success(), "The treasurer could not change the market");
            helper.assertTrue(MarketActions.view(server, kingdom.id(), stranger).isEmpty(), "A stranger sees the market");

            TradePackets.TradeChange offer = new TradePackets.TradeChange(kingdom.id(), TradePackets.TradeChange.Action.PROPOSE, other.id(), 0,
                    "minecraft:bread", 8, 40, true, true, 0);
            helper.assertTrue(!TradeActions.apply(server, subject, kingdom.id(), offer).success(), "A subject proposed a deal");
            helper.assertTrue(TradeActions.apply(server, chancellor, kingdom.id(), offer).success(), "The chancellor could not propose");
            helper.assertTrue(TradeActions.view(server, kingdom.id(), stranger).isEmpty(), "A stranger sees the deals");
            long dealId = state.trade().dealsOf(kingdom.id()).get(0).id();
            TradePackets.TradeChange cancel = new TradePackets.TradeChange(kingdom.id(), TradePackets.TradeChange.Action.CANCEL, null, dealId, "",
                    0, 0, false, false, 0);
            helper.assertTrue(!TradeActions.apply(server, subject, kingdom.id(), cancel).success(), "A subject called a deal off");
            state.membership().setRolePermission(king, Role.CHANCELLOR, Permission.MANAGE_TRADE, false);
            helper.assertTrue(!TradeActions.apply(server, chancellor, kingdom.id(), cancel).success(), "A revoked right still works");
            helper.assertTrue(TradeActions.apply(server, king, kingdom.id(), cancel).success(), "The king could not call the deal off");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            if (other != null) {
                KingdomWorldApi.dissolveKingdom(server, other.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_vault")
    public static void theKingTakesCoinsOfTheVaultIntoTheHands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Royal Vault", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer subject = player(helper, UUID.randomUUID(), "kw-page");
        join(server, kingdom, subject.getUUID());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.mint(kingdom.id(), Denomination.GOLD, 5, true, TreasuryActions.day(server));
            long before = economy.supply(kingdom.id());
            helper.assertTrue(!TreasuryActions.takeCoins(subject, kingdom.id(), "gold", 1).success(), "A subject took coins of the vault");
            helper.assertTrue(TreasuryActions.takeCoins(king, kingdom.id(), "gold", 3).success(), "The king could not take coins");
            helper.assertTrue(CoinStacks.count(king.getInventory(), kingdom.id()).equals(new Coins(0, 0, 3)), "The coins did not reach the hands");
            helper.assertTrue(treasury(server, kingdom).vault().gold() == 2, "The vault still holds them");
            helper.assertTrue(economy.supply(kingdom.id()) + 300 == before, "The books do not show the coins left them");
            helper.assertTrue(economy.coinsOut(kingdom.id()) == 300, "The coins in hands are not counted");
            long day = TreasuryActions.day(server);
            helper.assertTrue(treasury(server, kingdom).journal().totals(day, TreasuryJournal.Period.TODAY).get(LedgerCategory.TRANSFERS)
                    .expense() == 300, "The journal does not show the coins");
            helper.assertTrue(TreasuryActions.takeCoins(king, kingdom.id(), "gold", 9).success(), "The rest could not be taken");
            helper.assertTrue(treasury(server, kingdom).vault().gold() == 0, "Coins stayed in the vault");
            helper.assertTrue(!TreasuryActions.payByName(king, kingdom.id(), "kw-nobody-ever", 5).success(), "A stranger to the server was paid");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saved data

    @GameTest(template = EMPTY, batch = "kingdomworld_trade_saving")
    public static void tradeSurvivesSavingAndUpgrades(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Ledger Market", UUID.randomUUID());
        UUID seller = UUID.randomUUID();
        join(server, kingdom, seller);
        try {
            KingdomWorldState live = KingdomWorldApi.state(server);
            TradeRules rules = TradeSettings.rules();
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_SELL, "minecraft:bread", 7, 3, 0));
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_BUY, "minecraft:wheat", 0, 64, 90));
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.PUT_EXPORT, "minecraft:cobblestone", 0,
                    500, 0));
            MarketActions.apply(server, null, kingdom.id(), change(kingdom, TradePackets.MarketChange.Action.SET_OPEN, "", 0, 0, 0));
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(Enchantments.UNBREAKING, 2);
            live.listLot(seller, kingdom.id(), io.github.sekerator.kingdomworld.common.trade.GoodsStacks.goods(sword), 321,
                    KingdomWorldApi.gameTime(server), rules);
            CompoundTag saved = KingdomSavedData.get(server).save(new CompoundTag());

            KingdomSavedData loaded = KingdomSavedData.load(saved);
            helper.assertTrue(!loaded.isReadOnly(), "Saved trade became read-only: " + loaded.readOnlyReason());
            Market market = loaded.state().trade().existing(kingdom.id()).orElseThrow();
            helper.assertTrue(market.sells().equals(live.trade().market(kingdom.id(), rules).sells()), "The sells changed");
            helper.assertTrue(market.buys().size() == 1 && market.exports().size() == 1 && !market.openToOutsiders(), "The settings changed");
            Market.Lot lot = market.lots().get(0);
            List<ItemStack> back = io.github.sekerator.kingdomworld.common.trade.GoodsStacks.stacks(lot.goods());
            helper.assertTrue(back.size() == 1 && ItemStack.isSameItemSameTags(back.get(0), sword), "The lot lost its enchantment");
            helper.assertTrue(loaded.state().trade().peekNextId() > lot.id(), "Ids would repeat after loading");

            // Format 6: no trade yet; the new rights go to their positions.
            CompoundTag old = saved.copy();
            old.putInt("formatVersion", 6);
            old.remove("trade");
            CompoundTag kingdomTag = old.getList("kingdoms", 10).getCompound(0);
            ListTag treasurer = new ListTag();
            treasurer.add(StringTag.valueOf("manage_treasury"));
            kingdomTag.getCompound("permissions").put("treasurer", treasurer);
            KingdomSavedData upgraded = KingdomSavedData.load(old);
            helper.assertTrue(!upgraded.isReadOnly(), "Format 6 data became read-only: " + upgraded.readOnlyReason());
            var rights = upgraded.state().membership().kingdom(kingdom.id()).orElseThrow().permissions();
            helper.assertTrue(rights.has(Role.TREASURER, Permission.MANAGE_MARKET) && rights.has(Role.TREASURER, Permission.MANAGE_TRADE),
                    "The treasurer did not get the new rights");
            helper.assertTrue(upgraded.state().trade().existing(kingdom.id()).isEmpty(), "An upgraded kingdom came with a market");

            // Broken trade makes the data read-only instead of being dropped.
            CompoundTag broken = saved.copy();
            ListTag markets = broken.getCompound("trade").getList("markets", 10);
            for (int i = 0; i < markets.size(); i++) {
                ListTag lots = markets.getCompound(i).getList("lots", 10);
                if (!lots.isEmpty()) {
                    lots.getCompound(0).putLong("price", -3);
                    break;
                }
            }
            helper.assertTrue(KingdomSavedData.load(broken).isReadOnly(), "A broken lot did not make the data read-only");
            CompoundTag brokenEscrow = saved.copy();
            brokenEscrow.getCompound("economy").put("escrows", StringTag.valueOf("nonsense"));
            helper.assertTrue(KingdomSavedData.load(brokenEscrow).isReadOnly(), "Broken escrows did not make the data read-only");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }
}
