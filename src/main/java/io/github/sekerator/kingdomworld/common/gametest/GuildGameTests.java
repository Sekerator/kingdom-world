package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.guild.GuildRegistries;
import io.github.sekerator.kingdomworld.common.guild.GuildSavedData;
import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.guild.MonsterRanks;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.guild.Squad;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of the guild: a monster killed by a player leaves a crystal of its rank, monsters from a spawner less and
 * less; a player joins at a desk kept by a registrar and gets rank F; a hunt counts kills and pays from escrow with the
 * commission for the branch; a contract that runs out gives the reward back; items are handed in all at once or not at
 * all; an exam raises the rank; a squad goes on an expedition and comes back with the loot, and a fallen adventurer goes to
 * the chronicle; the server checks the right to manage the guild; the guild survives saving and the marshal gets the new
 * right on upgrade. Every test runs in a batch of its own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuildGameTests {
    private static final String EMPTY = "empty";

    private GuildGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID player, Role role) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
        if (role != Role.SUBJECT) {
            state.membership().setRole(kingdom.rulerId(), player, role);
        }
    }

    /**
     * Rules in which every monster leaves a crystal and farms start late.
     */
    private static GuildRules generous() {
        GuildRules.Builder builder = GuildSettings.configured();
        builder.crystalChance = new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1};
        builder.farmFreeKills = 1_000;
        builder.riskMultiplier = 0.0;
        return builder.build();
    }

    /**
     * A registrar at a post with a desk next to it, keeping the desk open.
     */
    private static Citizen registrar(GameTestHelper helper, Kingdom kingdom, BlockPos desk, BlockPos post) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.setBlock(desk, GuildRegistries.GUILD_DESK.get());
        WorldPos postPos = GatheringGameTests.post(helper, post, Professions.REGISTRAR.id());
        WorkPost found = PostWork.post(server, postPos).orElseThrow();
        ZoneSurveys.surveyNow(server, found);
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 3);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), Professions.REGISTRAR.id()));
        KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, postPos);
        return citizen;
    }

    private static Citizen adventurer(GameTestHelper helper, Kingdom kingdom, int x) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, 5);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), Professions.ADVENTURER.id()));
        return citizen;
    }

    private static GuildPackets.DeskAction post(BlockPos desk, Contract.Kind kind, String target, int count, long reward, boolean forKingdom) {
        return new GuildPackets.DeskAction(desk, GuildPackets.DeskAction.Action.POST, 0, kind, target, 0, count, reward, 0, 2, forKingdom, true);
    }

    private static GuildPackets.DeskAction act(BlockPos desk, GuildPackets.DeskAction.Action action, long id) {
        return GuildPackets.DeskAction.simple(desk, action, id);
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z, boolean spawner) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 1, z));
        zombie.setNoAi(true);
        if (spawner) {
            zombie.getPersistentData().putBoolean(GuildWork.SPAWNER_TAG, true);
        }
        return zombie;
    }

    private static void kill(GameTestHelper helper, net.minecraft.world.entity.LivingEntity victim, ServerPlayer player) {
        victim.hurt(helper.getLevel().damageSources().playerAttack(player), 10_000.0f);
    }

    private static List<ItemEntity> crystals(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16),
                item -> MonsterCrystalItem.isCrystal(item.getItem()));
    }

    private static void clearCrystals(GameTestHelper helper) {
        crystals(helper).forEach(ItemEntity::discard);
    }

    private static Contract newest(MinecraftServer server) {
        Contract found = null;
        for (Contract contract : GuildWork.state(server).contracts()) {
            if (found == null || contract.id() > found.id()) {
                found = contract;
            }
        }
        return found;
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        GuildSettings.override(null);
        GuildWork.overrideRolls(null);
        GuildWork.state(server).forgetKingdom(kingdom.id(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        CitizenGameTests.finish(helper, kingdom, citizens);
        clearCrystals(helper);
        GatheringGameTests.clear(helper.getLevel(), from, to);
    }

    // ---------------------------------------------------------------- crystals

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_crystals")
    public static void aMonsterKilledByAPlayerLeavesACrystalOfItsRank(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = player(helper, "kw-hunter");
        GuildSettings.override(generous());
        try {
            clearCrystals(helper);
            Zombie zombie = zombie(helper, 3, 3, false);
            Rank rank = MonsterRanks.of(zombie).rank();
            helper.assertTrue(rank == Rank.E, "A zombie should be of rank E, not " + rank);
            kill(helper, zombie, player);
            List<ItemEntity> dropped = crystals(helper);
            helper.assertTrue(dropped.size() == 1, "One crystal should fall, not " + dropped.size());
            helper.assertTrue(MonsterCrystalItem.rank(dropped.get(0).getItem()).orElseThrow() == Rank.E, "The crystal has the wrong rank");
            helper.assertTrue(GuildWork.lastKill().orElseThrow().factor() == 1.0, "A single kill is no farm");
            clearCrystals(helper);

            Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
            kill(helper, cow, player);
            Zombie unwatched = zombie(helper, 5, 5, false);
            unwatched.hurt(helper.getLevel().damageSources().generic(), 10_000.0f);
            helper.assertTrue(crystals(helper).isEmpty(), "Animals and monsters nobody killed left crystals");
            helper.assertTrue(server.getLootData() != null && KingdomWorldApi.itemValue(server, MonsterCrystalItem.stack(Rank.SSS, 1))
                    > KingdomWorldApi.itemValue(server, MonsterCrystalItem.stack(Rank.F, 1)), "A crystal of SSS should be worth more than one of F");
        } finally {
            GuildSettings.override(null);
            clearCrystals(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_spawner")
    public static void monstersFromASpawnerGiveLessAndLess(GameTestHelper helper) {
        ServerPlayer player = player(helper, "kw-farmer");
        GuildSettings.override(generous());
        GuildWork.forgetFarms();
        try {
            List<Double> factors = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                kill(helper, zombie(helper, 3, 3, true), player);
                factors.add(GuildWork.lastKill().orElseThrow().factor());
            }
            helper.assertTrue(factors.get(0) == GuildSettings.rules().spawnerFactor, "The first monster from a spawner gives "
                    + GuildSettings.rules().spawnerFactor + ", not " + factors.get(0));
            for (int i = 1; i < factors.size(); i++) {
                helper.assertTrue(factors.get(i) < factors.get(i - 1), "The chance did not fall: " + factors);
            }
            helper.assertTrue(GuildWork.lastKill().orElseThrow().chance() < 0.5, "The chance of the sixth crystal is still high");
        } finally {
            GuildSettings.override(null);
            clearCrystals(helper);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the desk

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_join")
    public static void aPlayerJoinsAtADeskKeptByARegistrar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Guild Hall", UUID.randomUUID());
        ServerPlayer player = player(helper, "kw-newcomer");
        Citizen registrar = null;
        try {
            BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
            helper.setBlock(new BlockPos(6, 0, 1), GuildRegistries.GUILD_DESK.get());
            helper.assertTrue(GuildActions.openBranch(player, desk, false).isEmpty(), "A desk without a registrar was open");
            registrar = registrar(helper, kingdom, new BlockPos(6, 0, 1), new BlockPos(5, 0, 1));
            helper.assertTrue(GuildActions.openBranch(player, desk, false).isPresent(), "The registrar did not open the desk");
            GuildActions.handleDesk(player, act(desk, GuildPackets.DeskAction.Action.JOIN, 0));
            Optional<GuildMember> member = GuildWork.state(server).member(player.getUUID());
            helper.assertTrue(member.isPresent() && member.get().rank() == Rank.F, "Joining did not give rank F");
            helper.assertTrue(!GuildActions.apply(player, kingdom, act(desk, GuildPackets.DeskAction.Action.JOIN, 0)).success(),
                    "A member joined twice");
            helper.assertTrue(GuildActions.deskView(player, desk, kingdom).orElseThrow().card().member(), "The card does not show the member");
        } finally {
            if (registrar != null) {
                end(helper, kingdom, from, to, registrar);
            } else {
                end(helper, kingdom, from, to);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_hunt")
    public static void aHuntCountsKillsAndPaysFromEscrowWithTheCommission(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Hunters Lodge", kingId);
        ServerPlayer king = new ServerPlayer(server, level, new GameProfile(kingId, "kw-king"));
        ServerPlayer hunter = player(helper, "kw-hunter");
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        GuildSettings.override(generous());
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            long before = economy.supply(kingdom.id());
            helper.assertTrue(GuildActions.apply(king, kingdom, post(desk, Contract.Kind.HUNT, "minecraft:zombie", 2, 100, true)).success(),
                    "The king could not post a hunt");
            Contract contract = newest(server);
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 900, "The reward did not go into escrow");
            helper.assertTrue(economy.supply(kingdom.id()) == before, "Escrow made or lost money");
            GuildActions.apply(hunter, kingdom, act(desk, GuildPackets.DeskAction.Action.JOIN, 0));
            helper.assertTrue(GuildActions.apply(hunter, kingdom, act(desk, GuildPackets.DeskAction.Action.TAKE, contract.id())).success(),
                    "The hunter could not take the hunt");
            kill(helper, zombie(helper, 3, 3, false), hunter);
            helper.assertTrue(GuildWork.state(server).contract(contract.id()).orElseThrow().progress() == 1, "The kill did not count");
            kill(helper, zombie(helper, 4, 4, false), hunter);
            helper.assertTrue(GuildWork.state(server).contract(contract.id()).isEmpty(), "The finished hunt stayed on the board");
            long commission = contract.commission();
            helper.assertTrue(economy.accounts().balance(hunter.getUUID(), kingdom.id()) == 100 - commission, "The hunter was not paid "
                    + (100 - commission));
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 900 + commission, "The commission did not reach the treasury");
            helper.assertTrue(economy.supply(kingdom.id()) == before, "The hunt made or lost money");
            helper.assertTrue(GuildWork.state(server).member(hunter.getUUID()).orElseThrow().points() >= contract.points(), "No points");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_expiry")
    public static void aContractThatRunsOutGivesTheRewardBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lapsed Bounty", UUID.randomUUID());
        ServerPlayer poster = player(helper, "kw-poster");
        ServerPlayer hunter = player(helper, "kw-slow");
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(poster.getUUID(), kingdom.id(), 1_000);
            long before = economy.supply(kingdom.id());
            helper.assertTrue(GuildActions.apply(poster, kingdom, post(desk, Contract.Kind.BOUNTY, "minecraft:ravager", 1, 500, false)).success(),
                    "The player could not post a bounty");
            Contract contract = newest(server);
            long fee = GuildState.postingFee(500, GuildSettings.rules());
            helper.assertTrue(economy.accounts().balance(poster.getUUID(), kingdom.id()) == 500 - fee, "The reward and the fee were not taken");
            GuildWork.state(server).setRank(hunter.getUUID(), Rank.B, 0);
            GuildWork.state(server).addPoints(hunter.getUUID(), 1_000);
            helper.assertTrue(GuildActions.apply(hunter, kingdom, act(desk, GuildPackets.DeskAction.Action.TAKE, contract.id())).success(),
                    "The bounty could not be taken");
            GuildWork.state(server).runOut(contract.id(), KingdomWorldApi.gameTime(server));
            GuildWork.tick(server);
            helper.assertTrue(GuildWork.state(server).contract(contract.id()).isEmpty(), "The contract did not run out");
            helper.assertTrue(economy.accounts().balance(poster.getUUID(), kingdom.id()) == 1_000 - fee, "The reward did not come back");
            helper.assertTrue(GuildWork.state(server).member(hunter.getUUID()).orElseThrow().points()
                    == 1_000 - GuildState.penalty(contract, GuildSettings.rules()), "The taker kept all points");
            helper.assertTrue(economy.supply(kingdom.id()) == before, "Money was made or lost");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_gather")
    public static void itemsAreHandedInAllAtOnceOrNotAtAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bone Collectors", kingId);
        ServerPlayer king = new ServerPlayer(server, level, new GameProfile(kingId, "kw-king"));
        ServerPlayer gatherer = player(helper, "kw-gatherer");
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            GuildActions.apply(king, kingdom, post(desk, Contract.Kind.GATHER, "minecraft:bone", 32, 64, true));
            Contract contract = newest(server);
            GuildActions.apply(gatherer, kingdom, act(desk, GuildPackets.DeskAction.Action.JOIN, 0));
            GuildActions.apply(gatherer, kingdom, act(desk, GuildPackets.DeskAction.Action.TAKE, contract.id()));
            gatherer.getInventory().add(new ItemStack(Items.BONE, 31));
            KingdomActions.Feedback refused = GuildActions.handIn(gatherer, contract.id());
            helper.assertTrue(!refused.success(), "Too few bones were taken");
            helper.assertTrue(gatherer.getInventory().countItem(Items.BONE) == 31, "Bones left the inventory of a refused hand-in");
            helper.assertTrue(CitizenGameTests.count(store, Items.BONE) == 0, "Bones reached the storehouse of a refused hand-in");
            gatherer.getInventory().add(new ItemStack(Items.BONE, 5));
            helper.assertTrue(GuildActions.handIn(gatherer, contract.id()).success(), "The hand-in failed");
            helper.assertTrue(gatherer.getInventory().countItem(Items.BONE) == 4, "The wrong number of bones left the inventory");
            helper.assertTrue(CitizenGameTests.count(store, Items.BONE) == 32, "The bones did not reach the kingdom's storehouse");
            helper.assertTrue(economy.accounts().balance(gatherer.getUUID(), kingdom.id()) == contract.payout(), "The gatherer was not paid");
            helper.assertTrue(!GuildActions.handIn(gatherer, contract.id()).success(), "A contract was handed in twice");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_exam")
    public static void anExamRaisesTheRank(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Exam Hall", UUID.randomUUID());
        ServerPlayer student = player(helper, "kw-student");
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        try {
            GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.JOIN, 0));
            helper.assertTrue(!GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.EXAM, 0)).success(),
                    "An exam started without points");
            GuildWork.state(server).addPoints(student.getUUID(), GuildSettings.rules().rankPoints(Rank.E));
            helper.assertTrue(GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.EXAM, 0)).success(),
                    "The exam did not start");
            Contract exam = GuildWork.state(server).exam(student.getUUID()).orElseThrow();
            for (int i = 0; i < exam.count(); i++) {
                kill(helper, zombie(helper, 1 + i % 6, 2 + i / 6, false), student);
            }
            helper.assertTrue(GuildWork.state(server).member(student.getUUID()).orElseThrow().rank() == Rank.E, "The exam did not give rank E");
            helper.assertTrue(GuildWork.state(server).exam(student.getUUID()).isEmpty(), "The exam stayed open");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_branch_rank")
    public static void anExamAboveTheRankOfTheBranchIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Small Branch", UUID.randomUUID());
        ServerPlayer student = player(helper, "kw-climber");
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        GuildRules rules = GuildSettings.rules();
        try {
            GuildState guild = GuildWork.state(server);
            helper.assertTrue(guild.branchOrDefault(kingdom.id(), rules).rank() == io.github.sekerator.kingdomworld.core.guild.Branch.FIRST_RANK,
                    "A new branch is not of the first rank");
            GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.JOIN, 0));
            guild.setRank(student.getUUID(), Rank.E, 0);
            guild.addPoints(student.getUUID(), rules.rankPoints(Rank.D));
            // A branch of rank E examines nobody for D.
            helper.assertTrue(!GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.EXAM, 0)).success(),
                    "A branch of rank E started an exam for D");
            helper.assertTrue(guild.exam(student.getUUID()).isEmpty(), "The refused exam was opened");
            helper.assertTrue(guild.checkExam(student.getUUID(), kingdom.id(), KingdomWorldApi.gameTime(server), rules).error()
                    == KingdomError.BRANCH_RANK_TOO_LOW, "The exam was refused for another reason");
            // A contract asking for a rank above the branch is refused as well.
            helper.assertTrue(!GuildActions.apply(student, kingdom, new GuildPackets.DeskAction(desk, GuildPackets.DeskAction.Action.POST, 0,
                    Contract.Kind.HUNT, "minecraft:zombie", 0, 1, 100, Rank.C.ordinal(), 2, false, true)).success()
                    || guild.contracts().stream().noneMatch(c -> !c.isExam() && c.minRank() == Rank.C), "A contract for C was posted at a branch of E");
            guild.setBranchRank(kingdom.id(), Rank.D, rules);
            helper.assertTrue(GuildActions.apply(student, kingdom, act(desk, GuildPackets.DeskAction.Action.EXAM, 0)).success(),
                    "A branch of rank D did not examine for D");
            helper.assertTrue(guild.exam(student.getUUID()).map(Contract::targetRank).orElse(null) == Rank.D, "The exam is not for D");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- squads

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_squad", timeoutTicks = 200)
    public static void aSquadGoesOnAnExpeditionAndComesBackWithLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Free Company", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        store.insertItem(1, new ItemStack(Items.IRON_SWORD), false);
        Citizen first = adventurer(helper, kingdom, 1);
        Citizen second = adventurer(helper, kingdom, 2);
        GuildSettings.override(generous());
        GuildWork.overrideRolls(() -> 0.0);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            long before = economy.supply(kingdom.id());
            helper.assertTrue(GuildActions.applyTab(server, null, new GuildPackets.TabChange(kingdom.id(), GuildPackets.TabChange.Action.FORM_SQUAD,
                    0, 0, List.of(first.id(), second.id()))).success(), "The squad was not formed");
            Squad squad = GuildWork.state(server).squadOf(first.id()).orElseThrow();
            GuildState.Made made = GuildWork.state(server).post(null, new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(),
                    Contract.Kind.HUNT, "minecraft:zombie", Rank.E, 4, 100, Rank.E, 2, true), KingdomWorldApi.gameTime(server),
                    TreasuryActions.day(server), GuildSettings.rules(), KingdomWorldApi.state(server));
            Contract contract = made.contract().orElseThrow();
            helper.assertTrue(GuildActions.applyTab(server, null, new GuildPackets.TabChange(kingdom.id(), GuildPackets.TabChange.Action.SEND,
                    squad.id(), contract.id(), List.of())).success(), "The squad was not sent");
            helper.assertTrue(GuildWork.state(server).isAway(first.id()), "The squad is not away");
            CitizenBodies.force(first.id(), true);
            CitizenBodies.update(server);
            helper.assertTrue(CitizenBodies.body(first.id()) == null, "An adventurer away kept a body");
            CitizenBodies.force(first.id(), false);
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.IRON_SWORD)) == 0,
                    "The adventurers did not take the swords");

            helper.assertTrue(GuildWork.bringBackAll(server) == 1, "The squad did not come back");
            helper.assertTrue(!GuildWork.state(server).isAway(first.id()), "The squad is still away");
            helper.assertTrue(GuildWork.state(server).contract(contract.id()).isEmpty(), "The contract is not done");
            long crystals = KingdomStorage.count(server, kingdom.id(), stack -> MonsterCrystalItem.rank(stack).orElse(Rank.F) == Rank.E);
            helper.assertTrue(crystals == 4, "Four crystals of rank E should be in the storehouse, not " + crystals);
            helper.assertTrue(GuildWork.state(server).hireling(first.id()).orElseThrow().kills() == 4, "The kills were not counted");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 1_000, "The kingdom paid itself, less nothing: "
                    + economy.treasury(kingdom.id()).orElseThrow().funds());
            helper.assertTrue(economy.supply(kingdom.id()) == before, "The expedition made or lost money");
            helper.assertTrue(first.experience() > 0, "The adventurers learned nothing");
        } finally {
            end(helper, kingdom, from, to, first, second);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_fallen")
    public static void anAdventurerWhoFallsOnAnExpeditionGoesToTheChronicle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lost Patrol", UUID.randomUUID());
        Citizen first = adventurer(helper, kingdom, 1);
        Citizen second = adventurer(helper, kingdom, 2);
        GuildRules.Builder deadly = GuildSettings.configured();
        deadly.riskMultiplier = 1.0;
        deadly.expeditionDeath = 1.0;
        GuildSettings.override(deadly.build());
        // Every roll is zero: the expedition ends, and against an even fight every member's death roll falls.
        GuildWork.overrideRolls(() -> 0.0);
        try {
            KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), 1_000, 0);
            GuildActions.applyTab(server, null, new GuildPackets.TabChange(kingdom.id(), GuildPackets.TabChange.Action.FORM_SQUAD, 0, 0,
                    List.of(first.id(), second.id())));
            Squad squad = GuildWork.state(server).squadOf(first.id()).orElseThrow();
            Contract contract = GuildWork.state(server).post(null, new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(),
                    Contract.Kind.HUNT, "", Rank.E, 3, 50, Rank.E, 2, true), KingdomWorldApi.gameTime(server), TreasuryActions.day(server),
                    GuildSettings.rules(), KingdomWorldApi.state(server)).contract().orElseThrow();
            GuildWork.send(server, null, squad.id(), contract.id());
            GuildWork.bringBackAll(server);
            List<DeathRecord> chronicle = KingdomWorldApi.state(server).citizens().registry().chronicle(kingdom.id());
            helper.assertTrue(chronicle.stream().anyMatch(record -> record.cause() == CauseOfDeath.EXPEDITION),
                    "No death on an expedition in the chronicle: " + chronicle);
            helper.assertTrue(KingdomWorldApi.state(server).citizens().get(first.id()).isEmpty(), "The fallen adventurer is still alive");
            helper.assertTrue(GuildWork.state(server).hireling(first.id()).isEmpty(), "The guild still counts the fallen");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- rights and data

    @GameTest(template = EMPTY, batch = "kingdomworld_guild_rights")
    public static void theServerChecksTheRightToManageTheGuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Guild Council", UUID.randomUUID());
        ServerPlayer subject = player(helper, "kw-subject");
        ServerPlayer marshal = player(helper, "kw-marshal");
        join(server, kingdom, subject.getUUID(), Role.SUBJECT);
        join(server, kingdom, marshal.getUUID(), Role.MARSHAL);
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        try {
            KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), 1_000, 0);
            helper.assertTrue(KingdomWorldApi.state(server).membership().hasPermission(marshal.getUUID(), kingdom.id(), Permission.MANAGE_GUILD),
                    "The marshal should manage the guild");
            GuildPackets.TabChange commission = new GuildPackets.TabChange(kingdom.id(), GuildPackets.TabChange.Action.SET_COMMISSION, 150, 0,
                    List.of());
            KingdomActions.Feedback refused = GuildActions.applyTab(server, subject.getUUID(), commission);
            helper.assertTrue(!refused.success(), "A subject set the commission");
            helper.assertTrue(GuildActions.applyTab(server, marshal.getUUID(), commission).success(), "The marshal could not set the commission");
            helper.assertTrue(GuildWork.state(server).branch(kingdom.id()).orElseThrow().commissionPermille() == 150, "The commission did not change");
            helper.assertTrue(!GuildActions.apply(subject, kingdom, post(desk, Contract.Kind.HUNT, "", 3, 30, true)).success(),
                    "A subject posted a contract paid by the kingdom");
            helper.assertTrue(KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().funds() == 1_000,
                    "The refused contract took money");
            helper.assertTrue(GuildActions.tabView(server, kingdom.id(), UUID.randomUUID()).isEmpty(), "An outsider saw the guild tab");
            helper.assertTrue(!GuildActions.tabView(server, kingdom.id(), subject.getUUID()).orElseThrow().canManage(),
                    "The tab lets a subject manage the guild");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theGuildSurvivesSavingAndNewerDataStaysReadOnly(GameTestHelper helper) {
        GuildSavedData data = new GuildSavedData();
        GuildState guild = data.state();
        UUID player = UUID.randomUUID();
        UUID kingdom = UUID.randomUUID();
        UUID citizen = UUID.randomUUID();
        guild.setRank(player, Rank.C, 5);
        guild.addPoints(player, 321);
        guild.restoreBranch(new io.github.sekerator.kingdomworld.core.guild.Branch(kingdom, 120, 75, 3, 20, 400, Rank.A, 42));
        guild.restoreHireling(new io.github.sekerator.kingdomworld.core.guild.Hireling(citizen, kingdom, Rank.D, 150, 9, 2));
        guild.restoreContract(new Contract(7, Contract.Kind.HUNT, kingdom, Contract.Poster.PLAYER, player, Rank.D, "minecraft:zombie", Rank.E,
                false, 5, 2, kingdom, 500, 10, 100, 10, 90_000, Contract.Status.TAKEN, null, 3, false));
        guild.restoreSquad(new Squad(3, kingdom, List.of(citizen), Squad.State.AWAY, 7, 100, 9_000, 42.5));

        CompoundTag saved = data.save(new CompoundTag());
        GuildSavedData reloaded = GuildSavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved guild data could not be read back: " + reloaded.readOnlyReason());
        GuildState back = reloaded.state();
        helper.assertTrue(back.member(player).equals(guild.member(player)), "The member changed");
        helper.assertTrue(back.contract(7).equals(guild.contract(7)), "The contract changed: " + back.contract(7));
        helper.assertTrue(back.squad(3).equals(guild.squad(3)), "The squad changed");
        helper.assertTrue(back.branch(kingdom).equals(guild.branch(kingdom)), "The branch changed");
        helper.assertTrue(back.branch(kingdom).orElseThrow().rank() == Rank.A && back.branch(kingdom).orElseThrow().done() == 42,
                "The rank of the branch changed");
        // A branch saved before branches had ranks is of the first rank, with nothing finished.
        CompoundTag old = saved.copy();
        for (Tag entry : old.getList("branches", Tag.TAG_COMPOUND)) {
            ((CompoundTag) entry).remove("rank");
            ((CompoundTag) entry).remove("done");
        }
        GuildSavedData before = GuildSavedData.load(old);
        helper.assertTrue(!before.isReadOnly() && before.state().branch(kingdom).orElseThrow().rank()
                == io.github.sekerator.kingdomworld.core.guild.Branch.FIRST_RANK && before.state().branch(kingdom).orElseThrow().done() == 0,
                "An old branch was not read with the first rank");
        helper.assertTrue(back.hireling(citizen).equals(guild.hireling(citizen)), "The adventurer changed");
        helper.assertTrue(back.held(kingdom) == 500, "The escrow changed");
        helper.assertTrue(back.isAway(citizen), "The squad came home while saved");

        CompoundTag newer = saved.copy();
        newer.putInt("formatVersion", GuildSavedData.FORMAT_VERSION + 1);
        newer.putString("futureField", "kept");
        GuildSavedData locked = GuildSavedData.load(newer);
        helper.assertTrue(locked.isReadOnly(), "Newer guild data was not kept read-only");
        helper.assertTrue("kept".equals(locked.save(new CompoundTag()).getString("futureField")), "Read-only data was not kept as it was");
        CompoundTag broken = saved.copy();
        broken.getList("contracts", Tag.TAG_COMPOUND).getCompound(0).putString("kind", "dragonslaying");
        helper.assertTrue(GuildSavedData.load(broken).isReadOnly(), "An unreadable contract did not keep the data read-only");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMarshalGetsTheRightToManageTheGuildOnUpgrade(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        UUID king = UUID.randomUUID();
        UUID marshal = UUID.randomUUID();
        Kingdom kingdom = ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) data.state().found(UUID.randomUUID(),
                "Upgraded Guild Realm", 0x123456, king, new io.github.sekerator.kingdomworld.core.territory.Capital("minecraft:overworld", 300, 64,
                        300), 1L, io.github.sekerator.kingdomworld.core.state.KingdomRules.DEFAULT)).kingdom();
        data.state().membership().invite(king, marshal, 2L);
        data.state().membership().acceptInvitation(marshal, kingdom.id(), 3L);
        data.state().membership().setRole(king, marshal, Role.MARSHAL);
        CompoundTag saved = data.save(new CompoundTag());
        // Data of the release before the guild: no list of upgrades, and no marshal with the right.
        saved.remove("upgrades");
        CompoundTag rights = saved.getList("kingdoms", Tag.TAG_COMPOUND).getCompound(0).getCompound("permissions");
        ListTag marshalRights = rights.getList("marshal", Tag.TAG_STRING);
        for (int i = marshalRights.size() - 1; i >= 0; i--) {
            if (marshalRights.getString(i).equals("manage_guild")) {
                marshalRights.remove(i);
            }
        }
        helper.assertTrue(!marshalRights.contains(StringTag.valueOf("manage_guild")), "The right is still there");
        KingdomSavedData upgraded = KingdomSavedData.load(saved);
        helper.assertTrue(!upgraded.isReadOnly(), "Upgraded data is read-only: " + upgraded.readOnlyReason());
        helper.assertTrue(upgraded.state().membership().hasPermission(marshal, kingdom.id(), Permission.MANAGE_GUILD),
                "The marshal did not get the right to manage the guild");
        helper.assertTrue(upgraded.isDirty(), "The upgraded data is not saved");
        CompoundTag again = upgraded.save(new CompoundTag());
        helper.assertTrue(again.getList("upgrades", Tag.TAG_STRING).contains(StringTag.valueOf("guild_rights")), "The upgrade was not noted");
        // A king who took the right away again keeps it away.
        CompoundTag removed = again.copy();
        ListTag kept = removed.getList("kingdoms", Tag.TAG_COMPOUND).getCompound(0).getCompound("permissions").getList("marshal", Tag.TAG_STRING);
        for (int i = kept.size() - 1; i >= 0; i--) {
            if (kept.getString(i).equals("manage_guild")) {
                kept.remove(i);
            }
        }
        helper.assertTrue(!KingdomSavedData.load(removed).state().membership().hasPermission(marshal, kingdom.id(), Permission.MANAGE_GUILD),
                "An upgrade ran twice");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void guildPacketsSurviveEncoding(GameTestHelper helper) {
        GuildPackets.DeskAction action = new GuildPackets.DeskAction(new BlockPos(1, 2, 3), GuildPackets.DeskAction.Action.POST, 9,
                Contract.Kind.GATHER, "minecraft:bone", 3, 32, 999, 2, 4, true, false);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        action.encode(buf);
        helper.assertTrue(GuildPackets.DeskAction.decode(buf).equals(action), "The desk action changed on the way");
        GuildPackets.TabChange change = new GuildPackets.TabChange(UUID.randomUUID(), GuildPackets.TabChange.Action.FORM_SQUAD, 1, 2,
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        change.encode(buf);
        helper.assertTrue(GuildPackets.TabChange.decode(buf).equals(change), "The tab change changed on the way");
        helper.assertTrue(KingdomError.valueOf("BRANCH_CLOSED").id().equals("branch_closed"), "Errors of the guild are missing");
        helper.succeed();
    }
}
