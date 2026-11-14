package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the goods of a city are worth to the crown: what the posts of the crown in it make, less the raw materials of its
 * workshops, what its people eat and what leaves the storehouses for its shops and the merchants, each at the price of export:
 * the lower of the share of its worth merchants get abroad and what the crown would pay for it at its stock now, so that a
 * surplus beyond the target of the storehouses counts for less. Written with the city of each post beside the ledger of
 * production.
 *
 * <p>The stock of a kingdom is counted once a day, the city of a post once a day too.
 */
public final class CityGoods {
    private static final Map<UUID, Cached> STOCK = new HashMap<>();
    private static final Map<String, Long> POSTS = new HashMap<>();
    private static long postsDay = Long.MIN_VALUE;

    private record Cached(long day, Map<String, Long> counts) {
    }

    private CityGoods() {
    }

    public static void reset() {
        STOCK.clear();
        POSTS.clear();
        postsDay = Long.MIN_VALUE;
    }

    private static Map<String, Long> counts(MinecraftServer server, UUID kingdom) {
        long day = TreasuryActions.day(server);
        Cached cached = STOCK.get(kingdom);
        if (cached == null || cached.day() != day) {
            cached = new Cached(day, KingdomStorage.countsById(server, kingdom));
            STOCK.put(kingdom, cached);
        }
        return cached.counts();
    }

    /**
     * What a number of an item is worth at the price of export.
     */
    public static long exportValue(MinecraftServer server, UUID kingdom, Item item, long count) {
        if (count <= 0) {
            return 0;
        }
        TradeRules rules = TradeSettings.rules();
        double worth = ItemPrices.value(server, new ItemStack(item)).amount();
        long stock = counts(server, kingdom).getOrDefault(KingdomStorage.itemId(new ItemStack(item)), 0L);
        double abroad = worth * rules.exportFactor();
        double home = Prices.autoBuyPrice(worth, stock, rules);
        return (long) Math.min(Money.MAX, Math.floor(Math.min(abroad, home) * count));
    }

    /**
     * The city a place of a kingdom belongs to, by the rule of the beds and homes, worked out once a day.
     */
    public static long cityOf(MinecraftServer server, UUID kingdom, WorldPos pos) {
        long day = TreasuryActions.day(server);
        if (day != postsDay) {
            POSTS.clear();
            postsDay = day;
        }
        String key = kingdom + "/" + pos.dimension() + "/" + pos.x() + "/" + pos.y() + "/" + pos.z();
        Long known = POSTS.get(key);
        if (known != null) {
            return known;
        }
        List<CityWalls.Place> places = CityPeople.places(server, kingdom);
        long city = Math.max(0, CityPeople.cityIdOf(server, kingdom, places, CityPeople.index(places), pos));
        POSTS.put(key, city);
        return city;
    }

    private static boolean active(MinecraftServer server, UUID kingdom) {
        return CityOf.active(server, kingdom) && !KingdomWorldApi.isReadOnly(server);
    }

    /**
     * A post of the crown made goods.
     */
    public static void made(MinecraftServer server, UUID kingdom, WorldPos post, String itemId, long count) {
        if (count <= 0 || !active(server, kingdom)) {
            return;
        }
        Item item = GoodsStacks.item(itemId);
        if (item == null || item instanceof io.github.sekerator.kingdomworld.common.economy.CoinItem) {
            // Coins of a mint are money, counted as such.
            return;
        }
        KingdomWorldApi.state(server).economy().cityTally(kingdom, cityOf(server, kingdom, post))
                .add(CityStatement.Line.GOODS_MADE, exportValue(server, kingdom, item, count));
    }

    /**
     * A mint of the crown struck coins worth an amount.
     */
    public static void minted(MinecraftServer server, UUID kingdom, WorldPos post, long worth) {
        if (worth <= 0 || !active(server, kingdom)) {
            return;
        }
        KingdomWorldApi.state(server).economy().cityTally(kingdom, cityOf(server, kingdom, post)).add(CityStatement.Line.CROWN_MINTING, worth);
    }

    /**
     * The merchants of the crown sold goods abroad: the money and the goods are shared by the cities of their posts.
     */
    public static void exported(MinecraftServer server, UUID kingdom, List<io.github.sekerator.kingdomworld.core.citizen.Citizen> merchants,
                                Item item, long count, long money) {
        if (merchants.isEmpty() || !active(server, kingdom)) {
            return;
        }
        long goods = exportValue(server, kingdom, item, count);
        int n = merchants.size();
        for (int i = 0; i < n; i++) {
            var merchant = merchants.get(i);
            WorldPos at = merchant.workplace() != null ? merchant.workplace() : merchant.home();
            long city = at == null ? 0 : cityOf(server, kingdom, at);
            CityStatement.Tally tally = KingdomWorldApi.state(server).economy().cityTally(kingdom, city);
            tally.add(CityStatement.Line.CROWN_EXPORTS, money / n + (i == 0 ? money % n : 0));
            tally.add(CityStatement.Line.GOODS_OUT, goods / n + (i == 0 ? goods % n : 0));
        }
    }

    /**
     * A workshop of the crown used up raw materials for what it made: what the result is worth without the work put into it.
     */
    public static void raw(MinecraftServer server, UUID kingdom, WorldPos post, ItemStack result) {
        if (result.isEmpty() || !active(server, kingdom)) {
            return;
        }
        double markup;
        try {
            markup = 1.0 + Math.max(0.0, io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.laborMarkup.get());
        } catch (RuntimeException e) {
            markup = 1.1;
        }
        long value = (long) Math.floor(exportValue(server, kingdom, result.getItem(), result.getCount()) / markup);
        KingdomWorldApi.state(server).economy().cityTally(kingdom, cityOf(server, kingdom, post)).add(CityStatement.Line.GOODS_RAW, value);
    }

    /**
     * Goods left the storehouses for the shops of a city or for merchants abroad.
     */
    public static void out(MinecraftServer server, UUID kingdom, long city, Item item, long count) {
        if (count <= 0 || !active(server, kingdom)) {
            return;
        }
        KingdomWorldApi.state(server).economy().cityTally(kingdom, city).add(CityStatement.Line.GOODS_OUT, exportValue(server, kingdom, item, count));
    }

    /**
     * What the people of a city ate today, at the price of export of bread for its nutrition.
     */
    public static long eaten(MinecraftServer server, UUID kingdom, long people) {
        int nutrition = Math.max(1, KingdomStorage.nutrition(new ItemStack(Items.BREAD)));
        long rations = Money.multiplyCapped(Math.max(0, people),
                io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules().dailyNutrition());
        return exportValue(server, kingdom, Items.BREAD, Math.max(0, rations / nutrition));
    }
}
