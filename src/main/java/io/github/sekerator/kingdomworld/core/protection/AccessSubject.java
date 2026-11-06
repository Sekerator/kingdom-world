package io.github.sekerator.kingdomworld.core.protection;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is trying to act in protected land.
 *
 * @param kind     what kind of actor it is
 * @param playerId the player's id; {@code null} for {@link Kind#OTHER}
 */
public record AccessSubject(Kind kind, UUID playerId) {

    public enum Kind {
        /** A real player. */
        PLAYER,
        /** A machine of another mod acting as a fake player. */
        FAKE_PLAYER,
        /** An operator who switched protection bypass on. */
        BYPASS,
        /** Anything else, for example a mob. */
        OTHER
    }

    public static final AccessSubject OTHER = new AccessSubject(Kind.OTHER, null);

    public AccessSubject {
        Objects.requireNonNull(kind, "kind");
        if (kind != Kind.OTHER) {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    public static AccessSubject player(UUID playerId) {
        return new AccessSubject(Kind.PLAYER, playerId);
    }

    public static AccessSubject fakePlayer(UUID playerId) {
        return new AccessSubject(Kind.FAKE_PLAYER, playerId);
    }

    public static AccessSubject bypass(UUID playerId) {
        return new AccessSubject(Kind.BYPASS, playerId);
    }
}
