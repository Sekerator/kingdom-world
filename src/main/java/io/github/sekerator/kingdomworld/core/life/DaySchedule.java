package io.github.sekerator.kingdomworld.core.life;

import java.util.Locale;
import java.util.UUID;

/**
 * The day of a citizen: in the morning it leaves home for work, by day it works, in the evening it goes to the tavern of its
 * city or walks near home, at night it sleeps in its bed. Every citizen keeps the day a little earlier or later than the others
 * (a fixed shift from its id), so that a city does not get up and lie down in step.
 */
public final class DaySchedule {

    /**
     * A part of the day.
     */
    public enum Phase {
        MORNING,
        DAY,
        EVENING,
        NIGHT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private DaySchedule() {
    }

    /**
     * Ticks from {@code from} to {@code to} going forward through the day.
     */
    public static int span(int from, int to) {
        return Math.floorMod(to - from, LifeRules.DAY);
    }

    /**
     * How much earlier (negative) or later a citizen keeps its day: the same for the citizen every day.
     */
    public static int shift(UUID citizen, int spread) {
        if (spread <= 0) {
            return 0;
        }
        long mixed = mix(citizen.getMostSignificantBits() ^ Long.rotateLeft(citizen.getLeastSignificantBits(), 17));
        return (int) Math.floorMod(mixed, 2L * spread + 1) - spread;
    }

    /**
     * The part of the day a citizen is in.
     *
     * @param dayTime the time of the day of the level (the game time of the day, any number of days)
     * @param shift   the citizen's own shift, see {@link #shift}
     */
    public static Phase phase(long dayTime, int shift, LifeRules rules) {
        int t = (int) Math.floorMod(dayTime - shift, (long) LifeRules.DAY);
        if (within(t, rules.wakeUp(), rules.workStart())) {
            return Phase.MORNING;
        }
        if (within(t, rules.workStart(), rules.eveningStart())) {
            return Phase.DAY;
        }
        if (within(t, rules.eveningStart(), rules.sleepStart())) {
            return Phase.EVENING;
        }
        return Phase.NIGHT;
    }

    private static boolean within(int t, int from, int to) {
        return from <= to ? t >= from && t < to : t >= from || t < to;
    }

    /**
     * Ticks of the day a part lasts.
     */
    public static int length(Phase phase, LifeRules rules) {
        return switch (phase) {
            case MORNING -> span(rules.wakeUp(), rules.workStart());
            case DAY -> span(rules.workStart(), rules.eveningStart());
            case EVENING -> span(rules.eveningStart(), rules.sleepStart());
            case NIGHT -> span(rules.sleepStart(), rules.wakeUp());
        };
    }

    /**
     * Whether a citizen wants to spend this evening in the tavern: about {@code tavernShare} percent of the citizens do, a
     * different choice every day.
     */
    public static boolean wantsTavern(UUID citizen, long day, LifeRules rules) {
        if (rules.tavernShare() <= 0) {
            return false;
        }
        long mixed = mix(citizen.getLeastSignificantBits() * 31 + citizen.getMostSignificantBits() + day * 0x9E3779B97F4A7C15L);
        return Math.floorMod(mixed, 100L) < rules.tavernShare();
    }

    /**
     * The game day a time belongs to, counted from 0.
     */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, LifeRules.DAY);
    }

    static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
