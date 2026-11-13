package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.GateScreen;
import io.github.sekerator.kingdomworld.client.screen.ItemTooltipScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.MagicFocusScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.magic.MagicRegistries;
import io.github.sekerator.kingdomworld.common.magic.MagicTexts;
import io.github.sekerator.kingdomworld.common.magic.StaffItem;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The screens and messages of magic in the layout check: the magic tab of the throne screen in every view, the screen of a
 * focus, the screen of a gate, the tooltips of the staff, of a crystal a spell started and of the blocks, and the lines
 * above the hotbar. Views carry the largest numbers, the longest names and more rows than fit.
 */
final class MagicLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final long MUCH = 999_999_999_999L;
    private static final UUID KINGDOM = UUID.nameUUIDFromBytes("magic".getBytes(StandardCharsets.UTF_8));
    private static final BlockPos FAR = new BlockPos(-30_000_000, 319, 29_999_999);
    private static final String LONG_DIMENSION = "twilightforest:twilight_forest";

    private MagicLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(KINGDOM, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483, 10, 100, 9_990_000L);
    }

    /**
     * Lines above the hotbar: the staff choosing a spell, casts and their refusals, blocks of magic refusing.
     */
    static void actionbars(Font font, int narrowest) {
        // The cost of one cast: the config allows any number, the defaults stay below a hundred; six digits are checked.
        long cost = 999_999;
        for (PlayerSpell spell : PlayerSpell.values()) {
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.staff.selected", MagicTexts.spell(spell), 9, 9), narrowest);
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.done", MagicTexts.spell(spell), MagicTexts.energy(cost)),
                    narrowest);
        }
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.cooldown", MagicTexts.seconds(72_000)), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.no_crystals", MagicTexts.energy(cost)), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.detected", 999, MagicTexts.energy(cost)), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.blink_blocked"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.blink_prepare_started", 120), narrowest);
        LayoutCheck.actionbar(font, io.github.sekerator.kingdomworld.common.magic.PlayerSpells.progress(50, 100), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.blink_broken_move"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.blink_broken_hurt"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.cast.blink_already_preparing"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.staff.no_spells"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.focus_outside"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.magic.gate_closed"), narrowest);
        for (KingdomError error : new KingdomError[]{KingdomError.NOT_OWNED, KingdomError.NO_PERMISSION, KingdomError.LIMIT_REACHED,
                KingdomError.NOT_LEARNED}) {
            LayoutCheck.actionbar(font, KingdomActions.errorMessage(error), narrowest);
        }
    }

    private static Component longestName() {
        CitizenNames.Alphabet alphabet = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected());
        int best = 0;
        int bestWidth = -1;
        for (int seed = 0; seed < 20_000; seed += 7) {
            int width = Minecraft.getInstance().font.width(CitizenNames.render(seed, Gender.FEMALE, alphabet));
            if (width > bestWidth) {
                best = seed;
                bestWidth = width;
            }
        }
        return CitizenTexts.name(best, Gender.FEMALE);
    }

    private static MagicViews.TabView tab(UUID kingdom, boolean canManage, boolean empty, boolean here) {
        List<List<MagicViews.JournalRow>> journal = new ArrayList<>();
        for (int period = 0; period < 3; period++) {
            List<MagicViews.JournalRow> rows = new ArrayList<>();
            if (!empty) {
                for (EnergyCategory category : EnergyCategory.values()) {
                    rows.add(new MagicViews.JournalRow(category, MUCH, MUCH));
                }
            }
            journal.add(rows);
        }
        List<MagicViews.MageRow> mages = new ArrayList<>();
        List<MagicViews.DeviceRow> gates = new ArrayList<>();
        List<MagicViews.DeviceRow> generators = new ArrayList<>();
        if (!empty) {
            Component name = longestName();
            WorkStatus[] statuses = WorkStatus.values();
            for (int i = 0; i < 20; i++) {
                mages.add(new MagicViews.MageRow(UUID.randomUUID(), name, 999_999, 200, statuses[i % statuses.length]));
                gates.add(new MagicViews.DeviceRow(i + 1_000_000L, MagicDevice.Kind.GATE, LONG_DIMENSION, FAR));
                generators.add(new MagicViews.DeviceRow(i + 2_000_000L, MagicDevice.Kind.GENERATOR, LONG_DIMENSION, FAR));
            }
        }
        List<MagicViews.ResearchRow> research = new ArrayList<>();
        MagicViews.ResearchState[] states = MagicViews.ResearchState.values();
        for (Research entry : Research.values()) {
            research.add(new MagicViews.ResearchRow(entry, empty ? MagicViews.ResearchState.AVAILABLE : states[entry.ordinal() % states.length], MUCH,
                    99_999, 99));
        }
        List<MagicViews.SpellRow> spells = new ArrayList<>();
        for (KingdomSpell spell : KingdomSpell.values()) {
            spells.add(new MagicViews.SpellRow(spell, empty || spell.ordinal() != 2, spell.ordinal() % 2 == 0, MUCH, 99_999));
        }
        return new MagicViews.TabView(kingdom, canManage, MUCH, MUCH, empty ? 0 : 99_999, empty ? 0 : 99_999, empty ? 0 : MUCH, empty ? 0 : MUCH,
                journal, mages, research, 8, spells, 99_999, 99_999, here, here, gates, generators, 1_000_000, 1_000_000, Rank.SSS);
    }

    private static ThroneScreen magicTab(Function<Role, KingdomView> member, Role role, boolean canManage, boolean empty, boolean here, String mode,
                                         Component status) {
        KingdomView view = member.apply(role);
        ThroneScreen screen = new ThroneScreen(view);
        screen.selectTab("magic");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateMagic(tab(view.id(), canManage, empty, here));
        screen.showMagic(mode);
        return screen;
    }

    private static MagicViews.FocusView focus(boolean learned, boolean staff) {
        List<MagicViews.Lesson> lessons = new ArrayList<>();
        KingdomError[] refusals = {KingdomError.TOWER_TOO_LOW, KingdomError.RANK_TOO_LOW, KingdomError.NOT_ENOUGH_MONEY, KingdomError.NOT_A_MEMBER};
        for (PlayerSpell spell : PlayerSpell.values()) {
            boolean known = learned && spell.ordinal() % 2 == 0;
            // Lessons learned to every level, the last one and the first, and lessons not taken yet.
            lessons.add(new MagicViews.Lesson(spell, known, MUCH, MUCH, Rank.SSS, 99,
                    known || spell.ordinal() == 1 ? Optional.empty() : Optional.of(refusals[spell.ordinal() % refusals.length]),
                    known ? 1 + spell.ordinal() % 4 : 0, 4));
        }
        List<Optional<PlayerSpell>> slots = new ArrayList<>();
        if (staff) {
            for (int i = 0; i < 9; i++) {
                slots.add(i % 3 == 2 ? Optional.empty() : Optional.of(PlayerSpell.values()[i % PlayerSpell.COUNT]));
            }
        }
        return new MagicViews.FocusView(FAR, KINGDOM, LONG_KINGDOM, 0x6C3483, currency(), 99, Rank.SSS, MUCH, MUCH, lessons, slots,
                Optional.of(Research.ARCANA_3), 99, Rank.SSS, true);
    }

    private static MagicFocusScreen focusScreen(MagicViews.FocusView view, String mode, Component status) {
        MagicFocusScreen screen = new MagicFocusScreen(view);
        screen.showForCheck(mode);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static GateScreen gate(boolean on, boolean empty) {
        List<MagicViews.GateRow> rows = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                rows.add(new MagicViews.GateRow(i + 1_000_000L, LONG_DIMENSION, FAR, i % 2 == 0 ? 59_999_999 : -1));
            }
        }
        return new GateScreen(new MagicViews.GateView(FAR, KINGDOM, LONG_KINGDOM, 0x6C3483, 999_999, on, MUCH, MUCH, rows));
    }

    private static ItemTooltipScreen itemTooltips() {
        List<ItemStack> stacks = new ArrayList<>();
        ItemStack staff = new ItemStack(MagicRegistries.STAFF.get());
        StaffItem.setSlot(staff, 0, PlayerSpell.DETECT, 3);
        StaffItem.setSlot(staff, 1, PlayerSpell.FIREBALL, 3);
        stacks.add(staff);
        stacks.add(MonsterCrystalItem.started(Rank.SSS, 99_999));
        return new ItemTooltipScreen("magic-items", stacks);
    }

    private static ItemTooltipScreen blockTooltips() {
        return new ItemTooltipScreen("magic-blocks", List.of(new ItemStack(MagicRegistries.MAGIC_FOCUS_ITEM.get()),
                new ItemStack(MagicRegistries.CRYSTAL_ACCUMULATOR_ITEM.get()), new ItemStack(MagicRegistries.CRYSTAL_GENERATOR_ITEM.get()),
                new ItemStack(MagicRegistries.MAGIC_GATE_ITEM.get())));
    }

    /**
     * Adds every case of magic.
     *
     * @param member a kingdom seen by a member of the position
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.READ_ONLY);
        Component learned = Component.translatable("kingdomworld.magic.learned", MagicTexts.spell(PlayerSpell.DETECT),
                MoneyTexts.amount(MUCH, currency().units()));
        add.accept("throne-magic-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("magic");
            return screen;
        });
        for (String mode : List.of("energy", "journal", "mages", "research", "spells", "gates", "machines")) {
            add.accept("throne-magic-" + mode, () -> magicTab(member, Role.KING, true, false, true, mode, Component.empty()));
            add.accept("throne-magic-" + mode + "-empty", () -> magicTab(member, Role.COURT_MAGE, true, true, false, mode, Component.empty()));
        }
        add.accept("throne-magic-research-subject", () -> magicTab(member, Role.SUBJECT, false, false, false, "research", Component.empty()));
        add.accept("throne-magic-spells-subject", () -> magicTab(member, Role.SUBJECT, false, false, true, "spells", longError));
        add.accept("magic-focus-learn", () -> focusScreen(focus(true, true), "learn", Component.empty()));
        add.accept("magic-focus-learn-new", () -> focusScreen(focus(false, false), "learn", learned));
        add.accept("magic-focus-staff", () -> focusScreen(focus(true, true), "staff", longError));
        add.accept("magic-focus-no-staff", () -> focusScreen(focus(true, false), "staff", Component.empty()));
        add.accept("magic-gate", () -> gate(true, false));
        add.accept("magic-gate-off", () -> gate(false, false));
        add.accept("magic-gate-empty", () -> gate(true, true));
        add.accept("magic-tooltips-items", MagicLayoutCases::itemTooltips);
        add.accept("magic-tooltips-blocks", MagicLayoutCases::blockTooltips);
    }
}
