package io.github.sekerator.kingdomworld.core.guide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A page of the guide.
 *
 * @param id      the name of the page, the same in every language, used by links
 * @param section the name of the section the page stands in
 * @param icon    the item shown beside the title, or empty
 */
public record GuidePage(String id, String section, String title, String icon, List<GuideBlock> blocks) {
    public GuidePage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        blocks = List.copyOf(blocks);
    }

    /**
     * Every piece of running text of the page, in the order of the page: headings, paragraphs, items of lists, notes and cells.
     */
    public List<List<GuideInline>> texts() {
        List<List<GuideInline>> texts = new ArrayList<>();
        for (GuideBlock block : blocks) {
            if (block instanceof GuideBlock.Heading heading) {
                texts.add(heading.text());
            } else if (block instanceof GuideBlock.Paragraph paragraph) {
                texts.add(paragraph.text());
            } else if (block instanceof GuideBlock.Bullets bullets) {
                texts.addAll(bullets.items());
            } else if (block instanceof GuideBlock.Note note) {
                texts.add(note.text());
            } else if (block instanceof GuideBlock.Table table) {
                table.rows().forEach(texts::addAll);
            }
        }
        return texts;
    }

    /**
     * Calls {@code action} with every inline piece of the page.
     */
    public void forEachInline(Consumer<GuideInline> action) {
        texts().forEach(text -> text.forEach(action));
    }

    /**
     * The pages the page links to, each once, in the order of the page.
     */
    public Set<String> links() {
        Set<String> links = new LinkedHashSet<>();
        forEachInline(inline -> {
            if (inline instanceof GuideInline.Link link) {
                links.add(link.target());
            }
        });
        return links;
    }

    /**
     * The items shown in the text of the page, each once.
     */
    public Set<String> items() {
        Set<String> items = new LinkedHashSet<>();
        if (!icon.isEmpty()) {
            items.add(icon);
        }
        forEachInline(inline -> {
            if (inline instanceof GuideInline.Item item) {
                items.add(item.id());
            }
        });
        return items;
    }

    /**
     * The recipes of the crafting grids of the page, each once.
     */
    public Set<String> recipes() {
        Set<String> recipes = new LinkedHashSet<>();
        for (GuideBlock block : blocks) {
            if (block instanceof GuideBlock.Recipes grids) {
                recipes.addAll(grids.ids());
            }
        }
        return recipes;
    }

    /**
     * The numbers of the configuration the page shows, each once, as written.
     */
    public Set<String> numbers() {
        Set<String> numbers = new LinkedHashSet<>();
        forEachInline(inline -> {
            if (inline instanceof GuideInline.Number number) {
                numbers.add(number.token());
            }
        });
        return numbers;
    }
}
