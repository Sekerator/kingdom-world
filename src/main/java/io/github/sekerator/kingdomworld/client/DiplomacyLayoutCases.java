package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The screens and lines of diplomacy in the layout check: the diplomacy tab of the throne screen for the king and for a
 * subject in every view (with the actions for a kingdom open), and the lines above the hotbar of the land of an enemy and
 * of refused blows. Views carry the longest names, the largest numbers and more rows than fit.
 */
final class DiplomacyLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";

    private DiplomacyLayoutCases() {
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static Component name(int index) {
        // Every other kingdom is a kingdom of the world with both of its names.
        return index % 2 == 0 ? Component.literal(LONG_KINGDOM)
                : Component.translatable("kingdomworld.npc.name", "Gorodishche Velikoe Severnoe", "Городище Великое Северное");
    }

    /**
     * Lines above the hotbar: the land of an enemy, shielded or not, and the blows refused.
     */
    static void actionbars(Font font, int narrowest) {
        String shortName = "ЩЩЩЩЩЩЩЩЩЩЩЩЩЩЩ…";
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.war.enemy_land", shortName), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.war.enemy_land_shielded", shortName), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.war.no_blow.away"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.war.no_blow.peace"), narrowest);
    }

    private static DiplomacyViews.TabView tab(UUID kingdom, boolean canManage, boolean empty, boolean shielded) {
        List<DiplomacyViews.KingdomRow> kingdoms = new ArrayList<>();
        List<DiplomacyViews.OfferRow> offers = new ArrayList<>();
        List<DiplomacyViews.CallRow> calls = new ArrayList<>();
        List<DiplomacyViews.WarRow> wars = new ArrayList<>();
        List<Component> chronicle = new ArrayList<>();
        if (!empty) {
            Bond[] bonds = Bond.values();
            for (int i = 0; i < 24; i++) {
                kingdoms.add(new DiplomacyViews.KingdomRow(id("k" + i), name(i), 0x922B21 + i * 977, i % 2 == 1, bonds[i % bonds.length],
                        i % 3 == 0 ? -100 : 100, i % 2 == 0 ? "broken_treaty" : "call_answered", i == 3, i == 4, i % 4 == 0 ? 99_999 : -1,
                        i % 5 != 0, i % 7 == 0));
            }
            Proposal.Kind[] kinds = Proposal.Kind.values();
            for (int i = 0; i < 12; i++) {
                offers.add(new DiplomacyViews.OfferRow(i + 1, i % 2 == 0, id("o" + i), name(i), kinds[i % kinds.length],
                        Component.translatable("kingdomworld.war.offer_text.vassalage", name(i), name(i + 1)), 99_999));
            }
            for (int i = 0; i < 3; i++) {
                calls.add(new DiplomacyViews.CallRow(i + 1, name(i), name(i + 1)));
            }
            Component terms = Component.translatable("kingdomworld.war.terms.indemnity", name(0),
                    Component.literal("999 999 999 999 Великая Северная Крона Щит"));
            for (int i = 0; i < 6; i++) {
                Component allies = Component.translatable("kingdomworld.war.tab.ally_ours", name(i));
                wars.add(new DiplomacyViews.WarRow(i + 1, name(i), i % 2 == 0, i % 3 == 0, WarGoal.values()[i % WarGoal.values().length],
                        i == 1 ? War.Phase.PREPARING : War.Phase.ACTIVE, 99_999, 100, 100, 100, 100, i == 2, i == 3, allies, terms, terms, i % 2 == 0));
            }
            for (int i = 0; i < 40; i++) {
                chronicle.add(Component.translatable("kingdomworld.war.chronicle.raid", 99_999, name(i), name(i + 1), TradeTexts.items(999_999)));
            }
        }
        return new DiplomacyViews.TabView(kingdom, canManage, canManage, shielded, kingdoms, offers, calls, wars, chronicle);
    }

    private static ThroneScreen diplomacyTab(Function<Role, KingdomView> member, Role role, boolean canManage, boolean empty, boolean shielded,
                                             String mode, boolean actions, Component status) {
        KingdomView kingdom = member.apply(role);
        ThroneScreen screen = new ThroneScreen(kingdom);
        screen.selectTab("diplomacy");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateDiplomacy(tab(kingdom.id(), canManage, empty, shielded));
        screen.showDiplomacy(mode, actions);
        return screen;
    }

    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.NOBODY_ONLINE);
        add.accept("throne-diplomacy-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("diplomacy");
            return screen;
        });
        for (String mode : List.of("kingdoms", "offers", "wars", "chronicle")) {
            add.accept("throne-diplomacy-" + mode, () -> diplomacyTab(member, Role.KING, true, false, false, mode, false, Component.empty()));
            add.accept("throne-diplomacy-" + mode + "-empty", () -> diplomacyTab(member, Role.CHANCELLOR, true, true, true, mode, false,
                    Component.empty()));
            add.accept("throne-diplomacy-" + mode + "-subject", () -> diplomacyTab(member, Role.SUBJECT, false, false, true, mode, false,
                    longError));
        }
        add.accept("throne-diplomacy-actions", () -> diplomacyTab(member, Role.KING, true, false, false, "kingdoms", true, longError));
        add.accept("throne-diplomacy-actions-subject", () -> diplomacyTab(member, Role.SUBJECT, false, false, false, "kingdoms", true,
                Component.empty()));
    }

    /**
     * Marks of the atlas for the map case: a war, an alliance, a pact, vassalage and a truce, one of them shielded.
     */
    static void fillMarks(UUID first, UUID second) {
        List<DiplomacyViews.MapMark> marks = new ArrayList<>();
        marks.add(new DiplomacyViews.MapMark(first, Component.literal(LONG_KINGDOM), "kingdomworld:layout_check", 0, 0, Bond.WAR, -100, true));
        marks.add(new DiplomacyViews.MapMark(second, name(1), "kingdomworld:layout_check", 112, 16, Bond.ALLIANCE, 100, false));
        ClientDiplomacyHandlers.setMarksForCheck(new DiplomacyViews.MapView(marks));
    }
}
