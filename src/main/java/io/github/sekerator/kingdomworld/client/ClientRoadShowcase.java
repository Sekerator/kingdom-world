package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.common.road.CaravanMerchantEntity;
import io.github.sekerator.kingdomworld.common.road.RoadBuilding;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * The roads part of the client self-check ({@code -PclientSelfTest=roads}): in a copy of a test world it lays a gravel road
 * across a stream, which gets a bridge, from the materials of a storehouse; puts a merchant with pack llamas on the road
 * and a camp of robbers beside it; then saves screenshots of the bridge, the caravan and the camp into the folder
 * {@code world} of the screenshot folder. The road and the camp stay in the world.
 */
final class ClientRoadShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 80;
    private static final String[] SHOTS = {"road-bridge", "caravan-on-road", "bandit-camp"};

    private final Path folder;
    private int ticks;
    private volatile boolean ready;
    private volatile BlockPos origin;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;

    ClientRoadShowcase(Path folder) {
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
            if (ticks > 2_400) {
                LOGGER.error(PREFIX + "the road was not built in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            wait--;
            if (wait == 0) {
                shoot(SHOTS[index]);
            }
            return;
        }
        index++;
        if (index >= SHOTS.length) {
            LOGGER.info(PREFIX + "roads shown: {} screenshot(s)", SHOTS.length);
            finished = true;
            return;
        }
        minecraft.options.hideGui = true;
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        BlockPos o = origin;
        switch (index) {
            case 0 -> teleport(server, playerId, o.getX() + 27.5, o.getY() + 8.0, o.getZ() + 21.5, 180.0f, 38.0f);
            case 1 -> teleport(server, playerId, o.getX() + 46.5, o.getY() + 4.0, o.getZ() + 19.5, 180.0f, 22.0f);
            default -> teleport(server, playerId, o.getX() + 20.5, o.getY() + 8.0, o.getZ() + 62.5, 180.0f, 30.0f);
        }
        wait = SETTLE;
    }

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
     * On the server: flat land with a stream, a kingdom with a storehouse of gravel and planks, a gravel road over the stream
     * paid and placed, a merchant with llamas on it and a camp of robbers beside it.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        // Flying in creative mode: the player does not fall into the stream, robbers walk about but leave it alone; the
        // day lights the scene.
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ServerLevel level = player.serverLevel();
        level.setDayTime(6_000);
        BlockPos start = player.blockPosition().offset(48, 0, 0);
        int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start).getY() - 1;
        BlockPos o = new BlockPos(start.getX(), ground, start.getZ());
        for (int x = -2; x < 70; x++) {
            for (int z = -2; z < 70; z++) {
                level.getChunk(o.offset(x, 0, z));
                boolean stream = x >= 24 && x < 31 && z < 40;
                level.setBlock(o.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(o.offset(x, -1, z), stream ? Blocks.WATER.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(o.offset(x, 0, z), stream ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 1; y < 16; y++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // The capital stands away from the ends of the road, so that they do not stick to it.
        BlockPos throne = o.offset(64, 1, 64);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Road Showcase", 0x2E86C1, playerId);
        UUID kingdom = result instanceof CreateKingdomResult.Created created ? created.kingdom().id()
                : KingdomWorldApi.kingdomOf(server, playerId).map(k -> k.id()).orElse(null);
        if (kingdom == null) {
            LOGGER.error(PREFIX + "no kingdom for the road showcase: {}", result);
            return;
        }
        level.setBlockAndUpdate(o.offset(66, 1, 60), ModRegistries.STOREHOUSE.get().defaultBlockState());
        for (int i = 0; i < 6; i++) {
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.GRAVEL, 64), null);
        }
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.SPRUCE_PLANKS, 64), null);
        Optional<RoadPlanning.Plan> plan = RoadPlanning.searchNow(level, kingdom, o.getX() + 2, o.getZ() + 12, o.getX() + 62, o.getZ() + 12,
                RoadSettings.rules());
        if (plan.isEmpty()) {
            LOGGER.error(PREFIX + "no way for the showcase road");
            return;
        }
        Road[] road = new Road[1];
        RoadPlanning.build(server, null, kingdom, plan.get().id(), RoadClass.GRAVEL, Road.Mode.OWN, road);
        if (road[0] == null) {
            LOGGER.error(PREFIX + "the showcase road was refused");
            return;
        }
        for (Road.Section section : road[0].sections()) {
            while (!section.done() && RoadBuilding.payNow(server, road[0], section)) {
                // Paid block by block from the storehouse.
            }
        }
        int placed = RoadBuilding.placeNow(server, road[0]);
        LOGGER.info(PREFIX + "showcase road #{}: {} blocks, {} paid, {} placed", road[0].id(), road[0].length(), road[0].paid(), placed);
        CaravanMerchantEntity merchant = RoadRegistries.CARAVAN_MERCHANT.get().create(level);
        if (merchant != null) {
            merchant.moveTo(o.getX() + 46.5, ground + 1, o.getZ() + 12.5, 90.0f, 0.0f);
            merchant.setCustomName(net.minecraft.network.chat.Component.literal("Road Showcase"));
            merchant.setNoAi(true);
            level.addFreshEntity(merchant);
            for (int i = 0; i < 2; i++) {
                Llama llama = EntityType.LLAMA.create(level);
                if (llama != null) {
                    llama.moveTo(o.getX() + 43.5 - i * 2.5, ground + 1, o.getZ() + 12.5, 90.0f, 0.0f);
                    llama.setNoAi(true);
                    level.addFreshEntity(llama);
                }
            }
        }
        BanditCamp camp = BanditWork.found(server, Dimensions.id(level), o.getX() + 20, o.getZ() + 50, 4);
        BanditWork.build(level, camp);
        origin = o;
        ready = true;
    }
}
