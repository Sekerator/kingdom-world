package io.github.sekerator.kingdomworld.core.tactics;

/**
 * What a squad knows of the ground it fights on. The server answers from the blocks of the world.
 */
public interface Ground {
    /** Ground that does not know anything: every place is good. */
    Ground OPEN = new Ground() {
        @Override
        public Spot settle(Spot spot) {
            return spot;
        }
    };

    /**
     * The place a fighter can stand on near a wanted one: on solid ground at about that height, not in lava, fire or deep
     * water and not over a drop; {@code null} if there is none close by.
     */
    Spot settle(Spot spot);

    /**
     * How good a place is for shooting from, 0 or more: higher ground and cover are better.
     */
    default double vantage(Spot spot) {
        return 0.0;
    }
}
