package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionStateTest {
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;
    private static final CompanionRules RULES = CompanionRules.DEFAULT;
    private static final int SKINS = 4;

    private KingdomWorldState world;
    private Economy economy;
    private CompanionState state;
    private Kingdom kingdom;
    private UUID king;
    private UUID buyer;
    private long now;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        economy = world.economy();
        state = new CompanionState();
        king = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Market Realm", 0x336699, king, new Capital("minecraft:overworld", 8, 64, 8),
                0, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        buyer = UUID.randomUUID();
        economy.adminAccount(buyer, kingdom.id(), 100_000);
        now = 3 * DAY;
    }

    private long money() {
        return economy.supply(kingdom.id());
    }

    private Offer firstOffer(boolean slave) {
        return state.offers(kingdom.id(), 3, 7L, RULES, SKINS).stream().filter(offer -> offer.slave() == slave).findFirst().orElseThrow();
    }

    private Companion buy(Rank rank) {
        Offer offer = firstOffer(true);
        CompanionState.Made made = state.take(buyer, Optional.of(rank), kingdom.id(), offer.id(), buyer, UUID.randomUUID(), now, 3, RULES, world);
        assertTrue(made.outcome().ok(), "buying failed: " + made.outcome());
        return made.companion().orElseThrow();
    }

    @Test
    void offersAreTheSameForTheSameDayAndChangeWithThePeriod() {
        List<Offer> first = MarketOffers.generate(kingdom.id(), 3, 7L, RULES, SKINS, new java.util.concurrent.atomic.AtomicLong(1)::getAndIncrement);
        List<Offer> again = MarketOffers.generate(kingdom.id(), 3, 7L, RULES, SKINS, new java.util.concurrent.atomic.AtomicLong(1)::getAndIncrement);
        assertEquals(first, again);
        assertEquals(RULES.slaveOffers + RULES.freeOffers, first.size());
        assertEquals(RULES.slaveOffers, first.stream().filter(Offer::slave).count());
        assertTrue(first.stream().filter(Offer::slave).allMatch(offer -> offer.origin().slave()));
        assertTrue(first.stream().filter(offer -> !offer.slave()).noneMatch(offer -> offer.origin().slave()));
        List<Offer> later = MarketOffers.generate(kingdom.id(), 4, 7L, RULES, SKINS, new java.util.concurrent.atomic.AtomicLong(1)::getAndIncrement);
        assertNotEquals(first, later);
        List<Offer> kept = state.offers(kingdom.id(), 3, 7L, RULES, SKINS);
        assertEquals(kept, state.offers(kingdom.id(), 3, 7L, RULES, SKINS), "the offers stay for the day");
    }

    @Test
    void thePriceFollowsTheStrength() {
        Skills weak = new Skills(1, 1, 1);
        Skills strong = new Skills(8, 5, 5);
        assertTrue(CompanionPower.price(0, weak, RULES) >= RULES.minPrice);
        assertTrue(CompanionPower.price(10, strong, RULES) > CompanionPower.price(0, weak, RULES));
        assertTrue(CompanionPower.price(10, weak, RULES) > CompanionPower.price(2, weak, RULES));
        assertTrue(CompanionPower.worth(0, new Skills(1, 6, 6)) > CompanionPower.worth(0, weak), "a strong back and stamina are worth more");
        assertEquals(CompanionPower.price(4, strong, RULES) * RULES.sellPercent / 100, CompanionPower.buyBack(4, strong, RULES));
        for (Offer offer : MarketOffers.generate(kingdom.id(), 9, 1L, RULES, SKINS, new java.util.concurrent.atomic.AtomicLong(1)::getAndIncrement)) {
            assertEquals(offer.slave() ? CompanionPower.price(offer.level(), offer.skills(), RULES)
                    : CompanionPower.wage(offer.level(), offer.skills(), RULES), offer.price());
        }
    }

    @Test
    void buyingPaysTheTreasuryOfTheBranchAndMakesNoMoney() {
        long before = money();
        long treasury = economy.treasury(kingdom.id()).orElseThrow().funds();
        Offer offer = firstOffer(true);
        assertEquals(Optional.of(KingdomError.NOT_IN_GUILD), state.take(buyer, Optional.empty(), kingdom.id(), offer.id(), buyer, UUID.randomUUID(),
                now, 3, RULES, world).outcome().failure());
        Companion companion = buy(Rank.F);
        assertEquals(buyer, companion.owner());
        assertTrue(companion.isSlave());
        assertEquals(RULES.slaveLoyalty, companion.loyalty());
        assertEquals(treasury + offer.price(), economy.treasury(kingdom.id()).orElseThrow().funds());
        assertEquals(100_000 - offer.price(), economy.accounts().balance(buyer, kingdom.id()));
        assertEquals(before, money(), "the price only moves money");
        assertFalse(state.offers(kingdom.id(), 3, 7L, RULES, SKINS).contains(offer), "a bought slave leaves the market");
        assertEquals(offer.price(), economy.treasury(kingdom.id()).orElseThrow().journal()
                .totals(3, io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.TODAY).get(LedgerCategory.COMPANIONS).income());
    }

    @Test
    void theRankLimitsTheCompanions() {
        assertEquals(1, RULES.limit(Rank.F));
        assertEquals(5, RULES.limit(Rank.SSS));
        buy(Rank.F);
        Offer next = firstOffer(true);
        assertEquals(Optional.of(KingdomError.TOO_MANY_COMPANIONS), state.take(buyer, Optional.of(Rank.F), kingdom.id(), next.id(), buyer,
                UUID.randomUUID(), now, 3, RULES, world).outcome().failure());
        assertTrue(state.take(buyer, Optional.of(Rank.D), kingdom.id(), next.id(), buyer, UUID.randomUUID(), now, 3, RULES, world).outcome().ok());
    }

    @Test
    void theKingClosesTheMarketOfSlavesButNotTheHire() {
        assertEquals(Optional.of(KingdomError.NOT_KING), state.setMarketClosed(buyer, kingdom.id(), true, world).failure());
        assertTrue(state.setMarketClosed(king, kingdom.id(), true, world).ok());
        assertEquals(Optional.of(KingdomError.SLAVE_MARKET_CLOSED), state.take(buyer, Optional.of(Rank.SSS), kingdom.id(), firstOffer(true).id(), buyer,
                UUID.randomUUID(), now, 3, RULES, world).outcome().failure());
        assertTrue(state.take(buyer, Optional.of(Rank.SSS), kingdom.id(), firstOffer(false).id(), buyer, UUID.randomUUID(), now, 3, RULES, world)
                .outcome().ok(), "free companions are still hired");
        state.setLaw(id -> false);
        assertTrue(state.setMarketClosed(null, kingdom.id(), false, world).ok());
        assertEquals(Optional.of(KingdomError.SLAVERY_FORBIDDEN), state.take(buyer, Optional.of(Rank.SSS), kingdom.id(), firstOffer(true).id(), buyer,
                UUID.randomUUID(), now, 3, RULES, world).outcome().failure());
    }

    @Test
    void hungerLowersLoyaltyAndALongWarnedCompanionEscapes() {
        Companion companion = buy(Rank.F);
        state.setLoyalty(companion.id(), 25, now);
        long t = now;
        // Twenty points of food at about a minute each, then hunger for long.
        for (int i = 0; i < 200; i++) {
            t += 600;
            state.live(companion.id(), 600, t, false, false, RULES);
        }
        assertEquals(0, companion.food());
        assertTrue(companion.isStarving(t, RULES));
        assertTrue(companion.loyalty() < RULES.escapeBelow, "starving lowered loyalty: " + companion.loyalty());
        assertEquals(LoyaltyReason.HUNGER, companion.notes().get(0).reason());
        assertEquals(CompanionState.Mood.WARNING, state.mood(companion.id(), t, RULES), "a warning comes first");
        assertTrue(companion.warned());
        assertEquals(CompanionState.Mood.CALM, state.mood(companion.id(), t + RULES.escapeTicks - 1, RULES));
        assertEquals(CompanionState.Mood.LEAVES, state.mood(companion.id(), t + RULES.escapeTicks, RULES));
    }

    @Test
    void goodKeepingAndGiftsRaiseLoyaltyAndCalmAWarning() {
        Companion companion = buy(Rank.F);
        state.setLoyalty(companion.id(), 10, now);
        assertEquals(CompanionState.Mood.WARNING, state.mood(companion.id(), now, RULES));
        assertEquals(RULES.maxGift, state.gift(buyer, companion.id(), 1_000_000, now, RULES), "a gift wins loyalty up to a limit");
        assertEquals(1, state.gift(buyer, companion.id(), 1, now, RULES), "even a small one");
        assertEquals(-1, state.gift(UUID.randomUUID(), companion.id(), 100, now, RULES), "only from the owner");
        assertEquals(CompanionState.Mood.RECOVERED, state.mood(companion.id(), now, RULES));
        int before = companion.loyalty();
        state.feed(companion.id(), 20);
        state.live(companion.id(), RULES.wellKeptTicks, now + RULES.wellKeptTicks, true, false, RULES);
        assertEquals(before + 1, companion.loyalty());
        state.beaten(companion.id(), now, RULES);
        assertEquals(before + 1 - RULES.beatenLoss, companion.loyalty());
        state.setLoyalty(companion.id(), RULES.wellKeptCap, now);
        for (int i = 0; i < 3; i++) {
            state.feed(companion.id(), 20);
            state.live(companion.id(), RULES.wellKeptTicks, now, true, false, RULES);
        }
        assertEquals(RULES.wellKeptCap, companion.loyalty(), "good keeping alone does not make a slave fully loyal");
    }

    @Test
    void aFreedSlaveServesForPayOrTheOwnerNeedsAKingdom() {
        Companion companion = buy(Rank.F);
        assertEquals(Optional.of(KingdomError.NOT_A_SUBJECT), state.checkFree(buyer, companion.id(), true, Optional.empty()).failure());
        assertTrue(state.checkFree(buyer, companion.id(), true, Optional.of(kingdom.id())).ok());
        assertEquals(Optional.of(KingdomError.NOT_YOUR_COMPANION), state.freeIntoService(UUID.randomUUID(), companion.id(), now, 3, RULES).failure());
        assertTrue(state.freeIntoService(buyer, companion.id(), now, 3, RULES).ok());
        assertFalse(companion.isSlave());
        assertEquals(Origin.FREEDMAN, companion.origin());
        assertTrue(companion.loyalty() >= RULES.freedLoyalty);
        assertEquals(CompanionPower.wage(companion.level(), companion.skills(), RULES), companion.wage());
        assertEquals(Optional.of(KingdomError.NOT_A_SLAVE), state.checkSell(buyer, companion.id(), RULES, world).failure());
        assertEquals(CompanionState.Wage.NONE, state.payWage(companion.id(), 3, RULES, world, now), "the first day is free");
        long before = money();
        assertEquals(CompanionState.Wage.PAID, state.payWage(companion.id(), 4, RULES, world, now));
        assertEquals(before, money());
    }

    @Test
    void anUnpaidFreeCompanionLeaves() {
        UUID poor = UUID.randomUUID();
        economy.adminAccount(poor, kingdom.id(), firstOffer(false).price());
        CompanionState.Made made = state.take(poor, Optional.of(Rank.F), kingdom.id(), firstOffer(false).id(), poor, UUID.randomUUID(), now, 3,
                RULES, world);
        assertTrue(made.outcome().ok(), "hiring pays the first day: " + made.outcome());
        Companion hired = made.companion().orElseThrow();
        assertEquals(0, economy.accounts().balance(poor, kingdom.id()));
        assertEquals(CompanionState.Wage.UNPAID, state.payWage(hired.id(), 4, RULES, world, now));
        assertEquals(CompanionState.Wage.LEAVES, state.payWage(hired.id(), 5, RULES, world, now));
    }

    @Test
    void sellingBackPaysFromTheTreasuryAndPutsTheSlaveOnTheMarket() {
        Companion companion = buy(Rank.F);
        long amount = CompanionPower.buyBack(companion.level(), companion.skills(), RULES);
        long before = money();
        long balance = economy.accounts().balance(buyer, kingdom.id());
        assertEquals(amount, state.sell(buyer, companion.id(), 3, RULES, world));
        assertEquals(balance + amount, economy.accounts().balance(buyer, kingdom.id()));
        assertEquals(before, money());
        assertTrue(state.companion(companion.id()).isEmpty());
        assertTrue(state.offers(kingdom.id(), 3, 7L, RULES, SKINS).stream().anyMatch(offer -> offer.nameSeed() == companion.nameSeed()),
                "the slave is on the market again");
    }

    @Test
    void anEmptyTreasuryDoesNotBuyBack() {
        Companion companion = buy(Rank.F);
        economy.adminTreasury(kingdom.id(), -economy.treasury(kingdom.id()).orElseThrow().funds(), 3);
        assertEquals(Optional.of(KingdomError.TREASURY_EMPTY), state.checkSell(buyer, companion.id(), RULES, world).failure());
        assertEquals(-1, state.sell(buyer, companion.id(), 3, RULES, world));
        assertTrue(state.companion(companion.id()).isPresent());
    }

    @Test
    void experienceRaisesLevelsAndSkills() {
        Companion companion = state.give(buyer, UUID.randomUUID(), 1, Gender.FEMALE, 0, 0, new Skills(1, 1, 1), kingdom.id(), now, 3, RULES);
        assertEquals(1, state.addExperience(companion.id(), RULES.experienceFor(1), RULES));
        assertEquals(1, companion.level());
        assertEquals(new Skills(2, 1, 1), companion.skills());
        assertEquals(Companion.maxHealth(1), companion.maxHealth());
        assertEquals(2, state.addExperience(companion.id(), RULES.experienceFor(2) + RULES.experienceFor(3), RULES));
        assertEquals(new Skills(2, 2, 2), companion.skills());
        assertEquals(18, new Skills(1, 4, 1).slots());
        assertEquals(36, new Skills(1, 10, 1).slots());
    }

    @Test
    void ordersAtAPlaceNeedThePlace() {
        Companion companion = buy(Rank.F);
        assertEquals(Optional.of(KingdomError.BAD_ORDER), state.setOrder(buyer, companion.id(), CompanionOrder.GUARD, null).failure());
        WorldPos here = new WorldPos("minecraft:overworld", 1, 2, 3);
        assertTrue(state.setOrder(buyer, companion.id(), CompanionOrder.GUARD, here).ok());
        assertEquals(Optional.of(here), companion.orderPos());
        assertTrue(state.setOrder(buyer, companion.id(), CompanionOrder.FOLLOW, here).ok());
        assertTrue(companion.orderPos().isEmpty());
    }

    @Test
    void countsOfCompanionsUseThePluralForms() {
        assertEquals(Plurals.Form.N1, Plurals.form(1));
        assertEquals(Plurals.Form.N21, Plurals.form(21));
        assertEquals(Plurals.Form.N2, Plurals.form(4));
        assertEquals(Plurals.Form.N5, Plurals.form(5));
        assertEquals(Plurals.Form.N5, Plurals.form(12));
    }
}
