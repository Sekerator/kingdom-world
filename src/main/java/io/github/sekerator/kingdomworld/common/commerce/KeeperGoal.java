package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Near players a keeper of a shop stands at its place during the hours of work, no farther than {@value #REACH} blocks from it:
 * at its counter, the stall of its market or its house. Trade does not wait for the body: a keeper who did not get there still
 * trades.
 */
public class KeeperGoal extends Goal {
    /** How far from its place a keeper may stand. */
    static final double REACH = 4.0;
    private static final int LOOK_TICKS = 40;

    private final CitizenEntity body;
    private BlockPos place;
    private int lookCooldown;
    private int pathCooldown;

    public KeeperGoal(CitizenEntity body) {
        this.body = body;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Nullable
    private BlockPos lookUp() {
        if (body.level().isClientSide || body.citizenId() == null || !Professions.UNEMPLOYED.id().equals(body.professionId())
                || !LifeWork.works(body)) {
            return null;
        }
        MinecraftServer server = body.getServer();
        if (server == null) {
            return null;
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
        if (citizen.isEmpty() || citizen.get().business() == 0) {
            return null;
        }
        Optional<Business.Place> found = Shops.placeOf(server, citizen.get().business());
        if (found.isEmpty() || !found.get().dimension().equals(Dimensions.id(body.level()))) {
            return null;
        }
        return new BlockPos(found.get().x(), found.get().y(), found.get().z());
    }

    @Override
    public boolean canUse() {
        if (lookCooldown-- > 0) {
            return false;
        }
        lookCooldown = LOOK_TICKS;
        place = lookUp();
        return place != null && body.blockPosition().distSqr(place) > REACH * REACH;
    }

    @Override
    public boolean canContinueToUse() {
        return place != null && LifeWork.works(body) && body.blockPosition().distSqr(place) > 2.0 * 2.0;
    }

    @Override
    public void start() {
        pathCooldown = 0;
    }

    @Override
    public void stop() {
        body.getNavigation().stop();
        place = null;
    }

    @Override
    public void tick() {
        if (place == null) {
            return;
        }
        if (pathCooldown > 0) {
            pathCooldown--;
            return;
        }
        if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
            body.getNavigation().moveTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, 0.6);
            pathCooldown = 40;
        }
    }
}
