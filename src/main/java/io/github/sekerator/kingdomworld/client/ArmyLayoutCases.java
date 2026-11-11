package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.BarracksScreen;
import io.github.sekerator.kingdomworld.client.screen.ItemTooltipScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.army.ArmyRegistries;
import io.github.sekerator.kingdomworld.common.army.ArmyTexts;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * The screens and messages of the army in the layout check: the army tab of the throne screen in every view and state, the
 * barracks, the tooltip of the marshal's baton and the lines above the hotbar. Views carry the largest numbers, the longest
 * names and more rows than fit.
 */
final class ArmyLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final long MUCH = 999_999_999_999L;
    private static final UUID KINGDOM = UUID.nameUUIDFromBytes("army".getBytes(StandardCharsets.UTF_8));
    private static final BlockPos FAR = new BlockPos(-30_000_000, 319, 29_999_999);
    private static final WorldPos FAR_POS = new WorldPos("minecraft:overworld", -29_999_999, 319, 29_999_999);

    private ArmyLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(KINGDOM, LONG_CURRENCY, LONG_KINGDOM, 0x922B21, 10, 100, 9_990_000L);
    }

    private static int longestNameSeed(Gender gender) {
        CitizenNames.Alphabet alphabet = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected());
        int best = 0;
        int bestWidth = -1;
        for (int seed = 0; seed < 20_000; seed += 7) {
            int width = Minecraft.getInstance().font.width(CitizenNames.render(seed, gender, alphabet));
            if (width > bestWidth) {
                best = seed;
                bestWidth = width;
            }
        }
        return best;
    }

    private static Component longName(int i) {
        Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
        return CitizenTexts.name(i < 2 ? longestNameSeed(gender) : i * 7_919, gender);
    }

    private static Order longOrder(int i) {
        return switch (i % 6) {
            case 0 -> Order.hold(FAR_POS);
            case 1 -> Order.patrol(List.of(FAR_POS, FAR_POS, FAR_POS, FAR_POS, FAR_POS, FAR_POS, FAR_POS, FAR_POS));
            case 2 -> Order.guard(FAR_POS, Order.MAX_RADIUS);
            case 3 -> Order.follow(UUID.randomUUID());
            case 4 -> Order.escort(MUCH);
            default -> Order.NONE;
        };
    }

    private static Component orderText(Order order) {
        return ArmyTexts.order(order, Component.literal("WWWWWWWWWWWWWWWW"), Component.translatable("kingdomworld.army.deal_goods", 4_096,
                new ItemStack(Items.LIGHT_BLUE_GLAZED_TERRACOTTA).getHoverName()));
    }

    private static List<ArmyViews.SoldierRow> soldiers(int count) {
        List<ArmyViews.SoldierRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack weapon = i % 4 == 3 ? ItemStack.EMPTY : new ItemStack(i % 2 == 0 ? Items.NETHERITE_SWORD : Items.CROSSBOW);
            Component where = switch (i % 4) {
                case 0 -> Component.translatable("kingdomworld.army.where.here", -29_999_999, 319, 29_999_999);
                case 1 -> Component.translatable("kingdomworld.army.where.road");
                case 2 -> ArmyTexts.orderName(Order.Kind.PATROL);
                default -> Component.translatable("kingdomworld.army.where.training");
            };
            rows.add(new ArmyViews.SoldierRow(UUID.nameUUIDFromBytes(("soldier" + i).getBytes(StandardCharsets.UTF_8)), longName(i), i % 2 == 1,
                    SoldierRank.ofIndex(i % SoldierRank.COUNT), io.github.sekerator.kingdomworld.core.army.Soldier.MAX_EXPERIENCE, 999_999, weapon, 30, i % 5 == 4 ? CitizenState.WOUNDED : CitizenState.HEALTHY,
                    1_024, i % 3 == 0 ? 0 : 99, where, i % 2 == 0 ? io.github.sekerator.kingdomworld.core.army.Branch.GUARD
                    : io.github.sekerator.kingdomworld.core.army.Branch.ARMY,
                    io.github.sekerator.kingdomworld.core.army.Post.values()[i % 3 == 0 ? 0 : 1 + i % 2], 1_024));
        }
        return rows;
    }

    private static ArmyViews.ArmyTabView tab(UUID kingdom, boolean canManage, boolean empty) {
        List<ArmyViews.SquadRow> squads = new ArrayList<>();
        List<ArmyViews.CandidateRow> candidates = new ArrayList<>();
        List<ArmyViews.BandRow> offers = new ArrayList<>();
        List<ArmyViews.BandRow> hired = new ArrayList<>();
        List<ArmyViews.DealRow> deals = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < 14; i++) {
                Order order = longOrder(i);
                squads.add(new ArmyViews.SquadRow(i + 1L, i % 3 == 2, Component.translatable(i % 3 == 2 ? "kingdomworld.army.band_squad"
                        : "kingdomworld.army.squad", MUCH), 32, longName(i), order.kind(), orderText(order), 999_999, i % 4 == 1));
            }
            String[] professions = {"unemployed", "lumberjack", "registrar", "adventurer", "carrier"};
            for (int i = 0; i < 24; i++) {
                candidates.add(new ArmyViews.CandidateRow(UUID.nameUUIDFromBytes(("candidate" + i).getBytes(StandardCharsets.UTF_8)), longName(i),
                        professions[i % professions.length], i % 2 == 0, i % 6 == 5 ? CitizenState.WOUNDED : CitizenState.HEALTHY));
            }
            for (int i = 0; i < 3; i++) {
                offers.add(new ArmyViews.BandRow(i + 1L, longName(i), 16, SoldierRank.ofIndex(i + 3), 999_999, MUCH, MUCH, 0,
                        new ItemStack(i == 1 ? Items.CROSSBOW : Items.NETHERITE_SWORD), false, 0));
            }
            for (int i = 0; i < 6; i++) {
                hired.add(new ArmyViews.BandRow(i + 10L, longName(i + 3), 16, SoldierRank.CAPTAIN, 999_999, MUCH, MUCH, 30,
                        new ItemStack(Items.DIAMOND_AXE), true, i + 20L));
            }
            for (int i = 0; i < 12; i++) {
                deals.add(new ArmyViews.DealRow(MUCH - i, LONG_KINGDOM, new ItemStack(i % 2 == 0 ? Items.LIGHT_BLUE_GLAZED_TERRACOTTA
                        : Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 4_096, i % 2 == 0, i % 3 == 0 ? 0 : MUCH, 1_000 - i, i % 4 == 3));
            }
        }
        return new ArmyViews.ArmyTabView(kingdom, canManage, currency(), empty ? 0 : 1_024, 1_024, empty ? 0 : 1_024, 1_024, 999_999_999,
                MUCH, MUCH, empty ? 0 : 999, 255_744, 30, empty ? List.of() : soldiers(24), squads, candidates, offers, hired, deals);
    }

    private static ThroneScreen armyTab(Function<Role, KingdomView> member, Role role, boolean canManage, boolean empty, String mode, int chosen,
                                        long ordering, long escorting, Component status) {
        KingdomView view = member.apply(role);
        ThroneScreen screen = new ThroneScreen(view);
        screen.selectTab("army");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateArmy(tab(view.id(), canManage, empty));
        screen.showArmy(mode, chosen, ordering, escorting);
        return screen;
    }

    private static BarracksScreen barracks(boolean canManage, boolean empty) {
        Component nearest = empty ? Component.empty() : longName(0);
        return new BarracksScreen(new ArmyViews.BarracksView(FAR, LONG_KINGDOM, 0x922B21, canManage, 1_024, 1_024, 999, 256,
                1_000_000_000L, 1_000, empty ? List.of() : soldiers(24), nearest));
    }

    private static ItemStack baton(long squad, int mode) {
        ItemStack stack = new ItemStack(ArmyRegistries.MARSHAL_BATON.get());
        if (squad > 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong("Squad", squad);
            tag.putInt("Mode", mode);
        }
        return stack;
    }

    /**
     * Lines above the hotbar: the baton choosing squads, changing modes and giving orders, and the barracks refusing.
     */
    static void actionbars(Font font, int narrowest) {
        Component squad = Component.translatable("kingdomworld.army.band_squad", MUCH);
        for (Order.Kind kind : Order.Kind.values()) {
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.baton.chosen", squad, ArmyTexts.orderName(kind)), narrowest);
        }
        for (String mode : List.of("hold", "patrol", "guard")) {
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.baton.mode_set",
                    Component.translatable("kingdomworld.army.baton.mode." + mode)), narrowest);
        }
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.baton.ordered", squad,
                ArmyTexts.orderName(Order.Kind.PATROL)), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.baton.no_squads"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.baton.no_squad_chosen"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.army.barracks.outside"), narrowest);
        LayoutCheck.actionbar(font, KingdomActions.errorMessage(KingdomError.SQUAD_ON_THE_ROAD), narrowest);
        LayoutCheck.actionbar(font, KingdomActions.errorMessage(KingdomError.BAD_ORDER), narrowest);
    }

    /**
     * Adds every case of the army.
     *
     * @param member a kingdom seen by a member of the position
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.TOO_MANY_MERCENARIES);
        add.accept("throne-army-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("army");
            return screen;
        });
        add.accept("throne-army-overview", () -> armyTab(member, Role.KING, true, false, "overview", 0, 0, 0, Component.empty()));
        add.accept("throne-army-overview-subject", () -> armyTab(member, Role.SUBJECT, false, false, "overview", 0, 0, 0, Component.empty()));
        add.accept("throne-army-overview-empty", () -> armyTab(member, Role.MARSHAL, true, true, "overview", 0, 0, 0, longError));
        add.accept("throne-army-soldiers", () -> armyTab(member, Role.MARSHAL, true, false, "soldiers", 3, 0, 0, Component.empty()));
        add.accept("throne-army-soldiers-subject", () -> armyTab(member, Role.SUBJECT, false, false, "soldiers", 0, 0, 0, Component.empty()));
        add.accept("throne-army-soldiers-empty", () -> armyTab(member, Role.KING, true, true, "soldiers", 0, 0, 0, Component.empty()));
        // The branch and the post of a soldier: a vice-captain of the army with the longest name, and a fighter of the guard.
        add.accept("throne-army-service", () -> {
            ThroneScreen screen = armyTab(member, Role.MARSHAL, true, false, "soldiers", 0, 0, 0, Component.empty());
            screen.showArmyService(1);
            return screen;
        });
        add.accept("throne-army-service-fighter", () -> {
            ThroneScreen screen = armyTab(member, Role.KING, true, false, "soldiers", 0, 0, 0, longError);
            screen.showArmyService(0);
            return screen;
        });
        add.accept("throne-army-recruit", () -> armyTab(member, Role.KING, true, false, "recruit", 0, 0, 0, longError));
        add.accept("throne-army-recruit-empty", () -> armyTab(member, Role.KING, true, true, "recruit", 0, 0, 0, Component.empty()));
        add.accept("throne-army-squads", () -> armyTab(member, Role.KING, true, false, "squads", 0, 0, 0, Component.empty()));
        add.accept("throne-army-squads-subject", () -> armyTab(member, Role.SUBJECT, false, false, "squads", 0, 0, 0, Component.empty()));
        add.accept("throne-army-squads-empty", () -> armyTab(member, Role.KING, true, true, "squads", 0, 0, 0, Component.empty()));
        add.accept("throne-army-order", () -> armyTab(member, Role.MARSHAL, true, false, "squads", 0, MUCH, 0, Component.empty()));
        add.accept("throne-army-mercenaries", () -> armyTab(member, Role.KING, true, false, "mercenaries", 0, 0, 0, longError));
        add.accept("throne-army-mercenaries-empty", () -> armyTab(member, Role.KING, true, true, "mercenaries", 0, 0, 0, Component.empty()));
        add.accept("throne-army-deals", () -> armyTab(member, Role.KING, true, false, "deals", 0, 0, 0, Component.empty()));
        add.accept("throne-army-deals-empty", () -> armyTab(member, Role.SUBJECT, false, true, "deals", 0, 0, 0, Component.empty()));
        add.accept("throne-army-escort", () -> armyTab(member, Role.KING, true, false, "deals", 0, 0, MUCH, Component.empty()));
        add.accept("throne-army-escort-none", () -> armyTab(member, Role.KING, true, true, "deals", 0, 0, MUCH, Component.empty()));
        add.accept("barracks", () -> barracks(true, false));
        add.accept("barracks-member", () -> barracks(false, false));
        add.accept("barracks-empty", () -> barracks(true, true));
        add.accept("baton-tooltip", () -> new ItemTooltipScreen("baton", List.of(baton(0, 0))));
        add.accept("baton-tooltip-patrol", () -> new ItemTooltipScreen("baton-patrol", List.of(baton(MUCH, 1))));
        add.accept("baton-tooltip-guard", () -> new ItemTooltipScreen("baton-guard", List.of(baton(MUCH, 2))));
        add.accept("barracks-tooltip", () -> new ItemTooltipScreen("barracks", List.of(new ItemStack(ArmyRegistries.BARRACKS_ITEM.get()))));
    }
}
