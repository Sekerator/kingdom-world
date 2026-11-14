package io.github.sekerator.kingdomworld.core.road;

/**
 * How the cities of a kingdom of players hold their people beyond the named citizens: the townsfolk, who live as a number
 * of their city, eat, pay and grow, and have no body and no record of their own.
 *
 * @param namedPerCity named citizens a city takes before its newcomers stay townsfolk; 0 never makes townsfolk
 * @param folkPerBed   townsfolk one free bed of a city holds, a family
 * @param folkDensity  people one block of the square of a city holds at most when built densely: a city whose people need
 *                     more room than its level gives grows its square by them, without an upper limit
 * @param folkDues     coins one of the townsfolk pays into the treasury a day
 */
public record FolkRules(int namedPerCity, int folkPerBed, int folkDensity, long folkDues) {
    public static final FolkRules DEFAULT = new FolkRules(64, 4, 8, 1);

    public FolkRules {
        if (namedPerCity < 0 || folkPerBed < 1 || folkDensity < 1 || folkDues < 0) {
            throw new IllegalArgumentException("Folk rules out of range");
        }
    }
}
