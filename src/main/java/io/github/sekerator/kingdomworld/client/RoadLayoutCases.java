package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.map.ClientRoadCache;
import io.github.sekerator.kingdomworld.client.screen.ItemTooltipScreen;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The screens of roads in the layout check: the roads tab of the throne screen in every view and state, planning a road on
 * the atlas and the roads, cities and camps on it, and the tooltip of the town hall. Views carry the largest numbers, the
 * longest names and more rows than fit.
 */
final class RoadLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CITY = "Новый Верхнеполянский Городок";
    private static final long MUCH = 999_999_999_999L;

    private RoadLayoutCases() {
    }

    private static RoadViews.RoadTabView tab(UUID kingdom, boolean canManage, boolean empty) {
        List<RoadViews.CityRow> cities = new ArrayList<>();
        List<RoadViews.RoadRow> roads = new ArrayList<>();
        List<RoadViews.CrossingRow> crossings = new ArrayList<>();
        List<RoadViews.CampRow> camps = new ArrayList<>();
        List<RoadViews.RiskRow> risks = new ArrayList<>();
        List<RoadViews.SquadRow> squads = new ArrayList<>();
        if (!empty) {
            cities.add(new RoadViews.CityRow(0, LONG_KINGDOM, -29_999_999, 319, 29_999_999, true, 256, CityLevel.CITY, 99_999, 99_999, MUCH,
                    Wall.WallClass.STONE, 1000, 64));
            CityLevel[] levels = CityLevel.values();
            for (int i = 0; i < 16; i++) {
                cities.add(new RoadViews.CityRow(i + 1L, LONG_CITY, -29_999_999, -64, 29_999_999 - i, false, 256, levels[i % levels.length], 99_999,
                        99_999, i % 3 == 0 ? 0 : MUCH - i, i % 3 == 0 ? null : Wall.WallClass.values()[i % 2], i * 70, 64));
            }
            Road.Status[] statuses = Road.Status.values();
            for (int i = 0; i < 20; i++) {
                Road.Status status = statuses[i % statuses.length];
                roads.add(new RoadViews.RoadRow(MUCH - i, LONG_CITY, i % 2 == 0 ? Component.translatable("kingdomworld.road.capital_of", LONG_KINGDOM)
                        .getString() : "точка -29999999 29999999", RoadClass.values()[i % 3], 8_192, i * 50, 1000 - i * 50, status, i % 5 == 4,
                        i % 3 != 2, i % 3 == 2 ? LONG_KINGDOM + ", " + LONG_KINGDOM : "", Road.Mode.values()[i % 3], 512, 512,
                        new int[]{999_999, 999_999, 999_999}));
            }
            Crossing.Status[] states = Crossing.Status.values();
            for (int i = 0; i < 12; i++) {
                crossings.add(new RoadViews.CrossingRow(i + 1L, MUCH - i, i % 2 == 0, LONG_KINGDOM, Road.Mode.values()[1 + i % 2],
                        states[i % states.length], 512, 24_000L * 30));
            }
            for (int i = 0; i < 12; i++) {
                camps.add(new RoadViews.CampRow(i + 1L, -29_999_999, 29_999_999, 99_999, 64, i % 2 == 0, MUCH, 999_999));
            }
            for (int i = 0; i < 12; i++) {
                risks.add(new RoadViews.RiskRow(MUCH - i, LONG_KINGDOM, new ItemStack(i % 2 == 0 ? Items.LIGHT_BLUE_GLAZED_TERRACOTTA
                        : Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), i % 2 == 0, 1_000 - i * 80, i * 90, 99_999, i % 3 == 0));
            }
            for (int i = 0; i < 14; i++) {
                squads.add(new RoadViews.SquadRow(MUCH - i, "#" + (MUCH - i), 32));
            }
        }
        return new RoadViews.RoadTabView(kingdom, canManage, canManage, empty ? 0 : 999, 256, 1_024, cities, roads, crossings, camps, risks,
                squads);
    }

    private static ThroneScreen roadsTab(Function<Role, KingdomView> member, Role role, boolean canManage, boolean empty, String mode,
                                         long acting, long patrolling, Component status) {
        KingdomView view = member.apply(role);
        ThroneScreen screen = new ThroneScreen(view);
        screen.selectTab("roads");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateRoads(tab(view.id(), canManage, empty));
        screen.showRoads(mode, acting, patrolling);
        return screen;
    }

    private static String dimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().location().toString();
    }

    /**
     * Cities, roads and a camp around the middle of the map.
     */
    private static void fillMapData() {
        String dimension = dimension();
        UUID kingdom = UUID.nameUUIDFromBytes("roads".getBytes(StandardCharsets.UTF_8));
        List<RoadViews.MapCity> cities = List.of(new RoadViews.MapCity(1, kingdom, LONG_CITY, dimension, -40, -30, 0x6C3483, CityLevel.CITY),
                new RoadViews.MapCity(2, kingdom, "Ostbrook", dimension, 60, -20, 0xB7950B, CityLevel.VILLAGE),
                new RoadViews.MapCity(0, kingdom, LONG_KINGDOM, dimension, 8, 8, 0x6C3483, CityLevel.TOWN));
        List<RoadViews.MapWall> walls = List.of(
                new RoadViews.MapWall(1, dimension, -40, -30, 48, Wall.WallClass.STONE, 1000, new int[]{8, -88}, new int[]{-78, -30}),
                new RoadViews.MapWall(2, dimension, 60, -20, 24, Wall.WallClass.PALISADE, 400, new int[]{36}, new int[]{-20}));
        List<RoadViews.MapRoad> roads = List.of(
                new RoadViews.MapRoad(1, kingdom, dimension, RoadClass.PAVED, 0x6C3483, 1000, new int[]{-40, 0, 20, 60}, new int[]{-30, -28, -10, -20}),
                new RoadViews.MapRoad(2, kingdom, dimension, RoadClass.GRAVEL, 0x6C3483, 400, new int[]{-40, -60, -90}, new int[]{-30, 10, 30}),
                new RoadViews.MapRoad(3, kingdom, dimension, RoadClass.PATH, 0x6C3483, 0, new int[]{60, 90, 120}, new int[]{-20, -40, -60}));
        ClientRoadCache.replace(new RoadViews.MapData(cities, roads, List.of(new RoadViews.MapCamp(1, dimension, 20, 40, 64)), walls));
    }

    private static RoadViews.PlanView plan(UUID kingdom) {
        int[] xs = new int[200];
        int[] zs = new int[200];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = -40 + i;
            zs[i] = (int) Math.round(-30 + 20 * Math.sin(i / 15.0));
        }
        return new RoadViews.PlanView(MUCH, kingdom, "", false, LONG_CITY, Component.translatable("kingdomworld.road.capital_of", LONG_KINGDOM)
                .getString(), 8_192, 999, 999, new int[]{0, 999_999, 999_999, 999_999, 999_999, 999_999, 999_999, 999_999, 999_999},
                new int[]{999_999, 999_999, 999_999}, 999, List.of(LONG_KINGDOM, LONG_KINGDOM), xs, zs);
    }

    private static KingdomMapScreen planning(Function<Role, KingdomView> member, boolean withPlan, Component status) {
        fillMapData();
        KingdomView view = member.apply(Role.KING);
        KingdomMapScreen screen = KingdomMapScreen.forRoads(view);
        screen.showForCheck(1.0, -0.5, 14.0, null, status, !status.getString().isEmpty());
        screen.showPlanForCheck(-40, -30, 160, -30, withPlan ? plan(view.id()) : null);
        return screen;
    }

    /**
     * Adds every case of roads.
     *
     * @param member a kingdom seen by a member of the position
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.ROUTE_TOO_LONG);
        add.accept("throne-roads-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("roads");
            return screen;
        });
        add.accept("throne-roads-overview", () -> roadsTab(member, Role.KING, true, false, "overview", 0, 0, Component.empty()));
        add.accept("throne-roads-overview-subject", () -> roadsTab(member, Role.SUBJECT, false, false, "overview", 0, 0, Component.empty()));
        add.accept("throne-roads-overview-empty", () -> roadsTab(member, Role.KING, true, true, "overview", 0, 0, longError));
        add.accept("throne-roads-cities", () -> roadsTab(member, Role.KING, true, false, "cities", 0, 0, Component.empty()));
        add.accept("throne-roads-cities-actions", () -> roadsTab(member, Role.KING, true, false, "cities", 2, 0, Component.empty()));
        add.accept("throne-roads-cities-actions-capital", () -> roadsTab(member, Role.KING, true, false, "cities", 1, 0, Component.empty()));
        add.accept("throne-roads-cities-subject", () -> roadsTab(member, Role.SUBJECT, false, false, "cities", 0, 0, Component.empty()));
        add.accept("throne-roads-cities-empty", () -> roadsTab(member, Role.SUBJECT, false, true, "cities", 0, 0, Component.empty()));
        add.accept("throne-roads-roads", () -> roadsTab(member, Role.KING, true, false, "roads", 0, 0, Component.empty()));
        add.accept("throne-roads-roads-subject", () -> roadsTab(member, Role.SUBJECT, false, false, "roads", 0, 0, longError));
        add.accept("throne-roads-roads-empty", () -> roadsTab(member, Role.KING, true, true, "roads", 0, 0, Component.empty()));
        add.accept("throne-roads-actions", () -> roadsTab(member, Role.KING, true, false, "roads", MUCH, 0, Component.empty()));
        add.accept("throne-roads-actions-partner", () -> roadsTab(member, Role.KING, true, false, "roads", MUCH - 2, 0, Component.empty()));
        add.accept("throne-roads-patrol", () -> roadsTab(member, Role.KING, true, false, "roads", 0, MUCH, Component.empty()));
        add.accept("throne-roads-patrol-empty", () -> roadsTab(member, Role.KING, true, true, "roads", 0, MUCH, Component.empty()));
        add.accept("throne-roads-requests", () -> roadsTab(member, Role.KING, true, false, "requests", 0, 0, longError));
        add.accept("throne-roads-requests-empty", () -> roadsTab(member, Role.KING, true, true, "requests", 0, 0, Component.empty()));
        add.accept("throne-roads-camps", () -> roadsTab(member, Role.KING, true, false, "camps", 0, 0, Component.empty()));
        add.accept("throne-roads-camps-empty", () -> roadsTab(member, Role.KING, true, true, "camps", 0, 0, Component.empty()));
        add.accept("throne-roads-risks", () -> roadsTab(member, Role.KING, true, false, "risks", 0, 0, Component.empty()));
        add.accept("throne-roads-risks-empty", () -> roadsTab(member, Role.SUBJECT, false, true, "risks", 0, 0, Component.empty()));
        add.accept("map-roads", () -> planning(member, false, Component.empty()));
        add.accept("map-roads-plan", () -> planning(member, true, Component.empty()));
        add.accept("map-roads-error", () -> planning(member, false, longError));
        add.accept("map-view-roads", () -> {
            fillMapData();
            KingdomMapScreen screen = new KingdomMapScreen(null);
            screen.showForCheck(0.0, -1.0, 10.0, null, Component.empty(), false);
            return screen;
        });
        add.accept("town-hall-tooltip", () -> new ItemTooltipScreen("town-hall", List.of(new ItemStack(RoadRegistries.TOWN_HALL_ITEM.get()))));
    }
}
