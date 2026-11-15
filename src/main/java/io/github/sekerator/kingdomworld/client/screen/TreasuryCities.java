package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CityViews;
import io.github.sekerator.kingdomworld.common.network.data.TreasuryView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The view "Cities" of the "Treasury" tab: the taxes of the kingdom for its cities, a row of each city (its treasury, or what
 * it brings the crown) and, after a click on a row, the window of the city with its statement, its own taxes, grants and money
 * taken back, the evasion and the means of its people and its shops. Every change is an intent the server checks.
 */
final class TreasuryCities {
    private static final String KEY = "kingdomworld.screen.cities.";
    private static final int ROW = TreasuryTab.ROW;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int BOX = 30;
    private static final String[] LABELS = {"sales_tax", "tithe", "crown_share"};
    private static final int AMOUNT_BOX = 70;
    private static final int MIN_AMOUNT_BOX = 40;
    /** Height of a row of a city. */
    static final int CITY_ROW = LINE + 2;

    private final ThroneScreen screen;
    private CityViews.CityDetail detail;
    private boolean crownView;
    private boolean editing;
    private int scroll;
    /** Whether the list was left or another city opened, so that the tab scrolls back to the top. */
    private boolean reset;
    private String salesText = "";
    private String titheText = "";
    private String shareText = "";
    private String citySales = "";
    private String cityTithe = "";
    private String cityShare = "";
    private String amountText = "";
    /** Where the amount and its buttons start, in the row of the view next to the button back. */
    private int amountX;
    private int amountY;
    private int amountWidth = AMOUNT_BOX;

    private int toggleWidth;
    private int editWidth;
    private int setWidth;
    private int backWidth;
    private int grantWidth;
    private int recallWidth;
    private int pageButtonWidth;
    private int numberWidth;
    private int[] taxLabelWidths = new int[3];

    TreasuryCities(ThroneScreen screen) {
        this.screen = screen;
    }

    CityViews.CityDetail detail() {
        return detail;
    }

    boolean crownView() {
        return crownView;
    }

    void setCrownView(boolean value) {
        crownView = value;
        scroll = 0;
    }

    void setDetail(CityViews.CityDetail value) {
        boolean other = detail == null || detail.cityId() != value.cityId();
        detail = value;
        if (other) {
            scroll = 0;
            reset = true;
            amountText = "";
        }
        citySales = setting(value.own().salesTax());
        cityTithe = setting(value.own().tithe());
        cityShare = setting(value.own().crownShare());
    }

    void closeDetail() {
        detail = null;
        scroll = 0;
        reset = true;
    }

    /**
     * Whether the tab should scroll back to the top, once.
     */
    boolean consumeReset() {
        boolean was = reset;
        reset = false;
        return was;
    }

    void viewChanged(TreasuryView view) {
        if (!editing) {
            CityViews.Taxes taxes = view.cities().taxes();
            salesText = setting(taxes.salesTax());
            titheText = setting(taxes.tithe());
            shareText = setting(taxes.crownShare());
        }
        if (detail != null && !detail.kingdomId().equals(view.kingdomId())) {
            detail = null;
        }
    }

    String checkName() {
        return detail != null ? "treasury-city-panel" : crownView ? "treasury-cities-crown" : "treasury-cities-treasury";
    }

    // ---------------------------------------------------------------- texts

    private static String setting(int permille) {
        return permille < 0 ? "" : percent(permille);
    }

    static String percent(int permille) {
        return permille % 10 == 0 ? Integer.toString(permille / 10) : String.format(java.util.Locale.ROOT, "%.1f", permille / 10.0);
    }

    /**
     * A percent typed into a field, in thousandths: -1 for an empty field, -2 for one that is no number.
     */
    private static int parsePercent(String text) {
        String value = text.strip().replace(',', '.');
        if (value.isEmpty()) {
            return -1;
        }
        try {
            double percent = Double.parseDouble(value);
            if (percent < 0 || percent > 100) {
                return -2;
            }
            return (int) Math.round(percent * 10);
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    static Component taxesLine(CityViews.Taxes taxes) {
        return Component.translatable(KEY + "taxes", percent(taxes.salesTax()), percent(taxes.tithe()), percent(taxes.crownShare()));
    }

    private static String number(long value, boolean signed) {
        String text = String.format(java.util.Locale.ROOT, "%,d", Math.abs(value)).replace(',', ' ');
        if (value < 0) {
            return "-" + text;
        }
        return signed && value > 0 ? "+" + text : text;
    }

    private Component money(TreasuryView view, long amount) {
        CurrencyUnits units = view.units();
        if (amount < 0) {
            return Component.literal("-").append(MoneyTexts.amount(-amount, units));
        }
        return MoneyTexts.amount(amount, units);
    }

    private static List<Component> columns(boolean crown) {
        List<Component> headers = new ArrayList<>();
        headers.add(Component.translatable(KEY + "city"));
        for (String key : crown ? List.of("money", "goods", "total") : List.of("income", "kept", "to_crown", "spent", "balance")) {
            headers.add(Component.translatable(KEY + key));
        }
        return headers;
    }

    private static long[] values(CityViews.CityRow row, boolean crown) {
        return crown ? new long[]{row.money(), row.goods(), row.total()}
                : new long[]{row.income(), row.kept(), row.toCrown(), row.spent(), row.balance()};
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        toggleWidth = screen.buttonWidth(Component.translatable(KEY + "view.treasury"), Component.translatable(KEY + "view.crown"));
        editWidth = screen.buttonWidth(Component.translatable(KEY + "edit"));
        setWidth = screen.buttonWidth(Component.translatable(KEY + "set"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        grantWidth = screen.buttonWidth(Component.translatable(KEY + "grant"));
        recallWidth = screen.buttonWidth(Component.translatable(KEY + "recall"));
        pageButtonWidth = screen.buttonWidth(Component.literal("<"), Component.literal(">"));
        numberWidth = font.width("-999 999") + 6;
        for (Component header : columns(false)) {
            numberWidth = Math.max(numberWidth, font.width(header) + 6);
        }
        for (Component header : columns(true)) {
            numberWidth = Math.max(numberWidth, font.width(header) + 6);
        }
        String[] labels = {"sales_tax", "tithe", "crown_share"};
        int form = 0;
        for (int i = 0; i < 3; i++) {
            taxLabelWidths[i] = font.width(Component.translatable(KEY + "label." + labels[i])) + 4;
            form += taxLabelWidths[i] + BOX + 6;
        }
        form += setWidth;
        int table = 70 + 5 * numberWidth;
        int amount = AMOUNT_BOX + GAP + grantWidth + GAP + recallWidth;
        return Math.max(Math.max(form, table), amount);
    }

    int toggleWidth() {
        return toggleWidth;
    }

    private int rowsTop(int listTop) {
        return listTop + ROW + LINE + 2;
    }

    int visibleRows(int listTop, int bottom) {
        return Math.max(1, (bottom - rowsTop(listTop)) / CITY_ROW);
    }

    private int panelTop(TreasuryView view, int listTop) {
        return managing(view) ? listTop + ROW + 4 : listTop;
    }

    /**
     * Whether the window of a city shows its forms: the amount and its buttons then stand in the row of the view in place of the
     * name of the city, which opens the lines instead, and the taxes take one row above the lines.
     */
    boolean managing(TreasuryView view) {
        return view.canManage() && detail != null && detail.canManage();
    }

    /**
     * Where the taxes of the window of a city end, so that the count of its lines fits to the right of them.
     */
    int taxRowEnd(int x) {
        return x + taxLabelWidths[0] + taxLabelWidths[1] + taxLabelWidths[2] + 3 * (BOX + 6) + setWidth;
    }

    int visiblePanelLines(TreasuryView view, int listTop, int bottom) {
        return Math.max(1, (bottom - panelTop(view, listTop)) / (LINE + 1));
    }

    int rowCount(TreasuryView view) {
        return detail != null ? panelLines(view).size() : view.cities().rows().size();
    }

    int visible(TreasuryView view, int listTop, int bottom) {
        return detail != null ? visiblePanelLines(view, listTop, bottom) : visibleRows(listTop, bottom);
    }

    int scroll() {
        return scroll;
    }

    void setScroll(int value) {
        scroll = Math.max(0, value);
    }

    // ---------------------------------------------------------------- widgets

    /**
     * Adds the buttons and fields of the view.
     *
     * @param buttonsX where the buttons next to the choice of the view start
     */
    void init(TreasuryView view, int buttonsX, int buttonsY, int x, int right, int listTop) {
        if (detail == null) {
            screen.add(new CycleButton(buttonsX, buttonsY, toggleWidth, ROW - 2, Component.translatable(crownView ? KEY + "view.crown"
                    : KEY + "view.treasury"), direction -> {
                setCrownView(!crownView);
                screen.rebuild();
            }));
            initList(view, x, right, listTop);
            return;
        }
        Button back = screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
            closeDetail();
            screen.rebuild();
        }).bounds(buttonsX, buttonsY, backWidth, ROW - 2).build());
        back.setTooltip(Tooltip.create(Component.translatable(KEY + "back_hint")));
        if (view.canManage() && detail.canManage()) {
            initPanelForm(view, buttonsX + backWidth + GAP + 4, buttonsY, x, right, listTop);
        }
    }

    private void initList(TreasuryView view, int x, int right, int listTop) {
        CityViews.Cities cities = view.cities();
        int buttonX = right;
        if (cities.count() > CityViews.PAGE) {
            int pages = (cities.count() + CityViews.PAGE - 1) / CityViews.PAGE;
            Button next = screen.add(Button.builder(Component.literal(">"), b -> requestPage(view, cities.page() + 1))
                    .bounds(buttonX - pageButtonWidth, listTop, pageButtonWidth, ROW - 2).build());
            next.active = cities.page() + 1 < pages;
            buttonX -= pageButtonWidth + GAP;
            Button previous = screen.add(Button.builder(Component.literal("<"), b -> requestPage(view, cities.page() - 1))
                    .bounds(buttonX - pageButtonWidth, listTop, pageButtonWidth, ROW - 2).build());
            previous.active = cities.page() > 0;
            buttonX -= pageButtonWidth + GAP;
        }
        if (!view.canManage()) {
            return;
        }
        if (!editing) {
            Button edit = screen.add(Button.builder(Component.translatable(KEY + "edit"), b -> {
                editing = true;
                screen.rebuild();
            }).bounds(buttonX - editWidth, listTop, editWidth, ROW - 2).build());
            edit.setTooltip(Tooltip.create(Component.translatable(KEY + "edit_hint", cities.maxSalesTax(), cities.maxTithe())));
            return;
        }
        int fieldX = x;
        String[] texts = {salesText, titheText, shareText};
        for (int i = 0; i < 3; i++) {
            int index = i;
            fieldX += taxLabelWidths[i];
            EditBox box = new EditBox(screen.font(), fieldX + 1, listTop + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "label." + LABELS[i]));
            box.setMaxLength(5);
            box.setValue(texts[i]);
            box.setResponder(value -> {
                if (index == 0) {
                    salesText = value;
                } else if (index == 1) {
                    titheText = value;
                } else {
                    shareText = value;
                }
            });
            screen.add(box);
            fieldX += BOX + 6;
        }
        Button set = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            int sales = parsePercent(salesText);
            int tithe = parsePercent(titheText);
            int share = parsePercent(shareText);
            if (sales >= 0 && tithe >= 0 && share >= 0) {
                editing = false;
                send(view, EconomyPackets.TreasuryChange.Action.CITY_DEFAULTS, sales + "," + tithe + "," + share, 0);
            }
        }).bounds(fieldX, listTop, setWidth, ROW - 2).build());
        set.setTooltip(Tooltip.create(Component.translatable(KEY + "edit_hint", cities.maxSalesTax(), cities.maxTithe())));
    }

    private void initPanelForm(TreasuryView view, int formX, int formY, int x, int right, int listTop) {
        int fieldX = x;
        String[] texts = {citySales, cityTithe, cityShare};
        for (int i = 0; i < 3; i++) {
            int index = i;
            fieldX += taxLabelWidths[i];
            EditBox box = new EditBox(screen.font(), fieldX + 1, listTop + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "label." + LABELS[i]));
            box.setMaxLength(5);
            box.setValue(texts[i]);
            box.setHint(Component.translatable(KEY + "as_kingdom_short"));
            box.setTooltip(Tooltip.create(Component.translatable(KEY + "as_kingdom")));
            box.setResponder(value -> {
                if (index == 0) {
                    citySales = value;
                } else if (index == 1) {
                    cityTithe = value;
                } else {
                    cityShare = value;
                }
            });
            screen.add(box);
            fieldX += BOX + 6;
        }
        Button set = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            int sales = parsePercent(citySales);
            int tithe = parsePercent(cityTithe);
            int share = parsePercent(cityShare);
            if (sales >= -1 && tithe >= -1 && share >= -1) {
                send(view, EconomyPackets.TreasuryChange.Action.CITY_SET, detail.cityId() + "," + sales + "," + tithe + "," + share, 0);
            }
        }).bounds(fieldX, listTop, setWidth, ROW - 2).build());
        set.setTooltip(Tooltip.create(Component.translatable(KEY + "as_kingdom")));
        int y = formY;
        amountX = formX;
        amountY = formY;
        // The amount gives up some of its width on the smallest screens, so that both buttons stay inside the window.
        amountWidth = Math.max(MIN_AMOUNT_BOX, Math.min(AMOUNT_BOX, right - formX - grantWidth - recallWidth - 2 * GAP));
        EditBox amount = new EditBox(screen.font(), formX + 1, y + 1, amountWidth - 2, ROW - 4, Component.translatable(KEY + "amount"));
        amount.setMaxLength(Money.MAX_TEXT);
        amount.setValue(amountText);
        amount.setHint(Component.translatable(KEY + "amount"));
        amount.setResponder(value -> amountText = value);
        screen.add(amount);
        int buttonX = formX + amountWidth + GAP;
        Button grant = screen.add(Button.builder(Component.translatable(KEY + "grant"), b -> Money.parse(amountText, view.units())
                .filter(value -> value > 0).ifPresent(value -> send(view, EconomyPackets.TreasuryChange.Action.CITY_GRANT,
                        Long.toString(detail.cityId()), value))).bounds(buttonX, y, grantWidth, ROW - 2).build());
        grant.setTooltip(Tooltip.create(Component.translatable(KEY + "grant_hint", money(view, detail.crownFunds()))));
        grant.active = detail.crownFunds() > 0;
        buttonX += grantWidth + GAP;
        Button recall = screen.add(Button.builder(Component.translatable(KEY + "recall"), b -> Money.parse(amountText, view.units())
                .filter(value -> value > 0).ifPresent(value -> send(view, EconomyPackets.TreasuryChange.Action.CITY_RECALL,
                        Long.toString(detail.cityId()), value))).bounds(buttonX, y, recallWidth, ROW - 2).build());
        recall.setTooltip(Tooltip.create(Component.translatable(KEY + "recall_hint",
                money(view, Math.max(0, detail.balance() - detail.committed())), money(view, detail.granted()))));
        recall.active = detail.balance() - detail.committed() > 0;
    }

    private void requestPage(TreasuryView view, int page) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EconomyPackets.TreasuryRequest(view.kingdomId(), Math.max(0, page)));
        }
    }

    private void send(TreasuryView view, EconomyPackets.TreasuryChange.Action action, String text, long amount) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EconomyPackets.TreasuryChange(view.kingdomId(), action, text, amount));
        }
    }

    /**
     * A click on the row of a city opens its window.
     */
    boolean click(TreasuryView view, int listTop, int bottom, int x, int right, double mouseX, double mouseY) {
        if (detail != null || mouseX < x || mouseX >= right) {
            return false;
        }
        int top = rowsTop(listTop);
        if (mouseY < top || mouseY >= bottom) {
            return false;
        }
        int index = scroll + (int) ((mouseY - top) / CITY_ROW);
        List<CityViews.CityRow> rows = view.cities().rows();
        if (index < 0 || index >= rows.size() || index - scroll >= visibleRows(listTop, bottom)) {
            return false;
        }
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new EconomyPackets.CityDetailRequest(view.kingdomId(), rows.get(index).id()));
        }
        return true;
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, Font font, TreasuryView view, int x, int right, int listTop, int bottom) {
        if (detail != null) {
            renderPanel(graphics, font, view, x, right, listTop, bottom);
            return;
        }
        CityViews.Cities cities = view.cities();
        if (!cities.enabled()) {
            screen.paragraph(graphics, Component.translatable(KEY + "off"), x, listTop + 4, right - x, 4, 0xFFB8B8B8);
            return;
        }
        if (!editing) {
            int textRight = view.canManage() ? right - editWidth - 4 : right;
            if (cities.count() > CityViews.PAGE) {
                textRight -= 2 * (pageButtonWidth + GAP);
            }
            screen.name(graphics, taxesLine(cities.taxes()).getString(), x, listTop + 5, 0xFFF0D070, textRight - x);
        } else {
            String[] labels = {"sales_tax", "tithe", "crown_share"};
            int labelX = x;
            for (int i = 0; i < 3; i++) {
                screen.label(graphics, Component.translatable(KEY + "label." + labels[i]), labelX, listTop + 5, 0xFFE8E8E8,
                        labelX + taxLabelWidths[i]);
                labelX += taxLabelWidths[i] + BOX + 6;
            }
        }
        int headerY = listTop + ROW + 1;
        List<Component> headers = columns(crownView);
        int columnsLeft = right - (headers.size() - 1) * numberWidth;
        screen.label(graphics, headers.get(0), x, headerY, 0xFF9A9A9A, columnsLeft - 4);
        for (int i = 1; i < headers.size(); i++) {
            int columnRight = columnsLeft + i * numberWidth;
            Component header = headers.get(i);
            screen.label(graphics, header, columnRight - font.width(header), headerY, 0xFF9A9A9A, columnRight);
        }
        List<CityViews.CityRow> rows = cities.rows();
        if (rows.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(KEY + "none"), x, rowsTop(listTop) + 2, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int visible = visibleRows(listTop, bottom);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            CityViews.CityRow row = rows.get(scroll + i);
            int y = rowsTop(listTop) + i * CITY_ROW;
            int color = row.capital() ? 0xFFF0D070 : 0xFFFFFFFF;
            int drawn = screen.name(graphics, row.name(), x, y, color, columnsLeft - 4 - x);
            long[] values = values(row, crownView);
            for (int c = 0; c < values.length; c++) {
                int columnRight = columnsLeft + (c + 1) * numberWidth;
                String text = number(values[c], crownView);
                int valueColor = crownView ? (values[c] < 0 ? 0xFFFF9070 : values[c] > 0 ? 0xFF90E090 : 0xFFD0D0D0) : 0xFFD0D0D0;
                screen.name(graphics, text, columnRight - Math.min(font.width(text), numberWidth - 4), y, valueColor, numberWidth - 4);
            }
            // The whole row on hover: what each number is, in money.
            for (Component line : rowHover(view, row)) {
                screen.hoverText(x, y, right - x, line);
            }
            if (drawn > 0) {
                graphics.fill(x, y + LINE, right, y + LINE + 1, 0x30FFFFFF);
            }
        }
    }

    private List<Component> rowHover(TreasuryView view, CityViews.CityRow row) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(row.name()));
        lines.add(Component.translatable(KEY + "hover.treasury", money(view, row.balance())));
        lines.add(Component.translatable(KEY + "hover.day", money(view, row.income()), money(view, row.kept()), money(view, row.toCrown()),
                money(view, row.spent())));
        lines.add(Component.translatable(KEY + "hover.crown", money(view, row.money()), money(view, row.goods()), money(view, row.total())));
        lines.add(Component.translatable(KEY + "hover.open"));
        return lines;
    }

    private List<Component> panelLines(TreasuryView view) {
        List<Component> lines = new ArrayList<>();
        if (detail == null) {
            return lines;
        }
        CityViews.CityDetail d = detail;
        Component treasury = Component.translatable(KEY + "panel.treasury", money(view, d.balance()), money(view, d.granted()));
        lines.add(managing(view) ? Component.translatable(KEY + "panel.named", d.name(), treasury) : treasury);
        lines.add(Component.translatable(KEY + "panel.taxes", percent(d.effective().salesTax()), percent(d.effective().tithe()),
                percent(d.effective().crownShare())));
        lines.add(Component.translatable(KEY + "panel.day", money(view, d.average(CityStatement.Line.TITHE) + d.average(CityStatement.Line.SALES_TAX)),
                money(view, d.average(CityStatement.Line.KEPT)), money(view, d.average(CityStatement.Line.TO_CROWN)),
                money(view, d.average(CityStatement.Line.SPENT))));
        long crownMoney = d.average(CityStatement.Line.CROWN_GOODS_SOLD) + d.average(CityStatement.Line.CROWN_TAVERNS)
                + d.average(CityStatement.Line.CROWN_EXPORTS) + d.average(CityStatement.Line.CROWN_MINTING) + d.average(CityStatement.Line.CROWN_SHARE)
                - d.average(CityStatement.Line.CROWN_WAGES) - d.average(CityStatement.Line.CROWN_GRANTS) - d.average(CityStatement.Line.CROWN_NOBLES);
        long goods = d.average(CityStatement.Line.GOODS_MADE) - d.average(CityStatement.Line.GOODS_RAW) - d.average(CityStatement.Line.GOODS_EATEN)
                - d.average(CityStatement.Line.GOODS_OUT);
        lines.add(Component.translatable(KEY + "panel.crown", money(view, crownMoney), money(view, goods), money(view, crownMoney + goods)));
        lines.add(Component.translatable(KEY + "evasion", percent((d.evasion() + 5) / 10 * 10)));
        int[] means = d.means();
        lines.add(Component.translatable(KEY + "panel.means", (means[4] > 0 ? "+" : "") + means[4]));
        if (!d.head()) {
            lines.add(Component.translatable(KEY + "no_head"));
        }
        lines.add(Component.translatable(KEY + "shops", d.shopCount()));
        for (CityViews.ShopRow shop : d.shops()) {
            Component keeper = shop.keeper().isEmpty() ? Component.translatable("kingdomworld.business.status.family")
                    : Component.literal(shop.keeper());
            Component trade = Component.translatable("kingdomworld.business.trade." + shop.trade());
            if (shop.pinned()) {
                trade = Component.translatable(KEY + "shop.pinned", trade);
            }
            if (shop.made() > 0 || shop.exported() > 0) {
                lines.add(Component.translatable(KEY + "shop.craft", trade, keeper, money(view, shop.sales()), money(view, shop.made()),
                        money(view, shop.exported())));
            } else {
                lines.add(Component.translatable(KEY + "shop", trade, keeper, Component.translatable("kingdomworld.business.status." + shop.status()),
                        money(view, shop.sales()), percent(shop.margin())));
            }
        }
        if (d.shortfall() > 0) {
            lines.add(Component.translatable(KEY + "shortfall", money(view, d.shortfall())));
            switch (d.blocked()) {
                case NO_PLACES -> lines.add(Component.translatable(KEY + "blocked.places"));
                case NO_TOWNSFOLK -> lines.add(Component.translatable(KEY + "blocked.townsfolk"));
                case NO_GOODS -> lines.add(Component.translatable(KEY + "blocked.goods", d.missing()));
                default -> {
                }
            }
        }
        return lines;
    }

    private List<Component> statementHover(TreasuryView view) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "statement", detail.days()));
        for (CityStatement.Line line : CityStatement.Line.values()) {
            long value = detail.latest(line);
            if (value != 0 && line != CityStatement.Line.BALANCE) {
                lines.add(Component.translatable(KEY + "statement_line", Component.translatable("kingdomworld.city_statement." + line.id()),
                        money(view, value)));
            }
        }
        return lines;
    }

    private void renderPanel(GuiGraphics graphics, Font font, TreasuryView view, int x, int right, int listTop, int bottom) {
        int top = listTop;
        if (view.canManage() && detail.canManage()) {
            String[] labels = {"sales_tax", "tithe", "crown_share"};
            int labelX = x;
            for (int i = 0; i < 3; i++) {
                screen.label(graphics, Component.translatable(KEY + "label." + labels[i]), labelX, top + 5, 0xFFE8E8E8, labelX + taxLabelWidths[i]);
                labelX += taxLabelWidths[i] + BOX + 6;
            }
            int previewX = amountX + amountWidth + GAP + grantWidth + GAP + recallWidth + 6;
            if (!amountText.isBlank() && previewX + 8 < right) {
                Optional<Long> parsed = Money.parse(amountText, view.units());
                String shown = parsed.map(value -> MoneyTexts.amount(value, view.units()).getString())
                        .orElseGet(() -> Component.translatable("kingdomworld.screen.treasury.bad_amount").getString());
                screen.name(graphics, shown, previewX, amountY + 4, parsed.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - previewX);
            }
        }
        int y = panelTop(view, listTop);
        List<Component> lines = panelLines(view);
        int visible = visiblePanelLines(view, listTop, bottom);
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            int index = scroll + i;
            int lineY = y + i * (LINE + 1);
            int color = index == 0 ? 0xFFF0D070 : 0xFFE0E0E0;
            int drawn = screen.name(graphics, lines.get(index).getString(), x, lineY, color, right - x);
            if (index > 5) {
                // A line cut short shows whole under the mouse.
                screen.hoverText(x, lineY, drawn, lines.get(index));
            }
            if (index == 0 || index == 2) {
                for (Component line : statementHover(view)) {
                    screen.hoverText(x, lineY, right - x, line);
                }
            }
            if (index == 5) {
                int[] means = detail.means();
                screen.hoverText(x, lineY, right - x, Component.translatable("kingdomworld.citizen.mood.means.burden", signed(means[0])));
                if (means[1] != 0) {
                    screen.hoverText(x, lineY, right - x, Component.translatable("kingdomworld.citizen.mood.means.shops", signed(means[1])));
                }
                if (means[2] != 0) {
                    screen.hoverText(x, lineY, right - x, Component.translatable("kingdomworld.citizen.mood.means.recall", signed(means[2])));
                }
                if (means[3] != 0) {
                    screen.hoverText(x, lineY, right - x, Component.translatable("kingdomworld.citizen.mood.means.share", signed(means[3])));
                }
            }
        }
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /**
     * The title of the window of a city, for the line of the buttons.
     */
    Component title() {
        return detail == null ? Component.empty() : Component.literal(detail.name());
    }

    int backWidth() {
        return backWidth;
    }

    /**
     * The line of the overview of the treasury about the cities; empty while the money of the cities is off.
     */
    Optional<Component> overviewLine(TreasuryView view) {
        CityViews.Cities cities = view.cities();
        if (!cities.enabled()) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable(KEY + "overview", MoneyTexts.amount(Math.max(0, cities.crownPerDay()), view.units()),
                MoneyTexts.amount(cities.held(), view.units())));
    }

    /**
     * Fills the forms, for the layout check.
     */
    void fillForCheck(boolean edit, String amount) {
        editing = edit;
        amountText = amount;
    }
}
