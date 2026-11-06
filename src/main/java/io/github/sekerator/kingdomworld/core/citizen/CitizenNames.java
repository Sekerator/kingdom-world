package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Locale;
import java.util.Objects;

/**
 * Names of citizens, built from syllables. A citizen stores only a seed; the seed picks the syllables, and the
 * same syllables exist in a Latin and a Cyrillic form, so the same citizen is called "Radomir" in English and
 * "Радомир" in Russian.
 *
 * <p>Both tables must stay aligned: entry {@code i} of one alphabet is the transliteration of entry {@code i}
 * of the other. Changing or reordering entries renames existing citizens, so only append.
 */
public final class CitizenNames {
    /** Alphabet a name is shown in. */
    public enum Alphabet {
        LATIN,
        CYRILLIC
    }

    /** Syllables a name may start with (capitalized); {latin, cyrillic}. */
    private static final String[][] STARTS = {
            {"Bo", "Бо"}, {"Bra", "Бра"}, {"Ve", "Ве"}, {"Vla", "Вла"}, {"Go", "Го"}, {"Do", "До"},
            {"Dra", "Дра"}, {"Zla", "Зла"}, {"Ka", "Ка"}, {"Lu", "Лу"}, {"Lyu", "Лю"}, {"Mi", "Ми"},
            {"Mo", "Мо"}, {"Ne", "Не"}, {"O", "О"}, {"Pe", "Пе"}, {"Ra", "Ра"}, {"Ro", "Ро"},
            {"Sve", "Све"}, {"Sta", "Ста"}, {"Ti", "Ти"}, {"Ya", "Я"}, {"Za", "За"}, {"Ze", "Зе"},
            {"Ole", "Оле"}, {"Ste", "Сте"}, {"Zhe", "Же"}, {"Dobro", "Добро"}};
    /** Optional syllables in the middle of a name. */
    private static final String[][] MIDDLES = {
            {"ra", "ра"}, {"sla", "сла"}, {"mi", "ми"}, {"do", "до"}, {"ve", "ве"}, {"li", "ли"},
            {"ro", "ро"}, {"ni", "ни"}, {"go", "го"}, {"da", "да"}, {"la", "ла"}, {"ti", "ти"}};
    private static final String[][] MALE_ENDS = {
            {"mir", "мир"}, {"slav", "слав"}, {"dan", "дан"}, {"rad", "рад"}, {"bor", "бор"}, {"mil", "мил"},
            {"vit", "вит"}, {"gost", "гост"}, {"ved", "вед"}, {"dar", "дар"}, {"nik", "ник"}, {"zar", "зар"}};
    private static final String[][] FEMALE_ENDS = {
            {"mira", "мира"}, {"slava", "слава"}, {"dana", "дана"}, {"rada", "рада"}, {"lena", "лена"},
            {"mila", "мила"}, {"vina", "вина"}, {"nika", "ника"}, {"lada", "лада"}, {"rina", "рина"},
            {"sha", "ша"}, {"zara", "зара"}};
    /** Extra values of the middle index that mean "no middle syllable", so short names are common. */
    private static final int NO_MIDDLE_SLOTS = 8;

    /**
     * Indices of the syllables of a name.
     *
     * @param middle index of the middle syllable, or {@code -1} for a name of two syllables
     */
    public record Parts(int start, int middle, int end) {
    }

    private CitizenNames() {
    }

    /**
     * The syllables chosen by a seed.
     */
    public static Parts parts(int seed, Gender gender) {
        Objects.requireNonNull(gender, "gender");
        int hash = mix(seed);
        int start = Math.floorMod(hash, STARTS.length);
        int middleSlot = Math.floorMod(hash >>> 8, MIDDLES.length + NO_MIDDLE_SLOTS);
        int middle = middleSlot < MIDDLES.length ? middleSlot : -1;
        String[][] ends = gender == Gender.FEMALE ? FEMALE_ENDS : MALE_ENDS;
        int end = Math.floorMod(hash >>> 16, ends.length);
        return new Parts(start, middle, end);
    }

    /**
     * The name of a citizen in the given alphabet.
     */
    public static String render(int seed, Gender gender, Alphabet alphabet) {
        Parts parts = parts(seed, gender);
        int column = alphabet == Alphabet.CYRILLIC ? 1 : 0;
        String[][] ends = gender == Gender.FEMALE ? FEMALE_ENDS : MALE_ENDS;
        StringBuilder name = new StringBuilder(STARTS[parts.start()][column]);
        if (parts.middle() >= 0) {
            name.append(MIDDLES[parts.middle()][column]);
        }
        name.append(ends[parts.end()][column]);
        return name.toString();
    }

    /**
     * The alphabet for a game language code such as {@code ru_ru}: Cyrillic for Russian, Latin otherwise.
     */
    public static Alphabet alphabetFor(String languageCode) {
        return languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("ru") ? Alphabet.CYRILLIC : Alphabet.LATIN;
    }

    /**
     * Longest possible name in characters, for limits of names sent over the network.
     */
    public static int maxLength() {
        int max = 0;
        for (Alphabet alphabet : Alphabet.values()) {
            int column = alphabet == Alphabet.CYRILLIC ? 1 : 0;
            int ends = Math.max(longest(MALE_ENDS, column), longest(FEMALE_ENDS, column));
            max = Math.max(max, longest(STARTS, column) + longest(MIDDLES, column) + ends);
        }
        return max;
    }

    private static int longest(String[][] table, int column) {
        int max = 0;
        for (String[] entry : table) {
            max = Math.max(max, entry[column].length());
        }
        return max;
    }

    /**
     * Spreads the bits of a seed so that neighbouring seeds give unrelated names.
     */
    private static int mix(int seed) {
        int h = seed;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }
}
