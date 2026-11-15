package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.CrownPurchase;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.commerce.ShopPricing;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The shops of citizens in the kingdoms of the world: in the houses of merchants and craftsmen of their capitals and towns, kept
 * by the citizens without work the army of the ruler left, else by the family of a house of a merchant in the evening. They buy of
 * the storehouses of the ruler and sell to players and to the ruler; the townspeople of the world buy next to nothing, the tax of
 * the ruler takes their purses. A city of the world with places always keeps one shop open, whatever it earns; another opens when
 * those open ran short of goods three of the last seven days. No trading house: the merchants of the ruler export as before.
 *
 * <p>The wages of the peaceful citizens of the world lie in the purses of their cities for the time of the payday: the share of
 * the houses goes home from there, the tax of the ruler comes out of them, and the rest leaves the world as it always did, so that
 * the ruler gets exactly what it got before. The treasuries of the cities of the world pass all they take on to the ruler.
 */
public final class WorldShops {
    /** The citizens without work the army of each ruler of the world still wants, by its last turn. */
    private static final Map<UUID, Integer> ARMY_NEED = new HashMap<>();

    private WorldShops() {
    }

    public static void reset() {
        ARMY_NEED.clear();
    }

    /**
     * Whether a kingdom of the world keeps shops of citizens: the money of the cities and the key are on.
     */
    public static boolean active(MinecraftServer server, UUID kingdom) {
        return CityEconomySettings.enabled() && FolkSettings.work().worldShops() && KingdomWorldApi.kingdom(server, kingdom).isPresent()
                && io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdom);
    }

    /**
     * Whether a kingdom keeps shops of citizens at all: a kingdom of players whose money stays in its cities, or a kingdom of the
     * world with shops.
     */
    public static boolean shops(MinecraftServer server, UUID kingdom) {
        return CityOf.active(server, kingdom) || active(server, kingdom);
    }

    // ---------------------------------------------------------------- keepers after the army

    /**
     * What the army of a ruler still wants of its citizens without work, after its turn: the shops take only the rest.
     */
    public static void armyNeed(UUID kingdom, int need) {
        ARMY_NEED.put(kingdom, Math.max(0, need));
    }

    /**
     * How many more citizens without work of a kingdom of the world may keep shops: those the army does not want, less those who
     * keep one. Before the first turn of its ruler the army is thought to want them all.
     */
    static int spareKeepers(MinecraftServer server, UUID kingdom) {
        Integer need = ARMY_NEED.get(kingdom);
        if (need == null) {
            return io.github.sekerator.kingdomworld.core.commerce.WorldShopRules.spareKeepers(0, -1);
        }
        // The army takes of all the citizens without work; the keepers already among them keep their shops while it leaves them.
        int free = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom)) {
            if (Professions.UNEMPLOYED.id().equals(citizen.professionId()) && citizen.isAlive() && citizen.business() == 0) {
                free++;
            }
        }
        return io.github.sekerator.kingdomworld.core.commerce.WorldShopRules.spareKeepers(free, need);
    }

    /**
     * The shops of a kingdom that trade, for its ruler.
     */
    public static int trading(MinecraftServer server, UUID kingdom) {
        int count = 0;
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            if (shop.status() == Business.Status.TRADING || shop.status() == Business.Status.FAMILY) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- the payday

    /**
     * The tax of the ruler on the wages of its peaceful citizens, out of the purses of their cities where the wages lie, the same
     * sum as before; the rest of the purses leaves the world as the wages did before.
     *
     * @param tax    the tax, worked out as before
     * @param byCity what the peaceful citizens of each city were paid
     */
    public static void citizenTax(MinecraftServer server, UUID kingdom, long tax, Map<Long, Long> byCity, int percent, long day) {
        Economy economy = KingdomWorldApi.state(server).economy();
        List<Long> cities = new ArrayList<>(new TreeMap<>(byCity).keySet());
        long[] paid = new long[cities.size()];
        long[] purses = new long[cities.size()];
        for (int i = 0; i < cities.size(); i++) {
            paid[i] = byCity.get(cities.get(i));
            purses[i] = economy.purse(CityPurses.PurseKey.cityPurse(kingdom, cities.get(i)));
        }
        long[] taken = io.github.sekerator.kingdomworld.core.commerce.WorldShopRules.taxFromPurses(tax, paid, purses, percent);
        for (int i = 0; i < cities.size(); i++) {
            if (taken[i] > 0) {
                economy.purseToTreasury(CityPurses.PurseKey.cityPurse(kingdom, cities.get(i)), taken[i], LedgerCategory.TAXES, CityLedger.OTHER, day);
            }
        }
        sweep(server, kingdom, day);
    }

    /**
     * The purses of the cities of a kingdom of the world empty out of the world, as its wages did before.
     */
    static void sweep(MinecraftServer server, UUID kingdom, long day) {
        Economy economy = KingdomWorldApi.state(server).economy();
        for (CityPurses.PurseKey key : economy.purses().keysOf(kingdom)) {
            if (key.kind() == CityPurses.PurseKind.CITY_PURSE) {
                long amount = economy.purse(key);
                if (amount > 0) {
                    economy.purseLeaves(key, key.id(), amount, CityPurses.Outflow.PURSE_FULL, day);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the ruler buys

    /**
     * The ruler buys of the shops of its citizens what they made of an item, the cheapest first, out of its treasury and without
     * the tax, the items going into the storehouse nearest the shop. It never buys back what the shops bought of it.
     *
     * @return how many it bought
     */
    public static long rulerBuys(MinecraftServer server, UUID kingdom, String id, long count, long budget, long day) {
        Item item = GoodsStacks.item(id);
        if (item == null || count <= 0 || budget <= 0 || !active(server, kingdom)) {
            return 0;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        String key = Business.key(id, true);
        List<CrownPurchase.Offer> offers = new ArrayList<>();
        for (Business shop : CommerceSavedData.get(server).shopsOf(kingdom)) {
            Business.Lot lot = shop.goods().get(key);
            if (lot != null && lot.count() > 0 && shop.status() != Business.Status.CLOSING) {
                offers.add(new CrownPurchase.Offer(shop.id(), key, lot.count(), Shops.askedMilli(server, shop, key, lot), Shops.worth(server, item)));
            }
        }
        long money = Math.min(budget, economy.treasury(kingdom).map(io.github.sekerator.kingdomworld.core.economy.Treasury::funds).orElse(0L));
        long bought = 0;
        for (CrownPurchase.Take take : CrownPurchase.choose(offers, count, Long.MAX_VALUE, money)) {
            Optional<Business> found = CommerceSavedData.get(server).shop(take.shop());
            if (found.isEmpty()) {
                continue;
            }
            Business shop = found.get();
            Business.Lot lot = shop.goods().get(take.key());
            WorldPos near = new WorldPos(shop.place().dimension(), shop.place().x(), shop.place().y(), shop.place().z());
            List<ItemStack> stacks = new ArrayList<>();
            long rest = take.count();
            while (rest > 0) {
                int part = (int) Math.min(rest, item.getMaxStackSize());
                stacks.add(new ItemStack(item, part));
                rest -= part;
            }
            if (lot == null || !KingdomStorage.fits(server, kingdom, stacks, near) || !economy.treasuryToPurse(
                    CityPurses.PurseKey.till(kingdom, shop.id()), take.total(), LedgerCategory.COMMERCE, CityLedger.PURCHASES, day).ok()) {
                continue;
            }
            shop.removeGoods(take.key(), take.count());
            for (ItemStack stack : stacks) {
                KingdomStorage.insert(server, kingdom, stack, near);
            }
            long[] sold = Shops.sold(shop.id());
            sold[0] += take.total();
            sold[1] += ShopPricing.total(lot.costMilli(), take.count());
            sold[2] += ShopPricing.total(lot.laborMilli(), take.count());
            if (lot.category() >= 0 && lot.category() < GoodsCategory.values().length) {
                sold[3 + lot.category()] += (long) Math.floor(Shops.worth(server, item) * take.count());
            }
            bought += take.count();
        }
        if (bought > 0) {
            CommerceSavedData.get(server).setDirty();
        }
        return bought;
    }

    // ---------------------------------------------------------------- settlements

    /**
     * A settlement of the world whose city keeps a shop that trades grows a little more prosperous each day; a number only, no
     * money and no goods.
     */
    public static void prosper(MinecraftServer server, Settlement settlement) {
        if (settlement.kingdomId() == null || settlement.cityId() < 0 || !active(server, settlement.kingdomId())) {
            return;
        }
        for (Business shop : CommerceSavedData.get(server).shopsOf(settlement.kingdomId(), settlement.cityId())) {
            if (shop.status() == Business.Status.TRADING || shop.status() == Business.Status.FAMILY) {
                settlement.live(new SettlementLife.Day(settlement.population(), settlement.food(), settlement.guards(), settlement.militia(),
                        Math.min(SettlementLife.MAX_PROSPERITY, settlement.prosperity() + 1)), settlement.lastDay());
                return;
            }
        }
    }
}
