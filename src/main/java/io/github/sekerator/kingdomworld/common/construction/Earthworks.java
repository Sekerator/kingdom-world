package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.MinerBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
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

/**
 * Earthworks done by the builders as a construction of their own kind: a rectangle of land levelled to a height, cut down to it
 * or filled up to it. When they are laid out the land is looked at once: what of it the work digs (only what grew there: earth,
 * sand, gravel, stone and ore, plants and trees), what it fills, and what it leaves as it is (a block holding items or of the
 * mod, a building of the register, a road, a wall, a block somebody made, the land of others). The plan of the work is made
 * from what was seen then and kept with the construction, so that a restart neither loses nor repeats any of it. The ground the
 * work digs stays at the site for its fill as long as the fill wants it; the rest goes to the storehouses while they have room
 * and then to the spoil heap; ore and everything else of worth always goes to the storehouses.
 */
public final class Earthworks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The template id of every earthworks: their blocks come from the land, not from a template of the catalog. */
    public static final String TEMPLATE_ID = "kingdomworld:earthwork";
    /** Earthworks are laid out as their land lies, not turned. */
    public static final Placement PLACEMENT = new Placement(0, false);
    /** Places left as they are a survey tells the player of at most. */
    public static final int KEPT_SHOWN = 64;
    /** The tool the ground is broken with, so that stone gives cobblestone and ore its ore. */
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);
    /** The templates of earthworks worked out already, by construction. */
    private static final Map<Long, BuildingTemplate> TEMPLATES = new HashMap<>();

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
        TEMPLATES.clear();
    }

    static void forget(long construction) {
        TEMPLATES.remove(construction);
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
            return "minecraft:cobblestone";
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
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || Trees.isLog(state)
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

    // ---------------------------------------------------------------- looking at the land

    /**
     * Looks at the land of a request for a kingdom: its heights, what the work digs, fills and leaves as it is, what the
     * fill is made of and what is left over. The land and a ring of a block around it must be loaded.
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
                boolean column = own.computeIfAbsent(io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4),
                        key -> kingdomId.equals(KingdomWorldApi.ownerAt(level, new BlockPos(x, 0, z)))) && !ways.contains(BlockPos.asLong(x, 0, z));
                for (int y = shape.digFrom(dx, dz); y <= shape.digTo(dx, dz); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) {
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
                    boolean open = state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LiquidBlock || diggable(state);
                    if (!column || inside(buildings, x, y, z) || !open || ConstructionWork.isProtected(level, pos, state)
                            || ConstructionWork.holdsItems(level, pos)) {
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
    private static Set<Long> ways(MinecraftServer server, Box area) {
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
     * The template of a construction of earthworks, made from its land once.
     */
    static Optional<BuildingTemplate> template(Construction construction) {
        Earthwork work = construction.earthwork();
        if (work == null) {
            return Optional.empty();
        }
        BuildingTemplate known = TEMPLATES.get(construction.id());
        if (known != null && known.fingerprint() == work.fingerprint()) {
            return Optional.of(known);
        }
        BuildingTemplate made = template(work);
        TEMPLATES.put(construction.id(), made);
        return Optional.of(made);
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

    // ---------------------------------------------------------------- laying out

    /**
     * Lays out earthworks for a kingdom: the right of construction and the room for another construction as for any, the land
     * looked at now, the plan kept with the construction.
     *
     * @param actor the player, who needs the right to manage construction; {@code null} for an operator
     */
    public static KingdomWorldState.ConstructionChange lay(ServerLevel level, @Nullable UUID actor, UUID kingdomId, Request request) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(KingdomError.READ_ONLY), Optional.empty());
        }
        Survey survey = survey(level, kingdomId, request);
        if (survey.error() != null || survey.work() == null) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(survey.error()), Optional.empty());
        }
        Earthwork work = survey.work();
        BuildingTemplate template = template(work);
        BlockPos anchor = anchor(work);
        BuildPlan plan = ConstructionWork.plan(template, anchor, PLACEMENT, survey.clear(), survey.skip(), Construction.Kind.EARTHWORK);
        ConstructionRules rules = ConstructionSettings.rules();
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(actor, kingdomId, TEMPLATE_ID,
                template.fingerprint(), ConstructionWork.worldPos(level, anchor), PLACEMENT, template.box(anchor, PLACEMENT), survey.clear(),
                plan.size(), KingdomWorldApi.gameTime(server), rules, Construction.Kind.EARTHWORK, 0,
                io.github.sekerator.kingdomworld.core.construction.Materials.NONE, survey.skip());
        change.construction().ifPresent(construction -> {
            construction.setEarthwork(work);
            TEMPLATES.put(construction.id(), template);
            ConstructionWork.remember(construction, plan);
            ConstructionWork.refreshReservations(server);
            LOGGER.info("Earthworks {} of {} laid at {} {} ({}x{}, {} to {}): {} to dig, {} to fill, {} left as they are", construction.id(),
                    kingdomId, work.minX(), work.minZ(), work.sizeX(), work.sizeZ(), work.mode().id(), work.target(), survey.digs(), survey.fills(),
                    survey.keptAll());
        });
        return change;
    }

    // ---------------------------------------------------------------- the work

    /**
     * Digs a block of earthworks: only what grew there; water goes, lava is closed off with cobblestone and its neighbours of
     * lava too, so that none runs into the work.
     */
    static ConstructionWork.StepResult dig(ServerLevel level, Construction construction, BlockPos at, @Nullable LivingEntity breaker,
                                           List<ItemStack> drops) {
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof LiquidBlock) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            sealLava(level, construction, at);
            return ConstructionWork.StepResult.DONE;
        }
        if (state.isAir()) {
            sealLava(level, construction, at);
            return ConstructionWork.StepResult.DONE;
        }
        if (ConstructionWork.isProtected(level, at, state) || ConstructionWork.holdsItems(level, at) || !diggable(state)) {
            // Somebody built or placed something there since the work was laid out: it stays.
            return ConstructionWork.StepResult.BLOCKED;
        }
        drops.addAll(Block.getDrops(state, level, at, null, breaker, PICK));
        level.destroyBlock(at, false, breaker);
        sealLava(level, construction, at);
        return ConstructionWork.StepResult.DONE;
    }

    /**
     * Closes off the lava next to a place dug with cobblestone of the site or the storehouses, as a miner does; on the land of
     * the kingdom only.
     */
    private static void sealLava(ServerLevel level, Construction construction, BlockPos at) {
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) {
                continue;
            }
            BlockPos next = at.relative(side);
            if (!level.isLoaded(next) || !level.getFluidState(next).is(FluidTags.LAVA)
                    || !construction.kingdomId().equals(KingdomWorldApi.ownerAt(level, next))) {
                continue;
            }
            String cobble = "minecraft:cobblestone";
            if (construction.delivered().getOrDefault(cobble, 0) > 0) {
                construction.takeDelivered(cobble, 1);
            } else {
                Item item = PostWork.item(cobble);
                KingdomStorage.take(level.getServer(), construction.kingdomId(), stack -> stack.is(item), 1, construction.anchor());
            }
            level.setBlock(next, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
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
        Map<String, Integer> lacking = new HashMap<>(plan.needed(construction));
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
