package io.github.sekerator.kingdomworld.core.territory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The moves of the thrones of kingdoms: for each kingdom the move its king started and has not finished, and when its throne
 * last moved. A move is known by a random id that the throne handed out for it carries, so that a throne of a move finished or
 * called off does nothing. Only keeps the records; the rules of moving live in
 * {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}. Not thread safe.
 */
public final class ThroneMoves {
    /**
     * A move of a throne that was started.
     *
     * @param id      the id the throne handed out for it carries
     * @param started game time in ticks when the king started it
     */
    public record Move(UUID id, long started) {
        public Move {
            Objects.requireNonNull(id, "id");
        }
    }

    private final Map<UUID, Move> pending = new LinkedHashMap<>();
    private final Map<UUID, Long> moved = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * The move the king of a kingdom started and has not finished.
     */
    public Optional<Move> pending(UUID kingdomId) {
        return Optional.ofNullable(pending.get(kingdomId));
    }

    /**
     * Whether {@code moveId} is the move of the throne of the kingdom that is going on.
     */
    public boolean isPending(UUID kingdomId, UUID moveId) {
        Move move = pending.get(kingdomId);
        return move != null && move.id().equals(moveId);
    }

    /**
     * Game time in ticks when the throne of the kingdom last moved, empty if it never did.
     */
    public OptionalLong lastMoved(UUID kingdomId) {
        Long time = moved.get(kingdomId);
        return time == null ? OptionalLong.empty() : OptionalLong.of(time);
    }

    /**
     * Game time in ticks from which a new move may start: {@code cooldown} ticks after the throne last moved, or
     * {@link Long#MIN_VALUE} if it never moved.
     */
    public long nextAllowed(UUID kingdomId, long cooldown) {
        Long time = moved.get(kingdomId);
        if (time == null) {
            return Long.MIN_VALUE;
        }
        long next = time + Math.max(0L, cooldown);
        return next < time ? Long.MAX_VALUE : next;
    }

    /**
     * Every move going on, by kingdom.
     */
    public Map<UUID, Move> allPending() {
        return Collections.unmodifiableMap(pending);
    }

    /**
     * When the throne of each kingdom last moved, by kingdom.
     */
    public Map<UUID, Long> allMoved() {
        return Collections.unmodifiableMap(moved);
    }

    /**
     * Records a move started, in place of any other of the kingdom.
     */
    public void begin(UUID kingdomId, Move move) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        pending.put(kingdomId, Objects.requireNonNull(move, "move"));
        changeListener.run();
    }

    /**
     * Forgets the move of a kingdom: the throne handed out for it does nothing any more.
     *
     * @return the move called off
     */
    public Optional<Move> cancel(UUID kingdomId) {
        Move move = pending.remove(kingdomId);
        if (move != null) {
            changeListener.run();
        }
        return Optional.ofNullable(move);
    }

    /**
     * Records the move of a kingdom finished at a game time: nothing is pending and the throne moved then.
     */
    public void finish(UUID kingdomId, long gameTime) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        pending.remove(kingdomId);
        moved.put(kingdomId, gameTime);
        changeListener.run();
    }

    /**
     * Forgets everything of a kingdom that is gone.
     */
    public void removeKingdom(UUID kingdomId) {
        boolean changed = pending.remove(kingdomId) != null;
        changed |= moved.remove(kingdomId) != null;
        if (changed) {
            changeListener.run();
        }
    }

    /**
     * Puts back what was saved of a kingdom, without telling the change listener.
     *
     * @param move  the move going on, or empty
     * @param moved when the throne last moved, or empty
     */
    public void restore(UUID kingdomId, Optional<Move> move, OptionalLong moved) {
        Objects.requireNonNull(kingdomId, "kingdomId");
        move.ifPresentOrElse(m -> pending.put(kingdomId, m), () -> pending.remove(kingdomId));
        if (moved.isPresent()) {
            this.moved.put(kingdomId, moved.getAsLong());
        } else {
            this.moved.remove(kingdomId);
        }
    }
}
