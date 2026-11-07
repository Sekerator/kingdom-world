package io.github.sekerator.kingdomworld.core.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A party of players of the guild: a leader and members who share the contracts the party takes, split the reward evenly
 * or by the shares the leader sets, and may be kept from hurting each other and each other's companions.
 *
 * <p>Changed only through {@link PartyState}.
 */
public final class Party {
    /** Players in one party at most, whatever the config says. */
    public static final int MAX_MEMBERS = 16;
    /** Largest share of one member. */
    public static final int MAX_SHARE = 100;
    /** The share of a new member. */
    public static final int DEFAULT_SHARE = 10;

    private final long id;
    private UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, Integer> shares = new LinkedHashMap<>();
    private boolean evenSplit = true;
    private boolean friendlyFire;
    /** Contracts of the party and who holds each. */
    private final Map<Long, UUID> contracts = new LinkedHashMap<>();

    public Party(long id, UUID leader) {
        if (id < 1) {
            throw new IllegalArgumentException("Party out of range: #" + id);
        }
        this.id = id;
        this.leader = Objects.requireNonNull(leader, "leader");
        members.add(leader);
        shares.put(leader, DEFAULT_SHARE);
    }

    public long id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    /**
     * The members in the order they joined.
     */
    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    public boolean has(UUID player) {
        return members.contains(player);
    }

    public int share(UUID player) {
        return shares.getOrDefault(player, 0);
    }

    /**
     * Whether the reward is split evenly; otherwise by the shares.
     */
    public boolean evenSplit() {
        return evenSplit;
    }

    /**
     * Whether members may hurt each other and each other's companions.
     */
    public boolean friendlyFire() {
        return friendlyFire;
    }

    /**
     * The contracts of the party and who holds each.
     */
    public Map<Long, UUID> contracts() {
        return Collections.unmodifiableMap(contracts);
    }

    /**
     * What a member gets of a reward, in thousandths.
     */
    public int permilleOf(UUID player) {
        if (!members.contains(player)) {
            return 0;
        }
        if (evenSplit) {
            return 1000 / members.size();
        }
        long total = 0;
        for (UUID member : members) {
            total += share(member);
        }
        return total == 0 ? 1000 / members.size() : (int) (share(player) * 1000L / total);
    }

    // ---------------------------------------------------------------- changes, through PartyState

    void add(UUID player) {
        if (!members.contains(player)) {
            members.add(player);
            shares.put(player, DEFAULT_SHARE);
        }
    }

    void remove(UUID player) {
        members.remove(player);
        shares.remove(player);
        contracts.values().removeIf(player::equals);
        if (leader.equals(player) && !members.isEmpty()) {
            leader = members.get(0);
        }
    }

    void setLeader(UUID player) {
        leader = player;
    }

    void setShare(UUID player, int value) {
        if (members.contains(player)) {
            shares.put(player, Math.max(0, Math.min(MAX_SHARE, value)));
        }
    }

    void setEvenSplit(boolean value) {
        evenSplit = value;
    }

    void setFriendlyFire(boolean value) {
        friendlyFire = value;
    }

    void addContract(long contractId, UUID holder) {
        contracts.put(contractId, holder);
    }

    boolean removeContract(long contractId) {
        return contracts.remove(contractId) != null;
    }

    /**
     * Restores a party from saved data: members after the leader with their shares, the settings and the contracts.
     */
    public void restore(List<UUID> others, Map<UUID, Integer> savedShares, boolean even, boolean fire, Map<Long, UUID> savedContracts) {
        for (UUID member : others) {
            add(member);
        }
        for (Map.Entry<UUID, Integer> entry : savedShares.entrySet()) {
            setShare(entry.getKey(), entry.getValue());
        }
        evenSplit = even;
        friendlyFire = fire;
        for (Map.Entry<Long, UUID> entry : savedContracts.entrySet()) {
            if (members.contains(entry.getValue())) {
                contracts.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
