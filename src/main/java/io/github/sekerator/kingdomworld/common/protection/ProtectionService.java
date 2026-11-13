package io.github.sekerator.kingdomworld.common.protection;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
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
    /** Longest kingdom name shown above the hotbar; the line is not wrapped and must fit the narrowest screen. */
    public static final int ACTIONBAR_NAME_LENGTH = 16;

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

    /**
     * Whether a player (or a machine acting as one) may hurt a citizen: in peacetime only members of the citizen's
     * kingdom may; exemptions of the access policy (war) allow more. Monsters are never asked.
     */
    public static boolean mayHurtCitizen(Player attacker, CitizenEntity citizen) {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.kingdomId() == null) {
            return true;
        }
        KingdomSavedData data = KingdomSavedData.get(level.getServer());
        if (attacker instanceof FakePlayer machine) {
            return machineMayHurt(data, machine, citizen);
        }
        String dimension = Dimensions.id(level);
        long chunk = ChunkKey.of(citizen.blockPosition().getX() >> 4, citizen.blockPosition().getZ() >> 4);
        return KingdomWorldApi.accessPolicy().isAllowed(data.state().membership(), citizen.kingdomId(), subjectOf(attacker),
                ProtectedAction.ATTACK_CITIZEN, dimension, chunk, settings());
    }

    /**
     * A machine of another mod (a deployer and the like) strikes the citizens of a kingdom only at home: the citizen stands
     * on the land of its kingdom, and the machine belongs to that kingdom (its owner is a member) or stands on its land too.
     * On the land of others and on nobody's land machines strike no citizens, whatever {@code allowFakePlayers} says.
     */
    static boolean machineMayHurt(KingdomSavedData data, FakePlayer machine, CitizenEntity citizen) {
        UUID kingdom = citizen.kingdomId();
        if (!kingdom.equals(KingdomWorldApi.ownerAt(citizen.level(), citizen.blockPosition()))
                || !kingdom.equals(KingdomWorldApi.ownerAt(citizen.level(), machine.blockPosition()))) {
            return false;
        }
        // The machine of the kingdom: whoever set it up is a member who may strike its citizens. A machine without a known
        // owner strikes nobody.
        UUID owner = machineOwner(data, machine);
        if (owner == null) {
            return false;
        }
        ProtectedAction action = ProtectedAction.ATTACK_CITIZEN;
        return action.permission() == null ? data.state().membership().isMember(owner, kingdom)
                : data.state().membership().hasPermission(owner, kingdom, action.permission());
    }

    /**
     * Who set a machine up, if it can be known: the player the fake player stands for, or an owner the machine of another
     * mod keeps (a field of the type UUID whose name says so, as the deployers of Create have).
     */
    @Nullable
    static UUID machineOwner(KingdomSavedData data, FakePlayer machine) {
        if (data.state().membership().kingdomOf(machine.getUUID()).isPresent()) {
            return machine.getUUID();
        }
        for (Class<?> type = machine.getClass(); type != null && type != FakePlayer.class; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (field.getType() == UUID.class && field.getName().toLowerCase(java.util.Locale.ROOT).contains("owner")
                        && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(machine);
                        if (value instanceof UUID uuid) {
                            return uuid;
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
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
        if (!(level instanceof ServerLevel serverLevel) || !KingdomWorldConfig.SERVER.protectFromFire.get() || !isClaimed(level, pos)) {
            return false;
        }
        // Fire burns freely in the wilds of a kingdom of the world, as on land of nobody.
        return !io.github.sekerator.kingdomworld.common.land.Wilderness.isWild(serverLevel.getServer(), Dimensions.id(level),
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4));
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
        Component message = kingdom.<Component>map(k -> Component.translatable("kingdomworld.protection.denied",
                        Component.literal(shortName(k.name())).withStyle(style -> style.withColor(TextColor.fromRgb(k.color())))))
                .orElseGet(() -> Component.translatable("kingdomworld.protection.denied_unknown"));
        player.displayClientMessage(message, true);
    }

    /**
     * A kingdom name short enough for a line above the hotbar.
     */
    public static String shortName(String name) {
        return name.length() <= ACTIONBAR_NAME_LENGTH ? name : name.substring(0, ACTIONBAR_NAME_LENGTH) + "\u2026";
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
