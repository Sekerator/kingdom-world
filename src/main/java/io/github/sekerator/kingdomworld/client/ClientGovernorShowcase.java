package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkforceScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.RemoteWorks;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.CityGovernors;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.GovernorNote;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
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
 * The governors of cities in the client self-check ({@code -PclientSelfTest=governors}): founds a kingdom of the player with two
 * towns on flat land, makes a citizen the mayor of the first and another the baron of the second, lets their governors decide,
 * and saves screenshots into the folder {@code city-self-development}: the row of a city with its governor hovered over, the
 * actions of a city, the office of a citizen in the window of the workers, the overview of the roads tab with the money of the
 * governors, and a house the mayor ordered, put up and looked at from near by.
 */
final class ClientGovernorShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final String HOUSE = "kingdomworld:small_house";
    /** Ticks the house of the mayor may take to be laid out and put up. */
    private static final int HOUSE_TICKS = 6_000;

    private final Path folder;
    private int ticks;
    private int stage;
    private int stageTick;
    private volatile String failure;
    private volatile BlockPos throne;
    private volatile UUID kingdomId;
    private volatile long town;
    private volatile long builtId;
    private volatile BlockPos builtAnchor;
    /** The middle of the house of the mayor at its floor and how far it reaches, for the camera. */
    private volatile int[] builtMiddle;
    private volatile boolean built;
    private boolean finished;
    private boolean failed;

    ClientGovernorShowcase(Path folder) {
        this.folder = folder.resolve("city-self-development");
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    /**
     * Goes on to the next stage after the ticks given, counted from the start of this one.
     */
    private boolean after(int wait) {
        if (ticks - stageTick < wait) {
            return false;
        }
        stage++;
        stageTick = ticks;
        return true;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || finished) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        // The hints of the tutorial would stand over the windows.
        minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        minecraft.getToasts().clear();
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
        }
        ticks++;
        if (failure != null) {
            LOGGER.error(PREFIX + "governor showcase failed: {}", failure);
            minecraft.options.hideGui = false;
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (stage) {
            case 0 -> {
                if (after(40)) {
                    server.execute(() -> prepare(server, playerId));
                }
            }
            case 1 -> {
                if (throne != null && after(100)) {
                    openThrone(server, playerId);
                } else if (ticks - stageTick > 1_200) {
                    failure = "the kingdom was not prepared";
                }
            }
            case 2 -> {
                if (after(60)) {
                    if (!(minecraft.screen instanceof ThroneScreen screen)) {
                        failure = "the throne screen did not open";
                        return;
                    }
                    screen.selectTab("roads");
                }
            }
            case 3 -> {
                if (after(40) && minecraft.screen instanceof ThroneScreen screen) {
                    screen.showRoads("cities", 0, 0);
                    screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 4 -> {
                if (after(40)) {
                    hover(minecraft, 1);
                }
            }
            case 5 -> {
                if (after(40)) {
                    shoot(minecraft, "governors-roads-city-row");
                    if (minecraft.screen instanceof ThroneScreen screen) {
                        screen.showRoads("cities", town + 1, 0);
                        // The actions of the governor come last in the list.
                        screen.scrollRoadsForCheck(7);
                        screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                    }
                    hover(minecraft, 40);
                }
            }
            case 6 -> {
                if (after(40)) {
                    shoot(minecraft, "governors-roads-city-actions");
                    KingdomWorldNetwork.sendToServer(new WorkforcePackets.Request(kingdomId, true, new WorkforceView.Query(
                            WorkforceView.Mode.CITIZENS, "c" + town, "", false, 0, 8, null, null)));
                }
            }
            case 7 -> {
                if (after(60)) {
                    if (!(minecraft.screen instanceof WorkforceScreen screen) || !screen.hasCitizensForCheck()) {
                        failure = "the window of the workers did not open";
                        return;
                    }
                    minecraft.setScreen(screen.officeForCheck(screen.firstWithoutOfficeForCheck()));
                }
            }
            case 8 -> {
                if (after(40)) {
                    shoot(minecraft, "governors-workforce-office");
                    minecraft.setScreen(null);
                    openThrone(server, playerId);
                }
            }
            case 9 -> {
                if (after(60) && minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("roads");
                }
            }
            case 10 -> {
                if (after(40) && minecraft.screen instanceof ThroneScreen screen) {
                    screen.showRoads("overview", 0, 0);
                    screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 11 -> {
                if (after(40)) {
                    shoot(minecraft, "governors-roads-overview");
                    minecraft.setScreen(null);
                }
            }
            case 12 -> {
                // The house the mayor ordered: laid out by the order, paid for and put up, then looked at from near by.
                server.execute(() -> watch(server));
                if (built && builtMiddle != null) {
                    int[] at = builtMiddle;
                    minecraft.options.hideGui = true;
                    // From the side of the entrance, far enough for the whole house: the camera looks at it from its south.
                    double distance = at[3] * 1.6 + 8;
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            player.teleportTo(player.serverLevel(), at[0] + 0.5, at[1] + at[3] * 0.5 + 2.0, at[2] + distance, 180.0f, 12.0f);
                        }
                    });
                    stage++;
                    stageTick = ticks;
                } else if (ticks - stageTick > HOUSE_TICKS) {
                    failure = "the house of the mayor was not put up in " + HOUSE_TICKS + " ticks";
                }
            }
            case 13 -> {
                if (after(100)) {
                    shoot(minecraft, "governors-built-house");
                    minecraft.options.hideGui = false;
                    LOGGER.info(PREFIX + "governor showcase finished: 5 screenshot(s)");
                    finished = true;
                }
            }
            default -> {
            }
        }
    }

    private void openThrone(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                KingdomActions.useThrone(player, player.serverLevel(), throne);
            }
        });
    }

    /**
     * Puts the mouse over the name of a row of the list of cities, so that its hover shows; a row out of the list moves it away.
     */
    private static void hover(Minecraft minecraft, int row) {
        if (!(minecraft.screen instanceof ThroneScreen screen)) {
            return;
        }
        int[] at = screen.roadsRowForCheck(row);
        double scale = minecraft.getWindow().getGuiScale();
        double x = (at[0] + 6) * scale;
        double y = Math.min(minecraft.getWindow().getHeight() - 2, (at[1] + 3) * scale);
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x, y);
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(minecraft.mouseHandler, x);
            ypos.setDouble(minecraft.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn(PREFIX + "governor showcase: the mouse could not be moved: {}", e.toString());
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

    // ---------------------------------------------------------------- the server

    /**
     * Every tick on the server: once the order of the mayor is laid out, every block of the house is paid for, so that it goes
     * up at once; the house stands once the construction is gone.
     */
    private void watch(IntegratedServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (builtId == 0) {
            for (Construction construction : state.construction().of(kingdomId)) {
                if (construction.governor() && construction.templateId().equals(HOUSE)) {
                    BuildPlan plan = ConstructionWork.plan(construction).orElse(null);
                    if (plan == null) {
                        return;
                    }
                    for (int step = 0; step < plan.size(); step++) {
                        construction.markPending(step);
                    }
                    construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
                    builtId = construction.id();
                    builtAnchor = ConstructionWork.blockPos(construction.anchor());
                    io.github.sekerator.kingdomworld.core.construction.Box box = construction.box();
                    builtMiddle = new int[]{(box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2,
                            Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) + 1};
                    LOGGER.info(PREFIX + "governor showcase: the house of the mayor laid out at {}", builtAnchor.toShortString());
                    return;
                }
            }
            return;
        }
        if (state.construction().get(builtId).isEmpty()) {
            built = true;
        }
    }

    /**
     * Flat grass around a middle, a radius wide, with air over it, the land given to the kingdom where nobody holds it.
     */
    private static void meadow(ServerLevel level, UUID kingdom, BlockPos middle, int radius, int ground) {
        KingdomWorldState state = KingdomWorldApi.state(level.getServer());
        String dimension = Dimensions.id(level);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos at = new BlockPos(middle.getX() + x, ground, middle.getZ() + z);
                level.getChunk(at);
                level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 1; y < 20; y++) {
                    if (!level.getBlockState(at.above(y)).isAir()) {
                        level.setBlock(at.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (int cx = (middle.getX() - radius) >> 4; cx <= (middle.getX() + radius) >> 4; cx++) {
            for (int cz = (middle.getZ() - radius) >> 4; cz <= (middle.getZ() + radius) >> 4; cz++) {
                long chunk = ChunkKey.of(cx, cz);
                if (state.territory().claimOwner(dimension, chunk) == null && state.territory().ownerOf(dimension, chunk) == null) {
                    state.territory().assign(kingdom, chunk);
                }
            }
        }
    }

    /**
     * A bed on the ground at a place, its two halves in the world and known to the kingdom.
     */
    private static WorldPos bed(ServerLevel level, UUID kingdom, int x, int y, int z) {
        BlockPos head = new BlockPos(x, y, z);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        WorldPos pos = new WorldPos(Dimensions.id(level), head.getX(), head.getY(), head.getZ());
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom);
        return pos;
    }

    /**
     * On the server: a kingdom of the player on flat land with two towns, a mayor of the first and a baron of the second, the
     * money of the governors, and what they decided.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
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
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            level.setDayTime(6_000);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            BlockPos origin = player.blockPosition().offset(32, 0, 0);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) - 1;
            BlockPos seat = new BlockPos(origin.getX(), ground + 1, origin.getZ());
            level.setBlockAndUpdate(seat, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, seat, "Северная Корона", 0x2E6E9E, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            Kingdom kingdom = created.kingdom();
            UUID id = kingdom.id();
            meadow(level, id, origin, 30, ground);
            String dimension = Dimensions.id(level);
            RoadState roads = RoadWork.state(server);
            int step = Math.max(120, io.github.sekerator.kingdomworld.common.road.RoadSettings.rules().minCityDistance() + 8);
            long now = KingdomWorldApi.gameTime(server);
            BlockPos first = new BlockPos(origin.getX() + step, ground + 1, origin.getZ());
            BlockPos second = new BlockPos(origin.getX(), ground + 1, origin.getZ() + step);
            meadow(level, id, first, 36, ground);
            meadow(level, id, second, 24, ground);
            City olkhovka = roads.addCity(id, "Ольховка", dimension, first.getX(), first.getY(), first.getZ(), now);
            City kamenka = roads.addCity(id, "Каменка", dimension, second.getX(), second.getY(), second.getZ(), now);
            CityAreas.invalidate();
            town = olkhovka.id();
            for (int i = 0; i < 4; i++) {
                bed(level, id, first.getX() - 6 + 3 * i, first.getY(), first.getZ() + 8);
            }
            WorldPos hall = new WorldPos(dimension, seat.getX(), seat.getY(), seat.getZ());
            List<Citizen> people = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                CitizenLife.arrive(server, id, hall, false).ifPresent(people::add);
            }
            if (people.size() < 2) {
                failure = "no citizens came";
                return;
            }
            WarWork.state(server).presence().present(id, now);
            if (!CityGovernors.appoint(server, null, id, people.get(0).id(), olkhovka.id()).success()) {
                failure = "the mayor was not appointed";
                return;
            }
            if (!CityGovernors.makeBaron(server, null, id, people.get(1).id(), kamenka.id()).success()) {
                failure = "the baron was not made";
                return;
            }
            NobilityWork.state(server).court(id).setGovernorAllowance(300);
            NobilityWork.state(server).court(id).governorSpend(NobilityWork.day(server), 120);
            CityPeople.charterOf(server, id, olkhovka.id()).spend(NobilityWork.day(server), 120);
            roads.charterOf(id, olkhovka.id(), now).setFolk(36);
            CityGovernors.decideNow(server, id, kamenka.id());
            // The mayor orders a house in the style of the kingdom; the window says so.
            if (BuildingTemplates.get(HOUSE).isPresent() && RemoteWorks.orderForGovernor(server, id, HOUSE, olkhovka.id()).ok()) {
                CityCharter charter = CityPeople.charterOf(server, id, olkhovka.id());
                charter.setNote(new GovernorNote(GovernorNote.Kind.BUILDS, HOUSE, 0).encode());
                charter.setDecidedDay(NobilityWork.day(server));
                roads.charterChanged();
            } else {
                failure = "the house of the mayor was not ordered";
                return;
            }
            kingdomId = id;
            throne = seat;
            player.teleportTo(level, origin.getX() - 4.5, seat.getY() + 1, origin.getZ() + 0.5, -90.0f, 5.0f);
            LOGGER.info(PREFIX + "governor showcase: kingdom at {}, Olkhovka {} with its mayor, Kamenka {} with its baron", seat.toShortString(),
                    olkhovka.id(), kamenka.id());
        } catch (RuntimeException e) {
            LOGGER.error(PREFIX + "governor showcase could not be prepared", e);
            failure = e.toString();
        }
    }
}
