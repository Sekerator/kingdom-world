package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.client.screen.FoundKingdomScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkPostScreen;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Layout check of every screen of the mod, part of the client self-check ({@code -PclientSelfTest=layout}).
 *
 * <p>For English and Russian, for windows of 854x480 and 1920x1080 and GUI scales 2, 3 and 4 (clamped the way the
 * game clamps them), every screen is laid out and drawn into an off-screen image of that size. The layout check
 * then reports text wider than its place, cut-off lines, button texts that do not fit, anything outside the screen
 * and lines above the hotbar that are too long. Screenshots of scales 2 and 4 are written to the screenshot folder.
 * Any problem fails the check.
 */
final class ClientLayoutCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld layout check: ";
    private static final List<String> LANGUAGES = List.of("en_us", "ru_ru");
    private static final int[][] WINDOWS = {{854, 480}, {1920, 1080}};
    private static final int[] SCALES = {2, 3, 4};
    private static final List<Integer> SCREENSHOT_SCALES = List.of(2, 4);
    /** Narrowest GUI the game allows: it never picks a scale that makes the screen smaller than 320x240. */
    private static final int NARROWEST_SCREEN = 320;
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";

    private record Case(String name, Supplier<Screen> screen) {
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path screenshots;
    private final List<String> problems = new ArrayList<>();
    private int languageIndex;
    private CompletableFuture<Void> reload;
    private int checkedScreens;
    private int savedScreenshots;
    private boolean finished;
    private boolean failed;

    ClientLayoutCheck(Path screenshots) {
        this.screenshots = screenshots;
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    /**
     * Advances the check by one step; called every client tick.
     */
    void tick() {
        if (finished || (reload != null && !reload.isDone())) {
            return;
        }
        if (languageIndex >= LANGUAGES.size()) {
            finish();
            return;
        }
        String language = LANGUAGES.get(languageIndex);
        if (!language.equals(minecraft.getLanguageManager().getSelected())) {
            LOGGER.info(PREFIX + "switching the language to {}", language);
            minecraft.getLanguageManager().setSelected(language);
            reload = minecraft.reloadResourcePacks();
            return;
        }
        reload = null;
        checkLanguage(language);
        languageIndex++;
    }

    static int effectiveScale(int windowWidth, int windowHeight, int requested) {
        int scale = 1;
        while (scale != requested && scale < windowWidth && scale < windowHeight
                && windowWidth / (scale + 1) >= NARROWEST_SCREEN && windowHeight / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    private void checkLanguage(String language) {
        LayoutCheck.begin(language + " actionbar");
        int color = 0x1F618D;
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.protection.denied",
                ProtectionService.shortName(LONG_KINGDOM)), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.protection.denied_unknown"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.protection.throne"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.protection.throne_king"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.throne.needs_crown"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.protection.citizen",
                ProtectionService.shortName(LONG_KINGDOM)), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.storehouse.read_only"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.post.outside"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.post.read_only"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.post.foreign",
                ProtectionService.shortName(LONG_KINGDOM)), NARROWEST_SCREEN);
        for (Gender gender : Gender.values()) {
            LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.citizen.describe",
                    CitizenTexts.name(longestNameSeed(gender), gender), CitizenTexts.profession("unemployed", gender),
                    CitizenTexts.state(CitizenState.WOUNDED, gender)), NARROWEST_SCREEN);
        }
        for (boolean entered : new boolean[]{true, false}) {
            LayoutCheck.actionbar(minecraft.font, ClientPacketHandlers.borderMessage(minecraft.font, NARROWEST_SCREEN, entered,
                    LONG_KINGDOM, color), NARROWEST_SCREEN);
        }
        problems.addAll(LayoutCheck.end());

        for (int[] window : WINDOWS) {
            for (int requested : SCALES) {
                int scale = effectiveScale(window[0], window[1], requested);
                int guiWidth = (int) Math.ceil(window[0] / (double) scale);
                int guiHeight = (int) Math.ceil(window[1] / (double) scale);
                String size = window[0] + "x" + window[1] + " scale " + requested + (scale != requested ? " (effective " + scale + ")" : "");
                for (Case check : cases()) {
                    LayoutCheck.begin(language + " " + size + " " + check.name());
                    Screen screen = check.screen().get();
                    screen.init(minecraft, guiWidth, guiHeight);
                    Path file = SCREENSHOT_SCALES.contains(requested)
                            ? screenshots.resolve(language).resolve(window[0] + "x" + window[1] + "-scale" + requested + "-" + check.name() + ".png")
                            : null;
                    draw(screen, window[0], window[1], scale, file);
                    problems.addAll(LayoutCheck.end());
                    checkedScreens++;
                }
            }
        }
        LOGGER.info(PREFIX + "{} checked, {} problem(s) so far", language, problems.size());
    }

    private void draw(Screen screen, int pixelWidth, int pixelHeight, int scale, Path file) {
        RenderTarget target = new TextureTarget(pixelWidth, pixelHeight, true, Minecraft.ON_OSX);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        try {
            target.setClearColor(0.12f, 0.12f, 0.14f, 1.0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, pixelWidth / (float) scale, pixelHeight / (float) scale,
                    0.0f, 1000.0f, 21000.0f), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.pushPose();
            modelView.setIdentity();
            modelView.translate(0.0f, 0.0f, -11000.0f);
            RenderSystem.applyModelViewMatrix();
            GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            screen.render(graphics, -1000, -1000, 0.0f);
            graphics.flush();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            if (file != null) {
                try (NativeImage image = Screenshot.takeScreenshot(target)) {
                    Files.createDirectories(file.getParent());
                    image.writeToFile(file);
                    savedScreenshots++;
                } catch (IOException e) {
                    problems.add("could not write " + file + ": " + e.getMessage());
                }
            }
        } finally {
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    private void finish() {
        finished = true;
        ClientTerritoryCache.clear();
        failed = !problems.isEmpty();
        List<String> report = new ArrayList<>();
        report.add((failed ? "FAILED" : "PASSED") + ": " + checkedScreens + " screen layouts checked, " + savedScreenshots
                + " screenshots, " + problems.size() + " problem(s)");
        report.addAll(problems);
        try {
            Files.createDirectories(screenshots);
            Files.write(screenshots.resolve("layout-check.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not write the report", e);
        }
        for (String problem : problems) {
            LOGGER.error(PREFIX + "{}", problem);
        }
        if (failed) {
            LOGGER.error(PREFIX + "FAILED with {} problem(s) in {} screen layouts; screenshots in {}", problems.size(), checkedScreens, screenshots);
        } else {
            LOGGER.info(PREFIX + "PASSED, {} screen layouts in 2 languages, {} screenshots in {}", checkedScreens, savedScreenshots, screenshots);
        }
    }

    // ---------------------------------------------------------------- screens to check

    private static Map<Role, EnumSet<Permission>> defaultRights() {
        RolePermissions defaults = RolePermissions.defaults();
        Map<Role, EnumSet<Permission>> rights = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            rights.put(role, defaults.effective(role));
        }
        return rights;
    }

    private static KingdomView view(KingdomView.Relation relation, Role role) {
        boolean member = relation == KingdomView.Relation.MEMBER;
        List<KingdomView.MemberEntry> members = new ArrayList<>();
        List<KingdomView.PendingEntry> applications = new ArrayList<>();
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (member) {
            permissions = RolePermissions.defaults().effective(role);
            members.add(new KingdomView.MemberEntry(UUID.randomUUID(), "Dev", Role.KING, true));
            String[] names = {LONG_PLAYER, "Aleksandr_Volkov", "Ivan", "Kseniya_Zhukova", "Bob", "Miroslava_Grand", "Hal"};
            Role[] roles = {Role.COURT_MAGE, Role.SPYMASTER, Role.SUBJECT, Role.MARSHAL, Role.SUBJECT, Role.CHANCELLOR, Role.STEWARD};
            for (int i = 0; i < names.length; i++) {
                members.add(new KingdomView.MemberEntry(UUID.randomUUID(), names[i], roles[i], i % 2 == 0));
            }
            if (permissions.contains(Permission.RECRUIT)) {
                applications.add(new KingdomView.PendingEntry(UUID.randomUUID(), LONG_PLAYER));
                applications.add(new KingdomView.PendingEntry(UUID.randomUUID(), "Yaroslav_Mudry"));
            }
        }
        return new KingdomView(UUID.randomUUID(), LONG_KINGDOM, 0x6C3483, LONG_PLAYER, 8, 10, 53, 187,
                new BlockPos(-30_000_000, 319, 29_999_999), relation, member ? role : null, permissions, members, applications,
                member ? defaultRights() : Map.of());
    }

    private static ThroneScreen throne(KingdomView view, String tab, Component status) {
        ThroneScreen screen = new ThroneScreen(view);
        screen.selectTab(tab);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    /**
     * A seed that gives one of the longest names in the current language.
     */
    private static int longestNameSeed(Gender gender) {
        CitizenNames.Alphabet alphabet = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected());
        int best = 0;
        int bestWidth = -1;
        for (int seed = 0; seed < 20_000; seed++) {
            int width = Minecraft.getInstance().font.width(CitizenNames.render(seed, gender, alphabet));
            if (width > bestWidth) {
                best = seed;
                bestWidth = width;
            }
        }
        return best;
    }

    private static CitizenView citizenView(boolean canManage, boolean empty, CitizenNeeds.GrowthCheck growth) {
        List<CitizenView.Entry> entries = new ArrayList<>();
        List<CitizenView.Death> deaths = new ArrayList<>();
        if (!empty) {
            BlockPos far = new BlockPos(-30_000_000, 319, 29_999_999);
            String[] professions = {"farmer", "unemployed", "dragon_tamer", "lumberjack", "miner", "fisher", "herder", "carrier"};
            WorkStatus[] statuses = WorkStatus.values();
            for (int i = 0; i < 24; i++) {
                Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
                int seed = i < 2 ? longestNameSeed(gender) : i * 7919;
                entries.add(new CitizenView.Entry(UUID.nameUUIDFromBytes(("citizen" + i).getBytes(StandardCharsets.UTF_8)), seed,
                        gender == Gender.FEMALE, professions[i % professions.length],
                        i % 3 == 1 ? CitizenState.WOUNDED : CitizenState.HEALTHY, i % 4 == 3 ? null : far, 20 - i % 15, 50 + i,
                        i % 5 == 4 ? null : far, statuses[i % statuses.length]));
            }
            CauseOfDeath[] causes = CauseOfDeath.values();
            for (int i = 0; i < causes.length + 2; i++) {
                Gender gender = i % 2 == 0 ? Gender.MALE : Gender.FEMALE;
                int seed = i < 2 ? longestNameSeed(gender) : i * 104_729;
                CauseOfDeath cause = i < 2 ? CauseOfDeath.PLAYER : causes[i - 2];
                deaths.add(new CitizenView.Death(seed, gender == Gender.FEMALE, i % 2 == 0 ? "unemployed" : "farmer", cause,
                        i == 0 ? LONG_PLAYER : "", 99_999L - i, far));
            }
        }
        List<CitizenView.Production> production = new ArrayList<>();
        if (!empty) {
            String[] items = {"minecraft:wheat", "minecraft:oak_log", "minecraft:cobblestone", "minecraft:cod", "minecraft:beef",
                    "minecraft:carved_pumpkin", "minecraft:enchanted_golden_apple", "mod:missing_item"};
            String[] professions = {"farmer", "lumberjack", "miner", "fisher", "herder", "carrier", "dragon_tamer", "unemployed"};
            for (int i = 0; i < professions.length; i++) {
                production.add(new CitizenView.Production(professions[i], 9_999_999L - i, 9_999_999L - 2 * i, items[i]));
            }
        }
        return new CitizenView(UUID.randomUUID(), canManage, empty ? 0 : 187, empty ? 0 : 180, empty ? 0 : 200,
                empty ? 0 : CitizenView.MAX_FOOD_DAYS, empty ? 50 : 100, empty ? 0 : 999, growth, entries, deaths, production);
    }

    private static ThroneScreen citizens(KingdomView view, CitizenView citizens, boolean chronicle, Component status) {
        ThroneScreen screen = throne(view, "citizens", status);
        screen.updateCitizens(new CitizenView(view.id(), citizens.canManage(), citizens.population(), citizens.bedsUsed(),
                citizens.bedsTotal(), citizens.foodDays(), citizens.mood(), citizens.storehouses(), citizens.growth(),
                citizens.citizens(), citizens.chronicle(), citizens.production()));
        screen.showChronicle(chronicle);
        return screen;
    }

    private static StorageView storageView(UUID kingdom, boolean empty) {
        List<StorageView.Entry> items = new ArrayList<>();
        List<StorageView.Storehouse> storehouses = new ArrayList<>();
        if (!empty) {
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
            named.setHoverName(Component.literal("Меч Великого Северного Королевства Щит и Меч"));
            ItemStack[] samples = {named, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.COBBLESTONE),
                    new ItemStack(Items.OAK_LOG), new ItemStack(Items.COD), new ItemStack(Items.WHEAT), new ItemStack(Items.TORCH),
                    new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.LIGHT_BLUE_GLAZED_TERRACOTTA),
                    new ItemStack(Items.CHAINMAIL_CHESTPLATE), new ItemStack(Items.BEEF), new ItemStack(Items.WHITE_WOOL),
                    new ItemStack(Items.SPRUCE_SAPLING), new ItemStack(Items.COAL), new ItemStack(Items.RAW_IRON),
                    new ItemStack(Items.PUFFERFISH), new ItemStack(Items.MELON_SLICE), new ItemStack(Items.SUGAR_CANE),
                    new ItemStack(Items.PITCHER_PLANT), new ItemStack(Items.SHEARS), new ItemStack(Items.FISHING_ROD)};
            for (int i = 0; i < samples.length; i++) {
                items.add(new StorageView.Entry(samples[i], 999_999_999L - i * 1_000L));
            }
            for (int i = 0; i < 20; i++) {
                storehouses.add(new StorageView.Storehouse(new BlockPos(-30_000_000 + i, 319, 29_999_999), i == 0 ? 54 : i, 54));
            }
        }
        return new StorageView(kingdom, items, storehouses, empty ? 0 : 9_999, empty ? 0 : 999_999_999L);
    }

    private static ThroneScreen storage(KingdomView view, boolean empty, boolean storehouses, String search) {
        ThroneScreen screen = throne(view, "storage", Component.empty());
        StorageView data = storageView(view.id(), empty);
        screen.updateStorage(new StorageView(view.id(), data.items(), data.storehouses(), data.kinds(), data.total()));
        screen.showStorehouses(storehouses, search);
        return screen;
    }

    private static ThroneScreen citizensMode(KingdomView view, boolean canManage, boolean empty, String mode) {
        ThroneScreen screen = citizens(view, citizenView(canManage, empty, CitizenNeeds.GrowthCheck.READY), false, Component.empty());
        screen.showCitizens(mode);
        return screen;
    }

    private static WorkPostView postView(String profession, WorkStatus status, boolean canManage, int workers, boolean container,
                                         boolean measured) {
        List<WorkPostView.Worker> list = new ArrayList<>();
        WorkStatus[] statuses = WorkStatus.values();
        for (int i = 0; i < workers; i++) {
            Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
            list.add(new WorkPostView.Worker(UUID.nameUUIDFromBytes(("worker" + i).getBytes(StandardCharsets.UTF_8)),
                    i < 2 ? longestNameSeed(gender) : i * 7919, gender == Gender.FEMALE, statuses[i % statuses.length]));
        }
        List<String> animals = profession.equals("herder")
                ? List.of("minecraft:cow", "minecraft:sheep", "minecraft:mooshroom", "examplemod:very_long_named_highland_cattle")
                : List.of();
        return new WorkPostView(new BlockPos(-30_000_000, 319, 29_999_999), UUID.randomUUID(), canManage, profession, 16, 16,
                Math.max(1, workers), 16, profession.equals("herder") ? "minecraft:mooshroom" : "", animals, 64, 64, status, container,
                measured, measured ? 999_999 : 0, 9_999_999L, list, 9_999);
    }

    private static WorkPostScreen post(WorkPostView view, Component status) {
        WorkPostScreen screen = new WorkPostScreen(view);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static void fillMapData() {
        UUID first = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));
        List<KingdomMapInfo> palette = List.of(
                new KingdomMapInfo(first, LONG_KINGDOM, 0x6C3483, LONG_PLAYER, 8, 20, 187, true, 0, 0),
                new KingdomMapInfo(second, "Valoria", 0xB7950B, "", 1, 9, 0, true, 7, 1));
        int width = 20;
        int height = 12;
        int[] cells = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int chunkX = x - 6;
                int chunkZ = z - 6;
                if (Math.abs(chunkX) <= 2 && Math.abs(chunkZ) <= 1) {
                    cells[z * width + x] = 1;
                } else if (Math.abs(chunkX - 7) <= 1 && Math.abs(chunkZ - 1) <= 1) {
                    cells[z * width + x] = 2;
                }
            }
        }
        ClientTerritoryCache.applyArea(new MapAreaPacket("kingdomworld:layout_check", -6, -6, width, height, palette, cells));
    }

    private static List<Case> cases() {
        Component longError = KingdomActions.errorMessage(KingdomError.READ_ONLY);
        Component claimError = Component.translatable("kingdomworld.claim.failed", KingdomActions.chunkText(new ChunkPos(-117187, 117186)),
                KingdomActions.errorMessage(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM));
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("founding-empty", () -> new FoundKingdomScreen(BlockPos.ZERO, 0x1F618D)));
        cases.add(new Case("founding-long-name", () -> {
            FoundKingdomScreen screen = new FoundKingdomScreen(BlockPos.ZERO, 0x1F618D);
            screen.fillForCheck(LONG_KINGDOM, Component.translatable("kingdomworld.screen.found.waiting"), false);
            return screen;
        }));
        cases.add(new Case("founding-invalid-name", () -> {
            FoundKingdomScreen screen = new FoundKingdomScreen(BlockPos.ZERO, 0x1F618D);
            screen.fillForCheck("Abc!", Component.empty(), false);
            return screen;
        }));
        cases.add(new Case("founding-error", () -> {
            FoundKingdomScreen screen = new FoundKingdomScreen(BlockPos.ZERO, 0x1F618D);
            screen.fillForCheck(LONG_KINGDOM, Component.translatable("kingdomworld.kingdom.error.read_only"), true);
            return screen;
        }));
        cases.add(new Case("throne-king-overview", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "overview", longError)));
        cases.add(new Case("throne-king-members", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "members", Component.empty())));
        cases.add(new Case("throne-king-rights", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "rights", Component.empty())));
        cases.add(new Case("throne-subject-overview", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT), "overview", Component.empty())));
        cases.add(new Case("throne-marshal-members", () -> throne(view(KingdomView.Relation.MEMBER, Role.MARSHAL), "members", Component.empty())));
        cases.add(new Case("throne-subject-rights", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT), "rights", Component.empty())));
        cases.add(new Case("throne-king-citizens", () -> citizens(view(KingdomView.Relation.MEMBER, Role.KING),
                citizenView(true, false, CitizenNeeds.GrowthCheck.NOT_ENOUGH_FOOD), false, Component.empty())));
        cases.add(new Case("throne-king-citizens-error", () -> citizens(view(KingdomView.Relation.MEMBER, Role.KING),
                citizenView(true, false, CitizenNeeds.GrowthCheck.READY), false, longError)));
        cases.add(new Case("throne-steward-chronicle", () -> citizens(view(KingdomView.Relation.MEMBER, Role.STEWARD),
                citizenView(true, false, CitizenNeeds.GrowthCheck.NO_FREE_BED), true, Component.empty())));
        cases.add(new Case("throne-subject-citizens", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT),
                citizenView(false, false, CitizenNeeds.GrowthCheck.TOO_SOON), false, Component.empty())));
        cases.add(new Case("throne-citizens-empty", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT),
                citizenView(false, true, CitizenNeeds.GrowthCheck.FULL), false, Component.empty())));
        cases.add(new Case("throne-chronicle-empty", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT),
                citizenView(false, true, CitizenNeeds.GrowthCheck.NO_FREE_BED), true, Component.empty())));
        cases.add(new Case("throne-citizens-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "citizens",
                Component.empty())));
        cases.add(new Case("throne-king-work", () -> citizensMode(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "work")));
        cases.add(new Case("throne-subject-work", () -> citizensMode(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "work")));
        cases.add(new Case("throne-king-production", () -> citizensMode(view(KingdomView.Relation.MEMBER, Role.KING), true, false,
                "production")));
        cases.add(new Case("throne-production-empty", () -> citizensMode(view(KingdomView.Relation.MEMBER, Role.KING), true, true,
                "production")));
        cases.add(new Case("throne-king-storage", () -> storage(view(KingdomView.Relation.MEMBER, Role.KING), false, false, "")));
        cases.add(new Case("throne-storage-search", () -> storage(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false,
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")));
        cases.add(new Case("throne-storehouses", () -> storage(view(KingdomView.Relation.MEMBER, Role.KING), false, true, "")));
        cases.add(new Case("throne-storage-empty", () -> storage(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "")));
        cases.add(new Case("throne-storehouses-empty", () -> storage(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "")));
        cases.add(new Case("throne-storage-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "storage",
                Component.empty())));
        cases.add(new Case("post-farmer-working", () -> post(postView("farmer", WorkStatus.WORKING, true, 2, true, true), Component.empty())));
        cases.add(new Case("post-lumberjack-no-trees", () -> post(postView("lumberjack", WorkStatus.NO_TREES, true, 3, false, false),
                Component.empty())));
        cases.add(new Case("post-miner-no-tool", () -> post(postView("miner", WorkStatus.NO_TOOL, true, 4, true, false), longError)));
        cases.add(new Case("post-fisher-no-water", () -> post(postView("fisher", WorkStatus.NO_WATER, true, 1, false, true),
                Component.empty())));
        cases.add(new Case("post-herder", () -> post(postView("herder", WorkStatus.NO_FEED, true, 2, true, true), Component.empty())));
        cases.add(new Case("post-herder-read-only", () -> post(postView("herder", WorkStatus.WAITING, false, 2, true, true),
                Component.empty())));
        cases.add(new Case("post-carrier", () -> post(postView("carrier", WorkStatus.NOTHING_TO_CARRY, true, 16, false, false),
                Component.empty())));
        cases.add(new Case("post-no-profession", () -> post(postView("unemployed", WorkStatus.NO_PROFESSION, true, 0, false, false),
                Component.empty())));
        cases.add(new Case("post-read-only", () -> post(postView("miner", WorkStatus.ZONE_PARTLY_OUTSIDE, false, 16, true, true),
                Component.empty())));
        cases.add(new Case("post-hired", () -> post(postView("lumberjack", WorkStatus.WORKING, true, 1, true, true),
                Component.translatable("kingdomworld.post.hired", CitizenTexts.name(longestNameSeed(Gender.FEMALE), Gender.FEMALE),
                        CitizenTexts.profession("lumberjack", Gender.FEMALE)))));
        cases.add(new Case("throne-outsider", () -> throne(view(KingdomView.Relation.OUTSIDER, null), "overview", Component.empty())));
        cases.add(new Case("throne-invited", () -> throne(view(KingdomView.Relation.INVITED, null), "overview", Component.empty())));
        cases.add(new Case("throne-applied", () -> throne(view(KingdomView.Relation.APPLIED, null), "overview", Component.empty())));
        cases.add(new Case("throne-other-kingdom", () -> throne(view(KingdomView.Relation.OTHER_KINGDOM, null), "overview", Component.empty())));
        cases.add(new Case("map-view-card", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(1.0, -0.5, 14.0, UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8)), Component.empty(), false);
            return screen;
        }));
        cases.add(new Case("map-edit-status", () -> {
            fillMapData();
            KingdomView editing = view(KingdomView.Relation.MEMBER, Role.KING);
            KingdomMapScreen screen = new KingdomMapScreen(editing);
            screen.showForCheck(1.0, -0.5, 9.0, null, claimError, true);
            return screen;
        }));
        cases.add(new Case("confirm-dissolve", () -> new ConfirmScreen(yes -> {
        }, Component.literal(LONG_KINGDOM), Component.translatable("kingdomworld.screen.throne.dissolve.confirm", LONG_KINGDOM))));
        cases.add(new Case("confirm-crown", () -> new ConfirmScreen(yes -> {
        }, Component.literal(LONG_KINGDOM), Component.translatable("kingdomworld.screen.throne.crown.confirm", LONG_PLAYER))));
        return cases;
    }
}
