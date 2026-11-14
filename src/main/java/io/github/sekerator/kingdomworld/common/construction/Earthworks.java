package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.MinerBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import io.github.sekerator.kingdomworld.core.construction.EarthworkPlan;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.TemplateCost;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Earthworks done by the builders as a construction of their own kind: a rectangle of land levelled to a height, cut down to it
 * or filled up to it. When they are laid out the land is looked at once: what of it the work digs (only what grew there: earth,
 * sand, gravel, stone and ore, plants and trees), what it fills, and what it leaves as it is (a block holding items or of the
 * mod, a building of the register, a road, a wall, a block somebody made, the land of others). The plan of the work is made
 * from what was seen then and kept with the construction, so that a restart neither loses nor repeats any of it; it is put
 * together off the server thread. Every block is looked at again when its turn comes: what somebody placed since stays. The
 * ground the work digs stays at the site for its fill as long as the fill wants it; the rest goes to the storehouses while
 * they have room and then to the spoil heap; ore and everything else of worth always goes to the storehouses.
 */
public final class Earthworks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The template id of every earthworks: their blocks come from the land, not from a template of the catalog. */
    public static final String TEMPLATE_ID = "kingdomworld:earthwork";
    /** Earthworks are laid out as their land lies, not turned. */
    public static final Placement PLACEMENT = new Placement(0, false);
    /** Places left as they are a survey tells the player of at most. */
    public static final int KEPT_SHOWN = 64;
    /** Blocks a survey looks at at most: what a plot of 64 by 64 dug or filled 32 deep and a few trees on it come to. */
    public static final int MAX_VOLUME = 160_000;
    /** The tool the ground is broken with, so that stone gives cobblestone and ore its ore. */
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);
    private static final String COBBLESTONE = "minecraft:cobblestone";

    /**
     * What the plan of earthworks is made into: the template of their fill, the plan of their steps, the chunks the steps touch
     * and how many blocks of the fill are placed of each item, kept up as the work goes on.
     */
    static final class Built {
        final BuildingTemplate template;
        final BuildPlan plan;
        final long[] chunks;
        final Map<String, Integer> total = new HashMap<>();
        final Map<String, Integer> placed = new HashMap<>();
        final BitSet counted = new BitSet();

        Built(BuildingTemplate template, BuildPlan plan, BitSet done) {
            this.template = template;
            this.plan = plan;
            Set<Long> touched = new HashSet<>();
            for (BuildPlan.Step step : plan.steps()) {
                touched.add(ChunkKey.of(step.x() >> 4, step.z() >> 4));
                if (step.kind() == BuildPlan.Kind.PLACE) {
                    step.cost().forEach((item, count) -> total.merge(item, count, Integer::sum));
                    if (done.get(step.index())) {
                        count(step);
                    }
                }
            }
            this.chunks = touched.stream().mapToLong(Long::longValue).toArray();
        }

        void count(BuildPlan.Step step) {
            counted.set(step.index());
            step.cost().forEach((item, count) -> placed.merge(item, count, Integer::sum));
        }
    }

    /** Plans made, by construction. */
    private static final Map<Long, Built> BUILT = new HashMap<>();
    /** Plans being made off the server thread, by construction. */
    private static final Map<Long, CompletableFuture<Built>> BUILDING = new HashMap<>();
    /** The last survey of every player, laid out without looking at the land a second time. */
    private static final Map<UUID, Remembered> SURVEYS = new HashMap<>();
    /** Ticks a survey is laid out from without looking again. */
    private static final int SURVEY_KEPT_TICKS = 1200;
    /** Steps of a plan made at once on the server thread; a larger one is made off it. */
    private static final int MADE_AT_ONCE = 4096;

    private record Remembered(Request request, UUID kingdomId, Survey survey, long tick) {
    }

    /**
     * What a player asks for: the two corners of the rectangle, what the work does, its height and whether its edges slope.
     */
    public record Request(BlockPos first, BlockPos second, Earthwork.Mode mode, int target, boolean smooth) {
    }

    /**
     * What the land of a request looks like to the work.
     *
     * @param error   why the work may not be laid out, or {@code null}
     * @param work    the land and height of the work
     * @param clear   the blocks the work digs, {x, y, z}
     * @param skip    the blocks of the fill left as they are, by their index in the plan of the fill
     * @param kept    where blocks are left as they are, the first of them ({@link #KEPT_SHOWN} at most)
     * @param keptAll how many blocks are left as they are
     * @param fills   blocks the work fills
     * @param missing blocks of the fill there is no earth for yet, neither dug nor in the storehouses
     * @param spoil   what the work digs that its fill does not take, by item
     */
    public record Survey(@Nullable KingdomError error, @Nullable Earthwork work, List<int[]> clear, BitSet skip, List<BlockPos> kept, int keptAll,
                         int fills, int missing, Map<String, Integer> spoil) {
        static Survey failed(KingdomError error) {
            return new Survey(error, null, List.of(), new BitSet(), List.of(), 0, 0, 0, Map.of());
        }

        public int digs() {
            return clear.size();
        }
    }

    private Earthworks() {
    }

    public static void reset() {
        BUILT.clear();
        BUILDING.clear();
        SURVEYS.clear();
    }

    static void forget(long construction) {
        BUILT.remove(construction);
        CompletableFuture<Built> building = BUILDING.remove(construction);
        if (building != null) {
            building.cancel(false);
        }
    }

    // ---------------------------------------------------------------- the land

    /**
     * Whether earthworks may dig a block: only the land as it grew, by the tags of what a miner breaks (earth, sand, gravel,
     * stone, ore), plants and trees, snow and ice.
     */
    public static boolean diggable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        return MinerBehavior.isMinable(state) || Trees.isLog(state) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.ICE) || state.is(Blocks.CLAY) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(Blocks.VINE)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.MOSS_CARPET)
                || state.getFluidState().isEmpty() && state.canBeReplaced();
    }

    /**
     * The item of the ground a block gives the fill when it is dug, or {@code null} for anything else.
     */
    @Nullable
    static String fillItem(BlockState state) {
        if (state.is(BlockTags.DIRT) && !state.is(Blocks.MUD) && !state.is(Blocks.MOSS_BLOCK) && !state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
            return EarthworkPlan.DIRT;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            return COBBLESTONE;
        }
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)) {
            return "minecraft:cobbled_deepslate";
        }
        for (Block block : new Block[]{Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.CALCITE, Blocks.GRAVEL, Blocks.SAND,
                Blocks.RED_SAND}) {
            if (state.is(block)) {
                return itemId(block.asItem());
            }
        }
        return null;
    }

    private static String itemId(Item item) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }

    /**
     * Whether a block is not ground: air, a plant, leaves, a log, snow or a liquid.
     */
    private static boolean soft(BlockState state) {
        return state.isAir() || Scaffolds.is(state) || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || Trees.isLog(state)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.SNOW) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * The height of the solid ground of a column: below trees, plants, snow and water.
     */
    static int groundAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight() && soft(level.getBlockState(pos.setY(y)))) {
            y--;
        }
        return y;
    }

    /**
     * Whether a fill may go where a block stands now: air, what any block replaces, a liquid, or the land as it grew (dug first).
     * Something somebody made, a block holding items and a block of the mod may not be filled over.
     */
    static boolean fillable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || Scaffolds.is(state)) {
            return true;
        }
        if (ConstructionWork.isProtected(level, pos, state) || ConstructionWork.holdsItems(level, pos) || state.hasBlockEntity()) {
            return false;
        }
        return state.canBeReplaced() || diggable(state);
    }

    // ---------------------------------------------------------------- looking at the land

    /**
     * Looks at the land of a request for a kingdom: its heights, what the work digs, fills and leaves as it is, what the
     * fill is made of and what is left over. The land and a ring of a block around it must be loaded; the work may look at
     * {@link #MAX_VOLUME} blocks at most and stay within the height of the world.
     */
    public static Survey survey(ServerLevel level, UUID kingdomId, Request request) {
        MinecraftServer server = level.getServer();
        int minX = Math.min(request.first().getX(), request.second().getX());
        int maxX = Math.max(request.first().getX(), request.second().getX());
        int minZ = Math.min(request.first().getZ(), request.second().getZ());
        int maxZ = Math.max(request.first().getZ(), request.second().getZ());
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int most = ConstructionSettings.earthworkSize();
        if (sizeX > most || sizeZ > most) {
            return Survey.failed(KingdomError.EARTHWORK_TOO_LARGE);
        }
        if (request.target() <= level.getMinBuildHeight() + 1 || request.target() >= level.getMaxBuildHeight() - 2) {
            return Survey.failed(KingdomError.EARTHWORK_OUTSIDE_WORLD);
        }
        for (int cx = (minX - 1) >> 4; cx <= (maxX + 1) >> 4; cx++) {
            for (int cz = (minZ - 1) >> 4; cz <= (maxZ + 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return Survey.failed(KingdomError.AREA_NOT_LOADED);
                }
            }
        }
        int[] ground = new int[(sizeX + 2) * (sizeZ + 2)];
        for (int dz = -1; dz <= sizeZ; dz++) {
            for (int dx = -1; dx <= sizeX; dx++) {
                ground[(dz + 1) * (sizeX + 2) + dx + 1] = groundAt(level, minX + dx, minZ + dz);
            }
        }
        int[] top = new int[sizeX * sizeZ];
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                top[dz * sizeX + dx] = level.getHeight(Heightmap.Types.WORLD_SURFACE, minX + dx, minZ + dz) - 1;
            }
        }
        Earthwork shape = new Earthwork(minX, minZ, sizeX, sizeZ, request.mode(), request.target(), request.smooth(), ground, top, Map.of(), Map.of());
        if (shape.span() > ConstructionSettings.earthworkDepth()) {
            return Survey.failed(KingdomError.EARTHWORK_TOO_DEEP);
        }
        if (shape.bottom() <= level.getMinBuildHeight() || shape.ceiling() >= level.getMaxBuildHeight() - 1) {
            return Survey.failed(KingdomError.EARTHWORK_OUTSIDE_WORLD);
        }
        long volume = 0;
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                volume += Math.max(0, shape.digTo(dx, dz) - shape.digFrom(dx, dz) + 1) + Math.max(0, shape.fillTo(dx, dz) - shape.fillFrom(dx, dz) + 1);
            }
        }
        if (volume > MAX_VOLUME) {
            return Survey.failed(KingdomError.EARTHWORK_TOO_MUCH);
        }
        Box area = new Box(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);
        Set<Long> ways = ways(server, area);
        List<Box> buildings = new ArrayList<>();
        for (Building building : KingdomWorldApi.state(server).construction().buildings()) {
            if (building.box().intersects(area)) {
                buildings.add(building.box());
            }
        }
        List<int[]> clear = new ArrayList<>();
        Set<Long> keptFill = new HashSet<>();
        List<BlockPos> kept = new ArrayList<>();
        int[] keptAll = {0};
        Map<String, Integer> dug = new LinkedHashMap<>();
        Map<Long, Boolean> own = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                int x = minX + dx;
                int z = minZ + dz;
                boolean column = own.computeIfAbsent(ChunkKey.of(x >> 4, z >> 4),
                        key -> kingdomId.equals(KingdomWorldApi.ownerAt(level, new BlockPos(x, 0, z)))) && !ways.contains(BlockPos.asLong(x, 0, z));
                for (int y = shape.digFrom(dx, dz); y <= shape.digTo(dx, dz); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || Scaffolds.is(state)) {
                        // Scaffolds are taken down by their builders: nothing to dig.
                        continue;
                    }
                    if (!column || inside(buildings, x, y, z) || ConstructionWork.isProtected(level, pos, state) || ConstructionWork.holdsItems(level, pos)
                            || !diggable(state) && !(state.getBlock() instanceof LiquidBlock)) {
                        keep(kept, keptAll, pos);
                        continue;
                    }
                    clear.add(new int[]{x, y, z});
                    String item = fillItem(state);
                    if (item != null) {
                        dug.merge(item, 1, Integer::sum);
                    }
                }
                for (int y = shape.fillFrom(dx, dz); y <= shape.fillTo(dx, dz); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!column || inside(buildings, x, y, z) || !fillable(level, pos, state)) {
                        keptFill.add(pos.asLong());
                        keep(kept, keptAll, pos);
                    }
                }
            }
        }
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (String item : EarthworkPlan.FROM_STOCK) {
            Item found = PostWork.item(item);
            if (found != null) {
                stock.put(item, (int) Math.min(Integer.MAX_VALUE, KingdomStorage.count(server, kingdomId, stack -> stack.is(found))));
            }
        }
        Earthwork work = new Earthwork(minX, minZ, sizeX, sizeZ, request.mode(), request.target(), request.smooth(), ground, top, dug, stock);
        EarthworkPlan.Result plan = EarthworkPlan.of(work);
        BitSet skip = new BitSet();
        for (int i = 0; i < plan.fills().size(); i++) {
            EarthworkPlan.Fill fill = plan.fills().get(i);
            if (keptFill.contains(BlockPos.asLong(minX + fill.dx(), fill.y(), minZ + fill.dz()))) {
                skip.set(i);
            }
        }
        int fills = plan.fills().size() - skip.cardinality();
        KingdomError error = clear.isEmpty() && fills == 0 ? KingdomError.EARTHWORK_NOTHING : null;
        return new Survey(error, work, List.copyOf(clear), skip, List.copyOf(kept), keptAll[0], fills, plan.missing(), plan.spoil());
    }

    /**
     * Looks at the land of a request and keeps what was seen for a player, so that laying the same work out soon after does not
     * look at it again (every block is looked at again when its turn comes anyway).
     */
    public static Survey check(ServerLevel level, UUID player, UUID kingdomId, Request request) {
        Survey survey = survey(level, kingdomId, request);
        long now = level.getGameTime();
        SURVEYS.values().removeIf(old -> now - old.tick() > SURVEY_KEPT_TICKS);
        if (survey.error() == null) {
            SURVEYS.put(player, new Remembered(request, kingdomId, survey, now));
        } else {
            SURVEYS.remove(player);
        }
        return survey;
    }

    /**
     * The survey a player made of the same request a moment ago, or the land looked at now.
     */
    public static Survey known(ServerLevel level, UUID player, UUID kingdomId, Request request) {
        Remembered remembered = SURVEYS.remove(player);
        if (remembered != null && remembered.request().equals(request) && remembered.kingdomId().equals(kingdomId)
                && level.getGameTime() - remembered.tick() <= SURVEY_KEPT_TICKS) {
            return remembered.survey();
        }
        return survey(level, kingdomId, request);
    }

    private static void keep(List<BlockPos> kept, int[] all, BlockPos pos) {
        all[0]++;
        if (kept.size() < KEPT_SHOWN) {
            kept.add(pos.immutable());
        }
    }

    private static boolean inside(List<Box> boxes, int x, int y, int z) {
        for (Box box : boxes) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The columns roads and walls of any kingdom take within a box, {x, 0, z} packed: earthworks leave them as they are.
     */
    static Set<Long> ways(MinecraftServer server, Box area) {
        Set<Long> taken = new HashSet<>();
        for (Road road : RoadWork.state(server).roads()) {
            for (RouteCell cell : road.cells()) {
                if (cell.x() >= area.minX() - 2 && cell.x() <= area.maxX() + 2 && cell.z() >= area.minZ() - 2 && cell.z() <= area.maxZ() + 2) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            taken.add(BlockPos.asLong(cell.x() + dx, 0, cell.z() + dz));
                        }
                    }
                }
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            for (RouteCell cell : wall.cells()) {
                if (cell.x() >= area.minX() && cell.x() <= area.maxX() && cell.z() >= area.minZ() && cell.z() <= area.maxZ()) {
                    taken.add(BlockPos.asLong(cell.x(), 0, cell.z()));
                }
            }
        }
        return taken;
    }

    // ---------------------------------------------------------------- the plan

    /**
     * The anchor of earthworks in the world: the north west corner of their land at the lowest height they touch.
     */
    static BlockPos anchor(Earthwork work) {
        return new BlockPos(work.minX(), work.bottom(), work.minZ());
    }

    /**
     * The plan of a construction of earthworks, once it is made: a small one is made at once, a large one (laid out just now or
     * kept over a restart) off the server thread from the land kept with the construction, which waits for it meanwhile. A plan
     * that could not be made is not tried again: the construction tells that its plan changed.
     */
    static Optional<Built> built(Construction construction) {
        Earthwork work = construction.earthwork();
        if (work == null) {
            return Optional.empty();
        }
        Built known = BUILT.get(construction.id());
        if (known != null && known.template.fingerprint() == work.fingerprint()) {
            return Optional.of(known);
        }
        CompletableFuture<Built> building = BUILDING.get(construction.id());
        if (building == null) {
            List<int[]> clear = construction.clear();
            BitSet skip = (BitSet) construction.skip().clone();
            BitSet done = construction.doneSet();
            long id = construction.id();
            building = construction.steps() <= MADE_AT_ONCE ? made(() -> make(work, clear, skip, done))
                    : CompletableFuture.supplyAsync(() -> make(work, clear, skip, done), Util.backgroundExecutor());
            building.whenComplete((made, error) -> {
                if (error != null) {
                    LOGGER.error("The plan of earthworks {} could not be made", id, error);
                }
            });
            BUILDING.put(id, building);
        }
        if (!building.isDone()) {
            return Optional.empty();
        }
        if (building.isCompletedExceptionally()) {
            construction.setStatus(Construction.Status.TEMPLATE_CHANGED);
            return Optional.empty();
        }
        BUILDING.remove(construction.id());
        Built made = building.join();
        BUILT.put(construction.id(), made);
        return Optional.of(made);
    }

    private static CompletableFuture<Built> made(java.util.function.Supplier<Built> make) {
        try {
            return CompletableFuture.completedFuture(make.get());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Makes the template, the plan and the tally of earthworks; touches nothing but its arguments.
     */
    private static Built make(Earthwork work, List<int[]> clear, BitSet skip, BitSet done) {
        BuildingTemplate template = template(work);
        BuildPlan plan = ConstructionWork.plan(template, anchor(work), PLACEMENT, clear, skip, Construction.Kind.EARTHWORK);
        return new Built(template, plan, done);
    }

    static Optional<BuildingTemplate> template(Construction construction) {
        return built(construction).map(built -> built.template);
    }

    static Optional<BuildPlan> plan(Construction construction) {
        return built(construction).map(built -> built.plan);
    }

    /**
     * The template of earthworks: every block of their fill, from the bottom of the work, made of the item the plan chose.
     */
    public static BuildingTemplate template(Earthwork work) {
        int bottom = work.bottom();
        int height = Math.max(1, work.ceiling() - bottom + 1);
        List<BuildingTemplate.Block> blocks = new ArrayList<>();
        List<BlockCost> costs = new ArrayList<>();
        Map<String, BlockState> states = new HashMap<>();
        for (EarthworkPlan.Fill fill : EarthworkPlan.of(work).fills()) {
            BlockState state = states.computeIfAbsent(fill.item(), Earthworks::stateOf);
            blocks.add(new BuildingTemplate.Block(new BlockPos(fill.dx(), fill.y() - bottom, fill.dz()), state));
            costs.add(TemplateBlocks.cost(state));
        }
        ItemStack icon = new ItemStack(Items.IRON_SHOVEL);
        return new BuildingTemplate(ResourceLocation.tryParse(TEMPLATE_ID), "kingdomworld.earthwork.name." + work.mode().id(), "",
                BuildingTemplate.Category.DECORATION, "", "", Map.of(), 1, "", null, icon, work.sizeX(), height, work.sizeZ(), BlockPos.ZERO,
                Facing.SOUTH, blocks, costs, TemplateCost.of(costs), work.fingerprint(), null);
    }

    private static BlockState stateOf(String item) {
        Item found = PostWork.item(item);
        return found instanceof BlockItem block ? block.getBlock().defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    // ---------------------------------------------------------------- what is left

    /**
     * Whether every chunk the steps of earthworks touch is the kingdom's land; the columns the work leaves as they are do not
     * count. Earthworks whose plan is not made yet count by their box.
     */
    static boolean onOwnLand(MinecraftServer server, Construction construction) {
        Built built = BUILT.get(construction.id());
        if (built == null) {
            return true;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (long chunk : built.chunks) {
            if (!construction.kingdomId().equals(state.territory().ownerOf(construction.dimension(), chunk))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every chunk the steps of earthworks touch is loaded.
     */
    static boolean loaded(ServerLevel level, Construction construction) {
        Built built = BUILT.get(construction.id());
        if (built == null) {
            return false;
        }
        for (long chunk : built.chunks) {
            if (!level.hasChunk(ChunkKey.x(chunk), ChunkKey.z(chunk))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A block of the fill is placed (or left as it was): the tally of the fill counts it once.
     */
    static void placed(Construction construction, int index) {
        Built built = BUILT.get(construction.id());
        if (built == null || index < 0 || index >= built.plan.size() || built.counted.get(index)) {
            return;
        }
        BuildPlan.Step step = built.plan.step(index);
        if (step.kind() == BuildPlan.Kind.PLACE) {
            built.count(step);
        }
    }

    /**
     * What the fill still costs: its blocks neither placed nor paid for, by item, from the tally rather than from every step.
     */
    static Map<String, Integer> remaining(Construction construction, BuildPlan plan) {
        Built built = BUILT.get(construction.id());
        if (built == null || built.plan != plan) {
            return plan.remaining(construction);
        }
        Map<String, Integer> left = new LinkedHashMap<>();
        built.total.forEach((item, count) -> {
            int rest = count - built.placed.getOrDefault(item, 0);
            if (rest > 0) {
                left.put(item, rest);
            }
        });
        for (int index : construction.pending()) {
            if (index < plan.size() && !built.counted.get(index)) {
                BuildPlan.Step step = plan.step(index);
                if (step.kind() == BuildPlan.Kind.PLACE) {
                    step.cost().forEach((item, count) -> left.computeIfPresent(item, (key, rest) -> rest > count ? rest - count : null));
                }
            }
        }
        return left;
    }

    /**
     * What the fill still needs brought: what it costs less what lies at the site already.
     */
    static Map<String, Integer> needed(Construction construction, BuildPlan plan) {
        Map<String, Integer> result = new LinkedHashMap<>();
        remaining(construction, plan).forEach((item, count) -> {
            int left = count - construction.delivered().getOrDefault(item, 0);
            if (left > 0) {
                result.put(item, left);
            }
        });
        return result;
    }

    /**
     * Whether every step of earthworks is done, by the count of the steps done.
     */
    static boolean finished(Construction construction, BuildPlan plan) {
        return construction.doneCount() >= plan.size();
    }

    // ---------------------------------------------------------------- laying out

    /**
     * Lays out earthworks for a kingdom: the right of construction and the room for another construction as for any, the land
     * looked at now (or the survey of the player of a moment ago for the same request), the plan made off the server thread.
     *
     * @param actor the player, who needs the right to manage construction; {@code null} for an operator
     */
    public static KingdomWorldState.ConstructionChange lay(ServerLevel level, @Nullable UUID actor, UUID kingdomId, Request request) {
        Survey survey = actor == null ? survey(level, kingdomId, request) : known(level, actor, kingdomId, request);
        return lay(level, actor, kingdomId, survey);
    }

    /**
     * Lays out earthworks for a kingdom from a survey of their land.
     *
     * @param actor the player, who needs the right to manage construction; {@code null} for an operator
     */
    public static KingdomWorldState.ConstructionChange lay(ServerLevel level, @Nullable UUID actor, UUID kingdomId, Survey survey) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(KingdomError.READ_ONLY), Optional.empty());
        }
        if (survey.error() != null || survey.work() == null) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(survey.error()), Optional.empty());
        }
        Earthwork work = survey.work();
        BlockPos anchor = anchor(work);
        Box box = PLACEMENT.box(work.sizeX(), Math.max(1, work.ceiling() - work.bottom() + 1), work.sizeZ(), 0, 0, 0, anchor.getX(), anchor.getY(),
                anchor.getZ());
        // The steps: every block dug, and every block of the fill not left as it is.
        int steps = survey.digs() + survey.fills();
        ConstructionRules rules = ConstructionSettings.rules();
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(actor, kingdomId, TEMPLATE_ID,
                work.fingerprint(), ConstructionWork.worldPos(level, anchor), PLACEMENT, box, survey.clear(), steps, KingdomWorldApi.gameTime(server),
                rules, Construction.Kind.EARTHWORK, 0, io.github.sekerator.kingdomworld.core.construction.Materials.NONE, survey.skip());
        change.construction().ifPresent(construction -> {
            construction.setEarthwork(work);
            // The plan is made off the server thread; the work begins once it is there.
            built(construction);
            LOGGER.info("Earthworks {} of {} laid at {} {} ({}x{}, {} to {}): {} to dig, {} to fill, {} left as they are", construction.id(),
                    kingdomId, work.minX(), work.minZ(), work.sizeX(), work.sizeZ(), work.mode().id(), work.target(), survey.digs(), survey.fills(),
                    survey.keptAll());
        });
        return change;
    }

    // ---------------------------------------------------------------- the work

    /**
     * Digs a block of earthworks, looked at anew: only what grew there; water goes; lava next to it is closed off with
     * cobblestone first (a stone of the site or the storehouses for each, waiting while there is none). What somebody placed
     * since the work was laid out stays where it is; a block holding items or of the mod blocks the step.
     */
    static ConstructionWork.StepResult dig(ServerLevel level, Construction construction, BlockPos at, @Nullable LivingEntity breaker,
                                           List<ItemStack> drops, boolean free) {
        BlockState state = level.getBlockState(at);
        if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
            if (ConstructionWork.isProtected(level, at, state) || ConstructionWork.holdsItems(level, at)) {
                return ConstructionWork.StepResult.BLOCKED;
            }
            if (!diggable(state)) {
                // Somebody built or placed something there since the work was laid out: it stays.
                return ConstructionWork.StepResult.DONE;
            }
        }
        if (!sealLava(level, construction, at, free)) {
            construction.setStatus(Construction.Status.NO_MATERIALS, new WorldPos(construction.dimension(), at.getX(), at.getY(), at.getZ()));
            return ConstructionWork.StepResult.NO_MATERIALS;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (!state.isAir()) {
            drops.addAll(Block.getDrops(state, level, at, null, breaker, PICK));
            level.destroyBlock(at, false, breaker);
        }
        return ConstructionWork.StepResult.DONE;
    }

    /**
     * Looks at the place of a block of the fill before it goes there: the land as it grew is dug first; what somebody placed
     * since the work was laid out stays and the block is left out; a block holding items or of the mod blocks the step.
     *
     * @return {@code null} to place the block, {@link ConstructionWork.StepResult#DONE} to leave it out, or why not now
     */
    @Nullable
    static ConstructionWork.StepResult beforeFill(ServerLevel level, Construction construction, BlockPos at, @Nullable LivingEntity breaker,
                                                  List<ItemStack> drops, boolean free) {
        BlockState there = level.getBlockState(at);
        if (there.isAir() || there.getBlock() instanceof LiquidBlock || there.canBeReplaced() && !there.hasBlockEntity() || Scaffolds.is(there)) {
            // A scaffold nobody stands on gives way to the fill as air does.
            return null;
        }
        if (ConstructionWork.isProtected(level, at, there) || ConstructionWork.holdsItems(level, at)) {
            return ConstructionWork.StepResult.BLOCKED;
        }
        if (there.hasBlockEntity() || !diggable(there)) {
            return ConstructionWork.StepResult.DONE;
        }
        ConstructionWork.StepResult dug = dig(level, construction, at, breaker, drops, free);
        return dug == ConstructionWork.StepResult.DONE ? null : dug;
    }

    /**
     * Closes off the lava next to a place dug with cobblestone of the site or the storehouses, as a miner does; on the land of
     * the kingdom only.
     *
     * @param free whether the cobblestone costs nothing (an operator finishing the work at once)
     * @return whether no lava is left next to the place, false when there was no cobblestone for it
     */
    private static boolean sealLava(ServerLevel level, Construction construction, BlockPos at, boolean free) {
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) {
                continue;
            }
            BlockPos next = at.relative(side);
            if (!level.isLoaded(next) || !level.getFluidState(next).is(FluidTags.LAVA)
                    || !construction.kingdomId().equals(KingdomWorldApi.ownerAt(level, next))) {
                continue;
            }
            // An operator finishing the work at once needs no cobblestone.
            if (!free && !takeCobblestone(level, construction)) {
                return false;
            }
            level.setBlock(next, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /**
     * Takes a cobblestone for closing off lava: from the site first, then from the storehouses.
     *
     * @return whether there was one
     */
    private static boolean takeCobblestone(ServerLevel level, Construction construction) {
        if (construction.delivered().getOrDefault(COBBLESTONE, 0) > 0) {
            construction.takeDelivered(COBBLESTONE, 1);
            return true;
        }
        Item item = PostWork.item(COBBLESTONE);
        if (item == null) {
            return false;
        }
        List<ItemStack> taken = KingdomStorage.take(level.getServer(), construction.kingdomId(), stack -> stack.is(item), 1, construction.anchor());
        return taken.stream().mapToInt(ItemStack::getCount).sum() >= 1;
    }

    /**
     * Where what earthworks dug goes: the ground their fill still lacks stays at the site, the rest of the ground goes to the
     * storehouses while they have room and then to the spoil heap; ore and everything else of worth goes to the storehouses,
     * and what they have no room for is carried by a builder near players or left at the site far from them.
     *
     * @param near whether a builder dug it, who carries what nobody else takes
     * @return what a builder carries
     */
    static List<ItemStack> route(MinecraftServer server, Construction construction, BuildPlan plan, ServerLevel level, List<ItemStack> drops,
                                 boolean near) {
        Map<String, Integer> lacking = new HashMap<>(needed(construction, plan));
        List<ItemStack> carried = new ArrayList<>();
        BlockPos at = ConstructionWork.blockPos(construction.anchor());
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            String id = KingdomStorage.itemId(drop);
            ItemStack rest = drop.copy();
            int wanted = drop.hasTag() ? 0 : lacking.getOrDefault(id, 0);
            if (EarthworkPlan.destination(id, wanted, true) == EarthworkPlan.Destination.SITE) {
                int taken = construction.deliver(id, Math.min(wanted, rest.getCount()));
                lacking.merge(id, -taken, Integer::sum);
                rest.shrink(taken);
                if (rest.isEmpty()) {
                    continue;
                }
            }
            rest = KingdomStorage.insert(server, construction.kingdomId(), rest, construction.anchor());
            if (rest.isEmpty() || !drop.hasTag() && EarthworkPlan.destination(id, 0, false) == EarthworkPlan.Destination.SPOIL) {
                // Ground the storehouses have no room for goes to the spoil heap.
                continue;
            }
            if (near) {
                carried.add(rest);
            } else {
                Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rest);
            }
        }
        return carried;
    }
}
