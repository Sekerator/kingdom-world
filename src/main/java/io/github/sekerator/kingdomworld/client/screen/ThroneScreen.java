package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
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
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
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
    /** The gap between narrow tabs, which have to fit in two rows on the smallest screens. */
    private static final int TAB_GAP = 1;
    /** The least room beside the picture of a style for the current style and what the shown one is like. */
    private static final int STYLE_TEXT = 140;
    private static final int STYLE_BUTTON_HEIGHT = 14;
    private static final List<Permission> EDITABLE_RIGHTS = List.of(Permission.BUILD, Permission.CONTAINERS,
            Permission.INTERACT, Permission.RECRUIT, Permission.TERRITORY, Permission.MANAGE_CITIZENS, Permission.MANAGE_PRODUCTION,
            Permission.MANAGE_TREASURY, Permission.MANAGE_MARKET, Permission.MANAGE_TRADE, Permission.MANAGE_GUILD,
            Permission.MANAGE_CONSTRUCTION, Permission.MANAGE_ARMY, Permission.MANAGE_MAGIC, Permission.MANAGE_LAW,
            Permission.MANAGE_DIPLOMACY, Permission.DECLARE_WAR, Permission.MANAGE_NOBILITY, Permission.MANAGE_SPIES);

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
        CONSTRUCTION("kingdomworld.screen.throne.tab.construction"),
        ARMY("kingdomworld.screen.throne.tab.army"),
        MAGIC("kingdomworld.screen.throne.tab.magic"),
        ROADS("kingdomworld.screen.throne.tab.roads"),
        LAW("kingdomworld.screen.throne.tab.law"),
        DIPLOMACY("kingdomworld.screen.throne.tab.diplomacy"),
        NOBILITY("kingdomworld.screen.throne.tab.nobility"),
        RIGHTS("kingdomworld.screen.throne.tab.rights");

        /**
         * Tabs with long lists that take the height the screen has.
         */
        boolean tall() {
            return this == CITIZENS || this == STORAGE || this == ORDERS || this == TREASURY || this == MARKET || this == TRADE
                    || this == GUILD || this == CONSTRUCTION || this == ARMY || this == MAGIC || this == ROADS || this == LAW
                    || this == DIPLOMACY || this == NOBILITY;
        }

        private final String key;

        /**
         * Whether the tab has a short label for the smallest screens, where even narrow tabs do not fit two rows.
         */
        boolean hasShort() {
            return this == OVERVIEW || this == MEMBERS || this == STORAGE || this == TRADE || this == CONSTRUCTION || this == DIPLOMACY;
        }

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
    private final ConstructionTab construction = new ConstructionTab(this);
    private final GuildTab guild = new GuildTab(this);
    private final ArmyTab army = new ArmyTab(this);
    private final MagicTab magic = new MagicTab(this);
    private final RoadsTab roads = new RoadsTab(this);
    private final LawTab law = new LawTab(this);
    private final DiplomacyTab diplomacy = new DiplomacyTab(this);
    private final NobilityTab nobility = new NobilityTab(this);
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
    /** Whether the longest tabs show their short labels, because even narrow tabs do not fit two rows. */
    private boolean shortTabs;
    /** The tabs further rows of tabs start with, when even narrow tabs do not fit one row. */
    private final List<Integer> rowStarts = new ArrayList<>();
    /** How much lower the content starts because of further rows of tabs. */
    private int contentOffset;
    /** The first column of rights shown, when not all of them fit. */
    private int rightsColumn;
    private int rightsVisible;
    private int arrowWidth;
    /** The choice of a new style for the kingdom, open in the overview for its king, or {@code null}. */
    @org.jetbrains.annotations.Nullable
    private StylePicker stylePicker;
    /** Whether a change of the style was asked and the answer of the server is awaited. */
    private boolean styleSent;
    private int changeStyleWidth;
    private int confirmStyleWidth;
    /** The width of the button at the end of the line of the throne that moves it or calls the move off. */
    private int moveThroneWidth;

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
            case CONSTRUCTION -> construction.checkName();
            case ARMY -> army.checkName();
            case MAGIC -> magic.checkName();
            case ROADS -> roads.checkName();
            case LAW -> law.checkName();
            case DIPLOMACY -> diplomacy.checkName();
            case NOBILITY -> nobility.checkName();
            case OVERVIEW -> stylePicker != null ? "overview-style" : "overview";
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
        if (!canChangeStyle()) {
            stylePicker = null;
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
     * Shows the life of the kingdom the server sent, if it belongs to the kingdom on screen.
     */
    public void updateLife(io.github.sekerator.kingdomworld.common.network.data.LifeViews.TabView lifeView) {
        if (!lifeView.kingdomId().equals(view.id())) {
            return;
        }
        citizens.setLife(lifeView);
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
     * Shows the army tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateArmy(io.github.sekerator.kingdomworld.common.network.data.ArmyViews.ArmyTabView armyView) {
        if (!armyView.kingdomId().equals(view.id())) {
            return;
        }
        army.setView(armyView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the army tab got its view, for the self-check.
     */
    public boolean armyShown() {
        return army.view() != null;
    }

    /**
     * Shows a view of the army tab ({@code overview}, {@code soldiers}, {@code recruit}, {@code squads}, {@code mercenaries},
     * {@code deals}), with soldiers chosen, a squad being ordered or a deal getting guards; for the layout check.
     */
    public void showArmy(String mode, int chosen, long ordering, long escorting) {
        army.showForCheck(ArmyTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), chosen, ordering, escorting);
    }

    /**
     * Shows the choice of branch and post of a soldier of the army tab, for the layout check.
     */
    public void showArmyService(int soldier) {
        army.showServiceForCheck(soldier);
    }

    /**
     * Shows the roads tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateRoads(io.github.sekerator.kingdomworld.common.network.data.RoadViews.RoadTabView roadView) {
        if (!roadView.kingdomId().equals(view.id())) {
            return;
        }
        roads.setView(roadView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the roads tab got its view, for the self-check.
     */
    public boolean roadsShown() {
        return roads.view() != null;
    }

    /**
     * Shows a view of the roads tab ({@code overview}, {@code cities}, {@code roads}, {@code requests}, {@code camps},
     * {@code risks}), with the actions of a road or the choice of a squad to patrol it; for the layout check.
     */
    public void showRoads(String mode, long acting, long patrolling) {
        roads.showForCheck(RoadsTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), acting, patrolling);
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
     * Shows the construction the server sent, if it belongs to the kingdom on screen.
     */
    public void updateConstruction(io.github.sekerator.kingdomworld.common.network.data.ConstructionView constructionView) {
        if (!constructionView.kingdomId().equals(view.id())) {
            return;
        }
        construction.setView(constructionView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the construction tab got its view, for the self-check.
     */
    public boolean constructionShown() {
        return construction.view() != null;
    }

    /**
     * Shows a view of the construction tab ({@code catalog}, {@code sites}, {@code buildings}), a template or a construction
     * in detail when given; for the layout check.
     */
    public void showConstruction(String mode, String template, long site) {
        construction.showForCheck(ConstructionTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)), template, site);
    }

    /**
     * Filters the catalog of the construction tab by style: 0 shows all, 1 the style of the kingdom, 2 and on each
     * architectural style in turn; for the layout check.
     */
    public void filterConstruction(int index) {
        construction.filterForCheck(index);
    }

    /**
     * Opens the choice of a new style in the overview with the style of this index shown, when the viewer may change the
     * style; for the layout check.
     */
    public void showStyleForCheck(int index) {
        if (canChangeStyle()) {
            stylePicker = StylePicker.change(ArchitecturalStyles.index(view.style()));
            stylePicker.select(index);
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the magic tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateMagic(io.github.sekerator.kingdomworld.common.network.data.MagicViews.TabView magicView) {
        if (!magicView.kingdomId().equals(view.id())) {
            return;
        }
        magic.setView(magicView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the magic tab got its view, for the self-check.
     */
    public boolean magicShown() {
        return magic.view() != null;
    }

    /**
     * Shows the law tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateLaw(io.github.sekerator.kingdomworld.common.network.data.LawViews.TabView lawView) {
        if (!lawView.kingdomId().equals(view.id())) {
            return;
        }
        law.setView(lawView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the law tab got its view, for the self-check.
     */
    public boolean lawShown() {
        return law.view() != null;
    }

    /**
     * Shows the diplomacy tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateDiplomacy(io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.TabView diplomacyView) {
        if (!diplomacyView.kingdomId().equals(view.id())) {
            return;
        }
        diplomacy.setView(diplomacyView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the nobility tab the server sent, if it belongs to the kingdom on screen.
     */
    public void updateNobility(io.github.sekerator.kingdomworld.common.network.data.NobilityViews.TabView nobilityView) {
        if (!nobilityView.kingdomId().equals(view.id())) {
            return;
        }
        nobility.setView(nobilityView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Whether the nobility tab got its view, for the self-check.
     */
    public boolean nobilityShown() {
        return nobility.view() != null;
    }

    /**
     * Shows a mode of the nobility tab ({@code houses}, {@code titles}, {@code court}, {@code chronicle}, {@code spies},
     * {@code reports}); for the layout check.
     */
    public void showNobility(String mode) {
        nobility.setMode(NobilityTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
    }

    /**
     * Whether the diplomacy tab got its view, for the self-check.
     */
    public boolean diplomacyShown() {
        return diplomacy.view() != null;
    }

    /**
     * Shows a view of the diplomacy tab ({@code kingdoms}, {@code offers}, {@code wars}, {@code chronicle}), with the actions
     * for the first kingdom open when asked; for the layout check.
     */
    public void showDiplomacy(String mode, boolean actions) {
        diplomacy.setMode(DiplomacyTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
        if (actions) {
            diplomacy.selectFirstForCheck();
        }
    }

    /**
     * Shows a view of the law tab ({@code wanted}, {@code court}, {@code mine}), with the form of a fine open when asked; for
     * the layout check.
     */
    public void showLaw(String mode, boolean fineForm) {
        law.setMode(LawTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
        if (fineForm) {
            law.editFirstForCheck();
        }
    }

    /**
     * Shows a view of the magic tab ({@code energy}, {@code journal}, {@code tower}, {@code spells}, {@code gates},
     * {@code machines}); for the layout check.
     */
    public void showMagic(String mode) {
        magic.setMode(MagicTab.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
    }

    /**
     * Shows a building of the construction tab in detail, with the work on it waiting for confirmation when given
     * ({@code upgrade}, {@code repair}, {@code demolish}), and chooses wood and stone by position; for the layout check.
     */
    public void showConstructionBuilding(long building, String work, int wood, int stone) {
        construction.showForCheck(ConstructionTab.Mode.BUILDINGS, "", 0, building, work.isEmpty() ? null
                : io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.valueOf(work.toUpperCase(Locale.ROOT)));
        construction.chooseForCheck(wood, stone);
    }

    /**
     * Chooses the wood and stone of blueprints by position in their lists; for the layout check.
     */
    public void chooseConstructionMaterials(int wood, int stone) {
        construction.chooseForCheck(wood, stone);
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
     * A point on a line of the list of the Citizens tab, to hover over it; for the layout check.
     */
    public int[] citizensRowPoint(int row) {
        return new int[]{left() + PAD + 40, citizens.rowY(top() + contentOffset, row) + 3};
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
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.LifePackets.TabRequest(view.id()));
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
        if (target != Tab.OVERVIEW) {
            stylePicker = null;
        }
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
        if (target == Tab.CONSTRUCTION && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.Request(view.id()));
        }
        if (target == Tab.TRADE && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.TradePackets.TradeRequest(view.id()));
        }
        if (target == Tab.GUILD && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.GuildPackets.TabRequest(view.id()));
        }
        if (target == Tab.ARMY && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets.TabRequest(view.id()));
        }
        if (target == Tab.MAGIC && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.MagicPackets.TabRequest(view.id()));
        }
        if (target == Tab.ROADS && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.TabRequest(view.id()));
        }
        if (target == Tab.LAW && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.LawPackets.TabRequest(view.id()));
        }
        if (target == Tab.DIPLOMACY && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.DiplomacyPackets.TabRequest(view.id()));
        }
        if (target == Tab.NOBILITY && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.NobilityPackets.TabRequest(view.id()));
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
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"), question));
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
        if (view.npc() != null) {
            // The overview has no room for one more line: the ranks of the guilds are told over the line of the size.
            lines.addAll(npcLines(view.npc(), false));
        } else {
            lines.add(Component.translatable("kingdomworld.screen.throne.ruler", PlayerNames.display(view.rulerName())));
        }
        lines.add(Component.translatable("kingdomworld.screen.throne.members", view.memberCount()));
        lines.add(Component.translatable("kingdomworld.screen.throne.population", view.population()));
        long provinces = io.github.sekerator.kingdomworld.client.map.ClientProvinceMap.chunksHeldBy(view.id());
        lines.add(provinces > 0
                ? Component.translatable("kingdomworld.screen.throne.land", view.chunks() + provinces, view.chunks(), view.chunkLimit())
                : Component.translatable("kingdomworld.screen.throne.chunks", view.chunks(), view.chunkLimit()));
        if (view.capital() != null) {
            lines.add(Component.translatable(view.throneMoving() ? "kingdomworld.screen.throne.capital.moving" : "kingdomworld.screen.throne.capital",
                    view.capital().getX(), view.capital().getY(), view.capital().getZ()));
        }
        Component style = styleLine();
        if (style != null) {
            lines.add(style);
        }
        lines.addAll(rulerLines());
        return lines;
    }

    // ---------------------------------------------------------------- moving the throne

    /**
     * Whether the viewer may move the throne: only the king of a kingdom of players with land; the rulers of the world keep their
     * thrones where they stand.
     */
    private boolean canMoveThrone() {
        return isMember() && view.viewerRole() == Role.KING && view.npc() == null && view.capital() != null;
    }

    /**
     * Where the line of the throne stands among the lines of the overview, or -1 without one: after the ruler and the three lines
     * of the people and the land.
     */
    private int capitalLineIndex() {
        if (view.capital() == null) {
            return -1;
        }
        return (view.npc() != null ? npcLines(view.npc(), false).size() : 1) + 3;
    }

    /**
     * The button at the end of the line of the throne: it moves the throne, or calls off the move going on.
     */
    private Component moveThroneLabel() {
        return Component.translatable(view.throneMoving() ? "kingdomworld.screen.throne.move.cancel" : "kingdomworld.screen.throne.move");
    }

    /**
     * What the king is asked before the move starts: what comes next, and when the throne may move again.
     */
    private Component moveThroneQuestion() {
        if (view.throneMoving()) {
            return Component.translatable("kingdomworld.screen.throne.move.cancel.confirm");
        }
        return view.throneMoveDays() > 0
                ? Component.translatable("kingdomworld.screen.throne.move.confirm", view.name(),
                io.github.sekerator.kingdomworld.common.trade.TradeTexts.days(view.throneMoveDays()))
                : Component.translatable("kingdomworld.screen.throne.move.confirm.any_time", view.name());
    }

    private void moveThrone() {
        boolean moving = view.throneMoving();
        confirm(moveThroneQuestion(), () -> send(moving ? KingdomActionPacket.Action.CANCEL_THRONE_MOVE : KingdomActionPacket.Action.MOVE_THRONE,
                null, 0));
    }

    // ---------------------------------------------------------------- style

    /**
     * Whether the viewer may change the style of the kingdom: only the king of a kingdom of players; a kingdom of the world keeps
     * the style of its capital.
     */
    private boolean canChangeStyle() {
        return isMember() && view.viewerRole() == Role.KING && view.npc() == null;
    }

    /**
     * The line of the architectural style of the kingdom, or {@code null} while the server sent none.
     */
    @org.jetbrains.annotations.Nullable
    private Component styleLine() {
        return view.style().isEmpty() ? null : Component.translatable("kingdomworld.screen.throne.style", StylePicker.name(view.style()));
    }

    /**
     * Where the line of the style stands among the lines of the overview, or -1 without one: last before those of a ruler.
     */
    private int styleLineIndex() {
        return styleLine() == null ? -1 : overviewLines().size() - rulerLines().size() - 1;
    }

    /**
     * The step between the lines of the overview: those of a ruler of the world come closer together, as on the card of the
     * atlas, and closer still when the kingdom has every line.
     */
    private static int overviewStep(int lines, int rulerLines) {
        if (rulerLines == 0) {
            return 14;
        }
        return lines > 11 ? 10 : 11;
    }

    /**
     * What changing the style costs, in the coins of the kingdom.
     */
    private Component styleFee() {
        CurrencyUnits units = CurrencyDirectory.get(view.id()).map(CurrencyInfo::units).orElse(CurrencyUnits.DEFAULT);
        return MoneyTexts.amount(view.styleFee(), units);
    }

    private Component confirmStyleLabel() {
        return Component.translatable("kingdomworld.screen.throne.style.confirm", styleFee());
    }

    private void openStylePicker() {
        stylePicker = StylePicker.change(ArchitecturalStyles.index(view.style()));
        rebuildWidgets();
    }

    /**
     * The column of the picture of a style in the overview: the picture, from the left edge of the tabs, with the row of its
     * name and arrows under it.
     */
    private int styleColumn() {
        return StylePicker.span(font, StylePicker.PICTURE_WIDTH);
    }

    /**
     * The choice of a new style in the overview: the picture with its arrows on the left, the current style marked on it; the
     * current style, the name of the shown one over what it is like and the way back beside it; the button that builds in the shown style for the
     * fee in place of leaving the kingdom at the bottom.
     */
    private void initStylePicker(int left, int top) {
        int y = top + contentOffset + CONTENT_TOP + 4;
        stylePicker.layout(left + PAD, y, styleColumn(), StylePicker.PICTURE_WIDTH, StylePicker.PICTURE_HEIGHT, false);
        for (Button arrow : stylePicker.buttons(this::rebuildWidgets)) {
            addRenderableWidget(arrow);
        }
        Component back = Component.translatable("gui.back");
        int backWidth = buttonWidth(back);
        addRenderableWidget(Button.builder(back, button -> {
            stylePicker = null;
            rebuildWidgets();
        }).bounds(left + panelWidth - PAD - backWidth, y + StylePicker.PICTURE_HEIGHT + StylePicker.ROW_GAP, backWidth, StylePicker.ARROW)
                .build());
        String id = stylePicker.id();
        int width = Math.min(confirmStyleWidth, panelWidth - 2 * PAD - doneWidth - GAP);
        Button confirmButton = addRenderableWidget(Button.builder(confirmStyleLabel(), button ->
                confirm(Component.translatable("kingdomworld.screen.throne.style.question", StylePicker.name(id), styleFee()), () -> {
                    styleSent = true;
                    send(KingdomActionPacket.Action.SET_STYLE, null, ArchitecturalStyles.index(id));
                })).bounds(left + PAD, top + panelHeight - 26, width, 20).build());
        confirmButton.active = !id.equals(view.style());
    }

    private void renderStylePicker(GuiGraphics graphics, int left, int top, int right) {
        stylePicker.render(this, graphics, font);
        int x = left + PAD + styleColumn() + PAD;
        int y = top + CONTENT_TOP + 4;
        Component current = styleLine();
        String shown = stylePicker.id();
        if (current != null) {
            fittedLabel(graphics, current, x, y, 0xFFF0D070, right - PAD);
            y += 14;
            if (!shown.equals(view.style())) {
                // The text under the current style tells what the shown one is like, so it is headed by its name.
                fittedLabel(graphics, StylePicker.name(shown), x, y, 0xFFFFFFFF, right - PAD);
                y += LINE + 2;
            }
        }
        paragraph(graphics, StylePicker.caption(shown), x, y, right - PAD - x, 4, 0xFFD0D0D0);
    }

    /**
     * The decrees of a ruler of the world: the last one, what the kingdom needs and how the ruler feels about the viewer.
     */
    private List<Component> rulerLines() {
        io.github.sekerator.kingdomworld.common.network.data.RulerInfo ruler = view.ruler();
        if (ruler == null || view.npc() == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        if (ruler.decrees().isEmpty()) {
            lines.add(Component.translatable("kingdomworld.screen.throne.npc.decree.none"));
        } else {
            lines.add(Component.translatable("kingdomworld.screen.throne.npc.decree",
                    io.github.sekerator.kingdomworld.common.npc.RulerTexts.decree(ruler.decrees().get(0).decision(), view.npc().rulerGender())));
        }
        lines.add(Component.translatable("kingdomworld.screen.throne.npc.needs", io.github.sekerator.kingdomworld.common.npc.RulerTexts.needs(ruler.needs())));
        lines.add(Component.translatable("kingdomworld.screen.throne.npc.attitude",
                io.github.sekerator.kingdomworld.common.npc.RulerTexts.attitude(ruler.attitude())));
        return lines;
    }

    /**
     * What hovering over a line of a ruler of the world tells: every recent decree, every need, the reasons of the attitude.
     */
    private List<Component> rulerHover(int line) {
        io.github.sekerator.kingdomworld.common.network.data.RulerInfo ruler = view.ruler();
        List<Component> texts = new ArrayList<>();
        if (ruler == null || view.npc() == null) {
            return texts;
        }
        switch (line) {
            case 0 -> ruler.decrees().forEach(decree -> texts.add(Component.translatable("kingdomworld.screen.throne.npc.decree.line", decree.day(),
                    io.github.sekerator.kingdomworld.common.npc.RulerTexts.decree(decree.decision(), view.npc().rulerGender()))));
            case 1 -> {
                ruler.needs().forEach(need -> texts.add(io.github.sekerator.kingdomworld.common.npc.RulerTexts.need(need)));
                texts.add(Component.translatable("kingdomworld.screen.throne.npc.needs.hint"));
            }
            default -> {
                texts.add(Component.translatable("kingdomworld.screen.throne.npc.attitude.hint", ruler.reputation(), ruler.dealings()));
                if (!ruler.councillor().isEmpty()) {
                    texts.add(Component.translatable("kingdomworld.screen.throne.npc.councillor", PlayerNames.display(ruler.councillor())));
                    texts.add(Component.translatable("kingdomworld.screen.throne.npc.councillor.rights"));
                }
            }
        }
        return texts;
    }

    private Component relationText() {
        return switch (view.relation()) {
            case MEMBER -> Component.translatable("kingdomworld.screen.throne.your_role", view.ruler() != null && view.ruler().yours()
                    ? Component.translatable("kingdomworld.ruler.position.councillor") : roleName(view.viewerRole()));
            case APPLIED -> Component.translatable("kingdomworld.screen.throne.applied");
            case INVITED -> Component.translatable("kingdomworld.screen.throne.invited");
            case OTHER_KINGDOM -> Component.translatable("kingdomworld.screen.throne.other_kingdom");
            case OUTSIDER -> view.npc() != null ? Component.translatable("kingdomworld.screen.throne.npc.outsider")
                    : Component.translatable("kingdomworld.screen.throne.outsider");
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
        labels.add(tabLabel(Tab.OVERVIEW));
        if (isMember()) {
            for (Tab value : List.of(Tab.MEMBERS, Tab.CITIZENS, Tab.STORAGE, Tab.ORDERS, Tab.TREASURY, Tab.MARKET, Tab.TRADE, Tab.GUILD,
                    Tab.CONSTRUCTION, Tab.ARMY, Tab.MAGIC, Tab.ROADS, Tab.LAW, Tab.DIPLOMACY, Tab.NOBILITY, Tab.RIGHTS)) {
                labels.add(tabLabel(value));
            }
        }
        return labels;
    }

    /**
     * Lays the tabs out in rows for the width of the screen: usual tabs in one row, narrow ones if they do not fit, and further
     * rows if even narrow tabs do not fit one.
     *
     * @return the width the rows of tabs take
     */
    private int arrangeTabs() {
        int tabs = 0;
        int compact = 0;
        List<Component> labels = tabLabels();
        for (Component label : labels) {
            tabs += buttonWidth(label) + GAP;
            compact += compactTabWidth(label) + TAB_GAP;
        }
        compactTabs = tabs + 2 * PAD > width - 8;
        rowStarts.clear();
        if (compactTabs) {
            tabs = compact;
            if (compact + 2 * PAD > width - 8) {
                // Even narrow tabs do not fit one row: the rest go to further rows.
                int room = width - 8 - 2 * PAD;
                int used = 0;
                int widest = 0;
                for (int i = 0; i < labels.size(); i++) {
                    int next = compactTabWidth(labels.get(i)) + TAB_GAP;
                    if (used + next > room && used > 0) {
                        rowStarts.add(i);
                        widest = Math.max(widest, used);
                        used = 0;
                    }
                    used += next;
                }
                tabs = Math.max(widest, used);
            }
        }
        return tabs;
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

        shortTabs = false;
        int tabs = arrangeTabs();
        if (rowStarts.size() > 1) {
            // Even narrow tabs need a third row: the longest show their short labels.
            shortTabs = true;
            tabs = arrangeTabs();
        }
        contentOffset = rowStarts.size() * (TAB_HEIGHT + 1);
        int members = MIN_NAME_COLUMN + 12 + roleWidth + kickWidth + crownWidth + 2 * GAP;
        int applications = MIN_NAME_COLUMN + 12 + approveWidth + rejectWidth + GAP;
        arrowWidth = buttonWidth(Component.literal("<"), Component.literal(">"));
        // The rights show as many columns as fit; the rest are reached with arrows.
        int rights = rightsLabelWidth + 4 * (rightsCellWidth + GAP) + 2 * (arrowWidth + GAP);
        int overview = font.width(relationText());
        for (Component line : overviewLines()) {
            overview = Math.max(overview, font.width(line));
        }
        changeStyleWidth = buttonWidth(Component.translatable("kingdomworld.screen.throne.style.change"));
        confirmStyleWidth = buttonWidth(confirmStyleLabel());
        Component style = styleLine();
        if (style != null && canChangeStyle()) {
            // The button that changes the style stands at the end of its line.
            overview = Math.max(overview, font.width(style) + 4 + changeStyleWidth);
        }
        if (canChangeStyle()) {
            overview = Math.max(overview, styleColumn() + PAD + STYLE_TEXT - 4);
        }
        moveThroneWidth = buttonWidth(moveThroneLabel());
        int capitalIndex = capitalLineIndex();
        if (capitalIndex >= 0 && canMoveThrone()) {
            // The button that moves the throne stands at the end of the line of the throne.
            overview = Math.max(overview, font.width(overviewLines().get(capitalIndex)) + 4 + moveThroneWidth);
        }
        int bottom = doneWidth + PAD;
        for (Component action : overviewActions()) {
            bottom += buttonWidth(action) + GAP;
        }
        if (isMember()) {
            bottom += buttonWidth(Component.translatable("kingdomworld.screen.throne.tab.territory")) + GAP;
        }
        if (canChangeStyle()) {
            bottom = Math.max(bottom, doneWidth + PAD + confirmStyleWidth + GAP);
        }
        int inner = Math.max(Math.max(tabs, members), Math.max(Math.max(applications, rights), Math.max(overview + 4, bottom)));
        if (isMember()) {
            inner = Math.max(inner, Math.max(Math.max(citizens.measure(font), storage.measure(font)),
                    Math.max(orders.measure(font), treasury.measure(font))));
            inner = Math.max(inner, Math.max(market.measure(font), trade.measure(font)));
            inner = Math.max(inner, construction.measure(font));
            inner = Math.max(inner, guild.measure(font));
            inner = Math.max(inner, army.measure(font));
            inner = Math.max(inner, magic.measure(font));
            inner = Math.max(inner, roads.measure(font));
            inner = Math.max(inner, law.measure(font));
            inner = Math.max(inner, diplomacy.measure(font));
            inner = Math.max(inner, nobility.measure(font));
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
            shown.addAll(List.of(Tab.MEMBERS, Tab.CITIZENS, Tab.STORAGE, Tab.ORDERS, Tab.TREASURY, Tab.MARKET, Tab.TRADE, Tab.GUILD,
                    Tab.CONSTRUCTION, Tab.ARMY, Tab.MAGIC, Tab.ROADS, Tab.LAW, Tab.DIPLOMACY, Tab.NOBILITY, Tab.RIGHTS));
        }
        for (int i = 0; i < shown.size(); i++) {
            if (rowStarts.contains(i)) {
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
            case CONSTRUCTION -> construction.init(left, inner, panelWidth, innerHeight);
            case ARMY -> army.init(left, inner, panelWidth, innerHeight);
            case MAGIC -> magic.init(left, inner, panelWidth, innerHeight);
            case ROADS -> roads.init(left, inner, panelWidth, innerHeight);
            case LAW -> law.init(left, inner, panelWidth, innerHeight);
            case DIPLOMACY -> diplomacy.init(left, inner, panelWidth, innerHeight);
            case NOBILITY -> nobility.init(left, inner, panelWidth, innerHeight);
            case RIGHTS -> initRights(inner);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top + panelHeight - 26, doneWidth, 20).build());
    }

    /**
     * A tab just wide enough for its text, for screens where the usual tabs do not fit in one row.
     */
    /**
     * The label of a tab: its short form on the smallest screens.
     */
    private Component tabLabel(Tab target) {
        return shortTabs && target.hasShort() ? Component.translatable(target.key + ".short") : Component.translatable(target.key);
    }

    private int compactTabWidth(Component label) {
        return font.width(label) + LayoutCheck.BUTTON_TEXT_MARGIN;
    }

    private int tabWidth(Component label) {
        return compactTabs ? compactTabWidth(label) : buttonWidth(label);
    }

    private int tabButton(Tab target, int x, int y) {
        Component label = tabLabel(target);
        int buttonWidth = tabWidth(label);
        Button button = addRenderableWidget(Button.builder(label, b -> openTab(target))
                .bounds(x, y, buttonWidth, TAB_HEIGHT).build());
        button.active = tab != target;
        if (shortTabs && target.hasShort()) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(target.key)));
        }
        return x + buttonWidth + (compactTabs ? TAB_GAP : GAP);
    }

    private void initOverview(int left, int top) {
        if (stylePicker != null && canChangeStyle()) {
            initStylePicker(left, top);
            return;
        }
        stylePicker = null;
        int styleIndex = styleLineIndex();
        if (styleIndex >= 0 && canChangeStyle()) {
            List<Component> lines = overviewLines();
            int styleY = top + contentOffset + CONTENT_TOP + 4 + styleIndex * overviewStep(lines.size(), rulerLines().size());
            addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.style.change"), button -> openStylePicker())
                    .bounds(left + panelWidth - PAD - changeStyleWidth, styleY - 3, changeStyleWidth, STYLE_BUTTON_HEIGHT).build());
        }
        int capitalIndex = capitalLineIndex();
        if (capitalIndex >= 0 && canMoveThrone()) {
            List<Component> lines = overviewLines();
            int capitalY = top + contentOffset + CONTENT_TOP + 4 + capitalIndex * overviewStep(lines.size(), rulerLines().size());
            addRenderableWidget(Button.builder(moveThroneLabel(), button -> moveThrone())
                    .bounds(left + panelWidth - PAD - moveThroneWidth, capitalY - 3, moveThroneWidth, STYLE_BUTTON_HEIGHT).build());
        }
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
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.dissolve.confirm", view.name()), () -> {
                                closeOnSuccess = true;
                                send(KingdomActionPacket.Action.DISSOLVE, null, 0);
                            })).bounds(x, y, buttonWidth(label), 20).build());
                } else {
                    addRenderableWidget(Button.builder(label,
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.leave.confirm", view.name()),
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
                    addRenderableWidget(new CycleButton(roleX(), y, roleWidth, 18, roleName(member.role()), direction ->
                            send(KingdomActionPacket.Action.SET_ROLE, member.id(), member.role().stepAssignable(direction).ordinal())));
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
        if (tab == Tab.CONSTRUCTION) {
            if (construction.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.ARMY) {
            if (army.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.MAGIC) {
            if (magic.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.ROADS) {
            if (roads.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.LAW) {
            if (law.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.DIPLOMACY) {
            if (diplomacy.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (tab == Tab.NOBILITY) {
            if (nobility.scroll(top() + contentOffset, panelHeight - contentOffset, delta)) {
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
        if (styleSent) {
            // The kingdom builds in the new style: the choice closes and the overview shows it.
            styleSent = false;
            if (success) {
                stylePicker = null;
                if (minecraft != null) {
                    rebuildWidgets();
                }
            }
        }
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
        centeredName(graphics, kingdomName(view.name(), view.npc()), width / 2, top + 7, 0xFF000000 | FoundKingdomScreen.readable(view.color()),
                panelWidth - 2 * PAD);
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
            case CONSTRUCTION -> construction.render(graphics, font, left, inner, panelWidth, innerHeight);
            case ARMY -> army.render(graphics, font, left, inner, panelWidth, innerHeight);
            case MAGIC -> magic.render(graphics, font, left, inner, panelWidth, innerHeight);
            case ROADS -> roads.render(graphics, font, left, inner, panelWidth, innerHeight);
            case LAW -> law.render(graphics, font, left, inner, panelWidth, innerHeight);
            case DIPLOMACY -> diplomacy.render(graphics, font, left, inner, panelWidth, innerHeight);
            case NOBILITY -> nobility.render(graphics, font, left, inner, panelWidth, innerHeight);
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
        if (bottomText.getString().isEmpty() && tab == Tab.CONSTRUCTION) {
            bottomText = construction.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.GUILD) {
            bottomText = guild.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.ARMY) {
            bottomText = army.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.MAGIC) {
            bottomText = magic.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.ROADS) {
            bottomText = roads.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.LAW) {
            bottomText = law.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.DIPLOMACY) {
            bottomText = diplomacy.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.NOBILITY) {
            bottomText = nobility.hint();
            color = 0xFFB8B8B8;
        }
        if (bottomText.getString().isEmpty() && tab == Tab.OVERVIEW && stylePicker != null) {
            bottomText = Component.translatable("kingdomworld.screen.throne.style.hint");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottomText, left + PAD, top + panelHeight - BOTTOM_AREA + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private void renderOverview(GuiGraphics graphics, int left, int top, int right) {
        if (stylePicker != null) {
            renderStylePicker(graphics, left, top, right);
            return;
        }
        int x = left + PAD + 4;
        int y = top + CONTENT_TOP + 4;
        int index = 0;
        List<Component> lines = overviewLines();
        int rulerFirst = lines.size() - rulerLines().size();
        int styleIndex = styleLineIndex();
        int capitalIndex = capitalLineIndex();
        int step = overviewStep(lines.size(), lines.size() - rulerFirst);
        // While a message of the status is shown, the lines of the ruler that would run into it wait until it goes; they never
        // run into the buttons.
        int buttonsTop = top - contentOffset + panelHeight - 26;
        int statusTop = status.getString().isEmpty() ? Integer.MAX_VALUE : top - contentOffset + panelHeight - BOTTOM_AREA + 2;
        int limit = Math.min(statusTop, buttonsTop - 1);
        for (Component line : lines) {
            if (index >= rulerFirst && y + 9 > limit) {
                break;
            }
            // The lines of the style and of the throne end before the buttons that change them.
            int lineRight = index == styleIndex && canChangeStyle() ? right - PAD - changeStyleWidth - 4
                    : index == capitalIndex && canMoveThrone() ? right - PAD - moveThroneWidth - 4 : right - PAD;
            fittedLabel(graphics, line, x, y, index >= rulerFirst ? 0xFFD8E8C8 : 0xFFE8E8E8, lineRight);
            int width = Math.min(font.width(line), lineRight - x);
            if (view.npc() != null && index == 1) {
                hoverText(x, y, width, npcHint());
                if (view.npc().ranked()) {
                    hoverText(x, y, width, npcGuilds(view.npc().adventurers(), view.npc().mages()));
                }
            }
            if (index == styleIndex) {
                for (Component hover : StylePicker.captionLines(font, view.style())) {
                    hoverText(x, y, width, hover);
                }
            }
            if (index == capitalIndex && view.throneMoving()) {
                hoverText(x, y, width, Component.translatable("kingdomworld.screen.throne.move.pending"));
            }
            if (index >= rulerFirst) {
                for (Component hover : rulerHover(index - rulerFirst)) {
                    hoverText(x, y, width, hover);
                }
            }
            index++;
            y += step;
        }
        int relationY = y + (step == 14 ? 6 : 4);
        boolean statusShown = !status.getString().isEmpty() && relationY + 9 > top - contentOffset + panelHeight - BOTTOM_AREA + 2;
        if (!statusShown && relationY + 9 <= buttonsTop) {
            fittedLabel(graphics, relationText(), x, relationY, 0xFFF0D070, right - PAD);
        }
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
        if (tab == Tab.CONSTRUCTION && button == 0) {
            return construction.click(left(), top() + contentOffset, panelWidth, panelHeight - contentOffset, mouseX, mouseY);
        }
        return tab == Tab.ORDERS && button == 0 && orders.click(left(), top() + contentOffset, panelWidth, panelHeight - contentOffset, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
