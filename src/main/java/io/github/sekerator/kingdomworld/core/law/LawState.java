package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The law of every kingdom: who is wanted where and for what, goods the guards confiscated, the reputation of players in
 * each kingdom, passes into cities and what each court decided. Pure logic; the server applies fines, executions and
 * reports around it.
 */
public final class LawState {
    /** Most wanted players one kingdom keeps on record. */
    public static final int MAX_WANTED = 512;

    /**
     * A crime written into the record.
     *
     * @param appeared whether the player was not wanted before
     */
    public record Committed(Wanted wanted, boolean appeared) {
    }

    private final Map<UUID, Map<UUID, Wanted>> wanted = new LinkedHashMap<>();
    private final Map<Long, Confiscation> confiscations = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> reputation = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Long>> tradeProgress = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Long>> passes = new LinkedHashMap<>();
    private final Map<UUID, KingdomLaw> laws = new LinkedHashMap<>();
    private final Set<UUID> autoPay = new LinkedHashSet<>();
    /** Heads a hunter collected of one player: hunter, then the player, then how many times. */
    private final Map<UUID, Map<UUID, Integer>> captures = new LinkedHashMap<>();
    private long nextConfiscation = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Optional<Wanted> wanted(UUID kingdomId, UUID player) {
        Map<UUID, Wanted> of = wanted.get(kingdomId);
        return of == null ? Optional.empty() : Optional.ofNullable(of.get(player));
    }

    /**
     * Everybody a kingdom wants, murderers first, then by the time they became wanted.
     */
    public List<Wanted> wantedIn(UUID kingdomId) {
        Map<UUID, Wanted> of = wanted.get(kingdomId);
        if (of == null) {
            return List.of();
        }
        List<Wanted> result = new ArrayList<>(of.values());
        result.sort(Comparator.comparing((Wanted w) -> !w.executable()).thenComparingLong(Wanted::since));
        return result;
    }

    /**
     * Every kingdom that wants a player.
     */
    public List<Wanted> wantedOf(UUID player) {
        List<Wanted> result = new ArrayList<>();
        for (Map<UUID, Wanted> of : wanted.values()) {
            Wanted entry = of.get(player);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public Collection<Wanted> allWanted() {
        List<Wanted> result = new ArrayList<>();
        wanted.values().forEach(of -> result.addAll(of.values()));
        return result;
    }

    public KingdomLaw law(UUID kingdomId) {
        return laws.getOrDefault(kingdomId, KingdomLaw.DEFAULT);
    }

    public Map<UUID, KingdomLaw> laws() {
        return Collections.unmodifiableMap(laws);
    }

    public boolean slaveryBanned(UUID kingdomId) {
        return law(kingdomId).slaveryBanned();
    }

    public int reputation(UUID kingdomId, UUID player) {
        Map<UUID, Integer> of = reputation.get(kingdomId);
        return of == null ? 0 : of.getOrDefault(player, 0);
    }

    /**
     * Reputation of a player in every kingdom where it is not neutral.
     */
    public Map<UUID, Integer> reputationOf(UUID player) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        reputation.forEach((kingdom, of) -> {
            Integer value = of.get(player);
            if (value != null && value != 0) {
                result.put(kingdom, value);
            }
        });
        return result;
    }

    public Map<UUID, Map<UUID, Integer>> reputations() {
        return Collections.unmodifiableMap(reputation);
    }

    public Map<UUID, Map<UUID, Long>> tradeProgress() {
        return Collections.unmodifiableMap(tradeProgress);
    }

    public Map<UUID, Map<UUID, Long>> passes() {
        return Collections.unmodifiableMap(passes);
    }

    public Optional<Confiscation> confiscation(long id) {
        return Optional.ofNullable(confiscations.get(id));
    }

    public Collection<Confiscation> confiscations() {
        return Collections.unmodifiableCollection(confiscations.values());
    }

    public List<Confiscation> confiscationsOf(UUID owner) {
        List<Confiscation> result = new ArrayList<>();
        for (Confiscation confiscation : confiscations.values()) {
            if (confiscation.owner().equals(owner)) {
                result.add(confiscation);
            }
        }
        return result;
    }

    public List<Confiscation> confiscationsIn(UUID kingdomId) {
        List<Confiscation> result = new ArrayList<>();
        for (Confiscation confiscation : confiscations.values()) {
            if (confiscation.kingdomId().equals(kingdomId)) {
                result.add(confiscation);
            }
        }
        return result;
    }

    public long nextConfiscationId() {
        return nextConfiscation;
    }

    public boolean autoPay(UUID player) {
        return autoPay.contains(player);
    }

    public Set<UUID> autoPayers() {
        return Collections.unmodifiableSet(autoPay);
    }

    // ---------------------------------------------------------------- crimes

    /**
     * Writes a crime into the record of the kingdom and lowers the player's reputation there.
     *
     * @param worth  what was stolen, for a theft
     * @param victim who suffered, for the record
     */
    public Committed commit(UUID kingdomId, UUID player, String name, CrimeKind kind, long worth, String victim, long now, LawRules rules) {
        Map<UUID, Wanted> of = wanted.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>());
        Wanted entry = of.get(player);
        boolean appeared = entry == null;
        if (appeared) {
            if (of.size() >= MAX_WANTED) {
                // The record is full: the oldest petty case gives way, so that the newest crimes are kept.
                of.values().stream().filter(w -> !w.murder()).min(Comparator.comparingLong(Wanted::since))
                        .ifPresent(oldest -> of.remove(oldest.player()));
            }
            entry = new Wanted(kingdomId, player, name, now);
            of.put(player, entry);
        } else {
            entry.setName(name);
        }
        boolean merged = entry.add(kind, worth, victim, now, rules.mergeTicks);
        if (!merged) {
            // One crime costs reputation once, however many acts it took.
            int drop = switch (kind) {
                case THEFT -> rules.theftReputation;
                case ASSAULT -> rules.assaultReputation;
                case MURDER -> rules.murderReputation;
                case TRESPASS -> rules.trespassReputation;
                case VANDALISM -> rules.vandalismReputation;
            };
            changeReputation(kingdomId, player, drop);
        }
        changed();
        return new Committed(entry, appeared);
    }

    /**
     * Forgets petty crimes that are old enough: sooner for a player of good reputation in that kingdom. A murder stays.
     *
     * @return the records that are now empty and were removed
     */
    public List<Wanted> decay(long now, LawRules rules) {
        List<Wanted> cleared = new ArrayList<>();
        boolean any = false;
        for (Map<UUID, Wanted> of : wanted.values()) {
            for (Wanted entry : List.copyOf(of.values())) {
                long ticks = rules.decayTicks(reputation(entry.kingdomId(), entry.player()));
                if (entry.forgetPettyBefore(now - ticks)) {
                    any = true;
                    if (entry.isEmpty()) {
                        of.remove(entry.player());
                        cleared.add(entry);
                    }
                }
            }
        }
        wanted.values().removeIf(Map::isEmpty);
        if (any) {
            changed();
        }
        return cleared;
    }

    /**
     * The fine was paid: the petty crimes (and a murder the court spared) are off the record.
     *
     * @return the record before, if there was one
     */
    public Optional<Wanted> settle(UUID kingdomId, UUID player) {
        Optional<Wanted> entry = wanted(kingdomId, player);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        entry.get().clearPetty();
        if (entry.get().isEmpty()) {
            remove(kingdomId, player);
        }
        changed();
        return entry;
    }

    /**
     * The player was executed or pardoned: the whole record is gone.
     */
    public Optional<Wanted> clear(UUID kingdomId, UUID player) {
        Optional<Wanted> entry = wanted(kingdomId, player);
        if (entry.isPresent()) {
            remove(kingdomId, player);
            changed();
        }
        return entry;
    }

    private void remove(UUID kingdomId, UUID player) {
        Map<UUID, Wanted> of = wanted.get(kingdomId);
        if (of != null) {
            of.remove(player);
            if (of.isEmpty()) {
                wanted.remove(kingdomId);
            }
        }
    }

    public void setBounty(UUID kingdomId, UUID player, long contract) {
        wanted(kingdomId, player).ifPresent(entry -> {
            if (entry.bounty() != contract) {
                entry.setBounty(contract);
                changed();
            }
        });
    }

    // ---------------------------------------------------------------- the court

    private static Outcome checkCourt(UUID actor, UUID kingdomId, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !world.membership().hasPermission(actor, kingdomId, Permission.MANAGE_LAW)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    public Outcome checkPardon(UUID actor, UUID kingdomId, UUID player, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        return wanted(kingdomId, player).isPresent() ? Outcome.DONE : Outcome.fail(KingdomError.NOT_WANTED);
    }

    /**
     * The king pardons a player: the record is gone.
     *
     * @param actor a member with {@link Permission#MANAGE_LAW}; {@code null} for an operator
     */
    public Outcome pardon(UUID actor, UUID kingdomId, UUID player, KingdomWorldState world) {
        Outcome check = checkPardon(actor, kingdomId, player, world);
        if (!check.ok()) {
            return check;
        }
        clear(kingdomId, player);
        return Outcome.DONE;
    }

    /**
     * The court lowers the fine of a player, or replaces the death of a murderer with a fine.
     *
     * @param fine the new fine; for a petty case no more than it was
     */
    public Outcome setFine(UUID actor, UUID kingdomId, UUID player, long fine, LawRules rules, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        Optional<Wanted> entry = wanted(kingdomId, player);
        if (entry.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_WANTED);
        }
        if (!Money.isAmount(fine)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Wanted record = entry.get();
        if (record.executable()) {
            record.setCommuted(true);
        } else if (actor != null && fine > record.fine(rules, law(kingdomId).fineMultiplierPermille())) {
            return Outcome.fail(KingdomError.FINE_TOO_HIGH);
        }
        record.setFineOverride(fine);
        changed();
        return Outcome.DONE;
    }

    public Outcome setMultiplier(UUID actor, UUID kingdomId, int permille, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (permille < KingdomLaw.MIN_MULTIPLIER || permille > KingdomLaw.MAX_MULTIPLIER) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        putLaw(kingdomId, law(kingdomId).withMultiplier(permille));
        return Outcome.DONE;
    }

    public Outcome setEntryFee(UUID actor, UUID kingdomId, long fee, LawRules rules, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (fee < 0 || fee > rules.maxEntryFee) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        putLaw(kingdomId, law(kingdomId).withEntryFee(fee));
        return Outcome.DONE;
    }

    public Outcome setBountyReward(UUID actor, UUID kingdomId, long reward, LawRules rules, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (reward < -1 || reward > rules.maxBounty) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        putLaw(kingdomId, law(kingdomId).withBounty(reward));
        return Outcome.DONE;
    }

    public Outcome setSlaveryBanned(UUID actor, UUID kingdomId, boolean banned, KingdomWorldState world) {
        Outcome check = checkCourt(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        putLaw(kingdomId, law(kingdomId).withSlaveryBanned(banned));
        return Outcome.DONE;
    }

    private void putLaw(UUID kingdomId, KingdomLaw value) {
        if (value.isDefault()) {
            laws.remove(kingdomId);
        } else {
            laws.put(kingdomId, value);
        }
        changed();
    }

    // ---------------------------------------------------------------- reputation

    /**
     * Moves the reputation of a player in a kingdom, within -100 and 100.
     */
    public void changeReputation(UUID kingdomId, UUID player, int delta) {
        if (delta == 0) {
            return;
        }
        int next = Reputation.clamp((long) reputation(kingdomId, player) + delta);
        setReputation(kingdomId, player, next);
    }

    public void setReputation(UUID kingdomId, UUID player, int value) {
        int next = Reputation.clamp(value);
        Map<UUID, Integer> of = reputation.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>());
        Integer before = next == 0 ? of.remove(player) : of.put(player, next);
        if (of.isEmpty()) {
            reputation.remove(kingdomId);
        }
        if (before == null ? next != 0 : before != next) {
            changed();
        }
    }

    /**
     * Money a player traded with a kingdom (bought from or sold to its market, its subjects' lots): every
     * {@link LawRules#tradePerPoint} wins a point of reputation.
     *
     * @return the points won
     */
    public int traded(UUID kingdomId, UUID player, long money, LawRules rules) {
        if (money <= 0) {
            return 0;
        }
        Map<UUID, Long> of = tradeProgress.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>());
        long sum = Money.addCapped(of.getOrDefault(player, 0L), money);
        int points = (int) Math.min(Reputation.MAX * 2L, sum / rules.tradePerPoint);
        long rest = sum - points * rules.tradePerPoint;
        if (rest <= 0) {
            of.remove(player);
        } else {
            of.put(player, rest);
        }
        if (of.isEmpty()) {
            tradeProgress.remove(kingdomId);
        }
        if (points > 0) {
            changeReputation(kingdomId, player, points);
        }
        changed();
        return points;
    }

    // ---------------------------------------------------------------- confiscated goods

    /**
     * Writes down goods the guards took. The goods themselves are kept by the caller under the returned id.
     */
    public Confiscation confiscate(UUID kingdomId, String kingdomName, UUID owner, String ownerName, long worth, int items, long now, long day) {
        Confiscation confiscation = new Confiscation(nextConfiscation++, kingdomId, kingdomName, owner, ownerName, now, day,
                Math.max(0, Math.min(Money.MAX, worth)), Math.max(0, items));
        confiscations.put(confiscation.id(), confiscation);
        changed();
        return confiscation;
    }

    /**
     * Whether the owner may buy goods back.
     */
    public Outcome checkRansom(UUID owner, long id) {
        Confiscation confiscation = confiscations.get(id);
        if (confiscation == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CONFISCATION);
        }
        if (!confiscation.owner().equals(owner)) {
            return Outcome.fail(KingdomError.NOT_YOUR_CONFISCATION);
        }
        return Outcome.DONE;
    }

    /**
     * The goods leave the record: bought back, sent to the storehouses or given back.
     */
    public Optional<Confiscation> release(long id) {
        Confiscation confiscation = confiscations.remove(id);
        if (confiscation != null) {
            changed();
        }
        return Optional.ofNullable(confiscation);
    }

    /**
     * Goods nobody claimed in time.
     */
    public List<Confiscation> expired(long today, LawRules rules) {
        List<Confiscation> result = new ArrayList<>();
        for (Confiscation confiscation : confiscations.values()) {
            if (confiscation.expired(today, rules)) {
                result.add(confiscation);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- passes into cities

    public boolean hasPass(UUID kingdomId, UUID player, long now) {
        Map<UUID, Long> of = passes.get(kingdomId);
        Long until = of == null ? null : of.get(player);
        return until != null && until > now;
    }

    public long passUntil(UUID kingdomId, UUID player) {
        Map<UUID, Long> of = passes.get(kingdomId);
        Long until = of == null ? null : of.get(player);
        return until == null ? 0 : until;
    }

    /**
     * Passes of a player that still hold, by kingdom.
     */
    public Map<UUID, Long> passesOf(UUID player, long now) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        passes.forEach((kingdom, of) -> {
            Long until = of.get(player);
            if (until != null && until > now) {
                result.put(kingdom, until);
            }
        });
        return result;
    }

    /**
     * Gives a pass that holds in every city of the kingdom for a game day from now.
     */
    public void grantPass(UUID kingdomId, UUID player, long now, LawRules rules) {
        passes.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>()).put(player, now + rules.passTicks);
        changed();
    }

    public boolean revokePass(UUID kingdomId, UUID player) {
        Map<UUID, Long> of = passes.get(kingdomId);
        if (of == null || of.remove(player) == null) {
            return false;
        }
        if (of.isEmpty()) {
            passes.remove(kingdomId);
        }
        changed();
        return true;
    }

    /**
     * Forgets passes that ran out.
     */
    public void expirePasses(long now) {
        boolean any = false;
        for (Map<UUID, Long> of : passes.values()) {
            any |= of.values().removeIf(until -> until <= now);
        }
        passes.values().removeIf(Map::isEmpty);
        if (any) {
            changed();
        }
    }

    public void setAutoPay(UUID player, boolean on) {
        if (on ? autoPay.add(player) : autoPay.remove(player)) {
            changed();
        }
    }

    // ---------------------------------------------------------------- heads collected

    /**
     * How many times a hunter collected the head of a player.
     */
    public int captures(UUID hunter, UUID player) {
        Map<UUID, Integer> of = captures.get(hunter);
        return of == null ? 0 : of.getOrDefault(player, 0);
    }

    /**
     * Every count of heads collected: hunter, then player.
     */
    public Map<UUID, Map<UUID, Integer>> allCaptures() {
        return Collections.unmodifiableMap(captures);
    }

    /**
     * A hunter collected the head of a player once more.
     */
    public void countCapture(UUID hunter, UUID player) {
        captures.computeIfAbsent(hunter, key -> new LinkedHashMap<>()).merge(player, 1, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        changed();
    }

    public void restoreCaptures(UUID hunter, UUID player, int count) {
        if (count > 0) {
            captures.computeIfAbsent(hunter, key -> new LinkedHashMap<>()).put(player, count);
        }
    }

    // ---------------------------------------------------------------- kingdoms

    /**
     * A kingdom is gone: its records, court, passes and reputations go with it. Confiscated goods stay for their owners,
     * who take them back for free.
     */
    public void forgetKingdom(UUID kingdomId) {
        boolean any = wanted.remove(kingdomId) != null;
        any |= reputation.remove(kingdomId) != null;
        any |= tradeProgress.remove(kingdomId) != null;
        any |= passes.remove(kingdomId) != null;
        any |= laws.remove(kingdomId) != null;
        if (any) {
            changed();
        }
    }

    // ---------------------------------------------------------------- saved data

    public void restoreWanted(Wanted entry, List<Crime> crimes, long since, long fineOverride, boolean commuted, long bounty) {
        crimes.forEach(entry::restore);
        entry.restore(since, fineOverride, commuted, bounty);
        if (entry.isEmpty()) {
            return;
        }
        Map<UUID, Wanted> of = wanted.computeIfAbsent(entry.kingdomId(), k -> new LinkedHashMap<>());
        if (of.putIfAbsent(entry.player(), entry) != null) {
            throw new IllegalArgumentException("Two records of " + entry.player() + " in " + entry.kingdomId());
        }
    }

    public void restoreConfiscation(Confiscation confiscation) {
        if (confiscations.putIfAbsent(confiscation.id(), confiscation) != null) {
            throw new IllegalArgumentException("Two confiscations #" + confiscation.id());
        }
        nextConfiscation = Math.max(nextConfiscation, confiscation.id() + 1);
    }

    public void restoreNextConfiscation(long id) {
        nextConfiscation = Math.max(nextConfiscation, id);
    }

    public void restoreReputation(UUID kingdomId, UUID player, int value) {
        if (value != 0) {
            reputation.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>()).put(player, Reputation.clamp(value));
        }
    }

    public void restoreTrade(UUID kingdomId, UUID player, long money) {
        if (money > 0) {
            tradeProgress.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>()).put(player, money);
        }
    }

    public void restorePass(UUID kingdomId, UUID player, long until) {
        passes.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>()).put(player, until);
    }

    public void restoreLaw(UUID kingdomId, KingdomLaw law) {
        if (!law.isDefault()) {
            laws.put(kingdomId, law);
        }
    }

    public void restoreAutoPay(UUID player) {
        autoPay.add(player);
    }

    public void clear() {
        wanted.clear();
        confiscations.clear();
        reputation.clear();
        tradeProgress.clear();
        passes.clear();
        laws.clear();
        autoPay.clear();
        captures.clear();
        nextConfiscation = 1;
    }
}
