package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.HouseNames;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.road.City;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

/**
 * Texts of the nobility. Names of houses, heads, spies and towns go to the client in both alphabets and the language of the
 * player picks one, so they are never declined; Russian lines quote them.
 */
public final class NobilityTexts {
    public static final String KEY = "kingdomworld.nobility.";

    private NobilityTexts() {
    }

    /**
     * A text in both alphabets: the language file of the player shows one.
     */
    public static MutableComponent both(String latin, String cyrillic) {
        return Component.translatable(KEY + "both", latin, cyrillic);
    }

    public static MutableComponent house(int familySeed) {
        return both(HouseNames.family(familySeed, CitizenNames.Alphabet.LATIN), HouseNames.family(familySeed, CitizenNames.Alphabet.CYRILLIC));
    }

    public static MutableComponent house(NobleHouse house) {
        return house(house.familySeed());
    }

    public static MutableComponent person(int seed, Gender gender) {
        return both(CitizenNames.render(seed, gender, CitizenNames.Alphabet.LATIN), CitizenNames.render(seed, gender, CitizenNames.Alphabet.CYRILLIC));
    }

    public static MutableComponent head(NobleHouse house) {
        return person(house.headSeed(), house.headGender());
    }

    public static MutableComponent spy(Spy spy) {
        return person(spy.seed(), spy.gender());
    }

    /**
     * An amount of money in the currency of a kingdom.
     */
    public static MutableComponent money(MinecraftServer server, UUID currency, long amount) {
        return io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(amount, io.github.sekerator.kingdomworld.common.api.KingdomWorldApi
                .state(server).economy().units(currency).orElse(io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT));
    }

    public static MutableComponent title(Title title) {
        return Component.translatable(KEY + "title." + title.id());
    }

    public static MutableComponent task(Spy.Task task) {
        return Component.translatable(KEY + "task." + task.id());
    }

    public static Component fief(MinecraftServer server, Fief fief) {
        if (fief.kind() == Fief.Kind.MARCH) {
            return Component.translatable(KEY + "march." + fief.march());
        }
        Optional<City> city = RoadWork.state(server).city(fief.city());
        if (city.isEmpty()) {
            return Component.translatable(KEY + "fief_gone");
        }
        return cityName(city.get().name());
    }

    public static Component cityName(String name) {
        Optional<NpcNames.Name> town = NpcWork.town(name);
        return town.<Component>map(both -> both(both.latin(), both.cyrillic())).orElseGet(() -> Component.literal(name));
    }

    /**
     * A line of a chronicle or a report, its names resolved now.
     */
    public static MutableComponent note(MinecraftServer server, Note note) {
        Object[] args = new Object[note.args().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = arg(server, note.args().get(i));
        }
        return Component.translatable(KEY + "note." + note.key(), args);
    }

    private static Object arg(MinecraftServer server, String token) {
        if (token.isEmpty()) {
            return "";
        }
        String rest = token.substring(1);
        try {
            return switch (token.charAt(0)) {
                case 'h' -> house(Integer.parseInt(rest));
                case 's' -> {
                    int dot = rest.indexOf('.');
                    yield person(Integer.parseInt(rest.substring(0, dot)), rest.endsWith("f") ? Gender.FEMALE : Gender.MALE);
                }
                case 'k' -> WarWork.name(server, UUID.fromString(rest));
                case 'p' -> PlayerNames.display(server, UUID.fromString(rest));
                case 't' -> Title.byId(rest).<Object>map(NobilityTexts::title).orElse(rest);
                case 'f' -> Fief.parse(rest).<Object>map(fief -> fief(server, fief)).orElse("?");
                case 'n' -> Long.parseLong(rest);
                case 'x' -> rest.matches("[a-z_]+") ? Component.translatable(KEY + "word." + rest) : rest;
                default -> token;
            };
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
