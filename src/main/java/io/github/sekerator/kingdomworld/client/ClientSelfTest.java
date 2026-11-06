package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.screen.FoundKingdomScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Development check of the client, switched on with the system property {@code kingdomworld.clientSelfTest}
 * ({@code gradlew runClient -PclientSelfTest=title|layout|world}); without it nothing here runs.
 *
 * <ul>
 *   <li>{@code title}: quits as soon as the title screen opens, proving the client starts with the mod.</li>
 *   <li>{@code layout}: checks the layout of every screen in English and Russian at several window sizes and GUI
 *       scales and saves screenshots (see {@link ClientLayoutCheck}); a problem ends the client with exit code 1.</li>
 *   <li>{@code world}: in a singleplayer world, founds a kingdom next to the player, opens the map, the throne
 *       screen (through the real server packet) with all tabs, the territory editor with a claim request and
 *       the founding screen, dissolves the kingdom again and quits. Every step is logged; rendering problems
 *       show up as errors in the log.</li>
 * </ul>
 */
public final class ClientSelfTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getProperty("kingdomworld.clientSelfTest", "");
    private static final String PREFIX = "KingdomWorld client self-test: ";

    private static int ticks;
    private static boolean quit;
    private static ClientLayoutCheck layout;
    private static volatile BlockPos throne;
    private static volatile UUID kingdomId;

    private ClientSelfTest() {
    }

    static void onTitleScreen() {
        if ("title".equals(MODE) && !quit) {
            LOGGER.info(PREFIX + "title screen reached");
            quit = true;
        }
        if ("layout".equals(MODE) && layout == null) {
            String folder = System.getProperty("kingdomworld.clientSelfTest.screenshots");
            Path screenshots = folder != null && !folder.isEmpty() ? Path.of(folder)
                    : Minecraft.getInstance().gameDirectory.toPath().resolve("kingdomworld-layout");
            LOGGER.info(PREFIX + "layout check started, screenshots go to {}", screenshots);
            layout = new ClientLayoutCheck(screenshots);
        }
    }

    static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (layout != null && !quit) {
            layout.tick();
            if (layout.finished()) {
                if (layout.failed()) {
                    // A failed check must fail the run that started it.
                    LOGGER.error(PREFIX + "layout check failed, exiting with code 1");
                    System.exit(1);
                }
                quit = true;
            }
            return;
        }
        if (quit) {
            LOGGER.info(PREFIX + "quitting");
            quit = false;
            minecraft.stop();
            return;
        }
        if (!"world".equals(MODE) || minecraft.player == null || minecraft.level == null) {
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        UUID playerId = minecraft.player.getUUID();
        if (ticks > 60 && ticks < 360 && throne == null) {
            LOGGER.error(PREFIX + "no throne was placed, stopping");
            quit = true;
            return;
        }
        if (ticks == 155 || ticks == 225 || ticks == 255 || ticks == 315 || ticks == 355) {
            // The last rendered frame shows the screen under test.
            Screenshot.grab(minecraft.gameDirectory, "kingdomworld-selftest-" + ticks + ".png",
                    minecraft.getMainRenderTarget(), message -> LOGGER.info(PREFIX + "screenshot {}", message.getString()));
        }
        switch (ticks) {
            case 60 -> server.execute(() -> found(server, playerId));
            case 100 -> {
                ClientHooks.openKingdomMap();
                LOGGER.info(PREFIX + "map opened: {}", minecraft.screen instanceof KingdomMapScreen);
            }
            case 160 -> {
                UUID owner = ClientTerritoryCache.ownerOf(throne.getX() >> 4, throne.getZ() >> 4);
                LOGGER.info(PREFIX + "map data: {} kingdom(s) known, throne chunk owned by the test kingdom: {}",
                        ClientTerritoryCache.kingdoms().size(), kingdomId != null && kingdomId.equals(owner));
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        KingdomActions.useThrone(player, player.serverLevel(), throne);
                    }
                });
            }
            case 200 -> {
                boolean open = minecraft.screen instanceof ThroneScreen;
                LOGGER.info(PREFIX + "throne screen opened by the server: {}", open);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("members");
                }
            }
            case 230 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("rights");
                    LOGGER.info(PREFIX + "throne tabs shown");
                }
            }
            case 260 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    ClientHooks.openTerritoryEditor(screen.view());
                }
                KingdomWorldNetwork.sendToServer(new ClaimChunkPacket((throne.getX() >> 4) + 2, throne.getZ() >> 4, true));
            }
            case 320 -> {
                String status = minecraft.screen instanceof KingdomMapScreen map ? map.statusText() : "<no map>";
                UUID owner = ClientTerritoryCache.ownerOf((throne.getX() >> 4) + 2, throne.getZ() >> 4);
                LOGGER.info(PREFIX + "territory editor answer: '{}', claimed chunk shown as owned: {}", status,
                        kingdomId != null && kingdomId.equals(owner));
                minecraft.setScreen(new FoundKingdomScreen(throne, 0x336699));
            }
            case 360 -> {
                LOGGER.info(PREFIX + "founding screen shown: {}", minecraft.screen instanceof FoundKingdomScreen);
                minecraft.setScreen(null);
                server.execute(() -> cleanUp(server));
            }
            case 380 -> {
                LOGGER.info(PREFIX + "finished");
                quit = true;
            }
            default -> {
            }
        }
    }

    private static void found(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            LOGGER.error(PREFIX + "player not found on the integrated server");
            return;
        }
        KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        KingdomWorldApi.findKingdom(server, "Client Test").ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        ServerLevel level = player.serverLevel();
        throne = player.blockPosition().offset(3, 0, 0);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Client Test", 0x336699, playerId);
        if (result instanceof CreateKingdomResult.Created created) {
            kingdomId = created.kingdom().id();
            LOGGER.info(PREFIX + "kingdom founded at {}", throne.toShortString());
        } else {
            LOGGER.error(PREFIX + "founding failed: {}", result);
        }
    }

    private static void cleanUp(IntegratedServer server) {
        if (kingdomId != null) {
            Optional<Kingdom> removed = KingdomWorldApi.dissolveKingdom(server, kingdomId);
            LOGGER.info(PREFIX + "kingdom dissolved: {}", removed.isPresent());
        }
        ServerPlayer any = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        if (any != null && throne != null) {
            any.serverLevel().setBlockAndUpdate(throne, Blocks.AIR.defaultBlockState());
        }
    }
}
