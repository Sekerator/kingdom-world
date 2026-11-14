package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The flags kingdoms have without a king drawing one: the flag of the colour of a kingdom, the default of every kingdom of
 * players, and the flags the kingdoms of the world are given, drawn by the style of their buildings and the character of
 * their ruler.
 *
 * <p>A generated flag lies on the field of the dye nearest to the colour of its kingdom, so that it matches the cloth its towns
 * already wear, and carries a motif of the heraldry of its style, of one or two patterns, and sometimes a border: one to three
 * layers. Every layer stands out from every other colour that lies under it: their contrast, as the contrast of text is
 * measured, is at least {@link #MIN_CONTRAST}. A further pattern takes a colour of its own only where one stands out from the
 * field and the first colour both; otherwise it takes the first colour again.
 */
public final class KingdomFlags {
    /** The least contrast of a layer of a generated flag with everything under it. */
    public static final double MIN_CONTRAST = 3.0;
    /** How often a generated flag gets a border its motif does not have. */
    private static final double BORDER_CHANCE = 0.25;
    /** How many seeds are tried before a flag already taken is given anyway. */
    private static final int ATTEMPTS = 16;
    private static final long SALT = 0x464C4147L;

    private static final int WHITE = 0;
    private static final int LIGHT_BLUE = 3;
    private static final int YELLOW = 4;
    private static final int PURPLE = 10;
    private static final int BLUE = 11;
    private static final int GREEN = 13;
    private static final int RED = 14;
    private static final int BLACK = 15;

    private static final String BORDER = "minecraft:border";
    private static final String CURLY_BORDER = "minecraft:curly_border";

    /** The motifs of the heraldry of each style with their weights; a motif of two patterns is drawn in two colours. */
    private static final Map<String, Map<List<String>, Integer>> MOTIFS = new LinkedHashMap<>();

    static {
        style("classic", "straight_cross", 3, "stripe_middle", 2, "stripe_top", 2, "border+circle", 2, "half_vertical", 2, "rhombus", 1);
        style("nordic", "straight_cross", 4, "straight_cross+border", 2, "cross", 2, "half_horizontal", 1);
        style("terem", "circle", 3, "half_horizontal", 2, "triangles_bottom", 2, "rhombus", 2, "stripe_top", 1);
        style("gothic", "cross", 3, "triangle_bottom", 2, "half_vertical", 2, "cross+curly_border", 2);
        style("southern", "small_stripes", 3, "stripe_center", 2, "stripe_downright", 2, "diagonal_right", 2);
        style("desert", "rhombus", 3, "circle", 2, "triangles_bottom", 2, "gradient_up", 1);
        style("eastern", "circle", 4, "border+circle", 2, "stripe_middle", 1);
        style("mountain", "triangles_bottom", 3, "triangle_bottom", 3, "half_horizontal", 2);
    }

    /** Every pattern a generated flag may carry. */
    public static final Set<String> GENERATED_PATTERNS;

    static {
        Set<String> patterns = new LinkedHashSet<>();
        for (Map<List<String>, Integer> motifs : MOTIFS.values()) {
            motifs.keySet().forEach(patterns::addAll);
        }
        patterns.add(id("cross"));
        patterns.add(id("triangle_bottom"));
        patterns.add(id("rhombus"));
        patterns.add(id("circle"));
        patterns.add(BORDER);
        patterns.add(CURLY_BORDER);
        GENERATED_PATTERNS = Set.copyOf(patterns);
    }

    private KingdomFlags() {
    }

    private static void style(String style, Object... motifs) {
        Map<List<String>, Integer> table = new LinkedHashMap<>();
        for (int i = 0; i < motifs.length; i += 2) {
            List<String> patterns = new ArrayList<>();
            for (String part : ((String) motifs[i]).split("\\+")) {
                patterns.add(id(part));
            }
            table.put(List.copyOf(patterns), (Integer) motifs[i + 1]);
        }
        MOTIFS.put(style, table);
    }

    private static String id(String path) {
        return "minecraft:" + path;
    }

    // ---------------------------------------------------------------- the flag of a colour

    /**
     * The default flag of a kingdom: a banner of one colour, the dye nearest to the colour of its name.
     */
    public static KingdomFlag standard(int color) {
        return KingdomFlag.plain(KingdomColors.nearestDye(color));
    }

    /**
     * The flag a kingdom keeps: {@code null} when it is the flag of its colour, so that the default is kept as the default and
     * follows a change of the colour.
     */
    public static KingdomFlag normalized(Kingdom kingdom, KingdomFlag flag) {
        return flag == null || flag.equals(standard(kingdom.color())) ? null : flag;
    }

    /**
     * The flag a kingdom flies: the one it keeps or the flag of its colour.
     */
    public static KingdomFlag of(Kingdom kingdom) {
        return kingdom.flag() != null ? kingdom.flag() : standard(kingdom.color());
    }

    // ---------------------------------------------------------------- contrast

    /**
     * How well two dyes stand out from each other, from 1 (the same) to 21, as the contrast of text with its background is
     * measured.
     */
    public static double contrast(int dyeA, int dyeB) {
        double a = luminance(KingdomColors.dyeRgb(dyeA));
        double b = luminance(KingdomColors.dyeRgb(dyeB));
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /**
     * The dye that stands out most from a field.
     */
    public static int mostContrasting(int field) {
        int best = WHITE;
        double bestContrast = -1;
        for (int dye = 0; dye < KingdomColors.DYES; dye++) {
            double contrast = contrast(field, dye);
            if (contrast > bestContrast) {
                bestContrast = contrast;
                best = dye;
            }
        }
        return best;
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel(rgb >> 16 & 0xFF) + 0.7152 * channel(rgb >> 8 & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static boolean dark(int field) {
        return contrast(field, WHITE) >= contrast(field, BLACK);
    }

    // ---------------------------------------------------------------- generated flags

    /**
     * The flag of a kingdom of the world. The same arguments always give the same flag; while it is one of {@code taken} the
     * next seeds are tried, up to sixteen of them, and the last is given even if taken.
     *
     * @param seed      what tells the kingdom from others
     * @param color     the colour of the kingdom
     * @param style     its architectural style; an empty or unknown one draws as the classic style
     * @param character the character of its ruler ({@code WARRIOR}, {@code MAGE}, {@code MERCHANT}, {@code BUILDER}), or
     *                  {@code null}
     * @param taken     the flags other kingdoms already fly
     */
    public static KingdomFlag generated(long seed, int color, String style, String character, Set<KingdomFlag> taken) {
        KingdomFlag flag = null;
        for (int i = 0; i < ATTEMPTS; i++) {
            flag = attempt(seed + i, color, style, character);
            if (!taken.contains(flag)) {
                return flag;
            }
        }
        return flag;
    }

    private static KingdomFlag attempt(long seed, int color, String style, String character) {
        SplittableRandom random = new SplittableRandom(seed ^ SALT);
        String leaning = character == null ? "" : character.toUpperCase(Locale.ROOT);
        int field = KingdomColors.nearestDye(color);
        Map<List<String>, Integer> motifs = motifs(style, leaning);
        List<String> motif = pick(random, motifs);
        List<Integer> under = new ArrayList<>(List.of(field));
        int first = pickDye(random, fitting(colors(field, leaning, false), under));
        if (first < 0) {
            // White or black stands out from every field.
            first = contrast(field, WHITE) >= contrast(field, BLACK) ? WHITE : BLACK;
        }
        under.add(first);
        List<KingdomFlag.Layer> layers = new ArrayList<>();
        layers.add(new KingdomFlag.Layer(motif.get(0), first));
        if (motif.size() > 1) {
            layers.add(new KingdomFlag.Layer(motif.get(1), second(random, field, leaning, under)));
        }
        boolean bordered = motif.contains(BORDER) || motif.contains(CURLY_BORDER);
        if (!bordered && random.nextDouble() < BORDER_CHANCE) {
            String border = random.nextBoolean() ? BORDER : CURLY_BORDER;
            layers.add(new KingdomFlag.Layer(border, second(random, field, leaning, under)));
        }
        return new KingdomFlag(field, layers);
    }

    /**
     * The colour of a further pattern: one that stands out from the field and the colours under it; when none does, the first
     * colour of the flag again, the way a coat of arms puts two charges of the same metal on its field.
     */
    private static int second(SplittableRandom random, int field, String leaning, List<Integer> under) {
        int dye = pickDye(random, fitting(colors(field, leaning, true), under));
        if (dye < 0) {
            return under.get(1);
        }
        under.add(dye);
        return dye;
    }

    /**
     * The motifs of a style, those its ruler likes weighing more.
     */
    private static Map<List<String>, Integer> motifs(String style, String leaning) {
        Map<List<String>, Integer> table = new LinkedHashMap<>(MOTIFS.getOrDefault(style == null ? "" : style, MOTIFS.get("classic")));
        switch (leaning) {
            case "WARRIOR" -> {
                table.merge(List.of(id("cross")), 2, Integer::sum);
                table.merge(List.of(id("triangle_bottom")), 2, Integer::sum);
            }
            case "MAGE" -> {
                table.merge(List.of(id("rhombus")), 2, Integer::sum);
                table.merge(List.of(id("circle")), 2, Integer::sum);
            }
            default -> {
            }
        }
        return table;
    }

    /**
     * The colours a motif may take on a field, with their weights: the metals white and yellow on a dark field, black and the
     * strong colours on a light one, and for a second colour white and black besides.
     */
    private static Map<Integer, Integer> colors(int field, String leaning, boolean second) {
        Map<Integer, Integer> colors = new LinkedHashMap<>();
        if (dark(field)) {
            colors.put(WHITE, 3);
            colors.put(YELLOW, 2);
        } else {
            colors.put(BLACK, 3);
            colors.put(RED, 2);
            colors.put(BLUE, 2);
            colors.put(GREEN, 1);
            colors.put(PURPLE, 1);
        }
        if (second) {
            colors.merge(WHITE, 2, Math::max);
            colors.merge(BLACK, 2, Math::max);
        }
        switch (leaning) {
            case "WARRIOR" -> {
                if (second) {
                    colors.merge(BLACK, 3, Integer::sum);
                }
            }
            case "MAGE" -> {
                colors.merge(PURPLE, 2, Integer::sum);
                colors.merge(LIGHT_BLUE, 2, Integer::sum);
            }
            case "MERCHANT" -> colors.merge(YELLOW, 3, Integer::sum);
            default -> {
            }
        }
        return colors;
    }

    /**
     * The colours that stand out from everything under them.
     */
    private static Map<Integer, Integer> fitting(Map<Integer, Integer> colors, List<Integer> under) {
        Map<Integer, Integer> fitting = new LinkedHashMap<>();
        colors.forEach((dye, weight) -> {
            if (under.stream().allMatch(below -> contrast(dye, below) >= MIN_CONTRAST)) {
                fitting.put(dye, weight);
            }
        });
        return fitting;
    }

    /**
     * One of the choices by its weight, or {@code null} when there is none.
     */
    private static <T> T pick(SplittableRandom random, Map<T, Integer> choices) {
        int total = 0;
        for (int weight : choices.values()) {
            total += weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Map.Entry<T, Integer> choice : choices.entrySet()) {
            roll -= choice.getValue();
            if (roll < 0) {
                return choice.getKey();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * One of the dyes by its weight, or -1 when there is none.
     */
    private static int pickDye(SplittableRandom random, Map<Integer, Integer> colors) {
        Integer dye = pick(random, colors);
        return dye == null ? -1 : dye;
    }
}
