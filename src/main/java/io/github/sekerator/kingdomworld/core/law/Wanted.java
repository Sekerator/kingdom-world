package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A player wanted by one kingdom: the crimes on record and what the court made of them. Petty crimes cost a fine (twice
 * the worth for a theft); a murder costs the life of the murderer unless the court replaced it with a fine. Changed only
 * through {@link LawState}.
 */
public final class Wanted {
    /** Most crimes kept on one record; later acts add to the last crime of their kind. */
    public static final int MAX_CRIMES = 32;

    private final UUID kingdomId;
    private final UUID player;
    private String name;
    private final List<Crime> crimes = new ArrayList<>();
    private long since;
    private long fineOverride = -1;
    private boolean commuted;
    private long bounty;

    public Wanted(UUID kingdomId, UUID player, String name, long since) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.player = Objects.requireNonNull(player, "player");
        this.name = name == null ? "" : name;
        this.since = since;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    public List<Crime> crimes() {
        return Collections.unmodifiableList(crimes);
    }

    public long since() {
        return since;
    }

    /**
     * The fine the court set, or -1 when the fine follows the crimes.
     */
    public long fineOverride() {
        return fineOverride;
    }

    /**
     * Whether the court replaced the death of the murderer with a fine.
     */
    public boolean commuted() {
        return commuted;
    }

    /**
     * The guild contract for the head of this player, or 0.
     */
    public long bounty() {
        return bounty;
    }

    public boolean isEmpty() {
        return crimes.isEmpty();
    }

    public boolean murder() {
        for (Crime crime : crimes) {
            if (crime.kind() == CrimeKind.MURDER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the guards kill this player rather than beat a fine out of them.
     */
    public boolean executable() {
        return murder() && !commuted;
    }

    /**
     * What was stolen in all.
     */
    public long stolen() {
        long sum = 0;
        for (Crime crime : crimes) {
            if (crime.kind() == CrimeKind.THEFT) {
                sum = Money.addCapped(sum, crime.worth());
            }
        }
        return sum;
    }

    /**
     * The fine the player owes: what the court set, or twice the stolen goods, the fixed fines of other petty crimes and of a
     * murder the court spared, times the multiplier of the kingdom. A murder that was not commuted is not paid with money.
     */
    public long fine(LawRules rules, int multiplierPermille) {
        if (fineOverride >= 0) {
            return fineOverride;
        }
        long sum = 0;
        for (Crime crime : crimes) {
            long part = switch (crime.kind()) {
                case THEFT -> rules.theftFine(crime.worth());
                case ASSAULT -> Money.multiplyCapped(rules.assaultFine, crime.count());
                case TRESPASS -> rules.trespassFine;
                case MURDER -> commuted ? rules.murderFine : 0;
            };
            sum = Money.addCapped(sum, part);
        }
        if (sum == 0) {
            return 0;
        }
        return Math.max(1, Money.share(sum, Math.max(KingdomLaw.MIN_MULTIPLIER, multiplierPermille), 1000));
    }

    // ---------------------------------------------------------------- changes, through the state

    void setName(String value) {
        if (value != null && !value.isEmpty()) {
            name = value;
        }
    }

    /**
     * Adds a crime, or adds the act to a crime of the same kind (a theft, or blows at the same victim) that is fresh enough.
     * A new crime makes the court look at the case again: a fine it set no longer holds, and a new murder is not commuted.
     *
     * @return whether the act was added to a crime already on record
     */
    boolean add(CrimeKind kind, long worth, String victim, long now, int mergeTicks) {
        for (int i = crimes.size() - 1; i >= 0; i--) {
            Crime crime = crimes.get(i);
            boolean same = crime.kind() == kind && (kind == CrimeKind.THEFT || kind == CrimeKind.ASSAULT && crime.victim().equals(victim == null
                    ? "" : victim));
            if (same && now - crime.time() <= mergeTicks) {
                crimes.set(i, crime.and(worth, now));
                fineOverride = -1;
                return true;
            }
        }
        if (crimes.size() >= MAX_CRIMES) {
            for (int i = crimes.size() - 1; i >= 0; i--) {
                if (crimes.get(i).kind() == kind) {
                    crimes.set(i, crimes.get(i).and(worth, now));
                    fineOverride = -1;
                    if (kind == CrimeKind.MURDER) {
                        commuted = false;
                    }
                    return true;
                }
            }
            crimes.remove(0);
        }
        crimes.add(Crime.of(kind, worth, now, victim));
        fineOverride = -1;
        if (kind == CrimeKind.MURDER) {
            commuted = false;
        }
        return false;
    }

    /**
     * Forgets petty crimes older than the given time.
     *
     * @return whether anything was forgotten
     */
    boolean forgetPettyBefore(long time) {
        boolean changed = crimes.removeIf(crime -> crime.kind().petty() && crime.time() < time);
        if (changed && crimes.isEmpty()) {
            fineOverride = -1;
        }
        return changed;
    }

    void clearPetty() {
        crimes.removeIf(crime -> crime.kind().petty() || commuted && crime.kind() == CrimeKind.MURDER);
        fineOverride = -1;
        if (!murder()) {
            commuted = false;
        }
    }

    void setFineOverride(long value) {
        fineOverride = value;
    }

    void setCommuted(boolean value) {
        commuted = value;
    }

    void setBounty(long contract) {
        bounty = Math.max(0, contract);
    }

    /**
     * Puts a crime back while the data is read.
     */
    void restore(Crime crime) {
        crimes.add(Objects.requireNonNull(crime, "crime"));
    }

    void restore(long since, long fineOverride, boolean commuted, long bounty) {
        this.since = since;
        this.fineOverride = Math.max(-1, fineOverride);
        this.commuted = commuted;
        this.bounty = Math.max(0, bounty);
    }
}
