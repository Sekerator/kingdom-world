package io.github.sekerator.kingdomworld.core.tactics;

/**
 * What the leader of a squad gives a fighter to do in a fight.
 */
public enum Role {
    /** Takes its sector of the ring around the target and strikes it from there. */
    ENCIRCLE,
    /** Holds the front against the enemy, shield first, and covers those behind it. */
    HOLD_FRONT,
    /** Goes round the enemy to its side or its back and strikes from there. */
    FLANK,
    /** Shoots from its place, at a distance, with a free line of fire. */
    SHOOT,
    /** Stands by the wounded and covers them. */
    GUARD_WOUNDED,
    /** Wounded: falls back behind the front and waits to be healed. */
    FALL_BACK,
    /** Heals the wounded behind the front. */
    HEAL,
    /** Holds a way out of the narrow place the target stands in instead of going in one by one. */
    HOLD_EXIT,
    /** Waits at the foot of a place it cannot climb, for the target to come down or the archers to bring it down. */
    WAIT_BELOW,
    /** Falls back with the squad in order, towards help. */
    RETREAT,
    /** Lies in wait where it is, unseen, until the leader gives the sign. */
    AMBUSH;

    /** Whether the fighter strikes or shoots its target in this role, and does not only stand. */
    public boolean attacks() {
        return this == ENCIRCLE || this == HOLD_FRONT || this == FLANK || this == SHOOT || this == GUARD_WOUNDED || this == HOLD_EXIT
                || this == WAIT_BELOW;
    }
}
