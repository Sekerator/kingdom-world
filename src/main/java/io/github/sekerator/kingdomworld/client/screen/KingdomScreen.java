package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the mod's screens. Sizes come from the width of the translated texts, so layouts hold in every language:
 * buttons are as wide as their text plus a margin, translated labels are checked against the space they get, and
 * names of kingdoms and players (which can be long) are shortened with an ellipsis and shown in full on hover.
 */
public abstract class KingdomScreen extends Screen {
    protected static final int PAD = 8;
    protected static final int LINE = 10;
    private static final String ELLIPSIS = "…";

    private record Hover(int x, int y, int width, Component text) {
    }

    private final List<Hover> hovers = new ArrayList<>();

    protected KingdomScreen(Component title) {
        super(title);
    }

    /**
     * Name of the screen in layout check messages.
     */
    public abstract String checkName();

    /**
     * Draws the screen's own content; widgets and hover texts are drawn afterwards.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovers.clear();
        renderContent(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        LayoutCheck.widgets(this, font);
        for (Hover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + hover.width() && mouseY >= hover.y() - 1 && mouseY < hover.y() + LINE) {
                graphics.renderTooltip(font, hover.text(), mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * Width of a button that shows the widest of the texts without scrolling.
     */
    protected int buttonWidth(Component... texts) {
        int width = 0;
        for (Component text : texts) {
            width = Math.max(width, font.width(text));
        }
        return width + LayoutCheck.BUTTON_TEXT_MARGIN + 8;
    }

    /**
     * A translated text that must fit between {@code x} and {@code right}; a misfit is reported by the layout check.
     */
    protected void label(GuiGraphics graphics, Component text, int x, int y, int color, int right) {
        graphics.drawString(font, text, x, y, color);
        LayoutCheck.text(this, text, x, y, font.width(text), x, right);
    }

    /**
     * A translated text centered on {@code centerX} that must stay between {@code left} and {@code right}.
     */
    protected void centeredLabel(GuiGraphics graphics, Component text, int centerX, int y, int color, int left, int right) {
        int width = font.width(text);
        int x = centerX - width / 2;
        graphics.drawString(font, text, x, y, color);
        LayoutCheck.text(this, text, x, y, width, left, right);
    }

    /**
     * Shortens a name with an ellipsis so that it is at most {@code maxWidth} wide.
     */
    protected String fit(String name, int maxWidth) {
        if (font.width(name) <= maxWidth) {
            return name;
        }
        return font.plainSubstrByWidth(name, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * Draws a name, shortened to {@code maxWidth}; when shortened, hovering shows it in full.
     *
     * @return the drawn width
     */
    protected int name(GuiGraphics graphics, String name, int x, int y, int color, int maxWidth) {
        String shown = fit(name, maxWidth);
        graphics.drawString(font, shown, x, y, color);
        int width = font.width(shown);
        if (!shown.equals(name)) {
            hovers.add(new Hover(x, y, width, Component.literal(name)));
        }
        return width;
    }

    /**
     * Shows a text in a tooltip while the mouse is over a line drawn at {@code x}, {@code y}.
     */
    protected void hoverText(int x, int y, int width, Component text) {
        hovers.add(new Hover(x, y, width, text));
    }

    /**
     * Draws a name centered on {@code centerX}, shortened to {@code maxWidth}.
     */
    protected void centeredName(GuiGraphics graphics, String name, int centerX, int y, int color, int maxWidth) {
        int width = font.width(fit(name, maxWidth));
        name(graphics, name, centerX - width / 2, y, color, maxWidth);
    }

    /**
     * Draws a text wrapped to {@code width}; a text that needs more than {@code maxLines} is reported as cut off.
     *
     * @return the number of lines drawn
     */
    protected int paragraph(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        if (text.getString().isEmpty()) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        LayoutCheck.lines(this, text, lines.size(), maxLines);
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            if (drawn == maxLines) {
                break;
            }
            graphics.drawString(font, line, x, y + drawn * LINE, color);
            drawn++;
        }
        return drawn;
    }
}
