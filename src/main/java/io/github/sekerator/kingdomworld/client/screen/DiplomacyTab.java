package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.map.ClientProvinceMap;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import io.github.sekerator.kingdomworld.common.network.packet.DiplomacyPackets;
import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The "Diplomacy" tab of the throne screen: how the kingdom stands with every other one and what can be done with it
 * (offers of a pact, an alliance, vassalage or freedom, breaking a treaty, declaring war with a goal, asking it to cede a
 * province that touches our land), offers and calls to war that wait for an answer, the wars of the kingdom with their
 * score, exhaustion, allies and terms of peace, and the chronicle of wars and treaties. The server checks every action.
 */
final class DiplomacyTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final String KEY = "kingdomworld.screen.diplomacy.";
    private static final String WAR = "kingdomworld.war.";
    private static final WarGoal[] DECLARABLE = {WarGoal.TRIBUTE, WarGoal.INDEMNITY, WarGoal.RAID, WarGoal.LAND, WarGoal.CITY, WarGoal.THRONE};

    enum Mode {
        KINGDOMS,
        OFFERS,
        WARS,
        WORLD,
        CHRONICLE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private DiplomacyViews.TabView view;
    private Mode mode = Mode.KINGDOMS;
    private int scroll;
    /** The kingdom whose actions are shown, or {@code null} for the list. */
    private UUID selected;
    private int goal;
    /** Which of the provinces of the open kingdom that may be asked for is chosen. */
    private int cede;

    private int modeWidth;
    private int openWidth;
    private int backWidth;
    private int actionWidth;
    private int answerWidth;
    private int peaceWidth;

    DiplomacyTab(ThroneScreen screen) {
        this.screen = screen;
    }

    DiplomacyViews.TabView view() {
        return view;
    }

    void setView(DiplomacyViews.TabView value) {
        view = value;
        if (selected != null && value.kingdoms().stream().noneMatch(row -> row.id().equals(selected))) {
            selected = null;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
        selected = null;
        cede = 0;
    }

    /**
     * Shows the actions for the first kingdom of the list, for the layout check.
     */
    void selectFirstForCheck() {
        if (view != null && !view.kingdoms().isEmpty()) {
            mode = Mode.KINGDOMS;
            selected = view.kingdoms().get(0).id();
        }
    }

    String checkName() {
        return "diplomacy-" + mode.id() + (selected != null ? "-actions" : "");
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    // ---------------------------------------------------------------- layout

    private static final String[] ACTIONS = {"pact", "alliance", "offer_vassal", "demand_vassal", "release", "break", "declare", "declare_break",
            "ask_cede"};

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        openWidth = screen.buttonWidth(Component.translatable(KEY + "open"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        actionWidth = 0;
        for (String action : ACTIONS) {
            actionWidth = Math.max(actionWidth, screen.buttonWidth(Component.translatable(KEY + "action." + action)));
        }
        for (WarGoal value : DECLARABLE) {
            actionWidth = Math.max(actionWidth, screen.buttonWidth(goalLabel(value)));
        }
        actionWidth = Math.max(actionWidth, screen.buttonWidth(Component.translatable(KEY + "cede_none")));
        answerWidth = screen.buttonWidth(Component.translatable(KEY + "accept"), Component.translatable(KEY + "decline"),
                Component.translatable(KEY + "cancel"), Component.translatable(KEY + "join"), Component.translatable(KEY + "refuse"));
        peaceWidth = screen.buttonWidth(Component.translatable(KEY + "peace_white"), Component.translatable(KEY + "peace_demand"),
                Component.translatable(KEY + "peace_concede"));
        int list = 160 + openWidth;
        int actions = 2 * actionWidth + GAP;
        int offers = 160 + 2 * answerWidth + GAP;
        int wars = 3 * peaceWidth + 2 * GAP;
        int messages = 0;
        for (String key : List.of("loading", "no_kingdoms", "no_offers", "no_wars", "no_world", "no_chronicle")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + backWidth + 60, list), Math.max(Math.max(actions, offers), Math.max(wars, messages)));
    }

    private static Component goalLabel(WarGoal value) {
        return Component.translatable(KEY + "goal_button", Component.translatable(WAR + "goal." + value.id()));
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

    private int visibleRows(int top, int panelHeight) {
        return Math.max(1, (bottom(top, panelHeight) - listTop(top) - 11) / ROW);
    }

    /**
     * Entries of the list of the mode.
     */
    private int itemCount() {
        if (view == null || selected != null) {
            return 0;
        }
        return switch (mode) {
            case KINGDOMS -> view.kingdoms().size();
            case OFFERS -> view.offers().size() + view.calls().size();
            case WARS -> view.wars().size();
            case WORLD -> view.world().size();
            case CHRONICLE -> view.chronicle().size();
        };
    }

    /**
     * Entries that fit: a war takes three rows.
     */
    private int visibleItems(int top, int panelHeight) {
        return Math.max(1, visibleRows(top, panelHeight) / (mode == Mode.WARS ? 3 : 1));
    }

    private Optional<DiplomacyViews.KingdomRow> selectedRow() {
        if (view == null || selected == null) {
            return Optional.empty();
        }
        return view.kingdoms().stream().filter(row -> row.id().equals(selected)).findFirst();
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
        Optional<DiplomacyViews.KingdomRow> chosen = selectedRow();
        if (chosen.isPresent()) {
            screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
                selected = null;
                screen.rebuild();
            }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
            initActions(x, right, top, panelHeight, chosen.get());
            return;
        }
        int rows = visibleItems(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, itemCount() - rows));
        switch (mode) {
            case KINGDOMS -> initKingdoms(right, top, rows);
            case OFFERS -> initOffers(right, top, rows);
            case WARS -> initWars(x, right, top, rows);
            case WORLD, CHRONICLE -> {
            }
        }
    }

    private void initKingdoms(int right, int top, int rows) {
        for (int i = 0; i < rows && scroll + i < view.kingdoms().size(); i++) {
            DiplomacyViews.KingdomRow row = view.kingdoms().get(scroll + i);
            int y = listTop(top) + i * ROW;
            screen.add(Button.builder(Component.translatable(KEY + "open"), b -> {
                selected = row.id();
                cede = 0;
                screen.rebuild();
            }).bounds(right - openWidth, y, openWidth, ROW - 2).build());
        }
    }

    private void initActions(int x, int right, int top, int panelHeight, DiplomacyViews.KingdomRow row) {
        int y = listTop(top) + ROW;
        int column = right - 2 * actionWidth - GAP;
        boolean manage = view.canManage();
        Bond bond = row.bond();
        boolean freeOfVassals = !row.vassalOf() && !row.lordOf();
        action(column, y, "pact", manage && bond == Bond.PEACE, DiplomacyPackets.TabChange.Action.PROPOSE_PACT, row, 0, "");
        action(column + actionWidth + GAP, y, "alliance", manage && (bond == Bond.PEACE || bond == Bond.PACT),
                DiplomacyPackets.TabChange.Action.PROPOSE_ALLIANCE, row, 0, "");
        y += ROW;
        boolean vassalable = manage && freeOfVassals && (bond == Bond.PEACE || bond == Bond.PACT || bond == Bond.ALLIANCE);
        action(column, y, "offer_vassal", vassalable, DiplomacyPackets.TabChange.Action.OFFER_VASSALAGE, row, 0, "");
        action(column + actionWidth + GAP, y, "demand_vassal", vassalable, DiplomacyPackets.TabChange.Action.DEMAND_VASSALAGE, row, 0, "");
        y += ROW;
        action(column, y, "release", manage && row.vassalOf(), DiplomacyPackets.TabChange.Action.ASK_RELEASE, row, 0, "");
        boolean breakable = manage && (bond == Bond.PACT || bond == Bond.ALLIANCE || row.lordOf());
        action(column + actionWidth + GAP, y, "break", breakable, DiplomacyPackets.TabChange.Action.BREAK, row, 0, "");
        y += ROW;
        WarGoal chosen = DECLARABLE[goal % DECLARABLE.length];
        Button goalButton = screen.add(Button.builder(goalLabel(chosen), b -> {
            goal = (goal + 1) % DECLARABLE.length;
            screen.rebuild();
        }).bounds(column, y, actionWidth, ROW - 2).build());
        goalButton.setTooltip(Tooltip.create(Component.translatable(KEY + "goal_hint." + chosen.id())));
        goalButton.active = view.canDeclare();
        boolean declarable = view.canDeclare() && bond != Bond.WAR && bond != Bond.TRUCE && freeOfVassals && row.playable();
        boolean breakFirst = bond == Bond.PACT || bond == Bond.ALLIANCE;
        action(column + actionWidth + GAP, y, breakFirst ? "declare_break" : "declare", declarable,
                DiplomacyPackets.TabChange.Action.DECLARE, row, breakFirst ? 1 : 0, chosen.id());
        y += ROW;
        if (cedeFits(top, panelHeight)) {
            initCede(column, y, row);
        }
    }

    /**
     * Whether the row of the provinces to ask for fits under the actions; where it does not, it is not shown and nothing
     * else gives way to it.
     */
    private static boolean cedeFits(int top, int panelHeight) {
        return listTop(top) + 5 * ROW + ROW - 2 <= bottom(top, panelHeight);
    }

    /**
     * The provinces of the open kingdom that touch the land of ours, one at a time on a button that goes on to the next (its
     * name and price on hover), and the offer to cede the one shown, for the price its ruler asks or for nothing.
     */
    private void initCede(int column, int y, DiplomacyViews.KingdomRow row) {
        List<DiplomacyViews.CedeOption> options = row.cede();
        DiplomacyViews.CedeOption shown = options.isEmpty() ? null : options.get(Math.floorMod(cede, options.size()));
        Component label = shown == null ? Component.translatable(KEY + "cede_none") : Component.literal(provinceName(shown.province()));
        Button province = screen.add(Button.builder(label, b -> {
            cede = options.isEmpty() ? 0 : (Math.floorMod(cede, options.size()) + 1) % options.size();
            screen.rebuild();
        }).bounds(column, y, actionWidth, ROW - 2).build());
        province.active = options.size() > 1;
        province.setTooltip(Tooltip.create(shown == null ? Component.translatable(KEY + "cede_none_hint")
                : Component.translatable(KEY + (options.size() > 1 ? "cede_hint_more" : "cede_hint"), provinceName(shown.province()), shown.cost(),
                options.size())));
        Bond bond = row.bond();
        boolean askable = view.canManage() && shown != null && bond != Bond.WAR && bond != Bond.TRUCE;
        action(column + actionWidth + GAP, y, "ask_cede", askable, DiplomacyPackets.TabChange.Action.ASK_CEDE, row,
                shown == null ? 0 : shown.province(), "");
    }

    /**
     * The name of a province as the atlas writes it, in the alphabet of the language of the game; its number while the
     * provinces are not known yet.
     */
    private static String provinceName(int id) {
        ProvinceSnapshotPacket.Entry entry = ClientProvinceMap.province(id);
        if (entry == null) {
            return "#" + id;
        }
        boolean cyrillic = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected()) == CitizenNames.Alphabet.CYRILLIC;
        return KingdomMapScreen.provinceName(entry.nameSeed(), cyrillic);
    }

    private void action(int x, int y, String key, boolean active, DiplomacyPackets.TabChange.Action action, DiplomacyViews.KingdomRow row,
                        long value, String word) {
        Button button = screen.add(Button.builder(Component.translatable(KEY + "action." + key), b -> send(action, row.id(), value, word))
                .bounds(x, y, actionWidth, ROW - 2).build());
        button.active = active;
        button.setTooltip(Tooltip.create(Component.translatable(KEY + "action_hint." + key)));
    }

    private void initOffers(int right, int top, int rows) {
        List<Object> lines = offerLines();
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            Object line = lines.get(scroll + i);
            int y = listTop(top) + i * ROW;
            int second = right - answerWidth;
            int first = second - answerWidth - GAP;
            if (line instanceof DiplomacyViews.OfferRow offer) {
                if (offer.incoming()) {
                    answer(first, y, "accept", DiplomacyPackets.TabChange.Action.ACCEPT, offer.id());
                    answer(second, y, "decline", DiplomacyPackets.TabChange.Action.DECLINE, offer.id());
                } else {
                    answer(second, y, "cancel", DiplomacyPackets.TabChange.Action.CANCEL, offer.id());
                }
            } else if (line instanceof DiplomacyViews.CallRow call) {
                answer(first, y, "join", DiplomacyPackets.TabChange.Action.JOIN, call.war());
                answer(second, y, "refuse", DiplomacyPackets.TabChange.Action.REFUSE, call.war());
            }
        }
    }

    private List<Object> offerLines() {
        List<Object> lines = new ArrayList<>(view.calls());
        lines.addAll(view.offers());
        return lines;
    }

    private void answer(int x, int y, String key, DiplomacyPackets.TabChange.Action action, long value) {
        Button button = screen.add(Button.builder(Component.translatable(KEY + key), b -> send(action, null, value, ""))
                .bounds(x, y, answerWidth, ROW - 2).build());
        button.active = view.canManage();
    }

    private void initWars(int x, int right, int top, int rows) {
        for (int i = 0; i < rows && scroll + i < view.wars().size(); i++) {
            DiplomacyViews.WarRow war = view.wars().get(scroll + i);
            int y = listTop(top) + (i * 3 + 2) * ROW;
            if (!war.leads() || war.phase() != War.Phase.ACTIVE) {
                continue;
            }
            int bx = right - 3 * peaceWidth - 2 * GAP;
            peace(bx, y, "peace_white", DiplomacyPackets.TabChange.Action.PEACE_WHITE, war, true,
                    Component.translatable(KEY + "peace_white_hint"));
            peace(bx + peaceWidth + GAP, y, "peace_demand", DiplomacyPackets.TabChange.Action.PEACE_DEMAND, war, war.demandOk(),
                    Component.translatable(KEY + "peace_terms_hint", war.demand()));
            peace(bx + 2 * (peaceWidth + GAP), y, "peace_concede", DiplomacyPackets.TabChange.Action.PEACE_CONCEDE, war, true,
                    Component.translatable(KEY + "peace_terms_hint", war.concede()));
        }
    }

    private void peace(int x, int y, String key, DiplomacyPackets.TabChange.Action action, DiplomacyViews.WarRow war, boolean active,
                       Component hint) {
        Button button = screen.add(Button.builder(Component.translatable(KEY + key), b -> send(action, null, war.id(), ""))
                .bounds(x, y, peaceWidth, ROW - 2).build());
        button.active = active && view.canManage();
        button.setTooltip(Tooltip.create(hint));
    }

    private void send(DiplomacyPackets.TabChange.Action action, UUID target, long value, String word) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new DiplomacyPackets.TabChange(view.kingdomId(), action, target, value, word));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        if (selected != null) {
            return false;
        }
        int max = Math.max(0, itemCount() - visibleItems(top, panelHeight));
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
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        int headerX = x + modeWidth + 6;
        Optional<DiplomacyViews.KingdomRow> chosen = selectedRow();
        if (chosen.isPresent()) {
            renderActions(graphics, font, x, right, top, panelHeight, headerX, chosen.get());
            return;
        }
        if (right - headerX > 8) {
            Component header = view.shielded() ? Component.translatable(KEY + "shielded_self") : Component.translatable(KEY + "title." + mode.id());
            screen.name(graphics, header.getString(), headerX, buttonsY(top) + 4, view.shielded() ? 0xFF80C0FF : 0xFFF0D070, right - headerX);
        }
        int rows = visibleItems(top, panelHeight);
        switch (mode) {
            case KINGDOMS -> renderKingdoms(graphics, font, x, right, top, rows);
            case OFFERS -> renderOffers(graphics, font, x, right, top, rows);
            case WARS -> renderWars(graphics, font, x, right, top, rows);
            case WORLD -> renderLines(graphics, x, right, top, rows, view.world(), "no_world");
            case CHRONICLE -> renderLines(graphics, x, right, top, rows, view.chronicle(), "no_chronicle");
        }
        int count = itemCount();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            int pageY = listTop(top) + rows * (mode == Mode.WARS ? 3 : 1) * ROW + 1;
            screen.label(graphics, page, right - font.width(page), pageY, 0xFF9A9A9A, right);
        }
    }

    static int relationColor(int relation) {
        if (relation <= -50) {
            return 0xFFFF5050;
        }
        if (relation <= -15) {
            return 0xFFF09050;
        }
        if (relation < 15) {
            return 0xFFE0E0E0;
        }
        return relation < 50 ? 0xFFA0E080 : 0xFF60E060;
    }

    static int bondColor(Bond bond) {
        return switch (bond) {
            case WAR -> 0xFFFF5050;
            case ALLIANCE -> 0xFF60E060;
            case PACT -> 0xFF80C0FF;
            case VASSAL -> 0xFFD090F0;
            case TRUCE -> 0xFFF0F0F0;
            case PEACE -> 0xFFB8B8B8;
        };
    }

    private Component status(DiplomacyViews.KingdomRow row) {
        Component bond = row.vassalOf() ? Component.translatable(KEY + "our_overlord") : row.lordOf() ? Component.translatable(KEY + "our_vassal")
                : Component.translatable(WAR + "bond." + row.bond().id());
        return Component.translatable(KEY + "status", bond, row.relation());
    }

    private void renderKingdoms(GuiGraphics graphics, Font font, int x, int right, int top, int rows) {
        if (view.kingdoms().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_kingdoms").getString(), x, listTop(top) + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < view.kingdoms().size(); i++) {
            DiplomacyViews.KingdomRow row = view.kingdoms().get(scroll + i);
            int y = listTop(top) + i * ROW;
            graphics.fill(x, y + 3, x + 9, y + 12, 0xFF000000 | row.color());
            if (row.shielded()) {
                graphics.renderOutline(x - 1, y + 2, 11, 11, 0xFF80C0FF);
            }
            int textRight = right - openWidth - 4;
            Component status = status(row);
            int statusWidth = Math.min(font.width(status), (textRight - x - 14) / 2);
            int nameWidth = textRight - x - 14 - statusWidth - 6;
            screen.name(graphics, row.name().getString(), x + 14, y + 4, row.playable() ? 0xFFFFFFFF : 0xFF9A9A9A, nameWidth);
            screen.hoverText(x + 14, y + 4, Math.max(0, Math.min(nameWidth, font.width(row.name()))), details(row));
            screen.name(graphics, status.getString(), textRight - statusWidth, y + 4, row.bond() == Bond.WAR ? 0xFFFF5050 : relationColor(row.relation()),
                    statusWidth);
        }
    }

    private Component details(DiplomacyViews.KingdomRow row) {
        Component reason = row.reason().isEmpty() ? Component.translatable(KEY + "reason.none")
                : Component.translatable(KEY + "reason." + row.reason());
        String extra = row.shielded() ? "details_shielded" : row.playable() ? "details" : "details_away";
        if (row.minutes() >= 0) {
            return Component.translatable(KEY + extra + "_left", reason, io.github.sekerator.kingdomworld.common.trade.TradeTexts.minutes(row.minutes()));
        }
        return Component.translatable(KEY + extra, reason);
    }

    private void renderActions(GuiGraphics graphics, Font font, int x, int right, int top, int panelHeight, int headerX,
                               DiplomacyViews.KingdomRow row) {
        int headerRight = right - backWidth - 4;
        if (headerRight - headerX > 8) {
            screen.name(graphics, row.name().getString(), headerX, buttonsY(top) + 4, 0xFF000000 | Math.max(0x404040, row.color()), headerRight - headerX);
        }
        int y = listTop(top);
        Component status = status(row);
        screen.name(graphics, status.getString(), x, y + 4, row.bond() == Bond.WAR ? 0xFFFF5050 : relationColor(row.relation()), right - x);
        screen.hoverText(x, y + 4, Math.min(font.width(status), right - x), details(row));
        int column = right - 2 * actionWidth - GAP;
        String[] hints = cedeFits(top, panelHeight) ? new String[]{"hint_treaties", "hint_vassals", "hint_break", "hint_war", "hint_cede"}
                : new String[]{"hint_treaties", "hint_vassals", "hint_break", "hint_war"};
        for (int i = 0; i < hints.length; i++) {
            int lineY = y + (i + 1) * ROW + 4;
            if (column - 6 - x > 8) {
                screen.name(graphics, Component.translatable(KEY + hints[i]).getString(), x, lineY, 0xFFB8B8B8, column - 6 - x);
            }
        }
    }

    private void renderOffers(GuiGraphics graphics, Font font, int x, int right, int top, int rows) {
        List<Object> lines = offerLines();
        if (lines.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_offers").getString(), x, listTop(top) + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            Object line = lines.get(scroll + i);
            int y = listTop(top) + i * ROW;
            int textRight = right - 2 * answerWidth - GAP - 4;
            Component text;
            Component hover;
            int color;
            if (line instanceof DiplomacyViews.OfferRow offer) {
                // A province asked for is told by its name and price.
                Component what = offer.kind() == Proposal.Kind.CEDE ? offer.text() : Component.translatable(WAR + "offer." + offer.kind().id());
                text = Component.translatable(KEY + (offer.incoming() ? "offer_in" : "offer_out"), offer.otherName(), what);
                hover = Component.translatable(KEY + "offer_hint", offer.text(), io.github.sekerator.kingdomworld.common.trade.TradeTexts.minutes(
                        offer.minutes()));
                color = offer.incoming() ? 0xFFFFFFFF : 0xFFB8B8B8;
            } else {
                DiplomacyViews.CallRow call = (DiplomacyViews.CallRow) line;
                text = Component.translatable(KEY + "call", call.leader(), call.enemy());
                hover = Component.translatable(KEY + "call_hint");
                color = 0xFFF0D070;
            }
            int width = screen.name(graphics, text.getString(), x, y + 4, color, textRight - x);
            screen.hoverText(x, y + 4, width, hover);
        }
    }

    private void renderWars(GuiGraphics graphics, Font font, int x, int right, int top, int rows) {
        if (view.wars().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_wars").getString(), x, listTop(top) + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < view.wars().size(); i++) {
            DiplomacyViews.WarRow war = view.wars().get(scroll + i);
            int y = listTop(top) + i * 3 * ROW;
            Component goal = Component.translatable(WAR + "goal." + war.goal().id());
            Component when = io.github.sekerator.kingdomworld.common.trade.TradeTexts.minutes(war.minutes());
            Component head = Component.translatable(KEY + (war.phase() == War.Phase.PREPARING ? "war_preparing" : "war_active"), war.enemy(), goal,
                    when);
            int width = screen.name(graphics, head.getString(), x, y + 4, war.phase() == War.Phase.PREPARING ? 0xFFF0D070 : 0xFFFF7070, right - x);
            screen.hoverText(x, y + 4, width, Component.translatable(KEY + "war_allies", war.allies()));
            Component score = Component.translatable(KEY + "war_score", war.ourScore(), war.theirScore(), war.ourExhaustion(), war.theirExhaustion());
            int scoreWidth = screen.name(graphics, score.getString(), x, y + ROW + 4, 0xFFE8E8E8, right - x);
            screen.hoverText(x, y + ROW + 4, scoreWidth, Component.translatable(KEY + "war_score_hint"));
            int lineY = y + 2 * ROW + 4;
            int textRight = war.leads() && war.phase() == War.Phase.ACTIVE ? right - 3 * peaceWidth - 2 * GAP - 4 : right;
            Component state = war.enemyShielded() ? Component.translatable(KEY + "enemy_shielded")
                    : war.usShielded() ? Component.translatable(KEY + "us_shielded")
                    : Component.translatable(KEY + (war.leads() ? "war_lead" : "war_ally"));
            if (textRight - x > 8) {
                screen.name(graphics, state.getString(), x, lineY, war.enemyShielded() || war.usShielded() ? 0xFF80C0FF : 0xFFB8B8B8, textRight - x);
            }
        }
    }

    private void renderLines(GuiGraphics graphics, int x, int right, int top, int rows, java.util.List<Component> lines, String empty) {
        if (lines.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + empty).getString(), x, listTop(top) + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            Component line = lines.get(scroll + i);
            int color = line.getStyle().getColor() != null ? 0xFF000000 | line.getStyle().getColor().getValue() : 0xFFE8E8E8;
            screen.name(graphics, line.getString(), x, listTop(top) + i * ROW + 4, color, right - x);
        }
    }

    /**
     * The status line at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (selected != null) {
            Optional<DiplomacyViews.KingdomRow> row = selectedRow();
            if (row.isPresent() && !row.get().playable() && !row.get().npc()) {
                return Component.translatable(KEY + "nobody_online");
            }
            return Component.translatable(KEY + (view.canManage() || view.canDeclare() ? "hint.actions" : "hint.no_rights"));
        }
        return Component.translatable(KEY + "hint." + mode.id());
    }
}
