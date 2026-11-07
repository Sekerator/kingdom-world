package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.EnergyJournal;
import io.github.sekerator.kingdomworld.core.magic.KingdomMagic;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The magic of a world in saved data. Every entry is read on its own: an entry that cannot be read is described and left
 * out, and the caller keeps the whole data read-only.
 *
 * <pre>
 * kingdoms: [ { kingdom, energy, done: [id], current?, progress, enabled: [id], paid: { spell: day }, ward: [ { dimension, chunk } ],
 *               healed: [uuid], wind: [long], journal: [ { day, entries: [ { category, income, expense } ] } ] } ]
 * devices: [ { id, kind, kingdom, dimension, x, y, z } ]
 * learned: [ { player, spells: [id] } ]
 * nextDevice
 * </pre>
 */
final class MagicNbt {
    static final String KINGDOMS = "kingdoms";
    static final String DEVICES = "devices";
    static final String LEARNED = "learned";
    static final String NEXT_DEVICE = "nextDevice";

    private MagicNbt() {
    }

    private static <E extends Enum<E>> ListTag ids(Iterable<E> values, Function<E, String> id) {
        ListTag list = new ListTag();
        for (E value : values) {
            list.add(StringTag.valueOf(id.apply(value)));
        }
        return list;
    }

    static void write(MagicState state, CompoundTag tag) {
        ListTag kingdoms = new ListTag();
        for (KingdomMagic magic : state.kingdoms()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", magic.kingdomId());
            entry.putLong("energy", magic.energy());
            entry.put("done", ids(magic.done(), Research::id));
            magic.current().ifPresent(research -> entry.putString("current", research.id()));
            entry.putDouble("progress", magic.progress());
            entry.put("enabled", ids(magic.enabled(), KingdomSpell::id));
            CompoundTag paid = new CompoundTag();
            magic.paidDays().forEach((spell, day) -> paid.putLong(spell.id(), day));
            entry.put("paid", paid);
            ListTag ward = new ListTag();
            for (KingdomMagic.WardChunk chunk : magic.ward()) {
                CompoundTag item = new CompoundTag();
                item.putString("dimension", chunk.dimension());
                item.putLong("chunk", chunk.chunk());
                ward.add(item);
            }
            entry.put("ward", ward);
            ListTag healed = new ListTag();
            for (UUID citizen : magic.healed()) {
                healed.add(NbtUtils.createUUID(citizen));
            }
            entry.put("healed", healed);
            ListTag wind = new ListTag();
            for (long caravan : magic.windCaravans()) {
                wind.add(LongTag.valueOf(caravan));
            }
            entry.put("wind", wind);
            ListTag journal = new ListTag();
            for (Map.Entry<Long, Map<EnergyCategory, EnergyJournal.Totals>> day : magic.journal().days().entrySet()) {
                CompoundTag dayTag = new CompoundTag();
                dayTag.putLong("day", day.getKey());
                ListTag entries = new ListTag();
                day.getValue().forEach((category, totals) -> {
                    CompoundTag item = new CompoundTag();
                    item.putString("category", category.id());
                    item.putLong("income", totals.income());
                    item.putLong("expense", totals.expense());
                    entries.add(item);
                });
                dayTag.put("entries", entries);
                journal.add(dayTag);
            }
            entry.put("journal", journal);
            kingdoms.add(entry);
        }
        tag.put(KINGDOMS, kingdoms);
        ListTag devices = new ListTag();
        for (MagicDevice device : state.devices()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", device.id());
            entry.putString("kind", device.kind().id());
            entry.putUUID("kingdom", device.kingdomId());
            entry.putString("dimension", device.pos().dimension());
            entry.putInt("x", device.pos().x());
            entry.putInt("y", device.pos().y());
            entry.putInt("z", device.pos().z());
            devices.add(entry);
        }
        tag.put(DEVICES, devices);
        ListTag learned = new ListTag();
        state.allLearned().forEach((player, spells) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.put("spells", ids(spells, PlayerSpell::id));
            learned.add(entry);
        });
        tag.put(LEARNED, learned);
        tag.putLong(NEXT_DEVICE, state.nextDeviceId());
    }

    private static <E extends Enum<E>> EnumSet<E> readIds(ListTag list, Class<E> type, Function<String, Optional<E>> byId, String what) {
        EnumSet<E> result = EnumSet.noneOf(type);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            result.add(byId.apply(id).orElseThrow(() -> new IllegalArgumentException("unknown " + what + " '" + id + "'")));
        }
        return result;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("no " + key);
        }
        return tag.getUUID(key);
    }

    /**
     * Reads the magic into an empty state.
     *
     * @return what could not be read; empty if everything was read
     */
    static List<String> read(CompoundTag tag, MagicState state) {
        List<String> problems = new ArrayList<>();
        ListTag kingdoms = tag.getList(KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag entry = kingdoms.getCompound(i);
            try {
                UUID kingdom = uuid(entry, "kingdom");
                EnumSet<Research> done = readIds(entry.getList("done", Tag.TAG_STRING), Research.class, Research::byId, "research");
                Research current = null;
                if (entry.contains("current", Tag.TAG_STRING)) {
                    String id = entry.getString("current");
                    current = Research.byId(id).orElseThrow(() -> new IllegalArgumentException("unknown research '" + id + "'"));
                }
                EnumSet<KingdomSpell> enabled = readIds(entry.getList("enabled", Tag.TAG_STRING), KingdomSpell.class, KingdomSpell::byId, "spell");
                Map<KingdomSpell, Long> paid = new EnumMap<>(KingdomSpell.class);
                CompoundTag paidTag = entry.getCompound("paid");
                for (String key : paidTag.getAllKeys()) {
                    paid.put(KingdomSpell.byId(key).orElseThrow(() -> new IllegalArgumentException("unknown spell '" + key + "'")),
                            paidTag.getLong(key));
                }
                List<KingdomMagic.WardChunk> ward = new ArrayList<>();
                ListTag wardTag = entry.getList("ward", Tag.TAG_COMPOUND);
                for (int j = 0; j < wardTag.size(); j++) {
                    CompoundTag item = wardTag.getCompound(j);
                    ward.add(new KingdomMagic.WardChunk(item.getString("dimension"), item.getLong("chunk")));
                }
                List<UUID> healed = new ArrayList<>();
                ListTag healedTag = entry.getList("healed", Tag.TAG_INT_ARRAY);
                for (Tag item : healedTag) {
                    healed.add(NbtUtils.loadUUID(item));
                }
                List<Long> wind = new ArrayList<>();
                ListTag windTag = entry.getList("wind", Tag.TAG_LONG);
                for (Tag item : windTag) {
                    wind.add(((LongTag) item).getAsLong());
                }
                state.restoreKingdom(kingdom, entry.getLong("energy"), done, current, entry.getDouble("progress"), enabled, paid, ward, healed,
                        wind);
                ListTag journal = entry.getList("journal", Tag.TAG_COMPOUND);
                for (int j = 0; j < journal.size(); j++) {
                    CompoundTag dayTag = journal.getCompound(j);
                    Map<EnergyCategory, EnergyJournal.Totals> entries = new EnumMap<>(EnergyCategory.class);
                    ListTag items = dayTag.getList("entries", Tag.TAG_COMPOUND);
                    for (int k = 0; k < items.size(); k++) {
                        CompoundTag item = items.getCompound(k);
                        String id = item.getString("category");
                        entries.put(EnergyCategory.byId(id).orElseThrow(() -> new IllegalArgumentException("unknown category '" + id + "'")),
                                new EnergyJournal.Totals(item.getLong("income"), item.getLong("expense")));
                    }
                    state.restoreJournal(kingdom, dayTag.getLong("day"), entries);
                }
            } catch (RuntimeException e) {
                problems.add("kingdom #" + i + ": " + e.getMessage());
            }
        }
        ListTag devices = tag.getList(DEVICES, Tag.TAG_COMPOUND);
        for (int i = 0; i < devices.size(); i++) {
            CompoundTag entry = devices.getCompound(i);
            try {
                String kind = entry.getString("kind");
                state.restoreDevice(new MagicDevice(entry.getLong("id"), MagicDevice.Kind.byId(kind)
                        .orElseThrow(() -> new IllegalArgumentException("unknown device '" + kind + "'")), uuid(entry, "kingdom"),
                        new WorldPos(entry.getString("dimension"), entry.getInt("x"), entry.getInt("y"), entry.getInt("z"))));
            } catch (RuntimeException e) {
                problems.add("device #" + i + ": " + e.getMessage());
            }
        }
        ListTag learned = tag.getList(LEARNED, Tag.TAG_COMPOUND);
        for (int i = 0; i < learned.size(); i++) {
            CompoundTag entry = learned.getCompound(i);
            try {
                state.restoreLearned(uuid(entry, "player"),
                        readIds(entry.getList("spells", Tag.TAG_STRING), PlayerSpell.class, PlayerSpell::byId, "spell"));
            } catch (RuntimeException e) {
                problems.add("learned #" + i + ": " + e.getMessage());
            }
        }
        state.restoreNextDevice(tag.getLong(NEXT_DEVICE));
        return problems;
    }
}
