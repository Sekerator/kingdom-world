package io.github.sekerator.kingdomworld.common.economy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.BankView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.RetiredCurrency;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do at a bank: hand in coins of the currency of the kingdom whose land the bank stands on, and take money
 * from their account in that currency as coins. Everything happens on the server in one tick: coins are taken from
 * the inventory and the account credited with exactly their worth; an account is charged for exactly the coins that
 * fit into the inventory, so no coin appears and none is lost.
 */
public final class BankActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far a player may stand from a bank, squared blocks. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    private static final String KEY = "kingdomworld.bank.";

    private BankActions() {
    }

    /**
     * A player used a bank: opens its screen, or says why it does not work.
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        Optional<Kingdom> kingdom = kingdomAt(player.serverLevel(), pos);
        if (kingdom.isEmpty()) {
            player.displayClientMessage(KingdomActions.errorMessage(KingdomError.BANK_OUTSIDE), true);
            return;
        }
        if (KingdomWorldApi.isReadOnly(player.server)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(KEY + "read_only"), true);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new EconomyPackets.BankOpen(view(player, pos, kingdom.get()), true));
    }

    private static Optional<Kingdom> kingdomAt(ServerLevel level, BlockPos pos) {
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner == null ? Optional.empty() : KingdomWorldApi.kingdom(level.getServer(), owner);
    }

    /**
     * What the bank screen shows to a player.
     */
    public static BankView view(ServerPlayer player, BlockPos pos, Kingdom kingdom) {
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        Economy economy = state.economy();
        Treasury treasury = economy.treasury(kingdom.id()).orElseThrow();
        List<BankView.Account> accounts = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : economy.accounts().of(player.getUUID()).entrySet()) {
            if (accounts.size() >= BankView.MAX_ACCOUNTS) {
                break;
            }
            UUID currency = entry.getKey();
            Optional<Treasury> active = economy.treasury(currency);
            Optional<Kingdom> owner = state.kingdoms().get(currency);
            if (active.isPresent() && owner.isPresent()) {
                accounts.add(new BankView.Account(currency, active.get().currencyName(), owner.get().name(), owner.get().color(), false,
                        entry.getValue(), active.get().units().silver(), active.get().units().gold()));
            } else {
                RetiredCurrency old = economy.retired().get(currency);
                if (old != null) {
                    accounts.add(new BankView.Account(currency, old.name(), old.kingdomName(), old.color(), true, entry.getValue(),
                            old.units().silver(), old.units().gold()));
                }
            }
        }
        return new BankView(pos, kingdom.id(), kingdom.name(), kingdom.color(), treasury.currencyName(), treasury.units().silver(),
                treasury.units().gold(), CoinStacks.count(player.getInventory(), kingdom.id()), treasury.debt(player.getUUID()), accounts);
    }

    /**
     * Handles a request from the bank screen and refreshes it.
     */
    public static void handle(ServerPlayer player, EconomyPackets.BankChange change) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = change.pos();
        if (player.isSpectator() || !level.isLoaded(pos) || !level.getBlockState(pos).is(ModRegistries.BANK.get())
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "too_far"));
            return;
        }
        Optional<Kingdom> kingdom = kingdomAt(level, pos);
        if (kingdom.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.BANK_OUTSIDE));
            return;
        }
        if (KingdomWorldApi.isReadOnly(player.server)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.READ_ONLY));
            return;
        }
        KingdomActions.Feedback feedback = change.action() == EconomyPackets.BankChange.Action.DEPOSIT
                ? deposit(player, kingdom.get(), change.coin(), change.amount())
                : withdraw(player, kingdom.get(), change.coin(), change.amount());
        KingdomActions.reply(player, feedback);
        KingdomWorldNetwork.sendToPlayer(player, new EconomyPackets.BankOpen(view(player, pos, kingdom.get()), false));
    }

    /**
     * Hands in coins of the bank's currency: every coin of a kind, a number of them, or every coin of the currency.
     *
     * @param coin  the kind, or {@code null} for every coin of the currency
     * @param count how many of the kind, 0 for all
     */
    public static KingdomActions.Feedback deposit(ServerPlayer player, Kingdom kingdom, Denomination coin, long count) {
        Economy economy = KingdomWorldApi.state(player.server).economy();
        UUID currency = kingdom.id();
        CurrencyUnits units = economy.units(currency).orElse(CurrencyUnits.DEFAULT);
        Coins carried = CoinStacks.count(player.getInventory(), currency);
        Coins handed;
        if (coin == null) {
            handed = carried;
        } else {
            long have = carried.count(coin);
            handed = Coins.of(coin, count <= 0 ? have : Math.min(count, have));
        }
        long worth = handed.value(units);
        if (worth >= Money.MAX) {
            return KingdomActions.Feedback.error(KingdomError.TOO_MUCH_MONEY);
        }
        Outcome check = economy.checkDeposit(player.getUUID(), currency, worth);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        Coins taken = Coins.NONE;
        for (Denomination denomination : Denomination.values()) {
            long wanted = handed.count(denomination);
            if (wanted > 0) {
                taken = taken.plus(denomination, CoinStacks.take(player.getInventory(), currency, denomination, wanted));
            }
        }
        long credited = taken.value(units);
        if (credited > 0 && !economy.deposit(player.getUUID(), currency, credited).ok()) {
            // Cannot happen after the check above: no more was taken than was checked. Give the coins back.
            CoinStacks.give(player.getInventory(), currency, taken);
            LOGGER.error("Bank deposit of {} by {} failed after the check; the coins were given back", credited, player.getGameProfile().getName());
            return KingdomActions.Feedback.error(KingdomError.TOO_MUCH_MONEY);
        }
        if (credited == 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_COINS);
        }
        economy.coinsIssued(currency, -credited);
        LOGGER.debug("{} handed in {} of {} at a bank", player.getGameProfile().getName(), credited, kingdom.name());
        return KingdomActions.Feedback.ok(KEY + "deposited", MoneyTexts.amount(credited, units),
                MoneyTexts.amount(economy.accounts().balance(player.getUUID(), currency), units));
    }

    /**
     * Takes money from the account in the bank's currency as coins: as many of {@code largest} as fit the amount, the
     * rest in smaller coins. The account is charged for exactly the coins that fit into the inventory.
     *
     * @param largest the largest coin to pay out in, or {@code null} for the fewest coins
     */
    public static KingdomActions.Feedback withdraw(ServerPlayer player, Kingdom kingdom, Denomination largest, long amount) {
        Economy economy = KingdomWorldApi.state(player.server).economy();
        UUID currency = kingdom.id();
        CurrencyUnits units = economy.units(currency).orElse(CurrencyUnits.DEFAULT);
        if (!Money.isPayment(amount)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (economy.accounts().balance(player.getUUID(), currency) < amount) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        Coins wanted = Coins.countOut(amount, units, largest == null ? Denomination.GOLD : largest);
        Coins fitting = CoinStacks.fitting(player.getInventory(), currency, wanted);
        long worth = fitting.value(units);
        if (worth <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        Outcome outcome = economy.withdraw(player.getUUID(), currency, worth);
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Coins left = CoinStacks.give(player.getInventory(), currency, fitting);
        long refund = left.value(units);
        if (refund > 0) {
            // What did not fit after all goes back onto the account; nothing is dropped. The account gave more a moment ago,
            // so it always takes this back.
            if (!economy.deposit(player.getUUID(), currency, refund).ok()) {
                LOGGER.error("Bank refund of {} to {} failed after a withdrawal of {}", refund, player.getGameProfile().getName(), worth);
            }
        }
        long paid = worth - refund;
        economy.coinsIssued(currency, paid);
        if (paid <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        LOGGER.debug("{} took {} of {} from a bank", player.getGameProfile().getName(), paid, kingdom.name());
        if (paid < amount) {
            return KingdomActions.Feedback.ok(KEY + "withdrew_partly", MoneyTexts.amount(paid, units), MoneyTexts.amount(amount, units));
        }
        return KingdomActions.Feedback.ok(KEY + "withdrew", MoneyTexts.amount(paid, units));
    }
}
