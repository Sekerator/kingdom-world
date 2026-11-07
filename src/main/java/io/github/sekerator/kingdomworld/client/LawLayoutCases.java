package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.LawDialogScreen;
import io.github.sekerator.kingdomworld.client.screen.LawScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.RansomScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WantedHudPreview;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The screens and lines of the law in the layout check: the law tab of the throne screen for the court and for a subject in
 * every view (with the form of a fine open), the player's window of the law, the windows to give up, to pay at a gate and to
 * buy back goods in every state, the line of the wanted above the hotbar together with the lines of the game there, and the
 * lines guards and the law send above the hotbar. Views carry the largest numbers, the longest names and more rows than fit.
 */
final class LawLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";
    private static final String LONG_CITY = "Новый Великий Северный Город Щит";
    private static final long MUCH = 999_999_999_999L;
    private static final long HOTBAR_MONEY = 99_999_999L;
    private static final UUID KINGDOM = UUID.nameUUIDFromBytes("law".getBytes(StandardCharsets.UTF_8));

    private LawLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(KINGDOM, LONG_CURRENCY, LONG_KINGDOM, 0x922B21, 10, 100, 9_990_000L);
    }

    private static Component money() {
        return MoneyTexts.amount(MUCH, currency().units());
    }

    // ---------------------------------------------------------------- lines above the hotbar

    /**
     * Lines above the hotbar: a guard calling out, a witness running, the price of a gate, a paid entry, and the line of the
     * wanted itself.
     */
    static void actionbars(Font font, int narrowest) {
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.law.hail", hotbarMoney()), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.law.hail_murder"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.law.witness_ran"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.law.entry_price", hotbarMoney()), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.law.entry_paid", hotbarMoney()), narrowest);
        LayoutCheck.actionbar(font, WantedHud.text(line(false)), narrowest);
        LayoutCheck.actionbar(font, WantedHud.text(line(true)), narrowest);
    }

    /**
     * The largest amount the lines above the hotbar are checked with: fines and fees of the defaults stay far below it, and
     * eight digits of copper are checked.
     */
    private static Component hotbarMoney() {
        return LawTexts.shortAmount(HOTBAR_MONEY, currency().units());
    }

    private static LawViews.WantedLine line(boolean execution) {
        // Kingdom names on the line are cut to 12 letters by the server.
        return new LawViews.WantedLine(true, "ЩЩЩЩЩЩЩЩЩЩЩЩ…", 0x922B21, execution, HOTBAR_MONEY, 10, 100);
    }

    // ---------------------------------------------------------------- views

    private static List<LawViews.CrimeRow> crimes(int count) {
        List<LawViews.CrimeRow> rows = new ArrayList<>();
        CrimeKind[] kinds = CrimeKind.values();
        for (int i = 0; i < count; i++) {
            CrimeKind kind = kinds[i % kinds.length];
            rows.add(new LawViews.CrimeRow(kind, kind == CrimeKind.THEFT ? MUCH : 0, kind == CrimeKind.THEFT || kind == CrimeKind.TRESPASS ? ""
                    : LONG_PLAYER, i % 2 == 0 ? 99_999 : 1));
        }
        return rows;
    }

    private static LawViews.WantedRow wanted(int index, boolean execution) {
        return new LawViews.WantedRow(UUID.nameUUIDFromBytes(("wanted" + index).getBytes(StandardCharsets.UTF_8)), LONG_PLAYER, index % 3 != 2,
                crimes(index % 2 == 0 ? 8 : 2), execution ? 0 : MUCH, execution, !execution && index % 4 == 1, index % 4 == 1, execution ? MUCH : 0,
                index % 2 == 0 ? -100 : 100);
    }

    private static LawViews.TabView tab(boolean canManage, boolean empty, boolean wantedSelf) {
        List<LawViews.WantedRow> rows = new ArrayList<>();
        if (canManage && !empty) {
            for (int i = 0; i < 24; i++) {
                rows.add(wanted(i, i % 3 == 0));
            }
        }
        return new LawViews.TabView(KINGDOM, currency(), canManage, rows, 5_000, MUCH, MUCH, !empty, MUCH, empty, MUCH, MUCH,
                wantedSelf ? Optional.of(wanted(0, true)) : Optional.empty(), -100, MUCH, 99);
    }

    private static ThroneScreen lawTab(Function<Role, KingdomView> member, Role role, LawViews.TabView view, String mode, boolean form,
                                       Component status) {
        KingdomView kingdom = member.apply(role);
        ThroneScreen screen = new ThroneScreen(kingdom);
        screen.selectTab("law");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        LawViews.TabView own = new LawViews.TabView(kingdom.id(), view.currency(), view.canManage(), view.wanted(), view.multiplier(),
                view.entryFee(), view.maxEntryFee(), view.slaveryBanned(), view.bounty(), view.bountyDefault(), view.maxBounty(), view.murderFine(),
                view.self(), view.reputation(), view.debt(), view.confiscations());
        screen.updateLaw(own);
        screen.showLaw(mode, form);
        return screen;
    }

    private static List<ItemStack> goods(int count) {
        List<ItemStack> stacks = new ArrayList<>();
        ItemStack[] kinds = {new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.BREAD, 64),
                new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64)};
        for (int i = 0; i < count; i++) {
            stacks.add(kinds[i % kinds.length].copy());
        }
        return stacks;
    }

    private static LawViews.ConfiscationRow confiscation(long id, boolean gone, boolean near) {
        return new LawViews.ConfiscationRow(id, LONG_KINGDOM, 0x922B21, currency(), MUCH, MUCH, 41 * 64, 365, gone, near, goods(9));
    }

    private static LawViews.PlayerView player(boolean empty) {
        List<LawViews.KingdomRow> kingdoms = new ArrayList<>();
        List<LawViews.ConfiscationRow> confiscations = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                kingdoms.add(new LawViews.KingdomRow(currency(), i % 3 == 2 ? Optional.empty() : Optional.of(wanted(i, i % 3 == 0)),
                        i % 2 == 0 ? -100 : 100, i % 4 == 3 ? 0 : MUCH, MUCH, i % 2 == 0 ? 1_440 : 0, i == 0));
            }
            for (int i = 0; i < 20; i++) {
                confiscations.add(confiscation(i + 1L, i % 5 == 4, i % 3 == 0));
            }
        }
        return new LawViews.PlayerView(kingdoms, confiscations, !empty);
    }

    private static LawScreen playerScreen(LawViews.PlayerView view, String mode, Component status) {
        LawScreen screen = new LawScreen(view);
        screen.showForCheck(mode);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static LawDialogScreen dialog(LawViews.Dialog dialog, boolean execution, int crimes, Component status) {
        LawDialogScreen screen = new LawDialogScreen(new LawViews.DialogView(dialog, KINGDOM, currency(), MUCH, execution,
                dialog == LawViews.Dialog.ENTRY ? LONG_CITY : "", dialog == LawViews.Dialog.SURRENDER ? crimes(crimes) : List.of(), MUCH, MUCH));
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static RansomScreen ransom(boolean gone, boolean near, int count, Component status) {
        RansomScreen screen = new RansomScreen(new LawViews.RansomView(confiscation(7, gone, near), goods(count), MUCH, MUCH));
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    /**
     * Adds every case of the law.
     *
     * @param member a kingdom seen by a member of the position
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.DEBT_TOO_HIGH);
        add.accept("throne-law-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("law");
            return screen;
        });
        for (String mode : List.of("wanted", "court", "mine")) {
            add.accept("throne-law-" + mode, () -> lawTab(member, Role.KING, tab(true, false, true), mode, false, Component.empty()));
            add.accept("throne-law-" + mode + "-empty", () -> lawTab(member, Role.CHANCELLOR, tab(true, true, false), mode, false, Component.empty()));
        }
        add.accept("throne-law-wanted-fine", () -> lawTab(member, Role.KING, tab(true, false, false), "wanted", true, longError));
        add.accept("throne-law-subject", () -> lawTab(member, Role.SUBJECT, tab(false, false, true), "mine", false, Component.empty()));
        add.accept("throne-law-subject-clean", () -> lawTab(member, Role.SUBJECT, tab(false, true, false), "mine", false,
                Component.translatable("kingdomworld.law.court.pardon")));
        add.accept("law-window-kingdoms", () -> playerScreen(player(false), "kingdoms", Component.empty()));
        add.accept("law-window-confiscated", () -> playerScreen(player(false), "confiscated", longError));
        add.accept("law-window-empty", () -> playerScreen(player(true), "kingdoms", Component.empty()));
        add.accept("law-window-empty-confiscated", () -> playerScreen(player(true), "confiscated", Component.empty()));
        add.accept("law-window-paid", () -> playerScreen(player(false), "kingdoms", Component.translatable("kingdomworld.law.debt_paid", money())));
        add.accept("law-surrender", () -> dialog(LawViews.Dialog.SURRENDER, false, 8, Component.empty()));
        add.accept("law-surrender-murder", () -> dialog(LawViews.Dialog.SURRENDER, true, 2, longError));
        add.accept("law-surrender-one", () -> dialog(LawViews.Dialog.SURRENDER, false, 1, Component.empty()));
        add.accept("law-entry", () -> dialog(LawViews.Dialog.ENTRY, false, 0, Component.empty()));
        add.accept("law-entry-refused", () -> dialog(LawViews.Dialog.ENTRY, false, 0, KingdomActions.errorMessage(KingdomError.NOT_ENOUGH_MONEY)));
        add.accept("law-ransom", () -> ransom(false, true, 64, Component.empty()));
        add.accept("law-ransom-far", () -> ransom(false, false, 12, KingdomActions.errorMessage(KingdomError.NOT_AT_RANSOM_PLACE)));
        add.accept("law-ransom-gone", () -> ransom(true, false, 3, Component.empty()));
        add.accept("law-hud", () -> new WantedHudPreview("fine", line(false), Component.translatable("kingdomworld.law.hail", hotbarMoney()),
                new ItemStack(Items.NETHERITE_SWORD).getHoverName()));
        add.accept("law-hud-murder", () -> new WantedHudPreview("murder", line(true), Component.translatable("kingdomworld.law.hail_murder"),
                Component.literal(LONG_PLAYER)));
        add.accept("law-hud-messages", () -> new WantedHudPreview("messages", line(false), Component.translatable("kingdomworld.law.entry_price",
                hotbarMoney()), TradeTexts.items(MUCH)));
    }
}
