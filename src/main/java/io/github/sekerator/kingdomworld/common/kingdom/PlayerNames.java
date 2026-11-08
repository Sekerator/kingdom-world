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

    /**
     * Whether the kingdoms know a player: online, a member of a kingdom or the owner of an account. Money is never paid to
     * a profile that has not played here, so that nothing lands on an account nobody will ever open.
     */
    public static boolean isKnown(MinecraftServer server, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (isOnline(server, playerId)) {
            return true;
        }
        var state = io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.state(server);
        return state.economy().accounts().players().contains(playerId) || state.membership().kingdomOf(playerId).isPresent();
    }

    /**
     * A player the kingdoms know, by name: online, a member of a kingdom or the owner of an account. Never asks the network,
     * so a name nobody here ever used finds nobody.
     */
    public static java.util.Optional<GameProfile> known(MinecraftServer server, String name) {
        if (name == null || name.isBlank() || name.length() > 64) {
            return java.util.Optional.empty();
        }
        String wanted = name.strip();
        ServerPlayer online = server.getPlayerList().getPlayerByName(wanted);
        if (online != null) {
            return java.util.Optional.of(online.getGameProfile());
        }
        var state = io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.state(server);
        java.util.Set<UUID> players = new java.util.LinkedHashSet<>(state.economy().accounts().players());
        for (var kingdom : state.kingdoms().all()) {
            state.membership().kingdom(kingdom.id()).ifPresent(members -> players.addAll(members.members().keySet()));
        }
        for (UUID player : players) {
            String known = name(server, player);
            if (known.equalsIgnoreCase(wanted)) {
                return java.util.Optional.of(new GameProfile(player, known));
            }
        }
        return java.util.Optional.empty();
    }
}
