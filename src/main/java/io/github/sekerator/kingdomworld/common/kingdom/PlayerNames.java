package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Player names for display. Offline players are looked up in the server's profile cache, which never asks
 * the network for ids.
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    public static String name(MinecraftServer server, UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            String cached = server.getProfileCache().get(playerId).map(GameProfile::getName).orElse(null);
            if (cached != null) {
                return cached;
            }
        }
        return playerId.toString().substring(0, 8);
    }

    public static boolean isOnline(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayer(playerId) != null;
    }
}
