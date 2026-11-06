package io.github.sekerator.kingdomworld.core.citizen;

/**
 * Ids of the kinds of places citizens use. Workplaces of professions add their own kinds.
 */
public final class SiteKinds {
    /** A bed: the home of one citizen. */
    public static final String BED = "bed";
    /** An infirmary block, where wounded citizens heal faster. */
    public static final String INFIRMARY = "infirmary";
    /** A composter: the workplace of one farmer. */
    public static final String COMPOSTER = "composter";

    private SiteKinds() {
    }
}
