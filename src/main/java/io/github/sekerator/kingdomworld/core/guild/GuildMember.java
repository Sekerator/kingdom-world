package io.github.sekerator.kingdomworld.core.guild;

import java.util.Objects;
import java.util.UUID;

/**
 * A player in the guild. The rank holds at every branch; the points come from contracts and from monsters killed, and a
 * rank needs both its points and a passed exam.
 *
 * @param joined          game time of joining
 * @param kills           monsters killed for points
 * @param contracts       contracts done
 * @param examCooldownEnd game time from which the next exam may be taken, after a failed one
 */
public record GuildMember(UUID player, Rank rank, long points, long joined, int kills, int contracts, long examCooldownEnd) {
    /** Most points anybody gathers. */
    public static final long MAX_POINTS = 1_000_000_000_000L;

    public GuildMember {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rank, "rank");
        if (points < 0 || points > MAX_POINTS || kills < 0 || contracts < 0) {
            throw new IllegalArgumentException("Guild member out of range: " + player);
        }
    }

    public static GuildMember joined(UUID player, long now) {
        return new GuildMember(player, Rank.F, 0, now, 0, 0, 0);
    }

    public GuildMember withRank(Rank value) {
        return new GuildMember(player, value, points, joined, kills, contracts, examCooldownEnd);
    }

    /**
     * Points added, or taken away for a negative amount; never below 0.
     */
    public GuildMember plusPoints(long amount) {
        long next = Math.max(0, Math.min(MAX_POINTS, points + amount));
        return new GuildMember(player, rank, next, joined, kills, contracts, examCooldownEnd);
    }

    public GuildMember plusKill() {
        return new GuildMember(player, rank, points, joined, kills == Integer.MAX_VALUE ? kills : kills + 1, contracts, examCooldownEnd);
    }

    public GuildMember plusContract() {
        return new GuildMember(player, rank, points, joined, kills, contracts == Integer.MAX_VALUE ? contracts : contracts + 1,
                examCooldownEnd);
    }

    public GuildMember withExamCooldown(long until) {
        return new GuildMember(player, rank, points, joined, kills, contracts, until);
    }
}
