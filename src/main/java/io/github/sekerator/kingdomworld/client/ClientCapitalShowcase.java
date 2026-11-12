package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.ThroneBlock;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.npc.BuildingLooks;
import io.github.sekerator.kingdomworld.core.npc.NpcStyle;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The buildings of the capitals of kingdoms of the world, shown in a flat test world ({@code -PclientSelfTest=capital}):
 * the castles in the looks of their styles, the buildings of the institutions, the towers, yards and monuments, and streets
 * of houses of every quarter, each house in the look its plot would give it, then a street of the same houses in the style
 * of another kingdom. Every building is shot from the front and the side, every street along its fronts and from above,
 * the castles also from afar and their throne halls from inside. Screenshots go to the folder {@code capital} of the
 * screenshot folder. Meant for a flat copy of a test world: the buildings stay.
 */
final class ClientCapitalShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 70;
    /** Blocks between two buildings of a row and between two rows. */
    private static final int GAP = 9;
    private static final int ROW_GAP = 22;

    /**
     * A building to put up: its template and the materials of its look, and how its shots are named.
     */
    private record Item(String template, Materials materials, String label) {
    }

    /**
     * A row of buildings along a street whose fronts line up.
     */
    private record Row(String name, List<Item> items) {
    }

    /**
     * Where the camera stands for a screenshot.
     */
    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {
    }

    private final Path folder;
    private int ticks;
    private volatile boolean ready;
    private volatile List<Shot> shots = List.of();
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;

    ClientCapitalShowcase(Path folder) {
        this.folder = folder.resolve("capital");
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
        if (ticks == 20) {
            // A larger picture than the window the game opens with.
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), 1600, 900);
            minecraft.options.renderDistance().set(16);
        }
        if (ticks == 40) {
            server.execute(() -> prepare(server, playerId));
            return;
        }
        if (!ready) {
            if (ticks > 2_400) {
                LOGGER.error(PREFIX + "the buildings of the capital were not built in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            wait--;
            if (wait == 0) {
                shoot(shots.get(index).name());
            }
            return;
        }
        index++;
        if (index < shots.size()) {
            minecraft.options.hideGui = true;
            minecraft.getToasts().clear();
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            Shot shot = shots.get(index);
            teleport(server, playerId, shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
            // The first shot of a row waits longer: its chunks may not be drawn yet.
            wait = index == 0 || !shots.get(index - 1).name().regionMatches(0, shot.name(), 0, 3) ? SETTLE * 2 : SETTLE;
            return;
        }
        LOGGER.info(PREFIX + "capital buildings shown: {} screenshots", shots.size());
        finished = true;
    }

    private static void teleport(IntegratedServer server, UUID playerId, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.serverLevel().setDayTime(6_000L);
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
            LOGGER.info(PREFIX + "screenshot {} saved to {}", name, file);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not save the screenshot {}", name, e);
            failed = true;
        }
    }

    // ---------------------------------------------------------------- what is shown

    private static Materials look(NpcStyle style, BuildingLooks.Quarter quarter, long seed) {
        return BuildingLooks.of(style, "minecraft:oak", quarter == BuildingLooks.Quarter.PUBLIC ? "red" : "red", quarter, seed);
    }

    private static Item item(String name, NpcStyle style, BuildingLooks.Quarter quarter, long seed) {
        return new Item(KingdomWorld.MOD_ID + ":" + name, look(style, quarter, seed), name + "-" + style.id());
    }

    /**
     * The templates of a kind of the catalog, in the order of the table.
     */
    private static List<String> names(TemplateCatalog.Kind... kinds) {
        List<String> names = new ArrayList<>();
        for (TemplateCatalog.Entry entry : TemplateCatalog.all()) {
            for (TemplateCatalog.Kind kind : kinds) {
                if (entry.kind() == kind && !entry.upgrade()) {
                    names.add(entry.id().substring(entry.id().indexOf(':') + 1));
                }
            }
        }
        return names;
    }

    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        // The castles, each in the look of a style, and the great castle once more in another.
        NpcStyle[] castleStyles = {NpcStyle.STONE, NpcStyle.BRICK, NpcStyle.DEEPSLATE, NpcStyle.SANDSTONE, NpcStyle.MUD};
        List<Item> castles = new ArrayList<>();
        int turn = 0;
        for (String name : names(TemplateCatalog.Kind.CASTLE)) {
            if (!name.equals("castle")) {
                castles.add(item(name, castleStyles[turn++ % castleStyles.length], BuildingLooks.Quarter.PUBLIC, 0));
            }
        }
        rows.add(new Row("castles", castles));
        List<Item> ward = new ArrayList<>();
        for (String name : names(TemplateCatalog.Kind.BARBICAN, TemplateCatalog.Kind.CASTLE_WARD, TemplateCatalog.Kind.ROYAL_BARRACKS)) {
            ward.add(item(name, NpcStyle.DEEPSLATE, BuildingLooks.Quarter.PUBLIC, 0));
        }
        rows.add(new Row("ward", ward));
        List<Item> institutions = new ArrayList<>();
        for (String name : names(TemplateCatalog.Kind.GUILD, TemplateCatalog.Kind.TREASURY, TemplateCatalog.Kind.COURT,
                TemplateCatalog.Kind.INFIRMARY)) {
            institutions.add(item(name, NpcStyle.STONE, BuildingLooks.Quarter.PUBLIC, 0));
        }
        for (String name : names(TemplateCatalog.Kind.GUILD, TemplateCatalog.Kind.TREASURY, TemplateCatalog.Kind.COURT,
                TemplateCatalog.Kind.INFIRMARY)) {
            institutions.add(item(name, NpcStyle.BRICK, BuildingLooks.Quarter.PUBLIC, 0));
        }
        rows.add(new Row("institutions", institutions));
        List<Item> landmarks = new ArrayList<>();
        for (String name : names(TemplateCatalog.Kind.MAGE_TOWER, TemplateCatalog.Kind.YARD, TemplateCatalog.Kind.DECORATION)) {
            if (!name.equals("mage_tower")) {
                landmarks.add(item(name, NpcStyle.STONE, BuildingLooks.Quarter.PUBLIC, 0));
            }
        }
        rows.add(new Row("landmarks", landmarks));
        // Streets: the houses of every quarter, each in the look of its plot.
        long seed = 1;
        for (NpcStyle style : new NpcStyle[]{NpcStyle.STONE, NpcStyle.BRICK, NpcStyle.SANDSTONE}) {
            List<Item> noble = new ArrayList<>();
            for (String name : names(TemplateCatalog.Kind.NOBLE_HOUSE, TemplateCatalog.Kind.MERCHANT_HOUSE)) {
                boolean rich = TemplateCatalog.of(KingdomWorld.MOD_ID + ":" + name).kind() == TemplateCatalog.Kind.NOBLE_HOUSE;
                noble.add(item(name, style, rich ? BuildingLooks.Quarter.NOBLE : BuildingLooks.Quarter.MERCHANT, seed++ * 7_919));
            }
            rows.add(new Row("street-rich-" + style.id(), noble));
            List<Item> plain = new ArrayList<>();
            for (String name : names(TemplateCatalog.Kind.CRAFT_HOUSE, TemplateCatalog.Kind.POOR_HOUSE)) {
                boolean poor = TemplateCatalog.of(KingdomWorld.MOD_ID + ":" + name).kind() == TemplateCatalog.Kind.POOR_HOUSE;
                plain.add(item(name, style, poor ? BuildingLooks.Quarter.POOR : BuildingLooks.Quarter.CRAFT, seed++ * 7_919));
            }
            // The same houses once more, with other plots: a street is no row of twins.
            for (String name : names(TemplateCatalog.Kind.CRAFT_HOUSE, TemplateCatalog.Kind.POOR_HOUSE)) {
                boolean poor = TemplateCatalog.of(KingdomWorld.MOD_ID + ":" + name).kind() == TemplateCatalog.Kind.POOR_HOUSE;
                plain.add(new Item(KingdomWorld.MOD_ID + ":" + name, look(style, poor ? BuildingLooks.Quarter.POOR : BuildingLooks.Quarter.CRAFT,
                        seed++ * 104_729), name + "-" + style.id() + "-b"));
            }
            rows.add(new Row("street-plain-" + style.id(), plain));
        }
        return rows;
    }

    // ---------------------------------------------------------------- on the server

    /**
     * On the server: a kingdom on the flat land, every row of buildings laid out and finished, the shots worked out.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        KingdomWorldApi.findKingdom(server, "Capital Showcase").ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
        BlockPos start = player.blockPosition();
        int ground = start.getY();
        List<Row> rows = rows();
        // Work out where everything goes before founding, so that the land is claimed once.
        int z = start.getZ() + 20;
        int maxX = start.getX();
        List<int[]> rowFronts = new ArrayList<>();
        for (Row row : rows) {
            int depth = 0;
            int x = start.getX();
            for (Item item : row.items()) {
                BuildingTemplate template = BuildingTemplates.get(item.template()).orElse(null);
                if (template == null) {
                    continue;
                }
                depth = Math.max(depth, template.sizeZ());
                x += template.sizeX() + GAP;
            }
            z += depth;
            rowFronts.add(new int[]{z, depth});
            maxX = Math.max(maxX, x);
            z += ROW_GAP;
        }
        BlockPos throne = new BlockPos(start.getX() - 12, ground, start.getZ());
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Capital Showcase", 0x9A2A2A, playerId);
        if (!(result instanceof CreateKingdomResult.Created created)) {
            LOGGER.error(PREFIX + "the showcase kingdom was not founded: {}", result);
            failed = true;
            finished = true;
            return;
        }
        UUID kingdom = created.kingdom().id();
        ChunkPos from = new ChunkPos(new BlockPos(start.getX() - 40, ground, start.getZ() - 40));
        ChunkPos to = new ChunkPos(new BlockPos(maxX + 40, ground, z + 40));
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                KingdomWorldApi.claimChunk(level, new ChunkPos(cx, cz), kingdom, null);
            }
        }
        List<Shot> list = new ArrayList<>();
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int front = rowFronts.get(r)[0];
            int x = start.getX();
            int first = x;
            int tallest = 0;
            for (Item item : row.items()) {
                BuildingTemplate template = BuildingTemplates.get(item.template()).orElse(null);
                if (template == null || !template.available()) {
                    LOGGER.error(PREFIX + "{} is no available template", item.template());
                    failed = true;
                    continue;
                }
                // Fronts on the line of the street, the anchor in the middle of the front edge at the height of the street.
                BlockPos anchor = new BlockPos(x + template.anchor().getX(), ground, front - (template.sizeZ() - 1 - template.anchor().getZ()));
                KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom, template, anchor, Placement.NONE,
                        item.materials());
                if (change.construction().isEmpty()) {
                    LOGGER.error(PREFIX + "{} was not laid out: {}", item.template(), change.outcome());
                    failed = true;
                    x += template.sizeX() + GAP;
                    continue;
                }
                Construction construction = change.construction().get();
                if (ConstructionWork.finishNow(server, construction).isEmpty()) {
                    LOGGER.error(PREFIX + "{} could not be finished", item.template());
                    failed = true;
                    x += template.sizeX() + GAP;
                    continue;
                }
                LOGGER.info(PREFIX + "built {} as {} with {}", item.template(), item.label(), item.materials());
                int x1 = x;
                int z1 = front - template.sizeZ() + 1;
                int bottom = ground - template.anchor().getY();
                placeMarks(level, template, x1, bottom, z1);
                shots(list, row.name(), item, template, x1, z1, bottom, ground);
                tallest = Math.max(tallest, template.sizeY() - template.anchor().getY());
                x += template.sizeX() + GAP;
            }
            int last = x - GAP;
            // Along the street, from its west end at the height of the eyes and from above its middle.
            double middle = (first + last) / 2.0;
            list.add(new Shot(row.name() + "-street", first - 6.5, ground + 2.6, front + 5.5, -100.0f, 4.0f));
            list.add(new Shot(row.name() + "-street-back", last + 6.5, ground + 2.6, front + 5.5, 100.0f, 4.0f));
            double height = Math.max(30, (last - first) * 0.35);
            list.add(new Shot(row.name() + "-above", middle, ground + tallest + height, front + height * 0.9, 180.0f, 42.0f));
        }
        LOGGER.info(PREFIX + "{} building(s) in the register", state.construction().buildingsOf(kingdom).size());
        shots = List.copyOf(list);
        ready = !list.isEmpty();
    }

    /**
     * What a template leaves out for the world to put there: the throne of a palace, the bank of a treasury.
     */
    private static void placeMarks(ServerLevel level, BuildingTemplate template, int x1, int bottom, int z1) {
        String id = template.id().toString();
        TemplateCatalog.Entry entry = TemplateCatalog.get(id).orElse(null);
        if (entry == null) {
            return;
        }
        int cx = x1 + (template.sizeX() - 1) / 2;
        int cz = z1 + (template.sizeZ() - 1) / 2;
        int street = bottom + template.anchor().getY();
        if (entry.kind() == TemplateCatalog.Kind.CASTLE) {
            level.setBlock(new BlockPos(cx, street, cz), ModRegistries.THRONE.get().defaultBlockState().setValue(ThroneBlock.FACING,
                    Direction.SOUTH), Block.UPDATE_ALL);
        }
        for (TemplateCatalog.Spot spot : entry.points("bank")) {
            level.setBlock(new BlockPos(cx + spot.x(), street + spot.y(), cz + spot.z()), ModRegistries.BANK.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * The shots of one building: from the front and a little to the east, from the west side, and for a castle from afar
     * and its throne hall from inside.
     */
    private static void shots(List<Shot> list, String row, Item item, BuildingTemplate template, int x1, int z1, int bottom, int ground) {
        double width = template.sizeX();
        double depth = template.sizeZ();
        double height = template.sizeY() - template.anchor().getY();
        double cx = x1 + width / 2.0;
        double cz = z1 + depth / 2.0;
        double target = ground + height * 0.38;
        double size = Math.max(Math.max(width, depth), height);
        double distance = size * 0.95 + 8;
        list.add(look(row + "-" + item.label(), cx + distance * 0.45, target + distance * 0.42, cz + distance * 0.95, cx, target, cz));
        list.add(look(row + "-" + item.label() + "-side", cx - distance * 0.9, target + distance * 0.3, cz + distance * 0.55, cx, target, cz));
        TemplateCatalog.Entry entry = TemplateCatalog.get(template.id().toString()).orElse(null);
        if (entry != null && entry.kind() == TemplateCatalog.Kind.CASTLE) {
            double far = size * 1.9 + 20;
            list.add(look(row + "-" + item.label() + "-far", cx + far * 0.35, ground + 6, cz + far, cx, ground + height * 0.55, cz));
            list.add(look(row + "-" + item.label() + "-top", cx + 0.5, ground + height + size * 1.1, cz + size * 0.55, cx, ground, cz));
            double tx = x1 + (template.sizeX() - 1) / 2 + 0.5;
            double tz = z1 + (template.sizeZ() - 1) / 2 + 0.5;
            List<TemplateCatalog.Spot> door = entry.points("palace_door");
            if (!door.isEmpty()) {
                TemplateCatalog.Spot spot = door.get(0);
                list.add(look(row + "-" + item.label() + "-hall", tx + spot.x(), ground + spot.y() + 1.7, tz + spot.z(), tx, ground + 1.2, tz));
            }
            List<TemplateCatalog.Spot> gate = entry.points("castle_gate");
            if (!gate.isEmpty()) {
                TemplateCatalog.Spot spot = gate.get(0);
                list.add(look(row + "-" + item.label() + "-court", tx + spot.x() + 0.5, ground + spot.y() + 3.5, tz + spot.z() - 1, tx,
                        ground + height * 0.45, tz - depth * 0.15));
            }
        }
    }

    /**
     * A shot from a place looking at a point.
     */
    private static Shot look(String name, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new Shot(name, x, y, z, yaw, pitch);
    }
}
