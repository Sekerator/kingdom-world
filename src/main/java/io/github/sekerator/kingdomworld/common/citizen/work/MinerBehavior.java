package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
import io.github.sekerator.kingdomworld.core.citizen.MinePlan;
import io.github.sekerator.kingdomworld.core.citizen.MineProgress;
import io.github.sekerator.kingdomworld.core.citizen.MineRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.Veins;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The miner: digs a mine at its post along {@link MinePlan}, at the depth its goal asks for, breaking only natural
 * rock, earth, gravel and ore (by their tags, so the stones and ores of other mods count), never blocks with
 * contents and never anything off the kingdom's land. Ore it uncovers beside a tunnel it takes too, following the
 * vein from block to block, but never the rock around it and never ore it has not seen; the holes ore leaves in
 * the floor it fills with cobblestone. Ore needs a pickaxe strong enough for it. Before it opens a block next to
 * lava or water it walls the fluid off with cobblestone, and it lights the tunnels with torches from the
 * storehouses. A level on which it opens block after block without meeting ore it leaves for the next, deeper one.
 */
public final class MinerBehavior implements ProfessionBehavior {
    /** Blocks one miner breaks in a working day at most. */
    static final double BLOCKS_PER_DAY = 240.0;
    /** Torches the post's container should hold; carriers bring them from the storehouses. */
    static final int TORCHES_WANTED = 16;

    /**
     * The plan of the mine of a post, kept until the depth, the step between its levels, the land or the settings
     * change, with the count of the blocks its miners open without meeting ore.
     */
    record Plan(int depth, int step, long landVersion, MineRules rules, int levels, List<MinePlan.Cell> cells, MineProgress progress) {
    }

    private static final Map<WorldPos, Plan> PLANS = new HashMap<>();

    /**
     * The floor of the first level of the mine of a post: what its goal asks for, within what the world allows.
     */
    public static int depth(ServerLevel level, WorkPost post) {
        return GatheringSettings.mine().depth(post.mineGoal(), post.pos().y(), level.getMinBuildHeight());
    }

    /**
     * The plan of the mine of a post, built once for its depth and the kingdom's land and kept.
     */
    static Plan plan(ServerLevel level, WorkPost post) {
        MineRules rules = GatheringSettings.mine();
        int levels = GatheringSettings.rules().mineLevels();
        int depth = depth(level, post);
        int step = MineRules.levelStep(post.mineGoal());
        long version = KingdomWorldApi.state(level.getServer()).territory().version();
        Plan known = PLANS.get(post.pos());
        if (known != null && known.depth() == depth && known.step() == step && known.landVersion() == version
                && known.levels() == levels && known.rules().equals(rules)) {
            return known;
        }
        // A plan is built seldom: the plans of posts that are gone or took another profession leave with it.
        PLANS.keySet().removeIf(pos -> !isMine(level.getServer(), pos));
        LandCheck land = new LandCheck(level, post.kingdomId());
        Plan plan = new Plan(depth, step, version, rules, levels, MinePlan.cells(post.pos().x(), post.pos().y(), post.pos().z(),
                depth, rules.corridorLength(), rules.branchLength(), levels, step, level.getMinBuildHeight(), land::owns),
                new MineProgress());
        PLANS.put(post.pos(), plan);
        return plan;
    }

    private static boolean isMine(MinecraftServer server, WorldPos pos) {
        return PostWork.post(server, pos).filter(post -> Professions.MINER.id().equals(post.professionId())).isPresent();
    }

    /**
     * Forgets the plan of the mine of a post that was removed or took another profession.
     */
    public static void forget(WorldPos post) {
        PLANS.remove(post);
    }

    /**
     * The cells of the mine of a post, for game tests.
     */
    public static List<MinePlan.Cell> cells(ServerLevel level, WorkPost post) {
        return plan(level, post).cells();
    }

    /**
     * Items on the kingdom's storehouses that count for the goal of a post: what its kind of ore drops.
     */
    public static long stock(ServerLevel level, WorkPost post) {
        Item item = post.mineGoal().anything() ? null : MineOres.drop(level, post.mineGoal().ore());
        return item == null ? 0L : KingdomStorage.count(level.getServer(), post.kingdomId(), stack -> stack.is(item));
    }

    public static void reset() {
        PLANS.clear();
        MineOres.reset();
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
     * Looks at a sample of the tunnels planned at the working depth, at the blocks around them and at the veins of
     * ore that show there, and at what all of it drops with an iron pickaxe: what a miner breaking a day's blocks
     * along that plan brings home.
     */
    private static final class Survey extends ZoneSurvey {
        /** Cells of the plan looked at, evenly spread over the first level. */
        private static final int SAMPLE_CELLS = 48;
        /** What looking at one cell counts against the budget of blocks per tick. */
        private static final int CELL_COST = 16;

        private final List<MinePlan.Cell> cells = new ArrayList<>();
        private final int stride;
        private final int veinBlocks;
        private final LandCheck land;
        private final Map<String, Integer> drops = new LinkedHashMap<>();
        private final Set<BlockPos> veins = new HashSet<>();
        private final ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        private int index;
        private int broken;
        private int seen;

        Survey(ServerLevel level, WorkPost post) {
            super(level, post, WorkZone.around(post.pos(), 0, 0, 0));
            Plan plan = plan(level, post);
            for (MinePlan.Cell cell : plan.cells()) {
                if (cell.level() == 0 && cell.kind() != MinePlan.Kind.STAIR) {
                    cells.add(cell);
                }
            }
            if (cells.isEmpty()) {
                cells.addAll(plan.cells());
            }
            stride = Math.max(1, cells.size() / SAMPLE_CELLS);
            veinBlocks = plan.rules().veinBlocks();
            land = new LandCheck(level, post.kingdomId());
        }

        @Override
        public int step(int budget) {
            int used = 0;
            while (used < budget && index < cells.size()) {
                look(cells.get(index));
                index += stride;
                used += CELL_COST;
            }
            return Math.max(1, used);
        }

        @Override
        public boolean done() {
            return index >= cells.size();
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
        }

        private void look(MinePlan.Cell cell) {
            BlockPos floor = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!land.owns(floor) || !level.isLoaded(floor)) {
                return;
            }
            seen++;
            for (int dy = 0; dy < cell.height(); dy++) {
                BlockPos pos = floor.above(dy);
                BlockState state = level.getBlockState(pos);
                if (!isOpen(level, pos) && isMinable(state) && level.getBlockEntity(pos) == null && !veins.contains(pos)) {
                    count(pos, state);
                }
                for (Direction direction : Direction.values()) {
                    BlockPos side = pos.relative(direction);
                    if (side.getX() == cell.x() && side.getZ() == cell.z() && side.getY() >= cell.y()
                            && side.getY() < cell.y() + cell.height()) {
                        continue;
                    }
                    // Ore in the walls, the floor and the ceiling is taken with its vein.
                    if (veinBlocks > 0 && isOre(side)) {
                        for (Veins.Block block : Veins.blocks(side.getX(), side.getY(), side.getZ(),
                                (x, y, z) -> isOre(new BlockPos(x, y, z)), veinBlocks)) {
                            BlockPos ore = new BlockPos(block.x(), block.y(), block.z());
                            count(ore, level.getBlockState(ore));
                            veins.add(ore);
                        }
                    }
                }
            }
        }

        private boolean isOre(BlockPos pos) {
            return !veins.contains(pos) && land.owns(pos) && level.isLoaded(pos) && level.getBlockState(pos).is(Tags.Blocks.ORES)
                    && level.getBlockEntity(pos) == null;
        }

        private void count(BlockPos pos, BlockState state) {
            broken++;
            for (ItemStack drop : Trees.drops(level, pos, state, pickaxe, null)) {
                drops.merge(PostWork.itemId(drop), drop.getCount(), Integer::sum);
            }
        }

        @Override
        public PostYield result() {
            int total = 0;
            for (int count : drops.values()) {
                total += count;
            }
            if (total == 0 || broken == 0) {
                return PostYield.NONE;
            }
            Map<String, Double> rates = new HashMap<>();
            for (Map.Entry<String, Integer> entry : drops.entrySet()) {
                rates.put(entry.getKey(), BLOCKS_PER_DAY * entry.getValue() / broken);
            }
            return new PostYield(rates, Map.of(), BLOCKS_PER_DAY, false);
        }

        @Override
        public WorkStatus status() {
            return broken > 0 ? WorkStatus.WORKING : seen > 0 ? WorkStatus.MINE_WORKED_OUT : WorkStatus.NO_ROCK;
        }
    }

    /**
     * The miner at work: goes through the cells of the mine plan, digging every cell whose entrance is open, and
     * between two cells takes the ore the last ones uncovered.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 300;
        /** A torch every so many cells, where it is dark. */
        private static final int TORCH_SPACING = 5;
        /** Uncovered ore remembered at most. */
        private static final int MAX_UNCOVERED = 256;
        /** A mine reaches far below and away from its post. */
        private static final int MAX_DISTANCE = 1_024;
        /** The way up a deep mine with a full bag takes a while. */
        private static final int DELIVERY_PATIENCE = 2_400;
        /** Ticks between two counts of the stock the goal asks for. */
        private static final int STOCK_INTERVAL = 200;

        /**
         * Ore seen in the side of a tunnel.
         *
         * @param anchor the cell it was seen from, where the miner stands to take it
         */
        private record Uncovered(BlockPos ore, BlockPos anchor) {
        }

        private Plan plan;
        private List<MinePlan.Cell> cells = List.of();
        /** Cells that could not be dug in this round; the next round tries them again. */
        private final BitSet failed = new BitSet();
        private final List<Uncovered> uncovered = new ArrayList<>();
        private final Deque<BlockPos> vein = new ArrayDeque<>();
        private BlockPos veinAnchor = BlockPos.ZERO;
        private TagKey<Block> wanted;
        private long stockCounted = Long.MIN_VALUE;
        private boolean goalMet;
        private int cursor;
        private int target = -1;
        private int block;
        private int timer;
        private int walking;
        private double closest = Double.MAX_VALUE;
        private int dug;
        private boolean weakTool;
        private boolean unreachable;
        private boolean unloaded;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected boolean isWorkTime() {
            // Down in the mine day and night are the same; miners rest only when the day's work would be done.
            return true;
        }

        @Override
        protected int maxDistance() {
            return MAX_DISTANCE;
        }

        @Override
        protected int deliveryPatience() {
            return DELIVERY_PATIENCE;
        }

        @Override
        protected void onStart() {
            Plan next = plan((ServerLevel) body.level(), post);
            if (next != plan) {
                // Another depth, other land or other settings: a new mine, and the old tunnels stay as they are.
                plan = next;
                cells = next.cells();
                cursor = 0;
                failed.clear();
                uncovered.clear();
                vein.clear();
            }
            wanted = goalMet((ServerLevel) body.level()) ? null : MineOres.tag(post.mineGoal().ore());
            target = -1;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (timer > 0) {
                timer--;
                return null;
            }
            if (isCarryingFull()) {
                return WorkStatus.WORKING;
            }
            if (target < 0) {
                if (followVein(level)) {
                    return null;
                }
                target = nextCell(level);
                if (target < 0 && plan.progress().anyLeft()) {
                    // Nothing else to dig: the levels left for lack of ore are taken up again.
                    plan.progress().resume();
                    target = nextCell(level);
                }
                walking = 0;
                closest = Double.MAX_VALUE;
                block = 0;
                if (target < 0) {
                    WorkStatus result = hasCarried() ? WorkStatus.WORKING : weakTool ? WorkStatus.TOOL_TOO_WEAK
                            : unreachable ? WorkStatus.UNREACHABLE : unloaded ? WorkStatus.WAITING : WorkStatus.MINE_WORKED_OUT;
                    cursor = 0;
                    failed.clear();
                    weakTool = false;
                    unreachable = false;
                    unloaded = false;
                    return result;
                }
                report(WorkStatus.WORKING);
            }
            MinePlan.Cell cell = cells.get(target);
            BlockPos stand = cell.from() < 0 ? postPos : cellPos(cells.get(cell.from()));
            BlockPos here = cellPos(cell);
            if (!near(here, REACH_SQR) && !near(stand, 1.5 * 1.5)) {
                if (!walk(stand)) {
                    // Unreachable for now; the next round tries again.
                    unreachable = true;
                    failed.set(target);
                    target = -1;
                }
                return null;
            }
            lookAt(here);
            if (block >= cell.height()) {
                finishCell(level, cell);
                target = -1;
                return null;
            }
            // Top down, so that nothing falls on the miner's head unannounced.
            BlockPos pos = here.above(cell.height() - 1 - block);
            if (!dig(level, pos, cell)) {
                failed.set(target);
                target = -1;
                return null;
            }
            block++;
            return null;
        }

        /**
         * Whether the storehouses hold what the post asks for, counted anew every ten seconds: then the miner takes
         * ore as it comes, and goes back to its kind first when the stock falls again.
         */
        private boolean goalMet(ServerLevel level) {
            MineGoal goal = post.mineGoal();
            if (goal.anything() || goal.amount() <= 0) {
                return false;
            }
            long now = level.getGameTime();
            if (now - stockCounted >= STOCK_INTERVAL || now < stockCounted) {
                stockCounted = now;
                goalMet = goal.metBy(stock(level, post));
            }
            return goalMet;
        }

        private static BlockPos cellPos(MinePlan.Cell cell) {
            return new BlockPos(cell.x(), cell.y(), cell.z());
        }

        /**
         * Walks towards a place and counts the ticks it gets no closer.
         *
         * @return whether the walk goes on; {@code false} when the miner made no way for too long
         */
        private boolean walk(BlockPos to) {
            double distance = Math.sqrt(body.distanceToSqr(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5));
            if (distance < closest - 0.5) {
                closest = distance;
                walking = 0;
            }
            moveTo(to);
            return ++walking <= GIVE_UP_TICKS;
        }

        /**
         * The next cell to dig: not open yet, with an open entrance, on the land, and not a tunnel of a level left
         * for lack of ore. The search starts where the last one ended and goes once around the plan.
         */
        private int nextCell(ServerLevel level) {
            int size = cells.size();
            for (int n = 0; n < size; n++) {
                int i = (cursor + n) % size;
                if (failed.get(i)) {
                    continue;
                }
                MinePlan.Cell cell = cells.get(i);
                BlockPos pos = cellPos(cell);
                if (!land.owns(pos) || cell.kind() != MinePlan.Kind.STAIR && plan.progress().left(cell.level())) {
                    continue;
                }
                if (!level.isLoaded(pos)) {
                    unloaded = true;
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
                    MinePlan.Cell from = cells.get(cell.from());
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

        // ------------------------------------------------------------ veins

        /**
         * Remembers the ore that shows in the sides of a block just opened.
         */
        private void uncover(ServerLevel level, BlockPos opened, BlockPos anchor) {
            if (plan.rules().veinBlocks() <= 0) {
                return;
            }
            for (Direction direction : Direction.values()) {
                BlockPos side = opened.relative(direction);
                // The land and the loaded state come first: reading a block of a chunk that sleeps would wake it.
                if (uncovered.size() >= MAX_UNCOVERED || !land.owns(side) || !level.isLoaded(side)
                        || !level.getBlockState(side).is(Tags.Blocks.ORES)) {
                    continue;
                }
                boolean known = false;
                for (Uncovered entry : uncovered) {
                    known |= entry.ore().equals(side);
                }
                if (!known) {
                    uncovered.add(new Uncovered(side, anchor));
                }
                plan.progress().ore();
            }
        }

        private boolean isOre(ServerLevel level, BlockPos pos) {
            return level.isLoaded(pos) && level.getBlockState(pos).is(Tags.Blocks.ORES) && land.owns(pos)
                    && level.getBlockEntity(pos) == null;
        }

        /**
         * Takes the next block of the vein at hand, or begins the vein of an uncovered ore: the kind the post asks
         * for first.
         *
         * @return whether the miner is busy with a vein
         */
        private boolean followVein(ServerLevel level) {
            if (vein.isEmpty() && !startVein(level)) {
                return false;
            }
            BlockPos ore = vein.peekFirst();
            if (!near(veinAnchor, REACH_SQR) && !near(ore, REACH_SQR)) {
                if (!walk(veinAnchor)) {
                    unreachable = true;
                    vein.clear();
                }
                return true;
            }
            vein.pollFirst();
            lookAt(ore);
            if (isOre(level, ore)) {
                if (!dig(level, ore, null)) {
                    // A block of the vein that cannot be taken closes the way to those behind it.
                    vein.clear();
                } else if (ore.getY() < veinAnchor.getY()) {
                    // Ore taken out of the floor would leave a pit in the tunnel: the hole is filled with cobblestone.
                    seal(level, ore);
                }
            }
            return true;
        }

        private boolean startVein(ServerLevel level) {
            int limit = plan.rules().veinBlocks();
            while (!uncovered.isEmpty()) {
                int pick = 0;
                if (wanted != null) {
                    for (int i = 0; i < uncovered.size(); i++) {
                        if (level.getBlockState(uncovered.get(i).ore()).is(wanted)) {
                            pick = i;
                            break;
                        }
                    }
                }
                Uncovered next = uncovered.remove(pick);
                BlockPos start = next.ore();
                for (Veins.Block found : Veins.blocks(start.getX(), start.getY(), start.getZ(),
                        (x, y, z) -> isOre(level, new BlockPos(x, y, z)), limit)) {
                    vein.add(new BlockPos(found.x(), found.y(), found.z()));
                }
                if (!vein.isEmpty()) {
                    veinAnchor = next.anchor();
                    walking = 0;
                    closest = Double.MAX_VALUE;
                    return true;
                }
            }
            return false;
        }

        // ------------------------------------------------------------ digging

        /**
         * Breaks one block of a cell or of a vein, after walling off fluids next to it.
         *
         * @param cell the cell the block belongs to, or {@code null} for a block of a vein
         * @return whether the cell or the vein can go on being dug
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
                if (cell != null && isPartOfTunnel(side, cell)) {
                    continue;
                }
                if (!level.isLoaded(side)) {
                    // What lies beyond the loaded world is not known and is not looked at: the block waits.
                    unloaded = true;
                    return false;
                }
                if (!level.getFluidState(side).isEmpty() && !seal(level, side)) {
                    // No cobblestone to wall it off with: the block stays.
                    return false;
                }
            }
            float hardness = state.getDestroySpeed(level, pos);
            for (ItemStack drop : Trees.drops(level, pos, state, pickaxe, body)) {
                gather(drop);
            }
            level.destroyBlock(pos, false, body);
            useTool(1);
            float speed = Math.max(1.0f, pickaxe.getDestroySpeed(state));
            timer = Math.max(3, (int) Math.ceil(30.0f * Math.max(0.3f, hardness) / speed));
            if (state.is(Tags.Blocks.ORES)) {
                plan.progress().ore();
            } else if (cell != null && cell.kind() != MinePlan.Kind.STAIR) {
                // Rock of a tunnel; the staircase is the way down and does not count against a level.
                plan.progress().opened(cell.level(), plan.rules().barrenBlocks());
            }
            if (cell != null) {
                uncover(level, pos, cellPos(cell));
            }
            return true;
        }

        private boolean isPartOfTunnel(BlockPos pos, MinePlan.Cell cell) {
            if (pos.getX() == cell.x() && pos.getZ() == cell.z() && pos.getY() >= cell.y() && pos.getY() < cell.y() + cell.height()) {
                return true;
            }
            if (cell.from() >= 0) {
                MinePlan.Cell from = cells.get(cell.from());
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
