package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.commerce.ShopActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.ShopView;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The shops of citizens on the network: the window of a shop, buying in it, selling to it and fixing its trade. The server checks
 * the distance, the keeper, the money, the room, the price the player saw and the right over citizens; a refused trade takes
 * nothing and sends a fresh view.
 */
public final class CommercePackets {
    private CommercePackets() {
    }

    /**
     * Server to client: open the window of a shop, or show it afresh.
     */
    public record ShopOpen(ShopView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static ShopOpen decode(FriendlyByteBuf buf) {
            return new ShopOpen(ShopView.decode(buf), buf.readBoolean());
        }

        public static void handle(ShopOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleShop(message.view(), message.open()));
        }
    }

    /**
     * Client to server: show me another page of the shop.
     */
    public record ShopPage(long shopId, int page) {
        public static final RequestPolicy POLICY = new RequestPolicy("shop_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(shopId);
            buf.writeVarInt(page);
        }

        public static ShopPage decode(FriendlyByteBuf buf) {
            return new ShopPage(buf.readVarLong(), buf.readVarInt());
        }

        public static void handle(ShopPage message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)
                    && ServerRequestGuard.requireRange(sender, POLICY, "page", message.page(), 0, 1_000_000)) {
                ShopActions.page(sender, message.shopId(), message.page());
            }
        }
    }

    /**
     * Client to server: buy items of a shop, or sell it items of the inventory.
     *
     * @param item    the key of the goods to buy, or the item to sell
     * @param seen    the price of one the player saw: with the tax when buying, what the shop pays when selling
     * @param account whether to pay from the account, else with coins; selling always pays onto the account
     */
    public record ShopTrade(long shopId, Action action, String item, int count, long seen, boolean account) {
        public static final RequestPolicy POLICY = new RequestPolicy("shop_trade", 0, 20, 10_000L);

        /**
         * Buying or selling.
         */
        public enum Action {
            BUY,
            SELL
        }

        public ShopTrade {
            item = item == null ? "" : item;
            action = action == null ? Action.BUY : action;
        }

        public ShopTrade(long shopId, String item, int count, long seen, boolean account) {
            this(shopId, Action.BUY, item, count, seen, account);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(shopId);
            buf.writeEnum(action);
            buf.writeUtf(item, 256);
            buf.writeVarInt(count);
            buf.writeVarLong(seen);
            buf.writeBoolean(account);
        }

        public static ShopTrade decode(FriendlyByteBuf buf) {
            return new ShopTrade(buf.readVarLong(), io.github.sekerator.kingdomworld.common.network.data.KingdomView.readEnum(buf, Action.class),
                    buf.readUtf(256), buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
        }

        public static void handle(ShopTrade message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "item", message.item(), 256)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "count", message.count(), 1, 64 * 36)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "seen", message.seen(), 0, Money.MAX)) {
                return;
            }
            if (message.action() == Action.SELL) {
                ShopActions.sell(sender, message);
            } else {
                ShopActions.buy(sender, message);
            }
        }
    }

    /**
     * Client to server: fix the trade of the place of a shop, or let it choose again with an empty trade.
     */
    public record ShopPin(long shopId, String trade) {
        public static final RequestPolicy POLICY = new RequestPolicy("shop_pin", 0, 10, 10_000L);

        public ShopPin {
            trade = trade == null ? "" : trade;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(shopId);
            buf.writeUtf(trade, 64);
        }

        public static ShopPin decode(FriendlyByteBuf buf) {
            return new ShopPin(buf.readVarLong(), buf.readUtf(64));
        }

        public static void handle(ShopPin message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY) || !ServerRequestGuard.requireLength(sender, POLICY, "trade", message.trade(), 64)) {
                return;
            }
            ShopActions.pin(sender, message.shopId(), message.trade());
        }
    }
}
