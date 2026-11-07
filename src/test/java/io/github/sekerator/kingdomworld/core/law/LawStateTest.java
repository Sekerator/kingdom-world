package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;

    private KingdomWorldState world;
    private LawState law;
    private Kingdom kingdom;
    private UUID king;
    private UUID chancellor;
    private UUID subject;
    private UUID stranger;
    private LawRules rules;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        law = new LawState();
        king = UUID.randomUUID();
        chancellor = UUID.randomUUID();
        subject = UUID.randomUUID();
        stranger = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Iron Realm", 0x993333, king, new Capital(OVERWORLD, 8, 64, 8), DAY,
                KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        for (UUID player : new UUID[]{chancellor, subject}) {
            world.membership().invite(king, player, 1L);
            world.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        world.membership().setRole(king, chancellor, Role.CHANCELLOR);
        rules = new LawRules.Builder().build();
    }

    private LawPolicy.Offender offender(UUID player, boolean hasRight, boolean owner) {
        Optional<Role> role = world.membership().isMember(player, kingdom.id())
                ? world.membership().memberOf(player).map(io.github.sekerator.kingdomworld.core.membership.Member::role) : Optional.empty();
        return new LawPolicy.Offender(player, role, hasRight, owner);
    }

    // ---------------------------------------------------------------- crime or not

    @Test
    void theKingAndOfficialsCommitNoCrimes() {
        LawPolicy policy = new LawPolicy();
        assertFalse(policy.isCrime(kingdom.id(), offender(king, false, false), CrimeKind.MURDER));
        assertFalse(policy.isCrime(kingdom.id(), offender(chancellor, false, false), CrimeKind.THEFT));
        assertFalse(policy.isCrime(kingdom.id(), offender(chancellor, false, false), CrimeKind.ASSAULT));
    }

    @Test
    void subjectsAndStrangersAnswerForTheirDeeds() {
        LawPolicy policy = new LawPolicy();
        assertTrue(policy.isCrime(kingdom.id(), offender(subject, false, false), CrimeKind.THEFT));
        assertTrue(policy.isCrime(kingdom.id(), offender(subject, false, false), CrimeKind.ASSAULT));
        assertTrue(policy.isCrime(kingdom.id(), LawPolicy.Offender.outsider(stranger), CrimeKind.MURDER));
    }

    @Test
    void theOwnerAndWhoeverHoldsTheRightStealNothing() {
        LawPolicy policy = new LawPolicy();
        assertFalse(policy.isCrime(kingdom.id(), offender(stranger, false, true), CrimeKind.THEFT));
        assertFalse(policy.isCrime(kingdom.id(), offender(subject, true, false), CrimeKind.THEFT));
        // A right to containers or to build is no right to strike or kill.
        assertTrue(policy.isCrime(kingdom.id(), offender(subject, true, false), CrimeKind.ASSAULT));
    }

    @Test
    void warAndNobilityAreExemptions() {
        LawPolicy policy = new LawPolicy();
        LawPolicy.Exemption war = (kingdomId, player, kind) -> player.equals(stranger);
        policy.addExemption(war);
        assertFalse(policy.isCrime(kingdom.id(), LawPolicy.Offender.outsider(stranger), CrimeKind.MURDER));
        policy.removeExemption(war);
        assertTrue(policy.isCrime(kingdom.id(), LawPolicy.Offender.outsider(stranger), CrimeKind.MURDER));
        policy.setNobility((kingdomId, player) -> player.equals(subject));
        assertFalse(policy.isCrime(kingdom.id(), offender(subject, false, false), CrimeKind.THEFT));
    }

    // ---------------------------------------------------------------- the record

    @Test
    void theftsCloseTogetherAddUpToOneCrimeAndCostReputationOnce() {
        LawState.Committed first = law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 10, "", 1_000, rules);
        assertTrue(first.appeared());
        LawState.Committed second = law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 15, "", 1_000 + rules.mergeTicks, rules);
        assertFalse(second.appeared());
        Wanted wanted = law.wanted(kingdom.id(), stranger).orElseThrow();
        assertEquals(1, wanted.crimes().size());
        assertEquals(25, wanted.stolen());
        assertEquals(2, wanted.crimes().get(0).count());
        assertEquals(rules.theftReputation, law.reputation(kingdom.id(), stranger));
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 5, "", 1_000 + 3L * rules.mergeTicks, rules);
        assertEquals(2, law.wanted(kingdom.id(), stranger).orElseThrow().crimes().size());
        assertEquals(2 * rules.theftReputation, law.reputation(kingdom.id(), stranger));
    }

    @Test
    void theFineOfATheftIsTwiceTheStolenGoodsTimesTheCourt() {
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 30, "", 0, rules);
        Wanted wanted = law.wanted(kingdom.id(), stranger).orElseThrow();
        assertEquals(60, wanted.fine(rules, 1000));
        assertEquals(90, wanted.fine(rules, 1500));
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.ASSAULT, 0, "", 0, rules);
        assertEquals(60 + rules.assaultFine, wanted.fine(rules, 1000));
        assertFalse(wanted.executable());
    }

    @Test
    void aMurderIsPunishedWithDeathUnlessTheCourtSparesIt() {
        law.commit(kingdom.id(), stranger, "Killer", CrimeKind.MURDER, 0, "Radomir", 0, rules);
        Wanted wanted = law.wanted(kingdom.id(), stranger).orElseThrow();
        assertTrue(wanted.executable());
        assertEquals(0, wanted.fine(rules, 1000));
        assertEquals(KingdomError.NO_PERMISSION, law.setFine(subject, kingdom.id(), stranger, 500, rules, world).error());
        assertTrue(law.setFine(chancellor, kingdom.id(), stranger, 500, rules, world).ok());
        assertFalse(wanted.executable());
        assertEquals(500, wanted.fine(rules, 1000));
        assertTrue(wanted.commuted());
        // Paying the fine of a spared murderer clears the record.
        law.settle(kingdom.id(), stranger);
        assertTrue(law.wanted(kingdom.id(), stranger).isEmpty());
    }

    @Test
    void theCourtMayOnlyLowerAPettyFine() {
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 50, "", 0, rules);
        assertEquals(KingdomError.FINE_TOO_HIGH, law.setFine(king, kingdom.id(), stranger, 101, rules, world).error());
        assertTrue(law.setFine(king, kingdom.id(), stranger, 40, rules, world).ok());
        assertEquals(40, law.wanted(kingdom.id(), stranger).orElseThrow().fine(rules, 1000));
        // A new crime makes the court look again.
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.ASSAULT, 0, "", 10, rules);
        assertEquals(100 + rules.assaultFine, law.wanted(kingdom.id(), stranger).orElseThrow().fine(rules, 1000));
    }

    @Test
    void aPardonClearsTheRecordAndNeedsTheRight() {
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.MURDER, 0, "", 0, rules);
        assertEquals(KingdomError.NO_PERMISSION, law.pardon(subject, kingdom.id(), stranger, world).error());
        assertTrue(law.pardon(chancellor, kingdom.id(), stranger, world).ok());
        assertTrue(law.wanted(kingdom.id(), stranger).isEmpty());
        assertEquals(KingdomError.NOT_WANTED, law.pardon(king, kingdom.id(), stranger, world).error());
    }

    @Test
    void theChancellorHoldsTheCourtByDefault() {
        assertTrue(RolePermissions.defaults().has(Role.CHANCELLOR, Permission.MANAGE_LAW));
        assertFalse(RolePermissions.defaults().has(Role.SUBJECT, Permission.MANAGE_LAW));
        assertTrue(RolePermissions.defaults().has(Role.KING, Permission.MANAGE_LAW));
    }

    // ---------------------------------------------------------------- forgetting

    @Test
    void pettyCrimesAreForgottenButAMurderIsNot() {
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 10, "", 0, rules);
        law.commit(kingdom.id(), subject, "Killer", CrimeKind.MURDER, 0, "", 0, rules);
        long later = rules.decayTicks(law.reputation(kingdom.id(), stranger)) + 1;
        List<Wanted> cleared = law.decay(later, rules);
        assertEquals(1, cleared.size());
        assertEquals(stranger, cleared.get(0).player());
        assertTrue(law.wanted(kingdom.id(), subject).isPresent());
        assertTrue(law.decay(100 * DAY, rules).isEmpty());
    }

    @Test
    void aGoodNameIsForgivenSooner() {
        assertTrue(rules.decayTicks(100) < rules.decayTicks(0));
        assertTrue(rules.decayTicks(0) < rules.decayTicks(-100));
        assertEquals(Math.round(rules.pettyDays * DAY / 2), rules.decayTicks(100));
        assertEquals(Math.round(rules.pettyDays * DAY * 1.5), rules.decayTicks(-100));
    }

    // ---------------------------------------------------------------- money

    @Test
    void aFineIsPaidWithCoinsThenTheAccountThenADebt() {
        assertEquals(new FinePlan(100, 0, 0), FinePlan.of(100, 250, 999));
        assertEquals(new FinePlan(30, 70, 0), FinePlan.of(100, 30, 999));
        assertEquals(new FinePlan(30, 20, 50), FinePlan.of(100, 30, 20));
        assertEquals(new FinePlan(0, 0, 100), FinePlan.of(100, 0, 0));
        assertEquals(100, FinePlan.of(100, 7, 13).total());
    }

    @Test
    void incomeInTheKingdomPaysTheDebtFirstAndMakesNoMoney() {
        var economy = world.economy();
        economy.adminTreasury(kingdom.id(), 1_000, 1);
        economy.adminAccount(subject, kingdom.id(), 100);
        assertTrue(economy.addDebt(kingdom.id(), stranger, 70).ok());
        long supply = economy.supply(kingdom.id());
        assertTrue(economy.transfer(subject, stranger, kingdom.id(), 50).ok());
        assertEquals(20, economy.treasury(kingdom.id()).orElseThrow().debt(stranger));
        assertEquals(0, economy.accounts().balance(stranger, kingdom.id()));
        assertTrue(economy.kingdomPays(kingdom.id(), 40, stranger, io.github.sekerator.kingdomworld.core.economy.LedgerCategory.TRADE, 1).ok());
        assertEquals(0, economy.treasury(kingdom.id()).orElseThrow().debt(stranger));
        assertEquals(20, economy.accounts().balance(stranger, kingdom.id()));
        assertEquals(supply, economy.supply(kingdom.id()));
        // Coins handed in at a bank are no income: a deposit pays nothing back.
        economy.addDebt(kingdom.id(), stranger, 5);
        assertTrue(economy.deposit(stranger, kingdom.id(), 10).ok());
        assertEquals(5, economy.treasury(kingdom.id()).orElseThrow().debt(stranger));
        assertTrue(economy.repayDebt(kingdom.id(), stranger, 5, 1).ok());
        assertEquals(0, economy.treasury(kingdom.id()).orElseThrow().debt(stranger));
    }

    @Test
    void theRansomIsHalfTheWorthRoundedUp() {
        assertEquals(50, rules.ransom(100));
        assertEquals(51, rules.ransom(101));
        assertEquals(1, rules.ransom(1));
        assertEquals(0, rules.ransom(0));
    }

    @Test
    void confiscatedGoodsAreReleasedOnceAndGoToTheStorehousesInTime() {
        Confiscation taken = law.confiscate(kingdom.id(), "Iron Realm", stranger, "Killer", 400, 12, 0, 3);
        assertEquals(KingdomError.NOT_YOUR_CONFISCATION, law.checkRansom(subject, taken.id()).error());
        assertTrue(law.checkRansom(stranger, taken.id()).ok());
        assertTrue(law.expired(3 + rules.confiscationDays - 1, rules).isEmpty());
        assertEquals(List.of(taken), law.expired(3 + rules.confiscationDays, rules));
        assertTrue(law.release(taken.id()).isPresent());
        assertTrue(law.release(taken.id()).isEmpty());
        assertEquals(KingdomError.UNKNOWN_CONFISCATION, law.checkRansom(stranger, taken.id()).error());
    }

    // ---------------------------------------------------------------- reputation

    @Test
    void reputationStaysWithinItsBoundsAndGrowsWithTrade() {
        law.changeReputation(kingdom.id(), stranger, 500);
        assertEquals(100, law.reputation(kingdom.id(), stranger));
        law.changeReputation(kingdom.id(), stranger, -1000);
        assertEquals(-100, law.reputation(kingdom.id(), stranger));
        law.setReputation(kingdom.id(), stranger, 0);
        assertEquals(1, law.traded(kingdom.id(), stranger, rules.tradePerPoint + rules.tradePerPoint / 2, rules));
        assertEquals(1, law.traded(kingdom.id(), stranger, rules.tradePerPoint / 2, rules));
        assertEquals(2, law.reputation(kingdom.id(), stranger));
    }

    @Test
    void reputationMovesPricesByTenPercentAtMost() {
        assertEquals(900, Reputation.pricePermille(100, 10));
        assertEquals(1100, Reputation.pricePermille(-100, 10));
        assertEquals(1000, Reputation.pricePermille(0, 10));
        assertEquals(90, Reputation.price(100, 100, 10));
        assertEquals(110, Reputation.price(100, -100, 10));
        assertEquals(1, Reputation.price(1, 100, 10));
        assertEquals(110, Reputation.payout(100, 100, 10));
        assertEquals(90, Reputation.payout(100, -100, 10));
    }

    // ---------------------------------------------------------------- the gates

    @Test
    void subjectsAndTheRespectedEnterForFreeOthersPayByReputation() {
        KingdomLaw city = KingdomLaw.DEFAULT.withEntryFee(100);
        assertEquals(0, EntryRules.fee(city, true, 0, rules));
        assertEquals(0, EntryRules.fee(city, false, rules.highReputation, rules));
        assertEquals(100, EntryRules.fee(city, false, 0, rules));
        assertEquals(110, EntryRules.fee(city, false, -100, rules));
        assertEquals(0, EntryRules.fee(KingdomLaw.DEFAULT, false, 0, rules));
    }

    @Test
    void theGateLetsInWithAPassAndAsksForTheFeeOtherwise() {
        assertEquals(EntryRules.Decision.FREE, EntryRules.decide(0, false, false, false));
        assertEquals(EntryRules.Decision.PASS, EntryRules.decide(50, true, false, false));
        assertEquals(EntryRules.Decision.PAY, EntryRules.decide(50, false, true, true));
        assertEquals(EntryRules.Decision.REFUSE, EntryRules.decide(50, false, true, false));
        assertEquals(EntryRules.Decision.REFUSE, EntryRules.decide(50, false, false, true));
    }

    @Test
    void aRunnerPastTheGateBetweenTwoLooksCameThroughTheGate() {
        // A wall along z = 10 with a gate at x = 0; a runner covers 5.6 blocks between two looks.
        List<int[]> gates = List.of(new int[]{0, 10});
        assertTrue(EntryRules.passesGate(gates, 3, 1.5, 14.5, 1.5, 8.9));
        assertTrue(EntryRules.passesGate(gates, 3, -2.5, 15.0, 0.5, 4.0));
        // Over the wall eight blocks from the gate: past the gates.
        assertFalse(EntryRules.passesGate(gates, 3, 8.5, 14.5, 8.5, 8.9));
        assertFalse(EntryRules.passesGate(gates, 3, 8.5, 12.0, 8.5, 12.0));
    }

    @Test
    void aPassHoldsOneGameDay() {
        law.grantPass(kingdom.id(), stranger, 1_000, rules);
        assertTrue(law.hasPass(kingdom.id(), stranger, 1_000 + DAY - 1));
        assertFalse(law.hasPass(kingdom.id(), stranger, 1_000 + DAY));
        law.expirePasses(1_000 + DAY);
        assertTrue(law.passes().isEmpty());
    }

    @Test
    void theCourtSetsItsLawsWithinTheirBounds() {
        assertTrue(law.setEntryFee(king, kingdom.id(), 250, rules, world).ok());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, law.setEntryFee(king, kingdom.id(), rules.maxEntryFee + 1, rules, world).error());
        assertEquals(KingdomError.NO_PERMISSION, law.setSlaveryBanned(subject, kingdom.id(), true, world).error());
        assertTrue(law.setSlaveryBanned(chancellor, kingdom.id(), true, world).ok());
        assertTrue(law.slaveryBanned(kingdom.id()));
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, law.setMultiplier(king, kingdom.id(), 50, world).error());
        assertTrue(law.setMultiplier(king, kingdom.id(), 2000, world).ok());
        assertEquals(new KingdomLaw(2000, 250, true, -1), law.law(kingdom.id()));
        Outcome reset = law.setSlaveryBanned(king, kingdom.id(), false, world);
        assertTrue(reset.ok());
    }

    @Test
    void aKingdomThatIsGoneTakesItsRecordsButLeavesTheGoods() {
        law.commit(kingdom.id(), stranger, "Thief", CrimeKind.THEFT, 5, "", 0, rules);
        law.grantPass(kingdom.id(), stranger, 0, rules);
        Confiscation goods = law.confiscate(kingdom.id(), "Iron Realm", stranger, "Thief", 10, 1, 0, 0);
        law.forgetKingdom(kingdom.id());
        assertTrue(law.wanted(kingdom.id(), stranger).isEmpty());
        assertTrue(law.passes().isEmpty());
        assertTrue(law.confiscation(goods.id()).isPresent());
    }
}
