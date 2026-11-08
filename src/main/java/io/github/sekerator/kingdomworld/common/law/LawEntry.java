package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.CityGates;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.EntryRules;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * The gates of cities. The guards at a gate check every player who comes in: a wanted player is called out and stopped as
 * guards elsewhere do; a large city asks its entry fee from those who are not subjects, officials or of high reputation.
 * Paid once, the pass holds a game day in every city of the kingdom; a player who switched paying by themselves on pays at
 * the gate without a word, others see the window with the price and are kept out until they pay. Getting in past the gates
 * is a petty crime when somebody sees it.
 *
 * <p>The cities and walls come with the roads: they call {@link #onEnter} when a player passes a gate and {@link #trespass}
 * when one gets over a wall; until then {@link LawWork#gates()} knows no city.
 */
public final class LawEntry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LawEntry() {
    }

    /**
     * What the entry costs this player in a city of a kingdom: 0 for subjects, officials, high reputation and small cities.
     */
    public static long fee(MinecraftServer server, ServerPlayer player, CityGates.City city) {
        if (!city.large()) {
            return 0;
        }
        LawState state = LawWork.state(server);
        boolean member = KingdomWorldApi.state(server).membership().isMember(player.getUUID(), city.kingdomId())
                || LawWork.policy().isNoble(city.kingdomId(), player.getUUID());
        return EntryRules.fee(state.law(city.kingdomId()), member, state.reputation(city.kingdomId(), player.getUUID()), LawSettings.rules());
    }

    /**
     * A player comes through the gate of a city.
     *
     * @return whether the guards let the player in
     */
    public static boolean onEnter(ServerPlayer player, CityGates.City city) {
        return onEnter(player, city, null);
    }

    /**
     * A player comes through a gate of a city.
     *
     * @param gate the gate, for the window that asks the fee
     * @return whether the guards let the player in
     */
    public static boolean onEnter(ServerPlayer player, CityGates.City city, @org.jetbrains.annotations.Nullable
            io.github.sekerator.kingdomworld.core.citizen.WorldPos gate) {
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return true;
        }
        LawState state = LawWork.state(server);
        long now = LawWork.now(server);
        if (state.wanted(city.kingdomId(), player.getUUID()).isPresent() && LawGuards.catchable(server, player)) {
            // The guards at the gate stop the wanted as any guard does.
            LawGuards.hail(server, null, player, city.kingdomId(), now);
            return false;
        }
        long fee = fee(server, player, city);
        boolean pass = state.hasPass(city.kingdomId(), player.getUUID(), now);
        boolean autoPay = state.autoPay(player.getUUID());
        EntryRules.Decision decision = EntryRules.decide(fee, pass, autoPay, canPay(player, city.kingdomId(), fee));
        switch (decision) {
            case FREE, PASS -> {
                return true;
            }
            case PAY -> {
                Outcome paid = pay(player, city.kingdomId(), fee, !hasCoins(player, city.kingdomId(), fee));
                if (paid.ok()) {
                    return true;
                }
                LawActions.openEntry(player, city, fee, gate);
                return false;
            }
            default -> {
                player.displayClientMessage(Component.translatable(LawWork.KEY + "entry_price", LawTexts.shortAmount(fee,
                        LawTexts.units(server, city.kingdomId()))).withStyle(ChatFormatting.GOLD), true);
                LawActions.openEntry(player, city, fee, gate);
                return false;
            }
        }
    }

    static boolean hasCoins(ServerPlayer player, UUID kingdomId, long fee) {
        CurrencyUnits units = Purses.units(player.server, kingdomId);
        return CoinStacks.count(player.getInventory(), kingdomId).value(units) >= fee;
    }

    static boolean canPay(ServerPlayer player, UUID kingdomId, long fee) {
        return hasCoins(player, kingdomId, fee)
                || KingdomWorldApi.state(player.server).economy().accounts().balance(player.getUUID(), kingdomId) >= fee;
    }

    /**
     * Pays the entry fee into the treasury and gives the pass for a game day.
     *
     * @param account whether to pay from the account rather than with coins
     */
    public static Outcome pay(ServerPlayer player, UUID kingdomId, long fee, boolean account) {
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        if (fee <= 0) {
            return Outcome.fail(KingdomError.NO_ENTRY_FEE);
        }
        if (LawWork.state(server).hasPass(kingdomId, player.getUUID(), LawWork.now(server))) {
            // A second click or a repeated packet pays nothing twice.
            return Outcome.fail(KingdomError.PASS_HELD);
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        if (account) {
            Outcome paid = economy.payKingdom(kingdomId, kingdomId, fee, player.getUUID(), LedgerCategory.LAW, day);
            if (!paid.ok()) {
                return paid;
            }
        } else {
            Optional<CoinPayment.Plan> plan = Purses.plan(player, kingdomId, fee);
            if (plan.isEmpty()) {
                return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
            }
            Outcome can = economy.checkPayKingdom(kingdomId, kingdomId, fee, null);
            if (!can.ok()) {
                return can;
            }
            if (!Purses.take(player, kingdomId, plan.get().coins())) {
                return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
            }
            Outcome paid = economy.payKingdom(kingdomId, kingdomId, fee, null, LedgerCategory.LAW, day);
            if (!paid.ok()) {
                Purses.giveBack(player, kingdomId, plan.get().coins());
                return paid;
            }
            if (plan.get().change() > 0) {
                Purses.payOut(player, kingdomId, plan.get().change());
            }
        }
        LawRules rules = LawSettings.rules();
        LawState state = LawWork.state(server);
        state.grantPass(kingdomId, player.getUUID(), LawWork.now(server), rules);
        state.changeReputation(kingdomId, player.getUUID(), rules.taxReputation);
        player.displayClientMessage(Component.translatable(LawWork.KEY + "entry_paid", LawTexts.shortAmount(fee, LawTexts.units(server, kingdomId)))
                .withStyle(ChatFormatting.GREEN), true);
        LOGGER.debug("{} paid {} to enter the cities of {}", player.getGameProfile().getName(), fee, kingdomId);
        return Outcome.DONE;
    }

    /**
     * A player got into a city of a kingdom past its gates (over a wall, or without paying): a petty crime if seen.
     */
    public static boolean trespass(ServerPlayer player, BlockPos where, UUID kingdomId) {
        return LawWork.deed(player, where, kingdomId, CrimeKind.TRESPASS, 0, "", null, false, null);
    }
}
