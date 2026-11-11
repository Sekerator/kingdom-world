package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.army.ArmyTexts;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The "Army" tab of the throne screen: the strength and the daily cost of the army, the soldiers with their rank, weapon,
 * armor, wounds and whereabouts, the citizens who can be recruited, the squads and their orders, mercenaries on offer and
 * hired, and the guards of the kingdom's deals. Members with the right to run the army recruit and dismiss, form squads,
 * give orders in place (hold here, guard around, follow me; routes and points come from the marshal's baton), hire and
 * release mercenaries and set guards of deals; the server checks everything.
 */
final class ArmyTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final String KEY = "kingdomworld.screen.army_tab.";

    enum Mode {
        OVERVIEW,
        SOLDIERS,
        RECRUIT,
        SQUADS,
        MERCENARIES,
        DEALS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A row of the list of mercenaries: a header or a band.
     */
    private record BandLine(@Nullable Component header, @Nullable ArmyViews.BandRow band) {
    }

    /**
     * An order the squad being ordered can get.
     */
    private record Choice(ArmyPackets.TabChange.Action action, String key) {
    }

    private static final List<Choice> ORDERS = List.of(new Choice(ArmyPackets.TabChange.Action.ORDER_HOLD_HERE, "order.hold_here"),
            new Choice(ArmyPackets.TabChange.Action.ORDER_GUARD_HERE, "order.guard_here"),
            new Choice(ArmyPackets.TabChange.Action.ORDER_FOLLOW_ME, "order.follow_me"),
            new Choice(ArmyPackets.TabChange.Action.ORDER_NONE, "order.none"),
            new Choice(ArmyPackets.TabChange.Action.DISBAND, "order.disband"));

    private final ThroneScreen screen;
    private ArmyViews.ArmyTabView view;
    private Mode mode = Mode.OVERVIEW;
    private int scroll;
    private final Set<UUID> chosen = new LinkedHashSet<>();
    /** The squad being given an order, 0 for none. */
    private long ordering;
    /** The deal whose guards are being chosen, 0 for none. */
    private long escorting;
    /** The soldier whose branch and post are being chosen, {@code null} for none. */
    @Nullable
    private UUID serving;
    private int serviceWidth;

    private int modeWidth;
    private int formWidth;
    private int dismissWidth;
    private int orderWidth;
    private int warriorWidth;
    private int archerWidth;
    private int hireWidth;
    private int releaseWidth;
    private int guardWidth;
    private int assignWidth;
    private int backWidth;
    private int clearWidth;
    private int pickWidth;

    ArmyTab(ThroneScreen screen) {
        this.screen = screen;
    }

    ArmyViews.ArmyTabView view() {
        return view;
    }

    void setView(ArmyViews.ArmyTabView value) {
        view = value;
        chosen.removeIf(id -> value.soldierRows().stream().noneMatch(row -> row.id().equals(id) && row.squad() == 0));
        if (ordering != 0 && value.squads().stream().noneMatch(squad -> squad.id() == ordering && !squad.onRoad())) {
            ordering = 0;
        }
        if (escorting != 0 && value.deals().stream().noneMatch(deal -> deal.id() == escorting)) {
            escorting = 0;
        }
        if (serving != null && served() == null) {
            serving = null;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
        ordering = 0;
        escorting = 0;
        serving = null;
    }

    /**
     * The soldier whose branch and post are being chosen.
     */
    @Nullable
    private ArmyViews.SoldierRow served() {
        if (view == null || serving == null) {
            return null;
        }
        for (ArmyViews.SoldierRow row : view.soldierRows()) {
            if (row.id().equals(serving)) {
                return row;
            }
        }
        return null;
    }

    /**
     * What may be decided about the service of a soldier: the other branch, and the posts of its branch.
     */
    private static List<Choice> serviceChoices(ArmyViews.SoldierRow row) {
        return List.of(row.branch() == Branch.GUARD ? new Choice(ArmyPackets.TabChange.Action.BRANCH_ARMY, "service.to_army")
                        : new Choice(ArmyPackets.TabChange.Action.BRANCH_GUARD, "service.to_guard"),
                new Choice(ArmyPackets.TabChange.Action.POST_VICE_CAPTAIN, "service.vice_captain"),
                new Choice(ArmyPackets.TabChange.Action.POST_CAPTAIN, "service.captain"),
                new Choice(ArmyPackets.TabChange.Action.POST_FIGHTER, "service.fighter"));
    }

    private static boolean holds(ArmyViews.SoldierRow row, ArmyPackets.TabChange.Action action) {
        return switch (action) {
            case POST_VICE_CAPTAIN -> row.post() == Post.VICE_CAPTAIN;
            case POST_CAPTAIN -> row.post() == Post.CAPTAIN;
            case POST_FIGHTER -> row.post() == Post.FIGHTER;
            default -> false;
        };
    }

    /**
     * Shows the choice of branch and post of a soldier, for the layout check.
     */
    void showServiceForCheck(int index) {
        setMode(Mode.SOLDIERS);
        if (view != null && index < view.soldierRows().size()) {
            serving = view.soldierRows().get(index).id();
        }
    }

    /**
     * Shows a view of the tab, with soldiers chosen, a squad being ordered or a deal getting guards; for the layout check.
     */
    void showForCheck(Mode value, int chosenCount, long orderingSquad, long escortingDeal) {
        setMode(value);
        if (view != null) {
            for (int i = 0; i < chosenCount && i < view.soldierRows().size(); i++) {
                chosen.add(view.soldierRows().get(i).id());
            }
        }
        ordering = orderingSquad;
        escorting = escortingDeal;
    }

    String checkName() {
        return "army-" + mode.id() + (ordering != 0 ? "-order" : "") + (escorting != 0 ? "-escort" : "") + (serving != null ? "-service" : "");
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view.currency().units());
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        formWidth = screen.buttonWidth(Component.translatable(KEY + "form", 32));
        dismissWidth = screen.buttonWidth(Component.translatable(KEY + "dismiss"));
        orderWidth = screen.buttonWidth(Component.translatable(KEY + "order"));
        warriorWidth = screen.buttonWidth(Component.translatable(KEY + "recruit.warrior"));
        archerWidth = screen.buttonWidth(Component.translatable(KEY + "recruit.archer"));
        hireWidth = screen.buttonWidth(Component.translatable(KEY + "hire"));
        releaseWidth = screen.buttonWidth(Component.translatable(KEY + "release"));
        guardWidth = screen.buttonWidth(Component.translatable(KEY + "guard"));
        assignWidth = screen.buttonWidth(Component.translatable(KEY + "assign"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        clearWidth = screen.buttonWidth(Component.translatable(KEY + "clear_guard"));
        pickWidth = screen.buttonWidth(Component.literal("[x]"), Component.literal("[ ]"));
        serviceWidth = screen.buttonWidth(Component.translatable(KEY + "service"));
        int orders = 0;
        for (Choice choice : ORDERS) {
            orders = Math.max(orders, screen.buttonWidth(Component.translatable(KEY + choice.key())));
        }
        for (String key : List.of("service.to_army", "service.to_guard", "service.vice_captain", "service.captain", "service.fighter")) {
            orders = Math.max(orders, screen.buttonWidth(Component.translatable(KEY + key)));
        }
        int rows = 150 + Math.max(Math.max(dismissWidth + GAP + serviceWidth, orderWidth), Math.max(warriorWidth + GAP + archerWidth,
                Math.max(Math.max(hireWidth, releaseWidth), guardWidth)));
        int messages = 0;
        for (String key : List.of("loading", "no_soldiers", "no_candidates", "no_squads", "no_mercenaries", "no_deals")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + Math.max(formWidth, Math.max(backWidth, clearWidth)), orders), Math.max(rows, messages));
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

    private List<BandLine> bandLines() {
        List<BandLine> lines = new ArrayList<>();
        lines.add(new BandLine(Component.translatable(KEY + "offers", view.offers().size()), null));
        for (ArmyViews.BandRow band : view.offers()) {
            lines.add(new BandLine(null, band));
        }
        lines.add(new BandLine(Component.translatable(KEY + "hired_header", ArmyTexts.mercenaries(view.mercenaries()), view.maxMercenaries()),
                null));
        for (ArmyViews.BandRow band : view.hired()) {
            lines.add(new BandLine(null, band));
        }
        return lines;
    }

    private List<ArmyViews.SquadRow> guardCandidates() {
        List<ArmyViews.SquadRow> rows = new ArrayList<>();
        for (ArmyViews.SquadRow row : view.squads()) {
            if (!row.onRoad()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        if (ordering != 0) {
            return ORDERS.size();
        }
        if (escorting != 0) {
            return guardCandidates().size();
        }
        ArmyViews.SoldierRow served = served();
        if (served != null) {
            return serviceChoices(served).size();
        }
        return switch (mode) {
            case OVERVIEW -> 0;
            case SOLDIERS -> view.soldierRows().size();
            case RECRUIT -> view.candidates().size();
            case SQUADS -> view.squads().size();
            case MERCENARIES -> bandLines().size();
            case DEALS -> view.deals().size();
        };
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
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        boolean manage = view.canManage();
        ArmyViews.SoldierRow served = served();
        if (ordering != 0 || escorting != 0 || served != null) {
            screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
                ordering = 0;
                escorting = 0;
                serving = null;
                scroll = 0;
                screen.rebuild();
            }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
        }
        if (served != null) {
            List<Choice> choices = serviceChoices(served);
            for (int i = 0; i < rows && scroll + i < choices.size(); i++) {
                Choice choice = choices.get(scroll + i);
                Button button = screen.add(Button.builder(Component.translatable(KEY + choice.key()),
                                b -> send(choice.action(), 0, 0, List.of(served.id())))
                        .bounds(x, listTop(top) + i * ROW, right - x, ROW - 2).build());
                button.setTooltip(Tooltip.create(Component.translatable(KEY + choice.key() + ".hint")));
                button.active = manage && !holds(served, choice.action());
            }
            return;
        }
        if (ordering != 0) {
            long squad = ordering;
            for (int i = 0; i < rows && scroll + i < ORDERS.size(); i++) {
                Choice choice = ORDERS.get(scroll + i);
                Component label = Component.translatable(KEY + choice.key());
                if (choice.action() == ArmyPackets.TabChange.Action.DISBAND) {
                    label = label.copy().withStyle(ChatFormatting.RED);
                }
                Button button = screen.add(Button.builder(label, b -> send(choice.action(), squad, 0, List.of()))
                        .bounds(x, listTop(top) + i * ROW, right - x, ROW - 2).build());
                button.setTooltip(Tooltip.create(Component.translatable(KEY + choice.key() + ".hint")));
                button.active = manage;
            }
            return;
        }
        if (escorting != 0) {
            long deal = escorting;
            screen.add(Button.builder(Component.translatable(KEY + "clear_guard"), b -> {
                view.deals().stream().filter(row -> row.id() == deal && row.escort() != 0).findFirst()
                        .ifPresent(row -> send(ArmyPackets.TabChange.Action.ESCORT, row.escort(), 0, List.of()));
                escorting = 0;
            }).bounds(right - backWidth - GAP - clearWidth, buttonsY(top), clearWidth, ROW - 2).build()).active = manage
                    && view.deals().stream().anyMatch(row -> row.id() == deal && row.escort() != 0);
            List<ArmyViews.SquadRow> squads = guardCandidates();
            for (int i = 0; i < rows && scroll + i < squads.size(); i++) {
                ArmyViews.SquadRow row = squads.get(scroll + i);
                screen.add(Button.builder(Component.translatable(KEY + "assign"), b -> {
                    send(ArmyPackets.TabChange.Action.ESCORT, row.id(), deal, List.of());
                    escorting = 0;
                }).bounds(right - assignWidth, listTop(top) + i * ROW, assignWidth, ROW - 2).build()).active = manage;
            }
            return;
        }
        switch (mode) {
            case SOLDIERS -> {
                Button form = screen.add(Button.builder(Component.translatable(KEY + "form", chosen.size()), b ->
                        send(ArmyPackets.TabChange.Action.FORM_SQUAD, 0, 0, List.copyOf(chosen))).bounds(right - formWidth, buttonsY(top),
                        formWidth, ROW - 2).build());
                form.active = manage && !chosen.isEmpty();
                form.setTooltip(Tooltip.create(Component.translatable(KEY + "form_hint")));
                for (int i = 0; i < rows && scroll + i < view.soldierRows().size(); i++) {
                    ArmyViews.SoldierRow row = view.soldierRows().get(scroll + i);
                    int y = listTop(top) + i * ROW;
                    if (!manage) {
                        continue;
                    }
                    if (row.squad() == 0) {
                        boolean picked = chosen.contains(row.id());
                        screen.add(Button.builder(Component.literal(picked ? "[x]" : "[ ]"), b -> {
                            if (!chosen.remove(row.id())) {
                                chosen.add(row.id());
                            }
                            screen.rebuild();
                        }).bounds(x, y, pickWidth, ROW - 2).build());
                    }
                    screen.add(Button.builder(Component.translatable(KEY + "dismiss"), b -> send(ArmyPackets.TabChange.Action.DISMISS, 0, 0,
                            List.of(row.id()))).bounds(right - dismissWidth, y, dismissWidth, ROW - 2).build());
                    screen.add(Button.builder(Component.translatable(KEY + "service"), b -> {
                        serving = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - dismissWidth - GAP - serviceWidth, y, serviceWidth, ROW - 2).build())
                            .setTooltip(Tooltip.create(Component.translatable(KEY + "service.hint")));
                }
            }
            case RECRUIT -> {
                for (int i = 0; i < rows && scroll + i < view.candidates().size(); i++) {
                    ArmyViews.CandidateRow row = view.candidates().get(scroll + i);
                    int y = listTop(top) + i * ROW;
                    screen.add(Button.builder(Component.translatable(KEY + "recruit.archer"), b -> send(ArmyPackets.TabChange.Action.RECRUIT_ARCHER,
                            0, 0, List.of(row.id()))).bounds(right - archerWidth, y, archerWidth, ROW - 2).build()).active = manage;
                    screen.add(Button.builder(Component.translatable(KEY + "recruit.warrior"), b -> send(ArmyPackets.TabChange.Action.RECRUIT_WARRIOR,
                            0, 0, List.of(row.id()))).bounds(right - archerWidth - GAP - warriorWidth, y, warriorWidth, ROW - 2).build()).active = manage;
                }
            }
            case SQUADS -> {
                for (int i = 0; i < rows && scroll + i < view.squads().size(); i++) {
                    ArmyViews.SquadRow row = view.squads().get(scroll + i);
                    if (row.onRoad() || !manage) {
                        continue;
                    }
                    screen.add(Button.builder(Component.translatable(KEY + "order"), b -> {
                        ordering = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - orderWidth, listTop(top) + i * ROW, orderWidth, ROW - 2).build());
                }
            }
            case MERCENARIES -> {
                List<BandLine> lines = bandLines();
                for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
                    ArmyViews.BandRow band = lines.get(scroll + i).band();
                    if (band == null) {
                        continue;
                    }
                    int y = listTop(top) + i * ROW;
                    if (band.hired()) {
                        screen.add(Button.builder(Component.translatable(KEY + "release"), b -> send(ArmyPackets.TabChange.Action.RELEASE, band.id(), 0,
                                List.of())).bounds(right - releaseWidth, y, releaseWidth, ROW - 2).build()).active = manage;
                    } else {
                        Button hire = screen.add(Button.builder(Component.translatable(KEY + "hire"), b -> send(ArmyPackets.TabChange.Action.HIRE,
                                band.id(), 0, List.of())).bounds(right - hireWidth, y, hireWidth, ROW - 2).build());
                        hire.active = manage;
                        hire.setTooltip(Tooltip.create(Component.translatable(KEY + "hire_hint", money(band.advance()), view.advanceDays())));
                    }
                }
            }
            case DEALS -> {
                for (int i = 0; i < rows && scroll + i < view.deals().size(); i++) {
                    ArmyViews.DealRow row = view.deals().get(scroll + i);
                    if (!manage || row.onRoad()) {
                        continue;
                    }
                    screen.add(Button.builder(Component.translatable(KEY + "guard"), b -> {
                        escorting = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - guardWidth, listTop(top) + i * ROW, guardWidth, ROW - 2).build());
                }
            }
            default -> {
            }
        }
    }

    private void send(ArmyPackets.TabChange.Action action, long first, long second, List<UUID> citizens) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new ArmyPackets.TabChange(view.kingdomId(), action, first, second, citizens));
        }
        if (action == ArmyPackets.TabChange.Action.FORM_SQUAD) {
            chosen.clear();
        }
        ordering = 0;
        serving = null;
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max = Math.max(0, rowCount() - visibleRows(top, panelHeight));
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draws a text shortened to the width; hovering shows the hint, which starts with the full text, or the full text.
     */
    private void text(GuiGraphics graphics, Component text, int x, int y, int color, int width, @Nullable Component hint) {
        if (width <= 4) {
            return;
        }
        String full = text.getString();
        String shown = screen.fitName(full, width);
        graphics.drawString(screen.font(), shown, x, y, color);
        if (hint != null) {
            screen.hoverText(x, y, Math.max(screen.font().width(shown), 8), hint);
        } else if (!shown.equals(full)) {
            screen.hoverText(x, y, screen.font().width(shown), Component.literal(full));
        }
    }

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        ArmyViews.SoldierRow served = served();
        if (mode == Mode.OVERVIEW && ordering == 0 && escorting == 0 && served == null) {
            renderOverview(graphics, x, right, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (ordering != 0 || escorting != 0 || served != null) {
            Component head = served != null ? Component.translatable(KEY + "serving", served.name(), ArmyTexts.service(served.branch(), served.post()))
                    : ordering != 0 ? Component.translatable(KEY + "ordering", ordering)
                    : Component.translatable(KEY + "escorting", escorting);
            int headX = x + modeWidth + 6;
            int headRight = right - backWidth - 4 - (escorting != 0 ? clearWidth + GAP : 0);
            text(graphics, head, headX, buttonsY(top) + 4, 0xFFF0D070, headRight - headX, null);
        }
        if (count == 0) {
            String key = escorting != 0 ? "no_squads" : switch (mode) {
                case SOLDIERS -> "no_soldiers";
                case RECRUIT -> "no_candidates";
                case SQUADS -> "no_squads";
                case MERCENARIES -> "no_mercenaries";
                default -> "no_deals";
            };
            text(graphics, Component.translatable(KEY + key), x, listTop + 5, 0xFFB8B8B8, right - x, null);
            return;
        }
        for (int i = 0; i < rows && scroll + i < count && ordering == 0 && served == null; i++) {
            int y = listTop + i * ROW;
            if (escorting != 0) {
                renderSquad(graphics, x, right - assignWidth - 4, y, guardCandidates().get(scroll + i));
                continue;
            }
            switch (mode) {
                case SOLDIERS -> renderSoldier(graphics, x, right, y, view.soldierRows().get(scroll + i));
                case RECRUIT -> renderCandidate(graphics, x, right - archerWidth - GAP - warriorWidth - 4, y, view.candidates().get(scroll + i));
                case SQUADS -> {
                    ArmyViews.SquadRow row = view.squads().get(scroll + i);
                    renderSquad(graphics, x, row.onRoad() || !view.canManage() ? right : right - orderWidth - 4, y, row);
                }
                case MERCENARIES -> renderBandLine(graphics, x, right, y, bandLines().get(scroll + i));
                case DEALS -> {
                    ArmyViews.DealRow row = view.deals().get(scroll + i);
                    renderDeal(graphics, x, view.canManage() && !row.onRoad() ? right - guardWidth - 4 : right, y, row);
                }
                default -> {
                }
            }
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private void renderOverview(GuiGraphics graphics, int x, int right, int top) {
        int stateX = x + modeWidth + 6;
        text(graphics, view.canManage() ? Component.translatable(KEY + "can_manage") : Component.translatable(KEY + "read_only"), stateX,
                buttonsY(top) + 4, view.canManage() ? 0xFFA0E0A0 : 0xFFB8B8B8, right - stateX, null);
        List<Component> lines = List.of(
                Component.translatable(KEY + "soldiers", ArmyTexts.soldiers(view.soldiers()), view.maxSoldiers()),
                Component.translatable(KEY + "mercenaries", ArmyTexts.mercenaries(view.mercenaries()), view.maxMercenaries()),
                Component.translatable(KEY + "squads", ArmyTexts.squads(view.squads().size())),
                Component.translatable(KEY + "power", view.power()),
                Component.translatable(KEY + "cost", money(view.soldierCost() + view.mercenaryCost())),
                Component.translatable(KEY + "cost_parts", money(view.soldierCost()), money(view.mercenaryCost())),
                view.barracks() == 0 ? Component.translatable(KEY + "no_barracks")
                        : Component.translatable(KEY + "barracks", view.barracks(), ArmyTexts.soldiers(view.trainingRoom())));
        int y = listTop(top) + 2;
        for (Component line : lines) {
            text(graphics, line, x, y, 0xFFE8E8E8, right - x, null);
            y += 12;
        }
    }

    /**
     * The color of the service of a soldier: the guard, the army, and their officers.
     */
    private static int serviceColor(Branch branch, Post post) {
        return switch (post) {
            case FIGHTER -> branch == Branch.GUARD ? 0xFFA8D8A8 : 0xFF90B8F0;
            case VICE_CAPTAIN -> 0xFFD080F0;
            case CAPTAIN -> 0xFFF0C040;
        };
    }

    private void renderSoldier(GuiGraphics graphics, int x, int right, int y, ArmyViews.SoldierRow row) {
        int nameX = x + pickWidth + 4;
        int buttons = view.canManage() ? dismissWidth + GAP + serviceWidth + 4 : 0;
        int gearX = right - buttons - 44;
        // The branch and the post come first; the rank by experience is in the hint.
        Component service = ArmyTexts.service(row.branch(), row.post());
        int rankWidth = Math.min(screen.font().width(service), 70);
        text(graphics, service, nameX, y + 4, serviceColor(row.branch(), row.post()), rankWidth, null);
        int textX = nameX + rankWidth + 4;
        Component where = row.where();
        int whereColor = row.state() == CitizenState.WOUNDED ? 0xFFFF9070 : 0xFFA0E0A0;
        int room = Math.max(0, gearX - 4 - textX);
        int whereWidth = Math.min(screen.font().width(where), room / 2);
        Component hint = Component.translatable(KEY + "soldier_hint", row.name(), CitizenTexts.profession(row.archer() ? "archer" : "warrior",
                Gender.MALE), service, ArmyTexts.rank(row.rank()), row.experience(), ArmyTexts.enemies(row.kills()), row.health(),
                row.maxHealth(), row.armor());
        text(graphics, row.name(), textX, y + 4, 0xFFFFFFFF, room - whereWidth - 4, hint);
        text(graphics, where, gearX - 4 - whereWidth, y + 4, whereColor, whereWidth, null);
        if (!row.weapon().isEmpty()) {
            graphics.renderItem(row.weapon(), gearX, y);
        }
        text(graphics, Component.translatable(KEY + "armor", row.armor()), gearX + 18, y + 4, 0xFFB8B8B8, 26, null);
    }

    private void renderCandidate(GuiGraphics graphics, int x, int textRight, int y, ArmyViews.CandidateRow row) {
        Component profession = CitizenTexts.profession(row.profession(), row.female() ? Gender.FEMALE : Gender.MALE);
        int professionWidth = Math.min(screen.font().width(profession), Math.max(0, (textRight - x) / 2));
        text(graphics, row.name(), x, y + 4, row.state() == CitizenState.WOUNDED ? 0xFFFF9070 : 0xFFFFFFFF, textRight - x - professionWidth - 6,
                null);
        text(graphics, profession, textRight - professionWidth, y + 4, 0xFFB8B8B8, professionWidth, null);
    }

    private void renderSquad(GuiGraphics graphics, int x, int textRight, int y, ArmyViews.SquadRow row) {
        Component head = Component.translatable(KEY + "squad_head", row.name(), row.mercenaries() ? ArmyTexts.mercenaries(row.members())
                : ArmyTexts.soldiers(row.members()), row.power());
        int headWidth = Math.min(screen.font().width(head), (textRight - x) / 2);
        Component hint = Component.translatable(KEY + "squad_hint", row.name(), row.commander(), row.order());
        text(graphics, head, x, y + 4, row.mercenaries() ? 0xFFD0A0F0 : 0xFFF0D070, headWidth, null);
        Component order = row.onRoad() ? Component.translatable(KEY + "on_road", row.order()) : row.order();
        text(graphics, order, x + headWidth + 6, y + 4, row.onRoad() ? 0xFFF0D070 : 0xFFD0D0D0, textRight - x - headWidth - 6, hint);
    }

    private void renderBandLine(GuiGraphics graphics, int x, int right, int y, BandLine line) {
        if (line.header() != null) {
            text(graphics, line.header(), x, y + 5, 0xFFF0D070, right - x, null);
            return;
        }
        ArmyViews.BandRow band = line.band();
        int buttons = band.hired() ? releaseWidth : hireWidth;
        int textRight = right - buttons - 4;
        if (!band.weapon().isEmpty()) {
            graphics.renderItem(band.weapon(), x, y);
        }
        int textX = x + 20;
        Component price = band.hired() ? Component.translatable(KEY + "days_left", TradeTexts.days(band.daysLeft()), money(band.dailyPrice()))
                : Component.translatable(KEY + "per_day", money(band.dailyPrice()));
        int priceWidth = Math.min(screen.font().width(price), Math.max(0, (textRight - textX) / 2));
        Component head = Component.translatable(KEY + "band_head", band.captain(), ArmyTexts.fighters(band.fighters()), ArmyTexts.rank(band.rank()),
                band.power());
        Component hint = Component.translatable(KEY + "band_hint", band.captain(), ArmyTexts.fighters(band.fighters()), ArmyTexts.rank(band.rank()),
                band.power(), money(band.dailyPrice()), money(band.advance()));
        text(graphics, head, textX, y + 4, 0xFFFFFFFF, textRight - textX - priceWidth - 6, hint);
        text(graphics, price, textRight - priceWidth, y + 4, 0xFFF0D070, priceWidth, null);
    }

    private void renderDeal(GuiGraphics graphics, int x, int textRight, int y, ArmyViews.DealRow row) {
        if (!row.goods().isEmpty()) {
            graphics.renderItem(row.goods(), x, y);
        }
        int textX = x + 20;
        Component guard = row.escort() == 0 ? Component.translatable(KEY + "unguarded", TradeTexts.percent(row.lossPermille()))
                : Component.translatable(KEY + "guarded", row.escort(), TradeTexts.percent(row.lossPermille()));
        int guardWidth = Math.min(screen.font().width(guard), Math.max(0, (textRight - textX) / 2));
        Component deal = Component.translatable(row.selling() ? KEY + "deal_to" : KEY + "deal_from", row.id(), row.count(),
                row.goods().getHoverName(), row.partner());
        Component state = row.onRoad() ? Component.translatable(KEY + "deal_on_road", deal) : deal;
        text(graphics, state, textX, y + 4, 0xFFFFFFFF, textRight - textX - guardWidth - 6, null);
        text(graphics, guard, textRight - guardWidth, y + 4, row.escort() == 0 ? 0xFFFF9070 : 0xFFA0E0A0, guardWidth, null);
    }

    /**
     * The status lines at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (ordering != 0) {
            return Component.translatable(KEY + "hint.order");
        }
        if (escorting != 0) {
            return Component.translatable(KEY + "hint.escort");
        }
        if (serving != null) {
            return Component.translatable(KEY + "hint.service");
        }
        if (!view.canManage()) {
            return Component.translatable(KEY + "read_only_hint");
        }
        return Component.translatable(KEY + "hint." + mode.id());
    }
}
