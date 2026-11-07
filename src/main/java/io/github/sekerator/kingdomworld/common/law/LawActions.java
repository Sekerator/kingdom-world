package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.CityGates;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.KingdomLaw;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the screens of the law show and do: the "Law" tab of the throne screen (the court), the player's window of the law,
 * the windows to give up to a guard, to pay at a gate and to buy back goods. The server checks every request.
 */
public final class LawActions {
    private static final String KEY = LawWork.KEY;
    /** How near a soldier a player must stand to give up, in blocks. */
    private static final double GUARD_REACH = 6.0;
    /** Goods shown in a row of the window of the law. */
    private static final int SAMPLE = 9;

    private LawActions() {
    }

    static TradeViews.Currency currency(MinecraftServer server, UUID kingdomId, String fallbackName) {
        return Purses.currency(server, kingdomId).orElseGet(() -> new TradeViews.Currency(kingdomId, "", fallbackName, 0x808080, 10, 100, 1));
    }

    // ---------------------------------------------------------------- rows

    static LawViews.WantedRow row(MinecraftServer server, Wanted wanted) {
        LawState state = LawWork.state(server);
        LawRules rules = LawSettings.rules();
        List<LawViews.CrimeRow> crimes = new ArrayList<>();
        for (Crime crime : wanted.crimes()) {
            crimes.add(LawViews.CrimeRow.of(crime));
        }
        long bounty = LawWork.bountyFor(server, wanted.kingdomId(), wanted.player()).map(Contract::reward).orElse(0L);
        return new LawViews.WantedRow(wanted.player(), wanted.name(), server.getPlayerList().getPlayer(wanted.player()) != null, crimes,
                wanted.fine(rules, state.law(wanted.kingdomId()).fineMultiplierPermille()), wanted.executable(), wanted.commuted(),
                wanted.fineOverride() >= 0, bounty, state.reputation(wanted.kingdomId(), wanted.player()));
    }

    // ---------------------------------------------------------------- the law tab

    public static LawViews.TabView tabView(ServerPlayer viewer, UUID kingdomId) {
        MinecraftServer server = viewer.server;
        LawState state = LawWork.state(server);
        LawRules rules = LawSettings.rules();
        KingdomLaw law = state.law(kingdomId);
        boolean canManage = KingdomWorldApi.state(server).membership().hasPermission(viewer.getUUID(), kingdomId, Permission.MANAGE_LAW);
        List<LawViews.WantedRow> wanted = new ArrayList<>();
        if (canManage) {
            for (Wanted entry : state.wantedIn(kingdomId)) {
                if (wanted.size() >= LawViews.MAX_ROWS) {
                    break;
                }
                wanted.add(row(server, entry));
            }
        }
        Optional<LawViews.WantedRow> self = state.wanted(kingdomId, viewer.getUUID()).map(entry -> row(server, entry));
        long debt = KingdomWorldApi.state(server).economy().treasury(kingdomId).map(t -> t.debt(viewer.getUUID())).orElse(0L);
        int confiscations = (int) state.confiscationsOf(viewer.getUUID()).stream().filter(c -> c.kingdomId().equals(kingdomId)).count();
        String name = KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("");
        return new LawViews.TabView(kingdomId, currency(server, kingdomId, name), canManage, wanted, law.fineMultiplierPermille(), law.entryFee(),
                rules.maxEntryFee, law.slaveryBanned(), law.bounty(rules), law.bountyReward() < 0, rules.maxBounty,
                io.github.sekerator.kingdomworld.core.economy.Money.share(rules.murderFine, law.fineMultiplierPermille(), 1000), self,
                state.reputation(kingdomId, viewer.getUUID()), debt, confiscations);
    }

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        if (KingdomWorldApi.kingdom(player.server, kingdomId).isEmpty()) {
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new LawPackets.TabResponse(tabView(player, kingdomId)));
    }

    /**
     * A decision of the court from the law tab.
     */
    public static KingdomActions.Feedback applyTab(ServerPlayer player, LawPackets.TabChange change) {
        MinecraftServer server = player.server;
        UUID kingdomId = change.kingdomId();
        UUID actor = player.getUUID();
        LawRules rules = LawSettings.rules();
        var world = KingdomWorldApi.state(server);
        Outcome outcome = switch (change.action()) {
            case PARDON -> {
                Optional<Wanted> before = LawWork.state(server).wanted(kingdomId, change.player());
                Outcome done = LawWork.change(server, "pardon", state -> state.pardon(actor, kingdomId, change.player(), world));
                if (done.ok() && before.isPresent()) {
                    LawWork.cleared(server, before.get(), true, "pardoned");
                }
                yield done;
            }
            case SET_FINE -> {
                Optional<Wanted> before = LawWork.state(server).wanted(kingdomId, change.player());
                Outcome done = LawWork.change(server, "set a fine", state -> state.setFine(actor, kingdomId, change.player(), change.value(), rules,
                        world));
                if (done.ok() && before.isPresent()) {
                    LawWork.withdrawBounty(server, before.get());
                    LawSync.dirty(change.player());
                }
                yield done;
            }
            case MULTIPLIER -> LawWork.change(server, "set the fines", state -> state.setMultiplier(actor, kingdomId, (int) Math.min(Integer.MAX_VALUE,
                    change.value()), world));
            case ENTRY_FEE -> LawWork.change(server, "set the entry fee", state -> state.setEntryFee(actor, kingdomId, change.value(), rules, world));
            case BOUNTY -> LawWork.change(server, "set the bounty", state -> state.setBountyReward(actor, kingdomId, change.value(), rules, world));
            case SLAVERY -> LawWork.change(server, "ban slavery", state -> state.setSlaveryBanned(actor, kingdomId, change.value() == 1, world));
        };
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        return KingdomActions.Feedback.ok(KEY + "court." + change.action().name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void handleTab(ServerPlayer player, LawPackets.TabChange change) {
        KingdomActions.reply(player, applyTab(player, change));
        sendTab(player, change.kingdomId());
    }

    // ---------------------------------------------------------------- the player's window

    public static LawViews.PlayerView playerView(ServerPlayer player) {
        MinecraftServer server = player.server;
        LawState state = LawWork.state(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        long now = LawWork.now(server);
        long today = io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server);
        LawRules rules = LawSettings.rules();
        Set<UUID> kingdoms = new LinkedHashSet<>();
        UUID here = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
        if (here != null) {
            kingdoms.add(here);
        }
        state.wantedOf(player.getUUID()).forEach(wanted -> kingdoms.add(wanted.kingdomId()));
        Map<UUID, Long> debts = EconomyEvents.debtsOf(server, player.getUUID());
        kingdoms.addAll(debts.keySet());
        kingdoms.addAll(state.reputationOf(player.getUUID()).keySet());
        kingdoms.addAll(state.passesOf(player.getUUID(), now).keySet());
        KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).ifPresent(kingdoms::add);
        List<LawViews.KingdomRow> rows = new ArrayList<>();
        for (UUID kingdomId : kingdoms) {
            Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
            if (kingdom.isEmpty() || rows.size() >= LawViews.MAX_ROWS) {
                continue;
            }
            long until = state.passUntil(kingdomId, player.getUUID());
            long passMinutes = until > now ? Math.max(1, (until - now + 1199) / 1200) : 0;
            rows.add(new LawViews.KingdomRow(currency(server, kingdomId, kingdom.get().name()),
                    state.wanted(kingdomId, player.getUUID()).map(wanted -> row(server, wanted)), state.reputation(kingdomId, player.getUUID()),
                    debts.getOrDefault(kingdomId, 0L), economy.accounts().balance(player.getUUID(), kingdomId), passMinutes,
                    kingdomId.equals(here)));
        }
        List<LawViews.ConfiscationRow> confiscations = new ArrayList<>();
        for (Confiscation confiscation : state.confiscationsOf(player.getUUID())) {
            confiscations.add(confiscationRow(player, confiscation, today, rules, SAMPLE));
        }
        return new LawViews.PlayerView(rows, confiscations, state.autoPay(player.getUUID()));
    }

    static LawViews.ConfiscationRow confiscationRow(ServerPlayer player, Confiscation confiscation, long today, LawRules rules, int sample) {
        MinecraftServer server = player.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, confiscation.kingdomId());
        List<ItemStack> goods = LawSavedData.get(server).goods(confiscation.id());
        List<ItemStack> shown = new ArrayList<>();
        for (ItemStack stack : goods) {
            if (shown.size() >= sample) {
                break;
            }
            shown.add(stack.copy());
        }
        String name = kingdom.map(Kingdom::name).orElse(confiscation.kingdomName());
        return new LawViews.ConfiscationRow(confiscation.id(), name, kingdom.map(Kingdom::color).orElse(0x808080),
                currency(server, confiscation.kingdomId(), name), confiscation.worth(), LawPunish.ransomPrice(server, confiscation),
                confiscation.items(), confiscation.daysLeft(today, rules), kingdom.isEmpty(),
                kingdom.isEmpty() || LawPunish.atRansomPlace(player, confiscation.kingdomId()), shown);
    }

    public static void openPlayer(ServerPlayer player, boolean open) {
        KingdomWorldNetwork.sendToPlayer(player, new LawPackets.PlayerOpen(playerView(player), open));
    }

    public static void handlePlayer(ServerPlayer player, LawPackets.PlayerAction action) {
        MinecraftServer server = player.server;
        switch (action.action()) {
            case OPEN -> openPlayer(player, true);
            case REFRESH -> openPlayer(player, false);
            case PAY_DEBT -> {
                if (LawWork.isReadOnly(server)) {
                    KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.READ_ONLY));
                } else {
                    long paid = LawPunish.payDebt(player, action.kingdomId());
                    KingdomActions.reply(player, paid > 0 ? KingdomActions.Feedback.ok(KEY + "debt_paid",
                            MoneyTexts.amount(paid, Purses.units(server, action.kingdomId())))
                            : KingdomActions.Feedback.error(debtOf(server, action.kingdomId(), player.getUUID()) > 0 ? KingdomError.NOT_ENOUGH_MONEY
                            : KingdomError.NO_DEBT));
                }
                openPlayer(player, false);
            }
            case AUTO_PAY -> {
                if (!LawWork.isReadOnly(server)) {
                    LawWork.state(server).setAutoPay(player.getUUID(), action.value() == 1);
                }
                openPlayer(player, false);
            }
            case RANSOM_VIEW -> openRansom(player, action.value());
        }
    }

    static long debtOf(MinecraftServer server, UUID kingdomId, UUID player) {
        return KingdomWorldApi.state(server).economy().treasury(kingdomId).map(t -> t.debt(player)).orElse(0L);
    }

    // ---------------------------------------------------------------- dialogs

    private static long coins(ServerPlayer player, UUID kingdomId) {
        CurrencyUnits units = Purses.units(player.server, kingdomId);
        return CoinStacks.count(player.getInventory(), kingdomId).value(units);
    }

    /**
     * The window to give up to a soldier of a kingdom that wants the player.
     */
    public static void openSurrender(ServerPlayer player, UUID kingdomId) {
        MinecraftServer server = player.server;
        Optional<Wanted> wanted = LawWork.state(server).wanted(kingdomId, player.getUUID());
        if (wanted.isEmpty()) {
            return;
        }
        LawViews.WantedRow row = row(server, wanted.get());
        String name = KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("");
        KingdomWorldNetwork.sendToPlayer(player, new LawPackets.DialogOpen(new LawViews.DialogView(LawViews.Dialog.SURRENDER, kingdomId,
                currency(server, kingdomId, name), row.fine(), row.execution(), "", row.crimes(), coins(player, kingdomId),
                KingdomWorldApi.state(server).economy().accounts().balance(player.getUUID(), kingdomId))));
    }

    /**
     * The window to pay at the gate of a city.
     */
    static void openEntry(ServerPlayer player, CityGates.City city, long fee) {
        MinecraftServer server = player.server;
        String name = KingdomWorldApi.kingdom(server, city.kingdomId()).map(Kingdom::name).orElse("");
        KingdomWorldNetwork.sendToPlayer(player, new LawPackets.DialogOpen(new LawViews.DialogView(LawViews.Dialog.ENTRY, city.kingdomId(),
                currency(server, city.kingdomId(), name), fee, false, city.name(), List.of(), coins(player, city.kingdomId()),
                KingdomWorldApi.state(server).economy().accounts().balance(player.getUUID(), city.kingdomId()))));
    }

    /**
     * Whether a soldier of the kingdom stands near the player.
     */
    static boolean nearSoldier(ServerPlayer player, UUID kingdomId) {
        return !player.serverLevel().getEntitiesOfClass(CitizenEntity.class, new AABB(player.blockPosition()).inflate(GUARD_REACH),
                body -> body.isAlive() && kingdomId.equals(body.kingdomId()) && LawWork.isSoldier(body)).isEmpty();
    }

    /**
     * The player gives up: pays the fine for petty crimes, or accepts the death a murder costs (the goods into the record of
     * the kingdom, as when the guards execute).
     */
    public static KingdomActions.Feedback surrender(ServerPlayer player, UUID kingdomId, boolean requireGuard) {
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<Wanted> wanted = LawWork.state(server).wanted(kingdomId, player.getUUID());
        if (wanted.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_WANTED);
        }
        if (requireGuard && !nearSoldier(player, kingdomId)) {
            return KingdomActions.Feedback.error(KingdomError.GUARD_FAR);
        }
        LawGuards.forget(player.getUUID());
        if (wanted.get().executable()) {
            LawPunish.sentence(player, kingdomId);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            LawPunish.forgetSentence(player.getUUID());
            return KingdomActions.Feedback.ok(KEY + "surrendered_murder");
        }
        Optional<LawPunish.Paid> paid = LawPunish.settle(player, kingdomId);
        return paid.isPresent() ? KingdomActions.Feedback.ok(KEY + "surrendered") : KingdomActions.Feedback.error(KingdomError.NOT_WANTED);
    }

    public static void handleDialog(ServerPlayer player, LawPackets.DialogAction action) {
        KingdomActions.Feedback feedback = switch (action.dialog()) {
            case SURRENDER -> surrender(player, action.kingdomId(), true);
            case ENTRY -> {
                Optional<CityGates.City> city = LawWork.gates().cityAt(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(player.level()),
                        player.getBlockX(), player.getBlockY(), player.getBlockZ());
                CityGates.City at = city.filter(c -> c.kingdomId().equals(action.kingdomId()))
                        .orElse(new CityGates.City(action.kingdomId(), action.kingdomId(), "", true));
                long fee = LawEntry.fee(player.server, player, at);
                Outcome paid = LawEntry.pay(player, action.kingdomId(), fee, action.account());
                yield paid.ok() ? KingdomActions.Feedback.ok(KEY + "entry_paid", MoneyTexts.amount(fee, Purses.units(player.server, action.kingdomId())))
                        : KingdomActions.Feedback.error(paid.error());
            }
        };
        KingdomActions.reply(player, feedback);
    }

    // ---------------------------------------------------------------- ransom

    public static void openRansom(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        Optional<Confiscation> confiscation = LawWork.state(server).confiscation(id).filter(c -> c.owner().equals(player.getUUID()));
        if (confiscation.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.UNKNOWN_CONFISCATION));
            return;
        }
        long today = io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server);
        LawViews.ConfiscationRow row = confiscationRow(player, confiscation.get(), today, LawSettings.rules(), 0);
        List<ItemStack> goods = new ArrayList<>();
        for (ItemStack stack : LawSavedData.get(server).goods(id)) {
            goods.add(stack.copy());
        }
        KingdomWorldNetwork.sendToPlayer(player, new LawPackets.RansomOpen(new LawViews.RansomView(row, goods,
                coins(player, confiscation.get().kingdomId()),
                KingdomWorldApi.state(server).economy().accounts().balance(player.getUUID(), confiscation.get().kingdomId()))));
    }

    public static void ransom(ServerPlayer player, long id, boolean account) {
        Outcome outcome = LawPunish.ransom(player, id, account);
        KingdomActions.reply(player, outcome.ok() ? KingdomActions.Feedback.ok(KEY + "ransomed") : KingdomActions.Feedback.error(outcome.error()));
        if (!outcome.ok()) {
            openRansom(player, id);
        }
        openPlayer(player, false);
    }
}
