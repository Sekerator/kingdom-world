package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Shows the tooltips of some items one under another, for the layout check: every line of a tooltip must fit the
 * narrowest screen. Never opened in play.
 */
public class ItemTooltipScreen extends KingdomScreen {
    private static final int TOOLTIP_MARGIN = 8;
    private final String name;
    private final List<ItemStack> stacks;

    public ItemTooltipScreen(String name, List<ItemStack> stacks) {
        super(Component.literal(name));
        this.name = name;
        this.stacks = List.copyOf(stacks);
    }

    @Override
    public String checkName() {
        return "tooltip/" + name;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int y = 6;
        for (ItemStack stack : stacks) {
            List<Component> lines = minecraft == null ? List.of() : Screen.getTooltipFromItem(minecraft, stack);
            if (minecraft != null && lines.isEmpty()) {
                lines = stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
            }
            graphics.renderItem(stack, 4, y);
            int x = 24;
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                // A tooltip draws its lines from its left edge with a margin on both sides.
                LayoutCheck.text(this, line, x, y + i * LINE, font.width(line), 0, width - TOOLTIP_MARGIN);
            }
            graphics.renderComponentTooltip(font, lines, x - 12, y + 12);
            y += Math.max(24, lines.size() * LINE + 14);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
