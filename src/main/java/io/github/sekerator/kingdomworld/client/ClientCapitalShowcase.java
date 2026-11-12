package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The buildings of the architectural styles of the kingdoms of the world, shown in a flat test world
 * ({@code -PclientSelfTest=capital}): for every style its castles, the houses of its guilds by rank, a street of its houses
 * twice over, each house in the look its plot would give it, and the other buildings of its towns; then a part of a capital
 * of every style laid out as its plan will lay it out (castle, square, guild, mage tower, an avenue of houses). Every building is shot
 * from the front, from the side and inside, every row along its fronts and from above, the castles also from afar, from
 * their courts and their throne halls from inside. Screenshots go to the folder {@code styles} of the
 * screenshot folder. Meant for a flat copy of a test world: the buildings stay.
 */
final class ClientCapitalShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 70;
    /** Blocks between two buildings of a row and between two rows. */
    private static final int GAP = 14;
    private static final int ROW_GAP = 64;

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

    /**
     * The styles to show, separated by commas ({@code -PclientSelfTest=capital-terem} or {@code capital-nordic,southern}),
     * or empty for all of them.
     */
    private final List<String> only;
    /** The rows to show (houses, guilds, castles, town, capital), all of them when empty: {@code capital-nordic@houses}. */
    private final List<String> rowsOnly;

    ClientCapitalShowcase(Path folder, String only) {
        this.folder = folder.resolve("styles");
        String styles = only.contains("@") ? only.substring(0, only.indexOf('@')) : only;
        String rows = only.contains("@") ? only.substring(only.indexOf('@') + 1) : "";
        this.only = java.util.Arrays.stream(styles.split(",")).map(String::trim).filter(style -> !style.isEmpty()).toList();
        this.rowsOnly = java.util.Arrays.stream(rows.split(",")).map(String::trim).filter(row -> !row.isEmpty()).toList();
    }

    /**
     * Whether a style is shown: every one when no styles were named.
     */
    private boolean shown(String style) {
        return only.isEmpty() || only.contains(style);
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

    /**
     * The quarter whose look a house of a kind wears.
     */
    private static BuildingLooks.Quarter quarter(TemplateCatalog.Kind kind) {
        return switch (kind) {
            case POOR_HOUSE -> BuildingLooks.Quarter.POOR;
            case CRAFT_HOUSE -> BuildingLooks.Quarter.CRAFT;
            case MERCHANT_HOUSE -> BuildingLooks.Quarter.MERCHANT;
            case NOBLE_HOUSE -> BuildingLooks.Quarter.NOBLE;
            case CASTLE -> BuildingLooks.Quarter.CASTLE;
            case GUILD -> BuildingLooks.Quarter.GUILD;
            case MAGE_TOWER -> BuildingLooks.Quarter.MAGES;
            default -> BuildingLooks.Quarter.PUBLIC;
        };
    }

    /**
     * The buildings of a style of the given kinds, each in the look of its quarter.
     */
    private static List<Item> items(String style, long[] seed, String suffix, TemplateCatalog.Kind... kinds) {
        List<Item> items = new ArrayList<>();
        for (TemplateCatalog.Kind kind : kinds) {
            for (TemplateCatalog.Entry entry : TemplateCatalog.ofStyle(style, kind)) {
                String name = entry.id().substring(entry.id().indexOf(':') + 1);
                Materials materials = BuildingLooks.of(style, "minecraft:oak", "red", quarter(kind), seed[0]);
                seed[0] += 7_919;
                items.add(new Item(entry.id(), materials, name + suffix));
            }
        }
        return items;
    }

    /**
     * For every architectural style: its castles, the houses of its guilds by rank, its houses twice over with other looks,
     * and the other buildings of its towns and capitals.
     */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        long[] seed = {1};
        for (String style : TemplateCatalog.STYLES) {
            if (!shown(style)) {
                continue;
            }
            rows.add(new Row(style + "-castles", items(style, seed, "", TemplateCatalog.Kind.CASTLE)));
            rows.add(new Row(style + "-guilds", items(style, seed, "", TemplateCatalog.Kind.GUILD, TemplateCatalog.Kind.MAGE_TOWER)));
            TemplateCatalog.Kind[] houses = {TemplateCatalog.Kind.POOR_HOUSE, TemplateCatalog.Kind.CRAFT_HOUSE,
                    TemplateCatalog.Kind.MERCHANT_HOUSE, TemplateCatalog.Kind.NOBLE_HOUSE};
            List<Item> street = new ArrayList<>(items(style, seed, "", houses));
            street.addAll(items(style, seed, "-b", houses));
            rows.add(new Row(style + "-houses", street));
            rows.add(new Row(style + "-town", items(style, seed, "", TemplateCatalog.Kind.WORKSHOP, TemplateCatalog.Kind.TAVERN,
                    TemplateCatalog.Kind.WALL_GATE, TemplateCatalog.Kind.TREASURY, TemplateCatalog.Kind.COURT, TemplateCatalog.Kind.INFIRMARY,
                    TemplateCatalog.Kind.YARD, TemplateCatalog.Kind.DECORATION, TemplateCatalog.Kind.BARBICAN, TemplateCatalog.Kind.CASTLE_WARD,
                    TemplateCatalog.Kind.ROYAL_BARRACKS)));
        }
        rows.removeIf(row -> row.items().isEmpty()
                || !rowsOnly.isEmpty() && rowsOnly.stream().noneMatch(name -> row.name().endsWith("-" + name)));
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
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModRegistries.BLUEPRINT.get())) {
                player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
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
        // South of the rows, every style once more as a part of a capital: castle, square, guild, mage tower and an avenue.
        int capitals = z;
        ChunkPos to = new ChunkPos(new BlockPos(Math.max(maxX, start.getX() + 140 * TemplateCatalog.STYLES.size()) + 40, ground, z + 200));
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
                for (int cx = (x - 1) >> 4; cx <= (x + template.sizeX() + 1) >> 4; cx++) {
                    for (int cz = (front - template.sizeZ() - 1) >> 4; cz <= (front + 1) >> 4; cz++) {
                        level.getChunk(cx, cz);
                    }
                }
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
                shots(level, list, row.name(), item, template, x1, z1, bottom, ground);
                tallest = Math.max(tallest, template.sizeY() - template.anchor().getY());
                x += template.sizeX() + GAP;
            }
            int last = x - GAP;
            // Along the street, from its west end at the height of the eyes and from above its middle.
            double middle = (first + last) / 2.0;
            // Along the street from before its west end, so that a row of different houses shows, none of them close.
            list.add(look(row.name() + "-street", first - 12.5, ground + 7, front + 14.5, first + 45, ground + 3, front - 4));
            list.add(new Shot(row.name() + "-street-back", last + 6.5, ground + 2.6, front + 5.5, 100.0f, 4.0f));
            double height = Math.max(30, (last - first) * 0.35);
            list.add(new Shot(row.name() + "-above", middle, ground + tallest + height, front + height * 0.9, 180.0f, 42.0f));
        }
        int column = 0;
        for (String style : TemplateCatalog.STYLES) {
            if (shown(style) && (rowsOnly.isEmpty() || rowsOnly.contains("capital"))) {
                capital(level, server, kingdom, style, start.getX() + 60 + 140 * column, capitals + 50, ground, list);
                column++;
            }
        }
        LOGGER.info(PREFIX + "{} building(s) in the register", state.construction().buildingsOf(kingdom).size());
        shots = List.copyOf(list);
        ready = !list.isEmpty();
    }

    /**
     * Lays out and finishes a building with its anchor at a place, turned to face the given way (0 south, 1 west, 2 north,
     * 3 east); false if it could not be built.
     */
    private boolean build(ServerLevel level, IntegratedServer server, UUID kingdom, BuildingTemplate template, BlockPos anchor, int facing,
                          Materials materials) {
        int reach = Math.max(template.sizeX(), template.sizeZ()) + 2;
        for (int cx = (anchor.getX() - reach) >> 4; cx <= (anchor.getX() + reach) >> 4; cx++) {
            for (int cz = (anchor.getZ() - reach) >> 4; cz <= (anchor.getZ() + reach) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom, template, anchor, new Placement(facing, false),
                materials);
        if (change.construction().isEmpty() || ConstructionWork.finishNow(server, change.construction().get()).isEmpty()) {
            LOGGER.error(PREFIX + "{} was not built in the capital of its style: {}", template.id(), change.outcome());
            failed = true;
            return false;
        }
        return true;
    }

    /**
     * A part of a capital of a style laid out the way its plan will lay it out: the castle at the north, a paved square
     * before its gate, the guild house and the mage tower facing the castle across the square on either side of the mouth of
     * an avenue, and houses facing each other along the avenue, the richest nearest the square; lamps and trees. Shot from
     * above, along the avenue and from a corner of the square.
     *
     * @param ox the middle of the front of the castle
     * @param oz the line of the front of the castle
     */
    private void capital(ServerLevel level, IntegratedServer server, UUID kingdom, String style, int ox, int oz, int ground, List<Shot> list) {
        List<TemplateCatalog.Entry> castles = TemplateCatalog.ofStyle(style, TemplateCatalog.Kind.CASTLE);
        if (castles.isEmpty()) {
            return;
        }
        long seed = style.hashCode() * 31L + 17;
        BuildingTemplate castle = BuildingTemplates.get(castles.get(Math.min(1, castles.size() - 1)).id()).orElse(null);
        if (castle == null) {
            return;
        }
        boolean wooden = style.equals("terem");
        net.minecraft.world.level.block.state.BlockState paving = (wooden ? Blocks.SPRUCE_PLANKS : Blocks.STONE_BRICKS).defaultBlockState();
        net.minecraft.world.level.block.state.BlockState avenue = (wooden ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.COBBLESTONE).defaultBlockState();
        if (build(level, server, kingdom, castle, new BlockPos(ox, ground, oz), 0, BuildingLooks.of(style, "minecraft:oak", "red",
                BuildingLooks.Quarter.CASTLE, seed))) {
            placeMarks(level, castle, ox - castle.anchor().getX(), ground - castle.anchor().getY(), oz - castle.anchor().getZ());
        }
        // The square before the castle.
        int square = 18;
        for (int x = ox - 24; x <= ox + 24; x++) {
            for (int z = oz + 1; z <= oz + square; z++) {
                level.setBlock(new BlockPos(x, ground - 1, z), paving, Block.UPDATE_CLIENTS);
            }
        }
        // The guild house and the mage tower across the square, facing the castle.
        int deepest = 0;
        TemplateCatalog.Entry guild = TemplateCatalog.guildHouse(style, TemplateCatalog.Kind.GUILD, TemplateCatalog.Rank.S).orElse(null);
        TemplateCatalog.Entry mages = TemplateCatalog.guildHouse(style, TemplateCatalog.Kind.MAGE_TOWER, TemplateCatalog.Rank.S).orElse(null);
        if (guild != null) {
            BuildingTemplates.get(guild.id()).ifPresent(template -> build(level, server, kingdom, template,
                    new BlockPos(ox - 5 - (template.sizeX() + 1) / 2, ground, oz + square + 1), 2,
                    BuildingLooks.of(style, "minecraft:oak", "red", BuildingLooks.Quarter.GUILD, seed + 1)));
            deepest = Math.max(deepest, guild.depth());
        }
        if (mages != null) {
            BuildingTemplates.get(mages.id()).ifPresent(template -> build(level, server, kingdom, template,
                    new BlockPos(ox + 5 + (template.sizeX() + 1) / 2, ground, oz + square + 1), 2,
                    BuildingLooks.of(style, "minecraft:oak", "red", BuildingLooks.Quarter.MAGES, seed + 2)));
            deepest = Math.max(deepest, mages.depth());
        }
        // The avenue southwards between them, houses on both sides facing it, the richest first.
        List<TemplateCatalog.Entry> houses = new ArrayList<>();
        for (TemplateCatalog.Kind kind : new TemplateCatalog.Kind[]{TemplateCatalog.Kind.NOBLE_HOUSE, TemplateCatalog.Kind.MERCHANT_HOUSE,
                TemplateCatalog.Kind.CRAFT_HOUSE, TemplateCatalog.Kind.POOR_HOUSE}) {
            houses.addAll(TemplateCatalog.ofStyle(style, kind));
        }
        int[] cursor = {oz + square + deepest + 3, oz + square + deepest + 3};
        long houseSeed = seed * 7;
        for (int i = 0; i < houses.size(); i++) {
            TemplateCatalog.Entry entry = houses.get(i);
            BuildingTemplate template = BuildingTemplates.get(entry.id()).orElse(null);
            if (template == null) {
                continue;
            }
            int side = i % 2;
            int along = cursor[side] + template.sizeX() / 2;
            BlockPos anchor = new BlockPos(side == 0 ? ox - 5 : ox + 5, ground, along);
            build(level, server, kingdom, template, anchor, side == 0 ? 3 : 1, BuildingLooks.of(style, "minecraft:oak", "red",
                    quarter(entry.kind()), houseSeed += 104_729));
            cursor[side] += template.sizeX() + 2 + (i % 3);
        }
        int end = Math.max(cursor[0], cursor[1]) + 4;
        for (int x = ox - 4; x <= ox + 4; x++) {
            for (int z = oz + square + 1; z <= end; z++) {
                level.setBlock(new BlockPos(x, ground - 1, z), avenue, Block.UPDATE_CLIENTS);
            }
        }
        // Lamps along the avenue and at the corners of the square, trees on the square.
        for (int z = oz + 4; z <= end; z += 8) {
            for (int x : new int[]{ox - 4, ox + 4}) {
                if (z <= oz + square && Math.abs(x - ox) < 20) {
                    continue;
                }
                lamp(level, new BlockPos(x, ground, z));
            }
        }
        for (int x : new int[]{ox - 22, ox + 22}) {
            lamp(level, new BlockPos(x, ground, oz + 2));
            tree(level, new BlockPos(x, ground, oz + square - 3));
        }
        double middle = oz + (end - oz) * 0.4;
        list.add(look(style + "-capital-above", ox + 48, ground + 42, middle + 22, ox, ground + 6, middle - 6));
        list.add(look(style + "-capital-avenue", ox + 0.5, ground + 2.2, end - 1.5, ox + 0.5, ground + 14, oz - 25));
        list.add(look(style + "-capital-square", ox + 13.5, ground + 4, oz + square - 2.5, ox - 4, ground + 12, oz - 18));
    }

    private static void lamp(ServerLevel level, BlockPos at) {
        level.setBlock(at, Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.above(2), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void tree(ServerLevel level, BlockPos at) {
        for (int y = 0; y < 5; y++) {
            level.setBlock(at.above(y), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        }
        net.minecraft.world.level.block.state.BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 3; y <= 6; y++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.max(0, y - 5) * 2 <= 3 && level.getBlockState(at.offset(dx, y, dz)).isAir()) {
                        level.setBlock(at.offset(dx, y, dz), leaves, Block.UPDATE_ALL);
                    }
                }
            }
        }
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
    private static void shots(ServerLevel level, List<Shot> list, String row, Item item, BuildingTemplate template, int x1, int z1, int bottom,
                              int ground) {
        double width = template.sizeX();
        double depth = template.sizeZ();
        double height = template.sizeY() - template.anchor().getY();
        double cx = x1 + width / 2.0;
        double cz = z1 + depth / 2.0;
        double target = ground + height * 0.38;
        double size = Math.max(Math.max(width, depth), height);
        double distance = size * 0.95 + 8;
        list.add(look(row + "-" + item.label(), cx + distance * 0.45, target + distance * 0.42, cz + distance * 0.95, cx, target, cz));
        TemplateCatalog.Entry entry = TemplateCatalog.get(template.id().toString()).orElse(null);
        if (item.label().endsWith("-b")) {
            // The second house of a kind: only its look matters.
            return;
        }
        list.add(look(row + "-" + item.label() + "-side", cx - distance * 0.9, target + distance * 0.3, cz + distance * 0.55, cx, target, cz));
        if (entry == null || entry.kind() != TemplateCatalog.Kind.CASTLE) {
            // Inside, from the first free place behind the front wall, looking at the back of the ground floor.
            int[] inside = inside(level, x1, z1, template, ground);
            if (inside != null) {
                // Stand a step back against the front wall, look at the far end of the view, a little down.
                list.add(look(row + "-" + item.label() + "-inside", inside[0] + 0.5, inside[1] + 1.6, inside[2] + 0.9, inside[0] + 0.5,
                        inside[1] + 1.0, inside[2] - inside[3] + 0.5));
            }
        }
        if (entry != null && entry.kind() == TemplateCatalog.Kind.CASTLE) {
            double far = size * 1.9 + 20;
            // From afar and a little above, over the low houses of the rows in front.
            list.add(look(row + "-" + item.label() + "-far", cx + far * 0.35, ground + 12 + far * 0.2, cz + far, cx, ground + height * 0.5, cz));
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
                // In the court: from the gate northwards out of the passage under the open sky, looking at the keep.
                TemplateCatalog.Spot spot = gate.get(0);
                BlockPos at = new BlockPos((int) Math.floor(tx + spot.x()), ground + spot.y(), (int) Math.floor(tz + spot.z()));
                int steps = 0;
                while (steps < 16 && !open(level, at)) {
                    at = at.north();
                    steps++;
                }
                at = at.north(3);
                list.add(look(row + "-" + item.label() + "-court", at.getX() + 0.5, at.getY() + 2.6, at.getZ() + 0.5, tx, ground + 7, tz));
            }
        }
    }

    /**
     * Where one stands inside a building to see most of its ground floor: of the places in its front half with a floor,
     * room for a body and a roof over it, at the layers of the street and the two above it, the one with the longest free
     * view toward the back at the height of the eyes; its x, y and z, and in the fourth number how far the view goes.
     */
    private static int[] inside(ServerLevel level, int x1, int z1, BuildingTemplate template, int ground) {
        int middle = x1 + (template.sizeX() - 1) / 2;
        int[] best = null;
        for (int depth = 2; depth < Math.max(3, template.sizeZ() / 2); depth++) {
            int z = z1 + template.sizeZ() - 1 - depth;
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = ground; y <= ground + 2; y++) {
                    BlockPos feet = new BlockPos(middle + dx, y, z);
                    if (!free(level, feet) || !free(level, feet.above()) || free(level, feet.below()) || !level.getBlockState(feet).isAir()) {
                        continue;
                    }
                    boolean roof = false;
                    for (int up = 2; up < 12 && !roof; up++) {
                        roof = !level.getBlockState(feet.above(up)).isAir();
                    }
                    int view = 0;
                    while (view < 24 && free(level, feet.above().north(view + 1))) {
                        view++;
                    }
                    if (roof && (best == null || view > best[3])) {
                        best = new int[]{feet.getX(), feet.getY(), feet.getZ(), view};
                    }
                }
            }
        }
        return best != null && best[3] >= 3 ? best : null;
    }

    /**
     * Whether nothing stands over a place up to twenty layers.
     */
    private static boolean open(ServerLevel level, BlockPos pos) {
        for (int up = 1; up <= 20; up++) {
            if (!level.getBlockState(pos.above(up)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
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
