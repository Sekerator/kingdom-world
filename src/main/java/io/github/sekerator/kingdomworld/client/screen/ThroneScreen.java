package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CatalogView;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.OrdersView;
import io.github.sekerator.kingdomworld.common.network.packet.OrderPackets;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import io.github.sekerator.kingdomworld.common.network.packet.StoragePackets;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The throne screen: overview of a kingdom, its members and applications, the rights of positions, its citizens,
 * and the way to the territory map. Every button sends an intent; the server decides and answers with a result and a
 * refreshed view. Widths of tabs, buttons, columns and the panel come from the translated texts.
 */
public class ThroneScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 246;
    /** The citizen list uses more height where the screen has it. */
    private static final int MAX_CITIZENS_PANEL_HEIGHT = 330;
    private static final int ROW_HEIGHT = 20;
    private static final int RIGHTS_ROW = 16;
    private static final int TAB_TOP = 20;
    private static final int TAB_HEIGHT = 18;
    static final int CONTENT_TOP = 44;
    private static final int STATUS_LINES = 3;
    static final int BOTTOM_AREA = 26 + 4 + STATUS_LINES * LINE;
    private static final int MIN_NAME_COLUMN = 64;
    private static final int GAP = 2;
    private static final List<Permission> EDITABLE_RIGHTS = List.of(Permission.BUILD, Permission.CONTAINERS,
            Permission.INTERACT, Permission.RECRUIT, Permission.TERRITORY, Permission.MANAGE_CITIZENS, Permission.MANAGE_PRODUCTION,
            Permission.MANAGE_TREASURY, Permission.MANAGE_MARKET, Permission.MANAGE_TRADE, Permission.MANAGE_GUILD);

    private enum Tab {
        OVERVIEW("kingdomworld.screen.throne.tab.overview"),
        MEMBERS("kingdomworld.screen.throne.tab.members"),
        CITIZENS("kingdomworld.screen.throne.tab.citizens"),
        STORAGE("kingdomworld.screen.throne.tab.storage"),
        ORDERS("kingdomworld.screen.throne.tab.orders"),
        TREASURY("kingdomworld.screen.throne.tab.treasury"),
        MARKET("kingdomworld.screen.throne.tab.market"),
        TRADE("kingdomworld.screen.throne.tab.trade"),
        GUILD("kingdomworld.screen.throne.tab.guild"),
        RIGHTS("kingdomworld.screen.throne.tab.rights");

        /**
         * Tabs with long lists that take the height the screen has.
         */
        boolean tall() {
            return this == CITIZENS || this == STORAGE || this == ORDERS || this == TREASURY || this == MARKET || this == TRADE
                    || this == GUILD;
        }

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private record Row(KingdomView.MemberEntry member, KingdomView.PendingEntry application, Component header) {
    }

    private KingdomView view;
    private final CitizensTab citizens = new CitizensTab(this);
    private final StorageTab storage = new StorageTab(this);
    private final OrdersTab orders = new OrdersTab(this);
    private final TreasuryTab treasury = new TreasuryTab(this);
    private final MarketTab market = new MarketTab(this);
    private final TradeTab trade = new TradeTab(this);
    private final GuildTab guild = new GuildTab(this);
    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private Component status = Component.empty();
    private int statusColor = 0xFFA0E0A0;
    private boolean closeOnSuccess;

    private int panelWidth;
    private int panelHeight;
    private int roleWidth;
    private int kickWidth;
    private int crownWidth;
    private int approveWidth;
    private int rejectWidth;
    private int doneWidth;
    private int rightsLabelWidth;
    private int rightsCellWidth;
    /** Whether the tabs are drawn narrower, because the row of usual ones does not fit the screen. */
    private boolean compactTabs;
    /** The tab each row of tabs starts with; a second row when even narrow tabs do not fit one. */
    private int secondRowStart = Integer.MAX_VALUE;
    /** How much lower the content starts because of a second row of tabs. */
    private int contentOffset;
    /** The first column of rights shown, when not all of them fit. */
    private int rightsColumn;
    private int rightsVisible;
    private int arrowWidth;

    public ThroneScreen(KingdomView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    @Override
    public String checkName() {
        String name = switch (tab) {
            case CITIZENS -> citizens.checkName();
            case STORAGE -> storage.checkName();
            case ORDERS -> orders.checkName();
            case TREASURY -> treasury.checkName();
            case MARKET -> market.checkName();
            case TRADE -> trade.checkName();
            case GUILD -> guild.checkName();
            default -> tab.name().toLowerCase(Locale.ROOT);
        };
        return "throne/" + name + "/" + view.relation().name().toLowerCase(Locale.ROOT);
    }

    public UUID kingdomId() {
        return view.id();
    }

    public KingdomView view() {
        return view;
    }

    public void update(KingdomView newView) {
        this.view = newView;
        if (newView.relation() != KingdomView.Relation.MEMBER) {
            tab = Tab.OVERVIEW;
        }
        rebuildWidgets();
    }

    /**
     * Shows the citizens the server sent, if they belong to the kingdom on screen.
     */
    public void updateCitizens(CitizenView citizenView) {
        if (!citizenView.kingdomId().equals(view.id())) {
            return;
        }
        citizens.setView(citizenView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows what the storehouses hold, if it belongs to the kingdom on screen.
     */
    public void updateStorage(StorageView storageView) {
        if (!storageView.kingdomId().equals(view.id())) {
            return;
        }
        storage.setView(storageView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the orders the server sent, if they belong to the kingdom on screen.
     */
    public void updateOrders(OrdersView ordersView) {
        if (!ordersView.kingdomId().equals(view.id())) {
            return;
        }
        orders.setView(ordersView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the treasury the server sent, if it belongs to the kingdom on screen.
     */
    public void updateTreasury(io.github.sekerator.kingdomworld.common.network.data.TreasuryView treasuryView) {
        if (!treasuryView.kingdomId().equals(view.id())) {
            return;
        }
        treasury.setView(treasuryView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the treasury tab got its view, for the self-check.
     */
    public boolean treasuryShown() {
        return treasury.view() != null;
    }

    /**
     * Shows a view of the treasury tab ({@code overview}, {@code journal}, {@code settings}, {@code debtors}) and a period
     * of the journal ({@code today}, {@code yesterday}, {@code week}); for the layout check.
     */
    public void showTreasury(String mode, String period) {
        treasury.setMode(TreasuryTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
        treasury.setPeriod(io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.valueOf(period.toUpperCase(Locale.ROOT)));
    }

    /**
     * Shows the market the server sent, if it belongs to the kingdom on screen.
     */
    public void updateMarket(io.github.sekerator.kingdomworld.common.network.data.TradeViews.MarketView marketView) {
        if (!marketView.kingdomId().equals(view.id())) {
            return;
        }
        market.setView(marketView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the deals the server sent, if they belong to the kingdom on screen.
     */
    public void updateTrade(io.github.sekerator.kingdomworld.common.network.data.TradeViews.TradeView tradeView) {
        if (!tradeView.kingdomId().equals(view.id())) {
            return;
        }
        trade.setView(tradeView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the guild tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateGuild(io.github.sekerator.kingdomworld.common.network.data.GuildViews.GuildTabView guildView) {
        if (!guildView.kingdomId().equals(view.id())) {
            return;
        }
        guild.setView(guildView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the guild tab got its view, for the self-check.
     */
    public boolean guildShown() {
        return guild.view() != null;
    }

    /**
     * Shows a view of the guild tab ({@code branch}, {@code adventurers}, {@code squads}, {@code contracts}), with adventurers
     * chosen or a squad's expedition being chosen; for the layout check.
     */
    public void showGuild(String mode, int chosen, long sending) {
        guild.showForCheck(GuildTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), chosen, sending);
    }

    /**
     * Whether the market and trade tabs got their views, for the self-check.
     */
    public boolean marketShown() {
        return market.view() != null;
    }

    public boolean tradeShown() {
        return trade.view() != null;
    }

    /**
     * Shows a view of the market tab ({@code sell}, {@code buy}, {@code lots}, {@code export}, {@code import}, {@code settings}),
     * with its form or the search of items; for the layout check.
     */
    public void showMarket(String mode, boolean form, ItemStack item, String price, String first, String second, String search) {
        market.showForCheck(MarketTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), form, item, price, first, second, search);
    }

    /**
     * Shows a view of the trade tab ({@code deals}, {@code caravans}, {@code propose}); for the layout check.
     */
    public void showTrade(String mode, ItemStack item, String count, String price, String period, String search) {
        trade.showForCheck(TradeTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), item, count, price, period, search);
    }

    void focus(GuiEventListener widget) {
        setInitialFocus(widget);
    }

    String fitName(String name, int maxWidth) {
        return fit(name, maxWidth);
    }

    /**
     * Fills the forms of the payments in the treasury tab, for the layout check.
     */
    public void fillPaymentsForCheck(String payee, String amount, String coins) {
        treasury.fillForCheck(payee, amount, coins);
    }

    /**
     * Shows a page of the items that can be ordered.
     */
    public void updateCatalog(CatalogView catalogView) {
        if (!catalogView.kingdomId().equals(view.id())) {
            return;
        }
        orders.setCatalog(catalogView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Orders the orders tab shows, or -1 while the server has not answered; for the self-check.
     */
    public int ordersShown() {
        return orders.view() == null ? -1 : orders.view().orders().size();
    }

    /**
     * Shows a mode of the orders tab ({@code list}, {@code create}, {@code edit}); for the layout check.
     */
    public void showOrders(String mode, String search, ItemStack chosen, String profession) {
        orders.showForCheck(OrdersTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), search, chosen, profession);
    }

    /**
     * Shows a page of the catalog without asking the server; for the layout check.
     */
    public void showCatalog(CatalogView catalogView) {
        orders.setCatalog(catalogView);
    }

    int panelTop() {
        return top();
    }

    int panelHeight() {
        return panelHeight;
    }

    /**
     * Kinds of items the storehouses tab shows, or -1 while the server has not answered; for the self-check.
     */
    public int storageShown() {
        return storage.view() == null ? -1 : storage.view().items().size();
    }

    /**
     * Shows the list of storehouses instead of the items, and a search; for the layout check.
     */
    public void showStorehouses(boolean storehouses, String search) {
        storage.showStorehouses(storehouses);
        storage.setSearch(search);
    }

    Font font() {
        return font;
    }

    /**
     * Number of citizens the citizens tab shows, or -1 while the server has not answered; for the self-check.
     */
    public int citizensShown() {
        return citizens.view() == null ? -1 : citizens.view().citizens().size();
    }

    /**
     * Shows the chronicle instead of the citizen list, or the other way round; for the layout check.
     */
    public void showChronicle(boolean chronicle) {
        citizens.setChronicle(chronicle);
    }

    /**
     * Shows a view of the citizens tab by name ({@code citizens}, {@code work}, {@code production},
     * {@code chronicle}); for the layout check.
     */
    public void showCitizens(String mode) {
        citizens.setMode(CitizensTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
    }

    private void requestCitizens() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CitizenRequestPacket(view.id()));
        }
    }

    <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        return addRenderableWidget(widget);
    }

    void rebuild() {
        rebuildWidgets();
    }

    /**
     * Switches to a tab by name ({@code overview}, {@code members}, {@code citizens}, {@code storage}, {@code rights}); unknown names
     * are ignored.
     */
    public void selectTab(String name) {
        for (Tab candidate : Tab.values()) {
            if (candidate.name().equalsIgnoreCase(name) && (candidate == Tab.OVERVIEW || isMember())) {
                openTab(candidate);
            }
        }
    }

    private void openTab(Tab target) {
        tab = target;
        scroll = 0;
        if (target == Tab.CITIZENS) {
            requestCitizens();
        }
        if (target == Tab.STORAGE && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new StoragePackets.Request(view.id()));
        }
        if (target == Tab.ORDERS && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new OrderPackets.Request(view.id()));
        }
        if (target == Tab.TREASURY && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets.TreasuryRequest(view.id()));
        }
        if (target == Tab.MARKET && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.TradePackets.MarketRequest(view.id()));
        }
        if (target == Tab.TRADE && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.TradePackets.TradeRequest(view.id()));
        }
        if (target == Tab.GUILD && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.GuildPackets.TabRequest(view.id()));
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private boolean isMember() {
        return view.relation() == KingdomView.Relation.MEMBER;
    }

    private void send(KingdomActionPacket.Action action, UUID target, int value) {
        KingdomWorldNetwork.sendToServer(new KingdomActionPacket(action, target, value));
    }

    private void confirm(Component question, Runnable onYes) {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                onYes.run();
            }
            minecraft.setScreen(this);
        }, Component.literal(fit(view.name(), 300)), question));
    }

    private static Component roleName(Role role) {
        return Component.translatable("kingdomworld.role." + role.id());
    }

    private static Component shortName(Permission permission) {
        return Component.translatable("kingdomworld.permission." + permission.id() + ".short");
    }

    // ---------------------------------------------------------------- layout

    private List<Component> overviewLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("kingdomworld.screen.throne.ruler", PlayerNames.display(view.rulerName())));
        lines.add(Component.translatable("kingdomworld.screen.throne.members", view.memberCount()));
        lines.add(Component.translatable("kingdomworld.screen.throne.population", view.population()));
        lines.add(Component.translatable("kingdomworld.screen.throne.chunks", view.chunks(), view.chunkLimit()));
        if (view.capital() != null) {
            lines.add(Component.translatable("kingdomworld.screen.throne.capital",
                    view.capital().getX(), view.capital().getY(), view.capital().getZ()));
        }
        return lines;
    }

    private Component relationText() {
        return switch (view.relation()) {
            case MEMBER -> Component.translatable("kingdomworld.screen.throne.your_role", roleName(view.viewerRole()));
            case APPLIED -> Component.translatable("kingdomworld.screen.throne.applied");
            case INVITED -> Component.translatable("kingdomworld.screen.throne.invited");
            case OTHER_KINGDOM -> Component.translatable("kingdomworld.screen.throne.other_kingdom");
            case OUTSIDER -> Component.translatable("kingdomworld.screen.throne.outsider");
        };
    }

    private List<Component> overviewActions() {
        return switch (view.relation()) {
            case OUTSIDER -> List.of(Component.translatable("kingdomworld.screen.throne.apply"));
            case INVITED -> List.of(Component.translatable("kingdomworld.screen.throne.accept_invitation"),
                    Component.translatable("kingdomworld.screen.throne.decline_invitation"));
            case MEMBER -> List.of(view.viewerRole() == Role.KING
                    ? Component.translatable("kingdomworld.screen.throne.dissolve")
                    : Component.translatable("kingdomworld.screen.throne.leave"));
            default -> List.of();
        };
    }

    private List<Component> tabLabels() {
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable(Tab.OVERVIEW.key));
        if (isMember()) {
            labels.add(Component.translatable(Tab.MEMBERS.key));
            labels.add(Component.translatable(Tab.CITIZENS.key));
            labels.add(Component.translatable(Tab.STORAGE.key));
            labels.add(Component.translatable(Tab.ORDERS.key));
            labels.add(Component.translatable(Tab.TREASURY.key));
            labels.add(Component.translatable(Tab.MARKET.key));
            labels.add(Component.translatable(Tab.TRADE.key));
            labels.add(Component.translatable(Tab.GUILD.key));
            labels.add(Component.translatable(Tab.RIGHTS.key));
        }
        return labels;
    }

    private void measure() {
        roleWidth = 0;
        for (Role role : Role.values()) {
            roleWidth = Math.max(roleWidth, buttonWidth(roleName(role)));
        }
        kickWidth = buttonWidth(Component.translatable("kingdomworld.screen.throne.kick"));
        crownWidth = buttonWidth(Component.translatable("kingdomworld.screen.throne.crown"));
        approveWidth = buttonWidth(Component.translatable("kingdomworld.screen.throne.approve"));
        rejectWidth = buttonWidth(Component.translatable("kingdomworld.screen.throne.reject"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        rightsLabelWidth = 0;
        rightsCellWidth = 24;
        for (Role role : Role.values()) {
            if (role.isAssignable()) {
                rightsLabelWidth = Math.max(rightsLabelWidth, font.width(roleName(role)) + PAD);
            }
        }
        for (Permission permission : EDITABLE_RIGHTS) {
            rightsCellWidth = Math.max(rightsCellWidth, font.width(shortName(permission)) + 6);
        }

        int tabs = 0;
        int compact = 0;
        List<Component> labels = tabLabels();
        for (Component label : labels) {
            tabs += buttonWidth(label) + GAP;
            compact += compactTabWidth(label) + GAP;
        }
        compactTabs = tabs + 2 * PAD > width - 8;
        secondRowStart = Integer.MAX_VALUE;
        if (compactTabs) {
            tabs = compact;
            if (compact + 2 * PAD > width - 8) {
                // Even narrow tabs do not fit one row: the rest go to a second row.
                int room = width - 8 - 2 * PAD;
                int used = 0;
                int widest = 0;
                for (int i = 0; i < labels.size(); i++) {
                    int next = compactTabWidth(labels.get(i)) + GAP;
                    if (used + next > room && secondRowStart == Integer.MAX_VALUE) {
                        secondRowStart = i;
                        widest = used;
                        used = 0;
                    }
                    used += next;
                }
                tabs = Math.max(widest, used);
            }
        }
        contentOffset = secondRowStart == Integer.MAX_VALUE ? 0 : TAB_HEIGHT + 1;
        int members = MIN_NAME_COLUMN + 12 + roleWidth + kickWidth + crownWidth + 2 * GAP;
        int applications = MIN_NAME_COLUMN + 12 + approveWidth + rejectWidth + GAP;
        arrowWidth = buttonWidth(Component.literal("<"), Component.literal(">"));
        // The rights show as many columns as fit; the rest are reached with arrows.
        int rights = rightsLabelWidth + 4 * (rightsCellWidth + GAP) + 2 * (arrowWidth + GAP);
        int overview = font.width(relationText());
        for (Component line : overviewLines()) {
            overview = Math.max(overview, font.width(line));
        }
        int bottom = doneWidth + PAD;
        for (Component action : overviewActions()) {
            bottom += buttonWidth(action) + GAP;
        }
        if (isMember()) {
            bottom += buttonWidth(Component.translatable("kingdomworld.screen.throne.tab.territory")) + GAP;
        }
        int inner = Math.max(Math.max(tabs, members), Math.max(Math.max(applications, rights), Math.max(overview + 4, bottom)));
        if (isMember()) {
            inner = Math.max(inner, Math.max(Math.max(citizens.measure(font), storage.measure(font)),
                    Math.max(orders.measure(font), treasury.measure(font))));
            inner = Math.max(inner, Math.max(market.measure(font), trade.measure(font)));
            inner = Math.max(inner, guild.measure(font));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(tab.tall() ? MAX_CITIZENS_PANEL_HEIGHT : MAX_PANEL_HEIGHT, height - 8);
        int available = panelWidth - 2 * PAD - rightsLabelWidth;
        rightsVisible = EDITABLE_RIGHTS.size();
        if (rightsVisible * (rightsCellWidth + GAP) > available) {
            rightsVisible = Math.max(1, (available - 2 * (arrowWidth + GAP)) / (rightsCellWidth + GAP));
        }
        rightsColumn = Math.max(0, Math.min(rightsColumn, EDITABLE_RIGHTS.size() - rightsVisible));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int top = top();
        int x = left + PAD;
        int y = top + TAB_TOP;
        List<Tab> shown = new ArrayList<>(List.of(Tab.OVERVIEW));
        if (isMember()) {
            shown.addAll(List.of(Tab.MEMBERS, Tab.CITIZENS, Tab.STORAGE, Tab.ORDERS, Tab.TREASURY, Tab.MARKET, Tab.TRADE, Tab.GUILD, Tab.RIGHTS));
        }
        for (int i = 0; i < shown.size(); i++) {
            if (i == secondRowStart) {
                x = left + PAD;
                y += TAB_HEIGHT + 1;
            }
            x = tabButton(shown.get(i), x, y);
        }
        int inner = top + contentOffset;
        int innerHeight = panelHeight - contentOffset;
        switch (tab) {
            case OVERVIEW -> initOverview(left, top);
            case MEMBERS -> initMembers(inner);
            case CITIZENS -> citizens.init(left, inner, panelWidth, innerHeight);
            case STORAGE -> storage.init(left, inner, panelWidth, innerHeight);
            case ORDERS -> orders.init(left, inner, panelWidth, innerHeight);
            case TREASURY -> treasury.init(left, inner, panelWidth, innerHeight);
            case MARKET -> market.init(left, inner, panelWidth, innerHeight);
            case TRADE -> trade.init(left, inner, panelWidth, innerHeight);
            case GUILD -> guild.init(left, inner, panelWidth, innerHeight);
            case RIGHTS -> initRights(inner);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top + panelHeight - 26, doneWidth, 20).build());
    }

    /**
     * A tab just wide enough for its text, for screens where the usual tabs do not fit in one row.
     */
    private int compactTabWidth(Component label) {
        return font.width(label) + LayoutCheck.BUTTON_TEXT_MARGIN + 2;
    }

    private int tabWidth(Component label) {
        return compactTabs ? compactTabWidth(label) : buttonWidth(label);
    }

    private int tabButton(Tab target, int x, int y) {
        Component label = Component.translatable(target.key);
        int buttonWidth = tabWidth(label);
        Button button = addRenderableWidget(Button.builder(label, b -> openTab(target))
                .bounds(x, y, buttonWidth, TAB_HEIGHT).build());
        button.active = tab != target;
        return x + buttonWidth + GAP;
    }

    private void initOverview(int left, int top) {
        int y = top + panelHeight - 26;
        int x = left + PAD;
        List<Component> actions = overviewActions();
        switch (view.relation()) {
            case OUTSIDER -> addRenderableWidget(Button.builder(actions.get(0),
                    button -> send(KingdomActionPacket.Action.APPLY, view.id(), 0)).bounds(x, y, buttonWidth(actions.get(0)), 20).build());
            case INVITED -> {
                int acceptWidth = buttonWidth(actions.get(0));
                addRenderableWidget(Button.builder(actions.get(0),
                        button -> send(KingdomActionPacket.Action.ACCEPT_INVITATION, view.id(), 0)).bounds(x, y, acceptWidth, 20).build());
                addRenderableWidget(Button.builder(actions.get(1),
                                button -> send(KingdomActionPacket.Action.DECLINE_INVITATION, view.id(), 0))
                        .bounds(x + acceptWidth + GAP, y, buttonWidth(actions.get(1)), 20).build());
            }
            case MEMBER -> {
                Component label = actions.get(0);
                // The map of the territory opens from the overview, next to leaving or dissolving.
                Component territory = Component.translatable("kingdomworld.screen.throne.tab.territory");
                addRenderableWidget(Button.builder(territory, button -> ClientHooks.openTerritoryEditor(view))
                        .bounds(x + buttonWidth(label) + GAP, y, buttonWidth(territory), 20).build());
                if (view.viewerRole() == Role.KING) {
                    addRenderableWidget(Button.builder(label.copy().withStyle(ChatFormatting.RED),
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.dissolve.confirm", fit(view.name(), 200)), () -> {
                                closeOnSuccess = true;
                                send(KingdomActionPacket.Action.DISSOLVE, null, 0);
                            })).bounds(x, y, buttonWidth(label), 20).build());
                } else {
                    addRenderableWidget(Button.builder(label,
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.leave.confirm", fit(view.name(), 200)),
                                    () -> send(KingdomActionPacket.Action.LEAVE, null, 0))).bounds(x, y, buttonWidth(label), 20).build());
                }
            }
            default -> {
            }
        }
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (KingdomView.MemberEntry member : view.members()) {
            rows.add(new Row(member, null, null));
        }
        if (view.can(Permission.RECRUIT)) {
            rows.add(new Row(null, null, Component.translatable("kingdomworld.screen.throne.applications", view.applications().size())));
            for (KingdomView.PendingEntry application : view.applications()) {
                rows.add(new Row(null, application, null));
            }
        }
        return rows;
    }

    /**
     * Rows that fit the list; one row less when a page counter is needed.
     */
    private int visibleRows() {
        int fit = Math.max(1, (panelHeight - contentOffset - CONTENT_TOP - BOTTOM_AREA) / ROW_HEIGHT);
        return rows().size() > fit ? Math.max(1, fit - 1) : fit;
    }

    private int listRight() {
        return left() + panelWidth - PAD;
    }

    private int crownX() {
        return listRight() - crownWidth;
    }

    private int kickX() {
        return crownX() - GAP - kickWidth;
    }

    private int roleX() {
        return kickX() - GAP - roleWidth;
    }

    private void initMembers(int top) {
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean king = view.viewerRole() == Role.KING;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = top + CONTENT_TOP + i * ROW_HEIGHT;
            if (row.member() != null) {
                KingdomView.MemberEntry member = row.member();
                boolean other = !member.id().equals(self) && member.role() != Role.KING;
                if (view.can(Permission.APPOINT) && other) {
                    addRenderableWidget(Button.builder(roleName(member.role()), button ->
                                    send(KingdomActionPacket.Action.SET_ROLE, member.id(), member.role().nextAssignable().ordinal()))
                            .bounds(roleX(), y, roleWidth, 18).build());
                }
                if (view.can(Permission.RECRUIT) && other && (king || member.role() == Role.SUBJECT)) {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.kick"), button ->
                                    confirm(Component.translatable("kingdomworld.screen.throne.kick.confirm", member.name()),
                                            () -> send(KingdomActionPacket.Action.KICK, member.id(), 0)))
                            .bounds(kickX(), y, kickWidth, 18).build());
                }
                if (king && other) {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.crown"), button ->
                                    confirm(Component.translatable("kingdomworld.screen.throne.crown.confirm", member.name()),
                                            () -> send(KingdomActionPacket.Action.ABDICATE, member.id(), 0)))
                            .bounds(crownX(), y, crownWidth, 18).build());
                }
            } else if (row.application() != null) {
                KingdomView.PendingEntry application = row.application();
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.approve"), button ->
                                send(KingdomActionPacket.Action.APPROVE, application.id(), 0))
                        .bounds(listRight() - rejectWidth - GAP - approveWidth, y, approveWidth, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.reject"), button ->
                                send(KingdomActionPacket.Action.REJECT, application.id(), 0))
                        .bounds(listRight() - rejectWidth, y, rejectWidth, 18).build());
            }
        }
    }

    private int rightsLeft() {
        return left() + PAD + rightsLabelWidth;
    }

    private boolean rightsPaged() {
        return rightsVisible < EDITABLE_RIGHTS.size();
    }

    private int rightsCellsLeft() {
        return rightsLeft() + (rightsPaged() ? arrowWidth + GAP : 0);
    }

    /**
     * Height of a row of the rights table: smaller on low screens, so that every position fits above the bottom line.
     */
    private int rightsRow() {
        int roles = 0;
        for (Role role : Role.values()) {
            if (role.isAssignable()) {
                roles++;
            }
        }
        int room = panelHeight - contentOffset - CONTENT_TOP - 12 - BOTTOM_AREA;
        return Math.max(12, Math.min(RIGHTS_ROW, room / Math.max(1, roles)));
    }

    private void initRights(int top) {
        if (rightsPaged()) {
            int arrowY = top + CONTENT_TOP - 2;
            Button back = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                rightsColumn = Math.max(0, rightsColumn - rightsVisible);
                rebuildWidgets();
            }).bounds(rightsLeft(), arrowY, arrowWidth, 12).build());
            back.active = rightsColumn > 0;
            Button forward = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                rightsColumn = Math.min(EDITABLE_RIGHTS.size() - rightsVisible, rightsColumn + rightsVisible);
                rebuildWidgets();
            }).bounds(rightsCellsLeft() + rightsVisible * (rightsCellWidth + GAP), arrowY, arrowWidth, 12).build());
            forward.active = rightsColumn + rightsVisible < EDITABLE_RIGHTS.size();
        }
        int row = 0;
        for (Role role : Role.values()) {
            if (!role.isAssignable()) {
                continue;
            }
            int y = top + CONTENT_TOP + 12 + row * rightsRow();
            EnumSet<Permission> granted = view.rights().getOrDefault(role, EnumSet.noneOf(Permission.class));
            for (int shown = 0; shown < rightsVisible && rightsColumn + shown < EDITABLE_RIGHTS.size(); shown++) {
                Permission permission = EDITABLE_RIGHTS.get(rightsColumn + shown);
                boolean has = granted.contains(permission);
                Button button = addRenderableWidget(Button.builder(
                                Component.translatable(has ? "kingdomworld.screen.throne.right_on" : "kingdomworld.screen.throne.right_off")
                                        .withStyle(has ? ChatFormatting.GREEN : ChatFormatting.RED),
                                b -> KingdomWorldNetwork.sendToServer(KingdomActionPacket.setPermission(role, permission, !has)))
                        .bounds(rightsCellsLeft() + shown * (rightsCellWidth + GAP), y, rightsCellWidth, rightsRow() - 1).build());
                button.active = view.can(Permission.APPOINT);
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.permission." + permission.id())));
            }
            row++;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.CITIZENS) {
            if (citizens.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.STORAGE) {
            if (storage.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.ORDERS) {
            if (orders.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.TREASURY) {
            if (treasury.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.MARKET) {
            if (market.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.TRADE) {
            if (trade.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.GUILD) {
            if (guild.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.MEMBERS) {
            int max = Math.max(0, rows().size() - visibleRows());
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onResult(boolean success, Component message) {
        if (success && closeOnSuccess && minecraft != null) {
            onClose();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
            return;
        }
        closeOnSuccess = false;
        status = message;
        // A message with a color of its own (news from the server, such as a proposal) keeps it; otherwise green or red.
        net.minecraft.network.chat.TextColor own = message.getStyle().getColor();
        statusColor = own != null ? 0xFF000000 | own.getValue() : success ? 0xFFA0E0A0 : 0xFFFF8080;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        graphics.fill(left, top, right, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.color());
        centeredName(graphics, view.name(), width / 2, top + 7, 0xFF000000 | FoundKingdomScreen.readable(view.color()), panelWidth - 2 * PAD);
        int inner = top + contentOffset;
        int innerHeight = panelHeight - contentOffset;
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics, left, inner, right);
            case MEMBERS -> renderMembers(graphics, left, inner);
            case CITIZENS -> citizens.render(graphics, font, left, inner, panelWidth, innerHeight);
            case STORAGE -> storage.render(graphics, font, left, inner, panelWidth, innerHeight);
            case ORDERS -> orders.render(graphics, font, left, inner, panelWidth, innerHeight);
            case TREASURY -> treasury.render(graphics, font, left, inner, panelWidth, innerHeight);
            case MARKET -> market.render(graphics, font, left, inner, panelWidth, innerHeight);
            case TRADE -> trade.render(graphics, font, left, inner, panelWidth, innerHeight);
            case GUILD -> guild.render(graphics, font, left, inner, panelWidth, innerHeight);
            case RIGHTS -> renderRights(graphics, left, inner);
        }
        Component bottomText = status;
        int color = statusColor;
        if (bottomText.getString().isEmpty() && tab == Tab.RIGHTS && !view.can(Permission.APPOINT)) {
            bottomText = Component.translatable("kingdomworld.screen.throne.rights_read_only");
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.CITIZENS) {
            bottomText = citizens.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.TREASURY) {
            bottomText = treasury.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.MARKET) {
            bottomText = market.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.TRADE) {
            bottomText = trade.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.GUILD) {
            bottomText = guild.hint();
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottomText, left + PAD, top + panelHeight - BOTTOM_AREA + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private void renderOverview(GuiGraphics graphics, int left, int top, int right) {
        int x = left + PAD + 4;
        int y = top + CONTENT_TOP + 4;
        for (Component line : overviewLines()) {
            label(graphics, line, x, y, 0xFFE8E8E8, right - PAD);
            y += 14;
        }
        label(graphics, relationText(), x, y + 6, 0xFFF0D070, right - PAD);
    }

    private void renderMembers(GuiGraphics graphics, int left, int top) {
        List<Row> rows = rows();
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        int nameLeft = left + PAD + 9;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = top + CONTENT_TOP + i * ROW_HEIGHT + 5;
            if (row.member() != null) {
                KingdomView.MemberEntry member = row.member();
                graphics.fill(left + PAD, y + 1, left + PAD + 5, y + 6, member.online() ? 0xFF50D050 : 0xFF808080);
                int drawn = name(graphics, member.name(), nameLeft, y, member.online() ? 0xFFFFFFFF : 0xFFB8B8B8, roleX() - 4 - nameLeft);
                hoverText(nameLeft, y, drawn, member.guildRank() < 0 ? Component.translatable("kingdomworld.screen.throne.no_guild_rank")
                        : Component.translatable("kingdomworld.screen.throne.guild_rank",
                        io.github.sekerator.kingdomworld.core.guild.Rank.ofIndex(member.guildRank()).label()));
                boolean hasRoleButton = view.can(Permission.APPOINT) && member.role() != Role.KING && !member.id().equals(self);
                if (!hasRoleButton) {
                    label(graphics, roleName(member.role()), roleX() + 4, y, member.role() == Role.KING ? 0xFFF0D070 : 0xFFD0D0D0,
                            listRight());
                }
            } else if (row.application() != null) {
                name(graphics, row.application().name(), nameLeft, y, 0xFFF0F0B0,
                        listRight() - rejectWidth - GAP - approveWidth - 4 - nameLeft);
            } else if (row.header() != null) {
                label(graphics, row.header(), left + PAD, y, 0xFFF0D070, listRight());
            }
        }
        if (rows.size() > visibleRows()) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1,
                    Math.min(rows.size(), scroll + visibleRows()), rows.size());
            label(graphics, page, listRight() - font.width(page), top + CONTENT_TOP + visibleRows() * ROW_HEIGHT + 5, 0xFF9A9A9A,
                    listRight());
        }
    }

    private void renderRights(GuiGraphics graphics, int left, int top) {
        for (int shown = 0; shown < rightsVisible && rightsColumn + shown < EDITABLE_RIGHTS.size(); shown++) {
            int cellLeft = rightsCellsLeft() + shown * (rightsCellWidth + GAP);
            Permission permission = EDITABLE_RIGHTS.get(rightsColumn + shown);
            Component header = shortName(permission);
            centeredLabel(graphics, header, cellLeft + rightsCellWidth / 2, top + CONTENT_TOP, 0xFFF0D070, cellLeft, cellLeft + rightsCellWidth);
            hoverText(cellLeft, top + CONTENT_TOP, rightsCellWidth, Component.translatable("kingdomworld.permission." + permission.id()));
        }
        int row = 0;
        for (Role role : Role.values()) {
            if (!role.isAssignable()) {
                continue;
            }
            label(graphics, roleName(role), left + PAD, top + CONTENT_TOP + 12 + (rightsRow() - 8) / 2 + row * rightsRow(), 0xFFE8E8E8,
                    rightsLeft() - 2);
            row++;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.ORDERS) {
            orders.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return tab == Tab.ORDERS && button == 0 && orders.click(left(), top() + contentOffset, panelWidth, panelHeight - contentOffset, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
