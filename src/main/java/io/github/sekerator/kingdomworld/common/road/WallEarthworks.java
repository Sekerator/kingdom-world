package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.LandCheck;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.construction.ChunkHold;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.construction.EarthworkPlan;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The earthworks of a wall, done before its columns go up: trees whose trunk stands in a column fall whole, everything natural and
 * loose in a column is cleared to the storehouses, a hole under a column is filled up to its foot, and a wall at a height of its own
 * stands in a cut through the ground above that height. Blocks a player made stay: the wall has a gap there, and its kingdom hears
 * of it once. The earth for the fill is what the wall dug first, then dirt, then the fill of roads from the storehouses; a kingdom
 * of the world fills for nothing, as it builds its walls.
 */
final class WallEarthworks {
    /** Logs of one tree felled at most. */
    private static final int MAX_LOGS = 256;
    /** Columns of the ramp before and after a gate at most. */
    private static final int RAMP = 8;

    /** What came of the earthworks of a column. */
    enum Step {
        /** The column may go up. */
        READY,
        /** Its place or what is around it is not loaded. */
        NOT_LOADED,
        /** Earth to fill under it is missing. */
        NO_EARTH
    }

    /** A block a player made in the way of a wall, the first of its wall, by wall. */
    private static final Map<Long, long[]> BLOCKED = new LinkedHashMap<>();
    /** Walls whose kingdom heard of the blocks in their way. */
    private static final Set<Long> TOLD_BLOCKED = new HashSet<>();
    /** The day a kingdom last heard that a wall waits for earth, by wall. */
    private static final Map<Long, Long> TOLD_EARTH = new HashMap<>();

    private WallEarthworks() {
    }

    static void reset() {
        BLOCKED.clear();
        TOLD_BLOCKED.clear();
        TOLD_EARTH.clear();
    }

    // ---------------------------------------------------------------- the ground

    /**
     * Whether a block lies over the ground without being it: air, water, what grows (trunks and leaves of trees, plants,
     * mushrooms, snow) and what is replaced by placing.
     */
    static boolean loose(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS) || RoadLand.cover(state) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(BlockTags.SNOW)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Whether a block is natural to clear out of the line of a wall: what grows there, what lies loose and the natural ground.
     */
    static boolean natural(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        if (state.is(BlockTags.LEAVES)) {
            return Trees.isNaturalLeaves(state);
        }
        return RoadLand.natural(state) || RoadLand.cover(state) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS) || state.is(BlockTags.SNOW) || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * The top of the solid ground at or under a height: water, plants, trees and the like are not ground.
     */
    static int groundUnder(ServerLevel level, int x, int from, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
        int lowest = Math.max(level.getMinBuildHeight(), from - 64);
        while (pos.getY() > lowest && loose(level.getBlockState(pos))) {
            pos.setY(pos.getY() - 1);
        }
        return pos.getY();
    }

    /**
     * The ground a wall is laid out on at a place: under what {@link #groundUnder} goes through, and under what somebody made that is
     * less than a whole block (a fence, a torch, a sign), which stays in the line of the wall as a gap. A whole block somebody made
     * on the ground is the ground of the wall.
     */
    static int layoutGround(ServerLevel level, int x, int from, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
        int lowest = Math.max(level.getMinBuildHeight(), from - 64);
        while (pos.getY() > lowest) {
            BlockState state = level.getBlockState(pos);
            boolean small = io.github.sekerator.kingdomworld.common.construction.Earthworks.made(level, pos, state)
                    && !state.isCollisionShapeFullBlock(level, pos);
            if (!loose(state) && !small) {
                break;
            }
            pos.setY(pos.getY() - 1);
        }
        return pos.getY();
    }

    // ---------------------------------------------------------------- a column

    /**
     * The earthworks of a column of a wall, before its blocks go up.
     */
    static Step column(ServerLevel level, Wall wall, Wall.Block block) {
        int x = block.x();
        int z = block.z();
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                if (!ChunkHold.loaded(level, new BlockPos(x + dx, 0, z + dz))) {
                    return Step.NOT_LOADED;
                }
            }
        }
        int foot = block.y();
        int top = foot + block.height();
        int cutTop = top;
        if (wall.hasBase()) {
            // A wall at a height of its own stands in a cut through the ground over it, up to the surface.
            int surface = io.github.sekerator.kingdomworld.common.construction.Earthworks.surface(level, x, z) - 1;
            cutTop = Math.max(top, Math.min(surface, foot + ConstructionSettings.earthworkDepth() + block.height()));
        }
        fell(level, wall, x, z, foot, cutTop);
        clear(level, wall, x, z, foot + 1, cutTop, top);
        if (!fillUnder(level, wall, x, foot, z)) {
            tellEarth(level.getServer(), wall);
            return Step.NO_EARTH;
        }
        return Step.READY;
    }

    /**
     * Fells every tree whose trunk stands in a column between two heights, whole: its logs and leaves to the storehouses. A column
     * of logs without a crown was put up by somebody and stays.
     */
    private static void fell(ServerLevel level, Wall wall, int x, int z, int foot, int top) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, foot, z);
        UUID kingdom = wall.kingdomId();
        for (int y = Math.max(foot - 1, level.getMinBuildHeight()); y <= top; y++) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (!Trees.isLog(state) || Trees.isWorkedWood(state) || mine(wall, pos.asLong())) {
                continue;
            }
            BlockPos base = pos.immutable();
            WorkZone zone = new WorkZone(Dimensions.id(level), x - 12, foot - 2, z - 12, x + 12, top + 32, z + 12);
            LandCheck land = LandCheck.where(level, (bx, bz) -> {
                UUID owner = KingdomWorldApi.ownerAt(level, new BlockPos(bx, foot, bz));
                return owner == null || owner.equals(kingdom);
            });
            Trees.Tree tree = Trees.find(level, base, zone, land, MAX_LOGS, false);
            if (tree == null) {
                continue;
            }
            for (BlockPos log : tree.logs()) {
                remove(level, wall, log, level.getBlockState(log));
            }
            for (BlockPos leaf : tree.leaves()) {
                BlockState leaves = level.getBlockState(leaf);
                if (Trees.isNaturalLeaves(leaves)) {
                    remove(level, wall, leaf, leaves);
                }
            }
        }
    }

    /**
     * Clears what is natural and loose in a column, from the bottom up: the blocks of the wall and of older walls are left for the
     * placing, and a block a player made stays, noted when it stands where the wall goes.
     *
     * @param wallTop the top of the wall itself; above it only natural ground is cut, nothing is noted
     */
    private static void clear(ServerLevel level, Wall wall, int x, int z, int from, int to, int wallTop) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
        for (int y = from; y <= to; y++) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
                // Water is taken by the block put in its place.
                continue;
            }
            long at = pos.asLong();
            if (mine(wall, at) || otherWall(level.getServer(), wall, at)) {
                continue;
            }
            if (natural(state) && !ConstructionWork.isProtected(level, pos, state)) {
                remove(level, wall, pos.immutable(), state);
            } else if (y <= wallTop) {
                blocked(wall, x, y, z);
            }
        }
    }

    /**
     * Fills a hole under a column from the solid ground up to its foot, as deep as earthworks may go.
     *
     * @return whether the column stands on the ground now; {@code false} if earth ran out
     */
    private static boolean fillUnder(ServerLevel level, Wall wall, int x, int foot, int z) {
        int ground = groundUnder(level, x, foot, z);
        int from = Math.max(ground + 1, foot - ConstructionSettings.earthworkDepth() + 1);
        return fill(level, wall, x, z, from, foot);
    }

    /**
     * Fills a column between two heights with earth, from the bottom up; natural loose blocks there are cleared first, made ones
     * left.
     *
     * @return whether it is filled; {@code false} if earth ran out
     */
    private static boolean fill(ServerLevel level, Wall wall, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
        for (int y = from; y <= to; y++) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (!loose(state)) {
                continue;
            }
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
                if (!natural(state) || mine(wall, pos.asLong())) {
                    continue;
                }
                remove(level, wall, pos.immutable(), state);
            }
            Item earth = earth(level, wall, pos);
            if (earth == null) {
                return false;
            }
            ConstructionWork.place(level, pos.immutable(), RoadBlocks.stateOf(earth), earth);
        }
        return true;
    }

    /**
     * One block of earth for a fill: from what the wall dug, then dirt, coarse dirt and the fill of roads from the storehouses; a
     * kingdom of the world fills with dirt for nothing.
     */
    private static Item earth(ServerLevel level, Wall wall, BlockPos at) {
        for (String id : List.copyOf(wall.spoil().keySet())) {
            Item item = PostWork.item(id);
            if (item instanceof BlockItem && wall.useSpoil(id)) {
                return item;
            }
        }
        MinecraftServer server = level.getServer();
        if (io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, wall.kingdomId())) {
            return net.minecraft.world.item.Items.DIRT;
        }
        Map<String, Long> stock = KingdomStorage.countsById(server, wall.kingdomId());
        List<String> order = new ArrayList<>(List.of("minecraft:dirt", "minecraft:coarse_dirt"));
        for (String id : stock.keySet()) {
            Item item = PostWork.item(id);
            if (!order.contains(id) && item instanceof BlockItem && new ItemStack(item).is(RoadBlocks.FILL)) {
                order.add(id);
            }
        }
        for (String id : order) {
            if (stock.getOrDefault(id, 0L) <= 0) {
                continue;
            }
            List<KingdomStorage.Take> takes = KingdomStorage.plan(server, wall.kingdomId(), Map.of(id, 1), item -> 0,
                    ConstructionWork.worldPos(level, at));
            if (takes != null && KingdomStorage.takePlanned(server, takes) != null) {
                return PostWork.item(id);
            }
        }
        return null;
    }

    /**
     * Takes a natural block out without breaking it as a player would: earth it gives is kept to fill under the wall, the rest goes
     * to the storehouses.
     */
    private static void remove(ServerLevel level, Wall wall, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return;
        }
        List<ItemStack> drops = state.getFluidState().isEmpty() ? Block.getDrops(state, level, pos, null) : List.of();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (ItemStack drop : drops) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(drop.getItem());
            if (id != null && drop.getItem() instanceof BlockItem && drop.is(RoadBlocks.FILL)) {
                wall.addSpoil(id.toString(), drop.getCount());
                continue;
            }
            RoadBlocks.store(level, wall.kingdomId(), pos, drop);
        }
    }

    /**
     * Puts the earth a wall kept and did not use into the storehouses once it stands; what they have no room for goes to the spoil
     * heap.
     */
    static void keepSpoil(ServerLevel level, Wall wall) {
        if (wall.spoil().isEmpty()) {
            return;
        }
        RouteCell first = wall.cells().get(0);
        BlockPos at = new BlockPos(first.x(), first.y(), first.z());
        for (Map.Entry<String, Integer> entry : wall.takeSpoil().entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item == null) {
                continue;
            }
            int left = entry.getValue();
            while (left > 0) {
                int count = Math.min(left, item.getMaxStackSize(new ItemStack(item)));
                left -= count;
                ItemStack rest = KingdomStorage.insert(level.getServer(), wall.kingdomId(), new ItemStack(item, count),
                        ConstructionWork.worldPos(level, at));
                if (!rest.isEmpty() && !EarthworkPlan.soil(entry.getKey())) {
                    RoadBlocks.store(level, wall.kingdomId(), at, rest);
                }
            }
        }
    }

    /**
     * Whether a block is the wall's own or an older wall's it takes the place of: the placing deals with those.
     */
    private static boolean mine(Wall wall, long at) {
        return wall.placedHere(at) || wall.replaces(at);
    }

    /**
     * Whether a block belongs to another wall of the same kingdom.
     */
    private static boolean otherWall(MinecraftServer server, Wall wall, long at) {
        for (Wall other : RoadWork.state(server).walls()) {
            if (other != wall && other.kingdomId().equals(wall.kingdomId()) && other.dimension().equals(wall.dimension())
                    && (other.placedHere(at) || other.replaces(at))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- gates

    /**
     * Levels a gate of a wall at a height of its own: its floor is the foot of the wall, the ground over it is cut, the hole under
     * it filled, and before and after it a short ramp of earth or a cut meets the land where the step would be higher than one
     * block. Only what is loaded is changed; earth missing leaves the rest of a ramp as it is.
     */
    static void levelGate(ServerLevel level, Wall wall, int gate) {
        if (!wall.hasBase()) {
            return;
        }
        int size = wall.cells().size();
        int foot = wall.baseY() - 1;
        RouteCell middle = wall.cells().get(gate);
        int dx = middle.x() - wall.centerX();
        int dz = middle.z() - wall.centerZ();
        int nx = Math.abs(dx) > Math.abs(dz) ? Integer.signum(dx) : 0;
        int nz = nx == 0 ? (dz >= 0 ? 1 : -1) : 0;
        int head = foot + wall.wallClass().height() + 1;
        for (int i = gate - Wall.GATE_HALF; i <= gate + Wall.GATE_HALF; i++) {
            RouteCell cell = wall.cells().get(Math.floorMod(i, size));
            if (!ChunkHold.loaded(level, new BlockPos(cell.x(), 0, cell.z()))) {
                continue;
            }
            int surface = io.github.sekerator.kingdomworld.common.construction.Earthworks.surface(level, cell.x(), cell.z()) - 1;
            int cutTop = Math.max(head, Math.min(surface, foot + ConstructionSettings.earthworkDepth() + head - foot));
            fell(level, wall, cell.x(), cell.z(), foot, cutTop);
            clear(level, wall, cell.x(), cell.z(), foot + 1, cutTop, foot);
            fillUnder(level, wall, cell.x(), foot, cell.z());
            for (int side = -1; side <= 1; side += 2) {
                ramp(level, wall, cell.x(), cell.z(), nx * side, nz * side, foot);
            }
        }
    }

    /**
     * A ramp from the floor of a gate out to the land along a direction: every column a block nearer the land than the one before,
     * until the land is within a block of it.
     */
    private static void ramp(ServerLevel level, Wall wall, int x, int z, int stepX, int stepZ, int floor) {
        for (int k = 1; k <= RAMP; k++) {
            int cx = x + stepX * k;
            int cz = z + stepZ * k;
            if (!ChunkHold.loaded(level, new BlockPos(cx, 0, cz)) || wall.cellAt(cx, cz) >= 0) {
                return;
            }
            int surface = io.github.sekerator.kingdomworld.common.construction.Earthworks.surface(level, cx, cz) - 1;
            int land = groundUnder(level, cx, surface, cz);
            if (Math.abs(land - floor) <= k) {
                return;
            }
            int height = floor + Integer.signum(land - floor) * k;
            if (land > height) {
                clear(level, wall, cx, cz, height + 1, land, Integer.MIN_VALUE);
            } else if (!fill(level, wall, cx, cz, land + 1, height)) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------- telling

    private static void blocked(Wall wall, int x, int y, int z) {
        BLOCKED.putIfAbsent(wall.id(), new long[]{x, y, z, 0});
        BLOCKED.get(wall.id())[3]++;
    }

    /**
     * Tells the kingdoms of walls with made blocks in their way, once for each wall.
     */
    static void tell(MinecraftServer server) {
        if (BLOCKED.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, long[]> entry : BLOCKED.entrySet()) {
            if (!TOLD_BLOCKED.add(entry.getKey())) {
                continue;
            }
            RoadWork.state(server).wall(entry.getKey()).ifPresent(wall -> {
                long[] at = entry.getValue();
                String name = CityWalls.place(server, wall.kingdomId(), wall.cityId()).map(CityWalls.Place::name).orElse("?");
                RoadWork.notify(server, wall.kingdomId(), Component.translatable(RoadWork.KEY + "wall_column_blocked", name, at[0], at[1], at[2],
                        at[3]).withStyle(ChatFormatting.GOLD));
            });
        }
        BLOCKED.clear();
    }

    /**
     * Tells the kingdom of a wall that waits for earth once a game day.
     */
    private static void tellEarth(MinecraftServer server, Wall wall) {
        long day = KingdomWorldApi.gameTime(server) / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
        Long last = TOLD_EARTH.get(wall.id());
        if (last != null && last >= day) {
            return;
        }
        TOLD_EARTH.put(wall.id(), day);
        String name = CityWalls.place(server, wall.kingdomId(), wall.cityId()).map(CityWalls.Place::name).orElse("?");
        RoadWork.notify(server, wall.kingdomId(), Component.translatable(RoadWork.KEY + "wall_waits_earth", name).withStyle(ChatFormatting.GOLD));
    }

    /**
     * Whether the wall noted made blocks in its way, for the tests.
     */
    static boolean blockedNoted(long wallId) {
        return BLOCKED.containsKey(wallId) || TOLD_BLOCKED.contains(wallId);
    }
}
