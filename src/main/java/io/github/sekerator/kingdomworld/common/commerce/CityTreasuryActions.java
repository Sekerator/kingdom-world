package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CityViews;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.commerce.CityMeans;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the king does with the treasuries of the cities, from the "Treasury" tab: the taxes of the kingdom and of each city,
 * grants and the money taken back, and the views of the cities. Members see them; changes need the right to manage the
 * treasury. The server checks the right, the city, the ranges and the money of every request.
 */
public final class CityTreasuryActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.treasury.cities.";
    /** The page of the cities each player looks at. */
    private static final Map<UUID, Integer> PAGES = new HashMap<>();

    private CityTreasuryActions() {
    }

    public static void setPage(ServerPlayer player, int page) {
        PAGES.put(player.getUUID(), Math.max(0, Math.min(1_000_000, page)));
    }

    // ---------------------------------------------------------------- views

    /**
     * The cities of a kingdom for a member: the taxes, a page of rows, the totals.
     */
    public static CityViews.Cities cities(MinecraftServer server, UUID kingdom, UUID viewer) {
        if (!CityOf.active(server, kingdom)) {
            return CityViews.Cities.NONE;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        Optional<Treasury> treasury = economy.treasury(kingdom);
        if (treasury.isEmpty()) {
            return CityViews.Cities.NONE;
        }
        CityViews.Taxes taxes = new CityViews.Taxes(CityEconomySettings.salesTax(treasury.get()), CityEconomySettings.tithe(treasury.get()),
                CityEconomySettings.crownShare(treasury.get()));
        List<CityWalls.Place> places = CityPeople.places(server, kingdom);
        int page = PAGES.getOrDefault(viewer, 0);
        int pages = Math.max(1, (places.size() + CityViews.PAGE - 1) / CityViews.PAGE);
        page = Math.min(page, pages - 1);
        List<CityViews.CityRow> rows = new ArrayList<>();
        long crown = 0;
        long held = 0;
        for (int i = 0; i < places.size(); i++) {
            CityWalls.Place place = places.get(i);
            CityViews.CityRow row = row(economy, kingdom, place);
            crown = Money.addCapped(crown, row.toCrown());
            held = Money.addCapped(held, row.balance());
            if (i >= page * CityViews.PAGE && rows.size() < CityViews.PAGE) {
                rows.add(row);
            }
        }
        return new CityViews.Cities(true, taxes, CityEconomyRules.MAX_SALES_TAX_PERCENT, CityEconomyRules.MAX_TITHE_PERCENT, rows, page,
                places.size(), crown, held);
    }

    private static CityViews.CityRow row(Economy economy, UUID kingdom, CityWalls.Place place) {
        Optional<CityTreasury> city = economy.cityTreasury(kingdom, place.cityId());
        List<CityStatement> statements = city.map(CityTreasury::statements).orElse(List.of());
        long balance = city.map(found -> economy.purse(found.key())).orElse(0L);
        long money = 0;
        long goods = 0;
        long income = 0;
        for (CityStatement statement : statements) {
            money += statement.crownMoney();
            goods += statement.crownGoods();
            income = Money.addCapped(income, statement.taxIncome());
        }
        int days = Math.max(1, statements.size());
        return new CityViews.CityRow(place.cityId(), place.name(), place.cityId() == 0, income / days,
                CityStatement.average(statements, CityStatement.Line.KEPT), CityStatement.average(statements, CityStatement.Line.TO_CROWN),
                CityStatement.average(statements, CityStatement.Line.SPENT), balance, statements.isEmpty() ? 0 : money / days,
                statements.isEmpty() ? 0 : goods / days);
    }

    public static void sendDetail(ServerPlayer player, UUID kingdom, long city) {
        if (city < 0) {
            return;
        }
        detail(player.server, kingdom, city, player.getUUID())
                .ifPresent(detail -> KingdomWorldNetwork.sendToPlayer(player, new EconomyPackets.CityDetailResponse(detail)));
    }

    /**
     * The window of a city for a member of its kingdom; empty for anybody else or a city it does not have.
     */
    public static Optional<CityViews.CityDetail> detail(MinecraftServer server, UUID kingdom, long city, UUID viewer) {
        var state = KingdomWorldApi.state(server);
        if (!state.membership().isMember(viewer, kingdom) || !CityOf.active(server, kingdom)) {
            return Optional.empty();
        }
        Optional<CityWalls.Place> place = CityWalls.place(server, kingdom, city);
        Optional<Treasury> treasury = state.economy().treasury(kingdom);
        if (place.isEmpty() || treasury.isEmpty()) {
            return Optional.empty();
        }
        Economy economy = state.economy();
        CityTreasury found = economy.openCityTreasury(kingdom, city);
        List<CityStatement> statements = found.statements();
        long[] average = new long[CityStatement.LINES];
        for (CityStatement.Line line : CityStatement.Line.values()) {
            average[line.ordinal()] = CityStatement.average(statements, line);
        }
        CityViews.Taxes own = new CityViews.Taxes(found.salesTaxSetting(), found.titheSetting(), found.crownShareSetting());
        CityViews.Taxes effective = new CityViews.Taxes(CommerceWork.salesTax(server, kingdom, city), CommerceWork.tithe(server, kingdom, city),
                CommerceWork.crownShare(server, kingdom, city));
        CityMeans means = CommerceWork.meansOf(server, kingdom, city);
        Shops.Overview shops = Shops.overview(server, kingdom, city);
        return Optional.of(new CityViews.CityDetail(kingdom, city, place.get().name(),
                state.membership().hasPermission(viewer, kingdom, Permission.MANAGE_TREASURY), economy.purse(found.key()), found.granted(),
                found.committed(), own, effective, average, found.latest().values(), statements.size(), CommerceWork.evasion(server, kingdom, city),
                new int[]{means.burden(), means.shops(), means.recall(), means.share(), means.value()},
                CommerceWork.hasHead(server, kingdom, city), treasury.get().funds(), shops.rows(), shops.count(), shops.blocked(),
                shops.shortfall(), shops.missing(), folk(server, kingdom, city)));
    }

    /**
     * The townsfolk of a city for its window: what one of them earned on the last payday and why they do not grow or leave.
     */
    private static CityViews.CityDetail.Folk folk(MinecraftServer server, UUID kingdom, long city) {
        var charter = io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).charter(kingdom, city);
        int count = charter.map(io.github.sekerator.kingdomworld.core.road.CityCharter::folk).orElse(0);
        if (count <= 0 || !FolkWork.active(server, kingdom)) {
            return CityViews.CityDetail.Folk.NONE;
        }
        var work = io.github.sekerator.kingdomworld.common.road.CityPeople.work(server, kingdom, charter.get());
        long earned = Math.max(0, charter.get().folkEarned());
        return new CityViews.CityDetail.Folk(count, earned * 1_000L / count,
                io.github.sekerator.kingdomworld.core.road.CityFolk.why(count, work).ordinal());
    }

    // ---------------------------------------------------------------- changes

    /**
     * The city a change names in its text, -1 if it names none.
     */
    public static long cityOfText(String text) {
        String[] parts = text.split(",");
        try {
            return Long.parseLong(parts[0].strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int[] numbers(String text, int count) {
        String[] parts = text.split(",");
        if (parts.length != count) {
            return null;
        }
        int[] values = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                long value = Long.parseLong(parts[i].strip());
                if (value < -1 || value > Integer.MAX_VALUE) {
                    return null;
                }
                values[i] = (int) value;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return values;
    }

    /**
     * A decision about the treasuries of the cities from the treasury tab; the caller sends the fresh views.
     */
    public static KingdomActions.Feedback handle(ServerPlayer player, UUID kingdom, EconomyPackets.TreasuryChange change) {
        MinecraftServer server = player.server;
        if (!CityOf.active(server, kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        return switch (change.action()) {
            case CITY_DEFAULTS -> setDefaults(player, kingdom, numbers(change.text(), 3));
            case CITY_SET -> setCity(player, kingdom, numbers(change.text(), 4));
            case CITY_GRANT -> grant(player, kingdom, cityOfText(change.text()), change.amount());
            case CITY_RECALL -> recall(player, kingdom, cityOfText(change.text()), change.amount());
            default -> KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        };
    }

    private static boolean ownCity(MinecraftServer server, UUID kingdom, long city) {
        return city >= 0 && CityWalls.place(server, kingdom, city).isPresent();
    }

    public static KingdomActions.Feedback setDefaults(ServerPlayer player, UUID kingdom, int[] values) {
        if (values == null || values[0] < 0 || values[1] < 0 || values[2] < 0) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Outcome outcome = KingdomWorldApi.change(player.server, "set the taxes of the cities",
                state -> state.setKingdomCityTaxes(player.getUUID(), kingdom, values[0], values[1], values[2]));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        CommerceWork.meansChanged(kingdom);
        return KingdomActions.Feedback.ok(KEY + "taxes_set", percent(values[0]), percent(values[1]), percent(values[2]));
    }

    public static KingdomActions.Feedback setCity(ServerPlayer player, UUID kingdom, int[] values) {
        if (values == null || !ownCity(player.server, kingdom, values[0])) {
            return KingdomActions.Feedback.error(values == null ? KingdomError.VALUE_OUT_OF_RANGE : KingdomError.UNKNOWN_CITY);
        }
        Outcome outcome = KingdomWorldApi.change(player.server, "set the taxes of a city",
                state -> state.setCityTaxes(player.getUUID(), kingdom, values[0], values[1], values[2], values[3]));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        CommerceWork.meansChanged(kingdom);
        return KingdomActions.Feedback.ok(KEY + "city_taxes_set", cityName(player.server, kingdom, values[0]));
    }

    public static KingdomActions.Feedback grant(ServerPlayer player, UUID kingdom, long city, long amount) {
        if (!ownCity(player.server, kingdom, city)) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
        }
        long day = TreasuryActions.day(player.server);
        Outcome outcome = KingdomWorldApi.change(player.server, "grant a city money",
                state -> state.grantCity(player.getUUID(), kingdom, city, amount, day));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        LOGGER.info("{} granted city {} of {} {}", player.getGameProfile().getName(), city, kingdom, amount);
        return KingdomActions.Feedback.ok(KEY + "granted", MoneyTexts.amount(amount, units(player.server, kingdom)),
                cityName(player.server, kingdom, city));
    }

    public static KingdomActions.Feedback recall(ServerPlayer player, UUID kingdom, long city, long amount) {
        MinecraftServer server = player.server;
        if (!ownCity(server, kingdom, city)) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
        }
        long day = TreasuryActions.day(server);
        long average = KingdomWorldApi.state(server).economy().cityTreasury(kingdom, city).map(CityTreasury::averageTax).orElse(0L);
        Economy.Recall[] done = new Economy.Recall[1];
        Outcome outcome = KingdomWorldApi.change(server, "take money back from a city", state -> {
            done[0] = state.recallCity(player.getUUID(), kingdom, city, amount, day);
            return done[0].outcome();
        });
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        long beyond = done[0] == null ? 0 : done[0].beyond();
        CommerceWork.meansChanged(kingdom);
        if (beyond > 0 && city > 0 && !NobilityWork.isReadOnly(server)) {
            // The noble house that holds the city resents a treasury taken beyond what the crown granted.
            int loss = CityTreasury.loyaltyLoss(beyond, average);
            var nobility = NobilityWork.state(server);
            nobility.holderOf(kingdom, Fief.city(city)).filter(grant -> !grant.heldByPlayer())
                    .ifPresent(grant -> nobility.loseLoyalty(grant.house(), loss));
        }
        LOGGER.info("{} took {} back from city {} of {}, {} of it beyond the grants", player.getGameProfile().getName(), amount, city, kingdom,
                beyond);
        return KingdomActions.Feedback.ok(beyond > 0 ? KEY + "recalled_resented" : KEY + "recalled",
                MoneyTexts.amount(amount, units(server, kingdom)), cityName(server, kingdom, city));
    }

    private static Object percent(int permille) {
        return permille % 10 == 0 ? Integer.toString(permille / 10) : String.format(java.util.Locale.ROOT, "%.1f", permille / 10.0);
    }

    private static net.minecraft.network.chat.Component cityName(MinecraftServer server, UUID kingdom, long city) {
        return io.github.sekerator.kingdomworld.common.npc.NpcTexts.city(CityWalls.place(server, kingdom, city).map(CityWalls.Place::name).orElse("?"));
    }

    private static io.github.sekerator.kingdomworld.core.economy.CurrencyUnits units(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.state(server).economy().units(kingdom).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT);
    }
}
