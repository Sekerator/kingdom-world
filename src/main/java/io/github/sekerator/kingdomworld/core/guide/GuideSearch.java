package io.github.sekerator.kingdomworld.core.guide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The search of the guide: finds the pages whose title or text holds every word of the query, ignoring case and the
 * difference between "ё" and "е", and tells where the words stand so that they can be highlighted.
 */
public final class GuideSearch {
    /** Letters of the text shown before the first word found in the piece of text of a result. */
    static final int BEFORE = 40;
    /** The most letters of the piece of text of a result. */
    static final int SNIPPET = 160;
    private static final int TITLE_WEIGHT = 20;

    private final List<Entry> entries;

    /**
     * A part of a text, from {@code start} up to {@code end}.
     */
    public record Range(int start, int end) {
    }

    /**
     * A page found: its title and a piece of its text, each with the places of the words found.
     *
     * @param snippetStart where the piece starts in the whole text of the page
     */
    public record Hit(GuidePage page, String title, List<Range> titleRanges, String snippet, List<Range> snippetRanges,
                      boolean cutBefore, boolean cutAfter, int snippetStart) {
    }

    private record Entry(GuidePage page, int order, String title, String foldedTitle, String text, String foldedText) {
    }

    /**
     * Prepares the search of a book.
     */
    public GuideSearch(GuideBook book, GuideText.Resolver resolver) {
        List<Entry> list = new ArrayList<>();
        int order = 0;
        for (GuidePage page : book.pages()) {
            String text = GuideText.plain(page, resolver);
            list.add(new Entry(page, order++, page.title(), fold(page.title()), text, fold(text)));
        }
        entries = List.copyOf(list);
    }

    /**
     * A text in the form the search compares: small letters, "ё" written as "е". Every letter stays at its place, so places
     * found in the folded text are places in the text itself.
     */
    public static String fold(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            result.append(c == 'ё' ? 'е' : c);
        }
        return result.toString();
    }

    /**
     * The words of a query, folded; punctuation around them does not count.
     */
    public static List<String> words(String query) {
        List<String> words = new ArrayList<>();
        for (String part : fold(query).split("\\s+")) {
            String word = strip(part);
            if (!word.isEmpty() && !words.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static String strip(String part) {
        int start = 0;
        int end = part.length();
        while (start < end && !Character.isLetterOrDigit(part.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(part.charAt(end - 1))) {
            end--;
        }
        return part.substring(start, end);
    }

    /**
     * The pages that hold every word of the query, those with the words in their title first, then those that hold them more
     * often, then in the order of the book. A query without words finds nothing.
     */
    public List<Hit> find(String query) {
        List<String> words = words(query);
        if (words.isEmpty()) {
            return List.of();
        }
        record Scored(Entry entry, int score) {
        }
        List<Scored> found = new ArrayList<>();
        for (Entry entry : entries) {
            int score = 0;
            boolean all = true;
            for (String word : words) {
                int inTitle = count(entry.foldedTitle(), word);
                int inText = count(entry.foldedText(), word);
                if (inTitle + inText == 0) {
                    all = false;
                    break;
                }
                score += inTitle * TITLE_WEIGHT + Math.min(inText, 10);
            }
            if (all) {
                found.add(new Scored(entry, score));
            }
        }
        found.sort(Comparator.comparingInt((Scored scored) -> -scored.score()).thenComparingInt(scored -> scored.entry().order()));
        List<Hit> hits = new ArrayList<>();
        for (Scored scored : found) {
            hits.add(hit(scored.entry(), words));
        }
        return hits;
    }

    private static Hit hit(Entry entry, List<String> words) {
        int first = -1;
        for (String word : words) {
            int at = entry.foldedText().indexOf(word);
            if (at >= 0 && (first < 0 || at < first)) {
                first = at;
            }
        }
        String text = entry.text();
        int start = first <= BEFORE ? 0 : wordStart(text, first - BEFORE);
        int end = Math.min(text.length(), start + SNIPPET);
        if (end < text.length()) {
            int space = text.lastIndexOf(' ', end);
            if (space > start + SNIPPET / 2) {
                end = space;
            }
        }
        String snippet = text.substring(start, end).strip();
        int shift = start + (text.substring(start, end).length() - text.substring(start, end).stripLeading().length());
        return new Hit(entry.page(), entry.title(), ranges(entry.foldedTitle(), words), snippet, ranges(fold(snippet), words),
                start > 0, end < text.length(), shift);
    }

    /**
     * Where a text read from {@code from} on starts its next word.
     */
    private static int wordStart(String text, int from) {
        int space = text.indexOf(' ', from);
        return space < 0 ? from : space + 1;
    }

    /**
     * The places of the words in a folded text, in order and joined where they meet or overlap.
     */
    public static List<Range> ranges(String folded, List<String> words) {
        List<Range> ranges = new ArrayList<>();
        for (String word : words) {
            int at = folded.indexOf(word);
            while (at >= 0) {
                ranges.add(new Range(at, at + word.length()));
                at = folded.indexOf(word, at + word.length());
            }
        }
        ranges.sort(Comparator.comparingInt(Range::start));
        List<Range> joined = new ArrayList<>();
        for (Range range : ranges) {
            if (!joined.isEmpty() && range.start() <= joined.get(joined.size() - 1).end()) {
                Range last = joined.remove(joined.size() - 1);
                joined.add(new Range(last.start(), Math.max(last.end(), range.end())));
            } else {
                joined.add(range);
            }
        }
        return joined;
    }

    private static int count(String text, String word) {
        int count = 0;
        int at = text.indexOf(word);
        while (at >= 0) {
            count++;
            at = text.indexOf(word, at + word.length());
        }
        return count;
    }
}
