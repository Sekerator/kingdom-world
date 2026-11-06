package io.github.sekerator.kingdomworld.core.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    @Test
    void allowsUpToLimitWithinWindow() {
        SlidingWindowRateLimiter<String> limiter = new SlidingWindowRateLimiter<>(3, 1000);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 10));
        assertTrue(limiter.tryAcquire("a", 20));
        assertFalse(limiter.tryAcquire("a", 30));
        assertFalse(limiter.tryAcquire("a", 999));
    }

    @Test
    void windowSlides() {
        SlidingWindowRateLimiter<String> limiter = new SlidingWindowRateLimiter<>(2, 1000);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 500));
        assertFalse(limiter.tryAcquire("a", 900));
        assertTrue(limiter.tryAcquire("a", 1000));
        assertFalse(limiter.tryAcquire("a", 1400));
        assertTrue(limiter.tryAcquire("a", 1500));
    }

    @Test
    void rejectedAttemptsDoNotConsumeQuota() {
        SlidingWindowRateLimiter<String> limiter = new SlidingWindowRateLimiter<>(1, 1000);
        assertTrue(limiter.tryAcquire("a", 0));
        for (int t = 1; t < 1000; t += 100) {
            assertFalse(limiter.tryAcquire("a", t));
        }
        assertTrue(limiter.tryAcquire("a", 1000));
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowRateLimiter<String> limiter = new SlidingWindowRateLimiter<>(1, 1000);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("b", 0));
        assertFalse(limiter.tryAcquire("a", 1));
    }

    @Test
    void forgetAndClearReleaseState() {
        SlidingWindowRateLimiter<String> limiter = new SlidingWindowRateLimiter<>(1, 1000);
        limiter.tryAcquire("a", 0);
        limiter.tryAcquire("b", 0);
        assertEquals(2, limiter.trackedKeys());
        limiter.forget("a");
        assertEquals(1, limiter.trackedKeys());
        assertTrue(limiter.tryAcquire("a", 1));
        limiter.clear();
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter<String>(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRateLimiter<String>(1, 0));
    }
}
