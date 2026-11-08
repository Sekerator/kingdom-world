package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An offer of one kingdom to another, like the deals of trade: it waits for an answer until it expires.
 *
 * @param vassal for {@link Kind#VASSALAGE}, the kingdom that would become the vassal: the proposer offering itself or the
 *               other asked to bow
 * @param war    for {@link Kind#PEACE}, the war it ends
 * @param terms  for {@link Kind#PEACE}, the terms
 */
public record Proposal(long id, UUID from, UUID to, Kind kind, UUID vassal, long war, PeaceTerms terms, long created, long expires,
                       Status status) {

    /**
     * What is offered.
     */
    public enum Kind {
        /** A pact of non-aggression for some days. */
        PACT,
        /** A defensive alliance. */
        ALLIANCE,
        /** Vassalage: one of the two becomes the vassal of the other. */
        VASSALAGE,
        /** A vassal asks its overlord to let it go in peace. */
        RELEASE,
        /** Peace on terms. */
        PEACE;

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
    }

    /**
     * Where an offer stands.
     */
    public enum Status {
        OPEN,
        ACCEPTED,
        DECLINED,
        EXPIRED,
        CANCELLED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    public Proposal {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        terms = terms == null ? PeaceTerms.WHITE : terms;
        if (id < 1 || from.equals(to) || expires < created) {
            throw new IllegalArgumentException("Proposal out of range: #" + id);
        }
        if (kind == Kind.VASSALAGE && (vassal == null || !(vassal.equals(from) || vassal.equals(to)))) {
            throw new IllegalArgumentException("Vassalage without a vassal: #" + id);
        }
        if (kind != Kind.VASSALAGE) {
            vassal = null;
        }
        if (kind == Kind.PEACE) {
            if (war < 1) {
                throw new IllegalArgumentException("Peace without a war: #" + id);
            }
        } else {
            war = 0;
            terms = PeaceTerms.WHITE;
        }
    }

    public boolean open() {
        return status == Status.OPEN;
    }

    public boolean involves(UUID kingdom) {
        return from.equals(kingdom) || to.equals(kingdom);
    }

    public Proposal withStatus(Status value) {
        return new Proposal(id, from, to, kind, vassal, war, terms, created, expires, value);
    }

    /**
     * The same offer closing at another time: closed offers keep the time they closed at here.
     */
    public Proposal withExpires(long value) {
        return new Proposal(id, from, to, kind, vassal, war, terms, created, Math.max(created, value), status);
    }

    /**
     * The overlord of a vassalage offer.
     */
    public UUID overlord() {
        return vassal == null ? null : (vassal.equals(from) ? to : from);
    }
}
