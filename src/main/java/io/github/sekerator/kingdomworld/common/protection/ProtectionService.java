package io.github.sekerator.kingdomworld.common.protection;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.protection.AccessPolicy;
import io.github.sekerator.kingdomworld.core.protection.AccessSubject;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single place that decides whether an actor may do something at a position. Every protection hook
 * (block events, interactions, explosions, fire) asks this service; the decision itself is made by the
 * {@link AccessPolicy} of {@link KingdomWorldApi#accessPolicy()}, where later mechanics add exemptions.
 *
 * <p>Only the logical server decides. On a client level every check answers "allowed", because the client has
 * no kingdom data; the server corrects the client when it refuses.
 */
public final class ProtectionService {
    /** Operators who switched protection bypass on. Kept in memory only; cleared when the server stops. */
    private static final Set<UUID> BYPASS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    private static final long MESSAGE_INTERVAL_MILLIS = 1_000L;
    public static final int BYPASS_PERMISSION_LEVEL = 2;

    private ProtectionService() {
    }

    /**
     * Whether the actor may perform the action at the position.
     *
     * @param actor the acting entity, or {@code null} for no entity (treated like a mob)
     */
    public static boolean isAllowed(@Nullable Entity actor, Level level, BlockPos pos, ProtectedAction action) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        KingdomSavedData data = KingdomSavedData.get(serverLevel.getServer());
        String dimension = Dimensions.id(level);
        long chunk = ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4);
        UUID owner = data.state().territory().ownerOf(dimension, chunk);
        if (owner == null) {
            return true;
        }
        return KingdomWorldApi.accessPolicy().isAllowed(data.state().membership(), owner, subjectOf(actor), action,
                dimension, chunk, settings());
    }

    public static AccessSubject subjectOf(@Nullable Entity actor) {
        if (actor instanceof FakePlayer fakePlayer) {
            return AccessSubject.fakePlayer(fakePlayer.getUUID());
        }
        if (actor instanceof Player player) {
            if (BYPASS.contains(player.getUUID()) && player.hasPermissions(BYPASS_PERMISSION_LEVEL)) {
                return AccessSubject.bypass(player.getUUID());
            }
            return AccessSubject.player(player.getUUID());
        }
        return AccessSubject.OTHER;
    }

    public static AccessPolicy.Settings settings() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        return new AccessPolicy.Settings(config.protectInteractions.get(), config.allowFakePlayers.get());
    }

    /**
     * Whether the position lies in land of any kingdom.
     */
    public static boolean isClaimed(Level level, BlockPos pos) {
        return KingdomWorldApi.ownerAt(level, pos) != null;
    }

    /**
     * Claims of the level's dimension for bulk checks (explosions), or {@code null} on a client level.
     */
    @Nullable
    public static TerritoryIndex territory(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return KingdomSavedData.get(serverLevel.getServer()).state().territory();
    }

    public static boolean protectsFromExplosions() {
        return KingdomWorldConfig.SERVER.protectFromExplosions.get();
    }

    /**
     * Whether fire may neither spread into nor burn the block at the position. Called from the fire block for
     * every fire tick, so it stays cheap.
     */
    public static boolean protectsFromFire(Level level, BlockPos pos) {
        return level instanceof ServerLevel && KingdomWorldConfig.SERVER.protectFromFire.get() && isClaimed(level, pos);
    }

    /**
     * Switches bypass for an operator.
     *
     * @return whether bypass is now on
     */
    public static boolean toggleBypass(UUID playerId) {
        if (BYPASS.remove(playerId)) {
            return false;
        }
        BYPASS.add(playerId);
        return true;
    }

    public static boolean hasBypass(UUID playerId) {
        return BYPASS.contains(playerId);
    }

    public static void reset() {
        BYPASS.clear();
        LAST_MESSAGE.clear();
    }

    public static void forget(UUID playerId) {
        LAST_MESSAGE.remove(playerId);
    }

    /**
     * Tells a player above the hotbar that an action was refused, at most once a second.
     */
    public static void notifyDenied(@Nullable Entity actor, Level level, BlockPos pos) {
        if (!(actor instanceof ServerPlayer player) || actor instanceof FakePlayer || player.connection == null) {
            return;
        }
        long now = Util.getMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MESSAGE_INTERVAL_MILLIS) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        Optional<Kingdom> kingdom = owner == null ? Optional.empty() : KingdomWorldApi.kingdom(player.server, owner);
        Component name = kingdom.<Component>map(k -> Component.literal(k.name())
                .withStyle(style -> style.withColor(TextColor.fromRgb(k.color())))).orElse(Component.literal("?"));
        player.displayClientMessage(Component.translatable("kingdomworld.protection.denied", name), true);
    }

    /**
     * Tells a player above the hotbar something about protection, at most once a second.
     */
    public static void notify(Player actor, Component message) {
        if (!(actor instanceof ServerPlayer player) || actor instanceof FakePlayer || player.connection == null) {
            return;
        }
        long now = Util.getMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MESSAGE_INTERVAL_MILLIS) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(message, true);
    }
}
