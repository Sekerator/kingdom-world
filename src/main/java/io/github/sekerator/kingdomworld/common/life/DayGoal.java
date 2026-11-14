package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * The day of a body apart from work: in the afternoon of a festival it joins the crowd at the throne or the town hall of its
 * city, in the evening it may spend the time in the tavern of its city, at night it goes home and sleeps in its bed. Soldiers
 * and adventurers keep watch and never sleep. Where to go is looked at again once a second; paths come from the
 * {@link PathBudget} like those of the other goals.
 */
public final class DayGoal extends Goal {
    private static final double SLEEP_REACH_SQR = 2.2 * 2.2;
    private static final double ARRIVED_SQR = 3.0 * 3.0;
    /** How far from the middle of the festival a body mills about. */
    private static final int CROWD = 5;

    private enum Plan {
        NONE,
        FESTIVAL,
        TAVERN,
        SLEEP
    }

    private final CitizenEntity body;
    private Plan plan = Plan.NONE;
    @Nullable
    private BlockPos target;
    @Nullable
    private BuildingEffects.Effect tavern;
    private int thinking;
    private int pathCooldown;
    /** Ticks until the body moves about again; the tavern serves it at the first of them. */
    private int wander;

    public DayGoal(CitizenEntity body) {
        this.body = body;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    /**
     * What the plan of the evening or the night has the body do, for the window of the workers; {@code null} while it has none.
     */
    @Nullable
    public io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing doing() {
        return switch (plan) {
            case FESTIVAL -> io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.FESTIVAL;
            case TAVERN -> io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.TAVERN;
            case SLEEP -> io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.SLEEPING;
            default -> null;
        };
    }

    private void think() {
        plan = Plan.NONE;
        target = null;
        tavern = null;
        MinecraftServer server = body.getServer();
        if (server == null || body.citizenId() == null || body.kingdomId() == null || LifeWork.keepsWatch(body.professionId())
                || body.isWounded() || LifeWork.isSick(body.citizenId())) {
            return;
        }
        WorldPos home = home();
        if (LifeWork.festivalRest(server, body.kingdomId())) {
            Optional<io.github.sekerator.kingdomworld.common.road.CityWalls.Place> city = LifeWork.cityOf(server, body.kingdomId(),
                    home != null ? home : here());
            if (city.isPresent() && city.get().dimension().equals(Dimensions.id(body.level()))) {
                plan = Plan.FESTIVAL;
                target = new BlockPos(city.get().x(), city.get().y(), city.get().z());
                return;
            }
        }
        DaySchedule.Phase phase = LifeWork.phase(server, body.citizenId());
        if (phase == DaySchedule.Phase.EVENING && !InnkeeperBehavior.INNKEEPER.id().equals(body.professionId())
                && DaySchedule.wantsTavern(body.citizenId(), LifeWork.today(server), LifeSettings.rules())) {
            Optional<BuildingEffects.Effect> found = LifeWork.tavernNear(server, body.kingdomId(), home != null ? home : here());
            if (found.isPresent() && LifeWork.open(server, found.get().building())) {
                plan = Plan.TAVERN;
                tavern = found.get();
                WorldPos center = LifeWork.center(found.get().building());
                target = new BlockPos(center.x(), center.y(), center.z());
                return;
            }
        }
        if (phase == DaySchedule.Phase.NIGHT && body.home() != null) {
            plan = Plan.SLEEP;
            target = body.home();
        }
    }

    @Nullable
    private WorldPos home() {
        BlockPos home = body.home();
        return home == null ? null : new WorldPos(Dimensions.id(body.level()), home.getX(), home.getY(), home.getZ());
    }

    private WorldPos here() {
        BlockPos pos = body.blockPosition();
        return new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean canUse() {
        if (body.isSleeping()) {
            return true;
        }
        if (--thinking <= 0) {
            thinking = 20;
            think();
        }
        return plan != Plan.NONE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pathCooldown = 0;
        wander = 0;
    }

    @Override
    public void stop() {
        body.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        if (body.isSleeping() || target == null) {
            return;
        }
        double distance = body.blockPosition().distSqr(target);
        if (plan == Plan.SLEEP) {
            if (distance <= SLEEP_REACH_SQR && body.level().getBlockState(target).getBlock() instanceof BedBlock && free(target)) {
                body.getNavigation().stop();
                body.startSleeping(target);
                return;
            }
        } else if (distance <= ARRIVED_SQR || plan == Plan.FESTIVAL && distance <= (CROWD + 1) * (CROWD + 1)) {
            arrived();
            return;
        }
        walkTo(target);
    }

    /**
     * Whether a bed is free: not taken, or taken by nobody lying in it (a body that went away asleep where its chunk was
     * no longer loaded leaves the bed marked).
     */
    private boolean free(BlockPos bed) {
        if (!body.level().getBlockState(bed).getValue(BedBlock.OCCUPIED)) {
            return true;
        }
        return body.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(bed).inflate(1.0),
                net.minecraft.world.entity.LivingEntity::isSleeping).isEmpty();
    }

    private void arrived() {
        MinecraftServer server = body.getServer();
        // Inside the tavern or in the crowd of the festival the body moves about now and then.
        if (--wander <= 0 && body.getNavigation().isDone() && target != null) {
            if (plan == Plan.TAVERN && server != null && tavern != null && body.citizenId() != null) {
                Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
                citizen.ifPresent(value -> LifeWork.enjoyTavern(server, value, tavern));
            }
            wander = 60 + body.getRandom().nextInt(120);
            int spread = plan == Plan.FESTIVAL ? CROWD : tavernSpread();
            BlockPos spot = target.offset(body.getRandom().nextInt(2 * spread + 1) - spread, 0, body.getRandom().nextInt(2 * spread + 1) - spread);
            if (PathBudget.tryAcquire()) {
                body.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.45);
            }
        }
    }

    private int tavernSpread() {
        if (tavern == null) {
            return 2;
        }
        var box = tavern.building().box();
        return Math.max(1, Math.min(box.maxX() - box.minX(), box.maxZ() - box.minZ()) / 2 - 2);
    }

    private void walkTo(BlockPos place) {
        if (pathCooldown > 0) {
            pathCooldown--;
            return;
        }
        if (body.getNavigation().isDone() && PathBudget.tryAcquire()) {
            body.getNavigation().moveTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, 0.6);
            pathCooldown = 40;
        }
    }

    /**
     * Whether a sleeping body should get up: the night is over, its bed is gone or it was hurt.
     */
    public static boolean shouldWake(CitizenEntity body) {
        MinecraftServer server = body.getServer();
        if (server == null || body.citizenId() == null) {
            return true;
        }
        // A bed that is no longer its home (a room of a tavern, the bed of a player) is left at once.
        return LifeWork.phase(server, body.citizenId()) != DaySchedule.Phase.NIGHT || body.isWounded()
                || body.kingdomId() != null && LifeWork.festivalRest(server, body.kingdomId())
                || body.getSleepingPos().filter(pos -> pos.equals(body.home())).isEmpty();
    }
}
