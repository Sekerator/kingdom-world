package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
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

    /**
     * @return the player's name, or an empty string for {@code null} (no player)
     */
    public static String name(MinecraftServer server, UUID playerId) {
        if (playerId == null) {
            return "";
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

    /**
     * The player's name for display, or a translated "nobody" for {@code null}.
     */
    public static Component display(MinecraftServer server, UUID playerId) {
        return playerId == null ? Component.translatable("kingdomworld.ruler.none") : Component.literal(name(server, playerId));
    }

    /**
     * A name received from the server, or a translated "nobody" for an empty one.
     */
    public static Component display(String name) {
        return name == null || name.isEmpty() ? Component.translatable("kingdomworld.ruler.none") : Component.literal(name);
    }

    public static boolean isOnline(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayer(playerId) != null;
    }
}
