package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A conspiracy of a discontented house against the crown. It ripens over played days; counter-intelligence may find it out,
 * and then the crown decides the fate of the house. A foreign kingdom whose spies stirred it up backs it.
 */
public final class Plot {
    public enum Status {
        BREWING,
        DISCOVERED;

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

    /**
     * What the crown does with a house whose plot was found out.
     */
    public enum Verdict {
        EXECUTE,
        PARDON,
        EXILE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Verdict> byId(String id) {
            for (Verdict verdict : values()) {
                if (verdict.id().equals(id)) {
                    return Optional.of(verdict);
                }
            }
            return Optional.empty();
        }
    }

    private final long id;
    private final UUID kingdomId;
    private final long house;
    private int progress;
    private final int needed;
    private Status status;
    private UUID sponsor;
    private final long started;

    public Plot(long id, UUID kingdomId, long house, int progress, int needed, Status status, UUID sponsor, long started) {
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.house = house;
        this.progress = Math.max(0, progress);
        this.needed = Math.max(1, needed);
        this.status = Objects.requireNonNull(status, "status");
        this.sponsor = sponsor;
        this.started = started;
    }

    public long id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public long house() {
        return house;
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    public boolean ripe() {
        return progress >= needed;
    }

    void advance(int days) {
        progress = Math.min(needed, progress + days);
    }

    public Status status() {
        return status;
    }

    void discover() {
        status = Status.DISCOVERED;
    }

    public Optional<UUID> sponsor() {
        return Optional.ofNullable(sponsor);
    }

    void backBy(UUID kingdom) {
        sponsor = kingdom;
    }

    public long started() {
        return started;
    }
}
