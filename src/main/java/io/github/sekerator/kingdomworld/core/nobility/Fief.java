package io.github.sekerator.kingdomworld.core.nobility;

import java.util.Locale;
import java.util.Optional;

/**
 * The land that comes with a title: a city of the kingdom, or one of the four marches (the land of the kingdom towards a
 * side of the world from the capital, outside its cities).
 *
 * @param city  the id of the city ({@link Kind#CITY})
 * @param march the side ({@link Kind#MARCH}): 0 north, 1 east, 2 south, 3 west
 */
public record Fief(Kind kind, long city, int march) {
    /** The number of marches of a kingdom. */
    public static final int MARCHES = 4;

    public enum Kind {
        CITY,
        MARCH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public Fief {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        if (kind == Kind.CITY) {
            march = 0;
            if (city <= 0) {
                throw new IllegalArgumentException("A city fief needs a city");
            }
        } else {
            city = 0;
            if (march < 0 || march >= MARCHES) {
                throw new IllegalArgumentException("A march is 0-3");
            }
        }
    }

    public static Fief city(long id) {
        return new Fief(Kind.CITY, id, 0);
    }

    public static Fief march(int side) {
        return new Fief(Kind.MARCH, 0, side);
    }

    /**
     * The side of a march a point lies on, seen from the capital: north, east, south or west.
     */
    public static int side(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 1 : 3;
        }
        return dz > 0 ? 2 : 0;
    }

    /**
     * A short stable text, for saving and the network.
     */
    public String code() {
        return kind == Kind.CITY ? "c" + city : "m" + march;
    }

    public static Optional<Fief> parse(String code) {
        if (code == null || code.length() < 2) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(code.substring(1));
            if (code.charAt(0) == 'c' && value > 0) {
                return Optional.of(city(value));
            }
            if (code.charAt(0) == 'm' && value >= 0 && value < MARCHES) {
                return Optional.of(march((int) value));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
