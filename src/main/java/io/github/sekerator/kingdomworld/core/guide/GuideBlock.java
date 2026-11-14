package io.github.sekerator.kingdomworld.core.guide;

import java.util.List;

/**
 * A block of a page of the guide, laid out one under the other: a heading, a paragraph, a list, a note, a table or a row of
 * crafting grids.
 */
public sealed interface GuideBlock {

    /**
     * A heading inside the page ({@code ### Heading}).
     */
    record Heading(List<GuideInline> text) implements GuideBlock {
        public Heading {
            text = List.copyOf(text);
        }
    }

    /**
     * A paragraph: the lines of the file that follow each other, joined.
     */
    record Paragraph(List<GuideInline> text) implements GuideBlock {
        public Paragraph {
            text = List.copyOf(text);
        }
    }

    /**
     * A list ({@code - item}), numbered when written {@code 1. item}.
     */
    record Bullets(boolean numbered, List<List<GuideInline>> items) implements GuideBlock {
        public Bullets {
            items = items.stream().map(List::copyOf).toList();
        }
    }

    /**
     * A note set apart in a box ({@code > text}): a hint or a warning.
     */
    record Note(List<GuideInline> text) implements GuideBlock {
        public Note {
            text = List.copyOf(text);
        }
    }

    /**
     * A table ({@code | a | b |}); its first row is a header when a row of dashes follows it.
     *
     * @param rows every row, each a list of cells
     */
    record Table(boolean header, List<List<List<GuideInline>>> rows) implements GuideBlock {
        public Table {
            rows = rows.stream().map(row -> row.stream().map(List::copyOf).toList()).toList();
        }

        public int columns() {
            return rows.stream().mapToInt(List::size).max().orElse(0);
        }
    }

    /**
     * Crafting grids of the recipes {@code ids} side by side ({@code {recipe:kingdomworld:throne}}), taken from the recipes of
     * the game.
     */
    record Recipes(List<String> ids) implements GuideBlock {
        public Recipes {
            ids = List.copyOf(ids);
        }
    }
}
