package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.production.ProductionSettings;
import io.github.sekerator.kingdomworld.common.production.Recipes;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopMaking;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.commerce.TradeRegistry;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * The workshops of citizens in the world: the recipes a trade makes at the workstations of a place, worked out once for each trade
 * and set of workstations until data packs reload, the fuel the crown can sell, the usual wage of a master, and what the work of a
 * workshop depends on. The work itself is {@link ShopMaking}.
 */
public final class Workshops {
    /** The items a trade makes at a set of workstations, by kind of goods, by trade and workstations. */
    private static final Map<String, Map<GoodsCategory, List<String>>> CANDIDATES = new HashMap<>();
    /** Whether a recipe may be made by a workshop, by id. */
    private static final Map<String, Boolean> USABLE = new HashMap<>();
    private static RecipeBook book;

    private Workshops() {
    }

    /**
     * Forgets what was worked out, after data packs reloaded.
     */
    public static void invalidate() {
        CANDIDATES.clear();
        USABLE.clear();
        book = null;
    }

    private static RecipeBook book(MinecraftServer server) {
        RecipeBook current = Recipes.book(server);
        if (current != book) {
            CANDIDATES.clear();
            USABLE.clear();
            book = current;
        }
        return current;
    }

    public static Trade trade(Business shop) {
        return TradeRegistry.current().trade(shop.trade());
    }

    /**
     * The workstations found at the place of a shop, empty when nobody looked at it yet.
     */
    public static Set<String> placeStations(MinecraftServer server, Business shop) {
        return PlaceSurvey.stations(server, shop.place().key()).orElse(Set.of());
    }

    /**
     * The usual income of the keepers of a trade a day: the usual wage of the profession it is compared with, or what a shopkeeper
     * usually earns.
     */
    public static long usualIncome(Trade trade) {
        if (trade.profession().isEmpty()) {
            return CityEconomySettings.rules().keeperUsualIncome();
        }
        long wage = EconomySettings.rules().defaultWage(trade.profession());
        return wage > 0 ? wage : CityEconomySettings.rules().keeperUsualIncome();
    }

    /**
     * Whether a workshop may make a recipe: no ingredient leaves something behind that would be lost (a bucket of milk).
     */
    static boolean usable(RecipeOption recipe) {
        return USABLE.computeIfAbsent(recipe.id(), id -> {
            for (RecipeOption.Part part : recipe.parts()) {
                for (String choice : part.choices()) {
                    Item item = GoodsStacks.item(choice);
                    if (item == null || new ItemStack(item).hasCraftingRemainingItem()) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    /**
     * The items a trade makes at the workstations of a place, by kind of goods: items of the tags of the kind (narrowed for the
     * trade) that a recipe of the place makes.
     */
    public static Map<GoodsCategory, List<String>> candidates(MinecraftServer server, Trade trade, Set<String> placeStations) {
        RecipeBook recipes = book(server);
        Set<String> stations = trade.stationsAt(placeStations);
        String key = trade.id() + "|" + String.join(",", new TreeSet<>(stations));
        Map<GoodsCategory, List<String>> known = CANDIDATES.get(key);
        if (known != null) {
            return known;
        }
        Map<GoodsCategory, List<String>> byKind = new EnumMap<>(GoodsCategory.class);
        for (GoodsCategory category : trade.goods()) {
            String narrowed = trade.only().get(category);
            TagKey<Item> only = narrowed == null ? null : TagKey.create(Registries.ITEM, new ResourceLocation(narrowed));
            List<String> items = new ArrayList<>();
            for (Item item : Shops.itemsOf(category)) {
                if (only != null && !new ItemStack(item).is(only)) {
                    continue;
                }
                String id = GoodsStacks.itemId(item);
                for (RecipeOption recipe : recipes.recipesFor(id)) {
                    if (stations.contains(recipe.kind().station()) && usable(recipe)) {
                        items.add(id);
                        break;
                    }
                }
            }
            byKind.put(category, List.copyOf(items));
        }
        CANDIDATES.put(key, byKind);
        return byKind;
    }

    /**
     * What the work of a workshop depends on.
     *
     * @param crownMilli what one raw item costs of the crown in thousandths, below 0 when it cannot sell it
     * @param fuel       the fuel the crown sells, {@code null} for none
     */
    public static ShopMaking.Context context(MinecraftServer server, Trade trade, Set<String> placeStations, ToLongFunction<String> crownMilli,
                                             ShopMaking.Fuel fuel) {
        return new ShopMaking.Context(book(server), trade.stationsAt(placeStations), Workshops::usable, crownMilli, fuel, usualIncome(trade),
                ProductionSettings.rules());
    }

    /**
     * The fuel the crown can sell the workshops of a kingdom today: coal or charcoal first, else what burns and the storehouses
     * hold most of, never food or tools.
     *
     * @param free  the free stock of each item
     * @param price what one costs of the crown
     */
    static ShopMaking.Fuel fuel(Map<String, Long> free, ToLongFunction<String> price) {
        String best = null;
        int bestBurn = 0;
        boolean bestCoal = false;
        long bestCount = 0;
        for (Map.Entry<String, Long> entry : free.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            Item item = GoodsStacks.item(entry.getKey());
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isDamageableItem() || stack.isEdible()) {
                continue;
            }
            int burn = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
            if (burn <= 0) {
                continue;
            }
            boolean coal = stack.is(ItemTags.COALS);
            if (best == null || coal && !bestCoal || coal == bestCoal && entry.getValue() > bestCount) {
                best = entry.getKey();
                bestBurn = burn;
                bestCoal = coal;
                bestCount = entry.getValue();
            }
        }
        if (best == null) {
            return null;
        }
        return new ShopMaking.Fuel(best, bestBurn, Math.max(1, price.applyAsLong(best)) * 1_000L);
    }

    // ---------------------------------------------------------------- the hands of the townsfolk

    /**
     * The shops and workshops of a city that may have hands of the townsfolk: those kept by keepers or masters, and any that still
     * has some.
     */
    public static List<Business> handPlaces(MinecraftServer server, UUID kingdom, long city) {
        List<Business> places = new java.util.ArrayList<>();
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            if (shop.cityId() == city && (shop.folkHands() > 0 || !shop.keepers().isEmpty())) {
                places.add(shop);
            }
        }
        return places;
    }

    /**
     * Each shop and workshop takes one more hand of the idle townsfolk or lets one go, once a day: it takes one when it sold out
     * the day before and both a hand and its keepers would earn enough, it lets one go when either does not (see
     * {@link io.github.sekerator.kingdomworld.core.road.FolkJobs#handChange}). A family shop or a trading house takes none.
     *
     * @param idle the idle townsfolk of the city
     * @return how many hands more the city has, fewer when negative
     */
    public static int changeHands(MinecraftServer server, List<Business> shops, int idle,
                                  io.github.sekerator.kingdomworld.core.road.FolkWorkRules rules) {
        long today = io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server);
        int change = 0;
        int left = Math.max(0, idle);
        for (Business shop : shops) {
            int keepers = shop.family() || shop.trader() || !shop.kept() ? 0 : shop.keepers().size();
            boolean soldOut = shop.day(today - 1).map(day -> day.soldCost() > 0 && shop.goodsValue() < day.soldCost()).orElse(false);
            var decision = io.github.sekerator.kingdomworld.core.road.FolkJobs.handChange(soldOut, shop.lastShared(), keepers, shop.folkHands(),
                    usualIncome(trade(shop)), Business.KEEPER_SHARE, rules);
            if (decision == io.github.sekerator.kingdomworld.core.road.FolkJobs.HandChange.HIRE && left > 0) {
                shop.setFolkHands(shop.folkHands() + 1);
                left--;
                change++;
            } else if (decision == io.github.sekerator.kingdomworld.core.road.FolkJobs.HandChange.RELEASE) {
                shop.setFolkHands(shop.folkHands() - 1);
                change--;
            }
        }
        if (!shops.isEmpty()) {
            CommerceSavedData.get(server).setDirty();
        }
        return change;
    }

    /**
     * The pace of the masters of a shop together, 1 a master at the usual mood.
     */
    static double pace(MinecraftServer server, Business shop) {
        // Each hand of the townsfolk adds a part of the work of a master.
        double pace = shop.folkHands() * io.github.sekerator.kingdomworld.common.citizen.FolkSettings.work().handPercent() / 100.0;
        for (UUID keeper : shop.keepers()) {
            pace += io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.state(server).citizens().get(keeper)
                    .map(citizen -> EconomySettings.workSpeed(citizen.mood())).orElse(1.0);
        }
        return pace;
    }
}
