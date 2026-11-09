package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.Flank;

/**
 * How squads march, see and meet in the field far from players.
 *
 * @param marchSpeed    blocks a squad in line marches per second
 * @param roadBonus     share a road adds to the speed
 * @param sight         blocks a squad sees around it
 * @param archerSight   share archers add to the sight of their squad
 * @param postSight     blocks a tower or a wall of a kingdom sees around it
 * @param scoutSight    share a squad that scouts adds to its sight
 * @param fogSeconds    seconds between two looks of the fog of war
 * @param lastSeenTicks ticks a pale mark of a squad seen last stays on the map
 * @param spyTicks      ticks the squads of a kingdom stay in sight after a spy scouted it
 * @param engage        blocks between two squads at which a battle begins
 * @param battleSeconds seconds a battle far from players lasts before it is decided; squads may join it meanwhile
 * @param flank         how much stronger blows at the side and the rear of a squad in battle are
 */
public record FieldRules(double marchSpeed, double roadBonus, int sight, double archerSight, int postSight, double scoutSight,
                         int fogSeconds, long lastSeenTicks, long spyTicks, int engage, int battleSeconds, Flank.Rules flank) {
    public static final FieldRules DEFAULT = new FieldRules(3.0, 0.5, 64, 0.5, 96, 1.0, 5, 24_000, 12_000, 24, 30, Flank.Rules.DEFAULT);

    public FieldRules {
        if (!(marchSpeed > 0.0 && marchSpeed <= 100.0) || !(roadBonus >= 0.0 && roadBonus <= 5.0) || sight < 0 || !(archerSight >= 0.0)
                || postSight < 0 || !(scoutSight >= 0.0) || fogSeconds < 1 || lastSeenTicks < 0 || spyTicks < 0 || engage < 1
                || battleSeconds < 1 || flank == null) {
            throw new IllegalArgumentException("Field rules out of range");
        }
    }

    /**
     * The cost of a chunk with a road for the search of ways, by one without.
     */
    public double roadCost() {
        return 1.0 / (1.0 + roadBonus);
    }

    /**
     * The same rules with other numbers of the march and the battles, for game tests.
     */
    public FieldRules withMarch(double speed, int battle) {
        return new FieldRules(speed, roadBonus, sight, archerSight, postSight, scoutSight, fogSeconds, lastSeenTicks, spyTicks, engage, battle, flank);
    }
}
