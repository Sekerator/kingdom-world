package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * The line above the hotbar while the player stands on the land of a kingdom that wants them: the kingdom and the fine, or
 * the death a murder costs. It sits above the line of messages and the name of the held item, so that none of them covers
 * another, and fits the narrowest window.
 */
public final class WantedHud {
    /** How far above the bottom of the screen the line is drawn, above the messages of the game. */
    public static final int OFFSET = 84;
    private static LawViews.WantedLine line = LawViews.WantedLine.NONE;

    private WantedHud() {
    }

    public static void setLine(LawViews.WantedLine value) {
        line = value == null ? LawViews.WantedLine.NONE : value;
    }

    public static LawViews.WantedLine line() {
        return line;
    }

    public static void clear() {
        line = LawViews.WantedLine.NONE;
    }

    /**
     * The text of a line.
     */
    public static Component text(LawViews.WantedLine shown) {
        Component kingdom = Component.literal(shown.kingdomName()).withStyle(style -> style.withColor(TextColor.fromRgb(shown.color() & 0xFFFFFF)));
        CurrencyUnits units = CurrencyUnits.valid(shown.silver(), shown.gold()) ? new CurrencyUnits(shown.silver(), shown.gold())
                : CurrencyUnits.DEFAULT;
        return Component.translatable("kingdomworld.law.hud", kingdom, LawTexts.punishmentShort(shown.execution(), shown.fine(), units));
    }

    /**
     * Draws the line from the game's overlay.
     */
    static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!line.shown() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        render(graphics, minecraft.font, line, width, height, PartyHud.Listener.NONE);
    }

    /**
     * Draws a line for a screen of a size.
     */
    public static void render(GuiGraphics graphics, Font font, LawViews.WantedLine shown, int width, int height, PartyHud.Listener listener) {
        Component text = text(shown);
        int textWidth = font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - OFFSET;
        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + 10, 0x80000000);
        graphics.drawString(font, text, x, y, 0xFFFF6060);
        listener.drawn(text, x, y, textWidth);
    }
}
