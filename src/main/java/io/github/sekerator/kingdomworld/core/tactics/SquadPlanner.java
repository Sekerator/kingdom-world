package io.github.sekerator.kingdomworld.core.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The leader of a squad looks at a fight and gives every fighter a duty. Against one enemy the blades take the sectors of a
 * ring around it (or hold the ways out of a narrow place, or wait at the foot of what it climbed); against a group a led
 * squad holds a front with its shields and sends wings round the sides; archers keep their distance with a free line of
 * fire; the badly wounded fall back behind the front with a guard; a squad that is losing falls back in order towards
 * help. A squad without an officer fights more simply: the enemy nearest its leader first and no wings. Nothing here
 * looks at the world: the server says what the squad sees and how the ground is.
 */
public final class SquadPlanner {
    /** Blocks between fighters standing side by side in a line. */
    private static final double SPACING = 1.6;
    /** Blocks an archer moves sideways to clear its line of fire, and how close to a friend a shot may pass. */
    private static final double SIDESTEP = 2.0;
    private static final double CLEARANCE = 0.9;
    /** The turn, in radians, an archer moves round its target to find a free line of fire: 15 degrees, up to 60. */
    private static final double ARC_STEP = Math.PI / 12;

    private SquadPlanner() {
    }

    /**
     * The plan of a squad.
     *
     * @param own       its fighters that stand and see the fight
     * @param startSize how many fighters it had when the fight began, for its losses
     * @param foes      the enemies it sees
     * @param rally     where help is (the post of the relief, the walls), for falling back; {@code null} if nowhere
     * @param simple    whether the squad fights simply whoever leads it (robbers, a crowd of adventurers)
     */
    public static Plan plan(List<Fighter> own, int startSize, List<Foe> foes, Spot rally, Ground ground, TacticRules rules, boolean simple) {
        if (own.isEmpty() || foes.isEmpty()) {
            return Plan.NONE;
        }
        Fighter leader = leader(own);
        int skill = simple ? 0 : leader.skill();
        Spot us = Spot.middle(own.stream().map(Fighter::spot).toList(), leader.spot());
        Spot them = Spot.middle(foes.stream().map(Foe::spot).toList(), foes.get(0).spot());
        if (rally != null && losing(own, startSize, foes, rules)) {
            return retreat(leader, own, rally, us, them, ground);
        }
        List<Duty> duties = new ArrayList<>();
        double away = them.angleTo(us);
        // The badly wounded fall back behind the front, the weakest first and never more than half of the squad.
        List<Fighter> able = new ArrayList<>(own);
        List<Fighter> wounded = new ArrayList<>();
        if (own.size() > 1) {
            List<Fighter> hurt = own.stream().filter(f -> f.share() < rules.woundedShare).sorted(Comparator.comparingDouble(Fighter::share)
                    .thenComparing(Fighter::id)).toList();
            for (Fighter fighter : hurt) {
                if (wounded.size() * 2 + 2 <= own.size()) {
                    wounded.add(fighter);
                }
            }
            able.removeAll(wounded);
        }
        Spot shelter = us.toward(away, rules.fallBack);
        for (int i = 0; i < wounded.size(); i++) {
            Fighter fighter = wounded.get(i);
            duties.add(new Duty(fighter.id(), Role.FALL_BACK, null, place(ground, shelter.toward(away + Math.PI / 2, (i - (wounded.size() - 1) / 2.0) * SPACING), fighter)));
        }
        Foe focus = focus(foes, leader, skill);
        // Healers tend the wounded; with nobody wounded they fight like the others.
        if (!wounded.isEmpty()) {
            for (Fighter healer : List.copyOf(able)) {
                if (healer.healer()) {
                    able.remove(healer);
                    Fighter worst = wounded.get(0);
                    duties.add(new Duty(healer.id(), Role.HEAL, worst.id(), place(ground, shelter.toward(away, 1.0), healer)));
                }
            }
        }
        List<Fighter> blades = new ArrayList<>(able.stream().filter(f -> !f.ranged()).toList());
        List<Fighter> archers = able.stream().filter(Fighter::ranged).toList();
        // A guard over the wounded: a shield if there is one, from a squad that can spare it.
        if (!wounded.isEmpty() && blades.size() >= 3) {
            Fighter guard = blades.stream().filter(Fighter::shield).findFirst()
                    .orElse(blades.stream().min(Comparator.comparingDouble(f -> f.spot().distanceSq(shelter))).orElseThrow());
            blades.remove(guard);
            Foe nearest = nearest(foes, shelter);
            duties.add(new Duty(guard.id(), Role.GUARD_WOUNDED, nearest.id(), place(ground, shelter.toward(shelter.angleTo(nearest.spot()), 2.0), guard)));
        }
        Plan.Formation formation;
        boolean pincer = skill >= 1 && foes.size() >= rules.pincerFoes && blades.size() >= 4 && blades.size() >= foes.size();
        if (pincer) {
            pincer(duties, blades, foes, focus, us, them, skill, ground, rules);
            formation = Plan.Formation.PINCER;
        } else {
            ring(duties, blades, focus, us, ground, rules);
            formation = archers.isEmpty() ? Plan.Formation.RING : Plan.Formation.LINE;
        }
        shoot(duties, archers, foes, focus, us, own, skill, ground, rules);
        return new Plan(leader.id(), duties, focus.id(), formation, false);
    }

    /**
     * The squad lies in wait where each fighter stands.
     */
    public static Plan ambush(List<Fighter> own) {
        if (own.isEmpty()) {
            return Plan.NONE;
        }
        List<Duty> duties = new ArrayList<>();
        own.forEach(fighter -> duties.add(new Duty(fighter.id(), Role.AMBUSH, null, fighter.spot())));
        return new Plan(leader(own).id(), duties, null, Plan.Formation.NONE, false);
    }

    /** The one who leads: the best leader, then the strongest. */
    public static Fighter leader(List<Fighter> own) {
        return own.stream().max(Comparator.comparingInt(Fighter::skill).thenComparingDouble(Fighter::worth)
                .thenComparing(Fighter::id, Comparator.reverseOrder())).orElseThrow();
    }

    /**
     * Whether a squad is losing: it lost a large share of its fighters, or the enemies it sees are worth much more.
     */
    public static boolean losing(List<Fighter> own, int startSize, List<Foe> foes, TacticRules rules) {
        double ours = own.stream().mapToDouble(Fighter::worth).sum();
        double theirs = foes.stream().mapToDouble(Foe::worth).sum();
        boolean lost = startSize >= 3 && startSize - own.size() >= Math.ceil(startSize * rules.retreatLosses);
        boolean outmatched = theirs > 0 && ours < theirs * rules.retreatBalance;
        return lost || outmatched;
    }

    /**
     * The enemy the squad strikes first. A led squad weighs how dangerous an enemy is against how long it takes to bring
     * down, and prefers one that already fights the squad; without an officer it is the enemy nearest the leader.
     */
    public static Foe focus(List<Foe> foes, Fighter leader, int skill) {
        if (skill <= 0) {
            return nearest(foes, leader.spot());
        }
        return foes.stream().max(Comparator.comparingDouble((Foe foe) -> score(foe, leader, skill)).thenComparing(Foe::id)).orElseThrow();
    }

    private static double score(Foe foe, Fighter leader, int skill) {
        double danger = foe.danger() * (foe.ranged() ? 1.3 : 1.0) * (foe.engaged() ? 1.25 : 1.0);
        // Quick to bring down is worth more: the health it has left.
        double effort = Math.max(1.0, foe.health());
        // A captain weighs the way to it better: far ones count less.
        double distance = foe.spot().distanceTo(leader.spot()) * (skill >= 3 ? 0.05 : 0.02);
        return danger / effort - distance;
    }

    // ---------------------------------------------------------------- shapes

    /**
     * The blades around one enemy: each its own sector of a ring, the first sectors facing the squad, so nobody crowds in one
     * spot and the back of the enemy is closed. Fighters keep their order around the enemy, so they do not cross each
     * other's way.
     */
    private static void ring(List<Duty> duties, List<Fighter> blades, Foe target, Spot us, Ground ground, TacticRules rules) {
        if (blades.isEmpty()) {
            return;
        }
        Role role = target.high() ? Role.WAIT_BELOW : target.cornered() ? Role.HOLD_EXIT : Role.ENCIRCLE;
        double radius = target.cornered() ? rules.exitRadius : rules.ringRadius;
        List<Spot> sectors = sectors(target.spot(), target.spot().angleTo(us), blades.size(), radius);
        assign(duties, blades, sectors, target, role, ground);
    }

    /**
     * The places of a ring of {@code count} sectors around a center, the first towards {@code facing}.
     */
    public static List<Spot> sectors(Spot center, double facing, int count, double radius) {
        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            spots.add(center.toward(facing + 2 * Math.PI * i / count, radius));
        }
        return spots;
    }

    /**
     * Gives fighters places so that their order around the enemy stays: both sorted by their angle around it.
     */
    private static void assign(List<Duty> duties, List<Fighter> fighters, List<Spot> places, Foe target, Role role, Ground ground) {
        Spot center = target.spot();
        List<Fighter> byAngle = new ArrayList<>(fighters);
        byAngle.sort(Comparator.comparingDouble((Fighter f) -> center.angleTo(f.spot())).thenComparing(Fighter::id));
        List<Spot> placesByAngle = new ArrayList<>(places);
        placesByAngle.sort(Comparator.comparingDouble(center::angleTo));
        // Turn the order of the places so that the first fighter gets the nearest one, keeping the order around.
        int shift = 0;
        double best = Double.MAX_VALUE;
        for (int s = 0; s < placesByAngle.size(); s++) {
            double total = 0;
            for (int i = 0; i < byAngle.size(); i++) {
                total += byAngle.get(i).spot().distanceSq(placesByAngle.get((i + s) % placesByAngle.size()));
            }
            if (total < best - 1e-9) {
                best = total;
                shift = s;
            }
        }
        // A place over lava or a drop on one side of the enemy, or a block another fighter of the ring has, is moved to the
        // nearest free place round the enemy one can stand on: falling back halfway to the fighter would bring several of them
        // to the same few blocks by the edge, where they push each other in.
        java.util.Set<Long> taken = new java.util.HashSet<>();
        List<Spot> pool = null;
        for (int i = 0; i < byAngle.size(); i++) {
            Fighter fighter = byAngle.get(i);
            Spot place = placesByAngle.get((i + shift) % placesByAngle.size());
            Spot stand = ground.settle(place);
            if (stand == null || taken.contains(cell(stand))) {
                if (pool == null) {
                    pool = ringPool(ground, center, center.flatDistanceTo(place));
                }
                stand = nearestFree(pool, place, taken);
                if (stand == null) {
                    stand = place(ground, place, fighter);
                }
            }
            taken.add(cell(stand));
            duties.add(new Duty(fighter.id(), role, target.id(), stand));
        }
    }

    /** Directions round an enemy looked at for free places of its ring, and blocks out from the ring they reach. */
    private static final int RING_ANGLES = 24;
    private static final int RING_OUT = 2;

    /**
     * The places one can stand on round an enemy at the radius of its ring and a little further out, looked at once a plan and
     * only when a place of the ring cannot be stood on.
     */
    private static List<Spot> ringPool(Ground ground, Spot center, double radius) {
        List<Spot> pool = new ArrayList<>();
        for (int out = 0; out <= RING_OUT; out++) {
            for (int k = 0; k < RING_ANGLES; k++) {
                Spot settled = ground.settle(center.toward(2 * Math.PI * k / RING_ANGLES, radius + out));
                if (settled != null) {
                    pool.add(settled);
                }
            }
        }
        return pool;
    }

    /**
     * The place of the pool nearest to the one wanted on a block nobody was given; null if every one is taken.
     */
    private static Spot nearestFree(List<Spot> pool, Spot wanted, java.util.Set<Long> taken) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Spot spot : pool) {
            double distance = wanted.distanceSq(spot);
            if (distance < bestDistance && !taken.contains(cell(spot))) {
                best = spot;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The block column a place stands in. */
    private static long cell(Spot spot) {
        return ((long) Math.floor(spot.x()) << 32) ^ ((long) Math.floor(spot.z()) & 0xFFFFFFFFL);
    }

    /**
     * Against a group: half of the blades hold a front facing it, shields first, and strike the focus; the others split into
     * two wings that go round its sides (with an officer, round to its back) and strike the enemies there.
     */
    private static void pincer(List<Duty> duties, List<Fighter> blades, List<Foe> foes, Foe focus, Spot us, Spot them, int skill,
                               Ground ground, TacticRules rules) {
        double axis = us.angleTo(them);
        List<Fighter> order = new ArrayList<>(blades);
        order.sort(Comparator.comparing((Fighter f) -> !f.shield()).thenComparingDouble(f -> f.spot().distanceSq(them)).thenComparing(Fighter::id));
        int frontCount = (order.size() + 1) / 2;
        List<Fighter> front = new ArrayList<>(order.subList(0, frontCount));
        List<Fighter> wings = new ArrayList<>(order.subList(frontCount, order.size()));
        Spot line = them.toward(axis + Math.PI, rules.ringRadius + 1.0);
        for (int i = 0; i < front.size(); i++) {
            double offset = (i - (front.size() - 1) / 2.0) * SPACING;
            Fighter fighter = front.get(i);
            duties.add(new Duty(fighter.id(), Role.HOLD_FRONT, focus.id(), place(ground, line.toward(axis + Math.PI / 2, offset), fighter)));
        }
        // The wings: a captain or vice-captain closes the pincer behind the enemy, a sergeant takes its sides.
        double swing = skill >= 2 ? Math.PI * 0.75 : Math.PI / 2;
        double[] sides = {axis - Math.PI + swing, axis + Math.PI - swing};
        wings.sort(Comparator.comparingDouble((Fighter f) -> Math.sin(us.angleTo(f.spot()) - axis)).thenComparing(Fighter::id));
        for (int i = 0; i < wings.size(); i++) {
            Fighter fighter = wings.get(i);
            int side = i < (wings.size() + 1) / 2 ? 0 : 1;
            Spot wing = them.toward(sides[side], rules.flankRadius);
            Foe target = nearest(foes, wing);
            int rank = side == 0 ? i : i - (wings.size() + 1) / 2;
            Spot place = wing.toward(sides[side] + Math.PI / 2, rank * SPACING * 0.75);
            duties.add(new Duty(fighter.id(), Role.FLANK, target.id(), place(ground, place, fighter)));
        }
    }

    /**
     * Archers: each at a good distance from its target on the side of the squad, side by side, stepping aside until no friend
     * stands in the line of fire, higher ground preferred; one an enemy came close to steps back to the far end of its range.
     */
    private static void shoot(List<Duty> duties, List<Fighter> archers, List<Foe> foes, Foe focus, Spot us, List<Fighter> own, int skill,
                              Ground ground, TacticRules rules) {
        if (archers.isEmpty()) {
            return;
        }
        // A led squad shoots the enemy that fights one of its own; else the focus.
        Foe target = focus;
        if (skill >= 1) {
            target = foes.stream().filter(Foe::engaged).max(Comparator.comparingDouble(Foe::danger).thenComparing(Foe::id)).orElse(focus);
        }
        List<Spot> friends = new ArrayList<>();
        for (Duty duty : duties) {
            if (duty.stand() != null && duty.role() != Role.FALL_BACK) {
                friends.add(duty.stand());
            }
        }
        double back = target.spot().angleTo(us);
        for (int i = 0; i < archers.size(); i++) {
            Fighter archer = archers.get(i);
            boolean pressed = nearest(foes, archer.spot()).spot().distanceTo(archer.spot()) < rules.archerTooClose;
            double distance = pressed ? rules.archerMax : Math.max(rules.archerMin, Math.min(rules.archerMax, archer.spot().distanceTo(target.spot())));
            // Archers spread over an arc around the target on the side of the squad, and move along it to shoot between friends.
            double spread = (i - (archers.size() - 1) / 2.0) * SIDESTEP / distance;
            Spot best = null;
            double bestScore = -Double.MAX_VALUE;
            for (int step = 0; step <= 8; step++) {
                double turn = step == 0 ? 0 : (step % 2 == 1 ? 1 : -1) * ((step + 1) / 2) * ARC_STEP;
                Spot candidate = ground.settle(target.spot().toward(back + spread + turn, distance));
                if (candidate == null) {
                    continue;
                }
                boolean clear = clear(candidate, target.spot(), friends);
                double score = (clear ? 100.0 : 0.0) + ground.vantage(candidate) - step * 0.5;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) {
                best = archer.spot();
            }
            friends.add(best);
            duties.add(new Duty(archer.id(), Role.SHOOT, target.id(), best));
        }
    }

    /**
     * Whether a shot from one place to another passes no friend closer than the clearance.
     */
    public static boolean clear(Spot from, Spot to, List<Spot> friends) {
        for (Spot friend : friends) {
            if (friend.distanceSq(from) < 0.01) {
                continue;
            }
            if (segmentDistance(from, to, friend) < CLEARANCE && friend.distanceSq(to) > CLEARANCE * CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    private static double segmentDistance(Spot a, Spot b, Spot p) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = dx * dx + dz * dz;
        double t = length == 0 ? 0 : ((p.x() - a.x()) * dx + (p.z() - a.z()) * dz) / length;
        t = Math.max(0, Math.min(1, t));
        double x = a.x() + t * dx - p.x();
        double z = a.z() + t * dz - p.z();
        return Math.sqrt(x * x + z * z);
    }

    /**
     * Falling back in order: everyone towards help, the shields last as a rear guard facing the enemy.
     */
    private static Plan retreat(Fighter leader, List<Fighter> own, Spot rally, Spot us, Spot them, Ground ground) {
        List<Duty> duties = new ArrayList<>();
        double way = us.angleTo(rally);
        double side = way + Math.PI / 2;
        List<Fighter> order = new ArrayList<>(own);
        // The shields go last, between the squad and the enemy.
        order.sort(Comparator.comparing(Fighter::shield).thenComparing(Fighter::id));
        for (int i = 0; i < order.size(); i++) {
            Fighter fighter = order.get(i);
            int row = i / 3;
            int column = i % 3 - 1;
            Spot place = rally.toward(way + Math.PI, row * SPACING).toward(side, column * SPACING);
            duties.add(new Duty(fighter.id(), Role.RETREAT, null, place(ground, place, fighter)));
        }
        return new Plan(leader.id(), duties, null, Plan.Formation.COLUMN, true);
    }

    // ---------------------------------------------------------------- helpers

    private static Foe nearest(List<Foe> foes, Spot spot) {
        return foes.stream().min(Comparator.comparingDouble((Foe foe) -> foe.spot().distanceSq(spot)).thenComparing(Foe::id)).orElseThrow();
    }

    /**
     * A place a fighter can stand on near the wanted one, or where it stands if there is none: never lava, a drop or deep water.
     */
    private static Spot place(Ground ground, Spot wanted, Fighter fighter) {
        Spot settled = ground.settle(wanted);
        if (settled != null) {
            return settled;
        }
        // Try a little closer to where the fighter stands before giving up the place.
        Spot halfway = new Spot((wanted.x() + fighter.spot().x()) / 2, wanted.y(), (wanted.z() + fighter.spot().z()) / 2);
        settled = ground.settle(halfway);
        return settled != null ? settled : fighter.spot();
    }

    /** The ids of the fighters with a role. */
    public static List<UUID> withRole(Plan plan, Role role) {
        return plan.duties().stream().filter(duty -> duty.role() == role).map(Duty::fighter).toList();
    }
}
