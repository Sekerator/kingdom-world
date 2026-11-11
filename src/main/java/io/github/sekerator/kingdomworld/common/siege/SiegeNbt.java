package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.GateMaterial;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.siege.WarDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes {@link SiegeState}. Reading collects problems instead of throwing; an entry that cannot be read is
 * skipped and named, and the caller keeps the data read-only.
 */
public final class SiegeNbt {
    private SiegeNbt() {
    }

    public static CompoundTag write(SiegeState state) {
        CompoundTag tag = new CompoundTag();
        ListTag gates = new ListTag();
        for (Gate gate : state.gates()) {
            CompoundTag g = new CompoundTag();
            g.putLong("wall", gate.wallId());
            g.putInt("cell", gate.cell());
            g.putUUID("kingdom", gate.kingdomId());
            g.putString("material", gate.material().id());
            g.putDouble("strength", gate.strength());
            g.putBoolean("hung", gate.hung());
            g.putBoolean("closed", gate.closed());
            g.putInt("leaves", gate.leaves());
            gates.add(g);
        }
        tag.put("gates", gates);
        ListTag holds = new ListTag();
        for (Hold hold : state.holds()) {
            CompoundTag h = new CompoundTag();
            h.putString("kind", hold.kind().id());
            h.putLong("war", hold.war());
            h.putUUID("attacker", hold.attacker());
            h.putUUID("defender", hold.defender());
            h.putString("dimension", hold.dimension());
            h.putLong("target", hold.target());
            h.putLong("progress", hold.progress());
            h.putBoolean("taken", hold.taken());
            h.putLong("since", hold.since());
            holds.add(h);
        }
        tag.put("holds", holds);
        ListTag sieges = new ListTag();
        for (Siege siege : state.sieges()) {
            CompoundTag s = new CompoundTag();
            s.putLong("war", siege.war());
            s.putUUID("attacker", siege.attacker());
            s.putUUID("defender", siege.defender());
            s.putLong("city", siege.city());
            s.putString("dimension", siege.dimension());
            s.putInt("x", siege.x());
            s.putInt("y", siege.y());
            s.putInt("z", siege.z());
            s.putLong("started", siege.started());
            s.putInt("rams", siege.rams());
            s.putBoolean("npc", siege.npc());
            sieges.add(s);
        }
        tag.put("sieges", sieges);
        ListTag conquests = new ListTag();
        for (Conquest conquest : state.conquests()) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", conquest.id());
            c.putString("kind", conquest.kind().id());
            c.putLong("war", conquest.war());
            c.putUUID("winner", conquest.winner());
            c.putUUID("loser", conquest.loser());
            c.putString("dimension", conquest.dimension());
            c.put("chunks", new LongArrayTag(conquest.chunks().stream().mapToLong(Long::longValue).toArray()));
            c.putLong("city", conquest.city());
            c.putString("step", conquest.step().id());
            c.putLong("made", conquest.made());
            conquests.add(c);
        }
        tag.put("conquests", conquests);
        tag.putLong("nextConquest", state.nextConquestId());
        ListTag damage = new ListTag();
        for (Map.Entry<UUID, List<WarDamage.Entry>> of : state.damage().all().entrySet()) {
            for (WarDamage.Entry entry : of.getValue()) {
                CompoundTag d = new CompoundTag();
                d.putLong("war", entry.war());
                d.putUUID("kingdom", entry.kingdom());
                d.putString("dimension", entry.dimension());
                d.putLong("pos", entry.pos());
                d.putString("state", entry.state());
                d.putString("item", entry.item());
                d.putLong("building", entry.building());
                d.putLong("time", entry.time());
                // Format 2: damage done in peace is put back for free, and remembers a block a stranger placed over it.
                if (entry.free()) {
                    d.putBoolean("free", true);
                }
                if (!entry.placed().isEmpty()) {
                    d.putString("placed", entry.placed());
                }
                damage.add(d);
            }
        }
        tag.put("damage", damage);
        ListTag kings = new ListTag();
        for (String key : state.kingsSeen()) {
            kings.add(StringTag.valueOf(key));
        }
        tag.put("kingsSeen", kings);
        ListTag near = new ListTag();
        for (Map.Entry<UUID, Long> entry : state.enemiesNear().entrySet()) {
            CompoundTag n = new CompoundTag();
            n.putUUID("kingdom", entry.getKey());
            n.putLong("time", entry.getValue());
            near.add(n);
        }
        tag.put("enemyNear", near);
        ListTag offers = new ListTag();
        for (SiegeState.Offer offer : state.offers()) {
            CompoundTag o = new CompoundTag();
            o.putUUID("player", offer.player());
            o.putUUID("winner", offer.winner());
            o.putString("name", offer.winnerName());
            o.putLong("made", offer.made());
            offers.add(o);
        }
        tag.put("offers", offers);
        tag.put("placed", strings(state.placedContainers()));
        tag.put("robbed", strings(state.robbedContainers()));
        CompoundTag loot = new CompoundTag();
        state.lootScores().forEach(loot::putDouble);
        tag.put("loot", loot);
        tag.put("scored", strings(state.scoredHolds()));
        return tag;
    }

    private static ListTag strings(java.util.Collection<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static java.util.Set<String> strings(CompoundTag tag, String key) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    /**
     * Reads into an empty state.
     *
     * @return the problems met; empty when everything was read
     */
    public static List<String> read(CompoundTag tag, SiegeState state) {
        List<String> problems = new ArrayList<>();
        ListTag gates = tag.getList("gates", Tag.TAG_COMPOUND);
        for (int i = 0; i < gates.size(); i++) {
            CompoundTag g = gates.getCompound(i);
            try {
                GateMaterial material = GateMaterial.byId(g.getString("material")).orElseThrow(() -> new IllegalArgumentException("material"));
                Gate gate = new Gate(g.getLong("wall"), g.getInt("cell"), g.getUUID("kingdom"), material, g.getDouble("strength"),
                        g.getBoolean("hung"), g.getBoolean("closed"));
                if (g.contains("leaves", Tag.TAG_INT)) {
                    gate.setLeaves(g.getInt("leaves"));
                }
                state.restoreGate(gate);
            } catch (RuntimeException e) {
                problems.add("gate " + i + ": " + e.getMessage());
            }
        }
        ListTag holds = tag.getList("holds", Tag.TAG_COMPOUND);
        for (int i = 0; i < holds.size(); i++) {
            CompoundTag h = holds.getCompound(i);
            try {
                Hold.Kind kind = Hold.Kind.byId(h.getString("kind")).orElseThrow(() -> new IllegalArgumentException("kind"));
                state.restoreHold(new Hold(kind, h.getLong("war"), h.getUUID("attacker"), h.getUUID("defender"), h.getString("dimension"),
                        h.getLong("target"), h.getLong("progress"), h.getBoolean("taken"), h.getLong("since")));
            } catch (RuntimeException e) {
                problems.add("hold " + i + ": " + e.getMessage());
            }
        }
        ListTag sieges = tag.getList("sieges", Tag.TAG_COMPOUND);
        for (int i = 0; i < sieges.size(); i++) {
            CompoundTag s = sieges.getCompound(i);
            try {
                state.restoreSiege(new Siege(s.getLong("war"), s.getUUID("attacker"), s.getUUID("defender"), s.getLong("city"),
                        s.getString("dimension"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getLong("started"), s.getInt("rams"),
                        s.getBoolean("npc")));
            } catch (RuntimeException e) {
                problems.add("siege " + i + ": " + e.getMessage());
            }
        }
        ListTag conquests = tag.getList("conquests", Tag.TAG_COMPOUND);
        for (int i = 0; i < conquests.size(); i++) {
            CompoundTag c = conquests.getCompound(i);
            try {
                Conquest.Kind kind = Conquest.Kind.byId(c.getString("kind")).orElseThrow(() -> new IllegalArgumentException("kind"));
                Conquest.Step step = Conquest.Step.byId(c.getString("step")).orElseThrow(() -> new IllegalArgumentException("step"));
                List<Long> chunks = new ArrayList<>();
                for (long chunk : c.getLongArray("chunks")) {
                    chunks.add(chunk);
                }
                state.restoreConquest(new Conquest(c.getLong("id"), kind, c.getLong("war"), c.getUUID("winner"), c.getUUID("loser"),
                        c.getString("dimension"), chunks, c.getLong("city"), step, c.getLong("made")));
            } catch (RuntimeException e) {
                problems.add("conquest " + i + ": " + e.getMessage());
            }
        }
        if (tag.contains("nextConquest", Tag.TAG_LONG)) {
            state.restoreNextConquest(tag.getLong("nextConquest"));
        }
        ListTag damage = tag.getList("damage", Tag.TAG_COMPOUND);
        for (int i = 0; i < damage.size(); i++) {
            CompoundTag d = damage.getCompound(i);
            try {
                state.damage().record(new WarDamage.Entry(d.getLong("war"), d.getUUID("kingdom"), d.getString("dimension"), d.getLong("pos"),
                        d.getString("state"), d.getString("item"), d.getLong("building"), d.getLong("time"), d.getBoolean("free"),
                        d.getString("placed")));
            } catch (RuntimeException e) {
                problems.add("damage " + i + ": " + e.getMessage());
            }
        }
        ListTag kings = tag.getList("kingsSeen", Tag.TAG_STRING);
        for (int i = 0; i < kings.size(); i++) {
            state.restoreKingSeen(kings.getString(i));
        }
        ListTag near = tag.getList("enemyNear", Tag.TAG_COMPOUND);
        for (int i = 0; i < near.size(); i++) {
            CompoundTag n = near.getCompound(i);
            try {
                state.restoreEnemyNear(n.getUUID("kingdom"), n.getLong("time"));
            } catch (RuntimeException e) {
                problems.add("enemy near " + i + ": " + e.getMessage());
            }
        }
        java.util.Map<String, Double> loot = new java.util.LinkedHashMap<>();
        CompoundTag lootTag = tag.getCompound("loot");
        for (String key : lootTag.getAllKeys()) {
            loot.put(key, lootTag.getDouble(key));
        }
        state.restoreLoot(strings(tag, "placed"), strings(tag, "robbed"), loot, strings(tag, "scored"));
        ListTag offers = tag.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.getCompound(i);
            try {
                state.restoreOffer(new SiegeState.Offer(o.getUUID("player"), o.getUUID("winner"), o.getString("name"), o.getLong("made")));
            } catch (RuntimeException e) {
                problems.add("offer " + i + ": " + e.getMessage());
            }
        }
        return problems;
    }
}
