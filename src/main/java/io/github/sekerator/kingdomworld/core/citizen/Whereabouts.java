package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;
import java.util.Objects;

/**
 * Where a citizen is and whether it went too far: the place of its body near players, or the last place the data knows far from
 * them; the side of the world and the distance from a player; whether it is far from its home or its post; and whether it may be
 * sent back.
 */
public final class Whereabouts {
    /** A body walks about its home this far, in blocks. */
    public static final int HOME_RANGE = 32;
    /** Farther than this from its home a citizen is far from home: twice the reach of its walks. */
    public static final int HOME_REACH = 2 * HOME_RANGE;
    /** A post whose zone is small still lets its workers go this far before they are far from it. */
    public static final int MIN_POST_REACH = HOME_RANGE;
    /** Distance of a place in another world or not known. */
    public static final int FAR = -1;

    private Whereabouts() {
    }

    /**
     * The eight sides of the world, north being towards smaller z.
     */
    public enum Compass {
        NORTH,
        NORTH_EAST,
        EAST,
        SOUTH_EAST,
        SOUTH,
        SOUTH_WEST,
        WEST,
        NORTH_WEST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The angle of the side in degrees, clockwise from north.
         */
        public int degrees() {
            return ordinal() * 45;
        }
    }

    /**
     * The side of the world a place lies on from another, by the difference of their coordinates.
     */
    public static Compass compass(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return Compass.NORTH;
        }
        // Clockwise from north: north is -z, east is +x.
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(((degrees % 360) + 360) % 360 / 45.0) % 8;
        return Compass.values()[index];
    }

    /**
     * The distance over the ground between two places of one world, in whole blocks; {@link #FAR} between worlds.
     */
    public static int distance(WorldPos from, WorldPos to) {
        if (from == null || to == null || !from.dimension().equals(to.dimension())) {
            return FAR;
        }
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /**
     * Where a citizen is: the place of its body when it has one, the last place the data knows otherwise.
     *
     * @param live whether it is the place of a body that is there now
     */
    public record Seen(WorldPos pos, boolean live) {
        public Seen {
            Objects.requireNonNull(pos, "pos");
        }
    }

    public static Seen seen(WorldPos body, WorldPos last) {
        return body != null ? new Seen(body, true) : new Seen(last, false);
    }

    /**
     * Whether a citizen went farther than it should.
     */
    public enum Away {
        NONE,
        FROM_HOME,
        FROM_POST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How far from its post its workers may be: twice the zone of the post, at least {@link #MIN_POST_REACH}.
     */
    public static int postReach(int radius) {
        return Math.max(MIN_POST_REACH, 2 * Math.max(0, radius));
    }

    /**
     * Whether a citizen at a place is far from where it belongs: near its post (at work) or near its home it is not; otherwise it is
     * far from its home, or from its post if it has no home. One with neither is never far.
     *
     * @param post       its post, {@code null} if it has none
     * @param postRadius the radius of the zone of the post
     */
    public static Away away(WorldPos at, WorldPos home, WorldPos post, int postRadius) {
        boolean nearHome = home != null && within(at, home, HOME_REACH);
        boolean nearPost = post != null && within(at, post, postReach(postRadius));
        if (nearHome || nearPost) {
            return Away.NONE;
        }
        if (home != null) {
            return Away.FROM_HOME;
        }
        return post != null ? Away.FROM_POST : Away.NONE;
    }

    private static boolean within(WorldPos at, WorldPos place, int reach) {
        int distance = distance(at, place);
        return distance != FAR && distance <= reach;
    }

    /**
     * What keeps a citizen from being sent back.
     */
    public enum Hold {
        /** It may be sent back. */
        NONE,
        /** A soldier: the army leads it. */
        ARMY,
        /** On an expedition of the guild. */
        EXPEDITION,
        /** On the land of another kingdom: in captivity or at a siege there. */
        ABROAD,
        /** It has neither a home nor a post to go to. */
        NOWHERE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Whether a citizen may be sent back home or to its post, and if not, why: a soldier goes where the army leads it, a member of
     * an expedition comes back with it, one on the land of another kingdom is held there, and one with no home and no post has
     * nowhere to go.
     */
    public static Hold hold(boolean soldier, boolean expedition, boolean abroad, WorldPos home, WorldPos post) {
        if (soldier) {
            return Hold.ARMY;
        }
        if (expedition) {
            return Hold.EXPEDITION;
        }
        if (abroad) {
            return Hold.ABROAD;
        }
        return home == null && post == null ? Hold.NOWHERE : Hold.NONE;
    }

    /**
     * Where a citizen sent back goes: home, or its post if it has no home.
     */
    public static WorldPos target(WorldPos home, WorldPos post) {
        return home != null ? home : post;
    }
}
