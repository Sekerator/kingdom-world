package io.github.sekerator.kingdomworld.architecture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.common.guide.GuideNumbers;
import io.github.sekerator.kingdomworld.core.guide.GuideBook;
import io.github.sekerator.kingdomworld.core.guide.GuideInline;
import io.github.sekerator.kingdomworld.core.guide.GuideNumbersFormat;
import io.github.sekerator.kingdomworld.core.guide.GuidePage;
import io.github.sekerator.kingdomworld.core.guide.GuideParser;
import io.github.sekerator.kingdomworld.core.guide.GuideRecipe;
import io.github.sekerator.kingdomworld.core.guide.GuideSection;
import io.github.sekerator.kingdomworld.core.guide.GuideText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pages of the guide in {@code assets/kingdomworld/guide/<language>/}: both languages have the same sections and pages, and
 * every page the same links, items, crafting grids and numbers in both; the Russian pages are written in Russian and the
 * English ones without Cyrillic, nowhere a long dash; links lead to pages, numbers to values of the configuration of the server,
 * items and recipes to those of the mod and of the game.
 */
class GuidePagesTest {
    private static final Pattern CYRILLIC = Pattern.compile("[\\p{IsCyrillic}]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    /** The share of Cyrillic letters a Russian text needs: names of mods, keys, commands and ranks are written in Latin. */
    private static final double RUSSIAN_SHARE = 0.6;
    /** Shorter texts are not counted: a cell of a table may be a rank or a key alone. */
    private static final int COUNTED_LETTERS = 15;

    private static Path guide;
    private static Path resources;
    private static Map<String, Map<String, String>> files;
    private static GuideBook russian;
    private static GuideBook english;

    @BeforeAll
    static void load() throws IOException {
        resources = SourceTree.mainSourceRoot().getParent().resolve("resources");
        guide = resources.resolve("assets").resolve("kingdomworld").resolve("guide");
        files = new TreeMap<>();
        for (String language : List.of("ru_ru", "en_us")) {
            Map<String, String> texts = new TreeMap<>();
            try (Stream<Path> list = Files.list(guide.resolve(language))) {
                for (Path file : list.filter(path -> path.toString().endsWith(".md")).toList()) {
                    texts.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
            files.put(language, texts);
        }
        russian = GuideParser.book(files.get("ru_ru"));
        english = GuideParser.book(files.get("en_us"));
    }

    @Test
    void bothLanguagesHaveTheSameSectionsAndPages() {
        assertEquals(files.get("en_us").keySet(), files.get("ru_ru").keySet());
        assertFalse(russian.isEmpty());
        assertEquals(sections(english), sections(russian));
        for (int i = 0; i < russian.sections().size(); i++) {
            GuideSection ru = russian.sections().get(i);
            GuideSection en = english.sections().get(i);
            assertEquals(pages(en), pages(ru), "the pages of the section " + ru.id());
            assertEquals(en.icon(), ru.icon(), "the icon of the section " + ru.id());
        }
    }

    @Test
    void pagesShowTheSameLinksItemsGridsAndNumbersInBothLanguages() {
        List<String> problems = new ArrayList<>();
        for (GuidePage ru : russian.pages()) {
            Optional<GuidePage> found = english.page(ru.id());
            if (found.isEmpty()) {
                problems.add(ru.id() + " is not in English");
                continue;
            }
            GuidePage en = found.get();
            compare(problems, ru.id(), "links", ru.links(), en.links());
            compare(problems, ru.id(), "items", ru.items(), en.items());
            compare(problems, ru.id(), "crafting grids", ru.recipes(), en.recipes());
            compare(problems, ru.id(), "numbers", ru.numbers(), en.numbers());
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void linksLeadToPages() {
        List<String> problems = new ArrayList<>(russian.problems());
        english.problems().forEach(problem -> problems.add("en_us: " + problem));
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void russianPagesAreInRussianAndEnglishPagesInEnglish() {
        GuideText.Resolver quiet = new GuideText.Resolver(id -> " ", number -> " ");
        List<String> problems = new ArrayList<>();
        for (GuidePage page : russian.pages()) {
            List<String> texts = new ArrayList<>(List.of(page.title()));
            page.texts().forEach(text -> texts.add(GuideText.plain(text, quiet)));
            for (String text : texts) {
                long cyrillic = CYRILLIC.matcher(text).results().count();
                long latin = LATIN.matcher(text).results().count();
                if (cyrillic + latin >= COUNTED_LETTERS && cyrillic < RUSSIAN_SHARE * (cyrillic + latin)) {
                    problems.add("ru_ru " + page.id() + ": " + text);
                }
            }
        }
        for (GuidePage page : english.pages()) {
            List<String> texts = new ArrayList<>(List.of(page.title()));
            page.texts().forEach(text -> texts.add(GuideText.plain(text, quiet)));
            texts.stream().filter(text -> CYRILLIC.matcher(text).find()).forEach(text -> problems.add("en_us " + page.id() + ": " + text));
        }
        for (Map.Entry<String, Map<String, String>> language : files.entrySet()) {
            for (Map.Entry<String, String> file : language.getValue().entrySet()) {
                if (file.getValue().indexOf('\u2014') >= 0) {
                    problems.add(language.getKey() + "/" + file.getKey() + " has a long dash");
                }
                if (file.getValue().indexOf('\r') >= 0) {
                    problems.add(language.getKey() + "/" + file.getKey() + " has line ends of Windows");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void numbersAreValuesOfTheConfigurationOfTheServer() {
        Map<String, String> defaults = GuideNumbers.defaults();
        assertEquals("25", defaults.get("territory.baseMaxChunks"));
        List<String> problems = new ArrayList<>();
        for (GuideBook book : List.of(russian, english)) {
            for (GuidePage page : book.pages()) {
                page.forEachInline(inline -> {
                    if (inline instanceof GuideInline.Number number) {
                        String raw = defaults.get(number.path());
                        if (raw == null) {
                            problems.add(page.id() + ": " + number.token() + " is no plain value of the configuration of the server");
                        } else if (GuideNumbersFormat.format(raw, number, false).equals(GuideNumbersFormat.UNKNOWN)) {
                            problems.add(page.id() + ": " + number.token() + " cannot be shown from " + raw);
                        }
                    }
                });
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void itemsAndCraftingGridsExist() throws IOException {
        Set<String> lang = JsonParser.parseString(Files.readString(guide.getParent().resolve("lang").resolve("en_us.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().keySet();
        Optional<JsonObject> vanillaLang = classpathJson("assets/minecraft/lang/en_us.json");
        List<String> problems = new ArrayList<>();
        for (GuidePage page : russian.pages()) {
            for (String item : page.items()) {
                String[] parts = item.split(":", 2);
                Set<String> keys = parts[0].equals("kingdomworld") ? lang
                        : parts[0].equals("minecraft") && vanillaLang.isPresent() ? vanillaLang.get().keySet() : null;
                if (keys == null) {
                    if (!parts[0].equals("minecraft")) {
                        problems.add(page.id() + ": the item " + item + " is neither of the mod nor of the game");
                    }
                } else if (!keys.contains("item." + parts[0] + "." + parts[1]) && !keys.contains("block." + parts[0] + "." + parts[1])) {
                    problems.add(page.id() + ": there is no item " + item);
                }
            }
            for (String recipe : page.recipes()) {
                Optional<GuideRecipe> grid = data(GuideRecipe.dataPath("recipes", recipe))
                        .flatMap(json -> GuideRecipe.fromJson(json, tag -> GuideRecipe.tagItems(tag, id -> data(GuideRecipe.dataPath("tags/items", id)))));
                if (grid.isEmpty()) {
                    problems.add(page.id() + ": there is no crafting recipe " + recipe);
                } else if (grid.get().slots().stream().allMatch(List::isEmpty)) {
                    problems.add(page.id() + ": the crafting recipe " + recipe + " has no ingredients");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A data file of the mod, or of the game when its files are on the class path of the tests.
     */
    private static Optional<JsonObject> data(String path) {
        Path own = resources.resolve(path);
        if (Files.isRegularFile(own)) {
            try {
                return Optional.of(JsonParser.parseString(Files.readString(own, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return classpathJson(path);
    }

    private static Optional<JsonObject> classpathJson(String path) {
        try (InputStream stream = GuidePagesTest.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? Optional.empty()
                    : Optional.of(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static <T> void compare(List<String> problems, String page, String what, Set<T> russian, Set<T> english) {
        if (!russian.equals(english)) {
            problems.add(page + ": the " + what + " differ, Russian " + russian + ", English " + english);
        }
    }

    private static List<String> sections(GuideBook book) {
        return book.sections().stream().map(GuideSection::id).toList();
    }

    private static List<String> pages(GuideSection section) {
        return section.pages().stream().map(GuidePage::id).toList();
    }
}
