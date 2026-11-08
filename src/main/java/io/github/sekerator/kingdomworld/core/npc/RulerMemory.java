package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the ruler of a kingdom of the world remembers between its turns: its last decrees, what the kingdom needs and what it
 * saves for, the decisions not carried out yet, how it stands with its neighbours and how it has dealt with players.
 */
public final class RulerMemory {
    /** Decrees kept for the overview. */
    public static final int MAX_DECREES = 8;
    /** Needs kept for the overview. */
    public static final int MAX_NEEDS = 6;
    /** Decisions waiting at most; older ones are dropped when a turn brings more. */
    public static final int MAX_PENDING = 16;
    /** Neighbours and players remembered at most; the ones with the weakest feelings are forgotten first. */
    public static final int MAX_RELATIONS = 64;
    /** How far a relation goes either way. */
    public static final int MAX_RELATION = 100;
    /** Decrees the chronicle of a kingdom keeps. */
    public static final int MAX_CHRONICLE = 32;
    /** Closed deals remembered, so that each counts once for the relations. */
    public static final int MAX_SEEN_DEALS = 64;

    /**
     * A decision carried out on a day.
     */
    public record Decree(long day, RulerDecision decision) {
        public Decree {
            Objects.requireNonNull(decision, "decision");
        }
    }

    /**
     * What the kingdom lacks or what the ruler saves for.
     *
     * @param subject an item, a role of a building or empty
     * @param amount  how many items or how much money; 0 when it does not count
     */
    public record Need(NeedKind kind, String subject, long amount) {
        public Need {
            Objects.requireNonNull(kind, "kind");
            subject = subject == null ? "" : subject;
        }
    }

    public enum NeedKind {
        /** Food for the citizens. */
        FOOD,
        /** Goods the storehouses lack; a player can sell them to the kingdom. */
        GOODS,
        /** A building the kingdom wants and saves for. */
        BUILDING,
        /** Money: the treasury is low. */
        MONEY,
        /** Soldiers against a threat. */
        SOLDIERS,
        /** Free land for its buildings. */
        LAND;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<NeedKind> byId(String id) {
            for (NeedKind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Why a ruler thinks better or worse of a neighbour.
     */
    public enum Reason {
        /** A deal was made. */
        TRADE(4),
        /** A deal was declined or not kept. */
        REFUSAL(-3),
        /** It claimed land the ruler wanted, or its land stands in the way. */
        LAND(-6),
        /** It let a road pass over its land. */
        PASSAGE(5),
        /** It refused a road over its land. */
        BLOCKED(-5);

        private final int weight;

        Reason(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Reason> byId(String id) {
            for (Reason reason : values()) {
                if (reason.id().equals(id)) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * How the ruler stands with another kingdom: a number from -100 to 100 and how often each reason came up.
     */
    public static final class Relation {
        private int score;
        private final EnumMap<Reason, Integer> reasons = new EnumMap<>(Reason.class);

        public int score() {
            return score;
        }

        public Map<Reason, Integer> reasons() {
            return Collections.unmodifiableMap(reasons);
        }

        void add(Reason reason, int times) {
            reasons.merge(reason, times, Integer::sum);
            score = clamp(score + reason.weight() * times);
        }

        void restore(int value, Map<Reason, Integer> counts) {
            score = clamp(value);
            reasons.clear();
            counts.forEach((reason, count) -> {
                if (count > 0) {
                    reasons.put(reason, count);
                }
            });
        }

        /**
         * The reason that weighs most in this relation, if any.
         */
        public Optional<Reason> main() {
            Reason best = null;
            int weight = 0;
            for (Map.Entry<Reason, Integer> entry : reasons.entrySet()) {
                int value = Math.abs(entry.getKey().weight() * entry.getValue());
                if (value > weight) {
                    weight = value;
                    best = entry.getKey();
                }
            }
            return Optional.ofNullable(best);
        }
    }

    private final List<Decree> decrees = new ArrayList<>();
    private final List<Decree> chronicle = new ArrayList<>();
    private final List<Long> seenDeals = new ArrayList<>();
    private final List<Need> needs = new ArrayList<>();
    private final List<String> spares = new ArrayList<>();
    private final List<RulerDecision> pending = new ArrayList<>();
    private final Map<UUID, Relation> relations = new LinkedHashMap<>();
    private final Map<UUID, Integer> players = new LinkedHashMap<>();
    private long chronicleDay = Long.MIN_VALUE;
    private int chronicleCount;
    private int turns;
    private long poorSince = -1;
    private UUID councillor;

    // ---------------------------------------------------------------- decrees

    public List<Decree> decrees() {
        return Collections.unmodifiableList(decrees);
    }

    /**
     * Remembers a decision that was carried out, the newest first.
     */
    public void decree(long day, RulerDecision decision) {
        decrees.add(0, new Decree(day, decision));
        while (decrees.size() > MAX_DECREES) {
            decrees.remove(decrees.size() - 1);
        }
    }

    /**
     * Whether one more decree may go to the chronicle on a day, counting it when it may.
     *
     * @param perDay decrees a day the chronicle takes from one kingdom
     */
    public boolean chronicle(long day, int perDay) {
        if (chronicleDay != day) {
            chronicleDay = day;
            chronicleCount = 0;
        }
        if (chronicleCount >= perDay) {
            return false;
        }
        chronicleCount++;
        return true;
    }

    /**
     * The important decrees of the ruler as the chronicle of the kingdom keeps them, the newest first.
     */
    public List<Decree> chronicle() {
        return Collections.unmodifiableList(chronicle);
    }

    /**
     * Writes an important decree into the chronicle if the day has room for it.
     *
     * @return whether it was written
     */
    public boolean writeChronicle(long day, RulerDecision decision, int perDay) {
        if (!decision.kind().important() || !chronicle(day, perDay)) {
            return false;
        }
        chronicle.add(0, new Decree(day, decision));
        while (chronicle.size() > MAX_CHRONICLE) {
            chronicle.remove(chronicle.size() - 1);
        }
        return true;
    }

    public void restoreChronicle(Decree decree) {
        if (chronicle.size() < MAX_CHRONICLE) {
            chronicle.add(decree);
        }
    }

    // ---------------------------------------------------------------- deals counted

    /**
     * Whether a closed deal was counted for the relations already; counts it if not.
     */
    public boolean seeDeal(long dealId) {
        if (seenDeals.contains(dealId)) {
            return false;
        }
        seenDeals.add(dealId);
        while (seenDeals.size() > MAX_SEEN_DEALS) {
            seenDeals.remove(0);
        }
        return true;
    }

    public List<Long> seenDeals() {
        return Collections.unmodifiableList(seenDeals);
    }

    public long chronicleDay() {
        return chronicleDay;
    }

    public int chronicleCount() {
        return chronicleCount;
    }

    // ---------------------------------------------------------------- needs

    public List<Need> needs() {
        return Collections.unmodifiableList(needs);
    }

    public void setNeeds(List<Need> value) {
        needs.clear();
        for (Need need : value) {
            if (needs.size() >= MAX_NEEDS) {
                break;
            }
            needs.add(need);
        }
    }

    /**
     * Goods the kingdom has more of than it needs, as the last turn saw them; other rulers look at them for deals. Not saved:
     * the next turn sees them again.
     */
    public List<String> spares() {
        return Collections.unmodifiableList(spares);
    }

    public void setSpares(List<String> value) {
        spares.clear();
        spares.addAll(value);
    }

    // ---------------------------------------------------------------- pending decisions

    public List<RulerDecision> pending() {
        return Collections.unmodifiableList(pending);
    }

    public void addPending(List<RulerDecision> decisions) {
        pending.addAll(decisions);
        while (pending.size() > MAX_PENDING) {
            pending.remove(0);
        }
    }

    /**
     * The next decision to carry out, taken out of the queue.
     */
    public Optional<RulerDecision> nextPending() {
        return pending.isEmpty() ? Optional.empty() : Optional.of(pending.remove(0));
    }

    public void clearPending() {
        pending.clear();
    }

    // ---------------------------------------------------------------- neighbours and players

    public Map<UUID, Relation> relations() {
        return Collections.unmodifiableMap(relations);
    }

    public int relation(UUID other) {
        Relation relation = relations.get(other);
        return relation == null ? 0 : relation.score();
    }

    /**
     * Something happened between the ruler and another kingdom.
     */
    public void relate(UUID other, Reason reason) {
        if (other == null) {
            return;
        }
        relations.computeIfAbsent(other, id -> new Relation()).add(reason, 1);
        trim(relations);
    }

    public void restoreRelation(UUID other, int score, Map<Reason, Integer> reasons) {
        relations.computeIfAbsent(other, id -> new Relation()).restore(score, reasons);
    }

    public void forgetRelation(UUID other) {
        relations.remove(other);
        players.remove(other);
    }

    /**
     * How the ruler remembers its dealings with a player: up with every fair deal, down with every unfair offer.
     */
    public int dealings(UUID player) {
        return players.getOrDefault(player, 0);
    }

    public Map<UUID, Integer> players() {
        return Collections.unmodifiableMap(players);
    }

    public void dealt(UUID player, int change) {
        if (player == null || change == 0) {
            return;
        }
        players.put(player, clamp(players.getOrDefault(player, 0) + change));
        if (players.size() > MAX_RELATIONS) {
            UUID weakest = null;
            int least = Integer.MAX_VALUE;
            for (Map.Entry<UUID, Integer> entry : players.entrySet()) {
                if (Math.abs(entry.getValue()) < least) {
                    least = Math.abs(entry.getValue());
                    weakest = entry.getKey();
                }
            }
            players.remove(weakest);
        }
    }

    /**
     * Feelings fade a little every day: relations and dealings move one step back towards nothing.
     */
    public void fade() {
        relations.values().forEach(relation -> relation.score -= Integer.signum(relation.score));
        players.replaceAll((id, value) -> value - Integer.signum(value));
        players.values().removeIf(value -> value == 0);
    }

    private static void trim(Map<UUID, Relation> map) {
        while (map.size() > MAX_RELATIONS) {
            UUID weakest = null;
            int least = Integer.MAX_VALUE;
            for (Map.Entry<UUID, Relation> entry : map.entrySet()) {
                if (Math.abs(entry.getValue().score()) < least) {
                    least = Math.abs(entry.getValue().score());
                    weakest = entry.getKey();
                }
            }
            map.remove(weakest);
        }
    }

    static int clamp(int value) {
        return Math.max(-MAX_RELATION, Math.min(MAX_RELATION, value));
    }

    /**
     * The subject the ruler named its honorary councillor, or {@code null}. The title is an honour alone: it gives no right in
     * the kingdom, its treasury, storage, market, trade, buildings, army or law.
     */
    public UUID councillor() {
        return councillor;
    }

    public void setCouncillor(UUID player) {
        councillor = player;
    }

    // ---------------------------------------------------------------- turns and hard times

    public int turns() {
        return turns;
    }

    public void countTurn() {
        turns++;
    }

    /**
     * The day the treasury became too poor, or -1 while it is not.
     */
    public long poorSince() {
        return poorSince;
    }

    public void setPoorSince(long day) {
        poorSince = day;
    }

    public void restore(int turnsTaken, long poor, long chronicle, int chronicled) {
        turns = Math.max(0, turnsTaken);
        poorSince = poor;
        chronicleDay = chronicle;
        chronicleCount = Math.max(0, chronicled);
    }

    public void restoreDecree(Decree decree) {
        if (decrees.size() < MAX_DECREES) {
            decrees.add(decree);
        }
    }
}
