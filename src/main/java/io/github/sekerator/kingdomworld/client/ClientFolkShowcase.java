package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.FolkRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The townsfolk of cities, in the client self-check ({@code -PclientSelfTest=folk}): in a copy of a test world the player founds a
 * kingdom whose capital is full of named citizens and has townsfolk, with a town beside it that lives by its townsfolk alone,
 * opens the roads tab of the throne and hovers the rows of the capital and the town; the screenshots go into the folder
 * {@code city-folk} of the screenshot folder.
 */
final class ClientFolkShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Named citizens of the capital, also the quota of the showcase. */
    private static final int NAMED = 6;

    private final Path folder;
    private int ticks;
    private volatile BlockPos throne;
    private volatile String failure;
    private boolean finished;
    private boolean failed;

    ClientFolkShowcase(Path folder) {
        this.folder = folder.resolve("city-folk");
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
        if (failure != null) {
            LOGGER.error(PREFIX + "folk showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (ticks) {
            case 40 -> server.execute(() -> prepare(server, playerId));
            case 140 -> {
                if (throne == null) {
                    failure = "the kingdom was not prepared";
                    return;
                }
                minecraft.options.hideGui = false;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        KingdomActions.useThrone(player, player.serverLevel(), throne);
                    }
                });
            }
            case 200 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("roads");
                } else {
                    failure = "the throne screen did not open";
                }
            }
            case 240 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    LOGGER.info(PREFIX + "folk showcase: roads tab answered by the server: {}", screen.roadsShown());
                    screen.showRoads("cities", 0, 0);
                    // The buttons follow the view chosen.
                    screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 290 -> hover(minecraft, 0);
            case 330 -> shoot(minecraft, "roads-capital-folk");
            case 340 -> hover(minecraft, 1);
            case 380 -> shoot(minecraft, "roads-town-folk");
            case 390 -> {
                minecraft.setScreen(null);
                FolkSettings.override(null);
                LOGGER.info(PREFIX + "folk showcase finished");
                finished = true;
            }
            default -> {
            }
        }
    }

    /**
     * Puts the mouse over the name of a row of the list of cities, so that its hover shows.
     */
    private static void hover(Minecraft minecraft, int row) {
        if (!(minecraft.screen instanceof ThroneScreen screen)) {
            return;
        }
        int[] at = screen.roadsRowForCheck(row);
        double scale = minecraft.getWindow().getGuiScale();
        double x = (at[0] + 6) * scale;
        double y = (at[1] + 3) * scale;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x, y);
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(minecraft.mouseHandler, x);
            ypos.setDouble(minecraft.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn(PREFIX + "folk showcase: the mouse could not be moved: {}", e.toString());
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

    /**
     * Founds the kingdom of the player beside it: a capital of {@value #NAMED} named citizens in fourteen beds with townsfolk in
     * the beds left, and a town east of it whose ten beds hold only townsfolk.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            player.setGameMode(GameType.CREATIVE);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            // The quota of the showcase: the capital is full of named citizens, so that its numbers stay as they are.
            FolkSettings.override(new FolkRules(NAMED, 4, 8, 1));
            BlockPos at = player.blockPosition();
            BlockPos seat = surface(level, at.getX() + 3, at.getZ());
            level.setBlockAndUpdate(seat, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, seat, "Ольховский край", 0x2E7DA0, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            Kingdom kingdom = created.kingdom();
            // Land east of the capital for the town.
            ChunkPos home = new ChunkPos(seat);
            for (int dx = 2; dx <= 4; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    KingdomWorldApi.claimChunk(level, new ChunkPos(home.x + dx, home.z + dz), kingdom.id(), null);
                }
            }
            String dimension = "minecraft:overworld";
            List<WorldPos> beds = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                beds.add(bed(level, kingdom, seat.getX() - 7 + i, seat.getZ() + 5));
            }
            List<Citizen> citizens = new ArrayList<>();
            for (int i = 0; i < NAMED; i++) {
                CitizenLife.arrive(server, kingdom.id(), new WorldPos(dimension, seat.getX(), seat.getY(), seat.getZ()), false)
                        .ifPresent(citizens::add);
            }
            for (int i = 0; i < citizens.size(); i++) {
                KingdomWorldApi.state(server).citizens().assignHome(citizens.get(i), beds.get(i));
            }
            RoadState roads = RoadWork.state(server);
            CityCharter capital = roads.charterOf(kingdom.id(), 0, KingdomWorldApi.gameTime(server));
            capital.setFolk(28);
            BlockPos hall = surface(level, seat.getX() + 44, seat.getZ());
            City town = roads.addCity(kingdom.id(), "Каменка", dimension, hall.getX(), hall.getY(), hall.getZ(), KingdomWorldApi.gameTime(server));
            for (int i = 0; i < 10; i++) {
                bed(level, kingdom, hall.getX() - 5 + i, hall.getZ() + 5);
            }
            roads.charterOf(kingdom.id(), town.id(), KingdomWorldApi.gameTime(server)).setFolk(36);
            roads.charterChanged();
            CityAreas.invalidate();
            throne = seat;
            player.teleportTo(level, at.getX() + 0.5, seat.getY(), at.getZ() + 0.5, -90.0f, 5.0f);
            LOGGER.info(PREFIX + "folk showcase: kingdom at {}, {} named citizen(s), town at {}", seat.toShortString(), citizens.size(),
                    hall.toShortString());
        } catch (RuntimeException e) {
            failure = e.toString();
        }
    }

    /**
     * A bed on the ground at a place, its two halves in the world and known to the kingdom.
     */
    private static WorldPos bed(ServerLevel level, Kingdom kingdom, int x, int z) {
        BlockPos head = surface(level, x, z);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        WorldPos pos = new WorldPos("minecraft:overworld", head.getX(), head.getY(), head.getZ());
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom.id());
        return pos;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
