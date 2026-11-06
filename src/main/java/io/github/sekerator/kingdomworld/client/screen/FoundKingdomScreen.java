package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Locale;
import java.util.Optional;

/**
 * Founding a kingdom on an empty throne: a name, checked with the same rules the server uses, and a color.
 * The server checks everything again and answers with the throne screen or an error.
 */
public class FoundKingdomScreen extends Screen implements ResultListener {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 180;
    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 2;

    private final BlockPos throne;
    private int color;
    private EditBox nameBox;
    private Button foundButton;
    private Component status = Component.empty();
    private boolean statusIsError;
    private boolean waiting;

    public FoundKingdomScreen(BlockPos throne, int suggestedColor) {
        super(Component.translatable("kingdomworld.screen.found.title"));
        this.throne = throne;
        this.color = suggestedColor;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        String previous = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(font, left() + 10, top() + 36, PANEL_WIDTH - 20, 18, Component.translatable("kingdomworld.screen.found.name"));
        nameBox.setMaxLength(FoundKingdomPacket.MAX_RAW_NAME);
        nameBox.setValue(previous);
        nameBox.setResponder(value -> updateButtons());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        foundButton = addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.found.confirm"), button -> submit())
                .bounds(left() + 10, top() + PANEL_HEIGHT - 28, 115, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left() + PANEL_WIDTH - 125, top() + PANEL_HEIGHT - 28, 115, 20).build());
        updateButtons();
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
        KingdomWorldNetwork.sendToServer(new FoundKingdomPacket(throne, KingdomNames.normalize(nameBox.getValue()), color));
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
        return left() + (PANEL_WIDTH - rowWidth) / 2 + (index % perRow) * (SWATCH + SWATCH_GAP);
    }

    private int swatchY(int index) {
        int perRow = KingdomColors.PALETTE.size() / 2;
        return top() + 76 + (index / perRow) * (SWATCH + SWATCH_GAP);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0201810);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC8A040);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xFFE8C860);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.found.name"), left + 10, top + 25, 0xFFD0D0D0);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.found.color"), left + 10, top + 64, 0xFFD0D0D0);
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
            graphics.drawCenteredString(font, Component.translatable("kingdomworld.screen.found.problem."
                    + problem.get().name().toLowerCase(Locale.ROOT), KingdomNames.MIN_LENGTH, KingdomNames.MAX_LENGTH),
                    width / 2, top + 112, 0xFFFF7070);
        } else if (!name.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal(name), width / 2, top + 112, 0xFF000000 | color);
        }
        int lineY = top + 124;
        for (FormattedCharSequence line : font.split(status, PANEL_WIDTH - 20)) {
            if (lineY > top + PANEL_HEIGHT - 40) {
                break;
            }
            graphics.drawCenteredString(font, line, width / 2, lineY, statusIsError ? 0xFFFF7070 : 0xFFA0E0A0);
            lineY += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
