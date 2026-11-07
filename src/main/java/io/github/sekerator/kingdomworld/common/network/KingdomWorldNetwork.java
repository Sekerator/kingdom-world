package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.BorderCrossingPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.OrderPackets;
import io.github.sekerator.kingdomworld.common.network.packet.OpenFoundingScreenPacket;
import io.github.sekerator.kingdomworld.common.network.packet.StoragePackets;
import io.github.sekerator.kingdomworld.common.network.packet.TerritoryDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostViewPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The mod's network channel.
 *
 * <p>Client and server must run the same {@link #PROTOCOL_VERSION}; a mismatch, or a missing mod on either
 * side, refuses the connection. Increase the version whenever a packet layout or meaning changes.
 *
 * <p>Every packet has a fixed direction. Serverbound packets are untrusted input: their handlers validate
 * them through {@link ServerRequestGuard} before acting.
 */
public final class KingdomWorldNetwork {
    public static final String PROTOCOL_VERSION = "9";
    public static final ResourceLocation CHANNEL_NAME = KingdomWorld.id("main");

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private KingdomWorldNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        add(id++, KingdomListRequestPacket.class, NetworkDirection.PLAY_TO_SERVER, KingdomListRequestPacket::encode, KingdomListRequestPacket::decode, KingdomListRequestPacket::handle);
        add(id++, KingdomListResponsePacket.class, NetworkDirection.PLAY_TO_CLIENT, KingdomListResponsePacket::encode, KingdomListResponsePacket::decode, KingdomListResponsePacket::handle);
        add(id++, FoundKingdomPacket.class, NetworkDirection.PLAY_TO_SERVER, FoundKingdomPacket::encode, FoundKingdomPacket::decode, FoundKingdomPacket::handle);
        add(id++, KingdomActionPacket.class, NetworkDirection.PLAY_TO_SERVER, KingdomActionPacket::encode, KingdomActionPacket::decode, KingdomActionPacket::handle);
        add(id++, ClaimChunkPacket.class, NetworkDirection.PLAY_TO_SERVER, ClaimChunkPacket::encode, ClaimChunkPacket::decode, ClaimChunkPacket::handle);
        add(id++, MapAreaRequestPacket.class, NetworkDirection.PLAY_TO_SERVER, MapAreaRequestPacket::encode, MapAreaRequestPacket::decode, MapAreaRequestPacket::handle);
        add(id++, OpenFoundingScreenPacket.class, NetworkDirection.PLAY_TO_CLIENT, OpenFoundingScreenPacket::encode, OpenFoundingScreenPacket::decode, OpenFoundingScreenPacket::handle);
        add(id++, KingdomViewPacket.class, NetworkDirection.PLAY_TO_CLIENT, KingdomViewPacket::encode, KingdomViewPacket::decode, KingdomViewPacket::handle);
        add(id++, ActionResultPacket.class, NetworkDirection.PLAY_TO_CLIENT, ActionResultPacket::encode, ActionResultPacket::decode, ActionResultPacket::handle);
        add(id++, MapAreaPacket.class, NetworkDirection.PLAY_TO_CLIENT, MapAreaPacket::encode, MapAreaPacket::decode, MapAreaPacket::handle);
        add(id++, TerritoryDeltaPacket.class, NetworkDirection.PLAY_TO_CLIENT, TerritoryDeltaPacket::encode, TerritoryDeltaPacket::decode, TerritoryDeltaPacket::handle);
        add(id++, BorderCrossingPacket.class, NetworkDirection.PLAY_TO_CLIENT, BorderCrossingPacket::encode, BorderCrossingPacket::decode, BorderCrossingPacket::handle);
        add(id++, CitizenRequestPacket.class, NetworkDirection.PLAY_TO_SERVER, CitizenRequestPacket::encode, CitizenRequestPacket::decode, CitizenRequestPacket::handle);
        add(id++, CitizenActionPacket.class, NetworkDirection.PLAY_TO_SERVER, CitizenActionPacket::encode, CitizenActionPacket::decode, CitizenActionPacket::handle);
        add(id++, CitizenViewPacket.class, NetworkDirection.PLAY_TO_CLIENT, CitizenViewPacket::encode, CitizenViewPacket::decode, CitizenViewPacket::handle);
        add(id++, WorkPostViewPacket.class, NetworkDirection.PLAY_TO_CLIENT, WorkPostViewPacket::encode, WorkPostViewPacket::decode, WorkPostViewPacket::handle);
        add(id++, WorkPostActionPacket.class, NetworkDirection.PLAY_TO_SERVER, WorkPostActionPacket::encode, WorkPostActionPacket::decode, WorkPostActionPacket::handle);
        add(id++, StoragePackets.Request.class, NetworkDirection.PLAY_TO_SERVER, StoragePackets.Request::encode, StoragePackets.Request::decode, StoragePackets.Request::handle);
        add(id++, StoragePackets.Response.class, NetworkDirection.PLAY_TO_CLIENT, StoragePackets.Response::encode, StoragePackets.Response::decode, StoragePackets.Response::handle);
        add(id++, OrderPackets.Request.class, NetworkDirection.PLAY_TO_SERVER, OrderPackets.Request::encode, OrderPackets.Request::decode, OrderPackets.Request::handle);
        add(id++, OrderPackets.Response.class, NetworkDirection.PLAY_TO_CLIENT, OrderPackets.Response::encode, OrderPackets.Response::decode, OrderPackets.Response::handle);
        add(id++, OrderPackets.CatalogRequest.class, NetworkDirection.PLAY_TO_SERVER, OrderPackets.CatalogRequest::encode, OrderPackets.CatalogRequest::decode, OrderPackets.CatalogRequest::handle);
        add(id++, OrderPackets.Catalog.class, NetworkDirection.PLAY_TO_CLIENT, OrderPackets.Catalog::encode, OrderPackets.Catalog::decode, OrderPackets.Catalog::handle);
        add(id++, OrderPackets.Change.class, NetworkDirection.PLAY_TO_SERVER, OrderPackets.Change::encode, OrderPackets.Change::decode, OrderPackets.Change::handle);
        add(id++, EconomyPackets.TreasuryRequest.class, NetworkDirection.PLAY_TO_SERVER, EconomyPackets.TreasuryRequest::encode,
                EconomyPackets.TreasuryRequest::decode, EconomyPackets.TreasuryRequest::handle);
        add(id++, EconomyPackets.TreasuryResponse.class, NetworkDirection.PLAY_TO_CLIENT, EconomyPackets.TreasuryResponse::encode,
                EconomyPackets.TreasuryResponse::decode, EconomyPackets.TreasuryResponse::handle);
        add(id++, EconomyPackets.TreasuryChange.class, NetworkDirection.PLAY_TO_SERVER, EconomyPackets.TreasuryChange::encode,
                EconomyPackets.TreasuryChange::decode, EconomyPackets.TreasuryChange::handle);
        add(id++, EconomyPackets.BankOpen.class, NetworkDirection.PLAY_TO_CLIENT, EconomyPackets.BankOpen::encode,
                EconomyPackets.BankOpen::decode, EconomyPackets.BankOpen::handle);
        add(id++, EconomyPackets.BankChange.class, NetworkDirection.PLAY_TO_SERVER, EconomyPackets.BankChange::encode,
                EconomyPackets.BankChange::decode, EconomyPackets.BankChange::handle);
        add(id++, EconomyPackets.Currencies.class, NetworkDirection.PLAY_TO_CLIENT, EconomyPackets.Currencies::encode,
                EconomyPackets.Currencies::decode, EconomyPackets.Currencies::handle);
        add(id++, TradePackets.StallOpen.class, NetworkDirection.PLAY_TO_CLIENT, TradePackets.StallOpen::encode,
                TradePackets.StallOpen::decode, TradePackets.StallOpen::handle);
        add(id++, TradePackets.StallAction.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.StallAction::encode,
                TradePackets.StallAction::decode, TradePackets.StallAction::handle);
        add(id++, TradePackets.MarketRequest.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.MarketRequest::encode,
                TradePackets.MarketRequest::decode, TradePackets.MarketRequest::handle);
        add(id++, TradePackets.MarketResponse.class, NetworkDirection.PLAY_TO_CLIENT, TradePackets.MarketResponse::encode,
                TradePackets.MarketResponse::decode, TradePackets.MarketResponse::handle);
        add(id++, TradePackets.MarketChange.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.MarketChange::encode,
                TradePackets.MarketChange::decode, TradePackets.MarketChange::handle);
        add(id++, TradePackets.TradeRequest.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.TradeRequest::encode,
                TradePackets.TradeRequest::decode, TradePackets.TradeRequest::handle);
        add(id++, TradePackets.TradeResponse.class, NetworkDirection.PLAY_TO_CLIENT, TradePackets.TradeResponse::encode,
                TradePackets.TradeResponse::decode, TradePackets.TradeResponse::handle);
        add(id++, TradePackets.TradeChange.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.TradeChange::encode,
                TradePackets.TradeChange::decode, TradePackets.TradeChange::handle);
        add(id++, TradePackets.ExchangeRequest.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.ExchangeRequest::encode,
                TradePackets.ExchangeRequest::decode, TradePackets.ExchangeRequest::handle);
        add(id++, TradePackets.ExchangeOpen.class, NetworkDirection.PLAY_TO_CLIENT, TradePackets.ExchangeOpen::encode,
                TradePackets.ExchangeOpen::decode, TradePackets.ExchangeOpen::handle);
        add(id++, TradePackets.ExchangeAction.class, NetworkDirection.PLAY_TO_SERVER, TradePackets.ExchangeAction::encode,
                TradePackets.ExchangeAction::decode, TradePackets.ExchangeAction::handle);
        add(id++, TradePackets.Caravans.class, NetworkDirection.PLAY_TO_CLIENT, TradePackets.Caravans::encode,
                TradePackets.Caravans::decode, TradePackets.Caravans::handle);
        add(id++, TerritorySnapshotRequestPacket.class, NetworkDirection.PLAY_TO_SERVER, TerritorySnapshotRequestPacket::encode,
                TerritorySnapshotRequestPacket::decode, TerritorySnapshotRequestPacket::handle);
        add(id++, TerritorySnapshotPacket.class, NetworkDirection.PLAY_TO_CLIENT, TerritorySnapshotPacket::encode,
                TerritorySnapshotPacket::decode, TerritorySnapshotPacket::handle);
        add(id++, GuildPackets.DeskOpen.class, NetworkDirection.PLAY_TO_CLIENT, GuildPackets.DeskOpen::encode,
                GuildPackets.DeskOpen::decode, GuildPackets.DeskOpen::handle);
        add(id++, GuildPackets.DeskAction.class, NetworkDirection.PLAY_TO_SERVER, GuildPackets.DeskAction::encode,
                GuildPackets.DeskAction::decode, GuildPackets.DeskAction::handle);
        add(id++, GuildPackets.TabRequest.class, NetworkDirection.PLAY_TO_SERVER, GuildPackets.TabRequest::encode,
                GuildPackets.TabRequest::decode, GuildPackets.TabRequest::handle);
        add(id++, GuildPackets.TabResponse.class, NetworkDirection.PLAY_TO_CLIENT, GuildPackets.TabResponse::encode,
                GuildPackets.TabResponse::decode, GuildPackets.TabResponse::handle);
        add(id++, GuildPackets.TabChange.class, NetworkDirection.PLAY_TO_SERVER, GuildPackets.TabChange::encode,
                GuildPackets.TabChange::decode, GuildPackets.TabChange::handle);
    }

    private static <T> void add(int id, Class<T> type, NetworkDirection direction, BiConsumer<T, FriendlyByteBuf> encoder,
                                Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, id, direction)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        // Players created for tests have no connection.
        if (player.connection != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }
}
