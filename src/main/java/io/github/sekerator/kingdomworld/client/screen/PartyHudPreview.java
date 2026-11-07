package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.PartyHud;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The list of the party in the corner of the screen, shown as a screen so that the layout check can measure it on every
 * size of window.
 */
public class PartyHudPreview extends KingdomScreen {
    private final String name;
    private final List<CompanionViews.HudLine> lines;

    public PartyHudPreview(String name, List<CompanionViews.HudLine> lines) {
        super(Component.empty());
        this.name = name;
        this.lines = List.copyOf(lines);
    }

    @Override
    public String checkName() {
        return "party-hud-" + name;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PartyHud.render(graphics, font, lines, width, height, (text, x, y, textWidth) -> {
            LayoutCheck.text(this, text, x, y, textWidth, 0, width);
            LayoutCheck.drawn(this, text, x, y, textWidth);
        });
    }
}
