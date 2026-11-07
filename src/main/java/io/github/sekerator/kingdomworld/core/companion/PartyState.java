package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.RewardSplit;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The parties of players: who leads, who is in, who is invited, the shares and the contracts each party took. A player is
 * in one party at most. Every operation checks first and then changes everything it changes, or refuses with a reason and
 * changes nothing.
 *
 * <p>The contracts of a party are those its leader took while leading it; kills and hand-ins of every member count for
 * them, and the reward is split among the members (see {@link #sharing()}).
 */
public final class PartyState {
    /**
     * An invitation to a party.
     */
    public record Invite(long party, UUID player, UUID from, long expires) {
        public Invite {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(from, "from");
        }
    }

    private final Map<Long, Party> parties = new LinkedHashMap<>();
    private final List<Invite> invites = new ArrayList<>();
    private long nextParty = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Optional<Party> party(long id) {
        return Optional.ofNullable(parties.get(id));
    }

    public Collection<Party> parties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    public Optional<Party> partyOf(UUID player) {
        for (Party party : parties.values()) {
            if (party.has(player)) {
                return Optional.of(party);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether two players are in the same party.
     */
    public boolean together(UUID a, UUID b) {
        return a != null && b != null && partyOf(a).map(party -> party.has(b)).orElse(false);
    }

    /**
     * The invitations a player has that did not run out.
     */
    public List<Invite> invitesOf(UUID player, long now) {
        List<Invite> result = new ArrayList<>();
        for (Invite invite : invites) {
            if (invite.player().equals(player) && invite.expires() > now && parties.containsKey(invite.party())) {
                result.add(invite);
            }
        }
        return result;
    }

    /**
     * The invitations of a party that did not run out.
     */
    public List<Invite> invitesTo(long party, long now) {
        List<Invite> result = new ArrayList<>();
        for (Invite invite : invites) {
            if (invite.party() == party && invite.expires() > now) {
                result.add(invite);
            }
        }
        return result;
    }

    public List<Invite> invites() {
        return Collections.unmodifiableList(invites);
    }

    public long nextPartyId() {
        return nextParty;
    }

    // ---------------------------------------------------------------- forming

    /**
     * A member of the guild founds a party and leads it.
     *
     * @param inGuild whether the player is a member of the guild
     */
    public Optional<Party> create(UUID player, boolean inGuild) {
        if (!inGuild || partyOf(player).isPresent()) {
            return Optional.empty();
        }
        Party party = new Party(nextParty++, player);
        parties.put(party.id(), party);
        invites.removeIf(invite -> invite.player().equals(player));
        changed();
        return Optional.of(party);
    }

    public Outcome checkCreate(UUID player, boolean inGuild) {
        if (!inGuild) {
            return Outcome.fail(KingdomError.NOT_IN_GUILD);
        }
        return partyOf(player).isPresent() ? Outcome.fail(KingdomError.ALREADY_IN_PARTY) : Outcome.DONE;
    }

    /**
     * The leader invites a member of the guild; a party that has none yet is founded first.
     *
     * @param targetInGuild whether the invited player is a member of the guild
     */
    public Outcome invite(UUID actor, UUID target, boolean actorInGuild, boolean targetInGuild, long now, CompanionRules rules) {
        if (actor.equals(target)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (!actorInGuild || !targetInGuild) {
            return Outcome.fail(KingdomError.NOT_IN_GUILD);
        }
        Optional<Party> current = partyOf(actor);
        if (current.isPresent() && !current.get().isLeader(actor)) {
            return Outcome.fail(KingdomError.NOT_PARTY_LEADER);
        }
        if (partyOf(target).isPresent()) {
            return Outcome.fail(KingdomError.ALREADY_IN_PARTY);
        }
        if (current.isPresent() && current.get().members().size() >= rules.maxParty) {
            return Outcome.fail(KingdomError.PARTY_FULL);
        }
        Party party = current.orElseGet(() -> create(actor, true).orElseThrow());
        invites.removeIf(invite -> invite.party() == party.id() && invite.player().equals(target) || invite.expires() <= now);
        invites.add(new Invite(party.id(), target, actor, now + rules.inviteTicks));
        changed();
        return Outcome.DONE;
    }

    /**
     * A player accepts an invitation and joins the party.
     */
    public Outcome accept(UUID player, long partyId, long now, CompanionRules rules) {
        Party party = parties.get(partyId);
        boolean invited = invites.stream().anyMatch(invite -> invite.party() == partyId && invite.player().equals(player) && invite.expires() > now);
        if (party == null || !invited) {
            return Outcome.fail(KingdomError.NOT_INVITED);
        }
        if (partyOf(player).isPresent()) {
            return Outcome.fail(KingdomError.ALREADY_IN_PARTY);
        }
        if (party.members().size() >= rules.maxParty) {
            return Outcome.fail(KingdomError.PARTY_FULL);
        }
        party.add(player);
        invites.removeIf(invite -> invite.player().equals(player));
        changed();
        return Outcome.DONE;
    }

    public Outcome decline(UUID player, long partyId) {
        boolean removed = invites.removeIf(invite -> invite.party() == partyId && invite.player().equals(player));
        if (!removed) {
            return Outcome.fail(KingdomError.NOT_INVITED);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * A player leaves the party; a leader who leaves hands the lead to the member who joined first, and a party left empty
     * is gone. The contracts the player holds stay with the player and leave the party.
     *
     * @return the party after it, empty if it is gone
     */
    public Optional<Party> leave(UUID player) {
        Optional<Party> found = partyOf(player);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Party party = found.get();
        party.remove(player);
        if (party.members().isEmpty()) {
            disband(party.id());
            return Optional.empty();
        }
        changed();
        return Optional.of(party);
    }

    private void disband(long id) {
        parties.remove(id);
        invites.removeIf(invite -> invite.party() == id);
        changed();
    }

    public Outcome kick(UUID actor, UUID target) {
        Optional<Party> found = partyOf(actor);
        if (found.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_IN_PARTY);
        }
        Party party = found.get();
        if (!party.isLeader(actor)) {
            return Outcome.fail(KingdomError.NOT_PARTY_LEADER);
        }
        if (actor.equals(target)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (!party.has(target)) {
            boolean removed = invites.removeIf(invite -> invite.party() == party.id() && invite.player().equals(target));
            if (removed) {
                changed();
                return Outcome.DONE;
            }
            return Outcome.fail(KingdomError.NOT_IN_PARTY);
        }
        party.remove(target);
        changed();
        return Outcome.DONE;
    }

    /**
     * The leader hands the lead to another member.
     */
    public Outcome transfer(UUID actor, UUID target) {
        Optional<Party> found = partyOf(actor);
        if (found.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_IN_PARTY);
        }
        Party party = found.get();
        if (!party.isLeader(actor)) {
            return Outcome.fail(KingdomError.NOT_PARTY_LEADER);
        }
        if (actor.equals(target)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (!party.has(target)) {
            return Outcome.fail(KingdomError.NOT_IN_PARTY);
        }
        party.setLeader(target);
        changed();
        return Outcome.DONE;
    }

    private Outcome leaderChange(UUID actor, java.util.function.Function<Party, Outcome> change) {
        Optional<Party> found = partyOf(actor);
        if (found.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_IN_PARTY);
        }
        if (!found.get().isLeader(actor)) {
            return Outcome.fail(KingdomError.NOT_PARTY_LEADER);
        }
        Outcome outcome = change.apply(found.get());
        if (outcome.ok()) {
            changed();
        }
        return outcome;
    }

    public Outcome setShare(UUID actor, UUID member, int share) {
        return leaderChange(actor, party -> {
            if (!party.has(member)) {
                return Outcome.fail(KingdomError.NOT_IN_PARTY);
            }
            if (share < 0 || share > Party.MAX_SHARE) {
                return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
            }
            party.setShare(member, share);
            return Outcome.DONE;
        });
    }

    public Outcome setEvenSplit(UUID actor, boolean even) {
        return leaderChange(actor, party -> {
            if (party.evenSplit() == even) {
                return Outcome.fail(KingdomError.UNCHANGED);
            }
            party.setEvenSplit(even);
            return Outcome.DONE;
        });
    }

    public Outcome setFriendlyFire(UUID actor, boolean on) {
        return leaderChange(actor, party -> {
            if (party.friendlyFire() == on) {
                return Outcome.fail(KingdomError.UNCHANGED);
            }
            party.setFriendlyFire(on);
            return Outcome.DONE;
        });
    }

    // ---------------------------------------------------------------- contracts

    /**
     * A contract a leader took becomes the party's.
     *
     * @return whether the taker leads a party
     */
    public boolean adoptContract(UUID taker, long contractId) {
        Optional<Party> found = partyOf(taker);
        if (found.isEmpty() || !found.get().isLeader(taker)) {
            return false;
        }
        found.get().addContract(contractId, taker);
        changed();
        return true;
    }

    /**
     * Forgets contracts that are no longer held by their holder (finished, run out, given up or called off).
     *
     * @param held whether a contract is still held by the given player
     */
    public void forgetContracts(java.util.function.BiPredicate<Long, UUID> held) {
        boolean any = false;
        for (Party party : parties.values()) {
            for (Map.Entry<Long, UUID> entry : List.copyOf(party.contracts().entrySet())) {
                if (!held.test(entry.getKey(), entry.getValue())) {
                    party.removeContract(entry.getKey());
                    any = true;
                }
            }
        }
        if (any) {
            changed();
        }
    }

    /**
     * Invitations that ran out are forgotten.
     */
    public void expireInvites(long now) {
        if (invites.removeIf(invite -> invite.expires() <= now)) {
            changed();
        }
    }

    /**
     * Players who left the guild leave their parties.
     */
    public void keepOnly(Predicate<UUID> inGuild) {
        for (Party party : List.copyOf(parties.values())) {
            for (UUID member : List.copyOf(party.members())) {
                if (!inGuild.test(member)) {
                    leave(member);
                }
            }
        }
    }

    /**
     * The sharing of contracts by parties, for the guild: every member works on every contract of the party, and the reward
     * is split by the shares of the members at the moment the contract is finished.
     */
    public GuildState.Sharing sharing() {
        return new GuildState.Sharing() {
            @Override
            public Set<Long> sharedWith(UUID player) {
                return partyOf(player).map(party -> (Set<Long>) new LinkedHashSet<>(party.contracts().keySet())).orElse(Set.of());
            }

            @Override
            public List<RewardSplit.Share> shares(Contract contract) {
                for (Party party : parties.values()) {
                    UUID holder = party.contracts().get(contract.id());
                    if (holder != null && holder.equals(contract.player())) {
                        List<RewardSplit.Share> shares = new ArrayList<>();
                        for (UUID member : party.members()) {
                            shares.add(new RewardSplit.Share(member, party.evenSplit() ? 1 : party.share(member)));
                        }
                        return shares;
                    }
                }
                return List.of();
            }
        };
    }

    // ---------------------------------------------------------------- saved data

    public void restoreParty(Party party) {
        for (UUID member : party.members()) {
            if (partyOf(member).isPresent()) {
                throw new IllegalArgumentException("Player " + member + " is in two parties");
            }
        }
        if (parties.putIfAbsent(party.id(), party) != null) {
            throw new IllegalArgumentException("Two parties #" + party.id());
        }
        nextParty = Math.max(nextParty, party.id() + 1);
    }

    public void restoreInvite(Invite invite) {
        invites.add(invite);
    }

    public void restoreNextId(long id) {
        nextParty = Math.max(nextParty, id);
    }

    public void clear() {
        parties.clear();
        invites.clear();
        nextParty = 1;
    }
}
