package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A kingdom of the world: the mark that it is ruled by no player, its names in both alphabets, its ruler with a character
 * and traits, and the day of the ruler's last turn. The ruler has no body; the throne stays empty and no player may take it.
 *
 * @param rulerSeed the seed of the ruler's name, as citizens' names are made
 * @param towns     the names of the towns of the kingdom, in the order of its cities
 * @param founded   the game time it appeared at
 */
public record NpcKingdom(UUID kingdomId, int siteId, NpcLevel level, NpcNames.Name name, RulerCharacter character,
                         List<RulerCharacter.Trait> traits, int rulerSeed, Gender rulerGender, List<NpcNames.Name> towns, long founded,
                         long lastTurnDay) {
    public NpcKingdom {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(rulerGender, "rulerGender");
        traits = List.copyOf(traits);
        towns = List.copyOf(towns);
        if (traits.isEmpty() || traits.size() > 2) {
            throw new IllegalArgumentException("A ruler has one or two traits");
        }
    }

    /**
     * The name of the ruler in an alphabet.
     */
    public String rulerName(CitizenNames.Alphabet alphabet) {
        return CitizenNames.render(rulerSeed, rulerGender, alphabet);
    }

    public NpcKingdom withLastTurnDay(long day) {
        return new NpcKingdom(kingdomId, siteId, level, name, character, traits, rulerSeed, rulerGender, towns, founded, day);
    }

    public NpcKingdom withTowns(List<NpcNames.Name> value) {
        return new NpcKingdom(kingdomId, siteId, level, name, character, traits, rulerSeed, rulerGender, value, founded, lastTurnDay);
    }

    /**
     * The ruler of a site: its character, traits and name come from the seed of the place.
     */
    public static NpcKingdom create(UUID kingdomId, NpcSite site, NpcNames.Name name, List<NpcNames.Name> towns, long founded, long day) {
        long seed = site.seed();
        int rulerSeed = (int) (seed ^ (seed >>> 32) ^ 0x51C0FFEE);
        Gender gender = Math.floorMod(seed >>> 7, 4) == 0 ? Gender.FEMALE : Gender.MALE;
        return new NpcKingdom(kingdomId, site.id(), site.level(), name, RulerCharacter.pick(seed, site.level()),
                RulerCharacter.traits(seed), rulerSeed, gender, towns, founded, day);
    }
}
