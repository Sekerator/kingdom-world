package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.life.InnkeeperBehavior;
import io.github.sekerator.kingdomworld.common.life.LifeSettings;
import io.github.sekerator.kingdomworld.common.life.TavernActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.Optional;
import java.util.UUID;

/**
 * The life of cities in the client self-check ({@code -PclientSelfTest=life}): in a copy of a test world it builds the tavern
 * of the mod with an innkeeper at the counter and guests, and beds in the open beside it; in the evening it saves screenshots of
 * the guests in the tavern and of the window of the tavern, at night of the citizens asleep in their beds, into the folder
 * {@code world} of the screenshot folder.
 */
final class ClientLifeShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final int SETTLE = 120;
    private static final String[] SHOTS = {"tavern-evening", "tavern-window", "citizens-asleep"};

    private final Path folder;
    private int ticks;
    private volatile boolean ready;
    private volatile BlockPos origin;
    private volatile BlockPos tavernMin;
    private volatile int innkeeperEntity;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch = Float.NaN;

    ClientLifeShowcase(Path folder) {
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
                LOGGER.error(PREFIX + "the tavern was not built in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            hold(minecraft);
            wait--;
            if (wait == 0) {
                shoot(SHOTS[index]);
            }
            return;
        }
        index++;
        if (index >= SHOTS.length) {
            LOGGER.info(PREFIX + "life of cities shown: {} screenshot(s)", SHOTS.length);
            minecraft.setScreen(null);
            server.execute(() -> LifeSettings.override(null));
            finished = true;
            return;
        }
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        BlockPos t = tavernMin;
        switch (index) {
            case 0 -> {
                minecraft.options.hideGui = true;
                teleport(server, playerId, t.getX() + 9.5, t.getY() + 2.6, t.getZ() + 8.5, 135.0f, 18.0f);
            }
            case 1 -> {
                minecraft.options.hideGui = false;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && player.level().getEntity(innkeeperEntity) instanceof CitizenEntity body) {
                        TavernActions.open(player, body);
                    }
                });
            }
            default -> {
                minecraft.setScreen(null);
                minecraft.options.hideGui = true;
                server.execute(() -> server.overworld().setDayTime(Math.floorDiv(server.overworld().getDayTime(), (long) LifeRules.DAY)
                        * LifeRules.DAY + 18_500));
                BlockPos o = origin;
                teleport(server, playerId, o.getX() + 4.5, o.getY() + 5.0, o.getZ() + 9.5, 180.0f, 45.0f);
                wait = SETTLE * 4;
                return;
            }
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

    private static Citizen citizen(IntegratedServer server, UUID kingdom, WorldPos at) {
        return CitizenLife.arrive(server, kingdom, at, false).orElseThrow(() -> new IllegalStateException("No citizen arrived"));
    }

    /**
     * On the server: flat land, a kingdom, the tavern of the mod with an innkeeper and food and drink in a storehouse, guests in
     * it and three beds of citizens beside it; the evening, without monsters and with the time standing still.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        ServerLevel level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        LifeRules.Builder rules = LifeSettings.configured();
        rules.tavernShare = 100;
        rules.spreadTicks = 0;
        LifeSettings.override(rules.build());
        BlockPos start = player.blockPosition().offset(40, 0, 0);
        int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start).getY() - 1;
        BlockPos o = new BlockPos(start.getX(), ground, start.getZ());
        for (int x = -4; x < 44; x++) {
            for (int z = -4; z < 44; z++) {
                level.getChunk(o.offset(x, 0, z));
                level.setBlock(o.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(o.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 1; y < 20; y++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        BlockPos throne = o.offset(30, 1, 30);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Tavern Showcase", 0xB9770E, playerId);
        UUID kingdom = result instanceof CreateKingdomResult.Created created ? created.kingdom().id()
                : KingdomWorldApi.kingdomOf(server, playerId).map(k -> k.id()).orElse(null);
        if (kingdom == null) {
            LOGGER.error(PREFIX + "no kingdom for the tavern showcase: {}", result);
            return;
        }
        level.setBlockAndUpdate(o.offset(34, 1, 26), ModRegistries.STOREHOUSE.get().defaultBlockState());
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.HONEY_BOTTLE, 16), null);
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.BREAD, 64), null);
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.RABBIT_STEW, 1), null);
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.COOKED_PORKCHOP, 32), null);
        Optional<BuildingTemplate> template = BuildingTemplates.get("kingdomworld:tavern");
        if (template.isEmpty()) {
            LOGGER.error(PREFIX + "the tavern template is missing");
            return;
        }
        var change = ConstructionWork.lay(level, null, kingdom, template.get(), o.offset(20, 1, 22), Placement.NONE, Materials.NONE);
        if (change.construction().isEmpty()) {
            LOGGER.error(PREFIX + "the showcase tavern was refused: {}", change.outcome());
            return;
        }
        Building tavern = ConstructionWork.finishNow(server, change.construction().get()).orElse(null);
        if (tavern == null) {
            LOGGER.error(PREFIX + "the showcase tavern was not finished");
            return;
        }
        BlockPos min = new BlockPos(tavern.box().minX(), tavern.box().minY(), tavern.box().minZ());
        // The innkeeper's post stands behind the counter, three in and two back.
        BlockPos postPos = min.offset(3, 1, 2);
        if (!level.getBlockState(postPos).is(ModRegistries.WORK_POST.get())) {
            level.setBlockAndUpdate(postPos, ModRegistries.WORK_POST.get().defaultBlockState());
        }
        WorldPos post = new WorldPos(Dimensions.id(level), postPos.getX(), postPos.getY(), postPos.getZ());
        KingdomWorldApi.change(server, "showcase", state -> state.setPostProfession(null, post, InnkeeperBehavior.INNKEEPER.id()));
        Citizen keeper = citizen(server, kingdom, post);
        KingdomWorldApi.change(server, "showcase", state -> state.setProfession(null, keeper.id(), InnkeeperBehavior.INNKEEPER.id()));
        KingdomWorldApi.state(server).citizens().assignWorkplace(keeper, post);
        CitizenBodies.force(keeper.id(), true);
        CitizenEntity keeperBody = CitizenBodies.spawnAt(level, keeper, postPos.south());
        innkeeperEntity = keeperBody == null ? -1 : keeperBody.getId();
        // Guests in the tavern, and three beds in the open for the night.
        List<Citizen> guests = new ArrayList<>();
        int[][] seats = {{7, 2}, {9, 2}, {7, 5}, {9, 5}, {2, 6}, {4, 6}};
        for (int[] seat : seats) {
            Citizen guest = citizen(server, kingdom, new WorldPos(post.dimension(), min.getX() + seat[0], min.getY() + 1, min.getZ() + seat[1]));
            guests.add(guest);
        }
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        for (int i = 0; i < 3; i++) {
            BlockPos head = o.offset(2 + i * 2, 1, 6);
            level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
            level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
            WorldPos pos = new WorldPos(post.dimension(), head.getX(), head.getY(), head.getZ());
            KingdomWorldApi.state(server).citizens().addSite(pos, SiteKinds.BED, kingdom);
            KingdomWorldApi.state(server).citizens().assignHome(guests.get(i), pos);
        }
        for (int i = 0; i < guests.size(); i++) {
            CitizenBodies.force(guests.get(i).id(), true);
            CitizenBodies.spawnAt(level, guests.get(i), min.offset(seats[i][0], 1, seats[i][1] + 1));
        }
        level.setBlockAndUpdate(o.offset(5, 1, 9), Blocks.LANTERN.defaultBlockState());
        level.setDayTime(Math.floorDiv(level.getDayTime(), (long) LifeRules.DAY) * LifeRules.DAY + 12_200);
        origin = o;
        tavernMin = min;
        LOGGER.info(PREFIX + "showcase tavern #{} at {}, {} guests", tavern.id(), min, guests.size());
        ready = true;
    }
}
