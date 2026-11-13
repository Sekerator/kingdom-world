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
 * plan at most. The looks at the noise of the generator (the survey, the thrones, the borders) are asked of the threads of the
 * noise of the plan of the kingdoms of the world ({@link NpcPlanning#noise()}), which read only the generator, and their answers
 * are put in order on the server thread; the worker thread lays the plans out from copies and touches nothing of the game.
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
    /** Throne candidates judged on the threads of the noise at once at most. */
    static final int THRONES_IN_FLIGHT = 3;
    /** Cells of the survey a task of the threads of the noise looks at. */
    static final int SURVEY_BLOCK = 64;
    /** Tasks of the survey asked of the threads of the noise at once at most. */
    static final int SURVEY_IN_FLIGHT = 9;
    /** Cells of a border along the water a task of the threads of the noise probes. */
    static final int REFINE_BLOCK = 8;
    /** Tasks of the borders asked of the threads of the noise at once at most. */
    static final int REFINE_IN_FLIGHT = 9;
    /** Times a block of the survey or of the borders is asked of the threads of the noise before the plan stops. */
    static final int NOISE_ATTEMPTS = 3;
    /** Tiles of the map of the land written in a tick at most. */
    static final int TILES_PER_TICK = 32;
    /** Provinces given to their state in a tick at most. */
    static final int GIVEN_PER_TICK = 8;
    /** Headers of region files read at once at most. */
    static final int REGIONS_IN_FLIGHT = 32;
    /** Passes over the throne candidates of a state, each asking less of the ground. */
    static final int THRONE_PASSES = 4;
    /**
     * How much more than a capital allows the heights of the survey under its square may differ before the candidate is given up
     * without a look at the world: the survey sees the ground once every 64 blocks.
     */
    static final int ROUGH_SLACK = 12;
    /** The same over the guesses of the height on the grid of the capital, which are a few blocks off each. */
    static final int GUESS_SLACK = 10;
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
        ProvinceSavedData provinces = ProvinceSavedData.get(server);
        if (provinces.isReadOnly()) {
            // The plan the file keeps may not have been read: the places of a world of states would look like a plan of capitals
            // begun, and the plan of capitals would go on among the states. Neither plan goes on until the file is fixed or
            // unlocked (the plan of states waits with a line in the log).
            return true;
        }
        NpcState state = NpcWork.state(server);
        boolean legacyBegun = !state.sites().isEmpty() || state.planCursor() > 0 || state.planPass() > 0;
        return wanted(provinces.hasPlan(), statesConfigured, legacyBegun);
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
                LOGGER.warn("The plan of the kingdoms of the world waits: the file of the provinces or of the survey is read-only (fix it "
                        + "or run /kingdomworld admin land unlock)");
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
     * A block asked of the threads of the noise: the first cell, how many, which try it is and the answer to come.
     */
    private record Asked<T>(int from, int count, int attempt, CompletableFuture<T> answer) {
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
        /** The blocks of the survey asked of the threads of the noise, in order, and the first cell not asked yet. */
        private final java.util.ArrayDeque<Asked<int[]>> surveyBlocks = new java.util.ArrayDeque<>();
        private int surveyAsked = -1;
        /** Blocks of the survey and of the borders that fail on purpose, by their first cell: for game tests. */
        private java.util.function.IntPredicate surveyFault = from -> false;
        private java.util.function.IntPredicate refineFault = from -> false;
        /** The verdicts of throne candidates asked of the threads of the noise, by their place in the stage. */
        private final Map<Integer, CompletableFuture<Boolean>> verdicts = new HashMap<>();
        /** The blocks of the borders asked of the threads of the noise, in order, and the first cell to probe not asked yet. */
        private final java.util.ArrayDeque<Asked<byte[]>> refineBlocks = new java.util.ArrayDeque<>();
        private int refineAsked = -1;
        /** The provinces of the state being given, already given. */
        private int givenOfState;
        /** What the land at the thrones is like, asked of the threads of the noise, by state. */
        private final Map<Integer, CompletableFuture<NpcPlanning.SiteLand>> lands = new HashMap<>();

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
         * For game tests: the blocks of the survey and of the borders, by their first cell, whose look at the noise fails. The
         * predicates are asked on the server thread when a block is asked.
         */
        public void faults(java.util.function.IntPredicate survey, java.util.function.IntPredicate borders) {
            this.surveyFault = survey;
            this.refineFault = borders;
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
            progress.tick(this, System::nanoTime, LandPlanning::pauseNanos, budgetNanos, System.currentTimeMillis());
            dirty.run();
            if (progress.done()) {
                finish();
            }
        }

        @Override
        public boolean run(Stage stage, PlanProgress progress, long deadline) {
            if (failure != null) {
                // A stopped plan stands where it is: what comes after would be made from what failed. It goes on after a restart.
                return false;
            }
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
         * The survey, a block of cells at a time on the threads of the noise: the answers are put into the survey here, in order,
         * as they come, so that the survey is the same as if it were looked at on the server thread.
         */
        private boolean survey(long deadline) {
            StateRules rules = setup.states();
            if (!survey.started() || survey.spawnX() != rules.spawnX() || survey.spawnZ() != rules.spawnZ() || survey.radius() != rules.radius()) {
                try {
                    survey.start(rules.spawnX(), rules.spawnZ(), rules.radius(), server.overworld().getSeaLevel());
                } catch (IllegalArgumentException e) {
                    // A plan kept from before the radius had its bound: the survey cannot hold it.
                    fail("the survey cannot be begun: " + e.getMessage());
                    return false;
                }
                surveyBlocks.clear();
                surveyAsked = -1;
            }
            if (survey.complete()) {
                return true;
            }
            int cells = survey.cells();
            if (surveyAsked < survey.nextCell()) {
                // After a restart: what was asked before is gone, the survey goes on from its row.
                surveyBlocks.clear();
                surveyAsked = survey.nextCell();
            }
            while (!surveyBlocks.isEmpty() && surveyBlocks.peek().answer().isDone() && System.nanoTime() - deadline < 0) {
                Asked<int[]> asked = surveyBlocks.poll();
                int[] looks;
                try {
                    looks = asked.answer().join();
                } catch (CompletionException | java.util.concurrent.CancellationException e) {
                    // The block is asked again in its place: the blocks after it wait, so that no answer lands on the cells of another.
                    if (askAgain("the survey", asked, e)) {
                        surveyBlocks.addFirst(askSurvey(asked.from(), asked.count(), asked.attempt() + 1));
                    }
                    return false;
                }
                int from = survey.nextCell();
                if (asked.from() != from || looks.length != 2 * asked.count()) {
                    fail("a block of the survey for cell " + asked.from() + " came when the survey was at cell " + from);
                    return false;
                }
                for (int i = 0; i < looks.length / 2; i++) {
                    survey.put(from + i, io.github.sekerator.kingdomworld.core.land.TerrainClass.byId(looks[2 * i]), looks[2 * i + 1]);
                }
                survey.advance(from + looks.length / 2);
            }
            if (survey.nextCell() >= cells) {
                survey.finish();
                return true;
            }
            while (surveyBlocks.size() < SURVEY_IN_FLIGHT && surveyAsked < cells) {
                int from = surveyAsked;
                int count = Math.min(SURVEY_BLOCK, cells - from);
                surveyBlocks.add(askSurvey(from, count, 0));
                surveyAsked += count;
            }
            return false;
        }

        /**
         * Asks the threads of the noise for a block of the survey.
         */
        private Asked<int[]> askSurvey(int from, int count, int attempt) {
            ServerLevel level = server.overworld();
            int originX = survey.originX();
            int originZ = survey.originZ();
            int width = survey.width();
            int spawnX = survey.spawnX();
            int spawnZ = survey.spawnZ();
            long reach = (long) survey.radius() + SurveySavedData.MARGIN;
            int sea = survey.seaLevel();
            boolean faulty = surveyFault.test(from);
            return new Asked<>(from, count, attempt, CompletableFuture.supplyAsync(() -> {
                if (faulty) {
                    throw new IllegalStateException("a fault asked by a test at cell " + from);
                }
                return LandSurvey.cells(level, from, count, originX, originZ, width, spawnX, spawnZ, reach, sea);
            }, NpcPlanning.noise()));
        }

        /**
         * Whether a block whose look at the noise failed is asked again; after {@value #NOISE_ATTEMPTS} tries the plan stops.
         */
        private boolean askAgain(String what, Asked<?> asked, Throwable thrown) {
            Throwable cause = thrown.getCause() != null ? thrown.getCause() : thrown;
            if (asked.attempt() + 1 >= NOISE_ATTEMPTS) {
                fail(what + " failed on a thread of the noise at cell " + asked.from() + " " + NOISE_ATTEMPTS + " times: " + cause);
                return false;
            }
            LOGGER.warn("A block of {} from cell {} failed on a thread of the noise ({}): it is asked again", what, asked.from(), cause);
            return true;
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

        /**
         * The thrones: the candidates of a state are judged in the world in their order, pass after pass, on the threads of the
         * noise, a few ahead of the first not judged yet; the first that suits is the throne, as if they were judged one after
         * another.
         */
        private boolean thrones(long deadline) {
            if (!ready(1)) {
                return false;
            }
            StatePlan states = plans.states();
            int count = states.states().size();
            int[] thrones = progress.thrones();
            if (thrones.length != count) {
                thrones = new int[count];
                Arrays.fill(thrones, -1);
                progress.setThrones(thrones);
            }
            int perState = THRONE_PASSES * StatePlanner.THRONE_CANDIDATES;
            int cursor = progress.cursor();
            while (cursor < count * perState && System.nanoTime() - deadline < 0) {
                int s = cursor / perState;
                if (thrones[s] >= 0) {
                    cursor = (s + 1) * perState;
                    continue;
                }
                int next = valid(states, cursor);
                if (next != cursor) {
                    if (next >= (s + 1) * perState) {
                        LOGGER.warn("No throne candidate of the {} state {} suits a capital on any pass: the provinces take the first "
                                + "land of the state never generated a capital may stand on", states.state(s).level().id(), s);
                    }
                    cursor = next;
                    continue;
                }
                if (!verdicts.containsKey(cursor) && tooRough(states.raster(), candidate(states, cursor), states.state(s).level(), relax(cursor))) {
                    // The survey shows ground far too rough for the square of the capital: no look at the world is needed.
                    cursor++;
                    continue;
                }
                // The candidate and a few after it in the same state are asked of the threads of the noise.
                int ahead = cursor;
                for (int asked = 0; asked < THRONES_IN_FLIGHT && ahead < Math.min(count, s + 1) * perState; ) {
                    if (!verdicts.containsKey(ahead)) {
                        int cell = candidate(states, ahead);
                        if (ahead != cursor && tooRough(states.raster(), cell, states.state(s).level(), relax(ahead))) {
                            ahead = valid(states, ahead + 1);
                            continue;
                        }
                        verdicts.put(ahead, ask(states, s, cell, relax(ahead)));
                    }
                    asked++;
                    ahead = valid(states, ahead + 1);
                }
                CompletableFuture<Boolean> verdict = verdicts.get(cursor);
                if (!verdict.isDone()) {
                    break;
                }
                boolean suits;
                try {
                    suits = verdict.join();
                } catch (CompletionException | java.util.concurrent.CancellationException e) {
                    LOGGER.warn("A throne candidate of the state {} could not be judged: {}", s, String.valueOf(e.getCause()));
                    suits = false;
                }
                verdicts.remove(cursor);
                if (suits) {
                    thrones[s] = candidate(states, cursor);
                    progress.setThrones(thrones);
                    // The candidates asked after it are not needed any more.
                    verdicts.clear();
                    cursor = (s + 1) * perState;
                } else {
                    cursor++;
                }
            }
            progress.moveTo(cursor);
            return cursor >= count * perState;
        }

        private static int relax(int position) {
            return position % (THRONE_PASSES * StatePlanner.THRONE_CANDIDATES) / StatePlanner.THRONE_CANDIDATES;
        }

        private static int candidate(StatePlan states, int position) {
            int perState = THRONE_PASSES * StatePlanner.THRONE_CANDIDATES;
            return states.state(position / perState).throneCandidates().get(position % StatePlanner.THRONE_CANDIDATES);
        }

        /**
         * The first place from a place on that names a candidate of its state: past the candidates of a pass the next pass begins,
         * past the last pass the next state.
         */
        private static int valid(StatePlan states, int position) {
            int candidates = StatePlanner.THRONE_CANDIDATES;
            int perState = THRONE_PASSES * candidates;
            int s = position / perState;
            while (s < states.states().size() && position < (s + 1) * perState
                    && position % candidates >= states.state(s).throneCandidates().size()) {
                int relax = relax(position);
                position = relax + 1 < THRONE_PASSES ? s * perState + (relax + 1) * candidates : (s + 1) * perState;
            }
            return position;
        }

        /**
         * Asks the threads of the noise whether a capital of a state may stand at a cell on a pass: the guesses of the height first,
         * then the columns of the noise themselves.
         */
        private CompletableFuture<Boolean> ask(StatePlan states, int s, int cell, int relax) {
            ServerLevel level = server.overworld();
            NpcLevel kingdom = states.state(s).level();
            int x = states.raster().centerX(cell);
            int z = states.raster().centerZ(cell);
            return CompletableFuture.supplyAsync(() -> !guessedRough(level, x, z, kingdom, relax)
                    && NpcPlanning.suitableCapital(level, x, z, kingdom, relax), NpcPlanning.noise());
        }

        /**
         * Whether the dry cells of the survey under the square of a capital at a cell differ in height by far more than the capital
         * allows on a pass.
         */
        private static boolean tooRough(SurveyRaster raster, int cell, NpcLevel level, int relax) {
            // The cells the square reaches into from the middle of the cell of the throne.
            int reach = (NpcPlanning.capitalReach(level) - SurveyRaster.CELL / 2 + SurveyRaster.CELL - 1) / SurveyRaster.CELL;
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int x = raster.cellX(cell) + dx;
                    int z = raster.cellZ(cell) + dz;
                    if (!raster.contains(x, z)) {
                        continue;
                    }
                    int near = raster.index(x, z);
                    if (raster.terrain(near).dry() && raster.terrain(near) != io.github.sekerator.kingdomworld.core.land.TerrainClass.RIVER) {
                        low = Math.min(low, raster.groundHeight(near));
                        high = Math.max(high, raster.groundHeight(near));
                    }
                }
            }
            return high - low > NpcPlanning.capitalRoughness(level, relax) + ROUGH_SLACK;
        }

        /**
         * Whether the guesses of the height over the grid of a capital differ by far more than the capital allows on a pass, the
         * two lowest and highest left out: the judgement in the world would end the same, at a hundred times the cost.
         */
        private static boolean guessedRough(ServerLevel world, int x, int z, NpcLevel level, int relax) {
            int reach = NpcPlanning.capitalReach(level);
            int step = Math.max(8, reach / 3);
            List<Integer> heights = new ArrayList<>();
            for (int dx = -reach; dx <= reach; dx += step) {
                for (int dz = -reach; dz <= reach; dz += step) {
                    Integer guess = SurveyProbes.guess(world, x + dx, z + dz);
                    if (guess == null) {
                        return false;
                    }
                    heights.add(guess);
                }
            }
            heights.sort(Integer::compare);
            int trim = Math.min(2, heights.size() / 16);
            return heights.get(heights.size() - 1 - trim) - heights.get(trim) > NpcPlanning.capitalRoughness(level, relax) + GUESS_SLACK;
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

        /**
         * The borders along the water: the chunks of the cells to probe are looked at on the threads of the noise, a block of
         * cells at a time, and put in order; then the borders are drawn on the worker thread.
         */
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
            // The place is a chunk; the chunks of a cell are put together.
            int done = progress.cursor() / perCell;
            if (refineAsked < done) {
                refineBlocks.clear();
                refineAsked = done;
            }
            while (!refineBlocks.isEmpty() && refineBlocks.peek().answer().isDone() && System.nanoTime() - deadline < 0) {
                Asked<byte[]> asked = refineBlocks.poll();
                byte[] block;
                try {
                    block = asked.answer().join();
                } catch (CompletionException | java.util.concurrent.CancellationException e) {
                    // Asked again in its place, as a block of the survey is.
                    if (askAgain("the borders", asked, e)) {
                        refineBlocks.addFirst(askBorders(cells, asked.from(), asked.count(), asked.attempt() + 1));
                    }
                    progress.moveTo(done * perCell);
                    return false;
                }
                if (asked.from() != done || block.length != asked.count() * perCell) {
                    fail("a block of the borders for cell " + asked.from() + " came when the borders were at cell " + done);
                    progress.moveTo(done * perCell);
                    return false;
                }
                System.arraycopy(block, 0, probeClasses, done * perCell, block.length);
                done += block.length / perCell;
            }
            progress.moveTo(done * perCell);
            if (done < cells.length) {
                while (refineBlocks.size() < REFINE_IN_FLIGHT && refineAsked < cells.length) {
                    int from = refineAsked;
                    int count = Math.min(REFINE_BLOCK, cells.length - from);
                    refineBlocks.add(askBorders(cells, from, count, 0));
                    refineAsked += count;
                }
                return false;
            }
            return ready(3);
        }

        /**
         * Asks the threads of the noise to probe the chunks of a block of the cells along the water.
         */
        private Asked<byte[]> askBorders(int[] cells, int from, int count, int attempt) {
            SurveyRaster raster = plans.provinces().raster();
            ServerLevel level = server.overworld();
            int sea = survey.seaLevel();
            int perCell = SurveyRaster.CHUNKS_PER_CELL;
            int[] chunkX = new int[count];
            int[] chunkZ = new int[count];
            io.github.sekerator.kingdomworld.core.land.TerrainClass[] kinds = new io.github.sekerator.kingdomworld.core.land.TerrainClass[count];
            for (int i = 0; i < count; i++) {
                chunkX[i] = raster.chunkX(cells[from + i]);
                chunkZ[i] = raster.chunkZ(cells[from + i]);
                kinds[i] = raster.terrain(cells[from + i]);
            }
            boolean faulty = refineFault.test(from);
            return new Asked<>(from, count, attempt, CompletableFuture.supplyAsync(() -> {
                if (faulty) {
                    throw new IllegalStateException("a fault asked by a test at cell " + from);
                }
                byte[] block = new byte[count * perCell];
                for (int i = 0; i < count; i++) {
                    System.arraycopy(LandSurvey.probeCell(level, chunkX[i], chunkZ[i], sea, kinds[i]), 0, block, i * perCell, perCell);
                }
                return block;
            }, NpcPlanning.noise()));
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
                // A place a tick: the founding of each goes on in the work of the kingdoms of the world. What the land at the
                // thrones is like (a look at the noise, and the table of materials the first time) is asked off the tick.
                ServerLevel level = server.overworld();
                for (int i = cursor; i < count; i++) {
                    Entry ask = entries.get(i);
                    lands.computeIfAbsent(i, k -> CompletableFuture.supplyAsync(() -> NpcPlanning.siteLand(level, ask.x, ask.z),
                            NpcPlanning.noise()));
                }
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
                        CompletableFuture<NpcPlanning.SiteLand> land = lands.get(cursor);
                        if (!land.isDone()) {
                            return false;
                        }
                        NpcPlanning.SiteLand known;
                        try {
                            known = land.join();
                        } catch (CompletionException | java.util.concurrent.CancellationException e) {
                            fail("the land of a throne could not be looked at: " + (e.getCause() != null ? e.getCause() : e));
                            return false;
                        }
                        site = NpcPlanning.addSite(server, entry.x, entry.z, entry.level, entry.seed, List.of(), known);
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
                // A few provinces a tick: every change of a holder is told to those who listen.
                long day = Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
                int end = Math.min(entry.provinces.length, givenOfState + GIVEN_PER_TICK);
                for (int i = givenOfState; i < end; i++) {
                    int id = entry.provinces[i];
                    try {
                        territory.transferProvince(id, owner.get(), day);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("The province {} of the state at {} {} is not in the map: it is not given", id, entry.x, entry.z);
                    }
                }
                givenOfState = end;
                if (end < entry.provinces.length) {
                    return false;
                }
                givenOfState = 0;
                LOGGER.info("The state of the world {} holds its {} province(s)", site.get(), territory.provincesOf(owner.get()).size());
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
