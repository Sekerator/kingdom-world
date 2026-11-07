package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildRegistries;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadBlocks;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadSync;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.NpcWealth;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The appearance of a kingdom of the world, in stages ({@link SpawnStage}) over ticks with a budget of blocks and chunks, one
 * kingdom at a time. The chunks it needs are kept loaded with tickets of its own (new ones are generated by the game off the
 * main thread) and let go when it is done. The place remembers the stage and the step; every step looks at the world and the
 * data first, so that a restart in the middle goes on without doing anything twice.
 */
public final class NpcSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("kingdomworld_npc", Comparator.comparingLong(ChunkPos::toLong));
    /** Half the side of the square of the throne, in blocks. */
    static final int PLAZA = 7;
    /** Half the side of the levelled square of a town, in blocks. */
    static final int TOWN_REACH = 18;
    /** Ticks the appearance waits for its chunks before it says so in the log. */
    private static final int LOAD_WARN_TICKS = 6_000;
    /** Ticks it waits for its roads and wall to stand before it lets the chunks go anyway. */
    private static final int PLACE_WAIT_TICKS = 2_400;
    /** The least radius of the walls of a village the capital is laid out for. */
    private static final int MIN_WALL_RADIUS = 24;
    /** Ticks longer than this while a kingdom appears are written to the log with their stage. */
    private static final long SLOW_TICK_NANOS = 50_000_000L;
    /** Chunks of land claimed in a tick for every chunk the rules let an appearance look through. */
    private static final int CLAIMS_PER_CHUNK_BUDGET = 8;
    /** Blocks of a building done in a tick at least, so that a small budget still moves on. */
    private static final int BLOCKS_PER_PART = 64;
    /** Blocks of the wall paid in a tick at most: the roads place the paid columns once a second, all at once. */
    private static final int WALL_BLOCKS_PER_TICK = 48;
    /** Columns of ramp around a levelled square, so that it does not end with a step a road cannot climb. */
    static final int RAMP = 6;
    /** Citizens added in a tick at most. */
    private static final int CITIZENS_PER_TICK = 4;
    /** Positions of the town plots around the town hall: x, z and where the front looks. */
    private static final int[][] TOWN_PLOTS = {{0, -12, 0}, {12, 0, 1}, {0, 12, 2}, {-12, 0, 3}, {12, -12, 0}, {-12, 12, 2}};

    private static Job job;

    /**
     * What an appearance keeps while it runs; rebuilt from the place after a restart.
     */
    private static final class Job {
        final int siteId;
        final NpcComposition composition;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Set<Long> ticketed = new LinkedHashSet<>();
        /** Chunks with a ticket that are not loaded yet. */
        final List<ChunkPos> pending = new ArrayList<>();
        int next;
        int waited;
        long ticks;
        long sumNanos;
        long maxNanos;
        SpawnStage worstStage = SpawnStage.LOAD;
        SpawnStage lastStage = SpawnStage.LOAD;
        boolean searching;
        boolean loaded;
        List<Long> land;
        int claimIndex;
        boolean claimedInPass;
        int claimed;

        Job(int siteId, NpcComposition composition) {
            this.siteId = siteId;
            this.composition = composition;
        }
    }

    /**
     * The times of the ticks of the server while the last kingdom appeared: how many, the average and the worst, in
     * milliseconds.
     */
    public record Timing(long ticks, double averageMillis, double worstMillis) {
    }

    private static Timing lastTiming = new Timing(0, 0, 0);

    private NpcSpawner() {
    }

    static void reset() {
        job = null;
    }

    /**
     * Forgets what the running appearance keeps in memory, as a restart of the server does; for game tests.
     */
    public static void resetForTests() {
        job = null;
    }

    public static Timing lastTiming() {
        return lastTiming;
    }

    /**
     * Starts the appearance of a planned place.
     */
    public static void start(MinecraftServer server, NpcSite site) {
        site.setStatus(NpcSite.Status.SPAWNING);
        job = null;
    }

    /**
     * The composition of the kingdom of a place, from its seed and the rules of the server.
     */
    public static NpcComposition composition(NpcSite site) {
        RoadRules roads = RoadSettings.rules();
        // The capital is laid out for walls of at least the default radius; a wall of a smaller one leaves out what stands in its way.
        return NpcComposition.of(site.level(), site.seed(), NpcSettings.rules(), roads.townPeople(), roads.townBuildings(), roads.cityPeople(),
                roads.cityBuildings(), Math.max(roads.wallRadius(), MIN_WALL_RADIUS));
    }

    /**
     * The id of the kingdom of a place: the same for the same world and place.
     */
    static UUID kingdomId(MinecraftServer server, NpcSite site) {
        return UUID.nameUUIDFromBytes(("kingdomworld:npc:" + server.overworld().getSeed() + ":" + site.id() + ":" + site.x() + ":" + site.z())
                .getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        Optional<NpcSite> spawning = NpcWork.state(server).spawning();
        if (spawning.isEmpty()) {
            job = null;
            return;
        }
        NpcSite site = spawning.get();
        if (job == null || job.siteId != site.id()) {
            job = new Job(site.id(), composition(site));
            job.chunks.addAll(chunks(site, job.composition));
        }
        if (job.ticks > 0) {
            long nanos = server.tickTimes[Math.floorMod(server.getTickCount() - 1, server.tickTimes.length)];
            job.sumNanos += nanos;
            if (nanos > job.maxNanos) {
                job.maxNanos = nanos;
                job.worstStage = job.lastStage;
            }
            if (nanos > SLOW_TICK_NANOS) {
                LOGGER.info("A tick of the appearance of {} took {} ms at {} step {}", site, String.format(java.util.Locale.ROOT, "%.1f",
                        nanos / 1e6), job.lastStage.id(), site.step());
            }
        }
        job.ticks++;
        job.lastStage = site.stage();
        ServerLevel level = server.overworld();
        try {
            step(server, level, site, job);
        } catch (RuntimeException e) {
            LOGGER.error("The appearance of the kingdom of the world at {} failed at {} step {}", site, site.stage().id(), site.step(), e);
            stop(level, site, "failed: " + e);
        }
    }

    /**
     * Runs the appearance of a place to its end at once, for game tests: the chunks are loaded as they are needed.
     */
    public static void runNow(MinecraftServer server, NpcSite site, int maxTicks) {
        if (site.status() != NpcSite.Status.SPAWNING) {
            start(server, site);
        }
        for (int i = 0; i < maxTicks && site.status() == NpcSite.Status.SPAWNING; i++) {
            if (job == null || job.siteId != site.id()) {
                job = new Job(site.id(), composition(site));
                job.chunks.addAll(chunks(site, job.composition));
            }
            if (!job.loaded) {
                for (ChunkPos chunk : job.chunks) {
                    server.overworld().getChunk(chunk.x, chunk.z);
                }
            }
            if (site.stage() == SpawnStage.ROADS && job.searching) {
                RoadPlanning.tickSearch(server);
            }
            if (site.stage() == SpawnStage.DONE) {
                site.kingdomId().ifPresent(id -> {
                    for (Road road : RoadWork.state(server).roads()) {
                        if (road.kingdomId().equals(id)) {
                            io.github.sekerator.kingdomworld.common.road.RoadBuilding.placeNow(server, road);
                        }
                    }
                    RoadWork.state(server).wallOf(0, id).ifPresent(wall -> CityWalls.placeNow(server, wall));
                });
            }
            step(server, server.overworld(), site, job);
        }
    }

    private static void step(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        if (site.stage() != SpawnStage.LOAD && site.stage() != SpawnStage.DONE && !loaded(level, site, job)) {
            // After a restart the chunks come back with the tickets first, so that nothing loads on the main thread.
            return;
        }
        switch (site.stage()) {
            case LOAD -> load(level, site, job);
            case CHECK -> check(server, level, site, job);
            case FOUND -> found(server, level, site, job);
            case GROUND -> ground(server, level, site, job);
            case BUILD -> build(server, level, site, job);
            case WORK -> work(server, level, site, job);
            case TOWNS -> towns(server, level, site, job);
            case PLACES -> places(server, level, site, job);
            case WEALTH -> wealth(server, level, site, job);
            case PEOPLE -> people(server, level, site, job);
            case ARMY -> army(server, site, job);
            case ROADS -> roads(server, level, site, job);
            case WALL -> wall(server, level, site, job);
            case DONE -> done(server, level, site, job);
        }
    }

    // ---------------------------------------------------------------- loading and checking

    /**
     * The chunks an appearance needs: the land of the capital with a chunk around it, every town with two chunks around it and
     * the chunks along the way to every town.
     */
    static List<ChunkPos> chunks(NpcSite site, NpcComposition composition) {
        Set<Long> keys = new LinkedHashSet<>();
        int cx = site.x() >> 4;
        int cz = site.z() >> 4;
        int r = NpcSettings.rules().landRadius(site.level()) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                keys.add(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        for (NpcSite.Town town : site.towns()) {
            int tx = town.x() >> 4;
            int tz = town.z() >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    keys.add(ChunkPos.asLong(tx + dx, tz + dz));
                }
            }
            // The way of the road wanders round hills: two chunks each side of the straight line are loaded for its search.
            for (long chunk : corridor(cx, cz, tx, tz)) {
                int x = ChunkPos.getX(chunk);
                int z = ChunkPos.getZ(chunk);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        keys.add(ChunkPos.asLong(x + dx, z + dz));
                    }
                }
            }
        }
        List<ChunkPos> list = new ArrayList<>();
        keys.forEach(key -> list.add(new ChunkPos(key)));
        return list;
    }

    /**
     * The chunks of a straight way from one chunk to another, each touching the one before by a side.
     */
    static List<Long> corridor(int fromX, int fromZ, int toX, int toZ) {
        List<Long> list = new ArrayList<>();
        int x = fromX;
        int z = fromZ;
        list.add(ChunkPos.asLong(x, z));
        while (x != toX || z != toZ) {
            int dx = Integer.signum(toX - x);
            int dz = Integer.signum(toZ - z);
            // Step along the axis that is further behind the straight line, so that the way stays close to it.
            if (dx != 0 && (dz == 0 || Math.abs((double) (x - fromX) * (toZ - fromZ)) <= Math.abs((double) (z - fromZ) * (toX - fromX)))) {
                x += dx;
            } else {
                z += dz;
            }
            list.add(ChunkPos.asLong(x, z));
        }
        return list;
    }

    private static void load(ServerLevel level, NpcSite site, Job job) {
        if (loaded(level, site, job)) {
            LOGGER.info("The chunks of {} are loaded ({} chunks, {} ticks)", site, job.chunks.size(), job.ticks);
            site.advance(SpawnStage.CHECK);
        }
    }

    /**
     * Adds the tickets of the chunks of an appearance within the budget and tells whether all of them are loaded.
     */
    private static boolean loaded(ServerLevel level, NpcSite site, Job job) {
        if (job.loaded) {
            return true;
        }
        // Tickets go out as the chunks before them come in, a few at a time, so that the server loads or generates them at its
        // own pace instead of finishing many in one tick.
        job.pending.removeIf(chunk -> level.hasChunk(chunk.x, chunk.z));
        int waiting = Math.max(1, NpcSettings.rules().chunksPerTick());
        while (job.next < job.chunks.size() && job.pending.size() < waiting) {
            ChunkPos chunk = job.chunks.get(job.next++);
            if (job.ticketed.add(chunk.toLong())) {
                level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
            }
            if (!level.hasChunk(chunk.x, chunk.z)) {
                job.pending.add(chunk);
            }
        }
        if (job.next < job.chunks.size() || !job.pending.isEmpty()) {
            job.waited++;
            if (job.waited == LOAD_WARN_TICKS) {
                LOGGER.warn("The appearance of {} is still waiting for its chunks: {} of {} asked for, {} loading", site, job.next,
                        job.chunks.size(), job.pending.size());
            }
            return false;
        }
        job.loaded = true;
        return true;
    }

    private static void check(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        NpcRules rules = NpcSettings.rules();
        UUID own = kingdomId(server, site);
        String dimension = Dimensions.id(level);
        KingdomWorldState state = KingdomWorldApi.state(server);
        int buffer = KingdomWorldApi.rules().claimBufferChunks() + 1;
        int r = rules.landRadius(site.level());
        if (state.territory().hasForeignLandWithin(dimension, ChunkKey.of(site.x() >> 4, site.z() >> 4), r + buffer, own)) {
            stop(level, site, "the land of another kingdom is near");
            return;
        }
        long limit = rules.maxInhabitedMinutes() * 1200L;
        for (ChunkPos chunk : job.chunks) {
            if (Math.abs(chunk.x - (site.x() >> 4)) > r || Math.abs(chunk.z - (site.z() >> 4)) > r) {
                continue;
            }
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            if (loaded != null && loaded.getInhabitedTime() > limit && state.kingdoms().get(own).isEmpty()) {
                stop(level, site, "players spent " + loaded.getInhabitedTime() / 1200 + " minutes in chunk " + chunk);
                return;
            }
        }
        if (site.groundY() == 0) {
            site.setGroundY(plazaHeight(level, site.x(), site.z()));
        }
        site.advance(SpawnStage.FOUND);
    }

    /**
     * The height of the square of a place: the middle of the heights of the ground around it.
     */
    static int plazaHeight(ServerLevel level, int x, int z) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -PLAZA; dx <= PLAZA; dx += PLAZA) {
            for (int dz = -PLAZA; dz <= PLAZA; dz += PLAZA) {
                heights.add(NpcGround.groundAt(level, x + dx, z + dz));
            }
        }
        heights.sort(Integer::compare);
        return Math.max(heights.get(heights.size() / 2), level.getSeaLevel());
    }

    /**
     * Leaves a place alone: the tickets go, and the place is blocked with a reason.
     */
    private static void stop(ServerLevel level, NpcSite site, String reason) {
        LOGGER.warn("The kingdom of the world at {} does not appear: {}", site, reason);
        releaseTickets(level);
        site.block(reason);
        job = null;
    }

    private static void releaseTickets(ServerLevel level) {
        if (job == null) {
            return;
        }
        for (long key : job.ticketed) {
            ChunkPos chunk = new ChunkPos(key);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
        }
        job.ticketed.clear();
        job.pending.clear();
        job.next = 0;
    }

    // ---------------------------------------------------------------- the kingdom and its land

    private static void found(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        NpcState npc = NpcWork.state(server);
        UUID id = kingdomId(server, site);
        KingdomWorldState state = KingdomWorldApi.state(server);
        BlockPos throne = new BlockPos(site.x(), site.groundY() + 1, site.z());
        if (state.kingdoms().get(id).isEmpty()) {
            Set<String> taken = new java.util.HashSet<>();
            state.kingdoms().all().forEach(kingdom -> taken.add(KingdomNames.key(kingdom.name())));
            NpcNames.Name name = NpcNames.kingdom(site.seed(), candidate -> taken.contains(KingdomNames.key(candidate)));
            level.setBlock(throne.below(), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            for (int y = 0; y < 3; y++) {
                level.setBlock(throne.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundWithId(level, throne, name.latin(), color(state, site), id);
            if (!(result instanceof CreateKingdomResult.Created)) {
                level.setBlockAndUpdate(throne, Blocks.AIR.defaultBlockState());
                stop(level, site, "the kingdom was refused: " + result);
                return;
            }
            List<NpcNames.Name> towns = new ArrayList<>();
            Set<String> cities = new java.util.HashSet<>();
            RoadWork.state(server).cities().forEach(city -> cities.add(KingdomNames.key(city.name())));
            npc.kingdoms().forEach(other -> other.towns().forEach(town -> cities.add(KingdomNames.key(town.latin()))));
            for (int i = 0; i < site.towns().size(); i++) {
                NpcNames.Name town = NpcNames.town(site.seed() + i * 7919L, candidate -> cities.contains(KingdomNames.key(candidate))
                        || taken.contains(KingdomNames.key(candidate)));
                cities.add(KingdomNames.key(town.latin()));
                towns.add(town);
            }
            NpcKingdom kingdom = NpcKingdom.create(id, site, name, towns, KingdomWorldApi.gameTime(server), TreasuryActions.day(server));
            npc.putKingdom(kingdom);
            NpcWork.remember(kingdom);
            site.setKingdomId(id);
            LOGGER.info("The kingdom of the world {} ({}) appears at {}: {} ruled by {} ({}, {})", name.latin(), name.cyrillic(),
                    throne.toShortString(), site.level().id(), kingdom.rulerName(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN),
                    kingdom.character().id(), kingdom.traits());
        } else if (npc.kingdom(id).isEmpty()) {
            // The kingdom stands but its record was lost: a kingdom of the world is never left without one.
            Kingdom existing = state.kingdoms().get(id).orElseThrow();
            NpcNames.Name name = new NpcNames.Name(existing.name(), existing.name());
            npc.putKingdom(NpcKingdom.create(id, site, name, List.of(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server)));
            site.setKingdomId(id);
        }
        if (claimLand(level, site, id, job)) {
            site.advance(SpawnStage.GROUND);
        }
    }

    /**
     * A color of the palette the fewest kingdoms have, so that the kingdom stands out on the map.
     */
    private static int color(KingdomWorldState state, NpcSite site) {
        List<Integer> palette = KingdomColors.PALETTE;
        int[] uses = new int[palette.size()];
        for (Kingdom kingdom : state.kingdoms().all()) {
            int index = palette.indexOf(kingdom.color());
            if (index >= 0) {
                uses[index]++;
            }
        }
        int start = (int) Math.floorMod(site.seed(), palette.size());
        int best = start;
        for (int i = 0; i < palette.size(); i++) {
            int index = (start + i) % palette.size();
            if (uses[index] < uses[best]) {
                best = index;
            }
        }
        return palette.get(best);
    }

    /**
     * Claims the land of the capital ring by ring, the way to every town and the land of the towns, so that it stays connected:
     * a chunk is claimed only next to land already held, so the list is gone through again until nothing more comes, a few
     * claims a tick.
     *
     * @return whether the land is all claimed
     */
    private static boolean claimLand(ServerLevel level, NpcSite site, UUID id, Job job) {
        if (job.land == null) {
            job.land = landChunks(site);
        }
        int budget = Math.max(1, NpcSettings.rules().chunksPerTick()) * CLAIMS_PER_CHUNK_BUDGET;
        while (budget > 0) {
            if (job.claimIndex >= job.land.size()) {
                if (!job.claimedInPass) {
                    LOGGER.info("{} chunks claimed for {}", job.claimed, site);
                    return true;
                }
                job.claimIndex = 0;
                job.claimedInPass = false;
            }
            long chunk = job.land.get(job.claimIndex++);
            if (claim(level, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), id)) {
                job.claimed++;
                job.claimedInPass = true;
                budget--;
            }
        }
        return false;
    }

    /**
     * The chunks of the land of a kingdom in the order they are claimed: the capital ring by ring, then for every town the way to
     * it and its square.
     */
    static List<Long> landChunks(NpcSite site) {
        Set<Long> keys = new LinkedHashSet<>();
        int cx = site.x() >> 4;
        int cz = site.z() >> 4;
        int r = NpcSettings.rules().landRadius(site.level());
        for (int ring = 1; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        keys.add(ChunkPos.asLong(cx + dx, cz + dz));
                    }
                }
            }
        }
        for (NpcSite.Town town : site.towns()) {
            int tx = town.x() >> 4;
            int tz = town.z() >> 4;
            keys.addAll(corridor(cx, cz, tx, tz));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    keys.add(ChunkPos.asLong(tx + dx, tz + dz));
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private static boolean claim(ServerLevel level, int x, int z, UUID id) {
        if (id.equals(KingdomWorldApi.ownerOfChunk(level, x, z))) {
            return false;
        }
        return KingdomWorldApi.claimChunk(level, new ChunkPos(x, z), id, null).ok();
    }

    // ---------------------------------------------------------------- the ground and the buildings of the capital

    /**
     * Levels the square of the capital row by row to the height of the square of the throne; plots and the square are levelled
     * and the rest becomes streets of trodden earth. Trees just outside are taken away.
     */
    private static void ground(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        int reach = job.composition.reach();
        int margin = RAMP;
        int rows = 2 * (reach + margin) + 1;
        int budget = NpcSettings.rules().blocksPerTick();
        NpcGround.Soil soil = NpcGround.soilAt(level, site.x(), site.z());
        List<Box> plots = plotBoxes(job.composition, site);
        while (site.step() < rows && budget > 0) {
            int z = site.z() - reach - margin + site.step();
            for (int x = site.x() - reach - margin; x <= site.x() + reach + margin; x++) {
                boolean inside = Math.abs(x - site.x()) <= reach && Math.abs(z - site.z()) <= reach;
                if (!inside) {
                    int out = Math.max(Math.abs(x - site.x()), Math.abs(z - site.z())) - reach;
                    budget -= Math.max(1, ramp(level, id, x, z, site.groundY(), out, soil));
                    continue;
                }
                BlockState top = soil.top();
                boolean plaza = Math.abs(x - site.x()) <= PLAZA && Math.abs(z - site.z()) <= PLAZA;
                if (plaza) {
                    boolean edge = Math.abs(x - site.x()) == PLAZA || Math.abs(z - site.z()) == PLAZA;
                    top = edge ? Blocks.STONE_BRICKS.defaultBlockState()
                            : (Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
                } else if (!inAny(plots, x, z) && soil == NpcGround.Soil.GRASS) {
                    top = Blocks.DIRT_PATH.defaultBlockState();
                }
                int changed = NpcGround.level(level, id, x, z, site.groundY(), top, soil.fill());
                budget -= Math.max(1, changed);
            }
            site.setStep(site.step() + 1);
        }
        if (site.step() >= rows) {
            decoratePlaza(level, site, id);
            site.advance(SpawnStage.BUILD);
        }
    }

    /**
     * A column just outside a levelled square: the ground there rises or falls at most a block for every column out, so that
     * the square ends in a slope a road can climb; trees over it go.
     *
     * @param out how many columns outside the square, from 1
     */
    private static int ramp(ServerLevel level, UUID id, int x, int z, int height, int out, NpcGround.Soil soil) {
        int ground = NpcGround.groundAt(level, x, z);
        int changed = NpcGround.clearTrees(level, id, x, z);
        if (Math.abs(ground - height) > out) {
            int target = height + Integer.signum(ground - height) * out;
            changed += Math.max(0, NpcGround.level(level, id, x, z, target, soil.top(), soil.fill()));
        }
        return changed;
    }

    private static boolean inAny(List<Box> boxes, int x, int z) {
        for (Box box : boxes) {
            if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The boxes the buildings of the capital take.
     */
    static List<Box> plotBoxes(NpcComposition composition, NpcSite site) {
        List<Box> boxes = new ArrayList<>();
        for (NpcComposition.Plot plot : composition.buildings()) {
            Optional<BuildingTemplate> template = BuildingTemplates.get(plot.template());
            if (template.isEmpty()) {
                continue;
            }
            Placement placement = new Placement(plot.facing(), false);
            BlockPos anchor = anchor(template.get(), site.x() + plot.x(), site.groundY() + 1, site.z() + plot.z(), placement);
            boxes.add(template.get().box(anchor, placement));
        }
        return boxes;
    }

    /**
     * Where to lay a template so that its footprint is centred on a point.
     */
    static BlockPos anchor(BuildingTemplate template, int centerX, int y, int centerZ, Placement placement) {
        Box box = template.box(BlockPos.ZERO, placement);
        int midX = Math.floorDiv(box.minX() + box.maxX(), 2);
        int midZ = Math.floorDiv(box.minZ() + box.maxZ(), 2);
        return new BlockPos(centerX - midX, y, centerZ - midZ);
    }

    /**
     * Lanterns on posts at the corners of the square of the throne.
     */
    private static void decoratePlaza(ServerLevel level, NpcSite site, UUID id) {
        int y = site.groundY() + 1;
        for (int[] corner : new int[][]{{-PLAZA, -PLAZA}, {PLAZA, -PLAZA}, {-PLAZA, PLAZA}, {PLAZA, PLAZA}}) {
            BlockPos base = new BlockPos(site.x() + corner[0], y, site.z() + corner[1]);
            NpcGround.set(level, id, base, Blocks.SPRUCE_FENCE.defaultBlockState());
            NpcGround.set(level, id, base.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
            NpcGround.set(level, id, base.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /**
     * Puts up the buildings of the capital a part at a time, as many blocks a tick as the budget allows.
     */
    private static void build(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        List<NpcComposition.Plot> plots = job.composition.buildings();
        int budget = NpcSettings.rules().blocksPerTick();
        Materials materials = new Materials(Materials.AUTO, "");
        while (site.step() < plots.size() && budget > 0) {
            NpcComposition.Plot plot = plots.get(site.step());
            int[] used = new int[1];
            boolean standing = buildAt(server, level, id, plot.template(), site.x() + plot.x(), site.groundY() + 1, site.z() + plot.z(),
                    new Placement(plot.facing(), false), materials, budget, used);
            budget -= Math.max(1, used[0]);
            if (standing) {
                site.setStep(site.step() + 1);
            }
        }
        if (site.step() >= plots.size()) {
            site.advance(SpawnStage.WORK);
        }
    }

    /**
     * Puts up a building a part at a time without materials (the founding), unless one of the template already stands there; a
     * construction left half done by a restart goes on.
     *
     * @param used gets the blocks done, for the budget
     * @return whether the building stands (or cannot be put up at all)
     */
    private static boolean buildAt(MinecraftServer server, ServerLevel level, UUID id, String templateId, int centerX, int y, int centerZ,
                                   Placement placement, Materials materials, int budget, int[] used) {
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        if (template.isEmpty() || !template.get().available()) {
            LOGGER.warn("The template {} for a kingdom of the world is missing or unavailable", templateId);
            return true;
        }
        BlockPos anchor = anchor(template.get(), centerX, y, centerZ, placement);
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(id)) {
            if (building.templateId().equals(templateId) && building.anchor().x() == anchor.getX() && building.anchor().z() == anchor.getZ()) {
                return true;
            }
        }
        Construction construction = null;
        for (Construction started : state.construction().of(id)) {
            if (started.templateId().equals(templateId) && started.anchor().x() == anchor.getX() && started.anchor().z() == anchor.getZ()) {
                construction = started;
            }
        }
        if (construction == null) {
            KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, id, template.get(), anchor, placement, materials);
            if (change.construction().isEmpty()) {
                LOGGER.warn("{} of a kingdom of the world was not laid out at {}: {}", templateId, anchor.toShortString(), change.outcome().error());
                return true;
            }
            construction = change.construction().get();
        }
        int part = Math.max(BLOCKS_PER_PART, budget);
        Optional<Building> built = ConstructionWork.finishSome(server, construction, part);
        used[0] = part;
        if (built.isPresent()) {
            return true;
        }
        if (construction.status() == Construction.Status.OUTSIDE_LAND) {
            LOGGER.warn("{} of a kingdom of the world could not be finished at {}", templateId, anchor.toShortString());
            ConstructionWork.cancel(server, null, id, construction.id());
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- work: the square, fields, posts

    /**
     * Places of the square of the throne: the builder's post, the carrier's post, the desk of the guild with the registrar's
     * post, the mint with the minter's post and the bank; x and z from the throne.
     */
    private static final int[][] PLAZA_PLACES = {{-5, -5}, {5, -5}, {-5, 5}, {-4, 5}, {5, 5}, {4, 5}, {0, -6}};

    private static void work(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        int y = site.groundY() + 1;
        NpcComposition composition = job.composition;
        List<int[]> outside = outsideSpots(site, composition);
        int fields = composition.fields();
        int step = site.step();
        if (step == 0) {
            int[][] p = PLAZA_PLACES;
            post(level, id, new BlockPos(site.x() + p[0][0], y, site.z() + p[0][1]), "builder", composition);
            post(level, id, new BlockPos(site.x() + p[1][0], y, site.z() + p[1][1]), "carrier", composition);
            place(level, id, new BlockPos(site.x() + p[2][0], y, site.z() + p[2][1]), GuildRegistries.GUILD_DESK.get().defaultBlockState());
            post(level, id, new BlockPos(site.x() + p[3][0], y, site.z() + p[3][1]), "registrar", composition);
            place(level, id, new BlockPos(site.x() + p[4][0], y, site.z() + p[4][1]), ModRegistries.MINT.get().defaultBlockState());
            post(level, id, new BlockPos(site.x() + p[5][0], y, site.z() + p[5][1]), "minter", composition);
            place(level, id, new BlockPos(site.x() + p[6][0], y, site.z() + p[6][1]), ModRegistries.BANK.get().defaultBlockState());
            site.setStep(1);
            return;
        }
        if (step <= fields) {
            if (step - 1 < outside.size()) {
                int[] spot = outside.get(step - 1);
                field(level, id, spot[0], spot[1]);
            }
            site.setStep(step + 1);
            return;
        }
        if (step == fields + 1) {
            if (fields < outside.size()) {
                int[] spot = outside.get(fields);
                BlockPos at = new BlockPos(spot[0], NpcGround.groundAt(level, spot[0], spot[1]) + 1, spot[1]);
                post(level, id, at, "lumberjack", composition);
                grove(level, id, at, site);
            }
            if (fields + 1 < outside.size()) {
                int[] spot = outside.get(fields + 1);
                post(level, id, new BlockPos(spot[0], NpcGround.groundAt(level, spot[0], spot[1]) + 1, spot[1]), "miner", composition);
            }
            site.setStep(step + 1);
            return;
        }
        professionsInBuildings(server, id, composition);
        site.advance(SpawnStage.TOWNS);
    }

    /**
     * Spots for fields, the lumberjack and the miner just outside the wall, on the sides away from the roads to the towns.
     */
    static List<int[]> outsideSpots(NpcSite site, NpcComposition composition) {
        RoadRules roads = RoadSettings.rules();
        int wall = switch (site.level()) {
            case SMALL -> roads.wallRadius();
            case MEDIUM -> roads.wallRadius() * 3 / 2;
            case LARGE -> roads.wallRadius() * 2;
        };
        int edge = NpcSettings.rules().landRadius(site.level()) * 16 + 8;
        int distance = Math.max(composition.reach() + 8, Math.min(wall + 12, edge - 8));
        List<double[]> directions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double nearest = Math.PI;
            for (NpcSite.Town town : site.towns()) {
                double toward = Math.atan2(town.z() - site.z(), town.x() - site.x());
                double gap = Math.abs(Math.atan2(Math.sin(angle - toward), Math.cos(angle - toward)));
                nearest = Math.min(nearest, gap);
            }
            directions.add(new double[]{angle, nearest});
        }
        directions.sort((a, b) -> Double.compare(b[1], a[1]));
        List<int[]> spots = new ArrayList<>();
        for (double[] direction : directions) {
            double angle = direction[0];
            // The wall and the land are squares: a spot lies the same number of columns out on the diagonals as on the sides.
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double scale = distance / Math.max(Math.abs(cos), Math.abs(sin));
            spots.add(new int[]{site.x() + (int) Math.round(cos * scale), site.z() + (int) Math.round(sin * scale)});
        }
        return spots;
    }

    /**
     * A field of nine by nine with water in the middle, wheat on farmland and two composters for farmers.
     */
    private static void field(ServerLevel level, UUID id, int cx, int cz) {
        int y = NpcGround.groundAt(level, cx, cz);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                NpcGround.level(level, id, cx + dx, cz + dz, y, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                if (dx == 0 && dz == 0) {
                    NpcGround.set(level, id, at, Blocks.WATER.defaultBlockState());
                    continue;
                }
                if (NpcGround.set(level, id, at, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE))) {
                    int age = Math.floorMod(cx * 31 + cz * 17 + dx * 7 + dz * 13, CropBlock.MAX_AGE + 1);
                    NpcGround.set(level, id, at.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(age));
                }
            }
        }
        place(level, id, new BlockPos(cx - 5, y + 1, cz - 5), Blocks.COMPOSTER.defaultBlockState());
        place(level, id, new BlockPos(cx + 5, y + 1, cz + 5), Blocks.COMPOSTER.defaultBlockState());
        for (int[] corner : new int[][]{{-5, 5}, {5, -5}}) {
            BlockPos base = new BlockPos(cx + corner[0], y + 1, cz + corner[1]);
            NpcGround.set(level, id, base, Blocks.OAK_FENCE.defaultBlockState());
            NpcGround.set(level, id, base.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /**
     * Saplings of the wood of the place around the lumberjack's post, so that it always has trees to fell.
     */
    private static void grove(ServerLevel level, UUID id, BlockPos post, NpcSite site) {
        Block sapling = sapling(level, post);
        for (int dx = -6; dx <= 6; dx += 4) {
            for (int dz = -6; dz <= 6; dz += 4) {
                if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
                    continue;
                }
                int x = post.getX() + dx;
                int z = post.getZ() + dz;
                int ground = NpcGround.groundAt(level, x, z);
                BlockPos at = new BlockPos(x, ground + 1, z);
                BlockState below = level.getBlockState(at.below());
                if (below.is(net.minecraft.tags.BlockTags.DIRT) && level.getBlockState(at).canBeReplaced()) {
                    NpcGround.set(level, id, at, sapling.defaultBlockState());
                }
            }
        }
    }

    private static Block sapling(ServerLevel level, BlockPos pos) {
        String wood = TemplateMaterials.woodAt(level, pos);
        if (!wood.isEmpty()) {
            Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(wood + "_sapling"));
            if (block != null && block != Blocks.AIR) {
                return block;
            }
        }
        return Blocks.OAK_SAPLING;
    }

    /**
     * Places a block of the mod as a player would (its place counts for citizens at once).
     */
    private static void place(ServerLevel level, UUID id, BlockPos pos, BlockState state) {
        if (!id.equals(KingdomWorldApi.ownerAt(level, pos)) || level.getBlockState(pos).is(state.getBlock())) {
            return;
        }
        BlockState there = level.getBlockState(pos);
        if (NpcGround.protectedAt(level, pos, there)) {
            return;
        }
        ConstructionWork.place(level, pos, state, state.getBlock().asItem());
    }

    /**
     * A post of work with its profession and as many seats as the kingdom has workers of it.
     */
    private static void post(ServerLevel level, UUID id, BlockPos pos, String profession, NpcComposition composition) {
        int seats = composition.jobs().getOrDefault(profession, 0);
        if (seats <= 0) {
            return;
        }
        place(level, id, pos, ModRegistries.WORK_POST.get().defaultBlockState());
        WorldPos at = ConstructionWork.worldPos(level, pos);
        MinecraftServer server = level.getServer();
        KingdomWorldApi.change(server, "set up a post of a kingdom of the world", state -> {
            if (state.citizens().posts().get(at).isEmpty()) {
                return Outcome.DONE;
            }
            state.setPostProfession(null, at, profession);
            state.setPostSeats(null, at, Math.min(seats, GatheringSettings.rules().maxSeats()), GatheringSettings.rules().maxSeats());
            return Outcome.DONE;
        });
    }

    /**
     * Gives the posts inside the workshops, the market and the tower of mages their professions.
     */
    private static void professionsInBuildings(MinecraftServer server, UUID id, NpcComposition composition) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<String> workshops = new ArrayList<>(List.of("crafter", "smith", "cook"));
        int merchants = composition.jobs().getOrDefault("merchant", 0);
        int mages = composition.jobs().getOrDefault("mage", 0);
        for (Building building : state.construction().buildingsOf(id)) {
            String profession = switch (building.templateId()) {
                case NpcComposition.WORKSHOP -> workshops.isEmpty() ? "" : workshops.remove(0);
                case NpcComposition.MARKET -> merchants-- > 0 ? "merchant" : "";
                case NpcComposition.MAGE_TOWER -> mages > 0 ? "mage" : "";
                default -> "";
            };
            if (profession.isEmpty() || composition.jobs().getOrDefault(profession, 0) <= 0) {
                continue;
            }
            Box box = building.box();
            for (WorkPost post : state.citizens().posts().all()) {
                WorldPos at = post.pos();
                if (post.kingdomId().equals(id) && at.x() >= box.minX() && at.x() <= box.maxX() && at.y() >= box.minY() && at.y() <= box.maxY()
                        && at.z() >= box.minZ() && at.z() <= box.maxZ()) {
                    state.setPostProfession(null, at, profession);
                    int seats = profession.equals("mage") ? mages : 1;
                    state.setPostSeats(null, at, Math.max(1, Math.min(seats, GatheringSettings.rules().maxSeats())), GatheringSettings.rules().maxSeats());
                }
            }
        }
    }

    // ---------------------------------------------------------------- towns

    /**
     * Every town in steps: its square is levelled row by row, then its town hall is founded and its houses put up.
     */
    private static void towns(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        if (site.towns().isEmpty() || job.composition.towns() == 0) {
            site.advance(SpawnStage.PLACES);
            return;
        }
        UUID id = site.kingdomId().orElseThrow();
        int index = site.step() / 1000;
        int sub = site.step() % 1000;
        if (index >= site.towns().size()) {
            site.advance(SpawnStage.PLACES);
            return;
        }
        NpcSite.Town town = site.towns().get(index);
        int rows = 2 * (TOWN_REACH + RAMP) + 1;
        int y = townHeight(level, town);
        if (sub < rows) {
            int budget = NpcSettings.rules().blocksPerTick();
            NpcGround.Soil soil = NpcGround.soilAt(level, town.x(), town.z());
            while (sub < rows && budget > 0) {
                int z = town.z() - TOWN_REACH - RAMP + sub;
                for (int x = town.x() - TOWN_REACH - RAMP; x <= town.x() + TOWN_REACH + RAMP; x++) {
                    int out = Math.max(Math.abs(x - town.x()), Math.abs(z - town.z())) - TOWN_REACH;
                    if (out > 0) {
                        budget -= Math.max(1, ramp(level, id, x, z, y, out, soil));
                        continue;
                    }
                    boolean square = Math.abs(x - town.x()) <= 3 && Math.abs(z - town.z()) <= 3;
                    BlockState top = square ? Blocks.COBBLESTONE.defaultBlockState() : soil.top();
                    budget -= Math.max(1, NpcGround.level(level, id, x, z, y, top, soil.fill()));
                }
                sub++;
            }
            site.setStep(index * 1000 + sub);
            return;
        }
        if (sub == rows) {
            BlockPos hall = new BlockPos(town.x(), y + 1, town.z());
            Optional<City> existing = RoadWork.state(server).cityAt(Dimensions.id(level), hall.getX(), hall.getY(), hall.getZ());
            if (existing.isEmpty()) {
                String name = NpcWork.kingdom(server, id).filter(k -> index < k.towns().size()).map(k -> k.towns().get(index).latin())
                        .orElse(null);
                Outcome founded = RoadWork.foundCity(level, null, id, hall, name);
                if (founded.ok()) {
                    level.setBlock(hall, RoadRegistries.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
                } else {
                    LOGGER.warn("The town {} of {} was not founded: {}", index + 1, site, founded.error());
                }
            }
            site.setStep(index * 1000 + sub + 1);
            return;
        }
        List<String> buildings = NpcComposition.townBuildings(job.composition.townHouses());
        int plot = sub - rows - 1;
        if (plot < buildings.size() && plot < TOWN_PLOTS.length) {
            String template = buildings.get(plot);
            int[] where = template.equals(NpcComposition.WELL) ? new int[]{-9, -9, 0} : TOWN_PLOTS[plot];
            int[] used = new int[1];
            if (buildAt(server, level, id, template, town.x() + where[0], y + 1, town.z() + where[1], new Placement(where[2], false),
                    new Materials(Materials.AUTO, ""), NpcSettings.rules().blocksPerTick(), used)) {
                site.setStep(index * 1000 + sub + 1);
            }
            return;
        }
        site.setStep((index + 1) * 1000);
    }

    private static int townHeight(ServerLevel level, NpcSite.Town town) {
        return plazaHeight(level, town.x(), town.z());
    }

    // ---------------------------------------------------------------- places, wealth and people

    /**
     * Looks through the chunks of the land for beds and posts the buildings brought, a few chunks a tick.
     */
    private static void places(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        List<Long> chunks = KingdomWorldApi.state(server).territory().territory(id).map(t -> List.copyOf(t.chunks())).orElse(List.of());
        int budget = NpcSettings.rules().chunksPerTick() * 4;
        while (site.step() < chunks.size() && budget-- > 0) {
            long chunk = chunks.get(site.step());
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
            if (loaded != null) {
                CitizenSites.scanChunk(level, loaded);
            }
            site.setStep(site.step() + 1);
        }
        if (site.step() >= chunks.size()) {
            site.advance(SpawnStage.WEALTH);
        }
    }

    /**
     * The founding money of the treasury, the stores and the market, all in one step.
     */
    private static void wealth(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String wood = woodOf(level, new BlockPos(site.x(), site.groundY(), site.z()));
        NpcWealth wealth = NpcWealth.of(job.composition, NpcSettings.rules(), wood + "_log", wood + "_planks",
                CitizenSettings.rules().dailyNutrition());
        long day = TreasuryActions.day(server);
        if (founded(state, id, day)) {
            // The founding was given already (the appearance runs again after a restart): nothing comes from nothing twice.
            site.advance(SpawnStage.PEOPLE);
            return;
        }
        if (wealth.money() > 0) {
            Outcome granted = state.economy().foundingGrant(id, wealth.money(), day);
            if (!granted.ok()) {
                LOGGER.warn("The founding money of {} was refused: {}", site, granted.error());
            }
        }
        WorldPos near = new WorldPos(Dimensions.id(level), site.x(), site.groundY() + 1, site.z());
        int lost = 0;
        for (Map.Entry<String, Integer> stack : wealth.stacks(64)) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(stack.getKey()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            int left = stack.getValue();
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                ItemStack rest = KingdomStorage.insert(server, id, new ItemStack(item, count), near);
                lost += rest.getCount();
                left -= count;
            }
        }
        if (lost > 0) {
            LOGGER.warn("{} item(s) of the founding stores of {} did not fit into its storehouses", lost, site);
        }
        var rules = TradeSettings.rules();
        for (String[] sell : new String[][]{{"minecraft:bread", "32"}, {wood + "_log", "64"}, {wood + "_planks", "64"},
                {"minecraft:cobblestone", "64"}, {"minecraft:wheat", "16"}, {"minecraft:arrow", "32"}}) {
            if (ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(sell[0]))) {
                try {
                    state.trade().putSell(id, new Market.SellEntry(sell[0], 0, Integer.parseInt(sell[1])), rules);
                } catch (IllegalArgumentException e) {
                    LOGGER.debug("No market entry for {}: {}", sell[0], e.getMessage());
                }
            }
        }
        state.trade().setOpenToOutsiders(id, true, rules);
        state.trade().setForeignCurrency(id, true, rules);
        LOGGER.info("{} got its founding wealth: {} in the treasury, {} kinds of goods in the storehouses, the wood of the place {}", site,
                wealth.money(), wealth.items().size(), wood);
        site.advance(SpawnStage.PEOPLE);
    }

    /**
     * Whether the treasury of a kingdom got its founding money already (the journal keeps a week).
     */
    static boolean founded(KingdomWorldState state, UUID id, long day) {
        return state.economy().treasury(id).map(treasury -> treasury.journal()
                .totals(day, io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.WEEK)
                .getOrDefault(io.github.sekerator.kingdomworld.core.economy.LedgerCategory.FOUNDING,
                        io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Totals.ZERO).income() > 0).orElse(false);
    }

    /**
     * The wood of a place, as a species id ({@code minecraft:spruce}); oak where the place has none of its own.
     */
    static String woodOf(ServerLevel level, BlockPos pos) {
        String wood = TemplateMaterials.woodAt(level, pos);
        if (wood.isEmpty() || !ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(wood + "_log"))
                || !ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(wood + "_planks"))) {
            return "minecraft:oak";
        }
        return wood;
    }

    /**
     * Citizens until the kingdom has as many as its beds, each arriving where its bed is; then they take the work of the posts
     * and the farmers their fields.
     */
    private static void people(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        KingdomWorldState state = KingdomWorldApi.state(server);
        int have = state.citizens().registry().citizensOf(id).size();
        int capital = 0;
        for (NpcComposition.Plot plot : job.composition.buildings()) {
            capital += NpcComposition.beds(plot.template());
        }
        int townBeds = 0;
        for (String building : NpcComposition.townBuildings(job.composition.townHouses())) {
            townBeds += NpcComposition.beds(building);
        }
        int want = capital + townBeds * Math.min(site.towns().size(), job.composition.towns());
        if (have < want) {
            String dimension = Dimensions.id(level);
            for (int i = 0; i < CITIZENS_PER_TICK && have < want; i++, have++) {
                WorldPos place;
                if (have < capital || site.towns().isEmpty()) {
                    place = new WorldPos(dimension, site.x(), site.groundY() + 1, site.z());
                } else {
                    int perTown = Math.max(1, (want - capital + site.towns().size() - 1) / site.towns().size());
                    NpcSite.Town town = site.towns().get(Math.min(site.towns().size() - 1, (have - capital) / perTown));
                    place = new WorldPos(dimension, town.x(), townHeight(level, town) + 1, town.z());
                }
                if (CitizenLife.arrive(server, id, place, false).isEmpty()) {
                    break;
                }
            }
            return;
        }
        // Work: the posts take the citizens without work nearest to them, the farmers find their composters.
        for (WorkPost post : state.citizens().posts().all()) {
            if (!post.kingdomId().equals(id) || !post.hasProfession()) {
                continue;
            }
            while (post.freeSeats() > 0 && state.hire(null, post.pos()).outcome().ok()) {
                // One citizen after another.
            }
        }
        int farmers = job.composition.jobs().getOrDefault("farmer", 0);
        int already = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            if (citizen.professionId().equals(Professions.FARMER.id())) {
                already++;
            }
        }
        int soldiers = job.composition.soldiers();
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(id))) {
            if (already >= farmers) {
                break;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id()) && unemployed(state, id) > soldiers) {
                if (state.setProfession(null, citizen.id(), Professions.FARMER.id()).ok()) {
                    CitizenActions.afterProfessionChange(server, citizen);
                    already++;
                }
            }
        }
        site.advance(SpawnStage.ARMY);
    }

    private static int unemployed(KingdomWorldState state, UUID id) {
        int count = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Soldiers from the citizens without work, warriors and archers by turns with the gear of the storehouses; a squad guards
     * the capital and, with four soldiers or more, a second one patrols inside its wall.
     */
    private static void army(MinecraftServer server, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (ArmyWork.isReadOnly(server)) {
            site.advance(SpawnStage.ROADS);
            return;
        }
        ArmyState army = ArmyWork.state(server);
        int want = job.composition.soldiers();
        int have = army.soldiersOf(id).size();
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(id))) {
            if (have >= want) {
                break;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                String profession = have % 2 == 0 ? Professions.WARRIOR.id() : Professions.ARCHER.id();
                if (ArmyWork.recruit(server, null, id, citizen.id(), profession).ok()) {
                    have++;
                }
            }
        }
        if (army.squadsOf(id).isEmpty() && have > 0) {
            List<UUID> soldiers = new ArrayList<>();
            for (Soldier soldier : army.soldiersOf(id)) {
                soldiers.add(soldier.citizenId());
            }
            String dimension = Dimensions.id(server.overworld());
            WorldPos center = new WorldPos(dimension, site.x(), site.groundY() + 1, site.z());
            int maxSize = ArmySettings.rules().maxSquadSize();
            List<UUID> guards = new ArrayList<>(soldiers.subList(0, Math.min(maxSize, soldiers.size() >= 4 ? (soldiers.size() + 1) / 2
                    : soldiers.size())));
            List<UUID> patrol = new ArrayList<>(soldiers.subList(guards.size(), Math.min(soldiers.size(), guards.size() + maxSize)));
            int reach = job.composition.reach();
            // The radius of a guarded area is counted in chunks.
            formAndOrder(server, id, guards, Order.guard(center, Math.min(Order.MAX_RADIUS, Math.max(1, (reach + 15) / 16))));
            if (!patrol.isEmpty()) {
                List<WorldPos> route = new ArrayList<>();
                int r = Math.max(10, reach - 2);
                for (int[] corner : new int[][]{{-r, -r}, {r, -r}, {r, r}, {-r, r}}) {
                    route.add(new WorldPos(dimension, site.x() + corner[0], site.groundY() + 1, site.z() + corner[1]));
                }
                formAndOrder(server, id, patrol, Order.patrol(route));
            }
        }
        site.advance(SpawnStage.ROADS);
    }

    private static void formAndOrder(MinecraftServer server, UUID id, List<UUID> members, Order order) {
        ArmyState.Formed formed = ArmyWork.state(server).formSquad(null, id, members, ArmySettings.rules(), KingdomWorldApi.state(server));
        if (!formed.outcome().ok()) {
            LOGGER.warn("A squad of a kingdom of the world was not formed: {}", formed.outcome().error());
            return;
        }
        ArmySquad squad = formed.squad().orElseThrow();
        Outcome ordered = ArmyWork.order(server, null, squad.id(), order);
        if (!ordered.ok()) {
            LOGGER.warn("A squad of a kingdom of the world got no order: {}", ordered.error());
        }
    }

    // ---------------------------------------------------------------- roads and the wall

    /**
     * A road from the capital to every town in turn: the way is searched over ticks, then every block of it is paid for (part
     * of the founding, like the buildings) and the roads place them as their chunks are loaded.
     */
    private static void roads(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        int index = site.step();
        if (index >= site.towns().size() || RoadWork.isReadOnly(server)) {
            site.advance(SpawnStage.WALL);
            return;
        }
        NpcSite.Town town = site.towns().get(index);
        Optional<Road> road = roadTo(server, id, town);
        if (road.isPresent()) {
            payRoad(server, level, road.get());
            job.searching = false;
            site.setStep(index + 1);
            return;
        }
        if (job.searching) {
            if (!RoadPlanning.searching(id)) {
                job.searching = false;
                if (roadTo(server, id, town).isEmpty()) {
                    LOGGER.warn("No way was found from {} to its town {}", site, index + 1);
                    site.setStep(index + 1);
                }
            }
            return;
        }
        if (RoadPlanning.searching(id)) {
            return;
        }
        RoadClass roadClass = RoadClass.byId(job.composition.roadClass()).orElse(RoadClass.PATH);
        Outcome started = RoadPlanning.startAndBuild(level, id, site.x(), site.z(), town.x(), town.z(), roadClass, Road.Mode.OWN);
        if (started.ok()) {
            job.searching = true;
        } else {
            LOGGER.warn("The road from {} to its town {} was not started: {}", site, index + 1, started.error());
            site.setStep(index + 1);
        }
    }

    private static Optional<Road> roadTo(MinecraftServer server, UUID id, NpcSite.Town town) {
        for (Road road : RoadWork.state(server).roads()) {
            if (!road.kingdomId().equals(id)) {
                continue;
            }
            for (Endpoint end : List.of(road.from(), road.to())) {
                if (Math.abs(end.x() - town.x()) <= RoadPlanning.SNAP && Math.abs(end.z() - town.z()) <= RoadPlanning.SNAP) {
                    return Optional.of(road);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Pays for every block of a road without taking anything from the storehouses: the road is part of the founding.
     */
    private static void payRoad(MinecraftServer server, ServerLevel level, Road road) {
        String wood = woodOf(level, new BlockPos(road.from().x(), 64, road.from().z()));
        RoadWork.change(server, "pay the road of a kingdom of the world", roads -> {
            for (Road.Section section : road.sections()) {
                while (!section.done()) {
                    RoadLayout.Block block = road.blocksOf(section).get(section.paid());
                    Map<String, Integer> items = new java.util.LinkedHashMap<>();
                    for (RoadLayout.Material material : block.needs(road.roadClass())) {
                        String item = material(material, road.roadClass(), wood);
                        if (item != null) {
                            items.merge(item, 1, Integer::sum);
                        }
                    }
                    section.markPaid(items);
                }
            }
            return Outcome.DONE;
        });
        RoadSync.changed();
    }

    private static String material(RoadLayout.Material material, RoadClass roadClass, String wood) {
        List<String> candidates = switch (material) {
            case SURFACE -> List.of("minecraft:gravel", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone");
            case BRIDGE -> List.of(wood + "_planks", "minecraft:oak_planks", "minecraft:spruce_planks");
            case FILL -> List.of("minecraft:dirt", "minecraft:cobblestone");
        };
        for (String candidate : candidates) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(candidate));
            if (item != null && item != net.minecraft.world.item.Items.AIR && RoadBlocks.fits(item, material, roadClass)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The wall of the capital: laid out on the land, then paid column by column within the budget of blocks (part of the
     * founding); the roads of the wall place the columns as their chunks are loaded.
     */
    private static void wall(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        if (RoadWork.isReadOnly(server)) {
            site.advance(SpawnStage.DONE);
            return;
        }
        Optional<Wall> existing = RoadWork.state(server).wallOf(0, id);
        if (existing.isEmpty()) {
            Wall[] made = new Wall[1];
            Wall.WallClass wallClass = job.composition.stoneWall() ? Wall.WallClass.STONE : Wall.WallClass.PALISADE;
            Outcome built = CityWalls.build(server, null, id, 0, wallClass, made);
            if (!built.ok() || made[0] == null) {
                LOGGER.warn("The wall of {} was refused: {}", site, built.error());
                site.advance(SpawnStage.DONE);
                return;
            }
            existing = Optional.of(made[0]);
        }
        Wall wall = existing.get();
        String item = wall.wallClass() == Wall.WallClass.PALISADE ? woodOf(level, new BlockPos(site.x(), site.groundY(), site.z())) + "_log"
                : "minecraft:stone_bricks";
        Item wanted = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(item));
        if (!(wanted instanceof BlockItem) || !new ItemStack(wanted).is(wall.wallClass() == Wall.WallClass.PALISADE ? CityWalls.WALL_WOOD
                : CityWalls.WALL_STONE)) {
            item = wall.wallClass() == Wall.WallClass.PALISADE ? "minecraft:oak_log" : "minecraft:cobblestone";
        }
        int budget = Math.min(WALL_BLOCKS_PER_TICK, NpcSettings.rules().blocksPerTick());
        String paid = item;
        RoadWork.change(server, "pay the wall of a kingdom of the world", roads -> {
            int left = budget;
            for (Road.Section section : wall.sections()) {
                while (!section.done() && left > 0) {
                    Wall.Block block = wall.blocksOf(section).get(section.paid());
                    section.markPaid(Map.of(paid, block.height()));
                    left -= block.height();
                }
                if (left <= 0) {
                    break;
                }
            }
            return Outcome.DONE;
        });
        if (wall.allPaid()) {
            RoadSync.changed();
            site.advance(SpawnStage.DONE);
        }
    }

    /**
     * Waits until the roads and the wall stand (or a while), lets the chunks go and says how long the ticks were.
     */
    private static void done(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        boolean standing = true;
        for (Road road : RoadWork.state(server).roads()) {
            if (road.kingdomId().equals(id)) {
                for (Road.Section section : road.sections()) {
                    standing &= section.placed() >= section.paid();
                }
            }
        }
        Optional<Wall> wall = RoadWork.state(server).wallOf(0, id);
        standing &= wall.isEmpty() || wall.get().built();
        if (!standing && site.step() < PLACE_WAIT_TICKS) {
            site.setStep(site.step() + 1);
            return;
        }
        releaseTickets(level);
        site.setStatus(NpcSite.Status.SPAWNED);
        double average = job.ticks > 1 ? job.sumNanos / 1e6 / (job.ticks - 1) : 0;
        lastTiming = new Timing(job.ticks, average, job.maxNanos / 1e6);
        KingdomWorldState state = KingdomWorldApi.state(server);
        LOGGER.info("The kingdom of the world {} appeared ({}): {} buildings, {} citizens, {} soldiers, {} town(s); {} ticks, average tick "
                        + "{} ms, worst tick {} ms (at {})", state.kingdoms().get(id).map(Kingdom::name).orElse("?"), site.level().id(),
                state.construction().buildingsOf(id).size(), state.citizens().registry().citizensOf(id).size(),
                ArmyWork.state(server).soldiersOf(id).size(), RoadWork.state(server).citiesOf(id).size(), job.ticks,
                String.format(java.util.Locale.ROOT, "%.2f", average), String.format(java.util.Locale.ROOT, "%.2f", job.maxNanos / 1e6),
                job.worstStage.id());
        NpcSpawner.job = null;
    }

    /**
     * Where the throne of a place stands once the place was looked at.
     */
    public static BlockPos thronePos(NpcSite site) {
        return new BlockPos(site.x(), site.groundY() + 1, site.z());
    }

    /**
     * Lets the chunks of an appearance go, for operators removing a place that is appearing.
     */
    static void abandon(MinecraftServer server, NpcSite site) {
        if (job != null && job.siteId == site.id()) {
            releaseTickets(server.overworld());
            job = null;
        }
    }

}
