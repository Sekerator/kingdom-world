package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.command.PerfCommands;
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
import io.github.sekerator.kingdomworld.core.npc.BackgroundPace;
import io.github.sekerator.kingdomworld.core.npc.GuildHouses;
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
    /** Ticks between two saves of the world by the game itself. */
    private static final int AUTOSAVE_TICKS = 6_000;
    /** Time a part that goes by time gets in a small step of the background. */
    private static final long SMALL_STEP_NANOS = 2_500_000L;
    /** Whether the step being made is a small one. */
    private static boolean smallStep;

    /**
     * The time a part of a step that goes by time gets: less in a small step.
     */
    private static long budget(long nanos) {
        return smallStep ? Math.min(nanos, SMALL_STEP_NANOS) : nanos;
    }
    /** Time of a tick the looks at the workplaces of a kingdom that just stood take at most, before its chunks go. */
    private static final long SURVEY_NANOS = 10_000_000L;
    /** Chunks of land claimed in a tick for every chunk the rules let an appearance look through. */
    private static final int CLAIMS_PER_CHUNK_BUDGET = 2;
    /** Blocks of a building done in a tick at least, so that a small budget still moves on. */
    private static final int BLOCKS_PER_PART = 64;
    /** Buildings being laid out over several ticks, by kingdom, template and place. */
    private static final Map<String, ConstructionWork.Laying> LAYING = new java.util.HashMap<>();
    /** Time a step gives the laying out of a building. */
    private static final long LAY_NANOS = 8_000_000L;
    /** Time a step gives the citizens who join the army. */
    private static final long ARMY_NANOS = 6_000_000L;
    /** Blocks of the wall paid in a tick at most: the roads place the paid columns once a second, all at once. */
    private static final int WALL_BLOCKS_PER_TICK = 48;
    /** Columns of ramp around a levelled square, so that it does not end with a step a road cannot climb. */
    static final int RAMP = 6;
    /** Citizens added in a tick at most. */
    private static final int CITIZENS_PER_TICK = 4;
    /** Time a step gives the posts that take their citizens. */
    private static final long PEOPLE_NANOS = 6_000_000L;
    /** Positions of the town plots around the town hall: x, z and where the front looks. */
    private static final int[][] TOWN_PLOTS = {{0, -12, 0}, {12, 0, 1}, {0, 12, 2}, {-12, 0, 3}, {12, -12, 0}, {-12, 12, 2}};

    private static Job job;
    /** Times the raising of each place stopped with a failure since the server started. */
    private static final Map<Integer, Integer> FAILURES = new java.util.HashMap<>();
    /** The game time a place whose raising stopped with a failure may go on from. */
    private static final Map<Integer, Long> RETRY_AT = new java.util.HashMap<>();
    /** Ticks a raising that stopped with a failure waits before it goes on: a day of the game. */
    static final long RETRY_TICKS = 24_000;
    /** Failures of one place after which every further one is told loudly. */
    private static final int LOUD_FAILURES = 3;

    /**
     * Whether the raising of a place may start now: one that stopped with a failure waits a day of the game before it goes on.
     */
    public static boolean mayRetry(MinecraftServer server, NpcSite site) {
        Long at = RETRY_AT.get(site.id());
        return at == null || server.overworld().getGameTime() >= at;
    }

    /**
     * Lets a place whose raising stopped with a failure go on at once; for operators and game tests.
     */
    public static void forgetFailures(int siteId) {
        FAILURES.remove(siteId);
        RETRY_AT.remove(siteId);
    }
    /** Ticks the background waits without players before its chunks go back to the game: five minutes. */
    private static final int ALONE_TICKS = 6_000;
    /** The time the parts of the step being made took, for the log of a slow tick. */
    private static final Map<String, Long> PARTS = new java.util.LinkedHashMap<>();

    /**
     * Counts the time a part of the step being made took.
     */
    private static void part(String name, long startedNanos) {
        PARTS.merge(name, System.nanoTime() - startedNanos, Long::sum);
    }
    /** Whether the next job starts in the background. */
    private static boolean startBackground;
    /** Whether the next job in the background goes on without players (an operator asked for it). */
    private static boolean startForced;

    /**
     * What an appearance keeps while it runs; rebuilt from the place after a restart.
     */
    private static final class Job {
        final int siteId;
        final NpcComposition composition;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Set<Long> ticketed = new LinkedHashSet<>();
        /**
         * The chunks of the ways to the towns alone (beyond the land of the capital and the squares of the towns): asked for only
         * while the stages that read them run, the look at the land and the roads to the end.
         */
        final List<ChunkPos> ways = new ArrayList<>();
        /** Whether the chunks of the ways are among the chunks asked for now. */
        boolean waysHeld = true;
        /** Ticks the background waited in a row without players. */
        int alone;
        /** Whether the buildings of the plan were looked for in the register once the last of them was raised. */
        boolean rechecked;
        /** The plots of the capital raised again, the others standing already; {@code null} while every plot is raised. */
        Set<Integer> only;
        /** Chunks with a ticket that are not loaded yet. */
        final List<ChunkPos> pending = new ArrayList<>();
        int next;
        int waited;
        long ticks;
        long sumNanos;
        long maxNanos;
        SpawnStage worstStage = SpawnStage.LOAD;
        SpawnStage lastStage = SpawnStage.LOAD;
        /** The time the last step of this work took itself, and of its parts, for the log of a slow tick. */
        long lastOwnNanos;
        String lastParts = "";
        /** Whether the last tick only waited: for chunks to load, or for the roads and the wall to be placed. */
        boolean lastWaited;
        /** Whether this tick only waits for what others place. */
        boolean idle;
        boolean searching;
        boolean loaded;
        List<Long> land;
        int claimIndex;
        boolean claimedInPass;
        int claimed;
        /** Whether the buildings go up in the background with the small budget (no player watches). */
        boolean background;
        /** The share of the blocks of the rules a step places now, as the time of the steps allows. */
        double scale = 0.25;
        /** Ticks the raising waited for the server or for players. */
        long paused;
        /** Whether the background did nothing in the last tick: that tick is not its own. */
        boolean pausedLast;
        /** Whether the last tick of the background was a small step in a tick that was long before it. */
        boolean smallLast;
        /** Whether the background works in a tick, by how long the tick took before it came to it. */
        final BackgroundPace pace = new BackgroundPace();
        /** The citizen the settling after the army goes on from. */
        int settled;
        /** The post the hiring of the citizens goes on from. */
        int hireIndex;
        /** Whether the background goes on without players. */
        boolean forced;
        /** Work posts still to be looked at before the chunks go; null before the kingdom settles. */
        java.util.ArrayDeque<WorkPost> surveys;

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
        LAYING.clear();
        RELEASING.clear();
        WAITING_SINCE.clear();
        waitedForLand = false;
        TownRoads.reset();
    }

    /**
     * Forgets what the running appearance keeps in memory, as a restart of the server does; for game tests.
     */
    public static void resetForTests() {
        job = null;
        LAYING.clear();
        RELEASING.clear();
        WAITING_SINCE.clear();
        waitedForLand = false;
    }

    public static Timing lastTiming() {
        return lastTiming;
    }

    /**
     * Starts the appearance of a planned place.
     */
    public static void start(MinecraftServer server, NpcSite site) {
        start(server, site, false);
    }

    /**
     * Starts raising a place in the world: a place only planned appears from the beginning, a place founded as data goes on with
     * its buildings.
     *
     * @param background whether it goes up with the small budget of the background
     */
    public static void start(MinecraftServer server, NpcSite site, boolean background) {
        if (job != null) {
            releaseTickets(server.overworld());
        }
        site.setStatus(NpcSite.Status.SPAWNING);
        job = null;
        startBackground = background;
        startForced = false;
    }

    /**
     * Lets the raising started last go on in the background without players, for operators measuring it.
     */
    static void forceBackground() {
        startForced = true;
        if (job != null) {
            job.forced = true;
        }
    }

    /**
     * Puts the raising of a place aside for another one: the place keeps its stage and step and goes on later from there.
     */
    public static void putAside(MinecraftServer server, NpcSite site) {
        if (job != null && job.siteId == site.id()) {
            releaseTickets(server.overworld());
            job = null;
        }
        site.setStatus(site.founded() || site.kingdomId().isPresent() ? NpcSite.Status.FOUNDED : NpcSite.Status.PLANNED);
    }

    /**
     * Whether the running raising goes with the small budget of the background.
     */
    public static boolean inBackground() {
        return job != null && job.background;
    }

    /**
     * Lets the running raising use the full budget (a player came to watch) or go back to the small one.
     */
    public static void setBackground(boolean value) {
        if (job != null) {
            job.background = value;
        }
        startBackground = value;
    }

    /**
     * Blocks the raising places, clears or levels in a tick at most.
     */
    static int blockBudget() {
        int configured = job != null && job.background ? Math.min(NpcSettings.build().backgroundBlocksPerTick(), NpcSettings.rules().blocksPerTick())
                : NpcSettings.rules().blocksPerTick();
        // What the blocks cost differs from stage to stage and from place to place (a hall of many blocks with light and
        // contents, a field, a forest to clear): the budget follows the time the steps took.
        return job == null ? configured : Math.max(MIN_BLOCKS, (int) Math.round(configured * job.scale));
    }

    /** The time a step of the work of an appearance aims at. */
    private static final long STEP_NANOS = 8_000_000L;
    /** Blocks a step may always place, however slow the blocks were. */
    private static final int MIN_BLOCKS = 64;
    /** The largest share of the blocks of the rules a step places: a step of many cheap blocks may meet a costly building next. */
    private static final double MAX_SCALE = 0.5;

    /**
     * Follows the time a step of the work took: a step that took longer than {@link #STEP_NANOS} halves the blocks of the next
     * ones, a quick one gives them back a quarter at a time.
     */
    private static void pace(Job job, long nanos) {
        if (nanos > STEP_NANOS) {
            job.scale = Math.max(0.01, job.scale * Math.max(0.1, 0.5 * STEP_NANOS / (double) nanos));
        } else if (nanos < STEP_NANOS / 2) {
            job.scale = Math.min(MAX_SCALE, job.scale * 1.25);
        }
    }

    /**
     * Chunks the raising asks for or looks through in a tick at most.
     */
    static int chunkBudget() {
        return job != null && job.background ? Math.min(NpcSettings.build().backgroundChunksPerTick(), NpcSettings.rules().chunksPerTick())
                : NpcSettings.rules().chunksPerTick();
    }

    /** Compositions kept at most: the garrisons and the views ask for the same few places again and again. */
    private static final int COMPOSITIONS = 16;
    /** The compositions asked for last, by everything they are made from; the one asked for longest ago goes first. */
    private static final Map<CompositionKey, NpcComposition> COMPOSED = new java.util.LinkedHashMap<>(COMPOSITIONS * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CompositionKey, NpcComposition> eldest) {
            return size() > COMPOSITIONS;
        }
    };

    /**
     * Everything a composition is made from: the place (its id, layout, level, seed and how many towns it has) and the values of
     * the rules its layout reads; the road rules count for the first layout only.
     */
    private record CompositionKey(int site, int layout, io.github.sekerator.kingdomworld.core.npc.NpcLevel level, long seed, int towns,
                                  int rulesTowns, int soldiers, int guards, int army, int townGuards, int townPeople, int townBuildings,
                                  int cityPeople, int cityBuildings, int wallRadius, String style, String guildRank) {
    }

    /**
     * The composition of the kingdom of a place, from its seed and the rules of the server. The same place under the same rules
     * gets the same composition, which cannot be changed, without laying it out again.
     */
    public static NpcComposition composition(NpcSite site) {
        NpcRules rules = NpcSettings.rules();
        io.github.sekerator.kingdomworld.core.npc.NpcLevel level = site.level();
        int towns = site.towns().size();
        CompositionKey key;
        RoadRules roads = null;
        int wallRadius = 0;
        if (site.styled()) {
            key = new CompositionKey(site.id(), site.layout(), level, site.seed(), towns, rules.towns(level), rules.soldiers(level),
                    rules.guards(level), rules.army(level), rules.townGuards(level), 0, 0, 0, 0, 0, site.style(),
                    GuildHouses.of(site).name());
        } else if (site.districts()) {
            key = new CompositionKey(site.id(), site.layout(), level, site.seed(), towns, rules.towns(level), rules.soldiers(level),
                    rules.guards(level), rules.army(level), rules.townGuards(level), 0, 0, 0, 0, 0, "", "");
        } else {
            roads = RoadSettings.rules();
            // The capital is laid out for walls of at least the default radius; a wall of a smaller one leaves out what stands in
            // its way.
            wallRadius = Math.max(roads.wallRadius(), MIN_WALL_RADIUS);
            key = new CompositionKey(site.id(), site.layout(), level, site.seed(), towns, rules.towns(level), rules.soldiers(level),
                    rules.guards(level), rules.army(level), rules.townGuards(level), roads.townPeople(), roads.townBuildings(),
                    roads.cityPeople(), roads.cityBuildings(), wallRadius, "", "");
        }
        synchronized (COMPOSED) {
            NpcComposition known = COMPOSED.get(key);
            if (known != null) {
                return known;
            }
        }
        NpcComposition made;
        if (site.styled()) {
            made = NpcComposition.styled(level, site.seed(), site.style(), GuildHouses.of(site), rules, towns);
        } else if (roads == null) {
            made = NpcComposition.districts(level, site.seed(), rules, towns);
        } else {
            // The work is planned for the towns the place has: a place without room for its towns gets no work for their people.
            made = NpcComposition.of(level, site.seed(), rules, roads.townPeople(), roads.townBuildings(), roads.cityPeople(),
                    roads.cityBuildings(), wallRadius, towns);
        }
        synchronized (COMPOSED) {
            COMPOSED.put(key, made);
        }
        return made;
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
        releaseSome(server.overworld());
        TownRoads.tick(server);
        Optional<NpcSite> spawning = NpcWork.state(server).spawning();
        if (spawning.isEmpty()) {
            if (job != null) {
                releaseTickets(server.overworld());
                job = null;
            }
            return;
        }
        NpcSite site = spawning.get();
        if (job == null || job.siteId != site.id()) {
            if (job != null) {
                releaseTickets(server.overworld());
            }
            job = newJob(site);
            if (job == null) {
                return;
            }
            job.background = startBackground;
            job.forced = startForced;
        }
        if (job.background && NpcBackground.waiting(server, job.forced)) {
            // The background waits for players and for short ticks; its chunks stay asked for while players come and go, and go
            // back to the game once nobody played for a while: they are asked for again, a few at a time, when the work goes on.
            job.paused++;
            job.pausedLast = true;
            job.alone = NpcBackground.playersOnline(server) ? 0 : job.alone + 1;
            if (job.alone == ALONE_TICKS && !job.ticketed.isEmpty() && site.stage() != SpawnStage.DONE) {
                LOGGER.info("Nobody plays: the {} chunks of {} go back to the game until its appearance goes on", job.ticketed.size(), site);
                releaseTickets(server.overworld());
                job.loaded = false;
            }
            return;
        }
        job.alone = 0;
        boolean small = false;
        if (job.background) {
            // A tick that took long before the work came to it is left to the rest of the server, but never for good: after a
            // run of them the background goes on with a small step.
            BackgroundPace.Step decided = job.pace.decide(PerfCommands.sinceTickStart());
            int[] minute = job.pace.minuteOver();
            if (minute != null && minute[1] > 0) {
                LOGGER.info("The background appearance of {} is short of time: {} of {} ticks of the last minute took more than {} ms before it "
                        + "came to them, it went on with {} small step(s)", site, minute[0], minute[2], BackgroundPace.LATE_NANOS / 1_000_000L, minute[1]);
            }
            if (decided == BackgroundPace.Step.SKIP) {
                job.paused++;
                job.pausedLast = true;
                return;
            }
            small = decided == BackgroundPace.Step.SMALL;
        }
        // The tick an appearance starts in belongs to what started it (a player coming near, a command looking for land), and the
        // tick the game saves the world in (every 6000 ticks) to the save. A tick in which the appearance only waited for the
        // game to load or generate its chunks is the game's own: it is counted in the average and told of in the log, but it is
        // not the worst tick of the work of the appearance (the chunks are asked for a few at a time for that reason). Neither
        // is a tick that was long before a small step came to it.
        int last = server.getTickCount() - 1;
        boolean own = !job.pausedLast && !job.smallLast;
        job.pausedLast = false;
        job.smallLast = small;
        if (own && job.ticks > 1 && last % AUTOSAVE_TICKS != 0) {
            // The last tick as the game and this work made it: the game's own time of a tick leaves out the handlers of END in which
            // this work runs, and the whole tick holds the work of every other system too.
            long nanos = PerfCommands.lastWorkTick();
            if (nanos < 0) {
                nanos = server.tickTimes[Math.floorMod(last, server.tickTimes.length)];
            }
            job.sumNanos += nanos;
            // The garbage collector holds the whole server whenever it comes: its pause in a tick is not the work of the tick, and
            // the worst tick of an appearance is told without it, as the turns of the rulers are.
            long gc = Math.min(nanos, PerfCommands.lastGc());
            long work = nanos - gc;
            if (job.lastWaited) {
                if (work > SLOW_TICK_NANOS) {
                    LOGGER.info("A tick in which the chunks of {} were loading took {} ms ({} ms of it the garbage collector)", site,
                            String.format(java.util.Locale.ROOT, "%.1f", nanos / 1e6), gc / 1_000_000L);
                }
            } else {
                if (work > job.maxNanos) {
                    job.maxNanos = work;
                    job.worstStage = job.lastStage;
                }
                if (work > SLOW_TICK_NANOS) {
                    LOGGER.info("A tick of the appearance of {} took {} ms ({} ms of it the garbage collector, {} ms the appearance itself{}) at {} "
                            + "step {}", site, String.format(java.util.Locale.ROOT, "%.1f", nanos / 1e6), gc / 1_000_000L,
                            String.format(java.util.Locale.ROOT, "%.1f", job.lastOwnNanos / 1e6), job.lastParts, job.lastStage.id(), site.step());
                }
            }
        }
        job.ticks++;
        job.lastStage = site.stage();
        ServerLevel level = server.overworld();
        Job running = job;
        running.idle = false;
        try {
            long started = System.nanoTime();
            double scale = running.scale;
            smallStep = small;
            if (small) {
                // The fewest blocks, and the parts that go by time get little of it.
                running.scale = 0;
            }
            PARTS.clear();
            try {
                step(server, level, site, running);
            } finally {
                smallStep = false;
                running.lastOwnNanos = System.nanoTime() - started;
                StringBuilder parts = new StringBuilder();
                PARTS.forEach((name, spent) -> parts.append(", ").append(name).append(' ')
                        .append(String.format(java.util.Locale.ROOT, "%.1f", spent / 1e6)));
                running.lastParts = parts.toString();
            }
            if (small) {
                running.scale = scale;
            } else {
                pace(running, System.nanoTime() - started);
            }
            // The last step of an appearance lets the job go.
            running.lastWaited = !running.loaded || running.idle;
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
                job = newJob(site);
                if (job == null) {
                    return;
                }
            }
            if (!job.loaded) {
                for (ChunkPos chunk : job.chunks) {
                    server.overworld().getChunk(chunk.x, chunk.z);
                }
            }
            if (site.stage() == SpawnStage.ROADS && job.searching) {
                // No tick passes between these steps: the search reads its chunks at once.
                RoadPlanning.tickSearchNow(server);
            }
            if (site.stage() == SpawnStage.DONE) {
                site.kingdomId().ifPresent(id -> {
                    for (Road road : RoadWork.state(server).roads()) {
                        if (road.kingdomId().equals(id)) {
                            io.github.sekerator.kingdomworld.common.road.RoadBuilding.placeNow(server, road);
                        }
                    }
                    for (Wall wall : List.copyOf(RoadWork.state(server).walls())) {
                        if (wall.kingdomId().equals(id)) {
                            CityWalls.placeNow(server, wall);
                        }
                    }
                });
            }
            // Game tests measure no ticks: the whole budget every step.
            job.scale = 1.0;
            step(server, server.overworld(), site, job);
        }
    }

    /**
     * The job of a place, or {@code null} if it cannot be made (the place is left alone, instead of failing every tick).
     */
    private static Job newJob(NpcSite site) {
        try {
            Job made = new Job(site.id(), composition(site));
            List<ChunkPos> all = chunks(site, made.composition);
            Set<Long> squares = squares(site);
            for (ChunkPos chunk : all) {
                if (squares.contains(chunk.toLong())) {
                    made.chunks.add(chunk);
                } else {
                    made.ways.add(chunk);
                }
            }
            made.waysHeld = needsWays(site, made.composition);
            if (made.waysHeld) {
                made.chunks.addAll(made.ways);
            }
            return made;
        } catch (RuntimeException e) {
            LOGGER.error("The appearance of the kingdom of the world at {} could not be prepared", site, e);
            site.block("failed: " + e);
            return null;
        }
    }

    private static void step(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        takeWaitedForLand();
        try {
            stepStage(server, level, site, job);
        } finally {
            if (takeWaitedForLand()) {
                // A building found its land gone: the chunks are looked at again from the first and asked for where they went.
                job.loaded = false;
                job.next = 0;
                job.idle = true;
            }
        }
    }

    /** Whether the next step of an appearance fails, as a fault of a stage would; for game tests. */
    private static boolean failNext;

    /**
     * Makes the next step of the running appearance fail; for game tests.
     */
    public static void failNextStepForTests() {
        failNext = true;
    }

    /**
     * Lets go of the tickets of the chunks of the running appearance at once while it still counts them as its own, as when another
     * raising of the same place let them go; for game tests.
     */
    public static int dropTicketsForTests(ServerLevel level) {
        if (job == null) {
            return 0;
        }
        for (long key : job.ticketed) {
            ChunkPos chunk = new ChunkPos(key);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
        }
        return job.ticketed.size();
    }

    /**
     * How many chunks the running appearance counts as its own are not loaded now; for game tests.
     */
    public static int unloadedForTests(ServerLevel level) {
        if (job == null) {
            return 0;
        }
        int unloaded = 0;
        for (long key : job.ticketed) {
            unloaded += level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null ? 1 : 0;
        }
        return unloaded;
    }

    private static void stepStage(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("a fault made for a game test at " + site.stage().id());
        }
        fitWays(site, job);
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
            case LAW -> law(server, site, job);
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
        int r = NpcCity.landRadius(site) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                keys.add(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        int around = NpcCity.townRadius(site) + 1;
        for (NpcSite.Town town : site.towns()) {
            int tx = town.x() >> 4;
            int tz = town.z() >> 4;
            for (int dx = -around; dx <= around; dx++) {
                for (int dz = -around; dz <= around; dz++) {
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

    /**
     * The chunks of the land of the capital and of the squares of the towns, as {@link #chunks} counts them: what the stages of an
     * appearance build on.
     */
    static Set<Long> squares(NpcSite site) {
        Set<Long> keys = new java.util.HashSet<>();
        int cx = site.x() >> 4;
        int cz = site.z() >> 4;
        int r = NpcCity.landRadius(site) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                keys.add(ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        int around = NpcCity.townRadius(site) + 1;
        for (NpcSite.Town town : site.towns()) {
            int tx = town.x() >> 4;
            int tz = town.z() >> 4;
            for (int dx = -around; dx <= around; dx++) {
                for (int dz = -around; dz <= around; dz++) {
                    keys.add(ChunkPos.asLong(tx + dx, tz + dz));
                }
            }
        }
        return keys;
    }

    /**
     * How many chunks the appearance of a place asks for: all of them, and those of the land of the capital and of the towns alone,
     * which the stages between the look at the land and the roads hold; for the stand of the load.
     */
    public static int[] chunkCounts(NpcSite site) {
        List<ChunkPos> all = chunks(site, composition(site));
        Set<Long> squares = squares(site);
        int held = 0;
        for (ChunkPos chunk : all) {
            if (squares.contains(chunk.toLong())) {
                held++;
            }
        }
        return new int[]{all.size(), held};
    }

    /**
     * How many chunks the running appearance holds a ticket for now; for game tests.
     */
    public static int heldChunks() {
        return job == null ? 0 : job.ticketed.size();
    }

    /**
     * Whether a stage reads the chunks of the ways to the towns: the look at the land, the roads and what comes after them while
     * their columns are placed.
     */
    static boolean needsWays(NpcSite site, NpcComposition composition) {
        return switch (site.stage()) {
            case LOAD, CHECK, ROADS, LAW, DONE -> true;
            // A capital of districts has its wall before its roads; another one after them.
            case WALL -> !composition.districts();
            default -> false;
        };
    }

    /**
     * Asks for the chunks of the ways to the towns when a stage that reads them comes, and lets them go while the stages between
     * the look at the land and the roads (the longest of an appearance) build only on the land of the capital and the towns.
     */
    private static void fitWays(NpcSite site, Job job) {
        boolean needed = needsWays(site, job.composition);
        if (needed == job.waysHeld || job.ways.isEmpty()) {
            job.waysHeld = needed;
            return;
        }
        job.waysHeld = needed;
        if (needed) {
            // They come back with the tickets, a few at a time, before the stage goes on.
            job.chunks.addAll(job.ways);
            job.loaded = false;
            return;
        }
        Set<Long> ways = new java.util.HashSet<>();
        for (ChunkPos chunk : job.ways) {
            ways.add(chunk.toLong());
        }
        job.chunks.removeIf(chunk -> ways.contains(chunk.toLong()));
        job.pending.removeIf(chunk -> ways.contains(chunk.toLong()));
        for (long chunk : ways) {
            if (job.ticketed.remove(chunk)) {
                RELEASING.add(chunk);
            }
        }
        // The chunks still asked for are looked at again from the first.
        job.next = 0;
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
        // own pace instead of finishing many in one tick. A chunk counts once the game has it whole: one it only holds a ticket
        // for may still be loading or generating, and a step that reads a block of it would wait for it in the tick.
        job.pending.removeIf(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        int waiting = Math.max(1, chunkBudget());
        while (job.next < job.chunks.size() && job.pending.size() < waiting) {
            ChunkPos chunk = job.chunks.get(job.next++);
            boolean absent = level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null;
            // A chunk asked for again while its ticket waits to go keeps it, and one that went away though the job held it (its
            // ticket was let go by another raising of the same place) is asked for again: a ticket given twice is one ticket.
            if (job.ticketed.add(chunk.toLong()) || absent) {
                RELEASING.remove(chunk.toLong());
                level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
            }
            if (absent) {
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
        int r = NpcCity.landRadius(site);
        // A kingdom founded as data holds its land already; only a place still to be founded looks at its neighbours.
        if (state.kingdoms().get(own).isEmpty()
                && state.territory().hasForeignLandWithin(dimension, ChunkKey.of(site.x() >> 4, site.z() >> 4), r + buffer, own)) {
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
        List<NpcSite.Town> kept = new ArrayList<>();
        for (NpcSite.Town town : site.towns()) {
            String problem = townProblem(level, state, site, town, own, buffer, limit);
            if (problem == null) {
                kept.add(town);
            } else {
                LOGGER.info("The town at {} {} of {} is left out: {}", town.x(), town.z(), site, problem);
            }
        }
        if (kept.size() != site.towns().size()) {
            site.retainTowns(kept);
        }
        if (site.groundY() == 0) {
            site.setGroundY(site.districts() ? wideHeight(level, site.x(), site.z(), NpcComposition.capitalWall(site.level(), site.layout()))
                    : plazaHeight(level, site.x(), site.z()));
        }
        site.advance(SpawnStage.FOUND);
    }

    /**
     * Why a town cannot be: a chunk of its square or of the way to it lies near the land of another kingdom or players spent
     * longer there than the rules allow; {@code null} if it can.
     */
    private static String townProblem(ServerLevel level, KingdomWorldState state, NpcSite site, NpcSite.Town town, UUID own, int buffer,
                                      long limit) {
        String dimension = Dimensions.id(level);
        int r = NpcCity.landRadius(site);
        for (long key : NpcPlanning.townChunks(site.x() >> 4, site.z() >> 4, r, town.x(), town.z())) {
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (state.territory().hasForeignLandWithin(dimension, ChunkKey.of(x, z), buffer, own)) {
                return "the land of another kingdom is near chunk [" + x + ", " + z + "]";
            }
            LevelChunk loaded = level.getChunkSource().getChunkNow(x, z);
            if (loaded == null) {
                loaded = level.getChunk(x, z);
            }
            if (loaded.getInhabitedTime() > limit) {
                return "players spent " + loaded.getInhabitedTime() / 1200 + " minutes in chunk [" + x + ", " + z + "]";
            }
        }
        return null;
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
     * The height a wide square is levelled to: the middle of the heights of the ground over all of it, so that about as much is
     * cut as is filled.
     *
     * @param reach half the side of the square
     */
    static int wideHeight(ServerLevel level, int x, int z, int reach) {
        List<Integer> heights = new ArrayList<>();
        int step = Math.max(4, reach / 4);
        for (int dx = -reach; dx <= reach; dx += step) {
            for (int dz = -reach; dz <= reach; dz += step) {
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
        releaseTickets(level);
        job = null;
        if (site.kingdomId().isPresent() && KingdomWorldApi.kingdom(level.getServer(), site.kingdomId().get()).isPresent()) {
            if (site.stage() == SpawnStage.DONE) {
                LOGGER.warn("The buildings of the kingdom of the world at {} are not raised: {}", site, reason);
                site.setStatus(NpcSite.Status.SPAWNED);
                return;
            }
            // A kingdom founded as data stays with its land and ruler, and its raising waits a day and goes on from where it
            // stopped: a capital is never left standing half raised for good.
            int failures = FAILURES.merge(site.id(), 1, Integer::sum);
            RETRY_AT.put(site.id(), level.getGameTime() + RETRY_TICKS);
            site.hold(reason);
            if (failures >= LOUD_FAILURES) {
                LOGGER.error("The raising of the kingdom of the world at {} stopped {} times at {} step {}: {}; it goes on again in a day",
                        site, failures, site.stage().id(), site.step(), reason);
            } else {
                LOGGER.warn("The raising of the kingdom of the world at {} stopped at {} step {}: {}; it goes on again in a day", site,
                        site.stage().id(), site.step(), reason);
            }
            return;
        }
        LOGGER.warn("The kingdom of the world at {} does not appear: {}", site, reason);
        site.block(reason);
    }

    private static void releaseTickets(ServerLevel level) {
        if (job == null) {
            return;
        }
        // The chunks of an appearance are let go a few a tick: the game saves a chunk it lets go of in the tick it unloads it,
        // and hundreds let go at once would be saved in a few long ticks.
        RELEASING.addAll(job.ticketed);
        job.ticketed.clear();
        job.pending.clear();
        job.next = 0;
    }

    /** Chunks of appearances that ended, whose tickets go a few a tick. */
    private static final Set<Long> RELEASING = new LinkedHashSet<>();
    /** Ticks between two chunks let go of: a chunk the game unloads it saves in the same tick. */
    private static final int RELEASE_EVERY = 2;

    /**
     * Lets go of a chunk of an appearance that ended every few ticks.
     */
    static void releaseSome(ServerLevel level) {
        if (level.getServer().getTickCount() % RELEASE_EVERY == 0 && !RELEASING.isEmpty()) {
            java.util.Iterator<Long> first = RELEASING.iterator();
            ChunkPos chunk = new ChunkPos(first.next());
            first.remove();
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
        }
    }

    // ---------------------------------------------------------------- the kingdom and its land

    private static void found(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        NpcState npc = NpcWork.state(server);
        UUID id = kingdomId(server, site);
        KingdomWorldState state = KingdomWorldApi.state(server);
        BlockPos throne = new BlockPos(site.x(), site.groundY() + 1, site.z());
        if (state.kingdoms().get(id).isEmpty()) {
            String refused = foundingProblem(level, state, throne);
            if (refused != null) {
                stop(level, site, refused);
                return;
            }
            placeThrone(level, throne);
            String problem = createKingdom(server, level, site, id, throne);
            if (problem != null) {
                level.setBlockAndUpdate(throne, Blocks.AIR.defaultBlockState());
                stop(level, site, problem);
                return;
            }
        } else if (npc.kingdom(id).isEmpty()) {
            // The kingdom stands but its record was lost: a kingdom of the world is never left without one.
            Kingdom existing = state.kingdoms().get(id).orElseThrow();
            NpcNames.Name name = new NpcNames.Name(existing.name(), existing.name());
            npc.putKingdom(NpcKingdom.create(id, site, name, List.of(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server)));
        }
        if (state.kingdoms().get(id).isPresent() && !level.getBlockState(throne).is(ModRegistries.THRONE.get())) {
            // A kingdom founded as data gets its throne when its place is raised.
            placeThrone(level, throne);
        }
        if (site.kingdomId().isEmpty()) {
            // A place appearing again (an operator spawned it anew) finds its kingdom by its id.
            site.setKingdomId(id);
        }
        if (claimLand(level, site, id, job)) {
            site.advance(SpawnStage.GROUND);
        }
    }

    /**
     * The throne on a block of stone bricks with air above it.
     */
    private static void placeThrone(ServerLevel level, BlockPos throne) {
        level.setBlock(throne.below(), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        for (int y = 0; y < 3; y++) {
            level.setBlock(throne.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
    }

    /**
     * Founds the kingdom of a place with its names, its ruler and the names of its towns; no block is touched.
     *
     * @return why it was refused, or {@code null} once it stands
     */
    static String createKingdom(MinecraftServer server, ServerLevel level, NpcSite site, UUID id, BlockPos throne) {
        return createKingdom(server, level, site, id, throne, false);
    }

    /**
     * @param asData whether the kingdom is founded as data before its buildings are raised
     */
    static String createKingdom(MinecraftServer server, ServerLevel level, NpcSite site, UUID id, BlockPos throne, boolean asData) {
        NpcState npc = NpcWork.state(server);
        KingdomWorldState state = KingdomWorldApi.state(server);
        Set<String> taken = new java.util.HashSet<>();
        state.kingdoms().all().forEach(kingdom -> taken.add(KingdomNames.key(kingdom.name())));
        NpcNames.Name name = NpcNames.kingdom(site.seed(), candidate -> taken.contains(KingdomNames.key(candidate)));
        CreateKingdomResult result = KingdomWorldApi.foundWithId(level, throne, name.latin(), color(state, site), id);
        if (!(result instanceof CreateKingdomResult.Created)) {
            return "the kingdom was refused: " + result;
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
        NpcGuilds.found(server, site, kingdom);
        LOGGER.info(asData ? "The kingdom of the world {} ({}) is founded at {}: {} ruled by {} ({}, {})"
                        : "The kingdom of the world {} ({}) appears at {}: {} ruled by {} ({}, {})", name.latin(), name.cyrillic(),
                throne.toShortString(), site.level().id(), kingdom.rulerName(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN),
                kingdom.character().id(), kingdom.traits());
        return null;
    }

    /**
     * Why a kingdom cannot be founded with its throne at a place, checked before any block is touched; {@code null} if it can.
     */
    private static String foundingProblem(ServerLevel level, KingdomWorldState state, BlockPos throne) {
        String dimension = Dimensions.id(level);
        long chunk = ChunkKey.of(throne.getX() >> 4, throne.getZ() >> 4);
        var rules = KingdomWorldApi.rules();
        if (!rules.allowedDimensions().contains(dimension)) {
            return "kingdoms may not be founded in " + dimension;
        }
        if (state.territory().ownerOf(dimension, chunk) != null
                || state.territory().hasForeignLandWithin(dimension, chunk, rules.minChunksFromOtherKingdom(), null)) {
            return "the land of another kingdom is too close to the throne";
        }
        for (int dy = -1; dy < 3; dy++) {
            BlockPos at = throne.above(dy);
            if (NpcGround.protectedAt(level, at, level.getBlockState(at))) {
                return "something that must not be broken stands at the throne " + at.toShortString();
            }
        }
        return null;
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
        int budget = Math.max(1, chunkBudget()) * CLAIMS_PER_CHUNK_BUDGET;
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
        int r = NpcCity.landRadius(site);
        int around = NpcCity.townRadius(site);
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
            // The square of a town ring by ring from its town hall, so that every chunk is claimed next to land already held.
            for (int ring = 0; ring <= around; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            keys.add(ChunkPos.asLong(tx + dx, tz + dz));
                        }
                    }
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
        if (job.composition.districts()) {
            groundOfDistricts(level, site, job, id);
            return;
        }
        int reach = job.composition.reach();
        int margin = RAMP;
        int rows = 2 * (reach + margin) + 1;
        int budget = blockBudget();
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
                } else if (street(job.composition, x - site.x(), z - site.z())) {
                    top = streetBlock(job.composition, soil);
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
     * Levels the ground of a capital of districts column by column, as many as the budget allows in a tick: inside the wall to the
     * height of the throne with streets, avenues and the square paved by the plan, and a slope outside it.
     */
    private static void groundOfDistricts(ServerLevel level, NpcSite site, Job job, UUID id) {
        NpcComposition composition = job.composition;
        int reach = composition.reach();
        int side = 2 * (reach + RAMP) + 1;
        int total = side * side;
        int budget = blockBudget();
        // The soil is looked at outside the levelled ground, where the land stays as it was.
        NpcGround.Soil soil = NpcGround.soilAt(level, site.x() + reach + RAMP + 3, site.z());
        int step = site.step();
        while (step < total && budget > 0) {
            int dx = step % side - reach - RAMP;
            int dz = step / side - reach - RAMP;
            int x = site.x() + dx;
            int z = site.z() + dz;
            int out = Math.max(Math.abs(dx), Math.abs(dz)) - reach;
            BlockState top = NpcCity.surface(site, composition, dx, dz, soil);
            if (out > 0 && top == soil.top()) {
                budget -= Math.max(1, ramp(level, id, x, z, site.groundY(), out, soil));
            } else {
                budget -= Math.max(1, NpcGround.level(level, id, x, z, site.groundY(), top, soil.fill()));
            }
            step++;
        }
        site.setStep(step);
        if (step >= total) {
            site.advance(SpawnStage.BUILD);
        }
    }

    /**
     * A column just outside a levelled square: the ground there rises or falls at most a block for every column out, so that
     * the square ends in a slope a road can climb; trees over it go.
     *
     * @param out how many columns outside the square, from 1
     */
    static int ramp(ServerLevel level, UUID id, int x, int z, int height, int out, NpcGround.Soil soil) {
        int ground = NpcGround.groundAt(level, x, z);
        int changed = NpcGround.clearTrees(level, id, x, z);
        if (Math.abs(ground - height) > out) {
            int target = height + Integer.signum(ground - height) * out;
            changed += Math.max(0, NpcGround.level(level, id, x, z, target, soil.top(), soil.fill()));
        }
        return changed;
    }

    /**
     * Whether a column of the capital lies on a street: the gaps of two blocks between the plots of the grid.
     */
    static boolean street(NpcComposition composition, int dx, int dz) {
        int edge = composition.rings() * NpcComposition.SPACING + NpcComposition.SPACING / 2 + 1;
        if (Math.abs(dx) > edge || Math.abs(dz) > edge) {
            return false;
        }
        return gap(dx) || gap(dz);
    }

    private static boolean gap(int d) {
        return Math.floorMod(d - NpcComposition.SPACING / 2, NpcComposition.SPACING) < NpcComposition.SPACING - NpcComposition.PLOT;
    }

    /**
     * The surface of the streets: trodden earth for a village, gravel for a town, stone for a large city.
     */
    private static BlockState streetBlock(NpcComposition composition, NpcGround.Soil soil) {
        return switch (composition.roadClass()) {
            case "paved" -> Blocks.STONE_BRICKS.defaultBlockState();
            case "gravel" -> Blocks.GRAVEL.defaultBlockState();
            default -> soil == NpcGround.Soil.GRASS ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        };
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
            Placement placement = new Placement(plot.facing(), plot.mirror());
            BlockPos anchor = anchor(template.get(), site.x() + plot.x(), site.groundY() + 1, site.z() + plot.z(), placement);
            boxes.add(template.get().box(anchor, placement));
        }
        return boxes;
    }

    /**
     * Where to lay a template so that its footprint is centred on a point.
     */
    static BlockPos anchor(BuildingTemplate template, int centerX, int y, int centerZ, Placement placement) {
        return io.github.sekerator.kingdomworld.common.construction.ConstructionWork.anchorAt(template, centerX, y, centerZ, placement);
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
        if (job.only == null && site.given(NpcSite.REBUILDING)) {
            job.only = plotsOf(missing(server, site, job.composition, true));
        }
        int budget = blockBudget();
        while (site.step() < plots.size() && budget > 0) {
            if (job.only != null && !job.only.contains(site.step())) {
                site.setStep(site.step() + 1);
                continue;
            }
            NpcComposition.Plot plot = plots.get(site.step());
            Materials materials = NpcCity.materials(server, site, plot);
            int[] used = new int[1];
            Raised raised = raise(server, level, id, plot.template(), site.x() + plot.x(), site.groundY() + 1, site.z() + plot.z(),
                    new Placement(plot.facing(), plot.mirror()), materials, budget, used);
            budget -= Math.max(1, used[0]);
            if (raised == Raised.WAITING) {
                // The land of the building is not loaded: the step waits for it, the chunks are asked for again.
                return;
            }
            if (raised == Raised.STANDING || raised == Raised.SKIPPED) {
                site.setStep(site.step() + 1);
            }
        }
        if (site.step() >= plots.size()) {
            if (!job.rechecked) {
                // Every building of the plan is looked for in the register once: one that is missing for a reason that passed
                // (its land was let go under it) is raised again before the work of the capital begins.
                job.rechecked = true;
                List<Planned> missing = missing(server, site, job.composition, true);
                if (!missing.isEmpty()) {
                    LOGGER.warn("{} of {} buildings of the capital of {} are missing after its buildings were raised ({}): they are raised "
                            + "again", missing.size(), plots.size(), site, templates(missing));
                    job.only = plotsOf(missing);
                    site.setStep(missing.get(0).plot());
                    return;
                }
            }
            if (site.given(NpcSite.REBUILDING)) {
                // A capital raised again: its towns get what they miss, and the rest of its raising stands already.
                site.advance(missing(server, site, job.composition, false).isEmpty() ? SpawnStage.DONE : SpawnStage.TOWNS);
                return;
            }
            site.advance(SpawnStage.WORK);
        }
    }

    /**
     * A building of the plan of a place: its template, the point it is centred on, its placement and, for a building of the
     * capital, its number in the plan ({@code -1} for one of a town).
     */
    public record Planned(String template, int x, int z, Placement placement, int plot) {
    }

    /**
     * The buildings of the plan of a place: those of the capital and, for the towns standing, those of the towns.
     */
    public static List<Planned> planned(MinecraftServer server, NpcSite site, NpcComposition composition) {
        List<Planned> result = new ArrayList<>();
        List<NpcComposition.Plot> plots = composition.buildings();
        for (int i = 0; i < plots.size(); i++) {
            NpcComposition.Plot plot = plots.get(i);
            result.add(new Planned(plot.template(), site.x() + plot.x(), site.z() + plot.z(), new Placement(plot.facing(), plot.mirror()), i));
        }
        if (composition.towns() == 0 || site.kingdomId().isEmpty()) {
            return result;
        }
        List<City> cities = RoadWork.state(server).citiesOf(site.kingdomId().get());
        for (int index = 0; index < site.towns().size(); index++) {
            NpcSite.Town town = site.towns().get(index);
            // A town that was never founded has no buildings to miss.
            if (cities.stream().noneMatch(c -> Math.abs(c.x() - town.x()) <= 2 && Math.abs(c.z() - town.z()) <= 2)) {
                continue;
            }
            if (composition.districts()) {
                for (NpcComposition.Plot at : composition.town(index)) {
                    result.add(new Planned(at.template(), town.x() + at.x(), town.z() + at.z(), new Placement(at.facing(), at.mirror()), -1));
                }
                continue;
            }
            List<String> buildings = NpcComposition.townBuildings(composition.townHouses());
            for (int plot = 0; plot < buildings.size() && plot < TOWN_PLOTS.length; plot++) {
                String template = buildings.get(plot);
                int[] where = template.equals(NpcComposition.WELL) ? new int[]{-9, -9, 0} : TOWN_PLOTS[plot];
                result.add(new Planned(template, town.x() + where[0], town.z() + where[1], new Placement(where[2], false), -1));
            }
        }
        return result;
    }

    /**
     * The buildings of the plan of a place that neither stand nor are being built, as the raising looks for them: a building of the
     * template whose anchor stands at the same x and z. A template the game does not have is not missing: it can never stand.
     *
     * @param capital whether only those of the capital are wanted, else only those of the towns
     */
    public static List<Planned> missing(MinecraftServer server, NpcSite site, NpcComposition composition, boolean capital) {
        List<Planned> result = new ArrayList<>();
        for (Planned planned : missing(server, site, composition)) {
            if ((planned.plot() >= 0) == capital) {
                result.add(planned);
            }
        }
        return result;
    }

    /**
     * Every building of the plan of a place, capital and towns, that neither stands nor is being built.
     */
    public static List<Planned> missing(MinecraftServer server, NpcSite site, NpcComposition composition) {
        List<Planned> result = new ArrayList<>();
        Optional<UUID> id = site.kingdomId();
        if (id.isEmpty()) {
            return result;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        // A plot stands while a building or a construction of the kingdom has its anchor there, of the template of the plan or
        // of another one (a ruler upgraded it), or covers its middle.
        Set<Long> anchors = new java.util.HashSet<>();
        List<Box> boxes = new ArrayList<>();
        for (Building building : state.construction().buildingsOf(id.get())) {
            anchors.add(ChunkPos.asLong(building.anchor().x(), building.anchor().z()));
            boxes.add(building.box());
        }
        for (Construction construction : state.construction().of(id.get())) {
            anchors.add(ChunkPos.asLong(construction.anchor().x(), construction.anchor().z()));
            boxes.add(construction.box());
        }
        for (Planned planned : planned(server, site, composition)) {
            Optional<BuildingTemplate> template = BuildingTemplates.get(planned.template());
            if (template.isEmpty() || !template.get().available()) {
                continue;
            }
            BlockPos anchor = anchor(template.get(), planned.x(), site.groundY() + 1, planned.z(), planned.placement());
            if (anchors.contains(ChunkPos.asLong(anchor.getX(), anchor.getZ()))) {
                continue;
            }
            boolean covered = false;
            for (Box box : boxes) {
                if (box.minX() <= planned.x() && planned.x() <= box.maxX() && box.minZ() <= planned.z() && planned.z() <= box.maxZ()) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.add(planned);
            }
        }
        return result;
    }

    private static Set<Integer> plotsOf(List<Planned> buildings) {
        Set<Integer> plots = new java.util.HashSet<>();
        for (Planned planned : buildings) {
            if (planned.plot() >= 0) {
                plots.add(planned.plot());
            }
        }
        return plots;
    }

    /**
     * The templates of some buildings with their counts, for the log.
     */
    static String templates(List<Planned> buildings) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Planned planned : buildings) {
            counts.merge(planned.template(), 1, Integer::sum);
        }
        return counts.toString();
    }

    /**
     * What became of a building of a kingdom of the world after a part of its work.
     */
    public enum Raised {
        /** It stands (or stood there already). */
        STANDING,
        /** Its work goes on in the next steps. */
        GOING,
        /** Its land is not loaded: the step waits for the chunks. */
        WAITING,
        /** It cannot be put up at all and is left out, with a warning. */
        SKIPPED
    }

    /** Ticks a building waits for its land before it is left out. */
    static final long LAND_WAIT_TICKS = 2_400;
    /** When each building waiting for its land began to wait, by kingdom, template and anchor. */
    private static final Map<String, Long> WAITING_SINCE = new java.util.HashMap<>();
    /** Whether a building waited for its land in the step being made: the job asks for its chunks again. */
    private static boolean waitedForLand;

    /**
     * Whether a building waited for its land since the last question, which the question clears: a raising that asks it asks for
     * its chunks again.
     */
    static boolean takeWaitedForLand() {
        boolean waited = waitedForLand;
        waitedForLand = false;
        return waited;
    }

    /**
     * Puts up a building a part at a time without materials (the founding), unless one of the template already stands there; a
     * construction left half done by a restart goes on.
     *
     * @param used gets the blocks done, for the budget
     * @return whether the building stands or cannot be put up at all; {@code false} while it goes on or waits for its land
     */
    static boolean buildAt(MinecraftServer server, ServerLevel level, UUID id, String templateId, int centerX, int y, int centerZ,
                           Placement placement, Materials materials, int budget, int[] used) {
        Raised raised = raise(server, level, id, templateId, centerX, y, centerZ, placement, materials, budget, used);
        return raised == Raised.STANDING || raised == Raised.SKIPPED;
    }

    /**
     * Puts up a building a part at a time, as {@link #buildAt}, and tells what became of it. A building whose land is not loaded
     * waits for it rather than count as standing, and is left out with a warning only after {@value #LAND_WAIT_TICKS} ticks.
     */
    static Raised raise(MinecraftServer server, ServerLevel level, UUID id, String templateId, int centerX, int y, int centerZ,
                        Placement placement, Materials materials, int budget, int[] used) {
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        if (template.isEmpty() || !template.get().available()) {
            LOGGER.warn("The template {} for a kingdom of the world is missing or unavailable", templateId);
            return Raised.SKIPPED;
        }
        BlockPos anchor = anchor(template.get(), centerX, y, centerZ, placement);
        String waitKey = id + "/" + templateId + "/" + anchor.asLong();
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(id)) {
            if (building.templateId().equals(templateId) && building.anchor().x() == anchor.getX() && building.anchor().z() == anchor.getZ()) {
                WAITING_SINCE.remove(waitKey);
                return Raised.STANDING;
            }
        }
        Construction construction = null;
        for (Construction started : state.construction().of(id)) {
            if (started.templateId().equals(templateId) && started.anchor().x() == anchor.getX() && started.anchor().z() == anchor.getZ()) {
                construction = started;
            }
        }
        if (construction == null) {
            // A large building (a castle of thousands of blocks) is laid out over several ticks, a small one in this one.
            String key = id + "/" + templateId + "/" + anchor.asLong();
            long preparing = System.nanoTime();
            ConstructionWork.Laying laying = LAYING.computeIfAbsent(key, k -> new ConstructionWork.Laying(level, id, template.get(), anchor, placement,
                    materials));
            part("preparing", preparing);
            long layingOut = System.nanoTime();
            Optional<KingdomWorldState.ConstructionChange> laid = laying.step(budget(LAY_NANOS));
            part("laying out", layingOut);
            if (laid.isEmpty()) {
                // The time of the step is spent: the rest of the budget goes too.
                used[0] = Math.max(1, budget);
                return Raised.GOING;
            }
            LAYING.remove(key);
            KingdomWorldState.ConstructionChange change = laid.get();
            if (change.construction().isEmpty()) {
                if (change.outcome().error() == io.github.sekerator.kingdomworld.core.kingdom.KingdomError.AREA_NOT_LOADED) {
                    // The land went away under the raising (its chunks were let go): it waits for them, a while.
                    long now = level.getGameTime();
                    long since = WAITING_SINCE.computeIfAbsent(waitKey, k -> now);
                    if (now - since < LAND_WAIT_TICKS) {
                        waitedForLand = true;
                        return Raised.WAITING;
                    }
                    WAITING_SINCE.remove(waitKey);
                    LOGGER.warn("{} of a kingdom of the world was left out at {}: its land was not loaded for {} ticks", templateId,
                            anchor.toShortString(), now - since);
                    return Raised.SKIPPED;
                }
                WAITING_SINCE.remove(waitKey);
                LOGGER.warn("{} of a kingdom of the world was not laid out at {}: {}", templateId, anchor.toShortString(), change.outcome().error());
                return Raised.SKIPPED;
            }
            WAITING_SINCE.remove(waitKey);
            construction = change.construction().get();
        }
        int part = Math.max(BLOCKS_PER_PART, budget);
        // The chunks of the building and those next to it, which the updates of its blocks reach: one not loaded is loaded in
        // the tick, and the log of a slow tick tells of it.
        int unloaded = 0;
        Box box = construction.box();
        for (int x = (box.minX() - 1) >> 4; x <= (box.maxX() + 1) >> 4; x++) {
            for (int z = (box.minZ() - 1) >> 4; z <= (box.maxZ() + 1) >> 4; z++) {
                unloaded += level.getChunkSource().getChunkNow(x, z) == null ? 1 : 0;
            }
        }
        long placing = System.nanoTime();
        // The blocks go by time as well as by count: placing some costs far more than others in a busy world.
        Optional<Building> built = ConstructionWork.finishSome(server, construction, part, budget(LAY_NANOS));
        part((built.isPresent() ? "placing and finishing " + templateId : "placing") + (unloaded > 0 ? " with " + unloaded + " chunks not loaded" : ""),
                placing);
        used[0] = part;
        if (built.isPresent()) {
            return Raised.STANDING;
        }
        if (construction.status() == Construction.Status.OUTSIDE_LAND) {
            LOGGER.warn("{} of a kingdom of the world could not be finished at {}", templateId, anchor.toShortString());
            ConstructionWork.cancel(server, null, id, construction.id());
            return Raised.SKIPPED;
        }
        return Raised.GOING;
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
        if (composition.districts()) {
            workOfDistricts(server, level, site, composition, id);
            return;
        }
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
        List<int[]> gardens = gardenPlots(composition);
        int garden = step - fields - 2;
        if (garden < gardens.size()) {
            int[] cell = gardens.get(garden);
            garden(level, id, site.x() + cell[0] * NpcComposition.SPACING, site.groundY(), site.z() + cell[1] * NpcComposition.SPACING, garden);
            site.setStep(step + 1);
            return;
        }
        professionsInBuildings(server, id, composition);
        site.advance(SpawnStage.TOWNS);
    }

    /**
     * The places of work of a capital of districts, a step a tick: the posts and the treasury on the square of the palace, the
     * fields (carrots, every third one wheat for the bakers), the posts of woodcutters and miners outside the wall, the lamps of
     * the avenues, the trees of the greens, and at last the professions of the posts inside the buildings.
     */
    private static void workOfDistricts(MinecraftServer server, ServerLevel level, NpcSite site, NpcComposition composition, UUID id) {
        int y = site.groundY() + 1;
        int fields = composition.fields();
        int step = site.step();
        if (step == 0) {
            for (NpcCity.Place place : NpcCity.places(composition)) {
                BlockPos at = new BlockPos(site.x() + place.x(), y, site.z() + place.z());
                switch (place.what()) {
                    case "guild_desk" -> place(level, id, at, GuildRegistries.GUILD_DESK.get().defaultBlockState());
                    case "mint" -> place(level, id, at, ModRegistries.MINT.get().defaultBlockState());
                    case "bank" -> place(level, id, at, ModRegistries.BANK.get().defaultBlockState());
                    default -> post(level, id, at, place.what(), place.seats());
                }
            }
            site.setStep(1);
            return;
        }
        if (step <= fields) {
            int[] spot = composition.fieldSpots().get(step - 1);
            field(level, id, site.x() + spot[0], site.z() + spot[1], (step - 1) % 3 == 2 ? Blocks.WHEAT : Blocks.CARROTS);
            site.setStep(step + 1);
            return;
        }
        if (step == fields + 1) {
            List<int[]> outer = NpcCity.outerPosts(composition);
            int each = composition.level() == io.github.sekerator.kingdomworld.core.npc.NpcLevel.LARGE ? 2 : 1;
            for (int i = 0; i < each; i++) {
                int[] wood = outer.get(2 * i);
                BlockPos at = new BlockPos(site.x() + wood[0], NpcGround.groundAt(level, site.x() + wood[0], site.z() + wood[1]) + 1,
                        site.z() + wood[1]);
                post(level, id, at, "lumberjack", share(composition.jobs().getOrDefault("lumberjack", 0), each, i));
                grove(level, id, at, site);
                int[] mine = outer.get(2 * i + 1);
                post(level, id, new BlockPos(site.x() + mine[0], NpcGround.groundAt(level, site.x() + mine[0], site.z() + mine[1]) + 1,
                        site.z() + mine[1]), "miner", share(composition.jobs().getOrDefault("miner", 0), each, i));
            }
            site.setStep(step + 1);
            return;
        }
        if (step == fields + 2) {
            for (int[] lamp : NpcCity.lamps(composition)) {
                NpcCity.lamp(level, id, site, new BlockPos(site.x() + lamp[0], y, site.z() + lamp[1]));
            }
            site.setStep(step + 1);
            return;
        }
        if (step >= fields + 3) {
            // The trees go in a few at a step: a styled capital has up to a hundred, some sixty blocks each.
            List<int[]> greens = NpcCity.greens(composition);
            int from = (step - fields - 3) * TREES_A_STEP;
            if (from < greens.size()) {
                for (int[] green : greens.subList(from, Math.min(greens.size(), from + TREES_A_STEP))) {
                    NpcCity.tree(level, id, site, new BlockPos(site.x() + green[0], y, site.z() + green[1]));
                }
                site.setStep(step + 1);
                return;
            }
        }
        professionsByRole(server, level, id, composition.level() == io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL ? 1
                : SEATS_IN_BUILDING);
        if (composition.styled()) {
            CapitalStaff.staff(server, level, site, composition, id);
        }
        site.advance(SpawnStage.TOWNS);
    }

    /**
     * The seats of one of several posts of a profession: the workers shared between the posts, the first ones taking what does
     * not divide.
     */
    private static int share(int workers, int posts, int index) {
        if (posts <= 0) {
            return 0;
        }
        return workers / posts + (index < workers % posts ? 1 : 0);
    }

    /**
     * The plots of the grid of the capital no building took, for gardens.
     */
    static List<int[]> gardenPlots(NpcComposition composition) {
        Set<Long> taken = new java.util.HashSet<>();
        for (NpcComposition.Plot plot : composition.buildings()) {
            taken.add(ChunkPos.asLong(plot.gx(), plot.gz()));
        }
        List<int[]> free = new ArrayList<>();
        int rings = composition.rings();
        for (int gx = -rings; gx <= rings; gx++) {
            for (int gz = -rings; gz <= rings; gz++) {
                if ((gx != 0 || gz != 0) && !taken.contains(ChunkPos.asLong(gx, gz))) {
                    free.add(new int[]{gx, gz});
                }
            }
        }
        return free;
    }

    /**
     * A garden on a plot: a hedge of leaves round beds of crops (wheat, carrots, potatoes or beetroots by turns) watered from the
     * middle, a composter for the farmers and lanterns on two corners for the street.
     */
    private static void garden(ServerLevel level, UUID id, int cx, int y, int cz, int index) {
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        CropBlock crop = (CropBlock) crops[Math.floorMod(index, crops.length)];
        int half = NpcComposition.PLOT / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos ground = new BlockPos(cx + dx, y, cz + dz);
                boolean border = Math.abs(dx) == half || Math.abs(dz) == half;
                if (border) {
                    boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                    if (corner && dx == dz) {
                        NpcGround.set(level, id, ground.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
                        NpcGround.set(level, id, ground.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                    } else if (!corner && (dx == 0 || dz == 0)) {
                        continue;
                    } else {
                        NpcGround.set(level, id, ground.above(), Blocks.OAK_LEAVES.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    }
                    continue;
                }
                if (Math.abs(dx) == half - 1 || Math.abs(dz) == half - 1) {
                    continue;
                }
                if (dx == 0 && dz == 0) {
                    NpcGround.set(level, id, ground, Blocks.WATER.defaultBlockState());
                    continue;
                }
                if (NpcGround.set(level, id, ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE))) {
                    int age = Math.floorMod(cx * 7 + cz * 3 + dx * 5 + dz * 11, crop.getMaxAge() + 1);
                    NpcGround.set(level, id, ground.above(), crop.getStateForAge(age));
                }
            }
        }
        place(level, id, new BlockPos(cx + half - 1, y + 1, cz - half + 1), Blocks.COMPOSTER.defaultBlockState());
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
        field(level, id, cx, cz, Blocks.WHEAT);
    }

    /**
     * A field of a crop.
     */
    static void field(ServerLevel level, UUID id, int cx, int cz, Block planted) {
        CropBlock crop = (CropBlock) planted;
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
                    int age = Math.floorMod(cx * 31 + cz * 17 + dx * 7 + dz * 13, crop.getMaxAge() + 1);
                    NpcGround.set(level, id, at.above(), crop.getStateForAge(age));
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
    static void grove(ServerLevel level, UUID id, BlockPos post, NpcSite site) {
        Block sapling = site.districts() ? saplingOf(NpcCity.wood(site)) : sapling(level, post);
        for (int dx = -6; dx <= 6; dx += 4) {
            for (int dz = -6; dz <= 6; dz += 4) {
                if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
                    continue;
                }
                int x = post.getX() + dx;
                int z = post.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    // No chunk is loaded on the spot for a sapling.
                    continue;
                }
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
        return saplingOf(TemplateMaterials.woodAt(level, pos));
    }

    private static Block saplingOf(String wood) {
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
        post(level, id, pos, profession, composition.jobs().getOrDefault(profession, 0));
    }

    /**
     * A post of work with its profession and seats; none where nobody works at it.
     */
    static void post(ServerLevel level, UUID id, BlockPos pos, String profession, int seats) {
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
     * Gives the posts inside the workshops, the market, the tower of mages and the tavern their professions.
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
                case NpcComposition.TAVERN, NpcComposition.TAVERN_2 -> "innkeeper";
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

    /** Workers at the post of a workshop, a market or a tower of mages of a capital of districts. */
    private static final int SEATS_IN_BUILDING = 2;
    /** The trees of a capital planted in one step of its raising. */
    private static final int TREES_A_STEP = 16;

    /**
     * Gives the posts inside the buildings of a kingdom their professions by what each building is for and what stands in it
     * ({@link NpcCity#profession}), whatever template it was built from.
     */
    private static void professionsByRole(MinecraftServer server, ServerLevel level, UUID id, int workers) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int max = GatheringSettings.rules().maxSeats();
        for (Building building : state.construction().buildingsOf(id)) {
            if (building.role().isEmpty()) {
                continue;
            }
            Box box = building.box();
            List<WorkPost> inside = new ArrayList<>();
            for (WorkPost post : state.citizens().posts().all()) {
                WorldPos at = post.pos();
                if (post.kingdomId().equals(id) && at.x() >= box.minX() && at.x() <= box.maxX() && at.y() >= box.minY() && at.y() <= box.maxY()
                        && at.z() >= box.minZ() && at.z() <= box.maxZ()) {
                    inside.add(post);
                }
            }
            if (inside.isEmpty()) {
                continue;
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
                continue;
            }
            int seats = "innkeeper".equals(profession) ? 1 : "merchant".equals(profession) ? SEATS_IN_BUILDING : workers;
            for (WorkPost post : inside) {
                state.setPostProfession(null, post.pos(), profession);
                state.setPostSeats(null, post.pos(), Math.max(1, Math.min(seats, max)), max);
            }
        }
    }

    // ---------------------------------------------------------------- towns

    /**
     * Every town in steps: its square is levelled row by row, then its town hall is founded and its houses put up.
     */
    private static void towns(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        if (site.towns().isEmpty() || job.composition.towns() == 0) {
            site.advance(afterTowns(site));
            return;
        }
        UUID id = site.kingdomId().orElseThrow();
        // The step holds the town and how far it is: a town of districts counts every column of its ground.
        int unit = job.composition.districts() ? TOWN_STEPS : 1000;
        int index = site.step() / unit;
        int sub = site.step() % unit;
        if (index >= site.towns().size()) {
            site.advance(afterTowns(site));
            return;
        }
        NpcSite.Town town = site.towns().get(index);
        if (job.composition.districts()) {
            townOfDistricts(server, level, site, job.composition, id, town, index, sub);
            return;
        }
        int rows = 2 * (TOWN_REACH + RAMP) + 1;
        int y = townHeight(level, town);
        if (sub < rows && site.given(NpcSite.REBUILDING)) {
            // A town raised again was levelled the first time: its standing buildings are not cut.
            site.setStep(index * 1000 + rows);
            return;
        }
        if (sub < rows) {
            int budget = blockBudget();
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
                    new Materials(Materials.AUTO, ""), blockBudget(), used)) {
                site.setStep(index * 1000 + sub + 1);
            }
            return;
        }
        site.setStep((index + 1) * 1000);
    }

    /** Steps of the appearance a town of districts may take: the columns of its ground, its town hall, buildings and fields. */
    private static final int TOWN_STEPS = 100_000;

    /**
     * A town of a kingdom of districts in steps: its ground is levelled column by column within the budget of a tick, with its
     * square and its street, then its town hall is founded, its buildings are put up along the street and its field is sown.
     */
    private static void townOfDistricts(MinecraftServer server, ServerLevel level, NpcSite site, NpcComposition composition, UUID id,
                                        NpcSite.Town town, int index, int sub) {
        int reach = composition.townReach();
        int side = 2 * (reach + RAMP) + 1;
        int cells = side * side;
        int y = townHeight(level, site, town);
        if (sub < cells && site.given(NpcSite.REBUILDING)) {
            // A town raised again was levelled the first time: its standing buildings are not cut.
            site.setStep(index * TOWN_STEPS + cells);
            return;
        }
        if (sub < cells) {
            int budget = blockBudget();
            NpcGround.Soil soil = NpcGround.soilAt(level, town.x() + reach + RAMP + 3, town.z());
            BlockState path = soil == NpcGround.Soil.GRASS ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            while (sub < cells && budget > 0) {
                int dx = sub % side - reach - RAMP;
                int dz = sub / side - reach - RAMP;
                int out = Math.max(Math.abs(dx), Math.abs(dz)) - reach;
                if (out > 0) {
                    budget -= Math.max(1, ramp(level, id, town.x() + dx, town.z() + dz, y, out, soil));
                } else {
                    boolean square = Math.abs(dx) <= 4 && Math.abs(dz) <= 4;
                    boolean street = Math.abs(dz) <= 2 && Math.abs(dx) <= reach - 3;
                    BlockState top = square ? Blocks.COBBLESTONE.defaultBlockState() : street ? path : soil.top();
                    budget -= Math.max(1, NpcGround.level(level, id, town.x() + dx, town.z() + dz, y, top, soil.fill()));
                }
                sub++;
            }
            site.setStep(index * TOWN_STEPS + sub);
            return;
        }
        if (sub == cells) {
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
            site.setStep(index * TOWN_STEPS + sub + 1);
            return;
        }
        List<NpcComposition.Plot> plots = composition.town(index);
        int plot = sub - cells - 1;
        if (plot < plots.size()) {
            NpcComposition.Plot at = plots.get(plot);
            int[] used = new int[1];
            if (buildAt(server, level, id, at.template(), town.x() + at.x(), y + 1, town.z() + at.z(), new Placement(at.facing(), at.mirror()),
                    NpcCity.townMaterials(server, site, index, at), blockBudget(), used)) {
                site.setStep(index * TOWN_STEPS + sub + 1);
            }
            return;
        }
        List<int[]> fields = composition.townFields();
        int field = plot - plots.size();
        if (field < fields.size() && !site.given(NpcSite.REBUILDING)) {
            field(level, id, town.x() + fields.get(field)[0], town.z() + fields.get(field)[1], Blocks.CARROTS);
            site.setStep(index * TOWN_STEPS + sub + 1);
            return;
        }
        site.setStep((index + 1) * TOWN_STEPS);
    }

    private static int townHeight(ServerLevel level, NpcSite.Town town) {
        return plazaHeight(level, town.x(), town.z());
    }

    /**
     * The height of the ground of a town of a place: of its whole ground where the kingdom is laid out in districts, since its
     * towns are wider than their square.
     */
    /**
     * The stage after the towns: the places of the capital, or its end for a capital raised again.
     */
    private static SpawnStage afterTowns(NpcSite site) {
        return site.given(NpcSite.REBUILDING) ? SpawnStage.DONE : SpawnStage.PLACES;
    }

    private static int townHeight(ServerLevel level, NpcSite site, NpcSite.Town town) {
        if (!site.districts()) {
            return townHeight(level, town);
        }
        Optional<City> city = RoadWork.state(level.getServer()).citiesOf(site.kingdomId().orElse(null)).stream()
                .filter(c -> Math.abs(c.x() - town.x()) <= 2 && Math.abs(c.z() - town.z()) <= 2).findFirst();
        // Once the town hall stands its height is the height of the town: the ground around it was levelled to it.
        return city.map(c -> c.y() - 1).orElseGet(() -> wideHeight(level, town.x(), town.z(), 24));
    }

    // ---------------------------------------------------------------- places, wealth and people

    /**
     * Looks through the chunks of the land for beds and posts the buildings brought, a few chunks a tick.
     */
    private static void places(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        List<Long> chunks = KingdomWorldApi.state(server).territory().territory(id).map(t -> List.copyOf(t.chunks())).orElse(List.of());
        int budget = chunkBudget() * 4;
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
        String wood = site.districts() ? NpcCity.wood(site) : woodOf(level, new BlockPos(site.x(), site.groundY(), site.z()));
        NpcWealth wealth = NpcWealth.of(job.composition, NpcSettings.rules(), wood + "_log", wood + "_planks",
                CitizenSettings.rules().dailyNutrition());
        long day = TreasuryActions.day(server);
        if (site.given(NpcSite.STORES) || !site.given(NpcSite.MONEY) && founded(state, id, day)) {
            // The founding was given already (the appearance runs again after a restart): nothing comes from nothing twice.
            site.advance(SpawnStage.PEOPLE);
            return;
        }
        if (!site.given(NpcSite.MONEY)) {
            // A kingdom founded as data got its money then; its stores come with its storehouses.
            grantMoney(server, site, id);
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
        site.markGiven(NpcSite.STORES);
        LOGGER.info("{} got its founding wealth: {} in the treasury, {} kinds of goods in the storehouses, the wood of the place {}", site,
                wealth.money(), wealth.items().size(), wood);
        site.advance(SpawnStage.PEOPLE);
    }

    /**
     * The founding money of the treasury, once.
     */
    static void grantMoney(MinecraftServer server, NpcSite site, UUID id) {
        long money = NpcSettings.rules().money(site.level());
        if (money > 0) {
            Outcome granted = KingdomWorldApi.state(server).economy().foundingGrant(id, money, TreasuryActions.day(server));
            if (!granted.ok()) {
                LOGGER.warn("The founding money of {} was refused: {}", site, granted.error());
            }
        }
        site.markGiven(NpcSite.MONEY);
        // Founded with the money of today: there is no difference to give later.
        site.markGiven(NpcSite.WEALTH);
    }

    /**
     * Gives the kingdoms of the world founded with the money of older versions the difference to the founding money of today,
     * once each, journaled as founding money. Kingdoms that did not get their founding money yet get today's when they do.
     *
     * @return how many treasuries got their wealth
     */
    public static int topUpWealth(MinecraftServer server) {
        if (NpcWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        int given = 0;
        for (NpcSite site : List.copyOf(NpcWork.state(server).sites())) {
            if (!site.given(NpcSite.MONEY) || site.given(NpcSite.WEALTH) || site.kingdomId().isEmpty()
                    || (site.status() != NpcSite.Status.FOUNDED && site.status() != NpcSite.Status.SPAWNING
                    && site.status() != NpcSite.Status.SPAWNED)) {
                continue;
            }
            UUID id = site.kingdomId().get();
            if (KingdomWorldApi.kingdom(server, id).isEmpty()) {
                continue;
            }
            long delta = Math.max(0, NpcSettings.rules().money(site.level()) - io.github.sekerator.kingdomworld.core.npc.NpcRules.LEGACY_MONEY[site.level().ordinal()]);
            if (delta > 0) {
                Outcome granted = KingdomWorldApi.state(server).economy().foundingGrant(id, delta, TreasuryActions.day(server));
                if (!granted.ok()) {
                    // Tried again later, when the treasury takes it.
                    LOGGER.warn("The wealth of the crown of {} was refused: {}", site, granted.error());
                    continue;
                }
                LOGGER.info("The treasury of {} got its wealth of {} copper", KingdomWorldApi.kingdom(server, id).map(Kingdom::name).orElse(id.toString()),
                        delta);
                given++;
            }
            site.markGiven(NpcSite.WEALTH);
        }
        return given;
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
        if (job.composition.districts()) {
            peopleOfDistricts(server, level, site, job, id, have);
            return;
        }
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
                long arriving = System.nanoTime();
                boolean arrived = CitizenLife.arrive(server, id, place, false).isPresent();
                part("arrivals", arriving);
                if (!arrived) {
                    break;
                }
            }
            return;
        }
        // Work: the posts take the citizens without work nearest to them, the farmers find their composters.
        long hiring = System.nanoTime();
        boolean hired = hireSome(state, id, job);
        part("hiring", hiring);
        if (!hired) {
            return;
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

    /**
     * The posts of a kingdom take the citizens without work nearest to them, one after another, while the time of the step lasts.
     *
     * @return whether every post is full or nobody is left without work; otherwise the rest is hired in the next tick
     */
    private static boolean hireSome(KingdomWorldState state, UUID id, Job job) {
        long started = System.nanoTime();
        List<WorkPost> posts = state.citizens().posts().postsOf(id);
        boolean worked = false;
        // The posts are gone through once, from the one the last step stopped at.
        while (job.hireIndex < posts.size()) {
            WorkPost post = posts.get(job.hireIndex);
            if (post.hasProfession()) {
                while (post.freeSeats() > 0 && state.hire(null, post.pos()).outcome().ok()) {
                    if (System.nanoTime() - started > budget(PEOPLE_NANOS)) {
                        return false;
                    }
                }
            }
            job.hireIndex++;
            worked = true;
            if (System.nanoTime() - started > budget(PEOPLE_NANOS)) {
                return false;
            }
        }
        // The farmers are chosen in a tick of their own when the hiring took most of this one.
        return !worked || System.nanoTime() - started <= budget(PEOPLE_NANOS) / 2;
    }

    /**
     * The citizens of a kingdom of districts: each arrives before its own house and takes the bed there, the posts then take
     * the citizens without work nearest to them, and the farmers come from those who live nearest to the fields: two for every
     * town from its own people, the rest from the houses of the capital furthest from the palace.
     */
    private static void peopleOfDistricts(MinecraftServer server, ServerLevel level, NpcSite site, Job job, UUID id, int have) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        NpcComposition composition = job.composition;
        List<int[]> arrivals = NpcCity.arrivals(site, composition);
        if (have < arrivals.size()) {
            String dimension = Dimensions.id(level);
            for (int i = 0; i < CITIZENS_PER_TICK && have < arrivals.size(); i++, have++) {
                int[] at = arrivals.get(have);
                int y = at[2] < 0 ? site.groundY() + 1 : townHeight(level, site, site.towns().get(at[2])) + 1;
                long arriving = System.nanoTime();
                boolean arrived = CitizenLife.arrive(server, id, new WorldPos(dimension, at[0], y, at[1]), false).isPresent();
                part("arrivals", arriving);
                if (!arrived) {
                    break;
                }
            }
            return;
        }
        long hiring = System.nanoTime();
        boolean hired = hireSome(state, id, job);
        part("hiring", hiring);
        if (!hired) {
            return;
        }
        long choosing = System.nanoTime();
        int soldiers = composition.soldiers();
        int townFarmers = 2 * composition.townFields().size();
        for (NpcSite.Town town : site.towns()) {
            List<Citizen> near = new ArrayList<>();
            for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
                WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
                if (at != null && Math.abs(at.x() - town.x()) <= composition.townReach() && Math.abs(at.z() - town.z()) <= composition.townReach()) {
                    near.add(citizen);
                }
            }
            farmers(server, state, id, near, townFarmers, soldiers);
        }
        List<Citizen> capital = new ArrayList<>();
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            if (at != null && Math.abs(at.x() - site.x()) <= composition.reach() && Math.abs(at.z() - site.z()) <= composition.reach()) {
                capital.add(citizen);
            }
        }
        capital.sort(Comparator.comparingInt((Citizen citizen) -> {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            return -Math.max(Math.abs(at.x() - site.x()), Math.abs(at.z() - site.z()));
        }).thenComparing(citizen -> citizen.id().toString()));
        farmers(server, state, id, capital, 2 * composition.fields(), soldiers);
        part("farmers", choosing);
        site.advance(SpawnStage.ARMY);
    }

    /**
     * Makes farmers of the first citizens without work of a list, as long as the kingdom keeps enough people without work for
     * its soldiers.
     */
    private static void farmers(MinecraftServer server, KingdomWorldState state, UUID id, List<Citizen> from, int wanted, int soldiers) {
        int made = 0;
        for (Citizen citizen : from) {
            if (made >= wanted || unemployed(state, id) <= soldiers) {
                break;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())
                    && state.setProfession(null, citizen.id(), Professions.FARMER.id()).ok()) {
                CitizenActions.afterProfessionChange(server, citizen);
                made++;
            }
        }
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
            site.advance(afterArmy(job.composition));
            return;
        }
        ArmyState army = ArmyWork.state(server);
        int want = job.composition.soldiers();
        int have = army.soldiersOf(id).size();
        // Citizens join a part a tick, while the time of the step lasts; the rest join in the next ticks.
        long started = System.nanoTime();
        int joined = 0;
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(id))) {
            if (have >= want) {
                break;
            }
            if (joined > 0 && System.nanoTime() - started > budget(ARMY_NANOS)) {
                return;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                String profession = have % 2 == 0 ? Professions.WARRIOR.id() : Professions.ARCHER.id();
                if (ArmyWork.recruit(server, null, id, citizen.id(), profession).ok()) {
                    have++;
                    joined++;
                }
            }
        }
        if (joined > 0) {
            // The recruits from abroad come in the next ticks, not in the one citizens joined in.
            return;
        }
        // The royal army comes from abroad a part a tick; then the guard and the army, their officers and the kits of their posts.
        if (!NpcGarrison.enlistSome(server, site)) {
            return;
        }
        if (site.step() == 0) {
            // The guard and the army get their posts once: the step of the stage remembers it over a restart.
            NpcGarrison.found(server, site, job.composition);
            site.setStep(1);
            return;
        }
        if (site.step() == 1) {
            // The adventurers of the branch of the guild and the mages of the tower come from abroad, a part a tick.
            if (!NpcGuilds.raise(server, site, budget(ARMY_NANOS))) {
                return;
            }
            site.setStep(2);
            return;
        }
        // The soldiers moved into the bunks of the barracks: whoever has no bed yet takes one of those they left, a part a tick.
        job.settled = io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation.settleSome(server, id, job.settled, budget(PEOPLE_NANOS));
        if (job.settled >= 0) {
            return;
        }
        site.advance(afterArmy(job.composition));
    }

    /**
     * The stage after the soldiers: a capital of districts gets its wall first and its roads start at the gates of the wall; a
     * capital of the first layout gets its roads first and its wall has gates where they cross it.
     */
    private static SpawnStage afterArmy(NpcComposition composition) {
        return composition.districts() ? SpawnStage.WALL : SpawnStage.ROADS;
    }

    /**
     * The laws of the ruler by its character (set through the law like a king would) and the guards: squads of the soldiers
     * guard the gates of the capital and the town halls of the towns; without gates or towns one squad guards the capital.
     */
    private static void law(MinecraftServer server, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        if (!site.given(NpcSite.LAWS)) {
            setLaws(server, site, id);
        }
        if (!ArmyWork.isReadOnly(server)) {
            guardGates(server, site, id, job);
        }
        site.advance(SpawnStage.DONE);
    }

    /**
     * The laws of the ruler by its character, set through the law like a king would, once.
     */
    static void setLaws(MinecraftServer server, NpcSite site, UUID id) {
        Optional<NpcKingdom> record = NpcWork.kingdom(server, id);
        if (record.isPresent() && !io.github.sekerator.kingdomworld.common.law.LawWork.isReadOnly(server)) {
            io.github.sekerator.kingdomworld.core.law.LawRules rules = io.github.sekerator.kingdomworld.common.law.LawSettings.rules();
            io.github.sekerator.kingdomworld.core.npc.NpcLaws laws = io.github.sekerator.kingdomworld.core.npc.NpcLaws.of(
                    record.get().character(), record.get().traits(), rules.maxEntryFee, rules.maxBounty);
            KingdomWorldState world = KingdomWorldApi.state(server);
            io.github.sekerator.kingdomworld.common.law.LawWork.change(server, "set the laws of a kingdom of the world", law -> {
                law.setMultiplier(null, id, laws.fineMultiplierPermille(), world);
                law.setEntryFee(null, id, laws.entryFee(), rules, world);
                law.setBountyReward(null, id, laws.bounty(), rules, world);
                return law.setSlaveryBanned(null, id, laws.slaveryBanned(), world);
            });
            LOGGER.info("The laws of {}: fines {}%, entry to large cities {}, bounty {}, slavery {}", site,
                    laws.fineMultiplierPermille() / 10, laws.entryFee(), laws.bounty(), laws.slaveryBanned() ? "banned" : "allowed");
            site.markGiven(NpcSite.LAWS);
        }
    }

    /**
     * The soldiers take their posts: the gates, the storehouse, the palace, the square, the rounds and the towns for the
     * guard, the barracks and the palace for the army in reserve. The posts are reckoned again every day of the ruler.
     */
    private static void guardGates(MinecraftServer server, NpcSite site, UUID id, Job job) {
        NpcGarrison.station(server, id);
    }

    // ---------------------------------------------------------------- roads and the wall

    /**
     * A road from the capital to every town in turn: the way is searched over ticks, then every block of it is paid for (part
     * of the founding, like the buildings) and the roads place them as their chunks are loaded.
     */
    private static void roads(MinecraftServer server, ServerLevel level, NpcSite site, Job job) {
        UUID id = site.kingdomId().orElseThrow();
        int index = site.step();
        boolean districts = job.composition.districts();
        if (RoadWork.isReadOnly(server)) {
            site.advance(districts ? SpawnStage.LAW : SpawnStage.WALL);
            return;
        }
        if (index >= site.towns().size()) {
            if (districts && job.composition.townWall() > 0 && index < 2 * site.towns().size()) {
                // The roads stand: now the palisades of the towns, each with a gate where its road comes in.
                if (townWall(server, level, site, job, id, index - site.towns().size())) {
                    site.setStep(index + 1);
                }
                return;
            }
            site.advance(districts ? SpawnStage.LAW : SpawnStage.WALL);
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
                    // The founding goes on; the road to the town is tried again later (see TownRoads).
                    TownRoads.missed(server, site, index, "no way was found");
                    site.setStep(index + 1);
                }
            }
            return;
        }
        if (RoadPlanning.searching(id)) {
            return;
        }
        Outcome started = startRoad(level, site, job.composition, id, town);
        if (started.ok()) {
            job.searching = true;
        } else {
            TownRoads.missed(server, site, index, "it was not started: " + started.error());
            site.setStep(index + 1);
        }
    }

    /**
     * Starts the search for the road from the capital of a kingdom of the world to a town of it, built once it is found.
     */
    static Outcome startRoad(ServerLevel level, NpcSite site, NpcComposition composition, UUID id, NpcSite.Town town) {
        RoadClass roadClass = RoadClass.byId(composition.roadClass()).orElse(RoadClass.PATH);
        Optional<NpcComposition.Gate> gate = composition.districts() ? NpcCity.gateTowards(composition, town.x() - site.x(), town.z() - site.z())
                : Optional.empty();
        if (gate.isPresent()) {
            // The road starts two blocks outside the gate nearest to the town: under the roof of the palace no way begins.
            int fromX = site.x() + gate.get().x() + 2 * Integer.signum(gate.get().x());
            int fromZ = site.z() + gate.get().z() + 2 * Integer.signum(gate.get().z());
            return RoadPlanning.startAndBuildFrom(level, id, Endpoint.capital(id, fromX, fromZ), town.x(), town.z(), roadClass,
                    Road.Mode.OWN);
        }
        return RoadPlanning.startAndBuild(level, id, site.x(), site.z(), town.x(), town.z(), roadClass, Road.Mode.OWN);
    }

    public static Optional<Road> roadTo(MinecraftServer server, UUID id, NpcSite.Town town) {
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
    public static void payRoad(MinecraftServer server, ServerLevel level, Road road) {
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
            // The roads of the rulers are laid on the land as it is and take none of the materials of the levelled ones.
            case PIER, LINING -> List.of("minecraft:cobblestone", "minecraft:stone");
            case RAIL -> List.of(wood + "_fence", "minecraft:oak_fence");
            case TORCH -> List.of("minecraft:torch");
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
            site.advance(SpawnStage.LAW);
            return;
        }
        boolean districts = job.composition.districts();
        SpawnStage next = districts ? SpawnStage.ROADS : SpawnStage.LAW;
        Optional<Wall> existing = RoadWork.state(server).wallOf(0, id);
        if (existing.isEmpty()) {
            Wall[] made = new Wall[1];
            Wall.WallClass wallClass = job.composition.stoneWall() ? Wall.WallClass.STONE : Wall.WallClass.PALISADE;
            Outcome built = districts ? CityWalls.build(server, null, id, 0, wallClass, made, job.composition.wallRadius())
                    : CityWalls.build(server, null, id, 0, wallClass, made);
            if (!built.ok() || made[0] == null) {
                LOGGER.warn("The wall of {} was refused: {}", site, built.error());
                site.advance(next);
                return;
            }
            if (districts) {
                NpcCity.cutGates(server, site, job.composition, id);
            }
            existing = Optional.of(made[0]);
        }
        Wall wall = existing.get();
        if (payWall(server, level, site, wall)) {
            RoadSync.changed();
            site.advance(next);
        }
    }

    /**
     * Pays a part of a wall within the budget of blocks, with the wood or stone of the place.
     *
     * @return whether the wall is all paid
     */
    static boolean payWall(MinecraftServer server, ServerLevel level, NpcSite site, Wall wall) {
        boolean palisade = wall.wallClass() == Wall.WallClass.PALISADE;
        String item = palisade ? (site.districts() ? NpcCity.wood(site) : woodOf(level, new BlockPos(site.x(), site.groundY(), site.z()))) + "_log"
                : site.districts() ? NpcCity.wallStone(site) : "minecraft:stone_bricks";
        Item wanted = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(item));
        if (!(wanted instanceof BlockItem) || !new ItemStack(wanted).is(palisade ? CityWalls.WALL_WOOD : CityWalls.WALL_STONE)) {
            item = palisade ? "minecraft:oak_log" : "minecraft:cobblestone";
        }
        int budget = Math.min(WALL_BLOCKS_PER_TICK, blockBudget());
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
        return wall.allPaid();
    }

    /**
     * The palisade of a town of a kingdom of districts: laid out around its town hall with a gate where its road comes in, then
     * paid a part a tick.
     *
     * @return whether the palisade is paid, or cannot be built
     */
    private static boolean townWall(MinecraftServer server, ServerLevel level, NpcSite site, Job job, UUID id, int index) {
        NpcSite.Town town = site.towns().get(index);
        Optional<City> city = RoadWork.state(server).citiesOf(id).stream()
                .filter(c -> Math.abs(c.x() - town.x()) <= 2 && Math.abs(c.z() - town.z()) <= 2).findFirst();
        if (city.isEmpty()) {
            return true;
        }
        Optional<Wall> existing = RoadWork.state(server).wallOf(city.get().id(), id);
        if (existing.isEmpty()) {
            Wall[] made = new Wall[1];
            Outcome built = CityWalls.build(server, null, id, city.get().id(), Wall.WallClass.PALISADE, made, job.composition.townWall());
            if (!built.ok() || made[0] == null) {
                LOGGER.warn("The palisade of the town {} of {} was refused: {}", index + 1, site, built.error());
                return true;
            }
            existing = Optional.of(made[0]);
        }
        return payWall(server, level, site, existing.get());
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
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(id)) {
                standing &= wall.built();
            }
        }
        if (!standing && site.step() < PLACE_WAIT_TICKS) {
            site.setStep(site.step() + 1);
            job.idle = true;
            return;
        }
        // While the land is still loaded, everyone finds a workplace and looks at it: a farmer who never saw its field would
        // harvest nothing until a player came by.
        if (job.surveys == null) {
            io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation.settle(server, id);
            // The zones of the posts the steps of the citizens look at in the ticks after them are looked at now, a share of
            // them a tick: once the chunks go nothing could see the workstations of a workshop, the stall of a market or the
            // desk of a registrar, and their workers would stand idle until a player came by.
            job.surveys = new java.util.ArrayDeque<>();
            for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(id)) {
                if (post.hasProfession() && (post.yield().isEmpty() || Professions.usesStations(post.professionId()))) {
                    job.surveys.add(post);
                }
            }
            return;
        }
        long until = System.nanoTime() + SURVEY_NANOS;
        while (!job.surveys.isEmpty() && System.nanoTime() < until) {
            io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys.surveyNow(server, job.surveys.poll());
        }
        if (!job.surveys.isEmpty()) {
            return;
        }
        List<Planned> missing = missing(server, site, job.composition);
        if (!missing.isEmpty()) {
            LOGGER.warn("{} of {} buildings of the plan of {} are missing at its end: {}", missing.size(),
                    planned(server, site, job.composition).size(), site, templates(missing));
        }
        if (site.given(NpcSite.REBUILDING)) {
            site.unmarkGiven(NpcSite.REBUILDING);
            site.clearReason();
            LOGGER.info("The capital of {} was raised again: {} building(s) of its plan missing now", site, missing.size());
        }
        releaseTickets(level);
        site.setStatus(NpcSite.Status.SPAWNED);
        forgetFailures(site.id());
        RoadSync.changed();
        double average = job.ticks > 2 ? job.sumNanos / 1e6 / (job.ticks - 2) : 0;
        lastTiming = new Timing(job.ticks, average, job.maxNanos / 1e6);
        if (job.background) {
            LOGGER.info("The buildings of {} were raised in the background: {} ticks of work, {} ticks waited, average tick {} ms, worst tick {} ms",
                    site, job.ticks, job.paused, String.format(java.util.Locale.ROOT, "%.2f", average),
                    String.format(java.util.Locale.ROOT, "%.2f", job.maxNanos / 1e6));
        }
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
