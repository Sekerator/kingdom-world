package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.WorkPostBlock;
import io.github.sekerator.kingdomworld.common.block.WorkPostLook;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostLooks;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.Wilderness;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The work posts part of the client self-check ({@code -PclientSelfTest=posts}): writes the hashes of built-in templates with
 * a post next to the values they had before the post got its property, then founds a kingdom on flat land with a post of every
 * profession, set the way the window of a post sets it, a post showing a profession of another mod just beyond the land, where
 * no scan puts it right, a post beside a chest and one under a block, and saves screenshots into the folder {@code posts}: near
 * and close by day with smooth lighting and near without it, at night by torches, from 32 and 64 blocks (with a narrow field of view as well),
 * the posts by the chest and under the block, and the post as an item in the hand.
 */
final class ClientPostShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Built-in templates with a post and their hashes before the post had a property (by their files, at 9fd40b4). */
    private static final String[] TEMPLATES = {"kingdomworld:workshop", "kingdomworld:market_square", "kingdomworld:mage_tower"};
    private static final long[] GOLDEN = {1301802044017965731L, 897146897576760734L, 1463876964105840601L};
    /** Ticks a shot waits for the chunks to be drawn again. */
    private static final int SETTLE = 80;
    private static final String[] SHOTS = {"posts-day", "posts-close", "posts-flat-light", "posts-culling", "posts-32", "posts-32-zoom", "posts-64",
            "posts-64-zoom", "posts-night-torch", "post-item-in-hand"};

    private final Path folder;
    private int ticks;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch;
    private int fov;
    private boolean smooth;
    private volatile boolean ready;
    private volatile String failure;
    /** The first block of the chunk just beyond the land: the posts stand in the chunk west of it. */
    private volatile int edge;
    private volatile int ground;
    private volatile int front;

    ClientPostShowcase(Path folder) {
        this.folder = folder.resolve("posts");
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
        if (failure != null) {
            LOGGER.error(PREFIX + "work post showcase failed: {}", failure);
            restore(minecraft);
            failed = true;
            finished = true;
            return;
        }
        if (ticks == 40) {
            fov = minecraft.options.fov().get();
            smooth = minecraft.options.ambientOcclusion().get();
            server.execute(() -> prepare(server, playerId));
            return;
        }
        if (!ready) {
            if (ticks > 1_200) {
                failure = "the posts were not set up in time";
            }
            return;
        }
        if (wait > 0) {
            hold(minecraft);
            wait--;
            if (wait == 0) {
                shoot(minecraft, SHOTS[index]);
            }
            return;
        }
        index++;
        if (index >= SHOTS.length) {
            restore(minecraft);
            server.execute(() -> logLooks(server));
            LOGGER.info(PREFIX + "work post showcase finished: {} screenshot(s)", SHOTS.length);
            finished = true;
            return;
        }
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        double middle = edge - 7.5;
        double near = front + 9.5;
        switch (SHOTS[index]) {
            case "posts-day" -> {
                minecraft.options.hideGui = true;
                time(server, 6_000);
                teleport(server, playerId, middle, ground + 3.2, near, 180.0f, 12.0f);
            }
            case "posts-close" -> teleport(server, playerId, edge - 11.0, ground + 2.6, front + 4.5, 180.0f, 14.0f);
            case "posts-flat-light" -> {
                minecraft.options.ambientOcclusion().set(false);
                teleport(server, playerId, middle, ground + 3.2, near, 180.0f, 12.0f);
            }
            case "posts-culling" -> {
                minecraft.options.ambientOcclusion().set(true);
                teleport(server, playerId, edge - 19.5, ground + 2.8, front + 7.5, 160.0f, 14.0f);
            }
            case "posts-32" -> teleport(server, playerId, middle, ground + 6.0, front + 32.5, 180.0f, 6.0f);
            case "posts-32-zoom", "posts-64-zoom" -> minecraft.options.fov().set(30);
            case "posts-64" -> {
                minecraft.options.fov().set(fov);
                teleport(server, playerId, middle, ground + 9.0, front + 64.5, 180.0f, 6.0f);
            }
            case "posts-night-torch" -> {
                minecraft.options.fov().set(fov);
                time(server, 18_000);
                server.execute(() -> torches(server));
                teleport(server, playerId, middle, ground + 3.2, near, 180.0f, 12.0f);
            }
            case "post-item-in-hand" -> {
                minecraft.options.hideGui = false;
                time(server, 6_000);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistries.WORK_POST_ITEM.get()));
                    }
                });
                teleport(server, playerId, middle, ground + 2.6, front + 6.5, 180.0f, 8.0f);
            }
            default -> {
            }
        }
        wait = SETTLE;
    }

    private void restore(Minecraft minecraft) {
        minecraft.options.hideGui = false;
        if (fov > 0) {
            minecraft.options.fov().set(fov);
            minecraft.options.ambientOcclusion().set(smooth);
        }
    }

    private static void time(IntegratedServer server, long time) {
        server.execute(() -> server.overworld().setDayTime(time));
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

    private void teleport(IntegratedServer server, UUID playerId, double x, double y, double z, float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
                player.setDeltaMovement(0, 0, 0);
            }
        });
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
     * The hashes of built-in templates with a post: the same as before the post had its property, or the buildings of saved
     * worlds would lose their template.
     */
    private static boolean checkHashes() {
        boolean all = true;
        for (int i = 0; i < TEMPLATES.length; i++) {
            long now = BuildingTemplates.get(TEMPLATES[i]).map(BuildingTemplate::fingerprint).orElse(0L);
            boolean same = now == GOLDEN[i];
            all &= same;
            LOGGER.info(PREFIX + "fingerprint of {}: {} (before the property of the post: {}) {}", TEMPLATES[i], now, GOLDEN[i],
                    same ? "match" : "NO MATCH");
        }
        LOGGER.info(PREFIX + "fingerprints of templates with a post: {}", all ? "match" : "NO MATCH");
        return all;
    }

    /**
     * On the server: the hashes, flat land, a kingdom whose land ends in the middle of the row of posts, a post of every
     * profession on it, a post of another mod beyond it, a post by a chest and one under a block; the day stands still.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            checkHashes();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                failure = "no player";
                return;
            }
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            ServerLevel level = player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            level.setDayTime(6_000);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            // The posts stand in one chunk, the row ending in the next one, which stays without an owner.
            int chunkX = (player.blockPosition().getX() >> 4) + 3;
            int chunkZ = player.blockPosition().getZ() >> 4;
            int edgeX = (chunkX + 1) << 4;
            BlockPos start = new BlockPos(edgeX - 36, 0, (chunkZ << 4) - 8);
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX() + 20, start.getZ() + 16) - 1;
            for (int x = 0; x < 56; x++) {
                for (int z = 0; z < 40; z++) {
                    BlockPos at = new BlockPos(start.getX() + x, groundY, start.getZ() + z);
                    level.getChunk(at);
                    level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    for (int y = 1; y < 24; y++) {
                        level.setBlock(at.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            // Flat land in front of the posts as far as the camera of 64 blocks goes.
            for (int x = -4; x < 24; x++) {
                for (int z = 40; z < 96; z++) {
                    BlockPos at = new BlockPos(edgeX - 20 + x, groundY, start.getZ() + z);
                    level.getChunk(at);
                    level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    for (int y = 1; y < 24; y++) {
                        level.setBlock(at.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            int frontZ = (chunkZ << 4) + 8;
            BlockPos throne = new BlockPos(edgeX - 26, groundY + 1, frontZ - 6);
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Post Showcase", 0x8E6E3A, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            UUID kingdom = created.kingdom().id();
            String dimension = Dimensions.id(level);
            KingdomWorldApi.claimChunk(level, new ChunkPos(chunkX, chunkZ), kingdom, null);
            if (KingdomWorldApi.state(server).territory().ownerOf(dimension, ChunkKey.of(chunkX + 1, chunkZ)) != null) {
                KingdomWorldApi.unclaimChunk(level, new ChunkPos(chunkX + 1, chunkZ), kingdom, null);
            }
            boolean inside = kingdom.equals(Wilderness.placeOwner(server, dimension, ChunkKey.of(chunkX, chunkZ)));
            boolean beyond = Wilderness.placeOwner(server, dimension, ChunkKey.of(chunkX + 1, chunkZ)) == null;
            if (!inside || !beyond) {
                failure = "the land does not end in the row of posts (posts on the land: " + inside + ", beyond it nobody's: " + beyond + ")";
                return;
            }
            // Two rows: the front one on the ground, the one behind it a block higher; the last post of each beyond the land.
            List<WorkPostLook> looks = new ArrayList<>();
            for (WorkPostLook look : WorkPostLook.values()) {
                if (look != WorkPostLook.NONE && look != WorkPostLook.OTHER) {
                    looks.add(look);
                }
            }
            BlockState post = ModRegistries.WORK_POST.get().defaultBlockState();
            List<WorldPos> placed = new ArrayList<>();
            for (int i = 0; i < looks.size(); i++) {
                int column = i % 8;
                boolean back = i >= 8;
                BlockPos at = new BlockPos(edgeX - 16 + column * 2, groundY + (back ? 2 : 1), frontZ - (back ? 1 : 0));
                if (back) {
                    level.setBlockAndUpdate(at.below(), Blocks.STONE_BRICKS.defaultBlockState());
                }
                level.setBlockAndUpdate(at, post);
                placed.add(new WorldPos(dimension, at.getX(), at.getY(), at.getZ()));
            }
            BlockPos other = new BlockPos(edgeX, groundY + 1, frontZ);
            BlockPos none = new BlockPos(edgeX, groundY + 2, frontZ - 1);
            level.setBlockAndUpdate(none.below(), Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(none, post);
            level.setBlockAndUpdate(other, post);
            // A post beside a chest and one under a block, on the land west of the rows.
            BlockPos byChest = new BlockPos(edgeX - 22, groundY + 1, frontZ);
            BlockPos underBlock = new BlockPos(edgeX - 19, groundY + 1, frontZ);
            level.setBlockAndUpdate(byChest, post);
            level.setBlockAndUpdate(byChest.east(), Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(underBlock, post);
            level.setBlockAndUpdate(underBlock.above(), Blocks.STONE_BRICKS.defaultBlockState());
            List<String> refused = new ArrayList<>();
            for (int i = 0; i < looks.size(); i++) {
                String profession = looks.get(i).getSerializedName();
                WorldPos at = placed.get(i);
                if (!KingdomWorldApi.change(server, "work post showcase", state -> state.setPostProfession(null, at, profession)).ok()) {
                    refused.add(profession);
                }
            }
            for (BlockPos at : List.of(byChest, underBlock)) {
                WorldPos where = new WorldPos(dimension, at.getX(), at.getY(), at.getZ());
                String profession = at.equals(byChest) ? "carrier" : "miner";
                if (!KingdomWorldApi.change(server, "work post showcase", state -> state.setPostProfession(null, where, profession)).ok()) {
                    refused.add(profession);
                }
            }
            if (!refused.isEmpty()) {
                failure = "the posts were refused the professions " + refused;
                return;
            }
            WorkPostLooks.flush(server);
            // Beyond the land nobody scans: a post showing the profession of another mod stays as it is set here.
            level.setBlock(other, level.getBlockState(other).setValue(WorkPostBlock.PROFESSION, WorkPostLook.OTHER),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            edge = edgeX;
            ground = groundY;
            front = frontZ;
            LOGGER.info(PREFIX + "work post showcase: kingdom at {}, {} posts with a profession and 4 more, the land ends at x {}",
                    throne.toShortString(), looks.size(), edgeX);
            logLooks(server);
            ready = true;
        } catch (RuntimeException e) {
            LOGGER.error(PREFIX + "work post showcase could not be prepared", e);
            failure = e.toString();
        }
    }

    /**
     * Torches on the ground in front of the posts, for the night.
     */
    private void torches(IntegratedServer server) {
        ServerLevel level = server.overworld();
        for (int x : new int[]{edge - 15, edge - 9, edge - 3, edge + 1}) {
            level.setBlockAndUpdate(new BlockPos(x, ground + 1, front + 2), Blocks.TORCH.defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(edge - 21, ground + 1, front + 2), Blocks.TORCH.defaultBlockState());
    }

    /**
     * What every post of the rows shows, in the log.
     */
    private void logLooks(IntegratedServer server) {
        ServerLevel level = server.overworld();
        StringBuilder text = new StringBuilder();
        for (int column = 0; column <= 8; column++) {
            for (int row = 0; row < 2; row++) {
                BlockState state = level.getBlockState(new BlockPos(edge - 16 + column * 2, ground + 1 + row, front - row));
                text.append(state.getBlock() instanceof WorkPostBlock ? state.getValue(WorkPostBlock.PROFESSION).getSerializedName() : "-")
                        .append(' ');
            }
        }
        LOGGER.info(PREFIX + "work posts show: {}", text.toString().trim());
    }
}
