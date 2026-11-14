package io.github.sekerator.kingdomworld.common.economy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TreasuryView;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.MintMetals;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with the treasury of their kingdom, shared by the throne screen and the commands: look at it, set
 * the tax and the wages, rename the currency, pay players from it and pay into it. Members see the treasury; changes
 * need the right to manage the treasury (renaming the currency is the king's). The server checks every request.
 */
public final class TreasuryActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.treasury.";

    private TreasuryActions() {
    }

    public static long day(MinecraftServer server) {
        return PostWork.gameDay(server);
    }

    // ---------------------------------------------------------------- the view

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new EconomyPackets.TreasuryResponse(view)));
    }

    /**
     * The treasury of a kingdom for one of its members; empty for anybody else.
     */
    public static Optional<TreasuryView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = state.kingdoms().get(kingdomId);
        Optional<Treasury> found = state.economy().treasury(kingdomId);
        if (kingdom.isEmpty() || found.isEmpty() || !state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        Treasury treasury = found.get();
        EconomyRules rules = EconomySettings.rules();
        long today = day(server);
        List<TreasuryView.JournalRow> journal = new ArrayList<>();
        for (TreasuryJournal.Period period : TreasuryJournal.Period.values()) {
            treasury.journal().totals(today, period).forEach((category, totals) ->
                    journal.add(new TreasuryView.JournalRow(period, category, totals.income(), totals.expense())));
        }
        Map<String, Integer> byProfession = new HashMap<>();
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            byProfession.merge(citizen.professionId(), 1, Integer::sum);
        }
        List<TreasuryView.Wage> wages = new ArrayList<>();
        for (Profession profession : Professions.REGISTRY.all()) {
            if (wages.size() < TreasuryView.MAX_WAGES) {
                wages.add(new TreasuryView.Wage(profession.id(), treasury.wage(profession.id(), rules), rules.defaultWage(profession.id()),
                        byProfession.getOrDefault(profession.id(), 0)));
            }
        }
        List<Map.Entry<UUID, Long>> debts = new ArrayList<>(treasury.debts().entrySet());
        debts.sort(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()));
        List<TreasuryView.Debtor> debtors = new ArrayList<>();
        long debtTotal = 0;
        for (Map.Entry<UUID, Long> debt : debts) {
            debtTotal = Money.addCapped(debtTotal, debt.getValue());
            if (debtors.size() < TreasuryView.MAX_DEBTORS) {
                String name = PlayerNames.name(server, debt.getKey());
                debtors.add(new TreasuryView.Debtor(name.length() > TreasuryView.MAX_NAME ? name.substring(0, TreasuryView.MAX_NAME) : name,
                        debt.getValue()));
            }
        }
        int taxpayers = state.membership().kingdom(kingdomId).map(members -> (int) members.members().values().stream()
                .filter(member -> member.role() != Role.KING).count()).orElse(0);
        long untilPayday = Math.max(0, (today + 1) * CitizenNeeds.TICKS_PER_DAY - KingdomWorldApi.gameTime(server));
        var payroll = treasury.lastPayroll();
        boolean canManage = state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_TREASURY);
        Role role = state.membership().memberOf(viewer).map(io.github.sekerator.kingdomworld.core.membership.Member::role).orElse(null);
        return Optional.of(new TreasuryView(kingdomId, canManage, canManage && (role == Role.KING || role == Role.TREASURER),
                viewer.equals(kingdom.get().rulerId()), kingdom.get().name(), treasury.currencyName(), treasury.units().silver(),
                treasury.units().gold(), treasury.balance(), treasury.vault(), journal, treasury.pollTax(), rules.maxPollTax(), taxpayers,
                untilPayday, new TreasuryView.Payroll(payroll.day(), payroll.due(), payroll.paid(), payroll.citizens(), payroll.underpaid()),
                wages, rules.maxWage(), debtors, debts.size(), debtTotal, mint(server, state, kingdomId, treasury, today),
                state.economy().rate(kingdomId), reserves(state, treasury)));
    }

    private static List<TreasuryView.Reserve> reserves(KingdomWorldState state, Treasury treasury) {
        List<TreasuryView.Reserve> reserves = new ArrayList<>();
        var economy = state.economy();
        for (Map.Entry<UUID, Long> entry : treasury.reserves().entrySet()) {
            if (reserves.size() >= TreasuryView.MAX_RESERVES) {
                break;
            }
            Optional<Treasury> other = economy.treasury(entry.getKey());
            Optional<Kingdom> kingdom = state.kingdoms().get(entry.getKey());
            if (other.isEmpty() || kingdom.isEmpty()) {
                continue;
            }
            long worth = io.github.sekerator.kingdomworld.core.trade.ExchangeRates.convert(entry.getValue(), economy.rate(entry.getKey()),
                    economy.rate(treasury.kingdomId()), 0);
            reserves.add(new TreasuryView.Reserve(other.get().currencyName(), kingdom.get().name(), kingdom.get().color(), entry.getValue(),
                    other.get().units().silver(), other.get().units().gold(), worth));
        }
        return reserves;
    }

    private static TreasuryView.Mint mint(MinecraftServer server, KingdomWorldState state, UUID kingdomId, Treasury treasury, long today) {
        int posts = 0;
        int workers = 0;
        WorkStatus best = WorkStatus.NONE;
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            if (!post.professionId().equals(Professions.MINTER.id())) {
                continue;
            }
            posts++;
            workers += post.workers().size();
            if (best != WorkStatus.WORKING) {
                best = post.status();
            }
        }
        TreasuryJournal.Totals day = treasury.journal().totals(today, TreasuryJournal.Period.TODAY)
                .getOrDefault(LedgerCategory.MINTING, TreasuryJournal.Totals.ZERO);
        TreasuryJournal.Totals week = treasury.journal().totals(today, TreasuryJournal.Period.WEEK)
                .getOrDefault(LedgerCategory.MINTING, TreasuryJournal.Totals.ZERO);
        return new TreasuryView.Mint(posts, workers, best, day.income(), week.income(), EconomySettings.rules().mintToVault(),
                mintMetals(server, kingdomId));
    }

    /**
     * The metals of the mint's config that have an item, each once, with what the mint of a kingdom does with them and how
     * many of them its storehouses hold.
     */
    public static List<TreasuryView.MintMetal> mintMetals(MinecraftServer server, UUID kingdomId) {
        List<TreasuryView.MintMetal> metals = new ArrayList<>();
        MintRates rates = EconomySettings.mintRates();
        for (MintRates.Rate rate : rates.rates()) {
            net.minecraft.world.item.Item item = rates.metal(rate);
            if (item == null || metals.size() >= TreasuryView.MAX_METALS || metals.stream().anyMatch(metal -> metal.metal().equals(rate.target()))) {
                continue;
            }
            MintMetals.Setting setting = MinterBehavior.setting(server, kingdomId, rate.target());
            long stock = io.github.sekerator.kingdomworld.common.storage.KingdomStorage.count(server, kingdomId, stack -> rates.rateOf(stack) == rate);
            metals.add(new TreasuryView.MintMetal(rate.target(), new net.minecraft.world.item.ItemStack(item), rate.coin(), rate.count(),
                    setting.strike(), setting.keep(), Math.min(Money.MAX, stock)));
        }
        return metals;
    }

    // ---------------------------------------------------------------- changes

    /**
     * Handles a decision from the treasury tab and sends the fresh view.
     */
    public static void handle(ServerPlayer player, EconomyPackets.TreasuryChange change) {
        MinecraftServer server = player.server;
        UUID kingdom = change.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdom)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.Feedback feedback = switch (change.action()) {
            case POLL_TAX -> setPollTax(player, kingdom, change.amount());
            case WAGE -> setWage(player, kingdom, change.text(), change.amount());
            case RENAME -> rename(player, kingdom, change.text());
            case PAY -> payByName(player, kingdom, change.text(), change.amount());
            case TAKE_COINS -> takeCoins(player, kingdom, change.text(), change.amount());
            case MINT_STRIKE -> setMintMetal(player, kingdom, change.text(), change.amount() != 0, null);
            case MINT_KEEP -> setMintMetal(player, kingdom, change.text(), null,
                    change.amount() > MintMetals.MAX_KEEP ? -1 : (int) change.amount());
        };
        KingdomActions.reply(player, feedback);
        sendView(player, kingdom);
    }

    public static KingdomActions.Feedback setPollTax(ServerPlayer player, UUID kingdom, long amount) {
        EconomyRules rules = EconomySettings.rules();
        Outcome outcome = KingdomWorldApi.change(player.server, "set the poll tax",
                state -> state.setPollTax(player.getUUID(), kingdom, amount, rules.maxPollTax()));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        return KingdomActions.Feedback.ok(KEY + "poll_tax_set", MoneyTexts.amount(amount, units(player.server, kingdom)));
    }

    public static KingdomActions.Feedback setWage(ServerPlayer player, UUID kingdom, String profession, long amount) {
        EconomyRules rules = EconomySettings.rules();
        Outcome outcome = KingdomWorldApi.change(player.server, "set a wage",
                state -> state.setWage(player.getUUID(), kingdom, profession, amount, rules));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        return KingdomActions.Feedback.ok(KEY + "wage_set", CitizenTexts.profession(profession),
                MoneyTexts.amount(amount, units(player.server, kingdom)));
    }

    /**
     * Sets whether the mint of a kingdom strikes coins from a metal of the config, or how many of it stay in the storehouses.
     * Kingdoms of the world strike every metal; their settings are not changed.
     *
     * @param strike whether to strike, or {@code null} to leave it
     * @param keep   items to leave in the storehouses, or {@code null} to leave it
     */
    public static KingdomActions.Feedback setMintMetal(ServerPlayer player, UUID kingdom, String metal, Boolean strike, Integer keep) {
        MinecraftServer server = player.server;
        Optional<TreasuryView.MintMetal> found = mintMetals(server, kingdom).stream().filter(entry -> entry.metal().equals(metal)).findFirst();
        if (found.isEmpty() || keep != null && (keep < 0 || keep > MintMetals.MAX_KEEP)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        MintMetals.Setting before = new MintMetals.Setting(found.get().strike(), found.get().keep());
        MintMetals.Setting after = strike != null ? before.withStrike(strike) : before.withKeep(keep == null ? before.keep() : keep);
        Outcome outcome = KingdomWorldApi.change(server, "set a metal of the mint", state -> state.setMintMetal(player.getUUID(), kingdom, metal, after));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Component name = found.get().item().getHoverName();
        if (strike != null) {
            return KingdomActions.Feedback.ok(strike ? KEY + "mint_on" : KEY + "mint_off", name);
        }
        return after.keep() == 0 ? KingdomActions.Feedback.ok(KEY + "mint_keep_none", name)
                : KingdomActions.Feedback.ok(KEY + "mint_keep", name, after.keep());
    }

    public static KingdomActions.Feedback rename(ServerPlayer player, UUID kingdom, String name) {
        Outcome outcome = KingdomWorldApi.change(player.server, "rename a currency", state -> state.renameCurrency(player.getUUID(), kingdom, name));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        EconomyEvents.currenciesChanged();
        Treasury treasury = KingdomWorldApi.state(player.server).economy().treasury(kingdom).orElseThrow();
        String kingdomName = KingdomWorldApi.kingdom(player.server, kingdom).map(Kingdom::name).orElse("");
        return KingdomActions.Feedback.ok(KEY + "renamed", MoneyTexts.currency(treasury.currencyName(), kingdomName));
    }

    // ---------------------------------------------------------------- payments

    static io.github.sekerator.kingdomworld.core.economy.CurrencyUnits units(MinecraftServer server, UUID currency) {
        return KingdomWorldApi.state(server).economy().units(currency).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT);
    }

    /**
     * The treasury of the actor's kingdom pays a player onto the player's account.
     */
    public static KingdomActions.Feedback pay(ServerPlayer actor, UUID target, String targetName, long amount) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        if (!io.github.sekerator.kingdomworld.common.kingdom.PlayerNames.isKnown(server, target)) {
            return KingdomActions.Feedback.fail(KEY + "unknown_player", targetName == null ? "" : targetName);
        }
        Outcome outcome = KingdomWorldApi.change(server, "pay from the treasury",
                state -> state.payFromTreasury(actor.getUUID(), kingdom.get().id(), target, amount, day(server)));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Component money = MoneyTexts.amount(amount, units(server, kingdom.get().id()));
        LOGGER.info("{} paid {} from the treasury of {} to {}", actor.getGameProfile().getName(), amount, kingdom.get().name(), targetName);
        KingdomActions.tell(server, target, Component.translatable(KEY + "received", KingdomActions.kingdomName(kingdom.get()), money));
        return KingdomActions.Feedback.ok(KEY + "paid", money, targetName);
    }

    /**
     * The treasury pays a player named in the treasury tab: one who is online or has played on this server.
     */
    public static KingdomActions.Feedback payByName(ServerPlayer actor, UUID kingdom, String name, long amount) {
        Optional<Kingdom> own = KingdomWorldApi.kingdomOf(actor.server, actor.getUUID());
        if (own.isEmpty() || !own.get().id().equals(kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER);
        }
        Optional<com.mojang.authlib.GameProfile> target = PlayerNames.known(actor.server, name);
        if (target.isEmpty()) {
            return KingdomActions.Feedback.fail(KEY + "unknown_player", name == null ? "" : name.strip());
        }
        return pay(actor, target.get().getId(), target.get().getName(), amount);
    }

    /**
     * Coins of one kind leave the vault for the hands of the king or the treasurer: as many as asked, the vault holds and
     * the inventory takes. The treasury's journal writes them down as a transfer.
     */
    public static KingdomActions.Feedback takeCoins(ServerPlayer actor, UUID kingdom, String coinId, long count) {
        MinecraftServer server = actor.server;
        Optional<io.github.sekerator.kingdomworld.core.economy.Denomination> coin =
                io.github.sekerator.kingdomworld.core.economy.Denomination.byId(coinId);
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdom);
        if (coin.isEmpty() || treasury.isEmpty() || count < 1) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        long wanted = Math.min(count, treasury.get().vault().count(coin.get()));
        if (wanted <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_COINS);
        }
        io.github.sekerator.kingdomworld.core.economy.Coins fitting = CoinStacks.fitting(actor.getInventory(), kingdom,
                io.github.sekerator.kingdomworld.core.economy.Coins.of(coin.get(), wanted));
        long taken = fitting.count(coin.get());
        if (taken <= 0) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        long day = day(server);
        Outcome outcome = KingdomWorldApi.change(server, "take coins from a vault",
                state -> state.takeVaultCoins(actor.getUUID(), kingdom, coin.get(), taken, day));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        io.github.sekerator.kingdomworld.core.economy.Coins left = CoinStacks.give(actor.getInventory(), kingdom,
                io.github.sekerator.kingdomworld.core.economy.Coins.of(coin.get(), taken));
        long back = left.count(coin.get());
        if (back > 0) {
            // What did not fit after all goes back into the vault; nothing is dropped.
            KingdomWorldApi.state(server).economy().returnToVault(kingdom, coin.get(), back, day);
        }
        long given = taken - back;
        KingdomWorldApi.state(server).economy().coinsIssued(kingdom, io.github.sekerator.kingdomworld.core.economy.Money.multiplyCapped(given, treasury.get().units().value(coin.get())));
        LOGGER.info("{} took {} {} coin(s) from the vault of {}", actor.getGameProfile().getName(), given, coin.get().id(),
                KingdomWorldApi.kingdom(server, kingdom).map(Kingdom::name).orElse("?"));
        Component coins = MoneyTexts.coins(coin.get(), given);
        return given < count ? KingdomActions.Feedback.ok(KEY + "coins_taken_partly", coins, count)
                : KingdomActions.Feedback.ok(KEY + "coins_taken", coins);
    }

    /**
     * A player pays into the treasury of a kingdom from the account in its currency; a debt is paid back first.
     */
    public static KingdomActions.Feedback deposit(ServerPlayer player, Kingdom kingdom, long amount) {
        MinecraftServer server = player.server;
        long debt = KingdomWorldApi.state(server).economy().treasury(kingdom.id()).map(t -> t.debt(player.getUUID())).orElse(0L);
        Outcome outcome = KingdomWorldApi.change(server, "pay into a treasury",
                state -> state.economy().playerToTreasury(player.getUUID(), kingdom.id(), amount, day(server)));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        var units = units(server, kingdom.id());
        long left = KingdomWorldApi.state(server).economy().treasury(kingdom.id()).map(t -> t.debt(player.getUUID())).orElse(0L);
        if (debt > 0) {
            return KingdomActions.Feedback.ok(KEY + "deposited_debt", MoneyTexts.amount(amount, units), KingdomActions.kingdomName(kingdom),
                    MoneyTexts.amount(left, units));
        }
        return KingdomActions.Feedback.ok(KEY + "deposited", MoneyTexts.amount(amount, units), KingdomActions.kingdomName(kingdom));
    }
}
