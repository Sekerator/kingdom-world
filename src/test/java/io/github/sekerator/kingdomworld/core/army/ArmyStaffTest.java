package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branches and posts of soldiers: who serves where, who holds a post, how the fallen are counted and replaced, how old
 * data gets its branches, and what an officer is paid.
 */
class ArmyStaffTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final ForceRules FORCE = ForceRules.DEFAULT;

    private KingdomWorldState world;
    private ArmyState army;
    private Kingdom kingdom;
    private UUID king;
    private UUID subject;
    private ArmyRules rules;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        army = new ArmyState();
        king = UUID.randomUUID();
        subject = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Iron Realm", 0x993333, king, new Capital(OVERWORLD, 8, 64, 8),
                5 * CitizenNeeds.TICKS_PER_DAY, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        world.membership().invite(king, subject, 1L);
        world.membership().acceptInvitation(subject, kingdom.id(), 1L);
        world.membership().setRole(king, subject, Role.SUBJECT);
        rules = new ArmyRules.Builder().build();
    }

    private UUID soldier(long experience) {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, Gender.MALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        world.citizens().add(citizen);
        assertTrue(army.recruit(null, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world).ok());
        army.reward(citizen.id(), experience, 0, 0);
        return citizen.id();
    }

    private Soldier record(UUID id) {
        return army.soldier(id).orElseThrow();
    }

    @Test
    void aRecruitIsAFighterOfTheGuardAndMovesToTheArmyByDecision() {
        UUID id = soldier(0);
        assertEquals(Branch.GUARD, record(id).branch());
        assertEquals(Post.FIGHTER, record(id).post());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), army.setBranch(subject, id, Branch.ARMY, world).failure());
        assertEquals(Optional.of(KingdomError.UNCHANGED), army.setBranch(king, id, Branch.GUARD, world).failure());
        assertTrue(army.setBranch(king, id, Branch.ARMY, world).ok());
        assertEquals(Branch.ARMY, record(id).branch());
        assertEquals(List.of(), army.branchOf(kingdom.id(), Branch.GUARD));
        assertEquals(Optional.of(KingdomError.NOT_A_SOLDIER), army.setBranch(king, UUID.randomUUID(), Branch.ARMY, world).failure());
    }

    @Test
    void aBranchHasOneViceCaptainAndOneCaptain() {
        UUID first = soldier(0);
        UUID second = soldier(0);
        UUID third = soldier(0);
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), army.appoint(subject, first, Post.CAPTAIN, world).failure());
        assertTrue(army.appoint(king, first, Post.CAPTAIN, world).ok());
        assertTrue(army.appoint(king, second, Post.VICE_CAPTAIN, world).ok());
        assertEquals(Optional.of(KingdomError.UNCHANGED), army.appoint(king, first, Post.CAPTAIN, world).failure());
        assertEquals(first, army.officer(kingdom.id(), Branch.GUARD, Post.CAPTAIN).orElseThrow().citizenId());
        // A new captain takes the post: the old one serves as a fighter again.
        assertTrue(army.appoint(king, third, Post.CAPTAIN, world).ok());
        assertEquals(Post.FIGHTER, record(first).post());
        assertEquals(third, army.officer(kingdom.id(), Branch.GUARD, Post.CAPTAIN).orElseThrow().citizenId());
        assertEquals(second, army.officer(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN).orElseThrow().citizenId());
        // The army has officers of its own; an officer who changes the branch leaves the post behind.
        assertTrue(army.officer(kingdom.id(), Branch.ARMY, Post.CAPTAIN).isEmpty());
        assertTrue(army.setBranch(king, third, Branch.ARMY, world).ok());
        assertEquals(Post.FIGHTER, record(third).post());
        assertTrue(army.officer(kingdom.id(), Branch.GUARD, Post.CAPTAIN).isEmpty());
        assertTrue(army.appoint(king, third, Post.CAPTAIN, world).ok());
        assertEquals(third, army.officer(kingdom.id(), Branch.ARMY, Post.CAPTAIN).orElseThrow().citizenId());
        assertTrue(army.verify().isEmpty());
        // Taking the post away makes a fighter.
        assertTrue(army.appoint(king, third, Post.FIGHTER, world).ok());
        assertTrue(army.officer(kingdom.id(), Branch.ARMY, Post.CAPTAIN).isEmpty());
    }

    @Test
    void theFallenAreCountedByBranchAndAFallenOfficerLeavesAVacantPost() {
        UUID captain = soldier(900);
        UUID fighter = soldier(0);
        UUID armyFighter = soldier(0);
        army.setBranch(null, armyFighter, Branch.ARMY, world);
        army.appoint(null, captain, Post.CAPTAIN, world);
        army.fell(fighter, 10);
        army.fell(armyFighter, 10);
        assertEquals(1, army.losses(kingdom.id(), Branch.GUARD));
        assertEquals(1, army.losses(kingdom.id(), Branch.ARMY));
        assertTrue(army.vacanciesOf(kingdom.id()).isEmpty(), "a fighter leaves no vacant post");
        army.fell(captain, 11);
        assertEquals(2, army.losses(kingdom.id(), Branch.GUARD));
        assertEquals(Map.of(new ArmyState.Slot(Branch.GUARD, Post.CAPTAIN), 11L), army.vacanciesOf(kingdom.id()));
        assertTrue(army.soldier(captain).isEmpty() && army.soldier(fighter).isEmpty());
        // Replacing takes the fallen off the count, one by one, and never below nothing.
        army.replaced(kingdom.id(), Branch.GUARD);
        army.replaced(kingdom.id(), Branch.GUARD);
        army.replaced(kingdom.id(), Branch.GUARD);
        assertEquals(0, army.losses(kingdom.id(), Branch.GUARD));
        assertEquals(1, army.losses(kingdom.id(), Branch.ARMY));
        // A soldier who is dismissed did not fall.
        UUID dismissed = soldier(0);
        assertTrue(army.dismiss(null, dismissed, world).ok());
        assertEquals(0, army.losses(kingdom.id(), Branch.GUARD));
    }

    @Test
    void theBestFighterTakesTheVacantPostAfterTheDaysOfMourning() {
        UUID captain = soldier(2_000);
        UUID veteran = soldier(700);
        UUID recruit = soldier(0);
        UUID corporal = soldier(300);
        UUID private1 = soldier(100);
        UUID private2 = soldier(100);
        army.appoint(null, captain, Post.CAPTAIN, world);
        assertFalse(army.postOpen(kingdom.id(), Branch.GUARD, Post.CAPTAIN, 20, FORCE), "the post is held");
        assertTrue(army.postOpen(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN, 20, FORCE), "a post nobody fell from is open at once");
        army.fell(captain, 20);
        assertFalse(army.postOpen(kingdom.id(), Branch.GUARD, Post.CAPTAIN, 20, FORCE));
        assertFalse(army.postOpen(kingdom.id(), Branch.GUARD, Post.CAPTAIN, 22, FORCE));
        assertTrue(army.postOpen(kingdom.id(), Branch.GUARD, Post.CAPTAIN, 23, FORCE));
        assertEquals(veteran, army.successor(kingdom.id(), Branch.GUARD, Post.CAPTAIN).orElseThrow().citizenId());
        assertTrue(army.appoint(null, veteran, Post.CAPTAIN, world).ok());
        assertTrue(army.vacanciesOf(kingdom.id()).isEmpty(), "a filled post is no longer vacant");
        // The vice-captain rises to captain; otherwise experience decides, then kills.
        army.appoint(null, recruit, Post.VICE_CAPTAIN, world);
        army.fell(veteran, 30);
        assertEquals(recruit, army.successor(kingdom.id(), Branch.GUARD, Post.CAPTAIN).orElseThrow().citizenId());
        assertEquals(corporal, army.successor(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN).orElseThrow().citizenId());
        army.fell(corporal, 30);
        army.reward(private2, 0, 3, 0);
        assertEquals(private2, army.successor(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN).orElseThrow().citizenId());
        // A branch too small has no officers.
        assertEquals(3, army.branchOf(kingdom.id(), Branch.GUARD).size());
        assertFalse(army.postOpen(kingdom.id(), Branch.GUARD, Post.CAPTAIN, 100, FORCE));
        assertTrue(army.successor(kingdom.id(), Branch.ARMY, Post.CAPTAIN).isEmpty());
        assertEquals(private1, private1);
    }

    @Test
    void soldiersOfOldDataGetTheirBranchByTheOrderOfTheirSquad() {
        UUID holding = soldier(0);
        UUID guarding = soldier(0);
        UUID patrolling = soldier(0);
        UUID marching = soldier(0);
        UUID idle = soldier(0);
        UUID inSquadWithoutOrder = soldier(0);
        WorldPos point = new WorldPos(OVERWORLD, 40, 70, 8);
        long hold = army.formSquad(null, kingdom.id(), List.of(holding), rules, world).squad().orElseThrow().id();
        long guard = army.formSquad(null, kingdom.id(), List.of(guarding), rules, world).squad().orElseThrow().id();
        long patrol = army.formSquad(null, kingdom.id(), List.of(patrolling), rules, world).squad().orElseThrow().id();
        long move = army.formSquad(null, kingdom.id(), List.of(marching), rules, world).squad().orElseThrow().id();
        army.formSquad(null, kingdom.id(), List.of(inSquadWithoutOrder), rules, world);
        assertTrue(army.setOrder(null, hold, Order.hold(point), OVERWORLD, world).ok());
        assertTrue(army.setOrder(null, guard, Order.guard(point, 1), OVERWORLD, world).ok());
        assertTrue(army.setOrder(null, patrol, Order.NONE.withPatrolPoint(point).withPatrolPoint(new WorldPos(OVERWORLD, 60, 70, 8)),
                OVERWORLD, world).ok());
        assertTrue(army.setOrder(null, move, Order.move(point), OVERWORLD, world).ok());
        army.appoint(null, idle, Post.CAPTAIN, world);
        assertEquals(3, army.assignBranchesByOrders());
        assertEquals(Branch.GUARD, record(holding).branch());
        assertEquals(Branch.GUARD, record(guarding).branch());
        assertEquals(Branch.GUARD, record(patrolling).branch());
        assertEquals(Branch.ARMY, record(marching).branch());
        assertEquals(Branch.ARMY, record(idle).branch());
        assertEquals(Branch.ARMY, record(inSquadWithoutOrder).branch());
        for (Soldier soldier : army.soldiersOf(kingdom.id())) {
            assertEquals(Post.FIGHTER, soldier.post(), "old data knows no officers");
        }
    }

    @Test
    void anOfficerIsPaidMoreThanAFighter() {
        assertEquals(1_000, FORCE.wagePermille(Post.FIGHTER));
        assertTrue(FORCE.wagePermille(Post.VICE_CAPTAIN) > 1_000);
        assertTrue(FORCE.wagePermille(Post.CAPTAIN) > FORCE.wagePermille(Post.VICE_CAPTAIN));
        UUID captain = soldier(0);
        UUID fighter = soldier(0);
        army.appoint(null, captain, Post.CAPTAIN, world);
        EconomyRules economy = EconomyRules.DEFAULT;
        assertTrue(world.economy().setWage(kingdom.id(), Professions.WARRIOR.id(), 15, 1_000, economy).ok());
        long wage = world.economy().treasury(kingdom.id()).orElseThrow().wage(Professions.WARRIOR.id(), economy);
        assertEquals(15, wage);
        assertTrue(world.economy().adminTreasury(kingdom.id(), 1_000_000, 10).ok());
        // The first payday of a treasury only starts the count of days.
        world.payday(kingdom.id(), 10, economy);
        Economy.Payday payday = world.payday(kingdom.id(), 11, economy,
                id -> army.soldier(id).map(soldier -> FORCE.wagePermille(soldier.post())).orElse(1_000));
        assertEquals(wage, payday.pay().get(fighter));
        assertEquals(wage * 5, payday.pay().get(captain));
        // Paid in full, both are as content as each other: an officer measures its pay against the wage of its post.
        assertEquals(payday.percents().get(fighter), payday.percents().get(captain));
        assertEquals(wage, new Economy.WageClaim(fighter, "warrior").scaled(wage));
        assertEquals(wage * 3, new Economy.WageClaim(fighter, "warrior", 3_000).scaled(wage));
    }

    @Test
    void theFullHealthOfACitizenFollowsItsPostAndKeepsItsShare() {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, Gender.MALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        assertEquals(Citizen.MAX_HEALTH, citizen.maxHealth());
        citizen.setHealth(100.0f, 0.5f);
        assertEquals(20.0f, citizen.health(), "a citizen who is no soldier has the health of a citizen");
        citizen.setMaxHealth(120.0f);
        assertEquals(120.0f, citizen.health(), "a healthy citizen stays healthy when its full health grows");
        citizen.setHealth(60.0f, 0.5f);
        assertEquals(CitizenState.HEALTHY, citizen.state());
        citizen.setHealth(59.0f, 0.5f);
        assertEquals(CitizenState.WOUNDED, citizen.state(), "wounds are measured against the full health of the post");
        citizen.setMaxHealth(30.0f);
        assertEquals(14.75f, citizen.health(), 1e-4f, "and a wounded one stays as wounded as it was");
        citizen.setHealth(500.0f, 0.5f);
        assertEquals(30.0f, citizen.health());
        citizen.setMaxHealth(Citizen.MAX_HEALTH);
        assertEquals(20.0f, citizen.health());
    }
}
