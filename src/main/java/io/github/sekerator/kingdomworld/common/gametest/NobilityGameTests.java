package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.nobility.NobilityActions;
import io.github.sekerator.kingdomworld.common.nobility.NobilitySavedData;
import io.github.sekerator.kingdomworld.common.nobility.NobilitySettings;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Stability;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The nobility in the world: a title makes a player noble with the share of the fief but no hand in the treasury; a demand
 * and its answer move the stability; a plot overthrows a ruler of the world; a plot against a king of players takes a city
 * into a kingdom of its own without losing or doubling anything; a spy takes from a foreign treasury by the books; a caught
 * spy spoils the relations; and the nobility outlives a save. Every test fences what it builds outside its cleared box.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NobilityGameTests {
    private static final String EMPTY = "empty";

    private NobilityGameTests() {
    }

    private static long day(MinecraftServer server) {
        return NobilityWork.day(server);
    }

    private static void fund(MinecraftServer server, UUID kingdomId, long amount) {
        KingdomWorldApi.state(server).economy().adminTreasury(kingdomId, amount, day(server));
    }

    private static void finish(GameTestHelper helper, Kingdom home, List<Kingdom> others, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        NobilityWork.overrideRolls(null);
        NobilitySettings.override(null);
        NobilityState state = NobilityWork.state(server);
        state.forget(home.id());
        for (Kingdom other : others) {
            state.forget(other.id());
            WarWork.markPresent(other.id(), null);
            WarWork.state(server).forget(other.id(), KingdomWorldApi.gameTime(server));
            NpcWork.state(server).removeKingdom(other.id());
            KingdomWorldApi.dissolveKingdom(server, other.id());
        }
        WarWork.markPresent(home.id(), null);
        WarWork.state(server).forget(home.id(), KingdomWorldApi.gameTime(server));
        CitizenGameTests.finish(helper, home, citizens);
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
    }

    // ---------------------------------------------------------------- titles

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_title", timeoutTicks = 100)
    public static void aTitleMakesAPlayerNobleWithTheShareOfItsFiefButNoTreasury(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
        Kingdom home = CitizenGameTests.found(helper, "GameTest Noble Title", king);
        WarGameTests.join(server, home, subject);
        Citizen citizen = CitizenGameTests.newcomer(helper, home, 4, 4);
        Map<Fief, Integer> people = NobilityWork.people(server, home.id());
        Fief fief = people.keySet().stream().findFirst().orElse(Fief.march(0));
        helper.assertFalse(LawWork.policy().isNoble(home.id(), subject), "A subject was noble before any title");
        helper.assertTrue(NobilityActions.grant(server, subject, home.id(), Title.BARON, fief, 0, subject).error() == KingdomError.NO_PERMISSION,
                "A subject granted a title");
        Outcome granted = NobilityActions.grant(server, king, home.id(), Title.BARON, fief, 0, subject);
        helper.assertTrue(granted.ok(), "The king could not make a subject a baron: " + granted);
        helper.assertTrue(LawWork.policy().isNoble(home.id(), subject), "A baron is not noble for the law");
        helper.assertFalse(KingdomWorldApi.state(server).membership().hasPermission(subject, home.id(), Permission.MANAGE_TREASURY),
                "A baron got a hand in the treasury");
        // The income of yesterday: taxes a subject paid.
        Economy economy = KingdomWorldApi.state(server).economy();
        long today = day(server);
        UUID payer = UUID.randomUUID();
        economy.adminAccount(payer, home.id(), 1_000);
        helper.assertTrue(economy.payTreasury(payer, home.id(), 1_000, LedgerCategory.TAXES, today - 1).ok(), "The taxes of yesterday were not paid");
        long treasury = economy.treasury(home.id()).orElseThrow().funds();
        long account = economy.accounts().balance(subject, home.id());
        int all = people.values().stream().mapToInt(Integer::intValue).sum();
        long share = NobilityWork.fiefShare(1_000, people.getOrDefault(fief, 0), Math.max(1, all), NobilitySettings.rules().shareBaron());
        helper.assertTrue(share > 0, "The fief has nobody to pay a share");
        NobilityWork.shares(server, home.id(), today);
        helper.assertTrue(economy.accounts().balance(subject, home.id()) == account + share,
                "The baron got " + (economy.accounts().balance(subject, home.id()) - account) + " instead of " + share);
        helper.assertTrue(economy.treasury(home.id()).orElseThrow().funds() == treasury - share, "The share did not come from the treasury");
        TreasuryJournal.Totals books = economy.treasury(home.id()).orElseThrow().journal().days().get(today).get(LedgerCategory.NOBILITY);
        helper.assertTrue(books != null && books.expense() == share, "The share is not in the books");
        finish(helper, home, List.of(), citizen);
        helper.succeed();
    }

    // ---------------------------------------------------------------- demands

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_demand", timeoutTicks = 100)
    public static void aDemandAndItsAnswerMoveTheStability(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
        Kingdom home = CitizenGameTests.found(helper, "GameTest Noble Demand", king);
        WarGameTests.join(server, home, subject);
        NobilityState state = NobilityWork.state(server);
        NobleHouse house = state.found(home.id(), 77, day(server));
        fund(server, home.id(), 1_000);
        Stability.Inputs world = Stability.Inputs.NEUTRAL;
        int before = state.stability(home.id(), world).value();
        Demand first = state.adminDemand(house, Demand.Kind.BUILD, null, 100, 2);
        helper.assertTrue(NobilityActions.answer(server, subject, home.id(), first.id(), Demand.Answer.REFUSE).error() == KingdomError.NO_PERMISSION,
                "A subject answered for the crown");
        helper.assertTrue(NobilityActions.answer(server, king, home.id(), first.id(), Demand.Answer.REFUSE).ok(), "The king could not refuse");
        int refused = state.stability(home.id(), world).value();
        helper.assertTrue(refused < before, "A refusal did not shake the kingdom: " + before + " -> " + refused);
        long funds = WarWork.funds(server, home.id());
        Demand second = state.adminDemand(house, Demand.Kind.BUILD, null, 100, 2);
        helper.assertTrue(NobilityActions.answer(server, king, home.id(), second.id(), Demand.Answer.ACCEPT).ok(), "The king could not agree");
        helper.assertTrue(WarWork.funds(server, home.id()) == funds - 100, "The works were not paid from the treasury");
        int accepted = state.stability(home.id(), world).value();
        helper.assertTrue(accepted > refused, "Agreeing did not steady the kingdom: " + refused + " -> " + accepted);
        helper.assertTrue(state.openDemand(home.id()).isEmpty(), "An answered demand is still open");
        finish(helper, home, List.of());
        helper.succeed();
    }

    // ---------------------------------------------------------------- plots

    /**
     * A kingdom of the world without land, for the court alone.
     */
    private static Kingdom worldKingdom(MinecraftServer server, String name, RulerCharacter.Trait trait) {
        for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
            if (stale.name().equals(name)) {
                NobilityWork.state(server).forget(stale.id());
                NpcWork.state(server).removeKingdom(stale.id());
                KingdomWorldApi.dissolveKingdom(server, stale.id());
            }
        }
        UUID id = UUID.randomUUID();
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, name, null);
        if (!(created instanceof CreateKingdomResult.Created result)) {
            throw new IllegalStateException("The kingdom was not created: " + created);
        }
        Kingdom kingdom = result.kingdom();
        NpcWork.state(server).putKingdom(new NpcKingdom(kingdom.id(), -1, NpcLevel.SMALL, new NpcNames.Name(name, name), RulerCharacter.BUILDER,
                List.of(trait), 1234, Gender.MALE, List.of(), KingdomWorldApi.gameTime(server), day(server)));
        return kingdom;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_overthrow", timeoutTicks = 100)
    public static void aPlotAgainstARulerOfTheWorldChangesTheRuler(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom home = CitizenGameTests.found(helper, "GameTest Noble Overthrow", king);
        Kingdom world = worldKingdom(server, "GameTest World Court", RulerCharacter.Trait.PATIENT);
        NobilityState state = NobilityWork.state(server);
        NobleHouse house = state.found(world.id(), 99, day(server));
        state.adminLoyalty(house, 5);
        state.adminPlot(house, NobilitySettings.rules().plotDays() - 1, NobilitySettings.rules().plotDays(), null, day(server));
        state.adminReplay(world.id());
        NobilityWork.overrideRolls(() -> 0.99);
        NobilityWork.kingdomDay(server, world.id(), day(server));
        NpcKingdom ruler = NpcWork.kingdom(server, world.id()).orElseThrow();
        helper.assertTrue(ruler.rulerSeed() == house.headSeed() && ruler.character() == house.character(),
                "The head of the plotting house does not rule: seed " + ruler.rulerSeed());
        helper.assertTrue(KingdomWorldApi.kingdom(server, world.id()).isPresent(), "The kingdom did not stay the same");
        helper.assertTrue(state.court(world.id()).chronicle().stream().anyMatch(note -> note.key().equals("overthrown")),
                "The overthrow is not in the chronicle");
        finish(helper, home, List.of(world));
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_secession", timeoutTicks = 400)
    public static void aPlotAgainstAKingOfPlayersTakesACityIntoAKingdomOfItsOwn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
        Kingdom home = CitizenGameTests.found(helper, "GameTest Noble Secession", king);
        // The city stands in the chunk east of the throne, fenced: the new throne goes there.
        BlockPos hall = helper.absolutePos(new BlockPos(2 + 16, 0, 2));
        BlockPos fenceFrom = hall.offset(-2, -1, -2);
        BlockPos fenceTo = hall.offset(2, 3, 2);
        GatheringGameTests.enclose(helper.getLevel(), fenceFrom, fenceTo);
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(helper.getLevel());
        City city = RoadWork.state(server).addCity(home.id(), "GameTest Breakaway", dimension, hall.getX(), hall.getY(), hall.getZ(),
                KingdomWorldApi.gameTime(server));
        Citizen there = CitizenLife.arrive(server, home.id(), new WorldPos(dimension, hall.getX() + 1, hall.getY(), hall.getZ() + 1), false).orElseThrow();
        Citizen here = CitizenGameTests.newcomer(helper, home, 3, 3);
        NobilityState state = NobilityWork.state(server);
        NobleHouse house = state.found(home.id(), 55, day(server));
        helper.assertTrue(NobilityActions.grant(server, king, home.id(), Title.COUNT, Fief.city(city.id()), house.id(), null).ok(), "No count of the city");
        fund(server, home.id(), 500);
        long money = KingdomWorldApi.state(server).economy().supply(home.id());
        int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(home.id()).size();
        WarWork.markPresent(home.id(), true);
        state.adminLoyalty(house, 5);
        state.adminLow(home.id(), NobilitySettings.rules().secessionDays(), true);
        state.adminPlot(house, NobilitySettings.rules().plotDays() - 1, NobilitySettings.rules().plotDays(), null, day(server));
        state.adminReplay(home.id());
        NobilityWork.overrideRolls(() -> 0.99);
        NobilityWork.kingdomDay(server, home.id(), day(server));
        UUID free = house.kingdomId();
        helper.assertTrue(!free.equals(home.id()) && WarWork.isNpc(server, free), "The house did not leave into a kingdom of the world");
        helper.runAfterDelay(240, () -> {
            try {
                helper.assertTrue(RoadWork.state(server).city(city.id()).map(City::kingdomId).orElse(null).equals(free), "The city did not change hands");
                helper.assertTrue(KingdomWorldApi.state(server).citizens().registry().get(there.id()).map(Citizen::kingdomId).orElse(null).equals(free),
                        "The citizen of the city did not go with it");
                helper.assertTrue(KingdomWorldApi.state(server).citizens().registry().get(here.id()).map(Citizen::kingdomId).orElse(null).equals(home.id()),
                        "A citizen of the capital left");
                int now = KingdomWorldApi.state(server).citizens().registry().citizensOf(home.id()).size()
                        + KingdomWorldApi.state(server).citizens().registry().citizensOf(free).size();
                helper.assertTrue(now == citizens, "Citizens were lost or doubled: " + citizens + " -> " + now);
                helper.assertTrue(KingdomWorldApi.state(server).membership().kingdom(home.id()).map(m -> m.members().get(king))
                        .map(member -> member.role() == Role.KING).orElse(false), "The king lost the crown");
                helper.assertTrue(KingdomWorldApi.state(server).economy().supply(home.id()) == money, "Money of the kingdom was made or lost");
                Optional<Territory> land = KingdomWorldApi.state(server).territory().territory(free);
                helper.assertTrue(land.isPresent() && land.get().chunks().contains(io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(
                        hall.getX() >> 4, hall.getZ() >> 4)), "The new kingdom has no land at the city");
                helper.assertTrue(state.court(home.id()).chronicle().stream().anyMatch(note -> note.key().equals("seceded")), "No chronicle of the secession");
                NpcWork.state(server).kingdom(free).ifPresent(npc -> NpcWork.state(server).site(npc.siteId())
                        .ifPresent(site -> site.setStatus(io.github.sekerator.kingdomworld.core.npc.NpcSite.Status.REMOVED)));
                Kingdom freeKingdom = KingdomWorldApi.kingdom(server, free).orElseThrow();
                finish(helper, home, List.of(freeKingdom), here, there);
                GatheringGameTests.clear(helper.getLevel(), fenceFrom, fenceTo);
                helper.succeed();
            } catch (RuntimeException e) {
                GatheringGameTests.clear(helper.getLevel(), fenceFrom, fenceTo);
                throw e;
            }
        });
    }

    // ---------------------------------------------------------------- spies

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_theft", timeoutTicks = 100)
    public static void aSpyStealsFromAForeignTreasuryByTheBooks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Spy Theft", king, UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom victim = both[1];
        WarWork.markPresent(home.id(), true);
        WarWork.markPresent(victim.id(), true);
        fund(server, home.id(), 1_000);
        fund(server, victim.id(), 2_000);
        helper.assertTrue(NobilityActions.hire(server, king, home.id()).ok(), "No spy was hired");
        Spy spy = NobilityWork.state(server).spiesOf(home.id()).get(0);
        helper.assertTrue(NobilityActions.send(server, king, home.id(), spy.id(), Spy.Task.THEFT, victim.id(), 0).ok(), "The spy was not sent");
        Economy economy = KingdomWorldApi.state(server).economy();
        long supply = economy.supply(victim.id());
        long reserve = economy.treasury(home.id()).orElseThrow().reserve(victim.id());
        long funds = WarWork.funds(server, victim.id());
        NobilityWork.overrideRolls(() -> 0.01);
        long done = day(server) + Spy.Task.THEFT.days();
        NobilityWork.spiesDay(server, done);
        long taken = Math.min(NobilitySettings.rules().theftMax(), funds * NobilitySettings.rules().theftPercent() / 100);
        helper.assertTrue(taken > 0 && WarWork.funds(server, victim.id()) == funds - taken,
                "The treasury of the victim lost " + (funds - WarWork.funds(server, victim.id())) + " instead of " + taken);
        helper.assertTrue(economy.treasury(home.id()).orElseThrow().reserve(victim.id()) == reserve + taken, "The money did not come into our treasury");
        helper.assertTrue(economy.supply(victim.id()) == supply, "Money was made or lost by the theft");
        TreasuryJournal.Totals books = economy.treasury(victim.id()).orElseThrow().journal().days().get(done).get(LedgerCategory.NOBILITY);
        helper.assertTrue(books != null && books.expense() == taken, "The theft is not in the books of the victim");
        helper.assertTrue(NobilityWork.state(server).court(home.id()).reports().stream().anyMatch(note -> note.key().equals("report.theft")),
                "No report of the theft");
        finish(helper, home, List.of(victim));
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_caught", timeoutTicks = 100)
    public static void aCaughtSpySpoilsTheRelations(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Spy Caught", king, UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom victim = both[1];
        WarWork.markPresent(home.id(), true);
        WarWork.markPresent(victim.id(), true);
        fund(server, home.id(), 1_000);
        helper.assertTrue(NobilityActions.hire(server, king, home.id()).ok(), "No spy was hired");
        Spy spy = NobilityWork.state(server).spiesOf(home.id()).get(0);
        helper.assertTrue(NobilityActions.send(server, king, home.id(), spy.id(), Spy.Task.INCITE, victim.id(), 0).ok(), "The spy was not sent");
        int before = WarWork.relation(server, victim.id(), home.id());
        NobilitySettings.override(NobilityRules.DEFAULT.withCaughtChance(1.0));
        NobilityWork.overrideRolls(() -> 0.99);
        NobilityWork.spiesDay(server, day(server) + Spy.Task.INCITE.days());
        helper.assertTrue(spy.status() == Spy.Status.CAPTURED && spy.captor().orElse(null).equals(victim.id()), "The spy was not caught");
        int after = WarWork.relation(server, victim.id(), home.id());
        helper.assertTrue(after < before, "The relations did not suffer: " + before + " -> " + after);
        helper.assertTrue(WarWork.state(server).relations().get(victim.id(), home.id()).map(r -> r.reasons().containsKey(Relations.Reason.SPY_CAUGHT))
                .orElse(false), "The caught spy is not a reason of the relations");
        helper.assertTrue(NobilityWork.state(server).court(victim.id()).chronicle().stream().anyMatch(note -> note.key().equals("spy_caught")),
                "The capture is not in the chronicle of the captor");
        long funds = WarWork.funds(server, home.id());
        helper.assertTrue(NobilityActions.ransom(server, king, home.id(), spy.id()).ok(), "The ransom could not be paid");
        helper.assertTrue(WarWork.funds(server, home.id()) == funds - NobilitySettings.rules().ransom() && spy.status() == Spy.Status.IDLE,
                "The ransom did not bring the spy home");
        finish(helper, home, List.of(victim));
        helper.succeed();
    }

    // ---------------------------------------------------------------- saving

    @GameTest(template = EMPTY, batch = "kingdomworld_nobility_save", timeoutTicks = 100)
    public static void theNobilityOutlivesASave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
        Kingdom home = CitizenGameTests.found(helper, "GameTest Noble Save", king);
        WarGameTests.join(server, home, subject);
        NobilityState state = NobilityWork.state(server);
        NobleHouse house = state.found(home.id(), 31, day(server));
        helper.assertTrue(state.grant(home.id(), Title.DUKE, Fief.march(1), house.id(), null, day(server)).ok(), "No duke");
        helper.assertTrue(state.grant(home.id(), Title.BARON, Fief.march(3), 0, subject, day(server)).ok(), "No baron");
        state.adminPlot(house, 2, 5, null, day(server));
        state.adminDemand(house, Demand.Kind.LOWER_TAX, null, 25, 2);
        Spy spy = state.hire(home.id(), 7, Gender.FEMALE, 60, day(server));
        state.send(spy.id(), Spy.Task.SCOUT, UUID.randomUUID(), 0, day(server));
        NobilitySavedData copy = NobilitySavedData.copyOf(NobilitySavedData.get(server));
        helper.assertFalse(copy.isReadOnly(), "The saved nobility went read-only: " + copy.readOnlyReason().orElse(""));
        NobilityState read = copy.state();
        helper.assertTrue(read.houses(home.id()).size() == 1 && read.house(house.id()).map(NobleHouse::familySeed).orElse(0) == house.familySeed(),
                "The house was not saved");
        helper.assertTrue(read.grants(home.id()).size() == 2 && read.isNoble(home.id(), subject), "The titles were not saved");
        helper.assertTrue(read.plotOf(home.id()).map(plot -> plot.progress() == 2).orElse(false), "The plot was not saved");
        helper.assertTrue(read.openDemand(home.id()).isPresent(), "The demand was not saved");
        helper.assertTrue(read.spy(spy.id()).map(s -> s.status() == Spy.Status.MISSION && s.skill() == 60).orElse(false), "The spy was not saved");
        finish(helper, home, List.of());
        helper.succeed();
    }
}
