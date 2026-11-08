package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.Gender;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A line of the chronicle of a court or of a report of a spy: a key and its arguments, kept as small typed tokens so that
 * names are shown in the language of whoever reads them later.
 *
 * <p>Tokens: {@code h<seed>} a house, {@code s<seed>.<f|m>} a person, {@code k<uuid>} a kingdom, {@code p<uuid>} a player,
 * {@code t<title>} a title, {@code f<fief>} a fief, {@code n<number>} a number, {@code x<text>} a text as it is.
 */
public record Note(long day, String key, List<String> args) {
    public static final int MAX_ARGS = 6;

    public Note {
        Objects.requireNonNull(key, "key");
        args = List.copyOf(args.size() > MAX_ARGS ? args.subList(0, MAX_ARGS) : args);
    }

    public static String house(NobleHouse house) {
        return "h" + house.familySeed();
    }

    public static String head(NobleHouse house) {
        return person(house.headSeed(), house.headGender());
    }

    public static String person(int seed, Gender gender) {
        return "s" + seed + "." + (gender == Gender.FEMALE ? "f" : "m");
    }

    public static String kingdom(UUID id) {
        return "k" + id;
    }

    public static String player(UUID id) {
        return "p" + id;
    }

    public static String title(Title title) {
        return "t" + title.id();
    }

    public static String fief(Fief fief) {
        return "f" + fief.code();
    }

    public static String number(long value) {
        return "n" + value;
    }

    public static String text(String value) {
        return "x" + (value == null ? "" : value);
    }
}
