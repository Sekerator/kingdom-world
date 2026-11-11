package io.github.sekerator.kingdomworld.core.law;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An alarm in a kingdom: somebody struck or killed its people, or robbed its storehouse under the eyes of a soldier. It
 * names the offender and the place where the offender was last known, and lasts until {@link LawRules#alarmTicks} after the
 * last cause. While it lasts the soldiers near the place run to it; while the attack goes on, and after a killing, they
 * strike without calling out.
 *
 * <p>The alarm also keeps what the army needs to decide on reinforcements: when it began, how long it lasts, and how many
 * soldiers the offender killed and wounded since. Changed only through {@link AlarmBook}.
 */
public final class Alarm {

    /**
     * What raises or feeds an alarm.
     */
    public enum Cause {
        /** A blow at a citizen or a soldier. */
        BLOW,
        /** A citizen or a soldier was killed. */
        KILLING,
        /** A theft a soldier noticed at once. */
        THEFT
    }

    /**
     * A crime of the offender nobody has told the guards of yet: it is written into the record when a soldier sees the
     * offender, whatever became of the first witnesses.
     *
     * @param key what makes the deed one: blows at the same victim are one assault
     */
    public record Deed(CrimeKind kind, String victim, long worth, String key) {
        public Deed {
            Objects.requireNonNull(kind, "kind");
            victim = victim == null ? "" : victim;
            key = key == null ? "" : key;
        }
    }

    private final UUID kingdomId;
    private final UUID offender;
    private String name;
    private String dimension;
    private int x;
    private int y;
    private int z;
    private final long started;
    private long lastCause;
    private long lastBlow = Long.MIN_VALUE / 4;
    private long firstBlow = Long.MIN_VALUE;
    private boolean killing;
    private boolean seen;
    private final Set<UUID> guardsKilled = new LinkedHashSet<>();
    private final Set<UUID> guardsWounded = new LinkedHashSet<>();
    private final Map<String, Deed> unseen = new LinkedHashMap<>();

    Alarm(UUID kingdomId, UUID offender, String name, String dimension, int x, int y, int z, long now) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.offender = Objects.requireNonNull(offender, "offender");
        this.name = name == null ? "" : name;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.x = x;
        this.y = y;
        this.z = z;
        this.started = now;
        this.lastCause = now;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public UUID offender() {
        return offender;
    }

    public String name() {
        return name;
    }

    public String dimension() {
        return dimension;
    }

    /** Where the offender was last known. */
    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** Game time the alarm was raised at. */
    public long started() {
        return started;
    }

    /** Game time of the last blow, killing or theft that fed the alarm. */
    public long lastCause() {
        return lastCause;
    }

    /**
     * How long the alarm lasts by now, in ticks.
     */
    public long duration(long now) {
        return Math.max(0, now - started);
    }

    /**
     * How long the fight lasts by now, in ticks: the time since the first blow or killing, 0 while nobody was struck (an
     * alarm for a theft alone is no fight).
     */
    public long fightTicks(long now) {
        return firstBlow == Long.MIN_VALUE ? 0 : Math.max(0, now - firstBlow);
    }

    /**
     * Whether the offender killed somebody since the alarm was raised.
     */
    public boolean killing() {
        return killing;
    }

    /**
     * Whether a soldier has seen the offender since the alarm was raised.
     */
    public boolean seen() {
        return seen;
    }

    /** Soldiers of the kingdom the offender killed since the alarm was raised. */
    public int guardsKilled() {
        return guardsKilled.size();
    }

    /** Soldiers of the kingdom the offender wounded since the alarm was raised and who are still alive. */
    public int guardsWounded() {
        return guardsWounded.size();
    }

    public boolean active(long now, LawRules rules) {
        return now - lastCause <= rules.alarmTicks;
    }

    /**
     * Whether the soldiers strike at once: after a killing for as long as the alarm lasts, and while blows still fall.
     */
    public boolean violent(long now, LawRules rules) {
        return killing || now - lastBlow <= rules.ongoingTicks;
    }

    /**
     * The deeds nobody has told the guards of yet.
     */
    public List<Deed> unseen() {
        return Collections.unmodifiableList(new ArrayList<>(unseen.values()));
    }

    // ---------------------------------------------------------------- changes, through the book

    void feed(Cause cause, String name, String dimension, int x, int y, int z, long now) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
        moveTo(dimension, x, y, z);
        lastCause = Math.max(lastCause, now);
        if (cause == Cause.BLOW || cause == Cause.KILLING) {
            lastBlow = Math.max(lastBlow, now);
            if (firstBlow == Long.MIN_VALUE) {
                firstBlow = now;
            }
        }
        if (cause == Cause.KILLING) {
            killing = true;
        }
    }

    void moveTo(String dimension, int x, int y, int z) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void markSeen() {
        seen = true;
    }

    void addUnseen(Deed deed) {
        unseen.putIfAbsent(deed.kind().id() + "|" + deed.key(), deed);
    }

    List<Deed> takeUnseen() {
        List<Deed> taken = new ArrayList<>(unseen.values());
        unseen.clear();
        return taken;
    }

    void guardWounded(UUID soldier) {
        if (!guardsKilled.contains(soldier)) {
            guardsWounded.add(soldier);
        }
    }

    void guardKilled(UUID soldier) {
        guardsWounded.remove(soldier);
        guardsKilled.add(soldier);
    }
}
