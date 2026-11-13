package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Founding a kingdom on an empty throne: a name, checked with the same rules the server uses, a color and the architectural
 * style its buildings will have, the one the land of the throne suggests chosen at first. Colors and the style stand side by
 * side under the box of the name, the colors from its left edge and the picture of the style up to its right edge; on a
 * screen too narrow for that the colors stand in one row and the style under them, with its arrows beside its picture.
 * The server checks everything again and answers with the throne screen or an error.
 */
public class FoundKingdomScreen extends KingdomScreen implements ResultListener {
    /** Two columns of at least the row of colors, with the gaps around and between them. */
    private static final int MIN_COLUMN = 126;
    /** The name box, the columns, the status and the buttons all stand this far inside the frame. */
    private static final int INSET = PAD + 2;
    private static final int MIN_PANEL_WIDTH = 2 * MIN_COLUMN + 2 * INSET + PAD;
    /** Fits the smallest screen the game allows, 240 high. */
    private static final int PANEL_HEIGHT = 232;
    /** The narrowest screen the game allows. */
    private static final int NARROWEST_SCREEN = 320;
    private static final int SWATCH = 14;
    private static final int SWATCH_GAP = 2;
    private static final int STATUS_LINES = 3;
    private static final int COLUMNS_TOP = 60;
    private static final int CHOICE_TOP = 72;
    /**
     * Where the label and the picture of the style stand when they go under the colors, which then take one row: the narrowest
     * panel, {@link #MIN_PANEL_WIDTH} wide, holds all of them in a row.
     */
    private static final int NARROW_STYLE_TOP = CHOICE_TOP + SWATCH + 4;
    private static final int NARROW_PICTURE_TOP = NARROW_STYLE_TOP + 11;
    private static final int PREVIEW_TOP = 160;
    private static final int STATUS_TOP = 172;

    private final BlockPos throne;
    private final String suggestedStyle;
    private final StylePicker picker;
    private int color;
    private int panelWidth;
    /** The width of each of the two columns, of the colors and of the style. */
    private int column;
    /** Whether the picture of the style stands under the colors instead of beside them. */
    private boolean narrow;
    /** Set by the client self-check to lay the screen out as a language whose labels need more than half of it would. */
    private boolean narrowForCheck;
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
        int suggested = ArchitecturalStyles.index(this.suggestedStyle);
        this.picker = new StylePicker(Math.max(0, suggested), suggested);
    }

    @Override
    public String checkName() {
        return "founding";
    }

    /**
     * The style the land of the throne suggests, or empty.
     */
    public String suggestedStyle() {
        return suggestedStyle;
    }

    /**
     * The style chosen now.
     */
    public String style() {
        return picker.id();
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

    /**
     * The width of a row of {@code perRow} colors.
     */
    private static int swatchRow(int perRow) {
        return perRow * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
    }

    /**
     * The colors stand in two rows beside the style, or in one row above it.
     */
    private int perRow() {
        return narrow ? KingdomColors.PALETTE.size() : KingdomColors.PALETTE.size() / 2;
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
        int columnNeeded = Math.max(Math.max(MIN_COLUMN, swatchRow(KingdomColors.PALETTE.size() / 2)), StylePicker.rowWidth(font));
        columnNeeded = Math.max(columnNeeded, font.width(Component.translatable("kingdomworld.screen.found.color")) + 2);
        columnNeeded = Math.max(columnNeeded, font.width(Component.translatable("kingdomworld.screen.found.style")) + 2);
        int twoColumns = 2 * columnNeeded + 2 * INSET + PAD;
        panelWidth = Math.min(Math.max(Math.max(MIN_PANEL_WIDTH, twoColumns), needed + 2 * INSET), width - 8);
        if (narrowForCheck) {
            // The panel a language whose two columns do not fit the narrowest screen gets there: as wide as that screen allows.
            panelWidth = Math.min(Math.max(panelWidth, NARROWEST_SCREEN - 8), width - 8);
        }
        column = (panelWidth - 2 * INSET - PAD) / 2;
        narrow = narrowForCheck || column < columnNeeded;

        String previous = nameBox == null ? pendingName : nameBox.getValue();
        int left = left();
        int top = top();
        nameBox = new EditBox(font, left + INSET, top + 35, panelWidth - 2 * INSET, 18,
                Component.translatable("kingdomworld.screen.found.name"));
        nameBox.setMaxLength(FoundKingdomPacket.MAX_RAW_NAME);
        nameBox.setValue(previous);
        nameBox.setResponder(value -> updateButtons());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        if (narrow) {
            picker.layout(left + INSET, top + NARROW_PICTURE_TOP, panelWidth - 2 * INSET, StylePicker.SMALL_PICTURE_WIDTH,
                    StylePicker.SMALL_PICTURE_HEIGHT, true);
        } else {
            picker.layout(styleX(), top + CHOICE_TOP, styleSpan(), StylePicker.PICTURE_WIDTH, StylePicker.PICTURE_HEIGHT, false);
        }
        for (Button arrow : picker.buttons(() -> { })) {
            addRenderableWidget(arrow);
        }
        foundButton = addRenderableWidget(Button.builder(found, button -> submit())
                .bounds(left + INSET, top + PANEL_HEIGHT - 28, buttons, 20).build());
        addRenderableWidget(Button.builder(cancel, button -> onClose())
                .bounds(left + panelWidth - INSET - buttons, top + PANEL_HEIGHT - 28, buttons, 20).build());
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

    /**
     * Shows the style of this index among the architectural styles, used by the client self-check.
     */
    public void showStyleForCheck(int index) {
        picker.select(index);
    }

    /**
     * Lays the screen out with the style under the colors, as on a screen too narrow to hold them side by side; used by the
     * client self-check, since in English and Russian every screen the game allows holds them side by side.
     */
    public void narrowForCheck() {
        narrowForCheck = true;
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
        KingdomWorldNetwork.sendToServer(new FoundKingdomPacket(throne, KingdomNames.normalize(nameBox.getValue()), color, picker.index()));
    }

    @Override
    public void onResult(boolean success, Component message) {
        waiting = false;
        status = message;
        statusIsError = !success;
        updateButtons();
    }

    /**
     * Where the column of the colors starts: the left one beside the style, or the whole panel when the style goes under it.
     */
    private int colorsX() {
        return left() + INSET;
    }

    /**
     * The width of the picture of the style with the row of its name and arrows, which stand at the edges of the picture.
     */
    private int styleSpan() {
        return Math.min(column, StylePicker.span(font, StylePicker.PICTURE_WIDTH));
    }

    /**
     * Where the picture of the style starts: it ends at the right edge of the name box, as the colors start at its left edge.
     */
    private int styleX() {
        return left() + panelWidth - INSET - styleSpan();
    }

    /**
     * The colors start at the left edge of their column beside the style, or stand in a row in the middle of the panel above it.
     */
    private int swatchX(int index) {
        int start = narrow ? colorsX() + (panelWidth - 2 * INSET - swatchRow(perRow())) / 2 : colorsX();
        return start + (index % perRow()) * (SWATCH + SWATCH_GAP);
    }

    private int swatchY(int index) {
        return top() + CHOICE_TOP + (index / perRow()) * (SWATCH + SWATCH_GAP);
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
        label(graphics, Component.translatable("kingdomworld.screen.found.name"), left + INSET, top + 24, 0xFFE0E0E0, right - PAD);
        int colorsLabel = swatchX(0);
        label(graphics, Component.translatable("kingdomworld.screen.found.color"), colorsLabel, top + COLUMNS_TOP, 0xFFE0E0E0,
                narrow ? right - INSET : colorsX() + column);
        if (narrow) {
            label(graphics, Component.translatable("kingdomworld.screen.found.style"), picker.left(), top + NARROW_STYLE_TOP, 0xFFE0E0E0,
                    right - INSET);
        } else {
            label(graphics, Component.translatable("kingdomworld.screen.found.style"), picker.pictureX(), top + COLUMNS_TOP, 0xFFE0E0E0,
                    right - INSET);
        }
        for (int i = 0; i < KingdomColors.PALETTE.size(); i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            int swatchColor = KingdomColors.PALETTE.get(i);
            if (swatchColor == color) {
                graphics.fill(x - 2, y - 2, x + SWATCH + 2, y + SWATCH + 2, 0xFFFFFFFF);
            }
            graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | swatchColor);
        }
        picker.render(this, graphics, font);
        Optional<NameProblem> problem = problem();
        String name = KingdomNames.normalize(nameBox.getValue());
        if (problem.isPresent() && !name.isEmpty()) {
            centeredLabel(graphics, problemText(problem.get()), width / 2, top + PREVIEW_TOP, 0xFFFF8080, left + PAD, right - PAD);
        } else if (!name.isEmpty()) {
            // The preview stands on a dark plate so that every kingdom color stays readable.
            int previewWidth = font.width(fit(name, panelWidth - 2 * PAD - 8));
            graphics.fill(width / 2 - previewWidth / 2 - 3, top + PREVIEW_TOP - 2, width / 2 + previewWidth / 2 + 3, top + PREVIEW_TOP + 10,
                    0xFF100C08);
            centeredName(graphics, name, width / 2, top + PREVIEW_TOP, 0xFF000000 | readable(color), panelWidth - 2 * PAD - 8);
        }
        paragraph(graphics, status, left + INSET, top + STATUS_TOP, panelWidth - 2 * INSET, STATUS_LINES,
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
