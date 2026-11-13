package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.tactics.SquadTactics;
import io.github.sekerator.kingdomworld.common.tactics.TacticMover;
import io.github.sekerator.kingdomworld.core.tactics.Duty;
import io.github.sekerator.kingdomworld.core.tactics.Spot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BowItem;

import java.util.EnumSet;

/**
 * A robber takes its part in the plan of its camp: it lies in wait crouched until the sign, rings its prey with the others,
 * shoots from its place with a bow, and runs back to the camp with the others when the fight is lost. Without a plan it
 * fights as a monster does.
 */
final class BanditTacticGoal extends Goal {
    /** Ticks between two arrows. */
    private static final int SHOOT_TICKS = 30;
    private static final double SHOOT_RANGE = 20.0;

    private final BanditEntity bandit;
    private final TacticMover mover;
    private int shootCooldown;

    BanditTacticGoal(BanditEntity bandit) {
        this.bandit = bandit;
        this.mover = new TacticMover(bandit);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return SquadTactics.duty(bandit) != null;
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
    public void stop() {
        bandit.setShiftKeyDown(false);
    }

    @Override
    public void tick() {
        mover.tick();
        if (shootCooldown > 0) {
            shootCooldown--;
        }
        Duty duty = SquadTactics.duty(bandit);
        if (duty == null) {
            return;
        }
        switch (duty.role()) {
            case AMBUSH -> {
                // In hiding: still and crouched, watching the way.
                bandit.getNavigation().stop();
                bandit.setShiftKeyDown(true);
                return;
            }
            case RETREAT, FALL_BACK -> {
                bandit.setShiftKeyDown(false);
                if (duty.stand() != null) {
                    mover.walk(duty.stand(), 1.2);
                }
                return;
            }
            default -> bandit.setShiftKeyDown(false);
        }
        LivingEntity aim = duty.target() != null && bandit.level() instanceof ServerLevel level
                && level.getEntity(duty.target()) instanceof LivingEntity found && found.isAlive() ? found : null;
        if (aim == null) {
            return;
        }
        Spot stand = duty.stand() != null ? duty.stand() : TacticMover.spot(bandit);
        if (aim instanceof net.minecraft.world.entity.player.Player && !bandit.getSensing().hasLineOfSight(aim)) {
            // The band knows a player only where one of it last saw the player: the robber goes to its place by that.
            mover.walk(stand, 1.1);
            return;
        }
        if (bandit.getTarget() != aim) {
            bandit.setTarget(aim);
        }
        if (bandit.getMainHandItem().getItem() instanceof BowItem) {
            if (bandit.distanceToSqr(stand.x(), stand.y(), stand.z()) > 4.0) {
                mover.walk(stand, 1.0);
            } else {
                bandit.getNavigation().stop();
            }
            bandit.getLookControl().setLookAt(aim, 30.0f, 30.0f);
            if (shootCooldown <= 0 && bandit.distanceToSqr(aim) < SHOOT_RANGE * SHOOT_RANGE && bandit.getSensing().hasLineOfSight(aim)) {
                shootCooldown = SHOOT_TICKS;
                bandit.performRangedAttack(aim, 1.0f);
            }
            return;
        }
        switch (duty.role()) {
            case ENCIRCLE, FLANK, SHOOT -> mover.strikeFrom(aim, stand, 1.1);
            default -> mover.hold(stand, aim, 1.0);
        }
    }
}
