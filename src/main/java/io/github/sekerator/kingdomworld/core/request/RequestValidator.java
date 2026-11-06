package io.github.sekerator.kingdomworld.core.request;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Generic checks every client request must pass before the server acts on it.
 *
 * <p>{@link #admit(Requester, RequestPolicy)} checks the sender, then the per-player rate limit, then the
 * permission level. The rate limit comes first so that a flood of requests is cut off before any other work.
 * The static {@code check*} methods validate individual fields of a request after admission.
 */
public final class RequestValidator {
    private final LongSupplier clockMillis;
    private final Map<RequestPolicy, SlidingWindowRateLimiter<UUID>> limiters = new ConcurrentHashMap<>();

    public RequestValidator(LongSupplier clockMillis) {
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    /**
     * @param requester the sender, or {@code null} if the request has no player behind it
     * @return the reason to refuse the request, or empty if it may proceed
     */
    public Optional<Rejection> admit(Requester requester, RequestPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (requester == null) {
            return Optional.of(Rejection.NO_SENDER);
        }
        if (!requester.isConnected()) {
            return Optional.of(Rejection.NOT_CONNECTED);
        }
        SlidingWindowRateLimiter<UUID> limiter = limiters.computeIfAbsent(policy,
                p -> new SlidingWindowRateLimiter<>(p.maxRequests(), p.windowMillis()));
        if (!limiter.tryAcquire(requester.id(), clockMillis.getAsLong())) {
            return Optional.of(Rejection.RATE_LIMITED);
        }
        if (!requester.hasPermissionLevel(policy.permissionLevel())) {
            return Optional.of(Rejection.INSUFFICIENT_PERMISSION);
        }
        return Optional.empty();
    }

    /**
     * Drops rate limit state of a player, for example when they log out.
     */
    public void forget(UUID requesterId) {
        limiters.values().forEach(limiter -> limiter.forget(requesterId));
    }

    public void clear() {
        limiters.clear();
    }

    public static Optional<Rejection> checkRange(long value, long min, long max) {
        return value >= min && value <= max ? Optional.empty() : Optional.of(Rejection.VALUE_OUT_OF_RANGE);
    }

    public static Optional<Rejection> checkLength(String value, int maxLength) {
        return value != null && value.length() <= maxLength ? Optional.empty() : Optional.of(Rejection.TEXT_TOO_LONG);
    }

    public static Optional<Rejection> checkSize(Collection<?> value, int maxSize) {
        return value != null && value.size() <= maxSize ? Optional.empty() : Optional.of(Rejection.COLLECTION_TOO_LARGE);
    }
}
