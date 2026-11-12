package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The choice of one of the architectural styles, shared by the founding screen and the overview of the throne screen: a
 * picture of the castle of the style, its name between two arrows, a mark when it is the style the land of the throne
 * suggests, and what the style is like on hover over the picture. Laid out in a column with the name under the picture,
 * or, where there is no room for that, with the arrows beside the picture and the name on it.
 */
final class StylePicker {
    static final int ARROW = 16;
    /** The pictures are 16:9, as their textures of 256x144. */
    static final int PICTURE_WIDTH = 112;
    static final int PICTURE_HEIGHT = 63;
    static final int SMALL_PICTURE_WIDTH = 96;
    static final int SMALL_PICTURE_HEIGHT = 54;
    /** The space between the picture and the row of the name under it. */
    static final int ROW_GAP = 3;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 144;
    /** The widest line of the text shown on hover over the picture. */
    private static final int CAPTION_WIDTH = 200;
    private static final int BAND = 11;
    private static final int FRAME = 0xFFC8A040;
    private static final int BAND_COLOR = 0xC0100C08;
    private static final String KEY = "kingdomworld.style.";

    private final int suggested;
    private int index;
    private int x;
    private int y;
    private int width;
    private int pictureWidth = PICTURE_WIDTH;
    private int pictureHeight = PICTURE_HEIGHT;
    private boolean compact;

    /**
     * @param index     the style shown first, as an index into {@link ArchitecturalStyles#IDS}
     * @param suggested the style the land suggests, or -1 when nothing is suggested
     */
    StylePicker(int index, int suggested) {
        this.index = Math.floorMod(index, ArchitecturalStyles.IDS.size());
        this.suggested = suggested;
    }

    int index() {
        return index;
    }

    String id() {
        return ArchitecturalStyles.id(index);
    }

    /**
     * Shows the style of this index; an index out of range is taken round the list.
     */
    void select(int value) {
        index = Math.floorMod(value, ArchitecturalStyles.IDS.size());
    }

    /**
     * The name of a style: «Терем», "Gothic".
     */
    static Component name(String id) {
        return Component.translatable(KEY + id);
    }

    /**
     * What a style is like, in one sentence.
     */
    static Component caption(String id) {
        return Component.translatable(KEY + id + ".caption");
    }

    /**
     * The caption of a style cut into lines that fit a tooltip.
     */
    static List<Component> captionLines(Font font, String id) {
        List<Component> lines = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(caption(id), CAPTION_WIDTH, Style.EMPTY)) {
            lines.add(Component.literal(line.getString()));
        }
        return lines;
    }

    private static ResourceLocation picture(String id) {
        return KingdomWorld.id("textures/gui/styles/" + id + ".png");
    }

    /**
     * The width a column needs for the name of every style between the arrows.
     */
    static int rowWidth(Font font) {
        int widest = 0;
        for (String id : ArchitecturalStyles.IDS) {
            widest = Math.max(widest, font.width(name(id)));
        }
        return widest + 2 * ARROW + 8;
    }

    /**
     * The height of the picker laid out with its name under the picture.
     */
    static int height(int pictureHeight) {
        return pictureHeight + ROW_GAP + ARROW;
    }

    /**
     * Places the picker in a column of {@code width} from {@code x}: the picture centred at the top of it, and the row of the
     * name and the arrows under it, or with {@code compact} the arrows beside the picture and the name on its foot.
     */
    void layout(int columnX, int columnY, int columnWidth, int pictureW, int pictureH, boolean compactLayout) {
        x = columnX;
        y = columnY;
        width = columnWidth;
        pictureWidth = pictureW;
        pictureHeight = pictureH;
        compact = compactLayout;
    }

    int pictureX() {
        return x + (width - pictureWidth) / 2;
    }

    /**
     * The arrows that show the previous and the next style; {@code changed} runs after either.
     */
    List<Button> buttons(Runnable changed) {
        int rowY = compact ? y + (pictureHeight - ARROW) / 2 : y + pictureHeight + ROW_GAP;
        int previousX = compact ? pictureX() - ARROW - 2 : x;
        int nextX = compact ? pictureX() + pictureWidth + 2 : x + width - ARROW;
        Button previous = Button.builder(Component.translatable("kingdomworld.screen.style.previous"), b -> {
            select(index - 1);
            changed.run();
        }).bounds(previousX, rowY, ARROW, ARROW).build();
        Button next = Button.builder(Component.translatable("kingdomworld.screen.style.next"), b -> {
            select(index + 1);
            changed.run();
        }).bounds(nextX, rowY, ARROW, ARROW).build();
        return List.of(previous, next);
    }

    /**
     * Draws the picture of the shown style with its frame, the mark of the suggested style on its top, the name, and the caption
     * on hover over the picture.
     */
    void render(KingdomScreen screen, GuiGraphics graphics, Font font) {
        String id = id();
        int left = pictureX();
        int right = left + pictureWidth;
        graphics.renderOutline(left - 1, y - 1, pictureWidth + 2, pictureHeight + 2, FRAME);
        graphics.blit(picture(id), left, y, pictureWidth, pictureHeight, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        if (index == suggested) {
            graphics.fill(left, y, right, y + BAND, BAND_COLOR);
            screen.centeredLabel(graphics, Component.translatable("kingdomworld.screen.style.suggested"), left + pictureWidth / 2, y + 2,
                    0xFFE8C860, left, right);
        }
        if (compact) {
            graphics.fill(left, y + pictureHeight - BAND, right, y + pictureHeight, BAND_COLOR);
            screen.centeredLabel(graphics, name(id), left + pictureWidth / 2, y + pictureHeight - BAND + 2, 0xFFFFFFFF, left, right);
        } else {
            screen.centeredLabel(graphics, name(id), x + width / 2, y + pictureHeight + ROW_GAP + 4, 0xFFFFFFFF, x + ARROW + 2,
                    x + width - ARROW - 2);
        }
        List<Component> caption = captionLines(font, id);
        // A hover text answers a line of text, so the picture is covered by stripes of the height of one.
        for (int stripe = y; stripe < y + pictureHeight; stripe += KingdomScreen.LINE) {
            for (Component line : caption) {
                screen.hoverText(left, stripe + 1, pictureWidth, line);
            }
        }
    }
}
