package io.github.sekerator.kingdomworld.core.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The guide in one language: its sections in order, each with its pages.
 */
public final class GuideBook {
    private final List<GuideSection> sections;
    private final Map<String, GuidePage> pages = new LinkedHashMap<>();
    private final Map<String, GuideSection> sectionsById = new LinkedHashMap<>();

    public GuideBook(List<GuideSection> sections) {
        this.sections = List.copyOf(sections);
        for (GuideSection section : this.sections) {
            sectionsById.putIfAbsent(section.id(), section);
            for (GuidePage page : section.pages()) {
                pages.putIfAbsent(page.id(), page);
            }
        }
    }

    public static GuideBook empty() {
        return new GuideBook(List.of());
    }

    public List<GuideSection> sections() {
        return sections;
    }

    /**
     * Every page in the order of the book.
     */
    public List<GuidePage> pages() {
        return List.copyOf(pages.values());
    }

    public Optional<GuidePage> page(String id) {
        return Optional.ofNullable(id == null ? null : pages.get(id));
    }

    public Optional<GuideSection> section(String id) {
        return Optional.ofNullable(id == null ? null : sectionsById.get(id));
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }

    /**
     * The first page of the book, the one that opens when nothing was read yet.
     */
    public Optional<GuidePage> first() {
        return pages.values().stream().findFirst();
    }

    /**
     * Every path of the configuration the book shows a number of, each once.
     */
    public Set<String> numberPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (GuidePage page : pages.values()) {
            page.forEachInline(inline -> {
                if (inline instanceof GuideInline.Number number) {
                    paths.add(number.path());
                }
            });
        }
        return paths;
    }

    /**
     * Problems of the book as a whole: the same page or section twice and links to pages that do not exist.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        Set<String> seenSections = new LinkedHashSet<>();
        Set<String> seenPages = new LinkedHashSet<>();
        for (GuideSection section : sections) {
            if (!seenSections.add(section.id())) {
                problems.add("section " + section.id() + " appears twice");
            }
            if (section.pages().isEmpty()) {
                problems.add("section " + section.id() + " has no pages");
            }
            for (GuidePage page : section.pages()) {
                if (!seenPages.add(page.id())) {
                    problems.add("page " + page.id() + " appears twice");
                }
            }
        }
        for (GuidePage page : pages.values()) {
            for (String link : page.links()) {
                if (!pages.containsKey(link)) {
                    problems.add("page " + page.id() + " links to the missing page " + link);
                }
            }
        }
        return problems;
    }
}
