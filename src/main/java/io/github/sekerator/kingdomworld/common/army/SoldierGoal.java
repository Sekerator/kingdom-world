package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * What a soldier's body does: fight the monsters that threaten (those that hurt it, that attacked the people of its kingdom
 * nearby, that roam the land it holds or guards or that come near the player it follows), otherwise carry out the order of
 * its squad, or train at a barracks. A warrior fights with the vanilla melee goal; an archer keeps its distance and shoots
 * arrows from the bow or crossbow it holds, which nobody can pick up. Players and citizens are never targets in peace.
 */
final class SoldierGoal extends Goal {
    private static final double SEARCH = 16.0;
    private static final int SEARCH_TICKS = 20;
    private static final int EQUIP_TICKS = 600;
    private static final int TRAIN_TICKS = 1_200;
    private static final double SHOOT_RANGE = 15.0;

    private final CitizenEntity body;
    private final boolean archer;
    private final MeleeAttackGoal melee;
    @Nullable
    private LivingEntity target;
    private boolean meleeRunning;
    private int searchCooldown;
    private int equipCooldown = 40;
    private int pathCooldown;
    private int shootCooldown;
    private int trainCooldown = TRAIN_TICKS;
    private int swingCooldown;
    private int patrolIndex;

    SoldierGoal(CitizenEntity body, boolean archer) {
        this.body = body;
        this.archer = archer;
        this.melee = new MeleeAttackGoal(body, 0.8, true);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ProfessionBehaviors.fights(body) && body.kingdomId() != null && body.citizenId() != null && body.serverLevel() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        searchCooldown = 0;
        pathCooldown = 0;
    }

    @Override
    public void stop() {
        disengage();
        body.getNavigation().stop();
    }

    private void disengage() {
        if (meleeRunning) {
            melee.stop();
            meleeRunning = false;
        }
        target = null;
        body.setTarget(null);
        body.setAggressive(false);
    }

    @Override
    public void tick() {
        ServerLevel level = body.serverLevel();
        if (level == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (--equipCooldown <= 0) {
            equipCooldown = EQUIP_TICKS;
            KingdomWorldApi.state(server).citizens().get(body.citizenId()).ifPresent(citizen -> ArmyWork.equip(server, citizen));
        }
        Order order = ArmyWork.orderOf(server, body.citizenId());
        if (target != null && (!target.isAlive() || !ArmyWork.mayAttack(server, body.kingdomId(), target)
                || body.distanceToSqr(target) > SEARCH * SEARCH * 4)) {
            disengage();
        }
        if (--searchCooldown <= 0) {
            searchCooldown = SEARCH_TICKS;
            LivingEntity found = findTarget(server, level, order);
            if (found != null && found != target) {
                disengage();
                target = found;
                body.setTarget(target);
                body.setAggressive(true);
            }
        }
        if (target != null) {
            fight();
            return;
        }
        follow(server, level, order);
    }

    // ---------------------------------------------------------------- fighting

    private void fight() {
        ItemStack weapon = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (archer && ArmyGear.isRanged(weapon)) {
            if (meleeRunning) {
                melee.stop();
                meleeRunning = false;
            }
            shoot(weapon);
            return;
        }
        if (!meleeRunning) {
            melee.start();
            meleeRunning = true;
        }
        melee.tick();
    }

    private void shoot(ItemStack weapon) {
        LivingEntity aim = target;
        if (aim == null) {
            return;
        }
        body.getLookControl().setLookAt(aim, 30.0f, 30.0f);
        double distance = body.distanceToSqr(aim);
        boolean sees = body.getSensing().hasLineOfSight(aim);
        if (distance > SHOOT_RANGE * SHOOT_RANGE || !sees) {
            if (--pathCooldown <= 0) {
                pathCooldown = 10;
                body.getNavigation().moveTo(aim, 0.7);
            }
        } else if (distance < 16.0) {
            // Too close for a bow: step back.
            if (--pathCooldown <= 0) {
                pathCooldown = 10;
                double dx = body.getX() - aim.getX();
                double dz = body.getZ() - aim.getZ();
                body.getNavigation().moveTo(body.getX() + Math.signum(dx) * 4, body.getY(), body.getZ() + Math.signum(dz) * 4, 0.8);
            }
        } else {
            body.getNavigation().stop();
        }
        if (--shootCooldown > 0 || !sees || distance > SHOOT_RANGE * SHOOT_RANGE) {
            return;
        }
        shootCooldown = ArmyGear.isCrossbow(weapon) ? 50 : 30;
        AbstractArrow arrow = ArmyGear.arrow(body, weapon, 1.0f);
        double dx = aim.getX() - body.getX();
        double dy = aim.getY(0.3333) - arrow.getY();
        double dz = aim.getZ() - body.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, ArmyGear.isCrossbow(weapon) ? 2.2f : 1.6f, 4.0f);
        body.swing(InteractionHand.MAIN_HAND);
        body.level().playSound(null, body.getX(), body.getY(), body.getZ(), ArmyGear.isCrossbow(weapon) ? SoundEvents.CROSSBOW_SHOOT
                : SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0f, 1.0f / (body.getRandom().nextFloat() * 0.4f + 0.8f));
        body.level().addFreshEntity(arrow);
        Equipment.wear(Equipment.of(body), EquipmentSlot.MAINHAND, 1, body.getRandom());
    }

    @Nullable
    private LivingEntity findTarget(MinecraftServer server, ServerLevel level, Order order) {
        UUID kingdom = body.kingdomId();
        LivingEntity attacker = body.getLastHurtByMob();
        if (attacker != null && body.tickCount - body.getLastHurtByMobTimestamp() < 200 && body.distanceToSqr(attacker) < SEARCH * SEARCH * 4
                && ArmyWork.mayAttack(server, kingdom, attacker)) {
            return attacker;
        }
        long now = KingdomWorldApi.gameTime(server);
        double alarm = Math.max(SEARCH, ArmySettings.rules().alarmRadius());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        ServerPlayer followed = order.kind() == Order.Kind.FOLLOW ? server.getPlayerList().getPlayer(order.player()) : null;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(alarm, 8.0, alarm),
                mob -> mob.isAlive() && mob instanceof Enemy && !(mob instanceof CitizenEntity));
        for (Mob mob : mobs) {
            if (!ArmyWork.mayAttack(server, kingdom, mob)) {
                continue;
            }
            double distance = body.distanceToSqr(mob);
            boolean wanted = ArmyWork.isAlarmed(kingdom, mob.getUUID(), now);
            if (!wanted && distance <= SEARCH * SEARCH && body.getSensing().hasLineOfSight(mob)) {
                BlockPos at = mob.blockPosition();
                WorldPos pos = new WorldPos(Dimensions.id(level), at.getX(), at.getY(), at.getZ());
                wanted = kingdom.equals(KingdomWorldApi.ownerAt(level, at)) || order.covers(pos)
                        || (followed != null && (mob.getTarget() == followed || mob.distanceToSqr(followed) < 64.0))
                        || order.anchor().map(anchor -> anchor.distanceSqr(pos) < SEARCH * SEARCH).orElse(false);
            }
            if (wanted && distance < bestDistance) {
                best = mob;
                bestDistance = distance;
            }
        }
        if (best == null && followed != null && followed.getLastHurtByMob() != null
                && ArmyWork.mayAttack(server, kingdom, followed.getLastHurtByMob())
                && followed.getLastHurtByMob().distanceToSqr(body) < SEARCH * SEARCH * 4) {
            best = followed.getLastHurtByMob();
        }
        return best;
    }

    // ---------------------------------------------------------------- orders

    private void follow(MinecraftServer server, ServerLevel level, Order order) {
        String dimension = Dimensions.id(level);
        switch (order.kind()) {
            case HOLD -> stayNear(order.points().get(0), dimension, 3.0);
            case GUARD -> stayNear(order.points().get(0), dimension, 6.0 + order.radius() * 8.0);
            case PATROL -> {
                List<WorldPos> route = order.points();
                patrolIndex = Math.floorMod(patrolIndex, route.size());
                WorldPos next = route.get(patrolIndex);
                if (next.dimension().equals(dimension) && body.distanceToSqr(next.x() + 0.5, next.y(), next.z() + 0.5) < 6.0) {
                    patrolIndex = (patrolIndex + 1) % route.size();
                } else {
                    walkTo(next, dimension, 0.6);
                }
            }
            case FOLLOW -> {
                ServerPlayer player = server.getPlayerList().getPlayer(order.player());
                if (player == null || player.level() != level) {
                    return;
                }
                double distance = body.distanceToSqr(player);
                if (distance > 48.0 * 48.0) {
                    // Left far behind in the same world: catch up, as tamed animals do.
                    body.moveTo(player.getX(), player.getY(), player.getZ(), body.getYRot(), body.getXRot());
                    body.getNavigation().stop();
                } else if (distance > 5.0 * 5.0) {
                    if (--pathCooldown <= 0) {
                        pathCooldown = 10;
                        body.getNavigation().moveTo(player, 0.75);
                    }
                }
                body.getLookControl().setLookAt(player, 10.0f, 10.0f);
            }
            default -> train(server, level);
        }
    }

    private void stayNear(WorldPos point, String dimension, double radius) {
        if (!point.dimension().equals(dimension)) {
            return;
        }
        if (body.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5) > radius * radius) {
            walkTo(point, dimension, 0.6);
        }
    }

    private void walkTo(WorldPos point, String dimension, double speed) {
        if (!point.dimension().equals(dimension) || --pathCooldown > 0) {
            return;
        }
        pathCooldown = 40;
        if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
            body.getNavigation().moveTo(point.x() + 0.5, point.y(), point.z() + 0.5, speed);
        }
    }

    /**
     * Without an order a soldier trains at the nearest barracks of its kingdom in reach, swinging its weapon.
     */
    private void train(MinecraftServer server, ServerLevel level) {
        String dimension = Dimensions.id(level);
        WorldPos nearest = null;
        double best = 48.0 * 48.0;
        for (WorldPos barracks : ArmyWork.state(server).barracksOf(body.kingdomId())) {
            if (!barracks.dimension().equals(dimension)) {
                continue;
            }
            double distance = body.distanceToSqr(barracks.x() + 0.5, barracks.y(), barracks.z() + 0.5);
            if (distance < best) {
                best = distance;
                nearest = barracks;
            }
        }
        if (nearest == null) {
            return;
        }
        if (best > 4.0 * 4.0) {
            walkTo(nearest, dimension, 0.55);
            return;
        }
        body.getLookControl().setLookAt(nearest.x() + 0.5, nearest.y() + 0.5, nearest.z() + 0.5);
        if (--swingCooldown <= 0) {
            swingCooldown = 25 + body.getRandom().nextInt(20);
            body.swing(InteractionHand.MAIN_HAND);
        }
        if (--trainCooldown <= 0) {
            trainCooldown = TRAIN_TICKS;
            ArmyWork.state(server).train(body.citizenId(), ArmySettings.rules().trainingPerMinute(), ArmySettings.rules());
        }
    }

    /**
     * Monsters within reach of a position, for checks.
     */
    static List<Mob> monstersNear(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius), mob -> mob.isAlive() && mob instanceof Enemy);
    }
}
