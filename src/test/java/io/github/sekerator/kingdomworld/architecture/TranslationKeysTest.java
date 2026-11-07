package io.github.sekerator.kingdomworld.architecture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.ItemValues;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod speaks English and Russian. Every text a player sees comes from a translation key, both language
 * files hold the same keys with real texts, and every key the code uses exists.
 */
class TranslationKeysTest {
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?[sd]");
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern CYRILLIC = Pattern.compile("[\\u0400-\\u04FF]");
    /** Full translation keys written as string literals in the code. */
    private static final Pattern KEY_LITERAL = Pattern.compile(
            "\"((?:kingdomworld|commands\\.kingdomworld|key|item\\.kingdomworld|block\\.kingdomworld)\\.[a-z0-9_.]*[a-z0-9_])\"");
    /** Constants holding a key prefix, for example {@code KEY = "commands.kingdomworld.kingdom."}. */
    private static final Pattern PREFIX_CONSTANT = Pattern.compile(
            "static final String (\\w+) = \"((?:kingdomworld|commands\\.kingdomworld)\\.[a-z0-9_.]*\\.)\";");
    private static final Pattern PREFIX_USE = Pattern.compile("\\b(\\w+) \\+ \"([a-z0-9_.]*[a-z0-9_])\"");
    /**
     * Keys that pick one of their arguments per language on purpose: a citizen name comes with its Latin and its
     * Cyrillic spelling, English shows the first and Russian the second.
     */
    private static final Set<String> LANGUAGE_PICKS = Set.of("kingdomworld.citizen.name");

    private static Map<String, String> english;
    private static Map<String, String> russian;
    private static Path assets;

    @BeforeAll
    static void load() throws IOException {
        assets = SourceTree.mainSourceRoot().getParent().resolve("resources").resolve("assets").resolve("kingdomworld");
        english = read(assets.resolve("lang").resolve("en_us.json"));
        russian = read(assets.resolve("lang").resolve("ru_ru.json"));
    }

    private static Map<String, String> read(Path file) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            assertTrue(entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString(),
                    file.getFileName() + ": value of " + entry.getKey() + " is not a string");
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() {
        Set<String> onlyEnglish = new TreeSet<>(english.keySet());
        onlyEnglish.removeAll(russian.keySet());
        Set<String> onlyRussian = new TreeSet<>(russian.keySet());
        onlyRussian.removeAll(english.keySet());
        assertTrue(onlyEnglish.isEmpty() && onlyRussian.isEmpty(),
                "Missing in ru_ru: " + onlyEnglish + "\nMissing in en_us: " + onlyRussian);
    }

    @Test
    void valuesAreNotEmptyAndHaveNoLongDash() {
        List<String> problems = new ArrayList<>();
        for (Map<String, String> language : List.of(english, russian)) {
            String name = language == english ? "en_us" : "ru_ru";
            language.forEach((key, value) -> {
                if (value.isBlank()) {
                    problems.add(name + ": " + key + " is empty");
                }
                if (value.indexOf('\u2014') >= 0) {
                    problems.add(name + ": " + key + " contains a long dash");
                }
            });
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void translationsTakeTheSameArguments() {
        List<String> problems = new ArrayList<>();
        english.forEach((key, value) -> {
            String other = russian.get(key);
            if (other != null && !LANGUAGE_PICKS.contains(key) && argumentCount(value) != argumentCount(other)) {
                problems.add(key + ": en takes " + argumentCount(value) + ", ru takes " + argumentCount(other));
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void russianTextsAreRussian() {
        List<String> problems = new ArrayList<>();
        english.forEach((key, value) -> {
            String other = russian.get(key);
            String words = ARGUMENT.matcher(value).replaceAll(" ");
            if (other != null && LATIN_WORD.matcher(words).find() && !CYRILLIC.matcher(other).find()) {
                problems.add(key + ": '" + other + "'");
            }
        });
        assertTrue(problems.isEmpty(), "Russian values without Russian text:\n" + String.join("\n", problems));
    }

    /**
     * Numbers of coins read right in both languages: «1 золотой, 2 золотых, 5 золотых, 21 медяк» and "1 gold coin,
     * 21 gold coins".
     */
    @Test
    void coinsHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("copper", "медяк|медяк|медяка|медяков", "silver",
                "серебряник|серебряник|серебряника|серебряников", "gold", "золотой|золотой|золотых|золотых");
        for (Denomination denomination : Denomination.values()) {
            String[] words = russianForms.get(denomination.id()).split("\\|");
            Plurals.Form[] forms = Plurals.Form.values();
            for (int i = 0; i < forms.length; i++) {
                String key = "kingdomworld.money." + denomination.id() + "." + forms[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                String english = TranslationKeysTest.english.get(key);
                assertEquals(forms[i] == Plurals.Form.N1, !english.endsWith("s"), key + ": English is singular only for exactly one");
            }
        }
    }

    @Test
    void argumentCounting() {
        assertEquals(2, argumentCount("%s and %s"));
        assertEquals(2, argumentCount("%2$s, %1$s"));
        assertEquals(0, argumentCount("100%% sure"));
    }

    @Test
    void everyKeyUsedByTheCodeExists() throws IOException {
        Set<String> used = new TreeSet<>();
        for (Path file : SourceTree.javaFiles(SourceTree.packageDir(SourceTree.BASE_PACKAGE))) {
            String code = SourceTree.code(file);
            Matcher literal = KEY_LITERAL.matcher(code);
            while (literal.find()) {
                used.add(literal.group(1));
            }
            Map<String, String> prefixes = new HashMap<>();
            Matcher constant = PREFIX_CONSTANT.matcher(code);
            while (constant.find()) {
                prefixes.put(constant.group(1), constant.group(2));
            }
            Matcher use = PREFIX_USE.matcher(code);
            while (use.find()) {
                String prefix = prefixes.get(use.group(1));
                if (prefix != null) {
                    used.add(prefix + use.group(2));
                }
            }
        }
        // Keys built from enums of the core.
        for (KingdomError error : KingdomError.values()) {
            used.add("kingdomworld.error." + error.id());
        }
        for (CreateKingdomResult.Error error : CreateKingdomResult.Error.values()) {
            used.add("kingdomworld.kingdom.error." + error.id());
        }
        for (Role role : Role.values()) {
            used.add("kingdomworld.role." + role.id());
        }
        for (Permission permission : Permission.values()) {
            used.add("kingdomworld.permission." + permission.id());
            used.add("kingdomworld.permission." + permission.id() + ".short");
        }
        // Words that agree with a citizen have a form for women.
        List<String> gendered = new ArrayList<>(List.of("kingdomworld.death.player_named", "kingdomworld.profession.unknown",
                "kingdomworld.citizen.arrived", "kingdomworld.citizen.died", "commands.kingdomworld.admin.citizens.kill.success",
                "commands.kingdomworld.admin.citizens.info.header"));
        for (CitizenState state : CitizenState.values()) {
            gendered.add("kingdomworld.citizen.state." + state.id());
        }
        for (CauseOfDeath cause : CauseOfDeath.values()) {
            gendered.add("kingdomworld.death." + cause.id());
        }
        for (CitizenNeeds.GrowthCheck check : CitizenNeeds.GrowthCheck.values()) {
            used.add("kingdomworld.screen.citizens.growth." + check.name().toLowerCase(Locale.ROOT));
        }
        for (Profession profession : Professions.REGISTRY.all()) {
            gendered.add("kingdomworld.profession." + profession.id());
        }
        for (String key : gendered) {
            used.add(key);
            used.add(key + ".female");
        }
        for (WorkStatus status : WorkStatus.values()) {
            used.add("kingdomworld.work.status." + status.id());
        }
        for (NameProblem problem : NameProblem.values()) {
            used.add("kingdomworld.screen.found.problem." + problem.name().toLowerCase(Locale.ROOT));
        }
        // Money: every coin in every plural form, the coins themselves, the journal and the views of the treasury.
        for (Denomination denomination : Denomination.values()) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.money." + denomination.id() + "." + form.suffix());
            }
            used.add("item.kingdomworld.coin." + denomination.id());
            used.add("kingdomworld.screen.bank.deposit." + denomination.id());
            used.add("kingdomworld.screen.bank.coins." + denomination.id());
        }
        for (LedgerCategory category : LedgerCategory.values()) {
            used.add("kingdomworld.ledger." + category.id());
        }
        for (TreasuryJournal.Period period : TreasuryJournal.Period.values()) {
            used.add("kingdomworld.screen.treasury.period." + period.id());
        }
        for (String mode : List.of("overview", "journal", "settings", "debtors")) {
            used.add("kingdomworld.screen.treasury.mode." + mode);
        }
        for (ItemValues.Source source : ItemValues.Source.values()) {
            used.add("commands.kingdomworld.economy.value.source." + source.name().toLowerCase(Locale.ROOT));
        }
        for (String station : io.github.sekerator.kingdomworld.core.production.Stations.ALL) {
            used.add("kingdomworld.station." + station);
        }
        // Names of registered blocks and items, found through their models.
        try (Stream<Path> models = Files.list(assets.resolve("models").resolve("item"))) {
            for (Path model : models.toList()) {
                String name = model.getFileName().toString().replace(".json", "");
                boolean block = Files.exists(assets.resolve("blockstates").resolve(name + ".json"));
                used.add((block ? "block.kingdomworld." : "item.kingdomworld.") + name);
            }
        }

        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(english.keySet());
        assertTrue(missing.isEmpty(), "Keys used by the code but missing in en_us:\n" + String.join("\n", missing));
        assertTrue(used.size() > 150, "The key scan found suspiciously few keys: " + used.size());
    }

    static int argumentCount(String text) {
        Matcher matcher = ARGUMENT.matcher(text.replace("%%", ""));
        int sequential = 0;
        int positional = 0;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                positional = Math.max(positional, Integer.parseInt(matcher.group(1)));
            } else {
                sequential++;
            }
        }
        return Math.max(sequential, positional);
    }
}
