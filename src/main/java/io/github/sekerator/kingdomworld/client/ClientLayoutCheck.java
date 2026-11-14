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
import io.github.sekerator.kingdomworld.client.screen.ItemTooltipScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkPostScreen;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.CatalogView;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.OrdersView;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.client.screen.ExchangeScreen;
import io.github.sekerator.kingdomworld.client.screen.StallScreen;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
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
    /** With {@code -PclientSelfTestOnly=<text>} only the screens whose name holds the text are checked. */
    private static final String ONLY = System.getProperty("kingdomworld.clientSelfTest.only", "");
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";
    /** A ruler of the world with long decrees and every kind of need, its names in the current language. */
    private static io.github.sekerator.kingdomworld.common.network.data.RulerInfo longRuler(boolean councillor) {
        return new io.github.sekerator.kingdomworld.common.network.data.RulerInfo(List.of(
                    new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(1234,
                            new io.github.sekerator.kingdomworld.core.npc.RulerDecision(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.OFFER,
                                    "minecraft:iron_ingot", -64, 900, "", cyrillic() ? "Сереброземье-над-Рекою XIV" : "Serebrozemye-nad-Rekoyu XIV")),
                    new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(1233,
                            io.github.sekerator.kingdomworld.core.npc.RulerDecision.of(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.BUILD,
                                    "mage_tower", 1)),
                    new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(1232,
                            io.github.sekerator.kingdomworld.core.npc.RulerDecision.of(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.PAUSE,
                                    "", 2))),
                    List.of(new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.FOOD,
                                    "minecraft:bread", 140),
                            new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.GOODS,
                                    "minecraft:iron_ingot", 64),
                            new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.BUILDING,
                                    "barracks", 1200),
                            new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.MONEY,
                                    "", 15000),
                            new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.SOLDIERS,
                                    "", 3),
                            new io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need(io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind.LAND,
                                    "", 0)),
                    35, 20, 15, LONG_PLAYER, councillor);
    }

    /**
     * Whether the current language writes names of the world in Cyrillic, as the server sends them to its players.
     */
    private static boolean cyrillic() {
        return CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected()) == CitizenNames.Alphabet.CYRILLIC;
    }

    /** The widest count of people or soldiers a kingdom of the world is shown with: five digits in the longest plural form. */
    private static final int WIDE_COUNT = 99_999;
    /**
     * A kingdom of the world with the longest names, leaning and traits there are, five digit people and soldiers, and the
     * longest ranks of guilds.
     */
    private static final io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo LONG_NPC =
            new io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo("Serebrozemye XIV", "Сереброземье XIV", "Dobroslaslava",
                    "Доброславслава", io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE,
                    io.github.sekerator.kingdomworld.core.npc.RulerCharacter.BUILDER,
                    List.of(io.github.sekerator.kingdomworld.core.npc.RulerCharacter.Trait.JUST,
                            io.github.sekerator.kingdomworld.core.npc.RulerCharacter.Trait.THRIFTY),
                    io.github.sekerator.kingdomworld.core.npc.NpcLevel.MEDIUM, 3, false, WIDE_COUNT, WIDE_COUNT, 64, WIDE_COUNT,
                    io.github.sekerator.kingdomworld.core.guild.Rank.SSS, io.github.sekerator.kingdomworld.core.guild.Rank.SSS);
    /** The same kingdom founded as data, large, its capital still being built, with the people and soldiers it expects. */
    private static final io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo RAISING_NPC =
            new io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo(LONG_NPC.nameLatin(), LONG_NPC.nameCyrillic(),
                    LONG_NPC.rulerLatin(), LONG_NPC.rulerCyrillic(), LONG_NPC.rulerGender(), LONG_NPC.character(), LONG_NPC.traits(),
                    io.github.sekerator.kingdomworld.core.npc.NpcLevel.LARGE, 3, true, WIDE_COUNT, WIDE_COUNT, 64, WIDE_COUNT,
                    io.github.sekerator.kingdomworld.core.guild.Rank.SS, io.github.sekerator.kingdomworld.core.guild.Rank.S);

    /**
     * A screen to check; {@code screenshots} false checks it without saving pictures of it (the many pages of the guide).
     */
    private record Case(String name, Supplier<Screen> screen, boolean screenshots,
                        java.util.function.Function<Screen, int[]> mouse) {
        Case(String name, Supplier<Screen> screen) {
            this(name, screen, true, null);
        }

        Case(String name, Supplier<Screen> screen, boolean screenshots) {
            this(name, screen, screenshots, null);
        }
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
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.storehouse.outside"), NARROWEST_SCREEN);
        // The pointer to a citizen looked for, with the longest name, distance and side.
        for (Gender gender : Gender.values()) {
            for (boolean live : new boolean[]{true, false}) {
                LayoutCheck.actionbar(minecraft.font, CitizenFinder.line("↖", longestNameSeed(gender), gender == Gender.FEMALE, 29_999_999,
                        io.github.sekerator.kingdomworld.core.citizen.Whereabouts.Compass.NORTH_WEST, live), NARROWEST_SCREEN);
            }
        }
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.storehouse.foreign",
                ProtectionService.shortName(LONG_KINGDOM)), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.post.outside"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.post.read_only"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("block.kingdomworld.mint.use"), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, KingdomActions.errorMessage(KingdomError.BANK_OUTSIDE), NARROWEST_SCREEN);
        LayoutCheck.actionbar(minecraft.font, Component.translatable("kingdomworld.bank.read_only"), NARROWEST_SCREEN);
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
        GuildLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        ArmyLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        MagicLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        CompanionLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        LawLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        DiplomacyLayoutCases.actionbars(minecraft.font, NARROWEST_SCREEN);
        problems.addAll(LayoutCheck.end());

        for (int[] window : WINDOWS) {
            for (int requested : SCALES) {
                int scale = effectiveScale(window[0], window[1], requested);
                int guiWidth = (int) Math.ceil(window[0] / (double) scale);
                int guiHeight = (int) Math.ceil(window[1] / (double) scale);
                String size = window[0] + "x" + window[1] + " scale " + requested + (scale != requested ? " (effective " + scale + ")" : "");
                for (Case check : cases()) {
                    if (!ONLY.isEmpty() && java.util.Arrays.stream(ONLY.split(",")).noneMatch(part -> check.name().contains(part.strip()))) {
                        continue;
                    }
                    LayoutCheck.begin(language + " " + size + " " + check.name());
                    Screen screen = check.screen().get();
                    screen.init(minecraft, guiWidth, guiHeight);
                    Path file = SCREENSHOT_SCALES.contains(requested) && check.screenshots()
                            ? screenshots.resolve(language).resolve(window[0] + "x" + window[1] + "-scale" + requested + "-" + check.name() + ".png")
                            : null;
                    draw(screen, window[0], window[1], scale, file, check.mouse() == null ? null : check.mouse().apply(screen));
                    problems.addAll(LayoutCheck.end());
                    checkedScreens++;
                }
            }
        }
        LOGGER.info(PREFIX + "{} checked, {} problem(s) so far", language, problems.size());
    }

    /**
     * @param mouse where the mouse stands, in the coordinates of the screen, so that what it hovers over shows; {@code null} for
     *              nowhere
     */
    private void draw(Screen screen, int pixelWidth, int pixelHeight, int scale, Path file, int[] mouse) {
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
            screen.render(graphics, mouse == null ? -1000 : mouse[0], mouse == null ? -1000 : mouse[1], 0.0f);
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

    /** The style of the kingdom of most checks: the one with the longest name in Russian. */
    private static final String LONG_STYLE = "classic";
    /** What changing the style costs by default. */
    private static final long STYLE_FEE = 2_000L;

    private static KingdomView view(KingdomView.Relation relation, Role role) {
        return view(relation, role, LONG_STYLE);
    }

    /**
     * A kingdom with the longest names, many members and applications, building in the given architectural style.
     */
    private static KingdomView view(KingdomView.Relation relation, Role role, String style) {
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
                member ? defaultRights() : Map.of(), null, null, style, STYLE_FEE);
    }

    /**
     * The founding screen with the style of {@code shown} chosen where the land suggests {@code suggested}, a name and an answer
     * of the server.
     */
    private static FoundKingdomScreen founding(String suggested, String shown, String name, Component status, boolean error) {
        FoundKingdomScreen screen = new FoundKingdomScreen(BlockPos.ZERO, 0x1F618D, suggested);
        screen.showStyleForCheck(io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles.index(shown));
        screen.fillForCheck(name, status, error);
        return screen;
    }

    /**
     * The founding screen laid out with the style under the colors.
     */
    private static FoundKingdomScreen narrow(FoundKingdomScreen screen) {
        screen.narrowForCheck();
        return screen;
    }

    /**
     * The overview of a king with the choice of a new style open on {@code shown}.
     */
    private static ThroneScreen styleChoice(String shown, Component status) {
        ThroneScreen screen = throne(view(KingdomView.Relation.MEMBER, Role.KING), "overview", status);
        screen.showStyleForCheck(io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles.index(shown));
        return screen;
    }

    /**
     * The catalog of a kingdom in the style {@code style}, filtered by style: 0 all, 1 its own, 2 and on each style.
     */
    private static ThroneScreen catalogFiltered(String style, int filter) {
        ThroneScreen screen = throne(view(KingdomView.Relation.MEMBER, Role.KING, style), "construction", Component.empty());
        screen.updateConstruction(constructionView(screen.kingdomId(), true, false));
        screen.showConstruction("catalog", "", 0);
        screen.filterConstruction(filter);
        return screen;
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
            List<io.github.sekerator.kingdomworld.core.citizen.DeathCause> causes = deathCauses();
            for (int i = 0; i < causes.size(); i++) {
                Gender gender = i % 2 == 0 ? Gender.MALE : Gender.FEMALE;
                int seed = i < 2 ? longestNameSeed(gender) : i * 104_729;
                io.github.sekerator.kingdomworld.core.citizen.DeathCause cause = causes.get(i);
                deaths.add(new CitizenView.Death(seed, gender == Gender.FEMALE, i % 2 == 0 ? "unemployed" : "farmer", cause.cause(),
                        cause.killer(), 99_999L - i, far, cause.killerType(), cause.damage()));
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

    /**
     * Every cause of death the chronicle tells, with the creatures, kingdoms and kinds of damage that make it longest; a kind of
     * damage of the game without words of the chronicle, and one of a mod without a translation.
     */
    static List<io.github.sekerator.kingdomworld.core.citizen.DeathCause> deathCauses() {
        List<io.github.sekerator.kingdomworld.core.citizen.DeathCause> causes = new ArrayList<>();
        // The first lines are those a small window shows: a kind of damage, a creature with a name, the longest kingdom.
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.OTHER, "", "", "inWall"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.ANIMAL, "Рекс", "minecraft:wolf", "mob"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.SOLDIER, LONG_KINGDOM, "kingdomworld:citizen", "mob"));
        causes.add(io.github.sekerator.kingdomworld.core.citizen.DeathCause.of(CauseOfDeath.PLAYER, LONG_PLAYER));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.MONSTER, "", "minecraft:zombie", "mob"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.MONSTER, "", "minecraft:wither_skeleton", "mob"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.GOLEM, "", "minecraft:iron_golem", "mob"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.CREATURE, "", "minecraft:villager", "mob"));
        causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.CITIZEN, "Марка", "kingdomworld:citizen", "mob"));
        for (String damage : List.of("explosion", "cactus", "sweetBerryBush", "magic", "wither", "freeze", "lightningBolt",
                "fallingBlock", "fallingStalactite", "outOfWorld", "sting", "somemod.acid")) {
            causes.add(new io.github.sekerator.kingdomworld.core.citizen.DeathCause(CauseOfDeath.OTHER, "", "", damage));
        }
        for (CauseOfDeath cause : CauseOfDeath.values()) {
            causes.add(io.github.sekerator.kingdomworld.core.citizen.DeathCause.of(cause));
        }
        return causes.subList(0, Math.min(CitizenView.MAX_CHRONICLE, causes.size()));
    }

    private static ThroneScreen citizens(KingdomView view, CitizenView citizens, boolean chronicle, Component status) {
        ThroneScreen screen = throne(view, "citizens", status);
        screen.updateCitizens(new CitizenView(view.id(), citizens.canManage(), citizens.population(), citizens.bedsUsed(),
                citizens.bedsTotal(), citizens.foodDays(), citizens.mood(), citizens.storehouses(), citizens.growth(),
                citizens.citizens(), citizens.chronicle(), citizens.production(), citizens.decrees()));
        screen.showChronicle(chronicle);
        return screen;
    }

    /**
     * The same citizens with decrees of a ruler of the world in their chronicle, among the deaths.
     */
    private static CitizenView withDecrees(CitizenView view) {
        List<io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree> decrees = new ArrayList<>(longRuler(false).decrees());
        decrees.add(0, new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(99_999,
                new io.github.sekerator.kingdomworld.core.npc.RulerDecision(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.TOWN, "",
                        1, 0, "Златогорье-на-Дальнем-Берегу", cyrillic() ? "Златогорье-на-Дальнем-Берегу" : "Zlatogorye-na-Dalnem-Beregu")));
        decrees.add(new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(3,
                io.github.sekerator.kingdomworld.core.npc.RulerDecision.of(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.HIRE, "", 21)));
        return new CitizenView(view.kingdomId(), view.canManage(), view.population(), view.bedsUsed(), view.bedsTotal(), view.foodDays(),
                view.mood(), view.storehouses(), view.growth(), view.citizens(), view.chronicle(), view.production(), decrees);
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
                items.add(new StorageView.Entry(samples[i], 999_999_999L - i * 1_000L, i % 3 == 1 ? 999_999_999L - i * 1_000L : 0));
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
        List<WorkPostView.Station> stations = new ArrayList<>();
        ItemStack current = ItemStack.EMPTY;
        if (io.github.sekerator.kingdomworld.core.citizen.Professions.usesStations(profession)) {
            if (profession.equals("minter")) {
                // A minter works at mints only and strikes from ingots.
                stations.add(new WorkPostView.Station(io.github.sekerator.kingdomworld.core.production.Stations.MINT, 16));
                current = new ItemStack(Items.IRON_INGOT);
            } else if (profession.equals("merchant")) {
                stations.add(new WorkPostView.Station(io.github.sekerator.kingdomworld.core.production.Stations.MARKET_STALL, 16));
            } else {
                for (String kind : io.github.sekerator.kingdomworld.core.production.Stations.ALL) {
                    if (!kind.equals(io.github.sekerator.kingdomworld.core.production.Stations.MINT)
                            && !kind.equals(io.github.sekerator.kingdomworld.core.production.Stations.MARKET_STALL)) {
                        stations.add(new WorkPostView.Station(kind, 16));
                    }
                }
                current = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            }
        }
        return new WorkPostView(new BlockPos(-30_000_000, 319, 29_999_999), UUID.randomUUID(), canManage, profession, 16, 16,
                Math.max(1, workers), 16, profession.equals("herder") ? "minecraft:mooshroom" : "", animals, 64, 64, status, container,
                measured, measured ? 999_999 : 0, 9_999_999L, list, 9_999, !profession.equals("cook"), stations,
                profession.equals("builder") ? new ItemStack(Items.SPRUCE_STAIRS) : current, 45,
                profession.equals("builder") ? Component.translatable("kingdomworld.screen.post.site", Component.literal(LONG_KINGDOM),
                        "-30000000 319 29999999", 100) : Component.empty())
                .withGoal(profession.equals("miner") ? mineGoal("forge:ores/iron", MineGoal.MAX_AMOUNT, 23, true) : WorkPostView.Goal.NONE)
                .withMint(profession.equals("minter") ? new WorkPostView.Mint(canManage, mintMetals()) : WorkPostView.Mint.NONE);
    }

    /**
     * The window of a double storehouse, both halves partly filled, the last slot of each half too.
     */
    private static io.github.sekerator.kingdomworld.client.screen.StorehouseScreen storehouse(Component title) {
        return storehouse(title, 2, true, List.of());
    }

    /**
     * The window of a storehouse of one or two halves, partly filled, the last slot of each half too, with whom it takes in from.
     *
     * @param canEdit whether the viewer may change the setting (a button) or only reads it (a line)
     */
    private static io.github.sekerator.kingdomworld.client.screen.StorehouseScreen storehouse(Component title, int halves, boolean canEdit,
                                                                                         List<String> accepts) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.entity.player.Inventory inventory = minecraft.player != null ? minecraft.player.getInventory()
                : new net.minecraft.world.entity.player.Inventory(null);
        io.github.sekerator.kingdomworld.common.storage.StorehouseMenu menu = io.github.sekerator.kingdomworld.common.storage.StorehouseMenu.client(0,
                inventory, halves, new io.github.sekerator.kingdomworld.common.storage.StorehouseMenu.Settings(BlockPos.ZERO, canEdit, accepts));
        net.minecraft.world.item.Item[] items = {Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.BREAD, Items.OAK_LOG, Items.WHEAT};
        int slots = halves * io.github.sekerator.kingdomworld.common.storage.StorehouseInventory.SLOTS;
        for (int slot = 0; slot < slots; slot += 5) {
            menu.getSlot(slot).set(new ItemStack(items[(slot / 5) % items.length], 1 + slot % 64));
        }
        menu.getSlot(53).set(new ItemStack(Items.IRON_INGOT, 64));
        if (halves == 2) {
            menu.getSlot(107).set(new ItemStack(Items.GOLD_INGOT, 64));
        }
        return new io.github.sekerator.kingdomworld.client.screen.StorehouseScreen(menu, inventory, title);
    }

    /** Every profession a storehouse may be given, for the longest line of its window. */
    private static List<String> allStoreProfessions() {
        return io.github.sekerator.kingdomworld.client.screen.StorehouseAcceptsScreen.choices(List.of());
    }

    /**
     * A minter's post whose viewer has one right and not the other: the citizens without the treasury, or the treasury without
     * the citizens; or a mint without metals in the config.
     */
    private static WorkPostView minterView(WorkStatus status, boolean citizens, boolean treasury, boolean metals) {
        WorkPostView view = postView("minter", status, citizens, 2, false, true);
        return view.withMint(new WorkPostView.Mint(treasury, metals ? mintMetals() : List.of()));
    }

    /**
     * The goal of a mine in a world as tall as worlds get, among ores of other mods with long names.
     */
    private static WorkPostView.Goal mineGoal(String ore, int amount, long stock, boolean autoDepth) {
        return new WorkPostView.Goal(List.of("forge:ores/coal", "forge:ores/copper", "forge:ores/diamond", "forge:ores/iron",
                "forge:ores/netherite_scrap", "examplemod:ores/very_long_named_ore_of_the_northern_mountains"), ore, amount, stock,
                autoDepth, autoDepth ? 16 : -2_026, -2_026, 2_031);
    }

    private static WorkPostScreen post(WorkPostView view, Component status) {
        return post(view, status, false);
    }

    /**
     * A post screen with a message from the server at the bottom: an error, or the report of a change that worked.
     */
    private static WorkPostScreen post(WorkPostView view, Component status, boolean success) {
        WorkPostScreen screen = new WorkPostScreen(view);
        if (!status.getString().isEmpty()) {
            screen.onResult(success, status);
        }
        return screen;
    }

    private static WorkPostView withoutStations(WorkPostView view) {
        return new WorkPostView(view.pos(), view.kingdomId(), view.canManage(), view.profession(), view.radius(), view.maxRadius(), view.seats(),
                view.maxSeats(), view.animal(), view.animals(), view.herdSize(), view.maxHerd(), view.status(), view.container(), view.measured(),
                0, view.producedToday(), view.workers(), view.unemployed(), view.auto(), List.of(), ItemStack.EMPTY, 0, Component.empty());
    }

    private static ItemStack longNamed(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal("Меч Великого Северного Королевства Щит и Меч"));
        return stack;
    }

    /**
     * Orders in every state, with sub-orders, long names and long lists of what is missing.
     */
    private static OrdersView ordersView(UUID kingdom, boolean canManage, boolean empty) {
        List<OrdersView.Entry> entries = new ArrayList<>();
        if (!empty) {
            List<OrdersView.Missing> missing = List.of(new OrdersView.Missing(new ItemStack(Items.RAW_IRON), 99_999, "miner"),
                    new OrdersView.Missing(new ItemStack(Items.OAK_LOG), 99_999, "lumberjack"),
                    new OrdersView.Missing(longNamed(Items.DIAMOND), 99_999, ""));
            Order.Status[] statuses = Order.Status.values();
            for (int i = 0; i < statuses.length; i++) {
                Order.Status status = statuses[i];
                ItemStack sample = i == 0 ? longNamed(Items.IRON_SWORD) : new ItemStack(i % 2 == 0 ? Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                        : Items.LIGHT_BLUE_GLAZED_TERRACOTTA);
                boolean lacks = status == Order.Status.MISSING || status == Order.Status.WAITING_FOR_PARTS;
                entries.add(new OrdersView.Entry(100 + i, 0, sample, i % 2 == 0 ? Order.Kind.MAKE : Order.Kind.KEEP, 100_000, 99_999L,
                        Order.Priority.values()[i % 3], status == Order.Status.PAUSED, status, lacks ? missing : List.of(),
                        status == Order.Status.NO_WORKSHOP ? "smith" : "", status == Order.Status.NO_WORKSHOP ? "smithing_table" : ""));
                if (i == 1) {
                    entries.add(new OrdersView.Entry(200, 1, new ItemStack(Items.IRON_INGOT), Order.Kind.KEEP, 100_000, 99_999L,
                            Order.Priority.HIGH, false, Order.Status.WAITING_FOR_PARTS, missing, "", ""));
                    entries.add(new OrdersView.Entry(201, 2, longNamed(Items.RAW_IRON), Order.Kind.KEEP, 100_000, 99_999L,
                            Order.Priority.HIGH, false, Order.Status.MISSING, missing, "", ""));
                }
            }
        }
        return new OrdersView(kingdom, canManage, 256, entries);
    }

    private static CatalogView catalogView(UUID kingdom, boolean empty) {
        List<CatalogView.Entry> entries = new ArrayList<>();
        if (!empty) {
            String[] professions = {"smith", "cook", "crafter", "smelter", "crafter"};
            ItemStack[] samples = {longNamed(Items.NETHERITE_SWORD), new ItemStack(Items.BREAD), new ItemStack(Items.LIGHT_BLUE_GLAZED_TERRACOTTA),
                    new ItemStack(Items.IRON_INGOT), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.STICK),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.CHAINMAIL_CHESTPLATE), new ItemStack(Items.TORCH),
                    new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.OAK_PLANKS)};
            for (int i = 0; i < samples.length; i++) {
                entries.add(new CatalogView.Entry(samples[i], professions[i % professions.length]));
            }
        }
        return new CatalogView(kingdom, "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", 9_998, entries.size(),
                empty ? 0 : 9_999 * Math.max(1, entries.size()), entries);
    }

    private static ThroneScreen orders(KingdomView view, boolean canManage, boolean empty, String mode, Component status) {
        ThroneScreen screen = throne(view, "orders", status);
        screen.updateOrders(ordersView(view.id(), canManage, empty));
        if (!mode.equals("list")) {
            screen.showCatalog(catalogView(view.id(), empty));
            screen.showOrders(mode, "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", empty ? null : longNamed(Items.NETHERITE_SWORD),
                    empty ? null : "smith");
        }
        return screen;
    }

    private static final long MUCH = 999_999_999_999L;
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";

    /**
     * A treasury with the largest numbers, long names, every category of the journal, every wage and many debtors.
     */
    private static io.github.sekerator.kingdomworld.common.network.data.TreasuryView treasuryView(UUID kingdom, boolean canManage, boolean king,
                                                                                                     boolean empty) {
        return treasuryView(kingdom, canManage, canManage && king, king, empty);
    }

    private static io.github.sekerator.kingdomworld.common.network.data.TreasuryView treasuryView(UUID kingdom, boolean canManage,
                                                                                                     boolean canTakeCoins, boolean king,
                                                                                                     boolean empty) {
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.JournalRow> journal = new ArrayList<>();
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Wage> wages = new ArrayList<>();
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Debtor> debtors = new ArrayList<>();
        if (!empty) {
            for (io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period period
                    : io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.values()) {
                for (io.github.sekerator.kingdomworld.core.economy.LedgerCategory category
                        : io.github.sekerator.kingdomworld.core.economy.LedgerCategory.values()) {
                    journal.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.JournalRow(period, category,
                            category.ordinal() % 2 == 0 ? MUCH : 0, category.ordinal() != 0 ? MUCH - 1 : 0));
                }
            }
            for (int i = 0; i < 30; i++) {
                debtors.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Debtor(i == 0 ? LONG_PLAYER : "Debtor_" + i,
                        MUCH - i));
            }
        }
        int index = 0;
        for (io.github.sekerator.kingdomworld.core.citizen.Profession profession
                : io.github.sekerator.kingdomworld.core.citizen.Professions.REGISTRY.all()) {
            long usual = 6;
            long wage = index % 3 == 0 ? usual : index % 3 == 1 ? 199 : 0;
            wages.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Wage(profession.id(), wage, usual, empty ? 0 : 9_999));
            index++;
        }
        return new io.github.sekerator.kingdomworld.common.network.data.TreasuryView(kingdom, canManage, canTakeCoins, king, LONG_KINGDOM,
                empty ? "" : LONG_CURRENCY, 10, 100, empty ? 0 : MUCH, empty ? io.github.sekerator.kingdomworld.core.economy.Coins.NONE
                : new io.github.sekerator.kingdomworld.core.economy.Coins(9_999, 9_999, 999_999), journal, 1_000, 1_000, 9_999, 23_999,
                new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Payroll(empty ? -1 : 5, MUCH, MUCH / 3, 9_999, 9_999),
                wages, 200, debtors, empty ? 0 : 9_999, empty ? 0 : MUCH,
                new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Mint(empty ? 0 : 99, 999, WorkStatus.NO_INGREDIENTS, MUCH,
                        MUCH, true, empty ? List.of() : mintMetals()), 9_990_000L, reserves(empty));
    }

    /**
     * The metals of a mint: those of the game, one of them off and kept back, and metals of other mods with long names, more than
     * fit without scrolling.
     */
    private static List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal> mintMetals() {
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal> metals = new ArrayList<>();
        int max = io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP;
        metals.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal("#forge:ingots/copper",
                new ItemStack(Items.COPPER_INGOT), io.github.sekerator.kingdomworld.core.economy.Denomination.COPPER, 5, true, 0, 9_999_999L));
        metals.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal("#forge:ingots/iron",
                new ItemStack(Items.IRON_INGOT), io.github.sekerator.kingdomworld.core.economy.Denomination.SILVER, 2, false, max, 9_999_999L));
        metals.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal("#forge:ingots/silver",
                longNamed(Items.IRON_INGOT), io.github.sekerator.kingdomworld.core.economy.Denomination.SILVER, 3, true, max - 1, 12L));
        metals.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal("#forge:ingots/gold",
                new ItemStack(Items.GOLD_INGOT), io.github.sekerator.kingdomworld.core.economy.Denomination.GOLD, 1, true, 64, 0L));
        metals.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal("examplemod:very_long_named_ingot",
                longNamed(Items.NETHERITE_INGOT), io.github.sekerator.kingdomworld.core.economy.Denomination.GOLD, 4_096, false, 0, 9_999_999L));
        return metals;
    }

    private static List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Reserve> reserves(boolean empty) {
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Reserve> reserves = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 12; i++) {
                reserves.add(new io.github.sekerator.kingdomworld.common.network.data.TreasuryView.Reserve(i % 2 == 0 ? LONG_CURRENCY : "",
                        i == 1 ? LONG_KINGDOM : "Valoria " + i, 0x1F618D + i * 4000, MUCH - i, 10, 100, MUCH / 3));
            }
        }
        return reserves;
    }

    private static ThroneScreen treasury(KingdomView view, boolean canManage, boolean empty, String mode, String period, Component status) {
        ThroneScreen screen = throne(view, "treasury", status);
        screen.updateTreasury(treasuryView(view.id(), canManage, view.viewerRole() == Role.KING, empty));
        screen.showTreasury(mode, period);
        return screen;
    }

    private static ThroneScreen payments(KingdomView view, boolean canManage, boolean canTakeCoins, boolean filled, Component status) {
        ThroneScreen screen = throne(view, "treasury", status);
        screen.updateTreasury(treasuryView(view.id(), canManage, canTakeCoins, view.viewerRole() == Role.KING, false));
        screen.showTreasury("payments", "today");
        if (filled) {
            screen.fillPaymentsForCheck(LONG_PLAYER, "999999g99s9c", "9999999999");
        }
        return screen;
    }

    /**
     * Coins of a current currency with a long name, of one named after its kingdom, of a dissolved kingdom, and a blank
     * coin.
     */
    private static ItemTooltipScreen coinTooltips() {
        fillMapData();
        UUID first = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));
        UUID old = UUID.nameUUIDFromBytes("old".getBytes(StandardCharsets.UTF_8));
        return new ItemTooltipScreen("coins", List.of(
                io.github.sekerator.kingdomworld.common.economy.CoinItem.stack(first, io.github.sekerator.kingdomworld.core.economy.Denomination.GOLD, 64),
                io.github.sekerator.kingdomworld.common.economy.CoinItem.stack(second, io.github.sekerator.kingdomworld.core.economy.Denomination.SILVER, 1),
                io.github.sekerator.kingdomworld.common.economy.CoinItem.stack(old, io.github.sekerator.kingdomworld.core.economy.Denomination.COPPER, 7),
                new ItemStack(io.github.sekerator.kingdomworld.common.registry.ModRegistries.COIN.get())));
    }

    /**
     * A bank with long names, coins and debts with the largest numbers and more accounts than fit.
     */
    private static io.github.sekerator.kingdomworld.common.network.data.BankView bankView(boolean empty) {
        UUID currency = UUID.nameUUIDFromBytes("bank".getBytes(StandardCharsets.UTF_8));
        List<io.github.sekerator.kingdomworld.common.network.data.BankView.Account> accounts = new ArrayList<>();
        accounts.add(new io.github.sekerator.kingdomworld.common.network.data.BankView.Account(currency, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483,
                false, empty ? 0 : MUCH, 10, 100));
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                accounts.add(new io.github.sekerator.kingdomworld.common.network.data.BankView.Account(
                        UUID.nameUUIDFromBytes(("account" + i).getBytes(StandardCharsets.UTF_8)), i % 2 == 0 ? "" : LONG_CURRENCY,
                        i == 0 ? LONG_KINGDOM : "Valoria " + i, 0x1F618D + i * 1000, i % 3 == 2, MUCH - i, 10, 100));
            }
        }
        return new io.github.sekerator.kingdomworld.common.network.data.BankView(new BlockPos(-30_000_000, 319, 29_999_999), currency,
                LONG_KINGDOM, 0x6C3483, empty ? "" : LONG_CURRENCY, 10, 100,
                empty ? io.github.sekerator.kingdomworld.core.economy.Coins.NONE
                        : new io.github.sekerator.kingdomworld.core.economy.Coins(2_304, 2_304, 2_304), empty ? 0 : MUCH, accounts);
    }

    private static io.github.sekerator.kingdomworld.client.screen.BankScreen bank(boolean empty, String amount, Component status, boolean success) {
        io.github.sekerator.kingdomworld.client.screen.BankScreen screen = new io.github.sekerator.kingdomworld.client.screen.BankScreen(bankView(empty));
        screen.fillForCheck(amount, amount.isEmpty() ? 0 : 2);
        if (!status.getString().isEmpty()) {
            screen.onResult(success, status);
        }
        return screen;
    }

    // ---------------------------------------------------------------- trade

    private static final UUID VALORIA = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));

    private static TradeViews.Currency ownCurrency(UUID kingdom) {
        return new TradeViews.Currency(kingdom, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483, 10, 100, 9_990_000L);
    }

    private static TradeViews.Currency valoriaCurrency() {
        return new TradeViews.Currency(VALORIA, "", "Valoria", 0xB7950B, 10, 100, 1_250_000L);
    }

    private static final net.minecraft.world.item.Item[] TRADE_ITEMS = {Items.BREAD, Items.IRON_INGOT, Items.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Items.ENCHANTED_GOLDEN_APPLE, Items.OAK_LOG, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.WHEAT, Items.COBBLESTONE,
            Items.CHAINMAIL_CHESTPLATE, Items.TORCH, Items.COOKED_BEEF, Items.DIAMOND};

    private static ItemStack tradeItem(int i) {
        return i == 0 ? longNamed(Items.NETHERITE_SWORD) : new ItemStack(TRADE_ITEMS[i % TRADE_ITEMS.length], i % 4 == 1 ? 64 : 1);
    }

    /**
     * A market with the largest numbers, long names and more rows than fit in every list.
     */
    private static TradeViews.MarketView marketView(UUID kingdom, boolean canManage, boolean empty) {
        List<TradeViews.SellRow> sells = new ArrayList<>();
        List<TradeViews.BuyRow> buys = new ArrayList<>();
        List<TradeViews.LotRow> lots = new ArrayList<>();
        List<TradeViews.ExportRow> exports = new ArrayList<>();
        List<TradeViews.ImportRow> imports = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                sells.add(new TradeViews.SellRow(tradeItem(i), i % 2 == 0 ? MUCH : 0, MUCH, 999_999, 999_999_999L));
                buys.add(new TradeViews.BuyRow(tradeItem(i), i % 2 == 0 ? 0 : MUCH, MUCH, 999_999, i % 3 == 0 ? 0 : MUCH, MUCH - 1, 999_999_999L));
                lots.add(new TradeViews.LotRow(i, tradeItem(i), MUCH, i == 0 ? LONG_PLAYER : "Seller_" + i, i % 3 == 0));
                exports.add(new TradeViews.ExportRow(tradeItem(i), 999_999, 999_999_999L, MUCH));
                imports.add(new TradeViews.ImportRow(tradeItem(i), 999_999, MUCH, i % 2 == 0 ? 23_999L * 30 : 0));
            }
        }
        TradeViews.Day day = new TradeViews.Day(MUCH, MUCH, MUCH, 999, 999_999, 999_999, MUCH, MUCH, 999_999, 999_999, MUCH);
        return new TradeViews.MarketView(kingdom, canManage, ownCurrency(kingdom), sells, buys, lots, exports, imports, false, true, 250, 250,
                100, 900, day, List.of("minecraft:iron_ingot", "minecraft:oak_log", "minecraft:cobblestone", "minecraft:wheat"), 256);
    }

    private static ThroneScreen market(KingdomView view, boolean canManage, boolean empty, String mode, boolean form, String search,
                                       Component status) {
        ThroneScreen screen = throne(view, "market", status);
        screen.updateMarket(marketView(view.id(), canManage, empty));
        screen.showMarket(mode, form, form ? longNamed(Items.NETHERITE_SWORD) : ItemStack.EMPTY, "999999g99s9c", "999999", "999999g", search);
        return screen;
    }

    /**
     * Deals in every state, both directions and both currencies, and caravans on the way and waiting.
     */
    private static TradeViews.TradeView tradeView(UUID kingdom, boolean canManage, boolean empty) {
        List<TradeViews.Partner> partners = new ArrayList<>();
        List<TradeViews.DealRow> deals = new ArrayList<>();
        List<TradeViews.CaravanRow> caravans = new ArrayList<>();
        if (!empty) {
            UUID far = UUID.nameUUIDFromBytes("far".getBytes(StandardCharsets.UTF_8));
            partners.add(new TradeViews.Partner(far, LONG_KINGDOM, 0x6C3483, new TradeViews.Currency(far, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483,
                    10, 100, 9_990_000L)));
            partners.add(new TradeViews.Partner(VALORIA, "Valoria", 0xB7950B, valoriaCurrency()));
            int i = 0;
            for (Deal.Status status : Deal.Status.values()) {
                for (boolean incoming : new boolean[]{true, false}) {
                    boolean proposed = status == Deal.Status.PROPOSED;
                    deals.add(new TradeViews.DealRow(i, i % 2 == 0 ? far : VALORIA, i % 2 == 0 ? LONG_KINGDOM : "Valoria", 0x6C3483, i % 2 == 0,
                            tradeItem(i), 4_096, MUCH, i % 3 == 0, i % 3, status, proposed && incoming, !status.closed() && !incoming,
                            23_999L * 30, 999));
                    i++;
                }
            }
            for (int c = 0; c < 12; c++) {
                caravans.add(new TradeViews.CaravanRow(c, c % 2 == 0, c % 2 == 0 ? LONG_KINGDOM : "Valoria", 0xB7950B, tradeItem(c), 999_999,
                        MUCH, c % 3 == 0, 99, 23_999L * 30, c % 4 == 3 ? Caravan.State.WAITING : Caravan.State.TRAVELING));
            }
        }
        return new TradeViews.TradeView(kingdom, canManage, ownCurrency(kingdom), partners, deals, caravans, 4_096, 30);
    }

    private static ThroneScreen trade(KingdomView view, boolean canManage, boolean empty, String mode, String search, Component status) {
        ThroneScreen screen = throne(view, "trade", status);
        screen.updateTrade(tradeView(view.id(), canManage, empty));
        screen.showTrade(mode, mode.equals("propose") ? longNamed(Items.NETHERITE_SWORD) : ItemStack.EMPTY, "4096", "999999g99s9c", "30", search);
        return screen;
    }

    private static TradeViews.StallView stallView(boolean empty, boolean member) {
        UUID kingdom = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
        List<TradeViews.Offer> offers = new ArrayList<>();
        List<TradeViews.Wanted> wanted = new ArrayList<>();
        List<TradeViews.LotRow> lots = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                offers.add(new TradeViews.Offer(tradeItem(i), MUCH, i % 5 == 4 ? 0 : 999_999_999L));
                wanted.add(new TradeViews.Wanted(tradeItem(i), i % 6 == 5 ? 0 : MUCH, 999_999, 2_304));
                lots.add(new TradeViews.LotRow(i, tradeItem(i), MUCH, i == 1 ? LONG_PLAYER : "Seller_" + i, i % 3 == 0));
            }
        }
        List<TradeViews.Purse> purses = List.of(
                new TradeViews.Purse(ownCurrency(kingdom), empty ? 0 : MUCH, empty ? io.github.sekerator.kingdomworld.core.economy.Coins.NONE
                        : new io.github.sekerator.kingdomworld.core.economy.Coins(2_304, 2_304, 2_304)),
                new TradeViews.Purse(valoriaCurrency(), MUCH, new io.github.sekerator.kingdomworld.core.economy.Coins(2_304, 2_304, 2_304)));
        return new TradeViews.StallView(new BlockPos(-30_000_000, 319, 29_999_999), kingdom, LONG_KINGDOM, 0x6C3483, ownCurrency(kingdom), member,
                250, 256, 100, offers, wanted, lots, empty ? purses.subList(0, 1) : purses);
    }

    private static StallScreen stall(boolean empty, boolean member, String mode, boolean account, int purse, Component status) {
        StallScreen screen = new StallScreen(stallView(empty, member));
        screen.showForCheck(mode, account, purse, "4096", "999999g99s9c");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static ExchangeScreen exchange(boolean empty, String amount, boolean fromAccount, boolean toAccount, Component status, boolean success) {
        UUID kingdom = UUID.nameUUIDFromBytes("bank".getBytes(StandardCharsets.UTF_8));
        List<TradeViews.Purse> sources = new ArrayList<>();
        if (!empty) {
            sources.add(new TradeViews.Purse(new TradeViews.Currency(UUID.nameUUIDFromBytes("far".getBytes(StandardCharsets.UTF_8)), LONG_CURRENCY,
                    LONG_KINGDOM, 0x6C3483, 10, 100, 9_990_000L), MUCH, new io.github.sekerator.kingdomworld.core.economy.Coins(2_304, 2_304, 2_304)));
            sources.add(new TradeViews.Purse(valoriaCurrency(), MUCH, io.github.sekerator.kingdomworld.core.economy.Coins.NONE));
        }
        ExchangeScreen screen = new ExchangeScreen(new TradeViews.ExchangeView(new BlockPos(-30_000_000, 319, 29_999_999), kingdom, LONG_KINGDOM,
                0x6C3483, ownCurrency(kingdom), 900, MUCH, MUCH, sources));
        screen.fillForCheck(amount, 0, fromAccount, toAccount);
        if (!status.getString().isEmpty()) {
            screen.onResult(success, status);
        }
        return screen;
    }

    // ---------------------------------------------------------------- construction

    /**
     * A catalog with long names and long costs, one template that cannot be built; constructions in every state with long
     * lists of what they lack; many buildings.
     */
    private static io.github.sekerator.kingdomworld.common.network.data.ConstructionView constructionView(UUID kingdom, boolean canManage,
                                                                                                         boolean empty) {
        List<io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Template> templates = new ArrayList<>();
        List<io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Site> sites = new ArrayList<>();
        List<io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Building> buildings = new ArrayList<>();
        String[] categories = {"housing", "farming", "production", "trade", "defense", "decoration"};
        String[] names = {"small_house", "large_house", "storehouse", "workshop", "market_square", "watchtower", "wall_gate", "well"};
        if (!empty) {
            List<io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Cost> cost = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                cost.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Cost(tradeItem(i), 99_999 - i));
            }
            for (int i = 0; i < 12; i++) {
                Component name = i == 0 ? Component.literal(LONG_KINGDOM + " " + LONG_KINGDOM)
                        : Component.translatable("kingdomworld.building." + names[i % names.length]);
                Component problem = i == 1 ? Component.translatable("kingdomworld.template.problem.missing_mod",
                        "create, immersiveengineering, mekanism, thermal_expansion") : Component.empty();
                templates.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Template("kingdomworld:" + names[i % names.length]
                        + i, name, categories[i % categories.length], new ItemStack(i % 2 == 0 ? Items.OAK_DOOR : Items.STONE_BRICKS), 48, 32, 48, cost,
                        73_728, i == 2 ? 9 : 0, problem, i % 3 == 0 ? Component.translatable("kingdomworld.building.role.warehouse") : Component.empty(),
                        16, i % 2 == 0 ? Component.literal(LONG_KINGDOM) : Component.empty(), 0));
            }
            // Templates of the architectural styles, which the catalog puts in groups, marks and filters by style.
            String[] styled = {"terem_castle_small", "classic_house_manor", "mountain_house_noble", "terem_house_boyar", "classic_castle_large",
                    "nordic_house_longhouse", "gothic_house_turret", "classic_smithy", "terem_tavern"};
            for (int i = 0; i < styled.length; i++) {
                boolean castle = styled[i].contains("_castle_");
                templates.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Template("kingdomworld:" + styled[i],
                        Component.translatable("kingdomworld.building." + styled[i]), castle ? "defense" : "housing",
                        new ItemStack(castle ? Items.STONE_BRICKS : Items.OAK_DOOR), castle ? 47 : 13, castle ? 48 : 12, castle ? 47 : 15, cost,
                        73_728, 0, Component.empty(), castle ? Component.empty() : Component.translatable("kingdomworld.building.role.house"),
                        1, Component.empty(), 0));
            }
            // The kingdom's own blueprints with long names, one that cannot be built any more.
            for (int i = 0; i < 9; i++) {
                templates.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Template("kingdomworld:custom/" + (i + 1),
                        Component.literal(i == 0 ? "Щ".repeat(32) : "Мой дом " + i), categories[i % categories.length], new ItemStack(Items.PAPER),
                        48, 32, 48, cost, 73_728, 0, i == 1 ? Component.translatable("kingdomworld.template.problem.missing_block", "create:shaft")
                        : Component.empty(), Component.translatable("kingdomworld.building.role.house"), 1, Component.empty(), i + 1));
            }
            List<io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Missing> missing = new ArrayList<>();
            String[] gatherers = {"miner", "", "lumberjack", ""};
            for (int i = 0; i < 24; i++) {
                missing.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Missing(i == 0 ? longNamed(Items.SPRUCE_PLANKS)
                        : tradeItem(i), 99_999, gatherers[i % gatherers.length], i % 3 != 0, i % 4 == 1));
            }
            io.github.sekerator.kingdomworld.core.construction.Construction.Status[] statuses =
                    io.github.sekerator.kingdomworld.core.construction.Construction.Status.values();
            BlockPos far = new BlockPos(-30_000_000, 319, 29_999_999);
            for (int i = 0; i < statuses.length; i++) {
                io.github.sekerator.kingdomworld.core.construction.Construction.Status status = statuses[i];
                boolean at = status == io.github.sekerator.kingdomworld.core.construction.Construction.Status.BLOCKED
                        || status == io.github.sekerator.kingdomworld.core.construction.Construction.Status.UNREACHABLE;
                sites.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Site(100 + i, i == 0
                        ? Component.literal(LONG_KINGDOM + " " + LONG_KINGDOM) : Component.translatable("kingdomworld.building." + names[i % names.length]),
                        new ItemStack(Items.OAK_DOOR), far, io.github.sekerator.kingdomworld.core.construction.Construction.Priority.values()[i % 3],
                        i % 4 == 1, i % 2 == 0, status, at ? far : null, 100 - i, 73_728 - i, 73_728, 16, i == 1 ? List.of() : missing,
                        io.github.sekerator.kingdomworld.core.construction.Construction.Kind.values()[i % 4]));
            }
            io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status[] roleStatuses =
                    io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status.values();
            String[] roles = {"house", "warehouse", "workshop", "market", "tower", "wall", "well"};
            for (int i = 0; i < 30; i++) {
                io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status roleStatus = roleStatuses[i % roleStatuses.length];
                Component reason = roleStatus == io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status.DAMAGED
                        ? Component.translatable("kingdomworld.construction.role_status.damaged", 9, 70)
                        : roleStatus == io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status.LACKING
                        ? Component.translatable("kingdomworld.construction.role_status.lacking.container")
                        : Component.translatable("kingdomworld.construction.role_status." + roleStatus.id());
                List<Component> effects = List.of(Component.translatable("kingdomworld.construction.effect.beds", 99, 99),
                        Component.translatable("kingdomworld.construction.effect.comfort", 99),
                        Component.translatable("kingdomworld.construction.effect.well", 99, Component.translatable("kingdomworld.count.blocks.n5", 99_999)),
                        Component.translatable("kingdomworld.construction.effect.merchants", 16),
                        Component.translatable("kingdomworld.construction.effect.lots", 64),
                        Component.translatable("kingdomworld.construction.effect.sight", Component.translatable("kingdomworld.count.blocks.n5", 99_999)));
                Component materials = i % 2 == 0 ? Component.translatable("kingdomworld.construction.materials.both", longNamed(Items.DARK_OAK_PLANKS)
                        .getHoverName(), longNamed(Items.DEEPSLATE_BRICKS).getHoverName()) : Component.empty();
                buildings.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Building(i + 1, i == 0
                        ? Component.literal(LONG_KINGDOM + " " + LONG_KINGDOM) : Component.translatable("kingdomworld.building." + names[i % names.length]),
                        "house", categories[i % categories.length], far, i % 5 == 4 ? -1 : 100 - i * 3, 99_999, i % 7 == 3,
                        Component.translatable("kingdomworld.building.role." + roles[i % roles.length]), roleStatus, reason, 1 + i % 16,
                        i % 3 == 0 ? Component.literal(LONG_KINGDOM) : Component.empty(), effects, materials, 73_728 - i, i % 2 == 1,
                        i == 2 ? io.github.sekerator.kingdomworld.core.construction.Construction.Kind.REPAIR : null, cost, cost, cost));
            }
            // A city wall, kept by the roads tab: its works are off with a hint.
            buildings.add(new io.github.sekerator.kingdomworld.common.network.data.ConstructionView.Building(CITY_WALL,
                    Component.translatable("kingdomworld.building.city_wall"), "fortification",
                    io.github.sekerator.kingdomworld.common.road.CityWalls.CATEGORY, far, 87, 99_999, false,
                    Component.translatable("kingdomworld.building.role.wall"), io.github.sekerator.kingdomworld.core.construction.BuildingRoles.Status.ACTIVE,
                    Component.translatable("kingdomworld.construction.role_status.active"), 1, Component.empty(),
                    List.of(Component.translatable("kingdomworld.screen.construction.work_hint.by_roads")), Component.empty(), 99_999,
                    false, null, List.of(), List.of(), List.of()));
        }
        return new io.github.sekerator.kingdomworld.common.network.data.ConstructionView(kingdom, canManage, 64, templates, sites, buildings, 256, 9_999_999L);
    }

    /** The number of the city wall among the buildings of the layout check. */
    private static final long CITY_WALL = 9_999;

    private static ThroneScreen constructionBuilding(KingdomView view, boolean canManage, long building, String work, Component status) {
        ThroneScreen screen = throne(view, "construction", status);
        screen.updateConstruction(constructionView(view.id(), canManage, false));
        screen.showConstructionBuilding(building, work, 0, 0);
        return screen;
    }

    private static ThroneScreen constructionTemplate(KingdomView view, String mode, String template, int wood, int stone) {
        ThroneScreen screen = construction(view, true, false, mode, template, 0, Component.empty());
        screen.chooseConstructionMaterials(wood, stone);
        return screen;
    }

    private static io.github.sekerator.kingdomworld.client.screen.CaptureScreen capture(String name, int role, Component message, boolean error) {
        io.github.sekerator.kingdomworld.client.screen.CaptureScreen screen = new io.github.sekerator.kingdomworld.client.screen.CaptureScreen(
                new io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem.Corners(new BlockPos(-30_000_000, -64, -30_000_000),
                        new BlockPos(-29_999_745, 191, -29_999_745), "minecraft:overworld"));
        screen.fillForCheck(name, role, message, error);
        return screen;
    }

    private static ThroneScreen construction(KingdomView view, boolean canManage, boolean empty, String mode, String template, long site,
                                             Component status) {
        ThroneScreen screen = throne(view, "construction", status);
        screen.updateConstruction(constructionView(view.id(), canManage, empty));
        screen.showConstruction(mode, template, site);
        return screen;
    }

    private static io.github.sekerator.kingdomworld.client.screen.BlueprintHintScreen blueprintHint(boolean refused, int obstacles) {
        Component status = refused ? KingdomActions.errorMessage(KingdomError.CONSTRUCTION_OUTSIDE_LAND)
                : Component.translatable("kingdomworld.construction.place_ok");
        return new io.github.sekerator.kingdomworld.client.screen.BlueprintHintScreen(refused ? "refused" : "ok",
                io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer.lines(Component.literal(LONG_KINGDOM),
                        Component.literal("Right Control"), Component.literal("Right Control"), Component.literal("Right Button"), status,
                        refused ? 0xFFFF8080 : 0xFFA0E0A0, obstacles));
    }

    /**
     * Squads of the kingdom on the map, a squad of an enemy in sight, the pale mark of one seen last and a battle.
     */
    private static void fillArmyMap() {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        String dimension = level != null ? level.dimension().location().toString() : "minecraft:overworld";
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View(
                true, dimension, 0x6C3483,
                List.of(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own(3, false, "move", 8, 8, 12, 1, 95, "wedge",
                                new int[]{40, -60, 90, -40, 130, 0}),
                        new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own(4, true, "hold", 40, 40, 6, 0, 0, "line", new int[0])),
                List.of(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other(9, LONG_KINGDOM, 0xB7950B, 120, 10, 20, false, 0)),
                List.of(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other(11, "Valoria", 0xB7950B, 160, -70, 8, false, 640)),
                new int[]{110, 20});
        io.github.sekerator.kingdomworld.client.map.ClientArmyMap.update(view);
    }

    private static void fillMapData() {
        fillMapData(LONG_NPC);
    }

    /**
     * The land of the atlas with a kingdom of players and the kingdom of the world {@code npc}.
     */
    private static void fillMapData(io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc) {
        UUID first = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));
        List<KingdomMapInfo> palette = List.of(
                new KingdomMapInfo(first, LONG_KINGDOM, 0x6C3483, LONG_PLAYER, 8, 20, 187, true, 0, 0),
                new KingdomMapInfo(second, "Valoria", 0xB7950B, "", 1, 9, 0, true, 7, 1, npc));
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
        DiplomacyLayoutCases.fillMarks(first, second);
        io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.replace(List.of(
                new io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo(first, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483, false, 10, 100,
                        9_990_000L),
                new io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo(second, "", "Valoria", 0xB7950B, false, 10, 100, 1_250_000L),
                new io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo(UUID.nameUUIDFromBytes("old".getBytes(StandardCharsets.UTF_8)),
                        "", LONG_KINGDOM, 0x1F618D, true, 10, 100, 0)));
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
        // The choice of the style: the one the land suggests with its mark, others without, the longest name and caption.
        cases.add(new Case("founding-suggested", () -> founding("classic", "classic", LONG_KINGDOM,
                Component.translatable("kingdomworld.screen.found.waiting"), false)));
        cases.add(new Case("founding-style-terem", () -> founding("classic", "terem", "Abc!", Component.empty(), false)));
        cases.add(new Case("founding-style-mountain", () -> founding("mountain", "mountain", LONG_KINGDOM,
                Component.translatable("kingdomworld.kingdom.error.read_only"), true)));
        // The same with the style under the colors, as a screen too narrow for them side by side has it.
        cases.add(new Case("founding-narrow-suggested", () -> narrow(founding("classic", "classic", LONG_KINGDOM,
                Component.translatable("kingdomworld.screen.found.waiting"), false))));
        cases.add(new Case("founding-narrow-style", () -> narrow(founding("classic", "terem", "Abc!", Component.empty(), false))));
        cases.add(new Case("founding-narrow-error", () -> narrow(founding("mountain", "mountain", LONG_KINGDOM,
                Component.translatable("kingdomworld.kingdom.error.read_only"), true))));
        cases.add(new Case("throne-king-overview", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "overview", longError)));
        cases.add(new Case("throne-king-overview-style", () -> styleChoice("mountain", Component.empty())));
        cases.add(new Case("throne-king-overview-style-error", () -> styleChoice(LONG_STYLE, longError)));
        // The answer of the server to a change of the style, a success.
        cases.add(new Case("throne-king-overview-style-changed", () -> {
            ThroneScreen screen = throne(view(KingdomView.Relation.MEMBER, Role.KING), "overview", Component.empty());
            screen.onResult(true, Component.translatable("kingdomworld.style.changed",
                    Component.translatable("kingdomworld.style." + LONG_STYLE),
                    io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(STYLE_FEE,
                            io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)));
            return screen;
        }));
        // The throne being moved: the line of the throne says so and its button calls the move off; the answers of the server.
        cases.add(new Case("throne-king-overview-moving", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING).withThroneMove(true, 1),
                "overview", Component.empty())));
        cases.add(new Case("throne-king-overview-move-started", () -> {
            ThroneScreen screen = throne(view(KingdomView.Relation.MEMBER, Role.KING).withThroneMove(true, 1), "overview", Component.empty());
            screen.onResult(true, Component.translatable("kingdomworld.throne_move.started"));
            return screen;
        }));
        cases.add(new Case("throne-king-overview-move-too-soon", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING).withThroneMove(false, 1),
                "overview", Component.translatable("kingdomworld.throne_move.too_soon",
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.ticks(23_000)))));
        cases.add(new Case("throne-subject-overview-moving", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT).withThroneMove(true, 1),
                "overview", Component.empty())));
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
        // A line of the chronicle hovered over: the whole text, the profession and the place.
        cases.add(new Case("throne-chronicle-hover", () -> citizens(view(KingdomView.Relation.MEMBER, Role.STEWARD),
                citizenView(true, false, CitizenNeeds.GrowthCheck.NO_FREE_BED), true, Component.empty()), true,
                screen -> ((ThroneScreen) screen).citizensRowPoint(1)));
        cases.add(new Case("throne-chronicle-hover-kingdom", () -> citizens(view(KingdomView.Relation.MEMBER, Role.STEWARD),
                citizenView(true, false, CitizenNeeds.GrowthCheck.NO_FREE_BED), true, Component.empty()), true,
                screen -> ((ThroneScreen) screen).citizensRowPoint(2)));
        cases.add(new Case("throne-subject-citizens", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT),
                citizenView(false, false, CitizenNeeds.GrowthCheck.TOO_SOON), false, Component.empty())));
        cases.add(new Case("throne-citizens-empty", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT),
                citizenView(false, true, CitizenNeeds.GrowthCheck.FULL), false, Component.empty())));
        cases.add(new Case("throne-npc-chronicle", () -> citizens(view(KingdomView.Relation.MEMBER, Role.SUBJECT).withNpc(LONG_NPC)
                .withRuler(longRuler(false)), withDecrees(citizenView(false, false, CitizenNeeds.GrowthCheck.NO_FREE_BED)), true, Component.empty())));
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
        cases.add(new Case("post-miner-goal", () -> post(postView("miner", WorkStatus.WORKING, true, 2, true, true), Component.empty())));
        cases.add(new Case("post-miner-goal-met", () -> post(postView("miner", WorkStatus.WORKING, true, 1, true, true)
                .withGoal(mineGoal("examplemod:ores/very_long_named_ore_of_the_northern_mountains", MineGoal.MAX_AMOUNT, 9_999_999L, false)),
                Component.empty())));
        cases.add(new Case("post-miner-anything", () -> post(postView("miner", WorkStatus.MINE_WORKED_OUT, true, 1, false, false)
                .withGoal(mineGoal("", 0, 0L, true)), Component.empty())));
        cases.add(new Case("post-miner-goal-read-only", () -> post(postView("miner", WorkStatus.WORKING, false, 1, true, true)
                .withGoal(mineGoal("examplemod:ores/very_long_named_ore_of_the_northern_mountains", 64, 64L, false)), Component.empty())));
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
                        CitizenTexts.profession("lumberjack", Gender.FEMALE)), true)));
        cases.add(new Case("post-smelter", () -> post(postView("smelter", WorkStatus.WORKING, true, 2, true, true), Component.empty())));
        cases.add(new Case("post-cook-read-only", () -> post(postView("cook", WorkStatus.NO_FUEL, false, 1, false, false), Component.empty())));
        cases.add(new Case("post-crafter-no-stations", () -> post(withoutStations(postView("crafter", WorkStatus.NO_WORKSTATION, true, 1, false,
                false)), Component.empty())));
        cases.add(new Case("post-smith", () -> post(postView("smith", WorkStatus.NO_INGREDIENTS, true, 16, true, false), longError)));
        cases.add(new Case("post-auto-set", () -> post(postView("smelter", WorkStatus.NO_ORDERS, true, 1, true, true),
                Component.translatable("kingdomworld.post.auto_off"), true)));
        cases.add(new Case("throne-king-orders", () -> orders(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "list",
                Component.empty())));
        cases.add(new Case("throne-subject-orders", () -> orders(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "list",
                Component.empty())));
        cases.add(new Case("throne-orders-empty", () -> orders(view(KingdomView.Relation.MEMBER, Role.STEWARD), true, true, "list",
                Component.empty())));
        cases.add(new Case("throne-orders-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "orders", Component.empty())));
        cases.add(new Case("throne-orders-create", () -> orders(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "create",
                longError)));
        cases.add(new Case("throne-orders-create-empty", () -> orders(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "create",
                Component.empty())));
        cases.add(new Case("throne-orders-edit", () -> orders(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "edit", longError)));
        cases.add(new Case("throne-king-treasury", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "overview", "today",
                Component.empty())));
        cases.add(new Case("throne-treasury-journal", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "journal", "week",
                Component.empty())));
        cases.add(new Case("throne-treasury-journal-empty", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, true,
                "journal", "yesterday", Component.empty())));
        cases.add(new Case("throne-treasury-settings", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "settings",
                "today", longError)));
        cases.add(new Case("throne-treasurer-settings", () -> treasury(view(KingdomView.Relation.MEMBER, Role.TREASURER), true, false,
                "settings", "today", Component.translatable("kingdomworld.treasury.wage_set", CitizenTexts.profession("minter"),
                        io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)))));
        cases.add(new Case("throne-subject-settings", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "settings",
                "today", Component.empty())));
        cases.add(new Case("throne-treasury-debtors", () -> treasury(view(KingdomView.Relation.MEMBER, Role.STEWARD), false, false, "debtors",
                "today", Component.empty())));
        cases.add(new Case("throne-treasury-empty", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, true, "overview",
                "today", Component.empty())));
        cases.add(new Case("throne-debtors-empty", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, true, "debtors",
                "today", Component.empty())));
        cases.add(new Case("throne-treasury-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "treasury", Component.empty())));
        cases.add(new Case("throne-treasury-payments", () -> payments(view(KingdomView.Relation.MEMBER, Role.KING), true, true, true,
                Component.translatable("kingdomworld.treasury.coins_taken_partly", io.github.sekerator.kingdomworld.common.economy.MoneyTexts.coins(
                        io.github.sekerator.kingdomworld.core.economy.Denomination.SILVER, 9_999_999_999L), 9_999_999_999L))));
        cases.add(new Case("throne-treasury-payments-empty", () -> payments(view(KingdomView.Relation.MEMBER, Role.STEWARD), true, false, false,
                Component.translatable("kingdomworld.treasury.unknown_player", LONG_PLAYER))));
        cases.add(new Case("throne-subject-payments", () -> payments(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, false,
                Component.empty())));
        cases.add(new Case("coin-tooltip", ClientLayoutCheck::coinTooltips));
        cases.add(new Case("throne-treasury-reserves", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "reserves",
                "today", Component.empty())));
        cases.add(new Case("throne-treasury-reserves-empty", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, true,
                "reserves", "today", Component.empty())));
        for (String mode : List.of("sell", "buy", "lots", "export", "import", "settings")) {
            cases.add(new Case("throne-market-" + mode, () -> market(view(KingdomView.Relation.MEMBER, Role.KING), true, false, mode, false, null,
                    Component.empty())));
        }
        for (String mode : List.of("sell", "buy", "export", "import")) {
            cases.add(new Case("throne-market-" + mode + "-form", () -> market(view(KingdomView.Relation.MEMBER, Role.TREASURER), true, false, mode,
                    true, null, longError)));
        }
        cases.add(new Case("throne-market-search", () -> market(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "sell", true, "",
                Component.empty())));
        cases.add(new Case("throne-market-search-none", () -> market(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "import", true,
                "zzzzzzzzzzzzzzzzzzzzzzzz", Component.empty())));
        cases.add(new Case("throne-subject-market", () -> market(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "buy", false, null,
                Component.empty())));
        cases.add(new Case("throne-subject-market-settings", () -> market(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false,
                "settings", false, null, Component.empty())));
        cases.add(new Case("throne-market-empty", () -> market(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "export", false, null,
                Component.empty())));
        cases.add(new Case("throne-market-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "market", Component.empty())));
        cases.add(new Case("throne-trade-deals", () -> {
            // News of a proposal is not an error: it keeps the color the server gave it.
            ThroneScreen screen = trade(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "deals", null, Component.empty());
            screen.onResult(true, Component.translatable("kingdomworld.trade.offer_sell", LONG_KINGDOM, Component.translatable("kingdomworld.trade.goods",
                    longNamed(Items.NETHERITE_SWORD).getHoverName(), io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(4_096)),
                    io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH,
                            io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)).withStyle(net.minecraft.ChatFormatting.AQUA));
            return screen;
        }));
        cases.add(new Case("throne-subject-trade", () -> trade(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "deals", null,
                Component.empty())));
        cases.add(new Case("throne-trade-caravans", () -> trade(view(KingdomView.Relation.MEMBER, Role.CHANCELLOR), true, false, "caravans",
                null, Component.empty())));
        cases.add(new Case("throne-trade-propose", () -> trade(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "propose", null,
                longError)));
        cases.add(new Case("throne-subject-propose", () -> trade(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "propose", null,
                Component.empty())));
        cases.add(new Case("throne-trade-propose-alone", () -> trade(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "propose", null,
                Component.empty())));
        cases.add(new Case("throne-trade-search", () -> trade(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "propose", "ingot",
                Component.empty())));
        cases.add(new Case("throne-trade-empty", () -> trade(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "caravans", null,
                Component.empty())));
        cases.add(new Case("throne-trade-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "trade", Component.empty())));
        cases.add(new Case("stall-buy", () -> stall(false, true, "buy", true, 0, Component.empty())));
        cases.add(new Case("stall-buy-foreign", () -> stall(false, false, "buy", false, 1, longError)));
        cases.add(new Case("stall-sell", () -> stall(false, true, "sell", false, 0, Component.translatable("kingdomworld.stall.and_account",
                Component.translatable("kingdomworld.stall.sold", longNamed(Items.NETHERITE_SWORD).getHoverName(),
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(4_096),
                        io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)),
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)))));
        cases.add(new Case("stall-lots", () -> stall(false, false, "lots", true, 1, Component.empty())));
        cases.add(new Case("stall-mine", () -> stall(false, true, "mine", true, 0, Component.empty())));
        cases.add(new Case("stall-empty", () -> stall(true, false, "buy", true, 0, Component.empty())));
        cases.add(new Case("stall-mine-empty", () -> stall(true, true, "mine", true, 0, Component.empty())));
        cases.add(new Case("exchange", () -> exchange(false, "999999g99s9c", true, true, Component.empty(), false)));
        cases.add(new Case("exchange-coins", () -> exchange(false, "abc", false, false, Component.translatable("kingdomworld.exchange.done",
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT),
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)),
                true)));
        cases.add(new Case("exchange-nothing", () -> exchange(true, "", true, true, longError, false)));
        cases.add(new Case("bank", () -> bank(false, "", Component.empty(), false)));
        cases.add(new Case("bank-withdraw", () -> bank(false, "999999g99s9c", Component.translatable("kingdomworld.bank.withdrew_partly",
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT),
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT)),
                true)));
        cases.add(new Case("bank-error", () -> bank(false, "abc", longError, false)));
        cases.add(new Case("bank-empty", () -> bank(true, "", Component.empty(), false)));
        cases.add(new Case("post-minter", () -> post(postView("minter", WorkStatus.NO_INGREDIENTS, true, 4, false, true), Component.empty())));
        cases.add(new Case("post-minter-held", () -> post(minterView(WorkStatus.MINT_HELD, true, true, true),
                Component.translatable("kingdomworld.treasury.mint_keep", longNamed(Items.IRON_INGOT).getHoverName(), 10_000), true)));
        cases.add(new Case("post-minter-no-treasury", () -> post(minterView(WorkStatus.WORKING, true, false, true), Component.empty())));
        cases.add(new Case("post-minter-treasury-only", () -> post(minterView(WorkStatus.MINT_HELD, false, true, true), Component.empty())));
        cases.add(new Case("post-minter-read-only", () -> post(minterView(WorkStatus.WORKING, false, false, true), Component.empty())));
        cases.add(new Case("post-minter-no-metals", () -> post(minterView(WorkStatus.NO_INGREDIENTS, true, true, false), Component.empty())));
        cases.add(new Case("throne-treasury-mint", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "mint", "today",
                Component.translatable("kingdomworld.treasury.mint_off", longNamed(Items.IRON_INGOT).getHoverName()))));
        cases.add(new Case("throne-treasurer-mint", () -> treasury(view(KingdomView.Relation.MEMBER, Role.TREASURER), true, false, "mint",
                "today", Component.empty())));
        cases.add(new Case("throne-subject-mint", () -> treasury(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false, "mint",
                "today", Component.empty())));
        cases.add(new Case("throne-treasury-mint-empty", () -> treasury(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "mint",
                "today", Component.empty())));
        cases.add(new Case("storehouse-double", () -> storehouse(Component.translatable("kingdomworld.storehouse.double"))));
        cases.add(new Case("storehouse-double-long-title", () -> storehouse(Component.literal(LONG_KINGDOM + " " + LONG_KINGDOM))));
        cases.add(new Case("storehouse-double-model", io.github.sekerator.kingdomworld.client.screen.StorehouseModelPreview::new));
        // The single storehouse with the line of whom it takes in from: everybody, some professions, every profession.
        cases.add(new Case("storehouse-single", () -> storehouse(Component.translatable("block.kingdomworld.storehouse"), 1, true, List.of())));
        cases.add(new Case("storehouse-single-wood", () -> storehouse(Component.translatable("block.kingdomworld.storehouse"), 1, true,
                List.of("lumberjack"))));
        cases.add(new Case("storehouse-single-food-hover", () -> storehouse(Component.translatable("block.kingdomworld.storehouse"), 1, true,
                List.of("farmer", "herder", "fisher")), true, screen -> new int[]{((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen)
                .getGuiLeft() + 40, ((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen).getGuiTop()
                + io.github.sekerator.kingdomworld.common.storage.StorehouseMenu.SETTINGS_TOP + 4}));
        cases.add(new Case("storehouse-single-reader", () -> storehouse(Component.translatable("kingdomworld.storehouse.outside_title"), 1,
                false, allStoreProfessions())));
        cases.add(new Case("storehouse-double-all", () -> storehouse(Component.translatable("kingdomworld.storehouse.double"), 2, true,
                allStoreProfessions())));
        cases.add(new Case("storehouse-accepts", () -> new io.github.sekerator.kingdomworld.client.screen.StorehouseAcceptsScreen(null,
                BlockPos.ZERO, List.of("lumberjack", "farmer"))));
        cases.add(new Case("storehouse-accepts-all", () -> new io.github.sekerator.kingdomworld.client.screen.StorehouseAcceptsScreen(null,
                BlockPos.ZERO, List.of())));
        cases.add(new Case("post-builder", () -> post(postView("builder", WorkStatus.WORKING, true, 4, false, true), Component.empty())));
        cases.add(new Case("post-builder-idle", () -> post(postView("builder", WorkStatus.NO_MATERIALS, false, 16, false, false), longError)));
        cases.add(new Case("throne-construction-catalog", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "catalog",
                "", 0, Component.empty())));
        cases.add(new Case("throne-construction-catalog-filter-own", () -> catalogFiltered("terem", 1)));
        cases.add(new Case("throne-construction-catalog-filter-style", () -> catalogFiltered("terem",
                2 + io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles.index(LONG_STYLE))));
        cases.add(new Case("throne-construction-template", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "catalog",
                "kingdomworld:storehouse2", 0, Component.translatable("kingdomworld.construction.blueprint_taken", Component.literal(LONG_KINGDOM)))));
        cases.add(new Case("throne-construction-template-unavailable", () -> construction(view(KingdomView.Relation.MEMBER, Role.STEWARD), true,
                false, "catalog", "kingdomworld:large_house1", 0, longError)));
        cases.add(new Case("throne-subject-construction-template", () -> construction(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false,
                false, "catalog", "kingdomworld:small_house0", 0, Component.empty())));
        cases.add(new Case("throne-construction-sites", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "sites",
                "", 0, Component.empty())));
        cases.add(new Case("throne-construction-site", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "sites",
                "", 104, longError)));
        cases.add(new Case("throne-construction-site-blocked", () -> construction(view(KingdomView.Relation.MEMBER, Role.STEWARD), true, false,
                "sites", "", 103, Component.empty())));
        cases.add(new Case("throne-subject-construction-site", () -> construction(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, false,
                "sites", "", 100, Component.empty())));
        cases.add(new Case("throne-construction-site-complete", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false,
                "sites", "", 101, Component.translatable("kingdomworld.construction.priority_set", Component.literal(LONG_KINGDOM),
                        Component.translatable("kingdomworld.order.priority.normal")))));
        cases.add(new Case("throne-construction-buildings", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false,
                "buildings", "", 0, Component.empty())));
        cases.add(new Case("throne-construction-empty", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "sites", "",
                0, Component.empty())));
        cases.add(new Case("throne-construction-catalog-empty", () -> construction(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false, true,
                "catalog", "", 0, Component.empty())));
        cases.add(new Case("throne-construction-own", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "own", "", 0,
                Component.empty())));
        cases.add(new Case("throne-construction-own-template", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, false, "own",
                "kingdomworld:custom/1", 0, Component.translatable("kingdomworld.construction.blueprint_renamed", "Щ".repeat(32), "Щ".repeat(32)))));
        cases.add(new Case("throne-construction-own-unavailable", () -> construction(view(KingdomView.Relation.MEMBER, Role.STEWARD), true, false,
                "own", "kingdomworld:custom/2", 0, longError)));
        cases.add(new Case("throne-construction-own-empty", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, true, "own", "",
                0, Component.empty())));
        cases.add(new Case("throne-construction-template-wood", () -> constructionTemplate(view(KingdomView.Relation.MEMBER, Role.KING), "catalog",
                "kingdomworld:small_house0", 9, 5)));
        cases.add(new Case("throne-construction-template-wood-auto", () -> constructionTemplate(view(KingdomView.Relation.MEMBER, Role.KING),
                "catalog", "kingdomworld:storehouse2", 0, 0)));
        cases.add(new Case("throne-construction-building", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true, 1, "",
                longError)));
        cases.add(new Case("throne-construction-building-damaged", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true, 3,
                "", Component.empty())));
        cases.add(new Case("throne-construction-building-city-wall", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true,
                CITY_WALL, "", Component.empty())));
        cases.add(new Case("throne-construction-building-busy", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.STEWARD), true, 3,
                "", Component.empty())));
        cases.add(new Case("throne-subject-construction-building", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.SUBJECT), false,
                1, "", Component.empty())));
        cases.add(new Case("throne-construction-building-upgrade", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true, 1,
                "upgrade", Component.empty())));
        cases.add(new Case("throne-construction-building-repair", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true, 4,
                "repair", longError)));
        cases.add(new Case("throne-construction-building-demolish", () -> constructionBuilding(view(KingdomView.Relation.MEMBER, Role.KING), true, 7,
                "demolish", Component.translatable("kingdomworld.construction.ordered.demolish", Component.literal(LONG_KINGDOM)))));
        cases.add(new Case("capture", () -> capture("", 0, Component.empty(), false)));
        cases.add(new Case("capture-problem", () -> capture("Щ".repeat(32), 2, Component.translatable("kingdomworld.capture.problem.too_big", 256, 256,
                256, 48, 32, 48), true)));
        cases.add(new Case("capture-saved", () -> capture("Мой дом", 1, Component.translatable("kingdomworld.capture.saved", "Щ".repeat(32), 256, 256,
                256), false)));
        cases.add(new Case("rename-blueprint", () -> new io.github.sekerator.kingdomworld.client.screen.RenameBlueprintScreen(null, UUID.randomUUID(),
                1, "Щ".repeat(32))));
        cases.add(new Case("confirm-delete-blueprint", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.construction.delete_question", "Щ".repeat(32)))));
        cases.add(new Case("ruler-hint-first", () -> new io.github.sekerator.kingdomworld.client.screen.BlueprintHintScreen("ruler-first",
                io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer.rulerLines(Component.literal("Right Button"),
                        Component.literal("Left Shift"), 0, new int[3]))));
        cases.add(new Case("ruler-hint-second", () -> new io.github.sekerator.kingdomworld.client.screen.BlueprintHintScreen("ruler-second",
                io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer.rulerLines(Component.literal("Right Button"),
                        Component.literal("Left Shift"), 1, new int[3]))));
        cases.add(new Case("ruler-hint-save", () -> new io.github.sekerator.kingdomworld.client.screen.BlueprintHintScreen("ruler-save",
                io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer.rulerLines(Component.literal("Right Button"),
                        Component.literal("Left Shift"), 2, new int[]{256, 256, 256}))));
        cases.add(new Case("throne-construction-buildings-empty", () -> construction(view(KingdomView.Relation.MEMBER, Role.KING), true, true,
                "buildings", "", 0, Component.empty())));
        cases.add(new Case("throne-construction-loading", () -> throne(view(KingdomView.Relation.MEMBER, Role.KING), "construction",
                Component.empty())));
        cases.add(new Case("blueprint-hint", () -> blueprintHint(false, 9_999)));
        cases.add(new Case("blueprint-hint-refused", () -> blueprintHint(true, 0)));
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
        cases.add(new Case("map-army", () -> {
            fillMapData();
            fillArmyMap();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(1.0, -0.5, 14.0, null, Component.empty(), false);
            screen.showArmyForCheck(null, List.of(3L), List.of());
            return screen;
        }));
        cases.add(new Case("map-army-order", () -> {
            fillMapData();
            fillArmyMap();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(1.0, -0.5, 14.0, null, Component.empty(), false);
            screen.showArmyForCheck("attack", List.of(3L, 4L), List.of(new int[]{40, -60}, new int[]{90, -40}));
            return screen;
        }));
        guideCases(cases);
        cases.add(new Case("map-find-nearest", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(400.0, -300.0, 14.0, null, Component.empty(), false);
            screen.showFindHintForCheck(Component.translatable("kingdomworld.screen.map.find.nearest", LONG_NPC.nameCyrillic(), 5300,
                    Component.translatable("kingdomworld.life.direction.north_west")));
            return screen;
        }));
        cases.add(new Case("map-far", () -> {
            fillMapData();
            // The whole ring of the kingdoms of the world in squares of 16 chunks: two kingdoms, names crowding near the middle.
            UUID first = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
            UUID second = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));
            List<KingdomMapInfo> palette = List.of(
                    new KingdomMapInfo(first, LONG_KINGDOM, 0x6C3483, LONG_PLAYER, 8, 20, 187, true, 0, 0),
                    new KingdomMapInfo(second, "Valoria", 0xB7950B, "", 1, 9, 0, true, 7, 1, LONG_NPC));
            int side = 60;
            int[] cells = new int[side * side];
            for (int i = 0; i < cells.length; i++) {
                int x = i % side - 30;
                int z = i / side - 30;
                cells[i] = Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 1 : Math.abs(x - 12) <= 2 && Math.abs(z + 8) <= 1 ? 2 : 0;
            }
            ClientTerritoryCache.applyArea(new MapAreaPacket("kingdomworld:layout_check", -480, -480, side, side, palette, cells, 16));
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(0.0, 0.0, 0.01, null, Component.empty(), false);
            return screen;
        }));
        cases.add(new Case("map-kingdoms-list", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(1.0, -0.5, 14.0, null, Component.empty(), false);
            List<KingdomMapScreen.KingdomEntry> entries = new ArrayList<>();
            String[] directions = {"north_west", "north_east", "south", "west", "here", "south_east"};
            // Kingdoms of the world of every size, raised and still raising, with five digit people and soldiers, and kingdoms of players.
            io.github.sekerator.kingdomworld.core.npc.NpcLevel[] levels = io.github.sekerator.kingdomworld.core.npc.NpcLevel.values();
            for (int i = 0; i < 30; i++) {
                io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom kingdom = i % 4 == 3 ? null
                        : new io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom(levels[i % levels.length], WIDE_COUNT,
                        WIDE_COUNT, i % 2 == 0, io.github.sekerator.kingdomworld.core.guild.Rank.SSS, io.github.sekerator.kingdomworld.core.guild.Rank.SS);
                entries.add(new KingdomMapScreen.KingdomEntry(i % 2 == 0 ? LONG_KINGDOM : LONG_NPC.nameCyrillic(), i * 400, -i * 300, 12_340L + i * 10,
                        directions[i % directions.length], kingdom));
            }
            screen.showKingdomListForCheck(entries);
            return screen;
        }));
        cases.add(new Case("map-kingdoms-list-empty", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(1.0, -0.5, 14.0, null, Component.empty(), false);
            screen.showKingdomListForCheck(List.of());
            return screen;
        }));
        cases.add(new Case("map-find-none", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(400.0, -300.0, 14.0, null, Component.empty(), false);
            screen.showFindHintForCheck(Component.translatable("kingdomworld.screen.map.find.none"));
            return screen;
        }));
        cases.add(new Case("throne-npc-raising", () -> throne(view(KingdomView.Relation.OUTSIDER, null).withNpc(RAISING_NPC)
                .withRuler(longRuler(false)), "overview", Component.empty())));
        cases.add(new Case("map-view-npc-card", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(6.0, -5.0, 14.0, UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8)), Component.empty(), false);
            return screen;
        }));
        cases.add(new Case("map-view-npc-card-raising", () -> {
            fillMapData(RAISING_NPC);
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(6.0, -5.0, 14.0, UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8)), Component.empty(), false);
            return screen;
        }));
        cases.add(new Case("throne-npc-outsider", () -> throne(view(KingdomView.Relation.OUTSIDER, null).withNpc(LONG_NPC).withRuler(longRuler(false)),
                "overview", Component.empty())));
        cases.add(new Case("throne-npc-subject", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT).withNpc(LONG_NPC).withRuler(longRuler(false)),
                "overview", Component.empty())));
        cases.add(new Case("throne-npc-councillor", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT).withNpc(LONG_NPC)
                .withRuler(longRuler(true)), "overview", Component.empty())));
        cases.add(new Case("throne-npc-subject-status", () -> throne(view(KingdomView.Relation.MEMBER, Role.SUBJECT).withNpc(LONG_NPC)
                .withRuler(longRuler(false)), "overview", Component.translatable("kingdomworld.screen.throne.npc.needs.hint"))));
        cases.add(new Case("throne-npc-new-ruler", () -> throne(view(KingdomView.Relation.OUTSIDER, null).withNpc(LONG_NPC)
                .withRuler(new io.github.sekerator.kingdomworld.common.network.data.RulerInfo(List.of(), List.of(), -60, -80, 0)), "overview",
                Component.empty())));
        cases.add(new Case("map-edit-status", () -> {
            fillMapData();
            KingdomView editing = view(KingdomView.Relation.MEMBER, Role.KING);
            KingdomMapScreen screen = new KingdomMapScreen(editing);
            screen.showForCheck(1.0, -0.5, 9.0, null, claimError, true);
            return screen;
        }));
        cases.add(new Case("confirm-dissolve", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.dissolve.confirm", LONG_KINGDOM))));
        cases.add(new Case("confirm-leave", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.leave.confirm", LONG_KINGDOM))));
        cases.add(new Case("confirm-crown", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.crown.confirm", LONG_PLAYER))));
        // Moving the throne: the question before the move with the days between moves, without them, and before calling it off.
        cases.add(new Case("confirm-throne-move", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.move.confirm", LONG_KINGDOM,
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.days(KingdomView.MAX_THRONE_MOVE_DAYS)))));
        cases.add(new Case("confirm-throne-move-any-time", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.move.confirm.any_time", LONG_KINGDOM))));
        cases.add(new Case("confirm-throne-move-cancel", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"),
                Component.translatable("kingdomworld.screen.throne.move.cancel.confirm"))));
        GuildLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        ArmyLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        MagicLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        CompanionLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)));
        RoadLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        LawLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        DiplomacyLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        NobilityLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)), role -> view(KingdomView.Relation.MEMBER, role));
        LifeLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)),
                (role, manage) -> citizensMode(view(KingdomView.Relation.MEMBER, role), manage, false, "life"));
        WorkforceLayoutCases.cases((name, screen) -> cases.add(new Case(name, screen)));
        WorkforceLayoutCases.posts((name, screen) -> cases.add(new Case(name, screen)),
                (profession, manage) -> postView(profession, WorkStatus.WORKING, manage, 3, true, true));
        return cases;
    }

    /**
     * The guide: the first page with the tree, a page with crafting grids, a table, the results of a search and a page opened
     * from them with the words highlighted, each with pictures; then every page of the guide without pictures.
     */
    private static void guideCases(List<Case> cases) {
        cases.add(new Case("guide-tree", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck("welcome", "", false)));
        cases.add(new Case("guide-craft", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck("founding", "", false)));
        cases.add(new Case("guide-table", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck("chunk_limit", "", false)));
        cases.add(new Case("guide-search", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck(null,
                cyrillic() ? "трон" : "throne", true)));
        cases.add(new Case("guide-search-none", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck(null,
                "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW", true)));
        cases.add(new Case("guide-found", () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck("founding",
                cyrillic() ? "корона трон" : "crown throne", false)));
        for (io.github.sekerator.kingdomworld.core.guide.GuidePage page
                : io.github.sekerator.kingdomworld.client.guide.GuideLibrary.current().book().pages()) {
            cases.add(new Case("guide-page-" + page.id(), () -> io.github.sekerator.kingdomworld.client.screen.GuideScreen.forCheck(page.id(),
                    "", false), false));
        }
    }
}
