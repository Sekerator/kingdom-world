package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which storehouses a worker goes to. A storehouse takes in from every citizen, or only from the professions it was given (a
 * storehouse of wood for the lumberjacks): a worker brings what it gathered or made first to the nearest storehouse of its own
 * profession, then to the nearest common one, never to a storehouse of other professions. What comes in otherwise (the market,
 * caravans, trophies, taxes) goes to the common storehouses first and then to any with room, so that nothing is lost. Taking is
 * from any storehouse. Near players a worker of a post in a city walks to the storehouses of that city at any distance inside its
 * border, and a worker of a post outside the cities to any storehouse on the land of the kingdom.
 */
public final class StorehouseRules {
    /** Professions a storehouse may be given at most. */
    public static final int MAX_PROFESSIONS = 32;

    private StorehouseRules() {
    }

    /**
     * Whom a storehouse takes in from.
     *
     * @param professions the professions, empty for every citizen
     */
    public record Accepts(Set<String> professions) {
        public static final Accepts ALL = new Accepts(Set.of());

        public Accepts {
            TreeSet<String> sorted = new TreeSet<>();
            for (String profession : Objects.requireNonNull(professions, "professions")) {
                if (profession != null && Profession.isValidId(profession) && sorted.size() < MAX_PROFESSIONS) {
                    sorted.add(profession);
                }
            }
            professions = java.util.Collections.unmodifiableSet(sorted);
        }

        public static Accepts of(Collection<String> professions) {
            return professions.isEmpty() ? ALL : new Accepts(Set.copyOf(professions));
        }

        /**
         * Whether it takes in from every citizen.
         */
        public boolean all() {
            return professions.isEmpty();
        }

        /**
         * What it is to a worker of a profession.
         */
        public Kind kindFor(String profession) {
            if (all()) {
                return Kind.COMMON;
            }
            return professions.contains(profession) ? Kind.OWN : Kind.OTHERS;
        }
    }

    /**
     * What a storehouse is to a worker.
     */
    public enum Kind {
        /** Given to its profession. */
        OWN,
        /** Takes in from everybody. */
        COMMON,
        /** Given to other professions: the worker never brings anything there. */
        OTHERS
    }

    /**
     * How far a worker near players walks to a storehouse: inside the square of the city its post stands in, or anywhere on the
     * land of the kingdom for a post outside the cities.
     *
     * @param city the square of the city, {@code null} outside the cities
     */
    public record Reach(String dimension, Square city) {
        public Reach {
            Objects.requireNonNull(dimension, "dimension");
        }

        public boolean contains(String placeDimension, int x, int z) {
            return dimension.equals(placeDimension) && (city == null || city.contains(x, z));
        }
    }

    /**
     * The square of a city: half its side around its middle.
     */
    public record Square(int x, int z, int radius) {
        public boolean contains(int px, int pz) {
            return Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
        }
    }

    /**
     * A storehouse as the choice sees it.
     *
     * @param room whether it has room for what is brought
     */
    public record Candidate<T>(T value, String dimension, int x, int y, int z, Accepts accepts, boolean room) {
        public Candidate {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(accepts, "accepts");
        }

        double distanceSqr(int fromX, int fromY, int fromZ) {
            double dx = x - fromX;
            double dy = y - fromY;
            double dz = z - fromZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /**
     * The storehouses a worker of a profession brings things to, in the order it tries them: those of its own profession, nearest
     * first, then the common ones, nearest first; never those of other professions, nor those out of reach or without room.
     *
     * @param reach where the worker may walk, {@code null} for anywhere (far from players)
     */
    public static <T> List<T> delivery(List<Candidate<T>> storehouses, String profession, Reach reach, int fromX, int fromY, int fromZ) {
        List<Candidate<T>> own = new ArrayList<>();
        List<Candidate<T>> common = new ArrayList<>();
        for (Candidate<T> candidate : storehouses) {
            if (!candidate.room() || reach != null && !reach.contains(candidate.dimension(), candidate.x(), candidate.z())) {
                continue;
            }
            switch (candidate.accepts().kindFor(profession)) {
                case OWN -> own.add(candidate);
                case COMMON -> common.add(candidate);
                case OTHERS -> {
                }
            }
        }
        Comparator<Candidate<T>> nearest = Comparator.comparingDouble(candidate -> candidate.distanceSqr(fromX, fromY, fromZ));
        own.sort(nearest);
        common.sort(nearest);
        List<T> result = new ArrayList<>(own.size() + common.size());
        own.forEach(candidate -> result.add(candidate.value()));
        common.forEach(candidate -> result.add(candidate.value()));
        return result;
    }

    /**
     * The storehouses what comes in otherwise goes to (the market, caravans, trophies, taxes), in order: the common ones nearest
     * first, then any other with room, nearest first, so that nothing is lost.
     */
    public static <T> List<T> income(List<Candidate<T>> storehouses, int fromX, int fromY, int fromZ) {
        List<Candidate<T>> common = new ArrayList<>();
        List<Candidate<T>> given = new ArrayList<>();
        for (Candidate<T> candidate : storehouses) {
            if (candidate.room()) {
                (candidate.accepts().all() ? common : given).add(candidate);
            }
        }
        Comparator<Candidate<T>> nearest = Comparator.comparingDouble(candidate -> candidate.distanceSqr(fromX, fromY, fromZ));
        common.sort(nearest);
        given.sort(nearest);
        List<T> result = new ArrayList<>(common.size() + given.size());
        common.forEach(candidate -> result.add(candidate.value()));
        given.forEach(candidate -> result.add(candidate.value()));
        return result;
    }

    /**
     * The storehouses a worker may take from: any within reach, nearest first.
     */
    public static <T> List<T> taking(List<Candidate<T>> storehouses, Reach reach, int fromX, int fromY, int fromZ) {
        List<Candidate<T>> inside = new ArrayList<>();
        for (Candidate<T> candidate : storehouses) {
            if (reach == null || reach.contains(candidate.dimension(), candidate.x(), candidate.z())) {
                inside.add(candidate);
            }
        }
        inside.sort(Comparator.comparingDouble(candidate -> candidate.distanceSqr(fromX, fromY, fromZ)));
        List<T> result = new ArrayList<>(inside.size());
        inside.forEach(candidate -> result.add(candidate.value()));
        return result;
    }
}
