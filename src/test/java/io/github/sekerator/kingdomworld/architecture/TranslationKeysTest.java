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

    /**
     * Numbers of items, days and minutes read right in both languages: «21 предмет», «3 дня», «через 5 минут».
     */
    @Test
    void countsOfTradeHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("items", "предмет|предмет|предмета|предметов", "days", "день|день|дня|дней",
                "minutes", "минуту|минуту|минуты|минут", "blocks", "блок|блок|блока|блоков");
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                assertEquals(all[i] == Plurals.Form.N1, !english.get(key).endsWith("s"), key + ": English is singular only for exactly one");
            }
        });
        assertEquals(Plurals.Form.N21, Plurals.form(21));
        assertEquals(Plurals.Form.N2, Plurals.form(64 - 60));
        assertEquals(Plurals.Form.N5, Plurals.form(111));
    }

    /**
     * Numbers of the guild read right in both languages: «21 монстр», «3 очка», «5 кристаллов», «1 босс».
     */
    /**
     * Numbers of magic read right in both languages: «21 единица энергии», «3 чанка», «5 секунд», "1 energy", "2 chunks".
     */
    @Test
    void countsOfMagicHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("energy", "единица энергии|единица энергии|единицы энергии|единиц энергии",
                "chunks", "чанк|чанк|чанка|чанков", "seconds", "секунда|секунда|секунды|секунд");
        Map<String, String> englishForms = Map.of("energy", "energy|energy", "chunks", "chunk|chunks", "seconds", "second|seconds");
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            String[] english = englishForms.get(word).split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                assertEquals("%s " + (all[i] == Plurals.Form.N1 ? english[0] : english[1]), TranslationKeysTest.english.get(key), key);
            }
        });
        // The forms the texts of magic pick for typical numbers.
        assertEquals(Plurals.Form.N1, Plurals.form(1));
        assertEquals(Plurals.Form.N21, Plurals.form(31));
        assertEquals(Plurals.Form.N2, Plurals.form(22));
        assertEquals(Plurals.Form.N5, Plurals.form(5));
        assertEquals(Plurals.Form.N5, Plurals.form(11));
        assertEquals(Plurals.Form.N5, Plurals.form(0));
    }

    @Test
    void countsOfTheGuildHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("monsters", "монстр|монстр|монстра|монстров", "bosses", "босс|босс|босса|боссов",
                "points", "очко|очко|очка|очков", "crystals", "кристалл|кристалл|кристалла|кристаллов");
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                String singular = word.equals("bosses") ? "boss" : word.substring(0, word.length() - 1);
                assertEquals("%s " + (all[i] == Plurals.Form.N1 ? singular : word), english.get(key), key + ": English is singular only for one");
            }
        });
        assertEquals(Plurals.Form.N21, Plurals.form(101));
        assertEquals(Plurals.Form.N5, Plurals.form(12));
    }

    /**
     * Numbers of the army read right in both languages: «21 солдат», «3 наёмника», «5 бойцов», «2 врага», «11 отрядов».
     */
    @Test
    void countsOfTheArmyHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("soldiers", "солдат|солдат|солдата|солдат", "mercenaries", "наёмник|наёмник|наёмника|наёмников",
                "fighters", "боец|боец|бойца|бойцов", "enemies", "враг|враг|врага|врагов", "squads", "отряд|отряд|отряда|отрядов");
        Map<String, String> englishSingular = Map.of("soldiers", "soldier", "mercenaries", "mercenary", "fighters", "fighter", "enemies", "enemy",
                "squads", "squad");
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                assertEquals("%s " + (all[i] == Plurals.Form.N1 ? englishSingular.get(word) : word), english.get(key),
                        key + ": English is singular only for one");
            }
        });
        assertEquals(Plurals.Form.N2, Plurals.form(3));
        assertEquals(Plurals.Form.N5, Plurals.form(11));
        assertEquals(Plurals.Form.N21, Plurals.form(21));
    }

    /**
     * Numbers of companions and parties read right in both languages: «21 спутник», «3 спутника», «5 участников».
     */
    @Test
    void countsOfCompanionsHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("companions", "спутник|спутник|спутника|спутников", "members", "участник|участник|участника|участников");
        Map<String, String> englishSingular = Map.of("companions", "companion", "members", "member");
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                assertEquals("%s " + (all[i] == Plurals.Form.N1 ? englishSingular.get(word) : word), english.get(key),
                        key + ": English is singular only for one");
            }
        });
    }

    /**
     * Numbers of roads read right in both languages: «21 участок», «3 разбойника», «5 городов», «2 дороги», «11 строителей».
     */
    @Test
    void countsOfRoadsHaveEveryPluralForm() {
        Map<String, String> russianForms = Map.of("sections", "участок|участок|участка|участков", "bandits",
                "разбойник|разбойник|разбойника|разбойников", "cities", "город|город|города|городов", "roads", "дорога|дорога|дороги|дорог",
                "builders", "строитель|строитель|строителя|строителей");
        Map<String, String[]> englishForms = Map.of("sections", new String[]{"section", "sections"}, "bandits", new String[]{"robber", "robbers"},
                "cities", new String[]{"city", "cities"}, "roads", new String[]{"road", "roads"}, "builders", new String[]{"builder", "builders"});
        russianForms.forEach((word, forms) -> {
            String[] words = forms.split("[|]");
            Plurals.Form[] all = Plurals.Form.values();
            for (int i = 0; i < all.length; i++) {
                String key = "kingdomworld.count." + word + "." + all[i].suffix();
                assertEquals("%s " + words[i], russian.get(key), key);
                assertEquals("%s " + englishForms.get(word)[all[i] == Plurals.Form.N1 ? 0 : 1], english.get(key), key);
            }
        });
        assertEquals(Plurals.Form.N21, Plurals.form(121));
        assertEquals(Plurals.Form.N5, Plurals.form(112));
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
        for (String mode : List.of("overview", "journal", "settings", "payments", "reserves", "debtors")) {
            used.add("kingdomworld.screen.treasury.mode." + mode);
        }
        // Trade: the views of the tabs and the stall, counts in every plural form.
        for (String mode : List.of("sell", "buy", "lots", "export", "import", "settings")) {
            used.add("kingdomworld.screen.market.mode." + mode);
        }
        for (String mode : List.of("deals", "caravans", "propose")) {
            used.add("kingdomworld.screen.trade.mode." + mode);
        }
        for (String mode : List.of("buy", "sell", "lots", "mine")) {
            used.add("kingdomworld.screen.stall.tab." + mode);
            used.add("kingdomworld.screen.stall.empty." + mode);
            used.add("kingdomworld.screen.stall.hint." + mode);
        }
        for (String status : List.of("done", "declined", "expired", "cancelled")) {
            used.add("kingdomworld.screen.trade.status." + status);
        }
        // Construction: the views of the tab, the states of constructions, the categories and names of the built-in buildings.
        for (String mode : List.of("catalog", "sites", "buildings")) {
            used.add("kingdomworld.screen.construction.mode." + mode);
        }
        for (io.github.sekerator.kingdomworld.core.construction.Construction.Status status
                : io.github.sekerator.kingdomworld.core.construction.Construction.Status.values()) {
            used.add("kingdomworld.construction.status." + status.id());
        }
        for (String category : List.of("housing", "farming", "production", "trade", "defense", "decoration")) {
            used.add("kingdomworld.building.category." + category);
        }
        try (Stream<Path> templates = Files.list(assets.getParent().getParent().resolve("data").resolve("kingdomworld")
                .resolve("kingdomworld_buildings"))) {
            for (Path template : templates.filter(file -> file.toString().endsWith(".json")).toList()) {
                JsonObject description = JsonParser.parseString(Files.readString(template, StandardCharsets.UTF_8)).getAsJsonObject();
                used.add(description.get("name").getAsString());
            }
        }
        for (String word : List.of("items", "days", "minutes", "blocks")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        for (Denomination denomination : Denomination.values()) {
            used.add("kingdomworld.screen.treasury.coin_choice." + denomination.id());
        }
        // The guild: kinds of contracts, the ends of expeditions, the views of the desk and of the tab, counts in every form.
        for (io.github.sekerator.kingdomworld.core.guild.Contract.Kind kind : io.github.sekerator.kingdomworld.core.guild.Contract.Kind.values()) {
            used.add("kingdomworld.guild.contract.kind." + kind.id());
        }
        for (String result : List.of("success", "partial", "failure")) {
            used.add("kingdomworld.guild.squad_back." + result);
        }
        for (String tab : List.of("card", "board", "mine", "post")) {
            used.add("kingdomworld.screen.guild.tab." + tab);
        }
        for (String mode : List.of("branch", "adventurers", "squads", "contracts")) {
            used.add("kingdomworld.screen.guild_tab.mode." + mode);
        }
        for (String word : List.of("monsters", "bosses", "points", "crystals")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        // The army: ranks, orders, the views of the tab, the modes of the baton, counts in every form.
        for (io.github.sekerator.kingdomworld.core.army.SoldierRank rank : io.github.sekerator.kingdomworld.core.army.SoldierRank.values()) {
            used.add("kingdomworld.army.rank." + rank.id());
        }
        for (io.github.sekerator.kingdomworld.core.army.Order.Kind kind : io.github.sekerator.kingdomworld.core.army.Order.Kind.values()) {
            used.add("kingdomworld.army.order." + kind.id());
            used.add("kingdomworld.army.order_text." + kind.id());
        }
        for (String mode : List.of("overview", "soldiers", "recruit", "squads", "mercenaries", "deals")) {
            used.add("kingdomworld.screen.army_tab.mode." + mode);
            used.add("kingdomworld.screen.army_tab.hint." + mode);
        }
        for (String mode : List.of("hold", "patrol", "guard")) {
            used.add("kingdomworld.army.baton.mode." + mode);
        }
        for (String word : List.of("soldiers", "mercenaries", "fighters", "enemies", "squads")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        // Magic: spells of players and of kingdoms, research, the journal, the views of the tab, the focus and the staff.
        for (io.github.sekerator.kingdomworld.core.magic.PlayerSpell spell : io.github.sekerator.kingdomworld.core.magic.PlayerSpell.values()) {
            used.add("kingdomworld.magic.spell." + spell.id());
            used.add("kingdomworld.magic.spell." + spell.id() + ".hint");
        }
        for (io.github.sekerator.kingdomworld.core.magic.KingdomSpell spell : io.github.sekerator.kingdomworld.core.magic.KingdomSpell.values()) {
            for (String part : List.of("", ".hint", ".price")) {
                used.add("kingdomworld.magic.kingdom_spell." + spell.id() + part);
            }
        }
        for (io.github.sekerator.kingdomworld.core.magic.Research research : io.github.sekerator.kingdomworld.core.magic.Research.values()) {
            used.add("kingdomworld.magic.research." + research.id());
        }
        for (String change : List.of("done", "undo", "start")) {
            used.add("commands.kingdomworld.admin.magic.research." + change);
        }
        for (io.github.sekerator.kingdomworld.core.magic.EnergyCategory category
                : io.github.sekerator.kingdomworld.core.magic.EnergyCategory.values()) {
            used.add("kingdomworld.magic.category." + category.id());
        }
        for (String mode : List.of("energy", "journal", "mages", "research", "spells", "gates", "machines")) {
            used.add("kingdomworld.screen.magic_tab.mode." + mode);
            used.add("kingdomworld.screen.magic_tab.hint." + mode);
        }
        for (String period : List.of("today", "yesterday", "week")) {
            used.add("kingdomworld.screen.magic_tab.period." + period);
        }
        for (String state : List.of("done", "current", "cost", "locked")) {
            used.add("kingdomworld.screen.magic_tab.research." + state);
        }
        for (String mode : List.of("learn", "staff")) {
            used.add("kingdomworld.screen.focus.tab." + mode);
            used.add("kingdomworld.screen.focus.hint." + mode);
        }
        for (io.github.sekerator.kingdomworld.core.magic.MagicDevice.Kind kind : io.github.sekerator.kingdomworld.core.magic.MagicDevice.Kind.values()) {
            String block = switch (kind) {
                case FOCUS -> "magic_focus";
                case ACCUMULATOR -> "crystal_accumulator";
                case GENERATOR -> "crystal_generator";
                case GATE -> "magic_gate";
            };
            used.add("block.kingdomworld." + block + ".tooltip");
        }
        for (String word : List.of("energy", "chunks", "seconds")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        // Roads and robbers: the views of the tab, classes, modes and states, the planning on the atlas, counts in every form.
        for (String mode : List.of("overview", "cities", "roads", "requests", "camps", "risks")) {
            used.add("kingdomworld.screen.roads.mode." + mode);
            used.add("kingdomworld.screen.roads.hint." + mode);
        }
        for (String action : List.of("repair", "pause", "patrol", "cancel")) {
            used.add("kingdomworld.screen.roads.action." + action);
            used.add("kingdomworld.screen.roads.action." + action + ".hint");
        }
        for (io.github.sekerator.kingdomworld.core.road.RoadClass roadClass : io.github.sekerator.kingdomworld.core.road.RoadClass.values()) {
            used.add("kingdomworld.road.class." + roadClass.id());
            used.add("kingdomworld.screen.map.road.cost." + roadClass.id());
        }
        for (io.github.sekerator.kingdomworld.core.road.Road.Mode mode : io.github.sekerator.kingdomworld.core.road.Road.Mode.values()) {
            used.add("kingdomworld.road.mode." + mode.id());
        }
        for (String mode : List.of("joint", "payer")) {
            used.add("kingdomworld.screen.map.road.mode." + mode);
            used.add("kingdomworld.screen.map.road.mode." + mode + ".hint");
        }
        for (io.github.sekerator.kingdomworld.core.road.Road.Status status : io.github.sekerator.kingdomworld.core.road.Road.Status.values()) {
            used.add("kingdomworld.road.status." + status.id());
        }
        for (io.github.sekerator.kingdomworld.core.road.Crossing.Status status : io.github.sekerator.kingdomworld.core.road.Crossing.Status.values()) {
            used.add("kingdomworld.road.crossing." + status.id());
        }
        for (io.github.sekerator.kingdomworld.core.road.RoadLayout.Material material
                : io.github.sekerator.kingdomworld.core.road.RoadLayout.Material.values()) {
            used.add("kingdomworld.road.material." + material.id());
        }
        for (String action : List.of("wall_palisade", "wall_stone", "gate_here")) {
            used.add("kingdomworld.screen.roads.city_action." + action);
            used.add("kingdomworld.screen.roads.city_action." + action + ".hint");
        }
        used.add("kingdomworld.screen.roads.hint.city_actions");
        for (io.github.sekerator.kingdomworld.core.road.CityLevel level : io.github.sekerator.kingdomworld.core.road.CityLevel.values()) {
            used.add("kingdomworld.road.level." + level.id());
        }
        for (io.github.sekerator.kingdomworld.core.road.Wall.WallClass wallClass
                : io.github.sekerator.kingdomworld.core.road.Wall.WallClass.values()) {
            used.add("kingdomworld.road.wall." + wallClass.id());
        }
        for (String word : List.of("sections", "bandits", "cities", "roads", "builders", "gates")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        for (String entity : List.of("bandit", "bandit_leader", "caravan_merchant")) {
            used.add("entity.kingdomworld." + entity);
        }
        used.add("kingdomworld.death.robbers");
        used.add("kingdomworld.death.robbers.female");
        for (String key : List.of("city_founded", "city_refused", "city_lost", "hall_empty", "hall_city", "place", "capital_of", "road_built",
                "repaired", "road_planned", "crossing_asked", "road_dropped", "crossing_accepted", "crossing_declined", "road_starts", "ambush",
                "beaten_off", "goods_taken", "robbed", "robbed_all", "merchant_name", "camp_fight", "camp_cleared", "camp_loot", "camp_found",
                "leader_dead", "wall_laid", "wall_built")) {
            used.add("kingdomworld.road." + key);
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

        // Companions and parties: origins, states and messages for both genders, orders, loyalty, the windows, counts in every form.
        List<String> companionGendered = new ArrayList<>(List.of("status.slave", "status.free", "freed", "freed_citizen", "belongs", "protected",
                "bought", "dismissed", "unpaid", "left_unpaid", "quit", "warning_slave", "warning_free", "recovered", "escaped", "died", "level_up",
                "freed_by_law"));
        for (io.github.sekerator.kingdomworld.core.companion.Origin origin : io.github.sekerator.kingdomworld.core.companion.Origin.values()) {
            companionGendered.add("origin." + origin.id());
        }
        for (String key : companionGendered) {
            used.add("kingdomworld.companion." + key);
            used.add("kingdomworld.companion." + key + ".female");
        }
        for (io.github.sekerator.kingdomworld.core.companion.CompanionOrder order
                : io.github.sekerator.kingdomworld.core.companion.CompanionOrder.values()) {
            used.add("kingdomworld.companion.order." + order.id());
            used.add("kingdomworld.screen.companion.order." + order.id());
            used.add("kingdomworld.screen.companion.order." + order.id() + ".hint");
        }
        for (io.github.sekerator.kingdomworld.core.companion.LoyaltyReason reason
                : io.github.sekerator.kingdomworld.core.companion.LoyaltyReason.values()) {
            used.add("kingdomworld.companion.loyalty." + reason.id());
        }
        for (String action : List.of("free", "free_to_kingdom", "sell", "dismiss")) {
            used.add("kingdomworld.screen.companion.action." + action);
            used.add("kingdomworld.screen.companion.action." + action + ".hint");
        }
        for (int slot = 0; slot < 6; slot++) {
            used.add("kingdomworld.screen.companion.gear." + slot);
        }
        for (String tab : List.of("market", "hire")) {
            used.add("kingdomworld.screen.guild.tab." + tab);
            used.add("kingdomworld.screen.guild.tab." + tab + ".full");
        }
        for (String word : List.of("companions", "members")) {
            for (Plurals.Form form : Plurals.Form.values()) {
                used.add("kingdomworld.count." + word + "." + form.suffix());
            }
        }
        used.add("kingdomworld.profession.companion");
        used.add("kingdomworld.profession.companion.female");
        used.add("entity.kingdomworld.companion");
        used.add("key.kingdomworld.party");

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
