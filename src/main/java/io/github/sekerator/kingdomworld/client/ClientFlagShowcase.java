package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.client.screen.FlagScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.FlagBlocks;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlockEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.flag.FlagSpotCache;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.FlagSpots;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The flags part of the client self-check ({@code -PclientSelfTest=flag}): in a copy of a test world the player founds a
 * kingdom beside a walled capital with a gate, a workshop, a tower of a camp and a house stand on its land with their flags;
 * the throne screen shows the flag beside the name, the king draws a flag of three layers in the editor and saves it, the flag
 * as a subject sees it is shown; then the flags in the world are seen: one standing, one on a wall by a door, those beside the
 * throne and those at the gate. The screenshots go into the folder {@code kingdom-flags} of the screenshot folder. The
 * fingerprints of built-in templates that had red banners are written to the log beside the values taken from their files
 * before flags existed.
 */
final class ClientFlagShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Fingerprints of built-in templates with red banners, taken from their files before flags existed. */
    private static final Map<String, Long> GOLDEN = new LinkedHashMap<>();

    static {
        GOLDEN.put("kingdomworld:classic_castle_small", 4459064522805080320L);
        GOLDEN.put("kingdomworld:classic_wall_gate", 2354646779713036405L);
        GOLDEN.put("kingdomworld:classic_camp_tower", 8401321818852998775L);
    }

    private enum Stage {
        PREPARE,
        FLAGS_UP,
        THRONE,
        EDITOR,
        SAVE,
        VIEW,
        WORLD,
        DONE
    }

    private final Path folder;
    private int ticks;
    private int stageTicks;
    private Stage stage = Stage.PREPARE;
    private volatile boolean ready;
    private volatile String failure;
    private volatile UUID kingdom;
    private volatile BlockPos throne;
    private final List<Shot> shots = new ArrayList<>();
    private int shot;
    private boolean finished;
    private boolean failed;

    /**
     * A picture of the world: where the eyes are and what they look at.
     */
    private record Shot(String name, BlockPos target, double eyeX, double eyeY, double eyeZ) {
    }

    ClientFlagShowcase(Path folder) {
        this.folder = folder.resolve("kingdom-flags");
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
        stageTicks++;
        if (failure != null) {
            LOGGER.error(PREFIX + "flag showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (stage) {
            case PREPARE -> {
                if (ticks == 40) {
                    server.execute(() -> prepare(server, playerId));
                } else if (ticks > 60 && ready) {
                    next(Stage.FLAGS_UP);
                } else if (ticks > 1_200) {
                    failure = "the kingdom was not prepared";
                }
            }
            case FLAGS_UP -> {
                // The flags of the throne and the gate are looked at every five seconds.
                if (stageTicks >= 240) {
                    server.execute(() -> findShots(server, playerId));
                    next(Stage.THRONE);
                }
            }
            case THRONE -> {
                if (stageTicks == 20) {
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            KingdomActions.useThrone(player, player.serverLevel(), throne);
                        }
                    });
                } else if (stageTicks == 80) {
                    if (!(minecraft.screen instanceof ThroneScreen)) {
                        failure = "the throne screen did not open";
                        return;
                    }
                    shoot(minecraft, "throne-header");
                    next(Stage.EDITOR);
                }
            }
            case EDITOR -> {
                if (stageTicks == 1 && minecraft.screen instanceof ThroneScreen screen) {
                    screen.openFlagForCheck();
                } else if (stageTicks == 10 && minecraft.screen instanceof FlagScreen editor) {
                    editor.pickPatternForCheck("minecraft:triangles_bottom", true);
                    editor.pickColorForCheck(0);
                    editor.pickPatternForCheck("minecraft:half_horizontal_bottom", true);
                    editor.pickColorForCheck(4);
                    editor.pickPatternForCheck("minecraft:diagonal_up_right", true);
                    editor.pickColorForCheck(15);
                    LOGGER.info(PREFIX + "flag showcase: three layers drawn");
                } else if (stageTicks == 20 && minecraft.screen instanceof FlagScreen editor) {
                    hoverLongestLayer(minecraft, editor);
                } else if (stageTicks == 30) {
                    shoot(minecraft, "editor-three-layers");
                    next(Stage.SAVE);
                }
            }
            case SAVE -> {
                if (stageTicks == 1 && minecraft.screen instanceof FlagScreen editor) {
                    editor.saveForCheck();
                } else if (stageTicks > 5 && minecraft.screen instanceof ThroneScreen) {
                    if (stageTicks == 40) {
                        shoot(minecraft, "throne-after-save");
                        server.execute(() -> KingdomWorldApi.kingdom(server, kingdom).ifPresent(saved ->
                                LOGGER.info(PREFIX + "flag showcase: the kingdom flies {}", saved.flag())));
                        next(Stage.VIEW);
                    }
                } else if (stageTicks > 200) {
                    failure = "the flag was not saved";
                }
            }
            case VIEW -> {
                if (stageTicks == 1 && minecraft.screen instanceof ThroneScreen screen) {
                    minecraft.setScreen(new FlagScreen(screen, kingdom, screen.view().name(), screen.view().color(), screen.view().style(), false));
                } else if (stageTicks == 20) {
                    shoot(minecraft, "flag-view");
                    minecraft.setScreen(null);
                    next(Stage.WORLD);
                }
            }
            case WORLD -> {
                if (shot >= shots.size()) {
                    next(Stage.DONE);
                    return;
                }
                Shot current = shots.get(shot);
                if (stageTicks == 1) {
                    server.execute(() -> look(server, playerId, current));
                } else if (stageTicks == 50) {
                    // The interface is hidden a few frames before the picture.
                    minecraft.options.hideGui = true;
                } else if (stageTicks == 60) {
                    shoot(minecraft, current.name());
                    minecraft.options.hideGui = false;
                    shot++;
                    stageTicks = 0;
                }
            }
            case DONE -> {
                LOGGER.info(PREFIX + "flag showcase finished");
                finished = true;
            }
        }
    }

    private void next(Stage value) {
        stage = value;
        stageTicks = 0;
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
     * Puts the mouse over the name of a layer that is cut short, so that its full name shows.
     */
    private static void hoverLongestLayer(Minecraft minecraft, FlagScreen editor) {
        int row = 3;
        int[] at = editor.rowTextForCheck(row);
        double scale = minecraft.getWindow().getGuiScale();
        double x = (at[0] + 30) * scale;
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
            LOGGER.warn(PREFIX + "flag showcase: the mouse could not be moved: {}", e.toString());
        }
    }

    /**
     * Founds the kingdom of the player on flat land beside it, puts up a workshop, a tower of a camp and a house with their
     * flags, and a stone wall with a gate around the capital.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            logFingerprints();
            BlockPos start = player.blockPosition().offset(24, 0, 0);
            int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start).getY() - 1;
            BlockPos o = new BlockPos(start.getX(), ground, start.getZ());
            for (int x = -2; x < 112; x++) {
                for (int z = -2; z < 96; z++) {
                    level.getChunk(o.offset(x, 0, z));
                    level.setBlock(o.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(o.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(o.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = 1; y < 24; y++) {
                        level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            BlockPos thronePos = o.offset(40, 1, 40);
            level.setBlockAndUpdate(thronePos, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, thronePos, "Северный Стяг", 0x1F618D, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            UUID id = created.kingdom().id();
            // A workshop that gets a flag by its door, a tower of a camp with the flags of its template and a house.
            build(level, id, workshop(), thronePos.offset(-16, 0, -4));
            build(level, id, BuildingTemplates.get(KingdomWorld.id("classic_camp_tower")).orElse(null), thronePos.offset(0, 0, -6));
            build(level, id, BuildingTemplates.get(KingdomWorld.id("small_house")).orElse(null), thronePos.offset(16, 0, -4));
            wall(server, level, id, thronePos);
            kingdom = id;
            throne = thronePos;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, thronePos.getX() + 0.5, thronePos.getY(), thronePos.getZ() + 3.5, 180.0f, 10.0f);
            ready = true;
        } catch (RuntimeException e) {
            failure = e.toString();
        }
    }

    private static void logFingerprints() {
        GOLDEN.forEach((id, golden) -> {
            Optional<BuildingTemplate> template = BuildingTemplates.get(id);
            long now = template.map(BuildingTemplate::fingerprint).orElse(0L);
            LOGGER.info(PREFIX + "flag showcase: fingerprint of {} is {}, before flags {}: {}", id, now, golden,
                    now == golden ? "совпало" : "не совпало");
        });
    }

    /**
     * The smallest workshop or shop of the catalog that gets a flag on the wall by its door.
     */
    private static BuildingTemplate workshop() {
        BuildingTemplate best = null;
        for (BuildingTemplate template : BuildingTemplates.all()) {
            if (!KingdomWorld.MOD_ID.equals(template.id().getNamespace()) || template.problem() != null
                    || template.category() != BuildingTemplate.Category.PRODUCTION && template.category() != BuildingTemplate.Category.TRADE) {
                continue;
            }
            boolean door = FlagSpotCache.of(template).added().stream().anyMatch(spot -> spot.kind() == FlagSpots.Kind.WALL);
            if (door && template.sizeX() <= 13 && template.sizeZ() <= 13
                    && (best == null || template.sizeX() * template.sizeZ() < best.sizeX() * best.sizeZ())) {
                best = template;
            }
        }
        return best;
    }

    private void build(ServerLevel level, UUID kingdomId, BuildingTemplate template, BlockPos anchor) {
        if (template == null) {
            LOGGER.warn(PREFIX + "flag showcase: a template is missing");
            return;
        }
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdomId, template, anchor, Placement.NONE);
        Optional<Construction> construction = change.construction();
        if (construction.isEmpty()) {
            LOGGER.warn(PREFIX + "flag showcase: {} was refused: {}", template.id(), change.outcome());
            return;
        }
        Optional<Building> building = ConstructionWork.finishNow(level.getServer(), construction.get());
        LOGGER.info(PREFIX + "flag showcase: {} stands at {}: {}", template.id(), anchor.toShortString(), building.isPresent());
    }

    /**
     * A stone wall around the capital with a gate on its north side, paid from storehouses and put up at once.
     */
    private static void wall(IntegratedServer server, ServerLevel level, UUID kingdomId, BlockPos thronePos) {
        level.setBlockAndUpdate(thronePos.offset(2, 0, 6), ModRegistries.STOREHOUSE.get().defaultBlockState());
        level.setBlockAndUpdate(thronePos.offset(-4, 0, 6), ModRegistries.STOREHOUSE.get().defaultBlockState());
        for (int i = 0; i < 18; i++) {
            KingdomStorage.insert(server, kingdomId, new ItemStack(Items.COBBLESTONE, 64), null);
        }
        Wall[] made = new Wall[1];
        Outcome walled = CityWalls.build(server, null, kingdomId, 0, Wall.WallClass.STONE, made);
        if (!walled.ok()) {
            LOGGER.warn(PREFIX + "flag showcase: the wall was refused: {}", walled.error());
            return;
        }
        made[0].addGate(made[0].nearestCell(thronePos.getX(), thronePos.getZ() - 60));
        RoadWork.state(server).putWall(made[0]);
        for (Road.Section section : made[0].sections()) {
            while (!section.done() && CityWalls.payColumn(server, made[0], section)) {
                // Paid column by column from the storehouses.
            }
        }
        CityWalls.placeNow(server, made[0]);
    }

    /**
     * Finds what the pictures of the world show: a standing flag, a flag on a wall by a door, the flags beside the throne and
     * those at the gate.
     */
    private void findShots(IntegratedServer server, UUID playerId) {
        ServerLevel level = server.overworld();
        BlockPos standing = null;
        BlockPos door = null;
        BlockPos gate = null;
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            Box box = building.box();
            if (box == null) {
                continue;
            }
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)) {
                if (!FlagBlocks.is(level.getBlockState(pos)) || !(level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag)) {
                    continue;
                }
                if (!FlagBlocks.onWall(level.getBlockState(pos)) && standing == null) {
                    standing = pos.immutable();
                } else if (FlagBlocks.onWall(level.getBlockState(pos)) && flag.origin() == KingdomFlagBlockEntity.Origin.ADDED && door == null) {
                    door = pos.immutable();
                }
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            if (!wall.kingdomId().equals(kingdom) || wall.gates().isEmpty()) {
                continue;
            }
            var cell = wall.cells().get(wall.gates().get(0));
            for (BlockPos pos : BlockPos.betweenClosed(cell.x() - 5, cell.y(), cell.z() - 5, cell.x() + 5, cell.y() + 8, cell.z() + 5)) {
                if (FlagBlocks.is(level.getBlockState(pos))) {
                    gate = pos.immutable();
                    break;
                }
            }
        }
        int flags = 0;
        for (BlockPos pos : BlockPos.betweenClosed(throne.getX() - 1, throne.getY(), throne.getZ() - 1, throne.getX() + 1, throne.getY(),
                throne.getZ() + 1)) {
            flags += FlagBlocks.is(level.getBlockState(pos)) ? 1 : 0;
        }
        LOGGER.info(PREFIX + "flag showcase: standing flag {}, flag by a door {}, flag at the gate {}, flags beside the throne {}", standing, door,
                gate, flags);
        Direction front = level.getBlockState(throne).getValue(io.github.sekerator.kingdomworld.common.block.ThroneBlock.FACING);
        if (standing != null) {
            shots.add(new Shot("standing-flag", standing.above(), standing.getX() + 6.5, standing.getY() + 2.0, standing.getZ() + 6.5));
        }
        if (door != null) {
            Direction out = level.getBlockState(door).getValue(io.github.sekerator.kingdomworld.common.block.KingdomWallFlagBlock.FACING);
            shots.add(new Shot("wall-flag-door", door.below(), door.getX() + 0.5 + out.getStepX() * 6 + out.getClockWise().getStepX() * 2,
                    door.getY() + 0.5, door.getZ() + 0.5 + out.getStepZ() * 6 + out.getClockWise().getStepZ() * 2));
        }
        shots.add(new Shot("throne-flags", throne.above(), throne.getX() + 0.5 + front.getStepX() * 5, throne.getY() + 1.5,
                throne.getZ() + 0.5 + front.getStepZ() * 5));
        if (gate != null) {
            shots.add(new Shot("wall-gate-flags", gate, gate.getX() + 0.5, gate.getY() + 2.0, gate.getZ() - 9.5));
        }
    }

    private static void look(IntegratedServer server, UUID playerId, Shot shot) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        double lookX = shot.target().getX() + 0.5 - shot.eyeX();
        double lookY = shot.target().getY() + 0.5 - (shot.eyeY() + player.getEyeHeight());
        double lookZ = shot.target().getZ() + 0.5 - shot.eyeZ();
        float yaw = (float) (Mth.atan2(lookZ, lookX) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(lookY, Math.sqrt(lookX * lookX + lookZ * lookZ)) * (180.0 / Math.PI));
        player.teleportTo(server.overworld(), shot.eyeX(), shot.eyeY(), shot.eyeZ(), yaw, pitch);
    }
}
