package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.EarthworkScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.EarthworkPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The earthworks part of the client self-check ({@code -PclientSelfTest=earthwork}): in a copy of a test world the player founds
 * a kingdom, a hill with a tree and a chest stands on its land, the survey stakes in hand mark it, and screenshots go into the
 * folder {@code earthwork} of the screenshot folder: the window of earthworks with the answer of the server, the outline over
 * the plot, and the Construction tab with the work laid out (the folder {@code terraforming}).
 */
final class ClientEarthworkShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";

    private final Path folder;
    private int ticks;
    private volatile BlockPos throne;
    private volatile BlockPos first;
    private volatile BlockPos second;
    private volatile int ground;
    private volatile long site;
    private volatile String failure;
    private boolean finished;
    private boolean failed;

    ClientEarthworkShowcase(Path folder) {
        this.folder = folder.resolve("terraforming");
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
            LOGGER.error(PREFIX + "earthwork showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (ticks) {
            case 40 -> server.execute(() -> prepare(server, playerId));
            case 140 -> {
                if (first == null) {
                    failure = "the land was not prepared";
                    return;
                }
                minecraft.options.hideGui = false;
                minecraft.setScreen(new EarthworkScreen(new ArchitectRulerItem.Corners(first, second, "minecraft:overworld")));
            }
            case 220 -> shoot(minecraft, "earthwork-window");
            case 230 -> minecraft.setScreen(null);
            case 300 -> {
                minecraft.options.hideGui = true;
                shoot(minecraft, "earthwork-outline");
                minecraft.options.hideGui = false;
                KingdomWorldNetwork.sendToServer(new EarthworkPackets.Survey(first, second, Earthwork.Mode.LEVEL.ordinal(), ground, true, true));
            }
            case 340 -> server.execute(() -> openThrone(server, playerId));
            case 400 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("construction");
                }
            }
            case 460 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.showConstruction("sites", "", 0);
                    screen.init(minecraft, screen.width, screen.height);
                }
            }
            case 480 -> shoot(minecraft, "earthwork-tab");
            case 490 -> {
                if (minecraft.screen instanceof ThroneScreen screen && site != 0) {
                    screen.showConstruction("sites", "", site);
                    screen.init(minecraft, screen.width, screen.height);
                }
            }
            case 510 -> shoot(minecraft, "earthwork-tab-site");
            case 520 -> {
                minecraft.setScreen(null);
                LOGGER.info(PREFIX + "earthwork showcase finished");
                finished = true;
            }
            default -> {
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

    /**
     * Founds the kingdom of the player beside it, raises a hill of earth and stone with a tree and a chest on its land, gives the
     * player the survey stakes over it and turns the player to look at it.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            player.setGameMode(GameType.CREATIVE);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            BlockPos at = player.blockPosition();
            throne = new BlockPos(at.getX() + 3, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + 3, at.getZ()), at.getZ());
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Earthworks Test", 0x8E6E3A, playerId);
            if (!(result instanceof CreateKingdomResult.Created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            // A plot of eleven by eleven east of the throne: a hill of earth over stone in it, a tree and a chest on the hill.
            int x0 = throne.getX() + 3;
            int z0 = throne.getZ() - 5;
            ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) - 1;
            for (int dx = 0; dx <= 10; dx++) {
                for (int dz = 0; dz <= 10; dz++) {
                    int high = Math.max(0, 4 - Math.max(Math.abs(dx - 5), Math.abs(dz - 5)) / 1);
                    for (int y = ground - 2; y <= ground + 6; y++) {
                        BlockPos pos = new BlockPos(x0 + dx, y, z0 + dz);
                        if (y <= ground) {
                            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                        } else if (y <= ground + high) {
                            level.setBlockAndUpdate(pos, y == ground + high ? Blocks.GRASS_BLOCK.defaultBlockState()
                                    : y < ground + high - 1 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                        } else {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            for (int y = 1; y <= 3; y++) {
                level.setBlockAndUpdate(new BlockPos(x0 + 7, ground + 2 + y, z0 + 4), Blocks.OAK_LOG.defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(new BlockPos(x0 + 7 + dx, ground + 6, z0 + 4 + dz), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
            BlockPos chest = new BlockPos(x0 + 4, ground + 4, z0 + 6);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container container) {
                container.setItem(0, new ItemStack(net.minecraft.world.item.Items.APPLE, 3));
            }
            first = new BlockPos(x0, ground, z0);
            second = new BlockPos(x0 + 10, ground, z0 + 10);
            ItemStack stakes = new ItemStack(ModRegistries.SURVEY_STAKES.get());
            ArchitectRulerItem.mark(stakes, first, Dimensions.id(level));
            ArchitectRulerItem.mark(stakes, second, Dimensions.id(level));
            player.setItemInHand(InteractionHand.MAIN_HAND, stakes);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, x0 - 6.5, ground + 9.0, z0 + 16.5, -135.0f, 26.0f);
            LOGGER.info(PREFIX + "earthwork showcase: kingdom at {}, plot {} to {}", throne.toShortString(), first.toShortString(), second.toShortString());
        } catch (RuntimeException e) {
            failure = e.toString();
        }
    }

    /**
     * Opens the window of the throne for the player, as the throne does when used; notes the earthworks laid out.
     */
    private void openThrone(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || throne == null) {
            failure = "no player or throne";
            return;
        }
        for (Construction construction : KingdomWorldApi.state(server).construction().all()) {
            if (construction.kind() == Construction.Kind.EARTHWORK) {
                site = construction.id();
            }
        }
        LOGGER.info(PREFIX + "earthworks laid out: {}", site);
        KingdomActions.useThrone(player, player.serverLevel(), throne);
    }
}
