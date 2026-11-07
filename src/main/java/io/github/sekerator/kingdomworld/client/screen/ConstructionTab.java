package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Construction" tab of the throne screen, in three views: the catalog of building templates (with the cost of one
 * and a button to take its blueprint), the constructions under way (their progress, state and what they lack, with
 * priority, pause, orders for missing materials and cancelling for members with the right) and the register of finished
 * buildings. Every change is an intent the server checks; it answers with a fresh view.
 */
final class ConstructionTab {
    static final int ROW = 20;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int CELL = 16;
    private static final String KEY = "kingdomworld.screen.construction.";

    enum Mode {
        CATALOG,
        SITES,
        BUILDINGS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private ConstructionView view;
    private Mode mode = Mode.CATALOG;
    /** The template shown in detail, or empty for the list. */
    private String template = "";
    /** The construction shown in detail, or 0 for the list. */
    private long site;
    private int scroll;

    private int modeWidth;
    private int takeWidth;
    private int backWidth;
    private int priorityWidth;
    private int pauseWidth;
    private int orderWidth;
    private int cancelWidth;

    ConstructionTab(ThroneScreen screen) {
        this.screen = screen;
    }

    ConstructionView view() {
        return view;
    }

    void setView(ConstructionView view) {
        this.view = view;
    }

    String checkName() {
        String detail = mode == Mode.CATALOG && !template.isEmpty() ? "-template" : mode == Mode.SITES && site != 0 ? "-site" : "";
        return "construction-" + mode.id() + detail;
    }

    /**
     * Shows a view of the tab, for the layout check and the self-check: a template or a construction in detail when
     * given.
     */
    void showForCheck(Mode value, String templateId, long siteId) {
        mode = value;
        template = templateId;
        site = siteId;
        scroll = 0;
    }

    // ---------------------------------------------------------------- texts

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private static Component category(String id) {
        return Component.translatable("kingdomworld.building.category." + id);
    }

    private static Component priorityLabel(Construction.Priority priority) {
        return Component.translatable(KEY + "priority", Component.translatable("kingdomworld.order.priority." + priority.id()));
    }

    private static Component pauseLabel(boolean paused) {
        return Component.translatable(paused ? KEY + "resume" : KEY + "pause");
    }

    private static Component orderLabel(boolean ordering) {
        return Component.translatable(ordering ? KEY + "order_on" : KEY + "order_off");
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static Component status(ConstructionView.Site entry) {
        Component name = Component.translatable("kingdomworld.construction.status." + entry.status().id());
        if (entry.statusPos() != null) {
            return Component.translatable(KEY + "status_at", name, coordinates(entry.statusPos()));
        }
        return name;
    }

    private static int statusColor(Construction.Status status) {
        if (status == Construction.Status.WORKING || status == Construction.Status.DONE) {
            return 0xFFA0E0A0;
        }
        return status.isProblem() ? 0xFFFF9070 : 0xFFE0D090;
    }

    private static Component size(ConstructionView.Template entry) {
        return Component.translatable(KEY + "size", entry.sizeX(), entry.sizeY(), entry.sizeZ());
    }

    @Nullable
    private ConstructionView.Template selectedTemplate() {
        if (view == null) {
            return null;
        }
        for (ConstructionView.Template entry : view.templates()) {
            if (entry.id().equals(template)) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private ConstructionView.Site selectedSite() {
        if (view == null) {
            return null;
        }
        for (ConstructionView.Site entry : view.sites()) {
            if (entry.id() == site) {
                return entry;
            }
        }
        return null;
    }

    private Component summary() {
        return switch (mode) {
            case CATALOG -> Component.translatable(KEY + "summary.catalog", view.templates().size());
            case SITES -> Component.translatable(KEY + "summary.sites", view.sites().size(), view.maxConstructions());
            case BUILDINGS -> Component.translatable(KEY + "summary.buildings", view.buildings().size());
        };
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        takeWidth = screen.buttonWidth(Component.translatable(KEY + "take"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        priorityWidth = 0;
        for (Construction.Priority priority : Construction.Priority.values()) {
            priorityWidth = Math.max(priorityWidth, screen.buttonWidth(priorityLabel(priority)));
        }
        pauseWidth = Math.max(screen.buttonWidth(pauseLabel(true)), screen.buttonWidth(pauseLabel(false)));
        orderWidth = Math.max(screen.buttonWidth(orderLabel(true)), screen.buttonWidth(orderLabel(false)));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        int top = modeWidth + 8 + font.width(Component.translatable(KEY + "summary.sites", 64, 64));
        int detail = takeWidth + GAP + backWidth;
        int controls = Math.max(priorityWidth + GAP + pauseWidth, orderWidth + GAP + cancelWidth);
        int messages = 0;
        for (String key : List.of("loading", "no_templates", "no_sites", "no_buildings", "read_only", "hint.catalog")) {
            messages = Math.max(messages, Math.min(300, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(top, detail), Math.max(controls + backWidth + GAP, messages));
    }

    private static int buttonsY(int top) {
        return top + ThroneScreen.CONTENT_TOP;
    }

    private static int listTop(int top) {
        return buttonsY(top) + ROW + 2;
    }

    private static int bottom(int top, int panelHeight) {
        return top + panelHeight - ThroneScreen.BOTTOM_AREA;
    }

    private static int visibleRows(int top, int panelHeight) {
        return Math.max(1, (bottom(top, panelHeight) - listTop(top)) / ROW);
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case CATALOG -> view.templates().size();
            case SITES -> view.sites().size();
            case BUILDINGS -> view.buildings().size();
        };
    }

    /**
     * The buttons of a construction in detail, laid out in rows from the left; a row that is full goes on below.
     */
    private record Placed(Component label, int x, int y, int width) {
    }

    private List<Placed> siteButtons(ConstructionView.Site entry, int x, int right, int y) {
        List<Placed> placed = new ArrayList<>();
        Component[] labels = {priorityLabel(entry.priority()), pauseLabel(entry.paused()), orderLabel(entry.orderMissing()),
                Component.translatable(KEY + "cancel")};
        int[] widths = {priorityWidth, pauseWidth, orderWidth, cancelWidth};
        int cursor = x;
        int row = y;
        for (int i = 0; i < labels.length; i++) {
            if (cursor + widths[i] > right && cursor > x) {
                cursor = x;
                row += ROW;
            }
            placed.add(new Placed(labels[i], cursor, row, widths[i]));
            cursor += widths[i] + GAP;
        }
        return placed;
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        ConstructionView.Template shownTemplate = mode == Mode.CATALOG ? selectedTemplate() : null;
        ConstructionView.Site shownSite = mode == Mode.SITES ? selectedSite() : null;
        if (shownTemplate == null && shownSite == null) {
            template = "";
            site = 0;
            Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            screen.add(Button.builder(modeLabel(mode), b -> {
                mode = next;
                scroll = 0;
                screen.rebuild();
            }).bounds(x, buttonsY(top), modeWidth, ROW - 2).build());
            int rows = visibleRows(top, panelHeight);
            scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
            return;
        }
        Button back = screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
            template = "";
            site = 0;
            scroll = 0;
            screen.rebuild();
        }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
        back.active = true;
        if (shownTemplate != null) {
            Button take = screen.add(Button.builder(Component.translatable(KEY + "take"), b -> send(new ConstructionPackets.TakeBlueprint(view.kingdomId(),
                    shownTemplate.id()))).bounds(right - backWidth - GAP - takeWidth, buttonsY(top), takeWidth, ROW - 2).build());
            take.active = view.canManage() && shownTemplate.available();
            take.setTooltip(Tooltip.create(Component.translatable(view.canManage() ? KEY + "take_hint" : KEY + "read_only")));
            return;
        }
        if (!view.canManage()) {
            return;
        }
        int controlsTop = siteControlsTop(top, panelHeight, shownSite, x, right);
        long id = shownSite.id();
        for (Placed placed : siteButtons(shownSite, x, right, controlsTop)) {
            Button button;
            if (placed.label().equals(priorityLabel(shownSite.priority()))) {
                button = Button.builder(placed.label(), b -> change(ConstructionPackets.Change.Action.PRIORITY, id, shownSite.priority().next().ordinal()))
                        .bounds(placed.x(), placed.y(), placed.width(), ROW - 2).build();
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "priority_hint")));
            } else if (placed.label().equals(pauseLabel(shownSite.paused()))) {
                button = Button.builder(placed.label(), b -> change(shownSite.paused() ? ConstructionPackets.Change.Action.RESUME
                        : ConstructionPackets.Change.Action.PAUSE, id, 0)).bounds(placed.x(), placed.y(), placed.width(), ROW - 2).build();
            } else if (placed.label().equals(orderLabel(shownSite.orderMissing()))) {
                button = Button.builder(placed.label(), b -> change(shownSite.orderMissing() ? ConstructionPackets.Change.Action.NO_ORDERS
                        : ConstructionPackets.Change.Action.ORDER_MISSING, id, 0)).bounds(placed.x(), placed.y(), placed.width(), ROW - 2).build();
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "order_hint")));
            } else {
                button = Button.builder(placed.label(), b -> {
                    change(ConstructionPackets.Change.Action.CANCEL, id, 0);
                    site = 0;
                }).bounds(placed.x(), placed.y(), placed.width(), ROW - 2).build();
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "cancel_hint")));
            }
            screen.add(button);
        }
    }

    /**
     * Where the buttons of a construction in detail start: at the bottom of the list area, as many rows as they need.
     */
    private int siteControlsTop(int top, int panelHeight, ConstructionView.Site entry, int x, int right) {
        List<Placed> placed = siteButtons(entry, x, right, 0);
        int rows = placed.get(placed.size() - 1).y() / ROW + 1;
        return bottom(top, panelHeight) - rows * ROW;
    }

    private void send(Object packet) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(packet);
        }
    }

    private void change(ConstructionPackets.Change.Action action, long id, int value) {
        if (view != null) {
            send(new ConstructionPackets.Change(view.kingdomId(), action, id, value));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max;
        if (mode == Mode.CATALOG && selectedTemplate() != null) {
            max = Math.max(0, costRows(selectedTemplate(), top, panelHeight)[1] - costRows(selectedTemplate(), top, panelHeight)[0]);
        } else if (mode == Mode.SITES && selectedSite() != null) {
            max = Math.max(0, selectedSite().missing().size() - missingRows(top, panelHeight));
        } else {
            max = Math.max(0, rowCount() - visibleRows(top, panelHeight));
        }
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    /**
     * A click on a row of the catalog or of the constructions shows it in detail.
     */
    boolean click(int left, int top, int panelWidth, int panelHeight, double mouseX, double mouseY) {
        if (view == null || mouseX < left + KingdomScreen.PAD || mouseX > left + panelWidth - KingdomScreen.PAD) {
            return false;
        }
        boolean list = (mode == Mode.CATALOG && selectedTemplate() == null) || (mode == Mode.SITES && selectedSite() == null);
        if (!list) {
            return false;
        }
        int row = (int) Math.floor((mouseY - listTop(top)) / ROW);
        if (mouseY < listTop(top) || row < 0 || row >= visibleRows(top, panelHeight) || scroll + row >= rowCount()) {
            return false;
        }
        if (mode == Mode.CATALOG) {
            template = view.templates().get(scroll + row).id();
        } else {
            site = view.sites().get(scroll + row).id();
        }
        scroll = 0;
        screen.rebuild();
        return true;
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        ConstructionView.Template shownTemplate = mode == Mode.CATALOG ? selectedTemplate() : null;
        ConstructionView.Site shownSite = mode == Mode.SITES ? selectedSite() : null;
        if (shownTemplate != null) {
            renderTemplate(graphics, font, shownTemplate, x, right, top, panelHeight);
            return;
        }
        if (shownSite != null) {
            renderSite(graphics, font, shownSite, x, right, top, panelHeight);
            return;
        }
        int summaryX = x + modeWidth + 8;
        screen.name(graphics, summary().getString(), summaryX, buttonsY(top) + 5, 0xFFD0D0D0, right - summaryX);
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            String key = switch (mode) {
                case CATALOG -> "no_templates";
                case SITES -> "no_sites";
                case BUILDINGS -> "no_buildings";
            };
            screen.name(graphics, Component.translatable(KEY + key).getString(), x, listTop + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int y = listTop + i * ROW;
            switch (mode) {
                case CATALOG -> renderTemplateRow(graphics, font, view.templates().get(scroll + i), x, right, y);
                case SITES -> renderSiteRow(graphics, font, view.sites().get(scroll + i), x, right, y);
                case BUILDINGS -> renderBuildingRow(graphics, font, view.buildings().get(scroll + i), x, right, y);
            }
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW, 0xFF9A9A9A, right);
        }
    }

    private void renderTemplateRow(GuiGraphics graphics, Font font, ConstructionView.Template entry, int x, int right, int y) {
        graphics.renderItem(entry.icon(), x, y + 1);
        Component info = Component.translatable(KEY + "row.template", category(entry.category()), size(entry));
        int infoWidth = Math.min(font.width(info), (right - x) / 2);
        int infoX = right - infoWidth;
        screen.name(graphics, info.getString(), infoX, y + 5, 0xFFB0B0B0, infoWidth);
        int nameX = x + 20;
        int drawn = screen.name(graphics, entry.name().getString(), nameX, y + 5, entry.available() ? 0xFFFFFFFF : 0xFF909090, infoX - 6 - nameX);
        if (!entry.available()) {
            screen.hoverText(nameX, y + 5, drawn, entry.problem());
        }
    }

    private void renderSiteRow(GuiGraphics graphics, Font font, ConstructionView.Site entry, int x, int right, int y) {
        graphics.renderItem(entry.icon(), x, y + 1);
        Component percent = Component.translatable(KEY + "percent", entry.percent());
        int percentX = right - font.width(percent);
        screen.label(graphics, percent, percentX, y + 5, 0xFFF0D070, right);
        int statusWidth = Math.min(font.width(status(entry)), (percentX - x) / 2);
        int statusX = percentX - 6 - statusWidth;
        int drawnStatus = screen.name(graphics, status(entry).getString(), statusX, y + 5, statusColor(entry.status()), statusWidth);
        screen.hoverText(statusX, y + 5, drawnStatus, status(entry));
        int nameX = x + 20;
        Component name = entry.paused() ? Component.translatable(KEY + "paused_name", entry.name()) : entry.name();
        screen.name(graphics, name.getString(), nameX, y + 5, 0xFFFFFFFF, statusX - 6 - nameX);
    }

    private void renderBuildingRow(GuiGraphics graphics, Font font, ConstructionView.Building entry, int x, int right, int y) {
        Component integrity = entry.integrity() < 0 ? Component.translatable(KEY + "integrity_unknown")
                : Component.translatable(KEY + "integrity", entry.integrity());
        int integrityX = right - font.width(integrity);
        int color = entry.integrity() < 0 ? 0xFFB0B0B0 : entry.integrity() >= 90 ? 0xFFA0E0A0 : entry.integrity() >= 50 ? 0xFFE0D090 : 0xFFFF9070;
        screen.label(graphics, integrity, integrityX, y + 5, color, right);
        Component place = Component.translatable(KEY + "row.building", category(entry.category()), coordinates(entry.pos()));
        int placeWidth = Math.min(font.width(place), (integrityX - x) / 2);
        int placeX = integrityX - 6 - placeWidth;
        screen.name(graphics, place.getString(), placeX, y + 5, 0xFFB0B0B0, placeWidth);
        Component name = entry.admin() ? Component.translatable(KEY + "admin_name", entry.name()) : entry.name();
        screen.name(graphics, name.getString(), x, y + 5, 0xFFFFFFFF, placeX - 6 - x);
    }

    /**
     * Cells of the cost per row and rows of cost in all, with the rows that fit: {fit, total, perRow}.
     */
    private int[] costRows(ConstructionView.Template entry, int top, int panelHeight) {
        Font font = screen.font();
        int cellWidth = CELL + 4 + font.width(Component.translatable(KEY + "count", 99_999)) + 6;
        int width = screen.width - 2 * KingdomScreen.PAD;
        int perRow = Math.max(1, Math.min(8, (panelWidthGuess(width) - 2 * KingdomScreen.PAD) / cellWidth));
        int total = (entry.cost().size() + perRow - 1) / perRow;
        int firstLine = listTop(top) + 3 * LINE + (entry.liquids() > 0 ? LINE : 0) + (entry.available() ? 0 : 2 * LINE) + 4;
        int fit = Math.max(1, (bottom(top, panelHeight) - firstLine) / (CELL + 2));
        return new int[]{fit, total, perRow, firstLine, cellWidth};
    }

    private int panelWidthGuess(int width) {
        return lastPanelWidth > 0 ? lastPanelWidth : width;
    }

    private int lastPanelWidth;

    private void renderTemplate(GuiGraphics graphics, Font font, ConstructionView.Template entry, int x, int right, int top, int panelHeight) {
        lastPanelWidth = right - x + 2 * KingdomScreen.PAD;
        int y = buttonsY(top);
        graphics.renderItem(entry.icon(), x, y + 1);
        int nameRight = right - backWidth - GAP - takeWidth - 6;
        screen.name(graphics, entry.name().getString(), x + 20, y + 5, 0xFFF0D070, nameRight - x - 20);
        int line = listTop(top);
        screen.name(graphics, Component.translatable(KEY + "category", category(entry.category())).getString(), x, line, 0xFFD0D0D0, right - x);
        line += LINE;
        screen.name(graphics, Component.translatable(KEY + "dimensions", size(entry), BlueprintRenderer.blocks(entry.blocks())).getString(), x, line,
                0xFFD0D0D0, right - x);
        line += LINE;
        if (entry.liquids() > 0) {
            screen.name(graphics, Component.translatable(KEY + "liquids", BlueprintRenderer.blocks(entry.liquids())).getString(), x, line,
                    0xFFB8B8B8, right - x);
            line += LINE;
        }
        if (!entry.available()) {
            screen.paragraph(graphics, entry.problem(), x, line, right - x, 2, 0xFFFF9070);
            line += 2 * LINE;
        }
        screen.label(graphics, Component.translatable(KEY + "cost"), x, line, 0xFFF0D070, right);
        int[] rows = costRows(entry, top, panelHeight);
        int perRow = rows[2];
        int first = rows[3];
        int cellWidth = rows[4];
        scroll = Math.max(0, Math.min(scroll, rows[1] - rows[0]));
        for (int row = 0; row < rows[0] && scroll + row < rows[1]; row++) {
            for (int column = 0; column < perRow; column++) {
                int index = (scroll + row) * perRow + column;
                if (index >= entry.cost().size()) {
                    break;
                }
                ConstructionView.Cost cost = entry.cost().get(index);
                int cellX = x + column * cellWidth;
                int cellY = first + row * (CELL + 2);
                graphics.renderItem(cost.item(), cellX, cellY);
                Component count = Component.translatable(KEY + "count", cost.count());
                screen.label(graphics, count, cellX + CELL + 4, cellY + 4, 0xFFE8E8E8, cellX + cellWidth);
                screen.hoverText(cellX, cellY + 4, CELL, cost.item().getHoverName());
            }
        }
        if (rows[1] > rows[0]) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows[1], scroll + rows[0]), rows[1]);
            screen.label(graphics, page, right - font.width(page), line, 0xFF9A9A9A, right);
        }
    }

    private int missingRows(int top, int panelHeight) {
        ConstructionView.Site entry = selectedSite();
        if (entry == null) {
            return 1;
        }
        int x = 0;
        int right = Math.max(1, lastPanelWidth - 2 * KingdomScreen.PAD);
        int controls = view != null && view.canManage() ? siteControlsTop(top, panelHeight, entry, x, right) : bottom(top, panelHeight);
        return Math.max(1, (controls - 2 - (listTop(top) + 3 * LINE + 2)) / (CELL + 2));
    }

    private void renderSite(GuiGraphics graphics, Font font, ConstructionView.Site entry, int x, int right, int top, int panelHeight) {
        lastPanelWidth = right - x + 2 * KingdomScreen.PAD;
        int y = buttonsY(top);
        graphics.renderItem(entry.icon(), x, y + 1);
        screen.name(graphics, entry.name().getString(), x + 20, y + 5, 0xFFF0D070, right - backWidth - 6 - x - 20);
        int line = listTop(top);
        screen.name(graphics, Component.translatable(KEY + "site_place", coordinates(entry.pos()), entry.builders()).getString(), x, line,
                0xFFD0D0D0, right - x);
        line += LINE;
        screen.name(graphics, Component.translatable(KEY + "progress", entry.percent(), entry.placed(), BlueprintRenderer.blocks(entry.total()))
                .getString(), x, line, 0xFFD0D0D0, right - x);
        line += LINE;
        int stateLine = line;
        line += LINE + 2;
        int controls = view.canManage() ? siteControlsTop(top, panelHeight, entry, x, right) : bottom(top, panelHeight);
        int rows = Math.max(1, (controls - 2 - line) / (CELL + 2));
        scroll = Math.max(0, Math.min(scroll, entry.missing().size() - rows));
        // When the missing materials take more than one page, the counter stands at the end of the state line.
        int stateRight = right;
        if (entry.missing().size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(entry.missing().size(), scroll + rows),
                    entry.missing().size());
            screen.label(graphics, page, right - font.width(page), stateLine, 0xFF9A9A9A, right);
            stateRight = right - font.width(page) - 6;
        }
        int drawn = screen.name(graphics, Component.translatable(KEY + "state", status(entry)).getString(), x, stateLine,
                statusColor(entry.status()), stateRight - x);
        screen.hoverText(x, stateLine, drawn, status(entry));
        if (entry.missing().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "nothing_missing").getString(), x, line + 4, 0xFFA0E0A0, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < entry.missing().size(); i++) {
            ConstructionView.Missing missing = entry.missing().get(scroll + i);
            int rowY = line + i * (CELL + 2);
            graphics.renderItem(missing.item(), x, rowY);
            Component note;
            if (missing.ordered()) {
                note = Component.translatable(KEY + "missing.ordered", missing.count());
            } else if (!missing.gatherer().isEmpty()) {
                note = Component.translatable(KEY + "missing.gathered", missing.count(), inSentence(CitizenTexts.profession(missing.gatherer())));
            } else if (missing.orderable()) {
                note = Component.translatable(KEY + "missing.orderable", missing.count());
            } else {
                note = Component.translatable(KEY + "missing.nobody", missing.count());
            }
            Component text = Component.translatable(KEY + "missing.line", missing.item().getHoverName(), note);
            int shown = screen.name(graphics, text.getString(), x + CELL + 4, rowY + 4, 0xFFFF9070, right - x - CELL - 4);
            screen.hoverText(x + CELL + 4, rowY + 4, shown, text);
        }
    }

    /**
     * A profession named inside a sentence starts with a small letter.
     */
    private static Component inSentence(Component name) {
        String text = name.getString();
        return text.isEmpty() ? name : Component.literal(text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1));
    }

    /**
     * A status line for the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (!view.canManage()) {
            return Component.translatable(KEY + "read_only");
        }
        if (mode == Mode.CATALOG && selectedTemplate() == null) {
            return Component.translatable(KEY + "hint.catalog");
        }
        if (mode == Mode.CATALOG) {
            return Component.translatable(KEY + "hint.template");
        }
        if (mode == Mode.SITES && selectedSite() == null) {
            return Component.translatable(KEY + "hint.sites");
        }
        return Component.empty();
    }
}
