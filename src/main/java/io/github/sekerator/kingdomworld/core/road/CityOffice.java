package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;

/**
 * Who may govern a city of a kingdom of players: a living citizen of the kingdom who is no soldier, governs no other city and
 * heads no other house of the nobility, and whose post, if it has one, stands in that city (its home follows its post, so a
 * mayor working in another city would move back and forth). A home outside the city takes nothing away: the citizen waits for
 * a free bed there. Pure, so that the server and the tests share it.
 */
public final class CityOffice {
    private CityOffice() {
    }

    /**
     * Why a citizen may not take or keep the office, {@link #FIT} if it may.
     */
    public enum Unfit {
        FIT,
        /** It died or is gone. */
        GONE,
        /** It is a citizen of another kingdom. */
        OTHER_KINGDOM,
        /** It serves in the army. */
        SOLDIER,
        /** It works at a post in another city, or outside every city. */
        POST_ELSEWHERE,
        /** It is the mayor of another city. */
        MAYOR_ELSEWHERE,
        /** It heads a house of the nobility. */
        HEAD_OF_HOUSE,
        /** No city was given and none could be told by its post or home. */
        NO_CITY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What is known of a citizen.
     *
     * @param alive       whether it lives
     * @param ownKingdom  whether it is a citizen of the kingdom of the city
     * @param soldier     whether it serves in the army
     * @param post        whether it works at a post
     * @param postCity    the city whose square holds its post (0 the capital), -1 for a post outside every city or none
     * @param mayorOf     the city it is the mayor of, -1 for none
     * @param houseOfCity the city its house of the nobility holds, -1 if it heads no house; -2 for a house without a city
     */
    public record Candidate(boolean alive, boolean ownKingdom, boolean soldier, boolean post, long postCity, long mayorOf, long houseOfCity) {
    }

    /**
     * Why a citizen may not become the mayor of a city.
     */
    public static Unfit mayor(Candidate candidate, long city) {
        Unfit common = common(candidate, city);
        if (common != Unfit.FIT) {
            return common;
        }
        if (candidate.mayorOf() >= 0 && candidate.mayorOf() != city) {
            return Unfit.MAYOR_ELSEWHERE;
        }
        return candidate.houseOfCity() != -1 ? Unfit.HEAD_OF_HOUSE : Unfit.FIT;
    }

    /**
     * Why a citizen may not become the baron of a city, the head of a new house: as for a mayor, and it heads no house yet. The
     * mayor of that city may become its baron.
     */
    public static Unfit baron(Candidate candidate, long city) {
        return mayor(candidate, city);
    }

    /**
     * Why a mayor or a baron loses its office in a city: it died or left the kingdom, it became a soldier or works at a post in
     * another city. A home elsewhere does not end it.
     */
    public static Unfit keep(Candidate candidate, long city) {
        return common(candidate, city);
    }

    private static Unfit common(Candidate candidate, long city) {
        if (city < 0) {
            return Unfit.NO_CITY;
        }
        if (!candidate.alive()) {
            return Unfit.GONE;
        }
        if (!candidate.ownKingdom()) {
            return Unfit.OTHER_KINGDOM;
        }
        if (candidate.soldier()) {
            return Unfit.SOLDIER;
        }
        if (candidate.post() && candidate.postCity() != city) {
            return Unfit.POST_ELSEWHERE;
        }
        return Unfit.FIT;
    }

    /**
     * The city a citizen would govern when none is given: that of its post, else that of its home; -1 for none.
     *
     * @param postCity the city of its post, -1 for none or outside every city
     * @param homeCity the city of its home, -1 for none or outside every city
     */
    public static long cityFor(boolean post, long postCity, long homeCity) {
        if (post) {
            return postCity;
        }
        return homeCity;
    }
}
