package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A new name for a blueprint the kingdom saved; the server checks the right and the name and answers in the throne screen.
 */
public class RenameBlueprintScreen extends KingdomScreen {
    private static final int MIN_PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 96;
    private static final String KEY = "kingdomworld.screen.rename.";

    @Nullable
    private final Screen parent;
    private final UUID kingdomId;
    private final long blueprint;
    private final String oldName;
    private int panelWidth;
    private EditBox nameBox;
    private Button saveButton;

    public RenameBlueprintScreen(@Nullable Screen parent, UUID kingdomId, long blueprint, String oldName) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.kingdomId = kingdomId;
        this.blueprint = blueprint;
        this.oldName = oldName;
    }

    @Override
    public String checkName() {
        return "rename-blueprint";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        Component save = Component.translatable(KEY + "save");
        Component cancel = Component.translatable("gui.cancel");
        int buttons = buttonWidth(save, cancel);
        int needed = Math.max(font.width(title), 2 * buttons + PAD);
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, needed + 2 * PAD + 4), width - 8);
        String previous = nameBox == null ? oldName : nameBox.getValue();
        int left = left();
        int top = top();
        nameBox = new EditBox(font, left + PAD + 2, top + 26, panelWidth - 2 * PAD - 4, 18, title);
        nameBox.setMaxLength(CustomBlueprint.MAX_NAME);
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
        saveButton.active = !name.isEmpty() && name.length() <= CustomBlueprint.MAX_NAME && !name.equals(oldName);
    }

    private void submit() {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new BuildingPackets.BlueprintChange(kingdomId, BuildingPackets.BlueprintChange.Action.RENAME, blueprint,
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
