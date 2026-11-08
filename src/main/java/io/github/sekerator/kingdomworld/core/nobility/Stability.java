package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The stability of a kingdom, 0 to 100, from what it is made of: the loyalty of the houses, the mood of the citizens, the
 * taxes, the weariness of war, defeats and lost cities, festivals, the content of the parties and what happened lately
 * (refused demands, unrest stirred by spies, executions, mercy).
 */
public final class Stability {
    public static final int MAX = 100;
    public static final int BASE = 50;

    /**
     * One part of the stability, with its sign.
     */
    public record Cause(String id, int amount) {
        public Cause {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Something that happened and weighs on the stability until a game day.
     */
    public record Modifier(String cause, int amount, long until) {
        public Modifier {
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * What a kingdom is like today.
     *
     * @param loyalty    the average loyalty of its houses, -1 without houses (the court fills it in)
     * @param mood       the average mood of its citizens, -1 without citizens
     * @param taxPercent its poll tax against the usual one, in percent
     * @param exhaustion the weariness of the war it is most tired of, 0 to 100
     * @param defeats    wars lost and cities taken from it lately
     * @param festival   whether a festival was held lately
     * @param content    the content of its parties weighed by their influence, -1 without houses (the court fills it in)
     */
    public record Inputs(int loyalty, int mood, int taxPercent, int exhaustion, int defeats, boolean festival, int content) {
        public static final Inputs NEUTRAL = new Inputs(-1, -1, 100, 0, 0, false, -1);

        Inputs withCourt(int houses, int parties) {
            return new Inputs(houses, mood, taxPercent, exhaustion, defeats, festival, parties);
        }
    }

    /**
     * The stability and its causes, largest first.
     */
    public record Result(int value, List<Cause> causes) {
        public static final Result NEUTRAL = new Result(BASE, List.of());

        public Result {
            causes = List.copyOf(causes);
        }
    }

    private Stability() {
    }

    public static Result compute(Inputs in, List<Modifier> modifiers) {
        List<Cause> causes = new ArrayList<>();
        if (in.loyalty() >= 0) {
            add(causes, "houses", (int) Math.round((in.loyalty() - 50) * 0.3));
        }
        if (in.mood() >= 0) {
            add(causes, "mood", clamp((int) Math.round((in.mood() - CitizenNeeds.NEUTRAL_MOOD) * 0.4), -15, 15));
        }
        int tax = in.taxPercent();
        add(causes, "taxes", tax <= 50 ? 5 : tax <= 100 ? 0 : -clamp((tax - 100) / 10, 0, 15));
        add(causes, "war", -clamp((int) Math.round(in.exhaustion() * 0.2), 0, 20));
        add(causes, "defeats", -clamp(8 * in.defeats(), 0, 24));
        if (in.festival()) {
            add(causes, "festival", 6);
        }
        if (in.content() >= 0) {
            add(causes, "factions", clamp((int) Math.round((in.content() - 50) * 0.2), -10, 10));
        }
        for (Modifier modifier : modifiers) {
            add(causes, modifier.cause(), modifier.amount());
        }
        int value = BASE;
        for (Cause cause : causes) {
            value += cause.amount();
        }
        causes.sort((a, b) -> Integer.compare(Math.abs(b.amount()), Math.abs(a.amount())));
        return new Result(clamp(value, 0, MAX), causes);
    }

    private static void add(List<Cause> causes, String id, int amount) {
        if (amount == 0) {
            return;
        }
        for (int i = 0; i < causes.size(); i++) {
            if (causes.get(i).id().equals(id)) {
                causes.set(i, new Cause(id, causes.get(i).amount() + amount));
                return;
            }
        }
        causes.add(new Cause(id, amount));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
