package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeGates;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The siege part of the client self-check ({@code -PclientSelfTest=siege}): in a copy of a test world it puts up a stone wall
 * around a capital with portcullises on its gates, declares a war of a kingdom of raiders for a city, and sends six soldiers
 * of the raiders with a ram to a gate; a second city of the kingdom is taken and some chunks are occupied. Then it saves
 * screenshots of the closed gate, of the siege at the gate with the line above the hotbar, and of the atlas with the siege,
 * the taken city and the occupied land, into the folder {@code world} of the screenshot folder.
 */
final class ClientSiegeShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 100;
    private static final String[] SHOTS = {"gate-closed", "gate-siege", "atlas-siege"};

    private final Path folder;
    private int ticks;
    private volatile boolean ready;
    private volatile BlockPos origin;
    private volatile BlockPos gate;
    private volatile BlockPos outward;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch = Float.NaN;

    ClientSiegeShowcase(Path folder) {
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
                LOGGER.error(PREFIX + "the siege was not set up in time");
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
            LOGGER.info(PREFIX + "siege shown: {} screenshot(s)", SHOTS.length);
            minecraft.setScreen(null);
            minecraft.options.hideGui = false;
            finished = true;
            return;
        }
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        BlockPos g = gate;
        BlockPos out = outward;
        switch (index) {
            case 0 -> {
                minecraft.options.hideGui = true;
                wait = SETTLE;
                look(server, playerId, g.getX() + 0.5 + out.getX() * 11, g.getY() + 4.0, g.getZ() + 0.5 + out.getZ() * 11, g);
            }
            case 1 -> {
                // Closer, with the line above the hotbar that tells how far the gate is broken.
                minecraft.options.hideGui = false;
                look(server, playerId, g.getX() + 0.5 + out.getX() * 7 + out.getZ() * 3, g.getY() + 2.0, g.getZ() + 0.5 + out.getZ() * 7 + out.getX() * 3, g);
            }
            default -> {
                minecraft.options.hideGui = true;
                yaw = Float.NaN;
                KingdomMapScreen atlas = new KingdomMapScreen(null);
                minecraft.setScreen(atlas);
                BlockPos o = origin;
                atlas.showForCheck((o.getX() + 48) / 16.0, (o.getZ() + 40) / 16.0, 40.0, null, net.minecraft.network.chat.Component.empty(), false);
            }
        }
        wait += SETTLE;
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

    /**
     * Moves the camera to a place, looking at a target.
     */
    private void look(IntegratedServer server, UUID playerId, double x, double y, double z, BlockPos target) {
        double dx = target.getX() + 0.5 - x;
        double dy = target.getY() + 0.5 - (y + 1.6);
        double dz = target.getZ() + 0.5 - z;
        this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        this.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float lookYaw = yaw;
        float lookPitch = pitch;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.teleportTo(player.serverLevel(), x, y, z, lookYaw, lookPitch);
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
     * On the server: flat land, a kingdom with a stone wall around its capital and portcullises on its gates, a second city,
     * raiders at war with it for a city and six of their soldiers with a ram at the first gate.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ServerLevel level = player.serverLevel();
        level.setDayTime(6_000);
        BlockPos start = player.blockPosition().offset(48, 0, 0);
        int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start).getY() - 1;
        BlockPos o = new BlockPos(start.getX(), ground, start.getZ());
        for (int x = -2; x < 112; x++) {
            for (int z = -2; z < 96; z++) {
                level.getChunk(o.offset(x, 0, z));
                level.setBlock(o.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(o.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(o.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 1; y < 16; y++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        BlockPos throne = o.offset(40, 1, 40);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Siege Showcase", 0x2E86C1, playerId);
        UUID kingdom = result instanceof CreateKingdomResult.Created created ? created.kingdom().id()
                : KingdomWorldApi.kingdomOf(server, playerId).map(k -> k.id()).orElse(null);
        if (kingdom == null) {
            LOGGER.error(PREFIX + "no kingdom for the siege showcase: {}", result);
            return;
        }
        level.setBlockAndUpdate(o.offset(42, 1, 36), ModRegistries.STOREHOUSE.get().defaultBlockState());
        level.setBlockAndUpdate(o.offset(36, 1, 42), ModRegistries.STOREHOUSE.get().defaultBlockState());
        for (int i = 0; i < 18; i++) {
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.COBBLESTONE, 64), null);
        }
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.IRON_INGOT, 32), null);
        Wall[] wall = new Wall[1];
        Outcome walled = CityWalls.build(server, null, kingdom, 0, Wall.WallClass.STONE, wall);
        if (!walled.ok()) {
            LOGGER.error(PREFIX + "the showcase wall was refused: {}", walled.error());
            return;
        }
        // A gate on the north side, cut before the wall is put up.
        wall[0].addGate(wall[0].nearestCell(throne.getX(), throne.getZ() - 60));
        RoadWork.state(server).putWall(wall[0]);
        for (Road.Section section : wall[0].sections()) {
            while (!section.done() && CityWalls.payColumn(server, wall[0], section)) {
                // Paid column by column from the storehouses.
            }
        }
        CityWalls.placeNow(server, wall[0]);
        SiegeWork.forceGates(server);
        for (int cell : wall[0].gates()) {
            SiegeWork.forceHang(server, wall[0], cell, true);
        }
        int cell = wall[0].gates().get(0);
        BlockPos middle = SiegeGates.leafPositions(wall[0], cell).get(SiegeGates.MIDDLE);
        RouteCell at = wall[0].cells().get(cell);
        int ox = Integer.signum(at.x() - wall[0].centerX());
        int oz = Integer.signum(at.z() - wall[0].centerZ());
        if (Math.abs(at.x() - wall[0].centerX()) >= Math.abs(at.z() - wall[0].centerZ())) {
            oz = 0;
        } else {
            ox = 0;
        }
        // A second city of the kingdom, east of the capital: it is taken in this war.
        City town = RoadWork.state(server).addCity(kingdom, "Showcase Town", Dimensions.id(level), o.getX() + 92, ground + 1, o.getZ() + 40,
                KingdomWorldApi.gameTime(server));
        // The raiders: a kingdom at war with it for a city, six soldiers at the gate with the order "siege" and a ram.
        CreateKingdomResult raiders = KingdomWorldApi.createKingdom(server, "Iron Raiders", UUID.randomUUID());
        if (!(raiders instanceof CreateKingdomResult.Created made)) {
            LOGGER.error(PREFIX + "no raiders for the siege showcase: {}", raiders);
            return;
        }
        UUID enemy = made.kingdom().id();
        WarWork.markPresent(enemy, true);
        Outcome declared = DiplomacyActions.declareByOperator(server, enemy, kingdom, WarGoal.CITY, false);
        War war = WarWork.state(server).warBetween(enemy, kingdom).orElse(null);
        if (!declared.ok() || war == null) {
            LOGGER.error(PREFIX + "the showcase war was refused: {}", declared.error());
            return;
        }
        WarWork.startNow(server, war);
        List<UUID> soldiers = new ArrayList<>();
        List<WorldPos> places = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            WorldPos place = new WorldPos(Dimensions.id(level), middle.getX() + ox * (5 + i % 2) + oz * (i - 3),
                    middle.getY(), middle.getZ() + oz * (5 + i % 2) + ox * (i - 3));
            Citizen citizen = CitizenLife.arrive(server, enemy, place, false).orElse(null);
            if (citizen != null && ArmyWork.recruit(server, null, enemy, citizen.id(), Professions.WARRIOR.id()).ok()) {
                soldiers.add(citizen.id());
                places.add(place);
            }
        }
        ArmyWork.change(server, "form the showcase squad", state -> state.formSquad(null, enemy, soldiers, ArmySettings.rules(),
                KingdomWorldApi.state(server)).outcome());
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(enemy)) {
            ArmyWork.order(server, null, squad.id(), Order.siege(new WorldPos(Dimensions.id(level), throne.getX(), throne.getY(), throne.getZ())));
        }
        for (UUID soldier : soldiers) {
            CitizenBodies.force(soldier, true);
        }
        CitizenBodies.update(server);
        int bodies = 0;
        for (int i = 0; i < soldiers.size(); i++) {
            io.github.sekerator.kingdomworld.common.citizen.CitizenEntity body = CitizenBodies.body(soldiers.get(i));
            if (body != null) {
                WorldPos place = places.get(i);
                body.moveTo(place.x() + 0.5, place.y(), place.z() + 0.5, 0, 0);
                bodies++;
            }
        }
        LOGGER.info(PREFIX + "siege showcase: {} soldier bodies outside the gate", bodies);
        SiegeState state = SiegeWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        state.startSiege(new Siege(war.id(), enemy, kingdom, 0, Dimensions.id(level), throne.getX(), throne.getY(), throne.getZ(), now, 1, false));
        state.holdAt(Hold.Kind.PLAZA, war.id(), enemy, kingdom, Dimensions.id(level), town.id(), now).take(now,
                io.github.sekerator.kingdomworld.common.siege.SiegeSettings.rules());
        for (int dx = -1; dx <= 1; dx++) {
            long chunk = ChunkKey.of(((o.getX() + 92) >> 4) + dx, ((o.getZ() + 24) >> 4));
            state.holdAt(Hold.Kind.CHUNK, war.id(), enemy, kingdom, Dimensions.id(level), chunk, now).take(now,
                    io.github.sekerator.kingdomworld.common.siege.SiegeSettings.rules());
        }
        state.changed();
        LOGGER.info(PREFIX + "siege showcase: wall #{} with {} gate(s), {} soldier(s) of the raiders at the gate at {}", wall[0].id(),
                wall[0].gates().size(), soldiers.size(), middle.toShortString());
        gate = middle;
        outward = new BlockPos(ox, 0, oz);
        origin = o;
        ready = true;
    }
}
