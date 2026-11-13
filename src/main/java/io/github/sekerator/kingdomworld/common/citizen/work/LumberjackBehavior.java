package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.ScanBudget;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Groves;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The lumberjack: fells natural trees in the zone of its post with an axe, takes down their crowns, gathers what falls
 * and plants a sapling where every tree stood, so that the grove never runs out; on free natural ground of the zone it
 * plants new saplings on a sparse grid ({@link Groves}). Saplings come from its bag, the container of the post (carriers
 * bring them there from the storehouses) or the storehouses; when there are none, the post says so.
 */
public final class LumberjackBehavior implements ProfessionBehavior {
    /** Logs of an average tree. */
    static final double LOGS_PER_TREE = 5.0;
    /** Saplings the container of the post should hold; carriers bring them from the storehouses. */
    static final int SAPLINGS_WANTED = 16;
    /** Saplings the lumberjack leaves in the container of its post at most; the rest go to a storehouse with the harvest. */
    static final int SAPLINGS_KEPT = 32;

    /** A tree a lumberjack began to fell and has not finished, by citizen: the next round of work finishes it first. */
    private static final Map<UUID, Unfinished> UNFINISHED = new HashMap<>();

    private record Unfinished(BlockPos base, @Nullable BlockState leafState) {
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new WorkGoal(body, this);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        PostWork.workAbstractly(server, citizen, this, intervalTicks);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public Equipment.ToolNeed tool() {
        return Equipment.ToolNeed.AXE;
    }

    @Override
    public boolean risky() {
        return true;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.FOREST;
    }

    /**
     * Saplings of the kind of the grove, when the container of the post holds few saplings.
     */
    @Override
    public Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        Item kind = PostWork.item(post.current());
        if (kind == null || !Trees.isSapling(new ItemStack(kind))) {
            return Map.of();
        }
        int saplings = 0;
        for (Map.Entry<String, Integer> entry : container.entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item != null && Trees.isSapling(new ItemStack(item))) {
                saplings += entry.getValue();
            }
        }
        return saplings >= SAPLINGS_WANTED / 2 ? Map.of() : Map.of(PostWork.itemId(new ItemStack(kind)), SAPLINGS_WANTED - saplings);
    }

    /**
     * Saplings stay in the container of the post: the lumberjack plants them.
     */
    @Override
    public boolean isInput(ItemStack stack) {
        return Trees.isSapling(stack);
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new Survey(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), 1));
    }

    /**
     * Forgets the trees left half felled, when the server stops.
     */
    public static void reset() {
        UNFINISHED.clear();
    }

    /**
     * Sets the kind of sapling the grove of a post is planted with, which the post asks carriers for.
     */
    static void setGroveKind(WorkPost post, Item sapling) {
        String id = PostWork.itemId(new ItemStack(sapling));
        if (!id.equals(post.current())) {
            post.setCurrent(id, 0.0);
        }
    }

    /**
     * Counts the trees, the saplings and the free spots for new saplings of a zone, column by column: the grove that is
     * planted again gives a tree on every one of them every day and a half.
     */
    private static final class Survey extends ZoneSurvey {
        private final GroveScan scan;

        Survey(ServerLevel level, WorkPost post, WorkZone zone) {
            super(level, post, zone);
            this.scan = new GroveScan(level, post, zone, new LandCheck(level, post.kingdomId()));
        }

        @Override
        public int step(int budget) {
            int used = 0;
            while (used < budget && !scan.done()) {
                used += scan.step();
            }
            return used;
        }

        @Override
        public boolean done() {
            return scan.done();
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
        }

        @Override
        protected void finish() {
            Item kind = scan.saplingKind();
            if (kind != null && PostWork.item(post.current()) == null) {
                setGroveKind(post, kind);
            }
        }

        @Override
        public PostYield result() {
            return scan.estimate();
        }

        @Override
        public WorkStatus status() {
            return scan.trees() > 0 ? WorkStatus.WORKING
                    : !scan.saplings().isEmpty() || !scan.newSpots(1).isEmpty() ? WorkStatus.WAITING : WorkStatus.NO_TREES;
        }
    }

    /**
     * The lumberjack at work: looks over the zone, fells its trees one after another (log by log, then the crown) and
     * plants a sapling where each stood, then plants new saplings on the free spots of the grid.
     */
    static final class WorkGoal extends PostWorkGoal {
        /** Ticks the lumberjack walks towards a tree or a spot before it gives up; a large zone is a long walk. */
        private static final int GIVE_UP_TICKS = 600;
        private final Deque<BlockPos> bases = new ArrayDeque<>();
        private final Deque<BlockPos> plantings = new ArrayDeque<>();
        @Nullable
        private GroveScan scan;
        private boolean scanned;
        private int saplings;
        private int plantedNew;
        /** Whether the last sapling the lumberjack wanted to plant was nowhere to be had; it stays so until one is planted. */
        private boolean lacksSaplings;
        /** Whether the lumberjack wanted to plant anything in this round of work. */
        private boolean planting;
        @Nullable
        private Unfinished resume;
        @Nullable
        private Trees.Tree tree;
        @Nullable
        private BlockPos spot;
        private int next;
        private int timer;
        private int walking;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected void onStart() {
            bases.clear();
            plantings.clear();
            scan = new GroveScan((ServerLevel) body.level(), post, zone, land);
            scanned = false;
            saplings = 0;
            plantedNew = 0;
            tree = null;
            spot = null;
            planting = false;
            UUID id = body.citizenId();
            resume = id == null ? null : UNFINISHED.get(id);
        }

        /**
         * By day, and at dusk until the tree it is felling is down: a tree is never left half felled for the night.
         */
        @Override
        protected boolean isWorkTime() {
            return super.isWorkTime() || tree != null && next > 0;
        }

        @Override
        protected int deliveryPatience() {
            return GIVE_UP_TICKS;
        }

        /**
         * Saplings go into the container only up to {@link #SAPLINGS_KEPT}; more go to a storehouse.
         */
        @Override
        protected int containerRoom(ItemStack stack, IItemHandler container) {
            if (!Trees.isSapling(stack)) {
                return stack.getCount();
            }
            int held = 0;
            for (int slot = 0; slot < container.getSlots(); slot++) {
                ItemStack there = container.getStackInSlot(slot);
                if (Trees.isSapling(there)) {
                    held += there.getCount();
                }
            }
            return Math.max(0, SAPLINGS_KEPT - held);
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (tree == null) {
                if (resume != null) {
                    resumeUnfinished(level);
                    return null;
                }
                if (!scanned) {
                    scan(level);
                    return null;
                }
                BlockPos base = bases.poll();
                if (base != null) {
                    tree = Trees.find(level, base, zone, land, GatheringSettings.rules().maxTreeBlocks());
                    next = 0;
                    timer = 0;
                    walking = 0;
                    if (tree != null) {
                        report(working());
                    }
                    return null;
                }
                if (spot != null || !plantings.isEmpty()) {
                    plantNew(level);
                    return null;
                }
                if (!planting) {
                    // Nothing wanted planting this round: no reason to say there are no saplings.
                    lacksSaplings = false;
                }
                if (hasCarried()) {
                    return working();
                }
                return lacksSaplings ? WorkStatus.NO_SAPLINGS
                        : saplings > 0 || plantedNew > 0 ? WorkStatus.WAITING : WorkStatus.NO_TREES;
            }
            BlockPos stand = tree.bases().isEmpty() ? tree.logs().get(tree.logs().size() - 1) : tree.bases().get(0);
            lookAt(stand);
            if (!near(stand, REACH_SQR)) {
                moveTo(stand);
                if (++walking > GIVE_UP_TICKS) {
                    tree = null;
                }
                return null;
            }
            if (next < tree.logs().size()) {
                if (timer > 0) {
                    timer--;
                    return null;
                }
                if (next == 0) {
                    remember(stand);
                }
                chop(level, tree.logs().get(next++));
                return null;
            }
            takeDownCrown(level);
            replant(level);
            forget();
            tree = null;
            return isCarryingFull() ? working() : null;
        }

        private WorkStatus working() {
            return lacksSaplings ? WorkStatus.NO_SAPLINGS : WorkStatus.WORKING;
        }

        // ---------------------------------------------------------------- looking over the zone

        private void scan(ServerLevel level) {
            int budget = ScanBudget.take(ScanBudget.PER_WORKER);
            while (budget > 0 && !scan.done()) {
                budget -= scan.step();
            }
            if (!scan.done()) {
                return;
            }
            scanned = true;
            saplings = scan.saplings().size();
            // The nearest trees first, so that a large zone is not crossed back and forth.
            List<BlockPos> found = new ArrayList<>(scan.bases());
            found.sort(Comparator.comparingDouble(pos -> pos.distSqr(postPos)));
            bases.addAll(found);
            plantings.addAll(scan.newSpots(Groves.NEW_PER_ROUND));
            Item kind = scan.saplingKind();
            if (kind != null && PostWork.item(post.current()) == null) {
                setGroveKind(post, kind);
            }
            // Far from players the grove gives at least what it regrows: it is planted again, so it never runs out.
            PostYield estimate = scan.estimate();
            if (!estimate.isEmpty()) {
                post.setYield(post.yield().atLeast(estimate));
            }
            scan = null;
        }

        // ---------------------------------------------------------------- felling

        private void chop(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!Trees.isLog(state) || !land.owns(pos) || level.getBlockEntity(pos) != null) {
                return;
            }
            lookAt(pos);
            ItemStack axe = tool();
            for (ItemStack drop : Trees.drops(level, pos, state, axe, body)) {
                gather(drop);
            }
            level.destroyBlock(pos, false, body);
            useTool(1);
            float speed = Math.max(1.0f, axe.getDestroySpeed(state));
            timer = Math.max(4, (int) Math.ceil(30.0f * Math.max(0.5f, state.getDestroySpeed(level, pos)) / speed));
        }

        private void takeDownCrown(ServerLevel level) {
            ItemStack axe = tool();
            for (BlockPos leaf : tree.leaves()) {
                BlockState state = level.getBlockState(leaf);
                if (!Trees.isNaturalLeaves(state) || !land.owns(leaf)) {
                    continue;
                }
                for (ItemStack drop : Trees.drops(level, leaf, state, axe, body)) {
                    gather(drop);
                }
                level.removeBlock(leaf, false);
            }
        }

        /**
         * Remembers the tree it begins to fell, so that a lumberjack that goes away halfway (the players leave, it is
         * wounded) finishes it when it comes back rather than leaving a stump without a crown.
         */
        private void remember(BlockPos base) {
            UUID id = body.citizenId();
            if (id != null) {
                UNFINISHED.put(id, new Unfinished(base.immutable(), tree.leafState()));
            }
        }

        private void forget() {
            UUID id = body.citizenId();
            if (id != null) {
                UNFINISHED.remove(id);
            }
        }

        private void resumeUnfinished(ServerLevel level) {
            Unfinished unfinished = resume;
            resume = null;
            tree = Trees.isLog(level.getBlockState(unfinished.base()))
                    ? Trees.find(level, unfinished.base(), zone, land, GatheringSettings.rules().maxTreeBlocks(), true) : null;
            if (tree == null) {
                forget();
                return;
            }
            tree = tree.withLeafState(unfinished.leafState());
            next = 0;
            timer = 0;
            walking = 0;
            report(working());
        }

        // ---------------------------------------------------------------- planting

        /**
         * Plants where the tree stood: four of its kind in a square where a giant tree stood, if four can be had; otherwise
         * one of its kind on the trunk's place (or right beside it, where the ground there takes no sapling); otherwise any
         * sapling that grows alone on that ground.
         */
        private void replant(ServerLevel level) {
            List<BlockPos> spots = tree.bases();
            if (spots.isEmpty()) {
                return;
            }
            planting = true;
            Item kind = tree.leafState() == null ? null : Trees.saplingOf(level, tree.leafState());
            if (kind != null) {
                setGroveKind(post, kind);
                BlockPos corner = squareCorner(spots);
                if (corner != null && plantSquare(level, corner, kind)) {
                    return;
                }
            }
            List<BlockPos> around = around(spots);
            if (kind != null && !Trees.growsInFours(new ItemStack(kind)) && plantOne(level, around, stack -> stack.is(kind))) {
                return;
            }
            if (!plantOne(level, around, stack -> !Trees.growsInFours(stack))) {
                lacksSaplings = true;
            }
        }

        /**
         * One new sapling on a free spot of the grid, of the grove's kind if there is one.
         */
        private void plantNew(ServerLevel level) {
            if (spot == null) {
                spot = plantings.poll();
                walking = 0;
                if (spot == null) {
                    return;
                }
                planting = true;
                report(working());
            }
            lookAt(spot);
            if (!near(spot, REACH_SQR)) {
                moveTo(spot);
                if (++walking > GIVE_UP_TICKS) {
                    spot = null;
                }
                return;
            }
            BlockPos at = spot;
            spot = null;
            if (!GroveScan.plantable(level, post.kingdomId(), at)) {
                return;
            }
            Item kind = PostWork.item(post.current());
            List<BlockPos> one = List.of(at);
            boolean done = kind != null && Trees.growsInFours(new ItemStack(kind)) ? plantSquare(level, at, kind)
                    : kind != null && plantOne(level, one, stack -> stack.is(kind));
            if (!done && !plantOne(level, one, stack -> !Trees.growsInFours(stack))) {
                lacksSaplings = true;
                plantings.clear();
                return;
            }
            plantedNew++;
        }

        /**
         * Plants one sapling that passes the test on the first of the spots it grows on.
         *
         * @return whether one was planted
         */
        private boolean plantOne(ServerLevel level, List<BlockPos> spots, Predicate<ItemStack> kind) {
            Predicate<ItemStack> fits = stack -> Trees.isSapling(stack) && kind.test(stack) && firstFit(level, spots, stack) != null;
            ItemStack sapling = takeSapling(level, fits, 1);
            if (sapling.isEmpty()) {
                return false;
            }
            BlockPos spot = firstFit(level, spots, sapling);
            Block block = Trees.saplingBlock(sapling);
            if (spot == null || block == null) {
                carry(sapling);
                return false;
            }
            place(level, spot, block, sapling);
            return true;
        }

        /**
         * Plants four saplings of a kind in a square from its north-west corner, if four can be had and all four places
         * take them.
         */
        private boolean plantSquare(ServerLevel level, BlockPos corner, Item kind) {
            ItemStack probe = new ItemStack(kind);
            Block block = Trees.saplingBlock(probe);
            List<BlockPos> square = List.of(corner, corner.east(), corner.south(), corner.south().east());
            if (block == null) {
                return false;
            }
            for (BlockPos spot : square) {
                if (!fits(level, spot, block)) {
                    return false;
                }
            }
            ItemStack saplings = takeSapling(level, stack -> stack.is(kind), 4);
            if (saplings.getCount() < 4) {
                if (!saplings.isEmpty()) {
                    carry(saplings);
                }
                return false;
            }
            for (BlockPos spot : square) {
                place(level, spot, block, saplings.split(1));
            }
            return true;
        }

        private void place(ServerLevel level, BlockPos spot, Block block, ItemStack sapling) {
            lookAt(spot);
            level.setBlockAndUpdate(spot, block.defaultBlockState());
            body.swing(InteractionHand.MAIN_HAND);
            PostWork.planted(post, sapling);
            lacksSaplings = false;
        }

        /**
         * Takes saplings that pass the test: from the bag first, then from the container of the post, then from the
         * storehouses.
         *
         * @return what was found, up to {@code count} of one kind; empty if none
         */
        private ItemStack takeSapling(ServerLevel level, Predicate<ItemStack> test, int count) {
            ItemStack found = ItemStack.EMPTY;
            for (int slot = 0; slot < body.carried().getContainerSize() && found.getCount() < count; slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty() && test.test(stack) && (found.isEmpty() || ItemStack.isSameItemSameTags(found, stack))) {
                    found = merge(found, body.carried().removeItem(slot, count - found.getCount()));
                }
            }
            PostContainers.Container container = found.getCount() >= count ? null : PostContainers.find(level, postPos);
            if (container != null) {
                IItemHandler handler = container.handler();
                for (int slot = 0; slot < handler.getSlots() && found.getCount() < count; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty() && test.test(stack) && (found.isEmpty() || ItemStack.isSameItemSameTags(found, stack))) {
                        found = merge(found, handler.extractItem(slot, count - found.getCount(), false));
                    }
                }
            }
            if (found.getCount() < count) {
                ItemStack like = found;
                Predicate<ItemStack> same = like.isEmpty() ? test : stack -> ItemStack.isSameItemSameTags(like, stack);
                for (ItemStack taken : KingdomStorage.take(level.getServer(), post.kingdomId(), same, count - found.getCount(), post.pos())) {
                    if (found.isEmpty() || ItemStack.isSameItemSameTags(found, taken)) {
                        found = merge(found, taken);
                    } else {
                        carry(taken);
                    }
                }
            }
            return found;
        }

        private static ItemStack merge(ItemStack into, ItemStack more) {
            if (more.isEmpty()) {
                return into;
            }
            if (into.isEmpty()) {
                return more;
            }
            into.grow(more.getCount());
            return into;
        }

        @Nullable
        private BlockPos firstFit(ServerLevel level, List<BlockPos> spots, ItemStack sapling) {
            Block block = Trees.saplingBlock(sapling);
            if (block == null) {
                return null;
            }
            for (BlockPos spot : spots) {
                if (fits(level, spot, block)) {
                    return spot;
                }
            }
            return null;
        }

        private boolean fits(ServerLevel level, BlockPos spot, Block sapling) {
            if (!land.owns(spot) || !level.isLoaded(spot)) {
                return false;
            }
            BlockState there = level.getBlockState(spot);
            return there.canBeReplaced() && there.getFluidState().isEmpty() && sapling.defaultBlockState().canSurvive(level, spot);
        }

        /**
         * The places for a sapling where a tree stood: its bases, then the blocks right around the first one, a step up
         * or down included, for a tree whose own ground takes no sapling.
         */
        private static List<BlockPos> around(List<BlockPos> bases) {
            List<BlockPos> spots = new ArrayList<>(bases);
            BlockPos first = bases.get(0);
            for (int dy = 0; dy >= -1; dy--) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos spot = first.offset(dx, dy, dz);
                        if (!spots.contains(spot)) {
                            spots.add(spot);
                        }
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = first.offset(dx, 1, dz);
                    if (!spots.contains(spot)) {
                        spots.add(spot);
                    }
                }
            }
            return spots;
        }

        /**
         * The north-west corner of a square of four bases, the trunk of a giant tree, if the bases make one.
         */
        @Nullable
        private static BlockPos squareCorner(List<BlockPos> bases) {
            if (bases.size() < 4) {
                return null;
            }
            for (BlockPos base : bases) {
                if (bases.contains(base.east()) && bases.contains(base.south()) && bases.contains(base.south().east())) {
                    return base;
                }
            }
            return null;
        }
    }
}
