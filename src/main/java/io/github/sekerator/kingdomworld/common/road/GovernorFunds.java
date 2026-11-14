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
 * Where the money of the governors of cities comes from, in one place, so that the treasuries of the cities can take it over
 * later. Today a governor builds from the storehouses of the kingdom (its constructions take what they need there themselves)
 * and buys what they lack abroad with the money the king gives the governors a day: each city its fair share of it, the treasury
 * of the kingdom never below its reserve. What is spent is counted for the city and for the court; the purchase goes into the
 * journal of the treasury as any purchase abroad.
 */
public final class GovernorFunds {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GovernorFunds() {
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
            if (MarketActions.orderImport(server, null, kingdomId, item, count).success()) {
                spent += cost;
                left -= count;
                LOGGER.info("The governor of city {} of {} bought {} {} abroad for {}", cityId, kingdomId, count, entry.getKey(), cost);
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
