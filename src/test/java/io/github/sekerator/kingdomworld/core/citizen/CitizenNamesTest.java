package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenNamesTest {
    private static final Pattern LATIN = Pattern.compile("[A-Z][a-z]+");
    private static final Pattern CYRILLIC = Pattern.compile("[А-ЯЁ][а-яё]+");

    @Test
    void namesUseOneAlphabetAndStartWithACapital() {
        for (int seed = -500; seed < 500; seed++) {
            for (Gender gender : Gender.values()) {
                String latin = CitizenNames.render(seed, gender, CitizenNames.Alphabet.LATIN);
                String cyrillic = CitizenNames.render(seed, gender, CitizenNames.Alphabet.CYRILLIC);
                assertTrue(LATIN.matcher(latin).matches(), "Latin name '" + latin + "' for seed " + seed);
                assertTrue(CYRILLIC.matcher(cyrillic).matches(), "Cyrillic name '" + cyrillic + "' for seed " + seed);
                assertTrue(latin.length() <= CitizenNames.maxLength() && cyrillic.length() <= CitizenNames.maxLength());
            }
        }
    }

    @Test
    void bothAlphabetsShowTheSameName() {
        for (int seed = 0; seed < 300; seed++) {
            CitizenNames.Parts parts = CitizenNames.parts(seed, Gender.MALE);
            String latin = CitizenNames.render(seed, Gender.MALE, CitizenNames.Alphabet.LATIN);
            String cyrillic = CitizenNames.render(seed, Gender.MALE, CitizenNames.Alphabet.CYRILLIC);
            // The same syllables are chosen, so a transliteration of the Cyrillic name gives the Latin one.
            assertEquals(latin, transliterate(cyrillic), "seed " + seed + " " + parts);
        }
    }

    @Test
    void seedsGiveStableAndVariedNames() {
        assertEquals(CitizenNames.render(12345, Gender.FEMALE, CitizenNames.Alphabet.CYRILLIC),
                CitizenNames.render(12345, Gender.FEMALE, CitizenNames.Alphabet.CYRILLIC));
        Set<String> names = new HashSet<>();
        for (int seed = 0; seed < 1000; seed++) {
            names.add(CitizenNames.render(seed, Gender.MALE, CitizenNames.Alphabet.LATIN));
        }
        assertTrue(names.size() > 400, "only " + names.size() + " different names in 1000 seeds");
        assertNotEquals(CitizenNames.render(7, Gender.MALE, CitizenNames.Alphabet.LATIN),
                CitizenNames.render(7, Gender.FEMALE, CitizenNames.Alphabet.LATIN));
    }

    @Test
    void languageChoosesTheAlphabet() {
        assertEquals(CitizenNames.Alphabet.CYRILLIC, CitizenNames.alphabetFor("ru_ru"));
        assertEquals(CitizenNames.Alphabet.LATIN, CitizenNames.alphabetFor("en_us"));
        assertEquals(CitizenNames.Alphabet.LATIN, CitizenNames.alphabetFor("de_de"));
        assertEquals(CitizenNames.Alphabet.LATIN, CitizenNames.alphabetFor(null));
    }

    private static String transliterate(String cyrillic) {
        String[][] table = {{"Добро", "Dobro"}, {"Лю", "Lyu"}, {"Я", "Ya"}, {"Же", "Zhe"}, {"ш", "sh"}, {"ж", "zh"},
                {"ю", "yu"}, {"я", "ya"}};
        String result = cyrillic;
        for (String[] pair : table) {
            result = result.replace(pair[0], pair[1]);
        }
        String from = "АБВГДЕЗИКЛМНОПРСТУабвгдезиклмнопрстуы";
        String to = "ABVGDEZIKLMNOPRSTUabvgdeziklmnoprstuy";
        StringBuilder out = new StringBuilder();
        for (char c : result.toCharArray()) {
            int index = from.indexOf(c);
            out.append(index >= 0 ? to.charAt(index) : c);
        }
        return out.toString();
    }
}
