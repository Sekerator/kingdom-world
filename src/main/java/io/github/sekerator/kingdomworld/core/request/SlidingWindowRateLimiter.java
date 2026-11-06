package io.github.sekerator.kingdomworld.core.request;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows at most {@code maxEvents} events per key within any window of {@code windowMillis}.
 *
 * <p>The caller supplies the current time, which keeps the class deterministic in tests. Memory use is bounded
 * by {@code maxEvents} timestamps per tracked key; call {@link #forget(Object)} when a key goes away.
 * Thread safe.
 */
public final class SlidingWindowRateLimiter<K> {
    private final int maxEvents;
    private final long windowMillis;
    private final Map<K, Deque<Long>> events = new HashMap<>();

    public SlidingWindowRateLimiter(int maxEvents, long windowMillis) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be positive: " + maxEvents);
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("windowMillis must be positive: " + windowMillis);
        }
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
    }

    /**
     * Records an event for {@code key} if the limit allows it.
     *
     * @return {@code true} if the event is allowed, {@code false} if the key is over its limit
     */
    public synchronized boolean tryAcquire(K key, long nowMillis) {
        Deque<Long> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && nowMillis - timestamps.peekFirst() >= windowMillis) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxEvents) {
            return false;
        }
        timestamps.addLast(nowMillis);
        return true;
    }

    public synchronized void forget(K key) {
        events.remove(key);
    }

    public synchronized void clear() {
        events.clear();
    }

    public synchronized int trackedKeys() {
        return events.size();
    }
}
