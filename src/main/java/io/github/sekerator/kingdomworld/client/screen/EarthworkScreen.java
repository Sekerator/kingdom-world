package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.EarthworkPackets;
import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * The window of earthworks over the rectangle the survey stakes marked: what the builders do (level to a height, cut down to
 * it or fill up to it), the height, whether the edges slope to the land around; the server answers what the land would take
 * (blocks to dig and to fill, earth that is lacking, places left as they are, what is left over) and lays the work out.
 */
public class EarthworkScreen extends KingdomScreen {
    private static final String KEY = "kingdomworld.screen.earthwork.";
    private static final int MIN_PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 222;
    /** Ticks after a change before the server is asked again, so that a height typed or nudged several times asks once. */
    private static final int ASK_DELAY = 12;

    private final BlockPos first;
    private final BlockPos second;
    private Earthwork.Mode mode = Earthwork.Mode.LEVEL;
    private int target;
    private boolean smooth = true;
    private int panelWidth;
    private EditBox heightBox;
    private Checkbox smoothBox;
    private Button layButton;
    private int askIn = 1;
    private boolean waiting;
    @Nullable
    private EarthworkPackets.Result result;
    /** The choice asked of the server last, and the last answer with its choice. */
    private String asked = "";
    private String answeredChoice = "";
    @Nullable
    private EarthworkPackets.Result answered;

    public EarthworkScreen(ArchitectRulerItem.Corners corners) {
        super(Component.translatable(KEY + "title"));
        this.first = corners.first();
        this.second = corners.second();
        // The height of the first corner: the ground there is where the work begins.
        this.target = first.getY();
    }

    @Override
    public String checkName() {
        return "earthwork";
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private Component modeLabel() {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + mode.id()));
    }

    private Component sizeText() {
        return Component.translatable(KEY + "size", Math.abs(first.getX() - second.getX()) + 1, Math.abs(first.getZ() - second.getZ()) + 1);
    }

    @Override
    protected void init() {
        Component lay = Component.translatable(KEY + "lay");
        Component close = Component.translatable("gui.cancel");
        int buttons = buttonWidth(lay, close);
        int cycle = 0;
        for (Earthwork.Mode value : Earthwork.Mode.values()) {
            cycle = Math.max(cycle, font.width(Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()))));
        }
        int needed = Math.max(Math.max(font.width(title), font.width(sizeText())), Math.max(cycle + 16, 2 * buttons + PAD));
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, needed + 2 * PAD + 4), width - 8);
        int left = left();
        int top = top();
        int inner = panelWidth - 2 * PAD - 4;
        int x = left + PAD + 2;
        addRenderableWidget(new CycleButton(x, top + 34, inner, 20, modeLabel(), direction -> {
            mode = Earthwork.Mode.values()[Cycle.step(mode.ordinal(), direction, Earthwork.Mode.values().length)];
            changed();
            rebuildWidgets();
        }));
        Component heightLabel = Component.translatable(KEY + "height");
        int labelWidth = font.width(heightLabel) + 6;
        String previous = heightBox == null ? Integer.toString(target) : heightBox.getValue();
        addRenderableWidget(Button.builder(Component.literal("-"), button -> nudge(-1)).bounds(x + labelWidth, top + 59, 20, 20).build());
        heightBox = new EditBox(font, x + labelWidth + 23, top + 60, 44, 18, heightLabel);
        heightBox.setMaxLength(5);
        heightBox.setFilter(value -> value.matches("-?\\d{0,4}"));
        heightBox.setValue(previous);
        heightBox.setResponder(value -> {
            try {
                target = Integer.parseInt(value);
                changed();
            } catch (NumberFormatException e) {
                // Not a height yet: the last one stays.
            }
        });
        addRenderableWidget(heightBox);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> nudge(1)).bounds(x + labelWidth + 70, top + 59, 20, 20).build());
        smoothBox = new Checkbox(x, top + 83, inner, 20, Component.translatable(KEY + "smooth"), smooth) {
            @Override
            public void onPress() {
                super.onPress();
                smooth = selected();
                changed();
            }
        };
        addRenderableWidget(smoothBox);
        layButton = addRenderableWidget(Button.builder(lay, button -> submit()).bounds(x, top + PANEL_HEIGHT - 26, buttons, 20).build());
        addRenderableWidget(Button.builder(close, button -> onClose()).bounds(left + panelWidth - PAD - 2 - buttons, top + PANEL_HEIGHT - 26, buttons, 20)
                .build());
        updateButtons();
    }

    private void nudge(int by) {
        target += by;
        if (heightBox != null) {
            heightBox.setValue(Integer.toString(target));
        }
        changed();
    }

    private void changed() {
        askIn = ASK_DELAY;
        result = null;
        updateButtons();
    }

    private void updateButtons() {
        if (layButton != null) {
            layButton.active = !waiting && result != null && result.ok() && !result.laid();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (heightBox != null) {
            heightBox.tick();
        }
        if (askIn > 0 && --askIn == 0) {
            // Only a choice that differs from the one asked last goes to the server.
            if (choice().equals(answeredChoice) && answered != null) {
                result = answered;
                updateButtons();
            } else if (!choice().equals(asked)) {
                ask(false);
            }
        }
    }

    private String choice() {
        return mode.ordinal() + ":" + target + ":" + smooth;
    }

    private void ask(boolean lay) {
        asked = choice();
        waiting = lay;
        updateButtons();
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EarthworkPackets.Survey(first, second, mode.ordinal(), target, smooth, lay));
        }
    }

    private void submit() {
        ask(true);
    }

    /**
     * The server's answer: shown, and the window closes once the work is laid out.
     */
    public void accept(EarthworkPackets.Result answer) {
        if (!answer.first().equals(first) || !answer.second().equals(second)) {
            return;
        }
        waiting = false;
        if (answer.laid()) {
            Minecraft.getInstance().setScreen(null);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(answer.message(), true);
            }
            return;
        }
        if (answer.target() == target && choice().equals(asked)) {
            result = answer;
            answered = answer;
            answeredChoice = asked;
        }
        updateButtons();
    }

    /**
     * Fills the window for the layout check and the screenshots: what the server would answer.
     */
    public void fillForCheck(Earthwork.Mode value, int height, boolean slopes, EarthworkPackets.Result answer) {
        mode = value;
        target = height;
        smooth = slopes;
        askIn = 0;
        if (heightBox != null) {
            heightBox.setValue(Integer.toString(height));
        }
        result = answer;
        rebuildWidgets();
        updateButtons();
    }

    private static Component itemName(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return item == null ? Component.literal(id) : item.getDescription();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        int x = left + PAD + 2;
        graphics.fill(left, top, right, top + PANEL_HEIGHT, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, PANEL_HEIGHT, 0xFFC8A040);
        centeredLabel(graphics, title, width / 2, top + 8, 0xFFE8C860, left + PAD, right - PAD);
        fittedLabel(graphics, sizeText(), x, top + 21, 0xFFD0D0D0, right - PAD);
        label(graphics, Component.translatable(KEY + "height"), x, top + 65, 0xFFE0E0E0, right - PAD);
        int y = top + 110;
        if (result == null) {
            label(graphics, Component.translatable(KEY + "counting"), x, y, 0xFFA0A0A0, right - PAD);
            return;
        }
        if (!result.ok() && result.digs() == 0 && result.fills() == 0) {
            paragraph(graphics, result.message(), x, y, panelWidth - 2 * PAD - 4, 4, 0xFFFF8080);
            return;
        }
        fittedLabel(graphics, Component.translatable(KEY + "digs", result.digs()), x, y, 0xFFE0E0E0, right - PAD);
        fittedLabel(graphics, Component.translatable(KEY + "fills", result.fills()), x, y + 11, 0xFFE0E0E0, right - PAD);
        fittedLabel(graphics, result.missing() > 0 ? Component.translatable(KEY + "missing", result.missing())
                : Component.translatable(KEY + "enough"), x, y + 22, result.missing() > 0 ? 0xFFFFB060 : 0xFFA0E0A0, right - PAD);
        fittedLabel(graphics, Component.translatable(KEY + "kept", result.kept()), x, y + 33, result.kept() > 0 ? 0xFFFFB060 : 0xFFA0A0A0,
                right - PAD);
        MutableComponent spoil = Component.empty();
        for (int i = 0; i < result.spoil().size(); i++) {
            EarthworkPackets.Result.Spoil entry = result.spoil().get(i);
            if (i > 0) {
                spoil.append(", ");
            }
            spoil.append(Component.translatable(KEY + "count", itemName(entry.item()), entry.count()));
        }
        fittedLabel(graphics, result.spoil().isEmpty() ? Component.translatable(KEY + "spoil.none") : Component.translatable(KEY + "spoil", spoil),
                x, y + 44, 0xFFA0A0A0, right - PAD);
        paragraph(graphics, result.message(), x, y + 58, panelWidth - 2 * PAD - 4, 2, result.ok() ? 0xFFA0E0A0 : 0xFFFF8080);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
