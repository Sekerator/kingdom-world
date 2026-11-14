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
    /** Steps of the same layer a builder on a column looks at for one it reaches from there. */
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
        // Called away (night, danger, another task): the column comes down at once and the builder stands on the ground.
        takeColumnDownNow();
        drop();
    }

    private void drop() {
        site = null;
        plan = null;
        step = -1;
        fetchFrom = null;
        walking = 0;
        timer = 0;
        forgetSpot();
    }

    private void forgetSpot() {
        spot = null;
        spots = null;
        refused.clear();
        nearest = false;
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
            WorkStatus problem = choose(server, me, now);
            if (problem != null) {
                return leave(hasCarried() ? WorkStatus.WORKING : problem);
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
            if (column != null) {
                // Down from the column first: the storehouse is on the ground.
                leaving = true;
                return null;
            }
            return fetch(level, construction);
        }
        if (current.kind() == BuildPlan.Kind.PLACE && !construction.hasDelivered(current.cost())) {
            int fetching = planFetch(level, construction, steps, current, now);
            if (fetching == NO_FETCH) {
                construction.setStatus(Construction.Status.NO_MATERIALS);
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                next(construction, me);
                report(WorkStatus.NO_MATERIALS);
            } else if (fetching == CART) {
                // The kingdom has it in storehouses out of reach: a cart brings it, another block meanwhile.
                ConstructionWork.defer(construction, step, now + Carts.CART_TICKS);
                next(construction, me);
            }
            return null;
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
            ConstructionWork.defer(construction, step, now + COLUMN_TICKS);
            next(construction, me);
            return null;
        }
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
                ConstructionWork.defer(construction, step, now + BLOCKED_TICKS);
                report(WorkStatus.BLOCKED);
            }
            case NO_MATERIALS -> {
                if (construction.kind() == Construction.Kind.EARTHWORK && current.kind() == BuildPlan.Kind.CLEAR) {
                    // Lava beside the dig waits for cobblestone in the storehouses: the builder goes on with the rest meanwhile.
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
     * Picks the next step of the constructions within reach, or says why there is none. A builder on a column takes a step of
     * the same layer it reaches from there first.
     */
    @Nullable
    private WorkStatus choose(MinecraftServer server, UUID me, long now) {
        WorkStatus problem = WorkStatus.NO_CONSTRUCTIONS;
        double reach = ConstructionSettings.rules().reach();
        for (Construction construction : ConstructionWork.candidates(server, post)) {
            Optional<BuildPlan> found = ConstructionWork.plan(construction);
            if (found.isEmpty()) {
                continue;
            }
            BuildPlan steps = found.get();
            Set<Integer> passed = new HashSet<>();
            BuildPlan.Step first = null;
            for (int i = 0; i < TRIES + NEAR_STEPS; i++) {
                OptionalInt next = steps.next(construction, index -> passed.contains(index) || ConstructionWork.busy(construction, index, me, now));
                if (next.isEmpty()) {
                    if (first == null && !steps.cleared(construction) && construction.status() == Construction.Status.BLOCKED) {
                        problem = WorkStatus.BLOCKED;
                    } else if (first == null && construction.status() == Construction.Status.NO_MATERIALS && problem != WorkStatus.BLOCKED) {
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
                if (Scaffolds.inUse(construction.dimension(), candidate.x(), candidate.y(), candidate.z(), now)) {
                    // A builder stands on a scaffold there: the block waits until the column is taken down.
                    ConstructionWork.defer(construction, candidate.index(), now + COLUMN_TICKS);
                    continue;
                }
                if (column != null && column.construction() == construction.id()
                        && !inReach(new BlockPos(candidate.x(), candidate.y(), candidate.z()), reach)) {
                    // On a column: a block of the same layer reached from here goes first, then the first one.
                    if (first == null) {
                        first = candidate;
                    }
                    if (candidate.y() == first.y() && passed.size() < NEAR_STEPS) {
                        passed.add(candidate.index());
                        continue;
                    }
                    candidate = first;
                }
                take(construction, steps, candidate, me);
                return null;
            }
            if (first != null) {
                take(construction, steps, first, me);
                return null;
            }
        }
        report(problem);
        return problem;
    }

    private void take(Construction construction, BuildPlan steps, BuildPlan.Step candidate, UUID me) {
        site = construction;
        plan = steps;
        step = candidate.index();
        // The post shows what its builder places now.
        post.setCurrent(candidate.cost().isEmpty() ? "" : candidate.cost().keySet().iterator().next(), 0.0);
        walking = 0;
        timer = 0;
        forgetSpot();
        ConstructionWork.claim(construction, step, me);
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
        forgetSpot();
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
        while (!spots.isEmpty()) {
            if (paths >= PATHS_PER_TICK || !PathBudget.tryAcquire()) {
                // The others are looked at in the next tick.
                return false;
            }
            Scaffolding.Spot candidate = spots.remove(0);
            if (refused.contains(Scaffolding.pack(candidate.x(), 0, candidate.z()))) {
                continue;
            }
            paths++;
            BlockPos base = new BlockPos(candidate.x(), candidate.ground(), candidate.z());
            Path path = body.blockPosition().equals(base) ? null : body.getNavigation().createPath(base, 0);
            if (body.blockPosition().equals(base) || path != null && path.canReach()) {
                spot = candidate;
                walking = 0;
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

    /**
     * Picks a storehouse with the first material the site lacks for the block.
     *
     * @return whether one was found
     */
    /** {@link #planFetch}: nothing to fetch anywhere. */
    private static final int NO_FETCH = 0;
    /** {@link #planFetch}: the builder goes to a storehouse, or nothing is missing. */
    private static final int WALK = 1;
    /** {@link #planFetch}: a cart brings it from storehouses out of reach. */
    private static final int CART = 2;

    private int planFetch(ServerLevel level, Construction construction, BuildPlan steps, BuildPlan.Step current, long now) {
        for (Map.Entry<String, Integer> entry : current.cost().entrySet()) {
            int missing = entry.getValue() - construction.delivered().getOrDefault(entry.getKey(), 0);
            if (missing <= 0) {
                continue;
            }
            if (ConstructionWork.available(level.getServer(), construction, entry.getKey()) < missing) {
                return NO_FETCH;
            }
            int needed = ConstructionWork.needed(construction, steps).getOrDefault(entry.getKey(), missing);
            BlockPos storehouse = storehouseWith(level, entry.getKey());
            if (storehouse == null) {
                // A village without a storehouse of its own: the materials come from storehouses of the kingdom out of reach.
                Carts.order(construction, entry.getKey(), Math.max(missing, Math.min(Carts.LOAD, needed)), now);
                return CART;
            }
            fetchFrom = storehouse;
            fetchItem = entry.getKey();
            fetchAmount = Math.max(missing, Math.min(64, needed));
            walking = 0;
            return WALK;
        }
        return WALK;
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
