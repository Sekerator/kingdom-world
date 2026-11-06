package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of an operation that either succeeds or is refused with a {@link KingdomError}.
 *
 * @param error  the reason for the refusal, or {@code null} on success
 * @param joined on success: whether the operation made a player join a kingdom (for example an application
 *               to a kingdom that had already invited the player)
 */
public record Outcome(KingdomError error, boolean joined) {
    public static final Outcome DONE = new Outcome(null, false);
    public static final Outcome JOINED = new Outcome(null, true);

    public Outcome {
        if (error != null && joined) {
            throw new IllegalArgumentException("A refused outcome cannot join a player");
        }
    }

    public static Outcome fail(KingdomError error) {
        return new Outcome(Objects.requireNonNull(error, "error"), false);
    }

    public boolean ok() {
        return error == null;
    }

    public Optional<KingdomError> failure() {
        return Optional.ofNullable(error);
    }
}
