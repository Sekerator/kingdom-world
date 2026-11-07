package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.law.KingdomLaw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The "Law" tab of the throne screen. The court (the king and the chancellor by default) sees the wanted with their crimes
 * and punishment, pardons them, lowers a fine or spares a murderer with a fine, and sets the multiplier of fines, the entry
 * fee of large cities, the reward for the heads of murderers and the ban of slavery. Every member sees their own record
 * here, their reputation, their debt and their confiscated goods. The server checks everything.
 */
final class LawTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final int BOX = 76;
    private static final String KEY = "kingdomworld.screen.law_tab.";

    enum Mode {
        WANTED,
        COURT,
        MINE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private LawViews.TabView view;
    private Mode mode = Mode.WANTED;
    private int scroll;
    /** The wanted player whose fine is being set, or {@code null}. */
    private LawViews.WantedRow editing;
    private String fineText = "";
    private String feeText = "";
    private String bountyText = "";

    private int modeWidth;
    private int pardonWidth;
    private int fineWidth;
    private int setWidth;
    private int cancelWidth;
    private int stepWidth;
    private int slaveryWidth;
    private int windowWidth;

    LawTab(ThroneScreen screen) {
        this.screen = screen;
    }

    LawViews.TabView view() {
        return view;
    }

    void setView(LawViews.TabView value) {
        boolean first = view == null || !view.kingdomId().equals(value.kingdomId());
        view = value;
        if (!value.canManage()) {
            mode = Mode.MINE;
        }
        if (first) {
            feeText = Long.toString(value.entryFee());
            bountyText = Long.toString(value.bounty());
        }
        if (editing != null && value.wanted().stream().noneMatch(row -> row.player().equals(editing.player()))) {
            editing = null;
        }
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
        editing = null;
    }

    /**
     * Opens the form to set the fine of the first wanted player, for the layout check.
     */
    void editFirstForCheck() {
        if (view != null && !view.wanted().isEmpty()) {
            editing = view.wanted().get(0);
            fineText = Long.toString(Math.max(editing.fine(), view.murderFine()));
        }
    }

    String checkName() {
        return "law-" + mode.id() + (editing != null ? "-fine" : "");
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private CurrencyUnits units() {
        return view.currency().units();
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        pardonWidth = screen.buttonWidth(Component.translatable(KEY + "pardon"));
        fineWidth = screen.buttonWidth(Component.translatable(KEY + "fine"), Component.translatable(KEY + "spare"));
        setWidth = screen.buttonWidth(Component.translatable(KEY + "set"));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        stepWidth = screen.buttonWidth(Component.literal("+"), Component.literal("-"));
        slaveryWidth = screen.buttonWidth(Component.translatable(KEY + "ban"), Component.translatable(KEY + "allow"));
        windowWidth = screen.buttonWidth(Component.translatable(KEY + "window"));
        int wanted = 140 + pardonWidth + fineWidth + 2 * GAP;
        int court = 150 + Math.max(BOX + setWidth + GAP, Math.max(2 * stepWidth + GAP, slaveryWidth));
        int form = 90 + BOX + setWidth + cancelWidth + 3 * GAP;
        int messages = 0;
        for (String key : List.of("loading", "no_wanted", "not_wanted")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + windowWidth, wanted), Math.max(Math.max(court, form), messages));
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

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case WANTED -> view.wanted().size();
            case COURT -> 4;
            case MINE -> 4;
        };
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view.canManage()) {
            Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            screen.add(Button.builder(modeLabel(mode), b -> {
                setMode(next);
                screen.rebuild();
            }).bounds(x, buttonsY(top), modeWidth, ROW - 2).build());
        }
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        switch (mode) {
            case WANTED -> initWanted(x, right, top, rows);
            case COURT -> initCourt(right, top);
            case MINE -> screen.add(Button.builder(Component.translatable(KEY + "window"), b -> {
                if (Minecraft.getInstance().getConnection() != null) {
                    KingdomWorldNetwork.sendToServer(new LawPackets.PlayerAction(LawPackets.PlayerAction.Action.OPEN, null, 0));
                }
            }).bounds(right - windowWidth, buttonsY(top), windowWidth, ROW - 2).build());
        }
    }

    private void initWanted(int x, int right, int top, int rows) {
        if (editing != null) {
            int formX = right - cancelWidth - setWidth - BOX - 2 * GAP;
            EditBox box = new EditBox(screen.font(), formX + 1, buttonsY(top) + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "fine_box"));
            box.setMaxLength(Money.MAX_TEXT);
            box.setValue(fineText);
            box.setResponder(value -> fineText = value);
            screen.add(box);
            LawViews.WantedRow target = editing;
            Button set = screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
                Optional<Long> amount = Money.parse(fineText, units());
                if (amount.isPresent()) {
                    send(LawPackets.TabChange.Action.SET_FINE, target.player(), amount.get());
                    editing = null;
                    screen.rebuild();
                }
            }).bounds(right - cancelWidth - setWidth - GAP, buttonsY(top), setWidth, ROW - 2).build());
            set.active = Money.parse(fineText, units()).isPresent();
            screen.add(Button.builder(Component.translatable(KEY + "cancel"), b -> {
                editing = null;
                screen.rebuild();
            }).bounds(right - cancelWidth, buttonsY(top), cancelWidth, ROW - 2).build());
        }
        for (int i = 0; i < rows && scroll + i < view.wanted().size(); i++) {
            LawViews.WantedRow row = view.wanted().get(scroll + i);
            int y = listTop(top) + i * ROW;
            screen.add(Button.builder(Component.translatable(KEY + "pardon"), b -> send(LawPackets.TabChange.Action.PARDON, row.player(), 0))
                    .bounds(right - pardonWidth, y, pardonWidth, ROW - 2).build());
            Button fine = screen.add(Button.builder(Component.translatable(KEY + (row.execution() ? "spare" : "fine")), b -> {
                editing = row;
                fineText = Long.toString(row.execution() ? view.murderFine() : row.fine());
                screen.rebuild();
            }).bounds(right - pardonWidth - fineWidth - GAP, y, fineWidth, ROW - 2).build());
            fine.setTooltip(Tooltip.create(Component.translatable(KEY + (row.execution() ? "spare_hint" : "fine_hint"))));
        }
    }

    private void initCourt(int right, int top) {
        int y = listTop(top);
        int multiplier = view.multiplier();
        Button minus = screen.add(Button.builder(Component.literal("-"), b -> send(LawPackets.TabChange.Action.MULTIPLIER, null,
                Math.max(KingdomLaw.MIN_MULTIPLIER, multiplier - 100))).bounds(right - 2 * stepWidth - GAP, y, stepWidth, ROW - 2).build());
        minus.active = multiplier > KingdomLaw.MIN_MULTIPLIER;
        Button plus = screen.add(Button.builder(Component.literal("+"), b -> send(LawPackets.TabChange.Action.MULTIPLIER, null,
                Math.min(KingdomLaw.MAX_MULTIPLIER, multiplier + 100))).bounds(right - stepWidth, y, stepWidth, ROW - 2).build());
        plus.active = multiplier < KingdomLaw.MAX_MULTIPLIER;
        y += ROW;
        moneyRow(right, y, feeText, value -> feeText = value, LawPackets.TabChange.Action.ENTRY_FEE, view.maxEntryFee(), "fee_box");
        y += ROW;
        moneyRow(right, y, bountyText, value -> bountyText = value, LawPackets.TabChange.Action.BOUNTY, view.maxBounty(), "bounty_box");
        y += ROW;
        screen.add(Button.builder(Component.translatable(KEY + (view.slaveryBanned() ? "allow" : "ban")), b -> send(LawPackets.TabChange.Action.SLAVERY,
                null, view.slaveryBanned() ? 0 : 1)).bounds(right - slaveryWidth, y, slaveryWidth, ROW - 2).build());
    }

    private void moneyRow(int right, int y, String value, java.util.function.Consumer<String> responder, LawPackets.TabChange.Action action, long max,
                          String name) {
        EditBox box = new EditBox(screen.font(), right - setWidth - GAP - BOX + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + name));
        box.setMaxLength(Money.MAX_TEXT);
        box.setValue(value);
        box.setResponder(responder);
        screen.add(box);
        screen.add(Button.builder(Component.translatable(KEY + "set"), b -> {
            Optional<Long> amount = Money.parse(box.getValue(), units());
            if (amount.isPresent() && amount.get() <= max) {
                send(action, null, amount.get());
            }
        }).bounds(right - setWidth, y, setWidth, ROW - 2).build());
    }

    private void send(LawPackets.TabChange.Action action, UUID player, long value) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new LawPackets.TabChange(view.kingdomId(), action, player, value));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        if (mode != Mode.WANTED) {
            return false;
        }
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
        int headerX = view.canManage() ? x + modeWidth + 6 : x;
        switch (mode) {
            case WANTED -> renderWanted(graphics, font, x, right, top, panelHeight, headerX);
            case COURT -> renderCourt(graphics, x, right, top, headerX);
            case MINE -> renderMine(graphics, x, right, top, headerX);
        }
    }

    private void renderWanted(GuiGraphics graphics, Font font, int x, int right, int top, int panelHeight, int headerX) {
        if (editing != null) {
            int formRight = right - cancelWidth - setWidth - BOX - 2 * GAP - 4;
            if (formRight - headerX > 8) {
                screen.name(graphics, Component.translatable(KEY + (editing.execution() ? "spare_for" : "fine_for"), editing.name()).getString(),
                        headerX, buttonsY(top) + 4, 0xFFF0D070, formRight - headerX);
            }
        } else if (right - headerX > 8) {
            screen.name(graphics, Component.translatable(KEY + "wanted_count", view.wanted().size()).getString(), headerX, buttonsY(top) + 4,
                    0xFFF0D070, right - headerX);
        }
        int rows = visibleRows(top, panelHeight);
        int listTop = listTop(top);
        if (view.wanted().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_wanted").getString(), x, listTop + 5, 0xFFB8B8B8, right - x);
            return;
        }
        int buttons = pardonWidth + fineWidth + 2 * GAP + 4;
        for (int i = 0; i < rows && scroll + i < view.wanted().size(); i++) {
            LawViews.WantedRow row = view.wanted().get(scroll + i);
            int y = listTop + i * ROW;
            graphics.renderItem(new ItemStack(Items.PLAYER_HEAD), x, y);
            int textRight = right - buttons;
            Component punishment = LawTexts.punishment(row.execution(), row.fine(), units());
            int punishmentWidth = Math.min(font.width(punishment), (textRight - x - 20) / 2);
            int nameWidth = textRight - x - 20 - punishmentWidth - 6;
            hover(x + 20, y + 4, Math.max(0, Math.min(nameWidth, font.width(row.name()))), details(row));
            screen.name(graphics, row.name(), x + 20, y + 4, row.online() ? 0xFFFFFFFF : 0xFF9A9A9A, nameWidth);
            screen.name(graphics, punishment.getString(), textRight - punishmentWidth, y + 4, row.execution() ? 0xFFFF7070 : 0xFFF0D070,
                    punishmentWidth);
        }
        int count = view.wanted().size();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private void hover(int x, int y, int width, List<Component> lines) {
        for (Component line : lines) {
            screen.hoverText(x, y, width, line);
        }
    }

    /**
     * The record of a wanted player in a hint, a line each: every crime, the court's decision, the bounty and the reputation.
     */
    static List<Component> details(LawViews.WantedRow row, CurrencyUnits units) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "record", row.name()));
        for (LawViews.CrimeRow crime : row.crimes()) {
            lines.add(Component.translatable(KEY + "record_line", LawTexts.crime(crime.crime(), units)));
        }
        if (row.set()) {
            lines.add(Component.translatable(KEY + (row.commuted() ? "spared" : "fine_set")));
        }
        if (row.bounty() > 0) {
            lines.add(Component.translatable(KEY + "bounty_posted", MoneyTexts.amount(row.bounty(), units)));
        }
        lines.add(Component.translatable(KEY + "reputation", LawTexts.reputation(row.reputation())));
        return lines;
    }

    private List<Component> details(LawViews.WantedRow row) {
        return details(row, units());
    }

    private void renderCourt(GuiGraphics graphics, int x, int right, int top, int headerX) {
        if (right - headerX > 8) {
            screen.name(graphics, Component.translatable(KEY + "court_title").getString(), headerX, buttonsY(top) + 4, 0xFFF0D070, right - headerX);
        }
        int y = listTop(top);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "multiplier", view.multiplier() / 10));
        lines.add(Component.translatable(KEY + "entry_fee", view.entryFee() > 0 ? MoneyTexts.amount(view.entryFee(), units())
                : Component.translatable(KEY + "free")));
        lines.add(Component.translatable(view.bountyDefault() ? KEY + "bounty_default" : KEY + "bounty", view.bounty() > 0
                ? MoneyTexts.amount(view.bounty(), units()) : Component.translatable(KEY + "none")));
        lines.add(Component.translatable(KEY + (view.slaveryBanned() ? "slavery_banned" : "slavery_allowed")));
        int[] controls = {2 * stepWidth + GAP, setWidth + GAP + BOX, setWidth + GAP + BOX, slaveryWidth};
        String[] hints = {"multiplier_hint", "entry_fee_hint", "bounty_hint", "slavery_hint"};
        for (int i = 0; i < lines.size(); i++) {
            int textRight = right - controls[i] - 6;
            int width = Math.min(screen.font().width(lines.get(i)), textRight - x);
            screen.hoverText(x, y + 4, Math.max(0, width), Component.translatable(KEY + hints[i]));
            screen.name(graphics, lines.get(i).getString(), x, y + 4, 0xFFE8E8E8, textRight - x);
            y += ROW;
        }
    }

    private void renderMine(GuiGraphics graphics, int x, int right, int top, int headerX) {
        int headerRight = right - windowWidth - 4;
        if (headerRight - headerX > 8) {
            screen.name(graphics, Component.translatable(KEY + "mine_title").getString(), headerX, buttonsY(top) + 4, 0xFFF0D070,
                    headerRight - headerX);
        }
        int y = listTop(top);
        Optional<LawViews.WantedRow> self = view.self();
        if (self.isPresent()) {
            LawViews.WantedRow row = self.get();
            Component line = Component.translatable(KEY + "you_wanted", LawTexts.punishment(row.execution(), row.fine(), units()));
            hover(x, y + 4, Math.min(screen.font().width(line), right - x), details(row));
            screen.name(graphics, line.getString(), x, y + 4, 0xFFFF7070, right - x);
        } else {
            screen.name(graphics, Component.translatable(KEY + "not_wanted").getString(), x, y + 4, 0xFFA0E0A0, right - x);
        }
        y += ROW;
        screen.name(graphics, Component.translatable(KEY + "reputation", LawTexts.reputation(view.reputation())).getString(), x, y + 4,
                0xFFE8E8E8, right - x);
        y += ROW;
        screen.name(graphics, Component.translatable(KEY + "debt", MoneyTexts.amount(view.debt(), units())).getString(), x, y + 4,
                view.debt() > 0 ? 0xFFF0D070 : 0xFFE8E8E8, right - x);
        y += ROW;
        screen.name(graphics, Component.translatable(KEY + "confiscated", view.confiscations()).getString(), x, y + 4,
                view.confiscations() > 0 ? 0xFFF0D070 : 0xFFE8E8E8, right - x);
    }

    /**
     * The status lines at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        return Component.translatable(KEY + "hint." + mode.id());
    }
}
