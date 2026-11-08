package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.NobilityViews;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Faction;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
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
 * The nobility tab of the throne screen in the layout check: every mode for the king, a chancellor without spies, a subject
 * and the court of a kingdom of the world. Views carry the longest names, the largest numbers and more rows than fit.
 */
final class NobilityLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";
    private static final String KEY = "kingdomworld.nobility.";

    private NobilityLayoutCases() {
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static Component house(int i) {
        return Component.translatable(KEY + "both", "Strelovenstan" + i, "Стреловенстан" + i);
    }

    private static Component person(int i) {
        return Component.translatable(KEY + "both", "Dobroslavamira" + i, "Доброславамира" + i);
    }

    private static Component money() {
        return Component.literal("999 999 999 Великая Северная Крона");
    }

    private static NobilityViews.TabView tab(UUID kingdom, boolean manage, boolean spies, boolean npc, boolean empty) {
        List<NobilityViews.CauseRow> causes = new ArrayList<>();
        List<NobilityViews.HouseRow> houses = new ArrayList<>();
        List<NobilityViews.GrantRow> grants = new ArrayList<>();
        List<NobilityViews.Choice> fiefs = new ArrayList<>();
        List<NobilityViews.Choice> subjects = new ArrayList<>();
        List<NobilityViews.FactionRow> factions = new ArrayList<>();
        List<NobilityViews.PlotRow> plots = new ArrayList<>();
        List<Component> chronicle = new ArrayList<>();
        List<NobilityViews.SpyRow> spyRows = new ArrayList<>();
        List<Component> reports = new ArrayList<>();
        List<NobilityViews.Choice> targets = new ArrayList<>();
        NobilityViews.DemandRow demand = NobilityViews.DemandRow.NONE;
        Component warning = Component.empty();
        if (!empty) {
            for (String cause : List.of("houses", "mood", "taxes", "war", "defeats", "festival", "factions", "demand_refused", "incited", "secession")) {
                causes.add(new NobilityViews.CauseRow(cause, cause.length() % 2 == 0 ? -24 : 15));
            }
            warning = Component.translatable(KEY + "tab.rebels", 99);
            for (int i = 0; i < 6; i++) {
                Component titles = Component.translatable(KEY + "tab.title_of", Component.translatable(KEY + "title.duke"),
                        Component.literal("Городище Великое Северное"));
                houses.add(new NobilityViews.HouseRow(i + 1, house(i), person(i), Faction.values()[i % Faction.values().length], i * 20, 999_999_999L, titles));
                grants.add(new NobilityViews.GrantRow(i + 1, Title.values()[i % Title.values().length], Component.translatable(KEY + "march." + (i % 4)),
                        i % 2 == 0 ? house(i) : Component.literal(LONG_PLAYER), i % 2 == 1));
                fiefs.add(new NobilityViews.Choice("m" + (i % 4), Component.literal("Городище Великое Северное Длинное " + i)));
                subjects.add(new NobilityViews.Choice(id("p" + i).toString(), Component.literal(LONG_PLAYER)));
                targets.add(new NobilityViews.Choice(id("k" + i).toString(), Component.literal(LONG_KINGDOM)));
            }
            for (Faction faction : Faction.values()) {
                factions.add(new NobilityViews.FactionRow(faction, 100, 100));
            }
            demand = new NobilityViews.DemandRow(5, house(1), Faction.OLD_NOBILITY, Demand.Kind.DECLARE_WAR,
                    Component.translatable(KEY + "demand.declare_war", Component.literal(LONG_KINGDOM)), 99);
            plots.add(new NobilityViews.PlotRow(7, house(2), Component.literal(LONG_KINGDOM)));
            plots.add(new NobilityViews.PlotRow(8, house(3), Component.empty()));
            for (int i = 0; i < 40; i++) {
                chronicle.add(Component.translatable(KEY + "tab.dated", 99_999, Component.translatable(KEY + "note.married", house(i), house(i + 1),
                        Component.literal(LONG_KINGDOM))));
                reports.add(Component.translatable(KEY + "tab.dated", 99_999, Component.translatable(KEY + "note.report.scout", Component.literal(LONG_KINGDOM),
                        999_999_999, 999, 99, Component.translatable(KEY + "word.plot_yes"), 100)));
            }
            Spy.Status[] statuses = Spy.Status.values();
            for (int i = 0; i < 4; i++) {
                Component doing = switch (statuses[i]) {
                    case IDLE -> Component.translatable(KEY + "spy.idle");
                    case COUNTER -> Component.translatable(KEY + "spy.counter");
                    case MISSION -> Component.translatable(KEY + "spy.mission", Component.translatable(KEY + "task.assassinate"), Component.literal(LONG_KINGDOM), 99);
                    case CAPTURED -> Component.translatable(KEY + "spy.captured", Component.literal(LONG_KINGDOM), money(), 99);
                };
                spyRows.add(new NobilityViews.SpyRow(i + 1, person(i), 95, statuses[i], doing));
            }
        }
        Component hire = Component.translatable(KEY + "tab.hire_cost", money(), money());
        return new NobilityViews.TabView(kingdom, manage, spies, manage, npc, empty ? 50 : 3, causes, warning, houses, grants, fiefs, subjects, factions, demand,
                plots, chronicle, spies ? spyRows : List.of(), spies ? reports : List.of(), spies ? targets : List.of(), 80, hire, 16);
    }

    private static ThroneScreen nobilityTab(Function<Role, KingdomView> member, Role role, boolean manage, boolean spies, boolean npc, boolean empty,
                                            String mode, Component status) {
        KingdomView kingdom = member.apply(role);
        ThroneScreen screen = new ThroneScreen(kingdom);
        screen.selectTab("nobility");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateNobility(tab(kingdom.id(), manage, spies, npc, empty));
        screen.showNobility(mode);
        return screen;
    }

    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.TOO_MANY_SPIES);
        add.accept("throne-nobility-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("nobility");
            return screen;
        });
        for (String mode : List.of("houses", "titles", "court", "chronicle", "spies", "reports")) {
            add.accept("throne-nobility-" + mode, () -> nobilityTab(member, Role.KING, true, true, false, false, mode, Component.empty()));
            add.accept("throne-nobility-" + mode + "-empty", () -> nobilityTab(member, Role.KING, true, true, false, true, mode, longError));
        }
        for (String mode : List.of("houses", "titles", "court", "chronicle")) {
            add.accept("throne-nobility-" + mode + "-subject", () -> nobilityTab(member, Role.SUBJECT, false, false, false, false, mode, Component.empty()));
            add.accept("throne-nobility-" + mode + "-npc", () -> nobilityTab(member, Role.SUBJECT, false, false, true, false, mode, longError));
        }
        add.accept("throne-nobility-spies-spymaster", () -> nobilityTab(member, Role.SPYMASTER, false, true, false, false, "spies", Component.empty()));
    }
}
