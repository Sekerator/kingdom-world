package io.github.sekerator.kingdomworld.common.flag;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.FlagBlocks;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlockEntity;
import io.github.sekerator.kingdomworld.common.block.ThroneBlock;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeRepair;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.FlagSpots;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlags;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.road.CellIndex;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The flags of kingdoms on the server: the flag a kingdom flies, the flags the kingdoms of the world are given, and the keeper of
 * the flags in the world.
 *
 * <p>A kingdom flies the flag it keeps; a kingdom of the world without one yet the flag drawn for it, and any other kingdom the
 * flag of its colour. The flags of the kingdoms of the world are drawn once, the first time the server sees such a kingdom
 * without a flag, and kept from then on: they never change, not even after a coup.
 *
 * <p>The keeper walks the flags loaded in the world, {@link #WALK_PER_TICK} a tick, and gives each the kingdom it belongs to:
 * the kingdom of the registered building whose site it stands in, the kingdom of its wall or throne, or the owner of the land
 * (the flags of a template on a construction going on). Flags of kingdoms that are no more, added flags no longer at a place
 * of their building, and flags of thrones and walls no longer at a place of theirs are taken away without a word.
 *
 * <p>A building gets a pass when it is finished, when a chunk of it loads and it was never looked at, was looked at for another
 * template, place or kingdom or lost a flag since, and when one of its flags changes its kingdom: plain banners where its
 * template has flags become flags, flags missing where its template has them come back, and the flags it gets besides are put
 * up. A pass runs only with the whole site loaded, no construction on the building and its kingdom calm; places members took
 * flags down from stay empty until the template or the kingdom of the building changes. Flags of thrones and walls are looked
 * at every {@link #PLACES_TICKS} ticks in loaded places. Nothing ever loads a chunk and nothing ever replaces a block.
 */
public final class FlagWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Flags walked in a tick. */
    private static final int WALK_PER_TICK = 64;
    /** Buildings looked at in a tick. */
    private static final int PASSES_PER_TICK = 2;
    /** Places of the world read in a tick. */
    private static final int READS_PER_TICK = 256;
    /** Blocks changed in a tick. */
    private static final int CHANGES_PER_TICK = 32;
    /** How long after a flag went without a member taking it down its building is looked at, in ticks. */
    private static final int DIRTY_DELAY = 600;
    /** How often the flags of thrones and walls are looked at, in ticks. */
    private static final int PLACES_TICKS = 100;
    /** A flag along a wall every so many of its blocks. */
    private static final int WALL_SPACING = 32;

    /** The flags loaded in the world, by dimension. */
    private static final Map<String, Set<Long>> LOADED = new HashMap<>();
    private static final ArrayDeque<Ref> WALK = new ArrayDeque<>();
    /** The flags in the walk, each once: a chunk that unloads and loads again before its flags are walked adds none twice. */
    private static final Set<Ref> QUEUED = new HashSet<>();
    /** The places of the added flags of buildings, by building, for the stamp they were worked out for. */
    private static final Map<Long, AddedPlaces> ADDED = new HashMap<>();
    /** Buildings waiting for a pass, and those that wait for a tick before it. */
    private static final LinkedHashSet<Long> PASSES = new LinkedHashSet<>();
    private static final Map<Long, Long> LATER = new HashMap<>();
    /** Chunks loaded since the last tick. */
    private static final ConcurrentLinkedQueue<ChunkRef> CHUNKS = new ConcurrentLinkedQueue<>();
    /** The registered buildings by the chunks their sites cover. */
    private static CellIndex<Building> buildings;
    private static ConstructionBook buildingsOf;
    private static long buildingsVersion = -1;
    /** The places of the flags of walls and thrones, worked out again every time they are looked at. */
    private static Map<String, Map<Long, UUID>> placeIndex;
    /** The tick of the server the places were worked out in. */
    private static int placesTick = -1;
    /** While the keeper takes a flag away itself: the flag that goes marks nothing. */
    private static boolean quiet;
    /** Blocks changed in this tick. */
    private static int changes;

    private record Ref(String dimension, long pos) {
    }

    private record ChunkRef(String dimension, int x, int z) {
    }

    private record AddedPlaces(long stamp, Set<Long> places) {
    }

    /**
     * A place of a flag of a wall or a throne.
     */
    /**
     * @param support what the flag leans on
     * @param anchor  what the place hangs on: the packed place of the throne, or the id of the wall
     */
    private record Place(ServerLevel level, BlockPos pos, BlockState state, BlockPos support, UUID kingdom, KingdomFlagBlockEntity.Origin origin,
                         long anchor) {
    }

    private FlagWork() {
    }

    public static void reset() {
        LOADED.clear();
        WALK.clear();
        QUEUED.clear();
        ADDED.clear();
        PASSES.clear();
        LATER.clear();
        CHUNKS.clear();
        buildings = null;
        buildingsOf = null;
        buildingsVersion = -1;
        placeIndex = null;
        placesTick = -1;
        quiet = false;
        FlagSpotCache.clear();
    }

    // ---------------------------------------------------------------- the flag a kingdom flies

    /**
     * The flag a kingdom flies.
     */
    public static KingdomFlag effective(MinecraftServer server, Kingdom kingdom) {
        if (kingdom.flag() != null) {
            return kingdom.flag();
        }
        if (NpcWork.isNpc(server, kingdom.id())) {
            // Drawn on the fly only while the data is read-only or before the flags of the world are given out.
            return generated(server, kingdom, kept(server, kingdom.id()));
        }
        return KingdomFlags.standard(kingdom.color());
    }

    /**
     * The flag drawn for a kingdom of the world.
     */
    static KingdomFlag generated(MinecraftServer server, Kingdom kingdom, Set<KingdomFlag> taken) {
        UUID id = kingdom.id();
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        String character = NpcWork.kingdom(server, id).map(NpcKingdom::character).map(Enum::name).orElse(null);
        return KingdomFlags.generated(seed, kingdom.color(), KingdomStyles.of(server, id), character, taken);
    }

    /**
     * The flags the other kingdoms fly without drawing any: those they keep and the flags of the colours of kingdoms of players.
     */
    private static Set<KingdomFlag> kept(MinecraftServer server, UUID except) {
        Set<KingdomFlag> taken = new HashSet<>();
        for (Kingdom other : KingdomWorldApi.state(server).kingdoms().all()) {
            if (other.id().equals(except)) {
                continue;
            }
            if (other.flag() != null) {
                taken.add(other.flag());
            } else if (!NpcWork.isNpc(server, other.id())) {
                taken.add(KingdomFlags.standard(other.color()));
            }
        }
        return taken;
    }

    /**
     * Gives every kingdom of the world without a flag the one drawn for it, all in one change, each flag unlike those already
     * flown. Nothing happens while the data is read-only.
     *
     * @return how many kingdoms got a flag
     */
    public static int fillWorldFlags(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        List<Kingdom> missing = new ArrayList<>();
        Set<KingdomFlag> taken = new HashSet<>();
        for (Kingdom kingdom : KingdomWorldApi.state(server).kingdoms().all()) {
            boolean npc = NpcWork.isNpc(server, kingdom.id());
            if (kingdom.flag() == null && npc) {
                missing.add(kingdom);
            } else {
                taken.add(kingdom.flag() != null ? kingdom.flag() : KingdomFlags.standard(kingdom.color()));
            }
        }
        if (missing.isEmpty()) {
            return 0;
        }
        Map<UUID, KingdomFlag> drawn = new LinkedHashMap<>();
        for (Kingdom kingdom : missing) {
            KingdomFlag flag = generated(server, kingdom, taken);
            drawn.put(kingdom.id(), flag);
            taken.add(flag);
        }
        KingdomWorldApi.change(server, "give the kingdoms of the world their flags", state -> {
            drawn.forEach(state::setKingdomFlag);
            return Outcome.DONE;
        });
        LOGGER.info("KingdomWorld gave {} kingdom(s) of the world their flags", drawn.size());
        return drawn.size();
    }

    // ---------------------------------------------------------------- flags loading and going

    /**
     * A flag came into the world: it is walked from now on.
     */
    public static void loaded(ServerLevel level, BlockPos pos) {
        String dimension = Dimensions.id(level);
        LOADED.computeIfAbsent(dimension, key -> new HashSet<>()).add(pos.asLong());
        Ref ref = new Ref(dimension, pos.asLong());
        if (QUEUED.add(ref)) {
            WALK.add(ref);
        }
    }

    /**
     * A flag left the world, or its chunk unloaded.
     */
    public static void unloaded(ServerLevel level, BlockPos pos) {
        Set<Long> set = LOADED.get(Dimensions.id(level));
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    /**
     * A chunk loaded: its buildings are looked at in the next tick, nothing changes during the load.
     */
    public static void chunkLoaded(ServerLevel level, ChunkPos chunk) {
        CHUNKS.add(new ChunkRef(Dimensions.id(level), chunk.x, chunk.z));
    }

    /**
     * A flag went. Unless the keeper took it away, its building is looked at again a while later: a flag knocked down by an
     * explosion, a war or a support gone comes back, one a member took down stays off.
     */
    public static void removed(ServerLevel level, BlockPos pos, KingdomFlagBlockEntity flag) {
        if (quiet) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        if (flag.origin() == KingdomFlagBlockEntity.Origin.THRONE || flag.origin() == KingdomFlagBlockEntity.Origin.WALL) {
            // The flags of thrones and walls are looked at every few seconds anyway.
            return;
        }
        Building building = buildingAt(server, Dimensions.id(level), pos);
        if (building != null) {
            FlagSavedData.get(server).markDirty(building.id());
            later(building.id(), KingdomWorldApi.gameTime(server) + DIRTY_DELAY);
        }
    }

    /**
     * A member of the kingdom of a flag takes it down: the place stays empty until the template or the kingdom of its building
     * changes.
     */
    public static void takenDown(ServerLevel level, BlockPos pos, KingdomFlagBlockEntity flag) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server) || flag.kingdom() == null) {
            return;
        }
        String dimension = Dimensions.id(level);
        if (flag.origin() == KingdomFlagBlockEntity.Origin.THRONE || flag.origin() == KingdomFlagBlockEntity.Origin.WALL) {
            // Off for this kingdom at this throne or wall: a new owner or a throne moved here puts it up again.
            long anchor = allPlaces(server).stream().filter(place -> place.level() == level && place.pos().equals(pos))
                    .mapToLong(Place::anchor).findFirst().orElse(0L);
            FlagSavedData.get(server).takenDown(dimension, pos.asLong(), flag.kingdom(), anchor);
            return;
        }
        Building building = buildingAt(server, dimension, pos);
        if (building != null) {
            FlagSavedData.get(server).takenDown(building.id(), pos.asLong());
        }
    }

    /**
     * The builders put up a flag of a template: it shows its kingdom at once.
     */
    public static void placed(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag) {
            flag.setOrigin(KingdomFlagBlockEntity.Origin.TEMPLATE);
            UUID kingdom = kingdomOf(level, pos, flag);
            flag.setKingdom(kingdom);
            flag.sendUpdate();
        }
    }

    /**
     * A construction was finished: a building built, raised or repaired gets its flags, at once when its whole site is loaded.
     */
    public static void completed(MinecraftServer server, Building building, Construction.Kind kind) {
        if (kind != Construction.Kind.BUILD && kind != Construction.Kind.UPGRADE && kind != Construction.Kind.REPAIR
                || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        long now = KingdomWorldApi.gameTime(server);
        if (!SiegeRepair.calm(server, building.kingdomId(), now)) {
            later(building.id(), now + 200);
            return;
        }
        ServerLevel level = ConstructionWork.level(server, building.anchor().dimension());
        if (level != null && loaded(level, building.box())) {
            int[] budget = {Integer.MAX_VALUE, Integer.MAX_VALUE};
            if (pass(server, level, building, budget)) {
                return;
            }
        }
        PASSES.add(building.id());
    }

    /**
     * Looks at the flags of a building at once, as a pass does, for operators and tests: only with its whole site loaded, no
     * construction on it and its kingdom calm.
     *
     * @return whether the pass ran
     */
    public static boolean lookAtNow(MinecraftServer server, long buildingId) {
        Optional<Building> building = KingdomWorldApi.state(server).construction().building(buildingId);
        if (building.isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return false;
        }
        ServerLevel level = ConstructionWork.level(server, building.get().anchor().dimension());
        if (level == null || !loaded(level, building.get().box())
                || KingdomWorldApi.state(server).construction().forBuilding(buildingId).isPresent()
                || !SiegeRepair.calm(server, building.get().kingdomId(), KingdomWorldApi.gameTime(server))) {
            return false;
        }
        int[] budget = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        boolean done = pass(server, level, building.get(), budget);
        PASSES.remove(buildingId);
        return done;
    }

    // ---------------------------------------------------------------- whose a flag is

    /**
     * The kingdom a flag belongs to: that of the registered building whose site it stands in, of its wall or throne, the owner
     * of the land, or the one it had.
     */
    @Nullable
    static UUID kingdomOf(ServerLevel level, BlockPos pos, KingdomFlagBlockEntity flag) {
        if (flag.origin() == KingdomFlagBlockEntity.Origin.WALL || flag.origin() == KingdomFlagBlockEntity.Origin.THRONE) {
            return flag.kingdom();
        }
        MinecraftServer server = level.getServer();
        Building building = buildingAt(server, Dimensions.id(level), pos);
        if (building != null) {
            return building.kingdomId();
        }
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner != null ? owner : flag.kingdom();
    }

    @Nullable
    static Building buildingAt(MinecraftServer server, String dimension, BlockPos pos) {
        for (Building building : index(server).at(dimension, pos.getX(), pos.getZ())) {
            if (building.box() != null && building.anchor().dimension().equals(dimension)
                    && building.box().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return building;
            }
        }
        return null;
    }

    private static CellIndex<Building> index(MinecraftServer server) {
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        if (buildings == null || book != buildingsOf || book.version() != buildingsVersion) {
            buildings = new CellIndex<>(4);
            for (Building building : book.buildings()) {
                Box box = building.box();
                if (box != null) {
                    buildings.add(building.anchor().dimension(), box.minX(), box.minZ(), box.maxX(), box.maxZ(), building);
                }
            }
            buildingsOf = book;
            buildingsVersion = book.version();
        }
        return buildings;
    }

    // ---------------------------------------------------------------- the tick

    public static void tick(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            CHUNKS.clear();
            return;
        }
        long now = KingdomWorldApi.gameTime(server);
        changes = 0;
        takeChunks(server);
        walk(server);
        if (!LATER.isEmpty()) {
            LATER.entrySet().removeIf(entry -> {
                if (entry.getValue() <= now) {
                    PASSES.add(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        passes(server, now);
        if (server.getTickCount() % PLACES_TICKS == 0) {
            placeIndex = null;
            if (KingdomWorldConfig.SERVER.buildingFlags.get()) {
                putUpPlaces(server, now);
            }
        }
    }

    private static void later(long building, long when) {
        LATER.merge(building, when, Math::max);
    }

    /**
     * The buildings of chunks loaded since the last tick that need a pass.
     */
    private static void takeChunks(MinecraftServer server) {
        if (CHUNKS.isEmpty()) {
            return;
        }
        FlagSavedData data = FlagSavedData.get(server);
        ChunkRef chunk;
        Set<Long> seen = new HashSet<>();
        while ((chunk = CHUNKS.poll()) != null) {
            for (Building building : index(server).at(chunk.dimension(), chunk.x() << 4, chunk.z() << 4)) {
                if (!seen.add(building.id()) || PASSES.contains(building.id())) {
                    continue;
                }
                FlagSavedData.Record record = data.building(building.id());
                if (record == null || !record.stamped() || record.stamp() != stamp(building) || record.dirty()) {
                    PASSES.add(building.id());
                }
            }
        }
    }

    /**
     * Gives the next loaded flags their kingdom, and takes away those that no longer belong where they stand.
     */
    private static void walk(MinecraftServer server) {
        int budget = Math.min(WALK_PER_TICK, WALK.size());
        for (int i = 0; i < budget && changes < CHANGES_PER_TICK; i++) {
            Ref ref = WALK.poll();
            if (ref == null) {
                return;
            }
            Set<Long> set = LOADED.get(ref.dimension());
            if (set == null || !set.contains(ref.pos())) {
                QUEUED.remove(ref);
                continue;
            }
            ServerLevel level = ConstructionWork.level(server, ref.dimension());
            BlockPos pos = BlockPos.of(ref.pos());
            if (level == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag)) {
                set.remove(ref.pos());
                QUEUED.remove(ref);
                continue;
            }
            if (check(server, level, pos, flag)) {
                WALK.add(ref);
            } else {
                QUEUED.remove(ref);
            }
        }
    }

    /**
     * Looks at one flag.
     *
     * @return whether it still stands
     */
    private static boolean check(MinecraftServer server, ServerLevel level, BlockPos pos, KingdomFlagBlockEntity flag) {
        String dimension = Dimensions.id(level);
        boolean place = flag.origin() == KingdomFlagBlockEntity.Origin.THRONE || flag.origin() == KingdomFlagBlockEntity.Origin.WALL;
        Building building = place ? null : buildingAt(server, dimension, pos);
        UUID owner = place ? placeOwner(server, dimension, pos) : null;
        UUID kingdom = owner != null ? owner : kingdomOf(level, pos, flag);
        if (kingdom == null) {
            return true;
        }
        if (KingdomWorldApi.kingdom(server, kingdom).isEmpty()) {
            takeAway(level, pos);
            return false;
        }
        // With the flags of buildings, walls and thrones switched off, those that stand stay; none are put up or put back.
        boolean kept = !KingdomWorldConfig.SERVER.buildingFlags.get();
        boolean belongs = kept || switch (flag.origin()) {
            case ADDED -> {
                if (building == null) {
                    yield false;
                }
                Set<Long> places = addedPlaces(building);
                // A building whose template changed is left as it is.
                yield places == null || places.contains(pos.asLong());
            }
            case THRONE -> owner != null || !throneLoaded(server, kingdom);
            case WALL -> owner != null;
            case TEMPLATE -> true;
        };
        if (!belongs) {
            takeAway(level, pos);
            return false;
        }
        if (flag.setKingdom(kingdom)) {
            flag.sendUpdate();
            changes++;
            if (building != null) {
                PASSES.add(building.id());
            }
        }
        return true;
    }

    /**
     * Whether the throne of a kingdom is loaded, so that the places of its flags are known.
     */
    private static boolean throneLoaded(MinecraftServer server, UUID kingdom) {
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdom).map(Territory::capital);
        if (capital.isEmpty()) {
            return true;
        }
        ServerLevel level = Dimensions.key(capital.get().dimension()).map(server::getLevel).orElse(null);
        return level != null && level.isLoaded(new BlockPos(capital.get().x(), capital.get().y(), capital.get().z()));
    }

    /**
     * Takes a flag away without a word: nothing is marked and nothing drops. Nothing leans on a flag, so its neighbours are not
     * told, as when one is put up: a neighbour told reads the blocks around it, which may lie in a chunk not loaded yet beyond the
     * edge of the loaded world.
     */
    private static void takeAway(ServerLevel level, BlockPos pos) {
        quiet = true;
        try {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } finally {
            quiet = false;
        }
        changes++;
    }

    /**
     * Puts up a flag of a kingdom.
     */
    private static void putUp(ServerLevel level, BlockPos pos, BlockState state, UUID kingdom, KingdomFlagBlockEntity.Origin origin) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag) {
            flag.setOrigin(origin);
            flag.setKingdom(kingdom);
            flag.setChanged();
            flag.sendUpdate();
        }
        changes++;
    }

    // ---------------------------------------------------------------- buildings

    /**
     * What the flags of a building were put up for: its template, its place and its kingdom.
     */
    static long stamp(Building building) {
        String text = building.templateId() + "|" + building.fingerprint() + "|" + building.materials() + "|"
                + building.anchor().dimension() + "|" + building.anchor().x() + "," + building.anchor().y() + "," + building.anchor().z()
                + "|" + building.placement().rotation() + "|" + building.placement().mirror() + "|" + building.kingdomId();
        long hash = 0xcbf29ce484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * The packed places of the world where a building gets flags besides those of its template, worked out once for each stamp
     * of the building; {@code null} while its template is not the one it was built by (a mod or a data pack changed it).
     */
    @Nullable
    private static Set<Long> addedPlaces(Building building) {
        long stamp = stamp(building);
        AddedPlaces cached = ADDED.get(building.id());
        if (cached != null && cached.stamp() == stamp) {
            return cached.places();
        }
        Optional<BuildingTemplate> template = BuildingWork.currentTemplate(building);
        if (template.isEmpty()) {
            return null;
        }
        Set<Long> result = new HashSet<>();
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        for (FlagSpots.Spot spot : FlagSpotCache.of(building, template.get()).added()) {
            result.add(template.get().place(new BlockPos(spot.x(), spot.y(), spot.z()), anchor, building.placement()).asLong());
        }
        Set<Long> places = Set.copyOf(result);
        ADDED.put(building.id(), new AddedPlaces(stamp, places));
        return places;
    }

    private static boolean loaded(ServerLevel level, @Nullable Box box) {
        if (box == null) {
            return false;
        }
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void passes(MinecraftServer server, long now) {
        if (PASSES.isEmpty()) {
            return;
        }
        int done = 0;
        Iterator<Long> queue = PASSES.iterator();
        List<Long> finished = new ArrayList<>();
        int[] budget = {READS_PER_TICK, CHANGES_PER_TICK};
        while (queue.hasNext() && done < PASSES_PER_TICK && budget[0] > 0 && budget[1] > 0 && changes < CHANGES_PER_TICK) {
            long id = queue.next();
            Optional<Building> found = KingdomWorldApi.state(server).construction().building(id);
            if (found.isEmpty()) {
                finished.add(id);
                continue;
            }
            Building building = found.get();
            ServerLevel level = ConstructionWork.level(server, building.anchor().dimension());
            if (BuildingWork.currentTemplate(building).isEmpty()) {
                // Without a template (a wall, a gate) or with one that changed there is nothing to do: the stamp keeps the loading of
                // its chunks from bringing it back.
                FlagSavedData.get(server).stamp(id, stamp(building));
                finished.add(id);
                continue;
            }
            if (level == null || !loaded(level, building.box())) {
                // An unloaded site waits for its chunks to load.
                finished.add(id);
                continue;
            }
            if (KingdomWorldApi.state(server).construction().forBuilding(id).isPresent()
                    || !SiegeRepair.calm(server, building.kingdomId(), now)) {
                // Builders at work, or a fight in the city: looked at again a while later.
                finished.add(id);
                later(id, now + 200);
                continue;
            }
            done++;
            budget[1] = Math.min(budget[1], CHANGES_PER_TICK - changes);
            if (pass(server, level, building, budget)) {
                finished.add(id);
            }
        }
        finished.forEach(PASSES::remove);
    }

    /**
     * Puts up the flags of a building.
     *
     * @param budget the places it may still read and the blocks it may still change in this tick, used up as it goes
     * @return whether the pass is done; one cut short by the budget goes on in a later tick
     */
    private static boolean pass(MinecraftServer server, ServerLevel level, Building building, int[] budget) {
        Optional<BuildingTemplate> found = BuildingWork.currentTemplate(building);
        if (found.isEmpty()) {
            return true;
        }
        BuildingTemplate template = found.get();
        FlagSavedData data = FlagSavedData.get(server);
        long stamp = stamp(building);
        FlagSavedData.Record record = data.building(building.id());
        if (record != null && record.stamped() && record.stamp() != stamp) {
            // Another template, place or kingdom: the flags members took down may fly again.
            data.clearOff(building.id());
        }
        UUID kingdom = building.kingdomId();
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        FlagSpotCache.Entry entry = FlagSpotCache.of(building, template);
        // The flags of the template.
        for (int index : entry.templateFlags()) {
            if (budget[0] <= 0 || budget[1] <= 0) {
                return false;
            }
            BuildingTemplate.Block block = template.blocks().get(index);
            BlockPos pos = template.place(block.pos(), anchor, building.placement());
            BlockState wanted = TemplateBlocks.transform(block.state(), building.placement());
            budget[0]--;
            BlockState there = level.getBlockState(pos);
            if (FlagBlocks.is(there)) {
                if (level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag && flag.setKingdom(kingdom)) {
                    flag.sendUpdate();
                }
                continue;
            }
            if (data.isOff(building.id(), pos.asLong())) {
                continue;
            }
            if (plainBanner(level, pos, there, wanted)) {
                // A plain banner where the template has a flag: the cloth of the kingdom becomes its flag, turned as it was.
                BlockState flag = FlagBlocks.onWall(wanted) ? FlagBlocks.wall(there.getValue(WallBannerBlock.FACING))
                        : FlagBlocks.standing(there.getValue(BannerBlock.ROTATION));
                putUp(level, pos, flag, kingdom, KingdomFlagBlockEntity.Origin.TEMPLATE);
                budget[1]--;
            } else if (there.isAir() && supportStands(building, level, support(pos, wanted)) && wanted.canSurvive(level, pos)) {
                putUp(level, pos, wanted, kingdom, KingdomFlagBlockEntity.Origin.TEMPLATE);
                budget[1]--;
            }
        }
        // The flags it gets besides.
        Set<Long> added = new HashSet<>();
        if (KingdomWorldConfig.SERVER.buildingFlags.get()) {
            for (FlagSpots.Spot spot : entry.added()) {
                if (budget[0] <= 0 || budget[1] <= 0) {
                    return false;
                }
                BlockPos local = new BlockPos(spot.x(), spot.y(), spot.z());
                BlockPos pos = template.place(local, anchor, building.placement());
                added.add(pos.asLong());
                BlockState wanted = TemplateBlocks.transform(FlagBlocks.of(spot), building.placement());
                budget[0] -= 3;
                BlockState there = level.getBlockState(pos);
                if (FlagBlocks.is(there)) {
                    if (level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag && flag.setKingdom(kingdom)) {
                        flag.sendUpdate();
                    }
                    continue;
                }
                if (data.isOff(building.id(), pos.asLong()) || !there.isAir()) {
                    continue;
                }
                BlockPos cloth = FlagBlocks.onWall(wanted) ? pos.below() : pos.above();
                if (!level.getBlockState(cloth).isAir() || !supportStands(building, level, support(pos, wanted)) || !wanted.canSurvive(level, pos)) {
                    continue;
                }
                putUp(level, pos, wanted, kingdom, KingdomFlagBlockEntity.Origin.ADDED);
                budget[1]--;
            }
        }
        // Added flags no longer at a place of the building go; switched off, those that stand stay.
        Box box = building.box();
        Set<Long> loadedHere = KingdomWorldConfig.SERVER.buildingFlags.get() ? LOADED.getOrDefault(building.anchor().dimension(), Set.of())
                : Set.of();
        for (long packed : List.copyOf(loadedHere)) {
            BlockPos pos = BlockPos.of(packed);
            if (box == null || !box.contains(pos.getX(), pos.getY(), pos.getZ()) || added.contains(packed)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag && flag.origin() == KingdomFlagBlockEntity.Origin.ADDED) {
                takeAway(level, pos);
                budget[1]--;
            }
        }
        data.stamp(building.id(), stamp);
        return true;
    }

    /**
     * Whether a flag added to a building still has the block of its template to hold it: a flag never hangs on what somebody
     * else put there. A flag on the ground outside the blocks of the template stands on whatever the ground is.
     */
    private static boolean supportStands(Building building, ServerLevel level, BlockPos support) {
        Optional<BlockState> wanted = BuildingWork.templateBlockAt(building, support);
        if (wanted.isEmpty()) {
            return true;
        }
        return !wanted.get().isAir() && level.getBlockState(support).getBlock() == wanted.get().getBlock();
    }

    /**
     * The block a flag leans on: the one under a standing flag, the wall behind one on a wall.
     */
    private static BlockPos support(BlockPos pos, BlockState flag) {
        return FlagBlocks.onWall(flag)
                ? pos.relative(flag.getValue(io.github.sekerator.kingdomworld.common.block.KingdomWallFlagBlock.FACING).getOpposite())
                : pos.below();
    }

    /**
     * Whether a block is a plain banner of the form a flag of the template has there: no patterns, no name.
     */
    private static boolean plainBanner(ServerLevel level, BlockPos pos, BlockState there, BlockState wanted) {
        boolean wall = FlagBlocks.onWall(wanted);
        if (wall ? !(there.getBlock() instanceof WallBannerBlock) : !(there.getBlock() instanceof BannerBlock)) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof BannerBlockEntity banner && banner.getCustomName() == null
                && banner.getPatterns().size() <= 1;
    }

    // ---------------------------------------------------------------- thrones and walls

    /**
     * The kingdom whose throne or wall a flag stands at the place of, if any. The places are those of chunks loaded when they were
     * worked out: a flag of a chunk loaded since then, not found there, has them worked out again, once a tick at most, before it is
     * taken for a flag whose wall or throne is gone.
     */
    @Nullable
    private static UUID placeOwner(MinecraftServer server, String dimension, BlockPos pos) {
        UUID owner = places(server).getOrDefault(dimension, Map.of()).get(pos.asLong());
        if (owner == null && placesTick != server.getTickCount()) {
            placeIndex = null;
            owner = places(server).getOrDefault(dimension, Map.of()).get(pos.asLong());
        }
        return owner;
    }

    /**
     * The places of the flags of thrones and walls in loaded places, by dimension.
     */
    private static Map<String, Map<Long, UUID>> places(MinecraftServer server) {
        if (placeIndex != null) {
            return placeIndex;
        }
        placesTick = server.getTickCount();
        Map<String, Map<Long, UUID>> result = new HashMap<>();
        for (Place place : allPlaces(server)) {
            result.computeIfAbsent(Dimensions.id(place.level()), key -> new HashMap<>()).put(place.pos().asLong(), place.kingdom());
        }
        placeIndex = result;
        return result;
    }

    /**
     * Every place of a flag of a throne or a wall whose chunk is loaded.
     */
    private static List<Place> allPlaces(MinecraftServer server) {
        List<Place> result = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            Capital capital = territory.capital();
            ServerLevel level = Dimensions.key(capital.dimension()).map(server::getLevel).orElse(null);
            BlockPos throne = new BlockPos(capital.x(), capital.y(), capital.z());
            if (level == null || !level.isLoaded(throne)) {
                continue;
            }
            BlockState state = level.getBlockState(throne);
            if (!(state.getBlock() instanceof ThroneBlock)) {
                continue;
            }
            Direction facing = state.getValue(ThroneBlock.FACING);
            int rotation = FlagSpots.rotation(io.github.sekerator.kingdomworld.core.construction.Facing.valueOf(facing.name()));
            for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos pos = throne.relative(side);
                if (level.isLoaded(pos)) {
                    result.add(new Place(level, pos, FlagBlocks.standing(rotation), pos.below(), territory.kingdomId(),
                            KingdomFlagBlockEntity.Origin.THRONE, throne.asLong()));
                }
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            ServerLevel level = Dimensions.key(wall.dimension()).map(server::getLevel).orElse(null);
            if (level == null) {
                continue;
            }
            for (Place place : wallPlaces(level, wall)) {
                if (level.isLoaded(place.pos())) {
                    result.add(place);
                }
            }
        }
        return result;
    }

    /**
     * The places of the flags of a wall: on the outer side of its blocks beside every gate and every {@link #WALL_SPACING} blocks
     * along it, one block below its top, only where the wall was built.
     */
    private static List<Place> wallPlaces(ServerLevel level, Wall wall) {
        List<RouteCell> cells = wall.cells();
        int size = cells.size();
        List<Integer> chosen = new ArrayList<>();
        for (int gate : wall.gates()) {
            for (int side : new int[]{-1, 1}) {
                chosen.add(Math.floorMod(gate + side * (Wall.GATE_HALF + 1), size));
            }
        }
        for (int i = 0; i < size; i += WALL_SPACING) {
            chosen.add(i);
        }
        List<Place> result = new ArrayList<>();
        List<Integer> taken = new ArrayList<>();
        for (int index : chosen) {
            if (wall.inGate(index) || taken.stream().anyMatch(other -> {
                int distance = Math.abs(other - index);
                return Math.min(distance, size - distance) < 3;
            })) {
                continue;
            }
            RouteCell cell = cells.get(index);
            int dx = cell.x() - wall.centerX();
            int dz = cell.z() - wall.centerZ();
            Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos support = new BlockPos(cell.x(), cell.y() + wall.wallClass().height() - 1, cell.z());
            if (!wall.placedHere(support.asLong())) {
                continue;
            }
            taken.add(index);
            result.add(new Place(level, support.relative(out), FlagBlocks.wall(out), support, wall.kingdomId(), KingdomFlagBlockEntity.Origin.WALL,
                    wall.id()));
        }
        return result;
    }

    /**
     * Puts up the flags of thrones and walls missing in loaded places, where nothing stands and no member took them down.
     */
    private static void putUpPlaces(MinecraftServer server, long now) {
        FlagSavedData data = FlagSavedData.get(server);
        Map<UUID, Boolean> calm = new HashMap<>();
        for (Place place : allPlaces(server)) {
            if (changes >= CHANGES_PER_TICK) {
                return;
            }
            ServerLevel level = place.level();
            String dimension = Dimensions.id(level);
            BlockPos pos = place.pos();
            BlockState there = level.getBlockState(pos);
            if (FlagBlocks.is(there)) {
                continue;
            }
            if (data.isOff(dimension, pos.asLong(), place.kingdom(), place.anchor()) || !there.isAir()
                    || !calm.computeIfAbsent(place.kingdom(), kingdom -> SiegeRepair.calm(server, kingdom, now))) {
                continue;
            }
            BlockPos cloth = place.origin() == KingdomFlagBlockEntity.Origin.WALL ? pos.below() : pos.above();
            // What a flag on a wall leans on may lie in the next chunk: it is read only when loaded, never loaded for it.
            if (!level.isLoaded(place.support()) || !level.getBlockState(cloth).isAir() || !place.state().canSurvive(level, pos)
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(pos)).isEmpty()) {
                continue;
            }
            putUp(level, pos, place.state(), place.kingdom(), place.origin());
        }
    }
}
