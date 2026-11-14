package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a citizen is: the side of the world and the distance from a player, whether it went far from its home or post, the place of
 * its body or the last place the data knows, and who may be sent back where.
 */
class WhereaboutsTest {
    private static final String WORLD = "minecraft:overworld";

    private static WorldPos at(int x, int z) {
        return new WorldPos(WORLD, x, 64, z);
    }

    @Test
    void theSideOfTheWorldIsTakenWithNorthTowardsSmallerZ() {
        assertEquals(Whereabouts.Compass.NORTH, Whereabouts.compass(0, -10));
        assertEquals(Whereabouts.Compass.NORTH_EAST, Whereabouts.compass(10, -10));
        assertEquals(Whereabouts.Compass.EAST, Whereabouts.compass(10, 0));
        assertEquals(Whereabouts.Compass.SOUTH_EAST, Whereabouts.compass(7, 7));
        assertEquals(Whereabouts.Compass.SOUTH, Whereabouts.compass(0, 30));
        assertEquals(Whereabouts.Compass.SOUTH_WEST, Whereabouts.compass(-5, 5));
        assertEquals(Whereabouts.Compass.WEST, Whereabouts.compass(-30, 2));
        assertEquals(Whereabouts.Compass.NORTH_WEST, Whereabouts.compass(-9, -10));
        // Nearly north is north: the sides split the circle in eighths.
        assertEquals(Whereabouts.Compass.NORTH, Whereabouts.compass(3, -100));
        assertEquals(Whereabouts.Compass.NORTH, Whereabouts.compass(0, 0));
    }

    @Test
    void theDistanceIsOverTheGroundAndOnlyInOneWorld() {
        assertEquals(312, Whereabouts.distance(at(0, 0), new WorldPos(WORLD, 312, 10, 0)), "the height does not count");
        assertEquals(5, Whereabouts.distance(at(0, 0), at(3, 4)));
        assertEquals(Whereabouts.FAR, Whereabouts.distance(at(0, 0), new WorldPos("minecraft:the_nether", 0, 64, 0)));
        assertEquals(Whereabouts.FAR, Whereabouts.distance(null, at(0, 0)));
    }

    @Test
    void aCitizenIsFarFromHomeBeyondTwiceTheReachOfItsWalks() {
        WorldPos home = at(0, 0);
        assertEquals(Whereabouts.Away.NONE, Whereabouts.away(at(Whereabouts.HOME_REACH, 0), home, null, 0));
        assertEquals(Whereabouts.Away.FROM_HOME, Whereabouts.away(at(Whereabouts.HOME_REACH + 1, 0), home, null, 0));
        // At its post far from home it is at work, not gone.
        WorldPos post = at(300, 0);
        assertEquals(Whereabouts.Away.NONE, Whereabouts.away(at(310, 5), home, post, 8));
        assertEquals(Whereabouts.Away.FROM_HOME, Whereabouts.away(at(150, 0), home, post, 8), "between home and post, far from both");
        // A post reaches twice its zone, at least the reach of the walks around a home.
        assertEquals(Whereabouts.MIN_POST_REACH, Whereabouts.postReach(4));
        assertEquals(80, Whereabouts.postReach(40));
        assertEquals(Whereabouts.Away.FROM_POST, Whereabouts.away(at(300 + 81, 0), null, post, 40), "no home: far from the post");
        assertEquals(Whereabouts.Away.NONE, Whereabouts.away(at(300 + 80, 0), null, post, 40));
        assertEquals(Whereabouts.Away.NONE, Whereabouts.away(at(5_000, 0), null, null, 0), "with neither a citizen is never far");
        assertEquals(Whereabouts.Away.FROM_HOME, Whereabouts.away(new WorldPos("minecraft:the_nether", 0, 64, 0), home, null, 0),
                "another world is far");
    }

    @Test
    void theBodyIsWhereItIsAndTheDataWhereItWasLast() {
        WorldPos body = at(10, 10);
        WorldPos last = at(-50, 3);
        Whereabouts.Seen live = Whereabouts.seen(body, last);
        assertEquals(body, live.pos());
        assertTrue(live.live());
        Whereabouts.Seen known = Whereabouts.seen(null, last);
        assertEquals(last, known.pos());
        assertFalse(known.live(), "without a body it is the last place known");
    }

    @Test
    void soldiersMembersOfExpeditionsAndThoseAbroadAreNotSentBack() {
        WorldPos home = at(0, 0);
        WorldPos post = at(50, 0);
        assertEquals(Whereabouts.Hold.NONE, Whereabouts.hold(false, false, false, home, post));
        assertEquals(Whereabouts.Hold.ARMY, Whereabouts.hold(true, false, false, home, post));
        assertEquals(Whereabouts.Hold.EXPEDITION, Whereabouts.hold(false, true, false, home, post));
        assertEquals(Whereabouts.Hold.ABROAD, Whereabouts.hold(false, false, true, home, post));
        assertEquals(Whereabouts.Hold.NOWHERE, Whereabouts.hold(false, false, false, null, null));
        assertEquals(Whereabouts.Hold.NONE, Whereabouts.hold(false, false, false, null, post), "a post is somewhere to go");
        assertEquals(home, Whereabouts.target(home, post), "home first");
        assertEquals(post, Whereabouts.target(null, post));
    }

    @Test
    void onlyACitizenWithNothingToDoWalksBackByItself() {
        assertTrue(Whereabouts.walksBack(Whereabouts.Away.FROM_HOME, Whereabouts.Hold.NONE, false, true), "after its hours of work");
        assertTrue(Whereabouts.walksBack(Whereabouts.Away.FROM_POST, Whereabouts.Hold.NONE, true, false), "without work of its own");
        assertFalse(Whereabouts.walksBack(Whereabouts.Away.FROM_HOME, Whereabouts.Hold.NONE, true, true),
                "a carrier or a worker between its rounds keeps to its work");
        assertFalse(Whereabouts.walksBack(Whereabouts.Away.NONE, Whereabouts.Hold.NONE, false, false), "near home it stays");
        for (Whereabouts.Hold hold : Whereabouts.Hold.values()) {
            if (hold != Whereabouts.Hold.NONE) {
                assertFalse(Whereabouts.walksBack(Whereabouts.Away.FROM_HOME, hold, false, false), "held: " + hold);
            }
        }
    }

    @Test
    void aWalkBackGoesOnHomeUntilWorkOrAHoldComesUp() {
        // No longer far is no reason to stop: the walks about home hold a body only close to it.
        assertTrue(Whereabouts.walksOn(Whereabouts.Hold.NONE, false, false), "on the way home");
        assertTrue(Whereabouts.walksOn(Whereabouts.Hold.NONE, true, false), "without work of its own");
        assertFalse(Whereabouts.walksOn(Whereabouts.Hold.NONE, true, true), "its hours of work began");
        for (Whereabouts.Hold hold : Whereabouts.Hold.values()) {
            if (hold != Whereabouts.Hold.NONE) {
                assertFalse(Whereabouts.walksOn(hold, false, false), "held: " + hold);
            }
        }
    }
}
