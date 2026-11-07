package io.github.sekerator.kingdomworld.core.citizen;

/**
 * The professions of the base game and the registry the game uses.
 */
public final class Professions {
    /** No work; the default for newcomers. */
    public static final Profession UNEMPLOYED = new Profession("unemployed", null);
    /** Harvests and replants crops around a composter and brings the harvest to a storehouse. */
    public static final Profession FARMER = new Profession("farmer", SiteKinds.COMPOSTER);
    /** Fells natural trees in the zone of a work post and plants saplings in their place. */
    public static final Profession LUMBERJACK = new Profession("lumberjack", SiteKinds.WORK_POST);
    /** Digs tunnels below a work post: natural stone, earth, gravel and ores. */
    public static final Profession MINER = new Profession("miner", SiteKinds.WORK_POST);
    /** Fishes in the water of the zone of a work post. */
    public static final Profession FISHER = new Profession("fisher", SiteKinds.WORK_POST);
    /** Keeps a herd of one kind of animal in the zone of a work post: breeds, culls and shears. */
    public static final Profession HERDER = new Profession("herder", SiteKinds.WORK_POST);
    /** Carries goods from the containers of work posts to storehouses, and between storehouses. */
    public static final Profession CARRIER = new Profession("carrier", SiteKinds.WORK_POST);

    /** The registry of the running game; more professions are registered here. */
    public static final ProfessionRegistry REGISTRY = ProfessionRegistry.withDefaults();

    private Professions() {
    }

    /**
     * Whether a citizen of the profession may work at a work post: every profession with a workplace may, the
     * farmer as well as the professions that work only at posts.
     */
    public static boolean worksAtPosts(String professionId) {
        return REGISTRY.byId(professionId).map(Profession::needsWorkplace).orElse(false);
    }

    /**
     * Professions a work post can be set to, in registration order.
     */
    public static java.util.List<Profession> postProfessions() {
        java.util.List<Profession> result = new java.util.ArrayList<>();
        for (Profession profession : REGISTRY.all()) {
            if (profession.needsWorkplace()) {
                result.add(profession);
            }
        }
        return result;
    }
}
