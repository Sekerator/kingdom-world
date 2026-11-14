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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The window of a shop of citizens: what it sells, the cheapest first, with the price with the tax as this player pays it, and
 * what the shop is. The player buys whole items from the account or with coins of the currency of the kingdom of the shop; the
 * server checks the price the player saw and answers with a fresh view.
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

    private int panelWidth;
    private int panelHeight;
    private int tabWidth;
    private int payWidth;
    private int actionWidth;
    private int pageWidth;
    private int doneWidth;
    private int countLabelWidth;

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

    private List<Component> aboutLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "about.trade", Component.translatable("kingdomworld.business.trade." + view.trade())));
        lines.add(view.keeper().isEmpty() ? Component.translatable(KEY + "about.family")
                : Component.translatable(KEY + "about.keepers", view.keeper()));
        lines.add(Component.translatable(KEY + "about.status", Component.translatable("kingdomworld.business.status." + view.status())));
        lines.add(Component.translatable(KEY + "about.margin", TradeTexts.percent(view.margin()), TradeTexts.percent(view.salesTax())));
        lines.add(Component.translatable(KEY + "about.sales", money(view.sales())));
        lines.add(view.debt() > 0 ? Component.translatable(KEY + "about.debt", money(view.debt())) : Component.translatable(KEY + "about.no_debt"));
        lines.add(Component.translatable(KEY + "about.currency", view.currencyName()));
        return lines;
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        tabWidth = 0;
        for (Tab value : Tab.values()) {
            tabWidth = Math.max(tabWidth, buttonWidth(tabLabel(value)));
        }
        payWidth = buttonWidth(Component.translatable("kingdomworld.screen.stall.pay.account"),
                Component.translatable("kingdomworld.screen.stall.pay.coins"));
        actionWidth = buttonWidth(Component.translatable(KEY + "buy"));
        pageWidth = buttonWidth(Component.literal("<"), Component.literal(">"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        countLabelWidth = font.width(Component.translatable("kingdomworld.screen.stall.count_label")) + 6;
        int tabs = 2 * (tabWidth + GAP) + 2 * (pageWidth + GAP);
        int controls = payWidth + GAP + countLabelWidth + BOX;
        int inner = Math.max(Math.max(tabs, controls), 200 + actionWidth);
        for (Component line : aboutLines()) {
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
        if (pages() > 1) {
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page(view.page() + 1))
                    .bounds(right - pageWidth, tabsY(), pageWidth, ROW - 2).build());
            next.active = view.page() + 1 < pages();
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page(view.page() - 1))
                    .bounds(right - 2 * pageWidth - GAP, tabsY(), pageWidth, ROW - 2).build());
            previous.active = view.page() > 0;
        }
        if (tab == Tab.BUY) {
            x = left + PAD;
            int y = controlsY();
            addRenderableWidget(Button.builder(payLabel(), b -> {
                account = !account;
                rebuildWidgets();
            }).bounds(x, y, payWidth, ROW - 2).build());
            int next = x + payWidth + GAP;
            EditBox count = new EditBox(font, next + countLabelWidth + 1, y + 1, BOX - 2, ROW - 4,
                    Component.translatable("kingdomworld.screen.stall.count_label"));
            count.setMaxLength(4);
            count.setValue(countText);
            count.setResponder(value -> countText = value);
            addRenderableWidget(count);
            int rows = visibleRows();
            scroll = Math.max(0, Math.min(scroll, view.rows().size() - rows));
            for (int i = 0; i < rows && scroll + i < view.rows().size(); i++) {
                ShopView.Row row = view.rows().get(scroll + i);
                int rowY = listTop() + i * ROW;
                Button buy = addRenderableWidget(Button.builder(Component.translatable(KEY + "buy"), b -> buy(row))
                        .bounds(right - actionWidth, rowY, actionWidth, ROW - 2).build());
                buy.active = view.canBuy() && row.count() > 0;
                buy.setTooltip(Tooltip.create(Component.translatable(KEY + "price", money(row.price()), money(row.tax()))));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private void buy(ShopView.Row row) {
        if (Minecraft.getInstance().getConnection() != null) {
            int count = (int) Math.min(count(), row.count());
            KingdomWorldNetwork.sendToServer(new CommercePackets.ShopTrade(view.shopId(), itemId(row.item()), count, row.price(), account));
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
        int max = Math.max(0, view.rows().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (tab == Tab.BUY && next != scroll) {
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
        String title = Component.translatable(KEY + "title", Component.translatable("kingdomworld.business.trade." + view.trade()),
                view.kingdomName()).getString();
        centeredName(graphics, title, width / 2, top + 7, 0xFFF0D070, panelWidth - 2 * PAD);
        Component who = view.keeper().isEmpty() ? Component.translatable(KEY + "about.family") : Component.translatable(KEY + "about.keepers",
                view.keeper());
        name(graphics, who.getString(), x, top + 20, 0xFFD0D0D0, right - x);
        if (tab == Tab.BUY) {
            int countX = x + payWidth + GAP;
            label(graphics, Component.translatable("kingdomworld.screen.stall.count_label"), countX, controlsY() + 5, 0xFFE8E8E8,
                    countX + countLabelWidth - 2);
            int rows = visibleRows();
            List<ShopView.Row> goods = view.rows();
            if (goods.isEmpty()) {
                paragraph(graphics, Component.translatable(KEY + "empty"), x, listTop() + 5, right - x, 2, 0xFFB8B8B8);
            }
            int textRight = right - actionWidth - 4;
            int nameWidth = Math.min(120, (textRight - x - 20) / 2);
            for (int i = 0; i < rows && scroll + i < goods.size(); i++) {
                ShopView.Row row = goods.get(scroll + i);
                int rowY = listTop() + i * ROW;
                graphics.renderItem(row.item(), x, rowY + 1);
                name(graphics, row.item().getHoverName().getString(), x + 20, rowY + 5, 0xFFFFFFFF, nameWidth - 4);
                int detailX = x + 20 + nameWidth;
                Component detail = Component.translatable(KEY + "row", money(row.price()), TradeTexts.items(row.count()));
                int drawn = name(graphics, detail.getString(), detailX, rowY + 5, 0xFFD0D0D0, textRight - detailX);
                hoverText(detailX, rowY + 5, drawn, Component.translatable(KEY + "price", money(row.price()), money(row.tax())));
            }
            if (goods.size() > rows) {
                Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(goods.size(), scroll + rows),
                        goods.size());
                label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
            }
        } else {
            int y = controlsY() + 2;
            for (Component line : aboutLines()) {
                name(graphics, line.getString(), x, y, 0xFFE0E0E0, right - x);
                y += LINE + 2;
            }
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = view.canBuy() ? Component.translatable(KEY + "hint", TradeTexts.percent(view.salesTax()))
                    : Component.translatable(view.why().isEmpty() ? KEY + "hint" : view.why(), TradeTexts.percent(view.salesTax()));
            color = view.canBuy() ? 0xFFB8B8B8 : 0xFFFFB070;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
