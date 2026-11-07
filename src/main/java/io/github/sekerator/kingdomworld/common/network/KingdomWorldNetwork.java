package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.BorderCrossingPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
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
    public static final String PROTOCOL_VERSION = "13";
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
        add(id++, ConstructionPackets.Request.class, NetworkDirection.PLAY_TO_SERVER, ConstructionPackets.Request::encode,
                ConstructionPackets.Request::decode, ConstructionPackets.Request::handle);
        add(id++, ConstructionPackets.Response.class, NetworkDirection.PLAY_TO_CLIENT, ConstructionPackets.Response::encode,
                ConstructionPackets.Response::decode, ConstructionPackets.Response::handle);
        add(id++, ConstructionPackets.Change.class, NetworkDirection.PLAY_TO_SERVER, ConstructionPackets.Change::encode,
                ConstructionPackets.Change::decode, ConstructionPackets.Change::handle);
        add(id++, ConstructionPackets.TakeBlueprint.class, NetworkDirection.PLAY_TO_SERVER, ConstructionPackets.TakeBlueprint::encode,
                ConstructionPackets.TakeBlueprint::decode, ConstructionPackets.TakeBlueprint::handle);
        add(id++, ConstructionPackets.TemplateRequest.class, NetworkDirection.PLAY_TO_SERVER, ConstructionPackets.TemplateRequest::encode,
                ConstructionPackets.TemplateRequest::decode, ConstructionPackets.TemplateRequest::handle);
        add(id++, ConstructionPackets.TemplateData.class, NetworkDirection.PLAY_TO_CLIENT, ConstructionPackets.TemplateData::encode,
                ConstructionPackets.TemplateData::decode, ConstructionPackets.TemplateData::handle);
        add(id++, ConstructionPackets.Place.class, NetworkDirection.PLAY_TO_SERVER, ConstructionPackets.Place::encode,
                ConstructionPackets.Place::decode, ConstructionPackets.Place::handle);
        add(id++, ConstructionPackets.CheckResult.class, NetworkDirection.PLAY_TO_CLIENT, ConstructionPackets.CheckResult::encode,
                ConstructionPackets.CheckResult::decode, ConstructionPackets.CheckResult::handle);
        add(id++, ArmyPackets.TabRequest.class, NetworkDirection.PLAY_TO_SERVER, ArmyPackets.TabRequest::encode,
                ArmyPackets.TabRequest::decode, ArmyPackets.TabRequest::handle);
        add(id++, ArmyPackets.TabResponse.class, NetworkDirection.PLAY_TO_CLIENT, ArmyPackets.TabResponse::encode,
                ArmyPackets.TabResponse::decode, ArmyPackets.TabResponse::handle);
        add(id++, ArmyPackets.TabChange.class, NetworkDirection.PLAY_TO_SERVER, ArmyPackets.TabChange::encode,
                ArmyPackets.TabChange::decode, ArmyPackets.TabChange::handle);
        add(id++, ArmyPackets.BarracksOpen.class, NetworkDirection.PLAY_TO_CLIENT, ArmyPackets.BarracksOpen::encode,
                ArmyPackets.BarracksOpen::decode, ArmyPackets.BarracksOpen::handle);
        add(id++, ArmyPackets.BarracksAction.class, NetworkDirection.PLAY_TO_SERVER, ArmyPackets.BarracksAction::encode,
                ArmyPackets.BarracksAction::decode, ArmyPackets.BarracksAction::handle);
        add(id++, MagicPackets.TabRequest.class, NetworkDirection.PLAY_TO_SERVER, MagicPackets.TabRequest::encode,
                MagicPackets.TabRequest::decode, MagicPackets.TabRequest::handle);
        add(id++, MagicPackets.TabResponse.class, NetworkDirection.PLAY_TO_CLIENT, MagicPackets.TabResponse::encode,
                MagicPackets.TabResponse::decode, MagicPackets.TabResponse::handle);
        add(id++, MagicPackets.TabChange.class, NetworkDirection.PLAY_TO_SERVER, MagicPackets.TabChange::encode,
                MagicPackets.TabChange::decode, MagicPackets.TabChange::handle);
        add(id++, MagicPackets.FocusOpen.class, NetworkDirection.PLAY_TO_CLIENT, MagicPackets.FocusOpen::encode,
                MagicPackets.FocusOpen::decode, MagicPackets.FocusOpen::handle);
        add(id++, MagicPackets.FocusAction.class, NetworkDirection.PLAY_TO_SERVER, MagicPackets.FocusAction::encode,
                MagicPackets.FocusAction::decode, MagicPackets.FocusAction::handle);
        add(id++, MagicPackets.GateOpen.class, NetworkDirection.PLAY_TO_CLIENT, MagicPackets.GateOpen::encode,
                MagicPackets.GateOpen::decode, MagicPackets.GateOpen::handle);
        add(id++, MagicPackets.GateTravel.class, NetworkDirection.PLAY_TO_SERVER, MagicPackets.GateTravel::encode,
                MagicPackets.GateTravel::decode, MagicPackets.GateTravel::handle);
        add(id++, BuildingPackets.Change.class, NetworkDirection.PLAY_TO_SERVER, BuildingPackets.Change::encode,
                BuildingPackets.Change::decode, BuildingPackets.Change::handle);
        add(id++, BuildingPackets.BlueprintChange.class, NetworkDirection.PLAY_TO_SERVER, BuildingPackets.BlueprintChange::encode,
                BuildingPackets.BlueprintChange::decode, BuildingPackets.BlueprintChange::handle);
        add(id++, BuildingPackets.Capture.class, NetworkDirection.PLAY_TO_SERVER, BuildingPackets.Capture::encode,
                BuildingPackets.Capture::decode, BuildingPackets.Capture::handle);
        add(id++, CompanionPackets.MarketRequest.class, NetworkDirection.PLAY_TO_SERVER, CompanionPackets.MarketRequest::encode,
                CompanionPackets.MarketRequest::decode, CompanionPackets.MarketRequest::handle);
        add(id++, CompanionPackets.MarketOpen.class, NetworkDirection.PLAY_TO_CLIENT, CompanionPackets.MarketOpen::encode,
                CompanionPackets.MarketOpen::decode, CompanionPackets.MarketOpen::handle);
        add(id++, CompanionPackets.MarketAction.class, NetworkDirection.PLAY_TO_SERVER, CompanionPackets.MarketAction::encode,
                CompanionPackets.MarketAction::decode, CompanionPackets.MarketAction::handle);
        add(id++, CompanionPackets.CompanionUpdate.class, NetworkDirection.PLAY_TO_CLIENT, CompanionPackets.CompanionUpdate::encode,
                CompanionPackets.CompanionUpdate::decode, CompanionPackets.CompanionUpdate::handle);
        add(id++, CompanionPackets.CompanionAction.class, NetworkDirection.PLAY_TO_SERVER, CompanionPackets.CompanionAction::encode,
                CompanionPackets.CompanionAction::decode, CompanionPackets.CompanionAction::handle);
        add(id++, CompanionPackets.PartyRequest.class, NetworkDirection.PLAY_TO_SERVER, CompanionPackets.PartyRequest::encode,
                CompanionPackets.PartyRequest::decode, CompanionPackets.PartyRequest::handle);
        add(id++, CompanionPackets.PartyOpen.class, NetworkDirection.PLAY_TO_CLIENT, CompanionPackets.PartyOpen::encode,
                CompanionPackets.PartyOpen::decode, CompanionPackets.PartyOpen::handle);
        add(id++, CompanionPackets.PartyAction.class, NetworkDirection.PLAY_TO_SERVER, CompanionPackets.PartyAction::encode,
                CompanionPackets.PartyAction::decode, CompanionPackets.PartyAction::handle);
        add(id++, CompanionPackets.PartyHud.class, NetworkDirection.PLAY_TO_CLIENT, CompanionPackets.PartyHud::encode,
                CompanionPackets.PartyHud::decode, CompanionPackets.PartyHud::handle);
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
