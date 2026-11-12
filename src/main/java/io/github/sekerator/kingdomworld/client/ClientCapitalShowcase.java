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
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.npc.BuildingLooks;
import io.github.sekerator.kingdomworld.core.npc.GuildHouses;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.StyledPlan;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
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
 * twice over, each house in the look its plot would give it, and the other buildings of its towns; then the capital of every
 * style laid out by its plan ({@link NpcComposition#styled}): every building on its plot in the look of its plot, the ground,
 * trees, front gardens and fields of the plan and a plain wall. Every building is shot from the front, from the side and
 * inside, every row along its fronts and from above, the castles also from afar, from their courts and their throne halls from
 * inside, every capital from above, along its avenue, from its square and along its outer street. Screenshots go to the folder
 * {@code styles} of the screenshot folder. Meant for a flat copy of a test world: the buildings stay.
 *
 * <p>The capitals are small unless the rows name another size: {@code -PclientSelfTest=capital-terem@capital,large}.
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

    /**
     * Whether the capitals of the styles are shown: unless the rows named leave them out.
     */
    private boolean capitalsShown() {
        return rowsOnly.isEmpty() || rowsOnly.contains("capital");
    }

    /**
     * The size of the capitals shown: small unless the rows name {@code medium} or {@code large}.
     */
    private NpcLevel capitalSize() {
        return rowsOnly.contains("large") ? NpcLevel.LARGE : rowsOnly.contains("medium") ? NpcLevel.MEDIUM : NpcLevel.SMALL;
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
            // The capitals of all the styles are put up in one go on the server: the client waits for it.
            if (ticks > 7_200) {
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
        // South of the rows, the capital of every style as its plan lays it out, side by side.
        int capitals = z;
        NpcLevel size = capitalSize();
        int wall = NpcComposition.capitalWall(size, NpcSite.LAYOUT_STYLED);
        int spacing = 2 * wall + 40;
        int shownStyles = 0;
        for (String style : TemplateCatalog.STYLES) {
            shownStyles += shown(style) && capitalsShown() ? 1 : 0;
        }
        int capitalsEnd = shownStyles == 0 ? z : capitals + 50 + 2 * wall + 40;
        ChunkPos to = new ChunkPos(new BlockPos(Math.max(maxX, start.getX() + 60 + spacing * shownStyles) + 40, ground, Math.max(z, capitalsEnd) + 40));
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
            if (shown(style) && capitalsShown()) {
                capital(level, server, kingdom, style, size, start.getX() + 60 + wall + spacing * column, capitals + 50 + wall, ground, list);
                column++;
            }
        }
        LOGGER.info(PREFIX + "{} building(s) in the register", state.construction().buildingsOf(kingdom).size());
        shots = List.copyOf(list);
        ready = !list.isEmpty();
    }

    /**
     * Lays out and finishes a building with its anchor at a place, turned and mirrored as the placement says; false if it could
     * not be built.
     */
    private boolean build(ServerLevel level, IntegratedServer server, UUID kingdom, BuildingTemplate template, BlockPos anchor,
                          Placement placement, Materials materials) {
        int reach = Math.max(template.sizeX(), template.sizeZ()) + 2;
        for (int cx = (anchor.getX() - reach) >> 4; cx <= (anchor.getX() + reach) >> 4; cx++) {
            for (int cz = (anchor.getZ() - reach) >> 4; cz <= (anchor.getZ() + reach) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom, template, anchor, placement, materials);
        if (change.construction().isEmpty() || ConstructionWork.finishNow(server, change.construction().get()).isEmpty()) {
            LOGGER.error(PREFIX + "{} was not built in the capital of its style: {}", template.id(), change.outcome());
            failed = true;
            return false;
        }
        return true;
    }

    /**
     * A block by its id, or a fallback for an id the game does not know.
     */
    private static BlockState block(String id, Block fallback) {
        ResourceLocation key = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        return block == null || block == Blocks.AIR ? fallback.defaultBlockState() : block.defaultBlockState();
    }

    /**
     * The look of a building on a plot of a styled capital, as the raising of a kingdom of the world gives it: a template of the
     * style wears the look of the quarter of its plot, one of all styles (a market, a well) the public look; the seed of the
     * look comes from the place of the plot.
     */
    private static Materials plotLook(String style, long seed, NpcComposition.Plot plot) {
        boolean styled = TemplateCatalog.get(plot.template()).map(entry -> !entry.style().isEmpty()).orElse(false);
        BuildingLooks.Quarter quarter = BuildingLooks.Quarter.PUBLIC;
        if (styled) {
            for (BuildingLooks.Quarter candidate : BuildingLooks.Quarter.values()) {
                if (candidate.name().toLowerCase(java.util.Locale.ROOT).equals(plot.quarter())) {
                    quarter = candidate;
                }
            }
        }
        long plotSeed = seed ^ (0x9E3779B97F4A7C15L) ^ (plot.x() * 0xBF58476D1CE4E5B9L) ^ (plot.z() * 0x94D049BB133111EBL);
        return BuildingLooks.of(style, "minecraft:oak", "red", quarter, plotSeed);
    }

    /**
     * The capital of a style laid out by its plan, the throne at a place: the ground of the plan (the square and the avenues
     * paved, the streets of cobbles, the way along the wall, greens), every building of the plan on its plot in the look of its
     * plot, the throne in the castle and the bank in the treasury, the trees, the front gardens and the fields inside the wall
     * of the plan, and a plain wall with its gates open. Shot from above, along its south avenue, from a corner of its square
     * and along the street before its outer ring.
     *
     * @param ox the throne, east
     * @param oz the throne, south
     */
    private void capital(ServerLevel level, IntegratedServer server, UUID kingdom, String style, NpcLevel size, int ox, int oz, int ground,
                         List<Shot> list) {
        if (!ArchitecturalStyles.known(style)) {
            return;
        }
        long seed = style.hashCode() * 31L + 17;
        NpcComposition composition = NpcComposition.styled(size, seed, style, GuildHouses.highest(size), NpcRules.DEFAULT, 0);
        StyledPlan.Shape shape = StyledPlan.shape(size);
        int wall = composition.wallRadius();
        for (int cx = (ox - wall - 2) >> 4; cx <= (ox + wall + 2) >> 4; cx++) {
            for (int cz = (oz - wall - 2) >> 4; cz <= (oz + wall + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // The ground of the plan, on the surface of the flat world.
        BlockState paving = block(ArchitecturalStyles.paving(style), Blocks.STONE_BRICKS);
        BlockState cobbles = block(ArchitecturalStyles.cobbles(style), Blocks.COBBLESTONE);
        BlockState path = Blocks.DIRT_PATH.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int dx = -wall; dx <= wall; dx++) {
            for (int dz = -wall; dz <= wall; dz++) {
                int kind = composition.ground(dx, dz);
                BlockState top = kind == NpcComposition.GROUND_SQUARE || kind == NpcComposition.GROUND_AVENUE ? paving
                        : kind == NpcComposition.GROUND_STREET ? cobbles : kind == NpcComposition.GROUND_WALK ? path : grass;
                level.setBlock(new BlockPos(ox + dx, ground - 1, oz + dz), top, Block.UPDATE_CLIENTS);
            }
        }
        // The fields inside the wall.
        for (int[] spot : composition.fieldSpots()) {
            if (Math.max(Math.abs(spot[0]), Math.abs(spot[1])) + 5 < wall) {
                field(level, new BlockPos(ox + spot[0], ground - 1, oz + spot[1]));
            }
        }
        // The buildings, in the order of the plan.
        int built = 0;
        for (NpcComposition.Plot plot : composition.buildings()) {
            BuildingTemplate template = BuildingTemplates.get(plot.template()).orElse(null);
            if (template == null || !template.available()) {
                LOGGER.error(PREFIX + "{} of the plan of the {} capital is no available template", plot.template(), style);
                failed = true;
                continue;
            }
            Placement placement = new Placement(plot.facing(), plot.mirror());
            io.github.sekerator.kingdomworld.core.construction.Box box = template.box(BlockPos.ZERO, placement);
            int midX = Math.floorDiv(box.minX() + box.maxX(), 2);
            int midZ = Math.floorDiv(box.minZ() + box.maxZ(), 2);
            BlockPos anchor = new BlockPos(ox + plot.x() - midX, ground, oz + plot.z() - midZ);
            if (build(level, server, kingdom, template, anchor, placement, plotLook(style, seed, plot))) {
                built++;
            }
        }
        level.setBlock(new BlockPos(ox, ground, oz), ModRegistries.THRONE.get().defaultBlockState().setValue(ThroneBlock.FACING, Direction.SOUTH),
                Block.UPDATE_ALL);
        StyledPlan.bank(composition).ifPresent(bank -> level.setBlock(new BlockPos(ox + bank[0], ground + bank[2], oz + bank[1]),
                ModRegistries.BANK.get().defaultBlockState(), Block.UPDATE_ALL));
        for (int[] tree : composition.trees()) {
            tree(level, new BlockPos(ox + tree[0], ground, oz + tree[1]));
        }
        for (int[] garden : composition.gardens()) {
            garden(level, ox, oz, ground, garden);
        }
        plainWall(level, composition, style, ox, oz, ground);
        LOGGER.info(PREFIX + "the {} {} capital of the plan: {} of {} buildings, {} trees, {} front gardens", size.id(), style, built,
                composition.buildings().size(), composition.trees().size(), composition.gardens().size());
        String name = style + "-capital";
        int square = (shape.precinct() + shape.rings()[0]) / 2;
        list.add(new Shot(name + "-above", ox + 0.5, ground + wall * 1.4, oz + wall * 1.3, 180.0f, 48.0f));
        list.add(look(name + "-avenue", ox + 0.5, ground + 2.2, oz + wall - 16.5, ox + 0.5, ground + 14, oz + 0.5));
        list.add(look(name + "-square", ox + square + 0.5, ground + 4, oz + square + 0.5, ox + 0.5, ground + 14, oz + 0.5));
        // Along the street before the fronts of the outer ring on the south side, from the gate square to the east.
        int street = shape.last() + 2;
        list.add(look(name + "-street", ox + StyledPlan.GATE_CLEAR + 0.5, ground + 3, oz + street + 0.5, ox + wall - 8.5, ground + 3,
                oz + street - 1.5));
    }

    /**
     * A field of wheat around its water, eleven by eleven, on the surface.
     */
    private static void field(ServerLevel level, BlockPos middle) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos soil = middle.offset(dx, 0, dz);
                if (dx == 0 && dz == 0) {
                    level.setBlock(soil, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    continue;
                }
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7),
                        Block.UPDATE_CLIENTS);
                level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * A front garden: a fence along the street with an opening before the door, flowers between it and the house.
     *
     * @param garden {minX, minZ, maxX, maxZ, facing, door} from the throne
     */
    private static void garden(ServerLevel level, int ox, int oz, int ground, int[] garden) {
        int facing = garden[4];
        boolean alongX = facing == 0 || facing == 2;
        for (int x = garden[0]; x <= garden[2]; x++) {
            for (int z = garden[1]; z <= garden[3]; z++) {
                int along = alongX ? x : z;
                if (garden.length > 5 && along == garden[5]) {
                    continue;
                }
                boolean street = switch (facing) {
                    case 0 -> z == garden[3];
                    case 1 -> x == garden[0];
                    case 2 -> z == garden[1];
                    default -> x == garden[2];
                };
                BlockPos pos = new BlockPos(ox + x, ground, oz + z);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                BlockState state = street ? Blocks.OAK_FENCE.defaultBlockState()
                        : (Math.floorMod(along, 2) == 0 ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState();
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * A plain wall of the plan: stone of the style around a medium or large capital, a palisade of its wood around a small one,
     * five blocks high, open at the gates of the plan.
     */
    private static void plainWall(ServerLevel level, NpcComposition composition, String style, int ox, int oz, int ground) {
        int r = composition.wallRadius();
        String wood = BuildingLooks.styleWood(style);
        BlockState stone = composition.stoneWall() ? block(ArchitecturalStyles.wallBlock(style), Blocks.STONE_BRICKS)
                : block((wood.isEmpty() ? "minecraft:oak" : wood) + "_log", Blocks.OAK_LOG);
        for (int i = -r; i <= r; i++) {
            for (int[] at : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                boolean gate = false;
                for (NpcComposition.Gate opening : composition.gates()) {
                    gate |= Math.abs(at[0] - opening.x()) <= 2 && Math.abs(at[1] - opening.z()) <= 2;
                }
                if (gate) {
                    continue;
                }
                for (int y = 0; y < 5; y++) {
                    level.setBlock(new BlockPos(ox + at[0], ground + y, oz + at[1]), stone, Block.UPDATE_CLIENTS);
                }
            }
        }
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
