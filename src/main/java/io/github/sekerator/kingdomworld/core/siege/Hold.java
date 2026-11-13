package io.github.sekerator.kingdomworld.core.siege;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Attackers holding a place of the enemy: a chunk of its land, the square at the town hall of a city, its throne hall, or
 * a province of a state (the square of its main settlement, or the boundary stone of an empty one). The time grows while
 * attackers are there and defenders are not; defenders coming back interrupt it (the time starts over), and while the
 * defenders are shielded because their players are away it stands still. A taken chunk is freed again when defenders hold
 * it as long without attackers; a taken city, throne or province stays taken until the peace.
 */
public final class Hold {
    /**
     * What is held.
     */
    public enum Kind {
        /** A chunk of the enemy's land: the goal "land". */
        CHUNK,
        /** The square at the town hall of a city (0 for the capital): the goal "city". */
        PLAZA,
        /** The throne hall: the goal "throne". */
        THRONE,
        /**
         * A province of a state: the square of its main settlement, or the boundary stone at its middle when nobody lives
         * there. The time depends on which, so the caller tells it (see {@link SiegeRules#provinceTicks}).
         */
        PROVINCE;

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
     * What a step of a hold did.
     */
    public enum Change {
        NONE,
        /** The time grew. */
        PROGRESS,
        /** Defenders came: the time starts over. */
        INTERRUPTED,
        /** The place is taken now. */
        TAKEN,
        /** Defenders took a taken chunk back. */
        FREED,
        /** Defenders hold a taken chunk; it is not free yet. */
        RETAKING
    }

    private final Kind kind;
    private final long war;
    private final UUID attacker;
    private final UUID defender;
    private final String dimension;
    /**
     * The chunk for {@link Kind#CHUNK}, the city for {@link Kind#PLAZA}, 0 for {@link Kind#THRONE}, the province for
     * {@link Kind#PROVINCE}.
     */
    private final long target;
    private long progress;
    private boolean taken;
    private long since;

    public Hold(Kind kind, long war, UUID attacker, UUID defender, String dimension, long target, long progress, boolean taken, long since) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.war = war;
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.defender = Objects.requireNonNull(defender, "defender");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.target = target;
        this.progress = Math.max(0, progress);
        this.taken = taken;
        this.since = since;
    }

    public static String key(Kind kind, long war, UUID defender, String dimension, long target) {
        return kind.id() + "/" + war + "/" + defender + "/" + dimension + "/" + target;
    }

    public String key() {
        return key(kind, war, defender, dimension, target);
    }

    public Kind kind() {
        return kind;
    }

    public long war() {
        return war;
    }

    public UUID attacker() {
        return attacker;
    }

    public UUID defender() {
        return defender;
    }

    public String dimension() {
        return dimension;
    }

    public long target() {
        return target;
    }

    public long progress() {
        return progress;
    }

    public boolean taken() {
        return taken;
    }

    /**
     * When the place was first held, or taken.
     */
    public long since() {
        return since;
    }

    /**
     * The share of the time held so far, 0 to 100.
     */
    public int percent(SiegeRules rules) {
        return percent(rules.ticks(kind));
    }

    /**
     * The share of the time held so far, 0 to 100, of a hold that needs a given time.
     *
     * @param need game ticks the place must be held
     */
    public int percent(long need) {
        if (taken) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, progress * 100 / Math.max(1, need)));
    }

    /**
     * One step of a hold.
     *
     * @param attackers whether attackers are at the place now
     * @param defenders whether defenders are at the place now
     * @param frozen    whether the defenders are shielded now (their players are away): nothing moves
     * @param ticks     game ticks since the last step
     */
    public Change step(boolean attackers, boolean defenders, boolean frozen, long ticks, long now, SiegeRules rules) {
        return step(attackers, defenders, frozen, ticks, now, rules.ticks(kind));
    }

    /**
     * One step of a hold that needs a given time, for a place whose time is not the same for every place of its kind: a
     * province with a settlement is held longer than an empty one.
     *
     * @param need game ticks the place must be held, at least 1
     */
    public Change step(boolean attackers, boolean defenders, boolean frozen, long ticks, long now, long need) {
        if (frozen || ticks <= 0) {
            return Change.NONE;
        }
        need = Math.max(1, need);
        if (taken) {
            if (kind != Kind.CHUNK) {
                return Change.NONE;
            }
            if (defenders && !attackers) {
                progress = Math.max(0, progress - ticks);
                if (progress == 0) {
                    taken = false;
                    since = now;
                    return Change.FREED;
                }
                return Change.RETAKING;
            }
            if (attackers && progress < need) {
                progress = need;
            }
            return Change.NONE;
        }
        if (defenders) {
            if (progress > 0) {
                progress = 0;
                return Change.INTERRUPTED;
            }
            return Change.NONE;
        }
        if (!attackers) {
            return Change.NONE;
        }
        if (progress == 0) {
            since = now;
        }
        progress = Math.min(need, progress + ticks);
        if (progress >= need) {
            taken = true;
            since = now;
            return Change.TAKEN;
        }
        return Change.PROGRESS;
    }

    /**
     * Takes the place at once, for operators.
     */
    public void take(long now, SiegeRules rules) {
        take(now, rules.ticks(kind));
    }

    /**
     * Takes the place at once, for operators, as a hold that needs a given time.
     */
    public void take(long now, long need) {
        progress = Math.max(0, need);
        taken = true;
        since = now;
    }

    /**
     * Whether the hold may be forgotten: nobody holds the place and it was never taken.
     */
    public boolean idle() {
        return !taken && progress == 0;
    }
}
