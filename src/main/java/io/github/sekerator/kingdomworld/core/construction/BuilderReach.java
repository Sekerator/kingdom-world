package io.github.sekerator.kingdomworld.core.construction;

/**
 * Which constructions the builders of a post take: those within the radius of builders of the post, as always, and, for a post
 * in a city, every construction whose middle lies in the same city, however far. The city only adds constructions: one just
 * beyond the square of a city stays with the builders near it. The storehouses of a city follow the same rule.
 */
public final class BuilderReach {
    /** The city of a place outside every city. */
    public static final long NO_CITY = Long.MIN_VALUE;

    private BuilderReach() {
    }

    /**
     * Whether the builders of a post take a construction.
     *
     * @param postCity    the city the post stands in, or {@link #NO_CITY}
     * @param siteCity    the city the middle of the construction lies in, or {@link #NO_CITY}
     * @param distanceSqr the squared distance from the post to the nearest block of the construction
     * @param radius      the radius of builders of the server
     */
    public static boolean reaches(long postCity, long siteCity, double distanceSqr, int radius) {
        return distanceSqr <= (double) radius * radius || postCity != NO_CITY && postCity == siteCity;
    }
}
