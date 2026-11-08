package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySavedData;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.road.Ambushes;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wars in the world: a declaration, its preparation and start; soldiers and players of kingdoms at war fight each other and
 * leave a player away from the keyboard alone, players of kingdoms at peace do not fight; a raid on a caravan of the enemy
 * takes goods from its cargo without making any; peace with an indemnity moves money between the treasuries by their books;
 * a kingdom with nobody online is shielded; the law does not judge the killing of an enemy in war; and diplomacy outlives a
 * save. Every test fences its place: everything it builds stays in the cleared box of the template.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarGameTests {
    private static final String EMPTY = "empty";

    private WarGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The test's own kingdom on its place, and a second kingdom without land.
     */
    static Kingdom[] kingdoms(GameTestHelper helper, String name, UUID first, UUID second) {
        MinecraftServer server = helper.getLevel().getServer();
        GatheringGameTests.clear(helper.getLevel(), from(helper), to(helper));
        Kingdom home = CitizenGameTests.found(helper, "GameTest War " + name, first);
        // A test that failed before left its enemy behind: it goes with its diplomacy.
        for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
            if (stale.name().equals("GameTest Enemy " + name) || stale.name().equals("GameTest Buyer of the Enemy")) {
                WarWork.state(server).forget(stale.id(), KingdomWorldApi.gameTime(server));
                KingdomWorldApi.dissolveKingdom(server, stale.id());
            }
        }
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, "GameTest Enemy " + name, second);
        if (!(created instanceof CreateKingdomResult.Created result)) {
            throw new IllegalStateException("The enemy kingdom was not created: " + created);
        }
        return new Kingdom[]{home, result.kingdom()};
    }

    static BlockPos from(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(-1, 0, -1));
    }

    static BlockPos to(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(6, 3, 6));
    }

    static ServerPlayer player(GameTestHelper helper, UUID id, String name, int x, int z) {
        ServerPlayer player = LawStandIn.create(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ArmyWork.markOnline(id, true);
        return player;
    }

    static void join(MinecraftServer server, Kingdom kingdom, UUID player) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
    }

    /**
     * Declares a war by an operator and starts it at once.
     */
    static War war(GameTestHelper helper, Kingdom attacker, Kingdom defender, WarGoal goal) {
        MinecraftServer server = helper.getLevel().getServer();
        // Game tests have no real players: both kingdoms count as having one at the keyboard unless a test says otherwise.
        WarWork.markPresent(attacker.id(), true);
        WarWork.markPresent(defender.id(), true);
        Outcome declared = DiplomacyActions.declareByOperator(server, attacker.id(), defender.id(), goal, false);
        helper.assertTrue(declared.ok(), "The war was not declared: " + declared);
        War war = WarWork.state(server).warBetween(attacker.id(), defender.id()).orElseThrow();
        WarWork.startNow(server, war);
        helper.assertTrue(war.active(), "The war did not start");
        return war;
    }

    static Citizen soldier(GameTestHelper helper, Kingdom kingdom, int x) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, 5);
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), Professions.WARRIOR.id()).ok(), "The citizen was not recruited");
        return citizen;
    }

    static void end(GameTestHelper helper, Kingdom home, List<Kingdom> others, List<UUID> players, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        long now = KingdomWorldApi.gameTime(server);
        DiplomacySettings.override(null);
        DiplomacySettings.overridePeaceful(null);
        WarWork.overrideRolls(null);
        for (UUID player : players) {
            ArmyWork.markOnline(player, false);
            ArmyWork.markAway(player, false);
        }
        DiplomacyState state = WarWork.state(server);
        WarWork.markPresent(home.id(), null);
        state.forget(home.id(), now);
        for (Kingdom other : others) {
            WarWork.markPresent(other.id(), null);
            state.forget(other.id(), now);
            ArmyWork.state(server).forgetKingdom(other.id());
            KingdomWorldApi.dissolveKingdom(server, other.id());
        }
        ArmyWork.state(server).forgetKingdom(home.id());
        CitizenGameTests.finish(helper, home, citizens);
        GatheringGameTests.clear(helper.getLevel(), from(helper), to(helper));
    }

    // ---------------------------------------------------------------- declaration

    @GameTest(template = EMPTY, batch = "kingdomworld_war_declare", timeoutTicks = 200)
    public static void aWarIsDeclaredPreparedAndThenStarts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        UUID enemyKing = UUID.randomUUID();
        Kingdom[] both = kingdoms(helper, "Declared", king, enemyKing);
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        UUID subject = UUID.randomUUID();
        join(server, home, subject);
        DiplomacySettings.override(DiplomacyRules.DEFAULT.withPreparation(40));
        WarWork.markPresent(enemy.id(), false);
        helper.assertTrue(DiplomacyActions.declare(server, king, home.id(), enemy.id(), WarGoal.RAID, false).error() == KingdomError.NOBODY_ONLINE,
                "A war was declared on a kingdom with nobody online");
        WarWork.markPresent(enemy.id(), true);
        helper.assertTrue(DiplomacyActions.declare(server, subject, home.id(), enemy.id(), WarGoal.RAID, false).error() == KingdomError.NO_PERMISSION,
                "A subject declared war");
        Outcome declared = DiplomacyActions.declare(server, king, home.id(), enemy.id(), WarGoal.TRIBUTE, false);
        helper.assertTrue(declared.ok(), "The king could not declare war: " + declared);
        DiplomacyState state = WarWork.state(server);
        War war = state.warBetween(home.id(), enemy.id()).orElseThrow();
        long now = KingdomWorldApi.gameTime(server);
        helper.assertTrue(war.phase() == War.Phase.PREPARING && state.bond(home.id(), enemy.id(), now) == Bond.WAR, "The war is not being prepared");
        helper.assertTrue(!WarWork.fighting(server, home.id(), enemy.id()), "The war started before its preparation was over");
        helper.assertTrue(state.chronicle().stream().anyMatch(event -> event.kind().equals("declared") && event.war() == war.id()),
                "The declaration is not in the chronicle");
        helper.runAfterDelay(80, () -> {
            try {
                helper.assertTrue(war.active() && WarWork.fighting(server, home.id(), enemy.id()), "The war did not start after its preparation");
                helper.assertTrue(state.chronicle().stream().anyMatch(event -> event.kind().equals("started") && event.war() == war.id()),
                        "The start is not in the chronicle");
            } finally {
                end(helper, home, List.of(enemy), List.of());
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- near players

    @GameTest(template = EMPTY, batch = "kingdomworld_war_soldiers")
    public static void soldiersAttackAnEnemyPlayerAtTheKeyboardButNotOneAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = kingdoms(helper, "Soldiers", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        Citizen soldier = soldier(helper, home, 1);
        UUID foeId = UUID.randomUUID();
        ServerPlayer foe = player(helper, foeId, "kw-foe", 3, 3);
        join(server, enemy, foeId);
        try {
            CitizenBodies.force(soldier.id(), true);
            CitizenBodies.update(server);
            CitizenEntity body = CitizenBodies.body(soldier.id());
            helper.assertTrue(body != null, "The soldier got no body");
            helper.assertTrue(!ArmyWork.mayAttack(server, home.id(), foe), "A soldier may attack a foreign player in peace");
            war(helper, home, enemy, WarGoal.RAID);
            helper.assertTrue(ArmyWork.mayAttack(server, home.id(), foe), "A soldier may not attack an enemy player at the keyboard");
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(foe, level.damageSources().mobAttack(body), 4.0f)),
                    "The blow of a soldier at an enemy player was stopped");
            ArmyWork.markAway(foeId, true);
            helper.assertTrue(!ArmyWork.mayAttack(server, home.id(), foe), "A soldier may attack an enemy player away from the keyboard");
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(foe, level.damageSources().mobAttack(body), 4.0f)),
                    "The blow of a soldier at an enemy player away from the keyboard was not stopped");
            ArmyWork.markAway(foeId, false);
            ArmyWork.markOnline(foeId, false);
            helper.assertTrue(!ArmyWork.mayAttack(server, home.id(), foe), "A soldier may attack an enemy player who is offline");
        } finally {
            end(helper, home, List.of(enemy), List.of(foeId), soldier);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_war_pvp")
    public static void playersOfKingdomsAtWarFightAndPlayersAtPeaceDoNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = kingdoms(helper, "Duel", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        UUID oursId = UUID.randomUUID();
        UUID theirsId = UUID.randomUUID();
        ServerPlayer ours = player(helper, oursId, "kw-ours", 2, 3);
        ServerPlayer theirs = player(helper, theirsId, "kw-theirs", 3, 3);
        join(server, home, oursId);
        join(server, enemy, theirsId);
        try {
            DiplomacySettings.overridePeaceful(true);
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(theirs, level.damageSources().playerAttack(ours), 4.0f)),
                    "A player hurt a player of a kingdom at peace");
            war(helper, home, enemy, WarGoal.RAID);
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(theirs, level.damageSources().playerAttack(ours), 4.0f)),
                    "A player could not hurt an enemy player at war");
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(ours, level.damageSources().playerAttack(theirs), 4.0f)),
                    "The enemy could not strike back");
            ArmyWork.markAway(theirsId, true);
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(theirs, level.damageSources().playerAttack(ours), 4.0f)),
                    "A player hurt an enemy player away from the keyboard");
            long now = KingdomWorldApi.gameTime(server);
            helper.assertTrue(WarWork.state(server).presence().lastFight(home.id()) == now, "The fight did not keep the shield away");
        } finally {
            end(helper, home, List.of(enemy), List.of(oursId, theirsId));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- caravans

    @GameTest(template = EMPTY, batch = "kingdomworld_war_raid")
    public static void aRaidTakesGoodsFromTheCargoOfTheEnemyWithoutMakingAny(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = kingdoms(helper, "Raiders", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        CreateKingdomResult third = KingdomWorldApi.createKingdom(server, "GameTest Buyer of the Enemy", UUID.randomUUID());
        Kingdom buyer = ((CreateKingdomResult.Created) third).kingdom();
        CitizenGameTests.storehouse(helper, 0, 0, 4);
        Citizen first = soldier(helper, home, 1);
        Citizen second = soldier(helper, home, 2);
        try {
            KingdomWorldState world = KingdomWorldApi.state(server);
            Economy economy = world.economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(buyer.id(), 500, day);
            long money = economy.supply(buyer.id());
            long now = KingdomWorldApi.gameTime(server);
            Deal deal = world.trade().addDeal(enemy.id(), buyer.id(), enemy.id(), "minecraft:wheat", 16, 80, buyer.id(), 0, now, now + 24_000);
            Caravan caravan = world.sendCaravan(deal, List.of(Goods.of("minecraft:wheat", 16)), Dimensions.id(helper.getLevel()), 0, 0, 4_000, 0,
                    now, 200_000).orElseThrow(() -> new IllegalStateException("No caravan"));
            ArmySquad raiders = ArmyWork.state(server).formSquad(null, home.id(), List.of(first.id(), second.id()), ArmySettings.rules(), world)
                    .squad().orElseThrow();
            War war = war(helper, home, enemy, WarGoal.RAID);
            WarWork.overrideRolls(() -> 0.0);
            List<Goods> taken = WarWork.raid(server, raiders, caravan, DiplomacySettings.rules(), now).orElseThrow(
                    () -> new IllegalStateException("The raid took nothing"));
            long items = Ambushes.items(taken);
            long stored = KingdomStorage.count(server, home.id(), stack -> stack.is(Items.WHEAT));
            Caravan left = world.trade().caravan(caravan.id()).orElse(null);
            long rest = left == null ? 0 : left.items();
            helper.assertTrue(items > 0 && stored == items, "The loot did not reach the storehouses: " + items + " taken, " + stored + " stored");
            helper.assertTrue(rest + stored == 16, "Goods were made or lost: " + rest + " on the way and " + stored + " stored");
            long refund = 80 * items / 16;
            helper.assertTrue(economy.treasury(buyer.id()).orElseThrow().funds() == 500 - 80 + refund, "The payment for the taken goods did not come back");
            helper.assertTrue(economy.supply(buyer.id()) == money, "The raid made or lost money");
            helper.assertTrue(war.tally(War.Side.ATTACKER).caravans() == 1 && war.score(War.Side.ATTACKER) > 0, "The raid did not count for the score");
            if (left != null) {
                world.trade().removeCaravan(left.id());
                economy.refundEscrow(left.escrowId(), day);
            }
        } finally {
            end(helper, home, List.of(enemy, buyer), List.of(), first, second);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- peace

    @GameTest(template = EMPTY, batch = "kingdomworld_war_indemnity")
    public static void peaceWithAnIndemnityMovesMoneyByTheBooks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        UUID enemyKing = UUID.randomUUID();
        Kingdom[] both = kingdoms(helper, "Indemnity", king, enemyKing);
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(enemy.id(), 1_000, day);
            War war = war(helper, home, enemy, WarGoal.INDEMNITY);
            PeaceTerms tooMuch = new PeaceTerms(home.id(), 300, 0, 0, false, false, 0, "", false);
            helper.assertTrue(DiplomacyActions.propose(server, king, home.id(), enemy.id(), Proposal.Kind.PEACE, null, war.id(), tooMuch).error()
                    == KingdomError.TERMS_TOO_HIGH, "Terms were asked without a lead in the score");
            WarWork.change(server, "lead", state -> state.setScore(war.id(), War.Side.ATTACKER, 60, -1));
            helper.assertTrue(DiplomacyActions.propose(server, king, home.id(), enemy.id(), Proposal.Kind.PEACE, null, war.id(), tooMuch).ok(),
                    "Terms could not be asked with a lead");
            long supply = economy.supply(enemy.id());
            long proposal = WarWork.state(server).openProposals(enemy.id()).get(0).id();
            Outcome accepted = DiplomacyActions.answer(server, enemyKing, enemy.id(), proposal, true);
            helper.assertTrue(accepted.ok(), "The peace was not accepted: " + accepted);
            helper.assertTrue(war.over() && war.result() == War.Result.PEACE, "The war is not over");
            helper.assertTrue(economy.treasury(enemy.id()).orElseThrow().funds() == 700, "The loser did not pay the indemnity");
            helper.assertTrue(economy.treasury(home.id()).orElseThrow().reserve(enemy.id()) == 300, "The winner did not get the indemnity");
            helper.assertTrue(economy.supply(enemy.id()) == supply, "The peace made or lost money");
            Map<LedgerCategory, TreasuryJournal.Totals> paid = economy.treasury(enemy.id()).orElseThrow().journal().totals(day,
                    TreasuryJournal.Period.TODAY);
            // The winner keeps the money of the loser in its reserves, as with trade: the journal of the loser shows the payment.
            helper.assertTrue(paid.getOrDefault(LedgerCategory.WAR, TreasuryJournal.Totals.ZERO).expense() == 300,
                    "The journal of the loser does not show the indemnity");
            long now = KingdomWorldApi.gameTime(server);
            helper.assertTrue(WarWork.state(server).bond(home.id(), enemy.id(), now) == Bond.TRUCE, "No truce after the peace");
        } finally {
            end(helper, home, List.of(enemy), List.of());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the absent

    @GameTest(template = EMPTY, batch = "kingdomworld_war_shield")
    public static void aKingdomWithNobodyOnlineIsShielded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = kingdoms(helper, "Shield", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        try {
            DiplomacyRules rules = DiplomacySettings.rules();
            WarWork.markPresent(home.id(), true);
            War war = war(helper, home, enemy, WarGoal.RAID);
            WarWork.markPresent(enemy.id(), false);
            helper.assertTrue(WarWork.shielded(server, enemy.id()), "A kingdom nobody of which was ever online is not shielded");
            helper.assertTrue(!WarWork.shielded(server, home.id()), "A kingdom with a player at the keyboard is shielded");
            WarWork.forceDay(server);
            helper.assertTrue(war.exhaustion(War.Side.DEFENDER) == 0.0 && war.exhaustion(War.Side.ATTACKER) > 0.0,
                    "Exhaustion grew for the shielded side or not for the other");
            long now = KingdomWorldApi.gameTime(server);
            WarWork.state(server).presence().present(enemy.id(), now);
            helper.assertTrue(!WarWork.shielded(server, enemy.id()), "The shield came at once after the last player left");
            WarWork.state(server).presence().restore(enemy.id(), now - rules.absenceTicks(), Long.MIN_VALUE / 4);
            helper.assertTrue(WarWork.shielded(server, enemy.id()), "The shield did not come after the delay");
            WarWork.fought(server, enemy.id());
            helper.assertTrue(!WarWork.shielded(server, enemy.id()), "The shield came right after a fight near the players");
        } finally {
            end(helper, home, List.of(enemy), List.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_war_lone_player")
    public static void aLonePlayerCannotLiftTheShieldOfAKingdomWithNobodyOnline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = kingdoms(helper, "Lone", UUID.randomUUID(), UUID.randomUUID());
        Kingdom absent = both[0];
        Kingdom raider = both[1];
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        Citizen soldier = soldier(helper, absent, 1);
        UUID lone = UUID.randomUUID();
        ServerPlayer player = player(helper, lone, "kw-lone", 3, 3);
        join(server, raider, lone);
        try {
            CitizenBodies.force(soldier.id(), true);
            CitizenBodies.update(server);
            CitizenEntity body = CitizenBodies.body(soldier.id());
            helper.assertTrue(body != null, "The soldier got no body");
            War war = war(helper, raider, absent, WarGoal.RAID);
            WarWork.markPresent(absent.id(), false);
            helper.assertTrue(WarWork.shielded(server, absent.id()), "A kingdom with nobody online is not shielded");
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(body, level.damageSources().playerAttack(player), 4.0f)),
                    "A blow of an enemy at a soldier of a shielded kingdom went through");
            helper.assertTrue(!ArmyWork.mayAttack(server, raider.id(), body), "Soldiers of the enemy may attack a soldier of a shielded kingdom");
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(body, level.damageSources().playerAttack(player)));
            helper.assertTrue(WarWork.shielded(server, absent.id()), "The lone enemy lifted the shield");
            helper.assertTrue(war.score(War.Side.ATTACKER) == 0.0 && war.exhaustion(War.Side.DEFENDER) == 0.0
                    && war.tally(War.Side.ATTACKER).kills() == 0, "A kill counted against a shielded kingdom: score "
                    + war.score(War.Side.ATTACKER) + ", exhaustion " + war.exhaustion(War.Side.DEFENDER));
            WarWork.markPresent(absent.id(), true);
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(body, level.damageSources().playerAttack(player), 4.0f)),
                    "A blow at a soldier of an enemy whose players are there was stopped");
        } finally {
            LawWork.state(server).forgetKingdom(absent.id());
            end(helper, absent, List.of(raider), List.of(lone), soldier);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the law

    @GameTest(template = EMPTY, batch = "kingdomworld_war_law")
    public static void theLawDoesNotJudgeTheKillingOfAnEnemyInWar(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = kingdoms(helper, "Law", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        UUID oursId = UUID.randomUUID();
        ServerPlayer ours = player(helper, oursId, "kw-warrior", 2, 3);
        join(server, home, oursId);
        try {
            helper.assertTrue(LawWork.isCrime(server, ours, enemy.id(), CrimeKind.MURDER, null, false, enemy.id()),
                    "Killing a foreigner in peace is no murder there");
            helper.assertTrue(LawWork.isCrime(server, ours, home.id(), CrimeKind.MURDER, null, false, enemy.id()),
                    "Killing a foreigner in peace is no murder at home");
            war(helper, home, enemy, WarGoal.RAID);
            helper.assertTrue(!LawWork.isCrime(server, ours, enemy.id(), CrimeKind.MURDER, null, false, enemy.id()),
                    "The law of the enemy judges the killing of its people in war");
            helper.assertTrue(!LawWork.isCrime(server, ours, home.id(), CrimeKind.MURDER, null, false, enemy.id()),
                    "The law at home judges the killing of an enemy");
            helper.assertTrue(!LawWork.isCrime(server, ours, enemy.id(), CrimeKind.ASSAULT, null, false, enemy.id()),
                    "A blow at an enemy in war is a crime");
            helper.assertTrue(LawWork.isCrime(server, ours, enemy.id(), CrimeKind.THEFT, null, false, null),
                    "A theft from the enemy is no crime: the land stays protected until sieges");
        } finally {
            end(helper, home, List.of(enemy), List.of(oursId));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saving

    @GameTest(template = EMPTY, batch = "kingdomworld_war_saving")
    public static void diplomacyOutlivesASave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = kingdoms(helper, "Saving", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        try {
            War war = war(helper, home, enemy, WarGoal.TRIBUTE);
            WarWork.change(server, "score", state -> state.setScore(war.id(), War.Side.ATTACKER, 42.5, 17.25));
            DiplomacySavedData data = DiplomacySavedData.get(server);
            DiplomacySavedData copy = DiplomacySavedData.copyOf(data);
            helper.assertTrue(!copy.isReadOnly(), "The copy is read-only: " + copy.readOnlyReason().orElse(""));
            War again = copy.state().war(war.id()).orElseThrow(() -> new IllegalStateException("The war was lost"));
            helper.assertTrue(again.active() && again.score(War.Side.ATTACKER) == 42.5 && again.exhaustion(War.Side.ATTACKER) == 17.25
                    && again.goal() == WarGoal.TRIBUTE, "The war changed on the way: " + again.score(War.Side.ATTACKER));
            CompoundTag broken = data.save(new CompoundTag());
            broken.putInt("formatVersion", 99);
            helper.assertTrue(DiplomacySavedData.load(broken).isReadOnly(), "Data of a newer format is not read-only");
            CompoundTag damaged = data.save(new CompoundTag());
            damaged.getList("wars", 10).getCompound(0).putString("goal", "conquest of the moon");
            DiplomacySavedData read = DiplomacySavedData.load(damaged);
            helper.assertTrue(read.isReadOnly(), "A war that cannot be read did not make the data read-only");
        } finally {
            end(helper, home, List.of(enemy), List.of());
        }
        helper.succeed();
    }
}
