package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Objects;
import java.util.UUID;

/**
 * A treaty between two kingdoms: a pact, an alliance, vassalage or a truce. The pair is kept in a fixed order (the smaller
 * id first), so that one pair has one treaty.
 *
 * @param since    game time it was made
 * @param until    game time a pact or a truce ends; {@link Long#MAX_VALUE} for alliances and vassalage
 * @param overlord the overlord for vassalage, {@code null} otherwise
 * @param share    for vassalage, the share of the vassal's daily income it pays as tribute, in permille
 */
public record Treaty(UUID first, UUID second, Bond kind, long since, long until, UUID overlord, int share) {
    public Treaty {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(kind, "kind");
        if (first.equals(second) || !kind.isTreaty() || first.compareTo(second) > 0 || until < since) {
            throw new IllegalArgumentException("Treaty out of range: " + kind);
        }
        if (kind == Bond.VASSAL) {
            if (overlord == null || !(overlord.equals(first) || overlord.equals(second)) || share < 0 || share > 1_000) {
                throw new IllegalArgumentException("Vassalage out of range");
            }
        } else {
            overlord = null;
            share = 0;
        }
    }

    /**
     * A treaty between two kingdoms in either order.
     */
    public static Treaty of(UUID a, UUID b, Bond kind, long since, long until, UUID overlord, int share) {
        return a.compareTo(b) <= 0 ? new Treaty(a, b, kind, since, until, overlord, share) : new Treaty(b, a, kind, since, until, overlord, share);
    }

    public boolean involves(UUID kingdom) {
        return first.equals(kingdom) || second.equals(kingdom);
    }

    public UUID other(UUID kingdom) {
        return first.equals(kingdom) ? second : first;
    }

    /**
     * The vassal of a vassalage, {@code null} otherwise.
     */
    public UUID vassal() {
        return overlord == null ? null : other(overlord);
    }

    /**
     * Whether a pact or a truce has run out by a time; alliances and vassalage never do.
     */
    public boolean expired(long now) {
        return until != Long.MAX_VALUE && now >= until;
    }

    /**
     * The key of the pair, the same in either order.
     */
    public static String key(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public String key() {
        return key(first, second);
    }
}
