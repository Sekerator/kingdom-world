package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A contract on the board of a branch of the guild. Its reward lies in escrow from the moment it is posted until it is
 * done (the taker gets it less the guild's commission, which goes to the branch's treasury), runs out or is called off
 * (it goes back to whoever posted it). Exams are contracts too: personal, without money, for the next rank.
 *
 * @param branch       the kingdom whose branch the contract was posted at; the reward is in its currency
 * @param posterId     the kingdom or the player who posted it; for the guild's own contracts the branch's kingdom, for an
 *                     exam the examinee
 * @param minRank      the lowest rank that may take it; an adventurer takes contracts of the own rank and one above
 * @param target       a monster id for hunts and bounties (empty for hunts by rank), an item id for gathering
 * @param targetRank   the lowest rank of monsters that count for hunts by rank and exams; the rank of the monster otherwise
 * @param boss         whether only bosses count (exams for S and above)
 * @param count        monsters to kill or items to hand in
 * @param progress     monsters killed so far
 * @param currency     the currency of the reward
 * @param reward       the money in escrow; 0 for exams
 * @param points       the guild points the taker gets
 * @param commissionPermille the share of the reward the branch keeps, fixed when the contract was posted
 * @param player       the player who took it, or {@code null}
 * @param squad        the squad that took it, or 0
 * @param squadsAllowed whether squads of citizens may take it (always for kingdoms and the guild, by choice for players)
 */
public record Contract(long id, Kind kind, UUID branch, Poster poster, UUID posterId, Rank minRank, String target, Rank targetRank,
                       boolean boss, int count, int progress, UUID currency, long reward, int points, int commissionPermille, long posted,
                       long deadline, Status status, UUID player, long squad, boolean squadsAllowed) {
    /** Most monsters or items one contract asks for. */
    public static final int MAX_COUNT = 4_096;
    /** Longest target id. */
    public static final int MAX_TARGET = 256;

    /**
     * What a contract asks for.
     */
    public enum Kind {
        /** Kill monsters of a kind, or of a rank and above. */
        HUNT,
        /** Hand in items at a desk of the guild. */
        GATHER,
        /** Kill one strong monster of a kind. */
        BOUNTY,
        /** The exam for the next rank. */
        EXAM,
        /** Guard a caravan; comes with the roads and caravans of a later stage. */
        ESCORT,
        /** Fight in a war; comes with wars. */
        WAR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Whether players and kingdoms may post contracts of this kind now.
         */
        public boolean postable() {
            return this == HUNT || this == GATHER || this == BOUNTY;
        }

        /**
         * Whether kills of monsters make progress.
         */
        public boolean byKills() {
            return this == HUNT || this == BOUNTY || this == EXAM;
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
     * Who posted a contract.
     */
    public enum Poster {
        KINGDOM,
        PLAYER,
        GUILD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Poster> byId(String id) {
            for (Poster poster : values()) {
                if (poster.id().equals(id)) {
                    return Optional.of(poster);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Where a contract stands. Closed contracts leave the board; their status only travels in messages.
     */
    public enum Status {
        OPEN,
        TAKEN,
        DONE,
        EXPIRED,
        CANCELLED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    public Contract {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(poster, "poster");
        Objects.requireNonNull(posterId, "posterId");
        Objects.requireNonNull(minRank, "minRank");
        Objects.requireNonNull(targetRank, "targetRank");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        target = target == null ? "" : target;
        if (id < 1 || target.length() > MAX_TARGET || count < 1 || count > MAX_COUNT || progress < 0 || !Money.isAmount(reward)
                || points < 0 || commissionPermille < 0 || commissionPermille > 1000 || squad < 0) {
            throw new IllegalArgumentException("Contract out of range: #" + id);
        }
        if (kind == Kind.GATHER && target.isEmpty()) {
            throw new IllegalArgumentException("A gathering contract needs an item: #" + id);
        }
        if (status == Status.TAKEN && player == null && squad == 0) {
            throw new IllegalArgumentException("A taken contract needs a taker: #" + id);
        }
        if (status == Status.OPEN && (player != null || squad != 0)) {
            throw new IllegalArgumentException("An open contract has no taker: #" + id);
        }
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isExam() {
        return kind == Kind.EXAM;
    }

    public boolean takenBy(UUID who) {
        return status == Status.TAKEN && who != null && who.equals(player);
    }

    /**
     * Whether the contract counts monsters by their rank rather than their kind.
     */
    public boolean byRank() {
        return kind.byKills() && target.isEmpty();
    }

    /**
     * Whether a killed monster counts for this contract.
     */
    public boolean counts(String monster, Rank rank, boolean isBoss) {
        if (!kind.byKills()) {
            return false;
        }
        if (boss && !isBoss) {
            return false;
        }
        if (!target.isEmpty()) {
            return target.equals(monster);
        }
        return rank.atLeast(targetRank);
    }

    public boolean complete() {
        return progress >= count;
    }

    /**
     * The guild's share of the reward.
     */
    public long commission() {
        return Money.share(reward, commissionPermille, 1000);
    }

    /**
     * What the taker gets: the reward less the commission.
     */
    public long payout() {
        return reward - commission();
    }

    public Contract withProgress(int value) {
        return new Contract(id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, Math.max(0, Math.min(count, value)),
                currency, reward, points, commissionPermille, posted, deadline, status, player, squad, squadsAllowed);
    }

    public Contract takenByPlayer(UUID who) {
        return new Contract(id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, progress, currency, reward, points,
                commissionPermille, posted, deadline, Status.TAKEN, who, 0, squadsAllowed);
    }

    public Contract takenBySquad(long squadId) {
        return new Contract(id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, progress, currency, reward, points,
                commissionPermille, posted, deadline, Status.TAKEN, null, squadId, squadsAllowed);
    }

    /**
     * The contract back on the board without a taker; the next taker starts from nothing, so that nobody finishes what
     * another gave up.
     */
    public Contract reopened() {
        return new Contract(id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, 0, currency, reward, points,
                commissionPermille, posted, deadline, Status.OPEN, null, 0, squadsAllowed);
    }

    /**
     * The contract with a closing status, for messages; closed contracts are not kept.
     */
    public Contract closed(Status value) {
        return new Contract(id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, progress, currency, reward, points,
                commissionPermille, posted, deadline, value, value == Status.OPEN ? null : player, value == Status.OPEN ? 0 : squad,
                squadsAllowed);
    }
}
