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

import java.util.Optional;

/**
 * The exchange of a bank: money of another currency for the bank's own, by the rates and less the fee, from the account
 * or from coins, onto the account or as coins. The treasury pays only what it has. The screen shows what the exchange would
 * give; the server does the counting.
 */
public class ExchangeScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 240;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int BOX = 90;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.exchange.";

    private TradeViews.ExchangeView view;
    private int source;
    private boolean fromAccount = true;
    private boolean toAccount = true;
    private String amount = "";
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int sourceWidth;
    private int fromWidth;
    private int toWidth;
    private int exchangeWidth;
    private int doneWidth;
    private int labelWidth;

    public ExchangeScreen(TradeViews.ExchangeView view) {
        super(Component.translatable(KEY + "name"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "exchange";
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(TradeViews.ExchangeView newView) {
        view = newView;
        source = Math.max(0, Math.min(source, view.sources().size() - 1));
        rebuildWidgets();
    }

    /**
     * Fills the form, for the layout check.
     */
    public void fillForCheck(String text, int sourceIndex, boolean account, boolean receiveOnAccount) {
        amount = text;
        source = Math.max(0, Math.min(sourceIndex, view.sources().size() - 1));
        fromAccount = account;
        toAccount = receiveOnAccount;
    }

    private Optional<TradeViews.Purse> chosen() {
        return view.sources().isEmpty() ? Optional.empty() : Optional.of(view.sources().get(source));
    }

    private static String shortName(TradeViews.Currency currency) {
        return currency.name().isEmpty() ? currency.kingdomName() : currency.name();
    }

    private void measure() {
        sourceWidth = buttonWidth(Component.translatable(KEY + "source", "W".repeat(16)));
        fromWidth = buttonWidth(Component.translatable(KEY + "from.account"), Component.translatable(KEY + "from.coins"));
        toWidth = buttonWidth(Component.translatable(KEY + "to.account"), Component.translatable(KEY + "to.coins"));
        exchangeWidth = buttonWidth(Component.translatable(KEY + "exchange"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        labelWidth = font.width(Component.translatable(KEY + "amount_label")) + 6;
        int inner = Math.max(sourceWidth, fromWidth + GAP + toWidth);
        inner = Math.max(inner, labelWidth + BOX + GAP + exchangeWidth);
        inner = Math.max(inner, Math.min(320, font.width(Component.translatable(KEY + "nothing"))));
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int formY() {
        return top() + 20 + 2 * (LINE + 1) + 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int y = formY();
        Optional<TradeViews.Purse> purse = chosen();
        if (purse.isPresent()) {
            Component label = Component.translatable(KEY + "source", fit(shortName(purse.get().currency()), sourceWidth - 20
                    - font.width(Component.translatable(KEY + "source", ""))));
            Button sourceButton = addRenderableWidget(Button.builder(label, b -> {
                source = (source + 1) % view.sources().size();
                rebuildWidgets();
            }).bounds(x, y, sourceWidth, ROW - 2).build());
            sourceButton.setTooltip(Tooltip.create(MoneyTexts.currency(purse.get().currency().name(), purse.get().currency().kingdomName())));
            y += 2 * ROW;
            addRenderableWidget(Button.builder(Component.translatable(fromAccount ? KEY + "from.account" : KEY + "from.coins"), b -> {
                fromAccount = !fromAccount;
                rebuildWidgets();
            }).bounds(x, y, fromWidth, ROW - 2).build());
            addRenderableWidget(Button.builder(Component.translatable(toAccount ? KEY + "to.account" : KEY + "to.coins"), b -> {
                toAccount = !toAccount;
                rebuildWidgets();
            }).bounds(x + fromWidth + GAP, y, toWidth, ROW - 2).build());
            y += ROW;
            EditBox box = new EditBox(font, x + labelWidth + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "amount_label"));
            box.setMaxLength(Money.MAX_TEXT);
            box.setValue(amount);
            box.setResponder(value -> amount = value);
            box.setTooltip(Tooltip.create(Component.translatable("kingdomworld.screen.bank.amount_tooltip")));
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.translatable(KEY + "exchange"), b -> {
                Optional<Long> parsed = Money.parse(amount, purse.get().currency().units());
                parsed.filter(value -> value > 0).ifPresent(value -> {
                    if (Minecraft.getInstance().getConnection() != null) {
                        KingdomWorldNetwork.sendToServer(new TradePackets.ExchangeAction(view.pos(), purse.get().currency().id(), value, fromAccount,
                                toAccount));
                    }
                });
            }).bounds(x + labelWidth + BOX + GAP, y, exchangeWidth, ROW - 2).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        int x = left + PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.color());
        Component own = MoneyTexts.currency(view.own().name(), view.own().kingdomName());
        centeredName(graphics, Component.translatable(KEY + "title", own).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.color()), panelWidth - 2 * PAD);
        name(graphics, Component.translatable(KEY + "rates", TradeTexts.rate(view.own().rate()), TradeTexts.percent(view.feePermille())).getString(),
                x, top + 20, 0xFFF0D070, right - x);
        name(graphics, Component.translatable(KEY + "funds", MoneyTexts.amount(view.funds(), view.own().units())).getString(), x,
                top + 20 + LINE + 1, 0xFFD0D0D0, right - x);
        Optional<TradeViews.Purse> purse = chosen();
        if (purse.isEmpty()) {
            paragraph(graphics, Component.translatable(KEY + "nothing"), x, formY() + 4, right - x, 3, 0xFFB8B8B8);
        } else {
            TradeViews.Purse wallet = purse.get();
            name(graphics, Component.translatable(KEY + "have", MoneyTexts.amount(wallet.balance(), wallet.currency().units()),
                    MoneyTexts.coins(wallet.carried())).getString(), x, formY() + ROW + 1, 0xFFD0D0D0, right - x);
            name(graphics, Component.translatable(KEY + "rate_of", TradeTexts.rate(wallet.currency().rate())).getString(), x,
                    formY() + ROW + LINE + 2, 0xFFB8B8B8, right - x);
            int amountY = formY() + 3 * ROW;
            label(graphics, Component.translatable(KEY + "amount_label"), x, amountY + 5, 0xFFE8E8E8, x + labelWidth - 2);
            if (!amount.isBlank()) {
                Optional<Long> parsed = Money.parse(amount, wallet.currency().units());
                String shown = parsed.map(value -> Component.translatable(KEY + "preview", MoneyTexts.amount(
                        ExchangeRates.convert(value, wallet.currency().rate(), view.own().rate(), view.feePermille()), view.own().units())).getString())
                        .orElseGet(() -> Component.translatable("kingdomworld.screen.bank.bad_amount").getString());
                name(graphics, shown, x, amountY + ROW + 2, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - x);
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
