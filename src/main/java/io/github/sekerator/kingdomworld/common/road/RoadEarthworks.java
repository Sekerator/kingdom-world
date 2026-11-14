package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.LandCheck;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.construction.ChunkHold;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.EarthworkPlan;
import io.github.sekerator.kingdomworld.core.road.CellIndex;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.Tags;
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
 * Lays a column of a levelled road into the world, in order: the trees whose trunk stands in its width are felled whole, the
 * liquids next to what is opened are closed off with stone, the roof of a tunnel and a cave under the deck are propped, the
 * natural land is dug from the top down, the embankment is put in from the bottom up, then the pier, the surface or the deck,
 * the rails and the torches. Only what is natural for a road is ever dug ({@link RoadLand#natural}); a protected block, a block
 * with a block entity and anything made stays, and the column is held up with the reason, as it is on the width of another road
 * or in the frame of a building. What is dug settles the debt of the road first, the rest goes to the storehouses (soil the
 * storehouses have no room for to the spoil); where nothing is loaded it waits with the constructions to be dropped.
 */
public final class RoadEarthworks {
    /** Logs of one tree felled at most. */
    private static final int MAX_LOGS = 256;

    /**
     * Why a column waits.
     */
    public enum Hold {
        /** Nothing: the column is laid. */
        NONE(false),
        /** Its place is not loaded. */
        NOT_LOADED(false),
        /** Its land belongs to a kingdom that did not agree. */
        NO_CONSENT(false),
        /** It lies on the width of another road. */
        OTHER_ROAD(true),
        /** It lies in the frame of a building or a construction. */
        BUILDING(true),
        /** Something made, protected or with a block entity stands in its way. */
        MADE_BLOCK(true),
        /** A log column without a crown stands in its way: put up by somebody. */
        LOG_PILLAR(true),
        /** Stone to close off a liquid or prop a roof is missing. */
        NO_LINING(true),
        /** Items for its embankment or pier are missing. */
        NO_ITEMS(true);

        private final boolean blocked;

        Hold(boolean blocked) {
            this.blocked = blocked;
        }

        /**
         * Whether the column is held up by something the king can see to: shown as a column in the way.
         */
        public boolean blocked() {
            return blocked;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * What laying a column came to.
     *
     * @param changes blocks of the world changed
     */
    public record Result(Hold hold, int changes) {
        static final Result LOADED_NOT = new Result(Hold.NOT_LOADED, 0);
    }

    /**
     * A column of a road that waits for something the king can see to.
     */
    public record Blocked(int x, int y, int z, Hold hold) {
    }

    /** Columns in the way, by road, in the order they were met. */
    private static final Map<Long, Map<Long, Blocked>> BLOCKED = new HashMap<>();
    /** The game day a road last told its king of a column in the way. */
    private static final Map<Long, Long> TOLD = new HashMap<>();

    private RoadEarthworks() {
    }

    static void reset() {
        BLOCKED.clear();
        TOLD.clear();
        frames = null;
        framesOf = null;
        ROAD_COLUMNS.clear();
    }

    /**
     * The columns of a road that wait for something the king can see to.
     */
    public static List<Blocked> blocked(long roadId) {
        Map<Long, Blocked> map = BLOCKED.get(roadId);
        return map == null ? List.of() : List.copyOf(map.values());
    }

    static void forget(long roadId) {
        BLOCKED.remove(roadId);
        TOLD.remove(roadId);
        ROAD_COLUMNS.remove(roadId);
    }

    // ---------------------------------------------------------------- what may not be touched

    /** The columns of every road, by road: the road a column lies on is never touched by another one. */
    private static final Map<Long, Columns> ROAD_COLUMNS = new HashMap<>();

    private record Columns(String dimension, int minX, int minZ, int maxX, int maxZ, Set<Long> columns) {
        boolean has(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && columns.contains(RoadLayoutColumns.key(x, z));
        }
    }

    private static Columns columnsOf(Road road) {
        return ROAD_COLUMNS.computeIfAbsent(road.id(), id -> {
            Set<Long> set = new HashSet<>();
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (RoadLayout.Block block : road.blocks()) {
                set.add(RoadLayoutColumns.key(block.x(), block.z()));
                minX = Math.min(minX, block.x());
                minZ = Math.min(minZ, block.z());
                maxX = Math.max(maxX, block.x());
                maxZ = Math.max(maxZ, block.z());
            }
            return new Columns(road.dimension(), minX, minZ, maxX, maxZ, set);
        });
    }

    /**
     * Whether a column lies on the width of another road of the dimension.
     */
    static boolean onOtherRoad(MinecraftServer server, Road road, int x, int z) {
        for (Road other : RoadWork.state(server).roads()) {
            if (other.id() != road.id() && other.dimension().equals(road.dimension()) && columnsOf(other).has(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static CellIndex<Box> frames;
    private static ConstructionBook framesOf;
    private static long framesVersion;

    /**
     * Whether a column lies in the frame of a building or a construction, widened by a block; walls and gates are crossed at their
     * gates and are not frames here.
     */
    static boolean inFrame(MinecraftServer server, String dimension, int x, int z) {
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        if (frames == null || framesOf != book || framesVersion != book.version()) {
            frames = new CellIndex<>(4);
            framesOf = book;
            framesVersion = book.version();
            for (io.github.sekerator.kingdomworld.core.construction.Building building : book.buildings()) {
                Box box = building.box();
                if (box != null && !BuildingRoles.WALL.equals(building.role())) {
                    frames.add(building.anchor().dimension(), box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1, box);
                }
            }
            for (io.github.sekerator.kingdomworld.core.construction.Construction construction : book.all()) {
                Box box = construction.box();
                frames.add(construction.dimension(), box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1, box);
            }
        }
        for (Box box : frames.at(dimension, x, z)) {
            if (x >= box.minX() - 1 && x <= box.maxX() + 1 && z >= box.minZ() - 1 && z <= box.maxZ() + 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- laying a column

    /**
     * Lays a column of a levelled road whose items were paid for.
     */
    static Result place(ServerLevel level, Road road, Road.Section section, RoadLayout.Block block, Set<UUID> consented) {
        MinecraftServer server = level.getServer();
        if (block.survey() != null && block.survey().onRoad()) {
            // On another road: nothing is done there, its blocks are left as they are.
            return new Result(Hold.NONE, 0);
        }
        int x = block.x();
        int z = block.z();
        // The column and two blocks round it are loaded: changing a block updates its neighbours.
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                if (!ChunkHold.loaded(level, new BlockPos(x + dx, 0, z + dz))) {
                    return Result.LOADED_NOT;
                }
            }
        }
        BlockPos column = new BlockPos(x, block.y(), z);
        if (!RoadBlocks.allowedAt(level, road, column, consented)) {
            return new Result(Hold.NO_CONSENT, 0);
        }
        String dimension = Dimensions.id(level);
        if (onOtherRoad(server, road, x, z)) {
            return held(road, block, Hold.OTHER_ROAD);
        }
        if (inFrame(server, dimension, x, z)) {
            return held(road, block, Hold.BUILDING);
        }
        Work work = new Work(level, road, section, block, consented);
        Hold hold = work.run();
        if (hold.blocked()) {
            return held(road, block, hold);
        }
        if (hold == Hold.NONE) {
            Map<Long, Blocked> map = BLOCKED.get(road.id());
            if (map != null && map.remove(RoadLayoutColumns.key(x, z)) != null && map.isEmpty()) {
                BLOCKED.remove(road.id());
            }
        }
        return new Result(hold, work.changes);
    }

    private static Result held(Road road, RoadLayout.Block block, Hold hold) {
        BLOCKED.computeIfAbsent(road.id(), id -> new LinkedHashMap<>()).put(RoadLayoutColumns.key(block.x(), block.z()),
                new Blocked(block.x(), block.y(), block.z(), hold));
        return new Result(hold, 0);
    }

    /**
     * Tells the king of a road with a column in the way once a game day.
     */
    static void tell(MinecraftServer server, Road road) {
        List<Blocked> list = blocked(road.id());
        if (list.isEmpty()) {
            return;
        }
        long day = KingdomWorldApi.gameTime(server) / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
        Long last = TOLD.get(road.id());
        if (last != null && last >= day) {
            return;
        }
        TOLD.put(road.id(), day);
        Blocked first = list.get(0);
        RoadWork.notify(server, road.kingdomId(), net.minecraft.network.chat.Component.translatable(RoadWork.KEY + "column_blocked", road.id(),
                list.size(), first.x(), first.y(), first.z(), net.minecraft.network.chat.Component.translatable(RoadWork.KEY + "hold." + first.hold().id()))
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    /**
     * The laying of one column.
     */
    private static final class Work {
        final ServerLevel level;
        final MinecraftServer server;
        final Road road;
        final Road.Section section;
        final RoadLayout.Block block;
        final Set<UUID> consented;
        final RoadClass roadClass;
        final UUID builder;
        final int deck;
        final int height;
        int changes;

        Work(ServerLevel level, Road road, Road.Section section, RoadLayout.Block block, Set<UUID> consented) {
            this.level = level;
            this.server = level.getServer();
            this.road = road;
            this.section = section;
            this.block = block;
            this.consented = consented;
            this.roadClass = road.roadClass();
            this.builder = section.builder();
            this.deck = block.y();
            this.height = RoadSettings.earth().tunnelHeight();
        }

        Hold run() {
            int x = block.x();
            int z = block.z();
            boolean tunnel = block.kind() == RouteCell.Kind.TUNNEL;
            boolean bridge = block.kind() == RouteCell.Kind.BRIDGE;
            // Nothing is changed before it is known that nothing made stands in the way.
            Hold check = room(x, z, new ArrayList<>());
            if (check != Hold.NONE) {
                return check;
            }
            // 1. Trees whose trunk stands in the width fall whole.
            Hold trees = fell(x, z, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
            if (trees != Hold.NONE) {
                return trees;
            }
            List<Integer> dig = new ArrayList<>();
            check = room(x, z, dig);
            if (check != Hold.NONE) {
                return check;
            }
            int high = dig.isEmpty() ? deck : dig.get(dig.size() - 1);
            // 2. Liquids next to the opened room are closed off, the roof of a tunnel and a cave under the deck propped.
            Hold lining = seal(x, z, deck + 1, Math.max(high, tunnel ? deck + height : deck), tunnel, bridge);
            if (lining != Hold.NONE) {
                return lining;
            }
            // 3. The natural land from the top down.
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, deck, z);
            for (int i = dig.size() - 1; i >= 0; i--) {
                pos.setY(dig.get(i));
                remove(pos.immutable(), level.getBlockState(pos));
            }
            int ground = groundBelow(x, z, deck);
            // 4. The embankment from the bottom up.
            int fillTop = block.role() == RoadLayout.Role.VERGE ? deck : deck - 1;
            int planned = block.survey() == null ? 0 : block.survey().fill();
            int fillFrom = ground + 1;
            if (!bridge && fillTop - ground > Math.max(planned, RoadSettings.rules().maxFill())) {
                // A cave opens under the deck: it is covered with a block of lining rather than filled to its floor.
                fillFrom = fillTop + 1;
                BlockPos under = new BlockPos(x, deck - 1, z);
                if (block.role() != RoadLayout.Role.VERGE && RoadBlocks.free(level.getBlockState(under))) {
                    Item item = item(RoadLayout.Material.LINING);
                    if (item == null) {
                        return Hold.NO_LINING;
                    }
                    put(under, RoadBlocks.stateOf(item), item);
                }
            }
            if (!bridge) {
                for (int y = fillFrom; y <= fillTop; y++) {
                    pos.setY(y);
                    if (!RoadBlocks.free(level.getBlockState(pos))) {
                        continue;
                    }
                    Item item = item(RoadLayout.Material.FILL);
                    if (item == null) {
                        return Hold.NO_ITEMS;
                    }
                    put(pos.immutable(), RoadBlocks.stateOf(item), item);
                }
            }
            // 5. The pier of a bridge.
            if (bridge && block.survey() != null && block.survey().pier() > 0) {
                for (int y = ground + 1; y < deck; y++) {
                    pos.setY(y);
                    if (!RoadBlocks.free(level.getBlockState(pos))) {
                        continue;
                    }
                    Item item = item(RoadLayout.Material.PIER);
                    if (item == null) {
                        return Hold.NO_ITEMS;
                    }
                    put(pos.immutable(), RoadBlocks.stateOf(item), item);
                }
            }
            // 6. The surface or the deck.
            surface(new BlockPos(x, deck, z), bridge);
            // 7. Rails and torches.
            BlockPos above = new BlockPos(x, deck + 1, z);
            if (bridge && block.role() == RoadLayout.Role.VERGE && roadClass != RoadClass.PATH) {
                Item rail = RoadBlocks.usePaid(section, RoadLayout.Material.RAIL, roadClass);
                if (rail != null) {
                    if (level.getBlockState(above).isAir()) {
                        put(above, RoadBlocks.stateOf(rail), rail);
                    } else {
                        keep(new ItemStack(rail), above);
                    }
                }
            }
            if (block.survey() != null && block.survey().light()) {
                Item torch = RoadBlocks.usePaid(section, RoadLayout.Material.TORCH, roadClass);
                if (torch != null) {
                    BlockState state = RoadBlocks.stateOf(torch);
                    if (level.getBlockState(above).isAir() && state.canSurvive(level, above)) {
                        put(above, state, torch);
                    } else {
                        // A torch with nowhere to stand goes back.
                        keep(new ItemStack(torch), above);
                    }
                }
            }
            return Hold.NONE;
        }

        /**
         * The heights to dig above the deck, from the bottom up: in a tunnel its room; in a cutting everything natural up to the
         * sky; elsewhere the ground over the deck and what overhangs it, up to the open air above the head of a rider.
         *
         * @return why it may not be dug, or {@link Hold#NONE}
         */
        private Hold room(int x, int z, List<Integer> dig) {
            boolean tunnel = block.kind() == RouteCell.Kind.TUNNEL;
            boolean cut = block.kind() == RouteCell.Kind.CUT;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int limit = tunnel ? deck + height : Math.max(deck + 3, top);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, deck, z);
            for (int y = deck + 1; y <= limit; y++) {
                BlockState state = level.getBlockState(pos.setY(y));
                if (state.isAir()) {
                    if (!tunnel && !cut && y > deck + 3) {
                        break;
                    }
                    continue;
                }
                if (!RoadLand.natural(state)) {
                    if (!state.getFluidState().isEmpty() && state.canBeReplaced()) {
                        // Water or lava in the room: closed off around and taken out.
                        dig.add(y);
                        continue;
                    }
                    return Hold.MADE_BLOCK;
                }
                if (!tunnel && !cut && y > deck + 3 && !solid(state)) {
                    continue;
                }
                dig.add(y);
            }
            return Hold.NONE;
        }

        /**
         * The surface of the way, the deck of a bridge, the earth of the embankment of a path, or on a verge only what grows there
         * cleared.
         */
        private void surface(BlockPos at, boolean bridge) {
            RoadLayout.Material top = block.top(roadClass);
            if (bridge) {
                BlockState here = level.getBlockState(at);
                if (!RoadBlocks.free(here) && RoadLand.natural(here)) {
                    remove(at, here);
                }
                Item item = RoadBlocks.usePaid(section, RoadLayout.Material.BRIDGE, roadClass);
                if (item != null && RoadBlocks.free(level.getBlockState(at))) {
                    RoadBlocks.clearAbove(level, builder, at);
                    put(at, RoadBlocks.stateOf(item), item);
                } else if (item != null) {
                    keep(new ItemStack(item), at);
                }
            } else if (top == RoadLayout.Material.SURFACE) {
                Item item = RoadBlocks.usePaid(section, RoadLayout.Material.SURFACE, roadClass);
                if (item != null) {
                    if (RoadBlocks.replace(level, builder, at, item)) {
                        changes++;
                    } else {
                        keep(new ItemStack(item), at);
                    }
                }
            } else if (top == RoadLayout.Material.FILL) {
                Item item = RoadBlocks.usePaid(section, RoadLayout.Material.FILL, roadClass);
                if (item != null && RoadBlocks.free(level.getBlockState(at))) {
                    put(at, RoadBlocks.stateOf(item), item);
                } else if (item != null) {
                    keep(new ItemStack(item), at);
                }
                if (RoadBlocks.makePath(level, at)) {
                    changes++;
                }
            } else if (block.role() != RoadLayout.Role.VERGE && roadClass == RoadClass.PATH) {
                if (RoadBlocks.makePath(level, at)) {
                    changes++;
                }
            } else {
                RoadBlocks.clearAbove(level, builder, at);
            }
        }

        /**
         * The top of the solid ground under a height: the bottom of an embankment or a pier.
         */
        private int groundBelow(int x, int z, int from) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
            for (int y = from; y > level.getMinBuildHeight(); y--) {
                BlockState state = level.getBlockState(pos.setY(y));
                if (!RoadBlocks.free(state) && state.getFluidState().isEmpty()) {
                    return y;
                }
            }
            return level.getMinBuildHeight();
        }

        private boolean solid(BlockState state) {
            return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES);
        }

        /**
         * Fells every tree whose trunk stands in the column above the deck, whole: its logs to the storehouses.
         */
        private Hold fell(int x, int z, int top) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, deck, z);
            for (int y = Math.max(deck - 1, level.getMinBuildHeight()); y <= top; y++) {
                BlockState state = level.getBlockState(pos.setY(y));
                if (!Trees.isLog(state) || Trees.isWorkedWood(state)) {
                    continue;
                }
                BlockPos base = pos.immutable();
                WorkZone zone = new WorkZone(Dimensions.id(level), x - 12, deck - 2, z - 12, x + 12, top + 32, z + 12);
                LandCheck land = LandCheck.where(level, (bx, bz) -> RoadBlocks.allowedAt(level, road, new BlockPos(bx, deck, bz), consented));
                Trees.Tree tree = Trees.find(level, base, zone, land, MAX_LOGS, false);
                if (tree == null) {
                    // A column of logs without a crown was put up by somebody; a branch of a tree standing elsewhere is dug as it is.
                    if (Trees.isBase(level, base, state)) {
                        return Hold.LOG_PILLAR;
                    }
                    continue;
                }
                for (BlockPos log : tree.logs()) {
                    remove(log, level.getBlockState(log));
                }
                for (BlockPos leaf : tree.leaves()) {
                    BlockState leaves = level.getBlockState(leaf);
                    if (Trees.isNaturalLeaves(leaves)) {
                        remove(leaf, leaves);
                    }
                }
            }
            return Hold.NONE;
        }

        /**
         * Closes off with stone every liquid next to the room opened between two heights and over it, and props the roof of a
         * tunnel that would fall or is not stone.
         */
        private Hold seal(int x, int z, int low, int high, boolean tunnel, boolean bridge) {
            List<BlockPos> places = new ArrayList<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            if (!bridge) {
                for (int y = low; y <= high; y++) {
                    for (Direction direction : Direction.values()) {
                        if (direction == Direction.DOWN) {
                            continue;
                        }
                        pos.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                        if (direction == Direction.UP && y < high) {
                            continue;
                        }
                        BlockState side = level.getBlockState(pos);
                        if (!side.getFluidState().isEmpty() && side.canBeReplaced() && RoadBlocks.allowedAt(level, road, pos, consented)) {
                            places.add(pos.immutable());
                        }
                    }
                }
            }
            if (tunnel) {
                BlockPos roof = new BlockPos(x, deck + height + 1, z);
                BlockState state = level.getBlockState(roof);
                boolean weak = state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof FallingBlock
                        || RoadLand.soil(state) || !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.BASE_STONE_NETHER)
                        && !state.is(Tags.Blocks.ORES) && !state.is(Tags.Blocks.STONE);
                if (weak && (state.isAir() || RoadLand.natural(state) || !state.getFluidState().isEmpty())) {
                    places.add(roof);
                }
            }
            for (BlockPos place : places) {
                Item item = item(RoadLayout.Material.LINING);
                if (item == null) {
                    return Hold.NO_LINING;
                }
                BlockState state = level.getBlockState(place);
                if (!state.isAir() && state.getFluidState().isEmpty() && RoadLand.natural(state)) {
                    remove(place, state);
                }
                level.setBlock(place, RoadBlocks.stateOf(item), Block.UPDATE_ALL);
                changes++;
            }
            return Hold.NONE;
        }

        /**
         * An item of a material: from the paid items of the section, then for an embankment and lining from what the road digs,
         * then from the storehouses; {@code null} if there is none.
         */
        private Item item(RoadLayout.Material material) {
            Item paid = RoadBlocks.usePaid(section, material, roadClass);
            if (paid != null) {
                return paid;
            }
            if ((material == RoadLayout.Material.FILL || material == RoadLayout.Material.LINING) && builder.equals(road.kingdomId())) {
                for (String id : new ArrayList<>(road.credit().keySet())) {
                    Item item = PostWork.item(id);
                    if (item != null && RoadBlocks.fits(item, material, roadClass) && road.useCredit(id, 1) == 1) {
                        return item;
                    }
                }
            }
            Map<String, Integer> chosen = RoadBuilding.choose(server, road, builder, List.of(material));
            if (chosen == null || chosen.isEmpty()) {
                return null;
            }
            String id = chosen.keySet().iterator().next();
            List<KingdomStorage.Take> takes = KingdomStorage.plan(server, builder, chosen, item -> 0,
                    ConstructionWork.worldPos(level, new BlockPos(block.x(), block.y(), block.z())));
            if (takes == null || KingdomStorage.takePlanned(server, takes) == null) {
                return null;
            }
            return PostWork.item(id);
        }

        private void put(BlockPos pos, BlockState state, Item item) {
            ConstructionWork.place(level, pos, state, item);
            changes++;
        }

        /**
         * Takes a natural block out without breaking it as a player would, so that ice does not turn into water: its drops settle
         * the debt of the road, the rest goes to the storehouses.
         */
        private void remove(BlockPos pos, BlockState state) {
            if (state.isAir()) {
                return;
            }
            List<ItemStack> drops = state.getFluidState().isEmpty() ? Block.getDrops(state, level, pos, null) : List.of();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            changes++;
            for (ItemStack drop : drops) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(drop.getItem());
                if (id != null && builder.equals(road.kingdomId())) {
                    int settled = road.settleOwed(id.toString(), drop.getCount());
                    drop.shrink(settled);
                }
                if (!drop.isEmpty()) {
                    keep(drop, pos);
                }
            }
        }

        /**
         * Puts something dug or left over into the storehouses; soil they have no room for goes to the spoil, anything else lies at
         * the place or waits to be dropped there.
         */
        private void keep(ItemStack stack, BlockPos at) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && EarthworkPlan.soil(id.toString())) {
                ItemStack rest = KingdomStorage.insert(server, builder, stack, ConstructionWork.worldPos(level, at));
                if (!rest.isEmpty() && EarthworkPlan.destination(id.toString(), 0, false) == EarthworkPlan.Destination.SPOIL) {
                    return;
                }
                if (!rest.isEmpty()) {
                    RoadBlocks.store(level, builder, at, rest);
                }
                return;
            }
            RoadBlocks.store(level, builder, at, stack);
        }
    }

    /**
     * The key of a column.
     */
    static final class RoadLayoutColumns {
        private RoadLayoutColumns() {
        }

        static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFF_FFFFL);
        }
    }
}
