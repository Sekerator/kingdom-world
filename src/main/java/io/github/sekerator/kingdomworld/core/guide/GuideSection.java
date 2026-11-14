package io.github.sekerator.kingdomworld.core.guide;

import java.util.List;
import java.util.Objects;

/**
 * A section of the guide: a branch of the tree on the left with its pages.
 *
 * @param id   the name of the section, the same in every language: the name of its file without the number in front
 * @param icon the item shown beside the title in the tree, or empty
 */
public record GuideSection(String id, String title, String icon, List<GuidePage> pages) {
    public GuideSection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        pages = List.copyOf(pages);
    }
}
