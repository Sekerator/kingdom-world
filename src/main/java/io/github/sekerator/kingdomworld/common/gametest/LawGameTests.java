package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.companion.CompanionBodies;
import io.github.sekerator.kingdomworld.common.companion.CompanionSettings;
import io.github.sekerator.kingdomworld.common.companion.CompanionWork;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.law.LawActions;
import io.github.sekerator.kingdomworld.common.law.LawEvents;
import io.github.sekerator.kingdomworld.common.law.LawPunish;
import io.github.sekerator.kingdomworld.common.law.LawSavedData;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of the law: a theft from a storehouse seen by a citizen makes the thief wanted, an unseen one does not; a
 * witness who dies before reaching the guards reports nothing; the king takes from the storehouse freely; a guard beats a
 * thief down to a fine (a poor thief ends with a debt); an executed murderer loses the goods into the record and the coins
 * into the treasury, and the ransom of half their worth gives back exactly those goods; guards leave players away from the
 * keyboard alone; a pardon clears the record; a member of the guild collects the head of a wanted murderer; the ban of
 * slavery frees the slave of a foreign owner and fines the owner; the law survives saving. Every test runs in a batch of its
 * own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LawGameTests {
    private static final String EMPTY = "empty";

    private LawGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, String name, int x, int z) {
        ServerPlayer player = LawStandIn.create(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /**
     * Reports are delivered only when a test asks, and nobody runs for long.
     */
    private static void slowReports() {
        LawRules.Builder rules = LawSettings.configured();
        rules.reportMinTicks = 1_000_000;
        rules.reportMaxTicks = 1_000_000;
        LawSettings.override(rules.build());
    }

    private static Citizen witness(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, z);
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = null;
        for (int i = 0; i < 5 && body == null; i++) {
            // A body may wait for its turn among the bodies of other tests.
            CitizenBodies.update(helper.getLevel().getServer());
            body = CitizenBodies.body(citizen.id());
        }
        helper.assertTrue(body != null, "The witness has no body");
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        body.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        body.setNoAi(true);
        return citizen;
    }

    private static Citizen soldier(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, z);
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), "warrior").ok(), "The citizen was not recruited");
        CitizenBodies.force(citizen.id(), true);
        CitizenBodies.update(server);
        helper.assertTrue(CitizenBodies.body(citizen.id()) != null, "The soldier has no body");
        return citizen;
    }

    /**
     * A blow from an entity that no invulnerability stops, as a guard's or a hunter's last blow.
     */
    private static DamageSource blow(ServerLevel level, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL), attacker);
    }

    private static void fund(MinecraftServer server, Kingdom kingdom, long amount) {
        KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), amount, TreasuryActions.day(server));
    }

    /**
     * Coins of the kingdom the treasury hands out, so that money stays where it is counted.
     */
    private static void coins(MinecraftServer server, ServerPlayer player, Kingdom kingdom, long amount) {
        Economy economy = KingdomWorldApi.state(server).economy();
        if (!economy.kingdomPays(kingdom.id(), amount, null, LedgerCategory.ADMIN, TreasuryActions.day(server)).ok()) {
            throw new IllegalStateException("The treasury could not hand out coins");
        }
        Purses.payOut(player, kingdom.id(), amount);
    }

    private static long coinsOf(MinecraftServer server, ServerPlayer player, Kingdom kingdom) {
        return CoinStacks.count(player.getInventory(), kingdom.id()).value(Purses.units(server, kingdom.id()));
    }

    /**
     * All money of the kingdom's currency: on the books and in coins in the hands of players.
     */
    private static long money(MinecraftServer server, Kingdom kingdom) {
        Economy economy = KingdomWorldApi.state(server).economy();
        return economy.supply(kingdom.id()) + economy.coinsOut(kingdom.id());
    }

    private static long funds(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().funds();
    }

    private static long debt(MinecraftServer server, Kingdom kingdom, ServerPlayer player) {
        return KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().debt(player.getUUID());
    }

    private static int count(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            total += player.getInventory().getItem(i).getCount();
        }
        return total;
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        LawSettings.override(null);
        LawWork.deliverNow(server);
        LawState state = LawWork.state(server);
        for (Wanted wanted : List.copyOf(state.wantedIn(kingdom.id()))) {
            state.clear(kingdom.id(), wanted.player());
        }
        for (Confiscation confiscation : List.copyOf(state.confiscationsIn(kingdom.id()))) {
            state.release(confiscation.id());
        }
        state.forgetKingdom(kingdom.id());
        ArmyWork.state(server).forgetKingdom(kingdom.id());
        GuildWork.state(server).forgetKingdom(kingdom.id(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), helper.absolutePos(new BlockPos(-1, 0, -1)), helper.absolutePos(new BlockPos(8, 3, 8)));
    }

    // ---------------------------------------------------------------- thefts and witnesses

    @GameTest(template = EMPTY, batch = "kingdomworld_law_theft")
    public static void aTheftSeenByACitizenMakesTheThiefWantedAnUnseenOneDoesNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Theft", UUID.randomUUID());
        slowReports();
        Citizen witness = witness(helper, kingdom, 4, 2);
        ServerPlayer thief = player(helper, "kw-thief", 2, 3);
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 1, 1, 1);
            storehouse.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.assertTrue(storehouse.getSlots() == 54, "The storehouse is no large chest: " + storehouse.getSlots());
            ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x6, 1, thief.getInventory(), new RecipeWrapper(storehouse), 6);
            LawEvents.watchForCheck(thief, pos, menu);
            menu.getSlot(0).remove(4);
            LawEvents.closedForCheck(thief, menu);
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).isEmpty(), "The thief was wanted before the report");
            helper.assertTrue(LawWork.pendingReports() > 0, "The witness did not run to the guards");
            LawWork.deliverNow(server);
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "The thief is not wanted"));
            long worth = LawPunish.worth(server, kingdom.id(), new ItemStack(Items.IRON_INGOT, 4));
            helper.assertTrue(wanted.crimes().size() == 1 && wanted.crimes().get(0).kind() == CrimeKind.THEFT && wanted.stolen() == worth,
                    "The record is wrong: " + wanted.crimes() + ", expected worth " + worth);
            // Nobody sees the next theft.
            CitizenBodies.force(witness.id(), false);
            CitizenBodies.update(server);
            ServerPlayer unseen = player(helper, "kw-unseen", 2, 3);
            ChestMenu second = new ChestMenu(MenuType.GENERIC_9x6, 2, unseen.getInventory(), new RecipeWrapper(storehouse), 6);
            LawEvents.watchForCheck(unseen, pos, second);
            second.getSlot(0).remove(2);
            LawEvents.closedForCheck(unseen, second);
            LawWork.deliverNow(server);
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), unseen.getUUID()).isEmpty(), "An unseen thief is wanted");
        } finally {
            end(helper, kingdom, witness);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_witness_dies")
    public static void aWitnessWhoDiesBeforeReachingTheGuardsReportsNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Witness", UUID.randomUUID());
        slowReports();
        Citizen witness = witness(helper, kingdom, 4, 2);
        ServerPlayer thief = player(helper, "kw-thief", 2, 3);
        try {
            helper.assertTrue(LawWork.deed(thief, thief.blockPosition(), kingdom.id(), CrimeKind.THEFT, 40, "", Permission.CONTAINERS, false, null),
                    "The witness did not see the theft");
            CitizenLife.die(server, witness.id(), CauseOfDeath.MONSTER, "", CitizenGameTests.at(helper, 4, 1, 2));
            LawWork.deliverNow(server);
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).isEmpty(), "A dead witness reported the theft");
        } finally {
            end(helper, kingdom, witness);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_king")
    public static void theKingTakesFromTheStorehouseWithoutCrime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law King", kingId);
        slowReports();
        Citizen witness = witness(helper, kingdom, 4, 2);
        ServerPlayer king = LawStandIn.create(server, helper.getLevel(), new GameProfile(kingId, "kw-king"));
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 3));
        king.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ServerPlayer subject = player(helper, "kw-subject", 2, 4);
        try {
            helper.assertTrue(!LawWork.deed(king, king.blockPosition(), kingdom.id(), CrimeKind.THEFT, 40, "", Permission.CONTAINERS, false, null),
                    "The king committed a theft");
            var membership = KingdomWorldApi.state(server).membership();
            membership.invite(kingId, subject.getUUID(), 1L);
            membership.acceptInvitation(subject.getUUID(), kingdom.id(), 1L);
            // A subject without the right to containers answers for it, one with the right does not.
            helper.assertTrue(LawWork.isCrime(server, subject, kingdom.id(), CrimeKind.THEFT, Permission.CONTAINERS, false),
                    "A subject without the right may take");
            membership.setRole(kingId, subject.getUUID(), Role.STEWARD);
            helper.assertTrue(!LawWork.isCrime(server, subject, kingdom.id(), CrimeKind.THEFT, Permission.CONTAINERS, false),
                    "An official committed a theft");
            LawWork.deliverNow(server);
            helper.assertTrue(LawWork.state(server).wantedIn(kingdom.id()).isEmpty(), "Somebody is wanted");
        } finally {
            end(helper, kingdom, witness);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- guards

    @GameTest(template = EMPTY, batch = "kingdomworld_law_beating")
    public static void aGuardBeatsAThiefDownToAFineAndAPoorThiefOwesTheRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Beating", UUID.randomUUID());
        Citizen guard = soldier(helper, kingdom, 5, 5);
        ServerPlayer thief = player(helper, "kw-thief", 2, 3);
        ServerPlayer poor = player(helper, "kw-poor", 3, 3);
        try {
            fund(server, kingdom, 10_000);
            coins(server, thief, kingdom, 500);
            long supply = money(server, kingdom);
            LawWork.commit(server, kingdom.id(), thief.getUUID(), "kw-thief", CrimeKind.THEFT, 40, "");
            LawWork.commit(server, kingdom.id(), poor.getUUID(), "kw-poor", CrimeKind.THEFT, 40, "");
            long fine = LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).orElseThrow().fine(LawSettings.rules(), 1000);
            helper.assertTrue(fine == 80, "The fine of a theft of 40 is " + fine);
            long treasury = funds(server, kingdom);
            CitizenEntity body = CitizenBodies.body(guard.id());
            // The guard called both out a while ago and they did not give up.
            LawWork.hailForCheck(server, thief, kingdom.id());
            LawWork.hailForCheck(server, poor, kingdom.id());
            thief.hurt(blow(level, body), 1_000.0f);
            helper.assertTrue(thief.isAlive() && thief.getHealth() > 0, "The guard killed a thief");
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).isEmpty(), "The beaten thief is still wanted");
            helper.assertTrue(coinsOf(server, thief, kingdom) == 500 - fine, "The fine did not come from the coins: " + coinsOf(server, thief, kingdom));
            helper.assertTrue(funds(server, kingdom) == treasury + fine, "The fine did not reach the treasury");
            poor.hurt(blow(level, body), 1_000.0f);
            helper.assertTrue(poor.isAlive(), "The guard killed a poor thief");
            helper.assertTrue(debt(server, kingdom, poor) == fine, "The poor thief owes " + debt(server, kingdom, poor));
            helper.assertTrue(money(server, kingdom) == supply, "Fines made or lost money");
        } finally {
            end(helper, kingdom, guard);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_execution")
    public static void anExecutedMurdererLosesGoodsAndCoinsAndBuysBackExactlyThoseGoods(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Execution", UUID.randomUUID());
        Citizen guard = soldier(helper, kingdom, 5, 5);
        ServerPlayer murderer = player(helper, "kw-murderer", 2, 3);
        try {
            fund(server, kingdom, 10_000);
            coins(server, murderer, kingdom, 300);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setHoverName(net.minecraft.network.chat.Component.literal("Oathkeeper"));
            murderer.getInventory().add(sword.copy());
            murderer.getInventory().add(new ItemStack(Items.BREAD, 16));
            murderer.getInventory().armor.set(2, new ItemStack(Items.IRON_CHESTPLATE));
            Economy economy = KingdomWorldApi.state(server).economy();
            long supply = money(server, kingdom);
            long treasury = funds(server, kingdom);
            LawWork.commit(server, kingdom.id(), murderer.getUUID(), "kw-murderer", CrimeKind.MURDER, 0, "");
            // The last blow of the guard: the death runs through the game as any death does.
            murderer.setHealth(0.0f);
            murderer.die(blow(level, CitizenBodies.body(guard.id())));
            helper.assertTrue(count(murderer) == 0, "Goods stayed with the body: " + count(murderer));
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), murderer.getUUID()).isEmpty(), "The executed murderer is still wanted");
            helper.assertTrue(funds(server, kingdom) == treasury + 300, "The coins did not reach the treasury");
            helper.assertTrue(money(server, kingdom) == supply, "The execution made or lost money");
            List<Confiscation> taken = LawWork.state(server).confiscationsOf(murderer.getUUID());
            helper.assertTrue(taken.size() == 1 && taken.get(0).items() == 18, "The record of the goods is wrong: " + taken);
            Confiscation goods = taken.get(0);
            long price = LawSettings.rules().ransom(goods.worth());
            // The murderer comes back to the throne and buys the goods back from the account.
            ServerPlayer back = LawStandIn.create(server, level, murderer.getGameProfile());
            BlockPos throne = helper.absolutePos(new BlockPos(2, 1, 3));
            back.setPos(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5);
            economy.adminAccount(back.getUUID(), kingdom.id(), price);
            helper.assertTrue(LawPunish.ransom(back, goods.id(), true).ok(), "The ransom failed");
            helper.assertTrue(count(back) == 18, "The ransom gave back " + count(back) + " items");
            boolean named = false;
            for (int i = 0; i < back.getInventory().getContainerSize(); i++) {
                named |= ItemStack.isSameItemSameTags(back.getInventory().getItem(i), sword);
            }
            helper.assertTrue(named, "The named sword did not come back as it was");
            helper.assertTrue(economy.accounts().balance(back.getUUID(), kingdom.id()) == 0, "The ransom was not half the worth");
            helper.assertTrue(LawWork.state(server).confiscation(goods.id()).isEmpty() && LawSavedData.get(server).goods(goods.id()).isEmpty(),
                    "The goods are still in the record");
            helper.assertTrue(!LawPunish.ransom(back, goods.id(), true).ok(), "The goods were bought back twice");
        } finally {
            end(helper, kingdom, guard);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_afk")
    public static void guardsLeaveAPlayerAwayFromTheKeyboardAlone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Away", UUID.randomUUID());
        ServerPlayer wanted = player(helper, "kw-away", 2, 3);
        ServerPlayer hunter = player(helper, "kw-hunter", 3, 3);
        try {
            fund(server, kingdom, 10_000);
            LawWork.commit(server, kingdom.id(), wanted.getUUID(), "kw-away", CrimeKind.MURDER, 0, "");
            helper.assertTrue(LawWork.catchable(server, wanted), "A player at the keyboard cannot be stopped");
            ArmyWork.markAway(wanted.getUUID(), true);
            helper.assertTrue(!LawWork.catchable(server, wanted), "Guards may stop a player away from the keyboard");
            helper.assertTrue(!ArmyWork.mayAttack(server, kingdom.id(), wanted), "Soldiers may attack a player away from the keyboard");
            GuildWork.state(server).setRank(hunter.getUUID(), Rank.F, KingdomWorldApi.gameTime(server));
            helper.assertTrue(LawWork.bountyFor(server, kingdom.id(), wanted.getUUID()).isPresent(), "No contract for the head");
            wanted.setHealth(0.0f);
            wanted.die(blow(helper.getLevel(), hunter));
            // Killing a player away from the keyboard is no lawful execution: the record and the contract stay.
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), wanted.getUUID()).isPresent(), "The record of an away player was cleared");
            helper.assertTrue(LawWork.bountyFor(server, kingdom.id(), wanted.getUUID()).isPresent(), "The contract was paid for an away player");
        } finally {
            ArmyWork.markAway(wanted.getUUID(), false);
            end(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the court and the guild

    @GameTest(template = EMPTY, batch = "kingdomworld_law_pardon")
    public static void aPardonOfTheCourtClearsTheRecord(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Pardon", kingId);
        ServerPlayer king = LawStandIn.create(server, helper.getLevel(), new GameProfile(kingId, "kw-king"));
        ServerPlayer thief = player(helper, "kw-thief", 2, 3);
        try {
            LawWork.commit(server, kingdom.id(), thief.getUUID(), "kw-thief", CrimeKind.THEFT, 40, "");
            helper.assertTrue(!LawActions.applyTab(thief, new LawPackets.TabChange(kingdom.id(), LawPackets.TabChange.Action.PARDON, thief.getUUID(), 0))
                    .success(), "The thief pardoned himself");
            helper.assertTrue(LawActions.applyTab(king, new LawPackets.TabChange(kingdom.id(), LawPackets.TabChange.Action.PARDON, thief.getUUID(), 0))
                    .success(), "The king could not pardon");
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).isEmpty(), "The pardoned thief is still wanted");
        } finally {
            end(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_head")
    public static void aMemberOfTheGuildCollectsTheHeadOfAWantedMurderer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Head", UUID.randomUUID());
        ServerPlayer outlaw = player(helper, "kw-outlaw", 2, 3);
        ServerPlayer hunter = player(helper, "kw-hunter", 3, 3);
        try {
            fund(server, kingdom, 10_000);
            outlaw.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
            LawWork.commit(server, kingdom.id(), outlaw.getUUID(), "kw-outlaw", CrimeKind.MURDER, 0, "");
            Optional<Contract> contract = LawWork.bountyFor(server, kingdom.id(), outlaw.getUUID());
            helper.assertTrue(contract.isPresent() && contract.get().kind() == Contract.Kind.OUTLAW
                    && contract.get().reward() == LawSettings.rules().bountyReward, "No contract for the head: " + contract);
            GuildWork.state(server).setRank(hunter.getUUID(), Rank.F, KingdomWorldApi.gameTime(server));
            long supply = money(server, kingdom);
            outlaw.setHealth(0.0f);
            outlaw.die(blow(level, hunter));
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), outlaw.getUUID()).isEmpty(), "The outlaw is still wanted");
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), hunter.getUUID()).isEmpty(), "The hunter became a murderer");
            helper.assertTrue(GuildWork.state(server).contract(contract.get().id()).isEmpty(), "The contract is still on the board");
            helper.assertTrue(KingdomWorldApi.state(server).economy().accounts().balance(hunter.getUUID(), kingdom.id()) == contract.get().payout(),
                    "The hunter was not paid");
            helper.assertTrue(LawWork.state(server).confiscationsOf(outlaw.getUUID()).size() == 1, "The goods of the outlaw were not confiscated");
            helper.assertTrue(money(server, kingdom) == supply, "The head made or lost money");
        } finally {
            end(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- slavery

    @GameTest(template = EMPTY, batch = "kingdomworld_law_slavery")
    public static void theBanOfSlaveryFreesTheSlaveOfAForeignOwnerAndFinesTheOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Slavery", UUID.randomUUID());
        ServerPlayer owner = player(helper, "kw-owner", 2, 3);
        Companion slave = CompanionWork.state(server).give(owner.getUUID(), UUID.randomUUID(), 77, Gender.MALE, 0, 2, new Skills(4, 4, 4),
                kingdom.id(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server), CompanionSettings.rules());
        try {
            helper.assertTrue(slave.isSlave(), "The companion is no slave");
            CompanionBodies.spawnAt(level, slave, helper.absolutePos(new BlockPos(3, 1, 3)));
            helper.assertTrue(LawWork.enforceSlaveryNow(server) == 0, "A slave went free where slavery is allowed");
            helper.assertTrue(LawWork.state(server).setSlaveryBanned(null, kingdom.id(), true, KingdomWorldApi.state(server)).ok(), "No ban");
            helper.assertTrue(!CompanionWork.state(server).law().allowsSlavery(kingdom.id()), "The companions do not know the ban");
            helper.assertTrue(LawWork.enforceSlaveryNow(server) == 1, "The slave was not set free");
            helper.assertTrue(!CompanionWork.state(server).companion(slave.id()).orElseThrow().isSlave(), "The companion is still a slave");
            helper.assertTrue(debt(server, kingdom, owner) == LawSettings.rules().slaveryFine, "The owner owes " + debt(server, kingdom, owner));
        } finally {
            CompanionBodies.despawnAll(server, owner.getUUID());
            CompanionWork.state(server).remove(slave.id());
            end(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- gates of cities

    private static io.github.sekerator.kingdomworld.core.law.CityGates.City burg(Kingdom kingdom) {
        return new io.github.sekerator.kingdomworld.core.law.CityGates.City(UUID.randomUUID(), kingdom.id(), "GameTest Burg", true);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_gate_pass")
    public static void aLargeCityTakesItsFeeAtTheGateAndThePassHoldsADay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Gate", kingId);
        ServerPlayer stranger = player(helper, "kw-traveller", 2, 6);
        ServerPlayer subject = player(helper, "kw-burgher", 2, 5);
        try {
            var world = KingdomWorldApi.state(server);
            world.membership().invite(kingId, subject.getUUID(), 1L);
            world.membership().acceptInvitation(subject.getUUID(), kingdom.id(), 1L);
            helper.assertTrue(LawWork.state(server).setEntryFee(null, kingdom.id(), 50, LawSettings.rules(), world).ok(), "No entry fee");
            world.economy().adminAccount(stranger.getUUID(), kingdom.id(), 200);
            LawWork.state(server).setAutoPay(stranger.getUUID(), true);
            long treasury = funds(server, kingdom);
            List<io.github.sekerator.kingdomworld.core.citizen.WorldPos> gates = List.of(CitizenGameTests.at(helper, 2, 1, 6));
            var center = CitizenGameTests.at(helper, 6, 1, 6);
            var city = burg(kingdom);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawCities.enter(stranger, city, true, gates, center),
                    "A traveller who pays by himself was kept out");
            helper.assertTrue(world.economy().accounts().balance(stranger.getUUID(), kingdom.id()) == 150, "The fee was not taken");
            helper.assertTrue(funds(server, kingdom) == treasury + 50, "The fee did not reach the treasury");
            helper.assertTrue(LawWork.state(server).hasPass(kingdom.id(), stranger.getUUID(), LawWork.now(server)), "No pass after paying");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawCities.enter(stranger, burg(kingdom), true, gates, center),
                    "A traveller with a pass was kept out of another city of the kingdom");
            helper.assertTrue(world.economy().accounts().balance(stranger.getUUID(), kingdom.id()) == 150, "A pass was paid twice");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawCities.enter(subject, city, true, gates, center),
                    "A subject was kept out");
            helper.assertTrue(world.economy().accounts().balance(subject.getUUID(), kingdom.id()) == 0, "A subject paid");
            // A village asks nothing.
            ServerPlayer walker = player(helper, "kw-walker", 3, 6);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawCities.enter(walker, new io.github.sekerator.kingdomworld.core.law
                    .CityGates.City(UUID.randomUUID(), kingdom.id(), "Hamlet", false), true, gates, center), "A village kept a walker out");
        } finally {
            end(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_gate_refused")
    public static void aTravellerWhoDoesNotPayIsPutBackOutsideTheGate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Refused", UUID.randomUUID());
        ServerPlayer stranger = player(helper, "kw-poor-traveller", 3, 6);
        try {
            var world = KingdomWorldApi.state(server);
            helper.assertTrue(LawWork.state(server).setEntryFee(null, kingdom.id(), 50, LawSettings.rules(), world).ok(), "No entry fee");
            var gate = CitizenGameTests.at(helper, 2, 1, 6);
            var center = CitizenGameTests.at(helper, 6, 1, 6);
            double before = stranger.distanceToSqr(center.x() + 0.5, stranger.getY(), center.z() + 0.5);
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.law.LawCities.enter(stranger, burg(kingdom), true, List.of(gate), center),
                    "A traveller who did not pay came in");
            double after = stranger.distanceToSqr(center.x() + 0.5, stranger.getY(), center.z() + 0.5);
            helper.assertTrue(after > before && stranger.getX() < gate.x(), "The traveller was not put back outside the gate: " + stranger.getX());
            helper.assertTrue(!LawWork.state(server).hasPass(kingdom.id(), stranger.getUUID(), LawWork.now(server)), "A pass without paying");
            // Paying at the window of the gate lets the traveller in for a day.
            world.economy().adminAccount(stranger.getUUID(), kingdom.id(), 50);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawEntry.pay(stranger, kingdom.id(), 50, true).ok(), "The window could not take the fee");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.law.LawCities.enter(stranger, burg(kingdom), true, List.of(gate), center),
                    "A traveller who paid at the window was kept out");
        } finally {
            end(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_trespass")
    public static void gettingInOverTheWallIsATrespassWhenSeen(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Trespass", kingId);
        slowReports();
        Citizen witness = witness(helper, kingdom, 4, 4);
        ServerPlayer climber = player(helper, "kw-climber", 3, 4);
        ServerPlayer subject = player(helper, "kw-local", 3, 5);
        try {
            var world = KingdomWorldApi.state(server);
            world.membership().invite(kingId, subject.getUUID(), 1L);
            world.membership().acceptInvitation(subject.getUUID(), kingdom.id(), 1L);
            helper.assertTrue(LawWork.state(server).setEntryFee(null, kingdom.id(), 50, LawSettings.rules(), world).ok(), "No entry fee");
            var gates = List.of(CitizenGameTests.at(helper, 2, 1, 6));
            var center = CitizenGameTests.at(helper, 6, 1, 6);
            io.github.sekerator.kingdomworld.common.law.LawCities.enter(subject, burg(kingdom), false, gates, center);
            io.github.sekerator.kingdomworld.common.law.LawCities.enter(climber, burg(kingdom), false, gates, center);
            LawWork.deliverNow(server);
            Optional<Wanted> wanted = LawWork.state(server).wanted(kingdom.id(), climber.getUUID());
            helper.assertTrue(wanted.isPresent() && wanted.get().crimes().get(0).kind() == CrimeKind.TRESPASS, "Climbing the wall was no trespass");
            helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), subject.getUUID()).isEmpty(), "A subject trespassed in his own city");
        } finally {
            end(helper, kingdom, witness);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- what the law does not judge

    private static net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos pos) {
        var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, pos, new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_sneaking_item")
    public static void aSneakingStrangerStillCannotUseAnItemAtAForeignChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Sneak", UUID.randomUUID());
        BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer stranger = player(helper, "kw-sneak", 2, 2);
        try {
            level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            stranger.setShiftKeyDown(true);
            var event = click(stranger, chest);
            helper.assertTrue(event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY,
                    "A sneaking stranger may use flint and steel at a foreign chest");
            helper.assertTrue(event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY, "The chest itself was refused");
        } finally {
            end(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_read_only")
    public static void aReadOnlyLawKeepsTheOldRefusals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Locked", UUID.randomUUID());
        Citizen citizen = witness(helper, kingdom, 4, 4);
        BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer stranger = player(helper, "kw-locked", 2, 2);
        LawSavedData data = LawSavedData.get(server);
        try {
            level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            helper.assertTrue(click(stranger, chest).getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY,
                    "A writable law refused the chest");
            data.setReadOnlyForCheck(true);
            helper.assertTrue(click(stranger, chest).getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY,
                    "A read-only law let a stranger open a chest");
            CitizenEntity body = CitizenBodies.body(citizen.id());
            float before = body.getHealth();
            body.hurt(level.damageSources().playerAttack(stranger), 4.0f);
            helper.assertTrue(body.getHealth() == before, "A read-only law let a stranger strike a citizen");
        } finally {
            data.setReadOnlyForCheck(false);
            end(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_law_machines")
    public static void machinesStillCannotHurtCitizens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Law Machines", UUID.randomUUID());
        Citizen citizen = witness(helper, kingdom, 4, 4);
        var allow = io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.allowFakePlayers;
        boolean allowed = allow.get();
        try {
            // Machines answer to the protection as before: with allowFakePlayers off they may not strike citizens.
            allow.set(false);
            net.minecraftforge.common.util.FakePlayer machine = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new GameProfile(UUID.randomUUID(), "[kw-machine]"));
            CitizenEntity body = CitizenBodies.body(citizen.id());
            float before = body.getHealth();
            body.hurt(level.damageSources().playerAttack(machine), 4.0f);
            helper.assertTrue(body.getHealth() == before, "A machine struck a citizen");
            helper.assertTrue(LawWork.state(level.getServer()).wantedIn(kingdom.id()).isEmpty(), "A machine became wanted");
        } finally {
            allow.set(allowed);
            end(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saving

    @GameTest(template = EMPTY, batch = "kingdomworld_law_saving")
    public static void theLawSurvivesSavingAndUnknownGoodsKeepItReadOnly(GameTestHelper helper) {
        LawSavedData data = new LawSavedData();
        UUID kingdom = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LawRules rules = LawRules.DEFAULT;
        data.state().commit(kingdom, player, "kw-saved", CrimeKind.THEFT, 25, "", 100, rules);
        data.state().commit(kingdom, player, "kw-saved", CrimeKind.MURDER, 0, "Radomir", 200, rules);
        data.state().setReputation(kingdom, player, -42);
        data.state().grantPass(kingdom, player, 300, rules);
        data.state().setAutoPay(player, true);
        Confiscation confiscation = data.state().confiscate(kingdom, "Saved Realm", player, "kw-saved", 77, 2, 400, 5);
        CompoundTag first = data.save(new CompoundTag());
        ListTag confiscations = first.getList("confiscations", 10);
        confiscations.getCompound(0).put("goods", new ListTag());
        ListTag goods = confiscations.getCompound(0).getList("goods", 10);
        goods.add(new ItemStack(Items.DIAMOND_SWORD).save(new CompoundTag()));
        goods.add(new ItemStack(Items.BREAD, 3).save(new CompoundTag()));
        confiscations.getCompound(0).put("goods", goods);
        LawSavedData loaded = LawSavedData.load(first);
        helper.assertTrue(!loaded.isReadOnly(), "Readable law data is read-only: " + loaded.readOnlyReason().orElse(""));
        Wanted wanted = loaded.state().wanted(kingdom, player).orElseThrow(() -> new IllegalStateException("The record was lost"));
        helper.assertTrue(wanted.crimes().size() == 2 && wanted.executable() && wanted.stolen() == 25, "The record changed: " + wanted.crimes());
        helper.assertTrue(loaded.state().reputation(kingdom, player) == -42 && loaded.state().hasPass(kingdom, player, 301)
                && loaded.state().autoPay(player), "Reputation, pass or the choice to pay changed");
        helper.assertTrue(loaded.goods(confiscation.id()).size() == 2 && loaded.goods(confiscation.id()).get(1).getCount() == 3,
                "The confiscated goods changed");
        CompoundTag again = loaded.save(new CompoundTag());
        again.getList("confiscations", 10).getCompound(0).getList("goods", 10).getCompound(0).putString("id", "gone_mod:relic");
        LawSavedData broken = LawSavedData.load(again);
        helper.assertTrue(broken.isReadOnly(), "Goods of a mod that is gone did not keep the law read-only");
        helper.assertTrue(broken.save(new CompoundTag()).equals(again), "Read-only law data would be overwritten");
        helper.succeed();
    }
}
