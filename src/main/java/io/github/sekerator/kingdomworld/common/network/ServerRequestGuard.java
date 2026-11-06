package io.github.sekerator.kingdomworld.common.network;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.request.Rejection;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.core.request.RequestValidator;
import io.github.sekerator.kingdomworld.core.request.Requester;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side checks for requests coming from clients.
 *
 * <p>A handler first calls {@link #admit(ServerPlayer, RequestPolicy)}, which verifies that the sender is a
 * connected player, applies the per-player rate limit of the policy and checks the permission level. It then
 * validates each field with the {@code require*} methods. Every method returns {@code false} for a rejected
 * request after logging the reason; the handler must then stop without changing any state.
 *
 * <p>Rejections are logged at WARN, except rate limiting, which is logged at DEBUG so that a flooding client
 * cannot flood the log as well.
 */
public final class ServerRequestGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RequestValidator VALIDATOR = new RequestValidator(Util::getMillis);

    private ServerRequestGuard() {
    }

    public static boolean admit(@Nullable ServerPlayer sender, RequestPolicy policy) {
        Optional<Rejection> rejection = VALIDATOR.admit(sender == null ? null : new PlayerRequester(sender), policy);
        rejection.ifPresent(reason -> logRejection(sender, policy, reason, null));
        return rejection.isEmpty();
    }

    public static boolean requireRange(ServerPlayer sender, RequestPolicy policy, String field, long value, long min, long max) {
        return accept(sender, policy, RequestValidator.checkRange(value, min, max),
                field + "=" + value + " not in [" + min + ", " + max + "]");
    }

    public static boolean requireLength(ServerPlayer sender, RequestPolicy policy, String field, @Nullable String value, int maxLength) {
        return accept(sender, policy, RequestValidator.checkLength(value, maxLength),
                field + " length " + (value == null ? "null" : value.length()) + " exceeds " + maxLength);
    }

    public static boolean requireSize(ServerPlayer sender, RequestPolicy policy, String field, @Nullable Collection<?> value, int maxSize) {
        return accept(sender, policy, RequestValidator.checkSize(value, maxSize),
                field + " size " + (value == null ? "null" : value.size()) + " exceeds " + maxSize);
    }

    /**
     * Forgets rate limit state of a player who left the server.
     */
    public static void forget(UUID playerId) {
        VALIDATOR.forget(playerId);
    }

    /**
     * Drops all state, for example when the server stops.
     */
    public static void reset() {
        VALIDATOR.clear();
    }

    private static boolean accept(ServerPlayer sender, RequestPolicy policy, Optional<Rejection> rejection, String detail) {
        rejection.ifPresent(reason -> logRejection(sender, policy, reason, detail));
        return rejection.isEmpty();
    }

    private static void logRejection(@Nullable ServerPlayer sender, RequestPolicy policy, Rejection reason, @Nullable String detail) {
        String who = sender == null ? "<no player>" : sender.getGameProfile().getName() + " (" + sender.getUUID() + ")";
        String suffix = detail == null ? "" : ": " + detail;
        if (reason == Rejection.RATE_LIMITED) {
            LOGGER.debug("Rejected {} request from {}: {}{}", policy.name(), who, reason, suffix);
        } else {
            LOGGER.warn("Rejected {} request from {}: {}{}", policy.name(), who, reason, suffix);
        }
    }

    private record PlayerRequester(ServerPlayer player) implements Requester {
        @Override
        public UUID id() {
            return player.getUUID();
        }

        @Override
        public boolean isConnected() {
            return !player.hasDisconnected();
        }

        @Override
        public boolean hasPermissionLevel(int level) {
            return player.hasPermissions(level);
        }
    }
}
