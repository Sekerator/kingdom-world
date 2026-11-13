package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide book "Kingdoms: a Guide": a table of contents and fifteen chapters, read page by page. The pages are cut from the
 * translated text by the width of the screen, so the book reads the same in every language and on a narrow screen; a chapter
 * title too long for its line is shortened and shown in full on hover. The book remembers where it was left open.
 */
public class GuideScreen extends KingdomScreen {
    public static final int CHAPTERS = 15;
    /** The widest the pages get, so that lines stay easy to read on a wide screen. */
    private static final int MAX_WIDTH = 420;
    private static final int HEADER = 22;
    private static final int FOOTER = 26;
    private static final int ROW = 13;
    private static final int TITLE_COLOR = 0xFFF0D070;
    private static final int TEXT_COLOR = 0xFFE8E0D0;
    private static final int LINK_COLOR = 0xFFE0C890;
    private static final int LINK_HOVER = 0xFFFFF0B0;

    /** Where the book was left open: the chapter (-1 for the contents) and the page. */
    private static int lastChapter = -1;
    private static int lastPage;

    /** The chapter shown, or -1 for the table of contents. */
    private int chapter;
    private int page;
    private int pages = 1;
    private List<FormattedCharSequence> lines = List.of();
    private int panelWidth;
    private int panelHeight;
    /** Rows of the contents on a page and its columns. */
    private int rowsPerColumn;
    private int columns;

    public GuideScreen() {
        this(lastChapter, lastPage);
    }

    public GuideScreen(int chapter, int page) {
        super(Component.translatable("kingdomworld.guide.title"));
        this.chapter = Mth.clamp(chapter, -1, CHAPTERS - 1);
        this.page = Math.max(0, page);
    }

    @Override
    public String checkName() {
        return "guide";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int bodyTop() {
        return top() + HEADER;
    }

    private int bodyHeight() {
        return Math.max(LINE, panelHeight - HEADER - FOOTER);
    }

    private int bodyWidth() {
        return panelWidth - 2 * PAD;
    }

    static Component chapterTitle(int index) {
        return Component.translatable("kingdomworld.guide.chapter.title", index + 1,
                Component.translatable("kingdomworld.guide.chapter." + (index + 1) + ".title"));
    }

    private static Component chapterText(int index) {
        return Component.translatable("kingdomworld.guide.chapter." + (index + 1) + ".text");
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 2 * PAD);
        panelHeight = height - 2 * PAD;
        layoutPages();
        Component contents = Component.translatable("kingdomworld.guide.contents");
        Component previous = Component.translatable("kingdomworld.guide.previous");
        Component next = Component.translatable("kingdomworld.guide.next");
        int arrows = buttonWidth(previous, next);
        int buttonY = top() + panelHeight - FOOTER + 4;
        int right = left() + panelWidth - PAD;
        Button back = addRenderableWidget(Button.builder(contents, button -> open(-1, 0))
                .bounds(left() + PAD, buttonY, buttonWidth(contents), 20).build());
        back.visible = chapter >= 0;
        Button forward = addRenderableWidget(Button.builder(next, button -> turn(1)).bounds(right - arrows, buttonY, arrows, 20).build());
        Button backward = addRenderableWidget(Button.builder(previous, button -> turn(-1))
                .bounds(right - 2 * arrows - 4, buttonY, arrows, 20).build());
        forward.active = chapter < CHAPTERS - 1 || page < pages - 1;
        backward.active = chapter > -1 || page > 0;
        remember();
    }

    /**
     * Cuts the shown chapter (or the contents) into pages that fit the screen.
     */
    private void layoutPages() {
        if (chapter < 0) {
            lines = List.of();
            columns = bodyWidth() >= 300 ? 2 : 1;
            rowsPerColumn = Math.max(1, bodyHeight() / ROW);
            int perPage = rowsPerColumn * columns;
            pages = Math.max(1, (CHAPTERS + perPage - 1) / perPage);
        } else {
            lines = new ArrayList<>(font.split(chapterText(chapter), bodyWidth()));
            int perPage = Math.max(1, bodyHeight() / LINE);
            pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        }
        page = Mth.clamp(page, 0, pages - 1);
    }

    private void open(int newChapter, int newPage) {
        chapter = Mth.clamp(newChapter, -1, CHAPTERS - 1);
        page = newPage;
        rebuildWidgets();
    }

    /**
     * Turns a page; past the last page of a chapter comes the next chapter, before the first page the one before.
     */
    private void turn(int direction) {
        if (direction > 0) {
            if (page < pages - 1) {
                open(chapter, page + 1);
            } else if (chapter < CHAPTERS - 1) {
                open(chapter + 1, 0);
            }
        } else if (page > 0) {
            open(chapter, page - 1);
        } else if (chapter > -1) {
            // The page before the first one is the last page of the chapter before.
            open(chapter - 1, Integer.MAX_VALUE);
        }
    }

    private void remember() {
        lastChapter = chapter;
        lastPage = page;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF8B6B3A);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201812);
        int right = left + panelWidth - PAD;
        Component heading = chapter < 0 ? title : chapterTitle(chapter);
        fittedLabel(graphics, heading, left + PAD, top + 7, TITLE_COLOR, right);
        graphics.fill(left + PAD, top + HEADER - 4, right, top + HEADER - 3, 0x808B6B3A);
        if (chapter < 0) {
            renderContents(graphics, mouseX, mouseY);
        } else {
            int perPage = Math.max(1, bodyHeight() / LINE);
            int first = page * perPage;
            for (int i = first; i < Math.min(lines.size(), first + perPage); i++) {
                int y = bodyTop() + (i - first) * LINE;
                graphics.drawString(font, lines.get(i), left + PAD, y, TEXT_COLOR);
                LayoutCheck.drawn(this, chapterText(chapter), left + PAD, y, font.width(lines.get(i)));
            }
        }
        if (pages > 1 || chapter >= 0) {
            Component number = Component.translatable("kingdomworld.guide.page", page + 1, pages);
            int buttons = buttonWidth(Component.translatable("kingdomworld.guide.previous"), Component.translatable("kingdomworld.guide.next"));
            int numberRight = right - 2 * buttons - 8;
            int numberX = numberRight - font.width(number);
            int contentsRight = chapter >= 0 ? left + PAD + buttonWidth(Component.translatable("kingdomworld.guide.contents")) + 4 : left + PAD;
            if (numberX >= contentsRight) {
                label(graphics, number, numberX, top + panelHeight - FOOTER + 10, 0xFFA8A8A8, numberRight);
            }
        }
    }

    private void renderContents(GuiGraphics graphics, int mouseX, int mouseY) {
        int perPage = rowsPerColumn * columns;
        int columnWidth = (bodyWidth() - (columns - 1) * PAD) / columns;
        for (int slot = 0; slot < perPage; slot++) {
            int index = page * perPage + slot;
            if (index >= CHAPTERS) {
                break;
            }
            int x = left() + PAD + (slot / rowsPerColumn) * (columnWidth + PAD);
            int y = bodyTop() + (slot % rowsPerColumn) * ROW + 2;
            boolean over = mouseX >= x && mouseX < x + columnWidth && mouseY >= y - 2 && mouseY < y + ROW - 2;
            if (over) {
                graphics.fill(x - 2, y - 2, x + columnWidth, y + ROW - 3, 0x40F0D070);
            }
            fittedLabel(graphics, chapterTitle(index), x, y, over ? LINK_HOVER : LINK_COLOR, x + columnWidth);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chapter < 0 && button == 0) {
            int perPage = rowsPerColumn * columns;
            int columnWidth = (bodyWidth() - (columns - 1) * PAD) / columns;
            for (int slot = 0; slot < perPage; slot++) {
                int index = page * perPage + slot;
                if (index >= CHAPTERS) {
                    break;
                }
                int x = left() + PAD + (slot / rowsPerColumn) * (columnWidth + PAD);
                int y = bodyTop() + (slot % rowsPerColumn) * ROW + 2;
                if (mouseX >= x && mouseX < x + columnWidth && mouseY >= y - 2 && mouseY < y + ROW - 2) {
                    open(index, 0);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Arrows and page keys turn pages, like in a book of the game.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            turn(delta < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * The pages of the chapter shown, for the client self-check.
     */
    public int pagesForCheck() {
        return pages;
    }
}
