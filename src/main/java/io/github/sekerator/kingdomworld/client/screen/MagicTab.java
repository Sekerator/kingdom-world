package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.magic.MagicRegistries;
import io.github.sekerator.kingdomworld.common.magic.MagicTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * The "Magic" tab of the throne screen: the kingdom's store of energy with its capacity, accumulators and the crystals in
 * the storehouses; the journal of energy; the mages; the research of the tower; the spells of the kingdom with their price
 * and switches (and the ward over the chunk the player stands in); the gates; the crystal generators. Members with the
 * right to run the mage tower start research and switch spells; the server checks everything.
 */
final class MagicTab {
    static final int ROW = 18;
    private static final int GAP = 2;
    private static final String KEY = "kingdomworld.screen.magic_tab.";

    enum Mode {
        ENERGY,
        JOURNAL,
        MAGES,
        RESEARCH,
        SPELLS,
        GATES,
        MACHINES;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private MagicViews.TabView view;
    private Mode mode = Mode.ENERGY;
    private int period;
    private int scroll;

    private int modeWidth;
    private int periodWidth;
    private int startWidth;
    private int switchWidth;
    private int wardWidth;

    MagicTab(ThroneScreen screen) {
        this.screen = screen;
    }

    MagicViews.TabView view() {
        return view;
    }

    void setView(MagicViews.TabView value) {
        view = value;
    }

    void setMode(Mode value) {
        mode = value;
        scroll = 0;
    }

    String checkName() {
        return "magic-" + mode.id();
    }

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private static Component periodLabel(int value) {
        return Component.translatable(KEY + "period." + List.of("today", "yesterday", "week").get(value));
    }

    // ---------------------------------------------------------------- icons

    static ItemStack icon(KingdomSpell spell) {
        return switch (spell) {
            case WARD -> new ItemStack(Items.SHIELD);
            case HEALING -> new ItemStack(Items.GOLDEN_APPLE);
            case FERTILITY -> new ItemStack(Items.WHEAT);
            case TAILWIND -> new ItemStack(Items.FEATHER);
            case GATES -> new ItemStack(MagicRegistries.MAGIC_GATE_ITEM.get());
        };
    }

    static ItemStack icon(Research research) {
        for (KingdomSpell spell : KingdomSpell.values()) {
            if (spell.research() == research) {
                return icon(spell);
            }
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    static ItemStack icon(EnergyCategory category) {
        return switch (category) {
            case CRYSTALS -> MonsterCrystalItem.stack(Rank.C, 1);
            case ADMIN -> new ItemStack(Items.COMMAND_BLOCK);
            case RESEARCH -> new ItemStack(Items.BOOK);
            case SPELLS -> new ItemStack(Items.AMETHYST_SHARD);
            case GATES -> new ItemStack(MagicRegistries.MAGIC_GATE_ITEM.get());
            case DEFENSE -> new ItemStack(Items.BLAZE_POWDER);
            case MACHINES -> new ItemStack(MagicRegistries.CRYSTAL_GENERATOR_ITEM.get());
            case MANA -> new ItemStack(Items.LAPIS_LAZULI);
        };
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        periodWidth = 0;
        for (int i = 0; i < 3; i++) {
            periodWidth = Math.max(periodWidth, screen.buttonWidth(periodLabel(i)));
        }
        startWidth = screen.buttonWidth(Component.translatable(KEY + "start"));
        switchWidth = screen.buttonWidth(Component.translatable(KEY + "on"), Component.translatable(KEY + "off"));
        wardWidth = screen.buttonWidth(Component.translatable(KEY + "ward_here"), Component.translatable(KEY + "unward_here"));
        int rows = 180 + Math.max(startWidth, switchWidth);
        int messages = 0;
        for (String key : List.of("loading", "no_mages", "no_gates", "no_generators", "no_journal")) {
            messages = Math.max(messages, Math.min(320, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(modeWidth + GAP + Math.max(periodWidth, wardWidth), rows), messages);
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

    private List<MagicViews.JournalRow> journal() {
        return period < view.journal().size() ? view.journal().get(period) : List.of();
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case ENERGY -> 0;
            case JOURNAL -> journal().size();
            case MAGES -> view.mages().size();
            case RESEARCH -> view.research().size();
            case SPELLS -> view.spells().size();
            case GATES -> view.gates().size();
            case MACHINES -> view.generators().size();
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
        switch (mode) {
            case JOURNAL -> screen.add(Button.builder(periodLabel(period), b -> {
                period = (period + 1) % 3;
                scroll = 0;
                screen.rebuild();
            }).bounds(right - periodWidth, buttonsY(top), periodWidth, ROW - 2).build());
            case RESEARCH -> {
                for (int i = 0; i < rows && scroll + i < view.research().size(); i++) {
                    MagicViews.ResearchRow row = view.research().get(scroll + i);
                    if (row.state() != MagicViews.ResearchState.AVAILABLE) {
                        continue;
                    }
                    Button start = screen.add(Button.builder(Component.translatable(KEY + "start"), b -> send(MagicPackets.TabChange.Action.RESEARCH,
                            row.research().ordinal(), true)).bounds(right - startWidth, listTop(top) + i * ROW, startWidth, ROW - 2).build());
                    start.active = view.canManage() && view.research().stream().noneMatch(r -> r.state() == MagicViews.ResearchState.CURRENT)
                            && view.energy() >= row.cost();
                    start.setTooltip(Tooltip.create(Component.translatable(KEY + "start_hint", MagicTexts.energy(row.cost()),
                            io.github.sekerator.kingdomworld.common.trade.TradeTexts.minutes(row.minutes()))));
                }
            }
            case SPELLS -> {
                if (view.hereOwned() || view.hereWarded()) {
                    Button ward = screen.add(Button.builder(Component.translatable(KEY + (view.hereWarded() ? "unward_here" : "ward_here")),
                            b -> send(MagicPackets.TabChange.Action.WARD_HERE, 0, true)).bounds(right - wardWidth, buttonsY(top), wardWidth, ROW - 2)
                            .build());
                    ward.active = view.canManage();
                    ward.setTooltip(Tooltip.create(Component.translatable(KEY + "ward_hint", view.wardChunks(), view.wardMax())));
                }
                for (int i = 0; i < rows && scroll + i < view.spells().size(); i++) {
                    MagicViews.SpellRow row = view.spells().get(scroll + i);
                    if (!row.researched()) {
                        continue;
                    }
                    Button toggle = screen.add(Button.builder(Component.translatable(KEY + (row.on() ? "off" : "on")),
                            b -> send(MagicPackets.TabChange.Action.SPELL, row.spell().ordinal(), !row.on()))
                            .bounds(right - switchWidth, listTop(top) + i * ROW, switchWidth, ROW - 2).build());
                    toggle.active = view.canManage();
                }
            }
            default -> {
            }
        }
    }

    private void send(MagicPackets.TabChange.Action action, int value, boolean on) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new MagicPackets.TabChange(view.kingdomId(), action, value, on));
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
        if (mode == Mode.ENERGY) {
            renderEnergy(graphics, x, right, top);
            return;
        }
        int headerX = x + modeWidth + 6;
        int headerRight = switch (mode) {
            case JOURNAL -> right - periodWidth - 4;
            case SPELLS -> view.hereOwned() || view.hereWarded() ? right - wardWidth - 4 : right;
            default -> right;
        };
        Component header = switch (mode) {
            case RESEARCH -> Component.translatable(KEY + "tower_level", view.towerLevel());
            case MACHINES -> Component.translatable(KEY + "fe_rate", view.feRate(), view.feMaxPerTick());
            case SPELLS -> Component.translatable(KEY + "ward_count", MagicTexts.chunks(view.wardChunks()), view.wardMax());
            case GATES -> Component.translatable(KEY + "gates_count", view.gates().size());
            case MAGES -> Component.translatable(KEY + "mages_count", view.mages().size());
            default -> Component.empty();
        };
        if (headerRight - headerX > 8) {
            screen.name(graphics, header.getString(), headerX, buttonsY(top) + 4, 0xFFF0D070, headerRight - headerX);
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            String key = switch (mode) {
                case JOURNAL -> "no_journal";
                case MAGES -> "no_mages";
                case GATES -> "no_gates";
                default -> "no_generators";
            };
            screen.name(graphics, Component.translatable(KEY + key).getString(), x, listTop + 5, 0xFFB8B8B8, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int y = listTop + i * ROW;
            switch (mode) {
                case JOURNAL -> renderJournal(graphics, x, right, y, journal().get(scroll + i));
                case MAGES -> renderMage(graphics, x, right, y, view.mages().get(scroll + i));
                case RESEARCH -> renderResearch(graphics, x, right, y, view.research().get(scroll + i));
                case SPELLS -> renderSpell(graphics, x, right, y, view.spells().get(scroll + i));
                case GATES, MACHINES -> renderDevice(graphics, x, right, y, (mode == Mode.GATES ? view.gates() : view.generators()).get(scroll + i));
                default -> {
                }
            }
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    private void line(GuiGraphics graphics, ItemStack icon, Component text, int x, int right, int y, int color) {
        graphics.renderItem(icon, x, y);
        screen.name(graphics, text.getString(), x + 20, y + 4, color, right - x - 20);
    }

    private void renderEnergy(GuiGraphics graphics, int x, int right, int top) {
        // The foci and the level of the tower share the row of the view button, to leave the height to the store.
        int headerX = x + modeWidth + 6;
        if (right - headerX > 8) {
            screen.name(graphics, Component.translatable(KEY + "foci", view.foci(), view.towerLevel()).getString(), headerX, buttonsY(top) + 4,
                    0xFFF0D070, right - headerX);
        }
        int y = listTop(top);
        line(graphics, new ItemStack(MagicRegistries.MAGIC_FOCUS_ITEM.get()), Component.translatable(KEY + "energy", MagicTexts.energy(view.energy()),
                view.capacity()), x, right, y, 0xFFF0D070);
        int barY = y + ROW;
        int barRight = right;
        graphics.fill(x, barY, barRight, barY + 6, 0xFF303030);
        double share = view.capacity() <= 0 ? 0.0 : Math.min(1.0, view.energy() / (double) view.capacity());
        graphics.fill(x, barY, x + (int) Math.round((barRight - x) * share), barY + 6, 0xFF9B59D0);
        y = barY + 10;
        line(graphics, new ItemStack(MagicRegistries.CRYSTAL_ACCUMULATOR_ITEM.get()), Component.translatable(KEY + "accumulators",
                view.accumulators()), x, right, y, 0xFFE8E8E8);
        y += ROW;
        line(graphics, MonsterCrystalItem.stack(Rank.C, 1), Component.translatable(KEY + "crystals", view.crystals(),
                MagicTexts.energy(view.crystalEnergy())), x, right, y, 0xFFE8E8E8);
    }

    private void renderJournal(GuiGraphics graphics, int x, int right, int y, MagicViews.JournalRow row) {
        Component amounts = Component.translatable(KEY + "journal_amounts", row.income(), row.expense());
        int amountsWidth = Math.min(screen.font().width(amounts), (right - x - 20) / 2);
        graphics.renderItem(icon(row.category()), x, y);
        screen.name(graphics, MagicTexts.category(row.category()).getString(), x + 20, y + 4, 0xFFFFFFFF, right - x - 20 - amountsWidth - 6);
        screen.name(graphics, amounts.getString(), right - amountsWidth, y + 4, 0xFFF0D070, amountsWidth);
    }

    private void renderMage(GuiGraphics graphics, int x, int right, int y, MagicViews.MageRow row) {
        Component status = Component.translatable("kingdomworld.work.status." + row.status().id());
        Component skill = Component.translatable(KEY + "mage_skill", row.speed(), row.experience());
        int statusWidth = Math.min(screen.font().width(status), (right - x - 20) / 3);
        int skillWidth = Math.min(screen.font().width(skill), (right - x - 20) / 3);
        graphics.renderItem(new ItemStack(MagicRegistries.MAGIC_FOCUS_ITEM.get()), x, y);
        int nameRight = right - statusWidth - skillWidth - 12;
        screen.name(graphics, row.name().getString(), x + 20, y + 4, 0xFFFFFFFF, nameRight - x - 20);
        screen.name(graphics, skill.getString(), nameRight + 6, y + 4, 0xFFB8B8B8, skillWidth);
        screen.name(graphics, status.getString(), right - statusWidth, y + 4, row.status() == WorkStatus.WORKING ? 0xFFA0E0A0 : 0xFFFF9070,
                statusWidth);
    }

    private void renderResearch(GuiGraphics graphics, int x, int right, int y, MagicViews.ResearchRow row) {
        Component state = switch (row.state()) {
            case DONE -> Component.translatable(KEY + "research.done");
            case CURRENT -> Component.translatable(KEY + "research.current", row.percent());
            case AVAILABLE -> Component.translatable(KEY + "research.cost", MagicTexts.energy(row.cost()));
            case LOCKED -> row.research().requires().<Component>map(required -> Component.translatable(KEY + "research.locked",
                    MagicTexts.research(required))).orElseGet(() -> Component.translatable(KEY + "research.cost", MagicTexts.energy(row.cost())));
        };
        int textRight = row.state() == MagicViews.ResearchState.AVAILABLE ? right - startWidth - 4 : right;
        int stateWidth = Math.min(screen.font().width(state), (textRight - x - 20) / 2);
        graphics.renderItem(icon(row.research()), x, y);
        int color = switch (row.state()) {
            case DONE -> 0xFFA0E0A0;
            case CURRENT -> 0xFFF0D070;
            case AVAILABLE -> 0xFFFFFFFF;
            case LOCKED -> 0xFF9A9A9A;
        };
        screen.name(graphics, MagicTexts.research(row.research()).getString(), x + 20, y + 4, color, textRight - x - 20 - stateWidth - 6);
        screen.name(graphics, state.getString(), textRight - stateWidth, y + 4, color, stateWidth);
    }

    private void renderSpell(GuiGraphics graphics, int x, int right, int y, MagicViews.SpellRow row) {
        int textRight = row.researched() ? right - switchWidth - 4 : right;
        Component state = !row.researched() ? Component.translatable(KEY + "spell.locked")
                : MagicTexts.price(row.spell(), row.price());
        int stateWidth = Math.min(screen.font().width(state), (textRight - x - 20) / 2);
        graphics.renderItem(icon(row.spell()), x, y);
        int nameWidth = textRight - x - 20 - stateWidth - 6;
        // The hint names the spell in full, so it stays readable when the name is shortened.
        screen.hoverText(x + 20, y + 4, Math.max(0, Math.min(nameWidth, screen.font().width(MagicTexts.kingdomSpell(row.spell())))),
                Component.translatable(KEY + "spell_hint", MagicTexts.kingdomSpell(row.spell()), MagicTexts.kingdomSpellHint(row.spell()),
                        row.on() ? Component.translatable(KEY + "spell.on") : Component.translatable(KEY + "spell.off")));
        screen.name(graphics, MagicTexts.kingdomSpell(row.spell()).getString(), x + 20, y + 4, row.on() ? 0xFFA0E0A0 : 0xFFFFFFFF, nameWidth);
        screen.name(graphics, state.getString(), textRight - stateWidth, y + 4, row.researched() ? 0xFFF0D070 : 0xFF9A9A9A, stateWidth);
    }

    private void renderDevice(GuiGraphics graphics, int x, int right, int y, MagicViews.DeviceRow row) {
        boolean gate = row.kind() == io.github.sekerator.kingdomworld.core.magic.MagicDevice.Kind.GATE;
        Component where = Component.translatable(KEY + "device_where", row.pos().getX(), row.pos().getY(), row.pos().getZ(), row.dimension());
        int whereWidth = Math.min(screen.font().width(where), (right - x - 20) / 2);
        graphics.renderItem(new ItemStack(gate ? MagicRegistries.MAGIC_GATE_ITEM.get() : MagicRegistries.CRYSTAL_GENERATOR_ITEM.get()), x, y);
        screen.name(graphics, Component.translatable(KEY + (gate ? "gate" : "generator"), row.id()).getString(), x + 20, y + 4, 0xFFFFFFFF,
                right - x - 20 - whereWidth - 6);
        screen.name(graphics, where.getString(), right - whereWidth, y + 4, 0xFFB8B8B8, whereWidth);
    }

    /**
     * The status lines at the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (!view.canManage() && (mode == Mode.RESEARCH || mode == Mode.SPELLS)) {
            return Component.translatable(KEY + "read_only");
        }
        return Component.translatable(KEY + "hint." + mode.id());
    }
}
