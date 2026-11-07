package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The adventurer: near players guards the kingdom's land, hunting the monsters on it with the weapon and armor it takes
 * from the storehouses (any mod's, like every worker's gear); a wounded adventurer goes to the infirmary like any
 * citizen. Far from players it guards nothing; the guild sends it on expeditions with its squad.
 */
public final class AdventurerBehavior implements ProfessionBehavior {

    /**
     * Whether a body is an adventurer able to fight: it neither flees from monsters nor goes home at night.
     */
    public static boolean fights(CitizenEntity body) {
        return Professions.ADVENTURER.id().equals(body.professionId()) && !body.isWounded();
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new GuardGoal(body);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public boolean risky() {
        return true;
    }

    /**
     * Hunts the nearest monster on the kingdom's land within reach of sight, and whatever attacks the adventurer.
     */
    static final class GuardGoal extends Goal {
        private static final double SEARCH = 16.0;
        private static final int SEARCH_TICKS = 20;
        private static final int EQUIP_TICKS = 600;
        private static final int ATTACK_TICKS = 20;

        private final CitizenEntity body;
        @Nullable
        private LivingEntity target;
        private int searchCooldown;
        private int attackCooldown;
        private int pathCooldown;
        private int equipCooldown;

        GuardGoal(CitizenEntity body) {
            this.body = body;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!fights(body) || body.kingdomId() == null) {
                return false;
            }
            if (--searchCooldown > 0) {
                return false;
            }
            searchCooldown = SEARCH_TICKS;
            target = findTarget();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && fights(body) && body.distanceToSqr(target) < SEARCH * SEARCH * 2.25;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            pathCooldown = 0;
            if (--equipCooldown <= 0) {
                equipCooldown = EQUIP_TICKS / SEARCH_TICKS;
                equip();
            }
            body.setAggressive(true);
            body.setTarget(target instanceof Mob ? target : null);
        }

        @Override
        public void stop() {
            target = null;
            body.setTarget(null);
            body.setAggressive(false);
            body.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            body.getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distance = body.distanceToSqr(target);
            double reach = body.getBbWidth() * 2.0 * body.getBbWidth() * 2.0 + target.getBbWidth();
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            if (distance > reach) {
                if (--pathCooldown <= 0) {
                    pathCooldown = 10;
                    body.getNavigation().moveTo(target, 0.75);
                }
                return;
            }
            if (attackCooldown <= 0) {
                attackCooldown = ATTACK_TICKS;
                body.swing(InteractionHand.MAIN_HAND);
                body.doHurtTarget(target);
            }
        }

        /**
         * Takes a weapon and armor from the storehouses if the adventurer has none.
         */
        private void equip() {
            ServerLevel level = body.serverLevel();
            if (level == null || body.kingdomId() == null || KingdomWorldApi.isReadOnly(level.getServer())) {
                return;
            }
            Equipment.Hands hands = Equipment.of(body);
            WorldPos near = new WorldPos(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), body.getBlockX(), body.getBlockY(),
                    body.getBlockZ());
            Equipment.equipTool(level.getServer(), body.kingdomId(), hands, EquipmentSlot.MAINHAND, Equipment.ToolNeed.WEAPON, 0, near);
            Equipment.equipArmor(level.getServer(), body.kingdomId(), hands, near);
        }

        @Nullable
        private LivingEntity findTarget() {
            ServerLevel level = body.serverLevel();
            if (level == null) {
                return null;
            }
            LivingEntity attacker = body.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && attacker instanceof Enemy && body.tickCount - body.getLastHurtByMobTimestamp() < 200
                    && body.distanceToSqr(attacker) < SEARCH * SEARCH) {
                return attacker;
            }
            UUID kingdom = body.kingdomId();
            List<Mob> monsters = level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(SEARCH, 6.0, SEARCH),
                    mob -> mob.isAlive() && mob instanceof Enemy && !(mob instanceof CitizenEntity));
            Mob best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Mob mob : monsters) {
                BlockPos at = mob.blockPosition();
                if (!kingdom.equals(KingdomWorldApi.ownerAt(level, at)) || !body.getSensing().hasLineOfSight(mob)) {
                    continue;
                }
                double distance = body.distanceToSqr(mob);
                if (distance < bestDistance) {
                    best = mob;
                    bestDistance = distance;
                }
            }
            return best;
        }
    }
}
