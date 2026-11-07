package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.TavernScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.life.LifeTexts;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.gui.screens.Screen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The screens of the life of cities in the layout check: the window of a tavern in every part (goods, rumors with camps to
 * mark, the room rented or not, closed, with an error), and the life of a kingdom in the citizens tab for the king and for a
 * subject, full and empty. Views carry the largest numbers, the longest names and more rows than fit.
 */
final class LifeLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final String LONG_CITY = "Новый Великий Северный Город Щит";
    private static final long MUCH = 999_999_999_999L;
    private static final UUID KINGDOM = UUID.nameUUIDFromBytes("life".getBytes(StandardCharsets.UTF_8));

    private LifeLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(KINGDOM, LONG_CURRENCY, LONG_KINGDOM, 0x922B21, 10, 100, 9_990_000L);
    }

    private static List<LifeViews.Good> goods(int count) {
        ItemStack[] kinds = {new ItemStack(Items.BREAD), new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.RABBIT_STEW),
                new ItemStack(Items.COOKED_PORKCHOP), new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.SUSPICIOUS_STEW)};
        List<LifeViews.Good> goods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            goods.add(new LifeViews.Good(kinds[i % kinds.length].copy(), i % 2 == 0 ? MUCH : 1, i % 3 == 0 ? 9_999_999L : 0));
        }
        return goods;
    }

    private static List<LifeViews.Told> rumors(int count) {
        List<LifeViews.Told> told = new ArrayList<>();
        Component[] texts = {
                Component.translatable(LifeTexts.KEY + "rumor.camp", Component.translatable(LifeTexts.KEY + "camp.large"), 1_450,
                        Component.translatable(LifeTexts.KEY + "direction.north_west")),
                Component.translatable(LifeTexts.KEY + "rumor.contract", Component.translatable("kingdomworld.guild.contract.kind.bounty"),
                        io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, currency().units())),
                Component.translatable(LifeTexts.KEY + "rumor.wanted_bounty", "WWWWWWWWWWWWWWWW",
                        io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, currency().units())),
                Component.translatable(LifeTexts.KEY + "rumor.founded", LONG_KINGDOM, Component.translatable(LifeTexts.KEY + "direction.south_east")),
        };
        for (int i = 0; i < count; i++) {
            told.add(new LifeViews.Told(texts[i % texts.length], i % texts.length == 0 ? i + 1 : 0, i == 4));
        }
        return told;
    }

    private static LifeViews.TavernView tavern(boolean open, int goods, int rumors, int rooms, long roomLeft) {
        return new LifeViews.TavernView(KINGDOM, currency(), 12_345, true, open, goods(goods), rumors(rumors), MUCH, rooms, MUCH, roomLeft,
                MUCH, MUCH);
    }

    private static TavernScreen tavernScreen(LifeViews.TavernView view, String mode, boolean account, Component status) {
        TavernScreen screen = new TavernScreen(view);
        screen.showForCheck(mode, account);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static LifeViews.TabView life(boolean canManage, boolean empty) {
        List<LifeViews.Sick> sick = new ArrayList<>();
        List<LifeViews.Fair> fairs = new ArrayList<>();
        List<LifeViews.Event> events = new ArrayList<>();
        if (!empty) {
            List<String> professions = new ArrayList<>();
            for (var profession : Professions.REGISTRY.all()) {
                professions.add(profession.id());
            }
            for (int i = 0; i < 30; i++) {
                sick.add(new LifeViews.Sick(1_000 + i * 7, i % 2 == 0, professions.get(i % professions.size()),
                        i % 3 == 0 ? null : new BlockPos(-30_000_000, 319, 29_999_999), i % 2 == 0 ? 99 : 0));
            }
            fairs.add(new LifeViews.Fair(LONG_CITY, 0));
            fairs.add(new LifeViews.Fair(LONG_KINGDOM, 365));
            for (int i = 0; i < 50; i++) {
                String[] kinds = {"festival", "founding", "fair"};
                events.add(new LifeViews.Event(kinds[i % 3], 99_999 - i, i % 3 == 2 ? LONG_CITY : ""));
            }
        }
        return new LifeViews.TabView(KINGDOM, canManage, new int[]{9_999, 9_999, 9_999, 9_999}, new int[]{12, 12, 12, 12}, sick,
                empty ? -1 : 9_999, empty ? 0 : 365, MUCH, 99_999, 50, empty ? new int[]{0, 0} : new int[]{99, 99}, 9_999, fairs, events, currency());
    }

    /**
     * Adds every case of the life of cities.
     *
     * @param citizens the citizens tab of a member of the position, in the view of the life
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, BiFunction<Role, Boolean, ThroneScreen> citizens) {
        Component longError = KingdomActions.errorMessage(KingdomError.DEBT_TOO_HIGH);
        add.accept("tavern-goods", () -> tavernScreen(tavern(true, 20, 4, 3, 0), "goods", false, Component.empty()));
        add.accept("tavern-goods-account", () -> tavernScreen(tavern(true, 2, 4, 3, 0), "goods", true, longError));
        add.accept("tavern-goods-empty", () -> tavernScreen(tavern(true, 0, 0, 0, 0), "goods", false, Component.empty()));
        add.accept("tavern-rumors", () -> tavernScreen(tavern(true, 20, 8, 3, 0), "rumors", false, Component.empty()));
        add.accept("tavern-rumors-none", () -> tavernScreen(tavern(true, 0, 0, 0, 0), "rumors", false, Component.empty()));
        add.accept("tavern-room", () -> tavernScreen(tavern(true, 20, 4, 99, 0), "room", false, Component.empty()));
        add.accept("tavern-room-rented", () -> tavernScreen(tavern(true, 20, 4, 0, 23_999), "room", true,
                Component.translatable(LifeTexts.KEY + "tavern.rented", io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH,
                        currency().units()))));
        add.accept("tavern-closed", () -> tavernScreen(tavern(false, 20, 4, 0, 0), "goods", false, Component.empty()));
        add.accept("throne-citizens-life", () -> {
            ThroneScreen screen = citizens.apply(Role.KING, true);
            screen.updateLife(withId(screen, life(true, false)));
            return screen;
        });
        add.accept("throne-citizens-life-empty", () -> {
            ThroneScreen screen = citizens.apply(Role.KING, true);
            screen.updateLife(withId(screen, life(true, true)));
            return screen;
        });
        add.accept("throne-citizens-life-subject", () -> {
            ThroneScreen screen = citizens.apply(Role.SUBJECT, false);
            screen.updateLife(withId(screen, life(false, false)));
            return screen;
        });
        add.accept("throne-citizens-life-loading", () -> citizens.apply(Role.KING, true));
    }

    private static LifeViews.TabView withId(ThroneScreen screen, LifeViews.TabView view) {
        return new LifeViews.TabView(screen.kingdomId(), view.canManage(), view.counts(), view.hours(), view.sick(), view.festival(), view.nextIn(),
                view.cost(), view.treat(), view.mood(), view.taverns(), view.guests(), view.fairs(), view.events(), view.currency());
    }
}
