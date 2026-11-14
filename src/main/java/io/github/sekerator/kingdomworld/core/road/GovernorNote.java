package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Objects;

/**
 * What the governor of a city decided last, for the window of the king: what it builds, what it saves for and how much it
 * lacks, or why it waits. Kept as a short text in the {@link CityCharter} of the city.
 *
 * @param kind    what it decided
 * @param subject the template it builds or saves for, the class of the wall it ordered, else empty
 * @param amount  what the materials it lacks are worth, the constructions it waits for, else 0
 */
public record GovernorNote(Kind kind, String subject, long amount) {
    /** The longest text of a note. */
    public static final int MAX_TEXT = 256;
    public static final GovernorNote NONE = new GovernorNote(Kind.NONE, "", 0);

    public GovernorNote {
        Objects.requireNonNull(kind, "kind");
        subject = subject == null ? "" : subject.replace('|', '_');
        if (subject.length() > MAX_TEXT / 2) {
            subject = subject.substring(0, MAX_TEXT / 2);
        }
        amount = Math.max(0, amount);
    }

    /**
     * What a governor decided.
     */
    public enum Kind {
        /** Nothing yet. */
        NONE,
        /** The king stopped it. */
        PAUSED,
        /** No builder of the city, nobody without work to hire and no builder of another city to help: it waits. */
        NO_BUILDER,
        /** It ordered a post of builders for the city, or waits for the one ordered. */
        POST,
        /** The city builds as much as its builders manage: it waits for them. */
        WAITING,
        /** It ordered a building. */
        BUILDS,
        /** It saves for a building whose missing materials cost more than its share of the day. */
        SAVES,
        /** It waits for the storehouses to hold the materials of a building, the king giving it no money to buy them. */
        LACKS,
        /** It ordered a wall round the city. */
        WALL,
        /** The city has every building its level wants. */
        DONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The kind of an id, {@link #NONE} for one this version does not know.
         */
        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return kind;
                }
            }
            return NONE;
        }
    }

    public static GovernorNote of(Kind kind) {
        return new GovernorNote(kind, "", 0);
    }

    /**
     * The note as a short text: {@code kind|subject|amount}.
     */
    public String encode() {
        if (kind == Kind.NONE) {
            return "";
        }
        return kind.id() + "|" + subject + "|" + amount;
    }

    /**
     * A note from its text; {@link #NONE} for an empty or unreadable one.
     */
    public static GovernorNote parse(String text) {
        if (text == null || text.isEmpty()) {
            return NONE;
        }
        String[] parts = text.split("\\|", -1);
        Kind kind = Kind.byId(parts[0]);
        if (kind == Kind.NONE) {
            return NONE;
        }
        String subject = parts.length > 1 ? parts[1] : "";
        long amount = 0;
        if (parts.length > 2) {
            try {
                amount = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
        }
        return new GovernorNote(kind, subject, amount);
    }
}
