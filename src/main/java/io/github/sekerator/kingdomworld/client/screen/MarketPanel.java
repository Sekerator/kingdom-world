package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The tabs "Slave market" and "Hire companions" of the desk of the guild: the people on offer with their level, strength,
 * skills and price, a button to buy or hire each, how to pay (from the account or with coins), how many companions the
 * rank allows, and for the king the switch that closes the market of slaves on the kingdom's land.
 */
final class MarketPanel {
    private static final String KEY = "kingdomworld.screen.market_companions.";
    private static final int ROW = 20;
    private static final int GAP = 2;

    private final GuildDeskScreen screen;
    private CompanionViews.MarketView view;
    private boolean coins;
    private int scroll;
    private int buyWidth;
    private int payWidth;
    private int closeWidth;
    private int badgeWidth;

    MarketPanel(GuildDeskScreen screen) {
        this.screen = screen;
    }

    CompanionViews.MarketView view() {
        return view;
    }

    void update(CompanionViews.MarketView value) {
        view = value;
    }

    void resetScroll() {
        scroll = 0;
    }

    /**
     * Asks the server for the market of the desk.
     */
    void request() {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CompanionPackets.MarketRequest(screen.pos()));
        }
    }

    private void send(CompanionPackets.MarketAction.Action action, long offer) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CompanionPackets.MarketAction(screen.pos(), action, offer, coins));
        }
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view.currency().units());
    }

    // ---------------------------------------------------------------- layout

    /**
     * The width the panel needs.
     */
    int measure(Font font) {
        buyWidth = screen.buttonWidth(Component.translatable(KEY + "buy"), Component.translatable(KEY + "hire"));
        payWidth = screen.buttonWidth(Component.translatable(KEY + "pay_account"), Component.translatable(KEY + "pay_coins"));
        closeWidth = screen.buttonWidth(Component.translatable(KEY + "close"), Component.translatable(KEY + "open"));
        badgeWidth = font.width(Component.translatable(KEY + "level", 30)) + 4;
        return Math.max(payWidth + GAP + closeWidth, 16 + badgeWidth + 160 + buyWidth);
    }

    private List<CompanionViews.OfferRow> rows(boolean slaves) {
        if (view == null) {
            return List.of();
        }
        return slaves ? view.slaves() : view.free();
    }

    private boolean listShown(boolean slaves) {
        return view != null && view.member() && !(slaves && (view.closed() || view.forbidden()));
    }

    static int listTop(int top) {
        return top + ROW + 2 + 10 + 2;
    }

    int visibleRows(int top, int bottom) {
        return Math.max(1, (bottom - listTop(top) - 11) / ROW);
    }

    int rowCount(boolean slaves) {
        return listShown(slaves) ? rows(slaves).size() : 0;
    }

    /**
     * @return whether the scroll changed
     */
    boolean scroll(boolean slaves, int top, int bottom, double delta) {
        int max = Math.max(0, rowCount(slaves) - visibleRows(top, bottom));
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        boolean changed = next != scroll;
        scroll = next;
        return changed;
    }

    void init(boolean slaves, int x, int right, int top, int bottom) {
        if (view == null) {
            return;
        }
        Button pay = screen.add(Button.builder(Component.translatable(coins ? KEY + "pay_coins" : KEY + "pay_account"), b -> {
            coins = !coins;
            screen.rebuild();
        }).bounds(x, top, payWidth, ROW - 2).build());
        pay.setTooltip(Tooltip.create(Component.translatable(KEY + "pay_hint")));
        if (slaves && view.canClose()) {
            screen.add(Button.builder(Component.translatable(view.closed() ? KEY + "open" : KEY + "close"),
                    b -> send(view.closed() ? CompanionPackets.MarketAction.Action.OPEN_MARKET : CompanionPackets.MarketAction.Action.CLOSE_MARKET, 0))
                    .bounds(x + payWidth + GAP, top, closeWidth, ROW - 2)
                    .tooltip(Tooltip.create(Component.translatable(KEY + "close_hint"))).build());
        }
        if (!listShown(slaves)) {
            return;
        }
        List<CompanionViews.OfferRow> rows = rows(slaves);
        int visible = visibleRows(top, bottom);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        boolean room = view.owned() < view.limit();
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            CompanionViews.OfferRow row = rows.get(scroll + i);
            int y = listTop(top) + i * ROW;
            Button take = screen.add(Button.builder(Component.translatable(slaves ? KEY + "buy" : KEY + "hire"),
                    b -> send(CompanionPackets.MarketAction.Action.TAKE, row.id()))
                    .bounds(right - buyWidth, y, buyWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
            take.active = room;
        }
    }

    /**
     * Everything about a person on offer, for the tooltip.
     */
    private Component details(CompanionViews.OfferRow row) {
        Component price = row.slave() ? Component.translatable(KEY + "price", money(row.price()))
                : Component.translatable(KEY + "wage", money(row.price()));
        return Component.translatable(KEY + "details", CitizenTexts.name(row.seed(), row.gender()), CompanionTexts.origin(row.origin(), row.gender()),
                row.level(), row.power(), row.health(), row.combat(), row.carrying(), row.stamina(),
                new Skills(row.combat(), row.carrying(), row.stamina()).slots(), price);
    }

    private Component header(boolean slaves) {
        Component count = Component.translatable(KEY + "count", view.owned(), view.limit(), GuildTexts.rank(view.rank()));
        return Component.translatable(KEY + "header", count, io.github.sekerator.kingdomworld.client.screen.GuildDeskScreen.time(view.ticksToChange()));
    }

    void render(GuiGraphics graphics, boolean slaves, int x, int right, int top, int bottom) {
        Font font = Minecraft.getInstance().font;
        if (view == null) {
            screen.paragraph(graphics, Component.translatable(KEY + "loading"), x, top + 5, right - x, 1, 0xFFB8B8B8);
            return;
        }
        int infoY = top + ROW + 2;
        screen.name(graphics, header(slaves).getString(), x, infoY, 0xFFF0D070, right - x);
        if (!view.member()) {
            screen.paragraph(graphics, Component.translatable(KEY + "members_only"), x, listTop(top) + 5, right - x, 3, 0xFFE8E8E8);
            return;
        }
        if (slaves && view.forbidden()) {
            screen.paragraph(graphics, Component.translatable("kingdomworld.error.slavery_forbidden"), x, listTop(top) + 5, right - x, 3, 0xFFFF9070);
            return;
        }
        if (slaves && view.closed()) {
            screen.paragraph(graphics, Component.translatable(KEY + "closed_text"), x, listTop(top) + 5, right - x, 3, 0xFFFF9070);
            return;
        }
        List<CompanionViews.OfferRow> rows = rows(slaves);
        if (rows.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(slaves ? KEY + "empty_slaves" : KEY + "empty_free"), x, listTop(top) + 5, right - x, 2,
                    0xFFB8B8B8);
            return;
        }
        int visible = visibleRows(top, bottom);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            CompanionViews.OfferRow row = rows.get(scroll + i);
            int y = listTop(top) + i * ROW;
            int textRight = right - buyWidth - 4;
            Component level = Component.translatable(KEY + "level", row.level());
            screen.label(graphics, level, x, y + 5, 0xFFF0D070, x + badgeWidth);
            int textX = x + badgeWidth;
            Component price = row.slave() ? money(row.price()) : Component.translatable(KEY + "per_day", money(row.price()));
            Component power = Component.translatable(KEY + "power", row.power());
            int priceWidth = Math.min(font.width(price), Math.max(0, (textRight - textX) / 3));
            int powerWidth = Math.min(font.width(power), Math.max(0, (textRight - textX) / 4));
            int nameRight = textRight - priceWidth - powerWidth - 12;
            String name = CitizenTexts.name(row.seed(), row.gender()).getString();
            screen.name(graphics, name, textX, y + 5, 0xFFFFFFFF, Math.max(0, nameRight - textX));
            screen.hoverText(textX, y + 5, Math.max(0, nameRight - textX), details(row));
            screen.name(graphics, power.getString(), textRight - priceWidth - powerWidth - 6, y + 5, 0xFFB0E0B0, powerWidth);
            screen.name(graphics, price.getString(), textRight - priceWidth, y + 5, 0xFFF0D070, priceWidth);
        }
        if (rows.size() > visible) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows.size(), scroll + visible), rows.size());
            screen.label(graphics, page, right - font.width(page), listTop(top) + visible * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    Component hint(boolean slaves) {
        if (view == null) {
            return Component.empty();
        }
        return Component.translatable(slaves ? KEY + "hint.slaves" : KEY + "hint.free");
    }

    /**
     * Shows a view with a way of paying; for the layout check.
     */
    void showForCheck(CompanionViews.MarketView value, boolean payWithCoins) {
        view = value;
        coins = payWithCoins;
    }
}
