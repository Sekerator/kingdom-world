package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.NobilityViews;
import io.github.sekerator.kingdomworld.common.network.packet.NobilityPackets;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The "Nobility" tab of the throne screen: the houses with their heads, parties and loyalty and the titles to grant; the titles
 * with their fiefs; the court with the stability and its causes, the parties, their demand and the plots found out; the
 * chronicle; and for those who run the spies the secret service with its spies and reports. The server checks every action.
 */
final class NobilityTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final String KEY = "kingdomworld.screen.nobility.";
    private static final String NOBLE = "kingdomworld.nobility.";

    enum Mode {
        HOUSES,
        TITLES,
        COURT,
        CHRONICLE,
        SPIES,
        REPORTS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean secret() {
            return this == SPIES || this == REPORTS;
        }
    }

    private record Act(String key, Runnable action, boolean active) {
    }

    private record Line(Component text, Component hover, int color, List<Act> acts) {
        Line(Component text, int color) {
            this(text, Component.empty(), color, List.of());
        }
    }

    private static final String[] ACTS = {"grant", "revoke", "transfer", "accept", "bargain", "refuse", "execute", "pardon", "exile", "hire", "send",
            "hunt", "rest", "ransom", "dismiss"};

    private final ThroneScreen screen;
    private NobilityViews.TabView view;
    private Mode mode = Mode.HOUSES;
    private int scroll;
    private int title;
    private int fief;
    private int task;
    private int target;
    private int recipient;

    private int modeWidth;
    private int actWidth;
    private int titleWidth;
    private int pickWidth;
    private int taskWidth;

    NobilityTab(ThroneScreen screen) {
        this.screen = screen;
    }

    NobilityViews.TabView view() {
        return view;
    }

    void setView(NobilityViews.TabView value) {
        view = value;
        if (mode.secret() && !value.canSpies()) {
            mode = Mode.HOUSES;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
    }

    String checkName() {
        return "nobility-" + mode.id();
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private Mode nextMode() {
        Mode next = mode;
        do {
            next = Mode.values()[(next.ordinal() + 1) % Mode.values().length];
        } while (next.secret() && (view == null || !view.canSpies()));
        return next;
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        actWidth = 0;
        for (String act : ACTS) {
            actWidth = Math.max(actWidth, screen.buttonWidth(Component.translatable(KEY + "act." + act)));
        }
        titleWidth = 0;
        for (Title value : Title.values()) {
            titleWidth = Math.max(titleWidth, screen.buttonWidth(Component.translatable(KEY + "pick_title", Component.translatable(NOBLE + "title." + value.id()))));
        }
        pickWidth = screen.buttonWidth(Component.translatable(KEY + "pick_fief"), Component.translatable(KEY + "pick_target"),
                Component.translatable(KEY + "pick_recipient"));
        taskWidth = 0;
        for (Spy.Task value : Spy.Task.values()) {
            taskWidth = Math.max(taskWidth, screen.buttonWidth(Component.translatable(KEY + "pick_task", Component.translatable(NOBLE + "task." + value.id()))));
        }
        int messages = 0;
        for (String key : List.of("loading", "no_houses", "no_titles", "no_chronicle", "no_spies", "no_reports")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        int rows = 120 + 3 * actWidth + 2 * GAP;
        int bar = Math.max(titleWidth + pickWidth + 60, taskWidth + pickWidth + 60) + GAP;
        return Math.max(Math.max(modeWidth + 80, rows), Math.max(bar, messages));
    }

    private static int buttonsY(int top) {
        return top + ThroneScreen.CONTENT_TOP;
    }

    private int listTop(int top) {
        return buttonsY(top) + ROW + 4 + (hasBar() ? ROW : 0);
    }

    private boolean hasBar() {
        return view != null && (((mode == Mode.HOUSES || mode == Mode.TITLES) && view.canManage()) || mode == Mode.SPIES);
    }

    private static int bottom(int top, int panelHeight) {
        return top + panelHeight - ThroneScreen.BOTTOM_AREA;
    }

    private int visibleRows(int top, int panelHeight) {
        return Math.max(1, (bottom(top, panelHeight) - listTop(top) - 11) / ROW);
    }

    // ---------------------------------------------------------------- the lines of a mode

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if (view == null) {
            return lines;
        }
        switch (mode) {
            case HOUSES -> houses(lines);
            case TITLES -> titles(lines);
            case COURT -> court(lines);
            case CHRONICLE -> view.chronicle().forEach(line -> lines.add(new Line(line, 0xFFE8E8E8)));
            case SPIES -> spies(lines);
            case REPORTS -> view.reports().forEach(line -> lines.add(new Line(line, 0xFFE8E8E8)));
        }
        return lines;
    }

    private void houses(List<Line> lines) {
        boolean canGrant = view.canManage() && !view.fiefs().isEmpty();
        String fiefCode = view.fiefs().isEmpty() ? "" : view.fiefs().get(Math.floorMod(fief, view.fiefs().size())).id();
        for (NobilityViews.HouseRow house : view.houses()) {
            Component text = Component.translatable(KEY + "house", house.name(), house.head(), Component.translatable(NOBLE + "faction." + house.faction().id()),
                    house.loyalty());
            Component hover = Component.translatable(KEY + "house_hint", house.titles(), house.wealth());
            List<Act> acts = view.canManage() ? List.of(new Act("grant", () -> send(NobilityPackets.TabChange.Action.GRANT, house.id(), title, null, fiefCode),
                    canGrant)) : List.of();
            lines.add(new Line(text, hover, house.loyalty() < 40 ? 0xFFF09050 : 0xFFFFFFFF, acts));
        }
        if (view.canManage()) {
            lines.add(new Line(Component.translatable(KEY + "new_house"), Component.translatable(KEY + "new_house_hint"), 0xFFB8B8B8,
                    List.of(new Act("grant", () -> send(NobilityPackets.TabChange.Action.GRANT, -1, title, null, fiefCode), canGrant))));
            for (NobilityViews.Choice subject : view.subjects()) {
                UUID player = UUID.fromString(subject.id());
                lines.add(new Line(Component.translatable(KEY + "subject", subject.name()), Component.translatable(KEY + "subject_hint"), 0xFFA0E0FF,
                        List.of(new Act("grant", () -> send(NobilityPackets.TabChange.Action.GRANT, 0, title, player, fiefCode), canGrant))));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new Line(Component.translatable(KEY + "no_houses"), 0xFFB8B8B8));
        }
    }

    /**
     * Those a title can be handed to: the houses, then the subjects without a title; an id and a name each.
     */
    private List<NobilityViews.Choice> recipients() {
        List<NobilityViews.Choice> all = new ArrayList<>();
        for (NobilityViews.HouseRow house : view.houses()) {
            all.add(new NobilityViews.Choice("h" + house.id(), house.name()));
        }
        for (NobilityViews.Choice subject : view.subjects()) {
            all.add(new NobilityViews.Choice("p" + subject.id(), subject.name()));
        }
        return all;
    }

    private void titles(List<Line> lines) {
        List<NobilityViews.Choice> to = recipients();
        NobilityViews.Choice chosen = to.isEmpty() ? null : to.get(Math.floorMod(recipient, to.size()));
        for (NobilityViews.GrantRow grant : view.grants()) {
            Component text = Component.translatable(KEY + "grant", Component.translatable(NOBLE + "title." + grant.title().id()), grant.fief(), grant.holder());
            List<Act> acts = new ArrayList<>();
            if (view.canManage()) {
                acts.add(new Act("transfer", () -> {
                    if (chosen != null) {
                        boolean player = chosen.id().startsWith("p");
                        send(NobilityPackets.TabChange.Action.TRANSFER, grant.id(), player ? 0 : Long.parseLong(chosen.id().substring(1)),
                                player ? UUID.fromString(chosen.id().substring(1)) : null, "");
                    }
                }, chosen != null));
                acts.add(new Act("revoke", () -> send(NobilityPackets.TabChange.Action.REVOKE, grant.id(), 0, null, ""), true));
            }
            lines.add(new Line(text, Component.translatable(KEY + (grant.player() ? "grant_player_hint" : "grant_house_hint")),
                    grant.player() ? 0xFFA0E0FF : 0xFFFFFFFF, acts));
        }
        if (lines.isEmpty()) {
            lines.add(new Line(Component.translatable(KEY + "no_titles"), 0xFFB8B8B8));
        }
    }

    static int stabilityColor(int value) {
        return value < 30 ? 0xFFFF5050 : value < 45 ? 0xFFF09050 : value < 70 ? 0xFFE0E0E0 : 0xFF60E060;
    }

    private void court(List<Line> lines) {
        lines.add(new Line(Component.translatable(KEY + "stability", view.stability()), Component.translatable(KEY + "stability_hint"),
                stabilityColor(view.stability()), List.of()));
        if (!view.warning().getString().isEmpty()) {
            lines.add(new Line(view.warning(), 0xFFFF5050));
        }
        for (NobilityViews.CauseRow cause : view.causes()) {
            lines.add(new Line(Component.translatable(KEY + "cause", Component.translatable(NOBLE + "cause." + cause.id()),
                    (cause.amount() > 0 ? "+" : "") + cause.amount()), cause.amount() < 0 ? 0xFFF09050 : 0xFFA0E080));
        }
        for (NobilityViews.FactionRow faction : view.factions()) {
            lines.add(new Line(Component.translatable(KEY + "faction", Component.translatable(NOBLE + "faction." + faction.faction().id()),
                    faction.influence(), faction.content()), Component.translatable(NOBLE + "faction_hint." + faction.faction().id()),
                    faction.content() < 35 ? 0xFFF09050 : 0xFFE8E8E8, List.of()));
        }
        NobilityViews.DemandRow demand = view.demand();
        if (demand.id() > 0) {
            boolean can = view.canManage();
            lines.add(new Line(Component.translatable(KEY + "demand", demand.house(), demand.text()),
                    Component.translatable(KEY + "demand_hint", Component.translatable(NOBLE + "faction." + demand.faction().id()), demand.days()), 0xFFF0D070,
                    List.of(new Act("accept", () -> send(NobilityPackets.TabChange.Action.ANSWER, demand.id(), 0, null, "accept"), can),
                            new Act("bargain", () -> send(NobilityPackets.TabChange.Action.ANSWER, demand.id(), 0, null, "bargain"), can),
                            new Act("refuse", () -> send(NobilityPackets.TabChange.Action.ANSWER, demand.id(), 0, null, "refuse"), can))));
        } else {
            lines.add(new Line(Component.translatable(KEY + "no_demand"), 0xFFB8B8B8));
        }
        for (NobilityViews.PlotRow plot : view.plots()) {
            boolean can = view.canManage();
            Component hint = plot.backer().getString().isEmpty() ? Component.translatable(KEY + "plot_hint")
                    : Component.translatable(KEY + "plot_backed", plot.backer());
            lines.add(new Line(Component.translatable(KEY + "plot", plot.house()), hint, 0xFFFF7070,
                    List.of(new Act("execute", () -> send(NobilityPackets.TabChange.Action.VERDICT, plot.id(), 0, null, "execute"), can),
                            new Act("pardon", () -> send(NobilityPackets.TabChange.Action.VERDICT, plot.id(), 0, null, "pardon"), can),
                            new Act("exile", () -> send(NobilityPackets.TabChange.Action.VERDICT, plot.id(), 0, null, "exile"), can))));
        }
    }

    private void spies(List<Line> lines) {
        lines.add(new Line(Component.translatable(KEY + "service", view.counter(), view.spies().size(), view.maxSpies()), view.hire(), 0xFFF0D070,
                List.of(new Act("hire", () -> send(NobilityPackets.TabChange.Action.HIRE, 0, 0, null, ""), view.spies().size() < view.maxSpies()))));
        UUID aim = view.targets().isEmpty() ? null : UUID.fromString(view.targets().get(Math.floorMod(target, view.targets().size())).id());
        String taskId = Spy.Task.values()[Math.floorMod(task, Spy.Task.values().length)].id();
        for (NobilityViews.SpyRow spy : view.spies()) {
            Component text = Component.translatable(KEY + "spy", spy.name(), spy.skill(), spy.doing());
            boolean home = spy.status() == Spy.Status.IDLE || spy.status() == Spy.Status.COUNTER;
            List<Act> acts = new ArrayList<>();
            acts.add(new Act("send", () -> send(NobilityPackets.TabChange.Action.SEND, spy.id(), 0, aim, taskId), home && aim != null));
            acts.add(spy.status() == Spy.Status.COUNTER
                    ? new Act("rest", () -> send(NobilityPackets.TabChange.Action.STATION, spy.id(), 0, null, ""), true)
                    : new Act("hunt", () -> send(NobilityPackets.TabChange.Action.STATION, spy.id(), 1, null, ""), spy.status() != Spy.Status.CAPTURED));
            acts.add(spy.status() == Spy.Status.CAPTURED
                    ? new Act("ransom", () -> send(NobilityPackets.TabChange.Action.RANSOM, spy.id(), 0, null, ""), true)
                    : new Act("dismiss", () -> send(NobilityPackets.TabChange.Action.DISMISS, spy.id(), 0, null, ""), home));
            lines.add(new Line(text, Component.translatable(KEY + "spy_hint." + spy.status().id()),
                    spy.status() == Spy.Status.CAPTURED ? 0xFFFF7070 : 0xFFFFFFFF, acts));
        }
        if (view.spies().isEmpty()) {
            lines.add(new Line(Component.translatable(KEY + "no_spies"), 0xFFB8B8B8));
        }
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        Mode next = nextMode();
        screen.add(Button.builder(modeLabel(mode), b -> {
            setMode(next);
            screen.rebuild();
        }).bounds(x, buttonsY(top), modeWidth, ROW - 2).build());
        int barY = buttonsY(top) + ROW + 4;
        if (mode == Mode.HOUSES && view.canManage()) {
            Title chosen = Title.values()[Math.floorMod(title, Title.values().length)];
            screen.add(Button.builder(Component.translatable(KEY + "pick_title", Component.translatable(NOBLE + "title." + chosen.id())), b -> {
                title = (title + 1) % Title.values().length;
                screen.rebuild();
            }).bounds(x, barY, titleWidth, ROW - 2).build());
            Button pick = screen.add(Button.builder(Component.translatable(KEY + "pick_fief"), b -> {
                fief++;
                screen.rebuild();
            }).bounds(x + titleWidth + GAP, barY, pickWidth, ROW - 2).build());
            pick.active = view.fiefs().size() > 1;
        } else if (mode == Mode.TITLES && view.canManage()) {
            Button pick = screen.add(Button.builder(Component.translatable(KEY + "pick_recipient"), b -> {
                recipient++;
                screen.rebuild();
            }).bounds(x, barY, pickWidth, ROW - 2).build());
            pick.active = recipients().size() > 1;
        } else if (mode == Mode.SPIES) {
            Spy.Task chosen = Spy.Task.values()[Math.floorMod(task, Spy.Task.values().length)];
            Button pickTask = screen.add(Button.builder(Component.translatable(KEY + "pick_task", Component.translatable(NOBLE + "task." + chosen.id())), b -> {
                task = (task + 1) % Spy.Task.values().length;
                screen.rebuild();
            }).bounds(x, barY, taskWidth, ROW - 2).build());
            pickTask.setTooltip(Tooltip.create(Component.translatable(NOBLE + "task_hint." + chosen.id())));
            Button pick = screen.add(Button.builder(Component.translatable(KEY + "pick_target"), b -> {
                target++;
                screen.rebuild();
            }).bounds(x + taskWidth + GAP, barY, pickWidth, ROW - 2).build());
            pick.active = view.targets().size() > 1;
        }
        List<Line> lines = lines();
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, lines.size() - rows));
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            Line line = lines.get(scroll + i);
            int y = listTop(top) + i * ROW;
            int bx = right - line.acts().size() * (actWidth + GAP) + GAP;
            for (Act act : line.acts()) {
                Button button = screen.add(Button.builder(Component.translatable(KEY + "act." + act.key()), b -> act.action().run())
                        .bounds(bx, y, actWidth, ROW - 2).build());
                button.active = act.active();
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "act_hint." + act.key())));
                bx += actWidth + GAP;
            }
        }
    }

    private void send(NobilityPackets.TabChange.Action action, long first, long second, UUID player, String word) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new NobilityPackets.TabChange(view.kingdomId(), action, first, second, player, word));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max = Math.max(0, lines().size() - visibleRows(top, panelHeight));
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
        if (right - headerX > 8) {
            Component header = view.npc() ? Component.translatable(KEY + "title_npc") : Component.translatable(KEY + "title." + mode.id());
            screen.name(graphics, header.getString(), headerX, buttonsY(top) + 4, 0xFFF0D070, right - headerX);
        }
        int barY = buttonsY(top) + ROW + 4;
        if (mode == Mode.HOUSES && view.canManage()) {
            int at = x + titleWidth + pickWidth + 2 * GAP + 4;
            Component chosen = view.fiefs().isEmpty() ? Component.translatable(KEY + "no_fief")
                    : view.fiefs().get(Math.floorMod(fief, view.fiefs().size())).name();
            if (right - at > 8) {
                screen.name(graphics, chosen.getString(), at, barY + 4, 0xFFE8E8E8, right - at);
            }
        } else if (mode == Mode.TITLES && view.canManage()) {
            int at = x + pickWidth + GAP + 4;
            List<NobilityViews.Choice> to = recipients();
            Component chosen = to.isEmpty() ? Component.translatable(KEY + "no_recipient")
                    : Component.translatable(KEY + "recipient", to.get(Math.floorMod(recipient, to.size())).name());
            if (right - at > 8) {
                screen.name(graphics, chosen.getString(), at, barY + 4, 0xFFE8E8E8, right - at);
            }
        } else if (mode == Mode.SPIES) {
            int at = x + taskWidth + pickWidth + 2 * GAP + 4;
            Component chosen = view.targets().isEmpty() ? Component.translatable(KEY + "no_target")
                    : view.targets().get(Math.floorMod(target, view.targets().size())).name();
            if (right - at > 8) {
                screen.name(graphics, chosen.getString(), at, barY + 4, 0xFFE8E8E8, right - at);
            }
        }
        List<Line> lines = lines();
        int rows = visibleRows(top, panelHeight);
        for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
            Line line = lines.get(scroll + i);
            int y = listTop(top) + i * ROW;
            int textRight = right - line.acts().size() * (actWidth + GAP) - 4;
            if (textRight - x <= 8) {
                continue;
            }
            int width = screen.name(graphics, line.text().getString(), x, y + 4, line.color(), textRight - x);
            if (!line.hover().getString().isEmpty()) {
                screen.hoverText(x, y + 4, width, line.hover());
            }
        }
        if (lines.size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(lines.size(), scroll + rows), lines.size());
            screen.label(graphics, page, right - font.width(page), listTop(top) + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    /**
     * The status line at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (view.npc()) {
            return Component.translatable(KEY + "hint.npc");
        }
        boolean can = mode.secret() ? view.canSpies() : view.canManage();
        return Component.translatable(KEY + "hint." + (can ? mode.id() : "no_rights"));
    }
}
