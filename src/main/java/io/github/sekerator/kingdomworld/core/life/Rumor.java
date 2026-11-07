package io.github.sekerator.kingdomworld.core.life;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Something an innkeeper heard: a translated sentence with its arguments, made from what is true in the world.
 *
 * @param source what kind of news it is (the source that told it: camps, contracts, news, prices, wanted, or another of a mod)
 * @param key    the translation key of the sentence
 * @param args   its arguments
 * @param campId the camp of robbers the rumor is about, which the player may buy marked on the atlas; 0 for none
 */
public record Rumor(String source, String key, List<Arg> args, long campId) {
    /** Most arguments of a rumor. */
    public static final int MAX_ARGS = 6;

    /**
     * What an argument is.
     */
    public enum Kind {
        /** A plain text: a name, a number. */
        PLAIN,
        /** A translation key of its own: a direction, a kind. */
        KEY,
        /** An amount of money, {@code amount|currency}. */
        MONEY,
        /** An item by its id, shown by its name. */
        ITEM
    }

    /**
     * An argument of the sentence.
     */
    public record Arg(Kind kind, String text) {
        public Arg {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
        }

        public static Arg plain(Object value) {
            return new Arg(Kind.PLAIN, String.valueOf(value));
        }

        public static Arg key(String key) {
            return new Arg(Kind.KEY, key);
        }

        public static Arg money(long amount, UUID currency) {
            return new Arg(Kind.MONEY, amount + "|" + currency);
        }

        public static Arg item(String id) {
            return new Arg(Kind.ITEM, id);
        }
    }

    public Rumor {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        args = List.copyOf(args);
        if (args.size() > MAX_ARGS) {
            throw new IllegalArgumentException("Too many arguments of a rumor");
        }
    }

    public Rumor(String source, String key, List<Arg> args) {
        this(source, key, args, 0);
    }
}
