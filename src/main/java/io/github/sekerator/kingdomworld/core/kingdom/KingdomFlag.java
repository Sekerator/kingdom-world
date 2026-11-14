package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The flag of a kingdom: the colour of its field and up to {@link #MAX_LAYERS} layers of patterns drawn over it in order, as a
 * banner made on a loom.
 *
 * <p>Colours are the sixteen dyes of the game by their number (white 0 to black 15, see {@link KingdomColors#dyeName(int)}); a
 * pattern is the id of a banner pattern of the game, for example {@code minecraft:cross}. A pattern with the right form that the
 * game does not know (one of a mod taken out) is kept and simply not drawn.
 *
 * @param base   the dye of the field
 * @param layers the patterns over the field, the lowest first
 */
public record KingdomFlag(int base, List<Layer> layers) {
    /** As many layers as a loom puts on a banner. */
    public static final int MAX_LAYERS = 6;
    /** The longest id of a pattern. */
    public static final int MAX_PATTERN_LENGTH = 128;
    /** The pattern of the field itself, which is never a layer. */
    public static final String BASE_PATTERN = "minecraft:base";
    private static final Pattern PATTERN_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /**
     * A pattern drawn in a dye.
     *
     * @param pattern the id of the banner pattern
     * @param dye     the dye it is drawn in
     */
    public record Layer(String pattern, int dye) {
        public Layer {
            Objects.requireNonNull(pattern, "pattern");
            if (!validPattern(pattern)) {
                throw new IllegalArgumentException("Not a pattern of a flag: '" + pattern + "'");
            }
            if (!validDye(dye)) {
                throw new IllegalArgumentException("Not a dye: " + dye);
            }
        }
    }

    public KingdomFlag {
        if (!validDye(base)) {
            throw new IllegalArgumentException("Not a dye: " + base);
        }
        layers = List.copyOf(layers);
        if (layers.size() > MAX_LAYERS) {
            throw new IllegalArgumentException("A flag holds at most " + MAX_LAYERS + " layers, not " + layers.size());
        }
    }

    /**
     * A flag of one colour.
     */
    public static KingdomFlag plain(int base) {
        return new KingdomFlag(base, List.of());
    }

    /**
     * Whether a dye number is one of the sixteen dyes.
     */
    public static boolean validDye(int dye) {
        return dye >= 0 && dye < KingdomColors.DYES;
    }

    /**
     * Whether a text has the form of the id of a pattern a flag may hold: {@code namespace:path}, at most
     * {@link #MAX_PATTERN_LENGTH} long, never the field itself.
     */
    public static boolean validPattern(String pattern) {
        return pattern != null && pattern.length() <= MAX_PATTERN_LENGTH && !BASE_PATTERN.equals(pattern)
                && PATTERN_ID.matcher(pattern).matches();
    }

    /**
     * Whether no further layer fits.
     */
    public boolean full() {
        return layers.size() >= MAX_LAYERS;
    }

    /**
     * The same flag on a field of another dye.
     */
    public KingdomFlag withBase(int newBase) {
        return new KingdomFlag(newBase, layers);
    }

    /**
     * The same flag with layer {@code index} replaced.
     */
    public KingdomFlag withLayer(int index, Layer layer) {
        Objects.requireNonNull(layer, "layer");
        List<Layer> changed = new ArrayList<>(layers);
        changed.set(index, layer);
        return new KingdomFlag(base, changed);
    }

    /**
     * The same flag with a layer added on top.
     *
     * @throws IllegalArgumentException if the flag is {@link #full()}
     */
    public KingdomFlag added(Layer layer) {
        Objects.requireNonNull(layer, "layer");
        List<Layer> changed = new ArrayList<>(layers);
        changed.add(layer);
        return new KingdomFlag(base, changed);
    }

    /**
     * The same flag without layer {@code index}.
     */
    public KingdomFlag removed(int index) {
        List<Layer> changed = new ArrayList<>(layers);
        changed.remove(index);
        return new KingdomFlag(base, changed);
    }

    /**
     * The same flag with layer {@code index} moved {@code delta} places up (towards the top, positive) or down, as far as the
     * layers go; a layer that does not exist leaves the flag as it is.
     */
    public KingdomFlag moved(int index, int delta) {
        if (index < 0 || index >= layers.size()) {
            return this;
        }
        int target = Math.max(0, Math.min(layers.size() - 1, index + delta));
        if (target == index) {
            return this;
        }
        List<Layer> changed = new ArrayList<>(layers);
        Layer layer = changed.remove(index);
        changed.add(target, layer);
        return new KingdomFlag(base, changed);
    }
}
