package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.magic.MagicTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The screen of a magic focus: the spells a subject can learn here (with the price of the lesson, the energy of a cast and
 * what the tower and the guild rank must be), and the slots of the staff in the player's hand, which take learned spells.
 * Every button sends an intent; the server checks and answers with a fresh view.
 */
public class MagicFocusScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 246;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int STATUS_LINES = 3;
    private static final String KEY = "kingdomworld.screen.focus.";

    enum Mode {
        LEARN,
        STAFF;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private MagicViews.FocusView view;
    private Mode mode = Mode.LEARN;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int tabWidth;
    private int doneWidth;
    private int learnWidth;
    private int slotWidth;

    public MagicFocusScreen(MagicViews.FocusView view) {
        super(Component.translatable("block.kingdomworld.magic_focus"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "magic-focus-" + mode.id();
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(MagicViews.FocusView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows a view of the screen by name ({@code learn}, {@code staff}); for the layout check.
     */
    public void showForCheck(String value) {
        mode = Mode.valueOf(value.toUpperCase(Locale.ROOT));
        scroll = 0;
    }

    private static Component tabLabel(Mode value) {
        return Component.translatable(KEY + "tab." + value.id());
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view.currency().units());
    }

    private static ItemStack icon(PlayerSpell spell) {
        return switch (spell) {
            case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
            case HEAL -> new ItemStack(Items.GLISTERING_MELON_SLICE);
            case LIGHT -> new ItemStack(Items.GLOWSTONE_DUST);
            case DETECT -> new ItemStack(Items.SPYGLASS);
            case SHIELD -> new ItemStack(Items.SHIELD);
            case BLINK -> new ItemStack(Items.ENDER_PEARL);
        };
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        tabWidth = 0;
        for (Mode value : Mode.values()) {
            tabWidth = Math.max(tabWidth, buttonWidth(tabLabel(value)));
        }
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        learnWidth = buttonWidth(Component.translatable(KEY + "learn"));
        slotWidth = buttonWidth(Component.translatable(KEY + "slot_empty"));
        for (PlayerSpell spell : PlayerSpell.values()) {
            slotWidth = Math.max(slotWidth, buttonWidth(MagicTexts.spell(spell)));
        }
        int tabs = Mode.values().length * (tabWidth + GAP);
        int list = 16 + 4 + 160 + learnWidth;
        int staff = font.width(Component.translatable(KEY + "slot", 9)) + 6 + slotWidth;
        int inner = Math.max(Math.max(tabs, list), staff);
        for (String key : List.of("no_staff", "summary")) {
            inner = Math.max(inner, Math.min(320, font.width(Component.translatable(KEY + key, 0, 0, 0, 0))));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int tabsY() {
        return top() + 20 + 2 * LINE + 4;
    }

    private int listTop() {
        return tabsY() + ROW + 2;
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

    private int rowCount() {
        return mode == Mode.LEARN ? view.lessons().size() : view.staff().size();
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        for (Mode value : Mode.values()) {
            Button tab = addRenderableWidget(Button.builder(tabLabel(value), b -> {
                mode = value;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, tabsY(), tabWidth, ROW - 2).build());
            tab.active = mode != value;
            x += tabWidth + GAP;
        }
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (mode == Mode.LEARN) {
            for (int i = 0; i < rows && scroll + i < view.lessons().size(); i++) {
                MagicViews.Lesson lesson = view.lessons().get(scroll + i);
                if (lesson.learned()) {
                    continue;
                }
                Button learn = addRenderableWidget(Button.builder(Component.translatable(KEY + "learn"),
                        b -> send(MagicPackets.FocusAction.Action.LEARN, lesson.spell().ordinal(), 0))
                        .bounds(right - learnWidth, listTop() + i * ROW, learnWidth, ROW - 2).build());
                learn.active = lesson.refusal().isEmpty();
                learn.setTooltip(Tooltip.create(lesson.refusal().map(KingdomActions::errorMessage)
                        .orElseGet(() -> Component.translatable(KEY + "learn_hint", money(lesson.price())))));
            }
        } else {
            List<PlayerSpell> learned = view.lessons().stream().filter(MagicViews.Lesson::learned).map(MagicViews.Lesson::spell).toList();
            for (int i = 0; i < rows && scroll + i < view.staff().size(); i++) {
                int slot = scroll + i;
                Optional<PlayerSpell> current = view.staff().get(slot);
                Component label = current.<Component>map(MagicTexts::spell).orElseGet(() -> Component.translatable(KEY + "slot_empty"));
                Button button = addRenderableWidget(Button.builder(label, b -> {
                    // Cycles through the learned spells, then an empty slot.
                    int index = current.map(learned::indexOf).orElse(-1);
                    int next = index + 1;
                    send(MagicPackets.FocusAction.Action.BIND, next < learned.size() ? learned.get(next).ordinal() : -1, slot);
                }).bounds(right - slotWidth, listTop() + i * ROW, slotWidth, ROW - 2).build());
                button.active = !learned.isEmpty();
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "slot_hint")));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private void send(MagicPackets.FocusAction.Action action, int spell, int slot) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new MagicPackets.FocusAction(view.pos(), action, spell, slot));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.kingdomColor());
        centeredName(graphics, Component.translatable(KEY + "title", view.kingdomName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.kingdomColor()), panelWidth - 2 * PAD);
        Component summary = Component.translatable(KEY + "summary", view.towerLevel(), GuildTexts.rank(view.rank()),
                MagicTexts.energy(view.carried()), money(view.balance()));
        name(graphics, summary.getString(), x, top + 20, 0xFFF0D070, right - x);
        Component research = view.current().<Component>map(current -> Component.translatable(KEY + "research", MagicTexts.research(current),
                view.percent())).orElseGet(() -> Component.translatable(KEY + "no_research"));
        name(graphics, research.getString(), x, top + 20 + LINE, 0xFFB8B8B8, right - x);
        int rows = visibleRows();
        if (mode == Mode.LEARN) {
            for (int i = 0; i < rows && scroll + i < view.lessons().size(); i++) {
                renderLesson(graphics, x, right, listTop() + i * ROW, view.lessons().get(scroll + i));
            }
        } else if (view.staff().isEmpty()) {
            paragraph(graphics, Component.translatable(KEY + "no_staff"), x, listTop() + 4, right - x, 3, 0xFFB8B8B8);
        } else {
            for (int i = 0; i < rows && scroll + i < view.staff().size(); i++) {
                int y = listTop() + i * ROW;
                graphics.renderItem(new ItemStack(io.github.sekerator.kingdomworld.common.magic.MagicRegistries.STAFF.get()), x, y);
                label(graphics, Component.translatable(KEY + "slot", scroll + i + 1), x + 20, y + 5, 0xFFE8E8E8, right - slotWidth - 4);
            }
        }
        int count = rowCount();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint." + mode.id());
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    private void renderLesson(GuiGraphics graphics, int x, int right, int y, MagicViews.Lesson lesson) {
        int textRight = lesson.learned() ? right : right - learnWidth - 4;
        Component state = lesson.learned() ? Component.translatable(KEY + "learned")
                : Component.translatable(KEY + "needs", money(lesson.price()), GuildTexts.rank(lesson.minRank()), lesson.towerLevel());
        int stateWidth = Math.min(font.width(state), (textRight - x - 20) / 2);
        graphics.renderItem(icon(lesson.spell()), x, y);
        int nameWidth = textRight - x - 20 - stateWidth - 6;
        Component name = MagicTexts.spell(lesson.spell());
        // The hint names the spell in full, so it stays readable when the name is shortened.
        hoverText(x + 20, y + 5, Math.max(0, Math.min(nameWidth, font.width(name))), Component.translatable(KEY + "spell_hint", name,
                MagicTexts.spellHint(lesson.spell()), MagicTexts.energy(lesson.cost())));
        name(graphics, name.getString(), x + 20, y + 5, lesson.learned() ? 0xFFA0E0A0 : 0xFFFFFFFF, nameWidth);
        name(graphics, state.getString(), textRight - stateWidth, y + 5, lesson.learned() ? 0xFFA0E0A0
                : lesson.refusal().isPresent() ? 0xFFFF9070 : 0xFFF0D070, stateWidth);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
