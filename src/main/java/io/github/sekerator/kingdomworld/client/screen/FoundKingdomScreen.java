package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Founding a kingdom on an empty throne: a name, checked with the same rules the server uses, and a color.
 * The server checks everything again and answers with the throne screen or an error.
 */
public class FoundKingdomScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 194;
    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 2;
    private static final int STATUS_LINES = 3;

    private final BlockPos throne;
    private final String suggestedStyle;
    private int color;
    private int panelWidth;
    private EditBox nameBox;
    private Button foundButton;
    private String pendingName = "";
    private Component status = Component.empty();
    private boolean statusIsError;
    private boolean waiting;

    public FoundKingdomScreen(BlockPos throne, int suggestedColor) {
        this(throne, suggestedColor, "");
    }

    /**
     * @param suggestedStyle the architectural style the land of the throne suggests, or empty
     */
    public FoundKingdomScreen(BlockPos throne, int suggestedColor, String suggestedStyle) {
        super(Component.translatable("kingdomworld.screen.found.title"));
        this.throne = throne;
        this.color = suggestedColor;
        this.suggestedStyle = suggestedStyle == null ? "" : suggestedStyle;
    }

    @Override
    public String checkName() {
        return "founding";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static Component problemText(NameProblem problem) {
        return Component.translatable("kingdomworld.screen.found.problem." + problem.name().toLowerCase(Locale.ROOT),
                KingdomNames.MIN_LENGTH, KingdomNames.MAX_LENGTH);
    }

    @Override
    protected void init() {
        Component found = Component.translatable("kingdomworld.screen.found.confirm");
        Component cancel = Component.translatable("gui.cancel");
        int buttons = buttonWidth(found, cancel);
        int needed = Math.max(font.width(title), 2 * buttons + PAD);
        for (NameProblem problem : NameProblem.values()) {
            needed = Math.max(needed, font.width(problemText(problem)));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, needed + 2 * PAD + 4), width - 8);

        String previous = nameBox == null ? pendingName : nameBox.getValue();
        int left = left();
        int top = top();
        nameBox = new EditBox(font, left + PAD + 2, top + 35, panelWidth - 2 * PAD - 4, 18,
                Component.translatable("kingdomworld.screen.found.name"));
        nameBox.setMaxLength(FoundKingdomPacket.MAX_RAW_NAME);
        nameBox.setValue(previous);
        nameBox.setResponder(value -> updateButtons());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        foundButton = addRenderableWidget(Button.builder(found, button -> submit())
                .bounds(left + PAD + 2, top + PANEL_HEIGHT - 28, buttons, 20).build());
        addRenderableWidget(Button.builder(cancel, button -> onClose())
                .bounds(left + panelWidth - PAD - 2 - buttons, top + PANEL_HEIGHT - 28, buttons, 20).build());
        updateButtons();
    }

    /**
     * Puts a name and a server answer into the screen, used by the client self-check.
     */
    public void fillForCheck(String name, Component message, boolean error) {
        pendingName = name;
        if (nameBox != null) {
            nameBox.setValue(name);
        }
        status = message;
        statusIsError = error;
    }

    private Optional<NameProblem> problem() {
        return KingdomNames.validate(nameBox.getValue());
    }

    private void updateButtons() {
        if (foundButton != null) {
            foundButton.active = !waiting && problem().isEmpty();
        }
    }

    private void submit() {
        if (problem().isPresent()) {
            return;
        }
        waiting = true;
        status = Component.translatable("kingdomworld.screen.found.waiting");
        statusIsError = false;
        updateButtons();
        KingdomWorldNetwork.sendToServer(new FoundKingdomPacket(throne, KingdomNames.normalize(nameBox.getValue()), color, 0));
    }

    @Override
    public void onResult(boolean success, Component message) {
        waiting = false;
        status = message;
        statusIsError = !success;
        updateButtons();
    }

    private int swatchX(int index) {
        int perRow = KingdomColors.PALETTE.size() / 2;
        int rowWidth = perRow * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
        return left() + (panelWidth - rowWidth) / 2 + (index % perRow) * (SWATCH + SWATCH_GAP);
    }

    private int swatchY(int index) {
        int perRow = KingdomColors.PALETTE.size() / 2;
        return top() + 72 + (index / perRow) * (SWATCH + SWATCH_GAP);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < KingdomColors.PALETTE.size(); i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                color = KingdomColors.PALETTE.get(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        label(graphics, Component.translatable("kingdomworld.screen.found.name"), left + PAD + 2, top + 24, 0xFFE0E0E0, right - PAD);
        label(graphics, Component.translatable("kingdomworld.screen.found.color"), left + PAD + 2, top + 60, 0xFFE0E0E0, right - PAD);
        for (int i = 0; i < KingdomColors.PALETTE.size(); i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            int swatchColor = KingdomColors.PALETTE.get(i);
            if (swatchColor == color) {
                graphics.fill(x - 2, y - 2, x + SWATCH + 2, y + SWATCH + 2, 0xFFFFFFFF);
            }
            graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | swatchColor);
        }
        Optional<NameProblem> problem = problem();
        String name = KingdomNames.normalize(nameBox.getValue());
        if (problem.isPresent() && !name.isEmpty()) {
            centeredLabel(graphics, problemText(problem.get()), width / 2, top + 110, 0xFFFF8080, left + PAD, right - PAD);
        } else if (!name.isEmpty()) {
            // The preview stands on a dark plate so that every kingdom color stays readable.
            int previewWidth = font.width(fit(name, panelWidth - 2 * PAD - 8));
            graphics.fill(width / 2 - previewWidth / 2 - 3, top + 108, width / 2 + previewWidth / 2 + 3, top + 120, 0xFF100C08);
            centeredName(graphics, name, width / 2, top + 110, 0xFF000000 | readable(color), panelWidth - 2 * PAD - 8);
        }
        paragraph(graphics, status, left + PAD + 2, top + 124, panelWidth - 2 * PAD - 4, STATUS_LINES,
                statusIsError ? 0xFFFF8080 : 0xFFA0E0A0);
    }

    /**
     * Lifts dark kingdom colors so that text in them stays readable on dark panels.
     */
    static int readable(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        if (luminance >= 140) {
            return color & 0xFFFFFF;
        }
        int lift = 140 - luminance;
        return (Math.min(255, r + lift) << 16) | (Math.min(255, g + lift) << 8) | Math.min(255, b + lift);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
