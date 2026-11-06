package io.github.sekerator.kingdomworld.core.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestValidatorTest {
    private static final RequestPolicy ANY_PLAYER = new RequestPolicy("test_public", 0, 2, 1000);
    private static final RequestPolicy OPERATOR = new RequestPolicy("test_admin", 2, 2, 1000);

    private final AtomicLong clock = new AtomicLong();
    private RequestValidator validator;

    private record FakeRequester(UUID id, boolean isConnected, int permissionLevel) implements Requester {
        @Override
        public boolean hasPermissionLevel(int level) {
            return permissionLevel >= level;
        }
    }

    private static FakeRequester player(int permissionLevel) {
        return new FakeRequester(UUID.randomUUID(), true, permissionLevel);
    }

    @BeforeEach
    void setUp() {
        validator = new RequestValidator(clock::get);
    }

    @Test
    void admitsValidRequests() {
        assertEquals(Optional.empty(), validator.admit(player(0), ANY_PLAYER));
        assertEquals(Optional.empty(), validator.admit(player(2), OPERATOR));
    }

    @Test
    void rejectsMissingOrDisconnectedSender() {
        assertEquals(Optional.of(Rejection.NO_SENDER), validator.admit(null, ANY_PLAYER));
        FakeRequester gone = new FakeRequester(UUID.randomUUID(), false, 4);
        assertEquals(Optional.of(Rejection.NOT_CONNECTED), validator.admit(gone, ANY_PLAYER));
    }

    @Test
    void enforcesPermissionLevel() {
        assertEquals(Optional.of(Rejection.INSUFFICIENT_PERMISSION), validator.admit(player(1), OPERATOR));
    }

    @Test
    void rateLimitsPerPlayerAndPolicy() {
        FakeRequester alice = player(4);
        FakeRequester bob = player(4);
        assertEquals(Optional.empty(), validator.admit(alice, ANY_PLAYER));
        assertEquals(Optional.empty(), validator.admit(alice, ANY_PLAYER));
        assertEquals(Optional.of(Rejection.RATE_LIMITED), validator.admit(alice, ANY_PLAYER));
        assertEquals(Optional.empty(), validator.admit(bob, ANY_PLAYER), "other players are not affected");
        assertEquals(Optional.empty(), validator.admit(alice, OPERATOR), "other request kinds are not affected");
        clock.set(1000);
        assertEquals(Optional.empty(), validator.admit(alice, ANY_PLAYER));
    }

    @Test
    void rateLimitAppliesBeforePermissionCheck() {
        FakeRequester guest = player(0);
        assertEquals(Optional.of(Rejection.INSUFFICIENT_PERMISSION), validator.admit(guest, OPERATOR));
        assertEquals(Optional.of(Rejection.INSUFFICIENT_PERMISSION), validator.admit(guest, OPERATOR));
        assertEquals(Optional.of(Rejection.RATE_LIMITED), validator.admit(guest, OPERATOR));
    }

    @Test
    void forgetResetsAPlayer() {
        FakeRequester alice = player(0);
        validator.admit(alice, ANY_PLAYER);
        validator.admit(alice, ANY_PLAYER);
        assertEquals(Optional.of(Rejection.RATE_LIMITED), validator.admit(alice, ANY_PLAYER));
        validator.forget(alice.id());
        assertEquals(Optional.empty(), validator.admit(alice, ANY_PLAYER));
    }

    @Test
    void fieldChecks() {
        assertEquals(Optional.empty(), RequestValidator.checkRange(5, 0, 5));
        assertEquals(Optional.of(Rejection.VALUE_OUT_OF_RANGE), RequestValidator.checkRange(-1, 0, 5));
        assertEquals(Optional.of(Rejection.VALUE_OUT_OF_RANGE), RequestValidator.checkRange(6, 0, 5));

        assertEquals(Optional.empty(), RequestValidator.checkLength("abc", 3));
        assertEquals(Optional.of(Rejection.TEXT_TOO_LONG), RequestValidator.checkLength("abcd", 3));
        assertEquals(Optional.of(Rejection.TEXT_TOO_LONG), RequestValidator.checkLength(null, 3));

        assertEquals(Optional.empty(), RequestValidator.checkSize(List.of(1, 2), 2));
        assertEquals(Optional.of(Rejection.COLLECTION_TOO_LARGE), RequestValidator.checkSize(List.of(1, 2, 3), 2));
        assertEquals(Optional.of(Rejection.COLLECTION_TOO_LARGE), RequestValidator.checkSize(null, 2));
    }

    @Test
    void policyValidatesItsLimits() {
        assertThrows(IllegalArgumentException.class, () -> new RequestPolicy("bad", 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RequestPolicy("bad", -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RequestPolicy("bad", 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RequestPolicy("bad", 0, 1, 0));
    }
}
