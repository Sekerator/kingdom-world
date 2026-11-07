package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.screen.FoundKingdomScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkPostScreen;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
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
 *   <li>{@code buildings}: in a copy of a test world, builds every built-in building template and saves screenshots of
 *       them and of the outline of a blueprint (see {@link ClientBuildingShowcase}).</li>
 *   <li>{@code world}: in a singleplayer world, founds a kingdom next to the player with three citizens and a work
 *       post, opens the map, the throne screen (through the real server packet) with all tabs including the citizens
 *       and the storehouses, the territory editor with a claim request, the screen of the work post (through the real
 *       server packet) and the founding screen, dissolves the kingdom again and quits. Every step is logged; rendering
 *       problems show up as errors in the log.</li>
 *   <li>{@code maps}: in a singleplayer world with map mods ({@code -PwithMaps}), shows kingdoms and a caravan on them
 *       and saves screenshots in English and Russian (see {@link ClientMapsSelfTest}).</li>
 * </ul>
 */
public final class ClientSelfTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getProperty("kingdomworld.clientSelfTest", "");
    private static final String PREFIX = "KingdomWorld client self-test: ";

    private static int ticks;
    private static boolean quit;
    private static ClientLayoutCheck layout;
    private static ClientBuildingShowcase showcase;
    private static volatile BlockPos throne;
    private static volatile UUID kingdomId;
    private static volatile BlockPos post;
    private static volatile BlockPos bank;
    private static ClientMapsSelfTest maps;

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
        if ("maps".equals(MODE)) {
            if (maps == null) {
                String folder = System.getProperty("kingdomworld.clientSelfTest.screenshots");
                maps = new ClientMapsSelfTest(folder != null && !folder.isEmpty() ? Path.of(folder)
                        : minecraft.gameDirectory.toPath().resolve("kingdomworld-maps"));
            }
            maps.tick();
            quit = maps.finished();
            return;
        }
        if ("buildings".equals(MODE) && minecraft.player != null && minecraft.getSingleplayerServer() != null) {
            if (showcase == null) {
                String folder = System.getProperty("kingdomworld.clientSelfTest.screenshots");
                Path screenshots = folder != null && !folder.isEmpty() ? Path.of(folder)
                        : minecraft.gameDirectory.toPath().resolve("kingdomworld-layout");
                LOGGER.info(PREFIX + "building showcase started, screenshots go to {}", screenshots.resolve("buildings"));
                showcase = new ClientBuildingShowcase(screenshots);
            }
            showcase.tick();
            if (showcase.finished()) {
                if (showcase.failed()) {
                    LOGGER.error(PREFIX + "building showcase failed, exiting with code 1");
                    System.exit(1);
                }
                LOGGER.info(PREFIX + "finished");
                quit = true;
            }
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
        if (ticks > 150 && ticks < 360 && throne == null) {
            LOGGER.error(PREFIX + "no throne was placed, stopping");
            quit = true;
            return;
        }
        if (ticks == 95 || ticks == 155 || ticks == 225 || ticks == 255 || ticks == 315 || ticks == 355) {
            // The last rendered frame shows the screen under test.
            Screenshot.grab(minecraft.gameDirectory, "kingdomworld-selftest-" + ticks + ".png",
                    minecraft.getMainRenderTarget(), message -> LOGGER.info(PREFIX + "screenshot {}", message.getString()));
        }
        switch (ticks) {
            case 60 -> server.execute(() -> found(server, playerId));
            case 80 -> {
                // Face the throne, where the newcomers stand.
                minecraft.player.setYRot(-90.0f);
                minecraft.player.setXRot(10.0f);
                long bodies = minecraft.level.getEntitiesOfClass(CitizenEntity.class, minecraft.player.getBoundingBox().inflate(32)).size();
                LOGGER.info(PREFIX + "citizen bodies near the player: {}", bodies);
            }
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
                }
            }
            case 240 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("citizens");
                    LOGGER.info(PREFIX + "throne tabs shown");
                }
            }
            case 250 -> {
                boolean loaded = minecraft.screen instanceof ThroneScreen screen && screen.citizensShown() >= 0;
                LOGGER.info(PREFIX + "citizens tab answered by the server: {} ({} citizen(s))", loaded,
                        minecraft.screen instanceof ThroneScreen screen ? screen.citizensShown() : -1);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.showCitizens("work");
                    screen.selectTab("storage");
                }
            }
            case 256 -> {
                LOGGER.info(PREFIX + "storehouses tab answered by the server: {} ({} kind(s) of items)",
                        minecraft.screen instanceof ThroneScreen screen && screen.storageShown() >= 0,
                        minecraft.screen instanceof ThroneScreen screen ? screen.storageShown() : -1);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("orders");
                }
            }
            case 259 -> {
                LOGGER.info(PREFIX + "orders tab answered by the server: {} ({} order(s))",
                        minecraft.screen instanceof ThroneScreen screen && screen.ordersShown() >= 0,
                        minecraft.screen instanceof ThroneScreen screen ? screen.ordersShown() : -1);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("treasury");
                }
            }
            case 262 -> {
                LOGGER.info(PREFIX + "treasury tab answered by the server: {}",
                        minecraft.screen instanceof ThroneScreen screen && screen.treasuryShown());
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.showTreasury("journal", "week");
                    screen.showTreasury("settings", "today");
                    screen.selectTab("market");
                }
            }
            case 265 -> {
                LOGGER.info(PREFIX + "market tab answered by the server: {}", minecraft.screen instanceof ThroneScreen screen && screen.marketShown());
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("trade");
                }
            }
            case 268 -> {
                LOGGER.info(PREFIX + "trade tab answered by the server: {}", minecraft.screen instanceof ThroneScreen screen && screen.tradeShown());
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("guild");
                }
            }
            case 270 -> {
                LOGGER.info(PREFIX + "guild tab answered by the server: {}", minecraft.screen instanceof ThroneScreen screen && screen.guildShown());
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("construction");
                }
            }
            case 273 -> {
                LOGGER.info(PREFIX + "construction tab answered by the server: {}",
                        minecraft.screen instanceof ThroneScreen screen && screen.constructionShown());
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("army");
                }
            }
            case 276 -> {
                LOGGER.info(PREFIX + "army tab answered by the server: {}", minecraft.screen instanceof ThroneScreen screen && screen.armyShown());
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
                minecraft.setScreen(null);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && post != null) {
                        WorkPostActions.open(player, post);
                    }
                });
            }
            case 340 -> {
                LOGGER.info(PREFIX + "work post screen opened by the server: {}", minecraft.screen instanceof WorkPostScreen);
                minecraft.setScreen(null);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && bank != null) {
                        io.github.sekerator.kingdomworld.common.economy.BankActions.open(player, bank);
                    }
                });
            }
            case 345 -> {
                LOGGER.info(PREFIX + "bank screen opened by the server: {}, currencies known: {}",
                        minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.BankScreen,
                        io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.get(kingdomId).isPresent());
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && bank != null) {
                        io.github.sekerator.kingdomworld.common.trade.ExchangeActions.open(player, bank);
                    }
                });
            }
            case 350 -> {
                LOGGER.info(PREFIX + "exchange screen opened by the server: {}",
                        minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.ExchangeScreen);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && bank != null && kingdomId != null) {
                        io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom ->
                                KingdomWorldNetwork.sendToPlayer(player, new io.github.sekerator.kingdomworld.common.network.packet.TradePackets
                                        .StallOpen(io.github.sekerator.kingdomworld.common.trade.StallActions.view(player, bank, kingdom), true)));
                    }
                });
            }
            case 355 -> {
                LOGGER.info(PREFIX + "stall screen shown: {}", minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.StallScreen);
                minecraft.setScreen(new FoundKingdomScreen(throne, 0x336699));
            }
            case 362 -> {
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
            WorldPos place = new WorldPos(Dimensions.id(level), throne.getX() + 1, throne.getY(), throne.getZ());
            for (int i = 0; i < 3; i++) {
                CitizenLife.arrive(server, kingdomId, place, false);
            }
            LOGGER.info(PREFIX + "3 citizens arrived");
            post = throne.offset(0, 0, 2);
            level.setBlockAndUpdate(post, ModRegistries.WORK_POST.get().defaultBlockState());
            WorldPos postPos = new WorldPos(Dimensions.id(level), post.getX(), post.getY(), post.getZ());
            KingdomWorldApi.change(server, "client self-test", state -> state.setPostProfession(null, postPos, "lumberjack"));
            LOGGER.info(PREFIX + "work post placed: {}", KingdomWorldApi.state(server).citizens().posts().get(postPos).isPresent());
            bank = throne.offset(0, 0, -2);
            level.setBlockAndUpdate(bank, ModRegistries.BANK.get().defaultBlockState());
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
        if (any != null && post != null) {
            any.serverLevel().setBlockAndUpdate(post, Blocks.AIR.defaultBlockState());
        }
        if (any != null && bank != null) {
            any.serverLevel().setBlockAndUpdate(bank, Blocks.AIR.defaultBlockState());
        }
    }
}
