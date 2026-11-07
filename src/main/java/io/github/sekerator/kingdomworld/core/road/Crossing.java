package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to build a road across the land of another kingdom: its owner accepts or declines it, like a deal.
 *
 * @param requester the kingdom that plans the road
 * @param owner     the kingdom whose land it crosses
 * @param sections  sections of the road on the owner's land
 * @param expires   game time it runs out unanswered
 */
public record Crossing(long id, long roadId, UUID requester, UUID owner, Road.Mode mode, Status status, int sections, long created,
                       long expires) {

    public enum Status {
        PROPOSED,
        ACCEPTED,
        DECLINED,
        EXPIRED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean open() {
            return this == PROPOSED;
        }
    }

    public Crossing {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(status, "status");
        if (requester.equals(owner) || sections < 0 || expires < created) {
            throw new IllegalArgumentException("Crossing out of range");
        }
    }

    public Crossing withStatus(Status value) {
        return new Crossing(id, roadId, requester, owner, mode, value, sections, created, expires);
    }
}
