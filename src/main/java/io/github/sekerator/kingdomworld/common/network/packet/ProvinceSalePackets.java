package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.land.ProvinceTrade;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The provinces of the states of the world on sale, on the map of the atlas: what each costs the player (its kingdom, or a
 * charter without one) and why it is not for sale; and the purchase. The server answers only for the sender.
 */
public final class ProvinceSalePackets {
    /** Most provinces one list carries. */
    public static final int MAX_OFFERS = 4_096;

    private ProvinceSalePackets() {
    }

    /**
     * Client to server: what the provinces of the world cost me.
     */
    public record Request() {
        public static final RequestPolicy POLICY = new RequestPolicy("province_offers", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ProvinceTrade.sendOffers(sender);
            }
        }
    }

    /**
     * A province of a state of the world as the player may buy it.
     *
     * @param refusal the number of the reason it is not for sale, -1 when it is
     * @param price   its price in the currency of the seller, in words
     * @param reason  why it is not for sale, in words; empty when it is
     */
    public record Offer(int id, int refusal, Component price, Component reason) {
        public Offer {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(reason, "reason");
            if (id < 1 || id > ProvinceMap.MAX_ID) {
                throw new IllegalArgumentException("Province number out of range: " + id);
            }
        }

        public boolean forSale() {
            return refusal < 0;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(refusal);
            buf.writeComponent(price);
            buf.writeComponent(reason);
        }

        static Offer decode(FriendlyByteBuf buf) {
            return new Offer(buf.readVarInt(), buf.readVarInt(), buf.readComponent(), buf.readComponent());
        }
    }

    /**
     * Server to client: the provinces of the world and what they cost the player.
     *
     * @param charter whether the player buys a charter (it has no kingdom)
     */
    public record Offers(boolean charter, List<Offer> offers) {
        public Offers {
            offers = List.copyOf(offers);
            if (offers.size() > MAX_OFFERS) {
                throw new IllegalArgumentException("Too many provinces on sale: " + offers.size());
            }
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(charter);
            buf.writeVarInt(offers.size());
            offers.forEach(offer -> offer.encode(buf));
        }

        public static Offers decode(FriendlyByteBuf buf) {
            boolean charter = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_OFFERS) {
                throw new IllegalArgumentException("Too many provinces on sale: " + count);
            }
            List<Offer> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offers.add(Offer.decode(buf));
            }
            return new Offers(charter, offers);
        }

        public static void handle(Offers message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.sekerator.kingdomworld.client.map.ClientProvinceOffers.replace(message));
        }
    }

    /**
     * Client to server: buy this province, for my kingdom or as a charter.
     */
    public record Buy(int id) {
        public static final RequestPolicy POLICY = new RequestPolicy("province_buy", 0, 5, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        public static Buy decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            if (id < 1 || id > ProvinceMap.MAX_ID) {
                throw new IllegalArgumentException("Province number out of range: " + id);
            }
            return new Buy(id);
        }

        public static void handle(Buy message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                KingdomActions.reply(sender, ProvinceTrade.buy(sender, message.id()));
                ProvinceTrade.sendOffers(sender);
            }
        }
    }
}
