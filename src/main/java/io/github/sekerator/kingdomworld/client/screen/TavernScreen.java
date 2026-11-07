package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.life.LifeTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.common.network.packet.LifePackets;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * The window of a tavern, opened by talking to its innkeeper: food and drink from the storehouses at the prices of the market,
 * the rumors of the day (a camp of robbers can be marked on the atlas for a small fee) and a room for a day, whose bed becomes
 * the player's point of return. Payment is in coins or from the account, switched by a button.
 */
public class TavernScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 250;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int STATUS_LINES = 2;
    private static final String KEY = "kingdomworld.screen.tavern.";

    enum Mode {
        GOODS,
        RUMORS,
        ROOM;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private LifeViews.TavernView view;
    private Mode mode = Mode.GOODS;
    private boolean account;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int modeWidth;
    private int payWidth;
    private int buyWidth;
    private int markWidth;
    private int roomWidth;
    private int doneWidth;
    private int priceWidth;

    public TavernScreen(LifeViews.TavernView view) {
        super(Component.translatable(KEY + "title"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "tavern-" + mode.id() + (view.open() ? "" : "-closed");
    }

    public void update(LifeViews.TavernView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows a part of the window without asking the server; for the layout check.
     */
    public void showForCheck(String name, boolean fromAccount) {
        mode = Mode.valueOf(name.toUpperCase(Locale.ROOT));
        account = fromAccount;
    }

    private CurrencyUnits units() {
        return view.currency().units();
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, units());
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode." + value.id());
    }

    private Component payLabel(boolean fromAccount) {
        return Component.translatable(KEY + (fromAccount ? "pay_account" : "pay_coins"));
    }

    private Component markLabel() {
        return Component.translatable(KEY + "mark", money(view.rumorPrice()));
    }

    private Component roomLabel() {
        return Component.translatable(KEY + (view.roomLeft() > 0 ? "extend" : "rent"), money(view.roomPrice()));
    }

    private void measure() {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, buttonWidth(modeLabel(value)));
        }
        payWidth = buttonWidth(payLabel(false), payLabel(true));
        buyWidth = buttonWidth(Component.translatable(KEY + "buy"));
        markWidth = buttonWidth(markLabel(), Component.translatable(KEY + "marked"));
        roomWidth = buttonWidth(roomLabel());
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        priceWidth = 0;
        for (LifeViews.Good good : view.goods()) {
            priceWidth = Math.max(priceWidth, font.width(money(good.price())));
        }
        int inner = 3 * modeWidth + 2 * GAP + GAP + payWidth;
        inner = Math.max(inner, 18 + 110 + priceWidth + 6 + buyWidth);
        inner = Math.max(inner, Math.min(360, roomWidth + doneWidth + 2 * GAP));
        for (Component line : roomLines()) {
            inner = Math.max(inner, Math.min(360, font.width(line)));
        }
        inner = Math.max(inner, Math.min(360, font.width(moneyLine())));
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
        return 26 + LINE + 2 + STATUS_LINES * LINE;
    }

    private int listBottom() {
        return top() + panelHeight - bottomArea();
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    private Component moneyLine() {
        return Component.translatable(KEY + "money", money(view.coins()), money(view.account()));
    }

    private List<Component> roomLines() {
        Component free = view.rooms() > 0 ? Component.translatable(KEY + "rooms", view.rooms()) : Component.translatable(KEY + "no_rooms");
        Component price = Component.translatable(KEY + "room_price", money(view.roomPrice()));
        Component own = view.roomLeft() > 0
                ? Component.translatable(KEY + "room_left", LifeTexts.hours(Math.max(1, Math.round(view.roomLeft() / 1000.0))))
                : Component.translatable(KEY + "room_none");
        return List.of(free, price, own, Component.translatable(KEY + "room_hint"));
    }

    private void send(LifePackets.TavernAction.Action action, String item, long value) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new LifePackets.TavernAction(action, item, value, account));
        }
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int x = left() + PAD;
        int y = buttonsY();
        for (Mode value : Mode.values()) {
            Button button = addRenderableWidget(Button.builder(modeLabel(value), b -> {
                mode = value;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, y, modeWidth, ROW - 2).build());
            button.active = value != mode;
            x += modeWidth + GAP;
        }
        addRenderableWidget(Button.builder(payLabel(account), b -> {
            account = !account;
            rebuildWidgets();
        }).bounds(left() + panelWidth - PAD - payWidth, y, payWidth, ROW - 2).build());
        int right = left() + panelWidth - PAD;
        int rows = visibleRows();
        if (mode == Mode.GOODS) {
            scroll = Math.max(0, Math.min(scroll, view.goods().size() - rows));
            for (int i = 0; i < rows && scroll + i < view.goods().size(); i++) {
                LifeViews.Good good = view.goods().get(scroll + i);
                Button buy = addRenderableWidget(Button.builder(Component.translatable(KEY + "buy"),
                        b -> send(LifePackets.TavernAction.Action.BUY, GoodsStacks.itemId(good.stack()), 1))
                        .bounds(right - buyWidth, listTop() + i * ROW, buyWidth, ROW - 2).build());
                buy.active = view.open() && good.stock() > 0;
            }
        } else if (mode == Mode.RUMORS) {
            int top = listTop();
            for (LifeViews.Told told : view.rumors()) {
                int lines = Math.min(3, font.split(told.text(), panelWidth - 2 * PAD).size());
                top += lines * LINE + 2;
                if (told.campId() != 0) {
                    if (top + ROW > listBottom()) {
                        break;
                    }
                    Button mark = addRenderableWidget(Button.builder(told.marked() ? Component.translatable(KEY + "marked") : markLabel(),
                            b -> send(LifePackets.TavernAction.Action.MARK, "", told.campId())).bounds(left() + PAD, top, markWidth, ROW - 2).build());
                    mark.active = view.open() && !told.marked();
                    top += ROW;
                }
                top += 4;
            }
        } else {
            Button room = addRenderableWidget(Button.builder(roomLabel(), b -> send(LifePackets.TavernAction.Action.ROOM, "", 0))
                    .bounds(left() + PAD, listTop() + 4 * (LINE + 2) + 4, roomWidth, ROW - 2).build());
            room.active = view.open() && (view.rooms() > 0 || view.roomLeft() > 0);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(right - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mode == Mode.GOODS) {
            int max = Math.max(0, view.goods().size() - visibleRows());
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | FoundKingdomScreen.readable(view.currency().color()));
        String innkeeper = CitizenTexts.name(view.innkeeper(), view.female() ? Gender.FEMALE : Gender.MALE).getString();
        centeredName(graphics, Component.translatable(KEY + "heading", view.currency().kingdomName(), innkeeper).getString(), width / 2, top + 7,
                0xFFF0D070, panelWidth - 2 * PAD);
        switch (mode) {
            case GOODS -> renderGoods(graphics, x, right, mouseX, mouseY);
            case RUMORS -> renderRumors(graphics, x, right);
            case ROOM -> renderRoom(graphics, x, right);
        }
        int bottom = top + panelHeight - bottomArea();
        name(graphics, moneyLine().getString(), x, bottom + 2, 0xFFE8E8E8, right - x);
        Component line = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (line.getString().isEmpty()) {
            line = Component.translatable(view.open() ? KEY + "hint" : KEY + "closed");
            color = view.open() ? 0xFFB8B8B8 : 0xFFFF9070;
        }
        paragraph(graphics, line, x, bottom + LINE + 4, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private void renderGoods(GuiGraphics graphics, int x, int right, int mouseX, int mouseY) {
        if (view.goods().isEmpty()) {
            paragraph(graphics, Component.translatable(KEY + "no_goods"), x, listTop() + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int rows = visibleRows();
        int priceX = right - buyWidth - 6 - priceWidth;
        for (int i = 0; i < rows && scroll + i < view.goods().size(); i++) {
            LifeViews.Good good = view.goods().get(scroll + i);
            int y = listTop() + i * ROW;
            graphics.renderItem(good.stack(), x, y);
            String name = good.stack().getHoverName().getString();
            Component stock = Component.translatable(KEY + "stock", good.stock());
            int stockWidth = font.width(stock);
            int nameRight = priceX - 6 - stockWidth - 4;
            name(graphics, name, x + 20, y + 5, 0xFFFFFFFF, nameRight - x - 20);
            label(graphics, stock, nameRight + 4, y + 5, 0xFF9A9A9A, priceX - 2);
            label(graphics, money(good.price()), priceX, y + 5, 0xFFF0D070, right - buyWidth - 2);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 && minecraft != null) {
                for (Component tip : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, good.stack())) {
                    hoverText(mouseX, mouseY, 1, tip);
                }
            }
        }
        if (view.goods().size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(view.goods().size(), scroll + rows),
                    view.goods().size());
            label(graphics, page, right - font.width(page), listBottom() - LINE, 0xFF9A9A9A, right);
        }
    }

    private void renderRumors(GuiGraphics graphics, int x, int right) {
        if (view.rumors().isEmpty()) {
            paragraph(graphics, Component.translatable(KEY + "no_rumors"), x, listTop() + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int top = listTop();
        for (LifeViews.Told told : view.rumors()) {
            List<FormattedCharSequence> lines = font.split(told.text(), right - x);
            int count = Math.min(3, lines.size());
            if (top + count * LINE > listBottom()) {
                break;
            }
            paragraph(graphics, told.text(), x, top, right - x, 3, 0xFFE8DCC0);
            top += count * LINE + 2;
            if (told.campId() != 0) {
                top += ROW;
            }
            top += 4;
        }
    }

    private void renderRoom(GuiGraphics graphics, int x, int right) {
        int y = listTop();
        int[] colors = {0xFFE8E8E8, 0xFFF0D070, 0xFFA0E0A0, 0xFFB8B8B8};
        List<Component> lines = roomLines();
        for (int i = 0; i < lines.size(); i++) {
            name(graphics, lines.get(i).getString(), x, y, colors[i], right - x);
            y += LINE + 2;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
