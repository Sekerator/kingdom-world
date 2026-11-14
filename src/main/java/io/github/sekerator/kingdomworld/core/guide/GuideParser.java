package io.github.sekerator.kingdomworld.core.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the files of the guide. A file holds one section:
 *
 * <pre>
 * # Title of the section
 * &#64;icon kingdomworld:throne
 *
 * ## page_id | Title of the page
 * &#64;icon minecraft:iron_axe
 * A paragraph: lines that follow each other are joined. **Emphasis**, [a link](other_page), an item {item:minecraft:iron_axe}
 * and a number of the configuration {cfg:territory.baseMaxChunks}, one of a table {cfg:guild.crystalChance#3*100} or a scaled
 * one {cfg:kingdom.styleChangeCost/100}.
 *
 * ### A heading
 * - an item of a list
 *   a line indented by two spaces goes on with the item
 * 1. an item of a numbered list
 * &gt; a note set apart in a box
 * | Header | Header |
 * |---|---|
 * | cell | cell |
 * {recipe:kingdomworld:throne} {recipe:kingdomworld:founder_crown}
 * %% a comment, never shown
 * </pre>
 *
 * <p>An empty line ends a paragraph, a list, a note or a table. A backslash shows the next character as it is
 * ({@code \*}, {@code \[}, {@code \{}, {@code \|}). Mistakes are reported with the number of the line.
 */
public final class GuideParser {
    /** The name of a file of the guide: an optional number that orders the sections, the name of the section, ".md". */
    private static final Pattern FILE = Pattern.compile("(?:\\d+-)?([a-z0-9_]+)\\.md");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern PAGE = Pattern.compile("##\\s+([^|\\s]+)\\s*\\|\\s*(.*\\S)\\s*");
    private static final Pattern ICON = Pattern.compile("@icon\\s+(\\S+)");
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    private static final Pattern NUMBERED = Pattern.compile("\\d+\\.\\s+(.*)");
    private static final Pattern RECIPES = Pattern.compile("(?:\\{recipe:[^}\\s]+}\\s*)+");
    private static final Pattern RECIPE = Pattern.compile("\\{recipe:([^}\\s]+)}");
    private static final Pattern TABLE_RULE = Pattern.compile("\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?");
    private static final Pattern NUMBER = Pattern.compile("([A-Za-z0-9_.]+)(?:#(\\d+))?(?:([*/])(\\d+(?:\\.\\d+)?))?");

    private GuideParser() {
    }

    /**
     * Reads a whole book from its files, ordered by their names.
     *
     * @param files the text of every file by its name ({@code 01-kingdom.md})
     * @throws GuideFormatException for a mistake in a file, naming the file and the line
     */
    public static GuideBook book(Map<String, String> files) {
        List<GuideSection> sections = new ArrayList<>();
        for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
            String id = sectionId(file.getKey());
            if (id == null) {
                throw new GuideFormatException(file.getKey() + ": the name of a file of the guide is a number, a dash, the name of"
                        + " the section and .md");
            }
            try {
                sections.add(section(id, file.getValue()));
            } catch (GuideFormatException e) {
                throw new GuideFormatException(file.getKey() + ", " + e.getMessage());
            }
        }
        return new GuideBook(sections);
    }

    /**
     * The name of the section a file holds, or null when the name of the file is not one of the guide.
     */
    public static String sectionId(String fileName) {
        Matcher matcher = FILE.matcher(fileName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Reads one section.
     *
     * @throws GuideFormatException for a mistake, naming the line
     */
    public static GuideSection section(String id, String source) {
        return new Reader(id).read(source);
    }

    /**
     * Reads the running text of a line: emphasis, links, items and numbers.
     */
    public static List<GuideInline> inline(String text) {
        return inline(text, 0);
    }

    private static List<GuideInline> inline(String text, int line) {
        List<GuideInline> result = new ArrayList<>();
        StringBuilder words = new StringBuilder();
        boolean bold = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                words.append(text.charAt(i + 1));
                i += 2;
            } else if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                addText(result, words, bold);
                bold = !bold;
                i += 2;
            } else if (c == '[' && isLink(text, i)) {
                int close = text.indexOf("](", i);
                int end = text.indexOf(')', close);
                if (end < 0) {
                    throw error(line, "a link is written [text](page)");
                }
                String shown = unescape(text.substring(i + 1, close)).strip();
                String target = text.substring(close + 2, end).strip();
                if (shown.isEmpty() || !ID.matcher(target).matches()) {
                    throw error(line, "the link [" + shown + "](" + target + ") needs a text and the name of a page");
                }
                addText(result, words, bold);
                result.add(new GuideInline.Link(target, shown));
                i = end + 1;
            } else if (c == '{') {
                int end = text.indexOf('}', i);
                if (end < 0) {
                    throw error(line, "{ is not closed");
                }
                addText(result, words, bold);
                result.add(token(text.substring(i + 1, end), line));
                i = end + 1;
            } else {
                words.append(c);
                i++;
            }
        }
        if (bold) {
            throw error(line, "** is not closed");
        }
        addText(result, words, false);
        return result;
    }

    /**
     * Whether the bracket at {@code at} opens a link: its closing bracket comes before any other and a parenthesis follows it.
     * Other brackets are text, such as the optional words of a command ({@code /kingdom info [name]}).
     */
    private static boolean isLink(String text, int at) {
        for (int i = at + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                return false;
            } else if (c == ']') {
                return i + 1 < text.length() && text.charAt(i + 1) == '(';
            }
        }
        return false;
    }

    private static GuideInline token(String token, int line) {
        if (token.startsWith("item:")) {
            String id = token.substring("item:".length());
            if (!RESOURCE.matcher(id).matches()) {
                throw error(line, "{item:" + id + "} needs the id of an item, such as minecraft:iron_axe");
            }
            return new GuideInline.Item(id);
        }
        if (token.startsWith("cfg:")) {
            Matcher matcher = NUMBER.matcher(token.substring("cfg:".length()));
            if (!matcher.matches()) {
                throw error(line, "{" + token + "} is written {cfg:section.name}, {cfg:section.name#3} or {cfg:section.name/100}");
            }
            int index = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            char operator = matcher.group(3) == null ? 0 : matcher.group(3).charAt(0);
            double operand = matcher.group(4) == null ? 0 : Double.parseDouble(matcher.group(4));
            if (matcher.group(2) != null && index < 1) {
                throw error(line, "{" + token + "}: the numbers of a table are counted from 1");
            }
            return new GuideInline.Number(matcher.group(1), index, operator, operand);
        }
        throw error(line, "{" + token + "} is unknown: {item:...}, {cfg:...} and, alone on a line, {recipe:...}");
    }

    private static void addText(List<GuideInline> result, StringBuilder words, boolean bold) {
        if (words.isEmpty()) {
            return;
        }
        String text = words.toString();
        words.setLength(0);
        if (!result.isEmpty() && result.get(result.size() - 1) instanceof GuideInline.Text last && last.bold() == bold) {
            result.set(result.size() - 1, new GuideInline.Text(last.text() + text, bold));
        } else {
            result.add(new GuideInline.Text(text, bold));
        }
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                result.append(text.charAt(++i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static GuideFormatException error(int line, String message) {
        return new GuideFormatException(line > 0 ? "line " + line + ": " + message : message);
    }

    /**
     * Splits a row of a table into its cells, leaving escaped bars in the text.
     */
    static List<String> cells(String row) {
        String text = row.strip();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|") && !text.endsWith("\\|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                cell.append(c).append(text.charAt(++i));
            } else if (c == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    /**
     * Reads one file line by line.
     */
    private static final class Reader {
        private final String sectionId;
        private String sectionTitle;
        private String sectionIcon = "";
        private final List<GuidePage> pages = new ArrayList<>();
        private String pageId;
        private String pageTitle;
        private String pageIcon = "";
        private final List<GuideBlock> blocks = new ArrayList<>();
        private int line;

        /** The block being read: one of the kinds below, or none. */
        private Kind open = Kind.NONE;
        private int openLine;
        private final StringBuilder paragraph = new StringBuilder();
        private boolean numbered;
        private final List<StringBuilder> items = new ArrayList<>();
        private final List<Integer> itemLines = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();
        private final List<Integer> rowLines = new ArrayList<>();
        private boolean header;

        private enum Kind { NONE, PARAGRAPH, LIST, NOTE, TABLE }

        Reader(String sectionId) {
            this.sectionId = sectionId;
        }

        GuideSection read(String source) {
            String[] lines = source.replace("\r", "").split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                line = i + 1;
                readLine(lines[i].stripTrailing());
            }
            line = lines.length;
            flush();
            closePage();
            if (sectionTitle == null) {
                throw error(1, "a section starts with its title: # Title");
            }
            if (pages.isEmpty()) {
                throw error(line, "a section needs at least one page: ## page_id | Title");
            }
            return new GuideSection(sectionId, sectionTitle, sectionIcon, pages);
        }

        private void readLine(String text) {
            String trimmed = text.strip();
            if (trimmed.isEmpty()) {
                flush();
                return;
            }
            if (trimmed.startsWith("%%")) {
                return;
            }
            boolean indented = text.startsWith("  ") || text.startsWith("\t");
            if (trimmed.startsWith("# ")) {
                if (sectionTitle != null || pageId != null) {
                    throw error(line, "the title of the section (# Title) comes once, before the pages");
                }
                sectionTitle = trimmed.substring(2).strip();
                return;
            }
            if (trimmed.startsWith("## ")) {
                flush();
                closePage();
                Matcher matcher = PAGE.matcher(trimmed);
                if (!matcher.matches() || !ID.matcher(matcher.group(1)).matches()) {
                    throw error(line, "a page starts with ## page_id | Title, the id of small latin letters, digits and _");
                }
                if (sectionTitle == null) {
                    throw error(line, "a section starts with its title: # Title");
                }
                pageId = matcher.group(1);
                pageTitle = matcher.group(2);
                return;
            }
            if (trimmed.startsWith("@icon")) {
                Matcher matcher = ICON.matcher(trimmed);
                if (!matcher.matches() || !RESOURCE.matcher(matcher.group(1)).matches()) {
                    throw error(line, "an icon is written @icon namespace:item");
                }
                if (pageId == null && sectionTitle != null) {
                    sectionIcon = matcher.group(1);
                } else if (pageId != null && blocks.isEmpty() && open == Kind.NONE) {
                    pageIcon = matcher.group(1);
                } else {
                    throw error(line, "@icon stands right under the title of the section or of a page");
                }
                return;
            }
            if (pageId == null) {
                throw error(line, "text before the first page: a page starts with ## page_id | Title");
            }
            if (trimmed.startsWith("### ")) {
                flush();
                blocks.add(new GuideBlock.Heading(inline(trimmed.substring(4).strip(), line)));
                return;
            }
            if (indented && open == Kind.LIST) {
                items.get(items.size() - 1).append(' ').append(trimmed);
                return;
            }
            Matcher numberedItem = NUMBERED.matcher(trimmed);
            boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ");
            if (!indented && (bullet || numberedItem.matches())) {
                boolean isNumbered = !bullet;
                if (open != Kind.LIST || numbered != isNumbered) {
                    flush();
                    open = Kind.LIST;
                    openLine = line;
                    numbered = isNumbered;
                }
                items.add(new StringBuilder(bullet ? trimmed.substring(2).strip() : numberedItem.group(1).strip()));
                itemLines.add(line);
                return;
            }
            if (trimmed.startsWith(">")) {
                if (open != Kind.NOTE) {
                    flush();
                    open = Kind.NOTE;
                    openLine = line;
                }
                append(paragraph, trimmed.substring(1).strip());
                return;
            }
            if (trimmed.startsWith("|")) {
                if (open != Kind.TABLE) {
                    flush();
                    open = Kind.TABLE;
                    openLine = line;
                    header = false;
                }
                if (TABLE_RULE.matcher(trimmed).matches()) {
                    if (rows.size() != 1 || header) {
                        throw error(line, "the row of dashes of a table stands right under its first row, once");
                    }
                    header = true;
                } else {
                    rows.add(cells(trimmed));
                    rowLines.add(line);
                }
                return;
            }
            if (RECIPES.matcher(trimmed).matches()) {
                flush();
                List<String> ids = new ArrayList<>();
                Matcher recipe = RECIPE.matcher(trimmed);
                while (recipe.find()) {
                    if (!RESOURCE.matcher(recipe.group(1)).matches()) {
                        throw error(line, "{recipe:" + recipe.group(1) + "} needs the id of a recipe, such as kingdomworld:throne");
                    }
                    ids.add(recipe.group(1));
                }
                blocks.add(new GuideBlock.Recipes(ids));
                return;
            }
            if (trimmed.contains("{recipe:")) {
                throw error(line, "{recipe:...} stands alone on its line");
            }
            if (open != Kind.PARAGRAPH) {
                flush();
                open = Kind.PARAGRAPH;
                openLine = line;
            }
            append(paragraph, trimmed);
        }

        private static void append(StringBuilder text, String more) {
            if (!text.isEmpty() && !more.isEmpty()) {
                text.append(' ');
            }
            text.append(more);
        }

        /**
         * Ends the block being read.
         */
        private void flush() {
            switch (open) {
                case PARAGRAPH -> blocks.add(new GuideBlock.Paragraph(inline(paragraph.toString(), openLine)));
                case NOTE -> blocks.add(new GuideBlock.Note(inline(paragraph.toString(), openLine)));
                case LIST -> {
                    List<List<GuideInline>> parsed = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        parsed.add(inline(items.get(i).toString(), itemLines.get(i)));
                    }
                    blocks.add(new GuideBlock.Bullets(numbered, parsed));
                }
                case TABLE -> {
                    List<List<List<GuideInline>>> parsed = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) {
                        List<List<GuideInline>> cells = new ArrayList<>();
                        for (String cell : rows.get(i)) {
                            cells.add(inline(cell, rowLines.get(i)));
                        }
                        parsed.add(cells);
                    }
                    if (parsed.isEmpty()) {
                        throw error(openLine, "a table needs rows");
                    }
                    blocks.add(new GuideBlock.Table(header, parsed));
                }
                case NONE -> {
                }
            }
            open = Kind.NONE;
            paragraph.setLength(0);
            items.clear();
            itemLines.clear();
            rows.clear();
            rowLines.clear();
            header = false;
        }

        private void closePage() {
            if (pageId == null) {
                return;
            }
            if (blocks.isEmpty()) {
                throw error(line, "the page " + pageId + " is empty");
            }
            pages.add(new GuidePage(pageId, sectionId, pageTitle, pageIcon, blocks));
            pageId = null;
            pageTitle = null;
            pageIcon = "";
            blocks.clear();
        }
    }
}
