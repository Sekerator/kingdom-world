package io.github.sekerator.kingdomworld.core.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Which metals the mint of a player's kingdom strikes and how many of each it leaves in the storehouses. A metal is one
 * rate of the mint, an item or a tag of the config such as {@code #forge:ingots/iron}. Iron is off until the king turns it
 * on, so that the iron the kingdom needs for tools and buildings does not go into coins; every other metal is struck and
 * none is left back. A treasury keeps only what its king changed, so a metal added to the config and the kingdoms of older
 * saves get these defaults. Kingdoms of the world strike every metal and leave nothing back ({@link #EVERYTHING}).
 */
public final class MintMetals {
    /** The most ingots of one metal the king can ask to leave in the storehouses. */
    public static final int MAX_KEEP = 10_000;
    /** The longest id of a metal (an item or a tag after {@code #}). */
    public static final int MAX_METAL_LENGTH = 128;
    /** The most metals a treasury keeps settings for. */
    public static final int MAX_SETTINGS = 64;

    /**
     * What the mint does with one metal.
     *
     * @param strike whether it strikes coins from it at all
     * @param keep   ingots of it that stay in the storehouses
     */
    public record Setting(boolean strike, int keep) {
        public Setting {
            if (keep < 0 || keep > MAX_KEEP) {
                throw new IllegalArgumentException("Metal to keep out of range: " + keep);
            }
        }

        public Setting withStrike(boolean value) {
            return new Setting(value, keep);
        }

        public Setting withKeep(int value) {
            return new Setting(strike, value);
        }
    }

    /** What kingdoms of the world do: strike every metal and leave nothing back. */
    public static final Setting EVERYTHING = new Setting(true, 0);

    private MintMetals() {
    }

    public static boolean isValidMetal(String metal) {
        if (metal == null || metal.isEmpty() || metal.length() > MAX_METAL_LENGTH) {
            return false;
        }
        for (int i = 0; i < metal.length(); i++) {
            if (Character.isWhitespace(metal.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a metal is iron, which a player's kingdom does not strike until its king says so: the tag of iron ingots or the
     * iron ingot of any mod.
     */
    public static boolean offByDefault(String metal) {
        String id = metal.startsWith("#") ? metal.substring(1) : metal;
        String path = id.substring(id.indexOf(':') + 1);
        return path.equals("ingots/iron") || path.equals("iron_ingot") || path.equals("iron_ingots");
    }

    /**
     * The setting of a metal the king has not changed.
     */
    public static Setting defaultFor(String metal) {
        return new Setting(!offByDefault(metal), 0);
    }

    /**
     * How many of a metal the mint may use: what is free of it beyond what stays in the storehouses; nothing while it is
     * off.
     *
     * @param free what the storehouses hold of it that no keep order holds and no workshop set aside
     */
    public static long usable(Setting setting, long free) {
        return setting.strike() ? Math.max(0, free - setting.keep()) : 0;
    }

    /**
     * Metal of the storehouses the mint takes.
     *
     * @param metal the rate it is struck at
     * @param coin  the kind of coin it gives
     * @param free  how many of it are free
     */
    public record Candidate(String metal, Denomination coin, long free) {
        public Candidate {
            Objects.requireNonNull(metal, "metal");
            Objects.requireNonNull(coin, "coin");
        }
    }

    /**
     * What the mint may strike.
     *
     * @param first for every kind of coin there is metal for, the index of the first candidate to strike it from
     * @param held  whether the mint has nothing to strike only because of the settings: every metal is off, or there is
     *              metal in the storehouses that is off or stays there
     */
    public record Choice(Map<Denomination, Integer> first, boolean held) {
        public Choice {
            first = Collections.unmodifiableMap(first.isEmpty() ? new EnumMap<>(Denomination.class) : new EnumMap<>(first));
        }

        /**
         * The kinds of coin there is metal for, in the order their first metal was met.
         */
        public List<Denomination> available(List<Candidate> candidates) {
            List<Denomination> kinds = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                Denomination coin = candidates.get(i).coin();
                Integer at = first.get(coin);
                if (at != null && at == i) {
                    kinds.add(coin);
                }
            }
            return kinds;
        }
    }

    /**
     * Of the metal in the storehouses, the first of every kind of coin the mint may strike from.
     *
     * @param candidates the metal there is, in the order of the rates of the config
     * @param metals     every metal of the rates, to tell whether all of them are off
     * @param settings   the setting of every metal
     */
    public static Choice choose(List<Candidate> candidates, List<String> metals, Function<String, Setting> settings) {
        Map<Denomination, Integer> first = new EnumMap<>(Denomination.class);
        boolean blocked = false;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.free() <= 0) {
                continue;
            }
            if (usable(settings.apply(candidate.metal()), candidate.free()) > 0) {
                first.putIfAbsent(candidate.coin(), i);
            } else {
                blocked = true;
            }
        }
        boolean allOff = !metals.isEmpty();
        for (String metal : metals) {
            if (settings.apply(metal).strike()) {
                allOff = false;
                break;
            }
        }
        return new Choice(first, first.isEmpty() && (blocked || allOff));
    }
}
