package io.github.sekerator.kingdomworld.common.tactics;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyGear;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.road.BanditEntity;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.tactics.Duty;
import io.github.sekerator.kingdomworld.core.tactics.Fighter;
import io.github.sekerator.kingdomworld.core.tactics.Foe;
import io.github.sekerator.kingdomworld.core.tactics.Plan;
import io.github.sekerator.kingdomworld.core.tactics.Spot;
import io.github.sekerator.kingdomworld.core.tactics.SquadPlanner;
import io.github.sekerator.kingdomworld.core.tactics.TacticRules;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The squads that fight near players and their leaders. Every few ticks it looks at who fights beside whom: the soldiers of
 * a squad, the soldiers of a kingdom who answer an alarm for the same offender, the adventurers of a squad of the guild or
 * of one place, the robbers of a camp. A squad with an enemy in sight gets a plan from its leader after a short moment to
 * react, and again every 10 to 20 ticks by the skill of the leader ({@link SquadPlanner}); its bodies read their duties and
 * carry them out with their own goals. A squad sees only what one of its fighters has in its line of sight. The decisions
 * of all squads share a budget of time in a tick; the squads beyond it decide in the next tick.
 */
public final class SquadTactics {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks between two looks at who fights beside whom. */
    private static final int GATHER_TICKS = 10;
    /** Blocks around the enemies a squad fights within which it looks for more of them. */
    private static final double AROUND = 8.0;
    private static final int MAX_FOES = 24;
    /** How many of the nearest fighters look for an enemy before the squad counts it unseen. */
    private static final int SIGHT_CHECKS = 4;
    /** Robbers wait in hiding until an enemy comes this close, one of them is hurt, or this many ticks pass. */
    private static final double AMBUSH_RANGE = 8.0;
    private static final int AMBUSH_TICKS = 600;
    /** A squad falls back towards help no further than this many blocks at a time. */
    private static final double RALLY_STEP = 24.0;

    enum Kind {
        SOLDIERS, ADVENTURERS, ROBBERS
    }

    /**
     * One squad in the eyes of its leader.
     */
    private static final class Group {
        final String key;
        final Kind kind;
        final UUID kingdomId;
        ServerLevel level;
        final List<UUID> members = new ArrayList<>();
        long firstSeen = -1;
        long next;
        int startSize;
        boolean pressedTold;
        Plan plan = Plan.NONE;

        Group(String key, Kind kind, UUID kingdomId) {
            this.key = key;
            this.kind = kind;
            this.kingdomId = kingdomId;
        }
    }

    private record Assigned(Duty duty, long until) {
    }

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    private static final Map<UUID, Assigned> DUTIES = new HashMap<>();
    private static long lastGather = Long.MIN_VALUE;
    private static int cursor;
    /** Measures of the decisions, for the operator and the client check. */
    private static long decisions;
    private static long decisionNanos;
    private static long worstTickNanos;

    private SquadTactics() {
    }

    public static void reset() {
        GROUPS.clear();
        DUTIES.clear();
        lastGather = Long.MIN_VALUE;
        cursor = 0;
        decisions = 0;
        decisionNanos = 0;
        worstTickNanos = 0;
    }

    // ---------------------------------------------------------------- what bodies read

    /**
     * The duty of a body in the plan of its squad now, or {@code null}: no fight, the tactics are off, or the plan is old.
     */
    public static Duty duty(Entity body) {
        Assigned assigned = DUTIES.get(body.getUUID());
        MinecraftServer server = body.getServer();
        if (assigned == null || server == null) {
            return null;
        }
        if (assigned.until() < server.getTickCount() || !TacticSettings.enabled()) {
            DUTIES.remove(body.getUUID());
            return null;
        }
        return assigned.duty();
    }

    /**
     * Whether a body fights by the plan of its squad now.
     */
    public static boolean fightsByPlan(Entity body) {
        return duty(body) != null;
    }

    /** Decisions made, their time in all and the worst tick of decisions, in nanoseconds: for measures. */
    public static long[] measures() {
        return new long[]{decisions, decisionNanos, worstTickNanos};
    }

    // ---------------------------------------------------------------- every tick

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 == 0) {
            TacticSettings.refresh();
        }
        if (!TacticSettings.enabled()) {
            if (!GROUPS.isEmpty() || !DUTIES.isEmpty()) {
                GROUPS.clear();
                DUTIES.clear();
            }
            return;
        }
        long started = System.nanoTime();
        if (lastGather == Long.MIN_VALUE || now - lastGather >= GATHER_TICKS || now < lastGather) {
            gather(server);
            lastGather = now;
        }
        decide(server, now, started);
        worstTickNanos = Math.max(worstTickNanos, System.nanoTime() - started);
    }

    /**
     * Who fights beside whom: the bodies of soldiers, adventurers and robbers by their squads.
     */
    private static void gather(MinecraftServer server) {
        Map<String, Group> fresh = new LinkedHashMap<>();
        ArmyState army = ArmyWork.isReadOnly(server) ? null : ArmyWork.state(server);
        for (CitizenEntity body : CitizenBodies.bodies()) {
            if (!body.isAlive() || body.kingdomId() == null || body.citizenId() == null || !(body.level() instanceof ServerLevel level)) {
                continue;
            }
            String profession = body.professionId();
            if (Professions.isMilitary(profession) && army != null) {
                Optional<Soldier> soldier = army.soldier(body.citizenId());
                if (soldier.isEmpty()) {
                    continue;
                }
                add(fresh, soldierKey(army, body), Kind.SOLDIERS, body.kingdomId(), level, body);
            } else if (profession.equals(Professions.ADVENTURER.id()) && !GuildWork.isReadOnly(server)) {
                String key = GuildWork.state(server).squadOf(body.citizenId()).map(squad -> "guild:" + squad.id())
                        .orElse("adventurers:" + body.kingdomId() + ":" + (body.getBlockX() >> 5) + ":" + (body.getBlockZ() >> 5));
                add(fresh, key, Kind.ADVENTURERS, body.kingdomId(), level, body);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : BanditWork.living()) {
                if (level.getEntity(id) instanceof BanditEntity bandit && bandit.isAlive()) {
                    add(fresh, "camp:" + bandit.campId(), Kind.ROBBERS, null, level, bandit);
                }
            }
        }
        // A squad keeps what it knew of its fight.
        for (Group group : fresh.values()) {
            Group old = GROUPS.get(group.key);
            if (old != null) {
                group.firstSeen = old.firstSeen;
                group.next = old.next;
                group.startSize = old.startSize;
                group.pressedTold = old.pressedTold;
                group.plan = old.plan;
            }
        }
        Set<UUID> members = new HashSet<>();
        fresh.values().forEach(group -> members.addAll(group.members));
        DUTIES.keySet().retainAll(members);
        GROUPS.clear();
        GROUPS.putAll(fresh);
    }

    private static void add(Map<String, Group> groups, String key, Kind kind, UUID kingdomId, ServerLevel level, Mob body) {
        Group group = groups.computeIfAbsent(key, k -> new Group(k, kind, kingdomId));
        group.level = level;
        group.members.add(body.getUUID());
    }

    /**
     * The squad a soldier fights in: all soldiers of a kingdom answering an alarm for one offender fight as one squad; the
     * others fight with their squad, or with the soldiers of their kingdom about the same place.
     */
    private static String soldierKey(ArmyState army, CitizenEntity body) {
        LivingEntity target = body.getTarget();
        if (target instanceof Player) {
            return "alarm:" + body.kingdomId() + ":" + target.getUUID();
        }
        Optional<ArmySquad> squad = army.squadOf(body.citizenId());
        if (squad.isPresent()) {
            return "squad:" + squad.get().id();
        }
        return "soldiers:" + body.kingdomId() + ":" + (body.getBlockX() >> 5) + ":" + (body.getBlockZ() >> 5);
    }

    /**
     * The squads whose time has come decide, while the budget of the tick lasts; at least one decides each tick.
     */
    private static void decide(MinecraftServer server, long now, long started) {
        if (GROUPS.isEmpty()) {
            return;
        }
        TacticRules rules = TacticSettings.rules();
        long budget = rules.budgetMicros * 1_000L;
        List<Group> groups = new ArrayList<>(GROUPS.values());
        int count = groups.size();
        int done = 0;
        for (int i = 0; i < count; i++) {
            Group group = groups.get((cursor + i) % count);
            if (group.next > now) {
                continue;
            }
            if (done > 0 && System.nanoTime() - started > budget) {
                // The rest decide in the next tick, the first of them first.
                cursor = (cursor + i) % count;
                return;
            }
            long begun = System.nanoTime();
            try {
                plan(server, group, now, rules);
            } catch (RuntimeException e) {
                LOGGER.error("KingdomWorld tactics: the squad {} could not decide", group.key, e);
                group.next = now + 100;
            }
            decisions++;
            decisionNanos += System.nanoTime() - begun;
            done++;
        }
        cursor = 0;
    }

    // ---------------------------------------------------------------- a decision

    private static void plan(MinecraftServer server, Group group, long now, TacticRules rules) {
        ServerLevel level = group.level;
        List<Mob> bodies = new ArrayList<>();
        for (UUID id : group.members) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                bodies.add(mob);
            }
        }
        Set<UUID> ours = new HashSet<>();
        bodies.forEach(body -> ours.add(body.getUUID()));
        // The enemies its fighters fight, then the others around them that one of the squad sees.
        Set<LivingEntity> seen = new LinkedHashSet<>();
        for (Mob body : bodies) {
            LivingEntity target = body.getTarget();
            if (target != null && target.isAlive() && target.level() == level && hostile(server, group, target, ours)) {
                seen.add(target);
            }
        }
        if (seen.isEmpty()) {
            end(group, now);
            return;
        }
        if (group.firstSeen < 0) {
            // A moment to see what is going on before the first orders.
            group.firstSeen = now;
            group.startSize = bodies.size();
            group.pressedTold = false;
            group.next = now + rules.reactionTicks;
            return;
        }
        group.startSize = Math.max(group.startSize, bodies.size());
        AABB around = null;
        for (LivingEntity foe : seen) {
            around = around == null ? foe.getBoundingBox() : around.minmax(foe.getBoundingBox());
        }
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, around.inflate(AROUND, 4.0, AROUND),
                entity -> entity.isAlive() && !ours.contains(entity.getUUID()))) {
            if (seen.size() >= MAX_FOES) {
                break;
            }
            if (!seen.contains(other) && hostile(server, group, other, ours) && sees(bodies, other, rules.sight)) {
                seen.add(other);
            }
        }
        List<Fighter> own = new ArrayList<>();
        for (Mob body : bodies) {
            own.add(fighter(server, group, body));
        }
        double middleY = bodies.stream().mapToDouble(Mob::getY).average().orElse(0);
        double nearestOwn;
        List<Foe> foes = new ArrayList<>();
        for (LivingEntity foe : seen) {
            nearestOwn = Double.MAX_VALUE;
            for (Mob body : bodies) {
                nearestOwn = Math.min(nearestOwn, horizontal(body, foe));
            }
            foes.add(foe(level, foe, ours, middleY, nearestOwn));
        }
        Plan plan;
        int skill = own.stream().mapToInt(Fighter::skill).max().orElse(0);
        if (group.kind == Kind.ROBBERS && waits(group, bodies, seen, now)) {
            plan = SquadPlanner.ambush(own);
        } else {
            Spot rally = rally(server, group, bodies);
            double baseY = seen.stream().mapToDouble(LivingEntity::getY).average().orElse(middleY);
            plan = SquadPlanner.plan(own, group.startSize, foes, rally, new WorldGround(level, baseY), rules, group.kind == Kind.ROBBERS);
        }
        group.plan = plan;
        int interval = rules.decideTicks(group.kind == Kind.ROBBERS ? 0 : skill);
        group.next = now + interval;
        long until = now + interval * 2L + 5;
        for (Duty duty : plan.duties()) {
            DUTIES.put(duty.fighter(), new Assigned(duty, until));
        }
        if (plan.retreat() && group.kind == Kind.SOLDIERS && !group.pressedTold && group.key.startsWith("alarm:")) {
            // The guards fall back from an offender: the relief of the alarm goes a step further at once.
            group.pressedTold = true;
            UUID offender = UUID.fromString(group.key.substring(group.key.lastIndexOf(':') + 1));
            io.github.sekerator.kingdomworld.common.army.ArmyRelief.pressed(group.kingdomId, offender);
        }
    }

    private static void end(Group group, long now) {
        group.members.forEach(DUTIES::remove);
        group.firstSeen = -1;
        group.startSize = 0;
        group.plan = Plan.NONE;
        group.next = now + GATHER_TICKS;
    }

    /**
     * Whether the robbers of a camp still lie in wait: nobody of them was hurt, no enemy came close and not too long passed.
     */
    private static boolean waits(Group group, List<Mob> bodies, Set<LivingEntity> seen, long now) {
        if (now - group.firstSeen > AMBUSH_TICKS) {
            return false;
        }
        for (Mob body : bodies) {
            if (body.getLastHurtByMob() != null && body.tickCount - body.getLastHurtByMobTimestamp() < 200) {
                return false;
            }
            for (LivingEntity foe : seen) {
                if (body.distanceToSqr(foe) < AMBUSH_RANGE * AMBUSH_RANGE) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a squad may fight an entity: soldiers whom the law and war let them, adventurers monsters, robbers players they
     * prey on, merchants and whoever fights them.
     */
    private static boolean hostile(MinecraftServer server, Group group, LivingEntity entity, Set<UUID> ours) {
        if (ours.contains(entity.getUUID())) {
            return false;
        }
        return switch (group.kind) {
            case SOLDIERS -> !(entity instanceof CitizenEntity citizen && group.kingdomId.equals(citizen.kingdomId()))
                    && ArmyWork.mayAttack(server, group.kingdomId, entity);
            case ADVENTURERS -> entity instanceof Enemy && entity instanceof Mob && !(entity instanceof CitizenEntity);
            case ROBBERS -> !(entity instanceof BanditEntity) && (entity instanceof Player player && BanditWork.mayAttack(player)
                    || entity instanceof io.github.sekerator.kingdomworld.common.road.CaravanMerchantEntity
                    || entity instanceof Mob mob && mob.getTarget() instanceof BanditEntity);
        };
    }

    /**
     * Whether one of the nearest fighters of a squad sees an entity within the sight of the squad.
     */
    private static boolean sees(List<Mob> bodies, LivingEntity entity, double sight) {
        List<Mob> nearest = new ArrayList<>(bodies);
        nearest.sort(java.util.Comparator.comparingDouble(body -> body.distanceToSqr(entity)));
        for (int i = 0; i < Math.min(SIGHT_CHECKS, nearest.size()); i++) {
            Mob body = nearest.get(i);
            if (body.distanceToSqr(entity) > sight * sight) {
                return false;
            }
            if (body.getSensing().hasLineOfSight(entity)) {
                return true;
            }
        }
        return false;
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- what the leader knows

    private static Fighter fighter(MinecraftServer server, Group group, Mob body) {
        boolean ranged = body.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
        boolean shield = ArmyGear.isShield(body.getItemBySlot(EquipmentSlot.OFFHAND));
        int skill = 0;
        if (body instanceof CitizenEntity citizen && citizen.citizenId() != null) {
            if (group.kind == Kind.SOLDIERS && !ArmyWork.isReadOnly(server)) {
                ArmyState army = ArmyWork.state(server);
                Optional<Soldier> soldier = army.soldier(citizen.citizenId());
                if (soldier.isPresent()) {
                    Post post = soldier.get().post();
                    skill = post == Post.CAPTAIN ? 3 : post == Post.VICE_CAPTAIN ? 2 : 0;
                    boolean commands = army.squadOf(citizen.citizenId()).map(squad -> citizen.citizenId().equals(squad.commander())).orElse(false);
                    if (skill == 0 && (commands || soldier.get().rank().ordinal() >= SoldierRank.SERGEANT.ordinal())) {
                        skill = 1;
                    }
                }
            } else if (group.kind == Kind.ADVENTURERS && !GuildWork.isReadOnly(server)) {
                // An experienced adventurer leads the others as a sergeant would.
                skill = GuildWork.state(server).hireling(citizen.citizenId()).map(record -> record.rank().atLeast(Rank.C) ? 1 : 0).orElse(0);
            }
        }
        return new Fighter(body.getUUID(), TacticMover.spot(body), body.getHealth(), Math.max(1.0f, body.getMaxHealth()), ranged, shield,
                skill, worth(body), false);
    }

    private static Foe foe(ServerLevel level, LivingEntity entity, Set<UUID> ours, double middleY, double nearestOwn) {
        boolean ranged = entity instanceof RangedAttackMob || entity.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
        boolean engaged = entity instanceof Mob mob && mob.getTarget() != null && ours.contains(mob.getTarget().getUUID())
                || entity.getLastHurtMob() != null && ours.contains(entity.getLastHurtMob().getUUID())
                && entity.tickCount - entity.getLastHurtMobTimestamp() < 100;
        double danger = damage(entity) * (ranged ? 1.3 : 1.0);
        boolean high = entity.getY() - middleY >= 2.5 && nearestOwn <= 6.0;
        return new Foe(entity.getUUID(), TacticMover.spot(entity), entity.getHealth(), Math.max(1.0f, entity.getMaxHealth()), danger,
                worth(entity), ranged, engaged, narrow(level, entity.blockPosition()), high);
    }

    /**
     * What an entity is worth in a fight: its health, more with armor, and its damage.
     */
    static double worth(LivingEntity entity) {
        double armor = entity.getAttributes().hasAttribute(Attributes.ARMOR) ? entity.getAttributeValue(Attributes.ARMOR) : 0.0;
        return entity.getMaxHealth() * (1.0 + Math.min(30.0, armor) / 20.0) + damage(entity) * 4.0;
    }

    private static double damage(LivingEntity entity) {
        return entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? Math.max(1.0, entity.getAttributeValue(Attributes.ATTACK_DAMAGE)) : 2.0;
    }

    /**
     * Whether a place is narrow: few ways round it at the height of the feet, or a roof over it and walls about it.
     */
    static boolean narrow(ServerLevel level, BlockPos at) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos feet = at.offset(dx, 0, dz);
                BlockPos head = feet.above();
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                    open++;
                }
            }
        }
        return open <= 3 || open <= 5 && !level.canSeeSky(at.above());
    }

    /**
     * Where help is, at most a stretch away: the post of a squad, the capital of its kingdom, the camp of robbers.
     */
    private static Spot rally(MinecraftServer server, Group group, List<Mob> bodies) {
        Mob first = bodies.get(0);
        Spot from = TacticMover.spot(first);
        Spot help = null;
        if (group.kind == Kind.ROBBERS) {
            // Robbers run back to their camp, also those of an ambush on a caravan.
            if (first instanceof BanditEntity bandit) {
                help = io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).camp(bandit.campId())
                        .map(camp -> new Spot(camp.x() + 0.5, camp.y(), camp.z() + 0.5)).orElse(null);
            }
            if (help == null && first.hasRestriction()) {
                BlockPos camp = first.getRestrictCenter();
                help = new Spot(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5);
            }
        } else if (group.kingdomId != null) {
            if (group.kind == Kind.SOLDIERS && first instanceof CitizenEntity citizen && citizen.citizenId() != null && !ArmyWork.isReadOnly(server)) {
                Order order = ArmyWork.orderOf(server, citizen.citizenId());
                Optional<WorldPos> anchor = order.anchor();
                if (anchor.isPresent() && anchor.get().dimension().equals(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(group.level))
                        && !group.key.startsWith("alarm:")) {
                    help = new Spot(anchor.get().x() + 0.5, anchor.get().y(), anchor.get().z() + 0.5);
                }
            }
            if (help == null) {
                Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(group.kingdomId).map(Territory::capital);
                if (capital.isPresent() && capital.get().dimension().equals(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(group.level))) {
                    help = new Spot(capital.get().x() + 0.5, capital.get().y(), capital.get().z() + 0.5);
                }
            }
        }
        if (help == null) {
            return null;
        }
        double distance = from.flatDistanceTo(help);
        if (distance < 3.0) {
            // Already at help: there is nowhere further to fall back to.
            return null;
        }
        return distance <= RALLY_STEP ? help : from.toward(from.angleTo(help), RALLY_STEP);
    }

    /**
     * A line about the squads that fight now, for the operator.
     */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Group group : GROUPS.values()) {
            if (group.plan.duties().isEmpty()) {
                continue;
            }
            Map<String, Integer> roles = new LinkedHashMap<>();
            group.plan.duties().forEach(duty -> roles.merge(duty.role().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum));
            lines.add(group.key + ": " + group.plan.formation().name().toLowerCase(java.util.Locale.ROOT) + (group.plan.retreat() ? ", falling back" : "")
                    + ", " + roles);
        }
        return lines;
    }
}
