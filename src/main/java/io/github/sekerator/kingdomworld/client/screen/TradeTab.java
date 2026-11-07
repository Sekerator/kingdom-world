package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The "Trade" tab of the throne screen: the deals of the kingdom with other kingdoms (proposals to answer, proposals
 * waiting for an answer, regular deals), the caravans on the way, and a form to propose a deal. Members with the right to
 * make deals propose, accept, decline and call off; the server checks everything.
 */
final class TradeTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int BOX = 64;
    private static final String KEY = "kingdomworld.screen.trade.";

    enum Mode {
        DEALS,
        CARAVANS,
        PROPOSE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private final ItemPicker picker;
    private TradeViews.TradeView view;
    private Mode mode = Mode.DEALS;
    private int scroll;
    private int partner;
    private boolean weSell = true;
    private boolean ourCurrency = true;
    private ItemStack chosen = ItemStack.EMPTY;
    private String countText = "";
    private String priceText = "";
    private String periodText = "";

    private int modeWidth;
    private int acceptWidth;
    private int declineWidth;
    private int cancelWidth;
    private int labelWidth;
    private int secondLabelWidth;
    private int partnerWidth;
    private int sideWidth;
    private int currencyWidth;
    private int chooseWidth;
    private int proposeWidth;

    TradeTab(ThroneScreen screen) {
        this.screen = screen;
        this.picker = new ItemPicker(screen);
    }

    TradeViews.TradeView view() {
        return view;
    }

    void setView(TradeViews.TradeView value) {
        view = value;
        partner = Math.max(0, Math.min(partner, value.partners().size() - 1));
    }

    void setMode(Mode value) {
        mode = value;
        picker.close();
        scroll = 0;
    }

    void showForCheck(Mode value, ItemStack item, String count, String price, String period, String search) {
        setMode(value);
        chosen = item;
        countText = count;
        priceText = price;
        periodText = period;
        if (search != null) {
            picker.showForCheck(search, null);
        }
    }

    String checkName() {
        return "trade-" + mode.id() + (picker.isOpen() ? "-search" : "");
    }

    // ---------------------------------------------------------------- texts

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private Optional<TradeViews.Partner> chosenPartner() {
        return view == null || view.partners().isEmpty() ? Optional.empty() : Optional.of(view.partners().get(partner));
    }

    private CurrencyUnits units(boolean ours, TradeViews.Partner other) {
        if (ours || other == null) {
            return view.own().units();
        }
        return other.currency().units();
    }

    private Optional<TradeViews.Partner> partnerById(java.util.UUID id) {
        return view.partners().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private Component dealText(TradeViews.DealRow deal) {
        TradeViews.Partner other = partnerById(deal.partner()).orElse(null);
        Component money = MoneyTexts.amount(deal.price(), units(deal.ourCurrency(), other));
        Component period = deal.periodDays() == 1 ? Component.translatable(KEY + "every_day")
                : deal.periodDays() > 1 ? Component.translatable(KEY + "every", TradeTexts.days(deal.periodDays()))
                : Component.translatable(KEY + "once");
        Component state = switch (deal.status()) {
            case PROPOSED -> deal.incoming() ? Component.translatable(KEY + "status.incoming", TradeTexts.ticks(deal.ticksToNext()))
                    : Component.translatable(KEY + "status.waiting", TradeTexts.ticks(deal.ticksToNext()));
            case ACTIVE -> Component.translatable(KEY + "status.active", TradeTexts.ticks(deal.ticksToNext()));
            default -> Component.translatable(KEY + "status." + deal.status().id());
        };
        return Component.translatable(deal.weSell() ? KEY + "row.sell" : KEY + "row.buy", TradeTexts.items(deal.count()), money, period, state);
    }

    private Component caravanText(TradeViews.CaravanRow caravan) {
        Component state = caravan.state() == Caravan.State.WAITING ? Component.translatable(KEY + "caravan.waiting")
                : Component.translatable(KEY + "caravan.left", caravan.progressPercent(), TradeTexts.ticks(caravan.ticksLeft()));
        return Component.translatable(caravan.outgoing() ? KEY + "caravan.out" : KEY + "caravan.in", TradeTexts.items(caravan.items()), state);
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        acceptWidth = screen.buttonWidth(Component.translatable(KEY + "accept"));
        declineWidth = screen.buttonWidth(Component.translatable(KEY + "decline"));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        chooseWidth = screen.buttonWidth(Component.translatable(KEY + "choose"));
        proposeWidth = screen.buttonWidth(Component.translatable(KEY + "propose"));
        sideWidth = screen.buttonWidth(Component.translatable(KEY + "side.sell"), Component.translatable(KEY + "side.buy"));
        currencyWidth = screen.buttonWidth(Component.translatable(KEY + "currency.ours"), Component.translatable(KEY + "currency.theirs"));
        partnerWidth = 120;
        labelWidth = 0;
        for (String key : List.of("partner_label", "count_label", "period_label")) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(KEY + key)) + 6);
        }
        secondLabelWidth = font.width(Component.translatable(KEY + "price_label")) + 6;
        int form = Math.max(labelWidth + partnerWidth + GAP + sideWidth, labelWidth + BOX + 8 + secondLabelWidth + BOX + GAP + currencyWidth);
        form = Math.max(form, labelWidth + BOX + GAP + proposeWidth);
        int rows = 120 + acceptWidth + GAP + declineWidth;
        int messages = 0;
        for (String key : List.of("loading", "no_deals", "no_caravans", "no_partners", "read_only", "hint")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth, form), Math.max(Math.max(rows, messages), picker.measure(font)));
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
            case DEALS -> view.deals().size();
            case CARAVANS -> view.caravans().size();
            case PROPOSE -> 0;
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
        Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        screen.add(Button.builder(modeLabel(mode), b -> {
            setMode(next);
            screen.rebuild();
        }).bounds(x, buttonsY(top), modeWidth, ROW - 2).build());
        if (picker.isOpen()) {
            picker.init(x, right, listTop(top), bottom(top, panelHeight));
            return;
        }
        if (mode == Mode.PROPOSE) {
            // Without another kingdom there is nobody to propose to: the tab only says so.
            if (!view.partners().isEmpty()) {
                initForm(x, top);
            }
            return;
        }
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (mode != Mode.DEALS || !view.canManage()) {
            return;
        }
        for (int i = 0; i < rows && scroll + i < view.deals().size(); i++) {
            TradeViews.DealRow deal = view.deals().get(scroll + i);
            int y = listTop(top) + i * ROW;
            if (deal.incoming()) {
                screen.add(Button.builder(Component.translatable(KEY + "decline"), b -> send(TradePackets.TradeChange.Action.DECLINE, deal.id()))
                        .bounds(right - declineWidth, y, declineWidth, ROW - 2).build());
                screen.add(Button.builder(Component.translatable(KEY + "accept"), b -> send(TradePackets.TradeChange.Action.ACCEPT, deal.id()))
                        .bounds(right - declineWidth - GAP - acceptWidth, y, acceptWidth, ROW - 2).build());
            } else if (deal.canCancel()) {
                screen.add(Button.builder(Component.translatable(KEY + "cancel"), b -> send(TradePackets.TradeChange.Action.CANCEL, deal.id()))
                        .bounds(right - cancelWidth, y, cancelWidth, ROW - 2).build());
            }
        }
    }

    private EditBox box(int x, int y, String value, java.util.function.Consumer<String> responder, Component name) {
        EditBox box = new EditBox(screen.font(), x + 1, y + 1, BOX - 2, ROW - 4, name);
        box.setMaxLength(Money.MAX_TEXT);
        box.setValue(value);
        box.setResponder(responder);
        box.setEditable(view.canManage());
        screen.add(box);
        return box;
    }

    /**
     * The form in four rows: the kingdom and the side, the item, the number, the price and its currency, the period and
     * the button.
     */
    private void initForm(int x, int top) {
        int fieldX = x + labelWidth;
        int y = listTop(top);
        Optional<TradeViews.Partner> other = chosenPartner();
        Component partnerName = Component.literal(other.map(p -> screen.fitName(p.name(), partnerWidth - 10)).orElse("-"));
        Button partnerButton = screen.add(Button.builder(partnerName, b -> {
            partner = (partner + 1) % Math.max(1, view.partners().size());
            screen.rebuild();
        }).bounds(fieldX, y, partnerWidth, ROW - 2).build());
        partnerButton.active = view.canManage() && view.partners().size() > 1;
        other.ifPresent(p -> partnerButton.setTooltip(Tooltip.create(Component.literal(p.name()))));
        Button side = screen.add(Button.builder(Component.translatable(weSell ? KEY + "side.sell" : KEY + "side.buy"), b -> {
            weSell = !weSell;
            screen.rebuild();
        }).bounds(fieldX + partnerWidth + GAP, y, sideWidth, ROW - 2).build());
        side.active = view.canManage();
        y += ROW + 1;
        Button choose = screen.add(Button.builder(Component.translatable(KEY + "choose"), b -> {
            picker.open(null, stack -> chosen = stack);
            screen.rebuild();
        }).bounds(x, y, chooseWidth, ROW - 2).build());
        choose.active = view.canManage();
        y += ROW + 1;
        int secondX = fieldX + BOX + 8;
        box(fieldX, y, countText, value -> countText = value, Component.translatable(KEY + "count_label"));
        box(secondX + secondLabelWidth, y, priceText, value -> priceText = value, Component.translatable(KEY + "price_label"));
        Button currency = screen.add(Button.builder(Component.translatable(ourCurrency ? KEY + "currency.ours" : KEY + "currency.theirs"), b -> {
            ourCurrency = !ourCurrency;
            screen.rebuild();
        }).bounds(secondX + secondLabelWidth + BOX + GAP, y, currencyWidth, ROW - 2).build());
        currency.active = view.canManage();
        y += ROW + 1;
        box(fieldX, y, periodText, value -> periodText = value, Component.translatable(KEY + "period_label"))
                .setTooltip(Tooltip.create(Component.translatable(KEY + "period_hint", view.maxPeriod())));
        Button propose = screen.add(Button.builder(Component.translatable(KEY + "propose"), b -> propose())
                .bounds(fieldX + BOX + GAP, y, proposeWidth, ROW - 2).build());
        propose.active = view.canManage() && other.isPresent() && !chosen.isEmpty();
    }

    private void propose() {
        Optional<TradeViews.Partner> other = chosenPartner();
        if (other.isEmpty() || chosen.isEmpty()) {
            return;
        }
        int count;
        int period;
        try {
            count = Integer.parseInt(countText.strip());
            period = periodText.isBlank() ? 0 : Integer.parseInt(periodText.strip());
        } catch (NumberFormatException e) {
            screen.onResult(false, Component.translatable(KEY + "bad_number"));
            return;
        }
        Optional<Long> price = Money.parse(priceText, units(ourCurrency, other.get()));
        if (price.isEmpty() || price.get() < 1 || count < 1 || period < 0) {
            screen.onResult(false, Component.translatable(KEY + "bad_number"));
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(chosen.getItem());
        if (id != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new TradePackets.TradeChange(view.kingdomId(), TradePackets.TradeChange.Action.PROPOSE,
                    other.get().id(), 0, id.toString(), count, price.get(), weSell, ourCurrency, period));
        }
    }

    private void send(TradePackets.TradeChange.Action action, long dealId) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new TradePackets.TradeChange(view.kingdomId(), action, null, dealId, "", 0, 0, false, false, 0));
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
        if (mode == Mode.PROPOSE) {
            renderForm(graphics, x, right, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            screen.name(graphics, Component.translatable(mode == Mode.DEALS ? KEY + "no_deals" : KEY + "no_caravans").getString(), x, listTop + 5,
                    0xFFB8B8B8, right - x);
            return;
        }
        int partnerColumn = Math.min(90, (right - x) / 4);
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int rowY = listTop + i * ROW;
            String partnerName;
            int color;
            ItemStack sample;
            Component text;
            int textRight = right;
            if (mode == Mode.DEALS) {
                TradeViews.DealRow deal = view.deals().get(scroll + i);
                partnerName = deal.partnerName();
                color = deal.partnerColor();
                sample = deal.sample();
                text = dealText(deal);
                if (view.canManage()) {
                    textRight = deal.incoming() ? right - acceptWidth - declineWidth - GAP - 4 : deal.canCancel() ? right - cancelWidth - 4 : right;
                }
            } else {
                TradeViews.CaravanRow caravan = view.caravans().get(scroll + i);
                partnerName = caravan.partnerName();
                color = caravan.partnerColor();
                sample = caravan.sample();
                text = caravanText(caravan);
            }
            screen.name(graphics, partnerName, x, rowY + 4, 0xFF000000 | FoundKingdomScreen.readable(color), partnerColumn - 4);
            graphics.renderItem(sample, x + partnerColumn, rowY);
            int textX = x + partnerColumn + 20;
            screen.name(graphics, text.getString(), textX, rowY + 4, 0xFFD0D0D0, textRight - textX);
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private void renderForm(GuiGraphics graphics, int x, int right, int top) {
        int y = listTop(top);
        int labelRight = x + labelWidth - 2;
        if (view.partners().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_partners").getString(), x, y + 5, 0xFFB8B8B8, right - x);
            return;
        }
        screen.label(graphics, Component.translatable(KEY + "partner_label"), x, y + 5, 0xFFE8E8E8, labelRight);
        int itemY = y + ROW + 1;
        int itemX = x + chooseWidth + 4;
        if (chosen.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_item").getString(), itemX, itemY + 5, 0xFFB8B8B8, right - itemX);
        } else if (itemX + 20 < right) {
            graphics.renderItem(chosen, itemX, itemY);
            screen.name(graphics, chosen.getHoverName().getString(), itemX + 18, itemY + 5, 0xFFFFFFFF, right - itemX - 18);
        }
        int countY = y + 2 * (ROW + 1);
        int secondX = x + labelWidth + BOX + 8;
        screen.label(graphics, Component.translatable(KEY + "count_label"), x, countY + 5, 0xFFE8E8E8, labelRight);
        screen.label(graphics, Component.translatable(KEY + "price_label"), secondX, countY + 5, 0xFFE8E8E8, secondX + secondLabelWidth - 2);
        int periodY = y + 3 * (ROW + 1);
        screen.label(graphics, Component.translatable(KEY + "period_label"), x, periodY + 5, 0xFFE8E8E8, labelRight);
        int previewX = x + labelWidth + BOX + GAP + proposeWidth + 6;
        if (previewX + 8 < right) {
            Optional<Long> price = Money.parse(priceText, units(ourCurrency, chosenPartner().orElse(null)));
            String shown = priceText.isBlank() ? "" : price.map(value -> MoneyTexts.amount(value, units(ourCurrency,
                    chosenPartner().orElse(null))).getString()).orElseGet(() -> Component.translatable(KEY + "bad_number").getString());
            screen.name(graphics, shown, previewX, periodY + 5, price.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - previewX);
        }
    }

    boolean editing() {
        return mode == Mode.PROPOSE || picker.isOpen();
    }

    /**
     * The status lines at the bottom while there is no other message: the deals and caravans in numbers, in the form how it
     * works.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (mode == Mode.PROPOSE) {
            return Component.translatable(view.canManage() ? KEY + "hint" : KEY + "read_only");
        }
        long incoming = view.deals().stream().filter(TradeViews.DealRow::incoming).count();
        long open = view.deals().stream().filter(deal -> !deal.status().closed()).count();
        return Component.translatable(KEY + "summary", open, incoming, view.caravans().size());
    }

    static boolean isOpen(Deal.Status status) {
        return !status.closed();
    }
}
