package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.storage.StorehouseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The window of a storehouse: six rows of nine slots, or both halves of a double storehouse side by side, eighteen slots a row;
 * under them the line of whom the storehouse takes in from (a button that opens the setting for one who may change it), and the
 * player's inventory under that in the middle. A long title or line is shortened and shown in full on hover.
 */
public class StorehouseScreen extends AbstractContainerScreen<StorehouseMenu> {
    private static final String ELLIPSIS = "…";
    private static final int TEXT = 0xFF404040;
    private static final String KEY = "kingdomworld.storehouse.accepts.";

    private Button settingsButton;

    public StorehouseScreen(StorehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = StorehouseMenu.width(menu.halves());
        this.imageHeight = StorehouseMenu.HEIGHT;
        this.inventoryLabelX = StorehouseMenu.inventoryLeft(menu.halves()) - 1;
        this.inventoryLabelY = StorehouseMenu.INVENTORY_TOP - 11;
    }

    public String checkName() {
        return menu.halves() == 2 ? "storehouse-double" : "storehouse";
    }

    /**
     * Whom the storehouse takes in from, in a few words: everybody, or the professions it was given.
     */
    static Component acceptsLine(List<String> professions) {
        if (professions.isEmpty()) {
            return Component.translatable(KEY + "all");
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < professions.size(); i++) {
            if (i > 0) {
                names.append(Component.translatable(KEY + "separator"));
            }
            names.append(CitizenTexts.profession(professions.get(i)));
        }
        return Component.translatable(KEY + "some", names);
    }

    /**
     * The whole setting, one profession a line, for the hover.
     */
    static List<Component> acceptsLines(List<String> professions) {
        List<Component> lines = new ArrayList<>();
        if (professions.isEmpty()) {
            lines.add(Component.translatable(KEY + "all_hint"));
            return lines;
        }
        lines.add(Component.translatable(KEY + "some_hint"));
        for (String profession : professions) {
            lines.add(Component.literal("  ").append(CitizenTexts.profession(profession)));
        }
        return lines;
    }

    private int lineRoom() {
        return imageWidth - 16;
    }

    private String fitted(Component text, int room) {
        String shown = text.getString();
        if (font.width(shown) > room) {
            shown = font.plainSubstrByWidth(shown, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
        }
        return shown;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.settings().canEdit()) {
            Component line = acceptsLine(menu.settings().accepts());
            settingsButton = addRenderableWidget(Button.builder(Component.literal(fitted(line, lineRoom() - 8)), button -> openSettings())
                    .bounds(leftPos + 8, topPos + StorehouseMenu.SETTINGS_TOP, lineRoom(), StorehouseMenu.SETTINGS_HEIGHT).build());
        } else {
            settingsButton = null;
        }
    }

    private void openSettings() {
        if (minecraft != null) {
            // The window of the storehouse stays open on the server: the setting comes back to it, and the server opens it again
            // with the new setting.
            StorehouseMenu.Settings settings = menu.settings();
            minecraft.setScreen(new StorehouseAcceptsScreen(this, settings.pos(), settings.accepts()));
        }
    }

    private boolean overLine(int mouseX, int mouseY) {
        int y = topPos + StorehouseMenu.SETTINGS_TOP;
        return mouseX >= leftPos + 8 && mouseX < leftPos + 8 + lineRoom() && mouseY >= y && mouseY < y + StorehouseMenu.SETTINGS_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (titleShortened() && mouseX >= leftPos + titleLabelX && mouseX < leftPos + titleLabelX + titleRoom() && mouseY >= topPos + titleLabelY - 1
                && mouseY < topPos + titleLabelY + font.lineHeight) {
            graphics.renderTooltip(font, title, mouseX, mouseY);
        } else if (overLine(mouseX, mouseY)) {
            List<Component> lines = acceptsLines(menu.settings().accepts());
            if (menu.settings().canEdit()) {
                lines.add(Component.translatable(KEY + "change_hint"));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF000000);
        if (menu.halves() == 2) {
            // The seam between the halves, as faint as the seam of the blocks.
            int seam = leftPos + 8 + StorehouseMenu.HALF_COLUMNS * StorehouseMenu.SLOT - 1;
            graphics.fill(seam, topPos + StorehouseMenu.STORE_TOP - 2, seam + 1,
                    topPos + StorehouseMenu.STORE_TOP + StorehouseMenu.ROWS * StorehouseMenu.SLOT + 1, 0xFF9A9A9A);
        }
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
        String shown = fitted(title, titleRoom());
        graphics.drawString(font, shown, titleLabelX, titleLabelY, TEXT, false);
        LayoutCheck.drawn(this, Component.literal(shown), leftPos + titleLabelX, topPos + titleLabelY, font.width(shown));
        if (settingsButton == null) {
            // Whom the storehouse takes in from, for one who may not change it.
            String line = fitted(acceptsLine(menu.settings().accepts()), lineRoom());
            graphics.drawString(font, line, 8, StorehouseMenu.SETTINGS_TOP + 2, TEXT, false);
            LayoutCheck.drawn(this, Component.literal(line), leftPos + 8, topPos + StorehouseMenu.SETTINGS_TOP + 2, font.width(line));
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        LayoutCheck.text(this, playerInventoryTitle, leftPos + inventoryLabelX, topPos + inventoryLabelY, font.width(playerInventoryTitle),
                leftPos + inventoryLabelX, leftPos + imageWidth - 8);
        LayoutCheck.drawn(this, playerInventoryTitle, leftPos + inventoryLabelX, topPos + inventoryLabelY, font.width(playerInventoryTitle));
    }
}
