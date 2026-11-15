package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadBuilding;
import io.github.sekerator.kingdomworld.common.road.RoadEarthworks;
import io.github.sekerator.kingdomworld.common.road.RoadPlanSessions;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.RoutePlan;
import io.github.sekerator.kingdomworld.core.road.RouteStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The levelled roads part of the client self-check ({@code -PclientSelfTest=roads}, after {@link ClientRoadShowcase}): in a copy
 * of a test world it lays out land with a hill, a ridge of rock and a river, founds a kingdom, plans a road on the atlas through the
 * real requests of a player (two points, then a point between over the hill, then the same points straight on, the profile and a
 * point inside a ring of chests the road cannot reach), builds the moderate road half way, shows the Roads tab, finishes it and
 * saves screenshots of the atlas, the tab and the cutting, the tunnel and the bridge in the world into the folder
 * {@code levelled} of the screenshot folder.
 */
final class ClientLevelledRoadShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 80;
    /** Ticks a plan may take to be answered. */
    private static final int ANSWER = 2_400;
    /** Ticks a screen is left to draw before its screenshot. */
    private static final int DRAW = 30;

    private final Path folder;
    private volatile BlockPos origin;
    private volatile BlockPos throne;
    private volatile ServerLevel level;
    private volatile UUID kingdomId;
    private volatile Road road;
    private volatile boolean ready;
    private volatile boolean halfBuilt;
    private volatile boolean built;
    /** Where the camera stands and looks for the shots of the world: x, y, z, yaw, pitch. */
    private volatile double[][] views;
    private int stage;
    private int timer;
    private int ticks;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch = Float.NaN;
    private KingdomMapScreen atlas;
    private int view;

    ClientLevelledRoadShowcase(Path folder) {
        this.folder = folder.resolve("levelled");
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    private int[] at(int x, int z) {
        return new int[]{origin.getX() + x, origin.getZ() + z};
    }

    private List<int[]> twoPoints() {
        return List.of(at(8, 60), at(232, 60));
    }

    private List<int[]> overTheHill() {
        return List.of(at(8, 60), at(64, 40), at(232, 60));
    }

    private List<int[]> intoTheChests() {
        return List.of(at(8, 60), at(64, 40), at(232, 60), at(222, 20));
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
        timer++;
        UUID playerId = minecraft.player.getUUID();
        hold(minecraft);
        switch (stage) {
            case 0 -> {
                minecraft.setScreen(null);
                minecraft.options.hideGui = true;
                server.execute(() -> prepare(server, playerId));
                next();
            }
            case 1 -> {
                if (ready) {
                    teleport(server, playerId, origin.getX() + 110.5, origin.getY() + 24.0, origin.getZ() + 80.5, 180.0f, 30.0f);
                    next();
                } else if (timer > 4_800) {
                    fail("the land of the levelled road was not made in time");
                }
            }
            case 2 -> {
                if (timer > 60) {
                    openThrone(server, playerId);
                    next();
                }
            }
            case 3 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    yaw = Float.NaN;
                    atlas = KingdomMapScreen.forRoads(screen, screen.view());
                    minecraft.setScreen(atlas);
                    atlas.showForCheck((origin.getX() + 120) / 16.0, (origin.getZ() + 34) / 16.0, 15.0, null, Component.empty(), false);
                    awayWithTheMouse(minecraft);
                    atlas.planForCheck(twoPoints(), RouteStyle.TERRAIN, false);
                    next();
                } else if (timer > 200) {
                    fail("the throne screen did not open");
                }
            }
            case 4 -> answered("two points, by the land", () -> atlas.planForCheck(overTheHill(), RouteStyle.TERRAIN, false), null);
            case 5 -> answered("a point between over the hill, by the land",
                    () -> atlas.planForCheck(overTheHill(), RouteStyle.MODERATE, false), "atlas-point-over-hill-card");
            case 6 -> answered("a point between over the hill, moderate", () -> atlas.planForCheck(overTheHill(), RouteStyle.DIRECT, false),
                    null);
            case 7 -> answered("a point between over the hill, straight on", () -> atlas.planForCheck(overTheHill(), RouteStyle.DIRECT, true),
                    "atlas-straight-on");
            case 8 -> answered("the profile", () -> atlas.planForCheck(intoTheChests(), RouteStyle.DIRECT, false), "atlas-profile");
            case 9 -> answered("a point inside a ring of chests", () -> {
                minecraft.setScreen(null);
                server.execute(() -> build(server));
            }, "atlas-failed-leg");
            case 10 -> {
                if (halfBuilt) {
                    openThrone(server, playerId);
                    next();
                } else if (timer > ANSWER) {
                    fail("the levelled road was not laid");
                }
            }
            case 11 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("roads");
                    next();
                } else if (timer > 200) {
                    fail("the throne screen did not open for the Roads tab");
                }
            }
            case 12 -> {
                if (timer > 60 && minecraft.screen instanceof ThroneScreen screen) {
                    LOGGER.info(PREFIX + "roads tab answered by the server: {}", screen.roadsShown());
                    screen.showRoads("roads", 0, 0);
                    screen.refreshForCheck();
                    next();
                }
            }
            case 13 -> {
                if (timer >= DRAW && minecraft.screen instanceof ThroneScreen screen) {
                    shoot("roads-tab-levelled-row");
                    screen.showRoads("overview", 0, 0);
                    screen.refreshForCheck();
                    next();
                }
            }
            case 14 -> {
                if (timer >= DRAW) {
                    shoot("roads-tab-miners-button");
                    minecraft.setScreen(null);
                    server.execute(() -> finish(server));
                    next();
                }
            }
            case 15 -> {
                if (built) {
                    view = 0;
                    next();
                } else if (timer > ANSWER) {
                    fail("the levelled road was not finished");
                }
            }
            case 16 -> {
                double[][] shots = views;
                if (view >= shots.length) {
                    LOGGER.info(PREFIX + "levelled road shown");
                    finished = true;
                    return;
                }
                double[] shot = shots[view];
                if (timer == 1) {
                    server.execute(() -> level.setDayTime(6_000));
                    teleport(server, playerId, shot[0], shot[1], shot[2], (float) shot[3], (float) shot[4]);
                } else if (timer == SETTLE) {
                    shoot(new String[]{"world-cutting", "world-tunnel", "world-bridge"}[view]);
                    view++;
                    timer = 0;
                }
            }
            default -> finished = true;
        }
    }

    private void next() {
        stage++;
        timer = 0;
    }

    private void fail(String why) {
        LOGGER.error(PREFIX + why);
        failed = true;
        finished = true;
    }

    /**
     * Waits for the answer to the last points, logs it, takes its screenshot and goes on.
     */
    private void answered(String what, Runnable then, String shot) {
        RoadViews.PlanView plan = atlas.answeredPlanForCheck();
        if (plan == null) {
            if (timer > ANSWER) {
                fail("no answer for " + what);
            }
            return;
        }
        if (timer < DRAW) {
            return;
        }
        LOGGER.info(PREFIX + "plan of {}: {} {}, error '{}', {} blocks ({} straight), caravan {} s, works {}, builders {} s, miners {} s, "
                        + "coins {}, legs {}, guessed {}", what, plan.style(), plan.stage(), plan.error(), plan.length(), plan.straight(),
                plan.caravan(), Arrays.toString(plan.works()), plan.builderSeconds(), plan.minerSeconds(), plan.coins(),
                plan.legs().stream().map(leg -> leg.stage() + (leg.reason().isEmpty() ? "" : ":" + leg.reason())).toList(), plan.guessed());
        if (shot != null) {
            shoot(shot);
        }
        then.run();
        next();
    }

    /**
     * Puts the pointer into the corner of the window, so that no hint of the atlas covers the road in the shots.
     */
    private static void awayWithTheMouse(Minecraft minecraft) {
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, 2.0);
            y.setDouble(minecraft.mouseHandler, minecraft.getWindow().getHeight() - 2.0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.info(PREFIX + "the pointer stays where it is: {}", e.toString());
        }
    }

    private void openThrone(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && throne != null) {
                KingdomActions.useThrone(player, player.serverLevel(), throne);
            }
        });
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

    // ---------------------------------------------------------------- the server

    /** Height of the hill over the ground at a column, by its distance from the middle of the hill. */
    private static int hill(int x, int z) {
        double r = Math.sqrt((x - 64) * (x - 64) + (z - 40) * (z - 40));
        return r < 12 ? 6 : r < 18 ? (int) Math.round(6 - (r - 12)) : 0;
    }

    /** Height of the ridge of rock over the ground: too high and wide to climb cheaply, so that the straight road bores through. */
    private static int ridge(int x) {
        return x < 110 || x >= 176 ? 0 : Math.min(40, Math.min(3 * (x - 109), 3 * (176 - x)));
    }

    private static boolean river(int x) {
        return x >= 194 && x < 210;
    }

    /**
     * On the server: the land, a kingdom with a capital away from the road, storehouses with what a levelled road takes, and a ring
     * of chests.
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
        // The kingdom of the first part of the showcase gives way to one of its own.
        KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        BlockPos start = player.blockPosition().offset(-120, 0, 200);
        int ground = Math.max(64, Math.min(90, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start).getY() - 1));
        BlockPos o = new BlockPos(start.getX(), ground, start.getZ());
        long began = System.nanoTime();
        int columns = 0;
        for (int x = -4; x < 244; x++) {
            boolean band = ridge(x) > 0 || river(x) || x >= 108 && x < 178 || x >= 192 && x < 212;
            for (int z = band ? -44 : -4; z < (band ? 164 : 124); z++) {
                column(level, o, x, z);
                columns++;
            }
        }
        LOGGER.info(PREFIX + "levelled road land: {} columns in {} ms", columns, (System.nanoTime() - began) / 1_000_000);
        BlockPos seat = o.offset(100, 1, 100);
        level.setBlockAndUpdate(seat, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, seat, "Высокий Перевал", 0x8E44AD, playerId);
        UUID kingdom = result instanceof CreateKingdomResult.Created created ? created.kingdom().id()
                : KingdomWorldApi.kingdomOf(server, playerId).map(k -> k.id()).orElse(null);
        if (kingdom == null) {
            LOGGER.error(PREFIX + "no kingdom for the levelled road: {}", result);
            return;
        }
        level.setBlockAndUpdate(o.offset(104, 1, 100), ModRegistries.STOREHOUSE.get().defaultBlockState());
        level.setBlockAndUpdate(o.offset(96, 1, 100), ModRegistries.STOREHOUSE.get().defaultBlockState());
        level.setBlockAndUpdate(o.offset(100, 1, 104), ModRegistries.STOREHOUSE.get().defaultBlockState());
        for (int i = 0; i < 24; i++) {
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.GRAVEL, 64), null);
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.COBBLESTONE, 64), null);
        }
        for (int i = 0; i < 12; i++) {
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.SPRUCE_FENCE, 64), null);
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.SPRUCE_PLANKS, 64), null);
        }
        for (int i = 0; i < 24; i++) {
            KingdomStorage.insert(server, kingdom, new ItemStack(Items.DIRT, 64), null);
        }
        KingdomStorage.insert(server, kingdom, new ItemStack(Items.TORCH, 64), null);
        // A ring of chests no road may cross, round a point a road is asked to reach.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                    level.setBlock(o.offset(222 + dx, 1, 20 + dz), Blocks.CHEST.defaultBlockState(), 2);
                }
            }
        }
        origin = o;
        throne = seat;
        kingdomId = kingdom;
        this.level = level;
        ready = true;
    }

    private static void column(ServerLevel level, BlockPos o, int x, int z) {
        BlockPos base = o.offset(x, 0, z);
        level.getChunk(base);
        int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, base.getX(), base.getZ()), o.getY() + 1);
        int rise = Math.max(ridge(x), z >= 0 && z < 120 ? hill(x, z) : 0);
        boolean water = river(x);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = -10; y <= Math.max(rise, top - o.getY() + 1); y++) {
            BlockState state;
            if (water) {
                state = y < -6 ? stone : y == -6 ? Blocks.GRAVEL.defaultBlockState() : y <= 0 ? Blocks.WATER.defaultBlockState() : air;
            } else if (y < rise - 2 || y < -2) {
                state = stone;
            } else if (y < rise || y < 0) {
                state = ridge(x) > 0 ? stone : dirt;
            } else if (y == rise) {
                state = grass;
            } else {
                state = air;
            }
            level.setBlock(base.offset(0, y, 0), state, 2);
        }
    }

    /**
     * On the server: plans the moderate road through the point over the hill at once, the one that bores the ridge here, lays it and
     * pays for half of it.
     */
    private void build(IntegratedServer server) {
        long began = System.nanoTime();
        RoutePlan.Result result = RoadPlanSessions.planNow(level, kingdomId, overTheHill(), RoadClass.GRAVEL, RouteStyle.MODERATE);
        LOGGER.info(PREFIX + "levelled road planned at once in {} ms: found {}, failure {}", (System.nanoTime() - began) / 1_000_000,
                result.found(), result.failure());
        RoadPlanning.Plan plan = RoadPlanSessions.lastTestPlan;
        if (!result.found() || plan == null) {
            return;
        }
        Road[] made = new Road[1];
        Outcome outcome = RoadPlanning.build(server, null, kingdomId, plan.id(), RoadClass.GRAVEL, Road.Mode.OWN, made);
        if (!outcome.ok() || made[0] == null) {
            LOGGER.error(PREFIX + "the levelled road was refused: {}", outcome.error());
            return;
        }
        road = made[0];
        int half = road.blocks().size() / 2;
        int paid = 0;
        for (Road.Section section : road.sections()) {
            while (paid < half && !section.done() && RoadBuilding.payNow(server, road, section)) {
                paid++;
            }
        }
        RoadBuilding.placeNow(server, road);
        int[] kinds = new int[RouteCell.Kind.values().length];
        for (RouteCell cell : road.cells()) {
            kinds[cell.kind().ordinal()]++;
        }
        LOGGER.info(PREFIX + "levelled road #{}: {} cells {} by kind, {} blocks, {} paid, levelled {}", road.id(), road.cells().size(),
                Arrays.toString(kinds), road.blocks().size(), road.paid(), road.levelledShare());
        halfBuilt = true;
    }

    /**
     * On the server: pays for the rest of the road, lays it and finds where the camera looks at its cutting, tunnel and bridge.
     */
    private void finish(IntegratedServer server) {
        Road laid = road;
        if (laid == null) {
            return;
        }
        for (Road.Section section : laid.sections()) {
            while (!section.done() && RoadBuilding.payNow(server, laid, section)) {
                // Paid column by column from the storehouses.
            }
        }
        int placed = RoadBuilding.placeNow(server, laid);
        LOGGER.info(PREFIX + "levelled road #{} finished: {} placed, {} paid of {}, levelled {}, columns in the way {}", laid.id(), placed,
                laid.paid(), laid.blocks().size(), laid.levelledShare(), RoadEarthworks.blocked(laid.id()));
        List<RouteCell> cells = laid.cells();
        double[] cutting = along(cells, deepest(cells, RouteCell.Kind.CUT), -7, 2.4, 12.0f);
        double[] tunnel = along(cells, first(cells, RouteCell.Kind.TUNNEL), -8, 1.8, 6.0f);
        double[] bridge = beside(cells, middle(cells, RouteCell.Kind.BRIDGE));
        views = new double[][]{cutting, tunnel, bridge};
        built = true;
    }

    private static int first(List<RouteCell> cells, RouteCell.Kind kind) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).kind() == kind) {
                return i;
            }
        }
        return cells.size() / 2;
    }

    private static int deepest(List<RouteCell> cells, RouteCell.Kind kind) {
        int best = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).kind() == kind && (best < 0 || cells.get(i).depth() > cells.get(best).depth())) {
                best = i;
            }
        }
        return best < 0 ? cells.size() / 4 : best;
    }

    private static int middle(List<RouteCell> cells, RouteCell.Kind kind) {
        int from = -1;
        int to = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).kind() == kind) {
                from = from < 0 ? i : from;
                to = i;
            }
        }
        return from < 0 ? cells.size() * 3 / 4 : (from + to) / 2;
    }

    /** A camera on the road some cells before a place, looking along it. */
    private static double[] along(List<RouteCell> cells, int index, int back, double up, float pitch) {
        RouteCell at = cells.get(Math.max(0, Math.min(cells.size() - 1, index + back)));
        RouteCell ahead = cells.get(Math.max(0, Math.min(cells.size() - 1, index + 4)));
        float yaw = (float) Math.toDegrees(Math.atan2(-(ahead.x() - at.x()), ahead.z() - at.z()));
        return new double[]{at.x() + 0.5, at.y() + up, at.z() + 0.5, yaw, pitch};
    }

    /** A camera to the side of a place on the road, looking at it. */
    private static double[] beside(List<RouteCell> cells, int index) {
        RouteCell at = cells.get(index);
        RouteCell before = cells.get(Math.max(0, index - 3));
        RouteCell after = cells.get(Math.min(cells.size() - 1, index + 3));
        double dx = after.x() - before.x();
        double dz = after.z() - before.z();
        double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        double sx = at.x() + 0.5 + dz / length * 16;
        double sz = at.z() + 0.5 - dx / length * 16;
        float yaw = (float) Math.toDegrees(Math.atan2(-(at.x() + 0.5 - sx), at.z() + 0.5 - sz));
        return new double[]{sx, at.y() + 3.0, sz, yaw, 14.0f};
    }
}
