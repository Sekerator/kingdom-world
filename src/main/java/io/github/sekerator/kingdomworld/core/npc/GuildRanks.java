package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.Rank;

/**
 * The ranks of the two guilds of a kingdom of the world from its first day: its branch of the adventurers' guild and its guild
 * of mages. A kingdom has a rank within the range of its level (by default a small one B or A, a medium one A or S, a large one
 * S or SS), chosen by the seed of its place; the highest rank of the range above S belongs to one kingdom of the world alone,
 * the strongest. The guild its ruler cares for keeps that rank and the other one stands a rank lower, not below the lowest of
 * the level: a warrior's adventurers, a mage's mages.
 */
public final class GuildRanks {
    /** The rank only the strongest kingdom of the world reaches. */
    public static final Rank TOP = Rank.SS;

    private GuildRanks() {
    }

    /**
     * The ranks of the two guilds of a kingdom.
     */
    public record Ranks(Rank adventurers, Rank mages) {
        public Ranks {
            java.util.Objects.requireNonNull(adventurers, "adventurers");
            java.util.Objects.requireNonNull(mages, "mages");
        }

        /** The higher of the two. */
        public Rank highest() {
            return Rank.max(adventurers, mages);
        }
    }

    /**
     * Whether a kingdom of a level may be the strongest of the world: its range reaches {@link #TOP}.
     */
    public static boolean mayBeStrongest(NpcLevel level, GuildRules rules) {
        return rules.worldRankMax(level.ordinal()).atLeast(TOP);
    }

    /**
     * The rank of a kingdom of the world.
     *
     * @param strongest whether it is the strongest kingdom of the world, the only one of rank {@link #TOP}
     */
    public static Rank kingdom(NpcLevel level, long seed, boolean strongest, GuildRules rules) {
        Rank low = rules.worldRankMin(level.ordinal());
        Rank high = rules.worldRankMax(level.ordinal());
        if (strongest && high.atLeast(TOP)) {
            return TOP;
        }
        // The top rank and what lies above it are kept for the strongest kingdom.
        if (high.atLeast(TOP)) {
            high = TOP.plus(-1);
        }
        if (high.ordinal() <= low.ordinal()) {
            return low;
        }
        int span = high.ordinal() - low.ordinal() + 1;
        return low.plus((int) Math.floorMod(mix(seed), (long) span));
    }

    /**
     * The ranks of the two guilds of a kingdom of the world.
     */
    public static Ranks of(NpcLevel level, long seed, RulerCharacter character, boolean strongest, GuildRules rules) {
        Rank rank = kingdom(level, seed, strongest, rules);
        Rank lower = Rank.max(rules.worldRankMin(level.ordinal()), rank.plus(-1));
        return switch (character) {
            case WARRIOR -> new Ranks(rank, lower);
            case MAGE -> new Ranks(lower, rank);
            default -> new Ranks(rank, rank);
        };
    }

    /**
     * The rank a guild of a kingdom of the world may grow to: the highest of its level, below {@link #TOP} unless it is the
     * strongest kingdom.
     */
    public static Rank ceiling(NpcLevel level, boolean strongest, GuildRules rules) {
        Rank high = rules.worldRankMax(level.ordinal());
        return strongest || !high.atLeast(TOP) ? high : Rank.max(rules.worldRankMin(level.ordinal()), TOP.plus(-1));
    }

    /**
     * A well spread number from a seed, so that close seeds give unrelated choices.
     */
    static long mix(long seed) {
        long z = seed + 0x6A09E667F3BCC909L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
