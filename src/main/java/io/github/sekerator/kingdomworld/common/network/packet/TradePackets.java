package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.trade.ExchangeActions;
import io.github.sekerator.kingdomworld.common.trade.MarketActions;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.common.trade.TradeActions;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Trade on the network: the stall screen, the "Market" and "Trade" tabs of the throne screen, the exchange of a bank and
 * the caravans on the map. Requests from clients are checked by the server (rights, distance, amounts, the rules of the
 * market); answers carry a fresh view.
 */
public final class TradePackets {

    private TradePackets() {
    }

    private static void writeNullableUuid(FriendlyByteBuf buf, UUID id) {
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeUUID(id);
        }
    }

    private static UUID readNullableUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    // ---------------------------------------------------------------- the stall

    /**
     * Server to client: open the stall screen, or refresh it.
     */
    public record StallOpen(TradeViews.StallView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static StallOpen decode(FriendlyByteBuf buf) {
            return new StallOpen(TradeViews.StallView.decode(buf), buf.readBoolean());
        }

        public static void handle(StallOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleStall(message.view(), message.open()));
        }
    }

    /**
     * Client to server: buy, sell, or trade lots at a stall.
     *
     * @param item     the item to buy or sell
     * @param id       the lot to buy or take back
     * @param count    items to buy, sell or put up
     * @param price    the price of a new lot
     * @param account  whether the money comes from or goes onto the account rather than as coins
     * @param currency the currency paid with, {@code null} for the stall's own
     */
    public record StallAction(BlockPos pos, Action action, String item, long id, int count, long price, boolean account, UUID currency) {
        public static final RequestPolicy POLICY = new RequestPolicy("stall_action", 0, 20, 10_000L);

        public enum Action {
            BUY,
            SELL,
            BUY_LOT,
            LIST_LOT,
            WITHDRAW_LOT
        }

        public StallAction {
            item = item == null ? "" : item;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeUtf(item, Goods.MAX_ITEM);
            buf.writeVarLong(id);
            buf.writeVarInt(count);
            buf.writeVarLong(price);
            buf.writeBoolean(account);
            writeNullableUuid(buf, currency);
        }

        public static StallAction decode(FriendlyByteBuf buf) {
            return new StallAction(buf.readBlockPos(), KingdomView.readEnum(buf, Action.class), buf.readUtf(Goods.MAX_ITEM), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong(), buf.readBoolean(), readNullableUuid(buf));
        }

        public static void handle(StallAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "count", message.count(), 0, StallActions.MAX_COUNT)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "price", message.price(), 0, Money.MAX)) {
                return;
            }
            StallActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- the market tab

    /**
     * Client to server: show me the market of this kingdom.
     */
    public record MarketRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("market_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static MarketRequest decode(FriendlyByteBuf buf) {
            return new MarketRequest(buf.readUUID());
        }

        public static void handle(MarketRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                MarketActions.sendView(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the market.
     */
    public record MarketResponse(TradeViews.MarketView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static MarketResponse decode(FriendlyByteBuf buf) {
            return new MarketResponse(TradeViews.MarketView.decode(buf));
        }

        public static void handle(MarketResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleMarketView(message.view()));
        }
    }

    /**
     * Client to server: a decision about the market.
     *
     * @param price  the king's price, 0 for the auto price
     * @param first  how many to keep, the most to buy, the stock to export above, how many to import, or a setting
     * @param second the daily budget of an item bought
     */
    public record MarketChange(UUID kingdomId, Action action, String item, long price, long first, long second) {
        public static final RequestPolicy POLICY = new RequestPolicy("market_change", 0, 20, 10_000L);

        public enum Action {
            PUT_SELL,
            REMOVE_SELL,
            PUT_BUY,
            REMOVE_BUY,
            PUT_EXPORT,
            REMOVE_EXPORT,
            ORDER_IMPORT,
            SET_OPEN,
            SET_FOREIGN,
            SET_TAX,
            SET_FEE
        }

        public MarketChange {
            item = item == null ? "" : item;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeUtf(item, Goods.MAX_ITEM);
            buf.writeVarLong(price);
            buf.writeVarLong(first);
            buf.writeVarLong(second);
        }

        public static MarketChange decode(FriendlyByteBuf buf) {
            return new MarketChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readUtf(Goods.MAX_ITEM), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong());
        }

        public static void handle(MarketChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "price", message.price(), 0, Money.MAX)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "first", message.first(), 0, Money.MAX)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "second", message.second(), 0, Money.MAX)) {
                return;
            }
            MarketActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- the trade tab

    /**
     * Client to server: show me the deals of this kingdom.
     */
    public record TradeRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("trade_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TradeRequest decode(FriendlyByteBuf buf) {
            return new TradeRequest(buf.readUUID());
        }

        public static void handle(TradeRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                TradeActions.sendView(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the deals.
     */
    public record TradeResponse(TradeViews.TradeView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TradeResponse decode(FriendlyByteBuf buf) {
            return new TradeResponse(TradeViews.TradeView.decode(buf));
        }

        public static void handle(TradeResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleTradeView(message.view()));
        }
    }

    /**
     * Client to server: a decision about deals.
     *
     * @param partner     the kingdom a new deal is proposed to
     * @param dealId      the deal answered or called off
     * @param sells       whether the proposing kingdom sends the goods
     * @param ownCurrency whether the price is in the proposing kingdom's currency
     * @param period      days between deliveries, 0 for one delivery
     */
    public record TradeChange(UUID kingdomId, Action action, UUID partner, long dealId, String item, int count, long price, boolean sells,
                              boolean ownCurrency, int period) {
        public static final RequestPolicy POLICY = new RequestPolicy("trade_change", 0, 20, 10_000L);

        public enum Action {
            PROPOSE,
            ACCEPT,
            DECLINE,
            CANCEL
        }

        public TradeChange {
            item = item == null ? "" : item;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            writeNullableUuid(buf, partner);
            buf.writeVarLong(dealId);
            buf.writeUtf(item, Goods.MAX_ITEM);
            buf.writeVarInt(count);
            buf.writeVarLong(price);
            buf.writeBoolean(sells);
            buf.writeBoolean(ownCurrency);
            buf.writeVarInt(period);
        }

        public static TradeChange decode(FriendlyByteBuf buf) {
            return new TradeChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), readNullableUuid(buf), buf.readVarLong(),
                    buf.readUtf(Goods.MAX_ITEM), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(TradeChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "count", message.count(), 0, 1_000_000)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "price", message.price(), 0, Money.MAX)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "period", message.period(), 0, 365)) {
                return;
            }
            TradeActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- the exchange of a bank

    /**
     * Client to server: show me the exchange of this bank.
     */
    public record ExchangeRequest(BlockPos pos) {
        public static final RequestPolicy POLICY = new RequestPolicy("exchange_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public static ExchangeRequest decode(FriendlyByteBuf buf) {
            return new ExchangeRequest(buf.readBlockPos());
        }

        public static void handle(ExchangeRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ExchangeActions.open(sender, message.pos());
            }
        }
    }

    /**
     * Server to client: open the exchange screen, or refresh it.
     */
    public record ExchangeOpen(TradeViews.ExchangeView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static ExchangeOpen decode(FriendlyByteBuf buf) {
            return new ExchangeOpen(TradeViews.ExchangeView.decode(buf), buf.readBoolean());
        }

        public static void handle(ExchangeOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleExchange(message.view(), message.open()));
        }
    }

    /**
     * Client to server: exchange an amount of a currency for the bank's.
     *
     * @param fromAccount whether the amount comes from the account rather than from coins
     * @param toAccount   whether the bank pays onto the account rather than in coins
     */
    public record ExchangeAction(BlockPos pos, UUID currency, long amount, boolean fromAccount, boolean toAccount) {
        public static final RequestPolicy POLICY = new RequestPolicy("exchange_action", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(currency);
            buf.writeVarLong(amount);
            buf.writeBoolean(fromAccount);
            buf.writeBoolean(toAccount);
        }

        public static ExchangeAction decode(FriendlyByteBuf buf) {
            return new ExchangeAction(buf.readBlockPos(), buf.readUUID(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(ExchangeAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "amount", message.amount(), 1, Money.MAX)) {
                return;
            }
            ExchangeActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- caravans

    /**
     * Server to client: every caravan on the way.
     */
    public record Caravans(List<TradeViews.CaravanInfo> caravans) {
        public Caravans {
            caravans = List.copyOf(caravans);
        }

        public void encode(FriendlyByteBuf buf) {
            TradeViews.writeCaravans(buf, caravans);
        }

        public static Caravans decode(FriendlyByteBuf buf) {
            return new Caravans(TradeViews.readCaravans(buf));
        }

        public static void handle(Caravans message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCaravans(message.caravans()));
        }
    }
}
