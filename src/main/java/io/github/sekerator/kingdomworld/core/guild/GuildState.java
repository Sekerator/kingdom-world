package io.github.sekerator.kingdomworld.core.guild;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The adventurers' guild of one world: its members and their ranks, the adventurers among the citizens, the branches in
 * the kingdoms, the contracts on the boards with the money in their escrow, and the squads.
 *
 * <p>Money moves only between the books of the kingdoms ({@link Economy}) and the escrow of contracts: posting takes the
 * reward from a treasury or an account, finishing pays it out less the commission (which goes to the branch's
 * treasury), running out or calling off gives it back. Every operation checks first and then changes everything it
 * changes, or refuses with a reason and changes nothing. Exams are personal contracts without money.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class GuildState {
    private final Map<UUID, GuildMember> members = new LinkedHashMap<>();
    private final Map<UUID, Hireling> hirelings = new LinkedHashMap<>();
    private final Map<UUID, Branch> branches = new LinkedHashMap<>();
    private final Map<Long, Contract> contracts = new LinkedHashMap<>();
    private final Map<Long, Squad> squads = new LinkedHashMap<>();
    private long nextContract = 1;
    private long nextSquad = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Optional<GuildMember> member(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    public Collection<GuildMember> members() {
        return Collections.unmodifiableCollection(members.values());
    }

    public Optional<Hireling> hireling(UUID citizenId) {
        return Optional.ofNullable(hirelings.get(citizenId));
    }

    public Collection<Hireling> hirelings() {
        return Collections.unmodifiableCollection(hirelings.values());
    }

    public Optional<Branch> branch(UUID kingdomId) {
        return Optional.ofNullable(branches.get(kingdomId));
    }

    /**
     * The branch of a kingdom, or the settings a new one starts with.
     */
    public Branch branchOrDefault(UUID kingdomId, GuildRules rules) {
        Branch branch = branches.get(kingdomId);
        return branch == null ? Branch.open(kingdomId, rules) : branch;
    }

    public Collection<Branch> branches() {
        return Collections.unmodifiableCollection(branches.values());
    }

    public Optional<Contract> contract(long id) {
        return Optional.ofNullable(contracts.get(id));
    }

    public Collection<Contract> contracts() {
        return Collections.unmodifiableCollection(contracts.values());
    }

    /**
     * The contracts on the board of a branch, without exams.
     */
    public List<Contract> board(UUID branch) {
        List<Contract> result = new ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.branch().equals(branch) && !contract.isExam()) {
                result.add(contract);
            }
        }
        return result;
    }

    /**
     * The contracts a player holds, the exam included.
     */
    public List<Contract> takenBy(UUID player) {
        List<Contract> result = new ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.takenBy(player)) {
                result.add(contract);
            }
        }
        return result;
    }

    public Optional<Contract> exam(UUID player) {
        for (Contract contract : contracts.values()) {
            if (contract.isExam() && contract.takenBy(player)) {
                return Optional.of(contract);
            }
        }
        return Optional.empty();
    }

    /**
     * Contracts a player posted that are still on a board.
     */
    public int postedBy(UUID player) {
        int count = 0;
        for (Contract contract : contracts.values()) {
            if (contract.poster() == Contract.Poster.PLAYER && contract.posterId().equals(player)) {
                count++;
            }
        }
        return count;
    }

    public Optional<Squad> squad(long id) {
        return Optional.ofNullable(squads.get(id));
    }

    public Collection<Squad> squads() {
        return Collections.unmodifiableCollection(squads.values());
    }

    public List<Squad> squadsOf(UUID kingdomId) {
        List<Squad> result = new ArrayList<>();
        for (Squad squad : squads.values()) {
            if (squad.kingdomId().equals(kingdomId)) {
                result.add(squad);
            }
        }
        return result;
    }

    public Optional<Squad> squadOf(UUID citizenId) {
        for (Squad squad : squads.values()) {
            if (squad.members().contains(citizenId)) {
                return Optional.of(squad);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a citizen is away on an expedition, so that it has no body.
     */
    public boolean isAway(UUID citizenId) {
        return squadOf(citizenId).map(Squad::away).orElse(false);
    }

    /**
     * Money of a currency in the escrow of contracts.
     */
    public long held(UUID currency) {
        long sum = 0;
        for (Contract contract : contracts.values()) {
            if (contract.currency().equals(currency)) {
                sum = Money.addCapped(sum, contract.reward());
            }
        }
        return sum;
    }

    public long nextContractId() {
        return nextContract;
    }

    public long nextSquadId() {
        return nextSquad;
    }

    // ---------------------------------------------------------------- joining and points

    /**
     * A player joins the guild at a branch, paying the fee of the config to the treasury of its kingdom.
     */
    public Outcome join(UUID player, UUID branch, long now, long day, GuildRules rules, KingdomWorldState world) {
        if (members.containsKey(player)) {
            return Outcome.fail(KingdomError.ALREADY_IN_GUILD);
        }
        if (world.kingdoms().get(branch).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (rules.joinFee > 0) {
            Outcome paid = world.economy().payTreasury(player, branch, rules.joinFee, LedgerCategory.GUILD, day);
            if (!paid.ok()) {
                return paid;
            }
        }
        members.put(player, GuildMember.joined(player, now));
        changed();
        return Outcome.DONE;
    }

    /**
     * Adds points to a member, or takes them away for a negative amount; nothing for a player outside the guild.
     */
    public Optional<GuildMember> addPoints(UUID player, long amount) {
        GuildMember member = members.get(player);
        if (member == null) {
            return Optional.empty();
        }
        GuildMember next = member.plusPoints(amount);
        members.put(player, next);
        changed();
        return Optional.of(next);
    }

    /**
     * A member killed a monster that gives points.
     */
    public Optional<GuildMember> recordKillPoints(UUID player, long points) {
        GuildMember member = members.get(player);
        if (member == null) {
            return Optional.empty();
        }
        GuildMember next = member.plusPoints(points).plusKill();
        members.put(player, next);
        changed();
        return Optional.of(next);
    }

    /**
     * An operator gives a player a rank, joining them to the guild if needed.
     */
    public GuildMember setRank(UUID player, Rank rank, long now) {
        GuildMember member = members.getOrDefault(player, GuildMember.joined(player, now)).withRank(rank);
        members.put(player, member);
        changed();
        return member;
    }

    // ---------------------------------------------------------------- exams

    /**
     * Whether a member may start the exam for the next rank now.
     */
    public Outcome checkExam(UUID player, long now, GuildRules rules) {
        GuildMember member = members.get(player);
        if (member == null) {
            return Outcome.fail(KingdomError.NOT_IN_GUILD);
        }
        Optional<Rank> next = member.rank().next();
        if (next.isEmpty()) {
            return Outcome.fail(KingdomError.TOP_RANK);
        }
        if (exam(player).isPresent()) {
            return Outcome.fail(KingdomError.EXAM_UNDER_WAY);
        }
        if (member.points() < rules.rankPoints(next.get())) {
            return Outcome.fail(KingdomError.TOO_FEW_POINTS);
        }
        if (now < member.examCooldownEnd()) {
            return Outcome.fail(KingdomError.EXAM_COOLDOWN);
        }
        return Outcome.DONE;
    }

    /**
     * The result of starting something that makes a contract.
     */
    public record Made(Outcome outcome, Optional<Contract> contract) {
        static Made failed(Outcome outcome) {
            return new Made(outcome, Optional.empty());
        }
    }

    /**
     * Starts the exam for the next rank: monsters of that rank or above (for S and above one boss) within the time of the
     * config.
     */
    public Made startExam(UUID player, UUID branch, long now, GuildRules rules) {
        Outcome check = checkExam(player, now, rules);
        if (!check.ok()) {
            return Made.failed(check);
        }
        GuildMember member = members.get(player);
        Rank next = member.rank().next().orElseThrow();
        Contract exam = new Contract(nextContract++, Contract.Kind.EXAM, branch, Contract.Poster.GUILD, player, member.rank(), "", next,
                rules.examNeedsBoss(next), rules.examKills(next), 0, branch, 0, 0, 0, now, now + rules.examTicks, Contract.Status.TAKEN,
                player, 0, false);
        contracts.put(exam.id(), exam);
        changed();
        return new Made(Outcome.DONE, Optional.of(exam));
    }

    private void failExam(Contract exam, long now, GuildRules rules) {
        contracts.remove(exam.id());
        GuildMember member = members.get(exam.player());
        if (member != null) {
            members.put(member.player(), member.withExamCooldown(now + rules.examCooldownTicks));
        }
        changed();
    }

    // ---------------------------------------------------------------- posting

    /**
     * A contract to post.
     *
     * @param poster     who posts it and pays
     * @param posterId   the player, or the kingdom (which must be the branch's)
     * @param target     a monster id, empty for a hunt by rank, or an item id
     * @param targetRank the lowest rank of monsters for a hunt by rank; the rank of the monster otherwise
     * @param days       how long it runs, in game days
     */
    public record Posting(UUID branch, Contract.Poster poster, UUID posterId, Contract.Kind kind, String target, Rank targetRank, int count,
                          long reward, Rank minRank, int days, boolean squadsAllowed) {
        public Posting {
            Objects.requireNonNull(branch, "branch");
            Objects.requireNonNull(poster, "poster");
            Objects.requireNonNull(posterId, "posterId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(targetRank, "targetRank");
            Objects.requireNonNull(minRank, "minRank");
            target = target == null ? "" : target;
        }
    }

    /**
     * The guild points a contract gives: the points of its monsters, three times for a bounty, a little for gathering.
     */
    public static int pointsFor(Contract.Kind kind, Rank rank, int count, GuildRules rules) {
        long base = rules.killPoints(rank);
        long points = switch (kind) {
            case HUNT -> base * Math.max(1, count);
            case BOUNTY -> base * 3L * Math.max(1, count);
            case GATHER -> base * Math.max(1, (count + 15) / 16);
            case OUTLAW -> base * 5L;
            default -> 0;
        };
        return (int) Math.min(Integer.MAX_VALUE, points);
    }

    /**
     * What a player pays the branch for posting a contract with a reward.
     */
    public static long postingFee(long reward, GuildRules rules) {
        if (rules.postingFeePermille <= 0) {
            return 0;
        }
        return Math.max(1, Money.share(reward, rules.postingFeePermille, 1000));
    }

    private int openAt(UUID branch) {
        int count = 0;
        for (Contract contract : contracts.values()) {
            if (contract.branch().equals(branch) && !contract.isExam()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a contract could be posted now, and what the player pays for posting it.
     *
     * @param actor the player posting, who for a kingdom needs {@link Permission#MANAGE_GUILD}; {@code null} for an operator or
     *              the guild itself
     */
    public Outcome checkPost(UUID actor, Posting posting, long day, GuildRules rules, KingdomWorldState world) {
        Economy economy = world.economy();
        if (world.kingdoms().get(posting.branch()).isEmpty() || economy.treasury(posting.branch()).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!posting.kind().postable() || posting.target().length() > Contract.MAX_TARGET || posting.count() < 1
                || posting.count() > Contract.MAX_COUNT || !Money.isPayment(posting.reward()) || posting.days() < 1
                || posting.days() > rules.maxContractDays) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if ((posting.kind() == Contract.Kind.GATHER || posting.kind() == Contract.Kind.BOUNTY) && posting.target().isEmpty()) {
            return Outcome.fail(posting.kind() == Contract.Kind.GATHER ? KingdomError.BAD_ITEM : KingdomError.UNKNOWN_MONSTER);
        }
        if (openAt(posting.branch()) >= rules.maxContractsPerBranch) {
            return Outcome.fail(KingdomError.BOARD_FULL);
        }
        switch (posting.poster()) {
            case KINGDOM -> {
                if (!posting.posterId().equals(posting.branch())) {
                    return Outcome.fail(KingdomError.NOT_PARTNER);
                }
                if (actor != null && !world.membership().hasPermission(actor, posting.branch(), Permission.MANAGE_GUILD)) {
                    return Outcome.fail(KingdomError.NO_PERMISSION);
                }
                return economy.checkHoldFromTreasury(posting.branch(), posting.reward());
            }
            case GUILD -> {
                if (!posting.posterId().equals(posting.branch())) {
                    return Outcome.fail(KingdomError.NOT_PARTNER);
                }
                if (branchOrDefault(posting.branch(), rules).budgetLeft(day) < posting.reward()) {
                    return Outcome.fail(KingdomError.BUDGET_SPENT);
                }
                return economy.checkHoldFromTreasury(posting.branch(), posting.reward());
            }
            case PLAYER -> {
                if (actor != null && !actor.equals(posting.posterId())) {
                    return Outcome.fail(KingdomError.NO_PERMISSION);
                }
                if (postedBy(posting.posterId()) >= rules.maxPostedPerPlayer) {
                    return Outcome.fail(KingdomError.TOO_MANY_CONTRACTS);
                }
                long fee = postingFee(posting.reward(), rules);
                java.util.OptionalLong total = Money.add(posting.reward(), fee);
                if (total.isEmpty()) {
                    return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
                }
                Outcome hold = economy.checkHoldFromAccount(posting.posterId(), posting.branch(), total.getAsLong());
                if (!hold.ok()) {
                    return hold;
                }
                return fee == 0 ? Outcome.DONE : economy.checkPayTreasury(posting.posterId(), posting.branch(), fee);
            }
            default -> {
                return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
            }
        }
    }

    /**
     * Posts a contract: the reward goes into escrow, a player also pays the posting fee to the branch.
     */
    public Made post(UUID actor, Posting posting, long now, long day, GuildRules rules, KingdomWorldState world) {
        Outcome check = checkPost(actor, posting, day, rules, world);
        if (!check.ok()) {
            return Made.failed(check);
        }
        Economy economy = world.economy();
        Branch branch = branchOrDefault(posting.branch(), rules);
        Outcome held;
        if (posting.poster() == Contract.Poster.PLAYER) {
            long fee = postingFee(posting.reward(), rules);
            held = economy.holdFromAccount(posting.posterId(), posting.branch(), posting.reward());
            if (held.ok() && fee > 0) {
                Outcome paid = economy.payTreasury(posting.posterId(), posting.branch(), fee, LedgerCategory.GUILD, day);
                if (!paid.ok()) {
                    economy.releaseToAccount(posting.posterId(), posting.branch(), posting.reward());
                    return Made.failed(paid);
                }
            }
        } else {
            held = economy.holdFromTreasury(posting.branch(), posting.reward(), LedgerCategory.GUILD, day);
        }
        if (!held.ok()) {
            return Made.failed(held);
        }
        if (posting.poster() == Contract.Poster.GUILD) {
            branch = branch.spent(day, posting.reward()).withLastAuto(now);
        }
        branches.put(posting.branch(), branch);
        boolean squadsAllowed = posting.poster() != Contract.Poster.PLAYER || posting.squadsAllowed();
        boolean boss = false;
        Contract contract = new Contract(nextContract++, posting.kind(), posting.branch(), posting.poster(), posting.posterId(),
                posting.minRank(), posting.target(), posting.targetRank(), boss, posting.count(), 0, posting.branch(), posting.reward(),
                pointsFor(posting.kind(), posting.targetRank(), posting.count(), rules), branch.commissionPermille(), now,
                now + posting.days() * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY, Contract.Status.OPEN, null, 0,
                squadsAllowed);
        contracts.put(contract.id(), contract);
        changed();
        return new Made(Outcome.DONE, Optional.of(contract));
    }

    // ---------------------------------------------------------------- heads of criminals

    /**
     * The law of a kingdom posts a contract for the head of a wanted murderer at the kingdom's branch: the reward goes from
     * the treasury into escrow. Squads of citizens never take it; any member of the guild may.
     *
     * @param target the player's id and name, {@code id|name}
     */
    public Made postOutlaw(UUID branch, String target, long reward, int days, long now, long day, GuildRules rules, KingdomWorldState world) {
        Economy economy = world.economy();
        if (world.kingdoms().get(branch).isEmpty() || economy.treasury(branch).isEmpty()) {
            return Made.failed(Outcome.fail(KingdomError.UNKNOWN_KINGDOM));
        }
        if (target == null || target.isEmpty() || target.length() > Contract.MAX_TARGET || !Money.isPayment(reward) || days < 1) {
            return Made.failed(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
        }
        Outcome held = economy.holdFromTreasury(branch, reward, LedgerCategory.GUILD, day);
        if (!held.ok()) {
            return Made.failed(held);
        }
        Branch office = branchOrDefault(branch, rules);
        branches.put(branch, office);
        Contract contract = new Contract(nextContract++, Contract.Kind.OUTLAW, branch, Contract.Poster.KINGDOM, branch, Rank.F, target, Rank.C,
                false, 1, 0, branch, reward, pointsFor(Contract.Kind.OUTLAW, Rank.C, 1, rules), office.commissionPermille(), now,
                now + days * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY, Contract.Status.OPEN, null, 0, false);
        contracts.put(contract.id(), contract);
        changed();
        return new Made(Outcome.DONE, Optional.of(contract));
    }

    /**
     * A member of the guild killed the wanted player of a contract for a head: the contract is theirs, whoever held it, and
     * done; the reward is paid as for any contract.
     */
    public Optional<Progress> claimOutlaw(long id, UUID killer, long day, KingdomWorldState world, Sharing sharing) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.kind() != Contract.Kind.OUTLAW || !members.containsKey(killer)) {
            return Optional.empty();
        }
        Contract taken = contract.takenByPlayer(killer);
        contracts.put(id, taken);
        return Optional.of(finishForPlayer(taken, day, world, sharing));
    }

    /**
     * The law no longer wants the head (the player paid, was pardoned or executed by the guards): the reward goes back to
     * the treasury.
     */
    public Optional<Contract> withdrawOutlaw(long id, long day, KingdomWorldState world) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.kind() != Contract.Kind.OUTLAW) {
            return Optional.empty();
        }
        contracts.remove(id);
        refund(contract, day, world);
        changed();
        return Optional.of(contract.closed(Contract.Status.CANCELLED));
    }

    // ---------------------------------------------------------------- taking, abandoning, calling off

    /**
     * Whether a member may take a contract: open, not their own, of their rank or one above, within their limit.
     */
    public Outcome checkTake(UUID player, long id, long now, GuildRules rules) {
        GuildMember member = members.get(player);
        if (member == null) {
            return Outcome.fail(KingdomError.NOT_IN_GUILD);
        }
        Contract contract = contracts.get(id);
        if (contract == null || contract.isExam() || contract.deadline() <= now) {
            return Outcome.fail(KingdomError.UNKNOWN_CONTRACT);
        }
        if (!contract.isOpen()) {
            return Outcome.fail(KingdomError.CONTRACT_TAKEN);
        }
        if (contract.poster() == Contract.Poster.PLAYER && contract.posterId().equals(player)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (contract.minRank().ordinal() > member.rank().ordinal() + 1) {
            return Outcome.fail(KingdomError.RANK_TOO_LOW);
        }
        long held = takenBy(player).stream().filter(c -> !c.isExam()).count();
        if (held >= rules.maxActiveContracts) {
            return Outcome.fail(KingdomError.TOO_MANY_CONTRACTS);
        }
        return Outcome.DONE;
    }

    public Outcome take(UUID player, long id, long now, GuildRules rules) {
        Outcome check = checkTake(player, id, now, rules);
        if (check.ok()) {
            contracts.put(id, contracts.get(id).takenByPlayer(player));
            changed();
        }
        return check;
    }

    /**
     * A member gives a contract up: it goes back to the board and the member loses points. Giving up an exam fails it.
     *
     * @return the points lost
     */
    public long abandon(UUID player, long id, long now, GuildRules rules) {
        Contract contract = contracts.get(id);
        if (contract == null || !contract.takenBy(player)) {
            return -1;
        }
        if (contract.isExam()) {
            failExam(contract, now, rules);
            return 0;
        }
        long penalty = penalty(contract, rules);
        contracts.put(id, contract.reopened());
        addPoints(player, -penalty);
        changed();
        return penalty;
    }

    /**
     * Points a taker loses when a contract fails.
     */
    public static long penalty(Contract contract, GuildRules rules) {
        return (long) contract.points() * rules.penaltyPercent / 100;
    }

    /**
     * Whether the actor may call a contract off: the player who posted it, or for a kingdom's and the guild's own contracts a
     * member with {@link Permission#MANAGE_GUILD}; only while nobody holds it.
     */
    public Outcome checkCancel(UUID actor, long id, KingdomWorldState world) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.isExam()) {
            return Outcome.fail(KingdomError.UNKNOWN_CONTRACT);
        }
        if (actor != null) {
            boolean allowed = contract.poster() == Contract.Poster.PLAYER ? contract.posterId().equals(actor)
                    : world.membership().hasPermission(actor, contract.branch(), Permission.MANAGE_GUILD);
            if (!allowed) {
                return Outcome.fail(KingdomError.NOT_YOUR_CONTRACT);
            }
        }
        if (!contract.isOpen()) {
            return Outcome.fail(KingdomError.CONTRACT_TAKEN);
        }
        return Outcome.DONE;
    }

    /**
     * Calls a contract off; the reward goes back to whoever posted it.
     */
    public Optional<Contract> cancel(UUID actor, long id, long day, KingdomWorldState world) {
        if (!checkCancel(actor, id, world).ok()) {
            return Optional.empty();
        }
        Contract contract = contracts.remove(id);
        refund(contract, day, world);
        changed();
        return Optional.of(contract.closed(Contract.Status.CANCELLED));
    }

    /**
     * The reward of a contract goes back to whoever posted it. A kingdom that is gone takes its money with it.
     */
    private static void refund(Contract contract, long day, KingdomWorldState world) {
        if (contract.reward() <= 0) {
            return;
        }
        Economy economy = world.economy();
        if (contract.poster() == Contract.Poster.PLAYER) {
            economy.releaseToAccount(contract.posterId(), contract.currency(), contract.reward());
        } else if (economy.canReleaseToTreasury(contract.posterId(), contract.currency(), contract.reward())) {
            economy.releaseToTreasury(contract.posterId(), contract.currency(), contract.reward(), LedgerCategory.GUILD, day);
        }
    }

    // ---------------------------------------------------------------- progress and the end of contracts

    /**
     * Contracts shared by a group of players, such as a party: a kill or a hand-in of any of them moves the contract, and the
     * reward and the points are split among them. The guild only asks; who shares what is kept elsewhere.
     */
    public interface Sharing {
        /** Nobody shares anything: every contract belongs to its taker alone. */
        Sharing NONE = new Sharing() {
            @Override
            public Set<Long> sharedWith(UUID player) {
                return Set.of();
            }

            @Override
            public List<RewardSplit.Share> shares(Contract contract) {
                return List.of();
            }
        };

        /**
         * The contracts a player works on with others, held by the player or by another.
         */
        Set<Long> sharedWith(UUID player);

        /**
         * Who gets what part of a finished contract; empty when the taker keeps everything.
         */
        List<RewardSplit.Share> shares(Contract contract);
    }

    /**
     * What one person got for a finished contract.
     */
    public record Payment(UUID player, long money, long points) {
    }

    /**
     * What a kill or a hand-in did to a contract.
     *
     * @param contract the contract after the change; closed with {@link Contract.Status#DONE} when it was finished
     * @param paid     what the taker got
     * @param payments what everybody got when the contract was shared, the taker first; empty otherwise
     */
    public record Progress(Contract contract, boolean finished, long paid, List<Payment> payments) {
        public Progress {
            payments = List.copyOf(payments);
        }

        public Progress(Contract contract, boolean finished, long paid) {
            this(contract, finished, paid, List.of());
        }
    }

    /**
     * A member killed a monster: every contract they hold that counts it moves on, and those that are complete are
     * finished and paid.
     */
    public List<Progress> recordKill(UUID player, String monster, Rank rank, boolean boss, long now, long day, KingdomWorldState world) {
        return recordKill(player, monster, rank, boss, now, day, world, Sharing.NONE);
    }

    /**
     * A member killed a monster: every contract they hold or share that counts it moves on, and those that are complete are
     * finished and paid (split among those who share them).
     */
    public List<Progress> recordKill(UUID player, String monster, Rank rank, boolean boss, long now, long day, KingdomWorldState world,
                                     Sharing sharing) {
        List<Progress> result = new ArrayList<>();
        for (Contract contract : List.copyOf(contracts.values())) {
            boolean mine = contract.takenBy(player) || isShared(contract, player, sharing);
            if (!mine || contract.deadline() <= now || !contract.counts(monster, rank, boss)) {
                continue;
            }
            Contract moved = contract.withProgress(contract.progress() + 1);
            contracts.put(moved.id(), moved);
            if (moved.complete()) {
                result.add(finishForPlayer(moved, day, world, sharing));
            } else {
                result.add(new Progress(moved, false, 0));
            }
        }
        if (!result.isEmpty()) {
            changed();
        }
        return result;
    }

    /**
     * Whether a contract held by another is shared with a player: taken by a player, not an exam, and named by the sharing.
     */
    public static boolean isShared(Contract contract, UUID player, Sharing sharing) {
        return contract.status() == Contract.Status.TAKEN && contract.player() != null && !contract.isExam()
                && sharing.sharedWith(player).contains(contract.id());
    }

    /**
     * Finishes a contract a player holds: an exam raises the rank, any other pays the reward less the commission onto the
     * player's account (or the accounts of those who share it, by their shares) and the commission to the branch, and gives
     * the points the same way.
     */
    private Progress finishForPlayer(Contract contract, long day, KingdomWorldState world, Sharing sharing) {
        contracts.remove(contract.id());
        UUID player = contract.player();
        GuildMember member = members.get(player);
        if (contract.isExam()) {
            if (member != null) {
                members.put(player, member.withRank(Rank.max(member.rank(), contract.targetRank())));
            }
            changed();
            return new Progress(contract.closed(Contract.Status.DONE), true, 0);
        }
        Economy economy = world.economy();
        long commission = contract.commission();
        long payout = contract.payout();
        if (!economy.canReleaseToTreasury(contract.branch(), contract.currency(), commission)) {
            // The branch cannot take its share: the taker gets everything.
            payout = contract.reward();
            commission = 0;
        }
        List<RewardSplit.Share> all = sharing.shares(contract);
        List<UUID> people = new ArrayList<>();
        List<Integer> weightList = new ArrayList<>();
        people.add(player);
        weightList.add(0);
        for (RewardSplit.Share share : all) {
            if (share.player().equals(player)) {
                weightList.set(0, share.weight());
            } else if (!people.contains(share.player())) {
                people.add(share.player());
                weightList.add(share.weight());
            }
        }
        if (people.size() == 1) {
            if (economy.canReleaseToAccount(player, contract.currency(), payout)) {
                economy.releaseToAccount(player, contract.currency(), payout);
                economy.releaseToTreasury(contract.branch(), contract.currency(), commission, LedgerCategory.GUILD, day);
                economy.repayFromIncome(player, contract.currency(), payout, day);
            } else {
                // An account that cannot take more: the money goes back to whoever posted the contract.
                refund(contract, day, world);
                payout = 0;
            }
            if (member != null) {
                members.put(player, member.plusPoints(contract.points()).plusContract());
            }
            changed();
            return new Progress(contract.closed(Contract.Status.DONE), true, payout);
        }
        // Shared: the taker first, then the others, each by the share; the parts add up to the whole.
        int[] weights = weightList.stream().mapToInt(Integer::intValue).toArray();
        long[] money = RewardSplit.split(payout, weights);
        long[] points = RewardSplit.split(contract.points(), weights);
        // A part an account cannot take goes to the taker; if the taker cannot take it either, everything goes back.
        for (int i = 1; i < money.length; i++) {
            if (!economy.canReleaseToAccount(people.get(i), contract.currency(), money[i])) {
                money[0] += money[i];
                money[i] = 0;
            }
        }
        if (economy.canReleaseToAccount(player, contract.currency(), money[0])) {
            for (int i = 0; i < money.length; i++) {
                economy.releaseToAccount(people.get(i), contract.currency(), money[i]);
            }
            economy.releaseToTreasury(contract.branch(), contract.currency(), commission, LedgerCategory.GUILD, day);
            for (int i = 0; i < money.length; i++) {
                economy.repayFromIncome(people.get(i), contract.currency(), money[i], day);
            }
        } else {
            refund(contract, day, world);
            java.util.Arrays.fill(money, 0);
        }
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) {
            GuildMember someone = members.get(people.get(i));
            if (someone != null) {
                members.put(someone.player(), someone.plusPoints(points[i]).plusContract());
            }
            payments.add(new Payment(people.get(i), money[i], points[i]));
        }
        changed();
        return new Progress(contract.closed(Contract.Status.DONE), true, money[0], payments);
    }

    /**
     * Whether a member could hand in the items of a gathering contract now.
     *
     * @param have how many of the items the player carries
     */
    public Outcome checkHandIn(UUID player, long id, int have, long now) {
        return checkHandIn(player, id, have, now, Sharing.NONE);
    }

    /**
     * Whether a member could hand in items for a gathering contract now: alone, everything that is missing at once; in a
     * group that shares the contract, any part of it.
     *
     * @param have how many of the items the player carries
     */
    public Outcome checkHandIn(UUID player, long id, int have, long now, Sharing sharing) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.deadline() <= now) {
            return Outcome.fail(KingdomError.UNKNOWN_CONTRACT);
        }
        boolean shared = !sharing.shares(contract).isEmpty() && (contract.takenBy(player) || isShared(contract, player, sharing));
        if (!contract.takenBy(player) && !shared) {
            return Outcome.fail(KingdomError.NOT_YOUR_CONTRACT);
        }
        if (contract.kind() != Contract.Kind.GATHER) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        int missing = contract.count() - contract.progress();
        return have >= missing || shared && have > 0 ? Outcome.DONE : Outcome.fail(KingdomError.NO_ITEMS);
    }

    /**
     * The items of a gathering contract were handed in (the caller took them from the player in the same moment): the
     * contract is finished and paid.
     */
    public Optional<Progress> handIn(UUID player, long id, int handed, long now, long day, KingdomWorldState world) {
        return handIn(player, id, handed, now, day, world, Sharing.NONE);
    }

    /**
     * Items of a gathering contract were handed in (the caller took them from the player in the same moment, no more than
     * are missing): the contract moves on, and when nothing is missing any more it is finished and paid.
     */
    public Optional<Progress> handIn(UUID player, long id, int handed, long now, long day, KingdomWorldState world, Sharing sharing) {
        if (!checkHandIn(player, id, handed, now, sharing).ok()) {
            return Optional.empty();
        }
        Contract contract = contracts.get(id);
        Contract moved = contract.withProgress(contract.progress() + handed);
        if (!moved.complete()) {
            contracts.put(id, moved);
            changed();
            return Optional.of(new Progress(moved, false, 0));
        }
        return Optional.of(finishForPlayer(moved, day, world, sharing));
    }

    /**
     * Contracts whose time ran out: rewards go back to whoever posted them, takers lose points, exams fail. Contracts
     * of squads still away wait for the squad.
     *
     * @return the contracts that ran out, closed
     */
    public List<Contract> expire(long now, long day, GuildRules rules, KingdomWorldState world) {
        List<Contract> result = new ArrayList<>();
        for (Contract contract : List.copyOf(contracts.values())) {
            if (contract.deadline() > now) {
                continue;
            }
            if (contract.squad() != 0 && squad(contract.squad()).map(Squad::away).orElse(false)) {
                continue;
            }
            if (contract.isExam()) {
                failExam(contract, now, rules);
            } else {
                contracts.remove(contract.id());
                refund(contract, day, world);
                if (contract.player() != null) {
                    addPoints(contract.player(), -penalty(contract, rules));
                }
            }
            result.add(contract.closed(Contract.Status.EXPIRED));
        }
        if (!result.isEmpty()) {
            changed();
        }
        return result;
    }

    /**
     * An operator makes a contract run out at {@code now}; the next {@link #expire} ends it.
     */
    public boolean runOut(long id, long now) {
        Contract contract = contracts.get(id);
        if (contract == null || contract.deadline() <= now) {
            return contract != null;
        }
        contracts.put(id, new Contract(contract.id(), contract.kind(), contract.branch(), contract.poster(), contract.posterId(),
                contract.minRank(), contract.target(), contract.targetRank(), contract.boss(), contract.count(), contract.progress(),
                contract.currency(), contract.reward(), contract.points(), contract.commissionPermille(), contract.posted(), now,
                contract.status(), contract.player(), contract.squad(), contract.squadsAllowed()));
        changed();
        return true;
    }

    // ---------------------------------------------------------------- branches

    private Outcome checkBranch(UUID actor, UUID kingdomId, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !world.membership().hasPermission(actor, kingdomId, Permission.MANAGE_GUILD)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    public Outcome setCommission(UUID actor, UUID kingdomId, int permille, GuildRules rules, KingdomWorldState world) {
        Outcome check = checkBranch(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (permille < rules.minCommissionPermille || permille > rules.maxCommissionPermille) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Branch branch = branchOrDefault(kingdomId, rules);
        if (branch.commissionPermille() == permille && branches.containsKey(kingdomId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        branches.put(kingdomId, branch.withCommission(permille));
        changed();
        return Outcome.DONE;
    }

    public Outcome setBudget(UUID actor, UUID kingdomId, long amount, GuildRules rules, KingdomWorldState world) {
        Outcome check = checkBranch(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (amount < 0 || amount > rules.maxDailyBudget) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Branch branch = branchOrDefault(kingdomId, rules);
        if (branch.dailyBudget() == amount && branches.containsKey(kingdomId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        branches.put(kingdomId, branch.withBudget(amount));
        changed();
        return Outcome.DONE;
    }

    /**
     * Remembers that the guild looked for contracts of its own at a branch, so that it does not look again too soon.
     */
    public void markAutoLook(UUID kingdomId, long now, GuildRules rules) {
        branches.put(kingdomId, branchOrDefault(kingdomId, rules).withLastAuto(now));
        changed();
    }

    /**
     * Contracts of the guild itself open at a branch.
     */
    public int autoContracts(UUID branch) {
        int count = 0;
        for (Contract contract : contracts.values()) {
            if (contract.branch().equals(branch) && contract.poster() == Contract.Poster.GUILD && !contract.isExam()) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- adventurers among the citizens

    /**
     * The guild's record of a citizen adventurer, made when first needed.
     */
    public Hireling hireling(UUID citizenId, UUID kingdomId) {
        Hireling hireling = hirelings.get(citizenId);
        if (hireling == null) {
            hireling = Hireling.recruit(citizenId, kingdomId);
            hirelings.put(citizenId, hireling);
            changed();
        }
        return hireling;
    }

    /**
     * A citizen adventurer earned points and kills.
     */
    public Hireling rewardHireling(UUID citizenId, UUID kingdomId, long points, int kills, int expeditions, GuildRules rules) {
        Hireling next = hireling(citizenId, kingdomId).earned(points, kills, expeditions, rules);
        hirelings.put(citizenId, next);
        changed();
        return next;
    }

    public void setHirelingRank(UUID citizenId, UUID kingdomId, Rank rank) {
        hirelings.put(citizenId, hireling(citizenId, kingdomId).withRank(rank));
        changed();
    }

    /**
     * Forgets a citizen who died or is no adventurer any more; a squad at home loses the member.
     */
    public void forgetHireling(UUID citizenId) {
        boolean changedAny = hirelings.remove(citizenId) != null;
        for (Squad squad : List.copyOf(squads.values())) {
            if (squad.members().contains(citizenId) && !squad.away()) {
                List<UUID> rest = new ArrayList<>(squad.members());
                rest.remove(citizenId);
                if (rest.isEmpty()) {
                    squads.remove(squad.id());
                } else {
                    squads.put(squad.id(), squad.withMembers(rest));
                }
                changedAny = true;
            }
        }
        if (changedAny) {
            changed();
        }
    }

    // ---------------------------------------------------------------- squads

    /**
     * The result of forming a squad.
     */
    public record Formed(Outcome outcome, Optional<Squad> squad) {
    }

    /**
     * Forms a squad of adventurers of one kingdom, each in no other squad.
     *
     * @param actor a member with {@link Permission#MANAGE_GUILD}; {@code null} for an operator
     */
    public Formed formSquad(UUID actor, UUID kingdomId, List<UUID> citizens, GuildRules rules, KingdomWorldState world) {
        Outcome check = checkBranch(actor, kingdomId, world);
        if (!check.ok()) {
            return new Formed(check, Optional.empty());
        }
        Set<UUID> unique = new HashSet<>(citizens);
        if (unique.size() != citizens.size() || citizens.size() < rules.minSquad || citizens.size() > rules.maxSquad) {
            return new Formed(Outcome.fail(KingdomError.SQUAD_SIZE), Optional.empty());
        }
        for (UUID id : citizens) {
            Optional<Citizen> citizen = world.citizens().get(id);
            if (citizen.isEmpty() || !citizen.get().kingdomId().equals(kingdomId)
                    || !citizen.get().professionId().equals(Professions.ADVENTURER.id()) || squadOf(id).isPresent()) {
                return new Formed(Outcome.fail(KingdomError.NOT_AN_ADVENTURER), Optional.empty());
            }
        }
        for (UUID id : citizens) {
            hireling(id, kingdomId);
        }
        Squad squad = new Squad(nextSquad++, kingdomId, citizens, Squad.State.HOME, 0, 0, 0, 0);
        squads.put(squad.id(), squad);
        changed();
        return new Formed(Outcome.DONE, Optional.of(squad));
    }

    public Outcome disband(UUID actor, long squadId, KingdomWorldState world) {
        Squad squad = squads.get(squadId);
        if (squad == null) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkBranch(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (squad.away()) {
            return Outcome.fail(KingdomError.SQUAD_AWAY);
        }
        squads.remove(squadId);
        changed();
        return Outcome.DONE;
    }

    /**
     * The rank of a squad: that of its best member.
     */
    public Rank squadRank(Squad squad) {
        List<Rank> ranks = new ArrayList<>();
        for (UUID member : squad.members()) {
            ranks.add(hireling(member).map(Hireling::rank).orElse(Rank.F));
        }
        return Expeditions.rank(ranks);
    }

    /**
     * Whether a squad may go to a contract now: at home, everybody healthy, a contract of its own kingdom's branch that it
     * may take (the kingdom's, the guild's, or a player's who lets squads take it), of its rank or one above, and back
     * before the contract runs out.
     */
    public Outcome checkSend(UUID actor, long squadId, long contractId, long now, GuildRules rules, KingdomWorldState world) {
        Squad squad = squads.get(squadId);
        if (squad == null) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkBranch(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (squad.away()) {
            return Outcome.fail(KingdomError.SQUAD_AWAY);
        }
        for (UUID id : squad.members()) {
            Optional<Citizen> citizen = world.citizens().get(id);
            if (citizen.isEmpty() || !citizen.get().professionId().equals(Professions.ADVENTURER.id())) {
                return Outcome.fail(KingdomError.NOT_AN_ADVENTURER);
            }
            if (citizen.get().state() != CitizenState.HEALTHY) {
                return Outcome.fail(KingdomError.SQUAD_WOUNDED);
            }
        }
        Contract contract = contracts.get(contractId);
        if (contract == null || contract.isExam() || contract.deadline() <= now || !contract.branch().equals(squad.kingdomId())) {
            return Outcome.fail(KingdomError.UNKNOWN_CONTRACT);
        }
        if (!contract.isOpen()) {
            return Outcome.fail(KingdomError.CONTRACT_TAKEN);
        }
        if (!contract.kind().byKills() || !contract.squadsAllowed()) {
            return Outcome.fail(KingdomError.SQUADS_NOT_ALLOWED);
        }
        if (contract.minRank().ordinal() > squadRank(squad).ordinal() + 1) {
            return Outcome.fail(KingdomError.RANK_TOO_LOW);
        }
        if (now + Expeditions.duration(contract, rules) > contract.deadline()) {
            return Outcome.fail(KingdomError.NO_TIME);
        }
        return Outcome.DONE;
    }

    /**
     * Sends a squad to a contract; it takes the contract and is away until it comes back.
     *
     * @param power the strength of the squad as it leaves
     */
    public Optional<Squad> send(UUID actor, long squadId, long contractId, double power, long now, GuildRules rules, KingdomWorldState world) {
        if (!checkSend(actor, squadId, contractId, now, rules, world).ok()) {
            return Optional.empty();
        }
        Contract contract = contracts.get(contractId);
        Squad squad = squads.get(squadId).departing(contractId, now, now + Expeditions.duration(contract, rules), power);
        squads.put(squadId, squad);
        contracts.put(contractId, contract.takenBySquad(squadId));
        changed();
        return Optional.of(squad);
    }

    /**
     * What came of an expedition for the books.
     *
     * @param contract the contract after the expedition: done, back on the board, or gone if it ran out meanwhile
     * @param paid     what the kingdom of the squad got
     */
    public record Return(Squad squad, Contract contract, long paid) {
    }

    /**
     * A squad came back: a finished contract pays the squad's kingdom (less the commission for the branch), anything else
     * goes back to the board with the kills made, or runs out. The members earn points; the dead leave the squad. The
     * caller writes the deaths into the chronicle and brings the loot to the storehouses.
     */
    public Optional<Return> finishExpedition(long squadId, Expeditions.Outcome outcome, Set<UUID> dead, long now, long day, GuildRules rules,
                                             KingdomWorldState world) {
        Squad squad = squads.get(squadId);
        if (squad == null || !squad.away()) {
            return Optional.empty();
        }
        Contract contract = contracts.get(squad.contractId());
        long paid = 0;
        Contract after;
        if (contract == null) {
            after = null;
        } else {
            Contract moved = contract.withProgress(contract.progress() + outcome.kills());
            if (outcome.result() == Expeditions.Result.SUCCESS || moved.complete()) {
                contracts.remove(contract.id());
                Economy economy = world.economy();
                long commission = moved.commission();
                long payout = moved.payout();
                if (!economy.canReleaseToTreasury(moved.branch(), moved.currency(), commission)) {
                    payout = moved.reward();
                    commission = 0;
                }
                if (economy.canReleaseToTreasury(squad.kingdomId(), moved.currency(), payout)) {
                    economy.releaseToTreasury(squad.kingdomId(), moved.currency(), payout, LedgerCategory.GUILD, day);
                    economy.releaseToTreasury(moved.branch(), moved.currency(), commission, LedgerCategory.GUILD, day);
                    paid = payout;
                } else {
                    refund(moved, day, world);
                }
                after = moved.closed(Contract.Status.DONE);
            } else if (moved.deadline() <= now) {
                contracts.remove(contract.id());
                refund(moved, day, world);
                after = moved.closed(Contract.Status.EXPIRED);
            } else {
                after = moved.reopened();
                contracts.put(after.id(), after);
            }
        }
        long share = contract == null ? 0 : (long) contract.points() * Math.max(0, outcome.kills())
                / Math.max(1, contract.count() - contract.progress());
        for (UUID member : squad.members()) {
            if (!dead.contains(member)) {
                rewardHireling(member, squad.kingdomId(), share, outcome.kills(), 1, rules);
            }
        }
        List<UUID> alive = new ArrayList<>(squad.members());
        alive.removeAll(dead);
        for (UUID member : dead) {
            hirelings.remove(member);
        }
        Squad home = squad.home();
        if (alive.isEmpty()) {
            squads.remove(squadId);
        } else {
            home = home.withMembers(alive);
            squads.put(squadId, home);
        }
        changed();
        return Optional.of(new Return(home, after, paid));
    }

    // ---------------------------------------------------------------- kingdoms that are gone

    /**
     * A kingdom was dissolved: its branch closes, the contracts on its board are called off (the rewards go back to the
     * players who posted them; the kingdom's own money is gone with its treasury), its squads and adventurers are
     * forgotten. Call it before the kingdom's treasury is retired, so players get their money back in a currency that
     * still has a kingdom behind it.
     *
     * @return the contracts that were called off
     */
    public List<Contract> forgetKingdom(UUID kingdomId, long day, KingdomWorldState world) {
        List<Contract> closed = new ArrayList<>();
        for (Contract contract : List.copyOf(contracts.values())) {
            if (contract.branch().equals(kingdomId)) {
                contracts.remove(contract.id());
                refund(contract, day, world);
                closed.add(contract.closed(Contract.Status.CANCELLED));
            }
        }
        for (Squad squad : List.copyOf(squads.values())) {
            if (squad.kingdomId().equals(kingdomId)) {
                squads.remove(squad.id());
                for (Contract contract : List.copyOf(contracts.values())) {
                    if (contract.squad() == squad.id()) {
                        contracts.put(contract.id(), contract.reopened());
                    }
                }
            }
        }
        hirelings.values().removeIf(hireling -> hireling.kingdomId().equals(kingdomId));
        branches.remove(kingdomId);
        changed();
        return closed;
    }

    // ---------------------------------------------------------------- saved data

    public void restoreMember(GuildMember member) {
        if (members.putIfAbsent(member.player(), member) != null) {
            throw new IllegalArgumentException("Two guild members " + member.player());
        }
    }

    public void restoreHireling(Hireling hireling) {
        if (hirelings.putIfAbsent(hireling.citizenId(), hireling) != null) {
            throw new IllegalArgumentException("Two adventurers " + hireling.citizenId());
        }
    }

    public void restoreBranch(Branch branch) {
        if (branches.putIfAbsent(branch.kingdomId(), branch) != null) {
            throw new IllegalArgumentException("Two branches in " + branch.kingdomId());
        }
    }

    public void restoreContract(Contract contract) {
        if (contracts.putIfAbsent(contract.id(), contract) != null) {
            throw new IllegalArgumentException("Two contracts #" + contract.id());
        }
        nextContract = Math.max(nextContract, contract.id() + 1);
    }

    public void restoreSquad(Squad squad) {
        for (UUID member : squad.members()) {
            if (squadOf(member).isPresent()) {
                throw new IllegalArgumentException("Citizen " + member + " is in two squads");
            }
        }
        if (squads.putIfAbsent(squad.id(), squad) != null) {
            throw new IllegalArgumentException("Two squads #" + squad.id());
        }
        nextSquad = Math.max(nextSquad, squad.id() + 1);
    }

    public void restoreNextIds(long contract, long squad) {
        nextContract = Math.max(nextContract, contract);
        nextSquad = Math.max(nextSquad, squad);
    }

    /**
     * Forgets everything, for data that could not be read.
     */
    public void clear() {
        members.clear();
        hirelings.clear();
        branches.clear();
        contracts.clear();
        squads.clear();
        nextContract = 1;
        nextSquad = 1;
    }
}
