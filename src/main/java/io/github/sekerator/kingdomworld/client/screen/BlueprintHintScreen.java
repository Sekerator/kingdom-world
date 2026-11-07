package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The hints of a blueprint in hand drawn on an empty screen, so that the layout check measures them like any screen: the
 * same box and the same wrapped lines the game draws above the world.
 */
public final class BlueprintHintScreen extends KingdomScreen {
    private final String name;
    private final List<BlueprintRenderer.Line> lines;

    public BlueprintHintScreen(String name, List<BlueprintRenderer.Line> lines) {
        super(Component.empty());
        this.name = name;
        this.lines = List.copyOf(lines);
    }

    @Override
    public String checkName() {
        return "blueprint/" + name;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BlueprintRenderer.Layout layout = BlueprintRenderer.layout(font, width, lines);
        graphics.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), 0xA0000000);
        int y = layout.y() + 4;
        for (BlueprintRenderer.Line line : lines) {
            y += paragraph(graphics, line.text(), layout.x() + 4, y, layout.width() - 8, 8, line.color()) * LINE;
        }
    }
}
