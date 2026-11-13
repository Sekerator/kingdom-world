package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.core.citizen.Groves;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A look over the zone of a lumberjack's post column by column, from the highest block that is neither air nor leaves down
 * to the ground: the trees and saplings standing there and the free spots of the grid of new saplings ({@link Groves}). A
 * column costs a few blocks rather than the whole height of the zone, so even the largest zone is looked over quickly.
 *
 * <p>A spot for a new sapling is natural ground (grass, earth, podzol and the like) of the kingdom's own land under the open
 * sky, outside the square of every city of the kingdom, with only natural blocks within {@value #NATURAL_MARGIN} blocks of
 * it: no building, road, path, field, farmland or block of a player.
 */
final class GroveScan {
    /** Blocks around a new sapling, along each side, that must all be natural. */
    static final int NATURAL_MARGIN = 2;
    /** Blocks the look around a spot for a new sapling costs. */
    private static final int SPOT_COST = (2 * NATURAL_MARGIN + 1) * (2 * NATURAL_MARGIN + 1) * 4;

    private final ServerLevel level;
    private final WorkPost post;
    private final WorkZone zone;
    private final LandCheck land;
    private final long total;
    private long index;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private final List<BlockPos> bases = new ArrayList<>();
    private final List<BlockPos> saplings = new ArrayList<>();
    private final List<BlockPos> spots = new ArrayList<>();
    private final Map<Item, Integer> saplingKinds = new HashMap<>();
    private final Map<Item, Integer> logKinds = new HashMap<>();
    private int trees;
    private int columns;

    GroveScan(ServerLevel level, WorkPost post, WorkZone zone, LandCheck land) {
        this.level = level;
        this.post = post;
        this.zone = zone;
        this.land = land;
        this.total = (long) zone.width() * zone.depth();
    }

    boolean done() {
        return index >= total;
    }

    /**
     * Looks at the next column of the zone.
     *
     * @return blocks looked at, at least 1
     */
    int step() {
        int x = zone.minX() + (int) (index % zone.width());
        int z = zone.minZ() + (int) (index / zone.width());
        index++;
        if (!land.owns(x, z) || !level.hasChunk(x >> 4, z >> 4)) {
            return 1;
        }
        columns++;
        int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), zone.maxY());
        int looked = 0;
        for (int y = top; y >= zone.minY(); y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            looked++;
            if (Trees.isLog(state)) {
                if (Trees.isBase(level, cursor, state)) {
                    bases.add(cursor.immutable());
                    if (Trees.isLog(level.getBlockState(cursor.above()))) {
                        trees++;
                        logKinds.merge(state.getBlock().asItem(), 1, Integer::sum);
                    }
                }
                continue;
            }
            if (state.is(BlockTags.SAPLINGS)) {
                saplings.add(cursor.immutable());
                saplingKinds.merge(state.getBlock().asItem(), 1, Integer::sum);
                continue;
            }
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced() || state.getCollisionShape(level, cursor).isEmpty()) {
                continue;
            }
            // The ground of the column.
            if (Groves.onGrid(post.pos().x(), post.pos().z(), x, z)) {
                BlockPos spot = cursor.above();
                looked += SPOT_COST;
                if (plantable(level, post.kingdomId(), spot)) {
                    spots.add(spot);
                }
            }
            break;
        }
        return Math.max(1, looked);
    }

    /**
     * The bases of the logs standing on the ground, trees and stumps.
     */
    List<BlockPos> bases() {
        return bases;
    }

    List<BlockPos> saplings() {
        return saplings;
    }

    /**
     * Free spots of the grid for new saplings: the blocks a sapling would take.
     */
    List<BlockPos> spots() {
        return spots;
    }

    /**
     * Trees standing: bases with a trunk above them.
     */
    int trees() {
        return trees;
    }

    /**
     * Columns of the zone on the kingdom's land that were looked at.
     */
    int columns() {
        return columns;
    }

    /**
     * The sapling most of the saplings of the zone are, if any stand there.
     */
    @Nullable
    Item saplingKind() {
        return mostCommon(saplingKinds);
    }

    /**
     * The log most of the trees of the zone are of; for a zone of saplings only, the log their name gives.
     */
    @Nullable
    Item logKind() {
        Item log = mostCommon(logKinds);
        if (log != null) {
            return log;
        }
        Item sapling = saplingKind();
        ResourceLocation id = sapling == null ? null : ForgeRegistries.ITEMS.getKey(sapling);
        if (id == null || !id.getPath().endsWith("_sapling")) {
            return null;
        }
        String wood = id.getPath().substring(0, id.getPath().length() - "_sapling".length());
        Item found = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), wood + "_log"));
        return found == null || !new ItemStack(found).is(net.minecraft.tags.ItemTags.LOGS) ? null : found;
    }

    /**
     * Where trees and saplings stand, as spots of the grid's rules.
     */
    List<Groves.Spot> stands() {
        List<Groves.Spot> stands = new ArrayList<>(bases.size() + saplings.size());
        for (BlockPos base : bases) {
            stands.add(new Groves.Spot(base.getX(), base.getZ()));
        }
        for (BlockPos sapling : saplings) {
            stands.add(new Groves.Spot(sapling.getX(), sapling.getZ()));
        }
        return stands;
    }

    /**
     * Spots for new saplings, the nearest to the post first: as many as the zone has room for, at most {@code max}.
     */
    List<BlockPos> newSpots(int max) {
        Map<Groves.Spot, BlockPos> byColumn = new HashMap<>();
        for (BlockPos spot : spots) {
            byColumn.put(new Groves.Spot(spot.getX(), spot.getZ()), spot);
        }
        int room = Groves.room(trees, saplings.size(), Groves.capacity(columns));
        List<BlockPos> result = new ArrayList<>();
        for (Groves.Spot spot : Groves.choose(post.pos().x(), post.pos().z(), byColumn.keySet(), stands(), room, max)) {
            result.add(byColumn.get(spot));
        }
        return result;
    }

    /**
     * What one lumberjack fells here a day in logs, the grove planted again and new saplings in its free spots; nothing if
     * no kind of tree is known.
     */
    PostYield estimate() {
        Item log = logKind();
        if (log == null) {
            return PostYield.NONE;
        }
        double felled = Groves.treesPerDay(trees, saplings.size(), newSpots(Integer.MAX_VALUE).size(), Groves.capacity(columns));
        if (felled <= 0.0) {
            return PostYield.NONE;
        }
        double logs = felled * LumberjackBehavior.LOGS_PER_TREE;
        return new PostYield(Map.of(PostWork.itemId(new ItemStack(log)), logs), Map.of(), logs, false);
    }

    @Nullable
    private static Item mostCommon(Map<Item, Integer> counts) {
        Item best = null;
        int most = 0;
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- spots for new saplings

    /**
     * Whether a new sapling may go on a block: natural ground below, free and under the open sky, outside every city of the
     * kingdom, and only natural blocks around it.
     */
    static boolean plantable(ServerLevel level, UUID kingdomId, BlockPos spot) {
        BlockState ground = level.getBlockState(spot.below());
        BlockState there = level.getBlockState(spot);
        if (!ground.is(BlockTags.DIRT) || !there.canBeReplaced() || !there.getFluidState().isEmpty() || !level.canSeeSky(spot)) {
            return false;
        }
        if (CityAreas.at(level.getServer(), kingdomId, Dimensions.id(level), spot.getX(), spot.getZ()).isPresent()) {
            return false;
        }
        BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        for (int dx = -NATURAL_MARGIN; dx <= NATURAL_MARGIN; dx++) {
            for (int dz = -NATURAL_MARGIN; dz <= NATURAL_MARGIN; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    near.set(spot.getX() + dx, spot.getY() + dy, spot.getZ() + dz);
                    if (!level.isLoaded(near) || !isNatural(level.getBlockState(near))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether a block is of nature rather than of people: air, water, earth, sand, rock, snow, natural trees and the
     * plants of the wild. Anything else (buildings, roads and paths, gravel of roads, fields and farmland, blocks with
     * contents, blocks of mods nobody tagged as natural) is not.
     */
    static boolean isNatural(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.hasBlockEntity()) {
            return state.is(Blocks.BEE_NEST);
        }
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.CROPS)) {
            return false;
        }
        if (Trees.isLog(state)) {
            return !Trees.isWorkedWood(state);
        }
        if (state.is(BlockTags.LEAVES)) {
            return Trees.isNaturalLeaves(state);
        }
        return state.getBlock() instanceof LiquidBlock || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES) || state.is(Blocks.CLAY)
                || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.CAVE_VINES)
                || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.COCOA) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.BIG_DRIPLEAF_STEM) || state.is(Blocks.SMALL_DRIPLEAF) || state.is(Blocks.POINTED_DRIPSTONE);
    }
}
