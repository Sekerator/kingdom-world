package io.github.sekerator.kingdomworld.client.guide;

import io.github.sekerator.kingdomworld.core.guide.GuideBlock;
import io.github.sekerator.kingdomworld.core.guide.GuideInline;
import io.github.sekerator.kingdomworld.core.guide.GuidePage;
import io.github.sekerator.kingdomworld.core.guide.GuideSearch;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Lays out a page of the guide (or the results of a search) for a width: lines of text with emphasis, links, numbers and the
 * icons of items, lists, notes, tables and crafting grids, each piece at its place measured from the top left corner of the
 * page. The screen draws the pieces and finds what is under the mouse with them.
 */
public final class GuideLayout {
    public static final int LINE = 10;
    public static final int GAP = 6;
    public static final int ICON = 8;
    public static final int SLOT = 18;
    public static final int GRID_WIDTH = 3 * SLOT + 8 + 16 + 8 + SLOT;
    public static final int GRID_HEIGHT = 3 * SLOT;
    private static final int GRID_GAP = 14;
    private static final int BULLET_INDENT = 10;
    private static final int NOTE_PAD = 5;
    private static final int CELL_PAD = 3;
    private static final int MIN_COLUMN = 28;
    private static final Pattern NUMERIC = Pattern.compile("[\\d\\s.,%+\\-x×/:]+");

    public static final int TEXT = 0xFFE8E0D0;
    public static final int MUTED = 0xFFA89A84;
    public static final int TITLE = 0xFFF0D070;
    public static final int BOLD = 0xFFFFDC7A;
    public static final int LINK = 0xFF9FD8FF;
    public static final int LINK_HOVER = 0xFFD8F0FF;
    public static final int NUMBER = 0xFFF0D070;
    public static final int GOLD = 0xFFB08A4A;
    public static final int GOLD_DARK = 0xFF6B5030;
    public static final int HIGHLIGHT = 0x90C89A30;
    public static final int NOTE_BACK = 0x28F0D070;
    public static final int TABLE_HEAD = 0x40B08A4A;
    public static final int TABLE_LINE = 0x508B6B3A;
    public static final int TABLE_STRIPE = 0x10FFFFFF;

    /**
     * How a piece of text looks.
     */
    public enum Kind {
        TEXT(Style.EMPTY, GuideLayout.TEXT),
        BOLD(Style.EMPTY.withBold(true), GuideLayout.BOLD),
        LINK(Style.EMPTY.withUnderlined(true), GuideLayout.LINK),
        NUMBER(Style.EMPTY, GuideLayout.NUMBER),
        HEADING(Style.EMPTY.withBold(true), GuideLayout.TITLE),
        TITLE(Style.EMPTY.withBold(true), GuideLayout.TITLE),
        MUTED(Style.EMPTY, GuideLayout.MUTED);

        public final Style style;
        public final int color;

        Kind(Style style, int color) {
            this.style = style;
            this.color = color;
        }
    }

    /** A piece of a laid out page. */
    public sealed interface Piece {
    }

    /**
     * A line of text of one look. {@code target} is the page a link or a result leads to.
     */
    public record Run(int x, int y, String text, Kind kind, @Nullable String target, int width) implements Piece {
    }

    /**
     * The icon of an item, {@code size} pixels wide.
     */
    public record Icon(int x, int y, ItemStack stack, int size) implements Piece {
    }

    /**
     * A filled rectangle: the back of a note, a line of a table, a highlighted word.
     */
    public record Box(int x1, int y1, int x2, int y2, int color) implements Piece {
    }

    /**
     * A crafting grid, or the note that the game has no such recipe.
     */
    public record Grid(int x, int y, String id, @Nullable GuideRecipes.Grid grid) implements Piece {
    }

    /**
     * A place that leads to a page when clicked: a result of the search.
     */
    public record Zone(int x1, int y1, int x2, int y2, String target) implements Piece {
    }

    /**
     * A laid out page: its pieces in the order of drawing and its height.
     */
    public record Result(List<Piece> pieces, int height) {
    }

    /**
     * What the layout needs to know of the game.
     *
     * @param numbers   the text of a number of the configuration
     * @param items     an item by its id
     * @param recipes   a crafting grid by the id of its recipe
     * @param highlight the folded words to highlight, or none
     */
    public record Context(Font font, Function<GuideInline.Number, String> numbers, Function<String, ItemStack> items,
                          Function<String, Optional<GuideRecipes.Grid>> recipes, List<String> highlight) {
    }

    private GuideLayout() {
    }

    // ------------------------------------------------------------------ pages

    /**
     * Lays out a page: the name of its section, its title with its icon and its blocks.
     */
    public static Result page(GuidePage page, String sectionTitle, int width, Context context) {
        List<Piece> pieces = new ArrayList<>();
        int y = 0;
        y = words(pieces, List.of(new GuideInline.Text(sectionTitle, false)), Kind.MUTED, 0, y, width, context);
        ItemStack icon = page.icon().isEmpty() ? ItemStack.EMPTY : context.items().apply(page.icon());
        int titleX = icon.isEmpty() ? 0 : 20;
        if (!icon.isEmpty()) {
            pieces.add(new Icon(0, y, icon, 16));
        }
        int titleTop = y + (icon.isEmpty() ? 1 : 4);
        int titleBottom = words(pieces, List.of(new GuideInline.Text(page.title(), false)), Kind.TITLE, titleX, titleTop, width, context);
        y = Math.max(titleBottom, icon.isEmpty() ? titleBottom : y + 18) + 2;
        pieces.add(new Box(0, y, width, y + 1, GOLD));
        y += 1 + GAP;
        boolean first = true;
        for (GuideBlock block : page.blocks()) {
            y = block(pieces, block, y, width, context, first);
            first = false;
        }
        return new Result(pieces, y);
    }

    private static int block(List<Piece> pieces, GuideBlock block, int y, int width, Context context, boolean first) {
        if (block instanceof GuideBlock.Heading heading) {
            int top = first ? y : y + 2;
            int bottom = words(pieces, heading.text(), Kind.HEADING, 0, top, width, context);
            pieces.add(new Box(0, bottom, Math.min(width, 40), bottom + 1, GOLD_DARK));
            return bottom + 4;
        }
        if (block instanceof GuideBlock.Paragraph paragraph) {
            return words(pieces, paragraph.text(), Kind.TEXT, 0, y, width, context) + GAP;
        }
        if (block instanceof GuideBlock.Bullets bullets) {
            int number = 1;
            int indent = bullets.numbered() ? context.font().width(bullets.items().size() + ".") + 4 : BULLET_INDENT;
            for (List<GuideInline> item : bullets.items()) {
                if (bullets.numbered()) {
                    String marker = number + ".";
                    run(pieces, 0, y, marker, Kind.BOLD, null, context);
                } else {
                    pieces.add(new Box(3, y + 3, 6, y + 6, GOLD));
                }
                y = words(pieces, item, Kind.TEXT, indent, y, width, context) + 2;
                number++;
            }
            return y - 2 + GAP;
        }
        if (block instanceof GuideBlock.Note note) {
            List<Piece> inner = new ArrayList<>();
            int bottom = words(inner, note.text(), Kind.TEXT, NOTE_PAD + 3, y + NOTE_PAD, width - NOTE_PAD, context);
            pieces.add(new Box(0, y, width, bottom + NOTE_PAD - 1, NOTE_BACK));
            pieces.add(new Box(0, y, 2, bottom + NOTE_PAD - 1, GOLD));
            pieces.addAll(inner);
            return bottom + NOTE_PAD - 1 + GAP;
        }
        if (block instanceof GuideBlock.Table table) {
            return table(pieces, table, y, width, context) + GAP;
        }
        if (block instanceof GuideBlock.Recipes recipes) {
            return grids(pieces, recipes.ids(), y, width, context) + GAP;
        }
        return y;
    }

    private static int grids(List<Piece> pieces, List<String> ids, int y, int width, Context context) {
        int x = 0;
        int rowBottom = y;
        for (String id : ids) {
            Optional<GuideRecipes.Grid> grid = context.recipes().apply(id);
            int gridWidth = grid.isPresent() ? GRID_WIDTH : width;
            if (x > 0 && x + gridWidth > width) {
                x = 0;
                y = rowBottom + GAP;
            }
            if (grid.isPresent()) {
                pieces.add(new Grid(x, y, id, grid.get()));
                rowBottom = Math.max(rowBottom, y + GRID_HEIGHT + LINE + 2);
                x += GRID_WIDTH + GRID_GAP;
            } else {
                pieces.add(new Grid(x, y, id, null));
                rowBottom = Math.max(rowBottom, y + LINE);
                x = width;
            }
        }
        return rowBottom;
    }

    // ------------------------------------------------------------------ tables

    private static int table(List<Piece> pieces, GuideBlock.Table table, int y, int width, Context context) {
        int columns = table.columns();
        if (columns == 0) {
            return y;
        }
        int[] natural = new int[columns];
        for (List<List<GuideInline>> row : table.rows()) {
            for (int c = 0; c < row.size(); c++) {
                natural[c] = Math.max(natural[c], naturalWidth(row.get(c), table.header() && row == table.rows().get(0), context) + 2 * CELL_PAD);
            }
        }
        int[] widths = fitColumns(natural, width);
        int tableWidth = 0;
        for (int w : widths) {
            tableWidth += w;
        }
        int top = y;
        for (int r = 0; r < table.rows().size(); r++) {
            List<List<GuideInline>> row = table.rows().get(r);
            boolean head = table.header() && r == 0;
            List<Piece> cells = new ArrayList<>();
            int bottom = y + CELL_PAD;
            int x = 0;
            for (int c = 0; c < columns; c++) {
                List<GuideInline> cell = c < row.size() ? row.get(c) : List.of();
                int inner = widths[c] - 2 * CELL_PAD;
                int cellX = x + CELL_PAD;
                if (!head && c > 0 && numeric(cell, context)) {
                    int used = naturalWidth(cell, false, context);
                    if (used <= inner) {
                        cellX = x + widths[c] - CELL_PAD - used;
                    }
                }
                int cellBottom = words(cells, cell, head ? Kind.BOLD : Kind.TEXT, cellX, y + CELL_PAD, x + widths[c] - CELL_PAD, context);
                bottom = Math.max(bottom, cellBottom);
                x += widths[c];
            }
            int rowBottom = bottom + CELL_PAD - 1;
            if (head) {
                pieces.add(new Box(0, y, tableWidth, rowBottom, TABLE_HEAD));
            } else if (r % 2 == 0) {
                pieces.add(new Box(0, y, tableWidth, rowBottom, TABLE_STRIPE));
            }
            pieces.add(new Box(0, rowBottom, tableWidth, rowBottom + 1, TABLE_LINE));
            pieces.addAll(cells);
            y = rowBottom + 1;
        }
        pieces.add(new Box(0, top, tableWidth, top + 1, TABLE_LINE));
        int x = 0;
        for (int c = 0; c < columns - 1; c++) {
            x += widths[c];
            pieces.add(new Box(x, top, x + 1, y, TABLE_LINE));
        }
        return y;
    }

    /**
     * Widths of the columns: as wide as their texts when the table fits, otherwise the widest columns give way first.
     */
    static int[] fitColumns(int[] natural, int width) {
        int columns = natural.length;
        int[] widths = natural.clone();
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        if (total <= width) {
            return widths;
        }
        // Water filling: every column gets an equal share, columns that need less give the rest to the others.
        boolean[] fixed = new boolean[columns];
        int left = width;
        int open = columns;
        boolean changed = true;
        while (changed && open > 0) {
            changed = false;
            int share = left / open;
            for (int c = 0; c < columns; c++) {
                if (!fixed[c] && natural[c] <= share) {
                    fixed[c] = true;
                    widths[c] = natural[c];
                    left -= natural[c];
                    open--;
                    changed = true;
                }
            }
        }
        if (open > 0) {
            int share = Math.max(MIN_COLUMN, left / open);
            int extra = left - share * open;
            for (int c = 0; c < columns; c++) {
                if (!fixed[c]) {
                    widths[c] = share + (extra > 0 ? 1 : 0);
                    extra--;
                }
            }
        }
        return widths;
    }

    private static boolean numeric(List<GuideInline> cell, Context context) {
        StringBuilder text = new StringBuilder();
        for (GuideInline inline : cell) {
            if (inline instanceof GuideInline.Text words) {
                text.append(words.text());
            } else if (inline instanceof GuideInline.Number number) {
                text.append(context.numbers().apply(number));
            } else {
                return false;
            }
        }
        String plain = text.toString().strip();
        return !plain.isEmpty() && NUMERIC.matcher(plain).matches();
    }

    private static int naturalWidth(List<GuideInline> text, boolean bold, Context context) {
        int width = 0;
        for (Atom atom : atoms(text, bold ? Kind.BOLD : Kind.TEXT, context)) {
            width += atom.width;
        }
        return width;
    }

    // ------------------------------------------------------------------ results of the search

    /**
     * Lays out the results of a search: how many pages were found, then every page with its section and a piece of its text.
     *
     * @param sections the title of the section of every page found, by page
     */
    public static Result results(List<GuideSearch.Hit> hits, Function<String, String> sections, String count, String none,
                                 int width, Context context) {
        List<Piece> pieces = new ArrayList<>();
        int y = 0;
        y = words(pieces, List.of(new GuideInline.Text(hits.isEmpty() ? none : count, false)), Kind.MUTED, 0, y, width, context) + 4;
        Font font = context.font();
        for (GuideSearch.Hit hit : hits) {
            int top = y;
            String target = hit.page().id();
            String section = sections.apply(target);
            String sectionShown = fit(font, section, width / 3);
            int sectionWidth = font.width(sectionShown);
            String title = fit(font, FormattedText.of(hit.title(), Kind.TITLE.style), width - sectionWidth - 8, Kind.TITLE);
            run(pieces, 2, y + 2, title, Kind.TITLE, target, context);
            run(pieces, width - sectionWidth - 2, y + 2, sectionShown, Kind.MUTED, target, context);
            y += LINE + 3;
            String snippet = (hit.cutBefore() ? "… " : "") + hit.snippet() + (hit.cutAfter() ? " …" : "");
            List<Piece> lines = new ArrayList<>();
            int bottom = words(lines, List.of(new GuideInline.Text(snippet, false)), Kind.TEXT, 8, y, width - 2, context);
            int kept = 0;
            for (Piece piece : lines) {
                if (piece instanceof Run line && line.y() >= y + 2 * LINE) {
                    continue;
                }
                if (piece instanceof Box box && box.y1() + 1 >= y + 2 * LINE) {
                    continue;
                }
                pieces.add(piece instanceof Run line ? new Run(line.x(), line.y(), line.text(), line.kind(), target, line.width()) : piece);
                kept++;
            }
            if (bottom > y + 2 * LINE && kept > 0) {
                bottom = y + 2 * LINE;
                markCut(pieces, font, width - 2);
            }
            y = bottom + 4;
            pieces.add(new Zone(0, top, width, y - 1, target));
            pieces.add(new Box(0, y - 1, width, y, TABLE_LINE));
            y += 3;
        }
        return new Result(pieces, y);
    }

    /**
     * Ends the last line of a piece of text that goes on with an ellipsis.
     */
    private static void markCut(List<Piece> pieces, Font font, int right) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            if (pieces.get(i) instanceof Run last) {
                String shown = fit(font, last.text() + "…", right - last.x());
                if (!shown.endsWith("…")) {
                    shown = font.plainSubstrByWidth(last.text(), Math.max(0, right - last.x() - font.width("…"))) + "…";
                }
                pieces.set(i, new Run(last.x(), last.y(), shown, last.kind(), last.target(), font.width(shown)));
                return;
            }
        }
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static String fit(Font font, FormattedText text, int width, Kind kind) {
        String plain = text.getString();
        if (font.width(text) <= width) {
            return plain;
        }
        int ellipsis = font.width(FormattedText.of("…", kind.style));
        String cut = plain;
        while (!cut.isEmpty() && font.width(FormattedText.of(cut, kind.style)) + ellipsis > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    // ------------------------------------------------------------------ running text

    /**
     * A word, a space or an icon of running text with its width.
     */
    private record Atom(String text, Kind kind, @Nullable String target, @Nullable ItemStack icon, boolean space, int width) {
    }

    private static List<Atom> atoms(List<GuideInline> text, Kind base, Context context) {
        Font font = context.font();
        List<Atom> atoms = new ArrayList<>();
        for (GuideInline inline : text) {
            if (inline instanceof GuideInline.Text words) {
                split(atoms, words.text(), words.bold() && base == Kind.TEXT ? Kind.BOLD : base, null, font);
            } else if (inline instanceof GuideInline.Link link) {
                split(atoms, link.text(), Kind.LINK, link.target(), font);
            } else if (inline instanceof GuideInline.Number number) {
                split(atoms, context.numbers().apply(number), base == Kind.TEXT ? Kind.NUMBER : base, null, font);
            } else if (inline instanceof GuideInline.Item item) {
                ItemStack stack = context.items().apply(item.id());
                atoms.add(new Atom("", base, null, stack, false, ICON + 1));
            }
        }
        return atoms;
    }

    private static void split(List<Atom> atoms, String text, Kind kind, @Nullable String target, Font font) {
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ' ') {
                if (i > start) {
                    String word = text.substring(start, i);
                    atoms.add(new Atom(word, kind, target, null, false, font.width(FormattedText.of(word, kind.style))));
                }
                if (i < text.length()) {
                    atoms.add(new Atom(" ", kind, target, null, true, font.width(FormattedText.of(" ", kind.style))));
                }
                start = i + 1;
            }
        }
    }

    /**
     * Lays out running text from {@code x} to {@code right}, wrapping between words.
     *
     * @return the bottom of the last line
     */
    private static int words(List<Piece> pieces, List<GuideInline> text, Kind base, int x, int y, int right, Context context) {
        Font font = context.font();
        List<Atom> atoms = atoms(text, base, context);
        int lineX = x;
        int lineY = y;
        boolean lineEmpty = true;
        int spaceWidth = 0;
        boolean pendingSpace = false;
        List<Placed> line = new ArrayList<>();
        List<Placed> placed = new ArrayList<>();
        int i = 0;
        while (i < atoms.size()) {
            Atom atom = atoms.get(i);
            if (atom.space()) {
                pendingSpace = !lineEmpty;
                spaceWidth = atom.width();
                i++;
                continue;
            }
            // A chunk: atoms with no space between them never part.
            int end = i;
            int chunkWidth = 0;
            while (end < atoms.size() && !atoms.get(end).space()) {
                chunkWidth += atoms.get(end).width();
                end++;
            }
            int startX = lineX + (pendingSpace ? spaceWidth : 0);
            if (!lineEmpty && startX + chunkWidth > right) {
                placed.addAll(line);
                line.clear();
                lineY += LINE;
                lineX = x;
                startX = x;
                lineEmpty = true;
            }
            if (chunkWidth > right - x) {
                // A word longer than a whole line is cut where the line ends.
                for (int a = i; a < end; a++) {
                    Atom part = atoms.get(a);
                    String rest = part.text();
                    if (part.icon() != null) {
                        if (startX + part.width() > right && !lineEmpty) {
                            placed.addAll(line);
                            line.clear();
                            lineY += LINE;
                            startX = x;
                        }
                        line.add(new Placed(startX, lineY, part));
                        startX += part.width();
                        lineEmpty = false;
                        continue;
                    }
                    while (!rest.isEmpty()) {
                        String piece = font.plainSubstrByWidth(rest, Math.max(1, right - startX));
                        if (piece.isEmpty()) {
                            if (lineEmpty) {
                                piece = rest.substring(0, 1);
                            } else {
                                placed.addAll(line);
                                line.clear();
                                lineY += LINE;
                                startX = x;
                                lineEmpty = true;
                                continue;
                            }
                        }
                        int w = font.width(FormattedText.of(piece, part.kind().style));
                        line.add(new Placed(startX, lineY, new Atom(piece, part.kind(), part.target(), null, false, w)));
                        startX += w;
                        lineEmpty = false;
                        rest = rest.substring(piece.length());
                        if (!rest.isEmpty()) {
                            placed.addAll(line);
                            line.clear();
                            lineY += LINE;
                            startX = x;
                            lineEmpty = true;
                        }
                    }
                }
                lineX = startX;
            } else {
                for (int a = i; a < end; a++) {
                    Atom part = atoms.get(a);
                    line.add(new Placed(startX, lineY, part));
                    startX += part.width();
                }
                lineX = startX;
                lineEmpty = false;
            }
            pendingSpace = false;
            i = end;
        }
        placed.addAll(line);
        if (placed.isEmpty()) {
            return y;
        }
        emit(pieces, placed, context);
        return lineY + LINE;
    }

    private record Placed(int x, int y, Atom atom) {
    }

    /**
     * Turns placed words into runs, joining the words of one look that follow each other on a line.
     */
    private static void emit(List<Piece> pieces, List<Placed> placed, Context context) {
        Font font = context.font();
        StringBuilder text = new StringBuilder();
        int runX = 0;
        int runY = 0;
        int runEnd = 0;
        Kind kind = null;
        String target = null;
        for (Placed item : placed) {
            Atom atom = item.atom();
            if (atom.icon() != null) {
                flush(pieces, text, runX, runY, kind, target, context);
                kind = null;
                if (!atom.icon().isEmpty()) {
                    pieces.add(new Icon(item.x(), item.y(), atom.icon(), ICON));
                }
                continue;
            }
            boolean joins = kind == atom.kind() && java.util.Objects.equals(target, atom.target()) && runY == item.y()
                    && (item.x() == runEnd || item.x() == runEnd + font.width(FormattedText.of(" ", atom.kind().style)));
            if (joins) {
                if (item.x() != runEnd) {
                    text.append(' ');
                }
                text.append(atom.text());
            } else {
                flush(pieces, text, runX, runY, kind, target, context);
                runX = item.x();
                runY = item.y();
                kind = atom.kind();
                target = atom.target();
                text.append(atom.text());
            }
            runEnd = item.x() + atom.width();
        }
        flush(pieces, text, runX, runY, kind, target, context);
    }

    private static void flush(List<Piece> pieces, StringBuilder text, int x, int y, @Nullable Kind kind, @Nullable String target,
                              Context context) {
        if (text.isEmpty() || kind == null) {
            text.setLength(0);
            return;
        }
        run(pieces, x, y, text.toString(), kind, target, context);
        text.setLength(0);
    }

    /**
     * Adds a run of text, with boxes behind the words to highlight.
     */
    private static void run(List<Piece> pieces, int x, int y, String text, Kind kind, @Nullable String target, Context context) {
        Font font = context.font();
        if (!context.highlight().isEmpty()) {
            for (GuideSearch.Range range : GuideSearch.ranges(GuideSearch.fold(text), context.highlight())) {
                int from = x + font.width(FormattedText.of(text.substring(0, range.start()), kind.style));
                int to = x + font.width(FormattedText.of(text.substring(0, range.end()), kind.style));
                pieces.add(new Box(from - 1, y - 1, to, y + LINE - 1, HIGHLIGHT));
            }
        }
        pieces.add(new Run(x, y, text, kind, target, font.width(FormattedText.of(text, kind.style))));
    }
}
