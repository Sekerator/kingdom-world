package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.command.EconomyCommands;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.BankActions;
import io.github.sekerator.kingdomworld.common.economy.CoinItem;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.MinterBehavior;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.data.TreasuryView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.Stations;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Game tests of money: the mint strikes coins from ingots of the storehouses (far from players and near them), a bank
 * hands coins in and out without making or losing any (also with a full inventory), players pay each other, wages are
 * paid and an empty treasury makes citizens unhappy, taxes are taken and debts grow, the server checks the right to
 * manage the treasury, the worth of items comes from recipes and tags, and treasuries and accounts survive saving.
 * Tests that found a kingdom run in batches of their own and clear the blocks they placed.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final String EMPTY = "empty";

    private EconomyGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static Treasury treasury(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow();
    }

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

    /**
     * A day of paydays far from the real day, so that the server's own payday never gets in the way.
     */
    private static long farDay(MinecraftServer server, Kingdom kingdom) {
        return Math.max(treasury(server, kingdom).lastPayday(), PostWork.gameDay(server)) + 1_000;
    }

    private static long coinWorth(Inventory inventory, UUID currency, CurrencyUnits units) {
        return CoinStacks.count(inventory, currency).value(units);
    }

    private static void steps(MinecraftServer server, Kingdom kingdom, int count) {
        GatheringSettings.override(GatheringGameTests.safeRules(0.0));
        try {
            for (int i = 0; i < count; i++) {
                CitizenSimulation.step(server, kingdom.id());
            }
        } finally {
            GatheringSettings.override(null);
        }
    }

    // ---------------------------------------------------------------- the mint

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_mint")
    public static void theMintStrikesCoinsFromIngotsOfTheStorehouses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mint Town", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false);
        store.insertItem(1, new ItemStack(Items.GOLD_INGOT, 2), false);
        store.insertItem(2, new ItemStack(Items.COPPER_INGOT, 4), false);
        helper.setBlock(new BlockPos(6, 0, 1), ModRegistries.MINT.get());
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "minter");
        WorkPost post = PostWork.post(server, postPos).orElseThrow();
        ZoneSurveys.surveyNow(server, post);
        Citizen minter = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, minter.id(), "minter"));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(minter, postPos), "The minter got no seat");
        // A keep order holds both gold ingots: the minter must leave them.
        KingdomWorldApi.state(server).order(null, kingdom.id(), "minecraft:gold_ingot", Order.Kind.KEEP, 2, Order.Priority.NORMAL,
                KingdomWorldApi.gameTime(server), 32, item -> true);
        try {
            helper.assertTrue(post.stations().getOrDefault(Stations.MINT, 0) == 1, "The mint was not found in the zone");
            EconomySettings.overrideMintTicks(20);
            steps(server, kingdom, 6);
            Treasury treasury = treasury(server, kingdom);
            CurrencyUnits units = treasury.units();
            long iron = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT));
            long gold = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.GOLD_INGOT));
            long copper = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.COPPER_INGOT));
            helper.assertTrue(gold == 2, "The minter took gold a keep order holds: " + gold + " left");
            helper.assertTrue(iron == 0 && copper == 0, "Metal was left: " + iron + " iron, " + copper + " copper");
            // Each iron ingot gives two silver coins, each copper ingot five copper coins.
            Coins expected = new Coins(4 * 5, 3 * 2, 0);
            helper.assertTrue(treasury.vault().equals(expected), "The vault holds " + treasury.vault() + ", expected " + expected);
            helper.assertTrue(treasury.balance() == 0, "Struck coins must go into the vault");
            helper.assertTrue(treasury.funds() == expected.value(units), "The treasury holds money that was not struck");
            helper.assertTrue(GatheringGameTests.produced(server, kingdom, "minter", "kingdomworld:coin") == 26, "The coins were not counted");
            helper.assertTrue(minter.experience() >= 7, "The minter gained no experience");
        } finally {
            EconomySettings.overrideMintTicks(0);
            CitizenGameTests.finish(helper, kingdom, minter);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_mint_choice")
    public static void plentyOfCopperDoesNotKeepTheMintFromIron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Copper Town", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.COPPER_INGOT, 64), false);
        store.insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false);
        helper.setBlock(new BlockPos(6, 0, 1), ModRegistries.MINT.get());
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "minter");
        WorkPost post = PostWork.post(server, postPos).orElseThrow();
        try {
            // Into the vault: copper first, then silver, then copper until the copper coins are worth more than the
            // silver ones, then silver again. Seven strikings use up both iron ingots out of 66.
            for (int i = 0; i < 7; i++) {
                helper.assertTrue(MinterBehavior.strike(server, post, false) == MinterBehavior.Struck.DONE, "Striking " + i + " failed");
            }
            long iron = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_INGOT));
            long copper = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.COPPER_INGOT));
            Treasury treasury = treasury(server, kingdom);
            helper.assertTrue(iron == 0, "The iron waited behind the copper: " + iron + " left, vault " + treasury.vault());
            helper.assertTrue(copper == 59, "Expected 59 copper ingots left, got " + copper);
            helper.assertTrue(treasury.vault().equals(new Coins(25, 4, 0)), "The vault holds " + treasury.vault());
        } finally {
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_mint_body", timeoutTicks = 1200)
    public static void aMinterNearPlayersWorksAtTheMint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Royal Mint", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.GOLD_INGOT, 2), false);
        helper.setBlock(new BlockPos(5, 0, 2), ModRegistries.MINT.get());
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "minter");
        ZoneSurveys.surveyNow(server, PostWork.post(server, postPos).orElseThrow());
        EconomySettings.overrideMintTicks(40);
        Citizen minter = GatheringGameTests.worker(helper, kingdom, "minter", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));
        helper.succeedWhen(() -> {
            Treasury treasury = treasury(server, kingdom);
            helper.assertTrue(treasury.vault().gold() == 2, "Gold coins struck: " + treasury.vault().gold());
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.GOLD_INGOT)) == 0, "Gold was left");
            EconomySettings.overrideMintTicks(0);
            GatheringGameTests.end(helper, kingdom, minter, from, to);
        });
    }

    // ---------------------------------------------------------------- the bank

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_bank")
    public static void theBankNeitherMakesNorLosesMoney(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Banking House", UUID.randomUUID());
        UUID currency = kingdom.id();
        CurrencyUnits units = treasury(server, kingdom).units();
        ServerPlayer player = player(helper, UUID.randomUUID(), "kw-banker");
        Inventory inventory = player.getInventory();
        try {
            inventory.add(CoinItem.stack(currency, Denomination.GOLD, 3));
            inventory.add(CoinItem.stack(currency, Denomination.COPPER, 40));
            inventory.add(CoinItem.stack(UUID.randomUUID(), Denomination.GOLD, 5));
            long before = coinWorth(inventory, currency, units);
            helper.assertTrue(BankActions.deposit(player, kingdom, null, 0).success(), "Handing in all coins failed");
            long account = KingdomWorldApi.balance(server, player.getUUID(), currency);
            helper.assertTrue(account == before && account == 340, "The account got " + account + " for coins worth " + before);
            helper.assertTrue(CoinStacks.count(inventory, currency).isEmpty(), "Coins of the currency stayed in the inventory");
            helper.assertTrue(inventory.countItem(ModRegistries.COIN.get()) == 5, "Coins of another currency were taken");
            helper.assertTrue(!BankActions.deposit(player, kingdom, Denomination.SILVER, 0).success(), "Nothing to hand in was accepted");

            // Taking money: as many coins of the chosen kind as the amount holds, the rest in smaller ones.
            helper.assertTrue(BankActions.withdraw(player, kingdom, Denomination.SILVER, 125).success(), "Taking money failed");
            Coins carried = CoinStacks.count(inventory, currency);
            helper.assertTrue(carried.equals(new Coins(5, 12, 0)), "Paid out " + carried);
            helper.assertTrue(KingdomWorldApi.balance(server, player.getUUID(), currency) == 215, "The account was not charged 125");
            helper.assertTrue(!BankActions.withdraw(player, kingdom, null, 216).success(), "More than the account holds was paid out");

            // A full inventory: only what fits is paid out, and the account is charged for exactly that.
            BankActions.deposit(player, kingdom, null, 0);
            inventory.clearContent();
            inventory.setItem(0, CoinItem.stack(currency, Denomination.COPPER, 60));
            for (int slot = 1; slot < 36; slot++) {
                inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            long total = KingdomWorldApi.balance(server, player.getUUID(), currency) + coinWorth(inventory, currency, units);
            helper.assertTrue(BankActions.withdraw(player, kingdom, Denomination.COPPER, 200).success(), "Nothing was paid into the last room");
            long after = KingdomWorldApi.balance(server, player.getUUID(), currency) + coinWorth(inventory, currency, units);
            helper.assertTrue(after == total, "Money changed with a full inventory: " + total + " before, " + after + " after");
            helper.assertTrue(CoinStacks.count(inventory, currency).copper() == 64, "The copper stack was not filled up to 64");
            helper.assertTrue(!BankActions.withdraw(player, kingdom, Denomination.COPPER, 10).success(), "Coins were paid out with no room");
            helper.assertTrue(KingdomWorldApi.balance(server, player.getUUID(), currency) + coinWorth(inventory, currency, units) == total,
                    "A refused payout changed money");
            inventory.setItem(35, ItemStack.EMPTY);
            helper.assertTrue(BankActions.withdraw(player, kingdom, null, 150).success(), "A free slot took nothing");
            long end = KingdomWorldApi.balance(server, player.getUUID(), currency) + coinWorth(inventory, currency, units);
            helper.assertTrue(end == total, "Money changed when one slot was free: " + total + " before, " + end + " after");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_pay")
    public static void playersPayEachOtherWithoutMakingMoney(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Trade Hall", UUID.randomUUID());
        ServerPlayer alice = player(helper, UUID.randomUUID(), "kw-alice");
        UUID bob = UUID.randomUUID();
        try {
            KingdomWorldApi.state(server).economy().adminAccount(alice.getUUID(), kingdom.id(), 500);
            helper.assertTrue(!EconomyCommands.transfer(alice, bob, "kw-bob", kingdom.id(), 120).success(),
                    "Money went to a player who never played here");
            KingdomWorldApi.state(server).economy().adminAccount(bob, kingdom.id(), 1);
            long supply = KingdomWorldApi.state(server).economy().supply(kingdom.id());
            helper.assertTrue(EconomyCommands.transfer(alice, bob, "kw-bob", kingdom.id(), 120).success(), "The payment failed");
            helper.assertTrue(KingdomWorldApi.balance(server, alice.getUUID(), kingdom.id()) == 380, "The payer was not charged");
            helper.assertTrue(KingdomWorldApi.balance(server, bob, kingdom.id()) == 121, "The payee got nothing");
            helper.assertTrue(!EconomyCommands.transfer(alice, bob, "kw-bob", kingdom.id(), 381).success(), "An overdraft was allowed");
            helper.assertTrue(!EconomyCommands.transfer(alice, alice.getUUID(), "kw-alice", kingdom.id(), 1).success(), "Paying oneself");
            helper.assertTrue(KingdomWorldApi.state(server).economy().supply(kingdom.id()) == supply, "Payments made or lost money");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- wages and taxes

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_wages")
    public static void wagesArePaidAndAnEmptyTreasuryMakesCitizensUnhappy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Paymasters", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.BREAD, 64), false);
        Citizen smith = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        Citizen farmer = CitizenGameTests.newcomer(helper, kingdom, 1, 2);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, smith.id(), "smith"));
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, farmer.id(), "farmer"));
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 16, 0);
            long day = farDay(server, kingdom);
            Economy.Payday paid = EconomyEvents.payday(server, kingdom, day);
            helper.assertTrue(paid.summary().paid() == 16 && paid.summary().underpaid() == 0, "Wages: " + paid.summary());
            helper.assertTrue(smith.payPercent() == 100 && farmer.payPercent() == 100, "Paid citizens do not know it");
            helper.assertTrue(treasury(server, kingdom).funds() == 0, "The wages were not taken from the treasury");

            Economy.Payday unpaid = EconomyEvents.payday(server, kingdom, day + 1);
            helper.assertTrue(unpaid.summary().paid() == 0 && unpaid.summary().underpaid() == 2, "Wages: " + unpaid.summary());
            helper.assertTrue(smith.payPercent() == 0, "An unpaid citizen thinks it was paid");
            TreasuryView view = TreasuryActions.view(server, kingdom.id(), kingdom.rulerId()).orElseThrow();
            helper.assertTrue(view.lastPayroll().underpaid() == 2 && view.lastPayroll().percent() == 0, "The throne screen does not show it");

            smith.setMood(CitizenNeeds.NEUTRAL_MOOD);
            steps(server, kingdom, 1);
            int target = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(smith.food(), smith.home() != null,
                    smith.state() == CitizenState.WOUNDED, 0, smith.payPercent()));
            int content = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(smith.food(), smith.home() != null,
                    smith.state() == CitizenState.WOUNDED, 0, 100));
            helper.assertTrue(target == content + Payroll.UNPAID_MOOD, "No pay did not lower the mood the citizen tends to");
            helper.assertTrue(smith.mood() == CitizenNeeds.nextMood(CitizenNeeds.NEUTRAL_MOOD, target),
                    "The mood went to " + smith.mood() + " instead of towards " + target);
        } finally {
            CitizenGameTests.finish(helper, kingdom, smith, farmer);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_taxes")
    public static void taxesAreTakenAndDebtsGrow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Tax Office", UUID.randomUUID());
        UUID rich = UUID.randomUUID();
        UUID poor = UUID.randomUUID();
        join(server, kingdom, rich);
        join(server, kingdom, poor);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(rich, kingdom.id(), 100);
            economy.adminAccount(kingdom.rulerId(), kingdom.id(), 100);
            long tax = treasury(server, kingdom).pollTax();
            long day = farDay(server, kingdom);
            EconomyEvents.payday(server, kingdom, day);
            EconomyEvents.payday(server, kingdom, day + 1);
            helper.assertTrue(KingdomWorldApi.balance(server, rich, kingdom.id()) == 100 - 2 * tax, "The tax was not taken");
            helper.assertTrue(KingdomWorldApi.balance(server, kingdom.rulerId(), kingdom.id()) == 100, "The king paid a tax");
            helper.assertTrue(treasury(server, kingdom).debt(poor) == 2 * tax, "The debt did not grow: " + treasury(server, kingdom).debt(poor));
            TreasuryView view = TreasuryActions.view(server, kingdom.id(), kingdom.rulerId()).orElseThrow();
            helper.assertTrue(view.debtorCount() == 1 && view.debtTotal() == 2 * tax, "The debtors are not shown");
            economy.adminAccount(poor, kingdom.id(), 3 * tax);
            EconomyEvents.payday(server, kingdom, day + 2);
            helper.assertTrue(treasury(server, kingdom).debt(poor) == 0 && KingdomWorldApi.balance(server, poor, kingdom.id()) == 0,
                    "The debt was not collected with the tax");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_rights")
    public static void theServerChecksTheRightToManageTheTreasury(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Exchequer", kingId);
        ServerPlayer subject = player(helper, UUID.randomUUID(), "kw-subject");
        ServerPlayer treasurer = player(helper, UUID.randomUUID(), "kw-treasurer");
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        join(server, kingdom, subject.getUUID());
        join(server, kingdom, treasurer.getUUID());
        KingdomWorldApi.state(server).membership().setRole(kingId, treasurer.getUUID(), Role.TREASURER);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            long tax = treasury(server, kingdom).pollTax();
            TreasuryActions.handle(subject, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.POLL_TAX, "", 500));
            TreasuryActions.handle(stranger, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.POLL_TAX, "", 500));
            TreasuryActions.handle(subject, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.WAGE, "smith", 50));
            helper.assertTrue(treasury(server, kingdom).pollTax() == tax, "A subject or a stranger changed the tax");
            helper.assertTrue(treasury(server, kingdom).wages().isEmpty(), "A subject changed a wage");
            helper.assertTrue(!TreasuryActions.pay(subject, subject.getUUID(), "kw-subject", 100).success(), "A subject paid itself");
            helper.assertTrue(treasury(server, kingdom).funds() == 1_000, "The treasury paid without the right");

            TreasuryActions.handle(treasurer, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.POLL_TAX, "", 25));
            helper.assertTrue(treasury(server, kingdom).pollTax() == 25, "The treasurer could not set the tax");
            helper.assertTrue(TreasuryActions.pay(treasurer, subject.getUUID(), "kw-subject", 100).success(), "The treasurer could not pay");
            helper.assertTrue(KingdomWorldApi.balance(server, subject.getUUID(), kingdom.id()) == 100, "The payment did not arrive");
            TreasuryActions.handle(treasurer, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.RENAME, "",
                    0));
            TreasuryActions.handle(treasurer, new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.RENAME,
                    "Gold Crown", 0));
            helper.assertTrue(treasury(server, kingdom).currencyName().isEmpty(), "Only the king renames the currency");

            KingdomWorldApi.state(server).membership().setRolePermission(kingId, Role.TREASURER, Permission.MANAGE_TREASURY, false);
            helper.assertTrue(!TreasuryActions.pay(treasurer, subject.getUUID(), "kw-subject", 100).success(), "A revoked right still pays");
            helper.assertTrue(TreasuryActions.view(server, kingdom.id(), stranger.getUUID()).isEmpty(), "A stranger sees the treasury");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the worth of items

    @GameTest(template = EMPTY)
    public static void theWorthOfItemsComesFromTagsAndRecipes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemPrices.invalidate();
        ItemPrices.Report report = ItemPrices.report(server);
        helper.assertTrue(report.errors() == 0, "Recipes could not be read: " + report.errors());
        helper.assertTrue(report.items() > 1_000 && report.fromRecipes() > 300, "Too few values: " + report);
        ItemValues.Value iron = ItemPrices.value(server, new ItemStack(Items.IRON_INGOT));
        ItemValues.Value sword = ItemPrices.value(server, new ItemStack(Items.IRON_SWORD));
        ItemValues.Value block = ItemPrices.value(server, new ItemStack(Items.IRON_BLOCK));
        ItemValues.Value oak = ItemPrices.value(server, new ItemStack(Items.OAK_LOG));
        ItemValues.Value planks = ItemPrices.value(server, new ItemStack(Items.OAK_PLANKS));
        helper.assertTrue(iron.source() == ItemValues.Source.BASE && iron.units() == 20, "Iron: " + iron);
        helper.assertTrue(oak.source() == ItemValues.Source.BASE, "Logs come from the tag: " + oak);
        helper.assertTrue(sword.source() == ItemValues.Source.RECIPE && sword.amount() > 2 * iron.amount(), "Sword: " + sword);
        helper.assertTrue(block.amount() > 9 * iron.amount() && block.amount() < 11 * iron.amount(), "Block: " + block);
        helper.assertTrue(planks.amount() < oak.amount(), "Four planks from a log: " + planks);
        helper.assertTrue(ItemPrices.value(server, new ItemStack(Items.DRAGON_EGG)).source() == ItemValues.Source.DEFAULT, "Nothing makes eggs");
        helper.assertTrue(KingdomWorldApi.itemValue(server, new ItemStack(Items.DIAMOND)) == 400, "Diamonds from the tag");
        helper.succeed();
    }

    // ---------------------------------------------------------------- saved data

    /**
     * The saved tag of a kingdom. The data holds every kingdom of the world, also those other tests left behind when they
     * failed, so the first one is not always the test's own.
     */
    private static CompoundTag savedKingdom(GameTestHelper helper, CompoundTag saved, Kingdom kingdom) {
        ListTag kingdoms = saved.getList("kingdoms", Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag tag = kingdoms.getCompound(i);
            if (tag.hasUUID("id") && tag.getUUID("id").equals(kingdom.id())) {
                return tag;
            }
        }
        helper.fail("The kingdom is not in the saved data");
        return new CompoundTag();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_economy_saving")
    public static void treasuriesAndAccountsSurviveSavingAndUpgrades(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Counting House", UUID.randomUUID());
        UUID saver = UUID.randomUUID();
        join(server, kingdom, saver);
        try {
            KingdomWorldState live = KingdomWorldApi.state(server);
            live.economy().adminAccount(saver, kingdom.id(), 777);
            live.economy().mint(kingdom.id(), Denomination.GOLD, 3, true, 5);
            live.economy().adminTreasury(kingdom.id(), 42, 5);
            live.economy().renameCurrency(kingdom.id(), "Saved Crown");
            live.economy().setWage(kingdom.id(), "smith", 33, 200, EconomySettings.rules());
            CompoundTag saved = KingdomSavedData.get(server).save(new CompoundTag());

            KingdomSavedData loaded = KingdomSavedData.load(saved);
            helper.assertTrue(!loaded.isReadOnly(), "Saved data became read-only: " + loaded.readOnlyReason());
            Treasury treasury = loaded.state().economy().treasury(kingdom.id()).orElseThrow();
            helper.assertTrue(treasury.balance() == 42 && treasury.vault().gold() == 3, "The treasury changed: " + treasury.funds());
            helper.assertTrue(treasury.currencyName().equals("Saved Crown") && treasury.wage("smith", EconomySettings.rules()) == 33,
                    "The settings of the treasury were lost");
            helper.assertTrue(loaded.state().economy().accounts().balance(saver, kingdom.id()) == 777, "The account changed");
            helper.assertTrue(loaded.state().economy().supply(kingdom.id()) == live.economy().supply(kingdom.id()), "Money changed in saving");
            helper.assertTrue(!treasury.journal().days().isEmpty(), "The journal was lost");

            // Format 5: no money yet; the treasurer gets the new right.
            CompoundTag old = saved.copy();
            old.putInt("formatVersion", 5);
            old.remove("economy");
            ListTag oldKingdoms = old.getList("kingdoms", Tag.TAG_COMPOUND);
            for (int i = 0; i < oldKingdoms.size(); i++) {
                oldKingdoms.getCompound(i).remove("treasury");
            }
            CompoundTag kingdomTag = savedKingdom(helper, old, kingdom);
            ListTag treasurerRights = new ListTag();
            treasurerRights.add(StringTag.valueOf("build"));
            kingdomTag.getCompound("permissions").put("treasurer", treasurerRights);
            KingdomSavedData upgraded = KingdomSavedData.load(old);
            helper.assertTrue(!upgraded.isReadOnly(), "Format 5 data became read-only: " + upgraded.readOnlyReason());
            helper.assertTrue(upgraded.isDirty(), "Upgraded data is not marked for saving");
            Treasury fresh = upgraded.state().economy().treasury(kingdom.id()).orElseThrow();
            helper.assertTrue(fresh.funds() == 0 && fresh.lastPayday() == Treasury.NO_PAYDAY, "An upgraded kingdom came with money");
            helper.assertTrue(upgraded.state().membership().kingdom(kingdom.id()).orElseThrow().permissions()
                    .has(Role.TREASURER, Permission.MANAGE_TREASURY), "The treasurer did not get the right to manage the treasury");

            // Broken money makes the data read-only instead of being dropped.
            CompoundTag broken = saved.copy();
            savedKingdom(helper, broken, kingdom).getCompound("treasury").putLong("balance", -5);
            helper.assertTrue(KingdomSavedData.load(broken).isReadOnly(), "A negative balance did not make the data read-only");
            CompoundTag brokenAccounts = saved.copy();
            brokenAccounts.getCompound("economy").put("accounts", StringTag.valueOf("nonsense"));
            helper.assertTrue(KingdomSavedData.load(brokenAccounts).isReadOnly(), "Unreadable accounts did not make the data read-only");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }
}
