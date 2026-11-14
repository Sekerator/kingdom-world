package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.common.road.RoadTexts;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Roads" tab of the throne screen: the cities of the kingdom, its roads with their class, length, how much is built
 * and their condition, the requests to cross the land of others, the camps of robbers it knows of and the risks on the
 * ways of its caravans. Members who run construction repair, pause or give up roads and answer requests; those who run
 * the army send squads to patrol a road. Roads are planned on the atlas. The server checks everything.
 */
final class RoadsTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final String KEY = "kingdomworld.screen.roads.";

    enum Mode {
        OVERVIEW,
        CITIES,
        ROADS,
        REQUESTS,
        CAMPS,
        RISKS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What can be done with the road being looked at.
     */
    private enum Choice {
        REPAIR,
        PAUSE,
        PATROL,
        CANCEL
    }

    /**
     * What can be done with the city being looked at.
     */
    private enum CityChoice {
        WALL_PALISADE,
        WALL_STONE,
        GATE_HERE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private RoadViews.RoadTabView view;
    private Mode mode = Mode.OVERVIEW;
    private int scroll;
    /** Where the list was drawn last, for the self-check: the left of its text and its top. */
    private int drawnX;
    private int drawnListTop;
    /** The road whose actions are shown, 0 for none. */
    private long acting;
    /** The road a squad is being chosen for, 0 for none. */
    private long patrolling;
    /** The city whose actions are shown (0 for the capital), -1 for none. */
    private long actingCity = -1;

    private int modeWidth;
    private int actionsWidth;
    private int acceptWidth;
    private int declineWidth;
    private int backWidth;
    private int sendWidth;
    private int planWidth;

    RoadsTab(ThroneScreen screen) {
        this.screen = screen;
    }

    RoadViews.RoadTabView view() {
        return view;
    }

    void setView(RoadViews.RoadTabView value) {
        view = value;
        if (acting != 0 && road(acting) == null) {
            acting = 0;
        }
        if (patrolling != 0 && road(patrolling) == null) {
            patrolling = 0;
        }
        if (actingCity >= 0 && city(actingCity) == null) {
            actingCity = -1;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
        acting = 0;
        patrolling = 0;
        actingCity = -1;
    }

    /**
     * Shows a view of the tab, with the actions of a road or the choice of a squad; for the layout check.
     */
    void showForCheck(Mode value, long actingRoad, long patrollingRoad) {
        setMode(value);
        if (value == Mode.CITIES) {
            // In the list of cities the number is the city whose actions are shown, counted from 1.
            actingCity = actingRoad - 1;
        } else {
            acting = actingRoad;
        }
        patrolling = patrollingRoad;
    }

    String checkName() {
        return "roads-" + mode.id() + (acting != 0 || actingCity >= 0 ? "-actions" : "") + (patrolling != 0 ? "-patrol" : "");
    }

    @Nullable
    private RoadViews.CityRow city(long id) {
        if (view == null) {
            return null;
        }
        for (RoadViews.CityRow row : view.cities()) {
            if (row.id() == id) {
                return row;
            }
        }
        return null;
    }

    private static Component cityChoiceLabel(CityChoice choice) {
        return Component.translatable(KEY + "city_action." + choice.id());
    }

    @Nullable
    private RoadViews.RoadRow road(long id) {
        if (view == null) {
            return null;
        }
        for (RoadViews.RoadRow row : view.roads()) {
            if (row.id() == id) {
                return row;
            }
        }
        return null;
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private static Component choiceLabel(Choice choice, @Nullable RoadViews.RoadRow row) {
        if (choice == Choice.PAUSE && row != null && row.paused()) {
            return Component.translatable(KEY + "action.resume");
        }
        Component label = Component.translatable(KEY + "action." + choice.name().toLowerCase(Locale.ROOT));
        return choice == Choice.CANCEL ? label.copy().withStyle(ChatFormatting.RED) : label;
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        actionsWidth = screen.buttonWidth(Component.translatable(KEY + "actions"));
        acceptWidth = screen.buttonWidth(Component.translatable(KEY + "accept"));
        declineWidth = screen.buttonWidth(Component.translatable(KEY + "decline"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        sendWidth = screen.buttonWidth(Component.translatable(KEY + "send"));
        planWidth = screen.buttonWidth(Component.translatable(KEY + "plan"));
        int choices = 0;
        for (Choice choice : Choice.values()) {
            choices = Math.max(choices, screen.buttonWidth(choiceLabel(choice, null)));
        }
        choices = Math.max(choices, screen.buttonWidth(Component.translatable(KEY + "action.resume")));
        for (CityChoice choice : CityChoice.values()) {
            choices = Math.max(choices, screen.buttonWidth(cityChoiceLabel(choice)));
        }
        int rows = 150 + Math.max(actionsWidth, Math.max(acceptWidth + GAP + declineWidth, sendWidth));
        int messages = 0;
        for (String key : List.of("loading", "no_cities", "no_roads", "no_requests", "no_camps", "no_risks", "no_squads")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + Math.max(backWidth, planWidth), choices), Math.max(rows, messages));
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

    private List<Choice> choices() {
        List<Choice> list = new ArrayList<>();
        RoadViews.RoadRow row = road(acting);
        if (row == null) {
            return list;
        }
        list.add(Choice.REPAIR);
        if (row.ours()) {
            list.add(Choice.PAUSE);
        }
        list.add(Choice.PATROL);
        if (row.ours()) {
            list.add(Choice.CANCEL);
        }
        return list;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        if (patrolling != 0) {
            return view.squads().size();
        }
        if (acting != 0) {
            return choices().size();
        }
        if (actingCity >= 0) {
            return CityChoice.values().length;
        }
        return switch (mode) {
            case OVERVIEW -> 0;
            case CITIES -> view.cities().size();
            case ROADS -> view.roads().size();
            case REQUESTS -> view.crossings().size();
            case CAMPS -> view.camps().size();
            case RISKS -> view.risks().size();
        };
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
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (acting != 0 || patrolling != 0 || actingCity >= 0) {
            screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
                acting = 0;
                patrolling = 0;
                actingCity = -1;
                scroll = 0;
                screen.rebuild();
            }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
        }
        if (patrolling != 0) {
            long road = patrolling;
            for (int i = 0; i < rows && scroll + i < view.squads().size(); i++) {
                RoadViews.SquadRow squad = view.squads().get(scroll + i);
                screen.add(Button.builder(Component.translatable(KEY + "send"), b -> {
                    send(RoadPackets.TabChange.Action.PATROL, road, squad.id());
                    patrolling = 0;
                }).bounds(right - sendWidth, listTop(top) + i * ROW, sendWidth, ROW - 2).build()).active = view.canOrder();
            }
            return;
        }
        if (acting != 0) {
            RoadViews.RoadRow row = road(acting);
            List<Choice> choices = choices();
            for (int i = 0; i < rows && scroll + i < choices.size(); i++) {
                Choice choice = choices.get(scroll + i);
                Button button = screen.add(Button.builder(choiceLabel(choice, row), b -> choose(choice, row))
                        .bounds(x, listTop(top) + i * ROW, right - x, ROW - 2).build());
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "action." + choice.name().toLowerCase(Locale.ROOT) + ".hint")));
                button.active = choice == Choice.PATROL ? view.canOrder() && !view.squads().isEmpty()
                        : view.canManage() && (choice != Choice.REPAIR || row != null && row.worn() > 0);
            }
            return;
        }
        if (actingCity >= 0) {
            RoadViews.CityRow row = city(actingCity);
            long cityId = actingCity;
            CityChoice[] choices = CityChoice.values();
            for (int i = 0; i < rows && scroll + i < choices.length; i++) {
                CityChoice choice = choices[scroll + i];
                Button button = screen.add(Button.builder(cityChoiceLabel(choice), b -> {
                    send(RoadPackets.TabChange.Action.valueOf(choice.name()), cityId, 0);
                    actingCity = -1;
                    screen.rebuild();
                }).bounds(x, listTop(top) + i * ROW, right - x, ROW - 2).build());
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "city_action." + choice.id() + ".hint")));
                button.active = view.canManage() && (choice != CityChoice.GATE_HERE || row != null && row.wallId() != 0);
            }
            return;
        }
        switch (mode) {
            case OVERVIEW -> {
                Button plan = screen.add(Button.builder(Component.translatable(KEY + "plan"),
                        b -> Minecraft.getInstance().setScreen(KingdomMapScreen.forRoads(screen.view())))
                        .bounds(right - planWidth, buttonsY(top), planWidth, ROW - 2).build());
                plan.active = view.canManage();
                plan.setTooltip(Tooltip.create(Component.translatable(KEY + "plan_hint")));
            }
            case ROADS -> {
                for (int i = 0; i < rows && scroll + i < view.roads().size(); i++) {
                    RoadViews.RoadRow row = view.roads().get(scroll + i);
                    screen.add(Button.builder(Component.translatable(KEY + "actions"), b -> {
                        acting = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - actionsWidth, listTop(top) + i * ROW, actionsWidth, ROW - 2).build())
                            .active = view.canManage() || view.canOrder();
                }
            }
            case CITIES -> {
                for (int i = 0; i < rows && scroll + i < view.cities().size(); i++) {
                    RoadViews.CityRow row = view.cities().get(scroll + i);
                    screen.add(Button.builder(Component.translatable(KEY + "actions"), b -> {
                        actingCity = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - actionsWidth, listTop(top) + i * ROW, actionsWidth, ROW - 2).build()).active = view.canManage();
                }
            }
            case REQUESTS -> {
                for (int i = 0; i < rows && scroll + i < view.crossings().size(); i++) {
                    RoadViews.CrossingRow row = view.crossings().get(scroll + i);
                    if (!row.incoming() || row.status() != Crossing.Status.PROPOSED) {
                        continue;
                    }
                    int y = listTop(top) + i * ROW;
                    screen.add(Button.builder(Component.translatable(KEY + "decline"), b -> send(RoadPackets.TabChange.Action.DECLINE, row.id(), 0))
                            .bounds(right - declineWidth, y, declineWidth, ROW - 2).build()).active = view.canManage();
                    screen.add(Button.builder(Component.translatable(KEY + "accept"), b -> send(RoadPackets.TabChange.Action.ACCEPT, row.id(), 0))
                            .bounds(right - declineWidth - GAP - acceptWidth, y, acceptWidth, ROW - 2).build()).active = view.canManage();
                }
            }
            default -> {
            }
        }
    }

    private void choose(Choice choice, @Nullable RoadViews.RoadRow row) {
        if (row == null) {
            return;
        }
        switch (choice) {
            case REPAIR -> send(RoadPackets.TabChange.Action.REPAIR, row.id(), -1);
            case PAUSE -> send(RoadPackets.TabChange.Action.PAUSE, row.id(), 0);
            case CANCEL -> send(RoadPackets.TabChange.Action.CANCEL, row.id(), 0);
            case PATROL -> {
                patrolling = row.id();
                acting = 0;
                scroll = 0;
                screen.rebuild();
                return;
            }
        }
        acting = 0;
    }

    private void send(RoadPackets.TabChange.Action action, long first, long second) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new RoadPackets.TabChange(view.kingdomId(), action, first, second, ""));
        }
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

    /**
     * Where the text of a row of the list was drawn last, {x, y}; for the self-check, which puts the mouse over it.
     */
    int[] rowForCheck(int row) {
        return new int[]{drawnX, drawnListTop + row * ROW + 4};
    }

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        drawnX = x;
        drawnListTop = listTop(top);
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        if (acting != 0 || patrolling != 0) {
            RoadViews.RoadRow row = road(acting != 0 ? acting : patrolling);
            Component head = Component.translatable(patrolling != 0 ? KEY + "patrolling" : KEY + "acting", acting != 0 ? acting : patrolling,
                    row == null ? "" : row.from(), row == null ? "" : row.to());
            int headX = x + modeWidth + 6;
            text(graphics, head, headX, buttonsY(top) + 4, 0xFFF0D070, right - backWidth - 4 - headX, null);
        } else if (actingCity >= 0) {
            RoadViews.CityRow row = city(actingCity);
            Component head = Component.translatable(KEY + "city_actions", row == null ? "" : row.name());
            int headX = x + modeWidth + 6;
            text(graphics, head, headX, buttonsY(top) + 4, 0xFFF0D070, right - backWidth - 4 - headX, null);
        }
        if (mode == Mode.OVERVIEW && acting == 0 && patrolling == 0) {
            renderOverview(graphics, x, right, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            String key = patrolling != 0 ? "no_squads" : switch (mode) {
                case CITIES -> "no_cities";
                case ROADS -> "no_roads";
                case REQUESTS -> "no_requests";
                case CAMPS -> "no_camps";
                default -> "no_risks";
            };
            text(graphics, Component.translatable(KEY + key), x, listTop + 5, 0xFFB8B8B8, right - x, null);
            return;
        }
        for (int i = 0; i < rows && scroll + i < count && acting == 0 && actingCity < 0; i++) {
            int y = listTop + i * ROW;
            if (patrolling != 0) {
                RoadViews.SquadRow squad = view.squads().get(scroll + i);
                text(graphics, Component.translatable(KEY + "squad", squad.name(), squad.members()), x, y + 4, 0xFFF0D070, right - sendWidth - 4 - x, null);
                continue;
            }
            switch (mode) {
                case CITIES -> renderCity(graphics, x, right - actionsWidth - 4, y, view.cities().get(scroll + i));
                case ROADS -> renderRoad(graphics, x, right - actionsWidth - 4, y, view.roads().get(scroll + i));
                case REQUESTS -> {
                    RoadViews.CrossingRow row = view.crossings().get(scroll + i);
                    boolean buttons = row.incoming() && row.status() == Crossing.Status.PROPOSED;
                    renderCrossing(graphics, x, buttons ? right - acceptWidth - GAP - declineWidth - 4 : right, y, row);
                }
                case CAMPS -> renderCamp(graphics, x, right, y, view.camps().get(scroll + i));
                case RISKS -> renderRisk(graphics, x, right, y, view.risks().get(scroll + i));
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
                buttonsY(top) + 4, view.canManage() ? 0xFFA0E0A0 : 0xFFB8B8B8, right - planWidth - 4 - stateX, null);
        long ours = view.roads().stream().filter(RoadViews.RoadRow::ours).count();
        long built = view.roads().stream().filter(row -> row.status() == Road.Status.DONE).count();
        long asked = view.crossings().stream().filter(row -> row.incoming() && row.status() == Crossing.Status.PROPOSED).count();
        long sent = view.crossings().stream().filter(row -> !row.incoming() && row.status() == Crossing.Status.PROPOSED).count();
        int cities = Math.max(0, view.cities().size() - (view.cities().stream().anyMatch(RoadViews.CityRow::capital) ? 1 : 0));
        List<Component> lines = List.of(
                view.maxCities() > 0 ? Component.translatable(KEY + "cities", cities, view.maxCities())
                        : Component.translatable(KEY + "cities_all", cities),
                view.maxRoads() > 0 ? Component.translatable(KEY + "roads", ours, view.maxRoads())
                        : Component.translatable(KEY + "roads_all", ours),
                Component.translatable(KEY + "built", built, view.roads().size()),
                Component.translatable(KEY + "builders", view.builders()),
                Component.translatable(KEY + "requests", asked, sent),
                Component.translatable(KEY + "camps", view.camps().size()),
                Component.translatable(KEY + "plan_hint"));
        int y = listTop(top) + 2;
        for (Component line : lines) {
            text(graphics, line, x, y, 0xFFE8E8E8, right - x, null);
            y += 12;
        }
    }

    private void renderCity(GuiGraphics graphics, int x, int right, int y, RoadViews.CityRow row) {
        Component where = Component.translatable(KEY + "city_where", row.x(), row.y(), row.z(), RoadTexts.roads(row.roads()));
        Component wall = row.wallClass() == null ? Component.translatable(KEY + "no_wall")
                : row.wallBuilt() >= 1000 ? RoadTexts.wallClass(row.wallClass())
                : Component.translatable(KEY + "wall_building", RoadTexts.wallClass(row.wallClass()), TradeTexts.percent(permille(row.wallBuilt())));
        Component state = Component.translatable(KEY + "city_state", RoadTexts.level(row.level()), wall);
        // The townsfolk live in the city as a number beside its named citizens.
        Component people = row.folk() > 0 ? Component.translatable(KEY + "city_people_folk", row.people(), row.folk())
                : Component.literal(Integer.toString(row.people()));
        Component beds = Component.translatable(KEY + "city_beds", row.beds(), row.freeBeds());
        Component hint = Component.translatable(KEY + "city_hint", row.name(), RoadTexts.level(row.level()), people, row.buildings(), beds, wall,
                RoadTexts.gates(row.gates()), where);
        int whereWidth = Math.min(screen.font().width(where), Math.max(0, (right - x) / 4));
        int stateWidth = Math.min(screen.font().width(state), Math.max(0, (right - x - whereWidth) / 2));
        Component name = row.capital() ? Component.translatable(KEY + "capital", row.name()) : Component.literal(row.name());
        text(graphics, name, x, y + 4, row.capital() ? 0xFFF0D070 : 0xFFFFFFFF, right - x - whereWidth - stateWidth - 12, hint);
        text(graphics, state, right - whereWidth - stateWidth - 6, y + 4, row.wallClass() == null ? 0xFFB8B8B8 : 0xFFA0E0A0, stateWidth, hint);
        text(graphics, where, right - whereWidth, y + 4, 0xFFB8B8B8, whereWidth, null);
    }

    private static int permille(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    private void renderRoad(GuiGraphics graphics, int x, int textRight, int y, RoadViews.RoadRow row) {
        Component state;
        int color;
        if (row.status() == Road.Status.WAITING_CONSENT) {
            state = Component.translatable(KEY + "waiting");
            color = 0xFFF0D070;
        } else if (row.paused()) {
            state = Component.translatable(KEY + "paused_state");
            color = 0xFFB8B8B8;
        } else if (row.status() == Road.Status.BUILDING) {
            state = Component.translatable(KEY + "building", TradeTexts.percent(permille(row.built())));
            color = 0xFFF0D070;
        } else {
            state = Component.translatable(KEY + "condition", TradeTexts.percent(permille(row.condition())));
            color = row.condition() < 500 ? 0xFFFF9070 : 0xFFA0E0A0;
        }
        int stateWidth = Math.min(screen.font().width(state), Math.max(0, (textRight - x) / 3));
        Component head = Component.translatable(KEY + "road_head", row.id(), row.from(), row.to());
        Component hint = Component.translatable(KEY + "road_hint", row.id(), row.from(), row.to(), RoadTexts.className(row.roadClass()),
                RoadTexts.blocks(row.length()), RoadTexts.mode(row.mode()), row.partners().isEmpty() ? Component.translatable(KEY + "nobody")
                        : Component.literal(row.partners()), RoadTexts.sections(row.worn()), RoadTexts.sections(row.repairing()),
                RoadTexts.items(row.missing()[0]), RoadTexts.items(row.missing()[1]), RoadTexts.items(row.missing()[2]));
        Component kind = Component.translatable(KEY + "road_kind", RoadTexts.className(row.roadClass()), RoadTexts.blocks(row.length()));
        int kindWidth = Math.min(screen.font().width(kind), Math.max(0, (textRight - x - stateWidth) / 2));
        text(graphics, head, x, y + 4, row.ours() ? 0xFFFFFFFF : 0xFFD0D0F0, textRight - x - stateWidth - kindWidth - 12, hint);
        text(graphics, kind, textRight - stateWidth - kindWidth - 6, y + 4, 0xFFB8B8B8, kindWidth, null);
        text(graphics, state, textRight - stateWidth, y + 4, color, stateWidth, null);
    }

    private void renderCrossing(GuiGraphics graphics, int x, int textRight, int y, RoadViews.CrossingRow row) {
        Component head = Component.translatable(row.incoming() ? KEY + "asks" : KEY + "asked", row.other(), row.roadId(),
                RoadTexts.sections(row.sections()), RoadTexts.mode(row.mode()));
        Component state = row.status() == Crossing.Status.PROPOSED
                ? Component.translatable(KEY + "left", TradeTexts.minutes(Math.max(1, row.ticksLeft() / 1200)))
                : RoadTexts.crossingStatus(row.status());
        int stateWidth = Math.min(screen.font().width(state), Math.max(0, (textRight - x) / 3));
        int color = switch (row.status()) {
            case ACCEPTED -> 0xFFA0E0A0;
            case DECLINED, EXPIRED -> 0xFFFF9070;
            default -> 0xFFF0D070;
        };
        text(graphics, head, x, y + 4, 0xFFFFFFFF, textRight - x - stateWidth - 6, null);
        text(graphics, state, textRight - stateWidth, y + 4, color, stateWidth, null);
    }

    private void renderCamp(GuiGraphics graphics, int x, int right, int y, RoadViews.CampRow row) {
        Component head = Component.translatable(row.leader() ? KEY + "camp_leader" : KEY + "camp", row.x(), row.z(), RoadTexts.bandits(row.bandits()));
        Component tail = Component.translatable(KEY + "camp_tail", RoadTexts.blocks(row.distance()), RoadTexts.items(row.loot()), row.raids());
        int tailWidth = Math.min(screen.font().width(tail), (right - x) / 2);
        text(graphics, head, x, y + 4, 0xFFFF9070, right - x - tailWidth - 6, null);
        text(graphics, tail, right - tailWidth, y + 4, 0xFFB8B8B8, tailWidth, null);
    }

    private void renderRisk(GuiGraphics graphics, int x, int right, int y, RoadViews.RiskRow row) {
        if (!row.goods().isEmpty()) {
            graphics.renderItem(row.goods(), x, y);
        }
        int textX = x + 20;
        Component risk = Component.translatable(row.guarded() ? KEY + "risk_guarded" : KEY + "risk", TradeTexts.percent(permille(row.permille())));
        Component way = Component.translatable(KEY + "way", TradeTexts.percent(permille(row.onRoads())), TradeTexts.minutes(row.minutes()));
        int riskWidth = Math.min(screen.font().width(risk), Math.max(0, (right - textX) / 3));
        int wayWidth = Math.min(screen.font().width(way), Math.max(0, (right - textX - riskWidth) / 2));
        Component deal = Component.translatable(row.selling() ? KEY + "deal_to" : KEY + "deal_from", row.dealId(), row.partner());
        text(graphics, deal, textX, y + 4, 0xFFFFFFFF, right - textX - riskWidth - wayWidth - 12, null);
        text(graphics, way, right - riskWidth - wayWidth - 6, y + 4, 0xFFB8B8B8, wayWidth, null);
        text(graphics, risk, right - riskWidth, y + 4, row.permille() > 100 ? 0xFFFF9070 : 0xFFA0E0A0, riskWidth, null);
    }

    /**
     * The status line at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (patrolling != 0) {
            return Component.translatable(KEY + "hint.patrol");
        }
        if (acting != 0) {
            return Component.translatable(KEY + "hint.actions");
        }
        if (actingCity >= 0) {
            return Component.translatable(KEY + "hint.city_actions");
        }
        if (!view.canManage()) {
            return Component.translatable(KEY + "read_only_hint");
        }
        return Component.translatable(KEY + "hint." + mode.id());
    }
}
