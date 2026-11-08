package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NobilityStateTest {
    private static final NobilityRules RULES = NobilityRules.DEFAULT;

    private NobilityState state;
    private UUID kingdom;
    private UUID other;

    @BeforeEach
    void setUp() {
        state = new NobilityState();
        kingdom = UUID.randomUUID();
        other = UUID.randomUUID();
    }

    /** Rolls that always say the same. */
    private static DoubleSupplier always(double value) {
        return () -> value;
    }

    /** Rolls in the order given, then the last one again and again. */
    private static DoubleSupplier rolls(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double value : values) {
            queue.add(value);
        }
        double[] last = {values.length == 0 ? 0.99 : values[values.length - 1]};
        return () -> queue.isEmpty() ? last[0] : (last[0] = queue.poll());
    }

    private NobilityState.DayInput day(long day, boolean npc, boolean played, Stability.Inputs inputs, int counter) {
        return new NobilityState.DayInput(kingdom, npc, played, day, inputs, counter, NobilityState.Context.NONE);
    }

    private static boolean has(List<Happening> list, Happening.Kind kind) {
        return list.stream().anyMatch(h -> h.kind() == kind);
    }

    // ---------------------------------------------------------------- stability

    @Test
    void stabilityIsMadeOfItsCausesAndShowsThem() {
        Stability.Result calm = Stability.compute(Stability.Inputs.NEUTRAL, List.of());
        assertEquals(Stability.BASE, calm.value());
        assertTrue(calm.causes().isEmpty());

        Stability.Inputs hard = new Stability.Inputs(20, 30, 200, 60, 1, false, 30);
        Stability.Result result = Stability.compute(hard, List.of(new Stability.Modifier("incited", -8, 10)));
        // houses -9, mood -8, taxes -10, war -12, defeats -8, factions -4, incited -8: below nothing
        assertEquals(0, result.value());
        Stability.Result milder = Stability.compute(new Stability.Inputs(20, 30, 200, 0, 0, false, 30), List.of());
        assertEquals(50 - 9 - 8 - 10 - 4, milder.value());
        assertEquals("war", result.causes().get(0).id());
        assertTrue(result.causes().stream().anyMatch(c -> c.id().equals("incited") && c.amount() == -8));

        Stability.Result festive = Stability.compute(new Stability.Inputs(80, 70, 40, 0, 0, true, 70), List.of());
        assertEquals(50 + 9 + 8 + 5 + 6 + 4, festive.value());
        assertEquals(100, Stability.compute(new Stability.Inputs(100, 100, 0, 0, 0, true, 100), List.of(new Stability.Modifier("x", 90, 1)))
                .value());
    }

    @Test
    void theCourtAddsTheLoyaltyOfHousesAndTheContentOfParties() {
        assertEquals(-1, state.loyalty(kingdom));
        NobleHouse house = state.found(kingdom, 42, 0);
        state.adminLoyalty(house, 10);
        state.adminContent(kingdom, house.faction(), 0);
        Stability.Result result = state.stability(kingdom, Stability.Inputs.NEUTRAL);
        assertTrue(result.value() < 40, "an angry house and an angry party shake the kingdom: " + result);
        assertTrue(result.causes().stream().anyMatch(c -> c.id().equals("houses")));
    }

    // ---------------------------------------------------------------- titles

    @Test
    void titlesGoToHousesAndPlayersOneFiefEachAndPlayersBecomeNoble() {
        NobleHouse house = state.found(kingdom, 1, 0);
        UUID player = UUID.randomUUID();
        assertTrue(state.grant(kingdom, Title.COUNT, Fief.city(7), house.id(), null, 1).ok());
        assertEquals(Optional.of(KingdomError.FIEF_TAKEN), state.grant(kingdom, Title.BARON, Fief.city(7), 0, player, 1).failure());
        assertFalse(state.isNoble(kingdom, player));
        assertTrue(state.grant(kingdom, Title.BARON, Fief.march(2), 0, player, 1).ok());
        assertTrue(state.isNoble(kingdom, player));
        assertFalse(state.isNoble(other, player));
        assertEquals(Optional.of(KingdomError.ALREADY_NOBLE), state.grant(kingdom, Title.BARON, Fief.march(3), 0, player, 1).failure());

        Grant playerGrant = state.grants(kingdom).stream().filter(Grant::heldByPlayer).findFirst().orElseThrow();
        assertTrue(state.transfer(playerGrant.id(), house.id(), null, 2).ok());
        assertFalse(state.isNoble(kingdom, player));
        assertEquals(2, state.grantsOf(house).size());
        int loyalty = house.loyalty();
        Grant city = state.holderOf(kingdom, Fief.city(7)).orElseThrow();
        assertTrue(state.revoke(city.id(), 3).isPresent());
        assertEquals(loyalty - 25, house.loyalty());
        assertTrue(state.holderOf(kingdom, Fief.city(7)).isEmpty());
    }

    @Test
    void aHouseTheCrownRaisedGoesAwayWithItsLastTitle() {
        NobleHouse raised = state.found(kingdom, 21, 0);
        raised.setRaised(true);
        NobleHouse old = state.found(kingdom, 22, 0);
        assertTrue(state.grant(kingdom, Title.BARON, Fief.march(0), raised.id(), null, 1).ok());
        assertTrue(state.grant(kingdom, Title.BARON, Fief.march(1), old.id(), null, 1).ok());
        state.revoke(state.holderOf(kingdom, Fief.march(0)).orElseThrow().id(), 2);
        assertTrue(state.house(raised.id()).isEmpty(), "a raised house without titles is gone");
        state.revoke(state.holderOf(kingdom, Fief.march(1)).orElseThrow().id(), 2);
        assertTrue(state.house(old.id()).isPresent(), "an old house stays without titles");
        NobleHouse other = state.found(kingdom, 23, 0);
        other.setRaised(true);
        assertTrue(state.grant(kingdom, Title.COUNT, Fief.city(5), other.id(), null, 3).ok());
        assertTrue(state.transfer(state.holderOf(kingdom, Fief.city(5)).orElseThrow().id(), old.id(), null, 3).ok());
        assertTrue(state.house(other.id()).isEmpty(), "a raised house that handed over its last title is gone");
        assertEquals(old.id(), state.holderOf(kingdom, Fief.city(5)).orElseThrow().house());
    }

    @Test
    void aKingdomOfTheWorldHasItsHousesWithTitlesFromTheStart() {
        List<NobleHouse> made = state.foundCourt(kingdom, 4, 99, List.of(Fief.city(3), Fief.march(0), Fief.march(1)), true, 0);
        assertEquals(4, made.size());
        assertEquals(3, state.grants(kingdom).size());
        assertEquals(Title.DUKE, state.grantsOf(made.get(0)).get(0).title());
        assertEquals(Title.BARON, state.grantsOf(made.get(1)).get(0).title());
        assertTrue(state.grantsOf(made.get(3)).isEmpty());
        assertNotEquals(made.get(0).family(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN),
                made.get(0).family(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC));
        int houses = NobilityMind.houses(io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL, 5);
        assertTrue(houses >= 2 && houses <= 5);
    }

    // ---------------------------------------------------------------- demands

    @Test
    void demandsComeRarelyOnlyOnPlayedDaysAndLapseAsAMildRefusal() {
        NobleHouse house = state.found(kingdom, 3, 0);
        state.adminContent(kingdom, house.faction(), 20);
        int made = 0;
        for (long d = 1; d <= 2; d++) {
            if (has(state.day(day(d, false, true, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9)), Happening.Kind.DEMAND_MADE)) {
                made++;
            }
        }
        assertEquals(0, made, "not before " + RULES.demandEveryDays() + " played days");
        // Days nobody played count for nothing.
        for (long d = 3; d <= 10; d++) {
            assertFalse(has(state.day(day(d, false, false, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9)), Happening.Kind.DEMAND_MADE));
        }
        assertTrue(has(state.day(day(11, false, true, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9)), Happening.Kind.DEMAND_MADE));
        Demand demand = state.openDemand(kingdom).orElseThrow();
        assertEquals(house.id(), demand.house());
        // The same day counts once.
        state.day(day(11, false, true, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9));
        assertTrue(state.openDemand(kingdom).isPresent());
        // Away from the game the demand waits.
        for (long d = 12; d <= 30; d++) {
            state.day(day(d, false, false, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9));
        }
        assertTrue(state.openDemand(kingdom).isPresent());
        int content = state.court(kingdom).content(demand.faction());
        state.day(day(31, false, true, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9));
        List<Happening> lapsed = state.day(day(32, false, true, Stability.Inputs.NEUTRAL, 0), RULES, always(0.9));
        assertTrue(has(lapsed, Happening.Kind.DEMAND_LAPSED));
        assertTrue(state.openDemand(kingdom).isEmpty());
        assertTrue(state.court(kingdom).content(demand.faction()) < content);
        assertTrue(state.court(kingdom).modifiers().stream().anyMatch(m -> m.cause().equals("demand_ignored")));
    }

    @Test
    void answersChangeContentAndStabilityAndBargainsHalveBoth() {
        NobleHouse house = state.found(kingdom, 4, 0);
        Faction party = house.faction();
        Demand first = state.adminDemand(house, Demand.Kind.BUILD, null, 150, 2);
        int before = state.court(kingdom).content(party);
        assertTrue(state.answer(first.id(), Demand.Answer.ACCEPT, 5).ok());
        assertEquals(Math.min(100, before + 20), state.court(kingdom).content(party));
        int accepted = state.stability(kingdom, Stability.Inputs.NEUTRAL).value();

        Demand second = state.adminDemand(house, Demand.Kind.BUILD, null, 150, 2);
        assertEquals(75, second.bargain(150));
        int mid = state.court(kingdom).content(party);
        assertTrue(state.answer(second.id(), Demand.Answer.BARGAIN, 5).ok());
        assertEquals(mid - 10, state.court(kingdom).content(party));

        Demand third = state.adminDemand(house, Demand.Kind.LOWER_TAX, null, 25, 2);
        int low = state.court(kingdom).content(party);
        assertTrue(state.answer(third.id(), Demand.Answer.REFUSE, 5).ok());
        assertEquals(Math.max(0, low - 20), state.court(kingdom).content(party));
        int refused = state.stability(kingdom, Stability.Inputs.NEUTRAL).value();
        assertTrue(refused < accepted, "a refusal shakes the kingdom: " + refused + " < " + accepted);
        assertEquals(Optional.of(KingdomError.NOT_PENDING), state.answer(third.id(), Demand.Answer.ACCEPT, 5).failure());
    }

    @Test
    void rulersOfTheWorldAnswerByTheirTraits() {
        Demand build = new Demand(1, kingdom, Faction.CROWN, 1, Demand.Kind.BUILD, null, 100, 0, 2);
        Demand war = new Demand(2, kingdom, Faction.WAR, 1, Demand.Kind.DECLARE_WAR, other, 100, 0, 2);
        assertEquals(Demand.Answer.REFUSE, NobilityMind.answer(build, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.PROUD), true));
        assertEquals(Demand.Answer.ACCEPT, NobilityMind.answer(build, RulerCharacter.BUILDER, List.of(RulerCharacter.Trait.GENEROUS), true));
        assertNotEquals(Demand.Answer.ACCEPT, NobilityMind.answer(war, RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.GENEROUS), true));
        assertEquals(Plot.Verdict.PARDON, NobilityMind.verdict(List.of(RulerCharacter.Trait.JUST)));
        assertEquals(Plot.Verdict.EXECUTE, NobilityMind.verdict(List.of(RulerCharacter.Trait.PROUD)));
    }

    // ---------------------------------------------------------------- plots

    private static final Stability.Inputs UNREST = new Stability.Inputs(-1, 10, 300, 80, 2, false, -1);

    @Test
    void aPlotRipensOverPlayedDaysAndOverthrowsARulerOfTheWorld() {
        NobleHouse house = state.found(kingdom, 5, 0);
        state.adminLoyalty(house, 5);
        List<Happening> all = new java.util.ArrayList<>();
        long d = 1;
        while (d < 40 && !has(all, Happening.Kind.RULER_OVERTHROWN)) {
            state.adminLoyalty(house, 5);
            all.addAll(state.day(day(d++, true, true, UNREST, 0), RULES, always(0.01)));
        }
        assertTrue(has(all, Happening.Kind.PLOT_STARTED));
        assertTrue(has(all, Happening.Kind.RULER_OVERTHROWN), "the plot struck: " + all);
        assertFalse(has(all, Happening.Kind.REBELS));
        assertEquals(90, house.loyalty());
        assertTrue(state.plotOf(kingdom).isEmpty());
        assertTrue(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("overthrown")));
    }

    @Test
    void counterIntelligenceFindsAPlotAndTheCrownJudges() {
        NobleHouse house = state.found(kingdom, 6, 0);
        Plot plot = state.adminPlot(house, 0, 4, null, 0);
        state.adminLoyalty(house, 5);
        List<Happening> found = state.day(day(1, false, true, UNREST, 80), RULES, always(0.01));
        assertTrue(has(found, Happening.Kind.PLOT_DISCOVERED));
        assertEquals(Plot.Status.DISCOVERED, plot.status());
        // A plot found out waits for the verdict and ripens no more.
        for (long d = 2; d < 10; d++) {
            state.adminLoyalty(house, 5);
            assertFalse(has(state.day(day(d, false, true, UNREST, 0), RULES, always(0.01)), Happening.Kind.REBELS));
        }
        int head = house.headSeed();
        assertTrue(state.verdict(plot.id(), Plot.Verdict.EXECUTE, 10, 1234).ok());
        assertNotEquals(head, house.headSeed());
        assertEquals(2, house.generation());
        assertTrue(state.plotOf(kingdom).isEmpty());

        NobleHouse second = state.found(kingdom, 7, 0);
        Plot exile = state.adminPlot(second, 0, 4, null, 0);
        state.adminDiscover(exile);
        assertTrue(state.verdict(exile.id(), Plot.Verdict.EXILE, 11, 0).ok());
        assertTrue(state.house(second.id()).isEmpty());
    }

    @Test
    void aPlotNeverOverthrowsAKingOfPlayersButKeepsIncomeOrTakesACity() {
        NobleHouse house = state.found(kingdom, 8, 0);
        state.adminLoyalty(house, 5);
        state.adminPlot(house, 3, 4, null, 0);
        List<Happening> struck = state.day(day(1, false, true, UNREST, 0), RULES, always(0.99));
        assertFalse(has(struck, Happening.Kind.RULER_OVERTHROWN));
        assertTrue(has(struck, Happening.Kind.REBELS));
        assertEquals(RULES.rebelDays(), state.court(kingdom).rebelDays());
        // The rebels keep part of the income on played days only.
        state.day(day(2, false, false, UNREST, 0), RULES, always(0.99));
        assertEquals(RULES.rebelDays(), state.court(kingdom).rebelDays());
        assertTrue(has(state.day(day(3, false, true, UNREST, 0), RULES, always(0.99)), Happening.Kind.REBELS));
        assertEquals(RULES.rebelDays() - 1, state.court(kingdom).rebelDays());

        // With a city, long low stability and a warning the city leaves.
        assertTrue(state.grant(kingdom, Title.COUNT, Fief.city(12), house.id(), null, 3).ok());
        state.adminLoyalty(house, 5);
        state.adminLow(kingdom, RULES.secessionDays(), true);
        state.adminPlot(house, 3, 4, other, 3);
        List<Happening> left = state.day(day(4, false, true, UNREST, 0), RULES, always(0.99));
        Happening secession = left.stream().filter(h -> h.kind() == Happening.Kind.SECESSION).findFirst().orElseThrow();
        assertEquals(12, secession.value());
        assertEquals(other, secession.other());
        assertFalse(has(left, Happening.Kind.RULER_OVERTHROWN));
        assertFalse(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("seceded")), "no chronicle before the city is gone");
        UUID free = UUID.randomUUID();
        state.seceded(kingdom, free, house.id(), 12, 4);
        assertEquals(free, house.kingdomId());
        assertTrue(state.holderOf(kingdom, Fief.city(12)).isEmpty());
        assertTrue(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("seceded")));
    }

    @Test
    void aCityThatCannotLeaveTurnsItsHouseToRebels() {
        NobleHouse house = state.found(kingdom, 30, 0);
        state.secessionFailed(kingdom, house.id(), RULES.rebelDays(), 5);
        assertEquals(RULES.rebelDays(), state.court(kingdom).rebelDays());
        assertEquals(house.id(), state.court(kingdom).rebelHouse());
        assertTrue(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("rebels")));
        assertFalse(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("seceded")));
    }

    @Test
    void theCrownOfPlayersIsWarnedBeforeACityMayLeave() {
        NobleHouse house = state.found(kingdom, 9, 0);
        assertTrue(state.grant(kingdom, Title.COUNT, Fief.city(4), house.id(), null, 0).ok());
        boolean warned = false;
        for (long d = 1; d <= RULES.secessionWarnDays() + 2 && !warned; d++) {
            state.adminLoyalty(house, 5);
            warned = has(state.day(day(d, false, true, UNREST, 0), RULES, always(0.99)), Happening.Kind.SECESSION_WARNING);
        }
        assertTrue(warned);
        assertTrue(state.court(kingdom).warned());
    }

    @Test
    void nothingPlotsWhileThePlayersAreAway() {
        NobleHouse house = state.found(kingdom, 10, 0);
        state.adminLoyalty(house, 0);
        state.adminLow(kingdom, 20, true);
        state.adminPlot(house, 3, 4, null, 0);
        for (long d = 1; d < 200; d++) {
            List<Happening> today = state.day(day(d, false, false, UNREST, 0), RULES, always(0.0));
            assertTrue(today.isEmpty(), "nothing happens on day " + d + ": " + today);
        }
        assertEquals(3, state.plotOf(kingdom).orElseThrow().progress());
    }

    // ---------------------------------------------------------------- spies

    private static final class World implements NobilityState.SpyWorld {
        boolean played = true;
        boolean harsh;
        int counter;
        UUID gone;

        @Override
        public boolean exists(UUID kingdomId) {
            return !kingdomId.equals(gone);
        }

        @Override
        public boolean npc(UUID kingdomId) {
            return false;
        }

        @Override
        public boolean played(UUID kingdomId) {
            return played;
        }

        @Override
        public int counter(UUID kingdomId) {
            return counter;
        }

        @Override
        public int relation(UUID a, UUID b) {
            return 0;
        }

        @Override
        public boolean atWar(UUID a, UUID b) {
            return false;
        }

        @Override
        public boolean harsh(UUID kingdomId) {
            return harsh;
        }
    }

    @Test
    void spiesSucceedOrFailByChanceAndAreCaughtForRansom() {
        World world = new World();
        Spy spy = state.hire(kingdom, 1, Gender.MALE, 50, 0);
        assertTrue(state.send(spy.id(), Spy.Task.INCITE, other, 0, 0).ok());
        assertEquals(Optional.of(KingdomError.SPY_BUSY), state.send(spy.id(), Spy.Task.SCOUT, other, 0, 0).failure());
        assertTrue(state.spiesDay(1, world, RULES, always(0.99)).isEmpty(), "not done before its days");
        List<Happening> done = state.spiesDay(3, world, RULES, always(0.01));
        assertTrue(has(done, Happening.Kind.SPY_DONE));
        assertEquals(Spy.Status.IDLE, spy.status());
        assertTrue(state.court(other).modifiers().stream().anyMatch(m -> m.cause().equals("incited")));

        assertTrue(state.send(spy.id(), Spy.Task.THEFT, other, 0, 3).ok());
        // Nobody of a kingdom of players is there: the spy waits.
        world.played = false;
        assertTrue(state.spiesDay(10, world, RULES, always(0.99)).isEmpty());
        assertEquals(Spy.Status.MISSION, spy.status());
        world.played = true;
        world.counter = 80;
        List<Happening> caught = state.spiesDay(11, world, RULES, rolls(0.99, 0.1));
        assertTrue(has(caught, Happening.Kind.SPY_CAUGHT), "" + caught);
        assertEquals(Spy.Status.CAPTURED, spy.status());
        assertEquals(Optional.of(other), spy.captor());
        assertTrue(state.ransomed(spy.id(), 12).ok());
        assertEquals(Spy.Status.IDLE, spy.status());

        assertTrue(state.send(spy.id(), Spy.Task.SCOUT, other, 0, 12).ok());
        world.harsh = true;
        List<Happening> executed = state.spiesDay(20, world, RULES, rolls(0.99, 0.1));
        assertTrue(has(executed, Happening.Kind.SPY_EXECUTED));
        assertTrue(state.spy(spy.id()).isEmpty());
    }

    @Test
    void theSpiesOfPlayersAwayWaitAndAPrisonerWithoutACaptorComesHome() {
        World world = new World();
        Spy spy = state.hire(kingdom, 3, Gender.MALE, 50, 0);
        assertTrue(state.send(spy.id(), Spy.Task.THEFT, other, 0, 0).ok());
        world.played = true;
        world.counter = 80;
        state.spiesDay(5, world, RULES, rolls(0.99, 0.1));
        assertEquals(Spy.Status.CAPTURED, spy.status());
        long release = spy.releaseBy();
        // The owner is away: the days of the ransom wait, the spy is not put to death.
        world.played = false;
        for (long d = release; d < release + 10; d++) {
            assertTrue(state.spiesDay(d, world, RULES, always(0.5)).isEmpty());
        }
        assertEquals(Spy.Status.CAPTURED, spy.status());
        assertEquals(release + 10, spy.releaseBy());
        // The captor is gone: the prisoner comes home instead of stopping the day.
        world.played = true;
        world.gone = other;
        assertTrue(state.spiesDay(release + 20, world, RULES, always(0.5)).isEmpty());
        assertEquals(Spy.Status.IDLE, spy.status());
    }

    @Test
    void aSpyStealsFromAForeignTreasuryByTheBooks() {
        Economy economy = new Economy();
        economy.open(kingdom, 0);
        economy.open(other, 0);
        assertTrue(economy.adminTreasury(other, 1_000, 0).ok());
        long supply = economy.supply(other);
        assertTrue(economy.nobilityTransfer(other, kingdom, 50, 1).ok());
        assertEquals(950, economy.treasury(other).orElseThrow().funds());
        assertEquals(50, economy.treasury(kingdom).orElseThrow().reserve(other));
        assertEquals(supply, economy.supply(other), "no money is made or lost");
        TreasuryJournal.Totals theirs = economy.treasury(other).orElseThrow().journal().days().get(1L).get(LedgerCategory.NOBILITY);
        assertEquals(50, theirs.expense());
        assertEquals(Optional.of(KingdomError.TREASURY_EMPTY), economy.nobilityTransfer(other, kingdom, 5_000, 1).failure());
        assertTrue(economy.nobilityPayment(kingdom, 10, 1).failure().isPresent(), "our own money is still none");
    }

    @Test
    void counterIntelligenceGrowsWithHuntersAndASpymaster() {
        assertEquals(RULES.counterBase(), state.counter(kingdom, false, RULES));
        Spy spy = state.hire(kingdom, 2, Gender.FEMALE, 40, 0);
        assertTrue(state.station(spy.id(), true).ok());
        assertEquals(RULES.counterBase() + 25, state.counter(kingdom, true, RULES));
    }

    @Test
    void marriagesJoinHousesOfKingdomsOnGoodTerms() {
        NobleHouse a = state.found(kingdom, 11, 0);
        NobleHouse b = state.found(other, 12, 0);
        assertTrue(state.marriage(1, RULES, always(0.5), (x, y) -> true).isEmpty(), "a marriage is rare");
        assertTrue(state.marriage(1, RULES, rolls(0.0, 0.1, 0.9), (x, y) -> false).isEmpty(), "enemies do not marry");
        Optional<Happening> wedding = state.marriage(1, RULES, rolls(0.0, 0.1, 0.9), (x, y) -> true);
        assertTrue(wedding.isPresent());
        assertTrue(state.court(kingdom).chronicle().stream().anyMatch(n -> n.key().equals("married")));
        assertTrue(a.loyalty() > 0 && b.loyalty() > 0);
    }

    @Test
    void numbersOfDaysAndSpiesHaveTheirFormsInBothLanguages() {
        assertEquals(Plurals.Form.N1, Plurals.form(1));
        assertEquals(Plurals.Form.N21, Plurals.form(21));
        assertEquals(Plurals.Form.N2, Plurals.form(3));
        assertEquals(Plurals.Form.N5, Plurals.form(11));
        assertEquals(Plurals.Form.N5, Plurals.form(0));
    }
}
