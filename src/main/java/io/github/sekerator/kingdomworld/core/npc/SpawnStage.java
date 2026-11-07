package io.github.sekerator.kingdomworld.core.npc;

import java.util.Locale;
import java.util.Optional;

/**
 * The stages of the appearance of a kingdom of the world, in order. Every stage works in steps of its own budget over ticks;
 * the place remembers the stage and the step, and every step looks at the world first, so that a restart in the middle goes
 * on from where it stopped without doing anything twice.
 */
public enum SpawnStage {
    /** The chunks of the land, the towns and the ways between them are loaded (generated off the main thread if new). */
    LOAD,
    /** The land is looked at again: nobody else's land and no traces of players. */
    CHECK,
    /** The throne and the kingdom with its land, its ruler and its towns' land. */
    FOUND,
    /** The square of the throne and the plots of the capital are levelled and cleared of trees. */
    GROUND,
    /** The buildings of the capital, one after another. */
    BUILD,
    /** Fields, the mint, the desk of the guild and the posts of work. */
    WORK,
    /** The towns: their squares, town halls, houses and wells. */
    TOWNS,
    /** The beds and posts of the new buildings are counted. */
    PLACES,
    /** The treasury, the storehouses and the market. */
    WEALTH,
    /** Citizens with homes and work. */
    PEOPLE,
    /** Soldiers, their gear and their orders. */
    ARMY,
    /** Roads from the capital to its towns. */
    ROADS,
    /** The wall of the capital with its gates. */
    WALL,
    /** The laws of the ruler and the guards of the gates of the capital and of the towns. */
    LAW,
    /** Done. */
    DONE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SpawnStage> byId(String id) {
        for (SpawnStage stage : values()) {
            if (stage.id().equals(id)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }
}
