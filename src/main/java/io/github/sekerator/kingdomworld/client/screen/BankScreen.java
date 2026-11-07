package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.BankView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bank screen: what the player has on the account in the currency of the bank and carries in its coins, buttons
 * to hand in coins (all of them or one kind), a form to take money as coins (the amount and the largest coin), and
 * every account of the player in every currency. The server does the counting and answers with the result and a fresh
 * view; nothing here changes money.
 */
public class BankScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int ROW = 18;
    private static final int GAP = 2;
    private static final int BOX_WIDTH = 90;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.bank.";
    /** Coins to pay out in: any (the fewest coins), then each kind from the largest. */
    private static final List<Optional<Denomination>> COIN_CHOICES = List.of(Optional.empty(), Optional.of(Denomination.GOLD),
            Optional.of(Denomination.SILVER), Optional.of(Denomination.COPPER));

    private BankView view;
    private String amount = "";
    private int coinChoice;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int depositLabelWidth;
    private int withdrawLabelWidth;
    private int coinWidth;
    private int takeWidth;
    private int doneWidth;

    public BankScreen(BankView view) {
        super(Component.translatable("block.kingdomworld.bank"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "bank";
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(BankView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    /**
     * Fills the form, for the layout check.
     */
    public void fillForCheck(String text, int choice) {
        amount = text;
        coinChoice = Math.floorMod(choice, COIN_CHOICES.size());
    }

    // ---------------------------------------------------------------- texts

    private CurrencyUnits units() {
        return view.units();
    }

    private Component title() {
        return Component.translatable(KEY + "title", MoneyTexts.currency(view.currencyName(), view.kingdomName()));
    }

    private static Component depositButton(Optional<Denomination> coin) {
        return coin.map(value -> Component.translatable(KEY + "deposit." + value.id())).orElseGet(() -> Component.translatable(KEY + "deposit.all"));
    }

    private static Component coinLabel(Optional<Denomination> coin) {
        return Component.translatable(KEY + "coins", coin.map(value -> Component.translatable(KEY + "coins." + value.id()))
                .orElseGet(() -> Component.translatable(KEY + "coins.any")));
    }

    private List<BankView.Account> otherAccounts() {
        List<BankView.Account> accounts = new ArrayList<>();
        for (BankView.Account account : view.accounts()) {
            if (!account.currency().equals(view.currencyId())) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        depositLabelWidth = font.width(Component.translatable(KEY + "deposit")) + 6;
        withdrawLabelWidth = font.width(Component.translatable(KEY + "withdraw")) + 6;
        int labels = Math.max(depositLabelWidth, withdrawLabelWidth);
        depositLabelWidth = labels;
        withdrawLabelWidth = labels;
        int deposits = 0;
        for (Optional<Denomination> coin : COIN_CHOICES) {
            deposits += buttonWidth(depositButton(coin)) + GAP;
        }
        coinWidth = 0;
        for (Optional<Denomination> coin : COIN_CHOICES) {
            coinWidth = Math.max(coinWidth, buttonWidth(coinLabel(coin)));
        }
        takeWidth = buttonWidth(Component.translatable(KEY + "take"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        int withdraw = labels + BOX_WIDTH + GAP + coinWidth + GAP + takeWidth;
        int inner = Math.max(labels + deposits, withdraw);
        inner = Math.max(inner, Math.min(360, font.width(title())));
        // The coins on the player may be many: the line gets the room it needs, as far as the screen allows.
        inner = Math.max(inner, Math.min(width - 8 - 2 * PAD, font.width(Component.translatable(KEY + "carried", MoneyTexts.coins(view.carried())))));
        for (String key : List.of("accounts", "no_accounts", "old")) {
            inner = Math.max(inner, font.width(Component.translatable(KEY + key)));
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

    private int depositY() {
        return top() + 22 + 3 * (LINE + 1) + 4;
    }

    private int withdrawY() {
        return depositY() + ROW + 4;
    }

    private int accountsTop() {
        return withdrawY() + ROW + LINE + 4;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int visibleAccounts() {
        return Math.max(0, (top() + panelHeight - bottomArea() - accountsTop() - LINE - 2) / (LINE + 1));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD + depositLabelWidth;
        int y = depositY();
        for (Optional<Denomination> coin : COIN_CHOICES) {
            Component label = depositButton(coin);
            int buttonWidth = buttonWidth(label);
            long have = coin.map(value -> view.carried().count(value)).orElse(view.carried().total());
            Button button = addRenderableWidget(Button.builder(label, b -> send(EconomyPackets.BankChange.Action.DEPOSIT, coin.orElse(null), 0))
                    .bounds(x, y, buttonWidth, ROW - 2).build());
            button.active = have > 0;
            x += buttonWidth + GAP;
        }

        int boxX = left + PAD + withdrawLabelWidth;
        y = withdrawY();
        EditBox box = new EditBox(font, boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "amount"));
        box.setMaxLength(Money.MAX_TEXT);
        box.setValue(amount);
        box.setHint(Component.translatable(KEY + "amount_hint"));
        box.setResponder(value -> amount = value);
        box.setTooltip(Tooltip.create(Component.translatable(KEY + "amount_tooltip")));
        addRenderableWidget(box);
        int coinX = boxX + BOX_WIDTH + GAP;
        addRenderableWidget(Button.builder(coinLabel(COIN_CHOICES.get(coinChoice)), b -> {
            coinChoice = (coinChoice + 1) % COIN_CHOICES.size();
            rebuildWidgets();
        }).bounds(coinX, y, coinWidth, ROW - 2).build());
        Button take = addRenderableWidget(Button.builder(Component.translatable(KEY + "take"), b -> {
            Optional<Long> parsed = Money.parse(amount, units());
            parsed.filter(value -> value > 0).ifPresent(value ->
                    send(EconomyPackets.BankChange.Action.WITHDRAW, COIN_CHOICES.get(coinChoice).orElse(null), value));
        }).bounds(coinX + coinWidth + GAP, y, takeWidth, ROW - 2).build());
        take.active = view.balance() > 0;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private void send(EconomyPackets.BankChange.Action action, Denomination coin, long value) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EconomyPackets.BankChange(view.pos(), action, coin, value));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, otherAccounts().size() - visibleAccounts());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
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
        centeredName(graphics, title().getString(), width / 2, top + 7, 0xFF000000 | FoundKingdomScreen.readable(view.color()),
                panelWidth - 2 * PAD);
        int y = top + 22;
        name(graphics, Component.translatable(KEY + "balance", MoneyTexts.amount(view.balance(), units())).getString(), x, y, 0xFFF0D070,
                right - x);
        y += LINE + 1;
        name(graphics, Component.translatable(KEY + "carried", MoneyTexts.coins(view.carried())).getString(), x, y, 0xFFD0D0D0, right - x);
        y += LINE + 1;
        if (view.debt() > 0) {
            name(graphics, Component.translatable(KEY + "debt", MoneyTexts.amount(view.debt(), units())).getString(), x, y, 0xFFFFB060,
                    right - x);
        }
        label(graphics, Component.translatable(KEY + "deposit"), x, depositY() + 4, 0xFFE8E8E8, x + depositLabelWidth - 2);
        label(graphics, Component.translatable(KEY + "withdraw"), x, withdrawY() + 4, 0xFFE8E8E8, x + withdrawLabelWidth - 2);
        Optional<Long> parsed = Money.parse(amount, units());
        if (!amount.isBlank()) {
            String preview = parsed.map(value -> MoneyTexts.amount(value, units()).getString())
                    .orElseGet(() -> Component.translatable(KEY + "bad_amount").getString());
            name(graphics, preview, x + withdrawLabelWidth, withdrawY() + ROW, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070,
                    right - x - withdrawLabelWidth);
        }

        int accountsTop = accountsTop();
        label(graphics, Component.translatable(KEY + "accounts"), x, accountsTop, 0xFFF0D070, right);
        List<BankView.Account> others = otherAccounts();
        int rows = visibleAccounts();
        scroll = Math.max(0, Math.min(scroll, others.size() - rows));
        if (others.isEmpty()) {
            if (rows > 0) {
                label(graphics, Component.translatable(KEY + "no_accounts"), x, accountsTop + LINE + 2, 0xFFB8B8B8, right);
            }
        } else {
            // The name of an account is the short one (the king's name for the currency or the kingdom's); the full name of the
            // currency is in the tooltip.
            int nameColumn = Math.min(150, (right - x) / 2);
            for (int i = 0; i < rows && scroll + i < others.size(); i++) {
                BankView.Account account = others.get(scroll + i);
                int rowY = accountsTop + (i + 1) * (LINE + 1) + 1;
                String shortName = account.name().isEmpty() ? account.kingdomName() : account.name();
                String label = account.old() ? Component.translatable(KEY + "account_old", shortName).getString() : shortName;
                int color = account.old() ? 0xFF9A9A9A : 0xFF000000 | FoundKingdomScreen.readable(account.color());
                int drawn = name(graphics, label, x, rowY, color, nameColumn - 6);
                hoverText(x, rowY, drawn, Component.translatable(KEY + "account_currency", MoneyTexts.currency(account.name(), account.kingdomName())));
                name(graphics, MoneyTexts.amount(account.balance(), account.units()).getString(), x + nameColumn, rowY, color, right - x - nameColumn);
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
