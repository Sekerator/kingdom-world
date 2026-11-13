package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientProvinceMap;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.LandPlanning;
import io.github.sekerator.kingdomworld.common.land.SurveyProbes;
import io.github.sekerator.kingdomworld.common.land.SurveySavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The provinces part of the client self-check ({@code -PclientSelfTest=provinces}): in a copy of a test world a ring of four
 * states (two small, a medium and a large one) is planned far from the player with the plan of states, founded and given its
 * provinces; once the provinces reached the client the atlas is opened over them and screenshots go into the folder
 * {@code provinces} of the screenshot folder: the whole ring, the provinces of a state with their names, a capital with the
 * borders of its provinces and the pointer on a province, and wild land. The states stay in the world.
 */
final class ClientProvinceShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks the plan may take at most. */
    private static final int MAX_WAIT = 24_000;
    /** Ticks after the plan is made before the first shot: the provinces reach the client a part a tick. */
    private static final int SETTLE = 120;
    /** Ticks after a shot is set up before it is taken. */
    private static final int SHOT_WAIT = 40;
    private static final int RADIUS = 4000;

    private final Path folder;
    private int ticks;
    private boolean started;
    private volatile boolean planned;
    private volatile boolean failedOnServer;
    private volatile int[] center;
    private volatile List<int[]> capitals = List.of();
    private volatile String report = "";
    private LandPlanning.Job job;
    private int shot = -1;
    private int wait;
    private boolean finished;
    private boolean failed;

    ClientProvinceShowcase(Path folder) {
        this.folder = folder.resolve("provinces");
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        ticks++;
        if (!started) {
            started = true;
            server.execute(() -> start(server));
            return;
        }
        if (failedOnServer) {
            fail("the plan of states could not be made: " + report);
            return;
        }
        if (!planned) {
            if (ticks > MAX_WAIT) {
                fail("the plan of states was not made in time: " + (job == null ? "not begun" : job.progress()));
                return;
            }
            server.execute(() -> step(server));
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (shot >= 0) {
            shoot(minecraft, SHOTS[shot]);
        }
        shot++;
        if (shot >= SHOTS.length) {
            minecraft.setScreen(null);
            LOGGER.info(PREFIX + "provinces showcase finished: {}; the client knows {} province(s) in {} tile(s), about {} KB", report,
                    ClientProvinceMap.provinces().size(), ClientProvinceMap.tileCount(), ClientProvinceMap.memoryBytes() / 1024);
            finished = true;
            return;
        }
        setUp(minecraft, shot);
        wait = shot == 0 ? SETTLE : SHOT_WAIT;
    }

    private static final String[] SHOTS = {"atlas-ring", "atlas-state", "atlas-capital", "atlas-wild"};

    /**
     * The atlas of a shot: where it looks and how near.
     */
    private void setUp(Minecraft minecraft, int index) {
        int[] ring = center;
        int[] capital = capitals.isEmpty() ? ring : capitals.get(capitals.size() - 1);
        KingdomMapScreen atlas = new KingdomMapScreen(null);
        minecraft.setScreen(atlas);
        int width = minecraft.getWindow().getGuiScaledWidth();
        switch (index) {
            case 0 -> atlas.showForCheck(ring[0] / 16.0, ring[1] / 16.0, Math.max(0.2, (width - 16) / (2.0 * RADIUS / 16 + 80)), null,
                    Component.empty(), false);
            case 1 -> atlas.showForCheck(capital[0] / 16.0, capital[1] / 16.0, 1.2, null, Component.empty(), false);
            case 2 -> {
                atlas.showForCheck(capital[0] / 16.0, capital[1] / 16.0, 5.0, null, Component.empty(), false);
                // The pointer on the province beside the capital, so that the footer tells it.
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), minecraft.getWindow().getScreenWidth() * 0.6,
                        minecraft.getWindow().getScreenHeight() * 0.5);
            }
            default -> {
                // Between the spawn zone and the states: the neutral land of the ring.
                double angle = Math.atan2(capital[1] - ring[1], capital[0] - ring[0]) + Math.PI;
                atlas.showForCheck((ring[0] + Math.cos(angle) * RADIUS * 0.5) / 16.0, (ring[1] + Math.sin(angle) * RADIUS * 0.5) / 16.0, 2.0,
                        null, Component.empty(), false);
            }
        }
    }

    private void shoot(Minecraft minecraft, String name) {
        Path file = folder.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            LOGGER.info(PREFIX + "screenshot of {} saved to {}", name, file);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not save the screenshot of {}", name, e);
            failed = true;
        }
    }

    private void fail(String why) {
        LOGGER.error(PREFIX + "provinces showcase failed: {}", why);
        failed = true;
        finished = true;
    }

    // ---------------------------------------------------------------- on the server

    /**
     * Begins the plan of a ring of four states around the place with the most land a few thousand blocks from the player.
     */
    private void start(IntegratedServer server) {
        try {
            ServerLevel level = server.overworld();
            var player = server.getPlayerList().getPlayers().get(0);
            int[] found = landed(level, (int) player.getX() + 12_000, (int) player.getZ());
            center = found;
            TerritoryIndex territory = LandEvents.attach(server);
            ProvinceMap map = territory.provinceMap().orElseThrow();
            int base = 0;
            for (Province province : map.provinces()) {
                base = Math.max(base, province.id());
            }
            LandPlanning.Setup setup = new LandPlanning.Setup(StateRules.defaults(found[0], found[1]).withQuota(2, 1, 1).withRing(800, RADIUS),
                    ProvinceRules.DEFAULT, level.getSeed() ^ 0x5157L, base);
            job = new LandPlanning.Job(server, setup, new SurveySavedData(), map, territory, true, () -> {
            });
            LOGGER.info(PREFIX + "provinces showcase: a ring of states around {} {}", found[0], found[1]);
        } catch (RuntimeException e) {
            report = e.toString();
            failedOnServer = true;
        }
    }

    private void step(IntegratedServer server) {
        if (job == null || planned) {
            return;
        }
        try {
            job.tick();
            for (int id : job.sites()) {
                Optional<NpcSite> site = id < 0 ? Optional.empty() : NpcWork.state(server).site(id);
                if (site.isPresent() && site.get().status() == NpcSite.Status.PLANNED) {
                    NpcWork.foundNow(server, site.get());
                }
            }
            if (job.failure().isPresent()) {
                report = job.failure().get();
                failedOnServer = true;
            } else if (job.done()) {
                List<int[]> list = new ArrayList<>();
                for (int id : job.sites()) {
                    NpcWork.state(server).site(id).ifPresent(site -> list.add(new int[]{site.x(), site.z()}));
                }
                capitals = list;
                report = job.report();
                planned = true;
            }
        } catch (RuntimeException e) {
            report = e.toString();
            failedOnServer = true;
        }
    }

    /**
     * The middle of a ring with the most dry land among a few around a place.
     */
    private static int[] landed(ServerLevel level, int x, int z) {
        int sea = level.getSeaLevel();
        int[] best = {x, z};
        int bestLand = -1;
        for (int i = 0; i < 9; i++) {
            int cx = x + (i % 3 - 1) * 9_000;
            int cz = z + (i / 3 - 1) * 9_000;
            int land = 0;
            for (int px = -RADIUS; px <= RADIUS; px += 800) {
                for (int pz = -RADIUS; pz <= RADIUS; pz += 800) {
                    if ((long) px * px + (long) pz * pz > (long) RADIUS * RADIUS) {
                        continue;
                    }
                    int floor = SurveyProbes.floor(level, cx + px, cz + pz);
                    SurveyClassifier.Probe probe = SurveyProbes.probe(level, cx + px, floor, cz + pz);
                    if (floor > sea && probe != SurveyClassifier.Probe.OCEAN && probe != SurveyClassifier.Probe.RIVER) {
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
}
