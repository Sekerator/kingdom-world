package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The memories of the rulers of the kingdoms of the world in saved data (format 2 of the kingdoms of the world). Data of format
 * 1 has none, and every ruler starts with an empty memory. An entry that cannot be read is described, and the caller keeps the
 * data read-only.
 *
 * <pre>
 * rulers: [ { kingdom, turns, poor, chronicleDay, chronicled, councillor?, deals: [id],
 *             decrees: [ { day, decision } ], chronicle: [ { day, decision } ], needs: [ { kind, subject, amount } ],
 *             pending: [ decision ], relations: [ { other, score, reasons: { reason: count } } ], players: [ { player, value } ] } ]
 * decision: { kind, subject, amount, extra, target, name }
 * </pre>
 */
final class RulerNbt {
    private RulerNbt() {
    }

    static void write(NpcState state, CompoundTag tag) {
        ListTag rulers = new ListTag();
        state.rulers().forEach((kingdom, memory) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", kingdom);
            entry.putInt("turns", memory.turns());
            entry.putLong("poor", memory.poorSince());
            entry.putLong("chronicleDay", memory.chronicleDay());
            entry.putInt("chronicled", memory.chronicleCount());
            if (memory.councillor() != null) {
                entry.putUUID("councillor", memory.councillor());
            }
            entry.put("deals", new LongArrayTag(memory.seenDeals()));
            entry.put("decrees", decrees(memory.decrees()));
            entry.put("chronicle", decrees(memory.chronicle()));
            ListTag needs = new ListTag();
            for (RulerMemory.Need need : memory.needs()) {
                CompoundTag item = new CompoundTag();
                item.putString("kind", need.kind().id());
                item.putString("subject", need.subject());
                item.putLong("amount", need.amount());
                needs.add(item);
            }
            entry.put("needs", needs);
            ListTag pending = new ListTag();
            memory.pending().forEach(decision -> pending.add(decision(decision)));
            entry.put("pending", pending);
            ListTag relations = new ListTag();
            memory.relations().forEach((other, relation) -> {
                CompoundTag item = new CompoundTag();
                item.putUUID("other", other);
                item.putInt("score", relation.score());
                CompoundTag reasons = new CompoundTag();
                relation.reasons().forEach((reason, count) -> reasons.putInt(reason.id(), count));
                item.put("reasons", reasons);
                relations.add(item);
            });
            entry.put("relations", relations);
            ListTag players = new ListTag();
            memory.players().forEach((player, value) -> {
                CompoundTag item = new CompoundTag();
                item.putUUID("player", player);
                item.putInt("value", value);
                players.add(item);
            });
            entry.put("players", players);
            rulers.add(entry);
        });
        tag.put("rulers", rulers);
    }

    private static ListTag decrees(List<RulerMemory.Decree> decrees) {
        ListTag list = new ListTag();
        for (RulerMemory.Decree decree : decrees) {
            CompoundTag item = new CompoundTag();
            item.putLong("day", decree.day());
            item.put("decision", decision(decree.decision()));
            list.add(item);
        }
        return list;
    }

    private static CompoundTag decision(RulerDecision decision) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", decision.kind().id());
        tag.putString("subject", decision.subject());
        tag.putLong("amount", decision.amount());
        tag.putLong("extra", decision.extra());
        tag.putString("target", decision.target());
        tag.putString("name", decision.targetName());
        return tag;
    }

    private static RulerDecision decision(CompoundTag tag) {
        String id = tag.getString("kind");
        RulerDecision.Kind kind = RulerDecision.Kind.byId(id).orElseThrow(() -> new IllegalArgumentException("decision " + id));
        return new RulerDecision(kind, tag.getString("subject"), tag.getLong("amount"), tag.getLong("extra"), tag.getString("target"),
                tag.getString("name"));
    }

    /**
     * Reads the memories that can be read into the state.
     *
     * @param problems gets descriptions of the entries that could not be read
     */
    static void read(CompoundTag tag, NpcState state, List<String> problems) {
        ListTag rulers = tag.getList("rulers", Tag.TAG_COMPOUND);
        for (int i = 0; i < rulers.size(); i++) {
            CompoundTag entry = rulers.getCompound(i);
            try {
                if (!entry.hasUUID("kingdom")) {
                    throw new IllegalArgumentException("kingdom");
                }
                UUID kingdom = entry.getUUID("kingdom");
                RulerMemory memory = new RulerMemory();
                memory.restore(entry.getInt("turns"), entry.getLong("poor"), entry.getLong("chronicleDay"), entry.getInt("chronicled"));
                if (entry.hasUUID("councillor")) {
                    memory.setCouncillor(entry.getUUID("councillor"));
                }
                for (long deal : entry.getLongArray("deals")) {
                    memory.seeDeal(deal);
                }
                ListTag decrees = entry.getList("decrees", Tag.TAG_COMPOUND);
                for (int d = 0; d < decrees.size(); d++) {
                    CompoundTag item = decrees.getCompound(d);
                    memory.restoreDecree(new RulerMemory.Decree(item.getLong("day"), decision(item.getCompound("decision"))));
                }
                ListTag chronicle = entry.getList("chronicle", Tag.TAG_COMPOUND);
                for (int d = 0; d < chronicle.size(); d++) {
                    CompoundTag item = chronicle.getCompound(d);
                    memory.restoreChronicle(new RulerMemory.Decree(item.getLong("day"), decision(item.getCompound("decision"))));
                }
                ListTag needTags = entry.getList("needs", Tag.TAG_COMPOUND);
                java.util.List<RulerMemory.Need> needs = new java.util.ArrayList<>();
                for (int n = 0; n < needTags.size(); n++) {
                    CompoundTag item = needTags.getCompound(n);
                    String id = item.getString("kind");
                    needs.add(new RulerMemory.Need(RulerMemory.NeedKind.byId(id).orElseThrow(() -> new IllegalArgumentException("need " + id)),
                            item.getString("subject"), item.getLong("amount")));
                }
                memory.setNeeds(needs);
                ListTag pending = entry.getList("pending", Tag.TAG_COMPOUND);
                java.util.List<RulerDecision> decisions = new java.util.ArrayList<>();
                for (int p = 0; p < pending.size(); p++) {
                    decisions.add(decision(pending.getCompound(p)));
                }
                memory.addPending(decisions);
                ListTag relations = entry.getList("relations", Tag.TAG_COMPOUND);
                for (int r = 0; r < relations.size(); r++) {
                    CompoundTag item = relations.getCompound(r);
                    if (!item.hasUUID("other")) {
                        throw new IllegalArgumentException("relation " + r);
                    }
                    Map<RulerMemory.Reason, Integer> reasons = new EnumMap<>(RulerMemory.Reason.class);
                    CompoundTag counts = item.getCompound("reasons");
                    for (String key : counts.getAllKeys()) {
                        reasons.put(RulerMemory.Reason.byId(key).orElseThrow(() -> new IllegalArgumentException("reason " + key)),
                                counts.getInt(key));
                    }
                    memory.restoreRelation(item.getUUID("other"), item.getInt("score"), reasons);
                }
                ListTag players = entry.getList("players", Tag.TAG_COMPOUND);
                for (int p = 0; p < players.size(); p++) {
                    CompoundTag item = players.getCompound(p);
                    if (!item.hasUUID("player")) {
                        throw new IllegalArgumentException("player " + p);
                    }
                    memory.dealt(item.getUUID("player"), item.getInt("value"));
                }
                state.restoreRuler(kingdom, memory);
            } catch (RuntimeException e) {
                problems.add("ruler " + i + ": " + e.getMessage());
            }
        }
    }
}
