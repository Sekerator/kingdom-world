package io.github.sekerator.kingdomworld.common.economy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.HouseholdRules;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What citizens keep at home: a share of every wage paid is set aside for the house the citizen lives in, and when the
 * house is loaded the money buys a little food from the storehouses of the kingdom and the rest is laid into the container
 * of the house as coins of the kingdom (see {@link HouseholdRules}). The goods of a house are the citizen's: taking them is
 * a theft from that citizen, and they never count as the stock of the kingdom, which is what its storehouses hold.
 *
 * <p>Nothing is made out of nothing. The coins were a wage the treasury paid; the food was in a storehouse and its price
 * went back to the treasury. What a house cannot take leaves the world, as the whole wage did before.
 */
public final class Households {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks between two looks at the houses with money set aside. */
    private static final int LOOK_TICKS = 100;
    /** Houses settled in one look. */
    private static final int PER_LOOK = 16;
    /** Items of food a storehouse keeps for each citizen before houses may buy from it. */
    private static final int RESERVE_PER_CITIZEN = 4;

    private static HouseholdRules override;
    /** Where the container of each house was last found. */
    private static final Map<Long, BlockPos> CONTAINERS = new HashMap<>();
    private static int cursor;

    private Households() {
    }

    public static void reset() {
        override = null;
        CONTAINERS.clear();
        cursor = 0;
    }

    public static HouseholdRules rules() {
        if (override != null) {
            return override;
        }
        try {
            KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
            return new HouseholdRules(config.householdSharePercent.get(), config.householdCoinsMax.get(), config.householdFood.get(),
                    HouseholdRules.DEFAULT.foodPortion());
        } catch (IllegalStateException e) {
            return HouseholdRules.DEFAULT;
        }
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(HouseholdRules rules) {
        override = rules;
    }

    // ---------------------------------------------------------------- houses

    /**
     * The house of a kingdom a place lies in: a registered building with the role of a house.
     */
    public static Optional<Building> houseAt(MinecraftServer server, UUID kingdomId, String dimension, int x, int y, int z) {
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdomId)) {
            if (BuildingRoles.HOUSE.equals(building.role()) && building.dimension().equals(dimension) && building.box().contains(x, y, z)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /**
     * The house a citizen lives in, if its home is in one.
     */
    public static Optional<Building> houseOf(MinecraftServer server, Citizen citizen) {
        WorldPos home = citizen.home();
        return home == null ? Optional.empty() : houseAt(server, citizen.kingdomId(), home.dimension(), home.x(), home.y(), home.z());
    }

    /**
     * A citizen who lives in a house: the first of them, so that the same name stands for the house every time.
     */
    public static Optional<Citizen> residentOf(MinecraftServer server, Building house) {
        Citizen first = null;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(house.kingdomId())) {
            WorldPos home = citizen.home();
            if (home != null && home.dimension().equals(house.dimension()) && house.box().contains(home.x(), home.y(), home.z())
                    && (first == null || citizen.id().compareTo(first.id()) < 0)) {
                first = citizen;
            }
        }
        return Optional.ofNullable(first);
    }

    /**
     * The container of a house: the first chest or barrel in it that no player placed. {@code null} while the house is not
     * loaded or has none.
     */
    @Nullable
    static BlockPos containerOf(ServerLevel level, Building house) {
        BlockPos known = CONTAINERS.get(house.id());
        if (known != null && level.isLoaded(known) && usable(level, known)) {
            return known;
        }
        CONTAINERS.remove(house.id());
        if (!ConstructionWork.loaded(level, house.box())) {
            return null;
        }
        var box = house.box();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).hasBlockEntity() && usable(level, pos)) {
                        CONTAINERS.put(house.id(), pos);
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean usable(ServerLevel level, BlockPos pos) {
        return BuildingEffects.isContainer(level, pos, level.getBlockState(pos))
                && !io.github.sekerator.kingdomworld.common.law.LawEvents.placedByPlayer(level, pos);
    }

    @Nullable
    private static IItemHandler handler(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    // ---------------------------------------------------------------- wages

    /**
     * A payday was held: of every wage paid to a citizen who lives in a house, the share of the house is set aside.
     */
    public static void onPayday(MinecraftServer server, UUID kingdomId, Economy.Payday payday) {
        HouseholdRules rules = rules();
        if (rules.sharePercent() <= 0 || payday.pay().isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        HouseholdSavedData data = HouseholdSavedData.get(server);
        // Many citizens share few houses: each home is looked up once.
        Map<WorldPos, Optional<Building>> houses = new HashMap<>();
        long aside = 0;
        for (Map.Entry<UUID, Long> pay : payday.pay().entrySet()) {
            long share = rules.share(pay.getValue());
            Optional<Citizen> citizen = share <= 0 ? Optional.empty() : state.citizens().get(pay.getKey());
            if (citizen.isEmpty() || citizen.get().home() == null) {
                continue;
            }
            Optional<Building> house = houses.computeIfAbsent(citizen.get().home(), home -> houseOf(server, citizen.get()));
            if (house.isEmpty()) {
                continue;
            }
            long before = data.amount(house.get().id());
            long after = Math.min(rules.purseMax(), Money.addCapped(before, share));
            data.set(house.get().id(), kingdomId, after);
            aside += after - before;
        }
        if (aside > 0) {
            LOGGER.debug("Payday of {}: {} set aside for the houses of its citizens", kingdomId, aside);
        }
    }

    /**
     * What is set aside for a house and not laid in yet, for checks.
     */
    public static long purse(MinecraftServer server, long building) {
        return HouseholdSavedData.get(server).amount(building);
    }

    // ---------------------------------------------------------------- laying in

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % LOOK_TICKS != 0 || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        HouseholdSavedData data = HouseholdSavedData.get(server);
        if (data.purses().isEmpty()) {
            return;
        }
        List<Long> houses = new ArrayList<>(data.purses().keySet());
        for (int i = 0; i < Math.min(PER_LOOK, houses.size()); i++) {
            settle(server, houses.get(Math.floorMod(cursor++, houses.size())));
        }
    }

    /**
     * Lays in what is set aside for a house, if the house is loaded: food bought from the storehouses, then coins.
     *
     * @return whether the purse was settled (not if the house is not loaded)
     */
    public static boolean settle(MinecraftServer server, long buildingId) {
        HouseholdSavedData data = HouseholdSavedData.get(server);
        HouseholdSavedData.Purse purse = data.purses().get(buildingId);
        if (purse == null || KingdomWorldApi.isReadOnly(server)) {
            return false;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Building> found = state.construction().building(buildingId);
        UUID kingdom = purse.kingdomId();
        if (found.isEmpty() || !BuildingRoles.HOUSE.equals(found.get().role()) || !found.get().kingdomId().equals(kingdom)
                || state.economy().treasury(kingdom).isEmpty()) {
            // The house is gone or changed hands: its purse goes with it.
            data.set(buildingId, kingdom, 0);
            CONTAINERS.remove(buildingId);
            return true;
        }
        Building house = found.get();
        ServerLevel level = ConstructionWork.level(server, house.dimension());
        if (level == null || !ConstructionWork.loaded(level, house.box())) {
            return false;
        }
        BlockPos at = containerOf(level, house);
        IItemHandler container = at == null ? null : handler(level, at);
        if (container == null) {
            // A house without a chest keeps nothing: the money leaves the world.
            data.set(buildingId, kingdom, 0);
            return true;
        }
        HouseholdRules rules = rules();
        CurrencyUnits units = Purses.units(server, kingdom);
        Coins coinsHere = Coins.NONE;
        int foodHere = 0;
        for (int slot = 0; slot < container.getSlots(); slot++) {
            ItemStack stack = container.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (KingdomStorage.isFood(stack)) {
                foodHere += stack.getCount();
            } else if (CoinItem.currency(stack).filter(kingdom::equals).isPresent()) {
                Denomination denomination = CoinItem.denomination(stack).orElse(null);
                if (denomination != null) {
                    coinsHere = coinsHere.plus(denomination, stack.getCount());
                }
            }
        }
        WorldPos near = new WorldPos(Dimensions.id(level), at.getX(), at.getY(), at.getZ());
        ItemStack sample = ItemStack.EMPTY;
        int onOffer = 0;
        if (foodHere < rules.foodItems() && rules.foodPortion() > 0) {
            // Food a workshop set aside for cooking is not for sale.
            Map<String, Integer> reserved = io.github.sekerator.kingdomworld.common.production.Production.reservations().reserved(kingdom);
            for (KingdomStorage.Found offer : KingdomStorage.find(server, kingdom, KingdomStorage::isFood, near)) {
                if (!reserved.containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.stack().getItem()).toString())) {
                    sample = offer.stack().copyWithCount(1);
                    break;
                }
            }
            if (!sample.isEmpty()) {
                long stock = KingdomStorage.count(server, kingdom, KingdomStorage::isFood);
                long reserve = (long) RESERVE_PER_CITIZEN * state.citizens().registry().citizensOf(kingdom).size();
                onOffer = (int) Math.max(0, Math.min(Integer.MAX_VALUE, stock - reserve));
            }
        }
        long price = sample.isEmpty() ? 0 : Math.max(1, io.github.sekerator.kingdomworld.common.law.LawPunish.worth(server, kingdom, sample));
        HouseholdRules.Settlement plan = rules.settle(purse.amount(), coinsHere.value(units), foodHere, price, onOffer);
        long left = purse.amount();
        if (plan.food() > 0) {
            Item item = sample.getItem();
            int bought = 0;
            for (ItemStack taken : KingdomStorage.take(server, kingdom, stack -> stack.is(item), plan.food(), near)) {
                ItemStack rest = ItemHandlerHelper.insertItemStacked(container, taken.copy(), false);
                bought += taken.getCount() - rest.getCount();
                if (!rest.isEmpty()) {
                    // No room in the house after all: back where it came from.
                    KingdomStorage.insert(server, kingdom, rest, near);
                }
            }
            long paid = Math.min(left, bought * price);
            if (paid > 0 && state.economy().payKingdom(kingdom, kingdom, paid, null, LedgerCategory.TRADE, TreasuryActions.day(server)).ok()) {
                left -= paid;
            }
        }
        long wanted = Math.max(0, Math.min(left, rules.coinsMax() - coinsHere.value(units)));
        long given = 0;
        if (wanted > 0) {
            Coins coins = Coins.countOut(wanted, units, Denomination.GOLD);
            int stackSize = CoinItem.stack(kingdom, Denomination.COPPER, 1).getMaxStackSize();
            for (Denomination denomination : Denomination.largestFirst()) {
                long count = coins.count(denomination);
                long each = Coins.NONE.plus(denomination, 1).value(units);
                while (count > 0) {
                    int part = (int) Math.min(count, stackSize);
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(container, CoinItem.stack(kingdom, denomination, part), false);
                    given += (part - rest.getCount()) * each;
                    count -= part;
                    if (!rest.isEmpty()) {
                        count = 0;
                    }
                }
            }
            if (given > 0) {
                // The coins lie in the world now: they count as coins in hands, as coins a bank pays out do.
                state.economy().coinsIssued(kingdom, given);
            }
        }
        // What the house could not take leaves the world, as the whole wage did before houses kept anything.
        data.set(buildingId, kingdom, 0);
        return true;
    }
}
