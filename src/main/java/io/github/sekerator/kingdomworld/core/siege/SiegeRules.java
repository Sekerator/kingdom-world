package io.github.sekerator.kingdomworld.core.siege;

/**
 * Settings of sieges, captures and the mending of war damage, from the server config.
 *
 * @param woodGateHp        strength of gate leaves of planks
 * @param ironGateHp        strength of a portcullis of iron
 * @param soldierHit        strength a soldier at a gate takes off in a second
 * @param gateSlots         most soldiers that strike one gate at once
 * @param ramHit            strength a ram takes off in a second
 * @param maxRams           most rams that strike one gate at once
 * @param abstractShare     share of the strength of a squad that tells on a gate in a siege far from players
 * @param occupyTicks       game ticks attackers hold a chunk without defenders until it is occupied
 * @param plazaTicks        game ticks attackers hold the square at the town hall without defenders until the city is taken
 * @param throneTicks       game ticks attackers hold the throne hall without defenders until the throne is taken
 * @param plazaRadius       blocks around the town hall that make its square
 * @param throneRadius      blocks around the throne that make its hall
 * @param closeRadius       blocks from a gate within which an enemy closes it in war
 * @param openRadius        blocks from a gate within which a subject or an ally opens it
 * @param repairQuietTicks  game ticks without an enemy near after which builders mend war damage during a war; 0 waits for
 *                          the peace
 * @param explosions        whether explosions break blocks on open land of the enemy
 * @param maxBodies         most soldiers of a siege that walk as bodies near players
 * @param ramPlayerFactor   how much faster a player with a ram breaks gate leaves and wall blocks
 */
public record SiegeRules(int woodGateHp, int ironGateHp, double soldierHit, int gateSlots, double ramHit, int maxRams,
                         double abstractShare, long occupyTicks, long plazaTicks, long throneTicks, int plazaRadius, int throneRadius,
                         int closeRadius, int openRadius, long repairQuietTicks, boolean explosions, int maxBodies,
                         double ramPlayerFactor) {
    public static final SiegeRules DEFAULT = new SiegeRules(720, 1_800, 1.0, 6, 3.0, 2, 0.5, 3_600L, 6_000L, 6_000L, 12, 8, 24, 4,
            12_000L, true, 60, 4.0);

    public SiegeRules {
        if (woodGateHp < 1 || ironGateHp < 1 || !(soldierHit > 0) || gateSlots < 1 || !(ramHit >= 0) || maxRams < 0
                || !(abstractShare >= 0 && abstractShare <= 1) || occupyTicks < 20 || plazaTicks < 20 || throneTicks < 20 || plazaRadius < 2
                || throneRadius < 2 || closeRadius < 2 || openRadius < 1 || openRadius >= closeRadius || repairQuietTicks < 0 || maxBodies < 0
                || !(ramPlayerFactor >= 1)) {
            throw new IllegalArgumentException("Siege rules out of range");
        }
    }

    /**
     * The full strength of a gate of a material.
     */
    public int strength(GateMaterial material) {
        return material == GateMaterial.IRON ? ironGateHp : woodGateHp;
    }

    /**
     * Strength taken off a gate in a second by soldiers and rams standing at it.
     */
    public double hitPerSecond(int soldiers, int rams) {
        int strikers = Math.max(0, Math.min(soldiers, gateSlots));
        int used = strikers == 0 ? 0 : Math.max(0, Math.min(rams, maxRams));
        return strikers * soldierHit + used * ramHit;
    }

    /**
     * Seconds a whole gate of a material holds against soldiers and rams; {@link Long#MAX_VALUE} with nobody striking.
     */
    public long secondsToBreak(GateMaterial material, int soldiers, int rams) {
        double hit = hitPerSecond(soldiers, rams);
        if (hit <= 0) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(strength(material) / hit);
    }

    /**
     * Game ticks a hold of a kind needs. A province is held as long as the square of a city here; an empty one takes less,
     * see {@link #provinceTicks}.
     */
    public long ticks(Hold.Kind kind) {
        return switch (kind) {
            case CHUNK -> occupyTicks;
            case PLAZA, PROVINCE -> plazaTicks;
            case THRONE -> throneTicks;
        };
    }

    /**
     * Game ticks a hold of a province needs: as long as the square of a city where people live in it (the square of its main
     * settlement is held), as long as a chunk where nobody does (its boundary stone is held).
     *
     * @param settled whether the province has a main settlement that stands (not ruined)
     */
    public long provinceTicks(boolean settled) {
        return settled ? plazaTicks : occupyTicks;
    }

    public SiegeRules withTimes(long occupy, long plaza, long throne) {
        return new SiegeRules(woodGateHp, ironGateHp, soldierHit, gateSlots, ramHit, maxRams, abstractShare, occupy, plaza, throne, plazaRadius,
                throneRadius, closeRadius, openRadius, repairQuietTicks, explosions, maxBodies, ramPlayerFactor);
    }

    public SiegeRules withRepairQuiet(long ticks) {
        return new SiegeRules(woodGateHp, ironGateHp, soldierHit, gateSlots, ramHit, maxRams, abstractShare, occupyTicks, plazaTicks, throneTicks,
                plazaRadius, throneRadius, closeRadius, openRadius, ticks, explosions, maxBodies, ramPlayerFactor);
    }
}
