package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * How rulers of the world deal with their neighbours, by character and relations. A merchant looks for pacts and alliances;
 * a warrior declares war on weak neighbours for tribute or loot, but not often and not on those who were just at war; a just
 * ruler never attacks first but stands by its allies; a mage keeps out of it. Every ruler answers offers sensibly and makes
 * peace when worn out or beaten.
 */
public final class DiplomacyMind {
    public static final String ASIDE = "aside";
    public static final String DISTRUST = "distrust";
    public static final String WEAK = "weak";
    public static final String PROUD = "proud";
    public static final String WINNING = "winning";
    public static final String TOO_MUCH = "too_much";
    public static final String AGREED = "agreed";
    public static final List<String> REASONS = List.of(ASIDE, DISTRUST, WEAK, PROUD, WINNING, TOO_MUCH, AGREED);

    /**
     * A ruler of the world, as diplomacy sees it.
     *
     * @param power        the strength of its army
     * @param poor         whether its treasury is low
     * @param lastDeclared the game day it last declared a war, {@code null} for never
     */
    public record Self(UUID id, RulerCharacter character, List<RulerCharacter.Trait> traits, double power, boolean poor, Long lastDeclared) {
        public Self {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(character, "character");
            traits = List.copyOf(traits);
        }

        boolean has(RulerCharacter.Trait trait) {
            return traits.contains(trait);
        }
    }

    /**
     * Another kingdom near a ruler.
     *
     * @param npc          whether it is a kingdom of the world
     * @param relation     how the ruler stands with it, from -100 to 100
     * @param playable     whether it can defend itself now (a kingdom of the world, or players online at the keyboard)
     * @param recentWar    whether it was at war a short time ago
     * @param lastNpcWar   for a kingdom of players, the game day a ruler of the world last declared war on it, {@code null}
     *                     for never
     */
    public record Neighbor(UUID id, boolean npc, double power, double distance, int relation, Bond bond, boolean playable, boolean recentWar,
                           Long lastNpcWar) {
        public Neighbor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bond, "bond");
        }
    }

    /**
     * Something a ruler does today.
     */
    public record Move(Kind kind, UUID target, WarGoal goal) {
        public enum Kind {
            PROPOSE_PACT,
            PROPOSE_ALLIANCE,
            DECLARE
        }
    }

    /**
     * An answer with its reason, for the messages to players.
     */
    public record Verdict(boolean accept, String reason) {
    }

    private DiplomacyMind() {
    }

    /**
     * What a ruler does today: at most one offer and at most one declaration of war.
     *
     * @param atWar whether the ruler fights or prepares a war already
     */
    public static List<Move> decide(Self self, List<Neighbor> neighbors, boolean atWar, long day, DiplomacyRules rules) {
        List<Move> moves = new ArrayList<>();
        Optional<Neighbor> victim = victim(self, neighbors, atWar, day, rules);
        victim.ifPresent(target -> moves.add(new Move(Move.Kind.DECLARE, target.id(), goal(self, target))));
        Optional<Move> offer = offer(self, neighbors, victim.map(Neighbor::id).orElse(null));
        offer.ifPresent(moves::add);
        return moves;
    }

    /**
     * Whether the ruler is one who starts wars at all: a warrior who is not just.
     */
    public static boolean aggressive(Self self) {
        return self.character() == RulerCharacter.WARRIOR && !self.has(RulerCharacter.Trait.JUST);
    }

    /**
     * The neighbour a warrior falls upon today, if any: weaker by far, at peace with it, able to defend itself, not at war
     * a short time ago, and for a kingdom of players only if such wars are allowed and none was declared on it lately.
     */
    public static Optional<Neighbor> victim(Self self, List<Neighbor> neighbors, boolean atWar, long day, DiplomacyRules rules) {
        if (!aggressive(self) || atWar || (self.lastDeclared() != null && day - self.lastDeclared() < rules.npcWarDays())) {
            return Optional.empty();
        }
        int hostility = self.has(RulerCharacter.Trait.CAUTIOUS) ? -20 : self.has(RulerCharacter.Trait.BOLD) ? 30 : 10;
        double strength = self.has(RulerCharacter.Trait.CAUTIOUS) ? rules.npcStrength() * 1.5 : rules.npcStrength();
        return neighbors.stream()
                .filter(n -> n.bond() == Bond.PEACE && n.playable() && !n.recentWar() && n.relation() <= hostility)
                .filter(n -> self.power() > 0 && self.power() >= n.power() * strength)
                .filter(n -> n.npc() || (rules.npcWarsOnPlayers() && (n.lastNpcWar() == null || day - n.lastNpcWar() >= rules.npcPlayerWarDays())))
                .max(Comparator.comparingDouble((Neighbor n) -> self.power() / Math.max(1.0, n.power())).thenComparingInt(n -> -n.relation()));
    }

    /**
     * What a warrior goes to war for: vassalage of a neighbour three times weaker, else loot.
     */
    public static WarGoal goal(Self self, Neighbor target) {
        return self.power() >= target.power() * 3.0 && !self.poor() ? WarGoal.TRIBUTE : WarGoal.RAID;
    }

    /**
     * The offer a ruler makes today, if any: merchants pacts and alliances, builders pacts, warriors alliances with strong
     * friends, mages none.
     */
    static Optional<Move> offer(Self self, List<Neighbor> neighbors, UUID victim) {
        List<Neighbor> candidates = new ArrayList<>(neighbors);
        candidates.removeIf(n -> n.id().equals(victim) || !n.playable());
        candidates.sort(Comparator.comparingInt(Neighbor::relation).reversed());
        switch (self.character()) {
            case MERCHANT -> {
                for (Neighbor n : candidates) {
                    if (n.bond() == Bond.PACT && n.relation() >= 25) {
                        return Optional.of(new Move(Move.Kind.PROPOSE_ALLIANCE, n.id(), null));
                    }
                }
                for (Neighbor n : candidates) {
                    if (n.bond() == Bond.PEACE && n.relation() >= -5) {
                        return Optional.of(new Move(Move.Kind.PROPOSE_PACT, n.id(), null));
                    }
                }
            }
            case BUILDER -> {
                for (Neighbor n : candidates) {
                    if (n.bond() == Bond.PEACE && n.relation() >= 10) {
                        return Optional.of(new Move(Move.Kind.PROPOSE_PACT, n.id(), null));
                    }
                }
            }
            case WARRIOR -> {
                for (Neighbor n : candidates) {
                    if ((n.bond() == Bond.PEACE || n.bond() == Bond.PACT) && n.relation() >= 40 && n.power() >= self.power() * 0.75) {
                        return Optional.of(new Move(Move.Kind.PROPOSE_ALLIANCE, n.id(), null));
                    }
                }
            }
            case MAGE -> {
            }
        }
        return Optional.empty();
    }

    /**
     * How a ruler answers an offer of another kingdom (not peace: see {@link #peace}).
     *
     * @param from the kingdom that made the offer
     */
    public static Verdict answer(Proposal proposal, Self self, Neighbor from) {
        int leniency = self.has(RulerCharacter.Trait.JUST) ? 10 : 0;
        int relation = from.relation() + leniency;
        switch (proposal.kind()) {
            case PACT -> {
                int needed = switch (self.character()) {
                    case MERCHANT -> -30;
                    case MAGE -> -20;
                    case BUILDER -> -15;
                    case WARRIOR -> from.power() >= self.power() ? -10 : 0;
                };
                return relation >= needed ? new Verdict(true, AGREED) : new Verdict(false, DISTRUST);
            }
            case ALLIANCE -> {
                if (self.character() == RulerCharacter.MAGE) {
                    return new Verdict(false, ASIDE);
                }
                int needed = switch (self.character()) {
                    case MERCHANT -> 15;
                    case WARRIOR -> 25;
                    default -> 25;
                };
                if (self.character() == RulerCharacter.WARRIOR && from.power() < self.power() * 0.5) {
                    return new Verdict(false, WEAK);
                }
                return relation >= needed ? new Verdict(true, AGREED) : new Verdict(false, DISTRUST);
            }
            case VASSALAGE -> {
                if (proposal.vassal().equals(self.id())) {
                    // Asked to bow: only to a far stronger neighbour, never when proud.
                    if (self.has(RulerCharacter.Trait.PROUD)) {
                        return new Verdict(false, PROUD);
                    }
                    boolean overwhelmed = from.power() >= Math.max(1.0, self.power()) * 3.0;
                    return overwhelmed && (self.has(RulerCharacter.Trait.CAUTIOUS) || relation >= 20) ? new Verdict(true, AGREED)
                            : new Verdict(false, overwhelmed ? DISTRUST : WEAK);
                }
                if (self.character() == RulerCharacter.MAGE && relation < 0) {
                    return new Verdict(false, ASIDE);
                }
                return relation >= -20 ? new Verdict(true, AGREED) : new Verdict(false, DISTRUST);
            }
            case RELEASE -> {
                boolean let = self.has(RulerCharacter.Trait.JUST) || relation >= 30 || from.power() >= self.power();
                return let ? new Verdict(true, AGREED) : new Verdict(false, PROUD);
            }
            case PEACE -> {
                return new Verdict(false, WINNING);
            }
        }
        return new Verdict(false, DISTRUST);
    }

    /**
     * Whether a ruler takes up the call of an ally: a just ruler always, a warrior if it does not dislike the ally, a merchant
     * and a builder only for good friends, a mage never; a poor ruler stays home.
     */
    public static boolean joins(Self self, int relationWithCaller) {
        if (self.character() == RulerCharacter.MAGE || self.poor()) {
            return false;
        }
        if (self.has(RulerCharacter.Trait.JUST)) {
            return true;
        }
        return switch (self.character()) {
            case WARRIOR -> relationWithCaller >= 0;
            case MERCHANT -> relationWithCaller >= 40;
            case BUILDER -> relationWithCaller >= 25;
            case MAGE -> false;
        };
    }

    /**
     * How a ruler answers terms of peace in a war it leads a side of: gladly when it gains, a white peace when it does not
     * lead by far or is tired, giving in when worn out or clearly beaten, but never more than the enemy may ask by the score.
     *
     * @param enemyMay what the enemy may ask by the score now
     */
    public static Verdict peace(War war, War.Side side, PeaceTerms terms, PeaceTerms enemyMay, DiplomacyRules rules) {
        UUID self = war.leader(side);
        double exhaustion = war.exhaustion(side);
        double lead = war.lead(side);
        if (!terms.isWhite() && terms.winner().equals(self)) {
            return new Verdict(true, AGREED);
        }
        if (terms.isWhite()) {
            boolean accept = lead < rules.imposeLead() / 2.0 || exhaustion >= rules.npcPeaceExhaustion() / 2.0;
            return accept ? new Verdict(true, AGREED) : new Verdict(false, WINNING);
        }
        boolean beaten = exhaustion >= rules.npcPeaceExhaustion() || lead <= -rules.imposeLead();
        if (!beaten) {
            return new Verdict(false, lead > 0 ? WINNING : DISTRUST);
        }
        if (!DiplomacyState.within(terms, enemyMay) && exhaustion < 90.0) {
            return new Verdict(false, TOO_MUCH);
        }
        return new Verdict(true, AGREED);
    }

    /**
     * The terms a ruler leading a side offers today, if any: its goal in full with a clear lead, a white peace or what the
     * enemy may ask when tired or beaten.
     *
     * @param mine     what this side may ask by the score
     * @param enemyMay what the enemy may ask by the score
     */
    public static Optional<PeaceTerms> offerPeace(War war, War.Side side, PeaceTerms mine, PeaceTerms enemyMay, DiplomacyRules rules) {
        double exhaustion = war.exhaustion(side);
        double lead = war.lead(side);
        if (exhaustion >= rules.npcPeaceExhaustion() || lead <= -rules.imposeLead()) {
            return Optional.of(lead > 0 ? PeaceTerms.WHITE : enemyMay);
        }
        if (lead >= rules.imposeLead() && !mine.isWhite()) {
            return Optional.of(mine);
        }
        if (exhaustion >= rules.npcPeaceExhaustion() / 2.0 && lead >= 0) {
            return Optional.of(PeaceTerms.WHITE);
        }
        return Optional.empty();
    }
}
