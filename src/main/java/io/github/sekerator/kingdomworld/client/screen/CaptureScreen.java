package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets;
import io.github.sekerator.kingdomworld.core.construction.BuildingRole;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Saving the building between the corners of the architect's ruler as a blueprint of the kingdom: its name, category and
 * role. The server reads the blocks itself and checks the role (a house needs a bed, a warehouse a container); it answers
 * here.
 */
public class CaptureScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 168;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.capture.";

    private final ArchitectRulerItem.Corners corners;
    private final List<String> roles = new ArrayList<>();
    private int category;
    private int role;
    private int panelWidth;
    private EditBox nameBox;
    private Button saveButton;
    private String pendingName = "";
    private Component status = Component.empty();
    private boolean statusIsError;
    private boolean waiting;

    public CaptureScreen(ArchitectRulerItem.Corners corners) {
        super(Component.translatable(KEY + "title"));
        this.corners = corners;
        roles.add("");
        for (BuildingRole known : BuildingRoles.all()) {
            roles.add(known.id());
        }
    }

    @Override
    public String checkName() {
        return "capture";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static Component categoryLabel(BuildingTemplate.Category value) {
        return Component.translatable(KEY + "category", Component.translatable("kingdomworld.building.category." + value.id()));
    }

    private static Component roleLabel(String value) {
        return Component.translatable(KEY + "role", value.isEmpty() ? Component.translatable(KEY + "no_role")
                : Component.translatable("kingdomworld.building.role." + value));
    }

    private Component sizeText() {
        int[] size = corners.size();
        return Component.translatable(KEY + "size", size[0], size[1], size[2]);
    }

    @Override
    protected void init() {
        Component save = Component.translatable(KEY + "save");
        Component cancel = Component.translatable("gui.cancel");
        int buttons = buttonWidth(save, cancel);
        int cycle = 0;
        for (BuildingTemplate.Category value : BuildingTemplate.Category.values()) {
            cycle = Math.max(cycle, buttonWidth(categoryLabel(value)));
        }
        for (String value : roles) {
            cycle = Math.max(cycle, buttonWidth(roleLabel(value)));
        }
        int needed = Math.max(Math.max(font.width(title), font.width(sizeText())), Math.max(cycle, 2 * buttons + PAD));
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, needed + 2 * PAD + 4), width - 8);
        String previous = nameBox == null ? pendingName : nameBox.getValue();
        int left = left();
        int top = top();
        int inner = panelWidth - 2 * PAD - 4;
        nameBox = new EditBox(font, left + PAD + 2, top + 44, inner, 18, Component.translatable(KEY + "name"));
        nameBox.setMaxLength(CustomBlueprint.MAX_NAME);
        nameBox.setValue(previous);
        nameBox.setResponder(value -> updateButtons());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(categoryLabel(BuildingTemplate.Category.values()[category]), button -> {
            category = (category + 1) % BuildingTemplate.Category.values().length;
            rebuildWidgets();
        }).bounds(left + PAD + 2, top + 68, inner, 20).build());
        addRenderableWidget(Button.builder(roleLabel(roles.get(role)), button -> {
            role = (role + 1) % roles.size();
            rebuildWidgets();
        }).bounds(left + PAD + 2, top + 92, inner, 20).build());
        saveButton = addRenderableWidget(Button.builder(save, button -> submit())
                .bounds(left + PAD + 2, top + PANEL_HEIGHT - 26, buttons, 20).build());
        addRenderableWidget(Button.builder(cancel, button -> onClose())
                .bounds(left + panelWidth - PAD - 2 - buttons, top + PANEL_HEIGHT - 26, buttons, 20).build());
        updateButtons();
    }

    /**
     * Fills the screen for the layout check: a name, a role and a server answer.
     */
    public void fillForCheck(String name, int roleIndex, Component message, boolean error) {
        pendingName = name;
        role = Math.max(0, Math.min(roles.size() - 1, roleIndex));
        if (nameBox != null) {
            nameBox.setValue(name);
        }
        status = message;
        statusIsError = error;
    }

    private void updateButtons() {
        if (saveButton != null) {
            String name = nameBox.getValue().strip();
            saveButton.active = !waiting && !name.isEmpty() && name.length() <= CustomBlueprint.MAX_NAME;
        }
    }

    private void submit() {
        waiting = true;
        status = Component.translatable(KEY + "waiting");
        statusIsError = false;
        updateButtons();
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new BuildingPackets.Capture(nameBox.getValue().strip(),
                    BuildingTemplate.Category.values()[category].id(), roles.get(role)));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        waiting = false;
        status = message;
        statusIsError = !success;
        updateButtons();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        graphics.fill(left, top, right, top + PANEL_HEIGHT, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, PANEL_HEIGHT, 0xFFC8A040);
        centeredLabel(graphics, title, width / 2, top + 8, 0xFFE8C860, left + PAD, right - PAD);
        label(graphics, sizeText(), left + PAD + 2, top + 21, 0xFFD0D0D0, right - PAD);
        label(graphics, Component.translatable(KEY + "name"), left + PAD + 2, top + 33, 0xFFE0E0E0, right - PAD);
        paragraph(graphics, status, left + PAD + 2, top + 116, panelWidth - 2 * PAD - 4, STATUS_LINES,
                statusIsError ? 0xFFFF8080 : 0xFFA0E0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
