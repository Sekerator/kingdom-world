package io.github.sekerator.kingdomworld.core.guild;

import java.util.Objects;
import java.util.UUID;

/**
 * A citizen with the profession of an adventurer, as the guild knows it: rank and points. Citizens rise through the ranks
 * by points alone, without exams. Experience is the citizen's own.
 *
 * @param kills       monsters killed, near players and on expeditions
 * @param expeditions expeditions returned from
 */
public record Hireling(UUID citizenId, UUID kingdomId, Rank rank, long points, int kills, int expeditions) {
    public Hireling {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(rank, "rank");
        if (points < 0 || points > GuildMember.MAX_POINTS || kills < 0 || expeditions < 0) {
            throw new IllegalArgumentException("Adventurer out of range: " + citizenId);
        }
    }

    public static Hireling recruit(UUID citizenId, UUID kingdomId) {
        return new Hireling(citizenId, kingdomId, Rank.F, 0, 0, 0);
    }

    /**
     * Points and kills added; the rank follows the points.
     */
    public Hireling earned(long morePoints, int moreKills, int moreExpeditions, GuildRules rules) {
        long next = Math.max(0, Math.min(GuildMember.MAX_POINTS, points + morePoints));
        Rank reached = rank;
        while (reached.next().isPresent() && next >= rules.rankPoints(reached.next().get())) {
            reached = reached.next().get();
        }
        return new Hireling(citizenId, kingdomId, reached, next, (int) Math.min(Integer.MAX_VALUE, (long) kills + Math.max(0, moreKills)),
                (int) Math.min(Integer.MAX_VALUE, (long) expeditions + Math.max(0, moreExpeditions)));
    }

    public Hireling withRank(Rank value) {
        return new Hireling(citizenId, kingdomId, value, points, kills, expeditions);
    }
}
