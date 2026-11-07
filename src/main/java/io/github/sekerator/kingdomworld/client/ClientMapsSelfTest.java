package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientMapSync;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.map.ClientTradeCache;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client self-check of the map integrations ({@code -PclientSelfTest=maps}), run in a singleplayer world with map mods
 * added by {@code -PwithMaps}. Founds two kingdoms around the player (one with a hole in its land, one shaped like the
 * letter L), shows a caravan between them, waits until the map mods have drawn the land, then for English and Russian
 * opens the full screen map of every installed map mod with the pointer over the capital of the first kingdom and over
 * the land of the second, and saves screenshots of it and of the minimap. Dissolves the kingdoms again and quits. Steps
 * are logged with the self-test prefix.
 */
final class ClientMapsSelfTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");
    private static final int MAPPING_TICKS = 400;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path screenshots;
    private final List<MapMod> mods = new ArrayList<>();
    private int ticks;
    private int languageIndex;
    private int step;
    private int wait;
    private CompletableFuture<Void> reload;
    private volatile UUID first;
    private volatile UUID second;
    private volatile BlockPos firstThrone;
    private volatile BlockPos secondThrone;
    private int saved;
    private boolean finished;
    private boolean warmedUp;
    private List<Runnable> actions;

    /**
     * A map mod with a full screen map opened by a key.
     */
    private record MapMod(String name, int key, double pixelsPerBlock) {
    }

    ClientMapsSelfTest(Path screenshots) {
        this.screenshots = screenshots;
        if (ModList.get().isLoaded("journeymap")) {
            mods.add(new MapMod("journeymap", GLFW.GLFW_KEY_J, 1.0));
        }
        if (ModList.get().isLoaded("xaeroworldmap")) {
            // Xaero's World Map opens at three screen pixels per block.
            mods.add(new MapMod("xaero", GLFW.GLFW_KEY_M, 3.0));
        } else if (ModList.get().isLoaded("xaerominimap")) {
            mods.add(new MapMod("xaero", -1, 1.0));
        }
        LOGGER.info(PREFIX + "maps check with {}, screenshots go to {}", mods.stream().map(MapMod::name).toList(), screenshots);
    }

    boolean finished() {
        return finished;
    }

    /**
     * Advances the check; called every client tick while a player is in a singleplayer world.
     */
    void tick() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (finished || server == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            // The check must go on while the window is in the background.
            minecraft.options.pauseOnLostFocus = false;
            // The movement hints of the game would cover the minimaps.
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            minecraft.getWindow().setWindowed(1280, 720);
        }
        if (ticks == 40) {
            UUID player = minecraft.player.getUUID();
            server.execute(() -> found(server, player));
        }
        if (ticks == 80) {
            showCaravan();
        }
        if (ticks < MAPPING_TICKS) {
            return;
        }
        if (ticks == MAPPING_TICKS) {
            UUID owner = ClientTerritoryCache.ownerOf(firstThrone.getX() >> 4, firstThrone.getZ() >> 4);
            LOGGER.info(PREFIX + "map sync: {}; capital known: {}", ClientMapSync.stats(), first != null && first.equals(owner));
        }
        if (reload != null) {
            if (!reload.isDone()) {
                return;
            }
            reload = null;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (languageIndex >= LANGUAGES.size()) {
            finish(server);
            return;
        }
        String language = LANGUAGES.get(languageIndex);
        if (!language.equals(minecraft.getLanguageManager().getSelected())) {
            LOGGER.info(PREFIX + "switching the language to {}", language);
            minecraft.getLanguageManager().setSelected(language);
            minecraft.options.languageCode = language;
            reload = minecraft.reloadResourcePacks();
            wait = 40;
            return;
        }
        stepLanguage(language);
    }

    /**
     * Per language: the minimap in the world, then the full screen map of every mod, once with the pointer over the
     * capital of the first kingdom and once over the land of the second. Before the first language every map is opened
     * and closed once, so that news screens of the map mods shown on the first opening are out of the way.
     */
    private void stepLanguage(String language) {
        if (actions == null) {
            actions = actions(language);
        }
        if (step < actions.size()) {
            actions.get(step++).run();
        } else {
            actions = null;
            step = 0;
            languageIndex++;
        }
    }

    private List<Runnable> actions(String language) {
        List<Runnable> actions = new ArrayList<>();
        actions.add(() -> {
            minecraft.setScreen(null);
            wait = 20;
        });
        if (!warmedUp) {
            for (MapMod mod : mods) {
                if (mod.key() >= 0) {
                    actions.add(() -> {
                        press(mod.key());
                        wait = 40;
                    });
                    actions.add(() -> {
                        minecraft.setScreen(null);
                        wait = 20;
                    });
                }
            }
            warmedUp = true;
        }
        if (!mods.isEmpty()) {
            String names = String.join("-", mods.stream().map(MapMod::name).toList());
            actions.add(() -> save(names + "-minimap-" + language));
        }
        for (MapMod mod : mods) {
            if (mod.key() < 0) {
                continue;
            }
            actions.add(() -> {
                press(mod.key());
                wait = 80;
            });
            actions.add(() -> {
                LOGGER.info(PREFIX + "{} map shown: {}", mod.name(), minecraft.screen == null ? "<none>" : minecraft.screen.getClass().getName());
                pointAt(mod, (firstThrone.getX() >> 4) * 16 + 8, (firstThrone.getZ() >> 4) * 16 + 8);
            });
            actions.add(() -> save(mod.name() + "-" + language + "-capital"));
            actions.add(() -> pointAt(mod, ((secondThrone.getX() >> 4) - 1) * 16 + 8, ((secondThrone.getZ() >> 4) + 3) * 16 + 8));
            actions.add(() -> {
                save(mod.name() + "-" + language + "-land");
                minecraft.setScreen(null);
                wait = 20;
            });
        }
        return actions;
    }

    /**
     * Puts the pointer over a block, for a full screen map centered on the player.
     */
    private void pointAt(MapMod mod, int blockX, int blockZ) {
        double x = minecraft.getWindow().getScreenWidth() / 2.0 + (blockX - minecraft.player.getX()) * mod.pixelsPerBlock();
        double y = minecraft.getWindow().getScreenHeight() / 2.0 + (blockZ - minecraft.player.getZ()) * mod.pixelsPerBlock();
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, x, y);
        // A window in the background gets no move event from the system, so the game is told directly.
        try {
            ObfuscationReflectionHelper.findMethod(MouseHandler.class, "m_91561_", long.class, double.class, double.class)
                    .invoke(minecraft.mouseHandler, window, x, y);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn(PREFIX + "could not move the pointer: {}", e.toString());
        }
        wait = 30;
    }

    private void press(int key) {
        long window = minecraft.getWindow().getWindow();
        minecraft.keyboardHandler.keyPress(window, key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_PRESS, 0);
        minecraft.keyboardHandler.keyPress(window, key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_RELEASE, 0);
    }

    private void save(String name) {
        Path file = screenshots.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(screenshots);
            image.writeToFile(file);
            saved++;
            LOGGER.info(PREFIX + "screenshot {}", file);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not write {}: {}", file, e.toString());
        }
    }

    // ---------------------------------------------------------------- the land shown

    private void found(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            LOGGER.error(PREFIX + "player not found on the integrated server");
            return;
        }
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        // Animals would cover the land with their icons on the maps.
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getEntities((Entity) null, player.getBoundingBox().inflate(320), entity -> !(entity instanceof Player)).forEach(Entity::discard);
        ChunkPos here = player.chunkPosition();
        List<UUID> stale = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            if (territory.chunks().stream().anyMatch(chunk -> ChunkKey.distance(chunk, here.toLong()) <= 40)) {
                stale.add(territory.kingdomId());
            }
        }
        KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> stale.add(old.id()));
        stale.forEach(id -> KingdomWorldApi.dissolveKingdom(server, id));

        // The player stands in the first kingdom, two chunks east of its capital: a square of 5 by 5 chunks with a hole.
        firstThrone = player.blockPosition().offset(-32, 0, 2);
        CreateKingdomResult a = KingdomWorldApi.foundKingdom(level, firstThrone, "Valoria", 0x2E6FD8, playerId);
        // The second one, east of it, in the shape of the letter L, with a name in Cyrillic letters.
        secondThrone = firstThrone.offset(16 * 9, 0, 0);
        CreateKingdomResult b = KingdomWorldApi.foundKingdom(level, secondThrone, "Северная Марка", 0xD8462E, null);
        if (!(a instanceof CreateKingdomResult.Created createdA) || !(b instanceof CreateKingdomResult.Created createdB)) {
            LOGGER.error(PREFIX + "founding failed: {}, {}", a, b);
            return;
        }
        first = createdA.kingdom().id();
        second = createdB.kingdom().id();
        ChunkPos capitalA = new ChunkPos(firstThrone);
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                        KingdomWorldApi.claimChunk(level, new ChunkPos(capitalA.x + dx, capitalA.z + dz), first, null);
                    }
                }
            }
        }
        KingdomWorldApi.unclaimChunk(level, new ChunkPos(capitalA.x + 1, capitalA.z + 1), first, null);
        ChunkPos capitalB = new ChunkPos(secondThrone);
        for (int dz = 2; dz <= 4; dz++) {
            KingdomWorldApi.claimChunk(level, new ChunkPos(capitalB.x - 1, capitalB.z + dz), second, null);
        }
        KingdomWorldApi.claimChunk(level, new ChunkPos(capitalB.x, capitalB.z + 4), second, null);
        KingdomWorldApi.claimChunk(level, new ChunkPos(capitalB.x + 1, capitalB.z + 4), second, null);
        LOGGER.info(PREFIX + "kingdoms founded: {} chunk(s) and {} chunk(s) in {}",
                KingdomWorldApi.state(server).territory().territory(first).map(Territory::size).orElse(0),
                KingdomWorldApi.state(server).territory().territory(second).map(Territory::size).orElse(0), Dimensions.id(level));
    }

    /**
     * A caravan half way between the capitals, shown on the client only: the maps draw what the server would send.
     */
    private void showCaravan() {
        if (first == null || second == null || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        List<TradeViews.CaravanInfo> caravans = new ArrayList<>(ClientTradeCache.caravans());
        caravans.add(new TradeViews.CaravanInfo(1L, first, second, 0x2E6FD8, minecraft.level.dimension().location().toString(),
                firstThrone.getX(), firstThrone.getZ(), secondThrone.getX(), secondThrone.getZ(), now - 24_000L, now + 24_000L));
        ClientTradeCache.replace(caravans);
    }

    private void finish(IntegratedServer server) {
        finished = true;
        minecraft.setScreen(null);
        if (!"en_us".equals(minecraft.getLanguageManager().getSelected())) {
            minecraft.getLanguageManager().setSelected("en_us");
            minecraft.options.languageCode = "en_us";
            minecraft.options.save();
        }
        UUID a = first;
        UUID b = second;
        server.execute(() -> {
            if (a != null) {
                KingdomWorldApi.dissolveKingdom(server, a);
            }
            if (b != null) {
                KingdomWorldApi.dissolveKingdom(server, b);
            }
        });
        LOGGER.info(PREFIX + "maps check finished, {} screenshot(s); {}", saved, ClientMapSync.stats());
    }
}
