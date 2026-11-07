package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.PartyHud;
import io.github.sekerator.kingdomworld.client.WantedHud;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The line of the wanted above the hotbar, shown as a screen together with the longest line of messages a guard sends and
 * the name of a held item where the game draws them, so that the layout check sees whether one covers another on every size
 * of window.
 */
public class WantedHudPreview extends KingdomScreen {
    /** Where the game draws the line of messages and the name of the held item, from the bottom of the screen. */
    private static final int ACTIONBAR = 72;
    private static final int ITEM_NAME = 59;

    private final String name;
    private final LawViews.WantedLine line;
    private final Component actionbar;
    private final Component item;

    public WantedHudPreview(String name, LawViews.WantedLine line, Component actionbar, Component item) {
        super(Component.empty());
        this.name = name;
        this.line = line;
        this.actionbar = actionbar;
        this.item = item;
    }

    @Override
    public String checkName() {
        return "law-hud-" + name;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PartyHud.Listener listener = (text, x, y, textWidth) -> {
            LayoutCheck.text(this, text, x, y, textWidth, 0, width);
            LayoutCheck.drawn(this, text, x, y, textWidth);
        };
        WantedHud.render(graphics, font, line, width, height, listener);
        int actionWidth = font.width(actionbar);
        graphics.drawString(font, actionbar, (width - actionWidth) / 2, height - ACTIONBAR, 0xFFFFFFFF);
        listener.drawn(actionbar, (width - actionWidth) / 2, height - ACTIONBAR, actionWidth);
        int itemWidth = font.width(item);
        graphics.drawString(font, item, (width - itemWidth) / 2, height - ITEM_NAME, 0xFFFFFFFF);
        listener.drawn(item, (width - itemWidth) / 2, height - ITEM_NAME, itemWidth);
    }
}
