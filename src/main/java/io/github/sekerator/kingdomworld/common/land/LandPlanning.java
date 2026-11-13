package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.npc.NpcPlanning;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.land.BorderRefiner;
import io.github.sekerator.kingdomworld.core.land.ChunkProbes;
import io.github.sekerator.kingdomworld.core.land.PlanProgress;
import io.github.sekerator.kingdomworld.core.land.PlanProgress.Stage;
import io.github.sekerator.kingdomworld.core.land.PlannedProvince;
import io.github.sekerator.kingdomworld.core.land.PlannedState;
import io.github.sekerator.kingdomworld.core.land.ProvincePlan;
import io.github.sekerator.kingdomworld.core.land.ProvincePlanner;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.RefinedLand;
import io.github.sekerator.kingdomworld.core.land.StatePlan;
import io.github.sekerator.kingdomworld.core.land.StatePlanner;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.SurveyRaster;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The plan of the states of a new world (the key {@code plan = "states"} of the kingdoms of the world), made over ticks in the
 * stages of {@link PlanProgress}: the land is surveyed; the land generated and the land of kingdoms are marked; the states are
 * laid out on a worker thread; their thrones are judged in the world; the provinces are laid out on the worker thread; their
 * borders through water are probed chunk by chunk; the provinces are written into the map of the land tile by tile; and the
 * states are founded as data, each given its provinces as soon as it stands. A tick spends {@value #BUDGET_NANOS} ns on the
 * plan at most, and the worker thread touches nothing of the game.
 *
 * <p>Where the plan has got is kept in the file of the provinces ({@link ProvinceSavedData#plan()}) with the provinces it
 * wrote, so that both are saved together: a restart goes on from the place reached in its stage. The plans of the states and the
 * provinces are not kept: after a restart the worker makes them again from the survey, the marks and the thrones kept, the same
 * as before.
 */
public final class LandPlanning {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The time of the server thread a tick spends on the plan at most. */
    public static final long BUDGET_NANOS = 8_000_000L;
    /** Throne candidates judged in a tick at most. */
    static final int THRONES_PER_TICK = 2;
    /** Tiles of the map of the land written in a tick at most. */
    static final int TILES_PER_TICK = 4;
    /** Headers of region files read at once at most. */
    static final int REGIONS_IN_FLIGHT = 32;
    /** Passes over the throne candidates of a state, each asking less of the ground. */
    static final int THRONE_PASSES = 4;
    /** Chunks around the land of a kingdom the states keep away from. */
    static final int KINGDOM_MARGIN = 4;
    /** What the plan of the provinces file says it is. */
    public static final String KIND = "states";
    static final int TAG_VERSION = 1;
    /** Mixed into the seed of the world for the seed of the plan. */
    private static final long SALT = 0x5374617465734C6EL;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KingdomWorld land plan");
        thread.setDaemon(true);
        return thread;
    });

    private static Job world;
    private static boolean warned;

    /**
     * The time the collector of garbage took so far, in nanoseconds: a tick of the plan it fell into is counted without it too.
     */
    static long pauseNanos() {
        long millis = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            millis += Math.max(0, bean.getCollectionTime());
        }
        return millis * 1_000_000L;
    }

    private LandPlanning() {
    }

    /**
     * Forgets the plan of the world held in memory, when a server stops.
     */
    public static void reset() {
        world = null;
        warned = false;
    }

    /**
     * Whether the plan of the kingdoms of a world is the plan of states: one was begun already (it goes on whatever the config
     * says now), or the config asks for it and the plan of capitals has not begun.
     */
    public static boolean wanted(MinecraftServer server, boolean statesConfigured) {
        if (world != null && world.server == server) {
            return true;
        }
        NpcState state = NpcWork.state(server);
        boolean legacyBegun = !state.sites().isEmpty() || state.planCursor() > 0 || state.planPass() > 0;
        return wanted(ProvinceSavedData.get(server).hasPlan(), statesConfigured, legacyBegun);
    }

    /**
     * The choice of the plan: a plan of states begun goes on; otherwise one begins only when the config asks for it and the plan of
     * capitals has not begun.
     */
    public static boolean wanted(boolean statesBegun, boolean statesConfigured, boolean legacyBegun) {
        return statesBegun || (statesConfigured && !legacyBegun);
    }

    /**
     * A tick of the plan of the world.
     */
    public static void tick(MinecraftServer server) {
        Job job = worldJob(server);
        if (job != null) {
            job.tick();
        }
    }

    /**
     * The plan of the world, begun or read from the file of the provinces; null while the files are read-only.
     */
    private static Job worldJob(MinecraftServer server) {
        if (world != null && world.server == server) {
            return world;
        }
        ProvinceSavedData data = ProvinceSavedData.get(server);
        SurveySavedData survey = SurveySavedData.get(server);
        if (data.isReadOnly() || survey.isReadOnly()) {
            if (!warned) {
                warned = true;
                LOGGER.warn("The plan of the states of the world waits: the file of the provinces or of the survey is read-only");
            }
            return null;
        }
        TerritoryIndex territory = LandEvents.attach(server);
        CompoundTag kept = data.plan();
        Job job;
        if (kept.isEmpty()) {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int base = 0;
            for (Province province : data.map().provinces()) {
                base = Math.max(base, province.id());
            }
            Setup setup = new Setup(NpcSettings.stateRules(spawn.getX(), spawn.getZ()), NpcSettings.provinceRules(),
                    level.getSeed() ^ SALT, base);
            job = new Job(server, setup, survey, data.map(), territory, true, data::setDirty);
            StateRules rules = setup.states();
            LOGGER.info("The plan of the states of the world begins: {} small, {} medium and {} large state(s) between {} and {} blocks "
                            + "of the spawn {} {}", rules.quota()[0], rules.quota()[1], rules.quota()[2], rules.spawnZone(), rules.radius(),
                    rules.spawnX(), rules.spawnZ());
        } else {
            job = Job.read(server, kept, survey, data.map(), territory, true, data::setDirty);
            if (job == null) {
                if (!warned) {
                    warned = true;
                    LOGGER.error("The plan of the states of the world in the file of the provinces cannot be read: it waits");
                }
                return null;
            }
            LOGGER.info("The plan of the states of the world goes on at {}", job.progress);
        }
        Job made = job;
        data.setPlanSource(made.progress.done() ? null : made::write);
        if (made.progress.done()) {
            data.setPlan(made.write());
        }
        world = job;
        return job;
    }

    /**
     * The plan of the world as it stands, for operators: held in memory, or read from the file of the provinces.
     */
    public static Optional<Job> describe(MinecraftServer server) {
        if (world != null && world.server == server) {
            return Optional.of(world);
        }
        CompoundTag kept = ProvinceSavedData.get(server).plan();
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Job.read(server, kept, SurveySavedData.get(server), null, null, false, () -> {
        }));
    }

    /**
     * What a plan is made with: the rules of its states and provinces, its seed and the number its provinces start after.
     */
    public record Setup(StateRules states, ProvinceRules provinces, long seed, int idBase) {
        public Setup {
            if (idBase < 0 || idBase >= ProvinceMap.MAX_ID) {
                throw new IllegalArgumentException("The provinces of a plan start after " + idBase);
            }
        }

        CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("spawnX", states.spawnX());
            tag.putInt("spawnZ", states.spawnZ());
            tag.putInt("spawnZone", states.spawnZone());
            tag.putInt("radius", states.radius());
            tag.putInt("gap", states.gapBlocks());
            tag.putDouble("neutral", states.neutralShare());
            tag.putDouble("ring", states.ringShare());
            tag.putIntArray("quota", states.quota());
            tag.putIntArray("least", states.minChunks());
            tag.putIntArray("most", states.maxChunks());
            tag.putIntArray("province", new int[]{provinces.cellsPerProvince(), provinces.minCells(), provinces.maxCells()});
            tag.putLong("seed", seed);
            tag.putInt("base", idBase);
            return tag;
        }

        static Setup read(CompoundTag tag) {
            int[] province = tag.getIntArray("province");
            StateRules states = new StateRules(tag.getInt("spawnX"), tag.getInt("spawnZ"), tag.getInt("spawnZone"), tag.getInt("radius"),
                    tag.getInt("gap"), tag.getDouble("neutral"), tag.getDouble("ring"), tag.getIntArray("quota"), tag.getIntArray("least"),
                    tag.getIntArray("most"));
            ProvinceRules provinces = province.length == 3 ? new ProvinceRules(province[0], province[1], province[2]) : ProvinceRules.DEFAULT;
            return new Setup(states, provinces, tag.getLong("seed"), tag.getInt("base"));
        }
    }

    /**
     * A state of the plan once its provinces are in the map: where its throne stands, its provinces and its place among the
     * kingdoms of the world.
     */
    static final class Entry {
        final NpcLevel level;
        final long seed;
        final int x;
        final int z;
        final int chunks;
        final int[] provinces;
        int site = -1;

        Entry(NpcLevel level, long seed, int x, int z, int chunks, int[] provinces) {
            this.level = level;
            this.seed = seed;
            this.x = x;
            this.z = z;
            this.chunks = chunks;
            this.provinces = provinces;
        }

        CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putString("level", level.id());
            tag.putLong("seed", seed);
            tag.putInt("x", x);
            tag.putInt("z", z);
            tag.putInt("chunks", chunks);
            tag.putIntArray("provinces", provinces);
            tag.putInt("site", site);
            return tag;
        }

        static Entry read(CompoundTag tag) {
            NpcLevel level = NpcLevel.byId(tag.getString("level")).orElse(NpcLevel.SMALL);
            Entry entry = new Entry(level, tag.getLong("seed"), tag.getInt("x"), tag.getInt("z"), tag.getInt("chunks"),
                    tag.getIntArray("provinces"));
            entry.site = tag.contains("site") ? tag.getInt("site") : -1;
            return entry;
        }
    }

    /**
     * The plans of the worker thread: those of the states, the provinces with the cells to probe, and the borders drawn.
     */
    private record Plans(StatePlan states, ProvincePlan provinces, int[] probeCells, RefinedLand refined) {
    }

    /**
     * A plan of states over ticks: the plan of the world, or one a game test makes apart from it.
     */
    public static final class Job implements PlanProgress.Work {
        private final MinecraftServer server;
        private final Setup setup;
        private final SurveySavedData survey;
        private final ProvinceMap map;
        private final TerritoryIndex territory;
        private final boolean found;
        private final Runnable dirty;
        private PlanProgress progress = new PlanProgress();
        private byte[] marks;
        private byte[] probeClasses;
        private boolean defined;
        private final List<Entry> entries = new ArrayList<>();
        private CompoundTag summary = new CompoundTag();

        // Held in memory only.
        private final Map<Integer, CompletableFuture<BitSet>> reads = new HashMap<>();
        private Plans plans = new Plans(null, null, null, null);
        private CompletableFuture<Plans> pending;
        private String failure;
        private long[] tiles;
        private boolean finished;
        /** The throne candidate being judged, column by column. */
        private NpcPlanning.CapitalJudge judge;
        private int judgeCursor = -1;
        /**
         * What a look at the height of the ground and a look at a biome cost lately, to stop before a look would go past the time
         * of the tick.
         */
        private long heightNanos = 3_000_000L;
        private long biomeNanos = 300_000L;
        private int looksThisTick;
        private long tickStart;

        /**
         * @param map       the map of the land the provinces are written into
         * @param territory the land of the kingdoms the states get their provinces through, or null when the plan founds nothing
         * @param found     whether the plan founds its states
         * @param dirty     told whenever the plan changed, so that its file is written
         */
        public Job(MinecraftServer server, Setup setup, SurveySavedData survey, ProvinceMap map, TerritoryIndex territory, boolean found,
                   Runnable dirty) {
            this.server = server;
            this.setup = setup;
            this.survey = survey;
            this.map = map;
            this.territory = territory;
            this.found = found;
            this.dirty = dirty;
        }

        public Setup setup() {
            return setup;
        }

        public PlanProgress progress() {
            return progress;
        }

        public SurveySavedData survey() {
            return survey;
        }

        public boolean done() {
            return progress.done();
        }

        /**
         * Why the plan stopped, if it did: the worker thread failed. It goes on after a restart.
         */
        public Optional<String> failure() {
            return Optional.ofNullable(failure);
        }

        /**
         * Whether the worker thread is making a plan now.
         */
        public boolean working() {
            return pending != null && !pending.isDone();
        }

        /**
         * The places of the states among the kingdoms of the world, -1 for one not planned yet, in the order of the states.
         */
        public int[] sites() {
            int[] sites = new int[entries.size()];
            for (int i = 0; i < sites.length; i++) {
                sites[i] = entries.get(i).site;
            }
            return sites;
        }

        /**
         * The provinces of each state in the map, once they are there.
         */
        public List<int[]> stateProvinces() {
            List<int[]> list = new ArrayList<>();
            for (Entry entry : entries) {
                list.add(entry.provinces.clone());
            }
            return list;
        }

        /**
         * What the plan came to so far, for the log and operators: the sizes of the states, the provinces, the neutral share.
         */
        public CompoundTag summary() {
            return summary.copy();
        }

        /**
         * How much of its stage the plan has done: the place reached and the end, both 0 when unknown.
         */
        public int[] stageWork() {
            int cursor = progress.cursor();
            return switch (progress.stage()) {
                case SURVEY -> new int[]{survey.started() ? survey.nextCell() : 0, survey.started() ? survey.cells() : 0};
                case GUARD -> new int[]{cursor, survey.started() ? regionCount() : 0};
                case THRONES -> new int[]{cursor / (THRONE_PASSES * StatePlanner.THRONE_CANDIDATES), setup.states().quota()[0]
                        + setup.states().quota()[1] + setup.states().quota()[2]};
                case REFINE -> new int[]{cursor, plans.probeCells() == null ? 0 : plans.probeCells().length * SurveyRaster.CHUNKS_PER_CELL};
                case RASTER -> new int[]{cursor, tiles == null ? 0 : tiles.length};
                case FOUND -> new int[]{cursor, 2 * entries.size()};
                default -> new int[]{0, 0};
            };
        }

        /**
         * A tick of the plan within {@link #BUDGET_NANOS}.
         */
        public void tick() {
            tick(BUDGET_NANOS);
        }

        /**
         * A tick of the plan within a time of the server thread: shorter ones for game tests that stop a stage in its middle.
         */
        public void tick(long budgetNanos) {
            if (progress.done()) {
                finish();
                return;
            }
            looksThisTick = 0;
            tickStart = System.nanoTime();
            progress.tick(this, System::nanoTime, LandPlanning::pauseNanos, budgetNanos, System.currentTimeMillis());
            dirty.run();
            if (progress.done()) {
                finish();
            }
        }

        @Override
        public boolean run(Stage stage, PlanProgress progress, long deadline) {
            return switch (stage) {
                case SURVEY -> survey(deadline);
                case GUARD -> guard(deadline);
                case STATES -> states();
                case THRONES -> thrones(deadline);
                case PROVINCES -> provinces();
                case REFINE -> refine(deadline);
                case RASTER -> raster(deadline);
                case SETTLE, ROADS -> true;
                case FOUND -> found();
                case DONE -> true;
            };
        }

        // ------------------------------------------------------------ stages

        /**
         * Whether looks at the noise may begin: they would end before the deadline by what such looks cost lately, or nothing was
         * looked at this tick yet and the tick has only begun.
         *
         * @param heights how many looks at the height of the ground they take
         * @param biomes  how many looks at a biome they take
         */
        private boolean room(long deadline, int heights, int biomes) {
            long now = System.nanoTime();
            if (now - deadline >= 0) {
                return false;
            }
            if (looksThisTick == 0 && now - tickStart < BUDGET_NANOS / 4) {
                return true;
            }
            // Half as much again as they cost lately: one look at the noise may cost more than the ones before it.
            long guess = heights * heightNanos + biomes * biomeNanos;
            return now + guess + guess / 2 - deadline < 0;
        }

        /**
         * Counts looks that began at a time: what they cost becomes the guess for the next ones, a dear one forgotten slowly.
         */
        private void looked(long start, int heights, int biomes) {
            long cost = System.nanoTime() - start;
            if (heights > 0) {
                long each = Math.max(0, cost - biomes * biomeNanos) / heights;
                heightNanos = Math.min(BUDGET_NANOS, Math.max(each, heightNanos - heightNanos / 8));
            } else if (biomes > 0) {
                biomeNanos = Math.min(BUDGET_NANOS, Math.max(cost / biomes, biomeNanos - biomeNanos / 8));
            }
            looksThisTick++;
        }

        private boolean survey(long deadline) {
            StateRules rules = setup.states();
            if (!survey.started() || survey.spawnX() != rules.spawnX() || survey.spawnZ() != rules.spawnZ() || survey.radius() != rules.radius()) {
                survey.start(rules.spawnX(), rules.spawnZ(), rules.radius(), server.overworld().getSeaLevel());
            }
            return LandSurvey.step(server, survey, deadline);
        }

        private int regionX0() {
            return Math.floorDiv(survey.originX(), 512);
        }

        private int regionZ0() {
            return Math.floorDiv(survey.originZ(), 512);
        }

        private int regionsAcross() {
            return Math.floorDiv(survey.originX() + survey.width() * SurveyRaster.CELL - 1, 512) - regionX0() + 1;
        }

        private int regionCount() {
            int down = Math.floorDiv(survey.originZ() + survey.height() * SurveyRaster.CELL - 1, 512) - regionZ0() + 1;
            return regionsAcross() * down;
        }

        private boolean guard(long deadline) {
            if (marks == null || marks.length != survey.cells()) {
                marks = new byte[survey.cells()];
            }
            int count = regionCount();
            int across = regionsAcross();
            int x0 = regionX0();
            int z0 = regionZ0();
            int cursor = progress.cursor();
            while (cursor < count && System.nanoTime() - deadline < 0) {
                CompletableFuture<BitSet> read = reads.get(cursor);
                if (read == null || !read.isDone()) {
                    break;
                }
                BitSet bits;
                try {
                    bits = read.join();
                } catch (CompletionException | java.util.concurrent.CancellationException e) {
                    bits = new BitSet();
                }
                int regionX = x0 + cursor % across;
                int regionZ = z0 + cursor / across;
                for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                    markChunk(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5), SurveyRaster.GENERATED);
                }
                reads.remove(cursor);
                cursor++;
            }
            progress.moveTo(cursor);
            for (int i = cursor; i < Math.min(count, cursor + REGIONS_IN_FLIGHT); i++) {
                reads.computeIfAbsent(i, k -> RegionHeaders.generated(server, x0 + k % across, z0 + k / across));
            }
            if (cursor < count) {
                return false;
            }
            markPlayers();
            markKingdoms();
            int generated = 0;
            int kingdoms = 0;
            for (byte mark : marks) {
                generated += (mark & SurveyRaster.GENERATED) != 0 ? 1 : 0;
                kingdoms += (mark & SurveyRaster.PLAYER) != 0 ? 1 : 0;
            }
            summary.putInt("generatedCells", generated);
            summary.putInt("kingdomCells", kingdoms);
            return true;
        }

        private void markChunk(int chunkX, int chunkZ, int mark) {
            int cellX = Math.floorDiv((chunkX << 4) - survey.originX(), SurveyRaster.CELL);
            int cellZ = Math.floorDiv((chunkZ << 4) - survey.originZ(), SurveyRaster.CELL);
            if (cellX >= 0 && cellZ >= 0 && cellX < survey.width() && cellZ < survey.height()) {
                marks[cellZ * survey.width() + cellX] |= (byte) mark;
            }
        }

        /**
         * Marks the chunks of a square around a chunk, a cell at a time.
         */
        private void markAround(int chunkX, int chunkZ, int reach, int mark) {
            int cellX0 = Math.floorDiv(((chunkX - reach) << 4) - survey.originX(), SurveyRaster.CELL);
            int cellX1 = Math.floorDiv(((chunkX + reach) << 4) + 15 - survey.originX(), SurveyRaster.CELL);
            int cellZ0 = Math.floorDiv(((chunkZ - reach) << 4) - survey.originZ(), SurveyRaster.CELL);
            int cellZ1 = Math.floorDiv(((chunkZ + reach) << 4) + 15 - survey.originZ(), SurveyRaster.CELL);
            for (int cz = Math.max(0, cellZ0); cz <= Math.min(survey.height() - 1, cellZ1); cz++) {
                for (int cx = Math.max(0, cellX0); cx <= Math.min(survey.width() - 1, cellX1); cx++) {
                    marks[cz * survey.width() + cx] |= (byte) mark;
                }
            }
        }

        /**
         * The chunks players see now are generated, though their region files may not hold them yet.
         */
        private void markPlayers() {
            int view = server.getPlayerList().getViewDistance();
            for (ServerPlayer player : server.overworld().players()) {
                markAround(player.chunkPosition().x, player.chunkPosition().z, view, SurveyRaster.GENERATED);
            }
        }

        /**
         * The land of the kingdoms there are, claims and provinces held, with {@value #KINGDOM_MARGIN} chunks around it.
         */
        private void markKingdoms() {
            TerritoryIndex index = KingdomWorldApi.state(server).territory();
            String dimension = Dimensions.id(server.overworld());
            for (long chunk : index.claimsIn(dimension).keySet()) {
                markAround(io.github.sekerator.kingdomworld.core.territory.ChunkKey.x(chunk),
                        io.github.sekerator.kingdomworld.core.territory.ChunkKey.z(chunk), KINGDOM_MARGIN, SurveyRaster.PLAYER);
            }
            ProvinceMap held = index.provinceMap().orElse(null);
            if (held == null || !held.anyOwned()) {
                return;
            }
            for (long key : held.tileKeys()) {
                char[] tile = held.tile(key);
                if (tile == null) {
                    continue;
                }
                for (int i = 0; i < tile.length; i++) {
                    if (tile[i] != 0 && held.owner(tile[i]) != null) {
                        markAround(ProvinceMap.tileX(key) * ProvinceMap.TILE + (i & 31), ProvinceMap.tileZ(key) * ProvinceMap.TILE + (i >> 5),
                                KINGDOM_MARGIN, SurveyRaster.PLAYER);
                    }
                }
            }
        }

        private boolean states() {
            if (!ready(1)) {
                return false;
            }
            StatePlan states = plans.states();
            ListTag sizes = new ListTag();
            for (PlannedState state : states.states()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("level", state.level().id());
                entry.putInt("chunks", state.chunks());
                entry.putInt("target", state.targetChunks());
                sizes.add(entry);
            }
            summary.put("states", sizes);
            summary.putDouble("neutralShare", states.neutralShare());
            summary.putInt("landCells", states.landCells());
            summary.putInt("missing", states.missing());
            return true;
        }

        private boolean thrones(long deadline) {
            if (!ready(1)) {
                return false;
            }
            StatePlan states = plans.states();
            SurveyRaster raster = states.raster();
            int count = states.states().size();
            int[] thrones = progress.thrones();
            if (thrones.length != count) {
                thrones = new int[count];
                Arrays.fill(thrones, -1);
                progress.setThrones(thrones);
            }
            int candidates = StatePlanner.THRONE_CANDIDATES;
            int perState = THRONE_PASSES * candidates;
            int cursor = progress.cursor();
            int judged = 0;
            while (cursor < count * perState) {
                int s = cursor / perState;
                if (thrones[s] >= 0) {
                    cursor = (s + 1) * perState;
                    continue;
                }
                int relax = (cursor % perState) / candidates;
                int c = cursor % candidates;
                PlannedState state = states.state(s);
                if (c >= state.throneCandidates().size()) {
                    if (relax + 1 < THRONE_PASSES) {
                        cursor = s * perState + (relax + 1) * candidates;
                    } else {
                        LOGGER.warn("No throne candidate of the {} state {} suits a capital on any pass: its first one is taken", state.level().id(), s);
                        cursor = (s + 1) * perState;
                    }
                    continue;
                }
                // A candidate is judged column by column of the noise, over as many ticks as it takes; two verdicts a tick at most.
                if (judged >= THRONES_PER_TICK || !room(deadline, 1, 0)) {
                    break;
                }
                int cell = state.throneCandidates().get(c);
                if (judge == null || judgeCursor != cursor) {
                    judge = new NpcPlanning.CapitalJudge(server.overworld(), raster.centerX(cell), raster.centerZ(cell), state.level(), relax);
                    judgeCursor = cursor;
                }
                Optional<Boolean> verdict = Optional.empty();
                while (verdict.isEmpty() && room(deadline, judge.heightsNext(), 0)) {
                    long start = System.nanoTime();
                    int heights = judge.heightsNext();
                    verdict = judge.look(server.overworld());
                    looked(start, heights, 0);
                }
                if (verdict.isEmpty()) {
                    break;
                }
                judged++;
                judge = null;
                judgeCursor = -1;
                if (verdict.get()) {
                    thrones[s] = cell;
                    progress.setThrones(thrones);
                    cursor = (s + 1) * perState;
                } else {
                    cursor++;
                }
            }
            progress.moveTo(cursor);
            return cursor >= count * perState;
        }

        private boolean provinces() {
            if (!ready(2)) {
                return false;
            }
            int ofStates = 0;
            int wild = 0;
            for (PlannedProvince province : plans.provinces().provinces()) {
                if (province.wild()) {
                    wild++;
                } else {
                    ofStates++;
                }
            }
            summary.putInt("stateProvinces", ofStates);
            summary.putInt("wildProvinces", wild);
            summary.putInt("probeCells", plans.probeCells().length);
            return true;
        }

        private boolean refine(long deadline) {
            if (!ready(2)) {
                return false;
            }
            int[] cells = plans.probeCells();
            int perCell = SurveyRaster.CHUNKS_PER_CELL;
            if (probeClasses == null || probeClasses.length != cells.length * perCell) {
                if (progress.cursor() > 0) {
                    fail("the cells to probe are not those of the probes kept");
                    return false;
                }
                probeClasses = new byte[cells.length * perCell];
            }
            SurveyRaster raster = plans.provinces().raster();
            ServerLevel level = server.overworld();
            int sea = survey.seaLevel();
            // The place is a chunk: the chunks of a cell come one after another.
            int cursor = progress.cursor();
            int chunks = cells.length * perCell;
            while (cursor < chunks) {
                int cell = cells[cursor / perCell];
                // In a cell of a lake the height of the ground may be looked at too.
                boolean lake = raster.terrain(cell) == io.github.sekerator.kingdomworld.core.land.TerrainClass.LAKE;
                if (!room(deadline, lake ? 1 : 0, 1)) {
                    break;
                }
                int local = cursor % perCell;
                int chunkX = raster.chunkX(cell) + (local % SurveyRaster.CELL_CHUNKS);
                int chunkZ = raster.chunkZ(cell) + (local / SurveyRaster.CELL_CHUNKS);
                long start = System.nanoTime();
                io.github.sekerator.kingdomworld.core.land.TerrainClass terrain = LandSurvey.probeChunk(level, chunkX, chunkZ, sea, raster.terrain(cell));
                probeClasses[cursor] = terrain.id();
                looked(start, LandSurvey.lookedAtGround() ? 1 : 0, 1);
                cursor++;
            }
            progress.moveTo(cursor);
            if (cursor < chunks) {
                return false;
            }
            return ready(3);
        }

        private boolean raster(long deadline) {
            if (!ready(3)) {
                return false;
            }
            if (!defined) {
                define();
            }
            if (tiles == null) {
                tiles = plans.refined().tileKeys();
                summary.putInt("tiles", tiles.length);
            }
            int base = setup.idBase();
            int cursor = progress.cursor();
            int put = 0;
            while (cursor < tiles.length && put < TILES_PER_TICK && System.nanoTime() - deadline < 0) {
                int tileX = RefinedLand.tileX(tiles[cursor]);
                int tileZ = RefinedLand.tileZ(tiles[cursor]);
                char[] planned = plans.refined().tile(tileX, tileZ);
                char[] ids = map.tile(ProvinceMap.tileKey(tileX, tileZ));
                if (ids == null) {
                    ids = new char[ProvinceMap.TILE_CHUNKS];
                }
                boolean any = false;
                for (int i = 0; i < planned.length; i++) {
                    // A chunk in a province already (of another plan, or given by an operator) stays where it is.
                    if (planned[i] != 0 && ids[i] == 0) {
                        ids[i] = (char) (base + planned[i]);
                        any = true;
                    }
                }
                if (any) {
                    map.putTile(tileX, tileZ, ids);
                }
                cursor++;
                put++;
            }
            progress.moveTo(cursor);
            if (cursor < tiles.length) {
                return false;
            }
            summary.putLong("mapBytes", map.memoryBytes());
            return true;
        }

        /**
         * Describes the provinces in the map and keeps what the states need to be founded.
         */
        private void define() {
            int base = setup.idBase();
            ProvincePlan provinces = plans.provinces();
            for (PlannedProvince province : provinces.provinces()) {
                List<Integer> near = new ArrayList<>();
                List<io.github.sekerator.kingdomworld.core.land.BorderKind> kinds = new ArrayList<>();
                for (int i = 0; i < province.neighbours().size(); i++) {
                    int other = province.neighbours().get(i);
                    if (other > 0) {
                        near.add(base + other);
                        kinds.add(province.borderKinds().get(i));
                    }
                }
                map.define(new Province(base + province.id(), province.centerX(), province.centerZ(), near, kinds, province.kind(),
                        province.nameSeed(), 0));
            }
            entries.clear();
            SurveyRaster raster = provinces.raster();
            for (PlannedState state : provinces.states().states()) {
                List<Integer> own = provinces.provincesOf(state.index());
                int[] ids = new int[own.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = base + own.get(i);
                }
                int throne = provinces.throneCell(state.index());
                entries.add(new Entry(state.level(), state.seed(), raster.centerX(throne), raster.centerZ(throne), state.chunks(), ids));
            }
            defined = true;
        }

        private boolean found() {
            if (!found) {
                return true;
            }
            int count = entries.size();
            int cursor = progress.cursor();
            NpcState npc = NpcWork.state(server);
            if (cursor < count) {
                // A place a tick: the founding of each goes on in the work of the kingdoms of the world.
                Entry entry = entries.get(cursor);
                if (entry.site < 0 || npc.site(entry.site).isEmpty()) {
                    // A place planned at the throne before a restart is taken again; one removed or left alone is not.
                    NpcSite site = null;
                    for (NpcSite existing : npc.sites()) {
                        if (existing.x() == entry.x && existing.z() == entry.z && live(existing)) {
                            site = existing;
                        }
                    }
                    if (site == null) {
                        site = NpcPlanning.addSite(server, entry.x, entry.z, entry.level, entry.seed, List.of());
                        LOGGER.info("Planned a state of the world: {} with {} province(s)", site, entry.provinces.length);
                    }
                    entry.site = site.id();
                }
                progress.moveTo(cursor + 1);
                return false;
            }
            int at = cursor - count;
            if (at >= count) {
                return true;
            }
            Entry entry = entries.get(at);
            Optional<NpcSite> site = entry.site < 0 ? Optional.empty() : npc.site(entry.site);
            if (site.isPresent() && site.get().status() == NpcSite.Status.PLANNED) {
                return false;
            }
            Optional<UUID> owner = site.flatMap(NpcSite::kingdomId);
            boolean stands = site.isPresent() && live(site.get());
            if (stands && owner.isPresent() && territory != null) {
                long day = Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
                int given = 0;
                for (int id : entry.provinces) {
                    try {
                        if (territory.transferProvince(id, owner.get(), day)) {
                            given++;
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("The province {} of the state at {} {} is not in the map: it is not given", id, entry.x, entry.z);
                    }
                }
                LOGGER.info("The state of the world {} holds its {} province(s)", site.get(), given);
            } else {
                LOGGER.warn("The state of the plan at {} {} was not founded ({}): its {} province(s) stay without a holder", entry.x, entry.z,
                        site.map(value -> value.status().id() + (value.reason().isEmpty() ? "" : ": " + value.reason())).orElse("no place"),
                        entry.provinces.length);
            }
            progress.moveTo(cursor + 1);
            return at + 1 >= count;
        }

        private static boolean live(NpcSite site) {
            return switch (site.status()) {
                case PLANNED, FOUNDED, SPAWNING, SPAWNED -> true;
                default -> false;
            };
        }

        // ------------------------------------------------------------ the worker thread

        /**
         * Whether the plans a stage needs are in memory (1 the states, 2 the provinces, 3 the borders drawn); when they are not,
         * the worker thread is asked for them, once.
         */
        private boolean ready(int level) {
            if (has(level)) {
                return true;
            }
            if (failure != null) {
                return false;
            }
            if (pending != null) {
                if (!pending.isDone()) {
                    return false;
                }
                try {
                    plans = pending.join();
                } catch (CompletionException | java.util.concurrent.CancellationException e) {
                    fail(String.valueOf(e.getCause() != null ? e.getCause() : e));
                    LOGGER.error("The plan of the states failed on its worker thread", e);
                    return false;
                } finally {
                    pending = null;
                }
                if (has(level)) {
                    return true;
                }
            }
            submit(level);
            return false;
        }

        private boolean has(int level) {
            return switch (level) {
                case 1 -> plans.states() != null;
                case 2 -> plans.provinces() != null;
                default -> plans.refined() != null;
            };
        }

        private void submit(int level) {
            // What the worker needs is copied here, on the server thread.
            SurveyRaster raster = plans.states() == null ? survey.raster(marks == null ? null : marks.clone()) : null;
            int[] thrones = progress.thrones();
            byte[] classes = probeClasses == null ? null : probeClasses.clone();
            Plans before = plans;
            pending = CompletableFuture.supplyAsync(() -> make(before, raster, thrones, classes, level), WORKER);
        }

        private Plans make(Plans before, SurveyRaster raster, int[] thrones, byte[] classes, int level) {
            StatePlan states = before.states() != null ? before.states() : StatePlanner.plan(raster, setup.states(), setup.seed());
            ProvincePlan provinces = before.provinces();
            int[] cells = before.probeCells();
            if (level >= 2 && provinces == null) {
                provinces = ProvincePlanner.plan(states, thrones, setup.provinces(), setup.seed());
                cells = BorderRefiner.probeCells(provinces);
            }
            RefinedLand refined = before.refined();
            if (level >= 3 && refined == null) {
                if (classes == null || classes.length != cells.length * SurveyRaster.CHUNKS_PER_CELL) {
                    throw new IllegalStateException("The probes kept do not fit the cells to probe");
                }
                refined = BorderRefiner.refine(provinces, new ChunkProbes(cells, classes), setup.seed());
            }
            return new Plans(states, provinces, cells, refined);
        }

        private void fail(String why) {
            if (failure == null) {
                failure = why;
                LOGGER.error("The plan of the states stopped at {}: {}. It goes on after a restart", progress, why);
            }
        }

        // ------------------------------------------------------------ the end

        private void finish() {
            if (found) {
                // The plan of the kingdoms of the world is made: the plan of capitals never begins in this world.
                NpcState npc = NpcWork.state(server);
                if (!npc.planned()) {
                    npc.setPlanProgress(npc.planCursor(), npc.planShift(), npc.planPass(), true);
                }
            }
            if (finished) {
                return;
            }
            finished = true;
            reads.clear();
            plans = new Plans(null, null, null, null);
            tiles = null;
            LOGGER.info("The plan of the states of the world is made: {}", report());
            if (this == world) {
                ProvinceSavedData data = ProvinceSavedData.get(server);
                data.setPlan(write());
                data.setPlanSource(null);
                RegionHeaders.forget();
            }
        }

        /**
         * What the plan came to and how long each stage took, in one line.
         */
        public String report() {
            StringBuilder text = new StringBuilder();
            ListTag states = summary.getList("states", Tag.TAG_COMPOUND);
            text.append(states.size()).append(" state(s) of");
            for (int i = 0; i < states.size(); i++) {
                CompoundTag state = states.getCompound(i);
                text.append(i == 0 ? " " : ", ").append(state.getInt("chunks")).append(' ').append(state.getString("level"));
            }
            text.append(" chunks; ").append(summary.getInt("stateProvinces")).append(" province(s) of states and ")
                    .append(summary.getInt("wildProvinces")).append(" wild; ")
                    .append(String.format(Locale.ROOT, "%.1f%%", 100 * summary.getDouble("neutralShare"))).append(" of the land neutral; ")
                    .append(summary.getInt("tiles")).append(" tiles; ")
                    .append(String.format(Locale.ROOT, "%.1f s in all, the worst tick %.2f ms (%s), %.2f ms (%s) without the garbage collector;"
                                    + " stages:", progress.totalWallMillis() / 1000.0, progress.worstTickNanos() / 1e6, progress.worstStage().key(),
                            progress.worstWorkNanos() / 1e6, progress.worstWorkStage().key()));
            for (Stage stage : Stage.values()) {
                if (stage == Stage.DONE || progress.ticks(stage) == 0) {
                    continue;
                }
                text.append(String.format(Locale.ROOT, " %s %.1f s (%.0f ms of the server)", stage.key(), progress.wallMillis(stage) / 1000.0,
                        progress.workNanos(stage) / 1e6));
            }
            return text.toString();
        }

        // ------------------------------------------------------------ the file

        /**
         * The plan as the file of the provinces keeps it; the arrays are copies.
         */
        public CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", KIND);
            tag.putInt("version", TAG_VERSION);
            tag.put("setup", setup.write());
            tag.putLongArray("progress", progress.numbers());
            tag.putIntArray("thrones", progress.thrones());
            if (!progress.done()) {
                if (marks != null) {
                    tag.putByteArray("marks", marks.clone());
                }
                if (probeClasses != null) {
                    tag.putByteArray("probes", probeClasses.clone());
                }
            }
            tag.putBoolean("defined", defined);
            ListTag list = new ListTag();
            for (Entry entry : entries) {
                list.add(entry.write());
            }
            tag.put("states", list);
            tag.put("summary", summary.copy());
            return tag;
        }

        /**
         * A plan read from its tag, or null when the tag is not a plan of states this version understands.
         */
        public static Job read(MinecraftServer server, CompoundTag tag, SurveySavedData survey, ProvinceMap map, TerritoryIndex territory,
                               boolean found, Runnable dirty) {
            if (!KIND.equals(tag.getString("kind")) || tag.getInt("version") > TAG_VERSION) {
                return null;
            }
            Setup setup;
            try {
                setup = Setup.read(tag.getCompound("setup"));
            } catch (IllegalArgumentException e) {
                LOGGER.error("The rules of the plan of states kept cannot be read", e);
                return null;
            }
            Job job = new Job(server, setup, survey, map, territory, found, dirty);
            job.progress = PlanProgress.restore(tag.getLongArray("progress"), tag.getIntArray("thrones"));
            if (tag.contains("marks", Tag.TAG_BYTE_ARRAY)) {
                job.marks = tag.getByteArray("marks").clone();
            }
            if (tag.contains("probes", Tag.TAG_BYTE_ARRAY)) {
                job.probeClasses = tag.getByteArray("probes").clone();
            }
            job.defined = tag.getBoolean("defined");
            ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                job.entries.add(Entry.read(list.getCompound(i)));
            }
            job.summary = tag.getCompound("summary").copy();
            job.finished = job.progress.done();
            return job;
        }
    }
}
