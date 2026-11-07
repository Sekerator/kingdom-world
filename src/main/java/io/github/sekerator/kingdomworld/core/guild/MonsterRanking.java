package io.github.sekerator.kingdomworld.core.guild;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The rank of a monster, from what it is made of: its health, attack, armor and speed, weighed by the config, and whether
 * it is a boss. Overrides by id and by tag come first, so a pack can rank its monsters by hand; monsters of every other
 * mod are ranked from their attributes without anything to set up.
 */
public final class MonsterRanking {

    /**
     * What a monster is made of.
     *
     * @param id     the entity id, for example {@code minecraft:zombie}
     * @param health maximum health
     * @param attack attack damage, 0 for monsters without a melee attack
     * @param armor  armor points
     * @param speed  movement speed
     * @param boss   whether it is a boss: in the {@code forge:bosses} tag or with a boss bar of its own
     * @param tags   the entity tags of its type, without {@code #}
     */
    public record Stats(String id, double health, double attack, double armor, double speed, boolean boss, Collection<String> tags) {
        public Stats {
            Objects.requireNonNull(id, "id");
            tags = tags == null ? java.util.List.of() : java.util.List.copyOf(tags);
            health = finite(health);
            attack = finite(attack);
            armor = finite(armor);
            speed = finite(speed);
        }

        private static double finite(double value) {
            return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
        }
    }

    /**
     * A rank and where it came from.
     *
     * @param score    the strength from the attributes
     * @param override whether an override of the config gave the rank
     */
    public record Ranked(Rank rank, double score, boolean boss, boolean override) {
    }

    private MonsterRanking() {
    }

    /**
     * The strength of a monster by the weights of the config.
     */
    public static double score(Stats stats, GuildRules rules) {
        return stats.health() * rules.healthWeight + stats.attack() * rules.damageWeight + stats.armor() * rules.armorWeight
                + stats.speed() * rules.speedWeight;
    }

    /**
     * The rank a strength reaches.
     */
    public static Rank rankOfScore(double score, GuildRules rules) {
        Rank result = Rank.F;
        for (Rank rank : Rank.values()) {
            if (score >= rules.rankScore(rank)) {
                result = rank;
            }
        }
        return result;
    }

    /**
     * The rank of a monster: an override by id, then by tag, otherwise its strength, raised for a boss.
     */
    public static Ranked rank(Stats stats, GuildRules rules) {
        double score = score(stats, rules);
        Rank byId = rules.idOverrides.get(stats.id());
        if (byId != null) {
            return new Ranked(byId, score, stats.boss(), true);
        }
        Optional<Rank> byTag = Optional.empty();
        for (String tag : stats.tags()) {
            Rank rank = rules.tagOverrides.get(tag);
            if (rank != null && (byTag.isEmpty() || rank.atLeast(byTag.get()))) {
                byTag = Optional.of(rank);
            }
        }
        if (byTag.isPresent()) {
            return new Ranked(byTag.get(), score, stats.boss(), true);
        }
        Rank rank = rankOfScore(score, rules);
        if (stats.boss()) {
            rank = Rank.max(rank.plus(rules.bossRankBonus), rules.bossMinRank);
        }
        return new Ranked(rank, score, stats.boss(), false);
    }
}
