package io.github.sekerator.kingdomworld.client.screen;


import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The "Guild" tab of the throne screen: the kingdom's branch (registrars and desks, the guild's commission, the daily budget
 * of the guild's own contracts), the crystals in the storehouses and their energy by rank, the adventurers with their rank,
 * gear and whereabouts, the squads and the contracts on the branch's board. Members with the right to manage the guild form
 * and send squads, set the commission and the budget and call off the kingdom's contracts; the server checks everything.
 */
final class GuildTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final int BOX = 64;
    /** Height of a line of crystals. */
    private static final int CELL = 11;
    private static final String KEY = "kingdomworld.screen.guild_tab.";

    enum Mode {
        BRANCH,
        ADVENTURERS,
        SQUADS,
        CONTRACTS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private GuildViews.GuildTabView view;
    private Mode mode = Mode.BRANCH;
    private int scroll;
    private final Set<UUID> chosen = new LinkedHashSet<>();
    /** The squad whose expedition is being chosen, 0 for none. */
    private long sending;
    private String commissionText = "";
    private String budgetText = "";

    private int modeWidth;
    private int setWidth;
    private int formWidth;
    private int sendWidth;
    private int disbandWidth;
    private int goWidth;
    private int backWidth;
    private int cancelWidth;
    private int pickWidth;
    private int labelWidth;

    GuildTab(ThroneScreen screen) {
        this.screen = screen;
    }

    GuildViews.GuildTabView view() {
        return view;
    }

    void setView(GuildViews.GuildTabView value) {
        view = value;
        chosen.removeIf(id -> value.adventurers().stream().noneMatch(row -> row.id().equals(id) && row.squad() == 0));
        if (sending != 0 && value.squads().stream().noneMatch(squad -> squad.id() == sending && !squad.away())) {
            sending = 0;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
        sending = 0;
    }

    /**
     * Shows a view of the tab, with adventurers chosen or a squad's expedition being chosen; for the layout check.
     */
    void showForCheck(Mode value, int chosenCount, long sendingSquad) {
        setMode(value);
        if (view != null) {
            for (int i = 0; i < chosenCount && i < view.adventurers().size(); i++) {
                chosen.add(view.adventurers().get(i).id());
            }
        }
        sending = sendingSquad;
    }

    String checkName() {
        return "guild-" + mode.id() + (sending != 0 ? "-send" : "");
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
        setWidth = screen.buttonWidth(Component.translatable(KEY + "set"));
        formWidth = screen.buttonWidth(Component.translatable(KEY + "form", 16));
        sendWidth = screen.buttonWidth(Component.translatable(KEY + "send"));
        disbandWidth = screen.buttonWidth(Component.translatable(KEY + "disband"));
        goWidth = screen.buttonWidth(Component.translatable(KEY + "go"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        pickWidth = screen.buttonWidth(Component.literal("[x]"), Component.literal("[ ]"));
        labelWidth = 0;
        for (String key : List.of("commission_label", "budget_label")) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(KEY + key)) + 6);
        }
        int form = labelWidth + BOX + GAP + setWidth;
        int rows = 160 + sendWidth + GAP + disbandWidth;
        int messages = 0;
        for (String key : List.of("loading", "no_adventurers", "no_squads", "no_contracts", "no_choice")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + formWidth, form), Math.max(rows, messages));
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

    private List<GuildViews.ContractRow> sendable() {
        List<GuildViews.ContractRow> rows = new ArrayList<>();
        for (GuildViews.ContractRow row : view.contracts()) {
            if (!row.taken() && row.squadsAllowed() && row.kind().byKills()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        if (sending != 0) {
            return sendable().size();
        }
        return switch (mode) {
            case BRANCH -> 0;
            case ADVENTURERS -> view.adventurers().size();
            case SQUADS -> view.squads().size();
            case CONTRACTS -> view.contracts().size();
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
        if (mode == Mode.BRANCH) {
            initBranch(x, top);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (sending != 0) {
            screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
                sending = 0;
                scroll = 0;
                screen.rebuild();
            }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
            List<GuildViews.ContractRow> contracts = sendable();
            for (int i = 0; i < rows && scroll + i < contracts.size(); i++) {
                GuildViews.ContractRow row = contracts.get(scroll + i);
                long squad = sending;
                screen.add(Button.builder(Component.translatable(KEY + "go"), b -> send(GuildPackets.TabChange.Action.SEND, squad, row.id(), List.of()))
                        .bounds(right - goWidth, listTop(top) + i * ROW, goWidth, ROW - 2).build()).active = view.canManage();
            }
            return;
        }
        switch (mode) {
            case ADVENTURERS -> {
                Button form = screen.add(Button.builder(Component.translatable(KEY + "form", chosen.size()), b ->
                        send(GuildPackets.TabChange.Action.FORM_SQUAD, 0, 0, List.copyOf(chosen))).bounds(right - formWidth, buttonsY(top),
                        formWidth, ROW - 2).build());
                form.active = view.canManage() && chosen.size() >= view.minSquad() && chosen.size() <= view.maxSquad();
                form.setTooltip(Tooltip.create(Component.translatable(KEY + "form_hint", view.minSquad(), view.maxSquad())));
                for (int i = 0; i < rows && scroll + i < view.adventurers().size(); i++) {
                    GuildViews.AdventurerRow row = view.adventurers().get(scroll + i);
                    if (row.squad() != 0 || !view.canManage()) {
                        continue;
                    }
                    boolean picked = chosen.contains(row.id());
                    screen.add(Button.builder(Component.literal(picked ? "[x]" : "[ ]"), b -> {
                        if (!chosen.remove(row.id())) {
                            chosen.add(row.id());
                        }
                        screen.rebuild();
                    }).bounds(x, listTop(top) + i * ROW, pickWidth, ROW - 2).build());
                }
            }
            case SQUADS -> {
                for (int i = 0; i < rows && scroll + i < view.squads().size(); i++) {
                    GuildViews.SquadRow row = view.squads().get(scroll + i);
                    if (row.away() || !view.canManage()) {
                        continue;
                    }
                    int y = listTop(top) + i * ROW;
                    screen.add(Button.builder(Component.translatable(KEY + "disband"), b -> send(GuildPackets.TabChange.Action.DISBAND, row.id(), 0,
                            List.of())).bounds(right - disbandWidth, y, disbandWidth, ROW - 2).build());
                    screen.add(Button.builder(Component.translatable(KEY + "send"), b -> {
                        sending = row.id();
                        scroll = 0;
                        screen.rebuild();
                    }).bounds(right - disbandWidth - GAP - sendWidth, y, sendWidth, ROW - 2).build());
                }
            }
            case CONTRACTS -> {
                for (int i = 0; i < rows && scroll + i < view.contracts().size(); i++) {
                    GuildViews.ContractRow row = view.contracts().get(scroll + i);
                    if (row.canCancel()) {
                        screen.add(Button.builder(Component.translatable(KEY + "cancel"), b -> send(GuildPackets.TabChange.Action.CANCEL, row.id(), 0,
                                List.of())).bounds(right - cancelWidth, listTop(top) + i * ROW, cancelWidth, ROW - 2).build());
                    }
                }
            }
            default -> {
            }
        }
    }

    private EditBox box(int x, int y, String value, java.util.function.Consumer<String> responder, Component name) {
        EditBox box = new EditBox(screen.font(), x + 1, y + 1, BOX - 2, ROW - 4, name);
        box.setMaxLength(Money.MAX_TEXT);
        box.setValue(value);
        box.setResponder(responder);
        box.setEditable(view.canManage());
        screen.add(box);
        return box;
    }

    private int branchY(int top, int line) {
        return listTop(top) + line * (ROW + 1);
    }

    private void initBranch(int x, int top) {
        int fieldX = x + labelWidth;
        box(fieldX, branchY(top, 0), commissionText, value -> commissionText = value, Component.translatable(KEY + "commission_label"))
                .setTooltip(Tooltip.create(Component.translatable(KEY + "commission_hint", TradeTexts.percent(view.minCommission()),
                        TradeTexts.percent(view.maxCommission()))));
        screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            try {
                double percent = Double.parseDouble(commissionText.strip().replace(',', '.'));
                send(GuildPackets.TabChange.Action.SET_COMMISSION, Math.max(0, Math.round(percent * 10)), 0, List.of());
            } catch (NumberFormatException e) {
                screen.onResult(false, Component.translatable(KEY + "bad_number"));
            }
        }).bounds(fieldX + BOX + GAP, branchY(top, 0), setWidth, ROW - 2).build()).active = view.canManage();
        box(fieldX, branchY(top, 1), budgetText, value -> budgetText = value, Component.translatable(KEY + "budget_label"))
                .setTooltip(Tooltip.create(Component.translatable(KEY + "budget_hint", money(view.maxBudget()))));
        screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            java.util.Optional<Long> amount = Money.parse(budgetText, view.currency().units());
            if (amount.isEmpty() && budgetText.strip().equals("0")) {
                amount = java.util.Optional.of(0L);
            }
            amount.ifPresentOrElse(value -> send(GuildPackets.TabChange.Action.SET_BUDGET, value, 0, List.of()),
                    () -> screen.onResult(false, Component.translatable(KEY + "bad_number")));
        }).bounds(fieldX + BOX + GAP, branchY(top, 1), setWidth, ROW - 2).build()).active = view.canManage();
    }

    private void send(GuildPackets.TabChange.Action action, long first, long second, List<UUID> citizens) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new GuildPackets.TabChange(view.kingdomId(), action, first, second, citizens));
        }
        if (action == GuildPackets.TabChange.Action.FORM_SQUAD) {
            chosen.clear();
        }
        if (action == GuildPackets.TabChange.Action.SEND) {
            sending = 0;
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

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        if (mode == Mode.BRANCH) {
            renderBranch(graphics, font, x, right, top, panelHeight);
            return;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            String key = sending != 0 ? "no_choice" : switch (mode) {
                case ADVENTURERS -> "no_adventurers";
                case SQUADS -> "no_squads";
                default -> "no_contracts";
            };
            screen.name(graphics, Component.translatable(KEY + key).getString(), x, listTop + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int y = listTop + i * ROW;
            if (sending != 0) {
                renderContract(graphics, x, right - goWidth - 4, y, sendable().get(scroll + i));
            } else {
                switch (mode) {
                    case ADVENTURERS -> renderAdventurer(graphics, x, right, y, view.adventurers().get(scroll + i));
                    case SQUADS -> renderSquad(graphics, x, right, y, view.squads().get(scroll + i));
                    case CONTRACTS -> {
                        GuildViews.ContractRow row = view.contracts().get(scroll + i);
                        renderContract(graphics, x, row.canCancel() ? right - cancelWidth - 4 : right, y, row);
                    }
                    default -> {
                    }
                }
            }
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private void renderBranch(GuiGraphics graphics, Font font, int x, int right, int top, int panelHeight) {
        Component branchRank = GuildTexts.rank(view.branchRank());
        Component state = view.registrars() <= 0 ? Component.translatable(KEY + "branch_closed", branchRank)
                : view.head().isEmpty() ? Component.translatable(KEY + "branch_open", branchRank, view.registrars(), view.desks())
                : Component.translatable(KEY + "branch_open_head", branchRank, view.head(), view.registrars(), view.desks());
        // The state of the branch shares the row of the view button, to leave the height to the crystals.
        int stateX = x + modeWidth + 6;
        screen.name(graphics, state.getString(), stateX, buttonsY(top) + 4, view.registrars() > 0 ? 0xFFA0E0A0 : 0xFFFF9070, right - stateX);
        int labelRight = x + labelWidth - 2;
        int valueX = x + labelWidth + BOX + GAP + setWidth + 6;
        screen.label(graphics, Component.translatable(KEY + "commission_label"), x, branchY(top, 0) + 5, 0xFFE8E8E8, labelRight);
        if (valueX + 8 < right) {
            screen.name(graphics, TradeTexts.percent(view.commissionPermille()).getString(), valueX, branchY(top, 0) + 5, 0xFFF0D070, right - valueX);
        }
        screen.label(graphics, Component.translatable(KEY + "budget_label"), x, branchY(top, 1) + 5, 0xFFE8E8E8, labelRight);
        if (valueX + 8 < right) {
            screen.name(graphics, Component.translatable(KEY + "budget_value", money(view.dailyBudget()), money(view.spentToday())).getString(),
                    valueX, branchY(top, 1) + 5, 0xFFF0D070, right - valueX);
        }
        int y = branchY(top, 2) + 4;
        screen.name(graphics, Component.translatable(KEY + "crystals", view.totalEnergy()).getString(), x, y, 0xFFE8E8E8, right - x);
        // Nine ranks in three rows where the height allows, else in two or in one.
        int room = bottom(top, panelHeight) - 2 - (y + CELL);
        int columns = room >= 3 * CELL ? 3 : room >= 2 * CELL ? 5 : Rank.COUNT;
        int columnWidth = (right - x) / columns;
        for (Rank rank : Rank.values()) {
            int cellX = x + rank.ordinal() % columns * columnWidth;
            int cellY = y + CELL + rank.ordinal() / columns * CELL;
            Component cell = columns == Rank.COUNT ? Component.literal(rank.label() + " " + view.crystals()[rank.ordinal()])
                    : Component.translatable(KEY + "crystal_cell", rank.label(), view.crystals()[rank.ordinal()], view.energy()[rank.ordinal()]);
            screen.name(graphics, cell.getString(), cellX, cellY, 0xFF000000 | MonsterCrystalItem.color(rank), columnWidth - 4);
        }
    }

    private void renderAdventurer(GuiGraphics graphics, int x, int right, int y, GuildViews.AdventurerRow row) {
        int nameX = x + pickWidth + 4;
        int weaponX = right - 80;
        Component where;
        int color;
        if (row.away()) {
            where = Component.translatable(KEY + "away", TradeTexts.ticks(row.ticksLeft()));
            color = 0xFFF0D070;
        } else if (row.state() == CitizenState.WOUNDED) {
            where = Component.translatable(KEY + "wounded");
            color = 0xFFFF9070;
        } else {
            where = row.squad() != 0 ? Component.translatable(KEY + "in_squad", row.squad()) : Component.translatable(KEY + "home");
            color = 0xFFA0E0A0;
        }
        screen.label(graphics, Component.literal(row.rank().label()), nameX, y + 4, 0xFF000000 | MonsterCrystalItem.color(row.rank()),
                nameX + 22);
        int textX = nameX + 22;
        int whereWidth = Math.min(screen.font().width(where), Math.max(0, (weaponX - textX) / 2));
        screen.name(graphics, row.name().getString(), textX, y + 4, 0xFFFFFFFF, weaponX - whereWidth - 6 - textX);
        screen.hoverText(textX, y + 4, Math.max(0, weaponX - whereWidth - 6 - textX), Component.translatable(KEY + "adventurer_hint",
                GuildTexts.points(row.points()), GuildTexts.monsters(row.kills()), row.armor()));
        screen.name(graphics, where.getString(), weaponX - whereWidth - 2, y + 4, color, whereWidth);
        if (!row.weapon().isEmpty()) {
            graphics.renderItem(row.weapon(), weaponX, y);
        }
        screen.name(graphics, Component.translatable(KEY + "armor", row.armor()).getString(), weaponX + 20, y + 4, 0xFFB8B8B8, right - weaponX - 20);
    }

    private void renderSquad(GuiGraphics graphics, int x, int right, int y, GuildViews.SquadRow row) {
        int buttons = row.away() || !view.canManage() ? 0 : sendWidth + GAP + disbandWidth + 4;
        int textRight = right - buttons;
        List<String> names = new ArrayList<>();
        for (Component name : row.members()) {
            names.add(name.getString());
        }
        Component head = Component.translatable(KEY + "squad", row.id(), row.rank().label(), row.power());
        Component state = row.away() ? Component.translatable(KEY + "squad_away", row.task(), TradeTexts.ticks(row.ticksLeft()))
                : Component.translatable(KEY + "squad_home", String.join(", ", names));
        int headWidth = Math.min(screen.font().width(head), (textRight - x) / 2);
        screen.name(graphics, head.getString(), x, y + 4, 0xFFF0D070, headWidth);
        screen.name(graphics, state.getString(), x + headWidth + 6, y + 4, row.away() ? 0xFFF0D070 : 0xFFD0D0D0, textRight - x - headWidth - 6);
        screen.hoverText(x + headWidth + 6, y + 4, Math.max(0, textRight - x - headWidth - 6), Component.literal(String.join(", ", names)));
    }

    private void renderContract(GuiGraphics graphics, int x, int textRight, int y, GuildViews.ContractRow row) {
        graphics.renderItem(row.icon(), x, y);
        int textX = x + 20;
        Component money = Component.translatable(KEY + "reward", money(row.reward()), row.taken() ? Component.translatable(KEY + "taken")
                : Component.empty());
        int moneyWidth = Math.min(screen.font().width(money), Math.max(0, (textRight - textX) / 2));
        Component task = GuildTexts.task(row.kind(), row.target(), row.targetRank(), row.boss(), row.count());
        screen.name(graphics, task.getString(), textX, y + 4, 0xFFFFFFFF, textRight - textX - moneyWidth - 6);
        screen.name(graphics, money.getString(), textRight - moneyWidth, y + 4, 0xFFF0D070, moneyWidth);
    }

    /**
     * The status lines at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (sending != 0) {
            return Component.translatable(KEY + "hint.send", sending);
        }
        return switch (mode) {
            case BRANCH -> Component.translatable(view.canManage() ? KEY + "hint.branch" : KEY + "read_only");
            case ADVENTURERS -> Component.translatable(KEY + "hint.adventurers", view.minSquad(), view.maxSquad());
            case SQUADS -> Component.translatable(KEY + "hint.squads");
            case CONTRACTS -> Component.translatable(KEY + "hint.contracts");
        };
    }
}
