package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.storage.StorehouseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The window of a double storehouse: both halves side by side, eighteen slots a row in six rows, with the player's
 * inventory under them in the middle. A long title is shortened and shown in full on hover.
 */
public class StorehouseScreen extends AbstractContainerScreen<StorehouseMenu> {
    private static final String ELLIPSIS = "…";
    private static final int TEXT = 0xFF404040;

    public StorehouseScreen(StorehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = StorehouseMenu.WIDTH;
        this.imageHeight = StorehouseMenu.HEIGHT;
        this.inventoryLabelX = StorehouseMenu.INVENTORY_LEFT - 1;
        this.inventoryLabelY = StorehouseMenu.INVENTORY_TOP - 11;
    }

    public String checkName() {
        return "storehouse-double";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (titleShortened() && mouseX >= leftPos + titleLabelX && mouseX < leftPos + titleLabelX + titleRoom() && mouseY >= topPos + titleLabelY - 1
                && mouseY < topPos + titleLabelY + font.lineHeight) {
            graphics.renderTooltip(font, title, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF000000);
        // The seam between the halves, as faint as the seam of the blocks.
        int seam = leftPos + 8 + StorehouseMenu.HALF_COLUMNS * StorehouseMenu.SLOT - 1;
        graphics.fill(seam, topPos + StorehouseMenu.STORE_TOP - 2, seam + 1,
                topPos + StorehouseMenu.STORE_TOP + StorehouseMenu.ROWS * StorehouseMenu.SLOT + 1, 0xFF9A9A9A);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
            graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
    }

    private int titleRoom() {
        return imageWidth - 2 * titleLabelX;
    }

    private boolean titleShortened() {
        return font.width(title) > titleRoom();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String shown = title.getString();
        if (titleShortened()) {
            shown = font.plainSubstrByWidth(shown, Math.max(0, titleRoom() - font.width(ELLIPSIS))) + ELLIPSIS;
        }
        graphics.drawString(font, shown, titleLabelX, titleLabelY, TEXT, false);
        LayoutCheck.drawn(this, Component.literal(shown), leftPos + titleLabelX, topPos + titleLabelY, font.width(shown));
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        LayoutCheck.text(this, playerInventoryTitle, leftPos + inventoryLabelX, topPos + inventoryLabelY, font.width(playerInventoryTitle),
                leftPos + inventoryLabelX, leftPos + imageWidth - 8);
        LayoutCheck.drawn(this, playerInventoryTitle, leftPos + inventoryLabelX, topPos + inventoryLabelY, font.width(playerInventoryTitle));
    }
}
