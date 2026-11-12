package io.github.sekerator.kingdomworld.core.npc;

import java.util.Optional;

/**
 * The rank of the guild house and the mage tower of a styled capital, decided once when its place is planned.
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
        return TemplateCatalog.Rank.B;
    }

    /**
     * The rank of an id ({@code b}, {@code a}, {@code s}, {@code ss}), if it names one.
     */
    public static Optional<TemplateCatalog.Rank> parse(String id) {
        return Optional.empty();
    }

    /**
     * The rank of the guild of a place: the one it keeps, or the default of its level.
     */
    public static TemplateCatalog.Rank of(NpcSite site) {
        return TemplateCatalog.Rank.B;
    }
}
