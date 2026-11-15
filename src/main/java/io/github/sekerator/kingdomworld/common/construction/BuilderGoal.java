package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Crew;
import io.github.sekerator.kingdomworld.core.construction.Scaffolding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * A builder at work near players. It picks the next step of the most urgent construction within reach of its post that no
 * other builder works on, fetches the materials of a block from a storehouse when the site lacks them, walks up to a place
 * from which it reaches the block and does the step: breaks what is in the way (the drops go into its bag and from there to
 * a storehouse) or lays the block.
 *
 * <p>Builders at one construction work together: each takes a step of the layer at hand away from where the others work (see
 * {@link Crew}), never a block whose support is not there yet, and none walks for materials another one already brings: it
 * goes to its block meanwhile and lays it once they are at the site. A walk brings materials for several blocks, never more
 * than the construction still needs.
 *
 * <p>A block it cannot reach from the ground (a roof, the top of a high wall) it reaches from scaffolds, as a player jumps and
 * puts a block under its feet: it chooses a column on the site or beside it, climbs it block by block to the height from
 * which the block is within reach, does there every step it reaches and takes the column down from the top as it goes down.
 * When no column helps (the block is walled in, there is no room for one), it works from the nearest point it got to, so that
 * no construction stays stuck on a block out of reach.
 */
final class BuilderGoal extends PostWorkGoal {
    private static final long BLOCKED_TICKS = 400;
    /** Ticks a block on a column that a builder stands on waits. */
    private static final long COLUMN_TICKS = 100;
    /** Ticks a builder walks towards a place before it may work from where it stands. */
    private static final int SETTLE_TICKS = 40;
    /** How many steps a builder looks at before it gives up for this round. */
    private static final int TRIES = 24;
    /** Steps more a builder looks at, for the steps of the layer at hand to choose from. */
    private static final int NEAR_STEPS = 12;
    /** Ticks it takes to climb a scaffold, and to take one down on the way down. */
    private static final int CLIMB_TICKS = 6;
    private static final int DESCEND_TICKS = 4;
    /** Columns tried for one block before the builder works from the nearest point it gets to. */
    private static final int COLUMN_TRIES = 6;
    /** Paths to columns looked for in one tick. */
    private static final int PATHS_PER_TICK = 3;
    /** Ticks a fall does a builder no harm after it stood on scaffolds. */
    private static final int SAFE_TICKS = 200;
    /** Ticks a builder on a column may stand on nothing (in water, say) before the column comes down. */
    private static final int AIRBORNE_TICKS = 60;
    /** Steps of the layer at hand a builder chooses from. */
    private static final int OPTIONS = 24;
    /** Items a builder carries from a storehouse in one walk at most. */
    private static final int LOAD = 64;
    /**
     * Ticks a builder waits by a construction whose steps left are only put off for a moment (another builder works on them,
     * their support is on its way, a builder stands where they go) before it looks again.
     */
    private static final int WAIT_TICKS = 10;
    /** Steps put off for no longer than this count as put off for a moment. */
    private static final long SOON_TICKS = COLUMN_TICKS;
    /** Rounds of such waiting before the builder leaves the work for a while, as when nothing is left to do. */
    private static final int WAIT_ROUNDS = 30;
    /** Ticks a builder that stands where its block goes tries to step aside before it takes another block. */
    private static final int ASIDE_TICKS = 40;
    private static final double SPEED = 0.6;

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
    /** Ticks left of waiting by the construction before the builder looks for a step again. */
    private int idle;
    /** Rounds the builder waited by the construction in a row. */
    private int waited;
    /** Ticks the builder has been stepping aside from the place of its block. */
    private int aside;
    /** Why the builder last put a step off, for the messages of tests. */
    private String deferWhy = "";
    /** Whether the last look for a step found steps that are only put off for a moment. */
    private boolean postponed;
    /** The construction the builder waits by, and its plan: it keeps off the places of their blocks meanwhile. */
    @Nullable
    private Construction waitingBy;
    @Nullable
    private BuildPlan waitingPlan;

    /** The place the builder goes to for the current step, {@code null} until it is chosen. */
    @Nullable
    private Scaffolding.Spot spot;
    /** Columns of the current step the builder could not get to, packed with y 0. */
    private final Set<Long> refused = new HashSet<>();
    /** The columns of the current step not tried yet, best first; looked for once a step, {@code null} until then. */
    @Nullable
    private List<Scaffolding.Spot> spots;
    /** Whether the builder works on the current step from the nearest point it gets to. */
    private boolean nearest;

    /**
     * The column of scaffolds the builder stands on: its construction, where it stands and the height of the ground under it.
     */
    private record Column(long construction, String dimension, int x, int z, int ground) {
    }

    @Nullable
    private Column column;
    /** The height of the feet the builder climbs or goes down to on its column. */
    private int columnGoal;
    /** The highest the builder stood on its column: its scaffolds go up to below it. */
    private int columnTop;
    /** Whether the builder takes its column down before anything else. */
    private boolean leaving;
    private int climbTimer;
    /** Ticks the builder has not stood on anything on its column. */
    private int airborne;
    /** What the work tells once the builder is down from its column. */
    @Nullable
    private WorkStatus afterLeaving;

    /** The goals of builders by citizen while a test follows them, for what it tells of a crew. */
    private static final Map<UUID, java.lang.ref.WeakReference<BuilderGoal>> GOALS = new java.util.HashMap<>();
    private static boolean tracing;

    BuilderGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    /**
     * What a builder does now, for the messages of tests: its step, the place it goes to, its column and its walk.
     */
    static void trace(boolean on) {
        tracing = on;
        GOALS.clear();
    }

    static String describe(UUID citizen) {
        java.lang.ref.WeakReference<BuilderGoal> reference = GOALS.get(citizen);
        BuilderGoal goal = reference == null ? null : reference.get();
        if (goal == null) {
            return "no goal";
        }
        Scaffolding.Spot place = goal.spot;
        Column on = goal.column;
        return "step " + goal.step + (goal.fetchFrom != null ? " fetching " + goal.fetchAmount + " " + goal.fetchItem : "")
                + (place != null ? " spot " + place.x() + "," + place.ground() + "," + place.z() + "+" + place.height() : "")
                + (goal.nearest ? " near" : "") + (on != null ? " column " + on.x() + "," + on.z() + " goal " + goal.columnGoal
                + (goal.leaving ? " leaving" : "") : "") + " walking " + goal.walking + " timer " + goal.timer + " idle " + goal.idle + " waited " + goal.waited + " deferred for " + goal.deferWhy + " refused " + goal.refused.size()
                + " nav " + (goal.body.getNavigation().isDone() ? "done" : "moving");
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
        // Called away (night, danger, another task): the column comes down at once and the builder stands on the ground.
        takeColumnDownNow();
        drop();
    }

    private void drop() {
        site = null;
        plan = null;
        step = -1;
        idle = 0;
        waited = 0;
        aside = 0;
        waitingBy = null;
        waitingPlan = null;
        stopFetching();
        walking = 0;
        timer = 0;
        forgetSpot();
    }

    private void forgetSpot() {
        spot = null;
        spots = null;
        refused.clear();
        nearest = false;
        UUID me = body.citizenId();
        if (me != null) {
            Scaffolds.unaim(me);
        }
    }

    /**
     * The builder walks no longer for materials: the others count on them no more.
     */
    private void stopFetching() {
        fetchFrom = null;
        UUID me = body.citizenId();
        if (me != null) {
            ConstructionWork.fetched(me);
        }
    }

    @Override
    protected WorkStatus work(ServerLevel level) {
        MinecraftServer server = level.getServer();
        UUID me = body.citizenId();
        if (me == null) {
            return WorkStatus.UNKNOWN;
        }
        if (tracing) {
            GOALS.put(me, new java.lang.ref.WeakReference<>(this));
        }
        long now = KingdomWorldApi.gameTime(server);
        if (site != null && (KingdomWorldApi.state(server).construction().get(site.id()).isEmpty() || site.paused())) {
            ConstructionWork.releaseAll(me);
            drop();
            if (column != null) {
                leaving = true;
            }
        }
        if (column != null) {
            boolean ready = keepColumn(level, me, now);
            if (column == null && afterLeaving != null) {
                WorkStatus result = afterLeaving;
                afterLeaving = null;
                return result;
            }
            if (!ready) {
                return null;
            }
        }
        if (step < 0) {
            if (idle > 0) {
                idle--;
                keepOffBlocks(level);
                return null;
            }
            WorkStatus problem = choose(server, me, now);
            if (problem != null) {
                if (postponed && !hasCarried() && ++waited <= WAIT_ROUNDS) {
                    // The steps left are another builder's or wait a moment: the builder waits by the construction, down from its
                    // column and off the places of the blocks, rather than leave the work for a long while.
                    if (column != null) {
                        leaving = true;
                    }
                    idle = WAIT_TICKS;
                    report(WorkStatus.WORKING);
                    return null;
                }
                waited = 0;
                return leave(hasCarried() ? WorkStatus.WORKING : problem);
            }
            waited = 0;
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
            if (column != null) {
                // Down from the column first: the storehouse is on the ground.
                leaving = true;
                return null;
            }
            return fetch(level, construction);
        }
        boolean awaited = false;
        if (current.kind() == BuildPlan.Kind.PLACE && !construction.hasDelivered(current.cost())) {
            int fetching = planFetch(level, construction, steps, current, now);
            if (fetching == COMING) {
                // Another builder brings what the block needs: this one goes to the block meanwhile.
                awaited = true;
            } else {
                if (fetching == NO_FETCH) {
                    construction.setStatus(Construction.Status.NO_MATERIALS);
                    deferWhy = "no materials";
                    ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                    next(construction, me);
                    report(WorkStatus.NO_MATERIALS);
                } else if (fetching == CART) {
                    // The kingdom has it in storehouses out of reach: a cart brings it, another block meanwhile.
                    deferWhy = "cart";
                    ConstructionWork.defer(construction, step, now + Carts.CART_TICKS);
                    next(construction, me);
                }
                return null;
            }
        }
        BlockPos target = new BlockPos(current.x(), current.y(), current.z());
        ConstructionRules rules = ConstructionSettings.rules();
        if (!inReach(target, rules.reach()) && !approach(level, construction, steps, target, rules)) {
            return null;
        }
        if (column == null) {
            body.getNavigation().stop();
        }
        if (Scaffolds.inUse(construction.dimension(), target.getX(), target.getY(), target.getZ(), now)) {
            // A builder stands on a scaffold there: the block waits until the column is taken down.
            deferWhy = "scaffold at the block";
            ConstructionWork.defer(construction, step, now + COLUMN_TICKS);
            next(construction, me);
            return null;
        }
        if (current.kind() == BuildPlan.Kind.PLACE) {
            List<net.minecraft.world.entity.LivingEntity> there = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    new net.minecraft.world.phys.AABB(target));
            if (!there.isEmpty()) {
                if (there.size() == 1 && there.get(0) == body && column == null && ++aside <= ASIDE_TICKS
                        && stepAside(level, target, construction, steps)) {
                    // The builder itself stands where the block goes: it steps aside and lays it.
                    return null;
                }
                // Somebody else stands there: another block first. A citizen of the kingdom standing still there is asked to step aside.
                for (net.minecraft.world.entity.LivingEntity other : there) {
                    if (other != body && other instanceof CitizenEntity citizen && citizen.getNavigation().isDone()
                            && construction.kingdomId().equals(citizen.kingdomId())) {
                        stepAside(level, target, construction, steps, citizen);
                    }
                }
                deferWhy = "somebody there: " + there.get(0).getType().toShortString() + (there.get(0) == body ? " itself" : "");
                ConstructionWork.defer(construction, step, now + 60);
                next(construction, me);
                return null;
            }
        }
        lookAt(target);
        Scaffolding.Spot place = spot;
        if (place != null) {
            // Working from there: no other builder chooses the place meanwhile.
            Scaffolds.aim(me, construction.dimension(), place.x(), place.z(), now);
        }
        if (awaited) {
            // At the block, the builder waits for the materials on their way.
            timer = 0;
            return null;
        }
        Optional<Citizen> citizen = citizen();
        int experience = citizen.map(Citizen::experience).orElse(0);
        double speed = EconomySettings.workSpeed(citizen.map(Citizen::mood).orElse(50));
        if (++timer < rules.ticksPerBlock(experience) / Math.max(0.1, speed)) {
            if (timer % 10 == 0) {
                body.swing(InteractionHand.MAIN_HAND);
            }
            return null;
        }
        timer = 0;
        Optional<BuildingTemplate> template = ConstructionWork.template(construction);
        if (template.isEmpty()) {
            drop();
            return leave(WorkStatus.NO_CONSTRUCTIONS);
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
                deferWhy = "blocked";
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                report(WorkStatus.BLOCKED);
            }
            case NO_MATERIALS -> {
                if (construction.kind() == Construction.Kind.EARTHWORK && current.kind() == BuildPlan.Kind.CLEAR) {
                    // Lava beside the dig waits for cobblestone in the storehouses: the builder goes on with the rest meanwhile.
                    deferWhy = "lava";
                    ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                }
            }
            case OUTSIDE_LAND -> {
                // The kingdom lost the place: the construction waits with what was paid for, the builder looks elsewhere.
                ConstructionWork.releaseAll(me);
                drop();
                report(WorkStatus.OUTSIDE_TERRITORY);
                if (column != null) {
                    leaving = true;
                }
                return null;
            }
        }
        boolean clearing = current.kind() == BuildPlan.Kind.CLEAR;
        next(construction, me);
        if (hasCarried() && (isCarryingFull() || (clearing && steps.cleared(construction)))) {
            // The bag of drops goes to a storehouse before the building goes on.
            return leave(WorkStatus.WORKING);
        }
        return null;
    }

    /**
     * Ends a round of work with a result, down from the column first if the builder stands on one.
     */
    @Nullable
    private WorkStatus leave(WorkStatus result) {
        if (column == null) {
            return result;
        }
        leaving = true;
        afterLeaving = result;
        return null;
    }

    private boolean inReach(BlockPos target, double reach) {
        return body.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= reach * reach;
    }

    /**
     * Picks the next step of the constructions within reach, or says why there is none. The steps it may take are those of the
     * tier at hand (see {@link BuildPlan#tier}) whose support is there: a builder on a column takes one it reaches from there
     * first, otherwise the one {@link Crew} gives it, away from the other builders.
     */
    @Nullable
    private WorkStatus choose(MinecraftServer server, UUID me, long now) {
        WorkStatus problem = WorkStatus.NO_CONSTRUCTIONS;
        double reach = ConstructionSettings.rules().reach();
        postponed = false;
        waitingBy = null;
        waitingPlan = null;
        for (Construction construction : ConstructionWork.candidates(server, post)) {
            Optional<BuildPlan> found = ConstructionWork.plan(construction);
            if (found.isEmpty()) {
                continue;
            }
            BuildPlan steps = found.get();
            List<BuildPlan.Step> options = new ArrayList<>();
            // The tier of the steps taken as options, and how many steps were looked at.
            int[] seen = {-1, 0};
            boolean[] starved = {false};
            // Whether some step is put off only for a moment: the builder then waits by the construction.
            boolean[] soon = {false};
            boolean stopped = steps.forEachNext(construction, index -> {
                if (!ConstructionWork.busy(construction, index, me, now)) {
                    return false;
                }
                soon[0] |= ConstructionWork.soon(construction, index, me, now, SOON_TICKS);
                return true;
            }, candidate -> {
                if (++seen[1] > TRIES + NEAR_STEPS || options.size() >= OPTIONS) {
                    return false;
                }
                if (seen[0] >= 0 && steps.tier(candidate.index()) != seen[0]) {
                    // The rest of the layer at hand is taken: what comes after it waits.
                    return false;
                }
                if (steps.waits(construction, candidate.index())) {
                    // Its support is still to be built, by another builder or after a wait.
                    soon[0] = true;
                    return true;
                }
                if (candidate.kind() == BuildPlan.Kind.PLACE && !construction.hasDelivered(candidate.cost()) && !obtainable(server, construction,
                        candidate.cost(), me, now)) {
                    // Nothing to build this block from: another block may have its materials.
                    deferWhy = "not obtainable";
                    ConstructionWork.defer(construction, candidate.index(), now + BLOCKED_TICKS);
                    construction.setStatus(Construction.Status.NO_MATERIALS);
                    starved[0] = true;
                    return true;
                }
                if (Scaffolds.inUse(construction.dimension(), candidate.x(), candidate.y(), candidate.z(), now)) {
                    // A builder stands on a scaffold there: the block waits until the column is taken down.
                    deferWhy = "scaffold in choose";
                    ConstructionWork.defer(construction, candidate.index(), now + COLUMN_TICKS);
                    soon[0] = true;
                    return true;
                }
                seen[0] = steps.tier(candidate.index());
                options.add(candidate);
                return true;
            });
            if (starved[0]) {
                problem = WorkStatus.NO_MATERIALS;
            }
            if (!stopped && options.isEmpty()) {
                if (!steps.cleared(construction) && construction.status() == Construction.Status.BLOCKED) {
                    problem = WorkStatus.BLOCKED;
                } else if (construction.status() == Construction.Status.NO_MATERIALS && problem != WorkStatus.BLOCKED) {
                    problem = WorkStatus.NO_MATERIALS;
                }
            }
            if (!options.isEmpty()) {
                take(construction, steps, options.get(pick(construction, steps, options, me, now, reach)), me);
                return null;
            }
            if (soon[0] && !postponed) {
                postponed = true;
                waitingBy = construction;
                waitingPlan = steps;
            }
        }
        report(problem);
        return problem;
    }

    /**
     * Of the steps a builder may take, the one it takes: on a column the first it reaches from there, otherwise the one away from
     * where the other builders of the construction work.
     */
    private int pick(Construction construction, BuildPlan steps, List<BuildPlan.Step> options, UUID me, long now, double reach) {
        if (column != null && column.construction() == construction.id()) {
            for (int i = 0; i < options.size(); i++) {
                BuildPlan.Step option = options.get(i);
                if (inReach(new BlockPos(option.x(), option.y(), option.z()), reach)) {
                    return i;
                }
            }
        }
        List<int[]> others = ConstructionWork.claimedByOthers(construction, steps, me);
        if (!others.isEmpty()) {
            others.addAll(Scaffolds.others(me, construction.dimension(), now));
        }
        return Crew.pick(options, others, body.getX(), body.getZ());
    }

    private void take(Construction construction, BuildPlan steps, BuildPlan.Step candidate, UUID me) {
        site = construction;
        plan = steps;
        step = candidate.index();
        // The post shows what its builder places now.
        post.setCurrent(candidate.cost().isEmpty() ? "" : candidate.cost().keySet().iterator().next(), 0.0);
        walking = 0;
        timer = 0;
        aside = 0;
        forgetSpot();
        ConstructionWork.claim(construction, step, me);
    }

    private boolean obtainable(MinecraftServer server, Construction construction, Map<String, Integer> cost, UUID me, long now) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int missing = entry.getValue() - construction.delivered().getOrDefault(entry.getKey(), 0);
            if (missing > 0 && ConstructionWork.available(server, construction, entry.getKey())
                    + ConstructionWork.coming(construction, entry.getKey(), me, now) < missing) {
                return false;
            }
        }
        return true;
    }

    private void next(Construction construction, UUID me) {
        ConstructionWork.release(construction, step, me);
        step = -1;
        stopFetching();
        walking = 0;
        timer = 0;
        aside = 0;
        forgetSpot();
    }

    /**
     * Steps off the place of a block onto a place beside it to stand on that no block of the plan still waits for, the nearest
     * first.
     *
     * @return whether the builder is on its way there
     */
    private boolean stepAside(ServerLevel level, BlockPos target, Construction construction, BuildPlan steps) {
        return stepAside(level, target, construction, steps, body);
    }

    /**
     * Has a body step off the place of a block, as {@link #stepAside(ServerLevel, BlockPos, Construction, BuildPlan)}: by a walk
     * where one leads there, otherwise by a step (off the top of a wall begun, say).
     */
    private static boolean stepAside(ServerLevel level, BlockPos target, Construction construction, BuildPlan steps,
                                     net.minecraft.world.entity.Mob mover) {
        if (!mover.getNavigation().isDone()) {
            return true;
        }
        BlockPos at = mover.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    if (dx == 0 && dz == 0 || pos.getX() == target.getX() && pos.getZ() == target.getZ() || !standable(level, pos)
                            || unbuilt(construction, steps, pos) || unbuilt(construction, steps, pos.above())) {
                        continue;
                    }
                    double distance = pos.distToCenterSqr(mover.getX(), mover.getY(), mover.getZ());
                    if (distance < bestDistance) {
                        best = pos;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best == null || !PathBudget.tryAcquire()) {
            return best != null;
        }
        Path path = mover.getNavigation().createPath(best, 0);
        if (path != null && path.canReach()) {
            mover.getNavigation().moveTo(path, SPEED);
        } else {
            mover.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        }
        return true;
    }

    /**
     * A builder waiting by a construction keeps off the places of its blocks still to be laid: it would only be in the way.
     */
    private void keepOffBlocks(ServerLevel level) {
        Construction construction = waitingBy;
        BuildPlan steps = waitingPlan;
        if (construction == null || steps == null || column != null) {
            return;
        }
        BlockPos at = body.blockPosition();
        if (unbuilt(construction, steps, at) || unbuilt(construction, steps, at.above())) {
            stepAside(level, at, construction, steps);
        }
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.isLoaded(feet) && Scaffolds.open(level, feet) && Scaffolds.open(level, feet.above())
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
    }

    /**
     * Whether a block of the plan still goes at a place.
     */
    private static boolean unbuilt(Construction construction, BuildPlan steps, BlockPos pos) {
        int index = steps.placeAt(pos.getX(), pos.getY(), pos.getZ());
        return index >= 0 && !construction.isDone(index) && !construction.isPending(index);
    }

    // ---------------------------------------------------------------- getting within reach

    /**
     * Gets the builder within reach of a block: up or down its column if it stands on one and the column reaches the block,
     * otherwise down from it and to a place on the ground or a new column; failing that, as near as it gets.
     *
     * @return whether the builder works on the block from where it is now
     */
    private boolean approach(ServerLevel level, Construction construction, BuildPlan steps, BlockPos target, ConstructionRules rules) {
        Column on = column;
        if (on != null) {
            if (leaving || on.construction() != construction.id()) {
                leaving = true;
                return false;
            }
            OptionalInt at = Scaffolding.feetOn(on.x(), on.z(), on.ground(), feet(), target.getX(), target.getY(), target.getZ(), rules.reach(),
                    body.getEyeHeight(), Scaffolds.world(level, construction, steps, target));
            if (at.isPresent()) {
                columnGoal = at.getAsInt();
                // Up or down the column: the work goes on once the builder stands there.
                return columnGoal == feet();
            }
            // The column does not reach the block: down, and on to another place.
            refuseColumn(on);
            return false;
        }
        if (nearest) {
            return walkNear(target, rules);
        }
        if (spot == null && !chooseSpot(level, construction, steps, target, rules)) {
            return false;
        }
        Scaffolding.Spot place = spot;
        if (place == null) {
            return false;
        }
        BlockPos base = new BlockPos(place.x(), place.ground(), place.z());
        UUID me = body.citizenId();
        if (me != null) {
            // Still on the way there: no other builder chooses the place meanwhile.
            Scaffolds.aim(me, construction.dimension(), place.x(), place.z(), KingdomWorldApi.gameTime(level.getServer()));
        }
        if (body.blockPosition().equals(base) && body.onGround()) {
            body.getNavigation().stop();
            body.setPos(place.x() + 0.5, body.getY(), place.z() + 0.5);
            if (place.height() == 0) {
                return true;
            }
            column = new Column(construction.id(), construction.dimension(), place.x(), place.z(), place.ground());
            columnGoal = place.feet();
            columnTop = place.ground();
            climbTimer = 0;
            airborne = 0;
            leaving = false;
            return false;
        }
        walking++;
        if (body.getNavigation().isDone() && walking % 20 == 0 && PathBudget.tryAcquire()) {
            Path path = body.getNavigation().createPath(base, 0);
            if (path != null && path.canReach()) {
                body.getNavigation().moveTo(path, SPEED);
            }
        }
        if (walking > rules.giveUpTicks()) {
            refuse(place);
        }
        return false;
    }

    /**
     * Chooses the place to work on a block from: the best column the builder finds a way to.
     *
     * @return whether one was chosen; when none is left the builder works from the nearest point it gets to
     */
    private boolean chooseSpot(ServerLevel level, Construction construction, BuildPlan steps, BlockPos target, ConstructionRules rules) {
        if (spots == null) {
            // The columns are looked for once a step; those the builder cannot get to are taken off the list as it tries them.
            spots = new ArrayList<>(Scaffolding.candidates(target.getX(), target.getY(), target.getZ(), rules.reach(), body.getEyeHeight(),
                    Scaffolds.world(level, construction, steps, target), Mth.floor(body.getX()), feet(), Mth.floor(body.getZ()), refused::contains));
        }
        int paths = 0;
        UUID me = body.citizenId();
        long now = KingdomWorldApi.gameTime(level.getServer());
        while (!spots.isEmpty()) {
            if (paths >= PATHS_PER_TICK || !PathBudget.tryAcquire()) {
                // The others are looked at in the next tick.
                return false;
            }
            Scaffolding.Spot candidate = spots.remove(0);
            if (refused.contains(Scaffolding.pack(candidate.x(), 0, candidate.z()))) {
                continue;
            }
            if (me != null && Scaffolds.taken(me, construction.dimension(), candidate.x(), candidate.z(), now)) {
                // Another builder goes there or stands on a column there.
                continue;
            }
            paths++;
            BlockPos base = new BlockPos(candidate.x(), candidate.ground(), candidate.z());
            Path path = body.blockPosition().equals(base) ? null : body.getNavigation().createPath(base, 0);
            if (body.blockPosition().equals(base) || path != null && path.canReach()) {
                spot = candidate;
                walking = 0;
                if (me != null) {
                    Scaffolds.aim(me, construction.dimension(), candidate.x(), candidate.z(), now);
                }
                if (path != null) {
                    body.getNavigation().moveTo(path, SPEED);
                }
                return true;
            }
            refuse(candidate);
            if (nearest) {
                return false;
            }
        }
        // No column left to try.
        startNear();
        return false;
    }

    /**
     * The builder could not get to a column: another one is tried, and after a few the builder works from as near as it gets.
     */
    private void refuse(Scaffolding.Spot place) {
        refused.add(Scaffolding.pack(place.x(), 0, place.z()));
        spot = null;
        walking = 0;
        if (refused.size() >= COLUMN_TRIES) {
            startNear();
        }
    }

    private void startNear() {
        nearest = true;
        spot = null;
        spots = null;
        walking = 0;
    }

    /**
     * The column the builder stands on does not serve the block (no room to climb, a place that takes no scaffold, no height
     * that reaches it): the builder comes down and tries another column, or works from as near as it gets after a few.
     */
    private void refuseColumn(Column on) {
        refused.add(Scaffolding.pack(on.x(), 0, on.z()));
        spot = null;
        leaving = true;
        if (refused.size() >= COLUMN_TRIES) {
            startNear();
        }
    }

    /**
     * Walks towards a block no column helps with and works on it from where it got to: once it stands still close enough, or
     * when walking longer does not bring it closer.
     */
    private boolean walkNear(BlockPos target, ConstructionRules rules) {
        moveTo(target);
        walking++;
        double distance = body.getEyePosition().distanceToSqr(Vec3.atCenterOf(target));
        boolean settled = walking > SETTLE_TICKS && body.getNavigation().isDone();
        return settled && distance <= rules.farReach() * rules.farReach() || walking > rules.giveUpTicks();
    }

    // ---------------------------------------------------------------- the column

    private int feet() {
        return Mth.floor(body.getY() + 0.01);
    }

    /**
     * Keeps the builder on its column: safe from falls, in the middle of it and held, so that no step replaces its scaffolds;
     * climbs or goes down a block at a time towards the height it wants, and takes the column down when it leaves it.
     *
     * @return whether the builder stands where it wants to work, or is down from a column it left
     */
    private boolean keepColumn(ServerLevel level, UUID me, long now) {
        Column on = column;
        if (on == null) {
            return true;
        }
        body.keepSafeFromFalls(SAFE_TICKS);
        body.getNavigation().stop();
        double offX = body.getX() - (on.x() + 0.5);
        double offZ = body.getZ() - (on.z() + 0.5);
        if (Math.abs(offX) > 0.45 || Math.abs(offZ) > 0.45 || !Dimensions.id(level).equals(on.dimension())) {
            // Pushed off the column or taken away: the column comes down at once.
            takeColumnDownNow();
            return false;
        }
        if (Math.abs(offX) > 0.05 || Math.abs(offZ) > 0.05) {
            body.setPos(on.x() + 0.5, body.getY(), on.z() + 0.5);
        }
        body.setDeltaMovement(0.0, Math.min(0.0, body.getDeltaMovement().y), 0.0);
        int feet = feet();
        columnTop = Math.max(columnTop, feet);
        Scaffolds.hold(me, on.dimension(), on.x(), on.z(), on.ground(), Math.max(columnTop, columnGoal), now);
        if (!body.onGround()) {
            // A scaffold under it was broken: it comes down onto the next one, unharmed. Standing on nothing for long (in water),
            // it gives the column up.
            if (++airborne > AIRBORNE_TICKS) {
                refused.add(Scaffolding.pack(on.x(), 0, on.z()));
                spot = null;
                takeColumnDownNow();
            }
            return false;
        }
        airborne = 0;
        int goal = leaving ? on.ground() : columnGoal;
        if (feet == goal) {
            if (leaving || feet <= on.ground()) {
                endColumn(me);
                return true;
            }
            return true;
        }
        if (++climbTimer < (feet < goal ? CLIMB_TICKS : DESCEND_TICKS)) {
            return false;
        }
        climbTimer = 0;
        if (feet < goal) {
            climb(level, on, feet);
        } else {
            descend(level, on, feet, me);
        }
        return false;
    }

    /**
     * Up a block, as a player jumps and puts a block under its feet.
     */
    private void climb(ServerLevel level, Column on, int feet) {
        Construction construction = KingdomWorldApi.state(level.getServer()).construction().get(on.construction()).orElse(null);
        BlockPos under = new BlockPos(on.x(), feet, on.z());
        if (construction == null || !Scaffolds.open(level, under.above(2)) || !Scaffolds.placeable(level, construction, under)) {
            // No room overhead, or the place takes no scaffold any longer: down, and another place.
            refuseColumn(on);
            return;
        }
        body.moveTo(on.x() + 0.5, feet + 1, on.z() + 0.5, body.getYRot(), body.getXRot());
        if (!Scaffolds.put(level, construction, under)) {
            body.moveTo(on.x() + 0.5, feet, on.z() + 0.5, body.getYRot(), body.getXRot());
            refuseColumn(on);
            return;
        }
        body.setDeltaMovement(Vec3.ZERO);
        body.swing(InteractionHand.MAIN_HAND);
        columnTop = Math.max(columnTop, feet + 1);
    }

    /**
     * Down a block, taking the scaffold under its feet away.
     */
    private void descend(ServerLevel level, Column on, int feet, UUID me) {
        BlockPos below = new BlockPos(on.x(), feet - 1, on.z());
        if (Scaffolds.is(level.getBlockState(below))) {
            Scaffolds.takeDown(level, below);
            body.moveTo(on.x() + 0.5, feet - 1, on.z() + 0.5, body.getYRot(), body.getXRot());
            body.setDeltaMovement(Vec3.ZERO);
            body.swing(InteractionHand.MAIN_HAND);
        } else if (Scaffolds.open(level, below)) {
            // Somebody broke that scaffold: one step down onto the next.
            body.moveTo(on.x() + 0.5, feet - 1, on.z() + 0.5, body.getYRot(), body.getXRot());
        } else {
            // A block of the building stands there now: the builder is on solid ground.
            endColumn(me);
        }
    }

    private void endColumn(UUID me) {
        column = null;
        leaving = false;
        climbTimer = 0;
        Scaffolds.release(me);
        body.keepSafeFromFalls(SAFE_TICKS);
    }

    /**
     * Takes the whole column down at once, from the top, and sets the builder on the ground beside where it stood: when it is
     * called away from its work or pushed off.
     */
    private void takeColumnDownNow() {
        Column on = column;
        if (on == null) {
            return;
        }
        body.keepSafeFromFalls(SAFE_TICKS);
        if (body.level() instanceof ServerLevel level && Dimensions.id(level).equals(on.dimension())) {
            boolean onIt = Math.abs(body.getX() - (on.x() + 0.5)) <= 0.45 && Math.abs(body.getZ() - (on.z() + 0.5)) <= 0.45;
            for (int y = Math.max(columnTop, columnGoal); y >= on.ground(); y--) {
                BlockPos pos = new BlockPos(on.x(), y, on.z());
                if (level.isLoaded(pos) && Scaffolds.is(level.getBlockState(pos))) {
                    Scaffolds.takeDown(level, pos);
                }
            }
            BlockPos ground = new BlockPos(on.x(), on.ground(), on.z());
            if (onIt && Scaffolds.open(level, ground) && Scaffolds.open(level, ground.above())) {
                body.moveTo(on.x() + 0.5, on.ground(), on.z() + 0.5, body.getYRot(), body.getXRot());
                body.setDeltaMovement(Vec3.ZERO);
            }
        }
        UUID me = body.citizenId();
        column = null;
        leaving = false;
        climbTimer = 0;
        if (me != null) {
            Scaffolds.release(me);
        }
    }

    // ---------------------------------------------------------------- materials

    /** {@link #planFetch}: nothing to fetch anywhere. */
    private static final int NO_FETCH = 0;
    /** {@link #planFetch}: the builder goes to a storehouse, or nothing is missing. */
    private static final int WALK = 1;
    /** {@link #planFetch}: a cart brings it from storehouses out of reach. */
    private static final int CART = 2;
    /** {@link #planFetch}: other builders or a cart bring what the block lacks. */
    private static final int COMING = 3;

    /**
     * Picks a storehouse with the first material the site lacks for the block that nobody brings yet. A walk brings what the
     * construction still needs of it, up to a load, less what other builders and a cart bring already: two builders walk for
     * the same material only when one walk does not bring all of it.
     */
    private int planFetch(ServerLevel level, Construction construction, BuildPlan steps, BuildPlan.Step current, long now) {
        UUID me = body.citizenId();
        Map<String, Integer> needed = ConstructionWork.needed(construction, steps);
        boolean awaited = false;
        for (Map.Entry<String, Integer> entry : current.cost().entrySet()) {
            String item = entry.getKey();
            int missing = entry.getValue() - construction.delivered().getOrDefault(item, 0);
            if (missing <= 0) {
                continue;
            }
            int coming = me == null ? 0 : ConstructionWork.coming(construction, item, me, now);
            // What no walk brings yet: the need of the whole construction less the site and what is on its way.
            int shortfall = needed.getOrDefault(item, missing) - coming;
            long available = ConstructionWork.available(level.getServer(), construction, item);
            if (coming >= missing && (shortfall <= 0 || available <= 0)) {
                awaited = true;
                continue;
            }
            int amount = Math.max(missing - Math.min(missing, coming), Math.min(LOAD, shortfall));
            if (available < missing - Math.min(missing, coming)) {
                return NO_FETCH;
            }
            BlockPos storehouse = storehouseWith(level, item);
            if (storehouse == null) {
                // A village without a storehouse of its own: the materials come from storehouses of the kingdom out of reach.
                Carts.order(construction, item, Math.max(missing, Math.min(Carts.LOAD, needed.getOrDefault(item, missing))), now);
                return CART;
            }
            fetchFrom = storehouse;
            fetchItem = item;
            fetchAmount = amount;
            walking = 0;
            if (me != null) {
                ConstructionWork.fetching(construction, me, item, amount, now);
            }
            return WALK;
        }
        return awaited ? COMING : WALK;
    }

    /**
     * The nearest storehouse holding the item whose place is loaded, in the city of the post (anywhere on the land of the kingdom for
     * a post outside the cities).
     */
    @Nullable
    private BlockPos storehouseWith(ServerLevel level, String item) {
        for (StorehouseInventory inventory : KingdomStorage.takingSources(level, post.kingdomId(), post.pos(), body.blockPosition())) {
            BlockPos pos = new BlockPos(inventory.pos().x(), inventory.pos().y(), inventory.pos().z());
            if (!level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get())) {
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
     * Walks to the storehouse and brings materials from it to the site. A storehouse the builder cannot get to does not stall
     * the construction: after a while the materials come to the site from where the builder got to.
     */
    @Nullable
    private WorkStatus fetch(ServerLevel level, Construction construction) {
        BlockPos from = fetchFrom;
        if (from == null) {
            return null;
        }
        if (!near(from, REACH_SQR) && ++walking <= ConstructionSettings.rules().giveUpTicks()) {
            moveTo(from);
            return null;
        }
        ConstructionWork.fetch(level.getServer(), construction, fetchItem, fetchAmount);
        stopFetching();
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
