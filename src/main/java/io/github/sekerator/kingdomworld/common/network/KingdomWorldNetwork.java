package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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
    public static final String PROTOCOL_VERSION = "1";
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
        CHANNEL.messageBuilder(KingdomListRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KingdomListRequestPacket::encode)
                .decoder(KingdomListRequestPacket::decode)
                .consumerMainThread(KingdomListRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(KingdomListResponsePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KingdomListResponsePacket::encode)
                .decoder(KingdomListResponsePacket::decode)
                .consumerMainThread(KingdomListResponsePacket::handle)
                .add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
