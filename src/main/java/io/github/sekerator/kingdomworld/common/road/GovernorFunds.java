package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.MarketActions;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.CityGrowth;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where the money of the governors of cities comes from, in one place. A governor builds from the storehouses of the kingdom (its
 * constructions take what they need there themselves) and buys what they lack abroad.
 *
 * <p>Where the cities keep money of their own ({@link io.github.sekerator.kingdomworld.common.commerce.CityOf#active}), a governor
 * pays out of the treasury of its city through {@link CityFunds}: what the city kept of its taxes and what the crown granted it,
 * less a reserve of a few days of its income and what it promised its constructions. The money the king gives the governors a
 * day goes into the treasuries of their cities as a grant, each city its fair share, never more than a week of shares granted
 * and unspent and never so that the treasury of the kingdom falls below its reserve. The city pays the crown for an import first
 * and gets back what the crown could not buy.
 *
 * <p>Elsewhere (a kingdom of the world, or the money of the cities switched off) a governor spends its share of the money of the
 * king a day straight out of the treasury of the kingdom, never below its reserve. What is spent is counted for the city and for
 * the court; the purchase goes into the journal of the treasury as any purchase abroad.
 */
public final class GovernorFunds {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many shares of the money of the king a city holds granted and unspent at most. */
    static final int GRANT_KEEP_DAYS = 7;

    private GovernorFunds() {
    }

    /**
     * Whether the governors of a kingdom pay out of the treasuries of their cities.
     */
    public static boolean fromCity(MinecraftServer server, UUID kingdomId) {
        return io.github.sekerator.kingdomworld.common.commerce.CityOf.active(server, kingdomId);
    }

    /**
     * Once a day before the governor of a city decides, where it pays out of the treasury of its city: its share of the money of
     * the king goes there as a grant ({@link CityGrowth#grant}).
     *
     * @return what was granted
     */
    public static long grantShare(MinecraftServer server, UUID kingdomId, long cityId, int governed, RulerView view, RulerRules rules) {
        if (!fromCity(server, kingdomId)) {
            return 0;
        }
        long share = CityGrowth.share(allowance(server, kingdomId), governed);
        if (share <= 0) {
            return 0;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        long unspent = economy.cityTreasury(kingdomId, cityId).map(CityTreasury::granted).orElse(0L);
        long amount = CityGrowth.grant(share, unspent, GRANT_KEEP_DAYS, view.balance, RulerMind.reserve(view, rules));
        if (amount <= 0 || !economy.cityGrant(kingdomId, cityId, amount, TreasuryActions.day(server)).ok()) {
            return 0;
        }
        view.balance -= amount;
        LOGGER.debug("The king granted the governor of city {} of {} {} for the day", cityId, kingdomId, amount);
        return amount;
    }

    /**
     * What the king gives the governors of a kingdom a day; 0 when it gives nothing.
     */
    public static long allowance(MinecraftServer server, UUID kingdomId) {
        if (NobilityWork.isReadOnly(server)) {
            return 0;
        }
        return NobilityWork.state(server).existingCourt(kingdomId).map(Court::governorAllowance).orElse(0L);
    }

    /**
     * What the governors of a kingdom spent today.
     */
    public static long spentToday(MinecraftServer server, UUID kingdomId) {
        if (NobilityWork.isReadOnly(server)) {
            return 0;
        }
        long day = NobilityWork.day(server);
        return NobilityWork.state(server).existingCourt(kingdomId).map(court -> court.governorSpent(day)).orElse(0L);
    }

    /**
     * What the governor of a city may still spend today: what is left of its share of the allowance, and never so much that the
     * treasury falls below the reserve a ruler would keep with its expenses ({@link RulerMind#reserve}).
     *
     * @param governed the cities of the kingdom with a governor, who share the allowance
     * @param view     the money of the treasury and its expenses
     */
    public static long budget(MinecraftServer server, UUID kingdomId, CityCharter charter, long day, int governed, RulerView view, RulerRules rules) {
        if (fromCity(server, kingdomId)) {
            return CityFunds.available(KingdomWorldApi.state(server).economy(), kingdomId, charter.cityId(),
                    io.github.sekerator.kingdomworld.common.commerce.CityEconomySettings.rules());
        }
        return CityGrowth.budget(allowance(server, kingdomId), governed, charter.spent(day), view.balance, RulerMind.reserve(view, rules));
    }

    /**
     * The one way a governor pays: the goods the constructions of its city lack, beyond what the storehouses hold and is bought
     * already, are bought abroad, at most the budget of the day and as many goods as the market lets in a day. What is spent is
     * counted for the city and for the court.
     *
     * @return what was spent
     */
    public static long pay(MinecraftServer server, UUID kingdomId, long cityId, CityCharter charter, long day, long budget) {
        if (budget <= 0) {
            return 0;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Map<String, Integer> lacking = new LinkedHashMap<>();
        for (Construction construction : state.construction().of(kingdomId)) {
            if (construction.paused() || ConstructionWork.cityOf(server, construction) != cityId) {
                continue;
            }
            ConstructionWork.plan(construction).ifPresent(plan -> ConstructionWork.missing(server, construction, plan)
                    .forEach((item, count) -> lacking.merge(item, count, Integer::sum)));
        }
        if (lacking.isEmpty()) {
            return 0;
        }
        TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(kingdomId, rules);
        for (Market.ImportOrder order : market.imports()) {
            lacking.computeIfPresent(order.item(), (item, count) -> count - order.count() > 0 ? count - order.count() : null);
        }
        int left = rules.importMaxPerDay() - market.stats(TreasuryActions.day(server)).imported();
        boolean fromCity = fromCity(server, kingdomId);
        Economy economy = state.economy();
        CityEconomyRules cityRules = io.github.sekerator.kingdomworld.common.commerce.CityEconomySettings.rules();
        long today = TreasuryActions.day(server);
        long spent = 0;
        for (Map.Entry<String, Integer> entry : lacking.entrySet()) {
            if (left <= 0 || spent >= budget) {
                break;
            }
            Item item = GoodsStacks.item(entry.getKey());
            if (item == Items.AIR || !MarketActions.isImportable(server, item)) {
                continue;
            }
            double worth = MarketStock.worth(server, item).amount();
            int count = Math.min(entry.getValue(), left);
            while (count > 0 && Prices.importCost(worth, count, rules) > budget - spent) {
                count /= 2;
            }
            if (count <= 0) {
                continue;
            }
            long cost = Prices.importCost(worth, count, rules);
            // Out of the treasury of the city: it pays the crown first, the crown buys, and what it could not buy goes back.
            if (fromCity && !CityFunds.payWorks(economy, kingdomId, cityId, cost, today, cityRules).ok()) {
                break;
            }
            if (MarketActions.orderImport(server, null, kingdomId, item, count).success()) {
                spent += cost;
                left -= count;
                LOGGER.info("The governor of city {} of {} bought {} {} abroad for {}", cityId, kingdomId, count, entry.getKey(), cost);
            } else if (fromCity) {
                CityFunds.refundWorks(economy, kingdomId, cityId, cost, today);
            }
        }
        if (spent > 0) {
            charter.spend(day, spent);
            RoadWork.state(server).charterChanged();
            NobilityWork.state(server).court(kingdomId).governorSpend(day, spent);
            NobilityWork.state(server).changed();
        }
        return spent;
    }
}
