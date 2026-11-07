package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Texts about the kingdoms of the world. Names are translated components that carry both spellings, so every player reads them
 * in the alphabet of their language.
 */
public final class NpcTexts {
    private static final String KEY = "kingdomworld.npc.";
    private static final String COUNT = "kingdomworld.count.";

    private NpcTexts() {
    }

    public static MutableComponent kingdoms(long count) {
        return Component.translatable(COUNT + "kingdoms." + io.github.sekerator.kingdomworld.core.economy.Plurals.form(count).suffix(), count);
    }

    public static MutableComponent places(long count) {
        return Component.translatable(COUNT + "places." + io.github.sekerator.kingdomworld.core.economy.Plurals.form(count).suffix(), count);
    }

    public static MutableComponent level(NpcLevel level) {
        return Component.translatable(KEY + "level." + level.id());
    }

    public static MutableComponent character(RulerCharacter character) {
        return Component.translatable(KEY + "character." + character.id());
    }

    public static MutableComponent trait(RulerCharacter.Trait trait) {
        return Component.translatable(KEY + "trait." + trait.id());
    }

    /**
     * The traits of a ruler, one or two joined.
     */
    public static MutableComponent traits(List<RulerCharacter.Trait> traits) {
        if (traits.size() >= 2) {
            return Component.translatable(KEY + "traits.two", trait(traits.get(0)), trait(traits.get(1)));
        }
        return traits.isEmpty() ? Component.empty() : trait(traits.get(0));
    }

    /**
     * The name of a city: a town of a kingdom of the world in both alphabets, any other as it is.
     */
    public static MutableComponent city(String name) {
        return NpcWork.town(name).map(town -> name(town.latin(), town.cyrillic())).orElseGet(() -> Component.literal(name));
    }

    /**
     * A name in both alphabets.
     */
    public static MutableComponent name(String latin, String cyrillic) {
        return Component.translatable(KEY + "name", latin, cyrillic);
    }

    /**
     * The ruler with the title of a king or a queen.
     */
    public static MutableComponent ruler(NpcKingdom kingdom) {
        return ruler(kingdom.rulerName(CitizenNames.Alphabet.LATIN), kingdom.rulerName(CitizenNames.Alphabet.CYRILLIC), kingdom.rulerGender());
    }

    public static MutableComponent ruler(String latin, String cyrillic, Gender gender) {
        return Component.translatable(gender == Gender.FEMALE ? KEY + "queen" : KEY + "king", name(latin, cyrillic));
    }
}
