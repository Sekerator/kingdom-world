package io.github.sekerator.kingdomworld.common.trade;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Exchanging currencies at a bank: a player gives money of another current currency (from the account or as coins), the
 * treasury of the bank's kingdom takes it into its reserves and pays its own currency by the rates, less the fee the king
 * set, as far as its funds go. Nothing is made: the money given and the money got only change hands.
 */
public final class ExchangeActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    private static final String KEY = "kingdomworld.exchange.";

    private ExchangeActions() {
    }

    private static Optional<Kingdom> kingdomAt(ServerLevel level, BlockPos pos) {
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner == null ? Optional.empty() : KingdomWorldApi.kingdom(level.getServer(), owner);
    }

    private static boolean nearBank(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        return !player.isSpectator() && level.isLoaded(pos) && level.getBlockState(pos).is(ModRegistries.BANK.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    /**
     * Opens the exchange of a bank the player stands at.
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        Optional<Kingdom> kingdom = kingdomAt(player.serverLevel(), pos);
        if (!nearBank(player, pos) || kingdom.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail("kingdomworld.bank.too_far"));
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new TradePackets.ExchangeOpen(view(player, pos, kingdom.get()), true));
    }

    public static TradeViews.ExchangeView view(ServerPlayer player, BlockPos pos, Kingdom kingdom) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        Treasury treasury = economy.treasury(kingdom.id()).orElseThrow();
        Market market = KingdomWorldApi.state(server).trade().market(kingdom.id(), TradeSettings.rules());
        List<TradeViews.Purse> sources = new ArrayList<>();
        for (Treasury other : economy.treasuries()) {
            UUID currency = other.kingdomId();
            if (currency.equals(kingdom.id()) || sources.size() >= TradeViews.MAX_ROWS) {
                continue;
            }
            long balance = economy.accounts().balance(player.getUUID(), currency);
            Coins carried = CoinStacks.count(player.getInventory(), currency);
            if (balance > 0 || !carried.isEmpty()) {
                Purses.currency(server, currency).ifPresent(info -> sources.add(new TradeViews.Purse(info, balance, carried)));
            }
        }
        return new TradeViews.ExchangeView(pos, kingdom.id(), kingdom.name(), kingdom.color(), Purses.currency(server, kingdom.id()).orElseThrow(),
                market.exchangeFeePermille(), treasury.funds(), economy.accounts().balance(player.getUUID(), kingdom.id()), sources);
    }

    /**
     * Handles a request from the exchange screen and refreshes it.
     */
    public static void handle(ServerPlayer player, TradePackets.ExchangeAction action) {
        Optional<Kingdom> kingdom = kingdomAt(player.serverLevel(), action.pos());
        if (!nearBank(player, action.pos()) || kingdom.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail("kingdomworld.bank.too_far"));
            return;
        }
        if (KingdomWorldApi.isReadOnly(player.server)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.READ_ONLY));
            return;
        }
        KingdomActions.reply(player, exchange(player, kingdom.get(), action.currency(), action.amount(), action.fromAccount(), action.toAccount()));
        KingdomWorldNetwork.sendToPlayer(player, new TradePackets.ExchangeOpen(view(player, action.pos(), kingdom.get()), false));
    }

    /**
     * Exchanges an amount of a currency for the bank's own.
     *
     * @param fromAccount whether the amount comes from the account rather than from coins (with change for coins)
     * @param toAccount   whether the bank pays onto the account rather than in coins (what does not fit goes onto it)
     */
    public static KingdomActions.Feedback exchange(ServerPlayer player, Kingdom kingdom, UUID currency, long amount, boolean fromAccount,
                                                  boolean toAccount) {
        MinecraftServer server = player.server;
        Economy economy = KingdomWorldApi.state(server).economy();
        TradeRules rules = TradeSettings.rules();
        int fee = KingdomWorldApi.state(server).trade().market(kingdom.id(), rules).exchangeFeePermille();
        UUID payer = fromAccount ? player.getUUID() : null;
        UUID payee = toAccount ? player.getUUID() : null;
        Outcome check = economy.checkExchange(kingdom.id(), currency, amount, fee, payer, payee);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        Optional<CoinPayment.Plan> plan = fromAccount ? Optional.empty() : Purses.plan(player, currency, amount);
        if (!fromAccount && (plan.isEmpty() || !Purses.take(player, currency, plan.get().coins()))) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        Economy.Exchanged done = economy.exchange(kingdom.id(), currency, amount, fee, payer, payee, TreasuryActions.day(server));
        if (!done.outcome().ok()) {
            plan.ifPresent(found -> Purses.giveBack(player, currency, found.coins()));
            LOGGER.error("An exchange at the bank of {} failed after its checks: {}", kingdom.name(), done.outcome().error());
            return KingdomActions.Feedback.error(done.outcome().error());
        }
        plan.ifPresent(found -> {
            if (found.change() > 0) {
                Purses.payOut(player, currency, found.change());
            }
        });
        if (!toAccount) {
            Purses.payOut(player, kingdom.id(), done.got());
        }
        LOGGER.debug("{} exchanged {} for {} at the bank of {}", player.getGameProfile().getName(), amount, done.got(), kingdom.name());
        return KingdomActions.Feedback.ok(KEY + "done", MoneyTexts.amount(amount, Purses.units(server, currency)),
                MoneyTexts.amount(done.got(), Purses.units(server, kingdom.id())));
    }
}
