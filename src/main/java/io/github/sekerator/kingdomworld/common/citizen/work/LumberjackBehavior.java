package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.ScanBudget;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The lumberjack: fells natural trees in the zone of its post with an axe, takes down their crowns, gathers what
 * falls and plants a sapling of the same kind where the tree stood.
 */
public final class LumberjackBehavior implements ProfessionBehavior {
    /** Trees one lumberjack fells in a working day at most. */
    static final double TREES_PER_DAY = 16.0;
    /** Days a sapling needs to grow into a tree, on average. */
    static final double REGROWTH_DAYS = 1.5;
    /** Logs of an average tree. */
    static final double LOGS_PER_TREE = 5.0;

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

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new Survey(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), 1));
    }

    /**
     * Counts the trees and saplings of a zone.
     */
    private static final class Survey extends ZoneSurvey {
        private int trees;
        private int saplings;
        private final Map<Item, Integer> logs = new HashMap<>();

        Survey(ServerLevel level, WorkPost post, WorkZone zone) {
            super(level, post, zone);
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (state.is(BlockTags.SAPLINGS)) {
                saplings++;
            } else if (Trees.isBase(level, pos, state) && Trees.isLog(level.getBlockState(pos.above()))) {
                trees++;
                logs.merge(state.getBlock().asItem(), 1, Integer::sum);
            }
        }

        @Override
        public PostYield result() {
            Item log = null;
            int best = 0;
            for (Map.Entry<Item, Integer> entry : logs.entrySet()) {
                if (entry.getValue() > best) {
                    log = entry.getKey();
                    best = entry.getValue();
                }
            }
            if (log == null) {
                return PostYield.NONE;
            }
            double felled = Math.min(TREES_PER_DAY, (trees + saplings * 0.5) / REGROWTH_DAYS);
            return new PostYield(Map.of(PostWork.itemId(new ItemStack(log)), felled * LOGS_PER_TREE), Map.of(),
                    felled * LOGS_PER_TREE, false);
        }

        @Override
        public WorkStatus status() {
            return trees > 0 ? WorkStatus.WORKING : saplings > 0 ? WorkStatus.WAITING : WorkStatus.NO_TREES;
        }
    }

    /**
     * The lumberjack at work: looks over the zone for trees, walks to one, fells it log by log, takes down its
     * crown and plants a sapling.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 300;
        private final Deque<BlockPos> bases = new ArrayDeque<>();
        private long scanIndex;
        private boolean scanned;
        private int saplings;
        @Nullable
        private Trees.Tree tree;
        private int next;
        private int timer;
        private int walking;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected void onStart() {
            bases.clear();
            scanIndex = 0;
            scanned = false;
            saplings = 0;
            tree = null;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (tree == null) {
                if (!scanned) {
                    scan(level);
                    return null;
                }
                BlockPos base = bases.poll();
                if (base == null) {
                    return hasCarried() ? WorkStatus.WORKING : saplings > 0 ? WorkStatus.WAITING : WorkStatus.NO_TREES;
                }
                tree = Trees.find(level, base, zone, land, GatheringSettings.rules().maxTreeBlocks());
                next = 0;
                timer = 0;
                walking = 0;
                if (tree != null) {
                    report(WorkStatus.WORKING);
                }
                return null;
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
                chop(level, tree.logs().get(next++));
                return null;
            }
            takeDownCrown(level);
            replant(level);
            tree = null;
            return isCarryingFull() ? WorkStatus.WORKING : null;
        }

        private void scan(ServerLevel level) {
            int budget = ScanBudget.take(ScanBudget.PER_WORKER);
            long total = zone.volume();
            int width = zone.width();
            int height = zone.height();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (budget-- > 0 && scanIndex < total) {
                long column = scanIndex / height;
                int y = zone.maxY() - (int) (scanIndex % height);
                pos.set(zone.minX() + (int) (column % width), y, zone.minZ() + (int) (column / width));
                scanIndex++;
                if (!land.owns(pos) || !level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.SAPLINGS)) {
                    saplings++;
                } else if (Trees.isBase(level, pos, state)) {
                    bases.add(pos.immutable());
                }
            }
            if (scanIndex >= total) {
                scanned = true;
            }
        }

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
         * Plants saplings of the tree's kind where it stood: one on every base if there are enough, otherwise one.
         */
        private void replant(ServerLevel level) {
            List<BlockPos> spots = tree.bases();
            if (spots.isEmpty()) {
                return;
            }
            Item kind = tree.leafState() == null ? null : Trees.saplingOf(level, tree.leafState());
            int wanted = spots.size();
            int have = countSaplings(kind);
            if (have < wanted && kind != null) {
                // Saplings of the same kind from the storehouses.
                for (ItemStack taken : KingdomStorage.take(level.getServer(), post.kingdomId(), stack -> stack.is(kind),
                        wanted - have, post.pos())) {
                    carry(taken);
                }
                have = countSaplings(kind);
            }
            int planted = 0;
            int limit = have >= wanted ? wanted : 1;
            Set<BlockPos> used = new HashSet<>();
            for (BlockPos spot : spots) {
                if (planted >= limit) {
                    break;
                }
                ItemStack sapling = takeSapling(kind);
                if (sapling.isEmpty()) {
                    break;
                }
                Block block = Trees.saplingBlock(sapling);
                BlockState state = block == null ? null : block.defaultBlockState();
                if (state != null && level.getBlockState(spot).canBeReplaced() && state.canSurvive(level, spot) && used.add(spot)) {
                    level.setBlockAndUpdate(spot, state);
                    planted++;
                } else {
                    carry(sapling);
                }
            }
        }

        private int countSaplings(@Nullable Item kind) {
            int count = 0;
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (kind != null ? stack.is(kind) : Trees.isSapling(stack)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private ItemStack takeSapling(@Nullable Item kind) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (kind != null ? stack.is(kind) : Trees.isSapling(stack)) {
                    return body.carried().removeItem(slot, 1);
                }
            }
            return ItemStack.EMPTY;
        }
    }
}
