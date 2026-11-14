package io.github.sekerator.kingdomworld.core.guide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideParserTest {
    private static final String SECTION = """
            # Королевство
            @icon kingdomworld:throne

            ## founding | Основание
            @icon kingdomworld:founder_crown
            Поставьте **трон** {item:kingdomworld:throne}
            и возьмите [корону](crown): {cfg:territory.baseMaxChunks} чанков.

            ### Что нужно
            - золото
              и шерсть
            - доски
            1. первое
            2. второе

            > Совет \\*не\\* забыть.
            > Вторая строка.

            | Что | Сколько |
            |---|---|
            | База | {cfg:territory.baseMaxChunks} |
            | Шанс \\| доля | {cfg:guild.crystalChance#3*100}% |

            {recipe:kingdomworld:throne} {recipe:kingdomworld:founder_crown}
            %% комментарий

            ## crown | Корона
            Текст.
            """;

    @Test
    void readsASectionWithItsPagesAndBlocks() {
        GuideSection section = GuideParser.section("kingdom", SECTION);
        assertEquals("kingdom", section.id());
        assertEquals("Королевство", section.title());
        assertEquals("kingdomworld:throne", section.icon());
        assertEquals(2, section.pages().size());
        GuidePage page = section.pages().get(0);
        assertEquals("founding", page.id());
        assertEquals("kingdom", page.section());
        assertEquals("Основание", page.title());
        assertEquals("kingdomworld:founder_crown", page.icon());
        List<GuideBlock> blocks = page.blocks();
        assertEquals(7, blocks.size(), blocks.toString());
        GuideBlock.Paragraph paragraph = assertInstanceOf(GuideBlock.Paragraph.class, blocks.get(0));
        assertEquals(List.of(new GuideInline.Text("Поставьте ", false), new GuideInline.Text("трон", true), new GuideInline.Text(" ", false),
                new GuideInline.Item("kingdomworld:throne"), new GuideInline.Text(" и возьмите ", false), new GuideInline.Link("crown", "корону"),
                new GuideInline.Text(": ", false), new GuideInline.Number("territory.baseMaxChunks", 0, (char) 0, 0),
                new GuideInline.Text(" чанков.", false)), paragraph.text());
        assertInstanceOf(GuideBlock.Heading.class, blocks.get(1));
        GuideBlock.Bullets bullets = assertInstanceOf(GuideBlock.Bullets.class, blocks.get(2));
        assertFalse(bullets.numbered());
        assertEquals(List.of(List.of(new GuideInline.Text("золото и шерсть", false)), List.of(new GuideInline.Text("доски", false))),
                bullets.items());
        GuideBlock.Bullets numbered = assertInstanceOf(GuideBlock.Bullets.class, blocks.get(3));
        assertTrue(numbered.numbered());
        assertEquals(2, numbered.items().size());
        GuideBlock.Note note = assertInstanceOf(GuideBlock.Note.class, blocks.get(4));
        assertEquals(List.of(new GuideInline.Text("Совет *не* забыть. Вторая строка.", false)), note.text());
        GuideBlock.Table table = assertInstanceOf(GuideBlock.Table.class, blocks.get(5));
        assertTrue(table.header());
        assertEquals(3, table.rows().size());
        assertEquals(2, table.columns());
        assertEquals(List.of(new GuideInline.Text("Шанс | доля", false)), table.rows().get(2).get(0));
        assertEquals(List.of(new GuideInline.Number("guild.crystalChance", 3, '*', 100), new GuideInline.Text("%", false)),
                table.rows().get(2).get(1));
        GuideBlock.Recipes recipes = assertInstanceOf(GuideBlock.Recipes.class, blocks.get(6));
        assertEquals(List.of("kingdomworld:throne", "kingdomworld:founder_crown"), recipes.ids());
    }

    @Test
    void tellsWhatAPageHolds() {
        GuidePage page = GuideParser.section("kingdom", SECTION).pages().get(0);
        assertEquals(List.of("crown"), List.copyOf(page.links()));
        assertEquals(List.of("kingdomworld:founder_crown", "kingdomworld:throne"), List.copyOf(page.items()));
        assertEquals(List.of("kingdomworld:throne", "kingdomworld:founder_crown"), List.copyOf(page.recipes()));
        assertEquals(List.of("{cfg:territory.baseMaxChunks}", "{cfg:guild.crystalChance#3*100}"), List.copyOf(page.numbers()));
    }

    @Test
    void readsABookOrderedByTheNamesOfItsFiles() {
        GuideBook book = GuideParser.book(Map.of(
                "02-land.md", "# Land\n\n## territory | Territory\nThe land, see [founding](founding).\n",
                "01-kingdom.md", SECTION));
        assertEquals(List.of("kingdom", "land"), book.sections().stream().map(GuideSection::id).toList());
        assertEquals(List.of("founding", "crown", "territory"), book.pages().stream().map(GuidePage::id).toList());
        assertEquals("founding", book.first().orElseThrow().id());
        assertTrue(book.problems().isEmpty(), book.problems().toString());
        assertEquals(List.of("territory.baseMaxChunks", "guild.crystalChance"), List.copyOf(book.numberPaths()));
    }

    @Test
    void findsLinksToMissingPagesAndPagesTwice() {
        GuideBook book = GuideParser.book(Map.of(
                "01-a.md", "# A\n\n## one | One\nSee [two](two) and [nowhere](nowhere).\n",
                "02-b.md", "# B\n\n## one | One again\nText.\n"));
        assertEquals(List.of("page one appears twice", "page one links to the missing page two",
                "page one links to the missing page nowhere"), book.problems());
    }

    @Test
    void namesTheLineOfAMistake() {
        assertMistake("line 4: ** is not closed", "# A\n\n## one | One\nText **bold.\n");
        assertMistake("line 3: a page starts with ## page_id", "# A\n\n## One Page\nText.\n");
        assertMistake("line 4: {color:red} is unknown", "# A\n\n## one | One\nText {color:red}.\n");
        assertMistake("line 4: {recipe:...} stands alone on its line", "# A\n\n## one | One\nText {recipe:minecraft:bread}.\n");
        assertMistake("line 3: text before the first page", "# A\n\nText.\n");
        assertMistake("line 1: a section starts with its title", "## one | One\nText.\n");
        assertMistake("line 5: the page one is empty", "# A\n\n## one | One\n\n## two | Two\nText.\n");
        assertMistake("line 4: the link [x]() needs a text and the name of a page", "# A\n\n## one | One\nSee [x]().\n");
        assertMistake("line 6: the row of dashes", "# A\n\n## one | One\n| a |\n| b |\n|---|\n");
        assertMistake("line 4: {cfg:a.b#0}: the numbers of a table are counted from 1", "# A\n\n## one | One\n{cfg:a.b#0}\n");
        GuideFormatException file = assertThrows(GuideFormatException.class, () -> GuideParser.book(Map.of("01-a.md", "# A\n\n## one | One\n**x\n")));
        assertTrue(file.getMessage().startsWith("01-a.md, line 4"), file.getMessage());
        assertThrows(GuideFormatException.class, () -> GuideParser.book(Map.of("Notes.txt", "# A")));
    }

    @Test
    void leavesBracketsThatAreNoLinkAsText() {
        assertEquals(List.of(new GuideInline.Text("/kingdom info [name] and [a [b] ", false), new GuideInline.Link("land", "land")),
                GuideParser.inline("/kingdom info [name] and [a [b] [land](land)"));
    }

    @Test
    void knowsTheSectionOfAFile() {
        assertEquals("kingdom", GuideParser.sectionId("02-kingdom.md"));
        assertEquals("land", GuideParser.sectionId("land.md"));
        assertEquals(null, GuideParser.sectionId("02-Kingdom.md"));
        assertEquals(null, GuideParser.sectionId("02-kingdom.txt"));
    }

    private static void assertMistake(String expected, String source) {
        GuideFormatException mistake = assertThrows(GuideFormatException.class, () -> GuideParser.section("a", source));
        assertTrue(mistake.getMessage().startsWith(expected), mistake.getMessage());
    }
}
