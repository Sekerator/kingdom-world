package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The stall screen: what the kingdom sells and buys and at what price, the lots of players to buy, and the player's own
 * lots with a form to put up what is in the hand. The player pays from the account or with coins, in the kingdom's
 * currency or, where the market takes them, another; the server does the counting and answers with a fresh view.
 */
public class StallScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int BOX = 48;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.stall.";

    enum Mode {
        BUY,
        SELL,
        LOTS,
        MINE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private TradeViews.StallView view;
    private Mode mode = Mode.BUY;
    private boolean account = true;
    private int purse;
    private int scroll;
    private String countText = "1";
    private String priceText = "";
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int tabWidth;
    private int payWidth;
    private int currencyWidth;
    private int actionWidth;
    private int listWidth;
    private int doneWidth;
    private int countLabelWidth;
    private int priceLabelWidth;

    public StallScreen(TradeViews.StallView view) {
        super(Component.translatable("block.kingdomworld.market_stall"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "stall-" + mode.id();
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(TradeViews.StallView newView) {
        view = newView;
        purse = Math.max(0, Math.min(purse, view.purses().size() - 1));
        rebuildWidgets();
    }

    /**
     * Shows a view with a payment and a form, for the layout check.
     */
    public void showForCheck(String value, boolean fromAccount, int purseIndex, String count, String price) {
        mode = Mode.valueOf(value.toUpperCase(Locale.ROOT));
        account = fromAccount;
        purse = Math.max(0, Math.min(purseIndex, view.purses().size() - 1));
        countText = count;
        priceText = price;
    }

    // ---------------------------------------------------------------- texts

    private Optional<TradeViews.Purse> purse() {
        return view.purses().isEmpty() ? Optional.empty() : Optional.of(view.purses().get(purse));
    }

    private TradeViews.Currency payCurrency() {
        return purse().map(TradeViews.Purse::currency).orElse(view.own());
    }

    /**
     * What a price of the kingdom costs in the currency chosen, by the rates and the fee.
     */
    private long cost(long price) {
        TradeViews.Currency currency = payCurrency();
        if (currency.id().equals(view.own().id())) {
            return price;
        }
        return ExchangeRates.priceIn(price, view.own().rate(), currency.rate(), view.feePermille());
    }

    private Component pay(long price) {
        long cost = cost(price);
        return cost < 0 ? Component.translatable(KEY + "too_dear") : MoneyTexts.amount(cost, payCurrency().units());
    }

    private Component own(long amount) {
        return MoneyTexts.amount(amount, view.own().units());
    }

    private static Component tabLabel(Mode value) {
        return Component.translatable(KEY + "tab." + value.id());
    }

    private Component payLabel() {
        if (mode == Mode.SELL) {
            return Component.translatable(account ? KEY + "get.account" : KEY + "get.coins");
        }
        return Component.translatable(account ? KEY + "pay.account" : KEY + "pay.coins");
    }

    private Component currencyLabel() {
        TradeViews.Currency currency = payCurrency();
        String name = currency.name().isEmpty() ? currency.kingdomName() : currency.name();
        return Component.translatable(KEY + "currency", fit(name, 90));
    }

    private Component actionLabel() {
        return Component.translatable(switch (mode) {
            case BUY, LOTS -> KEY + "buy";
            case SELL -> KEY + "sell";
            case MINE -> KEY + "withdraw";
        });
    }

    private int count() {
        try {
            return Math.max(1, Math.min(4_096, Integer.parseInt(countText.strip())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        tabWidth = 0;
        for (Mode value : Mode.values()) {
            tabWidth = Math.max(tabWidth, buttonWidth(tabLabel(value)));
        }
        payWidth = buttonWidth(Component.translatable(KEY + "pay.account"), Component.translatable(KEY + "pay.coins"),
                Component.translatable(KEY + "get.account"), Component.translatable(KEY + "get.coins"));
        currencyWidth = buttonWidth(Component.translatable(KEY + "currency", "W".repeat(15)));
        actionWidth = buttonWidth(Component.translatable(KEY + "buy"), Component.translatable(KEY + "sell"),
                Component.translatable(KEY + "withdraw"));
        listWidth = buttonWidth(Component.translatable(KEY + "list"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        countLabelWidth = font.width(Component.translatable(KEY + "count_label")) + 6;
        priceLabelWidth = font.width(Component.translatable(KEY + "price_label")) + 6;
        int tabs = 4 * (tabWidth + GAP);
        int controls = payWidth + GAP + currencyWidth + GAP + countLabelWidth + BOX;
        int lotForm = countLabelWidth + BOX + 6 + priceLabelWidth + BOX + 2 * GAP + listWidth;
        int inner = Math.max(Math.max(tabs, controls), lotForm);
        inner = Math.max(inner, 180 + actionWidth);
        for (String key : List.of("empty.buy", "empty.sell", "empty.lots", "empty.mine")) {
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

    private int tabsY() {
        return top() + 20 + 2 * (LINE + 1);
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

    private int rowCount() {
        return switch (mode) {
            case BUY -> view.offers().size();
            case SELL -> view.wanted().size();
            case LOTS -> (int) view.lots().stream().filter(lot -> !lot.mine()).count();
            case MINE -> (int) view.lots().stream().filter(TradeViews.LotRow::mine).count();
        };
    }

    private List<TradeViews.LotRow> lots(boolean mine) {
        List<TradeViews.LotRow> lots = new ArrayList<>();
        for (TradeViews.LotRow lot : view.lots()) {
            if (lot.mine() == mine) {
                lots.add(lot);
            }
        }
        return lots;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        for (Mode value : Mode.values()) {
            Button tab = addRenderableWidget(Button.builder(tabLabel(value), b -> {
                mode = value;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, tabsY(), tabWidth, ROW - 2).build());
            tab.active = mode != value && (value != Mode.MINE || view.member() || !lots(true).isEmpty());
            x += tabWidth + GAP;
        }
        x = left + PAD;
        int y = controlsY();
        if (mode == Mode.MINE) {
            EditBox count = new EditBox(font, x + countLabelWidth + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "count_label"));
            count.setMaxLength(4);
            count.setValue(countText);
            count.setResponder(value -> countText = value);
            addRenderableWidget(count);
            int priceX = x + countLabelWidth + BOX + 6;
            EditBox price = new EditBox(font, priceX + priceLabelWidth + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "price_label"));
            price.setMaxLength(Money.MAX_TEXT);
            price.setValue(priceText);
            price.setResponder(value -> priceText = value);
            price.setTooltip(Tooltip.create(Component.translatable(KEY + "price_hint")));
            addRenderableWidget(price);
            Button list = addRenderableWidget(Button.builder(Component.translatable(KEY + "list"), b -> {
                Optional<Long> amount = Money.parse(priceText, view.own().units());
                amount.filter(value -> value > 0).ifPresent(value -> send(TradePackets.StallAction.Action.LIST_LOT, "", 0, count(), value));
            }).bounds(priceX + priceLabelWidth + BOX + 2 * GAP, y, listWidth, ROW - 2).build());
            list.active = view.member() && view.myLots() < view.maxLots();
            list.setTooltip(Tooltip.create(Component.translatable(KEY + "list_hint", view.maxLots(), TradeTexts.percent(view.salesTaxPermille()))));
        } else {
            addRenderableWidget(Button.builder(payLabel(), b -> {
                account = !account;
                rebuildWidgets();
            }).bounds(x, y, payWidth, ROW - 2).build());
            int next = x + payWidth + GAP;
            if (mode != Mode.SELL && view.purses().size() > 1) {
                Button currency = addRenderableWidget(Button.builder(currencyLabel(), b -> {
                    purse = (purse + 1) % view.purses().size();
                    rebuildWidgets();
                }).bounds(next, y, currencyWidth, ROW - 2).build());
                TradeViews.Currency chosen = payCurrency();
                currency.setTooltip(Tooltip.create(MoneyTexts.currency(chosen.name(), chosen.kingdomName())));
                next += currencyWidth + GAP;
            }
            if (mode != Mode.LOTS) {
                EditBox count = new EditBox(font, next + countLabelWidth + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "count_label"));
                count.setMaxLength(4);
                count.setValue(countText);
                count.setResponder(value -> countText = value);
                addRenderableWidget(count);
            }
        }
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        for (int i = 0; i < rows && scroll + i < rowCount(); i++) {
            int index = scroll + i;
            int rowY = listTop() + i * ROW;
            Button action = addRenderableWidget(Button.builder(actionLabel(), b -> act(index)).bounds(right - actionWidth, rowY, actionWidth, ROW - 2)
                    .build());
            action.active = actionActive(index);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private boolean actionActive(int index) {
        return switch (mode) {
            case BUY -> view.offers().get(index).available() > 0 && view.offers().get(index).price() > 0;
            case SELL -> view.wanted().get(index).wanted() > 0 && view.wanted().get(index).carried() > 0;
            case LOTS, MINE -> true;
        };
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private void act(int index) {
        UUID currency = payCurrency().id().equals(view.own().id()) ? null : payCurrency().id();
        switch (mode) {
            case BUY -> send(TradePackets.StallAction.Action.BUY, itemId(view.offers().get(index).sample()), 0, count(), 0, currency);
            case SELL -> send(TradePackets.StallAction.Action.SELL, itemId(view.wanted().get(index).sample()), 0, count(), 0, null);
            case LOTS -> send(TradePackets.StallAction.Action.BUY_LOT, "", lots(false).get(index).id(), 0, 0, currency);
            case MINE -> send(TradePackets.StallAction.Action.WITHDRAW_LOT, "", lots(true).get(index).id(), 0, 0, null);
        }
    }

    private void send(TradePackets.StallAction.Action action, String item, long id, int count, long price) {
        send(action, item, id, count, price, null);
    }

    private void send(TradePackets.StallAction.Action action, String item, long id, int count, long price, UUID currency) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new TradePackets.StallAction(view.pos(), action, item, id, count, price, account, currency));
        }
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

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        int x = left + PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.color());
        centeredName(graphics, Component.translatable(KEY + "title", view.kingdomName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.color()), panelWidth - 2 * PAD);
        Optional<TradeViews.Purse> current = purse();
        if (current.isPresent()) {
            TradeViews.Purse wallet = current.get();
            name(graphics, Component.translatable(KEY + "balance", MoneyTexts.amount(wallet.balance(), wallet.currency().units())).getString(), x,
                    top + 20, 0xFFF0D070, right - x);
            name(graphics, Component.translatable(KEY + "carried", MoneyTexts.coins(wallet.carried())).getString(), x, top + 20 + LINE + 1,
                    0xFFD0D0D0, right - x);
        }
        if (mode == Mode.MINE) {
            label(graphics, Component.translatable(KEY + "count_label"), x, controlsY() + 5, 0xFFE8E8E8, x + countLabelWidth - 2);
            int priceX = x + countLabelWidth + BOX + 6;
            label(graphics, Component.translatable(KEY + "price_label"), priceX, controlsY() + 5, 0xFFE8E8E8, priceX + priceLabelWidth - 2);
        } else if (mode != Mode.LOTS) {
            int countX = x + payWidth + GAP + (mode != Mode.SELL && view.purses().size() > 1 ? currencyWidth + GAP : 0);
            label(graphics, Component.translatable(KEY + "count_label"), countX, controlsY() + 5, 0xFFE8E8E8, countX + countLabelWidth - 2);
        }
        int rows = visibleRows();
        int count = rowCount();
        if (count == 0) {
            paragraph(graphics, Component.translatable(KEY + "empty." + mode.id()), x, listTop() + 5, right - x, 2, 0xFFB8B8B8);
        }
        int textRight = right - actionWidth - 4;
        int nameWidth = Math.min(110, (textRight - x - 20) / 2);
        List<TradeViews.LotRow> lots = mode == Mode.LOTS ? lots(false) : mode == Mode.MINE ? lots(true) : List.of();
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int index = scroll + i;
            int rowY = listTop() + i * ROW;
            ItemStack stack;
            Component detail;
            switch (mode) {
                case BUY -> {
                    TradeViews.Offer offer = view.offers().get(index);
                    stack = offer.sample();
                    detail = Component.translatable(KEY + "row.buy", pay(offer.price()), TradeTexts.items(offer.available()));
                }
                case SELL -> {
                    TradeViews.Wanted wanted = view.wanted().get(index);
                    stack = wanted.sample();
                    detail = wanted.price() > 0 ? Component.translatable(KEY + "row.sell", own(wanted.price()), TradeTexts.items(wanted.wanted()),
                            wanted.carried()) : Component.translatable(KEY + "row.sell_none");
                }
                default -> {
                    TradeViews.LotRow lot = lots.get(index);
                    stack = lot.stack();
                    detail = mode == Mode.LOTS ? Component.translatable(KEY + "row.lot", pay(lot.price()), lot.seller())
                            : Component.translatable(KEY + "row.mine", own(lot.price()));
                }
            }
            graphics.renderItem(stack, x, rowY + 1);
            graphics.renderItemDecorations(font, stack, x, rowY + 1);
            name(graphics, stack.getHoverName().getString(), x + 20, rowY + 5, 0xFFFFFFFF, nameWidth - 4);
            int detailX = x + 20 + nameWidth;
            name(graphics, detail.getString(), detailX, rowY + 5, 0xFFD0D0D0, textRight - detailX);
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint." + mode.id(), TradeTexts.percent(view.salesTaxPermille()));
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
