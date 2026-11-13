package io.github.sekerator.kingdomworld.core.siege;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * What a peace hands over in the world, carried out step by step by a journal so that a restart in the middle neither loses
 * nor doubles anything: each step is done once and marked, and a step that was done and not marked does nothing the second
 * time.
 *
 * <ul>
 *   <li>{@link Kind#LAND}: the chunks the winner occupied, as many as the terms ask; the rest go back.</li>
 *   <li>{@link Kind#CITY}: a city the winner took, with its land, buildings and citizens.</li>
 *   <li>{@link Kind#ANNEX}: the whole loser: treasury, land, cities, buildings, citizens, settlements; its players
 *   choose, the crown is lost and the kingdom is no more.</li>
 *   <li>{@link Kind#PROVINCE}: whole provinces of a state, the winner held them or a treaty cedes them: their land, the
 *   claims of the loser in them, buildings, citizens, cities and settlements.</li>
 * </ul>
 *
 * <p>A step may take several ticks (a province or a settlement at a time): the one who carries it out finds what is left
 * from the world each time, so nothing more is saved for it.
 *
 * @param chunks the chunks of land handed over ({@link Kind#LAND}), the ids of the provinces ({@link Kind#PROVINCE})
 * @param city   the city handed over ({@link Kind#CITY}), 0 for the capital
 * @param step   the next step to carry out
 */
public record Conquest(long id, Kind kind, long war, UUID winner, UUID loser, String dimension, List<Long> chunks, long city, Step step,
                       long made) {
    /**
     * What is handed over.
     */
    public enum Kind {
        LAND,
        CITY,
        ANNEX,
        /** Whole provinces; their ids travel in {@link Conquest#chunks()}. */
        PROVINCE;

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

    /**
     * The steps, in order. A kind skips the steps it has nothing for.
     */
    public enum Step {
        TREASURY,
        LAND,
        BUILDINGS,
        CITIZENS,
        CITIES,
        /** The settlements of the provinces (all of the loser in an annexation) go to the winner, one at a time. */
        SETTLEMENTS,
        DAMAGE,
        MEMBERS,
        DISSOLVE,
        DONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Step> byId(String id) {
            for (Step step : values()) {
                if (step.id().equals(id)) {
                    return Optional.of(step);
                }
            }
            return Optional.empty();
        }
    }

    public Conquest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(step, "step");
        chunks = List.copyOf(chunks);
        if (winner.equals(loser)) {
            throw new IllegalArgumentException("A kingdom cannot conquer itself");
        }
    }

    /**
     * The first step a kind carries out.
     */
    public static Step first(Kind kind) {
        return next(kind, null);
    }

    /**
     * The step after one, for a kind; {@link Step#DONE} at the end.
     *
     * @param after the step just done, {@code null} for the start
     */
    public static Step next(Kind kind, Step after) {
        int from = after == null ? 0 : after.ordinal() + 1;
        for (int i = from; i < Step.values().length; i++) {
            Step step = Step.values()[i];
            if (step == Step.DONE || has(kind, step)) {
                return step;
            }
        }
        return Step.DONE;
    }

    /**
     * Whether a kind has a step.
     */
    public static boolean has(Kind kind, Step step) {
        return switch (kind) {
            case LAND -> step == Step.LAND || step == Step.DAMAGE;
            case CITY -> step == Step.LAND || step == Step.BUILDINGS || step == Step.CITIZENS || step == Step.CITIES || step == Step.DAMAGE;
            case PROVINCE -> step == Step.LAND || step == Step.BUILDINGS || step == Step.CITIZENS || step == Step.CITIES
                    || step == Step.SETTLEMENTS || step == Step.DAMAGE;
            case ANNEX -> step != Step.DONE;
        };
    }

    /**
     * The provinces a conquest of provinces hands over; empty for other kinds.
     */
    public List<Integer> provinces() {
        if (kind != Kind.PROVINCE) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>(chunks.size());
        for (long id : chunks) {
            ids.add((int) id);
        }
        return ids;
    }

    public Conquest advance() {
        return new Conquest(id, kind, war, winner, loser, dimension, chunks, city, next(kind, step), made);
    }

    public boolean done() {
        return step == Step.DONE;
    }

    /**
     * The chunks a peace for land hands over: those the winner occupied, the first taken first, no more than the terms ask.
     */
    public static List<Long> landToCede(List<Hold> holds, UUID winner, UUID loser, long war, int most) {
        List<Hold> taken = new ArrayList<>();
        for (Hold hold : holds) {
            if (hold.kind() == Hold.Kind.CHUNK && hold.taken() && hold.war() == war && hold.attacker().equals(winner)
                    && hold.defender().equals(loser)) {
                taken.add(hold);
            }
        }
        taken.sort(Comparator.comparingLong(Hold::since).thenComparingLong(Hold::target));
        List<Long> result = new ArrayList<>();
        for (Hold hold : taken) {
            if (result.size() >= Math.max(0, most)) {
                break;
            }
            result.add(hold.target());
        }
        return result;
    }

    /**
     * The provinces a peace for land hands over in a war on a state: those the winner took, the first taken first, never
     * the province of the capital of the loser (only an annexation takes it), no more than the terms ask.
     *
     * @param most     how many provinces the terms ask
     * @param excluded the province of the capital of the loser, 0 for none
     */
    public static List<Integer> provincesToCede(List<Hold> holds, UUID winner, UUID loser, long war, int most, long excluded) {
        List<Hold> taken = new ArrayList<>();
        for (Hold hold : holds) {
            if (hold.kind() == Hold.Kind.PROVINCE && hold.taken() && hold.war() == war && hold.attacker().equals(winner)
                    && hold.defender().equals(loser) && hold.target() > 0 && hold.target() != excluded) {
                taken.add(hold);
            }
        }
        taken.sort(Comparator.comparingLong(Hold::since).thenComparingLong(Hold::target));
        List<Integer> result = new ArrayList<>();
        for (Hold hold : taken) {
            if (result.size() >= Math.max(0, most)) {
                break;
            }
            result.add((int) hold.target());
        }
        return result;
    }

    /**
     * The province a peace for a city hands over in a war on a state: of the provinces with a settlement the winner took,
     * the one named by the terms, else the first taken; never the province of the capital; empty when the winner took none.
     *
     * @param named    the name of the city asked by the terms, "?" or empty for any
     * @param names    the name of the main settlement of a province by its id
     * @param settled  whether a province has a main settlement that stands
     * @param excluded the province of the capital of the loser, 0 for none
     */
    public static Optional<Integer> provinceOfCity(List<Hold> holds, UUID winner, UUID loser, long war, String named, LongFunction<String> names,
                                                   LongPredicate settled, long excluded) {
        Hold first = null;
        for (Hold hold : holds) {
            if (hold.kind() != Hold.Kind.PROVINCE || !hold.taken() || hold.war() != war || !hold.attacker().equals(winner)
                    || !hold.defender().equals(loser) || hold.target() <= 0 || hold.target() == excluded || !settled.test(hold.target())) {
                continue;
            }
            if (named != null && !named.isEmpty() && !"?".equals(named) && named.equalsIgnoreCase(names.apply(hold.target()))) {
                return Optional.of((int) hold.target());
            }
            if (first == null || hold.since() < first.since() || (hold.since() == first.since() && hold.target() < first.target())) {
                first = hold;
            }
        }
        return first == null ? Optional.empty() : Optional.of((int) first.target());
    }

    /**
     * The city a peace for a city hands over: the one the winner took, the one named by the terms first; empty when the
     * winner took none.
     *
     * @param named the city named by the terms, "?" or empty for any
     * @param names the name of a city of the loser by its id
     */
    public static Optional<Long> cityToCede(List<Hold> holds, UUID winner, UUID loser, long war, String named, LongFunction<String> names) {
        Hold first = null;
        for (Hold hold : holds) {
            if (hold.kind() != Hold.Kind.PLAZA || !hold.taken() || hold.war() != war || !hold.attacker().equals(winner)
                    || !hold.defender().equals(loser)) {
                continue;
            }
            if (named != null && !named.isEmpty() && !"?".equals(named) && named.equalsIgnoreCase(names.apply(hold.target()))) {
                return Optional.of(hold.target());
            }
            if (first == null || hold.since() < first.since()) {
                first = hold;
            }
        }
        return first == null ? Optional.empty() : Optional.of(first.target());
    }

    /**
     * Whether the winner took the throne of the loser in a war.
     */
    public static boolean throneTaken(List<Hold> holds, UUID winner, UUID loser, long war) {
        for (Hold hold : holds) {
            if (hold.kind() == Hold.Kind.THRONE && hold.taken() && hold.war() == war && hold.attacker().equals(winner)
                    && hold.defender().equals(loser)) {
                return true;
            }
        }
        return false;
    }
}
