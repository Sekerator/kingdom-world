package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.road.City;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A new name for a city of the kingdom, from its actions in the roads tab; the server checks the right and the name and answers
 * in the throne screen.
 */
public class RenameCityScreen extends KingdomScreen {
    private static final int MIN_PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 96;
    private static final String KEY = "kingdomworld.screen.rename_city.";

    @Nullable
    private final Screen parent;
    private final UUID kingdomId;
    private final long cityId;
    private final String oldName;
    private int panelWidth;
    private EditBox nameBox;
    private Button saveButton;

    public RenameCityScreen(@Nullable Screen parent, UUID kingdomId, long cityId, String oldName) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.kingdomId = kingdomId;
        this.cityId = cityId;
        this.oldName = oldName;
    }

    @Override
    public String checkName() {
        return "rename-city";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        Component save = Component.translatable("kingdomworld.screen.rename.save");
        Component cancel = Component.translatable("gui.cancel");
        int buttons = buttonWidth(save, cancel);
        int needed = Math.max(font.width(title), 2 * buttons + PAD);
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, needed + 2 * PAD + 4), width - 8);
        String previous = nameBox == null ? oldName : nameBox.getValue();
        int left = left();
        int top = top();
        nameBox = new EditBox(font, left + PAD + 2, top + 26, panelWidth - 2 * PAD - 4, 18, title);
        nameBox.setMaxLength(City.MAX_NAME);
        nameBox.setValue(previous);
        nameBox.setResponder(value -> updateButtons());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        saveButton = addRenderableWidget(Button.builder(save, button -> submit())
                .bounds(left + PAD + 2, top + PANEL_HEIGHT - 26, buttons, 20).build());
        addRenderableWidget(Button.builder(cancel, button -> onClose())
                .bounds(left + panelWidth - PAD - 2 - buttons, top + PANEL_HEIGHT - 26, buttons, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        String name = nameBox.getValue().strip();
        saveButton.active = !name.isEmpty() && name.length() <= City.MAX_NAME && !name.equals(oldName);
    }

    private void submit() {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new RoadPackets.TabChange(kingdomId, RoadPackets.TabChange.Action.RENAME_CITY, cityId, 0,
                    nameBox.getValue().strip()));
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        graphics.fill(left, top, right, top + PANEL_HEIGHT, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, PANEL_HEIGHT, 0xFFC8A040);
        centeredLabel(graphics, title, width / 2, top + 9, 0xFFE8C860, left + PAD, right - PAD);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
