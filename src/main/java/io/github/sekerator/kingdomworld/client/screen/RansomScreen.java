package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The window to buy back confiscated goods: every item the guards took (with its own tooltip), what the goods are worth and
 * the ransom, how many days are left before they go to the storehouses, and whether the player stands where the kingdom
 * gives them back. Goods of a kingdom that is gone are given back for free.
 */
public class RansomScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 240;
    private static final int MAX_PANEL_HEIGHT = 240;
    private static final int CELL = 18;
    private static final int COLUMNS = 9;
    private static final int GAP = 4;
    private static final int STATUS_LINES = 2;
    private static final String KEY = "kingdomworld.screen.ransom.";

    private LawViews.RansomView view;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int coinsWidth;
    private int accountWidth;
    private int cancelWidth;

    public RansomScreen(LawViews.RansomView view) {
        super(Component.translatable(KEY + "title"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "law-ransom" + (view.confiscation().gone() ? "-gone" : view.confiscation().near() ? "" : "-far");
    }

    public long id() {
        return view.confiscation().id();
    }

    public void update(LawViews.RansomView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private CurrencyUnits units() {
        return view.confiscation().currency().units();
    }

    private Component[] lines() {
        LawViews.ConfiscationRow row = view.confiscation();
        Component worth = Component.translatable(KEY + "worth", MoneyTexts.amount(row.worth(), units()), MoneyTexts.amount(row.ransom(), units()));
        Component days = row.gone() ? Component.translatable(KEY + "gone") : Component.translatable(KEY + "days", LawTexts.days(row.daysLeft()));
        Component place = Component.translatable(row.gone() ? KEY + "place_gone" : row.near() ? KEY + "place_here" : KEY + "place", row.kingdomName());
        Component money = Component.translatable(KEY + "money", MoneyTexts.amount(view.coins(), units()), MoneyTexts.amount(view.account(), units()));
        return new Component[]{worth, days, place, money};
    }

    private void measure() {
        boolean gone = view.confiscation().gone();
        coinsWidth = buttonWidth(Component.translatable(gone ? KEY + "take" : KEY + "pay_coins"));
        accountWidth = gone ? 0 : buttonWidth(Component.translatable(KEY + "pay_account"));
        cancelWidth = buttonWidth(Component.translatable(KEY + "cancel"));
        int inner = Math.max(COLUMNS * CELL, coinsWidth + accountWidth + cancelWidth + 2 * GAP);
        for (Component line : lines()) {
            inner = Math.max(inner, Math.min(360, font.width(line)));
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

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int gridTop() {
        return top() + 20 + 4 * (LINE + 2) + 2;
    }

    private int gridRows() {
        return Math.max(1, (top() + panelHeight - bottomArea() - gridTop()) / CELL);
    }

    private void pay(boolean account) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new LawPackets.RansomAction(view.confiscation().id(), account));
        }
    }

    @Override
    protected void init() {
        measure();
        LawViews.ConfiscationRow row = view.confiscation();
        int y = top() + panelHeight - 26;
        int x = left() + PAD;
        Button coins = addRenderableWidget(Button.builder(Component.translatable(row.gone() ? KEY + "take" : KEY + "pay_coins"), b -> pay(false))
                .bounds(x, y, coinsWidth, 20).build());
        coins.active = row.gone() || row.near() && view.coins() >= row.ransom();
        if (accountWidth > 0) {
            Button account = addRenderableWidget(Button.builder(Component.translatable(KEY + "pay_account"), b -> pay(true))
                    .bounds(x + coinsWidth + GAP, y, accountWidth, 20).build());
            account.active = row.near() && view.account() >= row.ransom();
        }
        addRenderableWidget(Button.builder(Component.translatable(KEY + "cancel"), b -> onClose())
                .bounds(left() + panelWidth - PAD - cancelWidth, y, cancelWidth, 20).build());
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
        if (success) {
            onClose();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        LawViews.ConfiscationRow row = view.confiscation();
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201818);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | FoundKingdomScreen.readable(row.color()));
        centeredName(graphics, Component.translatable(KEY + "heading", row.kingdomName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(row.color()), panelWidth - 2 * PAD);
        int y = top + 20;
        Component[] lines = lines();
        int[] colors = {0xFFF0D070, 0xFFB8B8B8, row.near() || row.gone() ? 0xFFA0E0A0 : 0xFFFF9070, 0xFFE8E8E8};
        for (int i = 0; i < lines.length; i++) {
            name(graphics, lines[i].getString(), x, y, colors[i], right - x);
            y += LINE + 2;
        }
        int columns = Math.max(1, Math.min(COLUMNS, (right - x) / CELL));
        int rows = gridRows();
        int shown = Math.min(view.goods().size(), columns * rows);
        for (int i = 0; i < shown; i++) {
            int cx = x + (i % columns) * CELL;
            int cy = gridTop() + (i / columns) * CELL;
            graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF303030);
            ItemStack stack = view.goods().get(i);
            graphics.renderItem(stack, cx + 1, cy + 1);
            graphics.renderItemDecorations(font, stack, cx + 1, cy + 1);
            if (minecraft != null && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                // The item's own tooltip, drawn after the buttons like every hint of the screen.
                for (Component line : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack)) {
                    hoverText(mouseX, mouseY, 1, line);
                }
            }
        }
        if (view.goods().size() > shown) {
            Component more = Component.translatable(KEY + "more", view.goods().size() - shown);
            int moreY = gridTop() + rows * CELL;
            if (moreY + LINE < top + panelHeight - bottomArea()) {
                label(graphics, more, x, moreY, 0xFF9A9A9A, right);
            }
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
