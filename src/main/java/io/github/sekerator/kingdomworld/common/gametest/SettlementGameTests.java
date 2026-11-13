package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.LandPlanning;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.SurveySavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.npc.SettlementWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.PlanProgress.Stage;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.PlannedRoad;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Game tests of the settlements of the states as data: a small ring planned and founded gets its settlements by rank in its
 * provinces with the roads between them; a plan stopped while it places its settlements goes on to the same places; and sixty
 * days of the numbers of the settlements touch no treasury and no storehouse.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementGameTests {
    private static final String EMPTY = "empty";
    /** The middle of the rings of these tests, far from the land of every other test, those of the plan of states included. */
    private static final int FAR = 260_000;
    /** The provinces of these tests are numbered after this, apart from those of the other tests of the plan. */
    private static final int BASE = 56_000;

    private SettlementGameTests() {
    }

    private static LandPlanning.Setup ring(int x, int z, int base) {
        return new LandPlanning.Setup(StateRules.defaults(x, z).withQuota(2, 0, 0).withRing(500, 2500), ProvinceRules.DEFAULT, 0x5E77L, base);
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

    private static void cleanUp(MinecraftServer server, TerritoryIndex territory, int[] sites) {
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (Province province : map.provinces()) {
            if (province.id() > BASE && province.id() < BASE + 4_000) {
                territory.clearProvince(province.id());
            }
        }
        for (int site : sites) {
            if (site >= 0) {
                NpcWork.remove(server, site, false);
            }
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_plan", timeoutTicks = 24_000)
    public static void aSmallRingGetsItsSettlementsByRankInItsProvinces(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        cleanUp(server, territory, new int[0]);
        LandPlanning.Job job = new LandPlanning.Job(server, ring(FAR, -FAR, BASE), new SurveySavedData(), map, territory, true, () -> {
        });
        repeat(helper, 0, 20_000, () -> {
            job.tick();
            for (int id : job.sites()) {
                Optional<NpcSite> site = id < 0 ? Optional.empty() : NpcWork.state(server).site(id);
                if (site.isPresent() && site.get().status() == NpcSite.Status.PLANNED) {
                    NpcWork.foundNow(server, site.get());
                }
            }
            helper.assertTrue(job.failure().isEmpty(), "The plan failed: " + job.failure().orElse(""));
            return job.done();
        }, () -> {
            NpcState npc = NpcWork.state(server);
            int[] sites = job.sites();
            List<int[]> provinces = job.stateProvinces();
            List<int[]> written = job.stateSettlements();
            Set<String> names = new HashSet<>();
            Set<Integer> all = new HashSet<>();
            for (int i = 0; i < sites.length; i++) {
                NpcSite site = npc.site(sites[i]).orElseThrow();
                UUID kingdom = site.kingdomId().orElseThrow();
                List<Settlement> own = npc.settlementsOf(kingdom);
                helper.assertTrue(own.size() == written.get(i).length && own.size() >= 3, "The state " + i + " has " + own.size()
                        + " settlement(s), " + written.get(i).length + " written");
                Set<Integer> ownProvinces = new HashSet<>();
                Arrays.stream(provinces.get(i)).forEach(ownProvinces::add);
                Map<Tier, Integer> byTier = new HashMap<>();
                for (Settlement settlement : own) {
                    byTier.merge(settlement.tier(), 1, Integer::sum);
                    all.add(settlement.id());
                    helper.assertTrue(names.add(settlement.name().latin()), "The name " + settlement.name().latin() + " repeats");
                    helper.assertTrue(settlement.status() == Settlement.Status.DATA, settlement + " is not data");
                    helper.assertTrue(ownProvinces.contains(settlement.province()), settlement + " is not in a province of its state");
                    if (settlement.tier() != Tier.CAPITAL) {
                        int[] range = SettlementLife.people(settlement.tier());
                        helper.assertTrue(settlement.housing() >= range[0] && settlement.housing() <= range[1], settlement + " has "
                                + settlement.housing() + " houses");
                        helper.assertTrue(settlement.population() > 0 && settlement.population() <= settlement.housing(), settlement
                                + " has no people");
                        helper.assertTrue(settlement.parent() != 0 && npc.settlement(settlement.parent()).isPresent(), settlement
                                + " belongs to no settlement");
                    }
                }
                helper.assertTrue(byTier.getOrDefault(Tier.CAPITAL, 0) == 1, "The state " + i + " has not one capital: " + byTier);
                helper.assertTrue(byTier.getOrDefault(Tier.CITY, 0) + byTier.getOrDefault(Tier.TOWN, 0) > 0, "The state " + i
                        + " has no town: " + byTier);
                // The seat of a province is its main settlement, one of its state.
                for (int id : provinces.get(i)) {
                    long seat = map.province(id).map(Province::seat).orElse(0L);
                    helper.assertTrue(seat == 0 || own.stream().anyMatch(s -> s.id() == seat && s.province() == id), "The seat " + seat
                            + " of the province " + id + " is not one of its settlements");
                }
            }
            int roads = 0;
            for (PlannedRoad road : npc.plannedRoads()) {
                if (all.contains(road.from()) || all.contains(road.to())) {
                    helper.assertTrue(all.contains(road.from()) && all.contains(road.to()), "A road of the plan leads out of the ring");
                    roads++;
                }
            }
            helper.assertTrue(roads >= all.size() - sites.length, roads + " road(s) for " + all.size() + " settlements");
            helper.succeed();
        }, () -> cleanUp(server, territory, job.sites()), () -> job.progress() + ", " + Arrays.toString(job.stageWork()));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_restart", timeoutTicks = 24_000)
    public static void aPlanStoppedWhilePlacingItsSettlementsGoesOnToTheSamePlaces(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // Both plans are made into maps of their own, apart from the land of the world, and found nothing.
        ProvinceMap straightMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        ProvinceMap stoppedMap = new ProvinceMap(ProvinceSavedData.DEFAULT_DIMENSION);
        LandPlanning.Setup setup = ring(-FAR, -FAR, 0);
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
            job.tick();
            if (restarts.isEmpty() && job.progress().stage() == Stage.SETTLE && job.progress().cursor() > 0) {
                int before = job.progress().cursor();
                int[] judged = job.settleChoices();
                stopped[0] = restart(server, job, stoppedMap);
                helper.assertTrue(stopped[0].progress().stage() == Stage.SETTLE && stopped[0].progress().cursor() == before,
                        "The plan went back: " + stopped[0].progress());
                // What was judged before the stop is kept and not judged again.
                int[] kept = stopped[0].settleChoices();
                for (int i = 0; i < before; i++) {
                    helper.assertTrue(kept[i] == judged[i] && kept[i] != -2, "The place of settlement " + i + " was lost");
                }
                restarts.add("settle at " + before);
            }
            helper.assertTrue(straight.failure().isEmpty() && stopped[0].failure().isEmpty(), "A plan failed");
            return straight.done() && stopped[0].done();
        }, () -> {
            helper.assertTrue(restarts.size() == 1, "The plan was not stopped while placing its settlements: " + restarts);
            int[] straightChoices = straight.settleChoices();
            int[] stoppedChoices = stopped[0].settleChoices();
            helper.assertTrue(straightChoices.length > 0 && Arrays.equals(straightChoices, stoppedChoices), "The places differ: "
                    + Arrays.toString(straightChoices) + " and " + Arrays.toString(stoppedChoices));
            helper.assertTrue(Arrays.stream(stoppedChoices).noneMatch(choice -> choice == -2), "A settlement was never judged");
            CompoundTag a = straight.summary();
            CompoundTag b = stopped[0].summary();
            helper.assertTrue(a.getInt("roads") == b.getInt("roads") && a.getInt("roads") > 0, "The roads differ: " + a.getInt("roads") + " and "
                    + b.getInt("roads"));
            helper.assertTrue(Arrays.equals(a.getIntArray("settlements"), b.getIntArray("settlements")), "The settlements by rank differ");
            helper.succeed();
        }, () -> {
        }, () -> straight.progress() + ", " + stopped[0].progress());
    }

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

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_days")
    public static void sixtyDaysOfTheNumbersTouchNoTreasuryAndNoStorehouse(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Settled Realm", UUID.randomUUID());
        NpcState npc = NpcWork.state(server);
        List<Settlement> made = new ArrayList<>();
        try {
            long seed = 11;
            for (Tier tier : List.of(Tier.CITY, Tier.TOWN, Tier.VILLAGE, Tier.HAMLET)) {
                Settlement settlement = npc.addSettlement(kingdom.id(), 0, 0, tier, 0, 0, seed, new NpcNames.Name("Gametest " + tier, "Тест " + tier));
                int housing = SettlementLife.housing(tier, seed++);
                // A city on poor land goes hungry, the rest are fed.
                settlement.found(housing, SettlementLife.startingPopulation(housing), tier == Tier.CITY ? 50 : 130);
                made.add(settlement);
            }
            Map<UUID, Long> funds = new HashMap<>();
            for (Treasury treasury : KingdomWorldApi.state(server).economy().treasuries()) {
                funds.put(treasury.kingdomId(), treasury.funds());
            }
            Map<String, Long> stock = KingdomStorage.countsById(server, kingdom.id());
            int[] before = made.stream().mapToInt(Settlement::population).toArray();
            for (long day = 1; day <= 61; day++) {
                for (Settlement settlement : made) {
                    SettlementWork.live(settlement, day);
                }
            }
            for (Treasury treasury : KingdomWorldApi.state(server).economy().treasuries()) {
                helper.assertTrue(funds.containsKey(treasury.kingdomId()) && funds.get(treasury.kingdomId()) == treasury.funds(),
                        "The treasury of " + treasury.kingdomId() + " changed");
            }
            helper.assertTrue(funds.size() == KingdomWorldApi.state(server).economy().treasuries().size(), "A treasury came or went");
            helper.assertTrue(stock.equals(KingdomStorage.countsById(server, kingdom.id())), "The storehouses of the kingdom changed");
            int[] after = made.stream().mapToInt(Settlement::population).toArray();
            helper.assertTrue(after[0] < before[0], "The hungry city did not lose people: " + before[0] + " -> " + after[0]);
            for (int i = 1; i < made.size(); i++) {
                helper.assertTrue(after[i] == made.get(i).housing(), made.get(i) + " did not fill its houses in sixty days");
            }
            helper.assertTrue(made.stream().allMatch(settlement -> settlement.lastDay() == 61), "A settlement missed a day");
            helper.succeed();
        } finally {
            npc.removeSettlementsOf(kingdom.id());
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
    }
}
