package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ShopView;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The window of a shop of citizens: what it sells, the cheapest first, with the price with the tax as this player pays it; what
 * of the inventory of the player it buys and for how much; and what the shop is, its trade and the workstations of its place,
 * where a member with the right over citizens fixes its trade. The player buys whole items from the account or with coins of the
 * currency of the kingdom of the shop and sells onto the account; the server checks the price the player saw and answers with a
 * fresh view.
 */
public class ShopScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int BOX = 40;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.shop.";

    enum Tab {
        BUY,
        SELL,
        ABOUT;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ShopView view;
    private Tab tab = Tab.BUY;
    private boolean account = true;
    private int scroll;
    private String countText = "1";
    private Component status = Component.empty();
    private boolean statusIsError;
    /** The trade chosen to fix, by its place among the trades the place serves. */
    private int pinChoice = -1;

    private int panelWidth;
    private int panelHeight;
    private int tabWidth;
    private int payWidth;
    private int actionWidth;
    private int pageWidth;
    private int doneWidth;
    private int countLabelWidth;
    private int pinWidth;
    private int tradeWidth;

    public ShopScreen(ShopView view) {
        super(Component.translatable("kingdomworld.business.trade." + view.trade()));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "shop-" + tab.id();
    }

    public long shopId() {
        return view.shopId();
    }

    public void update(ShopView newView) {
        view = newView;
        rebuildWidgets();
    }

    /**
     * Shows a tab, for the layout check.
     */
    public void showForCheck(String value, boolean fromAccount, String count) {
        tab = Tab.valueOf(value.toUpperCase(Locale.ROOT));
        account = fromAccount;
        countText = count;
    }

    // ---------------------------------------------------------------- texts

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view.units());
    }

    private static Component tabLabel(Tab value) {
        return Component.translatable(KEY + "tab." + value.id());
    }

    private static Component tradeName(String trade) {
        return Component.translatable("kingdomworld.business.trade." + trade);
    }

    private static Component stationName(String station) {
        return Component.translatable("kingdomworld.station." + station);
    }

    private Component payLabel() {
        return Component.translatable(account ? "kingdomworld.screen.stall.pay.account" : "kingdomworld.screen.stall.pay.coins");
    }

    private int count() {
        try {
            return Math.max(1, Math.min(64 * 36, Integer.parseInt(countText.strip())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * The workstations of the place, joined, or why none are known.
     */
    private Component stations() {
        ShopView.Workshop workshop = view.workshop();
        if (!workshop.surveyed()) {
            return Component.translatable(KEY + "stations.unknown");
        }
        if (workshop.stations().isEmpty()) {
            return Component.translatable(KEY + "stations", Component.translatable(KEY + "stations.none"));
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < workshop.stations().size(); i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append(stationName(workshop.stations().get(i)));
        }
        return Component.translatable(KEY + "stations", list);
    }

    private List<Component> aboutLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "about.trade", tradeName(view.trade())));
        ShopView.Workshop workshop = view.workshop();
        if (!workshop.pinned().isEmpty()) {
            lines.add(Component.translatable(KEY + "about.pinned", tradeName(workshop.pinned())));
        }
        if (!workshop.nextTrade().isEmpty() && !workshop.nextTrade().equals(view.trade())) {
            lines.add(Component.translatable(KEY + "about.next", tradeName(workshop.nextTrade())));
        }
        lines.add(stations());
        lines.add(view.keeper().isEmpty() ? Component.translatable(KEY + "about.family")
                : Component.translatable(KEY + "about.keepers", view.keeper()));
        lines.add(Component.translatable(KEY + "about.status", Component.translatable("kingdomworld.business.status." + view.status())));
        lines.add(Component.translatable(KEY + "about.margin", TradeTexts.percent(view.margin()), TradeTexts.percent(view.salesTax())));
        lines.add(Component.translatable(KEY + "about.sales", money(view.sales())));
        lines.add(view.debt() > 0 ? Component.translatable(KEY + "about.debt", money(view.debt())) : Component.translatable(KEY + "about.no_debt"));
        lines.add(Component.translatable(KEY + "about.currency", view.currencyName()));
        return lines;
    }

    /**
     * The trade the fixing button would fix: the one chosen, else the one fixed, else the one the shop has.
     */
    private String pinTrade() {
        List<String> trades = view.workshop().trades();
        if (trades.isEmpty()) {
            return view.trade();
        }
        if (pinChoice < 0 || pinChoice >= trades.size()) {
            String current = view.workshop().pinned().isEmpty() ? view.trade() : view.workshop().pinned();
            int index = trades.indexOf(current);
            pinChoice = Math.max(0, index);
        }
        return trades.get(pinChoice);
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        tabWidth = 0;
        for (Tab value : Tab.values()) {
            tabWidth = Math.max(tabWidth, buttonWidth(tabLabel(value)));
        }
        payWidth = buttonWidth(Component.translatable("kingdomworld.screen.stall.pay.account"),
                Component.translatable("kingdomworld.screen.stall.pay.coins"));
        actionWidth = buttonWidth(Component.translatable(KEY + "buy"), Component.translatable(KEY + "sell"));
        pageWidth = buttonWidth(Component.literal("<"), Component.literal(">"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        countLabelWidth = font.width(Component.translatable("kingdomworld.screen.stall.count_label")) + 6;
        pinWidth = buttonWidth(Component.translatable(KEY + "pin"), Component.translatable(KEY + "unpin"));
        tradeWidth = 0;
        for (String trade : view.workshop().trades()) {
            tradeWidth = Math.max(tradeWidth, buttonWidth(tradeName(trade)));
        }
        int tabs = Tab.values().length * (tabWidth + GAP) + 2 * (pageWidth + GAP);
        int controls = payWidth + GAP + countLabelWidth + BOX;
        // Room for the name of an item, its price in words and the button, as far as the screen allows.
        int inner = Math.max(Math.max(tabs, controls), 300 + actionWidth);
        inner = Math.max(inner, tradeWidth + GAP + pinWidth + GAP + doneWidth);
        for (Component line : aboutLines()) {
            inner = Math.max(inner, Math.min(360, font.width(line)));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
        // The trades and the fixing button share the row of the button that closes the window.
        int room = panelWidth - 2 * PAD - doneWidth - 2 * GAP - pinWidth;
        tradeWidth = Math.max(40, Math.min(tradeWidth, room));
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int tabsY() {
        return top() + 20 + LINE + 1;
    }

    private int controlsY() {
        return tabsY() + ROW;
    }

    private int listTop() {
        return controlsY() + ROW + 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int visibleRows() {
        return Math.max(1, (top() + panelHeight - bottomArea() - listTop() - 11) / ROW);
    }

    private int pages() {
        return Math.max(1, (view.count() + ShopView.PAGE - 1) / ShopView.PAGE);
    }

    private int listSize() {
        return tab == Tab.SELL ? view.sell().size() : view.rows().size();
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        for (Tab value : Tab.values()) {
            Button button = addRenderableWidget(Button.builder(tabLabel(value), b -> {
                tab = value;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, tabsY(), tabWidth, ROW - 2).build());
            button.active = tab != value;
            x += tabWidth + GAP;
        }
        if (tab == Tab.BUY && pages() > 1) {
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page(view.page() + 1))
                    .bounds(right - pageWidth, tabsY(), pageWidth, ROW - 2).build());
            next.active = view.page() + 1 < pages();
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page(view.page() - 1))
                    .bounds(right - 2 * pageWidth - GAP, tabsY(), pageWidth, ROW - 2).build());
            previous.active = view.page() > 0;
        }
        if (tab == Tab.BUY || tab == Tab.SELL) {
            x = left + PAD;
            int y = controlsY();
            if (tab == Tab.BUY) {
                addRenderableWidget(Button.builder(payLabel(), b -> {
                    account = !account;
                    rebuildWidgets();
                }).bounds(x, y, payWidth, ROW - 2).build());
            }
            int next = tab == Tab.BUY ? x + payWidth + GAP : x;
            EditBox count = new EditBox(font, next + countLabelWidth + 1, y + 1, BOX - 2, ROW - 4,
                    Component.translatable("kingdomworld.screen.stall.count_label"));
            count.setMaxLength(4);
            count.setValue(countText);
            count.setResponder(value -> countText = value);
            addRenderableWidget(count);
            int rows = visibleRows();
            scroll = Math.max(0, Math.min(scroll, listSize() - rows));
            for (int i = 0; i < rows && scroll + i < listSize(); i++) {
                int rowY = listTop() + i * ROW;
                if (tab == Tab.BUY) {
                    ShopView.Row row = view.rows().get(scroll + i);
                    Button buy = addRenderableWidget(Button.builder(Component.translatable(KEY + "buy"), b -> buy(row))
                            .bounds(right - actionWidth, rowY, actionWidth, ROW - 2).build());
                    buy.active = view.canBuy() && row.count() > 0;
                    buy.setTooltip(Tooltip.create(Component.translatable(KEY + "price", money(row.price()), money(row.tax()))));
                } else {
                    ShopView.SellRow row = view.sell().get(scroll + i);
                    Button sell = addRenderableWidget(Button.builder(Component.translatable(KEY + "sell"), b -> sell(row))
                            .bounds(right - actionWidth, rowY, actionWidth, ROW - 2).build());
                    sell.active = view.canBuy() && row.max() > 0 && row.money() > 0;
                    sell.setTooltip(Tooltip.create(sellHint(row)));
                }
            }
        }
        if (tab == Tab.ABOUT && view.workshop().canPin() && !view.workshop().trades().isEmpty()) {
            int y = top() + panelHeight - 26;
            String chosen = pinTrade();
            addRenderableWidget(new CycleButton(left + PAD, y, tradeWidth, 20, tradeName(chosen), direction -> {
                int size = view.workshop().trades().size();
                pinChoice = Math.floorMod(pinChoice + direction, size);
                rebuildWidgets();
            }));
            boolean fixed = !view.workshop().pinned().isEmpty() && view.workshop().pinned().equals(chosen);
            Button pin = addRenderableWidget(Button.builder(Component.translatable(KEY + (fixed ? "unpin" : "pin")), b -> pin(fixed ? "" : chosen))
                    .bounds(left + PAD + tradeWidth + GAP, y, pinWidth, 20).build());
            pin.setTooltip(Tooltip.create(Component.translatable(KEY + (fixed ? "unpin.hint" : "pin.hint"), tradeName(chosen))));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private Component sellHint(ShopView.SellRow row) {
        if (row.max() <= 0) {
            return Component.translatable(KEY + "not_wanted");
        }
        if (row.money() <= 0) {
            return Component.translatable(KEY + "till_short");
        }
        return Component.translatable(KEY + "sell.hint", money(row.price()), TradeTexts.items(Math.min(row.max(), row.money())));
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private void buy(ShopView.Row row) {
        if (Minecraft.getInstance().getConnection() != null) {
            int count = (int) Math.min(count(), row.count());
            String key = row.key().isEmpty() ? itemId(row.item()) : row.key();
            KingdomWorldNetwork.sendToServer(new CommercePackets.ShopTrade(view.shopId(), CommercePackets.ShopTrade.Action.BUY, key, count,
                    row.price(), account));
        }
    }

    private void sell(ShopView.SellRow row) {
        if (Minecraft.getInstance().getConnection() != null) {
            int count = (int) Math.max(1, Math.min(count(), Math.min(row.have(), Math.min(row.max(), row.money()))));
            KingdomWorldNetwork.sendToServer(new CommercePackets.ShopTrade(view.shopId(), CommercePackets.ShopTrade.Action.SELL, itemId(row.item()),
                    count, row.price(), true));
        }
    }

    private void pin(String trade) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CommercePackets.ShopPin(view.shopId(), trade));
        }
    }

    private void page(int page) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CommercePackets.ShopPage(view.shopId(), Math.max(0, page)));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, listSize() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if ((tab == Tab.BUY || tab == Tab.SELL) && next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        int x = left + PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF8A6A3A);
        String title = Component.translatable(KEY + "title", tradeName(view.trade()), view.kingdomName()).getString();
        centeredName(graphics, title, width / 2, top + 7, 0xFFF0D070, panelWidth - 2 * PAD);
        Component who = view.keeper().isEmpty() ? Component.translatable(KEY + "about.family") : Component.translatable(KEY + "about.keepers",
                view.keeper());
        name(graphics, who.getString(), x, top + 20, 0xFFD0D0D0, right - x);
        if (tab == Tab.BUY || tab == Tab.SELL) {
            int countX = tab == Tab.BUY ? x + payWidth + GAP : x;
            label(graphics, Component.translatable("kingdomworld.screen.stall.count_label"), countX, controlsY() + 5, 0xFFE8E8E8,
                    countX + countLabelWidth - 2);
            if (tab == Tab.SELL) {
                int hintX = countX + countLabelWidth + BOX + 4;
                Component hint = Component.translatable(KEY + "sell.account");
                int drawn = name(graphics, hint.getString(), hintX, controlsY() + 5, 0xFFB8B8B8, right - hintX);
                hoverText(hintX, controlsY() + 5, drawn, hint);
            }
            int rows = visibleRows();
            int size = listSize();
            if (size == 0) {
                Component empty = Component.translatable(tab == Tab.BUY ? KEY + "empty" : KEY + "sell.empty");
                paragraph(graphics, empty, x, listTop() + 5, right - x, 2, 0xFFB8B8B8);
            }
            int textRight = right - actionWidth - 4;
            int nameWidth = Math.min(120, (textRight - x - 20) / 2);
            for (int i = 0; i < rows && scroll + i < size; i++) {
                int rowY = listTop() + i * ROW;
                ItemStack item;
                Component detail;
                Component hover;
                int color = 0xFFD0D0D0;
                if (tab == Tab.BUY) {
                    ShopView.Row row = view.rows().get(scroll + i);
                    item = row.item();
                    detail = Component.translatable(row.made() ? KEY + "row.made" : KEY + "row", money(row.price()), TradeTexts.items(row.count()));
                    hover = Component.translatable(KEY + "price", money(row.price()), money(row.tax()));
                } else {
                    ShopView.SellRow row = view.sell().get(scroll + i);
                    item = row.item();
                    long takes = Math.min(row.max(), row.money());
                    detail = row.max() <= 0 ? Component.translatable(KEY + "sell.row.full", money(row.price()))
                            : row.money() <= 0 ? Component.translatable(KEY + "sell.row.till", money(row.price()))
                            : Component.translatable(KEY + "sell.row", money(row.price()), TradeTexts.items(takes));
                    hover = sellHint(row);
                    if (takes <= 0) {
                        color = 0xFFB0A090;
                    }
                }
                graphics.renderItem(item, x, rowY + 1);
                name(graphics, item.getHoverName().getString(), x + 20, rowY + 5, 0xFFFFFFFF, nameWidth - 4);
                int detailX = x + 20 + nameWidth;
                int drawn = name(graphics, detail.getString(), detailX, rowY + 5, color, textRight - detailX);
                hoverText(detailX, rowY + 5, drawn, hover);
            }
            if (size > rows) {
                Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(size, scroll + rows), size);
                label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
            }
        } else {
            int y = controlsY() + 2;
            int bottom = top + panelHeight - bottomArea();
            for (Component line : aboutLines()) {
                if (y + LINE > bottom) {
                    break;
                }
                int drawn = name(graphics, line.getString(), x, y, 0xFFE0E0E0, right - x);
                hoverText(x, y, drawn, line);
                y += LINE + 2;
            }
        }
        Component bottomText = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottomText.getString().isEmpty()) {
            Component hint = tab == Tab.SELL ? Component.translatable(KEY + "sell.hint.general")
                    : tab == Tab.ABOUT && view.workshop().canPin() ? Component.translatable(KEY + "pin.general")
                    : Component.translatable(KEY + "hint", TradeTexts.percent(view.salesTax()));
            bottomText = view.canBuy() ? hint
                    : Component.translatable(view.why().isEmpty() ? KEY + "hint" : view.why(), TradeTexts.percent(view.salesTax()));
            color = view.canBuy() ? 0xFFB8B8B8 : 0xFFFFB070;
        }
        paragraph(graphics, bottomText, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
