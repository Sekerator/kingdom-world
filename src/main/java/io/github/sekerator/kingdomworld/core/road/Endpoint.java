package io.github.sekerator.kingdomworld.core.road;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a road starts or ends: a city, the capital of a kingdom (one's own or another's), or a point such as the border.
 *
 * @param city    the city, for {@link Kind#CITY}
 * @param kingdom the kingdom whose capital it is, for {@link Kind#CAPITAL}
 */
public record Endpoint(Kind kind, long city, UUID kingdom, int x, int z) {

    public enum Kind {
        CITY,
        CAPITAL,
        POINT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Endpoint {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CAPITAL) {
            Objects.requireNonNull(kingdom, "kingdom");
        }
        if (kind != Kind.CAPITAL) {
            kingdom = null;
        }
        if (kind != Kind.CITY) {
            city = 0;
        }
    }

    public static Endpoint point(int x, int z) {
        return new Endpoint(Kind.POINT, 0, null, x, z);
    }

    public static Endpoint city(City city) {
        return new Endpoint(Kind.CITY, city.id(), null, city.x(), city.z());
    }

    public static Endpoint capital(UUID kingdom, int x, int z) {
        return new Endpoint(Kind.CAPITAL, 0, kingdom, x, z);
    }
}
