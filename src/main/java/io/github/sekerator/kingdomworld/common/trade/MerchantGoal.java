package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A merchant near players: walks to the nearest market stall in the zone of its post and keeps it, looking around at
 * the customers, for a while before it looks around again.
 */
final class MerchantGoal extends PostWorkGoal {
    private static final double STALL_REACH_SQR = 2.5 * 2.5;
    private static final int GIVE_UP_TICKS = 600;
    /** Ticks a merchant keeps a stall before it looks around again. */
    private static final int SHIFT = 400;

    @Nullable
    private BlockPos stall;
    private int walking;
    private int standing;

    MerchantGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    @Override
    protected boolean deliversToPost() {
        return false;
    }

    @Override
    protected void onStart() {
        stall = null;
        walking = 0;
        standing = 0;
    }

    @Override
    @Nullable
    protected WorkStatus work(ServerLevel level) {
        if (stall == null || !level.getBlockState(stall).is(ModRegistries.MARKET_STALL.get())) {
            stall = pickStall(level);
            if (stall == null) {
                return MerchantBehavior.surveyed(post.pos()) ? WorkStatus.NO_WORKSTATION : WorkStatus.WAITING;
            }
        }
        if (!near(stall, STALL_REACH_SQR)) {
            moveTo(stall);
            if (++walking > GIVE_UP_TICKS) {
                stall = null;
                ZoneSurveys.request(post.pos());
                return WorkStatus.NO_WORKSTATION;
            }
            return null;
        }
        if (standing % 60 == 0) {
            lookAt(stall);
        }
        report(WorkStatus.WORKING);
        return ++standing >= SHIFT ? WorkStatus.WORKING : null;
    }

    @Nullable
    private BlockPos pickStall(ServerLevel level) {
        if (!MerchantBehavior.surveyed(post.pos())) {
            ZoneSurveys.request(post.pos());
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : MerchantBehavior.knownStalls(post.pos())) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModRegistries.MARKET_STALL.get())) {
                continue;
            }
            double distance = body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        if (best == null) {
            ZoneSurveys.request(post.pos());
        }
        return best;
    }
}
