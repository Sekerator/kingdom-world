package io.github.sekerator.kingdomworld.core.citizen;

/**
 * The professions of the base game and the registry the game uses.
 */
public final class Professions {
    /** No work; the default for newcomers. */
    public static final Profession UNEMPLOYED = new Profession("unemployed", null);
    /** Harvests and replants crops around a composter and brings the harvest to a storehouse. */
    public static final Profession FARMER = new Profession("farmer", SiteKinds.COMPOSTER);

    /** The registry of the running game; more professions are registered here. */
    public static final ProfessionRegistry REGISTRY = ProfessionRegistry.withDefaults();

    private Professions() {
    }
}
