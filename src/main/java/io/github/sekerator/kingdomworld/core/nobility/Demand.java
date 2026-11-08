package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a party at court asks of the crown. The king agrees (and pays what it costs), refuses (the party is angry and the
 * kingdom less stable) or bargains (half of each). A demand nobody answers in time counts as a mild refusal; the time runs
 * only on days the kingdom is played.
 *
 * @param house  the house that speaks for the party
 * @param target the other kingdom of a war or a peace, {@code null} otherwise
 * @param value  what it asks: the percent of a lower tax, or the money for works, loot or the gift of a bargain
 * @param made   the day of the court (a played day) it was made on
 * @param due    the day of the court by which it must be answered
 */
public record Demand(long id, UUID kingdomId, Faction faction, long house, Kind kind, UUID target, long value, long made, long due) {
    public enum Kind {
        LOWER_TAX,
        DECLARE_WAR,
        MAKE_PEACE,
        GRANT_TITLE,
        BUILD,
        SHARE_LOOT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        /**
         * Whether accepting costs money from the treasury.
         */
        public boolean paid() {
            return this == BUILD || this == SHARE_LOOT;
        }
    }

    /**
     * How a demand was answered.
     */
    public enum Answer {
        ACCEPT,
        REFUSE,
        BARGAIN,
        LAPSED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Answer> byId(String id) {
            for (Answer answer : values()) {
                if (answer.id().equals(id)) {
                    return Optional.of(answer);
                }
            }
            return Optional.empty();
        }
    }

    public Demand {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(kind, "kind");
        value = Math.max(0, value);
    }

    /**
     * The money a bargain gives the house: half of what the demand is worth.
     */
    public long bargain(long nominal) {
        return Math.max(1, (kind.paid() ? value : nominal) / 2);
    }
}
