package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ShopView;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Players in the shops of citizens: a click on a counter or on a keeper opens the window of the shop, a click on a person of a
 * house in the evening that of the family shop of the house. A player buys from the goods of the shop, whole items that fit into
 * the inventory, from the account or with coins in the currency of the kingdom of the shop only: the price into the till, the
 * tax into the treasury of the city. Strangers are served while the market of the kingdom is open to them.
 */
public final class ShopActions {
    private static final String KEY = "kingdomworld.screen.shop.";
    /** How far from the shop or its keeper a player buys. */
    private static final double REACH = 8.0;
    /** The page each player looks at, by shop. */
    private static final Map<UUID, Integer> PAGES = new HashMap<>();

    private ShopActions() {
    }

    // ---------------------------------------------------------------- opening

    /**
     * A click on a counter.
     */
    public static void openAtCounter(ServerPlayer player, BlockPos pos) {
        String key = "counter/" + CommerceSavedData.counterKey(Dimensions.id(player.level()), pos.getX(), pos.getY(), pos.getZ());
        Optional<Business> shop = CommerceSavedData.get(player.server).at(key);
        if (shop.isEmpty()) {
            player.sendSystemMessage(Component.translatable("kingdomworld.shop.counter.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        open(player, shop.get(), true);
    }

    /**
     * A click on a citizen: its shop if it keeps one, or the family shop of its house in the evening.
     *
     * @return whether a shop window was opened
     */
    public static boolean openForCitizen(ServerPlayer player, CitizenEntity body) {
        MinecraftServer server = player.server;
        if (body.citizenId() == null || player.isShiftKeyDown()) {
            return false;
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
        if (citizen.isEmpty()) {
            return false;
        }
        CommerceSavedData data = CommerceSavedData.get(server);
        if (citizen.get().business() != 0) {
            Optional<Business> shop = data.shop(citizen.get().business());
            if (shop.isPresent()) {
                open(player, shop.get(), true);
                return true;
            }
        }
        if (LifeWork.phase(server, citizen.get().id()) != DaySchedule.Phase.EVENING) {
            return false;
        }
        for (Business shop : data.shopsOf(citizen.get().kingdomId())) {
            if (!shop.family()) {
                continue;
            }
            Optional<Building> house = KingdomWorldApi.state(server).construction().building(shop.place().building());
            if (house.isPresent() && citizen.get().home() != null && house.get().box().contains(citizen.get().home().x(), citizen.get().home().y(),
                    citizen.get().home().z())) {
                open(player, shop, true);
                return true;
            }
        }
        return false;
    }

    public static void page(ServerPlayer player, long shopId, int page) {
        PAGES.put(player.getUUID(), page);
        CommerceSavedData.get(player.server).shop(shopId).ifPresent(shop -> open(player, shop, false));
    }

    private static void open(ServerPlayer player, Business shop, boolean open) {
        if (open) {
            PAGES.remove(player.getUUID());
        }
        KingdomWorldNetwork.sendToPlayer(player, new CommercePackets.ShopOpen(view(player, shop), open));
    }

    // ---------------------------------------------------------------- the view

    /**
     * Why a player may not buy in a shop now, or empty when it may.
     */
    static Optional<String> refusal(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        if (!near(player, shop)) {
            return Optional.of(KEY + "too_far");
        }
        if (shop.family()) {
            boolean evening = false;
            for (Citizen resident : residents(server, shop)) {
                if (LifeWork.phase(server, resident.id()) == DaySchedule.Phase.EVENING) {
                    evening = true;
                    break;
                }
            }
            if (!evening) {
                return Optional.of(KEY + "evening_only");
            }
        } else {
            boolean able = false;
            for (UUID keeper : shop.keepers()) {
                Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(keeper);
                if (citizen.isPresent() && citizen.get().state() != CitizenState.WOUNDED && !LifeWork.isSick(keeper)) {
                    able = true;
                    break;
                }
            }
            if (!able) {
                return Optional.of(KEY + "keeper_unwell");
            }
        }
        boolean member = KingdomWorldApi.state(server).membership().isMember(player.getUUID(), shop.kingdomId());
        if (!member && !KingdomWorldApi.state(server).trade().market(shop.kingdomId(), TradeSettings.rules()).openToOutsiders()) {
            return Optional.of(KEY + "strangers");
        }
        return Optional.empty();
    }

    private static List<Citizen> residents(MinecraftServer server, Business shop) {
        Optional<Building> house = KingdomWorldApi.state(server).construction().building(shop.place().building());
        return house.isEmpty() ? List.of() : Shops.residents(house.get(), KingdomWorldApi.state(server).citizens().registry().citizensOf(shop.kingdomId()));
    }

    /**
     * Whether a player is near enough to a shop: to its counter, its building, or one of its keepers.
     */
    static boolean near(ServerPlayer player, Business shop) {
        Business.Place place = shop.place();
        if (!Dimensions.id(player.level()).equals(place.dimension())) {
            return false;
        }
        double reach = REACH * REACH;
        if (player.distanceToSqr(place.x() + 0.5, place.y() + 0.5, place.z() + 0.5) <= reach) {
            return true;
        }
        if (place.kind() != Business.PlaceKind.COUNTER) {
            Optional<Building> building = KingdomWorldApi.state(player.server).construction().building(place.building());
            if (building.isPresent()) {
                var box = building.get().box();
                double dx = Math.max(0, Math.max(box.minX() - player.getX(), player.getX() - box.maxX() - 1));
                double dy = Math.max(0, Math.max(box.minY() - player.getY(), player.getY() - box.maxY() - 1));
                double dz = Math.max(0, Math.max(box.minZ() - player.getZ(), player.getZ() - box.maxZ() - 1));
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    return true;
                }
            }
        }
        for (var entity : player.level().getEntitiesOfClass(CitizenEntity.class, player.getBoundingBox().inflate(REACH))) {
            if (entity.citizenId() != null && shop.keepers().contains(entity.citizenId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What one of the goods of a shop costs a player without the tax, in whole coins, for a number of them: the price of the shop
     * as the reputation of the player makes it, never below what the shop paid.
     */
    static long playerTotal(MinecraftServer server, Business shop, UUID player, Business.Lot lot, Item item, long count) {
        long asked = ShopPricing.askedMilli(lot.costMilli(), shop.marginPermille(), Shops.ceilingMilli(server, item));
        long total = ShopPricing.total(asked, count);
        long reputation = LawWork.priceFor(server, shop.kingdomId(), player, total);
        return Math.max(ShopPricing.total(lot.costMilli(), count), reputation);
    }

    static ShopView view(ServerPlayer player, Business shop) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        int tax = CommerceWork.salesTax(server, shop.kingdomId(), shop.cityId());
        List<ShopView.Row> rows = new ArrayList<>();
        List<Map.Entry<String, Business.Lot>> goods = new ArrayList<>(shop.goods().entrySet());
        Map<String, Long> unit = new HashMap<>();
        for (Map.Entry<String, Business.Lot> entry : goods) {
            Item item = GoodsStacks.item(entry.getKey());
            unit.put(entry.getKey(), item == null ? Long.MAX_VALUE : playerTotal(server, shop, player.getUUID(), entry.getValue(), item, 1));
        }
        goods.sort(Comparator.comparingLong((Map.Entry<String, Business.Lot> entry) -> unit.get(entry.getKey())).thenComparing(Map.Entry::getKey));
        int page = Math.max(0, Math.min(PAGES.getOrDefault(player.getUUID(), 0), (goods.size() - 1) / ShopView.PAGE));
        for (int i = page * ShopView.PAGE; i < goods.size() && rows.size() < ShopView.PAGE; i++) {
            Map.Entry<String, Business.Lot> entry = goods.get(i);
            Item item = GoodsStacks.item(entry.getKey());
            if (item == null) {
                continue;
            }
            long total = unit.get(entry.getKey());
            long taxOf = ShopPricing.tax(total, tax);
            rows.add(new ShopView.Row(new ItemStack(item), entry.getValue().count(), total + taxOf, taxOf));
        }
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, shop.kingdomId());
        String kingdomName = kingdom.map(Kingdom::name).orElse("?");
        var treasury = economy.treasury(shop.kingdomId());
        String currency = MoneyTexts.currencyShort(treasury.map(t -> t.currencyName()).orElse(""), kingdomName).getString();
        long silver = treasury.map(t -> t.units().silver()).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT.silver());
        long gold = treasury.map(t -> t.units().gold()).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT.gold());
        Optional<String> refused = refusal(player, shop);
        long day = TreasuryActions.day(server);
        return new ShopView(shop.id(), shop.kingdomId(), kingdomName.length() > ShopView.MAX_NAME ? kingdomName.substring(0, ShopView.MAX_NAME) : kingdomName,
                currency.length() > ShopView.MAX_NAME ? currency.substring(0, ShopView.MAX_NAME) : currency, silver, gold, shop.trade(),
                Shops.keeperNames(server, shop), shop.status().id(), rows, page, goods.size(), shop.marginPermille(), tax,
                shop.averageSales(day + 1), shop.debt(), refused.isEmpty(), refused.orElse(""));
    }

    // ---------------------------------------------------------------- buying

    public static void buy(ServerPlayer player, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        Optional<Business> found = CommerceSavedData.get(server).shop(trade.shopId());
        if (found.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "gone"));
            return;
        }
        Business shop = found.get();
        KingdomActions.reply(player, purchase(player, shop, trade));
        open(player, shop, false);
    }

    public static KingdomActions.Feedback purchase(ServerPlayer player, Business shop, CommercePackets.ShopTrade trade) {
        MinecraftServer server = player.server;
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<String> refused = refusal(player, shop);
        if (refused.isPresent()) {
            return KingdomActions.Feedback.fail(refused.get());
        }
        Business.Lot lot = shop.goods().get(trade.item());
        Item item = GoodsStacks.item(trade.item());
        if (lot == null || item == null || lot.count() < trade.count()) {
            return KingdomActions.Feedback.fail(KEY + "less_stock");
        }
        UUID kingdom = shop.kingdomId();
        int taxRate = CommerceWork.salesTax(server, kingdom, shop.cityId());
        long unit = playerTotal(server, shop, player.getUUID(), lot, item, 1);
        if (unit + ShopPricing.tax(unit, taxRate) > trade.seen()) {
            return KingdomActions.Feedback.fail(KEY + "price_changed");
        }
        long total = playerTotal(server, shop, player.getUUID(), lot, item, trade.count());
        long tax = ShopPricing.tax(total, taxRate);
        long cost = total + tax;
        if (!Money.isPayment(cost)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        List<ItemStack> stacks = new ArrayList<>();
        long left = trade.count();
        while (left > 0) {
            int part = (int) Math.min(left, item.getMaxStackSize());
            stacks.add(new ItemStack(item, part));
            left -= part;
        }
        if (!GoodsStacks.fits(player.getInventory(), stacks)) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom, shop.id());
        CityPurses.PurseKey treasury = CityPurses.PurseKey.cityTreasury(kingdom, shop.cityId());
        long collected = Evasion.collected(tax, CommerceWork.evasion(server, kingdom, shop.cityId()));
        long toTill = cost - collected;
        Object prepared = Purses.preparePayment(player, kingdom, kingdom, cost, trade.account(),
                payer -> economy.checkPlayerPurchase(payer, till, treasury, toTill, collected));
        if (prepared instanceof KingdomActions.Feedback feedback) {
            if (!trade.account() && Purses.plan(player, kingdom, cost).isEmpty()) {
                // Coins of another currency do not count: the shop takes only its own.
                String currency = MoneyTexts.currencyShort(economy.treasury(kingdom).map(t -> t.currencyName()).orElse(""),
                        KingdomWorldApi.kingdom(server, kingdom).map(Kingdom::name).orElse("?")).getString();
                return KingdomActions.Feedback.fail(KEY + "only_currency", currency);
            }
            return feedback;
        }
        Purses.Payment payment = (Purses.Payment) prepared;
        if (payment.payer() == null && !Purses.take(player, kingdom, payment.coins())) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        long day = TreasuryActions.day(server);
        Outcome paid = economy.playerPurchase(payment.payer(), till, treasury, toTill, collected, day);
        if (!paid.ok()) {
            if (payment.payer() == null) {
                Purses.giveBack(player, kingdom, payment.coins());
            }
            return KingdomActions.Feedback.error(paid.error());
        }
        Purses.change(player, payment);
        long soldCost = ShopPricing.total(lot.costMilli(), trade.count());
        shop.removeGoods(trade.item(), trade.count());
        for (ItemStack stack : stacks) {
            GoodsStacks.give(player, stack);
        }
        long[] categories = new long[GoodsCategory.values().length];
        if (lot.category() >= 0 && lot.category() < categories.length) {
            categories[lot.category()] = (long) Math.floor(Shops.worth(server, item) * trade.count());
        }
        shop.record(day, new Business.Day(toTill, soldCost, 0, 0, categories));
        CommerceSavedData.get(server).setDirty();
        CityStatement.Tally tally = economy.cityTally(kingdom, shop.cityId());
        tally.add(CityStatement.Line.SALES, total);
        tally.add(CityStatement.Line.PLAYERS, cost);
        LawWork.traded(server, kingdom, player.getUUID(), cost);
        return KingdomActions.Feedback.ok(KEY + "bought", trade.count(), new ItemStack(item).getHoverName(),
                MoneyTexts.amount(cost, Purses.units(server, kingdom)));
    }

    /**
     * The count of an item in the storehouses, for checks.
     */
    static long stock(MinecraftServer server, UUID kingdom, String id) {
        return KingdomStorage.countsById(server, kingdom).getOrDefault(id, 0L);
    }
}
