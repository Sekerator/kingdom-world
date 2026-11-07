package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * The money a player pays with at a stall or a bank: coins from the inventory (with change) or the account, and money paid
 * out as coins where they fit and onto the account where they do not. Every coin taken or handed out is counted for the
 * exchange rates.
 */
public final class Purses {

    private Purses() {
    }

    public static CurrencyUnits units(MinecraftServer server, UUID currency) {
        return KingdomWorldApi.state(server).economy().units(currency).orElse(CurrencyUnits.DEFAULT);
    }

    /**
     * A current currency as a screen of trade shows it.
     */
    public static Optional<TradeViews.Currency> currency(MinecraftServer server, UUID currency) {
        var state = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = state.kingdoms().get(currency);
        Optional<Treasury> treasury = state.economy().treasury(currency);
        if (kingdom.isEmpty() || treasury.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TradeViews.Currency(currency, treasury.get().currencyName(), kingdom.get().name(), kingdom.get().color(),
                treasury.get().units().silver(), treasury.get().units().gold(), state.economy().rate(currency)));
    }

    /**
     * Plans to pay with coins of a currency the player carries.
     */
    public static Optional<CoinPayment.Plan> plan(ServerPlayer player, UUID currency, long cost) {
        return CoinPayment.plan(CoinStacks.count(player.getInventory(), currency), cost, units(player.server, currency));
    }

    /**
     * Takes exactly these coins from the inventory.
     *
     * @return whether all of them were there; if not, nothing was taken
     */
    public static boolean take(ServerPlayer player, UUID currency, Coins coins) {
        Coins carried = CoinStacks.count(player.getInventory(), currency);
        for (Denomination denomination : Denomination.values()) {
            if (carried.count(denomination) < coins.count(denomination)) {
                return false;
            }
        }
        for (Denomination denomination : Denomination.values()) {
            long wanted = coins.count(denomination);
            if (wanted > 0) {
                CoinStacks.take(player.getInventory(), currency, denomination, wanted);
            }
        }
        KingdomWorldApi.state(player.server).economy().coinsIssued(currency, -coins.value(units(player.server, currency)));
        return true;
    }

    /**
     * Gives coins back that were taken and could not be used.
     */
    public static void giveBack(ServerPlayer player, UUID currency, Coins coins) {
        Coins left = CoinStacks.give(player.getInventory(), currency, coins);
        long worth = coins.value(units(player.server, currency));
        long notGiven = left.value(units(player.server, currency));
        if (notGiven > 0) {
            // No room after all: the worth goes onto the account rather than being lost.
            KingdomWorldApi.state(player.server).economy().deposit(player.getUUID(), currency, notGiven);
        }
        KingdomWorldApi.state(player.server).economy().coinsIssued(currency, worth - notGiven);
    }

    /**
     * Pays an amount out to a player in coins of a currency, the fewest coins; what does not fit goes onto the account.
     *
     * @return what went onto the account
     */
    public static long payOut(ServerPlayer player, UUID currency, long amount) {
        if (amount <= 0) {
            return 0;
        }
        Economy economy = KingdomWorldApi.state(player.server).economy();
        CurrencyUnits units = units(player.server, currency);
        Coins wanted = Coins.countOut(amount, units, Denomination.GOLD);
        Coins fitting = CoinStacks.fitting(player.getInventory(), currency, wanted);
        Coins left = CoinStacks.give(player.getInventory(), currency, fitting);
        long given = fitting.value(units) - left.value(units);
        long rest = amount - given;
        if (rest > 0) {
            economy.deposit(player.getUUID(), currency, rest);
        }
        economy.coinsIssued(currency, given);
        return rest;
    }
}
