package io.github.sekerator.kingdomworld.client.screen;

import com.google.common.collect.Lists;
import io.github.sekerator.kingdomworld.client.guide.ClientGuideNumbers;
import io.github.sekerator.kingdomworld.client.guide.GuideLayout;
import io.github.sekerator.kingdomworld.client.guide.GuideLibrary;
import io.github.sekerator.kingdomworld.client.guide.GuideRecipes;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.guide.GuideBook;
import io.github.sekerator.kingdomworld.core.guide.GuidePage;
import io.github.sekerator.kingdomworld.core.guide.GuideSearch;
import io.github.sekerator.kingdomworld.core.guide.GuideSection;
import io.github.sekerator.kingdomworld.core.guide.GuideText;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The guide "Kingdoms: a Guide": a tree of sections and pages on the left, the search on top and the page on the right, read
 * by scrolling. Pages hold headings, paragraphs with emphasis, links to other pages, the icons of items, lists, notes, tables
 * and the crafting grids of the recipes of the game; the numbers of the configuration are those of the server played on. The
 * search finds pages by their title and text whatever the case and the "ё"; the words found are highlighted in the list of
 * results and on the page opened from it. Back and forward go through the pages read, as in a browser, and the guide opens
 * again on the page read last. It is opened by the guide book, {@code /kingdom guide} and a key without a default.
 */
public class GuideScreen extends KingdomScreen {
    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 520;
    private static final int HEADER = 26;
    private static final int ROW = 12;
    private static final int SCROLL_STEP = 3 * GuideLayout.LINE;
    private static final int LEATHER = 0xF61E160F;
    private static final int LEATHER_DARK = 0xF6150F0A;
    private static final int HEADER_BACK = 0xF62A1F15;
    private static final int EDGE = 0xFF0C0805;
    private static final int ROW_HOVER = 0x24F0D070;
    private static final int ROW_SELECTED = 0x46F0D070;
    private static final int SLOT_BACK = 0xFF140E09;
    private static final int TREE_SECTION = 0xFFE6CC8A;

    /** The page read last in this run of the game, before the client configuration remembers it. */
    @Nullable
    private static String lastPage;

    /**
     * Where the reader is: a page (with the words of the search it was opened from highlighted) or the results of a search.
     */
    private record Place(@Nullable String page, String query, boolean results) {
        static Place page(String id, String query) {
            return new Place(id, query, false);
        }

        static Place results(String query) {
            return new Place(null, query, true);
        }
    }

    /**
     * A place of the history with how far it was scrolled.
     */
    private record Visit(Place place, int scroll) {
    }

    /**
     * A row of the tree: a section or a page of an open section.
     */
    private record TreeRow(GuideSection section, @Nullable GuidePage page) {
    }

    private GuideLibrary.Loaded loaded;
    private GuideBook book = GuideBook.empty();
    @Nullable
    private GuideSearch search;
    private Place current;
    private final Deque<Visit> back = new ArrayDeque<>();
    private final Deque<Visit> forward = new ArrayDeque<>();
    private final Set<String> expanded = new HashSet<>();
    private final List<TreeRow> rows = new ArrayList<>();
    private int treeScroll;
    private int pageScroll;
    private GuideLayout.Result layout = new GuideLayout.Result(List.of(), 0);
    private EditBox searchBox;
    private Button backButton;
    private Button forwardButton;
    private boolean updatingBox;
    private boolean revealCurrent = true;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int treeLeft;
    private int treeWidth;
    private int pageLeft;
    private int pageWidth;
    private int bodyTop;
    private int bodyBottom;

    /**
     * The guide on the page read last.
     */
    public GuideScreen() {
        this(null);
    }

    /**
     * The guide on the page {@code page}, or on the page read last when null.
     */
    public GuideScreen(@Nullable String page) {
        super(Component.translatable("kingdomworld.guide.title"));
        current = Place.page(page != null ? page : remembered(), "");
    }

    /**
     * The guide for the check of the layout: on a page, or on the results of a search when {@code results}.
     */
    public static GuideScreen forCheck(String page, String query, boolean results) {
        GuideScreen screen = new GuideScreen(page);
        screen.current = results ? Place.results(query) : Place.page(page, query);
        return screen;
    }

    private static String remembered() {
        if (lastPage != null) {
            return lastPage;
        }
        try {
            return KingdomWorldConfig.CLIENT.guidePage.get();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    @Override
    public String checkName() {
        return "guide";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        loaded = GuideLibrary.current();
        if (loaded.book() != book) {
            book = loaded.book();
            search = null;
        }
        if (!current.results() && book.page(current.page()).isEmpty()) {
            current = Place.page(book.first().map(GuidePage::id).orElse(""), current.query());
        }
        if (current.page() != null) {
            book.page(current.page()).ifPresent(page -> expanded.add(page.section()));
        }
        ClientGuideNumbers.request(book.numberPaths());

        panelWidth = Math.min(MAX_WIDTH, width - 2 * PAD);
        panelHeight = Math.min(MAX_HEIGHT, height - 2 * PAD);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        bodyTop = panelTop + HEADER + 4;
        bodyBottom = panelTop + panelHeight - PAD;
        treeLeft = panelLeft + PAD;
        treeWidth = Mth.clamp(panelWidth * 28 / 100, 92, 180);
        pageLeft = treeLeft + treeWidth + PAD + 2;
        pageWidth = Math.max(40, panelLeft + panelWidth - PAD - 6 - pageLeft);

        int buttonY = panelTop + 3;
        backButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> goBack())
                .bounds(panelLeft + PAD, buttonY, 20, 20).tooltip(Tooltip.create(Component.translatable("kingdomworld.guide.back"))).build());
        forwardButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> goForward())
                .bounds(panelLeft + PAD + 22, buttonY, 20, 20).tooltip(Tooltip.create(Component.translatable("kingdomworld.guide.forward")))
                .build());
        int searchWidth = Mth.clamp(panelWidth * 36 / 100, 100, 220);
        String query = searchBox != null ? searchBox.getValue() : current.query();
        searchBox = new EditBox(font, panelLeft + panelWidth - PAD - searchWidth, panelTop + 5, searchWidth, 16,
                Component.translatable("kingdomworld.guide.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("kingdomworld.guide.search.hint"));
        updatingBox = true;
        searchBox.setValue(query);
        updatingBox = false;
        searchBox.setResponder(this::searched);
        addRenderableWidget(searchBox);
        relayout();
    }

    private int pageTop() {
        return bodyTop + 2;
    }

    private int pageHeight() {
        return Math.max(GuideLayout.LINE, bodyBottom - pageTop() - 2);
    }

    private int treeTop() {
        return bodyTop + 2;
    }

    private int treeRows() {
        return Math.max(1, (bodyBottom - treeTop() - 2) / ROW);
    }

    /**
     * Lays the shown page or the results out again, for a new width, new numbers or new highlights.
     */
    private void relayout() {
        GuideLayout.Context context = new GuideLayout.Context(font, number -> ClientGuideNumbers.text(number, loaded.decimalComma()),
                GuideRecipes::stack, GuideRecipes::find, GuideSearch.words(current.query()));
        if (book.isEmpty()) {
            layout = GuideLayout.results(List.of(), id -> "", "", Component.translatable("kingdomworld.guide.empty").getString(),
                    pageWidth, context);
        } else if (current.results()) {
            List<GuideSearch.Hit> hits = search().find(current.query());
            layout = GuideLayout.results(hits, this::sectionTitle,
                    Component.translatable("kingdomworld.guide.search.found", hits.size()).getString(),
                    Component.translatable("kingdomworld.guide.search.none", current.query().strip()).getString(), pageWidth, context);
        } else {
            Optional<GuidePage> page = book.page(current.page());
            layout = page.map(shown -> GuideLayout.page(shown, sectionTitle(shown.id()), pageWidth, context))
                    .orElseGet(() -> GuideLayout.results(List.of(), id -> "", "", "", pageWidth, context));
        }
        pageScroll = Mth.clamp(pageScroll, 0, maxPageScroll());
        rebuildRows();
        if (backButton != null) {
            backButton.active = !back.isEmpty();
            forwardButton.active = !forward.isEmpty();
        }
    }

    private GuideSearch search() {
        if (search == null) {
            // An icon stands beside the name of its item in the text: the search reads the name once.
            search = new GuideSearch(book, new GuideText.Resolver(id -> "", number -> ClientGuideNumbers.text(number, loaded.decimalComma())));
        }
        return search;
    }

    private String sectionTitle(String pageId) {
        return book.page(pageId).flatMap(page -> book.section(page.section())).map(GuideSection::title).orElse("");
    }

    private void rebuildRows() {
        rows.clear();
        for (GuideSection section : book.sections()) {
            rows.add(new TreeRow(section, null));
            if (expanded.contains(section.id())) {
                for (GuidePage page : section.pages()) {
                    rows.add(new TreeRow(section, page));
                }
            }
        }
        if (revealCurrent && current.page() != null && !current.results()) {
            for (int i = 0; i < rows.size(); i++) {
                TreeRow row = rows.get(i);
                if (row.page() != null && row.page().id().equals(current.page())) {
                    int first = treeScroll;
                    if (i < first) {
                        treeScroll = i;
                    } else if (i >= first + treeRows()) {
                        treeScroll = i - treeRows() + 1;
                    }
                }
            }
            revealCurrent = false;
        }
        treeScroll = Mth.clamp(treeScroll, 0, Math.max(0, rows.size() - treeRows()));
    }

    private int maxPageScroll() {
        return Math.max(0, layout.height() - pageHeight());
    }

    /**
     * The values of the configuration came from the server: the numbers of the pages change.
     */
    public void numbersChanged() {
        search = null;
        relayout();
    }

    // ------------------------------------------------------------------ navigation

    private void navigate(Place place) {
        back.push(new Visit(current, pageScroll));
        forward.clear();
        show(new Visit(place, 0));
    }

    private void show(Visit visit) {
        current = visit.place();
        pageScroll = visit.scroll();
        if (current.page() != null) {
            book.page(current.page()).ifPresent(page -> expanded.add(page.section()));
            if (!current.results()) {
                lastPage = current.page();
            }
        }
        revealCurrent = true;
        syncBox();
        relayout();
        pageScroll = Mth.clamp(visit.scroll(), 0, maxPageScroll());
    }

    private void goBack() {
        if (!back.isEmpty()) {
            forward.push(new Visit(current, pageScroll));
            show(back.pop());
        }
    }

    private void goForward() {
        if (!forward.isEmpty()) {
            back.push(new Visit(current, pageScroll));
            show(forward.pop());
        }
    }

    private void open(String page) {
        String query = current.results() ? current.query() : "";
        navigate(Place.page(page, query));
    }

    /**
     * The search box shows the words of the place: the query of the results, the words highlighted on a page.
     */
    private void syncBox() {
        if (searchBox != null && !searchBox.getValue().equals(current.query())) {
            updatingBox = true;
            searchBox.setValue(current.query());
            updatingBox = false;
        }
    }

    private void searched(String text) {
        if (updatingBox) {
            return;
        }
        boolean blank = GuideSearch.words(text).isEmpty();
        if (!blank) {
            if (current.results()) {
                current = Place.results(text);
                pageScroll = 0;
                relayout();
            } else {
                navigate(Place.results(text));
            }
        } else if (current.results()) {
            // Clearing the search goes back to the page read before it.
            if (!back.isEmpty() && !back.peek().place().results()) {
                show(back.pop());
            } else {
                current = Place.page(book.first().map(GuidePage::id).orElse(""), "");
                relayout();
            }
        } else if (!current.query().isEmpty()) {
            current = Place.page(current.page(), "");
            relayout();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (current.page() != null && !current.results()) {
            lastPage = current.page();
            try {
                if (!KingdomWorldConfig.CLIENT.guidePage.get().equals(lastPage)) {
                    KingdomWorldConfig.CLIENT.guidePage.set(lastPage);
                    KingdomWorldConfig.CLIENT.guidePage.save();
                }
            } catch (IllegalStateException e) {
                // The client configuration is not loaded (a check of the layout): the page is remembered for this run only.
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawFrame(graphics);
        int titleLeft = panelLeft + PAD + 46;
        fittedLabel(graphics, title, titleLeft, panelTop + 9, GuideLayout.TITLE, searchBox.getX() - 6);
        drawTree(graphics, mouseX, mouseY);
        drawPage(graphics, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphics graphics) {
        int left = panelLeft;
        int top = panelTop;
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, EDGE);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, GuideLayout.GOLD);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, GuideLayout.GOLD_DARK);
        graphics.fill(left, top, right, bottom, LEATHER);
        graphics.fill(left, top, right, top + HEADER, HEADER_BACK);
        graphics.fill(left, top + HEADER, right, top + HEADER + 1, GuideLayout.GOLD);
        graphics.fill(left, top + HEADER + 1, right, top + HEADER + 2, GuideLayout.GOLD_DARK);
        // Studs of gold in the corners, as on a bound book.
        for (int[] corner : new int[][]{{left - 3, top - 3}, {right - 2, top - 3}, {left - 3, bottom - 2}, {right - 2, bottom - 2}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 5, corner[1] + 5, GuideLayout.GOLD);
            graphics.fill(corner[0] + 1, corner[1] + 1, corner[0] + 4, corner[1] + 4, 0xFFE8C878);
        }
        graphics.fill(treeLeft - 2, bodyTop - 2, treeLeft + treeWidth + 2, bodyBottom + 2, LEATHER_DARK);
        graphics.fill(treeLeft + treeWidth + 3, bodyTop - 2, treeLeft + treeWidth + 4, bodyBottom + 2, GuideLayout.GOLD_DARK);
    }

    private void drawTree(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = treeRows();
        int right = treeLeft + treeWidth;
        for (int i = 0; i < visible && treeScroll + i < rows.size(); i++) {
            TreeRow row = rows.get(treeScroll + i);
            int y = treeTop() + i * ROW;
            boolean over = mouseX >= treeLeft && mouseX < right && mouseY >= y && mouseY < y + ROW;
            boolean selected = row.page() != null && !current.results() && row.page().id().equals(current.page());
            if (selected) {
                graphics.fill(treeLeft, y, right, y + ROW, ROW_SELECTED);
                graphics.fill(treeLeft, y, treeLeft + 1, y + ROW, GuideLayout.GOLD);
            } else if (over) {
                graphics.fill(treeLeft, y, right, y + ROW, ROW_HOVER);
            }
            int textY = y + 2;
            if (row.page() == null) {
                boolean open = expanded.contains(row.section().id());
                drawArrow(graphics, treeLeft + 2, y + 3, open);
                int x = treeLeft + 9;
                ItemStack icon = row.section().icon().isEmpty() ? ItemStack.EMPTY : GuideRecipes.stack(row.section().icon());
                if (!icon.isEmpty()) {
                    drawIcon(graphics, icon, x, y + 2, GuideLayout.ICON);
                    x += GuideLayout.ICON + 3;
                }
                fittedLabel(graphics, Component.literal(row.section().title()), x, textY, TREE_SECTION, right - 2);
            } else {
                fittedLabel(graphics, Component.literal(row.page().title()), treeLeft + 13, textY,
                        selected ? GuideLayout.TITLE : over ? 0xFFFFFFFF : GuideLayout.TEXT, right - 2);
            }
        }
        if (rows.size() > visible) {
            scrollbar(graphics, right + 1, treeTop(), visible * ROW, rows.size() * ROW, treeScroll * ROW);
        }
    }

    /**
     * A small triangle of gold: pointing down for an open section, right for a closed one.
     */
    private static void drawArrow(GuiGraphics graphics, int x, int y, boolean open) {
        for (int i = 0; i < 3; i++) {
            if (open) {
                graphics.fill(x + i, y + 1 + i, x + 5 - i, y + 2 + i, GuideLayout.GOLD);
            } else {
                graphics.fill(x + 1 + i, y + i, x + 2 + i, y + 5 - i, GuideLayout.GOLD);
            }
        }
    }

    private void scrollbar(GuiGraphics graphics, int x, int top, int view, int content, int offset) {
        if (content <= view) {
            return;
        }
        graphics.fill(x, top, x + 2, top + view, 0x50000000);
        int thumb = Math.max(8, view * view / content);
        int y = top + (int) ((long) (view - thumb) * offset / Math.max(1, content - view));
        graphics.fill(x, y, x + 2, y + thumb, GuideLayout.GOLD);
    }

    private void drawPage(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = pageTop();
        int height = pageHeight();
        int originY = top - pageScroll;
        boolean inPage = mouseX >= pageLeft && mouseX < pageLeft + pageWidth && mouseY >= top && mouseY < top + height;
        GuideLayout.Piece hovered = inPage ? pieceAt(mouseX, mouseY) : null;
        String hoveredTarget = target(hovered);
        ItemStack hoveredStack = ItemStack.EMPTY;
        // The scissor follows the window of the game, not the picture the check of the layout draws into: there only what
        // shows whole is drawn.
        boolean clip = !LayoutCheck.active();
        int shownTop = clip ? Integer.MIN_VALUE / 2 : top;
        int shownBottom = clip ? Integer.MAX_VALUE / 2 : top + height;
        if (clip) {
            graphics.enableScissor(pageLeft - 2, top, pageLeft + pageWidth + 2, top + height);
        }
        for (GuideLayout.Piece piece : layout.pieces()) {
            if (piece instanceof GuideLayout.Box box) {
                boolean shown = clip ? originY + box.y2() >= top && originY + box.y1() <= top + height
                        : originY + box.y1() >= top && originY + box.y2() <= top + height;
                if (shown) {
                    graphics.fill(pageLeft + box.x1(), originY + box.y1(), pageLeft + box.x2(), originY + box.y2(), box.color());
                }
            } else if (piece instanceof GuideLayout.Zone zone) {
                if (zone.target().equals(hoveredTarget)) {
                    graphics.fill(pageLeft + zone.x1(), originY + zone.y1(), pageLeft + zone.x2(), originY + zone.y2(), ROW_HOVER);
                }
            } else if (piece instanceof GuideLayout.Run run) {
                int y = originY + run.y();
                // The check of the layout looks at the whole page, the lines below the window too.
                LayoutCheck.drawn(this, Component.literal(run.text()), pageLeft + run.x(), y, run.width());
                if (y < shownTop || y + GuideLayout.LINE > shownBottom || y + GuideLayout.LINE < top || y > top + height) {
                    continue;
                }
                int color = run.kind() == GuideLayout.Kind.LINK && run.target() != null && run.target().equals(hoveredTarget)
                        ? GuideLayout.LINK_HOVER : run.kind().color;
                graphics.drawString(font, Component.literal(run.text()).withStyle(run.kind().style), pageLeft + run.x(), y, color, false);
            } else if (piece instanceof GuideLayout.Icon icon) {
                int y = originY + icon.y();
                if (y >= shownTop && y + icon.size() <= shownBottom && y + icon.size() >= top && y <= top + height) {
                    drawIcon(graphics, icon.stack(), pageLeft + icon.x(), y, icon.size());
                    if (piece == hovered) {
                        hoveredStack = icon.stack();
                    }
                }
            } else if (piece instanceof GuideLayout.Grid grid) {
                int y = originY + grid.y();
                if (y >= shownTop && y + GuideLayout.GRID_HEIGHT + GuideLayout.LINE + 2 <= shownBottom
                        && y + GuideLayout.GRID_HEIGHT + GuideLayout.LINE + 2 >= top && y <= top + height) {
                    ItemStack over = drawGrid(graphics, grid, pageLeft + grid.x(), y, mouseX, mouseY, inPage, top, height);
                    if (!over.isEmpty()) {
                        hoveredStack = over;
                    }
                }
            }
        }
        if (clip) {
            graphics.disableScissor();
        }
        scrollbar(graphics, pageLeft + pageWidth + 3, top, height, layout.height(), pageScroll);
        if (!hoveredStack.isEmpty()) {
            setTooltipForNextRenderPass(Lists.transform(Screen.getTooltipFromItem(minecraft, hoveredStack), Component::getVisualOrderText));
        } else if (hovered instanceof GuideLayout.Run run && run.kind() == GuideLayout.Kind.LINK && run.target() != null) {
            book.page(run.target()).ifPresent(page -> setTooltipForNextRenderPass(
                    Component.translatable("kingdomworld.guide.link", sectionTitle(page.id()), page.title())));
        }
    }

    /**
     * Draws a crafting grid with the name of what it makes above it.
     *
     * @return the item under the mouse, or empty
     */
    private ItemStack drawGrid(GuiGraphics graphics, GuideLayout.Grid grid, int x, int y, int mouseX, int mouseY, boolean inPage,
                               int top, int height) {
        if (grid.grid() == null) {
            String text = Component.translatable("kingdomworld.guide.recipe.missing", grid.id()).getString();
            String shown = fit(text, pageWidth);
            graphics.drawString(font, shown, x, y, GuideLayout.MUTED, false);
            if (y >= top && y + GuideLayout.LINE <= top + height) {
                LayoutCheck.drawn(this, Component.literal(shown), x, y, font.width(shown));
            }
            return ItemStack.EMPTY;
        }
        GuideRecipes.Grid recipe = grid.grid();
        ItemStack hovered = ItemStack.EMPTY;
        String name = fit(recipe.result().getHoverName().getString(), GuideLayout.GRID_WIDTH);
        graphics.drawString(font, name, x, y, GuideLayout.TITLE, false);
        if (y >= top && y + GuideLayout.LINE <= top + height) {
            LayoutCheck.drawn(this, Component.literal(name), x, y, font.width(name));
        }
        int gridY = y + GuideLayout.LINE + 2;
        long second = Util.getMillis() / 1000L;
        for (int i = 0; i < recipe.slots().size(); i++) {
            int slotX = x + (i % 3) * GuideLayout.SLOT;
            int slotY = gridY + (i / 3) * GuideLayout.SLOT;
            slot(graphics, slotX, slotY);
            List<ItemStack> choices = recipe.slots().get(i);
            if (!choices.isEmpty()) {
                ItemStack stack = choices.get((int) (second % choices.size()));
                graphics.renderItem(stack, slotX + 1, slotY + 1);
                if (inPage && over(mouseX, mouseY, slotX, slotY, GuideLayout.SLOT)) {
                    hovered = stack;
                }
            }
        }
        int arrowX = x + 3 * GuideLayout.SLOT + 8;
        int arrowY = gridY + GuideLayout.SLOT + 7;
        graphics.fill(arrowX, arrowY + 1, arrowX + 11, arrowY + 3, GuideLayout.GOLD);
        for (int i = 0; i < 4; i++) {
            graphics.fill(arrowX + 11 + i, arrowY - 2 + i, arrowX + 12 + i, arrowY + 6 - i, GuideLayout.GOLD);
        }
        int resultX = arrowX + 16 + 8;
        int resultY = gridY + GuideLayout.SLOT;
        slot(graphics, resultX, resultY);
        graphics.renderItem(recipe.result(), resultX + 1, resultY + 1);
        graphics.renderItemDecorations(font, recipe.result(), resultX + 1, resultY + 1);
        if (inPage && over(mouseX, mouseY, resultX, resultY, GuideLayout.SLOT)) {
            hovered = recipe.result();
        }
        return hovered;
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + GuideLayout.SLOT, y + GuideLayout.SLOT, GuideLayout.GOLD_DARK);
        graphics.fill(x + 1, y + 1, x + GuideLayout.SLOT - 1, y + GuideLayout.SLOT - 1, SLOT_BACK);
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int size) {
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    /**
     * Draws the icon of an item {@code size} pixels wide: 16 as in an inventory, 8 among the words of a line.
     */
    private static void drawIcon(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (size == 16) {
            graphics.renderItem(stack, x, y);
            return;
        }
        float scale = size / 16.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * The piece of the page under the mouse: a link, a result, an icon or a crafting grid.
     */
    @Nullable
    private GuideLayout.Piece pieceAt(double mouseX, double mouseY) {
        double x = mouseX - pageLeft;
        double y = mouseY - pageTop() + pageScroll;
        GuideLayout.Piece found = null;
        for (GuideLayout.Piece piece : layout.pieces()) {
            if (piece instanceof GuideLayout.Run run && run.target() != null) {
                if (x >= run.x() && x < run.x() + run.width() && y >= run.y() - 1 && y < run.y() + GuideLayout.LINE) {
                    found = piece;
                }
            } else if (piece instanceof GuideLayout.Icon icon) {
                if (x >= icon.x() && x < icon.x() + icon.size() && y >= icon.y() && y < icon.y() + icon.size()) {
                    found = piece;
                }
            } else if (piece instanceof GuideLayout.Zone zone) {
                if (x >= zone.x1() && x < zone.x2() && y >= zone.y1() && y < zone.y2() && found == null) {
                    found = piece;
                }
            }
        }
        return found;
    }

    @Nullable
    private static String target(@Nullable GuideLayout.Piece piece) {
        if (piece instanceof GuideLayout.Run run) {
            return run.target();
        }
        if (piece instanceof GuideLayout.Zone zone) {
            return zone.target();
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            goBack();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            goForward();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mouseX >= treeLeft && mouseX < treeLeft + treeWidth && mouseY >= treeTop() && mouseY < treeTop() + treeRows() * ROW) {
            int index = treeScroll + (int) ((mouseY - treeTop()) / ROW);
            if (index >= 0 && index < rows.size()) {
                TreeRow row = rows.get(index);
                if (row.page() == null) {
                    if (!expanded.remove(row.section().id())) {
                        expanded.add(row.section().id());
                    }
                    rebuildRows();
                } else {
                    open(row.page().id());
                }
                return true;
            }
        }
        if (mouseX >= pageLeft && mouseX < pageLeft + pageWidth && mouseY >= pageTop() && mouseY < pageTop() + pageHeight()) {
            String target = target(pieceAt(mouseX, mouseY));
            if (target != null && book.page(target).isPresent()) {
                open(target);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < treeLeft + treeWidth + 4) {
            treeScroll = Mth.clamp(treeScroll - (int) Math.signum(delta) * 3, 0, Math.max(0, rows.size() - treeRows()));
        } else {
            pageScroll = Mth.clamp(pageScroll - (int) Math.signum(delta) * SCROLL_STEP, 0, maxPageScroll());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        if (control && key == GLFW.GLFW_KEY_F) {
            setFocused(searchBox);
            searchBox.setFocused(true);
            return true;
        }
        if (alt && key == GLFW.GLFW_KEY_LEFT) {
            goBack();
            return true;
        }
        if (alt && key == GLFW.GLFW_KEY_RIGHT) {
            goForward();
            return true;
        }
        if (searchBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!current.results() && !GuideSearch.words(searchBox.getValue()).isEmpty()) {
                    navigate(Place.results(searchBox.getValue()));
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(key, scanCode, modifiers);
            }
            if (searchBox.keyPressed(key, scanCode, modifiers) || searchBox.canConsumeInput()) {
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_UP -> pageScroll = Mth.clamp(pageScroll - 2 * GuideLayout.LINE, 0, maxPageScroll());
            case GLFW.GLFW_KEY_DOWN -> pageScroll = Mth.clamp(pageScroll + 2 * GuideLayout.LINE, 0, maxPageScroll());
            case GLFW.GLFW_KEY_PAGE_UP -> pageScroll = Mth.clamp(pageScroll - pageHeight() + GuideLayout.LINE, 0, maxPageScroll());
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE ->
                    pageScroll = Mth.clamp(pageScroll + pageHeight() - GuideLayout.LINE, 0, maxPageScroll());
            case GLFW.GLFW_KEY_HOME -> pageScroll = 0;
            case GLFW.GLFW_KEY_END -> pageScroll = maxPageScroll();
            case GLFW.GLFW_KEY_BACKSPACE -> goBack();
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!searchBox.isFocused() && Character.isLetterOrDigit(character)) {
            // Typing anywhere searches.
            setFocused(searchBox);
            searchBox.setFocused(true);
        }
        return super.charTyped(character, modifiers);
    }

    // ------------------------------------------------------------------ for checks

    /**
     * The height of the page or results shown, for the client self-check.
     */
    public int contentHeightForCheck() {
        return layout.height();
    }
}
