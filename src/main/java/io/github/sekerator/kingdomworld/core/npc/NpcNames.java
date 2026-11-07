package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;

import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Names of the kingdoms of the world and of their towns, made of a root and an ending that exist in a Latin and a Cyrillic
 * form, so that a kingdom is «Белогорье» in Russian and "Belogorye" in English. The Latin form is the name of the kingdom
 * in the register and in commands. No two kingdoms or towns of a world get the same name.
 *
 * <p>Both columns must stay aligned: entry {@code i} of one alphabet is the transliteration of entry {@code i} of the other.
 */
public final class NpcNames {
    /**
     * A name in both alphabets.
     */
    public record Name(String latin, String cyrillic) {
        public Name {
            Objects.requireNonNull(latin, "latin");
            Objects.requireNonNull(cyrillic, "cyrillic");
        }

        public String key() {
            return KingdomNames.key(latin);
        }
    }

    private static final String[][] ROOTS = {
            {"Bel", "Бел"}, {"Zlat", "Злат"}, {"Rad", "Рад"}, {"Svet", "Свет"}, {"Yar", "Яр"}, {"Mir", "Мир"},
            {"Dobr", "Добр"}, {"Ozer", "Озер"}, {"Lesn", "Лесн"}, {"Kamen", "Камен"}, {"Ver", "Вер"}, {"Vol", "Вол"},
            {"Sever", "Север"}, {"Zar", "Зар"}, {"Polyan", "Полян"}, {"Krasn", "Красн"}, {"Sosn", "Сосн"}, {"Dub", "Дуб"},
            {"Bor", "Бор"}, {"Vysh", "Выш"}, {"Serebr", "Серебр"}, {"Ryabin", "Рябин"}, {"Tikh", "Тих"}, {"Zelen", "Зелен"}};
    private static final String[][] KINGDOM_ENDS = {
            {"ogorye", "огорье"}, {"oslavia", "ославия"}, {"ania", "ания"}, {"ovia", "овия"}, {"ozemye", "оземье"},
            {"orye", "орье"}, {"odolye", "одолье"}, {"opolye", "ополье"}};
    private static final String[][] TOWN_ENDS = {
            {"ograd", "оград"}, {"ov", "ов"}, {"insk", "инск"}, {"ets", "ец"}, {"opol", "ополь"}, {"ovka", "овка"},
            {"ino", "ино"}, {"ichi", "ичи"}, {"ozersk", "озерск"}, {"oborsk", "оборск"}};

    private NpcNames() {
    }

    /**
     * The name of a kingdom: the first one the seed gives that is not taken.
     *
     * @param taken whether a name (its Latin form) is taken already, by a kingdom of players too
     */
    public static Name kingdom(long seed, Predicate<String> taken) {
        return unique(seed ^ 0x4B494E47L, KINGDOM_ENDS, taken);
    }

    /**
     * The name of a town: the first one the seed gives that is not taken.
     */
    public static Name town(long seed, Predicate<String> taken) {
        return unique(seed ^ 0x544F574EL, TOWN_ENDS, taken);
    }

    /**
     * How many names of kingdoms there are before numbers are added.
     */
    public static int kingdomNames() {
        return ROOTS.length * KINGDOM_ENDS.length;
    }

    private static Name unique(long seed, String[][] ends, Predicate<String> taken) {
        SplittableRandom random = new SplittableRandom(seed);
        int total = ROOTS.length * ends.length;
        int start = random.nextInt(total);
        // Every combination once, in an order of the seed (a step that shares no factor with the total), then with numbers.
        int step = 1 + 2 * random.nextInt(total);
        while (gcd(step, total) != 1) {
            step += 2;
        }
        for (int round = 0; round < 100; round++) {
            for (int i = 0; i < total; i++) {
                int index = (int) ((start + (long) step * i) % total);
                Name name = make(ROOTS[index / ends.length], ends[index % ends.length], round);
                if (!taken.test(name.latin()) && KingdomNames.isValid(name.latin())) {
                    return name;
                }
            }
        }
        throw new IllegalStateException("No free name");
    }

    private static Name make(String[] root, String[] end, int round) {
        String suffix = round == 0 ? "" : " " + roman(round + 1);
        return new Name(root[0] + end[0] + suffix, root[1] + end[1] + suffix);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * A number from 1 to 3999 in Roman numerals, for second and later kingdoms of a name.
     */
    static String roman(int number) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int left = number;
        for (int i = 0; i < values.length; i++) {
            while (left >= values[i]) {
                result.append(letters[i]);
                left -= values[i];
            }
        }
        return result.toString();
    }
}
