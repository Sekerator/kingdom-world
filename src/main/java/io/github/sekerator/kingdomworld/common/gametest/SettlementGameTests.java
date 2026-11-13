package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.LandPlanning;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.SurveySavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.npc.SettlementWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.PlanProgress.Stage;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
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
    /**
     * The provinces of these tests are numbered after these, apart from those of the other tests of the plan and below the
     * last number of a province ({@link ProvinceMap#MAX_ID}); each test has {@value #SPAN} numbers of its own.
     */
    private static final int BASE = 56_000;
    private static final int RAISE_BASE = 52_000;
    private static final int FOLD_BASE = 48_000;
    private static final int HANDED_BASE = 44_000;
    private static final int WON_BASE = 40_000;
    private static final int SPAN = 4_000;

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

    private static void cleanUp(MinecraftServer server, TerritoryIndex territory, int base, int[] sites) {
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (Province province : List.copyOf(map.provinces())) {
            if (province.id() > base && province.id() < base + SPAN) {
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
        cleanUp(server, territory, BASE, new int[0]);
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
        }, () -> cleanUp(server, territory, BASE, job.sites()), () -> job.progress() + ", " + Arrays.toString(job.stageWork()));
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

    /**
     * Plans and founds a small ring of its own, then hands a settlement of a rank of its first state to a check.
     */
    private static void withSettlement(GameTestHelper helper, int x, int z, int base, Tier tier,
                                       java.util.function.BiConsumer<MinecraftServer, Settlement> check) {
        withSettlement(helper, x, z, base, tier, (server, settlement, sites) -> check.accept(server, settlement));
    }

    /**
     * Plans and founds a small ring of its own, then hands a settlement of a rank of its first state and the places of the
     * ring to a check.
     */
    private static void withSettlement(GameTestHelper helper, int x, int z, int base, Tier tier, SettlementCheck check) {
        MinecraftServer server = helper.getLevel().getServer();
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        cleanUp(server, territory, base, new int[0]);
        LandPlanning.Job job = new LandPlanning.Job(server, ring(x, z, base), new SurveySavedData(), map, territory, true, () -> {
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
            UUID kingdom = npc.site(job.sites()[0]).flatMap(NpcSite::kingdomId).orElseThrow();
            Settlement settlement = npc.settlementsOf(kingdom).stream().filter(one -> one.tier() == tier).findFirst().orElse(null);
            helper.assertTrue(settlement != null, "The first state has no settlement of rank " + tier);
            check.accept(server, settlement, job.sites());
            helper.succeed();
        }, () -> cleanUp(server, territory, base, job.sites()), () -> job.progress().toString());
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_raise", timeoutTicks = 24_000)
    public static void aVillageRisesAndGoesOnAfterARestartWithoutCopies(GameTestHelper helper) {
        withSettlement(helper, FAR, FAR, RAISE_BASE, Tier.VILLAGE, (server, village) -> {
            // Halfway: the raising stops as a restart stops it, and goes on from the stage and step the village kept.
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, village, 40);
            String stopped = village.stage() + " " + village.step();
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.resetForTests();
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, village, 20_000);
            helper.assertTrue(village.status() == Settlement.Status.STANDING, village + " does not stand, stopped at " + stopped);
            KingdomWorldState world = KingdomWorldApi.state(server);
            java.util.Set<String> buildings = new java.util.HashSet<>();
            for (io.github.sekerator.kingdomworld.core.construction.Building building : world.construction().buildingsOf(village.kingdomId())) {
                if (Math.abs(building.anchor().x() - village.x()) <= 32 && Math.abs(building.anchor().z() - village.z()) <= 32) {
                    helper.assertTrue(buildings.add(building.templateId() + "@" + building.anchor().x() + "," + building.anchor().z()),
                            "A building of " + village + " stands twice: " + building.templateId());
                }
            }
            helper.assertTrue(buildings.size() >= 4, village + " has " + buildings.size() + " building(s)");
            long seed = server.overworld().getSeed();
            int named = 0;
            for (Citizen citizen : world.citizens().registry().citizensOf(village.kingdomId())) {
                if (citizen.settlement() == village.id()) {
                    named++;
                }
            }
            helper.assertTrue(named > 0 && named <= village.housing() && named <= village.population(), named + " named citizens in " + village);
            for (int slot = 0; slot < named; slot++) {
                helper.assertTrue(world.citizens().get(CitizenLife.settlerId(seed, village.id(), slot)).isPresent(),
                        "The settler of slot " + slot + " of " + village + " is missing");
            }
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.ticketsHeld() == 0, "The village holds its chunks");
        });
    }

    private interface SettlementCheck {
        void accept(MinecraftServer server, Settlement settlement, int[] sites);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_handed", timeoutTicks = 24_000)
    public static void aVillageHandedOverWhileItRisesHousesAsManyAsItsStandingHousesHold(GameTestHelper helper) {
        withSettlement(helper, 0, -FAR, HANDED_BASE, Tier.VILLAGE, (server, village, sites) -> {
            NpcState npc = NpcWork.state(server);
            UUID winner = npc.site(sites[1]).flatMap(NpcSite::kingdomId).orElseThrow();
            UUID loser = village.kingdomId();
            // The village rises for its state until a house stands.
            for (int tick = 0; tick < 20_000 && village.status() != Settlement.Status.STANDING
                    && io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.standingHomes(server, village) == 0; tick++) {
                io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, village, 1);
            }
            helper.assertTrue(village.status() == Settlement.Status.RAISING, "The village stood up before it was handed over: " + village);
            // Handed over as the journal of conquests does it: its buildings first, then the village itself.
            var book = KingdomWorldApi.state(server).construction();
            for (io.github.sekerator.kingdomworld.core.construction.Building b : List.copyOf(book.buildingsOf(loser))) {
                if (Math.abs(b.anchor().x() - village.x()) <= 48 && Math.abs(b.anchor().z() - village.z()) <= 48) {
                    book.putBuilding(new io.github.sekerator.kingdomworld.core.construction.Building(b.id(), winner, b.templateId(), b.type(),
                            b.category(), b.role(), b.level(), b.materials(), b.anchor(), b.placement(), b.box(), b.fingerprint(), b.builtTime(),
                            b.admin(), b.integrity(), b.checked(), b.autoRepair(), b.damaged()));
                }
            }
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.putAside(server, village);
            helper.assertTrue(npc.moveSettlement(village.id(), winner), "The village did not change hands");
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, village, 20_000);
            helper.assertTrue(village.status() == Settlement.Status.STANDING, village + " does not stand");
            int standing = io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.standingHomes(server, village);
            int layout = npc.kingdom(winner).map(NpcKingdom::siteId).flatMap(npc::site)
                    .map(site -> io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.layout(village, site).homes()).orElse(-1);
            helper.assertTrue(standing > 0, "No house of the village stands");
            helper.assertTrue(village.housing() == standing, village + " houses " + village.housing() + " though its standing houses hold "
                    + standing + " (the whole layout of its new state " + layout + ")");
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_won", timeoutTicks = 24_000)
    public static void aSettlementOfAKingdomThatIsGoneKeepsNoSettlementOfPlayersWaiting(GameTestHelper helper) {
        withSettlement(helper, 0, FAR, WON_BASE, Tier.VILLAGE, (server, first, sites) -> {
            NpcState npc = NpcWork.state(server);
            UUID otherState = npc.site(sites[1]).flatMap(NpcSite::kingdomId).orElseThrow();
            Settlement second = npc.settlementsOf(otherState).stream().filter(one -> one.tier() == Tier.VILLAGE || one.tier() == Tier.HAMLET)
                    .findFirst().orElse(null);
            helper.assertTrue(second != null, "The second state has no village or hamlet");
            for (Settlement settlement : List.of(first, second)) {
                io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, settlement, 20_000);
                helper.assertTrue(settlement.status() == Settlement.Status.STANDING, settlement + " does not stand");
                io.github.sekerator.kingdomworld.common.npc.SettlementResidents.foldNow(server, settlement);
                helper.assertTrue(settlement.folded().length > 0, "Nobody of " + settlement + " folded");
            }
            // The one looked at first goes to a kingdom that is gone, the other to a kingdom of players that won it.
            Settlement gone = first.id() < second.id() ? first : second;
            Settlement won = gone == first ? second : first;
            UUID nobody = UUID.randomUUID();
            io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult created = KingdomWorldApi.createKingdom(server,
                    "GameTest Settlement Winners", null);
            helper.assertTrue(created instanceof io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created, "No kingdom of players: "
                    + created);
            UUID players = ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) created).kingdom().id();
            try {
                helper.assertTrue(npc.moveSettlement(gone.id(), nobody) && npc.moveSettlement(won.id(), players), "The settlements did not change hands");
                int goneFolded = gone.folded().length;
                int wonFolded = won.folded().length;
                for (int tick = 0; tick < 20; tick++) {
                    io.github.sekerator.kingdomworld.common.npc.SettlementResidents.tickNow(server);
                }
                helper.assertTrue(won.folded().length < wonFolded, "The citizens of the settlement the players won did not come back: "
                        + won.folded().length + " of " + wonFolded + " still folded");
                helper.assertTrue(gone.folded().length == goneFolded, "Citizens of a kingdom that is gone came back");
            } finally {
                npc.removeSettlementsOf(nobody);
                npc.removeSettlementsOf(players);
                KingdomWorldApi.dissolveKingdom(server, players);
            }
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_settlements_fold", timeoutTicks = 24_000)
    public static void theCitizensOfAVillageFoldAndComeBackAsThemselves(GameTestHelper helper) {
        withSettlement(helper, -FAR, FAR, FOLD_BASE, Tier.VILLAGE, (server, village) -> {
            io.github.sekerator.kingdomworld.common.npc.SettlementRaiser.runNow(server, village, 20_000);
            KingdomWorldState world = KingdomWorldApi.state(server);
            java.util.List<UUID> before = new java.util.ArrayList<>();
            java.util.Map<UUID, String> names = new java.util.HashMap<>();
            for (Citizen citizen : world.citizens().registry().citizensOf(village.kingdomId())) {
                if (citizen.settlement() == village.id()) {
                    before.add(citizen.id());
                    names.put(citizen.id(), citizen.nameSeed() + "/" + citizen.gender() + "/" + citizen.skin() + "/" + citizen.professionId());
                }
            }
            int people = village.population();
            Map<String, Long> stock = KingdomStorage.countsById(server, village.kingdomId());
            int folded = io.github.sekerator.kingdomworld.common.npc.SettlementResidents.foldNow(server, village);
            helper.assertTrue(folded == before.size(), folded + " of " + before.size() + " citizens folded");
            helper.assertTrue(village.population() == people, "Folding changed the people of " + village);
            helper.assertTrue(village.folded().length == folded, village.folded().length + " records for " + folded + " folded");
            for (UUID id : before) {
                helper.assertTrue(world.citizens().get(id).isEmpty(), "A folded citizen is still named");
            }
            // Folding again folds nothing and copies nothing.
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.SettlementResidents.foldNow(server, village) == 0, "Folded twice");
            int back = io.github.sekerator.kingdomworld.common.npc.SettlementResidents.unfoldNow(server, village);
            helper.assertTrue(back == before.size(), back + " of " + before.size() + " came back");
            for (UUID id : before) {
                Citizen citizen = world.citizens().get(id).orElse(null);
                helper.assertTrue(citizen != null, "A citizen did not come back");
                helper.assertTrue(names.get(id).equals(citizen.nameSeed() + "/" + citizen.gender() + "/" + citizen.skin() + "/" + citizen.professionId()),
                        "A citizen came back as another: " + names.get(id));
            }
            helper.assertTrue(village.folded().length == 0, "Records left after the citizens came back");
            Map<String, Long> after = KingdomStorage.countsById(server, village.kingdomId());
            for (Map.Entry<String, Long> entry : stock.entrySet()) {
                helper.assertTrue(after.getOrDefault(entry.getKey(), 0L) >= entry.getValue(), "Something of " + entry.getKey() + " was lost");
            }
        });
    }
}
