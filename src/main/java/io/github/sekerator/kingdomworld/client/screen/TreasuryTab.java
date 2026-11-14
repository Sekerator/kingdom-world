package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TreasuryView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The "Treasury" tab of the throne screen: what the treasury holds (its balance and the coins in its vault) and its
 * views: an overview (currency, tax, the last payday, the mint, debts), the journal of income and expenses by category
 * for today, yesterday and seven days, the settings of taxes, wages and the name of the currency (for members with
 * the right to manage the treasury; the name only for the king), the metals of the mint (which of them it strikes and
 * how many of each stay in the storehouses), payments, reserves and the subjects in debt. Every change is an intent
 * the server checks; it answers with a fresh view.
 */
final class TreasuryTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int BOX_WIDTH = 86;
    private static final String KEY = "kingdomworld.screen.treasury.";

    /**
     * What the tab shows.
     */
    enum Mode {
        OVERVIEW,
        JOURNAL,
        SETTINGS,
        MINT,
        PAYMENTS,
        RESERVES,
        DEBTORS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private TreasuryView view;
    private Mode mode = Mode.OVERVIEW;
    private TreasuryJournal.Period period = TreasuryJournal.Period.TODAY;
    private int scroll;
    private String taxText;
    private String nameText;
    private String payeeText = "";
    private String payText = "";
    private String coinsText = "";
    private Denomination coinChoice = Denomination.GOLD;
    /** Whether the note under the payments did not fit and the bottom line shows it instead. */
    private boolean noteInHint;

    private int modeWidth;
    private int periodWidth;
    private int setWidth;
    private int renameWidth;
    private int stepWidth;
    private int pageWidth;
    private int categoryWidth;
    private int professionWidth;
    private int settingLabelWidth;
    private int paymentLabelWidth;
    private int payWidth;
    private int coinChoiceWidth;
    private int takeWidth;
    private int mintNameWidth;
    private int mintStockWidth;
    private int mintToggleWidth;
    private int mintValueWidth;
    /** Step of the ingots to keep; with Shift the big one. */
    private static final int KEEP_STEP = 16;
    private static final int KEEP_BIG_STEP = 64;
    /** Height of a row of a metal: one pixel under its buttons, so that three metals fit on the smallest screen. */
    private static final int MINT_ROW = ROW - 1;

    TreasuryTab(ThroneScreen screen) {
        this.screen = screen;
    }

    TreasuryView view() {
        return view;
    }

    void setView(TreasuryView view) {
        boolean first = this.view == null || !this.view.kingdomId().equals(view.kingdomId());
        this.view = view;
        if (first || taxText == null) {
            taxText = String.valueOf(view.pollTax());
        }
        if (first || nameText == null) {
            nameText = view.currencyName();
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
    }

    void setPeriod(TreasuryJournal.Period value) {
        period = value;
    }

    String checkName() {
        return "treasury-" + mode.id() + (mode == Mode.JOURNAL ? "-" + period.id() : "");
    }

    // ---------------------------------------------------------------- texts

    private CurrencyUnits units() {
        return view == null ? CurrencyUnits.DEFAULT : view.units();
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, units());
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private static Component periodLabel(TreasuryJournal.Period value) {
        return Component.translatable(KEY + "period." + value.id());
    }

    private static Component category(LedgerCategory category) {
        return Component.translatable("kingdomworld.ledger." + category.id());
    }

    private Component currencyName() {
        return MoneyTexts.currency(view.currencyName(), view.kingdomName());
    }

    private List<Component> summaryLines() {
        List<Component> lines = new ArrayList<>();
        if (view == null) {
            return lines;
        }
        lines.add(Component.translatable(KEY + "funds", money(view.funds())));
        lines.add(Component.translatable(KEY + "parts", money(view.balance()), MoneyTexts.coins(view.vault())));
        return lines;
    }

    private List<Component> overviewLines() {
        List<Component> lines = new ArrayList<>();
        CurrencyUnits units = units();
        lines.add(Component.translatable(KEY + "currency", currencyName()));
        lines.add(Component.translatable(KEY + "rate", io.github.sekerator.kingdomworld.common.trade.TradeTexts.rate(view.rate())));
        lines.add(Component.translatable(KEY + "coin_values", MoneyTexts.coins(Denomination.GOLD, 1),
                MoneyTexts.coins(Denomination.COPPER, units.gold()), MoneyTexts.coins(Denomination.SILVER, 1),
                MoneyTexts.coins(Denomination.COPPER, units.silver())));
        lines.add(Component.translatable(KEY + "tax", money(view.pollTax()), view.taxpayers()));
        TreasuryView.Payroll payroll = view.lastPayroll();
        lines.add(payroll.day() < 0 ? Component.translatable(KEY + "payroll_none")
                : Component.translatable(KEY + "payroll", money(payroll.paid()), money(payroll.due()), payroll.percent(), payroll.citizens()));
        if (payroll.day() >= 0 && payroll.underpaid() > 0) {
            lines.add(Component.translatable(KEY + "underpaid", payroll.underpaid()));
        }
        lines.add(Component.translatable(KEY + "next_payday", Math.max(1, (view.untilPayday() + 1_199) / 1_200)));
        TreasuryView.Mint mint = view.mint();
        if (mint.posts() == 0) {
            lines.add(Component.translatable(KEY + "mint_none"));
        } else {
            lines.add(Component.translatable(KEY + "mint", mint.posts(), mint.workers(),
                    Component.translatable("kingdomworld.work.status." + mint.status().id())));
            lines.add(Component.translatable(mint.toVault() ? KEY + "minted_vault" : KEY + "minted_balance", money(mint.today()),
                    money(mint.week())));
        }
        lines.add(view.debtorCount() == 0 ? Component.translatable(KEY + "debts_none")
                : Component.translatable(KEY + "debts", view.debtorCount(), money(view.debtTotal())));
        return lines;
    }

    private static int lineColor(Component line) {
        return 0xFFE8E8E8;
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        periodWidth = 0;
        for (TreasuryJournal.Period value : TreasuryJournal.Period.values()) {
            periodWidth = Math.max(periodWidth, screen.buttonWidth(periodLabel(value)));
        }
        setWidth = screen.buttonWidth(Component.translatable(KEY + "set"));
        renameWidth = screen.buttonWidth(Component.translatable(KEY + "rename"));
        stepWidth = Math.max(screen.buttonWidth(Component.literal("-")), screen.buttonWidth(Component.literal("+")));
        pageWidth = font.width(Component.translatable("kingdomworld.screen.throne.page", 500, 500, 500));
        categoryWidth = 0;
        for (LedgerCategory category : LedgerCategory.values()) {
            categoryWidth = Math.max(categoryWidth, font.width(category(category)) + 8);
        }
        categoryWidth = Math.max(categoryWidth, font.width(Component.translatable(KEY + "total")) + 8);
        professionWidth = 0;
        for (TreasuryView.Wage wage : view == null ? List.<TreasuryView.Wage>of() : view.wages()) {
            professionWidth = Math.max(professionWidth, font.width(CitizenTexts.profession(wage.profession())) + 8);
        }
        for (io.github.sekerator.kingdomworld.core.citizen.Profession profession
                : io.github.sekerator.kingdomworld.core.citizen.Professions.REGISTRY.all()) {
            professionWidth = Math.max(professionWidth, font.width(CitizenTexts.profession(profession.id())) + 8);
        }
        settingLabelWidth = Math.max(font.width(Component.translatable(KEY + "tax_label")),
                font.width(Component.translatable(KEY + "name_label"))) + 6;
        paymentLabelWidth = 0;
        for (String key : List.of("payee_label", "pay_label", "coins_label")) {
            paymentLabelWidth = Math.max(paymentLabelWidth, font.width(Component.translatable(KEY + key)) + 6);
        }
        payWidth = screen.buttonWidth(Component.translatable(KEY + "pay"));
        coinChoiceWidth = 0;
        for (Denomination coin : Denomination.values()) {
            coinChoiceWidth = Math.max(coinChoiceWidth, screen.buttonWidth(coinChoiceLabel(coin)));
        }
        takeWidth = screen.buttonWidth(Component.translatable(KEY + "take_coins"));
        int buttons = modeWidth + GAP + periodWidth + 8 + pageWidth;
        int settings = settingLabelWidth + BOX_WIDTH + GAP + Math.max(setWidth, renameWidth);
        int payments = paymentLabelWidth + BOX_WIDTH + GAP + Math.max(payWidth, coinChoiceWidth + GAP + takeWidth);
        settings = Math.max(settings, payments);
        int wages = professionWidth + 90 + 2 * (stepWidth + GAP);
        int mint = measureMint(font);
        int messages = 0;
        for (String key : List.of("loading", "journal_empty", "debtors_none", "read_only", "payroll_none", "mint_none", "debts_none",
                "payments_read_only", "coins_read_only", "reserves_none", "mint_metals_none")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(Math.max(buttons, settings), Math.max(wages, messages)), mint);
    }

    /**
     * Widths of the columns of the metals of the mint: the name of a metal is shortened and shown in full on hover, the others
     * fit their widest text.
     */
    private int measureMint(Font font) {
        mintToggleWidth = Math.max(Math.max(screen.buttonWidth(strikeLabel(true)), screen.buttonWidth(strikeLabel(false))),
                font.width(Component.translatable(KEY + "mint_header_strike")) + 4);
        mintValueWidth = Math.max(font.width(Integer.toString(io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP)), 24) + 4;
        mintStockWidth = Math.max(font.width(Component.translatable(KEY + "mint_header_stock")), font.width("9999999")) + 8;
        int names = font.width(Component.translatable(KEY + "mint_header_metal"));
        for (TreasuryView.MintMetal metal : view == null ? List.<TreasuryView.MintMetal>of() : view.mint().metals()) {
            names = Math.max(names, font.width(metal.item().getHoverName()));
        }
        mintNameWidth = Math.min(110, names + 6);
        int keepHeader = font.width(Component.translatable(KEY + "mint_header_keep"));
        return 20 + mintNameWidth + mintStockWidth + mintToggleWidth + GAP + 4 + Math.max(keepStepperWidth(), keepHeader);
    }

    private int keepStepperWidth() {
        return 2 * stepWidth + 2 * GAP + mintValueWidth;
    }

    private static Component strikeLabel(boolean strike) {
        return Component.translatable(strike ? KEY + "mint_strike_on" : KEY + "mint_strike_off");
    }

    private static int buttonsY(int top) {
        return top + ThroneScreen.CONTENT_TOP + 2 * LINE + 2;
    }

    private static int listTop(int top) {
        return buttonsY(top) + ROW + 4;
    }

    private static int visibleRows(int listTop, int top, int panelHeight) {
        return Math.max(1, (top + panelHeight - ThroneScreen.BOTTOM_AREA - listTop) / ROW);
    }

    /**
     * Where the list of wages starts in the settings: under the tax and the name of the currency.
     */
    private static int wagesTop(int top) {
        return listTop(top) + 2 * ROW + 2;
    }

    private List<TreasuryView.JournalRow> journalRows() {
        List<TreasuryView.JournalRow> rows = new ArrayList<>();
        for (TreasuryView.JournalRow row : view.journal()) {
            if (row.period() == period && (row.income() > 0 || row.expense() > 0)) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Lines of the journal that fit above the bottom line, leaving room for the total.
     */
    private static int journalLines(int top, int panelHeight) {
        int room = top + panelHeight - ThroneScreen.BOTTOM_AREA - (listTop(top) + 4) - (LINE + 4);
        return Math.max(1, room / (LINE + 1));
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case JOURNAL -> journalRows().size();
            case SETTINGS -> view.wages().size();
            case MINT -> view.mint().metals().size();
            case DEBTORS -> view.debtors().size();
            case RESERVES -> view.reserves().size();
            default -> 0;
        };
    }

    private int rowsTop(int top) {
        return mode == Mode.SETTINGS ? wagesTop(top) : mode == Mode.MINT ? mintTop(top) : listTop(top);
    }

    /**
     * Where the rows of the metals start: under the line of the column titles.
     */
    private static int mintTop(int top) {
        return listTop(top) + LINE;
    }

    /**
     * Where the stepper of the ingots to keep starts: at the right edge.
     */
    private int keepX(int right) {
        return right - keepStepperWidth();
    }

    private int toggleX(int right) {
        return keepX(right) - 4 - GAP - mintToggleWidth;
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
        if (mode == Mode.JOURNAL) {
            screen.add(new CycleButton(x + modeWidth + GAP, buttonsY(top), periodWidth, ROW - 2, periodLabel(period), direction -> {
                period = Cycle.step(period, direction);
                screen.rebuild();
            }));
        }
        int rows = visible(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (mode == Mode.SETTINGS) {
            initSettings(x, right, top, rows);
        }
        if (mode == Mode.PAYMENTS) {
            initPayments(x, top);
        }
        if (mode == Mode.MINT) {
            initMint(right, top, rows);
        }
    }

    /**
     * For every metal of the mint: a button that turns striking from it on and off, and a stepper of the ingots that stay
     * in the storehouses. Only for members with the right to manage the treasury.
     */
    private void initMint(int right, int top, int rows) {
        if (!view.canManage()) {
            return;
        }
        List<TreasuryView.MintMetal> metals = view.mint().metals();
        int keepX = keepX(right);
        for (int i = 0; i < rows && scroll + i < metals.size(); i++) {
            TreasuryView.MintMetal metal = metals.get(scroll + i);
            int rowY = mintTop(top) + i * MINT_ROW;
            Button toggle = screen.add(Button.builder(strikeLabel(metal.strike()),
                            b -> send(EconomyPackets.TreasuryChange.Action.MINT_STRIKE, metal.metal(), metal.strike() ? 0 : 1))
                    .bounds(toggleX(right), rowY, mintToggleWidth, ROW - 2).build());
            toggle.setTooltip(Tooltip.create(Component.translatable(metal.strike() ? KEY + "mint_strike_on_hint" : KEY + "mint_strike_off_hint",
                    metal.item().getHoverName())));
            Button less = screen.add(Button.builder(Component.literal("-"),
                            b -> send(EconomyPackets.TreasuryChange.Action.MINT_KEEP, metal.metal(), Math.max(0, metal.keep() - keepStep())))
                    .bounds(keepX, rowY, stepWidth, ROW - 2).build());
            less.active = metal.keep() > 0;
            less.setTooltip(Tooltip.create(Component.translatable(KEY + "mint_keep_hint")));
            Button more = screen.add(Button.builder(Component.literal("+"),
                            b -> send(EconomyPackets.TreasuryChange.Action.MINT_KEEP, metal.metal(),
                                    Math.min(io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP, metal.keep() + keepStep())))
                    .bounds(keepX + stepWidth + GAP + mintValueWidth + GAP, rowY, stepWidth, ROW - 2).build());
            more.active = metal.keep() < io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP;
            more.setTooltip(Tooltip.create(Component.translatable(KEY + "mint_keep_hint")));
        }
    }

    private static int keepStep() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown() ? KEEP_BIG_STEP : KEEP_STEP;
    }

    private static Component coinChoiceLabel(Denomination coin) {
        return Component.translatable(KEY + "coin_choice." + coin.id());
    }

    private void initPayments(int x, int top) {
        int boxX = x + paymentLabelWidth;
        int y = listTop(top);
        EditBox payee = new EditBox(screen.font(), boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "payee_label"));
        payee.setMaxLength(16);
        payee.setValue(payeeText);
        payee.setResponder(value -> payeeText = value);
        payee.setEditable(view.canManage());
        payee.setHint(Component.translatable(KEY + "payee_hint"));
        screen.add(payee);

        y += ROW + 1;
        EditBox amount = new EditBox(screen.font(), boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "pay_label"));
        amount.setMaxLength(Money.MAX_TEXT);
        amount.setValue(payText);
        amount.setResponder(value -> payText = value);
        amount.setEditable(view.canManage());
        screen.add(amount);
        Button pay = screen.add(Button.builder(Component.translatable(KEY + "pay"), b -> Money.parse(payText, units())
                        .filter(value -> value > 0 && !payeeText.isBlank())
                        .ifPresent(value -> send(EconomyPackets.TreasuryChange.Action.PAY, payeeText.strip(), value)))
                .bounds(boxX + BOX_WIDTH + GAP, y, payWidth, ROW - 2).build());
        pay.active = view.canManage();
        pay.setTooltip(Tooltip.create(Component.translatable(KEY + "pay_hint")));

        y += ROW + 6;
        EditBox coins = new EditBox(screen.font(), boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "coins_label"));
        coins.setMaxLength(19);
        coins.setValue(coinsText);
        coins.setResponder(value -> coinsText = value);
        coins.setEditable(view.canTakeCoins());
        coins.setHint(Component.translatable(KEY + "coins_hint"));
        screen.add(coins);
        int choiceX = boxX + BOX_WIDTH + GAP;
        Button choice = screen.add(new CycleButton(choiceX, y, coinChoiceWidth, ROW - 2, coinChoiceLabel(coinChoice), direction -> {
            coinChoice = Cycle.step(coinChoice, direction);
            screen.rebuild();
        }));
        choice.active = view.canTakeCoins();
        Button take = screen.add(Button.builder(Component.translatable(KEY + "take_coins"), b -> {
            long count = parseCount(coinsText);
            if (count > 0) {
                send(EconomyPackets.TreasuryChange.Action.TAKE_COINS, coinChoice.id(), count);
            }
        }).bounds(choiceX + coinChoiceWidth + GAP, y, takeWidth, ROW - 2).build());
        take.active = view.canTakeCoins() && view.vault().count(coinChoice) > 0;
        take.setTooltip(Tooltip.create(Component.translatable(KEY + "take_coins_hint")));
    }

    private static long parseCount(String text) {
        try {
            long value = Long.parseLong(text.strip());
            return value > 0 && value <= Money.MAX ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Fills the forms of the payments, for the layout check.
     */
    void fillForCheck(String payee, String amount, String coins) {
        payeeText = payee;
        payText = amount;
        coinsText = coins;
    }

    private void initSettings(int x, int right, int top, int rows) {
        int boxX = x + settingLabelWidth;
        int y = listTop(top);
        EditBox tax = new EditBox(screen.font(), boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "tax_label"));
        tax.setMaxLength(Money.MAX_TEXT);
        tax.setValue(taxText);
        tax.setResponder(value -> taxText = value);
        tax.setEditable(view.canManage());
        screen.add(tax);
        Button set = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            Optional<Long> amount = Money.parse(taxText, units());
            amount.ifPresent(value -> send(EconomyPackets.TreasuryChange.Action.POLL_TAX, "", value));
        }).bounds(boxX + BOX_WIDTH + GAP, y, setWidth, ROW - 2).build());
        set.active = view.canManage();
        set.setTooltip(Tooltip.create(Component.translatable(KEY + "tax_hint", MoneyTexts.amount(view.maxPollTax(), units()))));

        y += ROW + 1;
        EditBox name = new EditBox(screen.font(), boxX + 1, y + 1, BOX_WIDTH - 2, ROW - 4, Component.translatable(KEY + "name_label"));
        name.setMaxLength(KingdomNames.MAX_LENGTH);
        name.setValue(nameText);
        name.setResponder(value -> nameText = value);
        name.setEditable(view.isKing());
        name.setHint(Component.translatable(KEY + "name_hint"));
        screen.add(name);
        Button rename = screen.add(Button.builder(Component.translatable(KEY + "rename"),
                b -> send(EconomyPackets.TreasuryChange.Action.RENAME, nameText.strip(), 0))
                .bounds(boxX + BOX_WIDTH + GAP, y, renameWidth, ROW - 2).build());
        rename.active = view.isKing();
        rename.setTooltip(Tooltip.create(Component.translatable(KEY + "rename_hint")));

        if (!view.canManage()) {
            return;
        }
        int minusX = right - 2 * stepWidth - GAP;
        int plusX = right - stepWidth;
        List<TreasuryView.Wage> wages = view.wages();
        for (int i = 0; i < rows && scroll + i < wages.size(); i++) {
            TreasuryView.Wage wage = wages.get(scroll + i);
            int rowY = wagesTop(top) + i * ROW;
            Button minus = screen.add(Button.builder(Component.literal("-"),
                    b -> send(EconomyPackets.TreasuryChange.Action.WAGE, wage.profession(), Math.max(0, wage.wage() - 1)))
                    .bounds(minusX, rowY, stepWidth, ROW - 2).build());
            minus.active = wage.wage() > 0;
            Button plus = screen.add(Button.builder(Component.literal("+"),
                    b -> send(EconomyPackets.TreasuryChange.Action.WAGE, wage.profession(), Math.min(view.maxWage(), wage.wage() + 1)))
                    .bounds(plusX, rowY, stepWidth, ROW - 2).build());
            plus.active = wage.wage() < view.maxWage();
        }
    }

    private void send(EconomyPackets.TreasuryChange.Action action, String text, long amount) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EconomyPackets.TreasuryChange(view.kingdomId(), action, text, amount));
        }
    }

    private int visible(int top, int panelHeight) {
        if (mode == Mode.MINT) {
            return Math.max(1, (top + panelHeight - ThroneScreen.BOTTOM_AREA - mintTop(top)) / MINT_ROW);
        }
        return mode == Mode.JOURNAL ? journalLines(top, panelHeight) : visibleRows(rowsTop(top), top, panelHeight);
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max = Math.max(0, rowCount() - visible(top, panelHeight));
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
        List<Component> summary = summaryLines();
        screen.name(graphics, summary.get(0).getString(), x, y, 0xFFF0D070, right - x);
        screen.name(graphics, summary.get(1).getString(), x, y + LINE, 0xFFD0D0D0, right - x);
        int count = rowCount();
        int rows = visible(top, panelHeight);
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), buttonsY(top) + 4, 0xFF9A9A9A, right);
        }
        int listTop = listTop(top);
        switch (mode) {
            case OVERVIEW -> renderOverview(graphics, x, right, listTop, top, panelHeight);
            case JOURNAL -> renderJournal(graphics, font, x, right, listTop, rows);
            case SETTINGS -> renderSettings(graphics, font, x, right, top, rows);
            case MINT -> renderMint(graphics, font, x, right, top, rows);
            case PAYMENTS -> renderPayments(graphics, x, right, top, panelHeight);
            case DEBTORS -> renderDebtors(graphics, font, x, right, listTop, rows);
            case RESERVES -> renderReserves(graphics, x, right, listTop, rows);
        }
    }

    private void renderOverview(GuiGraphics graphics, int x, int right, int listTop, int top, int panelHeight) {
        int bottom = top + panelHeight - ThroneScreen.BOTTOM_AREA;
        int y = listTop;
        for (Component line : overviewLines()) {
            if (y + LINE > bottom) {
                break;
            }
            screen.name(graphics, line.getString(), x, y, lineColor(line), right - x);
            y += LINE + 1;
        }
    }

    private void renderJournal(GuiGraphics graphics, Font font, int x, int right, int listTop, int lines) {
        List<TreasuryView.JournalRow> rows = journalRows();
        if (rows.isEmpty()) {
            screen.label(graphics, Component.translatable(KEY + "journal_empty"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        int amountX = x + categoryWidth;
        long income = 0;
        long expense = 0;
        int y = listTop + 4;
        for (int i = 0; i < rows.size(); i++) {
            TreasuryView.JournalRow row = rows.get(i);
            income = Money.addCapped(income, row.income());
            expense = Money.addCapped(expense, row.expense());
            // Lines that do not fit are reached by scrolling; the total always counts all of them.
            if (i < scroll || i >= scroll + lines) {
                continue;
            }
            screen.label(graphics, category(row.category()), x, y, 0xFFE8E8E8, amountX - 4);
            renderFlow(graphics, font, row.income(), row.expense(), amountX, right, y);
            y += LINE + 1;
        }
        graphics.fill(x, y, right, y + 1, 0xFF706050);
        y += 3;
        screen.label(graphics, Component.translatable(KEY + "total"), x, y, 0xFFF0D070, amountX - 4);
        renderFlow(graphics, font, income, expense, amountX, right, y);
    }

    /**
     * Money in (green) and out (red) of a line of the journal.
     */
    private void renderFlow(GuiGraphics graphics, Font font, long income, long expense, int x, int right, int y) {
        String in = income > 0 ? Component.translatable(KEY + "income", money(income)).getString() : "";
        String out = expense > 0 ? Component.translatable(KEY + "expense", money(expense)).getString() : "";
        if (!in.isEmpty() && !out.isEmpty()) {
            int half = (right - x) / 2;
            int drawn = screen.name(graphics, in, x, y, 0xFF90E090, half - 6);
            int outX = Math.max(x + half, x + drawn + 6);
            screen.name(graphics, out, outX, y, 0xFFFF9070, right - outX);
        } else if (!in.isEmpty()) {
            screen.name(graphics, in, x, y, 0xFF90E090, right - x);
        } else {
            screen.name(graphics, out, x, y, 0xFFFF9070, right - x);
        }
    }

    private void renderSettings(GuiGraphics graphics, Font font, int x, int right, int top, int rows) {
        int y = listTop(top);
        screen.label(graphics, Component.translatable(KEY + "tax_label"), x, y + 5, 0xFFE8E8E8, x + settingLabelWidth - 2);
        screen.label(graphics, Component.translatable(KEY + "name_label"), x, y + ROW + 6, 0xFFE8E8E8, x + settingLabelWidth - 2);
        int nameRight = x + settingLabelWidth + BOX_WIDTH + GAP + Math.max(setWidth, renameWidth);
        if (nameRight + 8 < right) {
            Optional<Long> parsed = Money.parse(taxText, units());
            String shown = parsed.map(value -> MoneyTexts.amount(value, units()).getString())
                    .orElseGet(() -> Component.translatable(KEY + "bad_amount").getString());
            screen.name(graphics, shown, nameRight + 8, y + 5, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - nameRight - 8);
        }
        List<TreasuryView.Wage> wages = view.wages();
        int amountX = x + professionWidth;
        int amountRight = view.canManage() ? right - 2 * stepWidth - 2 * GAP - 4 : right;
        for (int i = 0; i < rows && scroll + i < wages.size(); i++) {
            TreasuryView.Wage wage = wages.get(scroll + i);
            int rowY = wagesTop(top) + i * ROW + 4;
            screen.label(graphics, CitizenTexts.profession(wage.profession()), x, rowY, wage.citizens() > 0 ? 0xFFFFFFFF : 0xFFB0B0B0,
                    amountX - 4);
            Component text = wage.wage() == wage.usual() ? Component.translatable(KEY + "wage", money(wage.wage()), wage.citizens())
                    : Component.translatable(KEY + "wage_changed", money(wage.wage()), money(wage.usual()), wage.citizens());
            int color = wage.wage() < wage.usual() ? 0xFFFF9070 : wage.wage() > wage.usual() ? 0xFF90E090 : 0xFFD0D0D0;
            screen.name(graphics, text.getString(), amountX, rowY, color, amountRight - amountX);
        }
        if (!view.canManage() && wages.size() <= rows) {
            int hintY = wagesTop(top) + wages.size() * ROW + 4;
            screen.name(graphics, Component.translatable(KEY + "read_only").getString(), x, hintY, 0xFFB8B8B8, right - x);
        }
    }

    /**
     * The metals of the mint: the name (in full and with the coins of one item on hover), how many the storehouses hold,
     * whether the mint strikes from it and how many of it stay in the storehouses.
     */
    private void renderMint(GuiGraphics graphics, Font font, int x, int right, int top, int rows) {
        List<TreasuryView.MintMetal> metals = view.mint().metals();
        int headerY = listTop(top) + 1;
        if (metals.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(KEY + "mint_metals_none"), x, headerY + 3, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int toggleX = toggleX(right);
        int keepX = keepX(right);
        int stockRight = toggleX - 6;
        int nameX = x + 20;
        int nameRight = stockRight - mintStockWidth;
        screen.label(graphics, Component.translatable(KEY + "mint_header_metal"), nameX, headerY, 0xFF9A9A9A, nameRight);
        Component stockHeader = Component.translatable(KEY + "mint_header_stock");
        screen.label(graphics, stockHeader, stockRight - font.width(stockHeader), headerY, 0xFF9A9A9A, stockRight);
        Component strikeHeader = Component.translatable(KEY + "mint_header_strike");
        screen.centeredLabel(graphics, strikeHeader, toggleX + mintToggleWidth / 2, headerY, 0xFF9A9A9A, toggleX - 2,
                toggleX + mintToggleWidth + 2);
        Component keepHeader = Component.translatable(KEY + "mint_header_keep");
        screen.centeredLabel(graphics, keepHeader, keepX + keepStepperWidth() / 2, headerY, 0xFF9A9A9A, keepX - 4, right);
        boolean manage = view.canManage();
        for (int i = 0; i < rows && scroll + i < metals.size(); i++) {
            TreasuryView.MintMetal metal = metals.get(scroll + i);
            int rowY = mintTop(top) + i * MINT_ROW;
            graphics.renderItem(metal.item(), x, rowY);
            String name = metal.item().getHoverName().getString();
            int drawn = screen.name(graphics, name, nameX, rowY + 5, metal.strike() ? 0xFFFFFFFF : 0xFFB0B0B0, nameRight - 4 - nameX);
            screen.hoverText(x, rowY + 5, drawn + 20, Component.translatable(KEY + "mint_rate", metal.item().getHoverName(),
                    MoneyTexts.coins(metal.coin(), metal.count())));
            Component stock = Component.literal(Long.toString(metal.stock()));
            int color = !metal.strike() || metal.stock() <= metal.keep() ? 0xFFB0B0B0 : 0xFFD0D0D0;
            screen.label(graphics, stock, stockRight - font.width(stock), rowY + 5, color, stockRight);
            Component keep = Component.literal(Integer.toString(metal.keep()));
            if (manage) {
                int valueLeft = keepX + stepWidth + GAP;
                screen.centeredLabel(graphics, keep, valueLeft + mintValueWidth / 2, rowY + 5, 0xFFFFFFFF, valueLeft, valueLeft + mintValueWidth);
            } else {
                Component strike = strikeLabel(metal.strike());
                screen.centeredLabel(graphics, strike, toggleX + mintToggleWidth / 2, rowY + 5, metal.strike() ? 0xFFA0E0A0 : 0xFFFF9070,
                        toggleX, toggleX + mintToggleWidth);
                screen.centeredLabel(graphics, keep, keepX + keepStepperWidth() / 2, rowY + 5, 0xFFFFFFFF, keepX, right);
            }
        }
    }

    private void renderPayments(GuiGraphics graphics, int x, int right, int top, int panelHeight) {
        int y = listTop(top);
        int labelRight = x + paymentLabelWidth - 2;
        screen.label(graphics, Component.translatable(KEY + "payee_label"), x, y + 5, 0xFFE8E8E8, labelRight);
        screen.label(graphics, Component.translatable(KEY + "pay_label"), x, y + ROW + 6, 0xFFE8E8E8, labelRight);
        int previewX = x + paymentLabelWidth + BOX_WIDTH + GAP + payWidth + 8;
        if (!payText.isBlank() && previewX + 8 < right) {
            Optional<Long> parsed = Money.parse(payText, units());
            String shown = parsed.map(value -> MoneyTexts.amount(value, units()).getString())
                    .orElseGet(() -> Component.translatable(KEY + "bad_amount").getString());
            screen.name(graphics, shown, previewX, y + ROW + 6, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - previewX);
        }
        int coinsY = y + 2 * ROW + 7;
        screen.label(graphics, Component.translatable(KEY + "coins_label"), x, coinsY + 5, 0xFFE8E8E8, labelRight);
        // Lines under the form only where they stay clear of the messages at the bottom; the coins of the vault are also
        // in the summary at the top, and the notes for members without the right go to the bottom line instead.
        int bottom = top + panelHeight - ThroneScreen.BOTTOM_AREA;
        int vaultY = coinsY + ROW + 4;
        if (vaultY + LINE <= bottom) {
            String vault = Component.translatable(KEY + "vault_coins", MoneyTexts.coins(view.vault())).getString();
            screen.name(graphics, vault, x, vaultY, 0xFFD0D0D0, right - x);
        }
        int noteY = vaultY + LINE + 2;
        noteInHint = noteY + LINE > bottom;
        if (!noteInHint && !view.canManage()) {
            screen.name(graphics, Component.translatable(KEY + "payments_read_only").getString(), x, noteY, 0xFFB8B8B8, right - x);
        } else if (!noteInHint && !view.canTakeCoins()) {
            screen.name(graphics, Component.translatable(KEY + "coins_read_only").getString(), x, noteY, 0xFFB8B8B8, right - x);
        }
    }

    private void renderReserves(GuiGraphics graphics, int x, int right, int listTop, int rows) {
        List<TreasuryView.Reserve> reserves = view.reserves();
        if (reserves.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "reserves_none").getString(), x, listTop + 5, 0xFFB8B8B8, right - x);
            return;
        }
        int nameWidth = Math.min(130, (right - x) / 3);
        for (int i = 0; i < rows && scroll + i < reserves.size(); i++) {
            TreasuryView.Reserve reserve = reserves.get(scroll + i);
            int y = listTop + i * ROW + 4;
            String shortName = reserve.name().isEmpty() ? reserve.kingdomName() : reserve.name();
            int drawn = screen.name(graphics, shortName, x, y, 0xFF000000 | FoundKingdomScreen.readable(reserve.color()), nameWidth - 4);
            screen.hoverText(x, y, drawn, MoneyTexts.currency(reserve.name(), reserve.kingdomName()));
            String amount = Component.translatable(KEY + "reserve", MoneyTexts.amount(reserve.amount(), reserve.units()), money(reserve.worth()))
                    .getString();
            screen.name(graphics, amount, x + nameWidth, y, 0xFFD0D0D0, right - x - nameWidth);
        }
    }

    private void renderDebtors(GuiGraphics graphics, Font font, int x, int right, int listTop, int rows) {
        List<TreasuryView.Debtor> debtors = view.debtors();
        if (debtors.isEmpty()) {
            screen.label(graphics, Component.translatable(KEY + "debtors_none"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        int nameWidth = Math.min(140, (right - x) / 2);
        for (int i = 0; i < rows && scroll + i < debtors.size(); i++) {
            TreasuryView.Debtor debtor = debtors.get(scroll + i);
            int y = listTop + i * ROW + 4;
            screen.name(graphics, debtor.name(), x, y, 0xFFFFFFFF, nameWidth - 4);
            screen.name(graphics, money(debtor.debt()).getString(), x + nameWidth, y, 0xFFFF9070, right - x - nameWidth);
        }
    }

    /**
     * Whether the tab has a text field the player is typing into, so that the screen keeps keys for it.
     */
    boolean editing() {
        return mode == Mode.SETTINGS || mode == Mode.PAYMENTS;
    }

    /**
     * A status line for the bottom while there is no other message.
     */
    Component hint() {
        if (view != null && mode == Mode.PAYMENTS && noteInHint && !view.canManage()) {
            return Component.translatable(KEY + "payments_read_only");
        }
        if (view != null && mode == Mode.PAYMENTS && noteInHint && !view.canTakeCoins()) {
            return Component.translatable(KEY + "coins_read_only");
        }
        if (view != null && mode == Mode.MINT && !view.mint().metals().isEmpty()) {
            return Component.translatable(view.canManage() ? KEY + "mint_hint" : KEY + "mint_read_only");
        }
        if (view == null || !view.canManage()) {
            return Component.empty();
        }
        return switch (mode) {
            case SETTINGS -> Component.translatable(KEY + "settings_hint");
            case PAYMENTS -> Component.translatable(KEY + "payments_hint");
            default -> Component.empty();
        };
    }

    static boolean isProblem(WorkStatus status) {
        return status.isProblem();
    }
}
