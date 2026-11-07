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
    /** Smelts ore and raw materials in the furnaces of the zone of a work post. */
    public static final Profession SMELTER = new Profession("smelter", SiteKinds.WORK_POST);
    /** Cooks food in smokers, furnaces and on campfires and bakes at the crafting table. */
    public static final Profession COOK = new Profession("cook", SiteKinds.WORK_POST);
    /** Makes things at the crafting table and the stonecutter. */
    public static final Profession CRAFTER = new Profession("crafter", SiteKinds.WORK_POST);
    /** Makes tools, weapons and armor at the crafting table and upgrades them at the smithing table. */
    public static final Profession SMITH = new Profession("smith", SiteKinds.WORK_POST);
    /** Strikes coins of the kingdom from metal of the storehouses at a mint in the zone of a work post. */
    public static final Profession MINTER = new Profession("minter", SiteKinds.WORK_POST);
    /** Keeps the market stalls in the zone of a work post open and sells surplus goods abroad. */
    public static final Profession MERCHANT = new Profession("merchant", SiteKinds.WORK_POST);
    /** Keeps the desks of the adventurers' guild in the zone of a work post open. */
    public static final Profession REGISTRAR = new Profession("registrar", SiteKinds.WORK_POST);
    /** Guards the land against monsters and goes on expeditions with a squad; needs no workplace. */
    public static final Profession ADVENTURER = new Profession("adventurer", null);

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
     * Whether the profession makes things at workstations from what is in the storehouses.
     */
    public static boolean isWorkshop(String professionId) {
        return SMELTER.id().equals(professionId) || COOK.id().equals(professionId) || CRAFTER.id().equals(professionId)
                || SMITH.id().equals(professionId);
    }

    /**
     * Whether the profession works at workstations in the zone of its post (the workshops and the minter): the post looks
     * at its zone again and again, and its screen shows the workstations.
     */
    public static boolean usesStations(String professionId) {
        return isWorkshop(professionId) || MINTER.id().equals(professionId) || MERCHANT.id().equals(professionId)
                || REGISTRAR.id().equals(professionId);
    }

    /**
     * Whether a workshop of the profession has an auto mode: work of its own when no order needs it.
     */
    public static boolean hasAutoMode(String professionId) {
        return SMELTER.id().equals(professionId) || COOK.id().equals(professionId);
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
