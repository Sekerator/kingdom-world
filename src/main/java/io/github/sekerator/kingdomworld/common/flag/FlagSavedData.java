package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the keeper of the flags remembers, stored with the overworld in {@code <world>/data/kingdomworld_flags.dat}: for every
 * building, what it last put up flags for (a stamp of the template, the place and the kingdom), whether a flag of it went
 * since, and the places members of its kingdom took flags down from; for every dimension, the places of flags of walls and
 * thrones members took down.
 *
 * <p>Nothing depends on the file: one that cannot be read is read as empty, and every building simply gets its flags looked
 * at once more.
 */
public final class FlagSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_flags";
    public static final int FORMAT_VERSION = 1;

    /**
     * The flags of one building.
     */
    public static final class Record {
        private long stamp;
        private boolean stamped;
        private boolean dirty;
        private final Set<Long> off = new HashSet<>();

        public boolean stamped() {
            return stamped;
        }

        public long stamp() {
            return stamp;
        }

        public boolean dirty() {
            return dirty;
        }

        public Set<Long> off() {
            return off;
        }
    }

    private final Map<Long, Record> buildings = new HashMap<>();
    /**
     * A place of a flag of a wall or a throne a member took down: the kingdom it was of and what it hung on, the packed place of
     * the throne or the id of the wall.
     */
    record Off(UUID kingdom, long anchor) {
    }

    /** Places of flags of walls and thrones taken down by members, by dimension. */
    private final Map<String, Map<Long, Off>> places = new HashMap<>();

    public static FlagSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FlagSavedData::load, FlagSavedData::new, DATA_NAME);
    }

    @Nullable
    public Record building(long id) {
        return buildings.get(id);
    }

    private Record record(long id) {
        return buildings.computeIfAbsent(id, key -> new Record());
    }

    /**
     * Notes that the flags of a building were put up for a stamp: it is no longer dirty.
     */
    public void stamp(long id, long stamp) {
        Record record = record(id);
        if (!record.stamped || record.stamp != stamp || record.dirty) {
            record.stamp = stamp;
            record.stamped = true;
            record.dirty = false;
            setDirty();
        }
    }

    /**
     * Notes that a flag of a building went without a member of its kingdom taking it down.
     */
    public void markDirty(long id) {
        Record record = record(id);
        if (!record.dirty) {
            record.dirty = true;
            setDirty();
        }
    }

    /**
     * Forgets the places a building's flags were taken down from: its template or its kingdom changed.
     */
    public void clearOff(long id) {
        Record record = buildings.get(id);
        if (record != null && !record.off.isEmpty()) {
            record.off.clear();
            setDirty();
        }
    }

    public void takenDown(long building, long pos) {
        if (record(building).off.add(pos)) {
            setDirty();
        }
    }

    public boolean isOff(long building, long pos) {
        Record record = buildings.get(building);
        return record != null && record.off.contains(pos);
    }

    public void takenDown(String dimension, long pos, UUID kingdom, long anchor) {
        Off off = new Off(kingdom, anchor);
        if (!off.equals(places.computeIfAbsent(dimension, key -> new HashMap<>()).put(pos, off))) {
            setDirty();
        }
    }

    /**
     * Whether a member took down the flag of a place while it was of this kingdom and hung on the same throne or wall: after a
     * conquest or a move of the throne the place flies a flag again.
     */
    public boolean isOff(String dimension, long pos, UUID kingdom, long anchor) {
        Map<Long, Off> taken = places.get(dimension);
        return taken != null && new Off(kingdom, anchor).equals(taken.get(pos));
    }

    /**
     * Drops what belongs to buildings and kingdoms that are no more.
     */
    void clean(MinecraftServer server) {
        var book = KingdomWorldApi.state(server).construction();
        if (buildings.keySet().removeIf(id -> book.building(id).isEmpty())) {
            setDirty();
        }
        for (Map<Long, Off> taken : places.values()) {
            if (taken.values().removeIf(off -> KingdomWorldApi.kingdom(server, off.kingdom()).isEmpty())) {
                setDirty();
            }
        }
    }

    public static FlagSavedData load(CompoundTag tag) {
        FlagSavedData data = new FlagSavedData();
        try {
            ListTag list = tag.getList("buildings", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                Record record = data.record(one.getLong("id"));
                if (one.contains("stamp", Tag.TAG_LONG)) {
                    record.stamp = one.getLong("stamp");
                    record.stamped = true;
                }
                record.dirty = one.getBoolean("dirty");
                for (long pos : one.getLongArray("off")) {
                    record.off.add(pos);
                }
            }
            ListTag dimensions = tag.getList("dimensions", Tag.TAG_COMPOUND);
            for (int i = 0; i < dimensions.size(); i++) {
                CompoundTag one = dimensions.getCompound(i);
                Map<Long, Off> taken = data.places.computeIfAbsent(one.getString("dimension"), key -> new HashMap<>());
                ListTag off = one.getList("off", Tag.TAG_COMPOUND);
                for (int j = 0; j < off.size(); j++) {
                    CompoundTag place = off.getCompound(j);
                    if (place.hasUUID("kingdom")) {
                        taken.put(place.getLong("pos"), new Off(place.getUUID("kingdom"), place.getLong("anchor")));
                    }
                }
            }
        } catch (RuntimeException e) {
            // Whatever cannot be read is looked at again.
            return new FlagSavedData();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            clean(server);
        }
        tag.putInt("formatVersion", FORMAT_VERSION);
        ListTag list = new ListTag();
        buildings.forEach((id, record) -> {
            CompoundTag one = new CompoundTag();
            one.putLong("id", id);
            if (record.stamped) {
                one.putLong("stamp", record.stamp);
            }
            if (record.dirty) {
                one.putBoolean("dirty", true);
            }
            if (!record.off.isEmpty()) {
                one.put("off", new LongArrayTag(record.off.stream().mapToLong(Long::longValue).sorted().toArray()));
            }
            list.add(one);
        });
        tag.put("buildings", list);
        ListTag dimensions = new ListTag();
        places.forEach((dimension, taken) -> {
            if (taken.isEmpty()) {
                return;
            }
            CompoundTag one = new CompoundTag();
            one.putString("dimension", dimension);
            ListTag off = new ListTag();
            taken.forEach((pos, was) -> {
                CompoundTag place = new CompoundTag();
                place.putLong("pos", pos);
                place.putUUID("kingdom", was.kingdom());
                place.putLong("anchor", was.anchor());
                off.add(place);
            });
            one.put("off", off);
            dimensions.add(one);
        });
        tag.put("dimensions", dimensions);
        return tag;
    }
}
