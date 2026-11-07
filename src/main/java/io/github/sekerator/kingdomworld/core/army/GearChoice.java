package io.github.sekerator.kingdomworld.core.army;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Which weapon, shield and armor a soldier takes from the storehouses. Gear of any mod is judged by what it does, not by
 * its name: a melee weapon by its attack damage and speed, a bow or a crossbow by its kind and enchantments, armor by its
 * defense and toughness, a shield by its durability. The best piece wins; of equal ones the most worn, so that gear is used
 * up one piece after another. The server config can change the score of items and tags or forbid them.
 */
public final class GearChoice {
    /** What a score override written as {@code deny} means. */
    public static final double DENIED = -1.0;

    /**
     * Where a piece of gear goes.
     */
    public enum Slot {
        MELEE,
        RANGED,
        SHIELD,
        HEAD,
        CHEST,
        LEGS,
        FEET;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A piece of gear that fits a slot.
     *
     * @param index          where the piece is, for the caller
     * @param score          how good it is; higher is better, nothing below or at 0 is taken
     * @param durabilityLeft uses left before it breaks; {@link Integer#MAX_VALUE} for gear that does not wear
     */
    public record Candidate(int index, Slot slot, double score, int durabilityLeft) {
    }

    private GearChoice() {
    }

    /**
     * Score of a melee weapon: damage per second, the attack damage (with the bare hand's point) times the attacks per second.
     *
     * @param damage attack damage the weapon adds
     * @param speed  attacks per second, 4 for a weapon that does not slow
     */
    public static double meleeScore(double damage, double speed) {
        if (!(damage > 0) || !Double.isFinite(damage)) {
            return 0.0;
        }
        double attacks = speed > 0 && Double.isFinite(speed) ? Math.min(4.0, speed) : 4.0;
        return (1.0 + damage) * attacks;
    }

    /**
     * Score of a piece of armor: its defense and half its toughness.
     */
    public static double armorScore(double defense, double toughness) {
        return Math.max(0.0, defense) + Math.max(0.0, toughness) / 2.0;
    }

    /**
     * The best piece for a slot, or empty if none is good for anything.
     */
    public static OptionalInt best(List<Candidate> candidates, Slot slot) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.slot() != slot || !(candidate.score() > 0)) {
                continue;
            }
            if (best == null || candidate.score() > best.score()
                    || (candidate.score() == best.score() && candidate.durabilityLeft() < best.durabilityLeft())) {
                best = candidate;
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best.index());
    }

    /**
     * Whether a piece is better than what is worn now, enough to swap: a better score, never the same one again.
     */
    public static boolean better(double candidate, double current) {
        return candidate > current * 1.05 + 1e-9;
    }

    // ---------------------------------------------------------------- overrides of the config

    /**
     * Score overrides by item id and by tag ({@code #namespace:path}), read from settings of the form {@code id=score} or
     * {@code id=deny}.
     */
    public record Overrides(Map<String, Double> items, Map<String, Double> tags) {
        public static final Overrides NONE = new Overrides(Map.of(), Map.of());

        public Overrides {
            items = Map.copyOf(items);
            tags = Map.copyOf(tags);
        }

        /**
         * The override of an item: its own, or the first of its tags; empty without one.
         */
        public OptionalDouble of(String item, List<String> itemTags) {
            Double value = items.get(item);
            if (value != null) {
                return OptionalDouble.of(value);
            }
            for (String tag : itemTags) {
                Double tagged = tags.get(tag);
                if (tagged != null) {
                    return OptionalDouble.of(tagged);
                }
            }
            return OptionalDouble.empty();
        }

        public boolean isEmpty() {
            return items.isEmpty() && tags.isEmpty();
        }
    }

    /**
     * Reads one override, {@code minecraft:trident=12}, {@code #forge:tools/knives=deny}.
     */
    public static Optional<Map.Entry<String, Double>> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int eq = text.indexOf('=');
        if (eq <= 0 || eq == text.length() - 1) {
            return Optional.empty();
        }
        String key = text.substring(0, eq).strip().toLowerCase(Locale.ROOT);
        String value = text.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
        String id = key.startsWith("#") ? key.substring(1) : key;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return Optional.empty();
        }
        if (value.equals("deny")) {
            return Optional.of(Map.entry(key, DENIED));
        }
        try {
            double score = Double.parseDouble(value);
            return Double.isFinite(score) && score >= 0 ? Optional.of(Map.entry(key, score)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the overrides of the config; entries that cannot be read are left out.
     */
    public static Overrides parseAll(List<? extends String> texts) {
        Map<String, Double> items = new LinkedHashMap<>();
        Map<String, Double> tags = new LinkedHashMap<>();
        for (String text : texts) {
            parse(text).ifPresent(entry -> {
                if (entry.getKey().startsWith("#")) {
                    tags.put(entry.getKey().substring(1), entry.getValue());
                } else {
                    items.put(entry.getKey(), entry.getValue());
                }
            });
        }
        return new Overrides(items, tags);
    }

    /**
     * The settings of the default config: nothing overridden.
     */
    public static List<String> defaultSettings() {
        return new ArrayList<>();
    }
}
