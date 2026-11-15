package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.CapitalRepair;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of capitals of the world raised whole: one put aside and started again in the middle of its buildings, one whose land
 * went away under its raising, one whose raising failed half way, and one missing buildings that is raised again.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapitalRepairGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TICKS = 12_000;

    private CapitalRepairGameTests() {
    }

    private static NpcSite plant(GameTestHelper helper, BlockPos center) {
        MinecraftServer server = helper.getLevel().getServer();
        NpcSettings.override(NpcRules.DEFAULT);
        helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
        NpcSite site = NpcWork.plantAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of(), NpcSite.LAYOUT_DISTRICTS, "").orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        return site;
    }

    /**
     * What is wrong with a capital that stood up: buildings of its plan missing, more citizens than beds; empty if nothing.
     */
    private static String whole(MinecraftServer server, NpcSite site) {
        if (site.status() != NpcSite.Status.SPAWNED || site.stage() != SpawnStage.DONE) {
            return "The appearance did not end: " + site + " at " + site.stage().id() + " " + site.reason();
        }
        NpcComposition composition = NpcSpawner.composition(site);
        List<NpcSpawner.Planned> missing = NpcSpawner.missing(server, site, composition);
        if (!missing.isEmpty()) {
            return missing.size() + " buildings of the plan are missing: " + missing;
        }
        UUID id = site.kingdomId().orElseThrow();
        var state = KingdomWorldApi.state(server);
        int citizens = state.citizens().registry().citizensOf(id).size();
        int beds = state.citizens().sites().count(id, SiteKinds.BED);
        LOGGER.info("Capital repair: {} stands with {} buildings, {} citizens, {} beds", site, state.construction().buildingsOf(id).size(),
                citizens, beds);
        return "";
    }

    private static void end(GameTestHelper helper, NpcSite site, BlockPos center, String problem) {
        NpcGameTests.cleanUp(helper, site, center);
        NpcSettings.override(null);
        if (problem != null && !problem.isEmpty()) {
            helper.fail(problem);
        }
        helper.succeed();
    }

    /**
     * A capital rising in ticks is put aside in the middle of its buildings and started again at once, as when a player passes
     * a settlement: its chunks are let go and asked for again, and every building of its plan stands at the end.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_capital_again", timeoutTicks = TICKS + 100)
    public static void aCapitalPutAsideAndStartedAgainRisesWhole(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = plant(helper, center);
        long started = server.getTickCount();
        boolean[] again = {false};
        helper.onEachTick(() -> {
            if (!again[0] && site.status() == NpcSite.Status.SPAWNING && site.stage() == SpawnStage.BUILD && site.step() >= 1) {
                again[0] = true;
                NpcSpawner.putAside(server, site);
                NpcSpawner.start(server, site, false);
                return;
            }
            if (site.status() == NpcSite.Status.SPAWNING && server.getTickCount() - started <= TICKS) {
                return;
            }
            String problem = again[0] ? whole(server, site) : "The capital was never put aside at its buildings";
            end(helper, site, center, problem);
        });
    }

    /**
     * The land of a capital rising in ticks goes away in the middle of its buildings while the raising still counts the chunks as
     * its own: the buildings wait for their land, the chunks are asked for again, and every building of the plan stands at the
     * end.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_capital_land", timeoutTicks = TICKS + 100)
    public static void aCapitalWhoseLandWentAwayWaitsForItAndRisesWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // Far from the chunks around the spawn, which the game keeps loaded whatever holds them.
        BlockPos center = helper.absolutePos(BlockPos.ZERO).offset(3_000, 0, 0);
        NpcSite site = plant(helper, center);
        long started = server.getTickCount();
        int[] phase = {0};
        long[] dropped = {0};
        int[] unloaded = {0};
        helper.onEachTick(() -> {
            if (phase[0] == 0 && site.status() == NpcSite.Status.SPAWNING && site.stage() == SpawnStage.BUILD && site.step() >= 1) {
                // Nobody plays: the raising waits; its tickets go, and the chunks with them.
                NpcSpawner.setBackground(true);
                NpcSpawner.dropTicketsForTests(level);
                dropped[0] = server.getTickCount();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 1) {
                int gone = NpcSpawner.unloadedForTests(level);
                if (gone < unloaded[0] + 1 && server.getTickCount() - dropped[0] < 1_200) {
                    unloaded[0] = Math.max(unloaded[0], gone);
                    return;
                }
                unloaded[0] = gone;
                LOGGER.info("Capital repair: {} chunks of the land of {} went away in {} ticks", gone, site, server.getTickCount() - dropped[0]);
                NpcSpawner.setBackground(false);
                phase[0] = 2;
                return;
            }
            if (site.status() == NpcSite.Status.SPAWNING && server.getTickCount() - started <= TICKS) {
                return;
            }
            String problem = phase[0] != 2 ? "The land of the capital never went away at its buildings"
                    : unloaded[0] == 0 ? "No chunk of the capital went away when its tickets went" : whole(server, site);
            NpcWork.remove(server, site.id(), true);
            NpcSettings.override(null);
            if (problem != null && !problem.isEmpty()) {
                helper.fail(problem);
            }
            helper.succeed();
        });
    }

    /**
     * A raising that fails half way keeps its place founded with its stage, gives the empty capital no recruits, waits a day, and
     * then rises whole.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_capital_failure", timeoutTicks = TICKS + 100)
    public static void aRaisingThatFailsHalfWayGoesOnLater(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = plant(helper, center);
        long started = server.getTickCount();
        int[] phase = {0};
        helper.onEachTick(() -> {
            if (phase[0] == 0 && site.status() == NpcSite.Status.SPAWNING && site.stage() == SpawnStage.BUILD && site.step() >= 1) {
                NpcSpawner.failNextStepForTests();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 1) {
                if (site.status() == NpcSite.Status.SPAWNING && server.getTickCount() - started <= TICKS) {
                    return;
                }
                String problem = null;
                UUID id = site.kingdomId().orElse(null);
                if (site.status() != NpcSite.Status.FOUNDED || site.stage() != SpawnStage.BUILD) {
                    problem = "The failed raising is " + site.status() + " at " + site.stage().id() + " instead of founded at its buildings";
                } else if (id == null || NpcWork.built(server, id)) {
                    problem = "The half raised capital counts as standing";
                } else if (NpcSpawner.mayRetry(server, site)) {
                    problem = "The failed raising goes on at once";
                }
                if (problem != null) {
                    end(helper, site, center, problem);
                    return;
                }
                // A day later it goes on.
                NpcSpawner.forgetFailures(site.id());
                NpcSpawner.start(server, site, false);
                phase[0] = 2;
                return;
            }
            if (phase[0] == 2 && site.status() == NpcSite.Status.SPAWNING && server.getTickCount() - started <= TICKS) {
                return;
            }
            if (phase[0] == 0 && server.getTickCount() - started <= TICKS) {
                return;
            }
            end(helper, site, center, phase[0] == 2 ? whole(server, site) : "The raising never reached its buildings");
        });
    }

    /**
     * A small capital missing three buildings of its plan is raised again: they come back where they stood, and the rest of the
     * capital, its citizens and its treasury stay as they were.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_capital_repair", timeoutTicks = 400)
    public static void aCapitalMissingBuildingsIsRaisedAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSite site = null;
        try {
            site = NpcGameTests.plant(helper, center);
            Kingdom kingdom = NpcGameTests.raise(helper, site, NpcGameTests.STEPS);
            UUID id = kingdom.id();
            var state = KingdomWorldApi.state(server);
            helper.assertTrue(NpcSpawner.missing(server, site, NpcSpawner.composition(site)).isEmpty(), "The capital did not rise whole");
            int citizens = state.citizens().registry().citizensOf(id).size();
            long balance = KingdomWorldApi.treasury(server, id).orElseThrow().balance();
            // Three buildings of the plan go, their records and their blocks.
            List<Building> all = new ArrayList<>(state.construction().buildingsOf(id));
            List<NpcSpawner.Planned> planned = NpcSpawner.planned(server, site, NpcSpawner.composition(site));
            Map<String, Long> kept = new HashMap<>();
            List<String> taken = new ArrayList<>();
            for (Building building : all) {
                boolean ofPlan = planned.stream().anyMatch(p -> p.template().equals(building.templateId()));
                if (taken.size() < 3 && ofPlan && !building.templateId().contains("castle") && !building.templateId().contains("well")) {
                    Box box = building.box();
                    state.construction().removeBuilding(building.id());
                    GatheringGameTests.clear(level, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
                    taken.add(building.templateId() + "@" + building.anchor().x() + "," + building.anchor().z());
                } else {
                    kept.put(building.templateId() + "@" + building.anchor().x() + "," + building.anchor().z(), building.id());
                }
            }
            helper.assertTrue(taken.size() == 3, "Three buildings of the plan were not found: " + taken);
            io.github.sekerator.kingdomworld.common.construction.BuildingEffects.changed();
            CapitalRepair.Look look = CapitalRepair.repair(server, site);
            helper.assertTrue(look.outcome() == CapitalRepair.Outcome.REBUILT && look.missing().size() == 3,
                    "The repair saw " + look.outcome() + " with " + look.missing().size() + " missing");
            helper.assertTrue(site.status() == NpcSite.Status.FOUNDED && site.given(NpcSite.REPAIRED) && site.given(NpcSite.REBUILDING),
                    "The capital is not set to rise again: " + site.status());
            helper.assertTrue(!NpcWork.built(server, id), "A capital rising again counts as standing");
            NpcSpawner.runNow(server, site, NpcGameTests.STEPS);
            String problem = whole(server, site);
            helper.assertTrue(problem.isEmpty(), problem);
            helper.assertTrue(!site.given(NpcSite.REBUILDING) && site.reason().isEmpty(), "The capital still rises again: " + site.reason());
            for (String building : taken) {
                boolean back = state.construction().buildingsOf(id).stream()
                        .anyMatch(b -> (b.templateId() + "@" + b.anchor().x() + "," + b.anchor().z()).equals(building));
                helper.assertTrue(back, building + " did not come back where it stood");
            }
            for (Map.Entry<String, Long> entry : kept.entrySet()) {
                boolean same = state.construction().buildingsOf(id).stream()
                        .anyMatch(b -> b.id() == entry.getValue() && (b.templateId() + "@" + b.anchor().x() + "," + b.anchor().z()).equals(entry.getKey()));
                helper.assertTrue(same, entry.getKey() + " is no longer the building " + entry.getValue());
            }
            helper.assertTrue(state.construction().buildingsOf(id).size() == all.size(), "Buildings " + state.construction().buildingsOf(id).size()
                    + " instead of " + all.size());
            int now = state.citizens().registry().citizensOf(id).size();
            helper.assertTrue(now == citizens, "Citizens " + now + " instead of " + citizens);
            long after = KingdomWorldApi.treasury(server, id).orElseThrow().balance();
            helper.assertTrue(after == balance, "The treasury holds " + after + " instead of " + balance);
            // Looked at once: the next start of the server leaves it alone.
            helper.assertTrue(site.given(NpcSite.REPAIRED), "The capital is not marked looked at");
        } finally {
            NpcGameTests.cleanUp(helper, site, center);
            NpcSettings.override(null);
        }
        helper.succeed();
    }
}
