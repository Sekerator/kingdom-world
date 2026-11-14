package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A name for a work post, from the window of the workers; an empty name gives the post its default one (its profession and
 * number) back. The server checks the right and the name and answers in the window of the workers.
 */
public class RenamePostScreen extends KingdomScreen {
    private static final int MIN_PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 108;
    private static final String KEY = "kingdomworld.screen.workforce.rename.";

    private final WorkforceScreen parent;
    private final WorkforceView.PostRef post;
    private final String oldName;
    private final String defaultName;
    private int panelWidth;
    private EditBox nameBox;
    private Button saveButton;

    /**
     * @param defaultName the name the post has without one of its own, shown as the hint of an empty field
     */
    public RenamePostScreen(WorkforceScreen parent, WorkforceView.PostRef post, String oldName, String defaultName) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.post = post;
        this.oldName = oldName;
        this.defaultName = defaultName;
    }

    @Override
    public String checkName() {
        return "rename-post";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private Component hint() {
        return Component.translatable(KEY + "hint", defaultName);
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
        nameBox = new EditBox(font, left + PAD + 2, top + 24, panelWidth - 2 * PAD - 4, 18, title);
        nameBox.setMaxLength(WorkPost.MAX_NAME_LENGTH);
        nameBox.setValue(previous);
        nameBox.setHint(Component.literal(defaultName));
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
        saveButton.active = WorkPost.isValidName(name) && !name.equals(oldName);
    }

    private void submit() {
        String name = nameBox.getValue().strip();
        onClose();
        parent.rename(post, name);
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
        paragraph(graphics, hint(), left + PAD + 2, top + 48, panelWidth - 2 * PAD - 4, 3, 0xFFB8B8B8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
