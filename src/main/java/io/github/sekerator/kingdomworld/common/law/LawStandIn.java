package io.github.sekerator.kingdomworld.common.law;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * A stand-in for a player who is not online, for operators and checks: a player object that is not in the world, with a
 * connection that leads nowhere, so that it can die by the law (the game tells a dying player about it through the
 * connection) and pay or buy back as the player would.
 */
public final class LawStandIn {

    private LawStandIn() {
    }

    public static ServerPlayer create(MinecraftServer server, ServerLevel level, GameProfile profile) {
        ServerPlayer player = new ServerPlayer(server, level, profile);
        // The listener puts itself on the player; packets for it wait in a connection that never opens.
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }
}
