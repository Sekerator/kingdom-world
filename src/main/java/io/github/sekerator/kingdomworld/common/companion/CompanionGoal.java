package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.army.ArmyGear;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
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
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a companion's body does, with the fighting of soldiers: fight the monsters that threaten (those that hurt it or its
 * owner, that its owner fights, that come near the place it guards, or for a companion on a contract the monsters of the
 * owner's contracts nearby), otherwise carry out the order: follow the owner, stand, guard a place. With a melee weapon or
 * bare hands it fights with the vanilla melee goal; with only a bow or crossbow and arrows in the pack it keeps its
 * distance and shoots, an arrow from the pack for every shot. Players, citizens and other companions are never targets.
 */
final class CompanionGoal extends Goal {
    private static final double SEARCH = 16.0;
    private static final double GUARD = 12.0;
    private static final int SEARCH_TICKS = 20;
    private static final double SHOOT_RANGE = 15.0;

    private final CompanionEntity body;
    private final MeleeAttackGoal melee;
    @Nullable
    private LivingEntity target;
    private boolean meleeRunning;
    private int searchCooldown;
    private int pathCooldown;
    private int shootCooldown;

    CompanionGoal(CompanionEntity body) {
        this.body = body;
        this.melee = new MeleeAttackGoal(body, 0.85, true);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return body.companionId() != null && body.owner() != null && body.serverLevel() != null;
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
        Optional<Companion> record = CompanionWork.state(server).companion(body.companionId());
        if (record.isEmpty()) {
            return;
        }
        CompanionOrder order = record.get().order();
        ServerPlayer owner = server.getPlayerList().getPlayer(body.owner());
        if (target != null && (!target.isAlive() || !mayAttack(target) || body.distanceToSqr(target) > SEARCH * SEARCH * 4)) {
            disengage();
        }
        if (--searchCooldown <= 0) {
            searchCooldown = SEARCH_TICKS;
            LivingEntity found = findTarget(server, level, order, record.get(), owner);
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
        follow(level, order, record.get(), owner);
    }

    /**
     * Whether a companion may attack something: a monster only, never a player, a citizen or another companion.
     */
    static boolean mayAttack(LivingEntity target) {
        return target.isAlive() && target instanceof Enemy && target instanceof Mob
                && !(target instanceof io.github.sekerator.kingdomworld.common.citizen.CitizenEntity) && !(target instanceof CompanionEntity);
    }

    // ---------------------------------------------------------------- fighting

    private void fight() {
        ItemStack weapon = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (ArmyGear.isRanged(weapon)) {
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
                body.getNavigation().moveTo(aim, 0.75);
            }
        } else if (distance < 16.0) {
            if (--pathCooldown <= 0) {
                pathCooldown = 10;
                double dx = body.getX() - aim.getX();
                double dz = body.getZ() - aim.getZ();
                body.getNavigation().moveTo(body.getX() + Math.signum(dx) * 4, body.getY(), body.getZ() + Math.signum(dz) * 4, 0.85);
            }
        } else {
            body.getNavigation().stop();
        }
        if (--shootCooldown > 0 || !sees || distance > SHOOT_RANGE * SHOOT_RANGE) {
            return;
        }
        CompanionBodies.Pack pack = CompanionBodies.pack(body);
        if (pack == null) {
            return;
        }
        if (!CompanionGear.takeArrow(pack.container(), pack.usable())) {
            // Out of arrows: the companion takes a melee weapon or fights with bare hands.
            CompanionGear.equip(body, pack.container(), pack.usable());
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
        CompanionGear.wearWeapon(body);
    }

    @Nullable
    private LivingEntity findTarget(MinecraftServer server, ServerLevel level, CompanionOrder order, Companion record,
                                    @Nullable ServerPlayer owner) {
        LivingEntity attacker = body.getLastHurtByMob();
        if (attacker != null && body.tickCount - body.getLastHurtByMobTimestamp() < 200 && body.distanceToSqr(attacker) < SEARCH * SEARCH * 4
                && mayAttack(attacker)) {
            return attacker;
        }
        boolean withOwner = owner != null && owner.level() == level && !order.atPlace();
        if (withOwner) {
            LivingEntity threat = owner.getLastHurtByMob();
            if (threat != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && mayAttack(threat)
                    && threat.distanceToSqr(body) < SEARCH * SEARCH * 4) {
                return threat;
            }
            LivingEntity fought = owner.getLastHurtMob();
            if (fought != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && mayAttack(fought)
                    && fought.distanceToSqr(body) < SEARCH * SEARCH * 4) {
                return fought;
            }
        }
        Predicate<Mob> wanted;
        WorldPos post = record.orderPos().orElse(null);
        switch (order) {
            case GUARD -> wanted = mob -> post != null && post.dimension().equals(Dimensions.id(level))
                    && mob.distanceToSqr(post.x() + 0.5, post.y(), post.z() + 0.5) < GUARD * GUARD;
            case CONTRACT -> {
                Predicate<LivingEntity> counts = owner == null ? m -> false : CompanionWork.contractTargets(server, owner);
                wanted = mob -> mob.getTarget() == body || owner != null && mob.getTarget() == owner || counts.test(mob);
            }
            case FOLLOW -> wanted = mob -> mob.getTarget() == body || owner != null && mob.getTarget() == owner;
            default -> wanted = mob -> mob.getTarget() == body;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(SEARCH, 8.0, SEARCH),
                mob -> mob.isAlive() && mayAttack(mob));
        for (Mob mob : mobs) {
            double distance = body.distanceToSqr(mob);
            if (distance < bestDistance && wanted.test(mob) && body.getSensing().hasLineOfSight(mob)) {
                best = mob;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- orders

    private void follow(ServerLevel level, CompanionOrder order, Companion record, @Nullable ServerPlayer owner) {
        String dimension = Dimensions.id(level);
        if (order.atPlace()) {
            record.orderPos().ifPresent(place -> stayNear(place, dimension, order == CompanionOrder.STAND ? 1.5 : 5.0));
            return;
        }
        if (owner == null || owner.level() != level) {
            return;
        }
        double distance = body.distanceToSqr(owner);
        if (distance > CompanionBodies.CATCH_UP * CompanionBodies.CATCH_UP) {
            // Left far behind: catch up, as tamed animals do.
            var spot = CompanionBodies.besideOwner(level, owner);
            if (spot != null) {
                body.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, body.getYRot(), body.getXRot());
                body.getNavigation().stop();
            }
        } else if (distance > 4.0 * 4.0) {
            if (--pathCooldown <= 0) {
                pathCooldown = 10;
                body.getNavigation().moveTo(owner, distance > 12.0 * 12.0 ? 1.0 : 0.8);
            }
        } else if (distance < 2.5 * 2.5) {
            body.getNavigation().stop();
        }
        body.getLookControl().setLookAt(owner, 10.0f, 10.0f);
    }

    private void stayNear(WorldPos point, String dimension, double radius) {
        if (!point.dimension().equals(dimension)) {
            return;
        }
        if (body.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5) > radius * radius && --pathCooldown <= 0) {
            pathCooldown = 40;
            if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
                body.getNavigation().moveTo(point.x() + 0.5, point.y(), point.z() + 0.5, 0.7);
            }
        }
    }
}
