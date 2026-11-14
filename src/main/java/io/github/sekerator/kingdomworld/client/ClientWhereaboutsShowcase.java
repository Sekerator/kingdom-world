package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.WorkforceActions;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Where citizens are, in the client self-check ({@code -PclientSelfTest=whereabouts}): in a copy of a test world the player founds a
 * kingdom with a few citizens, one of them behind a stone wall and one gone far from its home, looks for the one behind the wall,
 * and screenshots go into the folder {@code citizen-whereabouts} of the screenshot folder: the body outlined behind the wall with
 * the pointer above the hotbar, the window of the workers with where its citizens are, and the atlas with their dots.
 */
final class ClientWhereaboutsShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";

    private final Path folder;
    private int ticks;
    private volatile UUID kingdomId;
    private volatile UUID hidden;
    private volatile UUID gone;
    private volatile String failure;
    private boolean finished;
    private boolean failed;

    ClientWhereaboutsShowcase(Path folder) {
        this.folder = folder.resolve("citizen-whereabouts");
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
            LOGGER.error(PREFIX + "whereabouts showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (ticks) {
            case 40 -> server.execute(() -> prepare(server, playerId));
            case 140 -> {
                if (hidden == null) {
                    failure = "the citizens were not prepared";
                    return;
                }
                minecraft.options.hideGui = false;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    WorkforceActions.find(player, kingdomId, hidden);
                });
            }
            case 200 -> shoot(minecraft, "found-behind-wall");
            case 220 -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                WorkforceActions.request(player, kingdomId, true, new WorkforceView.Query(WorkforceView.Mode.CITIZENS, "", "", false, 0,
                        WorkforceActions.DEFAULT_PAGE, gone, null, WorkforceView.Where.NEAREST));
            });
            case 290 -> shoot(minecraft, "workforce-whereabouts");
            case 300 -> {
                minecraft.setScreen(null);
                KingdomMapScreen map = new KingdomMapScreen(null);
                minecraft.setScreen(map);
                map.showCitizens(true);
            }
            case 380 -> shoot(minecraft, "atlas-citizens");
            case 390 -> {
                minecraft.setScreen(null);
                LOGGER.info(PREFIX + "whereabouts showcase finished");
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
     * Founds the kingdom of the player beside it with five citizens: one behind a wall of stone in front of the player, one gone far
     * from its home, the others about the throne; the player looks at the wall.
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
            BlockPos at = player.blockPosition();
            BlockPos throne = surface(level, at.getX() + 3, at.getZ());
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Whereabouts Test", 0x2E7DA0, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            Kingdom kingdom = created.kingdom();
            kingdomId = kingdom.id();
            // A wall of stone eight blocks north of the player, a citizen behind it.
            BlockPos wallAt = surface(level, at.getX(), at.getZ() - 8);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlockAndUpdate(new BlockPos(at.getX() + dx, wallAt.getY() + dy, wallAt.getZ()), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            List<Citizen> citizens = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                CitizenLife.arrive(server, kingdom.id(), new WorldPos("minecraft:overworld", throne.getX(), throne.getY(), throne.getZ()), false)
                        .ifPresent(citizens::add);
            }
            if (citizens.size() < 5) {
                failure = "the citizens did not arrive";
                return;
            }
            // Homes by the throne: a bed each, both halves, known to the kingdom as a bed.
            BlockState bedState = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            for (int i = 0; i < citizens.size(); i++) {
                BlockPos head = surface(level, throne.getX() - 4 + i * 2, throne.getZ() + 5);
                level.setBlockAndUpdate(head.south(), bedState.setValue(BedBlock.PART, BedPart.FOOT));
                level.setBlockAndUpdate(head, bedState.setValue(BedBlock.PART, BedPart.HEAD));
                WorldPos bed = new WorldPos("minecraft:overworld", head.getX(), head.getY(), head.getZ());
                KingdomWorldApi.state(server).citizens().addSite(bed, SiteKinds.BED, kingdom.id());
                if (!KingdomWorldApi.state(server).citizens().assignHome(citizens.get(i), bed)) {
                    failure = "a citizen got no home";
                    return;
                }
            }
            Citizen behind = citizens.get(0);
            Citizen far = citizens.get(1);
            hidden = behind.id();
            gone = far.id();
            spawn(level, behind, surface(level, at.getX(), at.getZ() - 11));
            // The others about their homes, apart from the throne so that their dots show on the atlas.
            for (int i = 2; i < citizens.size(); i++) {
                spawn(level, citizens.get(i), surface(level, throne.getX() - 54 + i * 18, throne.getZ() + 16));
            }
            // The one gone far stands some ninety blocks east of its home.
            spawn(level, far, surface(level, throne.getX() + 90, throne.getZ() - 10));
            player.teleportTo(level, at.getX() + 0.5, wallAt.getY() + 0.0, at.getZ() + 0.5, 180.0f, 5.0f);
            LOGGER.info(PREFIX + "whereabouts showcase: kingdom at {}, a citizen behind the wall at {}", throne.toShortString(),
                    wallAt.toShortString());
        } catch (RuntimeException e) {
            failure = e.toString();
        }
    }

    private static void spawn(ServerLevel level, Citizen citizen, BlockPos at) {
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(level, citizen, at);
        if (body == null) {
            // A body that came already is moved there.
            body = CitizenBodies.body(citizen.id());
            if (body != null) {
                body.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            }
        }
        if (body != null) {
            body.setNoAi(true);
        }
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
