package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulerMindTest {
    private static final RulerRules RULES = RulerRules.DEFAULT;

    /**
     * A small village that lacks nothing: beds to spare, room in the stores, every building its level wants, money and a
     * steady income.
     */
    private static RulerView calm() {
        RulerView view = new RulerView();
        view.day = 10;
        view.character = RulerCharacter.BUILDER;
        view.traits = List.of(RulerCharacter.Trait.PATIENT);
        view.population = 10;
        view.bedsUsed = 10;
        view.bedsTotal = 14;
        view.foodDays = 10;
        view.storageSlots = 108;
        view.storageUsed = 30;
        view.balance = 3000;
        view.income = 200;
        view.expenses = 150;
        view.soldiers = 1;
        view.unemployed = 2;
        view.chunks = 50;
        view.maxChunks = 96;
        view.knownRoles = List.of("house", "warehouse", "market", "well", "workshop", "barracks", "tower", "mage_tower");
        for (RulerRules.RoleWant want : RULES.roles()) {
            view.roles.put(want.role(), want.count(0));
        }
        view.bounty = NpcLaws.of(view.character, view.traits, view.maxEntryFee, view.maxBounty).bounty();
        view.salesTaxPermille = RulerMind.salesTax(view, RULES);
        for (String role : view.knownRoles) {
            view.builds.add(new RulerView.Option(role, "kingdomworld:" + role, "", 0, true));
        }
        return view;
    }

    private static List<RulerDecision> decide(RulerView view) {
        return RulerMind.decide(view, new RulerMemory(), RULES).decisions();
    }

    private static boolean has(List<RulerDecision> decisions, Kind kind, String subject) {
        return decisions.stream().anyMatch(d -> d.kind() == kind && (subject == null || d.subject().equals(subject)));
    }

    @Test
    void aCalmKingdomLaysOutNothing() {
        List<RulerDecision> decisions = decide(calm());
        assertFalse(has(decisions, Kind.BUILD, null), decisions.toString());
    }

    @Test
    void bedsNearlyTakenMeanAHouse() {
        RulerView view = calm();
        view.bedsUsed = 14;
        List<RulerDecision> decisions = decide(view);
        assertTrue(has(decisions, Kind.BUILD, "house"), decisions.toString());
        RulerView planned = calm();
        planned.bedsUsed = 14;
        planned.planned.put("house", 1);
        assertFalse(has(decide(planned), Kind.BUILD, "house"), "a house being built already is enough");
    }

    @Test
    void fullStoresMeanAStorehouse() {
        RulerView view = calm();
        view.storageUsed = 100;
        List<RulerDecision> decisions = decide(view);
        assertTrue(has(decisions, Kind.BUILD, "warehouse"), decisions.toString());
    }

    @Test
    void aMissingRoleIsLaidOutInTheOrderOfTheTable() {
        RulerView view = calm();
        view.roles.remove("well");
        view.roles.remove("workshop");
        List<RulerDecision> decisions = decide(view);
        assertTrue(has(decisions, Kind.BUILD, "well"), decisions.toString());
        assertFalse(has(decisions, Kind.BUILD, "workshop"), "one new building a turn");
    }

    @Test
    void aRoleTheServerDoesNotKnowIsNeverWanted() {
        RulerView view = calm();
        view.cityLevel = 1;
        view.roles.put("well", 2);
        view.roles.put("workshop", 2);
        view.roles.put("tower", 2);
        List<RulerDecision> decisions = decide(view);
        assertFalse(has(decisions, Kind.BUILD, "tavern"), decisions.toString());
        RulerView known = view.copy();
        known.knownRoles = new java.util.ArrayList<>(known.knownRoles);
        known.knownRoles.add("tavern");
        known.builds.add(new RulerView.Option("tavern", "kingdomworld:tavern", "", 0, true));
        assertTrue(has(decide(known), Kind.BUILD, "tavern"), "a tavern once the role is known");
    }

    @Test
    void withoutAPlaceTheRulerClaimsLandAndSaves() {
        RulerView view = calm();
        view.bedsUsed = 14;
        view.builds.replaceAll(o -> o.role().equals("house") ? new RulerView.Option("house", o.template(), "", 0, false) : o);
        RulerMind.Plan plan = RulerMind.decide(view, new RulerMemory(), RULES);
        assertTrue(has(plan.decisions(), Kind.CLAIM, null), plan.decisions().toString());
        assertFalse(has(plan.decisions(), Kind.BUILD, "house"));
        assertTrue(plan.needs().stream().anyMatch(n -> n.kind() == RulerMemory.NeedKind.BUILDING && n.subject().equals("house")));
    }

    @Test
    void aRulerGrowsItsLandALittleEveryTurnUpToItsLimit() {
        RulerView merchant = calm();
        // A builder claims more on its own every few turns; any ruler grows a little.
        merchant.character = RulerCharacter.MERCHANT;
        RulerDecision claim = decide(merchant).stream().filter(d -> d.kind() == Kind.CLAIM).findFirst().orElseThrow();
        assertEquals(RULES.growthChunks(), claim.amount(), "a few chunks a turn");
        RulerView full = calm();
        full.chunks = full.maxChunks;
        assertFalse(has(decide(full), Kind.CLAIM, null), "no growth past the limit");
        RulerView nothingFree = calm();
        nothingFree.landToClaim = false;
        assertFalse(has(decide(nothingFree), Kind.CLAIM, null), "no growth without free land next to it");
        List<RulerDecision> off = RulerMind.decide(merchant, new RulerMemory(), RULES.withGrowthChunks(0)).decisions();
        assertFalse(has(off, Kind.CLAIM, null), "growth turned off: " + off);
    }

    @Test
    void theLandOfAKingdomStopsAtItsLimit() {
        RulerView view = calm();
        view.bedsUsed = 14;
        view.chunks = 96;
        view.builds.replaceAll(o -> new RulerView.Option(o.role(), o.template(), "", 0, false));
        RulerMind.Plan plan = RulerMind.decide(view, new RulerMemory(), RULES);
        assertFalse(has(plan.decisions(), Kind.CLAIM, null), plan.decisions().toString());
        assertTrue(plan.needs().stream().anyMatch(n -> n.kind() == RulerMemory.NeedKind.LAND));
        RulerView almost = calm();
        almost.chunks = 94;
        almost.bedsUsed = 14;
        almost.builds.replaceAll(o -> new RulerView.Option(o.role(), o.template(), "", 0, false));
        RulerDecision claim = decide(almost).stream().filter(d -> d.kind() == Kind.CLAIM).findFirst().orElseThrow();
        assertEquals(2, claim.amount(), "no more than the limit leaves");
    }

    @Test
    void aBankruptRulerCutsTheArmyAndTheBuildingAndGetsNoMoney() {
        RulerView view = calm();
        view.balance = 50;
        view.income = 20;
        view.expenses = 180;
        view.soldiers = 6;
        view.constructions = 2;
        view.bedsUsed = 14;
        view.subjects = 3;
        view.pollTax = 10;
        view.goods.put("minecraft:cobblestone", new RulerView.Good("minecraft:cobblestone", 900, 64, 1));
        RulerMind.Plan plan = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(10));
        List<RulerDecision> decisions = plan.decisions();
        assertTrue(has(decisions, Kind.DISMISS, null), decisions.toString());
        assertTrue(has(decisions, Kind.PAUSE, null), decisions.toString());
        assertTrue(has(decisions, Kind.TAX, null), decisions.toString());
        assertTrue(has(decisions, Kind.SELL, "minecraft:cobblestone"), decisions.toString());
        Set<Kind> spending = EnumSet.of(Kind.BUILD, Kind.UPGRADE, Kind.HIRE, Kind.CONTRACT, Kind.TOWN, Kind.ROAD, Kind.BUY, Kind.FESTIVAL,
                Kind.CLAIM, Kind.RESEARCH);
        assertTrue(decisions.stream().noneMatch(d -> spending.contains(d.kind())), "nothing that costs: " + decisions);
        assertTrue(plan.needs().stream().anyMatch(n -> n.kind() == RulerMemory.NeedKind.MONEY));
        RulerDecision tax = decisions.stream().filter(d -> d.kind() == Kind.TAX).findFirst().orElseThrow();
        assertTrue(tax.amount() > view.pollTax, "taxes go up");
    }

    @Test
    void aGreedyRulerTaxesMoreThanAJustOne() {
        RulerView greedy = calm();
        greedy.traits = List.of(RulerCharacter.Trait.THRIFTY, RulerCharacter.Trait.AMBITIOUS);
        RulerView just = calm();
        just.traits = List.of(RulerCharacter.Trait.JUST);
        long greedyTax = RulerMind.pollTax(greedy, RULES);
        long justTax = RulerMind.pollTax(just, RULES);
        assertTrue(greedyTax > greedy.defaultPollTax, "greedy " + greedyTax);
        assertTrue(justTax < just.defaultPollTax, "just " + justTax);
        assertTrue(RulerMind.salesTax(greedy, RULES) > RulerMind.salesTax(just, RULES));
        greedy.subjects = 2;
        greedy.pollTax = greedy.defaultPollTax;
        RulerDecision tax = decide(greedy).stream().filter(d -> d.kind() == Kind.TAX).findFirst().orElseThrow();
        assertEquals(greedyTax, tax.amount());
    }

    @Test
    void aThreatLeadsToHiringAndAContractOnTheCamp() {
        RulerView view = calm();
        view.population = 30;
        view.soldiers = 1;
        view.unemployed = 4;
        view.camps.add(new RulerView.Camp("7", 150, 5, 300, false));
        view.camps.add(new RulerView.Camp("9", 900, 8, 300, false));
        RulerMind.Plan plan = RulerMind.decide(view, new RulerMemory(), RULES.withDecisionsPerTurn(5));
        assertTrue(has(plan.decisions(), Kind.HIRE, null), plan.decisions().toString());
        RulerDecision contract = plan.decisions().stream().filter(d -> d.kind() == Kind.CONTRACT).findFirst().orElseThrow();
        assertEquals("7", contract.target(), "the nearest camp");
        RulerView far = calm();
        far.camps.add(new RulerView.Camp("9", 900, 8, 300, false));
        assertFalse(has(decide(far), Kind.CONTRACT, null), "a far camp is no threat");
        RulerView contracted = calm();
        contracted.camps.add(new RulerView.Camp("7", 150, 5, 300, true));
        assertFalse(has(decide(contracted), Kind.CONTRACT, null), "one contract on a camp is enough");
    }

    @Test
    void aFairDealIsTakenAndARobbingOneDeclined() {
        RulerView view = calm();
        view.goods.put("minecraft:oak_log", new RulerView.Good("minecraft:oak_log", 600, 64, 2));
        UUID player = UUID.randomUUID();
        RulerView.Offer fair = new RulerView.Offer(1, player, "Avalon", true, true, "minecraft:oak_log", 64, 128, 128, 600, 0);
        RulerView.Offer robbing = new RulerView.Offer(2, player, "Avalon", true, true, "minecraft:oak_log", 64, 40, 128, 600, 0);
        RulerView.Offer tooMany = new RulerView.Offer(3, player, "Avalon", true, true, "minecraft:oak_log", 600, 1200, 1200, 600, 0);
        RulerMemory memory = new RulerMemory();
        assertTrue(RulerMind.judge(fair, view, memory, RULES).accept());
        RulerMind.Verdict low = RulerMind.judge(robbing, view, memory, RULES);
        assertFalse(low.accept());
        assertEquals(RulerMind.PRICE_LOW, low.reason());
        assertEquals(RulerMind.NEEDED, RulerMind.judge(tooMany, view, memory, RULES).reason(), "the stores keep what the kingdom needs");
        RulerView.Offer dear = new RulerView.Offer(4, player, "Avalon", true, false, "minecraft:bread", 32, 500, 100, 0, 40);
        assertEquals(RulerMind.PRICE_HIGH, RulerMind.judge(dear, view, memory, RULES).reason());
        RulerView.Offer cheap = new RulerView.Offer(5, player, "Avalon", true, false, "minecraft:bread", 32, 100, 100, 0, 40);
        assertTrue(RulerMind.judge(cheap, view, memory, RULES).accept());
        RulerMind.Plan plan = RulerMind.decide(withOffers(view, fair, robbing), memory, RULES);
        assertEquals(2, plan.answers().size(), "answers do not count as decisions");
        assertEquals(Kind.ACCEPT, plan.answers().get(0).kind());
        assertEquals(Kind.DECLINE, plan.answers().get(1).kind());
        assertEquals(RulerMind.PRICE_LOW, plan.answers().get(1).subject());
    }

    private static RulerView withOffers(RulerView view, RulerView.Offer... offers) {
        RulerView copy = view.copy();
        copy.offers.addAll(List.of(offers));
        return copy;
    }

    @Test
    void aRulerMakesNoMoreDecisionsThanTheRulesAllow() {
        RulerView busy = calm();
        busy.bedsUsed = 14;
        busy.storageUsed = 100;
        busy.population = 40;
        busy.unemployed = 5;
        busy.roads = 2;
        busy.camps.add(new RulerView.Camp("1", 100, 4, 200, false));
        busy.damaged.add(5L);
        busy.goods.put("minecraft:cobblestone", new RulerView.Good("minecraft:cobblestone", 900, 64, 1));
        for (int limit = 0; limit <= 4; limit++) {
            List<RulerDecision> decisions = RulerMind.decide(busy, new RulerMemory(), RULES.withDecisionsPerTurn(limit)).decisions();
            assertEquals(limit, decisions.size(), decisions.toString());
            assertEquals(decisions.size(), decisions.stream().map(RulerDecision::kind).distinct().count(), "one of a kind");
        }
    }

    @Test
    void merchantsLetRoadsPassGladlyWarriorsOnlyForFriends() {
        assertTrue(RulerMind.letsPass(RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.PROUD), 0));
        assertFalse(RulerMind.letsPass(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.PATIENT), 0));
        assertTrue(RulerMind.letsPass(RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.PATIENT), 40));
        assertFalse(RulerMind.letsPass(RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.PATIENT), -60));
    }

    @Test
    void theAttitudeFollowsTheLawAndTheDeals() {
        List<RulerCharacter.Trait> traits = List.of(RulerCharacter.Trait.PATIENT);
        assertEquals(RulerMind.Attitude.NEUTRAL, RulerMind.Attitude.of(RulerMind.attitude(0, 0, traits)));
        assertEquals(RulerMind.Attitude.HOSTILE, RulerMind.Attitude.of(RulerMind.attitude(-100, 0, traits)));
        assertEquals(RulerMind.Attitude.WARM, RulerMind.Attitude.of(RulerMind.attitude(10, 20, traits)));
        assertTrue(RulerMind.attitude(0, 0, List.of(RulerCharacter.Trait.GENEROUS)) > RulerMind.attitude(0, 0,
                List.of(RulerCharacter.Trait.PROUD)));
    }

    @Test
    void aMageRulerResearchesAndOthersDoNot() {
        RulerView mage = calm();
        mage.character = RulerCharacter.MAGE;
        mage.tower = true;
        mage.research = "ward";
        assertTrue(has(decide(mage), Kind.RESEARCH, "ward"));
        RulerView builder = calm();
        builder.tower = true;
        builder.research = "ward";
        assertFalse(has(decide(builder), Kind.RESEARCH, null));
    }

    @Test
    void lowSpiritsBringAFeastOnlyWhereFeastsAreHeld() {
        RulerView view = calm();
        view.mood = 10;
        assertFalse(has(decide(view), Kind.FESTIVAL, null));
        view.festivals = true;
        assertTrue(has(decide(view), Kind.FESTIVAL, null));
    }

    @Test
    void aSubjectOfGoodNameBecomesAnHonoraryCouncillorAndNothingMore() {
        RulerView view = calm();
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        view.candidates = List.of(new RulerView.Candidate(low, "Low", 20), new RulerView.Candidate(high, "High", 80));
        view.councillorFree = true;
        List<RulerDecision> decisions = decide(view);
        assertTrue(decisions.stream().anyMatch(d -> d.kind() == Kind.APPOINT && d.subject().equals(RulerMind.COUNCILLOR)
                && d.target().equals(high.toString())), decisions.toString());
        view.councillorFree = false;
        assertFalse(has(decide(view), Kind.APPOINT, null), "one councillor is enough");
        RulerView unknown = calm();
        unknown.candidates = List.of(new RulerView.Candidate(low, "Low", 20));
        unknown.councillorFree = true;
        assertFalse(has(decide(unknown), Kind.APPOINT, null), "a subject of no name is not honoured");
    }
}
