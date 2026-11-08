package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Diplomacy and wars of a world: relations of every pair of kingdoms, treaties (pacts, alliances, vassalage, truces), offers
 * waiting for an answer, wars with their sides, scores and exhaustion, tributes to pay and claims of land, cities and thrones
 * for whoever takes them in the world. Every change goes through a method that checks it first and refuses with a
 * {@link KingdomError}; money moves outside, in the books of the treasuries, before the state records the change.
 */
public final class DiplomacyState {
    /** Closed offers are kept this long for the tab, then forgotten. */
    public static final long CLOSED_PROPOSAL_TICKS = 2L * CitizenNeeds.TICKS_PER_DAY;
    /** Wars that are over are kept this long for the tab and the atlas, then forgotten. */
    public static final long ENDED_WAR_TICKS = 10L * CitizenNeeds.TICKS_PER_DAY;
    /** Most open offers one kingdom may have made. */
    public static final int MAX_OPEN_PROPOSALS = 16;

    /**
     * A tribute for some days after a war.
     *
     * @param share   share of the daily income of the payer, in permille
     * @param untilDay the game day after which it is no longer paid
     */
    public record Tribute(UUID payer, UUID payee, int share, long untilDay, long war) {
        public Tribute {
            Objects.requireNonNull(payer, "payer");
            Objects.requireNonNull(payee, "payee");
            if (payer.equals(payee) || share < 0 || share > PeaceTerms.MAX_SHARE) {
                throw new IllegalArgumentException("Tribute out of range");
            }
        }
    }

    /**
     * Something a peace gave the winner in the world: chunks of land, a city or the throne. Taken by a later stage.
     */
    public record Claim(long war, UUID winner, UUID loser, int chunks, String city, boolean throne, long made) {
        public Claim {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(loser, "loser");
            city = city == null ? "" : city;
        }
    }

    /**
     * What a declaration of war did.
     *
     * @param broken the treaty broken for it, if any
     */
    public record Declared(Outcome outcome, Optional<War> war, Optional<Treaty> broken) {
        static Declared fail(KingdomError error) {
            return new Declared(Outcome.fail(error), Optional.empty(), Optional.empty());
        }
    }

    /**
     * What breaking a treaty did.
     *
     * @param heard the kingdoms who heard of it and think worse of the breaker
     */
    public record Broken(Outcome outcome, Optional<Treaty> treaty, List<UUID> heard) {
        static Broken fail(KingdomError error) {
            return new Broken(Outcome.fail(error), Optional.empty(), List.of());
        }
    }

    public record Proposed(Outcome outcome, Optional<Proposal> proposal) {
    }

    /**
     * A war that ends by itself: one side gave in, or the time ran out.
     */
    public record Ending(War war, War.Result how, PeaceTerms terms) {
    }

    /**
     * Money a peace moves at once: the loser pays the winner.
     */
    public record Settlement(UUID payer, UUID payee, long amount) {
    }

    private final Map<String, Treaty> treaties = new LinkedHashMap<>();
    private final Map<Long, War> wars = new LinkedHashMap<>();
    private final Map<Long, Proposal> proposals = new LinkedHashMap<>();
    private final Relations relations = new Relations();
    private final Presence presence = new Presence();
    private final List<Tribute> tributes = new ArrayList<>();
    private final List<Claim> claims = new ArrayList<>();
    /** The game day each kingdom last declared a war. */
    private final Map<UUID, Long> lastDeclared = new LinkedHashMap<>();
    /** The game day a ruler of the world last declared war on each kingdom of players. */
    private final Map<UUID, Long> lastNpcWar = new LinkedHashMap<>();
    /** The game time each kingdom was last in a war that ended. */
    private final Map<UUID, Long> lastWarEnd = new LinkedHashMap<>();
    private long nextWarId = 1;
    private long nextProposalId = 1;
    private long tributeDay = Long.MIN_VALUE;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {
        } : listener;
    }

    public void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Relations relations() {
        return relations;
    }

    public Presence presence() {
        return presence;
    }

    public Collection<Treaty> treaties() {
        return Collections.unmodifiableCollection(treaties.values());
    }

    public Collection<War> wars() {
        return Collections.unmodifiableCollection(wars.values());
    }

    public Optional<War> war(long id) {
        return Optional.ofNullable(wars.get(id));
    }

    public Collection<Proposal> proposals() {
        return Collections.unmodifiableCollection(proposals.values());
    }

    public Optional<Proposal> proposal(long id) {
        return Optional.ofNullable(proposals.get(id));
    }

    public List<Tribute> tributes() {
        return Collections.unmodifiableList(tributes);
    }

    public List<Claim> claims() {
        return Collections.unmodifiableList(claims);
    }

    public Map<UUID, Long> lastDeclared() {
        return Collections.unmodifiableMap(lastDeclared);
    }

    public Map<UUID, Long> lastNpcWar() {
        return Collections.unmodifiableMap(lastNpcWar);
    }

    public Map<UUID, Long> lastWarEnd() {
        return Collections.unmodifiableMap(lastWarEnd);
    }

    public long nextWarId() {
        return nextWarId;
    }

    public long nextProposalId() {
        return nextProposalId;
    }

    public long tributeDay() {
        return tributeDay;
    }

    public Optional<Treaty> treaty(UUID a, UUID b) {
        return Optional.ofNullable(treaties.get(Treaty.key(a, b)));
    }

    /**
     * How two kingdoms stand: at war if a war (declared or fought) sets them against each other, else by their treaty, else
     * at peace.
     */
    public Bond bond(UUID a, UUID b, long now) {
        if (a.equals(b)) {
            return Bond.PEACE;
        }
        if (warBetween(a, b).isPresent()) {
            return Bond.WAR;
        }
        Treaty treaty = treaties.get(Treaty.key(a, b));
        if (treaty == null || treaty.expired(now)) {
            return Bond.PEACE;
        }
        return treaty.kind();
    }

    /**
     * A war, declared or fought, that sets two kingdoms against each other.
     */
    public Optional<War> warBetween(UUID a, UUID b) {
        for (War war : wars.values()) {
            if (war.opposes(a, b)) {
                return Optional.of(war);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether two kingdoms fight each other now: a war between them has started and is not over.
     */
    public boolean fighting(UUID a, UUID b) {
        for (War war : wars.values()) {
            if (war.active() && war.opposes(a, b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a kingdom fights any war now.
     */
    public boolean fightsAnyWar(UUID kingdom) {
        for (War war : wars.values()) {
            if (war.active() && war.sideOf(kingdom).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wars of a kingdom that are not over.
     */
    public List<War> warsOf(UUID kingdom) {
        List<War> result = new ArrayList<>();
        for (War war : wars.values()) {
            if (!war.over() && war.sideOf(kingdom).isPresent()) {
                result.add(war);
            }
        }
        return result;
    }

    /**
     * The enemies a kingdom fights now.
     */
    public Set<UUID> enemiesOf(UUID kingdom) {
        Set<UUID> enemies = new LinkedHashSet<>();
        for (War war : wars.values()) {
            if (!war.active()) {
                continue;
            }
            war.sideOf(kingdom).ifPresent(side -> enemies.addAll(war.members(side.enemy())));
        }
        return enemies;
    }

    public Optional<UUID> overlordOf(UUID vassal) {
        for (Treaty treaty : treaties.values()) {
            if (treaty.kind() == Bond.VASSAL && vassal.equals(treaty.vassal())) {
                return Optional.of(treaty.overlord());
            }
        }
        return Optional.empty();
    }

    public List<UUID> vassalsOf(UUID overlord) {
        List<UUID> result = new ArrayList<>();
        for (Treaty treaty : treaties.values()) {
            if (treaty.kind() == Bond.VASSAL && overlord.equals(treaty.overlord())) {
                result.add(treaty.vassal());
            }
        }
        return result;
    }

    public List<UUID> alliesOf(UUID kingdom, long now) {
        List<UUID> result = new ArrayList<>();
        for (Treaty treaty : treaties.values()) {
            if (treaty.kind() == Bond.ALLIANCE && treaty.involves(kingdom) && !treaty.expired(now)) {
                result.add(treaty.other(kingdom));
            }
        }
        return result;
    }

    /**
     * Every kingdom that has dealings with any of the given ones: a relation on record or a treaty.
     */
    public Set<UUID> acquaintances(UUID... kingdoms) {
        Set<UUID> result = new LinkedHashSet<>();
        Set<UUID> these = Set.of(kingdoms);
        for (String key : relations.all().keySet()) {
            String[] pair = key.split("\\|");
            UUID a = UUID.fromString(pair[0]);
            UUID b = UUID.fromString(pair[1]);
            if (these.contains(a)) {
                result.add(b);
            }
            if (these.contains(b)) {
                result.add(a);
            }
        }
        for (Treaty treaty : treaties.values()) {
            for (UUID kingdom : kingdoms) {
                if (treaty.involves(kingdom)) {
                    result.add(treaty.other(kingdom));
                }
            }
        }
        result.removeAll(these);
        return result;
    }

    /**
     * Open offers that wait for an answer of a kingdom, or that it made.
     */
    public List<Proposal> openProposals(UUID kingdom) {
        List<Proposal> result = new ArrayList<>();
        for (Proposal proposal : proposals.values()) {
            if (proposal.open() && proposal.involves(kingdom)) {
                result.add(proposal);
            }
        }
        return result;
    }

    /**
     * The game time a kingdom was last in a war that ended, if ever.
     */
    public Optional<Long> lastWarEnded(UUID kingdom) {
        return Optional.ofNullable(lastWarEnd.get(kingdom));
    }

    // ---------------------------------------------------------------- relations

    public int relation(UUID a, UUID b) {
        return relations.score(a, b);
    }

    public void relate(UUID a, UUID b, Relations.Reason reason) {
        relations.add(a, b, reason);
        changed();
    }

    // ---------------------------------------------------------------- war

    /**
     * Whether a kingdom may declare war on another now.
     *
     * @param breakTreaty       whether the attacker is ready to break a pact or an alliance with the defender for it
     * @param defenderPlayable  whether the defender can defend itself: a kingdom of the world, or a kingdom of players with a
     *                          player online at the keyboard
     */
    public Outcome checkDeclare(UUID attacker, UUID defender, WarGoal goal, long now, boolean breakTreaty, boolean defenderPlayable) {
        Objects.requireNonNull(goal, "goal");
        if (attacker.equals(defender)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (warBetween(attacker, defender).isPresent()) {
            return Outcome.fail(KingdomError.AT_WAR);
        }
        Treaty treaty = treaties.get(Treaty.key(attacker, defender));
        if (treaty != null && treaty.expired(now)) {
            treaty = null;
        }
        Optional<UUID> overlord = overlordOf(attacker);
        if (goal == WarGoal.INDEPENDENCE) {
            if (overlord.isEmpty() || !overlord.get().equals(defender)) {
                return Outcome.fail(KingdomError.NOT_A_VASSAL);
            }
        } else {
            if (overlord.isPresent()) {
                return Outcome.fail(KingdomError.VASSAL_NO_WAR);
            }
            if (treaty != null && treaty.kind() == Bond.VASSAL) {
                return Outcome.fail(KingdomError.OWN_VASSAL);
            }
        }
        if (treaty != null) {
            switch (treaty.kind()) {
                case TRUCE -> {
                    return Outcome.fail(KingdomError.IN_TRUCE);
                }
                case PACT -> {
                    if (!breakTreaty) {
                        return Outcome.fail(KingdomError.IN_PACT);
                    }
                }
                case ALLIANCE -> {
                    if (!breakTreaty) {
                        return Outcome.fail(KingdomError.IN_ALLIANCE);
                    }
                }
                default -> {
                }
            }
        }
        if (!defenderPlayable) {
            return Outcome.fail(KingdomError.NOBODY_ONLINE);
        }
        return Outcome.DONE;
    }

    /**
     * Declares war. The war starts after the time of preparation; the vassals of both leaders and the overlord of the
     * defender join at once, the allies of the defender are called and answer for themselves. A pact or an alliance broken for
     * the war costs the attacker: the defender starts with points of score, and everybody who knows thinks worse of it.
     */
    public Declared declare(UUID attacker, UUID defender, WarGoal goal, long now, long day, DiplomacyRules rules, boolean breakTreaty,
                            boolean defenderPlayable) {
        Outcome check = checkDeclare(attacker, defender, goal, now, breakTreaty, defenderPlayable);
        if (!check.ok()) {
            return Declared.fail(check.error());
        }
        Optional<Treaty> broken = Optional.empty();
        Treaty treaty = treaties.get(Treaty.key(attacker, defender));
        if (treaty != null && (treaty.kind() == Bond.PACT || treaty.kind() == Bond.ALLIANCE) && !treaty.expired(now)) {
            broken = Optional.of(treaty);
            breakQuietly(attacker, defender, treaty);
        } else if (treaty != null && (treaty.expired(now) || goal != WarGoal.INDEPENDENCE)) {
            treaties.remove(treaty.key());
        }
        War war = new War(nextWarId++, attacker, defender, goal, now, now + rules.preparationTicks());
        if (broken.isPresent()) {
            war.markBrokePact();
            war.gain(War.Side.DEFENDER, rules.pactPenalty());
        }
        war.setLastDay(day);
        for (UUID vassal : vassalsOf(attacker)) {
            if (!vassal.equals(defender)) {
                war.join(vassal, War.Side.ATTACKER);
            }
        }
        for (UUID vassal : vassalsOf(defender)) {
            if (!vassal.equals(attacker)) {
                war.join(vassal, War.Side.DEFENDER);
            }
        }
        overlordOf(defender).filter(lord -> !lord.equals(attacker) && war.sideOf(lord).isEmpty())
                .ifPresent(lord -> war.join(lord, War.Side.DEFENDER));
        for (UUID ally : alliesOf(defender, now)) {
            if (war.sideOf(ally).isEmpty()) {
                war.call(ally, War.Side.DEFENDER);
            }
        }
        wars.put(war.id(), war);
        relations.add(attacker, defender, Relations.Reason.WAR);
        lastDeclared.put(attacker, day);
        cancelOpen(attacker, defender, Proposal.Kind.PACT, Proposal.Kind.ALLIANCE, Proposal.Kind.VASSALAGE, Proposal.Kind.RELEASE);
        changed();
        return new Declared(Outcome.DONE, Optional.of(war), broken);
    }

    /**
     * Notes that a ruler of the world declared war on a kingdom of players, for the limits on such wars.
     */
    public void noteNpcWar(UUID playerKingdom, long day) {
        lastNpcWar.put(playerKingdom, day);
        changed();
    }

    /**
     * An ally answers a call to war.
     */
    public Outcome answerCall(long warId, UUID ally, boolean join) {
        War war = wars.get(warId);
        if (war == null || war.over()) {
            return Outcome.fail(KingdomError.UNKNOWN_WAR);
        }
        War.Side side = war.calls().get(ally);
        if (side == null) {
            return Outcome.fail(KingdomError.NOT_CALLED);
        }
        war.uncall(ally);
        if (join) {
            war.join(ally, side);
            relations.add(ally, war.leader(side), Relations.Reason.CALL_ANSWERED);
            relations.add(ally, war.leader(side.enemy()), Relations.Reason.WAR);
        } else {
            relations.add(ally, war.leader(side), Relations.Reason.CALL_REFUSED);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Wars whose time of preparation is over start.
     *
     * @return the wars that started
     */
    public List<War> startDue(long now) {
        List<War> started = new ArrayList<>();
        for (War war : wars.values()) {
            if (war.phase() == War.Phase.PREPARING && now >= war.starts()) {
                war.start();
                started.add(war);
            }
        }
        if (!started.isEmpty()) {
            changed();
        }
        return started;
    }

    /**
     * Points of score for a side, from something a kingdom did against its enemy.
     *
     * @param kingdom the kingdom that did it
     * @param enemy   the kingdom it was done against
     * @param kind    what was done, for the tally
     * @return the points added, 0 if they are not at war or the war has not started
     */
    public double score(UUID kingdom, UUID enemy, Feat kind, double points) {
        for (War war : wars.values()) {
            if (!war.active() || !war.opposes(kingdom, enemy)) {
                continue;
            }
            War.Side side = war.sideOf(kingdom).orElseThrow();
            War.Tally tally = war.tally(side);
            switch (kind) {
                case BATTLE -> tally.battles++;
                case KILL -> tally.kills++;
                case CARAVAN -> tally.caravans++;
                case BLOCKADE -> tally.blockades++;
                case HOLD -> {
                }
            }
            double added = war.gain(side, points);
            changed();
            return added;
        }
        return 0.0;
    }

    /**
     * Something a side did against its enemy.
     */
    public enum Feat {
        BATTLE,
        KILL,
        CARAVAN,
        BLOCKADE,
        HOLD
    }

    /**
     * Exhaustion of the side of a kingdom in its war with an enemy: losses, caravans lost.
     *
     * @param lost fighters lost, for the tally
     */
    public double exhaust(UUID kingdom, UUID enemy, double points, int lost) {
        for (War war : wars.values()) {
            if (!war.active() || !war.opposes(kingdom, enemy)) {
                continue;
            }
            War.Side side = war.sideOf(kingdom).orElseThrow();
            war.tally(side).losses += Math.max(0, lost);
            double added = war.exhaust(side, points);
            changed();
            return added;
        }
        return 0.0;
    }

    /**
     * Sets the score or exhaustion of a side directly, for operators.
     */
    public Outcome setScore(long warId, War.Side side, double score, double exhaustion) {
        War war = wars.get(warId);
        if (war == null || war.over()) {
            return Outcome.fail(KingdomError.UNKNOWN_WAR);
        }
        if (score >= 0) {
            war.gain(side, score - war.score(side));
        }
        if (exhaustion >= 0) {
            war.exhaust(side, exhaustion - war.exhaustion(side));
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * A day of war: every side that is not shielded grows tired (more with an empty treasury). A side worn out to the end gives
     * in on the terms of the enemy; a war past its longest time ends in a white peace.
     *
     * @param shielded whether the players of a kingdom are all away and it is shielded
     * @param poor     whether the treasury of a kingdom is empty
     * @param funds    what the treasury of a kingdom holds, for the terms
     * @return the wars that end; the caller settles them and calls {@link #endWar}
     */
    public List<Ending> daily(long now, long day, DiplomacyRules rules, Predicate<UUID> shielded, Predicate<UUID> poor, ToLongFunction<UUID> funds) {
        List<Ending> endings = new ArrayList<>();
        for (War war : wars.values()) {
            if (!war.active()) {
                continue;
            }
            if (war.lastDay() < day) {
                war.setLastDay(day);
                for (War.Side side : War.Side.values()) {
                    UUID leader = war.leader(side);
                    if (shielded.test(leader)) {
                        continue;
                    }
                    war.exhaust(side, rules.exhaustionDay() + (poor.test(leader) ? rules.exhaustionPoor() : 0.0));
                }
                changed();
            }
            Optional<Ending> ending = ending(war, now, rules, funds);
            ending.ifPresent(endings::add);
        }
        return endings;
    }

    /**
     * Whether a war ends by itself now, and how.
     */
    public Optional<Ending> ending(War war, long now, DiplomacyRules rules, ToLongFunction<UUID> funds) {
        if (!war.active()) {
            return Optional.empty();
        }
        boolean attackerDone = war.exhaustion(War.Side.ATTACKER) >= War.MAX;
        boolean defenderDone = war.exhaustion(War.Side.DEFENDER) >= War.MAX;
        if (attackerDone && defenderDone) {
            return Optional.of(new Ending(war, War.Result.SURRENDER, PeaceTerms.WHITE));
        }
        if (attackerDone || defenderDone) {
            War.Side winner = attackerDone ? War.Side.DEFENDER : War.Side.ATTACKER;
            return Optional.of(new Ending(war, War.Result.SURRENDER, feasible(war, terms(war, winner, true,
                    funds.applyAsLong(war.leader(winner.enemy())), rules), rules)));
        }
        if (now - war.starts() >= (long) rules.maxWarDays() * CitizenNeeds.TICKS_PER_DAY) {
            return Optional.of(new Ending(war, War.Result.TIME, PeaceTerms.WHITE));
        }
        return Optional.empty();
    }

    /**
     * The terms a side of a war may ask: its goal in full with a clear lead (or when the enemy gives in), half of it with a
     * smaller lead, nothing (a white peace) without one.
     *
     * @param full       whether to ask for the goal in full whatever the score (the enemy gives in)
     * @param loserFunds what the treasury of the enemy holds
     */
    public static PeaceTerms terms(War war, War.Side side, boolean full, long loserFunds, DiplomacyRules rules) {
        UUID winner = war.leader(side);
        PeaceTerms goal = PeaceTerms.forGoal(war.goal(), winner, side == War.Side.ATTACKER, loserFunds, rules);
        if (full || war.lead(side) >= rules.imposeLead()) {
            return goal;
        }
        if (war.lead(side) > 0) {
            return goal.halved(rules);
        }
        return PeaceTerms.WHITE;
    }

    /**
     * Terms as they can be carried out: vassalage where it cannot be (the loser is somebody's vassal or has vassals of its
     * own, the winner is a vassal) becomes a tribute for some days.
     */
    public PeaceTerms feasible(War war, PeaceTerms terms, DiplomacyRules rules) {
        if (terms.vassalage() && !checkTerms(war, terms).ok()) {
            return terms.withoutVassalage(rules);
        }
        return terms;
    }

    /**
     * Whether a side may ask these terms now: no more than {@link #terms} allows.
     */
    public static boolean within(PeaceTerms asked, PeaceTerms allowed) {
        if (asked.isWhite()) {
            return true;
        }
        if (allowed.isWhite() || !asked.winner().equals(allowed.winner())) {
            return false;
        }
        return asked.indemnity() <= allowed.indemnity() && asked.tributeShare() <= Math.max(allowed.tributeShare(), allowed.vassalage() ? PeaceTerms.MAX_SHARE : 0)
                && asked.tributeDays() <= Math.max(allowed.tributeDays(), allowed.vassalage() ? PeaceTerms.MAX_DAYS : 0)
                && (!asked.vassalage() || allowed.vassalage()) && (!asked.independence() || allowed.independence())
                && asked.landChunks() <= allowed.landChunks() && (asked.city().isEmpty() || !allowed.city().isEmpty())
                && (!asked.throne() || allowed.throne());
    }

    /**
     * Whether terms of peace fit a war: the winner leads a side, independence only for a vassal at war with its overlord,
     * vassalage only of a loser that is nobody's vassal yet.
     */
    public Outcome checkTerms(War war, PeaceTerms terms) {
        if (war.over()) {
            return Outcome.fail(KingdomError.UNKNOWN_WAR);
        }
        if (terms.isWhite()) {
            return Outcome.DONE;
        }
        if (!(terms.winner().equals(war.attacker()) || terms.winner().equals(war.defender()))) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        UUID loser = terms.winner().equals(war.attacker()) ? war.defender() : war.attacker();
        if (terms.independence() && !(war.goal() == WarGoal.INDEPENDENCE && terms.winner().equals(war.attacker()))) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (terms.vassalage() && (overlordOf(loser).filter(lord -> !lord.equals(terms.winner())).isPresent() || !vassalsOf(loser).isEmpty()
                || overlordOf(terms.winner()).isPresent())) {
            return Outcome.fail(KingdomError.TREATY_IMPOSSIBLE);
        }
        return Outcome.DONE;
    }

    /**
     * The loser of terms: the leader of the side that does not win, {@code null} for a white peace.
     */
    public static UUID loser(War war, PeaceTerms terms) {
        if (terms.isWhite()) {
            return null;
        }
        return terms.winner().equals(war.attacker()) ? war.defender() : war.attacker();
    }

    /**
     * The money terms move at once.
     */
    public static Optional<Settlement> settlement(War war, PeaceTerms terms) {
        if (terms.isWhite() || terms.indemnity() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Settlement(loser(war, terms), terms.winner(), terms.indemnity()));
    }

    /**
     * Ends a war. The indemnity is paid already; here the truce starts between the two sides, the loser becomes a vassal or
     * owes a tribute, a vassal goes free, and claims of land, cities and thrones are recorded.
     */
    public Outcome endWar(long warId, War.Result how, PeaceTerms terms, long now, long day, DiplomacyRules rules) {
        War war = wars.get(warId);
        if (war == null || war.over()) {
            return Outcome.fail(KingdomError.UNKNOWN_WAR);
        }
        Outcome check = checkTerms(war, terms);
        if (!check.ok()) {
            return check;
        }
        long until = now + (long) rules.truceDays() * CitizenNeeds.TICKS_PER_DAY;
        UUID loser = loser(war, terms);
        for (UUID a : war.members(War.Side.ATTACKER)) {
            for (UUID b : war.members(War.Side.DEFENDER)) {
                Treaty existing = treaties.get(Treaty.key(a, b));
                boolean leaders = (a.equals(war.attacker()) && b.equals(war.defender()));
                if (existing != null && existing.kind() == Bond.VASSAL && !(leaders && terms.independence())) {
                    continue;
                }
                if (rules.truceDays() > 0) {
                    treaties.put(Treaty.key(a, b), Treaty.of(a, b, Bond.TRUCE, now, until, null, 0));
                } else {
                    treaties.remove(Treaty.key(a, b));
                }
            }
        }
        if (terms.vassalage() && loser != null) {
            treaties.put(Treaty.key(terms.winner(), loser), Treaty.of(terms.winner(), loser, Bond.VASSAL, now, Long.MAX_VALUE, terms.winner(),
                    rules.vassalShare()));
        }
        if (terms.tributeShare() > 0 && loser != null) {
            tributes.add(new Tribute(loser, terms.winner(), terms.tributeShare(), day + terms.tributeDays(), war.id()));
        }
        if (terms.claims() && loser != null) {
            claims.add(new Claim(war.id(), terms.winner(), loser, terms.landChunks(), terms.city(), terms.throne(), now));
        }
        war.end(now, how, terms);
        relations.add(war.attacker(), war.defender(), Relations.Reason.PEACE);
        for (War.Side side : War.Side.values()) {
            for (UUID member : war.members(side)) {
                lastWarEnd.put(member, now);
            }
        }
        for (Proposal proposal : List.copyOf(proposals.values())) {
            if (proposal.open() && proposal.kind() == Proposal.Kind.PEACE && proposal.war() == warId) {
                proposals.put(proposal.id(), proposal.withStatus(Proposal.Status.CANCELLED));
            }
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Takes a claim for whoever carries it out in the world.
     */
    public Optional<Claim> takeClaim(int index) {
        if (index < 0 || index >= claims.size()) {
            return Optional.empty();
        }
        Claim claim = claims.remove(index);
        changed();
        return Optional.of(claim);
    }

    // ---------------------------------------------------------------- treaties

    /**
     * A kingdom breaks a pact or an alliance, or lets a vassal go. A broken treaty costs the breaker with the other kingdom
     * and, a little, with everybody who knows either of them. A vassal cannot break its vassalage this way and a truce cannot
     * be broken at all.
     */
    public Broken breakTreaty(UUID kingdom, UUID other, long now) {
        Treaty treaty = treaties.get(Treaty.key(kingdom, other));
        if (treaty == null || treaty.expired(now)) {
            return Broken.fail(KingdomError.NO_TREATY);
        }
        switch (treaty.kind()) {
            case TRUCE -> {
                return Broken.fail(KingdomError.IN_TRUCE);
            }
            case VASSAL -> {
                if (!kingdom.equals(treaty.overlord())) {
                    return Broken.fail(KingdomError.OWN_VASSAL);
                }
                treaties.remove(treaty.key());
                relations.add(kingdom, other, Relations.Reason.RELEASED);
                changed();
                return new Broken(Outcome.DONE, Optional.of(treaty), List.of());
            }
            default -> {
                List<UUID> heard = breakQuietly(kingdom, other, treaty);
                changed();
                return new Broken(Outcome.DONE, Optional.of(treaty), heard);
            }
        }
    }

    private List<UUID> breakQuietly(UUID breaker, UUID victim, Treaty treaty) {
        List<UUID> heard = new ArrayList<>(acquaintances(breaker, victim));
        treaties.remove(treaty.key());
        relations.add(breaker, victim, Relations.Reason.BROKEN_TREATY);
        for (UUID kingdom : heard) {
            relations.add(breaker, kingdom, Relations.Reason.BROKEN_WORD);
        }
        return heard;
    }

    /**
     * Pacts and truces that ran out end.
     *
     * @return the treaties that ended
     */
    public List<Treaty> expireTreaties(long now) {
        List<Treaty> ended = new ArrayList<>();
        for (Iterator<Treaty> it = treaties.values().iterator(); it.hasNext(); ) {
            Treaty treaty = it.next();
            if (treaty.expired(now)) {
                ended.add(treaty);
                it.remove();
            }
        }
        if (!ended.isEmpty()) {
            changed();
        }
        return ended;
    }

    // ---------------------------------------------------------------- offers

    /**
     * Whether a kingdom may make an offer to another now.
     *
     * @param loserFunds for peace: what the treasury of the side that would pay holds
     */
    public Outcome checkPropose(UUID from, UUID to, Proposal.Kind kind, UUID vassal, long warId, PeaceTerms terms, long now, long loserFunds,
                                DiplomacyRules rules) {
        if (from.equals(to)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        int open = 0;
        for (Proposal proposal : proposals.values()) {
            if (!proposal.open()) {
                continue;
            }
            if (proposal.from().equals(from)) {
                open++;
            }
            boolean same = kind == Proposal.Kind.PEACE ? proposal.from().equals(from) && proposal.to().equals(to) && proposal.war() == warId
                    : proposal.involves(from) && proposal.involves(to);
            if (proposal.kind() == kind && same) {
                return Outcome.fail(KingdomError.PROPOSAL_EXISTS);
            }
        }
        if (open >= MAX_OPEN_PROPOSALS) {
            return Outcome.fail(KingdomError.TOO_MANY_PROPOSALS);
        }
        return checkStillPossible(from, to, kind, vassal, warId, terms, now, loserFunds, rules);
    }

    private Outcome checkStillPossible(UUID from, UUID to, Proposal.Kind kind, UUID vassal, long warId, PeaceTerms terms, long now, long loserFunds,
                                       DiplomacyRules rules) {
        Bond bond = bond(from, to, now);
        switch (kind) {
            case PACT -> {
                if (bond != Bond.PEACE) {
                    return Outcome.fail(bond == Bond.WAR ? KingdomError.AT_WAR : KingdomError.TREATY_IMPOSSIBLE);
                }
            }
            case ALLIANCE -> {
                if (bond != Bond.PEACE && bond != Bond.PACT) {
                    return Outcome.fail(bond == Bond.WAR ? KingdomError.AT_WAR : KingdomError.TREATY_IMPOSSIBLE);
                }
            }
            case VASSALAGE -> {
                if (vassal == null || !(vassal.equals(from) || vassal.equals(to))) {
                    return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
                }
                UUID lord = vassal.equals(from) ? to : from;
                if (bond == Bond.WAR || bond == Bond.VASSAL || bond == Bond.TRUCE || overlordOf(vassal).isPresent() || overlordOf(lord).isPresent()
                        || !vassalsOf(vassal).isEmpty()) {
                    return Outcome.fail(bond == Bond.WAR ? KingdomError.AT_WAR : KingdomError.TREATY_IMPOSSIBLE);
                }
            }
            case RELEASE -> {
                Optional<UUID> lord = overlordOf(from);
                if (lord.isEmpty() || !lord.get().equals(to)) {
                    return Outcome.fail(KingdomError.NOT_A_VASSAL);
                }
            }
            case PEACE -> {
                War war = wars.get(warId);
                if (war == null || war.over()) {
                    return Outcome.fail(KingdomError.UNKNOWN_WAR);
                }
                boolean leaders = (from.equals(war.attacker()) && to.equals(war.defender())) || (from.equals(war.defender()) && to.equals(war.attacker()));
                if (!leaders) {
                    return Outcome.fail(KingdomError.NOT_AT_WAR);
                }
                Outcome fit = checkTerms(war, terms);
                if (!fit.ok()) {
                    return fit;
                }
                if (!terms.isWhite() && terms.winner().equals(from)) {
                    War.Side side = war.sideOf(from).orElseThrow();
                    if (!within(terms, terms(war, side, false, loserFunds, rules))) {
                        return Outcome.fail(KingdomError.TERMS_TOO_HIGH);
                    }
                }
            }
        }
        return Outcome.DONE;
    }

    /**
     * Makes an offer.
     */
    public Proposed propose(UUID from, UUID to, Proposal.Kind kind, UUID vassal, long warId, PeaceTerms terms, long now, long loserFunds,
                            DiplomacyRules rules) {
        Outcome check = checkPropose(from, to, kind, vassal, warId, terms, now, loserFunds, rules);
        if (!check.ok()) {
            return new Proposed(check, Optional.empty());
        }
        Proposal proposal = new Proposal(nextProposalId++, from, to, kind, vassal, warId, terms, now, now + rules.proposalTicks(),
                Proposal.Status.OPEN);
        proposals.put(proposal.id(), proposal);
        changed();
        return new Proposed(Outcome.DONE, Optional.of(proposal));
    }

    /**
     * Whether a kingdom may accept an offer now: it is open, made to this kingdom and still possible.
     */
    public Outcome checkAccept(long id, UUID kingdom, long now, long loserFunds, DiplomacyRules rules) {
        Proposal proposal = proposals.get(id);
        if (proposal == null) {
            return Outcome.fail(KingdomError.UNKNOWN_PROPOSAL);
        }
        if (!proposal.open() || now >= proposal.expires()) {
            return Outcome.fail(KingdomError.PROPOSAL_CLOSED);
        }
        if (!proposal.to().equals(kingdom)) {
            return Outcome.fail(KingdomError.NOT_YOUR_PROPOSAL);
        }
        if (proposal.kind() == Proposal.Kind.PEACE) {
            // Terms asked by the side that gives in are always allowed; the check of the lead was made when it was offered.
            War war = wars.get(proposal.war());
            if (war == null || war.over()) {
                return Outcome.fail(KingdomError.UNKNOWN_WAR);
            }
            return checkTerms(war, proposal.terms());
        }
        return checkStillPossible(proposal.from(), proposal.to(), proposal.kind(), proposal.vassal(), proposal.war(), proposal.terms(), now,
                loserFunds, rules);
    }

    /**
     * Accepts an offer. For peace the indemnity must be paid already (see {@link #settlement}).
     */
    public Outcome accept(long id, UUID kingdom, long now, long day, DiplomacyRules rules) {
        Outcome check = checkAccept(id, kingdom, now, Long.MAX_VALUE, rules);
        if (!check.ok()) {
            return check;
        }
        Proposal proposal = proposals.get(id);
        UUID a = proposal.from();
        UUID b = proposal.to();
        switch (proposal.kind()) {
            case PACT -> {
                treaties.put(Treaty.key(a, b), Treaty.of(a, b, Bond.PACT, now, now + (long) rules.pactDays() * CitizenNeeds.TICKS_PER_DAY, null, 0));
                relations.add(a, b, Relations.Reason.TREATY);
            }
            case ALLIANCE -> {
                treaties.put(Treaty.key(a, b), Treaty.of(a, b, Bond.ALLIANCE, now, Long.MAX_VALUE, null, 0));
                relations.add(a, b, Relations.Reason.ALLIANCE);
            }
            case VASSALAGE -> {
                treaties.put(Treaty.key(a, b), Treaty.of(a, b, Bond.VASSAL, now, Long.MAX_VALUE, proposal.overlord(), rules.vassalShare()));
                relations.add(a, b, Relations.Reason.TREATY);
            }
            case RELEASE -> {
                treaties.remove(Treaty.key(a, b));
                relations.add(a, b, Relations.Reason.RELEASED);
            }
            case PEACE -> {
                Outcome ended = endWar(proposal.war(), War.Result.PEACE, proposal.terms(), now, day, rules);
                if (!ended.ok()) {
                    return ended;
                }
            }
        }
        proposals.put(id, proposals.get(id).withStatus(Proposal.Status.ACCEPTED).withExpires(now));
        changed();
        return Outcome.DONE;
    }

    public Outcome decline(long id, UUID kingdom, long now) {
        Proposal proposal = proposals.get(id);
        if (proposal == null) {
            return Outcome.fail(KingdomError.UNKNOWN_PROPOSAL);
        }
        if (!proposal.open()) {
            return Outcome.fail(KingdomError.PROPOSAL_CLOSED);
        }
        if (!proposal.to().equals(kingdom)) {
            return Outcome.fail(KingdomError.NOT_YOUR_PROPOSAL);
        }
        proposals.put(id, proposal.withStatus(Proposal.Status.DECLINED).withExpires(now));
        relations.add(proposal.from(), proposal.to(), Relations.Reason.DECLINED);
        changed();
        return Outcome.DONE;
    }

    public Outcome cancel(long id, UUID kingdom, long now) {
        Proposal proposal = proposals.get(id);
        if (proposal == null) {
            return Outcome.fail(KingdomError.UNKNOWN_PROPOSAL);
        }
        if (!proposal.open()) {
            return Outcome.fail(KingdomError.PROPOSAL_CLOSED);
        }
        if (!proposal.from().equals(kingdom)) {
            return Outcome.fail(KingdomError.NOT_YOUR_PROPOSAL);
        }
        proposals.put(id, proposal.withStatus(Proposal.Status.CANCELLED).withExpires(now));
        changed();
        return Outcome.DONE;
    }

    private void cancelOpen(UUID a, UUID b, Proposal.Kind... kinds) {
        Set<Proposal.Kind> set = Set.of(kinds);
        for (Proposal proposal : List.copyOf(proposals.values())) {
            if (proposal.open() && proposal.involves(a) && proposal.involves(b) && set.contains(proposal.kind())) {
                proposals.put(proposal.id(), proposal.withStatus(Proposal.Status.CANCELLED));
            }
        }
    }

    /**
     * Offers nobody answered in time expire; old closed offers and wars long over are forgotten.
     *
     * @return the offers that expired now
     */
    public List<Proposal> expire(long now) {
        List<Proposal> expired = new ArrayList<>();
        boolean changed = false;
        for (Iterator<Map.Entry<Long, Proposal>> it = proposals.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Proposal> entry = it.next();
            Proposal proposal = entry.getValue();
            if (proposal.open() && now >= proposal.expires()) {
                Proposal closed = proposal.withStatus(Proposal.Status.EXPIRED);
                entry.setValue(closed);
                expired.add(closed);
                changed = true;
            } else if (!proposal.open() && now - proposal.expires() > CLOSED_PROPOSAL_TICKS) {
                it.remove();
                changed = true;
            }
        }
        changed |= wars.values().removeIf(war -> war.over() && now - war.ended() > ENDED_WAR_TICKS);
        if (changed) {
            changed();
        }
        return expired;
    }

    // ---------------------------------------------------------------- tributes

    /**
     * Tributes and vassals that pay on a day: every payer once a day, the share of its income to its receiver. Tributes that
     * ran out are forgotten.
     *
     * @return what is due today, empty if the day was paid already
     */
    public List<Tribute> dueTributes(long day) {
        if (day <= tributeDay) {
            return List.of();
        }
        tributeDay = day;
        tributes.removeIf(tribute -> tribute.untilDay() < day);
        List<Tribute> due = new ArrayList<>(tributes);
        for (Treaty treaty : treaties.values()) {
            if (treaty.kind() == Bond.VASSAL && treaty.share() > 0) {
                due.add(new Tribute(treaty.vassal(), treaty.overlord(), treaty.share(), Long.MAX_VALUE, 0));
            }
        }
        changed();
        return due;
    }

    // ---------------------------------------------------------------- kingdoms that are gone

    /**
     * Forgets a kingdom that is gone: its wars end in a white peace, its treaties, offers, tributes and claims go.
     */
    public void forget(UUID kingdom, long now) {
        for (War war : wars.values()) {
            if (war.over() || war.sideOf(kingdom).isEmpty()) {
                war.leave(kingdom);
                continue;
            }
            if (kingdom.equals(war.attacker()) || kingdom.equals(war.defender())) {
                war.end(now, War.Result.GONE, PeaceTerms.WHITE);
            } else {
                war.leave(kingdom);
            }
        }
        treaties.values().removeIf(treaty -> treaty.involves(kingdom));
        proposals.values().removeIf(proposal -> proposal.involves(kingdom));
        tributes.removeIf(tribute -> tribute.payer().equals(kingdom) || tribute.payee().equals(kingdom));
        claims.removeIf(claim -> claim.winner().equals(kingdom) || claim.loser().equals(kingdom));
        relations.forget(kingdom);
        presence.forget(kingdom);
        lastDeclared.remove(kingdom);
        lastNpcWar.remove(kingdom);
        lastWarEnd.remove(kingdom);
        changed();
    }

    /**
     * Relations fade a little: once a game day.
     */
    public void fade() {
        relations.fade();
        changed();
    }

    // ---------------------------------------------------------------- saved data

    public void restoreTreaty(Treaty treaty) {
        treaties.put(treaty.key(), treaty);
    }

    public void restoreWar(War war) {
        wars.put(war.id(), war);
        nextWarId = Math.max(nextWarId, war.id() + 1);
    }

    public void restoreProposal(Proposal proposal) {
        proposals.put(proposal.id(), proposal);
        nextProposalId = Math.max(nextProposalId, proposal.id() + 1);
    }

    public void restoreTribute(Tribute tribute) {
        tributes.add(tribute);
    }

    public void restoreClaim(Claim claim) {
        claims.add(claim);
    }

    public void restoreTimes(Map<UUID, Long> declared, Map<UUID, Long> npcWars, Map<UUID, Long> warEnds, long tributes) {
        lastDeclared.putAll(declared);
        lastNpcWar.putAll(npcWars);
        lastWarEnd.putAll(warEnds);
        tributeDay = tributes;
    }

    public void restoreNextIds(long war, long proposal) {
        nextWarId = Math.max(nextWarId, war);
        nextProposalId = Math.max(nextProposalId, proposal);
    }

    public void clear() {
        treaties.clear();
        wars.clear();
        proposals.clear();
        relations.clear();
        presence.clear();
        tributes.clear();
        claims.clear();
        lastDeclared.clear();
        lastNpcWar.clear();
        lastWarEnd.clear();
        nextWarId = 1;
        nextProposalId = 1;
        tributeDay = Long.MIN_VALUE;
    }
}
