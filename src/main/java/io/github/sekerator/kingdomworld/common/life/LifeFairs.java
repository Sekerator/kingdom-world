package io.github.sekerator.kingdomworld.common.life;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadCaravans;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.life.Festivals;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fairs: a large city with a working market holds a fair for a day every {@code fairEveryDays} days. Its merchant posts get
 * more seats, its market sells a little cheaper, and merchants of every kingdom that trades with it and is linked to it by
 * road bring the surplus their kingdom exports: the goods leave their storehouses and lie on the market as lots of their
 * kingdom; what sells pays their treasury, what is left goes home after the fair. Players in the kingdom hear of it.
 */
public final class LifeFairs {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LifeFairs() {
    }

    static String key(CityWalls.Place place) {
        return place.kingdomId() + "/" + place.cityId();
    }

    private static long seed(CityWalls.Place place) {
        return place.kingdomId().getMostSignificantBits() ^ place.kingdomId().getLeastSignificantBits() * 31 + place.cityId();
    }

    /**
     * Whether a city may hold fairs: large enough, with a working market around it.
     */
    public static boolean holdsFairs(MinecraftServer server, CityWalls.Place place) {
        int[] counts = CityWalls.counts(server, place);
        CityLevel level = CityLevel.of(counts[0], counts[1], RoadSettings.rules());
        if (level.ordinal() < LifeSettings.rules().fairLevel().ordinal()) {
            return false;
        }
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, place.kingdomId(), BuildingRoles.MARKET)) {
            if (near(server, place, effect.building().anchor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(MinecraftServer server, CityWalls.Place place, WorldPos pos) {
        int area = CityWalls.area(server, place);
        return pos.dimension().equals(place.dimension()) && Math.abs(pos.x() - place.x()) <= area && Math.abs(pos.z() - place.z()) <= area;
    }

    /**
     * The city whose fair is today at a place, if any.
     */
    public static Optional<CityWalls.Place> fairAt(MinecraftServer server, @Nullable WorldPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        for (CityWalls.Place place : CityWalls.places(server)) {
            if (life.fairDay(key(place)) == today && near(server, place, pos)) {
                return Optional.of(place);
            }
        }
        return Optional.empty();
    }

    /**
     * Seats more a merchant post has during the fair of its city.
     */
    public static int extraSeats(MinecraftServer server, WorldPos post) {
        return fairAt(server, post).isPresent() ? LifeSettings.rules().fairSeats() : 0;
    }

    /**
     * The price of the market at a place: lower during the fair of the city there.
     */
    public static long price(MinecraftServer server, UUID kingdomId, @Nullable WorldPos at, long price) {
        Optional<CityWalls.Place> fair = fairAt(server, at);
        return fair.isPresent() && fair.get().kingdomId().equals(kingdomId) ? Festivals.fairPrice(price, LifeSettings.rules()) : price;
    }

    /**
     * The name the stall shows for the seller of a lot that merchants of a kingdom brought to a fair.
     */
    public static Optional<String> sellerName(MinecraftServer server, UUID seller) {
        return KingdomWorldApi.kingdom(server, seller).map(Kingdom::name);
    }

    // ---------------------------------------------------------------- the days of fairs

    /**
     * Opens the fairs of today and sends the goods of past fairs home.
     */
    static void tick(MinecraftServer server) {
        if (LifeWork.isReadOnly(server)) {
            return;
        }
        LifeRules rules = LifeSettings.rules();
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        if (rules.fairEveryDays() > 0) {
            for (CityWalls.Place place : CityWalls.places(server)) {
                if (Festivals.fairDay(seed(place), today, rules) && life.fairDay(key(place)) != today && holdsFairs(server, place)) {
                    open(server, place);
                }
            }
        }
        settle(server);
    }

    /**
     * Opens the fair of a city today, whatever the day; for operators and tests too.
     */
    public static void open(MinecraftServer server, CityWalls.Place place) {
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        life.setFairDay(key(place), today);
        life.addEvent(place.kingdomId(), new LifeState.Event("fair", today, place.cityId() == 0 ? "" : place.name()));
        int visitors = bringVisitors(server, place);
        LOGGER.info("A fair opens in {} with merchants of {} other kingdom(s)", place.name(), visitors);
        LifeWork.tellMembers(server, place.kingdomId(), visitors == 0 ? Component.translatable(LifeTexts.KEY + "fair.announce_local", place.name())
                : Component.translatable(LifeTexts.KEY + "fair.announce", place.name(), LifeTexts.kingdoms(visitors)));
    }

    /**
     * Days until the fair of a city, 0 if it is today; -1 if the city holds none.
     */
    public static long daysToFair(MinecraftServer server, CityWalls.Place place) {
        if (LifeWork.state(server).fairDay(key(place)) == LifeWork.today(server)) {
            return 0;
        }
        if (!holdsFairs(server, place)) {
            return -1;
        }
        return Festivals.daysToFair(seed(place), LifeWork.today(server), LifeSettings.rules());
    }

    private static int bringVisitors(MinecraftServer server, CityWalls.Place place) {
        LifeRules rules = LifeSettings.rules();
        if (rules.fairVisitorItems() <= 0) {
            return 0;
        }
        var state = KingdomWorldApi.state(server);
        int count = 0;
        for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
            if (other.id().equals(place.kingdomId()) || !trades(server, other.id(), place.kingdomId()) || !linked(server, other.id(), place)) {
                continue;
            }
            // Only a kingdom that has a market exports; looking does not open one.
            Optional<Market> found = state.trade().existing(other.id());
            if (found.isEmpty()) {
                continue;
            }
            Market market = found.get();
            int left = rules.fairVisitorItems();
            boolean any = false;
            for (Market.ExportRule rule : market.exports()) {
                Item item = GoodsStacks.item(rule.item());
                if (item == null || left <= 0) {
                    continue;
                }
                long spare = MarketStock.free(server, other.id(), item) - rule.above();
                int amount = (int) Math.min(left, Math.min(spare, item.getMaxStackSize() * 4L));
                if (amount <= 0) {
                    continue;
                }
                long unit = Prices.autoSellPrice(MarketStock.worth(server, item).amount(), MarketStock.stock(server, place.kingdomId(), item),
                        TradeSettings.rules());
                long price = Prices.total(Festivals.fairPrice(unit, rules), amount);
                if (price < 1) {
                    continue;
                }
                List<ItemStack> taken = MarketStock.take(server, other.id(), item, amount, null);
                if (taken == null || taken.isEmpty()) {
                    continue;
                }
                List<Goods> goods = MarketStock.goods(taken);
                if (goods.size() != 1 || !list(server, place, other.id(), goods.get(0), price)) {
                    MarketStock.putBack(server, other.id(), taken, null);
                    continue;
                }
                left -= amount;
                any = true;
            }
            if (any) {
                count++;
            }
        }
        return count;
    }

    private static boolean list(MinecraftServer server, CityWalls.Place place, UUID visitor, Goods goods, long price) {
        var trade = KingdomWorldApi.state(server).trade();
        long id = trade.peekNextId();
        if (!trade.addLot(place.kingdomId(), visitor, goods, price, KingdomWorldApi.gameTime(server), TradeSettings.rules()).ok()) {
            return false;
        }
        LifeWork.state(server).addFairLot(new LifeState.FairLot(id, place.kingdomId(), visitor, LifeWork.today(server)));
        return true;
    }

    /**
     * Whether two kingdoms trade: a deal between them, open or done.
     */
    static boolean trades(MinecraftServer server, UUID a, UUID b) {
        for (Deal deal : KingdomWorldApi.state(server).trade().dealsOf(a)) {
            if (deal.involves(b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the way from the capital of a kingdom to a city goes at least partly by road.
     */
    static boolean linked(MinecraftServer server, UUID kingdom, CityWalls.Place place) {
        Optional<Capital> capital = RoadWork.capital(server, kingdom);
        if (capital.isEmpty() || !capital.get().dimension().equals(place.dimension())) {
            return false;
        }
        return RoadCaravans.plan(server, place.dimension(), capital.get().x(), capital.get().z(), place.x(), place.z()).onRoads() > 0;
    }

    /**
     * Pays the treasuries of the visitors for what they sold and sends the goods of past fairs home.
     */
    static void settle(MinecraftServer server) {
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        var state = KingdomWorldApi.state(server);
        var economy = state.economy();
        long day = TreasuryActions.day(server);
        for (LifeState.FairLot lot : life.fairLots()) {
            // What the merchants took lies on their account in the money of the host; it goes to their treasury.
            long earned = economy.accounts().balance(lot.visitor(), lot.host());
            if (earned > 0 && economy.canReleaseToTreasury(lot.visitor(), lot.host(), earned)
                    && economy.holdFromAccount(lot.visitor(), lot.host(), earned).ok()) {
                if (!economy.releaseToTreasury(lot.visitor(), lot.host(), earned, LedgerCategory.LIFE, day).ok()) {
                    economy.releaseToAccount(lot.visitor(), lot.host(), earned);
                }
            }
            Optional<Market.Lot> listed = state.trade().existing(lot.host()).flatMap(market -> market.lot(lot.lotId()));
            if (listed.isEmpty()) {
                if (economy.accounts().balance(lot.visitor(), lot.host()) == 0) {
                    life.removeFairLot(lot.lotId());
                }
                continue;
            }
            if (lot.day() >= today) {
                continue;
            }
            Optional<Market.Lot> taken = state.trade().takeLot(lot.host(), lot.lotId());
            if (taken.isEmpty()) {
                continue;
            }
            List<ItemStack> stacks = MarketStock.stacks(List.of(taken.get().goods()));
            List<ItemStack> rest = KingdomWorldApi.kingdom(server, lot.visitor()).isPresent()
                    ? MarketStock.putBack(server, lot.visitor(), stacks, null) : stacks;
            life.removeFairLot(lot.lotId());
            if (!rest.isEmpty()) {
                // No room at home: the goods wait on the market of the host until the next look.
                List<Goods> goods = MarketStock.goods(rest);
                List<ItemStack> unlisted = new ArrayList<>();
                for (int i = 0; i < goods.size(); i++) {
                    long id = state.trade().peekNextId();
                    if (state.trade().addLot(lot.host(), lot.visitor(), goods.get(i), taken.get().price(), KingdomWorldApi.gameTime(server),
                            TradeSettings.rules()).ok()) {
                        life.addFairLot(new LifeState.FairLot(id, lot.host(), lot.visitor(), lot.day()));
                    } else {
                        unlisted.addAll(MarketStock.stacks(List.of(goods.get(i))));
                    }
                }
                if (!unlisted.isEmpty()) {
                    LOGGER.warn("Goods of a fair could neither go home nor stay on the market and went to the storehouses of the host: {}",
                            unlisted);
                    MarketStock.putBack(server, lot.host(), unlisted, null);
                }
            }
        }
    }

    public static KingdomActions.Feedback forceOpen(MinecraftServer server, UUID kingdomId, long cityId) {
        Optional<CityWalls.Place> place = CityWalls.place(server, kingdomId, cityId);
        if (place.isEmpty()) {
            return KingdomActions.Feedback.fail(LifeTexts.KEY + "fair.no_city");
        }
        open(server, place.get());
        return KingdomActions.Feedback.ok(LifeTexts.KEY + "fair.opened", place.get().name());
    }
}
