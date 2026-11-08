package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Objects;
import java.util.UUID;

/**
 * Something the day of the nobility brought that the world has to act on or tell about.
 *
 * @param house the house it is about, 0 if none
 * @param id    the demand, plot or spy it is about, 0 if none
 * @param other the other kingdom (the target of a spy, the backer of a plot, the kingdom of the other house), or {@code null}
 * @param value a number that goes with it (the task of a spy as its ordinal, the city of a fief)
 */
public record Happening(Kind kind, UUID kingdomId, long house, long id, UUID other, long value) {
    public enum Kind {
        /** A party made a demand. */
        DEMAND_MADE,
        /** A demand was not answered in time. */
        DEMAND_LAPSED,
        /** A house began to plot (the crown does not know). */
        PLOT_STARTED,
        /** Counter-intelligence found a plot out. */
        PLOT_DISCOVERED,
        /** A plot came to nothing: the house is content again. */
        PLOT_FIZZLED,
        /** A plot overthrew the ruler of a kingdom of the world; the head of the house rules now. */
        RULER_OVERTHROWN,
        /** A plot against a king of players: rebels keep part of the income for a while. */
        REBELS,
        /** A plot against a king of players: the city of the house leaves the kingdom ({@code value} is the city). */
        SECESSION,
        /** The crown of players is warned that a city may leave if nothing changes. */
        SECESSION_WARNING,
        /** The head of a house died; the heir took over. */
        HEAD_DIED,
        /** Two houses of two kingdoms married. */
        MARRIAGE,
        /** A spy did the mission ({@code value} the task). */
        SPY_DONE,
        /** A spy failed and came back. */
        SPY_FAILED,
        /** A spy was caught and is held for ransom by {@code other}. */
        SPY_CAUGHT,
        /** A caught spy was put to death by {@code other}. */
        SPY_EXECUTED
    }

    public Happening {
        Objects.requireNonNull(kind, "kind");
    }
}
