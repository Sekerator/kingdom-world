package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.construction.ClientBlueprint;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The buildings part of the client self-check ({@code -PclientSelfTest=buildings}): in a test world it founds a kingdom on
 * a flattened strip of land, lays out every built-in building template, finishes each at once with the operator's call,
 * looks at every building from the front and saves a screenshot, then takes a blueprint and saves a screenshot of the
 * outline drawn where the next building would stand. Screenshots go to the folder {@code buildings} of the screenshot
 * folder. Meant for a copy of a test world: the buildings stay.
 */
final class ClientBuildingShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Blocks between the anchors of two buildings. */
    private static final int SPACING = 18;
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 60;

    private final Path folder;
    private int ticks;
    private volatile boolean ready;
    private volatile List<String> built = List.of();
    private volatile BlockPos origin;
    private volatile int ground;
    private int index = -1;
    private int wait;
    private boolean ghostStarted;
    private boolean finished;
    private boolean failed;

    ClientBuildingShowcase(Path folder) {
        this.folder = folder.resolve("buildings");
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
        // A window without focus would pause the game, and with it the server of the world.
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
            if (ticks > 1_200) {
                LOGGER.error(PREFIX + "the buildings were not built in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            wait--;
            if (wait == 0) {
                shoot(ghostStarted ? "ghost-" + built.get(0) : built.get(index));
            }
            return;
        }
        index++;
        if (index < built.size()) {
            // The buildings alone: no chat, no hints, no toasts.
            minecraft.options.hideGui = true;
            minecraft.getToasts().clear();
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            // Stand in front of the building, a little above it, looking at its entrance.
            BlockPos anchor = origin.offset(index * SPACING, 0, 0);
            teleport(server, playerId, anchor.getX() + 0.5, ground + 7.0, anchor.getZ() + 15.5, 180.0f, 22.0f);
            wait = SETTLE;
            return;
        }
        if (!ghostStarted) {
            ghostStarted = true;
            // The outline with its hints at the top of the screen.
            minecraft.options.hideGui = false;
            minecraft.gui.getChat().clearMessages(false);
            BlockPos spot = origin.offset(-SPACING, 0, 0);
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    BuildingTemplates.get(built.get(0)).ifPresent(template -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            io.github.sekerator.kingdomworld.common.construction.BlueprintItem.describe(
                                    new net.minecraft.world.item.ItemStack(ModRegistries.BLUEPRINT.get()), template)));
                }
            });
            teleport(server, playerId, spot.getX() + 0.5, ground + 4.0, spot.getZ() + 9.5, 180.0f, 30.0f);
            wait = SETTLE + 40;
            index = built.size() - 1;
            return;
        }
        LOGGER.info(PREFIX + "buildings shown: {}, blueprint in hand: {}, outline anchored: {}", built.size(),
                !ClientBlueprint.heldTemplate().isEmpty(), ClientBlueprint.anchor() != null);
        finished = true;
    }

    /**
     * Moves the player on the server, which the client follows; a move made only by the client would be taken back.
     */
    private static void teleport(IntegratedServer server, UUID playerId, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
                player.setDeltaMovement(0, 0, 0);
            }
        });
    }

    private void shoot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Path file = folder.resolve(name.replace(':', '_') + ".png");
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
     * On the server: a kingdom on a strip of flat land, every built-in template laid out and finished.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        KingdomWorldApi.findKingdom(server, "Building Test").ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        List<BuildingTemplate> templates = new ArrayList<>();
        for (BuildingTemplate template : BuildingTemplates.sorted()) {
            if (template.id().getNamespace().equals(io.github.sekerator.kingdomworld.KingdomWorld.MOD_ID) && template.available()) {
                templates.add(template);
            }
        }
        BlockPos start = player.blockPosition();
        int y = start.getY();
        BlockPos throne = new BlockPos(start.getX() - SPACING, y, start.getZ() - 12);
        // Flat grass with air above, where the throne and the buildings go.
        int minX = throne.getX() - 30;
        int maxX = start.getX() + templates.size() * SPACING + 10;
        for (int x = minX; x <= maxX; x++) {
            for (int z = start.getZ() - 20; z <= start.getZ() + 24; z++) {
                level.setBlock(new BlockPos(x, y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int h = 0; h < 18; h++) {
                    level.setBlock(new BlockPos(x, y + h, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Building Test", 0x7D3C98, playerId);
        if (!(result instanceof CreateKingdomResult.Created created)) {
            LOGGER.error(PREFIX + "the building kingdom was not founded: {}", result);
            return;
        }
        UUID kingdom = created.kingdom().id();
        ChunkPos first = new ChunkPos(throne);
        for (int cx = first.x - 1; cx <= new ChunkPos(new BlockPos(maxX, y, 0)).x + 1; cx++) {
            for (int cz = first.z - 1; cz <= new ChunkPos(new BlockPos(0, y, start.getZ() + 24)).z + 1; cz++) {
                KingdomWorldApi.claimChunk(level, new ChunkPos(cx, cz), kingdom, null);
            }
        }
        origin = start;
        ground = y;
        List<String> names = new ArrayList<>();
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (int i = 0; i < templates.size(); i++) {
            BuildingTemplate template = templates.get(i);
            KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom, template, start.offset(i * SPACING, 0, 0),
                    Placement.NONE);
            if (change.construction().isEmpty()) {
                LOGGER.error(PREFIX + "{} was not laid out: {}", template.id(), change.outcome());
                failed = true;
                continue;
            }
            Construction construction = change.construction().get();
            ConstructionWork.finishNow(server, construction);
            names.add(template.id().toString());
            LOGGER.info(PREFIX + "built {} at {}", template.id(), start.offset(i * SPACING, 0, 0).toShortString());
        }
        LOGGER.info(PREFIX + "{} building(s) in the register", state.construction().buildingsOf(kingdom).size());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        built = List.copyOf(names);
        ready = !names.isEmpty();
    }
}
