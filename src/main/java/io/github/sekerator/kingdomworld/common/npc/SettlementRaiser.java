package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.GuildHouses;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.NpcWealth;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.SettlementStage;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Raises the settlements of the states in the world, one at a time and never while a capital rises: its chunks are held
 * loaded by a ticket of its own (its land and a ring of a chunk), its ground is levelled, its town hall and buildings go up in
 * the style of its kingdom, its fields are sown, its beds and posts are found, it gets its stores once and its named citizens
 * arrive. The settlement keeps its stage and the step within it; every step looks at the world and the data first, so that a
 * restart in the middle goes on without doing anything twice. A player coming near raises it with the full budget; the
 * background raises the larger ones near players with the small one.
 */
public final class SettlementRaiser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<Integer> TICKET = TicketType.create("kingdomworld_settlement", Integer::compare);
    /** Citizens arriving in a tick at most. */
    static final int CITIZENS_PER_TICK = 4;
    /** The time a step of the full budget aims at. */
    private static final long STEP_NANOS = 8_000_000L;
    /** The time a step of the background aims at. */
    private static final long BACKGROUND_NANOS = 2_500_000L;
    /** Blocks a step may always place. */
    private static final int MIN_BLOCKS = 64;
    /** Ticks between two chunks let go of. */
    private static final int RELEASE_EVERY = 2;

    private static Job job;
    private static boolean startBackground = true;
    private static final java.util.ArrayDeque<long[]> RELEASING = new java.util.ArrayDeque<>();

    /**
     * What a raising keeps while it runs; rebuilt from the settlement after a restart.
     */
    private static final class Job {
        final int id;
        final NpcSite site;
        final SettlementLayouts.Layout layout;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Set<Long> ticketed = new LinkedHashSet<>();
        final List<ChunkPos> pending = new ArrayList<>();
        int next;
        boolean loaded;
        boolean background;
        int hireIndex;
        double scale = 0.25;
        long ticks;
        long maxNanos;
        long sumNanos;
        long startedTick;

        Job(Settlement settlement, NpcSite site, SettlementLayouts.Layout layout, long startedTick) {
            this.id = settlement.id();
            this.site = site;
            this.layout = layout;
            this.startedTick = startedTick;
            for (long[] chunk : SettlementLayouts.square(settlement.x(), settlement.z(), SettlementLayouts.ticketSide(settlement.tier()))) {
                chunks.add(new ChunkPos((int) chunk[0], (int) chunk[1]));
            }
        }
    }

    private SettlementRaiser() {
    }

    static void reset() {
        job = null;
        startBackground = true;
        RELEASING.clear();
    }

    /**
     * Forgets what the running raising keeps in memory, as a restart of the server does; for game tests.
     */
    public static void resetForTests() {
        job = null;
    }

    /**
     * The settlement being raised, if any.
     */
    public static Optional<Settlement> raising(NpcState state) {
        for (Settlement settlement : state.settlements()) {
            if (settlement.status() == Settlement.Status.RAISING) {
                return Optional.of(settlement);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a settlement still waits to be raised: data whose rank rises in the world.
     */
    public static boolean waiting(Settlement settlement) {
        return settlement.status() == Settlement.Status.DATA && settlement.tier() != Tier.CAPITAL;
    }

    /**
     * Starts raising a settlement; another one being raised is put aside and keeps its stage.
     *
     * @param background whether it rises with the small budget of the background
     */
    public static void start(MinecraftServer server, Settlement settlement, boolean background) {
        NpcState state = NpcWork.state(server);
        raising(state).filter(other -> other.id() != settlement.id()).ifPresent(other -> putAside(server, other));
        if (job != null && job.id != settlement.id()) {
            release(server.overworld());
            job = null;
        }
        settlement.setStatus(Settlement.Status.RAISING);
        startBackground = background;
        if (job != null) {
            job.background = background;
        }
    }

    /**
     * Puts the raising of a settlement aside for another place: it keeps its stage and step, lets its chunks go and goes on
     * later from there.
     */
    public static void putAside(MinecraftServer server, Settlement settlement) {
        if (job != null && job.id == settlement.id()) {
            release(server.overworld());
            job = null;
        }
        if (settlement.status() == Settlement.Status.RAISING) {
            settlement.setStatus(Settlement.Status.DATA);
        }
    }

    public static boolean inBackground() {
        return job == null ? startBackground : job.background;
    }

    static void setBackground(boolean value) {
        startBackground = value;
        if (job != null) {
            job.background = value;
        }
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        releaseSome(level);
        NpcState state = NpcWork.state(server);
        Optional<Settlement> raising = raising(state);
        if (raising.isEmpty()) {
            if (job != null) {
                release(level);
                job = null;
            }
            return;
        }
        Settlement settlement = raising.get();
        if (state.spawning().isPresent()) {
            // One place rises in the world at a time: a capital goes first.
            putAside(server, settlement);
            return;
        }
        if (io.github.sekerator.kingdomworld.common.siege.SiegeWork.handingOver(server).test(settlement)) {
            // Its land changes hands by the journal of conquests: it rises no more for the kingdom that loses it.
            putAside(server, settlement);
            return;
        }
        if (job == null || job.id != settlement.id()) {
            if (job != null) {
                release(level);
            }
            job = newJob(server, settlement);
            if (job == null) {
                return;
            }
            job.background = startBackground;
        }
        if (job.background && NpcBackground.waiting(server)) {
            return;
        }
        long started = System.nanoTime();
        try {
            step(server, level, settlement, job);
        } catch (RuntimeException e) {
            LOGGER.error("The raising of {} failed at {} step {}", settlement, settlement.stage(), settlement.step(), e);
            block(server, settlement, "failed: " + e);
            return;
        }
        long nanos = System.nanoTime() - started;
        if (job != null) {
            job.ticks++;
            job.sumNanos += nanos;
            job.maxNanos = Math.max(job.maxNanos, nanos);
            long aim = job.background ? BACKGROUND_NANOS : STEP_NANOS;
            if (nanos > aim) {
                job.scale = Math.max(0.01, job.scale * Math.max(0.1, 0.5 * aim / (double) nanos));
            } else if (nanos < aim / 2) {
                job.scale = Math.min(0.5, job.scale * 1.25);
            }
        }
    }

    /**
     * Raises a settlement to its end at once, for game tests and operators: its chunks are loaded as they are needed.
     */
    public static void runNow(MinecraftServer server, Settlement settlement, int maxTicks) {
        if (settlement.status() != Settlement.Status.RAISING) {
            start(server, settlement, false);
        }
        for (int i = 0; i < maxTicks && settlement.status() == Settlement.Status.RAISING; i++) {
            if (job == null || job.id != settlement.id()) {
                job = newJob(server, settlement);
                if (job == null) {
                    return;
                }
            }
            if (!job.loaded) {
                for (ChunkPos chunk : job.chunks) {
                    server.overworld().getChunk(chunk.x, chunk.z);
                }
            }
            job.background = false;
            job.scale = 1.0;
            step(server, server.overworld(), settlement, job);
        }
    }

    private static Job newJob(MinecraftServer server, Settlement settlement) {
        NpcState state = NpcWork.state(server);
        Optional<NpcSite> site = state.kingdom(settlement.kingdomId()).map(NpcKingdom::siteId).flatMap(state::site);
        if (site.isEmpty() || KingdomWorldApi.kingdom(server, settlement.kingdomId()).isEmpty()) {
            block(server, settlement, "its kingdom is gone");
            return null;
        }
        try {
            SettlementLayouts.Layout layout = layout(settlement, site.get());
            return new Job(settlement, site.get(), layout, server.getTickCount());
        } catch (RuntimeException e) {
            LOGGER.error("The raising of {} could not be prepared", settlement, e);
            block(server, settlement, "failed: " + e);
            return null;
        }
    }

    /**
     * The layout of a settlement in the style of its kingdom.
     */
    public static SettlementLayouts.Layout layout(Settlement settlement, NpcSite site) {
        return SettlementLayouts.of(settlement.tier(), site.level(), site.styled(), site.style(), settlement.seed(), GuildHouses.of(site),
                NpcSettings.rules());
    }

    private static int blocks(Job job) {
        int configured = job.background ? Math.min(NpcSettings.build().backgroundBlocksPerTick(), NpcSettings.rules().blocksPerTick())
                : NpcSettings.rules().blocksPerTick();
        return Math.max(MIN_BLOCKS, (int) Math.round(configured * job.scale));
    }

    private static int chunkBudget(Job job) {
        return job.background ? Math.min(NpcSettings.build().backgroundChunksPerTick(), NpcSettings.rules().chunksPerTick())
                : NpcSettings.rules().chunksPerTick();
    }

    private static long deadline(Job job) {
        return System.nanoTime() + (job.background ? BACKGROUND_NANOS : STEP_NANOS);
    }

    private static void advance(Settlement settlement, SettlementStage stage) {
        settlement.setStage(stage.id(), 0);
    }

    private static void step(MinecraftServer server, ServerLevel level, Settlement settlement, Job job) {
        SettlementStage stage = SettlementStage.byId(settlement.stage());
        if (stage != SettlementStage.LOAD && stage != SettlementStage.DONE && !loaded(level, settlement, job)) {
            // After a restart the chunks come back with the tickets first, so that nothing loads on the main thread.
            return;
        }
        switch (stage) {
            case LOAD -> {
                if (loaded(level, settlement, job)) {
                    advance(settlement, SettlementStage.CHECK);
                }
            }
            case CHECK -> check(server, level, settlement, job);
            case GROUND -> ground(level, settlement, job);
            case BUILD -> build(server, level, settlement, job);
            case WORK -> work(level, settlement, job);
            case PLACES -> places(level, settlement, job);
            case WEALTH -> wealth(server, settlement, job);
            case PEOPLE -> people(server, level, settlement, job);
            case GUARDS -> SettlementGarrison.guards(server, settlement, job.layout, deadline(job));
            case ROADS -> roads(server, settlement);
            case WALL -> SettlementGarrison.wall(server, level, settlement, job.site, job.layout);
            case LAW -> SettlementGarrison.law(server, settlement, job.site, job.layout);
            case DONE -> done(server, level, settlement, job);
        }
    }

    // ---------------------------------------------------------------- loading and checking

    private static boolean loaded(ServerLevel level, Settlement settlement, Job job) {
        if (job.loaded) {
            return true;
        }
        job.pending.removeIf(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        int waiting = Math.max(1, chunkBudget(job));
        while (job.next < job.chunks.size() && job.pending.size() < waiting) {
            ChunkPos chunk = job.chunks.get(job.next++);
            if (job.ticketed.add(chunk.toLong())) {
                level.getChunkSource().addRegionTicket(TICKET, chunk, 0, settlement.id());
            }
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                job.pending.add(chunk);
            }
        }
        if (job.next < job.chunks.size() || !job.pending.isEmpty()) {
            return false;
        }
        job.loaded = true;
        return true;
    }

    /**
     * Lets the chunks of the running raising go, a few a tick.
     */
    private static void release(ServerLevel level) {
        if (job == null) {
            return;
        }
        for (long key : job.ticketed) {
            RELEASING.add(new long[]{key, job.id});
        }
        job.ticketed.clear();
        job.pending.clear();
        job.next = 0;
        job.loaded = false;
    }

    private static void releaseSome(ServerLevel level) {
        if (level.getServer().getTickCount() % RELEASE_EVERY == 0 && !RELEASING.isEmpty()) {
            long[] chunk = RELEASING.poll();
            ChunkPos pos = new ChunkPos(chunk[0]);
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, (int) chunk[1]);
        }
    }

    /**
     * Chunks of the square of a settlement that hold its tickets now; for game tests.
     */
    public static int ticketsHeld() {
        return job == null ? 0 : job.ticketed.size();
    }

    /**
     * Why the land of a settlement cannot take it: another kingdom holds a chunk of it, players spent too long in one, or
     * something that may not be broken stands there; {@code null} if it can.
     */
    static String problem(ServerLevel level, Settlement settlement) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(level);
        long limit = NpcSettings.rules().maxInhabitedMinutes() * 1200L;
        for (long[] chunk : SettlementLayouts.square(settlement.x(), settlement.z(), SettlementLayouts.land(settlement.tier()))) {
            int x = (int) chunk[0];
            int z = (int) chunk[1];
            UUID owner = state.territory().ownerOf(dimension, ChunkKey.of(x, z));
            if (owner != null && !owner.equals(settlement.kingdomId())) {
                return "the land of another kingdom at chunk [" + x + ", " + z + "]";
            }
            LevelChunk loaded = level.getChunkSource().getChunkNow(x, z);
            if (loaded == null) {
                continue;
            }
            if (loaded.getInhabitedTime() > limit) {
                return "players spent " + loaded.getInhabitedTime() / 1200 + " minutes in chunk [" + x + ", " + z + "]";
            }
            int ground = NpcGround.groundAt(level, (x << 4) + 8, (z << 4) + 8);
            BlockState there = level.getBlockState(new BlockPos((x << 4) + 8, ground, (z << 4) + 8));
            if (NpcGround.protectedAt(level, new BlockPos((x << 4) + 8, ground, (z << 4) + 8), there)) {
                return "what may not be broken stands in chunk [" + x + ", " + z + "]";
            }
        }
        return null;
    }

    private static void check(MinecraftServer server, ServerLevel level, Settlement settlement, Job job) {
        String problem = problem(level, settlement);
        if (problem != null) {
            if (!settlement.given(Settlement.GIVEN_MOVED)) {
                // The place moves once by one to three chunks, the way its seed says, onto land that suits it.
                int[] moved = moved(level, settlement);
                if (moved != null) {
                    LOGGER.info("{} moves from {} {} to {} {}: {}", settlement, settlement.x(), settlement.z(), moved[0], moved[1], problem);
                    settlement.give(Settlement.GIVEN_MOVED);
                    release(level);
                    SettlementRaiser.job = null;
                    settlement.moveTo(moved[0], moved[1]);
                    advance(settlement, SettlementStage.LOAD);
                    return;
                }
            }
            block(server, settlement, problem);
            return;
        }
        if (settlement.groundY() == 0) {
            int reach = job.layout.reach();
            settlement.setGroundY(settlement.tier() == Tier.HAMLET ? NpcSpawner.plazaHeight(level, settlement.x(), settlement.z())
                    : NpcSpawner.wideHeight(level, settlement.x(), settlement.z(), Math.min(reach, 48)));
        }
        advance(settlement, SettlementStage.GROUND);
    }

    /**
     * Another place for a settlement a few chunks away whose loaded land suits it, in the province of its own place, or null.
     */
    private static int[] moved(ServerLevel level, Settlement settlement) {
        int start = (int) Math.floorMod(settlement.seed(), 8L);
        io.github.sekerator.kingdomworld.core.territory.TerritoryIndex territory = KingdomWorldApi.state(level.getServer()).territory();
        String dimension = Dimensions.id(level);
        int province = territory.provinceAt(dimension, ChunkKey.of(settlement.x() >> 4, settlement.z() >> 4));
        for (int distance = 1; distance <= 3; distance++) {
            for (int i = 0; i < 8; i++) {
                double angle = (start + i) * Math.PI / 4;
                int x = settlement.x() + (int) Math.round(Math.cos(angle) * distance * 16);
                int z = settlement.z() + (int) Math.round(Math.sin(angle) * distance * 16);
                if (province != 0 && territory.provinceAt(dimension, ChunkKey.of(x >> 4, z >> 4)) != province) {
                    // The square of a settlement stays in its province: there the province is held in a war.
                    continue;
                }
                Settlement there = new Settlement(settlement.id(), settlement.kingdomId(), settlement.province(), settlement.parent(), settlement.tier(),
                        x, z, settlement.seed(), settlement.name());
                if (problem(level, there) == null) {
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }

    /**
     * Leaves a settlement as data for good: its chunks go, and its people go to the nearest settlement of its kingdom.
     */
    static void block(MinecraftServer server, Settlement settlement, String reason) {
        if (job != null && job.id == settlement.id()) {
            release(server.overworld());
            job = null;
        }
        LOGGER.warn("{} is not raised: {}", settlement, reason);
        Settlement nearest = null;
        long best = Long.MAX_VALUE;
        for (Settlement other : NpcWork.state(server).settlementsOf(settlement.kingdomId())) {
            if (other.id() == settlement.id() || other.tier() == Tier.CAPITAL || other.status() == Settlement.Status.BLOCKED
                    || other.status() == Settlement.Status.RUINED) {
                continue;
            }
            long dx = other.x() - settlement.x();
            long dz = other.z() - settlement.z();
            if (dx * dx + dz * dz < best) {
                best = dx * dx + dz * dz;
                nearest = other;
            }
        }
        if (nearest != null && settlement.population() > 0) {
            nearest.setPopulation(nearest.population() + settlement.population());
            settlement.setPopulation(0);
        }
        settlement.setStatus(Settlement.Status.BLOCKED);
    }

    // ---------------------------------------------------------------- the ground and the buildings

    /**
     * Levels the ground of a settlement column by column within the budget: its square, its street and the soil of its yards,
     * with a slope around it a road can climb.
     */
    private static void ground(ServerLevel level, Settlement settlement, Job job) {
        SettlementLayouts.Layout layout = job.layout;
        int reach = layout.reach();
        int side = 2 * (reach + NpcSpawner.RAMP) + 1;
        int total = side * side;
        int budget = blocks(job);
        long deadline = deadline(job);
        NpcGround.Soil soil = NpcGround.soilAt(level, settlement.x() + reach + NpcSpawner.RAMP + 3, settlement.z());
        BlockState path = soil == NpcGround.Soil.GRASS ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        int step = settlement.step();
        UUID id = settlement.kingdomId();
        int y = settlement.groundY();
        while (step < total && budget > 0 && System.nanoTime() - deadline < 0) {
            int dx = step % side - reach - NpcSpawner.RAMP;
            int dz = step / side - reach - NpcSpawner.RAMP;
            int x = settlement.x() + dx;
            int z = settlement.z() + dz;
            int out = Math.max(Math.abs(dx), Math.abs(dz)) - reach;
            BlockState top = layout.city() != null ? NpcCity.surface(job.site, layout.city(), dx, dz, soil) : surface(layout, dx, dz, soil, path);
            if (out > 0 && top == soil.top()) {
                budget -= Math.max(1, NpcSpawner.ramp(level, id, x, z, y, out, soil));
            } else {
                budget -= Math.max(1, NpcGround.level(level, id, x, z, y, top, soil.fill()));
            }
            step++;
        }
        settlement.setStage(SettlementStage.GROUND.id(), step);
        if (step >= total) {
            advance(settlement, SettlementStage.BUILD);
        }
    }

    /**
     * The top of the ground of a town, a village or a hamlet: a cobbled square around the town hall, a path along the street.
     */
    private static BlockState surface(SettlementLayouts.Layout layout, int dx, int dz, NpcGround.Soil soil, BlockState path) {
        if (layout.hall() && Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        int width = layout.tier() == Tier.HAMLET ? 1 : 2;
        if (Math.abs(dz) <= width && Math.abs(dx) <= layout.reach() - 3) {
            return path;
        }
        return soil.top();
    }

    /**
     * The town hall (with the town of a town or a large city) first, then the buildings a part at a time.
     */
    private static void build(MinecraftServer server, ServerLevel level, Settlement settlement, Job job) {
        UUID id = settlement.kingdomId();
        int y = settlement.groundY() + 1;
        int step = settlement.step();
        if (step == 0) {
            if (job.layout.hall()) {
                BlockPos hall = new BlockPos(settlement.x(), y, settlement.z());
                Optional<City> city = RoadWork.foundSettlementCity(level, id, hall, settlement.name().latin());
                if (city.isPresent()) {
                    if (!level.getBlockState(hall).is(RoadRegistries.TOWN_HALL.get())) {
                        level.setBlock(hall, RoadRegistries.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                    settlement.setCityId(city.get().id());
                }
            }
            settlement.setStage(SettlementStage.BUILD.id(), 1);
            return;
        }
        List<NpcComposition.Plot> plots = job.layout.plots();
        int budget = blocks(job);
        int index = step - 1;
        while (index < plots.size() && budget > 0) {
            NpcComposition.Plot plot = plots.get(index);
            Materials materials = job.layout.city() != null ? NpcCity.materials(server, job.site, plot) : NpcCity.townMaterials(server, job.site, 0, plot);
            int[] used = new int[1];
            boolean standing = NpcSpawner.buildAt(server, level, id, plot.template(), settlement.x() + plot.x(), y, settlement.z() + plot.z(),
                    new Placement(plot.facing(), plot.mirror()), materials, budget, used);
            budget -= Math.max(1, used[0]);
            if (!standing) {
                break;
            }
            index++;
            settlement.setStage(SettlementStage.BUILD.id(), index + 1);
        }
        if (index >= plots.size()) {
            advance(settlement, SettlementStage.WORK);
        }
    }

    /**
     * The fields of a settlement, one a tick.
     */
    private static void work(ServerLevel level, Settlement settlement, Job job) {
        List<int[]> fields = job.layout.fields();
        int step = settlement.step();
        if (step < fields.size()) {
            int[] field = fields.get(step);
            NpcSpawner.field(level, settlement.kingdomId(), settlement.x() + field[0], settlement.z() + field[1],
                    step % 2 == 0 ? Blocks.WHEAT : Blocks.CARROTS);
            settlement.setStage(SettlementStage.WORK.id(), step + 1);
            return;
        }
        advance(settlement, SettlementStage.PLACES);
    }

    /**
     * Looks through the chunks of the land of the settlement (not of the whole kingdom) for beds and posts, then gives the posts
     * inside its buildings their professions by what each building is for, a building a tick.
     */
    private static void places(ServerLevel level, Settlement settlement, Job job) {
        List<long[]> chunks = SettlementLayouts.square(settlement.x(), settlement.z(), SettlementLayouts.land(settlement.tier()));
        int budget = Math.max(1, chunkBudget(job)) * 4;
        int step = settlement.step();
        while (step < chunks.size() && budget-- > 0) {
            LevelChunk loaded = level.getChunkSource().getChunkNow((int) chunks.get(step)[0], (int) chunks.get(step)[1]);
            if (loaded != null) {
                CitizenSites.scanChunk(level, loaded);
            }
            step++;
        }
        if (step < chunks.size()) {
            settlement.setStage(SettlementStage.PLACES.id(), step);
            return;
        }
        List<io.github.sekerator.kingdomworld.core.construction.Building> buildings = buildingsOf(level.getServer(), settlement);
        int index = step - chunks.size();
        if (index < buildings.size()) {
            professions(level, settlement.kingdomId(), buildings.get(index));
            settlement.setStage(SettlementStage.PLACES.id(), step + 1);
            return;
        }
        settlement.setHousing(Math.max(job.layout.homes(), 0));
        advance(settlement, SettlementStage.WEALTH);
    }

    /**
     * The buildings of the kingdom of a settlement on its land, in a fixed order.
     */
    static List<io.github.sekerator.kingdomworld.core.construction.Building> buildingsOf(MinecraftServer server, Settlement settlement) {
        int reach = SettlementLayouts.land(settlement.tier()) * 8;
        List<io.github.sekerator.kingdomworld.core.construction.Building> list = new ArrayList<>();
        for (io.github.sekerator.kingdomworld.core.construction.Building building
                : KingdomWorldApi.state(server).construction().buildingsOf(settlement.kingdomId())) {
            if (Math.abs(building.anchor().x() - settlement.x()) <= reach && Math.abs(building.anchor().z() - settlement.z()) <= reach) {
                list.add(building);
            }
        }
        list.sort(java.util.Comparator.comparingLong(building -> BlockPos.asLong(building.anchor().x(), building.anchor().y(), building.anchor().z())));
        return list;
    }

    /**
     * The posts inside a building take the profession of what it is for and what stands in it: one worker each, two at a market.
     */
    private static void professions(ServerLevel level, UUID id, io.github.sekerator.kingdomworld.core.construction.Building building) {
        if (building.role().isEmpty()) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(level.getServer());
        io.github.sekerator.kingdomworld.core.construction.Box box = building.box();
        List<WorkPost> inside = new ArrayList<>();
        for (WorkPost post : state.citizens().posts().postsOf(id)) {
            WorldPos at = post.pos();
            if (at.x() >= box.minX() && at.x() <= box.maxX() && at.y() >= box.minY() && at.y() <= box.maxY() && at.z() >= box.minZ()
                    && at.z() <= box.maxZ()) {
                inside.add(post);
            }
        }
        if (inside.isEmpty()) {
            return;
        }
        Set<Block> blocks = new java.util.HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    blocks.add(level.getBlockState(pos.set(x, y, z)).getBlock());
                }
            }
        }
        String profession = NpcCity.profession(building.role(), blocks::contains);
        if (profession.isEmpty()) {
            return;
        }
        int max = io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings.rules().maxSeats();
        int seats = "merchant".equals(profession) ? 2 : 1;
        for (WorkPost post : inside) {
            state.setPostProfession(null, post.pos(), profession);
            state.setPostSeats(null, post.pos(), Math.max(1, Math.min(seats, max)), max);
        }
    }

    /**
     * The stores a settlement is founded with, once: food for its people for the days of the rules and a little wood and stone by
     * its rank, into the storehouses of its kingdom.
     */
    private static void wealth(MinecraftServer server, Settlement settlement, Job job) {
        if (settlement.given(Settlement.GIVEN_STORES)) {
            advance(settlement, SettlementStage.PEOPLE);
            return;
        }
        UUID id = settlement.kingdomId();
        int people = Math.min(settlement.population(), job.layout.homes());
        int bread = (int) Math.min(64L * 16, ((long) NpcSettings.rules().foodDays() * people * CitizenSettings.rules().dailyNutrition()
                + NpcWealth.BREAD_NUTRITION - 1) / NpcWealth.BREAD_NUTRITION);
        int stacks = switch (settlement.tier()) {
            case CITY -> 4;
            case TOWN -> 2;
            case VILLAGE -> 1;
            default -> 0;
        };
        String wood = NpcCity.wood(job.site);
        WorldPos near = new WorldPos(Dimensions.id(server.overworld()), settlement.x(), settlement.groundY() + 1, settlement.z());
        int lost = 0;
        lost += store(server, id, "minecraft:bread", bread, near);
        lost += store(server, id, wood + "_log", 64 * stacks, near);
        lost += store(server, id, wood + "_planks", 64 * stacks, near);
        lost += store(server, id, "minecraft:cobblestone", 64 * stacks, near);
        if (lost > 0) {
            LOGGER.warn("{} item(s) of the founding stores of {} did not fit into the storehouses of its kingdom", lost, settlement);
        }
        settlement.give(Settlement.GIVEN_STORES);
        LOGGER.info("{} got its founding stores: {} bread, {} stacks each of wood and stone", settlement, bread, stacks);
        advance(settlement, SettlementStage.PEOPLE);
    }

    private static int store(MinecraftServer server, UUID id, String itemId, int count, WorldPos near) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item == null || item == net.minecraft.world.item.Items.AIR || count <= 0) {
            return 0;
        }
        int lost = 0;
        int left = count;
        while (left > 0) {
            int part = Math.min(left, new ItemStack(item).getMaxStackSize());
            lost += KingdomStorage.insert(server, id, new ItemStack(item, part), near).getCount();
            left -= part;
        }
        return lost;
    }

    /**
     * The named citizens of a settlement, as many as its people and its beds allow, four a tick, each always the same person for
     * its slot; then the posts of its land take the citizens without work and its fields their farmers.
     */
    private static void people(MinecraftServer server, ServerLevel level, Settlement settlement, Job job) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int named = named(settlement, job.layout);
        int slot = settlement.step();
        WorldPos at = new WorldPos(Dimensions.id(level), settlement.x(), settlement.groundY() + 1, settlement.z());
        if (slot < named) {
            for (int i = 0; i < CITIZENS_PER_TICK && slot < named; i++, slot++) {
                CitizenLife.settle(server, settlement.kingdomId(), settlement.id(), settlement.seed(), slot, at, Long.MIN_VALUE);
            }
            settlement.setStage(SettlementStage.PEOPLE.id(), slot);
            return;
        }
        if (!hireSome(state, settlement, job)) {
            return;
        }
        // Two farmers for every field, from the citizens of the settlement without work.
        int wanted = 2 * job.layout.fields().size();
        int made = 0;
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(settlement.kingdomId()))) {
            if (made >= wanted) {
                break;
            }
            if (citizen.settlement() == settlement.id() && citizen.professionId().equals(Professions.UNEMPLOYED.id())
                    && state.setProfession(null, citizen.id(), Professions.FARMER.id()).ok()) {
                CitizenActions.afterProfessionChange(server, citizen);
                made++;
            }
        }
        advance(settlement, SettlementStage.GUARDS);
    }

    /**
     * The named citizens a settlement has room for: its people, as many as its houses have beds.
     */
    public static int named(Settlement settlement, SettlementLayouts.Layout layout) {
        return Math.max(0, Math.min(settlement.population(), layout.homes()));
    }

    /**
     * The posts on the land of a settlement take the citizens without work nearest to them, while the time of the step lasts.
     *
     * @return whether every post was gone through
     */
    private static boolean hireSome(KingdomWorldState state, Settlement settlement, Job job) {
        long deadline = deadline(job);
        int reach = SettlementLayouts.land(settlement.tier()) * 8;
        List<WorkPost> posts = state.citizens().posts().postsOf(settlement.kingdomId());
        while (job.hireIndex < posts.size()) {
            WorkPost post = posts.get(job.hireIndex);
            if (post.hasProfession() && Math.abs(post.pos().x() - settlement.x()) <= reach && Math.abs(post.pos().z() - settlement.z()) <= reach) {
                while (post.freeSeats() > 0 && state.hire(null, post.pos()).outcome().ok()) {
                    if (System.nanoTime() - deadline > 0) {
                        return false;
                    }
                }
            }
            job.hireIndex++;
            if (System.nanoTime() - deadline > 0) {
                return false;
            }
        }
        return true;
    }

    /** Ticks the raising waits for the search of another road of the plan before it goes on without its own. */
    private static final int ROAD_WAIT_TICKS = 600;

    /**
     * The road of the plan to the settlement it belongs to: once the other end stands, its search starts now and it is paid as
     * the roads of the founding are when it is laid (once, by the road of the edge); the raising does not wait for it to be laid.
     * A road that cannot start now is left to the roads of the plan, which lay it once both ends stand.
     */
    private static void roads(MinecraftServer server, Settlement settlement) {
        NpcState state = NpcWork.state(server);
        Optional<io.github.sekerator.kingdomworld.core.npc.PlannedRoad> edge = Optional.empty();
        for (io.github.sekerator.kingdomworld.core.npc.PlannedRoad road : state.plannedRoads()) {
            boolean ours = road.from() == settlement.id() && road.to() == settlement.parent()
                    || road.to() == settlement.id() && road.from() == settlement.parent();
            if (ours && road.roadId() == 0) {
                edge = Optional.of(road);
                break;
            }
        }
        Optional<Settlement> parent = state.settlement(settlement.parent());
        if (edge.isEmpty() || parent.isEmpty() || !io.github.sekerator.kingdomworld.common.road.PlannedRoads.stands(state, parent.get())
                || RoadWork.isReadOnly(server)) {
            advance(settlement, SettlementStage.WALL);
            return;
        }
        io.github.sekerator.kingdomworld.core.kingdom.Outcome started = io.github.sekerator.kingdomworld.common.road.PlannedRoads.start(server, edge.get());
        if (started.ok() || settlement.step() >= ROAD_WAIT_TICKS) {
            if (!started.ok()) {
                LOGGER.info("The road of {} to the settlement it belongs to waits for the roads of the plan: {}", settlement, started.error());
            }
            advance(settlement, SettlementStage.WALL);
            return;
        }
        settlement.setStage(SettlementStage.ROADS.id(), settlement.step() + 1);
    }

    private static void done(MinecraftServer server, ServerLevel level, Settlement settlement, Job job) {
        settlement.setStatus(Settlement.Status.STANDING);
        settlement.setLastPlayerTick(KingdomWorldApi.gameTime(server));
        long ticks = server.getTickCount() - job.startedTick;
        LOGGER.info("{} stands: {} building(s) in {} s, {} step tick(s), the average {} ms and the worst {} ms of its own work", settlement,
                job.layout.plots().size(), ticks / 20, job.ticks, job.ticks == 0 ? 0 : String.format(java.util.Locale.ROOT, "%.2f", job.sumNanos / 1e6 / job.ticks),
                String.format(java.util.Locale.ROOT, "%.2f", job.maxNanos / 1e6));
        release(level);
        SettlementRaiser.job = null;
        io.github.sekerator.kingdomworld.common.road.RoadSync.changed();
    }

    /**
     * What an operator is told of a settlement: its stage, step and how its raising goes.
     */
    public static String describe(Settlement settlement) {
        String running = job != null && job.id == settlement.id() ? String.format(java.util.Locale.ROOT,
                ", raising %s, %d tick(s), worst %.2f ms, %d chunk(s) held", job.background ? "in the background" : "at the full budget",
                job.ticks, job.maxNanos / 1e6, job.ticketed.size()) : "";
        return settlement + " at stage " + SettlementStage.byId(settlement.stage()).id() + " step " + settlement.step() + running;
    }
}
