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
    /**
     * A work post: the workplace of the gathering professions (and of farmers who prefer it to a composter). Unlike
     * the other places a post has several seats; who works there is kept by {@link WorkPosts}.
     */
    public static final String WORK_POST = "work_post";

    private SiteKinds() {
    }
}
