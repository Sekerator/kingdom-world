package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A builder at work near players. It picks the next step of the most urgent construction within reach of its post that no
 * other builder works on, fetches the materials of a block from a storehouse when the site lacks them, walks up to the
 * place and does the step: breaks what is in the way (the drops go into its bag and from there to a storehouse) or lays the
 * block. A place it cannot walk to is worked from the nearest point if that is close enough, otherwise put off for a while.
 */
final class BuilderGoal extends PostWorkGoal {
    /** Ticks a step stays put off after it could not be reached or a container stood in the way. */
    private static final long DEFER_TICKS = 2_400;
    private static final long BLOCKED_TICKS = 400;
    /** Ticks a builder walks towards a place before it may work from where it stands. */
    private static final int SETTLE_TICKS = 40;
    /** How many steps a builder looks at before it gives up for this round. */
    private static final int TRIES = 24;

    @Nullable
    private Construction site;
    @Nullable
    private BuildPlan plan;
    private int step = -1;
    @Nullable
    private BlockPos fetchFrom;
    private String fetchItem = "";
    private int fetchAmount;
    private int walking;
    private int timer;

    BuilderGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    @Override
    protected boolean deliversToPost() {
        return false;
    }

    @Override
    protected void onStart() {
        drop();
    }

    @Override
    protected void onStop() {
        UUID me = body.citizenId();
        if (me != null) {
            ConstructionWork.releaseAll(me);
        }
        drop();
    }

    private void drop() {
        site = null;
        plan = null;
        step = -1;
        fetchFrom = null;
        walking = 0;
        timer = 0;
    }

    @Override
    protected WorkStatus work(ServerLevel level) {
        MinecraftServer server = level.getServer();
        UUID me = body.citizenId();
        if (me == null) {
            return WorkStatus.UNKNOWN;
        }
        long now = KingdomWorldApi.gameTime(server);
        if (site != null && (KingdomWorldApi.state(server).construction().get(site.id()).isEmpty() || site.paused())) {
            ConstructionWork.releaseAll(me);
            drop();
        }
        if (step < 0) {
            WorkStatus problem = choose(server, me, now);
            if (problem != null) {
                return hasCarried() ? WorkStatus.WORKING : problem;
            }
            report(WorkStatus.WORKING);
        }
        Construction construction = site;
        BuildPlan steps = plan;
        if (construction == null || steps == null) {
            drop();
            return null;
        }
        ConstructionWork.worked(construction, me, now);
        if (construction.isDone(step) || construction.isPending(step)) {
            next(construction, me);
            return null;
        }
        BuildPlan.Step current = steps.step(step);
        if (fetchFrom != null) {
            return fetch(level, construction, me, now);
        }
        if (current.kind() == BuildPlan.Kind.PLACE && !construction.hasDelivered(current.cost())) {
            if (!planFetch(level, construction, steps, current)) {
                construction.setStatus(Construction.Status.NO_MATERIALS);
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                next(construction, me);
                report(WorkStatus.NO_MATERIALS);
            }
            return null;
        }
        BlockPos target = new BlockPos(current.x(), current.y(), current.z());
        ConstructionRules rules = ConstructionSettings.rules();
        double distance = body.getEyePosition().distanceToSqr(Vec3.atCenterOf(target));
        if (distance > rules.reach() * rules.reach()) {
            moveTo(target);
            walking++;
            boolean settled = walking > SETTLE_TICKS && body.getNavigation().isDone();
            if (!(settled && distance <= rules.farReach() * rules.farReach())) {
                if (walking > rules.giveUpTicks()) {
                    construction.setStatus(Construction.Status.UNREACHABLE, new WorldPos(construction.dimension(), current.x(), current.y(),
                            current.z()));
                    ConstructionWork.defer(construction, step, now + DEFER_TICKS);
                    next(construction, me);
                    report(WorkStatus.UNREACHABLE);
                }
                return null;
            }
        }
        body.getNavigation().stop();
        if (current.kind() == BuildPlan.Kind.PLACE && !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(target)).isEmpty()) {
            // Somebody stands where the block goes (the builder itself, perhaps): another block first.
            ConstructionWork.defer(construction, step, now + 60);
            next(construction, me);
            return null;
        }
        lookAt(target);
        Optional<Citizen> citizen = citizen();
        int experience = citizen.map(Citizen::experience).orElse(0);
        double speed = EconomySettings.workSpeed(citizen.map(Citizen::mood).orElse(50));
        if (++timer < rules.ticksPerBlock(experience) / Math.max(0.1, speed)) {
            if (timer % 10 == 0) {
                body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            return null;
        }
        timer = 0;
        Optional<BuildingTemplate> template = ConstructionWork.template(construction);
        if (template.isEmpty()) {
            drop();
            return WorkStatus.NO_CONSTRUCTIONS;
        }
        List<ItemStack> drops = new ArrayList<>();
        ConstructionWork.StepResult result = ConstructionWork.doStep(level, construction, steps, template.get(), step, body, tool(), drops, true);
        if (construction.kind() == Construction.Kind.EARTHWORK && !drops.isEmpty()) {
            // The ground of earthworks stays at the site for the fill or goes to the storehouses or the spoil heap at once:
            // only what is of worth is carried.
            drops = Earthworks.route(level.getServer(), construction, steps, level, drops, true);
        }
        for (ItemStack stack : drops) {
            carry(stack);
        }
        switch (result) {
            case DONE -> {
                if (current.kind() == BuildPlan.Kind.CLEAR) {
                    useTool(1);
                } else {
                    citizen.ifPresent(c -> c.addExperience(1));
                    post.addProduced(io.github.sekerator.kingdomworld.common.citizen.work.PostWork.gameDay(level.getServer()), 1);
                    risk(rules, current);
                }
                if (construction.status().isProblem() || construction.status() == Construction.Status.NO_BUILDER) {
                    construction.setStatus(Construction.Status.WORKING);
                }
            }
            case BLOCKED -> {
                construction.setStatus(Construction.Status.BLOCKED, new WorldPos(construction.dimension(), current.x(), current.y(), current.z()));
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                report(WorkStatus.BLOCKED);
            }
            case NO_MATERIALS -> {
            }
            case OUTSIDE_LAND -> {
                // The kingdom lost the place: the construction waits with what was paid for, the builder looks elsewhere.
                ConstructionWork.releaseAll(me);
                drop();
                report(WorkStatus.OUTSIDE_TERRITORY);
                return null;
            }
        }
        boolean clearing = current.kind() == BuildPlan.Kind.CLEAR;
        next(construction, me);
        if (hasCarried() && (isCarryingFull() || (clearing && steps.cleared(construction)))) {
            // The bag of drops goes to a storehouse before the building goes on.
            return WorkStatus.WORKING;
        }
        return null;
    }

    /**
     * Picks the next step of the constructions within reach, or says why there is none.
     */
    @Nullable
    private WorkStatus choose(MinecraftServer server, UUID me, long now) {
        WorkStatus problem = WorkStatus.NO_CONSTRUCTIONS;
        for (Construction construction : ConstructionWork.candidates(server, post)) {
            Optional<BuildPlan> found = ConstructionWork.plan(construction);
            if (found.isEmpty()) {
                continue;
            }
            BuildPlan steps = found.get();
            for (int i = 0; i < TRIES; i++) {
                OptionalInt next = steps.next(construction, index -> ConstructionWork.busy(construction, index, me, now));
                if (next.isEmpty()) {
                    if (!steps.cleared(construction) && construction.status() == Construction.Status.BLOCKED) {
                        problem = WorkStatus.BLOCKED;
                    } else if (construction.status() == Construction.Status.NO_MATERIALS && problem != WorkStatus.BLOCKED) {
                        problem = WorkStatus.NO_MATERIALS;
                    }
                    break;
                }
                BuildPlan.Step candidate = steps.step(next.getAsInt());
                if (candidate.kind() == BuildPlan.Kind.PLACE && !construction.hasDelivered(candidate.cost()) && !obtainable(server, construction,
                        candidate.cost())) {
                    // Nothing to build this block from: another block may have its materials.
                    ConstructionWork.defer(construction, candidate.index(), now + BLOCKED_TICKS);
                    construction.setStatus(Construction.Status.NO_MATERIALS);
                    problem = WorkStatus.NO_MATERIALS;
                    continue;
                }
                site = construction;
                plan = steps;
                step = candidate.index();
                // The post shows what its builder places now.
                post.setCurrent(candidate.cost().isEmpty() ? "" : candidate.cost().keySet().iterator().next(), 0.0);
                walking = 0;
                timer = 0;
                ConstructionWork.claim(construction, step, me);
                return null;
            }
        }
        report(problem);
        return problem;
    }

    private boolean obtainable(MinecraftServer server, Construction construction, Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int missing = entry.getValue() - construction.delivered().getOrDefault(entry.getKey(), 0);
            if (missing > 0 && ConstructionWork.available(server, construction, entry.getKey()) < missing) {
                return false;
            }
        }
        return true;
    }

    private void next(Construction construction, UUID me) {
        ConstructionWork.release(construction, step, me);
        step = -1;
        fetchFrom = null;
        walking = 0;
        timer = 0;
    }

    /**
     * Picks a storehouse with the first material the site lacks for the block.
     *
     * @return whether one was found
     */
    private boolean planFetch(ServerLevel level, Construction construction, BuildPlan steps, BuildPlan.Step current) {
        for (Map.Entry<String, Integer> entry : current.cost().entrySet()) {
            int missing = entry.getValue() - construction.delivered().getOrDefault(entry.getKey(), 0);
            if (missing <= 0) {
                continue;
            }
            if (ConstructionWork.available(level.getServer(), construction, entry.getKey()) < missing) {
                return false;
            }
            BlockPos storehouse = storehouseWith(level, entry.getKey());
            if (storehouse == null) {
                return false;
            }
            int needed = steps.needed(construction).getOrDefault(entry.getKey(), missing);
            fetchFrom = storehouse;
            fetchItem = entry.getKey();
            fetchAmount = Math.max(missing, Math.min(64, needed));
            walking = 0;
            return true;
        }
        return true;
    }

    @Nullable
    private BlockPos storehouseWith(ServerLevel level, String item) {
        BlockPos here = body.blockPosition();
        WorldPos near = new WorldPos(post.pos().dimension(), here.getX(), here.getY(), here.getZ());
        for (StorehouseInventory inventory : KingdomStorage.storehouses(level.getServer(), post.kingdomId(), near)) {
            BlockPos pos = new BlockPos(inventory.pos().x(), inventory.pos().y(), inventory.pos().z());
            if (!inventory.pos().dimension().equals(near.dimension()) || !level.isLoaded(pos)
                    || !level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get())) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (KingdomStorage.itemId(inventory.getStackInSlot(slot)).equals(item)) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Walks to the storehouse and brings materials from it to the site.
     */
    @Nullable
    private WorkStatus fetch(ServerLevel level, Construction construction, UUID me, long now) {
        BlockPos from = fetchFrom;
        if (from == null) {
            return null;
        }
        if (!near(from, REACH_SQR)) {
            moveTo(from);
            if (++walking > ConstructionSettings.rules().giveUpTicks()) {
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                next(construction, me);
            }
            return null;
        }
        ConstructionWork.fetch(level.getServer(), construction, fetchItem, fetchAmount);
        fetchFrom = null;
        walking = 0;
        return null;
    }

    /**
     * Work high above the feet may end in a fall.
     */
    private void risk(ConstructionRules rules, BuildPlan.Step current) {
        if (current.y() - body.getY() > ConstructionRules.HEIGHT && body.getRandom().nextDouble() < rules.fallChance()) {
            body.hurt(body.damageSources().fall(), 4.0f + body.getRandom().nextInt(7));
        }
    }
}
