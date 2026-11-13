package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.core.guild.Branch;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Hireling;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.guild.Squad;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The guild in saved data. Every entry is read on its own: an entry that cannot be read is described and left out, and
 * the caller keeps the whole data read-only.
 *
 * <pre>
 * members: [ { player, rank, points, joined, kills, contracts, cooldown } ]
 * hirelings: [ { citizen, kingdom, rank, points, kills, expeditions } ]
 * branches: [ { kingdom, commission, budget, budgetDay, spent, lastAuto, rank?, done? } ]  (rank and done added later: a branch
 *            without them has the first rank and has done nothing)
 * contracts: [ { id, kind, branch, poster, posterId, minRank, target, targetRank, boss, count, progress, currency, reward,
 *                points, commission, posted, deadline, status, player?, squad, squads } ]
 * squads: [ { id, kingdom, members: [uuid], state, contract, departed, returns, power } ]
 * nextContract, nextSquad
 * </pre>
 */
final class GuildNbt {
    static final String MEMBERS = "members";
    static final String HIRELINGS = "hirelings";
    static final String BRANCHES = "branches";
    static final String CONTRACTS = "contracts";
    static final String SQUADS = "squads";
    static final String NEXT_CONTRACT = "nextContract";
    static final String NEXT_SQUAD = "nextSquad";

    private GuildNbt() {
    }

    static void write(GuildState guild, CompoundTag tag) {
        ListTag members = new ListTag();
        for (GuildMember member : guild.members()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", member.player());
            entry.putString("rank", member.rank().id());
            entry.putLong("points", member.points());
            entry.putLong("joined", member.joined());
            entry.putInt("kills", member.kills());
            entry.putInt("contracts", member.contracts());
            entry.putLong("cooldown", member.examCooldownEnd());
            members.add(entry);
        }
        tag.put(MEMBERS, members);
        ListTag hirelings = new ListTag();
        for (Hireling hireling : guild.hirelings()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("citizen", hireling.citizenId());
            entry.putUUID("kingdom", hireling.kingdomId());
            entry.putString("rank", hireling.rank().id());
            entry.putLong("points", hireling.points());
            entry.putInt("kills", hireling.kills());
            entry.putInt("expeditions", hireling.expeditions());
            hirelings.add(entry);
        }
        tag.put(HIRELINGS, hirelings);
        ListTag branches = new ListTag();
        for (Branch branch : guild.branches()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", branch.kingdomId());
            entry.putInt("commission", branch.commissionPermille());
            entry.putLong("budget", branch.dailyBudget());
            entry.putLong("budgetDay", branch.budgetDay());
            entry.putLong("spent", branch.spentToday());
            entry.putLong("lastAuto", branch.lastAuto());
            entry.putString("rank", branch.rank().id());
            entry.putLong("done", branch.done());
            branches.add(entry);
        }
        tag.put(BRANCHES, branches);
        ListTag contracts = new ListTag();
        for (Contract contract : guild.contracts()) {
            contracts.add(writeContract(contract));
        }
        tag.put(CONTRACTS, contracts);
        ListTag squads = new ListTag();
        for (Squad squad : guild.squads()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", squad.id());
            entry.putUUID("kingdom", squad.kingdomId());
            ListTag ids = new ListTag();
            for (UUID member : squad.members()) {
                ids.add(NbtUtils.createUUID(member));
            }
            entry.put("members", ids);
            entry.putString("state", squad.state().id());
            entry.putLong("contract", squad.contractId());
            entry.putLong("departed", squad.departed());
            entry.putLong("returns", squad.returns());
            entry.putDouble("power", squad.power());
            squads.add(entry);
        }
        tag.put(SQUADS, squads);
        tag.putLong(NEXT_CONTRACT, guild.nextContractId());
        tag.putLong(NEXT_SQUAD, guild.nextSquadId());
    }

    static CompoundTag writeContract(Contract contract) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", contract.id());
        entry.putString("kind", contract.kind().id());
        entry.putUUID("branch", contract.branch());
        entry.putString("poster", contract.poster().id());
        entry.putUUID("posterId", contract.posterId());
        entry.putString("minRank", contract.minRank().id());
        entry.putString("target", contract.target());
        entry.putString("targetRank", contract.targetRank().id());
        entry.putBoolean("boss", contract.boss());
        entry.putInt("count", contract.count());
        entry.putInt("progress", contract.progress());
        entry.putUUID("currency", contract.currency());
        entry.putLong("reward", contract.reward());
        entry.putInt("points", contract.points());
        entry.putInt("commission", contract.commissionPermille());
        entry.putLong("posted", contract.posted());
        entry.putLong("deadline", contract.deadline());
        entry.putString("status", contract.status().id());
        if (contract.player() != null) {
            entry.putUUID("player", contract.player());
        }
        entry.putLong("squad", contract.squad());
        entry.putBoolean("squads", contract.squadsAllowed());
        return entry;
    }

    /**
     * Reads everything that can be read into an empty guild.
     *
     * @return what could not be read
     */
    static List<String> read(CompoundTag tag, GuildState guild) {
        List<String> problems = new ArrayList<>();
        for (CompoundTag entry : list(tag, MEMBERS, problems)) {
            try {
                guild.restoreMember(new GuildMember(uuid(entry, "player"), rank(entry, "rank"), entry.getLong("points"), entry.getLong("joined"),
                        entry.getInt("kills"), entry.getInt("contracts"), entry.getLong("cooldown")));
            } catch (RuntimeException e) {
                problems.add("guild member: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, HIRELINGS, problems)) {
            try {
                guild.restoreHireling(new Hireling(uuid(entry, "citizen"), uuid(entry, "kingdom"), rank(entry, "rank"), entry.getLong("points"),
                        entry.getInt("kills"), entry.getInt("expeditions")));
            } catch (RuntimeException e) {
                problems.add("adventurer: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, BRANCHES, problems)) {
            try {
                Rank rank = entry.contains("rank") ? rank(entry, "rank") : Branch.FIRST_RANK;
                guild.restoreBranch(new Branch(uuid(entry, "kingdom"), entry.getInt("commission"), entry.getLong("budget"),
                        entry.getLong("budgetDay"), entry.getLong("spent"), entry.getLong("lastAuto"), rank, Math.max(0, entry.getLong("done"))));
            } catch (RuntimeException e) {
                problems.add("branch: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, CONTRACTS, problems)) {
            try {
                guild.restoreContract(readContract(entry));
            } catch (RuntimeException e) {
                problems.add("contract #" + entry.getLong("id") + ": " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, SQUADS, problems)) {
            try {
                List<UUID> members = new ArrayList<>();
                ListTag ids = entry.getList("members", Tag.TAG_INT_ARRAY);
                for (Tag id : ids) {
                    members.add(NbtUtils.loadUUID(id));
                }
                Squad.State state = entry.getString("state").equals("away") ? Squad.State.AWAY : Squad.State.HOME;
                guild.restoreSquad(new Squad(entry.getLong("id"), uuid(entry, "kingdom"), members, state, entry.getLong("contract"),
                        entry.getLong("departed"), entry.getLong("returns"), entry.getDouble("power")));
            } catch (RuntimeException e) {
                problems.add("squad #" + entry.getLong("id") + ": " + e.getMessage());
            }
        }
        guild.restoreNextIds(tag.getLong(NEXT_CONTRACT), tag.getLong(NEXT_SQUAD));
        return problems;
    }

    static Contract readContract(CompoundTag entry) {
        Contract.Kind kind = Contract.Kind.byId(entry.getString("kind"))
                .orElseThrow(() -> new IllegalArgumentException("unknown kind '" + entry.getString("kind") + "'"));
        Contract.Poster poster = Contract.Poster.byId(entry.getString("poster"))
                .orElseThrow(() -> new IllegalArgumentException("unknown poster '" + entry.getString("poster") + "'"));
        Contract.Status status = Contract.Status.byId(entry.getString("status"))
                .filter(value -> value == Contract.Status.OPEN || value == Contract.Status.TAKEN)
                .orElseThrow(() -> new IllegalArgumentException("unknown status '" + entry.getString("status") + "'"));
        return new Contract(entry.getLong("id"), kind, uuid(entry, "branch"), poster, uuid(entry, "posterId"), rank(entry, "minRank"),
                entry.getString("target"), rank(entry, "targetRank"), entry.getBoolean("boss"), entry.getInt("count"), entry.getInt("progress"),
                uuid(entry, "currency"), entry.getLong("reward"), entry.getInt("points"), entry.getInt("commission"), entry.getLong("posted"),
                entry.getLong("deadline"), status, entry.hasUUID("player") ? entry.getUUID("player") : null, entry.getLong("squad"),
                entry.getBoolean("squads"));
    }

    private static List<CompoundTag> list(CompoundTag tag, String key, List<String> problems) {
        if (!tag.contains(key)) {
            return List.of();
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            problems.add("'" + key + "' is not a list");
            return List.of();
        }
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            problems.add("'" + key + "' does not hold compounds");
            return List.of();
        }
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("no '" + key + "'");
        }
        return tag.getUUID(key);
    }

    private static Rank rank(CompoundTag tag, String key) {
        return Rank.byId(tag.getString(key)).orElseThrow(() -> new IllegalArgumentException("unknown rank '" + tag.getString(key) + "'"));
    }
}
