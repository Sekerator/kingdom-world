package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;

import java.util.SplittableRandom;

/**
 * Names of noble houses in both alphabets, made from a seed the way names of citizens are. A family name is one word that
 * reads the same in every case, so texts quote it rather than decline it.
 */
public final class HouseNames {
    private static final String[][] STARTS = {
            {"Vel", "Вел"}, {"Dra", "Дра"}, {"Bor", "Бор"}, {"Gor", "Гор"}, {"Zar", "Зар"}, {"Kras", "Крас"}, {"Mor", "Мор"},
            {"Ost", "Ост"}, {"Rav", "Рав"}, {"Sev", "Сев"}, {"Tor", "Тор"}, {"Hal", "Хал"}, {"Ver", "Вер"}, {"Lan", "Лан"},
            {"Strel", "Стрел"}, {"Bel", "Бел"}, {"Yar", "Яр"}, {"Dub", "Дуб"}, {"Vol", "Вол"}, {"Kam", "Кам"}};
    private static final String[][] MIDDLES = {{"o", "о"}, {"e", "е"}, {"a", "а"}, {"i", "и"}};
    private static final String[][] ENDS = {
            {"mir", "мир"}, {"slav", "слав"}, {"grad", "град"}, {"dol", "дол"}, {"vin", "вин"}, {"sten", "стен"}, {"lev", "лев"},
            {"rod", "род"}, {"bor", "бор"}, {"gor", "гор"}, {"stan", "стан"}, {"ved", "вед"}};

    private HouseNames() {
    }

    /**
     * The family name of a seed in an alphabet.
     */
    public static String family(int seed, CitizenNames.Alphabet alphabet) {
        SplittableRandom random = new SplittableRandom(seed * 0x9E3779B97F4A7C15L ^ 0x484F555345L);
        int column = alphabet == CitizenNames.Alphabet.CYRILLIC ? 1 : 0;
        String start = STARTS[random.nextInt(STARTS.length)][column];
        String middle = MIDDLES[random.nextInt(MIDDLES.length)][column];
        String end = ENDS[random.nextInt(ENDS.length)][column];
        return start + middle + end;
    }
}
