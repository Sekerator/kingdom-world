package io.github.sekerator.kingdomworld.common.economy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.RetiredCurrency;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wires the economy into the game: once a second the server checks whether a new game day began and holds the payday
 * of every kingdom (taxes, then wages), tells the subjects what their taxes took, sends every client the list of
 * currencies when it changed, reminds players of their debts when they join, and forgets the worth of items when data
 * packs or tags reload. Nothing happens while the kingdom data is read-only.
 */
public final class EconomyEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_TICKS = 20;
    private static final String TAX = "kingdomworld.tax.";
    private static List<CurrencyInfo> lastSent;

    private EconomyEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EconomyEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EconomyEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(EconomyEvents::onReload);
        MinecraftForge.EVENT_BUS.addListener(EconomyEvents::onTags);
        MinecraftForge.EVENT_BUS.addListener(EconomyEvents::onServerStarted);
    }

    /**
     * The table of the worth of items is built with the server, not in the first tick that needs it: with many mods it takes
     * the time of several ticks.
     */
    private static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        try {
            ItemPrices.table(event.getServer());
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld could not build the table of the worth of items at the start", e);
        }
    }

    public static void reset() {
        lastSent = null;
        EconomySettings.reset();
        MinterBehavior.reset();
        ItemPrices.invalidate();
    }

    /**
     * Sends the currencies again on the next check, for example after a currency was renamed.
     */
    public static void currenciesChanged() {
        lastSent = null;
    }

    private static void onReload(AddReloadListenerEvent event) {
        event.addListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
            ItemPrices.invalidate();
            io.github.sekerator.kingdomworld.common.trade.MarketActions.invalidate();
        });
    }

    private static void onTags(TagsUpdatedEvent event) {
        ItemPrices.invalidate();
        io.github.sekerator.kingdomworld.common.trade.MarketActions.invalidate();
        // After /reload the table is built again at once, inside the reload, not in the first tick that asks for a price.
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && server != null && server.getTickCount() > 0) {
            ItemPrices.table(server);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        EconomySettings.refresh();
        EconomyRules rules = EconomySettings.rules();
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.economy().setDefaults(rules.units(), rules.defaultPollTax());
        if (!KingdomWorldApi.isReadOnly(server)) {
            long day = Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
            for (Kingdom kingdom : state.kingdoms().all()) {
                Optional<Treasury> treasury = state.economy().treasury(kingdom.id());
                if (treasury.isPresent() && treasury.get().lastPayday() < day) {
                    payday(server, kingdom, day);
                }
            }
        }
        syncCurrencies(server);
    }

    /**
     * Holds the payday of a kingdom now, if it was not held today, and tells everybody concerned.
     */
    public static Economy.Payday payday(MinecraftServer server, Kingdom kingdom, long day) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Economy.Payday payday = state.payday(kingdom.id(), day, EconomySettings.rules());
        if (payday == Economy.Payday.NONE) {
            return payday;
        }
        CurrencyUnits units = state.economy().units(kingdom.id()).orElse(CurrencyUnits.DEFAULT);
        for (Economy.TaxPaid tax : payday.taxes()) {
            if (tax.tax() > 0 && tax.paid() >= tax.tax() && tax.debt() == 0) {
                io.github.sekerator.kingdomworld.common.law.LawWork.taxPaid(server, kingdom.id(), tax.player());
            }
            Component message;
            if (tax.paid() == 0) {
                message = Component.translatable(TAX + "unpaid", KingdomActions.kingdomName(kingdom), MoneyTexts.amount(tax.debt(), units));
            } else if (tax.debt() > 0) {
                message = Component.translatable(TAX + "charged_debt", KingdomActions.kingdomName(kingdom), MoneyTexts.amount(tax.paid(), units),
                        MoneyTexts.amount(tax.debt(), units));
            } else {
                message = Component.translatable(TAX + "charged", KingdomActions.kingdomName(kingdom), MoneyTexts.amount(tax.paid(), units));
            }
            KingdomActions.tell(server, tax.player(), message);
        }
        if (payday.summary().underpaid() > 0) {
            KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_TREASURY, Component.translatable("kingdomworld.wages.short",
                    KingdomActions.kingdomName(kingdom), MoneyTexts.amount(payday.summary().paid(), units),
                    MoneyTexts.amount(payday.summary().due(), units)).withStyle(ChatFormatting.GOLD));
        }
        LOGGER.debug("Payday of {} on day {}: {} tax payer(s), wages {} of {}", kingdom.name(), day, payday.taxes().size(),
                payday.summary().paid(), payday.summary().due());
        return payday;
    }

    // ---------------------------------------------------------------- currencies

    /**
     * Every current and old currency, as clients see them.
     */
    public static List<CurrencyInfo> currencies(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        List<CurrencyInfo> list = new ArrayList<>();
        for (Kingdom kingdom : state.kingdoms().all()) {
            state.economy().treasury(kingdom.id()).ifPresent(treasury -> list.add(CurrencyInfo.of(kingdom, treasury,
                    state.economy().rate(kingdom.id()))));
        }
        for (RetiredCurrency old : state.economy().retired().values()) {
            if (list.size() >= CurrencyInfo.MAX_LIST) {
                break;
            }
            list.add(CurrencyInfo.of(old));
        }
        return list.size() > CurrencyInfo.MAX_LIST ? list.subList(0, CurrencyInfo.MAX_LIST) : list;
    }

    private static void syncCurrencies(MinecraftServer server) {
        List<CurrencyInfo> now = currencies(server);
        if (!now.equals(lastSent)) {
            lastSent = List.copyOf(now);
            KingdomWorldNetwork.sendToAll(new EconomyPackets.Currencies(now));
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = player.server;
        KingdomWorldNetwork.sendToPlayer(player, new EconomyPackets.Currencies(currencies(server)));
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Treasury treasury : state.economy().treasuries()) {
            long debt = treasury.debt(player.getUUID());
            if (debt <= 0) {
                continue;
            }
            state.kingdoms().get(treasury.kingdomId()).ifPresent(kingdom -> player.sendSystemMessage(Component.translatable(TAX + "reminder",
                    KingdomActions.kingdomName(kingdom), MoneyTexts.amount(debt, treasury.units()),
                    KingdomActions.commandButton("/kingdom treasury deposit", ChatFormatting.GREEN, ClickEvent.Action.SUGGEST_COMMAND))));
        }
    }

    /**
     * Debts a player has to every treasury, for {@code /kingdom balance}.
     */
    public static Map<UUID, Long> debtsOf(MinecraftServer server, UUID player) {
        Map<UUID, Long> debts = new java.util.LinkedHashMap<>();
        for (Treasury treasury : KingdomWorldApi.state(server).economy().treasuries()) {
            long debt = treasury.debt(player);
            if (debt > 0) {
                debts.put(treasury.kingdomId(), debt);
            }
        }
        return debts;
    }
}
