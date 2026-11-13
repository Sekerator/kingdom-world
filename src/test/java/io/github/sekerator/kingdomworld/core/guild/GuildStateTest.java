package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;
    private static final GuildRules RULES = GuildRules.DEFAULT;

    private KingdomWorldState world;
    private GuildState guild;
    private Economy economy;
    private Kingdom kingdom;
    private UUID king;
    private UUID marshal;
    private UUID subject;
    private UUID hunter;
    private long now;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        guild = new GuildState();
        economy = world.economy();
        economy.setHeldElsewhere(guild::held);
        king = UUID.randomUUID();
        marshal = UUID.randomUUID();
        subject = UUID.randomUUID();
        hunter = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Guild Realm", 0x336699, king, new Capital(OVERWORLD, 8, 64, 8), 0,
                KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        for (UUID player : new UUID[]{marshal, subject}) {
            world.membership().invite(king, player, 1L);
            world.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        world.membership().setRole(king, marshal, Role.MARSHAL);
        economy.adminTreasury(kingdom.id(), 10_000, 0);
        now = 2 * DAY;
    }

    private long money() {
        return economy.supply(kingdom.id());
    }

    private GuildState.Posting hunt(GuildRules.Builder unused, Contract.Poster poster, UUID posterId, String target, Rank rank, int count,
                                    long reward) {
        return new GuildState.Posting(kingdom.id(), poster, posterId, Contract.Kind.HUNT, target, rank, count, reward, rank, 2, true);
    }

    private Contract postByKingdom(String target, Rank rank, int count, long reward) {
        GuildState.Made made = guild.post(king, hunt(null, Contract.Poster.KINGDOM, kingdom.id(), target, rank, count, reward), now, 2, RULES,
                world);
        assertTrue(made.outcome().ok(), "posting failed: " + made.outcome());
        return made.contract().orElseThrow();
    }

    @Test
    void theMarshalManagesTheGuildByDefault() {
        assertTrue(RolePermissions.defaults().has(Role.MARSHAL, Permission.MANAGE_GUILD));
        assertTrue(RolePermissions.defaults().has(Role.KING, Permission.MANAGE_GUILD));
        assertFalse(RolePermissions.defaults().has(Role.STEWARD, Permission.MANAGE_GUILD));
        assertFalse(RolePermissions.defaults().has(Role.SUBJECT, Permission.MANAGE_GUILD));
    }

    @Test
    void joiningGivesRankFAndPaysTheFee() {
        GuildRules.Builder builder = new GuildRules.Builder();
        builder.joinFee = 25;
        GuildRules rules = builder.build();
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), guild.join(hunter, kingdom.id(), now, 2, rules, world).failure());
        economy.adminAccount(hunter, kingdom.id(), 30);
        long before = money();
        assertTrue(guild.join(hunter, kingdom.id(), now, 2, rules, world).ok());
        assertEquals(Rank.F, guild.member(hunter).orElseThrow().rank());
        assertEquals(5, economy.accounts().balance(hunter, kingdom.id()));
        assertEquals(10_025, economy.treasury(kingdom.id()).orElseThrow().funds());
        assertEquals(before, money(), "the fee only moves money");
        assertEquals(Optional.of(KingdomError.ALREADY_IN_GUILD), guild.join(hunter, kingdom.id(), now, 2, rules, world).failure());
        assertEquals(25, economy.treasury(kingdom.id()).orElseThrow().journal()
                .totals(2, io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.TODAY).get(LedgerCategory.GUILD).income());
    }

    @Test
    void aHuntPaysFromEscrowWithTheCommissionForTheBranch() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        long before = money();
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), guild.post(subject, hunt(null, Contract.Poster.KINGDOM, kingdom.id(),
                "minecraft:zombie", Rank.E, 3, 100), now, 2, RULES, world).outcome().failure(), "a subject may not post for the kingdom");
        Contract contract = postByKingdom("minecraft:zombie", Rank.E, 3, 100);
        assertTrue(guild.post(marshal, hunt(null, Contract.Poster.KINGDOM, kingdom.id(), "minecraft:skeleton", Rank.E, 1, 10), now, 2, RULES,
                world).outcome().ok(), "the marshal may");
        assertEquals(9_890, economy.treasury(kingdom.id()).orElseThrow().funds(), "the rewards left the treasury");
        assertEquals(before, money(), "the money in escrow still counts");
        assertTrue(guild.take(hunter, contract.id(), now, RULES).ok());
        assertTrue(guild.recordKill(hunter, "minecraft:skeleton", Rank.E, false, now, 2, world).isEmpty(), "a skeleton is no zombie");
        guild.recordKill(hunter, "minecraft:zombie", Rank.E, false, now, 2, world);
        guild.recordKill(hunter, "minecraft:zombie", Rank.E, false, now, 2, world);
        assertEquals(2, guild.contract(contract.id()).orElseThrow().progress());
        List<GuildState.Progress> done = guild.recordKill(hunter, "minecraft:zombie", Rank.E, false, now, 2, world);
        assertTrue(done.get(0).finished());
        assertEquals(90, done.get(0).paid(), "ten percent commission");
        assertEquals(90, economy.accounts().balance(hunter, kingdom.id()));
        assertEquals(9_900, economy.treasury(kingdom.id()).orElseThrow().funds(), "the commission came back to the branch");
        assertTrue(guild.contract(contract.id()).isEmpty());
        assertEquals(contract.points(), guild.member(hunter).orElseThrow().points());
        assertEquals(before, money(), "nothing was made or lost");
    }

    @Test
    void contractsOfTheOwnRankAndOneAbove() {
        // The branch takes contracts up to its own rank: one of rank B takes the hunts of rank D posted here.
        guild.setBranchRank(kingdom.id(), Rank.B, RULES);
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        Contract e = postByKingdom("", Rank.E, 1, 10);
        Contract d = postByKingdom("", Rank.D, 1, 10);
        assertEquals(Optional.of(KingdomError.RANK_TOO_LOW), guild.take(hunter, d.id(), now, RULES).failure());
        assertTrue(guild.take(hunter, e.id(), now, RULES).ok());
        assertEquals(Optional.of(KingdomError.CONTRACT_TAKEN), guild.take(hunter, e.id(), now, RULES).failure());
        assertEquals(Optional.of(KingdomError.NOT_IN_GUILD), guild.take(subject, d.id(), now, RULES).failure());
        // A hunt by rank counts every monster of the rank and above.
        assertEquals(1, guild.recordKill(hunter, "modded:beast", Rank.B, false, now, 2, world).size());
    }

    @Test
    void theLimitOfContractsHolds() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        for (int i = 0; i < RULES.maxActiveContracts; i++) {
            assertTrue(guild.take(hunter, postByKingdom("", Rank.F, 5, 5).id(), now, RULES).ok());
        }
        assertEquals(Optional.of(KingdomError.TOO_MANY_CONTRACTS), guild.take(hunter, postByKingdom("", Rank.F, 5, 5).id(), now,
                RULES).failure());
    }

    @Test
    void anExpiredContractGivesTheRewardBackAndCostsPoints() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        guild.addPoints(hunter, 100);
        long before = money();
        Contract contract = postByKingdom("minecraft:zombie", Rank.E, 5, 300);
        guild.take(hunter, contract.id(), now, RULES);
        assertTrue(guild.expire(now + DAY, 3, RULES, world).isEmpty(), "two days to go");
        List<Contract> expired = guild.expire(contract.deadline(), 4, RULES, world);
        assertEquals(1, expired.size());
        assertEquals(Contract.Status.EXPIRED, expired.get(0).status());
        assertEquals(10_000, economy.treasury(kingdom.id()).orElseThrow().funds(), "the reward came back");
        assertEquals(100 - GuildState.penalty(contract, RULES), guild.member(hunter).orElseThrow().points());
        assertEquals(before, money());
    }

    @Test
    void callingOffGivesTheRewardBackOnlyWhileNobodyHoldsIt() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        economy.adminAccount(subject, kingdom.id(), 1_000);
        long before = money();
        GuildState.Made made = guild.post(subject, hunt(null, Contract.Poster.PLAYER, subject, "minecraft:spider", Rank.E, 2, 500), now, 2,
                RULES, world);
        assertTrue(made.outcome().ok());
        long fee = GuildState.postingFee(500, RULES);
        assertEquals(10, fee);
        assertEquals(1_000 - 500 - fee, economy.accounts().balance(subject, kingdom.id()));
        assertEquals(10_000 + fee, economy.treasury(kingdom.id()).orElseThrow().funds(), "the fee went to the branch");
        Contract contract = made.contract().orElseThrow();
        assertEquals(Optional.of(KingdomError.NOT_YOUR_CONTRACT), guild.checkCancel(hunter, contract.id(), world).failure());
        guild.take(hunter, contract.id(), now, RULES);
        assertEquals(Optional.of(KingdomError.CONTRACT_TAKEN), guild.checkCancel(subject, contract.id(), world).failure());
        assertEquals(GuildState.penalty(contract, RULES), guild.abandon(hunter, contract.id(), now, RULES), "giving up costs points");
        assertTrue(guild.contract(contract.id()).orElseThrow().isOpen(), "the contract is back on the board");
        assertTrue(guild.cancel(subject, contract.id(), 2, world).isPresent());
        assertEquals(1_000 - fee, economy.accounts().balance(subject, kingdom.id()), "the reward came back, the fee did not");
        assertEquals(before, money());
    }

    @Test
    void aPlayerCannotTakeTheOwnContract() {
        guild.join(subject, kingdom.id(), now, 2, RULES, world);
        economy.adminAccount(subject, kingdom.id(), 1_000);
        Contract contract = guild.post(subject, hunt(null, Contract.Poster.PLAYER, subject, "", Rank.F, 2, 100), now, 2, RULES, world)
                .contract().orElseThrow();
        assertEquals(Optional.of(KingdomError.TARGET_IS_SELF), guild.take(subject, contract.id(), now, RULES).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), guild.post(hunter, hunt(null, Contract.Poster.PLAYER, subject, "", Rank.F, 1, 1),
                now, 2, RULES, world).outcome().failure(), "nobody posts on another's account");
    }

    @Test
    void theGuildPostsWithinTheDailyBudget() {
        GuildState.Posting posting = new GuildState.Posting(kingdom.id(), Contract.Poster.GUILD, kingdom.id(), Contract.Kind.HUNT,
                "minecraft:zombie", Rank.E, 5, 30, Rank.E, 1, true);
        assertTrue(guild.post(null, posting, now, 2, RULES, world).outcome().ok());
        assertEquals(Optional.of(KingdomError.BUDGET_SPENT), guild.post(null, posting, now, 2, RULES, world).outcome().failure(),
                "the default budget is 50 a day");
        assertTrue(guild.post(null, posting, now + DAY, 3, RULES, world).outcome().ok(), "a new day, a new budget");
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), guild.setBudget(subject, kingdom.id(), 500, RULES, world).failure());
        assertTrue(guild.setBudget(marshal, kingdom.id(), 500, RULES, world).ok());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), guild.setCommission(king, kingdom.id(), 900, RULES, world).failure());
        assertTrue(guild.setCommission(king, kingdom.id(), 250, RULES, world).ok());
        assertEquals(2, guild.autoContracts(kingdom.id()));
    }

    @Test
    void handingInItemsFinishesAGatheringContract() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        Contract contract = guild.post(king, new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(), Contract.Kind.GATHER,
                "minecraft:bone", Rank.F, 32, 64, Rank.F, 1, true), now, 2, RULES, world).contract().orElseThrow();
        assertEquals(Optional.of(KingdomError.NOT_YOUR_CONTRACT), guild.checkHandIn(hunter, contract.id(), 32, now).failure());
        guild.take(hunter, contract.id(), now, RULES);
        assertEquals(Optional.of(KingdomError.NO_ITEMS), guild.checkHandIn(hunter, contract.id(), 31, now).failure());
        assertTrue(guild.handIn(hunter, contract.id(), 31, now, 2, world).isEmpty(), "too few items change nothing");
        GuildState.Progress done = guild.handIn(hunter, contract.id(), 32, now, 2, world).orElseThrow();
        assertTrue(done.finished());
        assertEquals(58, economy.accounts().balance(hunter, kingdom.id()), "64 less ten percent, rounded for the guild");
    }

    @Test
    void itemsHandedInBeforeAContractEndsUnfinishedArePaidFor() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        GuildState.Sharing party = new GuildState.Sharing() {
            @Override
            public Set<Long> sharedWith(UUID player) {
                return Set.of();
            }

            @Override
            public List<RewardSplit.Share> shares(Contract contract) {
                return List.of(new RewardSplit.Share(hunter, 1));
            }
        };
        long before = money();
        Contract given = guild.post(king, new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(), Contract.Kind.GATHER,
                "minecraft:bone", Rank.F, 40, 80, Rank.F, 1, true), now, 2, RULES, world).contract().orElseThrow();
        guild.take(hunter, given.id(), now, RULES);
        guild.handIn(hunter, given.id(), 10, now, 2, world, party).orElseThrow();
        guild.abandon(hunter, given.id(), now, RULES, world);
        assertEquals(20, economy.accounts().balance(hunter, kingdom.id()), "a quarter of the items, a quarter of the reward");
        Contract rest = guild.contract(given.id()).orElseThrow();
        assertEquals(30, rest.count(), "the rest of the contract is back on the board");
        assertEquals(0, rest.progress());
        assertEquals(60, rest.reward());

        guild.take(hunter, rest.id(), now, RULES);
        guild.handIn(hunter, rest.id(), 15, now, 2, world, party).orElseThrow();
        guild.expire(rest.deadline(), 4, RULES, world);
        assertEquals(50, economy.accounts().balance(hunter, kingdom.id()), "half of what was left paid on expiry");
        assertEquals(before, money(), "no money made or lost");
    }

    @Test
    void anExamNeedsPointsAndRaisesTheRank() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        assertEquals(Optional.of(KingdomError.TOO_FEW_POINTS), guild.startExam(hunter, kingdom.id(), now, RULES).outcome().failure());
        guild.addPoints(hunter, RULES.rankPoints(Rank.E));
        Contract exam = guild.startExam(hunter, kingdom.id(), now, RULES).contract().orElseThrow();
        assertEquals(Rank.E, exam.targetRank());
        assertEquals(RULES.examKills(Rank.E), exam.count());
        assertEquals(Optional.of(KingdomError.EXAM_UNDER_WAY), guild.checkExam(hunter, now, RULES).failure());
        guild.recordKill(hunter, "minecraft:silverfish", Rank.F, false, now, 2, world);
        assertEquals(0, guild.exam(hunter).orElseThrow().progress(), "monsters below the rank do not count");
        for (int i = 0; i < exam.count(); i++) {
            guild.recordKill(hunter, "minecraft:zombie", Rank.E, false, now, 2, world);
        }
        assertEquals(Rank.E, guild.member(hunter).orElseThrow().rank());
        assertTrue(guild.exam(hunter).isEmpty());
    }

    @Test
    void aFailedExamWaitsForTheNextTry() {
        // A branch of rank SS examines up to SS.
        guild.setBranchRank(kingdom.id(), Rank.SS, RULES);
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        guild.addPoints(hunter, RULES.rankPoints(Rank.E));
        Contract exam = guild.startExam(hunter, kingdom.id(), now, RULES).contract().orElseThrow();
        guild.expire(exam.deadline(), 5, RULES, world);
        assertEquals(Rank.F, guild.member(hunter).orElseThrow().rank());
        assertEquals(Optional.of(KingdomError.EXAM_COOLDOWN), guild.checkExam(hunter, exam.deadline() + 1, RULES).failure());
        assertTrue(guild.checkExam(hunter, exam.deadline() + RULES.examCooldownTicks, RULES).ok());
        guild.setRank(hunter, Rank.A, now);
        guild.addPoints(hunter, RULES.rankPoints(Rank.S));
        Contract bossExam = guild.startExam(hunter, kingdom.id(), exam.deadline() + RULES.examCooldownTicks, RULES).contract().orElseThrow();
        assertTrue(bossExam.boss(), "the exam for S asks for a boss");
        assertTrue(guild.recordKill(hunter, "modded:giant", Rank.SS, false, now, 2, world).isEmpty(), "a giant is no boss");
        guild.setRank(hunter, Rank.SSS, now);
        guild.abandon(hunter, bossExam.id(), now, RULES);
        assertEquals(Optional.of(KingdomError.TOP_RANK), guild.checkExam(hunter, now + 10 * DAY, RULES).failure());
    }

    private Citizen adventurer() {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 7, Gender.FEMALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        world.citizens().add(citizen);
        world.setProfession(null, citizen.id(), Professions.ADVENTURER.id());
        return citizen;
    }

    @Test
    void squadsGoOnExpeditionsAndBringTheRewardHome() {
        guild.setBranchRank(kingdom.id(), Rank.B, RULES);
        List<UUID> members = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            members.add(adventurer().id());
        }
        assertEquals(Optional.of(KingdomError.SQUAD_SIZE), guild.formSquad(king, kingdom.id(), members.subList(0, 1), RULES, world).outcome()
                .failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), guild.formSquad(subject, kingdom.id(), members, RULES, world).outcome().failure());
        Squad squad = guild.formSquad(marshal, kingdom.id(), members, RULES, world).squad().orElseThrow();
        assertEquals(Optional.of(KingdomError.NOT_AN_ADVENTURER), guild.formSquad(king, kingdom.id(), members, RULES, world).outcome().failure(),
                "nobody is in two squads");
        Contract hard = postByKingdom("minecraft:enderman", Rank.C, 3, 300);
        assertEquals(Optional.of(KingdomError.RANK_TOO_LOW), guild.checkSend(king, squad.id(), hard.id(), now, RULES, world).failure());
        Contract contract = postByKingdom("minecraft:zombie", Rank.E, 4, 200);
        long before = money();
        assertTrue(guild.send(king, squad.id(), contract.id(), 90, now, RULES, world).isPresent());
        assertTrue(guild.isAway(members.get(0)));
        assertEquals(Optional.of(KingdomError.SQUAD_AWAY), guild.disband(king, squad.id(), world).failure());
        assertEquals(List.of(hard.id()), guild.expire(contract.deadline(), 9, RULES, world).stream().map(Contract::id).toList(),
                "only the contract nobody took runs out");
        assertTrue(guild.contract(contract.id()).isPresent(), "a contract waits for its squad");

        Expeditions.Outcome outcome = new Expeditions.Outcome(Expeditions.Result.SUCCESS, 4, List.of());
        GuildState.Return back = guild.finishExpedition(squad.id(), outcome, Set.of(members.get(2)), now, 3, RULES, world).orElseThrow();
        assertEquals(180, back.paid(), "the reward less the commission");
        assertEquals(Contract.Status.DONE, back.contract().status());
        assertEquals(2, back.squad().members().size(), "the fallen leave the squad");
        assertFalse(guild.isAway(members.get(0)));
        assertTrue(guild.hireling(members.get(0)).orElseThrow().kills() == 4);
        assertTrue(guild.hireling(members.get(2)).isEmpty());
        assertEquals(before, money(), "the kingdom paid itself, less nothing");
    }

    @Test
    void aPartialExpeditionPutsTheContractBack() {
        List<UUID> members = List.of(adventurer().id(), adventurer().id());
        Squad squad = guild.formSquad(king, kingdom.id(), members, RULES, world).squad().orElseThrow();
        Contract contract = postByKingdom("", Rank.E, 6, 120);
        guild.send(king, squad.id(), contract.id(), 50, now, RULES, world);
        GuildState.Return back = guild.finishExpedition(squad.id(), new Expeditions.Outcome(Expeditions.Result.PARTIAL, 3, List.of()), Set.of(),
                now, 3, RULES, world).orElseThrow();
        assertTrue(back.contract().isOpen());
        assertEquals(0, back.contract().progress(), "the next taker starts from nothing");
        assertEquals(0, back.paid());
    }

    @Test
    void playersDecideWhetherSquadsMayTakeTheirContracts() {
        List<UUID> members = List.of(adventurer().id(), adventurer().id());
        Squad squad = guild.formSquad(king, kingdom.id(), members, RULES, world).squad().orElseThrow();
        economy.adminAccount(subject, kingdom.id(), 1_000);
        Contract closed = guild.post(subject, new GuildState.Posting(kingdom.id(), Contract.Poster.PLAYER, subject, Contract.Kind.HUNT, "",
                Rank.E, 2, 100, Rank.E, 2, false), now, 2, RULES, world).contract().orElseThrow();
        Contract open = guild.post(subject, new GuildState.Posting(kingdom.id(), Contract.Poster.PLAYER, subject, Contract.Kind.HUNT, "",
                Rank.E, 2, 100, Rank.E, 2, true), now, 2, RULES, world).contract().orElseThrow();
        assertEquals(Optional.of(KingdomError.SQUADS_NOT_ALLOWED), guild.checkSend(king, squad.id(), closed.id(), now, RULES, world).failure());
        assertTrue(guild.checkSend(king, squad.id(), open.id(), now, RULES, world).ok());
        assertEquals(Optional.of(KingdomError.NO_TIME), guild.checkSend(king, squad.id(), open.id(), open.deadline() - 1, RULES, world).failure());
    }

    @Test
    void aDissolvedKingdomGivesPlayersTheirRewardsBack() {
        economy.adminAccount(subject, kingdom.id(), 1_000);
        Contract contract = guild.post(subject, hunt(null, Contract.Poster.PLAYER, subject, "", Rank.F, 2, 400), now, 2, RULES, world)
                .contract().orElseThrow();
        postByKingdom("", Rank.F, 2, 400);
        List<Contract> closed = guild.forgetKingdom(kingdom.id(), 2, world);
        assertEquals(2, closed.size());
        assertTrue(guild.contract(contract.id()).isEmpty());
        assertEquals(1_000 - GuildState.postingFee(400, RULES), economy.accounts().balance(subject, kingdom.id()));
        assertEquals(0, guild.held(kingdom.id()));
    }

    @Test
    void expeditionsFavourStrongSquadsAndProtectWithArmor() {
        Contract contract = new Contract(1, Contract.Kind.HUNT, kingdom.id(), Contract.Poster.GUILD, kingdom.id(), Rank.E, "", Rank.C, false, 4,
                0, kingdom.id(), 10, 10, 100, 0, DAY, Contract.Status.OPEN, null, 0, true);
        double difficulty = Expeditions.difficulty(contract);
        assertEquals(0.5, Expeditions.successChance(difficulty, difficulty), 1e-9);
        assertTrue(Expeditions.successChance(2 * difficulty, difficulty) > 0.75);
        assertTrue(Expeditions.successChance(difficulty / 2, difficulty) < 0.25);
        Expeditions.Member bare = new Expeditions.Member(UUID.randomUUID(), Rank.F, 0, 0, 0);
        Expeditions.Member armed = new Expeditions.Member(UUID.randomUUID(), Rank.F, 7, 15, 500);
        assertTrue(Expeditions.power(armed) > 2 * Expeditions.power(bare));
        assertTrue(Expeditions.power(new Expeditions.Member(UUID.randomUUID(), Rank.C, 0, 0, 0)) > Expeditions.power(bare));
        assertTrue(Expeditions.duration(contract, RULES) > RULES.expeditionBaseMinutes * CitizenNeeds.REFERENCE_STEP_TICKS);

        Expeditions.Outcome win = Expeditions.resolve(List.of(bare, armed), difficulty * 3, contract, RULES, 0.1, new double[]{0.9, 0.9, 0.9, 0.9});
        assertEquals(Expeditions.Result.SUCCESS, win.result());
        assertEquals(4, win.kills());
        assertTrue(win.fates().stream().allMatch(fate -> fate.accident() == TaskRisks.Accident.NONE));
        Expeditions.Outcome loss = Expeditions.resolve(List.of(bare, armed), difficulty / 4, contract, RULES, 0.99, new double[]{0.0, 0.0, 0.3, 0.3});
        assertEquals(Expeditions.Result.FAILURE, loss.result());
        assertEquals(0, loss.kills());
        assertEquals(TaskRisks.Accident.DEATH, loss.fates().get(0).accident(), "a weak squad that fails loses its bare-handed member");
        GuildRules.Builder safe = new GuildRules.Builder();
        safe.riskMultiplier = 0.0;
        Expeditions.Outcome calm = Expeditions.resolve(List.of(bare), difficulty / 4, contract, safe.build(), 0.99, new double[]{0.0, 0.0});
        assertEquals(TaskRisks.Accident.NONE, calm.fates().get(0).accident(), "no risk with taskRiskMultiplier 0");
        Expeditions.Outcome partial = Expeditions.resolve(List.of(bare), difficulty, contract, RULES, 0.6, new double[]{1, 1});
        assertEquals(Expeditions.Result.PARTIAL, partial.result());
        assertTrue(partial.kills() > 0 && partial.kills() < 4);
    }

    @Test
    void aBranchExaminesUpToItsOwnRank() {
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        guild.setRank(hunter, Rank.D, now);
        guild.addPoints(hunter, RULES.rankPoints(Rank.C));
        // A new branch is of rank E: the exam for C is taken at a branch of rank C or above.
        assertEquals(Rank.E, guild.branchOrDefault(kingdom.id(), RULES).rank());
        assertEquals(Optional.of(KingdomError.BRANCH_RANK_TOO_LOW), guild.startExam(hunter, kingdom.id(), now, RULES).outcome().failure());
        assertTrue(guild.checkExam(hunter, now, RULES).ok(), "the member could take the exam at another branch");
        guild.setBranchRank(kingdom.id(), Rank.C, RULES);
        assertTrue(guild.startExam(hunter, kingdom.id(), now, RULES).outcome().ok());
    }

    @Test
    void aBranchTakesNoContractAboveItsRank() {
        assertEquals(Optional.of(KingdomError.RANK_ABOVE_BRANCH), guild.post(king, hunt(null, Contract.Poster.KINGDOM, kingdom.id(), "", Rank.D,
                1, 10), now, 2, RULES, world).outcome().failure());
        guild.setBranchRank(kingdom.id(), Rank.D, RULES);
        assertTrue(guild.post(king, hunt(null, Contract.Poster.KINGDOM, kingdom.id(), "", Rank.D, 1, 10), now, 2, RULES, world).outcome().ok());
        // The rule of the own rank and one above still holds for those who take it.
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        Contract c = guild.board(kingdom.id()).get(0);
        assertEquals(Optional.of(KingdomError.RANK_TOO_LOW), guild.take(hunter, c.id(), now, RULES).failure());
    }

    @Test
    void aBranchOfPlayersRisesWithTheContractsFinishedThereUpToItsHouse() {
        assertEquals(Rank.E, RULES.branchRankOf(0, false));
        assertEquals(Rank.D, RULES.branchRankOf(3, false));
        assertEquals(Rank.C, RULES.branchRankOf(10, false));
        assertEquals(Rank.C, RULES.branchRankOf(1_000_000, false), "no higher than C without a guild house");
        assertEquals(Rank.B, RULES.branchRankOf(1_000_000, true), "no higher than B with one");
        // A finished hunt counts for its branch.
        guild.join(hunter, kingdom.id(), now, 2, RULES, world);
        Contract contract = postByKingdom("minecraft:zombie", Rank.E, 1, 10);
        assertTrue(guild.take(hunter, contract.id(), now, RULES).ok());
        assertTrue(guild.recordKill(hunter, "minecraft:zombie", Rank.E, false, now, 2, world).get(0).finished());
        assertEquals(1, guild.branchOrDefault(kingdom.id(), RULES).done());
    }

    @Test
    void theGuildMaySpendMoreOnItsOwnContractsAtAHigherBranch() {
        Branch branch = guild.branchOrDefault(kingdom.id(), RULES);
        assertEquals(RULES.defaultDailyBudget, branch.budgetLeft(3, RULES));
        assertEquals(RULES.ownBudget(Rank.S), branch.withRank(Rank.S).budgetLeft(3, RULES));
        assertTrue(RULES.ownBudget(Rank.S) > RULES.defaultDailyBudget);
        // The budget of the king counts when it is higher.
        assertEquals(5_000, branch.withRank(Rank.S).withBudget(5_000).budgetLeft(3, RULES));
    }
}
