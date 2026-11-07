package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deals between kingdoms from the throne screen's "Trade" tab: propose, accept, decline and call off, for members with
 * the right to make deals; every member sees the deals and the caravans. The same calls with no player serve operators and
 * kingdoms without a king.
 */
public final class TradeActions {
    private static final String KEY = "kingdomworld.trade.";

    private TradeActions() {
    }

    // ---------------------------------------------------------------- the view

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new TradePackets.TradeResponse(view)));
    }

    /**
     * The deals of a kingdom for one of its members; empty for anybody else.
     */
    public static Optional<TradeViews.TradeView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = state.kingdoms().get(kingdomId);
        if (kingdom.isEmpty() || state.economy().treasury(kingdomId).isEmpty() || !state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        TradeRules rules = TradeSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        List<TradeViews.Partner> partners = new ArrayList<>();
        for (Kingdom other : state.kingdoms().all()) {
            if (!other.id().equals(kingdomId) && partners.size() < TradeViews.MAX_ROWS) {
                Purses.currency(server, other.id()).ifPresent(currency -> partners.add(new TradeViews.Partner(other.id(), other.name(),
                        other.color(), currency)));
            }
        }
        boolean canManage = state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_TRADE);
        List<TradeViews.DealRow> deals = new ArrayList<>();
        for (Deal deal : state.trade().dealsOf(kingdomId)) {
            if (deals.size() >= TradeViews.MAX_ROWS) {
                break;
            }
            UUID partner = deal.partnerOf(kingdomId);
            Optional<Kingdom> other = state.kingdoms().get(partner);
            Item item = GoodsStacks.item(deal.item());
            boolean incoming = deal.status() == Deal.Status.PROPOSED && deal.answerer().equals(kingdomId);
            boolean cancel = !deal.status().closed() && (deal.status() == Deal.Status.ACTIVE || deal.proposer().equals(kingdomId));
            long next = deal.status() == Deal.Status.PROPOSED ? deal.expiresTime() - now : deal.nextTime() - now;
            deals.add(new TradeViews.DealRow(deal.id(), partner, other.map(Kingdom::name).orElse("?"), other.map(Kingdom::color).orElse(0x808080),
                    deal.seller().equals(kingdomId), item == null ? ItemStack.EMPTY : new ItemStack(item), deal.count(), deal.price(),
                    deal.currency().equals(kingdomId), deal.periodDays(), deal.status(), incoming, cancel, Math.max(0, next), deal.deliveries()));
        }
        List<TradeViews.CaravanRow> caravans = new ArrayList<>();
        for (Caravan caravan : state.trade().caravans()) {
            if (!(caravan.seller().equals(kingdomId) || caravan.buyer().equals(kingdomId)) || caravans.size() >= TradeViews.MAX_ROWS) {
                continue;
            }
            boolean outgoing = caravan.seller().equals(kingdomId);
            UUID partner = outgoing ? caravan.buyer() : caravan.seller();
            Optional<Kingdom> other = state.kingdoms().get(partner);
            Goods first = caravan.cargo().get(0);
            Item item = GoodsStacks.item(first.item());
            caravans.add(new TradeViews.CaravanRow(caravan.id(), outgoing, other.map(Kingdom::name).orElse("?"),
                    other.map(Kingdom::color).orElse(0x808080), item == null ? ItemStack.EMPTY : new ItemStack(item), caravan.items(),
                    caravan.payment(), caravan.currency().equals(kingdomId), (int) Math.round(caravan.progress(now) * 100),
                    Math.max(0, caravan.arriveTime() - now), caravan.state()));
        }
        return Optional.of(new TradeViews.TradeView(kingdomId, canManage, Purses.currency(server, kingdomId).orElseThrow(), partners, deals,
                caravans, Deal.MAX_COUNT, rules.maxPeriodDays()));
    }

    // ---------------------------------------------------------------- changes

    /**
     * Handles a decision from the trade tab and sends the fresh view.
     */
    public static void handle(ServerPlayer player, TradePackets.TradeChange change) {
        MinecraftServer server = player.server;
        UUID kingdom = change.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdom)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.reply(player, apply(server, player.getUUID(), kingdom, change));
        sendView(player, kingdom);
    }

    /**
     * Applies a decision about the deals of a kingdom; the API every kingdom uses, with a player or without one.
     *
     * @param actor the player, who needs the right to make deals; {@code null} for an operator or a kingdom without a king
     */
    public static KingdomActions.Feedback apply(MinecraftServer server, UUID actor, UUID kingdom, TradePackets.TradeChange change) {
        return switch (change.action()) {
            case PROPOSE -> propose(server, actor, kingdom, change);
            case ACCEPT -> {
                Outcome outcome = TradeWork.accept(server, actor, kingdom, change.dealId());
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "accept_done") : KingdomActions.Feedback.error(outcome.error());
            }
            case DECLINE -> {
                Optional<Deal> deal = KingdomWorldApi.state(server).trade().deal(change.dealId());
                Outcome outcome = KingdomWorldApi.change(server, "decline a deal", state -> state.declineDeal(actor, kingdom, change.dealId()));
                if (!outcome.ok()) {
                    yield KingdomActions.Feedback.error(outcome.error());
                }
                deal.ifPresent(found -> notify(server, found.proposer(), Component.translatable(KEY + "declined", name(server, kingdom))
                        .withStyle(ChatFormatting.GOLD)));
                yield KingdomActions.Feedback.ok(KEY + "decline_done");
            }
            case CANCEL -> {
                Optional<Deal> deal = KingdomWorldApi.state(server).trade().deal(change.dealId());
                Outcome outcome = KingdomWorldApi.change(server, "call off a deal", state -> state.cancelDeal(actor, kingdom, change.dealId()));
                if (!outcome.ok()) {
                    yield KingdomActions.Feedback.error(outcome.error());
                }
                deal.ifPresent(found -> notify(server, found.partnerOf(kingdom), Component.translatable(KEY + "cancelled", name(server, kingdom))
                        .withStyle(ChatFormatting.GOLD)));
                yield KingdomActions.Feedback.ok(KEY + "cancel_done");
            }
        };
    }

    private static KingdomActions.Feedback propose(MinecraftServer server, UUID actor, UUID kingdom, TradePackets.TradeChange change) {
        Item item = GoodsStacks.item(change.item());
        if (item == null || new ItemStack(item).is(ModRegistries.COIN.get())) {
            return KingdomActions.Feedback.error(KingdomError.BAD_ITEM);
        }
        if (change.partner() == null) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_KINGDOM);
        }
        long now = KingdomWorldApi.gameTime(server);
        KingdomWorldState.Proposal[] result = new KingdomWorldState.Proposal[1];
        Outcome outcome = KingdomWorldApi.change(server, "propose a deal", state -> {
            result[0] = state.proposeDeal(actor, kingdom, change.partner(), change.sells(), change.item(), change.count(), change.price(),
                    change.ownCurrency(), change.period(), now, TradeSettings.rules());
            return result[0].outcome();
        });
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Deal deal = result[0].deal().orElseThrow();
        Component goods = Component.translatable(KEY + "goods", new ItemStack(item).getHoverName(), TradeTexts.items(deal.count()));
        Component price = MoneyTexts.amount(deal.price(), Purses.units(server, deal.currency()));
        notify(server, change.partner(), Component.translatable(deal.seller().equals(kingdom) ? KEY + "offer_sell" : KEY + "offer_buy",
                name(server, kingdom), goods, price).withStyle(ChatFormatting.AQUA));
        return KingdomActions.Feedback.ok(KEY + "proposed", name(server, change.partner()));
    }

    private static Component name(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.kingdom(server, kingdom).<Component>map(KingdomActions::kingdomName).orElseGet(() -> Component.literal("?"));
    }

    private static void notify(MinecraftServer server, UUID kingdom, Component message) {
        KingdomWorldApi.kingdom(server, kingdom).ifPresent(found -> KingdomActions.notifyMembers(server, found, Permission.MANAGE_TRADE, message));
    }
}
