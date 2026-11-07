package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.road.WallContour;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cities and their walls at work on the server: the level of a city by the people and buildings around it, walls laid as
 * rings on the lay of the land with gates where roads cross them, built by the same builders and from the storehouses as
 * roads, entered into the register of buildings as fortifications once finished, and the entry of players into cities.
 *
 * <p>Public points for later stages: {@link #level}, {@link #gates} and {@link CityEntryEvent}.
 */
public final class CityWalls {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = RoadWork.KEY;
    public static final TagKey<Item> WALL_WOOD = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(KingdomWorld.MOD_ID, "wall_wood"));
    public static final TagKey<Item> WALL_STONE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(KingdomWorld.MOD_ID, "wall_stone"));
    public static final String WALL_TEMPLATE = KingdomWorld.MOD_ID + ":city_wall";
    public static final String GATE_TEMPLATE = KingdomWorld.MOD_ID + ":city_gate";
    /**
     * Category of walls and gates in the register of buildings. They are built and kept by the roads tab: the upgrades,
     * repairs, demolitions and automatic repairs of buildings by template leave them alone.
     */
    public static final String CATEGORY = "city_wall";
    /** Blocks around a city that count for its level. */
    public static final int AREA = 64;
    /** Where each player was last: the kingdom and city it is in, or none. */
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final java.util.Set<UUID> SEEN = new java.util.HashSet<>();
    private static int seconds;

    /**
     * A city or a capital: the place its level and wall belong to.
     *
     * @param cityId 0 for the capital
     */
    public record Place(UUID kingdomId, long cityId, String dimension, int x, int y, int z, String name) {
        String key() {
            return kingdomId + "/" + cityId;
        }
    }

    private CityWalls() {
    }

    static void reset() {
        INSIDE.clear();
        SEEN.clear();
        LAYOUTS.clear();
        seconds = 0;
    }

    // ---------------------------------------------------------------- cities

    /**
     * Every city and capital of the world.
     */
    public static List<Place> places(MinecraftServer server) {
        List<Place> result = new ArrayList<>();
        for (var kingdom : KingdomWorldApi.kingdoms(server)) {
            RoadWork.capital(server, kingdom.id()).ifPresent(capital -> result.add(new Place(kingdom.id(), 0, capital.dimension(), capital.x(),
                    capital.y(), capital.z(), kingdom.name())));
        }
        for (City city : RoadWork.state(server).cities()) {
            result.add(new Place(city.kingdomId(), city.id(), city.dimension(), city.x(), city.y(), city.z(), city.name()));
        }
        return result;
    }

    public static Optional<Place> place(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == 0) {
            Optional<Capital> capital = RoadWork.capital(server, kingdomId);
            return capital.map(c -> new Place(kingdomId, 0, c.dimension(), c.x(), c.y(), c.z(),
                    KingdomWorldApi.kingdom(server, kingdomId).map(k -> k.name()).orElse("?")));
        }
        return RoadWork.state(server).city(cityId).filter(city -> city.kingdomId().equals(kingdomId))
                .map(city -> new Place(city.kingdomId(), city.id(), city.dimension(), city.x(), city.y(), city.z(), city.name()));
    }

    /**
     * Citizens living around a city and buildings standing around it.
     */
    public static int[] counts(MinecraftServer server, Place place) {
        int people = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(place.kingdomId())) {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            if (at != null && near(place, at)) {
                people++;
            }
        }
        int buildings = 0;
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(place.kingdomId())) {
            if (!isFortification(building) && near(place, building.anchor())) {
                buildings++;
            }
        }
        return new int[]{people, buildings};
    }

    private static boolean near(Place place, WorldPos pos) {
        return pos.dimension().equals(place.dimension()) && Math.abs(pos.x() - place.x()) <= AREA && Math.abs(pos.z() - place.z()) <= AREA;
    }

    /**
     * The level of a city (0 for the capital): a village, a town or a large city.
     */
    public static CityLevel level(MinecraftServer server, UUID kingdomId, long cityId) {
        return place(server, kingdomId, cityId).map(place -> {
            int[] counts = counts(server, place);
            return CityLevel.of(counts[0], counts[1], RoadSettings.rules());
        }).orElse(CityLevel.VILLAGE);
    }

    /**
     * The gates of the wall of a city (0 for the capital), as the middle block of each opening; empty without a wall.
     */
    public static List<WorldPos> gates(MinecraftServer server, UUID kingdomId, long cityId) {
        List<WorldPos> result = new ArrayList<>();
        RoadWork.state(server).wallOf(cityId, kingdomId).ifPresent(wall -> {
            for (RouteCell cell : wall.gateCells()) {
                result.add(new WorldPos(wall.dimension(), cell.x(), cell.y() + 1, cell.z()));
            }
        });
        return result;
    }

    // ---------------------------------------------------------------- laying out walls

    /**
     * A wall being laid out from the tab: the land of its ring is read a few chunks a tick, as the search for a road does.
     */
    private static final class Layout {
        final @Nullable UUID actor;
        final UUID kingdomId;
        final long cityId;
        final Wall.WallClass wallClass;
        final Place place;
        final int radius;
        final RoadTerrain terrain;
        int ticks;

        Layout(@Nullable UUID actor, UUID kingdomId, long cityId, Wall.WallClass wallClass, Place place, int radius, RoadTerrain terrain) {
            this.actor = actor;
            this.kingdomId = kingdomId;
            this.cityId = cityId;
            this.wallClass = wallClass;
            this.place = place;
            this.radius = radius;
            this.terrain = terrain;
        }
    }

    /** Walls being laid out from the tab, by city. */
    private static final Map<String, Layout> LAYOUTS = new LinkedHashMap<>();
    /** Ticks a layout may take before it is given up. */
    private static final int LAYOUT_TICKS = 2_400;

    /**
     * Orders the wall of a city from the tab: the ring is read over the next ticks without loading the land at once, and the
     * kingdom hears when the wall is laid out or why not.
     *
     * @param actor the player, who needs the right to run construction
     */
    public static Outcome order(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId, Wall.WallClass wallClass) {
        if (RoadWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        if (!RoadWork.mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        Optional<Place> place = place(server, kingdomId, cityId);
        if (place.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        ServerLevel level = ConstructionWork.level(server, place.get().dimension());
        if (level == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        int radius = level(server, kingdomId, cityId).wallRadius(RoadSettings.rules());
        // A later order for the same city takes the place of one still being read.
        LAYOUTS.put(place.get().key(), new Layout(actor, kingdomId, cityId, wallClass, place.get(), radius, new RoadTerrain(level)));
        return Outcome.DONE;
    }

    /**
     * Reads a few more chunks of the rings being laid out; a ring read whole becomes the wall. Called every tick.
     */
    public static void tickLayouts(MinecraftServer server) {
        if (LAYOUTS.isEmpty()) {
            return;
        }
        Map.Entry<String, Layout> next = LAYOUTS.entrySet().iterator().next();
        Layout job = next.getValue();
        ServerLevel level = ConstructionWork.level(server, job.place.dimension());
        job.terrain.allow(RoadSettings.rules().chunksPerTick());
        List<RouteCell> ring = level == null ? null : WallContour.ring(job.terrain, job.place.x(), job.place.z(), job.radius);
        if (ring == null && level != null && ++job.ticks < LAYOUT_TICKS) {
            return;
        }
        LAYOUTS.remove(next.getKey());
        Wall[] made = new Wall[1];
        Outcome outcome = level == null || ring == null ? Outcome.fail(KingdomError.NO_GROUND_FOR_WALL)
                : lay(server, job.actor, job.kingdomId, job.cityId, job.wallClass, job.place, level, ring, made);
        if (!outcome.ok()) {
            RoadWork.notify(server, job.kingdomId, Component.translatable(KEY + "wall_refused", job.place.name(),
                    KingdomActions.errorMessage(outcome.error())).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Whether the wall of a city is being laid out from the tab.
     */
    public static boolean layingOut(UUID kingdomId, long cityId) {
        return LAYOUTS.containsKey(kingdomId + "/" + cityId);
    }

    /**
     * Lays out the wall of a city around it on the lay of the land at once, with gates where roads cross it; for operators,
     * game tests and the showcase. The tab orders walls with {@link #order}, which reads the land over several ticks.
     *
     * @param actor the player, who needs the right to run construction; {@code null} for an operator
     */
    public static Outcome build(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId, Wall.WallClass wallClass, Wall[] made) {
        if (!RoadWork.mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        Optional<Place> place = place(server, kingdomId, cityId);
        if (place.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        ServerLevel level = ConstructionWork.level(server, place.get().dimension());
        if (level == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CITY);
        }
        int radius = level(server, kingdomId, cityId).wallRadius(RoadSettings.rules());
        RoadTerrain terrain = new RoadTerrain(level);
        terrain.allow(Integer.MAX_VALUE);
        List<RouteCell> ring = WallContour.ring(terrain, place.get().x(), place.get().z(), radius);
        if (ring == null) {
            return Outcome.fail(KingdomError.NO_GROUND_FOR_WALL);
        }
        return lay(server, actor, kingdomId, cityId, wallClass, place.get(), level, ring, made);
    }

    /**
     * Turns a ring read whole into the wall of the city, in place of the old one.
     */
    private static Outcome lay(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId, Wall.WallClass wallClass, Place place,
                               ServerLevel level, List<RouteCell> whole, Wall[] made) {
        if (!RoadWork.mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        // No wall on the land of another kingdom: the ring has a gap there.
        List<RouteCell> ring = new ArrayList<>(whole);
        ring.removeIf(cell -> cell.owner() != null && !cell.owner().equals(kingdomId));
        ring.replaceAll(cell -> onGround(level, cell));
        if (ring.size() < 4) {
            LOGGER.info("No wall around {}: {} blocks of the ring are on the ground, {} of them on the land of others", place.name(), whole.size(),
                    whole.size() - ring.size());
            return Outcome.fail(KingdomError.NO_GROUND_FOR_WALL);
        }
        RoadState roads = RoadWork.state(server);
        Optional<Wall> old = roads.wallOf(cityId, kingdomId);
        List<RouteCell> cells = ring;
        List<List<RouteCell>> ways = new ArrayList<>();
        for (Road road : roads.roads()) {
            if (road.dimension().equals(place.dimension())) {
                ways.add(road.cells());
            }
        }
        List<Integer> gates = WallContour.gates(cells, ways);
        Outcome outcome = RoadWork.change(server, "lay out a wall", state -> {
            old.ifPresent(wall -> forget(server, state, wall));
            made[0] = new Wall(state.nextWallId(), cityId, kingdomId, wallClass, place.dimension(), place.x(), place.z(), cells,
                    gates, KingdomWorldApi.gameTime(server));
            state.putWall(made[0]);
            return Outcome.DONE;
        });
        if (outcome.ok()) {
            LOGGER.info("Wall {} of {} laid out around {}: {} columns, {} gate(s)", made[0].id(), RoadWork.kingdomName(server, kingdomId).getString(),
                    place.name(), made[0].blocks().size(), gates.size());
            RoadWork.notify(server, kingdomId, Component.translatable(KEY + "wall_laid", place.name(), RoadTexts.gates(made[0].gates().size()))
                    .withStyle(ChatFormatting.GREEN));
            RoadSync.changed();
        }
        return outcome;
    }

    /**
     * The block under a trunk or a plant on top of a column: a wall stands on the ground and leaves trees as they are.
     */
    private static RouteCell onGround(ServerLevel level, RouteCell cell) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(cell.x(), cell.y(), cell.z());
        int lowest = Math.max(level.getMinBuildHeight(), cell.y() - 32);
        while (pos.getY() > lowest) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.canBeReplaced()) {
                break;
            }
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return pos.getY() == cell.y() ? cell : new RouteCell(cell.x(), pos.getY(), cell.z(), cell.kind(), cell.depth(), cell.owner());
    }

    /**
     * Cuts a gate where a player stands, at the nearest block of the wall.
     */
    public static Outcome gateAt(MinecraftServer server, @Nullable UUID actor, long wallId, int x, int z) {
        Optional<Wall> wall = RoadWork.state(server).wall(wallId);
        if (wall.isEmpty()) {
            return Outcome.fail(KingdomError.NO_WALL);
        }
        if (!RoadWork.mayManage(server, actor, wall.get().kingdomId())) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        int cell = wall.get().nearestCell(x, z);
        RouteCell at = wall.get().cells().get(cell);
        if (Math.abs(at.x() - x) > 6 || Math.abs(at.z() - z) > 6) {
            return Outcome.fail(KingdomError.NOT_AT_WALL);
        }
        Outcome outcome = RoadWork.change(server, "cut a gate", state -> {
            if (!wall.get().addGate(cell)) {
                return Outcome.fail(KingdomError.NOT_AT_WALL);
            }
            state.changed();
            return Outcome.DONE;
        });
        if (outcome.ok()) {
            ServerLevel level = ConstructionWork.level(server, wall.get().dimension());
            if (level != null) {
                openGate(level, wall.get(), cell);
            }
            if (wall.get().buildingId() != 0) {
                registerGate(server, wall.get(), cell);
            }
            RoadSync.changed();
        }
        return outcome;
    }

    /**
     * Takes away the blocks the wall put where a gate is now; they go to the storehouses. Blocks the wall did not place, or
     * that were changed since, stay.
     */
    private static void openGate(ServerLevel level, Wall wall, int cell) {
        for (int i = cell - Wall.GATE_HALF; i <= cell + Wall.GATE_HALF; i++) {
            int index = Math.floorMod(i, wall.cells().size());
            RouteCell at = wall.cells().get(index);
            for (int up = 1; up <= wall.wallClass().height() + 1; up++) {
                BlockPos pos = new BlockPos(at.x(), at.y() + up, at.z());
                BlockState state = level.getBlockState(pos);
                if (!wall.placedHere(pos.asLong()) || !level.isLoaded(pos)) {
                    continue;
                }
                if (!state.is(io.github.sekerator.kingdomworld.common.construction.TemplateBlocks.UNCLEARABLE)
                        && (state.is(net.minecraft.tags.BlockTags.LOGS) || isWallStone(state))) {
                    for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, pos, null)) {
                        RoadBlocks.store(level, wall.kingdomId(), pos, drop);
                    }
                    level.removeBlock(pos, false);
                }
                wall.forgetPlaced(pos.asLong());
            }
        }
    }

    private static boolean isWallStone(BlockState state) {
        return new ItemStack(state.getBlock().asItem()).is(WALL_STONE);
    }

    /**
     * Gives up a wall: what was paid and not placed goes back to the storehouses, and the wall and its gates leave the
     * register. The blocks standing in the world stay.
     */
    public static Outcome remove(MinecraftServer server, long wallId) {
        Optional<Wall> wall = RoadWork.state(server).wall(wallId);
        if (wall.isEmpty()) {
            return Outcome.fail(KingdomError.NO_WALL);
        }
        Outcome outcome = RoadWork.change(server, "give up a wall", state -> {
            forget(server, state, wall.get());
            return Outcome.DONE;
        });
        if (outcome.ok()) {
            RoadSync.changed();
        }
        return outcome;
    }

    private static void forget(MinecraftServer server, RoadState state, Wall wall) {
        giveBack(server, wall);
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        if (wall.buildingId() != 0) {
            book.removeBuilding(wall.buildingId());
        }
        for (RouteCell gate : wall.gateCells()) {
            for (Building building : book.buildingsOf(wall.kingdomId())) {
                if (building.templateId().equals(GATE_TEMPLATE) && building.anchor().dimension().equals(wall.dimension())
                        && building.anchor().x() == gate.x() && building.anchor().z() == gate.z()) {
                    book.removeBuilding(building.id());
                }
            }
        }
        state.removeWall(wall.id());
        BuildingEffects.changed();
    }

    private static void giveBack(MinecraftServer server, Wall wall) {
        ServerLevel level = ConstructionWork.level(server, wall.dimension());
        if (level == null) {
            return;
        }
        for (Road.Section section : wall.sections()) {
            if (!section.pool().isEmpty()) {
                RouteCell first = wall.cells().get(section.firstCell());
                RoadBlocks.giveBack(level, wall.kingdomId(), new BlockPos(first.x(), first.y(), first.z()), section.takePool());
            }
        }
    }

    // ---------------------------------------------------------------- building

    /**
     * Pays for the next column of a section of a wall: one item of the class for every block of it.
     */
    public static boolean payColumn(MinecraftServer server, Wall wall, Road.Section section) {
        if (section.done()) {
            return false;
        }
        Wall.Block block = wall.blocksOf(section).get(section.paid());
        TagKey<Item> tag = wall.wallClass() == Wall.WallClass.PALISADE ? WALL_WOOD : WALL_STONE;
        Map<String, Long> stock = KingdomStorage.countsById(server, wall.kingdomId());
        UUID key = UUID.nameUUIDFromBytes(("kingdomworld:wall:" + wall.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String best = null;
        long bestCount = 0;
        for (Map.Entry<String, Long> entry : stock.entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item instanceof BlockItem && new ItemStack(item).is(tag)) {
                long free = entry.getValue() - Production.reservations().reserved(wall.kingdomId(), entry.getKey(), key);
                if (free >= block.height() && free > bestCount) {
                    best = entry.getKey();
                    bestCount = free;
                }
            }
        }
        if (best == null) {
            return false;
        }
        Map<String, Integer> items = Map.of(best, block.height());
        List<KingdomStorage.Take> takes = KingdomStorage.plan(server, wall.kingdomId(), items,
                item -> Production.reservations().reserved(wall.kingdomId(), item, key), new WorldPos(wall.dimension(), block.x(), block.y(), block.z()));
        if (takes == null || KingdomStorage.takePlanned(server, takes) == null) {
            return false;
        }
        section.markPaid(items);
        return true;
    }

    /**
     * Builders of a kingdom work on its walls with what is left of their time.
     *
     * @return seconds of work left
     */
    static double work(MinecraftServer server, UUID kingdomId, double seconds) {
        RoadRules rules = RoadSettings.rules();
        double per = rules.secondsPerBlock(io.github.sekerator.kingdomworld.core.road.RoadClass.PAVED);
        for (Wall wall : RoadWork.state(server).walls()) {
            if (!wall.kingdomId().equals(kingdomId)) {
                continue;
            }
            for (Road.Section section : wall.sections()) {
                while (!section.done() && seconds >= per && payColumn(server, wall, section)) {
                    seconds -= per;
                }
                if (seconds < per) {
                    return seconds;
                }
            }
        }
        return seconds;
    }

    /**
     * Places paid columns whose place is loaded, enters finished walls into the register and looks at their integrity.
     */
    static void tick(MinecraftServer server) {
        boolean minute = ++seconds % 60 == 0;
        RoadState roads = RoadWork.state(server);
        for (Wall wall : roads.walls()) {
            ServerLevel level = ConstructionWork.level(server, wall.dimension());
            if (level == null) {
                continue;
            }
            for (Road.Section section : wall.sections()) {
                placeSection(level, wall, section, Integer.MAX_VALUE);
            }
            if (wall.built() && wall.buildingId() == 0) {
                register(server, wall);
            }
            if (minute && wall.buildingId() != 0) {
                integrity(server, level, wall);
            }
        }
    }

    /**
     * Places the paid columns of a section in order while their place is loaded.
     *
     * @return columns placed
     */
    public static int placeSection(ServerLevel level, Wall wall, Road.Section section, int budget) {
        List<Wall.Block> blocks = wall.blocksOf(section);
        int done = 0;
        while (section.placed() < section.paid() && done < budget) {
            Wall.Block block = blocks.get(section.placed());
            BlockPos base = new BlockPos(block.x(), block.y(), block.z());
            if (!level.isLoaded(base)) {
                break;
            }
            String item = section.pool().keySet().stream().findFirst().orElse(null);
            Item found = item == null ? null : PostWork.item(item);
            for (int up = 1; up <= block.height(); up++) {
                if (found == null || !section.use(item)) {
                    break;
                }
                BlockPos pos = base.above(up);
                if (RoadBlocks.free(level.getBlockState(pos)) && found instanceof BlockItem blockItem) {
                    ConstructionWork.place(level, pos, blockItem.getBlock().defaultBlockState(), found);
                    if (level.getBlockState(pos).getBlock() == blockItem.getBlock()) {
                        wall.markPlaced(pos.asLong());
                    }
                } else {
                    // Something stands there: the item goes back.
                    RoadBlocks.store(level, wall.kingdomId(), pos, new ItemStack(found));
                }
            }
            section.markPlaced();
            done++;
        }
        if (section.placedAll() && !section.pool().isEmpty()) {
            RoadBlocks.giveBack(level, wall.kingdomId(), new BlockPos(wall.centerX(), level.getSeaLevel(), wall.centerZ()), section.takePool());
        }
        if (done > 0) {
            RoadWork.state(level.getServer()).changed();
        }
        return done;
    }

    private static void register(MinecraftServer server, Wall wall) {
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        RouteCell first = wall.cells().get(0);
        long id = book.nextBuildingId();
        book.restoreCounters(0, id + 1);
        long now = KingdomWorldApi.gameTime(server);
        book.putBuilding(new Building(id, wall.kingdomId(), WALL_TEMPLATE, "fortification", CATEGORY, BuildingRoles.WALL, 1, Materials.NONE,
                new WorldPos(wall.dimension(), first.x(), first.y(), first.z()), Placement.NONE,
                new Box(first.x(), first.y() + 1, first.z(), first.x(), first.y() + wall.wallClass().height(), first.z()), 0, now, false, 100, now,
                false, new java.util.BitSet()));
        wall.setBuildingId(id);
        for (int gate : wall.gates()) {
            registerGate(server, wall, gate);
        }
        RoadWork.state(server).changed();
        BuildingEffects.changed();
        RoadWork.notify(server, wall.kingdomId(), Component.translatable(KEY + "wall_built", wall.id()).withStyle(ChatFormatting.GREEN));
        LOGGER.info("Wall {} is built and entered into the register as building {}", wall.id(), id);
    }

    /**
     * Enters a gate into the register: the opening itself, the blocks of the ring it takes, as high as the wall.
     */
    private static void registerGate(MinecraftServer server, Wall wall, int index) {
        RouteCell gate = wall.cells().get(index);
        int minX = gate.x();
        int maxX = gate.x();
        int minY = gate.y();
        int maxY = gate.y();
        int minZ = gate.z();
        int maxZ = gate.z();
        for (int i = index - Wall.GATE_HALF; i <= index + Wall.GATE_HALF; i++) {
            RouteCell cell = wall.cells().get(Math.floorMod(i, wall.cells().size()));
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        long id = book.nextBuildingId();
        book.restoreCounters(0, id + 1);
        long now = KingdomWorldApi.gameTime(server);
        book.putBuilding(new Building(id, wall.kingdomId(), GATE_TEMPLATE, "gate", CATEGORY, BuildingRoles.WALL, 1, Materials.NONE,
                new WorldPos(wall.dimension(), gate.x(), gate.y() + 1, gate.z()), Placement.NONE,
                new Box(minX, minY + 1, minZ, maxX, maxY + wall.wallClass().height(), maxZ), 0, now, false, 100, now, false, new java.util.BitSet()));
        BuildingEffects.changed();
    }

    /**
     * Whether a building of the register is a city wall or gate, kept by the roads tab.
     */
    public static boolean isFortification(Building building) {
        return CATEGORY.equals(building.category()) && (WALL_TEMPLATE.equals(building.templateId()) || GATE_TEMPLATE.equals(building.templateId()));
    }

    /**
     * The name of a city wall or gate in the register, for the tabs that list buildings.
     */
    public static Optional<Component> name(String templateId) {
        if (WALL_TEMPLATE.equals(templateId)) {
            return Optional.of(Component.translatable("kingdomworld.building.city_wall"));
        }
        if (GATE_TEMPLATE.equals(templateId)) {
            return Optional.of(Component.translatable("kingdomworld.building.city_gate"));
        }
        return Optional.empty();
    }

    /**
     * The share of the blocks of a wall standing, for the register, where its place is loaded.
     */
    public static void integrity(MinecraftServer server, ServerLevel level, Wall wall) {
        int standing = 0;
        int counted = 0;
        for (Wall.Block block : wall.blocks()) {
            BlockPos base = new BlockPos(block.x(), block.y(), block.z());
            if (!level.isLoaded(base)) {
                return;
            }
            for (int up = 1; up <= block.height(); up++) {
                counted++;
                if (!RoadBlocks.free(level.getBlockState(base.above(up)))) {
                    standing++;
                }
            }
        }
        int percent = counted == 0 ? 100 : (int) Math.round(100.0 * standing / counted);
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        book.building(wall.buildingId()).ifPresent(building -> book.putBuilding(building.withIntegrity(percent, KingdomWorldApi.gameTime(server))));
        BuildingEffects.changed();
    }

    /**
     * Places every paid column of a wall at once, loading its chunks; for operators and game tests.
     */
    public static int placeNow(MinecraftServer server, Wall wall) {
        ServerLevel level = ConstructionWork.level(server, wall.dimension());
        if (level == null) {
            return 0;
        }
        int placed = 0;
        for (Road.Section section : wall.sections()) {
            for (Wall.Block block : wall.blocksOf(section)) {
                level.getChunk(new BlockPos(block.x(), block.y(), block.z()));
            }
            placed += placeSection(level, wall, section, Integer.MAX_VALUE);
        }
        if (wall.built() && wall.buildingId() == 0) {
            register(server, wall);
        }
        return placed;
    }

    // ---------------------------------------------------------------- entering cities

    /**
     * Players who came into a city since the last look: an event for each. Called once a second.
     */
    static void watchEntries(MinecraftServer server) {
        List<Place> places = places(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof FakePlayer) && !player.isSpectator()) {
                watch(server, player, places);
            }
        }
        SEEN.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        INSIDE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /**
     * Looks where a player is now and posts a {@link CityEntryEvent} if it came into a city since the last look.
     *
     * @param places the cities and capitals of the world
     */
    public static void watch(MinecraftServer server, ServerPlayer player, List<Place> places) {
        RoadRules rules = RoadSettings.rules();
        String dimension = Dimensions.id(player.level());
        Place inside = null;
        Wall insideWall = null;
        for (Place place : places) {
            if (!place.dimension().equals(dimension)) {
                continue;
            }
            Optional<Wall> wall = RoadWork.state(server).wallOf(place.cityId(), place.kingdomId());
            boolean in = wall.map(w -> w.inside(player.getBlockX(), player.getBlockZ())).orElseGet(() -> {
                int radius = CityLevel.VILLAGE.wallRadius(rules);
                return Math.abs(player.getBlockX() - place.x()) < radius && Math.abs(player.getBlockZ() - place.z()) < radius;
            });
            if (in) {
                inside = place;
                insideWall = wall.orElse(null);
                break;
            }
        }
        String before = INSIDE.get(player.getUUID());
        // A player seen for the first time (just logged in) did not come in.
        boolean seen = !SEEN.add(player.getUUID());
        if (inside == null) {
            INSIDE.remove(player.getUUID());
            return;
        }
        String key = inside.key();
        INSIDE.put(player.getUUID(), key);
        if (seen && !key.equals(before)) {
            boolean throughGate = insideWall != null && nearGate(insideWall, player.getBlockX(), player.getBlockZ());
            MinecraftForge.EVENT_BUS.post(new CityEntryEvent(player, inside.kingdomId(), inside.cityId(), throughGate));
        }
    }

    /**
     * Whether a place is in or next to the opening of a gate of a wall.
     */
    public static boolean nearGate(Wall wall, int x, int z) {
        for (RouteCell gate : wall.gateCells()) {
            if (Math.abs(gate.x() - x) <= Wall.GATE_HALF + 2 && Math.abs(gate.z() - z) <= Wall.GATE_HALF + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Who entered where, for the self-checks of later stages: the city a player is in now, or empty.
     */
    public static Optional<String> insideOf(UUID player) {
        return Optional.ofNullable(INSIDE.get(player));
    }

    static Map<String, Integer> levels(MinecraftServer server) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Place place : places(server)) {
            result.put(place.key(), level(server, place.kingdomId(), place.cityId()).ordinal());
        }
        return result;
    }
}
