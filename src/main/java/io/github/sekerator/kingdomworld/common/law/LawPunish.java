package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.FinePlan;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * What the law takes: fines (coins first, then the account, the rest a debt to the treasury), the goods of an executed
 * murderer (into the record of the kingdom, coins into its treasury), the ransom that gives them back, and debts paid back
 * by hand. Money is never made: whatever leaves a player arrives in a treasury in the same moment.
 */
public final class LawPunish {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = LawWork.KEY;
    /** How near the throne or a barracks block a player buys goods back, in blocks. */
    private static final double PLACE_REACH = 8.0;
    /** How far around a working tower or barracks building, in blocks. */
    private static final int BUILDING_MARGIN = 4;

    private LawPunish() {
    }

    // ---------------------------------------------------------------- worth

    /**
     * What an item is worth to the law of a kingdom: the price its market asks for it, or the worth of the item.
     */
    public static long worth(MinecraftServer server, UUID kingdomId, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        OptionalLong market = StallActions.marketPrice(server, kingdomId, stack.getItem());
        long unit = market.isPresent() ? market.getAsLong() : ItemPrices.value(server, stack).units();
        return Money.multiplyCapped(Math.max(0, unit), stack.getCount());
    }

    public static long worth(MinecraftServer server, UUID kingdomId, List<ItemStack> stacks) {
        long sum = 0;
        for (ItemStack stack : stacks) {
            sum = Money.addCapped(sum, worth(server, kingdomId, stack));
        }
        return sum;
    }

    // ---------------------------------------------------------------- fines

    /**
     * How a fine was paid.
     */
    public record Paid(long fromCoins, long fromAccount, long toDebt) {
        public long total() {
            return fromCoins + fromAccount + toDebt;
        }
    }

    /**
     * Takes a fine: coins of the kingdom the player carries (with change), then the account, and what is still missing
     * becomes a debt to the treasury.
     */
    public static Paid collectFine(ServerPlayer player, UUID kingdomId, long fine) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        CurrencyUnits units = Purses.units(server, kingdomId);
        Coins carried = CoinStacks.count(player.getInventory(), kingdomId);
        long coinsWorth = carried.value(units);
        long balance = economy.accounts().balance(player.getUUID(), kingdomId);
        FinePlan plan = FinePlan.of(fine, coinsWorth, balance);
        long fromCoins = 0;
        if (plan.fromCoins() > 0) {
            Optional<CoinPayment.Plan> coins = plan.fromCoins() >= coinsWorth ? Optional.of(new CoinPayment.Plan(carried, 0))
                    : CoinPayment.plan(carried, plan.fromCoins(), units);
            if (coins.isPresent() && economy.checkPayKingdom(kingdomId, kingdomId, plan.fromCoins(), null).ok()
                    && Purses.take(player, kingdomId, coins.get().coins())) {
                Outcome paid = economy.payKingdom(kingdomId, kingdomId, plan.fromCoins(), null, LedgerCategory.LAW, day);
                if (paid.ok()) {
                    fromCoins = plan.fromCoins();
                    if (coins.get().change() > 0) {
                        Purses.payOut(player, kingdomId, coins.get().change());
                    }
                } else {
                    Purses.giveBack(player, kingdomId, coins.get().coins());
                }
            }
        }
        long left = fine - fromCoins;
        long fromAccount = Math.min(left, economy.accounts().balance(player.getUUID(), kingdomId));
        if (fromAccount > 0 && !economy.payTreasury(player.getUUID(), kingdomId, fromAccount, LedgerCategory.LAW, day).ok()) {
            fromAccount = 0;
        }
        left -= fromAccount;
        if (left > 0) {
            economy.addDebt(kingdomId, player.getUUID(), left);
        }
        LOGGER.info("{} paid a fine of {} to {}: {} in coins, {} from the account, {} as a debt", player.getGameProfile().getName(), fine,
                kingdomId, fromCoins, fromAccount, left);
        return new Paid(fromCoins, fromAccount, left);
    }

    /**
     * The player pays for petty crimes: the fine is taken and the record of those crimes is gone.
     *
     * @return how the fine was paid, or empty if there was nothing to pay
     */
    public static Optional<Paid> settle(ServerPlayer player, UUID kingdomId) {
        MinecraftServer server = player.server;
        LawState state = LawWork.state(server);
        Optional<Wanted> wanted = state.wanted(kingdomId, player.getUUID());
        if (wanted.isEmpty() || wanted.get().executable() || LawWork.isReadOnly(server)) {
            return Optional.empty();
        }
        long fine = wanted.get().fine(LawSettings.rules(), state.law(kingdomId).fineMultiplierPermille());
        Paid paid = fine > 0 ? collectFine(player, kingdomId, fine) : new Paid(0, 0, 0);
        state.settle(kingdomId, player.getUUID());
        Optional<Wanted> after = state.wanted(kingdomId, player.getUUID());
        CurrencyUnits units = Purses.units(server, kingdomId);
        net.minecraft.network.chat.MutableComponent message = paid.toDebt() > 0
                ? Component.translatable(KEY + "fine_paid_debt", MoneyTexts.amount(fine, units), MoneyTexts.amount(paid.toDebt(), units))
                : Component.translatable(KEY + "fine_paid", MoneyTexts.amount(fine, units));
        player.sendSystemMessage(message.withStyle(ChatFormatting.GOLD));
        if (after.isEmpty()) {
            LawWork.cleared(server, wanted.get(), true, "wanted_cleared");
        } else {
            LawSync.dirty(player.getUUID());
        }
        return Optional.of(paid);
    }

    /**
     * Guards beat a player wanted for petty crimes until the player gives in: the player lives on with little health and a
     * short weakness, and pays.
     */
    static void beaten(ServerPlayer player, UUID kingdomId) {
        LawRules rules = LawSettings.rules();
        float floor = Math.min(player.getMaxHealth(), Math.max(1.0f, rules.beatenHealth));
        // The beating never heals: a player already weaker keeps what is left (a cancelled death keeps the floor).
        player.setHealth(player.getHealth() <= 0.0f ? floor : Math.min(player.getHealth(), floor));
        if (rules.beatenWeaknessTicks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, rules.beatenWeaknessTicks, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.min(rules.beatenWeaknessTicks, 100), 1));
        }
        settle(player, kingdomId);
        LawGuards.forget(player.getUUID());
    }

    // ---------------------------------------------------------------- execution

    /** Ticks a sentence waits for the death it orders; a later death is no execution. */
    private static final long SENTENCE_TICKS = 40;

    private record Sentence(UUID kingdom, long at) {
    }

    /** Players who accepted the death a murder costs, by the kingdom whose law kills them at their next death. */
    private static final java.util.Map<UUID, Sentence> SENTENCED = new java.util.HashMap<>();

    /**
     * The next death of the player is an execution by the law of the kingdom (the player gave up, or an operator ordered
     * it); the goods go into the record before anything falls.
     */
    public static void sentence(ServerPlayer player, UUID kingdomId) {
        SENTENCED.put(player.getUUID(), new Sentence(kingdomId, LawWork.now(player.server)));
    }

    /**
     * The sentence is over, whether the death came or not.
     */
    public static void forgetSentence(UUID player) {
        SENTENCED.remove(player);
    }

    /**
     * The kingdom that executes the player now, if the player was sentenced.
     */
    static Optional<UUID> takeSentence(ServerPlayer player) {
        Sentence sentence = SENTENCED.remove(player.getUUID());
        if (sentence == null || LawWork.now(player.server) - sentence.at() > SENTENCE_TICKS) {
            return Optional.empty();
        }
        return Optional.of(sentence.kingdom());
    }

    static void resetSentences() {
        SENTENCED.clear();
    }

    /**
     * A murderer dies by the law of a kingdom: everything in the inventory and the armor goes into the record of confiscated
     * goods of the kingdom (coins of its currency into its treasury), and the record of the murderer is gone. Called before
     * the death drops anything, so neither the ground nor a gravestone gets a copy.
     *
     * @return the record of the goods, if there were any
     */
    public static Optional<Confiscation> execute(ServerPlayer player, UUID kingdomId, String reasonKey) {
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return Optional.empty();
        }
        LawState state = LawWork.state(server);
        Optional<Wanted> wanted = state.wanted(kingdomId, player.getUUID());
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        CurrencyUnits units = Purses.units(server, kingdomId);
        // Coins of the kingdom go to its treasury; if the treasury could not take them, they stay with the goods.
        Coins coins = CoinStacks.count(player.getInventory(), kingdomId);
        long coinWorth = coins.value(units);
        long toTreasury = 0;
        if (coinWorth > 0 && economy.checkPayKingdom(kingdomId, kingdomId, coinWorth, null).ok() && Purses.take(player, kingdomId, coins)) {
            if (economy.payKingdom(kingdomId, kingdomId, coinWorth, null, LedgerCategory.LAW, day).ok()) {
                toTreasury = coinWorth;
            } else {
                Purses.giveBack(player, kingdomId, coins);
            }
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> goods = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                goods.add(stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
        Optional<Confiscation> made = Optional.empty();
        int count = goods.stream().mapToInt(ItemStack::getCount).sum();
        if (!goods.isEmpty()) {
            long worth = worth(server, kingdomId, goods);
            Confiscation confiscation = state.confiscate(kingdomId, kingdom.map(Kingdom::name).orElse(""), player.getUUID(),
                    player.getGameProfile().getName(), worth, count, LawWork.now(server), day);
            LawSavedData.get(server).putGoods(confiscation.id(), goods);
            made = Optional.of(confiscation);
        }
        Optional<Wanted> cleared = state.clear(kingdomId, player.getUUID());
        cleared.ifPresent(record -> LawWork.cleared(server, record, false, "wanted_cleared"));
        Component name = LawWork.kingdomName(server, kingdomId, "");
        Component message = made.<Component>map(confiscation -> Component.translatable(KEY + reasonKey, name,
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(confiscation.items()),
                        MoneyTexts.amount(LawSettings.rules().ransom(confiscation.worth()), units)))
                .orElseGet(() -> Component.translatable(KEY + reasonKey + "_empty", name));
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
        if (toTreasury > 0) {
            player.sendSystemMessage(Component.translatable(KEY + "coins_taken", MoneyTexts.amount(toTreasury, units)).withStyle(ChatFormatting.RED));
        }
        LOGGER.info("{} executed by the law of {}{}: {} item(s) confiscated (#{}), {} in coins to the treasury",
                player.getGameProfile().getName(), kingdomId, wanted.isPresent() ? "" : " (not on record)", count,
                made.map(Confiscation::id).orElse(0L), toTreasury);
        return made;
    }

    // ---------------------------------------------------------------- ransom

    /**
     * Whether a player stands where a kingdom gives confiscated goods back: by its throne, the town hall of one of its cities, a
     * barracks block, or in or near a working watchtower or barracks building of the kingdom.
     */
    public static boolean atRansomPlace(ServerPlayer player, UUID kingdomId) {
        MinecraftServer server = player.server;
        String dimension = Dimensions.id(player.level());
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
        if (capital.isPresent() && capital.get().dimension().equals(dimension) && player.distanceToSqr(capital.get().x() + 0.5,
                capital.get().y() + 0.5, capital.get().z() + 0.5) <= PLACE_REACH * PLACE_REACH) {
            return true;
        }
        // The town halls of the cities of the kingdom.
        for (io.github.sekerator.kingdomworld.core.road.City city : io.github.sekerator.kingdomworld.common.road.RoadWork.state(server)
                .citiesOf(kingdomId)) {
            if (city.dimension().equals(dimension)
                    && player.distanceToSqr(city.x() + 0.5, city.y() + 0.5, city.z() + 0.5) <= PLACE_REACH * PLACE_REACH) {
                return true;
            }
        }
        if (!ArmyWork.isReadOnly(server)) {
            for (WorldPos barracks : ArmyWork.state(server).barracksOf(kingdomId)) {
                if (barracks.dimension().equals(dimension)
                        && player.distanceToSqr(barracks.x() + 0.5, barracks.y() + 0.5, barracks.z() + 0.5) <= PLACE_REACH * PLACE_REACH) {
                    return true;
                }
            }
        }
        for (String role : List.of(BuildingRoles.TOWER, "barracks")) {
            for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdomId, role)) {
                var box = effect.building().box();
                if (effect.building().dimension().equals(dimension)
                        && player.getX() >= box.minX() - BUILDING_MARGIN && player.getX() <= box.maxX() + 1 + BUILDING_MARGIN
                        && player.getY() >= box.minY() - BUILDING_MARGIN && player.getY() <= box.maxY() + 1 + BUILDING_MARGIN
                        && player.getZ() >= box.minZ() - BUILDING_MARGIN && player.getZ() <= box.maxZ() + 1 + BUILDING_MARGIN) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * What the owner pays for goods: half their worth (the config's share), nothing when the kingdom is gone.
     */
    public static long ransomPrice(MinecraftServer server, Confiscation confiscation) {
        return KingdomWorldApi.kingdom(server, confiscation.kingdomId()).isEmpty() ? 0 : LawSettings.rules().ransom(confiscation.worth());
    }

    /**
     * The owner buys goods back: the ransom goes to the treasury of the kingdom, and exactly the goods taken come back (what
     * does not fit into the inventory falls at the owner's feet). Goods of a kingdom that is gone come back for free.
     *
     * @param account whether to pay from the account rather than with coins
     */
    public static Outcome ransom(ServerPlayer player, long id, boolean account) {
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        LawState state = LawWork.state(server);
        Outcome check = state.checkRansom(player.getUUID(), id);
        if (!check.ok()) {
            return check;
        }
        Confiscation confiscation = state.confiscation(id).orElseThrow();
        UUID kingdomId = confiscation.kingdomId();
        long price = ransomPrice(server, confiscation);
        if (price > 0) {
            if (!atRansomPlace(player, kingdomId)) {
                return Outcome.fail(KingdomError.NOT_AT_RANSOM_PLACE);
            }
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            if (account) {
                Outcome paid = economy.payKingdom(kingdomId, kingdomId, price, player.getUUID(), LedgerCategory.LAW, day);
                if (!paid.ok()) {
                    return paid;
                }
            } else {
                Optional<CoinPayment.Plan> plan = Purses.plan(player, kingdomId, price);
                if (plan.isEmpty()) {
                    return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
                }
                Outcome can = economy.checkPayKingdom(kingdomId, kingdomId, price, null);
                if (!can.ok()) {
                    return can;
                }
                if (!Purses.take(player, kingdomId, plan.get().coins())) {
                    return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
                }
                Outcome paid = economy.payKingdom(kingdomId, kingdomId, price, null, LedgerCategory.LAW, day);
                if (!paid.ok()) {
                    Purses.giveBack(player, kingdomId, plan.get().coins());
                    return paid;
                }
                if (plan.get().change() > 0) {
                    Purses.payOut(player, kingdomId, plan.get().change());
                }
            }
        }
        List<ItemStack> goods = LawSavedData.get(server).takeGoods(id);
        state.release(id);
        for (ItemStack stack : goods) {
            ItemStack rest = stack.copy();
            player.getInventory().add(rest);
            if (!rest.isEmpty()) {
                // No room: the rest falls at the owner's feet, so that exactly what was taken comes back.
                player.drop(rest, false);
            }
        }
        player.getInventory().setChanged();
        LOGGER.info("{} bought back confiscation #{} ({} item(s)) from {} for {}", player.getGameProfile().getName(), id, confiscation.items(),
                kingdomId, price);
        return Outcome.DONE;
    }

    /**
     * Goods nobody claimed in time go to the storehouses of their kingdom; what does not fit waits for room.
     */
    static void expireConfiscations(MinecraftServer server, long day, LawRules rules) {
        for (Confiscation confiscation : LawWork.state(server).expired(day, rules)) {
            store(server, confiscation);
        }
    }

    /**
     * Sends goods to the storehouses of their kingdom now, as if nobody claimed them in time; for operators.
     *
     * @return whether everything went into the storehouses
     */
    public static boolean storeNow(MinecraftServer server, long id) {
        if (LawWork.isReadOnly(server)) {
            return false;
        }
        Optional<Confiscation> confiscation = LawWork.state(server).confiscation(id);
        return confiscation.isPresent() && store(server, confiscation.get());
    }

    private static boolean store(MinecraftServer server, Confiscation confiscation) {
        LawState state = LawWork.state(server);
        LawSavedData data = LawSavedData.get(server);
        {
            Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, confiscation.kingdomId());
            if (kingdom.isEmpty()) {
                return false;
            }
            WorldPos near = KingdomWorldApi.state(server).territory().territory(confiscation.kingdomId()).map(Territory::capital)
                    .map(capital -> new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z())).orElse(null);
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack stack : data.takeGoods(confiscation.id())) {
                ItemStack left = KingdomStorage.insert(server, confiscation.kingdomId(), stack, near);
                if (!left.isEmpty()) {
                    rest.add(left);
                }
            }
            if (rest.isEmpty()) {
                state.release(confiscation.id());
                LOGGER.info("Confiscation #{} of {} went to the storehouses of {}", confiscation.id(), confiscation.ownerName(),
                        kingdom.get().name());
                return true;
            }
            data.putGoods(confiscation.id(), rest);
            return false;
        }
    }

    // ---------------------------------------------------------------- debts

    /**
     * The player pays back what they owe the treasury of a kingdom: from the account first, then with coins.
     *
     * @return what was paid back
     */
    public static long payDebt(ServerPlayer player, UUID kingdomId) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        long debt = economy.treasury(kingdomId).map(treasury -> treasury.debt(player.getUUID())).orElse(0L);
        if (debt <= 0) {
            return 0;
        }
        long day = TreasuryActions.day(server);
        long paid = 0;
        long fromAccount = Math.min(debt, economy.accounts().balance(player.getUUID(), kingdomId));
        if (fromAccount > 0 && economy.playerToTreasury(player.getUUID(), kingdomId, fromAccount, day).ok()) {
            paid += fromAccount;
        }
        long left = debt - paid;
        CurrencyUnits units = Purses.units(server, kingdomId);
        Coins carried = CoinStacks.count(player.getInventory(), kingdomId);
        long coinWorth = carried.value(units);
        if (left > 0 && coinWorth > 0) {
            long fromCoins = Math.min(left, coinWorth);
            Optional<CoinPayment.Plan> plan = fromCoins >= coinWorth ? Optional.of(new CoinPayment.Plan(carried, 0))
                    : CoinPayment.plan(carried, fromCoins, units);
            if (plan.isPresent() && Purses.take(player, kingdomId, plan.get().coins())) {
                if (economy.repayDebt(kingdomId, player.getUUID(), fromCoins, day).ok()) {
                    paid += fromCoins;
                    if (plan.get().change() > 0) {
                        Purses.payOut(player, kingdomId, plan.get().change());
                    }
                } else {
                    Purses.giveBack(player, kingdomId, plan.get().coins());
                }
            }
        }
        return paid;
    }
}
