package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * How the parties of a court choose what to ask, and how rulers of the world answer demands, judge plotters and use spies:
 * by their character and traits, in moderation.
 */
public final class NobilityMind {
    /** Parties more content than this ask for nothing. */
    static final int ASKING_CONTENT = 55;

    private NobilityMind() {
    }

    /**
     * Houses a kingdom of the world has from the start.
     */
    public static int houses(NpcLevel level, long seed) {
        int extra = (int) Math.floorMod(seed >>> 11, 2);
        return switch (level) {
            case SMALL -> 2;
            case MEDIUM -> 3 + extra;
            case LARGE -> 4 + extra;
        };
    }

    /**
     * What a party asks.
     */
    public record Ask(Faction faction, long house, Demand.Kind kind, UUID target, long value) {
        public Ask {
            Objects.requireNonNull(faction, "faction");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * The least content party with a house speaks, if it is not content enough to stay quiet; half the time it waits one
     * more day.
     */
    static Optional<Ask> demand(Court court, List<NobleHouse> own, NobilityState state, NobilityState.Context context,
                                NobilityRules rules, DoubleSupplier rolls) {
        List<Faction> parties = new ArrayList<>();
        for (NobleHouse house : own) {
            if (!parties.contains(house.faction())) {
                parties.add(house.faction());
            }
        }
        parties.sort(Comparator.comparingInt(court::content));
        if (parties.isEmpty() || court.content(parties.get(0)) > ASKING_CONTENT || rolls.getAsDouble() < 0.5) {
            return Optional.empty();
        }
        Faction faction = parties.get(0);
        NobleHouse speaker = own.stream().filter(h -> h.faction() == faction).max(Comparator.comparingLong(NobleHouse::wealth)).orElseThrow();
        long value = rules.demandValue();
        Ask ask = switch (faction) {
            case WAR -> context.enemy() != null
                    ? (context.loot() > 0 ? new Ask(faction, speaker.id(), Demand.Kind.SHARE_LOOT, null, Math.max(1, context.loot() / 5))
                    : new Ask(faction, speaker.id(), Demand.Kind.BUILD, null, value))
                    : context.rival() != null ? new Ask(faction, speaker.id(), Demand.Kind.DECLARE_WAR, context.rival(), value)
                    : new Ask(faction, speaker.id(), Demand.Kind.BUILD, null, value);
            case MERCHANTS -> context.enemy() != null ? new Ask(faction, speaker.id(), Demand.Kind.MAKE_PEACE, context.enemy(), value)
                    : context.taxPercent() > 60 ? new Ask(faction, speaker.id(), Demand.Kind.LOWER_TAX, null, 25)
                    : new Ask(faction, speaker.id(), Demand.Kind.BUILD, null, value);
            case OLD_NOBILITY -> {
                Optional<NobleHouse> untitled = own.stream().filter(h -> h.faction() == faction && state.grantsOf(h).isEmpty()).findFirst();
                yield untitled.isPresent() && context.freeFief()
                        ? new Ask(faction, untitled.get().id(), Demand.Kind.GRANT_TITLE, null, value)
                        : context.taxPercent() > 60 ? new Ask(faction, speaker.id(), Demand.Kind.LOWER_TAX, null, 25)
                        : new Ask(faction, speaker.id(), Demand.Kind.BUILD, null, value);
            }
            case CROWN -> context.enemy() != null && context.loot() == 0
                    ? new Ask(faction, speaker.id(), Demand.Kind.MAKE_PEACE, context.enemy(), value)
                    : new Ask(faction, speaker.id(), Demand.Kind.BUILD, null, value);
        };
        return Optional.of(ask);
    }

    /**
     * How a ruler of the world answers a demand of its court. Rulers do not go to war or make peace because a party asks.
     */
    public static Demand.Answer answer(Demand demand, RulerCharacter character, List<RulerCharacter.Trait> traits, boolean canPay) {
        boolean warOrPeace = demand.kind() == Demand.Kind.DECLARE_WAR || demand.kind() == Demand.Kind.MAKE_PEACE;
        if (traits.contains(RulerCharacter.Trait.PROUD) && !warOrPeace) {
            return Demand.Answer.REFUSE;
        }
        if (warOrPeace) {
            return canPay && !traits.contains(RulerCharacter.Trait.THRIFTY) ? Demand.Answer.BARGAIN : Demand.Answer.REFUSE;
        }
        if (traits.contains(RulerCharacter.Trait.GENEROUS) || traits.contains(RulerCharacter.Trait.JUST)) {
            return canPay || !demand.kind().paid() ? Demand.Answer.ACCEPT : Demand.Answer.REFUSE;
        }
        if (traits.contains(RulerCharacter.Trait.THRIFTY)) {
            return demand.kind().paid() || !canPay ? Demand.Answer.REFUSE : Demand.Answer.BARGAIN;
        }
        if (demand.kind() == Demand.Kind.GRANT_TITLE || demand.kind() == Demand.Kind.LOWER_TAX) {
            return Demand.Answer.ACCEPT;
        }
        return canPay ? Demand.Answer.BARGAIN : Demand.Answer.REFUSE;
    }

    /**
     * What a ruler of the world does with a house whose plot was found out.
     */
    public static Plot.Verdict verdict(List<RulerCharacter.Trait> traits) {
        if (traits.contains(RulerCharacter.Trait.JUST) || traits.contains(RulerCharacter.Trait.GENEROUS)) {
            return Plot.Verdict.PARDON;
        }
        if (traits.contains(RulerCharacter.Trait.PROUD) || traits.contains(RulerCharacter.Trait.BOLD)) {
            return Plot.Verdict.EXECUTE;
        }
        return Plot.Verdict.EXILE;
    }

    /**
     * Whether a ruler of the world puts caught spies to death at once instead of asking a ransom.
     */
    public static boolean harsh(List<RulerCharacter.Trait> traits) {
        return traits.contains(RulerCharacter.Trait.PROUD) || traits.contains(RulerCharacter.Trait.BOLD);
    }

    /**
     * How many spies a ruler of the world keeps: schemers and warriors more, the cautious and the just fewer.
     */
    public static int spies(RulerCharacter character, List<RulerCharacter.Trait> traits, NobilityRules rules) {
        int wanted = 1;
        if (character == RulerCharacter.WARRIOR || traits.contains(RulerCharacter.Trait.AMBITIOUS) || traits.contains(RulerCharacter.Trait.BOLD)) {
            wanted++;
        }
        if (traits.contains(RulerCharacter.Trait.JUST) || traits.contains(RulerCharacter.Trait.CAUTIOUS)) {
            wanted--;
        }
        return Math.max(0, Math.min(rules.npcMaxSpies(), wanted));
    }

    /**
     * A kingdom a ruler of the world could send a spy against.
     *
     * @param npc      whether a ruler of the world rules it
     * @param relation how the ruler stands with it
     * @param atWar    whether they fight
     * @param house    a house of it whose head could be killed, 0 if none
     */
    public record Mark(UUID kingdomId, boolean npc, int relation, boolean atWar, long house) {
    }

    /**
     * The mission a ruler of the world gives an idle spy: against an enemy theft, sabotage or unrest; against a kingdom it
     * dislikes scouting or unrest; an ambitious ruler of the world may have the head of a house of another ruler killed.
     */
    public static Optional<Spy.Mission> mission(List<RulerCharacter.Trait> traits, List<Mark> marks, long day, DoubleSupplier rolls) {
        Mark chosen = null;
        for (Mark mark : marks) {
            if (mark.atWar() || mark.relation() <= -20) {
                if (chosen == null || score(mark) < score(chosen)) {
                    chosen = mark;
                }
            }
        }
        if (chosen == null || rolls.getAsDouble() < 0.5) {
            return Optional.empty();
        }
        Spy.Task task;
        double roll = rolls.getAsDouble();
        if (chosen.atWar()) {
            task = roll < 0.35 ? Spy.Task.THEFT : roll < 0.65 ? Spy.Task.SABOTAGE : Spy.Task.INCITE;
        } else if (chosen.npc() && chosen.house() > 0 && traits.contains(RulerCharacter.Trait.AMBITIOUS) && roll < 0.2) {
            task = Spy.Task.ASSASSINATE;
        } else {
            task = roll < 0.6 ? Spy.Task.SCOUT : Spy.Task.INCITE;
        }
        return Optional.of(new Spy.Mission(task, chosen.kingdomId(), task == Spy.Task.ASSASSINATE ? chosen.house() : 0, day, day + task.days()));
    }

    private static int score(Mark mark) {
        return mark.relation() - (mark.atWar() ? 200 : 0);
    }
}
