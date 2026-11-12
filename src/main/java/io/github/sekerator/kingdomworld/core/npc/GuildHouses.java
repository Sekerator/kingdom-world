package io.github.sekerator.kingdomworld.core.npc;

import java.util.Optional;

/**
 * The rank of the guild house and the mage tower of a styled capital, decided once when its place is planned and kept with the
 * place, so that the plan made anew from the place stays the same when other kingdoms appear or go. A small kingdom has a guild
 * of rank B or A, a medium one A or S, a large one S, and the first large kingdom of a world the only guild of rank SS.
 *
 * <p>This is where the rank of a guild comes from until the guilds rank themselves in play; the buildings of a rank are
 * {@link TemplateCatalog#guildHouse(String, TemplateCatalog.Kind, TemplateCatalog.Rank)}.
 */
public final class GuildHouses {
    private GuildHouses() {
    }

    /**
     * The rank of the guild of a new capital of a level.
     *
     * @param ssTaken whether another kingdom of the world already holds the only guild of rank SS
     */
    public static TemplateCatalog.Rank rankOf(NpcLevel level, long seed, boolean ssTaken) {
        boolean higher = (mix(seed) >>> 17 & 1) == 1;
        return switch (level) {
            case SMALL -> higher ? TemplateCatalog.Rank.A : TemplateCatalog.Rank.B;
            case MEDIUM -> higher ? TemplateCatalog.Rank.S : TemplateCatalog.Rank.A;
            case LARGE -> ssTaken ? TemplateCatalog.Rank.S : TemplateCatalog.Rank.SS;
        };
    }

    /**
     * The rank of an id ({@code b}, {@code a}, {@code s}, {@code ss}), if it names one.
     */
    public static Optional<TemplateCatalog.Rank> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (TemplateCatalog.Rank rank : TemplateCatalog.Rank.values()) {
            if (rank.id().equals(id)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }

    /**
     * The rank of the guild of a place: the one it keeps, or the default of its level.
     */
    public static TemplateCatalog.Rank of(NpcSite site) {
        return parse(site.guildRank()).orElse(defaultOf(site.level()));
    }

    /**
     * The rank of the guild of a level when a place keeps none: B, A or S.
     */
    public static TemplateCatalog.Rank defaultOf(NpcLevel level) {
        return switch (level) {
            case SMALL -> TemplateCatalog.Rank.B;
            case MEDIUM -> TemplateCatalog.Rank.A;
            case LARGE -> TemplateCatalog.Rank.S;
        };
    }

    /**
     * The highest rank whose guild house and mage tower the square of a capital of a level holds: A, S or SS.
     */
    public static TemplateCatalog.Rank highest(NpcLevel level) {
        return switch (level) {
            case SMALL -> TemplateCatalog.Rank.A;
            case MEDIUM -> TemplateCatalog.Rank.S;
            case LARGE -> TemplateCatalog.Rank.SS;
        };
    }

    /**
     * A rank as the capital of a level can show it: the default of the level for none, at most the highest of the level.
     */
    public static TemplateCatalog.Rank fitting(NpcLevel level, TemplateCatalog.Rank rank) {
        if (rank == null) {
            return defaultOf(level);
        }
        TemplateCatalog.Rank highest = highest(level);
        return rank.ordinal() > highest.ordinal() ? highest : rank;
    }

    /**
     * Mixes the bits of a seed, so that neighbouring seeds give unrelated numbers.
     */
    static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
