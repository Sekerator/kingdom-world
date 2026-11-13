package io.github.sekerator.kingdomworld.core.tactics;

/**
 * The numbers of the tactics of squads: how often a leader decides, how far the ring and the flanks are, how archers keep
 * their distance, when the wounded fall back and when the squad does, and the bonus of a blow from the side or the back.
 */
public final class TacticRules {
    public static final TacticRules DEFAULT = new Builder().build();

    /** Ticks between two decisions of a leader by its skill: a fighter, a sergeant, a vice-captain, a captain. */
    private final int[] decideTicks;
    /** Ticks a squad takes to answer an enemy it has just seen. */
    public final int reactionTicks;
    /** Blocks between a target and the ring of blades around it. */
    public final double ringRadius;
    /** Blocks between a target in a narrow place and the fighters holding the ways out of it. */
    public final double exitRadius;
    /** Blocks from the enemy the wings of a pincer go to, on its sides. */
    public final double flankRadius;
    /** The distance archers keep from their target, at least and at most, in blocks. */
    public final double archerMin;
    public final double archerMax;
    /** An enemy closer than this to an archer makes it step back. */
    public final double archerTooClose;
    /** Blocks behind the front the wounded fall back to. */
    public final double fallBack;
    /** A fighter with less than this share of its health falls back. */
    public final double woundedShare;
    /** A squad that lost this share of its fighters, or whose worth is less than this share of the enemy's, falls back. */
    public final double retreatLosses;
    public final double retreatBalance;
    /** A squad splits into a front and wings against at least this many enemies, if it has twice as many fighters. */
    public final int pincerFoes;
    /** The further damage of a blow from the side and from the back, as a share. */
    public final double flankBonus;
    public final double rearBonus;
    /** Microseconds the decisions of all squads may take in one tick. */
    public final int budgetMicros;
    /** Blocks a squad sees its enemies at. */
    public final double sight;

    private TacticRules(Builder b) {
        if (b.decideTicks.length != 4) {
            throw new IllegalArgumentException("The ticks of decisions need 4 values");
        }
        decideTicks = new int[4];
        for (int i = 0; i < 4; i++) {
            decideTicks[i] = Math.max(1, b.decideTicks[i]);
        }
        reactionTicks = Math.max(0, b.reactionTicks);
        ringRadius = Math.max(1.0, b.ringRadius);
        exitRadius = Math.max(ringRadius, b.exitRadius);
        flankRadius = Math.max(ringRadius, b.flankRadius);
        archerMin = Math.max(2.0, b.archerMin);
        archerMax = Math.max(archerMin, b.archerMax);
        archerTooClose = Math.max(1.0, Math.min(archerMin, b.archerTooClose));
        fallBack = Math.max(1.0, b.fallBack);
        woundedShare = clamp(b.woundedShare);
        retreatLosses = clamp(b.retreatLosses);
        retreatBalance = clamp(b.retreatBalance);
        pincerFoes = Math.max(2, b.pincerFoes);
        flankBonus = Math.max(0.0, Math.min(2.0, b.flankBonus));
        rearBonus = Math.max(0.0, Math.min(2.0, b.rearBonus));
        budgetMicros = Math.max(100, b.budgetMicros);
        sight = Math.max(4.0, b.sight);
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    /** Ticks between two decisions of a leader of a skill (0 to 3). */
    public int decideTicks(int skill) {
        return decideTicks[Math.max(0, Math.min(3, skill))];
    }

    /**
     * How much harder a blow lands by where it comes from: the angle between where the struck one looks and the way to the
     * striker, in degrees (0 in front, 180 behind). In front it is plain, from the side and from the back it hits harder.
     */
    public double sideMultiplier(double degrees) {
        double angle = Math.abs(degrees) % 360.0;
        if (angle > 180.0) {
            angle = 360.0 - angle;
        }
        if (angle >= 120.0) {
            return 1.0 + rearBonus;
        }
        return angle >= 60.0 ? 1.0 + flankBonus : 1.0;
    }

    public static final class Builder {
        public int[] decideTicks = {20, 15, 12, 10};
        public int reactionTicks = 8;
        public double ringRadius = 2.2;
        public double exitRadius = 4.5;
        public double flankRadius = 4.0;
        public double archerMin = 8.0;
        public double archerMax = 15.0;
        public double archerTooClose = 4.0;
        public double fallBack = 6.0;
        /** As citizens count as wounded: below half of their health. */
        public double woundedShare = 0.5;
        public double retreatLosses = 0.5;
        public double retreatBalance = 0.4;
        public int pincerFoes = 2;
        public double flankBonus = 0.15;
        public double rearBonus = 0.3;
        public int budgetMicros = 3_000;
        public double sight = 24.0;

        public TacticRules build() {
            return new TacticRules(this);
        }
    }
}
