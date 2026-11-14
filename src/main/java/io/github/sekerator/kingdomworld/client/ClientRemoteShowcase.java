package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.RemoteWorks;
import io.github.sekerator.kingdomworld.common.construction.SiteLoader;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Building from the capital in the client self-check ({@code -PclientSelfTest=remote}): founds a kingdom with two cities on
 * flat land, orders a building and a post of builders from the capital, lays a house and a castle out far from the player with
 * everything paid for, and saves screenshots into the folder {@code remote-building}: a template ordered for a city, the orders
 * and constructions with their cities, the actions of a city in the roads tab, the atlas placing a building and founding a city,
 * and the far house finished without a player, looked at from near by afterwards. How many chunks and ticks the loader of
 * places took for the house and the castle, and the longest tick of the server meanwhile, go to the log.
 */
final class ClientRemoteShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final String[] SHOTS = {"remote-template-city", "remote-sites-commissions", "remote-roads-city-actions", "remote-map-build",
            "remote-map-found", "remote-far-house", "remote-far-castle"};
    /** Ticks a shot of a window waits for the server and the drawing. */
    private static final int SETTLE = 40;
    /** Ticks a shot of the world waits for the chunks to be drawn. */
    private static final int SETTLE_WORLD = 100;
    /** Ticks the far buildings may take to be finished. */
    private static final int FAR_TICKS = 24_000;
    private static final String HOUSE = "kingdomworld:small_house";
    private static final String CASTLE = "kingdomworld:castle";

    private final Path folder;
    private int ticks;
    private int index = -1;
    private int wait;
    private int waited;
    private boolean finished;
    private boolean failed;
    private float yaw = Float.NaN;
    private float pitch;
    private ThroneScreen throne;
    private volatile boolean ready;
    private volatile String failure;
    private volatile KingdomView view;
    private volatile long city;
    private volatile int cityX;
    private volatile int cityZ;
    private volatile int cityRadius;
    private volatile int cityDistance;
    private volatile int[] foundChunk;
    private volatile int[] buildPoint;
    private volatile Component buildMessage = Component.empty();
    private volatile boolean buildOk;
    private volatile Far house;
    private volatile Far castle;
    private volatile double longestTick;

    /**
     * A building laid out far away, everything paid for, and what the loader of places did with it.
     */
    private static final class Far {
        final long id;
        final String template;
        final BlockPos anchor;
        final long laid;
        volatile long done = -1;
        volatile int chunks;
        volatile int heldTicks;

        Far(long id, String template, BlockPos anchor, long laid) {
            this.id = id;
            this.template = template;
            this.anchor = anchor;
            this.laid = laid;
        }
    }

    ClientRemoteShowcase(Path folder) {
        this.folder = folder.resolve("remote-building");
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
            LOGGER.error(PREFIX + "remote building showcase failed: {}", failure);
            minecraft.options.hideGui = false;
            failed = true;
            finished = true;
            return;
        }
        if (ticks == 40) {
            server.execute(() -> prepare(server, playerId));
            return;
        }
        if (!ready) {
            if (ticks > 2_400) {
                failure = "the kingdom was not set up in time";
            }
            return;
        }
        server.execute(() -> watch(server));
        if (wait > 0) {
            hold(minecraft);
            wait--;
            if (wait == 0) {
                shoot(minecraft, SHOTS[index]);
            }
            return;
        }
        if (index >= 0 && !advance(minecraft, server, playerId)) {
            return;
        }
        index++;
        waited = 0;
        if (index >= SHOTS.length) {
            report();
            minecraft.options.hideGui = false;
            minecraft.setScreen(null);
            finished = true;
            return;
        }
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        switch (SHOTS[index]) {
            case "remote-template-city" -> {
                minecraft.options.hideGui = false;
                throne = new ThroneScreen(view);
                minecraft.setScreen(throne);
                throne.selectTab("construction");
            }
            case "remote-sites-commissions" -> {
                throne.showConstruction("sites", "", 0);
                throne.refreshForCheck();
            }
            case "remote-roads-city-actions" -> throne.selectTab("roads");
            case "remote-map-build" -> {
                KingdomMapScreen map = KingdomMapScreen.forBuilding(throne, view, city, HOUSE, Component.translatable("kingdomworld.building.small_house"),
                        9, 11, Materials.KINGDOM, "", cityX, cityZ, cityRadius);
                minecraft.setScreen(map);
                map.showBuildingForCheck(buildPoint[0], buildPoint[1], Facing.SOUTH, buildOk, buildMessage);
            }
            case "remote-map-found" -> {
                KingdomMapScreen map = KingdomMapScreen.forFounding(throne, view, cityDistance);
                minecraft.setScreen(map);
                map.showForCheck(foundChunk[0] + 0.5, foundChunk[1] + 0.5, 6.0, null, Component.translatable("kingdomworld.screen.map.found.chosen",
                        foundChunk[0] * 16 + 8, foundChunk[1] * 16 + 8), false);
                map.showFoundingForCheck(foundChunk[0], foundChunk[1], "Заречье", true);
            }
            case "remote-far-house", "remote-far-castle" -> minecraft.setScreen(null);
            default -> {
            }
        }
        wait = -1;
    }

    /**
     * Whether the shot just set up may be taken: the window got the view of the server, or the far building is finished and the
     * camera stands by it. While it may not, the shot waits.
     */
    private boolean advance(Minecraft minecraft, IntegratedServer server, UUID playerId) {
        if (wait == 0) {
            return true;
        }
        waited++;
        String shot = SHOTS[index];
        switch (shot) {
            case "remote-template-city" -> {
                if (!throne.constructionShown()) {
                    return timeout(shot, 600);
                }
                throne.showConstruction("catalog", HOUSE, 0);
                throne.constructionWhere(city);
                throne.refreshForCheck();
            }
            case "remote-roads-city-actions" -> {
                if (!throne.roadsShown()) {
                    return timeout(shot, 600);
                }
                throne.showRoads("cities", city + 1, 0);
                throne.refreshForCheck();
            }
            case "remote-far-house", "remote-far-castle" -> {
                Far far = shot.equals("remote-far-house") ? house : castle;
                if (far == null || far.done < 0) {
                    if (waited % 600 == 0) {
                        LOGGER.info(PREFIX + "waiting for the far {} ({} ticks, the loader: {})", far == null ? "?" : far.template, waited,
                                SiteLoader.describe().orElse("idle"));
                    }
                    return timeout(shot, FAR_TICKS);
                }
                minecraft.options.hideGui = true;
                BlockPos at = far.anchor;
                boolean big = shot.equals("remote-far-castle");
                teleport(server, playerId, at.getX() + 0.5, at.getY() + (big ? 24.0 : 6.0), at.getZ() + (big ? 60.5 : 16.5), 180.0f, big ? 20.0f : 18.0f);
                wait = SETTLE_WORLD;
                return false;
            }
            default -> {
            }
        }
        wait = SETTLE;
        return false;
    }

    private boolean timeout(String shot, int limit) {
        if (waited > limit) {
            failure = "the shot " + shot + " did not get ready in " + limit + " ticks";
        }
        return false;
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

    // ---------------------------------------------------------------- the server

    /**
     * Every tick on the server: how long ticks take and what the loader of places holds for the far buildings.
     */
    private void watch(IntegratedServer server) {
        longestTick = Math.max(longestTick, server.getAverageTickTime());
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Far far : new Far[]{house, castle}) {
            if (far == null || far.done >= 0) {
                continue;
            }
            SiteLoader.describe().filter(text -> text.contains(" " + far.id + ",")).ifPresent(text -> {
                String[] parts = text.split(", ");
                far.chunks = Math.max(far.chunks, Integer.parseInt(parts[1].split(" ")[0]));
                far.heldTicks = Math.max(far.heldTicks, Integer.parseInt(parts[2].split(" ")[0]));
            });
            if (state.construction().get(far.id).isEmpty()) {
                far.done = server.getTickCount();
            }
        }
    }

    private void report() {
        for (Far far : new Far[]{house, castle}) {
            if (far != null) {
                LOGGER.info(PREFIX + "far {}: finished {} ticks after it was laid out, the loader held {} chunks for {} ticks", far.template,
                        far.done < 0 ? "never" : far.done - far.laid, far.chunks, far.heldTicks);
            }
        }
        LOGGER.info(PREFIX + "remote building showcase finished: {} screenshot(s), the longest average tick {} ms", SHOTS.length,
                String.format(java.util.Locale.ROOT, "%.1f", longestTick));
    }

    /**
     * Flat grass around a middle, a radius wide, with air over it.
     */
    private static void meadow(ServerLevel level, BlockPos middle, int radius, int ground) {
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
    }

    /**
     * Gives the kingdom the chunks around a middle that nobody holds; the land of others stays theirs.
     */
    private static void claim(KingdomWorldState state, String dimension, UUID kingdom, BlockPos middle, int radius) {
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
     * A building laid out far away on a meadow made there, everything paid for, waiting for its place to be loaded.
     */
    private Far far(IntegratedServer server, ServerLevel level, UUID kingdom, String templateId, BlockPos middle, int radius) {
        BuildingTemplate template = BuildingTemplates.get(templateId).orElseThrow(() -> new IllegalStateException("no template " + templateId));
        level.getChunk(middle);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ()) - 1;
        meadow(level, middle, radius, ground);
        claim(KingdomWorldApi.state(server), Dimensions.id(level), kingdom, middle, radius + 2);
        BlockPos anchor = ConstructionWork.anchorAt(template, middle.getX(), ground + 1, middle.getZ(), Placement.NONE);
        Box box = template.box(anchor, Placement.NONE);
        BuildPlan plan = ConstructionWork.plan(template, anchor, Placement.NONE, List.of());
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(null, kingdom, template.id().toString(),
                template.fingerprint(), new WorldPos(Dimensions.id(level), anchor.getX(), anchor.getY(), anchor.getZ()), Placement.NONE, box, List.of(),
                plan.size(), KingdomWorldApi.gameTime(server), ConstructionSettings.rules());
        Construction construction = change.construction().orElseThrow(() -> new IllegalStateException("the far " + templateId + " was refused: "
                + change.outcome()));
        for (int step = 0; step < plan.size(); step++) {
            construction.markPending(step);
        }
        construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
        LOGGER.info(PREFIX + "far {} laid out at {} with {} steps paid for", templateId, anchor.toShortString(), plan.size());
        return new Far(construction.id(), templateId, anchor, server.getTickCount());
    }

    /**
     * On the server: a kingdom on flat land with two cities, a building and a post ordered from the capital, and a house and a
     * castle laid out far away; the day stands still.
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
            level.setWeatherParameters(12_000, 0, false, false);
            level.setDayTime(6_000);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            BlockPos origin = player.blockPosition().offset(32, 0, 0);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) - 1;
            meadow(level, origin, 40, ground);
            BlockPos throne = new BlockPos(origin.getX(), ground + 1, origin.getZ());
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Северная Корона", 0x2E6E9E, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            Kingdom kingdom = created.kingdom();
            UUID id = kingdom.id();
            KingdomWorldState state = KingdomWorldApi.state(server);
            int distance = RoadSettings.rules().minCityDistance();
            cityDistance = distance;
            String dimension = Dimensions.id(level);
            claim(state, dimension, id, origin, 40);
            // Two cities around the capital, as close as the server allows, where the land is nobody's.
            long[] ids = new long[2];
            int i = 0;
            int step = distance + 8;
            for (int[] way : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                if (i == 2) {
                    break;
                }
                BlockPos at = new BlockPos(origin.getX() + way[0] * step, 0, origin.getZ() + way[1] * step);
                level.getChunk(at);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                claim(state, dimension, id, at, 48);
                BlockPos hall = new BlockPos(at.getX(), y, at.getZ());
                String name = i == 0 ? "Новый Верхнеполянский Городок" : "Заречье";
                if (!RoadWork.foundCity(level, null, id, hall, name).ok()) {
                    LOGGER.info(PREFIX + "no city at {}, trying another side", hall.toShortString());
                    continue;
                }
                ids[i++] = RoadWork.state(server).cityAt(dimension, hall.getX(), hall.getY(), hall.getZ()).orElseThrow().id();
            }
            if (i < 2) {
                failure = "the cities were not founded";
                return;
            }
            city = ids[0];
            CityAreas.Area area = CityAreas.of(server, id).stream().filter(found -> found.cityId() == ids[0]).findFirst().orElseThrow();
            cityX = area.x();
            cityZ = area.z();
            cityRadius = area.radius();
            // Orders from the capital: a house for the first city, a post of builders for the second, a house at a point.
            RemoteWorks.orderBuild(server, null, id, HOUSE, new Materials(Materials.KINGDOM, ""), ids[0], Construction.Priority.HIGH);
            RemoteWorks.orderPost(server, null, id, ids[1], Commission.Origin.KING);
            RemoteWorks.orderBuild(server, null, id, "kingdomworld:storehouse", Materials.NONE, 0, Construction.Priority.NORMAL);
            buildPoint = new int[]{area.x() + 10, area.z() - 12};
            Component problem = RemoteWorks.checkPoint(server, null, id, HOUSE, Dimensions.id(level), buildPoint[0], buildPoint[1], 0);
            buildOk = problem == null;
            buildMessage = problem == null ? Component.translatable("kingdomworld.commission.check_ok") : problem;
            foundChunk = new int[]{(origin.getX() >> 4), (origin.getZ() >> 4) + 7};
            // Far from the player: a house and a castle, everything paid for, which the loader of places finishes.
            house = far(server, level, id, HOUSE, origin.offset(900, 0, 0), 10);
            castle = far(server, level, id, CASTLE, origin.offset(900, 0, 300), 30);
            view = KingdomViews.view(server, state.kingdoms().get(id).orElseThrow(), playerId);
            longestTick = server.getAverageTickTime();
            LOGGER.info(PREFIX + "remote building showcase: kingdom at {}, cities {} and {}, {} order(s)", throne.toShortString(), ids[0], ids[1],
                    state.construction().commissionsOf(id).size());
            ready = true;
        } catch (RuntimeException e) {
            LOGGER.error(PREFIX + "remote building showcase could not be prepared", e);
            failure = e.toString();
        }
    }
}
