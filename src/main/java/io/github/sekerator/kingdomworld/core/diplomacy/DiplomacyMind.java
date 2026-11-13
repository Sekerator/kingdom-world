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
 * a warrior declares war on weak neighbours for tribute or loot, or for their provinces when they border its state, but not
 * often and not on those who were just at war; a just ruler never attacks first but stands by its allies; a mage keeps out
 * of it. Every ruler answers offers sensibly, sells a province for a fair price and makes peace when worn out or beaten.
 */
public final class DiplomacyMind {
    public static final String ASIDE = "aside";
    public static final String DISTRUST = "distrust";
    public static final String WEAK = "weak";
    public static final String PROUD = "proud";
    public static final String WINNING = "winning";
    public static final String TOO_MUCH = "too_much";
    public static final String AGREED = "agreed";
    /** The heart of a state, where its capital stands, is never ceded. */
    public static final String HEART = "heart";
    /** The price offered for a province is below what the ruler wants for it. */
    public static final String LOW_PRICE = "low_price";
    public static final List<String> REASONS = List.of(ASIDE, DISTRUST, WEAK, PROUD, WINNING, TOO_MUCH, AGREED, HEART, LOW_PRICE);

    /** What a province is worth at least, whatever its size. */
    public static final long MIN_CEDE_VALUE = 1_500;
    /** Worth of a chunk of a province before character and relations. */
    public static final double CEDE_CHUNK_VALUE = 3.0;

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
     * @param bordering    whether a province of it borders a province of the ruler's state
     */
    public record Neighbor(UUID id, boolean npc, double power, double distance, int relation, Bond bond, boolean playable, boolean recentWar,
                           Long lastNpcWar, boolean bordering) {
        public Neighbor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bond, "bond");
        }

        /**
         * A neighbour whose provinces do not border the ruler's.
         */
        public Neighbor(UUID id, boolean npc, double power, double distance, int relation, Bond bond, boolean playable, boolean recentWar,
                        Long lastNpcWar) {
            this(id, npc, power, distance, relation, bond, playable, recentWar, lastNpcWar, false);
        }
    }

    /**
     * A province a ruler is asked to cede, as the land tells it.
     *
     * @param settled  whether people live in it: it has a main settlement that stands
     * @param heart    whether the capital of the ruler stands in it
     * @param chunks   how many chunks it has
     * @param price    what the asker offers for it, 0 for nothing
     * @param overlord whether the asker is the overlord of the ruler
     */
    public record CedeAsk(boolean settled, boolean heart, int chunks, long price, boolean overlord) {
        public CedeAsk {
            chunks = Math.max(0, chunks);
            price = Math.max(0, price);
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
     * What a warrior goes to war for: the provinces of a state of the world that borders its own and is twice weaker, else
     * vassalage of a neighbour three times weaker, else loot. Land is never the goal against a kingdom of players: rulers of
     * the world do not fight for places of their land.
     */
    public static WarGoal goal(Self self, Neighbor target) {
        if (self.character() == RulerCharacter.WARRIOR && target.bordering() && target.npc() && self.power() >= target.power() * 2.0) {
            return WarGoal.LAND;
        }
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
            case CEDE -> {
                // A cession is weighed by cede, which knows the land; without it the ruler keeps what it has.
                return new Verdict(false, DISTRUST);
            }
        }
        return new Verdict(false, DISTRUST);
    }

    /**
     * How a ruler answers a neighbour that asks for a province: never the heart of its state, never to a neighbour it dislikes
     * (a relation below -20), never for nothing when it is a warrior. Otherwise it sells for a price of at least
     * {@link #cedeValue}, and gives an empty province for nothing to its overlord, or to a dear friend (a relation of 80 or
     * more) when it is generous.
     *
     * @param asker the kingdom that asks for the province
     */
    public static Verdict cede(Self self, Neighbor asker, CedeAsk ask) {
        if (ask.heart()) {
            return new Verdict(false, HEART);
        }
        if (asker.relation() < -20) {
            return new Verdict(false, DISTRUST);
        }
        if (self.character() == RulerCharacter.WARRIOR && ask.price() <= 0) {
            return new Verdict(false, PROUD);
        }
        if (ask.price() >= cedeValue(self, asker, ask)) {
            return new Verdict(true, AGREED);
        }
        boolean gift = !ask.settled() && (ask.overlord() || (asker.relation() >= 80 && self.has(RulerCharacter.Trait.GENEROUS)));
        return gift ? new Verdict(true, AGREED) : new Verdict(false, LOW_PRICE);
    }

    /**
     * What a ruler wants for a province: 3 a chunk, more from a warrior (x1.4) and less from a merchant (x0.85), less from a
     * generous ruler (x0.9) and more from a proud one (x1.25), more from one who dislikes the asker (x1.2 at a relation of 0
     * or below, down to x0.9 at 50 or above), at least {@value #MIN_CEDE_VALUE}, and twice as much where people live.
     */
    public static long cedeValue(Self self, Neighbor asker, CedeAsk ask) {
        double character = switch (self.character()) {
            case MERCHANT -> 0.85;
            case BUILDER, MAGE -> 1.0;
            case WARRIOR -> 1.4;
        };
        double traits = 1.0;
        if (self.has(RulerCharacter.Trait.GENEROUS)) {
            traits *= 0.9;
        }
        if (self.has(RulerCharacter.Trait.PROUD)) {
            traits *= 1.25;
        }
        double friendship = Math.max(0, Math.min(50, asker.relation())) / 50.0;
        double relation = 1.2 - 0.3 * friendship;
        long value = Math.max(MIN_CEDE_VALUE, Math.round(ask.chunks() * CEDE_CHUNK_VALUE * character * traits * relation));
        return ask.settled() ? value * 2 : value;
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
