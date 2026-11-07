package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's window of the law (a key, or {@code /kingdom law}): by kingdom, whether it wants the player and for what,
 * the reputation there, the debt to its treasury with a button to pay it, the pass into its cities; then the goods guards
 * took, where they are bought back and for how much; and whether the player pays entry fees at gates by themselves.
 */
public class LawScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 260;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int STATUS_LINES = 2;
    private static final int ICONS = 4;
    private static final String KEY = "kingdomworld.screen.law.";

    enum Mode {
        KINGDOMS,
        CONFISCATED;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private LawViews.PlayerView view;
    private Mode mode = Mode.KINGDOMS;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int doneWidth;
    private int modeWidth;
    private int autoWidth;
    private int payWidth;
    private int ransomWidth;

    public LawScreen(LawViews.PlayerView view) {
        super(Component.translatable(KEY + "title"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "law-" + mode.id();
    }

    public void update(LawViews.PlayerView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows a view without asking the server; for the layout check.
     */
    public void showForCheck(String name) {
        mode = Mode.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private Component autoLabel() {
        return Component.translatable(KEY + (view.autoPay() ? "auto_on" : "auto_off"));
    }

    private void measure() {
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, buttonWidth(modeLabel(value)));
        }
        autoWidth = buttonWidth(Component.translatable(KEY + "auto_on"), Component.translatable(KEY + "auto_off"));
        payWidth = buttonWidth(Component.translatable(KEY + "pay"));
        ransomWidth = buttonWidth(Component.translatable(KEY + "ransom"), Component.translatable(KEY + "take"));
        int inner = Math.max(modeWidth + GAP + autoWidth, 200 + payWidth);
        inner = Math.max(inner, ICONS * 18 + 160 + ransomWidth);
        for (String key : List.of("no_kingdoms", "no_confiscated")) {
            inner = Math.max(inner, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int buttonsY() {
        return top() + 20;
    }

    private int listTop() {
        return buttonsY() + ROW + 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int visibleRows() {
        return Math.max(1, (top() + panelHeight - bottomArea() - listTop() - 11) / ROW);
    }

    private int rowCount() {
        return mode == Mode.KINGDOMS ? view.kingdoms().size() : view.confiscations().size();
    }

    private void send(LawPackets.PlayerAction.Action action, java.util.UUID kingdom, long value) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new LawPackets.PlayerAction(action, kingdom, value));
        }
    }

    @Override
    protected void init() {
        measure();
        int x = left() + PAD;
        int right = left() + panelWidth - PAD;
        Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        addRenderableWidget(Button.builder(modeLabel(mode), b -> {
            mode = next;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, buttonsY(), modeWidth, ROW - 2).build());
        Button auto = addRenderableWidget(Button.builder(autoLabel(), b -> send(LawPackets.PlayerAction.Action.AUTO_PAY, null,
                view.autoPay() ? 0 : 1)).bounds(right - autoWidth, buttonsY(), autoWidth, ROW - 2).build());
        auto.setTooltip(Tooltip.create(Component.translatable(KEY + "auto_hint")));
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        for (int i = 0; i < rows && scroll + i < rowCount(); i++) {
            int y = listTop() + i * ROW;
            if (mode == Mode.KINGDOMS) {
                LawViews.KingdomRow row = view.kingdoms().get(scroll + i);
                if (row.debt() > 0) {
                    Button pay = addRenderableWidget(Button.builder(Component.translatable(KEY + "pay"),
                            b -> send(LawPackets.PlayerAction.Action.PAY_DEBT, row.currency().id(), 0))
                            .bounds(right - payWidth, y, payWidth, ROW - 2).build());
                    pay.setTooltip(Tooltip.create(Component.translatable(KEY + "pay_hint")));
                }
            } else {
                LawViews.ConfiscationRow row = view.confiscations().get(scroll + i);
                Button ransom = addRenderableWidget(Button.builder(Component.translatable(KEY + (row.gone() ? "take" : "ransom")),
                        b -> send(LawPackets.PlayerAction.Action.RANSOM_VIEW, null, row.id())).bounds(right - ransomWidth, y, ransomWidth, ROW - 2)
                        .build());
                ransom.setTooltip(Tooltip.create(Component.translatable(KEY + (row.gone() ? "take_hint" : "ransom_hint"))));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rowCount() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201818);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF8A2A2A);
        centeredLabel(graphics, title, width / 2, top + 7, 0xFFFFFFFF, left + PAD, right);
        int rows = visibleRows();
        if (rowCount() == 0) {
            name(graphics, Component.translatable(KEY + (mode == Mode.KINGDOMS ? "no_kingdoms" : "no_confiscated")).getString(), x, listTop() + 5,
                    0xFFB8B8B8, right - x);
        }
        for (int i = 0; i < rows && scroll + i < rowCount(); i++) {
            int y = listTop() + i * ROW;
            if (mode == Mode.KINGDOMS) {
                renderKingdom(graphics, view.kingdoms().get(scroll + i), x, right, y);
            } else {
                renderConfiscation(graphics, view.confiscations().get(scroll + i), x, right, y, mouseX, mouseY);
            }
        }
        int count = rowCount();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint." + mode.id());
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private void renderKingdom(GuiGraphics graphics, LawViews.KingdomRow row, int x, int right, int y) {
        int textRight = row.debt() > 0 ? right - payWidth - 4 : right;
        Component state = row.wanted().<Component>map(w -> LawTexts.punishment(w.execution(), w.fine(), row.currency().units()))
                .orElseGet(() -> Component.translatable(KEY + "clean"));
        Component debt = row.debt() > 0 ? Component.translatable(KEY + "debt", MoneyTexts.amount(row.debt(), row.currency().units()))
                : Component.translatable(KEY + "reputation", LawTexts.reputation(row.reputation()));
        int available = textRight - x;
        int debtWidth = Math.min(font.width(debt), available / 3);
        int stateWidth = Math.min(font.width(state), available / 3);
        int nameWidth = available - debtWidth - stateWidth - 12;
        String kingdom = row.currency().kingdomName();
        List<Component> hints = new ArrayList<>();
        hints.add(Component.literal(kingdom));
        row.wanted().ifPresent(w -> hints.addAll(LawTab.details(w, row.currency().units())));
        hints.add(Component.translatable(KEY + "reputation", LawTexts.reputation(row.reputation())));
        hints.add(row.passMinutes() > 0 ? Component.translatable(KEY + "pass", LawTexts.minutes(row.passMinutes()))
                : Component.translatable(KEY + "no_pass"));
        if (row.account() > 0) {
            hints.add(Component.translatable(KEY + "account", MoneyTexts.amount(row.account(), row.currency().units())));
        }
        for (Component hint : hints) {
            hoverText(x, y + 5, Math.max(0, Math.min(nameWidth, font.width(kingdom))), hint);
        }
        name(graphics, (row.here() ? "» " : "") + kingdom, x, y + 5, 0xFF000000 | FoundKingdomScreen.readable(row.currency().color()), nameWidth);
        name(graphics, state.getString(), x + nameWidth + 6, y + 5, row.wanted().isPresent() ? 0xFFFF7070 : 0xFFA0E0A0, stateWidth);
        name(graphics, debt.getString(), textRight - debtWidth, y + 5, row.debt() > 0 ? 0xFFF0D070 : 0xFFE8E8E8, debtWidth);
    }

    private void renderConfiscation(GuiGraphics graphics, LawViews.ConfiscationRow row, int x, int right, int y, int mouseX, int mouseY) {
        int iconX = x;
        for (int i = 0; i < Math.min(ICONS, row.sample().size()); i++) {
            ItemStack stack = row.sample().get(i);
            graphics.renderItem(stack, iconX, y + 1);
            graphics.renderItemDecorations(font, stack, iconX, y + 1);
            iconX += 18;
        }
        int textX = x + ICONS * 18 + 2;
        int textRight = right - ransomWidth - 4;
        Component what = row.gone() ? Component.translatable(KEY + "goods_gone", io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(row.items()))
                : Component.translatable(KEY + "goods", io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(row.items()),
                MoneyTexts.amount(row.ransom(), row.currency().units()));
        Component days = row.gone() ? Component.empty() : Component.translatable(KEY + "days_left", LawTexts.days(row.daysLeft()));
        int daysWidth = Math.min(font.width(days), (textRight - textX) / 3);
        int kingdomWidth = Math.min(font.width(row.kingdomName()), (textRight - textX) / 3);
        name(graphics, row.kingdomName(), textX, y + 5, 0xFF000000 | FoundKingdomScreen.readable(row.color()), kingdomWidth);
        int whatX = textX + kingdomWidth + 6;
        name(graphics, what.getString(), whatX, y + 5, row.near() ? 0xFFF0D070 : 0xFFB8B8B8, textRight - daysWidth - 6 - whatX);
        if (daysWidth > 0) {
            name(graphics, days.getString(), textRight - daysWidth, y + 5, 0xFFB8B8B8, daysWidth);
        }
        hoverText(textX, y + 5, textRight - textX, Component.translatable(KEY + (row.gone() ? "place_gone" : row.near() ? "place_here" : "place"),
                row.kingdomName()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
