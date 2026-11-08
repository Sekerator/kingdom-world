package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A war between two kingdoms and their allies: declared with a goal, prepared for some time, fought until peace. Each side
 * has a score of war from 0 to 100 (battles won, enemies put down, caravans robbed, deals blocked, goals held) and an
 * exhaustion from 0 to 100 (time, losses, an empty treasury). A side worn out to the end gives in on the terms of the enemy;
 * a war that lasts too long ends in a white peace.
 */
public final class War {
    /** Score and exhaustion go from 0 to this. */
    public static final double MAX = 100.0;

    /**
     * Where a war stands.
     */
    public enum Phase {
        /** Declared; fighting starts at {@link #starts()}. */
        PREPARING,
        ACTIVE,
        OVER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Phase> byId(String id) {
            for (Phase phase : values()) {
                if (phase.id().equals(id)) {
                    return Optional.of(phase);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The two sides of a war.
     */
    public enum Side {
        ATTACKER,
        DEFENDER;

        public int index() {
            return ordinal();
        }

        public Side enemy() {
            return this == ATTACKER ? DEFENDER : ATTACKER;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Side> byId(String id) {
            for (Side side : values()) {
                if (side.id().equals(id)) {
                    return Optional.of(side);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * How a war ended.
     */
    public enum Result {
        NONE,
        /** Peace agreed, terms as written. */
        PEACE,
        /** The time of war ran out: a white peace. */
        TIME,
        /** One side was worn out and gave in. */
        SURRENDER,
        /** An operator ended it. */
        ADMIN,
        /** A leader of a side is gone. */
        GONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Result> byId(String id) {
            for (Result result : values()) {
                if (result.id().equals(id)) {
                    return Optional.of(result);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * What a side did in the war, for the tab and the chronicle.
     */
    public static final class Tally {
        int battles;
        int kills;
        int caravans;
        int blockades;
        int losses;

        public int battles() {
            return battles;
        }

        public int kills() {
            return kills;
        }

        public int caravans() {
            return caravans;
        }

        public int blockades() {
            return blockades;
        }

        public int losses() {
            return losses;
        }

        public void restore(int battlesWon, int killed, int robbed, int blocked, int lost) {
            battles = Math.max(0, battlesWon);
            kills = Math.max(0, killed);
            caravans = Math.max(0, robbed);
            blockades = Math.max(0, blocked);
            losses = Math.max(0, lost);
        }
    }

    private final long id;
    private final UUID attacker;
    private final UUID defender;
    private final WarGoal goal;
    private final long declared;
    private final long starts;
    private Phase phase = Phase.PREPARING;
    private final Set<UUID> attackerAllies = new LinkedHashSet<>();
    private final Set<UUID> defenderAllies = new LinkedHashSet<>();
    /** Allies called who have not answered yet, with the side they are called to. */
    private final Map<UUID, Side> calls = new LinkedHashMap<>();
    private final double[] score = new double[2];
    private final double[] exhaustion = new double[2];
    private final Tally[] tallies = {new Tally(), new Tally()};
    private boolean brokePact;
    private long ended;
    private Result result = Result.NONE;
    private PeaceTerms terms = PeaceTerms.WHITE;
    /** The game day exhaustion last grew for time. */
    private long lastDay;

    public War(long id, UUID attacker, UUID defender, WarGoal goal, long declared, long starts) {
        this.id = id;
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.defender = Objects.requireNonNull(defender, "defender");
        this.goal = Objects.requireNonNull(goal, "goal");
        if (id < 1 || attacker.equals(defender) || starts < declared) {
            throw new IllegalArgumentException("War out of range: #" + id);
        }
        this.declared = declared;
        this.starts = starts;
    }

    public long id() {
        return id;
    }

    public UUID attacker() {
        return attacker;
    }

    public UUID defender() {
        return defender;
    }

    public WarGoal goal() {
        return goal;
    }

    public long declared() {
        return declared;
    }

    public long starts() {
        return starts;
    }

    public Phase phase() {
        return phase;
    }

    public boolean over() {
        return phase == Phase.OVER;
    }

    public boolean active() {
        return phase == Phase.ACTIVE;
    }

    public UUID leader(Side side) {
        return side == Side.ATTACKER ? attacker : defender;
    }

    public Set<UUID> allies(Side side) {
        return Collections.unmodifiableSet(side == Side.ATTACKER ? attackerAllies : defenderAllies);
    }

    public Map<UUID, Side> calls() {
        return Collections.unmodifiableMap(calls);
    }

    /**
     * The side a kingdom fights on, if it fights in this war.
     */
    public Optional<Side> sideOf(UUID kingdom) {
        if (attacker.equals(kingdom) || attackerAllies.contains(kingdom)) {
            return Optional.of(Side.ATTACKER);
        }
        if (defender.equals(kingdom) || defenderAllies.contains(kingdom)) {
            return Optional.of(Side.DEFENDER);
        }
        return Optional.empty();
    }

    /**
     * Every kingdom of a side: the leader first, then its allies.
     */
    public Set<UUID> members(Side side) {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(leader(side));
        all.addAll(allies(side));
        return all;
    }

    /**
     * Whether two kingdoms fight each other in this war (not over).
     */
    public boolean opposes(UUID a, UUID b) {
        if (over()) {
            return false;
        }
        Optional<Side> first = sideOf(a);
        Optional<Side> second = sideOf(b);
        return first.isPresent() && second.isPresent() && first.get() != second.get();
    }

    public double score(Side side) {
        return score[side.index()];
    }

    public double exhaustion(Side side) {
        return exhaustion[side.index()];
    }

    public Tally tally(Side side) {
        return tallies[side.index()];
    }

    /**
     * The lead of a side in score over the other, from -100 to 100.
     */
    public double lead(Side side) {
        return score(side) - score(side.enemy());
    }

    public boolean brokePact() {
        return brokePact;
    }

    public long ended() {
        return ended;
    }

    public Result result() {
        return result;
    }

    public PeaceTerms terms() {
        return terms;
    }

    public long lastDay() {
        return lastDay;
    }

    // ---------------------------------------------------------------- changes, through the state

    void join(UUID kingdom, Side side) {
        if (kingdom.equals(attacker) || kingdom.equals(defender)) {
            return;
        }
        calls.remove(kingdom);
        (side == Side.ATTACKER ? defenderAllies : attackerAllies).remove(kingdom);
        (side == Side.ATTACKER ? attackerAllies : defenderAllies).add(kingdom);
    }

    void call(UUID kingdom, Side side) {
        if (sideOf(kingdom).isEmpty()) {
            calls.put(kingdom, side);
        }
    }

    Optional<Side> uncall(UUID kingdom) {
        return Optional.ofNullable(calls.remove(kingdom));
    }

    void leave(UUID kingdom) {
        attackerAllies.remove(kingdom);
        defenderAllies.remove(kingdom);
        calls.remove(kingdom);
    }

    void start() {
        if (phase == Phase.PREPARING) {
            phase = Phase.ACTIVE;
        }
    }

    /**
     * Adds points to the score of a side; the score stays between 0 and {@link #MAX}.
     *
     * @return the points actually added
     */
    double gain(Side side, double points) {
        double before = score[side.index()];
        score[side.index()] = clamp(before + points);
        return score[side.index()] - before;
    }

    double exhaust(Side side, double points) {
        double before = exhaustion[side.index()];
        exhaustion[side.index()] = clamp(before + points);
        return exhaustion[side.index()] - before;
    }

    void markBrokePact() {
        brokePact = true;
    }

    void setLastDay(long day) {
        lastDay = day;
    }

    void end(long now, Result how, PeaceTerms value) {
        phase = Phase.OVER;
        ended = now;
        result = Objects.requireNonNull(how, "how");
        terms = Objects.requireNonNull(value, "value");
        calls.clear();
    }

    /**
     * Restores a war from saved data.
     */
    public void restore(Phase savedPhase, double[] savedScore, double[] savedExhaustion, boolean pact, long endedAt, Result savedResult,
                        PeaceTerms savedTerms, long day) {
        phase = Objects.requireNonNull(savedPhase, "savedPhase");
        for (int i = 0; i < 2; i++) {
            score[i] = clamp(savedScore[i]);
            exhaustion[i] = clamp(savedExhaustion[i]);
        }
        brokePact = pact;
        ended = endedAt;
        result = Objects.requireNonNull(savedResult, "savedResult");
        terms = Objects.requireNonNull(savedTerms, "savedTerms");
        lastDay = day;
    }

    public void restoreAlly(UUID kingdom, Side side) {
        join(kingdom, side);
    }

    public void restoreCall(UUID kingdom, Side side) {
        call(kingdom, side);
    }

    static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(MAX, value));
    }
}
