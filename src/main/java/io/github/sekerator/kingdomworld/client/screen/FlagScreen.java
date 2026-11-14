package io.github.sekerator.kingdomworld.client.screen;

import com.mojang.datafixers.util.Pair;
import io.github.sekerator.kingdomworld.client.flag.ClientFlags;
import io.github.sekerator.kingdomworld.client.flag.FlagRendering;
import io.github.sekerator.kingdomworld.client.network.ClientKingdomCache;
import io.github.sekerator.kingdomworld.common.flag.FlagPatterns;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.FlagPackets;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlags;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The editor of the flag of a kingdom, opened from the throne screen. The king of a kingdom of players changes the colour of
 * the field, adds up to six layers of patterns, colours them, puts them in order and takes them away, sees the flag at once in
 * a large preview and saves it; everyone else sees the flag with the names of its layers.
 *
 * <p>The rows on the left are the field and the layers in the order they are drawn, the lowest first, as the tooltip of a banner
 * lists them; a click picks a row. A colour of the palette paints the row picked; a pattern of the grid on the right replaces the
 * pattern of the layer picked, or, with the field picked or with Shift, is added on top in the colour of the brush. The brush is
 * at first the dye that stands out most from the field, then the colour last picked. Whatever a click would do shows in the
 * preview while the mouse is over it.
 */
public class FlagScreen extends KingdomScreen implements ResultListener {
    private static final String KEY = "kingdomworld.screen.flag.";
    /** How many changes can be undone. */
    private static final int HISTORY = 32;
    private static final int PANEL_COLOR = 0xF0201810;
    private static final int GOLD = 0xFFC8A040;
    private static final int PLATE = 0xFF100C08;
    private static final int CELL = 0xFF2A2018;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int MUTED = 0xFFB8B8B8;
    private static final int BUTTON_ROW_HEIGHT = 20;
    private static final int CONTROL = 10;
    private static final int GAP = 2;
    private static final int SWATCH_GAP = 2;
    private static final int PER_ROW = 8;
    private static final int SCROLLBAR = 3;
    /** The pattern a layer added with the row at the end of the list starts with, until a pattern was picked. */
    private static final String FIRST_PATTERN = "minecraft:circle";

    private final ThroneScreen parent;
    private final UUID kingdom;
    private final String name;
    private final int color;
    private final String style;
    private final boolean editable;

    private KingdomFlag working;
    /** The row picked: 0 the field, then the layers. */
    private int selected;
    private int brush;
    private String lastPattern = FIRST_PATTERN;
    private final Deque<KingdomFlag> undo = new ArrayDeque<>();
    private final Deque<KingdomFlag> redo = new ArrayDeque<>();
    /** The answer of the server, or a note of the editor itself. */
    private Component status = Component.empty();
    private boolean statusError;
    private boolean waiting;
    /** The first row of patterns shown, when not all of them fit. */
    private int scroll;

    private boolean big;
    private int panelWidth;
    private int panelHeight;
    private int previewScale;
    private int rowHeight;
    private int iconScale;
    private int swatch;
    private int cellWidth;
    private int cellHeight;
    private int cellScale;
    private int columnWidth;
    private int gridColumns;
    private int gridRows;
    private List<String> patterns = List.of();
    private final Map<Integer, Map<String, List<Pair<Holder<BannerPattern>, DyeColor>>>> cells = new HashMap<>();

    private Button saveButton;
    private Button defaultButton;
    private Button bannerButton;

    public FlagScreen(ThroneScreen parent, UUID kingdom, String name, int color, String style, boolean editable) {
        super(Component.translatable(KEY + "title", name));
        this.parent = parent;
        this.kingdom = kingdom;
        this.name = name;
        this.color = color;
        this.style = style == null ? "" : style;
        this.editable = editable;
        this.working = current();
        this.brush = KingdomFlags.mostContrasting(working.base());
    }

    @Override
    public String checkName() {
        return "flag/" + (editable ? "edit" : parent.view().npc() != null ? "world" : "view");
    }

    /**
     * Shows a flag being drawn with a row picked, for the layout check.
     */
    public void showForCheck(KingdomFlag flag, int row) {
        working = flag;
        selected = Math.max(0, Math.min(row, flag.layers().size()));
    }

    /**
     * The flag the kingdom flies, as the server told it, or the flag of its colour until it did.
     */
    private KingdomFlag current() {
        return ClientFlags.flag(kingdom).orElse(KingdomFlags.standard(color));
    }

    private boolean changed() {
        return !working.equals(current());
    }

    // ---------------------------------------------------------------- layout

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int plateWidth() {
        return FlagRendering.width(previewScale) + 8;
    }

    private int plateHeight() {
        return FlagRendering.height(previewScale) + 12;
    }

    private int plateX() {
        return left() + 10;
    }

    private int plateY() {
        return top() + (big ? 24 : 22);
    }

    private int columnX() {
        return plateX() + plateWidth() + 8;
    }

    private int headerY() {
        return top() + (big ? 24 : 22);
    }

    private int rowsY() {
        return headerY() + 11;
    }

    private int rowY(int row) {
        return rowsY() + row * rowHeight;
    }

    private int columnRight() {
        return editable ? columnX() + columnWidth : left() + panelWidth - 10;
    }

    private int gridX() {
        return columnX() + columnWidth + 8;
    }

    private int gridY() {
        return rowsY();
    }

    private int gridRight() {
        return left() + panelWidth - 10;
    }

    private int gridBottom() {
        return statusY() - 2;
    }

    private int statusY() {
        return top() + panelHeight - 36;
    }

    private int swatchX(int index) {
        return columnX() + (index % PER_ROW) * (swatch + SWATCH_GAP);
    }

    private int swatchY(int index) {
        int rows = 2 * swatch + SWATCH_GAP;
        return statusY() - 4 - rows + (index / PER_ROW) * (swatch + SWATCH_GAP);
    }

    private int paletteLabelY() {
        return swatchY(0) - 11;
    }

    @Override
    protected void init() {
        FlagRendering.bake();
        patterns = FlagPatterns.all();
        cells.clear();
        big = width >= 520 && height >= 300;
        if (editable) {
            panelWidth = Math.min(width - 16, big ? 560 : 440);
            panelHeight = Math.min(height - 8, big ? 320 : 236);
            previewScale = big ? 72 : 48;
        } else {
            panelWidth = Math.min(width - 16, big ? 420 : 340);
            panelHeight = Math.min(height - 8, big ? 260 : 216);
            previewScale = big ? 96 : 72;
        }
        rowHeight = big ? 22 : 14;
        iconScale = big ? 12 : 6;
        swatch = big ? 18 : 14;
        columnWidth = big ? 190 : 150;
        fitGrid();

        int y = top() + panelHeight - 24;
        Component back = Component.translatable("gui.back");
        addRenderableWidget(Button.builder(back, b -> onClose()).bounds(left() + PAD, y, buttonWidth(back), BUTTON_ROW_HEIGHT).build());
        if (!editable) {
            return;
        }
        Component save = Component.translatable(KEY + "save");
        Component fromBanner = Component.translatable(KEY + "from_banner");
        Component random = Component.translatable(KEY + "random");
        Component standard = Component.translatable(KEY + "default");
        int x = left() + panelWidth - PAD;
        x -= buttonWidth(save);
        saveButton = addRenderableWidget(Button.builder(save, b -> save()).bounds(x, y, buttonWidth(save), BUTTON_ROW_HEIGHT).build());
        x -= GAP + buttonWidth(fromBanner);
        bannerButton = addRenderableWidget(Button.builder(fromBanner, b -> fromBanner())
                .bounds(x, y, buttonWidth(fromBanner), BUTTON_ROW_HEIGHT).build());
        x -= GAP + buttonWidth(random);
        addRenderableWidget(Button.builder(random, b -> change(KingdomFlags.generated(ThreadLocalRandom.current().nextLong(), color, style,
                null, Set.of()), 0)).bounds(x, y, buttonWidth(random), BUTTON_ROW_HEIGHT).build());
        x -= GAP + buttonWidth(standard);
        defaultButton = addRenderableWidget(Button.builder(standard, b -> change(KingdomFlags.standard(color), 0))
                .bounds(x, y, buttonWidth(standard), BUTTON_ROW_HEIGHT).build());
        updateButtons();
    }

    /**
     * The largest cells that show every pattern without scrolling, or the small ones with a scroll bar.
     */
    private void fitGrid() {
        int[][] sizes = big ? new int[][]{{22, 42, 24}, {17, 32, 18}, {12, 22, 12}} : new int[][]{{17, 32, 18}, {12, 22, 12}};
        for (int[] size : sizes) {
            cellWidth = size[0];
            cellHeight = size[1];
            cellScale = size[2];
            gridColumns = Math.max(1, (gridRight() - gridX()) / cellWidth);
            gridRows = Math.max(1, (gridBottom() - gridY()) / cellHeight);
            if (gridColumns * gridRows >= patterns.size()) {
                scroll = 0;
                return;
            }
        }
        // Room for the scroll bar.
        gridColumns = Math.max(1, (gridRight() - gridX() - SCROLLBAR - 1) / cellWidth);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int totalRows() {
        return (patterns.size() + gridColumns - 1) / gridColumns;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - gridRows);
    }

    private void updateButtons() {
        if (saveButton == null) {
            return;
        }
        saveButton.active = changed() && !waiting;
        defaultButton.active = !working.equals(KingdomFlags.standard(color));
        Optional<Taken> banner = fromHands();
        bannerButton.active = banner.isPresent();
        bannerButton.setTooltip(banner.isPresent() ? null : Tooltip.create(Component.translatable(KEY + "from_banner.none")));
    }

    @Override
    public void tick() {
        super.tick();
        if (undo.isEmpty() && !waiting && !working.equals(current())) {
            // The directory of the server came after the editor opened: nothing was drawn yet, so show the flag it told.
            working = current();
            selected = 0;
            brush = KingdomFlags.mostContrasting(working.base());
        }
        updateButtons();
    }

    // ---------------------------------------------------------------- changes

    /**
     * Makes a change that can be undone, and picks a row.
     */
    private void change(KingdomFlag next, int row) {
        if (next.equals(working)) {
            selected = Math.max(0, Math.min(row, working.layers().size()));
            return;
        }
        undo.push(working);
        while (undo.size() > HISTORY) {
            undo.removeLast();
        }
        redo.clear();
        working = next;
        selected = Math.max(0, Math.min(row, working.layers().size()));
        status = Component.empty();
        updateButtons();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            redo.push(working);
            working = undo.pop();
            selected = Math.min(selected, working.layers().size());
            updateButtons();
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            undo.push(working);
            working = redo.pop();
            selected = Math.min(selected, working.layers().size());
            updateButtons();
        }
    }

    /**
     * What a click on a colour of the palette makes of the flag: the row picked in that colour.
     */
    private KingdomFlag painted(int dye) {
        if (selected == 0) {
            return working.withBase(dye);
        }
        KingdomFlag.Layer layer = working.layers().get(selected - 1);
        return working.withLayer(selected - 1, new KingdomFlag.Layer(layer.pattern(), dye));
    }

    /**
     * What a click on a pattern makes of the flag, or {@code null} when no further layer fits.
     */
    @Nullable
    private KingdomFlag patterned(String pattern, boolean add) {
        if (add || selected == 0) {
            return working.full() ? null : working.added(new KingdomFlag.Layer(pattern, brush));
        }
        KingdomFlag.Layer layer = working.layers().get(selected - 1);
        return working.withLayer(selected - 1, new KingdomFlag.Layer(pattern, layer.dye()));
    }

    private void pickPattern(String pattern) {
        boolean add = hasShiftDown() || selected == 0;
        KingdomFlag next = patterned(pattern, add);
        if (next == null) {
            status = Component.translatable(KEY + "max", KingdomFlag.MAX_LAYERS);
            statusError = true;
            return;
        }
        lastPattern = pattern;
        play(SoundEvents.UI_LOOM_SELECT_PATTERN);
        change(next, add ? next.layers().size() : selected);
    }

    private void pickColor(int dye) {
        brush = dye;
        change(painted(dye), selected);
    }

    private void addLayer() {
        if (working.full()) {
            status = Component.translatable(KEY + "max", KingdomFlag.MAX_LAYERS);
            statusError = true;
            return;
        }
        KingdomFlag next = working.added(new KingdomFlag.Layer(lastPattern, brush));
        change(next, next.layers().size());
    }

    private void removeLayer(int index) {
        if (index >= 0 && index < working.layers().size()) {
            change(working.removed(index), index);
        }
    }

    private void moveLayer(int index, int delta) {
        if (index < 0 || index >= working.layers().size()) {
            return;
        }
        KingdomFlag next = working.moved(index, delta);
        change(next, Math.max(0, Math.min(working.layers().size() - 1, index + delta)) + 1);
    }

    private void save() {
        if (!editable || waiting || !changed()) {
            return;
        }
        // Layers of patterns of mods taken out cannot be saved: they go.
        List<KingdomFlag.Layer> known = new ArrayList<>();
        for (KingdomFlag.Layer layer : working.layers()) {
            if (FlagPatterns.holder(layer.pattern()) != null) {
                known.add(layer);
            }
        }
        waiting = true;
        status = Component.empty();
        updateButtons();
        KingdomWorldNetwork.sendToServer(new FlagPackets.Save(kingdom, new KingdomFlag(working.base(), known)));
    }

    @Override
    public void onResult(boolean success, Component message) {
        waiting = false;
        if (success && minecraft != null) {
            play(SoundEvents.UI_LOOM_TAKE_RESULT);
            minecraft.setScreen(parent);
            parent.onResult(true, message);
            return;
        }
        status = message;
        statusError = !success;
        updateButtons();
    }

    private void play(net.minecraft.sounds.SoundEvent sound) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
        }
    }

    // ---------------------------------------------------------------- the flag in the hands

    /**
     * A flag taken from a banner, with the number of layers it had.
     */
    private record Taken(KingdomFlag flag, int layers) {
    }

    /**
     * The flag of the banner in the main hand, else of the shield there, else the same in the other hand.
     */
    private Optional<Taken> fromHands() {
        if (minecraft == null || minecraft.player == null) {
            return Optional.empty();
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof BannerItem banner) {
                return Optional.of(taken(banner.getColor(), stack));
            }
            if (stack.getItem() instanceof ShieldItem && BlockItem.getBlockEntityData(stack) != null) {
                return Optional.of(taken(ShieldItem.getColor(stack), stack));
            }
        }
        return Optional.empty();
    }

    private static Taken taken(DyeColor base, ItemStack stack) {
        List<KingdomFlag.Layer> layers = new ArrayList<>();
        int count = 0;
        ListTag list = BannerBlockEntity.getItemPatterns(stack);
        for (int i = 0; list != null && i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Holder<BannerPattern> pattern = BannerPattern.byHash(entry.getString(BannerBlockEntity.TAG_PATTERN));
            String id = pattern == null ? null : pattern.unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id == null || !KingdomFlag.validPattern(id)) {
                continue;
            }
            count++;
            if (layers.size() < KingdomFlag.MAX_LAYERS) {
                layers.add(new KingdomFlag.Layer(id, DyeColor.byId(entry.getInt(BannerBlockEntity.TAG_COLOR)).getId()));
            }
        }
        return new Taken(new KingdomFlag(base.getId(), layers), count);
    }

    private void fromBanner() {
        Optional<Taken> taken = fromHands();
        if (taken.isEmpty()) {
            return;
        }
        change(taken.get().flag(), 0);
        brush = KingdomFlags.mostContrasting(working.base());
        if (taken.get().layers() > KingdomFlag.MAX_LAYERS) {
            status = Component.translatable(KEY + "from_banner.cut", KingdomFlag.MAX_LAYERS, taken.get().layers());
            statusError = false;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public void onClose() {
        if (minecraft == null) {
            return;
        }
        if (editable && changed()) {
            minecraft.setScreen(new ConfirmScreen(yes -> minecraft.setScreen(yes ? parent : this),
                    Component.translatable("kingdomworld.screen.throne.confirm_title"), Component.translatable(KEY + "discard")));
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editable) {
            boolean control = hasControlDown();
            switch (key) {
                case GLFW.GLFW_KEY_DELETE -> {
                    if (selected > 0) {
                        removeLayer(selected - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    if (control) {
                        moveLayer(selected - 1, -1);
                    } else {
                        selected = Math.max(0, selected - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (control) {
                        moveLayer(selected - 1, 1);
                    } else {
                        selected = Math.min(working.layers().size(), selected + 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> {
                    if (control) {
                        if (hasShiftDown()) {
                            redo();
                        } else {
                            undo();
                        }
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_Y -> {
                    if (control) {
                        redo();
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    save();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (editable && maxScroll() > 0 && mouseX >= gridX() && mouseX < gridRight() && mouseY >= gridY() && mouseY < gridBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!editable || button != 0) {
            return false;
        }
        int mx = (int) Math.floor(mouseX);
        int my = (int) Math.floor(mouseY);
        // The buttons of a layer first, then the rows.
        for (int i = 0; i < working.layers().size(); i++) {
            int y = rowY(i + 1);
            if (inside(mx, my, controlX(0), controlY(y), CONTROL, CONTROL)) {
                moveLayer(i, -1);
                return true;
            }
            if (inside(mx, my, controlX(1), controlY(y), CONTROL, CONTROL)) {
                moveLayer(i, 1);
                return true;
            }
            if (inside(mx, my, controlX(2), controlY(y), CONTROL, CONTROL)) {
                removeLayer(i);
                return true;
            }
        }
        for (int row = 0; row <= working.layers().size(); row++) {
            if (inside(mx, my, columnX() - 4, rowY(row), columnWidth + 4, rowHeight)) {
                selected = row;
                return true;
            }
        }
        if (!working.full() && inside(mx, my, columnX() - 4, rowY(working.layers().size() + 1), columnWidth + 4, rowHeight)) {
            addLayer();
            return true;
        }
        for (int i = 0; i < KingdomColors.DYES; i++) {
            if (inside(mx, my, swatchX(i), swatchY(i), swatch, swatch)) {
                pickColor(i);
                return true;
            }
        }
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            pickPattern(patterns.get(cell));
            return true;
        }
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Where the three buttons of a layer stand: up, down, away.
     */
    private int controlX(int index) {
        return columnX() + columnWidth - (3 - index) * (CONTROL + 2) + 2;
    }

    private int controlY(int rowY) {
        return rowY + (rowHeight - CONTROL) / 2;
    }

    /**
     * The pattern under the mouse, or -1.
     */
    private int cellAt(int mx, int my) {
        if (mx < gridX() || my < gridY()) {
            return -1;
        }
        int column = (mx - gridX()) / cellWidth;
        int row = (my - gridY()) / cellHeight;
        if (column >= gridColumns || row >= gridRows) {
            return -1;
        }
        int index = (scroll + row) * gridColumns + column;
        return index < patterns.size() ? index : -1;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        graphics.fill(left, top, right, top + panelHeight, PANEL_COLOR);
        graphics.renderOutline(left, top, panelWidth, panelHeight, GOLD);
        centeredName(graphics, title.getString(), width / 2, top + 7, 0xFFE8C860, panelWidth - 2 * PAD);

        KingdomFlag preview = working;
        Component hovered = null;
        if (editable) {
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                KingdomFlag next = patterned(patterns.get(cell), hasShiftDown() || selected == 0);
                if (next != null) {
                    preview = next;
                }
            }
            for (int i = 0; i < KingdomColors.DYES; i++) {
                if (inside(mouseX, mouseY, swatchX(i), swatchY(i), swatch, swatch)) {
                    preview = painted(i);
                }
            }
        }
        // The preview on its plate.
        int plateX = plateX();
        int plateY = plateY();
        graphics.fill(plateX, plateY, plateX + plateWidth(), plateY + plateHeight(), PLATE);
        graphics.renderOutline(plateX, plateY, plateWidth(), plateHeight(), 0xFF6A5420);
        FlagRendering.drawGui(graphics, preview, plateX + 4, plateY + 6, previewScale);

        renderRows(graphics, mouseX, mouseY);
        if (editable) {
            renderPalette(graphics, mouseX, mouseY);
            renderGrid(graphics, mouseX, mouseY);
        }
        renderStatus(graphics);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = columnX();
        int right = columnRight();
        label(graphics, Component.translatable(KEY + "layers", working.layers().size(), KingdomFlag.MAX_LAYERS), x, headerY(), GOLD, right);
        int iconWidth = FlagRendering.width(iconScale);
        int iconHeight = FlagRendering.height(iconScale);
        int textX = x + iconWidth + 4;
        int textRight = editable ? controlX(0) - 3 : right;
        int rows = working.layers().size() + 1;
        for (int row = 0; row < rows; row++) {
            int y = rowY(row);
            boolean over = editable && inside(mouseX, mouseY, x - 4, y, columnWidth + 4, rowHeight);
            if (over) {
                graphics.fill(x - 2, y, right, y + rowHeight, 0x30FFFFFF);
            }
            if (editable && row == selected) {
                graphics.fill(x - 4, y, x - 2, y + rowHeight, GOLD);
            }
            int textY = y + (rowHeight - 8) / 2;
            int iconY = y + (rowHeight - iconHeight) / 2;
            if (row == 0) {
                FlagRendering.drawGui(graphics, KingdomFlag.plain(working.base()), x, iconY, iconScale);
                fittedLabel(graphics, Component.translatable(KEY + "field", FlagPatterns.colorName(working.base())), textX, textY, TEXT, right);
                continue;
            }
            KingdomFlag.Layer layer = working.layers().get(row - 1);
            FlagRendering.drawGui(graphics, new KingdomFlag(working.base(), List.of(layer)), x, iconY, iconScale);
            boolean known = FlagPatterns.holder(layer.pattern()) != null;
            Component text = known ? FlagPatterns.name(layer.pattern(), layer.dye())
                    : Component.translatable(KEY + "missing", layer.pattern());
            fittedLabel(graphics, text, textX, textY, known ? TEXT : 0xFFFF8080, textRight);
            if (editable) {
                control(graphics, controlX(0), controlY(y), 0, row > 1, mouseX, mouseY, Component.translatable(KEY + "earlier"));
                control(graphics, controlX(1), controlY(y), 1, row < rows - 1, mouseX, mouseY, Component.translatable(KEY + "later"));
                control(graphics, controlX(2), controlY(y), 2, true, mouseX, mouseY, Component.translatable(KEY + "remove"));
            }
        }
        if (editable && !working.full()) {
            int y = rowY(rows);
            boolean over = inside(mouseX, mouseY, x - 4, y, columnWidth + 4, rowHeight);
            if (over) {
                graphics.fill(x - 2, y, right, y + rowHeight, 0x30FFFFFF);
            }
            fittedLabel(graphics, Component.translatable(KEY + "add"), x, y + (rowHeight - 8) / 2, over ? 0xFFFFFFA0 : 0xFFA0E0A0, right);
        }
    }

    /**
     * A small button of a layer: up, down or away, drawn as a sign in a box, with its hint on hover.
     */
    private void control(GuiGraphics graphics, int x, int y, int kind, boolean active, int mouseX, int mouseY, Component hint) {
        boolean over = active && inside(mouseX, mouseY, x, y, CONTROL, CONTROL);
        graphics.fill(x, y, x + CONTROL, y + CONTROL, over ? 0xFF5A4A30 : 0xFF3A3020);
        graphics.renderOutline(x, y, CONTROL, CONTROL, active ? 0xFF8A7040 : 0xFF4A4030);
        int ink = active ? (over ? 0xFFFFFFA0 : 0xFFE0D0A0) : 0xFF6A6050;
        switch (kind) {
            case 0 -> {
                for (int i = 0; i < 3; i++) {
                    graphics.fill(x + 4 - i, y + 3 + i, x + 6 + i, y + 4 + i, ink);
                }
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) {
                    graphics.fill(x + 4 - i, y + 6 - i, x + 6 + i, y + 7 - i, ink);
                }
            }
            default -> {
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, ink);
                    graphics.fill(x + 6 - i, y + 3 + i, x + 7 - i, y + 4 + i, ink);
                }
            }
        }
        if (active) {
            hoverText(x, y + 1, CONTROL, hint);
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        label(graphics, Component.translatable(KEY + "colour"), columnX(), paletteLabelY(), GOLD, columnX() + columnWidth);
        int picked = selected == 0 ? working.base() : working.layers().get(selected - 1).dye();
        for (int i = 0; i < KingdomColors.DYES; i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            if (i == picked) {
                graphics.fill(x - 2, y - 2, x + swatch + 2, y + swatch + 2, 0xFFFFFFFF);
            } else if (i == brush) {
                graphics.renderOutline(x - 1, y - 1, swatch + 2, swatch + 2, GOLD);
            }
            graphics.fill(x, y, x + swatch, y + swatch, 0xFF000000 | KingdomColors.dyeRgb(i));
            if (inside(mouseX, mouseY, x, y, swatch, swatch)) {
                graphics.renderOutline(x, y, swatch, swatch, 0xFF808080);
                hoverText(x, y + 2, swatch, FlagPatterns.colorName(i));
            }
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        label(graphics, Component.translatable(KEY + "pattern"), gridX(), headerY(), GOLD, gridRight());
        String chosen = selected == 0 ? null : working.layers().get(selected - 1).pattern();
        Map<String, List<Pair<Holder<BannerPattern>, DyeColor>>> drawn = cells.computeIfAbsent(working.base() * KingdomColors.DYES + brush,
                key -> new HashMap<>());
        int flagWidth = FlagRendering.width(cellScale);
        int flagHeight = FlagRendering.height(cellScale);
        for (int row = 0; row < gridRows; row++) {
            for (int column = 0; column < gridColumns; column++) {
                int index = (scroll + row) * gridColumns + column;
                if (index >= patterns.size()) {
                    break;
                }
                String pattern = patterns.get(index);
                int x = gridX() + column * cellWidth;
                int y = gridY() + row * cellHeight;
                boolean over = inside(mouseX, mouseY, x, y, cellWidth, cellHeight);
                graphics.fill(x, y, x + cellWidth - 1, y + cellHeight - 1, over ? 0xFF4A3C28 : CELL);
                List<Pair<Holder<BannerPattern>, DyeColor>> layers = drawn.computeIfAbsent(pattern,
                        id -> ClientFlags.layers(new KingdomFlag(working.base(), List.of(new KingdomFlag.Layer(id, brush)))));
                FlagRendering.drawGui(graphics, layers, x + (cellWidth - 1 - flagWidth) / 2, y + (cellHeight - 1 - flagHeight) / 2, cellScale);
                if (pattern.equals(chosen)) {
                    graphics.renderOutline(x, y, cellWidth - 1, cellHeight - 1, GOLD);
                }
                if (over) {
                    hoverText(x, y + 1, cellWidth - 1, FlagPatterns.name(pattern, brush));
                }
            }
        }
        if (maxScroll() > 0) {
            int barX = gridRight() - SCROLLBAR;
            int barTop = gridY();
            int barHeight = gridRows * cellHeight - 1;
            graphics.fill(barX, barTop, barX + SCROLLBAR, barTop + barHeight, 0xFF2A2018);
            int thumb = Math.max(8, barHeight * gridRows / totalRows());
            int thumbY = barTop + (barHeight - thumb) * scroll / maxScroll();
            graphics.fill(barX, thumbY, barX + SCROLLBAR, thumbY + thumb, 0xFF8A7040);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        int x = left() + PAD;
        int right = left() + panelWidth - PAD;
        Component text = status;
        int color = statusError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (text.getString().isEmpty()) {
            text = note();
            color = MUTED;
        }
        if (!text.getString().isEmpty()) {
            fittedLabel(graphics, text, x, statusY(), color, right);
        }
    }

    /**
     * What the status line tells while nothing else is to tell: why the flag cannot be changed here, that another kingdom flies
     * the same flag, that some layers are of mods taken out, or how the editor works.
     */
    private Component note() {
        if (!editable) {
            return Component.translatable(parent.view().npc() != null ? KEY + "readonly.world" : KEY + "readonly.king");
        }
        for (FlagPackets.Entry entry : ClientFlags.all()) {
            if (!entry.kingdom().equals(kingdom) && entry.flag().equals(working)) {
                String other = ClientKingdomCache.kingdoms().stream().filter(summary -> summary.id().equals(entry.kingdom()))
                        .map(KingdomSummary::name).findFirst().orElse(null);
                return other != null ? Component.translatable(KEY + "same_as", other) : Component.translatable(KEY + "same_as.unknown");
            }
        }
        for (KingdomFlag.Layer layer : working.layers()) {
            if (FlagPatterns.holder(layer.pattern()) == null) {
                return Component.translatable(KEY + "missing.note");
            }
        }
        return Component.translatable(KEY + "hint");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
