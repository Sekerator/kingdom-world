package io.github.sekerator.kingdomworld.core.tactics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadPlannerTest {
    private static final TacticRules RULES = TacticRules.DEFAULT;
    private static int next;

    private static UUID id() {
        return new UUID(0, ++next);
    }

    private static Fighter blade(double x, double z) {
        return new Fighter(id(), new Spot(x, 64, z), 30, 30, false, false, 0, 10);
    }

    private static Fighter shield(double x, double z) {
        return new Fighter(id(), new Spot(x, 64, z), 30, 30, false, true, 0, 10);
    }

    private static Fighter archer(double x, double z) {
        return new Fighter(id(), new Spot(x, 64, z), 30, 30, true, false, 0, 8);
    }

    private static Fighter led(Fighter fighter, int skill) {
        return new Fighter(fighter.id(), fighter.spot(), fighter.health(), fighter.full(), fighter.ranged(), fighter.shield(), skill,
                fighter.strength());
    }

    private static Foe foe(double x, double z, double danger, double health) {
        return new Foe(id(), new Spot(x, 64, z), health, 20, danger, 10, false, false, false, false);
    }

    private static List<Fighter> blades(int count, double x, double z) {
        List<Fighter> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(blade(x + i * 0.5, z + (i % 2)));
        }
        return list;
    }

    @Test
    void sixBladesTakeSixSectorsAroundOneEnemy() {
        List<Fighter> own = blades(6, -10, 0);
        Foe target = foe(0, 0, 4, 20);
        Plan plan = SquadPlanner.plan(own, 6, List.of(target), null, Ground.OPEN, RULES, false);
        assertEquals(Plan.Formation.RING, plan.formation());
        assertEquals(6, plan.count(Role.ENCIRCLE));
        List<Spot> places = plan.duties().stream().map(Duty::stand).toList();
        for (Spot place : places) {
            assertEquals(RULES.ringRadius, place.flatDistanceTo(target.spot()), 1e-6);
        }
        // No two in one place: neighbours of a ring of six stand a radius apart.
        for (int i = 0; i < places.size(); i++) {
            for (int j = i + 1; j < places.size(); j++) {
                assertTrue(places.get(i).distanceTo(places.get(j)) > RULES.ringRadius * 0.9, places.toString());
            }
        }
        // Someone stands behind the enemy, on the far side from the squad.
        assertTrue(places.stream().anyMatch(p -> p.x() > 1.5), "the back of the enemy is open: " + places);
    }

    @Test
    void theRingKeepsTheOrderOfTheFightersSoTheyDoNotCrossEachOther() {
        List<Fighter> own = List.of(blade(-5, -5), blade(-5, 5), blade(5, 5), blade(5, -5));
        Foe target = foe(0, 0, 4, 20);
        Map<UUID, Duty> duties = SquadPlanner.plan(own, 4, List.of(target), null, Ground.OPEN, RULES, false).byFighter();
        double total = 0;
        for (Fighter fighter : own) {
            total += fighter.spot().distanceTo(duties.get(fighter.id()).stand());
        }
        // Each goes to the sector on its own side: about the way from where it stands to the ring.
        double straight = 4 * (Math.sqrt(50) - RULES.ringRadius);
        assertTrue(total < straight * 1.3, total + " against " + straight);
    }

    @Test
    void aLedSquadStrikesTheDangerousAndWeakFirstAndAPlainOneTheNearest() {
        Fighter sergeant = led(blade(-6, 0), 1);
        Foe near = foe(-3, 0, 2, 20);
        Foe deadly = foe(4, 3, 8, 6);
        assertEquals(deadly.id(), SquadPlanner.focus(List.of(near, deadly), sergeant, 1).id());
        assertEquals(near.id(), SquadPlanner.focus(List.of(near, deadly), sergeant, 0).id());
        List<Fighter> own = new ArrayList<>(blades(3, -8, 0));
        own.add(sergeant);
        assertEquals(deadly.id(), SquadPlanner.plan(own, 4, List.of(near, deadly), null, Ground.OPEN, RULES, false).focus());
        assertEquals(near.id(), SquadPlanner.plan(own, 4, List.of(near, deadly), null, Ground.OPEN, RULES, true).focus());
    }

    @Test
    void aLedSquadHoldsAFrontAgainstAGroupAndSendsWingsRoundItsSides() {
        List<Fighter> own = new ArrayList<>();
        own.add(led(shield(-12, 0), 2));
        own.add(shield(-12, 1));
        own.addAll(blades(6, -12, -2));
        List<Foe> foes = List.of(foe(0, -1, 4, 20), foe(0, 1, 4, 20), foe(1, 0, 4, 20));
        Plan plan = SquadPlanner.plan(own, own.size(), foes, null, Ground.OPEN, RULES, false);
        assertEquals(Plan.Formation.PINCER, plan.formation());
        assertEquals(4, plan.count(Role.HOLD_FRONT));
        assertEquals(4, plan.count(Role.FLANK));
        // The shields are in the front.
        for (Fighter fighter : own) {
            if (fighter.shield()) {
                assertEquals(Role.HOLD_FRONT, plan.duty(fighter.id()).orElseThrow().role());
            }
        }
        // The wings go to both sides of the group, and with a vice-captain past its middle, to its back.
        Set<Boolean> sides = new HashSet<>();
        for (Duty duty : plan.duties()) {
            if (duty.role() == Role.FLANK) {
                sides.add(duty.stand().z() > 0);
                assertTrue(duty.stand().x() > -0.5, "a wing stays in front: " + duty.stand());
            }
        }
        assertEquals(2, sides.size());
        // Without an officer the same squad only rings the nearest enemy.
        List<Fighter> plain = new ArrayList<>(own);
        plain.set(0, led(plain.get(0), 0));
        assertEquals(Plan.Formation.RING, SquadPlanner.plan(plain, plain.size(), foes, null, Ground.OPEN, RULES, false).formation());
    }

    @Test
    void archersKeepTheirDistanceAndAFreeLineOfFire() {
        List<Fighter> own = new ArrayList<>(blades(3, -6, 0));
        own.add(archer(-20, 0));
        own.add(archer(-20, 2));
        Foe target = foe(0, 0, 4, 20);
        Plan plan = SquadPlanner.plan(own, 5, List.of(target), null, Ground.OPEN, RULES, false);
        List<Spot> blades = new ArrayList<>();
        plan.duties().stream().filter(d -> d.role() == Role.ENCIRCLE).forEach(d -> blades.add(d.stand()));
        for (Duty duty : plan.duties()) {
            if (duty.role() == Role.SHOOT) {
                double distance = duty.stand().flatDistanceTo(target.spot());
                assertTrue(distance >= RULES.archerMin - 0.5 && distance <= RULES.archerMax + 0.5, "distance " + distance);
                assertTrue(SquadPlanner.clear(duty.stand(), target.spot(), blades), "a friend stands in the line of fire of " + duty.stand());
            }
        }
        assertEquals(2, plan.count(Role.SHOOT));
        // An archer an enemy came close to steps back to the far end of its range.
        Fighter pressed = archer(-2, 0);
        Plan back = SquadPlanner.plan(List.of(blade(-3, 1), blade(-3, -1), pressed), 3, List.of(target), null, Ground.OPEN, RULES, false);
        assertTrue(back.duty(pressed.id()).orElseThrow().stand().flatDistanceTo(target.spot()) >= RULES.archerMax - 0.5);
    }

    @Test
    void theBadlyWoundedFallBackBehindTheFrontWithAGuardAndNoMoreThanHalf() {
        List<Fighter> own = new ArrayList<>(blades(4, -6, 0));
        Fighter hurt = new Fighter(id(), new Spot(-2, 64, 0), 4, 30, false, false, 0, 10);
        Fighter shieldBearer = shield(-6, 2);
        own.add(hurt);
        own.add(shieldBearer);
        Foe target = foe(0, 0, 4, 20);
        Plan plan = SquadPlanner.plan(own, 6, List.of(target), null, Ground.OPEN, RULES, false);
        Duty back = plan.duty(hurt.id()).orElseThrow();
        assertEquals(Role.FALL_BACK, back.role());
        assertTrue(back.stand().flatDistanceTo(target.spot()) > hurt.spot().flatDistanceTo(target.spot()) + 2, "the wounded did not fall back");
        assertEquals(Role.GUARD_WOUNDED, plan.duty(shieldBearer.id()).orElseThrow().role());
        // A squad of scratched fighters does not run as a whole: at most half fall back.
        List<Fighter> scratched = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            scratched.add(new Fighter(id(), new Spot(-4, 64, i), 3, 30, false, false, 0, 10));
        }
        Plan few = SquadPlanner.plan(scratched, 4, List.of(foe(0, 0, 1, 2)), null, Ground.OPEN, RULES, false);
        assertTrue(few.count(Role.FALL_BACK) <= 2, "too many fell back: " + few.count(Role.FALL_BACK));
        assertTrue(few.count(Role.ENCIRCLE) >= 2, "nobody is left to fight");
        assertFalse(few.retreat());
    }

    @Test
    void aHealerTendsTheWounded() {
        Fighter healer = new Fighter(id(), new Spot(-8, 64, 0), 20, 20, false, false, 0, 5, true);
        Fighter hurt = new Fighter(id(), new Spot(-2, 64, 0), 3, 30, false, false, 0, 10);
        List<Fighter> own = new ArrayList<>(blades(3, -5, 0));
        own.add(healer);
        own.add(hurt);
        Plan plan = SquadPlanner.plan(own, 5, List.of(foe(0, 0, 3, 20)), null, Ground.OPEN, RULES, false);
        Duty heal = plan.duty(healer.id()).orElseThrow();
        assertEquals(Role.HEAL, heal.role());
        assertEquals(hurt.id(), heal.target());
    }

    @Test
    void aLosingSquadFallsBackInOrderTowardsHelpWithShieldsLast() {
        List<Fighter> own = List.of(blade(-4, 0), shield(-4, 1), blade(-4, 2));
        List<Foe> foes = new ArrayList<>();
        // Nine enemies as strong as each of the three: far more than the squad can hold.
        for (int i = 0; i < 9; i++) {
            foes.add(foe(2, i, 6, 20));
        }
        Spot help = new Spot(-60, 64, 0);
        Plan plan = SquadPlanner.plan(own, 3, foes, help, Ground.OPEN, RULES, false);
        assertTrue(plan.retreat());
        assertEquals(3, plan.count(Role.RETREAT));
        for (Duty duty : plan.duties()) {
            assertTrue(duty.stand().distanceTo(help) < 6, "a fighter does not fall back to the help: " + duty.stand());
        }
        // The shield stands last, nearest the enemy.
        Duty shieldDuty = plan.duty(own.get(1).id()).orElseThrow();
        assertTrue(plan.duties().stream().allMatch(d -> d.stand().x() <= shieldDuty.stand().x() + 1e-9));
        // Without help to fall back to it fights on; with heavy losses it falls back too.
        assertFalse(SquadPlanner.plan(own, 3, foes, null, Ground.OPEN, RULES, false).retreat());
        assertTrue(SquadPlanner.losing(own.subList(0, 2), 6, List.of(foe(2, 0, 1, 20)), RULES));
        assertFalse(SquadPlanner.losing(own, 3, List.of(foe(2, 0, 1, 20)), RULES));
    }

    @Test
    void nobodyIsSentIntoLavaOrOverADrop() {
        // Everything east of x = 1 is lava: the ring there is not stood on.
        Ground lava = spot -> spot.x() > 1.0 ? null : spot;
        List<Fighter> own = blades(6, -8, 0);
        Plan plan = SquadPlanner.plan(own, 6, List.of(foe(0, 0, 4, 20)), null, lava, RULES, false);
        for (Duty duty : plan.duties()) {
            assertNotNull(duty.stand());
            assertTrue(duty.stand().x() <= 1.0, "sent into lava: " + duty.stand());
        }
    }

    @Test
    void aTargetInANarrowPlaceIsWaitedForAtItsWaysOutAndOneUpAPillarAtItsFoot() {
        List<Fighter> own = blades(4, -8, 0);
        Foe cornered = new Foe(id(), new Spot(0, 64, 0), 20, 20, 4, 10, false, false, true, false);
        Plan plan = SquadPlanner.plan(own, 4, List.of(cornered), null, Ground.OPEN, RULES, false);
        assertEquals(4, plan.count(Role.HOLD_EXIT));
        plan.duties().forEach(duty -> assertEquals(RULES.exitRadius, duty.stand().flatDistanceTo(cornered.spot()), 1e-6));
        Foe high = new Foe(id(), new Spot(0, 72, 0), 20, 20, 4, 10, false, false, false, true);
        assertEquals(4, SquadPlanner.plan(own, 4, List.of(high), null, Ground.OPEN, RULES, false).count(Role.WAIT_BELOW));
    }

    @Test
    void aBlowFromTheSideOrTheBackLandsHarder() {
        assertEquals(1.0, RULES.sideMultiplier(0), 1e-9);
        assertEquals(1.0, RULES.sideMultiplier(-45), 1e-9);
        assertEquals(1.0 + RULES.flankBonus, RULES.sideMultiplier(90), 1e-9);
        assertEquals(1.0 + RULES.rearBonus, RULES.sideMultiplier(180), 1e-9);
        assertEquals(1.0 + RULES.rearBonus, RULES.sideMultiplier(-170), 1e-9);
        assertEquals(1.0 + RULES.flankBonus, RULES.sideMultiplier(300), 1e-9);
    }

    @Test
    void anAmbushWaitsWhereItStandsAndACaptainDecidesMoreOften() {
        List<Fighter> own = blades(3, 0, 0);
        Plan plan = SquadPlanner.ambush(own);
        assertEquals(3, plan.count(Role.AMBUSH));
        plan.duties().forEach(duty -> assertFalse(duty.role().attacks()));
        assertTrue(RULES.decideTicks(3) < RULES.decideTicks(1) && RULES.decideTicks(1) < RULES.decideTicks(0));
        assertTrue(RULES.decideTicks(0) <= 20 && RULES.decideTicks(3) >= 10);
    }
}
