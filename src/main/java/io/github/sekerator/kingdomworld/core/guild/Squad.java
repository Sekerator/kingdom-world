package io.github.sekerator.kingdomworld.core.guild;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A squad of adventurers of one kingdom. At home its members guard the land; on an expedition their bodies are gone and
 * they come back at {@code returns} with whatever the expedition brought.
 *
 * @param contractId the contract of the expedition, 0 at home
 * @param departed   game time the squad left
 * @param returns    game time the squad comes back
 * @param power      the strength of the squad when it left
 */
public record Squad(long id, UUID kingdomId, List<UUID> members, State state, long contractId, long departed, long returns, double power) {

    public enum State {
        HOME,
        AWAY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Squad {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(state, "state");
        members = List.copyOf(members);
        if (id < 1 || members.isEmpty() || members.size() > 64 || contractId < 0 || !Double.isFinite(power) || power < 0) {
            throw new IllegalArgumentException("Squad out of range: #" + id);
        }
        if (state == State.AWAY && contractId == 0) {
            throw new IllegalArgumentException("A squad away needs a contract: #" + id);
        }
    }

    public boolean away() {
        return state == State.AWAY;
    }

    public Squad departing(long contract, long now, long back, double strength) {
        return new Squad(id, kingdomId, members, State.AWAY, contract, now, back, strength);
    }

    public Squad home() {
        return new Squad(id, kingdomId, members, State.HOME, 0, 0, 0, 0);
    }

    public Squad withMembers(List<UUID> value) {
        return new Squad(id, kingdomId, value, state, contractId, departed, returns, power);
    }
}
