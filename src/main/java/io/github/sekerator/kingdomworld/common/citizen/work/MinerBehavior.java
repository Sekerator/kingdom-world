package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.MinePlan;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The miner: digs a mine below its post along {@link MinePlan}, breaking only natural rock, earth, gravel and ore
 * (by their tags, so the stones and ores of other mods count), never blocks with contents and never anything off
 * the kingdom's land. Ore needs a pickaxe strong enough for it. Before it opens a block next to lava or water it
 * walls the fluid off with cobblestone, and it lights the tunnels with torches from the storehouses.
 */
public final class MinerBehavior implements ProfessionBehavior {
    /** Blocks one miner breaks in a working day at most. */
    static final double BLOCKS_PER_DAY = 240.0;
    /** Torches the post's container should hold; carriers bring them from the storehouses. */
    static final int TORCHES_WANTED = 16;

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
        return Equipment.ToolNeed.PICKAXE;
    }

    @Override
    public boolean risky() {
        return true;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.MINE;
    }

    @Override
    public Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        int torches = container.getOrDefault(PostWork.itemId(new ItemStack(Items.TORCH)), 0);
        return torches >= TORCHES_WANTED / 2 ? Map.of() : Map.of(PostWork.itemId(new ItemStack(Items.TORCH)), TORCHES_WANTED - torches);
    }

    @Override
    public boolean isInput(ItemStack stack) {
        return isTorch(stack);
    }

    /**
     * Rock, earth, gravel and ore a miner may break: natural blocks by their tags.
     */
    public static boolean isMinable(BlockState state) {
        return state.is(Tags.Blocks.STONE) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Tags.Blocks.GRAVEL) || state.is(BlockTags.DIRT)
                || state.is(Tags.Blocks.SAND) || state.is(Tags.Blocks.SANDSTONE) || state.is(BlockTags.TERRACOTTA)
                || state.is(Tags.Blocks.NETHERRACK) || state.is(Tags.Blocks.END_STONES);
    }

    public static boolean isTorch(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TorchBlock
                && !(item.getBlock() instanceof WallTorchBlock);
    }

    static boolean isCobble(ItemStack stack) {
        return stack.is(Tags.Items.COBBLESTONE) && stack.getItem() instanceof BlockItem;
    }

    /**
     * Whether a block is open: a body can stand in it and nothing flows there.
     */
    static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new Survey(level, post);
    }

    /**
     * Looks at the blocks of the mine plan and what they drop with an iron pickaxe.
     */
    private static final class Survey extends ZoneSurvey {
        private final Map<String, Integer> drops = new LinkedHashMap<>();
        private int minable;
        private int sampled;

        Survey(ServerLevel level, WorkPost post) {
            super(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), GatheringSettings.rules().mineLevels()));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (pos.getY() >= post.pos().y() + 2 || !isMinable(state) || level.getBlockEntity(pos) != null) {
                return;
            }
            minable++;
            // A sample of the blocks tells what the mine gives.
            if (sampled < 96 && (minable % 7 == 1 || state.is(Tags.Blocks.ORES))) {
                sampled++;
                for (ItemStack drop : Trees.drops(level, pos.immutable(), state, new ItemStack(Items.IRON_PICKAXE), null)) {
                    drops.merge(PostWork.itemId(drop), drop.getCount(), Integer::sum);
                }
            }
        }

        @Override
        public PostYield result() {
            int total = 0;
            for (int count : drops.values()) {
                total += count;
            }
            if (total == 0) {
                return PostYield.NONE;
            }
            int samples = Math.max(1, sampled);
            Map<String, Double> rates = new HashMap<>();
            for (Map.Entry<String, Integer> entry : drops.entrySet()) {
                rates.put(entry.getKey(), BLOCKS_PER_DAY * entry.getValue() / samples);
            }
            return new PostYield(rates, Map.of(), BLOCKS_PER_DAY, false);
        }

        @Override
        public WorkStatus status() {
            return minable > 0 ? WorkStatus.WORKING : WorkStatus.NO_ROCK;
        }
    }

    /**
     * The miner at work: goes through the cells of the mine plan, digging every cell whose entrance is open.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 300;
        /** A torch every so many cells, where it is dark. */
        private static final int TORCH_SPACING = 5;
        private List<MinePlan.Cell> plan = List.of();
        private int cursor;
        private int target = -1;
        private int block;
        private int timer;
        private int walking;
        private int dug;
        private boolean weakTool;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected boolean isWorkTime() {
            // Down in the mine day and night are the same; miners rest only when the day's work would be done.
            return true;
        }

        @Override
        protected void onStart() {
            plan = MinePlan.cells(postPos.getX(), postPos.getY(), postPos.getZ(), post.radius(), GatheringSettings.rules().mineLevels());
            target = -1;
            weakTool = false;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (target < 0) {
                target = nextCell(level);
                walking = 0;
                block = 0;
                timer = 0;
                if (target < 0) {
                    cursor = 0;
                    return hasCarried() ? WorkStatus.WORKING : weakTool ? WorkStatus.TOOL_TOO_WEAK : WorkStatus.NO_ROCK;
                }
                report(WorkStatus.WORKING);
            }
            MinePlan.Cell cell = plan.get(target);
            BlockPos stand = cell.from() < 0 ? postPos : cellPos(plan.get(cell.from()));
            BlockPos here = cellPos(cell);
            if (!near(here, REACH_SQR) && !near(stand, 1.5 * 1.5)) {
                moveTo(stand);
                if (++walking > GIVE_UP_TICKS) {
                    // Unreachable for now; the next round tries again.
                    cursor = target + 1;
                    target = -1;
                }
                return null;
            }
            lookAt(here);
            if (timer > 0) {
                timer--;
                return null;
            }
            if (block >= cell.height()) {
                finishCell(level, cell);
                target = -1;
                return isCarryingFull() ? WorkStatus.WORKING : null;
            }
            // Top down, so that nothing falls on the miner's head unannounced.
            BlockPos pos = here.above(cell.height() - 1 - block);
            if (!dig(level, pos, cell)) {
                cursor = target + 1;
                target = -1;
                return null;
            }
            block++;
            return null;
        }

        private static BlockPos cellPos(MinePlan.Cell cell) {
            return new BlockPos(cell.x(), cell.y(), cell.z());
        }

        /**
         * The next cell to dig: not open yet, with an open entrance, on the land.
         */
        private int nextCell(ServerLevel level) {
            for (int i = cursor; i < plan.size(); i++) {
                MinePlan.Cell cell = plan.get(i);
                BlockPos pos = cellPos(cell);
                if (!land.owns(pos) || !level.isLoaded(pos)) {
                    continue;
                }
                boolean open = true;
                for (int dy = 0; dy < cell.height(); dy++) {
                    open &= isOpen(level, pos.above(dy));
                }
                if (open) {
                    continue;
                }
                if (cell.from() >= 0) {
                    MinePlan.Cell from = plan.get(cell.from());
                    BlockPos entrance = cellPos(from);
                    if (!isOpen(level, entrance) || !isOpen(level, entrance.above())) {
                        continue;
                    }
                }
                cursor = i;
                return i;
            }
            return -1;
        }

        /**
         * Breaks one block of a cell, after walling off fluids next to it.
         *
         * @return whether the cell can go on being dug
         */
        private boolean dig(ServerLevel level, BlockPos pos, MinePlan.Cell cell) {
            BlockState state = level.getBlockState(pos);
            if (isOpen(level, pos)) {
                return true;
            }
            if (!state.getFluidState().isEmpty()) {
                // Water or lava came into the tunnel: it is closed off and the cell left alone.
                seal(level, pos);
                return false;
            }
            if (!land.owns(pos) || level.getBlockEntity(pos) != null || !isMinable(state) || state.getDestroySpeed(level, pos) < 0) {
                return false;
            }
            ItemStack pickaxe = tool();
            if (state.requiresCorrectToolForDrops() && !pickaxe.isCorrectToolForDrops(state)) {
                weakTool = true;
                return false;
            }
            for (Direction direction : Direction.values()) {
                BlockPos side = pos.relative(direction);
                if (isPartOfTunnel(side, cell)) {
                    continue;
                }
                if (!level.getFluidState(side).isEmpty() && !seal(level, side)) {
                    // No cobblestone to wall it off with: the block stays.
                    return false;
                }
            }
            for (ItemStack drop : Trees.drops(level, pos, state, pickaxe, body)) {
                gather(drop);
            }
            level.destroyBlock(pos, false, body);
            useTool(1);
            float speed = Math.max(1.0f, pickaxe.getDestroySpeed(state));
            timer = Math.max(3, (int) Math.ceil(30.0f * Math.max(0.3f, state.getDestroySpeed(level, pos)) / speed));
            return true;
        }

        private boolean isPartOfTunnel(BlockPos pos, MinePlan.Cell cell) {
            if (pos.getX() == cell.x() && pos.getZ() == cell.z() && pos.getY() >= cell.y() && pos.getY() < cell.y() + cell.height()) {
                return true;
            }
            if (cell.from() >= 0) {
                MinePlan.Cell from = plan.get(cell.from());
                return pos.getX() == from.x() && pos.getZ() == from.z() && pos.getY() >= from.y() && pos.getY() < from.y() + from.height();
            }
            return pos.getX() == postPos.getX() && pos.getZ() == postPos.getZ();
        }

        /**
         * Fills a block holding a fluid with cobblestone carried, from the post's container or from the
         * storehouses.
         *
         * @return whether the fluid is walled off
         */
        private boolean seal(ServerLevel level, BlockPos pos) {
            if (!land.owns(pos) || level.getBlockEntity(pos) != null) {
                return false;
            }
            ItemStack cobble = takeCobble(level);
            if (cobble.isEmpty()) {
                return false;
            }
            Block block = ((BlockItem) cobble.getItem()).getBlock();
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            return true;
        }

        private ItemStack takeCobble(ServerLevel level) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                if (isCobble(body.carried().getItem(slot))) {
                    return body.carried().removeItem(slot, 1);
                }
            }
            List<ItemStack> taken = KingdomStorage.take(level.getServer(), post.kingdomId(), MinerBehavior::isCobble, 8, post.pos());
            ItemStack first = ItemStack.EMPTY;
            for (ItemStack stack : taken) {
                if (first.isEmpty()) {
                    first = stack.split(1);
                }
                if (!stack.isEmpty()) {
                    carry(stack);
                }
            }
            return first;
        }

        private void finishCell(ServerLevel level, MinePlan.Cell cell) {
            dug++;
            BlockPos floor = cellPos(cell);
            if (dug % TORCH_SPACING != 0 || level.getBrightness(LightLayer.BLOCK, floor) >= 8
                    || level.getBrightness(LightLayer.SKY, floor) >= 8 || !isOpen(level, floor)
                    || !Blocks.TORCH.defaultBlockState().canSurvive(level, floor)) {
                return;
            }
            ItemStack torch = takeTorch(level);
            if (torch.isEmpty()) {
                return;
            }
            Block block = ((BlockItem) torch.getItem()).getBlock();
            BlockState state = block.defaultBlockState();
            if (state.canSurvive(level, floor)) {
                level.setBlockAndUpdate(floor, state);
            } else {
                carry(torch);
            }
        }

        /**
         * A torch from the post's container, carried, or from the storehouses.
         */
        private ItemStack takeTorch(ServerLevel level) {
            PostContainers.Container container = PostContainers.find(level, postPos);
            if (container != null) {
                for (int slot = 0; slot < container.handler().getSlots(); slot++) {
                    if (isTorch(container.handler().getStackInSlot(slot))) {
                        ItemStack taken = container.handler().extractItem(slot, 1, false);
                        if (!taken.isEmpty()) {
                            return taken;
                        }
                    }
                }
            }
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                if (isTorch(body.carried().getItem(slot))) {
                    return body.carried().removeItem(slot, 1);
                }
            }
            List<ItemStack> taken = KingdomStorage.take(level.getServer(), post.kingdomId(), MinerBehavior::isTorch, 1, post.pos());
            return taken.isEmpty() ? ItemStack.EMPTY : taken.get(0);
        }
    }
}
