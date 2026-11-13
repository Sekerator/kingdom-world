package io.github.sekerator.kingdomworld.core.law;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An alarm in a kingdom: somebody saw the offender strike or kill its people, or rob its storehouse under the eyes of a
 * soldier. It names the offender and the place where somebody of the kingdom last saw the offender, and lasts until
 * {@link LawRules#alarmTicks} after that sighting. While it lasts the soldiers near the place run to it; while the attack goes
 * on, and after a killing, they strike without calling out.
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

    private final UUID kingdomId;
    private final UUID offender;
    private String name;
    private String dimension;
    private int x;
    private int y;
    private int z;
    private final long started;
    private long lastCause;
    private long lastSeen;
    private long lastBlow = Long.MIN_VALUE / 4;
    private long firstBlow = Long.MIN_VALUE;
    private boolean killing;
    private boolean seen;
    private final Set<UUID> guardsKilled = new LinkedHashSet<>();
    private final Set<UUID> guardsWounded = new LinkedHashSet<>();

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
        this.lastSeen = now;
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

    /** Where somebody of the kingdom last saw the offender. */
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

    /** Game time somebody of the kingdom last saw the offender. */
    public long lastSeen() {
        return lastSeen;
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

    /**
     * Whether the alarm lasts: somebody of the kingdom saw the offender within {@link LawRules#alarmTicks}. A blow long ago
     * keeps no alarm alive; the soldiers search where the offender was last seen, and no longer.
     */
    public boolean active(long now, LawRules rules) {
        return now - lastSeen <= rules.alarmTicks;
    }

    /**
     * Whether the soldiers strike at once: after a killing for as long as the alarm lasts, and while blows still fall.
     */
    public boolean violent(long now, LawRules rules) {
        return killing || now - lastBlow <= rules.ongoingTicks;
    }

    // ---------------------------------------------------------------- changes, through the book

    void feed(Cause cause, String name, String dimension, int x, int y, int z, long now) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
        // The deed was seen: the offender is where it was done.
        sighted(dimension, x, y, z, now);
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

    /**
     * Somebody of the kingdom sees the offender at a place: the soldiers know where to, and the alarm lasts from now again.
     */
    void sighted(String dimension, int x, int y, int z, long now) {
        moveTo(dimension, x, y, z);
        lastSeen = Math.max(lastSeen, now);
    }

    void markSeen() {
        seen = true;
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
