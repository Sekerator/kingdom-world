package io.github.sekerator.kingdomworld.core.guide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideSearchTest {
    private static final GuideBook BOOK = GuideParser.book(Map.of(
            "01-kingdom.md", """
                    # Королевство

                    ## founding | Основание королевства
                    Поставьте трон {item:kingdomworld:throne} и наденьте корону. Ёлки вокруг трона не мешают.

                    ## land | Земля
                    Королевство владеет {cfg:territory.baseMaxChunks} чанками. Трон стоит в сердце земли.
                    """,
            "02-money.md", """
                    # Деньги

                    ## coins | Монеты
                    Медяк, серебряник и золотой. Казна ЁМКАЯ.
                    """));
    private static final GuideText.Resolver RESOLVER = new GuideText.Resolver(id -> id.equals("kingdomworld:throne") ? "Трон" : id,
            number -> "25");
    private static final GuideSearch SEARCH = new GuideSearch(BOOK, RESOLVER);

    @Test
    void foldsCaseAndYo() {
        assertEquals("ёлка".length(), GuideSearch.fold("ЁЛКА").length());
        assertEquals("елка", GuideSearch.fold("ЁЛКА"));
        assertEquals("throne", GuideSearch.fold("ThRoNe"));
        assertEquals(List.of("трон", "елки"), GuideSearch.words("  Трон,  «ЁЛКИ»! трон "));
        assertTrue(GuideSearch.words(" ... ").isEmpty());
    }

    @Test
    void findsPagesWithEveryWordTitlesFirst() {
        assertEquals(List.of("founding", "land"), ids(SEARCH.find("трон")));
        assertEquals(List.of("founding", "land"), ids(SEARCH.find("КОРОЛЕВСТВ")));
        assertEquals(List.of("founding"), ids(SEARCH.find("трон корону")));
        assertEquals(List.of("coins"), ids(SEARCH.find("емкая")));
        assertEquals(List.of("founding"), ids(SEARCH.find("елки")));
        assertEquals(List.of("land"), ids(SEARCH.find("25 чанками")));
        assertTrue(SEARCH.find("дракон").isEmpty());
        assertTrue(SEARCH.find("   ").isEmpty());
    }

    @Test
    void findsTheNamesOfItems() {
        GuideSearch raw = new GuideSearch(BOOK, GuideText.Resolver.RAW);
        assertEquals(List.of("founding"), ids(raw.find("kingdomworld:throne")));
    }

    @Test
    void tellsWhereTheWordsStand() {
        GuideSearch.Hit hit = SEARCH.find("ёлки трон").get(0);
        assertEquals("founding", hit.page().id());
        assertEquals("Основание королевства", hit.title());
        assertTrue(hit.titleRanges().isEmpty());
        assertFalse(hit.cutBefore());
        String snippet = hit.snippet();
        for (GuideSearch.Range range : hit.snippetRanges()) {
            String word = GuideSearch.fold(snippet.substring(range.start(), range.end()));
            assertTrue(word.equals("трон") || word.equals("елки"), word);
        }
        assertEquals(4, hit.snippetRanges().size(), hit.snippetRanges().toString());
        GuideSearch.Hit land = SEARCH.find("земли").get(0);
        assertEquals(List.of(), land.titleRanges());
        assertEquals("land", land.page().id());
    }

    @Test
    void cutsALongTextAroundTheFirstWord() {
        String words = "слово ".repeat(100);
        GuideBook book = GuideParser.book(Map.of("01-a.md", "# A\n\n## long | Длинная\n" + words + "дракон " + words + "\n"));
        GuideSearch.Hit hit = new GuideSearch(book, GuideText.Resolver.RAW).find("дракон").get(0);
        assertTrue(hit.cutBefore() && hit.cutAfter(), hit.toString());
        assertTrue(hit.snippet().length() <= GuideSearch.SNIPPET, hit.snippet());
        GuideSearch.Range range = hit.snippetRanges().get(0);
        assertEquals("дракон", hit.snippet().substring(range.start(), range.end()));
        assertTrue(range.start() <= GuideSearch.BEFORE, String.valueOf(range.start()));
    }

    @Test
    void joinsRangesThatMeet() {
        assertEquals(List.of(new GuideSearch.Range(0, 6)), GuideSearch.ranges("абвгде", List.of("абв", "вгде")));
        assertEquals(List.of(new GuideSearch.Range(0, 2), new GuideSearch.Range(3, 5)), GuideSearch.ranges("ab ab", List.of("ab")));
    }

    private static List<String> ids(List<GuideSearch.Hit> hits) {
        return hits.stream().map(hit -> hit.page().id()).toList();
    }
}
