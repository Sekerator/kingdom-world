package io.github.sekerator.kingdomworld.core.request;

import java.util.Objects;

/**
 * Admission rules for one kind of client request.
 *
 * @param name            short identifier used in logs
 * @param permissionLevel minimum operator permission level (0 means any player)
 * @param maxRequests     how many requests a single player may send within {@code windowMillis}
 * @param windowMillis    length of the rate limit window
 */
public record RequestPolicy(String name, int permissionLevel, int maxRequests, long windowMillis) {
    public static final int MAX_PERMISSION_LEVEL = 4;

    public RequestPolicy {
        Objects.requireNonNull(name, "name");
        if (permissionLevel < 0 || permissionLevel > MAX_PERMISSION_LEVEL) {
            throw new IllegalArgumentException("Permission level must be 0-4: " + permissionLevel);
        }
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be positive: " + maxRequests);
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("windowMillis must be positive: " + windowMillis);
        }
    }
}
