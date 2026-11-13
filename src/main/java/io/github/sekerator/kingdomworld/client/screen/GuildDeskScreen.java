package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The desk of the guild: the player's card (rank, points, the exam), the board of the branch with filters, the player's
 * contracts (progress, hand in, give up, call off) and a form to post a contract, paid from the player's account or, with
 * the right to manage the guild, by the kingdom. Every button sends an intent; the server decides and answers with a fresh
 * view.
 */
public class GuildDeskScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int BOX = 56;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.guild.";

    enum Mode {
        CARD,
        BOARD,
        MINE,
        POST,
        MARKET,
        HIRE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Kinds the board filter cycles through; {@code null} shows all. */
    private static final Contract.Kind[] FILTERS = {null, Contract.Kind.HUNT, Contract.Kind.GATHER, Contract.Kind.BOUNTY};
    private static final Contract.Kind[] POSTABLE = {Contract.Kind.HUNT, Contract.Kind.GATHER, Contract.Kind.BOUNTY};

    private GuildViews.DeskView view;
    private final GuildPicker picker = new GuildPicker(this);
    private final MarketPanel market = new MarketPanel(this);
    /** Width of each tab, by its label. */
    private final int[] tabWidths = new int[Mode.values().length];
    private int tabRows = 1;
    private Mode mode = Mode.CARD;
    private int filter;
    private boolean onlyMine;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int postKind;
    private boolean byRank;
    private Rank huntRank = Rank.F;
    private String target = "";
    private ItemStack targetIcon = ItemStack.EMPTY;
    private Component targetName = Component.empty();
    private String countText = "5";
    private String rewardText = "";
    private Rank minRank = Rank.F;
    private String daysText = "2";
    private boolean forKingdom;
    private boolean squads = true;

    private int panelWidth;
    private int panelHeight;
    private int tabWidth;
    private int doneWidth;
    private int actionWidth;
    private int filterWidth;
    private int rankFilterWidth;
    private int labelWidth;
    private int kindWidth;
    private int posterWidth;
    private int chooseWidth;
    private int modeWidth;
    private int rankWidth;
    private int squadsWidth;
    private int postWidth;
    private int examWidth;
    private int joinWidth;
    private int badgeWidth;

    public GuildDeskScreen(GuildViews.DeskView view) {
        super(Component.translatable("block.kingdomworld.guild_desk"));
        this.view = view;
        this.mode = view.card().member() ? Mode.BOARD : Mode.CARD;
    }

    @Override
    public String checkName() {
        return "guild-desk-" + mode.id() + (picker.isOpen() ? "-search" : "");
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(GuildViews.DeskView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * The market and the hire of companions at this desk, from the server.
     */
    public void updateMarket(io.github.sekerator.kingdomworld.common.network.data.CompanionViews.MarketView newView) {
        market.update(newView);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows the market or the hire with a view; for the layout check.
     */
    public void showMarketForCheck(boolean slaves, io.github.sekerator.kingdomworld.common.network.data.CompanionViews.MarketView marketView,
                                   boolean coins) {
        mode = slaves ? Mode.MARKET : Mode.HIRE;
        market.showForCheck(marketView, coins);
    }

    /**
     * Shows a view of the screen, with a filled form or the search; for the layout check.
     */
    public void showForCheck(String value, String targetId, ItemStack icon, Component name, String count, String reward, boolean kingdom,
                             String search, boolean monsters) {
        mode = Mode.valueOf(value.toUpperCase(Locale.ROOT));
        target = targetId;
        targetIcon = icon;
        targetName = name;
        countText = count;
        rewardText = reward;
        forKingdom = kingdom;
        if (search != null) {
            picker.showForCheck(monsters, search);
        }
    }

    <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        return addRenderableWidget(widget);
    }

    void rebuild() {
        rebuildWidgets();
    }

    void focus(GuiEventListener widget) {
        setInitialFocus(widget);
    }

    net.minecraft.client.gui.Font font() {
        return font;
    }

    // ---------------------------------------------------------------- texts

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view.currency().units());
    }

    /**
     * Ticks as game days or minutes.
     */
    static MutableComponent time(long ticks) {
        long days = ticks / 24_000L;
        return days >= 1 ? TradeTexts.days(days) : TradeTexts.ticks(ticks);
    }

    private static Component tabLabel(Mode value) {
        return Component.translatable(KEY + "tab." + value.id());
    }

    private static boolean companions(Mode value) {
        return value == Mode.MARKET || value == Mode.HIRE;
    }

    private static Component kindName(Contract.Kind kind) {
        return kind == null ? Component.translatable(KEY + "filter.all") : GuildTexts.kind(kind);
    }

    private Contract.Kind postKind() {
        return POSTABLE[postKind];
    }

    private static Component task(GuildViews.ContractRow row) {
        return GuildTexts.task(row.kind(), row.target(), row.targetRank(), row.boss(), row.count());
    }

    private Component details(GuildViews.ContractRow row) {
        Component poster = switch (row.poster()) {
            case KINGDOM -> Component.translatable(KEY + "poster.kingdom", row.posterName());
            case PLAYER -> Component.translatable(KEY + "poster.player", row.posterName());
            case GUILD -> Component.translatable(KEY + "poster.guild");
        };
        return Component.translatable(KEY + "details", task(row), GuildTexts.rank(row.minRank()), money(row.payout()),
                GuildTexts.points(row.points()), time(row.ticksLeft()), poster);
    }

    private List<GuildViews.ContractRow> board() {
        List<GuildViews.ContractRow> rows = new ArrayList<>();
        Contract.Kind kind = FILTERS[filter];
        Rank mine = view.card().rank();
        for (GuildViews.ContractRow row : view.board()) {
            if (kind != null && row.kind() != kind) {
                continue;
            }
            if (onlyMine && (row.taken() || row.minRank().ordinal() > mine.ordinal() + 1)) {
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    private int rowCount() {
        return switch (mode) {
            case BOARD -> board().size();
            case MINE -> view.mine().size();
            case MARKET, HIRE -> market.rowCount(mode == Mode.MARKET);
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        tabWidth = 0;
        int tabsTotal = 0;
        for (Mode value : Mode.values()) {
            tabWidths[value.ordinal()] = buttonWidth(tabLabel(value));
            tabWidth = Math.max(tabWidth, tabWidths[value.ordinal()]);
            tabsTotal += tabWidths[value.ordinal()] + GAP;
        }
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        actionWidth = buttonWidth(Component.translatable(KEY + "take"), Component.translatable(KEY + "hand_in"),
                Component.translatable(KEY + "abandon"), Component.translatable(KEY + "cancel"));
        filterWidth = 0;
        for (Contract.Kind kind : FILTERS) {
            filterWidth = Math.max(filterWidth, buttonWidth(Component.translatable(KEY + "filter.kind", kindName(kind))));
        }
        rankFilterWidth = buttonWidth(Component.translatable(KEY + "filter.rank.all"), Component.translatable(KEY + "filter.rank.mine"));
        labelWidth = 0;
        for (String key : List.of("form.kind", "form.target", "form.count", "form.min_rank")) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(KEY + key)) + 6);
        }
        kindWidth = 0;
        for (Contract.Kind kind : POSTABLE) {
            kindWidth = Math.max(kindWidth, buttonWidth(GuildTexts.kind(kind)));
        }
        posterWidth = buttonWidth(Component.translatable(KEY + "form.from_me"), Component.translatable(KEY + "form.from_kingdom"));
        chooseWidth = buttonWidth(Component.translatable(KEY + "form.choose"));
        modeWidth = buttonWidth(Component.translatable(KEY + "form.by_monster"), Component.translatable(KEY + "form.by_rank"));
        rankWidth = buttonWidth(Component.translatable(KEY + "form.rank", "SSS"));
        squadsWidth = buttonWidth(Component.translatable(KEY + "form.squads_yes"), Component.translatable(KEY + "form.squads_no"));
        postWidth = buttonWidth(Component.translatable(KEY + "form.post"));
        examWidth = buttonWidth(Component.translatable(KEY + "exam"));
        joinWidth = buttonWidth(Component.translatable(KEY + "join"));
        badgeWidth = font.width("SSS+") + 4;
        int rewardLabel = font.width(Component.translatable(KEY + "form.reward")) + 6;
        int daysLabel = font.width(Component.translatable(KEY + "form.days")) + 6;
        int tabs = tabsTotal;
        int form = Math.max(labelWidth + kindWidth + GAP + posterWidth, labelWidth + modeWidth + GAP + chooseWidth + GAP + rankWidth);
        form = Math.max(form, labelWidth + BOX + 6 + rewardLabel + BOX);
        form = Math.max(form, labelWidth + rankWidth + 6 + daysLabel + BOX + GAP + squadsWidth);
        form = Math.max(form, postWidth + 8);
        int list = 16 + 4 + badgeWidth + 140 + actionWidth;
        int inner = Math.max(Math.max(tabs, form), Math.max(list, filterWidth + GAP + rankFilterWidth));
        inner = Math.max(inner, picker.measure(font));
        inner = Math.max(inner, market.measure(font));
        for (String key : List.of("empty.board", "empty.mine", "not_member")) {
            inner = Math.max(inner, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
        // Tabs that do not fit in one row go on to a second.
        tabRows = 1;
        int x = 0;
        for (Mode value : Mode.values()) {
            int w = tabWidths[value.ordinal()];
            if (x > 0 && x + w > panelWidth - 2 * PAD) {
                tabRows++;
                x = 0;
            }
            x += w + GAP;
        }
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int tabsY() {
        return top() + 20 + LINE + 4;
    }

    private int controlsY() {
        return tabsY() + tabRows * ROW;
    }

    private int listTop() {
        return mode == Mode.BOARD ? controlsY() + ROW + 2 : controlsY() + 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int bottom() {
        return top() + panelHeight - bottomArea();
    }

    private int visibleRows() {
        return Math.max(1, (bottom() - listTop() - 11) / ROW);
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        int tabY = tabsY();
        for (Mode value : Mode.values()) {
            int w = tabWidths[value.ordinal()];
            if (x > left + PAD && x + w > right) {
                x = left + PAD;
                tabY += ROW;
            }
            Button tab = addRenderableWidget(Button.builder(tabLabel(value), b -> {
                mode = value;
                scroll = 0;
                picker.close();
                if (companions(value)) {
                    market.resetScroll();
                    market.request();
                }
                rebuildWidgets();
            }).bounds(x, tabY, w, ROW - 2).build());
            tab.active = mode != value && (value == Mode.CARD || view.card().member() || value == Mode.POST || companions(value));
            if (companions(value)) {
                tab.setTooltip(Tooltip.create(Component.translatable(KEY + "tab." + value.id() + ".full")));
            }
            x += w + GAP;
        }
        x = left + PAD;
        switch (mode) {
            case CARD -> initCard(x, right);
            case BOARD -> initBoard(x, right);
            case MINE -> initMine(right);
            case POST -> {
                if (picker.isOpen()) {
                    picker.init(x, right, controlsY() + 2, bottom());
                } else {
                    initForm(x);
                }
            }
            case MARKET, HIRE -> {
                if (market.view() == null) {
                    market.request();
                }
                market.init(mode == Mode.MARKET, x, right, controlsY(), bottom());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private int cardButtonY() {
        return controlsY() + 2 + 6 * (LINE + 2);
    }

    private void initCard(int x, int right) {
        int y = cardButtonY();
        if (!view.card().member()) {
            Button join = addRenderableWidget(Button.builder(Component.translatable(KEY + "join"),
                    b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.JOIN, 0)))
                    .bounds(x, y, joinWidth, ROW - 2).build());
            join.setTooltip(Tooltip.create(view.joinFee() > 0 ? Component.translatable(KEY + "join_fee", money(view.joinFee()))
                    : Component.translatable(KEY + "join_free")));
            return;
        }
        if (view.card().exam() == GuildViews.ExamState.AVAILABLE) {
            addRenderableWidget(Button.builder(Component.translatable(KEY + "exam"),
                    b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.EXAM, 0)))
                    .bounds(x, y, examWidth, ROW - 2).build());
        }
    }

    private void initBoard(int x, int right) {
        addRenderableWidget(Button.builder(Component.translatable(KEY + "filter.kind", kindName(FILTERS[filter])), b -> {
            filter = (filter + 1) % FILTERS.length;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, controlsY(), filterWidth, ROW - 2).build());
        addRenderableWidget(Button.builder(Component.translatable(onlyMine ? KEY + "filter.rank.mine" : KEY + "filter.rank.all"), b -> {
            onlyMine = !onlyMine;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x + filterWidth + GAP, controlsY(), rankFilterWidth, ROW - 2).build());
        List<GuildViews.ContractRow> rows = board();
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            GuildViews.ContractRow row = rows.get(scroll + i);
            int y = listTop() + i * ROW;
            if (row.canCancel()) {
                addRenderableWidget(Button.builder(Component.translatable(KEY + "cancel"),
                        b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.CANCEL, row.id())))
                        .bounds(right - actionWidth, y, actionWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
            } else if (!row.taken()) {
                Button take = addRenderableWidget(Button.builder(Component.translatable(KEY + "take"),
                        b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.TAKE, row.id())))
                        .bounds(right - actionWidth, y, actionWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
                take.active = view.card().member() && !row.posted() && row.minRank().ordinal() <= view.card().rank().ordinal() + 1;
            }
        }
    }

    private void initMine(int right) {
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, view.mine().size() - visible));
        for (int i = 0; i < visible && scroll + i < view.mine().size(); i++) {
            GuildViews.ContractRow row = view.mine().get(scroll + i);
            int y = listTop() + i * ROW;
            if (row.mine()) {
                addRenderableWidget(Button.builder(Component.translatable(KEY + "abandon"),
                        b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.ABANDON, row.id())))
                        .bounds(right - actionWidth, y, actionWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
                if (row.kind() == Contract.Kind.GATHER) {
                    addRenderableWidget(Button.builder(Component.translatable(KEY + "hand_in"),
                            b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.HAND_IN, row.id())))
                            .bounds(right - 2 * actionWidth - GAP, y, actionWidth, ROW - 2).build());
                }
            } else if (row.party() && row.kind() == Contract.Kind.GATHER) {
                addRenderableWidget(Button.builder(Component.translatable(KEY + "hand_in"),
                        b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.HAND_IN, row.id())))
                        .bounds(right - actionWidth, y, actionWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
            } else if (row.canCancel()) {
                addRenderableWidget(Button.builder(Component.translatable(KEY + "cancel"),
                        b -> send(GuildPackets.DeskAction.simple(view.pos(), GuildPackets.DeskAction.Action.CANCEL, row.id())))
                        .bounds(right - actionWidth, y, actionWidth, ROW - 2).tooltip(Tooltip.create(details(row))).build());
            }
        }
    }

    private EditBox box(int x, int y, String value, java.util.function.Consumer<String> responder, Component name, int length) {
        EditBox box = new EditBox(font, x + 1, y + 1, BOX - 2, ROW - 4, name);
        box.setMaxLength(length);
        box.setValue(value);
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    private int formY(int row) {
        return controlsY() + 2 + row * (ROW + 1);
    }

    /**
     * The form in five rows: the kind and who pays, the target, the number and the reward, the lowest rank and the days, the
     * squads and the button.
     */
    private void initForm(int x) {
        int fieldX = x + labelWidth;
        addRenderableWidget(Button.builder(GuildTexts.kind(postKind()), b -> {
            postKind = (postKind + 1) % POSTABLE.length;
            target = "";
            targetIcon = ItemStack.EMPTY;
            targetName = Component.empty();
            byRank = false;
            rebuildWidgets();
        }).bounds(fieldX, formY(0), kindWidth, ROW - 2).build());
        Button poster = addRenderableWidget(Button.builder(Component.translatable(forKingdom ? KEY + "form.from_kingdom" : KEY + "form.from_me"),
                b -> {
                    forKingdom = !forKingdom;
                    rebuildWidgets();
                }).bounds(fieldX + kindWidth + GAP, formY(0), posterWidth, ROW - 2).build());
        poster.active = view.canPostForKingdom();
        int targetX = fieldX;
        if (postKind() == Contract.Kind.HUNT) {
            addRenderableWidget(Button.builder(Component.translatable(byRank ? KEY + "form.by_rank" : KEY + "form.by_monster"), b -> {
                byRank = !byRank;
                rebuildWidgets();
            }).bounds(targetX, formY(1), modeWidth, ROW - 2).build());
            targetX += modeWidth + GAP;
        }
        if (byRank && postKind() == Contract.Kind.HUNT) {
            addRenderableWidget(Button.builder(Component.translatable(KEY + "form.rank", huntRank.label()), b -> {
                huntRank = Rank.ofIndex((huntRank.ordinal() + 1) % Rank.COUNT);
                rebuildWidgets();
            }).bounds(targetX, formY(1), rankWidth, ROW - 2).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable(KEY + "form.choose"), b -> {
                picker.open(postKind() != Contract.Kind.GATHER, (id, entry) -> {
                    target = id;
                    targetIcon = entry.icon();
                    targetName = entry.name();
                });
                rebuildWidgets();
            }).bounds(targetX, formY(1), chooseWidth, ROW - 2).build());
        }
        box(fieldX, formY(2), countText, value -> countText = value, Component.translatable(KEY + "form.count"), 4);
        int rewardX = fieldX + BOX + 6;
        int rewardLabel = font.width(Component.translatable(KEY + "form.reward")) + 6;
        box(rewardX + rewardLabel, formY(2), rewardText, value -> rewardText = value, Component.translatable(KEY + "form.reward"), Money.MAX_TEXT)
                .setTooltip(Tooltip.create(Component.translatable(KEY + "form.reward_hint")));
        addRenderableWidget(Button.builder(Component.translatable(KEY + "form.rank", minRank.label()), b -> {
            minRank = Rank.ofIndex((minRank.ordinal() + 1) % Rank.COUNT);
            rebuildWidgets();
        }).bounds(fieldX, formY(3), rankWidth, ROW - 2).build());
        int daysX = fieldX + rankWidth + 6;
        int daysLabel = font.width(Component.translatable(KEY + "form.days")) + 6;
        box(daysX + daysLabel, formY(3), daysText, value -> daysText = value, Component.translatable(KEY + "form.days"), 3)
                .setTooltip(Tooltip.create(Component.translatable(KEY + "form.days_hint", view.maxDays())));
        Button squadsButton = addRenderableWidget(Button.builder(Component.translatable(squads || forKingdom ? KEY + "form.squads_yes"
                : KEY + "form.squads_no"), b -> {
            squads = !squads;
            rebuildWidgets();
        }).bounds(x, formY(4), squadsWidth, ROW - 2).build());
        squadsButton.active = !forKingdom;
        squadsButton.setTooltip(Tooltip.create(Component.translatable(KEY + "form.squads_hint")));
        Button post = addRenderableWidget(Button.builder(Component.translatable(KEY + "form.post"), b -> post())
                .bounds(x + squadsWidth + GAP, formY(4), postWidth, ROW - 2).build());
        post.active = byRank && postKind() == Contract.Kind.HUNT || !target.isEmpty();
    }

    private void post() {
        int count;
        int days;
        try {
            count = Integer.parseInt(countText.strip());
            days = Integer.parseInt(daysText.strip());
        } catch (NumberFormatException e) {
            onResult(false, Component.translatable(KEY + "bad_number"));
            return;
        }
        Optional<Long> reward = Money.parse(rewardText, view.currency().units());
        if (reward.isEmpty() || reward.get() < 1 || count < 1 || count > view.maxCount() || days < 1 || days > view.maxDays()) {
            onResult(false, Component.translatable(KEY + "bad_number"));
            return;
        }
        String id = byRank && postKind() == Contract.Kind.HUNT ? "" : target;
        send(new GuildPackets.DeskAction(view.pos(), GuildPackets.DeskAction.Action.POST, 0, postKind(), id, huntRank.ordinal(), count,
                reward.get(), minRank.ordinal(), days, forKingdom, squads));
    }

    private void send(GuildPackets.DeskAction action) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(action);
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mode == Mode.POST && picker.isOpen()) {
            if (picker.scroll(controlsY() + 2, bottom(), delta)) {
                rebuildWidgets();
            }
            return true;
        }
        if (companions(mode)) {
            if (market.scroll(mode == Mode.MARKET, controlsY(), bottom(), delta)) {
                rebuildWidgets();
            }
            return true;
        }
        int max = Math.max(0, rowCount() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
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
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.branchColor());
        centeredName(graphics, Component.translatable(KEY + "title", view.branchName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.branchColor()), panelWidth - 2 * PAD);
        Component summary = view.card().member()
                ? Component.translatable(KEY + "summary", GuildTexts.rank(view.card().rank()), GuildTexts.points(view.card().points()),
                money(view.balance()))
                : Component.translatable(KEY + "summary_outsider", money(view.balance()));
        name(graphics, summary.getString(), x, top + 20, 0xFFF0D070, right - x);
        switch (mode) {
            case CARD -> renderCard(graphics, x, right);
            case BOARD, MINE -> renderList(graphics, x, right);
            case POST -> {
                if (picker.isOpen()) {
                    picker.render(graphics, font, x, right, controlsY() + 2, bottom());
                } else {
                    renderForm(graphics, x, right);
                }
            }
            case MARKET, HIRE -> market.render(graphics, mode == Mode.MARKET, x, right, controlsY(), bottom());
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = hint();
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private Component hint() {
        return switch (mode) {
            case CARD -> Component.translatable(KEY + "hint.card", TradeTexts.percent(view.commissionPermille()));
            case BOARD -> Component.translatable(KEY + "hint.board", view.maxActive());
            case MINE -> Component.translatable(KEY + "hint.mine");
            case POST -> forKingdom ? Component.translatable(KEY + "hint.post_kingdom", TradeTexts.percent(view.commissionPermille()))
                    : Component.translatable(KEY + "hint.post", TradeTexts.percent(view.postingFeePermille()),
                    TradeTexts.percent(view.commissionPermille()));
            case MARKET, HIRE -> market.hint(mode == Mode.MARKET);
        };
    }

    private void renderCard(GuiGraphics graphics, int x, int right) {
        GuildViews.Card card = view.card();
        int y = controlsY() + 4;
        int step = LINE + 2;
        if (!card.member()) {
            paragraph(graphics, Component.translatable(KEY + "not_member"), x, y, right - x, 4, 0xFFE8E8E8);
            return;
        }
        name(graphics, Component.translatable(KEY + "card.rank", GuildTexts.rank(card.rank()), GuildTexts.points(card.points())).getString(), x, y,
                0xFFFFFFFF, right - x);
        Optional<Rank> next = card.next();
        Component nextLine = next.isEmpty() ? Component.translatable(KEY + "card.top")
                : card.points() >= card.nextPoints() ? Component.translatable(KEY + "card.enough", GuildTexts.rank(next.get()))
                : Component.translatable(KEY + "card.next", GuildTexts.rank(next.get()), GuildTexts.points(card.nextPoints() - card.points()));
        name(graphics, nextLine.getString(), x, y + step, 0xFFE8E8E8, right - x);
        name(graphics, Component.translatable(KEY + "card.stats", GuildTexts.monsters(card.kills()), card.contracts()).getString(), x,
                y + 2 * step, 0xFFD0D0D0, right - x);
        Component exam = switch (card.exam()) {
            case TOO_FEW_POINTS -> Component.translatable(KEY + "exam.too_few", GuildTexts.rank(next.orElse(Rank.SSS)));
            case AVAILABLE -> Component.translatable(KEY + "exam.available", GuildTexts.task(Contract.Kind.EXAM, "", next.orElse(Rank.SSS),
                    card.examBoss(), card.examCount()));
            case COOLDOWN -> Component.translatable(KEY + "exam.cooldown", time(card.examTicks()));
            case UNDER_WAY -> Component.translatable(KEY + "exam.under_way", card.examProgress(), card.examCount(), time(card.examTicks()));
            case BRANCH_TOO_LOW -> Component.translatable(KEY + "exam.branch_low", GuildTexts.rank(next.orElse(Rank.SSS)),
                    GuildTexts.rank(view.branchRank()));
            case TOP, NONE -> Component.translatable(KEY + "exam.none");
        };
        paragraph(graphics, exam, x, y + 3 * step, right - x, 2, 0xFFF0D070);
        if (card.exam() != GuildViews.ExamState.AVAILABLE) {
            name(graphics, Component.translatable(KEY + "card.branch", view.branchName(), GuildTexts.rank(view.branchRank()),
                    TradeTexts.percent(view.commissionPermille())).getString(), x, cardButtonY() + 5, 0xFFB8B8B8, right - x);
        }
    }

    private void renderList(GuiGraphics graphics, int x, int right) {
        List<GuildViews.ContractRow> rows = mode == Mode.BOARD ? board() : view.mine();
        int listTop = listTop();
        if (rows.isEmpty()) {
            paragraph(graphics, Component.translatable(mode == Mode.BOARD ? KEY + "empty.board" : KEY + "empty.mine"), x, listTop + 5, right - x, 2,
                    0xFFB8B8B8);
            return;
        }
        int visible = visibleRows();
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            GuildViews.ContractRow row = rows.get(scroll + i);
            int y = listTop + i * ROW;
            int buttons = buttonsWidth(row);
            int textRight = right - buttons - (buttons > 0 ? 4 : 0);
            graphics.renderItem(row.icon(), x, y + 1);
            int badgeX = x + 20;
            label(graphics, Component.literal(row.minRank().label()), badgeX, y + 5, 0xFF000000 | rankColor(row.minRank()), badgeX + badgeWidth);
            int textX = badgeX + badgeWidth;
            Component right2 = mode == Mode.MINE && (row.mine() || row.party())
                    ? Component.translatable(KEY + "row.progress", row.progress(), row.count(), time(row.ticksLeft()))
                    : Component.translatable(KEY + "row.reward", money(row.payout()));
            int sideWidth = Math.min(font.width(right2), Math.max(0, (textRight - textX) / 2));
            int taskRight = textRight - sideWidth - 6;
            name(graphics, task(row).getString(), textX, y + 5, row.taken() && !row.mine() ? 0xFF9A9A9A : 0xFFFFFFFF, taskRight - textX);
            hoverText(textX, y + 5, Math.max(0, taskRight - textX), details(row));
            name(graphics, right2.getString(), textRight - sideWidth, y + 5, 0xFFF0D070, sideWidth);
        }
        if (rows.size() > visible) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows.size(), scroll + visible), rows.size());
            label(graphics, page, right - font.width(page), listTop + visible * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private int buttonsWidth(GuildViews.ContractRow row) {
        if (mode == Mode.BOARD) {
            return row.canCancel() || !row.taken() ? actionWidth : 0;
        }
        if (row.mine()) {
            return row.kind() == Contract.Kind.GATHER ? 2 * actionWidth + GAP : actionWidth;
        }
        if (row.party() && row.kind() == Contract.Kind.GATHER) {
            return actionWidth;
        }
        return row.canCancel() ? actionWidth : 0;
    }

    private static int rankColor(Rank rank) {
        return io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem.color(rank);
    }

    private void renderForm(GuiGraphics graphics, int x, int right) {
        int labelRight = x + labelWidth - 2;
        label(graphics, Component.translatable(KEY + "form.kind"), x, formY(0) + 5, 0xFFE8E8E8, labelRight);
        label(graphics, Component.translatable(KEY + "form.target"), x, formY(1) + 5, 0xFFE8E8E8, labelRight);
        label(graphics, Component.translatable(KEY + "form.count"), x, formY(2) + 5, 0xFFE8E8E8, labelRight);
        label(graphics, Component.translatable(KEY + "form.min_rank"), x, formY(3) + 5, 0xFFE8E8E8, labelRight);
        int fieldX = x + labelWidth;
        int rewardX = fieldX + BOX + 6;
        label(graphics, Component.translatable(KEY + "form.reward"), rewardX, formY(2) + 5, 0xFFE8E8E8,
                rewardX + font.width(Component.translatable(KEY + "form.reward")) + 4);
        int daysX = fieldX + rankWidth + 6;
        label(graphics, Component.translatable(KEY + "form.days"), daysX, formY(3) + 5, 0xFFE8E8E8,
                daysX + font.width(Component.translatable(KEY + "form.days")) + 4);
        int chosenX = fieldX + (postKind() == Contract.Kind.HUNT ? modeWidth + GAP : 0) + (byRank && postKind() == Contract.Kind.HUNT ? rankWidth
                : chooseWidth) + 4;
        if (!(byRank && postKind() == Contract.Kind.HUNT)) {
            if (target.isEmpty()) {
                name(graphics, Component.translatable(KEY + "form.nothing").getString(), chosenX, formY(1) + 5, 0xFFB8B8B8, right - chosenX);
            } else if (chosenX + 20 < right) {
                graphics.renderItem(targetIcon, chosenX, formY(1) + 1);
                name(graphics, targetName.getString(), chosenX + 18, formY(1) + 5, 0xFFFFFFFF, right - chosenX - 18);
            }
        }
        int previewX = x + squadsWidth + GAP + postWidth + 6;
        Optional<Long> reward = Money.parse(rewardText, view.currency().units());
        if (previewX + 8 < right && !rewardText.isBlank()) {
            String shown = reward.map(value -> money(value).getString()).orElseGet(() -> Component.translatable(KEY + "bad_number").getString());
            name(graphics, shown, previewX, formY(4) + 5, reward.isPresent() ? 0xFFB8B8B8 : 0xFFFF9070, right - previewX);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
