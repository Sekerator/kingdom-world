package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.LandPlanning;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.SurveyProbes;
import io.github.sekerator.kingdomworld.common.land.SurveySavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.land.PlanProgress.Stage;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Game tests of the plan of states (the world of the game tests is generated as a normal world, from a random seed): a small
 * ring far from everything, where there is most land, is surveyed, laid out, written into the map of the land and
 * founded within the time of a tick; a plan stopped in the middle of the survey and of the provinces goes on to the same end; and
 * a world that does not ask for states keeps the plan of capitals. The plans of these tests are made apart from the plan of the
 * world, around a place far from every other test.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandPlanGameTests {
    private static final String EMPTY = "empty";
    /** The middle of the ring of the tests, far from the land of every other test. */
    private static final int FAR = 200_000;
    /** The provinces of the tests are numbered after this, far above those of a plan of a world and below the other tests. */
    private static final int BASE = 60_000;
    /** The worst tick a plan may take. */
    private static final long WORST_TICK_NANOS = 10_000_000L;

    private LandPlanGameTests() {
    }

    private static LandPlanning.Setup ring(int[] center, int base) {
        return new LandPlanning.Setup(StateRules.defaults(center[0], center[1]).withQuota(2, 0, 0).withRing(500, 2500), ProvinceRules.DEFAULT,
                0x1A2B3CL, base);
    }

    /**
     * The middle of a ring with the most dry land among a few around a place: the world of the game tests is generated from a
     * random seed, and the states need land.
     */
    private static int[] landedCenter(MinecraftServer server, int x, int z) {
        ServerLevel level = server.overworld();
        int sea = level.getSeaLevel();
        int[] best = {x, z};
        int bestLand = -1;
        for (int i = 0; i < 9; i++) {
            int cx = x + (i % 3 - 1) * 8_000;
            int cz = z + (i / 3 - 1) * 8_000;
            int land = 0;
            for (int px = -2_400; px <= 2_400; px += 600) {
                for (int pz = -2_400; pz <= 2_400; pz += 600) {
                    if ((long) px * px + (long) pz * pz > 2_500L * 2_500L) {
                        continue;
                    }
                    int floor = SurveyProbes.floor(level, cx + px, cz + pz);
                    SurveyClassifier.Probe probe = SurveyProbes.probe(level, cx + px, floor, cz + pz);
                    if (floor > sea && probe != SurveyClassifier.Probe.OCEAN && probe != SurveyClassifier.Probe.RIVER
                            && probe != SurveyClassifier.Probe.MOUNTAIN) {
                        land++;
                    }
                }
            }
            if (land > bestLand) {
                bestLand = land;
                best = new int[]{cx, cz};
            }
        }
        return best;
    }

    /**
     * Calls a step every tick until it says it is done, then the end; fails after a number of ticks.
     */
    private static void repeat(GameTestHelper helper, int tick, int limit, BooleanSupplier step, Runnable end, Runnable cleanUp,
                               java.util.function.Supplier<String> where) {
        boolean done;
        try {
            done = step.getAsBoolean();
        } catch (RuntimeException | Error e) {
            cleanUp.run();
            throw e;
        }
        if (done) {
            try {
                end.run();
            } finally {
                cleanUp.run();
            }
            return;
        }
        if (tick >= limit) {
            cleanUp.run();
            helper.fail("The plan did not end within " + limit + " ticks: " + where.get());
            return;
        }
        helper.runAfterDelay(1, () -> repeat(helper, tick + 1, limit, step, end, cleanUp, where));
    }

    /**
     * Gives up the provinces of a test and takes its kingdoms down.
     */
    private static void cleanUp(MinecraftServer server, TerritoryIndex territory, int[] sites) {
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (Province province : map.provinces()) {
            if (province.id() > BASE && province.id() < BASE + 5_000) {
                territory.clearProvince(province.id());
            }
        }
        for (int site : sites) {
            if (site >= 0) {
                NpcWork.remove(server, site, false);
            }
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_plan", timeoutTicks = 24_000)
    public static void aSmallRingIsPlannedFoundedAndGivenItsProvinces(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        cleanUp(server, territory, new int[0]);
        LandPlanning.Job job = new LandPlanning.Job(server, ring(landedCenter(server, FAR, FAR), BASE), new SurveySavedData(), map, territory,
                true, () -> {
                });
        repeat(helper, 0, 20_000, () -> {
            job.tick();
            // The work of the kingdoms of the world founds the places planned; here it is done at once, apart from the plan.
            for (int id : job.sites()) {
                Optional<NpcSite> site = id < 0 ? Optional.empty() : NpcWork.state(server).site(id);
                if (site.isPresent() && site.get().status() == NpcSite.Status.PLANNED) {
                    NpcWork.foundNow(server, site.get());
                }
            }
            helper.assertTrue(job.failure().isEmpty(), "The plan failed: " + job.failure().orElse(""));
            return job.done();
        }, () -> {
            // A pause of the collector of garbage stops the server whatever it does: the plan answers for the rest of its tick.
            long worst = job.progress().worstWorkNanos();
            helper.assertTrue(worst <= WORST_TICK_NANOS, "A tick of the plan took " + worst / 1e6 + " ms at " + job.progress().worstWorkStage()
                    + " besides the garbage collector");
            int[] sites = job.sites();
            List<int[]> provinces = job.stateProvinces();
            helper.assertTrue(sites.length == 2, "Two states were planned: " + Arrays.toString(sites));
            String dimension = Dimensions.id(server.overworld());
            for (int i = 0; i < sites.length; i++) {
                NpcSite site = NpcWork.state(server).site(sites[i]).orElse(null);
                helper.assertTrue(site != null && site.kingdomId().isPresent(), "The state " + i + " was not founded: " + site);
                UUID kingdom = site.kingdomId().get();
                helper.assertTrue(provinces.get(i).length >= 2, "The state " + i + " has " + provinces.get(i).length + " province(s)");
                List<Integer> held = territory.provincesOf(kingdom);
                for (int id : provinces.get(i)) {
                    helper.assertTrue(map.exists(id) && map.chunks(id) > 0, "The province " + id + " is not in the map");
                    helper.assertTrue(held.contains(id), "The state " + i + " does not hold its province " + id + ": " + held);
                }
                long throne = ChunkKey.of(site.x() >> 4, site.z() >> 4);
                helper.assertTrue(kingdom.equals(territory.claimOwner(dimension, throne)), "The capital of the state " + i + " is not claimed");
                helper.assertTrue(kingdom.equals(territory.ownerOf(dimension, throne)), "The throne of the state " + i + " is not its land");
            }
            CompoundTag summary = job.summary();
            helper.assertTrue(summary.getInt("wildProvinces") > 0, "The neutral ring has no wild provinces: " + summary);
            helper.succeed();
        }, () -> cleanUp(server, territory, job.sites()), () -> job.progress() + ", " + Arrays.toString(job.stageWork()));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_restart", timeoutTicks = 24_000)
    public static void aPlanStoppedInTheSurveyAndInTheProvincesGoesOnToTheSameEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // Both plans are made into maps of their own, apart from the land of the world, and found nothing.
        ProvinceMap straightMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        ProvinceMap stoppedMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        LandPlanning.Setup setup = ring(landedCenter(server, -FAR, FAR), 0);
        LandPlanning.Job straight = new LandPlanning.Job(server, setup, new SurveySavedData(), straightMap, null, false, () -> {
        });
        LandPlanning.Job[] stopped = {new LandPlanning.Job(server, setup, new SurveySavedData(), stoppedMap, null, false, () -> {
        })};
        List<String> restarts = new ArrayList<>();
        repeat(helper, 0, 20_000, () -> {
            if (!straight.done()) {
                straight.tick();
            }
            LandPlanning.Job job = stopped[0];
            if (restarts.isEmpty()) {
                // The plan stops as soon as the first cells of the survey are in, in the middle of the survey.
                job.tick();
                if (job.survey().nextCell() == 0) {
                    return false;
                }
                helper.assertTrue(job.progress().stage() == Stage.SURVEY && !job.survey().complete(),
                        "The survey did not stop in its middle: " + job.progress() + " at cell " + job.survey().nextCell());
                int before = job.survey().nextCell();
                stopped[0] = restart(server, job, stoppedMap);
                int row = stopped[0].survey().width();
                helper.assertTrue(stopped[0].survey().nextCell() == before - before % row, "The survey went on from cell "
                        + stopped[0].survey().nextCell() + ", not from the start of the row of cell " + before);
                restarts.add("survey at cell " + before);
                return false;
            }
            job.tick();
            if (restarts.size() == 1 && job.progress().stage() == Stage.PROVINCES) {
                stopped[0] = restart(server, job, stoppedMap);
                helper.assertTrue(stopped[0].progress().stage() == Stage.PROVINCES, "The plan went back: " + stopped[0].progress());
                restarts.add("provinces");
            }
            helper.assertTrue(straight.failure().isEmpty() && stopped[0].failure().isEmpty(), "A plan failed");
            return straight.done() && stopped[0].done();
        }, () -> {
            helper.assertTrue(restarts.size() == 2, "The plan was not stopped twice: " + restarts);
            LandPlanning.Job job = stopped[0];
            helper.assertTrue(Arrays.equals(straight.progress().thrones(), job.progress().thrones()), "The thrones differ: "
                    + Arrays.toString(straight.progress().thrones()) + " and " + Arrays.toString(job.progress().thrones()));
            helper.assertTrue(straightMap.provinces().equals(stoppedMap.provinces()), "The provinces differ");
            long[] keys = straightMap.tileKeys();
            long[] other = stoppedMap.tileKeys();
            Arrays.sort(keys);
            Arrays.sort(other);
            helper.assertTrue(keys.length > 0 && Arrays.equals(keys, other), "The tiles differ: " + keys.length + " and " + other.length);
            for (long key : keys) {
                helper.assertTrue(Arrays.equals(straightMap.tile(key), stoppedMap.tile(key)), "The tile " + ProvinceMap.tileX(key) + " "
                        + ProvinceMap.tileZ(key) + " differs");
            }
            for (Stage stage : Stage.values()) {
                if (stage != Stage.DONE && stage != Stage.SETTLE && stage != Stage.ROADS && stage != Stage.FOUND) {
                    helper.assertTrue(job.progress().ticks(stage) > 0, "The stage " + stage + " has no ticks");
                }
            }
            helper.succeed();
        }, () -> {
        }, () -> straight.progress() + " " + Arrays.toString(straight.stageWork()) + ", " + stopped[0].progress() + " "
                + Arrays.toString(stopped[0].stageWork()));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_fault", timeoutTicks = 24_000)
    public static void aBlockOfTheNoiseThatFailsOnceIsAskedAgainAndThePlanEndsTheSame(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProvinceMap straightMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        ProvinceMap faultyMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        LandPlanning.Setup setup = ring(landedCenter(server, FAR, -FAR), 0);
        LandPlanning.Job straight = new LandPlanning.Job(server, setup, new SurveySavedData(), straightMap, null, false, () -> {
        });
        LandPlanning.Job faulty = new LandPlanning.Job(server, setup, new SurveySavedData(), faultyMap, null, false, () -> {
        });
        // The second block of the survey and the first block of the borders fail once each; the blocks after them come first.
        java.util.concurrent.atomic.AtomicInteger surveyFaults = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger borderFaults = new java.util.concurrent.atomic.AtomicInteger();
        faulty.faults(from -> from > 0 && surveyFaults.compareAndSet(0, 1), from -> borderFaults.compareAndSet(0, 1));
        repeat(helper, 0, 20_000, () -> {
            if (!straight.done()) {
                straight.tick();
            }
            if (!faulty.done()) {
                faulty.tick();
            }
            helper.assertTrue(straight.failure().isEmpty() && faulty.failure().isEmpty(), "A plan failed: "
                    + faulty.failure().or(straight::failure).orElse(""));
            return straight.done() && faulty.done();
        }, () -> {
            helper.assertTrue(surveyFaults.get() == 1, "No block of the survey failed");
            helper.assertTrue(borderFaults.get() == 1 || faulty.summary().getInt("probeCells") == 0, "No block of the borders failed");
            helper.assertTrue(Arrays.equals(straight.progress().thrones(), faulty.progress().thrones()), "The thrones differ: "
                    + Arrays.toString(straight.progress().thrones()) + " and " + Arrays.toString(faulty.progress().thrones()));
            helper.assertTrue(straightMap.provinces().equals(faultyMap.provinces()), "The provinces differ");
            long[] keys = straightMap.tileKeys();
            long[] other = faultyMap.tileKeys();
            Arrays.sort(keys);
            Arrays.sort(other);
            helper.assertTrue(keys.length > 0 && Arrays.equals(keys, other), "The tiles differ: " + keys.length + " and " + other.length);
            for (long key : keys) {
                helper.assertTrue(Arrays.equals(straightMap.tile(key), faultyMap.tile(key)), "The tile " + ProvinceMap.tileX(key) + " "
                        + ProvinceMap.tileZ(key) + " differs");
            }
            helper.succeed();
        }, () -> {
        }, () -> straight.progress() + " " + Arrays.toString(straight.stageWork()) + ", " + faulty.progress() + " "
                + Arrays.toString(faulty.stageWork()));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_fault_stop", timeoutTicks = 6_000)
    public static void aBlockOfTheNoiseThatAlwaysFailsStopsThePlanWhereItIs(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LandPlanning.Job job = new LandPlanning.Job(server, ring(new int[]{-FAR, -FAR}, 0), new SurveySavedData(),
                new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION), null, false, () -> {
        });
        // The third block of the survey (cells 128 to 191) never answers.
        int failing = 128;
        job.faults(from -> from == failing, from -> false);
        int[] after = {-1};
        repeat(helper, 0, 5_000, () -> {
            job.tick();
            if (job.failure().isEmpty()) {
                return false;
            }
            // The plan stopped: twenty more ticks move nothing, and nothing of the blocks after the failing one was put in its place.
            after[0]++;
            helper.assertTrue(job.survey().nextCell() == failing && !job.survey().complete(), "The survey went past the failing block to cell "
                    + job.survey().nextCell());
            helper.assertTrue(job.progress().stage() == Stage.SURVEY, "The plan went on to " + job.progress().stage());
            return after[0] >= 20;
        }, helper::succeed, () -> {
        }, () -> job.progress() + " at cell " + job.survey().nextCell() + ", " + job.failure().orElse("no failure"));
    }

    /**
     * The plan as a restart finds it: read from what its files keep, nothing of its memory.
     */
    private static LandPlanning.Job restart(MinecraftServer server, LandPlanning.Job job, ProvinceMap map) {
        CompoundTag plan = job.write();
        CompoundTag survey = job.survey().write(new CompoundTag());
        LandPlanning.Job read = LandPlanning.Job.read(server, plan, SurveySavedData.load(survey), map, null, false, () -> {
        });
        if (read == null) {
            throw new IllegalStateException("The plan kept cannot be read");
        }
        return read;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_legacy")
    public static void aWorldThatDoesNotAskForStatesKeepsThePlanOfCapitals(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(!NpcSettings.states(), "The config of the tests asks for the plan of states");
        helper.assertTrue(!LandPlanning.wanted(false, false, false), "A new world not asking for states got them");
        helper.assertTrue(!LandPlanning.wanted(false, false, true), "A world of capitals got states");
        helper.assertTrue(LandPlanning.wanted(false, true, false), "A new world asking for states did not get them");
        helper.assertTrue(!LandPlanning.wanted(false, true, true), "A plan of capitals begun was replaced by states");
        helper.assertTrue(LandPlanning.wanted(true, false, false), "A plan of states begun stopped when the config changed");
        helper.assertTrue(!LandPlanning.wanted(server, false), "The world of the tests got the plan of states");
        helper.assertTrue(!ProvinceSavedData.get(server).hasPlan(), "The world of the tests has a plan of states");
        helper.assertTrue(LandPlanning.describe(server).isEmpty(), "The world of the tests describes a plan of states");
        helper.succeed();
    }
}
