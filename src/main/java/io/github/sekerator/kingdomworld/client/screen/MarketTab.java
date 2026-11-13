package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The "Market" tab of the throne screen: what the kingdom sells and buys on its market and at what price (the king's or
 * the auto price), the lots of players, what its merchants sell abroad and what it ordered from abroad, and the settings:
 * the tax on lots, the exchange fee of its bank, whether outsiders and other currencies are welcome. Members with the
 * right to manage the market change them; every change is an intent the server checks.
 */
final class MarketTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int BOX = 64;
    private static final String KEY = "kingdomworld.screen.market.";

    enum Mode {
        SELL,
        BUY,
        LOTS,
        EXPORT,
        IMPORT,
        SETTINGS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean adds() {
            return this == SELL || this == BUY || this == EXPORT || this == IMPORT;
        }
    }

    private final ThroneScreen screen;
    private final ItemPicker picker;
    private TradeViews.MarketView view;
    private Mode mode = Mode.SELL;
    private boolean form;
    private ItemStack chosen = ItemStack.EMPTY;
    private String priceText = "";
    private String firstText = "";
    private String secondText = "";
    private String taxText;
    private String feeText;
    private int scroll;

    private int modeWidth;
    private int addWidth;
    private int removeWidth;
    private int labelWidth;
    private int secondLabelWidth;
    private int chooseWidth;
    private int cancelWidth;
    private int setWidth;
    private int toggleWidth;

    MarketTab(ThroneScreen screen) {
        this.screen = screen;
        this.picker = new ItemPicker(screen);
    }

    TradeViews.MarketView view() {
        return view;
    }

    void setView(TradeViews.MarketView value) {
        boolean first = view == null || !view.kingdomId().equals(value.kingdomId());
        view = value;
        if (first || taxText == null) {
            taxText = Integer.toString(value.salesTaxPermille() / 10);
            feeText = Integer.toString(value.feePermille() / 10);
        }
    }

    void setMode(Mode value) {
        mode = value;
        form = false;
        picker.close();
        scroll = 0;
    }

    /**
     * Shows a view, an open form or the search of items, for the layout check.
     */
    void showForCheck(Mode value, boolean withForm, ItemStack item, String price, String first, String second, String search) {
        setMode(value);
        form = withForm;
        chosen = item;
        priceText = price;
        firstText = first;
        secondText = second;
        if (search != null) {
            picker.showForCheck(search, value == Mode.IMPORT && view != null ? new HashSet<>(view.importable()) : null);
        }
    }

    String checkName() {
        return "market-" + mode.id() + (picker.isOpen() ? "-search" : form ? "-form" : "");
    }

    // ---------------------------------------------------------------- texts

    private CurrencyUnits units() {
        return view == null ? CurrencyUnits.DEFAULT : view.own().units();
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, units());
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private Component price(long manual, long auto) {
        return manual > 0 ? money(manual) : Component.translatable(KEY + "auto", money(auto));
    }

    private Component addLabel() {
        return Component.translatable(mode == Mode.IMPORT ? KEY + "order" : KEY + "add");
    }

    private static Component firstLabel(Mode value) {
        return Component.translatable(KEY + "first." + value.id());
    }

    private List<Component> summary() {
        TradeViews.Day day = view.day();
        return switch (mode) {
            case EXPORT -> List.of(Component.translatable(KEY + "export_day", day.merchants(), TradeTexts.items(day.exported()),
                    day.exportVolume(), money(day.exportIncome()), money(day.exportMaxIncome())));
            case IMPORT -> List.of(Component.translatable(KEY + "import_day", TradeTexts.items(day.imported()), day.importMax(),
                    money(day.importCost())));
            default -> List.of(Component.translatable(KEY + "day", money(day.sold()), money(day.bought()), money(day.lotTax())));
        };
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        addWidth = screen.buttonWidth(Component.translatable(KEY + "add"), Component.translatable(KEY + "order"));
        removeWidth = screen.buttonWidth(Component.translatable(KEY + "remove"));
        chooseWidth = screen.buttonWidth(Component.translatable(KEY + "choose"));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        setWidth = screen.buttonWidth(Component.translatable(KEY + "set"));
        toggleWidth = 0;
        for (String key : List.of("open.on", "open.off", "foreign.on", "foreign.off")) {
            toggleWidth = Math.max(toggleWidth, screen.buttonWidth(Component.translatable(KEY + key)));
        }
        labelWidth = 0;
        for (String key : List.of("price_label", "second_label", "tax_label", "fee_label")) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(KEY + key)) + 6);
        }
        secondLabelWidth = 0;
        for (Mode value : Mode.values()) {
            if (value.adds()) {
                secondLabelWidth = Math.max(secondLabelWidth, font.width(firstLabel(value)) + 6);
            }
        }
        int buttons = modeWidth + GAP + addWidth;
        int formRow = labelWidth + BOX + 8 + secondLabelWidth + BOX;
        int settings = labelWidth + BOX + GAP + setWidth;
        int actions = labelWidth + BOX + 8 + addWidth + GAP + cancelWidth;
        formRow = Math.max(formRow, Math.max(settings, chooseWidth + 24 + 80));
        int messages = 0;
        for (String key : List.of("loading", "empty", "read_only", "hint_sell", "hint_buy", "hint_export", "hint_import", "hint_lots")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(buttons, Math.max(formRow, actions)), Math.max(messages, Math.max(toggleWidth, picker.measure(font))));
    }

    private static int buttonsY(int top) {
        return top + ThroneScreen.CONTENT_TOP;
    }

    private static int listTop(int top) {
        return buttonsY(top) + ROW + 4;
    }

    private static int bottom(int top, int panelHeight) {
        return top + panelHeight - ThroneScreen.BOTTOM_AREA;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case SELL -> view.sells().size();
            case BUY -> view.buys().size();
            case LOTS -> view.lots().size();
            case EXPORT -> view.exports().size();
            case IMPORT -> view.imports().size();
            case SETTINGS -> 0;
        };
    }

    private int visibleRows(int top, int panelHeight) {
        return Math.max(1, (bottom(top, panelHeight) - listTop(top) - 11) / ROW);
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        screen.add(new CycleButton(x, buttonsY(top), modeWidth, ROW - 2, modeLabel(mode), direction -> {
            setMode(Cycle.step(mode, direction));
            screen.rebuild();
        }));
        if (mode.adds() && view.canManage() && !form && !picker.isOpen()) {
            screen.add(Button.builder(addLabel(), b -> {
                form = true;
                chosen = ItemStack.EMPTY;
                priceText = "";
                firstText = "";
                secondText = "";
                screen.rebuild();
            }).bounds(x + modeWidth + GAP, buttonsY(top), addWidth, ROW - 2).build());
        }
        if (picker.isOpen()) {
            picker.init(x, right, listTop(top), bottom(top, panelHeight));
            return;
        }
        if (mode == Mode.SETTINGS) {
            initSettings(x, top);
            return;
        }
        if (form) {
            initForm(x, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (!view.canManage() || mode == Mode.LOTS || mode == Mode.IMPORT) {
            return;
        }
        for (int i = 0; i < rows && scroll + i < rowCount(); i++) {
            String item = itemId(rowStack(scroll + i));
            TradePackets.MarketChange.Action remove = switch (mode) {
                case SELL -> TradePackets.MarketChange.Action.REMOVE_SELL;
                case BUY -> TradePackets.MarketChange.Action.REMOVE_BUY;
                default -> TradePackets.MarketChange.Action.REMOVE_EXPORT;
            };
            screen.add(Button.builder(Component.translatable(KEY + "remove"), b -> send(remove, item, 0, 0, 0))
                    .bounds(right - removeWidth, listTop(top) + i * ROW, removeWidth, ROW - 2).build());
        }
    }

    private ItemStack rowStack(int index) {
        return switch (mode) {
            case SELL -> view.sells().get(index).sample();
            case BUY -> view.buys().get(index).sample();
            case LOTS -> view.lots().get(index).stack();
            case EXPORT -> view.exports().get(index).sample();
            case IMPORT -> view.imports().get(index).sample();
            case SETTINGS -> ItemStack.EMPTY;
        };
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private EditBox box(int x, int y, String value, java.util.function.Consumer<String> responder, Component name, Component hint) {
        EditBox box = new EditBox(screen.font(), x + 1, y + 1, BOX - 2, ROW - 4, name);
        box.setMaxLength(Money.MAX_TEXT);
        box.setValue(value);
        box.setResponder(responder);
        if (hint != null) {
            box.setHint(hint);
        }
        screen.add(box);
        return box;
    }

    private boolean hasPrice() {
        return mode == Mode.SELL || mode == Mode.BUY;
    }

    /**
     * The form: the item on the first row, the price and the number on the second, the budget and the buttons on the third.
     */
    private void initForm(int x, int top) {
        int y = listTop(top);
        screen.add(Button.builder(Component.translatable(KEY + "choose"), b -> {
            picker.open(mode == Mode.IMPORT ? new HashSet<>(view.importable()) : null, stack -> chosen = stack);
            screen.rebuild();
        }).bounds(x, y, chooseWidth, ROW - 2).build());
        y += ROW + 1;
        int secondX = x + labelWidth + BOX + 8;
        if (hasPrice()) {
            box(x + labelWidth, y, priceText, value -> priceText = value, Component.translatable(KEY + "price_label"),
                    Component.translatable(KEY + "auto_hint")).setTooltip(Tooltip.create(Component.translatable(KEY + "price_hint")));
            box(secondX + secondLabelWidth, y, firstText, value -> firstText = value, firstLabel(mode), null);
        } else {
            box(x + labelWidth, y, firstText, value -> firstText = value, firstLabel(mode), null);
        }
        y += ROW + 1;
        int buttonsX = x;
        if (mode == Mode.BUY) {
            box(x + labelWidth, y, secondText, value -> secondText = value, Component.translatable(KEY + "second_label"),
                    Component.translatable(KEY + "no_limit"));
            buttonsX = secondX;
        }
        Button add = screen.add(Button.builder(addLabel(), b -> submit()).bounds(buttonsX, y, addWidth, ROW - 2).build());
        add.active = !chosen.isEmpty();
        add.setTooltip(Tooltip.create(hintText(mode)));
        screen.add(Button.builder(Component.translatable(KEY + "cancel"), b -> {
            form = false;
            screen.rebuild();
        }).bounds(buttonsX + addWidth + GAP, y, cancelWidth, ROW - 2).build());
    }

    private static long number(String text) {
        try {
            String value = text.strip();
            return value.isEmpty() ? 0 : Math.max(0, Math.min(Money.MAX, Long.parseLong(value)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long amount(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return Money.parse(text, units()).orElse(-1L);
    }

    private void submit() {
        if (chosen.isEmpty()) {
            return;
        }
        String item = itemId(chosen);
        long price = amount(priceText);
        long first = number(firstText);
        long second = amount(secondText);
        if (price < 0 || first < 0 || second < 0) {
            screen.onResult(false, Component.translatable(KEY + "bad_number"));
            return;
        }
        switch (mode) {
            case SELL -> send(TradePackets.MarketChange.Action.PUT_SELL, item, price, first, 0);
            case BUY -> send(TradePackets.MarketChange.Action.PUT_BUY, item, price, Math.max(1, first), second);
            case EXPORT -> send(TradePackets.MarketChange.Action.PUT_EXPORT, item, 0, first, 0);
            case IMPORT -> send(TradePackets.MarketChange.Action.ORDER_IMPORT, item, 0, Math.max(1, first), 0);
            default -> {
                return;
            }
        }
        form = false;
    }

    private void initSettings(int x, int top) {
        int boxX = x + labelWidth;
        int y = listTop(top);
        EditBox tax = box(boxX, y, taxText, value -> taxText = value, Component.translatable(KEY + "tax_label"), null);
        tax.setEditable(view.canManage());
        Button setTax = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            long value = number(taxText);
            if (value >= 0) {
                send(TradePackets.MarketChange.Action.SET_TAX, "", 0, Math.min(1000, value * 10), 0);
            }
        }).bounds(boxX + BOX + GAP, y, setWidth, ROW - 2).build());
        setTax.active = view.canManage();
        setTax.setTooltip(Tooltip.create(Component.translatable(KEY + "tax_hint", TradeTexts.percent(view.maxSalesTaxPermille()))));
        y += ROW + 1;
        EditBox fee = box(boxX, y, feeText, value -> feeText = value, Component.translatable(KEY + "fee_label"), null);
        fee.setEditable(view.canManage());
        Button setFee = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            long value = number(feeText);
            if (value >= 0) {
                send(TradePackets.MarketChange.Action.SET_FEE, "", 0, Math.min(1000, value * 10), 0);
            }
        }).bounds(boxX + BOX + GAP, y, setWidth, ROW - 2).build());
        setFee.active = view.canManage();
        setFee.setTooltip(Tooltip.create(Component.translatable(KEY + "fee_hint", TradeTexts.percent(view.maxFeePermille()))));
        y += ROW + 3;
        Button open = screen.add(Button.builder(Component.translatable(view.openToOutsiders() ? KEY + "open.on" : KEY + "open.off"),
                b -> send(TradePackets.MarketChange.Action.SET_OPEN, "", 0, view.openToOutsiders() ? 0 : 1, 0))
                .bounds(x, y, toggleWidth, ROW - 2).build());
        open.active = view.canManage();
        y += ROW + 1;
        Button foreign = screen.add(Button.builder(Component.translatable(view.foreignCurrency() ? KEY + "foreign.on" : KEY + "foreign.off"),
                b -> send(TradePackets.MarketChange.Action.SET_FOREIGN, "", 0, view.foreignCurrency() ? 0 : 1, 0))
                .bounds(x, y, toggleWidth, ROW - 2).build());
        foreign.active = view.canManage();
    }

    private void send(TradePackets.MarketChange.Action action, String item, long price, long first, long second) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new TradePackets.MarketChange(view.kingdomId(), action, item, price, first, second));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        if (picker.isOpen()) {
            return picker.scroll(listTop(top), bottom(top, panelHeight), delta);
        }
        int max = Math.max(0, rowCount() - visibleRows(top, panelHeight));
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        int y = top + ThroneScreen.CONTENT_TOP;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, y + 4, 0xFFB8B8B8, right);
            return;
        }
        if (picker.isOpen()) {
            picker.render(graphics, font, x, right, listTop(top), bottom(top, panelHeight));
            return;
        }
        if (mode == Mode.SETTINGS) {
            renderSettings(graphics, x, right, top);
            return;
        }
        if (form) {
            renderForm(graphics, x, right, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        if (count == 0) {
            screen.name(graphics, Component.translatable(KEY + "empty").getString(), x, listTop(top) + 5, 0xFFB8B8B8, right - x);
            return;
        }
        int listTop = listTop(top);
        boolean buttons = view.canManage() && mode != Mode.LOTS && mode != Mode.IMPORT;
        int textRight = buttons ? right - removeWidth - 4 : right;
        int nameWidth = Math.min(110, (textRight - x - 20) / 2);
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int index = scroll + i;
            int rowY = listTop + i * ROW;
            ItemStack stack = rowStack(index);
            graphics.renderItem(stack, x, rowY);
            if (mode == Mode.LOTS) {
                graphics.renderItemDecorations(font, stack, x, rowY);
            }
            screen.name(graphics, stack.getHoverName().getString(), x + 20, rowY + 4, 0xFFFFFFFF, nameWidth - 4);
            int detailX = x + 20 + nameWidth;
            screen.name(graphics, detail(index).getString(), detailX, rowY + 4, 0xFFD0D0D0, textRight - detailX);
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private Component detail(int index) {
        return switch (mode) {
            case SELL -> {
                TradeViews.SellRow row = view.sells().get(index);
                yield Component.translatable(KEY + "row.sell", price(row.price(), row.auto()), TradeTexts.items(row.stock()), row.keep());
            }
            case BUY -> {
                TradeViews.BuyRow row = view.buys().get(index);
                Component budget = row.budget() > 0 ? Component.translatable(KEY + "budget", money(row.spent()), money(row.budget()))
                        : Component.translatable(KEY + "budget_none");
                yield Component.translatable(KEY + "row.buy", price(row.price(), row.auto()), row.max(), TradeTexts.items(row.stock()), budget);
            }
            case LOTS -> {
                TradeViews.LotRow row = view.lots().get(index);
                yield Component.translatable(KEY + "row.lot", money(row.price()), row.seller());
            }
            case EXPORT -> {
                TradeViews.ExportRow row = view.exports().get(index);
                yield Component.translatable(KEY + "row.export", row.above(), TradeTexts.items(row.stock()), money(row.income() / 10));
            }
            case IMPORT -> {
                TradeViews.ImportRow row = view.imports().get(index);
                Component when = row.ticksLeft() > 0 ? Component.translatable(KEY + "arrives", TradeTexts.ticks(row.ticksLeft()))
                        : Component.translatable(KEY + "waits_room");
                yield Component.translatable(KEY + "row.import", TradeTexts.items(row.count()), money(row.cost()), when);
            }
            case SETTINGS -> Component.empty();
        };
    }

    private void renderForm(GuiGraphics graphics, int x, int right, int top) {
        int y = listTop(top);
        int itemX = x + chooseWidth + 4;
        if (chosen.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_item").getString(), itemX, y + 5, 0xFFB8B8B8, right - itemX);
        } else if (itemX + 20 < right) {
            graphics.renderItem(chosen, itemX, y);
            screen.name(graphics, chosen.getHoverName().getString(), itemX + 18, y + 5, 0xFFFFFFFF, right - itemX - 18);
        }
        y += ROW + 1;
        int secondX = x + labelWidth + BOX + 8;
        if (hasPrice()) {
            screen.label(graphics, Component.translatable(KEY + "price_label"), x, y + 5, 0xFFE8E8E8, x + labelWidth - 2);
            screen.label(graphics, firstLabel(mode), secondX, y + 5, 0xFFE8E8E8, secondX + secondLabelWidth - 2);
        } else {
            screen.label(graphics, firstLabel(mode), x, y + 5, 0xFFE8E8E8, x + Math.max(labelWidth, secondLabelWidth) - 2);
        }
        y += ROW + 1;
        if (mode == Mode.BUY) {
            screen.label(graphics, Component.translatable(KEY + "second_label"), x, y + 5, 0xFFE8E8E8, x + labelWidth - 2);
        }
    }

    private void preview(GuiGraphics graphics, int x, int right, int y, String text) {
        if (text.isBlank() || x + 8 >= right) {
            return;
        }
        Optional<Long> parsed = Money.parse(text, units());
        String shown = parsed.map(value -> money(value).getString()).orElseGet(() -> Component.translatable(KEY + "bad_number").getString());
        screen.name(graphics, shown, x, y, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - x);
    }

    private void renderSettings(GuiGraphics graphics, int x, int right, int top) {
        int y = listTop(top);
        int labelRight = x + labelWidth - 2;
        screen.label(graphics, Component.translatable(KEY + "tax_label"), x, y + 5, 0xFFE8E8E8, labelRight);
        int noteX = x + labelWidth + BOX + 2 + setWidth + 6;
        if (noteX + 8 < right) {
            screen.name(graphics, Component.translatable(KEY + "now", TradeTexts.percent(view.salesTaxPermille())).getString(), noteX, y + 5,
                    0xFFB8B8B8, right - noteX);
        }
        y += ROW + 1;
        screen.label(graphics, Component.translatable(KEY + "fee_label"), x, y + 5, 0xFFE8E8E8, labelRight);
        if (noteX + 8 < right) {
            screen.name(graphics, Component.translatable(KEY + "now", TradeTexts.percent(view.feePermille())).getString(), noteX, y + 5,
                    0xFFB8B8B8, right - noteX);
        }
    }

    private static Component hintText(Mode value) {
        return Component.translatable(switch (value) {
            case SELL -> KEY + "hint_sell";
            case BUY -> KEY + "hint_buy";
            case EXPORT -> KEY + "hint_export";
            case IMPORT -> KEY + "hint_import";
            default -> KEY + "hint_lots";
        });
    }

    boolean editing() {
        return form || picker.isOpen() || mode == Mode.SETTINGS;
    }

    /**
     * The status lines at the bottom while there is no other message: the counters of the day, and in a form how it works.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (form && view.canManage()) {
            return hintText(mode);
        }
        if (mode == Mode.SETTINGS) {
            Component rate = Component.translatable(KEY + "rate", TradeTexts.rate(view.own().rate()));
            return view.canManage() ? rate : Component.translatable(KEY + "rate_read_only", rate);
        }
        return summary().get(0);
    }
}
