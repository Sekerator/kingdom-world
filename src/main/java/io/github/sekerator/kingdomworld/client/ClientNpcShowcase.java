package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The kingdoms of the world part of the client self-check ({@code -PclientSelfTest=npc}): in a copy of a test world a large
 * kingdom of the world appears on suitable land near the player the way it appears for any player (over ticks, with its towns
 * and roads), then the camera flies over it and saves screenshots of the capital, the wall, a gate, the market, the road to a
 * town, the town and the atlas into the folder {@code world} of the screenshot folder. The kingdom stays in the world.
 */
final class ClientNpcShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 100;
    /** Ticks the appearance may take at most. */
    private static final int MAX_WAIT = 36_000;

    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {
    }

    private final Path folder;
    private int ticks;
    private volatile boolean started;
    private volatile boolean ready;
    private volatile int siteId = -1;
    private volatile List<Shot> shots = List.of();
    private volatile double atlasX;
    private volatile double atlasZ;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch = Float.NaN;

    ClientNpcShowcase(Path folder) {
        this.folder = folder.resolve("world");
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
        if (finished || minecraft.player == null || server == null) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
        }
        ticks++;
        UUID playerId = minecraft.player.getUUID();
        if (ticks == 40) {
            server.execute(() -> prepare(server, playerId));
            return;
        }
        if (!ready) {
            if (started && ticks % 100 == 0) {
                server.execute(() -> look(server));
            }
            if (ticks > MAX_WAIT) {
                LOGGER.error(PREFIX + "the kingdom of the world did not appear in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            hold(minecraft);
            wait--;
            if (wait == 0) {
                shoot(index < shots.size() ? shots.get(index).name() : "atlas-kingdom");
            }
            return;
        }
        index++;
        if (index > shots.size()) {
            LOGGER.info(PREFIX + "kingdom of the world shown: {} screenshot(s)", shots.size() + 1);
            minecraft.setScreen(null);
            finished = true;
            return;
        }
        minecraft.options.hideGui = true;
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        if (index < shots.size()) {
            Shot shot = shots.get(index);
            teleport(server, playerId, shot);
        } else {
            yaw = Float.NaN;
            KingdomMapScreen atlas = new KingdomMapScreen(null);
            minecraft.setScreen(atlas);
            atlas.showForCheck(atlasX / 16.0, atlasZ / 16.0, 3.0, null, net.minecraft.network.chat.Component.empty(), false);
        }
        wait = SETTLE;
    }

    private void hold(Minecraft minecraft) {
        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        if (player == null || Float.isNaN(yaw)) {
            return;
        }
        player.getAbilities().flying = true;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private void teleport(IntegratedServer server, UUID playerId, Shot shot) {
        this.yaw = shot.yaw();
        this.pitch = shot.pitch();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.serverLevel().setDayTime(6_000);
                player.teleportTo(player.serverLevel(), shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
                player.setDeltaMovement(0, 0, 0);
            }
        });
    }

    private void shoot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
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

    /**
     * On the server: suitable land near the player, and a large kingdom of the world planted there.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ServerLevel level = player.serverLevel();
        level.setDayTime(6_000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        Optional<NpcSite> appeared = NpcWork.state(server).sites().stream()
                .filter(site -> site.status() == NpcSite.Status.SPAWNED && site.level() == NpcLevel.LARGE).findFirst();
        if (appeared.isPresent()) {
            siteId = appeared.get().id();
            started = true;
            return;
        }
        int[] spot = null;
        for (int ring = 1; ring <= 12 && spot == null; ring++) {
            for (int i = 0; i < 8 * ring && spot == null; i++) {
                double angle = i * Math.PI * 2 / (8 * ring);
                int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * ring * 96);
                int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * ring * 96);
                if (NpcWork.suitableLand(level, x, z, NpcLevel.LARGE)) {
                    spot = new int[]{x, z};
                }
            }
        }
        if (spot == null) {
            spot = new int[]{player.getBlockX() + 200, player.getBlockZ()};
            LOGGER.warn(PREFIX + "no flat land near the player; the kingdom of the world appears at {} {}", spot[0], spot[1]);
        }
        Optional<NpcSite> site = NpcWork.plant(server, spot[0], spot[1], NpcLevel.LARGE, true);
        if (site.isEmpty()) {
            LOGGER.error(PREFIX + "the kingdom of the world could not be planted");
            return;
        }
        siteId = site.get().id();
        // The player watches from above while it appears, keeping the chunks around loaded like a player passing by.
        player.teleportTo(level, spot[0] + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot[0], spot[1]) + 60, spot[1] - 80.5, 0, 40);
        started = true;
        LOGGER.info(PREFIX + "a kingdom of the world appears at {} {} with {} town(s)", spot[0], spot[1], site.get().towns().size());
    }

    /**
     * On the server: once the kingdom stands, the places of the shots.
     */
    private void look(IntegratedServer server) {
        Optional<NpcSite> found = NpcWork.state(server).site(siteId);
        if (found.isEmpty() || ready) {
            return;
        }
        NpcSite site = found.get();
        if (site.status() == NpcSite.Status.BLOCKED) {
            LOGGER.error(PREFIX + "the kingdom of the world was left alone: {}", site.reason());
            failed = true;
            finished = true;
            return;
        }
        if (site.status() != NpcSite.Status.SPAWNED || site.kingdomId().isEmpty()) {
            return;
        }
        UUID kingdom = site.kingdomId().get();
        ServerLevel level = server.overworld();
        BlockPos throne = NpcSpawner.thronePos(site);
        List<Shot> list = new ArrayList<>();
        int y = throne.getY();
        list.add(new Shot("npc-capital", throne.getX() + 0.5, y + 55, throne.getZ() + 75.5, 180.0f, 38.0f));
        list.add(new Shot("npc-capital-square", throne.getX() + 0.5, y + 14, throne.getZ() + 22.5, 180.0f, 30.0f));
        RoadWork.state(server).wallOf(0, kingdom).ifPresent(wall -> {
            int r = wall.radius();
            list.add(new Shot("npc-wall", wall.centerX() + r + 14.5, y + 12, wall.centerZ() + r + 14.5, 135.0f, 18.0f));
        });
        List<WorldPos> gates = CityWalls.gates(server, kingdom, 0);
        if (!gates.isEmpty()) {
            WorldPos gate = gates.get(0);
            double dx = gate.x() - throne.getX();
            double dz = gate.z() - throne.getZ();
            double length = Math.max(1, Math.hypot(dx, dz));
            double outX = gate.x() + dx / length * 16;
            double outZ = gate.z() + dz / length * 16;
            float gateYaw = (float) Math.toDegrees(Math.atan2(-(gate.x() - outX), gate.z() - outZ));
            list.add(new Shot("npc-gate", outX + 0.5, gate.y() + 6, outZ + 0.5, gateYaw, 15.0f));
        }
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.role().equals("market")) {
                list.add(new Shot("npc-market", building.box().minX() + 5.5, building.anchor().y() + 9,
                        building.box().maxZ() + 9.5, 180.0f, 32.0f));
                break;
            }
        }
        for (Road road : RoadWork.state(server).roads()) {
            if (!road.kingdomId().equals(kingdom) || road.cells().size() < 40) {
                continue;
            }
            RouteCell middle = road.cells().get(road.cells().size() / 2);
            RouteCell ahead = road.cells().get(Math.min(road.cells().size() - 1, road.cells().size() / 2 + 20));
            float roadYaw = (float) Math.toDegrees(Math.atan2(-(ahead.x() - middle.x()), ahead.z() - middle.z()));
            list.add(new Shot("npc-road-to-town", middle.x() + 0.5, middle.y() + 10, middle.z() + 0.5, roadYaw, 25.0f));
            RouteCell end = road.cells().get(road.cells().size() - 1);
            list.add(new Shot("npc-town", end.x() + 0.5, end.y() + 30, end.z() + 40.5, 180.0f, 35.0f));
            break;
        }
        atlasX = throne.getX();
        atlasZ = throne.getZ();
        shots = List.copyOf(list);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, throne.getX(), throne.getZ());
        LOGGER.info(PREFIX + "the kingdom of the world stands at {} (ground {}); {} shot(s)", throne.toShortString(), ground, list.size());
        ready = true;
    }
}
